package com.renanpontes.isekaimahou.feature.ui_hud.client;

import com.renanpontes.isekaimahou.client.hud.HudLayout;

import net.minecraft.Util;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v1.0 — VIDA como barra numérica (v1.1.2: substitui os corações do vanilla, escondidos no UiHudClient). Com a inflação de números a vida base é 40 e chega a
 * 140 no nível 150 (200: 160): os corações viram várias fileiras empilhadas e fica difícil ler quanto falta. A barra fica
 * no canto de baixo à esquerda, alinhada com a barra de itens (à esquerda da mão secundária / indicador de ataque):
 * <ul>
 *   <li>coração + "Vida" + {@code 34 / 40} (absorção em dourado "+8");</li>
 *   <li>barra com "fantasma" do dano recente, lampejo verde ao curar, absorção como escudo dourado no fim da barra,
 *   marcas a cada 20 de vida (uma fileira de corações), brilho correndo com Regeneração;</li>
 *   <li>cor pelo estado (como os corações): veneno verde, Wither cinza-escuro, congelando azul-gelo;</li>
 *   <li>≤ 25%: moldura vermelha pulsando.</li>
 * </ul>
 * Só no modo em que o vanilla mostra corações (sobrevivência/aventura). Nunca por cima do minimapa ({@link HudLayout}).
 */
final class HudHealthBar {
    static final int H = 19;
    private static final int MAX_W = 132;

    private static float shownHp = -1F;
    private static float ghostHp = -1F;
    private static float lastTarget = -1F;
    private static long ghostHoldUntil;
    private static long healUntil;
    private static long lastFrame;

    private HudHealthBar() {}

    static void clear() {
        shownHp = ghostHp = lastTarget = -1F;
        ghostHoldUntil = healUntil = lastFrame = 0L;
    }

    static void render(GuiGraphics g, Minecraft mc) {
        Player p = mc.player;
        if (p == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
        Font font = mc.font;
        int gw = HudScale.w(g), gh = HudScale.h(g);
        int center = gw / 2;

        // Borda direita: à esquerda da hotbar e do que o vanilla desenha do lado esquerdo dela
        HumanoidArm off = p.getMainArm().getOpposite();
        // (HudScale.v: a barra de itens é do vanilla, em pixels reais; aqui as contas são na tela virtual do HUD)
        int limit = center - HudScale.v(91) - 4;
        if (off == HumanoidArm.LEFT && !p.getOffhandItem().isEmpty()) limit = center - HudScale.v(91 + 29) - 4;
        else if (off == HumanoidArm.RIGHT && mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) limit = center - HudScale.v(91 + 22) - 4;
        int x = 4;
        int w = Math.min(MAX_W, limit - x);
        if (w < 34) return;
        int y = gh - H - 2;
        // Minimapa no canto de baixo à esquerda: a barra sobe para cima dele
        HudLayout.Rect m = HudScale.minimap();
        if (m.intersects(x, y, w, H)) y = m.y() - HudLayout.MARGIN - H;

        float max = Math.max(1F, p.getMaxHealth());
        float hp = Math.max(0F, Math.min(p.getHealth(), max));
        float abs = Math.max(0F, p.getAbsorptionAmount());
        update(hp);
        long now = Util.getMillis();

        // Estado (a mesma lógica dos corações do vanilla)
        int fillTop = 0xFFFF7A7A, fillBottom = 0xFFB81E2E, heart = 0xFFFF5A5A;
        if (p.hasEffect(MobEffects.POISON)) {
            fillTop = 0xFFB8F06A; fillBottom = 0xFF4A8A1E; heart = 0xFF8AD84A;
        } else if (p.hasEffect(MobEffects.WITHER)) {
            fillTop = 0xFF8A8A92; fillBottom = 0xFF2A2A30; heart = 0xFF6A6A74;
        } else if (p.isFullyFrozen()) {
            fillTop = 0xFFCFF2FF; fillBottom = 0xFF5AA8D8; heart = 0xFF9AD8FF;
        }
        float frac = hp / max;
        boolean low = frac <= 0.25F && hp > 0F;
        float pulse = HudDraw.pulse(600);

        // Painel
        int frame = low ? HudDraw.lerp(pulse, 0xFF8A1E26, 0xFFFF5A5A) : 0xFF4A2A34;
        if (low) HudDraw.glow(g, x, y, w, H, 0xFFFF3030, 0.35F + 0.65F * pulse, 3);
        g.fillGradient(x, y, x + w, y + H, 0xC00C0A12, 0xA0080610);
        g.renderOutline(x, y, w, H, frame);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x18FFFFFF);

        // Linha de cima: coração + "Vida" e os números à direita
        boolean beat = low && HudDraw.blink(300);
        HudDraw.sprite(g, HudDraw.HEART, x + 3, y + 3, beat ? 0xFFFFFFFF : heart);
        String cur = HudDraw.num(hp);
        Component maxText = Component.literal("/" + HudDraw.num(max));
        Component absText = abs > 0.05F ? Component.literal("+" + HudDraw.num(abs)) : null;
        float maxW = font.width(maxText) * 0.75F;
        float absW = absText != null ? font.width(absText) * 0.75F + 2F : 0F;
        int curW = font.width(cur);
        float right = x + w - 3;
        float numsW = curW + absW + maxW + 1;
        float nx = right - numsW;
        int curColor = low ? HudDraw.lerp(pulse, 0xFFFFB0B0, 0xFFFFFFFF) : 0xFFFFFFFF;
        g.drawString(font, cur, Math.round(nx), y + 2, curColor, true);
        float ax = nx + curW + 1;
        if (absText != null) {
            HudDraw.scaledText(g, font, absText, ax, y + 3.5F, 0.75F, 0xFFFFD84F, true);
            ax += absW;
        }
        HudDraw.scaledText(g, font, maxText, ax, y + 3.5F, 0.75F, 0xFFD8A8B0, true);
        Component label = Component.translatable("hud.isekai_mahou.ui_hud.health");
        float lw = font.width(label) * 0.75F;
        if (x + 12 + lw + 3 <= nx) HudDraw.scaledText(g, font, label, x + 12, y + 3.5F, 0.75F, 0xFFFFB4B4, true);

        // Barra
        int bx = x + 3, by = y + 12, bw = w - 6, bh = 4;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF05040A);
        g.fill(bx, by, bx + bw, by + bh, 0xFF2A0E14);
        float shown = Math.max(0F, Math.min(shownHp, max));
        float ghost = Math.max(shown, Math.min(ghostHp, max));
        int fw = Math.round(bw * shown / max);
        int gwid = Math.round(bw * ghost / max);
        // Fantasma: o dano que acabou de entrar
        if (gwid > fw) g.fill(bx + fw, by, bx + gwid, by + bh, 0xD8FFE6C8);
        if (fw > 0) {
            g.fillGradient(bx, by, bx + fw, by + bh, fillTop, fillBottom);
            g.fill(bx, by, bx + fw, by + 1, HudDraw.fade(0xFFFFFFFF, 0.3F));
            // Regeneração: um brilho percorre a barra
            if (p.hasEffect(MobEffects.REGENERATION)) {
                float t = (now % 1200) / 1200F;
                int sx = bx + Math.round(t * (fw + 10)) - 8;
                int s0 = Math.max(bx, sx), s1 = Math.min(bx + fw, sx + 6);
                if (s1 > s0) g.fill(s0, by, s1, by + bh, 0x70FFFFFF);
            }
        }
        // Cura: lampejo verde que some
        if (now < healUntil && fw > 0) {
            float a = (healUntil - now) / 450F;
            g.fill(bx, by, bx + fw, by + bh, HudDraw.fade(0xFF7CFF8A, 0.55F * a));
        }
        // Absorção: escudo dourado colado no fim da vida (corta no fim da barra)
        if (abs > 0.05F) {
            int aw = Math.max(1, Math.round(bw * Math.min(1F, abs / max)));
            int a0 = Math.min(bx + bw - aw, bx + fw);
            g.fillGradient(a0, by, a0 + aw, by + bh, 0xFFFFE680, 0xFFD69A1E);
            g.fill(a0, by - 1, a0 + aw, by, 0xFFFFF4C8);
        }
        // Marcas a cada 20 de vida (uma fileira de corações) — só quando cabem
        if (max <= 400F && bw / (max / 20F) >= 6F) {
            for (float v = 20F; v < max; v += 20F) {
                int mx = bx + Math.round(bw * v / max);
                g.fill(mx, by, mx + 1, by + bh, 0x70000000);
            }
        }
    }

    /** Vida mostrada suavizada (por quadro, tempo real) + fantasma do dano e lampejo de cura. */
    private static void update(float target) {
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 0F : Math.min(0.25F, (now - lastFrame) / 1000F);
        lastFrame = now;
        if (shownHp < 0F) {
            shownHp = ghostHp = lastTarget = target;
            return;
        }
        if (target < lastTarget - 0.05F) ghostHoldUntil = now + 500;
        else if (target > lastTarget + 0.5F) healUntil = now + 450;
        lastTarget = target;
        shownHp += (target - shownHp) * (1F - (float) Math.exp(-dt * 12F));
        if (Math.abs(target - shownHp) < 0.02F) shownHp = target;
        if (ghostHp < shownHp) ghostHp = shownHp;
        else if (now > ghostHoldUntil) ghostHp += (shownHp - ghostHp) * (1F - (float) Math.exp(-dt * 4F));
    }
}
