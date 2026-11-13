package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.List;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Retorno visual/sonoro das Leis de Transformação (partículas do servidor com orçamento baixo). */
final class TlawFx {
    static final int CHAIN_COLOR = 0xFF5A8A;

    static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F), size);
    }

    static void sound(Entity e, SoundEvent s, float vol, float pitch) {
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Anel horizontal de partículas. */
    static void ring(ServerLevel level, Vec3 c, double r, int points, ParticleOptions p) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
        }
    }

    static void at(Entity e, ParticleOptions p, int count, double spread) {
        if (e.level() instanceof ServerLevel level) {
            level.sendParticles(p, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), count, spread, spread * 0.8, spread, 0.02);
        }
    }

    /** Lista "A · B · C" com a cor de cada lei. */
    static MutableComponent names(List<TransformLawGlyph> laws) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < laws.size(); i++) {
            if (i > 0) out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            out.append(laws.get(i).displayName().withColor(laws.get(i).rgbColor()));
        }
        return out;
    }

    /** Votos selados: correntes de luz sobem em volta, um anel por lei, som de corrente + sino. */
    static void sealed(ServerPlayer p, List<TransformLawGlyph> laws) {
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < laws.size(); i++) {
            ring(level, p.position().add(0, 0.25 + i * 0.45, 0), 1.1 + i * 0.12, 14, dust(laws.get(i).rgbColor(), 1.3F));
        }
        level.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 24, 0.6, 0.8, 0.6, 0.6);
        sound(p, SoundEvents.CHAIN_PLACE, 1.0F, 0.6F);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.5F);
        p.sendSystemMessage(Component.literal("⛓ ").withColor(CHAIN_COLOR)
                .append(Component.translatable("message.isekai_mahou.tlaw.sealed", names(laws)).withColor(0xE8D8F0)));
    }

    /** Fim da transformação: os votos se desfazem. */
    static void broken(ServerPlayer p, boolean logout) {
        if (logout) return;
        sound(p, SoundEvents.CHAIN_BREAK, 0.9F, 0.8F);
        at(p, dust(CHAIN_COLOR, 1.0F), 10, 0.5);
        p.sendSystemMessage(Component.literal("⛓ ").withColor(0x8A7A9A)
                .append(Component.translatable("message.isekai_mahou.tlaw.broken").withColor(0xA89EC0)));
    }

    /** Aviso na barra de ação (com recarga de 1 s para não piscar). */
    static void warn(ServerPlayer p, TlawSessions.State st, Component msg) {
        long now = p.serverLevel().getGameTime();
        if (st != null) {
            if (now - st.lastWarn < 20) return;
            st.lastWarn = now;
        }
        p.displayClientMessage(msg, true);
        sound(p, SoundEvents.CHAIN_HIT, 0.8F, 0.6F);
    }

    private TlawFx() {}
}
