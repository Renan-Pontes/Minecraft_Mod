package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * v1.1 ("mundo vivo"): <b>sinos da cidade</b>. Ao amanhecer (6h) e no fim da tarde (18h), as cidades com alguém dentro
 * tocam o sino três vezes no centro (o coração da cidade) — dá o ritmo do dia sem nada na tela.
 * <p>
 * Custo: uma conta de hora a cada segundo; só nas duas janelas do dia olha em que cidade cada jogador está
 * ({@link CityPlaces#cityAt}). Cada cidade toca no máximo uma vez por janela. Respeita {@code /mahou city routine off}.
 */
public final class CityBells {
    /** Janelas (ticks do dia): alvorada e fim da tarde. */
    private static final int DAWN_FROM = 0, DAWN_TO = 400, DUSK_FROM = 12000, DUSK_TO = 12400;
    private static final int TOLLS = 3, TOLL_GAP = 34;
    /** Volume do sino: alcance ≈ 16 × volume blocos (cidades são grandes). */
    private static final float VOLUME = 5.0F;

    private record Toll(ServerLevel level, BlockPos pos, long at, float pitch) {}

    /** Cidade → "dia*2 + janela" do último toque. */
    private static final Map<String, Long> RUNG = new HashMap<>();
    private static final List<Toll> PENDING = new ArrayList<>();

    private CityBells() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, CityBells::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            RUNG.clear();
            PENDING.clear();
        });
    }

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (!PENDING.isEmpty()) {
            for (int i = PENDING.size() - 1; i >= 0; i--) {
                Toll t = PENDING.get(i);
                if (t.level != level || now < t.at) continue;
                PENDING.remove(i);
                level.playSound(null, t.pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, VOLUME, t.pitch);
            }
        }
        if (now % 20 != 0 || level.players().isEmpty() || !CityRoutine.enabled() || level.dimensionType().hasFixedTime()) return;
        long dayTime = level.getDayTime();
        int tod = (int) Math.floorMod(dayTime, 24000L);
        int window;
        if (tod >= DAWN_FROM && tod < DAWN_TO) window = 0;
        else if (tod >= DUSK_FROM && tod < DUSK_TO) window = 1;
        else return;
        long stamp = Math.floorDiv(dayTime, 24000L) * 2 + window;
        for (ServerPlayer p : level.players()) {
            Places.Place city = CityPlaces.cityAt(level, p.blockPosition());
            if (city == null) continue;
            Long last = RUNG.get(city.id());
            if (last != null && last == stamp) continue;
            RUNG.put(city.id(), stamp);
            // alvorada: sino mais aberto; fim da tarde: mais grave
            float pitch = window == 0 ? 0.9F : 0.75F;
            BlockPos at = city.center().above(4);
            for (int i = 0; i < TOLLS; i++) PENDING.add(new Toll(level, at, now + 10 + (long) i * TOLL_GAP, pitch));
        }
    }
}
