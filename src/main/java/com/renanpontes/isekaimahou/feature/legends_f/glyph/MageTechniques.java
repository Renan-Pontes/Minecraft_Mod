package com.renanpontes.isekaimahou.feature.legends_f.glyph;

import java.util.List;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_f.LfNet;
import com.renanpontes.isekaimahou.feature.legends_f.LfRegistry;
import com.renanpontes.isekaimahou.feature.legends_f.LfStates;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.feature.legends_f.entity.BarrierEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Técnicas de origem do MAGO (homenagem a Frieren): Detectar Mana, Ocultar Mana e Barreira Defensiva Geral.
 * (Zoltraak já existe como Magia Lendária; estas completam o "kit" da maga elfa.)
 */
public final class MageTechniques {
    public static final int COLOR = 0x8FE6FF;

    public static List<EffectGlyph> all() {
        return List.of(new ManaSense(), new ConcealMana(), new GeneralBarrier());
    }

    // =====================================================================================
    //  Detectar Mana
    // =====================================================================================
    /**
     * Um pulso de mana se espalha (24 blocos; Expandir aumenta): toda criatura com mana à vista fica com o contorno
     * brilhando por 10 s e você vê a "chama" da mana de cada uma, colorida pela força (verde fraca → amarela → vermelha
     * → roxa = chefe). Diz quantas presenças e qual a mais forte. Quem está com Mana Oculta NÃO aparece.
     */
    public static class ManaSense extends EffectGlyph {
        public ManaSense() {
            super("mage_mana_sense", 8);
            element(Element.ARCANE);
            origin(MagicPath.MAGE);
            tier(2);
            minCircle(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float hintRadius() { return 24F; }

        @Override
        public int hintDuration() { return 200; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            pulse(ctx, target == ctx.caster() ? ctx.caster().position() : target.position(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            pulse(ctx, hit.getLocation(), stats);
        }

        private void pulse(SpellContext ctx, Vec3 c, SpellStats stats) {
            if (!LfUtil.oncePerTick(ctx, "mana_sense")) return;
            ServerLevel level = ctx.level();
            float radius = Mth.clamp(stats.radius(24F), 6F, 64F);
            int ticks = stats.duration(200);
            LfUtil.fx(level, LfNet.PULSE, c.add(0, 1, 0), new Vec3(0, 1, 0), COLOR, 26, radius);
            LfUtil.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.8F);
            LfUtil.sound(level, c, SoundEvents.BEACON_AMBIENT, 1.2F, 1.8F);
            ServerPlayer sp = ctx.caster() instanceof ServerPlayer p ? p : null;
            LivingEntity strongest = null;
            int found = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius),
                    e -> e.isAlive() && e != ctx.caster() && !e.isSpectator() && e.distanceToSqr(c) <= radius * radius)) {
                if (e.hasEffect(LfRegistry.MANA_CONCEALED) && !ctx.isAlly(e)) continue;
                found++;
                if (!ctx.isAlly(e)) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), ctx.caster());
                if (strongest == null || e.getMaxHealth() > strongest.getMaxHealth()) strongest = e;
                if (sp != null) {
                    int rgb = signature(e);
                    DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F,
                            ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), 1.6F);
                    level.sendParticles(sp, dust, true, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 8, 0.15, 0.4, 0.15, 0);
                }
            }
            if (sp == null) return;
            if (strongest == null) {
                LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.sense_none").withStyle(ChatFormatting.AQUA));
            } else {
                LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.sense_found", found,
                        strongest.getDisplayName(), Math.round(strongest.getMaxHealth())).withStyle(ChatFormatting.AQUA));
            }
        }

        /** Cor da mana pela força (vida máxima). */
        static int signature(LivingEntity e) {
            if (Statuses.isBossLike(e)) return 0xB050FF;
            float hp = e.getMaxHealth();
            if (hp >= 80) return 0xFF4A3A;
            if (hp >= 35) return 0xFFD84A;
            return 0x5AE07A;
        }
    }

    // =====================================================================================
    //  Ocultar Mana
    // =====================================================================================
    /**
     * Suprime a mana (60 s): criaturas a mais de 5 blocos não te notam (e quem já te caçava de longe perde o rastro),
     * Detectar Mana não te acha, e o PRIMEIRO golpe que você der revela a mana de uma vez — magia ×1,6, golpe ×1,3 —
     * e o efeito acaba. ("Ela escondeu a mana a vida inteira.")
     */
    public static class ConcealMana extends EffectGlyph {
        public ConcealMana() {
            super("mage_conceal_mana", 10);
            element(Element.ARCANE);
            origin(MagicPath.MAGE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return 1200; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            int ticks = stats.duration(1200);
            le.addEffect(new MobEffectInstance(LfRegistry.MANA_CONCEALED, ticks, 0, false, false, true), ctx.caster());
            LfStates.set(le, LfNet.S_CONCEAL, ticks, 0);
            ServerLevel level = ctx.level();
            // Quem já caçava de longe perde o rastro
            for (Mob m : level.getEntitiesOfClass(Mob.class, le.getBoundingBox().inflate(32), m -> m.getTarget() == le)) {
                if (m.distanceToSqr(le) > 5 * 5 && m.getLastHurtByMob() != le) m.setTarget(null);
            }
            LfUtil.fx(level, LfNet.RING, le.position().add(0, 1, 0), new Vec3(0, 1, 0), 0x5A7A88, 16, -2.5F);
            LfUtil.sound(level, le.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.8F, 1.4F);
            LfUtil.actionbar(le, Component.translatable("message.isekai_mahou.legends_f.conceal_on", ticks / 20).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    // =====================================================================================
    //  Barreira Defensiva Geral
    // =====================================================================================
    /**
     * Ergue uma cúpula de hexágonos de mana em volta do alvo (raio 3,5; 12 s): projéteis inimigos se desfazem na
     * parede, monstros não entram e golpes/magias vindos de fora em quem está dentro batem na barreira. Ela aguenta
     * 135 de dano (×poder) antes de se estilhaçar.
     */
    public static class GeneralBarrier extends EffectGlyph {
        public static final float HP = 90F;

        public GeneralBarrier() {
            super("mage_general_barrier", 24);
            element(Element.ARCANE);
            origin(MagicPath.MAGE);
            tier(4);
            minCircle(4);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public float hintRadius() { return 3.5F; }

        @Override
        public int hintDuration() { return 240; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !LfUtil.oncePerTick(ctx, "barrier:" + le.getId())) return;
            float radius = Mth.clamp(stats.radius(3.5F), 2F, 10F);
            float hp = HP * LfUtil.power(ctx, stats) * Progression.DAMAGE_SCALE;
            int life = Mth.clamp(stats.duration(240), 60, 1200);
            BarrierEntity.spawn(ctx, le.position(), radius, hp, life, stats);
            LfUtil.banner(le, "announce.isekai_mahou.legends_f.barrier", COLOR, false);
        }
    }

    private MageTechniques() {}
}
