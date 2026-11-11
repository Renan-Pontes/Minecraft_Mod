package com.renanpontes.isekaimahou.feature.dimensions.block;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.dimensions.DimKind;
import com.renanpontes.isekaimahou.feature.dimensions.DimPayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Altar da Convergência: abre a tela do Ritual de Convergência (fundir dois elementos em Mestre).
 * O ritual só funciona no Plano Elemental, onde os quatro elementos existem em estado puro.
 */
public class ConvergenceAltarBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(3, 4, 3, 13, 11, 13), Block.box(1, 11, 1, 15, 16, 15));
    private static final int[] ELEMENT_COLORS = {0xFF6A2B, 0x3FA9FF, 0xA0743C, 0x9FF0D0, 0xFFE27A, 0x8A4FC8};

    public ConvergenceAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        if (!level.dimension().equals(DimKind.ELEMENTAL.key)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.dimensions.altar_wrong_place", DimKind.ELEMENTAL.displayName())
                    .withStyle(ChatFormatting.GOLD), true);
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5F, 1.5F);
            return InteractionResult.CONSUME;
        }
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 0.8F);
        PacketDistributor.sendToPlayer(sp, new DimPayloads.OpenAltar(pos));
        return InteractionResult.CONSUME;
    }

    /** As seis gemas do altar soltam pó colorido; runas do encantamento giram em volta. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            int i = random.nextInt(ELEMENT_COLORS.length);
            double a = Math.PI * 2 * i / ELEMENT_COLORS.length + level.getGameTime() * 0.02;
            int c = ELEMENT_COLORS[i];
            level.addParticle(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 0.9F),
                    pos.getX() + 0.5 + Math.cos(a) * 0.55, pos.getY() + 1.0, pos.getZ() + 0.5 + Math.sin(a) * 0.55, 0.0, 0.04, 0.0);
        }
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 1.6, -0.6 - random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 1.6);
        }
    }
}
