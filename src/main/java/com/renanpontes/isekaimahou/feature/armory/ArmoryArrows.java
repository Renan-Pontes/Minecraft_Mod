package com.renanpontes.isekaimahou.feature.armory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryWeapon;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Flechas e virotes do Arsenal (servidor). A arma que disparou fica gravada na flecha pelo vanilla
 * ({@link AbstractArrow#getWeaponItem()}, salvo no mundo), então tudo aqui vale também para as flechas da técnica Flecha:
 * <ul>
 *   <li>Dano do tier ({@link WeaponKind#projectileDamage}); a Besta Arcana ainda multiplica pelo Poder Mágico do atirador
 *       (até ×2).</li>
 *   <li>Rastro colorido no ar (vento no Arco Longo, runas na Besta Arcana, poeira âmbar na Besta de Caçador).</li>
 *   <li>Arco Longo IV — Olho de Falcão: tiros com puxada completa causam +25% a 12+ blocos e +50% a 24+ blocos.</li>
 *   <li>Besta Arcana: faíscas arcanas no acerto. IV — Virote Explosivo: explode no impacto (5 de dano nos inimigos a
 *       2,5 blocos, sem quebrar blocos).</li>
 *   <li>Besta de Caçador: Lentidão I por 2 s no alvo. IV — Presa Marcada: o alvo brilha por 8 s e recebe +25% de dano de
 *       você e das suas criaturas.</li>
 * </ul>
 */
public final class ArmoryArrows {
    public static final double HAWK_NEAR = 12.0;
    public static final double HAWK_FAR = 24.0;
    public static final float HAWK_NEAR_BONUS = 0.25F;
    public static final float HAWK_FAR_BONUS = 0.5F;
    /** Teto do multiplicador de Poder Mágico nos virotes da Besta Arcana (o resto do Poder fica para as técnicas). */
    public static final double ARCANE_POWER_CAP = 2.0;
    /** Estouro dos virotes da Besta Arcana IV (v1.0: ×DAMAGE_SCALE = 7,5). */
    public static final float BURST_DAMAGE = 5.0F * com.renanpontes.isekaimahou.core.Progression.DAMAGE_SCALE;
    public static final double BURST_RADIUS = 2.5;
    public static final int SLOW_TICKS = 40;
    public static final int MARK_TICKS = 160;
    public static final float MARK_BONUS = 0.25F;
    /** Máximo de flechas com rastro ao mesmo tempo (as outras voam sem rastro). */
    private static final int MAX_TRACKED = 96;
    private static final int TRAIL_LIFETIME = 120;
    private static final String BURST_TAG = "isekai_mahou.armory_burst";

    private record Mark(UUID owner, long until) {}

    private static final List<AbstractArrow> TRACKED = new ArrayList<>();
    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    /** A arma do Arsenal (arco/besta) que disparou a flecha, ou null. */
    public static ArmoryWeapon weaponOf(Entity e) {
        if (!(e instanceof AbstractArrow arrow)) return null;
        ArmoryWeapon w = ArmoryWeapon.of(arrow.getWeaponItem());
        return w != null && w.kind().isRanged() ? w : null;
    }

    // =====================================================================================
    //  Dano
    // =====================================================================================
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        DamageSource src = event.getSource();
        float mult = 1F;
        // Só o acerto da própria flecha (a explosão do Virote Explosivo tem dano fixo, como diz o tooltip)
        ArmoryWeapon w = src.is(DamageTypeTags.IS_PROJECTILE) ? weaponOf(src.getDirectEntity()) : null;
        if (w != null) {
            AbstractArrow arrow = (AbstractArrow) src.getDirectEntity();
            mult *= w.kind().projectileDamage(w.tier());
            if (w.kind() == WeaponKind.ARCANE_CROSSBOW && arrow.getOwner() instanceof LivingEntity owner
                    && owner.getAttributes().hasAttribute(CoreRegistries.SPELL_POWER)) {
                mult *= (float) Mth.clamp(owner.getAttributeValue(CoreRegistries.SPELL_POWER), 1.0, ARCANE_POWER_CAP);
            }
            if (w.kind() == WeaponKind.LONGBOW && w.tier() >= WeaponKind.MAX_TIER && arrow.isCritArrow()
                    && arrow.getOwner() != null) {
                double dist = arrow.getOwner().distanceTo(target);
                if (dist >= HAWK_FAR) mult *= 1F + HAWK_FAR_BONUS;
                else if (dist >= HAWK_NEAR) mult *= 1F + HAWK_NEAR_BONUS;
                if (dist >= HAWK_NEAR && target.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
                            10, 0.2, 0.3, 0.2, 0.3);
                }
            }
        }
        // Presa Marcada: qualquer dano do caçador (ou das criaturas dele) no alvo marcado
        Mark mark = MARKS.get(target.getUUID());
        if (mark != null && mark.until() >= target.level().getGameTime() && isFromOwner(src, mark.owner())) {
            mult *= 1F + MARK_BONUS;
        }
        if (mult != 1F) event.setAmount(event.getAmount() * mult);
    }

    private static boolean isFromOwner(DamageSource src, UUID owner) {
        Entity e = src.getEntity();
        if (e == null) return false;
        if (owner.equals(e.getUUID())) return true;
        return e instanceof OwnableEntity own && owner.equals(own.getOwnerUUID());
    }

    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) return;
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) return;
        ArmoryWeapon w = weaponOf(event.getSource().getDirectEntity());
        if (w == null) return;
        Entity owner = event.getSource().getEntity();
        Vec3 c = WeaponFx.center(target);
        switch (w.kind()) {
            case ARCANE_CROSSBOW -> {
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 10, 0.25, 0.25, 0.25, 0.2);
                level.sendParticles(WeaponFx.dust(WeaponKind.ARCANE_CROSSBOW.color, 1.0F), c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0);
            }
            case HUNTER_CROSSBOW -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0), owner);
                if (w.tier() >= WeaponKind.MAX_TIER && owner != null && target != owner) {
                    MARKS.put(target.getUUID(), new Mark(owner.getUUID(), level.getGameTime() + MARK_TICKS));
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false), owner);
                    level.sendParticles(WeaponFx.dust(0xFF4A3A, 1.2F), c.x, target.getY() + target.getBbHeight() + 0.4, c.z,
                            12, 0.2, 0.1, 0.2, 0);
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CROSSBOW_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
                }
            }
            default -> {
            }
        }
    }

    // =====================================================================================
    //  Virote Explosivo (Besta Arcana IV)
    // =====================================================================================
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)) return;
        ArmoryWeapon w = weaponOf(arrow);
        if (w == null || w.kind() != WeaponKind.ARCANE_CROSSBOW || w.tier() < WeaponKind.MAX_TIER) return;
        if (arrow.getTags().contains(BURST_TAG) || !(arrow.getOwner() instanceof LivingEntity owner)) return;
        HitResult hit = event.getRayTraceResult();
        if (hit.getType() == HitResult.Type.MISS) return;
        arrow.addTag(BURST_TAG);
        Entity direct = hit instanceof EntityHitResult eh ? eh.getEntity() : null;
        if (direct == owner) return;
        Vec3 at = hit.getLocation();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(BURST_RADIUS),
                e -> e != direct && ArmoryAllies.isEnemy(owner, e) && e.distanceToSqr(at) <= BURST_RADIUS * BURST_RADIUS)) {
            e.hurt(level.damageSources().indirectMagic(arrow, owner), BURST_DAMAGE);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        ParticleOptions rune = WeaponFx.dust(WeaponKind.ARCANE_CROSSBOW.color, 1.3F);
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2 * i / 20;
            level.sendParticles(rune, at.x + Math.cos(a) * BURST_RADIUS * 0.8, at.y + 0.2, at.z + Math.sin(a) * BURST_RADIUS * 0.8,
                    1, 0, 0.05, 0, 0);
        }
        level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 0.3, at.z, 20, 0.6, 0.5, 0.6, 0.8);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    // =====================================================================================
    //  Rastros (orçamento fixo de flechas acompanhadas)
    // =====================================================================================
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (TRACKED.size() >= MAX_TRACKED || weaponOf(arrow) == null || arrow.tickCount > 2) return;
        TRACKED.add(arrow);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        if (!TRACKED.isEmpty()) {
            Iterator<AbstractArrow> it = TRACKED.iterator();
            while (it.hasNext()) {
                AbstractArrow a = it.next();
                Vec3 v = a.getDeltaMovement();
                if (a.isRemoved() || a.tickCount > TRAIL_LIFETIME || (a.tickCount > 3 && v.lengthSqr() < 0.04)) {
                    it.remove();
                    continue;
                }
                trail(a);
            }
        }
        if (now % 40 == 0 && !MARKS.isEmpty()) MARKS.values().removeIf(m -> m.until() < now);
    }

    private static void trail(AbstractArrow a) {
        if (!(a.level() instanceof ServerLevel level)) return;
        ArmoryWeapon w = weaponOf(a);
        if (w == null) return;
        // Tiers baixos: rastro a cada 2 ticks; III/IV: todo tick
        if (w.tier() < 3 && (a.tickCount & 1) == 1) return;
        double x = a.getX(), y = a.getY(), z = a.getZ();
        switch (w.kind()) {
            case LONGBOW -> {
                level.sendParticles(WeaponFx.dust(w.tier() >= 3 ? 0xE8FFE0 : 0xB8E8A0, 0.6F), x, y, z, 1, 0.02, 0.02, 0.02, 0);
                if (w.tier() >= WeaponKind.MAX_TIER && a.isCritArrow()) level.sendParticles(ParticleTypes.CLOUD, x, y, z, 1, 0, 0, 0, 0.0);
            }
            case ARCANE_CROSSBOW -> {
                level.sendParticles(WeaponFx.dust(WeaponKind.ARCANE_CROSSBOW.color, 0.7F), x, y, z, 1, 0.02, 0.02, 0.02, 0);
                if (w.tier() >= 3) level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0.05, 0.05, 0.05, 0.1);
            }
            case HUNTER_CROSSBOW -> level.sendParticles(WeaponFx.dust(w.tier() >= WeaponKind.MAX_TIER ? 0xFF6A3A : 0xC8A060, 0.6F),
                    x, y, z, 1, 0.02, 0.02, 0.02, 0);
            default -> {
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        TRACKED.clear();
        MARKS.clear();
    }

    private ArmoryArrows() {}
}
