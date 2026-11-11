package com.renanpontes.isekaimahou.client.status;

import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Letreiro ao entrar num lugar: nome grande no centro da tela na cor do lugar, subtítulo com a facção e, na primeira
 * visita, "Novo lugar descoberto". Ao sair, um aviso menor "Saindo de ...".
 */
final class PlaceBanner {
    private static final long ENTER_MS = 4200;
    private static final long LEAVE_MS = 2200;

    private static Component title;
    private static Component subtitle;
    private static boolean discovered;
    private static boolean leaving;
    private static int color;
    private static long start;
    /** v1.1: o último lugar anunciado e quando (para não repetir o letreiro). */
    private static final long REPEAT_MS = 30_000L;
    private static String lastPlace = "";
    private static long lastShown;

    static void showEnter(Places.Place p, boolean firstVisit) {
        long now = Util.getMillis();
        // v1.1: andar na borda de um lugar não repete o letreiro (mesmo lugar em até 30 s = só o registro de avisos)
        if (!firstVisit && p.id().equals(lastPlace) && now - lastShown < REPEAT_MS) {
            lastShown = now;
            return;
        }
        lastPlace = p.id();
        lastShown = now;
        if (firstVisit) {
            com.renanpontes.isekaimahou.client.notify.NoticeLog.add(Component.translatable("gui.isekai_mahou.place.discovered")
                    .append(Component.literal(": ")).append(p.name()), com.renanpontes.isekaimahou.client.notify.NoticeLog.Kind.INFO);
        }
        title = p.name();
        subtitle = p.faction().isBlank() ? null : Component.translatable("faction.isekai_mahou." + p.faction());
        discovered = firstVisit;
        leaving = false;
        color = p.color() & 0xFFFFFF;
        start = Util.getMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(firstVisit ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.AMETHYST_BLOCK_CHIME,
                    firstVisit ? 0.5F : 0.8F, firstVisit ? 1.2F : 0.9F);
        }
    }

    static void showLeave(Places.Place p) {
        long now = Util.getMillis();
        // v1.1: não corta o letreiro de entrada que ainda está na tela, nem repete a saída do mesmo lugar logo em seguida
        if (title != null && !leaving && now - start < ENTER_MS) return;
        if (p.id().equals(lastPlace) && now - lastShown < REPEAT_MS && leaving) return;
        lastPlace = p.id();
        lastShown = now;
        title = Component.translatable("gui.isekai_mahou.place.leaving", p.name());
        subtitle = null;
        discovered = false;
        leaving = true;
        color = p.color() & 0xFFFFFF;
        start = Util.getMillis();
    }

    static void clear() {
        title = null;
        lastPlace = "";
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        if (title == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        long age = Util.getMillis() - start;
        long total = leaving ? LEAVE_MS : ENTER_MS;
        if (age >= total) {
            title = null;
            return;
        }
        float in = Mth.clamp(age / 450F, 0F, 1F);
        float out = Mth.clamp((total - age) / 900F, 0F, 1F);
        float alpha = Math.min(in, out);
        if (alpha <= 0.02F) return;
        Font font = mc.font;
        int w = g.guiWidth();
        int cy = (int) (g.guiHeight() * 0.27F);
        int a = Mth.clamp((int) (alpha * 255), 4, 255);

        float scale = leaving ? 1.5F : 2.6F + (1F - in) * 0.4F;
        int tw = font.width(title);
        // Linhas decorativas que se abrem a partir do centro
        int lineHalf = (int) ((tw * scale / 2F + 18) * Mth.clamp(age / 600F, 0F, 1F));
        int lineY = cy + (int) (9 * scale / 2F) + 4;
        int lc = (a << 24) | color;
        if (!leaving) {
            g.fill(w / 2 - lineHalf, lineY, w / 2 + lineHalf, lineY + 1, lc);
            g.fill(w / 2 - lineHalf / 2, lineY + 2, w / 2 + lineHalf / 2, lineY + 3, ((a / 2) << 24) | color);
            diamond(g, w / 2 - lineHalf - 3, lineY, lc);
            diamond(g, w / 2 + lineHalf + 3, lineY, lc);
        }

        g.pose().pushPose();
        g.pose().translate(w / 2F, cy - 9 * scale / 2F, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, title, -tw / 2, 0, (a << 24) | color, true);
        g.pose().popPose();

        int y = lineY + 7;
        if (subtitle != null) {
            g.pose().pushPose();
            g.pose().translate(w / 2F, y, 0);
            g.pose().scale(1.3F, 1.3F, 1F);
            g.drawString(font, subtitle, -font.width(subtitle) / 2, 0, (a << 24) | 0xE8E2F4, true);
            g.pose().popPose();
            y += 15;
        }
        if (discovered) {
            Component d = Component.translatable("gui.isekai_mahou.place.discovered");
            g.drawString(font, d, w / 2 - font.width(d) / 2, y, (a << 24) | 0xFFD86B, true);
        }
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int c) {
        g.fill(cx - 1, cy - 2, cx + 2, cy + 3, c);
        g.fill(cx - 2, cy - 1, cx + 3, cy + 2, c);
    }

    private PlaceBanner() {}
}
