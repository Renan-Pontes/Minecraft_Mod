package com.renanpontes.isekaimahou.feature.class_rogue.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueContent;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Faca voadora do Ladino (desenhada como o item girando de ponta):
 * <ul>
 *   <li>{@link #MODE_SPELL} — uma faca do Leque de Facas: leva a magia (Perfurar vale), avisa o impacto para os
 *   estágios seguintes e some em 1,5 s;</li>
 *   <li>{@link #MODE_KNIFE} — Faca de Arremesso (item): 4 de dano físico (as passivas do Ladino valem: costas e
 *   crítico); 70% de cair no chão para ser recolhida.</li>
 * </ul>
 * Temporária: o tipo é {@code noSave} (some ao descarregar o chunk).
 */
public class RogueKnifeEntity extends ThrowableItemProjectile {
    public static final byte MODE_SPELL = 0;
    public static final byte MODE_KNIFE = 1;
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(RogueKnifeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(RogueKnifeEntity.class, EntityDataSerializers.INT);
    private static final int SPELL_LIFE = 30;
    private static final int KNIFE_LIFE = 100;
    public static final float KNIFE_DAMAGE = 4.0F;

    @Nullable
    private SpellContext ctx;
    private int pierce;
    private final Set<UUID> hit = new HashSet<>();

    public RogueKnifeEntity(EntityType<? extends RogueKnifeEntity> type, Level level) {
        super(type, level);
    }

    /** Faca do Leque de Facas saindo de {@code from} na direção {@code dir}. */
    public static RogueKnifeEntity spell(SpellContext ctx, Vec3 from, Vec3 dir, float speed) {
        RogueKnifeEntity e = new RogueKnifeEntity(RogueContent.KNIFE.get(), ctx.level());
        e.entityData.set(DATA_MODE, MODE_SPELL);
        if (ctx.caster() != null) e.setOwner(ctx.caster());
        e.setPos(from.x, from.y, from.z);
        e.setItem(new ItemStack(RogueContent.THROWING_KNIFE.get()));
        e.shoot(dir.x, dir.y, dir.z, speed, 0.0F);
        e.ctx = ctx;
        e.pierce = Math.max(0, ctx.formStats().pierce);
        e.entityData.set(DATA_COLOR, ctx.color() & 0xFFFFFF);
        if (ctx.previousTarget() != null) e.hit.add(ctx.previousTarget());
        return e;
    }

    /** Faca de Arremesso lançada por {@code owner}. */
    public static RogueKnifeEntity thrown(Level level, LivingEntity owner, ItemStack stack) {
        RogueKnifeEntity e = new RogueKnifeEntity(RogueContent.KNIFE.get(), level);
        e.entityData.set(DATA_MODE, MODE_KNIFE);
        e.setOwner(owner);
        e.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        e.setItem(stack.copyWithCount(1));
        e.entityData.set(DATA_COLOR, 0xD8E4F0);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, MODE_SPELL);
        builder.define(DATA_COLOR, 0xB98CFF);
    }

    public byte mode() {
        return entityData.get(DATA_MODE);
    }

    public int color() {
        return entityData.get(DATA_COLOR);
    }

    @Override
    protected Item getDefaultItem() {
        return RogueContent.THROWING_KNIFE.get();
    }

    @Override
    protected double getDefaultGravity() {
        return mode() == MODE_SPELL ? 0.01 : 0.03;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        if (mode() == MODE_SPELL) {
            if (tickCount > SPELL_LIFE) {
                if (ctx != null) ctx.end(position(), getDeltaMovement());
                discard();
                return;
            }
            if (tickCount % 2 == 0) sl.sendParticles(RogueFx.dust(color(), 0.7F), getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0);
        } else if (tickCount > KNIFE_LIFE) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target) || hit.contains(target.getUUID())) return false;
        if (ctx != null) return !ctx.isAlly(target);
        Entity owner = getOwner();
        return owner == null || (target != owner && !owner.isAlliedTo(target));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel sl)) return;
        Entity target = result.getEntity();
        Entity owner = getOwner();
        hit.add(target.getUUID());
        Vec3 at = result.getLocation();
        if (mode() == MODE_SPELL) {
            SpellContext c = ctx;
            if (c != null) {
                c.impact(at, getDeltaMovement(), target);
                // 1 tick depois: o mesmo alvo pode levar o golpe da faca de cada lado do leque
                SpellScheduler.schedule(1, () -> {
                    if (target.isAlive() && target.level() == sl) {
                        target.invulnerableTime = 0;
                        c.resolveOnEntity(target, this);
                    }
                });
                ElementFx.burst(sl, at, c.element(), 6, 0.15);
            }
            RogueFx.sound(sl, at, SoundEvents.TRIDENT_HIT, 0.6F, 1.7F);
            if (pierce > 0) pierce--;
            else discard();
            return;
        }
        if (target.hurt(damageSources().thrown(this, owner), KNIFE_DAMAGE)) {
            RogueFx.sound(sl, at, SoundEvents.TRIDENT_HIT, 0.6F, 1.8F);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 at = result.getLocation();
        if (mode() == MODE_SPELL) {
            if (ctx != null) {
                ctx.impact(at, getDeltaMovement(), null);
                ctx.resolveOnBlock(result, this);
            }
            RogueFx.sound(sl, at, SoundEvents.CHAIN_PLACE, 0.5F, 1.8F);
            discard();
            return;
        }
        if (!(getOwner() instanceof Player p && p.getAbilities().instabuild) && random.nextFloat() < 0.7F) {
            ItemEntity drop = new ItemEntity(sl, at.x, at.y + 0.1, at.z, getItem().copyWithCount(1));
            drop.setDefaultPickUpDelay();
            sl.addFreshEntity(drop);
        }
        RogueFx.sound(sl, at, SoundEvents.CHAIN_PLACE, 0.6F, 1.8F);
        discard();
    }
}
