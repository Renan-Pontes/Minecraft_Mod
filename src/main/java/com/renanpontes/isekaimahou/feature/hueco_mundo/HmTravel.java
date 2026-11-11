package com.renanpontes.isekaimahou.feature.hueco_mundo;

import java.util.Optional;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.RealmGate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Entrar e sair dos reinos. Entrar: guarda o ponto de volta (onde o jogador estava, olhando para fora do portal) e leva
 * para a chegada do hub. Sair: volta ao ponto guardado; sem ponto, ao spawn do Mundo Normal.
 * Os portais são entidades {@link RealmGate} (Garganta: rasgo negro no céu; Círculo das Fadas: anel de pétalas).
 */
public final class HmTravel {
    /** Recarga dos atalhos (glifos de Shinigami/Quincy e Chave da Garganta), em ticks. */
    public static final int SHORTCUT_COOLDOWN = 2400;
    private static final String COOLDOWN_KEY = "shortcut_until";

    /** Leva o jogador para o reino (guarda o ponto de volta se vier de fora). */
    public static boolean enter(ServerPlayer player, HmRealm realm) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        ServerLevel target = server.getLevel(realm.key);
        if (target == null) return false;
        ServerLevel from = player.serverLevel();
        if (!from.dimension().equals(realm.key)) saveReturn(player, realm, from);
        Vec3 arrival = HmSites.ensureBuilt(target, realm);
        // As duas chegadas ficam ao sul do que importa (palácio / trono): olhando para o norte.
        player.changeDimension(new DimensionTransition(target, arrival, Vec3.ZERO, 180F, 0F, arrivalFx(realm.color)));
        return true;
    }

    /** Volta do reino para o ponto guardado (ou para o spawn do Mundo Normal). */
    public static boolean leave(ServerPlayer player, HmRealm realm) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        CompoundTag points = player.getData(HmRegistries.RETURN_POINTS);
        if (points.contains(realm.id, Tag.TAG_COMPOUND)) {
            CompoundTag t = points.getCompound(realm.id);
            ResourceLocation dimId = ResourceLocation.tryParse(t.getString("dim"));
            ServerLevel level = dimId == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
            if (level != null && !level.dimension().equals(realm.key)) {
                Vec3 pos = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
                player.changeDimension(new DimensionTransition(level, pos, Vec3.ZERO, t.getFloat("yaw"), 0F, arrivalFx(realm.color)));
                return true;
            }
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        overworld.getChunkAt(spawn);
        int y = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        player.changeDimension(new DimensionTransition(overworld, new Vec3(spawn.getX() + 0.5, y, spawn.getZ() + 0.5), Vec3.ZERO,
                player.getYRot(), 0F, arrivalFx(realm.color)));
        return true;
    }

    /** Atravessar um portal: dentro do reino → sai; fora → entra. */
    public static void cross(ServerPlayer player, HmRealm realm) {
        if (player.level().dimension().equals(realm.key)) {
            leave(player, realm);
        } else {
            enter(player, realm);
        }
    }

    private static void saveReturn(ServerPlayer player, HmRealm realm, ServerLevel from) {
        Vec3 spot = exitSpot(from, player.blockPosition()).orElse(player.position());
        CompoundTag t = new CompoundTag();
        t.putString("dim", from.dimension().location().toString());
        t.putDouble("x", spot.x);
        t.putDouble("y", spot.y);
        t.putDouble("z", spot.z);
        t.putFloat("yaw", Mth.wrapDegrees(player.getYRot() + 180F));
        CompoundTag all = player.getData(HmRegistries.RETURN_POINTS).copy();
        all.put(realm.id, t);
        player.setData(HmRegistries.RETURN_POINTS, all);
    }

    private static Optional<Vec3> exitSpot(Level level, BlockPos origin) {
        for (BlockPos p : BlockPos.withinManhattan(origin.offset(0, 0, 3), 4, 3, 4)) {
            if (standable(level, p)) return Optional.of(Vec3.atBottomCenterOf(p.immutable()));
        }
        return Optional.empty();
    }

    private static boolean standable(Level level, BlockPos p) {
        BlockPos below = p.below();
        return free(level, p) && free(level, p.above()) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static boolean free(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    private static DimensionTransition.PostDimensionTransition arrivalFx(int rgb) {
        Vector3f color = new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
        return DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET).then(e -> {
            if (e.level() instanceof ServerLevel level) {
                level.sendParticles(new DustParticleOptions(color, 1.6F), e.getX(), e.getY() + 1.0, e.getZ(), 40, 0.5, 0.9, 0.5, 0.0);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1.0, e.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
                level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
            }
        });
    }

    // ------------------------------------------------------------------ Portais

    /** Abre um portal do reino em {@code base} (pés). Devolve null se já houver um aberto ali perto. */
    @Nullable
    public static RealmGate openGate(ServerLevel level, Vec3 base, HmRealm realm, int lifetime, float yaw) {
        AABB near = new AABB(base, base).inflate(5);
        if (!level.getEntitiesOfClass(RealmGate.class, near, g -> g.isAlive()).isEmpty()) return null;
        RealmGate gate = HmRegistries.REALM_GATE.get().create(level);
        if (gate == null) return null;
        gate.setup(realm, lifetime);
        gate.moveTo(base.x, base.y, base.z, yaw, 0F);
        level.addFreshEntity(gate);
        return gate;
    }

    /** Lugar à frente do jogador para abrir um atalho (no chão, a ~3 blocos). */
    public static Vec3 spotAhead(ServerPlayer p) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        Vec3 at = p.position().add(look.normalize().scale(3.0));
        BlockPos b = BlockPos.containing(at);
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos q = b.offset(0, dy, 0);
            if (standable(p.level(), q)) return Vec3.atBottomCenterOf(q);
        }
        return p.position();
    }

    /**
     * Atalho para o Mundo Oco (glifos do Shinigami/Quincy, Chave da Garganta): só no Mundo Normal (abre a entrada) ou
     * dentro do Mundo Oco (abre a saída). {@code checkCooldown} = os glifos têm recarga; a chave é consumida.
     */
    public static boolean shortcut(ServerPlayer p, Vec3 at, boolean checkCooldown) {
        ServerLevel level = p.serverLevel();
        boolean inHollow = level.dimension().equals(HmRealm.HOLLOW.key);
        if (!inHollow && level.dimension() != Level.OVERWORLD) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.hueco_mundo.shortcut_wrong_dim"), true);
            return false;
        }
        if (checkCooldown && !p.getAbilities().instabuild) {
            long until = p.getData(HmRegistries.RETURN_POINTS).getLong(COOLDOWN_KEY);
            long now = level.getGameTime();
            if (until > now) {
                p.displayClientMessage(Component.translatable("message.isekai_mahou.hueco_mundo.shortcut_cooldown",
                        (int) Math.ceil((until - now) / 20.0)), true);
                return false;
            }
        }
        RealmGate gate = openGate(level, at, HmRealm.HOLLOW, 200, p.getYRot());
        if (gate == null) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.hueco_mundo.gate_already_open"), true);
            return false;
        }
        if (checkCooldown) {
            CompoundTag all = p.getData(HmRegistries.RETURN_POINTS).copy();
            all.putLong(COOLDOWN_KEY, level.getGameTime() + SHORTCUT_COOLDOWN);
            p.setData(HmRegistries.RETURN_POINTS, all);
        }
        return true;
    }

    private HmTravel() {}
}
