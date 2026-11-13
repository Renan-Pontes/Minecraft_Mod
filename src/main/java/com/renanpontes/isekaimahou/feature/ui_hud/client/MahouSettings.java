package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.lang.ref.WeakReference;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.perf.PerfScreen;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.ui_hud.HintPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Opções do mod no MENU DO MINECRAFT (v1.0 RELEASE — "Deixe isso no menu do Minecraft, não do mod"):
 * <ul>
 *   <li><b>Mods → Isekai Mahou → Config</b> abre a tela de configuração padrão do NeoForge (todas as opções, com nome
 *       e descrição traduzidos: {@code isekai_mahou.configuration.*});</li>
 *   <li><b>Opções</b> (menu principal ou Esc no jogo) ganha o botão "Isekai Mahou" que abre a mesma tela;</li>
 *   <li>nessa tela, um botão leva ao "Modo do PC" (os presets de desempenho que já existiam).</li>
 * </ul>
 * Também manda ao servidor as preferências que só ele consegue aplicar (Modo Paz automático, tutorial): ao entrar no
 * mundo e sempre que mudarem.
 */
public final class MahouSettings {
    /** A tela de config criada por nós (para pôr o botão do Modo do PC só nela, não nas de outros mods). */
    private static WeakReference<Screen> ours = new WeakReference<>(null);
    private static int lastPrefs = -1;
    private static int prefsTicks;

    private MahouSettings() {}

    static void init() {
        // Registrado DEPOIS do núcleo (que apontava o botão "Config" para o Modo do PC): este vale
        ModContainer own = ModLoadingContext.get().getActiveContainer();
        if (!IsekaiMahou.MODID.equals(own.getModId())) own = ModList.get().getModContainerById(IsekaiMahou.MODID).orElse(null);
        if (own != null) own.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) MahouSettings::create);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, MahouSettings::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> lastPrefs = -1);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> lastPrefs = -1);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> syncPrefs());
    }

    private static Screen create(ModContainer mod, @Nullable Screen parent) {
        Screen screen = new ConfigurationScreen(mod, parent);
        ours = new WeakReference<>(screen);
        return screen;
    }

    /** Abre as opções do mod (a tela de configuração do NeoForge) voltando para {@code parent} ao fechar. */
    public static void open(@Nullable Screen parent) {
        ModList.get().getModContainerById(IsekaiMahou.MODID)
                .ifPresent(c -> Minecraft.getInstance().setScreen(create(c, parent)));
    }

    private static void onScreenInit(ScreenEvent.Init.Post e) {
        Screen s = e.getScreen();
        Minecraft mc = Minecraft.getInstance();
        if (s instanceof OptionsScreen) {
            // Canto superior direito das Opções do Minecraft (o cabeçalho só tem o título no meio)
            Component label = Component.translatable("options.isekai_mahou.button");
            int w = Math.min(150, mc.font.width(label) + 20);
            e.addListener(Button.builder(label, b -> open(s))
                    .bounds(s.width - w - 6, 6, w, 20)
                    .tooltip(Tooltip.create(Component.translatable("options.isekai_mahou.button.tooltip")))
                    .build());
        } else if (s == ours.get()) {
            Component label = Component.translatable("options.isekai_mahou.performance");
            int w = Math.min(150, mc.font.width(label) + 20);
            e.addListener(Button.builder(label, b -> mc.setScreen(new PerfScreen(s)))
                    .bounds(6, 6, w, 20)
                    .tooltip(Tooltip.create(Component.translatable("options.isekai_mahou.performance.tooltip")))
                    .build());
        }
    }

    /** A cada 2 s confere se as preferências que valem no servidor mudaram (a tela de config salva na hora). */
    private static void syncPrefs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        // Ao entrar no mundo manda na hora; depois confere de vez em quando
        if (lastPrefs != -1 && ++prefsTicks % 40 != 0) return;
        int bits = MahouConfig.localPlayerPrefs();
        if (bits == lastPrefs) return;
        lastPrefs = bits;
        PacketDistributor.sendToServer(new HintPayloads.Prefs(bits));
    }
}
