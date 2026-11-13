package com.renanpontes.isekaimahou.client.perf;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.MahouUi;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Sugestão do 1º início (cliente, aparece UMA vez, no menu principal): "Detectamos: … Sugerimos o modo OK." com
 * [Usar OK] [Ver opções] [Agora não]. Nada é aplicado sem o jogador clicar.
 */
public class PerfSuggestScreen extends Screen {
    @Nullable
    private final Screen parent;
    private final PcPreset suggestion;
    private int left, top, w, h;
    private List<FormattedCharSequence> body = List.of();

    public PerfSuggestScreen(@Nullable Screen parent, PcPreset suggestion) {
        super(Component.translatable("screen.isekai_mahou.perf.suggest.title"));
        this.parent = parent;
        this.suggestion = suggestion;
    }

    @Override
    protected void init() {
        w = Math.min(width - 16, 300);
        body = font.split(Component.translatable("screen.isekai_mahou.perf.suggest.body", HardwareProbe.info().summary(),
                suggestion.title(), suggestion.description()), w - 24);
        h = 34 + body.size() * 10 + 34;
        left = (width - w) / 2;
        top = (height - h) / 2;
        int bw = (w - 24 - 8) / 3;
        int by = top + h - 28;
        addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.perf.suggest.use", suggestion.title()), b -> {
            suggestion.apply();
            minecraft.setScreen(parent);
        }).bounds(left + 12, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.perf.suggest.more"),
                b -> minecraft.setScreen(new PerfScreen(parent).select(suggestion))).bounds(left + 12 + bw + 4, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.perf.suggest.later"), b -> onClose())
                .bounds(left + 12 + (bw + 4) * 2, by, bw, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        MahouUi.frame(g, left, top, w, h, suggestion.accent());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, left + w / 2, top + 10, MahouUi.GOLD);
        int y = top + 26;
        for (FormattedCharSequence line : body) {
            g.drawString(font, line, left + 12, y, MahouUi.TEXT, false);
            y += 10;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
