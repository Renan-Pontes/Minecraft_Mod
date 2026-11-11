package com.renanpontes.isekaimahou.feature.class_enchanter.effect;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_enchanter.Transformations;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * As TRANSFORMAÇÕES do Encantador (técnicas de anime, v1.0 "pacote de técnicas") como efeito: ícone, duração e os
 * atributos por nível (o nível é o amplificador + 1). A lógica (custo em vida, aura, esquiva, golpe em área) fica em
 * {@link Transformations}.
 * <ul>
 *   <li><b>Oito Portões</b> (nível = portão 1..8): dano corpo a corpo +10% → +160%, velocidade, ataque, firmeza;</li>
 *   <li><b>Kaioken</b> (nível 1/2/3 = ×2/×3/×10): dano corpo a corpo ×2/×3/×10 e mais velocidade;</li>
 *   <li><b>Segunda Marcha</b>: velocidade, ataque e pulo muito maiores (vapor);</li>
 *   <li><b>Quarta Marcha</b>: corpo inflado (tamanho ×1,6), soco gigante, defesa, alcance e vida;</li>
 *   <li><b>Instinto Superior</b>: esquiva automática (contada em {@link Transformations}) e um pouco de agilidade.</li>
 * </ul>
 */
public class TransformEffect extends MobEffect {
    public enum Kind {
        GATES(0x6FE08A), KAIOKEN(0xFF3B30), GEAR_SECOND(0xFF8FA0), GEAR_FOURTH(0xC8303C), ULTRA_INSTINCT(0xD8E4FF);

        public final int color;

        Kind(int color) {
            this.color = color;
        }
    }

    /** Dano corpo a corpo (multiplicador total) por portão aberto. */
    public static final double[] GATE_DAMAGE = {0.10, 0.20, 0.32, 0.45, 0.60, 0.80, 1.05, 1.60};
    /** Velocidade (base) por portão aberto. */
    public static final double[] GATE_SPEED = {0.04, 0.08, 0.12, 0.16, 0.20, 0.25, 0.30, 0.40};
    /** Multiplicadores do Kaioken por nível. */
    public static final int[] KAIOKEN_MULT = {2, 3, 10};

    private final Kind kind;

    public TransformEffect(Kind kind) {
        super(MobEffectCategory.BENEFICIAL, kind.color);
        this.kind = kind;
        String base = "enchanter." + kind.name().toLowerCase(java.util.Locale.ROOT);
        switch (kind) {
            case GATES -> {
                addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(base + ".damage"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                        amp -> GATE_DAMAGE[clamp(amp, GATE_DAMAGE.length)]);
                addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(base + ".speed"), AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                        amp -> GATE_SPEED[clamp(amp, GATE_SPEED.length)]);
                addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id(base + ".attack_speed"), 0.06,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id(base + ".knockback"), 0.05,
                        AttributeModifier.Operation.ADD_VALUE);
                addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id(base + ".jump"), 0.012, AttributeModifier.Operation.ADD_VALUE);
                addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id(base + ".fall"), 1.0, AttributeModifier.Operation.ADD_VALUE);
            }
            case KAIOKEN -> {
                addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(base + ".damage"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                        amp -> KAIOKEN_MULT[clamp(amp, KAIOKEN_MULT.length)] - 1.0);
                addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(base + ".speed"), AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                        amp -> amp >= 2 ? 0.30 : 0.10 + 0.05 * amp);
                addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id(base + ".attack_speed"), AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                        amp -> amp >= 2 ? 0.30 : 0.10 + 0.05 * amp);
            }
            case GEAR_SECOND -> {
                addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(base + ".speed"), AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                        amp -> 0.35 + 0.05 * amp);
                addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id(base + ".attack_speed"), AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                        amp -> 0.40 + 0.10 * amp);
                addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(base + ".damage"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                        amp -> 0.25 + 0.08 * amp);
                addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id(base + ".jump"), AttributeModifier.Operation.ADD_VALUE, amp -> 0.08);
                addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id(base + ".fall"), AttributeModifier.Operation.ADD_VALUE, amp -> 4.0);
            }
            case GEAR_FOURTH -> {
                addAttributeModifier(Attributes.SCALE, IsekaiMahou.id(base + ".scale"), AttributeModifier.Operation.ADD_VALUE, amp -> 0.6);
                addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(base + ".damage"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                        amp -> 1.2 + 0.2 * amp);
                addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id(base + ".armor"), AttributeModifier.Operation.ADD_VALUE, amp -> 8.0 + 2.0 * amp);
                addAttributeModifier(Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id(base + ".toughness"), AttributeModifier.Operation.ADD_VALUE,
                        amp -> 4.0 + amp);
                addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id(base + ".knockback"), AttributeModifier.Operation.ADD_VALUE,
                        amp -> 0.6);
                addAttributeModifier(Attributes.MAX_HEALTH, IsekaiMahou.id(base + ".health"), AttributeModifier.Operation.ADD_VALUE,
                        amp -> 20.0 + 10.0 * amp);
                addAttributeModifier(Attributes.ATTACK_KNOCKBACK, IsekaiMahou.id(base + ".punch"), AttributeModifier.Operation.ADD_VALUE, amp -> 2.0);
                addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id(base + ".jump"), AttributeModifier.Operation.ADD_VALUE, amp -> 0.12);
                addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id(base + ".fall"), AttributeModifier.Operation.ADD_VALUE, amp -> 12.0);
                addAttributeModifier(Attributes.ENTITY_INTERACTION_RANGE, IsekaiMahou.id(base + ".reach"), AttributeModifier.Operation.ADD_VALUE,
                        amp -> 1.5);
            }
            case ULTRA_INSTINCT -> {
                addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(base + ".speed"), AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                        amp -> 0.15);
                addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id(base + ".attack_speed"), AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                        amp -> 0.25);
            }
        }
    }

    private static int clamp(int amp, int len) {
        return Math.max(0, Math.min(len - 1, amp));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amp) {
        if (e.level().isClientSide) return true;
        return Transformations.tick(kind, e, amp);
    }
}
