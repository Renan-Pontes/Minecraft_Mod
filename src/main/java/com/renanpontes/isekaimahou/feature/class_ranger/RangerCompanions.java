package com.renanpontes.isekaimahou.feature.class_ranger;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerCompanion;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerEagleEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerSteedEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerWolfEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Companheiros do Ranger: Lobo (desde o início), Águia Batedora (Trilha rank I) e Corcel (rank II). O dono guarda os
 * UUIDs e a hora da morte nos dados persistentes do jogador ({@link #KEY}, copiados ao renascer). Morto, o companheiro
 * volta depois de {@link #respawnTicks}. O nível (1..10) vem da classe: 1 + rank da Trilha + Círculo/2; quem não é
 * Ranger (aprendeu por Lição) chega no máximo ao nível 6.
 */
public final class RangerCompanions {
    public static final String KEY = "isekai_mahou_ranger_companions";
    public static final String WOLF = "wolf";
    public static final String EAGLE = "eagle";
    public static final String STEED = "steed";

    // =====================================================================================
    //  Regras
    // =====================================================================================

    public static int level(MagicData d) {
        if (d.path() == MagicPath.RANGER) return Math.max(1, Math.min(10, 1 + RangerProgress.rank(d) + d.circle() / 2));
        return Math.max(1, Math.min(6, 1 + d.circle() / 2));
    }

    /** 0 = Filhote (1–3), 1 = Adulto (4–7), 2 = Alfa (8–10). */
    public static int stageOf(int level) {
        return level <= 3 ? 0 : level <= 7 ? 1 : 2;
    }

    /** A Águia vem no rank I da Trilha (outras classes que aprenderam o glifo: a partir do Círculo 4). */
    public static boolean eagleUnlocked(MagicData d) {
        return d.path() == MagicPath.RANGER ? RangerProgress.rank(d) >= 1 : d.circle() >= 4;
    }

    /** Tempo até um companheiro morto poder voltar (60 s; −6 s por rank, mín. 30 s). */
    public static int respawnTicks(MagicData d) {
        int rank = d.path() == MagicPath.RANGER ? RangerProgress.rank(d) : 0;
        return Math.max(30, 60 - 6 * rank) * 20;
    }

    private static CompoundTag data(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(KEY)) pd.put(KEY, new CompoundTag());
        return pd.getCompound(KEY);
    }

    // =====================================================================================
    //  Busca
    // =====================================================================================

    /** Companheiro atual do jogador (carregado em qualquer dimensão), ou null. */
    @Nullable
    public static Entity find(ServerPlayer p, String slot) {
        CompoundTag d = data(p);
        if (!d.hasUUID(slot)) return null;
        UUID id = d.getUUID(slot);
        Entity e = p.serverLevel().getEntity(id);
        if (e != null && e.isAlive()) return e;
        for (ServerLevel l : p.server.getAllLevels()) {
            if (l == p.serverLevel()) continue;
            e = l.getEntity(id);
            if (e != null && e.isAlive()) return e;
        }
        return null;
    }

    /** O companheiro é o atual do dono? (Dono offline ou sem registro: sim — não some à toa.) */
    public static boolean isCurrent(ServerLevel level, RangerCompanion c) {
        return isCurrent(level, c.getOwnerUUID(), c.slotKey(), c.getUUID());
    }

    public static boolean isCurrentSteed(ServerLevel level, RangerSteedEntity s) {
        return isCurrent(level, s.getOwnerUUID(), STEED, s.getUUID());
    }

    private static boolean isCurrent(ServerLevel level, @Nullable UUID owner, String slot, UUID self) {
        if (owner == null) return true;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
        if (p == null) return true;
        CompoundTag d = data(p);
        return !d.hasUUID(slot) || d.getUUID(slot).equals(self);
    }

    // =====================================================================================
    //  Chamado da Matilha (Lobo + Águia)
    // =====================================================================================

    /**
     * Chama o Lobo (e a Águia, se liberada). Com {@code target} inimigo: mandam atacar. Sem alvo (ou o próprio dono):
     * voltam para perto, curam 30% e ganham Regeneração. Devolve true se algum companheiro respondeu.
     */
    public static boolean call(ServerPlayer p, @Nullable LivingEntity target, float power) {
        ServerLevel level = p.serverLevel();
        MagicData md = MagicApi.get(p);
        int lvl = level(md);
        boolean recall = target == null || target == p;
        RangerCompanion wolf = ensure(p, level, RangerRegistries.WOLF.get(), WOLF, lvl, recall);
        RangerCompanion eagle = eagleUnlocked(md) ? ensure(p, level, RangerRegistries.EAGLE.get(), EAGLE, lvl, recall) : null;
        boolean any = false;
        for (RangerCompanion c : new RangerCompanion[] {wolf, eagle}) {
            if (c == null) continue;
            any = true;
            if (recall) {
                c.setTarget(null);
                c.heal(c.getMaxHealth() * 0.3F * power);
                c.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, power > 1.3F ? 1 : 0));
                level.sendParticles(ParticleTypes.HEART, c.getX(), c.getY() + c.getBbHeight(), c.getZ(), 3, 0.3, 0.2, 0.3, 0);
            } else if (!RangerUtil.isFriend(p, target)) {
                c.command(target);
                c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
                c.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, power > 1.3F ? 1 : 0));
            }
        }
        if (wolf instanceof RangerWolfEntity w && recall) w.howl();
        if (eagle instanceof RangerEagleEntity e && recall) e.screech();
        return any;
    }

    /** Companheiro do tipo pedido: o atual (trazido para perto se estiver longe/noutra dimensão) ou um novo. */
    @Nullable
    private static <T extends RangerCompanion> RangerCompanion ensure(ServerPlayer p, ServerLevel level, EntityType<T> type, String slot,
                                                                      int lvl, boolean recall) {
        RangerCompanion c = find(p, slot) instanceof RangerCompanion rc ? rc : null;
        boolean flying = EAGLE.equals(slot);
        if (c != null && c.level() != level) {
            // noutra dimensão: a velha se desfaz e a mesma fera "chega" ao lado do dono
            c.discard();
            c = null;
        } else if (c != null && (c.distanceToSqr(p) > 40 * 40 || (recall && c.distanceToSqr(p) > 6 * 6))) {
            Vec3 at = beside(p, flying ? -1.2 : 1.5, flying ? 1.8 : 0.1);
            level.sendParticles(ParticleTypes.CLOUD, c.getX(), c.getY() + 0.5, c.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            c.teleportTo(at.x, at.y, at.z);
            c.getNavigation().stop();
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 8, 0.3, 0.3, 0.3, 0.02);
        }
        if (c == null) {
            if (resting(p, slot, type.getDescription())) return null;
            T fresh = type.create(level);
            if (fresh == null) return null;
            Vec3 at = beside(p, flying ? -1.2 : 1.5, flying ? 1.8 : 0.1);
            fresh.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
            fresh.setTame(true, false);
            fresh.setOwnerUUID(p.getUUID());
            fresh.applyLevel(lvl);
            fresh.setHealth(fresh.getMaxHealth());
            if (!level.addFreshEntity(fresh)) return null;
            register(p, slot, fresh.getUUID());
            arrive(level, at);
            c = fresh;
        }
        c.setOrderedToSit(false);
        c.setInSittingPose(false);
        c.applyLevel(lvl);
        return c;
    }

    // =====================================================================================
    //  Chamado do Corcel
    // =====================================================================================

    /** Chama o Corcel (selado) ao lado do dono e, se ele estiver a pé, já monta. Devolve o corcel (ou null). */
    @Nullable
    public static RangerSteedEntity callSteed(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        RangerSteedEntity s = find(p, STEED) instanceof RangerSteedEntity rs ? rs : null;
        if (s != null && (s.level() != level || s.distanceToSqr(p) > 48 * 48)) {
            s.discard();
            s = null;
        }
        if (s == null) {
            if (resting(p, STEED, RangerRegistries.STEED.get().getDescription())) return null;
            s = RangerRegistries.STEED.get().create(level);
            if (s == null) return null;
            Vec3 at = beside(p, 2.0, 0.1);
            s.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
            s.setOwnerUUID(p.getUUID());
            s.refreshFromOwner(p);
            s.setHealth(s.getMaxHealth());
            if (!level.addFreshEntity(s)) return null;
            register(p, STEED, s.getUUID());
            arrive(level, at);
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, at.y + 1.2, at.z, 14, 0.6, 0.5, 0.6, 0.02);
        } else {
            s.refreshFromOwner(p);
        }
        s.neigh();
        if (!p.isPassenger()) p.startRiding(s);
        return s;
    }

    // =====================================================================================
    //  Registro, morte e nível
    // =====================================================================================

    private static void register(ServerPlayer p, String slot, UUID id) {
        CompoundTag d = data(p);
        d.putUUID(slot, id);
        d.remove(slot + "Dead");
    }

    /** O companheiro morreu há pouco? Avisa o tempo que falta e devolve true. */
    private static boolean resting(ServerPlayer p, String slot, Component name) {
        CompoundTag d = data(p);
        long dead = d.getLong(slot + "Dead");
        long left = dead + respawnTicks(MagicApi.get(p)) - p.serverLevel().getGameTime();
        if (dead > 0 && left > 0 && left <= respawnTicks(MagicApi.get(p))) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.class_ranger.resting", name,
                    (int) Math.ceil(left / 20.0)).withColor(0xC9A06B), true);
            return true;
        }
        return false;
    }

    private static Vec3 beside(ServerPlayer p, double side, double up) {
        return p.position().add(Vec3.directionFromRotation(0, p.getYRot() + 90).scale(side)).add(0, up, 0);
    }

    private static void arrive(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 16, 0.4, 0.4, 0.4, 0.04);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.5, at.z, 10, 0.5, 0.5, 0.5, 0);
        RangerFx.sound(level, at, SoundEvents.EVOKER_PREPARE_SUMMON, 0.5F, 1.7F);
    }

    public static void onDeath(ServerLevel level, RangerCompanion c) {
        markDead(level, c.getOwnerUUID(), c.slotKey(), c.getUUID(), c);
    }

    public static void onSteedDeath(ServerLevel level, RangerSteedEntity s) {
        markDead(level, s.getOwnerUUID(), STEED, s.getUUID(), s);
    }

    private static void markDead(ServerLevel level, @Nullable UUID owner, String slot, UUID self, LivingEntity who) {
        if (owner == null) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
        if (p == null) return;
        CompoundTag d = data(p);
        if (!d.hasUUID(slot) || !d.getUUID(slot).equals(self)) return;
        d.putLong(slot + "Dead", level.getGameTime());
        p.displayClientMessage(Component.translatable("message.isekai_mahou.class_ranger.fell", who.getDisplayName(),
                respawnTicks(MagicApi.get(p)) / 20).withColor(0xE05A4A), true);
    }

    /** Ao renascer, o jogador continua dono dos mesmos companheiros. */
    public static void copyOnClone(Player from, Player to) {
        if (from.getPersistentData().contains(KEY)) to.getPersistentData().put(KEY, from.getPersistentData().getCompound(KEY).copy());
    }

    /** Subiu de rank/Círculo: os companheiros vivos acompanham o nível novo. */
    public static void refreshLevel(ServerPlayer p) {
        int lvl = level(MagicApi.get(p));
        for (String slot : new String[] {WOLF, EAGLE}) {
            if (find(p, slot) instanceof RangerCompanion c) c.applyLevel(lvl);
        }
        if (find(p, STEED) instanceof RangerSteedEntity s) s.applyLevel(lvl);
    }

    private RangerCompanions() {}
}
