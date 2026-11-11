package com.renanpontes.isekaimahou.client.ux;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Aviso "Limite da arena: N blocos" (v1.0 — A3): aparece no meio da tela, logo abaixo da mira (região ARENA do
 * {@link HudLayout}), quando o jogador chega perto da borda de uma arena de {@code ArenaBounds}; fora dela vira
 * "Fora da arena! Volte N blocos" em vermelho. A cor esquenta conforme a borda chega perto e pulsa nos últimos 3 blocos.
 * Sons: um toque suave ao entrar nos últimos 3 blocos e um grave ao sair da arena.
 */
final class ArenaNotice implements LayeredDraw.Layer {
    private static final int NEAR = 3;
    private static final int HOT = 0xFFFFB547;
    private static final int DANGER = 0xFFFF5A4A;

    @Nullable private static ClientArenas.Focus focus;
    /** Último foco mostrado (o aviso some devagar depois que o jogador se afasta). */
    @Nullable private static ClientArenas.Focus display;
    private static float shown;
    private static int lastBand = -1;
    private static final RandomSource RANDOM = RandomSource.create();

    /** A cada tick do cliente: escolhe a arena, toca os sons e solta as faíscas da parede. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !MahouConfig.safe(MahouConfig.SHOW_ARENA_BOUNDS)) {
            focus = null;
            lastBand = -1;
            return;
        }
        focus = ClientArenas.focus(mc.player.position());
        if (focus != null) display = focus;
        int band;
        if (focus == null) band = -1;
        else if (!focus.inside()) band = 2;
        else band = focus.edgeDistance() <= NEAR ? 1 : 0;
        if (band != lastBand && lastBand != -1) {
            if (band == 1 && lastBand == 0) play(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 0.35F);
            else if (band == 2) play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.6F);
            else if (band == 0 && lastBand == 2) play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.6F);
        }
        lastBand = band;
        if (focus != null) ArenaWallRenderer.particles(mc, focus, RANDOM);
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    static void clear() {
        focus = null;
        display = null;
        shown = 0;
        lastBand = -1;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        float target = focus == null || mc.options.hideGui ? 0F : 1F;
        shown += (target - shown) * 0.25F;
        ClientArenas.Focus f = display;
        if (shown < 0.04F || f == null) return;
        if (mc.player == null || mc.getDebugOverlay().showDebugScreen()) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        Font font = mc.font;

        int blocks = (int) Math.ceil(Math.abs(f.edgeDistance()));
        boolean inside = f.inside();
        Component main = inside
                ? Component.translatable("ux.isekai_mahou.arena.limit", blocks)
                : Component.translatable("ux.isekai_mahou.arena.outside", blocks);
        Component name = f.arena().name();
        boolean hasName = !name.getString().isEmpty();

        int base = 0xFF000000 | f.arena().rgb();
        float heat = inside ? f.closeness() : 1F;
        int color = inside ? (heat < 0.6F ? MahouUi.mix(MahouUi.lighten(base, 0.45F), HOT, heat / 0.6F)
                : MahouUi.mix(HOT, DANGER, (heat - 0.6F) / 0.4F)) : DANGER;
        boolean pulse = !inside || blocks <= NEAR;
        float p = pulse ? 0.5F + 0.5F * Mth.sin((Util.getMillis() % 700) / 700F * Mth.TWO_PI) : 0F;

        int w = Math.max(font.width(main), hasName ? Math.round(font.width(name) * 0.75F) : 0) + 26;
        int h = hasName ? 26 : 17;
        HudLayout.Rect r = HudLayout.arenaNotice(w, h);
        int al = Math.round(shown * 255);
        int x = r.x(), y = r.y();

        MahouUi.roundRect(g, x, y, w, h, MahouUi.alpha(MahouUi.INK_TOP, Math.round(shown * 0xD8)),
                MahouUi.alpha(MahouUi.INK_BOTTOM, Math.round(shown * 0xD8)));
        MahouUi.roundOutline(g, x, y, w, h, MahouUi.alpha(color, Math.round(al * (0.55F + 0.45F * p))));
        // barra de proximidade embaixo (cheia = em cima da borda)
        int barW = Math.round((w - 8) * (inside ? heat : 1F));
        g.fill(x + 4, y + h - 3, x + 4 + barW, y + h - 2, MahouUi.alpha(color, Math.round(al * 0.8F)));
        MahouUi.diamond(g, x + 8, y + (hasName ? 15 : 8), 2, MahouUi.alpha(color, al));
        MahouUi.diamond(g, x + w - 9, y + (hasName ? 15 : 8), 2, MahouUi.alpha(color, al));

        int ty = y + 4;
        if (hasName) {
            Component n = name.copy().withStyle(ChatFormatting.ITALIC);
            MahouUi.smallText(g, font, n, x + w / 2F - font.width(n) * 0.75F / 2F, ty, 0.75F,
                    MahouUi.alpha(MahouUi.TEXT_DIM, al), true);
            ty += 9;
        }
        if (al > 10) g.drawString(font, main, x + (w - font.width(main)) / 2, ty, MahouUi.alpha(MahouUi.lighten(color, 0.2F), al), true);
        HudLayout.drawn(HudLayout.Region.ARENA, r);
    }
}
