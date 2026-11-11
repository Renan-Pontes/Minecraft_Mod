package com.renanpontes.isekaimahou.feature.legends_d.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Bolha Protetora (Eu Sou Atômico): o aliado fica numa bolha azul durante a explosão — sem gravidade (se o chão sumir
 * na cratera, fica flutuando), sem pulo e sem empurrão (modificadores de atributo em {@code LdRegistry}). O dano é
 * cancelado pelo {@code AtomicBubble}; quando o efeito acaba no ar, a descida é lenta.
 */
public class AtomicBubbleEffect extends MobEffect {
    public AtomicBubbleEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
