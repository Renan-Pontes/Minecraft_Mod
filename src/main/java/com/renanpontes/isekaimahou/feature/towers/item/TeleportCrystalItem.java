package com.renanpontes.isekaimahou.feature.towers.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.towers.TowerData;
import com.renanpontes.isekaimahou.feature.towers.TwProgress;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Cristal de Teleporte (SAO): segure 1,5 s. Dentro de uma Torre de Desafio volta à entrada (em cima do núcleo do térreo);
 * fora, ao ponto de renascimento (cama/âncora, ou o spawn do mundo). Não funciona com um chefe a até 24 blocos.
 */
public class TeleportCrystalItem extends Item {
    private static final int USE_TICKS = 30;

    public TeleportCrystalItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level instanceof ServerLevel sl && remaining % 4 == 0) {
            sl.sendParticles(ParticleTypes.PORTAL, user.getX(), user.getY() + 1, user.getZ(), 10, 0.5, 0.8, 0.5, 0.4);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (user instanceof ServerPlayer sp && teleport(sp)) {
            if (!sp.getAbilities().instabuild) stack.shrink(1);
            sp.getCooldowns().addCooldown(this, 40);
        }
        return stack;
    }

    private static boolean teleport(ServerPlayer sp) {
        ServerLevel sl = sp.serverLevel();
        List<Entity> bosses = sl.getEntities((Entity) null, sp.getBoundingBox().inflate(24),
                e -> e instanceof LivingEntity le && le.isAlive() && BossKit.isBossLike(e));
        if (!bosses.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.tower_teleport_crystal.blocked")
                    .withStyle(ChatFormatting.RED), true);
            sl.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
            return false;
        }
        Vec3 from = sp.position();
        fx(sl, from);
        StructureStart tower = TowerData.towerAt(sl, sp.blockPosition());
        if (tower.isValid()) {
            BlockPos door = TowerData.entrance(sl, tower);
            sp.teleportTo(sl, door.getX() + 0.5, door.getY(), door.getZ() + 0.5, sp.getYRot(), sp.getXRot());
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.tower_teleport_crystal.entrance")
                    .withColor(0x7FD4FF), true);
        } else {
            DimensionTransition dest = sp.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
            sp.changeDimension(dest);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.tower_teleport_crystal.home")
                    .withColor(0x7FD4FF), true);
        }
        fx(sp.serverLevel(), sp.position());
        TwProgress.flag(sp, TwProgress.FLAG_CRYSTAL);
        return true;
    }

    private static void fx(ServerLevel sl, Vec3 at) {
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 30, 0.4, 1.0, 0.4, 0.05);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 40, 0.5, 1.0, 0.5, 0.1);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.3F);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.tower_teleport_crystal.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.isekai_mahou.tower_teleport_crystal.use").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("item.isekai_mahou.tower_teleport_crystal.boss").withStyle(ChatFormatting.DARK_GRAY));
    }
}
