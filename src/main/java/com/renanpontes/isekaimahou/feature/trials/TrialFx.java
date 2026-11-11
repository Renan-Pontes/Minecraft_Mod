package com.renanpontes.isekaimahou.feature.trials;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Títulos, barra de ação e efeitos de teleporte usados pelas Câmaras (servidor). */
public final class TrialFx {

    /** Título grande (+ subtítulo, pode ser null) com tempos em ticks. */
    public static void title(ServerPlayer p, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle == null ? Component.empty() : subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void actionBar(ServerPlayer p, Component text) {
        p.displayClientMessage(text, true);
    }

    /** Som tocado só para o jogador (não vaza para outros). */
    public static void ding(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    /** Coluna de luz + estouro do elemento + som de teleporte no ponto (saída e chegada). */
    public static void warp(ServerLevel level, Vec3 at, Element element) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 40, 0.35, 0.8, 0.35, 0.05);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
        ElementFx.burst(level, at.add(0, 1.0, 0), element, 26, 0.5);
        for (int i = 0; i < 10; i++) {
            level.sendParticles(ElementFx.dust(element, 1.6F), at.x, at.y + i * 0.35, at.z, 2, 0.1, 0.05, 0.1, 0);
        }
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    private TrialFx() {}
}
