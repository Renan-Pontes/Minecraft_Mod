package com.renanpontes.isekaimahou.feature.creatures_b.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.creatures_b.CbRegistries;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.CbMonster;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.OrcLordEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Chifre de Guerra Orc: tocar desafia o Lorde Orc, que chega com uma escolta (precisa de dificuldade
 * acima de Pacífico e não pode haver outro Lorde Orc por perto). Some ao ser usado.
 */
public class OrcWarHornItem extends Item {
    public OrcWarHornItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        if (sl.getDifficulty() == Difficulty.PEACEFUL) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.orc_war_horn.peaceful").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!sl.getEntitiesOfClass(OrcLordEntity.class, player.getBoundingBox().inflate(64.0)).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.orc_war_horn.already").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        BlockPos lordPos = groundAhead(sl, player, 12.0, 0.0);
        if (lordPos == null) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.orc_war_horn.no_room").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        OrcLordEntity lord = CbRegistries.ORC_LORD.get().spawn(sl, lordPos, MobSpawnType.EVENT);
        if (lord == null) {
            // Algo impediu o spawn (evento cancelado, bloco ocupado...): não gasta o chifre.
            player.displayClientMessage(Component.translatable("message.isekai_mahou.orc_war_horn.no_room").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 6.0F, 0.85F);
        sl.gameEvent(player, GameEvent.INSTRUMENT_PLAY, player.position());
        smoke(sl, lord.position());
        if (!player.isCreative()) lord.setTarget(player);
        for (int i = 0; i < 3; i++) {
            BlockPos p = groundAhead(sl, player, 11.0 + i, (i - 1) * 3.5);
            if (p == null) continue;
            EntityType<? extends CbMonster> type = i == 1 ? CbRegistries.ORC_BERSERKER.get() : CbRegistries.ORC.get();
            CbMonster orc = type.spawn(sl, p, MobSpawnType.EVENT);
            if (orc != null) smoke(sl, orc.position());
        }
        player.displayClientMessage(Component.translatable("message.isekai_mahou.orc_war_horn.summoned").withStyle(ChatFormatting.DARK_GREEN), true);
        player.getCooldowns().addCooldown(this, 100);
        stack.consume(1, player);
        return InteractionResultHolder.consume(stack);
    }

    /** Chão firme {@code dist} blocos à frente do jogador (deslocado {@code side} blocos para o lado). */
    private static BlockPos groundAhead(ServerLevel sl, Player player, double dist, double side) {
        Vec3 f = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 r = new Vec3(-f.z, 0, f.x);
        Vec3 p = player.position().add(f.scale(dist)).add(r.scale(side));
        BlockPos top = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(p));
        // Em cavernas o heightmap aponta para a superfície: procura chão perto da altura do jogador.
        if (Math.abs(top.getY() - player.getY()) <= 8) return top;
        BlockPos.MutableBlockPos m = BlockPos.containing(p.x, player.getY() + 4, p.z).mutable();
        for (int i = 0; i < 12; i++) {
            if (sl.getBlockState(m).isAir() && sl.getBlockState(m.above()).isAir() && sl.getBlockState(m.above(2)).isAir()
                    && sl.getBlockState(m.below()).isSolidRender(sl, m.below())) {
                return m.immutable();
            }
            m.move(0, -1, 0);
        }
        return null;
    }

    private static void smoke(ServerLevel sl, Vec3 pos) {
        sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y + 0.3, pos.z, 15, 0.6, 0.2, 0.6, 0.02);
        sl.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 1, pos.z, 25, 0.5, 1.0, 0.5, 0.03);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.orc_war_horn.desc").withStyle(ChatFormatting.GRAY));
    }
}
