package com.renanpontes.isekaimahou.feature.character.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.feature.character.CharacterNetwork;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RaceFlight;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * SÓ CLIENTE (v1.3.1). Controles do <b>voo planado</b> das formas aladas ({@link RaceFlight}) no jogador local:
 * <ul>
 *   <li>Pular e apertar Pular de novo no ar (2× rápido) = decolagem (salto forte + batida; as asas abrem no topo).</li>
 *   <li>Pular no ar caindo = abre as asas (como o élitro).</li>
 *   <li>Planando: segurar Pular ou Correr = batidas de asas (impulso para onde olha + sustentação); Shift = fecha as asas.</li>
 * </ul>
 * O impulso é aplicado aqui na hora (o movimento do jogador é do cliente, como o foguete no élitro); o servidor valida a
 * recarga, cobra o recurso e mostra a batida para os outros. Também cuida da sensação de voo: a câmera <b>inclina nas
 * curvas</b> (como um avião) e o campo de visão abre com a velocidade (respeita a opção "Efeitos de FOV" do jogo).
 */
public final class GlideClient {
    private static boolean wasJump, wasSneak;
    private static long groundJumpAt = -100, lastGroundAt = -100;
    /** Ticks que faltam para as asas abrirem depois da decolagem (0 = nada pendente). */
    private static int pendingGlide;
    private static long nextBeat;
    private static long noManaMsgAt = -100;
    private static float lastYaw;
    private static float roll, rollPrev;
    private static float speedFov;

    static void init() {
        NeoForge.EVENT_BUS.addListener(GlideClient::onTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, GlideClient::onCamera);
        NeoForge.EVENT_BUS.addListener(GlideClient::onFov);
    }

    /** Raça alada transformada do jogador (null = não tem asas agora). */
    static Race wingedForm(Player p) {
        if (ClientCharacter.form(p.getId()) == null) return null;
        ClientCharacter.Look look = ClientCharacter.look(p.getId());
        Race r = look != null ? look.race() : null;
        return RaceFlight.winged(r) ? r : null;
    }

    private static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        rollPrev = roll;
        if (p == null || mc.level == null) {
            pendingGlide = 0;
            roll = rollPrev = speedFov = 0F;
            return;
        }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        Race race = wingedForm(p);
        boolean jump = mc.options.keyJump.isDown();
        boolean sneak = mc.options.keyShift.isDown();
        boolean jumpPressed = jump && !wasJump, sneakPressed = sneak && !wasSneak;
        wasJump = jump;
        wasSneak = sneak;
        feel(p, race);
        if (race == null || mc.screen != null) {
            pendingGlide = 0;
            return;
        }
        // O pulo do chão já tirou o jogador do chão neste mesmo tick: "estava no chão no tick anterior" conta como chão
        boolean grounded = p.onGround() || now - lastGroundAt <= 1;
        if (p.onGround()) lastGroundAt = now;
        if (grounded && !p.isFallFlying()) {
            pendingGlide = 0;
            if (jumpPressed) groundJumpAt = now;
            return;
        }
        boolean blocked = p.getAbilities().flying || p.isSpectator() || p.isPassenger() || p.isInWater() || p.isInLava()
                || p.onClimbable() || p.hasEffect(MobEffects.LEVITATION) || p.isDeadOrDying();
        if (blocked) {
            pendingGlide = 0;
            return;
        }
        RaceFlight.Wing w = RaceFlight.wing(race);
        if (p.isFallFlying()) {
            if (sneakPressed) {
                // Fecha as asas e cai
                p.stopFallFlying();
                send(RaceFlight.ACT_STOP);
                return;
            }
            if ((jump || sprintHeld(mc)) && now >= nextBeat) beat(p, race, w, now, false);
            return;
        }
        // No ar com as asas fechadas
        if (pendingGlide > 0) {
            pendingGlide--;
            if (p.getDeltaMovement().y < 0.25 || pendingGlide == 0) openWings(p);
            return;
        }
        if (jumpPressed) {
            if (now - groundJumpAt <= RaceFlight.TAKEOFF_WINDOW && now >= nextBeat) {
                // Pulo duplo: decolagem
                if (beat(p, race, w, now, true)) pendingGlide = 10;
                else openWings(p);
            } else {
                openWings(p);
            }
        }
    }

    /** Batida de asas prevista aqui (o servidor cobra o recurso). {@code false} = sem recurso. */
    private static boolean beat(LocalPlayer p, Race race, RaceFlight.Wing w, long now, boolean takeoff) {
        float cost = RaceFlight.cost(race, ClientMagic.maxMana, takeoff);
        if (!p.getAbilities().instabuild && ClientMagic.mana < cost) {
            nextBeat = now + 10;
            if (now - noManaMsgAt > 60) {
                noManaMsgAt = now;
                p.displayClientMessage(Component.translatable("message.isekai_mahou.character.glide_no_mana").withColor(0xA89EC0), true);
            }
            return false;
        }
        nextBeat = now + w.interval();
        Vec3 look = p.getLookAngle();
        p.setDeltaMovement(takeoff ? RaceFlight.takeoffVelocity(p.getDeltaMovement(), look, w)
                : RaceFlight.beatVelocity(p.getDeltaMovement(), look, w));
        p.resetFallDistance();
        ClientCharacter.markBeat(p.getId(), now);
        FormFx.beatFx(p, race, takeoff, true);
        send(takeoff ? RaceFlight.ACT_TAKEOFF : RaceFlight.ACT_BEAT);
        return true;
    }

    /** Abre as asas (planar): o som de vento do élitro vem do próprio vanilla. */
    private static void openWings(LocalPlayer p) {
        pendingGlide = 0;
        if (p.onGround() || p.isFallFlying()) return;
        p.startFallFlying();
        send(RaceFlight.ACT_START);
        Race race = wingedForm(p);
        if (race != null) {
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), RaceFlight.flapSound(race), SoundSource.PLAYERS,
                    0.5F, RaceFlight.flapPitch(race) * 1.15F, false);
        }
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(new CharacterNetwork.Glide(action));
    }

    /** Tecla Correr segurada de verdade (o "alternar corrida" do jogo deixaria o isDown sempre ligado). */
    private static boolean sprintHeld(Minecraft mc) {
        InputConstants.Key k = mc.options.keySprint.getKey();
        if (k.getType() != InputConstants.Type.KEYSYM || k.getValue() < 0) return false;
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), k.getValue());
    }

    // =====================================================================================
    //  Sensação de voo: inclinação nas curvas e FOV com a velocidade
    // =====================================================================================
    private static void feel(LocalPlayer p, Race race) {
        float yaw = p.getYRot();
        float rate = Mth.wrapDegrees(yaw - lastYaw);
        lastYaw = yaw;
        float target = 0F, fov = 0F;
        if (race != null && p.isFallFlying()) {
            target = Mth.clamp(rate * 1.3F, -24F, 24F) * FxQuality.shake();
            double speed = p.getDeltaMovement().length();
            fov = (float) Mth.clamp((speed - 0.7) * 0.11, 0.0, 0.15);
        }
        roll = Mth.lerp(0.2F, roll, target);
        if (Math.abs(roll) < 0.02F) roll = 0F;
        speedFov = Mth.lerp(0.15F, speedFov, fov);
        if (speedFov < 0.001F) speedFov = 0F;
    }

    private static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        if (roll == 0F && rollPrev == 0F) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getCameraEntity() != mc.player) return;
        // Curva para a direita = asa direita para baixo = horizonte gira para a esquerda (roll positivo)
        e.setRoll(e.getRoll() + Mth.lerp((float) e.getPartialTick(), rollPrev, roll));
    }

    private static void onFov(ComputeFovModifierEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getPlayer() != mc.player) return;
        float scale = mc.options.fovEffectScale().get().floatValue();
        float punch = FormFx.fovPunch();
        if (speedFov <= 0F && punch == 1F) return;
        e.setNewFovModifier(e.getNewFovModifier() * (1F + (speedFov + (punch - 1F)) * scale));
    }

    private GlideClient() {}
}
