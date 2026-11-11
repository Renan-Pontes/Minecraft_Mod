package com.renanpontes.isekaimahou.feature.ascension.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.ascension.AscensionAltarGuard;
import com.renanpontes.isekaimahou.feature.ascension.AscensionAltars;
import com.renanpontes.isekaimahou.feature.ascension.AscensionPattern;
import com.renanpontes.isekaimahou.feature.ascension.AscensionRegistry;
import com.renanpontes.isekaimahou.feature.ascension.AscensionRules;
import com.renanpontes.isekaimahou.feature.ascension.AscensionTributes;
import com.renanpontes.isekaimahou.feature.ascension.Tribulation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Estado do Núcleo de Ascensão. A lógica da Tribulação fica em {@link Tribulation}; aqui ficam:
 * a conferência/ativação, o tributo consumido (devolvido só se o ritual for interrompido por fora,
 * ex.: o servidor reiniciou) e os dados que o cliente desenha (fase, círculo no chão, prévia fantasma).
 *
 * <p><b>Altar público</b> (v0.9): {@code PublicAltar:1b} no NBT do Núcleo (no topo OU em {@code NeoForgeData}, como as
 * estruturas gravam). Serve a qualquer jogador sem montar nada: não confere o padrão de blocos e aceita o tributo
 * direto do INVENTÁRIO de quem ativa (além dos pedestais); o Núcleo e as peças em volta não quebram em sobrevivência
 * ({@link AscensionAltarGuard}). Em qualquer Núcleo, o item "Tributo do Círculo N" vale no inventário.
 *
 * <p>v1.0: a Ascensão acontece nos altares prontos — um Núcleo caseiro (não público) só ativa com a regra
 * {@code isekaiPrivateAltars}; sem ela, o clique explica e aponta o Altar Sagrado mais próximo.
 */
public class AscensionCoreBlockEntity extends BlockEntity {
    public static final int PHASE_IDLE = 0;
    public static final int PHASE_INTRO = 1;
    public static final int PHASE_TRIAL = 2;
    private static final int PREVIEW_TICKS = 240;

    public static final String PUBLIC_TAG = "PublicAltar";

    private int phase = PHASE_IDLE;
    /** Altar público (cidades, Academia, Santuário): qualquer jogador usa, sem montar nada, e não quebra em sobrevivência. */
    private boolean publicAltar;
    /** Altares caseiros liberados pela regra de jogo (o servidor manda junto com o Núcleo; o letreiro usa). */
    private boolean privateAllowed;
    /** Já entrou na lista de altares públicos do nível (só nesta sessão do servidor). */
    private boolean registered;
    private int target;
    private int boundary;
    private long phaseStart;
    private long previewUntil;
    /** Prévia fantasma: posições que faltam (dx, dz, parte). */
    private final List<AscensionPattern.Slot> preview = new ArrayList<>();
    /** Tributo absorvido no começo do ritual (só no servidor/disco). */
    private final List<ItemStack> consumed = new ArrayList<>();

    public AscensionCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AscensionRegistry.CORE_BE.get(), pos, state);
    }

    // =====================================================================================
    //  Leitura (cliente/renderer)
    // =====================================================================================
    public int phase() { return phase; }
    public int target() { return target; }
    public int boundary() { return boundary; }
    public long phaseStart() { return phaseStart; }
    public long previewUntil() { return previewUntil; }
    public List<AscensionPattern.Slot> preview() { return preview; }
    public boolean isRitualRunning() { return phase != PHASE_IDLE; }
    public boolean isPublic() { return publicAltar; }
    /** Este Núcleo pode fazer a Ascensão (público, ou caseiro com a regra {@code isekaiPrivateAltars}). */
    public boolean usable() { return publicAltar || privateAllowed; }

    /** Liga/desliga o altar público (comando de operador; estruturas usam o NBT). */
    public void setPublic(boolean on) {
        if (publicAltar == on) return;
        publicAltar = on;
        if (on) getPersistentData().putBoolean(PUBLIC_TAG, true);
        else getPersistentData().remove(PUBLIC_TAG);
        if (level instanceof ServerLevel sl) {
            if (on) AscensionAltars.add(sl, worldPosition);
            else AscensionAltars.remove(sl, worldPosition);
            registered = on;
        }
        sync();
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================
    /**
     * Chunk carregou (mesmo fora da distância de simulação, onde o ticker não roda): o altar público entra na lista
     * na hora — assim a seta do rastreador troca o "ponto da cidade" pelo Núcleo de verdade assim que ele é visto.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (publicAltar && !registered && level instanceof ServerLevel sl) {
            registered = true;
            AscensionAltars.add(sl, worldPosition);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AscensionCoreBlockEntity be) {
        // Altar público colocado por estrutura: entra na lista (rastreador "altar mais próximo" e proteção de magias).
        if (be.publicAltar && !be.registered && level instanceof ServerLevel sl) {
            be.registered = true;
            AscensionAltars.add(sl, pos);
        }
        // Ritual "fantasma" (ex.: o servidor reiniciou no meio): devolve o tributo e volta ao normal.
        if (be.phase != PHASE_IDLE && level instanceof ServerLevel sl && Tribulation.at(sl, pos) == null) {
            be.refundConsumed();
            be.phase = PHASE_IDLE;
            be.sync();
        }
        long now = level.getGameTime();
        if (now < be.previewUntil && now % 10 == 0 && !be.preview.isEmpty() && level instanceof ServerLevel sl) {
            // Partículas fantasmas nas posições que faltam; a marca some assim que a peça certa é colocada.
            boolean changed = be.preview.removeIf(s -> level.getBlockState(s.at(pos)).is(s.part().block()));
            for (AscensionPattern.Slot s : be.preview) {
                BlockPos p = s.at(pos);
                int c = s.part().color;
                DustParticleOptions dust = new DustParticleOptions(
                        new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 1.0F);
                sl.sendParticles(dust, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.28, 0.28, 0.28, 0);
                sl.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 1, 0.05, 0.05, 0.05, 0.005);
            }
            if (changed) {
                level.playSound(null, pos, be.preview.isEmpty() ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_CLUSTER_PLACE,
                        SoundSource.BLOCKS, 1.0F, be.preview.isEmpty() ? 1.5F : 1.1F);
                if (be.preview.isEmpty()) be.previewUntil = 0;
                be.sync();
            }
        }
    }

    /** Clique no Núcleo: confere tudo e mostra o relatório; ativa se estiver tudo certo (e não for só inspeção). */
    public void interact(ServerPlayer player, boolean inspectOnly) {
        if (!(level instanceof ServerLevel sl)) return;
        MagicData data = MagicApi.get(player);

        if (isRitualRunning()) {
            Tribulation running = Tribulation.at(sl, worldPosition);
            boolean mine = running != null && running.ownerId().equals(player.getUUID());
            player.displayClientMessage(Component.translatable(mine ? "message.isekai_mahou.ascension.survive"
                    : "message.isekai_mahou.ascension.busy").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        if (data.circle() >= Progression.MAX_CIRCLE) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.ascension.max").withStyle(ChatFormatting.GOLD));
            return;
        }
        boolean allowed = AscensionRules.privateAltars(sl);
        if (allowed != privateAllowed) {
            privateAllowed = allowed;
            sync();
        }
        if (!publicAltar && !allowed) {
            // v1.0: a Ascensão é nos Altares Sagrados prontos (cidades, Academia, Santuários).
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.ascension.private_off")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" "))
                    .append(Component.translatable("message.isekai_mahou.ascension.cap_hint_link").withStyle(st -> st
                            .withColor(ChatFormatting.YELLOW).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mahou ascension altar"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/mahou ascension altar"))))));
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.BLOCKS, 1.0F, 0.5F);
            return;
        }
        if (Tribulation.byOwner(player.getUUID()) != null) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.ascension.already").withStyle(ChatFormatting.RED));
            return;
        }

        int next = data.circle() + 1;
        // Altar público: nada para montar (as peças ficam de enfeite e não são conferidas).
        List<AscensionPattern.Slot> missing = publicAltar ? List.of() : AscensionPattern.missing(level, worldPosition, next);
        AscensionTributes.Tribute tribute = AscensionTributes.get(next);
        AscensionTributes.Check check = AscensionTributes.check(tribute, offeredItems(player, next), data);
        boolean levelOk = data.atLevelCap();
        boolean manaOk = data.mana() >= data.maxMana() - 0.5F;
        double dx = player.getX() - (worldPosition.getX() + 0.5), dz = player.getZ() - (worldPosition.getZ() + 0.5);
        boolean inside = Math.sqrt(dx * dx + dz * dz) <= AscensionPattern.boundary(next) - 0.5;
        boolean ready = missing.isEmpty() && check.complete() && levelOk && manaOk && inside;

        if (!missing.isEmpty()) showPreview(missing);
        if (ready && !inspectOnly) {
            activate(player, sl, next, tribute, check);
            return;
        }

        // Relatório no chat
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.ascension.header", data.circle(), next)
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        if (publicAltar) {
            player.sendSystemMessage(mark(true, Component.translatable("message.isekai_mahou.ascension.public_ok")));
        } else if (missing.isEmpty()) {
            player.sendSystemMessage(mark(true, Component.translatable("message.isekai_mahou.ascension.structure_ok")));
        } else {
            player.sendSystemMessage(mark(false, Component.translatable("message.isekai_mahou.ascension.structure_missing",
                    AscensionPattern.describeMissing(missing))));
        }
        player.sendSystemMessage(Component.translatable(publicAltar ? "message.isekai_mahou.ascension.tribute_public"
                : "message.isekai_mahou.ascension.tribute").withStyle(ChatFormatting.GOLD));
        for (AscensionTributes.Line line : check.lines()) player.sendSystemMessage(Component.literal("  ").append(line.text()));
        player.sendSystemMessage(mark(levelOk, Component.translatable("message.isekai_mahou.ascension.level", data.level(), data.levelCap())));
        player.sendSystemMessage(mark(manaOk, Component.translatable("message.isekai_mahou.ascension.resource",
                MagicApi.resourceName(player), (int) data.mana(), data.maxMana())));
        if (!inside) player.sendSystemMessage(mark(false, Component.translatable("message.isekai_mahou.ascension.inside")));
        if (ready) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.ascension.ready").withStyle(ChatFormatting.GREEN));
        }
        level.playSound(null, worldPosition, ready ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_CLUSTER_HIT,
                SoundSource.BLOCKS, 1.0F, ready ? 1.2F : 0.7F);
    }

    private static Component mark(boolean ok, MutableComponent body) {
        return Component.literal(ok ? "✔ " : "✘ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(body.withStyle(ok ? ChatFormatting.GRAY : ChatFormatting.WHITE));
    }

    private List<AscensionPedestalBlockEntity> pedestals(int targetCircle) {
        List<AscensionPedestalBlockEntity> list = new ArrayList<>();
        for (BlockPos p : AscensionPattern.pedestals(worldPosition, targetCircle)) {
            if (level.getBlockEntity(p) instanceof AscensionPedestalBlockEntity ped) list.add(ped);
        }
        return list;
    }

    private List<ItemStack> pedestalItems(int targetCircle) {
        List<ItemStack> items = new ArrayList<>();
        for (AscensionPedestalBlockEntity ped : pedestals(targetCircle)) items.add(ped.getItem());
        return items;
    }

    /**
     * O que conta como oferecido: os pedestais; no altar público também o inventário inteiro de quem ativa; em
     * qualquer altar, o "Tributo do Círculo N" no inventário.
     */
    private List<ItemStack> offeredItems(ServerPlayer player, int targetCircle) {
        List<ItemStack> items = pedestalItems(targetCircle);
        Item seal = AscensionRegistry.tributeItem(targetCircle);
        for (ItemStack s : inventory(player)) {
            if (s.isEmpty()) continue;
            if (publicAltar || s.is(seal)) items.add(s);
        }
        return items;
    }

    private static List<ItemStack> inventory(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>(player.getInventory().items);
        out.addAll(player.getInventory().offhand);
        return out;
    }

    /** Tira até {@code left} unidades do item do inventário (guardando em {@link #consumed}); devolve o que faltou. */
    private int takeFromInventory(ServerPlayer player, Item item, int left) {
        for (ItemStack s : inventory(player)) {
            if (left <= 0) break;
            if (s.isEmpty() || !s.is(item)) continue;
            ItemStack taken = s.split(left);
            left -= taken.getCount();
            consumed.add(taken);
        }
        return left;
    }

    private void activate(ServerPlayer player, ServerLevel sl, int next, AscensionTributes.Tribute tribute, AscensionTributes.Check check) {
        // Consome exatamente o que o tributo pede (sobras ficam nos pedestais).
        consumed.clear();
        for (Map.Entry<Item, Integer> e : check.consume().entrySet()) {
            int left = e.getValue();
            // Altar público: primeiro o inventário de quem ativa (os pedestais de uma cidade podem ter itens de
            // outro jogador); altar caseiro: primeiro os pedestais, o inventário só para o "Tributo do Círculo N".
            if (publicAltar) left = takeFromInventory(player, e.getKey(), left);
            for (AscensionPedestalBlockEntity ped : pedestals(next)) {
                if (left <= 0) break;
                ItemStack taken = ped.take(e.getKey(), left);
                left -= taken.getCount();
                if (!taken.isEmpty()) consumed.add(taken);
            }
            if (!publicAltar) takeFromInventory(player, e.getKey(), left);
        }
        player.getInventory().setChanged();
        if (publicAltar) {
            // O tributo voa do jogador até o Núcleo.
            sl.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.4, 0.5, 0.4, 0.6);
            ElementFx.line(sl, player.position().add(0, 1.1, 0), Vec3.atBottomCenterOf(worldPosition).add(0, 1.4, 0), Element.ARCANE);
        }
        phase = PHASE_INTRO;
        target = next;
        boundary = AscensionPattern.boundary(next);
        phaseStart = sl.getGameTime();
        previewUntil = 0;
        preview.clear();
        sync();
        Tribulation.start(sl, worldPosition, player, next, tribute);
    }

    /** Chamado pela Tribulação quando a luta começa (fim da introdução). */
    public void markTrialStarted(long gameTime) {
        phase = PHASE_TRIAL;
        phaseStart = gameTime;
        sync();
    }

    /** Fim do ritual (sucesso ou falha): o tributo consumido é perdido. */
    public void endRitual() {
        phase = PHASE_IDLE;
        consumed.clear();
        sync();
    }

    /** Devolve o tributo (ritual interrompido por fora, ou o Núcleo removido no criativo). */
    public void refundConsumed() {
        if (level == null || level.isClientSide || consumed.isEmpty()) return;
        for (ItemStack s : consumed) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, s);
        }
        consumed.clear();
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2,
                    worldPosition.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.05);
        }
        level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.6F);
        setChanged();
    }

    private void showPreview(List<AscensionPattern.Slot> missing) {
        preview.clear();
        preview.addAll(missing);
        previewUntil = level.getGameTime() + PREVIEW_TICKS;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, Block.UPDATE_CLIENTS);
        }
    }

    // =====================================================================================
    //  NBT / sincronização
    // =====================================================================================
    private void writeClientData(CompoundTag tag) {
        if (publicAltar) tag.putBoolean(PUBLIC_TAG, true);
        if (level instanceof ServerLevel sl) privateAllowed = AscensionRules.privateAltars(sl);
        if (privateAllowed) tag.putBoolean("private_allowed", true);
        tag.putInt("phase", phase);
        tag.putInt("target", target);
        tag.putInt("boundary", boundary);
        tag.putLong("phase_start", phaseStart);
        tag.putLong("preview_until", previewUntil);
        int[] arr = new int[preview.size() * 3];
        for (int i = 0; i < preview.size(); i++) {
            AscensionPattern.Slot s = preview.get(i);
            arr[i * 3] = s.dx();
            arr[i * 3 + 1] = s.dz();
            arr[i * 3 + 2] = s.part().ordinal();
        }
        tag.putIntArray("preview", arr);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeClientData(tag);
        ListTag list = new ListTag();
        for (ItemStack s : consumed) if (!s.isEmpty()) list.add(s.save(registries));
        tag.put("consumed", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // As estruturas gravam o flag no topo e/ou em NeoForgeData (lido pelo super em getPersistentData()).
        publicAltar = tag.getBoolean(PUBLIC_TAG) || getPersistentData().getBoolean(PUBLIC_TAG);
        privateAllowed = tag.getBoolean("private_allowed");
        phase = tag.getInt("phase");
        target = tag.getInt("target");
        boundary = tag.getInt("boundary");
        phaseStart = tag.getLong("phase_start");
        previewUntil = tag.getLong("preview_until");
        preview.clear();
        int[] arr = tag.getIntArray("preview");
        for (int i = 0; i + 2 < arr.length; i += 3) {
            preview.add(new AscensionPattern.Slot(arr[i], arr[i + 1], AscensionPattern.Part.byOrdinal(arr[i + 2])));
        }
        if (tag.contains("consumed", Tag.TAG_LIST)) {
            consumed.clear();
            ListTag list = tag.getList("consumed", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack s = ItemStack.parseOptional(registries, list.getCompound(i));
                if (!s.isEmpty()) consumed.add(s);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeClientData(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
