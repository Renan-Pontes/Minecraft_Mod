package com.renanpontes.isekaimahou.feature.glyphs_elements_a;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Comportamento dos 7 aumentos elementais (v1.4). Cada aumento é um marcador em {@code SpellStats.custom}; vale tanto
 * colocado depois da FORMA (todos os efeitos da magia) quanto depois de um EFEITO (só o dano daquele efeito).
 * <p>
 * O dano extra que estes aumentos causam sai por {@code hurt} direto (não por {@code ctx.damage}), então não dispara
 * de novo os eventos de dano de magia — nada aqui entra em laço. Cada alvo tem um intervalo mínimo por aumento (zonas e
 * auras acertam várias vezes por segundo).
 */
public final class ElementAugments {
    /** Combustão Tardia: atraso (2 s) e fração do dano da detonação. */
    static final int AFTERBURN_DELAY = 40;
    static final float AFTERBURN_FRACTION = 0.5F;
    /** Tremor Residual: atraso (1 s), raio e fração do dano. */
    static final int AFTERSHOCK_DELAY = 20;
    static final double AFTERSHOCK_RADIUS = 3.5;
    static final float AFTERSHOCK_FRACTION = 0.35F;
    /** Ricochete do Vento: alcance, cone (cosseno), poder por salto e saltos por cópia do aumento. */
    static final double RICOCHET_RANGE = 10.0;
    static final double RICOCHET_CONE = 0.45;
    static final float RICOCHET_POWER = 0.7F;
    static final int RICOCHET_BOUNCES = 2;
    /** Refração: fração do dano que vira cura, alcance e quantos aliados. */
    static final float REFRACTION_FRACTION = 0.25F;
    static final double REFRACTION_RANGE = 10.0;
    /** Emboscada: multiplicador em alvo desprevenido. */
    static final float AMBUSH_MULT = 1.5F;
    /** Mimetismo: poder dos efeitos copiados e validade da "última magia" (30 s). */
    static final float MIMIC_POWER = 0.4F;
    static final long MIMIC_MEMORY = 600;

    /** "id|alvo" → próximo tick em que o aumento pode agir de novo neste alvo. */
    private static final Map<String, Long> COOLDOWN = new HashMap<>();
    /** Combustões já armadas (um alvo só tem uma por vez). */
    private static final Set<UUID> ARMED = new HashSet<>();
    /** Ricochete em andamento: o alvo que vai receber o salto → saltos que sobram e quem já foi atingido. */
    private static final Map<UUID, Bounce> BOUNCES = new HashMap<>();
    /** Última magia (sem Mimetismo) de cada jogador. */
    private static final Map<UUID, Remembered> LAST_SPELL = new HashMap<>();

    private record Bounce(int remaining, Set<UUID> hit, long until) {}

    private record Remembered(SpellContext ctx, long at) {}

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    /** Emboscada mexe no valor do dano (antes do hurt). */
    public static void onDamagePre(SpellDamageEvent.Pre e) {
        SpellContext ctx = e.ctx();
        if (!has(ctx, e.stats(), ElementGlyphsA.AMBUSH)) return;
        LivingEntity caster = ctx.caster();
        LivingEntity t = e.target();
        if (caster == null || t == caster) return;
        if (unaware(t, caster)) {
            e.setAmount(e.getAmount() * AMBUSH_MULT);
            ServerLevel l = ctx.level();
            l.sendParticles(ParticleTypes.SQUID_INK, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 10, 0.25, 0.3, 0.25, 0.02);
            l.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
            sound(l, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.8F, 0.6F);
        } else if (ready(ElementGlyphsA.AMBUSH, t, l(ctx), 20)) {
            Statuses.apply(t, Statuses.Kind.CORRUPTED, 1, 100, caster, ctx.powerMult());
        }
    }

    public static void onDamagePost(SpellDamageEvent.Post e) {
        if (!e.landed() || !(e.dealt() > 0F)) return;
        SpellContext ctx = e.ctx();
        SpellStats st = e.stats();
        LivingEntity t = e.target();
        if (has(ctx, st, ElementGlyphsA.AFTERBURN)) afterburn(ctx, t, e.dealt());
        if (has(ctx, st, ElementGlyphsA.UNDERTOW)) undertow(ctx, t);
        if (has(ctx, st, ElementGlyphsA.AFTERSHOCK)) aftershock(ctx, t, e.dealt());
        if (has(ctx, st, ElementGlyphsA.WIND_RICOCHET)) ricochet(ctx, st, t, e.direct());
        if (has(ctx, st, ElementGlyphsA.REFRACTION)) refraction(ctx, t, e.dealt());
        if (has(ctx, st, ElementGlyphsA.MIMIC)) mimic(ctx, t);
    }

    /** Guarda a última magia de cada jogador (para o Mimetismo). Magias com Mimetismo não contam. */
    public static void onCastPost(SpellCastEvent.Post e) {
        if (!(e.getCaster() instanceof ServerPlayer p)) return;
        SpellContext ctx = e.getContext();
        ParsedSpell spell = ctx.spell();
        if (spell.effects().isEmpty() || spell.anyStats(s -> s.custom(ElementGlyphsA.MIMIC) > 0)) return;
        LAST_SPELL.put(p.getUUID(), new Remembered(ctx, ctx.level().getGameTime()));
    }

    // =====================================================================================
    //  Aumentos
    // =====================================================================================

    /** Fogo — Combustão Tardia: 2 s depois o alvo explode com metade do dano e pega Em Chamas I. */
    private static void afterburn(SpellContext ctx, LivingEntity t, float dealt) {
        if (!ARMED.add(t.getUUID())) return;
        ServerLevel level = ctx.level();
        float amount = dealt * AFTERBURN_FRACTION;
        LivingEntity caster = ctx.caster();
        // Brasa presa no alvo enquanto espera
        level.sendParticles(ParticleTypes.SMALL_FLAME, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
        SpellScheduler.schedule(AFTERBURN_DELAY, () -> {
            ARMED.remove(t.getUUID());
            if (!t.isAlive() || t.level() != level) return;
            Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
            ElementFx.impact(level, c, Element.FIRE, 2, null);
            level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 18, 0.4, 0.4, 0.4, 0.06);
            sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.4F);
            t.invulnerableTime = 0;
            t.hurt(ctx.damageSource(null), amount);
            Statuses.apply(t, Statuses.Kind.BURNING, 1, 60, caster, ctx.powerMult());
        });
    }

    /** Água — Ressaca: puxa o alvo na sua direção e o deixa Molhado (se já estava, também Gelado I). */
    private static void undertow(SpellContext ctx, LivingEntity t) {
        LivingEntity caster = ctx.caster();
        if (caster == null || t == caster || !ready(ElementGlyphsA.UNDERTOW, t, l(ctx), 10)) return;
        boolean wasWet = Statuses.has(t, Statuses.Kind.WET);
        Vec3 to = caster.position().subtract(t.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.lengthSqr() > 1.0E-4 && flat.lengthSqr() > 4.0) {
            double force = Statuses.isBossLike(t) ? 0.2 : 0.65;
            Vec3 pull = flat.normalize().scale(force);
            t.push(pull.x, 0.12, pull.z);
            t.hurtMarked = true;
        }
        Statuses.apply(t, Statuses.Kind.WET, 1, 120, caster, ctx.powerMult());
        if (wasWet) Statuses.apply(t, Statuses.Kind.CHILLED, 1, 100, caster, ctx.powerMult());
        ServerLevel level = ctx.level();
        level.sendParticles(ParticleTypes.SPLASH, t.getX(), t.getY() + 0.3, t.getZ(), 24, 0.5, 0.2, 0.5, 0.2);
        level.sendParticles(ParticleTypes.BUBBLE_POP, t.getX(), t.getY() + 0.6, t.getZ(), 10, 0.4, 0.3, 0.4, 0.05);
        sound(level, t.position(), SoundEvents.GENERIC_SPLASH, 0.6F, 0.8F);
    }

    /** Terra — Tremor Residual: 1 s depois, quem estiver no chão perto do alvo leva 35% do dano e Enrijecendo I. */
    private static void aftershock(SpellContext ctx, LivingEntity t, float dealt) {
        if (!ready(ElementGlyphsA.AFTERSHOCK, t, l(ctx), 20)) return;
        ServerLevel level = ctx.level();
        Vec3 at = t.position();
        float amount = dealt * AFTERSHOCK_FRACTION;
        SpellScheduler.schedule(AFTERSHOCK_DELAY, () -> {
            if (t.level() != level) return;
            Vec3 c = t.isAlive() ? t.position() : at;
            ElementFx.shockwave(level, c.add(0, 0.1, 0), Element.EARTH.color, (float) AFTERSHOCK_RADIUS, 14, null);
            level.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.1, c.z, 16, AFTERSHOCK_RADIUS * 0.5, 0.05, AFTERSHOCK_RADIUS * 0.5, 0.02);
            sound(level, c, SoundEvents.ROOTED_DIRT_BREAK, 1.2F, 0.5F);
            sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.35F, 0.5F);
            for (LivingEntity e : ctx.enemiesAround(c, AFTERSHOCK_RADIUS)) {
                if (!e.onGround()) continue;   // quem está no ar escapa do tremor
                e.invulnerableTime = 0;
                e.hurt(ctx.damageSource(null), amount);
                Statuses.apply(e, Statuses.Kind.PETRIFIED, 1, 60, ctx.caster(), ctx.powerMult());
            }
        });
    }

    /**
     * Vento — Ricochete do Vento: depois do acerto, os efeitos da magia pulam para o inimigo mais próximo DENTRO do cone
     * da direção em que a magia viajava (até 10 blocos), com 70% do poder. 2 saltos por cópia do aumento.
     */
    private static void ricochet(SpellContext ctx, SpellStats st, LivingEntity t, @Nullable Entity direct) {
        ServerLevel level = ctx.level();
        long now = level.getGameTime();
        Bounce b = BOUNCES.remove(t.getUUID());
        int remaining;
        Set<UUID> hit;
        if (b != null && b.until >= now) {
            remaining = b.remaining;
            hit = b.hit;
        } else {
            int copies = Math.max(st.custom(ElementGlyphsA.WIND_RICOCHET), ctx.formStats().custom(ElementGlyphsA.WIND_RICOCHET));
            remaining = RICOCHET_BOUNCES * Math.max(1, copies);
            hit = new HashSet<>();
        }
        if (remaining <= 0 || !ready(ElementGlyphsA.WIND_RICOCHET, t, now, 6)) return;
        hit.add(t.getUUID());
        Vec3 dir = travelDir(ctx, t, direct);
        Vec3 from = t.position().add(0, t.getBbHeight() * 0.5, 0);
        LivingEntity next = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : ctx.enemiesAround(from, RICOCHET_RANGE)) {
            if (e == t || hit.contains(e.getUUID())) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(from);
            double d = to.lengthSqr();
            if (d < 1.0E-4) continue;
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() < 1.0E-4 || flat.normalize().dot(dir) < RICOCHET_CONE) continue;
            if (d < best) { best = d; next = e; }
        }
        if (next == null) return;
        LivingEntity target = next;
        BOUNCES.put(target.getUUID(), new Bounce(remaining - 1, hit, now + 30));
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        ElementFx.line(level, from, to, Element.WIND);
        level.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 6, 0.2, 0.2, 0.2, 0.05);
        sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.6F);
        SpellContext c = ctx.copy(ctx.powerMult() * RICOCHET_POWER);
        SpellScheduler.schedule(4, () -> {
            if (!target.isAlive() || target.level() != level) return;
            // O vento empurra o alvo na direção do sopro
            target.push(dir.x * 0.35, 0.1, dir.z * 0.35);
            target.hurtMarked = true;
            for (ParsedSpell.EffectEntry entry : c.spell().effects()) {
                if (c.isValidTarget(entry.effect(), entry.stats(), target)) c.applyEffect(entry, target);
            }
        });
    }

    /** Direção horizontal em que a magia viajava: a do projétil, se houver; senão do conjurador até o alvo. */
    private static Vec3 travelDir(SpellContext ctx, LivingEntity t, @Nullable Entity direct) {
        Vec3 v = direct != null ? direct.getDeltaMovement() : Vec3.ZERO;
        v = new Vec3(v.x, 0, v.z);
        if (v.lengthSqr() > 1.0E-3) return v.normalize();
        LivingEntity caster = ctx.caster();
        Vec3 d = caster != null ? t.position().subtract(caster.position()) : ctx.look();
        d = new Vec3(d.x, 0, d.z);
        if (d.lengthSqr() < 1.0E-4) d = new Vec3(ctx.look().x, 0, ctx.look().z);
        return d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
    }

    /** Luz — Refração: 25% do dano vira cura, dividida entre os 2 aliados mais feridos perto do alvo (você conta). */
    private static void refraction(SpellContext ctx, LivingEntity t, float dealt) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !ready(ElementGlyphsA.REFRACTION, t, l(ctx), 5)) return;
        ServerLevel level = ctx.level();
        Vec3 c = t.position();
        List<LivingEntity> allies = level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(REFRACTION_RANGE),
                e -> e.isAlive() && e != t && ctx.isAlly(e) && e.getHealth() < e.getMaxHealth()
                        && e.distanceToSqr(c) <= REFRACTION_RANGE * REFRACTION_RANGE);
        if (allies.isEmpty()) return;
        // Os mais feridos (proporcionalmente) primeiro
        allies.sort((a, b) -> Float.compare(a.getHealth() / a.getMaxHealth(), b.getHealth() / b.getMaxHealth()));
        int n = Math.min(2, allies.size());
        float each = dealt * REFRACTION_FRACTION / n;
        Vec3 from = c.add(0, t.getBbHeight() * 0.5, 0);
        for (int i = 0; i < n; i++) {
            LivingEntity a = allies.get(i);
            a.heal(each);
            Vec3 to = a.position().add(0, a.getBbHeight() * 0.6, 0);
            ElementFx.line(level, from, to, Element.LIGHT);
            level.sendParticles(ParticleTypes.HEART, to.x, a.getY() + a.getBbHeight() + 0.3, to.z, 1, 0.2, 0.1, 0.2, 0);
        }
        sound(level, from, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
    }

    /** Arcano — Mimetismo: aplica no alvo os efeitos da magia ANTERIOR do conjurador, com 40% do poder. */
    private static void mimic(SpellContext ctx, LivingEntity t) {
        if (ctx.casterId() == null || !ready(ElementGlyphsA.MIMIC, t, l(ctx), 10)) return;
        Remembered r = LAST_SPELL.get(ctx.casterId());
        long now = l(ctx);
        if (r == null || r.ctx.level() != ctx.level() || now - r.at > MIMIC_MEMORY) return;
        SpellContext c = r.ctx.copy(r.ctx.powerMult() * MIMIC_POWER);
        boolean any = false;
        for (ParsedSpell.EffectEntry entry : c.spell().effects()) {
            if (!c.isValidTarget(entry.effect(), entry.stats(), t)) continue;
            c.applyEffect(entry, t);
            any = true;
        }
        if (!any) return;
        ServerLevel level = ctx.level();
        Vec3 p = t.position().add(0, t.getBbHeight() * 0.5, 0);
        ElementFx.burst(level, p, Element.ARCANE, 14, 0.4);
        ElementFx.ring(level, t.position().add(0, 0.1, 0), Element.ARCANE, 1.0, 12);
        sound(level, p, SoundEvents.EVOKER_CAST_SPELL, 0.6F, 1.5F);
    }

    // =====================================================================================
    //  Auxiliares
    // =====================================================================================

    /** O aumento está neste efeito ou na forma (vale para toda a magia)? */
    static boolean has(SpellContext ctx, SpellStats st, ResourceLocation id) {
        return (st != null && st.custom(id) > 0) || ctx.formStats().custom(id) > 0;
    }

    /** Desprevenido: de costas para o conjurador, conjurador invisível, ou mob que não está mirando nele. */
    static boolean unaware(LivingEntity t, LivingEntity caster) {
        if (caster.isInvisible()) return true;
        if (t instanceof Mob m && m.getTarget() != caster) return true;
        Vec3 look = t.getViewVector(1.0F);
        Vec3 to = caster.position().subtract(t.position());
        Vec3 fl = new Vec3(look.x, 0, look.z), ft = new Vec3(to.x, 0, to.z);
        if (fl.lengthSqr() < 1.0E-4 || ft.lengthSqr() < 1.0E-4) return false;
        return fl.normalize().dot(ft.normalize()) < -0.2;
    }

    private static long l(SpellContext ctx) {
        return ctx.level().getGameTime();
    }

    /** Intervalo mínimo por aumento e alvo. */
    private static boolean ready(ResourceLocation id, Entity t, long now, int interval) {
        String key = id.getPath() + "|" + t.getUUID();
        Long next = COOLDOWN.get(key);
        if (next != null && next > now) return false;
        COOLDOWN.put(key, now + interval);
        return true;
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Limpeza periódica (a cada 10 s): intervalos vencidos, ricochetes velhos e memórias de quem saiu. */
    static void cleanup(long now) {
        COOLDOWN.values().removeIf(t -> t < now);
        BOUNCES.values().removeIf(b -> b.until < now);
        Iterator<Map.Entry<UUID, Remembered>> it = LAST_SPELL.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Remembered> e = it.next();
            if (now - e.getValue().at > MIMIC_MEMORY || now < e.getValue().at) it.remove();
        }
    }

    static void forget(UUID player) {
        LAST_SPELL.remove(player);
    }

    static void clear() {
        COOLDOWN.clear();
        ARMED.clear();
        BOUNCES.clear();
        LAST_SPELL.clear();
    }

    private ElementAugments() {}
}
