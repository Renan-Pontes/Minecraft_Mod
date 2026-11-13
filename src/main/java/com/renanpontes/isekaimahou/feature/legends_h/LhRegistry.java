package com.renanpontes.isekaimahou.feature.legends_h;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_f.effect.LfMobEffect;
import com.renanpontes.isekaimahou.feature.legends_h.entity.SheerHeartAttackEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros do legends_h (glifos de anime comuns): a entidade do Sheer Heart Attack (TEMPORÁRIA, tipo {@code noSave})
 * e os efeitos de status que aparecem na tela (Full Cowl, Scale Mail, Boost transferido, marca do Bites the Dust e o
 * "fora do tempo" do King Crimson). As regras de cada um ficam nos gerenciadores do módulo.
 */
public final class LhRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    // ---------------------------------------------------------------- entidade
    /** O tanquinho com caveira que caça o alvo mais quente. */
    public static final DeferredHolder<EntityType<?>, EntityType<SheerHeartAttackEntity>> SHEER_HEART_ATTACK = ENTITIES.register("lh_sheer_heart_attack",
            () -> EntityType.Builder.<SheerHeartAttackEntity>of(SheerHeartAttackEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.45F).noSave().fireImmune().clientTrackingRange(10).updateInterval(1).build("lh_sheer_heart_attack"));

    // ---------------------------------------------------------------- efeitos
    /** Full Cowl (One For All): os atributos variam com a porcentagem e são postos à parte (ver {@code FullCowl}). */
    public static final DeferredHolder<MobEffect, MobEffect> FULL_COWL = EFFECTS.register("lh_full_cowl",
            () -> new LfMobEffect(MobEffectCategory.BENEFICIAL, 0x5CFF7A, false));

    /** Balance Breaker — Scale Mail: a armadura do Dragão Vermelho. */
    public static final DeferredHolder<MobEffect, MobEffect> SCALE_MAIL = EFFECTS.register("lh_scale_mail",
            () -> new LfMobEffect(MobEffectCategory.BENEFICIAL, 0xD0102A, false)
                    .addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.lh_scale_mail"), 8.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id("effect.lh_scale_mail"), 4.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.lh_scale_mail"), 0.5,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("effect.lh_scale_mail"), 3.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.lh_scale_mail"), 0.1,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** "Transfer!": dano +15% por nível (nível = amplificador + 1), corpo a corpo e magia. */
    public static final DeferredHolder<MobEffect, MobEffect> BOOSTED = EFFECTS.register("lh_boosted",
            () -> new LfMobEffect(MobEffectCategory.BENEFICIAL, 0x3CFF6A, false));

    /** Marca do Bites the Dust: quando o pavio acaba, o tempo de quem está marcado volta. */
    public static final DeferredHolder<MobEffect, MobEffect> REWIND_MARK = EFFECTS.register("lh_rewind_mark",
            () -> new LfMobEffect(MobEffectCategory.NEUTRAL, 0xFF7AC8, false));

    /** King Crimson (em si mesmo): fora do tempo apagado — ninguém te vê, nada te acerta, você também não acerta. */
    public static final DeferredHolder<MobEffect, MobEffect> TIME_SKIP = EFFECTS.register("lh_time_skip",
            () -> new LfMobEffect(MobEffectCategory.BENEFICIAL, 0xB01040, false));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
    }

    private LhRegistry() {}
}
