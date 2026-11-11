package com.renanpontes.isekaimahou.feature.class_gambler.client;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.RouletteWheelEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckBand;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseRanks;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * HUD do Apostador:
 * <ul>
 *   <li>a PLACA DA SORTE acima da mira: primeiro "embaralha" (dados rolando, cartas virando, roleta girando, rolos do
 *   caça-níquel), depois revela a faixa ("Ótimo +50%") na cor dela, e some;</li>
 *   <li>a barra de TENSÃO embaixo da placa (só o Apostador), que pisca perto de encher;</li>
 *   <li>o JACKPOT: letreiro arco-íris com o tempo + lâmpadas de cassino piscando na borda da tela;</li>
 *   <li>a RODADA DA CASA na coluna de avisos da esquerda (reserva no {@link HudLayout}): abates/meta e tempo.</li>
 * </ul>
 * Nada fica sobre o minimapa (a placa é central; a Rodada usa a região de avisos).
 */
public final class RollHud {
    private static final String CHALLENGE_ID = "class_gambler_challenge";
    private static final int SHUFFLE = 10;
    private static final int PLATE_W = 132, PLATE_H = 30;

    static void init() {
        HudLayout.reserve(HudLayout.Region.TOASTS, CHALLENGE_ID, () -> GbClientState.challengeVisible() ? 34 : 0);
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        Font font = mc.font;
        if (GbClientState.jackpotTicks > 0) renderJackpot(g, font, w, h, pt);
        if (GbClientState.rollAge < GbClientState.ROLL_SHOW) renderPlate(g, font, w, h, pt);
        if (GbClientState.challengeVisible()) renderChallenge(g, font);
    }

    // ------------------------------------------------------------------ placa da sorte
    private static void renderPlate(GuiGraphics g, Font font, int w, int h, float pt) {
        float age = GbClientState.rollAge + pt;
        float alpha = age > GbClientState.ROLL_SHOW - 8 ? Math.max(0F, (GbClientState.ROLL_SHOW - age) / 8F) : 1F;
        int a = (int) (alpha * 255);
        if (a < 8) return;
        LuckBand band = GbClientState.band;
        int x = (w - PLATE_W) / 2, y = h / 2 - 66;
        boolean revealed = age >= SHUFFLE;
        int accent = revealed ? 0xFF000000 | band.rgb : 0xFFFFD86B;
        float pop = revealed ? 1F + Math.max(0F, 0.12F * (1F - (age - SHUFFLE) / 6F)) : 1F;
        g.pose().pushPose();
        g.pose().translate(w / 2F, y + PLATE_H / 2F, 0);
        g.pose().scale(pop, pop, 1F);
        g.pose().translate(-w / 2F, -(y + PLATE_H / 2F), 0);
        MahouUi.roundRect(g, x, y, PLATE_W, PLATE_H, MahouUi.alpha(0xFF1A0E22, a * 0xE0 / 255), MahouUi.alpha(0xFF0B0612, a * 0xE0 / 255));
        MahouUi.roundOutline(g, x, y, PLATE_W, PLATE_H, MahouUi.alpha(accent, a));
        // bolinhas de "letreiro" nos cantos
        for (int i = 0; i < 6; i++) {
            boolean on = ((int) (age / 3) + i) % 2 == 0;
            int c = MahouUi.alpha(on ? 0xFFFFD86B : 0xFF6A4A20, a);
            g.fill(x + 4 + i * 4, y + 2, x + 6 + i * 4, y + 4, c);
            g.fill(x + PLATE_W - 6 - i * 4, y + PLATE_H - 4, x + PLATE_W - 4 - i * 4, y + PLATE_H - 2, c);
        }
        icon(g, font, x + 6, y + 5, age, a, revealed);
        int tx = x + 34;
        if (!revealed) {
            Component t = Component.translatable("hud.isekai_mahou.gambler.rolling");
            g.drawString(font, t, tx, y + 11, MahouUi.alpha(0xFFE8E2F4, a), true);
        } else {
            Component name = band == LuckBand.JACKPOT ? rainbow(Component.translatable("luck.isekai_mahou.band.jackpot").getString(), age)
                    : band.displayName().withStyle(ChatFormatting.BOLD);
            g.drawString(font, name, tx, y + 5, MahouUi.alpha(accent, a), true);
            int p = GbClientState.percent;
            String pct = (p > 0 ? "+" : p < 0 ? "−" : "±") + Math.abs(p) + "%";
            Component power = Component.translatable("hud.isekai_mahou.gambler.power", pct);
            g.drawString(font, power, tx, y + 17, MahouUi.alpha(p >= 0 ? 0xFFE8E2F4 : 0xFFB0A0B8, a), true);
        }
        g.pose().popPose();
        if (GbClientState.gambler) feverBar(g, font, x, y + PLATE_H + 3, a, age);
    }

    /** Ícone animado do tipo de rolagem (embaralhando ou revelado). */
    private static void icon(GuiGraphics g, Font font, int x, int y, float age, int a, boolean revealed) {
        LuckService.Kind kind = LuckService.Kind.values()[Mth.clamp(GbClientState.kind, 0, LuckService.Kind.values().length - 1)];
        int frame = (int) (age * 1.5F);
        int seed = revealed ? GbClientState.band.ordinal() * 7 + 3 : frame;
        switch (kind) {
            case DICE -> {
                die(g, x, y + 2, 1 + Math.floorMod(seed * 5 + 1, 6), a);
                die(g, x + 12, y + 8, 1 + Math.floorMod(seed * 3 + 4, 6), a);
            }
            case ROULETTE -> roulette(g, x + 10, y + 10, revealed ? 0F : age * 0.5F, a);
            case SLOT, DOMAIN -> {
                String[] sym = {"7", "♦", "♣", "★"};
                for (int i = 0; i < 3; i++) {
                    int bx = x + i * 8;
                    g.fill(bx, y + 3, bx + 7, y + 17, MahouUi.alpha(0xFFF4ECD6, a));
                    String s = revealed ? (GbClientState.band.good ? sym[0] : sym[(i + GbClientState.band.ordinal()) % 4])
                            : sym[Math.floorMod(frame + i * 2, 4)];
                    int col = s.equals("7") ? 0xFFD8303A : 0xFF26222C;
                    g.drawString(font, s, bx + 1, y + 6, MahouUi.alpha(col, a), false);
                }
            }
            case COIN -> {
                float spin = revealed ? 1F : Math.abs(Mth.cos(age * 0.6F));
                int half = Math.max(1, (int) (9 * spin));
                g.fill(x + 10 - half, y + 1, x + 10 + half, y + 19, MahouUi.alpha(0xFFE8B830, a));
                g.fill(x + 11 - half, y + 3, x + 9 + half, y + 17, MahouUi.alpha(0xFFFFD95A, a));
            }
            default -> {
                // cartas: o verso girando até revelar o naipe da faixa
                boolean back = !revealed && frame % 4 < 2;
                g.fill(x + 2, y, x + 18, y + 20, MahouUi.alpha(0xFFF4ECD6, a));
                if (back) {
                    g.fill(x + 4, y + 2, x + 16, y + 18, MahouUi.alpha(0xFF8A1E44, a));
                    g.fill(x + 8, y + 7, x + 12, y + 13, MahouUi.alpha(0xFFFFD86B, a));
                } else {
                    String[] suits = {"♠", "♥", "♦", "♣"};
                    String s = suits[Math.floorMod(seed, 4)];
                    int col = s.equals("♥") || s.equals("♦") ? 0xFFD8303A : 0xFF26222C;
                    g.drawString(font, revealed && GbClientState.band.good ? "A" : String.valueOf(2 + Math.floorMod(seed, 9)), x + 4, y + 2,
                            MahouUi.alpha(col, a), false);
                    g.drawString(font, s, x + 7, y + 10, MahouUi.alpha(col, a), false);
                }
            }
        }
    }

    private static void die(GuiGraphics g, int x, int y, int face, int a) {
        g.fill(x, y, x + 11, y + 11, MahouUi.alpha(0xFFF4ECD6, a));
        g.fill(x, y + 10, x + 11, y + 11, MahouUi.alpha(0xFFB4AE9E, a));
        int pip = MahouUi.alpha(face == 1 ? 0xFFD8303A : 0xFF26222C, a);
        int[][] spots = switch (face) {
            case 1 -> new int[][] {{5, 5}};
            case 2 -> new int[][] {{2, 2}, {8, 8}};
            case 3 -> new int[][] {{2, 2}, {5, 5}, {8, 8}};
            case 4 -> new int[][] {{2, 2}, {8, 2}, {2, 8}, {8, 8}};
            case 5 -> new int[][] {{2, 2}, {8, 2}, {5, 5}, {2, 8}, {8, 8}};
            default -> new int[][] {{2, 2}, {8, 2}, {2, 5}, {8, 5}, {2, 8}, {8, 8}};
        };
        for (int[] s : spots) g.fill(x + s[0], y + s[1], x + s[0] + 2, y + s[1] + 2, pip);
    }

    /** Roleta de 20 px: fatias vermelhas/pretas (e o zero verde) girando. */
    private static void roulette(GuiGraphics g, int cx, int cy, float angle, int a) {
        int r = 9;
        int n = RouletteWheelEntity.WHEEL.length;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int d2 = dx * dx + dy * dy;
                if (d2 > r * r) continue;
                int col;
                if (d2 <= 9) {
                    col = 0xFFC4962E;
                } else if (d2 >= (r - 1) * (r - 1)) {
                    col = 0xFF4A3410;
                } else {
                    double t = (Math.atan2(dy, dx) + angle) / (Math.PI * 2);
                    int i = Math.floorMod((int) Math.floor(t * n), n);
                    int num = RouletteWheelEntity.WHEEL[i];
                    col = num == 0 ? 0xFF2ECC71 : RouletteWheelEntity.isRed(num) ? 0xFFD8303A : 0xFF1A161E;
                }
                g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, MahouUi.alpha(col, a));
            }
        }
    }

    private static void feverBar(GuiGraphics g, Font font, int x, int y, int a, float age) {
        int fever = GbClientState.fever;
        float frac = fever / (float) LuckService.FEVER_MAX;
        boolean hot = fever >= 75;
        int col = hot && ((int) (age / 4)) % 2 == 0 ? 0xFFFF4AD2 : MahouUi.mix(0xFFFFD86B, 0xFFE0402A, frac);
        MahouUi.smallText(g, font, Component.translatable("hud.isekai_mahou.gambler.fever", fever), x + 2, y, 0.7F, MahouUi.alpha(0xFFE8E2F4, a), true);
        MahouUi.bar(g, x + 50, y + 1, PLATE_W - 52, 4, frac, MahouUi.alpha(col, a));
    }

    // ------------------------------------------------------------------ Jackpot
    private static void renderJackpot(GuiGraphics g, Font font, int w, int h, float pt) {
        float t = Minecraft.getInstance().player.tickCount + pt;
        // lâmpadas de cassino na borda da tela (dourado/rosa piscando em ondas)
        int step = 14;
        for (int x = 4; x < w - 4; x += step) {
            bulb(g, x, 2, t, x);
            bulb(g, x, h - 5, t, x + 7);
        }
        for (int y = 4 + step; y < h - 4 - step; y += step) {
            bulb(g, 2, y, t, y + 3);
            bulb(g, w - 5, y, t, y + 11);
        }
        float frac = GbClientState.jackpotTicks / (float) Math.max(1, GbClientState.jackpotTotal);
        Component title = rainbow("JACKPOT!", t);
        float scale = 1.6F + 0.08F * Mth.sin(t * 0.5F);
        int tw = font.width(title);
        int y = h / 2 - 100;
        g.pose().pushPose();
        g.pose().translate(w / 2F, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, title, -tw / 2, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        int bw = 120;
        MahouUi.bar(g, (w - bw) / 2, y + 16, bw, 3, frac, 0xFFFF4AD2);
        Component sub = Component.translatable("hud.isekai_mahou.gambler.jackpot", Mth.ceil(GbClientState.jackpotTicks / 20F));
        g.drawString(font, sub, (w - font.width(sub)) / 2, y + 22, 0xFFFFD86B, true);
    }

    private static void bulb(GuiGraphics g, int x, int y, float t, int phase) {
        boolean on = ((int) (t / 3) + phase / 7) % 2 == 0;
        int c = on ? (phase % 2 == 0 ? 0xE0FFD86B : 0xE0FF4AD2) : 0x60402030;
        g.fill(x, y, x + 3, y + 3, c);
    }

    private static Component rainbow(String text, float t) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            float hue = ((t * 0.04F) + i * 0.08F) % 1F;
            out.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(s -> s.withColor(Mth.hsvToRgb(hue, 0.7F, 1F)).withBold(true)));
        }
        return out;
    }

    // ------------------------------------------------------------------ Rodada da Casa
    private static void renderChallenge(GuiGraphics g, Font font) {
        HudLayout.Rect area = HudLayout.toasts();
        int x = area.x(), y = area.y() + HudLayout.reservedBefore(HudLayout.Region.TOASTS, CHALLENGE_ID);
        int bw = Math.min(150, Math.max(110, area.w()));
        int res = GbClientState.challengeResult;
        int accent = res == 1 ? 0xFF6FD88A : res == 2 ? 0xFFE06B5A : 0xFFFFD86B;
        MahouUi.roundRect(g, x, y, bw, 30, 0xD01A0E22, 0xD00B0612);
        MahouUi.roundOutline(g, x, y, bw, 30, accent);
        HouseRanks.Rank r = HouseRanks.rank(GbClientState.challengeRank);
        Component title = r == null ? Component.translatable("hud.isekai_mahou.gambler.challenge")
                : Component.translatable("hud.isekai_mahou.gambler.challenge_rank", r.title());
        g.drawString(font, MahouUi.fit(font, title, bw - 8), x + 4, y + 3, accent, true);
        Component line = res == 1 ? Component.translatable("hud.isekai_mahou.gambler.challenge_win")
                : res == 2 ? Component.translatable("hud.isekai_mahou.gambler.challenge_lose")
                : Component.translatable("hud.isekai_mahou.gambler.challenge_line", GbClientState.challengeKills, GbClientState.challengeTarget,
                        Mth.ceil(GbClientState.challengeTicks / 20F));
        g.drawString(font, MahouUi.fit(font, line, bw - 8), x + 4, y + 13, MahouUi.TEXT, false);
        MahouUi.bar(g, x + 4, y + 24, bw - 8, 3, GbClientState.challengeKills / (float) Math.max(1, GbClientState.challengeTarget), accent);
        HudLayout.drawn(HudLayout.Region.TOASTS, new HudLayout.Rect(x, y, bw, 30));
    }

    private RollHud() {}
}
