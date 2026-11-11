package com.renanpontes.isekaimahou.feature.class_gambler.client;

import com.renanpontes.isekaimahou.feature.class_gambler.GbNet;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckBand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Estado do HUD do Apostador no cliente (última rolagem, Jackpot, Rodada da Casa). Tudo some sozinho. */
public final class GbClientState {
    /** Quanto tempo a placa da rolagem fica na tela (ticks). */
    public static final int ROLL_SHOW = 40;

    public static LuckBand band = LuckBand.EVEN;
    public static int percent;
    public static int kind;
    public static int fever;
    public static boolean gambler;
    /** Ticks desde a última rolagem (≥ ROLL_SHOW = escondida). */
    public static int rollAge = ROLL_SHOW;
    /** Jackpot: ticks restantes e total (barra do banner). */
    public static int jackpotTicks;
    public static int jackpotTotal;
    public static int challengeKills, challengeTarget, challengeTicks, challengeRank;
    public static int challengeResult;
    /** Ticks mostrando o resultado da Rodada depois que ela acaba. */
    public static int challengeAfter;

    static void onRoll(GbNet.RollShown msg) {
        band = LuckBand.byOrdinal(msg.band());
        percent = msg.percent();
        kind = msg.kind();
        fever = msg.fever();
        gambler = msg.gambler();
        rollAge = 0;
    }

    static void onJackpot(GbNet.JackpotState msg) {
        jackpotTicks = msg.ticks();
        jackpotTotal = Math.max(1, msg.total());
        if (msg.ticks() > 0) {
            fever = 0;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.4F, 0.7F));
        }
    }

    static void onChallenge(GbNet.ChallengeState msg) {
        challengeKills = msg.kills();
        challengeTarget = msg.target();
        challengeTicks = msg.ticks();
        challengeRank = msg.rank();
        challengeResult = msg.result();
        challengeAfter = msg.result() != 0 ? 80 : 0;
    }

    static void tick(ClientTickEvent.Post event) {
        if (rollAge < ROLL_SHOW) rollAge++;
        if (jackpotTicks > 0) jackpotTicks--;
        if (challengeResult == 0 && challengeTicks > 0) challengeTicks--;
        if (challengeAfter > 0 && --challengeAfter == 0) {
            challengeTarget = 0;
            challengeResult = 0;
        }
    }

    /** A Rodada da Casa está na tela? */
    public static boolean challengeVisible() {
        return challengeTarget > 0 && (challengeResult == 0 ? challengeTicks > 0 : challengeAfter > 0);
    }

    /** Saiu do mundo: zera. */
    static void reset() {
        rollAge = ROLL_SHOW;
        jackpotTicks = 0;
        challengeTarget = 0;
        challengeResult = 0;
        challengeAfter = 0;
    }

    private GbClientState() {}
}
