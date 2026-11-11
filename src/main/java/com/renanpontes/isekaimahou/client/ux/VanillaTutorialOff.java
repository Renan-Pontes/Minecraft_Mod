package com.renanpontes.isekaimahou.client.ux;

import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * O tutorial do próprio Minecraft ("Ande com W, A, S e D", "Olhe ao redor"...) aparece num toast no canto superior direito,
 * por cima do minimapa e do rastreador. O Espírito Guia já faz esse papel, então ele é desligado ao entrar num mundo.
 */
public final class VanillaTutorialOff {
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.tutorialStep != TutorialSteps.NONE) {
            mc.getTutorial().setStep(TutorialSteps.NONE);
            mc.options.save();
        }
    }

    private VanillaTutorialOff() {}
}
