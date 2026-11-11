package com.renanpontes.isekaimahou.feature.class_steelball;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * As 9 Partes do Cadáver Santo. Cada parte absorvida grava a flag {@code steelball.part.<id>} e dá um poder passivo
 * próprio (aplicado em {@link SbEvents}): qualquer classe pode absorver; os Atos da unha giratória são do Steel Ball.
 * <p>
 * O CORAÇÃO DO CADÁVER SANTO é a 9ª e ÚLTIMA parte ({@link #FINAL}): nunca sai de Relicários — só aparece para quem já
 * tem as outras 8 no corpo, batendo dentro de um chefe derrotado por perto ({@link SbEvents}); só se funde a um corpo
 * com as outras 8, e o Santo completo desperta o LOVE TRAIN ({@link SbLoveTrain}).
 */
public enum SbPart {
    /** Olhos: agachado, revela (Brilho) as criaturas hostis por perto. */
    EYES("eyes", 0x7FD8FF),
    /** Braço Esquerdo: unhas giratórias +20% de dano. */
    LEFT_ARM("left_arm", 0xC9A0FF),
    /** Braço Direito: bolas de aço +20% de dano. */
    RIGHT_ARM("right_arm", 0xFFB86B),
    /** Coluna: resistência a repulsão e firmeza (resistência da armadura). */
    SPINE("spine", 0xE8E0C8),
    /** Orelhas: regeneração do recurso +15%. */
    EARS("ears", 0x9FE8B0),
    /** Pernas: +8% de velocidade e queda segura. */
    LEGS("legs", 0xB4C0CC),
    /** Tronco: +4 de armadura. */
    TORSO("torso", 0xD9B26A),
    /** Crânio: +8% de Poder Mágico e +60 do recurso máximo (20 × escala da v1.0). */
    SKULL("skull", 0xF2E6A8),
    /** Coração do Cadáver Santo (a ÚLTIMA parte, a mais rara): +8 de vida máxima e, com o Santo inteiro, o LOVE TRAIN. */
    HEART("heart", 0xFFE6A0);

    /** A parte final: o Coração. */
    public static final SbPart FINAL = HEART;

    public static final String COUNTER = "steelball.parts";
    private static final String PART_KEY = "tooltip.isekai_mahou.steelball.part.";

    /** Frase de ambientação da parte (dica do item). */
    public MutableComponent flavor() {
        return Component.translatable(PART_KEY + "flavor." + id);
    }

    public final String id;
    public final int rgb;

    SbPart(String id, int rgb) {
        this.id = id;
        this.rgb = rgb;
    }

    public String flag() {
        return "steelball.part." + id;
    }

    public String itemId() {
        return "steelball_part_" + id;
    }

    public MutableComponent displayName() {
        return Component.translatable(net.minecraft.Util.makeDescriptionId("item", com.renanpontes.isekaimahou.IsekaiMahou.id(itemId())));
    }

    /** "+6 de vida máxima" etc. */
    public MutableComponent bonus() {
        return Component.translatable(PART_KEY + id + ".bonus");
    }

    public boolean owned(MagicData d) {
        return d != null && d.hasFlag(flag());
    }

    public static int count(MagicData d) {
        int n = 0;
        for (SbPart p : values()) if (p.owned(d)) n++;
        return n;
    }

    /** É a parte final (o Coração)? */
    public boolean isFinal() {
        return this == FINAL;
    }

    /** Tem as 8 partes comuns e ainda não tem o Coração (ele já pode aparecer)? */
    public static boolean readyForHeart(MagicData d) {
        return d != null && !FINAL.owned(d) && countCommon(d) >= values().length - 1;
    }

    /** Quantas das 8 partes comuns (sem o Coração) estão no corpo. */
    public static int countCommon(MagicData d) {
        int n = 0;
        for (SbPart p : values()) if (!p.isFinal() && p.owned(d)) n++;
        return n;
    }

    /** O Santo inteiro (as 9)? */
    public static boolean complete(MagicData d) {
        return count(d) >= values().length;
    }

    /** Partes que um Relicário pode revelar: as comuns que faltam (o Coração NUNCA está num relicário). */
    public static List<SbPart> relicPool(MagicData d) {
        List<SbPart> out = new ArrayList<>();
        for (SbPart p : values()) if (!p.isFinal() && !p.owned(d)) out.add(p);
        return out;
    }

    public static List<SbPart> missing(MagicData d) {
        List<SbPart> out = new ArrayList<>();
        for (SbPart p : values()) if (!p.owned(d)) out.add(p);
        return out;
    }

    @Nullable
    public static SbPart byId(String id) {
        for (SbPart p : values()) if (p.id.equals(id)) return p;
        return null;
    }
}
