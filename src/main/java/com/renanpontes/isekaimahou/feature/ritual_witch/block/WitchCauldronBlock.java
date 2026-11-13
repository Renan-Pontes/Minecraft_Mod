package com.renanpontes.isekaimahou.feature.ritual_witch.block;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchContent;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchHeat;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchRecipes;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.RitualSpoonItem;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Caldeirão Ritual da Bruxa. Precisa de calor embaixo (fogueira = baixo; fogo / fogueira de alma = médio;
 * fogo de alma / magma / lava = alto). Interações: balde de água enche, ingredientes na ordem, Colher Ritual
 * mexe, frasco de vidro pega a poção pronta, balde vazio esvazia/limpa o lodo.
 */
public class WitchCauldronBlock extends BaseEntityBlock {
    public static final MapCodec<WitchCauldronBlock> CODEC = simpleCodec(WitchCauldronBlock::new);
    /** Aceso = poção pronta brilhando dentro. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape INSIDE = box(2.0, 4.0, 2.0, 14.0, 16.0, 14.0);
    private static final VoxelShape SHAPE = Shapes.join(Shapes.block(),
            Shapes.or(box(0.0, 0.0, 4.0, 16.0, 2.0, 12.0), box(4.0, 0.0, 0.0, 12.0, 2.0, 16.0), INSIDE), BooleanOp.ONLY_FIRST);

    public WitchCauldronBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return INSIDE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WitchCauldronBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, WitchContent.CAULDRON_BE.get(),
                level.isClientSide ? WitchCauldronBlockEntity::clientTick : WitchCauldronBlockEntity::serverTick);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WitchCauldronBlockEntity be ? be.comparatorSignal() : 0;
    }

    // =====================================================================================
    //  Interação
    // =====================================================================================
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WitchCauldronBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        boolean client = level.isClientSide;
        Item item = stack.getItem();
        WitchCauldronBlockEntity.Phase phase = be.phase();

        // Balde de água: enche
        if (item == Items.WATER_BUCKET) {
            if (phase == WitchCauldronBlockEntity.Phase.RUINED) return busy(player, client, "message.isekai_mahou.witch.sludge");
            if (phase != WitchCauldronBlockEntity.Phase.EMPTY) return busy(player, client, "message.isekai_mahou.witch.full");
            if (!client && be.fillWater()) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
            }
            return ItemInteractionResult.sidedSuccess(client);
        }
        // Balde vazio: recolhe a água ou descarta o preparo/lodo
        if (item == Items.BUCKET) {
            if (phase == WitchCauldronBlockEntity.Phase.EMPTY) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (phase == WitchCauldronBlockEntity.Phase.READY) return busy(player, client, "message.isekai_mahou.witch.use_bottles");
            if (!client) {
                WitchCauldronBlockEntity.Phase old = be.drain((ServerLevel) level);
                if (old == WitchCauldronBlockEntity.Phase.WATER) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
                    level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                } else {
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.witch.cleaned").withStyle(ChatFormatting.GRAY), true);
                }
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
            }
            return ItemInteractionResult.sidedSuccess(client);
        }
        // Frasco de vidro: pega uma dose pronta
        if (item == Items.GLASS_BOTTLE) {
            if (phase == WitchCauldronBlockEntity.Phase.EMPTY) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (phase != WitchCauldronBlockEntity.Phase.READY) {
                return busy(player, client, phase == WitchCauldronBlockEntity.Phase.RUINED
                        ? "message.isekai_mahou.witch.sludge" : "message.isekai_mahou.witch.not_ready");
            }
            if (!client) {
                ItemStack dose = be.takeBottle((ServerLevel) level);
                if (!dose.isEmpty()) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, dose));
                    level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                }
            }
            return ItemInteractionResult.sidedSuccess(client);
        }
        // Colher Ritual: mexe
        if (item instanceof RitualSpoonItem) {
            if (player.getCooldowns().isOnCooldown(item)) return ItemInteractionResult.CONSUME;
            if (phase == WitchCauldronBlockEntity.Phase.EMPTY) return busy(player, client, "message.isekai_mahou.witch.need_water");
            if (phase == WitchCauldronBlockEntity.Phase.READY || phase == WitchCauldronBlockEntity.Phase.RUINED) {
                return busy(player, client, phase == WitchCauldronBlockEntity.Phase.READY
                        ? "message.isekai_mahou.witch.use_bottles" : "message.isekai_mahou.witch.sludge");
            }
            if (!client) {
                be.stir((ServerLevel) level, player);
                player.getCooldowns().addCooldown(item, RitualSpoonItem.COOLDOWN);
            }
            return ItemInteractionResult.sidedSuccess(client);
        }
        // Ingredientes
        if (WitchRecipes.isIngredient(item, client)) {
            switch (phase) {
                case EMPTY -> { return busy(player, client, "message.isekai_mahou.witch.need_water"); }
                case READY -> { return busy(player, client, "message.isekai_mahou.witch.use_bottles"); }
                case RUINED -> { return busy(player, client, "message.isekai_mahou.witch.sludge"); }
                default -> { }
            }
            if (!client) {
                ItemStack remainder = stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : ItemStack.EMPTY;
                if (be.addIngredient((ServerLevel) level, player, item)) {
                    stack.consume(1, player);
                    if (!remainder.isEmpty() && !player.hasInfiniteMaterials() && !player.getInventory().add(remainder)) {
                        player.drop(remainder, false);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(client);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Mão vazia: mostra o estado do caldeirão (fase, calor, ingredientes). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WitchCauldronBlockEntity be)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            WitchHeat heat = WitchHeat.below(level, pos);
            Component msg = switch (be.phase()) {
                case EMPTY -> Component.translatable("message.isekai_mahou.witch.status_empty", heat.displayName());
                case WATER -> Component.translatable("message.isekai_mahou.witch.status_water", heat.displayName());
                case BREWING -> Component.translatable("message.isekai_mahou.witch.status_brewing", be.ingredients().size(), be.stirs(),
                        heat.displayName());
                case READY -> Component.translatable("message.isekai_mahou.witch.status_ready", be.result().getHoverName(), be.bottles());
                case RUINED -> Component.translatable("message.isekai_mahou.witch.sludge");
            };
            player.displayClientMessage(msg.copy().withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ItemInteractionResult busy(Player player, boolean client, String key) {
        if (!client) player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
        return ItemInteractionResult.CONSUME;
    }

    // =====================================================================================
    //  Visual (cliente): bolhas pelo calor, cor do preparo, fumaça do lodo
    // =====================================================================================
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof WitchCauldronBlockEntity be)) return;
        WitchHeat heat = WitchHeat.below(level, pos);
        double x0 = pos.getX(), y0 = pos.getY(), z0 = pos.getZ();
        float h = be.liquidHeight();
        double surf = y0 + (h < 0 ? 0.3 : h);
        int argb = 0xFF000000 | be.color();
        switch (be.phase()) {
            case EMPTY -> {
                if (heat != WitchHeat.NONE && random.nextInt(4) == 0) {
                    level.addParticle(Vfx.smoke(), x0 + 0.3 + random.nextDouble() * 0.4, surf, z0 + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
                }
            }
            case WATER, BREWING -> {
                if (heat == WitchHeat.NONE) break;
                int bubbles = heat.ordinal() * 2;
                for (int i = 0; i < bubbles; i++) {
                    if (random.nextInt(3) != 0) continue;
                    level.addParticle(ParticleTypes.BUBBLE_POP, x0 + 0.2 + random.nextDouble() * 0.6, surf + 0.01,
                            z0 + 0.2 + random.nextDouble() * 0.6, 0, 0.02 * heat.ordinal(), 0);
                }
                if (be.phase() == WitchCauldronBlockEntity.Phase.BREWING && random.nextInt(2) == 0) {
                    level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, argb),
                            x0 + 0.25 + random.nextDouble() * 0.5, surf + 0.05, z0 + 0.25 + random.nextDouble() * 0.5, 0, 0, 0);
                }
                if (heat == WitchHeat.HIGH && random.nextInt(3) == 0) {
                    level.addParticle(Vfx.cloud(), x0 + 0.3 + random.nextDouble() * 0.4, surf + 0.1,
                            z0 + 0.3 + random.nextDouble() * 0.4, 0, 0.03, 0);
                }
                if (random.nextInt(14 - heat.ordinal() * 3) == 0) {
                    level.playLocalSound(x0 + 0.5, surf, z0 + 0.5, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS,
                            0.4F + heat.ordinal() * 0.15F, 0.7F + random.nextFloat() * 0.5F, false);
                }
            }
            case READY -> {
                if (random.nextInt(2) == 0) {
                    level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, argb),
                            x0 + 0.25 + random.nextDouble() * 0.5, surf + 0.05, z0 + 0.25 + random.nextDouble() * 0.5, 0, 0, 0);
                }
                if (random.nextInt(5) == 0) {
                    level.addParticle(Vfx.witch(), x0 + 0.3 + random.nextDouble() * 0.4, surf + 0.2,
                            z0 + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
                }
                if (random.nextInt(60) == 0) {
                    level.playLocalSound(x0 + 0.5, surf, z0 + 0.5, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.4F,
                            1.2F + random.nextFloat() * 0.4F, false);
                }
            }
            case RUINED -> {
                level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                        x0 + 0.25 + random.nextDouble() * 0.5, surf + 0.05, z0 + 0.25 + random.nextDouble() * 0.5, 0, 0.03, 0);
                if (heat != WitchHeat.NONE && random.nextInt(20) == 0) {
                    level.playLocalSound(x0 + 0.5, surf, z0 + 0.5, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.3F, 0.5F, false);
                }
            }
        }
    }
}
