package com.renanpontes.isekaimahou.feature.character.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.character.CharacterService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Altar da Reencarnação: clicar abre a tela de renascer — trocar de RAÇA ou de CLASSE (release 1.0). Custo MUITO alto:
 * Cristais de Mana + 1 Núcleo de Guardião ({@link com.renanpontes.isekaimahou.feature.character.Reincarnation}). Brilha e
 * solta chamas de alma. (Resetar talentos é no outro altar: o do Recomeço.)
 */
public class ReincarnationAltarBlock extends Block {
    public static final MapCodec<ReincarnationAltarBlock> CODEC = simpleCodec(ReincarnationAltarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 4, 16),
            Block.box(3, 4, 3, 13, 11, 13),
            Block.box(1, 11, 1, 15, 14, 15));

    public ReincarnationAltarBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.7F, 1.3F);
            CharacterService.openAltar(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.95, cz = pos.getZ() + 0.5;
        // Chamas de alma girando em volta do topo
        float a = (level.getGameTime() % 80) / 80F * Mth.TWO_PI + rnd.nextFloat() * 0.4F;
        for (int i = 0; i < 2; i++) {
            float ang = a + i * Mth.PI;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, cx + Mth.cos(ang) * 0.42, cy, cz + Mth.sin(ang) * 0.42, 0, 0.012, 0);
        }
        if (rnd.nextInt(3) == 0) {
            level.addParticle(new DustParticleOptions(new org.joml.Vector3f(0.62F, 0.48F, 1.0F), 0.8F),
                    cx + (rnd.nextDouble() - 0.5) * 0.6, cy + 0.1, cz + (rnd.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
        }
        if (rnd.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, cx, cy + 1.2, cz, (rnd.nextDouble() - 0.5) * 1.5, -0.6, (rnd.nextDouble() - 0.5) * 1.5);
        }
        if (rnd.nextInt(90) == 0) {
            level.playLocalSound(cx, cy, cz, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.4F, 0.8F + rnd.nextFloat() * 0.3F, false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("block.isekai_mahou.reincarnation_altar.tooltip").withColor(0xB98CFF));
        lines.add(Component.translatable("block.isekai_mahou.reincarnation_altar.tooltip2",
                com.renanpontes.isekaimahou.feature.character.Reincarnation.RACE.crystals(),
                com.renanpontes.isekaimahou.feature.character.Reincarnation.CLASS.crystals()).withColor(0xA89EC0));
    }
}
