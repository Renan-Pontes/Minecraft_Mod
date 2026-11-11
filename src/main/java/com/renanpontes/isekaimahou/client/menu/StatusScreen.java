package com.renanpontes.isekaimahou.client.menu;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.notify.NoticeLog;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Menu do mod (tecla B), estilo "Janela de Status" de isekai.
 * <p>
 * v1.1 — DOIS NÍVEIS: a linha de cima mostra só as categorias ({@link MenuTabs.Category}: Personagem, Magia,
 * Aventura, Social, Avisos — as vazias não aparecem) e a de baixo as abas da categoria escolhida (só quando ela tem 2+
 * abas). Abas de classe só aparecem para quem é da classe, e as outras vão aparecendo aos poucos ({@link MenuReveal},
 * selo "NOVA"). Seções (ex.: Missões/Lugares/Bestiário do Guia de Viagem) são abertas dentro de um {@link ContainerTab}.
 * <p>
 * Painel de até 520×318 (menor em telas pequenas), moldura arcana. A área entregue às abas é
 * {@code (left+6, top+contentTop, panelW-12, panelH-contentTop-6)} — {@code contentTop} = 26 sem a 2ª linha, 44 com ela.
 * Atalhos: clique, roda do mouse sobre as linhas, Ctrl+Tab / Ctrl+Shift+Tab e Page Down / Page Up (todas as abas).
 * {@link #currentTab()} devolve sempre a aba "folha" (ex.: "journal" dentro do Guia).
 */
public class StatusScreen extends Screen {
    public static final int MAX_PANEL_W = 520;
    public static final int MAX_PANEL_H = 318;
    private static final int BAR_Y = 5;       // categorias: de top+5 ...
    private static final int BAR_H = 19;      // ... até top+24
    private static final int SUB_Y = 27;      // abas da categoria: de top+27 ...
    private static final int SUB_H = 14;      // ... até top+41
    private static final int ICON_TAB_W = 24;
    private static final int TAB_GAP = 3;

    private static String lastTab = "status";
    /** Última aba aberta em cada categoria e a última seção de cada aba-mãe. */
    private static final Map<MenuTabs.Category, String> LAST_IN_CATEGORY = new EnumMap<>(MenuTabs.Category.class);
    private static final Map<String, String> LAST_CHILD = new HashMap<>();

    private String tabId;
    private MenuTab tab;
    private MenuTabs.Category category = MenuTabs.Category.CHARACTER;
    private int left, top, panelW, panelH, contentTop;
    private final List<CatSlot> cats = new ArrayList<>();
    private final List<SubSlot> subs = new ArrayList<>();
    private boolean catNamesHidden;

    private record CatSlot(MenuTabs.Category cat, int x, int w, boolean named) {}

    private record SubSlot(MenuTabs.Entry entry, int x, int w) {}

    public StatusScreen() {
        this(lastTab);
    }

    public StatusScreen(String tabId) {
        super(Component.translatable("screen.isekai_mahou.menu.title"));
        this.tabId = tabId;
    }

    /** Abre o menu numa aba específica (uma seção abre dentro da aba-mãe; aba escondida cai no Status). */
    public static void open(String tabId) {
        // aberto pelo jogo (NPC, item, tecla própria): libera a aba se a revelação progressiva ainda a escondia
        MenuReveal.force(tabId);
        Minecraft.getInstance().setScreen(new StatusScreen(tabId));
    }

    public int panelLeft() { return left; }
    public int panelTop() { return top; }
    public int panelWidth() { return panelW; }
    public int panelHeight() { return panelH; }
    /** Id da aba aberta (a "folha": uma seção, se estiver dentro de uma aba-mãe). */
    public String currentTab() { return tabId; }

    /** Adiciona um widget da aba atual (recriado ao trocar de aba). */
    public <T extends AbstractWidget> T addTabWidget(T widget) {
        return addRenderableWidget(widget);
    }

    /** Recria a aba atual (ex.: depois de mudar de modo dentro da aba). */
    public void refreshTab() {
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- Montagem
    @Override
    protected void init() {
        panelW = Math.min(width - 16, MAX_PANEL_W);
        panelH = Math.min(height - 16, MAX_PANEL_H);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        cats.clear();
        subs.clear();

        String leaf = resolve(tabId);
        if (leaf == null) return;
        tabId = leaf;
        lastTab = leaf;
        String parent = MenuTabs.parentOf(leaf);
        String topId = parent != null ? parent : leaf;
        category = MenuTabs.categoryOf(topId);
        LAST_IN_CATEGORY.put(category, leaf);
        if (parent != null) LAST_CHILD.put(parent, leaf);
        MenuReveal.markSeen(leaf);
        if (parent != null) MenuReveal.markSeen(parent);
        if (category == MenuTabs.Category.NOTICES) NoticeLog.markRead();

        layoutCategories();
        List<MenuTabs.Entry> entries = MenuTabs.visibleIn(category);
        boolean hasSub = entries.size() >= 2;
        if (hasSub) layoutSubs(entries);
        contentTop = hasSub ? SUB_Y + SUB_H + 3 : BAR_Y + BAR_H + 2;

        if (tab != null) tab.removed();
        tab = parent != null ? new ContainerTab(parent, leaf) : MenuTabs.get(leaf).factory().get();
        tab.init(this, left + 6, top + contentTop, panelW - 12, panelH - contentTop - 6);
    }

    /** Aba "folha" a abrir para o id pedido: a seção lembrada de uma aba-mãe; aba escondida/inexistente → Status. */
    private static String resolve(String id) {
        if (id != null && MenuTabs.isContainer(id)) {
            List<MenuTabs.Entry> kids = MenuTabs.children(id);
            String last = LAST_CHILD.get(id);
            id = null;
            for (MenuTabs.Entry k : kids) if (k.id().equals(last)) id = last;
            if (id == null && !kids.isEmpty()) id = kids.get(0).id();
        }
        if (id != null && MenuTabs.visible(id)) {
            String parent = MenuTabs.parentOf(id);
            if (parent == null || MenuTabs.visible(parent)) return id;
        }
        List<String> leaves = MenuTabs.visibleLeaves();
        if (leaves.isEmpty()) return null;
        return leaves.contains("status") ? "status" : leaves.get(0);
    }

    /** Primeira linha: categorias com ícone + nome; se não couber, só ícones (nome no tooltip e na placa). */
    private void layoutCategories() {
        List<MenuTabs.Category> list = MenuTabs.visibleCategories();
        int n = list.size();
        if (n == 0) return;
        int avail = panelW - 12;
        int[] full = new int[n];
        int sum = TAB_GAP * (n - 1);
        for (int i = 0; i < n; i++) {
            full[i] = 7 + 16 + 4 + font.width(list.get(i).title()) + 9;
            sum += full[i];
        }
        boolean named = sum <= avail;
        int[] w = new int[n];
        int gap = TAB_GAP;
        if (named) {
            int extra = Math.min(24, (avail - sum) / n);
            for (int i = 0; i < n; i++) w[i] = full[i] + extra;
        } else {
            int each = Math.min(ICON_TAB_W, (avail - gap * (n - 1)) / n);
            for (int i = 0; i < n; i++) w[i] = Math.max(10, each);
        }
        catNamesHidden = !named;
        int total = gap * (n - 1);
        for (int i = 0; i < n; i++) total += w[i];
        int x = left + 6 + Math.max(0, (avail - total) / 2);
        for (int i = 0; i < n; i++) {
            cats.add(new CatSlot(list.get(i), x, w[i], named));
            x += w[i] + gap;
        }
    }

    /** Segunda linha: "pílulas" com ícone pequeno + nome, centralizadas (encolhem o nome se precisar). */
    private void layoutSubs(List<MenuTabs.Entry> entries) {
        int n = entries.size();
        int avail = panelW - 16;
        int[] w = new int[n];
        int sum = TAB_GAP * (n - 1);
        for (int i = 0; i < n; i++) {
            w[i] = 5 + 10 + 3 + font.width(entries.get(i).title()) + 6;
            sum += w[i];
        }
        if (sum > avail) {
            int each = Math.max(22, (avail - TAB_GAP * (n - 1)) / n);
            for (int i = 0; i < n; i++) w[i] = Math.min(w[i], each);
            sum = TAB_GAP * (n - 1);
            for (int i = 0; i < n; i++) sum += w[i];
        }
        int x = left + 8 + Math.max(0, (avail - sum) / 2);
        for (int i = 0; i < n; i++) {
            subs.add(new SubSlot(entries.get(i), x, w[i]));
            x += w[i] + TAB_GAP;
        }
    }

    public void switchTab(String id) {
        tabId = id;
        rebuildWidgets();
    }

    /** Vai para a categoria seguinte (dir=1) ou anterior (dir=-1). */
    private void cycleCategory(int dir) {
        if (cats.isEmpty()) return;
        int cur = 0;
        for (int i = 0; i < cats.size(); i++) if (cats.get(i).cat() == category) cur = i;
        openCategory(cats.get(Math.floorMod(cur + dir, cats.size())).cat());
    }

    private void cycleSub(int dir) {
        if (subs.isEmpty()) return;
        String topId = topOf(tabId);
        int cur = 0;
        for (int i = 0; i < subs.size(); i++) if (subs.get(i).entry().id().equals(topId)) cur = i;
        playClick();
        switchTab(subs.get(Math.floorMod(cur + dir, subs.size())).entry().id());
    }

    /** Vai para a aba seguinte (dir=1) ou anterior (dir=-1) entre TODAS as abas visíveis, dando a volta. */
    public void cycleTab(int dir) {
        List<String> leaves = MenuTabs.visibleLeaves();
        if (leaves.isEmpty()) return;
        int cur = Math.max(0, leaves.indexOf(tabId));
        playClick();
        switchTab(leaves.get(Math.floorMod(cur + dir, leaves.size())));
    }

    private void openCategory(MenuTabs.Category c) {
        if (c == category) return;
        playClick();
        String last = LAST_IN_CATEGORY.get(c);
        if (last != null && MenuTabs.visible(last) && MenuTabs.categoryOf(topOf(last)) == c) {
            switchTab(last);
            return;
        }
        List<MenuTabs.Entry> entries = MenuTabs.visibleIn(c);
        if (!entries.isEmpty()) switchTab(entries.get(0).id());
    }

    private static String topOf(String id) {
        String p = MenuTabs.parentOf(id);
        return p != null ? p : id;
    }

    private CatSlot catAt(double mx, double my) {
        if (my < top + BAR_Y || my >= top + BAR_Y + BAR_H) return null;
        for (CatSlot s : cats) if (mx >= s.x() && mx < s.x() + s.w()) return s;
        return null;
    }

    private SubSlot subAt(double mx, double my) {
        if (subs.isEmpty() || my < top + SUB_Y || my >= top + SUB_Y + SUB_H) return null;
        for (SubSlot s : subs) if (mx >= s.x() && mx < s.x() + s.w()) return s;
        return null;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.25F, 0.8F));
    }

    /** A categoria tem alguma aba (ou seção) com o selo "NOVA"? */
    private static boolean categoryHasNew(MenuTabs.Category c) {
        for (MenuTabs.Entry e : MenuTabs.visibleIn(c)) if (entryHasNew(e.id())) return true;
        return false;
    }

    static boolean entryHasNew(String id) {
        if (MenuReveal.isNew(id)) return true;
        if (MenuTabs.isContainer(id)) for (MenuTabs.Entry k : MenuTabs.children(id)) if (MenuReveal.isNew(k.id())) return true;
        return false;
    }

    // ---------------------------------------------------------------- Desenho
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        MagicPath path = ClientMagic.data().path();
        int accent = path == MagicPath.NONE ? 0xFF6A4BA0 : path.argb();
        MahouUi.frame(g, left, top, panelW, panelH, accent);
        // linha que separa as categorias do resto
        g.fill(left + 4, top + BAR_Y + BAR_H, left + panelW - 4, top + BAR_Y + BAR_H + 1, MahouUi.alpha(MahouUi.GOLD_DIM, 0xC0));
        CatSlot hoverCat = catAt(mouseX, mouseY);
        for (CatSlot s : cats) renderCategory(g, s, s.cat() == category, s == hoverCat);
        if (!subs.isEmpty()) {
            // faixa das abas da categoria
            g.fill(left + 6, top + SUB_Y - 1, left + panelW - 6, top + SUB_Y + SUB_H + 1, 0x40000000);
            g.fill(left + 10, top + SUB_Y + SUB_H + 2, left + panelW - 10, top + SUB_Y + SUB_H + 3, MahouUi.alpha(MahouUi.GOLD_DIM, 0x60));
            SubSlot hoverSub = subAt(mouseX, mouseY);
            String topId = topOf(tabId);
            for (SubSlot s : subs) renderSub(g, s, s.entry().id().equals(topId), s == hoverSub);
        }
        renderTitlePlate(g);
    }

    private void renderCategory(GuiGraphics g, CatSlot s, boolean active, boolean hover) {
        int x = s.x(), y = top + BAR_Y, w = s.w(), h = BAR_H;
        if (active) {
            MahouUi.roundRect(g, x, y, w, h + 2, 0xFF4E3C14, 0xFF1C1626);
            g.fill(x + 1, y, x + w - 1, y + 1, MahouUi.GOLD);
            g.fill(x, y + 1, x + 1, y + h + 2, MahouUi.GOLD);
            g.fill(x + w - 1, y + 1, x + w, y + h + 2, MahouUi.GOLD);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, MahouUi.alpha(MahouUi.GOLD_PALE, 0x90));
        } else {
            MahouUi.roundRect(g, x, y + 1, w, h - 1, hover ? 0xD8342A4C : 0xB81E182C, hover ? 0xD81C1628 : 0xB8120E1A);
            MahouUi.roundOutline(g, x, y + 1, w, h - 1, hover ? 0xFF8C7AB8 : 0xFF3E3458);
        }
        int icon = Math.max(4, Math.min(16, Math.min(w - 4, h - 3)));
        int ix = s.named() ? x + 7 : x + (w - icon) / 2;
        int iy = y + (h - icon) / 2 + (active ? 0 : 1);
        if (!active && !hover) g.setColor(1F, 1F, 1F, 0.72F);
        GlyphArt.tab(g, s.cat().iconTab, ix, iy, icon);
        g.setColor(1F, 1F, 1F, 1F);
        if (s.named()) {
            int color = active ? MahouUi.GOLD : hover ? 0xFFFFFFFF : MahouUi.TEXT_DIM;
            g.drawString(font, s.cat().title(), ix + icon + 4, y + (h - 8) / 2 + (active ? 0 : 1), color, true);
        }
        // Selos: avisos não lidos (Avisos) ou "aba nova" na categoria
        if (s.cat() == MenuTabs.Category.NOTICES && NoticeLog.unread() > 0 && !active) {
            String n = String.valueOf(NoticeLog.unread());
            int bw = font.width(n) + 4;
            MahouUi.roundRect(g, x + w - bw - 1, y - 3, bw, 9, 0xFFB8452E, 0xFF7A2418);
            MahouUi.smallText(g, font, Component.literal(n), x + w - bw + 1, y - 2, 0.85F, 0xFFFFFFFF, false);
        } else if (!active && categoryHasNew(s.cat())) {
            newBadge(g, x + w - 4, y - 1);
        }
    }

    private void renderSub(GuiGraphics g, SubSlot s, boolean active, boolean hover) {
        int x = s.x(), y = top + SUB_Y, w = s.w(), h = SUB_H;
        if (active) {
            MahouUi.roundRect(g, x, y, w, h, 0xFF3E3014, 0xFF1C1626);
            MahouUi.roundOutline(g, x, y, w, h, MahouUi.GOLD);
        } else {
            MahouUi.roundRect(g, x, y, w, h, hover ? 0xD0342A4C : 0x901E182C, hover ? 0xD01C1628 : 0x90120E1A);
            MahouUi.roundOutline(g, x, y, w, h, hover ? 0xFF8C7AB8 : 0xFF3A3050);
        }
        int ix = x + 4, iy = y + 2;
        if (!active && !hover) g.setColor(1F, 1F, 1F, 0.75F);
        GlyphArt.tab(g, s.entry().id(), ix, iy, 10);
        g.setColor(1F, 1F, 1F, 1F);
        int color = active ? MahouUi.GOLD : hover ? 0xFFFFFFFF : MahouUi.TEXT_DIM;
        g.drawString(font, MahouUi.fit(font, s.entry().title(), w - 21), ix + 13, y + 3, color, false);
        if (!active && entryHasNew(s.entry().id())) newBadge(g, x + w - 3, y - 2);
    }

    /** Selo "NOVA" (piscando de leve) com o canto direito em (rx, y). */
    private void newBadge(GuiGraphics g, int rx, int y) {
        Component t = Component.translatable("screen.isekai_mahou.menu.new_badge");
        float scale = 0.6F;
        int tw = (int) Math.ceil(font.width(t) * scale) + 4;
        float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 220.0);
        int glow = MahouUi.alpha(MahouUi.GOLD, (int) (0x90 + 0x6F * pulse));
        MahouUi.roundRect(g, rx - tw, y - 2, tw, 7, glow, 0xFFB8862E);
        MahouUi.smallText(g, font, t, rx - tw + 2, y - 1, scale, 0xFF2A1A05, false);
    }

    /** Placa "Janela de Status" no meio da borda de cima (com a categoria quando a barra não mostra os nomes). */
    private void renderTitlePlate(GuiGraphics g) {
        Component text = title;
        if (catNamesHidden) text = title.copy().append(Component.literal(" · ")).append(category.title());
        int tw = Math.min(panelW - 16, font.width(text) + 22);
        int px = left + (panelW - tw) / 2, py = top - 6;
        MahouUi.roundRect(g, px, py, tw, 11, 0xFF2C2238, 0xFF14101C);
        MahouUi.roundOutline(g, px, py, tw, 11, MahouUi.GOLD);
        MahouUi.diamond(g, px + 5, py + 5, 2, MahouUi.GOLD);
        MahouUi.diamond(g, px + tw - 6, py + 5, 2, MahouUi.GOLD);
        g.drawString(font, MahouUi.fit(font, text, tw - 22), px + 11, py + 2, MahouUi.GOLD_PALE, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (tab != null) tab.render(g, mouseX, mouseY, partialTick);
        CatSlot hc = catAt(mouseX, mouseY);
        if (hc != null && !hc.named()) g.renderTooltip(font, hc.cat().title(), mouseX, mouseY);
        SubSlot hs = subAt(mouseX, mouseY);
        if (hs != null && font.width(hs.entry().title()) > hs.w() - 21) g.renderTooltip(font, hs.entry().title(), mouseX, mouseY);
    }

    @Override
    public void tick() {
        if (tab != null) tab.tick();
    }

    // ---------------------------------------------------------------- Entrada
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        CatSlot c = catAt(mx, my);
        if (c != null) {
            if (button == 0) openCategory(c.cat());
            return true;
        }
        SubSlot s = subAt(mx, my);
        if (s != null) {
            if (button == 0 && !s.entry().id().equals(topOf(tabId))) {
                playClick();
                switchTab(s.entry().id());
            }
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        return tab != null && tab.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean r = super.mouseReleased(mx, my, button);
        return (tab != null && tab.mouseReleased(mx, my, button)) || r;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (tab != null && tab.mouseDragged(mx, my, button, dx, dy)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0 && catAt(mx, my) != null) {
            cycleCategory(sy > 0 ? -1 : 1);
            return true;
        }
        if (sy != 0 && subAt(mx, my) != null) {
            cycleSub(sy > 0 ? -1 : 1);
            return true;
        }
        if (tab != null && tab.mouseScrolled(mx, my, sx, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (tab != null && tab.keyPressed(key, scan, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_TAB && hasControlDown()) {
            cycleTab(hasShiftDown() ? -1 : 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            cycleTab(key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1);
            return true;
        }
        if (getFocused() == null && CoreKeys.MENU.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (tab != null && tab.charTyped(c, modifiers)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override
    public void removed() {
        if (tab != null) tab.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
