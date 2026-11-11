package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.FlyWanderGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.KeepRangeGoal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Sílfide: espírito do vento das montanhas. Brincalhona — de vez em quando dança em volta de um
 * jogador e o abençoa com Velocidade e Queda Lenta. Provocada, mantém distância lançando lâminas de
 * vento (magia "projétil + rajada" quando existir) e solta um ciclone que arremessa quem estiver perto.
 */
public class Sylph extends FlyingElemental {
    private int blessCooldown = 200;

    public Sylph(EntityType<? extends Sylph> type, Level level) {
        super(type, level);
        this.xpReward = 7;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.7)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public Element element() {
        return Element.WIND;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"attack", "cast", "special"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new AbilityGoal(this, "special", 0, 5, 12, 140, 200, true, t -> cyclone())
                .windup((t, left) -> {
                    if (level() instanceof ServerLevel sl) MobMagic.ring(sl, position(), ParticleTypes.CLOUD, 1.0 + (12 - left) * 0.25, 10, 0.02);
                }));
        goalSelector.addGoal(2, new AbilityGoal(this, "cast", 3, 16, 10, 40, 70, false, this::windBlade));
        goalSelector.addGoal(3, new KeepRangeGoal(this, 1.2, 5, 10, 2.5));
        goalSelector.addGoal(5, new FlyWanderGoal(this, 1.0, 2, 7, 12));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    private void windBlade(LivingEntity target) {
        if (MobMagic.cast(this, target, Element.WIND, 0.8F, "projectile", "gust")) return;
        // Sem o glifo "rajada": usa uma carga de vento de verdade
        Vec3 from = getEyePosition();
        Vec3 dir = MobMagic.center(target).subtract(from).normalize();
        WindCharge charge = new WindCharge(level(), from.x + dir.x, from.y + dir.y, from.z + dir.z, dir);
        charge.setOwner(this);
        level().addFreshEntity(charge);
        playSoundHere(SoundEvents.BREEZE_SHOOT, 1.0F, 1.3F);
    }

    /** Ciclone: arremessa e faz levitar quem estiver perto. */
    private void cyclone() {
        if (!(level() instanceof ServerLevel sl)) return;
        for (LivingEntity e : MobMagic.victimsAround(this, position(), 5.0)) {
            MobMagic.knockAway(e, position(), 1.1, 0.7);
            e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 25, 0), this);
        }
        for (int i = 0; i < 4; i++) MobMagic.ring(sl, position().add(0, i * 0.6, 0), ParticleTypes.CLOUD, 1.5 + i * 0.8, 16, 0.05);
        sl.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
        playSoundHere(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.2F, 0.9F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isAlive()) return;
        if (blessCooldown > 0) blessCooldown--;
        if (blessCooldown == 0 && tickCount % 20 == 0 && getTarget() == null && getLastHurtByMob() == null && random.nextInt(4) == 0) {
            Player p = level().getNearestPlayer(this, 6.0);
            if (p != null && !p.isSpectator()) bless(p);
        }
    }

    /** Bênção do vento: dança em volta do jogador e o deixa leve e rápido. */
    private void bless(Player p) {
        blessCooldown = 1200;
        playAction("cast");
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0), this);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0), this);
        if (level() instanceof ServerLevel sl) {
            for (int i = 0; i < 3; i++) MobMagic.ring(sl, p.position().add(0, i * 0.7, 0), ParticleTypes.CLOUD, 1.2, 14, 0.02);
            ElementFx.burst(sl, p.position().add(0, 1, 0), Element.WIND, 16, 0.6);
        }
        p.displayClientMessage(Component.translatable("message.isekai_mahou.sylph.blessing").withStyle(ChatFormatting.AQUA), true);
        playSoundHere(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
    }

    @Override
    protected void clientEffects() {
        if (random.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.CLOUD, getRandomX(0.2), getY() + 0.1, getRandomZ(0.2), 0, -0.02, 0);
        }
        if (random.nextInt(5) == 0) bodyParticle(ElementFx.dust(Element.WIND, 0.7F), 0.7, 0.5, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BlessCooldown", blessCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        blessCooldown = tag.getInt("BlessCooldown");
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

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.3F;
    }
}
