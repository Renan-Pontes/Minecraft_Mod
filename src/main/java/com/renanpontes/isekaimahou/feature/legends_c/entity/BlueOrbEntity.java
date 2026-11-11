package com.renanpontes.isekaimahou.feature.legends_c.entity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.LcRegistry;
import com.renanpontes.isekaimahou.feature.legends_c.LcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Azul (Lapso): um ponto de atração violento. Puxa criaturas, itens e projéteis num raio de 7 (Expandir +50%), esmaga
 * quem chega ao centro, e — com a destruição ligada — DEVORA o terreno: a cada 6 ticks uma esfera apagada (ERASE) que
 * cresce até o máximo, com blocos visivelmente arrancados e sugados para dentro. No fim implode.
 */
public class BlueOrbEntity extends LcEntity {
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(BlueOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PULL = SynchedEntityData.defineId(BlueOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(BlueOrbEntity.class, EntityDataSerializers.FLOAT);
    /** Máximo de Azuis vivos por conjurador. */
    public static final int MAX_PER_CASTER = 3;

    private static final Set<BlueOrbEntity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    @Nullable private SpellStats stats;
    private float eraseMax = 2.5F;

    public BlueOrbEntity(EntityType<? extends BlueOrbEntity> type, Level level) {
        super(type, level);
    }

    /** Cria o Azul em {@code at} (nada se o conjurador já tem {@link #MAX_PER_CASTER}). */
    public static void spawn(SpellContext ctx, SpellStats stats, Vec3 at) {
        ServerLevel l = ctx.level();
        LivingEntity caster = ctx.caster();
        if (caster != null) {
            int n = 0;
            for (BlueOrbEntity b : LIVE) if (b.isAlive() && b.owner == caster) n++;
            if (n >= MAX_PER_CASTER) return;
        }
        boolean colossal = LcUtil.colossal(stats);
        BlueOrbEntity b = new BlueOrbEntity(LcRegistry.BLUE_ORB.get(), l);
        b.ctx = ctx;
        b.stats = stats;
        b.bindOwner(caster);
        b.eraseMax = Mth.clamp(2.5F + 1.2F * Math.max(0, stats.aoe), 2.5F, 9F) * (colossal ? 1.8F : 1F);
        b.entityData.set(DATA_LIFE, Mth.clamp(stats.duration(70), 40, 200));
        b.entityData.set(DATA_PULL, stats.radius(7F) * (colossal ? 1.6F : 1F));
        b.entityData.set(DATA_SIZE, 0.7F + 0.15F * Math.max(0, stats.amplify) + (colossal ? 0.8F : 0F));
        b.setPos(at.x, at.y, at.z);
        l.addFreshEntity(b);
        LIVE.add(b);
        LcFx.sound(l, at, SoundEvents.BEACON_POWER_SELECT, 1.6F, 0.5F);
        LcFx.sound(l, at, SoundEvents.PORTAL_TRIGGER, 0.6F, 1.6F);
        LcFx.flash(l, at, 2.0F, LcFx.BLUE, 6);
    }

    @Override
    protected void defineExtra(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE, 70);
        builder.define(DATA_PULL, 7F);
        builder.define(DATA_SIZE, 0.7F);
    }

    public int life() { return entityData.get(DATA_LIFE); }
    public float pull() { return entityData.get(DATA_PULL); }
    public float size() { return entityData.get(DATA_SIZE); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel l = (ServerLevel) level();
        if (ctx == null || stats == null) {
            discard();
            return;
        }
        if (tickCount >= life()) {
            implode(l);
            discard();
            return;
        }
        Vec3 c = position();
        pullAll(l, c);
        if (tickCount % 5 == 0) crush(l, c, 2.2, 4.5F);
        if (tickCount % 6 == 0 && tickCount > 4) erode(l, c);
        if (tickCount % 12 == 0) LcFx.sound(l, c, SoundEvents.PORTAL_AMBIENT, 1.2F, 0.5F);
        if (tickCount % 3 == 0) {
            float p = pull();
            l.sendParticles(LcFx.dust(LcFx.BLUE, 1.6F), c.x, c.y, c.z, 4, p * 0.4, p * 0.4, p * 0.4, 0);
        }
    }

    private void pullAll(ServerLevel l, Vec3 c) {
        double r = pull();
        AABB box = new AABB(c, c).inflate(r);
        for (Entity e : l.getEntities(this, box, e -> e.isAlive() && !e.isSpectator()
                && (e instanceof LivingEntity || e instanceof ItemEntity || e instanceof Projectile || e instanceof FallingBlockEntity))) {
            if (e instanceof LivingEntity le && (le == owner || ctx.isAlly(le))) continue;
            if (e instanceof Player p && p.isCreative()) continue;
            // Quem está protegido pela barreira fechada de outro domínio não é sugado através dela
            if (e instanceof LivingEntity && LcUtil.behindWall(l, c, e, owner)) continue;
            Vec3 to = c.subtract(LcUtil.center(e));
            double d = to.length();
            if (d > r || d < 1.0E-3) continue;
            double s = 0.10 + 0.30 * (1.0 - d / r);
            if (e instanceof LivingEntity le && BossKit.isBossLike(le)) s *= 0.35;
            if (d < 1.2) s *= d / 1.2;
            e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(to.normalize().scale(s)));
            e.hurtMarked = true;
            e.resetFallDistance();
        }
    }

    private void crush(ServerLevel l, Vec3 c, double radius, float damage) {
        for (LivingEntity e : ctx.enemiesAround(c, radius + 1.0)) {
            if (e.distanceToSqr(c) > (radius + e.getBbWidth()) * (radius + e.getBbWidth())) continue;
            if (LcUtil.behindWall(l, c, e, owner)) continue;
            ctx.damage(e, damage, stats, this);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 3, false, false, false));
            l.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.2, 0.2, 0.2, 0.3);
        }
    }

    /** O terreno é devorado: esfera apagada que cresce + blocos arrancados voando para dentro. */
    private void erode(ServerLevel l, Vec3 c) {
        if (!Destruction.allowed(l, owner)) return;
        float prog = Math.min(1F, tickCount / (life() * 0.75F));
        float r = 1.5F + (eraseMax - 1.5F) * prog;
        int shown = 0;
        for (int i = 0; i < 14 && shown < 6; i++) {
            double z = random.nextDouble() * 2 - 1, a = random.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
            double rr = r + 0.3 + random.nextDouble() * 1.6;
            Vec3 p = c.add(Math.cos(a) * s * rr, z * rr, Math.sin(a) * s * rr);
            BlockPos bp = BlockPos.containing(p);
            BlockState st = l.getBlockState(bp);
            if (st.isAir() || !st.getFluidState().isEmpty() || Destruction.protectedAt(l, bp, owner)) continue;
            LcFx.suck(l, Vec3.atCenterOf(bp), c, st, 9 + random.nextInt(6));
            shown++;
        }
        if (Destruction.sphere(l, c, r, owner, Destruction.Mode.ERASE) > 0 && tickCount % 12 == 0) {
            LcFx.sound(l, c, SoundEvents.GRAVEL_BREAK, 1.5F, 0.5F);
        }
    }

    private void implode(ServerLevel l) {
        Vec3 c = position();
        crush(l, c, 3.5, 9F);
        if (Destruction.allowed(l, owner)) Destruction.sphere(l, c, Math.max(2F, eraseMax * 0.8F), owner, Destruction.Mode.ERASE);
        LcFx.flash(l, c, 4.5F + size() * 2F, LcFx.BLUE_LIGHT, 10);
        LcFx.wave(l, c, new Vec3(0, 1, 0), pull() * 0.8F, LcFx.BLUE, 12);
        LcFx.sound(l, c, SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 1.5F);
        LcFx.sound(l, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 2.0F, 0.5F);
        BossKit.shake(l, c, 24, 0.8F, 10);
        LIVE.remove(this);
    }

    public static void clear() {
        LIVE.clear();
    }
}
