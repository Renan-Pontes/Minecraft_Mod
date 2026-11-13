package com.renanpontes.isekaimahou.feature.las_noches;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.event.PlaceEvent;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmSites;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmText;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Ciclo de Las Noches no servidor: começa/continua a construção (com alguém no Mundo Oco), selos da torre (agachar no
 * selo negro sobe/desce do terraço), gatilho das salas das Espada, a Sala do Trono (marca do Diário), guardas, a
 * chegada (cutscene na 1ª entrada na cúpula) e as derrotas das Espada.
 */
public final class LnEvents {
    public static final String DOME_PLACE = "las_noches_dome";
    public static final String THRONE_FLAG = "las_noches.throne";
    private static final Map<UUID, Long> PAD_CD = new HashMap<>(), HINT_CD = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(LnEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(LnEvents::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(LnEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(LnEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(LnEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(LnEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(LnEvents::onPlaceEnter);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> onStarted(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            PAD_CD.clear();
            HINT_CD.clear();
            LnEspada.clearPending();
        });
        // A área do lugar "hollow_palace" passa a ser Las Noches inteira; refazer o reino refaz Las Noches.
        HmSites.palaceBoxExtension = LnEvents::complexBoxFromPalace;
        HmSites.RESET_LISTENERS.add(LnEvents::reset);
    }

    /** Centro da cúpula a partir da origem do molde do palácio (piso em y+3, centro em +40). */
    public static BlockPos centerFromPalace(BlockPos origin) {
        return new BlockPos(origin.getX() + 40, origin.getY() + 3, origin.getZ() + 40 + LnLayout.OFFSET_Z);
    }

    private static BoundingBox complexBoxFromPalace(BlockPos origin) {
        BlockPos c = centerFromPalace(origin);
        return new BoundingBox(c.getX() + LnLayout.MIN_X, c.getY() - 4, c.getZ() + LnLayout.MIN_Z,
                c.getX() + LnLayout.MAX_X, c.getY() + LnLayout.MAX_Y, c.getZ() + LnLayout.MAX_Z);
    }

    private static boolean isHollow(ServerLevel level) {
        return level.dimension().equals(HmRealm.HOLLOW.key);
    }

    // ------------------------------------------------------------------ Tick

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !isHollow(level) || level.players().isEmpty()) return;
        BlockPos origin = HmSites.palaceOrigin(level);
        if (origin == null) return;
        LnData d = LnData.get(level);
        BlockPos c = centerFromPalace(origin);
        if (d.stage == LnData.NONE || d.version < LnData.LAYOUT_VERSION || !c.equals(d.center)) startBuild(level, d, c);
        if (d.stage == LnData.BUILDING) {
            if (LnBuilder.step(level, d)) {
                finishBuild(level, d);
            } else if (level.getGameTime() % 100 == 0) {
                int pct = Math.round(LnBuilder.progress(d) * 100);
                Component msg = Component.translatable("message.isekai_mahou.las_noches.building", pct).withColor(0xD9DCE8);
                for (ServerPlayer p : level.players()) if (p.distanceToSqr(Vec3.atCenterOf(c)) < 260 * 260) p.displayClientMessage(msg, true);
            }
            return;
        }
        if (!d.built()) return;
        long t = level.getGameTime();
        if (t % 10 == 0) for (ServerPlayer p : level.players()) pads(level, c, p);
        if (t % 20 == 5) for (ServerPlayer p : level.players()) rooms(level, d, c, p);
        if (t % 200 == 13 && anyoneInside(level, c)) LnGuards.tick(level, c);
    }

    private static void startBuild(ServerLevel level, LnData d, BlockPos c) {
        d.center = c;
        d.stage = LnData.BUILDING;
        d.version = LnData.LAYOUT_VERSION;
        d.chunkIndex = 0;
        d.columnIndex = 0;
        d.setDirty();
        IsekaiMahou.LOGGER.info("Las Noches: construindo em {} ({} chunks)", c, LnBuilder.chunks(c).size());
    }

    private static void finishBuild(ServerLevel level, LnData d) {
        d.stage = LnData.DONE;
        d.setDirty();
        registerPlaces(level, d);
        HmSites.registerPlaces(level, HmRealm.HOLLOW);
        for (ServerPlayer p : level.players()) LnNet.send(p, d);
        Component msg = Component.translatable("message.isekai_mahou.las_noches.built").withColor(0xE8F0FF);
        for (ServerPlayer p : level.players()) p.displayClientMessage(msg, true);
        IsekaiMahou.LOGGER.info("Las Noches: pronta em {}", d.center);
    }

    private static void reset(ServerLevel level) {
        if (!isHollow(level)) return;
        LnData d = LnData.get(level);
        d.stage = LnData.NONE;
        d.chunkIndex = 0;
        d.columnIndex = 0;
        d.setDirty();
    }

    /** Força reconstruir (comando). */
    static void rebuild(ServerLevel level) {
        reset(level);
    }

    /** A cúpula vira um lugar (protegido, alvo do Diário). Só grava quando muda. */
    static void registerPlaces(ServerLevel level, LnData d) {
        if (d.center == null) return;
        BlockPos c = d.center;
        BoundingBox box = new BoundingBox(c.getX() - LnLayout.R, c.getY() - 2, c.getZ() - LnLayout.R,
                c.getX() + LnLayout.R, c.getY() + LnLayout.MAX_Y, c.getZ() + LnLayout.R);
        BlockPos center = c.offset(0, 1, 12);
        Places.Place old = Places.get(level, DOME_PLACE);
        if (old != null && old.box().equals(box) && old.center().equals(center) && old.dim().equals(level.dimension())) return;
        Places.register(level, new Places.Place(DOME_PLACE, HmText.of("place.isekai_mahou.hueco_mundo", DOME_PLACE), HmSites.LAIR_TYPE, "",
                level.dimension(), box, center, false, 0xE8F0FF));
    }

    private static void onStarted(MinecraftServer server) {
        ServerLevel level = server.getLevel(HmRealm.HOLLOW.key);
        if (level == null) return;
        LnData d = LnData.get(level);
        if (d.built()) registerPlaces(level, d);
    }

    private static boolean anyoneInside(ServerLevel level, BlockPos c) {
        var box = LnGuards.complexBox(c);
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && box.contains(p.position())) return true;
        return false;
    }

    // ------------------------------------------------------------------ Selos da torre

    private static void pads(ServerLevel level, BlockPos c, ServerPlayer p) {
        if (p.isSpectator()) return;
        double rx = p.getX() - c.getX() - 0.5, ry = p.getY() - c.getY(), rz = p.getZ() - c.getZ() - 0.5;
        boolean low = Math.abs(ry - LnLayout.PAD_LOW.getY()) < 1.2, high = Math.abs(ry - LnLayout.PAD_HIGH.getY()) < 1.2;
        if ((!low && !high) || rx * rx + rz * rz > 1.5 * 1.5) return;
        long now = level.getGameTime();
        if (!p.isShiftKeyDown()) {
            Long h = HINT_CD.get(p.getUUID());
            if (h == null || now - h > 80) {
                HINT_CD.put(p.getUUID(), now);
                p.displayClientMessage(Component.translatable(low ? "message.isekai_mahou.las_noches.pad_up" : "message.isekai_mahou.las_noches.pad_down")
                        .withColor(0xB8C8E8), true);
            }
            return;
        }
        Long cd = PAD_CD.get(p.getUUID());
        if (cd != null && now - cd < 40) return;
        PAD_CD.put(p.getUUID(), now);
        BlockPos to = c.offset(low ? LnLayout.EXIT_HIGH : LnLayout.EXIT_LOW);
        Vec3 from = p.position();
        sonido(level, from);
        p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0F, p.getXRot());
        sonido(level, p.position());
    }

    /** Passo rápido (Sonído): estalo seco, rastro branco e poeira. */
    private static void sonido(ServerLevel level, Vec3 at) {
        level.sendParticles(HmFx.dust(0xF4F6FF, 1.4F), at.x, at.y + 1, at.z, 18, 0.3, 0.7, 0.3, 0);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 8, 0.4, 0.05, 0.4, 0.04);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8F, 1.8F);
    }

    // ------------------------------------------------------------------ Salas

    private static void rooms(ServerLevel level, LnData d, BlockPos c, ServerPlayer p) {
        if (p.isSpectator()) return;
        double rx = p.getX() - c.getX(), ry = p.getY() - c.getY(), rz = p.getZ() - c.getZ();
        for (LnLayout.Chamber ch : LnLayout.CHAMBERS) {
            if (ch.contains(rx, ry, rz)) {
                LnEspada.onChamberEntered(level, d, ch, p);
                return;
            }
        }
        LnLayout.Hall a = LnLayout.AIZEN;
        if (rx > a.x0() && rx < a.x1() + 1 && rz > a.z0() && rz < a.z1() + 1 && ry >= 0 && ry < a.h()) {
            MagicData md = MagicApi.get(p);
            if (!md.hasFlag(THRONE_FLAG)) {
                md.setFlag(THRONE_FLAG, true);
                md.markDirty();
                p.displayClientMessage(Component.translatable("message.isekai_mahou.las_noches.throne").withColor(0xE8F0FF), false);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 1.0F, 0.5F);
            }
        }
    }

    // ------------------------------------------------------------------ Jogadores e mortes

    private static void onPlaceEnter(PlaceEvent.Enter e) {
        if (!DOME_PLACE.equals(e.getPlace().id()) || !e.isFirstVisit()) return;
        ServerPlayer sp = e.getPlayer();
        Cutscenes.play(sp, "portal", sp, Component.translatable("title.isekai_mahou.las_noches"),
                Component.translatable("title.isekai_mahou.las_noches.subtitle"), 0xE8F0FF, 90, false);
    }

    private static void sendInfo(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        if (!isHollow(level)) return;
        LnNet.send(sp, LnData.get(level));
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sendInfo(sp);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sendInfo(sp);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sendInfo(sp);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        PAD_CD.remove(id);
        HINT_CD.remove(id);
        LnEspada.forget(id);
    }

    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level) || !isHollow(level)) return;
        if (LnEspada.chamber(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getEntity().getType()).getPath()) == null) return;
        LnEspada.onDeath(level, LnData.get(level), e.getEntity());
    }

    private LnEvents() {}
}
