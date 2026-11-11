package com.renanpontes.isekaimahou.feature.travel;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.travel.block.TravelStoneBlockEntity;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import org.joml.Vector3f;

/**
 * Regras do servidor das Pedras de Teleporte: de qual lugar a pedra é, nome, ponto de chegada, sintonia, abrir a tela
 * e o brilho da pedra de partida/chegada (pelo contador {@code travel.count} do núcleo, +1 a cada viagem).
 */
public final class TravelStones {
    /** Cor das pedras e dos lugares "waystone". */
    public static final int WAYSTONE_RGB = 0x6FE3FF;
    /** Contador do núcleo (+1 por viagem concluída). */
    private static final String COUNTER_TRIPS = "travel.count";
    /** Tipos de lugar que nunca são destino (os mesmos do núcleo). */
    private static final Set<String> NOT_TRAVEL = Set.of("hunting_ground", "trial", "arena");
    /** Estruturas de cidade: a pedra espera o coração registrar a cidade. */
    private static final Set<String> CITY_STRUCTURES = Set.of("guild_city", "capital_city", "capital_sahra", "capital_nordheim");
    /** Estruturas novas da v0.9 com nome próprio aqui (as antigas já têm {@code structure.isekai_mahou.<id>}). */
    private static final Set<String> KNOWN_SITES = Set.of("hydra_lair", "swamp_hydra_lair", "wyvern_peak", "basilisk_tomb",
            "colossus_ruins", "elemental_gate", "abyssal_chasm", "celestial_stairway", "twilight_rupture", "ascension_sanctum",
            "false_hydra_lair", "mimic_vault", "manticore_den", "guild_hall_village", "goblin_fortress", "minotaur_labyrinth");
    /** Maior área de estrutura usada como área do lugar da pedra (acima disso, uma área média em volta da pedra). */
    private static final int MAX_SITE_SPAN = 160;
    private static final int NEAR_PLACE_RANGE = 400;
    /** Janela entre abrir a tela da pedra e a viagem para a pedra brilhar na partida (= sessão de pedra do núcleo, 5 min). */
    private static final long DEPARTURE_WINDOW = 5 * 60 * 20;

    private record Departure(ResourceKey<Level> dim, BlockPos pos, long at) {}

    private static final Map<UUID, Departure> DEPARTURES = new HashMap<>();

    // =====================================================================================
    //  Lugares
    // =====================================================================================
    /** Id do lugar próprio de uma pedra solta (único no mundo: inclui a dimensão fora do Overworld). */
    public static String ownId(ServerLevel sl, BlockPos pos) {
        String dim = sl.dimension() == Level.OVERWORLD ? ""
                : sl.dimension().location().getPath().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_") + "_";
        return "waystone_" + dim + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    public static boolean travelable(Places.Place p) {
        return p != null && !NOT_TRAVEL.contains(p.type()) && !Travel.isHub(p);
    }

    public static boolean isWaystone(Places.Place p) {
        return p != null && Travel.WAYSTONE_TYPE.equals(p.type());
    }

    /**
     * Lugar viajável (de outro dono, hubs incluídos) que contém a pedra: o menor que não seja "waystone"; com {@code allowWaystones},
     * na falta dele, o menor lugar de outra pedra.
     */
    @Nullable
    public static Places.Place host(ServerLevel sl, BlockPos pos, @Nullable String ownId, boolean allowWaystones) {
        Places.Place best = null, bestStone = null;
        for (Places.Place p : Places.all(sl)) {
            // Hubs de dimensão também servem: a pedra vira a chegada do hub
            if (!p.contains(pos) || p.id().equals(ownId) || NOT_TRAVEL.contains(p.type()) || !p.dim().equals(sl.dimension())) continue;
            if (isWaystone(p)) {
                if (allowWaystones && (bestStone == null || p.volume() < bestStone.volume())) bestStone = p;
            } else if (best == null || p.volume() < best.volume()) {
                best = p;
            }
        }
        return best != null ? best : bestStone;
    }

    /** Cor do ponto/letreiro pelo tipo do lugar. */
    public static int typeColor(Places.Place p) {
        return switch (p.type()) {
            case "capital" -> 0xFFD86B;
            case "city" -> 0x6FA8FF;
            case "village" -> 0x7CE07C;
            case Travel.WAYSTONE_TYPE -> WAYSTONE_RGB;
            default -> p.color() != 0 ? p.color() : WAYSTONE_RGB;
        };
    }

    @Nullable
    private static StructureStart structureAt(ServerLevel sl, BlockPos pos) {
        if (!sl.isLoaded(pos)) return null;
        StructureStart start = sl.structureManager().getStructureWithPieceAt(pos, h -> true);
        return start.isValid() ? start : null;
    }

    @Nullable
    private static ResourceLocation structureId(ServerLevel sl, StructureStart start) {
        Structure s = start.getStructure();
        return sl.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(s);
    }

    /**
     * Quanto uma pedra de estrutura espera outro módulo registrar o lugar dela antes de criar o próprio: cidade/capital
     * (o coração registra a cidade) 1 min; outras estruturas do mod (covis, portais, santuários registram o lugar ao
     * carregar ou quando o chefe aparece) 10 s; fora de estruturas do mod, nada.
     */
    public static int registerWait(ServerLevel sl, BlockPos pos) {
        if (!sl.isLoaded(pos)) return 0;
        StructureStart start = sl.structureManager().getStructureWithPieceAt(pos, h -> h.unwrapKey()
                .map(k -> IsekaiMahou.MODID.equals(k.location().getNamespace())).orElse(false));
        if (!start.isValid()) return 0;
        ResourceLocation id = structureId(sl, start);
        return id != null && CITY_STRUCTURES.contains(id.getPath()) ? 1200 : 200;
    }

    /** Área do lugar próprio: a da estrutura onde a pedra está (se couber) ou uma área pequena em volta. */
    public static BoundingBox siteBox(ServerLevel sl, BlockPos pos, boolean playerMade) {
        if (!playerMade) {
            StructureStart start = structureAt(sl, pos);
            if (start != null) {
                BoundingBox b = start.getBoundingBox();
                if (b.isInside(pos) && b.getXSpan() <= MAX_SITE_SPAN && b.getZSpan() <= MAX_SITE_SPAN) {
                    return b.inflatedBy(2);
                }
                return new BoundingBox(pos.getX() - 16, pos.getY() - 8, pos.getZ() - 16, pos.getX() + 16, pos.getY() + 16,
                        pos.getZ() + 16);
            }
        }
        return new BoundingBox(pos.getX() - 4, pos.getY() - 2, pos.getZ() - 4, pos.getX() + 4, pos.getY() + 4, pos.getZ() + 4);
    }

    /**
     * Nome do lugar próprio: o da bigorna/etiqueta → "Pedra de &lt;dono&gt;" → o da estrutura → "Arredores de &lt;lugar
     * mais próximo&gt;" → o do bioma.
     */
    public static Component nameFor(ServerLevel sl, TravelStoneBlockEntity be) {
        if (be.customName() != null) return be.customName();
        BlockPos pos = be.getBlockPos();
        if (be.playerMade() && !be.ownerName().isEmpty()) {
            return Component.translatable("travel.isekai_mahou.stone.owned", be.ownerName());
        }
        Component site = siteName(sl, pos);
        if (site != null) return site;
        Places.Place near = null;
        double best = (double) NEAR_PLACE_RANGE * NEAR_PLACE_RANGE;
        for (Places.Place p : Places.all(sl)) {
            if (isWaystone(p) || !travelable(p)) continue;
            double d = p.center().distSqr(pos);
            if (d < best) {
                best = d;
                near = p;
            }
        }
        if (near != null) return Component.translatable("travel.isekai_mahou.stone.near", near.name());
        Component biome = sl.getBiome(pos).unwrapKey()
                .<Component>map(k -> Component.translatable(Util.makeDescriptionId("biome", k.location())))
                .orElse(Component.translatable("travel.isekai_mahou.stone.wilds"));
        return Component.translatable("travel.isekai_mahou.stone.biome", biome);
    }

    @Nullable
    private static Component siteName(ServerLevel sl, BlockPos pos) {
        StructureStart start = structureAt(sl, pos);
        if (start == null) return null;
        ResourceLocation id = structureId(sl, start);
        if (id == null) return null;
        String path = id.getPath();
        if (IsekaiMahou.MODID.equals(id.getNamespace())) {
            String key = "structure.isekai_mahou." + path;
            if (Language.getInstance().has(key)) return Component.translatable(key);
            String own = "travel.isekai_mahou.site." + path;
            if (KNOWN_SITES.contains(path)) return Component.translatable(own);
            if (path.startsWith("shrine_")) return Component.translatable("travel.isekai_mahou.site.shrine");
            return null;
        }
        if ("minecraft".equals(id.getNamespace()) && path.startsWith("village")) {
            return Component.translatable("travel.isekai_mahou.site.village");
        }
        return null;
    }

    // =====================================================================================
    //  Chegada
    // =====================================================================================
    /** Pés num chão firme ao lado da pedra (anel de 2, depois de 1 bloco; mesma altura, 1 acima, 1 abaixo). */
    @Nullable
    public static BlockPos findAnchor(ServerLevel sl, BlockPos stone) {
        int[] dys = {0, 1, -1};
        for (int dy : dys) {
            for (int ring = 2; ring >= 1; ring--) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                        BlockPos p = stone.offset(dx, dy, dz);
                        if (sl.isLoaded(p) && sl.isLoaded(p.below()) && standable(sl, p) && headroom(sl, p)) return p;
                    }
                }
            }
        }
        return null;
    }

    /** Chão firme e seguro embaixo, dois blocos livres (mesmas regras do núcleo). */
    public static boolean standable(ServerLevel sl, BlockPos feet) {
        if (!sl.isLoaded(feet)) return true; // sem como checar agora: mantém
        BlockPos below = feet.below();
        BlockState floor = sl.getBlockState(below);
        if (!floor.isFaceSturdy(sl, below, Direction.UP) || !floor.getFluidState().isEmpty()) return false;
        if (floor.is(BlockTags.FIRE) || floor.is(BlockTags.CAMPFIRES) || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS)
                || floor.is(Blocks.POWDER_SNOW)) return false;
        return free(sl, feet) && free(sl, feet.above());
    }

    /** Céu livre por mais 2 blocos (a busca do núcleo começa um pouco acima do ponto: nada de telhado por perto). */
    private static boolean headroom(ServerLevel sl, BlockPos feet) {
        return free(sl, feet.above(2)) && free(sl, feet.above(3));
    }

    private static boolean free(ServerLevel sl, BlockPos pos) {
        BlockState s = sl.getBlockState(pos);
        return s.getCollisionShape(sl, pos).isEmpty() && s.getFluidState().isEmpty() && !s.is(BlockTags.FIRE)
                && !s.is(Blocks.POWDER_SNOW) && !s.is(Blocks.COBWEB);
    }

    /** A pedra registrada nesse ponto ainda existe? (chunk descarregado conta como sim) */
    public static boolean stoneStillThere(MinecraftServer server, GlobalPos at) {
        ServerLevel l = server.getLevel(at.dimension());
        if (l == null) return false;
        if (!l.isLoaded(at.pos())) return true;
        return stoneNear(l, at.pos()) != null;
    }

    /** Pedra a até 2 blocos (o ponto registrado é o chão ao lado da pedra). */
    @Nullable
    public static TravelStoneBlockEntity stoneNear(ServerLevel l, BlockPos p) {
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -1, -2), p.offset(2, 1, 2))) {
            if (l.isLoaded(q) && l.getBlockEntity(q) instanceof TravelStoneBlockEntity s) return s;
        }
        return null;
    }

    // =====================================================================================
    //  Interação e efeitos
    // =====================================================================================
    /**
     * O jogador já tocou esta pedra (flag {@code travel.<id>} do núcleo)? Diferente de {@link Travel#discovered}: quem
     * entrou na cidade já conhece o destino (flag de visita), mas ainda não sintonizou a pedra — o 1º toque ainda dá o
     * efeito e conta para a missão "Ache a Pedra de Teleporte" do Diário.
     */
    public static boolean attuned(ServerPlayer sp, String placeId) {
        return placeId != null && !placeId.isEmpty() && MagicApi.get(sp).hasFlag("travel." + placeId);
    }

    /** Primeiro toque: grava a descoberta (o núcleo mostra o aviso "Novo destino" se for nova) e a pedra responde. */
    public static void attune(ServerLevel sl, TravelStoneBlockEntity be, ServerPlayer sp) {
        Travel.discover(sp, be.placeId());
        be.triggerAnim("flare", "flare");
        BlockPos pos = be.getBlockPos();
        Vec3 top = Vec3.atBottomCenterOf(pos).add(0, 2.2, 0);
        Vec3 chest = sp.position().add(0, 1.1, 0);
        DustParticleOptions dust = new DustParticleOptions(rgb(WAYSTONE_RGB), 1.1F);
        // Fio de luz da pedra até o jogador
        for (int i = 0; i <= 14; i++) {
            Vec3 p = top.lerp(chest, i / 14.0);
            sl.sendParticles(dust, p.x, p.y + Math.sin(i / 14.0 * Math.PI) * 0.6, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        sl.sendParticles(ParticleTypes.END_ROD, top.x, top.y, top.z, 18, 0.25, 0.4, 0.25, 0.05);
        sl.sendParticles(ParticleTypes.ENCHANT, chest.x, chest.y, chest.z, 30, 0.4, 0.6, 0.4, 0.6);
        sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4F, 0.8F);
        sl.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.5F, 1.6F);
        Component name = be.display() != null ? be.display() : Component.translatable("block.isekai_mahou.travel_stone");
        sp.displayClientMessage(Component.translatable("travel.isekai_mahou.stone.attuned", name).withColor(WAYSTONE_RGB), true);
    }

    /** Pedra erguida pelo jogador. */
    public static void raisedFx(ServerLevel sl, TravelStoneBlockEntity be, ServerPlayer sp) {
        be.triggerAnim("flare", "flare");
        flareFx(sl, be.getBlockPos(), true);
        Component name = be.display() != null ? be.display() : Component.translatable("block.isekai_mahou.travel_stone");
        sp.displayClientMessage(Component.translatable("travel.isekai_mahou.stone.raised", name).withColor(WAYSTONE_RGB), true);
    }

    /** Abre a tela "Viagem" da pedra e guarda a partida (para a pedra brilhar quando o jogador sumir). */
    public static void open(ServerLevel sl, TravelStoneBlockEntity be, ServerPlayer sp) {
        Travel.openMenu(sp, be.getBlockPos());
        DEPARTURES.put(sp.getUUID(), new Departure(sl.dimension(), be.getBlockPos().immutable(), now(sl.getServer())));
        sp.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 1.3F);
    }

    /** A pedra ainda está despertando (lugar não registrado). */
    public static void waking(ServerPlayer sp) {
        sp.displayClientMessage(Component.translatable("travel.isekai_mahou.stone.waking").withColor(WAYSTONE_RGB), true);
    }

    /** Coluna de luz + anel de pó + som (partida grave, chegada aguda). */
    public static void flareFx(ServerLevel sl, BlockPos pos, boolean arrival) {
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        DustParticleOptions dust = new DustParticleOptions(rgb(WAYSTONE_RGB), 1.3F);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            sl.sendParticles(dust, x + Math.cos(a) * 1.6, y + 0.15, z + Math.sin(a) * 1.6, 1, 0, 0.05, 0, 0);
        }
        sl.sendParticles(ParticleTypes.END_ROD, x, y + 2.2, z, 26, 0.12, 1.4, 0.12, 0.06);
        sl.sendParticles(arrival ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL, x, y + 1.2, z, 30, 0.4, 0.8, 0.4, 0.2);
        sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, arrival ? 1.5F : 0.9F);
        sl.playSound(null, pos, arrival ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.45F, 1.8F);
    }

    /** Viagem concluída (contador do núcleo): a pedra de partida e a de chegada brilham. */
    static void onCounter(MagicProgressEvent.Counter event) {
        if (!COUNTER_TRIPS.equals(event.key)) return;
        ServerPlayer sp = event.getPlayer();
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        Departure d = DEPARTURES.remove(sp.getUUID());
        // Viagem pelo menu (a recarga do menu acabou de começar): a pedra aberta antes não foi a partida
        boolean menuTrip = !sp.getAbilities().instabuild && Travel.menuCooldown(sp) > Travel.MENU_COOLDOWN_TICKS - 100;
        if (d != null && !menuTrip && now(server) - d.at() <= DEPARTURE_WINDOW) {
            ServerLevel from = server.getLevel(d.dim());
            if (from != null && from.isLoaded(d.pos()) && from.getBlockEntity(d.pos()) instanceof TravelStoneBlockEntity s) {
                s.flare(from, false);
            }
        }
        ServerLevel here = sp.serverLevel();
        TravelStoneBlockEntity s = null;
        Places.Place place = Places.at(here, sp.blockPosition());
        GlobalPos st = place == null ? null : Travel.stone(server, place.id());
        if (st != null && st.dimension().equals(here.dimension()) && st.pos().distSqr(sp.blockPosition()) <= 144) {
            s = stoneNear(here, st.pos());
        }
        // Lugar aninhado (o menor que contém o jogador não é o da pedra) ou chão ajustado: procura a pedra em volta
        if (s == null) s = stoneAround(here, sp.blockPosition(), 4);
        if (s != null) s.flare(here, true);
    }

    /** Pedra mais próxima num cubo de raio {@code r} (chegada: a pedra fica a poucos blocos dos pés). */
    @Nullable
    private static TravelStoneBlockEntity stoneAround(ServerLevel l, BlockPos c, int r) {
        TravelStoneBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 2, r))) {
            if (!l.isLoaded(q) || !(l.getBlockEntity(q) instanceof TravelStoneBlockEntity s)) continue;
            double d = q.distSqr(c);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DEPARTURES.remove(event.getEntity().getUUID());
    }

    static void clear() {
        DEPARTURES.clear();
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static Vector3f rgb(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }

    private TravelStones() {}
}
