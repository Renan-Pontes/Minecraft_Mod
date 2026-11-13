package com.renanpontes.isekaimahou.feature.glyphs_elements_b;

import static com.renanpontes.isekaimahou.feature.glyphs_elements_b.GebGlyphs.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.api.Statuses.Kind;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.fx.Vfx;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/**
 * Comportamento dos 15 aumentos de fusão (ver {@link GebGlyphs}). Tudo no servidor, a partir de:
 * <ul>
 *   <li>{@link SpellDamageEvent.Pre}: bônus de dano (Âncora na água, Estilhaços) e marcas que precisam existir ANTES
 *       do golpe que mata (Chamas Famintas, Aniquilar);</li>
 *   <li>{@link SpellDamageEvent.Post}: o efeito de cada aumento no alvo atingido;</li>
 *   <li>{@link SpellCastEvent}: Jato de Vapor (recuo ao conjurar) e Maré Crepuscular (alterna a fase).</li>
 * </ul>
 * Cada efeito por alvo tem uma recarga interna curta ({@link GebUtil#ready}) para feixes e zonas (que acertam a cada
 * poucos ticks) não dispararem o aumento todo tick. O dano extra usa {@link GebUtil#hit}, que os ouvintes ignoram.
 */
public final class GebAugments {
    /** Chamas Famintas: alvo marcado -> marca (o golpe que mata já conta: a marca nasce no Pre). */
    private static final Map<UUID, HellMark> HELL = new HashMap<>();
    /** Aniquilar: alvo atingido NESTE tick (vítima -> tick). Se morrer agora, o corpo é apagado. */
    private static final Map<UUID, Long> ERASE_CANDIDATE = new HashMap<>();
    /** Aniquilar: corpos apagados (vítima -> tick limite) para zerar o XP que cai ~1 s depois. */
    private static final Map<UUID, Long> ERASED = new HashMap<>();
    /** Estilhaços: alvos que vão estilhaçar no Post deste golpe. */
    private static final Set<UUID> SHATTER = new HashSet<>();
    /** Maré Crepuscular: fase atual de cada conjurador (true = Ocaso, false = Aurora). */
    private static final Map<UUID, Boolean> DUSK = new HashMap<>();

    private record HellMark(SpellContext ctx, float amount, int gen, long until) {}

    // =====================================================================================
    //  Eventos de dano
    // =====================================================================================
    static void onDamagePre(SpellDamageEvent.Pre e) {
        if (GebUtil.guard > 0) return;
        SpellContext ctx = e.ctx();
        SpellStats st = e.stats();
        LivingEntity t = e.target();
        long now = ctx.level().getGameTime();

        // Âncora Abissal: o abismo puxa mais forte quem está na água
        if (has(ctx, st, ABYSS_ANCHOR) && t.isInWater()) e.setAmount(e.getAmount() * 1.35F);

        // Estilhaços: alvo Congelado ou Gelado III quebra (+25% neste golpe; os estilhaços saem no Post)
        if (has(ctx, st, ICE_SPLINTER) && (Statuses.has(t, Kind.FROZEN) || Statuses.stacks(t, Kind.CHILLED) >= 3)
                && GebUtil.ready("ice|" + t.getUUID(), now, 20)) {
            e.setAmount(e.getAmount() * 1.25F);
            SHATTER.add(t.getUUID());
        }

        // Chamas Famintas: marca antes do dano para o golpe que mata também espalhar o fogo
        if (has(ctx, st, HELLFIRE_HUNGER)) HELL.put(t.getUUID(), new HellMark(ctx, e.getAmount(), 0, now + 120));

        // Aniquilar: só criaturas comuns (nunca jogadores nem chefes — loot de progressão fica a salvo)
        if (has(ctx, st, VOID_ANNIHILATE) && !(t instanceof Player) && !Statuses.isBossLike(t)) {
            ERASE_CANDIDATE.put(t.getUUID(), now);
        }
    }

    static void onDamagePost(SpellDamageEvent.Post e) {
        if (GebUtil.guard > 0) return;
        SpellContext ctx = e.ctx();
        SpellStats st = e.stats();
        LivingEntity t = e.target();
        ServerLevel level = ctx.level();
        long now = level.getGameTime();
        String tid = t.getUUID().toString();
        String cid = ctx.casterId() == null ? "none" : ctx.casterId().toString();
        float amount = e.getAmount();
        boolean alive = t.isAlive() && !e.killed();

        if (SHATTER.remove(t.getUUID()) && e.landed()) {
            shatter(ctx, t, amount);
        } else if (alive && e.landed() && has(ctx, st, ICE_SPLINTER)) {
            Statuses.apply(t, Kind.CHILLED, 1, Statuses.DEFAULT_TICKS, ctx.caster(), ctx.powerMult());
        }
        if (!e.landed()) return;

        if (has(ctx, st, MAGMA_AFTERSHOCK) && GebUtil.ready("magma|" + tid, now, 20)) aftershock(ctx, t, amount);
        if (has(ctx, st, LIGHTNING_FORK) && GebUtil.ready("fork|" + tid, now, 10)) fork(ctx, t, amount);
        if (alive && has(ctx, st, SUN_CAPTIVE_STAR) && GebUtil.ready("sun|" + tid, now, 80)) captiveStar(ctx, t, amount);
        if (alive && has(ctx, st, HELLFIRE_HUNGER)) {
            Statuses.applyUpTo(t, Kind.CORRUPTED, 1, Statuses.DEFAULT_TICKS, ctx.caster(), ctx.powerMult(), 3);
        }
        if (has(ctx, st, NATURE_SEED) && GebUtil.ready("seed|" + tid, now, 60)) seed(ctx, t, amount);
        if (alive && has(ctx, st, PURITY_ABLUTION) && GebUtil.ready("purity|" + tid, now, 40)) ablution(ctx, t);
        if (alive && has(ctx, st, ABYSS_ANCHOR) && GebUtil.ready("anchor|" + tid, now, 20)) anchor(ctx, t);
        if (alive && has(ctx, st, SAND_EROSION) && GebUtil.ready("sand|" + tid, now, 10)) erosion(ctx, t);
        // Ressonância: os ecos do cristal (poder < 0,5) não criam outro cristal
        if (has(ctx, st, CRYSTAL_RESONANCE) && ctx.powerMult() >= 0.5F && GebUtil.ready("crystal|" + cid, now, 10)) {
            resonance(ctx, t);
        }
        if (has(ctx, st, GRAVITY_COLLAPSE) && GebUtil.ready("gravity|" + cid, now, 10)) collapse(ctx, t, amount);
        if (has(ctx, st, ASTRAL_STARSTEP) && ctx.caster() instanceof ServerPlayer sp && !(sp instanceof FakePlayer)
                && GebUtil.ready("star|" + cid, now, 40)) {
            starstep(sp, t);
        }
        if (e.killed() && has(ctx, st, VOID_ANNIHILATE)) voidFeast(ctx, t, now);
        if (has(ctx, st, TWILIGHT_TIDE)) tide(ctx, t, e.dealt(), alive);
    }

    // =====================================================================================
    //  Eventos de conjuração
    // =====================================================================================

    /** Maré Crepuscular: cada conjuração com o aumento troca a fase (Aurora ↔ Ocaso). Roda por último no Pre. */
    static void onCastPre(SpellCastEvent.Pre e) {
        if (!(e.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (!anywhere(e.getContext(), TWILIGHT_TIDE)) return;
        boolean dusk = !DUSK.getOrDefault(p.getUUID(), true); // a 1ª conjuração é Aurora
        DUSK.put(p.getUUID(), dusk);
        ServerLevel level = p.serverLevel();
        ElementFx.ring(level, p.position(), dusk ? Element.SHADOW : Element.LIGHT, 1.1, 16);
        level.sendParticles(dusk ? Vfx.witch() : Vfx.endRod(), p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
        GebUtil.sound(level, p.position(), dusk ? SoundEvents.SOUL_ESCAPE.value() : SoundEvents.AMETHYST_BLOCK_CHIME,
                0.7F, dusk ? 0.7F : 1.5F);
        p.displayClientMessage(Component.translatable(dusk ? "message.isekai_mahou.geb.dusk" : "message.isekai_mahou.geb.dawn")
                .withColor(dusk ? 0xA070D0 : 0xFFD870), true);
    }

    /** Jato de Vapor: recuo para trás ao conjurar (agachado: para a frente). */
    static void onCastPost(SpellCastEvent.Post e) {
        if (!(e.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer || p.isPassenger()) return;
        if (!anywhere(e.getContext(), STEAM_JET)) return;
        ServerLevel level = p.serverLevel();
        if (!GebUtil.ready("jet|" + p.getUUID(), level.getGameTime(), 10)) return;
        Vec3 look = p.getLookAngle();
        Vec3 dir = p.isShiftKeyDown() ? look : look.scale(-1);
        Vec3 h = new Vec3(dir.x, 0, dir.z);
        h = h.lengthSqr() < 1.0E-4 ? Vec3.ZERO : h.normalize();
        p.setDeltaMovement(p.getDeltaMovement().add(h.x * 1.1, 0.35 + Math.max(0, dir.y) * 0.4, h.z * 1.1));
        p.hurtMarked = true;
        p.resetFallDistance();
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20, 0, false, false, true));
        Vec3 feet = p.position();
        level.sendParticles(ParticleTypes.CLOUD, feet.x, feet.y + 0.3, feet.z, 14, 0.3, 0.15, 0.3, 0.08);
        level.sendParticles(Vfx.whiteSmoke(), feet.x, feet.y + 0.6, feet.z, 8, 0.25, 0.25, 0.25, 0.04);
        GebUtil.sound(level, feet, SoundEvents.LAVA_EXTINGUISH, 0.6F, 1.6F);
    }

    // =====================================================================================
    //  Morte e drops (Chamas Famintas, Aniquilar)
    // =====================================================================================
    static void onDeath(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!(v.level() instanceof ServerLevel level)) return;
        HellMark m = HELL.remove(v.getUUID());
        if (m == null) return;
        long now = level.getGameTime();
        SpellContext ctx = m.ctx();
        if (now > m.until() || ctx.level() != level) return;
        int burn = Math.min(5, Math.max(2, Statuses.stacks(v, Kind.BURNING) + 1));
        Vec3 from = GebUtil.center(v);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 18, 0.35, 0.45, 0.35, 0.04);
        ElementFx.burst(level, from, Element.HELLFIRE, 12, 0.4);
        GebUtil.sound(level, from, SoundEvents.FIRECHARGE_USE, 0.7F, 0.55F);
        for (LivingEntity next : GebUtil.enemiesNear(ctx, from, 6.0, v, 2)) {
            SpellScheduler.schedule(4, () -> {
                if (!next.isAlive() || next.level() != level) return;
                ElementFx.line(level, from, GebUtil.center(next), Element.HELLFIRE);
                // A marca vem antes do dano: se este salto matar, o fogo continua (até a 3ª onda)
                if (m.gen() < 2) HELL.put(next.getUUID(), new HellMark(ctx, m.amount() * 0.5F, m.gen() + 1, level.getGameTime() + 120));
                Statuses.apply(next, Kind.BURNING, burn, Statuses.DEFAULT_TICKS, ctx.caster(), ctx.powerMult());
                Statuses.applyUpTo(next, Kind.CORRUPTED, 1, Statuses.DEFAULT_TICKS, ctx.caster(), ctx.powerMult(), 3);
                GebUtil.hit(ctx, next, m.amount() * 0.5F, Element.HELLFIRE);
            });
        }
    }

    /** Aniquilar: o corpo some sem deixar nada (prioridade alta: outros módulos nem veem os drops). */
    static void onDrops(LivingDropsEvent e) {
        LivingEntity v = e.getEntity();
        Long tick = ERASE_CANDIDATE.remove(v.getUUID());
        if (tick == null || tick != v.level().getGameTime()) return;
        e.setCanceled(true);
        ERASED.put(v.getUUID(), v.level().getGameTime() + 100);
    }

    static void onXp(LivingExperienceDropEvent e) {
        if (ERASED.remove(e.getEntity().getUUID()) != null) e.setDroppedExperience(0);
    }

    /** O corpo foi apagado por Aniquilar? (para o gatilho Ao Apagar) */
    static boolean wasErased(UUID id) {
        return ERASED.containsKey(id);
    }

    // =====================================================================================
    //  Efeitos de cada aumento
    // =====================================================================================

    /** Erupção Tardia: aviso no chão; 1,5 s depois um gêiser de magma explode embaixo do alvo. */
    private static void aftershock(SpellContext ctx, LivingEntity t, float amount) {
        ServerLevel level = ctx.level();
        Vec3 mark = t.position();
        ElementFx.ring(level, mark, Element.MAGMA, 1.2, 12);
        level.sendParticles(ParticleTypes.LAVA, mark.x, mark.y + 0.1, mark.z, 3, 0.4, 0.05, 0.4, 0);
        GebUtil.sound(level, mark, SoundEvents.LAVA_POP, 0.9F, 0.7F);
        SpellScheduler.schedule(30, () -> {
            Vec3 at = t.isAlive() && t.level() == level && t.position().distanceToSqr(mark) < 64 ? t.position() : mark;
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 12, 0.5, 0.2, 0.5, 0);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.8, at.z, 26, 0.3, 1.0, 0.3, 0.06);
            ElementFx.pillar(level, at, Element.MAGMA.color, 0.8F, 4.0F, 14);
            GebUtil.sound(level, at, SoundEvents.BLAZE_SHOOT, 0.9F, 0.6F);
            for (LivingEntity e : GebUtil.enemiesNear(ctx, at.add(0, 0.5, 0), 1.8, null, 6)) {
                GebUtil.hit(ctx, e, amount * 0.4F, Element.MAGMA);
                if (e.isAlive() && !Statuses.isBossLike(e)) {
                    e.push(0, 0.75, 0);
                    e.hurtMarked = true;
                }
            }
        });
    }

    /** Cadeia: arcos para até 3 inimigos a ≤5 blocos (35% do golpe cada, Eletrizado pelo próprio raio). */
    private static void fork(SpellContext ctx, LivingEntity t, float amount) {
        ServerLevel level = ctx.level();
        Vec3 from = GebUtil.center(t);
        int n = 0;
        for (LivingEntity e : GebUtil.enemiesNear(ctx, from, 5.0, t, 3)) {
            Vec3 to = GebUtil.center(e);
            ElementFx.line(level, from, to, Element.LIGHTNING);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 8, 0.2, 0.3, 0.2, 0.1);
            GebUtil.hit(ctx, e, amount * 0.35F, Element.LIGHTNING);
            n++;
        }
        if (n > 0) GebUtil.sound(level, from, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.35F, 1.9F);
    }

    /** Sol Cativo: o alvo brilha e solta labaredas a cada 1 s por 4 s (Em Chamas nele e 15% do golpe em volta). */
    private static void captiveStar(SpellContext ctx, LivingEntity t, float amount) {
        ServerLevel level = ctx.level();
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false), ctx.caster());
        GebUtil.sound(level, t.position(), SoundEvents.FIRECHARGE_USE, 0.6F, 1.3F);
        for (int i = 1; i <= 4; i++) {
            SpellScheduler.schedule(20 * i, () -> {
                if (!t.isAlive() || t.level() != level) return;
                Vec3 c = GebUtil.center(t);
                ElementFx.ring(level, t.position().add(0, 0.2, 0), Element.SUN, 2.6, 16);
                level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.06);
                level.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 6, 0.5, 0.5, 0.5, 0.05);
                Statuses.applyUpTo(t, Kind.BURNING, 1, Statuses.DEFAULT_TICKS, ctx.caster(), ctx.powerMult(), 3);
                for (LivingEntity e : GebUtil.enemiesNear(ctx, c, 3.0, t, 5)) GebUtil.hit(ctx, e, amount * 0.15F, Element.SUN);
                GebUtil.sound(level, c, SoundEvents.BLAZE_SHOOT, 0.35F, 1.6F);
            });
        }
    }

    /** Semente Latente: 3 s depois brota — Enraizado + 30% do golpe; se o alvo já morreu, floresce e cura aliados. */
    private static void seed(SpellContext ctx, LivingEntity t, float amount) {
        ServerLevel level = ctx.level();
        Vec3 c = GebUtil.center(t);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0);
        GebUtil.sound(level, c, SoundEvents.CROP_PLANTED, 0.8F, 1.2F);
        SpellScheduler.schedule(60, () -> {
            Vec3 at = t.position();
            if (t.isAlive() && t.level() == level) {
                Statuses.apply(t, Kind.ROOTED, 1, 40, ctx.caster(), ctx.powerMult());
                GebUtil.hit(ctx, t, amount * 0.3F, Element.NATURE);
                level.sendParticles(Vfx.composter(), at.x, at.y + 0.3, at.z, 18, 0.5, 0.4, 0.5, 0.02);
                ElementFx.ring(level, at, Element.NATURE, 0.9, 10);
                GebUtil.sound(level, at, SoundEvents.BONE_MEAL_USE, 1.0F, 0.8F);
                return;
            }
            if (t.level() != level) return;
            // Floração: cura o conjurador e os aliados perto do lugar onde o alvo caiu
            SpellStats heal = new SpellStats();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(4.0),
                    x -> x.isAlive() && x.distanceToSqr(at) <= 16.0 && ctx.isAlly(x))) {
                ctx.heal(e, 3.0F, heal);
            }
            level.sendParticles(Vfx.cherry(), at.x, at.y + 0.5, at.z, 24, 1.2, 0.5, 1.2, 0.02);
            ElementFx.burst(level, at.add(0, 0.5, 0), Element.NATURE, 14, 0.6);
            GebUtil.sound(level, at, SoundEvents.BONE_MEAL_USE, 1.0F, 1.4F);
        });
    }

    /** Estilhaços: o gelo quebra e lança lascas em até 6 inimigos a ≤4 blocos (50% do golpe + Gelado). */
    private static void shatter(SpellContext ctx, LivingEntity t, float amount) {
        ServerLevel level = ctx.level();
        Statuses.clear(t, Kind.FROZEN);
        Vec3 c = GebUtil.center(t);
        level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 26, 0.4, 0.4, 0.4, 0.25);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                c.x, c.y, c.z, 20, 0.4, 0.4, 0.4, 0.3);
        GebUtil.sound(level, c, SoundEvents.GLASS_BREAK, 0.9F, 1.3F);
        for (LivingEntity e : GebUtil.enemiesNear(ctx, c, 4.0, t, 6)) {
            ElementFx.line(level, c, GebUtil.center(e), Element.ICE);
            GebUtil.hit(ctx, e, amount * 0.5F, Element.ICE);
            if (e.isAlive()) Statuses.apply(e, Kind.CHILLED, 1, Statuses.DEFAULT_TICKS, ctx.caster(), ctx.powerMult());
        }
    }

    /** Ablução: tira 1 efeito BOM do inimigo; cada um tirado limpa 1 efeito ruim seu e devolve 3% do mana. */
    private static void ablution(SpellContext ctx, LivingEntity t) {
        ServerLevel level = ctx.level();
        Vec3 c = GebUtil.center(t);
        MobEffectInstance best = strongest(t, MobEffectCategory.BENEFICIAL);
        if (best == null) {
            // Nada para lavar: a água benta só encharca
            Statuses.apply(t, Kind.WET, 1, Statuses.DEFAULT_TICKS, ctx.caster(), ctx.powerMult());
            level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.1);
            return;
        }
        t.removeEffect(best.getEffect());
        level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 16, 0.3, 0.4, 0.3, 0.1);
        level.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 10, 0.3, 0.5, 0.3, 0.06);
        GebUtil.sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.4F);
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive()) return;
        MobEffectInstance worst = strongest(caster, MobEffectCategory.HARMFUL);
        if (worst != null) {
            Kind k = Kind.of(worst.getEffect());
            if (k != null) Statuses.clear(caster, k);
            else caster.removeEffect(worst.getEffect());
        }
        if (caster instanceof ServerPlayer sp) {
            MagicData d = MagicApi.get(sp);
            d.addMana(d.maxMana() * 0.03F);
        }
        ElementFx.ring(level, caster.position(), Element.PURITY, 0.8, 12);
    }

    /** Efeito mais forte (amplificador, depois duração) da categoria, sem os infinitos (mecânicas de chefe/raça). */
    private static MobEffectInstance strongest(LivingEntity e, MobEffectCategory cat) {
        MobEffectInstance best = null;
        for (MobEffectInstance i : e.getActiveEffects()) {
            if (i.isInfiniteDuration() || i.getEffect().value().getCategory() != cat) continue;
            if (best == null || i.getAmplifier() > best.getAmplifier()
                    || (i.getAmplifier() == best.getAmplifier() && i.getDuration() > best.getDuration())) {
                best = i;
            }
        }
        return best;
    }

    /** Âncora Abissal: arrasta o alvo para baixo (voadores despencam) e o deixa lento. */
    private static void anchor(SpellContext ctx, LivingEntity t) {
        ServerLevel level = ctx.level();
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), ctx.caster());
        if (!Statuses.isBossLike(t)) {
            Vec3 v = t.getDeltaMovement();
            double down = t.onGround() ? -0.2 : -1.2;
            t.setDeltaMovement(v.x * 0.3, Math.min(v.y, down), v.z * 0.3);
            t.hurtMarked = true;
        }
        Vec3 c = GebUtil.center(t);
        level.sendParticles(Vfx.ink(), c.x, c.y + 0.4, c.z, 14, 0.3, 0.6, 0.3, 0.02);
        level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 10, 0.4, 0.5, 0.4, 0.05);
        ElementFx.ring(level, t.position(), Element.ABYSS, 1.0, 12);
        GebUtil.sound(level, c, SoundEvents.CONDUIT_ATTACK_TARGET, 0.6F, 0.6F);
    }

    /** Erosão: Exposto (até II) e desgaste da armadura do alvo. */
    private static void erosion(SpellContext ctx, LivingEntity t) {
        ServerLevel level = ctx.level();
        Statuses.applyUpTo(t, Kind.EXPOSED, 1, 100, ctx.caster(), ctx.powerMult(), 2);
        boolean worn = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack s = t.getItemBySlot(slot);
            if (!s.isEmpty() && s.isDamageableItem()) {
                s.hurtAndBreak(3, t, slot);
                worn = true;
            }
        }
        Vec3 c = GebUtil.center(t);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                c.x, c.y, c.z, 16, 0.35, 0.45, 0.35, 0.1);
        level.sendParticles(ElementFx.dust(Element.SAND, 1.0F), c.x, c.y, c.z, 8, 0.4, 0.4, 0.4, 0.02);
        GebUtil.sound(level, c, SoundEvents.SAND_BREAK, 0.8F, 0.8F);
        if (worn) GebUtil.sound(level, c, SoundEvents.GRINDSTONE_USE, 0.35F, 1.7F);
    }

    /** Ressonância: um cristal fica no ponto de impacto e, 1,5 s depois, repete os efeitos da magia a 30% (raio 3). */
    private static void resonance(SpellContext ctx, LivingEntity t) {
        ServerLevel level = ctx.level();
        Vec3 at = GebUtil.center(t);
        SpellContext echo = ctx.copy(ctx.powerMult() * 0.3F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_CLUSTER.defaultBlockState()),
                at.x, at.y, at.z, 12, 0.2, 0.3, 0.2, 0.05);
        GebUtil.sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F);
        for (int i = 1; i <= 2; i++) {
            SpellScheduler.schedule(10 * i, () -> {
                level.sendParticles(ElementFx.dust(Element.CRYSTAL, 1.2F), at.x, at.y, at.z, 6, 0.15, 0.25, 0.15, 0);
                level.sendParticles(Vfx.endRod(), at.x, at.y, at.z, 2, 0.1, 0.2, 0.1, 0.01);
            });
        }
        SpellScheduler.schedule(30, () -> {
            ElementFx.burst(level, at, Element.CRYSTAL, 18, 0.5);
            ElementFx.shockwave(level, at, Element.CRYSTAL.color, 3.0F, 10, null);
            GebUtil.sound(level, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.2F);
            List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.0),
                    e -> e.isAlive() && !e.isSpectator() && e.distanceToSqr(at) <= 9.0);
            int n = 0;
            for (LivingEntity e : near) {
                if (n >= 8) break;
                boolean any = false;
                for (ParsedSpell.EffectEntry entry : echo.spell().effects()) {
                    if (echo.isValidTarget(entry.effect(), entry.stats(), e)) {
                        echo.applyEffect(entry, e);
                        any = true;
                    }
                }
                if (any) {
                    n++;
                    ElementFx.line(level, at, GebUtil.center(e), Element.CRYSTAL);
                }
            }
        });
    }

    /** Colapso: puxa os inimigos a ≤5 blocos para o ponto de impacto; 0,5 s depois esmaga (40% do golpe + lentidão). */
    private static void collapse(SpellContext ctx, LivingEntity t, float amount) {
        ServerLevel level = ctx.level();
        Vec3 p = GebUtil.center(t);
        for (LivingEntity e : GebUtil.enemiesNear(ctx, p, 5.0, t, 8)) {
            if (Statuses.isBossLike(e)) continue;
            Vec3 to = p.subtract(e.position());
            double d = to.length();
            if (d < 0.6) continue;
            Vec3 v = to.normalize().scale(Math.min(1.3, 0.3 + d * 0.2));
            e.setDeltaMovement(v.x, Math.max(0.1, v.y * 0.5 + 0.15), v.z);
            e.hurtMarked = true;
        }
        level.sendParticles(Vfx.portal(), p.x, p.y, p.z, 36, 2.0, 1.0, 2.0, 0.6);
        ElementFx.ring(level, t.position(), Element.GRAVITY, 5.0, 24);
        GebUtil.sound(level, p, SoundEvents.BEACON_DEACTIVATE, 0.8F, 0.5F);
        SpellScheduler.schedule(10, () -> {
            ElementFx.shockwave(level, p, Element.GRAVITY.color, 2.8F, 10, null);
            ElementFx.burst(level, p, Element.GRAVITY, 16, 0.4);
            GebUtil.sound(level, p, SoundEvents.ANVIL_LAND, 0.5F, 0.6F);
            for (LivingEntity e : GebUtil.enemiesNear(ctx, p, 2.8, null, 8)) {
                GebUtil.hit(ctx, e, amount * 0.4F, Element.GRAVITY);
                if (e.isAlive()) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), ctx.caster());
            }
        });
    }

    /** Passo Estelar: no tick seguinte, você surge atrás do alvo (≤24 blocos), virado para ele. */
    private static void starstep(ServerPlayer sp, LivingEntity t) {
        SpellScheduler.schedule(1, () -> {
            if (!sp.isAlive() || sp.isRemoved() || sp.level() != t.level() || sp.isPassenger()) return;
            ServerLevel level = sp.serverLevel();
            Vec3 tp = t.position();
            if (sp.position().distanceToSqr(tp) > 24 * 24) return;
            Vec3 dir = tp.subtract(sp.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() < 1.0E-4) dir = sp.getLookAngle().multiply(1, 0, 1);
            if (dir.lengthSqr() < 1.0E-4) return;
            dir = dir.normalize();
            double off = t.getBbWidth() * 0.5 + 1.0;
            Vec3[] options = {tp.add(dir.scale(off)), tp.add(dir.yRot(Mth.HALF_PI).scale(off)),
                    tp.add(dir.yRot(-Mth.HALF_PI).scale(off)), tp.subtract(dir.scale(off))};
            for (Vec3 dest : options) {
                AABB box = sp.getBoundingBox().move(dest.subtract(sp.position()));
                if (!level.noCollision(sp, box)) continue;
                Vec3 from = sp.position();
                level.sendParticles(Vfx.endRod(), from.x, from.y + 1.0, from.z, 18, 0.3, 0.6, 0.3, 0.05);
                ElementFx.line(level, from.add(0, 1.0, 0), dest.add(0, 1.0, 0), Element.ASTRAL);
                Vec3 look = tp.subtract(dest);
                float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
                sp.teleportTo(level, dest.x, dest.y, dest.z, yaw, sp.getXRot());
                sp.resetFallDistance();
                level.sendParticles(Vfx.firework(), dest.x, dest.y + 1.0, dest.z, 16, 0.3, 0.6, 0.3, 0.08);
                ElementFx.ring(level, dest, Element.ASTRAL, 0.9, 12);
                GebUtil.sound(level, dest, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 1.5F);
                GebTriggers.teleported(sp);
                return;
            }
        });
    }

    /** Aniquilar (abate): implosão e mana para o conjurador (4% do máx + 25% da vida da vítima, até +15%). */
    private static void voidFeast(SpellContext ctx, LivingEntity t, long now) {
        ServerLevel level = ctx.level();
        Vec3 c = GebUtil.center(t);
        level.sendParticles(Vfx.reversePortal(), c.x, c.y, c.z, 28, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 10, 0.2, 0.3, 0.2, 0.02);
        GebUtil.sound(level, c, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 0.5F);
        if (ctx.caster() instanceof ServerPlayer sp && !(sp instanceof FakePlayer)
                && GebUtil.ready("voidmana|" + sp.getUUID(), now, 7)) {
            MagicData d = MagicApi.get(sp);
            float gain = d.maxMana() * 0.04F + Math.min(t.getMaxHealth() * 0.25F, d.maxMana() * 0.15F);
            d.addMana(gain);
            ElementFx.line(level, c, GebUtil.center(sp), Element.VOID);
        }
    }

    /** Maré Crepuscular: Aurora = cura 15% do dano causado; Ocaso = Corrompido (até III) e Exposto I. */
    private static void tide(SpellContext ctx, LivingEntity t, float dealt, boolean alive) {
        LivingEntity caster = ctx.caster();
        UUID id = ctx.casterId();
        boolean dusk = id != null && DUSK.getOrDefault(id, false);
        ServerLevel level = ctx.level();
        Vec3 c = GebUtil.center(t);
        if (dusk) {
            if (!alive) return;
            Statuses.applyUpTo(t, Kind.CORRUPTED, 1, Statuses.DEFAULT_TICKS, caster, ctx.powerMult(), 3);
            Statuses.applyUpTo(t, Kind.EXPOSED, 1, 60, caster, ctx.powerMult(), 1);
            level.sendParticles(Vfx.witch(), c.x, c.y, c.z, 6, 0.3, 0.4, 0.3, 0.02);
        } else if (caster != null && caster.isAlive() && dealt > 0) {
            caster.heal(dealt * 0.15F);
            level.sendParticles(Vfx.endRod(), caster.getX(), caster.getY() + 1.0, caster.getZ(), 4, 0.3, 0.5, 0.3, 0.02);
        }
    }

    // =====================================================================================
    //  Limpeza
    // =====================================================================================
    static void sweep(long now) {
        HELL.values().removeIf(m -> m.until() < now);
        ERASE_CANDIDATE.values().removeIf(t -> t < now);
        ERASED.values().removeIf(t -> t < now);
        SHATTER.clear();
    }

    static void forget(UUID player) {
        DUSK.remove(player);
    }

    static void clear() {
        HELL.clear();
        ERASE_CANDIDATE.clear();
        ERASED.clear();
        SHATTER.clear();
        DUSK.clear();
    }

    private GebAugments() {}
}
