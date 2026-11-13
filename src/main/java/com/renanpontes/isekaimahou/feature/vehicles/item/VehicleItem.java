package com.renanpontes.isekaimahou.feature.vehicles.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleKind;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleRegistry;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleStacks;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleUpgrades;
import com.renanpontes.isekaimahou.feature.vehicles.VehiclesRegistries;
import com.renanpontes.isekaimahou.feature.vehicles.entity.MagicVehicle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Item de um veículo mágico: clique no chão (ou na água) para colocá-lo; quem coloca vira o dono.
 * O item guarda a carga do tanque e, desde a onda 10, a vida, as melhorias e a identidade do veículo
 * ({@link VehicleStacks}). Um item que o Mecânico já substituiu (resgate) não funciona mais e some.
 */
public class VehicleItem extends Item {
    private final VehicleKind kind;

    public VehicleItem(VehicleKind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    public VehicleKind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        if (level instanceof ServerLevel sl) {
            VehicleStacks.Data d = VehicleStacks.read(stack);
            if (stale(sl, d)) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_recalled").withStyle(ChatFormatting.GRAY), true);
                stack.setCount(0);
                return InteractionResultHolder.fail(stack);
            }
            // proteção de spawn / modo aventura / áreas onde o jogador não pode mexer
            if (!player.mayUseItemAt(hit.getBlockPos(), hit.getDirection(), stack) || !sl.mayInteract(player, hit.getBlockPos())) {
                return InteractionResultHolder.fail(stack);
            }
            MagicVehicle vehicle = VehiclesRegistries.type(kind).create(sl);
            if (vehicle == null) return InteractionResultHolder.fail(stack);
            Vec3 p = hit.getLocation();
            vehicle.moveTo(p.x, p.y + 0.05, p.z, player.getYRot(), 0.0F);
            if (!sl.noCollision(vehicle, vehicle.getBoundingBox())) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_no_room"), true);
                return InteractionResultHolder.fail(stack);
            }
            vehicle.setOwner(player.getUUID());
            vehicle.applyData(d);
            VehicleRegistry.get(sl.getServer()).out(sl.getServer(), vehicle);
            sl.addFreshEntity(vehicle);
            sl.playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.9F);
            stack.consume(1, player);
            Hints.show(player, "vehicles.placed", Component.keybind("key.sneak"));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** O Mecânico já entregou uma cópia mais nova deste veículo? */
    private static boolean stale(ServerLevel sl, VehicleStacks.Data d) {
        if (d.id() == null) return false;
        VehicleRegistry.Entry e = VehicleRegistry.get(sl.getServer()).get(d.id());
        return e != null && e.gen > d.gen();
    }

    /** Item de veículo substituído pelo Mecânico some sozinho do inventário (confere a cada 2 s). */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof ServerPlayer sp) || (level.getGameTime() + slot) % 40 != 0) return;
        if (stale(sl, VehicleStacks.read(stack))) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_recalled").withStyle(ChatFormatting.GRAY), true);
            stack.setCount(0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        VehicleStacks.Data d = VehicleStacks.read(stack);
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.isekai_mahou.vehicle.seats", kind.seats).withStyle(ChatFormatting.DARK_AQUA));
        int slots = kind.cargoSlots(VehicleUpgrades.level(d.upg(), VehicleUpgrades.Part.STORAGE));
        if (slots > 0) tooltip.add(Component.translatable("item.isekai_mahou.vehicle.cargo", slots).withStyle(ChatFormatting.DARK_AQUA));
        int hp = Math.round(d.hp() * 100);
        tooltip.add(Component.translatable("item.isekai_mahou.vehicle.health", hp)
                .withStyle(hp >= 100 ? ChatFormatting.GREEN : hp >= 50 ? ChatFormatting.YELLOW : ChatFormatting.RED));
        tooltip.add(Component.translatable("item.isekai_mahou.vehicle.charge", d.fuel() * 100 / MagicVehicle.MAX_FUEL).withStyle(ChatFormatting.AQUA));
        for (VehicleUpgrades.Part part : VehicleUpgrades.Part.values()) {
            int lv = VehicleUpgrades.level(d.upg(), part);
            if (lv > 0) {
                tooltip.add(Component.translatable("item.isekai_mahou.vehicle.upgrade", Component.translatable("item.isekai_mahou." + part.itemId()),
                        lv, VehicleUpgrades.MAX).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        tooltip.add(Component.translatable("item.isekai_mahou.vehicle.energy").withStyle(ChatFormatting.DARK_GRAY));
    }
}
