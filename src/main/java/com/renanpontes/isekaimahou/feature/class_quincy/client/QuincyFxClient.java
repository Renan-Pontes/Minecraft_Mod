package com.renanpontes.isekaimahou.feature.class_quincy.client;

import static com.renanpontes.isekaimahou.feature.class_quincy.client.QuincyRenderTypes.argb;
import static com.renanpontes.isekaimahou.feature.class_quincy.client.QuincyRenderTypes.billboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFxNet;
import com.renanpontes.isekaimahou.feature.class_quincy.item.QuincyCrossItem;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Efeitos de anime do Quincy no cliente (só visual):
 * <ul>
 *   <li><b>Arco de Reishi se formando</b>: enquanto um Quincy puxa a Cruz, faíscas azuis são sugadas do ar para a mão
 *   (mais rápidas quanto mais carregado) e um lampejo em anel quando a carga fica cheia;</li>
 *   <li><b>Vollständig</b>: um par de asas grandes de penas de luz e a auréola (Heiligenschein) girando;
 *   <b>Letzt Stil</b>: dois pares de asas douradas, a estrela Quincy girando nas costas e a auréola;
 *   <b>O Todo-Poderoso</b> (w10): SOMBRA — poça de tinta negra sob ele, aura de fumaça negra subindo pelo corpo e
 *   tentáculos de tinta se contorcendo em volta; os OLHOS de muitas pupilas se abrindo e piscando numa nuvem em volta e
 *   acima dele (mais olhos no preset alto), o brilho nos olhos do próprio jogador e, ao ativar, um respingo de tinta e
 *   um anel de olhos se abrindo — para quem ativou, um breve "olho" e bordas de tinta na tela;</li>
 *   <li>Heilig Bogen (o arco gigante + a estrela atrás), a estrela da Chuva de Luz no céu com o cone de luz, a troca
 *   tremeluzente da Antítese (com o lampejo invertido na tela) e a coluna de ascensão das formas finais.</li>
 * </ul>
 */
public final class QuincyFxClient {
    private static final int BLUE = Quincy.REISHI_RGB;
    private static final int GOLD = 0xFFE89A;
    private static final int MAX_SPARKS = 800;

    private record FormC(int kind, long start, long end) {}

    private static final class Fx {
        final QuincyFxNet.Burst p;
        final int life;
        int age;

        Fx(QuincyFxNet.Burst p, int life) {
            this.p = p;
            this.life = life;
        }
    }

    private static final class Spark {
        double x, y, z, vx, vy, vz;
        int rgb, age, life;
        float size;
    }

    private static final Map<Integer, FormC> FORMS = new HashMap<>();
    private static final List<Fx> FX = new ArrayList<>();
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final Map<Integer, Integer> DRAW = new HashMap<>();
    /** w10: respingos de TINTA (escuros, mistura normal) — Todo-Poderoso. */
    private static final List<Spark> INK = new ArrayList<>();
    private static final int MAX_INK = 300;
    /** w10: quem está no Todo-Poderoso e é desenhado neste quadro (e se é o próprio jogador em 1ª pessoa). */
    private static final List<LivingEntity> ALMIGHTY = new ArrayList<>();
    private static final ResourceLocation VIGNETTE = IsekaiMahou.id("textures/misc/boss_vignette.png");
    /** w10: a tela do Todo-Poderoso (para quem ativou): quando começou (tempo do jogo) — -1 = nada. */
    private static long almightyScreenStart = -1;
    private static final int ALMIGHTY_SCREEN_TICKS = 36;
    private static final RandomSource RND = RandomSource.create();
    private static ClientLevel level;

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    static void onForm(QuincyFxNet.Form p) {
        ClientLevel l = sync();
        if (l == null) return;
        long now = l.getGameTime();
        if (p.kind() == QuincyFxNet.FORM_NONE || p.ticks() <= 0) {
            FORMS.remove(p.entity());
            return;
        }
        FormC old = FORMS.get(p.entity());
        long start = old != null && old.kind() == p.kind() ? old.start() : now;
        FORMS.put(p.entity(), new FormC(p.kind(), start, now + p.ticks() + 5));
    }

    static void onBurst(QuincyFxNet.Burst p) {
        ClientLevel l = sync();
        if (l == null) return;
        int life = switch (p.kind()) {
            case QuincyFxNet.BOGEN -> 16;
            case QuincyFxNet.ZWINGER -> 24;
            case QuincyFxNet.STAR -> 50;
            case QuincyFxNet.ANTITHESIS -> 26;
            case QuincyFxNet.EYES -> 24;
            case QuincyFxNet.ASCEND -> 44;
            default -> 20;
        };
        if (FX.size() > 200) FX.remove(0);
        FX.add(new Fx(p, life));
        if (p.kind() == QuincyFxNet.EYES) almightyBurst(l, p);
        if (p.kind() == QuincyFxNet.BOGEN) {
            // estilhaços de luz saindo do arco
            for (int i = 0; i < 24 * QuincyRenderTypes.particleScale(); i++) {
                Vec3 v = p.vec().scale(0.4 + RND.nextDouble() * 0.6).add(RND.nextGaussian() * 0.12, RND.nextGaussian() * 0.12, RND.nextGaussian() * 0.12);
                spark(p.pos().x, p.pos().y, p.pos().z, v.x, v.y, v.z, i % 3 == 0 ? 0xFFFFFF : BLUE, 10 + RND.nextInt(8), 0.12F);
            }
        }
    }

    /**
     * w10 — O Todo-Poderoso abre os olhos: a tinta ESPIRRA para fora do corpo e corre pelo chão; para quem ativou,
     * começa o efeito curto de tela ({@link #overlay}).
     */
    private static void almightyBurst(ClientLevel l, QuincyFxNet.Burst p) {
        Entity e = l.getEntity(p.entity());
        Vec3 c = e != null ? e.position().add(0, e.getBbHeight() * 0.55, 0) : p.pos();
        Vec3 feet = e != null ? e.position() : p.pos().subtract(0, 1.5, 0);
        int n = inkCount(46);
        for (int i = 0; i < n; i++) {
            double th = RND.nextDouble() * Mth.TWO_PI, ph = Math.acos(RND.nextDouble() * 2 - 1);
            double sp = 0.12 + RND.nextDouble() * 0.22;
            ink(c.x, c.y, c.z, Math.sin(ph) * Math.cos(th) * sp, Math.cos(ph) * sp * 0.7 + 0.04, Math.sin(ph) * Math.sin(th) * sp,
                    14 + RND.nextInt(12), 0.22F + RND.nextFloat() * 0.25F);
        }
        int g = inkCount(28);
        for (int i = 0; i < g; i++) {
            double a = i * Mth.TWO_PI / g + RND.nextDouble() * 0.2;
            double sp = 0.16 + RND.nextDouble() * 0.1;
            ink(feet.x, feet.y + 0.08, feet.z, Math.cos(a) * sp, 0, Math.sin(a) * sp, 18 + RND.nextInt(8), 0.35F + RND.nextFloat() * 0.2F);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && p.entity() == mc.player.getId()) almightyScreenStart = l.getGameTime();
    }

    /** Quantidade de efeito escuro: preset do mod × configuração de partículas do jogo. */
    private static int inkCount(int base) {
        return Math.max(1, Math.round(FxQuality.count(base) * QuincyRenderTypes.particleScale()));
    }

    private static void ink(double x, double y, double z, double vx, double vy, double vz, int life, float size) {
        if (INK.size() >= MAX_INK) return;
        Spark s = new Spark();
        s.x = x;
        s.y = y;
        s.z = z;
        s.vx = vx;
        s.vy = vy;
        s.vz = vz;
        s.rgb = 0x050308;
        s.life = life;
        s.size = size;
        INK.add(s);
    }

    private static ClientLevel sync() {
        ClientLevel l = Minecraft.getInstance().level;
        if (l != level) {
            clear();
            level = l;
        }
        return l;
    }

    static void clear() {
        FORMS.clear();
        FX.clear();
        SPARKS.clear();
        DRAW.clear();
        INK.clear();
        ALMIGHTY.clear();
        almightyScreenStart = -1;
        level = null;
    }

    private static void spark(double x, double y, double z, double vx, double vy, double vz, int rgb, int life, float size) {
        if (SPARKS.size() >= MAX_SPARKS) return;
        Spark s = new Spark();
        s.x = x;
        s.y = y;
        s.z = z;
        s.vx = vx;
        s.vy = vy;
        s.vz = vz;
        s.rgb = rgb;
        s.life = life;
        s.size = size;
        SPARKS.add(s);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    static void tick(ClientTickEvent.Post e) {
        ClientLevel l = sync();
        Minecraft mc = Minecraft.getInstance();
        if (l == null || mc.isPaused()) return;
        long now = l.getGameTime();
        FORMS.entrySet().removeIf(en -> now > en.getValue().end());
        for (Fx f : FX) f.age++;
        FX.removeIf(f -> f.age >= f.life);
        Iterator<Spark> si = SPARKS.iterator();
        while (si.hasNext()) {
            Spark s = si.next();
            if (++s.age >= s.life) {
                si.remove();
                continue;
            }
            s.x += s.vx;
            s.y += s.vy;
            s.z += s.vz;
        }
        float scale = QuincyRenderTypes.particleScale();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        // o arco de Reishi se formando na mão de quem puxa a Cruz
        for (Player p : l.players()) {
            if (!p.isUsingItem() || !(p.getUseItem().getItem() instanceof QuincyCrossItem) || p.distanceToSqr(cam) > 40 * 40) continue;
            int used = p.getTicksUsingItem();
            Vec3 hand = hand(p, 1F);
            float charge = Mth.clamp(used / 20F, 0F, 1F);
            int n = Math.max(1, Math.round((1 + 3 * charge) * scale));
            for (int i = 0; i < n; i++) {
                double th = RND.nextDouble() * Mth.TWO_PI, ph = Math.acos(RND.nextDouble() * 2 - 1);
                double r = 1.0 + RND.nextDouble() * 0.6;
                double sx = hand.x + Math.sin(ph) * Math.cos(th) * r, sy = hand.y + Math.cos(ph) * r, sz = hand.z + Math.sin(ph) * Math.sin(th) * r;
                int life = 7 + RND.nextInt(3);
                spark(sx, sy, sz, (hand.x - sx) / life, (hand.y - sy) / life, (hand.z - sz) / life, RND.nextFloat() < 0.25F ? 0xFFFFFF : BLUE,
                        life, 0.07F + 0.05F * charge);
            }
            if (used == 20) {
                // carga cheia: anel de luz
                for (int i = 0; i < 24; i++) {
                    double a = i * Mth.TWO_PI / 24;
                    Vec3 look = p.getLookAngle();
                    Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
                    Vec3 up = side.cross(look).normalize();
                    Vec3 v = side.scale(Math.cos(a) * 0.12).add(up.scale(Math.sin(a) * 0.12));
                    spark(hand.x, hand.y, hand.z, v.x, v.y, v.z, 0xFFFFFF, 8, 0.1F);
                }
            }
        }
        Iterator<Spark> ii = INK.iterator();
        while (ii.hasNext()) {
            Spark s = ii.next();
            if (++s.age >= s.life) {
                ii.remove();
                continue;
            }
            s.x += s.vx;
            s.y += s.vy;
            s.z += s.vz;
            // a tinta freia e escorre
            s.vx *= 0.86;
            s.vz *= 0.86;
            s.vy = s.vy * 0.86 - 0.004;
        }
        // O Todo-Poderoso: fiapos de sombra subindo do corpo (quantidade pelo preset)
        for (Map.Entry<Integer, FormC> en : FORMS.entrySet()) {
            if (en.getValue().kind() != QuincyFxNet.FORM_ALMIGHTY) continue;
            Entity ent = l.getEntity(en.getKey());
            if (ent == null || ent.isInvisible() || !FxQuality.inRange(ent.distanceToSqr(cam))) continue;
            // em 1ª pessoa, a fumaça subindo do próprio corpo passaria na frente da câmera: não solta
            if (ent == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) continue;
            float chance = 0.9F * FxQuality.particles() * scale;
            while (chance > 0) {
                if (RND.nextFloat() < chance) {
                    double a = RND.nextDouble() * Mth.TWO_PI, r = 0.35 + RND.nextDouble() * 0.5;
                    ink(ent.getX() + Math.cos(a) * r, ent.getY() + RND.nextDouble() * ent.getBbHeight() * 0.9, ent.getZ() + Math.sin(a) * r,
                            0, 0.03 + RND.nextDouble() * 0.03, 0, 18 + RND.nextInt(10), 0.18F + RND.nextFloat() * 0.18F);
                }
                chance -= 1F;
            }
        }
        // a Letzt Stil solta faíscas douradas subindo
        for (Map.Entry<Integer, FormC> en : FORMS.entrySet()) {
            if (en.getValue().kind() != QuincyFxNet.FORM_LETZT || RND.nextFloat() > 0.6F * scale) continue;
            Entity ent = l.getEntity(en.getKey());
            if (ent == null) continue;
            spark(ent.getX() + RND.nextGaussian() * 0.6, ent.getY() + RND.nextDouble() * 2, ent.getZ() + RND.nextGaussian() * 0.6, 0, 0.05, 0,
                    RND.nextBoolean() ? GOLD : BLUE, 20, 0.08F);
        }
    }

    /** A mão que segura o arco (aproximada pela visão). */
    private static Vec3 hand(LivingEntity e, float pt) {
        Vec3 look = e.getViewVector(pt);
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        return e.getEyePosition(pt).add(look.scale(0.75)).add(right.scale(-0.1)).add(0, -0.3, 0);
    }

    // =====================================================================================
    //  Mundo
    // =====================================================================================
    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level) return;
        if (FORMS.isEmpty() && FX.isEmpty() && SPARKS.isEmpty() && INK.isEmpty()) return;
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = e.getPoseStack();
        Matrix4f m = ps.last().pose();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float time = (mc.level.getGameTime() % 100000L) + pt;
        Entity camEntity = mc.getCameraEntity();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        // quem está nas formas (e visível)
        DRAW.clear();
        for (Map.Entry<Integer, FormC> en : FORMS.entrySet()) {
            Entity ent = mc.level.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity) || ent.isInvisible() || ent.distanceToSqr(cam) > 96 * 96) continue;
            if (firstPerson && ent == camEntity) continue;
            DRAW.put(en.getKey(), en.getValue().kind());
        }

        if (!DRAW.isEmpty()) {
            RenderType wingT = QuincyRenderTypes.additive(QuincyRenderTypes.WING);
            VertexConsumer vc = buffers.getBuffer(wingT);
            for (Map.Entry<Integer, Integer> en : DRAW.entrySet()) {
                if (en.getValue() == QuincyFxNet.FORM_ALMIGHTY) continue;
                drawWings(vc, m, (LivingEntity) mc.level.getEntity(en.getKey()), FORMS.get(en.getKey()), cam, time, pt);
            }
            buffers.endBatch(wingT);

            RenderType haloT = QuincyRenderTypes.additive(QuincyRenderTypes.HALO);
            vc = buffers.getBuffer(haloT);
            for (Map.Entry<Integer, Integer> en : DRAW.entrySet()) {
                if (en.getValue() == QuincyFxNet.FORM_ALMIGHTY) continue;
                drawHalo(vc, m, (LivingEntity) mc.level.getEntity(en.getKey()), FORMS.get(en.getKey()), cam, time, pt);
            }
            buffers.endBatch(haloT);

        }

        // O Todo-Poderoso (w10): primeiro a SOMBRA (tinta, mistura normal), depois os OLHOS por cima
        ALMIGHTY.clear();
        for (Map.Entry<Integer, FormC> en : FORMS.entrySet()) {
            if (en.getValue().kind() != QuincyFxNet.FORM_ALMIGHTY) continue;
            Entity ent = mc.level.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity le) || ent.isInvisible() || !FxQuality.inRange(ent.distanceToSqr(cam))) continue;
            ALMIGHTY.add(le);
        }
        boolean eyeBurst = false;
        for (Fx fx : FX) eyeBurst |= fx.p.kind() == QuincyFxNet.EYES;
        if (!ALMIGHTY.isEmpty() || !INK.isEmpty()) {
            RenderType inkT = QuincyRenderTypes.translucent(QuincyRenderTypes.INK);
            VertexConsumer ivc = buffers.getBuffer(inkT);
            for (LivingEntity le : ALMIGHTY) {
                boolean self1p = firstPerson && le == camEntity;
                drawShadow(ivc, m, left, up, le, FORMS.get(le.getId()), cam, time, pt, self1p);
            }
            for (Spark s : INK) {
                double dx = s.x - cam.x, dy = s.y - cam.y, dz = s.z - cam.z;
                if (dx * dx + dy * dy + dz * dz < 1.0) continue; // colado na lente vira um borrão: não desenha
                float a = Mth.clamp((s.life - s.age - pt) / 6F, 0F, 1F) * Mth.clamp((s.age + pt) / 2F, 0F, 1F);
                float sz = s.size * (1F + 0.4F * (s.age + pt) / s.life);
                billboard(ivc, m, left, up, (float) (s.x + s.vx * pt - cam.x), (float) (s.y + s.vy * pt - cam.y), (float) (s.z + s.vz * pt - cam.z),
                        sz, sz, s.age * 0.1F, argb(s.rgb, 0.8F * a));
            }
            buffers.endBatch(inkT);
        }
        if (!ALMIGHTY.isEmpty() || eyeBurst) {
            RenderType eyeT = QuincyRenderTypes.translucent(QuincyRenderTypes.SHADOW_EYE);
            VertexConsumer vc = buffers.getBuffer(eyeT);
            for (LivingEntity le : ALMIGHTY) {
                if (firstPerson && le == camEntity) continue;
                drawEyes(vc, m, left, up, le, FORMS.get(le.getId()), cam, time, pt);
            }
            for (Fx fx : FX) if (fx.p.kind() == QuincyFxNet.EYES) drawEyeOpen(vc, m, left, up, fx, cam, pt);
            buffers.endBatch(eyeT);
        }

        // estrelas (Letzt Stil nas costas, céu da Chuva de Luz, ascensão, arco gigante)
        RenderType starT = QuincyRenderTypes.additive(QuincyRenderTypes.STAR);
        VertexConsumer svc = buffers.getBuffer(starT);
        for (Map.Entry<Integer, Integer> en : DRAW.entrySet()) {
            if (en.getValue() == QuincyFxNet.FORM_LETZT) drawBackStar(svc, m, (LivingEntity) mc.level.getEntity(en.getKey()), FORMS.get(en.getKey()), cam, time, pt);
        }
        for (Fx fx : FX) {
            switch (fx.p.kind()) {
                case QuincyFxNet.STAR, QuincyFxNet.ASCEND, QuincyFxNet.ZWINGER -> drawGroundStar(svc, m, fx, cam, time, pt);
                case QuincyFxNet.BOGEN -> drawBowStar(svc, m, fx, cam, time, pt);
                default -> {}
            }
        }
        buffers.endBatch(starT);

        // feixes (cone da Chuva de Luz, coluna da ascensão, a troca da Antítese, o arco gigante)
        RenderType beamT = QuincyRenderTypes.additive(QuincyRenderTypes.BEAM);
        VertexConsumer bvc = buffers.getBuffer(beamT);
        for (Fx fx : FX) {
            switch (fx.p.kind()) {
                case QuincyFxNet.STAR -> drawSkyCone(bvc, m, fx, cam, pt);
                case QuincyFxNet.ASCEND -> drawColumn(bvc, m, fx, cam, time, pt);
                default -> {}
            }
        }
        buffers.endBatch(beamT);

        RenderType streakT = QuincyRenderTypes.additive(QuincyRenderTypes.STREAK);
        VertexConsumer tvc = buffers.getBuffer(streakT);
        for (Fx fx : FX) {
            if (fx.p.kind() == QuincyFxNet.BOGEN) drawGiantBow(tvc, m, fx, cam, pt);
            else if (fx.p.kind() == QuincyFxNet.ANTITHESIS) drawAntithesis(tvc, m, fx, cam, time, pt);
        }
        buffers.endBatch(streakT);

        RenderType glowT = QuincyRenderTypes.additive(QuincyRenderTypes.GLOW);
        VertexConsumer gvc = buffers.getBuffer(glowT);
        for (Spark s : SPARKS) {
            float a = Mth.clamp((s.life - s.age - pt) / 4F, 0F, 1F);
            billboard(gvc, m, left, up, (float) (s.x + s.vx * pt - cam.x), (float) (s.y + s.vy * pt - cam.y), (float) (s.z + s.vz * pt - cam.z),
                    s.size, s.size, 0, argb(s.rgb, 0.9F * a));
        }
        for (Map.Entry<Integer, Integer> en : DRAW.entrySet()) {
            if (en.getValue() == QuincyFxNet.FORM_ALMIGHTY) {
                // w10: os DOIS olhos acendem (azul-branco), com um risco de luz horizontal em cada
                LivingEntity le = (LivingEntity) mc.level.getEntity(en.getKey());
                Vec3 look = le.getViewVector(pt);
                Vec3 right = new Vec3(-look.z, 0, look.x);
                right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
                float a = formAlpha(FORMS.get(en.getKey()), pt);
                float pulse = 0.16F + 0.04F * Mth.sin(time * 0.3F);
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 eye = le.getEyePosition(pt).add(look.scale(0.28)).add(right.scale(0.11 * side)).subtract(cam);
                    billboard(gvc, m, left, up, (float) eye.x, (float) eye.y, (float) eye.z, pulse, pulse * 0.7F, 0, argb(0x8FC0FF, 0.95F * a));
                    billboard(gvc, m, left, up, (float) eye.x, (float) eye.y, (float) eye.z, pulse * 2.6F, pulse * 0.18F, 0, argb(0xDDEBFF, 0.7F * a));
                }
            }
        }
        for (Fx fx : FX) if (fx.p.kind() == QuincyFxNet.ANTITHESIS) drawAntithesisGlow(gvc, m, left, up, fx, cam, pt);
        buffers.endBatch(glowT);

        RenderType ringT = QuincyRenderTypes.additive(QuincyRenderTypes.HALO);
        VertexConsumer rvc = buffers.getBuffer(ringT);
        for (Fx fx : FX) if (fx.p.kind() == QuincyFxNet.ANTITHESIS) drawAntithesisRings(rvc, m, fx, cam, time, pt);
        buffers.endBatch(ringT);
    }

    private static float formAlpha(FormC f, float pt) {
        float now = level.getGameTime() + pt;
        float in = Mth.clamp((now - f.start()) / 12F, 0F, 1F);
        float left = f.end() - now;
        float blink = left < 60 ? 0.6F + 0.4F * Mth.cos(now * 0.6F) : 1F;
        return in * Mth.clamp(left / 8F, 0F, 1F) * blink;
    }

    // ---------------- asas e auréola ----------------
    private static void drawWings(VertexConsumer vc, Matrix4f m, LivingEntity e, FormC f, Vec3 cam, float time, float pt) {
        float a = formAlpha(f, pt);
        if (a <= 0.01F) return;
        boolean letzt = f.kind() == QuincyFxNet.FORM_LETZT;
        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(bodyYaw), 0, Mth.cos(bodyYaw));
        Vec3 back = fwd.scale(-1);
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 upv = new Vec3(0, 1, 0);
        float sc = e.getBbHeight() / 1.8F;
        Vec3 anchor = e.getPosition(pt).add(0, e.getBbHeight() * 0.72, 0).add(back.scale(0.25 * sc)).subtract(cam);
        int pairs = letzt ? 2 : 1;
        for (int pair = 0; pair < pairs; pair++) {
            float flapSpeed = e.onGround() ? 0.07F : 0.18F;
            float flap = (float) Math.toRadians(22 + pair * 18 + 12 * Mth.sin(time * flapSpeed + pair * 0.8F));
            float w = (letzt ? 3.0F - pair * 0.6F : 2.4F) * sc, h = w;
            float lift = pair == 1 ? -0.45F * sc : 0F;
            int rgb = letzt ? (pair == 0 ? GOLD : 0xFFF6D8) : 0xD8F0FF;
            int col = argb(rgb, (letzt ? 0.8F : 0.7F) * a);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 dir = right.scale(s * Mth.cos(flap)).add(back.scale(Mth.sin(flap))).normalize();
                Vec3 root = anchor.add(right.scale(s * 0.12 * sc)).add(0, lift, 0);
                // raiz da asa na textura em (u 0,03, v 0,22) — v invertido: as penas abrem para CIMA e para fora
                Vec3 bl = root.subtract(dir.scale(0.03 * w)).subtract(upv.scale(0.22 * h));
                Vec3 tl = bl.add(upv.scale(h));
                Vec3 tr = tl.add(dir.scale(w));
                Vec3 br = bl.add(dir.scale(w));
                vc.addVertex(m, (float) bl.x, (float) bl.y, (float) bl.z).setUv(0, 0).setColor(col);
                vc.addVertex(m, (float) tl.x, (float) tl.y, (float) tl.z).setUv(0, 1).setColor(col);
                vc.addVertex(m, (float) tr.x, (float) tr.y, (float) tr.z).setUv(1, 1).setColor(col);
                vc.addVertex(m, (float) br.x, (float) br.y, (float) br.z).setUv(1, 0).setColor(col);
            }
        }
    }

    private static void drawHalo(VertexConsumer vc, Matrix4f m, LivingEntity e, FormC f, Vec3 cam, float time, float pt) {
        float a = formAlpha(f, pt);
        if (a <= 0.01F) return;
        boolean letzt = f.kind() == QuincyFxNet.FORM_LETZT;
        Vec3 c = e.getPosition(pt).add(0, e.getBbHeight() + (letzt ? 0.55 : 0.4) + 0.05 * Mth.sin(time * 0.1F), 0).subtract(cam);
        float r = letzt ? 0.75F : 0.55F;
        QuincyRenderTypes.flat(vc, m, (float) c.x, (float) c.y, (float) c.z, r, time * 0.04F, argb(letzt ? GOLD : 0xEAF6FF, 0.95F * a));
        QuincyRenderTypes.flat(vc, m, (float) c.x, (float) c.y + 0.01F, (float) c.z, r * 1.5F, -time * 0.025F, argb(BLUE, 0.35F * a));
    }

    private static void drawBackStar(VertexConsumer vc, Matrix4f m, LivingEntity e, FormC f, Vec3 cam, float time, float pt) {
        float a = formAlpha(f, pt);
        if (a <= 0.01F) return;
        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(bodyYaw), 0, Mth.cos(bodyYaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 c = e.getPosition(pt).add(0, e.getBbHeight() * 0.75, 0).subtract(fwd.scale(0.6)).subtract(cam);
        float r = 1.3F * e.getBbHeight() / 1.8F;
        float rot = time * 0.03F;
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        // quad no plano (direita, cima), girando no próprio plano
        Vec3 ax = right.scale(cs).add(0, sn, 0), ay = right.scale(-sn).add(0, cs, 0);
        int col = argb(GOLD, 0.75F * a);
        vc.addVertex(m, (float) (c.x - ax.x - ay.x), (float) (c.y - ax.y - ay.y), (float) (c.z - ax.z - ay.z)).setUv(0, 1).setColor(col);
        vc.addVertex(m, (float) (c.x - ax.x + ay.x), (float) (c.y - ax.y + ay.y), (float) (c.z - ax.z + ay.z)).setUv(0, 0).setColor(col);
        vc.addVertex(m, (float) (c.x + ax.x + ay.x), (float) (c.y + ax.y + ay.y), (float) (c.z + ax.z + ay.z)).setUv(1, 0).setColor(col);
        vc.addVertex(m, (float) (c.x + ax.x - ay.x), (float) (c.y + ax.y - ay.y), (float) (c.z + ax.z - ay.z)).setUv(1, 1).setColor(col);
    }

    // ---------------- O Todo-Poderoso (w10) ----------------
    /**
     * A SOMBRA do Todo-Poderoso (tinta negra, mistura normal): poça larga sob os pés que respira e gira devagar,
     * borrões de sombra subindo pelo corpo e tentáculos de tinta saindo da poça e se contorcendo em volta.
     * Em 1ª pessoa o próprio jogador vê só a poça e os tentáculos (nada colado na câmera).
     */
    private static void drawShadow(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, LivingEntity e, FormC f, Vec3 cam, float time,
                                   float pt, boolean self1p) {
        if (f == null) return;
        float a = formAlpha(f, pt);
        if (a <= 0.01F) return;
        float sc = e.getBbHeight() / 1.8F;
        Vec3 feet = e.getPosition(pt);
        Vec3 g = feet.subtract(cam);
        // poça (duas camadas girando ao contrário)
        float breathe = 1F + 0.06F * Mth.sin(time * 0.09F);
        QuincyRenderTypes.flat(vc, m, (float) g.x, (float) g.y + 0.03F, (float) g.z, 1.9F * sc * breathe, time * 0.006F, argb(0x050308, 0.9F * a));
        QuincyRenderTypes.flat(vc, m, (float) g.x, (float) g.y + 0.025F, (float) g.z, 3.1F * sc * breathe, -time * 0.004F + 1.3F, argb(0x0A0612, 0.45F * a));
        // tentáculos de tinta
        int tendrils = FxQuality.atLeast(MahouConfig.Graphics.HIGH) ? 7 : FxQuality.atLeast(MahouConfig.Graphics.MEDIUM) ? 5 : 3;
        int segs = 9;
        for (int k = 0; k < tendrils; k++) {
            float base = k * Mth.TWO_PI / tendrils + time * 0.008F + k * 0.7F;
            float reach = (1.0F + 0.25F * Mth.sin(time * 0.05F + k * 2.1F)) * sc;
            Vec3 prev = null;
            float prevW = 0;
            for (int j = 0; j <= segs; j++) {
                float u = j / (float) segs;
                float ang = base + 0.55F * Mth.sin(time * 0.07F + k * 1.3F + u * 3.2F) * u;
                float rad = (1.35F - 0.65F * u) * sc + 0.25F * Mth.sin(time * 0.11F + k + u * 4F) * u;
                // em 1ª pessoa os tentáculos ficam baixos (não passam na frente da câmera)
                float h = u * reach * (self1p ? 0.9F : 2.1F);
                Vec3 pnt = g.add(Mth.cos(ang) * rad, 0.04 + h, Mth.sin(ang) * rad);
                float w = (0.2F - 0.17F * u) * sc;
                if (prev != null) inkRibbon(vc, m, prev, pnt, prevW, w, argb(0x040208, 0.85F * a * (1F - 0.5F * u)));
                prev = pnt;
                prevW = w;
            }
        }
        if (self1p) return;
        // aura: borrões de sombra subindo e girando pelo corpo
        int blobs = Math.max(4, Math.round(12 * FxQuality.particles()));
        for (int k = 0; k < blobs; k++) {
            float ph = (k * 0.618F + time * 0.012F) % 1F;
            float ang = k * 2.39996F + time * 0.03F;
            float r = (0.45F + 0.2F * Mth.sin(time * 0.05F + k)) * sc;
            float y = ph * e.getBbHeight() * 1.15F;
            float fade = Mth.sin(ph * Mth.PI);
            float size = (0.55F + 0.35F * fade) * sc;
            Vec3 c = g.add(Mth.cos(ang) * r, y, Mth.sin(ang) * r);
            billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, size, size, ang, argb(0x07040C, 0.5F * a * fade));
        }
    }

    /** Fita de tinta de {@code a} até {@code b} (posições relativas à câmera), larguras diferentes nas pontas. */
    private static void inkRibbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float wa, float wb, int col) {
        Vec3 d = b.subtract(a);
        Vec3 mid = a.add(b).scale(-0.5);
        Vec3 side = d.cross(mid);
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize();
        vc.addVertex(m, (float) (a.x - side.x * wa), (float) (a.y - side.y * wa), (float) (a.z - side.z * wa)).setUv(0, 0.5F).setColor(col);
        vc.addVertex(m, (float) (a.x + side.x * wa), (float) (a.y + side.y * wa), (float) (a.z + side.z * wa)).setUv(1, 0.5F).setColor(col);
        vc.addVertex(m, (float) (b.x + side.x * wb), (float) (b.y + side.y * wb), (float) (b.z + side.z * wb)).setUv(1, 0.5F).setColor(col);
        vc.addVertex(m, (float) (b.x - side.x * wb), (float) (b.y - side.y * wb), (float) (b.z - side.z * wb)).setUv(0, 0.5F).setColor(col);
    }

    /**
     * Os OLHOS do Todo-Poderoso: uma nuvem de olhos de muitas pupilas em volta e ACIMA dele (6 / 9 / 14 pelo preset),
     * cada um se abrindo em sequência quando a forma começa e depois piscando no seu próprio ritmo, todos encarando a
     * câmera. Tamanhos e alturas variam (deterministicamente, por índice) para não parecer um anel.
     */
    private static void drawEyes(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, LivingEntity e, FormC f, Vec3 cam, float time,
                                 float pt) {
        if (f == null) return;
        float a = formAlpha(f, pt);
        if (a <= 0.01F) return;
        float sc = e.getBbHeight() / 1.8F;
        int count = FxQuality.atLeast(MahouConfig.Graphics.HIGH) ? 14 : FxQuality.atLeast(MahouConfig.Graphics.MEDIUM) ? 9 : 6;
        float since = level.getGameTime() + pt - f.start();
        Vec3 c = e.getPosition(pt);
        for (int k = 0; k < count; k++) {
            float fr = (k * 0.618034F) % 1F, fr2 = (k * 0.381966F + 0.13F) % 1F;
            float ang = k * 2.39996F + time * 0.006F;
            float r = (1.15F + 0.9F * fr2) * sc;
            float y = e.getBbHeight() * (0.5F + 0.75F * fr) + 0.12F * Mth.sin(time * 0.05F + k);
            Vec3 p = c.add(Mth.cos(ang) * r, y, Mth.sin(ang) * r).subtract(cam);
            // abre em sequência (2 ticks entre um olho e outro) e depois pisca
            float open = Mth.clamp((since - k * 2F) / 5F, 0F, 1F);
            float cyc = (time + k * 37F) % 140F;
            float blink = cyc < 8F ? Math.abs(cyc - 4F) / 4F : 1F;
            float lid = open * Math.max(0.06F, blink);
            if (lid <= 0.05F) continue;
            float size = (0.26F + 0.22F * fr2) * sc;
            billboard(vc, m, left, up, (float) p.x, (float) p.y, (float) p.z, size, size * lid, 0, argb(0xFFFFFF, 0.95F * a * open));
        }
    }

    /** Ao ativar: um olho GRANDE se abre acima da cabeça e um anel de olhos estala em volta, um depois do outro. */
    private static void drawEyeOpen(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx fx, Vec3 cam, float pt) {
        Entity e = level.getEntity(fx.p.entity());
        Minecraft mc = Minecraft.getInstance();
        if (e == null || e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) return;
        float t = fx.age + pt;
        float open = Mth.clamp(t / 6F, 0F, 1F);
        float a = 1F - Mth.clamp((t - 12) / 12F, 0F, 1F);
        Vec3 c = e.getEyePosition(pt).add(0, 1.1, 0).subtract(cam);
        float s = fx.p.size() * 0.55F * (0.8F + 0.2F * open);
        billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, s, s * open, 0, argb(0xFFFFFF, 0.95F * a));
        int ring = 8;
        Vec3 mid = e.getPosition(pt).add(0, e.getBbHeight() * 0.6, 0);
        for (int k = 0; k < ring; k++) {
            float o = Mth.clamp((t - k * 1.2F) / 3F, 0F, 1F);
            if (o <= 0.01F) continue;
            double ang = k * Mth.TWO_PI / ring;
            double r = 1.6 + 0.6 * o;
            Vec3 p = mid.add(Math.cos(ang) * r, 0.25 * Math.sin(ang * 3), Math.sin(ang) * r).subtract(cam);
            billboard(vc, m, left, up, (float) p.x, (float) p.y, (float) p.z, 0.4F, 0.4F * o, 0, argb(0xFFFFFF, 0.9F * a));
        }
    }

    // ---------------- estrelas, colunas e o arco gigante ----------------
    private static void drawGroundStar(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float time, float pt) {
        float t = fx.age + pt;
        float k = Mth.clamp(t / fx.life, 0F, 1F);
        float open = Mth.clamp(t / 8F, 0F, 1F);
        float a = Math.min(open, Mth.clamp((fx.life - t) / 12F, 0F, 1F));
        Vec3 c = fx.p.pos().subtract(cam);
        float r = fx.p.size() * (0.4F + 0.6F * (1F - (1F - open) * (1F - open)));
        int rgb = fx.p.kind() == QuincyFxNet.ASCEND ? fx.p.rgb() : 0xDDF2FF;
        QuincyRenderTypes.flat(vc, m, (float) c.x, (float) c.y + 0.06F, (float) c.z, r, time * 0.02F, argb(rgb, 0.95F * a));
        if (fx.p.kind() == QuincyFxNet.ZWINGER || fx.p.kind() == QuincyFxNet.ASCEND) {
            QuincyRenderTypes.flat(vc, m, (float) c.x, (float) c.y + 0.08F, (float) c.z, r * (1F + k), -time * 0.02F, argb(BLUE, 0.4F * (1 - k)));
        }
    }

    private static void drawBowStar(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float time, float pt) {
        float t = fx.age + pt;
        float a = 1F - Mth.clamp(t / fx.life, 0F, 1F);
        Vec3 look = fx.p.vec().normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 upv = side.cross(look).normalize();
        Vec3 c = fx.p.pos().subtract(look.scale(0.9)).subtract(cam);
        float r = 1.6F + 0.6F * (1 - a);
        float rot = time * 0.08F;
        Vec3 ax = side.scale(Mth.cos(rot) * r).add(upv.scale(Mth.sin(rot) * r));
        Vec3 ay = side.scale(-Mth.sin(rot) * r).add(upv.scale(Mth.cos(rot) * r));
        int col = argb(0xCFEAFF, 0.85F * a);
        vc.addVertex(m, (float) (c.x - ax.x - ay.x), (float) (c.y - ax.y - ay.y), (float) (c.z - ax.z - ay.z)).setUv(0, 1).setColor(col);
        vc.addVertex(m, (float) (c.x - ax.x + ay.x), (float) (c.y - ax.y + ay.y), (float) (c.z - ax.z + ay.z)).setUv(0, 0).setColor(col);
        vc.addVertex(m, (float) (c.x + ax.x + ay.x), (float) (c.y + ax.y + ay.y), (float) (c.z + ax.z + ay.z)).setUv(1, 0).setColor(col);
        vc.addVertex(m, (float) (c.x + ax.x - ay.x), (float) (c.y + ax.y - ay.y), (float) (c.z + ax.z - ay.z)).setUv(1, 1).setColor(col);
    }

    /** O arco gigante de luz: dois braços curvos e a corda puxada, feitos de rastros de luz. */
    private static void drawGiantBow(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float pt) {
        float t = fx.age + pt;
        float form = Mth.clamp(t / 3F, 0F, 1F);
        float a = 1F - Mth.clamp((t - 5) / (fx.life - 5F), 0F, 1F);
        if (a <= 0.01F) return;
        Vec3 look = fx.p.vec().normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 upv = side.cross(look).normalize();
        Vec3 c = fx.p.pos().subtract(cam);
        float H = fx.p.size() * form;
        int seg = 14;
        int col = argb(0xDFF4FF, 0.95F * a);
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double tt = -1 + 2.0 * i / seg;
            Vec3 p = c.add(upv.scale(tt * H)).add(look.scale((1 - tt * tt) * 0.7 * form));
            if (prev != null) ribbon(vc, m, prev, p, side.scale(0.09), col);
            prev = p;
        }
        Vec3 top = c.add(upv.scale(H)), bottom = c.add(upv.scale(-H));
        Vec3 nock = c.subtract(look.scale(0.6 * form));
        int str = argb(0xFFFFFF, 0.8F * a);
        ribbon(vc, m, top, nock, side.scale(0.03), str);
        ribbon(vc, m, bottom, nock, side.scale(0.03), str);
    }

    /** Fita de {@code a} até {@code b} (textura do rastro: cauda em a, cabeça em b). */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 half, int col) {
        vc.addVertex(m, (float) (a.x - half.x), (float) (a.y - half.y), (float) (a.z - half.z)).setUv(0.3F, 0).setColor(col);
        vc.addVertex(m, (float) (b.x - half.x), (float) (b.y - half.y), (float) (b.z - half.z)).setUv(1, 0).setColor(col);
        vc.addVertex(m, (float) (b.x + half.x), (float) (b.y + half.y), (float) (b.z + half.z)).setUv(1, 1).setColor(col);
        vc.addVertex(m, (float) (a.x + half.x), (float) (a.y + half.y), (float) (a.z + half.z)).setUv(0.3F, 1).setColor(col);
    }

    /** Cone de luz caindo da estrela da Chuva de Luz até o chão. */
    private static void drawSkyCone(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float pt) {
        float t = fx.age + pt;
        float a = Math.min(Mth.clamp(t / 8F, 0F, 1F), Mth.clamp((fx.life - t) / 12F, 0F, 1F)) * 0.35F;
        Vec3 c = fx.p.pos().subtract(cam);
        float r = fx.p.size();
        float H = 14F;
        for (int i = 0; i < 4; i++) {
            float ang = i * Mth.PI / 4;
            float dx = Mth.cos(ang), dz = Mth.sin(ang);
            float x = (float) c.x, y = (float) c.y, z = (float) c.z;
            QuincyRenderTypes.quad(vc, m, x - dx * r, y - H, z - dz * r, x + dx * r, y - H, z + dz * r, x + dx * r * 0.8F, y, z + dz * r * 0.8F,
                    x - dx * r * 0.8F, y, z - dz * r * 0.8F, argb(BLUE, a));
        }
    }

    /** Coluna de luz da ascensão (Letzt Stil / Vollständig). */
    private static void drawColumn(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float time, float pt) {
        float t = fx.age + pt;
        float grow = Mth.clamp(t / 6F, 0F, 1F);
        float a = Mth.clamp((fx.life - t) / 20F, 0F, 1F);
        Entity e = level.getEntity(fx.p.entity());
        Vec3 c = (e != null ? e.getPosition(pt) : fx.p.pos()).subtract(cam);
        float H = 30F * grow, R = 1.2F + 0.4F * Mth.sin(time * 0.4F);
        for (int i = 0; i < 4; i++) {
            float ang = i * Mth.PI / 4;
            float dx = Mth.cos(ang) * R, dz = Mth.sin(ang) * R;
            float x = (float) c.x, y = (float) c.y, z = (float) c.z;
            // a textura do pilar some no topo (v 0) e é forte na base (v 1)
            QuincyRenderTypes.quad(vc, m, x - dx, y, z - dz, x + dx, y, z + dz, x + dx, y + H, z + dz, x - dx, y + H, z - dz, argb(fx.p.rgb(), 0.85F * a));
        }
    }

    // ---------------- Antítese ----------------
    private static void drawAntithesis(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float time, float pt) {
        float t = fx.age + pt;
        float a = 1F - Mth.clamp(t / fx.life, 0F, 1F);
        Vec3 pa = fx.p.pos(), pb = fx.p.vec();
        Vec3 d = pb.subtract(pa);
        if (d.lengthSqr() < 1.0E-3) return;
        Vec3 dir = d.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 upv = side.cross(dir).normalize();
        // duas espirais (luz e sombra-azulada) trocando de lado, deslizando ao longo do tempo
        int seg = 24;
        for (int s = 0; s < 2; s++) {
            Vec3 prev = null;
            for (int i = 0; i <= seg; i++) {
                double u = i / (double) seg;
                double ang = u * Math.PI * 4 + time * 0.35 * (s == 0 ? 1 : -1) + s * Math.PI;
                Vec3 p = pa.add(d.scale(u)).add(side.scale(Math.cos(ang) * 0.5)).add(upv.scale(Math.sin(ang) * 0.5)).subtract(cam);
                if (prev != null) ribbon(vc, m, prev, p, upv.scale(0.06), argb(s == 0 ? 0xFFFFFF : 0x6A7AFF, 0.85F * a));
                prev = p;
            }
        }
    }

    private static void drawAntithesisGlow(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, Fx fx, Vec3 cam, float pt) {
        float t = fx.age + pt;
        float a = 1F - Mth.clamp(t / fx.life, 0F, 1F);
        for (Vec3 p : new Vec3[]{fx.p.pos(), fx.p.vec()}) {
            Vec3 c = p.subtract(cam);
            float r = 0.8F + 0.4F * Mth.sin(t * 0.8F);
            billboard(vc, m, left, up, (float) c.x, (float) c.y, (float) c.z, r, r, 0, argb(0xD8E4FF, 0.7F * a));
        }
    }

    private static void drawAntithesisRings(VertexConsumer vc, Matrix4f m, Fx fx, Vec3 cam, float time, float pt) {
        float t = fx.age + pt;
        float a = 1F - Mth.clamp(t / fx.life, 0F, 1F);
        int i = 0;
        for (Vec3 p : new Vec3[]{fx.p.pos(), fx.p.vec()}) {
            Vec3 c = p.subtract(cam);
            float spin = time * 0.15F * (i++ == 0 ? 1 : -1);
            QuincyRenderTypes.flat(vc, m, (float) c.x, (float) c.y, (float) c.z, 1.1F, spin, argb(i == 1 ? 0xFFFFFF : 0x6A7AFF, 0.8F * a));
        }
    }

    // =====================================================================================
    //  Tela
    // =====================================================================================
    static void overlay(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.level != level) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        FormC mine = FORMS.get(mc.player.getId());
        if (mine != null && mine.kind() == QuincyFxNet.FORM_ALMIGHTY) {
            // w10: bordas de tinta (mais escuras que antes), pulsando devagar enquanto dura
            float a = formAlpha(mine, pt) * (0.7F + 0.3F * Mth.sin((mc.level.getGameTime() + pt) * 0.08F));
            int edge = ((int) (85 * a) << 24) | 0x05030A;
            g.fillGradient(0, 0, w, h / 5, edge, 0x0005030A);
            g.fillGradient(0, h - h / 5, w, h, 0x0005030A, edge);
        }
        almightyScreen(g, mc, w, h, pt);
        for (Fx fx : FX) {
            if (fx.p.kind() != QuincyFxNet.ANTITHESIS) continue;
            boolean involved = fx.p.entity() == mc.player.getId() || mc.player.getEyePosition().distanceToSqr(fx.p.vec()) < 4;
            if (!involved) continue;
            float t = fx.age + pt;
            // lampejo "invertido": claro e logo escuro, como a realidade se desfazendo
            if (t < 4) g.fill(0, 0, w, h, ((int) (150 * (1 - t / 4F)) << 24) | 0xE8F0FF);
            else if (t < 12) g.fill(0, 0, w, h, ((int) (110 * (1 - (t - 4) / 8F)) << 24) | 0x0A0A20);
        }
    }

    /**
     * w10 — a tela de quem ATIVOU o Todo-Poderoso (1,8 s, sem poluir): a tinta fecha pelas bordas e volta, e um olho
     * grande e translúcido se abre no meio da visão e some. Respeita o tremor/flash do preset ({@link FxQuality#shake}).
     */
    private static void almightyScreen(GuiGraphics g, Minecraft mc, int w, int h, float pt) {
        if (almightyScreenStart < 0) return;
        float t = mc.level.getGameTime() + pt - almightyScreenStart;
        if (t < 0 || t > ALMIGHTY_SCREEN_TICKS) {
            if (t > ALMIGHTY_SCREEN_TICKS) almightyScreenStart = -1;
            return;
        }
        float k = FxQuality.shake();
        float in = Mth.clamp(t / 6F, 0F, 1F), out = Mth.clamp((ALMIGHTY_SCREEN_TICKS - t) / 14F, 0F, 1F);
        float a = Math.min(in, out) * k;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // bordas de tinta
        RenderSystem.setShaderColor(0.02F, 0.01F, 0.04F, 0.85F * a);
        g.blit(VIGNETTE, 0, 0, 0, 0, w, h, w, h);
        // o olho no meio: abre (pálpebra) e some
        float open = Mth.clamp((t - 3F) / 6F, 0F, 1F);
        int size = (int) (Math.min(w, h) * 0.42F);
        int eh = Math.max(1, (int) (size * open));
        RenderSystem.setShaderColor(1F, 1F, 1F, 0.32F * a);
        g.blit(QuincyRenderTypes.SHADOW_EYE, (w - size) / 2, (h - eh) / 2, size, eh, 0, 0, 64, 64, 64, 64);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    private QuincyFxClient() {}
}
