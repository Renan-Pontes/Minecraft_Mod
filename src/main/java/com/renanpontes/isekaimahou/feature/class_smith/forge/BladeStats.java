package com.renanpontes.isekaimahou.feature.class_smith.forge;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Números derivados das partes de uma Lâmina Forjada. Tudo aqui é trocado por outra coisa: comprimento dá dano e
 * alcance mas pesa; largura dá dano e repulsão mas pesa; curva dá velocidade e corte em área; ponta e serrilha só valem
 * nos golpes (perfurar armadura / sangrar — ver {@code SmithEvents}). Nenhuma combinação é "a melhor".
 *
 * @param damage   modificador de dano de ataque (somado ao 1 do jogador)
 * @param speed    modificador da velocidade de ataque (o jogador tem 4; espada vanilla = −2,4)
 * @param reach    alcance extra de golpe (blocos)
 * @param knockback repulsão extra
 * @param sweep    fração do dano no corte em área
 * @param weight   peso (só para mostrar e para o Martelo)
 * @param durability durabilidade máxima
 */
public record BladeStats(float damage, float speed, float reach, float knockback, float sweep, float weight, int durability) {
    public static final ResourceLocation REACH_ID = IsekaiMahou.id("forged_blade_reach");
    public static final ResourceLocation KNOCKBACK_ID = IsekaiMahou.id("forged_blade_knockback");
    public static final ResourceLocation SWEEP_ID = IsekaiMahou.id("forged_blade_sweep");

    /** Armadura perfurada por degrau de Ponta (dano extra proporcional à armadura do alvo). */
    public static final float PIERCE_PER_TIP = 0.10F;
    /** Chance de golpe certeiro (×1,5) por degrau de Ponta. */
    public static final float CRIT_PER_TIP = 0.06F;

    public static BladeStats of(ForgedBlade b) {
        BladeFamily f = b.family();
        BladeMaterial m = b.material();
        float damage = m.damage + f.damage + f.damagePerLength * b.length() + 0.6F * b.width() + 0.2F * b.serration()
                + b.grip().damage;
        float speed = f.speed + f.speedPerLength * b.length() - 0.1F * b.width() + 0.1F * b.curve() + b.grip().speed + m.speed;
        // nunca mais lento que 0,6 golpe/s nem mais rápido que 3 golpes/s
        speed = Mth.clamp(speed, -3.4F, -1.0F);
        float reach = f.reach + f.reachPerLength * b.length() + b.grip().reach + (f == BladeFamily.SPEAR ? 0.15F * b.tip() : 0F);
        float knockback = 0.15F * b.width() + (f == BladeFamily.HAMMER ? 0.6F : 0F) + (f == BladeFamily.AXE ? 0.2F : 0F);
        float sweep = 0.15F * b.curve() + (f == BladeFamily.SCYTHE ? 0.4F : 0F)
                + (f == BladeFamily.BLADE && b.length() >= 3 ? 0.2F : 0F);
        float weight = m.weight * (1F + 0.3F * b.width() + 0.15F * b.length() + f.weight);
        int durability = Math.round(m.durability * (1F + 0.1F * (b.quality() - 1)));
        return new BladeStats(damage, speed, reach, knockback, Math.min(1F, sweep), weight, durability);
    }

    /** Golpes por segundo (para mostrar). */
    public float attacksPerSecond() {
        return 4F + speed;
    }

    /** Dano total de um golpe cheio (o 1 do jogador + o modificador). */
    public float hitDamage() {
        return 1F + damage;
    }

    /** "Leve", "Médio", "Pesado", "Colossal". */
    public String weightClass() {
        if (weight < 1.4F) return "light";
        if (weight < 2.0F) return "medium";
        if (weight < 2.7F) return "heavy";
        return "colossal";
    }

    /** Modificadores de atributo da arma na mão principal. */
    public ItemAttributeModifiers modifiers() {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        b.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        b.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed,
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        if (Math.abs(reach) > 0.01F) {
            b.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID, reach,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        if (knockback > 0.01F) {
            b.add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(KNOCKBACK_ID, knockback,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        if (sweep > 0.01F) {
            b.add(Attributes.SWEEPING_DAMAGE_RATIO, new AttributeModifier(SWEEP_ID, sweep,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        return b.build();
    }
}
