package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Travel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * As estruturas do Mundo Normal onde os portais esperam ADORMECIDOS (Portão Elemental, Fenda Abissal, Escadaria
 * Celeste, Ruptura do Crepúsculo — geradas pelo módulo world_v09, que registra cada uma no {@link Places} com id
 * {@code <estrutura>_<chunkX>_<chunkZ>}). Aqui só se lê: quais o jogador já achou (para {@code /mahou dimension info}) e
 * se uma peça de portal está dentro de uma delas (proteção: o portal da estrutura não pode ser desmontado e levado).
 */
public final class DimSites {
    /** Tipo de lugar das estruturas de portal (world_v09). */
    public static final String PORTAL_SITE_TYPE = "portal_site";

    /** O jogador já encontrou a estrutura do portal desta dimensão? (contador do world_v09 ou lugar visitado) */
    public static boolean discovered(ServerPlayer player, DimKind kind) {
        return MagicApi.get(player).counter("visit.site." + kind.portalSite) > 0 || !known(player, kind).isEmpty();
    }

    /** O jogador já abriu este caminho alguma vez (portal aceso por ele ou dimensão visitada)? */
    public static boolean pathOpened(ServerPlayer player, DimKind kind) {
        MagicData d = MagicApi.get(player);
        return d.hasFlag("dimensions.portal_opened." + kind.id) || d.hasFlag("dimensions.seen." + kind.id)
                || d.counter(kind.visitKey()) > 0;
    }

    /** Estruturas deste portal que o jogador já visitou, da mais perto para a mais longe (mesma dimensão primeiro). */
    public static List<Places.Place> known(ServerPlayer player, DimKind kind) {
        List<Places.Place> out = new ArrayList<>();
        MinecraftServer server = player.getServer();
        if (server == null) return out;
        for (ServerLevel level : server.getAllLevels()) {
            for (Places.Place place : Places.all(level)) {
                if (isSiteOf(place, kind) && Travel.discovered(player, place.id())) out.add(place);
            }
        }
        BlockPos here = player.blockPosition();
        out.sort(Comparator.<Places.Place>comparingInt(p -> p.dim().equals(player.level().dimension()) ? 0 : 1)
                .thenComparingDouble(p -> p.center().distSqr(here)));
        return out;
    }

    /** O lugar é uma estrutura do portal desta dimensão? (pelo prefixo do id; pedras e hubs ficam de fora) */
    public static boolean isSiteOf(Places.Place place, DimKind kind) {
        return place.id().startsWith(kind.portalSite + "_") && !Travel.WAYSTONE_TYPE.equals(place.type())
                && !Travel.HUB_TYPE.equals(place.type());
    }

    /** A posição está dentro de alguma estrutura de portal? */
    public static boolean inPortalSite(ServerLevel level, BlockPos pos) {
        for (Places.Place place : Places.all(level)) {
            if (!place.contains(pos)) continue;
            if (PORTAL_SITE_TYPE.equals(place.type())) return true;
            for (DimKind k : DimKind.values()) if (isSiteOf(place, k)) return true;
        }
        return false;
    }

    /**
     * Peças que fazem o portal funcionar: molduras, portais acesos, Farol (e o ouro sob ele), Fenda e o feixe de luz.
     * Só elas são protegidas nas estruturas — o resto da ruína pode ser explorado normalmente.
     */
    public static boolean isPortalPiece(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(DimRegistry.ELEMENTAL_PORTAL_FRAME.get()) || state.is(DimRegistry.ABYSS_PORTAL_FRAME.get())
                || state.is(DimRegistry.ELEMENTAL_PORTAL.get()) || state.is(DimRegistry.ABYSS_PORTAL.get())
                || state.is(DimRegistry.CELESTIAL_BEACON.get()) || state.is(DimRegistry.CELESTIAL_PORTAL.get())
                || state.is(DimRegistry.TWILIGHT_RIFT.get())) {
            return true;
        }
        return state.is(Blocks.GOLD_BLOCK) && level.getBlockState(pos.above()).is(DimRegistry.CELESTIAL_BEACON.get());
    }

    /** Peça de portal dentro de uma estrutura de portal (não pode ser quebrada nem explodida em sobrevivência). */
    public static boolean isProtectedPiece(ServerLevel level, BlockPos pos, BlockState state) {
        // Nas dimensões do mod quem protege é o hub (DimEvents); as estruturas de portal ficam no Mundo Normal.
        if (DimKind.of(level.dimension()) != null) return false;
        return isPortalPiece(level, pos, state) && inPortalSite(level, pos);
    }

    /**
     * Proteção contra a destruição por magias (core {@code Destruction}): a área dos hubs nas dimensões do mod (a arena
     * da Fenda não é zona segura do {@code Places}, mas precisa continuar de pé para o Arauto voltar) e as peças de
     * portal das estruturas do Mundo Normal. Chamado por bloco: primeiro os testes baratos.
     */
    public static boolean destructionProtected(ServerLevel level, BlockPos pos) {
        DimKind kind = DimKind.of(level.dimension());
        if (kind != null) return kind.isProtected(pos);
        BlockState state = level.getBlockState(pos);
        return isPortalPiece(level, pos, state) && inPortalSite(level, pos);
    }

    private DimSites() {}
}
