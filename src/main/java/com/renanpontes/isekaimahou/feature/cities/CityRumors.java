package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;
import com.renanpontes.isekaimahou.feature.cities.network.CitiesNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Rumores: o NPC aponta uma estrutura do mod por perto (forte goblin, santuário, templo, labirinto...). A busca usa a
 * mesma lógica do /locate ({@code findNearestMapStructure}) num raio limitado, com recarga por jogador. O rumor vira
 * uma seta no rastreador de objetivos até o jogador chegar lá (+ XP).
 */
public final class CityRumors {
    /** Estruturas que não viram rumor (as próprias cidades e o que só existe em outras dimensões já some sozinho). */
    private static final Set<String> SKIP = Set.of("guild_city", "capital_city", "capital_sahra", "capital_nordheim");
    private static final int SEARCH_RADIUS_CHUNKS = 36;
    private static final int MAX_TRIES = 4;
    private static final int COOLDOWN_TICKS = 400;
    private static final double ARRIVE_DIST = 40.0;
    /** Guia de morador (altar/pedra dentro da cidade): chegou quando está a esta distância. */
    private static final double GUIDE_ARRIVE_DIST = 4.0;

    public static MutableComponent tell(ServerPlayer player, CityNpc npc) {
        ServerLevel sl = player.serverLevel();
        CompoundTag t = CityPlayer.tag(player);
        long now = sl.getGameTime();
        if (t.contains("RumorAt") && now - t.getLong("RumorAt") < COOLDOWN_TICKS && now >= t.getLong("RumorAt")) {
            // Recarga também quando a última busca não achou nada (cada busca procura estruturas no mapa).
            return t.contains("RumorX") ? CityLines.line("rumor.again", rumorName(t.getString("RumorKey")))
                    : CityLines.line("rumor.none");
        }
        t.putLong("RumorAt", now);
        RandomSource r = npc.getRandom();
        BlockPos from = npc.blockPosition();
        Found found = search(sl, from, r, path -> !SKIP.contains(path), MAX_TRIES);
        if (found == null) return CityLines.line("rumor.none");
        setRumor(player, found.pos(), found.key());
        int dist = (int) Math.sqrt(from.distSqr(found.pos()));
        sl.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
        return CityLines.line("rumor.found." + r.nextInt(3), rumorName(found.key()), dist, direction(from, found.pos()));
    }

    /** Uma estrutura achada: onde fica (na superfície) e o id dela (caminho, sem o namespace). */
    public record Found(BlockPos pos, String key) {}

    /**
     * Procura perto de {@code from} uma estrutura do mod cujo id passe em {@code filter}: sorteia a ordem e tenta as
     * {@code maxTries} primeiras (cada tentativa é uma busca igual à do /locate, por isso o limite). Null = nada perto.
     */
    @Nullable
    public static Found search(ServerLevel sl, BlockPos from, RandomSource r, java.util.function.Predicate<String> filter, int maxTries) {
        Registry<Structure> reg = sl.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> candidates = new ArrayList<>();
        for (Holder.Reference<Structure> h : reg.holders().toList()) {
            ResourceLocation id = h.key().location();
            if (!id.getNamespace().equals(IsekaiMahou.MODID) || !filter.test(id.getPath())) continue;
            if (sl.getChunkSource().getGeneratorState().getPlacementsForStructure(h).isEmpty()) continue;
            candidates.add(h);
        }
        for (int i = candidates.size() - 1; i > 0; i--) {
            int k = r.nextInt(i + 1);
            Holder<Structure> tmp = candidates.get(i);
            candidates.set(i, candidates.get(k));
            candidates.set(k, tmp);
        }
        for (int i = 0; i < Math.min(maxTries, candidates.size()); i++) {
            Holder<Structure> h = candidates.get(i);
            Pair<BlockPos, Holder<Structure>> found = sl.getChunkSource().getGenerator()
                    .findNearestMapStructure(sl, HolderSet.direct(h), from, SEARCH_RADIUS_CHUNKS, false);
            if (found == null) continue;
            BlockPos pos = found.getFirst();
            int y = sl.getChunkSource().getGenerator().getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Types.WORLD_SURFACE_WG, sl,
                    sl.getChunkSource().randomState());
            return new Found(new BlockPos(pos.getX(), y, pos.getZ()), h.unwrapKey().map(k -> k.location().getPath()).orElse("unknown"));
        }
        return null;
    }

    /** Marca no rastreador um lugar achado por {@link #search} (dica paga do estalajadeiro). */
    public static void mark(ServerPlayer p, Found found) {
        setRumor(p, found.pos(), found.key());
    }

    /** Nome traduzido da estrutura (reserva genérica se não houver tradução). */
    public static MutableComponent rumorName(String path) {
        // Reserva: o id legível ("goblin_fortress" → "goblin fortress"); todas as estruturas do mod têm tradução.
        return Component.translatableWithFallback("rumor.isekai_mahou.cities.structure." + path, path.replace('_', ' '));
    }

    /** "ao norte", "a sudeste"... */
    public static MutableComponent direction(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        // 0 = norte (-Z), sentido horário.
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        int idx = Mth.positiveModulo((int) Math.round(ang / 45.0), 8);
        String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return CityLines.tr("rumor.isekai_mahou.cities.dir." + dirs[idx]);
    }

    /**
     * Um morador mostra o caminho de um ponto da cidade (Altar de Ascensão, Pedra de Teleporte): a mesma seta do rumor,
     * mas sem XP na chegada (é logo ali) e com o texto de guia no rastreador. Um rumor de estrutura que o jogador estava
     * seguindo fica guardado e volta à seta quando ele chega ao ponto do guia.
     */
    public static void guide(ServerPlayer p, BlockPos pos, String key) {
        CompoundTag t = CityPlayer.tag(p);
        if (t.contains("RumorX") && !t.getBoolean("RumorGuide")) {
            CompoundTag prev = new CompoundTag();
            for (String k : new String[] {"RumorX", "RumorY", "RumorZ"}) prev.putInt(k, t.getInt(k));
            prev.putString("RumorKey", t.getString("RumorKey"));
            prev.putString("RumorDim", t.getString("RumorDim"));
            t.put(PREV, prev);
        }
        t.putBoolean("RumorGuide", true);
        setRumor(p, pos, key);
    }

    /** Rumor de estrutura guardado enquanto o jogador segue um guia. */
    private static final String PREV = "RumorPrev";

    private static void setRumor(ServerPlayer p, BlockPos pos, String key) {
        CompoundTag t = CityPlayer.tag(p);
        if (!key.equals("ascension_altar") && !key.equals("travel_stone")) {
            t.remove("RumorGuide");
            t.remove(PREV);   // rumor novo: o antigo guardado não volta mais
        }
        t.putInt("RumorX", pos.getX());
        t.putInt("RumorY", pos.getY());
        t.putInt("RumorZ", pos.getZ());
        t.putString("RumorKey", key);
        t.putString("RumorDim", p.level().dimension().location().toString());
        sync(p);
    }

    public static void clear(ServerPlayer p) {
        CompoundTag t = CityPlayer.tag(p);
        for (String k : new String[] {"RumorX", "RumorY", "RumorZ", "RumorKey", "RumorDim", "RumorGuide", PREV}) t.remove(k);
        sync(p);
    }

    /** Manda o rumor atual ao cliente (login, mudança). */
    public static void sync(ServerPlayer p) {
        CompoundTag t = CityPlayer.tag(p);
        if (!t.contains("RumorX")) {
            CitiesNetwork.send(p, new CitiesNetwork.Rumor(false, BlockPos.ZERO, p.level().dimension().location(), Component.empty(), false));
            return;
        }
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("RumorDim"));
        CitiesNetwork.send(p, new CitiesNetwork.Rumor(true, new BlockPos(t.getInt("RumorX"), t.getInt("RumorY"), t.getInt("RumorZ")),
                dim != null ? dim : p.level().dimension().location(), rumorName(t.getString("RumorKey")), t.getBoolean("RumorGuide")));
    }

    /** Chegou ao lugar do rumor? (checado a cada 2 s) */
    public static void checkArrival(ServerPlayer p) {
        CompoundTag t = CityPlayer.tag(p);
        if (!t.contains("RumorX") || !p.level().dimension().location().toString().equals(t.getString("RumorDim"))) return;
        double dx = p.getX() - t.getInt("RumorX");
        double dz = p.getZ() - t.getInt("RumorZ");
        boolean guide = t.getBoolean("RumorGuide");
        double reach = guide ? GUIDE_ARRIVE_DIST : ARRIVE_DIST;
        if (dx * dx + dz * dz > reach * reach) return;
        if (guide && Math.abs(p.getY() - t.getInt("RumorY")) > 6) return;
        Component name = rumorName(t.getString("RumorKey"));
        CompoundTag prev = guide && t.contains(PREV) ? t.getCompound(PREV) : null;
        clear(p);
        if (guide) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.guide_arrived", name), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
            if (prev != null) {
                // O rumor de antes volta para a seta (na dimensão em que foi dado).
                for (String k : new String[] {"RumorX", "RumorY", "RumorZ"}) t.putInt(k, prev.getInt(k));
                t.putString("RumorKey", prev.getString("RumorKey"));
                t.putString("RumorDim", prev.getString("RumorDim"));
                sync(p);
            }
            return;
        }
        MagicApi.giveXp(p, 40, "cities.rumor");
        MagicApi.addCounter(p, "cities.rumor", 1);
        p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.rumor_found", name), false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    private CityRumors() {}
}
