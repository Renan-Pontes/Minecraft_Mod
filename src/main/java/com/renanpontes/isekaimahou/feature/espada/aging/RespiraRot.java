package com.renanpontes.isekaimahou.feature.espada.aging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * O Respira apodrece o cenário — SÓ NA APARÊNCIA (v1.3.2). O servidor nunca muda o bloco: manda para os jogadores
 * perto um "bloco falso" (pacote de atualização de bloco) com a versão apodrecida e, {@link #RESTORE_TICKS} depois,
 * manda o bloco verdadeiro de volta. Assim nada se perde (nem em arenas, lugares protegidos ou se o servidor cair) e
 * o chão de Las Noches/do palácio fica intacto.
 * <p>
 * Só troca por blocos com a MESMA forma de colisão (cubo cheio por cubo cheio; plantas sem colisão por arbusto seco),
 * para o jogador não "afundar" nem tropeçar num bloco que o servidor acha diferente: terra/grama → terra grossa,
 * pedra → cascalho, tijolos → tijolos rachados, quartzo → calcita, madeira/folhas/lã → coral morto, areia → solo de
 * almas, arenito → bloco de espeleotema. Blocos com dados ou inquebráveis nunca mudam.
 */
public final class RespiraRot {
    public static final int RESTORE_TICKS = 600;
    /** Distância (blocos) dos jogadores que veem o bloco apodrecer. */
    private static final double VIEW = 64.0;
    private static final int MAX_ENTRIES = 600;

    private record Entry(BlockPos pos, long restoreAt) {}

    private static final Map<ResourceKey<Level>, List<Entry>> ROTTED = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> KNOWN = new HashMap<>();

    /** Tenta apodrecer (na aparência) o bloco; devolve true se mandou a troca. */
    public static boolean rot(ServerLevel level, BlockPos pos, @Nullable Entity boss) {
        if (!level.isLoaded(pos)) return false;
        BlockState st = level.getBlockState(pos);
        if (st.isAir() || st.hasBlockEntity() || st.getDestroySpeed(level, pos) < 0) return false;
        BlockState to = rotted(level, pos, st);
        if (to == null || to == st) return false;
        Set<BlockPos> known = KNOWN.computeIfAbsent(level.dimension(), k -> new HashSet<>());
        List<Entry> list = ROTTED.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        if (list.size() >= MAX_ENTRIES || known.contains(pos)) return false;
        BlockPos p = pos.immutable();
        known.add(p);
        list.add(new Entry(p, level.getGameTime() + RESTORE_TICKS + level.random.nextInt(200)));
        send(level, new ClientboundBlockUpdatePacket(p, to), p);
        return true;
    }

    @Nullable
    private static BlockState rotted(ServerLevel level, BlockPos pos, BlockState st) {
        boolean full = st.isCollisionShapeFullBlock(level, pos);
        boolean none = st.getCollisionShape(level, pos).isEmpty();
        if (none && (st.is(Blocks.SHORT_GRASS) || st.is(Blocks.TALL_GRASS) || st.is(BlockTags.FLOWERS) || st.is(Blocks.FERN)
                || st.is(Blocks.LARGE_FERN))) {
            return Blocks.DEAD_BUSH.defaultBlockState();
        }
        if (!full) return null;
        if (st.is(BlockTags.LOGS) || st.is(BlockTags.PLANKS) || st.is(BlockTags.LEAVES) || st.is(BlockTags.WOOL)) {
            return Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState();
        }
        if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM) || st.is(Blocks.DIRT) || st.is(Blocks.ROOTED_DIRT)
                || st.is(Blocks.MOSS_BLOCK)) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(Blocks.COBBLESTONE) || st.is(Blocks.MOSSY_COBBLESTONE)) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (st.is(BlockTags.STONE_BRICKS) || st.is(Blocks.SMOOTH_STONE) || st.is(Blocks.POLISHED_ANDESITE)
                || st.is(Blocks.POLISHED_DIORITE) || st.is(Blocks.POLISHED_GRANITE)) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        if (st.is(Blocks.QUARTZ_BLOCK) || st.is(Blocks.SMOOTH_QUARTZ) || st.is(Blocks.QUARTZ_BRICKS) || st.is(Blocks.QUARTZ_PILLAR)
                || st.is(Blocks.CHISELED_QUARTZ_BLOCK) || st.is(Blocks.WHITE_CONCRETE) || st.is(Blocks.BONE_BLOCK)) {
            return Blocks.CALCITE.defaultBlockState();
        }
        if (st.is(BlockTags.SAND) || st.is(Blocks.WHITE_CONCRETE_POWDER)) return Blocks.SOUL_SOIL.defaultBlockState();
        if (st.is(Blocks.SANDSTONE) || st.is(Blocks.SMOOTH_SANDSTONE) || st.is(Blocks.CUT_SANDSTONE) || st.is(Blocks.CHISELED_SANDSTONE)) {
            return Blocks.DRIPSTONE_BLOCK.defaultBlockState();
        }
        return null;
    }

    private static void send(ServerLevel level, ClientboundBlockUpdatePacket packet, BlockPos at) {
        double r2 = VIEW * VIEW;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) < r2) p.connection.send(packet);
        }
    }

    private static void tick(ServerTickEvent.Post event) {
        if (ROTTED.isEmpty()) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) return;
        Iterator<Map.Entry<ResourceKey<Level>, List<Entry>>> it = ROTTED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceKey<Level>, List<Entry>> me = it.next();
            ServerLevel level = server.getLevel(me.getKey());
            Set<BlockPos> known = KNOWN.get(me.getKey());
            if (level == null) {
                it.remove();
                KNOWN.remove(me.getKey());
                continue;
            }
            long now = level.getGameTime();
            List<Entry> list = me.getValue();
            for (int i = list.size() - 1; i >= 0; i--) {
                Entry e = list.get(i);
                if (e.restoreAt > now) continue;
                list.remove(i);
                if (known != null) known.remove(e.pos);
                // O bloco verdadeiro (o que o servidor sempre teve) volta para quem está perto.
                if (level.isLoaded(e.pos)) send(level, new ClientboundBlockUpdatePacket(level, e.pos), e.pos);
            }
            if (list.isEmpty()) {
                it.remove();
                KNOWN.remove(me.getKey());
            }
        }
    }

    /** Fechou o servidor: nada para devolver (o mundo nunca mudou); só limpa. */
    private static void clear(ServerStoppingEvent event) {
        ROTTED.clear();
        KNOWN.clear();
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(RespiraRot::tick);
        NeoForge.EVENT_BUS.addListener(RespiraRot::clear);
    }

    private RespiraRot() {}
}
