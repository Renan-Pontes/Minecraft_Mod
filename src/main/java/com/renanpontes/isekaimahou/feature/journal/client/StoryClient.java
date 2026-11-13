package com.renanpontes.isekaimahou.feature.journal.client;

import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.journal.JournalNetwork;
import com.renanpontes.isekaimahou.feature.journal.story.StoryConfig;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Campanha no cliente: avisa o servidor das opções do jogador ({@link StoryConfig}) ao entrar no mundo e sempre que
 * mudarem (pelo menu do Minecraft ou pelo botão do Diário). É o servidor que decide mandar ou não as falas.
 */
final class StoryClient {
    /** O que o servidor já sabe: -1 = nada enviado nesta sessão; senão bit 0 campanha, bit 1 cutscenes. */
    private static int sent = -1;
    private static int cooldown;

    static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            sent = -1;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = 20;
        int now = bits();
        if (now == sent) return;
        sent = now;
        PacketDistributor.sendToServer(new JournalNetwork.StoryPrefs((now & 1) != 0, (now & 2) != 0));
    }

    private static int bits() {
        // Cutscenes de marco: a opção da campanha E a opção geral "Cutscenes" do mod (sem ela, a narração vira fala).
        boolean scenes = StoryConfig.on(StoryConfig.SCENES) && generalCutscenes();
        return (StoryConfig.on(StoryConfig.CAMPAIGN) ? 1 : 0) | (scenes ? 2 : 0);
    }

    private static boolean generalCutscenes() {
        try {
            return MahouConfig.CUTSCENES.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    static boolean campaign() {
        return StoryConfig.on(StoryConfig.CAMPAIGN);
    }

    /** Botão do Diário: liga/desliga a campanha (grava no arquivo de opções e avisa o servidor no próximo tick). */
    static void toggleCampaign() {
        boolean on = !campaign();
        try {
            StoryConfig.CAMPAIGN.set(on);
            StoryConfig.CAMPAIGN.save();
        } catch (RuntimeException ex) {
            // Arquivo de opções ainda não carregado: nada a gravar (o padrão continua valendo).
            return;
        }
        cooldown = 0;
    }

    static void reset() {
        sent = -1;
        cooldown = 0;
    }

    private StoryClient() {}
}
