package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ondas de lâmina (Raio de Espada, eco das Espadas Lendárias, Liberações): meia-lua que voa em linha reta, atravessa
 * até N inimigos e para no primeiro bloco sólido (ou atravessa, com Fantasma). Sem entidade: posição e acertos ficam
 * aqui e o visual é de partículas. Limite global de ondas ativas para não pesar.
 */
public final class BladeWaves {
    /** Teto de ondas vivas no servidor inteiro. */
    private static final int MAX_WAVES = 96;

    /** O que fazer ao acertar um alvo / ao terminar. */
    public static final class Wave {
        final ServerLevel level;
        final LivingEntity owner;
        Vec3 pos;
        final Vec3 dir;
        final double speed;
        final double half;
        int life;
        int hitsLeft;
        final boolean ghost;
        final int rgb;
        final boolean big;
        final Set<UUID> hit = new HashSet<>();
        final Predicate<LivingEntity> valid;
        final BiConsumer<LivingEntity, Vec3> onHit;
        @Nullable
        final BiConsumer<Vec3, Vec3> onEnd;
        int age;

        Wave(ServerLevel level, LivingEntity owner, Vec3 pos, Vec3 dir, double speed, double half, int life, int hits,
             boolean ghost, int rgb, boolean big, Predicate<LivingEntity> valid, BiConsumer<LivingEntity, Vec3> onHit,
             @Nullable BiConsumer<Vec3, Vec3> onEnd) {
            this.level = level;
            this.owner = owner;
            this.pos = pos;
            this.dir = dir.normalize();
            this.speed = speed;
            this.half = half;
            this.life = life;
            this.hitsLeft = hits;
            this.ghost = ghost;
            this.rgb = rgb;
            this.big = big;
            this.valid = valid;
            this.onHit = onHit;
            this.onEnd = onEnd;
        }
    }

    private static final List<Wave> WAVES = new ArrayList<>();
    /** Ondas lançadas durante o tick das outras (entram no próximo tick, sem mexer na lista em uso). */
    private static final List<Wave> PENDING = new ArrayList<>();

    /**
     * Lança uma onda.
     *
     * @param range  alcance em blocos
     * @param half   meia-largura da lâmina (raio de acerto para os lados)
     * @param hits   quantos alvos atravessa (≥ 1)
     * @param valid  quem pode ser atingido (ex.: não aliados)
     * @param onHit  ao acertar (alvo, posição da onda)
     * @param onEnd  ao terminar (posição, direção) — sem alvo restante, bloco ou alcance
     */
    public static boolean launch(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, double speed, double range, double half,
                                 int hits, boolean ghost, int rgb, boolean big, Predicate<LivingEntity> valid,
                                 BiConsumer<LivingEntity, Vec3> onHit, @Nullable BiConsumer<Vec3, Vec3> onEnd) {
        if (WAVES.size() + PENDING.size() >= MAX_WAVES || dir.lengthSqr() < 1.0E-6) return false;
        int life = Math.max(2, (int) Math.ceil(range / Math.max(0.2, speed)));
        PENDING.add(new Wave(level, owner, from, dir, speed, half, life, Math.max(1, hits), ghost, rgb, big, valid, onHit, onEnd));
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (!PENDING.isEmpty()) {
            WAVES.addAll(PENDING);
            PENDING.clear();
        }
        if (WAVES.isEmpty()) return;
        Iterator<Wave> it = WAVES.iterator();
        List<Wave> ended = new ArrayList<>();
        while (it.hasNext()) {
            Wave w = it.next();
            // dono saiu do mundo (desconectou/morreu e sumiu): a onda some junto
            if (w.owner.isRemoved()) {
                it.remove();
                continue;
            }
            if (!step(w)) {
                it.remove();
                ended.add(w);
            }
        }
        // fora do laço: o fim pode lançar outra onda (estágio 2) sem mexer na lista durante a iteração
        for (Wave w : ended) {
            if (w.onEnd != null) w.onEnd.accept(w.pos, w.dir);
        }
    }

    /** Avança um tick. false = acabou. */
    private static boolean step(Wave w) {
        w.age++;
        int sub = w.speed > 1.25 ? 2 : 1;
        double ds = w.speed / sub;
        for (int s = 0; s < sub; s++) {
            Vec3 next = w.pos.add(w.dir.scale(ds));
            if (!w.ghost) {
                BlockHitResult bh = w.level.clip(new ClipContext(w.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w.owner));
                if (bh.getType() == HitResult.Type.BLOCK) {
                    w.pos = bh.getLocation().subtract(w.dir.scale(0.2));
                    burst(w);
                    return false;
                }
            }
            w.pos = next;
            if (hitAround(w)) return false;
        }
        // visual a cada tick: meia-lua e uma faísca de varrida
        KnightFx.crescent(w.level, w.pos, w.dir, w.half, w.rgb, w.big ? 9 : 6, w.big ? 1.6F : 1.1F);
        return --w.life > 0;
    }

    /** Acerta quem está na lâmina. true = sem acertos restantes. */
    private static boolean hitAround(Wave w) {
        double reach = w.half + 1.0;
        AABB box = new AABB(w.pos, w.pos).inflate(reach, Math.max(1.2, w.half * 0.7), reach);
        for (LivingEntity e : w.level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != w.owner && !e.isSpectator())) {
            if (w.hit.contains(e.getUUID()) || !w.valid.test(e)) continue;
            Vec3 d = KnightFx.center(e).subtract(w.pos);
            double along = d.dot(w.dir);
            double side = d.subtract(w.dir.scale(along)).length();
            if (Math.abs(along) > 0.9 + w.speed * 0.5 || side > w.half + e.getBbWidth() * 0.5) continue;
            w.hit.add(e.getUUID());
            w.onHit.accept(e, w.pos);
            if (--w.hitsLeft <= 0) {
                burst(w);
                return true;
            }
        }
        return false;
    }

    private static void burst(Wave w) {
        KnightFxNet.at(w.level, KnightFxNet.WAVE_BURST, w.pos, w.dir, (float) w.half, w.rgb, -1, w.big ? 1 : 0);
        KnightFx.sound(w.level, w.pos, SoundEvents.AMETHYST_BLOCK_BREAK, 0.7F, 1.4F);
    }

    public static void clear() {
        WAVES.clear();
        PENDING.clear();
    }

    private BladeWaves() {}
}
