package com.renanpontes.isekaimahou.feature.towers;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;

/**
 * <b>Afixos de andar</b> (v1.0): modificadores sorteados quando um andar de combate da Torre de Desafio desperta.
 * Cada criatura do andar recebe a marca {@code isekai_mahou.affix.<id>} (tag da entidade) — os afixos de "na hora do
 * golpe/morte" são resolvidos pelos eventos em {@link TwAffixEvents} lendo essa marca; os de atributo/efeito são
 * aplicados aqui, no nascimento. Nada mexe em blocos.
 * <p>Quantos por andar: térreo 0 (é o andar de aprender), andares 2–4 um, 5–6 dois, Salão da Elite três.
 * Cada afixo vencido rende +20% do XP do andar.
 */
public enum TowerAffix {
    /** +40% de vida. */
    FORTIFIED("fortified", 0xB0B8C8),
    /** +30% de dano. */
    SAVAGE("savage", 0xE0503C),
    /** +25% de velocidade. */
    SWIFT("swift", 0x7FE8FF),
    /** +2 criaturas por onda (resolvido no Núcleo). */
    HORDE("horde", 0xC8A050),
    /** Regeneram vida o tempo todo. */
    REGENERATING("regenerating", 0xFF7FB0),
    /** Nascem com escudo (absorção) e resistência. */
    SHIELDED("shielded", 0x7FA8FF),
    /** Explodem 1 s depois de morrer (sem quebrar blocos). */
    VOLATILE("volatile", 0xFF9A2A),
    /** Curam-se com o dano que causam. */
    VAMPIRIC("vampiric", 0xB01840),
    /** Os golpes incendeiam. */
    BURNING("burning", 0xFF6A1A),
    /** Os golpes deixam lento. */
    CHILLING("chilling", 0xA8E4FF),
    /** Um campeão (maior, bem mais forte) em cada onda (resolvido no Núcleo). */
    CHAMPION("champion", 0xFFD24A),
    /** Devolvem parte do dano corpo a corpo. */
    THORNS("thorns", 0x6FBF4A);

    public static final String TAG_PREFIX = "isekai_mahou.affix.";
    /** XP extra do andar por afixo. */
    public static final float XP_BONUS = 0.20F;

    private final String id;
    private final int color;

    TowerAffix(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    /** Marca de entidade que os eventos conferem. */
    public String tag() {
        return TAG_PREFIX + id;
    }

    /** Nome colorido, com a descrição ao passar o mouse. */
    public MutableComponent label() {
        Component desc = Component.translatable("tower.isekai_mahou.affix." + id + ".desc");
        return Component.translatable("tower.isekai_mahou.affix." + id)
                .withStyle(s -> s.withColor(color).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, desc)));
    }

    @Nullable
    public static TowerAffix byId(String id) {
        for (TowerAffix a : values()) {
            if (a.id.equals(id)) return a;
        }
        return null;
    }

    /** Quantos afixos o andar sorteia. */
    public static int countFor(int floor, boolean elite) {
        if (floor <= 0) return 0;
        if (elite) return 3;
        return floor <= 3 ? 1 : 2;
    }

    /** Sorteia {@code count} afixos diferentes. */
    public static List<TowerAffix> roll(RandomSource random, int count) {
        List<TowerAffix> pool = new ArrayList<>(List.of(values()));
        List<TowerAffix> out = new ArrayList<>();
        while (out.size() < count && !pool.isEmpty()) {
            out.add(pool.remove(random.nextInt(pool.size())));
        }
        return out;
    }

    /** "Fortificados · Horda" (ou vazio). */
    public static MutableComponent list(List<TowerAffix> affixes) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < affixes.size(); i++) {
            if (i > 0) c.append(Component.literal(" · "));
            c.append(affixes.get(i).label());
        }
        return c;
    }

    /** Aplica este afixo a uma criatura recém-nascida do andar. */
    public void apply(Mob m) {
        m.addTag(tag());
        switch (this) {
            case FORTIFIED -> {
                mod(m, Attributes.MAX_HEALTH, 0.40);
                m.setHealth(m.getMaxHealth());
            }
            case SAVAGE -> mod(m, Attributes.ATTACK_DAMAGE, 0.30);
            case SWIFT -> mod(m, Attributes.MOVEMENT_SPEED, 0.25);
            case REGENERATING -> effect(m, MobEffects.REGENERATION, 0);
            case SHIELDED -> {
                effect(m, MobEffects.ABSORPTION, 2);
                effect(m, MobEffects.DAMAGE_RESISTANCE, 0);
            }
            case BURNING -> effect(m, MobEffects.FIRE_RESISTANCE, 0);
            default -> { }
        }
    }

    /** Campeão da onda: maior, o dobro da vida, mais dano, e brilha para ser achado. */
    public static void makeChampion(Mob m) {
        m.addTag(TAG_PREFIX + "champion_unit");
        modNamed(m, Attributes.MAX_HEALTH, "tower_affix_champion", 1.0);
        modNamed(m, Attributes.ATTACK_DAMAGE, "tower_affix_champion", 0.25);
        modNamed(m, Attributes.SCALE, "tower_affix_champion", 0.35);
        m.setHealth(m.getMaxHealth());
        m.setGlowingTag(true);
    }

    private void mod(Mob m, Holder<Attribute> attr, double amount) {
        modNamed(m, attr, "tower_affix_" + id, amount);
    }

    private static void modNamed(Mob m, Holder<Attribute> attr, String name, double amount) {
        AttributeInstance inst = m.getAttribute(attr);
        ResourceLocation key = IsekaiMahou.id(name);
        if (inst != null && inst.getModifier(key) == null) {
            inst.addPermanentModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void effect(Mob m, Holder<net.minecraft.world.effect.MobEffect> effect, int amp) {
        m.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amp, true, false));
    }
}
