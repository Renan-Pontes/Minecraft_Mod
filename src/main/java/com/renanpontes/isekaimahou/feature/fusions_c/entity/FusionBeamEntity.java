package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Feixe das Misturas C, de {@code position()} até {@code position() + endOffset()} (sincronizado). Tipos:
 * <ul>
 *   <li>JATO DE VAPOR (Canhão de Vapor): segue a mira do conjurador, empurra longe, escalda e molha;</li>
 *   <li>ERUPÇÃO SOLAR: feixe de plasma que segue a mira (com "peso": gira devagar), queima o chão por onde passa;</li>
 *   <li>CANHÃO ELÉTRICO e RAIO DE PURGA: só visuais (o dano já foi aplicado por quem criou).</li>
 * </ul>
 */
public class FusionBeamEntity extends FcSpellEntity {
    public enum Kind { STEAM_JET, SOLAR_FLARE, RAILGUN, PURGE_RAY }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(FusionBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> EX = SynchedEntityData.defineId(FusionBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EY = SynchedEntityData.defineId(FusionBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EZ = SynchedEntityData.defineId(FusionBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final int MAX_CHANNELED_PER_OWNER = 2;

    private float range = 14F;
    private float tickDamage = 2.5F;
    /** Direção atual (feixes que seguem a mira giram até a direção desejada). */
    private Vec3 dir = new Vec3(0, 0, 1);
    /** Feixe preso (estágio de magia ou sem conjurador): não segue a mira. */
    private boolean fixed;

    public FusionBeamEntity(EntityType<? extends FusionBeamEntity> type, Level level) {
        super(type, level);
    }

    /** Feixe só visual de {@code from} a {@code to}. */
    public static FusionBeamEntity visual(ServerLevel sl, Vec3 from, Vec3 to, Kind kind, float width, int ticks) {
        FusionBeamEntity b = new FusionBeamEntity(FcRegistries.FUSION_BEAM.get(), sl);
        b.setPos(from.x, from.y, from.z);
        b.setup(null, new SpellStats(), Element.ARCANE, Math.max(2, ticks), width);
        b.entityData.set(KIND, kind.ordinal());
        b.setEnd(to.subtract(from));
        b.fixed = true;
        sl.addFreshEntity(b);
        return b;
    }

    /** Feixe canalizado que segue a mira (jato de vapor / erupção solar). */
    public static void channel(SpellContext ctx, SpellStats stats, Kind kind, Element element, Vec3 from, Vec3 aimDir,
                               float range, float width, int ticks, float tickDamage) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            List<FusionBeamEntity> mine = new ArrayList<>(sl.getEntities(FcRegistries.FUSION_BEAM.get(),
                    b -> b.isAlive() && owner.equals(b.ownerId()) && b.kind() == kind));
            while (mine.size() >= MAX_CHANNELED_PER_OWNER) mine.remove(0).discard();
        }
        FusionBeamEntity b = new FusionBeamEntity(FcRegistries.FUSION_BEAM.get(), sl);
        b.setPos(from.x, from.y, from.z);
        b.setup(ctx, stats, element, ticks, width);
        b.entityData.set(KIND, kind.ordinal());
        b.range = range;
        b.tickDamage = tickDamage;
        b.dir = aimDir.normalize();
        b.fixed = ctx.hasOrigin() || ctx.caster() == null;
        b.setEnd(b.dir.scale(range));
        sl.addFreshEntity(b);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, 0);
        builder.define(EX, 0F);
        builder.define(EY, 0F);
        builder.define(EZ, 1F);
    }

    public Kind kind() {
        return Kind.values()[Mth.clamp(entityData.get(KIND), 0, Kind.values().length - 1)];
    }

    /** Vetor do começo ao fim do feixe. */
    public Vec3 endOffset() {
        return new Vec3(entityData.get(EX), entityData.get(EY), entityData.get(EZ));
    }

    private void setEnd(Vec3 v) {
        entityData.set(EX, (float) v.x);
        entityData.set(EY, (float) v.y);
        entityData.set(EZ, (float) v.z);
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================

    @Override
    protected void serverTick(ServerLevel sl) {
        Kind k = kind();
        if (k != Kind.STEAM_JET && k != Kind.SOLAR_FLARE) return;
        LivingEntity caster = owner();
        if (!fixed) {
            if (caster == null || !caster.isAlive() || caster.isRemoved() || caster.level() != sl) {
                discard();
                return;
            }
            // Sai da altura da mão e segue a mira (mob: mira o alvo dele)
            Vec3 eye = caster.getEyePosition();
            Vec3 want = caster.getLookAngle();
            if (caster instanceof Mob m && m.getTarget() != null) want = m.getTarget().getEyePosition().subtract(eye).normalize();
            float turn = k == Kind.SOLAR_FLARE ? 0.16F : 0.5F;
            dir = dir.add(want.subtract(dir).scale(turn)).normalize();
            Vec3 from = eye.add(0, -0.25, 0).add(dir.scale(0.6));
            setPos(from.x, from.y, from.z);
            if (k == Kind.SOLAR_FLARE) caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 1, false, false));
        }
        Vec3 from = position();
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult block = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean hitBlock = block.getType() == HitResult.Type.BLOCK;
        Vec3 stop = hitBlock ? block.getLocation() : to;
        setEnd(stop.subtract(from));
        if (k == Kind.STEAM_JET) steamTick(sl, from, stop, caster);
        else flareTick(sl, from, stop, hitBlock ? block : null);
    }

    /** Entidades dentro do "tubo" do feixe, com a distância ao longo dele. */
    private List<LivingEntity> inBeam(Vec3 from, Vec3 to, double radius) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.1) return List.of();
        Vec3 n = d.scale(1 / len);
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(from, to).inflate(radius + 1);
        for (LivingEntity le : level().getEntitiesOfClass(LivingEntity.class, box, le -> le.isAlive() && !le.isSpectator())) {
            if (ally(le) || FcUtil.untouchable(le)) continue;
            Vec3 c = FcUtil.center(le);
            double t = c.subtract(from).dot(n);
            if (t < -0.5 || t > len + 0.5) continue;
            double dist = c.subtract(from.add(n.scale(t))).length();
            if (dist <= radius + le.getBbWidth() * 0.5) out.add(le);
        }
        return out;
    }

    private void steamTick(ServerLevel sl, Vec3 from, Vec3 stop, @Nullable LivingEntity caster) {
        int age = age();
        float w = scale();
        Vec3 n = stop.subtract(from).normalize();
        if (age % 2 == 0) {
            for (LivingEntity le : inBeam(from, stop, w)) {
                // Empurra ao longo do jato (mais forte perto da boca)
                double t = FcUtil.center(le).subtract(from).length() / Math.max(1, range);
                double push = (0.55 + 0.15 * power) * (1.2 - t);
                Vec3 v = le.getDeltaMovement();
                FcUtil.setMotion(le, FcUtil.bossLike(le) ? v.add(n.scale(push * 0.15)) : new Vec3(n.x * push, Math.max(v.y, n.y * push + 0.12), n.z * push));
                le.resetFallDistance();
                if (age % 4 == 0) {
                    hit(le, tickDamage);
                    Statuses.apply(le, Statuses.Kind.WET, 1, 120, caster, power);
                    le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), caster);
                    Statuses.clear(le, Statuses.Kind.BURNING);
                }
            }
        }
        // Coice no conjurador (agachado segura firme)
        if (caster != null && !fixed && age % 4 == 0 && !caster.isShiftKeyDown() && !caster.onGround()) {
            FcUtil.addMotion(caster, n.scale(-0.05));
        }
        // Apaga fogo no caminho
        if (age % 5 == 0) {
            BlockPos bp = BlockPos.containing(stop.subtract(n.scale(0.3)));
            if (sl.getBlockState(bp).is(BlockTags.FIRE)) sl.removeBlock(bp, false);
        }
        for (int i = 0; i < 3; i++) {
            double t = random.nextDouble();
            Vec3 p = from.add(stop.subtract(from).scale(t));
            double s = w * t;
            sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, n.x + (random.nextDouble() - 0.5) * 0.3,
                    n.y + (random.nextDouble() - 0.5) * 0.3, n.z + (random.nextDouble() - 0.5) * 0.3, 0.4 + s * 0.1);
        }
        sl.sendParticles(ParticleTypes.SPLASH, stop.x, stop.y, stop.z, 6, 0.4, 0.4, 0.4, 0.1);
        if (age % 6 == 0) FcFx.sound(sl, from, SoundEvents.FIRE_EXTINGUISH, 0.9F, 0.6F);
        if (age % 10 == 0) FcFx.sound(sl, from, SoundEvents.LAVA_EXTINGUISH, 1F, 0.5F);
    }

    private void flareTick(ServerLevel sl, Vec3 from, Vec3 stop, @Nullable BlockHitResult block) {
        int age = age();
        float w = scale();
        if (age % 4 == 0) {
            for (LivingEntity le : inBeam(from, stop, w)) {
                hit(le, FcUtil.undead(le) ? tickDamage * 1.5F : tickDamage);
                le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0), owner());
            }
        }
        // Queima o chão por onde passa: o feixe "escreve" uma trilha de terra queimada
        if (block != null && age % 6 == 0) {
            Destruction.surface(sl, block.getLocation(), 1.4F, owner(), Destruction.Mode.SCORCH);
            BlockPos above = block.getBlockPos().relative(block.getDirection());
            if (sl.getBlockState(above).isAir() && random.nextInt(3) == 0 && BaseFireBlock.canBePlacedAt(sl, above, block.getDirection())
                    && Destruction.allowed(sl, owner())
                    && !Destruction.protectedAt(sl, above, owner())) {
                sl.setBlockAndUpdate(above, Blocks.FIRE.defaultBlockState());
            }
        }
        Vec3 p = stop;
        sl.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.06);
        sl.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0);
        if (age % 2 == 0) sl.sendParticles(FcFx.dust(FcFx.SUN, 1.6F), p.x, p.y, p.z, 3, 0.4, 0.4, 0.4, 0);
        if (age % 8 == 0) FcFx.sound(sl, from, SoundEvents.BEACON_AMBIENT, 1.4F, 1.6F);
        if (age % 5 == 0) FcFx.sound(sl, p, SoundEvents.FIRECHARGE_USE, 0.6F, 0.7F);
    }
}
