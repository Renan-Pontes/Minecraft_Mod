package com.renanpontes.isekaimahou.client.menu;

import java.util.List;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Aba-mãe com SEÇÕES (v1.1): uma coluna de botões à esquerda e, ao lado, a aba da seção escolhida — as abas antigas
 * continuam iguais, só ganham um lugar dentro desta. O "Guia de Viagem" ({@link #GUIDE}) junta Missões (Diário),
 * Lugares (Viagem) e Bestiário: "o que já viu e o que vai ver". Seções aparecem conforme a revelação progressiva
 * ({@link MenuReveal}); Missões sempre. Trocar de seção reabre a tela na seção nova ({@link StatusScreen#switchTab}).
 */
public class ContainerTab implements MenuTab {
    /** Id da aba "Guia de Viagem". */
    public static final String GUIDE = "guide";
    private static final int SIDE_W = 82;
    private static final int SIDE_W_SMALL = 24;
    private static final int BTN_H = 22;

    private final String parentId;
    private final String childId;
    private MenuTab child;
    private StatusScreen screen;
    private List<MenuTabs.Entry> sections = List.of();
    private int x, y, w, h, side;

    public ContainerTab(String parentId, String childId) {
        this.parentId = parentId;
        this.childId = childId;
    }

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.screen = screen;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        sections = MenuTabs.children(parentId);
        side = w >= 340 ? SIDE_W : SIDE_W_SMALL;
        MenuTabs.Entry e = MenuTabs.get(childId);
        if (e == null) return;
        child = e.factory().get();
        child.init(screen, x + side + 4, y, w - side - 4, h);
    }

    private boolean named() {
        return side == SIDE_W;
    }

    private int sectionAt(double mx, double my) {
        if (mx < x || mx >= x + side) return -1;
        int i = (int) Math.floor((my - y - 14) / (double) (BTN_H + 2));
        if (my < y + 14 || i < 0 || i >= sections.size()) return -1;
        return my < y + 14 + i * (BTN_H + 2) + BTN_H ? i : -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        // Coluna das seções
        MahouUi.roundRect(g, x, y, side, h, 0xC0181226, 0xC00B0912);
        MahouUi.roundOutline(g, x, y, side, h, MahouUi.alpha(MahouUi.GOLD_DIM, 0xA0));
        MenuTabs.Entry parent = MenuTabs.get(parentId);
        if (named() && parent != null) {
            g.drawString(font, MahouUi.fit(font, parent.title(), side - 8), x + 5, y + 4, MahouUi.GOLD_PALE, true);
        } else {
            MahouUi.diamond(g, x + side / 2, y + 7, 2, MahouUi.GOLD);
        }
        int hover = sectionAt(mouseX, mouseY);
        for (int i = 0; i < sections.size(); i++) {
            MenuTabs.Entry s = sections.get(i);
            boolean active = s.id().equals(childId);
            int bx = x + 3, by = y + 14 + i * (BTN_H + 2), bw = side - 6;
            if (active) {
                MahouUi.roundRect(g, bx, by, bw, BTN_H, 0xFF4E3C14, 0xFF1C1626);
                MahouUi.roundOutline(g, bx, by, bw, BTN_H, MahouUi.GOLD);
                g.fill(bx + bw - 1, by + 3, bx + bw + 3, by + BTN_H - 3, MahouUi.GOLD);
            } else {
                MahouUi.roundRect(g, bx, by, bw, BTN_H, i == hover ? 0xD8342A4C : 0xA01E182C, i == hover ? 0xD81C1628 : 0xA0120E1A);
                MahouUi.roundOutline(g, bx, by, bw, BTN_H, i == hover ? 0xFF8C7AB8 : 0xFF3E3458);
            }
            int ix = named() ? bx + 4 : bx + (bw - 14) / 2;
            if (!active && i != hover) g.setColor(1F, 1F, 1F, 0.75F);
            GlyphArt.tab(g, s.id(), ix, by + 4, 14);
            g.setColor(1F, 1F, 1F, 1F);
            if (named()) {
                int color = active ? MahouUi.GOLD : i == hover ? 0xFFFFFFFF : MahouUi.TEXT_DIM;
                g.drawString(font, MahouUi.fit(font, sectionTitle(s), bw - 24), ix + 18, by + 7, color, true);
            }
            if (!active && MenuReveal.isNew(s.id())) {
                Component t = Component.translatable("screen.isekai_mahou.menu.new_badge");
                int tw = (int) Math.ceil(font.width(t) * 0.6F) + 4;
                MahouUi.roundRect(g, bx + bw - tw, by - 2, tw, 7, MahouUi.GOLD, 0xFFB8862E);
                MahouUi.smallText(g, font, t, bx + bw - tw + 2, by - 1, 0.6F, 0xFF2A1A05, false);
            }
        }
        if (child != null) child.render(g, mouseX, mouseY, partialTick);
        if (hover >= 0 && !named()) g.renderTooltip(font, sectionTitle(sections.get(hover)), mouseX, mouseY);
    }

    /** Nome da seção dentro do Guia ("Missões", "Lugares"...) — ou o título da própria aba. */
    private static Component sectionTitle(MenuTabs.Entry e) {
        String key = "screen.isekai_mahou.menu.section." + e.id();
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key) : e.title();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = sectionAt(mx, my);
        if (i >= 0) {
            String id = sections.get(i).id();
            if (button == 0 && !id.equals(childId)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3F, 0.7F));
                screen.switchTab(id);
            }
            return true;
        }
        return child != null && child.mouseClicked(mx, my, button);
    }

    @Override
    public void tick() {
        if (child != null) child.tick();
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return child != null && child.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return child != null && child.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= x && mx < x + side && my >= y && my < y + h && scrollY != 0 && sections.size() > 1) {
            int cur = 0;
            for (int i = 0; i < sections.size(); i++) if (sections.get(i).id().equals(childId)) cur = i;
            int next = Math.floorMod(cur + (scrollY > 0 ? -1 : 1), sections.size());
            screen.switchTab(sections.get(next).id());
            return true;
        }
        return child != null && child.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return child != null && child.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return child != null && child.charTyped(c, modifiers);
    }

    @Override
    public void removed() {
        if (child != null) child.removed();
    }
}
