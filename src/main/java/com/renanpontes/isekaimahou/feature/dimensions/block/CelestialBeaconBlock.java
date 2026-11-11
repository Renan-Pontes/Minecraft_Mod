package com.renanpontes.isekaimahou.feature.dimensions.block;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.feature.dimensions.DimKind;
import com.renanpontes.isekaimahou.feature.dimensions.DimRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Farol Celestial ("Escada de Luz"). Desde a v0.9 ele espera apagado sobre o ouro no alto da Escadaria Celeste (estrutura
 * do Mundo Normal); ative ao MEIO-DIA com uma Essência de Luz (Círculo 6+): um feixe de luz sobe dele; entrar no feixe
 * leva ao Santuário Celestial. Faróis de mundos antigos continuam valendo (sobre ouro, a céu aberto).
 * Tirar o ouro de baixo (ou quebrar o farol) apaga o feixe. Clicar de mão vazia no farol apagado mostra como acender.
 */
public class CelestialBeaconBlock extends Block implements EntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final int MIN_CIRCLE = 6;
    public static final int BEAM_HEIGHT = 4;
    /** Janela do meio-dia (6000 = sol a pino): das 11h às 13h do jogo. */
    public static final int NOON_START = 5000, NOON_END = 7000;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(3, 4, 3, 13, 12, 13), Block.box(1, 12, 1, 15, 16, 15));

    public CelestialBeaconBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CelestialBeaconBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(CoreRegistries.ESSENCES.get(Element.LIGHT).get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        ServerLevel sl = (ServerLevel) level;
        boolean creative = sp.getAbilities().instabuild;
        MagicData d = MagicApi.get(sp);

        Component problem = null;
        if (state.getValue(ACTIVE)) problem = Component.translatable("message.isekai_mahou.dimensions.beacon_already");
        else if (d.circle() < MIN_CIRCLE && !creative) {
            problem = Component.translatable("message.isekai_mahou.dimensions.need_circle", MIN_CIRCLE, DimKind.CELESTIAL.displayName());
        } else if (!level.getBlockState(pos.below()).is(Blocks.GOLD_BLOCK)) {
            problem = Component.translatable("message.isekai_mahou.dimensions.beacon_gold");
        } else if (!level.canSeeSky(pos.above())) {
            problem = Component.translatable("message.isekai_mahou.dimensions.beacon_sky");
        } else if (!isNoon(level) && !creative) {
            problem = Component.translatable("message.isekai_mahou.dimensions.beacon_noon");
        } else if (!beamSpaceFree(level, pos)) {
            problem = Component.translatable("message.isekai_mahou.dimensions.beacon_blocked");
        }
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withStyle(ChatFormatting.GOLD), true);
            sl.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 1.4F);
            return ItemInteractionResult.CONSUME;
        }
        if (!creative) stack.shrink(1);
        activate(sl, pos, state);

        String flag = "dimensions.portal_opened." + DimKind.CELESTIAL.id;
        if (!d.hasFlag(flag)) {
            d.setFlag(flag, true);
            Cutscenes.play(sp, "portal", sp, DimKind.CELESTIAL.portalOpenTitle().withStyle(ChatFormatting.BOLD),
                    DimKind.CELESTIAL.displayName(), DimKind.CELESTIAL.color, 60, true);
        }
        return ItemInteractionResult.CONSUME;
    }

    /** Mão vazia no farol apagado: como acender e quanto falta para o meio-dia. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Essência de Luz na outra mão: deixa o clique seguir para ela (acende como antes).
        if (state.getValue(ACTIVE) || !player.getMainHandItem().isEmpty()
                || player.getOffhandItem().is(CoreRegistries.ESSENCES.get(Element.LIGHT).get())) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Component how = Component.translatable("tooltip.isekai_mahou.dimensions.beacon_noon",
                CoreRegistries.ESSENCES.get(Element.LIGHT).get().getDescription(), MIN_CIRCLE);
        Component when = isNoon(level) ? Component.translatable("message.isekai_mahou.dimensions.beacon_now")
                : Component.translatable("message.isekai_mahou.dimensions.beacon_wait", ticksToNoon(level) / 1000 + 1);
        player.displayClientMessage(Component.translatable("message.isekai_mahou.dimensions.beacon_dormant", how, when)
                .withColor(DimKind.CELESTIAL.color), true);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.6F);
        return InteractionResult.CONSUME;
    }

    /** Ticks até o início da janela do meio-dia (0 se já é). */
    public static long ticksToNoon(Level level) {
        long t = level.getDayTime() % 24000L;
        if (t >= NOON_START && t <= NOON_END) return 0;
        return t < NOON_START ? NOON_START - t : 24000L - t + NOON_START;
    }

    public static boolean isNoon(Level level) {
        long t = level.getDayTime() % 24000L;
        return t >= NOON_START && t <= NOON_END;
    }

    private static boolean beamSpaceFree(Level level, BlockPos pos) {
        for (int i = 1; i <= BEAM_HEIGHT; i++) {
            BlockState s = level.getBlockState(pos.above(i));
            if (!s.isAir() && !s.is(DimRegistry.CELESTIAL_PORTAL.get())) return false;
        }
        return true;
    }

    /** Liga o farol e ergue a coluna de luz (também usado pelo hub do Santuário, que já vem aceso no template). */
    public static void activate(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(ACTIVE, true), Block.UPDATE_ALL);
        BlockState beam = DimRegistry.CELESTIAL_PORTAL.get().defaultBlockState();
        for (int i = 1; i <= BEAM_HEIGHT; i++) level.setBlock(pos.above(i), beam, Block.UPDATE_ALL);
        Vec3 c = Vec3.atCenterOf(pos);
        ElementFx.burst(level, c.add(0, 1.0, 0), Element.LIGHT, 60, 0.6);
        for (int i = 0; i < 24; i++) {
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1 + i * 0.5, c.z, 3, 0.15, 0.1, 0.15, 0.02);
        }
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2F, 1.2F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.5F);
    }

    private static void deactivate(Level level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(ACTIVE, false), Block.UPDATE_ALL);
        clearBeam(level, pos);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void clearBeam(Level level, BlockPos pos) {
        for (int i = 1; i <= BEAM_HEIGHT + 1; i++) {
            BlockPos p = pos.above(i);
            if (level.getBlockState(p).is(DimRegistry.CELESTIAL_PORTAL.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moved);
        if (!level.isClientSide && state.getValue(ACTIVE) && !level.getBlockState(pos.below()).is(Blocks.GOLD_BLOCK)) {
            deactivate(level, pos, state);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && state.getValue(ACTIVE)) clearBeam(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE)) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 1.0,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.08 + random.nextDouble() * 0.05, 0.0);
        } else if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.WAX_OFF, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.0,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.02, 0.0);
        }
    }
}
