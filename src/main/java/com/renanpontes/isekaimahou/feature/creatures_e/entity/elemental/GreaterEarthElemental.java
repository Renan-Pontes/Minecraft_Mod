package com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Elemental de Terra Maior (Picos Terrosos): colosso de pedra com musgo e cristais.
 * Esmaga e arremessa para cima, lanca pedregulhos e faz um Terremoto que enraiza quem estiver no chao.
 * Resiste a projeteis e explosoes e se regenera enquanto toca o chao.
 */
public class GreaterEarthElemental extends DimensionalCreature implements Enemy {

    public GreaterEarthElemental(EntityType<? extends GreaterEarthElemental> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(140, 16, 15, 0.2)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.5)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0);
    }

    @Override
    public Faction faction() {
        return Faction.ELEMENTAL;
    }

    @Override
    protected int experience() {
        return 30;
    }

    @Override
    protected boolean breathesUnderwater() {
        return true;
    }

    @Override
    protected boolean fallImmune() {
        return true;
    }

    @Override
    public double chaseSpeed() {
        return 1.25;
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of(
                CreatureMove.of("quake").timing(20, 36).cooldown(180).range(0, 5.0).blind()
                        .onStart(t -> CeFx.sound(level(), position(), SoundEvents.RAVAGER_ROAR, 1.2F, 0.5F))
                        .during(this::quake),
                CreatureMove.of("attack").timing(12, 22).cooldown(30).range(0, 2.6).perform(this::smash),
                CreatureMove.of("cast").timing(16, 28).cooldown(80).range(4.5, 26)
                        .during((t, i) -> {
                            if (i < 16 && level() instanceof ServerLevel level && i % 3 == 0) {
                                level.sendParticles(CeFx.block(groundState()), getX(), getY() + 0.2, getZ(), 6, 0.8, 0.1, 0.8, 0.15);
                            }
                            if (i == 16) throwBoulder(t);
                        }));
    }

    private BlockState groundState() {
        BlockState s = level().getBlockState(blockPosition().below());
        return s.isAir() ? Blocks.STONE.defaultBlockState() : s;
    }

    private void smash(LivingEntity t) {
        if (!(level() instanceof ServerLevel level) || gapTo(t) > 3.4) return;
        if (meleeHit(t, 1.0F, 0.5)) {
            t.push(0, 0.6, 0);
            t.hurtMarked = true;
        }
        Vec3 at = t.position();
        level.sendParticles(CeFx.block(groundState()), at.x, at.y + 0.1, at.z, 30, 0.6, 0.1, 0.6, 0.2);
        CeFx.sound(level, at, SoundEvents.IRON_GOLEM_ATTACK, 1.2F, 0.6F);
        CeFx.sound(level, at, SoundEvents.DEEPSLATE_BREAK, 1.0F, 0.6F);
    }

    private void throwBoulder(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 from = position().add(0, getBbHeight() + 0.3, 0);
        // 1.2 b/tick: com a gravidade do pedregulho o arco alcanca todo o raio do golpe (26 blocos)
        DimensionalBolt.fire(this, BoltKind.BOULDER, from, t.position().add(0, 0.6, 0), 1.2F, 12.0F, 1.0F);
        CeFx.sound(level, from, SoundEvents.IRON_GOLEM_ATTACK, 1.2F, 0.5F);
        CeFx.sound(level, from, SoundEvents.DEEPSLATE_BREAK, 1.0F, 0.8F);
    }

    /** Terremoto: onda de choque de pedras que fere e enraiza quem estiver no chao. */
    private void quake(LivingEntity target, int i) {
        if (!(level() instanceof ServerLevel level)) return;
        BlockState ground = groundState();
        if (i < 20 && i % 4 == 0) {
            level.sendParticles(CeFx.block(ground), getX(), getY() + 0.1, getZ(), 12, 1.0, 0.05, 1.0, 0.1);
        }
        if (i == 20) {
            CeFx.sound(level, position(), SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.6F);
            CeFx.sound(level, position(), SoundEvents.POINTED_DRIPSTONE_LAND, 1.5F, 0.5F);
            CeFx.burst(level, position().add(0, 0.3, 0), ParticleTypes.EXPLOSION, 3, 1.0, 0.0);
            for (LivingEntity e : foesNear(position(), 6.5)) {
                boolean grounded = e.onGround() || level.getBlockState(e.blockPosition().below()).isSolid();
                if (!grounded) continue;
                strike(e, 10.0F, magicSource(this));
                e.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, 40, 0), this);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
                e.push(0, 0.35, 0);
                e.hurtMarked = true;
            }
        }
        // anel de pedras se expandindo
        if (i >= 20 && i <= 30) {
            double r = (i - 19) * 0.65;
            int n = (int) (r * 10);
            for (int k = 0; k < n; k++) {
                double a = Math.PI * 2 * k / n;
                double x = getX() + Math.cos(a) * r;
                double z = getZ() + Math.sin(a) * r;
                BlockPos bp = BlockPos.containing(x, getY() - 0.5, z);
                BlockState s = level.getBlockState(bp);
                level.sendParticles(CeFx.block(s.isAir() ? ground : s), x, getY() + 0.15, z, 2, 0.1, 0.0, 0.1, 0.15);
            }
            if (i % 3 == 0) level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.2, getZ(), 6, r * 0.5, 0.05, r * 0.5, 0.0);
        }
    }

    @Override
    protected float modifyIncomingDamage(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return amount * 0.5F;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return amount * 0.5F;
        return amount;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CACTUS) || super.isInvulnerableTo(source);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // regenera enquanto toca o chao
        if (!level().isClientSide && tickCount % 40 == 0 && onGround() && getHealth() < getMaxHealth()) heal(2.0F);
    }

    @Override
    protected void clientFx() {
        var r = random;
        if (r.nextInt(5) == 0) {
            level().addParticle(CeFx.block(Blocks.GRAVEL.defaultBlockState()), getRandomX(0.8), getY() + 2.4 + r.nextDouble() * 0.4,
                    getRandomZ(0.8), 0, 0, 0);
        }
        if (r.nextInt(10) == 0) level().addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, getRandomX(0.6), getY() + 2.8, getRandomZ(0.6), 0, 0, 0);
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 1.5, 0);
        level.sendParticles(CeFx.block(Blocks.MOSSY_COBBLESTONE.defaultBlockState()), c.x, c.y, c.z, 80, 0.8, 1.0, 0.8, 0.2);
        level.sendParticles(CeFx.block(Blocks.AMETHYST_BLOCK.defaultBlockState()), c.x, c.y, c.z, 20, 0.6, 0.8, 0.6, 0.2);
        CeFx.sound(level, c, SoundEvents.DEEPSLATE_BREAK, 1.5F, 0.5F);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.2F, 0.6F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BASALT_BREAK;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }
}
