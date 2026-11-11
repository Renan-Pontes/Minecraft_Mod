package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_knight.BladeWaves;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightSwords;
import com.renanpontes.isekaimahou.feature.class_knight.LegendSword;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Raio de Espada (forma, tier 1): o golpe solta uma MEIA-LUA de energia da lâmina que voa 22 blocos e atravessa até
 * 3 inimigos (+Perfurar), aplicando os efeitos em cada um. Dividir = leque de meias-luas (cada uma é uma cópia),
 * Espalhar abre o leque, Acelerar/Retardar mudam a velocidade, Estender o alcance, Expandir a largura e Fantasma
 * atravessa paredes. Com espada na mão a lâmina é mais larga; com Espada Lendária ganha a cor dela.
 * Funciona como estágio 2+ (sai do ponto do estágio).
 */
public class SwordBeamForm extends FormGlyph {
    public static final float RANGE = 22F;

    public SwordBeamForm() {
        super(KnightGlyphs.SWORD_BEAM, 5);
        element(Element.ARCANE);
        origin(MagicPath.KNIGHT);
        tier(1);
    }

    @Override
    public int baseCooldown() { return 14; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public float copies(SpellStats stats, int circle) { return stats.count(); }

    @Override
    public float hintRange() { return RANGE; }

    @Override
    public float hintRadius() { return 1.2F; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        boolean sword = !ctx.hasOrigin() && KnightSwords.holdsSword(caster);
        LegendSword legend = ctx.hasOrigin() ? null : KnightSwords.legendIn(caster);
        int rgb = legend != null && ctx.recipe().color() < 0 ? legend.rgb : ctx.color() & 0xFFFFFF;
        Vec3 dir = ctx.look();
        if (!ctx.hasOrigin() && caster instanceof Mob mob && mob.getTarget() != null) {
            dir = KnightFx.center(mob.getTarget()).subtract(caster.getEyePosition()).normalize();
        }
        Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().add(0, -0.35, 0).add(dir.scale(0.8));
        double speed = 1.4 * s.speedMult();
        double range = RANGE * (1.0 + 0.4 * Math.max(-1, s.duration));
        double half = s.radius(sword ? 1.4F : 1.1F);
        int hits = 3 + Math.max(0, s.pierce);
        int n = s.count();
        float fan = s.spread ? 22F : 11F;
        for (int i = 0; i < n; i++) {
            float off = (i - (n - 1) / 2F) * fan;
            if (s.spread) off += (ctx.random().nextFloat() - 0.5F) * 10F;
            Vec3 d = rotateY(dir, off);
            BladeWaves.launch(level, caster, from, d, speed, range, half, hits, s.ghost, rgb, legend != null,
                    e -> !ctx.isAlly(e) && !ctx.ignores(e),
                    (e, at) -> {
                        ctx.impact(KnightFx.center(e), d, e);
                        e.invulnerableTime = 0;
                        ctx.resolveOnEntity(e, caster);
                    },
                    (at, dd) -> ctx.end(at, dd));
        }
        if (!ctx.hasOrigin()) caster.swing(InteractionHand.MAIN_HAND, true);
        KnightFx.crescent(level, from, dir, half, rgb, 6, 1.3F);
        KnightFx.sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
        KnightFx.sound(level, from, SoundEvents.TRIDENT_THROW.value(), 0.8F, 1.3F);
        ElementFx.castSound(level, caster, ctx.element());
    }

    /** Gira a direção em volta do eixo vertical (graus). */
    static Vec3 rotateY(Vec3 d, float deg) {
        if (Math.abs(deg) < 1.0E-3) return d;
        float r = deg * Mth.DEG_TO_RAD;
        double c = Math.cos(r), sn = Math.sin(r);
        return new Vec3(d.x * c - d.z * sn, d.y, d.x * sn + d.z * c).normalize();
    }
}
