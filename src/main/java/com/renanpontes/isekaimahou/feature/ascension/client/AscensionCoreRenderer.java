package com.renanpontes.isekaimahou.feature.ascension.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.ascension.AscensionAltars;
import com.renanpontes.isekaimahou.feature.ascension.AscensionPattern;
import com.renanpontes.isekaimahou.feature.ascension.AscensionRegistry;
import com.renanpontes.isekaimahou.feature.ascension.AscensionTributes;
import com.renanpontes.isekaimahou.feature.ascension.block.AscensionCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.ascension.block.AscensionPedestalBlockEntity;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Núcleo de Ascensão no cliente:
 * <ul>
 *   <li>cristal flutuando e girando em cima do Núcleo (mais rápido durante o ritual);</li>
 *   <li>durante o ritual: círculo mágico brilhante no chão marcando o LIMITE (sair dele = falha) e um feixe de luz;</li>
 *   <li>prévia fantasma: contorno pulsante + miniatura girando em cada posição que falta no padrão;</li>
 *   <li>v0.9: letreiro flutuante com o tributo pedido e o progresso de QUEM OLHA (altar público: até 10 blocos,
 *       contando o inventário; altar caseiro: até 6 blocos, contando os pedestais), e uma coroa de runas douradas
 *       girando em volta dos altares públicos.</li>
 *   <li>v1.0: <b>domo de runas</b> (a barreira da Tribulação: parede de {@link AscensionPattern#DOME_WALL} blocos e
 *       cúpula, com faixas de runas subindo) e, em volta dos altares públicos parados, um círculo dourado bem fraco no
 *       chão marcando a área protegida (onde ninguém constrói nem quebra).</li>
 * </ul>
 */
public class AscensionCoreRenderer implements BlockEntityRenderer<AscensionCoreBlockEntity> {
    private static final ResourceLocation CIRCLE = IsekaiMahou.id("textures/misc/ascension_circle.png");
    private static final ResourceLocation SIGIL = IsekaiMahou.id("textures/misc/ascension_sigil.png");
    private static final ResourceLocation DOME = IsekaiMahou.id("textures/misc/ascension_dome.png");
    /** Segmentos em volta e anéis da cúpula do domo (≈ 48×11 quads por altar em ritual: barato). */
    private static final int DOME_SEGMENTS = 48;
    private static final int DOME_CAP_RINGS = 9;
    /** Tamanho (blocos) de um ladrilho da textura de runas no domo. */
    private static final float DOME_TILE = 3.0F;
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    /** Duração da introdução do ritual (igual a {@code Tribulation.INTRO_TICKS}). */
    private static final int INTRO_TICKS = com.renanpontes.isekaimahou.feature.ascension.Tribulation.INTRO_TICKS;

    private final ItemRenderer items;

    public AscensionCoreRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(AscensionCoreBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        long gameTime = level.getGameTime();
        float t = gameTime + partialTick;
        boolean active = be.isRitualRunning();
        int lightAbove = LevelRenderer.getLightColor(level, be.getBlockPos().above());

        renderCrystal(be, ps, buffers, t, active, active ? FULL_BRIGHT : lightAbove);

        if (active) {
            float since = (gameTime - be.phaseStart()) + partialTick;
            float fade = Mth.clamp(since / 30F, 0F, 1F);
            boolean trial = be.phase() == AscensionCoreBlockEntity.PHASE_TRIAL;
            float r = trial ? be.boundary() : be.boundary() * fade;
            int color = trial ? 0xB98CFF : 0xFFD86B;
            renderGroundLayer(ps, buffers, CIRCLE, r, t * 0.6F, color, trial ? 0.85F : 0.6F * fade, 0.03F);
            renderGroundLayer(ps, buffers, SIGIL, Math.max(1.2F, r * 0.55F), -t * 1.1F, color, trial ? 0.75F : 0.5F * fade, 0.04F);
            // Barreira: o domo cresce na introdução e fica firme durante a Tribulação.
            float grow = trial ? 1F : Mth.clamp(since / (float) INTRO_TICKS, 0F, 1F);
            renderDome(ps, buffers, be.boundary() * (0.35F + 0.65F * grow), t, color, trial ? 1F : grow);
            if (trial) {
                BeaconRenderer.renderBeaconBeam(ps, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, gameTime, 1, 96,
                        0xFFB98CFF, 0.12F, 0.18F);
            }
        }

        if (gameTime < be.previewUntil() && !be.preview().isEmpty()) {
            renderPreview(be, ps, buffers, t);
        }
        if (!active) {
            if (be.isPublic()) {
                renderGroundLayer(ps, buffers, SIGIL, 1.35F, t * 0.35F, 0xFFC857, 0.35F + 0.1F * Mth.sin(t / 15F), 0.02F);
                // Área protegida do altar público (ninguém constrói/quebra): círculo dourado bem fraco no chão.
                renderGroundLayer(ps, buffers, CIRCLE, AscensionAltars.AREA_RADIUS + 0.5F, -t * 0.08F, 0xFFC857,
                        0.10F + 0.03F * Mth.sin(t / 25F), 0.015F);
            }
            renderInfo(be, ps, buffers, gameTime);
        }
    }

    // =====================================================================================
    //  Letreiro: tributo e progresso de quem olha
    // =====================================================================================
    private record Info(long time, List<Component> lines) {}

    /** Cache por Núcleo (recalcula a cada 10 ticks; poucos Núcleos por perto). */
    private static final Map<BlockPos, Info> INFO = new HashMap<>();

    private void renderInfo(AscensionCoreBlockEntity be, PoseStack ps, MultiBufferSource buffers, long gameTime) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;
        BlockPos pos = be.getBlockPos();
        double max = be.isPublic() ? 10.0 : 6.0;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > max * max) return;
        MagicData d = ClientMagic.data();
        if (d.path() == MagicPath.NONE) return;

        Info info = INFO.get(pos);
        if (info == null || gameTime - info.time() >= 10 || gameTime < info.time()) {
            if (INFO.size() > 16) INFO.entrySet().removeIf(e -> gameTime - e.getValue().time() > 100 || gameTime < e.getValue().time());
            info = new Info(gameTime, buildInfo(be, player, d));
            INFO.put(pos.immutable(), info);
        }
        List<Component> lines = info.lines();
        if (lines.isEmpty()) return;

        Font font = mc.font;
        float scale = 0.022F;
        ps.pushPose();
        ps.translate(0.5, 2.25 + lines.size() * 10 * scale, 0.5);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(scale, -scale, scale);
        Matrix4f matrix = ps.last().pose();
        int bg = (int) (mc.options.getBackgroundOpacity(0.35F) * 255.0F) << 24;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            float x = -font.width(line) / 2F;
            font.drawInBatch(line, x, i * 10, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, bg, FULL_BRIGHT);
        }
        ps.popPose();
    }

    private static List<Component> buildInfo(AscensionCoreBlockEntity be, LocalPlayer player, MagicData d) {
        List<Component> out = new ArrayList<>();
        boolean pub = be.isPublic();
        out.add(Component.translatable(pub ? "ascension.isekai_mahou.altar.sign_public" : "ascension.isekai_mahou.altar.sign")
                .withStyle(pub ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        if (d.circle() >= Progression.MAX_CIRCLE) {
            out.add(Component.translatable("message.isekai_mahou.ascension.max").withStyle(ChatFormatting.GOLD));
            return out;
        }
        if (!be.usable()) {
            // v1.0: Núcleo caseiro sem a regra isekaiPrivateAltars — a Ascensão é nos Altares Sagrados.
            out.add(Component.translatable("ascension.isekai_mahou.altar.sign_private_off").withStyle(ChatFormatting.GRAY));
            return out;
        }
        int next = d.circle() + 1;
        out.add(Component.translatable("ascension.isekai_mahou.altar.sign_circle", d.circle(), next).withStyle(ChatFormatting.LIGHT_PURPLE));
        AscensionTributes.Tribute tribute = AscensionTributes.synced(next);
        boolean tributeOk = false;
        if (tribute != null) {
            List<ItemStack> items = new ArrayList<>();
            Level level = be.getLevel();
            if (level != null) {
                for (BlockPos p : AscensionPattern.pedestals(be.getBlockPos(), next)) {
                    if (level.getBlockEntity(p) instanceof AscensionPedestalBlockEntity ped && !ped.getItem().isEmpty()) items.add(ped.getItem());
                }
            }
            Item seal = AscensionRegistry.tributeItem(next);
            List<ItemStack> inv = new ArrayList<>(player.getInventory().items);
            inv.addAll(player.getInventory().offhand);
            for (ItemStack s : inv) if (!s.isEmpty() && (pub || s.is(seal))) items.add(s);
            AscensionTributes.Check check = AscensionTributes.check(tribute, items, d);
            for (AscensionTributes.Line l : check.lines()) out.add(l.text());
            tributeOk = check.complete();
        }
        boolean levelOk = d.atLevelCap();
        out.add(Component.literal(levelOk ? "✔ " : "✘ ").withStyle(levelOk ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(Component.translatable("ascension.isekai_mahou.altar.sign_level", d.level(), d.levelCap())
                        .withStyle(levelOk ? ChatFormatting.GRAY : ChatFormatting.WHITE)));
        // Recurso cheio (Mana/Vigor/Foco): o Núcleo também confere — sem esta linha o letreiro diria "pronto" à toa.
        boolean manaOk = d.mana() >= d.maxMana() - 0.5F;
        out.add(Component.literal(manaOk ? "✔ " : "✘ ").withStyle(manaOk ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(Component.translatable("message.isekai_mahou.ascension.resource",
                        MagicApi.resourceName(player), (int) d.mana(), d.maxMana())
                        .withStyle(manaOk ? ChatFormatting.GRAY : ChatFormatting.WHITE)));
        if (tributeOk && levelOk && manaOk) {
            out.add(Component.translatable("ascension.isekai_mahou.altar.sign_ready").withStyle(ChatFormatting.GREEN));
        } else if (!tributeOk) {
            out.add(Component.translatable(pub ? "ascension.isekai_mahou.altar.sign_hint_public" : "ascension.isekai_mahou.altar.sign_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    private void renderCrystal(AscensionCoreBlockEntity be, PoseStack ps, MultiBufferSource buffers, float t, boolean active, int lightColor) {
        ItemStack crystal = new ItemStack(AscensionRegistry.MANA_CRYSTAL_BLOCK_ITEM.get());
        float bob = Mth.sin(t / 12F) * 0.08F;
        float spin = active ? t * 9F : t * 2F;
        ps.pushPose();
        ps.translate(0.5, 1.45 + bob + (active ? 0.25 : 0), 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(spin % 360F));
        ps.mulPose(Axis.XP.rotationDegrees(35.26F));
        ps.mulPose(Axis.ZP.rotationDegrees(45F));
        float s = active ? 0.38F : 0.3F;
        ps.scale(s, s, s);
        items.renderStatic(crystal, ItemDisplayContext.NONE, lightColor, OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), 0);
        ps.popPose();
    }

    /** Quadrado texturizado deitado no chão, centrado no Núcleo, girando. */
    private static void renderGroundLayer(PoseStack ps, MultiBufferSource buffers, ResourceLocation tex, float radius, float angle,
                                          int rgb, float alpha, float y) {
        ps.pushPose();
        ps.translate(0.5, y, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(angle % 360F));
        PoseStack.Pose pose = ps.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(tex));
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, a = (int) (Mth.clamp(alpha, 0F, 1F) * 255);
        vertex(vc, pose, -radius, -radius, 0, 0, r, g, b, a);
        vertex(vc, pose, -radius, radius, 0, 1, r, g, b, a);
        vertex(vc, pose, radius, radius, 1, 1, r, g, b, a);
        vertex(vc, pose, radius, -radius, 1, 0, r, g, b, a);
        ps.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float z, float u, float v, int r, int g, int b, int a) {
        vc.addVertex(pose, x, 0, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /**
     * Domo de runas centrado no Núcleo: parede reta até {@link AscensionPattern#DOME_WALL} e cúpula de raio
     * {@code radius}. Translúcido, sem cull (visto de dentro e de fora), runas subindo devagar e brilho que pulsa.
     */
    private static void renderDome(PoseStack ps, MultiBufferSource buffers, float radius, float t, int rgb, float fade) {
        if (radius < 0.5F || fade <= 0.01F) return;
        float wallH = AscensionPattern.DOME_WALL * Mth.clamp(radius / 3F, 0.3F, 1F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(DOME));
        ps.pushPose();
        ps.translate(0.5, 0.02, 0.5);
        PoseStack.Pose pose = ps.last();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float pulse = 0.85F + 0.15F * Mth.sin(t / 9F);
        float repU = Math.max(6, Math.round(TWO_PI * radius / DOME_TILE));
        float scroll = t * 0.012F;
        // Perfil do domo: (raio horizontal, altura, comprimento do arco) por anel.
        int rings = 2 + DOME_CAP_RINGS;
        float[] pr = new float[rings + 1], py = new float[rings + 1], pv = new float[rings + 1], pa = new float[rings + 1];
        pr[0] = radius; py[0] = 0F; pv[0] = 0F;
        pr[1] = radius; py[1] = wallH * 0.5F; pv[1] = wallH * 0.5F;
        pr[2] = radius; py[2] = wallH; pv[2] = wallH;
        for (int k = 1; k <= DOME_CAP_RINGS; k++) {
            // Para um pouco antes do topo (sem triângulos degenerados); o topo fica quase transparente.
            float phi = (Mth.HALF_PI * 0.96F) * k / DOME_CAP_RINGS;
            pr[2 + k] = radius * Mth.cos(phi);
            py[2 + k] = wallH + radius * Mth.sin(phi);
            pv[2 + k] = wallH + radius * phi;
        }
        float total = pv[rings];
        for (int k = 0; k <= rings; k++) {
            float h = pv[k] / total;
            // Base forte (faixa de runas), sumindo até o topo; a borda de baixo some de leve no chão.
            float a = k == 0 ? 0.45F : 0.70F - 0.55F * h;
            pa[k] = Mth.clamp(a * pulse * fade, 0F, 1F);
        }
        for (int k = 0; k < rings; k++) {
            int a0 = (int) (pa[k] * 255), a1 = (int) (pa[k + 1] * 255);
            float v0 = pv[k] / DOME_TILE - scroll, v1 = pv[k + 1] / DOME_TILE - scroll;
            for (int i = 0; i < DOME_SEGMENTS; i++) {
                float t0 = TWO_PI * i / DOME_SEGMENTS, t1 = TWO_PI * (i + 1) / DOME_SEGMENTS;
                float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
                float u0 = repU * i / DOME_SEGMENTS, u1 = repU * (i + 1) / DOME_SEGMENTS;
                domeVertex(vc, pose, c0 * pr[k], py[k], s0 * pr[k], u0, v0, r, g, b, a0, c0, s0);
                domeVertex(vc, pose, c1 * pr[k], py[k], s1 * pr[k], u1, v0, r, g, b, a0, c1, s1);
                domeVertex(vc, pose, c1 * pr[k + 1], py[k + 1], s1 * pr[k + 1], u1, v1, r, g, b, a1, c1, s1);
                domeVertex(vc, pose, c0 * pr[k + 1], py[k + 1], s0 * pr[k + 1], u0, v1, r, g, b, a1, c0, s0);
            }
        }
        ps.popPose();
    }

    private static void domeVertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                                   int r, int g, int b, int a, float nx, float nz) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, nx, 0, nz);
    }

    private void renderPreview(AscensionCoreBlockEntity be, PoseStack ps, MultiBufferSource buffers, float t) {
        float pulse = 0.55F + 0.45F * Mth.sin(t / 5F);
        for (AscensionPattern.Slot slot : be.preview()) {
            int c = slot.part().color;
            float r = ((c >> 16) & 255) / 255F, g = ((c >> 8) & 255) / 255F, b = (c & 255) / 255F;
            AABB box = new AABB(slot.dx() + 0.02, 0.02, slot.dz() + 0.02, slot.dx() + 0.98, 0.98, slot.dz() + 0.98);
            LevelRenderer.renderLineBox(ps, buffers.getBuffer(RenderType.lines()), box, r, g, b, pulse);

            ps.pushPose();
            ps.translate(slot.dx() + 0.5, 0.45 + Mth.sin(t / 8F + slot.dx() * 3 + slot.dz()) * 0.06, slot.dz() + 0.5);
            ps.mulPose(Axis.YP.rotationDegrees((t * 3F) % 360F));
            ps.scale(0.45F, 0.45F, 0.45F);
            items.renderStatic(new ItemStack(slot.part().block().asItem()), ItemDisplayContext.NONE, FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), slot.dx() * 31 + slot.dz());
            ps.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(AscensionCoreBlockEntity be) {
        return be.isRitualRunning();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(AscensionCoreBlockEntity be) {
        BlockPos p = be.getBlockPos();
        // Domo (até 9 de raio) / área do altar público (10) + folga; o feixe sobe durante o ritual.
        int r = Math.max(AscensionAltars.AREA_RADIUS, be.boundary()) + 2;
        return new AABB(p.getX() - r, p.getY() - 1, p.getZ() - r, p.getX() + r + 1, p.getY() + (be.isRitualRunning() ? 100 : 5), p.getZ() + r + 1);
    }
}
