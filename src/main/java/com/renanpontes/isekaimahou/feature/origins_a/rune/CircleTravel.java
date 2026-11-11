package com.renanpontes.isekaimahou.feature.origins_a.rune;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.origins_a.OaData;
import com.renanpontes.isekaimahou.feature.origins_a.OaRegistries;
import com.renanpontes.isekaimahou.feature.origins_a.OaUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Viagem das runas do Mago: criar/desfazer Círculos de Teleporte, atravessar de um para o outro e a
 * Runa de Retorno (concentração de 3s que leva ao último círculo, ou à cama/ponto de nascimento).
 */
public final class CircleTravel {
    private static final String GRACE = "isekai_mahou:circle_grace";
    public static final int RECALL_TICKS = 60;

    private record Recall(Vec3 start, int total, int left) {}

    private static final Map<UUID, Recall> RECALLS = new HashMap<>();

    // =====================================================================================
    //  Círculos
    // =====================================================================================
    /** Cria um círculo (ou desfaz um seu que esteja a ≤ 2 blocos). Só jogadores têm círculos. */
    public static void createCircle(SpellContext ctx, BlockPos at) {
        ServerPlayer sp = ctx.casterPlayer();
        if (sp == null) return;
        ServerLevel level = ctx.level();
        Vec3 pos = Vec3.atBottomCenterOf(at).add(0, 0.02, 0);
        OaData data = OaData.of(sp);

        for (TeleportCircleEntity old : level.getEntitiesOfClass(TeleportCircleEntity.class, new AABB(pos, pos).inflate(2.0),
                e -> sp.getUUID().equals(e.ownerId()))) {
            data.removeCircle(old.getUUID());
            old.vanish(level);
            refreshPartners(sp);
            OaUtil.actionbar(sp, "circle_removed");
            return;
        }

        TeleportCircleEntity circle = new TeleportCircleEntity(OaRegistries.TELEPORT_CIRCLE.get(), level);
        circle.moveTo(pos.x, pos.y, pos.z, sp.getYRot(), 0);
        circle.setOwner(sp.getUUID());
        circle.setColor(ctx.color());
        OaData.Circle replaced = data.addCircle(new OaData.Circle(circle.getUUID(), GlobalPos.of(level.dimension(), at)));
        if (replaced != null) {
            ServerLevel ol = level.getServer().getLevel(replaced.pos().dimension());
            if (ol != null && ol.getEntity(replaced.id()) instanceof TeleportCircleEntity oc) oc.vanish(ol);
        }
        // Avisa quem está em cima na hora da criação: só vale depois de sair e pisar de novo
        for (Entity e : level.getEntities((Entity) null, new AABB(pos, pos).inflate(TeleportCircleEntity.RADIUS, 2, TeleportCircleEntity.RADIUS),
                e -> e.isAlive())) {
            circle.markInside(e);
        }
        level.addFreshEntity(circle);
        refreshPartners(sp);
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 0.3, pos.z, 50, 0.8, 0.2, 0.8, 0.4);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.3F);
        OaUtil.actionbar(sp, data.circles().size() >= 2 ? "circle_linked" : "circle_first");
    }

    /** Atualiza o destino guardado nos círculos carregados do jogador. */
    public static void refreshPartners(ServerPlayer sp) {
        OaData data = OaData.of(sp);
        for (OaData.Circle c : data.circles()) {
            ServerLevel lvl = sp.server.getLevel(c.pos().dimension());
            if (lvl != null && lvl.getEntity(c.id()) instanceof TeleportCircleEntity ce) {
                OaData.Circle p = data.partnerOf(c.id());
                ce.setPartner(p != null ? p.pos() : null);
            }
        }
    }

    /** Atravessa de um círculo para o par. */
    static void travel(ServerLevel level, TeleportCircleEntity from, Entity e, GlobalPos dest) {
        ServerLevel target = level.getServer().getLevel(dest.dimension());
        if (target == null) return;
        ServerPlayer owner = OaUtil.player(level, from.ownerId());
        if (owner != null) {
            OaData data = OaData.of(owner);
            OaData.Circle p = data.partnerOf(from.getUUID());
            if (p != null) data.setLastCircle(p.id());
        }
        Vec3 to = Vec3.atBottomCenterOf(dest.pos()).add(0, 0.05, 0);
        if (!clear(target, e, to)) {
            // Alguém construiu em cima do outro círculo: não joga ninguém dentro de blocos
            level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 0.3, e.getZ(), 12, 0.4, 0.1, 0.4, 0.01);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
            if (e instanceof ServerPlayer sp) OaUtil.actionbar(sp, "circle_blocked");
            return;
        }
        departFx(level, e.position(), from.color());
        teleport(e, target, to);
        arriveFx(target, to, from.color());
    }

    /** Cabe a entidade no destino? (carrega o chunk de destino se preciso) */
    private static boolean clear(ServerLevel target, Entity e, Vec3 to) {
        return target.noCollision(e, e.getDimensions(e.getPose()).makeBoundingBox(to).deflate(1.0E-3));
    }

    /** Teleporta qualquer entidade (mesma dimensão ou outra), com período de graça para não voltar na hora. */
    public static void teleport(Entity e, ServerLevel target, Vec3 to) {
        e.getPersistentData().putLong(GRACE, target.getGameTime() + 40);
        target.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, new ChunkPos(BlockPos.containing(to)), 1, e.getId());
        e.resetFallDistance();
        e.teleportTo(target, to.x, to.y, to.z, Set.of(), e.getYRot(), e.getXRot());
    }

    static boolean inGrace(Entity e) {
        return e.getPersistentData().getLong(GRACE) > e.level().getGameTime();
    }

    /**
     * Quem acabou de entrar no mundo/renascer/trocar de dimensão em cima de um círculo não é teleportado:
     * o círculo o marca como "já em cima" e só leva depois de sair e pisar de novo.
     */
    public static void grantGrace(Entity e, int ticks) {
        e.getPersistentData().putLong(GRACE, e.level().getGameTime() + ticks);
    }

    private static void departFx(ServerLevel level, Vec3 p, int color) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 1.0, p.z, 40, 0.3, 0.8, 0.3, 0.05);
        level.sendParticles(dust(color), p.x, p.y + 0.8, p.z, 20, 0.35, 0.7, 0.35, 0);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.1F);
    }

    private static void arriveFx(ServerLevel level, Vec3 p, int color) {
        level.sendParticles(ParticleTypes.PORTAL, p.x, p.y + 0.8, p.z, 50, 0.4, 0.8, 0.4, 0.4);
        level.sendParticles(dust(color), p.x, p.y + 0.5, p.z, 20, 0.5, 0.4, 0.5, 0);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    private static DustParticleOptions dust(int rgb) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), 1.2F);
    }

    // =====================================================================================
    //  Runa de Retorno
    // =====================================================================================
    public static void startRecall(ServerPlayer sp, int ticks) {
        RECALLS.put(sp.getUUID(), new Recall(sp.position(), ticks, ticks));
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
        OaUtil.actionbar(sp, "recall_start");
    }

    public static boolean recalling(ServerPlayer sp) {
        return RECALLS.containsKey(sp.getUUID());
    }

    public static void cancelRecall(ServerPlayer sp, String reasonKey) {
        if (RECALLS.remove(sp.getUUID()) != null) {
            OaUtil.actionbar(sp, reasonKey);
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }

    /** Chamado a cada tick do jogador (servidor). */
    public static void tickRecall(ServerPlayer sp) {
        Recall r = RECALLS.get(sp.getUUID());
        if (r == null) return;
        if (!sp.isAlive() || sp.position().distanceToSqr(r.start()) > 2.25) {
            cancelRecall(sp, "recall_moved");
            return;
        }
        ServerLevel level = sp.serverLevel();
        int done = r.total() - r.left();
        double rise = done / (double) r.total();
        double ang = done * 0.4;
        for (int i = 0; i < 3; i++) {
            double a = ang + i * Math.PI * 2 / 3;
            level.sendParticles(ParticleTypes.ENCHANT, sp.getX() + Math.cos(a) * 0.8, sp.getY() + rise * 2.0,
                    sp.getZ() + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.PORTAL, sp.getX() + Math.cos(a) * 0.8, sp.getY() + 0.1,
                    sp.getZ() + Math.sin(a) * 0.8, 1, 0, 0.2, 0, 0.1);
        }
        if (r.left() > 1) {
            RECALLS.put(sp.getUUID(), new Recall(r.start(), r.total(), r.left() - 1));
            return;
        }
        RECALLS.remove(sp.getUUID());
        finishRecall(sp);
    }

    private static void finishRecall(ServerPlayer sp) {
        ServerLevel from = sp.serverLevel();
        OaData data = OaData.of(sp);
        OaData.Circle c = data.lastCircle();
        departFx(from, sp.position(), 0x9A7BFF);
        if (c != null) {
            ServerLevel lvl = sp.server.getLevel(c.pos().dimension());
            if (lvl != null && clear(lvl, sp, Vec3.atBottomCenterOf(c.pos().pos()).add(0, 0.05, 0))) {
                Vec3 to = Vec3.atBottomCenterOf(c.pos().pos()).add(0, 0.05, 0);
                teleport(sp, lvl, to);
                arriveFx(lvl, to, 0x9A7BFF);
                OaUtil.actionbar(sp, "recall_circle");
                return;
            }
        }
        boolean hasHome = sp.getRespawnPosition() != null;
        DimensionTransition t = sp.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
        teleport(sp, t.newLevel(), t.pos());
        arriveFx(t.newLevel(), t.pos(), 0x9A7BFF);
        OaUtil.actionbar(sp, hasHome && !t.missingRespawnBlock() ? "recall_home" : "recall_spawn");
    }

    public static void clear() {
        RECALLS.clear();
    }

    private CircleTravel() {}
}
