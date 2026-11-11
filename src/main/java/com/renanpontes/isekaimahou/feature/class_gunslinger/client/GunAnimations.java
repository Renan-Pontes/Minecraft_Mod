package com.renanpontes.isekaimahou.feature.class_gunslinger.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsNetwork;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IActualAnimation;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.kosmx.playerAnim.api.IPlayable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Animações das armas (cliente):
 * <ul>
 *   <li>Corpo (playerAnimator, camada própria acima da de conjuração): tiro com coice, tiro da mão esquerda (pistolas
 *       duplas), recarga girando o tambor, leque do cão (a mão de apoio bate no cão), tiro carregado e a pose do
 *       saque das Doze Badaladas. Os braços seguem a mira (cima/baixo).</li>
 *   <li>1ª pessoa (próprio jogador): coice e mergulho/giro da recarga, aplicados no {@code GunClientExtensions}.</li>
 *   <li>"Mirando": quem atirou nos últimos 2 s segura a arma erguida (pose de braço do vanilla).</li>
 * </ul>
 */
public final class GunAnimations {
    public static final ResourceLocation LAYER = IsekaiMahou.id("gunslinger_gun");
    private static final int PRIORITY = 1300;
    static final ResourceLocation SHOOT = IsekaiMahou.id("gunslinger_shoot");
    static final ResourceLocation SHOOT_LEFT = IsekaiMahou.id("gunslinger_shoot_left");
    static final ResourceLocation RELOAD = IsekaiMahou.id("gunslinger_reload");
    static final ResourceLocation FAN = IsekaiMahou.id("gunslinger_fan");
    static final ResourceLocation CHARGE = IsekaiMahou.id("gunslinger_charge");
    static final ResourceLocation NOON = IsekaiMahou.id("gunslinger_noon");

    /** Até quando (tempo do mundo no cliente) cada entidade segura a arma erguida. */
    private static final Map<Integer, Long> AIM = new HashMap<>();
    private static ClientLevel lastLevel;

    // ---- estado do próprio jogador (1ª pessoa e HUD)
    private static long recoilStart = Long.MIN_VALUE / 2;
    private static float recoilStrength = 1F;
    private static long reloadStart = -1;
    private static int reloadTicks = 1;

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, GunLayer::new);
    }

    // =====================================================================================
    //  Pacote
    // =====================================================================================
    public static void receive(GsNetwork.Anim a) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity e = level.getEntity(a.entityId());
        if (e == null) return;
        long now = level.getGameTime();
        if (!(e instanceof AbstractClientPlayer p)) {
            if (a.kind() == GsNetwork.DRAW) drawFlash(level, e, a.rgb());
            return;
        }
        boolean self = p == mc.player;
        switch (a.kind()) {
            case GsNetwork.SHOOT, GsNetwork.SHOOT_LEFT -> {
                play(p, a.kind() == GsNetwork.SHOOT_LEFT ? SHOOT_LEFT : SHOOT, 1);
                AIM.put(p.getId(), now + 40);
                if (self) kick(now, a.ticks());
            }
            case GsNetwork.FAN -> {
                play(p, FAN, 1);
                AIM.put(p.getId(), now + 40);
                if (self) kick(now, a.ticks());
            }
            case GsNetwork.CHARGE -> {
                play(p, CHARGE, 1);
                AIM.put(p.getId(), now + 40);
                if (self) kick(now, GunKind.RIFLE.ordinal());
            }
            case GsNetwork.NOON -> play(p, NOON, 3);
            case GsNetwork.RELOAD -> {
                play(p, RELOAD, 2);
                AIM.remove(p.getId());
                if (self) {
                    reloadStart = now;
                    reloadTicks = Math.max(1, a.ticks());
                }
            }
            case GsNetwork.RELOAD_CANCEL -> {
                stop(p);
                if (self) reloadStart = -1;
            }
            default -> { }
        }
    }

    private static void kick(long now, int gunOrdinal) {
        recoilStart = now;
        GunKind k = GunKind.byOrdinal(gunOrdinal);
        recoilStrength = k == null ? 1F : switch (k) {
            case SHOTGUN -> 1.8F;
            case RIFLE -> 1.5F;
            case PISTOL, DUAL_PISTOLS -> 0.6F;
            default -> 1.0F;
        };
    }

    // =====================================================================================
    //  Consultas
    // =====================================================================================

    /** Coice da arma do próprio jogador (0..~1.8), decaindo em 6 ticks. */
    public static float recoil(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0F;
        float t = mc.level.getGameTime() + partial - recoilStart;
        if (t < 0 || t > 6) return 0F;
        float k = 1F - t / 6F;
        return k * k * recoilStrength;
    }

    /** Progresso da recarga do próprio jogador (0..1), ou -1 sem recarga. */
    public static float reloadProgress(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (reloadStart < 0 || mc.level == null) return -1F;
        float p = (mc.level.getGameTime() + partial - reloadStart) / reloadTicks;
        if (p >= 1F || p < 0F) {
            if (p >= 1F) reloadStart = -1;
            return -1F;
        }
        return p;
    }

    /** A entidade atirou há pouco (segura a arma erguida). */
    public static boolean aiming(Entity e) {
        Long until = AIM.get(e.getId());
        return until != null && e.level().getGameTime() <= until;
    }

    // =====================================================================================
    //  Camada
    // =====================================================================================
    private static GunLayer layer(AbstractClientPlayer player) {
        IAnimation a = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        return a instanceof GunLayer g ? g : null;
    }

    private static void play(AbstractClientPlayer player, ResourceLocation id, int fade) {
        GunLayer layer = layer(player);
        if (layer == null) return;
        IPlayable playable = PlayerAnimationRegistry.getAnimation(id);
        if (playable == null) return;
        IActualAnimation<?> anim = playable.playAnimation();
        // 1ª pessoa: a arma do vanilla (com o coice do GunClientExtensions); o corpo animado é para os outros
        anim.setFirstPersonMode(FirstPersonMode.VANILLA);
        layer.current = id;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, Ease.INOUTSINE), anim, true);
    }

    private static void stop(AbstractClientPlayer player) {
        GunLayer layer = layer(player);
        if (layer == null) return;
        layer.current = null;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE), null);
    }

    /** Clarão dourado na mão do Forasteiro no "SAQUE!". */
    private static void drawFlash(ClientLevel level, Entity e, int rgb) {
        float yaw = e.getYRot() * Mth.DEG_TO_RAD;
        double x = e.getX() - Mth.sin(yaw) * 0.6 - Mth.cos(yaw) * 0.35;
        double z = e.getZ() + Mth.cos(yaw) * 0.6 - Mth.sin(yaw) * 0.35;
        double y = e.getY() + 1.35;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F,
                (rgb & 255) / 255F), 1.2F);
        int n = MahouConfig.REDUCED_PARTICLES.get() ? 4 : 12;
        for (int i = 0; i < n; i++) {
            level.addParticle(dust, x + (level.random.nextDouble() - 0.5) * 0.3, y + (level.random.nextDouble() - 0.5) * 0.3,
                    z + (level.random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
        }
        level.addParticle(ParticleTypes.FLASH, x, y, z, 0, 0, 0);
    }

    /** Limpa o mapa de mira ao trocar de mundo e de tempos em tempos (entidades que sumiram). */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            AIM.clear();
            lastLevel = mc.level;
            reloadStart = -1;
        }
        if (mc.level == null || mc.level.getGameTime() % 100 != 0) return;
        long now = mc.level.getGameTime();
        for (Iterator<Long> it = AIM.values().iterator(); it.hasNext(); ) {
            if (it.next() < now) it.remove();
        }
    }

    /** Camada de um jogador: guarda a animação atual e faz os braços seguirem a mira. */
    static final class GunLayer extends ModifierLayer<IAnimation> {
        final AbstractClientPlayer player;
        ResourceLocation current;

        GunLayer(AbstractClientPlayer player) {
            super();
            this.player = player;
            addModifierBefore(new AimModifier());
        }

        private boolean aims() {
            return SHOOT.equals(current) || SHOOT_LEFT.equals(current) || FAN.equals(current) || CHARGE.equals(current);
        }

        /** Braço estendido acompanha a arfagem da câmera (o tiro sai para onde se olha). */
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

    private GunAnimations() {}
}
