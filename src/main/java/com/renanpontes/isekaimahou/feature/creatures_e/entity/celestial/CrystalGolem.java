package com.renanpontes.isekaimahou.feature.creatures_e.entity.celestial;

import java.util.List;

import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.BoltKind;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.CreatureMove;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalBolt;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalCreature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Golem de Cristal (Cidade de Cristal): guardiao neutro. REFLETE projeteis (flechas, bolas de fogo e ate
 * magias em projetil) de volta para quem atirou. Esmaga e dispara estilhacos em anel. Defende os celestiais.
 * Fraco a picaretas e explosoes.
 */
public class CrystalGolem extends DimensionalCreature {
    /** Reflexo prismatico: devolve o projetil na direcao de quem o disparou. */
    private static final ProjectileDeflection PRISM = (proj, entity, random) -> {
        Entity shooter = proj.getOwner();
        double speed = Math.max(0.9, proj.getDeltaMovement().length());
        Vec3 dir = shooter != null && shooter != entity
                ? shooter.position().add(0, shooter.getBbHeight() * 0.6, 0).subtract(proj.position()).normalize()
                : proj.getDeltaMovement().scale(-1).normalize();
        proj.setDeltaMovement(dir.scale(speed));
        proj.hasImpulse = true;
        if (proj.level() instanceof ServerLevel level) {
            CeFx.burst(level, proj.position(), CeFx.dust(0xE07AF0, 1.2F), 12, 0.2, 0.0);
            CeFx.burst(level, proj.position(), ParticleTypes.WAX_OFF, 8, 0.3, 0.1);
            CeFx.sound(level, proj.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.8F);
        }
        if (entity instanceof CrystalGolem golem) golem.playAction("reflect");
    };

    public CrystalGolem(EntityType<? extends CrystalGolem> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(150, 18, 16, 0.2)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public Faction faction() {
        return Faction.CELESTIAL;
    }

    @Override
    protected int experience() {
        return 35;
    }

    @Override
    protected boolean breathesUnderwater() {
        return true;
    }

    @Override
    public double chaseSpeed() {
        return 1.25;
    }

    @Override
    protected List<String> extraAnimations() {
        return List.of("reflect");
    }

    /** Neutro: so revida (e chama os outros celestiais); a {@link CelestialHost} tambem o aciona. */
    @Override
    protected void registerTargetGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of(
                CreatureMove.of("attack").timing(12, 22).cooldown(32).range(0, 2.6).perform(this::smash),
                CreatureMove.of("cast").timing(14, 24).cooldown(110).range(0, 16)
                        .during((t, i) -> {
                            if (i < 14 && i % 3 == 0 && level() instanceof ServerLevel level) {
                                level.sendParticles(CeFx.dust(0xE07AF0, 1.0F), getX(), getY() + 2.0, getZ(), 6, 0.8, 0.6, 0.8, 0.0);
                            }
                            if (i == 14) shards(t);
                        }));
    }

    private void smash(LivingEntity t) {
        if (!(level() instanceof ServerLevel level) || gapTo(t) > 3.4) return;
        meleeHit(t, 1.0F, 1.5);
        t.push(0, 0.35, 0);
        t.hurtMarked = true;
        Vec3 at = center(t);
        CeFx.burst(level, at, CeFx.dust(0xE07AF0, 1.4F), 16, 0.4, 0.0);
        CeFx.burst(level, at, ParticleTypes.WAX_OFF, 10, 0.4, 0.1);
        CeFx.sound(level, at, SoundEvents.IRON_GOLEM_ATTACK, 1.2F, 0.9F);
        CeFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 1.2F, 0.7F);
    }

    /** Anel de 10 estilhacos + 3 mirados no alvo. */
    private void shards(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = position().add(0, 1.8, 0);
        for (int k = 0; k < 10; k++) {
            double a = Math.PI * 2 * k / 10 + random.nextDouble() * 0.2;
            Vec3 to = c.add(Math.cos(a) * 10, -0.6, Math.sin(a) * 10);
            DimensionalBolt.fire(this, BoltKind.CRYSTAL_SHARD, c, to, 1.0F, 6.0F, 2.0F);
        }
        for (int k = 0; k < 3; k++) DimensionalBolt.fire(this, BoltKind.CRYSTAL_SHARD, c, center(t), 1.3F, 7.0F, 5.0F);
        CeFx.burst(level, c, ParticleTypes.WAX_OFF, 20, 0.6, 0.15);
        CeFx.sound(level, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.5F, 0.8F);
        CeFx.sound(level, c, SoundEvents.GLASS_BREAK, 1.0F, 1.4F);
    }

    @Override
    public ProjectileDeflection deflection(Projectile projectile) {
        if (projectile.getOwner() != null && isKin(projectile.getOwner())) return ProjectileDeflection.NONE;
        return PRISM;
    }

    /** Picaretas e explosoes racham o cristal (x1,5). */
    @Override
    protected float modifyIncomingDamage(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) amount *= 1.5F;
        if (source.getDirectEntity() instanceof LivingEntity l && l.getMainHandItem().is(ItemTags.PICKAXES)) {
            amount *= 1.5F;
            if (level() instanceof ServerLevel level) {
                CeFx.burst(level, center(this), ParticleTypes.WAX_OFF, 12, 0.5, 0.15);
                CeFx.sound(level, position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.2F);
            }
        }
        return amount;
    }

    @Override
    protected void onHurtBy(LivingEntity attacker, DamageSource source, float amount) {
        CelestialHost.alert(this, attacker);
    }

    @Override
    protected void clientFx() {
        var r = random;
        if (r.nextInt(5) == 0) level().addParticle(ParticleTypes.WAX_OFF, getRandomX(1.0), getY() + 1.5 + r.nextDouble() * 1.2, getRandomZ(1.0), 0, 0, 0);
        if (r.nextInt(8) == 0) level().addParticle(CeFx.dust(0xE07AF0, 0.6F), getRandomX(0.9), getY() + 2.4, getRandomZ(0.9), 0, 0.01, 0);
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 1.4, 0);
        CeFx.burst(level, c, CeFx.dust(0xE07AF0, 1.6F), 50, 0.8, 0.0);
        CeFx.burst(level, c, ParticleTypes.WAX_OFF, 40, 0.8, 0.2);
        CeFx.sound(level, c, SoundEvents.GLASS_BREAK, 1.5F, 0.6F);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.AMETHYST_BLOCK_STEP, 1.5F, 0.6F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_RESONATE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_CLUSTER_BREAK;
    }

    @Override
    protected float getSoundVolume() {
        return 1.5F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.7F;
    }
}
