package com.renanpontes.isekaimahou.feature.legends_h.jojo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.legends_h.LhNet;
import com.renanpontes.isekaimahou.feature.legends_h.LhRegistry;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * As regras do KING CRIMSON e do EPITÁFIO (servidor, em memória):
 * <ul>
 *   <li><b>Fora do tempo</b> (King Crimson em si mesmo): invisível, ninguém mira você, golpes/magias/projéteis
 *       atravessam — e você também não acerta ninguém. Ao voltar: reaparece com um clarão, o PRIMEIRO golpe em até
 *       3 s sai ×1,5 e por 3 s você vê o Epitáfio (para onde os inimigos vão).</li>
 *   <li><b>Tempo apagado</b> (King Crimson num alvo): ele continua andando "no automático", mas não mira e o que ele
 *       faz não acontece (os ataques dele não causam dano); deixa rastros de imagens. Jogadores: véu vermelho, sem
 *       travar o controle.</li>
 *   <li><b>Epitáfio</b> (aumento): quem você atinge fica "previsto" — você vê a linha do próximo movimento dele e o
 *       primeiro ataque dele em você é ESQUIVADO (um passo para o lado).</li>
 * </ul>
 * Dano de vazio/{@code /kill} sempre passa.
 */
public final class TimeSkip {
    public static final int CRIMSON = 0xB01040;
    public static final int GHOST = 0xD0306A;
    public static final float FIRST_STRIKE = 1.5F;

    private static final class Phase {
        final LivingEntity e;
        final Vec3 start;
        long end;

        Phase(LivingEntity e, long end) {
            this.e = e;
            this.start = e.position();
            this.end = end;
        }
    }

    private static final class Erased {
        final LivingEntity e;
        long end;

        Erased(LivingEntity e, long end) {
            this.e = e;
            this.end = end;
        }
    }

    private static final class Sight {
        final LivingEntity target;
        long end;
        boolean dodge;

        Sight(LivingEntity target, long end, boolean dodge) {
            this.target = target;
            this.end = end;
            this.dodge = dodge;
        }
    }

    private static final Map<UUID, Phase> PHASED = new HashMap<>();
    private static final Map<UUID, Erased> ERASED = new HashMap<>();
    private static final Map<UUID, Long> STRIKE = new HashMap<>();
    private static final Map<UUID, Map<UUID, Sight>> SIGHT = new HashMap<>();

    // =====================================================================================
    //  API
    // =====================================================================================

    public static boolean phased(@Nullable Entity e) {
        return e != null && PHASED.containsKey(e.getUUID());
    }

    public static boolean erased(@Nullable Entity e) {
        return e != null && ERASED.containsKey(e.getUUID());
    }

    /** King Crimson em si mesmo: sai do tempo por {@code ticks}. */
    public static void phase(LivingEntity e, int ticks) {
        if (!(e.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Phase ph = PHASED.get(e.getUUID());
        if (ph != null) {
            ph.end = Math.max(ph.end, now + ticks);
        } else {
            PHASED.put(e.getUUID(), new Phase(e, now + ticks));
        }
        e.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks + 2, 0, false, false, false));
        e.addEffect(new MobEffectInstance(LhRegistry.TIME_SKIP, ticks, 0, false, false, true));
        LhUtil.screenTracking(e, LhNet.PHASE, ticks);
        // Ninguém mais mira em você
        for (Mob m : level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(32), m -> m.getTarget() == e)) {
            m.setTarget(null);
            m.getNavigation().stop();
        }
        skipFor(level, e.position(), e, 8);
        Vec3 c = LhUtil.center(e);
        ElementFx.shockwave(level, c, CRIMSON, 7F, 14, null);
        ElementFx.flash(level, c, 0xFF2050, 3F);
        level.sendParticles(LhUtil.dust(GHOST, 1.6F), c.x, c.y, c.z, 24, 0.4, 0.8, 0.4, 0);
        LhUtil.sound(level, c, SoundEvents.RESPAWN_ANCHOR_DEPLETE, 1.2F, 0.6F);
        LhUtil.sound(level, c, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.5F);
    }

    /** King Crimson num alvo: o tempo dele é apagado por {@code ticks}. */
    public static void erase(LivingEntity e, int ticks) {
        if (!(e.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Erased er = ERASED.get(e.getUUID());
        if (er != null) er.end = Math.max(er.end, now + ticks);
        else ERASED.put(e.getUUID(), new Erased(e, now + ticks));
        if (e instanceof Mob m) {
            m.setTarget(null);
            m.setAggressive(false);
        }
        if (e instanceof ServerPlayer sp) {
            LhUtil.screen(sp, LhNet.ERASED, sp.getId(), ticks);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_h.erased_you", String.format("%.1f", ticks / 20F))
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
        Vec3 c = LhUtil.center(e);
        level.sendParticles(LhUtil.dust(GHOST, 1.4F), c.x, c.y, c.z, 14, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4,
                e.getBbWidth() * 0.5, 0);
        LhUtil.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 0.5F);
    }

    /** Epitáfio: {@code seer} prevê {@code target} por {@code ticks}; com {@code dodge}, esquiva o 1º ataque dele. */
    public static void foresee(LivingEntity seer, LivingEntity target, int ticks, boolean dodge) {
        if (!(seer instanceof ServerPlayer) || seer == target || !(seer.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Map<UUID, Sight> map = SIGHT.computeIfAbsent(seer.getUUID(), k -> new HashMap<>());
        if (map.size() >= 16 && !map.containsKey(target.getUUID())) return;
        Sight s = map.get(target.getUUID());
        if (s == null) map.put(target.getUUID(), new Sight(target, now + ticks, dodge));
        else {
            s.end = Math.max(s.end, now + ticks);
            s.dodge |= dodge;
        }
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity src = e.getSource().getEntity();
        // Fora do tempo: nenhum ATAQUE te acerta (lava, queda e afogamento continuam valendo — não é imortalidade)
        if (phased(victim) && src != null && src != victim) {
            e.setCanceled(true);
            return;
        }
        if (src instanceof LivingEntity a && a != victim) {
            if (phased(a) || erased(a)) {
                e.setCanceled(true);
                return;
            }
            // Epitáfio: o golpe já estava previsto
            Map<UUID, Sight> map = SIGHT.get(victim.getUUID());
            Sight s = map == null ? null : map.get(a.getUUID());
            if (s != null && s.dodge && s.end > victim.level().getGameTime()) {
                s.dodge = false;
                e.setCanceled(true);
                sidestep(victim, a);
                return;
            }
            // Primeiro golpe depois do tempo apagado
            Long until = STRIKE.get(a.getUUID());
            if (until != null) {
                STRIKE.remove(a.getUUID());
                if (until >= victim.level().getGameTime()) {
                    e.setAmount(e.getAmount() * FIRST_STRIKE);
                    if (victim.level() instanceof ServerLevel sl) {
                        Vec3 c = LhUtil.center(victim);
                        ElementFx.flash(sl, c, 0xFF2050, 2F);
                        sl.sendParticles(LhUtil.dust(CRIMSON, 1.5F), c.x, c.y, c.z, 16, 0.3, 0.5, 0.3, 0);
                        LhUtil.sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.7F);
                    }
                }
            }
        }
    }

    /** Projéteis atravessam quem está fora do tempo. */
    public static void onProjectileImpact(ProjectileImpactEvent e) {
        if (e.getRayTraceResult() instanceof EntityHitResult hit && phased(hit.getEntity())) e.setCanceled(true);
    }

    public static void onChangeTarget(LivingChangeTargetEvent e) {
        if (phased(e.getNewAboutToBeSetTarget()) || erased(e.getEntity())) e.setNewAboutToBeSetTarget(null);
    }

    private static void sidestep(LivingEntity victim, LivingEntity attacker) {
        Vec3 away = victim.position().subtract(attacker.position());
        Vec3 side = new Vec3(-away.z, 0, away.x);
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(victim.getRandom().nextBoolean() ? 0.75 : -0.75);
        victim.setDeltaMovement(victim.getDeltaMovement().add(side.x, 0.15, side.z));
        victim.hurtMarked = true;
        if (victim.level() instanceof ServerLevel sl) {
            Vec3 c = LhUtil.center(victim);
            sl.sendParticles(LhUtil.dust(GHOST, 1.2F), c.x, c.y, c.z, 10, 0.3, 0.6, 0.3, 0);
            LhUtil.sound(sl, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9F, 1.3F);
        }
        LhUtil.actionbar(victim, Component.translatable("message.isekai_mahou.legends_h.epitaph_dodge").withStyle(ChatFormatting.RED));
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    public static void tick(MinecraftServer server) {
        if (PHASED.isEmpty() && ERASED.isEmpty() && SIGHT.isEmpty() && STRIKE.isEmpty()) return;
        long tick = server.getTickCount();
        for (Iterator<Phase> it = PHASED.values().iterator(); it.hasNext(); ) {
            Phase ph = it.next();
            LivingEntity e = ph.e;
            if (!(e.level() instanceof ServerLevel level) || e.isRemoved() || !e.isAlive()) {
                it.remove();
                LhUtil.screenTracking(e, LhNet.PHASE, 0);
                continue;
            }
            if (level.getGameTime() >= ph.end) {
                it.remove();
                resume(level, ph);
                continue;
            }
            for (Mob m : level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(32), m -> m.getTarget() == e)) m.setTarget(null);
            // Para quem está fora do tempo, os inimigos deixam imagens do que vão fazer
            if (tick % 4 == 0 && e instanceof ServerPlayer sp) {
                int shown = 0;
                for (Mob m : level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(16), m -> m.isAlive() && m != e)) {
                    if (++shown > 12) break;
                    Vec3 c = LhUtil.center(m);
                    Vec3 future = c.add(m.getDeltaMovement().multiply(12, 0, 12));
                    LhUtil.privateLine(sp, c, future.add(0, 0.01, 0), GHOST, 0.8F, 8);
                }
            }
        }
        for (Iterator<Erased> it = ERASED.values().iterator(); it.hasNext(); ) {
            Erased er = it.next();
            LivingEntity e = er.e;
            if (!(e.level() instanceof ServerLevel level) || e.isRemoved() || !e.isAlive() || level.getGameTime() >= er.end) {
                it.remove();
                continue;
            }
            if (e instanceof Mob m && m.getTarget() != null) m.setTarget(null);
            if (tick % 3 == 0) {
                // Rastro de imagens: o que ele "faz" enquanto o tempo dele não existe
                Vec3 c = LhUtil.center(e);
                level.sendParticles(LhUtil.dust(GHOST, 1.1F), c.x, c.y, c.z, 3, e.getBbWidth() * 0.35, e.getBbHeight() * 0.35,
                        e.getBbWidth() * 0.35, 0);
            }
        }
        if (tick % 5 == 0) tickSight(server);
        if (tick % 100 == 0) {
            long now = server.overworld().getGameTime();
            STRIKE.values().removeIf(t -> t + 200 < now);
        }
    }

    private static void tickSight(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Map<UUID, Sight>>> it = SIGHT.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<UUID, Sight>> en = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp == null || !sp.isAlive()) {
                it.remove();
                continue;
            }
            long now = sp.serverLevel().getGameTime();
            LivingEntity nearest = null;
            double nd = Double.MAX_VALUE;
            for (Iterator<Sight> si = en.getValue().values().iterator(); si.hasNext(); ) {
                Sight s = si.next();
                LivingEntity t = s.target;
                if (s.end < now || t.isRemoved() || !t.isAlive() || t.level() != sp.level() || t.distanceToSqr(sp) > 48 * 48) {
                    si.remove();
                    continue;
                }
                Vec3 c = LhUtil.center(t);
                // O "futuro": para onde ele vai no próximo segundo...
                LhUtil.privateLine(sp, c, c.add(t.getDeltaMovement().multiply(20, 4, 20)), GHOST, 0.7F, 10);
                // ...e, se vem em você, a linha vermelha do golpe
                if (t instanceof Mob m && m.getTarget() == sp) {
                    LhUtil.privateLine(sp, c, LhUtil.center(sp), 0xFF2030, 0.9F, 16);
                    double d = t.distanceToSqr(sp);
                    if (d < nd) {
                        nd = d;
                        nearest = t;
                    }
                }
            }
            if (en.getValue().isEmpty()) {
                it.remove();
                continue;
            }
            if (nearest != null && server.getTickCount() % 10 == 0) {
                LhUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_h.epitaph_dir", direction(sp, nearest))
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    /** "pela frente/esquerda/direita/costas/de cima" — relativo para onde o jogador olha. */
    private static Component direction(ServerPlayer sp, LivingEntity from) {
        Vec3 to = from.position().subtract(sp.position());
        if (to.y > 3 && to.y * to.y > to.x * to.x + to.z * to.z) return Component.translatable("message.isekai_mahou.legends_h.dir_above");
        double ang = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-to.x, to.z)) - sp.getYRot());
        String key = Math.abs(ang) <= 45 ? "front" : Math.abs(ang) >= 135 ? "back" : ang > 0 ? "right" : "left";
        return Component.translatable("message.isekai_mahou.legends_h.dir_" + key);
    }

    /** O tempo volta: o conjurador reaparece onde está agora. */
    private static void resume(ServerLevel level, Phase ph) {
        LivingEntity e = ph.e;
        e.removeEffect(MobEffects.INVISIBILITY);
        e.removeEffect(LhRegistry.TIME_SKIP);
        LhUtil.screenTracking(e, LhNet.PHASE, 0);
        long now = level.getGameTime();
        STRIKE.put(e.getUUID(), now + 60);
        Vec3 c = LhUtil.center(e);
        // Imagem que fica para trás no ponto de partida e clarão onde ele "aparece"
        if (ph.start.distanceToSqr(e.position()) > 4) {
            Vec3 s = ph.start.add(0, e.getBbHeight() * 0.5, 0);
            level.sendParticles(LhUtil.dust(GHOST, 1.3F), s.x, s.y, s.z, 18, 0.3, 0.7, 0.3, 0);
        }
        ElementFx.flash(level, c, 0xFF2050, 4F);
        ElementFx.shockwave(level, c, CRIMSON, 9F, 12, null);
        level.sendParticles(LhUtil.dust(CRIMSON, 1.8F), c.x, c.y, c.z, 30, 0.5, 0.9, 0.5, 0);
        LhUtil.sound(level, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2F, 0.6F);
        LhUtil.sound(level, c, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 1.6F);
        skipFor(level, e.position(), e, 6);
        // Epitáfio curto: quem está em volta fica previsto por 3 s (sem esquiva)
        for (Mob m : level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(16), m -> m.isAlive())) {
            foresee(e, m, 60, false);
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
        }
        LhUtil.actionbar(e, Component.translatable("message.isekai_mahou.legends_h.time_resumes").withStyle(ChatFormatting.DARK_RED));
    }

    /** Corte rápido na tela de quem está perto (o mundo "pulou"). */
    private static void skipFor(ServerLevel level, Vec3 p, Entity except, int ticks) {
        for (ServerPlayer sp : level.getPlayers(pl -> pl != except && pl.distanceToSqr(p) < 24 * 24)) {
            LhUtil.screen(sp, LhNet.SKIP, except.getId(), ticks);
        }
    }

    public static void forget(UUID id) {
        Phase ph = PHASED.remove(id);
        if (ph != null) ph.e.removeEffect(MobEffects.INVISIBILITY);
        ERASED.remove(id);
        STRIKE.remove(id);
        SIGHT.remove(id);
    }

    public static void clear() {
        PHASED.clear();
        ERASED.clear();
        STRIKE.clear();
        SIGHT.clear();
    }

    private TimeSkip() {}
}
