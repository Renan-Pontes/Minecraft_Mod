package com.renanpontes.isekaimahou.feature.world_events.client;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.feature.world_events.WorldEventType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * SÓ CLIENTE. O que o jogador VÊ dos eventos do céu, sem nada na tela: a névoa puxa para a cor do evento
 * (vermelho na Lua de Sangue, azul na Tempestade Arcana, violeta na Tempestade de Mana) e algumas partículas
 * flutuam em volta de quem está ao ar livre. Tudo some/aparece aos poucos e respeita o preset gráfico.
 */
public final class WeClientState {
    /** Evento ativo no nível do jogador (null = nenhum). */
    private static WorldEventType active;
    /** Último evento com cor (para a névoa sumir aos poucos depois que ele acaba). */
    private static WorldEventType shown;
    /** 0..1: quanto do efeito está visível (sobe e desce devagar). */
    private static float strength;

    private WeClientState() {}

    public static void apply(int ordinal) {
        WorldEventType[] all = WorldEventType.values();
        active = ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        active = null;
        shown = null;
        strength = 0;
    }

    /** Força máxima da cor na névoa para cada evento (os locais não mudam o céu). */
    private static float tint(WorldEventType t) {
        return switch (t) {
            case BLOOD_MOON -> 0.38F;
            case ARCANE_STORM -> 0.16F;
            case MANA_STORM -> 0.22F;
            default -> 0F;
        };
    }

    static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        boolean on = active != null && tint(active) > 0 && level.dimension() == Level.OVERWORLD;
        if (on) shown = active;
        strength = Mth.clamp(strength + (on ? 0.01F : -0.01F), 0F, 1F);
        if (strength <= 0F) {
            shown = null;
            return;
        }
        if (!on || shown == null) return;
        ambient(level, player, shown);
    }

    /** Partículas esparsas em volta do jogador, só ao ar livre. */
    private static void ambient(ClientLevel level, LocalPlayer player, WorldEventType type) {
        ParticleOptions particle;
        int base;
        switch (type) {
            case MANA_STORM -> { particle = ParticleTypes.END_ROD; base = 2; }
            case BLOOD_MOON -> { particle = ParticleTypes.CRIMSON_SPORE; base = 2; }
            default -> { return; }
        }
        if (level.getGameTime() % 4 != 0) return;
        RandomSource rnd = level.random;
        int n = FxQuality.count(base);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            double x = player.getX() + (rnd.nextDouble() - 0.5) * 28;
            double y = player.getY() + rnd.nextDouble() * 10 - 1;
            double z = player.getZ() + (rnd.nextDouble() - 0.5) * 28;
            if (!level.canSeeSky(m.set(x, y, z))) continue;
            double vy = type == WorldEventType.MANA_STORM ? -0.04 - rnd.nextDouble() * 0.04 : 0.0;
            level.addParticle(particle, x, y, z, 0, vy, 0);
        }
    }

    static void onFogColor(ViewportEvent.ComputeFogColor e) {
        if (strength <= 0F || shown == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // debaixo da terra o céu não aparece: lá a névoa fica como é
        BlockPos cam = BlockPos.containing(e.getCamera().getPosition());
        float sky = mc.level.getBrightness(LightLayer.SKY, cam) / 15F;
        float k = tint(shown) * strength * sky;
        if (k <= 0.001F) return;
        int c = shown.color;
        float r = (c >> 16 & 0xFF) / 255F, g = (c >> 8 & 0xFF) / 255F, b = (c & 0xFF) / 255F;
        // a cor do evento entra escurecida junto com a névoa atual (à noite continua escuro)
        float lum = Math.max(0.12F, Math.max(e.getRed(), Math.max(e.getGreen(), e.getBlue())));
        e.setRed(Mth.lerp(k, e.getRed(), r * lum));
        e.setGreen(Mth.lerp(k, e.getGreen(), g * lum));
        e.setBlue(Mth.lerp(k, e.getBlue(), b * lum));
    }
}
