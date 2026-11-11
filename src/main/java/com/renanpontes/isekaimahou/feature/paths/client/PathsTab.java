package com.renanpontes.isekaimahou.feature.paths.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.feature.paths.PathNode;
import com.renanpontes.isekaimahou.feature.paths.PathProgress;
import com.renanpontes.isekaimahou.feature.paths.PathReq;
import com.renanpontes.isekaimahou.feature.paths.PathRules;
import com.renanpontes.isekaimahou.feature.paths.PathsNetwork;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.network.ClientMagic;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Caminhos" do menu B.
 * Esquerda: hexágono com as runas dos 6 elementos base (Luz em cima, Sombra embaixo), as fusões nas ligações
 * entre os pais (Crepúsculo no centro; ligações de pares opostos em curva) e o caminho Arcano no canto.
 * Direita: cabeçalho do elemento (runa, nível, barra de XP e "?" com as fontes de XP) e a árvore em coluna:
 * cada nó mostra a Provação do rank (nível do elemento, Círculo, rank anterior), o botão "Iniciar Provação"
 * (ou "↻" para repetir como Desafio) e os glifos em fichas com ícone; no fim, os feitos-bônus.
 */
public class PathsTab implements MenuTab {
    private static final ResourceLocation ICONS = IsekaiMahou.id("textures/gui/paths_nodes.png");
    private static final int ICON_LOCK = 8;
    /** Ordem no hexágono (ângulos -90°, -30°, 30°, 90°, 150°, 210°): pares opostos Luz/Sombra, Fogo/Água, Terra/Vento. */
    private static final Element[] RING = {Element.LIGHT, Element.FIRE, Element.EARTH, Element.SHADOW, Element.WATER, Element.WIND};
    private static final int BASE_R = 11, FUSION_R = 5;
    private static final int ROW = 10, CHIP_H = 18, HEADER_H = 40;
    private static final int OK = 0xFF7CE07C, BAD = 0xFFE06A6A, MUTED = 0xFF8A8796, TEXT = 0xFFE8E4F0, GOLD = 0xFFFFD86B;

    /** Seleção lembrada entre aberturas do menu (null = ainda não escolhida, ARCANE = caminho Arcano). */
    private static Element lastSelected;

    private int x, y, w, h;
    private int mapW, dx, dw;
    private float cx, cy, radius;
    private float arcX, arcY;
    private final Map<Element, float[]> pos = new EnumMap<>(Element.class);
    /** Pontos de controle das ligações curvas (pares opostos). */
    private final Map<Element, float[]> ctrl = new EnumMap<>(Element.class);
    private Element selected;
    private double scroll;
    private int contentHeight;
    private int listTop, listBottom;
    private Button awakenButton;
    private String buttonState = "";
    private List<FormattedCharSequence> tooltip;
    /** Botões desenhados dentro da lista rolável (recalculados a cada quadro). */
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // =====================================================================================
    //  Montagem
    // =====================================================================================

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        mapW = Mth.clamp((int) (w * 0.40F), 132, 190);
        cx = x + mapW / 2F;
        cy = y + h / 2F + 6;
        radius = Math.max(36F, Math.min(mapW / 2F - 26F, h / 2F - 34F));
        arcX = x + 12;
        arcY = y + 12;
        dx = x + mapW + 8;
        dw = x + w - dx - 5;
        layoutNodes();

        selected = lastSelected != null ? lastSelected : defaultSelection(ClientMagic.data());
        awakenButton = screen.addTabWidget(Button.builder(Component.translatable("screen.isekai_mahou.paths.awaken"),
                b -> requestAwaken()).bounds(dx, y + h - 20, dw, 20).build());
        updateButton();
    }

    /**
     * Fusões de vizinhos ficam no meio da aresta; de "pulo um", no meio da corda (a 0,5·raio do centro).
     * Os 3 pares opostos não podem usar o diâmetro reto (ele passa por cima de duas fusões de corda):
     * Crepúsculo fica no centro com a ligação em arco pela esquerda; Vapor e Areia ficam a 0,3·raio, para
     * a direita, perpendiculares ao diâmetro, e a ligação é uma curva que passa por eles.
     */
    private void layoutNodes() {
        pos.clear();
        ctrl.clear();
        for (int i = 0; i < RING.length; i++) {
            double a = Math.toRadians(-90 + 60 * i);
            pos.put(RING[i], new float[]{cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius});
        }
        for (Element f : Element.FUSIONS) {
            float[] a = pos.get(f.parentA), b = pos.get(f.parentB);
            if (!isOpposite(f)) {
                pos.put(f, new float[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2});
            } else if (f == Element.TWILIGHT) {
                pos.put(f, new float[]{cx, cy});
                float bulge = radius * 0.45F;
                ctrl.put(f, new float[]{(a[0] + cx) / 2 - bulge, (a[1] + cy) / 2, (b[0] + cx) / 2 - bulge, (b[1] + cy) / 2});
            } else {
                // Direção perpendicular ao diâmetro, escolhida para o lado direito (x > 0).
                float px = -(a[1] - cy) / radius, py = (a[0] - cx) / radius;
                if (px < 0) {
                    px = -px;
                    py = -py;
                }
                pos.put(f, new float[]{cx + px * radius * 0.3F, cy + py * radius * 0.3F});
                // Bézier quadrática com B(0,5) no nó: controle = 2·nó − média dos pais (= 2·nó, relativo ao centro).
                ctrl.put(f, new float[]{cx + px * radius * 0.6F, cy + py * radius * 0.6F});
            }
        }
    }

    private static boolean isOpposite(Element fusion) {
        int diff = Math.abs(ringIndex(fusion.parentA) - ringIndex(fusion.parentB));
        return Math.min(diff, 6 - diff) == 3;
    }

    private static int ringIndex(Element e) {
        for (int i = 0; i < RING.length; i++) if (RING[i] == e) return i;
        return 0;
    }

    private static Element defaultSelection(MagicData d) {
        Element active = d.activeElement();
        if (active != Element.ARCANE) return active;
        for (Element e : Element.BASE) if (d.isAwakened(e)) return e;
        return Element.ARCANE;
    }

    private void select(Element e) {
        if (e == selected) return;
        selected = e;
        lastSelected = e;
        scroll = 0;
        click(1.2F);
        updateButton();
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private void requestAwaken() {
        if (selected != null && selected.isBase()) PacketDistributor.sendToServer(new PathsNetwork.AwakenAffinity(selected.id));
    }

    private void requestTrial(Element e, int rank) {
        click(1.0F);
        PacketDistributor.sendToServer(new CorePayloads.RequestTrial(e.id, rank));
    }

    /** O botão só aparece para elemento base de afinidade ainda não despertado; fica ativo com o rank de ritual. */
    private void updateButton() {
        if (awakenButton == null) return;
        MagicData d = ClientMagic.data();
        int rank = selected != null && selected.isBase() ? PathRules.affinityRank(d.path(), selected) : -1;
        boolean show = rank >= 0 && !d.isAwakened(selected);
        boolean active = show && PathRules.canAwakenByAffinity(d, selected);
        // Só refaz texto/dica quando algo muda (o tick chama isto 20x por segundo).
        String state = selected + ":" + show + ":" + active + ":" + d.counter(PathRules.RITUAL_RANK) + ":" + d.path();
        if (state.equals(buttonState)) return;
        buttonState = state;
        awakenButton.visible = show;
        awakenButton.active = active;
        if (show) {
            awakenButton.setMessage(Component.translatable("screen.isekai_mahou.paths.awaken_named", selected.displayName()));
            awakenButton.setTooltip(Tooltip.create(awakenButton.active
                    ? Component.translatable("screen.isekai_mahou.paths.awaken_tip", d.path().displayName())
                    : Component.translatable("screen.isekai_mahou.paths.awaken_locked", rank, d.counter(PathRules.RITUAL_RANK))));
        }
    }

    @Override
    public void tick() {
        updateButton();
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tooltip = null;
        hits.clear();
        MagicData d = ClientMagic.data();
        renderMap(g, d, mouseX, mouseY);
        g.fill(dx - 5, y, dx - 4, y + h, 0x30FFFFFF);
        renderHeader(g, d, mouseX, mouseY);
        renderList(g, d, mouseX, mouseY);
        if (tooltip != null) g.renderTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
    }

    private void renderMap(GuiGraphics g, MagicData d, int mx, int my) {
        Font font = Minecraft.getInstance().font;
        float time = (Util.getMillis() % 360000L) / 1000F;

        // Círculo mágico de fundo, girando devagar.
        PathShapes.ring(g, cx, cy, radius + 17, radius + 18, 0x33B98CFF);
        PathShapes.ring(g, cx, cy, radius + 21, radius + 21.6F, 0x22B98CFF);
        for (int i = 0; i < 12; i++) {
            double a = time * 0.15 + Math.PI * 2 * i / 12;
            float rx = cx + (float) Math.cos(a) * (radius + 19.5F), ry = cy + (float) Math.sin(a) * (radius + 19.5F);
            PathShapes.diamond(g, rx, ry, i % 3 == 0 ? 1.6F : 0.9F, 0x88B98CFF, 0x22000000);
        }

        // Ligações: cada fusão liga os dois pais (passa pelo nó da fusão).
        for (Element f : Element.FUSIONS) {
            float[] a = pos.get(f.parentA), b = pos.get(f.parentB);
            boolean fusionOn = d.isAwakened(f);
            boolean parentsOn = d.isAwakened(f.parentA) && d.isAwakened(f.parentB);
            int ca = fusionOn ? PathShapes.alpha(f.parentA.argb(), 0xC0) : parentsOn ? PathShapes.alpha(f.parentA.argb(), 0x50) : 0x1FFFFFFF;
            int cb = fusionOn ? PathShapes.alpha(f.parentB.argb(), 0xC0) : parentsOn ? PathShapes.alpha(f.parentB.argb(), 0x50) : 0x1FFFFFFF;
            float thick = fusionOn ? 2.0F : 1.2F;
            float[] q = ctrl.get(f);
            if (q == null) {
                PathShapes.line(g, a[0], a[1], b[0], b[1], thick, ca, cb);
            } else if (f == Element.TWILIGHT) {
                int mid = PathShapes.mix(ca, cb, 0.5F);
                PathShapes.curve(g, a[0], a[1], q[0], q[1], cx, cy, thick, ca, mid);
                PathShapes.curve(g, cx, cy, q[2], q[3], b[0], b[1], thick, mid, cb);
            } else {
                PathShapes.curve(g, a[0], a[1], q[0], q[1], b[0], b[1], thick, ca, cb);
            }
        }

        Element hovered = hoveredNode(mx, my);

        // Fusões: disco com a runa da fusão em miniatura (apagada enquanto não desperta).
        for (Element f : Element.FUSIONS) {
            float[] p = pos.get(f);
            float r = f == Element.TWILIGHT ? FUSION_R + 1.5F : FUSION_R;
            boolean on = d.isAwakened(f);
            if (on) PathShapes.disc(g, p[0], p[1], r + 2.5F, PathShapes.alpha(f.argb(), 0x50), PathShapes.alpha(f.argb(), 0x00));
            PathShapes.disc(g, p[0], p[1], r, 0xFF16131F, 0xFF16131F);
            PathShapes.ring(g, p[0], p[1], r - 1.2F, r, PathShapes.alpha(f.argb(), on ? 0xFF : 0x90));
            if (!on) g.setColor(0.4F, 0.38F, 0.46F, 0.75F);
            GlyphArt.element(g, f, Math.round(p[0]) - 4, Math.round(p[1]) - 4, 8);
            g.setColor(1F, 1F, 1F, 1F);
            if (f == selected || f == hovered) PathShapes.ring(g, p[0], p[1], r + 1.5F, r + 2.5F, 0xDDFFFFFF);
        }

        // Elementos base: disco + runa do elemento + nível no canto (estilo contador de item).
        for (Element e : RING) {
            float[] p = pos.get(e);
            boolean on = d.isAwakened(e);
            if (on) {
                float pulse = 0.5F + 0.5F * (float) Math.sin(time * 2.2 + e.ordinal());
                PathShapes.disc(g, p[0], p[1], BASE_R + 6, PathShapes.alpha(e.argb(), (int) (0x30 + 0x40 * pulse)), PathShapes.alpha(e.argb(), 0));
                PathShapes.disc(g, p[0], p[1], BASE_R, PathShapes.mix(e.argb(), 0xFF000000, 0.35F), PathShapes.mix(e.argb(), 0xFF000000, 0.6F));
            } else {
                PathShapes.disc(g, p[0], p[1], BASE_R, 0xFF1B1726, PathShapes.mix(e.argb(), 0xFF100D18, 0.8F));
            }
            PathShapes.ring(g, p[0], p[1], BASE_R - 1.4F, BASE_R, PathShapes.alpha(e.argb(), on ? 0xFF : 0xA0));
            // 8 "pips" em volta: nós liberados do caminho.
            for (int i = 0; i < PathNode.values().length; i++) {
                double a = -Math.PI / 2 + Math.PI * 2 * i / PathNode.values().length;
                float px = p[0] + (float) Math.cos(a) * (BASE_R + 3.5F), py = p[1] + (float) Math.sin(a) * (BASE_R + 3.5F);
                boolean has = d.hasNode(e, PathNode.values()[i].id);
                PathShapes.diamond(g, px, py, 1.2F, has ? PathShapes.mix(e.argb(), 0xFFFFFFFF, 0.5F) : 0x40FFFFFF, 0x80000000);
            }
            int ix = Math.round(p[0]) - 8, iy = Math.round(p[1]) - 8;
            if (!on) g.setColor(0.42F, 0.40F, 0.48F, 0.8F);
            GlyphArt.element(g, e, ix, iy, 16);
            g.setColor(1F, 1F, 1F, 1F);
            if (on) {
                Component lvl = Component.literal(String.valueOf(Math.max(0, d.elementLevel(e))));
                MahouUi.smallText(g, font, lvl, ix + 15 - font.width(lvl) * 0.75F, iy + 11, 0.75F, 0xFFFFFFFF, true);
            }
            if (e == selected || e == hovered) {
                for (int i = 0; i < 10; i++) {
                    double a = time * (e == selected ? 1.4 : 0.6) + Math.PI * 2 * i / 10;
                    PathShapes.diamond(g, p[0] + (float) Math.cos(a) * (BASE_R + 7), p[1] + (float) Math.sin(a) * (BASE_R + 7),
                            1.0F, e == selected ? 0xFFFFFFFF : 0x99FFFFFF, 0x00000000);
                }
            }
            // Nome: acima para os de cima, abaixo para os de baixo.
            Component name = e.displayName();
            int ly = p[1] < cy ? (int) p[1] - BASE_R - 15 : (int) p[1] + BASE_R + 7;
            g.drawString(font, name, (int) (p[0] - font.width(name) / 2F), ly, on ? 0xFFFFFFFF : 0xFFB0ADBC, true);
        }

        // Caminho Arcano (canto)
        boolean arcSel = selected == Element.ARCANE;
        PathShapes.disc(g, arcX, arcY, 10, PathShapes.mix(Element.ARCANE.argb(), 0xFF000000, 0.45F), PathShapes.mix(Element.ARCANE.argb(), 0xFF000000, 0.7F));
        PathShapes.ring(g, arcX, arcY, 9, 10, Element.ARCANE.argb());
        if (arcSel || hovered == Element.ARCANE) PathShapes.ring(g, arcX, arcY, 11, 12, 0xDDFFFFFF);
        GlyphArt.element(g, Element.ARCANE, Math.round(arcX) - 8, Math.round(arcY) - 8, 16);
        g.drawString(font, Element.ARCANE.displayName(), (int) arcX + 14, (int) arcY - 4, 0xFFFFFFFF, true);

        if (hovered != null) tooltip = nodeTooltip(hovered, d);
    }

    private Element hoveredNode(int mx, int my) {
        if (dist(mx, my, arcX, arcY) <= 11) return Element.ARCANE;
        for (Map.Entry<Element, float[]> en : pos.entrySet()) {
            float r = en.getKey().isBase() ? BASE_R + 2 : FUSION_R + 2;
            if (dist(mx, my, en.getValue()[0], en.getValue()[1]) <= r) return en.getKey();
        }
        return null;
    }

    private static float dist(double mx, double my, float px, float py) {
        return (float) Math.sqrt((mx - px) * (mx - px) + (my - py) * (my - py));
    }

    private List<FormattedCharSequence> nodeTooltip(Element e, MagicData d) {
        List<Component> lines = new ArrayList<>();
        lines.add(e.displayName().withStyle(ChatFormatting.BOLD));
        if (e == Element.ARCANE) {
            lines.add(Component.translatable("screen.isekai_mahou.paths.arcane_tip").withStyle(ChatFormatting.GRAY));
        } else {
            if (e.isFusion()) {
                lines.add(Component.translatable("screen.isekai_mahou.paths.fusion_of", e.parentA.displayName(), e.parentB.displayName())
                        .withStyle(ChatFormatting.GRAY));
            }
            if (d.isAwakened(e)) {
                lines.add(Component.translatable("screen.isekai_mahou.paths.tip_level", d.elementLevel(e), nodeCount(d, e),
                        PathNode.values().length).withStyle(ChatFormatting.WHITE));
                int next = TrialsApi.nextRank(d, e);
                lines.add(next == 0 ? Component.translatable("screen.isekai_mahou.paths.tip_trials_done").withStyle(ChatFormatting.GOLD)
                        : Component.translatable("screen.isekai_mahou.paths.tip_trial", TrialsApi.title(e, next),
                        Progression.trialElementLevel(next), Progression.trialCircle(next)).withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.translatable("screen.isekai_mahou.paths.not_awakened").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (d.path() != MagicPath.NONE) lines.add(affinityLine(d, e));
        }
        lines.add(Component.translatable("screen.isekai_mahou.paths.click_hint").withStyle(ChatFormatting.DARK_GRAY));
        return toTooltip(lines);
    }

    private static int nodeCount(MagicData d, Element e) {
        int n = 0;
        for (PathNode node : PathNode.values()) if (d.hasNode(e, node.id)) n++;
        return n;
    }

    private static MutableComponent affinityLine(MagicData d, Element e) {
        MagicPath.Affinity a = d.path().affinity(e);
        ChatFormatting color = switch (a) {
            case AFFINITY -> ChatFormatting.GREEN;
            case HARD -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
        return Component.translatable("screen.isekai_mahou.paths.affinity_line", a.displayName().withStyle(color),
                fmt(a.xpMult), fmt(a.featMult)).withStyle(ChatFormatting.GRAY);
    }

    private static String fmt(float v) {
        return v == (int) v ? String.valueOf((int) v) : String.valueOf(v);
    }

    // ---------------------------------------------------------------- Cabeçalho da direita

    private void renderHeader(GuiGraphics g, MagicData d, int mx, int my) {
        Font font = Minecraft.getInstance().font;
        Element e = selected;
        boolean arcane = e == Element.ARCANE;
        int titleColor = MahouUi.lighten(e.argb(), 0.35F);

        GlyphArt.element(g, e, dx, y, 16);
        Component title = arcane ? Component.translatable("screen.isekai_mahou.paths.arcane_title") : e.displayName();
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), dx + 20, y + 4, titleColor, true);
        if (!arcane && d.path() != MagicPath.NONE) {
            MagicPath.Affinity a = d.path().affinity(e);
            Component aff = a.displayName();
            int col = a == MagicPath.Affinity.AFFINITY ? OK : a == MagicPath.Affinity.HARD ? BAD : 0xFFB0ADBC;
            g.drawString(font, aff, dx + dw - font.width(aff), y + 4, col, false);
        }

        int ly = y + 20, by = y + 31;
        // "?" com as fontes de XP (canto direito da linha do nível).
        int qx = dx + dw - 9;
        boolean qHover = mx >= qx - 1 && mx < qx + 10 && my >= ly - 2 && my < ly + 10;
        MahouUi.roundRect(g, qx, ly - 1, 9, 10, qHover ? 0xFF6A5A90 : 0xFF3A3050, qHover ? 0xFF4A3A70 : 0xFF241C34);
        g.drawString(font, "?", qx + 2, ly, qHover ? 0xFFFFFFFF : GOLD, false);
        if (qHover) tooltip = toTooltip(xpHelp(d, e));

        int textW = dw - 14;
        if (arcane) {
            int nextTier = nextArcaneTier(d);
            Component lv = nextTier == 0 ? Component.translatable("screen.isekai_mahou.paths.arcane_level_done", d.level(), d.circle())
                    : Component.translatable("screen.isekai_mahou.paths.arcane_level", d.level(), d.circle(),
                    Progression.arcaneUnlockLevel(nextTier));
            g.drawString(font, MahouUi.fit(font, lv, textW), dx, ly, TEXT, false);
            float frac = nextTier == 0 ? 1F : arcaneFraction(d, nextTier);
            MahouUi.bar(g, dx, by, dw, 4, frac, Element.ARCANE.argb());
        } else if (d.isAwakened(e)) {
            int lvl = d.elementLevel(e);
            int xpTotal = d.element(e).xp;
            boolean max = lvl >= Progression.MAX_ELEMENT_LEVEL;
            int into = Progression.elementXpIntoLevel(xpTotal), need = Progression.elementXpToNext(lvl);
            Component lv = max ? Component.translatable("screen.isekai_mahou.paths.level_max", lvl)
                    : Component.translatable("screen.isekai_mahou.paths.level", lvl, into, need);
            g.drawString(font, MahouUi.fit(font, lv, textW), dx, ly, TEXT, false);
            MahouUi.bar(g, dx, by, dw, 4, max ? 1F : Math.min(1F, into / (float) need), e.argb());
        } else {
            g.drawString(font, Component.translatable("screen.isekai_mahou.paths.not_awakened"), dx, ly, MUTED, false);
            MahouUi.bar(g, dx, by, dw, 4, 0F, e.argb());
        }
    }

    /** Menor tier Arcano ainda não liberado pelo nível (0 = todos liberados). */
    private static int nextArcaneTier(MagicData d) {
        for (int t = 1; t <= PathRules.ARCANE_TIERS; t++) if (d.level() < Progression.arcaneUnlockLevel(t)) return t;
        return 0;
    }

    /** Progresso de XP do nível do tier anterior até o nível do próximo tier Arcano. */
    private static float arcaneFraction(MagicData d, int tier) {
        int from = tier > 1 ? Progression.arcaneUnlockLevel(tier - 1) : 1;
        int to = Progression.arcaneUnlockLevel(tier);
        int total = Progression.xpForLevels(from, to - from);
        int done = Progression.xpForLevels(from, Math.max(0, d.level() - from)) + d.xp();
        return total <= 0 ? 1F : Mth.clamp(done / (float) total, 0F, 1F);
    }

    private static List<Component> xpHelp(MagicData d, Element e) {
        List<Component> lines = new ArrayList<>();
        if (e == Element.ARCANE) {
            lines.add(Component.translatable("screen.isekai_mahou.paths.help_arcane_title").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("screen.isekai_mahou.paths.help_arcane_1").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.isekai_mahou.paths.help_arcane_2").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.isekai_mahou.paths.help_arcane_3").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.isekai_mahou.paths.help_arcane_4").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.isekai_mahou.paths.help_arcane_5").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.translatable("screen.isekai_mahou.paths.help_title", e.displayName()).withColor(e.color));
        lines.add(Component.translatable("screen.isekai_mahou.paths.help_cast", e.displayName()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.isekai_mahou.paths.help_kill").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.isekai_mahou.paths.help_feats", PathRules.featBonus(1), PathRules.featBonus(2),
                PathRules.featBonus(3)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.isekai_mahou.paths.help_trials").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.isekai_mahou.paths.help_rituals").withStyle(ChatFormatting.GRAY));
        if (d.path() != MagicPath.NONE) lines.add(affinityLine(d, e));
        return lines;
    }

    // ---------------------------------------------------------------- Lista rolável

    private void renderList(GuiGraphics g, MagicData d, int mx, int my) {
        Player player = Minecraft.getInstance().player;
        listTop = y + HEADER_H;
        listBottom = awakenButton != null && awakenButton.visible ? y + h - 24 : y + h;
        List<Row> rows = selected == Element.ARCANE ? arcaneRows(d) : elementRows(d, selected, player);
        contentHeight = 0;
        for (Row r : rows) contentHeight += r.height();
        int viewH = listBottom - listTop;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - viewH));

        g.enableScissor(dx - 2, listTop, dx + dw + 2, listBottom);
        int ry = listTop - (int) scroll;
        boolean inside = mx >= dx - 2 && mx < dx + dw + 2 && my >= listTop && my < listBottom;
        for (Row r : rows) {
            if (ry + r.height() >= listTop && ry <= listBottom) r.draw(g, dx, ry, dw, inside ? mx : -1, inside ? my : -1);
            ry += r.height();
        }
        g.disableScissor();

        if (contentHeight > viewH) {
            int barH = Math.max(12, viewH * viewH / contentHeight);
            int barY = listTop + (int) ((viewH - barH) * (scroll / (contentHeight - viewH)));
            g.fill(dx + dw + 2, listTop, dx + dw + 4, listBottom, 0x30FFFFFF);
            g.fill(dx + dw + 2, barY, dx + dw + 4, barY + barH, 0xA0FFFFFF);
        }
    }

    /** Uma linha da lista rolável. */
    private interface Row {
        int height();

        void draw(GuiGraphics g, int left, int top, int width, int mx, int my);
    }

    private List<Row> elementRows(MagicData d, Element e, Player player) {
        List<Row> rows = new ArrayList<>();
        boolean awake = d.isAwakened(e);
        if (awake) rows.add(rankStrip(d, e));
        if (d.path() != MagicPath.NONE) rows.addAll(wrapped(affinityLine(d, e), MUTED, 0));
        rows.addAll(wrapped(Component.translatable("screen.isekai_mahou.paths.xp_hint", e.displayName()), MUTED, 0));
        rows.add(separator());

        if (!awake) {
            if (e.isFusion()) {
                rows.add(text(Component.translatable("screen.isekai_mahou.paths.convergence").withStyle(ChatFormatting.GOLD), 0));
                for (PathReq r : PathRules.convergence(e)) rows.add(req(r, d, player));
                rows.addAll(wrapped(Component.translatable("screen.isekai_mahou.paths.convergence_hint"), MUTED, 4));
            } else {
                rows.add(text(Component.translatable("screen.isekai_mahou.paths.how_awaken").withStyle(ChatFormatting.GOLD), 0));
                rows.addAll(wrapped(Component.translatable("screen.isekai_mahou.paths.shrine", e.displayName()), TEXT, 4));
                int rank = PathRules.affinityRank(d.path(), e);
                if (rank == 0) {
                    rows.addAll(wrapped(Component.translatable("screen.isekai_mahou.paths.affinity_primary", d.path().displayName()), OK, 4));
                } else if (rank > 0) {
                    rows.add(req(new PathReq.Counter(PathRules.RITUAL_RANK, rank, "paths.isekai_mahou.req.ritual_rank",
                            d.path().displayName()), d, player));
                    rows.addAll(wrapped(Component.translatable("screen.isekai_mahou.paths.affinity_hint"), MUTED, 4));
                }
            }
            rows.add(separator());
        }

        int next = awake ? TrialsApi.nextRank(d, e) : 0;
        for (PathNode n : PathNode.values()) {
            boolean owned = d.hasNode(e, n.id);
            int rank = n.trialRank();
            boolean isNext = awake && !owned && (rank == 0 || rank == next);
            rows.add(nodeHeader(e, n, owned, isNext, d, player));
            if (!owned) {
                if (rank == 0) {
                    for (PathReq r : PathRules.requirements(e, n)) rows.add(req(r, d, player));
                } else {
                    rows.add(text(Component.translatable("screen.isekai_mahou.paths.trial_needs", TrialsApi.title(e, rank)), 4));
                    for (PathReq r : PathRules.trialEntry(e, rank)) rows.add(req(r, d, player));
                    rows.add(text(Component.translatable("screen.isekai_mahou.paths.trial_info", PathRules.trialWaves(rank),
                            PathRules.trialBoss(e, rank)).withColor(MUTED), 16));
                    if (awake && rank == next) rows.add(trialButton(e, rank, d));
                }
            }
            List<Glyph> glyphs = PathRules.nodeGlyphs(e, n, d);
            if (!glyphs.isEmpty()) {
                rows.addAll(chips(glyphs, d, owned, false));
            } else if (n == PathNode.FLUIDITY) {
                rows.add(text(Component.translatable("screen.isekai_mahou.paths.no_trigger_left").withColor(MUTED), 16));
            } else if (n.glyphTier > 0 || n == PathNode.CATACLYSM) {
                rows.add(text(Component.translatable("screen.isekai_mahou.paths.no_glyphs").withColor(MUTED), 16));
            }
            rows.add(separator());
        }

        // Feitos: bônus de XP do elemento (os antigos requisitos de Aprendiz/Adepto/Mestre).
        rows.add(text(Component.translatable("screen.isekai_mahou.paths.feats_title").withStyle(ChatFormatting.GOLD), 0));
        rows.addAll(wrapped(Component.translatable("screen.isekai_mahou.paths.feats_desc"), MUTED, 0));
        for (int stage = 1; stage <= PathRules.FEAT_STAGES; stage++) {
            boolean got = d.hasFlag(PathRules.featFlag(e, stage));
            int amount = Math.max(1, Math.round(PathRules.featBonus(stage) * d.path().affinity(e).xpMult));
            rows.add(featHeader(e, stage, got, amount));
            if (!got) for (PathReq r : PathRules.feats(e, stage, d.path())) rows.add(req(r, d, player));
        }
        return rows;
    }

    private List<Row> arcaneRows(MagicData d) {
        List<Row> rows = new ArrayList<>(wrapped(Component.translatable("screen.isekai_mahou.paths.arcane_desc"), MUTED, 0));
        rows.add(separator());
        for (int tier = 1; tier <= PathRules.ARCANE_TIERS; tier++) {
            int level = Progression.arcaneUnlockLevel(tier);
            boolean reached = d.level() >= level && d.path() != MagicPath.NONE;
            rows.add(levelHeader(level, tier, reached, d));
            List<Glyph> glyphs = PathRules.arcaneGlyphs(tier);
            if (glyphs.isEmpty()) rows.add(text(Component.translatable("screen.isekai_mahou.paths.no_glyphs").withColor(MUTED), 16));
            else rows.addAll(chips(glyphs, d, reached, true));
            rows.add(separator());
        }
        return rows;
    }

    private Row text(Component c, int indent) {
        return new Row() {
            @Override public int height() { return ROW + 1; }
            @Override public void draw(GuiGraphics g, int left, int top, int width, int mx, int my) {
                Font font = Minecraft.getInstance().font;
                g.drawString(font, MahouUi.fit(font, c, width - indent), left + indent, top + 1, TEXT, false);
                if (font.width(c) > width - indent && mx >= left && mx < left + width && my >= top && my < top + ROW) {
                    tooltip = toTooltip(List.of(c));
                }
            }
        };
    }

    private List<Row> wrapped(Component c, int color, int indent) {
        List<Row> rows = new ArrayList<>();
        Font font = Minecraft.getInstance().font;
        for (FormattedCharSequence line : font.split(c, Math.max(40, dw - indent))) {
            rows.add(new Row() {
                @Override public int height() { return ROW; }
                @Override public void draw(GuiGraphics g, int left, int top, int width, int mx, int my) {
                    g.drawString(Minecraft.getInstance().font, line, left + indent, top + 1, color, false);
                }
            });
        }
        return rows;
    }

    private Row separator() {
        return new Row() {
            @Override public int height() { return 5; }
            @Override public void draw(GuiGraphics g, int left, int top, int width, int mx, int my) {
                g.fill(left, top + 2, left + width, top + 3, 0x18FFFFFF);
            }
        };
    }

    /** Faixa "Provações: I II III ... VII" (concluídas na cor do elemento, a próxima com borda pulsando). */
    private Row rankStrip(MagicData d, Element e) {
        return new Row() {
            @Override public int height() { return 16; }
            @Override public void draw(GuiGraphics g, int left, int top, int width, int mx, int my) {
                Font font = Minecraft.getInstance().font;
                Component label = Component.translatable("screen.isekai_mahou.paths.trials_label");
                g.drawString(font, label, left, top + 4, MUTED, false);
                int cleared = TrialsApi.clearedRank(d, e), next = TrialsApi.nextRank(d, e);
                int bx = left + font.width(label) + 4;
                int cell = Math.max(14, Math.min(20, (width - (bx - left)) / Progression.MAX_TRIAL_RANK));
                float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 200.0);
                for (int r = 1; r <= Progression.MAX_TRIAL_RANK; r++) {
                    int cxp = bx + (r - 1) * cell;
                    boolean done = r <= cleared;
                    int fill = done ? MahouUi.darken(e.argb(), 0.25F) : 0xFF1A1724;
                    MahouUi.roundRect(g, cxp, top + 1, cell - 2, 13, fill, MahouUi.darken(fill, 0.3F));
                    int edge = r == next ? MahouUi.mix(0xFF6A6080, GOLD, pulse) : done ? MahouUi.lighten(e.argb(), 0.3F) : 0xFF3A3248;
                    MahouUi.roundOutline(g, cxp, top + 1, cell - 2, 13, edge);
                    String roman = TrialsApi.roman(r);
                    float scale = font.width(roman) > cell - 5 ? 0.7F : 1F;
                    MahouUi.smallText(g, font, Component.literal(roman), cxp + (cell - 2 - font.width(roman) * scale) / 2F,
                            top + (scale < 1 ? 5 : 4), scale, done ? 0xFFFFFFFF : r == next ? GOLD : MUTED, done);
                    if (mx >= cxp && mx < cxp + cell - 2 && my >= top && my < top + 15) {
                        List<Component> lines = new ArrayList<>();
                        lines.add(TrialsApi.title(e, r));
                        PathNode node = PathNode.byTrialRank(r);
                        if (node != null) lines.add(Component.translatable("screen.isekai_mahou.paths.trial_unlocks", node.displayName())
                                .withStyle(ChatFormatting.GRAY));
                        lines.add(done ? Component.translatable("screen.isekai_mahou.paths.trial_done").withStyle(ChatFormatting.GREEN)
                                : Component.translatable("screen.isekai_mahou.paths.trial_entry", Progression.trialElementLevel(r),
                                Progression.trialCircle(r)).withStyle(ChatFormatting.GRAY));
                        tooltip = toTooltip(lines);
                    }
                }
            }
        };
    }

    private Row nodeHeader(Element e, PathNode n, boolean owned, boolean isNext, MagicData d, Player player) {
        return new Row() {
            @Override public int height() { return 19; }
            @Override public void draw(GuiGraphics g, int left, int top, int width, int mx, int my) {
                Font font = Minecraft.getInstance().font;
                int rank = n.trialRank();
                if (isNext) g.fill(left - 2, top, left + width + 1, top + 18, PathShapes.alpha(e.argb(), 0x26));
                if (owned) g.fill(left - 2, top, left - 1, top + 18, e.argb());
                // Ícone pintado com a cor do elemento (liberado) ou cinza (bloqueado).
                float[] c = owned ? rgb(e.color) : new float[]{0.42F, 0.40F, 0.48F};
                g.setColor(c[0], c[1], c[2], 1F);
                g.blit(ICONS, left + 1, top + 1, n.ordinal() * 16, 0, 16, 16, 16 * 9, 16);
                g.setColor(1F, 1F, 1F, 1F);

                // Lado direito: ✔ / "!" (pode começar) / cadeado, o rank em romano e o "↻" de repetir.
                int right = left + width;
                boolean ready = !owned && rank > 0 && isNext && TrialsApi.meetsRequirements(d, e, rank, new ArrayList<>());
                if (owned) {
                    g.drawString(font, "✔", right - 9, top + 5, OK, false);
                } else if (ready || (rank == 0 && PathRules.allMet(e, n, d, player))) {
                    float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 180.0);
                    g.drawString(font, "!", right - 7, top + 5, MahouUi.mix(GOLD, 0xFFFFFFFF, pulse), true);
                } else {
                    g.setColor(0.55F, 0.53F, 0.6F, 1F);
                    g.blit(ICONS, right - 12, top + 3, 12, 12, ICON_LOCK * 16, 0, 16, 16, 16 * 9, 16);
                    g.setColor(1F, 1F, 1F, 1F);
                }
                int tagRight = right - 14;
                if (rank > 0 && TrialsApi.isCleared(d, e, rank)) {
                    tagRight = repeatButton(g, font, e, rank, right - 26, top + 3, mx, my) - 3;
                }
                if (rank > 0) {
                    Component tag = Component.literal(TrialsApi.roman(rank));
                    g.drawString(font, tag, tagRight - font.width(tag), top + 5, owned ? MahouUi.lighten(e.argb(), 0.4F) : MUTED, false);
                    tagRight -= font.width(tag) + 4;
                }
                Component name = n.displayName().withStyle(owned ? ChatFormatting.BOLD : ChatFormatting.RESET);
                g.drawString(font, MahouUi.fit(font, name, tagRight - left - 21), left + 21, top + 5,
                        owned ? (e.color | 0xFF000000) : 0xFFC8C4D2, owned);

                if (tooltip == null && mx >= left && mx < tagRight && my >= top && my < top + 18) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(n.displayName().withColor(e.color));
                    lines.add(n.description().withStyle(ChatFormatting.GRAY));
                    if (rank > 0) {
                        lines.add(Component.translatable("screen.isekai_mahou.paths.node_by_trial", TrialsApi.title(e, rank)));
                        lines.add(Component.translatable("screen.isekai_mahou.paths.trial_info", PathRules.trialWaves(rank),
                                PathRules.trialBoss(e, rank)).withStyle(ChatFormatting.DARK_GRAY));
                    }
                    lines.add(owned ? Component.translatable("screen.isekai_mahou.paths.unlocked").withStyle(ChatFormatting.GREEN)
                            : Component.translatable("screen.isekai_mahou.paths.locked").withStyle(ChatFormatting.RED));
                    tooltip = toTooltip(lines);
                }
            }
        };
    }

    /** Botãozinho "↻" (repetir a Provação como Desafio). Retorna o x da borda esquerda. */
    private int repeatButton(GuiGraphics g, Font font, Element e, int rank, int bx, int by, int mx, int my) {
        int bw = 12, bh = 12;
        boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        MahouUi.roundRect(g, bx, by, bw, bh, hover ? MahouUi.lighten(e.argb(), 0.2F) : MahouUi.darken(e.argb(), 0.45F),
                hover ? e.argb() : MahouUi.darken(e.argb(), 0.7F));
        MahouUi.roundOutline(g, bx, by, bw, bh, hover ? 0xFFFFFFFF : MahouUi.lighten(e.argb(), 0.2F));
        g.drawString(font, "↻", bx + 2, by + 2, 0xFFFFFFFF, false);
        addHit(bx, by, bw, bh, () -> requestTrial(e, rank));
        if (hover) {
            tooltip = toTooltip(List.of(Component.translatable("screen.isekai_mahou.paths.repeat", TrialsApi.title(e, rank)),
                    Component.translatable("screen.isekai_mahou.paths.repeat_desc").withStyle(ChatFormatting.GRAY)));
        }
        return bx;
    }

    /** Linha com o botão grande "Iniciar Provação" (cinza com a lista do que falta quando ainda não pode). */
    private Row trialButton(Element e, int rank, MagicData d) {
        return new Row() {
            @Override public int height() { return 19; }
            @Override public void draw(GuiGraphics g, int left, int top, int width, int mx, int my) {
                Font font = Minecraft.getInstance().font;
                List<Component> missing = new ArrayList<>();
                boolean can = TrialsApi.meetsRequirements(d, e, rank, missing);
                int bx = left + 16, by = top + 2, bw = width - 16, bh = 15;
                boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
                int topC, botC, edge;
                if (can) {
                    float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 260.0);
                    topC = hover ? MahouUi.lighten(e.argb(), 0.25F) : MahouUi.darken(e.argb(), 0.2F);
                    botC = hover ? e.argb() : MahouUi.darken(e.argb(), 0.55F);
                    edge = MahouUi.mix(GOLD, 0xFFFFFFFF, hover ? 1F : pulse * 0.5F);
                } else {
                    topC = 0xFF2A2634;
                    botC = 0xFF1C1924;
                    edge = 0xFF45405A;
                }
                MahouUi.roundRect(g, bx, by, bw, bh, topC, botC);
                MahouUi.roundOutline(g, bx, by, bw, bh, edge);
                Component label = Component.translatable("screen.isekai_mahou.paths.start_trial");
                g.drawString(font, MahouUi.fit(font, label, bw - 6), bx + (bw - Math.min(bw - 6, font.width(label))) / 2, by + 4,
                        can ? 0xFFFFFFFF : 0xFF8A8796, can);
                if (can) addHit(bx, by, bw, bh, () -> requestTrial(e, rank));
                if (hover) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(TrialsApi.title(e, rank));
                    if (can) {
                        lines.add(Component.translatable("screen.isekai_mahou.paths.start_trial_desc").withStyle(ChatFormatting.GRAY));
                    } else {
                        lines.add(Component.translatable("screen.isekai_mahou.paths.start_trial_missing").withStyle(ChatFormatting.RED));
                        for (Component m : missing) lines.add(Component.literal("✘ ").append(m).withStyle(ChatFormatting.GRAY));
                    }
                    tooltip = toTooltip(lines);
                }
            }
        };
    }

    private Row levelHeader(int level, int tier, boolean reached, MagicData d) {
        return new Row() {
            @Override public int height() { return 19; }
            @Override public void draw(GuiGraphics g, int left, int top, int width, int mx, int my) {
                Font font = Minecraft.getInstance().font;
                int col = Element.ARCANE.argb();
                PathShapes.disc(g, left + 9, top + 9, 7.5F, reached ? PathShapes.mix(col, 0xFFFFFFFF, 0.35F) : 0xFF231E30,
                        reached ? col : 0xFF3A3248);
                String num = String.valueOf(level);
                float scale = font.width(num) > 10 ? 0.75F : 1F;
                MahouUi.smallText(g, font, Component.literal(num), left + 9 - font.width(num) * scale / 2F + 0.5F,
                        top + (scale < 1 ? 6 : 5), scale, reached ? 0xFF1A1424 : 0xFF8A8796, false);
                g.drawString(font, Component.translatable("screen.isekai_mahou.paths.level_stage", level, tier),
                        left + 21, top + 5, reached ? 0xFFFFFFFF : 0xFFB0ADBC, reached);
                if (reached) {
                    g.drawString(font, "✔", left + width - 9, top + 5, OK, false);
                } else {
                    Component left2 = Component.translatable("screen.isekai_mahou.paths.levels_left", level - d.level());
                    g.drawString(font, left2, left + width - font.width(left2), top + 5, GOLD, false);
                }
            }
        };
    }

    private Row featHeader(Element e, int stage, boolean got, int amount) {
        return new Row() {
            @Override public int height() { return 13; }
            @Override public void draw(GuiGraphics g, int left, int top, int width, int mx, int my) {
                Font font = Minecraft.getInstance().font;
                g.drawString(font, got ? "★" : "☆", left + 4, top + 2, got ? GOLD : MUTED, false);
                Component name = Component.translatable("screen.isekai_mahou.paths.feat_name", TrialsApi.roman(stage));
                g.drawString(font, name, left + 16, top + 2, got ? 0xFFFFFFFF : 0xFFD6D2DE, false);
                Component reward = got ? Component.translatable("screen.isekai_mahou.paths.feat_got")
                        : Component.translatable("screen.isekai_mahou.paths.feat_reward", amount, e.displayName());
                g.drawString(font, MahouUi.fit(font, reward, width - 24 - font.width(name)), left + width
                        - Math.min(font.width(reward), width - 24 - font.width(name)), top + 2, got ? OK : 0xFF8FD8FF, false);
            }
        };
    }

    private Row req(PathReq r, MagicData d, Player player) {
        return new Row() {
            @Override public int height() { return ROW + 1; }
            @Override public void draw(GuiGraphics g, int left, int top, int width, int mx, int my) {
                Font font = Minecraft.getInstance().font;
                boolean met = r.met(d, player);
                g.drawString(font, met ? "✔" : "✘", left + 6, top + 1, met ? OK : BAD, false);
                String prog = "";
                if (r.countable()) {
                    int cur = Math.min(r.current(d, player), r.target());
                    prog = cur / r.divisor() + "/" + r.target() / r.divisor();
                }
                int progW = prog.isEmpty() ? 0 : font.width(prog) + 4;
                int labelW = width - 16 - progW;
                Component label = r.label();
                g.drawString(font, MahouUi.fit(font, label, labelW), left + 16, top + 1, met ? 0xFFB8E6B8 : 0xFFD6D2DE, false);
                if (!prog.isEmpty()) g.drawString(font, prog, left + width - font.width(prog), top + 1, met ? OK : GOLD, false);
                if (font.width(label) > labelW && mx >= left && mx < left + width && my >= top && my < top + ROW) {
                    tooltip = toTooltip(List.of(label));
                }
            }
        };
    }

    /** Glifos em fichas (ícone 16 px + nome), quebrando linha conforme a largura. */
    private List<Row> chips(List<Glyph> glyphs, MagicData d, boolean bright, boolean arcane) {
        Font font = Minecraft.getInstance().font;
        List<Row> rows = new ArrayList<>();
        List<Glyph> line = new ArrayList<>();
        int lineW = 16;
        for (Glyph gl : glyphs) {
            int cw = chipWidth(font, gl);
            if (!line.isEmpty() && lineW + cw > dw) {
                rows.add(chipRow(new ArrayList<>(line), d, bright, arcane));
                line.clear();
                lineW = 16;
            }
            line.add(gl);
            lineW += cw;
        }
        if (!line.isEmpty()) rows.add(chipRow(line, d, bright, arcane));
        return rows;
    }

    private int chipWidth(Font font, Glyph gl) {
        return Math.min(dw - 16, 20 + font.width(gl.displayName()) + 4);
    }

    private Row chipRow(List<Glyph> glyphs, MagicData d, boolean bright, boolean arcane) {
        return new Row() {
            @Override public int height() { return CHIP_H; }
            @Override public void draw(GuiGraphics g, int left, int top, int width, int mx, int my) {
                Font font = Minecraft.getInstance().font;
                int cxp = left + 16;
                for (Glyph gl : glyphs) {
                    int cw = chipWidth(font, gl);
                    boolean known = d.knowsGlyph(gl.id());
                    boolean circleLocked = gl.minCircle() > d.circle();
                    int col = GlyphArt.color(gl);
                    boolean hover = mx >= cxp && mx < cxp + cw - 3 && my >= top && my < top + CHIP_H - 1;
                    g.fill(cxp, top + 1, cxp + cw - 3, top + CHIP_H - 1, hover ? 0x40FFFFFF : PathShapes.alpha(col, known ? 0x30 : 0x12));
                    if (!known) g.setColor(0.5F, 0.5F, 0.55F, 0.85F);
                    GlyphArt.glyph(g, gl, cxp + 1, top + 1, 16);
                    g.setColor(1F, 1F, 1F, 1F);
                    if (circleLocked) {
                        MahouUi.smallText(g, font, Component.literal("C" + gl.minCircle()), cxp + 1, top + 11, 0.6F, 0xFFFF9A6A, true);
                    }
                    int tc = known ? 0xFFFFFFFF : bright ? 0xFFC8C4D2 : 0xFF8A8796;
                    g.drawString(font, MahouUi.fit(font, gl.displayName(), cw - 24), cxp + 20, top + 5, tc, false);
                    if (hover) tooltip = glyphTooltip(gl, known, d, arcane);
                    cxp += cw;
                }
            }
        };
    }

    private List<FormattedCharSequence> glyphTooltip(Glyph gl, boolean known, MagicData d, boolean arcane) {
        List<Component> lines = new ArrayList<>();
        lines.add(gl.displayName().withColor(GlyphArt.color(gl)).withStyle(ChatFormatting.BOLD));
        lines.add(PathProgress.kindName(gl.kind()).withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(gl.element().displayName()));
        lines.add(gl.description().withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.isekai_mahou.paths.glyph_cost", gl.cost()).withStyle(ChatFormatting.AQUA));
        if (arcane) {
            lines.add(Component.translatable("screen.isekai_mahou.paths.glyph_level", Progression.arcaneUnlockLevel(PathRules.arcaneTier(gl)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (gl.minCircle() > 1) {
            lines.add(Component.translatable("screen.isekai_mahou.paths.glyph_circle", gl.minCircle())
                    .withStyle(gl.minCircle() > d.circle() ? ChatFormatting.RED : ChatFormatting.GOLD));
        }
        lines.add(known ? Component.translatable("screen.isekai_mahou.paths.glyph_known").withStyle(ChatFormatting.GREEN)
                : Component.translatable("screen.isekai_mahou.paths.glyph_unknown").withStyle(ChatFormatting.DARK_GRAY));
        return toTooltip(lines);
    }

    // ---------------------------------------------------------------- Utilidades

    /** Registra um botão da lista (só se estiver inteiro na parte visível). */
    private void addHit(int bx, int by, int bw, int bh, Runnable action) {
        if (by >= listTop && by + bh <= listBottom) hits.add(new Hit(bx, by, bw, bh, action));
    }

    private static List<FormattedCharSequence> toTooltip(List<Component> lines) {
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : lines) out.addAll(font.split(c, 230));
        return out;
    }

    private static float[] rgb(int color) {
        return new float[]{((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F};
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (Hit hit : hits) {
            if (hit.contains(mx, my)) {
                hit.action().run();
                return true;
            }
        }
        Element e = hoveredNode((int) mx, (int) my);
        if (e != null) {
            select(e);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= dx - 4 && mx < x + w && my >= y && my < y + h) {
            scroll -= scrollY * 14;
            return true;
        }
        return false;
    }
}
