package com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental;

import java.util.List;

import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.BoltKind;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.CreatureMove;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalBolt;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalCreature;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Elemental de Ar Maior (Ceus de Tempestade): tornado vivo que voa. Corta com laminas de vento, dispara
 * rajadas que arremessam e invoca um Ciclone que levanta os inimigos (a queda doi). Desvia parte dos projeteis.
 */
public class GreaterAirElemental extends DimensionalCreature implements Enemy {
    /** Desvio de vento: devolve o projetil com uma lufada. */
    private static final ProjectileDeflection WIND_DEFLECT = (proj, entity, random) -> {
        ProjectileDeflection.REVERSE.deflect(proj, entity, random);
        if (proj.level() instanceof ServerLevel level) {
            CeFx.burst(level, proj.position(), ParticleTypes.GUST, 1, 0.0, 0.0);
            CeFx.sound(level, proj.position(), SoundEvents.BREEZE_DEFLECT, 1.0F, 1.0F);
        }
    };

    /** Altura maxima (blocos) a que o Ciclone ergue os inimigos. */
    private static final double CYCLONE_LIFT = 6.0;

    private Vec3 cycloneBase;

    public GreaterAirElemental(EntityType<? extends GreaterAirElemental> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(75, 4, 9, 0.3)
                .add(Attributes.FLYING_SPEED, 0.16)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    public Faction faction() {
        return Faction.ELEMENTAL;
    }

    @Override
    protected int experience() {
        return 25;
    }

    @Override
    public boolean flies() {
        return true;
    }

    @Override
    public double preferredRange() {
        return 6.0;
    }

    @Override
    public double hoverHeight() {
        return 2.5;
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of(
                CreatureMove.of("cyclone").timing(10, 70).cooldown(220).range(0, 7.0).blind()
                        .onStart(t -> CeFx.sound(level(), position(), SoundEvents.BREEZE_WHIRL, 1.5F, 0.6F))
                        .during(this::cyclone),
                CreatureMove.of("attack").timing(7, 14).cooldown(22).range(0, 2.4).perform(this::slash),
                CreatureMove.of("cast").timing(8, 16).cooldown(32).range(2.0, 22).perform(this::gust));
    }

    private void slash(LivingEntity t) {
        if (!(level() instanceof ServerLevel level) || gapTo(t) > 3.2) return;
        meleeHit(t, 1.0F, 0.8);
        Vec3 at = center(t);
        CeFx.burst(level, at, ParticleTypes.SWEEP_ATTACK, 2, 0.3, 0.0);
        CeFx.burst(level, at, ParticleTypes.CLOUD, 8, 0.3, 0.05);
        CeFx.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.4F);
    }

    private void gust(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 from = position().add(0, getBbHeight() * 0.7, 0).add(getLookAngle().scale(0.8));
        DimensionalBolt.fire(this, BoltKind.GUST, from, center(t), 1.3F, 6.0F, 1.0F);
        CeFx.sound(level, from, SoundEvents.BREEZE_SHOOT, 1.0F, 0.8F);
    }

    /**
     * Ciclone: nasce onde o alvo estava (o jogador pode sair dele), ergue os inimigos em espiral ate
     * {@link #CYCLONE_LIFT} blocos, segura-os no alto e no fim os solta de uma vez (a queda faz o resto).
     * A coluna e fixa: seguir o alvo faria a subida nunca acabar.
     */
    private void cyclone(LivingEntity target, int i) {
        if (!(level() instanceof ServerLevel level) || i < 10 || i > 69) return;
        if (i == 10 || cycloneBase == null) cycloneBase = target.position();
        Vec3 base = cycloneBase;
        if (i % 2 == 0) {
            CeFx.spiral(level, base, 1.8, 6.0, ParticleTypes.CLOUD, 10, i * 0.5);
            CeFx.spiral(level, base, 2.6, 5.0, CeFx.dust(0x9FF0D0, 1.2F), 8, -i * 0.4);
        }
        if (i % 6 == 0) level.sendParticles(ParticleTypes.SMALL_GUST, base.x, base.y + 1.0, base.z, 3, 1.2, 1.0, 1.2, 0.0);
        if (i % 20 == 0) CeFx.sound(level, base, SoundEvents.BREEZE_WHIRL, 1.2F, 0.7F);
        boolean last = i >= 69;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(base, base).inflate(3.5, 0, 3.5).expandTowards(0, CYCLONE_LIFT + 3, 0)
                .expandTowards(0, -1, 0), e -> isFoe(e) && e.position().subtract(base).horizontalDistanceSqr() <= 3.5 * 3.5)) {
            Vec3 to = base.subtract(e.position()).multiply(1, 0, 1);
            Vec3 swirl = to.lengthSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(-to.z, 0, to.x).normalize().scale(0.2);
            Vec3 v = e.getDeltaMovement();
            double up = last ? -0.6 : e.getY() < base.y + CYCLONE_LIFT ? Math.max(v.y, 0.3) : 0.03;
            e.setDeltaMovement(v.x * 0.6 + swirl.x + to.x * 0.08, up, v.z * 0.6 + swirl.z + to.z * 0.08);
            e.hurtMarked = true;
            e.resetFallDistance();
            if (i % 10 == 0) strike(e, 2.0F, magicSource(this));
        }
        if (last) {
            CeFx.burst(level, base.add(0, CYCLONE_LIFT * 0.5, 0), ParticleTypes.GUST, 3, 1.0, 0.0);
            CeFx.sound(level, base, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, 0.7F);
            cycloneBase = null;
        }
    }

    @Override
    public ProjectileDeflection deflection(Projectile projectile) {
        if (projectile.getOwner() != null && isKin(projectile.getOwner())) return ProjectileDeflection.NONE;
        return random.nextFloat() < 0.4F ? WIND_DEFLECT : ProjectileDeflection.NONE;
    }

    @Override
    protected void clientFx() {
        var r = random;
        double a = tickCount * 0.5;
        for (int k = 0; k < 2; k++) {
            double h = r.nextDouble() * 1.6;
            double rad = 0.25 + h * 0.3;
            double ang = a + k * Math.PI + h * 3;
            level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(ang) * rad, getY() + h, getZ() + Math.sin(ang) * rad, 0, 0.01, 0);
        }
        if (r.nextInt(4) == 0) level().addParticle(ParticleTypes.WHITE_ASH, getRandomX(0.8), getY() + r.nextDouble() * 2.4, getRandomZ(0.8), 0, 0, 0);
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 1.2, 0);
        CeFx.spiral(level, position(), 1.2, 3.0, ParticleTypes.CLOUD, 40, 0);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        CeFx.sound(level, c, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, 0.6F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BREEZE_IDLE_AIR;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BREEZE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BREEZE_DEATH;
    }
}
