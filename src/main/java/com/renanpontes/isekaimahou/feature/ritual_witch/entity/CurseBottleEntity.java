package com.renanpontes.isekaimahou.feature.ritual_witch.entity;

import com.renanpontes.isekaimahou.feature.ritual_witch.WitchContent;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.BottledCurseItem;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Garrafa de maldição em voo. Ao bater em algo, a maldição do item se espalha em área. */
public class CurseBottleEntity extends ThrowableItemProjectile {
    public CurseBottleEntity(EntityType<? extends CurseBottleEntity> type, Level level) {
        super(type, level);
    }

    public CurseBottleEntity(Level level, LivingEntity shooter) {
        super(WitchContent.CURSE_BOTTLE.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return WitchContent.CURSE_HEX.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    public void tick() {
        super.tick();
        // Rastro de fumaça colorida (cliente)
        if (level().isClientSide && getItem().getItem() instanceof BottledCurseItem curse) {
            level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | curse.color()),
                    getX(), getY() + 0.1, getZ(), 0, 0, 0);
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.WITCH, getX(), getY() + 0.1, getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel sl)) return;
        Entity direct = result.getType() == HitResult.Type.ENTITY ? ((EntityHitResult) result).getEntity() : null;
        if (getItem().getItem() instanceof BottledCurseItem curse) curse.shatter(sl, this, direct, getOwner());
        discard();
    }
}
