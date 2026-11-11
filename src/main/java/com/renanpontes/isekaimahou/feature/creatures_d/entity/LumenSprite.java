package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import java.util.EnumSet;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.FlyWanderGoal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fada de Luz: espírito de luz pacífico das planícies e florestas de flores, só aparece de dia.
 * Segue quem segura pó de pedra luminosa; alimentada (clique direito), rodopia, deixa cair uma
 * Essência de Luz e dá Visão Noturna (recarga de 5 minutos). Foge quando atacada e some à noite.
 */
public class LumenSprite extends FlyingElemental {
    private static final int FEED_COOLDOWN = 6000;
    private int fedCooldown;
    private int fleeTicks;

    public LumenSprite(EntityType<? extends LumenSprite> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    public Element element() {
        return Element.LIGHT;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"special"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FleeGoal());
        goalSelector.addGoal(2, new TemptGoal(this, 1.1, s -> s.is(Items.GLOWSTONE_DUST), false));
        goalSelector.addGoal(4, new FlyWanderGoal(this, 1.0, 1, 5, 8));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
    }

    /** Foge voando para longe de quem a feriu. */
    private class FleeGoal extends Goal {
        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return fleeTicks > 0 && getLastHurtByMob() != null;
        }

        @Override
        public void tick() {
            LivingEntity from = getLastHurtByMob();
            if (from == null || tickCount % 10 != 0) return;
            Vec3 away = position().subtract(from.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
            Vec3 dest = position().add(away.normalize().scale(8)).add(0, 3, 0);
            getNavigation().moveTo(dest.x, dest.y, dest.z, 1.6);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.GLOWSTONE_DUST)) return super.mobInteract(player, hand);
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (fedCooldown > 0) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.lumen_sprite.full").withStyle(ChatFormatting.GRAY), true);
            playSoundHere(SoundEvents.AMETHYST_CLUSTER_HIT, 0.8F, 0.6F);
            serverParticles(ParticleTypes.SMOKE, position().add(0, 0.4, 0), 4, 0.15, 0.01);
            return InteractionResult.CONSUME;
        }
        stack.consume(1, player);
        fedCooldown = FEED_COOLDOWN;
        playAction("special");
        spawnAtLocation(new ItemStack(CoreRegistries.ESSENCES.get(Element.LIGHT).get()), 0.3F);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0), this);
        if (level() instanceof ServerLevel sl) {
            ElementFx.burst(sl, position().add(0, 0.4, 0), Element.LIGHT, 20, 0.4);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.6, getZ(), 6, 0.3, 0.3, 0.3, 0);
        }
        player.displayClientMessage(Component.translatable("message.isekai_mahou.lumen_sprite.fed").withStyle(ChatFormatting.YELLOW), true);
        playSoundHere(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.8F);
        playSoundHere(SoundEvents.ALLAY_ITEM_GIVEN, 1.0F, 1.2F);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r) fleeTicks = 100;
        return r;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isAlive()) return;
        if (fedCooldown > 0) fedCooldown--;
        if (fleeTicks > 0) fleeTicks--;
        // Espírito do dia: ao anoitecer se desfaz em luz (a não ser que tenha nome/esteja preso)
        if (!isPersistenceRequired() && level().isNight() && level().canSeeSky(blockPosition()) && random.nextInt(400) == 0) {
            if (level() instanceof ServerLevel sl) ElementFx.burst(sl, position().add(0, 0.3, 0), Element.LIGHT, 16, 0.3);
            playSoundHere(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.6F);
            discard();
        }
    }

    @Override
    protected void clientEffects() {
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.END_ROD, getRandomX(0.3), getY() + 0.3 + random.nextDouble() * 0.3, getRandomZ(0.3),
                    random.nextGaussian() * 0.005, -0.01, random.nextGaussian() * 0.005);
        }
        if (random.nextInt(4) == 0) bodyParticle(ElementFx.dust(Element.LIGHT, 0.6F), 0.5, 0.4, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FedCooldown", fedCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fedCooldown = tag.getInt("FedCooldown");
    }

    @Override
    public float renderAlpha(float partialTick) {
        return 0.92F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.6F;
    }
}
