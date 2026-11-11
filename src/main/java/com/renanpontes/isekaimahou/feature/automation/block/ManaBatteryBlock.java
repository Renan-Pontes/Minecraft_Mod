package com.renanpontes.isekaimahou.feature.automation.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.AutoRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bateria de Mana. Clique: mostra a carga. Agachado + mão vazia: despeja o seu mana nela.
 * Quebrada, o item guarda a carga. Comparador lê o nível.
 */
public class ManaBatteryBlock extends BaseEntityBlock {
    public static final MapCodec<ManaBatteryBlock> CODEC = simpleCodec(ManaBatteryBlock::new);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);

    public ManaBatteryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        int stored = ctx.getItemInHand().getOrDefault(AutoRegistries.BATTERY_MANA.get(), 0);
        return defaultBlockState().setValue(CHARGE, ManaBatteryBlockEntity.chargeLevel(stored));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManaBatteryBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof ManaBatteryBlockEntity battery)) {
            return InteractionResult.CONSUME;
        }
        if (sp.isSecondaryUseActive()) transfer(sp, battery);
        else showStatus(sp, battery);
        return InteractionResult.CONSUME;
    }

    private static void showStatus(ServerPlayer sp, ManaBatteryBlockEntity battery) {
        int pct = Math.round(battery.mana() * 100F / ManaBatteryBlockEntity.CAPACITY);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.automation.battery_status",
                battery.mana(), ManaBatteryBlockEntity.CAPACITY, pct).withColor(AutoFx.MANA_RGB), true);
    }

    /** Despeja o mana do jogador na bateria (tudo o que couber). */
    private static void transfer(ServerPlayer sp, ManaBatteryBlockEntity battery) {
        ServerLevel level = sp.serverLevel();
        MagicData data = MagicApi.get(sp);
        if (battery.space() <= 0) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.automation.battery_full").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        int amount = Math.min((int) Math.floor(data.mana()), battery.space());
        if (amount <= 0) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.automation.no_player_mana").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!data.consumeMana(amount)) return;
        battery.insert(amount);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.automation.battery_charged",
                amount, battery.mana(), ManaBatteryBlockEntity.CAPACITY).withColor(AutoFx.MANA_RGB), true);
        Vec3 c = Vec3.atCenterOf(battery.getBlockPos());
        AutoFx.line(level, sp.position().add(0, 1.1, 0), c, AutoFx.dust(AutoFx.MANA_RGB, 1.0F), 0.3);
        level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y + 0.4, c.z, 20, 0.35, 0.35, 0.35, 0.6);
        AutoFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 1.2F);
        AutoFx.sound(level, c, SoundEvents.BEACON_POWER_SELECT, 0.4F, 1.6F);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ManaBatteryBlockEntity b ? ManaBatteryBlockEntity.comparatorSignal(b.mana()) : 0;
    }

    /** Faíscas de mana subindo, mais quanto mais cheia. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int charge = state.getValue(CHARGE);
        if (charge == 0 || random.nextInt(6 - charge) != 0) return;
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        level.addParticle(AutoFx.dust(AutoFx.MANA_RGB, 0.7F), x, pos.getY() + 1.05, z, 0, 0.02, 0);
        if (charge == 4 && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x, pos.getY() + 1.1, z, 0, 0.03, 0);
        }
    }
}
