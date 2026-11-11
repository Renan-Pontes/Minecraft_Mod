package com.renanpontes.isekaimahou.feature.class_shinigami.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hadō nº 90 — Kurohitsugi (Caixão Negro): uma caixa de Reiatsu negro que se ergue em volta do alvo, prende quem
 * está dentro (Enraizado + Atordoado, puxado para o centro) e é atravessada por LANÇAS de trevas quatro vezes
 * (cada lança aplica os efeitos da magia em todos lá dentro); no fim o caixão se ESMAGA (dano próprio + efeitos) e se
 * despedaça. Dura {@link #LIFE} ticks e nunca é salvo (some ao descarregar). Chefes não são puxados, só presos.
 */
public class BlackCoffinEntity extends Entity {
    public static final int LIFE = 66;
    public static final int RISE = 16;
    public static final int[] SPEARS = {22, 31, 40, 49};
    public static final int CRUSH = 58;
    /** Dano próprio do esmagamento (antes da escala do núcleo). */
    public static final float CRUSH_DAMAGE = 7.0F;

    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(BlackCoffinEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(BlackCoffinEntity.class, EntityDataSerializers.FLOAT);
    private static final DustParticleOptions VIOLET = ShinigamiFx.dust(0x7A2AC8, 1.1F);

    @Nullable
    private SpellContext ctx;
    @Nullable
    private UUID target;

    public BlackCoffinEntity(EntityType<? extends BlackCoffinEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** O caixão em volta de {@code at} (pés), com a largura/altura dadas; {@code target} é puxado para o centro. */
    public static BlackCoffinEntity of(SpellContext ctx, Vec3 at, float width, float height, @Nullable LivingEntity target) {
        BlackCoffinEntity c = new BlackCoffinEntity(ShinigamiRegistries.BLACK_COFFIN.get(), ctx.level());
        c.setPos(at.x, at.y, at.z);
        c.ctx = ctx;
        c.target = target == null ? null : target.getUUID();
        c.entityData.set(WIDTH, Math.max(1.5F, width));
        c.entityData.set(HEIGHT, Math.max(2.0F, height));
        return c;
    }

    public float width() {
        return entityData.get(WIDTH);
    }

    public float height() {
        return entityData.get(HEIGHT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WIDTH, 2.0F);
        builder.define(HEIGHT, 3.0F);
    }

    private AABB inside() {
        double h = width() / 2;
        return new AABB(getX() - h, getY(), getZ() - h, getX() + h, getY() + height(), getZ() + h);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount < RISE && tickCount % 2 == 0) {
                double h = width() / 2;
                for (int k = 0; k < 4; k++) {
                    double sx = (k & 1) == 0 ? -h : h, sz = (k & 2) == 0 ? -h : h;
                    level().addParticle(ParticleTypes.SQUID_INK, getX() + sx, getY() + height() * tickCount / RISE, getZ() + sz, 0, 0.02, 0);
                }
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (ctx == null || tickCount > LIFE) {
            discard();
            return;
        }
        AABB box = inside();
        List<LivingEntity> trapped = new ArrayList<>(sl.getEntitiesOfClass(LivingEntity.class, box.inflate(0.3),
                e -> e.isAlive() && e != ctx.caster() && !ctx.isAlly(e)));
        // o alvo principal é puxado para dentro (chefes só ficam presos)
        if (target != null && sl.getEntity(target) instanceof LivingEntity t && t.isAlive() && !trapped.contains(t)
                && t.distanceToSqr(position()) < 16 * 16) {
            trapped.add(t);
        }
        Vec3 center = position().add(0, height() * 0.5, 0);
        for (LivingEntity e : trapped) {
            if (tickCount == 1 || tickCount % 10 == 0) {
                int dur = Math.max(10, LIFE - tickCount);
                if (Statuses.isBossLike(e)) dur = Math.max(10, dur / 3);
                e.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, dur, 0), ctx.caster());
                e.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, Math.max(10, dur / 2), 0), ctx.caster());
            }
            if (!Statuses.isBossLike(e)) {
                Vec3 to = position().subtract(e.position());
                to = new Vec3(to.x, 0, to.z);
                if (to.lengthSqr() > 0.09) {
                    Vec3 pull = to.normalize().scale(Math.min(0.5, to.length() * 0.35));
                    e.setDeltaMovement(pull.x, Math.min(0.05, e.getDeltaMovement().y), pull.z);
                    e.hurtMarked = true;
                }
            }
        }
        if (tickCount % 3 == 0) walls(sl, tickCount < RISE ? tickCount / (float) RISE : 1F);
        if (tickCount == RISE) {
            ShinigamiFx.sound(sl, center, SoundEvents.IRON_DOOR_CLOSE, 1.2F, 0.4F);
            ShinigamiFx.sound(sl, center, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 0.6F);
        }
        for (int s : SPEARS) {
            if (tickCount == s) spears(sl, trapped, center);
        }
        if (tickCount == CRUSH) crush(sl, trapped, center);
    }

    /** As paredes do caixão: arestas negras e veios violeta subindo. */
    private void walls(ServerLevel sl, float grown) {
        double h = width() / 2, top = height() * grown;
        for (int k = 0; k < 4; k++) {
            double sx = (k & 1) == 0 ? -h : h, sz = (k & 2) == 0 ? -h : h;
            Vec3 a = new Vec3(getX() + sx, getY(), getZ() + sz);
            ShinigamiFx.line(sl, a, a.add(0, top, 0), ShinigamiFx.BLACK, 0.5);
        }
        for (int i = 0; i < 6; i++) {
            double u = (random.nextDouble() * 2 - 1) * h;
            double y = random.nextDouble() * top;
            int face = random.nextInt(4);
            double x = face < 2 ? (face == 0 ? -h : h) : u, z = face < 2 ? u : (face == 2 ? -h : h);
            sl.sendParticles(i % 2 == 0 ? VIOLET : ShinigamiFx.BLACK, getX() + x, getY() + y, getZ() + z, 1, 0, 0.02, 0, 0.01);
        }
    }

    /** Uma rodada de lanças de trevas atravessando o caixão: aplica os efeitos em todos lá dentro. */
    private void spears(ServerLevel sl, List<LivingEntity> trapped, Vec3 center) {
        double h = width() / 2 + 0.6;
        for (int k = 0; k < 8; k++) {
            double ang = random.nextDouble() * Math.PI * 2;
            Vec3 out = center.add(Math.cos(ang) * h, (random.nextDouble() - 0.5) * height() * 0.8, Math.sin(ang) * h);
            Vec3 in = center.add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6);
            ShinigamiFx.line(sl, out, in, k % 2 == 0 ? ShinigamiFx.BLACK : VIOLET, 0.2);
        }
        for (LivingEntity e : trapped) {
            if (!e.isAlive()) continue;
            ctx.impact(e.position().add(0, e.getBbHeight() / 2, 0), Vec3.ZERO, e);
            ctx.resolveOnEntity(e, this);
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 3, 0.2, 0.3, 0.2, 0.1);
        }
        ShinigamiFx.sound(sl, center, SoundEvents.TRIDENT_HIT, 1.0F, 0.5F);
        ShinigamiFx.sound(sl, center, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 0.4F);
    }

    /** O esmagamento final: dano próprio + efeitos e o caixão se despedaça. */
    private void crush(ServerLevel sl, List<LivingEntity> trapped, Vec3 center) {
        for (LivingEntity e : trapped) {
            if (!e.isAlive()) continue;
            ctx.damage(e, CRUSH_DAMAGE, ctx.formStats(), this);
            if (e.isAlive()) ctx.resolveOnEntity(e, this);
        }
        double h = width() / 2;
        sl.sendParticles(ShinigamiFx.BLACK, center.x, center.y, center.z, 70, h, height() * 0.4, h, 0.08);
        sl.sendParticles(VIOLET, center.x, center.y, center.z, 40, h, height() * 0.4, h, 0.12);
        sl.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 20, h, height() * 0.3, h, 0.15);
        sl.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        ShinigamiFx.sound(sl, center, SoundEvents.GLASS_BREAK, 1.2F, 0.4F);
        ShinigamiFx.sound(sl, center, SoundEvents.WITHER_BREAK_BLOCK, 0.8F, 0.6F);
        ctx.end(center, Vec3.ZERO);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 96 * 96;
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
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
