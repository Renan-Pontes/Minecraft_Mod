package com.renanpontes.isekaimahou.feature.character.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.character.Reincarnation;

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
 * release 1.0 — <b>Altar do Recomeço</b>: clicar abre a confirmação de devolver todos os pontos de talento e desfazer a
 * especialização da classe atual (custa Cristais de Mana). Pedra clara com um broto de luz: folhas e brilhos subindo.
 */
public class NewBeginningsAltarBlock extends Block {
    public static final MapCodec<NewBeginningsAltarBlock> CODEC = simpleCodec(NewBeginningsAltarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 4, 16),
            Block.box(3, 4, 3, 13, 11, 13),
            Block.box(1, 11, 1, 15, 14, 15));

    public NewBeginningsAltarBlock(Properties props) {
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
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.9F);
            Reincarnation.openRenewal(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.95, cz = pos.getZ() + 0.5;
        // Dois brilhos girando devagar em volta do broto
        float a = (level.getGameTime() % 120) / 120F * Mth.TWO_PI + rnd.nextFloat() * 0.3F;
        for (int i = 0; i < 2; i++) {
            float ang = a + i * Mth.PI;
            level.addParticle(new DustParticleOptions(new org.joml.Vector3f(0.62F, 0.95F, 0.70F), 0.7F),
                    cx + Mth.cos(ang) * 0.42, cy + 0.1, cz + Mth.sin(ang) * 0.42, 0, 0.02, 0);
        }
        if (rnd.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, cx + (rnd.nextDouble() - 0.5) * 0.4, cy + 0.2, cz + (rnd.nextDouble() - 0.5) * 0.4,
                    0, 0.02, 0);
        }
        if (rnd.nextInt(7) == 0) {
            level.addParticle(ParticleTypes.CHERRY_LEAVES, cx + (rnd.nextDouble() - 0.5) * 1.2, cy + 1.4,
                    cz + (rnd.nextDouble() - 0.5) * 1.2, 0, 0, 0);
        }
        if (rnd.nextInt(120) == 0) {
            level.playLocalSound(cx, cy, cz, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.35F, 1.2F + rnd.nextFloat() * 0.3F, false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("block.isekai_mahou.new_beginnings_altar.tooltip").withColor(0x9FE8B0));
        lines.add(Component.translatable("block.isekai_mahou.new_beginnings_altar.tooltip2", Reincarnation.RENEWAL.crystals())
                .withColor(0xA89EC0));
    }
}
