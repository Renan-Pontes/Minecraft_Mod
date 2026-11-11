package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

/**
 * Ao Jackpot (gatilho, vai no começo da receita): o slot sai SOZINHO, mirando o inimigo mais próximo, no instante em
 * que você tira um JACKPOT — já com o poder máximo da sorte infinita. Apertar a tecla do slot não conjura.
 * Ver {@link JackpotTriggers}.
 */
public class JackpotTrigger extends TriggerGlyph {
    public JackpotTrigger() {
        super("trigger_jackpot", 6, true);
        origin(MagicPath.GAMBLER);
        tier(4);
        minCircle(5);
    }
}
