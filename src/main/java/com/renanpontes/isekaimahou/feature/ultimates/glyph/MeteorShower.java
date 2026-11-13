package com.renanpontes.isekaimahou.feature.ultimates.glyph;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.ultimates.MeteorImpact;
import com.renanpontes.isekaimahou.feature.ultimates.UltFx;
import com.renanpontes.isekaimahou.feature.ultimates.UltNet;
import com.renanpontes.isekaimahou.feature.ultimates.entity.MeteorEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sequência da Chuva de Meteoros, rodada tick a tick pelo {@link SpellScheduler}:
 * 1,5 s para o céu escurecer e o círculo rúnico se abrir no alto; depois as rochas caem espaçadas (com um pouco de
 * acaso) até o fim do tempo. Cada rocha é um {@link MeteorEntity} com os efeitos da magia (poder ×0,85, a chuva
 * acerta muitas vezes), cratera própria e onda de choque. A Carga de Ultimate não é recarregada pela própria chuva.
 */
final class MeteorShower implements Runnable {
    static final int PREP = 30;
    /** Poder de cada rocha (são muitas). */
    static final float ROCK_POWER = 0.85F;
    private static final AtomicInteger IDS = new AtomicInteger(1);

    private final SpellContext ctx;
    private final ServerLevel level;
    private final Vec3 center;
    private final float radius;
    private final int count;
    private final int duration;
    private final int rgb;
    private final double skyY;
    private final int[] times;
    private final int omenId = IDS.getAndIncrement();
    private final float sizeBoost;
    private final boolean homing;
    private final float rockRadius;
    private SpellStack.Pending pending;
    private boolean aborted;
    private int spawned;
    private int tick;
    /** Duração total do presságio no céu (ticks), para reenviar o que falta a quem chega depois. */
    private int omenTotal;

    MeteorShower(SpellContext ctx, Vec3 center, float radius, int count, int duration) {
        this.ctx = ctx.copy(ctx.powerMult() * ROCK_POWER);
        this.level = ctx.level();
        this.center = center;
        this.radius = radius;
        this.count = count;
        this.duration = duration;
        this.rgb = UltFx.rgb(ctx);
        SpellStats s = ctx.formStats();
        this.sizeBoost = 0.9F + 0.25F * Math.max(0, s.amplify) + 0.15F * Math.max(0, s.charge);
        this.homing = s.homing;
        this.rockRadius = Mth.clamp(3.5F + 0.8F * Math.max(0, s.aoe), 3F, 8F);
        this.skyY = Math.min(level.getMaxBuildHeight() - 4, center.y + 46);
        // Horários de queda: espalhados pela duração, com um pouco de acaso dentro de cada fatia
        RandomSource rnd = level.random;
        this.times = new int[count];
        float slice = (duration - PREP) / (float) count;
        for (int i = 0; i < count; i++) times[i] = PREP + (int) (slice * i + rnd.nextFloat() * slice * 0.8F);
    }

    void start() {
        LivingEntity caster = ctx.caster();
        int total = PREP + duration + 40;
        omenTotal = total;
        if (caster != null) {
            pending = SpellStack.push(level, caster, center, radius, PREP + duration, SpellStack.Tier.ULTIMATE,
                    Component.translatable("glyph.isekai_mahou.meteor_shower"), ctx.element(), this::abort);
        }
        UltNet.sendNear(level, center, radius + 112, new UltNet.SkyOmen(omenId, center, radius, (float) (skyY - center.y), total, rgb));
        UltFx.sound(level, center, SoundEvents.END_PORTAL_SPAWN, 5F, 0.6F);
        UltFx.sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 6F, 0.45F);
        UltFx.sound(level, center.add(0, skyY - center.y, 0), SoundEvents.WITHER_SPAWN, 3F, 0.5F);
        BossKit.shake(level, center, radius + 48, 0.6F, 30);
        SpellScheduler.schedule(1, this);
    }

    /** Contrariada (Pilha): para de cair rocha e o céu volta (as que já estão no ar ainda caem). */
    private void abort() {
        if (aborted) return;
        aborted = true;
        UltNet.sendNear(level, center, radius + 160, new UltNet.SkyOmen(omenId, center, radius, (float) (skyY - center.y), 0, rgb));
        UltFx.far(level, Vfx.reversePortal(), center.x, skyY, center.z, 120, radius * 0.4, 1.0, radius * 0.4, 0.3);
        UltFx.sound(level, center.add(0, skyY - center.y, 0), SoundEvents.GLASS_BREAK, 6F, 0.4F);
    }

    @Override
    public void run() {
        tick++;
        if (aborted) return;
        if (!UltFx.casterReady(ctx) && spawned < count) {
            // Quem conjurou morreu, saiu ou mudou de dimensão: a fenda se fecha
            if (pending != null) SpellStack.cancel(pending);
            abort();
            return;
        }
        skyFx();
        // Quem chegou perto depois do início (ou trocou de dimensão e voltou) também vê o céu escarlate
        if (tick % 40 == 0 && omenTotal - tick > 0) {
            UltNet.sendNear(level, center, radius + 112,
                    new UltNet.SkyOmen(omenId, center, radius, (float) (skyY - center.y), omenTotal - tick, rgb));
        }
        while (spawned < count && times[spawned] <= tick) {
            spawnOne();
            spawned++;
        }
        if (spawned >= count && tick >= times[count - 1] + 50) {
            if (pending != null) SpellStack.resolve(pending);
            ctx.end(center.add(0, 1, 0), new Vec3(0, -1, 0));
            return;
        }
        SpellScheduler.schedule(1, this);
    }

    private void skyFx() {
        RandomSource rnd = level.random;
        float open = Mth.clamp(tick / (float) PREP, 0F, 1F);
        if (tick % 3 == 0) {
            double r = radius * 0.55 * open;
            int n = 12;
            for (int i = 0; i < n; i++) {
                double a = tick * 0.05 + Math.PI * 2 * i / n;
                UltFx.far(level, Vfx.reversePortal(), center.x + Math.cos(a) * r, skyY, center.z + Math.sin(a) * r,
                        1, 0.2, 0.1, 0.2, 0.01);
                if (i % 3 == 0) UltFx.far(level, ParticleTypes.FLAME, center.x + Math.cos(a) * r, skyY - 0.5,
                        center.z + Math.sin(a) * r, 1, 0.2, 0.1, 0.2, 0.01);
            }
        }
        if (tick > PREP / 2 && tick % 2 == 0) {
            // Gotas de lava e brasas pingando da fenda
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * radius * 0.5;
            UltFx.far(level, Vfx.lavaDrip(), center.x + Math.cos(a) * d, skyY - 1, center.z + Math.sin(a) * d,
                    2, 0.5, 0.2, 0.5, 0);
        }
        if (tick % 40 == 5) {
            UltFx.sound(level, center.add(0, (skyY - center.y) * 0.5, 0), SoundEvents.LIGHTNING_BOLT_THUNDER, 3F, 0.35F + rnd.nextFloat() * 0.2F);
        }
    }

    private void spawnOne() {
        RandomSource rnd = level.random;
        Vec3 point = null;
        LivingEntity caster = ctx.caster();
        if (rnd.nextFloat() < (homing ? 0.7F : 0.35F)) {
            List<LivingEntity> foes = UltFx.foesAround(ctx, caster, center, radius + 4);
            if (!foes.isEmpty()) point = foes.get(rnd.nextInt(foes.size())).position();
        }
        if (point == null) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * radius;
            point = UltFx.groundBelow(level, new Vec3(center.x + Math.cos(a) * d, center.y + 12, center.z + Math.sin(a) * d), 40);
        }
        double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * radius * 0.4;
        // Debaixo de teto/montanha a rocha nasce logo abaixo dele (nunca dentro do bloco)
        Vec3 start = MeteorForm.belowCeiling(level, point,
                new Vec3(center.x + Math.cos(a) * d, skyY - 1.5, center.z + Math.sin(a) * d));
        float size = (1.0F + rnd.nextFloat() * 0.9F) * sizeBoost;
        float speed = 1.3F + rnd.nextFloat() * 0.5F;
        int fall = Math.max(8, (int) Math.ceil(start.distanceTo(point) / speed));
        float crater = Mth.clamp(1.8F + 1.0F * size, 2F, 5.5F);
        MeteorEntity m = MeteorEntity.create(ctx, start, point, fall, size, UltFx.mix(MeteorImpact.FIRE, rgb, 0.35F), rockRadius,
                crater, 3F + 1.2F * size, true, 0.7F);
        level.addFreshEntity(m);
        Telegraph.show(level, Telegraph.Shape.circle(point, rockRadius).warmup(fall).noRunes()
                .color(UltFx.mix(MeteorImpact.FIRE, rgb, 0.4F)));
        UltFx.far(level, Vfx.flame(), start.x, start.y, start.z, 8, 0.6, 0.3, 0.6, 0.05);
        if (spawned % 2 == 0) UltFx.sound(level, start, SoundEvents.BLAZE_SHOOT, 2.5F, 0.5F + rnd.nextFloat() * 0.2F);
    }
}
