package com.renanpontes.isekaimahou.feature.earth_wind.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tempestade (glifo lendario de Vento): nuvem negra local com chuva, rajadas que jogam os inimigos de um lado
 * para o outro (e os mantem dentro), encharca quem esta embaixo e derruba raios nos inimigos.
 * Os raios sao so visuais (nao queimam o terreno); o dano e o de magia do conjurador.
 */
public class TempestStorm extends SpellBoundEntity {
    private static final double CLOUD_HEIGHT = 10.0;
    private int nextStrike = 20;
    private Vec3 gust = Vec3.ZERO;

    public TempestStorm(EntityType<? extends TempestStorm> type, Level level) {
        super(type, level);
    }

    public static TempestStorm spawn(SpellContext ctx, Vec3 pos, float radius, int life, float power) {
        ServerLevel level = ctx.level();
        limitPerOwner(level, TempestStorm.class, ctx.casterId(), 1, 96, new AABB(pos, pos));
        TempestStorm s = new TempestStorm(EwRegistries.TEMPEST_STORM.get(), level);
        s.bind(ctx, radius, life, power);
        s.moveTo(pos.x, pos.y, pos.z, 0, 0);
        level.addFreshEntity(s);
        EwFx.sound(level, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 0.6F);
        EwFx.sound(level, pos, SoundEvents.ELYTRA_FLYING, 1.5F, 0.6F);
        EwUtil.shake(level, pos, radius * 2, 0.5F, 20);
        return s;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) return;
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        double r = radius();
        AABB box = new AABB(getX() - r, getY() - 3, getZ() - r, getX() + r, getY() + CLOUD_HEIGHT, getZ() + r);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> isEnemy(e) && EwUtil.horizontal(e.position().subtract(position())).length() <= r);

        // rajadas mudam de direcao; quem tenta sair e puxado de volta
        if (tickCount % 30 == 1) {
            double a = random.nextDouble() * Math.PI * 2;
            gust = new Vec3(Math.cos(a), 0, Math.sin(a));
            EwFx.sound(level, position(), SoundEvents.BREEZE_IDLE_AIR, 1.5F, 0.5F);
        }
        for (LivingEntity e : victims) {
            Vec3 rel = EwUtil.horizontal(e.position().subtract(position()));
            double dist = rel.length();
            Vec3 push = gust.scale(0.06);
            if (dist > r * 0.75 && dist > 1.0E-3) push = push.add(rel.scale(-0.12 / dist));
            EwUtil.push(e, push, 0);
            if (tickCount % 40 == 0) e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 80, 0), ownerEntity());
        }

        if (--nextStrike <= 0) {
            // ~12 raios em 15 s, espalhados entre os inimigos (dano total alto, mas nao instantaneo)
            nextStrike = 16 + random.nextInt(16);
            strike(level, victims);
        }
        if (tickCount % 15 == 0) EwFx.sound(level, position(), SoundEvents.WEATHER_RAIN, 1.2F, 0.8F);
    }

    /** Raio num inimigo aleatorio (ou num ponto qualquer, para o espetaculo). */
    private void strike(ServerLevel level, List<LivingEntity> victims) {
        LivingEntity target = victims.isEmpty() ? null : victims.get(random.nextInt(victims.size()));
        Vec3 at;
        if (target != null) {
            at = target.position();
        } else {
            double a = random.nextDouble() * Math.PI * 2;
            double d = random.nextDouble() * radius();
            int x = (int) Math.floor(getX() + Math.cos(a) * d);
            int z = (int) Math.floor(getZ() + Math.sin(a) * d);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            at = new Vec3(x + 0.5, y, z + 0.5);
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at.x, at.y, at.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        if (target != null) {
            SpellContext c = context();
            if (c != null) c.damage(target, 7.0F * power, new SpellStats(), this, Element.WIND);
            else target.hurt(level.damageSources().lightningBolt(), 7.0F * power);
            target.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 20, 0), ownerEntity());
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1, at.z, 20, 0.4, 0.8, 0.4, 0.2);
        }
        BlockPos ground = BlockPos.containing(at).below();
        EwFx.debris(level, at.add(0, 0.2, 0), EwFx.groundState(level, ground), 10, 0.4);
    }

    private LivingEntity ownerEntity() {
        SpellContext c = context();
        return c != null ? c.caster() : null;
    }

    @Override
    protected void expire() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + CLOUD_HEIGHT, getZ(), 60, radius() * 0.5, 0.5, radius() * 0.5, 0.05);
            EwFx.sound(level, position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 1.2F);
        }
        discard();
    }

    // =====================================================================================
    //  Visual (cliente): nuvem negra, chuva, vento e faiscas
    // =====================================================================================

    private void clientFx() {
        Level level = level();
        boolean reduced = MahouConfig.REDUCED_PARTICLES.get();
        double r = radius();
        int left = life() - tickCount;
        double fade = left < 30 ? Math.max(0.15, left / 30.0) : Math.min(1.0, tickCount / 20.0 + 0.2);
        int clouds = (int) ((reduced ? 4 : 10) * fade);
        for (int i = 0; i < clouds; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * r;
            level.addParticle(ParticleTypes.LARGE_SMOKE, getX() + Math.cos(a) * d, getY() + CLOUD_HEIGHT + random.nextDouble(),
                    getZ() + Math.sin(a) * d, Math.cos(a + 1.5) * 0.05, -0.01, Math.sin(a + 1.5) * 0.05);
        }
        int rain = (int) ((reduced ? 6 : 18) * fade);
        for (int i = 0; i < rain; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * r;
            level.addParticle(ParticleTypes.FALLING_WATER, getX() + Math.cos(a) * d, getY() + 2 + random.nextDouble() * (CLOUD_HEIGHT - 2),
                    getZ() + Math.sin(a) * d, 0, -0.5, 0);
        }
        if (random.nextInt(3) == 0) {
            double a = tickCount * 0.2 + random.nextDouble();
            double d = r * (0.3 + random.nextDouble() * 0.7);
            level.addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * d, getY() + 0.5 + random.nextDouble() * 3,
                    getZ() + Math.sin(a) * d, -Math.sin(a) * 0.3, 0.02, Math.cos(a) * 0.3);
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + (random.nextDouble() - 0.5) * r,
                    getY() + CLOUD_HEIGHT - 0.5, getZ() + (random.nextDouble() - 0.5) * r, 0, -0.1, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ElementFx.dust(Element.WIND, 1.5F), getX() + (random.nextDouble() - 0.5) * r * 2,
                    getY() + 1 + random.nextDouble() * 4, getZ() + (random.nextDouble() - 0.5) * r * 2, 0, 0, 0);
        }
    }
}
