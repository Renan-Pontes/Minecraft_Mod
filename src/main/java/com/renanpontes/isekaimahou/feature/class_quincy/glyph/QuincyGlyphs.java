package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.world.phys.Vec3;

/**
 * Os glifos de ORIGEM do Quincy (10 + a forma G), todos montáveis no Grimório e combináveis com o resto do sistema
 * (estágios, ligações, multi-elemento). Exclusivos: outras classes só aprendem por Lição.
 * <pre>
 *  Formas:   Arco Espiritual (t1) · Passo Voador (t1) · Lâmina Espiritual (t2) · Absorção (t2) · Chuva de Luz (t3, C3)
 *            Vollständig — forma G / ultimate (C6)
 *  Efeitos:  Drenar Reishi (t1) · Devolução (t2) · Sangue Espiritual (t2, benéfico) · Schrift (t3, C4)
 *  Aumento:  Ampola de Prata (t2) — gasta 1 Ampola do inventário ao conjurar e dá +35% de poder
 *  Técnicas avançadas (QuincyTechniques): Heilig Bogen (t4, C5) · Sankt Zwinger (t4, C6) · A Antítese (efeito t4, C7) ·
 *            Quincy: Letzt Stil — forma G (t5, C8) · O Todo-Poderoso — forma G (t5, C9)
 * </pre>
 */
public final class QuincyGlyphs {
    /** Poder extra de uma magia com Ampola de Prata (quando há uma Ampola para gastar). */
    public static final float SILVER_TUBE_POWER = 1.35F;

    public static void register() {
        GlyphRegistry.register(new SpiritBowForm());
        GlyphRegistry.register(new HirenkyakuForm());
        GlyphRegistry.register(new SeeleBladeForm());
        GlyphRegistry.register(new AbsorbForm());
        GlyphRegistry.register(new LightRainForm());
        GlyphRegistry.register(new VollstandigForm());
        GlyphRegistry.register(new DrainReishiEffect());
        GlyphRegistry.register(new ReleaseEffect());
        GlyphRegistry.register(new BlutEffect());
        GlyphRegistry.register(new SchriftEffect());
        // técnicas avançadas (QuincyTechniques)
        GlyphRegistry.register(new HeiligBogenForm());
        GlyphRegistry.register(new SanktZwingerForm());
        GlyphRegistry.register(new AntithesisEffect());
        GlyphRegistry.register(new LetztStilForm());
        GlyphRegistry.register(new AlmightyForm());
        // Ampola de Prata: só marca a magia; o gasto da Ampola e o poder são aplicados ao conjurar (QuincyEvents)
        GlyphRegistry.register(new AugmentGlyph(Quincy.SILVER_TUBE, 2, s -> s.custom.merge(Quincy.SILVER_TUBE_ID, 1, Integer::sum))
                .origin(MagicPath.QUINCY).element(Element.LIGHT).tier(2));
    }

    /** Gira uma direção em yaw/pitch (graus), para leques de flechas. */
    static Vec3 rotate(Vec3 dir, float yawDeg, float pitchDeg) {
        if (yawDeg == 0 && pitchDeg == 0) return dir;
        double yaw = Math.toRadians(yawDeg);
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        Vec3 d = new Vec3(dir.x * cos - dir.z * sin, dir.y, dir.x * sin + dir.z * cos);
        if (pitchDeg != 0) d = new Vec3(d.x, d.y + Math.tan(Math.toRadians(pitchDeg)) * Math.sqrt(d.x * d.x + d.z * d.z), d.z);
        return d.normalize();
    }

    private QuincyGlyphs() {}
}
