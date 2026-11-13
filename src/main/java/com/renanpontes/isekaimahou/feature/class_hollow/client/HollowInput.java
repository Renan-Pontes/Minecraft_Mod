package com.renanpontes.isekaimahou.feature.class_hollow.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;
import com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTech;
import com.renanpontes.isekaimahou.feature.mounts.client.MountKeyContext;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * SÓ CLIENTE (v1.4). As teclas das técnicas inatas do Hollow (só fazem algo para quem é Hollow):
 * <ul>
 *   <li><b>Instinto Hollow</b> (padrão R): apertar/soltar vão ao servidor, que decide pelo movimento — toque = Bala,
 *   segurar = Cero; agachado: toque = Pesquisa, segurar = Gran Rey Cero; correndo = Sonído. Pilotando uma montaria
 *   com habilidade, a tecla fica para a montaria (v1.0: contexto {@link MountKeyContext#ON_FOOT} — as duas dividem o R
 *   sem conflito na tela de Controles; em barco, cavalo e outros veículos comuns a técnica continua valendo);</li>
 *   <li><b>Roda Hollow</b> (padrão H): abre a roda das outras técnicas ({@link HollowWheelScreen}).</li>
 * </ul>
 * Também guarda as recargas recebidas do servidor (para o HUD e a roda) e o tempo de carga da tecla.
 */
public final class HollowInput {
    public static final KeyMapping TECH = new KeyMapping("key.isekai_mahou.hollow_technique", MountKeyContext.ON_FOOT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CoreKeys.CATEGORY);
    public static final KeyMapping WHEEL = new KeyMapping("key.isekai_mahou.hollow_wheel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CoreKeys.CATEGORY);

    private static final int N = HollowTech.values().length;
    private static final long[] CD_END = new long[N];
    private static final int[] CD_TOTAL = new int[N];
    private static int sonidoChain;

    private static boolean down;
    private static long downAt;
    /** 0 normal, 1 agachado, 2 correndo (no instante do aperto). */
    private static int mode;

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(TECH);
            e.register(WHEEL);
        });
        NeoForge.EVENT_BUS.addListener(HollowInput::tick);
    }

    static boolean isHollow() {
        MagicData d = ClientMagic.data();
        return d != null && d.path() == MagicPath.HOLLOW;
    }

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            down = false;
            return;
        }
        if (!isHollow()) {
            if (down) send(HollowNetwork.TECH_RELEASE, 0);
            down = false;
            while (WHEEL.consumeClick()) { }
            return;
        }
        boolean held = TECH.isDown() && mc.screen == null && !MountKeyContext.ridingAbilityMount(mc.player)
                && !mc.player.isSpectator();
        if (held && !down) {
            down = true;
            downAt = mc.level.getGameTime();
            mode = mc.player.isSprinting() ? 2 : mc.player.isShiftKeyDown() ? 1 : 0;
            send(HollowNetwork.TECH_PRESS, 0);
        } else if (!held && down) {
            down = false;
            send(HollowNetwork.TECH_RELEASE, 0);
        }
        while (TECH.consumeClick()) { }
        while (WHEEL.consumeClick()) {
            if (mc.screen == null && !mc.player.isSpectator()) mc.setScreen(new HollowWheelScreen());
        }
    }

    static void send(int action, int value) {
        PacketDistributor.sendToServer(new HollowNetwork.TechInput(action, value));
    }

    // =====================================================================================
    //  Recargas (do servidor)
    // =====================================================================================
    static void accept(HollowNetwork.TechCd p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || p.tech() < 0 || p.tech() >= N) return;
        CD_END[p.tech()] = mc.level.getGameTime() + p.ticks();
        CD_TOTAL[p.tech()] = Math.max(1, p.ticks());
        if (p.tech() == HollowTech.SONIDO.ordinal()) sonidoChain = p.chain();
    }

    /** Fração da recarga que ainda falta (0 = pronta). */
    static float cooldown(HollowTech t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0F;
        long left = CD_END[t.ordinal()] - mc.level.getGameTime();
        return left <= 0 ? 0F : Math.min(1F, left / (float) CD_TOTAL[t.ordinal()]);
    }

    /** Segundos de recarga que faltam (0 = pronta). */
    static float cooldownSeconds(HollowTech t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0F;
        return Math.max(0, CD_END[t.ordinal()] - mc.level.getGameTime()) / 20F;
    }

    static int sonidoChain() {
        return sonidoChain;
    }

    /** Ticks segurando a tecla (-1 = solta). */
    static int heldTicks(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!down || mc.level == null) return -1;
        return (int) (mc.level.getGameTime() - downAt);
    }

    static int mode() {
        return mode;
    }

    static void clear() {
        down = false;
        java.util.Arrays.fill(CD_END, 0L);
        sonidoChain = 0;
    }

    private HollowInput() {}
}
