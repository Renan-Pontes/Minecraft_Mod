package com.renanpontes.isekaimahou.feature.gear_plus;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Os 6 tipos de gema. Cada gema existe em 3 lapidações ({@link #GRADES}: lascada, lapidada, perfeita) e dá UM atributo.
 * Os valores são baixos: a gema é um tempero das classes que encaixam, não um atalho de poder.
 */
public enum GemType {
    RUBY("ruby", 0xE0304A, () -> Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE, 0.5, 0.8, 1.2),
    SAPPHIRE("sapphire", 0x3A78F0, () -> CoreRegistries.MAX_MANA, AttributeModifier.Operation.ADD_VALUE, 15, 30, 50),
    EMERALD("emerald", 0x2FCB6A, () -> Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.01, 0.02, 0.03),
    TOPAZ("topaz", 0xF2B63A, () -> Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 1, 1.5, 2),
    AMETHYST("amethyst", 0xA65CF0, () -> CoreRegistries.SPELL_POWER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.015, 0.03, 0.045),
    ONYX("onyx", 0x4A4460, () -> Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 2, 3, 4);

    /** Sufixos de id das lapidações (a do meio não tem sufixo: {@code gem_ruby}). */
    public static final String[] GRADES = {"chipped", "cut", "flawless"};
    /** Nível do jogador para a gema de cada lapidação funcionar (upar pesa mais que item). */
    public static final int[] GRADE_LEVEL = {1, 30, 55};

    public final String id;
    public final int color;
    private final Supplier<Holder<Attribute>> attribute;
    public final AttributeModifier.Operation op;
    private final double[] byGrade;

    GemType(String id, int color, Supplier<Holder<Attribute>> attribute, AttributeModifier.Operation op, double... byGrade) {
        this.id = id;
        this.color = color;
        this.attribute = attribute;
        this.op = op;
        this.byGrade = byGrade;
    }

    public Holder<Attribute> attribute() {
        return attribute.get();
    }

    /** Valor do bônus na lapidação (1..3). */
    public double amount(int grade) {
        return byGrade[Math.max(1, Math.min(3, grade)) - 1];
    }

    public MutableComponent describe(int grade) {
        return GearText.attribute(attribute(), op, amount(grade));
    }

    /** Id do item: {@code gem_ruby_chipped}, {@code gem_ruby}, {@code gem_ruby_flawless}. */
    public String itemId(int grade) {
        return grade == 2 ? "gem_" + id : "gem_" + id + "_" + GRADES[grade - 1];
    }
}
