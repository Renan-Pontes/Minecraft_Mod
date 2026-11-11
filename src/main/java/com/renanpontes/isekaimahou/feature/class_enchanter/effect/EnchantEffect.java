package com.renanpontes.isekaimahou.feature.class_enchanter.effect;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.class_enchanter.AntiMagic;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchFx;
import com.renanpontes.isekaimahou.feature.class_enchanter.GearEnchant;
import com.renanpontes.isekaimahou.feature.class_enchanter.HeavenlyRestriction;
import com.renanpontes.isekaimahou.feature.class_enchanter.Imbue;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Um encantamento do Encantador como efeito (ícone, duração e sincronização do vanilla). Os números por nível
 * (o modificador soma a cada nível empilhado):
 * <ul>
 *   <li>Força: +2 de dano corpo a corpo · Celeridade: +8% velocidade e +10% velocidade de ataque ·
 *   Égide: +3 armadura, +1 resistência, +8% firmeza · Vitalidade: cura a cada 2 s;</li>
 *   <li>Arma Imbuída, Equipamento Encantado: regras em {@code Imbue}/{@code GearEnchant};</li>
 *   <li>Sobrecarga: +20% dano, +15% ataque, +8% velocidade; ao acabar, Exaustão;</li>
 *   <li>Anti-Magia: custa recurso por segundo ({@code AntiMagic}); Restrição Celestial: corpo absurdo, recurso travado
 *   em zero (regen ×0) e sentidos aguçados ({@code HeavenlyRestriction}).</li>
 * </ul>
 */
public class EnchantEffect extends MobEffect {
    public enum Kind {
        MIGHT(0xFF8A5C), HASTE(0x9FF0D0), AEGIS(0xFFE27A), VITALITY(0x7CF08A), IMBUE(0xB98CFF), OVERDRIVE(0xFF5FA8),
        GEAR(0x5FE3C0), ANTI_MAGIC(0xBFE8FF), HEAVENLY(0x9AA3AE);

        public final int color;

        Kind(int color) {
            this.color = color;
        }
    }

    private final Kind kind;

    public EnchantEffect(Kind kind) {
        super(MobEffectCategory.BENEFICIAL, kind.color);
        this.kind = kind;
        String base = "enchanter." + kind.name().toLowerCase(java.util.Locale.ROOT);
        switch (kind) {
            case MIGHT -> addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(base + ".damage"), 2.0,
                    AttributeModifier.Operation.ADD_VALUE);
            case HASTE -> {
                addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(base + ".speed"), 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id(base + ".attack_speed"), 0.10,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case AEGIS -> {
                addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id(base + ".armor"), 3.0, AttributeModifier.Operation.ADD_VALUE);
                addAttributeModifier(Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id(base + ".toughness"), 1.0, AttributeModifier.Operation.ADD_VALUE);
                addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id(base + ".knockback"), 0.08,
                        AttributeModifier.Operation.ADD_VALUE);
            }
            case OVERDRIVE -> {
                addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(base + ".damage"), 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id(base + ".attack_speed"), 0.15,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(base + ".speed"), 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case HEAVENLY -> {
                addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(base + ".damage"), 2.0, AttributeModifier.Operation.ADD_VALUE);
                addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id(base + ".damage_mult"), 0.12,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id(base + ".speed"), 0.07, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id(base + ".attack_speed"), 0.10,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id(base + ".armor"), 2.0, AttributeModifier.Operation.ADD_VALUE);
                addAttributeModifier(Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id(base + ".toughness"), 1.5, AttributeModifier.Operation.ADD_VALUE);
                addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id(base + ".knockback"), 0.08,
                        AttributeModifier.Operation.ADD_VALUE);
                addAttributeModifier(Attributes.MAX_HEALTH, IsekaiMahou.id(base + ".health"), 4.0, AttributeModifier.Operation.ADD_VALUE);
                addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id(base + ".jump"), 0.03, AttributeModifier.Operation.ADD_VALUE);
                addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id(base + ".fall"), 2.0, AttributeModifier.Operation.ADD_VALUE);
                addAttributeModifier(Attributes.STEP_HEIGHT, IsekaiMahou.id(base + ".step"), AttributeModifier.Operation.ADD_VALUE, amp -> 0.4);
                // sem mana: a regeneração do recurso para por completo enquanto durar
                addAttributeModifier(CoreRegistries.MANA_REGEN, IsekaiMahou.id(base + ".no_regen"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                        amp -> -1.0);
            }
            default -> {
            }
        }
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return switch (kind) {
            case HEAVENLY -> true;
            case OVERDRIVE -> duration % 5 == 0;
            case IMBUE -> duration % 8 == 0;
            case VITALITY, GEAR -> duration % 40 == 0;
            default -> duration % 20 == 0;
        };
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amp) {
        if (!(e.level() instanceof ServerLevel level)) return true;
        int lvl = amp + 1;
        switch (kind) {
            case VITALITY -> {
                if (e.getHealth() < e.getMaxHealth()) {
                    e.heal((1.0F + 0.5F * amp) * Progression.HEAL_SCALE);
                    level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 1, 0.25, 0.1, 0.25, 0);
                }
                level.sendParticles(EnchFx.dust(kind.color, 0.7F), e.getX(), e.getY() + 0.2, e.getZ(), 4, 0.35, 0.1, 0.35, 0);
            }
            case ANTI_MAGIC -> {
                return AntiMagic.upkeep(e, lvl);
            }
            case HEAVENLY -> HeavenlyRestriction.tick(e, lvl);
            case OVERDRIVE -> {
                double h = e.getBbHeight() * 0.55;
                level.sendParticles(EnchFx.dust(kind.color, 0.9F), e.getX(), e.getY() + h, e.getZ(), 3, 0.35, 0.3, 0.35, 0);
                if (e.tickCount % 10 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + h, e.getZ(), 2 + amp, 0.3, 0.4, 0.3, 0.12);
                }
            }
            case IMBUE -> Imbue.ambient(level, e);
            case GEAR -> {
                if (e instanceof ServerPlayer sp) GearEnchant.mark(sp);
                level.sendParticles(ParticleTypes.ENCHANT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.3, 0.5, 0.3, 0.5);
            }
            default -> level.sendParticles(EnchFx.dust(kind.color, 0.6F), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(),
                    Math.min(4, lvl), 0.3, 0.4, 0.3, 0);
        }
        return true;
    }
}
