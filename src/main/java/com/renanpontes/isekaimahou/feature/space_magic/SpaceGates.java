package com.renanpontes.isekaimahou.feature.space_magic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.feature.space_magic.entity.SpaceGateEntity;
import com.renanpontes.isekaimahou.feature.space_magic.network.GateChoicePayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Abrir Portal: escolhe o destino (dimensões que o jogador JÁ visitou pelo caminho normal — contador
 * {@code visit.<ns>.<path>} do núcleo), acha o chão seguro do outro lado (hub do {@link Places} → último chão firme do
 * jogador naquela dimensão → ponto de nascimento/plataforma do End → busca perto da origem), abre os dois portais e
 * leva quem atravessa (jogadores e os companheiros deles). Os teleportes acontecem no FIM do tick do servidor.
 */
public final class SpaceGates {
    /** Duração de cada portal: 30 s. */
    public static final int GATE_LIFE = 30 * 20;
    /** Tempo para escolher o destino na tela. */
    private static final int CHOICE_TICKS = 30 * 20;
    /** Dimensões que nunca são destino (provas e o mundo interior são "fora do mapa"). */
    /** Mantém os dois lados carregados enquanto o portal existe (senão o portal de volta sumiria com o chunk). */
    private static final TicketType<ChunkPos> GATE_TICKET = TicketType.create("isekai_mahou_space_gate",
            Comparator.comparingLong(ChunkPos::toLong), GATE_LIFE + 40);
    private static final Set<ResourceLocation> EXCLUDED = Set.of(IsekaiMahou.id("trial_realm"), IsekaiMahou.id("inner_world"));

    private record Pending(ResourceKey<Level> from, Vec3 pos, float yaw, int color, long expires, List<ResourceKey<Level>> options) {}

    private record Move(Entity entity, ResourceKey<Level> dim, Vec3 pos, float yaw) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> ARRIVED = new HashMap<>();
    private static final Deque<Move> QUEUE = new ArrayDeque<>();

    // ------------------------------------------------------------------ destinos
    /** Destinos possíveis agora (outras dimensões já visitadas; o Overworld sempre vale). */
    public static List<ResourceKey<Level>> destinations(ServerPlayer sp) {
        List<ResourceKey<Level>> out = new ArrayList<>();
        MinecraftServer server = sp.getServer();
        if (server == null) return out;
        MagicData d = MagicApi.get(sp);
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> key = level.dimension();
            if (key == sp.level().dimension() || EXCLUDED.contains(key.location())) continue;
            boolean visited = key == Level.OVERWORLD || d.counter(visitKey(key)) > 0;
            if (visited) out.add(key);
        }
        out.sort((a, b) -> a == Level.OVERWORLD ? -1 : b == Level.OVERWORLD ? 1 : a.location().compareTo(b.location()));
        return out;
    }

    public static String visitKey(ResourceKey<Level> dim) {
        return "visit." + dim.location().getNamespace() + "." + dim.location().getPath();
    }

    /** Nome legível da dimensão (traduções do módulo, com o id como reserva). */
    public static Component dimName(ResourceLocation dim) {
        String path = dim.getPath().replace('_', ' ');
        String fallback = path.isEmpty() ? dim.toString() : Character.toUpperCase(path.charAt(0)) + path.substring(1);
        return Component.translatableWithFallback("space_magic.isekai_mahou.dim." + dim.getNamespace() + "." + dim.getPath(), fallback);
    }

    /** Pode abrir portal aqui? (não dentro de uma arena de chefe selada nem numa dimensão "fora do mapa") */
    public static boolean blockedHere(ServerPlayer sp) {
        return EXCLUDED.contains(sp.level().dimension().location()) || BossArena.at(sp.level(), sp.position()) != null;
    }

    // ------------------------------------------------------------------ pedido (conjuração) e escolha
    /**
     * Conjuração do glifo: abre direto se só houver um destino, senão manda a tela de escolha ao jogador.
     * @return false se não deu (a mensagem já foi enviada)
     */
    public static boolean request(ServerPlayer sp, Vec3 gatePos, float yaw, int color) {
        if (blockedHere(sp)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.space_magic.gate_sealed").withStyle(ChatFormatting.RED), true);
            return false;
        }
        List<ResourceKey<Level>> options = destinations(sp);
        if (options.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.space_magic.gate_none").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Pending p = new Pending(sp.level().dimension(), gatePos, yaw, color, sp.server.getTickCount() + CHOICE_TICKS, options);
        if (options.size() == 1) return open(sp, p, options.get(0));
        PENDING.put(sp.getUUID(), p);
        List<String> ids = new ArrayList<>();
        for (ResourceKey<Level> k : options) ids.add(k.location().toString());
        PacketDistributor.sendToPlayer(sp, new GateChoicePayload(ids, color));
        return true;
    }

    /** Resposta da tela (texto vazio = cancelou). */
    public static void choose(ServerPlayer sp, String dimId) {
        Pending p = PENDING.remove(sp.getUUID());
        if (p == null || dimId.isEmpty()) return;
        if (sp.server.getTickCount() > p.expires() || sp.level().dimension() != p.from() || sp.position().distanceTo(p.pos()) > 24) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.space_magic.gate_expired").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(dimId);
        if (id == null) return;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        if (!p.options().contains(key)) return;
        open(sp, p, key);
    }

    /** Abre o par de portais (aqui ↔ destino). */
    private static boolean open(ServerPlayer sp, Pending p, ResourceKey<Level> destKey) {
        MinecraftServer server = sp.server;
        ServerLevel here = server.getLevel(p.from());
        ServerLevel there = server.getLevel(destKey);
        if (here == null || there == null) return false;
        Vec3 dest = arrival(sp, there);
        if (dest == null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.space_magic.gate_no_ground").withStyle(ChatFormatting.RED), true);
            return false;
        }
        Vec3 origin = groundNear(here, p.pos());
        float yaw = p.yaw();
        SpaceGateEntity a = SpaceGateEntity.create(here, origin, yaw, destKey, dest, yaw, sp.getUUID(), p.color(), GATE_LIFE, false);
        SpaceGateEntity b = SpaceGateEntity.create(there, dest, yaw, p.from(), origin, yaw + 180F, sp.getUUID(), p.color(), GATE_LIFE, true);
        ChunkPos ca = new ChunkPos(BlockPos.containing(origin)), cb = new ChunkPos(BlockPos.containing(dest));
        here.getChunkSource().addRegionTicket(GATE_TICKET, ca, 2, ca);
        there.getChunkSource().addRegionTicket(GATE_TICKET, cb, 2, cb);
        here.addFreshEntity(a);
        there.addFreshEntity(b);
        here.playSound(null, origin.x, origin.y + 1, origin.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.6F, 1.6F);
        here.playSound(null, origin.x, origin.y + 1, origin.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
        here.sendParticles(ParticleTypes.REVERSE_PORTAL, origin.x, origin.y + 1.3, origin.z, 60, 0.5, 1.0, 0.5, 0.08);
        there.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1.3, dest.z, 60, 0.5, 1.0, 0.5, 0.08);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.space_magic.gate_open", dimName(destKey.location()), GATE_LIFE / 20)
                .withColor(p.color()), true);
        MagicApi.addCounter(sp, "space_magic.gates", 1);
        return true;
    }

    // ------------------------------------------------------------------ chegada
    /** Ponto de chegada seguro na dimensão de destino, ou null. */
    @Nullable
    public static Vec3 arrival(ServerPlayer sp, ServerLevel there) {
        // 1) hub da dimensão (Places tipo dimension_hub)
        for (Places.Place place : Places.all(there)) {
            if (Travel.HUB_TYPE.equals(place.type())) {
                Vec3 v = safeNear(there, place.center(), 8);
                if (v != null) return v;
            }
        }
        // 2) último chão firme do jogador lá
        BlockPos last = SpaceData.lastPos(sp, there.dimension());
        if (last != null) {
            Vec3 v = safeNear(there, last, 4);
            if (v != null) return v;
        }
        // 3) Overworld: cama/âncora ou o ponto de nascimento do mundo
        if (there.dimension() == Level.OVERWORLD) {
            BlockPos bed = sp.getRespawnDimension() == Level.OVERWORLD ? sp.getRespawnPosition() : null;
            BlockPos spawn = bed != null ? bed : there.getSharedSpawnPos();
            BlockPos top = there.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
            Vec3 v = safeNear(there, bed != null ? bed : top, 8);
            if (v != null) return v;
        }
        // 4) End: a plataforma de obsidiana do vanilla
        if (there.dimension() == Level.END) {
            BlockPos plat = ServerLevel.END_SPAWN_POINT;
            EndPlatformFeature.createEndPlatform(there, plat, true); // obsidiana embaixo, ar em cima
            return Vec3.atBottomCenterOf(plat);
        }
        // 5) qualquer outra: procura chão perto da origem do mapa
        int y = Math.min(there.getLogicalHeight() - 8, Math.max(there.getSeaLevel(), there.getMinBuildHeight() + 8));
        return safeNear(there, new BlockPos(0, y, 0), 16);
    }

    /** Chão perto de {@code at} na mesma dimensão (para o portal de origem); senão o próprio ponto. */
    private static Vec3 groundNear(ServerLevel level, Vec3 at) {
        Vec3 v = safeNear(level, BlockPos.containing(at), 2);
        return v != null ? v : at;
    }

    /** Busca em anéis um bloco com chão firme e 2 de ar (sem líquido), até {@code radius} na horizontal e ±24 na vertical. */
    @Nullable
    public static Vec3 safeNear(ServerLevel level, BlockPos around, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int i = 0; i <= 48; i++) {
                        int dy = (i % 2 == 0) ? i / 2 : -(i + 1) / 2;
                        BlockPos p = around.offset(dx, dy, dz);
                        if (standable(level, p)) return Vec3.atBottomCenterOf(p);
                    }
                }
            }
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos p) {
        if (p.getY() <= level.getMinBuildHeight() + 1 || p.getY() >= level.getMinBuildHeight() + level.getLogicalHeight() - 2) return false;
        if (!level.getWorldBorder().isWithinBounds(p)) return false;
        BlockState below = level.getBlockState(p.below());
        if (!below.isFaceSturdy(level, p.below(), Direction.UP) || below.getFluidState().is(FluidTags.LAVA)) return false;
        if (below.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || below.is(net.minecraft.world.level.block.Blocks.BEDROCK)) return false;
        for (int i = 0; i < 2; i++) {
            BlockPos q = p.above(i);
            BlockState s = level.getBlockState(q);
            if (!s.getCollisionShape(level, q).isEmpty() || !s.getFluidState().isEmpty()) return false;
            if (s.is(net.minecraft.world.level.block.Blocks.FIRE) || s.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ travessia
    /** Quem pode atravessar: jogadores e companheiros (domados, na guia, montarias); nunca chefes. */
    public static boolean canPass(Entity e) {
        if (e instanceof Player p) return !p.isSpectator();
        if (!(e instanceof Mob m) || e.getType().is(Tags.EntityTypes.BOSSES)) return false;
        if (m instanceof TamableAnimal t && t.isTame()) return true;
        if (m instanceof AbstractHorse h && h.isTamed()) return true;
        return m instanceof Leashable l && l.getLeashHolder() instanceof Player;
    }

    public static boolean justArrived(Entity e) {
        Long t = ARRIVED.get(e.getUUID());
        MinecraftServer server = e.getServer();
        return t != null && server != null && server.getTickCount() - t < 40;
    }

    /** Leva {@code e} (e, se for jogador, os companheiros dele por perto) para o outro lado — no fim do tick. */
    public static void pass(SpaceGateEntity gate, Entity e, ResourceKey<Level> dim, Vec3 pos, float yaw) {
        Set<Entity> group = new LinkedHashSet<>();
        group.add(e);
        if (e instanceof Player p) {
            if (p.getVehicle() != null && canPass(p.getVehicle())) group.add(p.getVehicle());
            AABB box = p.getBoundingBox().inflate(12);
            for (Mob m : p.level().getEntitiesOfClass(Mob.class, box, m -> isCompanionOf(m, p))) group.add(m);
        }
        for (Entity g : group) QUEUE.add(new Move(g, dim, pos, yaw));
        if (gate.level() instanceof ServerLevel sl) {
            sl.playSound(null, gate.getX(), gate.getY() + 1, gate.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
            sl.sendParticles(ParticleTypes.PORTAL, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 30, 0.3, 0.6, 0.3, 0.6);
        }
    }

    private static boolean isCompanionOf(Mob m, Player p) {
        if (m instanceof TamableAnimal t) return t.isTame() && p.getUUID().equals(t.getOwnerUUID()) && !t.isOrderedToSit();
        return m instanceof Leashable l && l.getLeashHolder() == p;
    }

    /** Processa as travessias pendentes (fim do tick do servidor). */
    static void flush(MinecraftServer server) {
        while (!QUEUE.isEmpty()) {
            Move mv = QUEUE.poll();
            Entity e = mv.entity();
            if (!e.isAlive() || e.isRemoved()) continue;
            ServerLevel to = server.getLevel(mv.dim());
            if (to == null) continue;
            if (e instanceof ServerPlayer sp && sp.isPassenger()) sp.stopRiding();
            Vec3 p = mv.pos();
            e.setDeltaMovement(Vec3.ZERO);
            boolean ok = e.teleportTo(to, p.x, p.y, p.z, Set.of(), mv.yaw(), e.getXRot());
            if (!ok) continue;
            ARRIVED.put(e.getUUID(), (long) server.getTickCount());
            e.resetFallDistance();
            e.setPortalCooldown();
            if (e instanceof Mob mob) mob.getNavigation().stop();
            to.playSound(null, p.x, p.y + 1, p.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.5F);
            to.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 1, p.z, 24, 0.3, 0.6, 0.3, 0.05);
        }
        if (server.getTickCount() % 200 == 0) {
            long now = server.getTickCount();
            ARRIVED.values().removeIf(t -> now - t > 200);
            PENDING.values().removeIf(pd -> now > pd.expires());
        }
    }

    static void clear() {
        PENDING.clear();
        ARRIVED.clear();
        QUEUE.clear();
    }

    private SpaceGates() {}
}
