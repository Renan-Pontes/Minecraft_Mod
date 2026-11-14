package com.renanpontes.isekaimahou.feature.stands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MasteryMutations;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Executa as PEÇAS ({@link Perk}) dos estágios de evolução ({@link Evolutions}) — servidor, só em conjurações de
 * JOGADOR pela tecla. Nada aqui muda o código do glifo: tudo acontece nos eventos de magia.
 * <ul>
 *   <li>Qual glifo causou o dano: o EFEITO pela identidade dos stats; um AUMENTO (Boost, Haki) pela marca dele nos
 *       stats ({@code custom(id)}).</li>
 *   <li>Limites: cada glifo age no acerto no máx. 1 vez a cada {@link #HIT_GAP} ticks por jogador; arcos ≤ 3, raios
 *       extras ≤ 5, chamas negras ≤ {@link #MAX_FLAMES} por jogador e {@link #MAX_FLAMES_GLOBAL} no servidor; dano
 *       extra só em inimigos (monstros ou quem mira você), metade em jogadores e chefes; nada quebra blocos; nada age
 *       com o Modo Paz ligado.</li>
 *   <li>Visual: ao conjurar, uma aura curta na cor do estágio e o nome do estágio na barra de ação (no máx. 1 vez a
 *       cada 20 s por glifo); no acerto, um impacto na cor do estágio.</li>
 * </ul>
 */
public final class EvolutionEffects {
    public static final int HIT_GAP = 8;
    public static final int MAX_FLAMES = 3;
    public static final int MAX_FLAMES_GLOBAL = 30;
    private static final int NAME_GAP = 400;

    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();
    private static final List<Flame> FLAMES = new ArrayList<>();
    private static boolean applying;

    /** Anel de chama negra (Enton/Kagutsuchi). */
    private static final class Flame {
        final SpellContext ctx;
        final ServerPlayer owner;
        final Vec3 at;
        final float radius;
        final float dmg;
        final long end;

        Flame(SpellContext ctx, ServerPlayer owner, Vec3 at, float radius, float dmg, long end) {
            this.ctx = ctx;
            this.owner = owner;
            this.at = at;
            this.radius = radius;
            this.dmg = dmg;
            this.end = end;
        }
    }

    // =====================================================================================
    //  Quem / qual glifo
    // =====================================================================================

    @Nullable
    private static ServerPlayer player(@Nullable SpellContext ctx) {
        if (ctx == null || !ctx.isPlayerCast()) return null;
        ServerPlayer sp = ctx.casterPlayer();
        return sp == null || sp instanceof FakePlayer || sp.isSpectator() || PeaceMode.isOn(sp) ? null : sp;
    }

    /** Glifos com evolução que participaram deste dano (efeito pelos stats; aumentos pela marca). */
    private static List<ResourceLocation> sources(SpellContext ctx, @Nullable SpellStats stats) {
        List<ResourceLocation> out = new ArrayList<>(2);
        if (stats == null) return out;
        for (ParsedSpell.EffectEntry en : ctx.spell().effects()) {
            if (en.stats() == stats && Evolutions.hasPerks(en.effect().id())) out.add(en.effect().id());
        }
        for (ResourceLocation id : Evolutions.glyphs()) {
            if (out.contains(id) || stats.custom(id) <= 0) continue;
            Glyph g = GlyphRegistry.get(id);
            if (g instanceof AugmentGlyph) out.add(id);
        }
        return out;
    }

    private static boolean ready(ServerPlayer sp, String key, long now, int gap) {
        Map<String, Long> m = COOLDOWNS.computeIfAbsent(sp.getUUID(), k -> new HashMap<>());
        Long next = m.get(key);
        if (next != null && now < next && next - now <= Math.max(gap, 1)) return false;
        m.put(key, now + gap);
        return true;
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    /** Multiplicador de dano do estágio (em jogadores, metade do extra). */
    static void onDamagePre(SpellDamageEvent.Pre e) {
        ServerPlayer sp = player(e.ctx());
        if (sp == null) return;
        float mult = 1F;
        for (ResourceLocation id : sources(e.ctx(), e.stats())) {
            for (Perk p : Evolutions.perks(id, MasteryMutations.stageOf(sp, id))) {
                if (p.kind() == Perk.Kind.DMG) mult *= p.a();
            }
        }
        if (mult == 1F) return;
        if (e.target() instanceof Player) mult = 1F + (mult - 1F) * 0.5F;
        e.setAmount(e.getAmount() * mult);
    }

    /** Peças de acerto: arcos, raio do céu, chama negra, onda, rajada, por dentro, trava. */
    static void onDamagePost(SpellDamageEvent.Post e) {
        if (applying || !e.landed() || !(e.getAmount() > 0F)) return;
        SpellContext ctx = e.ctx();
        ServerPlayer sp = player(ctx);
        if (sp == null) return;
        LivingEntity target = e.target();
        ServerLevel level = ctx.level();
        long now = level.getGameTime();
        for (ResourceLocation id : sources(ctx, e.stats())) {
            int stage = MasteryMutations.stageOf(sp, id);
            if (stage <= 0 || !ready(sp, id + "#hit", now, HIT_GAP)) continue;
            MasteryMutations.Stage st = MasteryMutations.currentStage(id, stageLevel(id, stage));
            int rgb = st != null ? st.color() : ctx.color();
            ElementFx.impact(level, target.position().add(0, target.getBbHeight() * 0.5, 0), ctx.element(), rgb, Math.min(3, stage), null);
            List<Perk> perks = Evolutions.perks(id, stage);
            for (int i = 0; i < perks.size(); i++) {
                Perk p = perks.get(i);
                if (p.onCast() || p.kind() == Perk.Kind.DMG) continue;
                hitPerk(ctx, sp, target, e.getAmount(), p, rgb, id + "#" + i, now);
            }
        }
    }

    /** Nível do estágio (para achar a cor). */
    private static int stageLevel(ResourceLocation id, int stage) {
        List<MasteryMutations.Stage> l = MasteryMutations.stages(id);
        return stage >= 1 && stage <= l.size() ? l.get(stage - 1).level() : 1;
    }

    /** Peças de conjuração (Balance Breaker, Juggernaut, Susanoo) + aura e nome do estágio. */
    static void onCast(SpellCastEvent.Post e) {
        SpellContext ctx = e.getContext();
        ServerPlayer sp = player(ctx);
        if (sp == null || e.getCaster() != sp) return;
        ServerLevel level = ctx.level();
        long now = level.getGameTime();
        List<ResourceLocation> seen = new ArrayList<>(4);
        for (ResourceLocation id : ctx.recipe().glyphs()) {
            if (seen.contains(id) || MasteryMutations.stages(id).isEmpty()) continue;
            seen.add(id);
            int stage = MasteryMutations.stageOf(sp, id);
            if (stage <= 0) continue;
            MasteryMutations.Stage st = MasteryMutations.stages(id).get(stage - 1);
            if (ready(sp, id + "#aura", now, 30)) {
                ElementFx.aura(level, sp, ctx.element(), st.color(), 1.0F, Math.min(3, stage), 14);
            }
            if (ready(sp, id + "#name", now, NAME_GAP)) {
                sp.displayClientMessage(Component.literal("⟡ ").append(st.displayName()).withColor(st.color()), true);
            }
            List<Perk> perks = Evolutions.perks(id, stage);
            for (int i = 0; i < perks.size(); i++) {
                Perk p = perks.get(i);
                if (!p.onCast()) continue;
                String key = id + "#" + i;
                if (p.kind() == Perk.Kind.BUFF && p.effect() != null && ready(sp, key, now, Math.round(p.a() * 20))) {
                    sp.addEffect(new MobEffectInstance(p.effect(), p.c(), Math.round(p.b()), false, true, true));
                } else if (p.kind() == Perk.Kind.BERSERK && ready(sp, key, now, 200)) {
                    berserk(sp, st.color());
                }
            }
        }
    }

    // =====================================================================================
    //  Peças
    // =====================================================================================

    private static void hitPerk(SpellContext ctx, ServerPlayer sp, LivingEntity target, float amount, Perk p, int rgb, String key, long now) {
        ServerLevel level = ctx.level();
        Vec3 at = target.position();
        switch (p.kind()) {
            case ARCS -> {
                List<LivingEntity> near = hostilesAround(ctx, sp, at, 6, target);
                near.sort(Comparator.comparingDouble(v -> v.distanceToSqr(target)));
                Vec3 a = at.add(0, target.getBbHeight() * 0.5, 0);
                for (int i = 0; i < Math.min(Math.min(3, p.c()), near.size()); i++) {
                    LivingEntity v = near.get(i);
                    ElementFx.beam(level, a, v.position().add(0, v.getBbHeight() * 0.5, 0), ctx.element(), rgb, 0.12F, 1);
                    hurt(ctx, v, amount * p.a());
                }
            }
            case SKY -> {
                if (!ready(sp, key, now, p.c())) return;
                ElementFx.skyStrike(level, at, ctx.element(), rgb, p.b(), 3, 6);
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(at.x, at.y, at.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                float dmg = amount * p.a();
                SpellScheduler.schedule(6, () -> {
                    if (!sp.isAlive() || sp.level() != level) return;
                    List<LivingEntity> hit = hostilesAround(ctx, sp, at, p.b(), null);
                    if (target.isAlive() && !hit.contains(target) && !ctx.isAlly(target) && target.distanceToSqr(at) <= p.b() * p.b()) hit.add(target);
                    for (LivingEntity v : hit) hurt(ctx, v, dmg);
                    ElementFx.shake(level, at, 16, 0.6F, 8);
                });
            }
            case BLACK_FLAME -> {
                int mine = 0;
                for (Flame f : FLAMES) if (f.owner == sp) mine++;
                if (mine >= MAX_FLAMES || FLAMES.size() >= MAX_FLAMES_GLOBAL || !ready(sp, key, now, 20)) return;
                FLAMES.add(new Flame(ctx, sp, at, p.b(), amount * p.a(), now + p.c()));
                ElementFx.shockwave(level, at.add(0, 0.1, 0), 0x140010, p.b(), 14, null);
            }
            case SHOCK -> {
                float r = p.c() / 10F;
                ElementFx.shockwave(level, at.add(0, 0.1, 0), rgb, r, 12, null);
                for (LivingEntity v : hostilesAround(ctx, sp, at, r, null)) {
                    if (!BossKit.isBossLike(v)) {
                        v.knockback(p.b(), sp.getX() - v.getX(), sp.getZ() - v.getZ());
                        v.hurtMarked = true;
                    }
                    if (v != target) hurt(ctx, v, amount * p.a());
                }
                if (r >= 3.5F) ElementFx.shake(level, at, 14, 0.5F, 8);
            }
            case VOLLEY -> {
                List<LivingEntity> near = hostilesAround(ctx, sp, at, 12, target);
                near.sort(Comparator.comparingDouble(v -> v.distanceToSqr(target)));
                Vec3 from = sp.getEyePosition().add(sp.getLookAngle().scale(0.6));
                for (int i = 0; i < Math.min(Math.min(5, p.c()), near.size()); i++) {
                    LivingEntity v = near.get(i);
                    float dmg = amount * p.a();
                    SpellScheduler.schedule(2 + 2 * i, () -> {
                        if (!sp.isAlive() || sp.level() != level || !v.isAlive()) return;
                        ElementFx.beam(level, from, v.position().add(0, v.getBbHeight() * 0.5, 0), ctx.element(), rgb, 0.1F, 1);
                        hurt(ctx, v, dmg);
                    });
                }
            }
            case PIERCE -> {
                if (!target.isAlive() || ctx.isAlly(target)) return;
                float a = amount * p.a() * (target instanceof Player || BossKit.isBossLike(target) ? 0.5F : 1F);
                applying = true;
                try {
                    target.invulnerableTime = 0;
                    target.hurt(level.damageSources().indirectMagic(sp, sp), a);
                } finally {
                    applying = false;
                }
                ElementFx.flash(level, at.add(0, target.getBbHeight() * 0.5, 0), rgb, 1.2F);
            }
            case FREEZE -> {
                if (!target.isAlive() || ctx.isAlly(target)) return;
                boolean boss = BossKit.isBossLike(target);
                int t = boss ? p.c() / 2 : p.c();
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, boss ? 2 : 6, false, false), sp);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 1, false, false), sp);
            }
            default -> {}
        }
    }

    /** Juggernaut Drive: força e velocidade, mas a fúria cobra 2 de vida (nunca abaixo de 1 coração). */
    private static void berserk(ServerPlayer sp, int rgb) {
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 0, false, true, true));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, false, true, true));
        if (sp.getHealth() > 4F) sp.setHealth(sp.getHealth() - 2F);
        BossKit.vignette(sp, rgb, 0.25F, 30);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    /** Anéis de chama negra: queimam inimigos dentro a cada 0,5 s (a água não apaga). */
    static void tick(MinecraftServer server) {
        if (FLAMES.isEmpty()) return;
        for (Iterator<Flame> it = FLAMES.iterator(); it.hasNext(); ) {
            Flame f = it.next();
            ServerLevel level = f.ctx.level();
            long now = level.getGameTime();
            if (now >= f.end || !f.owner.isAlive() || f.owner.level() != level || PeaceMode.isOn(f.owner)) {
                it.remove();
                continue;
            }
            if (now % 10 != 0) continue;
            level.sendParticles(ElementFx.glow(0x100010, 1.4F), f.at.x, f.at.y + 0.2, f.at.z, 6, f.radius * 0.5, 0.15, f.radius * 0.5, 0);
            ElementFx.ring(level, f.at.add(0, 0.1, 0), f.ctx.element(), f.radius, 12);
            for (LivingEntity v : hostilesAround(f.ctx, f.owner, f.at, f.radius, null)) {
                v.setRemainingFireTicks(Math.max(v.getRemainingFireTicks(), 30));
                hurt(f.ctx, v, f.dmg);
            }
        }
    }

    /** Inimigos numa esfera (monstros ou quem mira você). Nunca jogadores, aliados, aldeões ou animais. */
    private static List<LivingEntity> hostilesAround(SpellContext ctx, ServerPlayer sp, Vec3 c, double r, @Nullable LivingEntity except) {
        return new ArrayList<>(ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                v -> v != except && v != sp && v.isAlive() && !(v instanceof Player) && !(v instanceof ArmorStand)
                        && v.distanceToSqr(c) <= r * r && !ctx.isAlly(v)
                        && (v instanceof Enemy || (v instanceof Mob m && m.getTarget() == sp))));
    }

    /** Dano extra (fonte = a magia do jogador, sem passar pelos eventos): metade em jogadores e chefes. */
    private static void hurt(SpellContext ctx, LivingEntity v, float amount) {
        if (!(amount > 0F) || !v.isAlive()) return;
        float a = amount * (v instanceof Player || BossKit.isBossLike(v) ? 0.5F : 1F);
        applying = true;
        try {
            v.invulnerableTime = 0;
            v.hurt(ctx.damageSource(null), a);
        } finally {
            applying = false;
        }
    }

    static void forget(UUID id) {
        COOLDOWNS.remove(id);
        FLAMES.removeIf(f -> f.owner.getUUID().equals(id));
    }

    static void clear() {
        COOLDOWNS.clear();
        FLAMES.clear();
    }

    private EvolutionEffects() {}
}
