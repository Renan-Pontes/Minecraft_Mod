package com.renanpontes.isekaimahou.feature.space_magic;

import com.renanpontes.isekaimahou.feature.space_magic.client.GateChoiceScreen;
import com.renanpontes.isekaimahou.feature.space_magic.network.GateChoicePayload;
import com.renanpontes.isekaimahou.feature.space_magic.network.GateSelectPayload;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Módulo "space_magic" (v1.6): Magia Espacial — 5 glifos Arcanos de pergaminho: Inventário Dimensional (o "Item Box"
 * de isekai, que não cai na morte e cresce com o Círculo), Abrir Portal (portal de ida e volta para uma dimensão já
 * visitada), Passo Espacial (teleporte curto que atravessa paredes finas), Marca de Retorno e Gravidade.
 * <p>
 * v1.0: glifo Recuperar Pertences ({@link RecoverBelongings}, Círculo 7: traz os itens da última morte) e o loot de
 * chefe que não cabe no inventário indo para o Item Box ({@link BossLootBox}).
 */
public class SpaceMagicModule implements Module {
    @Override
    public String id() {
        return "space_magic";
    }

    @Override
    public void init(IEventBus modBus) {
        SpaceContent.register(modBus);
        SpaceGlyphs.register();
        SpaceEvents.register();
        RecoverBelongings.register();
        BossLootBox.register();
        SpaceCommands.register();
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("space_magic-1");
            r.playToClient(GateChoicePayload.TYPE, GateChoicePayload.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> GateChoiceScreen.open(p)));
            r.playToServer(GateSelectPayload.TYPE, GateSelectPayload.STREAM_CODEC,
                    (p, ctx) -> ctx.enqueueWork(() -> {
                        if (ctx.player() instanceof ServerPlayer sp) SpaceGates.choose(sp, p.dim());
                    }));
        });
    }
}
