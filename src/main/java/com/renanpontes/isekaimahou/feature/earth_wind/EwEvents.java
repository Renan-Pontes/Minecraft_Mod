package com.renanpontes.isekaimahou.feature.earth_wind;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.earth_wind.effect.SilentStepsEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedStoneGolem;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedSylph;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Eventos de jogo do modulo (lado servidor, exceto onde indicado). */
public final class EwEvents {
    /** Marca (NBT da entidade) dos projeteis rebatidos pela Barreira de Vento: UUID de quem rebateu. */
    private static final String DEFLECTED = "isekai_mahou_wind_deflected";
    /** Pulos extras ja usados no ar (validacao do Pulo Duplo no servidor). */
    private static final Map<UUID, Integer> AIR_JUMPS = new HashMap<>();
    /** Vibracoes "silenciadas" por Passos Silenciosos (sculk e Warden nao ouvem). */
    private static final Set<Holder<GameEvent>> QUIET_EVENTS = Set.of(GameEvent.STEP, GameEvent.HIT_GROUND,
            GameEvent.SWIM, GameEvent.SPLASH, GameEvent.FLAP, GameEvent.ELYTRA_GLIDE);

    public static void register(IEventBus bus) {
        bus.addListener(EwEvents::levelTick);
        bus.addListener(EwEvents::serverTick);
        bus.addListener(EwEvents::serverStopping);
        bus.addListener(EwEvents::playerTick);
        bus.addListener(EwEvents::onFall);
        bus.addListener(EwEvents::onProjectileImpact);
        bus.addListener(EwEvents::onVisibility);
        bus.addListener(EwEvents::onChangeTarget);
        bus.addListener(EwEvents::onVibration);
    }

    private static void levelTick(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel sl) TempBlocks.get(sl).tick(sl);
    }

    private static void serverTick(ServerTickEvent.Post e) {
        EwTasks.tick();
        long now = e.getServer().overworld().getGameTime();
        if (now % 1200 == 0) EwUtil.pruneFallGuard(now);
    }

    private static void serverStopping(ServerStoppingEvent e) {
        EwTasks.clear();
        EwUtil.clear();
        AIR_JUMPS.clear();
        SummonedStoneGolem.clearActive();
        SummonedSylph.clearActive();
    }

    private static void playerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp
                && (sp.onGround() || sp.isInWater() || sp.onClimbable() || !sp.hasEffect(EwRegistries.DOUBLE_JUMP))) {
            AIR_JUMPS.remove(sp.getUUID());
        }
    }

    // =====================================================================================
    //  Pulo Duplo (pacote do cliente)
    // =====================================================================================

    static void onDoubleJump(ServerPlayer sp) {
        MobEffectInstance eff = sp.getEffect(EwRegistries.DOUBLE_JUMP);
        if (eff == null || sp.onGround()) return;
        int used = AIR_JUMPS.merge(sp.getUUID(), 1, Integer::sum);
        if (used > eff.getAmplifier() + 1) return;
        sp.resetFallDistance();
        ServerLevel level = sp.serverLevel();
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            level.sendParticles(Vfx.cloud(), sp.getX() + Math.cos(a) * 0.6, sp.getY(), sp.getZ() + Math.sin(a) * 0.6,
                    1, 0, 0, 0, 0.02);
        }
        level.sendParticles(ParticleTypes.GUST, sp.getX(), sp.getY(), sp.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7F, 1.3F);
    }

    // =====================================================================================
    //  Queda: protecao de impulsos/pilares e Planar
    // =====================================================================================

    private static void onFall(LivingFallEvent e) {
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide) return; // o mapa de protecao e do servidor (o dano de queda tambem)
        if (EwUtil.isFallProtected(le)) {
            e.setCanceled(true);
            return;
        }
        if (le.hasEffect(EwRegistries.WIND_GLIDE) && !le.isShiftKeyDown()) e.setDamageMultiplier(0.0F);
        else if (le.hasEffect(EwRegistries.WIND_GLIDE)) e.setDamageMultiplier(e.getDamageMultiplier() * 0.5F);
    }

    // =====================================================================================
    //  Desviar Projeteis (Barreira de Vento)
    // =====================================================================================

    private static void onProjectileImpact(ProjectileImpactEvent e) {
        Projectile p = e.getProjectile();
        if (p.level().isClientSide || !(e.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity target)) return;
        Entity shooter = p.getOwner();
        // um projetil ja rebatido nunca acerta quem o rebateu
        if (p.getPersistentData().hasUUID(DEFLECTED) && p.getPersistentData().getUUID(DEFLECTED).equals(target.getUUID())) {
            e.setCanceled(true);
            return;
        }
        if (!target.hasEffect(EwRegistries.WIND_BARRIER) || shooter == target || friendly(shooter, target)) return;
        e.setCanceled(true);
        p.getPersistentData().putUUID(DEFLECTED, target.getUUID());
        Vec3 v = p.getDeltaMovement();
        double speed = Math.max(0.8, v.length());
        Vec3 dir;
        if (shooter != null && shooter.isAlive() && shooter.level() == target.level() && shooter.distanceToSqr(target) < 64 * 64) {
            dir = shooter.getEyePosition().subtract(p.position()).normalize();
        } else {
            dir = v.lengthSqr() > 1.0E-6 ? v.scale(-1).normalize() : target.getLookAngle();
        }
        p.setDeltaMovement(dir.scale(speed));
        float yRot = (float) (Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG);
        float xRot = (float) (Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        p.setYRot(yRot);
        p.setXRot(xRot);
        p.yRotO = yRot;
        p.xRotO = xRot;
        // o rebatido passa a ser "dono" (o dano conta para ele). Tridentes ficam com o dono original:
        // senao a Lealdade levaria o tridente de outra pessoa para quem rebateu.
        if (!(p instanceof ThrownTrident)) p.setOwner(target);
        // tira o projetil de dentro da caixa do alvo para nao acertar de novo no proximo tick
        p.setPos(p.position().add(dir.scale(target.getBbWidth() * 0.5 + 0.6)));
        p.hasImpulse = true;
        if (p.level() instanceof ServerLevel sl) {
            Vec3 at = hit.getLocation();
            sl.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            sl.sendParticles(Vfx.cloud(), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.05);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 1.0F, 1.1F);
        }
    }

    /** Projeteis de aliados (cura/bencao de outro jogador, flechas de um companheiro) nao sao rebatidos. */
    private static boolean friendly(Entity shooter, LivingEntity target) {
        if (shooter == null) return false;
        if (shooter.isAlliedTo(target) || target.isAlliedTo(shooter)) return true;
        return shooter instanceof Player && target instanceof Player && target.getServer() != null && !target.getServer().isPvpAllowed();
    }

    // =====================================================================================
    //  Passos Silenciosos
    // =====================================================================================

    private static void onVisibility(LivingEvent.LivingVisibilityEvent e) {
        if (!e.getEntity().hasEffect(EwRegistries.SILENT_STEPS)) return;
        if (e.getLookingEntity() instanceof Mob mob && mob.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE)) {
            double range = Math.max(SilentStepsEffect.NOTICE_RANGE, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
            e.modifyVisibility(SilentStepsEffect.NOTICE_RANGE / range);
        } else {
            e.modifyVisibility(0.25);
        }
    }

    private static void onChangeTarget(LivingChangeTargetEvent e) {
        LivingEntity target = e.getNewAboutToBeSetTarget();
        if (target == null || !target.hasEffect(EwRegistries.SILENT_STEPS)) return;
        LivingEntity mob = e.getEntity();
        double r = SilentStepsEffect.NOTICE_RANGE + 1;
        if (mob.distanceToSqr(target) > r * r && mob.getLastHurtByMob() != target) e.setCanceled(true);
    }

    private static void onVibration(VanillaGameEvent e) {
        if (e.getCause() instanceof LivingEntity le && le.hasEffect(EwRegistries.SILENT_STEPS)
                && QUIET_EVENTS.contains(e.getVanillaEvent())) {
            e.setCanceled(true);
        }
    }

    private EwEvents() {}
}
