package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.cities.block.CityHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.cities.entity.CityMerchant;
import com.renanpontes.isekaimahou.feature.cities.entity.Profession;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Onda 10 (vehicles_mechanic) — <b>um Mecânico em toda cidade do mundo</b>, para quem ainda não tem cidade comprar
 * veículos ("pra que sejam vendidos na cidade por um mecânico"). Sem mexer nos moldes: o coração da cidade traz o
 * Mecânico uma vez (mundo novo ou cidade que já existia), num ponto livre da praça — o mesmo cuidado do
 * {@link CityFixtures}: só sobre chão firme, com ar em volta, longe das avenidas; nada é quebrado nem posto.
 * <p>Depois disso ele é um morador como os outros: se morrer, entra na fila de reposição da cidade
 * ({@code merchant:mechanic}). A marca de "já veio" fica nos dados do coração ({@code MechanicDone}).
 */
public final class CityMechanic {
    private static final String DONE = "MechanicDone";
    private static final String TRIES = "MechanicTries";
    /** Tentativas com a praça carregada e sem ponto livre antes de desistir (uma a cada 10 s). */
    private static final int MAX_TRIES = 90;

    /** Pontos candidatos (deslocamento do coração): anéis em volta do chafariz, fora das avenidas. */
    private static final List<int[]> SPOTS = new ArrayList<>();

    static {
        for (int radius : new int[] {14, 18, 10, 22}) {
            for (int i = 0; i < 16; i++) {
                double a = (i + 0.5) * Math.PI / 8;
                int dx = (int) Math.round(Math.cos(a) * radius), dz = (int) Math.round(Math.sin(a) * radius);
                if (Math.abs(dx) <= 4 || Math.abs(dz) <= 4) continue;
                SPOTS.add(new int[] {dx, dz});
            }
        }
    }

    private CityMechanic() {}

    /** Chamado pelo coração a cada 10 s até o Mecânico chegar (depois é só ler uma marca). */
    public static void tick(ServerLevel sl, CityHeartBlockEntity heart) {
        CompoundTag data = heart.getPersistentData();
        if (data.getBoolean(DONE) || "home_realm".equals(sl.dimension().location().getPath())) return;
        if (!BuiltInRegistries.ITEM.containsKey(IsekaiMahou.id("golem_cart"))) return;   // módulo de veículos ausente
        Places.Place place = Places.get(sl, heart.placeId());
        if (place == null) return;
        BlockPos c = heart.getBlockPos();
        int y = heart.floorY() + 1;
        int start = Math.floorMod(c.getX() * 13 + c.getZ() * 7, 8);
        boolean unloaded = false;
        for (int i = 0; i < SPOTS.size(); i++) {
            int[] s = SPOTS.get(i < 8 ? (i + start) % 8 : i);
            BlockPos p = new BlockPos(c.getX() + s[0], y, c.getZ() + s[1]);
            if (!place.contains(p)) continue;
            if (!sl.isPositionEntityTicking(p)) {
                unloaded = true;
                continue;
            }
            if (!fits(sl, p)) continue;
            if (spawn(sl, heart, p)) {
                data.putBoolean(DONE, true);
                heart.setChanged();
                return;
            }
        }
        if (unloaded) return;
        int tries = data.getInt(TRIES) + 1;
        data.putInt(TRIES, tries);
        heart.setChanged();
        if (tries >= MAX_TRIES) {
            // praça sem um canto livre: o Mecânico fica no meio da praça mesmo (perto do coração) — nunca falta
            int fx = c.getX() + 3, fz = c.getZ() + 3;
            BlockPos p = new BlockPos(fx, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fx, fz), fz);
            if (spawn(sl, heart, p)) {
                data.putBoolean(DONE, true);
                IsekaiMahou.LOGGER.info("Cidade {}: sem canto livre, Mecânico posto perto do coração", heart.placeId());
            }
        }
    }

    private static boolean spawn(ServerLevel sl, CityHeartBlockEntity heart, BlockPos p) {
        CityMerchant m = CitiesContent.CITY_MERCHANT.get().create(sl);
        if (m == null) return false;
        m.setProfession(Profession.MECHANIC);
        m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, sl.random.nextFloat() * 360F, 0F);
        m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
        m.setProfession(Profession.MECHANIC);
        m.bindToCity(heart.placeId(), p);
        m.joinKingdom(heart.kingdom());
        if (!sl.addFreshEntity(m)) return false;
        CityData.get(sl).registerNpc(heart.placeId(), m.censusKey());
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + 1.0, m.getZ(), 10, 0.4, 0.6, 0.4, 0.0);
        IsekaiMahou.LOGGER.info("Cidade {}: Mecânico chegou em {}", heart.placeId(), p.toShortString());
        return true;
    }

    /** Lugar livre: chão firme, o ponto e os 8 vizinhos em ar (com 2 de altura), sem água nem borda. */
    private static boolean fits(ServerLevel sl, BlockPos p) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, 0, dz);
                if (!open(sl, n) || !sl.getBlockState(n.below()).isFaceSturdy(sl, n.below(), Direction.UP)) return false;
            }
        }
        return true;
    }

    private static boolean open(ServerLevel sl, BlockPos p) {
        BlockState st = sl.getBlockState(p);
        return (st.isAir() || st.is(Blocks.SNOW)) && sl.getBlockState(p.above()).isAir() && sl.getFluidState(p).isEmpty();
    }
}
