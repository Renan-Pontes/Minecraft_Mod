package com.renanpontes.isekaimahou.feature.class_smith.forge;

import com.renanpontes.isekaimahou.feature.class_smith.SmithKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Família da arma forjada (o "esqueleto" que o Reforjar troca). A família Lâmina muda de nome com o comprimento:
 * adaga (0) → espada (1–2) → espada longa (3) → montante (4). As outras usam o comprimento para o cabo/haste.
 * Números: dano somado ao do material, modificador de velocidade de ataque (base do jogador = 4), alcance extra e quanto
 * cada degrau de comprimento soma.
 */
public enum BladeFamily {
    //       id        dano  veloc.  alcance  +dano/comp +alc/comp +veloc/comp peso
    BLADE("blade", 1.0F, -2.0F, -0.5F, 0.9F, 0.35F, -0.25F, 0.0F),
    AXE("axe", 3.5F, -3.0F, 0.0F, 0.4F, 0.25F, -0.05F, 0.6F),
    SPEAR("spear", 2.0F, -2.6F, 1.0F, 0.3F, 0.4F, -0.05F, 0.3F),
    HAMMER("hammer", 3.5F, -3.2F, 0.0F, 0.6F, 0.2F, -0.08F, 1.0F),
    SCYTHE("scythe", 2.5F, -2.8F, 0.75F, 0.5F, 0.3F, -0.06F, 0.5F);

    public static final BladeFamily[] VALUES = values();

    public final String id;
    public final float damage, speed, reach, damagePerLength, reachPerLength, speedPerLength, weight;

    BladeFamily(String id, float damage, float speed, float reach, float damagePerLength, float reachPerLength,
                float speedPerLength, float weight) {
        this.id = id;
        this.damage = damage;
        this.speed = speed;
        this.reach = reach;
        this.damagePerLength = damagePerLength;
        this.reachPerLength = reachPerLength;
        this.speedPerLength = speedPerLength;
        this.weight = weight;
    }

    /** Nome do TIPO mostrado (adaga, espada, espada longa, montante, machado, lança, martelo, foice). */
    public String typeId(int length) {
        if (this != BLADE) return id;
        if (length <= 0) return "dagger";
        if (length <= 2) return "sword";
        if (length == 3) return "longsword";
        return "greatsword";
    }

    public MutableComponent typeName(int length) {
        return Component.translatable(SmithKeys.BLADE_TYPE + typeId(length));
    }

    public MutableComponent familyName() {
        return Component.translatable(SmithKeys.BLADE_FAMILY + id);
    }

    /** Arma de haste (o cabo é longo e a "cabeça" fica no alto). */
    public boolean polearm() {
        return this != BLADE;
    }

    public BladeFamily next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public BladeFamily previous() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }

    public static BladeFamily byId(String id) {
        for (BladeFamily f : VALUES) if (f.id.equals(id)) return f;
        return BLADE;
    }
}
