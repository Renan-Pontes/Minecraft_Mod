package com.renanpontes.isekaimahou.feature.ambience.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ambience.AmbienceConfig;
import com.renanpontes.isekaimahou.feature.ambience.AmbienceNet;
import com.renanpontes.isekaimahou.feature.ambience.AmbienceSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * SÓ CLIENTE. O "DJ" da música ambiente — "sempre música baixa e de dark fantasy":
 * <ul>
 * <li>o servidor manda a zona ({@link AmbienceNet.Zone}); a faixa só entra depois de a zona ficar firme por 2 s
 * (andar na borda não liga/desliga música) e só sai 5 s depois de o jogador ir embora;</li>
 * <li>fade in de 4 s, troca cruzada de 4 s entre zonas, fade out de 5 s;</li>
 * <li>qualquer OUTRA música do mod (tema de chefe, vinheta de vitória, Las Noches) tem prioridade: a ambiente some
 * rápido e só volta 10 s depois de a outra acabar. Isso é visto pelo som que começa a tocar, sem depender das classes
 * do módulo das músicas de chefe;</li>
 * <li>enquanto toca (e ~15 s depois) a música do vanilla fica calada;</li>
 * <li>volume e liga/desliga em {@link AmbienceConfig} (valem na hora).</li>
 * </ul>
 */
public final class AmbientPlayer {
    private static final int START_DELAY = 40;
    private static final int SWITCH_DELAY = 60;
    private static final int LEAVE_GRACE = 100;
    private static final int FADE_IN = 80;
    private static final int CROSSFADE = 80;
    private static final int FADE_OUT = 100;
    private static final int DUCK = 30;
    private static final int AFTER_OTHER_MUSIC = 200;
    private static final int VANILLA_QUIET = 300;
    /** Volume da faixa com a opção em 100 (as faixas já são masterizadas baixas). */
    private static final float FULL = 0.7F;

    /** Zona dita pelo servidor ("" = nenhuma). */
    private static String serverZone = "";
    /** Teste por comando: null = automático; "" = silêncio forçado; outra = faixa forçada. */
    @Nullable private static String forced;
    @Nullable private static AmbientTrack current;
    private static final List<AmbientTrack> FADING = new ArrayList<>();
    /** Outras músicas do mod tocando agora (som → tick em que começou). */
    private static final Map<SoundInstance, Long> OTHER_MUSIC = new IdentityHashMap<>();
    private static String candidate = "";
    private static long candidateSince;
    private static long ticks;
    private static long blockedUntil;
    private static long quietUntil;
    private static long failedUntil;
    private static boolean inWorld;

    // ------------------------------------------------------------------ ganchos

    /** Pacote do servidor (zona detectada ou teste por comando). */
    public static void receive(AmbienceNet.Zone p) {
        switch (p.mode()) {
            case AmbienceNet.MODE_FORCED -> {
                forced = p.zone();
                blockedUntil = 0;
                failedUntil = 0;
            }
            case AmbienceNet.MODE_AUTO -> forced = null;
            default -> serverZone = p.zone();
        }
    }

    /** Todo som que começa: anota as músicas do mod que não são nossas (chefes têm prioridade). */
    static void onSound(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (s == null || s instanceof AmbientTrack || s.getSource() != SoundSource.MUSIC) return;
        if (!IsekaiMahou.MODID.equals(s.getLocation().getNamespace())) return;
        OTHER_MUSIC.put(s, ticks);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (inWorld) reset();
            return;
        }
        inWorld = true;
        ticks++;
        SoundManager sm = mc.getSoundManager();
        // músicas dos outros que já acabaram (os 10 ticks cobrem o intervalo entre "vai tocar" e "está tocando")
        OTHER_MUSIC.entrySet().removeIf(en -> ticks - en.getValue() > 10 && !sm.isActive(en.getKey()));
        boolean busy = !OTHER_MUSIC.isEmpty();
        if (busy) blockedUntil = ticks + AFTER_OTHER_MUSIC;

        float volume = AmbienceConfig.volume(AmbienceConfig.AMBIENT_VOLUME);
        boolean enabled = AmbienceConfig.on(AmbienceConfig.AMBIENT_MUSIC) && volume > 0F;
        String want = !enabled ? "" : forced != null ? forced : serverZone;
        if (!AmbienceSounds.isZone(want)) want = "";
        if (!want.equals(candidate)) {
            candidate = want;
            candidateSince = ticks;
        }
        long held = ticks - candidateSince;
        boolean now = forced != null || !enabled;

        if (current != null) {
            current.setMax(FULL * volume);
            if (busy) {
                release(DUCK);
            } else if (!current.isStopped() && !sm.isActive(current) && ticks % 20 == 0) {
                // o motor de som perdeu a faixa (F3+T, troca de dispositivo...): toca de novo
                sm.stop(current);
                current = null;
            }
        }
        if (current != null && !candidate.equals(current.zone)) {
            if (candidate.isEmpty()) {
                if (now || held >= LEAVE_GRACE) release(now ? DUCK : FADE_OUT);
            } else if (now || held >= SWITCH_DELAY) {
                start(mc, sm, candidate, FULL * volume, CROSSFADE);
            }
        } else if (current == null && !candidate.isEmpty() && !busy && ticks >= blockedUntil && ticks >= failedUntil
                && (now || held >= START_DELAY)) {
            start(mc, sm, candidate, FULL * volume, FADE_IN);
        }
        if (current != null) quietUntil = ticks + VANILLA_QUIET;

        Iterator<AmbientTrack> it = FADING.iterator();
        while (it.hasNext()) {
            AmbientTrack t = it.next();
            if (t.isStopped() || !sm.isActive(t)) it.remove();
        }
    }

    /** O vanilla deve ficar calado agora? (lido pelo {@code SelectMusicEvent}). */
    static boolean suppressVanilla() {
        return current != null || !FADING.isEmpty() || ticks < quietUntil;
    }

    /** Sai do mundo/desconecta: para tudo e esquece o estado. */
    static void reset() {
        SoundManager sm = Minecraft.getInstance().getSoundManager();
        if (current != null) sm.stop(current);
        for (AmbientTrack t : FADING) sm.stop(t);
        current = null;
        FADING.clear();
        OTHER_MUSIC.clear();
        serverZone = "";
        forced = null;
        candidate = "";
        candidateSince = 0;
        ticks = 0;
        blockedUntil = 0;
        quietUntil = 0;
        failedUntil = 0;
        inWorld = false;
    }

    // ------------------------------------------------------------------ tocagem

    private static void release(int fadeTicks) {
        if (current == null) return;
        current.fadeOut(fadeTicks);
        FADING.add(current);
        current = null;
        quietUntil = ticks + fadeTicks + VANILLA_QUIET;
    }

    private static void start(Minecraft mc, SoundManager sm, String zone, float max, int fadeTicks) {
        SoundEvent ev = AmbienceSounds.music(zone);
        if (ev == null) return;
        if (current != null) {
            current.fadeOut(fadeTicks);
            FADING.add(current);
            current = null;
        }
        // voltou para uma zona cuja faixa ainda está sumindo: reaproveita (a música continua de onde estava)
        AmbientTrack reuse = null;
        for (AmbientTrack t : FADING) {
            if (t.zone.equals(zone) && !t.isStopped() && sm.isActive(t)) {
                reuse = t;
                break;
            }
        }
        if (reuse != null) {
            FADING.remove(reuse);
            reuse.setMax(max);
            reuse.fadeIn(fadeTicks);
            current = reuse;
        } else {
            AmbientTrack t = new AmbientTrack(zone, ev, max);
            t.fadeIn(fadeTicks);
            sm.play(t);
            if (!sm.isActive(t)) {
                // arquivo faltando? não tenta de novo por 30 s, para não inundar o log
                failedUntil = ticks + 600;
                return;
            }
            current = t;
        }
        mc.getMusicManager().stopPlaying();
        quietUntil = ticks + VANILLA_QUIET;
    }

    private AmbientPlayer() {}
}
