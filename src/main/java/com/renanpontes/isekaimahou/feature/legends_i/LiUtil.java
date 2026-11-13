package com.renanpontes.isekaimahou.feature.legends_i;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Utilitários de servidor dos glifos de anime do legends_i. As regras de alvo vêm do legends_f
 * ({@link LfUtil#isFoe}: monstros, chefes, jogadores NÃO aliados — PvP desligado = aliado — e quem te ataca), então
 * Modo Paz, grupos, duelos e guerras de cidade valem igual aqui.
 */
public final class LiUtil {

    /** Poder efetivo (aumentos × conjuração × atributo de poder mágico). */
    public static float power(SpellContext ctx, SpellStats stats) {
        return LfUtil.power(ctx, stats);
    }

    /** O alvo é o próprio conjurador (forma Si Mesmo)? */
    public static boolean self(SpellContext ctx, Entity target) {
        return ctx.caster() != null && (target == ctx.caster() || target.getUUID().equals(ctx.casterId()));
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Direção do golpe: para onde o conjurador olha (Si Mesmo/aliado) ou dos olhos até o alvo. */
    public static Vec3 aim(SpellContext ctx, @Nullable Entity target) {
        LivingEntity c = ctx.caster();
        if (c == null) return new Vec3(0, 0, 1);
        if (target == null || self(ctx, target) || ctx.isAlly(target)) return c.getLookAngle();
        Vec3 d = center(target).subtract(c.getEyePosition());
        return d.lengthSqr() < 1.0E-4 ? c.getLookAngle() : d.normalize();
    }

    public static boolean isFoe(SpellContext ctx, Entity e) {
        return LfUtil.isFoe(ctx, e);
    }

    public static List<LivingEntity> foesAround(SpellContext ctx, Vec3 c, double radius, int max) {
        return LfUtil.foesAround(ctx, c, radius, max);
    }

    public static boolean boss(LivingEntity e) {
        return Statuses.isBossLike(e);
    }

    /**
     * Inimigo de {@code owner} SEM contexto de magia (clones, corte do Susanoo): mesma regra do
     * {@link SpellContext#isAlly} + {@link LfUtil#isFoe} — grupo/duelo/guerra mandam, PvP desligado = jogadores aliados,
     * aldeões só se atacarem, bichos pacíficos nunca.
     */
    public static boolean hostile(@Nullable LivingEntity owner, @Nullable Entity e) {
        if (owner == null || !(e instanceof LivingEntity le) || !le.isAlive() || le.isSpectator() || e instanceof ArmorStand) return false;
        if (e == owner || e.getUUID().equals(owner.getUUID())) return false;
        if (e instanceof OwnableEntity own && owner.getUUID().equals(own.getOwnerUUID())) return false;
        Boolean social = Party.allyOverride(owner, e);
        if (social != null) return !social;
        if (owner.isAlliedTo(e)) return false;
        if (e instanceof Player p) {
            return !p.isCreative() && owner instanceof Player && owner.level().getServer() != null
                    && owner.level().getServer().isPvpAllowed();
        }
        boolean attacking = e instanceof Mob m && m.getTarget() != null && m.getTarget().isAlive()
                && (m.getTarget() == owner || (m.getTarget() instanceof OwnableEntity o && owner.getUUID().equals(o.getOwnerUUID())));
        if (e instanceof Npc) return attacking;
        return attacking || e instanceof Enemy || boss(le);
    }

    /** Ataques automáticos (clones, corte do Susanoo) ficam quietos em Modo Paz ou dentro de zona segura (cidades). */
    public static boolean calm(@Nullable LivingEntity owner) {
        if (owner == null) return true;
        if (owner instanceof Player p && PeaceMode.isOn(p)) return true;
        return owner.level() instanceof ServerLevel sl && Places.isSafe(sl, BlockPos.containing(owner.position()));
    }

    public static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void sound(ServerLevel level, Vec3 p, Holder<SoundEvent> s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void actionbar(@Nullable Entity e, net.minecraft.network.chat.Component msg) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(msg, true);
    }

    /** Só na primeira chamada de {@code key} nesta conjuração neste tick (formas de área chamam o efeito por alvo). */
    public static boolean once(SpellContext ctx, String key) {
        return LfUtil.oncePerTick(ctx, "li:" + key);
    }

    // =====================================================================================
    //  Efeitos desenhados no cliente
    // =====================================================================================

    /** Teto de efeitos desenhados por tick (uma magia de área em 30 alvos não vira 30 braços esticados). */
    private static final int FX_PER_TICK = 32;
    private static long fxTick = Long.MIN_VALUE;
    private static int fxUsed;

    /** Efeito curto para quem está perto (até 96 blocos). */
    public static void fx(ServerLevel level, byte kind, Vec3 pos, Vec3 dir, int color, int life, float size) {
        long now = level.getGameTime();
        if (now != fxTick) {
            fxTick = now;
            fxUsed = 0;
        }
        if (++fxUsed > FX_PER_TICK) return;
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, 96, new LiNet.Fx(kind, pos, dir, color, life, size));
    }

    /** entidade → estados (fim em tick de jogo), para quem começa a ver a entidade depois. */
    private static final Map<Integer, long[]> STATES = new HashMap<>();

    /** Liga (ou com {@code ticks = 0} desliga) um estado visual na entidade, para ela mesma e quem a vê. */
    public static void state(Entity e, byte kind, int ticks) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        long[] arr = STATES.computeIfAbsent(e.getId(), k -> new long[LiNet.STATE_KINDS]);
        arr[kind] = ticks > 0 ? now + ticks : 0;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new LiNet.State(kind, e.getId(), ticks));
    }

    static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        long[] arr = STATES.get(e.getTarget().getId());
        if (arr == null) return;
        long now = sp.serverLevel().getGameTime();
        for (byte k = 0; k < arr.length; k++) {
            if (arr[k] > now) PacketDistributor.sendToPlayer(sp, new LiNet.State(k, e.getTarget().getId(), (int) (arr[k] - now)));
        }
    }

    /** Joga fora estados vencidos (a cada 5 s). */
    static void prune(long now) {
        for (Iterator<long[]> it = STATES.values().iterator(); it.hasNext(); ) {
            long[] arr = it.next();
            boolean any = false;
            for (long end : arr) any |= end > now;
            if (!any) it.remove();
        }
    }

    static void clearStates() {
        STATES.clear();
    }

    private LiUtil() {}
}
