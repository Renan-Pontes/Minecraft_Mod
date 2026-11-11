package com.renanpontes.isekaimahou.client.classes;

import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.core.classes.ClassesPayloads;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Inicialização do núcleo de classes no cliente: avisa o servidor quais teclas de slot estão seguradas (a Postura dura
 * enquanto a tecla dela estiver segurada) e registra o modelo da Lição ({@link LessonClient}). A tela de classe é
 * {@code client.PathSelectionScreen}.
 */
public final class ClassesCoreClient {
    /** Último estado enviado (bit i = slot i); -1 = ainda não enviou nesta conexão. */
    private static int sentMask = -1;

    public static void init(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, LessonClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(BoundTooltip::onTooltip);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> sentMask = -1);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> sentMask = -1);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        int mask = 0;
        if (mc.screen == null) {
            for (int s = 0; s < CoreKeys.SLOTS.length; s++) {
                KeyMapping k = CoreKeys.SLOTS[s];
                if (k.isDown()) mask |= 1 << s;
            }
        }
        // Primeiro envio só quando algo é segurado (não manda pacote à toa ao entrar)
        if (mask == sentMask || (sentMask == -1 && mask == 0)) return;
        sentMask = mask;
        PacketDistributor.sendToServer(new ClassesPayloads.SlotKeys(mask));
    }

    private ClassesCoreClient() {}
}
