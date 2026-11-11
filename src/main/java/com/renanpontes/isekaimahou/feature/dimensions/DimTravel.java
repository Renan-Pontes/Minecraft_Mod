package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Viagem entre dimensões. Entrar: vai para o hub fixo da dimensão e o jogador guarda o ponto de onde saiu
 * (ao lado do portal usado). Sair: volta para esse ponto; sem ponto salvo, vai para o spawn do Mundo Normal.
 */
public final class DimTravel {
    public static final int TWILIGHT_MIN_CIRCLE = 9;
    public static final String BOSS_ABYSS = "abyss_sovereign";
    public static final String BOSS_SERAPH = "seraph";

    @Nullable
    public static DimensionTransition enter(ServerLevel from, Entity entity, DimKind kind) {
        ServerLevel target = from.getServer().getLevel(kind.key);
        if (target == null) return null;
        // Montaria atravessando com jogadores: cada um guarda o seu ponto de volta.
        for (ServerPlayer player : players(entity)) saveReturn(player, kind, from);
        DimHubs.ensureHub(target, kind);
        return new DimensionTransition(target, kind.arrival, Vec3.ZERO, kind.arrivalYaw, 0F, arrivalFx(kind.color));
    }

    @Nullable
    public static DimensionTransition leave(ServerLevel from, Entity entity, DimKind kind) {
        MinecraftServer server = from.getServer();
        List<ServerPlayer> riders = players(entity);
        if (!riders.isEmpty()) {
            // Numa montaria, o ponto de volta é o do primeiro jogador (o próprio jogador, se for ele que atravessa).
            CompoundTag points = riders.get(0).getData(DimRegistry.RETURN_POINTS);
            if (points.contains(kind.id, Tag.TAG_COMPOUND)) {
                CompoundTag t = points.getCompound(kind.id);
                ResourceLocation dimId = ResourceLocation.tryParse(t.getString("dim"));
                ServerLevel level = dimId == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
                if (level != null && level != from) {
                    Vec3 pos = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
                    return new DimensionTransition(level, pos, Vec3.ZERO, t.getFloat("yaw"), 0F, arrivalFx(kind.color));
                }
            }
        }
        ServerLevel overworld = server.overworld();
        if (overworld == from) return null;
        return new DimensionTransition(overworld, entity, arrivalFx(kind.color));
    }

    /** A própria entidade, se for jogador, mais os jogadores montados nela (direta ou indiretamente). */
    private static List<ServerPlayer> players(Entity entity) {
        List<ServerPlayer> out = new ArrayList<>();
        if (entity instanceof ServerPlayer p) out.add(p);
        for (Entity e : entity.getIndirectPassengers()) {
            if (e instanceof ServerPlayer p) out.add(p);
        }
        return out;
    }

    /** Guarda o ponto de retorno: o lugar seguro mais perto do portal, olhando para fora dele. */
    public static void saveReturn(ServerPlayer player, DimKind kind, ServerLevel from) {
        if (from.dimension().equals(kind.key)) return;
        Vec3 spot = exitSpot(from, player.blockPosition()).orElse(player.position());
        CompoundTag t = new CompoundTag();
        t.putString("dim", from.dimension().location().toString());
        t.putDouble("x", spot.x);
        t.putDouble("y", spot.y);
        t.putDouble("z", spot.z);
        t.putFloat("yaw", Mth.wrapDegrees(player.getYRot() + 180F));
        CompoundTag all = player.getData(DimRegistry.RETURN_POINTS).copy();
        all.put(kind.id, t);
        player.setData(DimRegistry.RETURN_POINTS, all);
    }

    public static Optional<Vec3> exitSpot(Level level, BlockPos origin) {
        for (BlockPos p : BlockPos.withinManhattan(origin, 3, 2, 3)) {
            if (isStandable(level, p)) return Optional.of(Vec3.atBottomCenterOf(p.immutable()));
        }
        // A Fenda da Ruptura do Crepúsculo paira acima da ilha rachada: sem chão por perto, procura mais longe e mais
        // abaixo (antes, a volta deixava o jogador no ar e ele caía).
        for (BlockPos p : BlockPos.withinManhattan(origin, 6, 10, 6)) {
            if (isStandable(level, p)) return Optional.of(Vec3.atBottomCenterOf(p.immutable()));
        }
        return Optional.empty();
    }

    private static boolean isStandable(Level level, BlockPos p) {
        BlockPos below = p.below();
        return isFree(level, p) && isFree(level, p.above()) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static boolean isFree(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && !(s.getBlock() instanceof Portal) && s.getFluidState().isEmpty();
    }

    /** Som de travessia (só para quem atravessa) + brilho e som para quem estiver perto da chegada. */
    private static DimensionTransition.PostDimensionTransition arrivalFx(int rgb) {
        Vector3f color = new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
        return DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET).then(e -> {
            if (e.level() instanceof ServerLevel level) {
                level.sendParticles(new DustParticleOptions(color, 1.6F), e.getX(), e.getY() + 1.0, e.getZ(), 40, 0.5, 0.9, 0.5, 0.0);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1.0, e.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
                level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
        });
    }

    // ------------------------------------------------------------------ Fenda do Crepúsculo
    public static boolean canEnterTwilight(ServerPlayer player) {
        return player.getAbilities().instabuild || missingTwilight(MagicApi.get(player)).isEmpty();
    }

    /** O que falta para atravessar a Fenda (vazio = pode). */
    public static List<Component> missingTwilight(MagicData d) {
        List<Component> out = new ArrayList<>();
        if (d.circle() < TWILIGHT_MIN_CIRCLE) {
            out.add(Component.translatable("message.isekai_mahou.dimensions.req_circle", TWILIGHT_MIN_CIRCLE));
        }
        if (d.counter("boss." + BOSS_ABYSS) < 1) out.add(Component.translatable("message.isekai_mahou.dimensions.req_boss", bossName(BOSS_ABYSS)));
        if (d.counter("boss." + BOSS_SERAPH) < 1) out.add(Component.translatable("message.isekai_mahou.dimensions.req_boss", bossName(BOSS_SERAPH)));
        return out;
    }

    /** Nome do chefe pela entidade registrada (outro módulo) ou pela tradução reserva deste módulo. */
    public static Component bossName(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id(id))
                .<Component>map(t -> t.getDescription())
                .orElseGet(() -> Component.translatable(String.join(".", "boss", IsekaiMahou.MODID, "dimensions", id)));
    }

    private DimTravel() {}
}
