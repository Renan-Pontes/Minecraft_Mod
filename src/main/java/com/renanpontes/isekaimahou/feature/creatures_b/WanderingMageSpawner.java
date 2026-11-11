package com.renanpontes.isekaimahou.feature.creatures_b;

import java.util.List;

import com.renanpontes.isekaimahou.feature.creatures_b.entity.WanderingMageEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Faz o Mago Errante visitar os jogadores de vez em quando (como o vendedor ambulante): a cada minuto,
 * 4% de chance no Overworld de ele surgir a 16–30 blocos de um jogador aleatório, se não houver outro por perto.
 * Respeita a regra de jogo doTraderSpawning.
 */
public final class WanderingMageSpawner {
    private static final int CHECK_INTERVAL = 1200;
    private static final float CHANCE = 0.04F;
    private static final int STAY_TICKS = 36000;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WanderingMageSpawner::onLevelTick);
    }

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % CHECK_INTERVAL != 0) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DO_TRADER_SPAWNING)) return;
        if (level.random.nextFloat() >= CHANCE) return;
        List<ServerPlayer> players = level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
        if (players.isEmpty()) return;
        ServerPlayer player = players.get(level.random.nextInt(players.size()));
        if (!level.getEntitiesOfClass(WanderingMageEntity.class, player.getBoundingBox().inflate(128.0)).isEmpty()) return;
        trySpawn(level, player);
    }

    private static void trySpawn(ServerLevel level, ServerPlayer player) {
        for (int i = 0; i < 10; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = 16 + level.random.nextDouble() * 14;
            BlockPos column = BlockPos.containing(player.getX() + Math.cos(a) * d, player.getY(), player.getZ() + Math.sin(a) * d);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (Math.abs(pos.getY() - player.getY()) > 12) continue;
            if (!SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, CbRegistries.WANDERING_MAGE.get())) continue;
            if (!level.getFluidState(pos).isEmpty()) continue;
            WanderingMageEntity mage = CbRegistries.WANDERING_MAGE.get().spawn(level, pos, MobSpawnType.EVENT);
            if (mage == null) continue;
            mage.setDespawnDelay(STAY_TICKS);
            mage.setWanderTarget(player.blockPosition());
            mage.arrivalFx();
            return;
        }
    }

    private WanderingMageSpawner() {}
}
