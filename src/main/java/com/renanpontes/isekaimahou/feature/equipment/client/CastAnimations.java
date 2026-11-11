package com.renanpontes.isekaimahou.feature.equipment.client;

import java.util.function.Consumer;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.equipment.network.CastAnimPayload;
import com.renanpontes.isekaimahou.network.ClientHooks;
import com.renanpontes.isekaimahou.network.ClientMagic;
import com.renanpontes.isekaimahou.network.CorePayloads;

import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IActualAnimation;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Animações de conjuração (Player Animator) — cliente.
 * <ul>
 *   <li>Próprio jogador: o gancho {@link ClientHooks#castState} (encadeado com o anterior) liga/desliga {@code cast_channel}.</li>
 *   <li>Todos (inclusive o próprio): pacote {@link CastAnimPayload} do servidor (canalização, mão estendida, suprema,
 *       em si mesmo). Para o próprio jogador a canalização é idempotente com o gancho.</li>
 * </ul>
 * Os arquivos ficam em assets/isekai_mahou/player_animations/*.json (formato emotecraft, gerados por tools/gen/equipment.py).
 */
public final class CastAnimations {
    public static final ResourceLocation LAYER = IsekaiMahou.id("equipment_cast");
    public static final ResourceLocation QUICK = IsekaiMahou.id("cast_quick");
    public static final ResourceLocation CHANNEL = IsekaiMahou.id("cast_channel");
    public static final ResourceLocation ULTIMATE = IsekaiMahou.id("cast_ultimate");
    public static final ResourceLocation SELF = IsekaiMahou.id("cast_self");
    /** Acima das camadas comuns (emotes) para a conjuração não ser escondida. */
    private static final int PRIORITY = 1200;

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, CastLayer::new);
        // Encadeia: quem registrou antes (HUD, cinemática...) continua recebendo o estado
        Consumer<CorePayloads.CastState> previous = ClientHooks.castState;
        ClientHooks.castState = p -> {
            previous.accept(p);
            onOwnCastState(p);
        };
        NeoForge.EVENT_BUS.addListener(CastAnimations::onClientTick);
    }

    // =====================================================================================
    //  Entradas
    // =====================================================================================

    /** Estado da conjuração do PRÓPRIO jogador (0 começou, 1 terminou, 2 interrompida). */
    private static void onOwnCastState(CorePayloads.CastState p) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (p.state() == 0) {
            if (p.ticks() > 0) startChannel(player, ownColor());
        } else {
            stopChannel(player);
        }
    }

    /** Pacote do servidor: animação de outro jogador (ou a mão estendida/pose do próprio). */
    public static void receive(CastAnimPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (!(e instanceof AbstractClientPlayer player)) return;
        // Para o próprio jogador, início/fim da canalização já chegaram pelo gancho do núcleo; aqui é idempotente
        switch (p.kind()) {
            case CastAnimPayload.QUICK -> {
                play(player, QUICK, 2, p.rgb());
                handBurst(mc.level, player, p.rgb());
            }
            case CastAnimPayload.ULTIMATE -> {
                play(player, ULTIMATE, 3, p.rgb());
                ultimateBurst(mc.level, player, p.rgb());
            }
            case CastAnimPayload.SELF -> {
                play(player, SELF, 2, p.rgb());
                selfBurst(mc.level, player, p.rgb());
            }
            case CastAnimPayload.CHANNEL_START -> startChannel(player, p.rgb());
            case CastAnimPayload.CHANNEL_STOP -> stopChannel(player);
            default -> { }
        }
    }

    // =====================================================================================
    //  Camada
    // =====================================================================================
    private static CastLayer layer(AbstractClientPlayer player) {
        IAnimation a = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        return a instanceof CastLayer c ? c : null;
    }

    private static void play(AbstractClientPlayer player, ResourceLocation id, int fadeTicks, int rgb) {
        CastLayer layer = layer(player);
        if (layer == null) return;
        IPlayable playable = PlayerAnimationRegistry.getAnimation(id);
        if (playable == null) return; // pacote de recursos sem a animação: segue sem animar
        IActualAnimation<?> anim = playable.playAnimation();
        // Em primeira pessoa mostra os braços do modelo (a mão estendida aparece na tela)
        anim.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
        anim.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        layer.current = id;
        layer.rgb = rgb;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeTicks, Ease.INOUTSINE), anim, true);
    }

    private static void startChannel(AbstractClientPlayer player, int rgb) {
        CastLayer layer = layer(player);
        if (layer == null) return;
        if (layer.isPlaying(CHANNEL)) {
            layer.rgb = rgb; // já erguidos (pelo gancho): só passa a usar a cor real da magia
            return;
        }
        play(player, CHANNEL, 4, rgb);
    }

    /** Para só a canalização (se a mão estendida já começou, ela continua). */
    private static void stopChannel(AbstractClientPlayer player) {
        CastLayer layer = layer(player);
        if (layer == null || !CHANNEL.equals(layer.current)) return;
        layer.current = null;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(6, Ease.INOUTSINE), null);
    }

    // =====================================================================================
    //  Partículas nas mãos
    // =====================================================================================
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        int every = MahouConfig.REDUCED_PARTICLES.get() ? 8 : 3;
        if (mc.level.getGameTime() % every != 0) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.isInvisible()) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue; // colado na câmera
            CastLayer layer = layer(p);
            if (layer == null || !layer.isPlaying(CHANNEL)) continue;
            DustParticleOptions dust = dust(layer.rgb, 0.75F);
            for (int side = -1; side <= 1; side += 2) {
                Vec3 h = raisedHand(p, side);
                mc.level.addParticle(dust, h.x, h.y, h.z, 0, 0.015, 0);
            }
            if (mc.level.random.nextInt(4) == 0) {
                Vec3 h = raisedHand(p, mc.level.random.nextBoolean() ? 1 : -1);
                mc.level.addParticle(ParticleTypes.ENCHANT, h.x, h.y + 0.2, h.z, 0, -0.3, 0);
            }
        }
    }

    /** Mão estendida à frente (mira para onde o jogador olha). */
    private static void handBurst(ClientLevel level, AbstractClientPlayer p, int rgb) {
        Vec3 look = p.getLookAngle();
        Vec3 right = right(p.getYRot());
        Vec3 h = p.getEyePosition().add(look.scale(0.85)).add(right.scale(0.32)).add(0, -0.22, 0);
        int n = reduced() ? 3 : 8;
        DustParticleOptions dust = dust(rgb, 1.0F);
        for (int i = 0; i < n; i++) {
            level.addParticle(dust, h.x + jitter(level, 0.08), h.y + jitter(level, 0.08), h.z + jitter(level, 0.08),
                    look.x * 0.06, look.y * 0.06, look.z * 0.06);
        }
        level.addParticle(ParticleTypes.END_ROD, h.x, h.y, h.z, look.x * 0.04, look.y * 0.04, look.z * 0.04);
    }

    /** Suprema: espiral subindo em volta do conjurador. */
    private static void ultimateBurst(ClientLevel level, AbstractClientPlayer p, int rgb) {
        int n = reduced() ? 10 : 28;
        DustParticleOptions dust = dust(rgb, 1.3F);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n * 2;
            double y = 0.1 + 2.0 * i / n;
            level.addParticle(dust, p.getX() + Math.cos(a) * 0.9, p.getY() + y, p.getZ() + Math.sin(a) * 0.9,
                    0, 0.05, 0);
        }
        for (int side = -1; side <= 1; side += 2) {
            Vec3 h = raisedHand(p, side).add(0, 0.35, 0);
            level.addParticle(ParticleTypes.END_ROD, h.x, h.y, h.z, 0, 0.08, 0);
        }
    }

    /** Em si mesmo: anel que se abre na altura do peito. */
    private static void selfBurst(ClientLevel level, AbstractClientPlayer p, int rgb) {
        int n = reduced() ? 6 : 14;
        DustParticleOptions dust = dust(rgb, 0.9F);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double cx = Math.cos(a), cz = Math.sin(a);
            level.addParticle(dust, p.getX() + cx * 0.4, p.getY() + 1.2, p.getZ() + cz * 0.4, cx * 0.12, 0.01, cz * 0.12);
        }
    }

    /** Posição aproximada de uma mão erguida (pose de canalização). side = 1 direita, -1 esquerda. */
    private static Vec3 raisedHand(AbstractClientPlayer p, int side) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        Vec3 r = right(p.yBodyRot);
        double s = p.getScale();
        double sideOff = 0.42 * side;
        return new Vec3(p.getX() + (fx * 0.45 + r.x * sideOff) * s, p.getY() + 1.72 * s, p.getZ() + (fz * 0.45 + r.z * sideOff) * s);
    }

    private static Vec3 right(float yawDeg) {
        float yaw = yawDeg * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    private static double jitter(ClientLevel level, double r) {
        return (level.random.nextDouble() - 0.5) * 2 * r;
    }

    private static boolean reduced() {
        return MahouConfig.REDUCED_PARTICLES.get();
    }

    /** Cor do elemento ativo do próprio jogador (o CastState do núcleo não traz a cor). */
    private static int ownColor() {
        MagicData d = ClientMagic.data();
        if (d == null) return Element.ARCANE.color;
        Element e = d.activeElement();
        return e != null ? e.color : Element.ARCANE.color;
    }

    private CastAnimations() {}
}
