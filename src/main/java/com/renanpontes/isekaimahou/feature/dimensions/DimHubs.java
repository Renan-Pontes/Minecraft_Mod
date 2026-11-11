package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.Optional;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Travel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/**
 * Hubs de chegada das dimensões (plataforma do Plano Elemental com o Altar da Convergência, salão do Abismo,
 * jardim de nuvens do Santuário e a arena da Fenda do Crepúsculo). Cada um é colocado UMA vez por mundo, a
 * partir do template .nbt, e marcado num SavedData da própria dimensão. Cada hub construído também vira um lugar
 * do {@code Places} (id {@code hub_<dimensão>}, tipo {@link Travel#HUB_TYPE}, centro = pés no ponto de chegada): letreiro
 * ao chegar e destino da Viagem rápida depois da primeira visita.
 */
public final class DimHubs {
    /** O Arauto do Crepúsculo volta para a arena um dia (24000 ticks) depois de ser derrotado. */
    public static final long HERALD_RESPAWN_TICKS = 24000L;
    /**
     * Versão do molde dos hubs. 2 = v1.0: a arena da Fenda ficou GRANDE (raio 43,5) para o Dragão Primordial; mundos
     * da v0.8 com a arena pequena recebem a nova (sem duplicar o Arauto) na próxima chegada, fora de uma luta.
     */
    public static final int HUB_VERSION = 2;
    private static final String DATA_NAME = "isekai_mahou_dimension_hub";

    public static final class HubData extends SavedData {
        boolean built;
        int version;
        long heraldDefeatedAt = -1L;

        static HubData load(CompoundTag tag, HolderLookup.Provider registries) {
            HubData d = new HubData();
            d.built = tag.getBoolean("built");
            d.version = tag.contains("version") ? tag.getInt("version") : 1;
            d.heraldDefeatedAt = tag.contains("herald_defeated_at") ? tag.getLong("herald_defeated_at") : -1L;
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("built", built);
            tag.putInt("version", version);
            tag.putLong("herald_defeated_at", heraldDefeatedAt);
            return tag;
        }
    }

    private static final SavedData.Factory<HubData> FACTORY = new SavedData.Factory<>(HubData::new, HubData::load, null);

    public static HubData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Garante o hub construído e um chão seguro no ponto de chegada. */
    public static void ensureHub(ServerLevel level, DimKind kind) {
        HubData d = data(level);
        if (!d.built) {
            build(level, kind, true);
            d.built = true;
            d.version = HUB_VERSION;
            d.setDirty();
        } else if (kind == DimKind.TWILIGHT && d.version < HUB_VERSION && !bossFightNear(level)) {
            // Mundo antigo: a arena pequena vira a arena grande (o molde novo cobre e limpa a antiga). Sem entidades:
            // o Arauto que já está lá continua sendo o único.
            if (build(level, kind, false)) {
                for (var p : level.players()) {
                    p.displayClientMessage(Component.translatable("message.isekai_mahou.dimensions.arena_expanded")
                            .withColor(DimKind.TWILIGHT.color), false);
                }
            }
            d.version = HUB_VERSION;
            d.setDirty();
        } else if (d.version < HUB_VERSION && kind != DimKind.TWILIGHT) {
            d.version = HUB_VERSION;
            d.setDirty();
        }
        ensureSafeArrival(level, kind);
        registerPlace(level, kind);
    }

    /**
     * Há um chefe lutando na arena agora (Dragão Primordial ou Arauto em combate)? Por id, sem depender dos módulos
     * de chefes: o molde não é trocado no meio de uma luta (o terreno temporário do dragão voltaria por cima do novo).
     */
    private static boolean bossFightNear(ServerLevel level) {
        AABB box = new AABB(DimKind.TWILIGHT.hubCenter).inflate(64);
        for (String id : new String[] {"primordial_dragon", "twilight_herald"}) {
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id(id));
            if (type.isEmpty()) continue;
            EntityType<?> t = type.get();
            boolean busy = !level.getEntitiesOfClass(Entity.class, box,
                    e -> e.getType() == t && e.isAlive() && (!(e instanceof Mob m) || m.getTarget() != null || "primordial_dragon".equals(id)))
                    .isEmpty();
            if (busy) return true;
        }
        return false;
    }

    /** Mundos que já tinham hubs (v0.8): registra no Places os hubs já construídos assim que o servidor sobe. */
    public static void registerBuiltHubs(MinecraftServer server) {
        for (DimKind kind : DimKind.values()) {
            ServerLevel level = server.getLevel(kind.key);
            if (level != null && data(level).built) registerPlace(level, kind);
        }
    }

    /**
     * Registra (ou corrige) o hub no Places. A caixa é a área protegida do hub; o centro, os pés no ponto de chegada
     * (é onde a Viagem deixa o jogador). A arena da Fenda não é zona segura (é o ringue do Arauto); os outros hubs
     * são (nada de monstros nascendo em cima de quem acabou de chegar). Só grava se algo mudou.
     */
    public static void registerPlace(ServerLevel level, DimKind kind) {
        BlockPos c = kind.hubCenter;
        BoundingBox box = new BoundingBox(c.getX() - kind.protectRadius, c.getY() - kind.protectHeight, c.getZ() - kind.protectRadius,
                c.getX() + kind.protectRadius, c.getY() + kind.protectHeight, c.getZ() + kind.protectRadius);
        BlockPos feet = kind.arrivalFeet();
        boolean safe = kind != DimKind.TWILIGHT;
        Places.Place old = Places.get(level, kind.hubPlaceId());
        if (old != null && old.dim().equals(kind.key) && old.box().equals(box) && old.center().equals(feet)
                && Travel.HUB_TYPE.equals(old.type()) && old.safeZone() == safe && old.color() == kind.color) {
            return;
        }
        Places.register(level, new Places.Place(kind.hubPlaceId(), kind.hubName(), Travel.HUB_TYPE, "", kind.key, box, feet,
                safe, kind.color));
    }

    /**
     * Coloca o template do hub. {@code withEntities} = também coloca as entidades do template (o Arauto na arena);
     * "/mahou dimension rebuild" passa false para não duplicar o chefe.
     */
    public static boolean build(ServerLevel level, DimKind kind, boolean withEntities) {
        Optional<StructureTemplate> opt = level.getStructureManager().get(kind.hubTemplate);
        if (opt.isEmpty()) {
            IsekaiMahou.LOGGER.warn("Template do hub {} não encontrado; só a plataforma de segurança será criada", kind.hubTemplate);
            return false;
        }
        StructureTemplate template = opt.get();
        BlockPos origin = kind.hubOrigin();
        Vec3i size = template.getSize();
        // Os chunks precisam existir antes de colocar blocos neles.
        for (int cx = origin.getX() >> 4; cx <= (origin.getX() + size.getX()) >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + size.getZ()) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(!withEntities).setFinalizeEntities(true);
        boolean placed = template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (placed && (kind == DimKind.ELEMENTAL || kind == DimKind.CELESTIAL)) openSky(level, kind, origin.getY() + size.getY());
        return placed;
    }

    /** Altura (acima do topo do template) e raio do céu aberto acima dos hubs das ilhas. */
    private static final int SKY_CLEAR_HEIGHT = 40, SKY_CLEAR_RADIUS = 10;

    /**
     * Os hubs do Plano e do Santuário ficam numa altura fixa; se uma ilha gerada nasceu exatamente ali, o domo
     * de ar do template vira uma caverna fechada. Abre um poço até o céu: um cilindro acima do domo onde tudo
     * que não é ar (terreno, água de lago, nuvens) é removido.
     */
    private static void openSky(ServerLevel level, DimKind kind, int fromY) {
        int top = Math.min(level.getMaxBuildHeight() - 1, fromY + SKY_CLEAR_HEIGHT);
        BlockPos c = kind.hubCenter;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -SKY_CLEAR_RADIUS; dx <= SKY_CLEAR_RADIUS; dx++) {
            for (int dz = -SKY_CLEAR_RADIUS; dz <= SKY_CLEAR_RADIUS; dz++) {
                if (dx * dx + dz * dz > SKY_CLEAR_RADIUS * SKY_CLEAR_RADIUS) continue;
                for (int y = fromY; y <= top; y++) {
                    p.set(c.getX() + dx, y, c.getZ() + dz);
                    // Sem atualizar vizinhos: um lago da ilha ao lado não deve despencar sobre o hub.
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Se alguém (ou algo) destruiu o chão da chegada, cria 3×3 de pedra da dimensão e limpa o espaço do corpo. */
    public static void ensureSafeArrival(ServerLevel level, DimKind kind) {
        BlockPos feet = BlockPos.containing(kind.arrival);
        level.getChunkAt(feet);
        BlockPos below = feet.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            BlockState floor = floorBlock(kind).defaultBlockState();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = below.offset(dx, 0, dz);
                    if (level.getBlockState(p).canBeReplaced()) level.setBlockAndUpdate(p, floor);
                }
            }
        }
        for (BlockPos p : new BlockPos[] {feet, feet.above()}) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
    }

    public static Block floorBlock(DimKind kind) {
        return switch (kind) {
            case ELEMENTAL -> DimRegistry.FLOATING_STONE_BRICKS.get();
            case ABYSS -> DimRegistry.ABYSSAL_STONE_BRICKS.get();
            case CELESTIAL -> DimRegistry.CELESTIAL_MARBLE_BRICKS.get();
            case TWILIGHT -> DimRegistry.TWILIGHT_STONE.get();
        };
    }

    // ------------------------------------------------------------------ Arauto do Crepúsculo
    public static void heraldDefeated(ServerLevel level) {
        HubData d = data(level);
        d.heraldDefeatedAt = level.getGameTime();
        d.setDirty();
    }

    /** Ao chegar na Fenda: se o Arauto foi derrotado há mais de um dia e não há outro na arena, ele volta. */
    public static void checkHerald(ServerLevel level) {
        HubData d = data(level);
        if (d.heraldDefeatedAt < 0 || level.getGameTime() - d.heraldDefeatedAt < HERALD_RESPAWN_TICKS) return;
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("twilight_herald"));
        if (type.isEmpty()) return;
        BlockPos center = DimKind.TWILIGHT.hubCenter.above();
        EntityType<?> herald = type.get();
        boolean present = !level.getEntitiesOfClass(Entity.class, new AABB(center).inflate(48),
                e -> e.getType() == herald && e.isAlive()).isEmpty();
        if (!present) {
            Entity e = herald.spawn(level, center, MobSpawnType.EVENT);
            if (e == null) return;
            if (e instanceof Mob mob) mob.setPersistenceRequired();
            level.playSound(null, center, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 0.7F);
            for (var p : level.players()) {
                p.displayClientMessage(Component.translatable("message.isekai_mahou.dimensions.herald_returns")
                        .withColor(DimKind.TWILIGHT.color), true);
            }
        }
        d.heraldDefeatedAt = -1L;
        d.setDirty();
    }

    private DimHubs() {}
}
