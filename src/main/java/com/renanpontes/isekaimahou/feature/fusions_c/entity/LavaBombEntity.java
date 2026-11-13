package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Bomba de lava do Vulcão: rocha em brasa num arco balístico que cai EXATAMENTE no círculo telegrafado depois de
 * {@code flight} ticks. Explode queimando (Em Chamas 2), empurra e chamusca o chão. {@code scale()} = tamanho da bomba.
 */
public class LavaBombEntity extends FcSpellEntity {
    public static final double GRAVITY = 0.06;

    private Vec3 velocity = Vec3.ZERO;
    private float damage = 9F;
    private float blastRadius = 2.2F;

    public LavaBombEntity(EntityType<? extends LavaBombEntity> type, Level level) {
        super(type, level);
    }

    /** Lança de {@code from} para cair em {@code to} depois de {@code flight} ticks. */
    public static void launch(SpellContext ctx, SpellStats stats, Vec3 from, Vec3 to, int flight, float damage, float blastRadius) {
        ServerLevel sl = ctx.level();
        LavaBombEntity b = new LavaBombEntity(FcRegistries.LAVA_BOMB.get(), sl);
        b.setPos(from.x, from.y, from.z);
        b.setup(ctx, stats, Element.MAGMA, flight + 1, 0.55F + 0.1F * stats.amplify);
        double t = flight;
        b.velocity = new Vec3((to.x - from.x) / t, (to.y - from.y) / t + 0.5 * GRAVITY * t, (to.z - from.z) / t);
        b.setDeltaMovement(b.velocity);
        b.damage = damage;
        b.blastRadius = blastRadius;
        sl.addFreshEntity(b);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        Vec3 next = position().add(velocity);
        // Bateu num bloco no caminho: explode ali
        BlockPos bp = BlockPos.containing(next);
        if (age() > 3 && !sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty()) {
            setPos(next.x, next.y, next.z);
            end();
            return;
        }
        setPos(next.x, next.y, next.z);
        velocity = velocity.add(0, -GRAVITY, 0);
        setDeltaMovement(velocity);
        if (age() % 2 == 0) sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 1, 0.1, 0.1, 0.1, 0.01);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 c = position();
        for (LivingEntity le : enemiesIn(c, blastRadius + 0.5)) {
            hit(le, damage);
            Statuses.applyUpTo(le, Statuses.Kind.BURNING, 1, 120, owner(), power, 2);
            Vec3 out = FcUtil.flat(le.position().subtract(c));
            FcUtil.knock(le, new Vec3(out.x * 0.6, 0.45, out.z * 0.6));
        }
        Destruction.surface(sl, c, blastRadius * 0.6F, owner(), Destruction.Mode.SCORCH);
        sl.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 14, 0.6, 0.3, 0.6, 0.25);
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(Vfx.flame(), c.x, c.y + 0.3, c.z, 24, 0.4, 0.2, 0.4, 0.18);
        FcFx.shockwave(sl, c.add(0, 0.2, 0), FcFx.fade(FcFx.MAGMA_HOT, FcFx.MAGMA_DARK, 1.4F), 18, 0.3);
        FcFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 1.2F);
        FcFx.sound(sl, c, SoundEvents.LAVA_POP, 1.4F, 0.6F);
    }

    @Override
    protected void clientTick() {
        level().addParticle(Vfx.flame(), getX(), getY(), getZ(), 0, 0.02, 0);
        if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.LAVA, getX(), getY(), getZ(), 0, 0, 0);
    }
}
