package com.renanpontes.isekaimahou.feature.ultimates.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.feature.ultimates.entity.MeteorEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Som crescente do meteoro: um rugido de vento que segue a rocha e fica mais alto e mais agudo conforme ela se
 * aproxima do chão. Para sozinho quando a rocha some (impacto).
 */
final class MeteorSound extends AbstractTickableSoundInstance {
    /** No máximo tantos rugidos ao mesmo tempo (a Chuva solta dezenas de rochas: não esgota os canais de som). */
    private static final int MAX_ACTIVE = 10;
    private static final List<MeteorSound> ACTIVE = new ArrayList<>();

    private final MeteorEntity meteor;

    /** Começa o rugido da rocha (se ainda houver canal livre para meteoros). */
    static void start(MeteorEntity meteor) {
        ACTIVE.removeIf(s -> s.isStopped() || s.meteor.isRemoved() || s.meteor.level() != meteor.level());
        if (ACTIVE.size() >= MAX_ACTIVE) return;
        MeteorSound s = new MeteorSound(meteor);
        ACTIVE.add(s);
        Minecraft.getInstance().getSoundManager().play(s);
    }

    static void clear() {
        ACTIVE.clear();
    }

    MeteorSound(MeteorEntity meteor) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.meteor = meteor;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.3F;
        this.pitch = 0.5F;
        this.x = meteor.getX();
        this.y = meteor.getY();
        this.z = meteor.getZ();
    }

    @Override
    public void tick() {
        if (meteor.isRemoved() || meteor.progress(0F) >= 1.05F) {
            stop();
            return;
        }
        float t = Mth.clamp(meteor.progress(0F), 0F, 1F);
        this.x = meteor.getX();
        this.y = meteor.getY();
        this.z = meteor.getZ();
        // Volume acima de 1 aumenta o alcance: a rocha grande é ouvida de longe
        this.volume = (0.4F + 2.2F * t * t) * (0.7F + 0.3F * meteor.size());
        this.pitch = 0.45F + 0.55F * t;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
