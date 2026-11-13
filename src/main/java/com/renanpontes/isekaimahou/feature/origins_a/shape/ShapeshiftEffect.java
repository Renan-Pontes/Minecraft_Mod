package com.renanpontes.isekaimahou.feature.origins_a.shape;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.origins_a.OaData;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * Efeito de uma forma da Metamorfose. Os atributos (tamanho, velocidade, dano, vida...) vêm dos modificadores
 * do efeito; as habilidades passivas de cada forma rodam em {@link #applyEffectTick} (servidor) e em
 * {@link ShapeEvents} (ataques, respiração, queda). O visual de animal é do cliente.
 */
public class ShapeshiftEffect extends MobEffect {
    /** Golfinho fora d'água fica lento (modificador temporário, removido ao entrar na água ou sair da forma). */
    public static final ResourceLocation DOLPHIN_LAND = IsekaiMahou.id("origins_a.dolphin_on_land");

    public final Shape shape;

    public ShapeshiftEffect(Shape shape) {
        super(MobEffectCategory.BENEFICIAL, shape.color);
        this.shape = shape;
        ResourceLocation id = IsekaiMahou.id("effect." + shape.effectId());
        switch (shape) {
            case WOLF -> {
                mod(Attributes.SCALE, id, -0.45, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.MOVEMENT_SPEED, id, 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                mod(Attributes.ATTACK_DAMAGE, id, 3.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.ATTACK_SPEED, id, 1.5, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.JUMP_STRENGTH, id, 0.12, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.SAFE_FALL_DISTANCE, id, 3.0, AttributeModifier.Operation.ADD_VALUE);
            }
            case BEAR -> {
                mod(Attributes.SCALE, id, 0.15, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.MAX_HEALTH, id, 12.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.ARMOR, id, 6.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.ARMOR_TOUGHNESS, id, 2.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.KNOCKBACK_RESISTANCE, id, 0.6, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.ATTACK_DAMAGE, id, 6.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.ATTACK_KNOCKBACK, id, 1.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.MOVEMENT_SPEED, id, -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            case FALCON -> {
                mod(Attributes.SCALE, id, -0.55, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.MOVEMENT_SPEED, id, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                mod(Attributes.FALL_DAMAGE_MULTIPLIER, id, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                mod(Attributes.MAX_HEALTH, id, -4.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.ATTACK_DAMAGE, id, 1.0, AttributeModifier.Operation.ADD_VALUE);
            }
            case DOLPHIN -> {
                mod(Attributes.SCALE, id, -0.3, AttributeModifier.Operation.ADD_VALUE);
                mod(NeoForgeMod.SWIM_SPEED, id, 1.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                mod(Attributes.WATER_MOVEMENT_EFFICIENCY, id, 1.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.ATTACK_DAMAGE, id, 2.0, AttributeModifier.Operation.ADD_VALUE);
            }
            case SPIDER -> {
                mod(Attributes.SCALE, id, -0.4, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.MOVEMENT_SPEED, id, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                mod(Attributes.ATTACK_DAMAGE, id, 3.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.SAFE_FALL_DISTANCE, id, 7.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.STEP_HEIGHT, id, 0.4, AttributeModifier.Operation.ADD_VALUE);
            }
            case BEAST -> {
                mod(Attributes.SCALE, id, 0.3, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.MAX_HEALTH, id, 16.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.ARMOR, id, 8.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.ATTACK_DAMAGE, id, 7.0, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.MOVEMENT_SPEED, id, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                mod(Attributes.KNOCKBACK_RESISTANCE, id, 0.5, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.ATTACK_KNOCKBACK, id, 0.5, AttributeModifier.Operation.ADD_VALUE);
                mod(Attributes.STEP_HEIGHT, id, 0.5, AttributeModifier.Operation.ADD_VALUE);
            }
        }
    }

    private void mod(Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        addAttributeModifier(attr, id, amount, op);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity instanceof Player player) || !(entity.level() instanceof ServerLevel level)) return true;
        switch (shape) {
            case WOLF -> {
                // Faro: revela monstros por perto (contorno) a cada 2 segundos
                if (player.tickCount % 40 == 0) {
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16),
                            e -> e instanceof Enemy && e.isAlive())) {
                        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false, false), player);
                    }
                }
            }
            case DOLPHIN -> {
                AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
                if (player.isInWaterOrBubble()) {
                    if (speed != null) speed.removeModifier(DOLPHIN_LAND);
                    keep(player, MobEffects.CONDUIT_POWER, 0, 120);
                    keep(player, MobEffects.DOLPHINS_GRACE, 0, 60);
                } else if (speed != null && !speed.hasModifier(DOLPHIN_LAND)) {
                    speed.addTransientModifier(new AttributeModifier(DOLPHIN_LAND, -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            }
            case SPIDER -> {
                // Agarrado na parede não acumula queda; enxerga no escuro (8 olhos)
                if (player.horizontalCollision) player.resetFallDistance();
                keep(player, MobEffects.NIGHT_VISION, 0, 400);
            }
            case BEAST -> beastTick(player, level, OaData.of(player).formElement());
            default -> {}
        }
        return true;
    }

    /** Passivas da Fera Elemental (fusões contam como os dois elementos-pai). */
    private static void beastTick(Player player, ServerLevel level, Element el) {
        if (el.matches(Element.FIRE) && player.isOnFire()) player.clearFire();
        if (el.matches(Element.EARTH)) keep(player, MobEffects.DAMAGE_RESISTANCE, 0, 80);
        if (el.matches(Element.WIND)) keep(player, MobEffects.JUMP, 1, 80);
        if (el.matches(Element.WATER) && player.isInWaterOrBubble()) keep(player, MobEffects.DOLPHINS_GRACE, 0, 60);
        if (el.matches(Element.SHADOW)) keep(player, MobEffects.NIGHT_VISION, 0, 400);
        if (el.matches(Element.LIGHT) && player.tickCount % 60 == 0 && player.getHealth() < player.getMaxHealth()) {
            player.heal(1.0F);
            level.sendParticles(Vfx.endRod(), player.getX(), player.getY() + player.getBbHeight() * 0.6,
                    player.getZ(), 3, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /**
     * Mantém um efeito "escondido" da forma (ambiente, sem partículas e sem ícone), renovando antes de acabar.
     * Não mexe se já existir um efeito igual de outra fonte (poção, sinalizador).
     */
    static void keep(LivingEntity e, Holder<MobEffect> effect, int amp, int duration) {
        MobEffectInstance cur = e.getEffect(effect);
        if (cur != null && !(cur.isAmbient() && !cur.isVisible())) return;
        if (cur == null || cur.getDuration() < duration - 40) {
            e.addEffect(new MobEffectInstance(effect, duration, amp, true, false, false));
        }
    }

    /** Remove os efeitos escondidos dados pela forma (só os ambientes e invisíveis, que são os nossos). */
    static void clearHidden(LivingEntity e) {
        for (Holder<MobEffect> h : java.util.List.of(MobEffects.NIGHT_VISION, MobEffects.CONDUIT_POWER, MobEffects.DOLPHINS_GRACE,
                MobEffects.DAMAGE_RESISTANCE, MobEffects.JUMP)) {
            MobEffectInstance cur = e.getEffect(h);
            if (cur != null && cur.isAmbient() && !cur.isVisible() && !cur.showIcon()) e.removeEffect(h);
        }
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(DOLPHIN_LAND);
    }
}
