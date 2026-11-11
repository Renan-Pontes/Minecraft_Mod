package com.renanpontes.isekaimahou.feature.legends_f.client;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_f.entity.ShadowSoldierEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Soldado das Sombras: desenha o MODELO DA CRIATURA ORIGINAL (uma cópia "fantasma" só do cliente que imita a pose,
 * os passos e os golpes do soldado) com todas as cores puxadas para um preto-arroxeado, mais dois olhos roxos acesos.
 * Saindo do chão, aparece afundado e sobe. Se o modelo de alguma criatura falhar, cai para a silhueta de um zumbi.
 */
public class ShadowSoldierRenderer extends EntityRenderer<ShadowSoldierEntity> {
    private static final Map<ShadowSoldierEntity, LivingEntity> DUMMIES = new WeakHashMap<>();
    private static final Set<String> BROKEN = new HashSet<>();
    private static final ResourceLocation FALLBACK = ResourceLocation.withDefaultNamespace("zombie");

    public ShadowSoldierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.5F;
        this.shadowStrength = 0.9F;
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowSoldierEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public boolean shouldRender(ShadowSoldierEntity e, Frustum frustum, double x, double y, double z) {
        return super.shouldRender(e, frustum, x, y, z) || e.distanceToSqr(x, y, z) < 16 * 16;
    }

    @Override
    public void render(ShadowSoldierEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        LivingEntity dummy = dummyFor(e);
        float rise = e.riseProgress(pt);
        pose.pushPose();
        if (rise < 1F) pose.translate(0, -(1F - LfDraw.smooth(rise)) * e.getBbHeight(), 0);
        boolean monarch = e.monarch();
        if (dummy != null) {
            sync(e, dummy);
            String type = e.sourceType();
            try {
                EntityRenderer<? super LivingEntity> r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
                MultiBufferSource tinted = new TintedBuffers(buffers, monarch ? 0.30F : 0.2F, monarch ? 0.18F : 0.12F, monarch ? 0.48F : 0.32F);
                r.render(dummy, yaw, pt, pose, tinted, light);
            } catch (RuntimeException ex) {
                if (BROKEN.add(type)) IsekaiMahou.LOGGER.warn("Soldado das Sombras: o modelo de {} falhou, usando silhueta padrão", type, ex);
                DUMMIES.remove(e);
            }
        }
        // Olhos acesos
        float headYaw = Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) * Mth.DEG_TO_RAD;
        float eyeY = e.getEyeHeight() + 0.02F;
        float fwd = e.getBbWidth() * 0.5F + 0.02F;
        float sep = Math.max(0.08F, e.getBbWidth() * 0.16F);
        Vec3 f = new Vec3(-Mth.sin(headYaw), 0, Mth.cos(headYaw));
        Vec3 s = new Vec3(f.z, 0, -f.x);
        VertexConsumer glow = buffers.getBuffer(LfRenderTypes.GLOW);
        Matrix4f m = pose.last().pose();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        int core = monarch ? 0xF0E8FF : 0xD8C8FF, halo = monarch ? 0x9A6BFF : 0x6A3DD8;
        float blink = (e.tickCount + e.getId() * 13) % 90 < 3 ? 0.2F : 1F;
        for (int k = -1; k <= 1; k += 2) {
            Vec3 c = f.scale(fwd).add(s.scale(sep * k)).add(0, eyeY, 0);
            LfDraw.billboard(glow, m, c, left, up, 0.07F + e.getBbWidth() * 0.03F, LfDraw.argb(core, blink), LfDraw.argb(halo, 0F));
            LfDraw.billboard(glow, m, c, left, up, 0.2F + e.getBbWidth() * 0.06F, LfDraw.argb(halo, 0.35F * blink), LfDraw.argb(halo, 0F));
        }
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    /** Cópia de cliente da criatura original (criada uma vez por soldado). */
    private static LivingEntity dummyFor(ShadowSoldierEntity e) {
        LivingEntity d = DUMMIES.get(e);
        if (d != null && d.level() == e.level()) return d;
        String type = e.sourceType();
        d = create(e, BROKEN.contains(type) ? FALLBACK : ResourceLocation.tryParse(type));
        if (d == null) d = create(e, FALLBACK);
        if (d != null) DUMMIES.put(e, d);
        return d;
    }

    private static LivingEntity create(ShadowSoldierEntity e, ResourceLocation rl) {
        if (rl == null) return null;
        Optional<EntityType<?>> t = BuiltInRegistries.ENTITY_TYPE.getOptional(rl);
        if (t.isEmpty()) return null;
        try {
            Entity made = t.get().create(e.level());
            if (made instanceof LivingEntity le) {
                le.setInvisible(false);
                le.setSilent(true);
                return le;
            }
        } catch (RuntimeException ex) {
            BROKEN.add(rl.toString());
        }
        return null;
    }

    /** A cópia imita o soldado: posição, giro do corpo e da cabeça, passos e golpe. */
    private static void sync(ShadowSoldierEntity e, LivingEntity d) {
        d.setPos(e.getX(), e.getY(), e.getZ());
        d.xo = e.xo;
        d.yo = e.yo;
        d.zo = e.zo;
        d.xOld = e.xOld;
        d.yOld = e.yOld;
        d.zOld = e.zOld;
        d.yBodyRot = e.yBodyRot;
        d.yBodyRotO = e.yBodyRotO;
        d.yHeadRot = e.yHeadRot;
        d.yHeadRotO = e.yHeadRotO;
        d.setYRot(e.getYRot());
        d.yRotO = e.yRotO;
        d.setXRot(e.getXRot());
        d.xRotO = e.xRotO;
        d.attackAnim = e.attackAnim;
        d.oAttackAnim = e.oAttackAnim;
        d.swinging = e.swinging;
        d.setOnGround(e.onGround());
        if (d.tickCount != e.tickCount) {
            d.walkAnimation.update(e.walkAnimation.speed(), 1F);
            d.tickCount = e.tickCount;
        }
        if (d instanceof Mob dm) dm.setAggressive(e.isAggressive());
    }

    // =====================================================================================
    //  Buffers que escurecem tudo
    // =====================================================================================

    /** Embrulha o MultiBufferSource para que toda cor saia multiplicada pela tinta de sombra. */
    private record TintedBuffers(MultiBufferSource inner, float r, float g, float b) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return new Tinted(inner.getBuffer(type), r, g, b);
        }
    }

    private record Tinted(VertexConsumer inner, float r, float g, float b) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            inner.setColor((int) (red * r), (int) (green * g), (int) (blue * b), alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }
    }
}
