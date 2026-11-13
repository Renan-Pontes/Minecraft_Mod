package com.renanpontes.isekaimahou.feature.home_realm.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.feature.home_realm.HomeNetwork;
import com.renanpontes.isekaimahou.feature.home_realm.HomeNetwork.Action;
import com.renanpontes.isekaimahou.feature.home_realm.HomeNetwork.Info;
import com.renanpontes.isekaimahou.feature.home_realm.HomeNetwork.Person;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Seção "Lar" do menu B. À esquerda: o estado (tem lar? está lá?), o botão de ir/voltar — com a contagem dos 2 min fora
 * de combate quando falta — e as opções do dono. À direita: uma lista só, rolável, com os convidados, quem dá para
 * convidar agora e os lares dos amigos que convidaram você. Tudo desenhado à mão, poucos elementos, nada piscando.
 */
public class HomeTab implements MenuTab {
    private static final int SKY = 0xFF8FD8FF, SKY_DIM = 0x708FD8FF, TEXT = 0xFFE9F2F8, MUTED = 0xFFA4B6C4, FAINT = 0xFF6C7C8A;
    private static final int CARD = 0xFF16202A, CARD_HOVER = 0xFF1F2D3A, GOLD = 0xFFFFD86B, RED = 0xFFE8735E, GREEN = 0xFF8FE3B0;
    private static final int ROW_H = 20;

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private int x, y, w, h;
    private int scroll, maxScroll;
    private long lastRequest;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        request();
    }

    private void request() {
        long now = Util.getMillis();
        if (now - lastRequest < 400) return;
        lastRequest = now;
        PacketDistributor.sendToServer(new Action(Action.INFO));
    }

    @Override
    public void tick() {
        // Estado fresco com a aba aberta (alguém aceitou convite, entrou no jogo...): a cada 4 s basta
        if (Util.getMillis() - lastRequest > 4000) request();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        Font font = Minecraft.getInstance().font;
        Info info = HomeClientState.info;
        int leftW = Math.max(110, Math.min(180, w * 9 / 20));
        renderLeft(g, font, info, x, y, leftW, mx, my);
        g.fill(x + leftW + 5, y, x + leftW + 6, y + h, SKY_DIM);
        renderPeople(g, font, info, x + leftW + 12, y, w - leftW - 12, mx, my);
    }

    // ------------------------------------------------------------------------------------------------ coluna esquerda
    private void renderLeft(GuiGraphics g, Font font, Info info, int lx, int ly, int lw, int mx, int my) {
        g.drawString(font, Component.translatable("screen.isekai_mahou.home_realm.title").withStyle(ChatFormatting.BOLD), lx, ly, SKY, true);
        int cy = ly + 14;
        Component where;
        if (info.inRealm()) {
            where = info.hostName().isEmpty() ? Component.translatable("screen.isekai_mahou.home_realm.at_home")
                    : Component.translatable("screen.isekai_mahou.home_realm.at_friend", info.hostName());
        } else {
            where = Component.translatable(info.owned() ? "screen.isekai_mahou.home_realm.owned" : "screen.isekai_mahou.home_realm.not_owned");
        }
        cy += wrapped(g, font, where, lx, cy, lw, info.inRealm() ? GREEN : info.owned() ? TEXT : MUTED, 2) + 4;

        if (info.inRealm()) {
            button(g, font, lx, cy, lw, 18, Component.translatable("screen.isekai_mahou.home_realm.leave"), mx, my, GOLD,
                    () -> send(new Action(Action.LEAVE), true));
            cy += 22;
            if (info.owned() && !info.hostName().isEmpty()) {
                button(g, font, lx, cy, lw, 18, Component.translatable("screen.isekai_mahou.home_realm.go_own"), mx, my, SKY,
                        () -> send(new Action(Action.ENTER), true));
                cy += 22;
            }
        } else if (info.owned()) {
            int wait = HomeClientState.combatSecondsLeft();
            if (wait > 0) {
                disabled(g, font, lx, cy, lw, 18, Component.translatable("screen.isekai_mahou.home_realm.combat", clock(wait)));
            } else {
                button(g, font, lx, cy, lw, 18, Component.translatable("screen.isekai_mahou.home_realm.enter"), mx, my, SKY,
                        () -> send(new Action(Action.ENTER), true));
            }
            cy += 22;
        }

        if (info.owned()) {
            cy += 2;
            Component toggle = Component.translatable(info.guestsBuild() ? "screen.isekai_mahou.home_realm.guests_build_on"
                    : "screen.isekai_mahou.home_realm.guests_build_off");
            button(g, font, lx, cy, lw, 16, toggle, mx, my, info.guestsBuild() ? GREEN : FAINT, () -> send(new Action(Action.GUESTS_BUILD), false));
            cy += 20;
            if (info.inRealm() && info.hostName().isEmpty()) {
                button(g, font, lx, cy, lw, 16, Component.translatable("screen.isekai_mahou.home_realm.set_arrival"), mx, my, FAINT,
                        () -> send(new Action(Action.SET_ARRIVAL), true));
                cy += 20;
            }
        }
        cy += 4;
        int room = (ly + h - cy) / 10;
        if (room > 0) {
            wrapped(g, font, Component.translatable(info.owned() ? "screen.isekai_mahou.home_realm.desc" : "screen.isekai_mahou.home_realm.how_to"),
                    lx, cy, lw, FAINT, room);
        }
    }

    // ------------------------------------------------------------------------------------------------ coluna direita
    /** Uma linha da lista da direita: cabeçalho de grupo, pessoa com botão, ou texto de "vazio". */
    private record Row(Component header, Person person, int kind, Component note) {
        static final int INVITED = 0, CANDIDATE = 1, VISIT = 2;
    }

    private List<Row> rows(Info info) {
        List<Row> out = new ArrayList<>();
        if (!info.visitable().isEmpty()) {
            out.add(new Row(Component.translatable("screen.isekai_mahou.home_realm.friends_homes"), null, 0, null));
            for (Person p : info.visitable()) out.add(new Row(null, p, Row.VISIT, null));
        }
        if (info.owned()) {
            out.add(new Row(Component.translatable("screen.isekai_mahou.home_realm.guests", info.invited().size()), null, 0, null));
            if (info.invited().isEmpty()) out.add(new Row(null, null, 0, Component.translatable("screen.isekai_mahou.home_realm.guests_empty")));
            for (Person p : info.invited()) out.add(new Row(null, p, Row.INVITED, null));
            List<Person> candidates = candidates(info);
            if (!candidates.isEmpty()) {
                out.add(new Row(Component.translatable("screen.isekai_mahou.home_realm.invite"), null, 0, null));
                for (Person p : candidates) out.add(new Row(null, p, Row.CANDIDATE, null));
            }
        }
        return out;
    }

    /** Jogadores online que ainda não foram convidados (o cliente já conhece a lista do TAB). */
    private static List<Person> candidates(Info info) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == null || mc.player == null || info.invited().size() >= HomeNetwork.MAX_PEOPLE) return List.of();
        Set<UUID> skip = new HashSet<>();
        skip.add(mc.player.getUUID());
        for (Person p : info.invited()) skip.add(p.id());
        List<Person> out = new ArrayList<>();
        for (PlayerInfo pi : conn.getOnlinePlayers()) {
            if (out.size() >= 40) break;
            if (!skip.contains(pi.getProfile().getId())) out.add(new Person(pi.getProfile().getId(), pi.getProfile().getName(), true));
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    private void renderPeople(GuiGraphics g, Font font, Info info, int lx, int ly, int lw, int mx, int my) {
        List<Row> rows = rows(info);
        int top = ly, bottom = ly + h;
        if (rows.isEmpty()) {
            wrapped(g, font, Component.translatable("screen.isekai_mahou.home_realm.nothing"), lx, top, lw, MUTED, 6);
            maxScroll = 0;
            scroll = 0;
            return;
        }
        int listW = lw - 6;
        boolean combat = !info.inRealm() && HomeClientState.combatSecondsLeft() > 0;
        g.enableScissor(lx, top, lx + lw, bottom);
        int cy = top - scroll;
        int contentH = 0;
        for (Row row : rows) {
            int rh = row.header != null ? 14 : ROW_H;
            if (cy + rh > top && cy < bottom) {
                if (row.header != null) {
                    g.drawString(font, row.header.copy().withStyle(ChatFormatting.BOLD), lx, cy + 3, SKY, true);
                } else if (row.note != null) {
                    g.drawString(font, font.plainSubstrByWidth(row.note.getString(), listW), lx + 2, cy + 5, FAINT, false);
                } else {
                    personRow(g, font, row, lx, cy, listW, top, bottom, mx, my, combat);
                }
            }
            cy += rh;
            contentH += rh;
        }
        g.disableScissor();
        maxScroll = Math.max(0, contentH - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        if (maxScroll > 0) {
            int vh = bottom - top;
            int barH = Math.max(14, vh * vh / Math.max(1, contentH));
            int barY = top + (int) ((vh - barH) * (scroll / (float) maxScroll));
            g.fill(lx + lw - 3, top, lx + lw - 1, bottom, 0x40000000);
            g.fill(lx + lw - 3, barY, lx + lw - 1, barY + barH, SKY);
        }
    }

    private void personRow(GuiGraphics g, Font font, Row row, int lx, int cy, int listW, int top, int bottom, int mx, int my, boolean combat) {
        Person p = row.person;
        boolean hover = mx >= lx && mx < lx + listW && my >= Math.max(cy, top) && my < Math.min(cy + ROW_H - 2, bottom);
        g.fill(lx, cy, lx + listW, cy + ROW_H - 2, hover ? CARD_HOVER : CARD);
        g.fill(lx, cy, lx + 2, cy + ROW_H - 2, p.online() ? GREEN : FAINT);
        int btnW = Math.min(64, listW / 2);
        g.drawString(font, font.plainSubstrByWidth(p.name(), listW - btnW - 12), lx + 6, cy + 5, p.online() ? TEXT : MUTED, false);
        int bx = lx + listW - btnW - 2, by = cy + 2, bh = ROW_H - 6;
        if (by < top || by + bh > bottom) return; // botão cortado pela rolagem: não clicável
        switch (row.kind) {
            case Row.INVITED -> button(g, font, bx, by, btnW, bh, Component.translatable("screen.isekai_mahou.home_realm.remove"), mx, my, RED,
                    () -> send(new Action(Action.UNINVITE, p.id()), false));
            case Row.CANDIDATE -> button(g, font, bx, by, btnW, bh, Component.translatable("screen.isekai_mahou.home_realm.invite_btn"), mx, my,
                    GREEN, () -> send(new Action(Action.INVITE, p.id()), false));
            default -> {
                if (combat) disabled(g, font, bx, by, btnW, bh, Component.literal(clock(HomeClientState.combatSecondsLeft())));
                else button(g, font, bx, by, btnW, bh, Component.translatable("screen.isekai_mahou.home_realm.visit"), mx, my, SKY,
                        () -> send(new Action(Action.VISIT, p.id()), true));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ utilidades
    private static String clock(int seconds) {
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    private void send(Action action, boolean close) {
        PacketDistributor.sendToServer(action);
        lastRequest = 0;
        if (close) Minecraft.getInstance().setScreen(null);
    }

    private void button(GuiGraphics g, Font font, int bx, int by, int bw, int bh, Component label, int mx, int my, int accent, Runnable action) {
        boolean hover = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
        g.fill(bx, by, bx + bw, by + bh, mix(accent, 0xFF0E151C, hover ? 0.45F : 0.72F));
        g.renderOutline(bx, by, bw, bh, hover ? 0xFFFFFFFF : accent);
        String shown = font.plainSubstrByWidth(label.getString(), bw - 6);
        g.drawString(font, shown, bx + (bw - font.width(shown)) / 2, by + (bh - 8) / 2, TEXT, true);
        hits.add(new Hit(bx, by, bw, bh, action));
    }

    /** Botão apagado (não clicável): usado enquanto faltam os 2 min fora de combate. */
    private static void disabled(GuiGraphics g, Font font, int bx, int by, int bw, int bh, Component label) {
        g.fill(bx, by, bx + bw, by + bh, 0xFF141A20);
        g.renderOutline(bx, by, bw, bh, FAINT);
        String shown = font.plainSubstrByWidth(label.getString(), bw - 6);
        g.drawString(font, shown, bx + (bw - font.width(shown)) / 2, by + (bh - 8) / 2, MUTED, false);
    }

    private static int wrapped(GuiGraphics g, Font font, Component text, int tx, int ty, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) g.drawString(font, lines.get(i), tx, ty + i * 10, color, false);
        return n * 10;
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (Hit hit : List.copyOf(hits)) {
            if (mx >= hit.x && my >= hit.y && mx < hit.x + hit.w && my < hit.y + hit.h) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hit.action.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (maxScroll <= 0) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (sy * 18)));
        return true;
    }
}
