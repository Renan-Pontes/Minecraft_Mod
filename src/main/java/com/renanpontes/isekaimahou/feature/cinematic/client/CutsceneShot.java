package com.renanpontes.isekaimahou.feature.cinematic.client;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * "Plano de câmera" de cada tipo de cutscene. Ângulos em graus; raio e altura são multiplicadores do tamanho do foco.
 *
 * @param startOffset ângulo inicial da órbita relativo ao ângulo-base (atrás do jogador / lado de quem assiste)
 * @param sweep       quanto a câmera gira durante a cena
 * @param radiusStart raio no início (× raio-base); radiusEnd no fim — permite "dolly in/out"
 * @param heightStart altura da câmera acima do ponto mirado (× altura do foco); negativo = câmera baixa olhando pra cima
 * @param lookHeight  ponto mirado no foco (fração da altura)
 * @param rollStart   inclinação lateral ("dutch angle") no início; rollEnd no fim
 * @param fovStart    multiplicador de FOV no início; fovEnd no fim (zoom)
 * @param flash       força do clarão inicial (0..1)
 * @param shake       intensidade do tremor inicial (ver {@link CameraShake}); shakeTicks duração
 * @param boss        cena de chefe (tem prioridade e aparece mesmo com "só as minhas cutscenes")
 */
record CutsceneShot(float startOffset, float sweep, float radiusStart, float radiusEnd, float heightStart, float heightEnd,
                    float lookHeight, float rollStart, float rollEnd, float fovStart, float fovEnd, float flash,
                    float shake, int shakeTicks, boolean boss) {

    static CutsceneShot of(String kind) {
        return switch (kind) {
            // Gira de trás do conjurador até a frente, mostrando o rosto enquanto ele grita o nome
            case "ultimate" -> new CutsceneShot(25, 160, 1.0F, 0.85F, 0.35F, 0.15F, 0.75F, 0, 0, 1.0F, 0.82F, 0.85F, 1.2F, 14, false);
            // Grua: sobe e se afasta para revelar o domo
            case "domain" -> new CutsceneShot(10, 220, 1.3F, 1.75F, 0.5F, 1.6F, 0.6F, 0, 0, 1.05F, 0.95F, 1.0F, 1.6F, 20, false);
            // Câmera baixa (o chefe parece enorme), aproximando devagar, com leve inclinação
            case "boss_intro" -> new CutsceneShot(-30, 110, 1.5F, 0.95F, -0.15F, 0.25F, 0.8F, 5, 0, 1.0F, 0.8F, 0.5F, 0.6F, 40, true);
            // Corte rápido e violento
            case "boss_phase" -> new CutsceneShot(-15, 70, 1.0F, 0.75F, 0.2F, 0.3F, 0.75F, -7, 0, 1.12F, 0.8F, 0.9F, 2.2F, 18, true);
            // Ângulo baixo e lento, ar de ameaça
            case "patron" -> new CutsceneShot(0, 90, 1.3F, 1.0F, -0.1F, 0.1F, 0.7F, 4, 2, 0.95F, 0.85F, 0.6F, 0.8F, 30, false);
            // Espiral subindo em volta de quem ascende
            case "ascension" -> new CutsceneShot(0, 300, 0.9F, 1.2F, 0.0F, 1.4F, 0.6F, 0, 0, 1.0F, 0.9F, 1.0F, 0.5F, 25, false);
            // Aproximação lenta do portal
            case "portal" -> new CutsceneShot(20, 50, 1.6F, 0.9F, 0.3F, 0.2F, 0.6F, 0, 0, 1.05F, 0.85F, 0.7F, 0.7F, 20, false);
            default -> new CutsceneShot(25, 120, 1.0F, 0.9F, 0.3F, 0.2F, 0.7F, 0, 0, 1.0F, 0.85F, 0.7F, 1.0F, 12, kind.startsWith("boss"));
        };
    }

    /** Sons do início da cena (todos do vanilla, tocados só para quem assiste). */
    static void playStartSounds(String kind, SoundManager sounds) {
        switch (kind) {
            case "ultimate" -> {
                ui(sounds, SoundEvents.BEACON_ACTIVATE, 1.3F, 0.7F);
                ui(sounds, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.7F, 0.6F);
            }
            case "domain" -> {
                ui(sounds, SoundEvents.END_PORTAL_SPAWN, 1.2F, 0.45F);
                ui(sounds, SoundEvents.BEACON_ACTIVATE, 0.7F, 0.7F);
            }
            case "boss_intro" -> {
                ui(sounds, SoundEvents.RAID_HORN.value(), 1.0F, 0.5F);
                ui(sounds, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, 0.8F, 0.6F);
            }
            case "boss_phase" -> {
                ui(sounds, SoundEvents.RAVAGER_ROAR, 0.7F, 0.6F);
                ui(sounds, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.3F);
            }
            case "patron" -> {
                ui(sounds, SoundEvents.ELDER_GUARDIAN_CURSE, 0.5F, 0.5F);
                ui(sounds, SoundEvents.SOUL_ESCAPE.value(), 0.6F, 0.8F);
            }
            case "ascension" -> {
                ui(sounds, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.6F);
                ui(sounds, SoundEvents.BEACON_POWER_SELECT, 1.5F, 0.6F);
            }
            case "portal" -> {
                ui(sounds, SoundEvents.PORTAL_TRIGGER, 1.4F, 0.3F);
                ui(sounds, SoundEvents.END_PORTAL_SPAWN, 1.5F, 0.3F);
            }
            default -> ui(sounds, SoundEvents.EVOKER_CAST_SPELL, 0.8F, 0.6F);
        }
    }

    private static void ui(SoundManager sounds, SoundEvent sound, float pitch, float volume) {
        sounds.play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
