package com.renanpontes.isekaimahou.feature.character.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.character.Race;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Aparência racial no jogador (terceira pessoa, inventário e outros jogadores): orelhas pontudas (Elfo), barba (Anão,
 * 4 cores), presas e pele esverdeada (Meio-Orc), chifres e cauda com ponta de lança (Demônio), chifres para trás,
 * escamas na cor da ancestralidade e cauda grossa (Dracônico), orelhas e cauda de raposa/lobo/gato/coelho (Bestial).
 * v1.0.1: olhos extras e patinhas nas costas (Aracnídeo), corpo espectral translúcido (Fantasma), auréola dourada e
 * asinhas de penas (Anjo), corpo de geleia translúcido (Slime).
 * <p>
 * <b>Transformações</b> ({@link ClientCharacter#form}): asas de luz enormes + auréola maior (Anjo), asas de sombra
 * (Demônio), asas dracônicas na cor da ancestralidade (Dracônico), corpo fantasma translúcido mesmo invisível
 * (Fantasma), 8 patas de aranha andando (Aracnídeo), geleia densa + coroa (Rei Slime), aura na cor da raça (Elfo com
 * coroa de folhas, Anão de pedra, Meio-Orc rubra, Bestial, Humano dourada). O tamanho vem do atributo de escala.
 * <p>
 * As <b>Asas Sombrias</b> (skill racial do Demônio) continuam: membrana translúcida abrindo em 8 ticks e batendo.
 */
public class RaceFeatureLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/character/race_features.png");
    private static final ResourceLocation TEXTURE2 = IsekaiMahou.id("textures/entity/character/race_features2.png");
    private static final ResourceLocation WINGS_TEXTURE = IsekaiMahou.id("textures/entity/character/shadow_wings.png");
    private static final ResourceLocation ANGEL_WINGS = IsekaiMahou.id("textures/entity/character/angel_wings.png");
    private static final ResourceLocation DRAGON_WINGS = IsekaiMahou.id("textures/entity/character/dragon_wings.png");
    private static final ResourceLocation AURA = IsekaiMahou.id("textures/entity/character/aura.png");
    private static final int[] BEARD = {0xFF6B4226, 0xFFB5532A, 0xFF2B2420, 0xFF9A9AA0};
    private static final int[] FUR = {0xFFD9782F, 0xFF8E949E, 0xFF3B3442, 0xFFE8E0D6};
    private static final int[] FUR_TIP = {0xFFF4EEE6, 0xFFDADDE2, 0xFF57506A, 0xFFFFFFFF};

    private static ModelPart root;
    private static ModelPart root2;
    private static ModelPart wings;

    public RaceFeatureLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    // =====================================================================================
    //  Modelo (pixels do jogador: cabeça de −8 a 0 em y, frente em z = −4)
    // =====================================================================================
    private static ModelPart model() {
        if (root != null) return root;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        // Orelhas de elfo: compridas, para trás e para cima
        for (int s : new int[]{1, -1}) {
            String side = s > 0 ? "l" : "r";
            r.addOrReplaceChild("elf_ear_" + side, CubeListBuilder.create().texOffs(0, 0).mirror(s < 0)
                            .addBox(s > 0 ? 0 : -1, -1.5F, -0.5F, 1, 3, 4)
                            .texOffs(0, 8).mirror(s < 0).addBox(s > 0 ? 0 : -1, -2.6F, 2.6F, 1, 2, 2),
                    PartPose.offsetAndRotation(4F * s, -4.2F, -0.5F, -0.45F, 0.42F * s, 0.12F * s));
            // Chifres de demônio: base grossa, curva para trás, ponta fina
            PartDefinition horn = r.addOrReplaceChild("demon_horn_" + side, CubeListBuilder.create().texOffs(12, 0)
                    .addBox(-1F, -3F, -1F, 2, 3, 2), PartPose.offsetAndRotation(2.4F * s, -7.6F, -1.8F, -0.25F, 0F, 0.3F * s));
            PartDefinition mid = horn.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(12, 6)
                    .addBox(-0.5F, -3F, -0.5F, 1, 3, 1), PartPose.offsetAndRotation(0F, -2.6F, 0F, 0.65F, 0F, 0F));
            mid.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(16, 6)
                    .addBox(-0.5F, -2F, -0.5F, 1, 2, 1), PartPose.offsetAndRotation(0F, -2.6F, 0F, 0.7F, 0F, 0F));
            // Chifres de dracônico: varridos para trás, saindo de cima da nuca
            PartDefinition dh = r.addOrReplaceChild("dragon_horn_" + side, CubeListBuilder.create().texOffs(20, 0)
                    .addBox(-1F, -1F, 0F, 2, 2, 4), PartPose.offsetAndRotation(2.6F * s, -7.2F, 1.5F, 0.5F, 0.18F * s, 0F));
            dh.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(20, 6)
                    .addBox(-0.5F, -0.5F, 0F, 1, 1, 3), PartPose.offsetAndRotation(0F, 0F, 3.6F, 0.35F, 0F, 0F));
            // Presas de meio-orc
            r.addOrReplaceChild("orc_tusk_" + side, CubeListBuilder.create().texOffs(32, 0)
                    .addBox(-0.5F, -2F, -0.5F, 1, 2, 1), PartPose.offsetAndRotation(2.1F * s, -0.6F, -4.35F, 0F, 0F, -0.15F * s));
            // Orelhas animais (triangulares) e de coelho (compridas)
            PartDefinition be = r.addOrReplaceChild("beast_ear_" + side, CubeListBuilder.create().texOffs(40, 0)
                    .addBox(-1.5F, -3F, -0.5F, 3, 3, 1)
                    .texOffs(40, 4).addBox(-1F, -4F, -0.5F, 2, 1, 1)
                    .texOffs(40, 6).addBox(-0.5F, -5F, -0.5F, 1, 1, 1),
                    PartPose.offsetAndRotation(2.6F * s, -7.6F, -0.2F, -0.1F, 0F, 0.22F * s));
            be.addOrReplaceChild("inner", CubeListBuilder.create().texOffs(48, 8)
                    .addBox(-1F, -2.6F, -0.6F, 2, 2, 0.1F), PartPose.ZERO);
            r.addOrReplaceChild("rabbit_ear_" + side, CubeListBuilder.create().texOffs(48, 0)
                    .addBox(-1F, -6F, -0.5F, 2, 6, 1), PartPose.offsetAndRotation(1.8F * s, -7.6F, 0.4F, -0.2F, 0F, 0.12F * s));
        }
        // Barba de anão (com bigode)
        r.addOrReplaceChild("dwarf_beard", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-4F, -3F, -4.9F, 8, 4, 1)
                .texOffs(0, 22).addBox(-3F, 1F, -4.9F, 6, 3, 1)
                .texOffs(0, 27).addBox(-2F, 4F, -4.9F, 4, 2, 1)
                .texOffs(20, 16).addBox(-3.5F, -3.6F, -5.1F, 7, 1, 1), PartPose.ZERO);
        // Caudas (no corpo: base na lombar, crescendo para trás)
        tail(r, "beast_tail", 0, 32, 3, 3, 4, 3);
        tail(r, "demon_tail", 20, 32, 1, 1, 4, 3);
        PartDefinition dt = r.addOrReplaceChild("dragon_tail", CubeListBuilder.create().texOffs(32, 32)
                .addBox(-2F, -2F, 0F, 4, 4, 5), PartPose.offset(0F, 10.5F, 1.6F));
        PartDefinition dt2 = dt.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(32, 41)
                .addBox(-1.5F, -1.5F, 0F, 3, 3, 5), PartPose.offset(0F, 0.3F, 4.6F));
        dt2.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(32, 49)
                .addBox(-1F, -1F, 0F, 2, 2, 4), PartPose.offset(0F, 0.2F, 4.6F));
        r.addOrReplaceChild("spade", CubeListBuilder.create().texOffs(20, 38)
                .addBox(-1.5F, -0.5F, 0F, 3, 1, 2).texOffs(20, 41).addBox(-0.5F, -0.5F, 2F, 1, 1, 1), PartPose.ZERO);
        r.addOrReplaceChild("beast_tip", CubeListBuilder.create().texOffs(0, 46)
                .addBox(-1.5F, -1.5F, 0F, 3, 3, 3), PartPose.ZERO);
        r.addOrReplaceChild("rabbit_puff", CubeListBuilder.create().texOffs(0, 52)
                .addBox(-1.5F, -1.5F, 0F, 3, 3, 2), PartPose.offset(0F, 10.5F, 1.8F));
        root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        return root;
    }

    /**
     * Partes da v1.0.1 ({@code race_features2.png}): auréola (octógono de 8 gomos, ouro em (0,0)), olhos de aranha (vermelho
     * brilhante em (0,8)), pata de aranha de 2 segmentos (quitina em (0,16)/(0,24)) e a coroa (ouro em (0,40), pontas
     * com joia em (40,40)).
     */
    private static ModelPart model2() {
        if (root2 != null) return root2;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition halo = r.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            halo.addOrReplaceChild("seg" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-1.4F, -0.35F, -0.35F, 2.8F, 0.7F, 0.7F),
                    PartPose.offsetAndRotation((float) Math.cos(a) * 3.3F, 0F, (float) Math.sin(a) * 3.3F, 0F, (float) (-a + Math.PI / 2), 0F));
        }
        // Olhos de aranha: dois pares na testa (os de cima menores)
        r.addOrReplaceChild("spider_eyes", CubeListBuilder.create().texOffs(0, 8)
                .addBox(-3.6F, -6.4F, -4.35F, 1.2F, 1.2F, 0.4F)
                .addBox(2.4F, -6.4F, -4.35F, 1.2F, 1.2F, 0.4F)
                .texOffs(4, 8)
                .addBox(-2.0F, -7.3F, -4.3F, 0.9F, 0.9F, 0.3F)
                .addBox(1.1F, -7.3F, -4.3F, 0.9F, 0.9F, 0.3F), PartPose.ZERO);
        // Pata de aranha: coxa saindo das costas para o lado e a canela descendo
        PartDefinition leg = r.addOrReplaceChild("spider_leg", CubeListBuilder.create().texOffs(0, 16)
                .addBox(0F, -0.6F, -0.6F, 7F, 1.2F, 1.2F), PartPose.ZERO);
        leg.addOrReplaceChild("lower", CubeListBuilder.create().texOffs(0, 24)
                .addBox(-0.5F, 0F, -0.5F, 1F, 9F, 1F), PartPose.offset(6.6F, 0F, 0F));
        // Coroa (Rei Slime): aro + 4 pontas com joia
        r.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 40)
                .addBox(-4.5F, -10F, -4.5F, 9, 2, 9)
                .texOffs(40, 40)
                .addBox(-4.5F, -12F, -0.5F, 1, 2, 1).addBox(3.5F, -12F, -0.5F, 1, 2, 1)
                .addBox(-0.5F, -12F, -4.5F, 1, 2, 1).addBox(-0.5F, -12F, 3.5F, 1, 2, 1), PartPose.ZERO);
        root2 = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        return root2;
    }

    /** Asas: osso + membrana (plano) e a ponta articulada, nas costas (pivô entre as omoplatas). */
    private static ModelPart wingModel() {
        if (wings != null) return wings;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        for (int s : new int[]{1, -1}) {
            String side = s > 0 ? "l" : "r";
            PartDefinition w = r.addOrReplaceChild("wing_" + side, CubeListBuilder.create()
                    .texOffs(0, 0).mirror(s < 0).addBox(s > 0 ? 0F : -12F, -1F, 0F, 12, 1, 1)
                    .texOffs(0, 4).mirror(s < 0).addBox(s > 0 ? 0F : -12F, 0F, 0.5F, 12, 11, 0),
                    PartPose.offset(1.2F * s, 2.5F, 2.2F));
            w.addOrReplaceChild("tip", CubeListBuilder.create()
                    .texOffs(0, 16).mirror(s < 0).addBox(s > 0 ? 0F : -8F, -1F, 0F, 8, 1, 1)
                    .texOffs(0, 20).mirror(s < 0).addBox(s > 0 ? 0F : -8F, 0F, 0.5F, 8, 8, 0),
                    PartPose.offset(12F * s, 0F, 0F));
        }
        wings = LayerDefinition.create(mesh, 64, 32).bakeRoot();
        return wings;
    }

    /** Cauda de 3 segmentos encadeados (seg2 e seg3 filhos). */
    private static void tail(PartDefinition r, String name, int u, int v, int w, int h, int len, int segs) {
        PartDefinition prev = r.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v)
                .addBox(-w / 2F, -h / 2F, 0F, w, h, len), PartPose.offset(0F, 10.5F, 1.6F));
        for (int i = 2; i <= segs; i++) {
            prev = prev.addOrReplaceChild("seg" + i, CubeListBuilder.create().texOffs(u, v)
                    .addBox(-w / 2F, -h / 2F, 0F, w, h, len), PartPose.offset(0F, 0F, len - 0.4F));
        }
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float netHeadYaw, float headPitch) {
        int overlay = LivingEntityRenderer.getOverlayCoords(p, 0F);
        ClientCharacter.Look look = ClientCharacter.look(p.getId());
        Race race = look == null ? null : look.race();
        ClientCharacter.Fx form = race == null ? null : ClientCharacter.form(p.getId());
        if (p.isInvisible()) {
            // Forma Espectral: o corpo fica invisível para as criaturas, mas os jogadores veem o fantasma translúcido
            if (race == Race.PHANTOM && form != null) {
                skinShell(ps, buffers, light, overlay, p, 0x66E8F8FF, 1.0F);
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, 0x509FD8E8, 1.06F);
            }
            return;
        }
        ClientCharacter.Fx wingFx = ClientCharacter.wings(p.getId());
        if (wingFx != null) renderShadowWings(ps, buffers, light, overlay, p, wingFx, partialTick);
        if (race == null) return;
        float formOpen = form == null ? 0F : formOpen(p, form, partialTick);
        if (form != null) renderForm(ps, buffers, light, overlay, p, race, look.ancestryRgb(), formOpen, partialTick, limbSwing, limbSwingAmount);
        if (race == Race.HUMAN) return;
        int variant = Math.floorMod(p.getUUID().hashCode(), 4);

        skinTint(ps, buffers, light, overlay, p, race, look.ancestryRgb());

        ModelPart m = model();
        ModelPart m2 = model2();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PlayerModel<AbstractClientPlayer> pm = getParentModel();
        float sway = Mth.sin(age * 0.09F) * 0.22F + Mth.sin(limbSwing * 0.6F) * limbSwingAmount * 0.35F;

        // ---------- cabeça ----------
        ps.pushPose();
        pm.head.translateAndRotate(ps);
        switch (race) {
            case ELF -> {
                m.getChild("elf_ear_l").render(ps, vc, light, overlay);
                m.getChild("elf_ear_r").render(ps, vc, light, overlay);
            }
            case DWARF -> m.getChild("dwarf_beard").render(ps, vc, light, overlay, BEARD[variant]);
            case HALF_ORC -> {
                m.getChild("orc_tusk_l").render(ps, vc, light, overlay);
                m.getChild("orc_tusk_r").render(ps, vc, light, overlay);
            }
            case DEMON -> {
                m.getChild("demon_horn_l").render(ps, vc, light, overlay);
                m.getChild("demon_horn_r").render(ps, vc, light, overlay);
            }
            case DRAGONBORN -> {
                int horn = 0xFF000000 | mix(0xE8DCC0, look.ancestryRgb(), 0.25F);
                m.getChild("dragon_horn_l").render(ps, vc, light, overlay, horn);
                m.getChild("dragon_horn_r").render(ps, vc, light, overlay, horn);
            }
            case BEASTFOLK -> {
                String ear = variant == 3 ? "rabbit_ear_" : "beast_ear_";
                float twitch = (p.tickCount + p.getId() * 7) % 90 < 4 ? 0.25F : 0F;
                for (String side : new String[]{"l", "r"}) {
                    ModelPart e = m.getChild(ear + side);
                    float base = e.zRot;
                    e.zRot = base + (side.equals("l") ? twitch : -twitch);
                    e.render(ps, vc, light, overlay, FUR[variant]);
                    e.zRot = base;
                }
            }
            case ARACHNID -> m2.getChild("spider_eyes").render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE2)),
                    LightTexture.FULL_BRIGHT, overlay);
            case ANGEL -> {
                // Auréola dourada flutuando (brilha no escuro); na Forma Seráfica fica maior e gira
                float bob = Mth.sin(age * 0.08F) * 0.35F;
                float big = form != null ? 1.0F + 0.35F * formOpen : 1.0F;
                ps.pushPose();
                ps.translate(0, (-10.2F + bob - (big - 1F) * 2F) / 16F, 0);
                ps.scale(big, 1F, big);
                ModelPart halo = m2.getChild("halo");
                halo.yRot = form != null ? age * 0.06F : 0F;
                halo.render(ps, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE2)), LightTexture.FULL_BRIGHT, overlay, 0xF0FFFFFF);
                ps.popPose();
            }
            case SLIME -> {
                if (form != null) m2.getChild("crown").render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE2)), light, overlay);
            }
            default -> { }
        }
        ps.popPose();

        // ---------- corpo: caudas, patas, asinhas ----------
        ps.pushPose();
        pm.body.translateAndRotate(ps);
        switch (race) {
            case DEMON -> {
                ModelPart t = m.getChild("demon_tail");
                curl(t, -0.9F, 0.45F, sway * 1.4F);
                t.render(ps, vc, light, overlay, 0xFF2A1A22);
                // ponta de lança no fim da cauda
                ps.pushPose();
                t.translateAndRotate(ps);
                ModelPart s2 = t.getChild("seg2");
                s2.translateAndRotate(ps);
                ModelPart s3 = s2.getChild("seg3");
                s3.translateAndRotate(ps);
                ps.translate(0, 0, 3.6F / 16F);
                m.getChild("spade").render(ps, vc, light, overlay, 0xFFB0303C);
                ps.popPose();
            }
            case DRAGONBORN -> {
                ModelPart t = m.getChild("dragon_tail");
                curl(t, -0.75F, 0.25F, sway * 0.7F);
                t.render(ps, vc, light, overlay, 0xFF000000 | look.ancestryRgb());
            }
            case BEASTFOLK -> {
                if (variant == 3) {
                    m.getChild("rabbit_puff").render(ps, vc, light, overlay, FUR_TIP[variant]);
                } else {
                    ModelPart t = m.getChild("beast_tail");
                    curl(t, -0.55F, 0.35F, sway);
                    ps.pushPose();
                    if (variant == 2) ps.scale(0.7F, 0.7F, 1.05F); // gato: cauda fina
                    t.render(ps, vc, light, overlay, FUR[variant]);
                    // ponta clara (raposa/lobo)
                    t.translateAndRotate(ps);
                    ModelPart s2 = t.getChild("seg2");
                    s2.translateAndRotate(ps);
                    ModelPart s3 = s2.getChild("seg3");
                    s3.translateAndRotate(ps);
                    ps.translate(0, 0, 3.6F / 16F);
                    m.getChild("beast_tip").render(ps, vc, light, overlay, FUR_TIP[variant]);
                    ps.popPose();
                }
            }
            case ARACHNID -> spiderLegs(ps, buffers, light, overlay, form != null, age, limbSwing, limbSwingAmount);
            default -> { }
        }
        ps.popPose();
        // Asinhas de penas do Anjo (na Forma Seráfica as grandes já foram desenhadas)
        if (race == Race.ANGEL && form == null) {
            drawWings(ps, buffers, light, overlay, p, ANGEL_WINGS, 0.18F, 0.55F, 0xF0FFFFFF, age, false);
        }

        // ---------- corpo inteiro: fantasma e geleia ----------
        if (race == Race.PHANTOM && form == null) {
            auraShell(ps, buffers, light, overlay, 0x48BFE8F4, 1.05F);
        } else if (race == Race.SLIME) {
            float wob = 1.06F + Mth.sin(age * 0.15F) * 0.012F;
            auraShell(ps, buffers, light, overlay, form != null ? 0x908FE05A : 0x588FE05A, form != null ? wob + 0.04F : wob);
        }
    }

    /** Quanto a forma está "aberta" (0..1): abre em 10 ticks e fecha nos 10 últimos. */
    private static float formOpen(AbstractClientPlayer p, ClientCharacter.Fx fx, float partialTick) {
        long now = p.level().getGameTime();
        float open = Mth.clamp((now - fx.start() + partialTick) / 10F, 0F, 1F);
        long left = fx.until() - now;
        if (left < 10) open = Math.min(open, Math.max(0F, (left - partialTick) / 10F));
        return open;
    }

    /** Visual das Transformações (asas, auras). */
    private void renderForm(PoseStack ps, MultiBufferSource buffers, int light, int overlay, AbstractClientPlayer p, Race race,
                            int ancestry, float open, float partialTick, float limbSwing, float limbSwingAmount) {
        if (open <= 0.01F) return;
        float age = p.tickCount + partialTick;
        float pulse = 0.5F + 0.5F * Mth.sin(age * 0.2F);
        int a = (int) (open * (0x28 + 0x18 * pulse));
        switch (race) {
            case ANGEL -> {
                drawWings(ps, buffers, LightTexture.FULL_BRIGHT, overlay, p, ANGEL_WINGS, open, 1.45F, 0xF8FFFFFF, age, true);
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, (a << 24) | 0xFFE08A, 1.07F);
            }
            case DEMON -> {
                drawWings(ps, buffers, light, overlay, p, WINGS_TEXTURE, open, 1.35F, 0xF0FFFFFF, age, true);
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, (a << 24) | 0xD8455B, 1.06F);
            }
            case DRAGONBORN -> {
                int tint = 0xF0000000 | mix(0xFFFFFF, ancestry, 0.75F);
                drawWings(ps, buffers, light, overlay, p, DRAGON_WINGS, open, 1.45F, tint, age, true);
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, (a << 24) | (ancestry & 0xFFFFFF), 1.05F);
            }
            case ELF -> {
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, (a << 24) | 0x7BD88F, 1.07F);
                ps.pushPose();
                getParentModel().head.translateAndRotate(ps);
                model2().getChild("crown").render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE2)), light, overlay, 0xFF6FCB5A);
                ps.popPose();
            }
            case DWARF -> auraShell(ps, buffers, light, overlay, ((int) (open * 0x90) << 24) | 0x9A8F80, 1.05F);
            case HALF_ORC -> auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (open * (0x30 + 0x30 * pulse)) << 24) | 0xC02020, 1.07F);
            case BEASTFOLK -> auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, (a << 24) | 0xD08A3A, 1.06F);
            case HUMAN -> auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (open * (0x30 + 0x20 * pulse)) << 24) | 0xFFD86B, 1.07F);
            case ARACHNID -> auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, (a << 24) | 0x9A6AD8, 1.05F);
            default -> { }
        }
    }

    /** Patas de aranha nas costas: 2 pares pequenos (base) ou 4 pares grandes que andam (Forma Aracne). */
    private void spiderLegs(PoseStack ps, MultiBufferSource buffers, int light, int overlay, boolean form, float age, float limbSwing,
                            float limbSwingAmount) {
        ModelPart leg = model2().getChild("spider_leg");
        ModelPart lower = leg.getChild("lower");
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE2));
        int pairs = form ? 4 : 2;
        float scale = form ? 1.25F : 0.6F;
        for (int i = 0; i < pairs; i++) {
            float y = form ? 1.5F + i * 2.6F : 3F + i * 3F;
            for (int s : new int[]{1, -1}) {
                float phase = (i + (s > 0 ? 0 : 1)) * 1.6F;
                float walk = Mth.sin(limbSwing * 0.9F + phase) * limbSwingAmount * (form ? 0.5F : 0.2F);
                float idle = Mth.sin(age * 0.07F + phase) * 0.06F;
                ps.pushPose();
                ps.translate(1.5F * s / 16F, y / 16F, 2.2F / 16F);
                ps.scale(scale, scale, scale);
                // Para o lado e um pouco para trás; pares da frente apontam para a frente, os de trás para trás
                float spread = form ? (-0.55F + i * 0.37F) : (-0.2F + i * 0.4F);
                leg.yRot = s > 0 ? -spread - walk : (float) Math.PI + spread + walk;
                // zRot é aplicado no espaço do corpo: o lado espelhado (yRot = π) precisa do sinal trocado para subir
                leg.zRot = s * (-0.55F - idle);
                leg.xRot = 0F;
                lower.zRot = 0.35F + idle;
                leg.render(ps, vc, light, overlay);
                ps.popPose();
            }
        }
    }

    /** Asas de sombra da skill Asas Sombrias (abrem em 8 ticks e fecham no fim). */
    private void renderShadowWings(PoseStack ps, MultiBufferSource buffers, int light, int overlay, AbstractClientPlayer p,
                                   ClientCharacter.Fx fx, float partialTick) {
        float open = Mth.clamp((p.level().getGameTime() - fx.start() + partialTick) / 8F, 0F, 1F);
        long left = fx.until() - p.level().getGameTime();
        if (left < 10) open = Math.min(open, Math.max(0F, (left - partialTick) / 10F));
        if (open <= 0.01F) return;
        drawWings(ps, buffers, light, overlay, p, WINGS_TEXTURE, open, 1F, 0xE8FFFFFF, p.tickCount + partialTick, true);
    }

    /**
     * Par de asas nas costas. {@code open} 0 = dobradas para trás e caídas, 1 = abertas para os lados batendo (rápido no
     * ar, devagar no chão); {@code scale} cresce a partir da raiz da asa.
     */
    private void drawWings(PoseStack ps, MultiBufferSource buffers, int light, int overlay, AbstractClientPlayer p, ResourceLocation tex,
                           float open, float scale, int color, float t, boolean flap) {
        boolean air = !p.onGround();
        float f = !flap ? Mth.sin(t * 0.08F) * 0.06F : air ? Mth.sin(t * 0.55F) * 0.45F : Mth.sin(t * 0.12F) * 0.12F;
        ModelPart m = wingModel();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
        ps.pushPose();
        getParentModel().body.translateAndRotate(ps);
        ps.translate(0, 2.5F / 16F, 2.2F / 16F);
        ps.scale(scale, scale, scale);
        ps.translate(0, -2.5F / 16F, -2.2F / 16F);
        for (String side : new String[]{"l", "r"}) {
            float s = side.equals("l") ? 1F : -1F;
            ModelPart w = m.getChild("wing_" + side);
            // (rotationZYX: yRot positivo leva +x para a FRENTE, por isso o sinal −s varre as asas para trás; no modelo,
            //  y cresce para baixo, então zRot −s ergue a asa.)
            w.yRot = -s * Mth.lerp(open, 1.25F, 0.45F + f);
            w.zRot = s * Mth.lerp(open, 0.55F, -0.30F - f * 0.5F);
            w.xRot = 0.12F;
            ModelPart tip = w.getChild("tip");
            tip.yRot = -s * Mth.lerp(open, 1.2F, 0.30F + f * 0.6F);
            w.render(ps, vc, light, overlay, color);
        }
        ps.popPose();
    }

    /** Casca translúcida em volta do corpo inteiro (textura branca tingida): fantasma, geleia, auras das formas. */
    private void auraShell(PoseStack ps, MultiBufferSource buffers, int light, int overlay, int argb, float grow) {
        if ((argb >>> 24) < 4) return;
        ps.pushPose();
        ps.translate(0, 0.75F, 0);
        ps.scale(grow, grow, grow);
        ps.translate(0, -0.75F, 0);
        getParentModel().renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(AURA)), light, overlay, argb);
        ps.popPose();
    }

    /** O próprio skin translúcido (Forma Espectral, quando o corpo de verdade está invisível). */
    private void skinShell(PoseStack ps, MultiBufferSource buffers, int light, int overlay, AbstractClientPlayer p, int argb, float grow) {
        ResourceLocation skin;
        try {
            skin = getTextureLocation(p);
        } catch (RuntimeException e) {
            return;
        }
        if (skin == null) return;
        ps.pushPose();
        ps.translate(0, 0.75F, 0);
        ps.scale(grow, grow, grow);
        ps.translate(0, -0.75F, 0);
        getParentModel().renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, overlay, argb);
        ps.popPose();
    }

    /** Cauda descendo da lombar e curvando para cima, balançando para os lados. */
    private static void curl(ModelPart t, float base, float bend, float sway) {
        t.xRot = base;
        t.yRot = sway;
        ModelPart s2 = t.getChild("seg2");
        s2.xRot = bend;
        s2.yRot = sway * 0.8F;
        ModelPart s3 = s2.getChild("seg3");
        s3.xRot = bend * 1.1F;
        s3.yRot = sway * 0.6F;
    }

    /** Tom de pele (Meio-Orc verde, Demônio avermelhado, Dracônico escamas na cor da ancestralidade): cabeça e braços. */
    private void skinTint(PoseStack ps, MultiBufferSource buffers, int light, int overlay, AbstractClientPlayer p, Race race, int ancestry) {
        int argb = switch (race) {
            case HALF_ORC -> 0x807FB85A;
            case DEMON -> 0x58E06868;
            case DRAGONBORN -> 0x70000000 | (ancestry & 0xFFFFFF);
            case ARACHNID -> 0x389A6AD8;
            default -> 0;
        };
        if (argb == 0) return;
        ResourceLocation skin;
        try {
            skin = getTextureLocation(p);
        } catch (RuntimeException e) {
            return;
        }
        if (skin == null) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(skin));
        PlayerModel<AbstractClientPlayer> pm = getParentModel();
        if (pm.head.visible) pm.head.render(ps, vc, light, overlay, argb);
        if (pm.leftArm.visible) pm.leftArm.render(ps, vc, light, overlay, argb);
        if (pm.rightArm.visible) pm.rightArm.render(ps, vc, light, overlay, argb);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
