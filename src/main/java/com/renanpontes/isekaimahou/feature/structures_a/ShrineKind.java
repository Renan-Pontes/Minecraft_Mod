package com.renanpontes.isekaimahou.feature.structures_a;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;

/**
 * Os seis Santuários Elementais: o que cada prova invoca, a recompensa e o "clima" (sons, cor da barra).
 * As criaturas do mod são referenciadas por id em texto (módulo creatures_d); se não existirem, a prova
 * usa a reserva vanilla.
 */
public enum ShrineKind {
    FIRE(Element.FIRE, BossEvent.BossBarColor.RED, 6,
            "isekai_mahou:salamander", "isekai_mahou:ember_imp", "minecraft:magma_cube", "minecraft:blaze",
            List.of("ignite", "explosion", "ember", "propulsion", "smoke"),
            SoundEvents.BLAZE_AMBIENT, SoundEvents.FIRECHARGE_USE),
    WATER(Element.WATER, BossEvent.BossBarColor.BLUE, 6,
            "isekai_mahou:undine", "isekai_mahou:tide_serpent", "minecraft:drowned", "minecraft:guardian",
            List.of("soak", "tide", "bubble", "water_blade", "create_water", "water_breathing"),
            SoundEvents.CONDUIT_ACTIVATE, SoundEvents.PLAYER_SPLASH_HIGH_SPEED),
    EARTH(Element.EARTH, BossEvent.BossBarColor.GREEN, 6,
            "isekai_mahou:moss_golem", "isekai_mahou:crystal_mole", "minecraft:husk", "minecraft:zombie",
            List.of("rock", "raise_pillar", "stalagmite", "stoneskin", "excavate"),
            SoundEvents.DEEPSLATE_BREAK, SoundEvents.ROOTED_DIRT_PLACE),
    WIND(Element.WIND, BossEvent.BossBarColor.WHITE, 5,
            "isekai_mahou:sylph", "isekai_mahou:storm_hawk", "minecraft:stray", "minecraft:breeze",
            List.of("wind_blade", "gust", "impulse", "double_jump", "glide"),
            SoundEvents.BELL_RESONATE, SoundEvents.PHANTOM_FLAP),
    LIGHT(Element.LIGHT, BossEvent.BossBarColor.YELLOW, 6,
            "isekai_mahou:lumen_sprite", "isekai_mahou:solar_sentinel", "minecraft:skeleton", "minecraft:vex",
            List.of("holy_ray", "heal", "blind", "regenerate", "reveal"),
            SoundEvents.AMETHYST_BLOCK_RESONATE, SoundEvents.BEACON_POWER_SELECT),
    SHADOW(Element.SHADOW, BossEvent.BossBarColor.PURPLE, 6,
            "isekai_mahou:creeping_shade", "isekai_mahou:void_bat", "minecraft:spider", "minecraft:witch",
            List.of("life_drain", "weaken", "fear", "darkness", "shadow_step"),
            SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundEvents.ENDERMAN_TELEPORT);

    public final Element element;
    public final BossEvent.BossBarColor barColor;
    /** Raio (blocos) da arena em volta do altar onde as criaturas surgem. */
    public final int arenaRadius;
    /** Criatura comum / forte do mod (id em texto) e as reservas vanilla. */
    public final String weakMob, strongMob, vanillaWeak, vanillaStrong;
    /** Glifos (path) dados como recompensa, do mais básico ao mais raro. */
    public final List<String> rewardGlyphs;
    public final SoundEvent introSound;
    public final SoundEvent summonSound;

    ShrineKind(Element element, BossEvent.BossBarColor barColor, int arenaRadius, String weakMob, String strongMob,
               String vanillaWeak, String vanillaStrong, List<String> rewardGlyphs, SoundEvent introSound, SoundEvent summonSound) {
        this.element = element;
        this.barColor = barColor;
        this.arenaRadius = arenaRadius;
        this.weakMob = weakMob;
        this.strongMob = strongMob;
        this.vanillaWeak = vanillaWeak;
        this.vanillaStrong = vanillaStrong;
        this.rewardGlyphs = rewardGlyphs;
        this.introSound = introSound;
        this.summonSound = summonSound;
    }

    /** Id da estrutura (e do baú): shrine_fire, shrine_water... */
    public String structureId() {
        return "shrine_" + element.id;
    }

    /** Chave do contador/flag da prova concluída (convenção do contrato: "shrine.<elemento>"). */
    public String progressKey() {
        return "shrine." + element.id;
    }

    /** Nome do santuário ("Templo das Brasas"...), já na cor do elemento. */
    public MutableComponent displayName() {
        String key = "structure.isekai_mahou." + structureId();
        return Component.translatable(key).withColor(element.color);
    }

    public static ShrineKind of(Element e) {
        for (ShrineKind k : values()) if (k.element == e) return k;
        return FIRE;
    }
}
