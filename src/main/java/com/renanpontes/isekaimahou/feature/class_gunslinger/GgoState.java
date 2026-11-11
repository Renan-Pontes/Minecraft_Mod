package com.renanpontes.isekaimahou.feature.class_gunslinger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Estado de servidor das técnicas "Gun Gale" do Pistoleiro (não salvo; limpo ao sair/parar):
 * <ul>
 *   <li><b>Defesa da Espada de Fótons</b>: janela curta em que os projéteis que iam acertar você são CORTADOS e
 *   rebatidos para quem atirou;</li>
 *   <li><b>Linha de Bala</b>: linhas vermelhas (só você vê) dos inimigos que miram em você e dos projéteis vindo na sua
 *   direção, e as cargas de ESQUIVA (o golpe previsto não acerta; com efeitos na magia, você revida);</li>
 *   <li><b>Camuflagem Óptica</b>: os inimigos perdem você de vista; o primeiro ataque sai da camuflagem como emboscada.</li>
 * </ul>
 */
public final class GgoState {
    public static final int RED = 0xFF2A2A;
    private static final DustParticleOptions LINE = new DustParticleOptions(new org.joml.Vector3f(1.0F, 0.12F, 0.12F), 0.55F);

    /** Defesa da espada: jogador → tick até quando corta projéteis. */
    private static final Map<UUID, Long> DEFLECT = new HashMap<>();

    /** Linha de Bala: sessão (contexto para o revide, fim, cargas, tick da última esquiva). */
    private static final class Lines {
        final SpellContext ctx;
        final long until;
        int charges;
        long lastDodge = -100;

        Lines(SpellContext ctx, long until, int charges) {
            this.ctx = ctx;
            this.until = until;
            this.charges = charges;
        }
    }

    private static final Map<UUID, Lines> LINES = new HashMap<>();
    /** Camuflagem Óptica: jogador → fim. */
    private static final Map<UUID, Long> CAMO = new HashMap<>();
    /** Quem já atacou saindo da camuflagem neste instante (o manto desliga logo depois). */
    private static final java.util.Set<UUID> AMBUSH = new java.util.HashSet<>();
    public static final float AMBUSH_MULT = 1.5F;

    // =====================================================================================
    //  API
    // =====================================================================================

    public static void deflect(LivingEntity e, int ticks) {
        long until = e.level().getGameTime() + ticks;
        DEFLECT.merge(e.getUUID(), until, Math::max);
    }

    public static void startLines(ServerPlayer sp, SpellContext ctx, int ticks, int charges) {
        LINES.put(sp.getUUID(), new Lines(ctx, sp.serverLevel().getGameTime() + ticks, charges));
    }

    public static int lineCharges(ServerPlayer sp) {
        Lines l = LINES.get(sp.getUUID());
        return l == null ? 0 : l.charges;
    }

    public static void startCamo(LivingEntity e, int ticks) {
        CAMO.put(e.getUUID(), e.level().getGameTime() + ticks);
    }

    public static boolean camouflaged(@Nullable Entity e) {
        if (e == null) return false;
        Long until = CAMO.get(e.getUUID());
        return until != null && e.level().getGameTime() <= until;
    }

    /** Sai da camuflagem (com o brilho de "desligar" o manto). */
    public static void breakCamo(LivingEntity e) {
        if (CAMO.remove(e.getUUID()) == null) return;
        e.removeEffect(MobEffects.INVISIBILITY);
        if (e.level() instanceof ServerLevel sl) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            sl.sendParticles(GsFx.dust(0xBFE8FF, 0.9F), c.x, c.y, c.z, 24, 0.3, 0.7, 0.3, 0);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 12, 0.3, 0.6, 0.3, 0.05);
            GsFx.sound(sl, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 0.7F);
        }
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    /** Todo tick: linhas vermelhas e manto da camuflagem; limpa sessões vencidas. */
    public static void onServerTick(ServerTickEvent.Post e) {
        if (LINES.isEmpty() && CAMO.isEmpty()) return;
        for (Map.Entry<UUID, Lines> entry : new ArrayList<>(LINES.entrySet())) {
            Lines l = entry.getValue();
            if (!(l.ctx.caster() instanceof ServerPlayer sp) || !sp.isAlive() || sp.serverLevel().getGameTime() > l.until) {
                if (l.ctx.caster() instanceof ServerPlayer sp2 && sp2.isAlive()) {
                    sp2.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.lines_end").withStyle(ChatFormatting.GRAY), true);
                }
                LINES.remove(entry.getKey(), l);
                continue;
            }
            if (sp.tickCount % 4 == 0) drawLines(sp);
        }
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(CAMO.entrySet())) {
            Entity ent = null;
            for (ServerLevel sl : e.getServer().getAllLevels()) {
                ent = sl.getEntity(entry.getKey());
                if (ent != null) break;
            }
            if (!(ent instanceof LivingEntity le) || !le.isAlive() || le.level().getGameTime() > entry.getValue()) {
                CAMO.remove(entry.getKey());
                if (ent instanceof LivingEntity le2 && le2.isAlive()) breakCamo(le2);
                continue;
            }
            if (le.tickCount % 10 == 0 && le.level() instanceof ServerLevel sl) {
                // o manto: poucos reflexos tremendo no ar
                Vec3 c = le.position().add(0, le.getBbHeight() * 0.5, 0);
                sl.sendParticles(GsFx.dust(0xD8F0FF, 0.4F), c.x, c.y, c.z, GsFx.reduced() ? 1 : 3, 0.25, 0.6, 0.25, 0);
            }
        }
    }

    /** Linhas vermelhas para quem mira em você e para os projéteis vindo — só o dono da Linha de Bala vê. */
    private static void drawLines(ServerPlayer sp) {
        ServerLevel sl = sp.serverLevel();
        Vec3 chest = sp.position().add(0, sp.getBbHeight() * 0.6, 0);
        int budget = 6;
        for (Mob mob : sl.getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(24), m -> m.isAlive() && m.getTarget() == sp)) {
            if (budget-- <= 0) break;
            line(sp, mob.getEyePosition(), chest, 16);
        }
        for (Projectile p : sl.getEntitiesOfClass(Projectile.class, sp.getBoundingBox().inflate(24), p -> p.isAlive() && !p.onGround())) {
            if (budget-- <= 0) break;
            Entity owner = p.getOwner();
            if (owner == sp || (owner != null && GunCombat.isAlly(sp, owner))) continue;
            Vec3 v = p.getDeltaMovement();
            if (v.lengthSqr() < 0.04) continue;
            Vec3 to = chest.subtract(p.position());
            if (to.normalize().dot(v.normalize()) < 0.85) continue;
            line(sp, p.position(), p.position().add(v.normalize().scale(Math.min(20, to.length() + 2))), 14);
        }
    }

    private static void line(ServerPlayer sp, Vec3 from, Vec3 to, int max) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.5) return;
        int steps = (int) Math.min(max, Math.max(3, len * 0.8));
        ServerLevel sl = sp.serverLevel();
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) steps));
            sl.sendParticles(sp, LINE, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Camuflagem: o ataque de quem está camuflado é EMBOSCADA (×1,5) e desliga o manto 2 ticks depois (a rajada inteira
     * conta). Linha de Bala: esquiva do golpe previsto. Levar dano não quebra a camuflagem (só atacar).
     */
    public static void onIncoming(LivingIncomingDamageEvent e) {
        if (e.getSource().getEntity() instanceof LivingEntity atk && atk != e.getEntity() && camouflaged(atk)) {
            e.setAmount(e.getAmount() * AMBUSH_MULT);
            if (AMBUSH.add(atk.getUUID())) {
                if (atk instanceof ServerPlayer asp) MagicApi.addCounter(asp, GsTechniques.CAMO_SHOTS, 1);
                if (atk.level() instanceof ServerLevel sl) {
                    Vec3 c = e.getEntity().position().add(0, e.getEntity().getBbHeight() * 0.5, 0);
                    sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 12, 0.25, 0.3, 0.25, 0.4);
                }
                GsTechniques.actionbar(atk, Component.translatable("message.isekai_mahou.gunslinger.ambush"), 0x9FB8C8);
                SpellScheduler.schedule(2, () -> {
                    AMBUSH.remove(atk.getUUID());
                    breakCamo(atk);
                });
            }
        }
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Lines l = LINES.get(sp.getUUID());
        if (l == null || l.charges <= 0) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || e.getSource().is(DamageTypeTags.IS_FALL)) return;
        Entity attacker = e.getSource().getEntity();
        if (attacker == null || attacker == sp || GunCombat.isAlly(sp, attacker)) return;
        long now = sp.serverLevel().getGameTime();
        e.setCanceled(true);
        if (now - l.lastDodge < 10) return; // o mesmo golpe (vários acertos no tick) conta uma esquiva só
        l.lastDodge = now;
        l.charges--;
        dodge(sp, attacker, l);
    }

    private static void dodge(ServerPlayer sp, Entity attacker, Lines l) {
        ServerLevel sl = sp.serverLevel();
        Vec3 from = attacker.position().subtract(sp.position()).multiply(1, 0, 1);
        Vec3 side = from.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : new Vec3(-from.z, 0, from.x).normalize();
        if (sp.getRandom().nextBoolean()) side = side.scale(-1);
        Vec3 c = sp.position().add(0, 1, 0);
        sl.sendParticles(GsFx.dust(0xFFFFFF, 1.0F), c.x, c.y, c.z, 14, 0.25, 0.6, 0.25, 0);
        sl.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY() + 0.1, sp.getZ(), 6, 0.2, 0.05, 0.2, 0.02);
        sp.push(side.x * 0.8, 0.15, side.z * 0.8);
        sp.hurtMarked = true;
        sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.8F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.dodge", l.charges).withColor(0xFF7A7A), true);
        // com efeitos na magia: revida em quem você esquivou
        if (!l.ctx.spell().effects().isEmpty() && attacker instanceof LivingEntity target && target.isAlive()) {
            SpellScheduler.schedule(3, () -> {
                if (!target.isAlive() || !sp.isAlive() || target.level() != sl) return;
                Vec3 muzzle = GsFx.muzzle(sp, target.getEyePosition().subtract(sp.getEyePosition()).normalize(), 1);
                GsFx.tracer(sl, muzzle, target.getEyePosition(), RED, 0.9F);
                GsFx.shot(sl, muzzle, target.getEyePosition().subtract(muzzle).normalize(), RED, GunKind.PISTOL);
                target.invulnerableTime = 0;
                l.ctx.copy(l.ctx.powerMult() * 0.8F).resolveOnEntity(target, null);
            });
        }
    }

    /** Espada de Fótons: o projétil que ia acertar quem está cortando é rebatido de volta. */
    public static void onProjectileImpact(ProjectileImpactEvent e) {
        if (!(e.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity le)) return;
        Long until = DEFLECT.get(le.getUUID());
        if (until == null || le.level().getGameTime() > until) return;
        Projectile p = e.getProjectile();
        if (p.getOwner() == le) return;
        e.setCanceled(true);
        reflect(le, p);
    }

    /** Rebate um projétil (corte de fótons): volta para quem atirou, mais rápido, e passa a ser de quem cortou. */
    public static void reflect(LivingEntity cutter, Projectile p) {
        Entity shooter = p.getOwner();
        Vec3 v = p.getDeltaMovement();
        double speed = Math.max(0.8, v.length() * 1.2);
        Vec3 dir = shooter != null ? shooter.getEyePosition().subtract(p.position()).normalize() : v.scale(-1).normalize();
        p.setOwner(cutter);
        p.setDeltaMovement(dir.scale(speed));
        p.hasImpulse = true;
        p.hurtMarked = true;
        if (p.level() instanceof ServerLevel sl) {
            Vec3 at = p.position();
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.1, 0.1, 0.1, 0.2);
            sl.sendParticles(GsFx.dust(0xB98CFF, 1.0F), at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0);
            GsFx.sound(sl, at, SoundEvents.ANVIL_LAND, 0.4F, 2.0F);
        }
        if (cutter instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.deflect").withColor(0xB98CFF), true);
        }
    }

    /** Camuflagem: criaturas não escolhem como alvo quem está camuflado (a não ser coladas, a 3 blocos). */
    public static void onChangeTarget(LivingChangeTargetEvent e) {
        LivingEntity t = e.getNewAboutToBeSetTarget();
        if (t == null || !camouflaged(t) || !(e.getEntity() instanceof Enemy)) return;
        if (e.getEntity().distanceToSqr(t) > 9) e.setCanceled(true);
    }

    public static void forget(UUID id) {
        DEFLECT.remove(id);
        LINES.remove(id);
        CAMO.remove(id);
        AMBUSH.remove(id);
    }

    public static void clear() {
        DEFLECT.clear();
        LINES.clear();
        CAMO.clear();
        AMBUSH.clear();
    }

    /** Usado pela Camuflagem para "apagar" você da mira de quem já te via. */
    public static void dropAggro(LivingEntity e, double radius) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        for (Mob m : sl.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(radius), m -> m.getTarget() == e)) {
            if (m.distanceToSqr(e) > 9) m.setTarget(null);
        }
    }

    /** Recurso atual do jogador (para o Dedo Espiritual). */
    public static float focusFraction(ServerPlayer sp) {
        var d = MagicApi.get(sp);
        return d.maxMana() <= 0 ? 0F : Math.max(0F, Math.min(1F, d.mana() / (float) d.maxMana()));
    }

    private GgoState() {}
}
