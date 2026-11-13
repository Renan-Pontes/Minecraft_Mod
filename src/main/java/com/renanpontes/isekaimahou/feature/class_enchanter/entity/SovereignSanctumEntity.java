package com.renanpontes.isekaimahou.feature.class_enchanter.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_enchanter.EnchFx;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchFxNet;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchRegistries;
import com.renanpontes.isekaimahou.feature.class_enchanter.Enchants;
import com.renanpontes.isekaimahou.feature.class_enchanter.glyph.EnchanterGlyphs;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.entity.SpellProjectileEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * O Santuário do Soberano no mundo: um círculo de inscrição gigante (modelo GeckoLib: anéis de runas girando, tábuas
 * flutuantes e a coroa no centro) que pulsa os efeitos da magia a cada segundo e é um campo anti-magia para os inimigos.
 * Temporário: some sozinho, se o dono morrer/sair/trocar de mundo, e nunca é salvo (EntityType.noSave()).
 */
public class SovereignSanctumEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SovereignSanctumEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SovereignSanctumEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation ANIM = RawAnimation.begin().thenPlay("animation.enchanter_sanctum.open")
            .thenLoop("animation.enchanter_sanctum.idle");
    /** No máximo tantos alvos por pulso (orçamento). */
    private static final int MAX_TARGETS = 24;

    /** Santuários ativos no servidor (campo anti-magia). */
    private static final List<SovereignSanctumEntity> ACTIVE = new ArrayList<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private SpellContext ctx;
    private int age;

    public SovereignSanctumEntity(EntityType<? extends SovereignSanctumEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Abre o santuário do conjurador (substitui o anterior dele). */
    public static void open(SpellContext ctx, LivingEntity caster, float radius, int ticks) {
        ServerLevel level = ctx.level();
        for (SovereignSanctumEntity s : new ArrayList<>(ACTIVE)) {
            if (s.ctx != null && s.ctx.caster() == caster) s.discard();
        }
        SovereignSanctumEntity s = new SovereignSanctumEntity(EnchRegistries.SANCTUM.get(), level);
        Vec3 feet = ground(level, caster.position());
        s.moveTo(feet.x, feet.y, feet.z, 0F, 0F);
        s.ctx = ctx;
        s.entityData.set(RADIUS, radius);
        s.entityData.set(LIFE, ticks);
        if (level.addFreshEntity(s)) ACTIVE.add(s);
        EnchFxNet.at(level, EnchFxNet.SANCTUM_OPEN, feet, Vec3.ZERO, (float) radius, EnchFx.GOLD, -1);
        EnchFx.sound(level, feet, SoundEvents.BEACON_ACTIVATE, 1.4F, 0.8F);
        EnchFx.sound(level, feet, SoundEvents.BELL_RESONATE, 1.0F, 1.2F);
        EnchFx.sound(level, feet, SoundEvents.ENCHANTMENT_TABLE_USE, 1.2F, 0.6F);
    }

    private static Vec3 ground(ServerLevel level, Vec3 p) {
        BlockPos.MutableBlockPos m = BlockPos.containing(p).mutable();
        for (int i = 0; i < 6 && m.getY() > level.getMinBuildHeight(); i++) {
            if (!level.getBlockState(m.below()).getCollisionShape(level, m.below()).isEmpty()) break;
            m.move(0, -1, 0);
        }
        return new Vec3(p.x, m.getY(), p.z);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(RADIUS, 10F);
        b.define(LIFE, 300);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) {
            clientAmbient();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        LivingEntity owner = ctx == null ? null : ctx.caster();
        if (ctx == null || owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level || age > life()) {
            close(level);
            return;
        }
        float r = radius();
        if (age % 20 == 1) pulse(level, owner, r);
        if (age % 4 == 0) dissolveProjectiles(level, r);
        if (age % 20 == 10) {
            EnchFx.ring(level, position().add(0, 0.15, 0), r, EnchFx.GOLD, Math.min(96, (int) (r * 6)), 1.0F);
            level.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.5, getZ(), 24, r * 0.4, 0.8, r * 0.4, 0.8);
            EnchFx.sound(level, position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.7F + random.nextFloat() * 0.4F);
        }
    }

    /** Um pulso: os efeitos em todos lá dentro (benéficos em aliados/no dono, o resto nos inimigos). */
    private void pulse(ServerLevel level, LivingEntity owner, float r) {
        Vec3 c = position();
        List<LivingEntity> inside = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 6, r),
                e -> e.isAlive() && !e.isSpectator() && horiz(e.position(), c) <= r * r));
        inside.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c)));
        int n = 0;
        for (LivingEntity e : inside) {
            if (n++ >= MAX_TARGETS) break;
            if (e == owner) EnchanterGlyphs.blessCaster(ctx);
            else ctx.resolveOnEntity(e, null);
        }
        EnchFx.sound(level, c, SoundEvents.BEACON_AMBIENT, 1.0F, 1.4F);
    }

    /** Campo anti-magia: projéteis mágicos de inimigos se desfazem lá dentro. */
    private void dissolveProjectiles(ServerLevel level, float r) {
        Vec3 c = position();
        for (SpellProjectileEntity p : level.getEntitiesOfClass(SpellProjectileEntity.class, new AABB(c, c).inflate(r, 8, r),
                p -> horiz(p.position(), c) <= r * r)) {
            Entity o = p.getOwner();
            if (o == null || o == ctx.caster() || ctx.isAlly(o) || (o instanceof LivingEntity lo && Enchants.isBoss(lo))) continue;
            EnchFx.shatter(level, p.position(), 0.6);
            EnchFx.sound(level, p.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8F, 1.6F);
            p.discard();
        }
    }

    private static double horiz(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private void close(ServerLevel level) {
        EnchFx.ring(level, position().add(0, 0.2, 0), radius(), EnchFx.WHITE, 48, 1.2F);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 24, radius() * 0.3, 0.6, radius() * 0.3, 0.05);
        EnchFx.sound(level, position(), SoundEvents.BEACON_DEACTIVATE, 1.2F, 0.9F);
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        // a lista é só do servidor (no single player o cliente roda em outra thread: nunca mexe nela)
        if (!level().isClientSide) ACTIVE.remove(this);
    }

    /** O conjurador está num santuário INIMIGO? (as magias dele não saem) */
    public static boolean blocksCaster(LivingEntity caster) {
        if (ACTIVE.isEmpty()) return false;
        for (SovereignSanctumEntity s : ACTIVE) {
            if (s.isRemoved() || s.ctx == null || s.level() != caster.level()) continue;
            LivingEntity owner = s.ctx.caster();
            if (owner == caster || s.ctx.isAlly(caster)) continue;
            float r = s.radius();
            if (horiz(caster.position(), s.position()) <= r * r && Math.abs(caster.getY() - s.getY()) < 8) return true;
        }
        return false;
    }

    /** Servidor fechou. */
    public static void clearAll() {
        ACTIVE.clear();
    }

    /** Brilho ambiente no cliente (barato: poucas partículas na borda). */
    private void clientAmbient() {
        if (age % 3 != 0) return;
        float r = radius();
        double a = random.nextDouble() * Math.PI * 2;
        level().addParticle(ParticleTypes.END_ROD, getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r, 0, 0.04, 0);
        double b = random.nextDouble() * Math.PI * 2, rr = random.nextDouble() * r;
        level().addParticle(ParticleTypes.ENCHANT, getX() + Math.cos(b) * rr, getY() + 0.3, getZ() + Math.sin(b) * rr, 0, 0.6, 0);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    // ---------------- GeckoLib ----------------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(ANIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
