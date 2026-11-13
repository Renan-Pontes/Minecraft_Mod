package com.renanpontes.isekaimahou.client.social;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.social.ClientSocialView;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.Member;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * HUD de grupo: um cartão por membro (rosto, nome, nível, barra de vida e do recurso da classe) na coluna da esquerda,
 * logo abaixo do cartão do jogador. Quem está em outra dimensão/longe fica apagado; offline, cinza. Se a Pilha estiver
 * na tela, o HUD passa para o lado dela. {@link #enabled} = false desliga (o módulo ui_hud pode desenhar o próprio).
 */
public class PartyHud implements LayeredDraw.Layer {
    /** Liga/desliga o HUD de grupo do núcleo. */
    public static volatile boolean enabled = true;
    static final int ROW_H = 19, W = 104;

    /** Vida mostrada (suavizada) por membro. */
    private static final Map<UUID, Float> SHOWN_HP = new HashMap<>();
    private static final Map<UUID, Float> SHOWN_RES = new HashMap<>();

    static void tick() {
        List<Member> members = ClientSocialView.partyMembers();
        if (members.isEmpty()) {
            SHOWN_HP.clear();
            SHOWN_RES.clear();
            return;
        }
        for (Member m : members) {
            float hp = frac(m.hp(), m.maxHp()), res = frac(m.res(), m.maxRes());
            SHOWN_HP.merge(m.id(), hp, (old, now) -> old + (now - old) * 0.35F);
            SHOWN_RES.merge(m.id(), res, (old, now) -> old + (now - old) * 0.35F);
        }
        SHOWN_HP.keySet().removeIf(id -> members.stream().noneMatch(m -> m.id().equals(id)));
        SHOWN_RES.keySet().removeIf(id -> members.stream().noneMatch(m -> m.id().equals(id)));
    }

    static void clear() {
        SHOWN_HP.clear();
        SHOWN_RES.clear();
    }

    private static float frac(float v, float max) {
        return max <= 0 ? 0 : Mth.clamp(v / max, 0F, 1F);
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        List<Member> members = ClientSocialView.partyMembers();
        if (members.isEmpty()) return;
        Font font = mc.font;
        UUID me = mc.player.getUUID();
        UUID leader = ClientSocialView.leader();
        int shown = 0;
        for (Member m : members) if (!m.id().equals(me)) shown++;
        if (shown == 0) return;

        int h = 12 + shown * (ROW_H + 2);
        int x = HudLayout.leftEdge();
        int y = HudLayout.CARD_BOTTOM + HudLayout.MARGIN;
        HudLayout.Rect stack = HudLayout.lastDrawn(HudLayout.Region.STACK);
        if (stack != null && stack.intersects(x, y, W, h)) x = stack.right() + 6;
        x = Math.min(x, g.guiWidth() / 2 - W - 100);
        x = Math.max(2, x);

        // Cabeçalho
        Component head = Component.translatable("hud.isekai_mahou.party.header", members.size());
        MahouUi.roundRect(g, x, y, W, 11, 0xB0201830, 0xB0100C18);
        MahouUi.diamond(g, x + 5, y + 5, 2, 0xFF7CFF8A);
        MahouUi.smallText(g, font, head, x + 10, y + 2.5F, 0.75F, 0xFF9FE8A0, false);
        int yy = y + 13;
        String myDim = mc.player.level().dimension().location().toString();
        for (Member m : members) {
            if (m.id().equals(me)) continue;
            drawMember(g, font, mc, m, x, yy, m.id().equals(leader), myDim);
            yy += ROW_H + 2;
        }
    }

    private void drawMember(GuiGraphics g, Font font, Minecraft mc, Member m, int x, int y, boolean leader, String myDim) {
        boolean sameDim = m.online() && m.dim().equals(myDim);
        Player p = sameDim && mc.level != null ? mc.level.getPlayerByUUID(m.id()) : null;
        boolean far = sameDim && p == null;
        int alpha = !m.online() ? 0x70 : !sameDim || far ? 0xA8 : 0xE0;
        MahouUi.roundRect(g, x, y, W, ROW_H, MahouUi.alpha(0xFF181226, alpha), MahouUi.alpha(0xFF0B0912, alpha));
        MagicPath path = MagicPath.byId(m.path());
        int accent = path.isPlayable() ? path.argb() : 0xFFB98CFF;
        g.fill(x, y + 2, x + 2, y + ROW_H - 2, MahouUi.alpha(accent, alpha));

        // Rosto
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(m.id());
        if (info != null) {
            PlayerFaceRenderer.draw(g, info.getSkin(), x + 4, y + 2, 8);
        } else {
            g.fill(x + 4, y + 2, x + 12, y + 10, 0xFF3A3150);
        }
        if (leader) MahouUi.diamond(g, x + 8, y + 13, 2, MahouUi.GOLD);

        // Nome + nível
        int nameColor = !m.online() ? 0xFF6E6880 : leader ? MahouUi.GOLD_PALE : MahouUi.TEXT;
        String lvl = m.online() && m.level() > 0 ? String.valueOf(m.level()) : "";
        int lvlW = lvl.isEmpty() ? 0 : Math.round(font.width(lvl) * 0.75F) + 2;
        // Release 1.0 — marcador dos amigos: seta para onde o membro está + distância (vale para quem está fora da
        // visão: a posição vem do servidor). Só de longe, para o cartão continuar limpo.
        Vec3 where = sameDim && mc.player != null ? PartyMarkers.where(mc, m.id(), 1F) : null;
        int dist = where == null ? 0 : (int) Math.sqrt(where.distanceToSqr(mc.player.position()));
        String distText = dist > 12 ? dist + "m" : "";
        int dirW = distText.isEmpty() ? 0 : Math.round(font.width(distText) * 0.6F) + 10;
        int nameMax = Math.round((W - 18 - lvlW - dirW) / 0.75F);
        g.pose().pushPose();
        g.pose().translate(x + 15, y + 2, 0);
        g.pose().scale(0.75F, 0.75F, 1F);
        g.drawString(font, MahouUi.fit(font, Component.literal(m.name()), nameMax), 0, 0, nameColor, false);
        g.pose().popPose();
        if (!lvl.isEmpty()) MahouUi.smallText(g, font, Component.literal(lvl), x + W - lvlW, y + 2, 0.75F, 0xFFD9A8FF, false);
        if (dirW > 0) {
            int dx = x + W - lvlW - dirW;
            PartyMarkers.arrow(g, dx + 3, y + 5, PartyMarkers.bearing(mc, where), MahouUi.alpha(0xFF7CFF8A, alpha));
            MahouUi.smallText(g, font, Component.literal(distText), dx + 8, y + 3, 0.6F, 0xFFB8E8C0, false);
        }

        if (!m.online()) {
            MahouUi.smallText(g, font, Component.translatable("hud.isekai_mahou.party.offline"), x + 15, y + 10, 0.65F, 0xFF6E6880, false);
            return;
        }
        // Barras: vida (vermelha; amarela com absorção não é enviada) e recurso da classe
        float hp = SHOWN_HP.getOrDefault(m.id(), frac(m.hp(), m.maxHp()));
        float res = SHOWN_RES.getOrDefault(m.id(), frac(m.res(), m.maxRes()));
        int bx = x + 15, bw = W - 18;
        int hpColor = hp > 0.5F ? 0xFF4FD86A : hp > 0.25F ? 0xFFE8C84A : 0xFFE84A4A;
        MahouUi.bar(g, bx, y + 10, bw, 3, hp, MahouUi.alpha(hpColor, alpha));
        MahouUi.bar(g, bx, y + 14, bw, 2, res, MahouUi.alpha(m.resArgb() | 0xFF000000, alpha));
        if (hp <= 0.25F && m.hp() > 0) {
            // pisca quando está quase morrendo
            float blink = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 120.0);
            g.renderOutline(x, y, W, ROW_H, MahouUi.alpha(0xFFFF4A4A, Math.round(0x90 * blink)));
        }
        if (!sameDim) {
            MahouUi.smallText(g, font, Component.translatable("hud.isekai_mahou.party.elsewhere"), x + W - 30, y + 10, 0.5F, 0xFFB8B0C8, false);
        }
    }
}
