package com.renanpontes.isekaimahou.feature.fusions_a;

import com.renanpontes.isekaimahou.feature.fusions_a.block.FaTempBlock;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/**
 * Blocos temporários das fusões (Poça de Lava, Obsidiana, Ponte de Gelo, Chão Escorregadio, Areia Movediça,
 * prisões de vidro/gelo). Guarda o bloco ORIGINAL de cada posição e o devolve quando o tempo acaba — salvo junto
 * com o mundo (SavedData), então nada fica para trás nem se o servidor fechar no meio.
 * Quebrar um bloco temporário só o desfaz (sem drop), para não virar fonte de obsidiana/gelo grátis.
 */
public final class FaTempBlocks extends SavedData {
    private static final String NAME = "isekai_mahou_fusions_a_temp_blocks";

    private record Entry(BlockState original, BlockState placed, long expire) {}

    private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();

    public static FaTempBlocks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FaTempBlocks::new, FaTempBlocks::load), NAME);
    }

    // =====================================================================================
    //  API
    // =====================================================================================

    /**
     * Coloca {@code state} em {@code pos} por {@code ticks}. Se a posição já tem um bloco temporário, mantém o
     * original verdadeiro (só troca o bloco e renova o tempo). Retorna true se o bloco está lá no fim.
     */
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        FaTempBlocks data = get(level);
        long key = pos.asLong();
        Entry old = data.entries.get(key);
        BlockState current = level.getBlockState(pos);
        BlockState original;
        if (old != null && current.is(old.placed.getBlock())) {
            original = old.original;
        } else {
            original = sanitize(current);
        }
        if (current != state && !level.setBlock(pos, state, Block.UPDATE_ALL)) return false;
        long expire = level.getGameTime() + Math.max(10, ticks);
        if (old != null && current.is(old.placed.getBlock())) expire = Math.max(expire, old.expire);
        data.entries.put(key, new Entry(original, state, expire));
        data.setDirty();
        return true;
    }

    public static boolean isTracked(ServerLevel level, BlockPos pos) {
        return get(level).entries.containsKey(pos.asLong());
    }

    /**
     * Antes de transformar o chão em {@code ground}: plantinhas em cima (grama, samambaia, arbusto seco) não
     * sobreviveriam no gelo/areia e soltariam itens — saem junto, por {@code ticks}, e voltam no fim.
     * Retorna false se a coluna não deve ser mexida (planta de dois blocos).
     */
    public static boolean clearPlantAbove(ServerLevel level, BlockPos ground, int ticks) {
        BlockPos up = ground.above();
        BlockState s = level.getBlockState(up);
        if (s.isAir()) return true;
        if (s.getBlock() instanceof DoublePlantBlock) return false;
        if (s.getBlock() instanceof BushBlock) return place(level, up, Blocks.AIR.defaultBlockState(), ticks);
        return true;
    }

    /** Desfaz agora (quebrado por alguém ou pela própria magia). */
    public static boolean restoreNow(ServerLevel level, BlockPos pos) {
        FaTempBlocks data = get(level);
        Entry e = data.entries.remove(pos.asLong());
        if (e == null) return false;
        data.setDirty();
        data.restore(level, pos, e, true);
        return true;
    }

    /** Antecipa o fim dos blocos temporários {@code block} de uma área (ex.: estátua quebrada antes da hora). */
    public static void expireSoon(ServerLevel level, BlockPos min, BlockPos max, Block block, int ticks) {
        FaTempBlocks data = get(level);
        if (data.entries.isEmpty()) return;
        long at = level.getGameTime() + ticks;
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            long key = p.asLong();
            Entry e = data.entries.get(key);
            if (e != null && e.expire > at && e.placed.is(block)) {
                data.entries.put(key, new Entry(e.original, e.placed, at));
                data.setDirty();
            }
        }
    }

    // =====================================================================================
    //  Tick (chamado pelo FaEvents no fim da tick de cada mundo)
    // =====================================================================================

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 4 != 0) return;
        FaTempBlocks data = get(level);
        if (data.entries.isEmpty()) return;
        long now = level.getGameTime();
        int sounds = 0;
        ObjectIterator<Long2ObjectMap.Entry<Entry>> it = data.entries.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<Entry> me = it.next();
            Entry e = me.getValue();
            if (e.expire > now) continue;
            BlockPos pos = BlockPos.of(me.getLongKey());
            if (!level.isLoaded(pos)) continue; // devolve quando o chunk carregar de novo
            it.remove();
            data.setDirty();
            data.restore(level, pos, e, sounds++ < 6);
        }
    }

    private void restore(ServerLevel level, BlockPos pos, Entry e, boolean sound) {
        BlockState now = level.getBlockState(pos);
        boolean ours = now.is(e.placed.getBlock());
        // Explodido (ou apagado por outra coisa): o original volta mesmo assim — o chão do gelo liso, a água da ponte
        boolean blownAway = !ours && now.isAir() && !e.original.isAir();
        if (!ours && !blownAway) return; // alguém já pôs outro bloco ali: não mexe
        if (!e.original.getCollisionShape(level, pos).isEmpty()) liftEntities(level, pos);
        level.setBlock(pos, e.original, Block.UPDATE_ALL);
        if (!ours || e.placed.isAir()) return; // nada para "quebrar" na tela
        if (sound) {
            level.levelEvent(2001, pos, Block.getId(e.placed)); // partículas + som de quebrar
        } else {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, e.placed), pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
        }
        if (e.placed.getBlock() instanceof FaTempBlock tb) tb.onExpire(level, pos);
    }

    /** O bloco original volta sólido: quem estiver dentro sobe para cima dele em vez de sufocar. */
    private static void liftEntities(ServerLevel level, BlockPos pos) {
        for (Entity en : level.getEntities((Entity) null, new AABB(pos), en -> en.isAlive() && !en.isSpectator())) {
            BlockPos up = pos.above();
            int guard = 0;
            while (guard++ < 4 && !level.getBlockState(up).getCollisionShape(level, up).isEmpty()) up = up.above();
            en.teleportTo(en.getX(), up.getY() + 0.01, en.getZ());
        }
    }

    /** Fogo e plantas de dois blocos não voltam (meia planta quebraria; fogo reacenderia o mundo). */
    private static BlockState sanitize(BlockState s) {
        if (s.getBlock() instanceof BaseFireBlock || s.getBlock() instanceof DoublePlantBlock) return Blocks.AIR.defaultBlockState();
        return s;
    }

    // =====================================================================================
    //  Salvar/carregar
    // =====================================================================================

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Long2ObjectMap.Entry<Entry> me : entries.long2ObjectEntrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", me.getLongKey());
            t.put("o", NbtUtils.writeBlockState(me.getValue().original));
            t.put("p", NbtUtils.writeBlockState(me.getValue().placed));
            t.putLong("t", me.getValue().expire);
            list.add(t);
        }
        tag.put("blocks", list);
        return tag;
    }

    public static FaTempBlocks load(CompoundTag tag, HolderLookup.Provider registries) {
        FaTempBlocks data = new FaTempBlocks();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            BlockState o = NbtUtils.readBlockState(blocks, t.getCompound("o"));
            BlockState p = NbtUtils.readBlockState(blocks, t.getCompound("p"));
            data.entries.put(t.getLong("pos"), new Entry(o, p, t.getLong("t")));
        }
        return data;
    }
}
