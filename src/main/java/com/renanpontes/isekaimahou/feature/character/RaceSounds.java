package com.renanpontes.isekaimahou.feature.character;

import java.util.EnumMap;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sons das Transformações raciais (v1.3), sintetizados por {@code tools/gen/character_sounds.py} (arquivos em
 * {@code sounds/character/}, chaves {@code race.*} no {@code sounds.json}): a carga, a explosão, o desfazer e o
 * rugido/assinatura de cada raça.
 */
public final class RaceSounds {
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, IsekaiMahou.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> FORM_CHARGE = reg("race.form_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORM_BURST = reg("race.form_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORM_END = reg("race.form_end");
    private static final Map<Race, DeferredHolder<SoundEvent, SoundEvent>> ROARS = new EnumMap<>(Race.class);

    static {
        for (Race r : Race.ALL) ROARS.put(r, reg("race.roar." + r.id));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(IsekaiMahou.id(name)));
    }

    /** Rugido/assinatura da Transformação da raça. */
    public static SoundEvent roar(Race r) {
        return ROARS.get(r).get();
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private RaceSounds() {}
}
