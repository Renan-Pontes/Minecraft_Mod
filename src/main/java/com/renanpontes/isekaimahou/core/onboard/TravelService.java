package com.renanpontes.isekaimahou.core.onboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.core.api.Travel.TravelResult;
import com.renanpontes.isekaimahou.core.event.PlaceEvent;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.joml.Vector3f;

/** Implementação de {@link Travel} (servidor). */
public final class TravelService {
    /** Contador: segundo do jogo em que a recarga do menu acaba. */
    static final String COUNTER_MENU_UNTIL = "travel.menu_until";
    /** Contador: viagens feitas (para missões/feitos). */
    public static final String COUNTER_TRIPS = "travel.count";

    private static final int STONE_BASE = 10, STONE_PER_BLOCKS = 50, STONE_MAX = 120, MENU_MULT = 3;
    /** Distância "virtual" de uma viagem entre dimensões, para o custo. */
    private static final int CROSS_DIM_DISTANCE = 2000;
    private static final int COMBAT_TICKS = 100;
    private static final int ALREADY_HERE_RADIUS = 12;
    private static final int SEARCH_RADIUS = 6;
    private static final long SESSION_TICKS = 5 * 60 * 20;
    private static final int DUST_RGB = 0x9F7CFF;

    private static final ResourceKey<Level> TRIAL_REALM = ResourceKey.create(Registries.DIMENSION, IsekaiMahou.id("trial_realm"));
    private static final Set<String> EXCLUDED = ConcurrentHashMap.newKeySet();
    static {
        EXCLUDED.add("hunting_ground");
        EXCLUDED.add("trial");
        EXCLUDED.add("arena");
    }

    /** Sessão aberta pela última lista enviada: pedra usada (null = menu) e quando. */
    private record Session(@Nullable GlobalPos stone, long openedAt) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    /** Hubs das dimensões do mod (posição dos pés de reserva, igual ao centro dos hubs do módulo dimensions). */
    enum Hub {
        ELEMENTAL("elemental_plane", 0x7FE3FF, 111),
        ABYSS("abyss", 0x8A4FC8, 65),
        CELESTIAL("celestial_sanctuary", 0xFFE6A0, 151),
        TWILIGHT("twilight_rift", 0xC06AA0, 65);

        final String path;
        final int color;
        final int fallbackY;
        final ResourceKey<Level> dim;

        Hub(String path, int color, int fallbackY) {
            this.path = path;
            this.color = color;
            this.fallbackY = fallbackY;
            this.dim = ResourceKey.create(Registries.DIMENSION, IsekaiMahou.id(path));
        }

        String placeId() {
            return "hub_" + path;
        }

        @Nullable
        static Hub byDim(ResourceKey<Level> dim) {
            for (Hub h : values()) if (h.dim.equals(dim)) return h;
            return null;
        }

        @Nullable
        static Hub byPlaceId(String id) {
            for (Hub h : values()) if (h.placeId().equals(id)) return h;
            return null;
        }
    }

    // =====================================================================================
    //  Descoberta
    // =====================================================================================
    public static boolean discovered(Player p, String placeId) {
        if (p == null || placeId == null) return false;
        MagicData d = p.level().isClientSide() ? ClientMagic.data() : MagicApi.get(p);
        if (d.hasFlag("visit.place." + placeId) || d.hasFlag("travel." + placeId)) return true;
        Hub hub = Hub.byPlaceId(placeId);
        return hub != null && visitedDim(d, hub);
    }

    private static boolean visitedDim(MagicData d, Hub hub) {
        return d.counter("visit." + IsekaiMahou.MODID + "." + hub.path) > 0 || d.hasFlag("dimensions.seen." + hub.path)
                || d.hasFlag("travel." + hub.placeId());
    }

    /** Grava {@code travel.<id>}; com {@code notify}, avisa (toast) se for nova e o jogador não estiver na entrada. */
    public static boolean discover(ServerPlayer p, String placeId, boolean notify) {
        if (p == null || p instanceof FakePlayer || placeId == null || placeId.isBlank()) return false;
        boolean known = discovered(p, placeId);
        MagicApi.get(p).setFlag("travel." + placeId, true);
        if (known) return false;
        if (notify) {
            Places.Place place = findPlace(p.getServer(), placeId);
            if (place != null) notifyNew(p, place);
        }
        return true;
    }

    private static void notifyNew(ServerPlayer p, Places.Place place) {
        if (Onboarding.quiet(p) || !travelable(place)) return;
        Rewards.toast(p, Component.translatable("travel.isekai_mahou.discovered"), place.name(),
                new ItemStack(isHub(place) ? Items.ENDER_EYE : Items.LODESTONE), place.color() != 0 ? place.color() : DUST_RGB);
    }

    /** 1ª visita a um lugar que aceita viagem: aviso discreto depois do letreiro do lugar. */
    static void onPlaceEnter(PlaceEvent.Enter event) {
        Places.Place place = event.getPlace();
        if (!event.isFirstVisit() || !travelable(place) || isHub(place)) return;
        if (isWaystone(place) && insideOtherPlace(event.getPlayer().serverLevel(), place)) return; // é a chegada do lugar
        OnboardTasks.later(event.getPlayer(), 70, "", p -> notifyNew(p, place));
    }

    /** A pedra está dentro de outro lugar viajável (cidade, vila...) — ela é a chegada dele, não um destino à parte. */
    private static boolean insideOtherPlace(ServerLevel level, Places.Place waystone) {
        for (Places.Place o : Places.all(level)) {
            if (o != waystone && !isWaystone(o) && !isHub(o) && travelable(o) && o.contains(waystone.center())) return true;
        }
        return false;
    }

    /** 1ª chegada a uma dimensão com hub: o hub vira destino. */
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        Hub hub = Hub.byDim(event.getTo());
        if (hub == null) return;
        MagicData d = MagicApi.get(sp);
        String flag = "travel." + hub.placeId();
        if (d.hasFlag(flag)) return;
        d.setFlag(flag, true);
        // Depois da cutscene de chegada do módulo dimensions
        OnboardTasks.later(sp, 200, "", p -> {
            Places.Place place = hubPlace(p.getServer(), hub);
            if (place != null) notifyNew(p, place);
        });
    }

    // =====================================================================================
    //  Destinos
    // =====================================================================================
    public static void excludeType(String type) {
        if (type != null && !type.isBlank()) EXCLUDED.add(type);
    }

    static boolean travelable(Places.Place place) {
        return place != null && !EXCLUDED.contains(place.type()) && !TRIAL_REALM.equals(place.dim());
    }

    public static boolean isHub(Places.Place place) {
        return place != null && (Travel.HUB_TYPE.equals(place.type()) || Hub.byPlaceId(place.id()) != null);
    }

    private static boolean isWaystone(Places.Place place) {
        return Travel.WAYSTONE_TYPE.equals(place.type());
    }

    /**
     * Lugar pelo id em qualquer dimensão (cada nível guarda os seus lugares); hubs sem registro viram um lugar sintético
     * no centro (0, chão do hub, 0).
     */
    @Nullable
    static Places.Place findPlace(@Nullable MinecraftServer server, String id) {
        if (server == null || id == null || id.isBlank()) return null;
        Places.Place p = Places.get(server.overworld(), id);
        if (p != null) return p;
        Hub hub = Hub.byPlaceId(id);
        if (hub != null) return hubPlace(server, hub);
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() == Level.OVERWORLD) continue;
            p = Places.get(level, id);
            if (p != null) return p;
        }
        return null;
    }

    /** Hub registrado no {@link Places} (id {@code hub_<dim>} ou tipo dimension_hub), ou um sintético. */
    @Nullable
    static Places.Place hubPlace(@Nullable MinecraftServer server, Hub hub) {
        if (server == null) return null;
        ServerLevel level = server.getLevel(hub.dim);
        if (level == null) return null; // módulo de dimensões ausente
        Places.Place p = Places.get(level, hub.placeId());
        if (p != null) return p;
        for (Places.Place other : Places.all(level)) {
            if (Travel.HUB_TYPE.equals(other.type())) return other;
        }
        BlockPos center = new BlockPos(0, hub.fallbackY, 0);
        return new Places.Place(hub.placeId(), Component.translatable("travel.isekai_mahou.hub." + hub.path), Travel.HUB_TYPE, "",
                hub.dim, new BoundingBox(-8, hub.fallbackY - 8, -8, 8, hub.fallbackY + 16, 8), center, false, hub.color);
    }

    private static boolean known(ServerPlayer sp, MagicData d, Places.Place place) {
        if (discovered(sp, place.id())) return true;
        Hub hub = Hub.byDim(place.dim());
        return isHub(place) && hub != null && visitedDim(d, hub);
    }

    public static List<Places.Place> destinations(ServerPlayer sp) {
        List<Places.Place> out = new ArrayList<>();
        MinecraftServer server = sp.getServer();
        if (server == null) return out;
        MagicData d = MagicApi.get(sp);
        for (ServerLevel level : server.getAllLevels()) {
            if (TRIAL_REALM.equals(level.dimension())) continue;
            for (Places.Place place : Places.all(level)) {
                if (travelable(place) && known(sp, d, place)) out.add(place);
            }
        }
        for (Hub hub : Hub.values()) {
            if (!visitedDim(d, hub)) continue;
            boolean listed = false;
            for (Places.Place p : out) {
                if (p.dim().equals(hub.dim) && isHub(p)) {
                    listed = true;
                    break;
                }
            }
            if (!listed) {
                Places.Place p = hubPlace(server, hub);
                if (p != null) out.add(p);
            }
        }
        // A pedra dentro de um lugar conhecido é a chegada dele, não um destino à parte
        List<Places.Place> all = List.copyOf(out);
        out.removeIf(w -> isWaystone(w) && all.stream().anyMatch(o -> o != w && !isWaystone(o) && !isHub(o)
                && o.dim().equals(w.dim()) && o.contains(w.center())));
        ResourceKey<Level> here = sp.level().dimension();
        BlockPos pos = sp.blockPosition();
        out.sort(Comparator.<Places.Place>comparingInt(p -> p.dim().equals(here) ? 0 : 1)
                .thenComparing(p -> p.dim().location().toString())
                .thenComparingDouble(p -> p.dim().equals(here) ? horizontalDist(pos, p.center()) : 0)
                .thenComparing(p -> p.name().getString()));
        return out;
    }

    private static double horizontalDist(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // =====================================================================================
    //  Custo e regras
    // =====================================================================================
    public static int cost(ServerPlayer sp, Places.Place to, boolean fromStone) {
        if (sp.getAbilities().instabuild) return 0;
        double dist = to.dim().equals(sp.level().dimension()) ? horizontalDist(sp.blockPosition(), to.center()) : CROSS_DIM_DISTANCE;
        int base = Math.min(STONE_MAX, STONE_BASE + (int) (dist / STONE_PER_BLOCKS));
        if (!fromStone) base *= MENU_MULT;
        // Nunca mais que a barra cheia: com o recurso cheio sempre dá para ir
        return Math.max(1, Math.min(base, MagicApi.get(sp).maxMana()));
    }

    public static TravelResult check(ServerPlayer sp, Places.Place to, boolean fromStone) {
        if (sp == null || to == null) return TravelResult.UNKNOWN_PLACE;
        MagicData d = MagicApi.get(sp);
        if (sp instanceof FakePlayer || d.path() == MagicPath.NONE || sp.isDeadOrDying() || sp.isSleeping()
                || TRIAL_REALM.equals(sp.level().dimension())) return TravelResult.BLOCKED;
        if (!travelable(to)) return TravelResult.UNKNOWN_PLACE;
        if (!known(sp, d, to)) return TravelResult.NOT_DISCOVERED;
        boolean sameDim = to.dim().equals(sp.level().dimension());
        if (!sameDim && !isHub(to)) return TravelResult.OTHER_DIMENSION;
        if (sameDim && (to.contains(sp.blockPosition()) || horizontalDist(sp.blockPosition(), to.center()) < ALREADY_HERE_RADIUS)) {
            return TravelResult.ALREADY_HERE;
        }
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && !fromStone && menuCooldown(sp) > 0) return TravelResult.COOLDOWN;
        if (!creative && sp.getLastHurtByMob() != null && sp.tickCount - sp.getLastHurtByMobTimestamp() < COMBAT_TICKS) {
            return TravelResult.IN_COMBAT;
        }
        if (!creative && d.mana() < cost(sp, to, fromStone)) return TravelResult.NO_RESOURCE;
        return TravelResult.OK;
    }

    public static int menuCooldown(ServerPlayer sp) {
        MinecraftServer server = sp.getServer();
        if (server == null) return 0;
        int until = MagicApi.get(sp).counter(COUNTER_MENU_UNTIL);
        int left = until - nowSeconds(server);
        return left > 0 ? left * 20 : 0;
    }

    public static void resetCooldown(ServerPlayer sp) {
        int cur = MagicApi.get(sp).counter(COUNTER_MENU_UNTIL);
        if (cur != 0) MagicApi.addCounter(sp, COUNTER_MENU_UNTIL, -cur);
    }

    private static void startCooldown(ServerPlayer sp) {
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        int until = nowSeconds(server) + Travel.MENU_COOLDOWN_TICKS / 20;
        MagicApi.addCounter(sp, COUNTER_MENU_UNTIL, until - MagicApi.get(sp).counter(COUNTER_MENU_UNTIL));
    }

    private static int nowSeconds(MinecraftServer server) {
        return (int) Math.min(Integer.MAX_VALUE / 2, server.overworld().getGameTime() / 20L);
    }

    // =====================================================================================
    //  Viagem
    // =====================================================================================
    public static TravelResult travel(ServerPlayer sp, String placeId, boolean fromStone) {
        MinecraftServer server = sp.getServer();
        Places.Place place = findPlace(server, placeId);
        TravelResult r = place == null ? TravelResult.UNKNOWN_PLACE : check(sp, place, fromStone);
        ServerLevel target = place == null || server == null ? null : server.getLevel(place.dim());
        if (r.success() && target == null) r = TravelResult.UNKNOWN_PLACE;
        BlockPos feet = null;
        if (r.success()) {
            feet = arrival(target, place);
            if (feet == null) r = TravelResult.NO_SAFE_SPOT;
        }
        int cost = r.success() ? cost(sp, place, fromStone) : 0;
        if (r.success() && !sp.getAbilities().instabuild && !MagicApi.get(sp).consumeMana(cost)) r = TravelResult.NO_RESOURCE;
        if (!r.success()) {
            fail(sp, r, place, fromStone);
            return r;
        }

        int rgb = place.color() != 0 ? place.color() : DUST_RGB;
        departFx(sp, rgb);
        sp.teleportTo(target, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, sp.getYRot(), sp.getXRot());
        if (sp.level() != target) {
            // Outro mod cancelou a troca de dimensão: nada foi gasto
            if (!sp.getAbilities().instabuild) MagicApi.get(sp).addMana(cost);
            fail(sp, TravelResult.BLOCKED, place, fromStone);
            return TravelResult.BLOCKED;
        }
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hurtMarked = true;
        sp.resetFallDistance();
        // Rede de segurança contra quedas se o chão escolhido sumir na hora
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
        arriveFx(sp, rgb);

        if (!fromStone && !sp.getAbilities().instabuild) startCooldown(sp);
        MagicApi.addCounter(sp, COUNTER_TRIPS, 1);
        sp.displayClientMessage(Component.translatable("travel.isekai_mahou.arrived", place.name()).withColor(rgb), true);
        return TravelResult.OK;
    }

    private static void fail(ServerPlayer sp, TravelResult r, @Nullable Places.Place place, boolean fromStone) {
        Component msg = switch (r) {
            case NO_RESOURCE -> place == null ? r.message() : Component.translatable("travel.isekai_mahou.result.no_resource_detail",
                    MagicApi.resourceName(sp), (int) MagicApi.get(sp).mana(), cost(sp, place, fromStone));
            case COOLDOWN -> Component.translatable("travel.isekai_mahou.result.cooldown_detail", formatTime(menuCooldown(sp)));
            default -> r.message();
        };
        sp.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
        sp.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
    }

    /** "m:ss" a partir de ticks. */
    public static String formatTime(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /**
     * Pés no destino: pedra registrada do lugar → pedra solta dentro da área → centro; sempre num chão seguro por
     * perto. Hub sem chão nenhum ganha uma plataforma. Null = nenhum lugar seguro.
     */
    @Nullable
    static BlockPos arrival(ServerLevel level, Places.Place place) {
        BlockPos near = place.center();
        GlobalPos stone = level.getServer() == null ? null : TravelData.of(level.getServer()).get(place.id());
        if (stone != null && stone.dimension().equals(level.dimension())) {
            near = stone.pos();
        } else if (!isWaystone(place)) {
            Places.Place best = null;
            for (Places.Place w : Places.all(level)) {
                if (!isWaystone(w) || !place.contains(w.center())) continue;
                if (best == null || w.center().distSqr(place.center()) < best.center().distSqr(place.center())) best = w;
            }
            if (best != null) near = best.center();
        }
        // Hub sintético: o centro já é o chão do hub (o módulo dimensions o gera na 1ª visita e o protege); a busca
        // perto dessa altura vem antes do topo da coluna (o Abismo tem teto: o "topo" seria a rocha de cima)
        BlockPos spot = SafeSpot.find(level, near, SEARCH_RADIUS);
        if (spot == null && isHub(place)) spot = SafeSpot.platform(level, near);
        return spot;
    }

    private static void departFx(ServerPlayer sp, int rgb) {
        ServerLevel level = sp.serverLevel();
        double x = sp.getX(), y = sp.getY(), z = sp.getZ();
        DustParticleOptions dust = new DustParticleOptions(rgbVec(rgb), 1.4F);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            level.sendParticles(dust, x + Math.cos(a) * 1.1, y + 0.1, z + Math.sin(a) * 1.1, 2, 0.02, 0.6, 0.02, 0.0);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1.0, z, 70, 0.4, 0.9, 0.4, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.5, z, 20, 0.2, 1.2, 0.2, 0.08);
        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.75F);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        PacketDistributor.sendToPlayer(sp, new OnboardPayloads.TravelFx(rgb, false));
    }

    private static void arriveFx(ServerPlayer sp, int rgb) {
        ServerLevel level = sp.serverLevel();
        double x = sp.getX(), y = sp.getY(), z = sp.getZ();
        DustParticleOptions dust = new DustParticleOptions(rgbVec(rgb), 1.2F);
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            level.sendParticles(dust, x + Math.cos(a) * 1.6, y + 0.05, z + Math.sin(a) * 1.6, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.PORTAL, x, y + 1.0, z, 80, 0.5, 1.0, 0.5, 0.6);
        level.sendParticles(ParticleTypes.FLASH, x, y + 1.0, z, 1, 0, 0, 0, 0);
        level.playSound(null, x, y, z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.0F);
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.6F);
        PacketDistributor.sendToPlayer(sp, new OnboardPayloads.TravelFx(rgb, true));
    }

    private static Vector3f rgbVec(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }

    // =====================================================================================
    //  Lista para o cliente e pedidos
    // =====================================================================================
    public static void openMenu(ServerPlayer sp, @Nullable BlockPos stone) {
        if (sp == null || sp instanceof FakePlayer) return;
        boolean fromStone = stone != null;
        SESSIONS.put(sp.getUUID(), new Session(fromStone ? GlobalPos.of(sp.level().dimension(), stone.immutable()) : null,
                sp.level().getGameTime()));
        MinecraftServer server = sp.getServer();
        List<OnboardPayloads.TravelDest> list = new ArrayList<>();
        for (Places.Place p : destinations(sp)) {
            GlobalPos st = server == null ? null : TravelData.of(server).get(p.id());
            BlockPos pos = st != null && st.dimension().equals(p.dim()) ? st.pos() : p.center();
            list.add(new OnboardPayloads.TravelDest(p.id(), p.name(), p.type(), p.faction(), p.dim(), pos,
                    p.color() != 0 ? p.color() : DUST_RGB, cost(sp, p, fromStone), check(sp, p, fromStone).ordinal(), isHub(p)));
        }
        PacketDistributor.sendToPlayer(sp, new OnboardPayloads.TravelMenu(fromStone, Optional.ofNullable(stone), menuCooldown(sp),
                Travel.MENU_COOLDOWN_TICKS, MagicApi.resourceName(sp), MagicApi.resourceColor(sp) & 0xFFFFFF, list));
    }

    /** Pedido do cliente: vale como "de pedra" só se a sessão é de pedra, recente, e o jogador ainda está nela. */
    static void requestTravel(ServerPlayer sp, String placeId) {
        Session s = SESSIONS.get(sp.getUUID());
        boolean fromStone = s != null && s.stone() != null && stoneValid(sp, s);
        TravelResult r = travel(sp, placeId, fromStone);
        if (r.success()) SESSIONS.remove(sp.getUUID());
        PacketDistributor.sendToPlayer(sp, new OnboardPayloads.TravelOutcome(placeId, r.ordinal()));
    }

    private static boolean stoneValid(ServerPlayer sp, Session s) {
        GlobalPos st = s.stone();
        if (st == null || !st.dimension().equals(sp.level().dimension())) return false;
        if (sp.level().getGameTime() - s.openedAt() > SESSION_TICKS) return false;
        if (sp.distanceToSqr(Vec3.atCenterOf(st.pos())) > Travel.STONE_REACH * Travel.STONE_REACH) return false;
        // A pedra ainda está lá (se o módulo travel existir)
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(IsekaiMahou.id("travel_stone"));
        return block.isEmpty() || sp.level().getBlockState(st.pos()).is(block.get());
    }

    public static void registerStone(ServerLevel level, String placeId, BlockPos pos) {
        if (level == null || placeId == null || placeId.isBlank() || pos == null) return;
        TravelData.of(level.getServer()).put(placeId, GlobalPos.of(level.dimension(), pos.immutable()));
    }

    public static void unregisterStone(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        TravelData.of(level.getServer()).removeAt(GlobalPos.of(level.dimension(), pos.immutable()));
    }

    @Nullable
    public static GlobalPos stone(MinecraftServer server, String placeId) {
        return server == null || placeId == null ? null : TravelData.of(server).get(placeId);
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    static void clear() {
        SESSIONS.clear();
    }

    private TravelService() {}
}
