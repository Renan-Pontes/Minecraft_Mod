package com.renanpontes.isekaimahou.feature.legends_a;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_a.entity.CraterBlastEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Explosões Colossais pedidas pelo efeito {@code crater}. Os pedidos do mesmo tick são juntados: uma forma de área
 * aplica o efeito em CADA alvo e no bloco do centro, mas a magia deve virar UMA explosão (no centro do impacto).
 * Também limita o custo: cratera de verdade no máx. 1 a cada 2 s por conjurador e no máx. 3 explosões "completas"
 * (com o desenho gigante) perto umas das outras.
 * <p>
 * v0.8: a cratera é a do núcleo ({@link Destruction#crater}: tigela, borda queimada, escombros, fila com orçamento por
 * tick) — esta classe só decide o tamanho (raio da cratera = 2,2 × raio da explosão, crescendo com o poder: a lenda
 * "Explosão!" com o mana todo chega a ~35) e respeita a regra de destruição ({@code /mahou destruction}), cidades
 * e Provações. Explosões grandes ainda queimam a terra em volta (SCORCH) e mandam uma onda de choque que arremessa.
 */
public final class CraterQueue {
    public static final float BASE_RADIUS = 3.0F;
    public static final float MAX_RADIUS = 14.0F;
    public static final float BASE_DAMAGE = 22.0F;
    /** Raio máximo da cratera de verdade. */
    public static final float MAX_CARVE = 35.0F;
    public static final int CARVE_COOLDOWN = 40;
    public static final int MAX_NEARBY_BLASTS = 3;
    public static final int MAX_FULL_PER_TICK = 2;

    private record Request(SpellContext ctx, Vec3 pos, SpellStats stats, boolean block) {}

    private record Done(ServerLevel level, UUID caster, Vec3 pos, float radius) {}

    private static final List<Request> PENDING = new ArrayList<>();
    private static final Map<UUID, Long> LAST_CARVE = new HashMap<>();

    public static void request(SpellContext ctx, Vec3 pos, SpellStats stats, boolean block) {
        PENDING.add(new Request(ctx, pos, stats, block));
    }

    public static void clear() {
        PENDING.clear();
        LAST_CARVE.clear();
    }

    /** Raio da cratera: 3 × área dos aumentos × um pouco do poder da conjuração (Tudo ou Nada, cântico...). */
    public static float radiusOf(SpellContext ctx, SpellStats stats) {
        float pm = Math.min(4.0F, Math.max(0.5F, ctx.powerMult()));
        return Mth.clamp(stats.radius(BASE_RADIUS) * (1.0F + 0.12F * (pm - 1.0F)), 2.5F, MAX_RADIUS);
    }

    /** Raio da cratera de verdade: 2,2 × o da explosão, +10% por ponto de poder acima de 1 (máx. {@link #MAX_CARVE}). */
    public static float carveRadiusOf(SpellContext ctx, float blastRadius) {
        float pm = Math.min(4.0F, Math.max(0.5F, ctx.powerMult()));
        return Mth.clamp(blastRadius * 2.2F * (1.0F + 0.1F * (pm - 1.0F)), 3.0F, MAX_CARVE);
    }

    public static void tick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        List<Request> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        // Bloco do impacto primeiro: é o centro de verdade da explosão
        batch.sort((a, b) -> Boolean.compare(b.block(), a.block()));
        List<Done> done = new ArrayList<>();
        for (Request r : batch) {
            float radius = radiusOf(r.ctx(), r.stats());
            boolean merged = false;
            for (Done d : done) {
                float m = Math.max(d.radius(), radius) * 1.2F;
                if (d.level() == r.ctx().level() && Objects.equals(d.caster(), r.ctx().casterId())
                        && d.pos().distanceToSqr(r.pos()) <= m * m) {
                    merged = true;
                    break;
                }
            }
            if (merged) continue;
            // Muitas explosões do mesmo conjurador no mesmo tick (ex.: Domínio com vários inimigos espalhados):
            // as 2 primeiras são completas; as outras só ferem, com um efeito leve (rede e FPS agradecem)
            long same = done.stream().filter(d -> Objects.equals(d.caster(), r.ctx().casterId())).count();
            detonate(r.ctx(), r.stats(), r.pos(), radius, same < MAX_FULL_PER_TICK);
            done.add(new Done(r.ctx().level(), r.ctx().casterId(), r.pos(), radius));
        }
    }

    // =====================================================================================
    //  A explosão
    // =====================================================================================
    private static void detonate(SpellContext ctx, SpellStats stats, Vec3 at, float R, boolean full) {
        ServerLevel sl = ctx.level();
        Vec3 ground = LegendFx.groundBelow(sl, at, R + 2.0);
        Vec3 center = ground != null ? ground : at;
        float carveR = carveRadiusOf(ctx, R);
        float blastR = Math.max(R * 1.5F, carveR * 1.1F);
        int color = LegendFx.colorOf(ctx, LegendFx.CRIMSON);

        // ---------- Dano, fogo e empurrão ----------
        Vec3 mid = center.add(0, 0.6, 0);
        for (LivingEntity e : ctx.enemiesAround(mid, blastR)) {
            double d = e.position().distanceTo(center);
            double near = 1.0 - Math.min(1.0, d / blastR);
            e.invulnerableTime = 0;
            ctx.damage(e, BASE_DAMAGE * (float) (0.4 + 0.6 * near), stats, null);
            if (e.isAlive()) {
                e.igniteForSeconds(stats.duration(120) / 20F);
                Vec3 away = new Vec3(e.getX() - center.x, 0, e.getZ() - center.z);
                away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
                double k = 0.4 + 1.8 * near;
                e.push(away.x * k, 0.45 + 0.8 * near, away.z * k);
                e.hurtMarked = true;
            }
        }

        if (!full) {
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1.0, center.z, 1, 0, 0, 0, 0);
            // v1.3: impacto de fogo montado no cliente (escala com o preset gráfico) no lugar das 30 chamas soltas
            ElementFx.impactFx(sl, center.add(0, 0.3, 0), Element.FIRE, color, 4, null, Math.min(3F, R / 3F));
            LegendFx.sound(sl, center, SoundEvents.GENERIC_EXPLODE, 3.0F, 0.6F);
            return;
        }

        // ---------- Cratera (núcleo: respeita a regra de destruição, cidades e Provações) ----------
        UUID caster = ctx.casterId();
        long now = sl.getGameTime();
        boolean carve = Destruction.allowed(sl, ctx.caster())
                && (caster == null || LAST_CARVE.getOrDefault(caster, Long.MIN_VALUE / 2) + CARVE_COOLDOWN <= now);
        int carved = 0;
        if (carve) {
            carved = Destruction.crater(sl, center, carveR, ctx.caster());
            if (carved > 0 && caster != null) LAST_CARVE.put(caster, now);
            // Quem conjurou dentro da própria cratera (a "Explosão!" chega a 35 blocos) desce devagar até o fundo
            LivingEntity self = ctx.caster();
            if (carved > 0 && self != null && self.level() == sl
                    && self.position().distanceToSqr(center) <= (carveR + 3) * (carveR + 3)) {
                self.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,
                        160, 0, false, false, true));
            }
            // Explosões grandes: a terra em volta da cratera fica queimada (a onda de calor passa depois do impacto)
            if (carved > 0 && carveR >= 10F) {
                LivingEntity who = ctx.caster();
                float scorchR = Math.min(64F, carveR * 2.1F);
                BossKit.schedule(sl, 8, () -> Destruction.surface(sl, center, scorchR, who, Destruction.Mode.SCORCH));
            }
        }

        // ---------- O desenho gigante (bola de fogo, coluna, cogumelo, onda) ----------
        float visualR = Math.max(R, Math.min(22F, carveR * 0.6F));
        int nearby = sl.getEntitiesOfClass(CraterBlastEntity.class, new AABB(center, center).inflate(48)).size();
        if (nearby < MAX_NEARBY_BLASTS || carved > 0) {
            sl.addFreshEntity(CraterBlastEntity.create(sl, center, visualR, color));
        }

        // ---------- Clarão, poeira, som e tremor ----------
        float V = visualR;
        LegendFx.far(sl, ParticleTypes.FLASH, center.x, center.y + 1.0, center.z, 3, V * 0.2, V * 0.2, V * 0.2, 0);
        LegendFx.far(sl, ParticleTypes.FLASH, center.x, center.y + V * 2.0, center.z, 2, V * 0.3, V * 0.3, V * 0.3, 0);
        LegendFx.far(sl, ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1.0, center.z, 2 + (int) (V / 2), V * 0.4, V * 0.25, V * 0.4, 0);
        LegendFx.far(sl, Vfx.flame(), center.x, center.y + 1.0, center.z, (int) (16 + V * 4), V * 0.45, V * 0.3, V * 0.45, 0.25);
        LegendFx.far(sl, ParticleTypes.LAVA, center.x, center.y + 0.5, center.z, (int) (6 + V * 2), V * 0.35, 0.4, V * 0.35, 0);
        // v1.3: nova de fogo (clarões em camadas, 3 ondas, pilar carmesim, detritos, chão fervendo) montada no
        // cliente pelo preset gráfico — substitui o "pó" vermelho que vinha do servidor; o tremor já é o de baixo
        ElementFx.nova(sl, center, Element.FIRE, color, Math.min(40F, V * 1.4F), 5, false);
        ElementFx.pillar(sl, center, LegendFx.CRIMSON, Math.max(1.0F, V * 0.18F), Math.min(64F, V * 4F), 34);
        BlockPos groundPos = BlockPos.containing(center.x, center.y - 0.05, center.z);
        BlockState st = sl.getBlockState(groundPos);
        if (!st.isAir()) {
            LegendFx.far(sl, new BlockParticleOption(ParticleTypes.BLOCK, st), center.x, center.y + 0.5, center.z,
                    (int) (40 + V * 8), V * 0.4, 0.6, V * 0.4, 0.8);
        }
        LegendFx.sound(sl, center, SoundEvents.GENERIC_EXPLODE, 6.0F + V * 0.4F, 0.5F);
        LegendFx.sound(sl, center, SoundEvents.DRAGON_FIREBALL_EXPLODE, 4.0F, 0.5F);
        LegendFx.sound(sl, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 5.0F, 0.7F);
        if (carveR >= 18F) LegendFx.sound(sl, center, SoundEvents.WARDEN_SONIC_BOOM, 5.0F, 0.4F);
        BossKit.shake(sl, center, 40 + carveR * 6, Math.min(3.0F, 0.9F + carveR * 0.09F), 30 + (int) (carveR * 2));
        if (carveR >= 18F) shockwave(ctx, sl, center, carveR);
    }

    /**
     * Explosão gigante: uma segunda onda de choque corre pelo chão até 2,5× a cratera e arremessa (sem dano extra)
     * quem a explosão não pegou — ninguém fica de pé perto de uma "Explosão!".
     */
    private static void shockwave(SpellContext ctx, ServerLevel sl, Vec3 center, float carveR) {
        float reach = Math.min(80F, carveR * 2.5F);
        for (int step = 1; step <= 8; step++) {
            final float front = reach * step / 8F;
            final int s = step;
            BossKit.schedule(sl, step * 2, () -> {
                // v1.3: a frente da onda é um anel fx (1 partícula) + poeira mais rala (era até 96 por passo)
                if (s % 2 == 1) ElementFx.shockwave(sl, center.add(0, 0.3, 0), 0xFF8A40, front + reach / 8F, 6, null);
                int n = Math.min(40, (int) (front * 1.2));
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    LegendFx.far(sl, ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x + Math.cos(a) * front, center.y + 0.4,
                            center.z + Math.sin(a) * front, 1, 0.3, 0.1, 0.3, 0.02);
                    if (i % 3 == 0) LegendFx.far(sl, ParticleTypes.FLAME, center.x + Math.cos(a) * front, center.y + 0.3,
                            center.z + Math.sin(a) * front, 1, 0.2, 0.1, 0.2, 0.02);
                }
                LivingEntity caster = ctx.caster();
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(front + 1, 8, front + 1),
                        e -> e.isAlive() && e != caster && !ctx.isAlly(e))) {
                    double dx = e.getX() - center.x, dz = e.getZ() - center.z;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > front || d < front - reach / 8F - 1) continue;
                    Vec3 dir = d < 0.01 ? new Vec3(1, 0, 0) : new Vec3(dx / d, 0, dz / d);
                    double k = 1.4 * (1.0 - d / reach) + 0.3;
                    e.push(dir.x * k, 0.35 + 0.3 * k, dir.z * k);
                    e.hurtMarked = true;
                }
                if (s % 3 == 0) {
                    LegendFx.sound(sl, center.add(Math.cos(s) * front, 0, Math.sin(s) * front), SoundEvents.GENERIC_EXPLODE, 2.0F, 0.4F);
                }
            });
        }
    }

    private CraterQueue() {}
}
