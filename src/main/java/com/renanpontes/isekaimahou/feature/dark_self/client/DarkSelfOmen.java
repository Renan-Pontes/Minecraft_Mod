package com.renanpontes.isekaimahou.feature.dark_self.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.dark_self.DarkSelfService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * O PRESSÁGIO em primeira pessoa ("sua sombra vai sumir"): em primeira pessoa o jogo nunca desenha a sombra do próprio
 * jogador, então o dono veria nada. Aqui desenhamos, só para ele e só em primeira pessoa:
 * <ol>
 *   <li><b>Presa</b>: uma silhueta humanoide escura e translúcida no chão, saindo dos pés, esticada para longe do sol
 *       (de noite, da lua; luz a pino ou Nether/End: curta, para a frente) — {@link DarkSelfService#shadowVector};</li>
 *   <li><b>Solta</b> (pacote do servidor com o ponto): ela desliza dos pés até o ponto, ondulando, virando para lá;</li>
 *   <li><b>Afunda</b>: encolhe e some no chão com ondas (anéis) — é ali que a Sombra surge logo depois.</li>
 * </ol>
 * Leve: 1 quadrado da silhueta + até 3 anéis por quadro, sem varrer o mundo (altura = chão do jogador / ponto do
 * servidor). Preset LOW: 1 anel e sem partículas; MEDIUM: 2 anéis; HIGH+: 3 anéis.
 */
final class DarkSelfOmen {
    private static final ResourceLocation SILHOUETTE = IsekaiMahou.id("textures/entity/dark_self_silhouette.png");
    private static final ResourceLocation RIPPLE = IsekaiMahou.id("textures/entity/dark_self_ripple.png");
    private static final RenderType SILHOUETTE_TYPE = decal("isekai_mahou_dark_self_silhouette", SILHOUETTE);
    private static final RenderType RIPPLE_TYPE = decal("isekai_mahou_dark_self_ripple", RIPPLE);

    private static final int FADE_IN = 12, SLIDE = 25, SINK = 30;
    /** Sem o pacote de "soltar" por tanto tempo, desiste (segurança). */
    private static final int MAX_ATTACHED = 400;
    private static final float ALPHA = 0.62F;
    private static final float WIDTH = 0.95F;

    private static boolean active;
    private static int age;
    private static int detachAge = -1;
    private static Vec3 spot = Vec3.ZERO;
    private static Vec3 detachFrom = Vec3.ZERO;
    private static Vec3 detachAxis = new Vec3(0, 0, 1);
    private static double groundY = Double.NaN;

    private DarkSelfOmen() {}

    /** Mesmo estilo das telegrafias do núcleo: translúcido, sem escrever profundidade, polygon offset contra z-fight. */
    private static RenderType decal(String name, ResourceLocation texture) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1024, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                        .setOutputState(RenderStateShard.PARTICLES_TARGET)
                        .createCompositeState(false));
    }

    // =====================================================================================
    //  Estado (pacotes)
    // =====================================================================================
    static void start() {
        active = true;
        age = 0;
        detachAge = -1;
        Player p = Minecraft.getInstance().player;
        groundY = p != null ? p.getY() : Double.NaN;
    }

    static void detach(Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!active) start();
        spot = to;
        detachAge = age;
        detachFrom = new Vec3(mc.player.getX(), groundY(mc.player), mc.player.getZ());
        detachAxis = attachedAxis(mc.player, 1F);
    }

    static void stop() {
        active = false;
    }

    static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }
        if (mc.isPaused()) return;
        age++;
        if (mc.player.onGround()) groundY = mc.player.getY();
        if (detachAge < 0) {
            if (age > MAX_ATTACHED) stop();
            return;
        }
        int sinkAge = age - detachAge - SLIDE;
        if (sinkAge > SINK) {
            stop();
            return;
        }
        // Tinta subindo do ponto enquanto ela afunda (nada no preset LOW).
        if (sinkAge >= 0 && age % 2 == 0 && FxQuality.atLeast(Graphics.MEDIUM)) {
            for (int i = 0; i < FxQuality.count(2); i++) {
                mc.level.addParticle(ParticleTypes.SQUID_INK, spot.x + (mc.level.random.nextDouble() - 0.5) * 1.2, spot.y + 0.05,
                        spot.z + (mc.level.random.nextDouble() - 0.5) * 1.2, 0, 0.02, 0);
            }
        }
    }

    private static double groundY(Player p) {
        return Double.isNaN(groundY) ? p.getY() : groundY;
    }

    /** Eixo da silhueta presa: para longe da luz; luz a pino/sem céu → para a frente (para o jogador enxergar). */
    private static Vec3 attachedAxis(Player p, float partialTick) {
        Vec3 sv = DarkSelfService.shadowVector(p.level(), partialTick);
        if (sv.lengthSqr() > 0.05 * 0.05) return sv.normalize();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        return look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
    }

    /** Comprimento da silhueta presa (1,8 bloco × tangente da luz, entre 1 e ~4 blocos). */
    private static double attachedLength(Player p, float partialTick) {
        return 1.8 * Mth.clamp(DarkSelfService.shadowVector(p.level(), partialTick).length(), 0.55, 2.2);
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    static void render(RenderLevelStageEvent e) {
        if (!active || e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        // Em terceira pessoa a sombra de verdade (escondida pelo DarkSelfClient) já mostra o efeito.
        if (p == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) return;
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = age + pt;
        Vec3 cam = e.getCamera().getPosition();
        Vec3 feet = new Vec3(Mth.lerp(pt, p.xo, p.getX()), groundY(p), Mth.lerp(pt, p.zo, p.getZ()));

        Vec3 base;
        Vec3 axis;
        double len = attachedLength(p, pt);
        double width = WIDTH;
        float alpha = ALPHA;
        double sinkU = -1;
        if (detachAge < 0 || t < detachAge) {
            // 1) Presa aos pés, respirando de leve.
            base = feet;
            axis = attachedAxis(p, pt);
            alpha *= (float) Math.min(1.0, t / FADE_IN);
            width *= 1.0 + 0.04 * Math.sin(t * 0.3);
        } else {
            double since = t - detachAge;
            Vec3 toSpot = spot.subtract(detachFrom).multiply(1, 0, 1);
            Vec3 slideDir = toSpot.lengthSqr() < 1.0E-4 ? detachAxis : toSpot.normalize();
            if (since < SLIDE) {
                // 2) Solta: desliza até o ponto, virando para lá e ondulando.
                double s = since / SLIDE;
                double ease = s * s * (3 - 2 * s);
                base = detachFrom.lerp(spot, ease);
                axis = detachAxis.lerp(slideDir, Math.min(1.0, s * 1.6));
                axis = axis.lengthSqr() < 1.0E-4 ? slideDir : axis.normalize();
                width *= 1.0 + 0.16 * Math.sin(t * 1.2);
                len *= 1.0 + 0.08 * Math.sin(t * 0.9 + 1.0);
            } else {
                // 3) Afunda: encolhe para dentro do ponto e some, com ondas.
                sinkU = Math.min(1.0, (since - SLIDE) / SINK);
                base = spot;
                axis = slideDir;
                double k = Math.pow(1.0 - sinkU, 1.5);
                len *= k;
                width *= 0.6 + 0.4 * k;
                alpha *= (float) (1.0 - sinkU);
            }
        }

        if (alpha > 0.01F && len > 0.02) {
            BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            silhouette(buf, cam, base, axis, len, width, alpha);
            draw(buf, SILHOUETTE_TYPE);
        }
        if (sinkU >= 0) {
            int rings = FxQuality.atLeast(Graphics.HIGH) ? 3 : FxQuality.atLeast(Graphics.MEDIUM) ? 2 : 1;
            BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            boolean any = false;
            for (int i = 0; i < rings; i++) {
                double ph = sinkU * 1.4 - i * 0.22;
                if (ph <= 0 || ph >= 1) continue;
                ripple(buf, cam, spot, 0.35 + 2.0 * ph, (float) (0.55 * (1.0 - ph)));
                any = true;
            }
            if (any) draw(buf, RIPPLE_TYPE);
        }
    }

    /** Quadrado deitado no chão: pés em {@code base}, cabeça a {@code len} blocos na direção {@code axis}. */
    private static void silhouette(BufferBuilder buf, Vec3 cam, Vec3 base, Vec3 axis, double len, double width, float alpha) {
        double px = -axis.z * width / 2, pz = axis.x * width / 2;
        double hx = base.x + axis.x * len, hz = base.z + axis.z * len;
        float y = (float) (base.y + 0.015 - cam.y);
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        vertex(buf, base.x - px - cam.x, y, base.z - pz - cam.z, 0F, 1F, 10, 5, 18, a);
        vertex(buf, base.x + px - cam.x, y, base.z + pz - cam.z, 1F, 1F, 10, 5, 18, a);
        vertex(buf, hx + px - cam.x, y, hz + pz - cam.z, 1F, 0F, 10, 5, 18, a);
        vertex(buf, hx - px - cam.x, y, hz - pz - cam.z, 0F, 0F, 10, 5, 18, a);
    }

    private static void ripple(BufferBuilder buf, Vec3 cam, Vec3 c, double r, float alpha) {
        float y = (float) (c.y + 0.02 - cam.y);
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        vertex(buf, c.x - r - cam.x, y, c.z - r - cam.z, 0F, 0F, 52, 20, 82, a);
        vertex(buf, c.x + r - cam.x, y, c.z - r - cam.z, 1F, 0F, 52, 20, 82, a);
        vertex(buf, c.x + r - cam.x, y, c.z + r - cam.z, 1F, 1F, 52, 20, 82, a);
        vertex(buf, c.x - r - cam.x, y, c.z + r - cam.z, 0F, 1F, 52, 20, 82, a);
    }

    private static void vertex(BufferBuilder buf, double x, float y, double z, float u, float v, int r, int g, int b, int a) {
        buf.addVertex((float) x, y, (float) z).setUv(u, v).setColor(r, g, b, a);
    }

    private static void draw(BufferBuilder buf, RenderType type) {
        MeshData mesh = buf.build();
        if (mesh != null) type.draw(mesh);
    }
}
