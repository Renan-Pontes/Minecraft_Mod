package com.renanpontes.isekaimahou.client.perf;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/**
 * Tela "Desempenho — Modo do PC" (cliente). Quatro cartões (Fraco / OK / Bom / Ultra): clicar num cartão mostra a
 * lista do que vai mudar (opção: atual → nova) e o botão "Aplicar" confirma. Mostra o hardware detectado e o modo
 * sugerido. Aberta pela engrenagem do menu (B), pelo botão "Config" da lista de mods e pela sugestão do 1º início.
 */
public class PerfScreen extends Screen {
    private static final int MAX_W = 460, MAX_H = 300;
    private static final int CARD_H = 50;

    @Nullable
    private final Screen parent;
    private PcPreset selected;
    private List<PcPreset.Change> changes = List.of();
    private int left, top, panelW, panelH, cardsY, listY;
    private Button applyBtn;
    /** Mensagem curta depois de aplicar ("Pronto!"), com o horário para sumir. */
    private Component flash;
    private long flashUntil;

    public PerfScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.isekai_mahou.perf.title"));
        this.parent = parent;
        PcPreset cur = PcPreset.of(MahouConfig.safe(MahouConfig.PC_MODE));
        this.selected = cur != null ? cur : HardwareProbe.info().suggestion();
    }

    /** Abre já com um preset escolhido (a sugestão do 1º início usa isto). */
    public PerfScreen select(PcPreset p) {
        this.selected = p;
        return this;
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 16, MAX_W);
        panelH = Math.min(height - 16, MAX_H);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        cardsY = top + 36;
        listY = cardsY + CARD_H + 16;
        changes = selected.diff();

        int by = top + panelH - 26;
        int bw = Math.min(104, (panelW - 30) / 4);
        int bx = left + (panelW - (bw * 4 + 18)) / 2;
        applyBtn = addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.perf.apply"), b -> apply())
                .bounds(bx, by, bw, 20).build());
        applyBtn.active = !changes.isEmpty();
        addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.perf.video"), b ->
                minecraft.setScreen(new VideoSettingsScreen(this, minecraft, minecraft.options))).bounds(bx + bw + 6, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.perf.all"), b -> openModConfig())
                .bounds(bx + (bw + 6) * 2, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(bx + (bw + 6) * 3, by, bw, 20).build());
    }

    private void openModConfig() {
        ModList.get().getModContainerById(IsekaiMahou.MODID)
                .ifPresent(c -> minecraft.setScreen(new ConfigurationScreen(c, this)));
    }

    private void apply() {
        if (changes.isEmpty()) return;
        selected.apply();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.0F));
        flash = Component.translatable("screen.isekai_mahou.perf.applied", selected.title()).withStyle(ChatFormatting.GREEN);
        flashUntil = net.minecraft.Util.getMillis() + 4000L;
        rebuildWidgets();
    }

    private int cardW() {
        return (panelW - 24 - 3 * 6) / 4;
    }

    private int cardX(int i) {
        return left + 12 + i * (cardW() + 6);
    }

    @Nullable
    private PcPreset cardAt(double mx, double my) {
        if (my < cardsY || my >= cardsY + CARD_H) return null;
        for (int i = 0; i < 4; i++) {
            int x = cardX(i);
            if (mx >= x && mx < x + cardW()) return PcPreset.values()[i];
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        PcPreset p = cardAt(mx, my);
        if (p != null && button == 0) {
            if (p != selected) {
                selected = p;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        MahouUi.frame(g, left, top, panelW, panelH, selected.accent());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // Placa do título
        int tw = Math.min(panelW - 16, font.width(title) + 22);
        int px = left + (panelW - tw) / 2, py = top - 6;
        MahouUi.roundRect(g, px, py, tw, 11, 0xFF2C2238, 0xFF14101C);
        MahouUi.roundOutline(g, px, py, tw, 11, MahouUi.GOLD);
        g.drawString(font, MahouUi.fit(font, title, tw - 22), px + 11, py + 2, MahouUi.GOLD_PALE, false);

        // Hardware detectado + sugestão
        HardwareProbe.Info hw = HardwareProbe.info();
        g.drawString(font, MahouUi.fit(font, hw.summary(), panelW - 24), left + 12, top + 10, MahouUi.TEXT_DIM, false);
        Component sug = Component.translatable("screen.isekai_mahou.perf.suggested", hw.suggestion().title());
        g.drawString(font, MahouUi.fit(font, sug, panelW - 24), left + 12, top + 21, MahouUi.GOLD, false);

        PcPreset current = PcPreset.of(MahouConfig.safe(MahouConfig.PC_MODE));
        PcPreset hover = cardAt(mouseX, mouseY);
        for (int i = 0; i < 4; i++) renderCard(g, PcPreset.values()[i], cardX(i), cardsY, cardW(), current, hover);

        // O que vai mudar
        int x = left + 12, w = panelW - 24;
        g.drawString(font, Component.translatable("screen.isekai_mahou.perf.changes", selected.title()), x, listY - 11, MahouUi.GOLD_PALE, false);
        g.fill(x, listY - 2, x + w, listY - 1, MahouUi.alpha(MahouUi.GOLD_DIM, 0x80));
        int bottom = top + panelH - 32;
        if (changes.isEmpty()) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.perf.no_changes"), x + 2, listY + 3, MahouUi.TEXT_DIM, false);
        } else {
            int colW = w / 2 - 4;
            int rows = Math.max(1, (bottom - listY - 12) / 10);
            boolean twoCols = changes.size() > rows; // uma coluna sempre que couber (linhas longas)
            int perCol = twoCols ? (changes.size() + 1) / 2 : changes.size();
            for (int i = 0; i < changes.size(); i++) {
                int col = twoCols ? i / perCol : 0;
                int row = twoCols ? i % perCol : i;
                int ry = listY + 2 + row * 10;
                if (ry > bottom - 20) continue;
                PcPreset.Change c = changes.get(i);
                Component line = Component.empty().append(c.label().copy().withStyle(ChatFormatting.GRAY)).append(": ")
                        .append(c.from().copy().withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(" → ").withStyle(ChatFormatting.GOLD))
                        .append(c.to().copy().withStyle(ChatFormatting.WHITE));
                g.drawString(font, MahouUi.fit(font, line, twoCols ? colW : w), x + 2 + col * (colW + 8), ry, 0xFFFFFFFF, false);
            }
            if (changes.stream().anyMatch(PcPreset.Change::reloads)) {
                g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.perf.reload_hint"), w),
                        x + 2, bottom - 9, MahouUi.TEXT_DIM, false);
            }
        }
        if (!DhBridge.present()) {
            Component dh = Component.translatable("screen.isekai_mahou.perf.dh_missing");
            g.drawString(font, MahouUi.fit(font, dh, w), x + 2, bottom - (changes.isEmpty() ? 9 : 19), 0xFF7F7898, false);
        }
        if (flash != null && net.minecraft.Util.getMillis() < flashUntil) {
            int fw = font.width(flash);
            g.drawString(font, flash, left + (panelW - fw) / 2, listY + 16, 0xFFFFFFFF, true);
        }

        if (hover != null) {
            g.renderTooltip(font, font.split(hover.description(), 220), mouseX, mouseY);
        }
    }

    private void renderCard(GuiGraphics g, PcPreset p, int x, int y, int w, @Nullable PcPreset current, @Nullable PcPreset hover) {
        boolean sel = p == selected;
        int accent = p.accent();
        if (sel) {
            MahouUi.roundRect(g, x, y, w, CARD_H, MahouUi.darken(accent, 0.55F), 0xFF14101C);
            MahouUi.roundOutline(g, x, y, w, CARD_H, accent);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, MahouUi.alpha(MahouUi.lighten(accent, 0.4F), 0xC0));
        } else {
            boolean h = p == hover;
            MahouUi.roundRect(g, x, y, w, CARD_H, h ? 0xD8342A4C : 0xB81E182C, h ? 0xD81C1628 : 0xB8120E1A);
            MahouUi.roundOutline(g, x, y, w, CARD_H, h ? MahouUi.alpha(accent, 0xC0) : 0xFF3E3458);
        }
        // Barrinhas de "força" (1 a 4)
        int bars = p.ordinal() + 1;
        for (int b = 0; b < 4; b++) {
            int bh = 3 + b * 2;
            int bx = x + w - 8 - (3 - b) * 4;
            g.fill(bx, y + 14 - bh, bx + 3, y + 14, b < bars ? accent : 0x50FFFFFF);
        }
        g.drawString(font, MahouUi.fit(font, p.title(), w - 26), x + 5, y + 5, sel ? MahouUi.GOLD : MahouUi.TEXT, true);
        Component fx = PcPreset.fxName(p.graphics);
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.perf.card_fx", fx), x + 5, y + 18, 0.75F, MahouUi.TEXT_DIM, false);
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.perf.card_rd", p.renderDistance), x + 5, y + 26, 0.75F, MahouUi.TEXT_DIM, false);
        // Selos: "ATUAL" e "SUGERIDO"
        int sy = y + CARD_H - 10;
        int sx = x + 4;
        if (p == current) sx = badge(g, sx, sy, Component.translatable("screen.isekai_mahou.perf.badge_current"), 0xFF3C7A4A);
        if (p == HardwareProbe.info().suggestion()) badge(g, sx, sy, Component.translatable("screen.isekai_mahou.perf.badge_suggested"), 0xFF8A6A1E);
    }

    private int badge(GuiGraphics g, int x, int y, Component text, int color) {
        float scale = 0.65F;
        int tw = (int) Math.ceil(font.width(text) * scale) + 5;
        MahouUi.roundRect(g, x, y, tw, 8, MahouUi.lighten(color, 0.2F), color);
        MahouUi.smallText(g, font, text, x + 3, y + 1.5F, scale, 0xFFFFFFFF, false);
        return x + tw + 3;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
