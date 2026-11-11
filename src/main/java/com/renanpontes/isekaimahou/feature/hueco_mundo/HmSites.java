package com.renanpontes.isekaimahou.feature.hueco_mundo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.feature.hueco_mundo.boss.HmBoss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lugares fixos dos reinos, colocados UMA vez por mundo a partir dos moldes (.nbt) e guardados num SavedData do próprio
 * reino: o Mirante da Garganta (chegada do Mundo Oco), o Palácio da Noite Eterna (centro, dois chefes) e a Corte das
 * Fadas (chegada + arena da Rainha). Cada um vira um lugar do {@code Places}: hubs são destino da Viagem (zona segura),
 * palácio e corte são protegidos pela {@code BossArena} (registrado no init do módulo). Os chefes do lugar voltam um dia
 * de jogo depois de derrotados, quando alguém se aproxima.
 */
public final class HmSites {
    public static final String PALACE_PLACE = "hollow_palace";
    public static final String COURT_PLACE = "fey_court";
    public static final String LAIR_TYPE = "boss_lair";
    public static final long BOSS_RESPAWN_TICKS = 24000L;
    private static final String DATA_NAME = "isekai_mahou_hueco_mundo_sites";

    // Moldes: posição (no molde) do bloco de piso central e dos pontos importantes. Gerados por tools/gen/hueco_mundo.py.
    static final Vec3i HUB_FLOOR = new Vec3i(8, 2, 8);
    static final Vec3i HUB_ARRIVAL = new Vec3i(8, 3, 4);
    static final Vec3i PALACE_FLOOR = new Vec3i(40, 3, 40);
    static final Vec3i KING_SPOT = new Vec3i(40, 4, 19);
    static final Vec3i EMPRESS_SPOT = new Vec3i(40, 4, 59);
    static final Vec3i COURT_FLOOR = new Vec3i(30, 2, 30);
    static final Vec3i QUEEN_SPOT = new Vec3i(30, 3, 22);
    static final Vec3i COURT_ARRIVAL = new Vec3i(30, 3, 53);
    /** Raio das arenas dos chefes (a BossArena usa no mínimo 24). */
    public static final double KING_ARENA = 17, EMPRESS_ARENA = 17, QUEEN_ARENA = 20;

    public static final class SiteData extends SavedData {
        boolean hubBuilt, palaceBuilt;
        @Nullable BlockPos hubOrigin, palaceOrigin;
        final Map<String, Long> defeatedAt = new HashMap<>();

        static SiteData load(CompoundTag tag, HolderLookup.Provider registries) {
            SiteData d = new SiteData();
            d.hubBuilt = tag.getBoolean("hub_built");
            d.palaceBuilt = tag.getBoolean("palace_built");
            d.hubOrigin = NbtUtils.readBlockPos(tag, "hub_origin").orElse(null);
            d.palaceOrigin = NbtUtils.readBlockPos(tag, "palace_origin").orElse(null);
            CompoundTag def = tag.getCompound("defeated");
            for (String k : def.getAllKeys()) d.defeatedAt.put(k, def.getLong(k));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("hub_built", hubBuilt);
            tag.putBoolean("palace_built", palaceBuilt);
            if (hubOrigin != null) tag.put("hub_origin", NbtUtils.writeBlockPos(hubOrigin));
            if (palaceOrigin != null) tag.put("palace_origin", NbtUtils.writeBlockPos(palaceOrigin));
            CompoundTag def = new CompoundTag();
            defeatedAt.forEach(def::putLong);
            tag.put("defeated", def);
            return tag;
        }
    }

    private static final SavedData.Factory<SiteData> FACTORY = new SavedData.Factory<>(SiteData::new, SiteData::load, null);

    public static SiteData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server, HmRealm realm) {
        return server.getLevel(realm.key);
    }

    // ------------------------------------------------------------------ Construção

    /** Garante o hub (e, no Mundo Oco, o palácio) construídos; devolve a posição dos pés na chegada. */
    public static Vec3 ensureBuilt(ServerLevel level, HmRealm realm) {
        SiteData d = data(level);
        if (!d.hubBuilt || d.hubOrigin == null) {
            Vec3i floor = realm == HmRealm.HOLLOW ? HUB_FLOOR : COURT_FLOOR;
            int gy = groundY(level, realm.hubX, realm.hubZ, realm == HmRealm.HOLLOW ? 64 : 70);
            BlockPos origin = new BlockPos(realm.hubX - floor.getX(), gy - floor.getY(), realm.hubZ - floor.getZ());
            place(level, realm.hubTemplate, origin, floor.getY());
            d.hubOrigin = origin;
            d.hubBuilt = true;
            d.setDirty();
        }
        if (realm == HmRealm.HOLLOW && (!d.palaceBuilt || d.palaceOrigin == null)) {
            int gy = groundY(level, 0, 0, 66);
            BlockPos origin = new BlockPos(-PALACE_FLOOR.getX(), gy - PALACE_FLOOR.getY(), -PALACE_FLOOR.getZ());
            place(level, IsekaiMahou.id("hollow_palace"), origin, PALACE_FLOOR.getY());
            d.palaceOrigin = origin;
            d.palaceBuilt = true;
            d.setDirty();
        }
        registerPlaces(level, realm);
        return arrival(level, realm);
    }

    /**
     * Chão firme no ponto (gera o chunk), descendo por árvores (de madeira, de quartzo ou cogumelos gigantes) até o
     * terreno; limitado para o lugar não nascer no fundo de um vale nem no topo de uma duna.
     */
    private static int groundY(ServerLevel level, int x, int z, int fallback) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        for (int i = 0; i < 32 && y > level.getMinBuildHeight() + 4; i++) {
            BlockState s = level.getBlockState(p.setY(y));
            // Água conta como chão (o lugar fica sobre o lago, com a base de arenito embaixo).
            if (!s.getFluidState().isEmpty() || (!isTreePart(s) && !s.canBeReplaced())) break;
            y--;
        }
        if (y <= level.getMinBuildHeight() + 4) y = fallback;
        return Math.max(52, Math.min(110, y));
    }

    private static boolean isTreePart(BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(Blocks.QUARTZ_PILLAR) || s.is(Blocks.QUARTZ_BLOCK)
                || s.is(Blocks.SMOOTH_QUARTZ) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.SHROOMLIGHT) || s.is(Blocks.BONE_BLOCK)
                || s.is(HmRegistries.FEY_GLOWCAP.get()) || s.is(HmRegistries.HOLLOW_CRYSTAL.get());
    }

    /**
     * Coloca o molde: limpa o volume acima do piso (dunas e árvores que nasceram ali), coloca os blocos e firma a base
     * com colunas de arenito até o chão (o lugar nunca fica flutuando sobre um vale).
     */
    private static void place(ServerLevel level, net.minecraft.resources.ResourceLocation id, BlockPos origin, int floorY) {
        Optional<StructureTemplate> opt = level.getStructureManager().get(id);
        if (opt.isEmpty()) {
            IsekaiMahou.LOGGER.warn("Molde {} não encontrado; só uma plataforma de segurança será criada", id);
            safePlatform(level, origin.offset(8, floorY, 8));
            return;
        }
        StructureTemplate t = opt.get();
        Vec3i size = t.getSize();
        for (int cx = origin.getX() >> 4; cx <= (origin.getX() + size.getX()) >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + size.getZ()) >> 4; cz++) level.getChunk(cx, cz);
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState base = HmRegistries.HOLLOW_SANDSTONE.get().defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int y = floorY + 1; y < size.getY() + 6; y++) {
                    p.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, Block.UPDATE_CLIENTS);
                }
                // Base: desce até achar chão (máx. 40 blocos).
                for (int y = -1; y > -40; y--) {
                    p.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) break;
                    level.setBlock(p, base, Block.UPDATE_CLIENTS);
                }
            }
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(false).setFinalizeEntities(true);
        t.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void safePlatform(ServerLevel level, BlockPos center) {
        BlockState floor = HmRegistries.HOLLOW_SANDSTONE_BRICKS.get().defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(center.offset(dx, 0, dz), floor);
                level.setBlockAndUpdate(center.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(center.offset(dx, 2, dz), Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** Pés na chegada (o hub precisa estar construído). */
    public static Vec3 arrival(ServerLevel level, HmRealm realm) {
        SiteData d = data(level);
        Vec3i a = realm == HmRealm.HOLLOW ? HUB_ARRIVAL : COURT_ARRIVAL;
        if (d.hubOrigin == null) return new Vec3(realm.hubX + 0.5, 90, realm.hubZ + 0.5);
        BlockPos feet = d.hubOrigin.offset(a);
        ensureSafe(level, feet);
        return Vec3.atBottomCenterOf(feet);
    }

    /** Se alguém destruiu o chão da chegada, refaz 3×3 de tijolos e limpa o espaço do corpo. */
    private static void ensureSafe(ServerLevel level, BlockPos feet) {
        level.getChunkAt(feet);
        BlockPos below = feet.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            BlockState floor = HmRegistries.HOLLOW_SANDSTONE_BRICKS.get().defaultBlockState();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos q = below.offset(dx, 0, dz);
                    if (level.getBlockState(q).canBeReplaced()) level.setBlockAndUpdate(q, floor);
                }
            }
        }
        for (BlockPos q : new BlockPos[] {feet, feet.above()}) {
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) level.setBlockAndUpdate(q, Blocks.AIR.defaultBlockState());
        }
    }

    // ------------------------------------------------------------------ Places

    /** Registra (ou corrige) os lugares do reino. Só grava quando algo mudou. */
    public static void registerPlaces(ServerLevel level, HmRealm realm) {
        SiteData d = data(level);
        if (d.hubOrigin != null) {
            Vec3i a = realm == HmRealm.HOLLOW ? HUB_ARRIVAL : COURT_ARRIVAL;
            BlockPos feet = d.hubOrigin.offset(a);
            BoundingBox box = new BoundingBox(feet.getX() - 9, feet.getY() - 6, feet.getZ() - 9, feet.getX() + 9, feet.getY() + 12, feet.getZ() + 9);
            put(level, new Places.Place(realm.hubPlaceId, realm.hubName(), Travel.HUB_TYPE, "", realm.key, box, feet, true, realm.color));
            if (realm == HmRealm.FEY) {
                BlockPos o = d.hubOrigin;
                BoundingBox court = new BoundingBox(o.getX(), o.getY() - 4, o.getZ(), o.getX() + 60, o.getY() + 30, o.getZ() + 60);
                put(level, new Places.Place(COURT_PLACE, HmText.of("place.isekai_mahou.hueco_mundo", COURT_PLACE),
                        LAIR_TYPE, "", realm.key, court, o.offset(QUEEN_SPOT), false, HmFx.FEY_PINK));
            }
        }
        if (realm == HmRealm.HOLLOW && d.palaceOrigin != null) {
            BlockPos o = d.palaceOrigin;
            BoundingBox box = new BoundingBox(o.getX(), o.getY() - 4, o.getZ(), o.getX() + 80, o.getY() + 44, o.getZ() + 80);
            put(level, new Places.Place(PALACE_PLACE, HmText.of("place.isekai_mahou.hueco_mundo", PALACE_PLACE),
                    LAIR_TYPE, "", realm.key, box, o.offset(PALACE_FLOOR).above(), false, 0xD9DCE8));
        }
    }

    private static void put(ServerLevel level, Places.Place p) {
        Places.Place old = Places.get(level, p.id());
        if (old != null && old.dim().equals(p.dim()) && old.box().equals(p.box()) && old.center().equals(p.center())
                && old.type().equals(p.type()) && old.safeZone() == p.safeZone() && old.color() == p.color()) {
            return;
        }
        Places.register(level, p);
    }

    /** Servidor subiu: registra no Places os lugares já construídos (mundos de antes desta versão do registro). */
    public static void registerBuilt(MinecraftServer server) {
        for (HmRealm realm : HmRealm.values()) {
            ServerLevel level = level(server, realm);
            if (level != null && data(level).hubBuilt) registerPlaces(level, realm);
        }
    }

    // ------------------------------------------------------------------ Chefes dos lugares

    /** Chefe do lugar derrotado: volta depois de {@link #BOSS_RESPAWN_TICKS}. */
    public static void bossDefeated(ServerLevel level, String bossId) {
        SiteData d = data(level);
        d.defeatedAt.put(bossId, level.getGameTime());
        d.setDirty();
    }

    /** A cada 5 s em cada reino: alguém perto do palácio/corte → chama os chefes que faltam (sem duplicar). */
    public static void tick(ServerLevel level, HmRealm realm) {
        SiteData d = data(level);
        if (realm == HmRealm.HOLLOW && d.palaceOrigin != null) {
            BlockPos o = d.palaceOrigin;
            AABB zone = new AABB(o.getX() - 24, o.getY() - 16, o.getZ() - 24, o.getX() + 104, o.getY() + 60, o.getZ() + 104);
            if (anyoneIn(level, zone)) {
                // Os dois olham para o sul (para o portão por onde os jogadores chegam).
                ensureBoss(level, d, "hollow_king", HmRegistries.HOLLOW_KING, o.offset(KING_SPOT), KING_ARENA, 0F);
                ensureBoss(level, d, "sand_empress", HmRegistries.SAND_EMPRESS, o.offset(EMPRESS_SPOT), EMPRESS_ARENA, 0F);
            }
        } else if (realm == HmRealm.FEY && d.hubOrigin != null) {
            BlockPos o = d.hubOrigin;
            AABB zone = new AABB(o.getX() - 24, o.getY() - 16, o.getZ() - 24, o.getX() + 84, o.getY() + 50, o.getZ() + 84);
            if (anyoneIn(level, zone)) ensureBoss(level, d, "thorn_queen", HmRegistries.THORN_QUEEN, o.offset(QUEEN_SPOT), QUEEN_ARENA, 0F);
        }
    }

    private static boolean anyoneIn(ServerLevel level, AABB zone) {
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && zone.contains(p.position())) return true;
        }
        return false;
    }

    private static void ensureBoss(ServerLevel level, SiteData d, String id, Supplier<? extends EntityType<? extends HmBoss>> type,
                                   BlockPos spot, double arena, float yaw) {
        Long at = d.defeatedAt.get(id);
        if (at != null && level.getGameTime() - at < BOSS_RESPAWN_TICKS) return;
        if (!level.isLoaded(spot)) return;
        EntityType<? extends HmBoss> t = type.get();
        List<? extends HmBoss> present = level.getEntities(t, new AABB(spot).inflate(72), e -> e.isAlive());
        if (!present.isEmpty()) return;
        HmBoss boss = t.create(level);
        if (boss == null) return;
        boss.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0F);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
        boss.markSiteBoss(Vec3.atBottomCenterOf(spot), arena);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null);
        level.addFreshEntity(boss);
        if (at != null) {
            d.defeatedAt.remove(id);
            d.setDirty();
            level.playSound(null, spot, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7F, 0.6F);
            Component msg = Component.translatable("message.isekai_mahou.hueco_mundo.boss_returns", boss.getDisplayName()).withColor(boss.bossColor());
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(boss) < 96 * 96) p.displayClientMessage(msg, true);
            }
        }
    }

    /** Recolocar os moldes (comando de teste): limpa a marca e reconstrói na próxima chegada. */
    public static void resetBuilt(ServerLevel level) {
        SiteData d = data(level);
        d.hubBuilt = false;
        d.palaceBuilt = false;
        d.setDirty();
    }

    @Nullable
    public static BlockPos palaceOrigin(ServerLevel level) {
        return data(level).palaceOrigin;
    }

    private HmSites() {}
}
