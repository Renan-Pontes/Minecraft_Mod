package com.renanpontes.isekaimahou.feature.dimensions.block;

import java.util.Optional;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.feature.dimensions.PortalKind;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Moldura de portal (Plano Elemental / Abismo). Desde a v0.9 o portal vem PRONTO e adormecido nas estruturas do
 * Mundo Normal (Portão Elemental, Fenda Abissal); clique numa peça da moldura com o item-chave para despertá-lo: exige
 * o Círculo mínimo e gasta mana/oferenda. Molduras retangulares montadas à mão (mundos antigos, criativo) continuam
 * funcionando do mesmo jeito (interior mínimo 2×3). Clicar de mão vazia mostra como despertar.
 */
public class PortalFrameBlock extends Block {
    private final PortalKind kind;

    public PortalFrameBlock(Properties properties, PortalKind kind) {
        super(properties);
        this.kind = kind;
    }

    public PortalKind kind() {
        return kind;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(kind.key())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        ServerLevel sl = (ServerLevel) level;
        boolean creative = sp.getAbilities().instabuild;
        MagicData d = MagicApi.get(sp);

        // Portal já aceso: avisa sem gastar nada (antes, isso aparecia como "moldura incompleta").
        if (FrameShape.touchesPortal(level, pos, kind.portal())) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.dimensions.portal_active")
                    .withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.CONSUME;
        }
        if (d.circle() < kind.minCircle && !creative) {
            fail(sp, sl, pos, Component.translatable("message.isekai_mahou.dimensions.need_circle", kind.minCircle, kind.dim.displayName()));
            return ItemInteractionResult.CONSUME;
        }
        Optional<FrameShape> shape = FrameShape.findAround(level, pos, hit.getDirection(), this);
        if (shape.isEmpty()) {
            fail(sp, sl, pos, Component.translatable("message.isekai_mahou.dimensions.frame_incomplete"));
            return ItemInteractionResult.CONSUME;
        }
        if (kind.manaCost > 0 && !creative && !d.consumeMana(kind.manaCost)) {
            fail(sp, sl, pos, Component.translatable("message.isekai_mahou.dimensions.need_mana", kind.manaCost));
            return ItemInteractionResult.CONSUME;
        }
        if (kind.consumesKey && !creative) stack.shrink(1);

        FrameShape s = shape.get();
        s.fill(level, kind.portal());
        openFx(sl, s);

        // Primeira abertura deste tipo de portal: cutscene curta em volta do jogador.
        if (!d.hasFlag(kind.openedFlag())) {
            d.setFlag(kind.openedFlag(), true);
            Cutscenes.play(sp, "portal", sp, kind.dim.portalOpenTitle().withStyle(ChatFormatting.BOLD),
                    kind.dim.displayName(), kind.dim.color, 60, true);
        }
        return ItemInteractionResult.CONSUME;
    }

    /** Mão vazia: lembra como despertar o portal (sem gastar nada). Com outro item na mão o clique segue normal. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Item-chave na outra mão: deixa o clique seguir para ela (desperta como antes, sem a dica no meio).
        if (!player.getMainHandItem().isEmpty() || player.getOffhandItem().is(kind.key())) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (FrameShape.touchesPortal(level, pos, kind.portal())) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.dimensions.portal_active")
                    .withStyle(ChatFormatting.GRAY), true);
        } else {
            Component how = kind.manaCost > 0
                    ? Component.translatable("tooltip.isekai_mahou.dimensions.frame_mana", kind.key().getDescription(), kind.minCircle, kind.manaCost)
                    : Component.translatable("tooltip.isekai_mahou.dimensions.frame_offer", kind.key().getDescription(), kind.minCircle);
            player.displayClientMessage(Component.translatable("message.isekai_mahou.dimensions.dormant", kind.dim.displayName(), how)
                    .withColor(kind.dim.color), true);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, kind == PortalKind.ABYSS ? 0.6F : 1.2F);
        }
        return InteractionResult.CONSUME;
    }

    private void fail(ServerPlayer player, ServerLevel level, BlockPos pos, Component msg) {
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.4F);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.25, 0.1, 0.25, 0.01);
    }

    private void openFx(ServerLevel level, FrameShape s) {
        BlockPos c = s.center();
        Vec3 center = Vec3.atCenterOf(c);
        int rgb = kind.dim.color;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), 2.0F);
        level.sendParticles(dust, center.x, center.y, center.z, 80, s.width() * 0.4, s.height() * 0.4, s.width() * 0.4, 0.0);
        level.sendParticles(kind == PortalKind.ABYSS ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD,
                center.x, center.y, center.z, 40, s.width() * 0.3, s.height() * 0.3, s.width() * 0.3, 0.05);
        level.playSound(null, c, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6F, kind == PortalKind.ABYSS ? 0.6F : 1.3F);
        level.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** Brilho discreto das gemas da moldura. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(12) != 0) return;
        Direction d = Direction.getRandom(random);
        BlockPos side = pos.relative(d);
        if (level.getBlockState(side).isSolidRender(level, side)) return;
        double x = pos.getX() + 0.5 + d.getStepX() * 0.52;
        double y = pos.getY() + 0.5 + d.getStepY() * 0.52;
        double z = pos.getZ() + 0.5 + d.getStepZ() * 0.52;
        if (kind == PortalKind.ABYSS) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z, 0.0, 0.0, 0.0);
        } else {
            int[] colors = {0xFF6A2B, 0x3FA9FF, 0xA0743C, 0x9FF0D0};
            int c = colors[random.nextInt(colors.length)];
            level.addParticle(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 0.8F),
                    x, y, z, 0.0, 0.02, 0.0);
        }
    }
}
