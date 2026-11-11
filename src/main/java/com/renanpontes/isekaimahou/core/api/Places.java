package com.renanpontes.isekaimahou.core.api;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.status.PlaceData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Lugares nomeados (cidades, capital, vilas...) — v0.8. Guardados por nível ({@code SavedData}), sobrevivem ao
 * reinício. O núcleo cuida de: entrada/saída de jogadores (evento {@code PlaceEvent}, letreiro no cliente, contadores
 * {@code visit.<tipo>} e flag {@code visit.place.<id>}), e das zonas seguras ({@code safeZone} + config
 * {@code cityProtection}): magias não destroem blocos, explosões não quebram blocos e monstros não nascem
 * naturalmente lá dentro. Chame no servidor.
 *
 * <pre>
 * Places.register(level, new Places.Place("city_120_-340", Component.literal("Valdória"), "city", "kingdom",
 *         level.dimension(), caixa, centro, true, 0x3F6FD8));
 * Places.Place aqui = Places.at(level, player.blockPosition());          // null fora de qualquer lugar
 * </pre>
 */
public final class Places {

    /**
     * Um lugar.
     *
     * @param id       único no mundo ({@code a-z0-9_-}), ex. {@code city_120_-340}
     * @param name     nome mostrado no letreiro ("Valdória")
     * @param type     {@code "capital"}, {@code "city"}, {@code "village"}... (contador {@code visit.<type>})
     * @param faction  id da facção dona (subtítulo do letreiro: {@code faction.isekai_mahou.<faction>}); "" = nenhuma
     * @param dim      dimensão
     * @param box      área do lugar (inclusive)
     * @param center   centro (teleporte, distância)
     * @param safeZone zona segura (sem destruição, explosões sem quebrar blocos, sem monstros naturais)
     * @param color    cor RGB do letreiro
     */
    public record Place(String id, Component name, String type, String faction, ResourceKey<Level> dim, BoundingBox box,
                        BlockPos center, boolean safeZone, int color) {
        public boolean contains(BlockPos pos) {
            return box.isInside(pos);
        }

        /** Volume da caixa (lugares menores dentro de maiores ganham na busca por posição). */
        public long volume() {
            return (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        }
    }

    /** Registra ou atualiza (mesmo id) um lugar. Retorna o lugar guardado. */
    public static Place register(ServerLevel level, Place place) {
        return PlaceData.register(level, place);
    }

    public static void remove(ServerLevel level, String id) {
        PlaceData.remove(level, id);
    }

    @Nullable
    public static Place get(ServerLevel level, String id) {
        return PlaceData.get(level, id);
    }

    /** Lugar que contém a posição (o menor, se houver vários aninhados), ou null. */
    @Nullable
    public static Place at(ServerLevel level, BlockPos pos) {
        return PlaceData.at(level, pos);
    }

    /** Lugar mais próximo (pelo centro) neste nível, opcionalmente só de um tipo; null se não houver. */
    @Nullable
    public static Place nearest(ServerLevel level, BlockPos pos, @Nullable String type) {
        return PlaceData.nearest(level, pos, type);
    }

    /** Todos os lugares deste nível. */
    public static List<Place> all(ServerLevel level) {
        return PlaceData.all(level);
    }

    /** A posição está numa zona segura (lugar seguro e config {@code cityProtection} ligada)? */
    public static boolean isSafe(ServerLevel level, BlockPos pos) {
        return PlaceData.safeAt(level, pos);
    }

    private Places() {}
}
