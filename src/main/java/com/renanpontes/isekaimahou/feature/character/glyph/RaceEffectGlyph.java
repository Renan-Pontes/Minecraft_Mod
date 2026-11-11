package com.renanpontes.isekaimahou.feature.character.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.server.level.ServerPlayer;

/**
 * Base dos EFEITOS raciais: origem racial ({@code origin(MagicPath.NONE)}, ver {@code RaceGlyphs}), elemento (define
 * o primário quando a magia não tem outro glifo elemental) e tier (1 = nasce com ela, 2 = desperta no nível 15).
 */
public abstract class RaceEffectGlyph extends EffectGlyph {
    protected RaceEffectGlyph(String path, int cost, Element element, int tier) {
        super(path, cost);
        element(element);
        origin(MagicPath.NONE);
        tier(tier);
    }

    /** Dados raciais de quem conjura (null = mob ou sem raça escolhida). */
    protected static CharacterData casterData(SpellContext ctx) {
        ServerPlayer sp = ctx.casterPlayer();
        if (sp == null) return null;
        CharacterData d = CharacterContent.data(sp);
        return d.created() ? d : null;
    }

    protected static boolean casterIs(SpellContext ctx, Race r) {
        CharacterData d = casterData(ctx);
        return d != null && d.race() == r;
    }
}
