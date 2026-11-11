package com.renanpontes.isekaimahou.core.status;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.event.PlaceEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Entrada/saída de jogadores nos {@link Places.Place} (a cada 20 ticks) e as regras das zonas seguras: explosões não
 * quebram blocos lá dentro e monstros não nascem naturalmente.
 */
public final class PlaceTracker {
    private static final Map<UUID, Places.Place> CURRENT = new HashMap<>();

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 7) return;
        check(sp);
    }

    /** Confere em que lugar o jogador está e avisa entrada/saída. */
    public static void check(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        Places.Place now = sp.isSpectator() ? null : PlaceData.at(level, sp.blockPosition());
        Places.Place old = CURRENT.get(sp.getUUID());
        if (now != null && old != null && now.id().equals(old.id()) && now.dim().equals(old.dim())) {
            CURRENT.put(sp.getUUID(), now); // pode ter sido atualizado (nome, caixa)
            return;
        }
        if (now == null && old == null) return;
        if (old != null) NeoForge.EVENT_BUS.post(new PlaceEvent.Leave(sp, old));
        boolean first = false;
        if (now != null) {
            MagicData d = MagicApi.get(sp);
            String flag = "visit.place." + now.id();
            first = !d.hasFlag(flag);
            if (first) {
                d.setFlag(flag, true);
                if (!now.type().isBlank()) MagicApi.addCounter(sp, "visit." + now.type(), 1);
            }
            CURRENT.put(sp.getUUID(), now);
            NeoForge.EVENT_BUS.post(new PlaceEvent.Enter(sp, now, first));
        } else {
            CURRENT.remove(sp.getUUID());
        }
        PacketDistributor.sendToPlayer(sp, new StatusPayloads.PlaceChange(Optional.ofNullable(now), Optional.ofNullable(old), first));
    }

    @Nullable
    public static Places.Place current(ServerPlayer sp) {
        return CURRENT.get(sp.getUUID());
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // O cliente começa sem lugar: o próximo check reenvia (com letreiro, como uma chegada)
        CURRENT.remove(event.getEntity().getUUID());
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CURRENT.remove(event.getEntity().getUUID());
    }

    static void clear() {
        CURRENT.clear();
    }

    // =====================================================================================
    //  Zonas seguras
    // =====================================================================================
    static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || event.getAffectedBlocks().isEmpty()) return;
        event.getAffectedBlocks().removeIf(pos -> PlaceData.safeAt(sl, pos));
    }

    /** Spawn natural e de geração de chunk (pode rodar fora da thread principal: só lê o cache). */
    static void onSpawnPosition(MobSpawnEvent.PositionCheck event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) return;
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        ServerLevel sl = event.getLevel().getLevel();
        if (PlaceData.safeAt(sl, event.getEntity().blockPosition())) event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    /** Patrulhas e outros spawns naturais que não passam pela checagem de posição (fantasmas). */
    static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION && type != MobSpawnType.PATROL) return;
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        ServerLevel sl = event.getLevel().getLevel();
        if (PlaceData.safeAt(sl, event.getEntity().blockPosition())) event.setSpawnCancelled(true);
    }

    private PlaceTracker() {}
}
