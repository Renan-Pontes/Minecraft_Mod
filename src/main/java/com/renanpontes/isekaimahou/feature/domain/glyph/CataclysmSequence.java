package com.renanpontes.isekaimahou.feature.domain.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.feature.domain.DomainFx;
import com.renanpontes.isekaimahou.feature.domain.UltimateCharge;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sequência do Cataclismo, rodada tick a tick pelo {@link SpellScheduler}:
 * <ol>
 *   <li>30 ticks de preparação: pilar de energia do elemento subindo ao céu, espirais descendo,
 *       anéis de aviso no chão e tremor (tela treme, poeira sobe do chão);</li>
 *   <li>impacto: clarão, explosões, som fortíssimo e os efeitos da magia em toda a área;</li>
 *   <li>duas ondas de choque que se espalham pelo chão e arremessam os inimigos para fora.</li>
 * </ol>
 * Todas as partículas usam {@link DomainFx#far} porque o conjurador pode estar a 48 blocos do alvo.
 * Magias multi-elemento: cada braço da espiral e cada explosão do impacto usa um dos elementos.
 * Magias programáveis: o impacto avisa o núcleo (Ao Acertar) e o fim das ondas dispara Ao Terminar.
 */
final class CataclysmSequence implements Runnable {
    static final int PREP = 30;
    private static final int WAVE = 12;
    private static final int WAVE_GAP = 5;

    private final SpellContext ctx;
    private final ServerLevel level;
    private final Vec3 center;
    private final float radius;
    private final Element element;
    /** Elementos da magia (o primário primeiro) para o visual. */
    private final List<Element> elements;
    /** Direção horizontal "para fora" (do conjurador para o alvo): rumo dos estágios seguintes. */
    private final Vec3 outward;
    private final float height;
    private final float volume;
    private final BlockState ground;
    private final Set<UUID> pushed = new HashSet<>();
    private int tick;

    CataclysmSequence(SpellContext ctx, Vec3 center, float radius) {
        // Cópia própria com o poder do Cataclismo: o original pode ser reusado pelo Repetir da forma, e multiplicar
        // nele acumularia (×2, ×4...). A cópia (v0.7) mantém estágio, origem, visões por elemento e o orçamento das
        // ligações; efeitos atrasados também saem com o poder cheio.
        this.ctx = ctx.copy(ctx.powerMult() * CataclysmForm.POWER);
        this.level = ctx.level();
        this.center = center;
        this.radius = radius;
        this.element = ctx.element();
        List<Element> els = new ArrayList<>();
        els.add(ctx.primaryElement());
        for (Element e : ctx.rootSpell().elements()) if (!els.contains(e)) els.add(e);
        this.elements = List.copyOf(els);
        Vec3 flat = center.subtract(ctx.eyePos()).multiply(1, 0, 1);
        this.outward = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : ctx.look();
        this.height = 18 + radius;
        this.volume = ctx.formStats().silent ? 0.4F : 1.0F;
        BlockState g = level.getBlockState(BlockPos.containing(center).below());
        this.ground = g.isAir() ? Blocks.DIRT.defaultBlockState() : g;
    }

    void start() {
        sound(SoundEvents.WARDEN_SONIC_CHARGE, 4.0F, 0.7F);
        sound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.5F);
        SpellScheduler.schedule(1, this);
    }

    @Override
    public void run() {
        tick++;
        if (tick < PREP) prepare();
        else if (tick == PREP) impact();
        else waves(tick - PREP);
        if (tick < PREP + WAVE + WAVE_GAP) SpellScheduler.schedule(1, this);
        else if (ctx.caster() != null && ctx.caster().isAlive()) ctx.end(center.add(0, 1, 0), outward);
    }

    // =====================================================================================
    //  Preparação
    // =====================================================================================
    private void prepare() {
        float p = tick / (float) PREP;
        RandomSource rnd = level.random;
        ParticleOptions dust = ElementFx.dust(element, 2.0F);
        ParticleOptions sig = ElementFx.signature(element);

        // Pilar: coluna que vai se estreitando (a energia se concentra)
        double colR = 1.6 * (1 - p) + 0.3;
        for (int i = 0; i < 8; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double y = rnd.nextDouble() * height * Math.min(1.0, p * 2.5);
            far(dust, center.x + Math.cos(a) * colR, center.y + y, center.z + Math.sin(a) * colR, 1, 0.05, 0.2, 0.05, 0);
        }
        far(sig, center.x, center.y + rnd.nextDouble() * height * p, center.z, 2, colR * 0.5, 0.5, colR * 0.5, 0.02);
        if (tick % 2 == 0) far(ParticleTypes.END_ROD, center.x, center.y + height * p, center.z, 2, 0.3, 0.3, 0.3, 0.02);

        // Espirais: três braços que descem do topo do pilar até o chão girando (um elemento por braço)
        double armR = radius * (1 - p) * 0.9 + 0.6;
        for (int k = 0; k < 3; k++) {
            ParticleOptions armDust = k == 0 ? dust : ElementFx.dust(elements.get(k % elements.size()), 2.0F);
            for (int s = 0; s < 4; s++) {
                double f = s / 4.0;
                double a = tick * 0.45 + k * Math.PI * 2 / 3 + f * 1.4;
                double rr = armR * (0.35 + 0.65 * f);
                double y = height * (1 - f) * (1 - p * 0.6);
                far(armDust, center.x + Math.cos(a) * rr, center.y + y, center.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            }
        }

        // Anéis no chão: o de aviso (borda da área) e um interno que gira e se fecha
        if (tick % 3 == 0) ring(center.y + 0.15, radius, Math.max(24, (int) (radius * 5)), dust, 0);
        if (tick % 2 == 0) {
            double inner = radius * (0.6 - 0.3 * p);
            int n = Math.max(12, (int) (inner * 4));
            double rot = tick * 0.2;
            for (int i = 0; i < n; i++) {
                double a = rot + Math.PI * 2 * i / n;
                far(sig, center.x + Math.cos(a) * inner, center.y + 0.2, center.z + Math.sin(a) * inner, 1, 0, 0.02, 0, 0.0);
            }
        }

        // Tremor: tela treme cada vez mais e o chão solta poeira
        if (tick % 5 == 1) {
            DomainFx.shake(level, center, radius * 4 + 16, 0.4F + p * 0.9F, 8);
            BlockParticleOption pillar = new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground);
            for (int i = 0; i < 6; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(rnd.nextDouble()) * radius;
                far(pillar, center.x + Math.cos(a) * d, center.y + 0.1, center.z + Math.sin(a) * d, 4, 0.3, 0.0, 0.3, 0.0);
            }
            double a = rnd.nextDouble() * Math.PI * 2;
            level.playSound(null, center.x + Math.cos(a) * radius * 0.5, center.y, center.z + Math.sin(a) * radius * 0.5,
                    SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 2.5F * volume, 0.5F);
        }
        if (tick == PREP / 2) sound(ElementFx.castSoundOf(element), 3.0F, 0.5F);
    }

    // =====================================================================================
    //  Impacto
    // =====================================================================================
    private void impact() {
        sound(SoundEvents.GENERIC_EXPLODE.value(), 6.0F, 0.6F);
        sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 0.8F);
        sound(SoundEvents.WARDEN_SONIC_BOOM, 4.0F, 0.6F);
        sound(ElementFx.castSoundOf(element), 4.0F, 0.5F);

        far(ParticleTypes.FLASH, center.x, center.y + 1, center.z, 2, 0, 0, 0, 0);
        far(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z, 1 + (int) (radius / 6), radius * 0.3, 0.5, radius * 0.3, 0);
        int n = elements.size();
        for (Element e : elements) {
            far(ElementFx.dust(e, 2.5F), center.x, center.y + 2, center.z, 220 / n, radius * 0.5, radius * 0.25, radius * 0.5, 0);
            far(ElementFx.signature(e), center.x, center.y + 1, center.z, 120 / n, radius * 0.45, 1.0, radius * 0.45, 0.08);
        }
        // Coluna de luz: o pilar "desaba" sobre o alvo
        for (int y = 0; y < height; y += 1) {
            far(ElementFx.dust(element, 3.0F), center.x, center.y + y, center.z, 3, 0.35, 0.3, 0.35, 0);
        }
        DomainFx.shake(level, center, radius * 5 + 24, 2.6F, 26);
        // v0.8: o impacto abre uma cratera de verdade (a API respeita a regra de destruição, cidades e Provações)
        float craterR = Mth.clamp(radius * 0.55F, 5F, 36F);
        if (Destruction.crater(level, center, craterR, ctx.caster()) > 0) {
            // Quem conjurou perto demais do próprio impacto desce devagar para o fundo (não morre na queda)
            LivingEntity caster = ctx.caster();
            if (caster != null && caster.level() == level && caster.position().distanceToSqr(center) <= (craterR + 3) * (craterR + 3)) {
                caster.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,
                        120, 0, false, false, true));
            }
        }

        // Magia programável (Cataclismo → Ao Acertar → ...): cada inimigo atingido conta como impacto (o núcleo limita
        // os disparos); sem ninguém na área, o impacto é no chão. Avisado ANTES dos efeitos.
        Vec3 core = center.add(0, 1, 0);
        List<LivingEntity> victims = new ArrayList<>(ctx.enemiesAround(core, radius));
        victims.removeIf(e -> e instanceof ArmorStand || e.isSpectator());
        victims.sort(Comparator.comparingDouble(e -> e.distanceToSqr(core)));
        if (victims.isEmpty()) ctx.impact(core, outward, null);
        for (LivingEntity v : victims) ctx.impact(v.getBoundingBox().getCenter(), outward, v);

        // Os efeitos da magia em toda a área (entidades + bloco central). A ultimate não recarrega a si mesma.
        float[] charge = UltimateCharge.snapshot(ctx.caster());
        ctx.resolveArea(core, radius, null, true);
        UltimateCharge.restore(ctx.caster(), charge);
        // Efeitos que mexem em blocos também em volta (anel a meio raio)
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            Vec3 p = CataclysmForm.groundBelow(level, center.add(Math.cos(a) * radius * 0.5, 2, Math.sin(a) * radius * 0.5));
            BlockPos below = BlockPos.containing(p).below();
            ctx.resolveOnBlock(new BlockHitResult(p, Direction.UP, below, false), null);
        }
    }

    // =====================================================================================
    //  Ondas de choque
    // =====================================================================================
    private void waves(int w) {
        if (w <= WAVE) {
            shockRing(radius * w / (float) WAVE, true);
            if (w % 3 == 1) sound(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.4F + 0.5F * w / WAVE);
        }
        int w2 = w - WAVE_GAP;
        if (w2 >= 1 && w2 <= WAVE) shockRing(radius * 1.15F * w2 / (float) WAVE, false);
    }

    private void shockRing(float front, boolean push) {
        int n = Math.max(16, (int) (front * 6));
        // A 1ª onda tem a cor do elemento primário; a 2ª, a do segundo elemento (se houver)
        ParticleOptions dust = ElementFx.dust(push ? element : elements.get(elements.size() > 1 ? 1 : 0), 1.8F);
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double x = center.x + Math.cos(a) * front, z = center.z + Math.sin(a) * front;
            far(dust, x, center.y + 0.3, z, 1, 0.1, 0.1, 0.1, 0);
            if (i % 3 == 0) far(ParticleTypes.POOF, x, center.y + 0.3, z, 1, 0.1, 0.05, 0.1, 0.02);
            if (push && i % 4 == 0) far(debris, x, center.y + 0.4, z, 2, 0.2, 0.2, 0.2, 0.15);
        }
        if (!push) return;
        // Arremessa para fora quem a frente da onda alcança (uma vez por alvo)
        LivingEntity caster = ctx.caster();
        double power = 1.3 * ctx.formStats().power();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(front + 1, 6, front + 1),
                e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e != caster)) {
            if (pushed.contains(e.getUUID()) || ctx.isAlly(e)) continue;
            double dx = e.getX() - center.x, dz = e.getZ() - center.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > front) continue;
            pushed.add(e.getUUID());
            double s = power * (1.0 - 0.5 * Math.min(1.0, d / radius));
            Vec3 dir = d < 0.01 ? new Vec3(1, 0, 0) : new Vec3(dx / d, 0, dz / d);
            e.push(dir.x * s, 0.45 + 0.15 * s, dir.z * s);
            e.hurtMarked = true;
        }
    }

    // =====================================================================================
    //  Ajudantes
    // =====================================================================================
    private void ring(double y, double r, int points, ParticleOptions p, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            far(p, center.x + Math.cos(a) * r, y, center.z + Math.sin(a) * r, 1, 0.02, 0.02, 0.02, speed);
        }
    }

    private void far(ParticleOptions p, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        DomainFx.far(level, p, x, y, z, count, dx, dy, dz, speed);
    }

    private void sound(SoundEvent s, float vol, float pitch) {
        level.playSound(null, center.x, center.y, center.z, s, SoundSource.PLAYERS, vol * volume, pitch);
    }
}
