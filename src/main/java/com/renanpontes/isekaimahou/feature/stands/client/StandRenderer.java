package com.renanpontes.isekaimahou.feature.stands.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.stands.Stand;
import com.renanpontes.isekaimahou.feature.stands.StandNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Vector3f;

/**
 * SÓ CLIENTE. Desenha os stands ativos: um corpo humanoide TRANSLÚCIDO na cor do stand, flutuando atrás do usuário
 * (ou indo para a frente dele nos golpes), com a pose da ação (rajada de socos, apontar, apertar o botão, apagar o
 * tempo, braços abertos...). Entra e sai em fade; nenhum HUD, nenhum texto.
 * <ul>
 *   <li>Desempenho: no máx. {@link #MAX_DRAWN} stands desenhados por quadro, só dentro do alcance do preset
 *       ({@link FxQuality#inRange}); o "rastro" de braços da rajada só em HIGH+; partículas do Tusk/King Crimson pelo
 *       {@link FxQuality#count}.</li>
 *   <li>Primeira pessoa: o seu próprio stand fica ao lado, mais baixo e mais transparente (não tapa a mira).</li>
 * </ul>
 */
public final class StandRenderer {
    public static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/stands/stand.png");
    private static final int MAX_DRAWN = 8;
    private static final int FADE_IN = 5, FADE_OUT = 6;

    private record Active(Stand stand, Stand.Action action, long start, int ticks) {}

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static StandModel model;
    private static long clientTicks;

    static void onShow(StandNet.Show p) {
        if (p.ticks() <= 0) {
            ACTIVE.remove(p.entity());
            return;
        }
        ACTIVE.put(p.entity(), new Active(Stand.byId(p.stand()), Stand.Action.byId(p.action()), clientTicks, p.ticks()));
    }

    static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ACTIVE.clear();
    }

    static void tick(ClientTickEvent.Post e) {
        clientTicks++;
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            if (level == null) ACTIVE.clear();
            return;
        }
        for (Iterator<Map.Entry<Integer, Active>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Active> en = it.next();
            Active a = en.getValue();
            Entity user = level.getEntity(en.getKey());
            if (user == null || !user.isAlive() || clientTicks - a.start() > a.ticks()) {
                it.remove();
                continue;
            }
            particles(level, user, a, clientTicks - a.start());
        }
    }

    /** Aura do Tusk (espiral de "unhas" rosa-douradas) e cacos vermelhos do King Crimson. Poucas, pelo preset. */
    private static void particles(ClientLevel level, Entity user, Active a, long age) {
        if (age % 3 != 0 || !FxQuality.inRange(user.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()))) return;
        int rgb = a.stand().color();
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F,
                (rgb & 255) / 255F), 0.8F);
        boolean tusk = a.stand().has(Stand.TUSK);
        if (!tusk && a.stand() != Stand.KING_CRIMSON && a.stand() != Stand.D4C) return;
        int n = FxQuality.count(tusk ? 3 : 2);
        for (int i = 0; i < n; i++) {
            double ang = age * 0.35 + i * (Mth.TWO_PI / n);
            double r = tusk ? 0.7 : 0.9;
            double y = user.getY() + 0.2 + ((age * 0.08 + i * 0.4) % 1.8);
            level.addParticle(dust, user.getX() + Math.cos(ang) * r, y, user.getZ() + Math.sin(ang) * r, 0, 0.01, 0);
        }
    }

    static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        if (model == null) model = new StandModel(mc.getEntityModels().bakeLayer(StandModel.LAYER));
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(TEXTURE);
        // como os outros desenhos de mundo do mod: pose nova (a câmera já está na matriz do RenderSystem)
        PoseStack ps = new PoseStack();
        int drawn = 0;
        for (Map.Entry<Integer, Active> en : ACTIVE.entrySet()) {
            if (drawn >= MAX_DRAWN) break;
            Entity ent = level.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity user)) continue;
            Vec3 pos = user.getPosition(pt);
            if (!FxQuality.inRange(pos.distanceToSqr(cam))) continue;
            boolean firstPerson = user == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            draw(ps, buffers, type, user, en.getValue(), pos, cam, pt, firstPerson);
            drawn++;
        }
        buffers.endBatch(type);
    }

    private static void draw(PoseStack ps, MultiBufferSource buffers, RenderType type, LivingEntity user, Active a, Vec3 pos, Vec3 cam,
                             float pt, boolean firstPerson) {
        float age = (clientTicks - a.start()) + pt;
        float left = a.ticks() - age;
        float alpha = Math.min(1F, age / FADE_IN) * Math.min(1F, Math.max(0F, left) / FADE_OUT);
        if (alpha <= 0.02F) return;
        float base = firstPerson ? 0.32F : 0.55F;
        if (a.action() == Stand.Action.ERASE) base *= 0.75F + 0.25F * Mth.sin(age * 0.9F);
        float bodyYaw = Mth.rotLerp(pt, user.yBodyRotO, user.yBodyRot);
        float lookYaw = Mth.rotLerp(pt, user.yHeadRotO, user.yHeadRot);
        float yaw = a.action().front() ? lookYaw : bodyYaw;
        double rad = Math.toRadians(yaw);
        Vec3 fwd = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        Vec3 right = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
        float out = a.action().front() ? Math.min(1F, age / 4F) : 0F;
        float scale = a.stand().scale();
        Vec3 off;
        if (firstPerson) {
            // 13/11 (teste): colado na câmera ele virava uma mancha roxa tapando a direita da tela. Em 1ª pessoa o stand
            // só aparece quando VAI À FRENTE golpear (a guarda fica atrás de você, onde não dá pra ver mesmo), mais longe.
            if (!a.action().front()) return;
            off = fwd.scale(1.1 + 0.6 * out).add(right.scale(0.35)).add(0, -0.25, 0);
        } else {
            // atrás e à direita; nos golpes desliza para a frente do usuário
            off = fwd.scale(Mth.lerp(out, -0.6F, 0.85F)).add(right.scale(Mth.lerp(out, 0.4F, 0.1F)))
                    .add(0, 0.2 + (scale < 0.8F ? 0.9 - scale : 0), 0);
        }
        float bob = Mth.sin(age * 0.15F) * 0.05F;

        StandModel m = model;
        m.setup(a.stand());
        pose(m, a.action(), age, Mth.lerp(pt, user.xRotO, user.getXRot()));

        ps.pushPose();
        ps.translate(pos.x - cam.x + off.x, pos.y - cam.y + off.y + bob, pos.z - cam.z + off.z);
        ps.mulPose(Axis.YP.rotationDegrees(180F - yaw));
        ps.scale(-scale, -scale, scale);
        ps.translate(0F, -1.501F, 0F);
        int color = ((int) (Mth.clamp(base * alpha, 0F, 1F) * 255) << 24) | a.stand().color();
        m.renderToBuffer(ps, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        // rajada: braços-fantasma extras (o "ORA ORA" de muitos punhos), só em HIGH+
        if (a.action() == Stand.Action.BARRAGE && !firstPerson && FxQuality.atLeast(Graphics.HIGH)) {
            int ghost = ((int) (Mth.clamp(base * alpha * 0.45F, 0F, 1F) * 255) << 24) | a.stand().color();
            for (int k = 1; k <= 2; k++) {
                barrageArms(m, age + k * 1.7F);
                m.rightArm.render(ps, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ghost);
                m.leftArm.render(ps, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ghost);
            }
        }
        ps.popPose();
    }

    /** Pose da ação. */
    private static void pose(StandModel m, Stand.Action action, float age, float pitch) {
        m.head.xRot = pitch * Mth.DEG_TO_RAD * 0.6F;
        // pernas soltas, flutuando
        m.rightLeg.xRot = 0.25F + Mth.sin(age * 0.1F) * 0.08F;
        m.leftLeg.xRot = 0.1F - Mth.sin(age * 0.1F) * 0.08F;
        m.rightLeg.zRot = 0.05F;
        m.leftLeg.zRot = -0.05F;
        switch (action) {
            case BARRAGE -> barrageArms(m, age);
            case POINT -> {
                m.rightArm.xRot = -Mth.HALF_PI + pitch * Mth.DEG_TO_RAD;
                m.rightArm.yRot = -0.1F;
                m.leftArm.xRot = -0.3F;
                m.leftArm.zRot = -0.2F;
            }
            case SWITCH -> {
                // o polegar no "botão": braço direito dobrado à frente do peito, esquerdo para trás
                m.rightArm.xRot = -1.35F;
                m.rightArm.yRot = -0.55F;
                m.rightArm.zRot = age > 8 ? 0.15F : 0.35F;
                m.leftArm.xRot = 0.35F;
                m.leftArm.zRot = -0.25F;
            }
            case ERASE -> {
                m.rightArm.zRot = 0.9F + Mth.sin(age * 0.2F) * 0.1F;
                m.leftArm.zRot = -0.9F - Mth.sin(age * 0.2F) * 0.1F;
                m.rightArm.xRot = -0.4F;
                m.leftArm.xRot = -0.4F;
                m.head.xRot = 0.2F;
            }
            case SPREAD -> {
                m.rightArm.zRot = 1.45F;
                m.leftArm.zRot = -1.45F;
                m.rightArm.xRot = Mth.sin(age * 0.08F) * 0.1F;
                m.leftArm.xRot = -Mth.sin(age * 0.08F) * 0.1F;
            }
            case SLAM -> {
                // carrega (puxa o braço) e solta um soco pesado no 6º tick
                float t = Mth.clamp((age - 6F) / 3F, 0F, 1F);
                m.rightArm.xRot = Mth.lerp(t, 0.6F, -Mth.HALF_PI);
                m.rightArm.yRot = Mth.lerp(t, 0.4F, 0F);
                m.leftArm.xRot = -0.6F;
                m.leftArm.zRot = -0.3F;
                m.body.yRot = Mth.lerp(t, 0.35F, -0.15F);
            }
            case GUARD -> {
                m.rightArm.xRot = -1.1F;
                m.rightArm.yRot = -0.75F;
                m.leftArm.xRot = -1.1F;
                m.leftArm.yRot = 0.75F;
            }
        }
    }

    /** Socos alternados bem rápidos (o braço vai e volta a cada ~2,4 ticks). */
    private static void barrageArms(StandModel m, float age) {
        float s = Mth.sin(age * 2.6F);
        m.rightArm.xRot = -Mth.HALF_PI + s * 0.45F;
        m.leftArm.xRot = -Mth.HALF_PI - s * 0.45F;
        m.rightArm.yRot = -0.15F + Mth.cos(age * 1.9F) * 0.2F;
        m.leftArm.yRot = 0.15F - Mth.cos(age * 2.1F) * 0.2F;
        m.rightArm.zRot = 0F;
        m.leftArm.zRot = 0F;
    }

    private StandRenderer() {}
}
