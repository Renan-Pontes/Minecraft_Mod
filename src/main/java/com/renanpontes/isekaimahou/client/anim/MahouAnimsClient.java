package com.renanpontes.isekaimahou.client.anim;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.ux.MeditationPose;
import com.renanpontes.isekaimahou.core.anim.AnimConfig;
import com.renanpontes.isekaimahou.core.anim.AnimPayloads;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Animações de combate estilo Epic Fight — cliente.
 * <ul>
 *   <li>Camada própria do Player Animator ({@link MahouAnimLayer}, prioridade 1250: acima da conjuração do módulo de
 *       equipamento (1200) e abaixo das armas do Pistoleiro (1300)).</li>
 *   <li>Combos de arma e esquiva: {@link CombatInput} (toca na hora e avisa o servidor).</li>
 *   <li>Gestos de magia e golpes dos outros: pacote {@link AnimPayloads.Play}.</li>
 *   <li>Guarda: escudo levantado (qualquer jogador visível) → braço livre em prontidão.</li>
 * </ul>
 * Nada toca enquanto o jogador medita, dorme, nada ou plana.
 */
public final class MahouAnimsClient {
    public static final ResourceLocation LAYER = IsekaiMahou.id("combat_anim");
    private static final int PRIORITY = 1250;
    private static final FirstPersonConfiguration FP_ARMS = new FirstPersonConfiguration(true, true, true, true);

    // ---- regras do servidor (chegam ao entrar)
    static boolean serverDodge = true;
    static int serverDodgeCooldown = 24;

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, AnimConfig.CLIENT_SPEC, "isekai_mahou-combat-client.toml");
        AnimLibrary.init();
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, MahouAnimLayer::new);
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(CombatInput.DODGE_KEY));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, MahouAnimsClient::tick);
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, CombatInput::onInteraction);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            serverDodge = true;
            serverDodgeCooldown = 24;
            CombatInput.reset();
        });
    }

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    public static void receive(AnimPayloads.Play p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (!(e instanceof AbstractClientPlayer player)) return;
        if (p.stop()) stopHold(player);
        else play(player, p.anim());
    }

    public static void rules(AnimPayloads.Rules r) {
        serverDodge = r.dodge();
        serverDodgeCooldown = Math.max(1, r.cooldown());
    }

    // =====================================================================================
    //  Tocar / parar
    // =====================================================================================
    @Nullable
    static MahouAnimLayer layer(AbstractClientPlayer player) {
        IAnimation a = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        return a instanceof MahouAnimLayer l ? l : null;
    }

    /** Toca a animação {@code id} no jogador. @return true se tocou (respeita config, prioridade e estado do jogador). */
    public static boolean play(AbstractClientPlayer player, String id) {
        AnimLibrary.Def d = AnimLibrary.get(id);
        if (d == null || !allowed(d) || blocked(player)) return false;
        MahouAnimLayer layer = layer(player);
        if (layer == null) return false;
        if (layer.playing() && layer.current.rank() > d.rank()) return false;
        KeyframeAnimationPlayer anim = new KeyframeAnimationPlayer(d.anim());
        boolean fpArms = AnimConfig.FIRST_PERSON_ARMS.get() && (d.kind() == AnimLibrary.Kind.MELEE || d.kind() == AnimLibrary.Kind.SPELL);
        // NONE = em 1ª pessoa a camada não interfere (mão normal do Minecraft / outras camadas)
        anim.setFirstPersonMode(fpArms ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE);
        if (fpArms) anim.setFirstPersonConfiguration(FP_ARMS);
        layer.mirror.setEnabled(d.mirror() && player.getMainArm() == HumanoidArm.LEFT);
        layer.current = d;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(Math.max(1, d.fadeIn()), Ease.INOUTSINE), anim, true);
        return true;
    }

    /** Solta a pose contínua (canalização/carga/guarda) se for ela que está tocando. */
    static void stopHold(AbstractClientPlayer player) {
        MahouAnimLayer layer = layer(player);
        if (layer == null || layer.current == null || !layer.current.hold()) return;
        fadeOut(layer, 5);
    }

    private static void fadeOut(MahouAnimLayer layer, int ticks) {
        layer.current = null;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(ticks, Ease.INOUTSINE), null);
    }

    private static boolean allowed(AnimLibrary.Def d) {
        return d.kind() == AnimLibrary.Kind.SPELL ? AnimConfig.SPELL_GESTURES.get() : AnimConfig.COMBAT_ANIMATIONS.get();
    }

    /** Estados em que nenhuma animação nossa deve tocar (pose do Minecraft ou da meditação manda). */
    static boolean blocked(AbstractClientPlayer p) {
        return p.isSleeping() || p.isVisuallySwimming() || p.isFallFlying() || p.isSpectator() || MeditationPose.isMeditating(p);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        CombatInput.tick(mc);
        if (mc.isPaused()) return;
        boolean combat = AnimConfig.COMBAT_ANIMATIONS.get();
        for (AbstractClientPlayer p : mc.level.players()) {
            MahouAnimLayer layer = layer(p);
            if (layer == null) continue;
            boolean playing = layer.playing();
            if (playing && blocked(p)) {
                // Começou a meditar/dormir/nadar no meio: larga tudo (a pose contínua não volta sozinha depois)
                fadeOut(layer, 3);
                continue;
            }
            // Guarda: escudo levantado → braço livre em prontidão
            boolean blocking = combat && p.isBlocking() && !blocked(p);
            if (blocking && !playing) {
                boolean shieldInMain = p.getUsedItemHand() == InteractionHand.MAIN_HAND;
                HumanoidArm shieldArm = shieldInMain ? p.getMainArm() : p.getMainArm().getOpposite();
                play(p, shieldArm == HumanoidArm.LEFT ? MahouAnims.GUARD_R : MahouAnims.GUARD_L);
            } else if (!blocking && playing && AnimLibrary.isGuard(layer.current)) {
                fadeOut(layer, 4);
            }
            // Golpes e magias viram o corpo para onde o jogador olha (como no Epic Fight)
            if (playing && layer.current != null && layer.current.faceLook() && !p.isPassenger()) {
                p.yBodyRot = Mth.approachDegrees(p.yBodyRot, p.getYHeadRot(), 40F);
            }
        }
    }

    private MahouAnimsClient() {}
}
