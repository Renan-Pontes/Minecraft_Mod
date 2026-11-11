package com.renanpontes.isekaimahou.feature.farm.blueprint;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.farm.FarmFx;
import com.renanpontes.isekaimahou.feature.farm.FarmRegistries;
import com.renanpontes.isekaimahou.feature.farm.ManaLink;
import com.renanpontes.isekaimahou.feature.farm.build.Placer;
import com.renanpontes.isekaimahou.feature.farm.build.Supply;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * O canteiro que constrói a Planta sozinho: segue o molde andar por andar, pega os blocos dos baús a até
 * {@link #CHEST_RANGE} blocos (prefere sempre a mesma variante de madeira/pedra), limpa o terreno no caminho (os
 * drops vão para os baús) e espera quando falta algo ("Faltando: Tábuas"). Com uma Bateria de Mana a até 8 blocos,
 * acelera (gasta {@link #BOOST_MANA} por bloco).
 */
public class ConstructionSiteBlockEntity extends BlockEntity {
    public static final int CHEST_RANGE = 3;
    public static final int BOOST_MANA = 2;
    /** Verificações rápidas (posição já certa) por tick. */
    private static final int SKIP_BUDGET = 48;

    private BlueprintKind kind = BlueprintKind.SMALL_HOUSE;
    @Nullable
    private UUID owner;
    private int index;
    private boolean completed;
    @Nullable
    private Mat missing;
    private final Map<Mat, Item> preferred = new EnumMap<>(Mat.class);
    // transitórios
    private List<BlockPos> chests = List.of();
    private List<BlockEntity> batteries = List.of();
    private long nextScan;
    private long nextTry;
    private boolean boosted;
    private int sinceSync;

    public ConstructionSiteBlockEntity(BlockPos pos, BlockState state) {
        super(FarmRegistries.SITE_BE.get(), pos, state);
    }

    public void setup(BlueprintKind kind, UUID owner) {
        this.kind = kind;
        this.owner = owner;
        this.index = 0;
        setChanged();
        sync();
    }

    public BlueprintKind kind() { return kind; }
    public int index() { return index; }
    public int total() { return kind.template().size(); }
    public boolean completed() { return completed; }
    @Nullable public Mat missing() { return missing; }
    public boolean boosted() { return boosted; }

    public Direction look() {
        BlockState s = getBlockState();
        return s.hasProperty(ConstructionSiteBlock.FACING) ? s.getValue(ConstructionSiteBlock.FACING) : Direction.NORTH;
    }

    public ItemStack blueprintStack() {
        return new ItemStack(FarmRegistries.BLUEPRINTS.get(kind).get());
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================
    public static void serverTick(Level level, BlockPos pos, BlockState state, ConstructionSiteBlockEntity be) {
        if (be.completed || !(level instanceof ServerLevel sl)) return;
        be.tick(sl);
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now >= nextScan) {
            nextScan = now + 40;
            chests = Supply.findContainers(level, worldPosition, CHEST_RANGE);
            batteries = ManaLink.batteries(level, worldPosition);
        }
        if (now < nextTry) return;
        boolean wasBoosted = boosted;
        boosted = !batteries.isEmpty() && ManaLink.total(batteries) >= BOOST_MANA * 2;
        if (boosted != wasBoosted) sync();
        int actions = boosted ? 2 : 1;
        if (!boosted) nextTry = now + 2;
        Template t = kind.template();
        Supply supply = Supply.containers(level, chests, worldPosition);
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player != null && player.level() != level) player = null;
        Direction look = look();
        int skips = 0;
        while (actions > 0 && index < t.size() && skips < SKIP_BUDGET) {
            Template.Entry e = t.entries().get(index);
            BlockPos p = t.world(worldPosition, look, e.x(), e.y(), e.z());
            BlockState current = level.getBlockState(p);
            if (p.equals(worldPosition) || e.mat().satisfiedBy(current) || !Placer.canEdit(level, player, p)) {
                index++;
                skips++;
                continue;
            }
            Step r = step(level, player, supply, e, p, current, look);
            if (r == Step.WAIT) {
                if (missing != e.mat()) {
                    missing = e.mat();
                    sync();
                }
                nextTry = now + 20;
                return;
            }
            index++;
            if (r == Step.DONE) {
                actions--;
                if (boosted) ManaLink.drain(batteries, BOOST_MANA);
                if (missing != null) {
                    missing = null;
                    sync();
                } else if (++sinceSync >= 8) {
                    sinceSync = 0;
                    sync();
                }
            } else {
                skips++;
            }
        }
        if (index >= t.size()) complete(level);
        else setChanged();
    }

    private enum Step { DONE, SKIP, WAIT }

    private Step step(ServerLevel level, @Nullable ServerPlayer player, Supply supply, Template.Entry e, BlockPos p,
                      BlockState current, Direction look) {
        if (e.mat() == Mat.AIR) {
            if (!Placer.removable(level, p, current)) return Step.SKIP;
            if (!Placer.remove(level, player, p, false, supply::give)) return Step.SKIP;
            if (level.random.nextInt(3) == 0) level.sendParticles(FarmFx.dust(kind.rgb, 0.7F), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
            return Step.DONE;
        }
        if (!Placer.replaceable(level, p)) {
            if (!Placer.removable(level, p, current) || !Placer.remove(level, player, p, false, supply::give)) return Step.SKIP;
        }
        ItemStack taken = supply.take(e.mat().matcher(), preferred.get(e.mat()));
        if (taken.isEmpty()) return Step.WAIT;
        preferred.putIfAbsent(e.mat(), taken.getItem());
        Turn r = Turn.of(look);
        Direction facing = e.facing() == null ? null : r.apply(e.facing());
        BlockState state = e.mat().state(taken, facing, Template.worldAxis(e.axis(), look), e.variant());
        if (!Placer.place(level, player, p, state)) {
            supply.give(taken);
            return Step.SKIP;
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && Placer.replaceable(level, p.above())) {
            level.setBlock(p.above(), state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }
        if (e.mat() == Mat.WATER) supply.give(new ItemStack(Items.BUCKET));
        // Efeitos: brilho no bloco, raio saindo do canteiro, som do bloco
        FarmFx.placed(level, p, kind.rgb);
        if (level.random.nextInt(3) == 0) {
            FarmFx.beam(level, Vec3.atCenterOf(worldPosition).add(0, 0.6, 0), Vec3.atCenterOf(p), FarmFx.dust(kind.rgb, 0.55F), 0.7);
        }
        if (level.random.nextInt(2) == 0) Placer.placeSound(level, p, state, 0.7F);
        return Step.DONE;
    }

    /** Rotação da planta (fachada virada para quem montou). */
    private record Turn(net.minecraft.world.level.block.Rotation rot) {
        static Turn of(Direction look) {
            return new Turn(Template.rotation(look));
        }

        Direction apply(Direction local) {
            return local.getAxis().isVertical() ? local : rot.rotate(local);
        }
    }

    private void complete(ServerLevel level) {
        completed = true;
        Vec3 c = Vec3.atCenterOf(worldPosition);
        Supply supply = Supply.containers(level, chests, worldPosition);
        supply.give(blueprintStack());
        FarmFx.celebrate(level, c.add(0, 1.0, 0), kind.rgb);
        FarmFx.sound(level, c, SoundEvents.PLAYER_LEVELUP, 0.8F, 1.2F);
        FarmFx.sound(level, c, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0F, 1.0F);
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) {
            player.displayClientMessage(Component.translatable("farm.isekai_mahou.site.done", kind.displayName()).withColor(kind.rgb), false);
            MagicApi.addCounter(player, "farm.blueprints_built", 1);
        }
        level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Clique no canteiro: andamento e o que falta. */
    public void report(ServerPlayer sp) {
        int pct = total() == 0 ? 100 : index * 100 / total();
        sp.displayClientMessage(Component.translatable("farm.isekai_mahou.site.progress", kind.displayName(), pct).withColor(kind.rgb), false);
        if (missing != null) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.site.missing", missing.displayName()).withStyle(ChatFormatting.RED), false);
        }
        if (chests.isEmpty()) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.site.no_chest", CHEST_RANGE).withStyle(ChatFormatting.YELLOW), false);
        }
        sp.displayClientMessage(Component.translatable(boosted ? "farm.isekai_mahou.site.boosted" : "farm.isekai_mahou.site.normal",
                ManaLink.RANGE).withStyle(ChatFormatting.GRAY), false);
    }

    // =====================================================================================
    //  NBT e sincronização
    // =====================================================================================
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeSynced(tag);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putBoolean("completed", completed);
        CompoundTag pref = new CompoundTag();
        preferred.forEach((m, item) -> pref.putString(m.name(), BuiltInRegistries.ITEM.getKey(item).toString()));
        tag.put("preferred", pref);
    }

    private void writeSynced(CompoundTag tag) {
        tag.putInt("kind", kind.ordinal());
        tag.putInt("index", index);
        tag.putInt("missing", missing == null ? -1 : missing.ordinal());
        tag.putBoolean("boosted", boosted);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        kind = BlueprintKind.byIndex(tag.getInt("kind"));
        index = Math.max(0, tag.getInt("index"));
        int m = tag.contains("missing") ? tag.getInt("missing") : -1;
        missing = m >= 0 && m < Mat.values().length ? Mat.values()[m] : null;
        boosted = tag.getBoolean("boosted");
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        completed = tag.getBoolean("completed");
        preferred.clear();
        CompoundTag pref = tag.getCompound("preferred");
        for (String k : pref.getAllKeys()) {
            try {
                Mat mat = Mat.valueOf(k);
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(pref.getString(k)));
                if (item != Items.AIR) preferred.put(mat, item);
            } catch (IllegalArgumentException ignored) {
                // material antigo/desconhecido: ignora
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeSynced(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Lista de posições ainda por fazer (o cliente usa só para o contorno; aqui: andares restantes). */
    public List<BlockPos> remainingPreview(int max) {
        List<BlockPos> out = new ArrayList<>();
        Template t = kind.template();
        for (int i = index; i < t.size() && out.size() < max; i++) {
            Template.Entry e = t.entries().get(i);
            if (e.mat() == Mat.AIR) continue;
            out.add(t.world(worldPosition, look(), e.x(), e.y(), e.z()));
        }
        return out;
    }
}
