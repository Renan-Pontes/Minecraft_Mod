package com.renanpontes.isekaimahou.feature.structures_a.block;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.structures_a.ShrineKind;
import com.renanpontes.isekaimahou.feature.structures_a.ShrineRegistry;
import com.renanpontes.isekaimahou.feature.structures_a.ShrineTrial;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Estado do Altar do Santuário. A lógica da prova fica em {@link ShrineTrial}; aqui ficam a conversa com o
 * jogador (explicação + confirmação), a recarga depois de uma prova e o "ativo" que o cliente desenha.
 */
public class ShrineAltarBlockEntity extends BlockEntity {
    /** Tempo para confirmar com o segundo clique. */
    private static final int CONFIRM_TICKS = 120;

    private boolean active;
    private long cooldownUntil;
    /** Quem clicou uma vez e ainda pode confirmar (não é salvo). */
    private UUID confirmPlayer;
    private long confirmUntil;

    public ShrineAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ShrineRegistry.ALTAR_BE.get(), pos, state);
    }

    public Element element() {
        BlockState s = getBlockState();
        return s.hasProperty(ShrineAltarBlock.ELEMENT) ? s.getValue(ShrineAltarBlock.ELEMENT) : Element.FIRE;
    }

    public boolean isActive() {
        return active;
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================
    public static void serverTick(Level level, BlockPos pos, BlockState state, ShrineAltarBlockEntity be) {
        // Prova "fantasma" (o servidor reiniciou no meio): o altar volta ao normal.
        if (be.active && level.getGameTime() % 20 == 0 && level instanceof ServerLevel sl && ShrineTrial.at(sl, pos) == null) {
            be.setActive(false);
        }
    }

    public void setActive(boolean on) {
        if (active == on) return;
        active = on;
        sync();
    }

    /** Fim de uma prova (sucesso ou falha): o altar descansa alguns segundos antes de aceitar outra. */
    public void finishTrial(int cooldownTicks) {
        if (level != null) cooldownUntil = level.getGameTime() + cooldownTicks;
        setActive(false);
    }

    /** Clique no altar. */
    public void interact(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl)) return;
        ShrineKind kind = ShrineKind.of(element());
        Vec3 top = Vec3.atCenterOf(worldPosition).add(0, 0.6, 0);

        ShrineTrial running = ShrineTrial.at(sl, worldPosition);
        if (running != null) {
            // Agachado + clique: desiste (a única saída "limpa" do alto do Pico dos Ventos).
            if (player.isShiftKeyDown() && running.hasParticipant(player.getUUID())) running.giveUp(player);
            else running.join(player);
            return;
        }
        if (ShrineTrial.of(player.getUUID()) != null) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.shrine.other_trial").withStyle(ChatFormatting.RED), true);
            return;
        }
        MagicData data = MagicApi.get(player);
        if (data.hasFlag(kind.progressKey())) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.shrine.completed", kind.displayName())
                    .withStyle(ChatFormatting.GRAY), true);
            sl.sendParticles(ElementFx.dust(kind.element, 1.0F), top.x, top.y, top.z, 12, 0.3, 0.3, 0.3, 0);
            sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.2F);
            return;
        }
        if (sl.getDifficulty() == Difficulty.PEACEFUL) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.shrine.peaceful").withStyle(ChatFormatting.RED), true);
            return;
        }
        long now = sl.getGameTime();
        if (now < cooldownUntil) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.shrine.recovering").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        if (!player.getUUID().equals(confirmPlayer) || now > confirmUntil) {
            // Primeiro clique: explica a prova e pede confirmação (evita começar sem querer).
            confirmPlayer = player.getUUID();
            confirmUntil = now + CONFIRM_TICKS;
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.shrine.title", kind.displayName())
                    .withStyle(ChatFormatting.BOLD));
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.shrine.info", kind.element.displayName())
                    .withStyle(ChatFormatting.GRAY));
            if (data.isAwakened(kind.element)) {
                player.sendSystemMessage(Component.translatable("message.isekai_mahou.shrine.info_awakened", kind.element.displayName())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.shrine.confirm").withStyle(ChatFormatting.YELLOW));
            sl.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 0.7F);
            ElementFx.ring(sl, Vec3.atBottomCenterOf(worldPosition).add(0, 0.05, 0), kind.element, 1.6, 20);
            return;
        }

        confirmPlayer = null;
        ShrineTrial.start(sl, worldPosition, kind, player);
        setActive(true);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, Block.UPDATE_CLIENTS);
        }
    }

    // =====================================================================================
    //  Salvar / sincronizar
    // =====================================================================================
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("active", active);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        active = tag.getBoolean("active");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", active);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
