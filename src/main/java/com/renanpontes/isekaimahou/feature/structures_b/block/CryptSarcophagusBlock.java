package com.renanpontes.isekaimahou.feature.structures_b.block;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Sarcófago Antigo: bloco de duas partes (como a cama). {@link #FACING} aponta do pé para a cabeça.
 * {@link #OPEN}: tampa deslocada. {@link #SEALED}: veio lacrado da Cripta Esquecida — ao abrir pela
 * primeira vez solta o tesouro ({@code chests/forgotten_crypt_sarcophagus}) e às vezes desperta um morto.
 * Sarcófagos postos por jogadores nascem sem lacre (só abrem/fecham, sem loot).
 */
public class CryptSarcophagusBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CryptSarcophagusBlock> CODEC = simpleCodec(CryptSarcophagusBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            IsekaiMahou.id("chests/forgotten_crypt_sarcophagus"));

    // Caixa de pedra (até 11 px) + tampa (até 14 px) quando fechado.
    private static final VoxelShape BOX_NS = Block.box(1, 0, 0, 15, 11, 16);
    private static final VoxelShape BOX_EW = Block.box(0, 0, 1, 16, 11, 15);
    private static final VoxelShape LID_NS = Shapes.or(BOX_NS, Block.box(0, 11, 0, 16, 14, 16));
    private static final VoxelShape LID_EW = Shapes.or(BOX_EW, Block.box(0, 11, 0, 16, 14, 16));

    /** Mortos que podem despertar: [id da entidade, peso]. Ids de outros módulos são opcionais. */
    private static final String[][] RISEN = {
            {"minecraft:skeleton", "5"},
            {"minecraft:zombie", "3"},
            {"minecraft:husk", "1"},
            {"isekai_mahou:wandering_spirit", "2"}
    };

    public CryptSarcophagusBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT)
                .setValue(OPEN, false).setValue(SEALED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OPEN, SEALED);
    }

    // ---------------- Forma ----------------
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        boolean ns = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        if (state.getValue(OPEN)) return ns ? BOX_NS : BOX_EW;
        return ns ? LID_NS : LID_EW;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ---------------- Duas partes (mesma lógica da cama) ----------------
    private static Direction towardsOther(BedPart part, Direction facing) {
        return part == BedPart.FOOT ? facing : facing.getOpposite();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction dir = ctx.getHorizontalDirection();
        BlockPos head = ctx.getClickedPos().relative(dir);
        Level level = ctx.getLevel();
        return level.getBlockState(head).canBeReplaced(ctx) && level.getWorldBorder().isWithinBounds(head)
                ? defaultBlockState().setValue(FACING, dir)
                : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BlockPos head = pos.relative(state.getValue(FACING));
            level.setBlock(head, state.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (dir == towardsOther(state.getValue(PART), state.getValue(FACING))) {
            // A outra metade sumiu → esta some também (e solta o item se for a cabeça).
            return neighbor.is(this) && neighbor.getValue(PART) != state.getValue(PART)
                    ? state.setValue(OPEN, neighbor.getValue(OPEN)).setValue(SEALED, neighbor.getValue(SEALED))
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            // Quebrar um sarcófago lacrado não perde o tesouro: ele é liberado como se tivesse sido aberto.
            if (state.getValue(SEALED) && level instanceof ServerLevel sl) release(sl, pos, state, player);
            if (player.isCreative() && state.getValue(PART) == BedPart.FOOT) {
                BlockPos other = pos.relative(towardsOther(BedPart.FOOT, state.getValue(FACING)));
                BlockState os = level.getBlockState(other);
                if (os.is(this) && os.getValue(PART) == BedPart.HEAD) {
                    level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, other, Block.getId(os));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Explosão num sarcófago lacrado também libera o tesouro (sem isso o loot da cripta sumiria com TNT/creeper). */
    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (state.getValue(SEALED) && level instanceof ServerLevel sl) {
            Player cause = explosion.getIndirectSourceEntity() instanceof Player p ? p : null;
            release(sl, pos, state, cause);
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---------------- Abrir / fechar ----------------
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        boolean open = !state.getValue(OPEN);
        BlockPos other = pos.relative(towardsOther(state.getValue(PART), state.getValue(FACING)));
        if (open && state.getValue(SEALED)) release(sl, pos, state, player);
        setBoth(sl, pos, other, open);
        sl.playSound(null, pos, open ? SoundEvents.GRINDSTONE_USE : SoundEvents.DEEPSLATE_BRICKS_PLACE,
                SoundSource.BLOCKS, 0.8F, open ? 0.55F : 0.7F);
        return InteractionResult.CONSUME;
    }

    private void setBoth(ServerLevel level, BlockPos pos, BlockPos other, boolean open) {
        BlockState a = level.getBlockState(pos);
        if (a.is(this)) level.setBlock(pos, a.setValue(OPEN, open).setValue(SEALED, false), Block.UPDATE_ALL);
        BlockState b = level.getBlockState(other);
        if (b.is(this)) level.setBlock(other, b.setValue(OPEN, open).setValue(SEALED, false), Block.UPDATE_ALL);
    }

    /** Primeiro "abrir" de um sarcófago lacrado: tesouro + chance de despertar um morto, com almas escapando. */
    private void release(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player) {
        BlockPos other = pos.relative(towardsOther(state.getValue(PART), state.getValue(FACING)));
        // Desfaz o lacre nas duas metades antes de tudo, para nunca liberar duas vezes.
        BlockState b = level.getBlockState(other);
        if (b.is(this)) level.setBlock(other, b.setValue(SEALED, false), Block.UPDATE_CLIENTS);
        BlockState a = level.getBlockState(pos);
        if (a.is(this)) level.setBlock(pos, a.setValue(SEALED, false), Block.UPDATE_CLIENTS);

        Vec3 center = Vec3.atCenterOf(pos).add(Vec3.atCenterOf(other)).scale(0.5).add(0, 0.4, 0);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(LOOT);
        LootParams.Builder params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, center);
        if (player != null) params.withParameter(LootContextParams.THIS_ENTITY, player).withLuck(player.getLuck());
        for (ItemStack stack : table.getRandomItems(params.create(LootContextParamSets.CHEST))) {
            Block.popResource(level, BlockPos.containing(center), stack);
        }

        level.sendParticles(ParticleTypes.SOUL, center.x, center.y, center.z, 14, 0.5, 0.3, 0.5, 0.03);
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 0.3, center.z, 6, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, BlockPos.containing(center), SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.2F, 0.8F);

        if (level.random.nextFloat() < 0.35F && level.getDifficulty() != Difficulty.PEACEFUL) {
            awaken(level, BlockPos.containing(center).above(), player);
        }
    }

    private void awaken(ServerLevel level, BlockPos at, @Nullable Player player) {
        int total = 0;
        for (String[] r : RISEN) if (BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(r[0]))) total += Integer.parseInt(r[1]);
        if (total <= 0) return;
        int roll = level.random.nextInt(total);
        for (String[] r : RISEN) {
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(r[0]));
            if (type.isEmpty()) continue;
            roll -= Integer.parseInt(r[1]);
            if (roll >= 0) continue;
            Entity e = type.get().spawn(level, at, MobSpawnType.EVENT);
            if (e instanceof Mob mob && player != null && !player.isCreative()) mob.setTarget(player);
            level.playSound(null, at, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 0.6F, 0.6F);
            level.sendParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 20, 0.4, 0.6, 0.4, 0.02);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.sarcophagus_awaken")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), true);
            }
            return;
        }
    }
}
