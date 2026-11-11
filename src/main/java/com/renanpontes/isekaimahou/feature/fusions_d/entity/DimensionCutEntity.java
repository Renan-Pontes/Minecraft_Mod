package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Corte Dimensional: uma linha fina de luz violeta risca o espaço e, um instante depois, o próprio espaço se ABRE ao
 * longo dela — um rasgo para o vazio. Tudo no plano do corte é fatiado (ignora armadura, atravessa paredes, Sangrando)
 * e o terreno é cortado de verdade: uma fenda limpa e funda no chão e nas paredes.
 */
public class DimensionCutEntity extends FdSpellEntity {
    public static final int LINE = 0, OPEN = 1;
    public static final int LINE_TICKS = 7;
    private static final EntityDataAccessor<Vector3f> FROM = SynchedEntityData.defineId(DimensionCutEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> TO = SynchedEntityData.defineId(DimensionCutEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(DimensionCutEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 14F;
    private SpellStats stats = new SpellStats();

    public DimensionCutEntity(EntityType<? extends DimensionCutEntity> type, Level level) {
        super(type, level);
    }

    public static void slash(SpellContext ctx, Vec3 from, Vec3 to, SpellStats stats) {
        ServerLevel sl = ctx.level();
        DimensionCutEntity c = new DimensionCutEntity(FdRegistries.DIMENSION_CUT.get(), sl);
        Vec3 mid = from.lerp(to, 0.5);
        c.setPos(mid.x, mid.y, mid.z);
        c.setup(ctx, LINE_TICKS + 34, stats);
        c.stats = stats;
        c.damage = 14F;
        c.entityData.set(FROM, from.toVector3f());
        c.entityData.set(TO, to.toVector3f());
        c.entityData.set(HEIGHT, Math.min(16F, 7F + 2F * Math.max(0, stats.amplify)));
        sl.addFreshEntity(c);
        FdFx.sound(sl, from, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.4F, 1.6F);
        FdFx.sound(sl, mid, SoundEvents.AMETHYST_BLOCK_CHIME, 2F, 0.5F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(FROM, new Vector3f());
        b.define(TO, new Vector3f());
        b.define(HEIGHT, 7F);
    }

    public Vec3 from() { return new Vec3(entityData.get(FROM)); }

    public Vec3 to() { return new Vec3(entityData.get(TO)); }

    public float height() { return entityData.get(HEIGHT); }

    @Override
    protected void serverTick(ServerLevel sl) {
        if (state() == LINE && tickCount >= LINE_TICKS) cut(sl);
    }

    private void cut(ServerLevel sl) {
        setState(OPEN);
        Vec3 a = from(), b = to();
        Vec3 d = b.subtract(a);
        double len = d.length();
        Vec3 n = d.scale(1 / Math.max(1.0E-4, len));
        float h = height();
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(2, h / 2 + 1, 2),
                e -> e.isAlive() && !e.isSpectator() && !ally(e) && !FdUtil.untouchable(e))) {
            Vec3 c = FdUtil.center(le);
            double t = c.subtract(a).dot(n);
            if (t < -0.5 || t > len + 0.5) continue;
            Vec3 p = a.add(n.scale(Math.max(0, Math.min(len, t))));
            double side = Math.hypot(c.x - p.x, c.z - p.z);
            if (side > le.getBbWidth() * 0.5 + 1.1) continue;
            if (le.getY() > p.y + h / 2 || le.getY() + le.getBbHeight() < p.y - h / 2) continue;
            if (ctx != null) FdUtil.typedSpellDamage(ctx, le, damage, stats, FdRegistries.DIMENSION_SLASH_DAMAGE, ctx.element(), this);
            Statuses.apply(le, Statuses.Kind.BLEEDING, 3, 120, owner(), ctx != null ? ctx.powerMult() : 1F);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0);
            sl.sendParticles(FdFx.dust(0x8B0012, 1.4F), c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0);
        }
        // Corta o terreno: fenda fina e alta (de verdade, com a regra de destruição)
        Destruction.line(sl, a, b, 0.9F, h, owner(), Destruction.Mode.ERASE);
        for (int i = 0; i <= 10; i++) {
            Vec3 q = a.add(d.scale(i / 10.0));
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, q.x, q.y, q.z, 6, 0.1, h * 0.3, 0.1, 0.05);
        }
        FdFx.sound(sl, a.lerp(b, 0.5), SoundEvents.WARDEN_SONIC_BOOM, 1.6F, 1.5F);
        FdFx.sound(sl, a.lerp(b, 0.5), SoundEvents.GLASS_BREAK, 2F, 0.4F);
        FdFx.sound(sl, a, SoundEvents.PLAYER_ATTACK_SWEEP, 2F, 0.5F);
        BossKit.shake(sl, a.lerp(b, 0.5), 24, 1.2F, 10);
    }

    @Override
    protected void clientTick() {
        if (state() != OPEN) return;
        Vec3 a = from(), b = to();
        for (int i = 0; i < 2; i++) {
            Vec3 q = a.lerp(b, random.nextDouble());
            level().addParticle(ParticleTypes.REVERSE_PORTAL, q.x, q.y + (random.nextDouble() - 0.5) * height(), q.z, 0, 0, 0);
        }
    }
}
