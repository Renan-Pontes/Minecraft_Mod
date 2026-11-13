package com.renanpontes.isekaimahou.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreGameplay;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tela de escolha de classe (primeira vez no mundo), no estilo da Janela de Status.
 * <ul>
 *   <li>Topo: 3 abas de categoria (Mágicas / Físicas / Destreza), com ícone {@code textures/gui/category/<id>.png}.</li>
 *   <li>Esquerda: as 5 classes da aba (ícone {@code textures/gui/class/<id>.png}; reserva: ícone do kit).</li>
 *   <li>Direita: nome, estilo, recurso (na cor), descrição, sistema próprio, bônus de classe, afinidades, elemento
 *       inicial, difícil, técnicas iniciais, arma inicial e kit, e o lembrete de multiclasse (com rolagem).</li>
 * </ul>
 * Não fecha com ESC. Teclado: ←/→ (ou Tab) trocam a aba, ↑/↓ trocam a classe.
 */
public class PathSelectionScreen extends Screen {
    private static final int TAB_H = 22, PAD = 8, FOOT_H = 26;

    private final MagicPath.Category[] categories = MagicPath.Category.values();
    private final int[] selectedInTab = new int[categories.length];
    private int tab = 0;
    private int left, top, panelW, panelH, listW, rowH;
    private int detailX, detailY, detailW, detailH;
    private int scroll, contentH;
    private Button chooseButton;

    public PathSelectionScreen() {
        super(Component.translatable("screen.isekai_mahou.choose_path.title"));
    }

    private List<MagicPath> paths() {
        return MagicPath.byCategory(categories[tab]);
    }

    private MagicPath current() {
        List<MagicPath> list = paths();
        if (list.isEmpty()) return MagicPath.MAGE;
        return list.get(Mth.clamp(selectedInTab[tab], 0, list.size() - 1));
    }

    // =====================================================================================
    //  Layout
    // =====================================================================================
    @Override
    protected void init() {
        panelW = Mth.clamp(width - 24, 300, 460);
        panelH = Mth.clamp(height - 44, 196, 270);
        left = (width - panelW) / 2;
        top = Math.max(14, (height - panelH) / 2 + 2);
        listW = Mth.clamp(panelW * 34 / 100, 108, 150);
        int listTop = top + PAD + TAB_H + 6;
        int listH = top + panelH - PAD - listTop;
        rowH = Mth.clamp((listH - 4 * 4) / 5, 20, 34);
        detailX = left + PAD + listW + 10;
        detailY = listTop;
        detailW = left + panelW - PAD - detailX;
        detailH = top + panelH - PAD - FOOT_H - detailY;
        chooseButton = addRenderableWidget(Button.builder(chooseLabel(), b -> choose())
                .bounds(detailX, top + panelH - PAD - 20, detailW, 20).build());
        scroll = 0;
    }

    private Component chooseLabel() {
        return Component.translatable("screen.isekai_mahou.choose_named", current().displayName());
    }

    private void choose() {
        PacketDistributor.sendToServer(new CorePayloads.ChoosePath(current().ordinal()));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2F, 0.6F));
        onClose();
    }

    private void select(int newTab, int index) {
        newTab = Math.floorMod(newTab, categories.length);
        int size = MagicPath.byCategory(categories[newTab]).size();
        index = size == 0 ? 0 : Math.floorMod(index, size);
        if (newTab == tab && index == selectedInTab[tab]) return;
        boolean tabChanged = newTab != tab;
        tab = newTab;
        selectedInTab[tab] = index;
        scroll = 0;
        if (chooseButton != null) chooseButton.setMessage(chooseLabel());
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                tabChanged ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.UI_BUTTON_CLICK.value(), tabChanged ? 1.25F : 1.4F, 0.6F));
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int t = tabAt(mx, my);
        if (t >= 0) {
            select(t, selectedInTab[t]);
            return true;
        }
        int r = rowAt(mx, my);
        if (r >= 0) {
            select(tab, r);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= detailX && mx < detailX + detailW && my >= detailY && my < detailY + detailH && contentH > detailH) {
            scroll = Mth.clamp(scroll - (int) Math.round(sy * 12), 0, contentH - detailH);
            return true;
        }
        if (mx >= left && mx < left + PAD + listW && sy != 0) {
            select(tab, selectedInTab[tab] + (sy > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> { select(tab - 1, selectedInTab[Math.floorMod(tab - 1, categories.length)]); return true; }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_TAB -> {
                select(tab + 1, selectedInTab[Math.floorMod(tab + 1, categories.length)]);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> { select(tab, selectedInTab[tab] - 1); return true; }
            case GLFW.GLFW_KEY_DOWN -> { select(tab, selectedInTab[tab] + 1); return true; }
            default -> { return super.keyPressed(key, scan, mods); }
        }
    }

    private int tabX(int i) {
        int w = tabW();
        return left + PAD + i * (w + 4);
    }

    private int tabW() {
        return (panelW - 2 * PAD - 4 * (categories.length - 1)) / categories.length;
    }

    private int tabAt(double mx, double my) {
        int y = top + PAD;
        if (my < y || my >= y + TAB_H) return -1;
        for (int i = 0; i < categories.length; i++) {
            int x = tabX(i);
            if (mx >= x && mx < x + tabW()) return i;
        }
        return -1;
    }

    private int rowY(int i) {
        return detailY + i * (rowH + 4);
    }

    private int rowAt(double mx, double my) {
        int x = left + PAD;
        if (mx < x || mx >= x + listW) return -1;
        for (int i = 0; i < paths().size(); i++) {
            int y = rowY(i);
            if (my >= y && my < y + rowH) return i;
        }
        return -1;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        MagicPath p = current();
        MahouUi.frame(g, left, top, panelW, panelH, p.argb());
        renderTitlePlate(g);
        int hoverTab = tabAt(mx, my);
        for (int i = 0; i < categories.length; i++) renderTab(g, i, i == tab, i == hoverTab);
        g.fill(left + 4, top + PAD + TAB_H, left + panelW - 4, top + PAD + TAB_H + 1, MahouUi.alpha(MahouUi.GOLD_DIM, 0xC0));
        // divisória entre a lista e os detalhes
        int dx = left + PAD + listW + 4;
        g.fill(dx, detailY, dx + 1, top + panelH - PAD, MahouUi.alpha(MahouUi.GOLD_DIM, 0x80));
    }

    private void renderTitlePlate(GuiGraphics g) {
        int tw = Math.min(panelW - 16, font.width(title) + 22);
        int px = left + (panelW - tw) / 2, py = top - 6;
        MahouUi.roundRect(g, px, py, tw, 11, 0xFF2C2238, 0xFF14101C);
        MahouUi.roundOutline(g, px, py, tw, 11, MahouUi.GOLD);
        MahouUi.diamond(g, px + 5, py + 5, 2, MahouUi.GOLD);
        MahouUi.diamond(g, px + tw - 6, py + 5, 2, MahouUi.GOLD);
        g.drawString(font, MahouUi.fit(font, title, tw - 22), px + 11, py + 2, MahouUi.GOLD_PALE, false);
    }

    private void renderTab(GuiGraphics g, int i, boolean active, boolean hover) {
        MagicPath.Category c = categories[i];
        int x = tabX(i), y = top + PAD, w = tabW(), h = TAB_H;
        if (active) {
            MahouUi.roundRect(g, x, y, w, h + 1, 0xFF4E3C14, 0xFF1C1626);
            g.fill(x + 1, y, x + w - 1, y + 1, MahouUi.GOLD);
            g.fill(x, y + 1, x + 1, y + h + 1, MahouUi.GOLD);
            g.fill(x + w - 1, y + 1, x + w, y + h + 1, MahouUi.GOLD);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, MahouUi.alpha(MahouUi.GOLD_PALE, 0x90));
        } else {
            MahouUi.roundRect(g, x, y + 1, w, h - 1, hover ? 0xD8342A4C : 0xB81E182C, hover ? 0xD81C1628 : 0xB8120E1A);
            MahouUi.roundOutline(g, x, y + 1, w, h - 1, hover ? 0xFF8C7AB8 : 0xFF3E3458);
        }
        Component name = c.displayName();
        int icon = 16;
        int textW = font.width(name);
        boolean showName = w >= icon + 10 + Math.min(textW, 40);
        int contentW = showName ? icon + 4 + Math.min(textW, w - icon - 12) : icon;
        int ix = x + (w - contentW) / 2, iy = y + (h - icon) / 2 + (active ? 0 : 1);
        if (!active && !hover) g.setColor(1F, 1F, 1F, 0.72F);
        categoryIcon(g, c, ix, iy, icon);
        g.setColor(1F, 1F, 1F, 1F);
        if (showName) {
            int color = active ? MahouUi.GOLD : hover ? 0xFFFFFFFF : MahouUi.TEXT_DIM;
            g.drawString(font, MahouUi.fit(font, name, w - icon - 12), ix + icon + 4, y + (h - 8) / 2 + (active ? 0 : 1), color, true);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderList(g, mx, my);
        ItemStack hovered = renderDetails(g, mx, my);
        // Rodapé: com o Shinigami selecionado, mostra a dica da classe secreta (Hollow) no lugar da nota geral
        Component foot = current() == MagicPath.SHINIGAMI
                ? Component.translatable("screen.isekai_mahou.hollow.locked_line", MagicPath.HOLLOW.displayName(), MagicPath.HOLLOW.lockedHint())
                : Component.translatable("screen.isekai_mahou.choose_path.note");
        g.drawCenteredString(font, MahouUi.fit(font, foot, Math.min(width - 16, 460)), width / 2,
                Math.min(height - 10, top + panelH + 5), current() == MagicPath.SHINIGAMI ? 0xFFB8A89A : MahouUi.TEXT_DIM);
        int ht = tabAt(mx, my);
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mx, my);
        } else if (ht >= 0) {
            MagicPath.Category c = categories[ht];
            g.renderComponentTooltip(font, List.of(c.displayName().withColor(MahouUi.GOLD & 0xFFFFFF),
                    c.hint().withColor(MahouUi.TEXT & 0xFFFFFF)), mx, my);
        }
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        List<MagicPath> list = paths();
        int x = left + PAD;
        int hover = rowAt(mx, my);
        for (int i = 0; i < list.size(); i++) {
            MagicPath p = list.get(i);
            int y = rowY(i);
            boolean sel = i == selectedInTab[tab];
            int accent = p.argb();
            if (sel) {
                MahouUi.roundRect(g, x, y, listW, rowH, MahouUi.mix(0xFF2A2240, accent, 0.18F), 0xFF17131F);
                MahouUi.roundOutline(g, x, y, listW, rowH, accent);
                g.fill(x + 1, y + 2, x + 3, y + rowH - 2, accent);
            } else {
                MahouUi.roundRect(g, x, y, listW, rowH, i == hover ? 0xE0262036 : 0xC0181422, i == hover ? 0xE0181422 : 0xC0100C18);
                MahouUi.roundOutline(g, x, y, listW, rowH, i == hover ? 0xFF6C5C90 : 0xFF2E2642);
            }
            int icon = Math.min(20, rowH - 6);
            int iy = y + (rowH - icon) / 2;
            classIcon(g, p, x + 6, iy, icon);
            int tx = x + 10 + icon;
            int tw = listW - (tx - x) - 4;
            boolean twoLines = rowH >= 26;
            int nameY = twoLines ? y + rowH / 2 - 9 : y + (rowH - 8) / 2;
            g.drawString(font, MahouUi.fit(font, p.displayName(), tw), tx, nameY, 0xFFFFFFFF, true);
            if (twoLines) {
                MahouUi.smallText(g, font, Component.literal(font.substrByWidth(p.styleName(), (int) (tw / 0.75F)).getString()),
                        tx, nameY + 11, 0.75F, sel ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);
            }
        }
    }

    /** Detalhes da classe (com rolagem). Retorna o item sob o mouse (para a dica), ou vazio. */
    private ItemStack renderDetails(GuiGraphics g, int mx, int my) {
        MagicPath p = current();
        int x = detailX, w = detailW;
        ItemStack hovered = ItemStack.EMPTY;
        g.enableScissor(detailX - 2, detailY, detailX + detailW + 2, detailY + detailH);
        int y = detailY - scroll;

        // Cabeçalho: ícone grande, nome (1,5×), categoria · estilo
        int big = 24;
        classIcon(g, p, x, y, big);
        g.pose().pushPose();
        g.pose().translate(x + big + 6, y + 1, 0);
        g.pose().scale(1.5F, 1.5F, 1F);
        g.drawString(font, MahouUi.fit(font, p.displayName(), (int) ((w - big - 6) / 1.5F)), 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        MutableComponent sub = p.category().displayName().withColor(MahouUi.GOLD & 0xFFFFFF)
                .append(Component.literal(" · ").withColor(MahouUi.TEXT_DIM & 0xFFFFFF))
                .append(p.styleName().withColor(MahouUi.TEXT_DIM & 0xFFFFFF));
        g.drawString(font, MahouUi.fit(font, sub, w - big - 6), x + big + 6, y + 15, 0xFFFFFFFF, false);
        y += big + 6;

        // Recurso (chip na cor) + explicação
        MagicPath.Resource res = p.resource();
        Component resLabel = Component.translatable("screen.isekai_mahou.classes.resource", res.displayName());
        int chipW = Math.min(w, font.width(resLabel) + 10);
        MahouUi.roundRect(g, x, y, chipW, 12, MahouUi.alpha(MahouUi.darken(res.argb(), 0.55F), 0xE0), 0xE0100C18);
        MahouUi.roundOutline(g, x, y, chipW, 12, res.argb());
        g.drawString(font, MahouUi.fit(font, resLabel, chipW - 8), x + 5, y + 2, 0xFFFFFFFF, false);
        boolean chipHover = mx >= x && mx < x + chipW && my >= y && my < y + 12 && my >= detailY && my < detailY + detailH;
        y += 15;
        y = paragraph(g, res.description(), x, y, w, MahouUi.TEXT_DIM);
        y += 5;

        // Descrição
        y = paragraph(g, p.description(), x, y, w, 0xFFDDD8EA);
        y += 5;

        // Sistema próprio (o que só essa classe faz) + bônus de classe
        int accent = p.argb();
        g.fill(x, y + 1, x + 2, y + 9, accent);
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.classes.system", p.systemName())
                .withColor(MahouUi.lighten(accent, 0.25F) & 0xFFFFFF), w - 6), x + 5, y + 1, 0xFFFFFFFF, true);
        y += 12;
        y = paragraph(g, p.systemDescription(), x + 5, y, w - 5, MahouUi.TEXT);
        y += 3;
        g.drawString(font, Component.translatable("screen.isekai_mahou.classes.bonus").withColor(MahouUi.GOLD & 0xFFFFFF), x, y, 0xFFFFFFFF, true);
        y += 11;
        y = paragraph(g, p.bonusDescription(), x + 5, y, w - 5, MahouUi.TEXT_DIM);
        y += 5;

        // Afinidades: elemento principal destacado
        y = elementRow(g, Component.translatable("screen.isekai_mahou.affinity").withColor(MahouUi.GOLD & 0xFFFFFF),
                p.affinityElements(), p.primaryAffinity(), x, y, w);
        List<Element> hard = p.hardElements();
        if (!hard.isEmpty()) {
            y = elementRow(g, Component.translatable("screen.isekai_mahou.classes.hard").withColor(0xE07070), hard, null, x, y, w);
        }
        if (p.primaryAffinity() != null) {
            y = paragraph(g, Component.translatable("screen.isekai_mahou.classes.primary", p.primaryAffinity().displayName()),
                    x, y, w, MahouUi.TEXT_DIM);
        }
        y += 5;

        // Técnicas iniciais (as magias prontas da categoria)
        g.drawString(font, Component.translatable("screen.isekai_mahou.classes.starter_spells").withColor(MahouUi.GOLD & 0xFFFFFF), x, y, 0xFFFFFFFF, true);
        y += 11;
        String[] keys = {"key.isekai_mahou.slot1", "key.isekai_mahou.slot2", "key.isekai_mahou.slot3"};
        List<SpellRecipe> spells = CoreGameplay.starterSpells(p.category(), Minecraft.getInstance().getLanguageManager().getSelected());
        for (int i = 0; i < spells.size() && i < keys.length; i++) {
            SpellRecipe r = spells.get(i);
            MutableComponent line = Component.literal("[").append(Component.keybind(keys[i])).append("] ")
                    .withColor(MahouUi.TEXT_DIM & 0xFFFFFF)
                    .append(Component.literal(r.name()).withColor(r.color() >= 0 ? r.color() : 0xFFFFFF));
            g.drawString(font, MahouUi.fit(font, line, w - 4), x + 4, y, 0xFFFFFFFF, false);
            y += 10;
        }
        y += 5;

        // Arma inicial + kit
        List<ItemStack> kit = kit(p);
        g.drawString(font, Component.translatable("screen.isekai_mahou.starting_kit").withColor(MahouUi.GOLD & 0xFFFFFF), x, y, 0xFFFFFFFF, true);
        ItemStack weapon = ClassKits.weapon(p);
        if (!weapon.isEmpty()) {
            Component wl = Component.translatable("screen.isekai_mahou.classes.weapon", weapon.getHoverName());
            int lx = x + font.width(Component.translatable("screen.isekai_mahou.starting_kit")) + 6;
            g.drawString(font, MahouUi.fit(font, wl, x + w - lx), lx, y, MahouUi.TEXT, false);
        }
        y += 11;
        if (kit.isEmpty()) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.classes.kit_soon"), x + 4, y, MahouUi.TEXT_DIM, false);
            y += 12;
        } else {
            int perRow = Math.max(1, w / 20);
            for (int i = 0; i < kit.size(); i++) {
                int ix = x + (i % perRow) * 20, iy = y + (i / perRow) * 20;
                g.fill(ix, iy, ix + 18, iy + 18, 0x40FFFFFF);
                g.renderOutline(ix, iy, 18, 18, 0x30FFFFFF);
                g.renderItem(kit.get(i), ix + 1, iy + 1);
                g.renderItemDecorations(font, kit.get(i), ix + 1, iy + 1);
                if (mx >= ix && mx < ix + 18 && my >= iy && my < iy + 18 && my >= detailY && my < detailY + detailH) {
                    hovered = kit.get(i);
                }
            }
            y += ((kit.size() + perRow - 1) / perRow) * 20;
        }
        y += 4;
        // Multiclasse livre: a classe dá direcionamento, não prisão
        y = paragraph(g, Component.translatable("screen.isekai_mahou.classes.multiclass"), x, y, w, MahouUi.alpha(MahouUi.GOLD_PALE, 0xC8));
        g.disableScissor();

        contentH = y + scroll - detailY;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - detailH));
        if (contentH > detailH) {
            // barrinha de rolagem
            int bx = detailX + detailW + 1;
            int barH = Math.max(12, detailH * detailH / contentH);
            int by = detailY + (detailH - barH) * scroll / Math.max(1, contentH - detailH);
            g.fill(bx, detailY, bx + 2, detailY + detailH, 0x40FFFFFF);
            g.fill(bx, by, bx + 2, by + barH, MahouUi.GOLD_DIM);
        }
        if (chipHover && hovered.isEmpty()) {
            g.renderComponentTooltip(font, List.of(res.displayName(), res.description()), mx, my);
        }
        return hovered;
    }

    private int paragraph(GuiGraphics g, Component text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : font.split(text, w)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    /** "Afinidade: [ícone] Fogo [ícone] Vento" — quebra a linha se não couber. O principal ganha um losango. */
    private int elementRow(GuiGraphics g, Component label, List<Element> elements, Element primary, int x, int y, int w) {
        g.drawString(font, label, x, y + 1, 0xFFFFFFFF, true);
        int cx = x + font.width(label) + 4;
        for (Element e : elements) {
            Component name = e.displayName();
            int ew = 11 + font.width(name) + 6;
            if (cx + ew > x + w && cx > x + font.width(label) + 4) {
                y += 11;
                cx = x + 8;
            }
            GlyphArt.element(g, e, cx, y, 9);
            g.drawString(font, name, cx + 11, y + 1, 0xFFFFFFFF, true);
            if (e == primary) MahouUi.diamond(g, cx + 11 + font.width(name) + 3, y + 4, 2, MahouUi.GOLD);
            cx += ew;
        }
        return y + 12;
    }

    /** Kit para mostrar: arma do kit (+ escudo do Cavaleiro) + armadura + itens, sem repetir. */
    private static List<ItemStack> kit(MagicPath p) {
        List<ItemStack> kit = new ArrayList<>(ClassKits.armor(p));
        kit.addAll(ClassKits.items(p));
        List<ItemStack> gear = ClassKits.fallbackGear(p);
        for (int i = gear.size() - 1; i >= 0; i--) {
            ItemStack g = gear.get(i);
            if (kit.stream().noneMatch(s -> s.is(g.getItem()))) kit.add(0, g);
        }
        return kit;
    }

    private static void classIcon(GuiGraphics g, MagicPath p, int x, int y, int size) {
        if (GlyphArt.exists(p.iconTexture())) {
            GlyphArt.icon(g, p.iconTexture(), x, y, size);
            return;
        }
        item(g, ClassKits.icon(p), x, y, size);
    }

    private static void categoryIcon(GuiGraphics g, MagicPath.Category c, int x, int y, int size) {
        if (GlyphArt.exists(c.iconTexture())) {
            GlyphArt.icon(g, c.iconTexture(), x, y, size);
            return;
        }
        Item fallback = switch (c) {
            case PHYSICAL -> Items.IRON_SWORD;
            case DEXTERITY -> Items.BOW;
            default -> Items.ENCHANTED_BOOK;
        };
        item(g, new ItemStack(fallback), x, y, size);
    }

    /** Item escalado para size×size. */
    private static void item(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        if (size == 16) {
            g.renderItem(stack, x, y);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        float s = size / 16F;
        g.pose().scale(s, s, 1F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
