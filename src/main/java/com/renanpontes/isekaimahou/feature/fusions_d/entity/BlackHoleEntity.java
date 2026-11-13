package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Buraco Negro: uma singularidade flutuante que puxa TUDO (inimigos, itens, XP, projéteis) num raio grande. Com a
 * destruição ligada, devora o terreno: uma esfera apagada que cresce em volta do núcleo e blocos arrancados do chão
 * em volta que voam em espiral até ele. Quem chega ao horizonte de eventos é triturado; no fim ele colapsa e explode.
 */
public class BlackHoleEntity extends FdSpellEntity implements RubbleEntity.Anchor {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 3F;
    private float maxEat = 4F;
    private float eaten = 0F;
    private float lastEat = 0F;
    private int devoured;

    public BlackHoleEntity(EntityType<? extends BlackHoleEntity> type, Level level) {
        super(type, level);
    }

    public static void open(SpellContext ctx, Vec3 at, SpellStats stats) {
        ServerLevel sl = ctx.level();
        enforceCap(sl, FdRegistries.BLACK_HOLE.get(), ctx.casterId(), 1);
        BlackHoleEntity h = new BlackHoleEntity(FdRegistries.BLACK_HOLE.get(), sl);
        h.setPos(at.x, at.y, at.z);
        h.setup(ctx, stats.duration(130), stats);
        float pow = Math.min(3F, stats.power());
        h.damage = 3.0F * stats.power();
        h.maxEat = Math.min(9F, 3.5F + 1.5F * pow + 1.5F * stats.aoe);
        h.entityData.set(RADIUS, Math.min(24F, stats.radius(12F)));
        sl.addFreshEntity(h);
        FdFx.implode(sl, at.add(0, 0.75, 0), Vfx.reversePortal(), 4, 48, 0.4);
        // v1.3: o espaço se abre (portal escuro) e a luz em volta é sugada durante a vida inteira do buraco negro
        float rr = Math.min(24F, stats.radius(12F));
        com.renanpontes.isekaimahou.core.ElementFx.portal(sl, at.add(0, 0.75, 0), null, com.renanpontes.isekaimahou.core.Element.VOID,
                0x5A2A8A, 2.2F, 5, Math.min(200, stats.duration(130)));
        com.renanpontes.isekaimahou.core.ElementFx.gatherAt(sl, at.add(0, 0.75, 0), com.renanpontes.isekaimahou.core.Element.VOID,
                0x9A70E0, Math.min(10F, rr * 0.6F), 5, Math.min(80, stats.duration(130)));
        FdFx.sound(sl, at, SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.4F);
        FdFx.sound(sl, at, SoundEvents.END_PORTAL_SPAWN, 1.2F, 0.6F);
        BossKit.shake(sl, at, 24, 0.8F, 20);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(RADIUS, 12F);
        b.define(SIZE, 0.8F);
    }

    public float radius() { return entityData.get(RADIUS); }

    /** Raio visual do núcleo (cresce com o que devorou). */
    public float coreSize() { return entityData.get(SIZE); }

    @Override
    public Vec3 rubbleCenter() { return position().add(0, 0.75, 0); }

    @Override
    protected void serverTick(ServerLevel sl) {
        Vec3 c = rubbleCenter();
        float r = radius();
        float ramp = Math.min(1F, tickCount / 20F);
        // Puxão: mais forte perto do núcleo; chefes resistem
        for (Entity e : sl.getEntities((Entity) null, new AABB(c, c).inflate(r), e -> e.isAlive() && e.distanceToSqr(c) <= r * r)) {
            boolean pull = e instanceof ItemEntity || e instanceof ExperienceOrb
                    || (e instanceof Projectile p && (p.getOwner() == null || !ally(p.getOwner())))
                    || (e instanceof LivingEntity le && !ally(le) && !FdUtil.untouchable(le) && !le.isSpectator());
            if (!pull) continue;
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d < 0.05) continue;
            double strength = ramp * 0.16 * (0.4 + 1.6 * (1.0 - d / r));
            if (FdUtil.isBoss(e)) strength *= 0.3;
            // Componente tangencial: tudo gira em volta antes de cair
            Vec3 tangent = new Vec3(-to.z, 0, to.x).normalize().scale(strength * 0.45);
            Vec3 v = e.getDeltaMovement().scale(d < 2 ? 0.55 : 0.9).add(to.scale(strength / d)).add(tangent);
            if (e instanceof ServerPlayer) FdUtil.setMotion(e, v);
            else {
                e.setDeltaMovement(v);
                e.hasImpulse = true;
            }
            if (e instanceof LivingEntity le) {
                le.resetFallDistance();
                if (d < 2.4 + coreSize() && tickCount % 8 == 0) {
                    hit(le, damage, null);
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), owner());
                    sl.sendParticles(Vfx.crit(), le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 5, 0.2, 0.2, 0.2, 0.15);
                }
            } else if (e instanceof Projectile && !(e instanceof ThrownTrident) && d < 1.0 + coreSize() * 0.5) {
                // Projéteis inimigos que caem no horizonte de eventos somem (tridente não: o dono o perderia)
                e.discard();
            }
        }
        devour(sl, c, r);
        if (tickCount % 10 == 0) entityData.set(SIZE, 0.8F + Math.min(1.4F, devoured / 70F));
        if (tickCount % 24 == 0) FdFx.sound(sl, c, SoundEvents.BEACON_AMBIENT, 1.6F, 0.35F);
        if (tickCount % 40 == 5) FdFx.sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.5F);
    }

    /** Devorar o terreno: esfera apagada crescente + blocos arrancados em volta voando até o núcleo. */
    private void devour(ServerLevel sl, Vec3 c, float r) {
        LivingEntity caster = owner();
        if (!Destruction.allowed(sl, caster)) return;
        eaten = Math.min(maxEat, 1.2F + tickCount / 12F);
        if (eaten - lastEat >= 0.7F) {
            // Antes de apagar, alguns blocos da casca nova saem voando
            for (int i = 0; i < 6; i++) {
                double u = random.nextDouble() * 2 - 1, th = random.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
                Vec3 p = c.add(s * Math.cos(th) * eaten, u * eaten, s * Math.sin(th) * eaten);
                ripAt(sl, BlockPos.containing(p), c, caster, false);
            }
            devoured += Destruction.sphere(sl, c, eaten, caster, Destruction.Mode.ERASE);
            lastEat = eaten;
        }
        // Arranca blocos da superfície em volta
        if (tickCount > 10 && tickCount % 3 == 0) {
            float reach = Math.min(r * 0.8F, 4F + tickCount / 8F);
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = 1.5 + random.nextDouble() * reach;
                int x = Mth.floor(c.x + Math.cos(a) * d), z = Mth.floor(c.z + Math.sin(a) * d);
                BlockPos top = FdUtil.surface(sl, x, z, Mth.floor(c.y + 4), Mth.floor(c.y - reach));
                if (top != null) ripAt(sl, top, c, caster, true);
            }
        }
    }

    private void ripAt(ServerLevel sl, BlockPos pos, Vec3 c, LivingEntity caster, boolean erase) {
        BlockState st = sl.getBlockState(pos);
        if (!FdUtil.rubbleable(st) || Destruction.protectedAt(sl, pos, caster)) return;
        Vec3 from = Vec3.atCenterOf(pos);
        int flight = 12 + (int) (from.distanceTo(c) * 2.2);
        RubbleEntity.attract(sl, st, from, this, flight);
        if (erase) devoured += Destruction.sphere(sl, from, 0.5F, caster, Destruction.Mode.ERASE);
        if (random.nextInt(4) == 0) sl.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 3, 0.3, 0.3, 0.3, 0.02);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 c = rubbleCenter();
        float r = 4F + coreSize() * 2F;
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
        sl.sendParticles(Vfx.reversePortal(), c.x, c.y, c.z, 50, 0.2, 0.2, 0.2, 1.2);
        sl.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 30, 0.2, 0.2, 0.2, 0.6);
        // v1.3: o colapso vira uma nova do vazio (montada no cliente pelo preset gráfico)
        com.renanpontes.isekaimahou.core.ElementFx.nova(sl, c, com.renanpontes.isekaimahou.core.Element.VOID, 0x5A2A8A, Math.max(5F, r * 1.4F), 5, false);
        FdFx.shockwave(sl, c, FdFx.fade(0xE8DFFF, FdFx.VOID, 2.5F), 48, 0.9);
        FdFx.shockwave(sl, c.add(0, 0.5, 0), ParticleTypes.SONIC_BOOM, 10, 0.6);
        FdFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 3F, 0.5F);
        FdFx.sound(sl, c, SoundEvents.WARDEN_SONIC_BOOM, 2.5F, 0.6F);
        BossKit.shake(sl, c, 40, 2.2F, 26);
        for (LivingEntity le : foes(c, r)) {
            hit(le, damage * 3.5F, null);
            Vec3 out = le.position().subtract(c);
            out = out.lengthSqr() < 1.0E-3 ? new Vec3(0, 1, 0) : out.normalize();
            if (!FdUtil.isBoss(le)) FdUtil.setMotion(le, new Vec3(out.x * 1.4, 0.7, out.z * 1.4));
        }
        LivingEntity caster = owner();
        Destruction.sphere(sl, c, Math.min(12F, lastEat + 1.5F), caster, Destruction.Mode.ERASE);
    }

    @Override
    protected void clientTick() {
        float r = radius();
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = r * (0.3 + random.nextDouble() * 0.7);
            double y = (random.nextDouble() - 0.5) * 3;
            double x = Math.cos(a) * d, z = Math.sin(a) * d;
            double tx = -Math.sin(a), tz = Math.cos(a);
            level().addParticle(i == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL, getX() + x, getY() + 0.75 + y, getZ() + z,
                    -x * 0.06 + tx * 0.15, -y * 0.06, -z * 0.06 + tz * 0.15);
        }
    }
}
