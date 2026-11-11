package com.renanpontes.isekaimahou.client.boss;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.core.boss.BossPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Telegrafias no cliente: guarda as formas recebidas (máx. {@link #MAX_SHAPES}) e desenha tudo em 3 chamadas por
 * quadro (cores, runas, setas) no estágio AFTER_PARTICLES — o mesmo lugar das partículas, então funciona igual com
 * gráficos Rápido/Bonito/Fabuloso e com shaders (Iris troca o shader POSITION_COLOR pelo dele).
 */
public final class TelegraphRenderer {
    static final int MAX_SHAPES = 256;
    /** Não desenha formas mais longe que isto da câmera. */
    private static final double DRAW_RANGE = 160;

    private static final Map<Integer, TelegraphShape> SHAPES = new LinkedHashMap<>();
    /** Relógio próprio (ticks de cliente sem pausa): não pula com a sincronização de tempo do servidor. */
    private static long ticks;
    private static ClientLevel shapesLevel;
    private static Boolean texturesPresent;

    // ------------------------------------------------------------------ pacotes (thread principal)

    public static void receive(BossPayloads.Show p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level != shapesLevel) {
            SHAPES.clear();
            shapesLevel = mc.level;
        }
        // Acima do limite: some a mais antiga
        while (SHAPES.size() >= MAX_SHAPES) {
            Iterator<Integer> it = SHAPES.keySet().iterator();
            it.next();
            it.remove();
        }
        TelegraphShape s = new TelegraphShape(p, ticks);
        if (s.isFollow()) {
            var e = mc.level.getEntity(s.entityId);
            if (e != null) s.a = e.position();
        }
        s.ensureSampled(mc.level);
        SHAPES.put(s.id, s);
    }

    public static void cancel(BossPayloads.Cancel p) {
        TelegraphShape s = SHAPES.get(p.id());
        if (s == null || s.endedAt >= 0) return;
        s.endedAt = ticks + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        s.endFlash = p.flash();
    }

    public static void lock(BossPayloads.Lock p) {
        TelegraphShape s = SHAPES.get(p.id());
        if (s == null) return;
        s.a = new Vec3(p.x(), p.y(), p.z());
        s.locked = true;
    }

    static void clear() {
        SHAPES.clear();
        shapesLevel = null;
        texturesPresent = null;
    }

    // ------------------------------------------------------------------ eventos

    static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!SHAPES.isEmpty()) clear();
            return;
        }
        if (mc.level != shapesLevel && !SHAPES.isEmpty()) {
            SHAPES.clear();
            shapesLevel = mc.level;
        }
        if (mc.isPaused()) return;
        ticks++;
        double now = ticks;
        SHAPES.values().removeIf(s -> s.expired(now));
    }

    static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHAPES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != shapesLevel) return;
        float partial = mc.isPaused() ? 0 : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = ticks + partial;
        Vec3 cam = e.getCamera().getPosition();

        for (TelegraphShape s : SHAPES.values()) {
            if (s.isFollow()) {
                s.updateFollow(level, now, partial);
                s.ensureSampled(level);
            }
        }

        BufferBuilder colors = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (TelegraphShape s : SHAPES.values()) {
            if (visible(s, cam)) s.drawColor(colors, cam, now);
        }
        draw(colors, TelegraphRenderTypes.COLOR);

        if (!texturesPresent(mc)) return;
        BufferBuilder runes = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (TelegraphShape s : SHAPES.values()) {
            if (visible(s, cam)) s.drawRunes(runes, cam, now);
        }
        draw(runes, TelegraphRenderTypes.RUNES);

        BufferBuilder chevrons = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (TelegraphShape s : SHAPES.values()) {
            if (visible(s, cam)) s.drawChevrons(chevrons, cam, now);
        }
        draw(chevrons, TelegraphRenderTypes.CHEVRONS);
    }

    private static void draw(BufferBuilder buf, RenderType type) {
        MeshData mesh = buf.build();
        if (mesh != null) type.draw(mesh);
    }

    private static boolean visible(TelegraphShape s, Vec3 cam) {
        double r = DRAW_RANGE + s.extent();
        return s.middle().distanceToSqr(cam) <= r * r;
    }

    /** As texturas de runas/setas existem? (sem elas, desenha só as cores — nunca a textura "faltando"). */
    private static boolean texturesPresent(Minecraft mc) {
        if (texturesPresent == null) {
            var rm = mc.getResourceManager();
            texturesPresent = rm.getResource(TelegraphRenderTypes.RUNES_TEXTURE).isPresent()
                    && rm.getResource(TelegraphRenderTypes.CHEVRON_TEXTURE).isPresent();
        }
        return texturesPresent;
    }

    private TelegraphRenderer() {}
}
