package com.renanpontes.isekaimahou.feature.settlements.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;
import com.renanpontes.isekaimahou.feature.settlements.SkyCity;
import com.renanpontes.isekaimahou.feature.settlements.WaterCity;
import com.renanpontes.isekaimahou.feature.settlements.network.SettlementsNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Carta de Fundação: escolha do estilo (gravura, descrição e bônus de cada um) e do nome da cidade. Confirmar manda o
 * pedido; o servidor confere tudo de novo e funda.
 * <p>Onda 5: são 7 estilos (Reino, Desértico, Nórdico, Oriental, Élfico, Sombrio, Anão) — a carta mostra 3 cartões por
 * vez e as setas (ou a roda do mouse / ← →) passam os outros; as bolinhas embaixo mostram onde você está.
 * <p>Onda 7 (sky_ui): embaixo dos cartões fica a escolha do TERRENO — "No chão" ou "Ilha Celeste (SkyLand)", que
 * combina com qualquer estilo. Sem o Núcleo Celeste no inventário a Ilha Celeste aparece apagada, com a dica de como
 * conseguir. Com ela escolhida o holograma atrás da tela já mostra a ilha no céu; fechar a tela (Esc) volta para a
 * prévia para mover/girar — estilo, nome e terreno ficam guardados enquanto a Carta estiver na mão.
 */
public class FoundingScreen extends Screen {
    private static final int MAX_W = 480, MAX_H = 270;
    /** Faixa reservada embaixo dos cartões: bolinhas, linha do nome + terreno, caixa do nome e botões. */
    private static final int BOTTOM = 70;
    private static final int SKY = 0xFF000000 | SkyPreview.SKY;
    /** Onda 8 (water_city): cor do botão "Sobre a Água". */
    private static final int AQUA = 0xFF000000 | CityPlacer.AQUA;
    /** Altura dos dois botões de terreno. */
    private static final int ROW_H = 13;
    /** Escolhas guardadas entre uma abertura e outra (o jogador fecha a tela para ajustar a prévia e volta). */
    @Nullable private static SettlementStyle keptStyle;
    @Nullable private static String keptName;
    private static int keptFirst;

    private final BlockPos pos;
    /** Rotação da praça escolhida na prévia (0..3). */
    private final int rot;
    private SettlementStyle chosen = SettlementStyle.KINGDOM;
    private static final int VISIBLE = 3;
    /** Primeiro estilo mostrado (os cartões são {@code first .. first+2}). */
    private int first;
    private EditBox name;
    private Button confirmButton;
    private int left, top, pw, ph;
    private int age;
    /** Dica do item sob o mouse (uma por quadro), desenhada por cima de tudo. */
    @Nullable private List<Component> tip;
    /** Onde os dois botões de terreno foram desenhados neste quadro: x, largura (chão) e x, largura (céu). */
    private final int[] chips = new int[6];

    public FoundingScreen(BlockPos pos) {
        this(pos, 0);
    }

    public FoundingScreen(BlockPos pos, int rot) {
        super(Component.translatable("settlements.isekai_mahou.found.title"));
        this.pos = pos;
        this.rot = rot;
        if (keptStyle != null) {
            chosen = keptStyle;
            first = keptFirst;
        }
    }

    /** A Carta saiu da mão (guardada ou gasta): a próxima tela começa do zero. */
    static void forget() {
        keptStyle = null;
        keptName = null;
        keptFirst = 0;
    }

    @Override
    protected void init() {
        pw = Math.min(MAX_W, width - 16);
        ph = Math.min(MAX_H, height - 16);
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        String old = name != null ? name.getValue() : keptName != null ? keptName
                : Component.translatable("settlements.isekai_mahou.found.default_name",
                        minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().getName() : "").getString();
        name = new EditBox(font, left + 12, top + ph - 26, pw - 200, 16, Component.translatable("settlements.isekai_mahou.found.name"));
        name.setMaxLength(32);
        name.setValue(old);
        addRenderableWidget(name);
        confirmButton = addRenderableWidget(Button.builder(Component.translatable("settlements.isekai_mahou.found.confirm"), b -> confirm())
                .bounds(left + pw - 182, top + ph - 28, 96, 20).build());
        addRenderableWidget(Button.builder(net.minecraft.network.chat.CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(left + pw - 82, top + ph - 28, 70, 20).build());
        // setas do carrossel, por cima das bordas dos cartões
        int ay = top + 20 + (ph - BOTTOM) / 2 - 10;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> page(-1)).bounds(left - 2, ay, 14, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> page(1)).bounds(left + pw - 12, ay, 14, 20).build());
        // a resposta do servidor para o terreno escolhido (pode? em que altitude?) aparece na linha do terreno
        CityPlacer.probeNow();
    }

    private static java.util.List<SettlementStyle> styles() {
        return SettlementStyle.playable();
    }

    private int maxFirst() {
        return Math.max(0, styles().size() - VISIBLE);
    }

    private void page(int d) {
        int old = first;
        first = Math.max(0, Math.min(maxFirst(), first + d));
        if (first != old && minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2F));
        }
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        // perdeu o Núcleo Celeste com a tela aberta: a Ilha Celeste deixa de valer
        if (CityPlacer.sky() && !CityPlacer.hasSkyCore()) CityPlacer.setTerrain(SkyCity.GROUND);
        // a resposta não chegou (ou envelheceu): pergunta de novo, sem enxurrada
        if (age % 20 == 0 && CityPlacer.foundState() < 0) CityPlacer.probeNow();
        if (confirmButton != null) confirmButton.active = CityPlacer.foundState() != 0;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy != 0) {
            page(dy > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // ← → só quando não se está digitando o nome
        if (name == null || !name.isFocused()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
                page(-1);
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
                page(1);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    private void confirm() {
        PacketDistributor.sendToServer(new SettlementsNetwork.Found(pos, chosen.id, name.getValue(), rot, CityPlacer.terrain()));
        onClose();
    }

    /** Fechar (Esc, Cancelar ou Fundar) guarda as escolhas: se a cidade nascer, a Carta some e tudo é esquecido. */
    @Override
    public void removed() {
        keptStyle = chosen;
        keptFirst = first;
        if (name != null) keptName = name.getValue();
        super.removed();
    }

    private int cardW() {
        return (pw - 40) / VISIBLE;
    }

    private int cardH() {
        return ph - BOTTOM;
    }

    /** Tudo é desenhado no fundo: os widgets (nome, botões) ficam por cima. */
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        tip = null;
        g.fillGradient(left, top, left + pw, top + ph, 0xF4211810, 0xF4140E09);
        frame(g, left, top, pw, ph, 0xFFC9A55A);
        Font f = font;
        Component title = Component.translatable("settlements.isekai_mahou.found.title");
        g.drawString(f, title, left + (pw - f.width(title)) / 2, top + 6, 0xFFFFD86B, true);
        int cw = cardW(), ch = cardH();
        first = Math.max(0, Math.min(maxFirst(), first));
        java.util.List<SettlementStyle> all = styles();
        // bolinhas: uma por estilo; as 3 visíveis mais claras, a escolhida na cor do estilo
        int dots = all.size() * 8 - 2, dx0 = left + (pw - dots) / 2, dy0 = top + 20 + ch + 2;
        for (int i = 0; i < all.size(); i++) {
            boolean shown = i >= first && i < first + VISIBLE;
            int c = all.get(i) == chosen ? 0xFF000000 | all.get(i).color : shown ? 0xFFC9A55A : 0x60C9A55A;
            g.fill(dx0 + i * 8, dy0, dx0 + i * 8 + 4, dy0 + 3, c);
        }
        for (int i = 0; i < VISIBLE && first + i < all.size(); i++) {
            SettlementStyle s = all.get(first + i);
            int cx = left + 12 + i * (cw + 8), cy = top + 20;
            boolean sel = s == chosen;
            boolean hover = mx >= cx && my >= cy && mx < cx + cw && my < cy + ch;
            g.fill(cx, cy, cx + cw, cy + ch, sel ? 0xE0302418 : hover ? 0xD0281E14 : 0xC0201810);
            frame(g, cx, cy, cw, ch, sel ? 0xFF000000 | s.color : hover ? 0xFFC9A55A : 0x70C9A55A);
            int ih = Math.min(70, cw * 80 / 128);
            ResourceLocation art = IsekaiMahou.id("textures/gui/settlements/style_" + s.id + ".png");
            g.blit(art, cx + 3, cy + 3, cw - 6, ih, 0, 0, 128, 80, 128, 80);
            int ty = cy + ih + 6;
            g.drawString(f, s.displayName(), cx + 5, ty, 0xFF000000 | s.color, true);
            ty += 12;
            boolean cut = false;
            for (FormattedCharSequence line : f.split(s.description(), cw - 10)) {
                if (ty > cy + ch - 30) {
                    cut = true;
                    break;
                }
                g.drawString(f, line, cx + 5, ty, 0xFFB8A27A, false);
                ty += 9;
            }
            ty += 3;
            for (FormattedCharSequence line : f.split(s.bonus(), cw - 10)) {
                if (ty > cy + ch - 10) {
                    cut = true;
                    break;
                }
                g.drawString(f, line, cx + 5, ty, 0xFFFFD86B, false);
                ty += 9;
            }
            // texto que não coube no cartão (janela pequena): inteiro na dica ao passar o mouse
            if (cut && hover) {
                tip = List.of(s.displayName().copy().withColor(0xFF000000 | s.color), s.description().withColor(0xFFB8A27A),
                        s.bonus().withColor(0xFFFFD86B));
            }
        }
        Component nameLabel = Component.translatable("settlements.isekai_mahou.found.name");
        g.drawString(f, nameLabel, left + 12, top + ph - 40, 0xFFB8A27A, false);
        terrainRow(g, f, mx, my, left + 12 + f.width(nameLabel) + 10);
    }

    private int rowY() {
        return top + ph - 43;
    }

    /**
     * Onda 7 — o terreno, na mesma linha do rótulo do nome (não rouba altura dos cartões): dois botões ("No chão" /
     * "Ilha Celeste (SkyLand)") e, ao lado, UMA frase curta: a altitude em que a ilha nasce, o motivo de não poder
     * fundar ali, ou o que falta (o Núcleo Celeste).
     */
    private void terrainRow(GuiGraphics g, Font f, int mx, int my, int x) {
        int y = rowY(), h = ROW_H;
        boolean core = CityPlacer.hasSkyCore();
        boolean sky = CityPlacer.sky();
        // onda 8 (water_city): terceira opção, "Sobre a Água" (sem item: o custo é achar o lugar) — entre o chão e o céu
        boolean water = CityPlacer.water();
        Component ground = SettlementsLang.tr("sky.terrain.ground"), island = SettlementsLang.tr("sky.terrain.skyland");
        Component onWater = SettlementsLang.tr("water.terrain");
        int gw = f.width(ground) + 12, sw = f.width(island) + 12, ww = f.width(onWater) + 12;
        chips[0] = x;
        chips[1] = gw;
        chips[4] = x + gw + 4;
        chips[5] = ww;
        chips[2] = chips[4] + ww + 4;
        chips[3] = sw;
        chip(g, f, ground, x, y, gw, h, !sky && !water, true, 0xFFFFD86B, mx, my);
        chip(g, f, onWater, chips[4], y, ww, h, water, true, AQUA, mx, my);
        chip(g, f, island, chips[2], y, sw, h, sky, core, SKY, mx, my);
        if (mx >= chips[4] && mx < chips[4] + ww && my >= y && my < y + h) {
            tip = new ArrayList<>();
            tip.add(onWater.copy().withColor(AQUA));
            tip.add(SettlementsLang.tr("water.ui.pick.tip").withColor(0xFFB8A27A));
            tip.add(SettlementsLang.tr("water.ui.pick.cost").withColor(0xFFFFD86B));
            tip.add(SettlementsLang.tr("water.ui.pick.preview").withColor(0xFF7D6C52));
        }
        boolean overSky = mx >= chips[2] && mx < chips[2] + sw && my >= y && my < y + h;
        if (overSky) {
            tip = new ArrayList<>();
            tip.add(island.copy().withColor(SKY));
            tip.add(SettlementsLang.tr("sky.ui.pick.tip").withColor(0xFFB8A27A));
            tip.add(SettlementsLang.tr(core ? "sky.ui.pick.cost" : "sky.ui.core.missing").withColor(core ? 0xFFFFD86B : 0xFFE8735E));
            if (!core) tip.add(SettlementsLang.tr("sky.ui.core.recipe").withColor(0xFFB8A27A));
            else tip.add(SettlementsLang.tr("sky.ui.pick.preview").withColor(0xFF7D6C52));
        }
        // a frase ao lado: uma só, cortada na largura que sobra (inteira na dica ao passar o mouse)
        int tx = chips[2] + sw + 8, tw = left + pw - 12 - tx;
        if (tw < 40) return;
        Component note;
        int color;
        int state = CityPlacer.foundState();
        if (state == 0) {
            note = CityPlacer.foundProblem();
            color = 0xFFE8735E;
        } else if (sky) {
            int alt = CityPlacer.skyY();
            note = alt > 0 ? SettlementsLang.tr("sky.ui.altitude", alt) : SettlementsLang.tr("sky.ui.checking");
            color = SKY;
        } else if (water) {
            int pct = CityPlacer.waterPct();
            note = pct >= 0 ? SettlementsLang.tr("water.ui.share", pct) : SettlementsLang.tr("water.ui.checking");
            color = AQUA;
        } else if (!core) {
            note = SettlementsLang.tr("sky.ui.need_core");
            color = 0xFF7D6C52;
        } else {
            return;
        }
        List<FormattedCharSequence> lines = f.split(note, tw);
        if (lines.isEmpty()) return;
        g.drawString(f, lines.get(0), tx, y + 3, color, false);
        if (lines.size() > 1 && tip == null && mx >= tx && mx < tx + tw && my >= y && my < y + h) tip = List.of(note.copy().withColor(color));
    }

    private void chip(GuiGraphics g, Font f, Component label, int x, int y, int w, int h, boolean selected, boolean enabled, int accent,
                      int mx, int my) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, selected ? 0xE0302418 : hover ? 0xD0281E14 : 0xC0201810);
        frame(g, x, y, w, h, selected ? accent : hover ? 0xFFC9A55A : enabled ? 0x70C9A55A : 0x40C9A55A);
        g.drawString(f, label, x + 6, y + (h - 8) / 2 + 1, selected ? accent : enabled ? 0xFFB8A27A : 0xFF5E5140, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (tip == null) return;
        // as dicas têm frases longas (receita do Núcleo, texto dos estilos): quebradas numa largura confortável
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component c : tip) lines.addAll(font.split(c, 230));
        g.renderTooltip(font, lines, mx, my);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cw = cardW(), ch = cardH();
        java.util.List<SettlementStyle> all = styles();
        for (int i = 0; i < VISIBLE && first + i < all.size(); i++) {
            int cx = left + 12 + i * (cw + 8), cy = top + 20;
            if (mx >= cx && my >= cy && mx < cx + cw && my < cy + ch) {
                chosen = all.get(first + i);
                if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
                return true;
            }
        }
        // onda 7: os dois botões de terreno
        int y = rowY();
        if (my >= y && my < y + ROW_H) {
            if (mx >= chips[0] && mx < chips[0] + chips[1]) {
                pickTerrain(SkyCity.GROUND);
                return true;
            }
            if (mx >= chips[4] && mx < chips[4] + chips[5]) {
                pickTerrain(WaterCity.WATER);
                return true;
            }
            if (mx >= chips[2] && mx < chips[2] + chips[3]) {
                if (CityPlacer.hasSkyCore()) pickTerrain(SkyCity.SKYLAND);
                else if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void pickTerrain(String terrain) {
        if (terrain.equals(CityPlacer.terrain())) return;
        CityPlacer.setTerrain(terrain);
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SkyCity.SKYLAND.equals(terrain) ? SoundEvents.AMETHYST_BLOCK_CHIME
                            : WaterCity.WATER.equals(terrain) ? SoundEvents.BUCKET_FILL : SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
