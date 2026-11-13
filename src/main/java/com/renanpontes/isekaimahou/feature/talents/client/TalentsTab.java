package com.renanpontes.isekaimahou.feature.talents.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.talents.TalentStats;
import com.renanpontes.isekaimahou.feature.talents.TalentText;
import com.renanpontes.isekaimahou.feature.talents.TalentTree;
import com.renanpontes.isekaimahou.feature.talents.Talents;
import com.renanpontes.isekaimahou.feature.talents.TalentsNetwork;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Talentos" (menu B → Personagem). Os pontos livres ficam sempre no topo. Vistas:
 * <ul>
 *   <li><b>Árvore</b>: um ramo por coluna, fileiras de cima para baixo (rola com a roda do mouse), o nível pedido por
 *       cada fileira na margem. Molduras diferentes para cada tipo de nó: pequeno (quadrado), NOTÁVEL (moldura dupla),
 *       ESCOLHA (losango, "um ou outro"), FINAL (grande, cantoneiras douradas) e Maestria (repetível). Clicar gasta 1
 *       ponto (Shift = até 5). A dica do nó mostra agora → próximo nível e o que falta para liberar;</li>
 *   <li><b>Especialização</b>: antes de escolher, os 2 cartões lado a lado (como joga, assinatura, talentos
 *       exclusivos) e um 2º clique para confirmar; depois, o cartão da escolhida e a fileira exclusiva dela;</li>
 *   <li><b>Bônus</b>: a soma de tudo que está ativo (em telas largas é um painel ao lado da árvore).</li>
 * </ul>
 * Tudo que aparece vem do JSON da árvore (mandado pelo servidor): a descrição de cada nó é montada dos efeitos dele.
 */
public class TalentsTab implements MenuTab {
    private static final int GUTTER = 26, HEAD = 18;
    private static final int PANEL_TOP = 0xC0141020, PANEL_BOTTOM = 0xC00B0912;
    private static final int LOCKED = 0xFF3A3448, IDLE = 0xFF6A6080, GOOD = 0xFF7CE08A, BAD = 0xFFE07070, EMPTY = 0xFF1C1828;
    /** 0 = árvore, 1 = especialização, 2 = bônus (lembrado entre aberturas). */
    private static int view;
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private Font font;
    private int x, y, w, h;
    private int treeX, treeY, treeW, treeH, bodyY, bodyH, sideX, sideW, colW, contentH;
    private int treeScroll, sideScroll, subScroll, subScrollMax;
    /** Altura de uma fileira (encolhe em telas baixas) e "ainda não rolou até onde há ponto para gastar". */
    private int row = 36;
    private boolean autoScroll;
    private Button treeButton, subButton, sumButton;
    /** Caixa de cada nó desenhado nesta vista: id → {x, y, tamanho} (já com a rolagem). */
    private final Map<String, int[]> boxes = new LinkedHashMap<>();
    /** Botões dos cartões de especialização: id → {x, y, w, h}. */
    private final Map<String, int[]> subButtons = new LinkedHashMap<>();
    /** Especialização esperando o 2º clique. */
    private String confirm = "";
    private List<Component> totals = List.of();
    private int totalsVersion = -1;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.font = Minecraft.getInstance().font;
        this.sideW = w >= 400 ? 128 : 0;
        int n = sideW > 0 ? 2 : 3;
        int bw = Mth.clamp((w / 2 - 8) / n, 44, 84);
        int bx = x + w - 4 - n * (bw + 2);
        treeButton = screen.addTabWidget(viewButton("view_tree", 0, bx, bw));
        subButton = screen.addTabWidget(viewButton("view_subclass", 1, bx + bw + 2, bw));
        sumButton = sideW > 0 ? null : screen.addTabWidget(viewButton("view_summary", 2, bx + 2 * (bw + 2), bw));
        if (sideW > 0 && view == 2) view = 0;
        autoScroll = true;
    }

    private Button viewButton(String key, int target, int bx, int bw) {
        return Button.builder(Component.translatable("screen.isekai_mahou.talents." + key), b -> {
            view = target;
            confirm = "";
            click(1.1F);
        }).bounds(bx, y + 2, bw, 14).build();
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.6F));
    }

    private static ItemStack icon(String id) {
        return ICONS.computeIfAbsent(id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(k);
            Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    private static String key(String k) {
        return "screen.isekai_mahou.talents." + k;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        MagicData md = ClientMagic.data();
        TalentTree tree = ClientTalents.tree();
        Button[] buttons = {treeButton, subButton, sumButton};
        int titleW = treeButton != null ? treeButton.getX() - x - 12 : w - 12;
        g.drawString(font, MahouUi.fit(font, Component.translatable(key("title"), md.path().displayName())
                .withStyle(ChatFormatting.BOLD), titleW), x + 6, y + 4, MahouUi.GOLD, true);
        for (Button b : buttons) if (b != null) b.visible = tree != null;
        if (tree == null) {
            g.drawCenteredString(font, Component.translatable(key("none")), x + w / 2, y + h / 2 - 4, MahouUi.TEXT_DIM);
            return;
        }
        // marca a vista aberta
        Button active = buttons[Mth.clamp(view, 0, 2)];
        if (active != null) g.fill(active.getX() + 2, active.getY() + 15, active.getX() + active.getWidth() - 2, active.getY() + 16, MahouUi.GOLD);

        // Pontos livres: sempre à vista, em destaque quando há o que gastar
        int free = ClientTalents.unspent();
        MutableComponent points = Component.translatable(key("points"), free).withStyle(ChatFormatting.BOLD);
        g.drawString(font, points, x + 6, y + 19, free > 0 ? MahouUi.GOLD : MahouUi.TEXT_DIM, free > 0);
        int px = x + 6 + font.width(points) + 8;
        g.drawString(font, MahouUi.fit(font, Component.translatable(key("points_spent"), ClientTalents.spent(), ClientTalents.earned()),
                Math.max(10, x + w - 6 - px)), px, y + 19, MahouUi.TEXT_DIM, false);

        treeX = x + 4;
        treeY = y + 32;
        treeW = w - 8 - (sideW > 0 ? sideW + 6 : 0);
        treeH = h - 36;
        sideX = treeX + treeW + 6;
        boxes.clear();
        subButtons.clear();
        List<Component> tip = new ArrayList<>();
        if (view == 1) renderSubclasses(g, md, tree, mx, my, tip);
        else if (view == 2 && sideW == 0) renderSummary(g, md, tree, treeX, treeW);
        else renderTree(g, md, tree, mx, my, tip);

        if (!tip.isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : tip) lines.addAll(font.split(c, 220));
            g.renderTooltip(font, lines, mx, my);
        }
    }

    // ---------------------------------------------------------------- Árvore
    private static int size(TalentTree.Kind kind) {
        return switch (kind) {
            case CAPSTONE -> 28;
            case NOTABLE, CHOICE -> 26;
            default -> 22;
        };
    }

    private void renderTree(GuiGraphics g, MagicData md, TalentTree tree, int mx, int my, List<Component> tip) {
        String cls = tree.classId();
        int cols = Math.max(1, tree.branches().size());
        int tiers = Math.max(1, tree.tiers());
        colW = (treeW - GUTTER - 4) / cols;
        bodyY = treeY + HEAD;
        bodyH = treeH - HEAD - 2;
        row = Mth.clamp((bodyH - 8) / tiers, 32, 40);
        contentH = 8 + tiers * row;
        if (autoScroll) {
            // ao abrir: mostra a primeira fileira onde dá para gastar um ponto (se houver ponto)
            autoScroll = false;
            int first = -1;
            for (TalentTree.Node n : tree.nodes()) {
                if (!n.isSpec() && n.kind() != TalentTree.Kind.MASTERY && ClientTalents.check(n) == Talents.Result.OK
                        && (first < 0 || n.tier() < first)) first = n.tier();
            }
            if (first > 0) treeScroll = first * row - row / 2;
        }
        treeScroll = Mth.clamp(treeScroll, 0, Math.max(0, contentH - bodyH));
        MahouUi.roundRect(g, treeX, treeY, treeW, treeH, PANEL_TOP, PANEL_BOTTOM);

        // casas (ramo, fileira) → nós, e o nível pedido por fileira
        Map<Integer, List<TalentTree.Node>> cells = new LinkedHashMap<>();
        int[] tierLevel = new int[tiers];
        int[] spent = new int[cols];
        for (TalentTree.Node n : tree.nodes()) {
            if (n.isSpec() || n.tier() >= tiers) continue;
            cells.computeIfAbsent(n.branch() * 100 + n.tier(), k -> new ArrayList<>()).add(n);
            tierLevel[n.tier()] = tierLevel[n.tier()] == 0 ? n.level() : Math.min(tierLevel[n.tier()], n.level());
            if (n.kind() != TalentTree.Kind.MASTERY) spent[n.branch()] += Math.min(ClientTalents.rank(n.id()), n.max());
        }

        // cabeçalho dos ramos (fixo): nome e pontos gastos no ramo
        for (int b = 0; b < cols; b++) {
            TalentTree.Branch br = tree.branches().get(b);
            int color = 0xFF000000 | br.color();
            int cx = treeX + GUTTER + b * colW + colW / 2;
            MutableComponent head = TalentText.branchName(cls, br.id()).withStyle(ChatFormatting.BOLD);
            if (spent[b] > 0) head = head.append(Component.literal("  " + spent[b]).withStyle(s -> s.withBold(false)));
            FormattedCharSequence name = MahouUi.fit(font, head, colW - 6);
            g.drawString(font, name, cx - font.width(name) / 2, treeY + 5, color, false);
            g.fill(cx - colW / 2 + 8, treeY + 15, cx + colW / 2 - 8, treeY + 16, MahouUi.alpha(color, 0x60));
        }

        g.enableScissor(treeX, bodyY, treeX + treeW, bodyY + bodyH);
        boolean inside = mx >= treeX && mx < treeX + treeW && my >= bodyY && my < bodyY + bodyH;
        int top = bodyY + 4 - treeScroll;
        // margem: nível pedido por fileira
        for (int t = 0; t < tiers; t++) {
            int cy = top + t * row + row / 2;
            if (cy < bodyY - row || cy > bodyY + bodyH + row || tierLevel[t] <= 1) continue;
            MahouUi.smallText(g, font, Component.translatable(key("tier_level"), tierLevel[t]), treeX + 4, cy - 3, 0.75F,
                    md.level() >= tierLevel[t] ? IDLE : BAD, false);
        }
        // espinha de cada ramo e ligação entre os nós da mesma fileira (atrás dos nós)
        for (int b = 0; b < cols; b++) {
            int bc = 0xFF000000 | tree.branches().get(b).color();
            int cx = treeX + GUTTER + b * colW + colW / 2;
            for (int t = 0; t < tiers; t++) {
                List<TalentTree.Node> cell = cells.get(b * 100 + t);
                if (cell == null) continue;
                int cy = top + t * row + row / 2;
                TalentTree.Node first = cell.get(0);
                boolean open = md.level() >= first.level() && ClientTalents.groupPoints(first) >= first.branchPoints();
                if (t > 0) g.fill(cx - 1, cy - row, cx + 1, cy, open ? MahouUi.darken(bc, 0.35F) : LOCKED);
                if (cell.size() > 1) {
                    int half = spread(cell) * (cell.size() - 1) / 2;
                    g.fill(cx - half, cy - 1, cx + half, cy + 1, open ? MahouUi.darken(bc, 0.35F) : LOCKED);
                }
            }
        }
        // nós
        for (Map.Entry<Integer, List<TalentTree.Node>> e : cells.entrySet()) {
            List<TalentTree.Node> cell = e.getValue();
            for (int k = 0; k < cell.size(); k++) {
                TalentTree.Node n = cell.get(k);
                int s = size(n.kind());
                int cx = treeX + GUTTER + n.branch() * colW + colW / 2 + (2 * k - (cell.size() - 1)) * spread(cell) / 2;
                int cy = top + n.tier() * row + row / 2;
                if (cy + row < bodyY || cy - row > bodyY + bodyH) continue;
                drawNode(g, tree, n, cx - s / 2, cy - s / 2, s, inside, mx, my, tip);
                // escolha "um ou outro": o "ou" entre os dois
                if (n.kind() == TalentTree.Kind.CHOICE && k == 0 && cell.size() == 2) {
                    int mid = treeX + GUTTER + n.branch() * colW + colW / 2;
                    Component or = Component.translatable(key("or"));
                    float ow = font.width(or) * 0.75F;
                    g.fill(Math.round(mid - ow / 2 - 2), cy - 5, Math.round(mid + ow / 2 + 2), cy + 5, 0xFF100C1A);
                    MahouUi.smallText(g, font, or, mid - ow / 2, cy - 3, 0.75F, MahouUi.GOLD_PALE, false);
                }
            }
        }
        g.disableScissor();
        if (contentH > bodyH) {
            int barH = Math.max(12, bodyH * bodyH / contentH);
            int barY = bodyY + (bodyH - barH) * treeScroll / Math.max(1, contentH - bodyH);
            g.fill(treeX + treeW - 2, barY, treeX + treeW, barY + barH, 0x80FFFFFF);
        }
        if (sideW > 0) {
            MahouUi.roundRect(g, sideX, treeY, sideW, treeH, PANEL_TOP, PANEL_BOTTOM);
            renderSummary(g, md, tree, sideX, sideW);
        }
    }

    /** Distância entre os nós de uma mesma casa (as escolhas ficam mais afastadas, com o "ou" no meio). */
    private int spread(List<TalentTree.Node> cell) {
        if (cell.size() < 2) return 0;
        boolean choice = cell.get(0).kind() == TalentTree.Kind.CHOICE;
        return Math.min(choice ? 64 : 52, Math.round(colW * (choice ? 0.5F : 0.42F)) * 2 / cell.size());
    }

    /** Um nó, com a moldura do tipo dele. Guarda a caixa para o clique e, com o mouse em cima, monta a dica. */
    private void drawNode(GuiGraphics g, TalentTree tree, TalentTree.Node n, int nx, int ny, int s, boolean inside, int mx, int my,
                          List<Component> tip) {
        boxes.put(n.id(), new int[] {nx, ny, s});
        int rank = Math.min(ClientTalents.rank(n.id()), n.max());
        boolean maxed = rank >= n.max();
        Talents.Result res = ClientTalents.check(n);
        boolean spendable = res == Talents.Result.OK;
        boolean open = spendable || res == Talents.Result.NO_POINTS || res == Talents.Result.MAXED;
        boolean rivalTaken = res == Talents.Result.EXCLUSIVE;
        boolean hover = inside && mx >= nx && mx < nx + s && my >= ny && my < ny + s;
        int bc = 0xFF000000 | color(tree, n);
        float pulse = 0.5F + 0.5F * Mth.sin(Util.getMillis() / 280F);
        int fill = maxed ? MahouUi.darken(bc, 0.45F) : rank > 0 ? MahouUi.darken(bc, 0.72F) : EMPTY;
        int outline = maxed ? MahouUi.GOLD : spendable ? MahouUi.mix(rank > 0 ? bc : IDLE, MahouUi.GOLD_PALE, pulse)
                : rank > 0 ? bc : open ? IDLE : LOCKED;
        int cx = nx + s / 2, cy = ny + s / 2;
        switch (n.kind()) {
            case CHOICE -> {
                MahouUi.diamond(g, cx, cy, s / 2 + 2, hover ? 0xFFFFFFFF : outline);
                MahouUi.diamond(g, cx, cy, s / 2, fill);
            }
            case CAPSTONE -> {
                MahouUi.roundRect(g, nx, ny, s, s, fill, MahouUi.darken(fill, 0.35F));
                MahouUi.roundOutline(g, nx, ny, s, s, outline);
                MahouUi.roundOutline(g, nx + 2, ny + 2, s - 4, s - 4, rank > 0 ? MahouUi.GOLD : MahouUi.GOLD_DIM);
                MahouUi.brackets(g, nx - 3, ny - 3, s + 6, s + 6, 6, open || rank > 0 ? MahouUi.GOLD : MahouUi.GOLD_DIM);
            }
            case NOTABLE -> {
                MahouUi.roundRect(g, nx, ny, s, s, fill, MahouUi.darken(fill, 0.35F));
                MahouUi.roundOutline(g, nx, ny, s, s, outline);
                MahouUi.roundOutline(g, nx + 2, ny + 2, s - 4, s - 4, rank > 0 ? MahouUi.GOLD_PALE : MahouUi.GOLD_DIM);
            }
            default -> {
                MahouUi.roundRect(g, nx, ny, s, s, fill, MahouUi.darken(fill, 0.35F));
                MahouUi.roundOutline(g, nx, ny, s, s, outline);
            }
        }
        if (hover && n.kind() != TalentTree.Kind.CHOICE) MahouUi.roundOutline(g, nx - 1, ny - 1, s + 2, s + 2, 0xFFFFFFFF);
        ItemStack stack = icon(n.icon());
        if (stack.isEmpty()) MahouUi.diamond(g, cx, cy, 5, bc);
        else g.renderItem(stack, cx - 8, cy - 8);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        // nó trancado (ou o lado não escolhido): escurece o ícone
        if (rank == 0 && (!open || rivalTaken)) g.fill(cx - 9, cy - 9, cx + 9, cy + 9, 0xB00A0810);
        if (rivalTaken) g.fill(cx - 7, cy, cx + 8, cy + 1, BAD);
        String badge = n.kind() == TalentTree.Kind.MASTERY ? (rank > 0 ? "+" + rank : "+") : rank + "/" + n.max();
        float bw = font.width(badge) * 0.75F;
        g.fill(Math.round(nx + s - bw - 3), ny + s - 8, nx + s - 1, ny + s - 1, 0xC0000000);
        MahouUi.smallText(g, font, Component.literal(badge), nx + s - bw - 2, ny + s - 7, 0.75F,
                maxed ? MahouUi.GOLD : rank > 0 ? 0xFFFFFFFF : MahouUi.TEXT_DIM, false);
        g.pose().popPose();
        if (hover) nodeTooltip(tip, tree, n, rank, res);
    }

    private static int color(TalentTree tree, TalentTree.Node n) {
        if (n.isSpec()) {
            TalentTree.Subclass sub = tree.subclass(n.subclass());
            return sub == null ? 0xFFD86B : sub.color();
        }
        return tree.branches().get(n.branch()).color();
    }

    /** Linhas dos efeitos do nó com {@code rank} níveis (sem os atributos que são só suporte de uma passiva). */
    private static void effectLines(List<Component> out, TalentTree.Node n, int rank, int rgb) {
        for (TalentTree.Effect e : n.effects()) {
            if (e.isAttribute() && TalentText.HIDDEN_ATTRIBUTES.contains(e.attribute().toString())) continue;
            out.add(Component.literal("  ").append(TalentText.describe(e, n.scale(rank))).withColor(rgb));
        }
    }

    private void nodeTooltip(List<Component> tip, TalentTree tree, TalentTree.Node n, int rank, Talents.Result res) {
        String cls = tree.classId();
        MagicData md = ClientMagic.data();
        boolean specCap = n.isSpec() && n.kind() == TalentTree.Kind.CAPSTONE;
        tip.add(TalentText.nodeName(cls, n.id()).withStyle(ChatFormatting.BOLD).withColor(color(tree, n)));
        if (n.kind() != TalentTree.Kind.SMALL) {
            tip.add((specCap ? Component.translatable(key("kind.spec_capstone")) : TalentText.kindName(n.kind()))
                    .withColor(MahouUi.GOLD_PALE & 0xFFFFFF));
        }
        if (n.kind() == TalentTree.Kind.MASTERY || n.max() > 1) {
            tip.add(Component.translatable(key("rank"), rank, n.max()).withStyle(ChatFormatting.GRAY));
        }
        // agora → próximo nível
        if (rank >= n.max()) {
            tip.add(Component.translatable(key("at_max")).withColor(MahouUi.GOLD & 0xFFFFFF));
            effectLines(tip, n, rank, MahouUi.GOLD & 0xFFFFFF);
        } else {
            if (rank > 0) {
                tip.add(Component.translatable(key("now")).withStyle(ChatFormatting.GRAY));
                effectLines(tip, n, rank, 0xC8C8D2);
            }
            tip.add(Component.translatable(key("next")).withColor(GOOD & 0xFFFFFF));
            effectLines(tip, n, rank + 1, GOOD & 0xFFFFFF);
        }
        if (n.kind() == TalentTree.Kind.MASTERY) {
            tip.add(Component.translatable(key("mastery_note")).withStyle(ChatFormatting.DARK_GRAY));
        }
        // o que falta
        if (n.isSpec() && !n.subclass().equals(ClientTalents.subclass())) {
            tip.add(Component.translatable(key("need_spec"), TalentText.subclassName(n.subclass())).withColor(BAD & 0xFFFFFF));
        }
        if (md.level() < n.level()) tip.add(Component.translatable(key("need_level"), n.level()).withColor(BAD & 0xFFFFFF));
        int have = ClientTalents.groupPoints(n);
        if (have < n.branchPoints()) {
            tip.add((n.isSpec() ? Component.translatable(key("need_spec_points"), n.branchPoints(), have)
                    : Component.translatable(key("need_branch"), n.branchPoints(),
                    TalentText.branchName(cls, tree.branches().get(n.branch()).id()), have)).withColor(BAD & 0xFFFFFF));
        }
        for (Map.Entry<String, Integer> r : n.requires().entrySet()) {
            if (ClientTalents.rank(r.getKey()) >= r.getValue()) continue;
            tip.add(Component.translatable(key("need_node"), TalentText.nodeName(cls, r.getKey()), r.getValue()).withColor(BAD & 0xFFFFFF));
        }
        for (String rival : n.exclusive()) {
            boolean taken = ClientTalents.rank(rival) > 0;
            if (rank > 0 && !taken) continue;
            tip.add(Component.translatable(key(taken ? "rival_taken" : "rival"), TalentText.nodeName(cls, rival))
                    .withColor((taken ? BAD : MahouUi.GOLD_PALE) & 0xFFFFFF));
        }
        if (res == Talents.Result.OK) {
            tip.add(Component.translatable(key(n.max() - rank > 1 ? "click" : "click_one")).withColor(MahouUi.GOLD & 0xFFFFFF));
        } else if (res == Talents.Result.MAXED) {
            if (n.max() > 1) tip.add(Component.translatable(key("maxed")).withColor(MahouUi.GOLD & 0xFFFFFF));
        } else if (res == Talents.Result.NO_POINTS) {
            tip.add(Component.translatable(key("no_points")).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ---------------------------------------------------------------- Bônus ativos
    private record Total(TalentTree.Effect effect, double[] sum) {}

    private List<Component> totals(TalentTree tree) {
        if (totalsVersion == ClientTalents.version) return totals;
        totalsVersion = ClientTalents.version;
        Map<String, Total> sums = new LinkedHashMap<>();
        for (TalentTree.Node n : tree.nodes()) {
            int rank = Math.min(ClientTalents.rank(n.id()), n.max());
            if (rank > 0) addTotals(sums, n.effects(), n.scale(rank));
        }
        TalentTree.Subclass sub = tree.subclass(ClientTalents.subclass());
        if (sub != null) addTotals(sums, sub.effects(), 1);
        List<Component> out = new ArrayList<>();
        for (Total t : sums.values()) {
            TalentTree.Effect e = t.effect();
            double sum = t.sum()[0];
            if (e.isAttribute()) {
                if (TalentText.HIDDEN_ATTRIBUTES.contains(e.attribute().toString())) continue;
                out.add(TalentText.describe(new TalentTree.Effect(e.attribute(), e.stat(), sum, e.op()), 1));
                continue;
            }
            // passiva acima do teto: mostra o que vale de verdade, e avisa
            Double cap = TalentStats.CAPS.get(e.stat());
            MutableComponent line = TalentText.describeStat(e.stat(), cap == null ? sum : Math.min(cap, sum));
            if (cap != null && sum > cap + 1.0E-6) {
                line.append(" ").append(Component.translatable(key("side_cap"), TalentText.plainValue(e.stat(), cap))
                        .withColor(MahouUi.GOLD_PALE & 0xFFFFFF));
            }
            out.add(line);
        }
        totals = out;
        return out;
    }

    private static void addTotals(Map<String, Total> sums, List<TalentTree.Effect> effects, double scale) {
        for (TalentTree.Effect e : effects) {
            String key = e.isAttribute() ? e.attribute() + "|" + e.op().ordinal() : e.stat();
            sums.computeIfAbsent(key, k -> new Total(e, new double[1])).sum()[0] += e.amount() * scale;
        }
    }

    /** A soma de tudo que está ativo (painel ao lado da árvore, ou a vista "Bônus" em telas estreitas). */
    private void renderSummary(GuiGraphics g, MagicData md, TalentTree tree, int px, int pw) {
        if (sideW == 0) MahouUi.roundRect(g, px, treeY, pw, treeH, PANEL_TOP, PANEL_BOTTOM);
        int tx = px + 6, tw = pw - 12, ty = treeY + 5;
        String chosen = ClientTalents.subclass();
        TalentTree.Subclass sub = tree.subclass(chosen);
        Component subLine = sub != null ? TalentText.subclassName(chosen).withColor(sub.color())
                : tree.subclasses().isEmpty() ? null
                : md.circle() >= Talents.SUBCLASS_CIRCLE ? Component.translatable(key("side_sub_open")).withColor(MahouUi.GOLD & 0xFFFFFF)
                : Component.translatable(key("side_sub_locked"), Talents.SUBCLASS_CIRCLE);
        if (subLine != null) {
            g.drawString(font, MahouUi.fit(font, subLine, tw), tx, ty, MahouUi.TEXT_DIM, false);
            ty += 12;
        }
        g.drawString(font, Component.translatable(key("side_title")).withStyle(ChatFormatting.BOLD), tx, ty, MahouUi.GOLD, false);
        ty += 12;
        List<Component> lines = totals(tree);
        int listH = treeY + treeH - 4 - ty;
        if (lines.isEmpty()) {
            for (FormattedCharSequence l : font.split(Component.translatable(key("side_empty")), tw)) {
                g.drawString(font, l, tx, ty, MahouUi.TEXT_DIM, false);
                ty += 10;
            }
            return;
        }
        // linhas pequenas, quebradas na largura do painel
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component c : lines) wrapped.addAll(font.split(c, Math.round(tw / 0.75F)));
        int step = 9;
        sideScroll = Mth.clamp(sideScroll, 0, Math.max(0, wrapped.size() * step - listH));
        g.enableScissor(px, ty, px + pw, ty + listH);
        for (int i = 0; i < wrapped.size(); i++) {
            int ly = ty + i * step - sideScroll;
            if (ly + step < ty || ly > ty + listH) continue;
            g.pose().pushPose();
            g.pose().translate(tx, ly, 0);
            g.pose().scale(0.75F, 0.75F, 1F);
            g.drawString(font, wrapped.get(i), 0, 0, GOOD, false);
            g.pose().popPose();
        }
        g.disableScissor();
    }

    // ---------------------------------------------------------------- Especialização
    private void renderSubclasses(GuiGraphics g, MagicData md, TalentTree tree, int mx, int my, List<Component> tip) {
        List<TalentTree.Subclass> subs = tree.subclasses();
        if (subs.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(key("sub_none")), x + w / 2, y + h / 2 - 4, MahouUi.TEXT_DIM);
            return;
        }
        String chosen = ClientTalents.subclass();
        TalentTree.Subclass mine = tree.subclass(chosen);
        boolean open = md.circle() >= Talents.SUBCLASS_CIRCLE;
        Component head = open ? Component.translatable(key("sub_pick"))
                : Component.translatable(key("sub_locked"), Talents.SUBCLASS_CIRCLE, md.circle());
        int ty = y + 32;
        // com a especialização escolhida não há aviso em cima: o espaço vai para o cartão e a fileira dela
        if (mine == null) {
            for (FormattedCharSequence l : font.split(head, w - 12)) {
                g.drawString(font, l, x + 6, ty, open ? MahouUi.GOLD_PALE : MahouUi.TEXT_DIM, false);
                ty += 10;
            }
        }
        int top = ty + 3, gap = 6, cardH = y + h - 4 - top;
        subScrollMax = 0;
        if (mine == null) {
            // escolha: os cartões lado a lado
            int n = subs.size();
            int cardW = (w - 8 - gap * (n - 1)) / n;
            for (int i = 0; i < n; i++) card(g, tree, subs.get(i), x + 4 + i * (cardW + gap), top, cardW, cardH, true, open, mx, my);
            return;
        }
        // escolhida: o cartão dela e, ao lado, a fileira exclusiva
        int cardW = (w - 8 - gap) / 2;
        card(g, tree, mine, x + 4, top, cardW, cardH, false, open, mx, my);
        int rx = x + 4 + cardW + gap, rw = w - 8 - cardW - gap;
        MahouUi.roundRect(g, rx, top, rw, cardH, PANEL_TOP, PANEL_BOTTOM);
        int c = 0xFF000000 | mine.color();
        g.drawString(font, Component.translatable(key("sub_row")).withStyle(ChatFormatting.BOLD), rx + 7, top + 6, MahouUi.GOLD, false);
        int ry = top + 18;
        List<TalentTree.Node> specRow = tree.specNodes(mine.id());
        if (cardH - 22 >= specRow.size() * 30 + 24) {
            // sobra altura: lembra que os pontos são os mesmos da árvore
            for (FormattedCharSequence l : font.split(Component.translatable(key("sub_row_hint")), rw - 14)) {
                g.drawString(font, l, rx + 7, ry, MahouUi.TEXT_DIM, false);
                ry += 10;
            }
            ry += 4;
        }
        int rowH = Mth.clamp((top + cardH - 3 - ry) / Math.max(1, specRow.size()), 28, 40);
        boolean inside = mx >= rx && mx < rx + rw && my >= top && my < top + cardH;
        g.enableScissor(rx, top, rx + rw, top + cardH);
        for (TalentTree.Node n : specRow) {
            int s = size(n.kind());
            int cy = ry + rowH / 2;
            // linha entre os nós da fileira
            if (n != specRow.get(0)) g.fill(rx + 7 + 14, cy - rowH + 15, rx + 7 + 16, cy - s / 2, MahouUi.darken(c, 0.35F));
            drawNode(g, tree, n, rx + 7 + 15 - s / 2, cy - s / 2, s, inside, mx, my, tip);
            int rank = Math.min(ClientTalents.rank(n.id()), n.max());
            int tx = rx + 7 + 36, tw = rw - 50;
            g.drawString(font, MahouUi.fit(font, TalentText.nodeName(tree.classId(), n.id()), tw), tx, cy - 9,
                    rank > 0 ? c : MahouUi.TEXT, false);
            List<Component> fx = new ArrayList<>();
            effectLines(fx, n, Math.max(1, rank), 0);
            if (!fx.isEmpty()) {
                MahouUi.smallText(g, font, Component.literal("").append(fitText(fx.get(0), Math.round(tw / 0.75F))), tx, cy + 2,
                        0.75F, MahouUi.TEXT_DIM, false);
            }
            ry += rowH;
        }
        g.disableScissor();
    }

    /** Corta um texto na largura (com "…"), devolvendo ainda um {@link Component} (para o texto pequeno). */
    private Component fitText(Component text, int width) {
        String s = text.getString().trim();
        if (font.width(s) <= width) return Component.literal(s);
        return Component.literal(font.plainSubstrByWidth(s, Math.max(0, width - font.width("…"))) + "…");
    }

    private record Line(FormattedCharSequence text, int color) {}

    private void addLines(List<Line> out, Component text, int width, int color) {
        for (FormattedCharSequence l : font.split(text, width)) out.add(new Line(l, color));
    }

    /** Cartão de uma especialização: o que é, como joga, a assinatura e (antes de escolher) os talentos exclusivos. */
    private void card(GuiGraphics g, TalentTree tree, TalentTree.Subclass sub, int cx, int top, int cardW, int cardH, boolean picking,
                      boolean open, int mx, int my) {
        int c = 0xFF000000 | sub.color();
        MahouUi.roundRect(g, cx, top, cardW, cardH, picking ? PANEL_TOP : MahouUi.mix(PANEL_TOP, c, 0.18F), PANEL_BOTTOM);
        MahouUi.roundOutline(g, cx, top, cardW, cardH, picking ? MahouUi.alpha(c, 0xA0) : c);
        int bh = picking ? 16 : 0, by = top + cardH - bh - 6;
        int tx = cx + 7, tw = cardW - 14;

        // cabeçalho fixo: ícone e nome
        ItemStack stack = icon(sub.icon());
        if (!stack.isEmpty()) g.renderItem(stack, cx + 6, top + 5);
        g.drawString(font, MahouUi.fit(font, TalentText.subclassName(sub.id()).withStyle(ChatFormatting.BOLD), cardW - 34),
                cx + (stack.isEmpty() ? 7 : 26), top + 9, c, true);

        // corpo (rola com a roda do mouse)
        List<Line> lines = new ArrayList<>();
        addLines(lines, Component.translatable(key("sub_play")).withStyle(ChatFormatting.BOLD), tw, MahouUi.GOLD);
        addLines(lines, TalentText.subclassPlay(sub.id()), tw, MahouUi.GOLD_PALE);
        lines.add(null);
        addLines(lines, Component.translatable(key("sub_signature")).withStyle(ChatFormatting.BOLD), tw, MahouUi.GOLD);
        for (TalentTree.Effect e : sub.effects()) {
            if (e.isAttribute() && TalentText.HIDDEN_ATTRIBUTES.contains(e.attribute().toString())) continue;
            addLines(lines, Component.literal("• ").append(TalentText.describe(e, 1)), tw, GOOD);
        }
        if (picking) {
            lines.add(null);
            addLines(lines, Component.translatable(key("sub_row")).withStyle(ChatFormatting.BOLD), tw, MahouUi.GOLD);
            for (TalentTree.Node n : tree.specNodes(sub.id())) {
                addLines(lines, Component.literal("• ").append(TalentText.nodeName(tree.classId(), n.id())), tw, MahouUi.TEXT);
                List<Component> fx = new ArrayList<>();
                effectLines(fx, n, n.max(), 0);
                for (Component f : fx) addLines(lines, f, tw, MahouUi.TEXT_DIM);
            }
        }
        lines.add(null);
        addLines(lines, TalentText.subclassDescription(sub.id()).withStyle(ChatFormatting.ITALIC), tw, MahouUi.TEXT_DIM);
        if (!picking) {
            lines.add(null);
            addLines(lines, Component.translatable(key("sub_chosen")), tw, IDLE);
        }
        int bodyTop = top + 24, bodyBottom = (picking ? by : top + cardH) - 3;
        int total = 0;
        for (Line l : lines) total += l == null ? 5 : 10;
        int maxScroll = Math.max(0, total - (bodyBottom - bodyTop));
        subScrollMax = Math.max(subScrollMax, maxScroll);
        int scroll = Math.min(subScroll, maxScroll);
        g.enableScissor(cx, bodyTop, cx + cardW, bodyBottom);
        int ty = bodyTop - scroll;
        for (Line l : lines) {
            if (l == null) {
                ty += 5;
                continue;
            }
            if (ty + 10 >= bodyTop && ty <= bodyBottom) g.drawString(font, l.text(), tx, ty, l.color(), false);
            ty += 10;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int areaH = bodyBottom - bodyTop;
            int barH = Math.max(10, areaH * areaH / total);
            g.fill(cx + cardW - 3, bodyTop + (areaH - barH) * scroll / maxScroll, cx + cardW - 1,
                    bodyTop + (areaH - barH) * scroll / maxScroll + barH, 0x80FFFFFF);
        }
        if (!picking) return;

        // botão do cartão
        int bx = cx + 8, bw = cardW - 16;
        boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        boolean waiting = confirm.equals(sub.id());
        String label = !open ? "sub_btn_locked" : waiting ? "sub_btn_confirm" : "sub_btn_pick";
        int color = !open ? LOCKED : waiting ? BAD : MahouUi.GOLD;
        int fill = open && hover ? MahouUi.darken(color, 0.55F) : MahouUi.darken(color, 0.78F);
        MahouUi.roundRect(g, bx, by, bw, bh, fill, MahouUi.darken(fill, 0.3F));
        MahouUi.roundOutline(g, bx, by, bw, bh, color);
        FormattedCharSequence text = MahouUi.fit(font, Component.translatable(key(label), Talents.SUBCLASS_CIRCLE), bw - 6);
        g.drawString(font, text, bx + (bw - font.width(text)) / 2, by + 4, open ? 0xFFFFFFFF : IDLE, false);
        if (open) subButtons.put(sub.id(), new int[] {bx, by, bw, bh});
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        TalentTree tree = ClientTalents.tree();
        if (button != 0 || tree == null) return false;
        if (view == 1) {
            for (Map.Entry<String, int[]> e : subButtons.entrySet()) {
                int[] r = e.getValue();
                if (mx < r[0] || mx >= r[0] + r[2] || my < r[1] || my >= r[1] + r[3]) continue;
                if (confirm.equals(e.getKey())) {
                    PacketDistributor.sendToServer(new TalentsNetwork.Pick(e.getKey()));
                    confirm = "";
                    subScroll = 0;
                    click(1.3F);
                } else {
                    confirm = e.getKey();
                    click(0.9F);
                }
                return true;
            }
            confirm = "";
        } else if (mx < treeX || mx >= treeX + treeW || my < bodyY || my >= bodyY + bodyH) {
            return false;
        }
        for (Map.Entry<String, int[]> e : boxes.entrySet()) {
            int[] b = e.getValue();
            if (mx < b[0] || mx >= b[0] + b[2] || my < b[1] || my >= b[1] + b[2]) continue;
            TalentTree.Node n = tree.node(e.getKey());
            if (n == null) return false;
            if (ClientTalents.check(n) != Talents.Result.OK) {
                click(0.6F);
                return true;
            }
            // Shift: até 5 de uma vez (o servidor confere cada ponto)
            int times = Screen.hasShiftDown() ? Math.min(5, Math.min(n.max() - ClientTalents.rank(n.id()), ClientTalents.unspent())) : 1;
            for (int i = 0; i < times; i++) PacketDistributor.sendToServer(new TalentsNetwork.Spend(n.id()));
            click(1.25F);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (my < treeY || my >= treeY + treeH) return false;
        if (view == 1) {
            subScroll = Mth.clamp(subScroll - (int) (sy * 12), 0, subScrollMax);
            return true;
        }
        if (view == 0 && mx >= treeX && mx < treeX + treeW) {
            treeScroll -= (int) (sy * row);
            return true;
        }
        if (view == 2 || sideW > 0 && mx >= sideX && mx < sideX + sideW) {
            sideScroll -= (int) (sy * 9);
            return true;
        }
        return false;
    }
}
