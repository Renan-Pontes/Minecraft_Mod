package com.renanpontes.isekaimahou.client.onboard;

import com.renanpontes.isekaimahou.core.onboard.OnboardPayloads;

/** Handlers dos pacotes S→C do núcleo A2 (chamados na thread do cliente; só carregados no cliente). */
public final class OnboardClientHandlers {
    public static void dialogueShow(OnboardPayloads.DialogueShow p) {
        DialogueClient.show(p);
    }

    public static void dialogueClose(OnboardPayloads.DialogueClose p) {
        DialogueClient.close(p.id());
    }

    public static void dialogueSignal(OnboardPayloads.DialogueSignal p) {
        DialogueClient.signal(p.token());
    }

    public static void state(OnboardPayloads.OnboardState p) {
        ClientOnboarding.applyState(p);
    }

    public static void entryNudge() {
        ClientOnboarding.entryNudge();
    }

    public static void congrats(OnboardPayloads.Congrats p) {
        CongratsOverlay.show(p);
    }

    public static void travelMenu(OnboardPayloads.TravelMenu p) {
        ClientTravel.receive(p);
    }

    public static void travelOutcome(OnboardPayloads.TravelOutcome p) {
        ClientTravel.outcome(p);
    }

    public static void travelFx(OnboardPayloads.TravelFx p) {
        TravelFlash.show(p);
    }

    private OnboardClientHandlers() {}
}
