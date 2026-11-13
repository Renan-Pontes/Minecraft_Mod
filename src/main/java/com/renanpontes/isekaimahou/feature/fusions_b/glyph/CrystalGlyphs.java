package com.renanpontes.isekaimahou.feature.fusions_b.glyph;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.CrystalPrisonEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.CrystalShardEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos de CRISTAL (Terra + Luz).
 * Tier 1: Estilhaços · Tier 2: Cristalizar · Tier 3: Armadura de Cristal · Tier 4: Prisma Refletor.
 */
public final class CrystalGlyphs {

    public static void register() {
        GlyphRegistry.register(new Shards());
        GlyphRegistry.register(new Crystallize());
        GlyphRegistry.register(new CrystalArmor());
        GlyphRegistry.register(new ReflectingPrism());
    }

    /**
     * Estilhaços: o alvo leva dano e o cristal se parte em um leque de fragmentos que seguem em frente,
     * ferindo quem estiver atrás. Em Si Mesmo vira uma "escopeta" de cristal na direção do olhar.
     * Dividir = +2 fragmentos.
     */
    public static class Shards extends EffectGlyph {
        public Shards() {
            super("shards", 12);
            element(Element.CRYSTAL);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            boolean self = FbUtil.isSelf(ctx, target);
            if (!FbUtil.firstHit(ctx, "shards", target)) return;
            int n = 5 + 2 * Math.max(0, stats.split);
            // Só os aumentos do efeito: o fragmento passa por ctx.damage, que aplica a conjuração e o poder mágico
            float dmg = 2.5F * stats.power();
            float speed = 1.3F * stats.speedMult();
            Vec3 origin;
            Vec3 dir;
            Entity excluded = null;
            if (self) {
                origin = ctx.eyePos().add(ctx.look().scale(0.6)).subtract(0, 0.15, 0);
                dir = ctx.look();
            } else {
                FbUtil.hurt(ctx, target, 3.0F, stats, null);
                origin = FbUtil.center(target);
                Vec3 from = ctx.caster() != null ? ctx.caster().getEyePosition() : origin.subtract(ctx.look());
                dir = origin.subtract(from);
                dir = dir.lengthSqr() < 1.0E-4 ? ctx.look() : dir.normalize();
                excluded = target;
            }
            // Leque de 70° em volta da direção, com um pouco de variação vertical
            for (int i = 0; i < n; i++) {
                float off = n == 1 ? 0F : (i / (float) (n - 1) - 0.5F) * (float) Math.toRadians(70);
                Vec3 d = dir.yRot(off).add(0, (sl.random.nextFloat() - 0.4F) * 0.15F, 0).normalize();
                sl.addFreshEntity(CrystalShardEntity.create(ctx, origin.add(d.scale(0.4)), d, speed, dmg, excluded, stats));
            }
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.AMETHYST_SHARD)),
                    origin.x, origin.y, origin.z, 16, 0.25, 0.25, 0.25, 0.15);
            FbFx.burst(sl, origin, Element.CRYSTAL, 12, 0.3);
            FbFx.sound(sl, origin, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 1.0F);
            FbFx.sound(sl, origin, SoundEvents.GLASS_BREAK, 0.7F, 1.4F);
        }
    }

    /**
     * Cristalizar: prende o alvo dentro de um cristal (não anda, não pula, quase não ataca) por 3s.
     * Ao fim o cristal estilhaça e fere. Chefes ficam presos só 1/3 do tempo. Em Si Mesmo: cristaliza os inimigos em volta.
     */
    public static class Crystallize extends EffectGlyph {
        public Crystallize() {
            super("crystallize", 16);
            element(Element.CRYSTAL);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            for (LivingEntity e : FbUtil.offensiveTargets(ctx, target, stats.radius(3F))) {
                if (FbUtil.untouchable(e)) continue;
                int ticks = stats.duration(60);
                if (FbUtil.isBoss(e)) ticks = Math.max(10, ticks / 3);
                FbUtil.hurt(ctx, e, 2.0F, stats, null);
                if (!e.isAlive()) continue;
                e.addEffect(new MobEffectInstance(FbRegistries.CRYSTALLIZED, ticks, 0, false, false, true), ctx.caster());
                CrystalPrisonEntity.encase(ctx, e, ticks, 3.0F * stats.power(), stats);
            }
        }
    }

    /**
     * Armadura de Cristal: +armadura e +resistência; quem golpeia corpo a corpo recebe de volta 30% do dano.
     * Invertido em inimigos: Cristal Frágil (ficam Expostos, recebendo mais dano mágico).
     */
    public static class CrystalArmor extends EffectGlyph {
        public CrystalArmor() {
            super("crystal_armor", 18);
            element(Element.CRYSTAL);
            tier(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            Vec3 c = FbUtil.center(le);
            if (le != ctx.caster() && !ctx.isAlly(le)) {
                le.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, stats.duration(200), Math.min(1, FbUtil.amp(ctx, stats))), ctx.caster());
                sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.AMETHYST_SHARD)), c.x, c.y, c.z,
                        14, 0.3, 0.4, 0.3, 0.1);
                FbFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.7F);
                return;
            }
            int amp = Math.min(2, FbUtil.amp(ctx, stats));
            le.addEffect(new MobEffectInstance(FbRegistries.CRYSTAL_ARMOR, stats.duration(600), amp), ctx.caster());
            // Cristais subindo em espiral pelo corpo
            for (int i = 0; i < 16; i++) {
                double a = i * 0.8, y = le.getY() + le.getBbHeight() * i / 16.0;
                double r = le.getBbWidth() * 0.7 + 0.2;
                sl.sendParticles(FbFx.fade(FbFx.CRYSTAL_LIGHT, FbFx.CRYSTAL, 1.1F), le.getX() + Math.cos(a) * r, y,
                        le.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            sl.sendParticles(Vfx.waxOn(), c.x, c.y, c.z, 10, 0.4, 0.6, 0.4, 0.05);
            FbFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.0F);
            FbFx.sound(sl, c, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0F, 0.9F);
        }
    }

    /**
     * Prisma Refletor: por 10s, flechas, bolas de fogo e outros projéteis que viriam no alvo são devolvidos a
     * quem atirou (magias de outros conjuradores são absorvidas pelo cristal). Invertido em inimigos: clarão
     * do prisma que cega por um instante.
     */
    public static class ReflectingPrism extends EffectGlyph {
        public ReflectingPrism() {
            super("reflecting_prism", 22);
            element(Element.CRYSTAL);
            tier(4);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            Vec3 c = FbUtil.center(le);
            for (int col : FbFx.RAINBOW) {
                sl.sendParticles(FbFx.dust(col, 1.2F), c.x, c.y, c.z, 5, le.getBbWidth() * 0.7, le.getBbHeight() * 0.4,
                        le.getBbWidth() * 0.7, 0);
            }
            if (le != ctx.caster() && !ctx.isAlly(le)) {
                le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, stats.duration(40), 0), ctx.caster());
                FbUtil.forgetTarget(le);
                sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                FbFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.8F);
                return;
            }
            le.addEffect(new MobEffectInstance(FbRegistries.PRISM_REFLECTION, stats.duration(200), 0), ctx.caster());
            FbFx.ring(sl, le.position().add(0, 0.1, 0), Vfx.endRod(), le.getBbWidth() + 0.8, 16, 0.01);
            FbFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.5F);
            FbFx.sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.8F);
        }
    }

    private CrystalGlyphs() {}
}
