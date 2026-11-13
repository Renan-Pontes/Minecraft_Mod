package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcNet;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Supernova: uma estrela em miniatura se forma no ponto por {@link #CHARGE} ticks (cresce, pulsa, corona girando e
 * puxa de leve) — é uma conjuração PENDENTE na Pilha (dá para ser contrariada!). Então explode: dano enorme no centro,
 * Em Chamas 3, cegueira em quem estava olhando, clarão na tela, tremor e cratera com borda queimada.
 */
public class SupernovaEntity extends FcSpellEntity {
    public static final int CHARGE = 34;
    private static final int MAX_PER_OWNER = 2;
    private static final EntityDataAccessor<Boolean> FIZZLED = SynchedEntityData.defineId(SupernovaEntity.class, EntityDataSerializers.BOOLEAN);

    private float radius = 8F;
    private float damage = 20F;
    @Nullable
    private SpellStack.Pending pending;
    private boolean done;

    public SupernovaEntity(EntityType<? extends SupernovaEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(SpellContext ctx, SpellStats stats, Vec3 at) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            java.util.List<SupernovaEntity> mine = new java.util.ArrayList<>(sl.getEntities(FcRegistries.SUPERNOVA.get(),
                    x -> x.isAlive() && owner.equals(x.ownerId())));
            while (mine.size() >= MAX_PER_OWNER) mine.remove(0).discard();
        }
        SupernovaEntity s = new SupernovaEntity(FcRegistries.SUPERNOVA.get(), sl);
        s.setPos(at.x, at.y, at.z);
        s.radius = stats.radius(8F);
        s.setup(ctx, stats, Element.SUN, CHARGE + 12, s.radius);
        s.damage = 20F;
        sl.addFreshEntity(s);
        LivingEntity caster = ctx.caster();
        if (caster != null && caster.isAlive()) {
            s.pending = SpellStack.push(sl, caster, at, s.radius, CHARGE, SpellStack.Tier.MAJOR,
                    Component.translatable("pending.isekai_mahou.supernova"), Element.SUN, s::fizzle);
        }
        FcFx.sound(sl, at, SoundEvents.BEACON_ACTIVATE, 2F, 0.6F);
        FcFx.sound(sl, at, SoundEvents.FIRECHARGE_USE, 1.5F, 0.5F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FIZZLED, false);
    }

    public boolean fizzled() { return entityData.get(FIZZLED); }

    /** 0→1 enquanto carrega. */
    public float charge(float pt) {
        return Mth.clamp((age() + pt) / CHARGE, 0F, 1F);
    }

    /** Depois da explosão: 0→1 (a casca de luz se expandindo). */
    public float blast(float pt) {
        return Mth.clamp((age() + pt - CHARGE) / 12F, 0F, 1F);
    }

    /** Contramágica: a estrela colapsa sem explodir. */
    private void fizzle() {
        if (done || !(level() instanceof ServerLevel sl)) return;
        done = true;
        entityData.set(FIZZLED, true);
        Vec3 c = position();
        sl.sendParticles(Vfx.largeSmoke(), c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.05);
        FcFx.implode(sl, c, FcFx.dust(FcFx.SUN, 1.4F), 3, 24, 0.3);
        FcFx.sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 2F, 0.6F);
        life = Math.min(life, 8);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        if (done) return;
        int age = age();
        Vec3 c = position();
        if (age < CHARGE) {
            // Gravidade da estrela: puxa de leve quem está perto
            if (age % 2 == 0) {
                for (LivingEntity le : enemiesIn(c, radius * 0.9)) {
                    Vec3 to = c.subtract(FcUtil.center(le));
                    if (to.lengthSqr() > 2.0) FcUtil.knock(le, to.normalize().scale(0.05));
                }
                sl.sendParticles(Vfx.flame(), c.x, c.y, c.z, 0, 0, 0, 0, 0);
                FcFx.implode(sl, c, FcFx.dust(FcFx.SUN_WHITE, 1.2F), radius * 0.6, 10, 0.25);
            }
            if (age % 8 == 0) FcFx.sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 1.6F, 0.5F + age / (float) CHARGE);
            return;
        }
        if (age == CHARGE) detonate(sl, c);
    }

    private void detonate(ServerLevel sl, Vec3 c) {
        done = true;
        if (pending != null) SpellStack.resolve(pending);
        for (LivingEntity le : enemiesIn(c, radius)) {
            double d = Math.sqrt(le.distanceToSqr(c));
            float falloff = (float) (1.0 - 0.5 * d / radius);
            float dmg = damage * falloff * (FcUtil.undead(le) ? 1.5F : 1F);
            hit(le, dmg);
            if (!le.isAlive()) continue;
            Statuses.applyUpTo(le, Statuses.Kind.BURNING, 3, 160, owner(), power, 3);
            Vec3 out = le.position().subtract(c);
            FcUtil.knock(le, (out.lengthSqr() < 1.0E-3 ? new Vec3(0, 1, 0) : out.normalize()).scale(1.1).add(0, 0.45, 0));
        }
        // Quem olhou para a estrela fica cego
        for (LivingEntity le : enemiesIn(c, radius * 2.2)) {
            Vec3 eye = le.getEyePosition();
            Vec3 to = c.subtract(eye).normalize();
            if (le.getLookAngle().dot(to) < 0.4) continue;
            if (sl.clip(new ClipContext(eye, c, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, le)).getType() == HitResult.Type.BLOCK) continue;
            le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0), owner());
        }
        // Cratera do tamanho da estrela + chão em volta queimado
        Vec3 ground = FcUtil.ground(sl, c, 6);
        if (c.y - ground.y < radius * 0.8) {
            Destruction.crater(sl, ground, Math.max(2.5F, radius * 0.45F), owner());
            Destruction.surface(sl, ground, radius * 0.9F, owner(), Destruction.Mode.SCORCH);
        }
        FusionShockwaveEntity.visual(sl, ground.add(0, 0.1, 0), FusionShockwaveEntity.Kind.SOLAR, radius * 1.8F, 18);
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        FcFx.sphere(sl, c, Vfx.flame(), radius * 0.4, 80, 0.6);
        FcFx.sphere(sl, c, FcFx.dust(FcFx.SUN, 2.4F), radius * 0.6, 60, 0.2);
        FcFx.shockwave(sl, c, Vfx.endRod(), 48, 0.8);
        FcFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 4F, 0.6F);
        FcFx.sound(sl, c, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 3F, 0.5F);
        FcFx.sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 2F, 1.6F);
        BossKit.shake(sl, c, 40, 2.2F, 30);
        FcNet.flash(sl, c, 48, FcFx.SUN_WHITE, 0.85F, 22);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Sumiu antes de explodir (chunk descarregado, /kill): tira da Pilha
        if (!level().isClientSide && !done && pending != null) SpellStack.cancel(pending);
        super.remove(reason);
    }
}
