package com.renanpontes.isekaimahou.client.anim;

import javax.annotation.Nullable;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.ux.MeditationPose;
import com.renanpontes.isekaimahou.client.ux.PeaceClient;
import com.renanpontes.isekaimahou.core.anim.AnimConfig;
import com.renanpontes.isekaimahou.core.anim.AnimPayloads;
import com.renanpontes.isekaimahou.core.anim.GuardService;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;
import com.renanpontes.isekaimahou.core.api.Onboarding;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * v1.0 — <b>Bloqueio com aparo</b> no cliente. Segurar a tecla "Bloquear" (padrão: botão lateral do mouse 4) com uma
 * arma ou cajado na mão levanta a guarda; apertar no instante do golpe é o aparo (quem decide é o servidor:
 * {@link GuardService}). Aqui: a tecla, a pose (3ª pessoa pela camada de animação; 1ª pessoa inclinando a arma), o
 * clarão curto do aparo e uma dica única na barra de ação.
 */
final class GuardInput {
    static final KeyMapping KEY = new KeyMapping("key.isekai_mahou.guard", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, CoreKeys.CATEGORY);

    private static final int FLASH_TICKS = 5;
    /** Guarda levantada (previsto no cliente; o servidor confirma em silêncio ou manda soltar). */
    private static boolean guarding;
    /** O servidor recusou/encerrou: espera o jogador soltar a tecla antes de tentar de novo. */
    private static boolean suppressed;
    private static long lastHungryMsg = Long.MIN_VALUE / 2;
    private static int flash;
    /** Quanto a arma está inclinada em 1ª pessoa (0..1), com o valor do tick anterior para suavizar. */
    private static float lift, liftO;
    private static int lastHurtTime;
    // ---- matriz da mão principal guardada para a mão secundária não herdar a inclinação
    @Nullable
    private static Matrix4f savedPose;
    @Nullable
    private static Matrix3f savedNormal;

    static void reset() {
        guarding = false;
        suppressed = false;
        flash = 0;
        lift = liftO = 0F;
        lastHurtTime = 0;
        savedPose = null;
        savedNormal = null;
    }

    static boolean isGuarding() {
        return guarding;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        long now = mc.level.getGameTime();
        boolean down = KEY.isDown() && mc.screen == null;
        if (!down) suppressed = false;
        boolean want = down && !suppressed && canGuard(p, now);
        if (want != guarding) {
            guarding = want;
            PacketDistributor.sendToServer(new AnimPayloads.Guard(want));
            if (want) {
                p.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.35F, 1.5F);
                seenHint();
            } else {
                MahouAnimsClient.stopIf(p, MahouAnims.GUARD_WEAPON);
            }
        }
        if (guarding) {
            if (p.isSprinting()) p.setSprinting(false);
            // A pose expira/é trocada pelo gesto do aparo: retoca enquanto a tecla está segurada
            MahouAnimLayer layer = MahouAnimsClient.layer(p);
            if (layer != null && !layer.playing()) MahouAnimsClient.play(p, MahouAnims.GUARD_WEAPON);
        }
        liftO = lift;
        lift = Mth.clamp(lift + (guarding ? 0.34F : -0.34F), 0F, 1F);
        if (flash > 0) flash--;
        hint(mc, p);
    }

    private static boolean canGuard(LocalPlayer p, long now) {
        if (p.isSpectator() || p.isSleeping() || p.isDeadOrDying() || p.isUsingItem() || p.isFallFlying() || p.isSwimming()
                || MeditationPose.isMeditating(p) || PeaceClient.isOn()) {
            return false;
        }
        if (!GuardService.canGuardWith(p.getMainHandItem())) return false;
        if (!p.getAbilities().instabuild && p.getFoodData().getFoodLevel() <= 6) {
            if (now - lastHungryMsg > 40) {
                lastHungryMsg = now;
                p.displayClientMessage(Component.translatable("message.isekai_mahou.guard.hungry"), true);
            }
            return false;
        }
        return true;
    }

    /** Pacote do servidor: guarda recusada/encerrada ou aparo. */
    static void fx(byte kind) {
        Minecraft mc = Minecraft.getInstance();
        if (kind == GuardService.FX_PARRY) {
            flash = FLASH_TICKS;
            return;
        }
        if (kind == GuardService.FX_END && guarding) {
            guarding = false;
            suppressed = true;
            if (mc.player != null) MahouAnimsClient.stopIf(mc.player, MahouAnims.GUARD_WEAPON);
        }
    }

    // =====================================================================================
    //  Dica única (barra de ação): na primeira vez que apanha com uma arma na mão
    // =====================================================================================
    private static void hint(Minecraft mc, LocalPlayer p) {
        int hurt = p.hurtTime;
        boolean justHurt = hurt > lastHurtTime;
        lastHurtTime = hurt;
        if (!justHurt || AnimConfig.GUARD_HINT_SEEN.get() || KEY.isUnbound() || mc.screen != null) return;
        if (Onboarding.quiet(p) || !GuardService.canGuardWith(p.getMainHandItem()) || PeaceClient.isOn()) return;
        p.displayClientMessage(Component.translatable("message.isekai_mahou.guard.hint", KEY.getTranslatedKeyMessage()), true);
        seenHint();
    }

    private static void seenHint() {
        if (AnimConfig.GUARD_HINT_SEEN.get()) return;
        AnimConfig.GUARD_HINT_SEEN.set(true);
        AnimConfig.GUARD_HINT_SEEN.save();
    }

    // =====================================================================================
    //  1ª pessoa: a arma sobe e se inclina para a frente do rosto
    // =====================================================================================
    static void onRenderHand(RenderHandEvent e) {
        PoseStack ps = e.getPoseStack();
        if (e.getHand() != InteractionHand.MAIN_HAND) {
            // A mão secundária é desenhada depois, com a mesma pilha: devolve a matriz de antes da inclinação
            if (savedPose != null && savedNormal != null) {
                ps.last().pose().set(savedPose);
                ps.last().normal().set(savedNormal);
            }
            savedPose = null;
            savedNormal = null;
            return;
        }
        savedPose = null;
        savedNormal = null;
        float t = Mth.lerp(e.getPartialTick(), liftO, lift);
        Minecraft mc = Minecraft.getInstance();
        if (t <= 0.01F || mc.player == null) return;
        t = t * t * (3F - 2F * t);
        savedPose = new Matrix4f(ps.last().pose());
        savedNormal = new Matrix3f(ps.last().normal());
        float side = mc.player.getMainArm() == HumanoidArm.RIGHT ? 1F : -1F;
        // Gira em torno de onde a mão fica na tela (não do centro da câmera), depois puxa para o meio e para cima
        float px = 0.56F * side, py = -0.52F, pz = -0.72F;
        ps.translate(-0.22F * side * t, 0.14F * t, 0.02F * t);
        ps.translate(px, py, pz);
        ps.mulPose(Axis.ZP.rotationDegrees(32F * side * t));
        ps.mulPose(Axis.XP.rotationDegrees(-8F * t));
        ps.translate(-px, -py, -pz);
    }

    // =====================================================================================
    //  Clarão do aparo: duas faixas claras, em cima e embaixo, por 1/4 de segundo
    // =====================================================================================
    static void renderFlash(GuiGraphics g, DeltaTracker dt) {
        if (flash <= 0 || !AnimConfig.PARRY_FLASH.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        float k = Mth.clamp((flash - dt.getGameTimeDeltaPartialTick(false)) / FLASH_TICKS, 0F, 1F);
        int a = (int) (k * k * 110F);
        if (a <= 2) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int band = Math.max(12, h / 7);
        int solid = (a << 24) | 0xFFF6D8, clear = 0x00FFF6D8;
        g.fillGradient(0, 0, w, band, solid, clear);
        g.fillGradient(0, h - band, w, h, clear, solid);
    }

    private GuardInput() {}
}
