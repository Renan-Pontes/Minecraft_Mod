package com.renanpontes.isekaimahou.feature.earth_wind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/**
 * Blocos temporarios por dimensao (salvos no mundo): pilares, muralhas, domo, areia movedica e a Fenda.
 * Cada posicao guarda o bloco ORIGINAL e volta a ele quando o tempo acaba, mesmo depois de reiniciar o servidor.
 * Se um jogador colocar outra coisa no lugar, o bloco dele e respeitado (nada e apagado).
 */
public final class TempBlocks extends SavedData {
    private static final String NAME = "isekai_mahou_earth_wind_temp_blocks";

    private record Entry(BlockState original, BlockState placed, long expire) {}

    private final Map<BlockPos, Entry> entries = new HashMap<>();

    public static TempBlocks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TempBlocks::new, TempBlocks::load), NAME);
    }

    // =====================================================================================
    //  API
    // =====================================================================================

    /** Coloca {@code state} so se o lugar estiver livre (ar, grama, flores, agua...). */
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        BlockState cur = level.getBlockState(pos);
        if (!cur.canBeReplaced() || cur.hasBlockEntity()) return false;
        return get(level).put(level, pos, state, ticks);
    }

    /** Troca um bloco solido por {@code state} (ex.: chao vira areia movedica). */
    public static boolean convert(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        BlockState cur = level.getBlockState(pos);
        if (cur.hasBlockEntity() || cur.isAir()) return false;
        return get(level).put(level, pos, state, ticks);
    }

    /** Abre um buraco temporario (o bloco volta depois). */
    public static boolean carve(ServerLevel level, BlockPos pos, int ticks) {
        BlockState cur = level.getBlockState(pos);
        if (cur.hasBlockEntity() || cur.isAir()) return false;
        return get(level).put(level, pos, Blocks.AIR.defaultBlockState(), ticks);
    }

    public static boolean isTemporary(ServerLevel level, BlockPos pos) {
        return get(level).entries.containsKey(pos);
    }

    /**
     * Desfaz AGORA uma troca temporaria (ex.: um mod de protecao cancelou a colocacao): o bloco original volta
     * e a entrada some. So mexe se o bloco atual ainda for o que colocamos.
     */
    public static void revert(ServerLevel level, BlockPos pos) {
        TempBlocks data = get(level);
        Entry e = data.entries.remove(pos.immutable());
        if (e == null) return;
        data.setDirty();
        if (level.getBlockState(pos) == e.placed()) level.setBlock(pos, e.original(), SILENT);
    }

    /**
     * Atualiza so os clientes e NAO recalcula a forma dos vizinhos: sem isso, tochas, flores, portas e a metade
     * de cima de plantas altas ao lado "caem" e viram item. Tudo volta ao normal (com atualizacao completa) no fim.
     */
    private static final int SILENT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private boolean put(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        BlockPos key = pos.immutable();
        Entry old = entries.get(key);
        BlockState original = old != null ? old.original() : level.getBlockState(key);
        if (!level.setBlock(key, state, SILENT)) return false;
        long expire = level.getGameTime() + Math.max(1, ticks);
        if (old != null) expire = Math.max(expire, old.expire());
        entries.put(key, new Entry(original, state, expire));
        setDirty();
        return true;
    }

    // =====================================================================================
    //  Restauracao
    // =====================================================================================

    void tick(ServerLevel level) {
        if (entries.isEmpty()) return;
        long now = level.getGameTime();
        List<Map.Entry<BlockPos, Entry>> due = new ArrayList<>();
        Iterator<Map.Entry<BlockPos, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Entry> e = it.next();
            if (e.getValue().expire() <= now && level.isLoaded(e.getKey())) {
                due.add(e);
                it.remove();
            }
        }
        if (due.isEmpty()) return;
        setDirty();
        // de baixo para cima: quem estiver em cima de um pilar desce junto, sem ficar preso
        due.sort((a, b) -> Integer.compare(a.getKey().getY(), b.getKey().getY()));
        int i = 0;
        for (Map.Entry<BlockPos, Entry> e : due) {
            restore(level, e.getKey(), e.getValue(), i++ % 4 == 0);
        }
        BlockPos first = due.get(0).getKey();
        level.playSound(null, first, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    private static void restore(ServerLevel level, BlockPos pos, Entry e, boolean fx) {
        BlockState cur = level.getBlockState(pos);
        boolean ours = cur == e.placed() || cur.canBeReplaced()
                || cur.is(EwRegistries.RAISED_STONE.get()) || cur.is(EwRegistries.EARTHEN_QUICKSAND.get());
        if (!ours) return;
        if (!e.original().getCollisionShape(level, pos).isEmpty()) ejectFrom(level, pos);
        level.setBlock(pos, e.original(), Block.UPDATE_ALL);
        if (fx && !cur.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, cur), pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /** Empurra para cima quem ficaria dentro do bloco que volta. */
    public static void ejectFrom(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos);
        for (Entity en : level.getEntities((Entity) null, box, en -> !en.isSpectator() && en.isAlive() && !en.noPhysics)) {
            double y = pos.getY() + 1.0;
            for (int i = 0; i < 8; i++) {
                AABB moved = en.getBoundingBox().move(0, y - en.getY(), 0);
                if (level.noCollision(en, moved)) break;
                y += 1.0;
            }
            en.teleportTo(en.getX(), y, en.getZ());
            en.resetFallDistance();
        }
    }

    // =====================================================================================
    //  Salvar/carregar
    // =====================================================================================

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Entry> e : entries.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.getKey().asLong());
            t.put("original", NbtUtils.writeBlockState(e.getValue().original()));
            t.put("placed", NbtUtils.writeBlockState(e.getValue().placed()));
            t.putLong("expire", e.getValue().expire());
            list.add(t);
        }
        tag.put("entries", list);
        return tag;
    }

    private static TempBlocks load(CompoundTag tag, HolderLookup.Provider registries) {
        TempBlocks data = new TempBlocks();
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            data.entries.put(BlockPos.of(t.getLong("pos")), new Entry(
                    NbtUtils.readBlockState(blocks, t.getCompound("original")),
                    NbtUtils.readBlockState(blocks, t.getCompound("placed")),
                    t.getLong("expire")));
        }
        return data;
    }
}
