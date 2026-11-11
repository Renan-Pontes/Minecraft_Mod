package com.renanpontes.isekaimahou.feature.ritual_witch.item;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.feature.ritual_witch.entity.CurseBottleEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Maldição Engarrafada: arremessável. Ao quebrar amaldiçoa quem estiver a até 4 blocos (menos quem arremessou).
 * As "persistentes" deixam uma nuvem amaldiçoada no chão.
 */
public class BottledCurseItem extends Item {
    public static final double RADIUS = 4.0;

    private final Supplier<List<MobEffectInstance>> effects;
    private final int color;
    private final boolean lingering;

    public BottledCurseItem(Supplier<List<MobEffectInstance>> effects, int color, boolean lingering, Properties properties) {
        super(properties);
        this.effects = effects;
        this.color = color;
        this.lingering = lingering;
    }

    public int color() {
        return color;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 0.6F,
                0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            CurseBottleEntity bottle = new CurseBottleEntity(level, player);
            bottle.setItem(stack);
            bottle.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.6F, 1.0F);
            level.addFreshEntity(bottle);
        }
        player.getCooldowns().addCooldown(this, 12);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Quebra da garrafa: aplica a maldição em área. {@code direct} = entidade atingida em cheio (ou null). */
    public void shatter(ServerLevel level, Entity bottle, Entity direct, Entity owner) {
        Vec3 c = bottle.position();
        AABB box = bottle.getBoundingBox().inflate(RADIUS, 2.0, RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == owner || !target.isAffectedByPotions()) continue;
            double d2 = bottle.distanceToSqr(target);
            if (d2 >= RADIUS * RADIUS) continue;
            double factor = target == direct ? 1.0 : Math.max(0.25, 1.0 - Math.sqrt(d2) / RADIUS);
            for (MobEffectInstance e : effects.get()) {
                int dur = (int) Math.round(e.getDuration() * factor);
                if (dur > 20) target.addEffect(new MobEffectInstance(e.getEffect(), dur, e.getAmplifier()), owner);
            }
            level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    4, 0.25, 0.3, 0.25, 0.02);
        }
        if (lingering) {
            AreaEffectCloud cloud = new AreaEffectCloud(level, c.x, c.y, c.z);
            if (owner instanceof LivingEntity le) cloud.setOwner(le);
            cloud.setRadius(2.5F);
            cloud.setRadiusOnUse(-0.3F);
            cloud.setWaitTime(10);
            cloud.setDuration(140);
            cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
            cloud.setPotionContents(new PotionContents(Optional.empty(), Optional.of(color), List.of()));
            for (MobEffectInstance e : effects.get()) {
                cloud.addEffect(new MobEffectInstance(e.getEffect(), Math.max(40, e.getDuration() / 4), e.getAmplifier()));
            }
            level.addFreshEntity(cloud);
        }
        // Visual da maldição: respingo colorido (evento vanilla 2002 = som de vidro + partículas), tinta e almas
        level.levelEvent(2002, bottle.blockPosition(), color);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | color), c.x, c.y + 0.3, c.z,
                30, RADIUS * 0.35, 0.4, RADIUS * 0.35, 0.4);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.3, c.z, 12, 0.6, 0.2, 0.6, 0.05);
        level.sendParticles(ParticleTypes.SOUL, c.x, c.y + 0.4, c.z, 5, 0.8, 0.3, 0.8, 0.02);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 0.5F, 0.6F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.bottled_curse", (int) RADIUS).withStyle(ChatFormatting.DARK_PURPLE));
        if (lingering) tooltip.add(Component.translatable("tooltip.isekai_mahou.bottled_curse.lingering").withStyle(ChatFormatting.DARK_GREEN));
        PotionContents.addPotionTooltip(effects.get(), tooltip::add, 1.0F, ctx.tickRate());
    }
}
