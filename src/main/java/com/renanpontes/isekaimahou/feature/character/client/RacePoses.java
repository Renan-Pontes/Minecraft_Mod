package com.renanpontes.isekaimahou.feature.character.client;

import java.util.EnumMap;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.anim.AnimConfig;
import com.renanpontes.isekaimahou.feature.character.Race;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.AnimationFormat;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * SÓ CLIENTE (release 1.0). A <b>pose de revelação</b> de cada raça: no instante da explosão da Transformação o corpo
 * estala numa pose própria (o herói com o punho no céu, o anjo de braços abertos, a fera de quatro, o demônio com as
 * garras para baixo...), segura respirando ~1,5 s e volta. Feitas em código com o construtor de keyframes do Player
 * Animator (mesmas convenções do {@code client/anim/AnimLibrary}: braço pitch −90 = para a frente, −180 = para cima;
 * roll + no braço direito = abre; {@code body} pitch − = inclina para a frente, y = sobe em blocos).
 * <p>
 * Camada própria por jogador, ACIMA da camada de combate do núcleo (a pose substitui o "estouro" genérico). Em 1ª pessoa
 * não mexe em nada. Respeita a opção "gestos de magia".
 */
final class RacePoses {
    private static final ResourceLocation LAYER = IsekaiMahou.id("race_pose");
    /** Acima da camada de combate do núcleo (1250). */
    private static final int PRIORITY = 1300;
    private static final String RA = "rightArm", LA = "leftArm", RL = "rightLeg", LL = "leftLeg", T = "torso", B = "body";
    private static final int SNAP = 5, HOLD = 34, END = 46;

    private static final Map<Race, KeyframeAnimation> POSES = new EnumMap<>(Race.class);

    static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, p -> new ModifierLayer<IAnimation>());
        try {
            build();
        } catch (RuntimeException e) {
            // Uma pose malformada não pode derrubar o jogo: as que entraram continuam valendo
            IsekaiMahou.LOGGER.error("[character] falha ao construir as poses de Transformação", e);
        }
    }

    /** Toca a pose de revelação da raça no jogador (chamado na explosão da Transformação). */
    static void play(AbstractClientPlayer player, Race race) {
        KeyframeAnimation anim = POSES.get(race);
        if (anim == null || !AnimConfig.SPELL_GESTURES.get()) return;
        if (player.isSleeping() || player.isFallFlying() || player.isSpectator()) return;
        if (!(PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER) instanceof ModifierLayer<?> raw)) return;
        @SuppressWarnings("unchecked")
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) raw;
        KeyframeAnimationPlayer p = new KeyframeAnimationPlayer(anim);
        // Corpo inteiro se mexe: em 1ª pessoa fica a mão normal do Minecraft
        p.setFirstPersonMode(FirstPersonMode.VANILLA);
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.OUTCUBIC), p, true);
    }

    // =====================================================================================
    //  As 11 poses
    // =====================================================================================
    private static void build() {
        Ease in = Ease.OUTBACK, hold = Ease.INOUTSINE;

        // Humano — Despertar Heroico: punho direito no céu, esquerdo fechado na cintura, peito aberto
        put(Race.HUMAN, new P()
                .arm(RA, -172, 0, 12, 10, in).arm(LA, 18, 0, -22, 95, in)
                .body(8, 0, 0.06F, in).leg(RL, 6, 0, 10, in).leg(LL, -8, 0, -10, in)
                .breathe(RA, -168, 0, 14, 10).breatheBody(10, 0, 0.09F));

        // Elfo — Graça da Lua: braços abertos e baixos, palmas para cima, o corpo flutua de leve
        put(Race.ELF, new P()
                .arm(RA, -35, 0, 62, 20, Ease.OUTSINE).arm(LA, -35, 0, -62, 20, Ease.OUTSINE)
                .body(6, 0, 0.12F, Ease.OUTSINE).leg(RL, 10, 0, 3, Ease.OUTSINE).leg(LL, 4, 0, -3, Ease.OUTSINE)
                .breathe(RA, -42, 0, 66, 16).breathe(LA, -42, 0, -66, 16).breatheBody(8, 0, 0.18F));

        // Anão — Colosso: punhos batendo na frente do peito, base larga e baixa
        put(Race.DWARF, new P()
                .arm(RA, -62, -42, 10, 105, in).arm(LA, -62, 42, -10, 105, in)
                .body(-6, 0, -0.14F, in).leg(RL, -4, 0, 20, in).leg(LL, -4, 0, -20, in)
                .breathe(RA, -66, -46, 8, 110).breathe(LA, -66, 46, -8, 110).breatheBody(-8, 0, -0.16F));

        // Meio-Orc — Fúria: braços jogados para trás e abertos, peito para a frente num rugido
        put(Race.HALF_ORC, new P()
                .arm(RA, 38, 0, 58, 35, in).arm(LA, 38, 0, -58, 35, in)
                .body(-16, 0, -0.05F, in).leg(RL, -14, 0, 14, in).leg(LL, 12, 0, -14, in)
                .breathe(RA, 42, 0, 62, 30).breathe(LA, 42, 0, -62, 30).breatheBody(-19, 2, -0.05F));

        // Demônio — Senhor do Inferno: braços abertos para baixo, garras viradas, corpo inclinado para trás
        put(Race.DEMON, new P()
                .arm(RA, -18, 0, 78, 30, in).arm(LA, -18, 0, -78, 30, in)
                .body(14, 0, 0.04F, in).leg(RL, 4, 0, 12, in).leg(LL, -6, 0, -12, in)
                .breathe(RA, -12, 0, 84, 26).breathe(LA, -12, 0, -84, 26).breatheBody(16, 0, 0.07F));

        // Dracônico — Sopro: braços para trás, corpo projetado para a frente, uma perna atrás
        put(Race.DRAGONBORN, new P()
                .arm(RA, 48, 0, 30, 20, in).arm(LA, 48, 0, -30, 20, in)
                .body(-24, 0, -0.04F, in).leg(RL, -22, 0, 8, in).leg(LL, 24, 0, -8, in)
                .breathe(RA, 52, 0, 34, 18).breathe(LA, 52, 0, -34, 18).breatheBody(-27, 0, -0.04F));

        // Bestial — Fera Total: de quatro, pronto para o bote (corpo bem baixo, mãos no chão à frente)
        put(Race.BEASTFOLK, new P()
                .arm(RA, -105, -12, 10, 25, in).arm(LA, -118, 12, -10, 25, in)
                .body(-52, 0, -0.28F, in).leg(RL, 30, 0, 12, in).leg(LL, 58, 0, -12, in)
                .breathe(RA, -108, -12, 10, 30).breathe(LA, -121, 12, -10, 30).breatheBody(-55, 0, -0.31F));

        // Aracnídeo — Rainha da Teia: braços erguidos e dobrados como patas, agachado
        put(Race.ARACHNID, new P()
                .arm(RA, -150, 0, 55, 110, in).arm(LA, -150, 0, -55, 110, in)
                .body(-12, 0, -0.18F, in).leg(RL, -18, 0, 24, in).leg(LL, -18, 0, -24, in)
                .breathe(RA, -156, 0, 60, 100).breathe(LA, -156, 0, -60, 100).breatheBody(-14, 0, -0.2F));

        // Fantasma — Espectro: braços moles pendendo à frente, corpo torto flutuando
        put(Race.PHANTOM, new P()
                .arm(RA, -72, -8, 6, 55, Ease.OUTSINE).arm(LA, -58, 8, -6, 70, Ease.OUTSINE)
                .body(-10, 7, 0.14F, Ease.OUTSINE).leg(RL, 14, 0, 2, Ease.OUTSINE).leg(LL, 22, 0, -2, Ease.OUTSINE)
                .breathe(RA, -66, -8, 6, 62).breathe(LA, -64, 8, -6, 62).breatheBody(-8, -6, 0.2F));

        // Anjo — Serafim: braços em cruz um pouco acima do ombro, corpo subindo
        put(Race.ANGEL, new P()
                .arm(RA, -8, 0, 102, 6, Ease.OUTSINE).arm(LA, -8, 0, -102, 6, Ease.OUTSINE)
                .body(10, 0, 0.16F, Ease.OUTSINE).leg(RL, 8, 0, 2, Ease.OUTSINE).leg(LL, 14, 0, -2, Ease.OUTSINE)
                .breathe(RA, -10, 0, 108, 4).breathe(LA, -10, 0, -108, 4).breatheBody(12, 0, 0.22F));

        // Slime — Rei Slime: achata e incha (agacha de braços abertos e balança)
        put(Race.SLIME, new P()
                .arm(RA, -10, 0, 70, 50, in).arm(LA, -10, 0, -70, 50, in)
                .body(0, 0, -0.22F, in).leg(RL, 0, 0, 22, in).leg(LL, 0, 0, -22, in)
                .breathe(RA, -14, 0, 58, 40).breathe(LA, -14, 0, -58, 40).breatheBody(0, 5, -0.12F));
    }

    private static void put(Race race, P pose) {
        POSES.put(race, pose.build("race_pose_" + race.id));
    }

    /**
     * Uma pose: neutro (tick 0) → pose (tick {@link #SNAP}) → variação "respirando" (meio do caminho) → pose
     * ({@link #HOLD}) → neutro ({@link #END}). Quem não chama {@code breathe} segura a pose parada.
     */
    private static final class P {
        private final KeyframeAnimation.AnimationBuilder b = new KeyframeAnimation.AnimationBuilder(AnimationFormat.JSON_EMOTECRAFT);
        private static final int MID = (SNAP + HOLD) / 2;

        P() {
            b.beginTick = 0;
            b.endTick = END;
            b.stopTick = END + 3;
            b.isEasingBefore = true;
            b.isLooped = false;
            b.returnTick = 0;
            // O tronco não é usado nas poses, mas precisa voltar do "estouro" genérico sem torção
            rot(T, 0, 0, 0, 0, Ease.LINEAR);
            rot(T, END, 0, 0, 0, Ease.INOUTSINE);
        }

        /** Um keyframe pendente (são gravados em ordem de tick no {@link #build}). */
        private record K(String part, char channel, int tick, float value, Ease ease) {}

        private final java.util.List<K> keys = new java.util.ArrayList<>();

        private void rot(String part, int t, float pitch, float yaw, float roll, Ease e) {
            keys.add(new K(part, 'p', t, pitch, e));
            keys.add(new K(part, 'y', t, yaw, e));
            keys.add(new K(part, 'r', t, roll, e));
        }

        private void bend(String part, int t, float deg, Ease e) {
            keys.add(new K(part, 'b', t, deg, e));
        }

        private void height(int t, float blocks, Ease e) {
            keys.add(new K(B, 'h', t, blocks, e));
        }

        /** Braço: pose no tick do estalo, segura até o fim e volta ao neutro. */
        P arm(String part, float pitch, float yaw, float roll, float bendDeg, Ease e) {
            float side = RA.equals(part) ? 8 : -8;
            rot(part, 0, -10, 0, side, Ease.LINEAR);
            bend(part, 0, 0, Ease.LINEAR);
            rot(part, SNAP, pitch, yaw, roll, e);
            bend(part, SNAP, bendDeg, Ease.OUTCUBIC);
            rot(part, HOLD, pitch, yaw, roll, Ease.INOUTSINE);
            bend(part, HOLD, bendDeg, Ease.INOUTSINE);
            rot(part, END, 0, 0, 0, Ease.INOUTSINE);
            bend(part, END, 0, Ease.INOUTSINE);
            return this;
        }

        P leg(String part, float pitch, float yaw, float roll, Ease e) {
            rot(part, 0, 0, 0, 0, Ease.LINEAR);
            rot(part, SNAP, pitch, yaw, roll, e);
            rot(part, HOLD, pitch, yaw, roll, Ease.INOUTSINE);
            rot(part, END, 0, 0, 0, Ease.INOUTSINE);
            return this;
        }

        /** Jogador inteiro: inclinação (pitch − = para a frente), rolagem e altura (blocos). */
        P body(float pitch, float roll, float y, Ease e) {
            rot(B, 0, 0, 0, 0, Ease.LINEAR);
            rot(B, SNAP, pitch, 0, roll, e);
            rot(B, HOLD, pitch, 0, roll, Ease.INOUTSINE);
            rot(B, END, 0, 0, 0, Ease.INOUTSINE);
            height(0, 0, Ease.LINEAR);
            height(SNAP, y, Ease.OUTCUBIC);
            height(HOLD, y, Ease.INOUTSINE);
            height(END, 0, Ease.INOUTSINE);
            return this;
        }

        /** Variação do braço no meio da pose (o corpo "respira" em vez de congelar). */
        P breathe(String part, float pitch, float yaw, float roll, float bendDeg) {
            rot(part, MID, pitch, yaw, roll, Ease.INOUTSINE);
            bend(part, MID, bendDeg, Ease.INOUTSINE);
            return this;
        }

        P breatheBody(float pitch, float roll, float y) {
            rot(B, MID, pitch, 0, roll, Ease.INOUTSINE);
            height(MID, y, Ease.INOUTSINE);
            return this;
        }

        KeyframeAnimation build(String name) {
            // Em ordem de tick (a variação do meio é declarada depois do fim)
            keys.sort(java.util.Comparator.comparingInt(K::tick));
            for (K k : keys) {
                KeyframeAnimation.StateCollection p = b.getPart(k.part());
                switch (k.channel()) {
                    case 'p' -> p.pitch.addKeyFrame(k.tick(), k.value(), k.ease(), 0, true);
                    case 'y' -> p.yaw.addKeyFrame(k.tick(), k.value(), k.ease(), 0, true);
                    case 'r' -> p.roll.addKeyFrame(k.tick(), k.value(), k.ease(), 0, true);
                    case 'b' -> {
                        if (p.isBendable() && p.bend != null) p.bend.addKeyFrame(k.tick(), k.value(), k.ease(), 0, true);
                    }
                    default -> p.y.addKeyFrame(k.tick(), k.value(), k.ease());
                }
            }
            b.name = name;
            return b.build();
        }
    }

    private RacePoses() {}
}
