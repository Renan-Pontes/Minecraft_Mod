package com.renanpontes.isekaimahou.feature.potions.entity;

import com.renanpontes.isekaimahou.feature.potions.PotionForm;
import com.renanpontes.isekaimahou.feature.potions.PotionKind;
import com.renanpontes.isekaimahou.feature.potions.PotionLogic;
import com.renanpontes.isekaimahou.feature.potions.PotionTier;
import com.renanpontes.isekaimahou.feature.potions.PotionsContent;
import com.renanpontes.isekaimahou.feature.potions.item.MahouSplashPotionItem;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Frasco do mod em voo. Ao bater, respinga: cura aliados (nunca monstros nem quem está mirando o arremessador) ou
 * restaura o recurso dos jogadores no raio, mais forte perto do centro. Some na hora do impacto.
 */
public class ThrownMahouPotion extends ThrowableItemProjectile {
    /** Evento de nível do vanilla "respingo de efeito instantâneo" (partículas coloridas + som de vidro). */
    private static final int INSTANT_SPLASH_EVENT = 2007;

    public ThrownMahouPotion(EntityType<? extends ThrownMahouPotion> type, Level level) {
        super(type, level);
    }

    public ThrownMahouPotion(Level level, LivingEntity shooter) {
        super(PotionsContent.THROWN_POTION.get(), shooter, level);
    }

    public ThrownMahouPotion(Level level, double x, double y, double z) {
        super(PotionsContent.THROWN_POTION.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return PotionsContent.item(PotionKind.HEALTH, PotionForm.SPLASH, PotionTier.LESSER).get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) return;
        if (getItem().getItem() instanceof MahouSplashPotionItem potion) {
            Entity direct = result instanceof EntityHitResult ehr ? ehr.getEntity() : null;
            splash(potion.kind(), potion.tier(), direct);
            level().levelEvent(INSTANT_SPLASH_EVENT, blockPosition(), potion.tier().color(potion.kind()));
        }
        discard();
    }

    private void splash(PotionKind kind, PotionTier tier, Entity direct) {
        double r = tier.splashRadius();
        AABB box = getBoundingBox().inflate(r, r * 0.5, r);
        Entity owner = getOwner();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.isAffectedByPotions() || !e.isAlive()) continue;
            if (kind == PotionKind.MANA && !(e instanceof Player)) continue;
            if (kind == PotionKind.HEALTH && !friendly(e, owner)) continue;
            double d = Math.sqrt(distanceToSqr(e));
            if (d > r) continue;
            double factor = e == direct ? 1.0 : Math.max(0.25, 1.0 - d / r);
            PotionLogic.apply(e, kind, tier, PotionForm.SPLASH, factor);
        }
    }

    /** Respingo de vida não cura monstros nem mobs que estão atacando quem arremessou. */
    private static boolean friendly(LivingEntity e, Entity owner) {
        if (e instanceof Enemy) return false;
        return !(e instanceof Mob mob && owner != null && mob.getTarget() == owner);
    }
}
