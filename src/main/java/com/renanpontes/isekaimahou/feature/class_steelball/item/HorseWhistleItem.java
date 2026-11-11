package com.renanpontes.isekaimahou.feature.class_steelball.item;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Apito do Corcel (lição I): o Steel Ball nunca anda a pé. O primeiro sopro chama um corcel de corrida já domado e
 * selado (rápido, saltador, resistente); depois o apito chama SEMPRE o mesmo cavalo para o seu lado. Se ele morrer
 * (ou ficar muito longe, fora do mundo carregado), um novo corcel atende — com uma espera de 5 min.
 */
public class HorseWhistleItem extends Item {
    public static final String STEED_TAG = "isekai_mahou.steelball_steed";
    private static final int CALL_COOLDOWN = 100;
    private static final int NEW_STEED_COOLDOWN = 6000;

    public HorseWhistleItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        SbFx.sound(sl, sp.position(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), 0.6F, 1.8F);
        UUID bound = bound(stack);
        Entity old = bound == null ? null : sl.getEntity(bound);
        if (old instanceof AbstractHorse horse && horse.isAlive()) {
            if (horse.isVehicle() && !horse.hasPassenger(sp)) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.whistle.busy").withStyle(ChatFormatting.GRAY), true);
            } else {
                Vec3 at = side(sp);
                horse.teleportTo(at.x, at.y, at.z);
                horse.getNavigation().stop();
                sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 12, 0.5, 0.3, 0.5, 0.02);
                SbFx.sound(sl, at, SoundEvents.HORSE_GALLOP, 1.0F, 1.0F);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.whistle.called").withStyle(ChatFormatting.GOLD), true);
            }
            sp.getCooldowns().addCooldown(this, CALL_COOLDOWN);
            return InteractionResultHolder.consume(stack);
        }
        Horse steed = spawnSteed(sl, sp);
        if (steed == null) return InteractionResultHolder.fail(stack);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Steed", steed.getUUID());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        sp.getCooldowns().addCooldown(this, bound == null ? CALL_COOLDOWN : NEW_STEED_COOLDOWN);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.whistle.new").withStyle(ChatFormatting.GOLD), true);
        return InteractionResultHolder.consume(stack);
    }

    @Nullable
    private static UUID bound(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.hasUUID("Steed") ? tag.getUUID("Steed") : null;
    }

    private static Vec3 side(ServerPlayer sp) {
        Vec3 look = Vec3.directionFromRotation(0, sp.getYRot() + 90F);
        Vec3 at = sp.position().add(look.scale(2.0));
        return sp.serverLevel().noCollision(EntityType.HORSE.getDimensions().makeBoundingBox(at)) ? at : sp.position();
    }

    @Nullable
    private static Horse spawnSteed(ServerLevel sl, ServerPlayer sp) {
        Horse horse = EntityType.HORSE.create(sl);
        if (horse == null) return null;
        Vec3 at = side(sp);
        horse.moveTo(at.x, at.y, at.z, sp.getYRot(), 0);
        horse.setVariant(Variant.values()[sl.random.nextInt(Variant.values().length)]);
        horse.tameWithName(sp);
        horse.equipSaddle(new ItemStack(Items.SADDLE), null);
        // corcel de corrida: o melhor que um cavalo vanilla pode ser
        var speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.3375);
        var jump = horse.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) jump.setBaseValue(1.0);
        var hp = horse.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(30.0);
        horse.setHealth(horse.getMaxHealth());
        horse.setCustomName(Component.translatable("entity.isekai_mahou.steelball_steed", sp.getName()));
        horse.addTag(STEED_TAG);
        horse.setPersistenceRequired();
        if (!sl.addFreshEntity(horse)) return null;
        sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.6, at.z, 20, 0.6, 0.4, 0.6, 0.03);
        SbFx.sound(sl, at, SoundEvents.HORSE_AMBIENT, 1.0F, 1.0F);
        SbFx.sound(sl, at, SoundEvents.HORSE_GALLOP, 1.0F, 0.9F);
        return horse;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.whistle").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(bound(stack) == null ? "tooltip.isekai_mahou.steelball.whistle.unbound"
                : "tooltip.isekai_mahou.steelball.whistle.bound").withStyle(ChatFormatting.DARK_GRAY));
    }
}
