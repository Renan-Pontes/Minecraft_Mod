package com.renanpontes.isekaimahou.feature.bosses_e.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Terreno TEMPORÁRIO da arena do Dragão Primordial (chão de lava, gelo, crateras, pilares, a arena de cada fase):
 * cada bloco trocado guarda o original e a hora de voltar. Fica salvo no mundo (sobrevive a reinícios e a chunks
 * descarregados) e volta sozinho — inclusive se o dragão sumir. Blocos com dados, inquebráveis, imunes ao
 * Wither/Dragão e lugares seguros (cidades) nunca são trocados (veja {@link #replaceable}). Jogadores não quebram esses blocos (BeEvents).
 */
public final class ArenaTerraform extends SavedData {
    public static final String NAME = "isekai_mahou_dragon_arena";
    /** Teto de blocos trocados ao mesmo tempo no nível (protege o salvamento e a memória). */
    private static final int MAX_ENTRIES = 24000;
    /** Blocos devolvidos por tick, no máximo. */
    private static final int RESTORE_PER_TICK = 400;
    /** Sem flags de vizinhos: areia não cai, água não escorre para o buraco enquanto ele existe. */
    private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /**
     * Só o terreno "de fase" (que ainda dura mais que isto) é esticado por {@link #extendOwner}: perigos curtos (magma,
     * jaula de gelo, estacas, pilares, crateras) nunca viram permanentes por pegarem a extensão no meio do caminho.
     */
    private static final long LONG_TERRAIN_MARGIN = 3000;
    private static final ResourceLocation TRIAL_REALM = IsekaiMahou.id("trial_realm");

    private final Map<Long, Entry> entries = new HashMap<>();
    /** Próximo gameTime em que algo vence (evita varrer o mapa todo a cada tick). MIN = varrer já. */
    private long nextDue = Long.MIN_VALUE;

    /**
     * Um bloco trocado. Pode ter DUAS camadas: o terreno longo da fase ({@code under}, até {@code restoreAt}) e um
     * perigo curto por cima ({@code placed}, até {@code underAt}) — quando o perigo vence, volta o terreno da fase,
     * não o original. Ex.: magma do Mar de Lava sobre o chão de basalto da fase 1.
     */
    private static final class Entry {
        final BlockState original;
        BlockState placed;
        long restoreAt;
        UUID owner;
        int retries;
        @Nullable BlockState under;
        long underAt;

        Entry(BlockState original, BlockState placed, long restoreAt, UUID owner) {
            this.original = original;
            this.placed = placed;
            this.restoreAt = restoreAt;
            this.owner = owner;
        }

        long dueAt() {
            return under != null ? Math.min(restoreAt, underAt) : restoreAt;
        }
    }

    private void bump(long when) {
        if (when < nextDue) nextDue = when;
    }

    private static SavedData.Factory<ArenaTerraform> factory() {
        return new SavedData.Factory<>(ArenaTerraform::new, ArenaTerraform::load, null);
    }

    /** Dados do nível (cria se não houver). */
    public static ArenaTerraform get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /** Dados do nível só se já existirem (não cria arquivo à toa). */
    @Nullable
    public static ArenaTerraform peek(ServerLevel level) {
        return level.getDataStorage().get(factory(), NAME);
    }

    // =====================================================================================
    //  Trocar / consultar
    // =====================================================================================

    /**
     * Pode trocar este bloco (estado atual {@code cur})? Nunca: blocos com dados, inquebráveis, imunes ao Wither/Dragão,
     * lugares seguros (cidades, santuários) e a dimensão das Provações. Áreas protegidas só contra destruição
     * PERMANENTE (ex.: a arena da Fenda do Crepúsculo, onde o Dragão nasce) aceitam o terreno temporário, que volta
     * sozinho — mas só no chão comum (blocos do Minecraft), nunca em peças do mod (a fenda, portais, altares).
     */
    public static boolean replaceable(ServerLevel level, BlockPos pos, BlockState cur) {
        if (cur.hasBlockEntity() || cur.getDestroySpeed(level, pos) < 0) return false;
        if (cur.is(BlockTags.WITHER_IMMUNE) || cur.is(BlockTags.DRAGON_IMMUNE)) return false;
        if (level.dimension().location().equals(TRIAL_REALM) || Places.isSafe(level, pos)) return false;
        if (!Destruction.protectedAt(level, pos, null)) return true;
        return "minecraft".equals(BuiltInRegistries.BLOCK.getKey(cur.getBlock()).getNamespace());
    }

    /**
     * Troca o bloco por {@code ticks}. Se a posição já era temporária, mantém o original: um perigo mais CURTO que o
     * terreno de baixo vira uma camada por cima (depois volta o terreno de baixo); um mais longo substitui o prazo.
     * @return true se trocou
     */
    public boolean place(ServerLevel level, BlockPos pos, BlockState state, int ticks, UUID owner) {
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) return false;
        long key = pos.asLong();
        long now = level.getGameTime();
        Entry e = entries.get(key);
        BlockState cur = level.getBlockState(pos);
        if (e == null) {
            if (entries.size() >= MAX_ENTRIES || !replaceable(level, pos, cur)) return false;
            if (cur == state) return false;
            e = new Entry(cur, state, now + ticks, owner);
            entries.put(key, e);
        } else {
            long until = now + ticks;
            if (until < e.restoreAt) {
                // Camada curta por cima do terreno longo
                if (e.under == null) {
                    e.under = e.placed;
                    e.underAt = until;
                } else {
                    e.underAt = Math.max(e.underAt, until);
                }
            } else {
                e.restoreAt = until;
                e.under = null;
            }
            e.placed = state;
            e.owner = owner;
            e.retries = 0;
        }
        bump(e.dueAt());
        level.setBlock(pos, state, SET_FLAGS);
        setDirty();
        return true;
    }

    public boolean isTemp(BlockPos pos) {
        return entries.containsKey(pos.asLong());
    }

    /** O bloco temporário em {@code pos} é deste dono? */
    public boolean ownedBy(BlockPos pos, UUID owner) {
        Entry e = entries.get(pos.asLong());
        return e != null && e.owner.equals(owner);
    }

    public int count(UUID owner) {
        int n = 0;
        for (Entry e : entries.values()) if (e.owner.equals(owner)) n++;
        return n;
    }

    /** Devolve agora tudo do dono, numa onda que sai de {@code center} ({@code speed} blocos por tick). */
    public void releaseOwner(ServerLevel level, UUID owner, Vec3 center, double speed) {
        long now = level.getGameTime();
        boolean any = false;
        for (Map.Entry<Long, Entry> me : entries.entrySet()) {
            Entry e = me.getValue();
            if (!e.owner.equals(owner)) continue;
            BlockPos p = BlockPos.of(me.getKey());
            double d = Math.sqrt(p.distToCenterSqr(center));
            e.restoreAt = Math.min(e.restoreAt, now + 1 + (long) (d / Math.max(0.1, speed)));
            bump(e.dueAt());
            any = true;
        }
        if (any) setDirty();
    }

    /** Devolve agora (com a onda) só os blocos do dono que estão no conjunto {@code only}. */
    public void releaseSome(ServerLevel level, UUID owner, Iterable<BlockPos> only) {
        long now = level.getGameTime();
        for (BlockPos p : only) {
            Entry e = entries.get(p.asLong());
            if (e != null && e.owner.equals(owner)) {
                e.restoreAt = Math.min(e.restoreAt, now + 1);
                bump(e.dueAt());
            }
        }
        setDirty();
    }

    /**
     * Estica até {@code until} o terreno LONGO do dono (o chão da fase dura enquanto a luta durar). Perigos curtos
     * (menos de {@link #LONG_TERRAIN_MARGIN} ticks restantes) não são tocados.
     */
    public void extendOwner(UUID owner, long until) {
        boolean any = false;
        for (Entry e : entries.values()) {
            if (e.owner.equals(owner) && e.restoreAt < until && e.restoreAt > until - LONG_TERRAIN_MARGIN) {
                e.restoreAt = until;
                any = true;
            }
        }
        if (any) setDirty();
    }

    // =====================================================================================
    //  Devolução
    // =====================================================================================

    public void tick(ServerLevel level) {
        if (entries.isEmpty()) return;
        long now = level.getGameTime();
        if (now < nextDue) return;
        List<Long> due = new ArrayList<>();
        long next = Long.MAX_VALUE;
        for (Map.Entry<Long, Entry> me : entries.entrySet()) {
            long when = me.getValue().dueAt();
            if (when <= now && due.size() < RESTORE_PER_TICK) due.add(me.getKey());
            else next = Math.min(next, when);
        }
        nextDue = next;
        if (due.isEmpty()) return;
        for (long key : due) {
            Entry e = entries.get(key);
            BlockPos pos = BlockPos.of(key);
            if (!level.isLoaded(pos)) {
                if (e.restoreAt <= now) e.restoreAt = now + 100;
                if (e.under != null && e.underAt <= now) e.underAt = now + 100;
                bump(e.dueAt());
                continue;
            }
            BlockState cur = level.getBlockState(pos);
            // Alguém pôs outro bloco ali (criativo, outro mod): não apaga o trabalho dele.
            if (cur != e.placed && !cur.isAir() && cur.getFluidState().isEmpty()) {
                entries.remove(key);
                continue;
            }
            boolean full = e.restoreAt <= now || e.under == null;
            BlockState back = full ? e.original : e.under;
            if (!back.isAir() && blockedByEntity(level, pos, back)) {
                if (e.retries++ < 6) {
                    if (full) e.restoreAt = now + 10;
                    else e.underAt = now + 10;
                    bump(now + 10);
                    continue;
                }
                liftOut(level, pos);
            }
            e.retries = 0;
            if (full) {
                boolean fluid = !e.original.getFluidState().isEmpty();
                level.setBlock(pos, e.original, fluid ? Block.UPDATE_ALL : SET_FLAGS);
                entries.remove(key);
            } else {
                // O perigo curto venceu: volta o terreno da fase que estava embaixo.
                level.setBlock(pos, back, SET_FLAGS);
                e.placed = back;
                e.under = null;
                bump(e.dueAt());
            }
        }
        setDirty();
    }

    /** Alguém está dentro do espaço do bloco que vai voltar? (não sufocar ninguém) */
    private static boolean blockedByEntity(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getCollisionShape(level, pos).isEmpty()) return false;
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), Entity::isAlive).isEmpty();
    }

    private static void liftOut(ServerLevel level, BlockPos pos) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), Entity::isAlive)) {
            BlockPos up = pos.above();
            for (int i = 0; i < 6 && !level.getBlockState(up).getCollisionShape(level, up).isEmpty(); i++) up = up.above();
            e.teleportTo(e.getX(), up.getY(), e.getZ());
        }
    }

    /** Devolve TUDO agora (comando de emergência). @return quantos blocos */
    public int restoreAll(ServerLevel level) {
        int n = 0;
        Iterator<Map.Entry<Long, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Entry> me = it.next();
            BlockPos pos = BlockPos.of(me.getKey());
            if (!level.isLoaded(pos)) continue;
            BlockState cur = level.getBlockState(pos);
            if (cur == me.getValue().placed || cur.isAir() || !cur.getFluidState().isEmpty()) {
                level.setBlock(pos, me.getValue().original, SET_FLAGS);
                n++;
            }
            it.remove();
        }
        nextDue = Long.MIN_VALUE;
        setDirty();
        return n;
    }

    public int size() {
        return entries.size();
    }

    // =====================================================================================
    //  Salvar / carregar
    // =====================================================================================

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Entry> me : entries.entrySet()) {
            Entry e = me.getValue();
            CompoundTag c = new CompoundTag();
            c.putLong("p", me.getKey());
            c.put("o", NbtUtils.writeBlockState(e.original));
            c.put("n", NbtUtils.writeBlockState(e.placed));
            c.putLong("t", e.restoreAt);
            c.putUUID("u", e.owner);
            if (e.under != null) {
                c.put("ub", NbtUtils.writeBlockState(e.under));
                c.putLong("ut", e.underAt);
            }
            list.add(c);
        }
        tag.put("Blocks", list);
        return tag;
    }

    private static ArenaTerraform load(CompoundTag tag, HolderLookup.Provider provider) {
        ArenaTerraform d = new ArenaTerraform();
        HolderGetter<Block> blocks = provider.lookupOrThrow(Registries.BLOCK);
        for (Tag t : tag.getList("Blocks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("u")) continue;
            BlockState o = NbtUtils.readBlockState(blocks, c.getCompound("o"));
            BlockState n = NbtUtils.readBlockState(blocks, c.getCompound("n"));
            if (n.is(Blocks.AIR) && o.is(Blocks.AIR)) continue;
            Entry e = new Entry(o, n, c.getLong("t"), c.getUUID("u"));
            if (c.contains("ub", Tag.TAG_COMPOUND)) {
                e.under = NbtUtils.readBlockState(blocks, c.getCompound("ub"));
                e.underAt = c.getLong("ut");
            }
            d.entries.put(c.getLong("p"), e);
        }
        return d;
    }
}
