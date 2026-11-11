package com.renanpontes.isekaimahou.feature.forms_a.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_a.FormAim;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.feature.forms_a.entity.SpellFallingEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Queda: mira um ponto até 48 blocos (alvo ou chão) e faz cair do céu, 15 blocos acima, uma {@link SpellFallingEntity}.
 * No impacto aplica numa área de raio 3 (× área) e no bloco. Debaixo de teto, surge logo abaixo dele.
 * Dividir = meteoros extras caindo em volta (em sequência); Espalhar = espalhados mais longe;
 * Acelerar = cai mais rápido; Teleguiado = segue o alvo mirado; Fantasma = atravessa blocos.
 * <p>
 * Estágios (v0.7): cai sobre o ponto de impacto que gerou o estágio ({@code ctx.anchor()}; com Saltar, sobre o próximo
 * inimigo), vindo em diagonal do lado da origem do estágio. O meteoro avisa {@code impact} e {@code end} ao cair
 * (direção horizontal da queda), então "queda → Ao Terminar → onda" espalha uma onda a partir da cratera.
 */
public class FallForm extends FormGlyph {
    private static final double RANGE = 48.0;
    private static final double HEIGHT = 15.0;
    /** Recuo horizontal do ponto de surgimento (em direção ao conjurador), para a queda vir em diagonal. */
    private static final double SLANT = 4.0;
    /** Até quantos blocos abaixo do ponto de impacto o meteoro procura o chão (mais longe: cai no próprio ponto). */
    private static final double ANCHOR_GROUND = 6.0;

    public FallForm() {
        super("fall", 12);
        element(Element.ARCANE);
        tier(3);
        // Pergaminhos podem ensinar antes, mas só dá para usar a partir deste Círculo
        minCircle(3);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 15; }

    @Override
    public int baseCooldown() { return 40; }

    @Override
    public void cast(SpellContext ctx) {
        if (!FormAim.casterReady(ctx)) return;
        SpellStats s = ctx.formStats();
        FormAim.Point aim = FormAim.areaPoint(ctx, RANGE, ANCHOR_GROUND);
        int n = s.count();

        spawn(ctx, aim.pos(), aim.entity());
        for (int i = 1; i < n; i++) {
            double a = Math.PI * 2 * (i - 1) / Math.max(1, n - 1) + ctx.random().nextDouble() * 0.6;
            double d = (s.spread ? 4.0 + ctx.random().nextDouble() * 4.0 : 2.5 + ctx.random().nextDouble() * 1.5);
            Vec3 around = aim.pos().add(Math.cos(a) * d, 3.0, Math.sin(a) * d);
            Vec3 tgt = FormAim.groundBelow(ctx, around, 10.0);
            SpellScheduler.schedule(i * 6, () -> spawn(ctx, tgt, null));
        }
        FormsFx.ring(ctx, aim.pos().add(0, 0.05, 0), s.radius(SpellFallingEntity.BASE_RADIUS), 2.0);
    }

    private static void spawn(SpellContext ctx, Vec3 target, Entity targetEntity) {
        // Vem em diagonal do lado de quem lançou: o conjurador, ou (num estágio) a origem do estágio
        Vec3 side = ctx.hasOrigin() ? ctx.eyePos() : ctx.caster() != null ? ctx.caster().position() : null;
        Vec3 back = Vec3.ZERO;
        if (side != null) {
            Vec3 h = side.subtract(target);
            h = new Vec3(h.x, 0, h.z);
            if (h.lengthSqr() > 1.0E-4) back = h.normalize().scale(Math.min(SLANT, h.length()));
        }
        Vec3 start = target.add(back.x, HEIGHT, back.z);
        // Teto (caverna, casa): surge logo abaixo dele
        Vec3 from = target.add(0, 0.5, 0);
        BlockHitResult roof = ctx.level().clip(FormAim.clipContext(from, start, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        if (roof.getType() == HitResult.Type.BLOCK) {
            Vec3 dir = start.subtract(from).normalize();
            start = roof.getLocation().subtract(dir.scale(0.8));
        }
        ctx.level().addFreshEntity(SpellFallingEntity.create(ctx, start, target, targetEntity));

        // Portal de onde o meteoro sai
        FormsFx.ring(ctx, start.add(0, 0.4, 0), 1.3, 4.0);
        FormsFx.burst(ctx, start.add(0, 0.4, 0), 14, 0.5);
        FormsFx.send(ctx.level(), ParticleTypes.REVERSE_PORTAL, start, 20, 0.6, 0.05);
        FormsFx.sound(ctx, start, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 0.7F);
    }
}
