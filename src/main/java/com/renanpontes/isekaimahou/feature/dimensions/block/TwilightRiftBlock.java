package com.renanpontes.isekaimahou.feature.dimensions.block;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.dimensions.DimKind;
import com.renanpontes.isekaimahou.feature.dimensions.DimTravel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Fenda do Crepúsculo: um rasgo no céu que só deixa passar quem venceu a Luz e a Sombra
 * (Soberano do Abismo e Serafim) e chegou ao Círculo 9. Os outros são empurrados de volta.
 * Dentro da dimensão da Fenda, o rasgo da arena leva de volta para casa sem exigências.
 */
public class TwilightRiftBlock extends Block implements EntityBlock, Portal {
    private static final String WARN_TAG = "isekai_mahou_rift_warn";

    public TwilightRiftBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TwilightRiftBlockEntity(pos, state);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || !entity.canUsePortal(false)) return;
        if (level.dimension().equals(DimKind.TWILIGHT.key) || DimTravel.canEnterTwilight(player)) {
            entity.setAsInsidePortal(this, pos);
            return;
        }
        // Barrado: empurrão para fora + aviso (no máximo a cada 2 s).
        Vec3 away = player.position().subtract(Vec3.atCenterOf(pos)).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
        player.setDeltaMovement(away.x * 0.8, 0.35, away.z * 0.8);
        player.hurtMarked = true;
        long now = level.getGameTime();
        if (now - player.getPersistentData().getLong(WARN_TAG) >= 40) {
            player.getPersistentData().putLong(WARN_TAG, now);
            List<Component> missing = DimTravel.missingTwilight(MagicApi.get(player));
            MutableComponent msg = Component.translatable("message.isekai_mahou.dimensions.rift_denied").withStyle(ChatFormatting.LIGHT_PURPLE);
            player.sendSystemMessage(msg);
            for (Component c : missing) player.sendSystemMessage(Component.literal(" - ").append(c).withStyle(ChatFormatting.GRAY));
            level.playSound(null, pos, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 1.0F, 0.6F);
            ((ServerLevel) level).sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    30, 0.4, 0.4, 0.4, 0.3);
        }
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return entity instanceof ServerPlayer p && !p.getAbilities().invulnerable ? 40 : 1;
    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        if (level.dimension().equals(DimKind.TWILIGHT.key)) return DimTravel.leave(level, entity, DimKind.TWILIGHT);
        if (!(entity instanceof ServerPlayer player) || !DimTravel.canEnterTwilight(player)) return null;
        return DimTravel.enter(level, entity, DimKind.TWILIGHT);
    }

    @Override
    public Transition getLocalTransition() {
        return Transition.CONFUSION;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.6;
            double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.6;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.6;
            // Partículas "sugadas" para o centro do rasgo.
            level.addParticle(random.nextBoolean() ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.WITCH, x, y, z,
                    (pos.getX() + 0.5 - x) * 0.08, (pos.getY() + 0.5 - y) * 0.08, (pos.getZ() + 0.5 - z) * 0.08);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
        }
        if (random.nextInt(80) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                    0.4F, 0.5F + random.nextFloat() * 0.2F, false);
        }
    }
}
