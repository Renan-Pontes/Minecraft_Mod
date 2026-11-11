package com.renanpontes.isekaimahou.feature.creatures_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.AbstractGoblin;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.GoblinKing;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.GoblinShaman;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.GoblinWolfRider;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Todos os goblins (GeckoLib). O mesmo renderer serve para cada tipo, com escala própria; itens da mão
 * principal/secundária (porrete, arco, espada, escudo) são desenhados nos ossos "right_item"/"left_item".
 * O xamã e o rei têm partes que brilham no escuro.
 */
public class GoblinRenderer extends GeoEntityRenderer<AbstractGoblin> {
    public GoblinRenderer(EntityRendererProvider.Context ctx, float scale, float shadow, float itemScale) {
        super(ctx, new GoblinModel());
        withScale(scale);
        this.shadowRadius = shadow;
        // Gema do cajado do xamã e rubi da coroa do rei brilham no escuro (texturas _glowmask).
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(PoseStack poseStack, AbstractGoblin goblin, BakedGeoModel model, RenderType renderType,
                               MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int light, int overlay) {
                if (goblin instanceof GoblinShaman || goblin instanceof GoblinKing) {
                    super.render(poseStack, goblin, model, renderType, buffers, buffer, partialTick, light, overlay);
                }
            }
        });
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, AbstractGoblin goblin) {
                ItemStack stack = switch (bone.getName()) {
                    case "right_item" -> goblin.getMainHandItem();
                    case "left_item" -> goblin.getOffhandItem();
                    default -> ItemStack.EMPTY;
                };
                return stack.isEmpty() ? null : stack;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, AbstractGoblin goblin) {
                return bone.getName().equals("left_item") ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                        : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, AbstractGoblin goblin,
                                              MultiBufferSource buffers, float partialTick, int light, int overlay) {
                boolean left = bone.getName().equals("left_item");
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                if (stack.getItem() instanceof ShieldItem) {
                    poseStack.translate(0.0, 0.125, left ? 0.25 : -0.25);
                    if (left) poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                }
                poseStack.scale(itemScale, itemScale, itemScale);
                super.renderStackForBone(poseStack, bone, stack, goblin, buffers, partialTick, light, overlay);
            }
        });
    }

    /** O Rei Goblin tem animação de morte própria: não tomba de lado como os mobs vanilla. */
    @Override
    protected float getDeathMaxRotation(AbstractGoblin goblin) {
        return goblin instanceof GoblinKing ? 0.0F : super.getDeathMaxRotation(goblin);
    }

    /** Rei morrendo: vermelho só no golpe final (a animação de morte dura 2,5 s). */
    @Override
    public int getPackedOverlay(AbstractGoblin goblin, float u, float partialTick) {
        if (!(goblin instanceof GoblinKing) || goblin.deathTime <= 0) return super.getPackedOverlay(goblin, u, partialTick);
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(goblin.deathTime < 8));
    }

    /** Modelo/textura pelo id do tipo; o cavaleiro de lobo e o rei têm animações próprias. A cabeça segue o olhar. */
    static final class GoblinModel extends GeoModel<AbstractGoblin> {
        private static final ResourceLocation GOBLIN_ANIM = IsekaiMahou.id("animations/entity/goblin.animation.json");
        private static final ResourceLocation RIDER_ANIM = IsekaiMahou.id("animations/entity/goblin_wolf_rider.animation.json");
        private static final ResourceLocation KING_ANIM = IsekaiMahou.id("animations/entity/goblin_king.animation.json");

        private static String id(AbstractGoblin goblin) {
            return EntityType.getKey(goblin.getType()).getPath();
        }

        @Override
        public ResourceLocation getModelResource(AbstractGoblin goblin) {
            return IsekaiMahou.id("geo/entity/" + id(goblin) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(AbstractGoblin goblin) {
            return IsekaiMahou.id("textures/entity/" + id(goblin) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(AbstractGoblin goblin) {
            if (goblin instanceof GoblinKing) return KING_ANIM;
            return goblin instanceof GoblinWolfRider ? RIDER_ANIM : GOBLIN_ANIM;
        }

        @Override
        public void setCustomAnimations(AbstractGoblin goblin, long instanceId, AnimationState<AbstractGoblin> state) {
            GeoBone head = getAnimationProcessor().getBone("head");
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            // Rei morrendo: a cabeça segue só a animação de morte.
            if (head == null || data == null || (goblin instanceof GoblinKing && goblin.isDeadOrDying())) return;
            // Soma ao valor da animação (a corneta/intro do rei inclinam a cabeça); goblins sem animação de cabeça
            // partem de zero a cada quadro, então para eles nada muda.
            head.setRotX(head.getRotX() + data.headPitch() * ((float) Math.PI / 180F));
            head.setRotY(head.getRotY() + data.netHeadYaw() * ((float) Math.PI / 180F));
        }
    }
}
