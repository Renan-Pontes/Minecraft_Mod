package com.renanpontes.isekaimahou.feature.afterlife.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeManager;
import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeNet;

import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Estado do módulo no cliente: quem é alma, o desafio do jogador local (tempo, batidas, ajuda), o corpo mais recente
 * (rastreador) e o minijogo das batidas do coração — um pulso corre pela tela a cada {@link #PERIOD_MS}; apertar
 * PULAR (ou clicar) quando ele passa pela faixa do meio = acerto. Uma tentativa por batida (apertar sem parar erra).
 */
public final class ClientAfterlife {
    public static final long PERIOD_MS = 1100L;
    /** Meia-largura da faixa de acerto (fração do período: ±12% ≈ ±130 ms). */
    public static final double WINDOW = 0.12;
    static final int GIVE_UP_TICKS = 40;
    private static final ResourceLocation POST = IsekaiMahou.id("shaders/post/afterlife_spirit.json");

    public static final KeyMapping GIVE_UP = new KeyMapping("key.isekai_mahou.afterlife_give_up", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, CoreKeys.CATEGORY);

    private static final Set<UUID> SPIRITS = new HashSet<>();
    static AfterlifeNet.State state = AfterlifeNet.State.NONE;
    static AfterlifeNet.Mark mark = AfterlifeNet.Mark.NONE;

    // minijogo
    static boolean inRange;
    static long rhythmStart;
    private static long judgedCycle = -1;
    private static long cueCycle = -1;
    static long flashAt;
    static boolean flashHit;
    static int giveUpHeld;
    private static int ambient;
    private static boolean postOurs;
    private static boolean postFailed;

    // ============================================================================================= pacotes

    public static void onState(AfterlifeNet.State s) {
        boolean was = state.active();
        state = s;
        if (s.active() && !was) {
            inRange = false;
            giveUpHeld = 0;
            judgedCycle = -1;
        }
        refreshLocal();
    }

    public static void onSpirit(AfterlifeNet.Spirit s) {
        if (s.spirit()) SPIRITS.add(s.player());
        else SPIRITS.remove(s.player());
        refreshLocal();
    }

    public static void onMark(AfterlifeNet.Mark m) {
        mark = m;
    }

    private static void refreshLocal() {
        Minecraft mc = Minecraft.getInstance();
        boolean local = state.active() || (mc.player != null && SPIRITS.contains(mc.player.getUUID()));
        AfterlifeManager.localSpirit = local;
    }

    public static boolean isSpirit(Player p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && p == mc.player && state.active()) return true;
        return SPIRITS.contains(p.getUUID());
    }

    static boolean active() {
        return state.active();
    }

    static void reset() {
        SPIRITS.clear();
        state = AfterlifeNet.State.NONE;
        mark = AfterlifeNet.Mark.NONE;
        AfterlifeManager.localSpirit = false;
        inRange = false;
        giveUpHeld = 0;
        Minecraft mc = Minecraft.getInstance();
        if (postOurs && mc.gameRenderer != null) shutdownPost(mc.gameRenderer);
    }

    // ============================================================================================= ritmo

    static double phase() {
        long t = Util.getMillis() - rhythmStart;
        return (t % PERIOD_MS) / (double) PERIOD_MS;
    }

    private static long cycle() {
        return (Util.getMillis() - rhythmStart) / PERIOD_MS;
    }

    /** Aperto do jogador (PULAR ou ataque) durante o desafio. */
    static void press() {
        if (!state.active() || !inRange) return;
        long c = cycle();
        if (c == judgedCycle) return;
        judgedCycle = c;
        boolean hit = Math.abs(phase() - 0.5) <= WINDOW;
        flashAt = Util.getMillis();
        flashHit = hit;
        Minecraft mc = Minecraft.getInstance();
        if (hit) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4F, 0.6F));
        else mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.8F));
        PacketDistributor.sendToServer(new AfterlifeNet.Beat(hit));
    }

    // ============================================================================================= eventos

    static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (!state.active() || mc.screen != null || e.getAction() != GLFW.GLFW_PRESS) return;
        if (mc.options.keyJump.matches(e.getKey(), e.getScanCode())) press();
    }

    /** A alma não ataca, não usa nem escolhe blocos; o clique de ataque vale como batida. */
    static void onInteractKey(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isSpirit(mc.player)) return;
        if (e.isAttack()) press();
        e.setSwingHand(false);
        e.setCanceled(true);
    }

    static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (state.active() || !SPIRITS.isEmpty()) reset();
            return;
        }
        updatePost(mc, AfterlifeManager.localSpirit);
        spiritParticles(mc);
        if (!state.active()) {
            giveUpHeld = 0;
            return;
        }
        Vec3 body = new Vec3(state.x(), state.y(), state.z());
        boolean now = mc.player.position().distanceTo(body) <= AfterlifeManager.CLIENT_RANGE;
        if (now && !inRange) {
            rhythmStart = Util.getMillis() - PERIOD_MS / 4; // primeira batida em ~0,3 s
            judgedCycle = -1;
            cueCycle = -1;
        }
        inRange = now;
        if (inRange) {
            // a batida "toca" quando o pulso passa pela faixa: ajuda a pegar o ritmo
            long c = cycle();
            if (c != cueCycle && phase() >= 0.47) {
                cueCycle = c;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.0F));
            }
        } else if (++ambient % 30 == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.8F, 0.45F));
        }
        // segurar Desistir por 2 s (soltar zera; depois de mandar, só conta de novo ao soltar)
        if (mc.screen == null && GIVE_UP.isDown()) {
            if (giveUpHeld >= 0 && ++giveUpHeld >= GIVE_UP_TICKS) {
                giveUpHeld = -1;
                PacketDistributor.sendToServer(AfterlifeNet.GiveUp.INSTANCE);
            }
        } else {
            giveUpHeld = 0;
        }
    }

    private static void spiritParticles(Minecraft mc) {
        if (SPIRITS.isEmpty() || mc.level == null || mc.level.getGameTime() % 3 != 0) return;
        for (UUID id : SPIRITS) {
            Player p = mc.level.getPlayerByUUID(id);
            if (p == null || (p == mc.player && mc.options.getCameraType().isFirstPerson())) continue;
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, p.getX() + (p.getRandom().nextDouble() - 0.5) * 0.5,
                    p.getY() + 0.2, p.getZ() + (p.getRandom().nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
        }
    }

    // ============================================================================================= tela cinza

    private static void updatePost(Minecraft mc, boolean need) {
        GameRenderer gr = mc.gameRenderer;
        PostChain cur = gr.currentEffect();
        boolean ours = cur != null && POST.toString().equals(cur.getName());
        if (need) {
            if (!ours && cur == null && !postFailed) {
                try {
                    gr.loadEffect(POST);
                } catch (Throwable t) {
                    postFailed = true;
                    IsekaiMahou.LOGGER.warn("afterlife: efeito de tela cinza indisponível", t);
                }
                cur = gr.currentEffect();
                ours = cur != null && POST.toString().equals(cur.getName());
            }
            postOurs = ours;
        } else if (ours) {
            shutdownPost(gr);
        } else {
            postOurs = false;
        }
    }

    private static void shutdownPost(GameRenderer gr) {
        PostChain cur = gr.currentEffect();
        if (cur != null && POST.toString().equals(cur.getName())) gr.shutdownEffect();
        postOurs = false;
    }

    private ClientAfterlife() {}
}
