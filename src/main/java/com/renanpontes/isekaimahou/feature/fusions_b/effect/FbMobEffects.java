package com.renanpontes.isekaimahou.feature.fusions_b.effect;

import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCure;

/**
 * Efeitos de status das fusões B. Todos rodam a lógica só no servidor (no cliente, o tick do efeito
 * acontece apenas para o próprio jogador e é ignorado aqui).
 */
public final class FbMobEffects {

    /** Efeito base com construtor público. */
    public static class FbEffect extends MobEffect {
        public FbEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    // =====================================================================================
    //  Fábricas dos efeitos só com atributos
    // =====================================================================================

    public static MobEffect crystallized() {
        return new Crystallized()
                .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.crystallized"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                .addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("effect.crystallized"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                .addAttributeModifier(Attributes.FLYING_SPEED, IsekaiMahou.id("effect.crystallized"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.crystallized"), -0.9, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    public static MobEffect crystalArmor() {
        return new CrystalArmor()
                .addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.crystal_armor"), 4.0, AttributeModifier.Operation.ADD_VALUE)
                .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id("effect.crystal_armor"), 2.0, AttributeModifier.Operation.ADD_VALUE)
                .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.crystal_armor"), 0.15, AttributeModifier.Operation.ADD_VALUE);
    }

    /** Gravidade zero: o modificador não escala com o nível (nível 2 inverteria a gravidade). */
    public static MobEffect zeroGravity() {
        return new ZeroGravity()
                .addAttributeModifier(Attributes.GRAVITY, IsekaiMahou.id("effect.zero_gravity"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amp -> -1.0);
    }

    public static MobEffect bloodPact() {
        return new BloodPact()
                .addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("effect.blood_pact"), 2.0, AttributeModifier.Operation.ADD_VALUE)
                .addAttributeModifier(CoreRegistries.SPELL_POWER, IsekaiMahou.id("effect.blood_pact"), 0.15, AttributeModifier.Operation.ADD_VALUE)
                .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.blood_pact"), 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    public static MobEffect inked() {
        return new Inked()
                .addAttributeModifier(Attributes.FOLLOW_RANGE, IsekaiMahou.id("effect.inked"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amp -> -0.8);
    }

    // =====================================================================================
    //  Cristal
    // =====================================================================================

    /** Cristalizado: parado dentro do cristal (a prisão visual é a CrystalPrisonEntity). */
    public static class Crystallized extends FbEffect {
        public Crystallized() { super(MobEffectCategory.HARMFUL, FbFx.CRYSTAL); }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (e.level().isClientSide) return true;
            Vec3 v = e.getDeltaMovement();
            // Não desliza nem voa dentro do cristal (a gravidade continua puxando para baixo)
            if (!(e instanceof Player)) e.setDeltaMovement(0, Math.min(0, v.y), 0);
            if (e instanceof Mob m) m.getNavigation().stop();
            return true;
        }
    }

    /** Armadura de Cristal: brilho de cristal em volta (o reflexo de dano está em FbEvents). */
    public static class CrystalArmor extends FbEffect {
        public CrystalArmor() { super(MobEffectCategory.BENEFICIAL, FbFx.CRYSTAL); }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 12 == 0; }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (e.level() instanceof ServerLevel sl) {
                FbFx.around(sl, e, ParticleTypes.WAX_ON, 3, 0.02);
                FbFx.around(sl, e, FbFx.dust(FbFx.CRYSTAL_LIGHT, 0.8F), 3, 0);
            }
            return true;
        }
    }

    /** Prisma Refletor: a cada tick procura projéteis vindo na direção do alvo e os devolve. */
    public static class PrismReflection extends FbEffect {
        public PrismReflection() { super(MobEffectCategory.BENEFICIAL, 0xF0D8FF); }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            for (Projectile p : sl.getEntitiesOfClass(Projectile.class, e.getBoundingBox().inflate(4.5),
                    p -> p.isAlive() && Prism.threatens(p, e))) {
                Prism.reflect(sl, p, e);
            }
            if (e.tickCount % 5 == 0) {
                // Três pontos do arco-íris girando em volta (o "prisma")
                double a = e.tickCount * 0.25;
                for (int i = 0; i < 3; i++) {
                    double ang = a + i * Math.PI * 2 / 3;
                    int c = FbFx.RAINBOW[(e.tickCount / 5 + i * 2) % FbFx.RAINBOW.length];
                    sl.sendParticles(FbFx.dust(c, 0.9F), e.getX() + Math.cos(ang) * (e.getBbWidth() + 0.5),
                            e.getY() + e.getBbHeight() * 0.6, e.getZ() + Math.sin(ang) * (e.getBbWidth() + 0.5), 1, 0, 0, 0, 0);
                }
            }
            return true;
        }
    }

    // =====================================================================================
    //  Astral
    // =====================================================================================

    /** Gravidade Zero: sem dano de queda enquanto flutua; mobs sobem devagar e ficam à deriva. */
    public static class ZeroGravity extends FbEffect {
        public ZeroGravity() { super(MobEffectCategory.BENEFICIAL, FbFx.ASTRAL); }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            e.resetFallDistance();
            if (!(e instanceof Player)) {
                Vec3 v = e.getDeltaMovement();
                // Mobs à deriva: sobem bem devagar (~0,25 bloco/s) e quase não controlam o movimento
                e.setDeltaMovement(v.x * 0.9, v.y + (0.012 - v.y) * 0.1, v.z * 0.9);
            }
            if (e.tickCount % 8 == 0) {
                sl.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() - 0.1, e.getZ(), 1, 0.25, 0.05, 0.25, 0.01);
                sl.sendParticles(FbFx.dust(FbFx.ASTRAL_LIGHT, 0.8F), e.getX(), e.getY() + 0.1, e.getZ(), 2, 0.3, 0.1, 0.3, 0);
            }
            return true;
        }
    }

    /** Projeção Astral: só indicador/cronômetro — a lógica está em AstralProjection. Não sai com leite. */
    public static class AstralProjectionEffect extends FbEffect {
        public AstralProjectionEffect() { super(MobEffectCategory.BENEFICIAL, FbFx.ASTRAL_LIGHT); }

        @Override
        public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {}
    }

    // =====================================================================================
    //  Chama Infernal
    // =====================================================================================

    /** Fogo Negro: dano a cada segundo que cura quem conjurou; água e leite não apagam. */
    public static class BlackFlame extends FbEffect {
        public BlackFlame() { super(MobEffectCategory.HARMFUL, 0x2A0610); }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            if (e.tickCount % 3 == 0) {
                sl.sendParticles(FbFx.fade(FbFx.HELLFIRE, FbFx.HELL_BLACK, 1.3F), e.getX(), e.getY() + e.getBbHeight() * 0.4,
                        e.getZ(), 3, e.getBbWidth() * 0.4, e.getBbHeight() * 0.35, e.getBbWidth() * 0.4, 0.02);
                sl.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + e.getBbHeight() * 0.7, e.getZ(), 2,
                        e.getBbWidth() * 0.3, 0.2, e.getBbWidth() * 0.3, 0.02);
                if (e.getRandom().nextInt(3) == 0) {
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + 0.2, e.getZ(), 1,
                            e.getBbWidth() * 0.4, 0.1, e.getBbWidth() * 0.4, 0.01);
                }
            }
            if (e.tickCount % 20 == 0) {
                DamageSource src = FbUtil.source(e, FbRegistries.BLACK_FLAME_DAMAGE);
                float before = e.getHealth();
                e.invulnerableTime = 0;
                if (e.hurt(src, 1.5F + amplifier)) {
                    float dealt = Math.max(0, before - e.getHealth());
                    Entity attacker = src.getEntity();
                    // Drena: metade do dano volta como vida para quem acendeu o fogo
                    if (attacker instanceof LivingEntity le && le.isAlive() && le != e && le.distanceToSqr(e) < 32 * 32) {
                        le.heal(dealt * 0.5F);
                        if (le.distanceToSqr(e) < 16 * 16) {
                            FbFx.line(sl, FbUtil.center(e), FbUtil.center(le), FbFx.dust(FbFx.HELLFIRE, 0.7F), 1.5);
                        }
                    }
                }
            }
            return true;
        }

        @Override
        public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {}
    }

    /** Pacto de Sangue: gotas de sangue caindo do corpo. */
    public static class BloodPact extends FbEffect {
        public BloodPact() { super(MobEffectCategory.BENEFICIAL, FbFx.BLOOD); }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 10 == 0; }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (e.level() instanceof ServerLevel sl) {
                FbFx.around(sl, e, FbFx.dust(FbFx.BLOOD, 1.0F), 3, 0);
                if (e.getRandom().nextInt(3) == 0) FbFx.around(sl, e, ParticleTypes.CRIMSON_SPORE, 4, 0.01);
            }
            return true;
        }
    }

    // =====================================================================================
    //  Abismo
    // =====================================================================================

    /** Veneno Abissal: dano a cada 1,25s que pode matar (funciona em mortos-vivos). */
    public static class AbyssalVenom extends FbEffect {
        public AbyssalVenom() { super(MobEffectCategory.HARMFUL, FbFx.VENOM); }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            if (e.tickCount % 6 == 0) {
                FbFx.around(sl, e, FbFx.fade(FbFx.VENOM, FbFx.ABYSS_DEEP, 0.9F), 2, 0);
            }
            if (e.tickCount % 25 == 0) {
                e.invulnerableTime = 0;
                e.hurt(FbUtil.source(e, FbRegistries.ABYSSAL_VENOM_DAMAGE), 1.0F + amplifier);
                FbFx.around(sl, e, ParticleTypes.SQUID_INK, 3, 0.02);
            }
            return true;
        }
    }

    /** Afogamento Abissal: o ar acaba mesmo fora da água; sem ar, dano a cada segundo. */
    public static class AbyssalDrown extends FbEffect {
        public AbyssalDrown() { super(MobEffectCategory.HARMFUL, FbFx.ABYSS); }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            // O jogo devolve +4 de ar por tick fora da água: tirar 10 deixa o saldo em -6/tick
            e.setAirSupply(Math.max(-20, e.getAirSupply() - 10));
            double headY = e.getEyeY();
            if (e.tickCount % 4 == 0) {
                sl.sendParticles(ParticleTypes.BUBBLE_POP, e.getX(), headY, e.getZ(), 2, 0.2, 0.15, 0.2, 0.02);
                sl.sendParticles(FbFx.dust(FbFx.ABYSS, 0.8F), e.getX(), headY, e.getZ(), 1, 0.25, 0.2, 0.25, 0);
            }
            if (e.getAirSupply() <= 0 && e.tickCount % 20 == 0) {
                e.invulnerableTime = 0;
                e.hurt(FbUtil.source(e, FbRegistries.ABYSSAL_DROWN_DAMAGE), 2.0F + amplifier);
                sl.sendParticles(ParticleTypes.SPLASH, e.getX(), headY, e.getZ(), 10, 0.3, 0.2, 0.3, 0.1);
                FbFx.sound(sl, e.position(), SoundEvents.PLAYER_HURT_DROWN, 0.6F, 0.8F);
            }
            return true;
        }
    }

    /** Tinta: mobs que perderam a visão esquecem alvos que não estão bem perto. */
    public static class Inked extends FbEffect {
        public Inked() { super(MobEffectCategory.HARMFUL, 0x101018); }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 10 == 0; }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            sl.sendParticles(ParticleTypes.SQUID_INK, e.getX(), e.getEyeY(), e.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
            if (e instanceof Mob m && m.getTarget() != null && m.distanceToSqr(m.getTarget()) > 3.5 * 3.5) {
                m.setTarget(null);
            }
            return true;
        }
    }

    // =====================================================================================
    //  Gravidade
    // =====================================================================================

    /**
     * Gravidade Invertida: cai para cima; bater a cabeça no teto machuca.
     * Gravidade ×(-0,1) = -0,008/tick: velocidade máxima ~0,39 bloco/tick (com o atrito do ar), então em 2,5s o
     * alvo sobe uns 12 blocos — a queda depois machuca bastante, mas não é morte garantida.
     */
    public static class InvertedGravity extends FbEffect {
        public InvertedGravity() {
            super(MobEffectCategory.HARMFUL, FbFx.GRAVITY);
            addAttributeModifier(Attributes.GRAVITY, IsekaiMahou.id("effect.inverted_gravity"),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amp -> -1.1);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            e.resetFallDistance();
            if (e.tickCount % 3 == 0) {
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY(), e.getZ(), 3,
                        e.getBbWidth() * 0.4, 0.1, e.getBbWidth() * 0.4, 0.05);
            }
            boolean ceiling = e.verticalCollision && !e.verticalCollisionBelow && !e.onGround();
            if (ceiling && e.tickCount % 10 == 0) {
                e.invulnerableTime = 0;
                e.hurt(FbUtil.source(e, FbRegistries.GRAVITY_CRASH_DAMAGE), 2.0F + amplifier);
                sl.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 8, 0.3, 0.05, 0.3, 0.1);
                FbFx.sound(sl, e.position(), SoundEvents.ANVIL_LAND, 0.3F, 1.6F);
            }
            return true;
        }
    }

    private FbMobEffects() {}
}
