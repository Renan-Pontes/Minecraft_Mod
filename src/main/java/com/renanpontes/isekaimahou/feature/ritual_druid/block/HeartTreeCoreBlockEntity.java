package com.renanpontes.isekaimahou.feature.ritual_druid.block;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.ritual_druid.DruidContent;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidGrove;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidNetwork;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidProgress;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidStages;
import com.renanpontes.isekaimahou.feature.ritual_druid.Harmony;
import com.renanpontes.isekaimahou.feature.ritual_druid.HeartTreeShape;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Núcleo da Árvore Coração. Guarda o dono, o estágio (0..10), a oferenda já entregue para o próximo estágio
 * e faz a árvore procedural CRESCER bloco a bloco (com partículas e sons) até o estágio atual.
 * Também "cura" a árvore aos poucos se alguém quebrar partes dela.
 */
public class HeartTreeCoreBlockEntity extends BlockEntity {
    /** Blocos colocados/removidos por tick durante o crescimento. */
    private static final int GROW_BUDGET = 14;
    private static final int HARMONY_CACHE_TICKS = 40;
    /** Idade máxima da Harmonia "fresca" (tela/botão): evita refazer a varredura a cada clique repetido. */
    private static final int HARMONY_FRESH_TICKS = 5;
    private static final DustParticleOptions GROW_DUST = new DustParticleOptions(new Vector3f(0.55F, 1.0F, 0.5F), 1.2F);
    private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.3F), 1.3F);

    @Nullable private UUID owner;
    private String ownerName = "";
    private int stage;
    private int builtStage = -1;
    private int[] offered = new int[0];
    /** Face do Núcleo que fica aberta (alcova no pé do tronco). Null = derivada da semente. */
    @Nullable private Direction opening;

    // ---- Temporários (servidor) ----
    private final ArrayDeque<Step> queue = new ArrayDeque<>();
    private int buildingTo = -1;
    private Harmony harmony = Harmony.EMPTY;
    private long harmonyTime = Long.MIN_VALUE / 2;

    private record Step(BlockPos rel, @Nullable HeartTreeShape.Part part) {}

    public HeartTreeCoreBlockEntity(BlockPos pos, BlockState state) {
        super(DruidContent.HEART_TREE_CORE_BE.get(), pos, state);
    }

    // =====================================================================================
    //  Leitura
    // =====================================================================================
    @Nullable
    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public int stage() { return stage; }
    public int builtStage() { return builtStage; }
    public boolean isGrowing() { return !queue.isEmpty() || builtStage != stage; }
    public int offered(int i) { return i >= 0 && i < offered.length ? offered[i] : 0; }
    public boolean isOwner(ServerPlayer p) { return owner != null && owner.equals(p.getUUID()); }

    /** Semente estável da forma da árvore (depende só da posição). */
    public long seed() {
        return worldPosition.asLong() * 0x9E3779B97F4A7C15L ^ 0x5DEECE66DL;
    }

    /** Face aberta do Núcleo: a escolhida ao germinar (virada para o druida) ou uma fixa pela semente. */
    public Direction opening() {
        return opening != null ? opening : Direction.from2DDataValue((int) Math.floorMod(seed() >>> 7, 4L));
    }

    /** Forma da árvore deste Núcleo no estágio. */
    private Map<BlockPos, HeartTreeShape.Part> shape(int s) {
        return HeartTreeShape.build(s, seed(), opening());
    }

    /** Vira a alcova do coração para quem plantou — só antes de existir árvore (senão as formas não bateriam). */
    public void faceToward(Entity e) {
        if (builtStage >= 0 || buildingTo >= 0) return;
        double dx = e.getX() - (worldPosition.getX() + 0.5), dz = e.getZ() - (worldPosition.getZ() + 0.5);
        if (dx * dx + dz * dz < 1.0E-4) return;
        opening = Direction.getNearest(dx, 0, dz);
        setChanged();
    }

    public Harmony harmony(ServerLevel level, boolean fresh) {
        long now = level.getGameTime();
        if (now - harmonyTime > (fresh ? HARMONY_FRESH_TICKS : HARMONY_CACHE_TICKS)) {
            harmony = Harmony.compute(level, worldPosition);
            harmonyTime = now;
        }
        return harmony;
    }

    // =====================================================================================
    //  Criação / dono
    // =====================================================================================
    /** A Muda Ancestral germinou: troca pelo Núcleo e começa a árvore. */
    public static void germinate(ServerLevel level, BlockPos pos, @Nullable UUID owner, String ownerName) {
        level.setBlock(pos, DruidContent.HEART_TREE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof HeartTreeCoreBlockEntity core)) return;
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        core.owner = owner;
        core.ownerName = ownerName;
        if (player != null && player.level() == level) core.faceToward(player);
        core.setStage(player != null ? DruidProgress.rank(player) : 0);

        double x = pos.getX() + 0.5, y = pos.getY() + 0.8, z = pos.getZ() + 0.5;
        level.sendParticles(Vfx.totem(), x, y, z, 60, 0.4, 0.6, 0.4, 0.35);
        level.sendParticles(GROW_DUST, x, y, z, 40, 0.8, 0.5, 0.8, 0.0);
        level.sendParticles(Vfx.happy(), x, y, z, 25, 1.2, 0.4, 1.2, 0.0);
        level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 0.8F);
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
        if (player != null) DruidProgress.onGerminated(player, core);
    }

    /** Núcleo sem dono (colocado no criativo, por exemplo): o primeiro druida sem árvore pode tomá-lo. */
    public boolean claim(ServerPlayer player) {
        if (owner != null || DruidProgress.hasLivingTree(player)) return false;
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        DruidGrove.of(player).setTree(GlobalPos.of(level.dimension(), worldPosition));
        setStage(DruidProgress.rank(player));
        return true;
    }

    /** Define o estágio lógico (a árvore cresce/encolhe até ele) e zera a oferenda do próximo. */
    public void setStage(int s) {
        stage = Math.max(0, Math.min(DruidStages.MAX_STAGE, s));
        DruidStages.Stage next = DruidStages.get(stage + 1);
        offered = new int[next == null ? 0 : next.offerings().size()];
        sync();
    }

    /** Garante que a lista de oferendas tem o tamanho da do próximo estágio (NBT antigo/editado). */
    private void ensureOffered(DruidStages.Stage next) {
        if (offered.length != next.offerings().size()) offered = java.util.Arrays.copyOf(offered, next.offerings().size());
    }

    // =====================================================================================
    //  Oferenda
    // =====================================================================================
    /** Tenta entregar o item da mão como oferenda do próximo estágio. Retorna true se o item era da oferenda. */
    public boolean tryOffer(ServerPlayer player, ItemStack held) {
        DruidStages.Stage next = DruidStages.get(stage + 1);
        if (next == null || held.isEmpty()) return false;
        ensureOffered(next);
        for (int i = 0; i < next.offerings().size(); i++) {
            DruidStages.Offering o = next.offerings().get(i);
            if (!held.is(o.item())) continue;
            if (isGrowing()) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.ritual_druid.still_growing")
                        .withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
            int missing = o.count() - offered(i);
            if (missing <= 0) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.ritual_druid.offering_full",
                        held.getHoverName()).withStyle(ChatFormatting.GRAY), true);
                return true;
            }
            int n = Math.min(missing, held.getCount());
            ItemStack shown = held.copyWithCount(1);
            if (!player.getAbilities().instabuild) held.shrink(n);
            offered[i] += n;
            setChanged();
            offerFx(shown);
            boolean complete = DruidProgress.offeringComplete(this, next);
            player.displayClientMessage(Component.translatable(complete ? "message.isekai_mahou.ritual_druid.offering_complete"
                            : "message.isekai_mahou.ritual_druid.offering_progress", shown.getHoverName(), offered[i], o.count())
                    .withStyle(complete ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
            return true;
        }
        return false;
    }

    private void offerFx(ItemStack shown) {
        if (!(level instanceof ServerLevel sl)) return;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.9, z = worldPosition.getZ() + 0.5;
        sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shown), x, y + 0.3, z, 14, 0.25, 0.25, 0.25, 0.06);
        sl.sendParticles(Vfx.happy(), x, y, z, 10, 0.5, 0.4, 0.5, 0.0);
        sl.sendParticles(Vfx.endRod(), x, y + 0.4, z, 6, 0.2, 0.3, 0.2, 0.03);
        sl.playSound(null, worldPosition, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 1.0F, 1.0F);
        sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.5F);
    }

    /** O druida subiu de estágio: a oferenda é absorvida e a árvore começa a crescer. */
    public void completeStage(int newStage) {
        setStage(newStage);
    }

    // =====================================================================================
    //  Tick: crescimento, cura e sincronia com o rank do dono
    // =====================================================================================
    public static void serverTick(Level level, BlockPos pos, BlockState state, HeartTreeCoreBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        long t = sl.getGameTime();
        if (!be.queue.isEmpty()) {
            be.processQueue(sl);
        } else if (be.builtStage != be.stage) {
            be.startBuild(sl, be.builtStage < be.stage ? be.builtStage + 1 : be.stage);
        } else if (Math.floorMod(t + pos.hashCode(), 100) == 0) {
            be.heal(sl);
        }
        if (Math.floorMod(t + pos.hashCode(), 40) == 0) be.followOwnerRank(sl);
        if (t % 60 == 0 && be.builtStage >= 0) be.ambient(sl);
    }

    private void startBuild(ServerLevel sl, int target) {
        Map<BlockPos, HeartTreeShape.Part> from = shape(builtStage);
        Map<BlockPos, HeartTreeShape.Part> to = shape(target);
        queue.clear();
        for (BlockPos p : from.keySet()) if (!to.containsKey(p)) queue.add(new Step(p, null));
        for (Map.Entry<BlockPos, HeartTreeShape.Part> e : to.entrySet()) queue.add(new Step(e.getKey(), e.getValue()));
        buildingTo = target;
        sl.playSound(null, worldPosition, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.2F, 0.5F);
        sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.6F + target * 0.06F);
    }

    private void processQueue(ServerLevel sl) {
        int changed = 0, scanned = 0;
        while (!queue.isEmpty() && changed < GROW_BUDGET && scanned < 600) {
            Step s = queue.poll();
            scanned++;
            BlockPos wp = worldPosition.offset(s.rel());
            if (!sl.isLoaded(wp)) continue;
            boolean did = s.part() == null ? removeTreeBlock(sl, wp) : growInto(sl, wp, s.part());
            if (did) {
                changed++;
                if (sl.random.nextInt(3) == 0) {
                    boolean gold = s.part() == HeartTreeShape.Part.GOLDEN;
                    sl.sendParticles(gold ? GOLD_DUST : GROW_DUST, wp.getX() + 0.5, wp.getY() + 0.5, wp.getZ() + 0.5, 2, 0.4, 0.4, 0.4, 0.0);
                    if (gold) sl.sendParticles(Vfx.waxOn(), wp.getX() + 0.5, wp.getY() + 0.5, wp.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.0);
                    else sl.sendParticles(Vfx.happy(), wp.getX() + 0.5, wp.getY() + 0.5, wp.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }
        if (changed > 0 && sl.getGameTime() % 4 == 0) {
            BlockPos top = worldPosition.above(HeartTreeShape.trunkHeight(Math.max(0, buildingTo)) / 2);
            sl.playSound(null, top, sl.random.nextBoolean() ? SoundEvents.AZALEA_LEAVES_PLACE : SoundEvents.CHERRY_WOOD_PLACE,
                    SoundSource.BLOCKS, 0.6F, 0.8F + sl.random.nextFloat() * 0.4F);
        }
        if (queue.isEmpty() && buildingTo >= 0) {
            builtStage = buildingTo;
            buildingTo = -1;
            sync();
            finishFx(sl);
        }
    }

    private void finishFx(ServerLevel sl) {
        double x = worldPosition.getX() + 0.5, z = worldPosition.getZ() + 0.5;
        double top = worldPosition.getY() + HeartTreeShape.trunkHeight(builtStage) + 1;
        float r = HeartTreeShape.canopyRadius(builtStage);
        sl.sendParticles(GROW_DUST, x, top, z, 60, r * 0.6, 1.5, r * 0.6, 0.0);
        sl.sendParticles(Vfx.endRod(), x, top, z, 30, r * 0.5, 1.2, r * 0.5, 0.05);
        if (builtStage >= 6) sl.sendParticles(GOLD_DUST, x, top + 1, z, 40, r * 0.5, 1.0, r * 0.5, 0.0);
        sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 1.2F);
        sl.playSound(null, worldPosition, SoundEvents.FLOWERING_AZALEA_PLACE, SoundSource.BLOCKS, 1.2F, 0.9F);
    }

    /** Reconstrói até 3 blocos que faltam na árvore (alguém quebrou ou a copa estava bloqueada). */
    private void heal(ServerLevel sl) {
        int healed = 0;
        for (Map.Entry<BlockPos, HeartTreeShape.Part> e : shape(builtStage).entrySet()) {
            BlockPos wp = worldPosition.offset(e.getKey());
            if (!sl.isLoaded(wp)) continue;
            if (growInto(sl, wp, e.getValue())) {
                sl.sendParticles(GROW_DUST, wp.getX() + 0.5, wp.getY() + 0.5, wp.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.0);
                if (++healed >= 3) break;
            }
        }
        if (healed > 0) sl.playSound(null, worldPosition, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.BLOCKS, 0.5F, 1.3F);
    }

    /** Mantém a árvore no estágio = rank do dono (replantar regenera a árvore; comandos de op também). */
    private void followOwnerRank(ServerLevel sl) {
        if (owner == null || !queue.isEmpty()) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(owner);
        if (p == null) return;
        if (!ownerName.equals(p.getGameProfile().getName())) {
            ownerName = p.getGameProfile().getName();
            setChanged();
        }
        DruidGrove grove = DruidGrove.of(p);
        if (grove.tree() == null) grove.setTree(GlobalPos.of(sl.dimension(), worldPosition));
        int rank = DruidProgress.rank(p);
        if (rank != stage) setStage(rank);
    }

    /** Brilho ambiente subindo do núcleo (mais forte nos estágios altos). */
    private void ambient(ServerLevel sl) {
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.0, z = worldPosition.getZ() + 0.5;
        sl.sendParticles(GROW_DUST, x, y, z, 2 + builtStage / 2, 0.4, 0.3, 0.4, 0.0);
        if (builtStage >= 5) sl.sendParticles(Vfx.endRod(), x, y + 0.5, z, 1, 0.3, 0.3, 0.3, 0.02);
    }

    private static boolean isTreeBlock(BlockState s) {
        return s.is(DruidContent.HEART_TREE_LOG.get()) || s.is(DruidContent.HEART_TREE_LEAVES.get());
    }

    /** A árvore só cresce no ar e em plantas "substituíveis" (grama alta, cipós...) — nunca destrói flores ou construções. */
    private static boolean canGrowInto(BlockState s) {
        if (s.isAir() || isTreeBlock(s) || s.is(Blocks.SNOW)) return true;
        return s.is(BlockTags.REPLACEABLE_BY_TREES) && !s.is(BlockTags.FLOWERS) && s.getFluidState().isEmpty();
    }

    private static boolean growInto(ServerLevel sl, BlockPos wp, HeartTreeShape.Part part) {
        BlockState cur = sl.getBlockState(wp);
        BlockState want = HeartTreeShape.state(part);
        if (cur == want || !canGrowInto(cur)) return false;
        // Nunca cresce DENTRO de alguém (o druida costuma estar colado no tronco): a cura preenche depois
        if (!isTreeBlock(cur) && !sl.isUnobstructed(want, wp, CollisionContext.empty())) return false;
        return sl.setBlock(wp, want, Block.UPDATE_ALL);
    }

    private static boolean removeTreeBlock(ServerLevel sl, BlockPos wp) {
        if (!isTreeBlock(sl.getBlockState(wp))) return false;
        return sl.removeBlock(wp, false);
    }

    // =====================================================================================
    //  Remoção
    // =====================================================================================
    /** O Núcleo foi quebrado: a árvore se desfaz em pétalas e o dono pode plantar outra. */
    public void onRemoved() {
        if (!(level instanceof ServerLevel sl)) return;
        int n = 0;
        for (int s : new int[] {builtStage, buildingTo}) {
            for (BlockPos rel : shape(s).keySet()) {
                BlockPos wp = worldPosition.offset(rel);
                if (!sl.isLoaded(wp) || !isTreeBlock(sl.getBlockState(wp))) continue;
                sl.removeBlock(wp, false);
                if (n++ % 6 == 0) sl.sendParticles(ParticleTypes.CHERRY_LEAVES, wp.getX() + 0.5, wp.getY() + 0.5, wp.getZ() + 0.5, 2, 0.4, 0.4, 0.4, 0.0);
            }
        }
        sl.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
        queue.clear();
        if (owner != null) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(owner);
            if (p != null) {
                DruidGrove grove = DruidGrove.of(p);
                if (GlobalPos.of(sl.dimension(), worldPosition).equals(grove.tree())) grove.setTree(null);
                p.sendSystemMessage(Component.translatable("message.isekai_mahou.ritual_druid.tree_lost").withStyle(ChatFormatting.RED));
            }
        }
    }

    // =====================================================================================
    //  Interação
    // =====================================================================================
    /** Clique no núcleo: oferenda (se o item for da oferenda) ou abre a tela da Harmonia. */
    public void interact(ServerPlayer player, ItemStack held) {
        if (!(level instanceof ServerLevel sl)) return;
        if (owner == null) {
            if (claim(player)) {
                player.sendSystemMessage(Component.translatable("message.isekai_mahou.ritual_druid.claimed").withStyle(ChatFormatting.GREEN));
                sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.ritual_druid.already_have")
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        if (isOwner(player) && tryOffer(player, held)) return;
        DruidNetwork.sendView(player, this, sl);
    }

    // =====================================================================================
    //  NBT / sincronização
    // =====================================================================================
    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            level.sendBlockUpdated(worldPosition, s, s, Block.UPDATE_CLIENTS);
        }
    }

    private void writeClient(CompoundTag tag) {
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("owner_name", ownerName);
        tag.putInt("stage", stage);
        tag.putInt("built", builtStage);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeClient(tag);
        tag.putIntArray("offered", offered);
        if (opening != null) tag.putString("opening", opening.getName());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        stage = tag.getInt("stage");
        builtStage = tag.contains("built") ? tag.getInt("built") : -1;
        if (tag.contains("offered")) offered = tag.getIntArray("offered");
        if (tag.contains("opening")) {
            Direction d = Direction.byName(tag.getString("opening"));
            opening = d != null && d.getAxis().isHorizontal() ? d : null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeClient(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
