package com.renanpontes.isekaimahou.feature.settlements;

import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.network.SettlementsNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Onda 7 (sky_ui) — lado do servidor da ESCOLHA DE TERRENO na fundação ("No chão" / "Ilha Celeste (SkyLand)").
 * A prévia do cliente ({@code client/CityPlacer}) manda o terreno escolhido no campo {@code arg} dos pedidos
 * {@code found} e {@code found_open}; aqui o servidor confere com as regras do céu
 * ({@link SettlementService#foundingProblem(ServerPlayer, BlockPos, String)}) e devolve a ALTITUDE da ilha — quem decide a
 * altura é sempre o servidor ({@link SkyCity#heartY}); o cliente só desenha.
 */
public final class SkyFounding {

    /**
     * Resposta da prévia para a Ilha Celeste. Devolve false se o terreno pedido não é o céu (o chamador responde com a
     * prévia do chão, como sempre foi). Campos novos: {@code Sky} (true) e {@code SkyY} (altitude do Coração, -1 = o
     * terreno ali é alto demais). Os 25 lotes vão como "terreno bom": a ilha é plana.
     */
    static boolean probe(ServerPlayer sp, BlockPos pos, String terrain, CompoundTag out) {
        if (!SkyCity.SKYLAND.equals(terrain)) return false;
        Component pr = SettlementService.foundingProblem(sp, pos, SkyCity.SKYLAND);
        out.putBoolean("Ok", pr == null);
        if (pr != null) out.putString("Problem", SettlementView.json(pr, sp.registryAccess()));
        out.putBoolean("Sky", true);
        out.putInt("SkyY", SkyCity.heartY(sp.serverLevel(), pos));
        out.putIntArray("Cells", new int[CityLayout.CELLS]);
        return true;
    }

    /** O jogador confirmou a prévia: abre a tela de estilo/nome se o terreno escolhido serve (senão diz o motivo). */
    static void open(ServerPlayer sp, BlockPos pos, String terrain) {
        if (!SkyCity.SKYLAND.equals(terrain)) {
            SettlementService.openFounding(sp, pos);
            return;
        }
        Component problem = SettlementService.foundingProblem(sp, pos, SkyCity.SKYLAND);
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withColor(0xE8735E), true);
            sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }
        PacketDistributor.sendToPlayer(sp, new SettlementsNetwork.FoundOpen(pos));
    }

    private SkyFounding() {}
}
