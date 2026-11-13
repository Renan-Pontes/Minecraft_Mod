package com.renanpontes.isekaimahou.feature.las_noches;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Guardas de Las Noches: Exequias (Arrancar de patrulha pelos corredores, colunata e salão de entrada), Fracciones
 * diante das salas 2 e 6, e Menos Gigantes rondando o pátio de areia sob o céu falso. A cada 10 s, se há alguém dentro
 * do complexo, completa o que falta (no máx. 2 por vez) num posto LONGE dos jogadores (ninguém "pipoca" na frente de
 * ninguém). Não são persistentes: quando todos vão embora, somem como monstros comuns.
 */
final class LnGuards {
    static final String TAG = "isekai_mahou_ln_guard";
    private static final double MIN_PLAYER_DIST = 20.0;

    /** Posto: rota de patrulha (relativa ao centro da cúpula), tipo e teto. */
    private record Post(String kind, List<BlockPos> route, int roam) {}

    private record Kind(String id, int cap, boolean menos) {}

    private static final Kind EXEQUIAS = new Kind("exequias", 6, false), FRACCION_2 = new Kind("fraccion_barragan", 2, false),
            FRACCION_6 = new Kind("fraccion_grimmjow", 2, false), MENOS = new Kind("menos", 2, true);
    private static final List<Kind> KINDS = List.of(EXEQUIAS, FRACCION_2, FRACCION_6, MENOS);

    private static final List<Post> POSTS = List.of(
            new Post("exequias", List.of(new BlockPos(0, 1, 70), new BlockPos(0, 1, 96), new BlockPos(-20, 1, 96), new BlockPos(-20, 1, 107)), 30),
            new Post("exequias", List.of(new BlockPos(0, 1, 44), new BlockPos(0, 1, 62)), 16),
            new Post("exequias", ring(36, 0), 20),
            new Post("exequias", ring(36, 180), 20),
            new Post("exequias", List.of(new BlockPos(-6, 1, -38), new BlockPos(6, 1, -38), new BlockPos(0, 1, -30)), 14),
            new Post("exequias", List.of(new BlockPos(0, 1, 30), new BlockPos(10, 1, 31), new BlockPos(-10, 1, 31)), 14),
            new Post("fraccion_barragan", List.of(new BlockPos(-37, 1, -6), new BlockPos(-37, 1, 6)), 10),
            new Post("fraccion_grimmjow", List.of(new BlockPos(33, 1, 27), new BlockPos(45, 1, 27)), 10),
            new Post("menos", List.of(new BlockPos(-20, 1, 4)), 12),
            new Post("menos", List.of(new BlockPos(6, 1, -20)), 12));

    private static List<BlockPos> ring(int r, int startDeg) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(startDeg + i * 30);
            out.add(new BlockPos((int) Math.round(Math.cos(a) * r), 1, (int) Math.round(Math.sin(a) * r)));
        }
        return out;
    }

    static AABB complexBox(BlockPos c) {
        return new AABB(c.getX() + LnLayout.MIN_X, c.getY() - 4, c.getZ() + LnLayout.MIN_Z,
                c.getX() + LnLayout.MAX_X + 1, c.getY() + LnLayout.MAX_Y, c.getZ() + LnLayout.MAX_Z + 1);
    }

    static void tick(ServerLevel level, BlockPos c) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;
        AABB box = complexBox(c);
        Map<String, Integer> have = new HashMap<>();
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.getTags().contains(TAG))) {
            for (String t : m.getTags()) {
                if (t.startsWith(TAG + ".")) have.merge(t.substring(TAG.length() + 1), 1, Integer::sum);
            }
        }
        int spawned = 0;
        for (Post post : POSTS) {
            if (spawned >= 2) break;
            Kind kind = kindOf(post.kind);
            if (kind == null || have.getOrDefault(kind.id, 0) >= kind.cap) continue;
            BlockPos at = c.offset(post.route.get(level.getRandom().nextInt(post.route.size())));
            if (!level.isLoaded(at) || nearPlayer(level, at)) continue;
            if (spawn(level, c, post, kind, at)) {
                have.merge(kind.id, 1, Integer::sum);
                spawned++;
            }
        }
    }

    private static Kind kindOf(String id) {
        for (Kind k : KINDS) if (k.id.equals(id)) return k;
        return null;
    }

    private static boolean nearPlayer(ServerLevel level, BlockPos at) {
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(at)) < MIN_PLAYER_DIST * MIN_PLAYER_DIST) return true;
        }
        return false;
    }

    private static boolean spawn(ServerLevel level, BlockPos c, Post post, Kind kind, BlockPos at) {
        EntityType<? extends PathfinderMob> type = kind.menos ? HmRegistries.MENOS_GIANT.get() : HmRegistries.ARRANCAR.get();
        PathfinderMob m = type.create(level);
        if (m == null) return false;
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
        if (!level.noCollision(m)) return false;
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null);
        m.addTag(TAG);
        m.addTag(TAG + "." + kind.id);
        if (!kind.menos) m.setCustomName(Component.translatable("entity.isekai_mahou.las_noches." + kind.id));
        m.restrictTo(at, post.roam + 14);
        List<BlockPos> route = new ArrayList<>();
        for (BlockPos r : post.route) route.add(c.offset(r));
        if (route.size() > 1) m.goalSelector.addGoal(5, new PatrolGoal(m, route));
        level.addFreshEntity(m);
        level.sendParticles(HmFx.dust(kind.menos ? 0x15151C : 0xF4F4F0, 1.4F), m.getX(), m.getY() + 1, m.getZ(), 10, 0.3, 0.6, 0.3, 0);
        level.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + 0.5, m.getZ(), 6, 0.3, 0.3, 0.3, 0.01);
        return true;
    }

    /** Patrulha de vai-e-volta pelos pontos da rota, com pausas curtas olhando em volta. Some na luta (alvo > patrulha). */
    static final class PatrolGoal extends Goal {
        private final PathfinderMob mob;
        private final List<BlockPos> route;
        private int index, dir = 1, wait;

        PatrolGoal(PathfinderMob mob, List<BlockPos> route) {
            this.mob = mob;
            this.route = route;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() == null && !mob.isVehicle();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (wait > 0) {
                wait--;
                return;
            }
            BlockPos to = route.get(index);
            if (mob.blockPosition().distSqr(to) < 4.0) {
                if (index + dir < 0 || index + dir >= route.size()) dir = -dir;
                index += dir;
                wait = 20 + mob.getRandom().nextInt(40);
                return;
            }
            if (mob.getNavigation().isDone() || mob.tickCount % 40 == 0) {
                mob.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.7);
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }

    private LnGuards() {}
}
