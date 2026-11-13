package com.renanpontes.isekaimahou.feature.legends_j.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_j.LjRegistry;
import com.renanpontes.isekaimahou.feature.legends_j.LjUtil;
import com.renanpontes.isekaimahou.feature.legends_j.glyph.TengaiShinseiEffect;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A rocha do Tengai Shinsei: só VISUAL + o impacto. Não tem física nem colisão — o servidor a move numa linha reta do
 * céu até o ponto marcado em {@link #FALL_TICKS} ticks (acelerando) e aplica o impacto ao chegar. Temporária
 * ({@code noSave}): some com o servidor.
 */
public class MeteorRockEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(MeteorRockEntity.class, EntityDataSerializers.FLOAT);

    public static final int FALL_TICKS = 50;

    @Nullable private SpellContext ctx;
    @Nullable private SpellStats stats;
    private Vec3 start = Vec3.ZERO;
    private Vec3 end = Vec3.ZERO;
    private float radius = 6F;
    private int age;

    public MeteorRockEntity(EntityType<? extends MeteorRockEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public static void spawn(SpellContext ctx, SpellStats stats, Vec3 at, float radius, float size) {
        ServerLevel l = ctx.level();
        LivingEntity c = ctx.caster();
        Vec3 look = c != null ? c.getLookAngle() : ctx.look();
        Vec3 h = new Vec3(look.x, 0, look.z);
        h = h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
        // Vem do céu, por trás de quem conjurou (cai "à frente" dele, bem visível)
        Vec3 from = at.add(h.scale(-22)).add(0, 50, 0);
        double top = l.getMaxBuildHeight() - 2;
        if (from.y > top) from = new Vec3(from.x, top, from.z);
        MeteorRockEntity m = new MeteorRockEntity(LjRegistry.METEOR_ROCK.get(), l);
        m.ctx = ctx;
        m.stats = stats;
        m.start = from;
        m.end = at;
        m.radius = radius;
        m.entityData.set(DATA_SIZE, size);
        m.setPos(from.x, from.y, from.z);
        l.addFreshEntity(m);
    }

    public float size() {
        return entityData.get(DATA_SIZE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, 4.5F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Rastro de fogo e fumaça (só no cliente: barato e some sozinho)
            float s = size();
            for (int i = 0; i < 3; i++) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + (random.nextDouble() - 0.5) * s, getY() + s * 0.5 + (random.nextDouble() - 0.5) * s,
                        getZ() + (random.nextDouble() - 0.5) * s, 0, 0.05, 0);
                level().addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5) * s * 0.8, getY() + random.nextDouble() * s * 0.4,
                        getZ() + (random.nextDouble() - 0.5) * s * 0.8, 0, 0.02, 0);
            }
            return;
        }
        if (ctx == null || stats == null) {
            discard();
            return;
        }
        age++;
        double t = Math.min(1.0, age / (double) FALL_TICKS);
        double eased = t * t; // acelera na queda
        Vec3 p = start.add(end.subtract(start).scale(eased));
        Vec3 old = position();
        setPos(p.x, p.y, p.z);
        setDeltaMovement(p.subtract(old));
        ServerLevel l = (ServerLevel) level();
        if (age % 10 == 0) LjUtil.sound(l, p, SoundEvents.FIRECHARGE_USE, 2.0F, 0.4F);
        if (age >= FALL_TICKS) {
            impact(l);
            discard();
        }
    }

    private void impact(ServerLevel l) {
        SpellContext c = ctx;
        if (c == null || stats == null) return;
        LivingEntity caster = c.caster();
        Vec3 at = end;
        ElementFx.flash(l, at.add(0, 1, 0), 0xFFD8A0, radius * 1.2F);
        ElementFx.nova(l, at.add(0, 0.5, 0), Element.EARTH, TengaiShinseiEffect.COLOR, Math.min(14F, radius * 1.3F), 5);
        ElementFx.shockwave(l, at.add(0, 0.2, 0), 0xC8A070, radius * 1.6F, 26, null);
        ElementFx.shake(l, at, radius * 6, 2.2F, 26);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                at.x, at.y + 0.5, at.z, 60, radius * 0.4, 0.5, radius * 0.4, 0.3);
        LjUtil.sound(l, at, SoundEvents.GENERIC_EXPLODE, 3.0F, 0.5F);
        LjUtil.sound(l, at, SoundEvents.ANVIL_LAND, 2.0F, 0.4F);
        for (LivingEntity e : LjUtil.foesAround(c, at, radius, 30)) {
            boolean boss = Statuses.isBossLike(e);
            c.damage(e, TengaiShinseiEffect.DAMAGE * (boss ? 0.7F : 1F), stats, caster, Element.EARTH);
            Vec3 out = e.position().subtract(at);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : new Vec3(out.x, 0, out.z).normalize();
            double k = boss ? 0.3 : 1.2;
            e.setDeltaMovement(e.getDeltaMovement().add(out.scale(k)).add(0, boss ? 0.15 : 0.55, 0));
            e.hurtMarked = true;
        }
        // Cratera PEQUENA, só com a destruição de magias ligada (a API já poupa tudo que é protegido)
        if (Destruction.allowed(l, caster)) Destruction.crater(l, at, Math.min(4F, radius * 0.5F), caster);
    }

    // ---------------- É magia: sem física, sem interação, sem salvar ----------------
    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
