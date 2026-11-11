package com.renanpontes.isekaimahou.client.status;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Textos flutuantes das reações ("ELETROCUTAR!") sobre o alvo: aparecem com um "pulo", sobem e somem em 1,2 s.
 * Seguem a entidade enquanto ela existir; vários textos no mesmo alvo empilham.
 */
final class ReactionTexts {
    static final long DURATION_MS = 1200;
    private static final int MAX = 32;

    private static final class Floater {
        final int entityId;
        final ElementReactions.Reaction reaction;
        final long start;
        final int slot;
        Vec3 pos;

        Floater(int entityId, ElementReactions.Reaction reaction, Vec3 pos, long start, int slot) {
            this.entityId = entityId;
            this.reaction = reaction;
            this.pos = pos;
            this.start = start;
            this.slot = slot;
        }
    }

    private static final List<Floater> ACTIVE = new ArrayList<>();

    static void add(int entityId, ElementReactions.Reaction r, double x, double y, double z) {
        if (!enabled()) return;
        long now = Util.getMillis();
        int slot = 0;
        for (Floater f : ACTIVE) if (f.entityId == entityId && now - f.start < DURATION_MS * 0.6) slot++;
        if (ACTIVE.size() >= MAX) ACTIVE.remove(0);
        ACTIVE.add(new Floater(entityId, r, new Vec3(x, y, z), now, Math.min(slot, 3)));
    }

    static boolean enabled() {
        try {
            return MahouConfig.SHOW_REACTION_TEXT.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    static void clear() {
        ACTIVE.clear();
    }

    static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    /** Desenha (chamado pelo StatusWorldRenderer dentro do lote dele). */
    static void render(PoseStack ps, MultiBufferSource buffers, Camera cam, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;
        long now = Util.getMillis();
        Vec3 camPos = cam.getPosition();
        Font font = mc.font;
        Iterator<Floater> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Floater f = it.next();
            long age = now - f.start;
            if (age >= DURATION_MS) {
                it.remove();
                continue;
            }
            Entity e = mc.level.getEntity(f.entityId);
            if (e != null && !e.isRemoved()) {
                Vec3 p = e.getPosition(partialTick);
                f.pos = new Vec3(p.x, p.y + e.getBbHeight(), p.z);
            }
            float t = age / (float) DURATION_MS;
            // Pulo inicial (cresce além do tamanho e volta), sobe devagar, some no último terço
            float pop = t < 0.12F ? Mth.lerp(t / 0.12F, 0.4F, 1.25F) : t < 0.22F ? Mth.lerp((t - 0.12F) / 0.1F, 1.25F, 1.0F) : 1.0F;
            float alpha = t > 0.66F ? 1.0F - (t - 0.66F) / 0.34F : 1.0F;
            double rise = 0.55 + t * 0.9 + f.slot * 0.38;
            Vec3 at = f.pos.add(0, rise, 0);
            double dist = at.distanceTo(camPos);
            float scale = 0.028F * pop * (1.0F + Mth.clamp((float) (dist - 8.0) / 24F, 0F, 1F) * 0.8F);

            Component text = f.reaction.displayName();
            FormattedCharSequence seq = text.getVisualOrderText();
            int w = font.width(seq);
            int a = Mth.clamp((int) (alpha * 255), 8, 255);
            int color = (a << 24) | f.reaction.color();
            int outline = (a << 24) | 0x101018;

            ps.pushPose();
            ps.translate(at.x - camPos.x, at.y - camPos.y, at.z - camPos.z);
            ps.mulPose(cam.rotation());
            ps.scale(scale, -scale, scale);
            Matrix4f m = ps.last().pose();
            font.drawInBatch8xOutline(seq, -w / 2F, -4F, color, outline, m, buffers, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
    }

    private ReactionTexts() {}
}
