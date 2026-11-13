package com.renanpontes.isekaimahou.feature.espada;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sons próprios dos Espada (sintetizados por {@code tools/gen/espada_sfx.py}, arquivos em
 * {@code assets/isekai_mahou/sounds/espada/}). Ids {@code isekai_mahou:espada.<nome>}.
 */
public final class EspadaSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, IsekaiMahou.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SONIDO = reg("sonido");
    public static final DeferredHolder<SoundEvent, SoundEvent> CERO_CHARGE = reg("cero_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> CERO_FIRE = reg("cero_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANZA_CHARGE = reg("lanza_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANZA_EXPLODE = reg("lanza_explode");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELEASE = reg("release");
    public static final DeferredHolder<SoundEvent, SoundEvent> RESPIRA = reg("respira");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRUMBLE = reg("crumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAW = reg("claw");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROAR = reg("roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> BALA = reg("bala");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIERRO = reg("hierro");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register("espada." + name, () -> SoundEvent.createVariableRangeEvent(IsekaiMahou.id("espada." + name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    /** Toca para todos perto da entidade (categoria hostil). */
    public static void play(Entity e, DeferredHolder<SoundEvent, SoundEvent> sound, float volume, float pitch) {
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), sound.get(), net.minecraft.sounds.SoundSource.HOSTILE, volume, pitch);
    }

    private EspadaSounds() {}
}
