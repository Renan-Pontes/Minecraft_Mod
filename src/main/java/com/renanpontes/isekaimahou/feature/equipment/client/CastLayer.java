package com.renanpontes.isekaimahou.feature.equipment.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Camada de animação de conjuração de UM jogador (criada pelo Player Animator para cada jogador do mundo).
 * Guarda qual animação está tocando (para "parar a canalização" não cortar a mão estendida que veio logo depois)
 * e a cor da magia (partículas nas mãos).
 */
final class CastLayer extends ModifierLayer<IAnimation> {
    final AbstractClientPlayer player;
    /** Animação tocando agora (id do arquivo em player_animations), ou null. */
    ResourceLocation current;
    /** Cor RGB da magia atual. */
    int rgb = 0xB98CFF;

    CastLayer(AbstractClientPlayer player) {
        super();
        this.player = player;
        addModifierBefore(new AimModifier());
    }

    boolean isPlaying(ResourceLocation id) {
        return id.equals(current) && isActive();
    }

    private boolean aims() {
        return CastAnimations.QUICK.equals(current) || CastAnimations.ULTIMATE.equals(current);
    }

    /**
     * Faz o braço estendido seguir para onde o jogador olha (cima/baixo): a mão aponta para o alvo da magia.
     * O peso é máximo com o braço na horizontal e some com ele abaixado ou erguido, então o começo e o fim
     * da animação continuam suaves.
     */
    private final class AimModifier extends AbstractModifier {
        @Override
        public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
            Vec3f v = super.get3DTransform(modelName, type, tickDelta, value0);
            if (type != TransformType.ROTATION || !aims()) return v;
            if (!"rightArm".equals(modelName) && !"leftArm".equals(modelName)) return v;
            float weight = 1F - Math.abs(v.getX() + Mth.HALF_PI) / Mth.HALF_PI;
            if (weight <= 0F) return v;
            float pitch = player.getViewXRot(tickDelta) * Mth.DEG_TO_RAD;
            return new Vec3f(v.getX() + pitch * weight, v.getY(), v.getZ());
        }
    }
}
