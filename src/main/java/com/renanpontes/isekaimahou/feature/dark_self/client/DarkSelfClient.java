package com.renanpontes.isekaimahou.feature.dark_self.client;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.dark_self.DarkSelfNetwork;
import com.renanpontes.isekaimahou.feature.dark_self.DarkSelfRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cliente do módulo "dark_self": o renderer da Sombra e o efeito de "a sua sombra sumiu" — o círculo de sombra do
 * jogador (sob os pés) deixa de ser desenhado, saem uns fiapos escuros dos pés dele e, para o próprio jogador, a tela
 * escurece de leve. Nada pesado: com shaders fica só o véu e as partículas. Em primeira pessoa (onde o jogo não desenha
 * a sombra do próprio jogador) o dono vê o presságio do {@link DarkSelfOmen}.
 */
public class DarkSelfClient implements ClientModule {
    /** Jogadores cuja sombra sumiu. */
    private static final Set<UUID> HIDDEN = ConcurrentHashMap.newKeySet();
    /** Força original da sombra de cada renderer de jogador que mexemos (para devolver). */
    private static final Map<EntityRenderer<?>, Float> ORIGINAL = new IdentityHashMap<>();
    private static Field shadowStrength;
    private static boolean reflectionFailed;
    private static float veil, veilPrev;

    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(DarkSelfRegistry.DARK_SELF.get(), DarkSelfRenderer::new));
        DarkSelfNetwork.clientShadow = s -> {
            if (s.hidden()) {
                HIDDEN.add(s.player());
            } else {
                HIDDEN.remove(s.player());
                Player me = Minecraft.getInstance().player;
                if (me != null && me.getUUID().equals(s.player())) DarkSelfOmen.stop(); // cancelado/reiniciado
            }
        };
        // Presságio em primeira pessoa (só o dono recebe).
        DarkSelfNetwork.clientOmen = o -> {
            if (o.detach()) DarkSelfOmen.detach(new Vec3(o.x(), o.y(), o.z()));
            else DarkSelfOmen.start();
        };
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, DarkSelfOmen::render);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderPlayerEvent.Pre.class, DarkSelfClient::preRender);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> {
            tick();
            DarkSelfOmen.tick();
        });
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Pre.class, DarkSelfClient::veil);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            HIDDEN.clear();
            DarkSelfOmen.stop();
            veil = veilPrev = 0;
        });
    }

    /** Tira (ou devolve) a sombra no chão. O renderer é o mesmo para todos os jogadores: decide a cada jogador. */
    private static void preRender(RenderPlayerEvent.Pre e) {
        EntityRenderer<?> r = e.getRenderer();
        boolean hide = HIDDEN.contains(e.getEntity().getUUID());
        if (!hide && !ORIGINAL.containsKey(r)) return; // nunca mexemos neste renderer
        Field f = field();
        if (f == null) return;
        try {
            if (hide) {
                if (!ORIGINAL.containsKey(r)) ORIGINAL.put(r, f.getFloat(r));
                f.setFloat(r, 0F);
            } else {
                f.setFloat(r, ORIGINAL.get(r));
            }
        } catch (IllegalAccessException | RuntimeException ex) {
            reflectionFailed = true;
        }
    }

    private static Field field() {
        if (shadowStrength != null || reflectionFailed) return shadowStrength;
        try {
            shadowStrength = ObfuscationReflectionHelper.findField(EntityRenderer.class, "shadowStrength");
        } catch (RuntimeException ex) {
            reflectionFailed = true;
            IsekaiMahou.LOGGER.warn("dark_self: nao achei EntityRenderer.shadowStrength; a sombra nao vai sumir", ex);
        }
        return shadowStrength;
    }

    /** Fiapos escuros nos pés de quem perdeu a sombra; véu de tela para o próprio jogador. */
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        veilPrev = veil;
        if (mc.level == null || mc.player == null) return;
        boolean mine = HIDDEN.contains(mc.player.getUUID());
        veil = Mth.approach(veil, mine ? 0.13F : 0F, mine ? 0.004F : 0.02F);
        if (HIDDEN.isEmpty() || mc.isPaused() || mc.level.getGameTime() % 6 != 0) return;
        for (Player p : mc.level.players()) {
            if (!HIDDEN.contains(p.getUUID()) || p.distanceToSqr(mc.player) > 48 * 48) continue;
            mc.level.addParticle(ParticleTypes.SQUID_INK, p.getRandomX(0.6), p.getY() + 0.05, p.getRandomZ(0.6), 0, 0.01, 0);
        }
    }

    private static void veil(RenderGuiEvent.Pre e) {
        float a = Mth.lerp(e.getPartialTick().getGameTimeDeltaPartialTick(false), veilPrev, veil);
        if (a <= 0.003F) return;
        var g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int center = ((int) (a * 255) & 0xFF) << 24;
        int edge = ((int) (Math.min(1F, a * 2.2F) * 255) & 0xFF) << 24;
        g.fill(0, 0, w, h, center | 0x07030F);
        int band = Math.max(8, h / 5);
        g.fillGradient(0, 0, w, band, edge | 0x07030F, 0x0007030F);
        g.fillGradient(0, h - band, w, h, 0x0007030F, edge | 0x07030F);
    }
}
