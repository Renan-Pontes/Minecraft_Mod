package com.renanpontes.isekaimahou.client.anim;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.ux.MeditationPose;
import com.renanpontes.isekaimahou.core.anim.AnimConfig;
import com.renanpontes.isekaimahou.core.anim.AnimPayloads;
import com.renanpontes.isekaimahou.core.anim.GuardService;
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
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Animações de combate estilo Epic Fight — cliente.
 * <ul>
 *   <li>Camada própria do Player Animator ({@link MahouAnimLayer}, prioridade 1250: acima da conjuração do módulo de
 *       equipamento (1200) e abaixo das armas do Pistoleiro (1300)).</li>
 *   <li>Combos de arma e esquiva: {@link CombatInput} (toca na hora e avisa o servidor).</li>
 *   <li>Gestos de magia e golpes dos outros: pacote {@link AnimPayloads.Play}.</li>
 *   <li>Guarda: escudo levantado (qualquer jogador visível) → braço livre em prontidão.</li>
 *   <li>v1.0 — Bloqueio com arma/cajado e aparo: {@link GuardInput} (tecla, pose, clarão do aparo).</li>
 *   <li>v1.3: efeitos de impacto ({@link AnimCues}) no tick certo, poses com limite de tempo, emotes contínuos que
 *       param quando o jogador se mexe, e a roda de emotes ({@link EmoteWheelScreen}).</li>
 * </ul>
 * Primeira pessoa: só golpes/gestos de braço podem aparecer (opção "braços animados"); animações de corpo inteiro
 * (rolar, cair, girar, emotes) forçam a mão do Minecraft ({@code VANILLA}) para nada atravessar a câmera — inclusive a
 * camada de conjuração do equipamento (prioridade menor) fica escondida enquanto elas tocam.
 * Nada toca enquanto o jogador medita, dorme, nada ou plana.
 */
public final class MahouAnimsClient {
    public static final ResourceLocation LAYER = IsekaiMahou.id("combat_anim");
    private static final int PRIORITY = 1250;
    private static final FirstPersonConfiguration FP_ARMS = new FirstPersonConfiguration(true, true, true, true);

    private static final List<Runnable> PARRY_LISTENERS = new CopyOnWriteArrayList<>();
    // ---- regras do servidor (chegam ao entrar)
    static boolean serverDodge = true;
    static int serverDodgeCooldown = 24;

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, AnimConfig.CLIENT_SPEC, "isekai_mahou-combat-client.toml");
        AnimLibrary.init();
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, MahouAnimLayer::new);
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(CombatInput.DODGE_KEY);
            e.register(EmoteWheelScreen.KEY);
            e.register(GuardInput.KEY);
        });
        // v1.0: clarão curto do aparo (por baixo do resto do HUD) e a arma inclinada em 1ª pessoa ao bloquear
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerBelowAll(IsekaiMahou.id("anim_parry_flash"), GuardInput::renderFlash));
        NeoForge.EVENT_BUS.addListener(RenderHandEvent.class, GuardInput::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, MahouAnimsClient::tick);
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, CombatInput::onInteraction);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            serverDodge = true;
            serverDodgeCooldown = 24;
            CombatInput.reset();
            GuardInput.reset();
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
        if (!p.stop()) play(player, p.anim());
        else if (p.anim().isEmpty()) stopHold(player);
        else stopIf(player, p.anim());
    }

    /** v1.0: resposta do servidor ao bloqueio (recusou/encerrou a guarda ou aparo). */
    public static void guardFx(AnimPayloads.GuardFx p) {
        GuardInput.fx(p.kind());
        if (p.kind() == GuardService.FX_PARRY) {
            for (Runnable r : PARRY_LISTENERS) r.run();
        }
    }

    /** Outros módulos de cliente (ex.: micro-pausa do combate) pedem para ser avisados quando o jogador local apara um golpe. */
    public static void onLocalParry(Runnable listener) {
        PARRY_LISTENERS.add(listener);
    }

    /** O jogador local está com a guarda levantada (bloqueio com arma/cajado)? */
    public static boolean isLocalGuarding() {
        return GuardInput.isGuarding();
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
        if (layer.playing()) {
            AnimLibrary.Def cur = layer.current;
            if (cur.rank() > d.rank()) return false;
            // Tranco não corta pose de carga/canalização (a magia continua carregando no servidor)
            if (d.kind() == AnimLibrary.Kind.FEEL && d.rank() <= 2 && cur.hold() && cur.kind() == AnimLibrary.Kind.SPELL) return false;
        }
        KeyframeAnimationPlayer anim = new KeyframeAnimationPlayer(d.anim());
        AnimLibrary.Kind k = d.kind();
        boolean armKind = k == AnimLibrary.Kind.MELEE || k == AnimLibrary.Kind.SPELL || k == AnimLibrary.Kind.DRAW;
        boolean fpArms = AnimConfig.FIRST_PERSON_ARMS.get() && armKind && d.fpArms();
        // NONE = em 1ª pessoa a camada não interfere (mão normal do Minecraft / outras camadas);
        // VANILLA = corpo inteiro se mexe: força a mão normal (acima da camada de conjuração, que mostraria braços)
        anim.setFirstPersonMode(fpArms ? FirstPersonMode.THIRD_PERSON_MODEL : d.fpArms() ? FirstPersonMode.NONE : FirstPersonMode.VANILLA);
        if (fpArms) anim.setFirstPersonConfiguration(FP_ARMS);
        layer.mirror.setEnabled(d.mirror() && player.getMainArm() == HumanoidArm.LEFT);
        layer.current = d;
        layer.startTick = player.level().getGameTime();
        layer.cueDone = d.cue() == AnimLibrary.Cue.NONE;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(Math.max(1, d.fadeIn()), Ease.INOUTSINE), anim, true);
        return true;
    }

    /** Solta a pose contínua (canalização/carga/guarda) se for ela que está tocando. */
    static void stopHold(AbstractClientPlayer player) {
        MahouAnimLayer layer = layer(player);
        if (layer == null || layer.current == null || !layer.current.hold()) return;
        fadeOut(layer, 5);
    }

    /** Solta a animação {@code id} só se for ela que está tocando (não corta o que começou depois). */
    static void stopIf(AbstractClientPlayer player, String id) {
        MahouAnimLayer layer = layer(player);
        if (layer == null || layer.current == null || !layer.current.id().equals(id)) return;
        fadeOut(layer, 4);
    }

    private static void fadeOut(MahouAnimLayer layer, int ticks) {
        layer.current = null;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(ticks, Ease.INOUTSINE), null);
    }

    private static boolean allowed(AnimLibrary.Def d) {
        return switch (d.kind()) {
            case SPELL -> AnimConfig.SPELL_GESTURES.get();
            case FEEL -> AnimConfig.FEEL_ANIMATIONS.get();
            case DRAW -> AnimConfig.WEAPON_DRAW.get();
            case EMOTE -> AnimConfig.EMOTES.get();
            default -> AnimConfig.COMBAT_ANIMATIONS.get();
        };
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
        GuardInput.tick(mc);
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
            } else if (!blocking && playing && AnimLibrary.isShieldGuard(layer.current)) {
                fadeOut(layer, 4);
            }
            // Golpes e magias viram o corpo para onde o jogador olha (como no Epic Fight)
            if (playing && layer.current != null && layer.current.faceLook() && !p.isPassenger()) {
                p.yBodyRot = Mth.approachDegrees(p.yBodyRot, p.getYHeadRot(), 40F);
            }
            if (playing && layer.current != null) timeline(p, layer);
        }
    }

    /** Efeito de impacto no tick certo, limite das poses contínuas e emotes que param quando o jogador se mexe. */
    private static void timeline(AbstractClientPlayer p, MahouAnimLayer layer) {
        AnimLibrary.Def d = layer.current;
        long elapsed = p.level().getGameTime() - layer.startTick;
        if (!layer.cueDone && elapsed >= d.cueTick()) {
            layer.cueDone = true;
            // Atrasado demais (ex.: o jogador entrou na vista agora) = sem efeito fora de hora
            if (elapsed <= d.cueTick() + 2) AnimCues.fire(p, d.cue());
        }
        if (d.hold() && d.maxTicks() > 0 && elapsed > d.maxTicks()) {
            fadeOut(layer, 5);
            return;
        }
        if (d.kind() == AnimLibrary.Kind.EMOTE && elapsed > 3 && interruptsEmote(p, d.hold())) fadeOut(layer, 4);
    }

    /** Andar, pular, agachar, atacar ou levar dano solta o emote (os de uma vez só param só com ataque/dano). */
    private static boolean interruptsEmote(AbstractClientPlayer p, boolean hold) {
        if (p.swinging || p.hurtTime > 0 || p.isUsingItem()) return true;
        if (!hold) return false;
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        return dx * dx + dz * dz > 0.0016 || !p.onGround() || p.isShiftKeyDown();
    }

    private MahouAnimsClient() {}
}
