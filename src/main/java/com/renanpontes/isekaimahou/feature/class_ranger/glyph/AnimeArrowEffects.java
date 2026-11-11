package com.renanpontes.isekaimahou.feature.class_ranger.glyph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerCompanions;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerFx;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerGlyphIds;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.ChainTetherEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerWolfEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Efeitos do pacote de técnicas de anime do Ranger (glifos de origem; combinam com Flecha, Disparo Triplo, Linha de
 * Bala ou qualquer forma):
 * <ul>
 *   <li><b>Flecha Sagrada</b> ({@code ranger_sacred_arrow}, rank I — homenagem à sacerdotisa arqueira que viajou no
 *   tempo): luz rosada que PURIFICA;</li>
 *   <li><b>Correntes Conjuradas</b> ({@code ranger_conjured_chain}, rank III — homenagem ao caçador das correntes):
 *   prende, rouba e cura;</li>
 *   <li><b>Despertar da Deusa Loba</b> ({@code ranger_wolf_god}, rank V — homenagem à loba gigante da floresta): o Lobo
 *   companheiro vira a fera divina.</li>
 * </ul>
 */
public final class AnimeArrowEffects {
    private AnimeArrowEffects() {}

    static Vec3 mid(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Criatura "impura" (mortos-vivos, quem tem Corrupção): a luz sagrada fere muito mais. */
    public static boolean impure(LivingEntity e) {
        return e.getType().is(EntityTypeTags.UNDEAD) || e.isInvertedHealAndHarm() || Statuses.has(e, Statuses.Kind.CORRUPTED);
    }

    /** Tira até {@code n} efeitos ruins (mais a Corrupção, sempre). */
    static int cleanse(LivingEntity e, int n) {
        int removed = 0;
        for (MobEffectInstance i : new ArrayList<>(e.getActiveEffects())) {
            if (removed >= n) break;
            if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                e.removeEffect(i.getEffect());
                removed++;
            }
        }
        if (Statuses.has(e, Statuses.Kind.CORRUPTED)) Statuses.clear(e, Statuses.Kind.CORRUPTED);
        return removed;
    }

    // =====================================================================================
    //  Flecha Sagrada
    // =====================================================================================
    /**
     * Flecha Sagrada: 7 de dano de Luz; criaturas impuras (mortos-vivos, Corrompidas) levam ×2,5 e perdem TODOS os
     * efeitos bons. No impacto, uma onda de purificação (raio 3, Expandir) tira efeitos ruins dos aliados e cura um pouco.
     * Em você/aliado: purifica e cura.
     */
    public static class SacredArrow extends EffectGlyph {
        public static final float DAMAGE = 7F;
        public static final float RADIUS = 3F;
        private static final Map<UUID, Object[]> LAST = new HashMap<>();

        public SacredArrow() {
            super(RangerGlyphIds.SACRED, 14);
            origin(MagicPath.RANGER);
            element(Element.LIGHT);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float baseHeal() { return 3F; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
            ServerLevel level = ctx.level();
            if (target == ctx.caster() || ctx.isAlly(target)) {
                cleanse(le, 2 + Math.max(0, stats.amplify));
                ctx.heal(le, 3F, stats);
                light(level, mid(le), 1.0);
                return;
            }
            boolean impure = impure(le);
            le.invulnerableTime = 0;
            ctx.damage(le, impure ? DAMAGE * 2.5F : DAMAGE, stats, null, Element.LIGHT);
            if (impure) {
                for (MobEffectInstance i : new ArrayList<>(le.getActiveEffects())) {
                    if (i.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) le.removeEffect(i.getEffect());
                }
                le.igniteForSeconds(3);
            }
            burst(ctx, mid(le), stats, impure);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            burst(ctx, hit.getLocation(), stats, false);
        }

        /** Onda de purificação: fere os impuros em volta e limpa/cura os aliados. Uma por conjurador e ponto no tick. */
        private static void burst(SpellContext ctx, Vec3 at, SpellStats stats, boolean strong) {
            if (!ArrowEffects.firstAt(LAST, ctx, at, 2.0)) return;
            ServerLevel level = ctx.level();
            float r = stats.radius(RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
                    x -> x.isAlive() && x.distanceToSqr(at) <= r * r)) {
                if (e == ctx.caster() || ctx.isAlly(e)) {
                    cleanse(e, 1 + Math.max(0, stats.amplify));
                    ctx.heal(e, 1.5F, stats);
                } else if (impure(e)) {
                    e.invulnerableTime = 0;
                    ctx.damage(e, DAMAGE * 0.8F, stats, null, Element.LIGHT);
                }
            }
            light(level, at, r);
            if (strong) level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }

        /** Coluna de luz rosa-perolada e anel de purificação. */
        private static void light(ServerLevel level, Vec3 at, double r) {
            for (int y = 0; y < 8; y++) {
                level.sendParticles(WeaponFx.dust(y % 2 == 0 ? 0xFFB8E8 : 0xFFFFFF, 1.2F), at.x, at.y + y * 0.4, at.z, 2, 0.08, 0.1, 0.08, 0);
            }
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 14, 0.3, 0.8, 0.3, 0.04);
            RangerFx.ring(level, at.add(0, 0.1, 0), r, 28, WeaponFx.dust(0xFF9AD8, 1.3F), 0);
            RangerFx.ring(level, at.add(0, 0.4, 0), r * 0.6, 20, WeaponFx.dust(0xFFFFFF, 1.0F), 0);
            RangerFx.sound(level, at, SoundEvents.BEACON_ACTIVATE, 0.8F, 1.8F);
            RangerFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
            RangerFx.sound(level, at, SoundEvents.BELL_RESONATE, 0.5F, 1.6F);
        }

        public static void reset() {
            LAST.clear();
        }
    }

    // =====================================================================================
    //  Correntes Conjuradas
    // =====================================================================================
    /**
     * Correntes Conjuradas. Num inimigo: PRISÃO (4 de dano; preso no lugar, lento e sem teletransporte por 5 s, Estender;
     * Enraizado e Silenciado por um instante) + ROUBO (tira 1 efeito bom, +1 por Amplificar, e passa para você; de
     * jogadores rouba 10% da mana). Em você/aliado: CORRENTE SAGRADA — cura 6 e tira 2 efeitos ruins (+1 por Amplificar).
     */
    public static class ConjuredChains extends EffectGlyph {
        public static final float DAMAGE = 4F;
        public static final float HEAL = 6F;
        public static final int BIND = 100;

        public ConjuredChains() {
            super(RangerGlyphIds.CHAINS, 16);
            origin(MagicPath.RANGER);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float baseHeal() { return HEAL; }

        @Override
        public int hintDuration() { return BIND; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (caster == null || !(target instanceof LivingEntity le) || !le.isAlive()) return;
            ServerLevel level = ctx.level();
            if (target == caster || ctx.isAlly(target)) {
                ctx.heal(le, HEAL, stats);
                cleanse(le, 2 + Math.max(0, stats.amplify));
                if (le != caster) ChainTetherEntity.holy(level, caster, le);
                level.sendParticles(WeaponFx.dust(0xFFD86B, 1.2F), le.getX(), le.getY() + 1, le.getZ(), 16, 0.4, 0.6, 0.4, 0);
                RangerFx.sound(level, le.position(), SoundEvents.CHAIN_PLACE, 1.0F, 1.4F);
                RangerFx.sound(level, le.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.6F);
                return;
            }
            le.invulnerableTime = 0;
            ctx.damage(le, DAMAGE, stats, null);
            int ticks = stats.duration(BIND);
            ChainTetherEntity.bind(level, caster, le, ticks);
            Statuses.apply(le, Statuses.Kind.ROOTED, 1, Math.min(ticks, 60), caster, ctx.powerMult());
            Statuses.apply(le, Statuses.Kind.SILENCED, 1, 40, caster, ctx.powerMult());
            int stolen = steal(caster, le, 1 + Math.max(0, stats.amplify));
            if (caster instanceof ServerPlayer sp && le instanceof ServerPlayer tp) {
                MagicData their = MagicApi.get(tp);
                float mana = their.mana() * 0.10F;
                their.setMana(their.mana() - mana);
                MagicApi.get(sp).addMana(mana);
            }
            if (caster instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_ranger.chain_bound", ticks / 20, stolen), true);
            }
            Vec3 c = mid(le);
            RangerFx.ring(level, c.add(0, -0.3, 0), Math.max(0.7, le.getBbWidth()), 18, WeaponFx.dust(0xC8CCD6, 1.1F), 0);
            RangerFx.ring(level, c.add(0, 0.3, 0), Math.max(0.7, le.getBbWidth()), 18, WeaponFx.dust(0xC8CCD6, 1.1F), 0);
            RangerFx.sound(level, c, SoundEvents.CHAIN_PLACE, 1.2F, 0.7F);
            RangerFx.sound(level, c, SoundEvents.ANVIL_LAND, 0.4F, 1.8F);
        }

        /** Rouba até {@code n} efeitos bons (o vencedor recebe o resto do tempo, até 60 s). */
        private static int steal(LivingEntity thief, LivingEntity victim, int n) {
            List<MobEffectInstance> good = new ArrayList<>();
            for (MobEffectInstance i : victim.getActiveEffects()) {
                if (i.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL && !i.isInfiniteDuration()) good.add(i);
            }
            int k = 0;
            for (; k < n && k < good.size(); k++) {
                MobEffectInstance i = good.get(k);
                MobEffectInstance copy = new MobEffectInstance(i.getEffect(), Math.min(1200, i.getDuration()), i.getAmplifier(), i.isAmbient(),
                        i.isVisible(), i.showIcon());
                victim.removeEffect(i.getEffect());
                thief.addEffect(copy, thief);
            }
            return k;
        }
    }

    // =====================================================================================
    //  Despertar da Deusa Loba
    // =====================================================================================
    /**
     * Despertar da Deusa Loba: o Lobo do Ranger (chamado se precisar) cresce até virar a fera divina da floresta — branca,
     * GIGANTE (×2,5), com muito mais vida, mordida e armadura, mordidas em área, uivo que aterroriza os inimigos a cada
     * 5 s e a Bênção da Floresta (Regeneração) no dono por perto. 30 s (Estender). Num inimigo: a Deusa já desperta
     * atacando ele. Só jogadores (o Lobo é companheiro).
     */
    public static class WolfGod extends EffectGlyph {
        public static final int TICKS = 600;

        public WolfGod() {
            super(RangerGlyphIds.WOLF_GOD, 40);
            origin(MagicPath.RANGER);
            tier(5);
            minCircle(8);
        }

        @Override
        public boolean isBeneficial() { return false; }

        @Override
        public int hintDuration() { return TICKS; }

        @Override
        public float baseDamage() { return 14F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(ctx.caster() instanceof ServerPlayer sp)) return;
            boolean self = target == sp;
            if (!self && (ctx.isAlly(target) || !(target instanceof LivingEntity))) return;
            if (!RangerCompanions.call(sp, self ? null : (LivingEntity) target, stats.power())) return;
            if (!(RangerCompanions.find(sp, RangerCompanions.WOLF) instanceof RangerWolfEntity wolf) || wolf.level() != sp.level()) return;
            boolean was = wolf.isGod();
            int ticks = stats.duration(TICKS);
            wolf.awaken(ticks, stats.power());
            if (!self && target instanceof LivingEntity le) wolf.command(le);
            if (!was && !ctx.formStats().silent) {
                Announce.send(sp, Component.translatable("announce.isekai_mahou.class_ranger.wolf_god"), 0xF4F4FF, Announce.STYLE_ULTIMATE);
            }
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_ranger.wolf_god", ticks / 20), true);
        }
    }
}
