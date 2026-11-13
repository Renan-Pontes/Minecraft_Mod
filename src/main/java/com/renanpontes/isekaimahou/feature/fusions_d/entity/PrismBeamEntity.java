package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import java.util.HashSet;
import java.util.Set;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Canhão Prismático: um feixe branco sai do conjurador e atravessa um prisma de cristal que se forma à frente; o
 * prisma o divide em SETE feixes coloridos em leque, cada um de um elemento (vermelho = Fogo, laranja = Sol,
 * amarelo = Raio, verde = Natureza, azul = Gelo, anil = Abismo, violeta = Cristal) — reações entre eles acontecem
 * de verdade (anil molha, amarelo eletrocuta...). Os feixes varrem um pouco de um lado para o outro.
 */
public class PrismBeamEntity extends FdSpellEntity {
    private static final EntityDataAccessor<Vector3f> ORIGIN = SynchedEntityData.defineId(PrismBeamEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(PrismBeamEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> SPREAD = SynchedEntityData.defineId(PrismBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(PrismBeamEntity.class, EntityDataSerializers.FLOAT);

    public static final int[] COLORS = {0xFF3A3A, 0xFF9A2A, 0xFFE83A, 0x48E868, 0x44AEFF, 0x5A5CF0, 0xC060FF};
    private static final Element[] ELEMENTS = {Element.FIRE, Element.SUN, Element.LIGHTNING, Element.NATURE, Element.ICE,
            Element.ABYSS, Element.CRYSTAL};
    public static final int LIFE = 28;

    private float damage = 2.4F;

    public PrismBeamEntity(EntityType<? extends PrismBeamEntity> type, Level level) {
        super(type, level);
    }

    public static void fire(SpellContext ctx, Vec3 origin, Vec3 dir, SpellStats stats) {
        ServerLevel sl = ctx.level();
        dir = dir.normalize();
        Vec3 prism = origin.add(dir.scale(3.2));
        PrismBeamEntity p = new PrismBeamEntity(FdRegistries.PRISM_BEAM.get(), sl);
        p.setPos(prism.x, prism.y, prism.z);
        p.setup(ctx, stats.duration(LIFE), stats);
        p.damage = 2.4F * stats.power();
        p.entityData.set(ORIGIN, origin.toVector3f());
        p.entityData.set(DIR, dir.toVector3f());
        p.entityData.set(SPREAD, Math.min(80F, 36F + 12F * stats.aoe));
        p.entityData.set(LENGTH, Math.min(40F, 22F * (1F + 0.25F * stats.aoe)));
        sl.addFreshEntity(p);
        FdFx.sound(sl, origin, SoundEvents.BEACON_ACTIVATE, 1.4F, 1.6F);
        FdFx.sound(sl, prism, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F, 1.4F);
        FdFx.sound(sl, prism, SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 1.8F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ORIGIN, new Vector3f());
        b.define(DIR, new Vector3f(0, 0, 1));
        b.define(SPREAD, 36F);
        b.define(LENGTH, 22F);
    }

    public Vec3 origin() { return new Vec3(entityData.get(ORIGIN)); }

    public Vec3 dir() { return new Vec3(entityData.get(DIR)); }

    public float length() { return entityData.get(LENGTH); }

    /** Direção do feixe colorido i (0–6) no tempo t: leque em volta da direção principal, varrendo devagar. */
    public Vec3 beamDir(int i, float t) {
        Vec3 d = dir();
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(d).normalize();
        float spread = entityData.get(SPREAD);
        double sweep = Math.sin(t * 0.18) * 5.0;
        double ang = Math.toRadians(-spread / 2 + spread * i / 6.0 + sweep);
        double tilt = Math.toRadians(Math.sin(t * 0.25 + i) * 2.0);
        return d.scale(Math.cos(ang)).add(side.scale(Math.sin(ang))).add(up.scale(Math.sin(tilt))).normalize();
    }

    /** Fim do feixe a partir do prisma (para no primeiro bloco). */
    public Vec3 beamEnd(Vec3 from, Vec3 dir, double len) {
        Vec3 to = from.add(dir.scale(len));
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        if (tickCount % 4 != 1) return;
        Vec3 prism = position();
        // Feixe branco (conjurador → prisma)
        beamHits(sl, origin(), prism, damage * 1.2F, null, -1);
        for (int i = 0; i < 7; i++) {
            Vec3 end = beamEnd(prism, beamDir(i, tickCount), length());
            beamHits(sl, prism, end, damage, ELEMENTS[i], i);
            if (tickCount % 8 == 1) sl.sendParticles(FdFx.dust(COLORS[i], 1.6F), end.x, end.y, end.z, 4, 0.2, 0.2, 0.2, 0);
        }
        if (tickCount % 8 == 1) FdFx.sound(sl, prism, SoundEvents.BEACON_AMBIENT, 1.2F, 1.8F);
    }

    private void beamHits(ServerLevel sl, Vec3 from, Vec3 to, float dmg, Element el, int color) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 n = d.scale(1 / len);
        Set<LivingEntity> done = new HashSet<>();
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0),
                e -> e.isAlive() && !e.isSpectator() && !ally(e))) {
            Vec3 c = le.position().add(0, le.getBbHeight() * 0.5, 0);
            double t = c.subtract(from).dot(n);
            if (t < 0 || t > len) continue;
            double r = le.getBbWidth() * 0.5 + 0.6;
            if (c.subtract(from.add(n.scale(t))).lengthSqr() > r * r || !done.add(le)) continue;
            hit(le, dmg, el);
            if (color == 3) le.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), owner());
            if (color == 6) Statuses.apply(le, Statuses.Kind.EXPOSED, 1, 60, owner(), 1F);
            sl.sendParticles(color < 0 ? Vfx.endRod() : FdFx.dust(COLORS[color], 1.2F), c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.02);
        }
    }
}
