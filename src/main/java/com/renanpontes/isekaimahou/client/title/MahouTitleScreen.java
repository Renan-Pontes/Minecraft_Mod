package com.renanpontes.isekaimahou.client.title;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.MahouUi;

import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ModListScreen;

/**
 * Menu principal do Isekai Mahou (v1.0 — A3). É uma SUBCLASSE do {@link TitleScreen}: o vanilla monta os botões (e o
 * NeoForge o "Mods"), outros mods que procuram {@code instanceof TitleScreen} continuam funcionando, e os botões que
 * eles acrescentam ({@code ScreenEvent.Init.Post}) ficam onde eles puseram. Os botões conhecidos do vanilla são
 * trocados por {@link RuneButton}s com os MESMOS destinos (o clique chama o botão original: avisos de multijogador,
 * Realms, banimento... iguais). Fundo animado ({@link TitleSky}) sobre o panorama, logo desenhado ({@link TitleLogo}),
 * frase aleatória, versões embaixo e um atalho para o menu clássico.
 */
public class MahouTitleScreen extends TitleScreen {
    private static final int SPLASHES = 16;
    private static final ResourceLocation LANGUAGE_ICON = ResourceLocation.withDefaultNamespace("icon/language");
    private static final ResourceLocation ACCESSIBILITY_ICON = ResourceLocation.withDefaultNamespace("icon/accessibility");

    private final TitleSky sky = new TitleSky();
    private final boolean fadeIn;
    private long openedAt;
    private float logoScale = 3F;
    private int logoTop = 20;
    @Nullable
    private Component splash;

    public MahouTitleScreen(boolean fadeIn) {
        super(false, null);
        this.fadeIn = fadeIn;
    }

    // =====================================================================================
    //  Botões
    // =====================================================================================
    @Override
    protected void init() {
        super.init(); // botões do vanilla + "Mods" do NeoForge + notificações do Realms
        if (splash == null) {
            splash = Component.translatable("title.isekai_mahou.splash." + Util.getMillis() % SPLASHES);
        }
        logoScale = TitleLogo.scale(font, width);
        logoTop = Math.max(14, height / 4 - TitleLogo.height(logoScale) + 8);
        int by = Math.max(height / 4 + 40, logoTop + TitleLogo.height(logoScale) + 14);
        by = Math.min(by, height - 4 * 24 - 34);

        Button single = null, multi = null, realms = null, mods = null, options = null, quit = null, lang = null, access = null;
        for (GuiEventListener l : new ArrayList<>(children())) {
            if (!(l instanceof Button b)) continue;
            String key = key(b.getMessage());
            if (key == null) continue;
            switch (key) {
                case "menu.singleplayer" -> single = b;
                case "menu.multiplayer" -> multi = b;
                case "menu.online" -> realms = b;
                case "fml.menu.mods" -> mods = b;
                case "menu.options" -> options = b;
                case "menu.quit" -> quit = b;
                case "options.language" -> lang = b;
                case "options.accessibility", "accessibility.onboarding.accessibility.button" -> access = b;
                default -> { }
            }
        }
        for (Button b : new Button[] {single, multi, realms, mods, options, quit, lang, access}) {
            if (b != null) removeWidget(b);
        }

        int cx = width / 2;
        add(single, cx - 100, by, 200, Component.translatable("title.isekai_mahou.singleplayer"), true,
                () -> minecraft.setScreen(new SelectWorldScreen(this)));
        add(multi, cx - 100, by + 24, 200, Component.translatable("title.isekai_mahou.multiplayer"), true,
                () -> minecraft.setScreen(minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this)));
        // Realms e Mods dividem a linha (se o Realms não existir, Mods ocupa tudo)
        if (realms != null) {
            add(realms, cx - 100, by + 48, 98, Component.translatable("title.isekai_mahou.realms"), false, null);
            add(mods, cx + 2, by + 48, 98, Component.translatable("title.isekai_mahou.mods"), false,
                    () -> minecraft.setScreen(new ModListScreen(this)));
        } else {
            add(mods, cx - 100, by + 48, 200, Component.translatable("title.isekai_mahou.mods"), false,
                    () -> minecraft.setScreen(new ModListScreen(this)));
        }
        add(options, cx - 100, by + 72, 98, Component.translatable("title.isekai_mahou.options"), false,
                () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
        add(quit, cx + 2, by + 72, 98, Component.translatable("title.isekai_mahou.quit"), false, () -> minecraft.stop());
        if (lang != null) addIcon(lang, cx - 124, by + 72, LANGUAGE_ICON);
        if (access != null) addIcon(access, cx + 104, by + 72, ACCESSIBILITY_ICON);

        // Atalho para o menu clássico (canto superior direito)
        Component classic = Component.translatable("title.isekai_mahou.classic");
        int cw = font.width(classic) + 20;
        RuneButton toClassic = new RuneButton(width - cw - 4, 4, cw, 14, classic, TitleHook::openClassic, false);
        toClassic.setTooltip(Tooltip.create(Component.translatable("title.isekai_mahou.classic.tooltip")));
        addRenderableWidget(toClassic);
    }

    /**
     * Botão estilizado no lugar de {@code vanilla} (clicar = clicar no original; ativo/dica copiados). Sem o original
     * (outro mod tirou), usa {@code fallback}; sem os dois, não cria nada.
     */
    private void add(@Nullable Button vanilla, int x, int y, int w, Component label, boolean primary, @Nullable Runnable fallback) {
        Runnable action = vanilla != null ? vanilla::onPress : fallback;
        if (action == null) return;
        RuneButton b = new RuneButton(x, y, w, 20, label, action, primary);
        if (vanilla != null) {
            b.active = vanilla.active;
            b.setTooltip(vanilla.getTooltip());
        }
        addRenderableWidget(b);
    }

    private void addIcon(Button vanilla, int x, int y, ResourceLocation icon) {
        RuneButton b = new RuneButton(x, y, 20, 20, vanilla.getMessage(), vanilla::onPress, false, icon);
        b.active = vanilla.active;
        b.setTooltip(Tooltip.create(vanilla.getMessage()));
        addRenderableWidget(b);
    }

    @Nullable
    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : null;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        if (openedAt == 0L) openedAt = now;
        float seconds = (now % 3_600_000L) / 1000F;
        float t = fadeIn ? Mth.clamp((now - openedAt) / 1800F, 0F, 1F) : 1F;
        float skyFade = Mth.clamp(t / 0.5F, 0F, 1F);
        float uiFade = Mth.clamp((t - 0.35F) / 0.65F, 0F, 1F);

        // Panorama do vanilla por baixo, depois o céu mágico
        PANORAMA.render(g, width, height, skyFade, partialTick);
        int circleY = logoTop + Math.round(8 * logoScale / 2F) + 4;
        // Milhares de retângulos pequenos (estrelas, anéis, aurora): em lote, um envio só no fim (sem isso cada fill
        // vira uma chamada de desenho e o menu pesa em PCs fracos)
        g.drawManaged(() -> sky.render(g, font, width, height, mouseX, mouseY, circleY, skyFade, seconds));

        TitleLogo.render(g, font, width, logoTop, logoScale, uiFade, seconds);
        renderSplash(g, seconds, uiFade);
        renderFooter(g, uiFade);
        // Aviso de versão de testes do NeoForge / atualização (o vanilla chama aqui também)
        net.neoforged.neoforge.client.ClientHooks.renderMainMenu(this, g, font, width, height, Mth.ceil(uiFade * 255F) << 24);

        for (Renderable r : renderables) {
            if (fadeIn && r instanceof AbstractWidget w) w.setAlpha(uiFade); // termina em 1
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // o fundo é desenhado no render()
    }

    /** Frase amarela inclinada ao lado do logo, pulsando (como a do vanilla). */
    private void renderSplash(GuiGraphics g, float seconds, float fade) {
        if (splash == null || minecraft.options.hideSplashTexts().get() || fade < 0.05F) return;
        float pulse = 1.8F - Mth.abs(Mth.sin(seconds * Mth.TWO_PI / 1.2F) * 0.1F);
        float s = pulse * 100F / (font.width(splash) + 32);
        int tw = Math.round(font.width(TitleLogo.TITLE) * logoScale);
        g.pose().pushPose();
        g.pose().translate(width / 2F + tw / 2F - 6, logoTop + 8 * logoScale + 2, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-16F));
        g.pose().scale(s, s, 1F);
        g.drawCenteredString(font, splash, 0, -8, MahouUi.alpha(0xFFFFE066, Math.max(5, Math.round(fade * 255))));
        g.pose().popPose();
    }

    /** Embaixo à esquerda: versão do mod, do Minecraft, do NeoForge e quantos mods. Embaixo à direita: atualização do NeoForge. */
    private void renderFooter(GuiGraphics g, float fade) {
        int al = Math.max(5, Math.round(fade * 255));
        ModList mods = ModList.get();
        String modVersion = mods.getModContainerById(IsekaiMahou.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        String neoVersion = mods.getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        List<Component> lines = List.of(
                Component.translatable("title.isekai_mahou.footer.mods", mods.size()),
                Component.translatable("title.isekai_mahou.footer.neoforge", neoVersion),
                Component.translatable("title.isekai_mahou.footer.minecraft", SharedConstants.getCurrentVersion().getName()),
                Component.translatable("title.isekai_mahou.footer.version", modVersion));
        int y = height - 10;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == lines.size() - 1 ? MahouUi.GOLD : MahouUi.TEXT_DIM;
            g.drawString(font, lines.get(i), 3, y, MahouUi.alpha(color, al), true);
            y -= 10;
        }
        String status = net.neoforged.neoforge.client.ClientHooks.forgeStatusLine;
        if (status != null && !status.isEmpty()) {
            g.drawString(font, status, width - font.width(status) - 3, height - 22, MahouUi.alpha(0xFFFFE066, al), true);
        }
    }
}
