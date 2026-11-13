package com.renanpontes.isekaimahou.feature.transform_laws.client;

import com.renanpontes.isekaimahou.feature.transform_laws.TransformForge;
import com.renanpontes.isekaimahou.feature.ui_grimoire.client.GrimoireTransform;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;

/**
 * Parte de cliente do módulo "transform_laws".
 * <p>
 * Onda 9: a antiga aba "Votos" (menu B → Personagem) saiu da barra. Moldar transformações e escolher os Votos agora é
 * no Grimório, pelo glifo Transformação ({@link TransformPanel}); os botões "Moldar liberação" das classes e o selo
 * "✦ Transformação" abrem o Grimório já nesse editor ({@link TransformForge#openShaper}).
 */
public class TransformLawsClient implements ClientModule {
    /** Id da aba antiga — não é mais registrada (abrir este id cai no Status); mantido para quem ainda o cita. */
    public static final String TAB = "transform_laws";

    @Override
    public void initClient(IEventBus modBus) {
        GrimoireTransform.install(TransformPanel::new, TransformPanel::shown, ShaperView::select);
        TransformForge.installOpener(GrimoireTransform::openAt);
    }
}
