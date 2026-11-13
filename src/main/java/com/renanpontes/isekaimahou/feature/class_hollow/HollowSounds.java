package com.renanpontes.isekaimahou.feature.class_hollow;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sons próprios da classe Hollow (sintetizados por {@code tools/gen/class_hollow_sfx.py}, em
 * {@code assets/isekai_mahou/sounds/class_hollow/}). Sempre tocados JUNTO com um som do vanilla, para a cena continuar
 * boa mesmo se um arquivo faltar.
 */
public final class HollowSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, IsekaiMahou.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ROAR = reg("hollow.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> CERO_CHARGE = reg("hollow.cero_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> CERO_FIRE = reg("hollow.cero_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> SONIDO = reg("hollow.sonido");
    public static final DeferredHolder<SoundEvent, SoundEvent> GARGANTA = reg("hollow.garganta");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEVOUR = reg("hollow.devour");
    public static final DeferredHolder<SoundEvent, SoundEvent> MASK = reg("hollow.mask");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(IsekaiMahou.id(id)));
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private HollowSounds() {}
}
