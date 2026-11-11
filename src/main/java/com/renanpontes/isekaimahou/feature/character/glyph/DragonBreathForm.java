package com.renanpontes.isekaimahou.feature.character.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sopro do Dragão (Dragonato): um cone de 7 blocos (× área) que sai da boca e aplica os efeitos em tudo dentro dele que
 * esteja à vista. O sopro tem a cor e as partículas do elemento da magia; numa magia só Arcana/de origem, sai no
 * elemento da sua ANCESTRALIDADE. Se o elemento da magia for o da sua ancestralidade, +25% de poder. Dividir não
 * multiplica (é um sopro só — 1 cópia); Expandir = mais longe e mais aberto; Fantasma = atravessa paredes.
 * Estágios (v0.7): sopra a partir da origem do estágio; avisa {@code impact} em cada alvo e {@code end} na ponta.
 */
public class DragonBreathForm extends FormGlyph {
    public static final float RANGE = 7.0F;
    private static final double HALF_ANGLE = 32.0;

    public DragonBreathForm() {
        super("race_dragon_breath", 8);
        element(Element.ARCANE);
        origin(MagicPath.NONE);
        tier(1);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 4; }

    @Override
    public int baseCooldown() { return 20; }

    @Override
    public float copies(SpellStats stats, int circle) { return 1F; }

    @Override
    public float hintRange() { return RANGE; }

    @Override
    public float hintRadius() { return -1F; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        float range = s.radius(RANGE);
        double half = Math.toRadians(Math.min(65.0, HALF_ANGLE + 6.0 * Math.max(0, s.aoe) - (s.concentrate ? 10 : 0)));
        double cosHalf = Math.cos(half);

        Element visual = ctx.element();
        Element ancestry = ancestry(ctx);
        if (ancestry != null) {
            if (visual == Element.ARCANE) visual = ancestry;
            if (ctx.element().matches(ancestry)) ctx.setPowerMult(ctx.powerMult() * 1.25F);
        }

        Vec3 origin = ctx.eyePos().subtract(0, 0.15, 0);
        Vec3 dir = ctx.look().normalize();
        List<Entity> inside = new ArrayList<>();
        for (Entity e : level.getEntities(caster, new AABB(origin, origin).inflate(range + 1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !ctx.ignores(e))) {
            Vec3 c = RaceFx.center(e);
            Vec3 to = c.subtract(origin);
            double len = to.length();
            if (len > range + e.getBbWidth() * 0.5) continue;
            if (len > 1.2 && to.scale(1 / len).dot(dir) < cosHalf) continue;
            if (!s.ghost) {
                BlockHitResult wall = level.clip(new ClipContext(origin, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                if (wall.getType() == HitResult.Type.BLOCK) continue;
            }
            inside.add(e);
        }
        inside.sort(Comparator.comparingDouble(e -> e.distanceToSqr(origin)));
        int hits = 0;
        for (Entity e : inside) {
            if (++hits > 32) break; // orçamento
            Vec3 c = RaceFx.center(e);
            Vec3 d = c.subtract(origin);
            ctx.impact(c, d.lengthSqr() > 1e-4 ? d.normalize() : dir, e);
            ctx.resolveOnEntity(e, null);
        }
        // Blocos tocados no eixo central (só efeitos que mexem em blocos ou com Sensível)
        BlockHitResult bh = level.clip(new ClipContext(origin, origin.add(dir.scale(range)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, caster));
        if (bh.getType() == HitResult.Type.BLOCK) ctx.resolveOnBlock(bh, null);

        drawBreath(level, origin, dir, range, half, visual, ctx);
        RaceFx.sound(level, origin, SoundEvents.ENDER_DRAGON_SHOOT, 1.0F, 1.15F);
        level.playSound(null, origin.x, origin.y, origin.z, ElementFx.castSoundOf(visual), net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.85F);
        ctx.end(origin.add(dir.scale(range)), dir);
    }

    /** Elemento da ancestralidade de quem sopra (só Dragonatos). */
    private static Element ancestry(SpellContext ctx) {
        ServerPlayer sp = ctx.casterPlayer();
        if (sp == null) return null;
        CharacterData d = CharacterContent.data(sp);
        return d.is(Race.DRAGONBORN) ? d.ancestry() : null;
    }

    /** Cone em camadas: pó na cor do elemento, a partícula assinatura e jatos de chama de dragão. */
    private static void drawBreath(ServerLevel level, Vec3 origin, Vec3 dir, float range, double half, Element el, SpellContext ctx) {
        ParticleOptions dust = ElementFx.dust(el, 1.5F);
        ParticleOptions sig = ElementFx.signature(el);
        double spreadPerBlock = Math.tan(half);
        for (double d = 0.8; d <= range + 0.01; d += 0.7) {
            Vec3 p = origin.add(dir.scale(d));
            double r = Math.max(0.15, d * spreadPerBlock * 0.55);
            int n = 3 + (int) (d * 1.4);
            level.sendParticles(dust, p.x, p.y, p.z, n, r, r * 0.7, r, 0.01);
            level.sendParticles(sig, p.x, p.y, p.z, Math.max(1, n / 3), r * 0.8, r * 0.6, r * 0.8, 0.02);
        }
        for (int i = 0; i < 10; i++) {
            Vec3 jitter = dir.add((ctx.random().nextDouble() - 0.5) * half, (ctx.random().nextDouble() - 0.5) * half * 0.6,
                    (ctx.random().nextDouble() - 0.5) * half).normalize();
            Vec3 p = origin.add(jitter.scale(0.6));
            level.sendParticles(ParticleTypes.DRAGON_BREATH, p.x, p.y, p.z, 0, jitter.x, jitter.y, jitter.z, 0.35 + range * 0.03);
        }
    }
}
