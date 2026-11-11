package com.renanpontes.isekaimahou.client.social;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.client.reputation.FactionArt;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.social.ClientSocialView;
import com.renanpontes.isekaimahou.core.social.Requests;
import com.renanpontes.isekaimahou.core.social.SocialPayloads;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.Member;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.Req;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.State;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Grupo" do menu B: membros do grupo (vida, recurso, líder; o líder promove/expulsa), sair/desfazer, chat de
 * grupo; pedidos pendentes (aceitar/recusar); cidade, guerras, facções aliadas e duelos.
 */
public class SocialTab implements MenuTab {
    private static final int ROW_H = 26;
    private static final int GREEN = 0xFF7CFF8A, RED = 0xFFFF6B6B, MUTED = 0xFF8A82A0, EDGE = 0xFF6E5A9A;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    @Nullable private List<Component> tooltip;
    private int x, y, w, h, mouseX, mouseY;
    private int leftW;
    /** Faixa visível da lista rolável: chips fora dela não recebem clique. */
    private int clipTop = Integer.MIN_VALUE, clipBottom = Integer.MAX_VALUE;
    private float scroll;
    private int content;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.leftW = Mth.clamp(Math.round(w * 0.56F), 180, w - 150);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(mx, my)) {
                hits.get(i).action().run();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < x || mx >= x + leftW || my < y || my >= y + h) return false;
        scroll = Mth.clamp(scroll - (float) sy * 16F, 0F, Math.max(0, content - (h - 22)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null) return;
        Font font = mc.font;
        hits.clear();
        tooltip = null;
        mouseX = mx;
        mouseY = my;
        renderParty(g, font, mc, me);
        renderSide(g, font, me);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    // =====================================================================================
    //  Esquerda: grupo
    // =====================================================================================
    private void renderParty(GuiGraphics g, Font font, Minecraft mc, LocalPlayer me) {
        panel(g, x, y, leftW, h, GREEN);
        List<Member> members = ClientSocialView.partyMembers();
        UUID leader = ClientSocialView.leader();
        boolean iLead = me.getUUID().equals(leader);
        int cx = x + 8, cw = leftW - 16;
        // Cabeçalho + botões
        Component title = members.isEmpty() ? Component.translatable("screen.isekai_mahou.party.title_none")
                : Component.translatable("screen.isekai_mahou.party.title", members.size(), Party.MAX_MEMBERS);
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), cx, y + 6, MahouUi.GOLD, true);
        if (members.isEmpty()) {
            int yy = y + 22;
            for (FormattedCharSequence line : font.split(Component.translatable("screen.isekai_mahou.party.how"), cw)) {
                g.drawString(font, line, cx, yy, MahouUi.TEXT_DIM, false);
                yy += 10;
            }
            return;
        }
        int bx = x + leftW - 8;
        bx = chip(g, font, bx, y + 4, Component.translatable("screen.isekai_mahou.party.leave"), RED,
                () -> op(SocialPayloads.OP_LEAVE, me.getUUID()), Component.translatable("screen.isekai_mahou.party.leave_tip"));
        if (iLead) {
            bx = chip(g, font, bx - 3, y + 4, Component.translatable("screen.isekai_mahou.party.disband"), RED,
                    () -> op(SocialPayloads.OP_DISBAND, me.getUUID()), Component.translatable("screen.isekai_mahou.party.disband_tip"));
        }
        boolean chatOn = ClientSocialView.state().partyChat();
        chip(g, font, bx - 3, y + 4, Component.translatable(chatOn ? "screen.isekai_mahou.party.chat_on" : "screen.isekai_mahou.party.chat_off"),
                chatOn ? GREEN : MUTED, () -> op(SocialPayloads.OP_CHAT_TOGGLE, me.getUUID()),
                Component.translatable("screen.isekai_mahou.party.chat_tip"));

        int top = y + 20, bottom = y + h - 3;
        content = members.size() * (ROW_H + 3);
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - (bottom - top)));
        g.enableScissor(x + 1, top, x + leftW - 1, bottom);
        clipTop = top;
        clipBottom = bottom;
        int yy = top - Math.round(scroll);
        String myDim = me.level().dimension().location().toString();
        for (Member m : members) {
            if (yy + ROW_H > top - ROW_H && yy < bottom) row(g, font, mc, me, m, m.id().equals(leader), iLead, cx, yy, cw, myDim);
            yy += ROW_H + 3;
        }
        g.disableScissor();
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
    }

    private void row(GuiGraphics g, Font font, Minecraft mc, LocalPlayer me, Member m, boolean isLeader, boolean iLead, int rx, int ry, int rw,
                     String myDim) {
        boolean self = m.id().equals(me.getUUID());
        MagicPath path = MagicPath.byId(m.path());
        int accent = path.isPlayable() ? path.argb() : 0xFFB98CFF;
        MahouUi.roundRect(g, rx, ry, rw, ROW_H, self ? 0x50305A3A : 0x50000000, 0x30000000);
        g.fill(rx, ry + 3, rx + 2, ry + ROW_H - 3, m.online() ? accent : 0xFF4A4458);
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(m.id());
        if (info != null) PlayerFaceRenderer.draw(g, info.getSkin(), rx + 5, ry + 5, 16);
        else g.fill(rx + 5, ry + 5, rx + 21, ry + 21, 0xFF3A3150);
        int tx = rx + 26;
        Component name = Component.literal(m.name()).withStyle(ChatFormatting.BOLD);
        g.drawString(font, MahouUi.fit(font, name, rw - 120), tx, ry + 3, !m.online() ? 0xFF6E6880 : isLeader ? MahouUi.GOLD : MahouUi.TEXT, true);
        Component sub;
        if (!m.online()) sub = Component.translatable("screen.isekai_mahou.party.offline");
        else if (!m.dim().equals(myDim)) sub = Component.translatable("screen.isekai_mahou.party.other_dim", path.displayName(), m.level());
        else sub = Component.translatable("screen.isekai_mahou.party.class_level", path.displayName(), m.level());
        MahouUi.smallText(g, font, sub, tx, ry + 13, 0.7F, MahouUi.TEXT_DIM, false);
        if (isLeader) MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.party.leader"), tx, ry + 19.5F, 0.6F, MahouUi.GOLD, false);
        if (m.online()) {
            int bw = 54, bx = rx + rw - bw - 4;
            MahouUi.bar(g, bx, ry + 6, bw, 3, m.maxHp() <= 0 ? 0 : m.hp() / m.maxHp(), 0xFF4FD86A);
            MahouUi.bar(g, bx, ry + 11, bw, 2, m.maxRes() <= 0 ? 0 : m.res() / m.maxRes(), m.resArgb() | 0xFF000000);
            MahouUi.smallText(g, font, Component.literal(Math.round(m.hp()) + "/" + Math.round(m.maxHp())), bx, ry + 15, 0.55F, MUTED, false);
        }
        if (self) return;
        // Ações por membro
        int cx = rx + rw - 62;
        if (m.online()) {
            cx = chip(g, font, cx, ry + ROW_H - 11, Component.translatable("screen.isekai_mahou.party.profile"), 0xFF8FD3FF,
                    () -> op(SocialPayloads.OP_PROFILE, m.id()), null);
        }
        if (iLead) {
            cx = chip(g, font, cx - 2, ry + ROW_H - 11, Component.translatable("screen.isekai_mahou.party.kick"), RED,
                    () -> op(SocialPayloads.OP_KICK, m.id()), Component.translatable("screen.isekai_mahou.party.kick_tip", m.name()));
            chip(g, font, cx - 2, ry + ROW_H - 11, Component.translatable("screen.isekai_mahou.party.promote"), MahouUi.GOLD,
                    () -> op(SocialPayloads.OP_PROMOTE, m.id()), Component.translatable("screen.isekai_mahou.party.promote_tip", m.name()));
        }
    }

    // =====================================================================================
    //  Direita: pedidos, cidade, guerras, aliados, duelos
    // =====================================================================================
    private void renderSide(GuiGraphics g, Font font, LocalPlayer me) {
        int sx = x + leftW + 6, sw = w - leftW - 6;
        panel(g, sx, y, sw, h, MahouUi.GOLD);
        State s = ClientSocialView.state();
        int cx = sx + 8, cw = sw - 16;
        int yy = y + 6;

        // Pedidos
        yy = section(g, font, Component.translatable("screen.isekai_mahou.party.requests"), cx, yy, cw);
        if (s.requests().isEmpty()) {
            MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.party.no_requests"), cx, yy, 0.75F, MUTED, false);
            yy += 10;
        }
        for (Req r : s.requests()) {
            Requests.Kind kind = Requests.Kind.byIndex(r.kind());
            Component line = Component.translatable("screen.isekai_mahou.party.request." + kind.id, r.name());
            g.drawString(font, MahouUi.fit(font, line, cw - 4), cx, yy, MahouUi.TEXT, false);
            yy += 10;
            int bx = chip(g, font, cx + cw, yy, Component.translatable("social.isekai_mahou.button.decline"), RED,
                    () -> PacketDistributor.sendToServer(new SocialPayloads.Reply(r.from(), r.kind(), false)), null);
            chip(g, font, bx - 3, yy, Component.translatable("social.isekai_mahou.button.accept"), GREEN,
                    () -> PacketDistributor.sendToServer(new SocialPayloads.Reply(r.from(), r.kind(), true)), null);
            MahouUi.smallText(g, font, Component.literal((r.ticksLeft() + 19) / 20 + "s"), cx, yy + 2, 0.7F, MUTED, false);
            yy += 13;
            if (yy > y + h - 60) break;
        }
        yy += 3;

        // Cidade e guerras
        yy = section(g, font, Component.translatable("screen.isekai_mahou.party.city"), cx, yy, cw);
        Component city = s.allegianceName().isPresent() ? s.allegianceName().get()
                : Component.translatable("screen.isekai_mahou.party.no_city").withColor(MUTED & 0xFFFFFF);
        g.drawString(font, MahouUi.fit(font, city, cw), cx, yy, MahouUi.TEXT, false);
        yy += 11;
        for (Component war : s.wars()) {
            if (yy > y + h - 40) break;
            MahouUi.smallText(g, font, war, cx + 4, yy, 0.75F, 0xFFFF8A7A, false);
            yy += 9;
        }
        if (!s.wars().isEmpty()) {
            MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.party.war_hint"), cx, yy, 0.65F, MUTED, false);
            yy += 9;
        }
        yy += 3;

        // Facções aliadas
        yy = section(g, font, Component.translatable("screen.isekai_mahou.party.allies", Reputation.allied(me).size(), Reputation.MAX_ALLIED), cx, yy, cw);
        List<String> allied = Reputation.allied(me);
        if (allied.isEmpty()) {
            MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.party.no_allies"), cx, yy, 0.7F, MUTED, false);
            yy += 10;
        }
        for (String fid : allied) {
            Reputation.Faction f = Reputation.faction(fid);
            if (f == null) continue;
            FactionArt.emblem(g, f, cx, yy, 10);
            g.drawString(font, MahouUi.fit(font, f.coloredName(), cw - 14), cx + 13, yy + 1, f.argb(), false);
            yy += 12;
        }
        yy += 3;

        // Duelos
        if (yy + 20 < y + h) {
            yy = section(g, font, Component.translatable("screen.isekai_mahou.party.duels"), cx, yy, cw);
            MagicData d = ClientMagic.data();
            g.drawString(font, Component.translatable("screen.isekai_mahou.party.duel_record", d.counter("duel.win"), d.counter("duel.loss")),
                    cx, yy, MahouUi.TEXT_DIM, false);
        }
    }

    // =====================================================================================
    //  Peças
    // =====================================================================================
    private static void op(int op, UUID target) {
        PacketDistributor.sendToServer(new SocialPayloads.PartyOp(op, target));
    }

    /** Botãozinho alinhado pela DIREITA em {@code right}. Devolve o x da esquerda (para o próximo chip). */
    private int chip(GuiGraphics g, Font font, int right, int cy, Component label, int color, Runnable action, @Nullable Component tip) {
        int tw = Math.round(font.width(label) * 0.75F);
        int cw = tw + 8, ch = 10;
        int cx = right - cw;
        boolean visible = cy >= clipTop && cy + ch <= clipBottom;
        boolean hov = visible && mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
        MahouUi.roundRect(g, cx, cy, cw, ch, MahouUi.alpha(color, hov ? 0x70 : 0x38), MahouUi.alpha(color, hov ? 0x40 : 0x18));
        MahouUi.roundOutline(g, cx, cy, cw, ch, MahouUi.alpha(color, hov ? 0xFF : 0xA0));
        MahouUi.smallText(g, font, label, cx + 4, cy + 2, 0.75F, hov ? 0xFFFFFFFF : MahouUi.lighten(color, 0.3F), false);
        if (visible) hits.add(new Hit(cx, cy, cw, ch, action));
        if (hov && tip != null) tooltip = List.of(tip);
        return cx;
    }

    private static int section(GuiGraphics g, Font font, Component title, int cx, int yy, int cw) {
        MahouUi.diamond(g, cx + 2, yy + 4, 2, MahouUi.GOLD);
        Component bold = title.copy().withStyle(ChatFormatting.BOLD);
        g.drawString(font, bold, cx + 8, yy, MahouUi.GOLD, true);
        int lx = cx + 8 + font.width(bold) + 5;
        if (lx < cx + cw) g.fillGradient(lx, yy + 4, cx + cw, yy + 5, MahouUi.alpha(MahouUi.GOLD_DIM, 0xC0), MahouUi.alpha(MahouUi.GOLD_DIM, 0x20));
        return yy + 12;
    }

    private static void panel(GuiGraphics g, int px, int py, int pw, int ph, int accent) {
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xE0141026, 0xE00A0814);
        g.renderOutline(px, py, pw, ph, MahouUi.alpha(EDGE, 0x8C));
        MahouUi.brackets(g, px - 1, py - 1, pw + 2, ph + 2, 5, MahouUi.lighten(accent, 0.2F));
    }
}
