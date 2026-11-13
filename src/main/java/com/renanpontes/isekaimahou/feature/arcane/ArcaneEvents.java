package com.renanpontes.isekaimahou.feature.arcane;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.entity.SpellProjectileEntity;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Eventos de jogo do módulo: Reflexão Arcana, críticos de flecha (gatilho), Atordoar/Marcar no dano, ticks dos rastreadores. */
public final class ArcaneEvents {

    public static void onServerTick(ServerTickEvent.Post event) {
        Telekinesis.tick(event.getServer());
        StatusAuras.tick(event.getServer());
        RecallMarks.tickParticles(event.getServer());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        Telekinesis.clear();
        StatusAuras.clear();
        ArcaneTriggers.clear();
    }

    /**
     * Projétil acertando alguém com Reflexão Arcana: o impacto é cancelado e o projétil volta mirando em quem o lançou
     * (e passa a ser de quem refletiu). Projéteis de magia do mod são recriados com a magia "trocando de dono".
     * Também detecta flechas críticas para o gatilho Ao Acertar Crítico.
     */
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile proj = event.getProjectile();
        if (!(proj.level() instanceof ServerLevel level) || !(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        Entity hitEntity = hit.getEntity();

        // Anzol não é ataque: pescar alguém com Reflexão funciona normalmente
        if (hitEntity instanceof LivingEntity target && target.hasEffect(ArcaneRegistries.REFLECT) && !(proj instanceof FishingHook)) {
            Entity owner = proj.getOwner();
            event.setCanceled(true);
            if (owner == target) return; // já refletido por este alvo: atravessa sem acertar de novo
            reflect(level, proj, target, owner);
            return;
        }

        if (proj instanceof AbstractArrow arrow && arrow.isCritArrow() && arrow.getOwner() instanceof ServerPlayer p
                && hitEntity instanceof LivingEntity && hitEntity != p) {
            ArcaneTriggers.react(p, ArcaneTriggers.ON_CRIT);
        }
    }

    /**
     * Complementos de dano dos glifos de controle:
     * <ul>
     *   <li>Atordoado: mobs não conseguem golpear corpo a corpo (o efeito do núcleo só deixa o ataque de JOGADORES lento).</li>
     *   <li>Marcar: o Exposto do núcleo só aumenta dano de magia; no alvo marcado pelo glifo, golpes e flechas de
     *       jogadores também ficam +25% por nível mais fortes.</li>
     * </ul>
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        DamageSource src = event.getSource();
        if (src.getEntity() instanceof Mob m && src.getDirectEntity() == m && m.hasEffect(CoreRegistries.STUNNED)
                && (src.is(DamageTypes.MOB_ATTACK) || src.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) {
            event.setCanceled(true);
            return;
        }
        if (src.getEntity() instanceof Player && !src.is(CoreRegistries.SPELL_DAMAGE) && StatusAuras.has(target, StatusAuras.Kind.MARK)) {
            MobEffectInstance exposed = target.getEffect(CoreRegistries.EXPOSED);
            if (exposed != null) event.setAmount(event.getAmount() * (1.0F + 0.25F * (exposed.getAmplifier() + 1)));
        }
    }

    private static void reflect(ServerLevel level, Projectile proj, LivingEntity reflector, Entity owner) {
        Vec3 vel = proj.getDeltaMovement();
        double speed = Math.max(0.6, vel.length());
        Vec3 dir;
        if (owner != null && owner.isAlive() && owner.level() == level && owner.distanceToSqr(proj) < 96 * 96) {
            dir = owner.getEyePosition().subtract(proj.position()).normalize();
        } else {
            dir = vel.lengthSqr() > 1.0E-6 ? vel.normalize().scale(-1) : reflector.getLookAngle();
        }
        Vec3 newVel = dir.scale(speed);
        Vec3 at = proj.position();

        if (proj instanceof SpellProjectileEntity spell) {
            // A magia vira do refletor: aliados/inimigos passam a ser contados a partir dele
            CompoundTag tag = spell.saveWithoutId(new CompoundTag());
            SpellContext old = SpellContext.load(level, tag.getCompound("Spell"));
            spell.discard();
            if (old != null) {
                SpellContext mine = new SpellContext(level, reflector, old.element(), old.recipe(), old.spell());
                mine.setPowerMult(old.powerMult());
                SpellProjectileEntity copy = SpellProjectileEntity.create(mine, reflector);
                copy.setPos(at.add(dir.scale(0.6)));
                copy.setDeltaMovement(newVel);
                level.addFreshEntity(copy);
            }
        } else if (proj instanceof ShulkerBullet) {
            // A bala do shulker sempre volta a perseguir o mesmo alvo: a reflexão a desfaz
            proj.discard();
        } else {
            // O tridente continua do dono (senão a Lealdade o levaria para quem refletiu e o jogador o perderia)
            Entity newOwner = proj instanceof ThrownTrident ? owner : reflector;
            proj.deflect((p, e, random) -> {
                p.setDeltaMovement(newVel);
                p.hasImpulse = true;
            }, reflector, newOwner, false);
            proj.setPos(at.add(dir.scale(0.6)));
        }

        level.sendParticles(Vfx.magicCrit(), at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.3);
        level.sendParticles(ArcaneUtil.dust(0xC9A6FF, 1.2F), at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, reflector.getSoundSource(), 1.0F, 1.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, reflector.getSoundSource(), 0.6F, 1.5F);
    }

    private ArcaneEvents() {}
}
