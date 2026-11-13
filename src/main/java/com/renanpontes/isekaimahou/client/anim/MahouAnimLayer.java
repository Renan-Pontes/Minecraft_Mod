package com.renanpontes.isekaimahou.client.anim;

import com.renanpontes.isekaimahou.client.ux.MeditationPose;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * Camada de animações de combate de UM jogador (o Player Animator cria uma por jogador do mundo).
 * <ul>
 *   <li>Guarda a animação atual (para prioridades e para "parar a pose contínua" não cortar um golpe).</li>
 *   <li>Fica inativa enquanto o jogador medita (a pose sentada do {@link MeditationPose} manda).</li>
 *   <li>Espelha para canhotos e faz os braços esticados seguirem a mira (cima/baixo).</li>
 * </ul>
 */
final class MahouAnimLayer extends ModifierLayer<IAnimation> {
    final AbstractClientPlayer player;
    final MirrorModifier mirror = new MirrorModifier(false);
    /** Animação tocando agora (ou a última; ver {@link #playing()}). */
    AnimLibrary.Def current;
    /** Tick do mundo (cliente) em que {@link #current} começou — para o efeito de impacto e o limite das poses. */
    long startTick;
    /** O efeito de impacto desta animação já saiu. */
    boolean cueDone;

    MahouAnimLayer(AbstractClientPlayer player) {
        super();
        this.player = player;
        addModifierBefore(new AimModifier());
        addModifierLast(mirror);
    }

    /** Há uma animação nossa tocando de verdade. */
    boolean playing() {
        IAnimation a = getAnimation();
        return current != null && a != null && a.isActive();
    }

    @Override
    public boolean isActive() {
        return super.isActive() && !MeditationPose.isMeditating(player);
    }

    /**
     * O braço esticado acompanha a arfagem da câmera. Peso máximo com o braço na horizontal e zero com ele
     * abaixado/erguido, então o começo e o fim da animação continuam suaves.
     */
    private final class AimModifier extends AbstractModifier {
        @Override
        public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
            Vec3f v = super.get3DTransform(modelName, type, tickDelta, value0);
            AnimLibrary.Def d = current;
            if (d == null || !d.aim() || type != TransformType.ROTATION) return v;
            if (!"rightArm".equals(modelName) && !"leftArm".equals(modelName)) return v;
            float weight = 1F - Math.abs(v.getX() + Mth.HALF_PI) / Mth.HALF_PI;
            if (weight <= 0F) return v;
            float pitch = player.getViewXRot(tickDelta) * Mth.DEG_TO_RAD;
            return new Vec3f(v.getX() + pitch * weight, v.getY(), v.getZ());
        }
    }
}
