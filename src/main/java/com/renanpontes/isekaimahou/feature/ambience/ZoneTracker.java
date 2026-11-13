package com.renanpontes.isekaimahou.feature.ambience;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * SERVIDOR. Descobre em que "zona de música" cada jogador está e avisa o cliente quando muda:
 * <ul>
 * <li>dimensões: Abismo (as duas) → {@code abyss}; Reino do Lar → {@code home};</li>
 * <li>estruturas do mundo (pelo id, sem depender das classes dos módulos): cidadela → {@code citadel}, biblioteca →
 * {@code library}, torres → {@code tower}, masmorras/criptas/covis → {@code dungeon}, capitais/guildas → {@code city};</li>
 * <li>lugares nomeados ({@link Places}): cidades NPC e cidades de jogador → {@code city}.</li>
 * </ul>
 * Custo: uma consulta a cada 2 s por jogador; enquanto ele continua dentro da mesma caixa nem isso (cache).
 * Só lê — nunca mexe em blocos nem cria nada.
 */
public final class ZoneTracker {
    private static final int CHECK_EVERY = 40;
    /** Folga em volta da estrutura: a música começa um pouco antes da porta. */
    private static final int MARGIN = 6;

    /** Última zona mandada + a caixa onde ela vale (null = sem caixa: dimensão inteira ou "nenhuma"). */
    private record Seen(String zone, ResourceKey<Level> dim, @Nullable BoundingBox box) {}

    private static final Map<UUID, Seen> LAST = new HashMap<>();
    /** id da estrutura → zona ("" = nenhuma). Os ids não mudam com o jogo rodando. */
    private static final Map<ResourceLocation, String> STRUCTURE_ZONE = new ConcurrentHashMap<>();

    static void init(IEventBus bus) {
        bus.addListener(ZoneTracker::onTick);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> LAST.remove(e.getEntity().getUUID()));
        // mudou de mundo / renasceu: confere de novo na hora (a música antiga não fica tocando no lugar errado)
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> recheck(e.getEntity()));
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> recheck(e.getEntity()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> recheck(e.getEntity()));
    }

    private static void recheck(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        LAST.remove(sp.getUUID());
        update(sp);
    }

    private static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if ((sp.tickCount + sp.getId()) % CHECK_EVERY != 0) return;
        update(sp);
    }

    private static void update(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        BlockPos pos = sp.blockPosition();
        Seen last = LAST.get(sp.getUUID());
        // ainda dentro da mesma estrutura: nada a fazer
        if (last != null && last.box != null && last.dim == level.dimension() && last.box.isInside(pos)) return;
        Seen now = detect(level, pos);
        if (last != null && last.zone.equals(now.zone)) {
            LAST.put(sp.getUUID(), now);
            return;
        }
        LAST.put(sp.getUUID(), now);
        PacketDistributor.sendToPlayer(sp, new AmbienceNet.Zone(now.zone, AmbienceNet.MODE_DETECTED));
        // dica única: onde baixar/desligar a música
        if (!now.zone.isEmpty()) Hints.show(sp, "ambience_music");
    }

    /** Zona do jogador agora (comando de teste). */
    public static String zoneAt(ServerLevel level, BlockPos pos) {
        return detect(level, pos).zone;
    }

    private static Seen detect(ServerLevel level, BlockPos pos) {
        ResourceKey<Level> dim = level.dimension();
        ResourceLocation dimId = dim.location();
        if (IsekaiMahou.MODID.equals(dimId.getNamespace())) {
            String path = dimId.getPath();
            if (path.equals("endless_abyss") || path.equals("abyss")) return new Seen("abyss", dim, null);
            if (path.equals("home_realm")) return new Seen("home", dim, null);
        }
        Seen structure = structureZone(level, pos);
        if (structure != null) return structure;
        Places.Place place = Places.at(level, pos);
        // cidade não guarda caixa: ela cresce, muda de dono ou some, então é conferida de novo a cada vez
        if (place != null && isCityType(place.type())) return new Seen("city", dim, null);
        return new Seen("", dim, null);
    }

    private static boolean isCityType(String type) {
        return "capital".equals(type) || "city".equals(type) || "village".equals(type);
    }

    @Nullable
    private static Seen structureZone(ServerLevel level, BlockPos pos) {
        Map<Structure, LongSet> here = level.structureManager().getAllStructuresAt(pos);
        if (here.isEmpty()) return null;
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Seen[] found = new Seen[1];
        for (Map.Entry<Structure, LongSet> en : here.entrySet()) {
            ResourceLocation id = registry.getKey(en.getKey());
            if (id == null) continue;
            String zone = STRUCTURE_ZONE.computeIfAbsent(id, ZoneTracker::classify);
            if (zone.isEmpty()) continue;
            level.structureManager().fillStartsForStructure(en.getKey(), en.getValue(), start -> {
                if (found[0] != null || !start.isValid()) return;
                BoundingBox box = start.getBoundingBox().inflatedBy(MARGIN);
                if (box.isInside(pos)) found[0] = new Seen(zone, level.dimension(), "city".equals(zone) ? null : box);
            });
            // masmorra dentro de cidade ganha da cidade; a primeira zona "forte" encerra a busca
            if (found[0] != null && !"city".equals(found[0].zone)) return found[0];
        }
        return found[0];
    }

    /** Pelo NOME da estrutura: pega também as variantes novas (torre da floresta, submersa...) sem mudar código. */
    private static String classify(ResourceLocation id) {
        String p = id.getPath();
        if (IsekaiMahou.MODID.equals(id.getNamespace())) {
            if (p.contains("citadel")) return "citadel";
            if (p.contains("library")) return "library";
            if (p.contains("tower")) return "tower";
            if (p.contains("dungeon") || p.contains("depths") || p.contains("crypt") || p.contains("labyrinth")
                    || p.contains("dwarf_hold") || p.contains("tomb") || p.contains("vault") || p.endsWith("_den")
                    || p.endsWith("_lair")) return "dungeon";
            if (p.contains("capital") || p.endsWith("_city") || p.startsWith("guild_")) return "city";
            return "";
        }
        if ("minecraft".equals(id.getNamespace())) {
            if (p.equals("stronghold") || p.equals("trial_chambers") || p.equals("ancient_city")) return "dungeon";
            if (p.equals("fortress") || p.equals("bastion_remnant")) return "citadel";
        }
        return "";
    }

    private ZoneTracker() {}
}
