package com.renanpontes.isekaimahou.feature.guild.block;

import org.joml.Vector3f;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.guild.GuildFaction;
import com.renanpontes.isekaimahou.feature.guild.GuildService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Quadro de Facção: o mesmo painel do Quadro de Missões, com o brasão da facção pintado no topo. Abre a tela de missões
 * filtrada pela facção ({@link GuildService#openBoard}). Propriedades {@code facing} e {@code faction}
 * (guild|kingdom|academy|church|underworld) — os moldes das cidades usam exatamente esses nomes.
 */
public class FactionBoardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FactionBoardBlock> CODEC = simpleCodec(FactionBoardBlock::new);
    public static final EnumProperty<GuildFaction> FACTION = EnumProperty.create("faction", GuildFaction.class);

    private static final VoxelShape SHAPE_NS = Shapes.or(Block.box(1, 0, 6.5, 3, 16, 9.5), Block.box(13, 0, 6.5, 15, 16, 9.5),
            Block.box(0, 4, 7, 16, 16, 9));
    private static final VoxelShape SHAPE_EW = Shapes.or(Block.box(6.5, 0, 1, 9.5, 16, 3), Block.box(6.5, 0, 13, 9.5, 16, 15),
            Block.box(7, 4, 0, 9, 16, 16));

    public FactionBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACTION, GuildFaction.GUILD));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACTION);
    }

    /** A facção vem do item (componente block_state); só a direção depende de quem coloca. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) GuildService.openBoard(sp, pos, state.getValue(FACTION));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Brilho discreto na cor da facção diante do brasão (e às vezes uma runa de encantamento). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(10) != 0) return;
        Direction f = state.getValue(FACING);
        int rgb = state.getValue(FACTION).fallbackRgb;
        Vector3f color = new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F);
        double x = pos.getX() + 0.5 + f.getStepX() * 0.2 + (f.getAxis() == Direction.Axis.Z ? (random.nextDouble() - 0.5) * 0.5 : 0);
        double z = pos.getZ() + 0.5 + f.getStepZ() * 0.2 + (f.getAxis() == Direction.Axis.X ? (random.nextDouble() - 0.5) * 0.5 : 0);
        level.addParticle(new DustParticleOptions(color, 0.7F), x, pos.getY() + 0.85 + random.nextDouble() * 0.1, z, 0, 0.02, 0);
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.ENCHANT, x, pos.getY() + 0.6, z, 0, 0.1, 0);
    }
}
