package com.renanpontes.isekaimahou.feature.legends_g.entity;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.feature.legends_g.LgRegistry;
import com.renanpontes.isekaimahou.feature.legends_g.LgUtil;
import com.renanpontes.isekaimahou.feature.legends_g.glyph.RudeusTechniques;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Canhão de Pedra (Rudeus): uma bala de pedra em forma de ogiva se forma na frente da mão girando cada vez mais rápido
 * ({@link #FORM_TICKS}) e é disparada reta, sem gravidade, a {@link #SPEED} blocos/tick. Acerta o primeiro inimigo
 * (dano alto de TERRA + empurrão); com Perfurar atravessa (−30% por alvo). No bloco: estilhaça (buraco pequeno via
 * {@link Destruction} — que já respeita cidades, torres, masmorras, a regra de jogo e o mobGriefing).
 */
public class StoneShellEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(StoneShellEntity.class, EntityDataSerializers.INT);

    public static final int FORM = 0, FLY = 1;
    public static final int FORM_TICKS = 8;
    public static final double SPEED = 4.0;
    public static final double RANGE = 80.0;

    @Nullable private SpellContext ctx;
    @Nullable private SpellStats stats;
    @Nullable private LivingEntity owner;
    @Nullable private LivingEntity aimTarget;
    /** Ponto fixo onde a bala se forma (alvo atingido por outra forma); null = na mão do dono. */
    @Nullable private Vec3 anchor;
    private Vec3 velocity = Vec3.ZERO;
    private double travelled;
    private int pierceLeft;
    private float damageMult = 1F;
    private int age;
    private final Set<Integer> hit = new HashSet<>();

    public StoneShellEntity(EntityType<? extends StoneShellEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public static void spawn(SpellContext ctx, SpellStats stats, @Nullable LivingEntity target) {
        LivingEntity c = ctx.caster();
        if (c == null) return;
        ServerLevel l = ctx.level();
        StoneShellEntity s = new StoneShellEntity(LgRegistry.STONE_SHELL.get(), l);
        s.ctx = ctx;
        s.stats = stats;
        s.owner = c;
        s.aimTarget = target;
        s.pierceLeft = Math.max(0, stats.pierce);
        Vec3 hand = hand(c);
        s.setPos(hand.x, hand.y, hand.z);
        s.aim(c.getLookAngle());
        l.addFreshEntity(s);
        LgUtil.sound(l, hand, SoundEvents.STONE_PLACE, 1.2F, 0.6F);
        LgUtil.sound(l, hand, SoundEvents.GRINDSTONE_USE, 0.8F, 1.6F);
    }

    /**
     * Num alvo atingido por qualquer forma: a bala se forma a até 3 blocos dele, do lado de quem conjurou, e o fura.
     */
    public static void spawnNear(SpellContext ctx, SpellStats stats, Entity target) {
        LivingEntity c = ctx.caster();
        if (c == null) return;
        ServerLevel l = ctx.level();
        Vec3 tc = LgUtil.center(target);
        Vec3 back = c.getEyePosition().subtract(tc);
        back = back.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : back.normalize();
        Vec3 at = LgUtil.rayEnd(l, tc, back, 3.0, target);
        at = at.subtract(back.scale(Math.min(0.4, at.distanceTo(tc) * 0.5)));
        StoneShellEntity s = new StoneShellEntity(LgRegistry.STONE_SHELL.get(), l);
        s.ctx = ctx;
        s.stats = stats;
        s.owner = c;
        s.aimTarget = target instanceof LivingEntity le ? le : null;
        s.anchor = at;
        s.pierceLeft = Math.max(0, stats.pierce);
        s.setPos(at.x, at.y, at.z);
        s.aim(tc.subtract(at));
        l.addFreshEntity(s);
        LgUtil.sound(l, at, SoundEvents.STONE_PLACE, 1.2F, 0.6F);
        LgUtil.sound(l, at, SoundEvents.GRINDSTONE_USE, 0.8F, 1.6F);
    }

    /** À frente da mão direita, na altura do peito. */
    static Vec3 hand(LivingEntity o) {
        Vec3 look = o.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        return o.getEyePosition().add(look.scale(1.1)).add(right.scale(0.35)).add(0, -0.3, 0);
    }

    public int phase() { return entityData.get(DATA_PHASE); }

    /** Direção pela rotação sincronizada. */
    public Vec3 dir() {
        return Vec3.directionFromRotation(getXRot(), getYRot());
    }

    private void aim(Vec3 d) {
        Vec3 n = d.normalize();
        setYRot((float) (Mth.atan2(-n.x, n.z) * Mth.RAD_TO_DEG));
        setXRot((float) (-Math.asin(Mth.clamp(n.y, -1.0, 1.0)) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PHASE, FORM);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel l = (ServerLevel) level();
        age++;
        if (ctx == null || stats == null || owner == null || age > 200) {
            discard();
            return;
        }
        if (phase() == FORM) form(l);
        else fly(l);
    }

    private void form(ServerLevel l) {
        if (anchor == null && (!owner.isAlive() || owner.level() != l)) {
            discard();
            return;
        }
        Vec3 hand = anchor != null ? anchor : hand(owner);
        setPos(hand.x, hand.y, hand.z);
        Vec3 dir = anchor != null ? dir() : owner.getLookAngle();
        if (aimTarget != null && aimTarget.isAlive() && aimTarget.level() == l) dir = LgUtil.center(aimTarget).subtract(hand);
        aim(dir);
        if (age % 2 == 0) {
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), hand.x, hand.y, hand.z,
                    2, 0.25, 0.25, 0.25, 0.02);
        }
        if (age >= FORM_TICKS) {
            velocity = dir().scale(SPEED * stats.speedMult());
            entityData.set(DATA_PHASE, FLY);
            LgUtil.sound(l, hand, SoundEvents.TRIDENT_THROW.value(), 1.4F, 0.5F);
            LgUtil.sound(l, hand, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0F, 0.6F);
            ElementFx.shockwave(l, hand, RudeusTechniques.STONE_COLOR, 1.2F, 6, dir());
        }
    }

    private void fly(ServerLevel l) {
        Vec3 from = position();
        Vec3 to = from.add(velocity);
        BlockHitResult bh = l.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : to;
        Vec3 dir = velocity.normalize();
        // Inimigos no caminho, do mais perto ao mais longe
        for (LivingEntity e : LgUtil.foesOnLine(ctx, from, end, 0.35, 8)) {
            if (!hit.add(e.getId())) continue;
            float dealt = ctx.damage(e, RudeusTechniques.StoneCannon.DAMAGE * damageMult, stats, this, Element.EARTH);
            if (dealt > 0) e.knockback(Math.min(2.5F, 0.9F * stats.power()), -dir.x, -dir.z);
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 14, 0.3, 0.3, 0.3, 0.15);
            LgUtil.sound(l, e.position(), SoundEvents.ANVIL_LAND, 0.7F, 1.6F);
            if (pierceLeft <= 0) {
                impact(l, LgUtil.center(e), false);
                return;
            }
            pierceLeft--;
            damageMult *= 0.7F;
        }
        setPos(end.x, end.y, end.z);
        travelled += from.distanceTo(end);
        if (bh.getType() == HitResult.Type.BLOCK) {
            impact(l, end, true);
            return;
        }
        if (travelled > RANGE) {
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), end.x, end.y, end.z,
                    10, 0.2, 0.2, 0.2, 0.05);
            discard();
        }
    }

    private void impact(ServerLevel l, Vec3 at, boolean block) {
        ElementFx.impactFx(l, at, Element.EARTH, RudeusTechniques.STONE_COLOR, 2, velocity.normalize().reverse(), 1.0F);
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), at.x, at.y, at.z,
                24, 0.4, 0.4, 0.4, 0.2);
        LgUtil.sound(l, at, SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 1.4F);
        LgUtil.sound(l, at, SoundEvents.STONE_BREAK, 1.4F, 0.7F);
        if (block) {
            // Buraquinho de estilhaço: Destruction checa regra de jogo, cidades, torres, masmorras e o Lar
            float r = Math.min(2.6F, 0.9F + 0.6F * stats.power() * ctx.powerMult());
            Destruction.sphere(l, at, r, owner, Destruction.Mode.BREAK);
        }
        discard();
    }

    // ---------------- É magia: sem física nem interação ----------------
    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public boolean isIgnoringBlockTriggers() { return true; }

    @Override
    public boolean canUsePortal(boolean allowPassengers) { return false; }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }

    @Override
    public boolean displayFireAnimation() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
