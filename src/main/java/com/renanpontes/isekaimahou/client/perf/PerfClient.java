package com.renanpontes.isekaimahou.client.perf;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.MahouConfig.PcMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cliente do "Modo do PC" (v1.3): a sugestão única do 1º início (no menu principal, depois de 1,5 s) e a reaplicação
 * do alcance do Distant Horizons (a API dele não salva no arquivo, então vale por sessão).
 */
public final class PerfClient {
    private static boolean suggestionDone;
    private static int titleTicks;
    private static boolean dhDone;
    private static int dhTries;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(PerfClient::tick);
    }

    /** Abre a tela de Desempenho por cima da tela atual (volta para ela ao fechar). */
    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new PerfScreen(parent));
    }

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!suggestionDone) suggestionTick(mc);
        if (!dhDone && mc.level != null && mc.player != null && mc.player.tickCount % 40 == 0) dhTick();
    }

    private static void suggestionTick(Minecraft mc) {
        if (!(mc.screen instanceof TitleScreen title)) {
            titleTicks = 0;
            return;
        }
        if (++titleTicks < 30) return;
        suggestionDone = true;
        if (MahouConfig.safe(MahouConfig.PERF_SUGGESTION_SHOWN) || MahouConfig.safe(MahouConfig.PC_MODE) != PcMode.NONE) return;
        try {
            MahouConfig.PERF_SUGGESTION_SHOWN.set(true);
            MahouConfig.PERF_SUGGESTION_SHOWN.save();
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.debug("Não foi possível salvar perfSuggestionShown", ex);
        }
        PcPreset s = HardwareProbe.info().suggestion();
        IsekaiMahou.LOGGER.info("Isekai Mahou: hardware {} MB / {} núcleos / '{}' -> modo sugerido {}",
                HardwareProbe.info().maxMemMb(), HardwareProbe.info().cores(), HardwareProbe.info().gpu(), s);
        mc.setScreen(new PerfSuggestScreen(title, s));
    }

    /** Distant Horizons: aplica o alcance do Modo do PC escolhido assim que a API dele estiver pronta. */
    private static void dhTick() {
        PcPreset p = PcPreset.of(MahouConfig.safe(MahouConfig.PC_MODE));
        if (p == null || !DhBridge.present()) {
            dhDone = true;
            return;
        }
        if (DhBridge.apply(p) || ++dhTries > 60) dhDone = true;
    }

    private PerfClient() {}
}
