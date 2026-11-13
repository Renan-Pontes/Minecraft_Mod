package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.feature.transform_laws.TransformForge;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.LinkGlyph;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellBreakdown;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellPreset;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;
import com.renanpontes.isekaimahou.network.ClientMagic;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Grimório" (v1.0): editor de magias PROGRAMÁVEIS.
 * <ul>
 *   <li>Esquerda: paleta (abas de tipo com ícone, chips de elemento com runa e de CATEGORIA de classe para os glifos
 *       de Origem, em multisseleção, busca), Presets (rascunhos nomeados), Biblioteca ou galeria de Lendas.</li>
 *   <li>Direita: "Salvar em: Loadout N, slot Z/X/C/G, versão" (9 loadouts, sem elemento: o elemento vem dos
 *       glifos), o PROGRAMA em estágios ligados ({@link ProgramView}) ou o DETALHAMENTO ({@link BreakdownView}: dano e
 *       custo de cada parte, cópias, aviso de custo por cópia), situação/erros, nome e cântico, cor, selos e ações.</li>
 * </ul>
 * Arraste glifos da paleta para qualquer ponto do programa; arraste chips para reordenar. Glifos de origem de
 * outra classe (aprendidos por Lição) levam o selo de pergaminho.
 */
public class GrimoireTab implements MenuTab, LibraryPanel.Host, PresetPanel.Host {
    private static final String K = "screen.isekai_mahou.grimoire.";
    private static final int CELL = 20;
    private static final int ICON = GlyphIcons.SIZE;
    /** Conjuração mínima (ticks, exclusiva) para o cântico ser recitado — mesma regra do SpellCaster. */
    private static final int CHANT_MIN_TICKS = 10;
    private static final int LINK_COLOR = GlyphArt.kindColor(GlyphKind.TRIGGER, true);
    private static final int MODE_ACCENT = 0xFF8A6FD0;
    /** Violeta da forja de transformações (glifo Transformação e o selo). */
    private static final int TF_COLOR = 0xFFB98CFF;
    /** Largura do seletor compacto de loadout ("1 ▾"), usado quando as caixinhas não cabem. */
    private static final int LOADOUT_DROP_W = 21;

    // ícones 9×9 dos botões e 7×7 das estatísticas
    private static final String[] PX_LOAD = {"....#....", "...###...", "..#####..", "....#....", "....#....", "#...#...#", "#.......#", "#########", "........."};
    private static final String[] PX_CLEAR = {"#.......#", ".#.....#.", "..#...#..", "...#.#...", "....#....", "...#.#...", "..#...#..", ".#.....#.", "#.......#"};
    private static final String[] PX_BOOK = {".###.###.", "#...#...#", "#.#.#.#.#", "#...#...#", "#.#.#.#.#", "#...#...#", "#...#...#", ".###.###.", "........."};
    private static final String[] PX_SCROLL = {".#######.", "#.#.....#", ".#......#", ".#.####.#", ".#......#", ".#.###..#", ".#......#", ".#######.", "##......."};
    private static final String[] PX_SAVE = {"....#....", "...###...", "..#####..", ".#######.", "#########", ".#######.", "..#####..", "...###...", "....#...."};
    private static final String[] PX_MANA = {"...#...", "..###..", ".#####.", ".#####.", "#######", ".#####.", "..###.."};
    private static final String[] PX_CAST = {"#######", ".#####.", "..###..", "...#...", "..#.#..", ".#...#.", "#######"};
    private static final String[] PX_CD = {"..###..", ".#.#.#.", "#..#..#", "#..##.#", "#.....#", ".#...#.", "..###.."};
    private static final String[] PX_HOP = {".####..", "#....#.", "#.....#", "#...###", "#....#.", ".#.....", "..###.."};
    private static final String[] PX_DMG = {"......#", ".....#.", "#...#..", ".#.#...", "..#....", ".#.#...", "#...#.."};
    private static final String[] PX_HEAL = {"..###..", "..###..", "#######", "#######", "#######", "..###..", "..###.."};
    private static final String[] PX_PRESET = {"#######.", "#.....##", "#.###..#", "#......#", "#.####.#", "#......#", "#.###..#", "########"};

    private StatusScreen screen;
    private int x, y, w, h;
    private int L, E, lx, ex;

    // esquerda
    private int searchY, listTop, gridTop;
    private int palCols, gridX;
    private final ScrollState palScroll = new ScrollState();
    private final LibraryPanel library = new LibraryPanel(this);
    private final PresetPanel presets = new PresetPanel(this);

    // direita
    private int destY, headY, boxY, boxH, statusY, fieldsY, colorY, btnY;
    private int colorW, hueX, hueW;
    /** Botões do círculo mágico da magia (forma, símbolo, cor), logo depois da barra de matiz. */
    private int circX;
    private static final int CIRC_STEP = 14;
    private final ProgramView program = new ProgramView();
    private final BreakdownView breakdown = new BreakdownView();

    private EditBox search, nameBox, chantBox, presetName;
    /** Último preset em renomeação visto (para preencher a caixa uma vez quando começa). */
    private int lastRenaming = -1;
    private RuneButton saveSlotBtn, loadSlotBtn, clearSlotBtn, libraryBtn, scrollBtn, presetSaveBtn;
    private final List<AbstractWidget> widgets = new ArrayList<>();

    private final HotZones zones = new HotZones();
    private final Picker picker = new Picker();
    /** Onda 9: editor de transformações (glifo Transformação) — ocupa a aba inteira enquanto aberto. */
    private GrimoireTransform.Panel tfPanel;
    private boolean tfWasOpen;
    private List<Glyph> palette = List.of();
    private String paletteStamp = "";
    private SpellCheck check;
    private String checkStamp = "";
    private List<ResourceLocation> checkedGlyphs = List.of();
    private int seenRevision = -1;
    private int revealIndex = -1;

    private int mouseX, mouseY;
    private int pressChip = -1;
    private boolean draggingChip;
    private Glyph pressPalette;
    private boolean draggingPalette;
    private double pressX, pressY;
    private boolean draggingHue;

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    // =====================================================================================
    //  Layout
    // =====================================================================================
    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.screen = screen;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        Font font = Gfx.font();
        widgets.clear();
        int gap = 6;
        L = Mth.clamp(w * 46 / 100, 150, 236);
        E = w - L - gap;
        lx = x;
        ex = x + L + gap;

        // ---- esquerda: busca (vale para os três modos) e a área da lista/grade
        searchY = y + 16;
        search = new EditBox(font, lx + 1, searchY, L - 2, 13, tr("search"));
        search.setMaxLength(40);
        search.setValue(GrimoireDraft.search);
        search.setResponder(s -> {
            GrimoireDraft.search = s;
            paletteStamp = "";
        });
        updateSearchHint();
        widgets.add(screen.addTabWidget(search));
        listTop = searchY + 16;
        palCols = Math.max(1, (L - 6) / CELL);
        gridX = lx + 2 + Math.max(0, (L - 6 - palCols * CELL) / 2);
        library.bounds(lx, listTop, L, y + h - listTop);
        library.libScroll.set(GrimoireDraft.libraryScroll);
        library.legendScroll.set(GrimoireDraft.legendScroll);
        presets.bounds(lx, listTop, L, y + h - listTop);
        presets.scroll.set(GrimoireDraft.presetScroll);
        // rodapé do modo Presets: nome + "Salvar rascunho" (escondidos nos outros modos)
        int psW = Mth.clamp(L * 40 / 100, 58, 96);
        int pfY = y + h - PresetPanel.FOOTER + 2;
        presetName = new EditBox(font, lx + 1, pfY + 1, L - psW - 4, 13, tr("presets.name"));
        presetName.setMaxLength(SpellPreset.MAX_NAME);
        presetName.setHint(tr("presets.name_hint").withStyle(ChatFormatting.DARK_GRAY));
        presetName.setTooltip(Tooltip.create(tr("presets.name.tip")));
        widgets.add(screen.addTabWidget(presetName));
        presetSaveBtn = addButton(new RuneButton(lx + L - psW, pfY, psW, 15, tr("presets.save"), Gfx.GOLD,
                pixelIcon(PX_PRESET, Gfx.GOLD), this::presetButton).preferLabel());

        // ---- direita: de cima para baixo e de baixo para cima; o programa fica com o que sobra
        destY = y;
        headY = y + 19;
        boxY = y + 31;
        btnY = y + h - 16;
        colorY = btnY - 15;
        fieldsY = colorY - 16;
        statusY = fieldsY - 23;
        boxH = Math.max(40, statusY - 3 - boxY);
        program.bounds(ex, boxY, E, boxH);
        program.scroll.set(GrimoireDraft.programScroll);
        breakdown.bounds(ex, boxY, E, boxH);
        breakdown.scroll.set(GrimoireDraft.detailScroll);

        int nameW = E * 42 / 100;
        nameBox = new EditBox(font, ex + 1, fieldsY, nameW - 4, 13, tr("name"));
        nameBox.setMaxLength(SpellRecipe.MAX_NAME);
        nameBox.setHint(tr("name_hint").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(GrimoireDraft.name);
        nameBox.setResponder(s -> GrimoireDraft.name = s);
        nameBox.setTooltip(Tooltip.create(tr("name.tip")));
        widgets.add(screen.addTabWidget(nameBox));
        chantBox = new EditBox(font, ex + nameW + 1, fieldsY, E - nameW - 2, 13, tr("chant"));
        chantBox.setMaxLength(SpellRecipe.MAX_CHANT);
        chantBox.setHint(tr("chant_hint").withStyle(ChatFormatting.DARK_GRAY));
        chantBox.setValue(GrimoireDraft.chant);
        chantBox.setResponder(s -> GrimoireDraft.chant = s);
        chantBox.setTooltip(Tooltip.create(tr("chant.tip")));
        widgets.add(screen.addTabWidget(chantBox));

        colorW = Math.max(100, E * 48 / 100);
        hueX = ex + 16;
        // os 3 botões do círculo mágico ficam no fim da faixa de cor; a barra de matiz usa o resto
        circX = ex + colorW - 3 * CIRC_STEP + 2;
        hueW = Math.max(10, circX - 6 - hueX);

        // ---- botões: Salvar (com rótulo) + 4 ações com ícone (rótulo quando cabe)
        int saveW = Mth.clamp(E * 36 / 100, 82, 120); // largura mínima que cabe "Salvar no slot" inteiro
        int each = (E - saveW - 8) / 4;
        int bx = ex;
        saveSlotBtn = addButton(new RuneButton(bx, btnY, saveW, 16, tr("save_slot"), Gfx.GOLD, pixelIcon(PX_SAVE, Gfx.GOLD), this::saveToSlot).preferLabel());
        bx += saveW + 2;
        loadSlotBtn = addButton(new RuneButton(bx, btnY, each, 16, tr("load_slot"), Gfx.FRAME_LIGHT, pixelIcon(PX_LOAD, 0xFFD8CCFF), this::loadFromSlot));
        bx += each + 2;
        clearSlotBtn = addButton(new RuneButton(bx, btnY, each, 16, tr("clear_slot"), 0xFFB05A6A, pixelIcon(PX_CLEAR, 0xFFFF9A9A), this::clearSlot));
        bx += each + 2;
        libraryBtn = addButton(new RuneButton(bx, btnY, each, 16, tr("save_library"), 0xFF5C9CE0, pixelIcon(PX_BOOK, 0xFFB8DCFF), this::saveToLibrary));
        bx += each + 2;
        scrollBtn = addButton(new RuneButton(bx, btnY, ex + E - bx, 16, tr("write_scroll"), 0xFFD9A066, pixelIcon(PX_SCROLL, 0xFFFFD9A6), this::writeScroll));

        seenRevision = GrimoireDraft.revision;
        MagicData d = ClientMagic.data();
        refreshPalette(d);
        palScroll.set(GrimoireDraft.paletteScroll);
        updateButtons();
        updatePresetWidgets();
        tfPanel = GrimoireTransform.newPanel();
        if (tfPanel != null) tfPanel.layout(x, y, w, h);
        tfWasOpen = GrimoireTransform.isOpen();
        hideForTransform();
    }

    /** Onda 9: com o editor de transformações aberto, os widgets do Grimório somem (não recebem clique nem foco). */
    private void hideForTransform() {
        if (!GrimoireTransform.isOpen() || tfPanel == null) return;
        for (AbstractWidget wd : widgets) wd.visible = false;
        if (screen.getFocused() instanceof AbstractWidget) screen.setFocused(null);
    }

    /** Abre o editor de transformações por cima do Grimório (glifo Transformação da paleta, selo da situação). */
    private void openTransform() {
        if (tfPanel == null) return;
        GrimoireTransform.setOpen(true);
        tfWasOpen = true;
        picker.close();
        pressPalette = null;
        draggingPalette = false;
        hideForTransform();
        Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 0.85F, 0.7F);
    }

    /** O editor abriu/fechou (ex.: botão "◂ Grimório"): os widgets acompanham. */
    private void syncTransform() {
        boolean open = GrimoireTransform.isOpen() && tfPanel != null;
        if (open == tfWasOpen) return;
        tfWasOpen = open;
        if (open) hideForTransform();
        else updateWidgetVisibility();
    }

    private RuneButton addButton(RuneButton b) {
        widgets.add(screen.addTabWidget(b));
        return b;
    }

    private static Picker.Icon pixelIcon(String[] rows, int color) {
        return (g, ix, iy, size) -> GlyphIcons.pixels(g, rows, ix + (size - rows[0].length()) / 2, iy + (size - rows.length) / 2, 1, color);
    }

    private void updateSearchHint() {
        String key = switch (GrimoireDraft.mode) {
            case GLYPHS -> "search";
            case PRESETS -> "search.presets";
            case LIBRARY -> "search.library";
            case LEGENDS -> "search.legends";
        };
        search.setHint(tr(key).withStyle(ChatFormatting.DARK_GRAY));
    }

    // =====================================================================================
    //  Estado / validação
    // =====================================================================================
    @Override
    public void tick() {
        if (seenRevision != GrimoireDraft.revision) {
            if (!nameBox.getValue().equals(GrimoireDraft.name)) nameBox.setValue(GrimoireDraft.name);
            if (!chantBox.getValue().equals(GrimoireDraft.chant)) chantBox.setValue(GrimoireDraft.chant);
            seenRevision = GrimoireDraft.revision;
        }
        library.tick();
        presets.tick();
        updateButtons();
        updatePresetWidgets();
        hideForTransform();
    }

    /** Análise do rascunho, recalculada só quando a receita ou os dados mudam (o elemento vem dos glifos). */
    private SpellCheck check(MagicData d) {
        String stamp = ClientMagic.syncVersion + "|" + d.level() + "|" + d.circle() + "|" + d.glyphs().size() + "|" + ClientMagic.maxMana;
        // compara a própria lista (não só o hash): a estrutura do programa nunca fica velha
        if (check == null || !stamp.equals(checkStamp) || !checkedGlyphs.equals(GrimoireDraft.glyphs)) {
            checkedGlyphs = List.copyOf(GrimoireDraft.glyphs);
            check = SpellCheck.of(checkedGlyphs, d);
            checkStamp = stamp;
        }
        return check;
    }

    /** Magia atual no slot/versão de destino (ou null). */
    private static SpellRecipe slotRecipe(MagicData d) {
        return d.peekSlot(GrimoireDraft.loadout(d), GrimoireDraft.slot()).get(GrimoireDraft.trigger(d));
    }

    /** Motivo para não poder salvar no slot (null = pode). */
    private Component saveBlockReason(MagicData d) {
        if (GrimoireDraft.glyphs.isEmpty()) return tr("need_glyphs");
        SpellCheck c = check(d);
        if (!c.ok()) return c.result.error();
        boolean ultSlot = GrimoireDraft.slot() == Progression.ULTIMATE_SLOT;
        if (ultSlot && !c.spell().isUltimate()) return Component.translatable("message.isekai_mahou.g_needs_ultimate");
        if (!ultSlot && c.spell().isUltimate()) return Component.translatable("message.isekai_mahou.ultimate_only_g");
        return null;
    }

    private static boolean hasPaper() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getAbilities().instabuild; // v1.0: só no criativo (survival usa o Pergaminho de Receita)
    }

    private void updateButtons() {
        MagicData d = ClientMagic.data();
        Component block = saveBlockReason(d);
        saveSlotBtn.active = block == null;
        saveSlotBtn.tip(block != null ? block : tr("save_slot.tip", destSummary(d)));
        SpellRecipe current = slotRecipe(d);
        clearSlotBtn.active = current != null;
        clearSlotBtn.tip(current != null ? tr("clear_slot.tip", GrimoireDraft.trigger(d).displayName()) : tr("clear_slot.empty"));
        loadSlotBtn.active = current != null && !current.equals(GrimoireDraft.recipe());
        loadSlotBtn.tip(current != null ? tr("load_slot.tip", GlyphIcons.recipeTitle(current)) : tr("clear_slot.empty"));
        boolean full = d.library().size() >= MagicData.MAX_LIBRARY;
        boolean stored = inLibrary(d);
        libraryBtn.active = !GrimoireDraft.glyphs.isEmpty() && !full && !stored;
        libraryBtn.tip(full ? Component.translatable("message.isekai_mahou.library_full")
                : GrimoireDraft.glyphs.isEmpty() ? tr("need_glyphs")
                : stored ? tr("save_library.duplicate") : tr("save_library.tip", d.library().size(), MagicData.MAX_LIBRARY));
        boolean paper = hasPaper();
        scrollBtn.active = !GrimoireDraft.glyphs.isEmpty() && paper;
        scrollBtn.tip(GrimoireDraft.glyphs.isEmpty() ? tr("need_glyphs") : paper ? tr("write_scroll.tip") : tr("write_scroll.no_paper"));
    }

    /** O rascunho já está guardado (igual) na biblioteca? Evita encher a biblioteca de cópias. */
    private static boolean inLibrary(MagicData d) {
        return d.library().contains(GrimoireDraft.recipe());
    }

    private Component destSummary(MagicData d) {
        return Component.empty()
                .append(tr("dest.loadout", GrimoireDraft.loadout(d) + 1).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                .append(keyName(GrimoireDraft.slot()).copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                .append(GrimoireDraft.trigger(d).displayName().withColor(GlyphIcons.triggerColor(GrimoireDraft.trigger(d)) & 0xFFFFFF));
    }

    private static Component keyName(int slot) {
        return CoreKeys.SLOTS[slot].getTranslatedKeyMessage();
    }

    // =====================================================================================
    //  Paleta
    // =====================================================================================
    /** Chave do filtro de "elemento": o id do elemento, ou {@code origin:<categoria>} para glifos de Origem. */
    static String elementKey(Glyph gl) {
        return gl.origin() != null ? GrimoireDraft.ORIGIN_KEY + gl.origin().category().id : gl.element().id;
    }

    /**
     * Glifo que pode entrar numa magia do jogador: Arcano, de Origem (qualquer classe) ou de um elemento despertado.
     * v1.0: o primário vem dos glifos, então não há mais "elemento do slot" restringindo a paleta.
     */
    static boolean usable(Glyph gl, MagicData d) {
        return SpellParser.canUse(Element.ARCANE, gl, d);
    }

    private void refreshPalette(MagicData d) {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        boolean tf = GrimoireTransform.shown(d);
        String stamp = ClientMagic.syncVersion + "|" + d.glyphs().size() + "|" + GrimoireDraft.kindFilter + "|"
                + GrimoireDraft.elementFilter + "|" + GrimoireDraft.search + "|" + lang + "|" + tf;
        if (stamp.equals(paletteStamp)) return;
        paletteStamp = stamp;
        String q = Gfx.norm(GrimoireDraft.search.trim());
        List<Glyph> list = new ArrayList<>();
        for (ResourceLocation id : d.glyphs()) {
            Glyph gl = GlyphRegistry.get(id);
            if (gl == null || !usable(gl, d) || !GlyphIcons.listed(gl, d)) continue;
            if (GrimoireDraft.kindFilter != null && gl.kind() != GrimoireDraft.kindFilter) continue;
            if (!GrimoireDraft.elementFilter.isEmpty() && !GrimoireDraft.elementFilter.contains(elementKey(gl))) continue;
            if (!q.isEmpty() && !matches(gl, q)) continue;
            list.add(gl);
        }
        list.sort(GlyphIcons.ORDER);
        // Onda 9: o glifo Transformação fica fixo no começo da paleta para quem pode moldar transformações — mesmo sem
        // conhecê-lo (moldar nunca exigiu o glifo; só conjurá-lo como magia é que pede aprender, no Círculo 5)
        Glyph tfGlyph = GlyphRegistry.get(TransformForge.FORM_ID);
        if (tf && tfGlyph != null) {
            list.removeIf(gl -> gl.id().equals(TransformForge.FORM_ID));
            if ((GrimoireDraft.kindFilter == null || GrimoireDraft.kindFilter == tfGlyph.kind())
                    && (GrimoireDraft.elementFilter.isEmpty() || GrimoireDraft.elementFilter.contains(elementKey(tfGlyph)))
                    && (q.isEmpty() || matches(tfGlyph, q) || Gfx.norm(tr("transform.search").getString()).contains(q))) {
                list.add(0, tfGlyph);
            }
        }
        palette = list;
        if (GrimoireDraft.selected == null || palette.stream().noneMatch(g -> g.id().equals(GrimoireDraft.selected))) {
            GrimoireDraft.selected = palette.isEmpty() ? null : palette.get(0).id();
        }
    }

    static boolean matches(Glyph gl, String q) {
        return Gfx.norm(gl.displayName().getString()).contains(q)
                || Gfx.norm(gl.description().getString()).contains(q)
                || gl.id().getPath().contains(q)
                || Gfx.norm(GlyphIcons.kindName(gl).getString()).contains(q);
    }

    /**
     * Chips de "elemento" disponíveis: Arcano, cada elemento despertado/com glifos usáveis e, para os glifos de Origem,
     * um chip por CATEGORIA de classe (Mágicas / Físicas / Destreza) de que o jogador conhece algum glifo.
     */
    private static List<String> elementKeys(MagicData d) {
        List<String> keys = new ArrayList<>();
        keys.add(Element.ARCANE.id);
        java.util.Set<MagicPath.Category> cats = java.util.EnumSet.noneOf(MagicPath.Category.class);
        java.util.Set<Element> withGlyphs = java.util.EnumSet.noneOf(Element.class);
        for (ResourceLocation id : d.glyphs()) {
            Glyph gl = GlyphRegistry.get(id);
            if (gl == null) continue;
            if (gl.origin() != null) cats.add(gl.origin().category());
            else if (usable(gl, d)) withGlyphs.add(gl.element());
        }
        for (Element e : Element.values()) {
            if (e == Element.ARCANE) continue;
            if (d.isAwakened(e) || withGlyphs.contains(e)) keys.add(e.id);
        }
        for (MagicPath.Category c : MagicPath.Category.values()) if (cats.contains(c)) keys.add(GrimoireDraft.ORIGIN_KEY + c.id);
        return keys;
    }

    private static int countFor(MagicData d, GlyphKind kind, String key) {
        int c = 0;
        for (ResourceLocation id : d.glyphs()) {
            Glyph gl = GlyphRegistry.get(id);
            if (gl == null || !usable(gl, d) || !GlyphIcons.listed(gl, d)) continue;
            if (kind != null && gl.kind() != kind) continue;
            if (key != null && !key.equals(elementKey(gl))) continue;
            c++;
        }
        return c;
    }

    private void toggleElementFilter(String key, int button, List<String> available) {
        var f = GrimoireDraft.elementFilter;
        if (button == 1 || f.isEmpty()) {
            f.clear();
            f.add(key);
        } else if (f.contains(key)) {
            f.remove(key);
        } else {
            f.add(key);
            if (f.containsAll(available)) f.clear();
        }
        paletteStamp = "";
        Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.2F, 0.25F);
    }

    private int selectedIndex() {
        for (int i = 0; i < palette.size(); i++) if (palette.get(i).id().equals(GrimoireDraft.selected)) return i;
        return -1;
    }

    private void moveSelection(int delta) {
        if (palette.isEmpty()) return;
        int i = selectedIndex();
        i = i < 0 ? 0 : Mth.clamp(i + delta, 0, palette.size() - 1);
        GrimoireDraft.selected = palette.get(i).id();
        palScroll.reveal((i / palCols) * CELL, CELL + 2);
    }

    /** Glifo da paleta sob o mouse (ou null). */
    private Glyph paletteAt(double mx, double my) {
        if (GrimoireDraft.mode != GrimoireDraft.Mode.GLYPHS || !palScroll.over(mx, my)) return null;
        int col = (int) Math.floor((mx - gridX) / CELL);
        int row = (int) Math.floor((my - gridTop - 2 + palScroll.offset()) / CELL);
        if (col < 0 || col >= palCols || row < 0) return null;
        double inX = mx - gridX - col * CELL, inY = my - gridTop - 2 + palScroll.offset() - row * CELL;
        if (inX >= ICON + 2 || inY >= ICON + 2) return null;
        int i = row * palCols + col;
        return i >= 0 && i < palette.size() ? palette.get(i) : null;
    }

    private void addGlyph(Glyph gl) {
        insertGlyph(gl, GrimoireDraft.glyphs.size());
    }

    private void insertGlyph(Glyph gl, int index) {
        MagicData d = ClientMagic.data();
        // Gatilhos de movimento não entram na receita: escolhem a versão do slot
        if (gl instanceof TriggerGlyph t && !t.isPlaceable() && !t.isLink()) {
            for (MoveTrigger mt : MoveTrigger.values()) {
                if (gl.id().equals(mt.glyphId())) {
                    selectTrigger(d, mt);
                    return;
                }
            }
            UiToast.error(Component.translatable("spell_error.isekai_mahou.trigger_not_placeable", gl.displayName()));
            return;
        }
        if (!GrimoireDraft.insert(index, gl.id())) {
            UiToast.error(tr("sequence.full", GrimoireDraft.MAX_EDIT));
            return;
        }
        revealIndex = Math.min(index, GrimoireDraft.glyphs.size() - 1);
        int max = Progression.maxGlyphs(d.level(), d.circle());
        if (GrimoireDraft.glyphs.size() == max + 1) {
            UiToast.show(tr("over_limit", max), Gfx.WARN);
        } else if (gl instanceof LinkGlyph link) {
            UiToast.show(link.linkKind() == LinkGlyph.LinkKind.LEAP ? tr("link_added.leap") : tr("link_added"), LINK_COLOR);
        }
        Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 1.25F + ThreadLocalRandom.current().nextFloat() * 0.35F, 0.7F);
    }

    private void addSelected() {
        if (GrimoireDraft.mode != GrimoireDraft.Mode.GLYPHS) return;
        int i = selectedIndex();
        if (i < 0 && !palette.isEmpty()) i = 0;
        if (i < 0) {
            UiToast.error(tr("palette.empty"));
            return;
        }
        if (isTransformTile(palette.get(i))) {
            openTransform();
            return;
        }
        addGlyph(palette.get(i));
    }

    /** O glifo é o Transformação com o editor ligado (clique abre o editor em vez de pôr na magia)? */
    private boolean isTransformTile(Glyph gl) {
        return tfPanel != null && gl != null && gl.id().equals(TransformForge.FORM_ID) && GrimoireTransform.shown(ClientMagic.data());
    }

    /** Arrastar o glifo Transformação para o programa: só quem o conhece monta uma magia com ele. */
    private boolean canPlaceTransform(Glyph gl) {
        if (!gl.id().equals(TransformForge.FORM_ID) || ClientMagic.data().knowsGlyph(gl.id())) return true;
        UiToast.error(tr("transform.need_known", TransformForge.MIN_CIRCLE));
        return false;
    }

    private void setMode(GrimoireDraft.Mode mode) {
        if (GrimoireDraft.mode == mode) return;
        GrimoireDraft.mode = mode;
        library.disarm();
        presets.disarm();
        picker.close();
        updateSearchHint();
        Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.5F);
    }

    // =====================================================================================
    //  Destino
    // =====================================================================================
    private void selectTrigger(MagicData d, MoveTrigger t) {
        if (!GrimoireDraft.triggerUnlocked(d, t)) {
            UiToast.error(tr("dest.trigger_locked", glyphName(t.glyphId())));
            return;
        }
        GrimoireDraft.setTrigger(t);
        UiToast.info(tr("version_set", t.displayName()));
        Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.3F, 0.25F);
    }

    /** Nome de um glifo por id, tolerando glifo ausente. */
    private static Component glyphName(ResourceLocation id) {
        Glyph g = id == null ? null : GlyphRegistry.get(id);
        if (g != null) return g.displayName();
        return Component.literal(id == null ? "?" : id.getPath());
    }

    // =====================================================================================
    //  Ações
    // =====================================================================================
    private void saveToSlot() {
        MagicData d = ClientMagic.data();
        Component block = saveBlockReason(d);
        if (block != null) {
            UiToast.error(block);
            return;
        }
        int l = GrimoireDraft.loadout(d);
        int s = GrimoireDraft.slot();
        MoveTrigger t = GrimoireDraft.trigger(d);
        SpellRecipe r = GrimoireDraft.recipe();
        PacketDistributor.sendToServer(CorePayloads.SetLoadoutSlot.set(l, s, t, r));
        d.setSlotRecipe(l, s, t, r); // previsão local até a sincronização
        UiToast.ok(tr("saved", GlyphIcons.recipeTitle(r), destSummary(d)));
        Gfx.sound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.2F, 0.6F);
    }

    private void clearSlot() {
        MagicData d = ClientMagic.data();
        if (slotRecipe(d) == null) return;
        int l = GrimoireDraft.loadout(d);
        int s = GrimoireDraft.slot();
        MoveTrigger t = GrimoireDraft.trigger(d);
        PacketDistributor.sendToServer(CorePayloads.SetLoadoutSlot.clear(l, s, t));
        d.setSlotRecipe(l, s, t, null);
        UiToast.info(tr("slot_cleared"));
        Gfx.sound(SoundEvents.BOOK_PUT, 0.8F, 0.6F);
    }

    private void loadFromSlot() {
        SpellRecipe r = slotRecipe(ClientMagic.data());
        if (r == null) return;
        GrimoireDraft.load(r);
        program.scroll.set(0);
        UiToast.ok(tr("loaded_slot"));
        Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 0.9F, 0.7F);
    }

    private void saveToLibrary() {
        MagicData d = ClientMagic.data();
        if (GrimoireDraft.glyphs.isEmpty()) return;
        if (d.library().size() >= MagicData.MAX_LIBRARY) {
            UiToast.error(Component.translatable("message.isekai_mahou.library_full"));
            return;
        }
        if (inLibrary(d)) {
            UiToast.error(tr("save_library.duplicate"));
            return;
        }
        SpellRecipe r = GrimoireDraft.recipe();
        PacketDistributor.sendToServer(new CorePayloads.LibraryEdit(0, 0, r));
        d.addToLibrary(r);
        UiToast.ok(tr("library_saved"));
        Gfx.sound(SoundEvents.VILLAGER_WORK_LIBRARIAN, 1.1F, 0.6F);
    }

    private void writeScroll() {
        if (GrimoireDraft.glyphs.isEmpty()) return;
        if (!hasPaper()) {
            UiToast.error(tr("write_scroll.no_paper"));
            return;
        }
        PacketDistributor.sendToServer(new CorePayloads.LibraryEdit(2, 0, GrimoireDraft.recipe()));
        UiToast.ok(tr("scroll_written"));
        Gfx.sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0F, 0.6F);
    }

    @Override
    public void loadPreset(SpellPreset p) {
        GrimoireDraft.load(p.recipe());
        program.scroll.set(0);
        breakdown.scroll.set(0);
        if (presets.renaming < 0) presetName.setValue(p.name());
        UiToast.ok(tr("presets.loaded", p.name()));
        Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 1.1F, 0.7F);
    }

    /** Botão do rodapé dos Presets: salva o rascunho (ou confirma a renomeação). */
    private void presetButton() {
        if (presets.renaming >= 0) {
            presets.rename(presets.renaming, presetName.getValue());
        } else {
            PresetPanel.saveDraft(presetName.getValue());
        }
        screen.setFocused(null);
    }

    /** Rodapé dos Presets só no modo Presets; o botão vira "Renomear" durante a renomeação. */
    private void updatePresetWidgets() {
        boolean on = GrimoireDraft.mode == GrimoireDraft.Mode.PRESETS;
        presetName.visible = on && (!picker.isOpen() || !picker.intersects(presetName.getX(), presetName.getY(), presetName.getWidth(), presetName.getHeight()));
        presetSaveBtn.visible = on;
        if (!on) return;
        MagicData d = ClientMagic.data();
        boolean renaming = presets.renaming >= 0 && presets.renaming < d.presets().size();
        if (presets.renaming != lastRenaming) {
            // começou a renomear: a caixa recebe o nome atual, já focada, para só editar
            lastRenaming = presets.renaming;
            if (renaming) {
                presetName.setValue(d.presets().get(presets.renaming).name());
                screen.setFocused(presetName);
                presetName.setFocused(true);
            }
        }
        presetSaveBtn.setMessage(renaming ? tr("presets.rename_btn") : tr("presets.save"));
        boolean full = d.presets().size() >= MagicData.MAX_PRESETS;
        String typed = SpellPreset.cleanName(presetName.getValue());
        String effective = typed.isEmpty() ? SpellPreset.cleanName(GlyphIcons.recipeTitle(GrimoireDraft.recipe()).getString()) : typed;
        boolean replaces = !effective.isEmpty() && d.presetIndex(effective) >= 0;
        if (renaming) {
            presetSaveBtn.active = !typed.isEmpty();
            presetSaveBtn.tip(tr("presets.rename_btn.tip", d.presets().get(presets.renaming).name()));
        } else if (GrimoireDraft.glyphs.isEmpty()) {
            presetSaveBtn.active = false;
            presetSaveBtn.tip(tr("need_glyphs"));
        } else if (full && !replaces) {
            presetSaveBtn.active = false;
            presetSaveBtn.tip(tr("presets.full", MagicData.MAX_PRESETS));
        } else {
            presetSaveBtn.active = true;
            presetSaveBtn.tip(replaces ? tr("presets.save.replace", effective) : tr("presets.save.tip", effective, d.presets().size(), MagicData.MAX_PRESETS));
        }
    }

    @Override
    public void loadFromLibrary(SpellRecipe r) {
        GrimoireDraft.load(r);
        program.scroll.set(0);
        breakdown.scroll.set(0);
        UiToast.ok(tr("library.loaded", GlyphIcons.recipeTitle(r)));
        Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.7F);
    }

    @Override
    public void loadLegend(Legends.Legend legend) {
        MagicData d = ClientMagic.data();
        GrimoireDraft.load(legend.recipe());
        program.scroll.set(0);
        breakdown.scroll.set(0);
        int missing = LibraryPanel.missing(legend, d).size();
        if (missing == 0 && d.circle() >= legend.minCircle()) UiToast.ok(tr("legends.loaded", legend.title()));
        else UiToast.show(tr("legends.loaded_missing", legend.title(), missing), Gfx.WARN);
        Gfx.sound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 0.5F);
    }

    private void undo() {
        if (GrimoireDraft.undo()) {
            UiToast.info(tr("undone"));
            Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.5F);
        }
    }

    private void clearDraft() {
        if (GrimoireDraft.isEmpty()) return;
        GrimoireDraft.clear();
        UiToast.info(tr("cleared"));
        Gfx.sound(SoundEvents.BOOK_PUT, 1.0F, 0.6F);
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        mouseX = mx;
        mouseY = my;
        zones.clear();
        syncTransform();
        if (GrimoireTransform.isOpen() && tfPanel != null) {
            // Onda 9: o editor de transformações toma a aba inteira
            List<Component> tip = tfPanel.render(g, mx, my);
            UiToast.render(g, x + w / 2, y + h - 18, w);
            if (tip != null && !tip.isEmpty()) g.renderTooltip(Gfx.font(), GlyphIcons.wrap(tip, 220), mx, my);
            return;
        }
        MagicData d = ClientMagic.data();
        refreshPalette(d);
        SpellCheck c = check(d);
        Element el = c.primary;
        program.layout(GrimoireDraft.glyphs, c.model, c.spell());
        if (revealIndex >= 0) {
            if (!GrimoireDraft.details) program.reveal(revealIndex);
            revealIndex = -1;
        }

        renderLeft(g, d, el, c, mx, my);
        renderDest(g, d, mx, my);
        renderProgramHeader(g, d, c, mx, my);
        boolean dragging = draggingChip || draggingPalette;
        if (GrimoireDraft.details) {
            breakdown.render(g, zones, c, mx, my);
            if (draggingPalette && breakdown.over(mx, my)) {
                Gfx.frame(g, ex + 1, boxY + 1, E - 2, boxH - 2, Gfx.alpha(Gfx.GOLD, 0xC0));
            }
        } else {
            program.render(g, zones, d, c, GrimoireDraft.glyphs, draggingChip ? pressChip : -1, !dragging, mx, my);
        }
        renderStatus(g, d, el, c, mx, my);
        renderColorRow(g, el, c, mx, my);

        GrimoireDraft.paletteScroll = palScroll.offset();
        GrimoireDraft.libraryScroll = library.libScroll.offset();
        GrimoireDraft.legendScroll = library.legendScroll.offset();
        GrimoireDraft.presetScroll = presets.scroll.offset();
        GrimoireDraft.programScroll = program.scroll.offset();
        GrimoireDraft.detailScroll = breakdown.scroll.offset();

        if (dragging) renderDrag(g, mx, my);
        UiToast.render(g, ex + E / 2, statusY - 4, E);
        picker.render(g, zones, mx, my);
        updateWidgetVisibility();
        if (!dragging && !draggingHue) zones.renderTooltip(g, mx, my);
    }

    /** Esconde os widgets cobertos por uma lista aberta (senão eles receberiam o clique antes dela). */
    private void updateWidgetVisibility() {
        for (AbstractWidget wd : widgets) {
            wd.visible = !picker.isOpen() || !picker.intersects(wd.getX(), wd.getY(), wd.getWidth(), wd.getHeight());
        }
        updatePresetWidgets();
        hideForTransform();
    }

    // ------------------------------------------------------------------ esquerda
    private void renderLeft(GuiGraphics g, MagicData d, Element el, SpellCheck c, int mx, int my) {
        List<ChipStrip.Chip> modes = new ArrayList<>();
        int legendCount = Legends.all().size();
        modes.add(new ChipStrip.Chip((gg, ix, iy, s) -> GlyphArt.tab(gg, "glyphs", ix, iy, s), tr("mode.glyphs"), null, MODE_ACCENT,
                GrimoireDraft.mode == GrimoireDraft.Mode.GLYPHS, b -> setMode(GrimoireDraft.Mode.GLYPHS),
                () -> List.of(tr("mode.glyphs").withStyle(ChatFormatting.BOLD), tr("mode.glyphs.tip", countFor(d, null, null)))));
        modes.add(new ChipStrip.Chip(pixelIcon(PX_PRESET, Gfx.GOLD), tr("mode.presets"), null, 0xFFD9A066,
                GrimoireDraft.mode == GrimoireDraft.Mode.PRESETS, b -> setMode(GrimoireDraft.Mode.PRESETS),
                () -> List.of(tr("mode.presets").withStyle(ChatFormatting.BOLD), tr("mode.presets.tip", d.presets().size(), MagicData.MAX_PRESETS))));
        modes.add(new ChipStrip.Chip((gg, ix, iy, s) -> GlyphArt.tab(gg, "grimoire", ix, iy, s), tr("mode.library"), null, 0xFF5C9CE0,
                GrimoireDraft.mode == GrimoireDraft.Mode.LIBRARY, b -> setMode(GrimoireDraft.Mode.LIBRARY),
                () -> List.of(tr("mode.library").withStyle(ChatFormatting.BOLD), tr("mode.library.tip", d.library().size(), MagicData.MAX_LIBRARY))));
        modes.add(new ChipStrip.Chip((gg, ix, iy, s) -> GlyphArt.tab(gg, "legends", ix, iy, s), tr("mode.legends"), null, Gfx.GOLD,
                GrimoireDraft.mode == GrimoireDraft.Mode.LEGENDS, b -> setMode(GrimoireDraft.Mode.LEGENDS),
                () -> List.of(tr("mode.legends").withStyle(ChatFormatting.BOLD), tr("mode.legends.tip", legendCount))));
        ChipStrip.render(g, zones, lx, y, L, modes, ChipStrip.plan(modes, L, 1), mx, my);

        String q = Gfx.norm(GrimoireDraft.search.trim());
        switch (GrimoireDraft.mode) {
            case PRESETS -> presets.render(g, zones, d, q, mx, my);
            case LIBRARY -> library.renderLibrary(g, zones, d, el, q, mx, my);
            case LEGENDS -> library.renderLegends(g, zones, d, q, mx, my);
            default -> renderPalette(g, d, el, c, mx, my);
        }
    }

    private void renderPalette(GuiGraphics g, MagicData d, Element el, SpellCheck c, int mx, int my) {
        Font font = Gfx.font();
        int cy = listTop;
        // ---- abas de tipo (ícone + rótulo)
        List<ChipStrip.Chip> kinds = new ArrayList<>();
        kinds.add(new ChipStrip.Chip(null, tr("filter.all"), null, Gfx.FRAME_LIGHT, GrimoireDraft.kindFilter == null,
                b -> setKind(null), () -> List.of(tr("filter.all").withStyle(ChatFormatting.BOLD), tr("filter.count", countFor(d, null, null)).withStyle(ChatFormatting.GRAY))));
        for (GlyphKind k : GlyphIcons.KIND_ORDER) {
            boolean trig = k == GlyphKind.TRIGGER;
            int accent = GlyphArt.kindColor(k, trig);
            kinds.add(new ChipStrip.Chip((gg, ix, iy, s) -> GlyphIcons.kindIcon(gg, k, trig, ix, iy, s, true), kindFilterName(k),
                    kindFilterShort(k), accent, GrimoireDraft.kindFilter == k, b -> setKind(k),
                    () -> List.of(kindFilterName(k).withColor(accent & 0xFFFFFF).withStyle(ChatFormatting.BOLD),
                            tr("filter." + kindKey(k) + ".tip").withStyle(ChatFormatting.GRAY),
                            tr("filter.count", countFor(d, k, null)).withStyle(ChatFormatting.DARK_GRAY))));
        }
        cy += ChipStrip.render(g, zones, lx, cy, L, kinds, ChipStrip.plan(kinds, L, 2), mx, my) + 3;

        // ---- chips de elemento (runa), multisseleção; filtros de elementos que não valem mais (trocou o destino) saem
        List<String> keys = elementKeys(d);
        if (GrimoireDraft.elementFilter.retainAll(keys)) paletteStamp = "";
        List<ChipStrip.Chip> elems = new ArrayList<>();
        boolean all = GrimoireDraft.elementFilter.isEmpty();
        elems.add(new ChipStrip.Chip(null, tr("filter.all"), null, Gfx.FRAME_LIGHT, all, b -> {
            GrimoireDraft.elementFilter.clear();
            paletteStamp = "";
            Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.2F, 0.25F);
        }, () -> List.of(tr("filter.all_elements").withStyle(ChatFormatting.BOLD), tr("filter.elements.tip").withStyle(ChatFormatting.GRAY))));
        for (String key : keys) {
            boolean origin = key.startsWith(GrimoireDraft.ORIGIN_KEY);
            MagicPath.Category cat = origin ? MagicPath.Category.byId(key.substring(GrimoireDraft.ORIGIN_KEY.length())) : null;
            Element e = origin ? null : Element.byId(key);
            Component name = origin ? cat.displayName() : Component.literal(e.displayName().getString());
            int accent = origin ? GlyphIcons.categoryColor(cat) : e.argb();
            Picker.Icon icon = origin ? (gg, ix, iy, s) -> GlyphIcons.categoryIcon(gg, cat, ix, iy, s)
                    : (gg, ix, iy, s) -> GlyphArt.element(gg, e, ix, iy, s);
            boolean on = !all && GrimoireDraft.elementFilter.contains(key);
            elems.add(new ChipStrip.Chip(icon, name, null, accent, on, b -> toggleElementFilter(key, b, keys),
                    () -> elementChipTip(d, el, key, e)));
        }
        // painel alto: até 2 linhas para os nomes dos elementos caberem inteiros; baixo: 1 linha (runas + dica)
        cy += ChipStrip.render(g, zones, lx, cy, L, elems, ChipStrip.plan(elems, L, h >= 200 ? 2 : 1), mx, my) + 2;

        // ---- grade de glifos
        gridTop = cy;
        palScroll.bounds(lx, gridTop, L, y + h - gridTop);
        palScroll.content((palette.size() + palCols - 1) / palCols * CELL + 4);
        int off = palScroll.offset();
        int allowed = SpellCheck.allowedExtras(el, c.maxElements);
        g.enableScissor(palScroll.x, palScroll.y, palScroll.x + palScroll.w, palScroll.y + palScroll.h);
        for (int i = 0; i < palette.size(); i++) {
            Glyph gl = palette.get(i);
            int px = gridX + (i % palCols) * CELL + 1;
            int py = gridTop + 2 + (i / palCols) * CELL - off + 1;
            if (py + ICON < gridTop || py > gridTop + palScroll.h) continue;
            boolean hov = Gfx.in(mx, my, px - 1, py - 1, ICON + 2, ICON + 2) && palScroll.over(mx, my) && !draggingChip && !draggingPalette;
            boolean tfTile = isTransformTile(gl);
            int flags = (hov ? GlyphIcons.HOVER : 0)
                    | (gl.id().equals(GrimoireDraft.selected) ? GlyphIcons.SELECTED : 0)
                    | (d.circle() < gl.minCircle() && !tfTile ? GlyphIcons.LOCKED : 0)
                    | (wouldExceedElements(gl, el, c, allowed) ? GlyphIcons.WARN : 0)
                    | (GlyphIcons.isLesson(gl, d) ? GlyphIcons.LESSON : 0)
                    | (draggingPalette && gl == pressPalette ? GlyphIcons.DIM : 0);
            GlyphIcons.draw(g, gl, px, py, ICON, flags);
            // moldura violeta: este glifo abre o editor de transformações (com os Votos)
            if (tfTile) Gfx.frame(g, px - 1, py - 1, ICON + 2, ICON + 2, TF_COLOR);
            if (d.knowsGlyph(gl.id())) GlyphIcons.masteryBar(g, d, gl, px, py, ICON);
            zones.addClipped(px - 1, py - 1, ICON + 2, ICON + 2, palScroll.x, palScroll.y, palScroll.w, palScroll.h,
                    null, () -> paletteTip(gl, d, el, c));
        }
        g.disableScissor();
        palScroll.renderBar(g);
        if (palette.isEmpty()) {
            Component msg = d.glyphs().isEmpty() ? tr("palette.none_known") : tr("palette.empty");
            int ty = gridTop + 8;
            for (FormattedCharSequence line : font.split(msg, L - 10)) {
                g.drawString(font, line, lx + (L - font.width(line)) / 2, ty, Gfx.TEXT_FAINT, false);
                ty += 10;
            }
        }
    }

    private void setKind(GlyphKind k) {
        GrimoireDraft.kindFilter = k;
        paletteStamp = "";
        Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.2F, 0.25F);
    }

    /** O glifo traria um elemento NOVO além do limite do Círculo? */
    private static boolean wouldExceedElements(Glyph gl, Element el, SpellCheck c, int allowed) {
        if (gl.origin() != null || gl.element() == Element.ARCANE || el.canUseGlyphOf(gl.element())) return false;
        return !c.extras.contains(gl.element()) && c.extras.size() + 1 > allowed;
    }

    private List<Component> elementChipTip(MagicData d, Element el, String key, Element e) {
        List<Component> t = new ArrayList<>();
        if (e == null) {
            MagicPath.Category cat = MagicPath.Category.byId(key.substring(GrimoireDraft.ORIGIN_KEY.length()));
            t.add(tr("filter.origin_cat", cat.displayName()).withStyle(ChatFormatting.BOLD).withColor(GlyphIcons.categoryColor(cat) & 0xFFFFFF));
            t.add(tr("filter.origin.tip").withStyle(ChatFormatting.GRAY));
            if (d.path() != MagicPath.NONE && d.path().category() == cat) {
                t.add(tr("filter.origin.own", d.path().displayName()).withColor(Gfx.GOOD & 0xFFFFFF));
            }
            t.add(tr("filter.origin.lesson").withColor(0xE7A6FF));
        } else {
            t.add(e.displayName().withStyle(ChatFormatting.BOLD));
            if (e != Element.ARCANE) {
                t.add(tr("dest.element_level", Math.max(0, d.elementLevel(e))).withStyle(ChatFormatting.GRAY));
                if (e == el || el.canUseGlyphOf(e)) t.add(tr("filter.primary").withColor(Gfx.GOLD & 0xFFFFFF));
                else t.add(tr("filter.extra").withColor(Gfx.MANA & 0xFFFFFF));
            } else {
                t.add(tr("filter.arcane").withStyle(ChatFormatting.GRAY));
            }
        }
        t.add(tr("filter.count", countFor(d, GrimoireDraft.kindFilter, key)).withStyle(ChatFormatting.GRAY));
        t.add(tr("filter.chip_hint").withStyle(ChatFormatting.DARK_GRAY));
        return t;
    }

    private List<Component> paletteTip(Glyph gl, MagicData d, Element el, SpellCheck c) {
        if (isTransformTile(gl)) {
            List<Component> t = new ArrayList<>();
            t.add(tr("transform.tile").withColor(TF_COLOR & 0xFFFFFF).withStyle(ChatFormatting.BOLD));
            t.add(tr("transform.tile.tip").withStyle(ChatFormatting.GRAY));
            t.add(tr(d.knowsGlyph(gl.id()) ? "transform.tile.cast" : "transform.tile.cast_locked", TransformForge.MIN_CIRCLE)
                    .withStyle(ChatFormatting.DARK_GRAY));
            t.add(tr("transform.tile.click").withStyle(ChatFormatting.DARK_GRAY));
            return t;
        }
        List<Component> lines = GlyphIcons.tooltip(gl, d, true);
        if (gl.origin() == null && gl.element() != Element.ARCANE && !el.canUseGlyphOf(gl.element()) && !c.extras.contains(gl.element())) {
            int allowed = SpellCheck.allowedExtras(el, c.maxElements);
            if (c.extras.size() + 1 > allowed) {
                lines.add(tr("palette.element_limit", gl.element().displayName(), c.maxElements).withColor(0xFFA040));
            } else {
                lines.add(tr("palette.new_element", gl.element().displayName()).withColor(Gfx.MANA & 0xFFFFFF));
            }
        }
        boolean moveTrigger = gl instanceof TriggerGlyph t && !t.isPlaceable() && !t.isLink();
        lines.add(tr(moveTrigger ? "palette.hint_trigger" : "palette.hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static String kindKey(GlyphKind kind) {
        return switch (kind) {
            case FORM -> "form";
            case EFFECT -> "effect";
            case AUGMENT -> "augment";
            case TRIGGER -> "trigger";
            case LAW -> "law";
        };
    }

    private static MutableComponent kindFilterName(GlyphKind kind) {
        return tr("filter." + kindKey(kind));
    }

    private static MutableComponent kindFilterShort(GlyphKind kind) {
        return tr("filter." + kindKey(kind) + ".short");
    }

    // ------------------------------------------------------------------ destino
    /**
     * "Salvar em: Loadout [1..9] | Z X C G | versão" (v1.0: 9 loadouts fixos, sem elemento — o elemento vem dos
     * glifos). Escolhe o arranjo mais completo que cabe (ver {@code modes}); as caixinhas encolhem de 15 até 11px
     * antes de passar para o próximo arranjo.
     */
    private void renderDest(GuiGraphics g, MagicData d, int mx, int my) {
        Font font = Gfx.font();
        int lc = d.loadoutCount();
        Component label = tr("dest.save_in");
        int labelW = font.width(label) + 5;
        Component word = tr("dest.loadout_word");
        int wordW = font.width(word) + 4;
        int box = 11;
        boolean compact = true, showLabel = false, showWord = false;
        // {rótulo "Salvar em", palavra "Loadout", lista compacta}: do mais completo ao mais enxuto — o texto
        // "Salvar em: Loadout N" tem prioridade sobre as 9 caixinhas (a lista abre com um clique)
        boolean[][] modes = {{true, true, false}, {true, false, false}, {true, true, true}, {true, false, true},
                {false, false, false}, {false, false, true}};
        search:
        for (boolean[] mode : modes) {
            boolean lab = mode[0];
            boolean wd = mode[1];
            boolean cp = mode[2];
            for (int b = 15; b >= 11; b--) {
                if ((lab ? labelW : 0) + (wd ? wordW : 0) + destControlsW(b, lc, cp) <= E) {
                    box = b;
                    compact = cp;
                    showLabel = lab;
                    showWord = wd;
                    break search;
                }
            }
        }
        int step = box + 1;
        int verW = box + 8;
        int accent = check(d).primary.argb();
        int bx = ex;
        int by = destY;
        if (showLabel) {
            g.drawString(font, label, bx, by + 4, Gfx.TEXT_DIM, false);
            zones.add(bx, by, labelW, 16, null, () -> List.of(tr("dest.save_in").withStyle(ChatFormatting.BOLD), tr("dest.save_in.tip").withStyle(ChatFormatting.GRAY)));
            bx += labelW;
        }
        if (showWord) {
            g.drawString(font, word, bx, by + 4, Gfx.GOLD, false);
            bx += wordW;
        }

        // ---- loadouts: caixinhas 1..9, ou uma lista "N ▾" quando não cabem
        int loadout = GrimoireDraft.loadout(d);
        int activeL = d.activeLoadout();
        if (compact) {
            boolean lh = Gfx.in(mx, my, bx, by, LOADOUT_DROP_W, 16);
            Gfx.toggle(g, bx, by, LOADOUT_DROP_W, 16, true, lh, accent);
            String s = String.valueOf(loadout + 1);
            g.drawString(font, s, bx + 4, by + 4, 0xFFFFFFFF, false);
            if (loadout == activeL) g.fill(bx + 2, by + 1, bx + 4, by + 3, Gfx.GOLD);
            g.drawString(font, "▾", bx + LOADOUT_DROP_W - 8, by + 4, lh ? 0xFFFFFFFF : Gfx.TEXT_DIM, false);
            final int lbx = bx;
            zones.add(bx, by, LOADOUT_DROP_W, 16, b -> openLoadoutPicker(d, lc, lbx, by), () -> {
                List<Component> t = loadoutTip(d, loadout);
                t.add(tr("dest.loadout.click").withStyle(ChatFormatting.DARK_GRAY));
                return t;
            });
            bx += LOADOUT_DROP_W + 1;
        } else {
            for (int l = 0; l < lc; l++) {
                final int fl = l;
                boolean on = l == loadout;
                boolean hov = Gfx.in(mx, my, bx, by, box, 16);
                Gfx.toggle(g, bx, by, box, 16, on, hov, accent);
                String s = String.valueOf(l + 1);
                g.drawString(font, s, bx + (box - font.width(s)) / 2 + 1, by + 4, on ? 0xFFFFFFFF : d.loadoutEmpty(l) ? Gfx.TEXT_FAINT : Gfx.TEXT_DIM, false);
                if (l == activeL) g.fill(bx + box - 3, by + 1, bx + box - 1, by + 3, Gfx.GOLD);
                zones.add(bx, by, box, 16, b -> selectLoadout(fl), () -> loadoutTip(d, fl));
                bx += step;
            }
        }
        g.fill(bx + 1, by + 2, bx + 2, by + 14, Gfx.FRAME);
        bx += 5;

        // ---- slots Z X C G
        int slot = GrimoireDraft.slot();
        for (int s = 0; s < Progression.SLOTS; s++) {
            final int fs = s;
            boolean on = s == slot;
            boolean ult = s == Progression.ULTIMATE_SLOT;
            boolean hov = Gfx.in(mx, my, bx, by, box, 16);
            Gfx.toggle(g, bx, by, box, 16, on, hov, ult ? Gfx.GOLD : accent);
            String key = keyName(s).getString();
            int kc = on ? 0xFFFFFFFF : ult ? Gfx.GOLD : Gfx.TEXT_DIM;
            int kw = font.width(key);
            if (kw <= box - 3) {
                g.drawString(font, key, bx + (box - kw) / 2 + 1, by + 4, kc, false);
            } else {
                // tecla com nome comprido ("Mouse 4"): letra miúda para caber inteira
                float sc = Math.max(0.45F, (box - 3) / (float) kw);
                Gfx.small(g, key, bx + (box - kw * sc) / 2F + 0.5F, by + (16 - 8 * sc) / 2F, kc, sc);
            }
            if (!d.peekSlot(loadout, s).isEmpty()) g.fill(bx + box - 3, by + 13, bx + box - 1, by + 15, Gfx.GOOD);
            zones.add(bx, by, box, 16, b -> {
                GrimoireDraft.slot = fs;
                Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.3F, 0.25F);
            }, () -> slotTip(d, loadout, fs));
            bx += step;
        }
        g.fill(bx + 1, by + 2, bx + 2, by + 14, Gfx.FRAME);
        bx += 5;

        // ---- versão de movimento
        MoveTrigger trig = GrimoireDraft.trigger(d);
        boolean vh = Gfx.in(mx, my, bx, by, verW, 16);
        Gfx.toggle(g, bx, by, verW, 16, false, vh, GlyphIcons.triggerColor(trig));
        int vi = Math.min(12, verW - 9); // ícone encolhe junto com a caixinha para não encostar no "▾"
        GlyphIcons.triggerIcon(g, trig, bx + 2, by + (16 - vi) / 2, vi, true);
        g.drawString(font, "▾", bx + verW - 6, by + 4, vh ? 0xFFFFFFFF : Gfx.TEXT_DIM, false);
        final int vbx = bx;
        zones.add(bx, by, verW, 16, b -> openVersionPicker(d, vbx, by, verW), () -> versionTip(d, trig));
    }

    /** Largura de loadouts | slots | versão para o tamanho de caixinha informado ({@code compact}: loadout em lista). */
    private static int destControlsW(int box, int loadouts, boolean compact) {
        int step = box + 1;
        int loadoutW = compact ? LOADOUT_DROP_W : loadouts * step - 1;
        return loadoutW + 6 + Progression.SLOTS * step - 1 + 6 + box + 8;
    }

    private void selectLoadout(int l) {
        if (l == GrimoireDraft.loadout(ClientMagic.data())) return;
        GrimoireDraft.setLoadout(l);
        Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.3F, 0.25F);
    }

    private List<Component> loadoutTip(MagicData d, int l) {
        List<Component> t = new ArrayList<>();
        t.add(tr("dest.loadout", l + 1).withStyle(ChatFormatting.BOLD));
        if (l == d.activeLoadout()) t.add(tr("dest.loadout_active").withColor(Gfx.GOLD & 0xFFFFFF));
        boolean any = false;
        for (int s = 0; s < Progression.SLOTS; s++) {
            SpellRecipe r = d.peekSlot(l, s).main();
            if (r == null) continue;
            any = true;
            t.add(Component.empty().append(keyName(s).copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(GlyphIcons.recipeTitle(r).copy().withColor(GlyphIcons.recipeColor(r, GlyphIcons.elementOf(r)) & 0xFFFFFF)));
        }
        if (!any) t.add(tr("dest.loadout_empty").withStyle(ChatFormatting.DARK_GRAY));
        t.add(tr("dest.loadout.tip", CoreKeys.LOADOUT_MODIFIER.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY));
        return t;
    }

    private void openLoadoutPicker(MagicData d, int lc, int ax, int ay) {
        List<Picker.Option> opts = new ArrayList<>();
        int current = GrimoireDraft.loadout(d);
        int activeL = d.activeLoadout();
        int accent = check(d).primary.argb();
        for (int l = 0; l < lc; l++) {
            final int fl = l;
            MutableComponent label = tr("dest.loadout", l + 1);
            if (l == activeL) label.append(Component.literal(" ★").withColor(Gfx.GOLD & 0xFFFFFF));
            final boolean ff = !d.loadoutEmpty(l);
            opts.add(new Picker.Option((g, ix, iy, s) -> {
                MahouUi.roundRect(g, ix, iy, s, s, MahouUi.darken(accent, 0.3F), MahouUi.darken(accent, 0.65F));
                MahouUi.roundOutline(g, ix, iy, s, s, MahouUi.darken(accent, 0.8F));
                String n = String.valueOf(fl + 1);
                g.drawString(Gfx.font(), n, ix + (s - Gfx.font().width(n)) / 2 + 1, iy + (s - 8) / 2 + 1, 0xFFFFFFFF, true);
                if (ff) g.fill(ix + s - 3, iy + s - 3, ix + s - 1, iy + s - 1, Gfx.GOOD);
            }, label, null, accent, true, l == current, () -> selectLoadout(fl), () -> loadoutTip(d, fl)));
        }
        picker.toggle(ax, ay, LOADOUT_DROP_W, 16, false, x, x + w, y + 16, statusY - 2, tr("dest.pick_loadout"), opts);
    }

    private void openVersionPicker(MagicData d, int ax, int ay, int aw) {
        List<Picker.Option> opts = new ArrayList<>();
        MoveTrigger cur = GrimoireDraft.trigger(d);
        for (MoveTrigger t : MoveTrigger.values()) {
            boolean unlocked = GrimoireDraft.triggerUnlocked(d, t);
            opts.add(new Picker.Option((g, ix, iy, s) -> GlyphIcons.triggerIcon(g, t, ix, iy, s, unlocked), t.displayName(), null,
                    GlyphIcons.triggerColor(t), unlocked, t == cur, () -> selectTrigger(d, t), () -> versionTip(d, t)));
        }
        picker.toggle(ax, ay, aw, 16, false, x, x + w, y + 16, statusY - 2, tr("dest.pick_version"), opts);
    }

    private void openColorPicker(Element el, int ax, int ay) {
        List<Picker.Option> opts = new ArrayList<>();
        opts.add(new Picker.Option((g, ix, iy, s) -> swatch(g, el.argb(), true, ix, iy, s), tr("color.auto_short"), Component.literal("A"), el.argb(), true,
                GrimoireDraft.color < 0, () -> setColor(-1), () -> List.of(tr("color.auto"))));
        for (Element e : Element.values()) {
            int rgb = e.color;
            opts.add(new Picker.Option((g, ix, iy, s) -> swatch(g, e.argb(), false, ix, iy, s), Component.literal(e.displayName().getString()),
                    null, e.argb(), true, GrimoireDraft.color == rgb, () -> setColor(rgb), () -> List.of(tr("color.element", e.displayName()))));
        }
        picker.toggle(ax, ay, 12, 12, true, x, x + w, listTop, colorY - 2, tr("color.pick"), opts);
    }

    private static void swatch(GuiGraphics g, int argb, boolean auto, int sx, int sy, int s) {
        MahouUi.roundRect(g, sx, sy, s, s, MahouUi.lighten(argb, 0.25F), MahouUi.darken(argb, 0.35F));
        MahouUi.roundOutline(g, sx, sy, s, s, 0xFF000000);
        if (auto) Gfx.small(g, "A", sx + s / 2F - 1.5F, sy + s / 2F - 2.5F, Gfx.luminance(argb) > 0.6F ? 0xFF000000 : 0xFFFFFFFF, 0.75F);
    }

    private List<Component> versionTip(MagicData d, MoveTrigger t) {
        List<Component> tip = new ArrayList<>();
        int tc = GlyphIcons.triggerColor(t);
        tip.add(tr("dest.trigger", t.displayName()).withColor(tc & 0xFFFFFF).withStyle(ChatFormatting.BOLD));
        tip.add(tr("dest.trigger." + t.id).withStyle(ChatFormatting.GRAY));
        if (!GrimoireDraft.triggerUnlocked(d, t)) tip.add(tr("dest.trigger_locked", glyphName(t.glyphId())).withStyle(ChatFormatting.RED));
        if (d.peekSlot(GrimoireDraft.loadout(d), GrimoireDraft.slot()).get(t) != null) tip.add(tr("dest.trigger_exists").withStyle(ChatFormatting.GREEN));
        return tip;
    }

    private List<Component> slotTip(MagicData d, int loadout, int s) {
        List<Component> t = new ArrayList<>();
        t.add(tr("dest.slot", keyName(s)).withStyle(ChatFormatting.BOLD));
        if (s == Progression.ULTIMATE_SLOT) t.add(tr("dest.slot_ultimate").withColor(Gfx.GOLD & 0xFFFFFF));
        SpellSlot ss = d.peekSlot(loadout, s);
        if (ss.isEmpty()) {
            t.add(tr("dest.slot_empty").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (MoveTrigger mt : MoveTrigger.values()) {
                SpellRecipe r = ss.get(mt);
                if (r == null) continue;
                t.add(Component.empty().append(mt.displayName().withColor(GlyphIcons.triggerColor(mt) & 0xFFFFFF))
                        .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY)).append(GlyphIcons.recipeTitle(r)));
            }
        }
        return t;
    }

    // ------------------------------------------------------------------ programa
    private void renderProgramHeader(GuiGraphics g, MagicData d, SpellCheck c, int mx, int my) {
        Font font = Gfx.font();
        int n = GrimoireDraft.glyphs.size();
        int max = c.maxGlyphs;
        int buttonsX = ex + E - 36;
        // abas da caixa: Programa | Detalhes (dano e custo por parte)
        int cx = headTab(g, ex, tr("program"), !GrimoireDraft.details, mx, my, () -> setDetails(false),
                () -> List.of(tr("program").withStyle(ChatFormatting.BOLD), tr("view.program.tip").withStyle(ChatFormatting.GRAY)));
        cx = headTab(g, cx + 4, tr("details"), GrimoireDraft.details, mx, my, () -> setDetails(true),
                () -> List.of(tr("details").withStyle(ChatFormatting.BOLD), tr("view.details.tip").withStyle(ChatFormatting.GRAY),
                        tr("view.details.key").withStyle(ChatFormatting.DARK_GRAY)));
        cx += 6;
        String cnt = n + "/" + max;
        if (cx + font.width(cnt) <= buttonsX - 2) {
            g.drawString(font, cnt, cx, headY + 1, n > max ? Gfx.BAD : Gfx.TEXT_DIM, false);
            zones.add(cx, headY, font.width(cnt), 10, null, () -> List.of(tr("stat.complexity").withStyle(ChatFormatting.BOLD),
                    tr("stat.complexity.tip", d.level(), d.circle(), max), tr("stat.complexity.grow").withStyle(ChatFormatting.GRAY)));
            cx += font.width(cnt) + 6;
        }
        // magia válida: o número de estágios vem do próprio ParsedSpell; senão, da estrutura do rascunho
        int stages = c.ok() ? c.spell().stageCount() : c.model.stages.size();
        if (stages > 1) {
            Component st = tr("program.stages", stages);
            if (cx + font.width(st) < buttonsX - 4) g.drawString(font, st, cx, headY + 1, LINK_COLOR, false);
        }
        int ix = ex + E - 11;
        iconButton(g, ix, headY, "✕", !GrimoireDraft.isEmpty(), mx, my, this::clearDraft, () -> List.of(tr("clear")));
        ix -= 12;
        iconButton(g, ix, headY, "↶", GrimoireDraft.canUndo(), mx, my, this::undo, () -> List.of(tr("undo")));
        ix -= 12;
        iconButton(g, ix, headY, "?", true, mx, my, null, () -> List.of(
                tr("help").withStyle(ChatFormatting.GOLD), tr("help.1"), tr("help.2"), tr("help.6").withColor(LINK_COLOR & 0xFFFFFF),
                tr("help.3"), tr("help.4"), tr("help.8").withColor(Gfx.WARN & 0xFFFFFF), tr("help.5"), tr("help.7").withStyle(ChatFormatting.GRAY)));
    }

    /** Aba de texto do cabeçalho (sublinhada quando ativa); devolve o x depois dela. */
    private int headTab(GuiGraphics g, int tx, Component text, boolean on, int mx, int my, Runnable action, Supplier<List<Component>> tip) {
        Font font = Gfx.font();
        Component shown = on ? text.copy().withStyle(ChatFormatting.BOLD) : text;
        // largura sempre a do negrito: trocar de aba não empurra a outra
        int tw = font.width(text.copy().withStyle(ChatFormatting.BOLD));
        boolean hov = !on && Gfx.in(mx, my, tx - 1, headY - 1, tw + 2, 11);
        g.drawString(font, shown, tx, headY + 1, on ? Gfx.GOLD : hov ? 0xFFFFFFFF : Gfx.TEXT_DIM, false);
        if (on) g.fill(tx, headY + 10, tx + tw, headY + 11, Gfx.GOLD);
        else if (hov) g.fill(tx, headY + 10, tx + tw, headY + 11, Gfx.FRAME_LIGHT);
        zones.add(tx - 1, headY - 1, tw + 2, 12, on ? null : b -> action.run(), tip);
        return tx + tw;
    }

    private void setDetails(boolean on) {
        if (GrimoireDraft.details == on) return;
        GrimoireDraft.details = on;
        Gfx.sound(SoundEvents.BOOK_PAGE_TURN, on ? 1.35F : 1.15F, 0.5F);
    }

    private void iconButton(GuiGraphics g, int bx, int by, String s, boolean enabled, int mx, int my, Runnable action,
                            Supplier<List<Component>> tip) {
        Font font = Gfx.font();
        boolean hov = enabled && Gfx.in(mx, my, bx, by, 11, 11);
        Gfx.round(g, bx, by, 11, 11, hov ? 0xFF3A3058 : 0xFF1C1828);
        Gfx.frame(g, bx, by, 11, 11, hov ? Gfx.FRAME_LIGHT : Gfx.FRAME);
        g.drawString(font, s, bx + (12 - font.width(s)) / 2, by + 2, enabled ? (hov ? 0xFFFFFFFF : Gfx.TEXT) : Gfx.TEXT_FAINT, false);
        zones.add(bx, by, 11, 11, action == null || !enabled ? null : b -> action.run(), tip);
    }

    // ------------------------------------------------------------------ situação
    private void renderStatus(GuiGraphics g, MagicData d, Element el, SpellCheck c, int mx, int my) {
        Font font = Gfx.font();
        int sy = statusY;
        int n = GrimoireDraft.glyphs.size();
        if (n == 0) {
            List<FormattedCharSequence> lines = font.split(tr("status.empty"), E - 4);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), ex + 2, sy + 1 + i * 10, Gfx.TEXT_FAINT, false);
            return;
        }
        if (!c.ok()) {
            Component err = Component.empty().append(c.result.error());
            List<FormattedCharSequence> lines = font.split(err, E - 14);
            g.drawString(font, "✖", ex + 1, sy + 1, Gfx.BAD, false);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                FormattedCharSequence line = lines.get(i);
                if (i == 1 && lines.size() > 2) line = MahouUi.fit(font, Component.literal(lastLines(lines, 1)), E - 14);
                g.drawString(font, line, ex + 12, sy + 1 + i * 10, 0xFFFFB8B8, false);
            }
            // erro curto: a dica de como resolver vai direto na 2ª linha (inteira na dica do mouse)
            Component hintLine = c.hint();
            if (lines.size() == 1 && hintLine != null) {
                g.drawString(font, MahouUi.fit(font, hintLine, E - 14), ex + 12, sy + 11, MahouUi.alpha(Gfx.GOLD, 0xD0), false);
            }
            zones.add(ex, sy, E, 20, null, () -> {
                List<Component> t = new ArrayList<>();
                t.add(tr("status.invalid").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                t.add(c.result.error());
                Component hint = c.hint();
                if (hint != null) t.add(hint.copy().withStyle(ChatFormatting.GOLD));
                if (!c.culprits.isEmpty()) t.add(tr("status.culprit").withStyle(ChatFormatting.DARK_GRAY));
                return t;
            });
            return;
        }
        // válida: estatísticas na 1ª linha, avisos (ou "pronta") na 2ª
        ParsedSpell sp = c.spell();
        SpellBreakdown b = breakdown.breakdown(sp);
        int cx = ex + 1;
        int manaCol = sp.manaCost() > ClientMagic.maxMana ? Gfx.BAD : b != null && b.heavyCopies() ? Gfx.WARN : Gfx.MANA;
        cx = stat(g, cx, sy + 1, PX_MANA, String.valueOf(sp.manaCost()), manaCol, bb -> setDetails(!GrimoireDraft.details), () -> manaTip(sp));
        if (b != null && b.damage() > 0) {
            cx = stat(g, cx, sy + 1, PX_DMG, "≈" + SpellBreakdown.num(b.damage()), 0xFFFF7A6B, bb -> setDetails(true),
                    () -> List.of(tr("stat.damage").withStyle(ChatFormatting.BOLD), tr("stat.damage.tip").withStyle(ChatFormatting.GRAY),
                            tr("stat.open_details").withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (b != null && b.heal() > 0) {
            cx = stat(g, cx, sy + 1, PX_HEAL, "≈" + SpellBreakdown.num(b.heal()), 0xFF7DFF9A, bb -> setDetails(true),
                    () -> List.of(tr("stat.heal").withStyle(ChatFormatting.BOLD), tr("stat.heal.tip").withStyle(ChatFormatting.GRAY),
                            tr("stat.open_details").withStyle(ChatFormatting.DARK_GRAY)));
        }
        cx = stat(g, cx, sy + 1, PX_CAST, sp.castTicks() <= 0 ? tr("instant").getString() : Gfx.seconds(sp.castTicks()) + "s", Gfx.TEXT,
                () -> List.of(tr("stat.cast").withStyle(ChatFormatting.BOLD), tr("stat.cast.tip")));
        cx = stat(g, cx, sy + 1, PX_CD, Gfx.seconds(sp.cooldown()) + "s", Gfx.TEXT,
                () -> List.of(tr("stat.cooldown").withStyle(ChatFormatting.BOLD), tr("stat.cooldown.tip")));
        if (ProgramView.hops(sp)) {
            String hop = ProgramView.hopText(sp);
            cx = stat(g, cx, sy + 1, PX_HOP, tr("stat.hop_short", hop).getString(), LINK_COLOR,
                    () -> List.of(tr("stat.hop").withStyle(ChatFormatting.BOLD), tr("program.hop_cost", hop)));
        }
        if (sp.isUltimate()) {
            Component u = tr("stat.ultimate_badge");
            if (cx + font.width(u) <= ex + E) {
                g.drawString(font, u, cx, sy + 1, Gfx.GOLD, false);
                zones.add(cx, sy, font.width(u), 10, null, () -> List.of(tr("stat.ultimate.tip")));
            }
        }
        // Release 1.0: Transformação montada — o selo abre o resumo (o que ganha, o que custa, duração, recarga)
        if (TransformForge.isForge(sp)) {
            Component u = tr("stat.transform_badge");
            if (cx + font.width(u) <= ex + E) {
                g.drawString(font, u, cx, sy + 1, 0xFFB98CFF, false);
                zones.add(cx, sy, font.width(u), 10, bb -> openTransform(), () -> {
                    List<Component> t = new ArrayList<>(TransformForge.summary(sp, true));
                    t.add(tr("stat.transform.tip").withStyle(ChatFormatting.DARK_GRAY));
                    return t;
                });
            }
        }
        List<Component> warns = warnings(d, el, sp);
        Component line2;
        int col2;
        if (warns.isEmpty()) {
            line2 = Component.literal("✔ ").append(tr("valid"));
            col2 = Gfx.GOOD;
        } else {
            MutableComponent w0 = Component.literal("⚠ ").append(warns.get(0));
            if (warns.size() > 1) w0.append(Component.literal("  (+" + (warns.size() - 1) + ")").withStyle(ChatFormatting.GRAY));
            line2 = w0;
            col2 = Gfx.WARN;
        }
        g.drawString(font, MahouUi.fit(font, line2, E - 2), ex + 1, sy + 11, col2, false);
        zones.add(ex, sy + 10, E, 10, null, () -> {
            List<Component> t = new ArrayList<>();
            if (warns.isEmpty()) t.add(tr("valid").withStyle(ChatFormatting.GREEN));
            else {
                t.add(tr("warnings").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                for (Component wl : warns) t.add(Component.literal("• ").append(wl).withStyle(ChatFormatting.YELLOW));
            }
            return t;
        });
    }

    /** Junta o texto das linhas a partir de {@code from} (para a última linha visível mostrar o resto com "…"). */
    private static String lastLines(List<FormattedCharSequence> lines, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < lines.size(); i++) {
            lines.get(i).accept((idx, style, cp) -> {
                sb.appendCodePoint(cp);
                return true;
            });
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /** Uma estatística com ícone de pixels; devolve o x seguinte. */
    private int stat(GuiGraphics g, int sx, int sy, String[] icon, String value, int color, Supplier<List<Component>> tip) {
        return stat(g, sx, sy, icon, value, color, null, tip);
    }

    /** Estatística clicável ({@code click} != null: realça sob o mouse). */
    private int stat(GuiGraphics g, int sx, int sy, String[] icon, String value, int color, java.util.function.IntConsumer click,
                     Supplier<List<Component>> tip) {
        Font font = Gfx.font();
        int wdt = 7 + 2 + font.width(value);
        if (sx + wdt > ex + E) return sx;
        if (click != null && Gfx.in(mouseX, mouseY, sx - 1, sy - 1, wdt + 2, 10)) g.fill(sx - 1, sy + 8, sx + wdt + 1, sy + 9, Gfx.alpha(color, 0xA0));
        GlyphIcons.pixels(g, icon, sx, sy, 1, MahouUi.lighten(color, 0.1F));
        g.drawString(font, value, sx + 9, sy, color, false);
        zones.add(sx, sy - 1, wdt, 10, click, tip);
        return sx + wdt + 7;
    }

    /** Dica do custo: o detalhamento inteiro do núcleo (custo por estágio/efeito, cópias) + como abrir os Detalhes. */
    private List<Component> manaTip(ParsedSpell sp) {
        List<Component> t = new ArrayList<>();
        t.add(tr("stat.mana").withStyle(ChatFormatting.BOLD));
        t.add(tr("stat.mana.tip", ClientMagic.maxMana).withStyle(ChatFormatting.GRAY));
        // Maestria: quanto do custo os glifos treinados já tiraram (já está no número mostrado)
        float mastery = com.renanpontes.isekaimahou.glyph.GlyphMastery.spellDiscount(ClientMagic.data(), GrimoireDraft.glyphs);
        if (mastery >= 0.005F) {
            t.add(tr("stat.mana.mastery", String.format(java.util.Locale.ROOT, "%.1f", mastery * 100F)).withColor(Gfx.GOOD & 0xFFFFFF));
        }
        SpellBreakdown b = breakdown.breakdown(sp);
        if (b != null) t.addAll(b.lines());
        t.add(tr(GrimoireDraft.details ? "stat.close_details" : "stat.open_details").withStyle(ChatFormatting.DARK_GRAY));
        return t;
    }

    private List<Component> warnings(MagicData d, Element el, ParsedSpell sp) {
        List<Component> out = new ArrayList<>();
        int slot = GrimoireDraft.slot();
        if (slot == Progression.ULTIMATE_SLOT && !sp.isUltimate()) out.add(Component.translatable("message.isekai_mahou.g_needs_ultimate"));
        if (slot != Progression.ULTIMATE_SLOT && sp.isUltimate()) out.add(Component.translatable("message.isekai_mahou.ultimate_only_g"));
        SpellBreakdown b = breakdown.breakdown(sp);
        if (b != null && b.heavyCopies()) {
            BreakdownView.Worst wc = BreakdownView.worstCopy(b);
            out.add(wc != null ? tr("warn.copies", BreakdownView.copyLine(wc)) : tr("warn.copies_pct", BreakdownView.copyPercent(b)));
        }
        if (sp.manaCost() > ClientMagic.maxMana) out.add(tr("warn.over_mana", ClientMagic.maxMana));
        // O núcleo só recita o cântico (e dá o bônus de poder) em conjurações de mais de 10 ticks
        if (!GrimoireDraft.chant.isBlank() && sp.castTicks() <= CHANT_MIN_TICKS) out.add(tr("warn.chant_short", Gfx.seconds(CHANT_MIN_TICKS)));
        SpellRecipe existing = slotRecipe(d);
        if (existing != null && !existing.equals(GrimoireDraft.recipe())) out.add(tr("warn.replace", GlyphIcons.recipeTitle(existing)));
        if (GrimoireDraft.name.isBlank()) out.add(tr("warn.no_name"));
        return out;
    }

    // ------------------------------------------------------------------ cor + selos de elemento
    private void renderColorRow(GuiGraphics g, Element el, SpellCheck c, int mx, int my) {
        Font font = Gfx.font();
        int eff = GrimoireDraft.color >= 0 ? Gfx.opaque(GrimoireDraft.color) : el.argb();
        // prévia (abre a paleta de cores)
        boolean ph = Gfx.in(mx, my, ex, colorY, 12, 12);
        swatch(g, eff, GrimoireDraft.color < 0, ex, colorY, 12);
        if (ph) MahouUi.roundOutline(g, ex - 1, colorY - 1, 14, 14, 0xFFFFFFFF);
        zones.add(ex, colorY, 12, 12, b -> {
            if (b == 1) setColor(-1);
            else openColorPicker(el, ex, colorY);
        }, () -> List.of(
                GrimoireDraft.color < 0 ? tr("color.auto") : Component.literal(String.format("#%06X", GrimoireDraft.color)).withColor(GrimoireDraft.color),
                tr("color.click").withStyle(ChatFormatting.DARK_GRAY)));

        // barra de matiz
        float[] hsv = Gfx.toHsv(eff);
        float s = Math.max(hsv[1], 0.45F);
        float v = Math.max(hsv[2], 0.6F);
        int hy = colorY + 3;
        int mk = hueX + Math.round(hsv[0] * (hueW - 1));
        Gfx.batch(g, () -> {
            Gfx.frame(g, hueX - 1, hy - 1, hueW + 2, 8, Gfx.FRAME);
            for (int i = 0; i < hueW; i++) {
                g.fill(hueX + i, hy, hueX + i + 1, hy + 6, Gfx.hsv(i / (float) Math.max(1, hueW - 1), s, v));
            }
            g.fill(mk - 1, hy - 2, mk + 2, hy + 8, 0xFF000000);
            g.fill(mk, hy - 1, mk + 1, hy + 7, 0xFFFFFFFF);
        });
        zones.add(hueX, hy - 2, hueW, 10, null, () -> List.of(tr("color.hue")));
        renderCircleButtons(g, eff, mx, my);

        // selos de elemento da magia + custo extra
        int sx = ex + colorW + 8;
        int right = ex + E;
        List<Element> seals = new ArrayList<>();
        for (Element e : c.elements) if (e != Element.ARCANE) seals.add(e);
        if (seals.isEmpty()) seals.add(Element.ARCANE);
        int allowed = SpellCheck.allowedExtras(el, c.maxElements);
        boolean over = c.extras.size() > allowed;
        int extraPct = Math.round(c.extras.size() * SpellParser.EXTRA_ELEMENT_COST * 100);
        Component extra = c.extras.isEmpty() ? null : Component.literal("+" + extraPct + "%");
        int extraW = extra == null ? 0 : font.width(extra) + 3;
        int x0 = sx;
        int shown = 0;
        for (Element e : seals) {
            if (sx + 12 + extraW > right && shown < seals.size()) {
                String more = "+" + (seals.size() - shown);
                g.drawString(font, more, sx, colorY + 2, Gfx.TEXT_DIM, false);
                sx += font.width(more) + 2;
                break;
            }
            boolean primary = e == el;
            if (primary) MahouUi.roundOutline(g, sx - 1, colorY - 1, 14, 14, Gfx.GOLD);
            GlyphArt.element(g, e, sx, colorY, 12);
            sx += 14;
            shown++;
        }
        if (extra != null && sx + extraW - 3 <= right) {
            g.drawString(font, extra, sx + 1, colorY + 2, over ? Gfx.BAD : Gfx.MANA, false);
            sx += extraW;
        }
        zones.add(x0 - 1, colorY - 1, Math.max(14, sx - x0 + 1), 14, null, () -> sealsTip(el, c));
    }

    // ------------------------------------------------------------------ círculo mágico da magia
    /** Cor efetiva do círculo: a escolhida para o círculo, ou a cor da magia. */
    private static int circleColor(int spellArgb) {
        return GrimoireDraft.circle.color() >= 0 ? Gfx.opaque(GrimoireDraft.circle.color()) : spellArgb;
    }

    /** Forma, símbolo e cor do círculo de conjuração DESTA magia + prévia girando ao passar o mouse. */
    private void renderCircleButtons(GuiGraphics g, int spellArgb, int mx, int my) {
        var st = GrimoireDraft.circle;
        int cc = circleColor(spellArgb);
        boolean any = false;
        for (int i = 0; i < 3; i++) {
            int bx = circX + i * CIRC_STEP;
            boolean hov = Gfx.in(mx, my, bx, colorY, 12, 12) && !picker.isOpen();
            any |= hov;
            MahouUi.roundRect(g, bx, colorY, 12, 12, 0xFF2A2140, 0xFF15101F);
            MahouUi.roundOutline(g, bx, colorY, 12, 12, hov ? 0xFFFFFFFF : Gfx.FRAME);
            if (i == 0) CircleArt.shapeIcon(g, st.shape(), cc, bx, colorY, 12);
            else if (i == 1) CircleArt.symbolIcon(g, st.symbol(), Gfx.lighten(cc, 0.25F), bx + 1, colorY + 1, 10);
            else {
                Gfx.circle(g, bx + 6, colorY + 6, 4, cc);
                if (st.color() < 0) Gfx.small(g, "A", bx + 4.5F, colorY + 3.5F, Gfx.luminance(cc) > 0.6F ? 0xFF000000 : 0xFFFFFFFF, 0.75F);
            }
            final int which = i;
            zones.add(bx, colorY, 12, 12, b -> circleClick(which, b, spellArgb, bx), () -> circleTip(which));
        }
        // prévia: aparece ao passar o mouse nos botões ou com uma das listas deles aberta
        if (any || (picker.isOpen() && circlePickerOpen)) {
            int ps = 60;
            int px = Math.max(ex, circX - ps - 6);
            int py = colorY - ps - 4;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            MahouUi.roundRect(g, px, py, ps, ps, 0xF4221A34, 0xF4100C18);
            MahouUi.roundOutline(g, px, py, ps, ps, MahouUi.GOLD_DIM);
            float ang = (net.minecraft.Util.getMillis() % 12000L) / 12000F * Mth.TWO_PI;
            CircleArt.draw(g, st, cc, px + ps / 2F, py + ps / 2F, ps / 2F - 4, ang);
            g.pose().popPose();
        } else if (!picker.isOpen()) {
            circlePickerOpen = false;
        }
    }

    /** true enquanto a lista aberta é uma das três do círculo mágico (mantém a prévia visível). */
    private boolean circlePickerOpen;

    private void circleClick(int which, int button, int spellArgb, int bx) {
        var st = GrimoireDraft.circle;
        if (button == 1) {
            // botão direito: volta ao padrão
            setCircle(which == 0 ? st.withShape(0) : which == 1 ? st.withSymbol(0) : st.withColor(-1));
            return;
        }
        int cc = circleColor(spellArgb);
        List<Picker.Option> opts = new ArrayList<>();
        if (which == 0) {
            for (int s = 0; s <= com.renanpontes.isekaimahou.glyph.CircleStyle.SHAPES; s++) {
                final int fs = s;
                Component label = com.renanpontes.isekaimahou.glyph.CircleStyle.shapeName(s);
                opts.add(new Picker.Option((gg, ix, iy, sz) -> CircleArt.shapeIcon(gg, fs, cc, ix, iy, sz), label, null, cc, true,
                        st.shape() == s, () -> setCircle(GrimoireDraft.circle.withShape(fs)), () -> List.of(label)));
            }
        } else if (which == 1) {
            for (int s = 0; s <= com.renanpontes.isekaimahou.glyph.CircleStyle.SYMBOLS; s++) {
                final int fs = s;
                Component label = com.renanpontes.isekaimahou.glyph.CircleStyle.symbolName(s);
                opts.add(new Picker.Option((gg, ix, iy, sz) -> CircleArt.symbolIcon(gg, fs, Gfx.lighten(cc, 0.25F), ix, iy, sz), label, null, cc, true,
                        st.symbol() == s, () -> setCircle(GrimoireDraft.circle.withSymbol(fs)), () -> List.of(label)));
            }
        } else {
            opts.add(new Picker.Option((gg, ix, iy, sz) -> swatch(gg, spellArgb, true, ix, iy, sz), tr("circle.color.auto_short"), Component.literal("A"),
                    spellArgb, true, st.color() < 0, () -> setCircle(GrimoireDraft.circle.withColor(-1)), () -> List.of(tr("circle.color.auto"))));
            for (Element e : Element.values()) {
                int rgb = e.color;
                opts.add(new Picker.Option((gg, ix, iy, sz) -> swatch(gg, e.argb(), false, ix, iy, sz), Component.literal(e.displayName().getString()),
                        null, e.argb(), true, st.color() == rgb, () -> setCircle(GrimoireDraft.circle.withColor(rgb)),
                        () -> List.of(tr("color.element", e.displayName()))));
            }
            // algumas cores que nenhum elemento tem
            int[] extra = {0xFFFFFF, 0xFFD86B, 0xE23B4E, 0x1B1B2A};
            String[] keys = {"white", "gold", "crimson", "black"};
            for (int i = 0; i < extra.length; i++) {
                int rgb = extra[i];
                Component label = tr("circle.color." + keys[i]);
                opts.add(new Picker.Option((gg, ix, iy, sz) -> swatch(gg, Gfx.opaque(rgb), false, ix, iy, sz), label, null, Gfx.opaque(rgb), true,
                        st.color() == rgb, () -> setCircle(GrimoireDraft.circle.withColor(rgb)), () -> List.of(label)));
            }
        }
        circlePickerOpen = true;
        picker.toggle(bx, colorY, 12, 12, true, x, x + w, listTop, colorY - 2,
                tr(which == 0 ? "circle.pick_shape" : which == 1 ? "circle.pick_symbol" : "circle.pick_color"), opts);
    }

    private List<Component> circleTip(int which) {
        var st = GrimoireDraft.circle;
        List<Component> t = new ArrayList<>();
        t.add(tr("circle.title").withStyle(ChatFormatting.BOLD));
        Component value = which == 0 ? com.renanpontes.isekaimahou.glyph.CircleStyle.shapeName(st.shape())
                : which == 1 ? com.renanpontes.isekaimahou.glyph.CircleStyle.symbolName(st.symbol())
                : st.color() < 0 ? tr("circle.color.auto") : Component.literal(String.format("#%06X", st.color())).withColor(st.color());
        t.add(tr(which == 0 ? "circle.shape" : which == 1 ? "circle.symbol" : "circle.color", value).withStyle(ChatFormatting.GRAY));
        t.add(tr("circle.click").withStyle(ChatFormatting.DARK_GRAY));
        return t;
    }

    private void setCircle(com.renanpontes.isekaimahou.glyph.CircleStyle c) {
        if (c.equals(GrimoireDraft.circle)) return;
        GrimoireDraft.setCircle(c);
        Gfx.sound(SoundEvents.AMETHYST_CLUSTER_PLACE, 1.6F, 0.4F);
    }

    /** Roda do mouse em cima dos botões de forma/símbolo: troca na hora (a prévia mostra). */
    private boolean scrollCircle(double mx, double my, double sy) {
        if (!Gfx.in(mx, my, circX, colorY, 2 * CIRC_STEP - 2, 12)) return false;
        var st = GrimoireDraft.circle;
        int dir = sy > 0 ? -1 : 1;
        if (mx < circX + CIRC_STEP) setCircle(st.withShape(Math.floorMod(st.shape() + dir, com.renanpontes.isekaimahou.glyph.CircleStyle.SHAPES + 1)));
        else setCircle(st.withSymbol(Math.floorMod(st.symbol() + dir, com.renanpontes.isekaimahou.glyph.CircleStyle.SYMBOLS + 1)));
        return true;
    }

    private List<Component> sealsTip(Element el, SpellCheck c) {
        List<Component> t = new ArrayList<>();
        t.add(tr("elements.title").withStyle(ChatFormatting.BOLD));
        for (Element e : c.elements) {
            if (e == Element.ARCANE && c.elements.size() > 1) continue;
            MutableComponent line = Component.literal("• ").append(e.displayName());
            if (e == el) line.append(Component.literal(" ").append(tr("elements.primary")).withStyle(ChatFormatting.GOLD));
            else if (c.extras.contains(e)) line.append(Component.literal(" ").append(tr("elements.extra")).withColor(Gfx.MANA & 0xFFFFFF));
            t.add(line);
        }
        int pct = Math.round(c.extras.size() * SpellParser.EXTRA_ELEMENT_COST * 100);
        t.add(tr("elements.cost", pct).withColor(Gfx.MANA & 0xFFFFFF));
        int allowed = SpellCheck.allowedExtras(el, c.maxElements);
        t.add(tr("elements.max", c.maxElements).withStyle(c.extras.size() > allowed ? ChatFormatting.RED : ChatFormatting.GRAY));
        t.add(tr("elements.hint").withStyle(ChatFormatting.DARK_GRAY));
        return t;
    }

    private void setColor(int rgb) {
        if (rgb == GrimoireDraft.color) return;
        GrimoireDraft.setColor(rgb);
        Gfx.sound(SoundEvents.AMETHYST_CLUSTER_PLACE, 1.6F, 0.4F);
    }

    private void setHue(double mx) {
        Element el = check(ClientMagic.data()).primary;
        int eff = GrimoireDraft.color >= 0 ? GrimoireDraft.color : el.color;
        float[] hsv = Gfx.toHsv(eff);
        float t = (float) Mth.clamp((mx - hueX) / Math.max(1.0, hueW - 1), 0, 1);
        GrimoireDraft.setColor(Gfx.hsv(t, Math.max(hsv[1], 0.45F), Math.max(hsv[2], 0.6F)) & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ arrastar
    private void renderDrag(GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (!GrimoireDraft.details && program.over(mx, my)) {
            int[] p = program.insertionAt(mx, my);
            g.fill(p[0], p[1] - 2, p[0] + 2, p[1] + ICON + 2, Gfx.GOLD);
            g.fill(p[0] - 1, p[1] - 3, p[0] + 3, p[1] - 2, Gfx.GOLD);
            g.fill(p[0] - 1, p[1] + ICON + 2, p[0] + 3, p[1] + ICON + 3, Gfx.GOLD);
        }
        Glyph gl = draggingChip ? (pressChip >= 0 && pressChip < GrimoireDraft.glyphs.size() ? GlyphRegistry.get(GrimoireDraft.glyphs.get(pressChip)) : null)
                : pressPalette;
        if (gl != null) GlyphIcons.draw(g, gl, mx - ICON / 2, my - ICON / 2, ICON, GlyphIcons.SELECTED);
        g.pose().popPose();
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Clique fora das caixas de texto tira o foco delas
        screen.setFocused(null);
        if (GrimoireTransform.isOpen() && tfPanel != null) {
            boolean used = tfPanel.mouseClicked(mx, my, button);
            syncTransform();
            return used;
        }
        if (picker.isOpen()) {
            if (picker.contains(mx, my)) zones.click(mx, my, button);
            else picker.close();
            return true;
        }
        boolean bar = switch (GrimoireDraft.mode) {
            case GLYPHS -> palScroll.clickBar(mx, my);
            case PRESETS -> presets.scroll.clickBar(mx, my);
            case LIBRARY -> library.libScroll.clickBar(mx, my);
            case LEGENDS -> library.legendScroll.clickBar(mx, my);
        };
        if (bar || (GrimoireDraft.details ? breakdown.scroll.clickBar(mx, my) : program.scroll.clickBar(mx, my))) return true;
        int chip = GrimoireDraft.details ? -1 : program.chipAt(mx, my);
        if (chip >= 0) {
            if (button == 0) {
                pressChip = chip;
                pressX = mx;
                pressY = my;
                draggingChip = false;
            } else if (button == 1) {
                GrimoireDraft.remove(chip);
                Gfx.sound(SoundEvents.BOOK_PUT, 1.5F, 0.5F);
            }
            return true;
        }
        Glyph pg = paletteAt(mx, my);
        if (pg != null) {
            GrimoireDraft.selected = pg.id();
            if (button == 0) {
                pressPalette = pg;
                pressX = mx;
                pressY = my;
                draggingPalette = false;
            } else {
                Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.6F, 0.2F);
            }
            return true;
        }
        if (button == 0 && Gfx.in(mx, my, hueX, colorY + 1, hueW, 10)) {
            draggingHue = true;
            setHue(mx);
            return true;
        }
        return zones.click(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (GrimoireTransform.isOpen() && tfPanel != null) return false;
        if (palScroll.drag(my) || presets.scroll.drag(my) || library.libScroll.drag(my) || library.legendScroll.drag(my)
                || program.scroll.drag(my) || breakdown.scroll.drag(my)) return true;
        if (draggingHue) {
            setHue(mx);
            return true;
        }
        boolean moved = Math.abs(mx - pressX) + Math.abs(my - pressY) > 4;
        if (pressChip >= 0) {
            if (!draggingChip && moved) draggingChip = true;
            return true;
        }
        if (pressPalette != null) {
            if (!draggingPalette && moved) draggingPalette = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        // Botão clicado com o mouse não fica com o foco: senão Espaço o repetiria e a tecla do menu não fecharia
        if (screen.getFocused() instanceof RuneButton) screen.setFocused(null);
        if (GrimoireTransform.isOpen() && tfPanel != null) {
            pressPalette = null;
            pressChip = -1;
            return false;
        }
        boolean handled = palScroll.release() | presets.scroll.release() | library.libScroll.release() | library.legendScroll.release()
                | program.scroll.release() | breakdown.scroll.release();
        if (draggingHue) {
            draggingHue = false;
            Gfx.sound(SoundEvents.AMETHYST_CLUSTER_PLACE, 1.6F, 0.4F);
            handled = true;
        }
        if (pressChip >= 0 && button == 0) {
            int n = GrimoireDraft.glyphs.size();
            int from = pressChip;
            if (draggingChip) {
                if (program.over(mx, my) && GrimoireDraft.move(from, program.insertionAt(mx, my)[2])) {
                    Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.4F, 0.25F);
                }
            } else if (from < n && program.chipAt(mx, my) == from) {
                int zone = program.chipZone(from, mx);
                if (zone == 0) {
                    GrimoireDraft.remove(from);
                    Gfx.sound(SoundEvents.BOOK_PUT, 1.5F, 0.5F);
                } else if (GrimoireDraft.move(from, zone < 0 ? from - 1 : from + 2)) {
                    Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.4F, 0.25F);
                }
            }
            pressChip = -1;
            draggingChip = false;
            handled = true;
        }
        if (pressPalette != null && button == 0) {
            Glyph gl = pressPalette;
            if (draggingPalette) {
                if (GrimoireDraft.details) {
                    if (breakdown.over(mx, my) && canPlaceTransform(gl)) addGlyph(gl); // nos Detalhes, soltar = adicionar no fim
                } else if (program.over(mx, my) && canPlaceTransform(gl)) {
                    insertGlyph(gl, program.insertionAt(mx, my)[2]);
                }
            } else if (paletteAt(mx, my) == gl) {
                // Onda 9: clicar no glifo Transformação abre o editor de transformações (arrastar põe na magia)
                if (isTransformTile(gl)) openTransform();
                else addGlyph(gl);
            }
            pressPalette = null;
            draggingPalette = false;
            handled = true;
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (GrimoireTransform.isOpen() && tfPanel != null) return tfPanel.mouseScrolled(mx, my, sy);
        if (picker.isOpen()) return true;
        if (scrollCircle(mx, my, sy)) return true;
        if (GrimoireDraft.details ? breakdown.scroll.scroll(mx, my, sy, 20) : program.scroll.scroll(mx, my, sy, 20)) return true;
        return switch (GrimoireDraft.mode) {
            case GLYPHS -> palScroll.scroll(mx, my, sy, CELL);
            case PRESETS -> presets.scroll.scroll(mx, my, sy, PresetPanel.ROW);
            case LIBRARY -> library.libScroll.scroll(mx, my, sy, 25);
            case LEGENDS -> library.legendScroll.scroll(mx, my, sy, 26);
        };
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (GrimoireTransform.isOpen() && tfPanel != null) {
            // no editor de transformações: Backspace volta ao Grimório; a tecla do menu fecha
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                GrimoireTransform.close();
                syncTransform();
                Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.6F);
                return true;
            }
            if (CoreKeys.MENU.matches(key, scan)) {
                screen.onClose();
                return true;
            }
            return false;
        }
        if (picker.isOpen() && key == GLFW.GLFW_KEY_ESCAPE) {
            picker.close();
            return true;
        }
        GuiEventListener focused = screen.getFocused();
        boolean textFocus = focused instanceof EditBox eb && eb.isFocused();
        boolean searchFocus = textFocus && focused == search;
        boolean typing = textFocus && !searchFocus; // nome ou cântico
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;

        if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_S) {
            saveToSlot();
            return true;
        }
        if (enter) {
            if (typing && focused == presetName) {
                presetButton(); // Enter no nome do preset = salvar (ou renomear)
                return true;
            }
            if (typing) {
                screen.setFocused(null); // Enter no nome/cântico termina a edição
                return true;
            }
            // Também com um botão focado: ao abrir o menu pelo teclado o vanilla foca o 1º botão,
            // e o Enter o acionaria em vez de adicionar o glifo.
            addSelected();
            return true;
        }
        // Tecla do menu fecha mesmo com um botão focado (o StatusScreen só fecha sem foco)
        if (!textFocus && CoreKeys.MENU.matches(key, scan)) {
            screen.onClose();
            return true;
        }
        if (searchFocus) {
            if (key == GLFW.GLFW_KEY_DOWN) {
                moveSelection(palCols);
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP) {
                moveSelection(-palCols);
                return true;
            }
            return false;
        }
        if (typing) return false;

        if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }
        if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_D) {
            setDetails(!GrimoireDraft.details);
            return true;
        }
        if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_P) {
            PresetPanel.saveDraft(GrimoireDraft.name);
            return true;
        }
        int hoveredChip = GrimoireDraft.details ? -1 : program.chipAt(mouseX, mouseY);
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                int dir = key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
                if (hoveredChip >= 0) {
                    if (GrimoireDraft.move(hoveredChip, dir < 0 ? hoveredChip - 1 : hoveredChip + 2)) {
                        Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.4F, 0.25F);
                    }
                } else if (GrimoireDraft.mode == GrimoireDraft.Mode.GLYPHS) {
                    moveSelection(dir);
                }
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                if (GrimoireDraft.mode == GrimoireDraft.Mode.GLYPHS) moveSelection(key == GLFW.GLFW_KEY_UP ? -palCols : palCols);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> {
                int idx = hoveredChip >= 0 ? hoveredChip : GrimoireDraft.glyphs.size() - 1;
                if (idx >= 0) {
                    GrimoireDraft.remove(idx);
                    Gfx.sound(SoundEvents.BOOK_PUT, 1.5F, 0.5F);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public void removed() {
        GrimoireDraft.paletteScroll = palScroll.offset();
        GrimoireDraft.libraryScroll = library.libScroll.offset();
        GrimoireDraft.legendScroll = library.legendScroll.offset();
        GrimoireDraft.presetScroll = presets.scroll.offset();
        GrimoireDraft.programScroll = program.scroll.offset();
        GrimoireDraft.detailScroll = breakdown.scroll.offset();
    }
}
