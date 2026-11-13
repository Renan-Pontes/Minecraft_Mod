package com.renanpontes.isekaimahou.feature.depths.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Explorador Perdido: o esqueleto de um aventureiro que nunca voltou da masmorra. Ainda usa o capacete com lampião e
 * a picareta: o golpe racha a armadura (Exposto) e, de longe, ele ACENDE o lampião na sua cara (cegueira curta) —
 * olhe para o lado quando ele erguer a cabeça.
 */
public class LostDelver extends DepthsMob {
    private int flashCd = 100;
    private int flashWarm;

    public LostDelver(EntityType<? extends LostDelver> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 28.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    protected String modelId() {
        return "depths_lost_delver";
    }

    @Override
    protected List<String> actions() {
        return List.of("attack", "flash");
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        action("attack");
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le && random.nextFloat() < 0.3F) {
            Statuses.apply(le, Statuses.Kind.EXPOSED, 1, 100, this, 1F);
        }
        return hit;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity target = getTarget();
        if (flashWarm > 0) {
            getNavigation().stop();
            if (target != null) getLookControl().setLookAt(target, 30F, 30F);
            if (--flashWarm == 0) flash(sl);
            return;
        }
        if (flashCd > 0) flashCd--;
        if (target instanceof Player && flashCd <= 0 && distanceToSqr(target) > 16 && distanceToSqr(target) < 144 && hasLineOfSight(target)) {
            flashWarm = 16;
            flashCd = 200 + random.nextInt(80);
            action("flash");
            playSound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 0.6F);
        }
    }

    /** Lampião: cega por 2 s quem estiver OLHANDO para ele (virar o rosto evita). */
    private void flash(ServerLevel sl) {
        Vec3 lamp = getEyePosition().add(getLookAngle().scale(0.4));
        sl.sendParticles(ParticleTypes.FLASH, lamp.x, lamp.y + 0.3, lamp.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.END_ROD, lamp.x, lamp.y + 0.3, lamp.z, 10, 0.2, 0.2, 0.2, 0.08);
        playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 1.8F);
        for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(12))) {
            if (p.isCreative() || p.isSpectator() || !p.hasLineOfSight(this)) continue;
            Vec3 toMe = lamp.subtract(p.getEyePosition()).normalize();
            if (p.getLookAngle().dot(toMe) > 0.6) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.SKELETON_STEP, 0.15F, 1.0F);
    }
}
