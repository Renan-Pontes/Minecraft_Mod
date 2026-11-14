package com.renanpontes.isekaimahou.feature.character.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
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
 * <b>Transformações</b> ({@link ClientCharacter#form}): v1.11 Jibril, a Flügel (Anjo: asas de penas de luz branco → rosa →
 * violeta e a auréola geométrica girando, desenhadas com quads), asas de sombra
 * (Demônio), asas dracônicas na cor da ancestralidade (Dracônico), corpo fantasma translúcido mesmo invisível
 * (Fantasma), 8 patas de aranha andando (Aracnídeo), geleia densa (Slime), aura na cor da raça (Elfo com
 * coroa de folhas, Anão de pedra, Meio-Orc rubra, Bestial, Humano dourada). O tamanho vem do atributo de escala.
 * <p>
 * As <b>Asas Sombrias</b> (skill racial do Demônio) continuam: membrana translúcida abrindo em 8 ticks e batendo.
 * <p>
 * <b>v1.3 — formas "grandes e temíveis"</b> (peças de {@link #model3()}, desenhadas com a textura branca tingida):
 * olhos brilhando em todas; Demônio com chifres enormes em brasa e asas maiores; Dracônico com escamas brilhando
 * (pulsando na cor da ancestralidade) e chifres maiores; Anjo (v1.11: Jibril) com asas de penas de luz e a auréola
 * geométrica; Elfo espírito da lua (aura prateada, runas orbitando, flutuando — {@link FormFx}); Anão
 * golem de pedra (pele de rocha, rachaduras de magma pulsando, ombreiras de pedra); Meio-Orc berserker (pele rubra,
 * presas maiores); Bestial lobisomem (pelagem, garras, orelhas maiores); Aracnídeo com abdômen de aranha e patas
 * maiores; Fantasma com correntes espectrais; v1.7: Lorde Demônio (Rimuru) humanoide de olhos dourados; Humano "Herói Desperto"
 * com o estandarte de luz nas costas. Na carga da Transformação o corpo brilha cada vez mais.
 * <p>
 * Os traços de base crescem um pouco com o nível de raça (I–V): chifres e caudas maiores, olhos que brilham no IV+.
 */
public class RaceFeatureLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/character/race_features.png");
    private static final ResourceLocation TEXTURE2 = IsekaiMahou.id("textures/entity/character/race_features2.png");
    private static final ResourceLocation WINGS_TEXTURE = IsekaiMahou.id("textures/entity/character/shadow_wings.png");
    private static final ResourceLocation ANGEL_WINGS = IsekaiMahou.id("textures/entity/character/angel_wings.png");
    private static final ResourceLocation DRAGON_WINGS = IsekaiMahou.id("textures/entity/character/dragon_wings.png");
    private static final ResourceLocation AURA = IsekaiMahou.id("textures/entity/character/aura.png");
    private static final ResourceLocation GOLEM = IsekaiMahou.id("textures/entity/character/golem_skin.png");
    private static final ResourceLocation MOLTEN = IsekaiMahou.id("textures/entity/character/molten_cracks.png");
    private static final ResourceLocation FUR_SHELL = IsekaiMahou.id("textures/entity/character/fur_shell.png");
    private static final int[] BEARD = {0xFF6B4226, 0xFFB5532A, 0xFF2B2420, 0xFF9A9AA0};
    private static final int[] FUR = {0xFFD9782F, 0xFF8E949E, 0xFF3B3442, 0xFFE8E0D6};
    private static final int[] FUR_TIP = {0xFFF4EEE6, 0xFFDADDE2, 0xFF57506A, 0xFFFFFFFF};

    private static ModelPart root;
    private static ModelPart root2;
    private static ModelPart wings;
    private static ModelPart root3;
    /** Partial tick do quadro em desenho (as asas leem para a batida e a abertura do planar). */
    private float pt;

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
        this.pt = partialTick;
        ClientCharacter.Look look = ClientCharacter.look(p.getId());
        Race race = look == null ? null : look.race();
        ClientCharacter.Fx form = race == null ? null : ClientCharacter.form(p.getId());
        if (p.isInvisible()) {
            // Forma Espectral: o corpo fica invisível para as criaturas, mas os jogadores veem o fantasma translúcido
            if (race == Race.PHANTOM && form != null) {
                float open = formOpen(p, form, partialTick);
                skinShell(ps, buffers, light, overlay, p, ((int) (0x66 * open) << 24) | 0xE8F8FF, 1.0F);
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (0x50 * open) << 24) | 0x9FD8E8, 1.06F);
                glowEyes(ps, buffers, overlay, 0x9FF4FF, open);
                chains(ps, buffers, overlay, p.tickCount + partialTick, open);
            }
            return;
        }
        ClientCharacter.Fx wingFx = ClientCharacter.wings(p.getId());
        if (wingFx != null) renderShadowWings(ps, buffers, light, overlay, p, wingFx, partialTick);
        if (race == null) return;
        float formOpen = form == null ? 0F : formOpen(p, form, partialTick);
        int variant = Math.floorMod(p.getUUID().hashCode(), 4);
        if (form != null) renderForm(ps, buffers, light, overlay, p, race, look.ancestryRgb(), formOpen, partialTick, limbSwing, limbSwingAmount, variant);
        // Carga da Transformação: o corpo brilha cada vez mais forte (e os olhos acendem)
        float charge = FormFx.chargeProgress(p, partialTick);
        if (charge >= 0F) {
            int rgb = FormFx.color(p, race);
            float beat = 0.5F + 0.5F * Mth.sin(age * (0.3F + charge * 0.9F));
            auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) ((0x20 + 0x60 * charge) * (0.6F + 0.4F * beat)) << 24) | rgb,
                    1.04F + 0.06F * charge);
            glowEyes(ps, buffers, overlay, FormFx.mix(rgb, 0xFFFFFF, 0.3F), charge);
        }
        if (race == Race.HUMAN) return;
        // Traços de base crescem com o nível de raça (I = 90%, V = 110%)
        int lv = look.level();
        float grow = lv <= 0 ? 1F : 0.85F + 0.05F * lv;
        if (form != null) grow = Math.max(grow, 1F);
        // Nível IV+: olhos com um brilho discreto (as formas já acendem os olhos)
        if (form == null && charge < 0F && lv >= 4) {
            int eye = switch (race) {
                case DEMON -> 0xFF5040;
                case DRAGONBORN -> look.ancestryRgb() & 0xFFFFFF;
                case BEASTFOLK -> 0xFFC840;
                case PHANTOM -> 0x9FF4FF;
                default -> -1;
            };
            if (eye >= 0) glowEyes(ps, buffers, overlay, eye, lv >= 5 ? 0.55F : 0.35F);
        }

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
                float big = form != null ? 1F + 0.5F * formOpen : 1F;
                scaledPart(ps, m.getChild("orc_tusk_l"), vc, light, overlay, 0xFFFFFFFF, big);
                scaledPart(ps, m.getChild("orc_tusk_r"), vc, light, overlay, 0xFFFFFFFF, big);
            }
            case DEMON -> {
                // Na Forma Demoníaca os chifres ficam enormes e em brasa
                float big = form != null ? grow + 0.8F * formOpen : grow;
                int tint = form != null ? 0xFF000000 | mix(0xFFFFFF, 0xFF6040, 0.45F * formOpen) : 0xFFFFFFFF;
                int hornLight = form != null ? LightTexture.FULL_BRIGHT : light;
                scaledPart(ps, m.getChild("demon_horn_l"), vc, hornLight, overlay, tint, big);
                scaledPart(ps, m.getChild("demon_horn_r"), vc, hornLight, overlay, tint, big);
            }
            case DRAGONBORN -> {
                int horn = 0xFF000000 | mix(0xE8DCC0, look.ancestryRgb(), form != null ? 0.45F : 0.25F);
                float big = form != null ? grow + 0.5F * formOpen : grow;
                scaledPart(ps, m.getChild("dragon_horn_l"), vc, light, overlay, horn, big);
                scaledPart(ps, m.getChild("dragon_horn_r"), vc, light, overlay, horn, big);
            }
            case BEASTFOLK -> {
                String ear = variant == 3 ? "rabbit_ear_" : "beast_ear_";
                float twitch = (p.tickCount + p.getId() * 7) % 90 < 4 ? 0.25F : 0F;
                float big = form != null ? 1F + 0.35F * formOpen : 1F;
                for (String side : new String[]{"l", "r"}) {
                    ModelPart e = m.getChild(ear + side);
                    float base = e.zRot;
                    e.zRot = base + (side.equals("l") ? twitch : -twitch) + (form != null ? (side.equals("l") ? 0.15F : -0.15F) : 0F);
                    scaledPart(ps, e, vc, light, overlay, FUR[variant], big);
                    e.zRot = base;
                }
            }
            case ARACHNID -> m2.getChild("spider_eyes").render(ps, buffers.getBuffer(form != null
                    ? RenderType.entityTranslucentEmissive(TEXTURE2) : RenderType.entityCutoutNoCull(TEXTURE2)), LightTexture.FULL_BRIGHT, overlay);
            case ANGEL -> {
                float bob = Mth.sin(age * 0.08F) * 0.35F;
                if (form != null) {
                    // v1.11 Jibril, a Flügel: a auréola geométrica grande (anéis, raios, hexagrama) girando sobre a cabeça
                    jibrilHalo(ps, buffers, overlay, age, formOpen, bob);
                } else {
                    // Auréola dourada flutuando (brilha no escuro)
                    ps.pushPose();
                    ps.translate(0, (-10.2F + bob) / 16F, 0);
                    ModelPart halo = m2.getChild("halo");
                    halo.yRot = 0F;
                    // A auréola fica mais viva com o nível de raça
                    int haloA = lv <= 0 ? 0xF0 : 0xA0 + 0x14 * lv;
                    halo.render(ps, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE2)), LightTexture.FULL_BRIGHT, overlay,
                            (Math.min(0xFF, haloA) << 24) | 0xFFFFFF);
                    ps.popPose();
                }
            }
            // v1.7: o Despertar do Lorde Demônio (Rimuru) é humanoide e sem coroa — o brilho fica nos olhos (renderForm)
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
                ps.pushPose();
                ps.translate(0, 10.5F / 16F, 1.6F / 16F);
                float tg = form != null ? grow + 0.5F * formOpen : grow;
                ps.scale(tg, tg, tg);
                ps.translate(0, -10.5F / 16F, -1.6F / 16F);
                t.render(ps, vc, light, overlay, 0xFF2A1A22);
                // ponta de lança no fim da cauda
                ps.pushPose();
                t.translateAndRotate(ps);
                ModelPart s2 = t.getChild("seg2");
                s2.translateAndRotate(ps);
                ModelPart s3 = s2.getChild("seg3");
                s3.translateAndRotate(ps);
                ps.translate(0, 0, 3.6F / 16F);
                m.getChild("spade").render(ps, vc, form != null ? LightTexture.FULL_BRIGHT : light, overlay, 0xFFB0303C);
                ps.popPose();
                ps.popPose();
            }
            case DRAGONBORN -> {
                ModelPart t = m.getChild("dragon_tail");
                curl(t, -0.75F, 0.25F, sway * 0.7F);
                ps.pushPose();
                ps.translate(0, 10.5F / 16F, 1.6F / 16F);
                float tg = form != null ? grow + 0.4F * formOpen : grow;
                ps.scale(tg, tg, tg);
                ps.translate(0, -10.5F / 16F, -1.6F / 16F);
                t.render(ps, vc, light, overlay, 0xFF000000 | look.ancestryRgb());
                ps.popPose();
            }
            case BEASTFOLK -> {
                if (variant == 3) {
                    m.getChild("rabbit_puff").render(ps, vc, light, overlay, FUR_TIP[variant]);
                } else {
                    ModelPart t = m.getChild("beast_tail");
                    curl(t, -0.55F, 0.35F, sway * (form != null ? 1.6F : 1F));
                    ps.pushPose();
                    if (form != null) {
                        float tg = 1F + 0.4F * formOpen;
                        ps.translate(0, 10.5F / 16F, 1.6F / 16F);
                        ps.scale(tg, tg, tg);
                        ps.translate(0, -10.5F / 16F, -1.6F / 16F);
                    }
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
            case ARACHNID -> {
                // v1.7 (Kumoko): o corpo evolui com o nível de raça — Pequena Taratect (1 par de patinhas) → Zana Horowa
                // (3 pares e um abdômen pequeno) → Arachne (a forma: 4 pares e o abdômen inteiro)
                spiderLegs(ps, buffers, light, overlay, form != null, lv, age, limbSwing, limbSwingAmount, formOpen);
                if (form != null) abdomen(ps, buffers, light, overlay, formOpen);
                else if (lv >= 4) abdomen(ps, buffers, light, overlay, 0.42F);
            }
            default -> { }
        }
        ps.popPose();
        // Asinhas de penas do Anjo (como Jibril, as grandes de penas já foram desenhadas)
        if (race == Race.ANGEL && form == null) {
            drawWings(ps, buffers, light, overlay, p, ANGEL_WINGS, 0.18F, 0.55F, 0xF0FFFFFF, age, false);
        }

        // ---------- corpo inteiro: fantasma e geleia ----------
        if (race == Race.PHANTOM && form == null) {
            auraShell(ps, buffers, light, overlay, 0x48BFE8F4, 1.05F);
        } else if (race == Race.SLIME && form == null) {
            // Geleia que balança (na forma humanoide do Lorde Demônio, só a aura azul do renderForm)
            float wob = 1.06F + Mth.sin(age * 0.15F) * 0.012F;
            auraShell(ps, buffers, light, overlay, 0x588FE05A, wob);
        }
    }

    /** Quanto a forma está "aberta" (0..1): abre em 10 ticks e se dissipa nos últimos {@link ClientCharacter#FADE}. */
    private static float formOpen(AbstractClientPlayer p, ClientCharacter.Fx fx, float partialTick) {
        long now = p.level().getGameTime();
        float open = Mth.clamp((now - fx.start() + partialTick) / 10F, 0F, 1F);
        long left = fx.until() - now;
        float fade = ClientCharacter.FADE - 2;
        if (left < fade) open = Math.min(open, Math.max(0F, (left - partialTick) / fade));
        return open;
    }

    /** Visual das Transformações (asas, auras, corpo da forma). */
    private void renderForm(PoseStack ps, MultiBufferSource buffers, int light, int overlay, AbstractClientPlayer p, Race race,
                            int ancestry, float open, float partialTick, float limbSwing, float limbSwingAmount, int variant) {
        if (open <= 0.01F) return;
        float age = p.tickCount + partialTick;
        float pulse = 0.5F + 0.5F * Mth.sin(age * 0.2F);
        int a = (int) (open * (0x28 + 0x18 * pulse));
        boolean rich = FxQuality.atLeast(Graphics.HIGH);
        switch (race) {
            case ANGEL -> {
                // v1.11 Jibril, a Flügel: um par grande de asas de PENAS de luz (fileiras de penas, branco → rosa → violeta);
                // no ALTO/ULTRA, um segundo par menor por baixo e o brilho em volta das penas compridas
                drawWings(ps, buffers, LightTexture.FULL_BRIGHT, overlay, p, null, open, 1.6F, 0xF0FFFFFF, age, true, 0.3F, -1.0F);
                if (rich) drawWings(ps, buffers, LightTexture.FULL_BRIGHT, overlay, p, null, open, 1.05F, 0xB8FFFFFF, age + 4F, true, -0.5F, 2.5F);
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, (a << 24) | 0xFFE6F4, 1.07F);
                glowEyes(ps, buffers, overlay, 0xFFD860, open);
            }
            case DEMON -> {
                drawWings(ps, buffers, light, overlay, p, WINGS_TEXTURE, open, 1.75F, 0xF8FFFFFF, age, true, 0.1F, 0F);
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (open * (0x30 + 0x30 * pulse)) << 24) | 0xD02A20, 1.06F);
                if (rich) auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (open * 0x22) << 24) | 0xFF7A20, 1.12F + 0.02F * pulse);
                glowEyes(ps, buffers, overlay, 0xFF3020, open);
            }
            case DRAGONBORN -> {
                int tint = 0xF0000000 | mix(0xFFFFFF, ancestry, 0.75F);
                drawWings(ps, buffers, light, overlay, p, DRAGON_WINGS, open, 1.75F, tint, age, true, 0.05F, 0F);
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, (a << 24) | (ancestry & 0xFFFFFF), 1.05F);
                // Escamas brilhando: a pele pulsa na cor da ancestralidade
                skinGlow(ps, buffers, overlay, p, ((int) (open * (0x40 + 0x50 * pulse)) << 24) | mix(ancestry, 0xFFFFFF, 0.2F));
                glowEyes(ps, buffers, overlay, mix(ancestry, 0xFFFFFF, 0.4F), open);
            }
            case ELF -> {
                // Espírito da lua: aura prateada, coroa, runas orbitando (o corpo flutua — FormFx)
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, (a << 24) | 0xBFF0FF, 1.07F);
                if (rich) auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (open * 0x18) << 24) | 0x7BD88F, 1.13F);
                ps.pushPose();
                getParentModel().head.translateAndRotate(ps);
                model2().getChild("crown").render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE2)), light, overlay, 0xFF6FCB5A);
                ps.popPose();
                runes(ps, buffers, overlay, age, open);
                glowEyes(ps, buffers, overlay, 0xBFF6FF, open);
            }
            case DWARF -> {
                // Golem de pedra: pele de rocha, magma pulsando nas rachaduras, ombreiras
                textureShell(ps, buffers, RenderType.entityCutoutNoCull(GOLEM), light, overlay, ((int) (open * 0xFF) << 24) | 0xFFFFFF, 1.035F);
                textureShell(ps, buffers, RenderType.entityTranslucentEmissive(MOLTEN), LightTexture.FULL_BRIGHT, overlay,
                        ((int) (open * (0x90 + 0x6F * pulse)) << 24) | 0xFFFFFF, 1.04F);
                pauldrons(ps, buffers, light, overlay, open);
                glowEyes(ps, buffers, overlay, 0xFF9A30, open);
            }
            case HALF_ORC -> {
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (open * (0x30 + 0x30 * pulse)) << 24) | 0xC02020, 1.07F);
                skinGlow(ps, buffers, overlay, p, ((int) (open * 0x50) << 24) | 0x8A1010);
                glowEyes(ps, buffers, overlay, 0xFF2020, open);
            }
            case BEASTFOLK -> {
                // Lobisomem: pelagem por cima do corpo, garras, olhos de fera
                int fur = FUR[variant] & 0xFFFFFF;
                textureShell(ps, buffers, RenderType.entityTranslucent(FUR_SHELL), light, overlay, ((int) (open * 0xE0) << 24) | fur, 1.06F);
                if (rich) textureShell(ps, buffers, RenderType.entityTranslucent(FUR_SHELL), light, overlay, ((int) (open * 0x80) << 24) | fur, 1.1F);
                claws(ps, buffers, light, overlay, open);
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (a * 0.6F) << 24) | 0xD08A3A, 1.12F);
                glowEyes(ps, buffers, overlay, 0xFFD040, open);
            }
            case HUMAN -> {
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (open * (0x30 + 0x20 * pulse)) << 24) | 0xFFD86B, 1.07F);
                if (rich) auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (open * 0x18) << 24) | 0xFFF2C0, 1.14F + 0.02F * pulse);
                banner(ps, buffers, overlay, age, open);
                glowEyes(ps, buffers, overlay, 0xFFE89A, open);
            }
            case ARACHNID -> auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, (a << 24) | 0x9A6AD8, 1.05F);
            case SLIME -> {
                // v1.7 Despertar do Lorde Demônio (Rimuru): forma humanoide — aura azul-prateada, um véu escuro de Lorde
                // Demônio por fora e os olhos dourados (o corpo gigante de geleia do antigo Rei Slime saiu)
                auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (open * (0x30 + 0x20 * pulse)) << 24) | 0x7FD4FF, 1.05F);
                if (rich) auraShell(ps, buffers, LightTexture.FULL_BRIGHT, overlay, ((int) (open * 0x22) << 24) | 0x3A2A6A, 1.12F + 0.02F * pulse);
                glowEyes(ps, buffers, overlay, 0xFFD040, open);
            }
            default -> { }
        }
    }

    /** Patas de aranha nas costas: 2 pares pequenos (base) ou 4 pares grandes que andam (Forma Aracne). */
    private void spiderLegs(PoseStack ps, MultiBufferSource buffers, int light, int overlay, boolean form, int lv, float age,
                            float limbSwing, float limbSwingAmount, float open) {
        ModelPart leg = model2().getChild("spider_leg");
        ModelPart lower = leg.getChild("lower");
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE2));
        // Evolução (v1.7): I = 1 par miúdo, II–III = 2 pares crescendo, IV+ = 3 pares; a forma Arachne = 4 pares
        int pairs = form ? 4 : lv <= 1 ? 1 : lv >= 4 ? 3 : 2;
        float scale = form ? 0.6F + 1.0F * open : lv <= 1 ? 0.45F : lv == 2 ? 0.52F : lv == 3 ? 0.6F : 0.66F;
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
     * ar, devagar no chão); {@code scale} cresce a partir da raiz da asa. v1.3.1: planando (pose de élitro), as asas
     * ficam abertas e retas como as de uma ave planando, tremendo com o vento; cada batida de asas
     * ({@link ClientCharacter#beatPhase}) ergue e desce as asas num golpe grande.
     */
    private void drawWings(PoseStack ps, MultiBufferSource buffers, int light, int overlay, AbstractClientPlayer p, ResourceLocation tex,
                           float open, float scale, int color, float t, boolean flap) {
        drawWings(ps, buffers, light, overlay, p, tex, open, scale, color, t, flap, 0F, 0F);
    }

    /** Igual, com {@code lift} (rad: ergue/baixa o par) e {@code yOff} (pixels: sobe/desce a raiz) — as 6 asas do Anjo. */
    private void drawWings(PoseStack ps, MultiBufferSource buffers, int light, int overlay, AbstractClientPlayer p, ResourceLocation tex,
                           float open, float scale, int color, float t, boolean flap, float lift, float yOff) {
        boolean air = !p.onGround();
        float f = !flap ? Mth.sin(t * 0.08F) * 0.06F : air ? Mth.sin(t * 0.55F) * 0.45F : Mth.sin(t * 0.12F) * 0.12F;
        // Planar: entra em ~6 ticks (junto com a pose de élitro do vanilla) e respeita a forma abrindo/fechando
        float glide = p.isFallFlying() ? Mth.clamp((p.getFallFlyingTicks() + pt) / 6F, 0F, 1F) * open : 0F;
        // Batida: primeiro ergue (−), depois desce com força (+); 0 fora da batida
        float beat = flap ? ClientCharacter.beatPhase(p.getId(), p.level().getGameTime(), pt) : -1F;
        float stroke = beat < 0F ? 0F : -(beat < 0.5F ? 0.75F : 0.95F) * Mth.sin(beat * (float) (Math.PI * 2));
        float flutter = Mth.sin(t * 1.3F) * 0.025F;
        ModelPart m = wingModel();
        // tex == null: asas de PENAS de luz da Jibril (quads emissivos desenhados na hora, mesmo esqueleto e animação)
        boolean feathers = tex == null;
        VertexConsumer vc = buffers.getBuffer(feathers ? RenderType.entityTranslucentEmissive(AURA) : RenderType.entityTranslucent(tex));
        int q = !feathers ? 0 : FxQuality.atLeast(Graphics.HIGH) ? 2 : FxQuality.atLeast(Graphics.MEDIUM) ? 1 : 0;
        float featherAlpha = ((color >>> 24) / 255F) * Mth.clamp(open * 1.6F, 0F, 1F);
        ps.pushPose();
        getParentModel().body.translateAndRotate(ps);
        ps.translate(0, (2.5F + yOff) / 16F, 2.2F / 16F);
        ps.scale(scale, scale, scale);
        ps.translate(0, -2.5F / 16F, -2.2F / 16F);
        for (String side : new String[]{"l", "r"}) {
            float s = side.equals("l") ? 1F : -1F;
            ModelPart w = m.getChild("wing_" + side);
            // (rotationZYX: yRot positivo leva +x para a FRENTE, por isso o sinal −s varre as asas para trás; no modelo,
            //  y cresce para baixo, então zRot −s ergue a asa.)
            float baseY = -s * Mth.lerp(open, 1.25F, 0.45F + f);
            float baseZ = s * Mth.lerp(open, 0.55F, -0.30F - f * 0.5F - lift + stroke);
            // Planando: quase retas para os lados (o corpo já está deitado pela pose de élitro), leve diedro para cima
            float glideY = -s * (0.12F + flutter);
            float glideZ = s * (-0.06F - lift * 0.4F + stroke + flutter);
            w.yRot = Mth.lerp(glide, baseY, glideY);
            w.zRot = Mth.lerp(glide, baseZ, glideZ);
            w.xRot = Mth.lerp(glide, 0.12F, 0.04F);
            ModelPart tip = w.getChild("tip");
            // A ponta dobra para trás na subida da batida (menos arrasto) e abre na descida
            float tipGlide = -s * (0.06F + Math.max(0F, -stroke) * 0.6F);
            tip.yRot = Mth.lerp(glide, -s * Mth.lerp(open, 1.2F, 0.30F + f * 0.6F), tipGlide);
            if (feathers) {
                if (featherAlpha <= 0.02F) continue;
                ps.pushPose();
                w.translateAndRotate(ps);
                featherWing(ps, vc, overlay, s, false, t, featherAlpha, q);
                tip.translateAndRotate(ps);
                featherWing(ps, vc, overlay, s, true, t, featherAlpha, q);
                ps.popPose();
            } else {
                w.render(ps, vc, light, overlay, color);
            }
        }
        ps.popPose();
    }

    // =====================================================================================
    //  v1.11: Jibril, a Flügel (Anjo) — asas de penas de luz e a auréola geométrica (quads na hora, sem textura nova)
    // =====================================================================================
    private static final int JB_GOLD = 0xFFD860, JB_CYAN = 0x7FF4FF, JB_PALE = 0xFFF4C0;

    /**
     * Penas de uma metade da asa (no espaço do osso, em pixels: o osso corre para fora em +x·{@code s}, as penas pendem
     * em +y). {@code tip} = a ponta (primárias compridas abrindo em leque); senão a parte de dentro (coberteiras,
     * secundárias e as compridas). {@code q}: 0 = gráfico baixo (menos penas), 1 = médio, 2 = alto/ultra (+ brilho).
     */
    private static void featherWing(PoseStack ps, VertexConsumer vc, int overlay, float s, boolean tip, float t, float alpha, int q) {
        ps.pushPose();
        ps.scale(1F / 16F, 1F / 16F, 1F / 16F);
        PoseStack.Pose pose = ps.last();
        if (!tip) {
            if (q >= 1) featherRow(vc, pose, overlay, s, 6, 0F, 12F, 4.0F, 3.5F, 2.6F, 0.05F, 0.20F, 0xFFFFFF, 0xFFE8F6, 0.75F, alpha, t, 0, false);
            featherRow(vc, pose, overlay, s, q >= 1 ? 7 : 5, 1.0F, 12F, 7.0F, 7.5F, 2.3F, 0.08F, 0.28F, 0xFFF6FB, 0xF4A6D8, 0.6F, alpha, t, 1, false);
            featherRow(vc, pose, overlay, s, q >= 1 ? 7 : 5, 2.0F, 12F, 9.0F, 10.0F, 2.0F, 0.10F, 0.35F, 0xFBD8F0, 0xB98AF2, 0.45F, alpha, t, 2, q >= 2);
        } else {
            if (q >= 1) featherRow(vc, pose, overlay, s, 4, 0F, 8F, 3.5F, 3.0F, 2.4F, 0.25F, 0.45F, 0xFFFFFF, 0xFFE8F6, 0.75F, alpha, t, 3, false);
            featherRow(vc, pose, overlay, s, q >= 1 ? 6 : 4, 0.6F, 8F, 11.0F, 8.0F, 2.0F, 0.35F, 0.95F, 0xFFF0FA, 0xA070F0, 0.45F, alpha, t, 4, q >= 2);
        }
        ps.popPose();
    }

    /** Uma fileira de {@code n} penas ao longo do osso (comprimento e ângulo indo de {@code 0} a {@code 1} para fora). */
    private static void featherRow(VertexConsumer vc, PoseStack.Pose pose, int overlay, float s, int n, float y, float span,
                                   float len0, float len1, float w, float th0, float th1, int c0, int c1, float z, float alpha,
                                   float t, int seed, boolean glow) {
        for (int k = 0; k < n; k++) {
            float f = n <= 1 ? 0F : k / (float) (n - 1);
            float rx = 0.6F + f * (span - 1.2F);
            float th = th0 + (th1 - th0) * f + Mth.sin(t * 0.15F + k * 0.9F + seed * 1.7F) * 0.035F;
            float len = len0 + (len1 - len0) * f;
            if (glow) feather(vc, pose, overlay, s, rx, y, z - 0.12F, th, len * 1.08F, w * 1.7F, 0xFFFFFF, c1, alpha * 0.22F);
            feather(vc, pose, overlay, s, rx, y, z, th, len, w, c0, c1, alpha);
        }
    }

    /** Uma pena de luz: raiz estreita → meio largo → ponta fina, com degradê {@code c0} → {@code c1}. */
    private static void feather(VertexConsumer vc, PoseStack.Pose pose, int overlay, float s, float rx, float ry, float z,
                                float theta, float len, float w, int c0, int c1, float alpha) {
        float dx = Mth.sin(theta), dy = Mth.cos(theta);
        float nx = dy, ny = -dx;
        float mx = rx + dx * len * 0.45F, my = ry + dy * len * 0.45F;
        float tx = rx + dx * len, ty = ry + dy * len;
        float w0 = w * 0.3F, w1 = w * 0.5F, w2 = w * 0.06F;
        int k0 = argb(c0, alpha), km = argb(mix(c0, c1, 0.45F), alpha), k1 = argb(c1, alpha * 0.85F);
        fv(vc, pose, overlay, s * (rx - nx * w0), ry - ny * w0, z, k0);
        fv(vc, pose, overlay, s * (rx + nx * w0), ry + ny * w0, z, k0);
        fv(vc, pose, overlay, s * (mx + nx * w1), my + ny * w1, z, km);
        fv(vc, pose, overlay, s * (mx - nx * w1), my - ny * w1, z, km);
        fv(vc, pose, overlay, s * (mx - nx * w1), my - ny * w1, z, km);
        fv(vc, pose, overlay, s * (mx + nx * w1), my + ny * w1, z, km);
        fv(vc, pose, overlay, s * (tx + nx * w2), ty + ny * w2, z, k1);
        fv(vc, pose, overlay, s * (tx - nx * w2), ty - ny * w2, z, k1);
    }

    /**
     * A auréola da Jibril: anel de ouro grande e anel ciano de dentro girando em sentidos opostos, raios ligando os dois,
     * um hexagrama (2 triângulos) no meio e um anelzinho branco no centro; no ALTO/ULTRA, brilho, anel tracejado de fora,
     * losangos e pontas triangulares. Inclinada sobre a cabeça, um pouco para trás; cresce com a forma abrindo.
     */
    private static void jibrilHalo(PoseStack ps, MultiBufferSource buffers, int overlay, float age, float open, float bob) {
        if (open <= 0.01F) return;
        int q = FxQuality.atLeast(Graphics.HIGH) ? 2 : FxQuality.atLeast(Graphics.MEDIUM) ? 1 : 0;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(AURA));
        ps.pushPose();
        ps.translate(0, (-12.5F + bob) / 16F, 2.5F / 16F);
        ps.mulPose(Axis.XP.rotationDegrees(62));
        float sc = (0.35F + 0.65F * open) / 16F;
        ps.scale(sc, sc, sc);
        PoseStack.Pose pose = ps.last();
        float spin = age * 0.045F;
        float a = open;
        int segs = q == 2 ? 56 : q == 1 ? 40 : 28;
        float rOut = 7.2F, rIn = 4.4F;
        if (q == 2) ringQ(vc, pose, overlay, rOut, 2.6F, segs, 0F, false, -0.05F, argb(JB_GOLD, 0.16F * a));
        ringQ(vc, pose, overlay, rOut, 0.7F, segs, spin, false, 0F, argb(JB_GOLD, 0.95F * a));
        ringQ(vc, pose, overlay, rIn, 0.45F, segs, -spin, false, 0F, argb(JB_CYAN, 0.9F * a));
        if (q >= 1) {
            // Raios entre os dois anéis
            for (int i = 0; i < 8; i++) {
                float an = spin + (float) (Math.PI * 2 * i / 8);
                float c = Mth.cos(an), sn = Mth.sin(an);
                lineQ(vc, pose, overlay, c * rIn, sn * rIn, c * rOut, sn * rOut, 0.02F, i % 2 == 0 ? 0.35F : 0.22F,
                        argb(i % 2 == 0 ? JB_PALE : JB_GOLD, 0.85F * a));
            }
            // Hexagrama inscrito no anel de dentro
            float hs = -spin * 1.6F;
            for (int tri = 0; tri < 2; tri++) {
                for (int i = 0; i < 3; i++) {
                    float a0 = hs + tri * (float) Math.PI / 3F + (float) (Math.PI * 2 * i / 3);
                    float a1 = hs + tri * (float) Math.PI / 3F + (float) (Math.PI * 2 * (i + 1) / 3);
                    float r = rIn - 0.2F;
                    lineQ(vc, pose, overlay, Mth.cos(a0) * r, Mth.sin(a0) * r, Mth.cos(a1) * r, Mth.sin(a1) * r, 0.04F, 0.28F,
                            argb(tri == 0 ? JB_GOLD : JB_CYAN, 0.8F * a));
                }
            }
            ringQ(vc, pose, overlay, 1.3F, 0.3F, 20, spin * 2F, false, 0.03F, argb(0xFFFFFF, 0.9F * a));
        }
        if (q == 2) {
            ringQ(vc, pose, overlay, 8.6F, 0.25F, 48, -spin * 0.7F, true, 0.01F, argb(JB_CYAN, 0.7F * a));
            // Losangos nos 4 cantos do anel de fora
            for (int i = 0; i < 4; i++) {
                float an = spin + (float) (Math.PI / 4 + Math.PI / 2 * i);
                float cx = Mth.cos(an) * rOut, cy = Mth.sin(an) * rOut, d = 1.1F;
                int col = argb(JB_CYAN, 0.95F * a);
                fv(vc, pose, overlay, cx + Mth.cos(an) * d, cy + Mth.sin(an) * d, 0.06F, col);
                fv(vc, pose, overlay, cx - Mth.sin(an) * d * 0.6F, cy + Mth.cos(an) * d * 0.6F, 0.06F, col);
                fv(vc, pose, overlay, cx - Mth.cos(an) * d, cy - Mth.sin(an) * d, 0.06F, col);
                fv(vc, pose, overlay, cx + Mth.sin(an) * d * 0.6F, cy - Mth.cos(an) * d * 0.6F, 0.06F, col);
            }
            // Pontas triangulares para fora, entre os raios
            for (int i = 0; i < 8; i++) {
                float an = spin + (float) (Math.PI * 2 * (i + 0.5) / 8);
                float c = Mth.cos(an), sn = Mth.sin(an), half = 0.18F;
                float cl = Mth.cos(an - half), sl = Mth.sin(an - half), cr = Mth.cos(an + half), sr = Mth.sin(an + half);
                int col = argb(JB_GOLD, 0.85F * a);
                fv(vc, pose, overlay, cl * (rOut + 0.4F), sl * (rOut + 0.4F), 0.02F, col);
                fv(vc, pose, overlay, cr * (rOut + 0.4F), sr * (rOut + 0.4F), 0.02F, col);
                fv(vc, pose, overlay, c * (rOut + 2.0F), sn * (rOut + 2.0F), 0.02F, col);
                fv(vc, pose, overlay, c * (rOut + 2.0F), sn * (rOut + 2.0F), 0.02F, col);
            }
        }
        ps.popPose();
    }

    /** Anel no plano XY (raio {@code r}, espessura {@code w}); {@code dashed} = só gomos alternados. */
    private static void ringQ(VertexConsumer vc, PoseStack.Pose pose, int overlay, float r, float w, int segs, float rot,
                              boolean dashed, float z, int argb) {
        if ((argb >>> 24) < 4) return;
        float ri = Math.max(0F, r - w * 0.5F), ro = r + w * 0.5F;
        for (int i = 0; i < segs; i++) {
            if (dashed && i % 2 == 1) continue;
            float a0 = rot + (float) (Math.PI * 2 * i / segs), a1 = rot + (float) (Math.PI * 2 * (i + 1) / segs);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            fv(vc, pose, overlay, c0 * ri, s0 * ri, z, argb);
            fv(vc, pose, overlay, c0 * ro, s0 * ro, z, argb);
            fv(vc, pose, overlay, c1 * ro, s1 * ro, z, argb);
            fv(vc, pose, overlay, c1 * ri, s1 * ri, z, argb);
        }
    }

    /** Traço reto no plano XY (largura {@code w}). */
    private static void lineQ(VertexConsumer vc, PoseStack.Pose pose, int overlay, float x1, float y1, float x2, float y2, float z,
                              float w, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1e-4F || (argb >>> 24) < 4) return;
        float nx = -dy / len * w * 0.5F, ny = dx / len * w * 0.5F;
        fv(vc, pose, overlay, x1 + nx, y1 + ny, z, argb);
        fv(vc, pose, overlay, x2 + nx, y2 + ny, z, argb);
        fv(vc, pose, overlay, x2 - nx, y2 - ny, z, argb);
        fv(vc, pose, overlay, x1 - nx, y1 - ny, z, argb);
    }

    /** Vértice emissivo da textura branca (o centro dela: cor lisa). */
    private static void fv(VertexConsumer vc, PoseStack.Pose pose, int overlay, float x, float y, float z, int argb) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(0.5F, 0.5F).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0F, 0F, 1F);
    }

    private static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    // =====================================================================================
    //  v1.3: peças das formas
    // =====================================================================================
    /** Peças novas (textura branca {@code aura.png}, tingida na hora de desenhar). */
    private static ModelPart model3() {
        if (root3 != null) return root3;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        // Olhos (na altura dos olhos da skin, um tiquinho à frente do rosto)
        r.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.1F, -4.2F, -4.2F, 2.2F, 1.2F, 0.3F)
                .addBox(0.9F, -4.2F, -4.2F, 2.2F, 1.2F, 0.3F), PartPose.ZERO);
        // Garras: três por mão, saindo da ponta do braço
        r.addOrReplaceChild("claws", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.6F, 0F, -0.3F, 0.6F, 3.2F, 0.6F)
                .addBox(-0.3F, 0F, -0.3F, 0.6F, 3.6F, 0.6F)
                .addBox(1.0F, 0F, -0.3F, 0.6F, 3.2F, 0.6F), PartPose.ZERO);
        // Elo de corrente (Fantasma)
        r.addOrReplaceChild("link", CubeListBuilder.create().texOffs(0, 0).addBox(-0.35F, -0.9F, -0.15F, 0.7F, 1.8F, 0.3F), PartPose.ZERO);
        // Runa flutuante (Elfo): plaquinha fina
        r.addOrReplaceChild("rune", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -1F, -0.05F, 2F, 2F, 0.1F), PartPose.ZERO);
        // Estandarte do Herói: mastro, travessa e o pano (tiras que ondulam)
        r.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(0, 0).addBox(-0.4F, -22F, -0.4F, 0.8F, 32F, 0.8F)
                .addBox(-4F, -21.6F, -0.3F, 8F, 0.6F, 0.6F), PartPose.ZERO);
        r.addOrReplaceChild("cloth", CubeListBuilder.create().texOffs(0, 0).addBox(-0.8F, 0F, -0.05F, 1.6F, 12F, 0.1F), PartPose.ZERO);
        // Ombreiras de pedra (Colosso)
        r.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -3.4F, -3F, 6F, 3F, 6F)
                .addBox(-2.5F, -4.4F, -2.5F, 5F, 1F, 5F), PartPose.ZERO);
        // Abdômen de aranha (Aracnídeo) e a marca vermelha
        r.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -3.5F, 0F, 9F, 7F, 10F)
                .addBox(-3.5F, -2.5F, 10F, 7F, 5F, 2F), PartPose.ZERO);
        r.addOrReplaceChild("mark", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -3.7F, 2.5F, 2F, 0.2F, 2F)
                .addBox(-0.5F, -3.7F, 4.5F, 1F, 0.2F, 1F).addBox(-1F, -3.7F, 5.5F, 2F, 0.2F, 2F), PartPose.ZERO);
        // Corpo de geleia do Rei Slime (do pé até acima da cabeça)
        r.addOrReplaceChild("jelly", CubeListBuilder.create().texOffs(0, 0).addBox(-12F, -11F, -12F, 24F, 35F, 24F), PartPose.ZERO);
        root3 = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        return root3;
    }

    /** Desenha a peça com escala a partir do pivô dela (chifres, presas e orelhas crescendo). */
    private static void scaledPart(PoseStack ps, ModelPart part, VertexConsumer vc, int light, int overlay, int color, float scale) {
        if (Math.abs(scale - 1F) < 0.005F) {
            part.render(ps, vc, light, overlay, color);
            return;
        }
        ps.pushPose();
        ps.translate(part.x / 16F, part.y / 16F, part.z / 16F);
        ps.scale(scale, scale, scale);
        ps.translate(-part.x / 16F, -part.y / 16F, -part.z / 16F);
        part.render(ps, vc, light, overlay, color);
        ps.popPose();
    }

    /** Olhos brilhando (emissivos) na cor {@code rgb}, com intensidade {@code k} (0..1). */
    private void glowEyes(PoseStack ps, MultiBufferSource buffers, int overlay, int rgb, float k) {
        PlayerModel<AbstractClientPlayer> pm = getParentModel();
        if (k <= 0.02F || !pm.head.visible) return;
        ps.pushPose();
        pm.head.translateAndRotate(ps);
        model3().getChild("eyes").render(ps, buffers.getBuffer(RenderType.entityTranslucentEmissive(AURA)), LightTexture.FULL_BRIGHT, overlay,
                ((int) (Mth.clamp(k, 0F, 1F) * 0xF0) << 24) | (rgb & 0xFFFFFF));
        ps.popPose();
    }

    /** O modelo do jogador inteiro com outra textura (rocha, pelagem, rachaduras), um pouco maior que o corpo. */
    private void textureShell(PoseStack ps, MultiBufferSource buffers, RenderType type, int light, int overlay, int argb, float grow) {
        if ((argb >>> 24) < 4) return;
        ps.pushPose();
        ps.translate(0, 0.75F, 0);
        ps.scale(grow, grow, grow);
        ps.translate(0, -0.75F, 0);
        getParentModel().renderToBuffer(ps, buffers.getBuffer(type), light, overlay, argb);
        ps.popPose();
    }

    /** A pele do jogador acesa (escamas do Dracônico, sangue fervendo do Meio-Orc): cabeça, corpo e braços emissivos. */
    private void skinGlow(PoseStack ps, MultiBufferSource buffers, int overlay, AbstractClientPlayer p, int argb) {
        if ((argb >>> 24) < 4) return;
        ResourceLocation skin;
        try {
            skin = getTextureLocation(p);
        } catch (RuntimeException e) {
            return;
        }
        if (skin == null) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(skin));
        PlayerModel<AbstractClientPlayer> pm = getParentModel();
        if (pm.head.visible) pm.head.render(ps, vc, LightTexture.FULL_BRIGHT, overlay, argb);
        if (pm.body.visible) pm.body.render(ps, vc, LightTexture.FULL_BRIGHT, overlay, argb);
        if (pm.leftArm.visible) pm.leftArm.render(ps, vc, LightTexture.FULL_BRIGHT, overlay, argb);
        if (pm.rightArm.visible) pm.rightArm.render(ps, vc, LightTexture.FULL_BRIGHT, overlay, argb);
    }

    /** Garras de lobisomem nas duas mãos. */
    private void claws(PoseStack ps, MultiBufferSource buffers, int light, int overlay, float open) {
        PlayerModel<AbstractClientPlayer> pm = getParentModel();
        ModelPart claws = model3().getChild("claws");
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AURA));
        float len = 0.3F + 0.7F * open;
        for (int side : new int[]{1, -1}) {
            ModelPart arm = side > 0 ? pm.leftArm : pm.rightArm;
            if (!arm.visible) continue;
            ps.pushPose();
            arm.translateAndRotate(ps);
            ps.translate(side * 1F / 16F, 9.6F / 16F, -1.2F / 16F);
            ps.mulPose(Axis.XP.rotation(-0.35F));
            ps.scale(1F, len, 1F);
            claws.render(ps, vc, light, overlay, 0xFFEDE4D2);
            ps.popPose();
        }
    }

    /** Correntes espectrais: uma pendurada em cada mão balançando e um anel girando na cintura. */
    private void chains(PoseStack ps, MultiBufferSource buffers, int overlay, float age, float open) {
        if (open <= 0.02F) return;
        PlayerModel<AbstractClientPlayer> pm = getParentModel();
        ModelPart link = model3().getChild("link");
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(AURA));
        int color = ((int) (open * 0xC8) << 24) | 0xB8F0FF;
        for (int side : new int[]{1, -1}) {
            ModelPart arm = side > 0 ? pm.leftArm : pm.rightArm;
            ps.pushPose();
            arm.translateAndRotate(ps);
            ps.translate(side * 1F / 16F, 10.2F / 16F, 0);
            for (int i = 0; i < 7; i++) {
                ps.mulPose(Axis.ZP.rotation(Mth.sin(age * 0.09F + i * 0.5F + side) * 0.12F));
                ps.mulPose(Axis.YP.rotationDegrees(90));
                link.render(ps, vc, LightTexture.FULL_BRIGHT, overlay, color);
                ps.translate(0, 1.4F / 16F, 0);
            }
            ps.popPose();
        }
        if (!FxQuality.atLeast(Graphics.MEDIUM)) return;
        ps.pushPose();
        pm.body.translateAndRotate(ps);
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a = age * 0.04 + Math.PI * 2 * i / n;
            ps.pushPose();
            ps.translate(Math.cos(a) * 6.5F / 16F, (9F + Mth.sin((float) a * 2F) * 0.8F) / 16F, Math.sin(a) * 6.5F / 16F);
            ps.mulPose(Axis.YP.rotation((float) -a));
            ps.mulPose(Axis.ZP.rotationDegrees(90));
            if (i % 2 == 1) ps.mulPose(Axis.YP.rotationDegrees(90));
            link.render(ps, vc, LightTexture.FULL_BRIGHT, overlay, color);
            ps.popPose();
        }
        ps.popPose();
    }

    /** Runas da lua orbitando o Elfo transformado. */
    private void runes(PoseStack ps, MultiBufferSource buffers, int overlay, float age, float open) {
        ModelPart rune = model3().getChild("rune");
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(AURA));
        int n = FxQuality.atLeast(Graphics.HIGH) ? 8 : 5;
        for (int i = 0; i < n; i++) {
            double a = age * 0.05 + Math.PI * 2 * i / n;
            float y = 6F + Mth.sin(age * 0.08F + i * 1.3F) * 3F;
            float r = 11F * open;
            ps.pushPose();
            ps.translate(Math.cos(a) * r / 16F, y / 16F, Math.sin(a) * r / 16F);
            ps.mulPose(Axis.YP.rotation((float) (-a + Math.PI / 2)));
            ps.mulPose(Axis.ZP.rotationDegrees(45));
            rune.render(ps, vc, LightTexture.FULL_BRIGHT, overlay, ((int) (open * 0xD0) << 24) | (i % 2 == 0 ? 0xBFF6FF : 0x9FF0B8));
            ps.popPose();
        }
    }

    /** Estandarte de luz do Herói Desperto, preso nas costas (estilo sashimono), ondulando. */
    private void banner(PoseStack ps, MultiBufferSource buffers, int overlay, float age, float open) {
        ModelPart m3 = model3();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(AURA));
        ps.pushPose();
        getParentModel().body.translateAndRotate(ps);
        ps.translate(0, 0, 3.2F / 16F);
        // Sobe do chão das costas conforme a forma abre
        ps.translate(0, (1F - open) * 12F / 16F, 0);
        m3.getChild("pole").render(ps, vc, LightTexture.FULL_BRIGHT, overlay, ((int) (open * 0xF0) << 24) | 0xFFE7A0);
        ModelPart cloth = m3.getChild("cloth");
        for (int i = 0; i < 5; i++) {
            float x = -3.2F + i * 1.6F;
            float wave = Mth.sin(age * 0.18F - i * 0.7F) * (0.6F + i * 0.1F);
            ps.pushPose();
            ps.translate(x / 16F, -21F / 16F, (0.8F + wave) / 16F);
            ps.mulPose(Axis.XP.rotation(0.12F + wave * 0.05F));
            int glow = i % 2 == 0 ? 0xFFF4C8 : 0xFFD86B;
            cloth.render(ps, vc, LightTexture.FULL_BRIGHT, overlay, ((int) (open * (0xB0 + 0x30 * Mth.sin(age * 0.2F + i))) << 24) | glow);
            ps.popPose();
        }
        ps.popPose();
    }

    /** Ombreiras de pedra do Colosso. */
    private void pauldrons(PoseStack ps, MultiBufferSource buffers, int light, int overlay, float open) {
        PlayerModel<AbstractClientPlayer> pm = getParentModel();
        ModelPart pd = model3().getChild("pauldron");
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(GOLEM));
        for (int side : new int[]{1, -1}) {
            ModelPart arm = side > 0 ? pm.leftArm : pm.rightArm;
            if (!arm.visible) continue;
            ps.pushPose();
            arm.translateAndRotate(ps);
            ps.translate(side * 1F / 16F, 0, 0);
            ps.scale(open, open, open);
            pd.render(ps, vc, light, overlay, 0xFFFFFFFF);
            ps.popPose();
        }
    }

    /** Abdômen de aranha nas costas (com a marca vermelha brilhando). */
    private void abdomen(PoseStack ps, MultiBufferSource buffers, int light, int overlay, float open) {
        ModelPart m3 = model3();
        ps.pushPose();
        ps.translate(0, 9.5F / 16F, 2F / 16F);
        ps.mulPose(Axis.XP.rotation(-0.55F));
        ps.scale(open, open, open);
        m3.getChild("abdomen").render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(AURA)), light, overlay, 0xFF2E2238);
        m3.getChild("mark").render(ps, buffers.getBuffer(RenderType.entityTranslucentEmissive(AURA)), LightTexture.FULL_BRIGHT, overlay, 0xF0E02030);
        ps.popPose();
    }

    /** O Rei Slime dentro do corpo de geleia gigante, balançando. */
    private void slimeBody(PoseStack ps, MultiBufferSource buffers, int overlay, float age, float open) {
        ModelPart jelly = model3().getChild("jelly");
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(AURA));
        float wob = Mth.sin(age * 0.15F) * 0.04F;
        ps.pushPose();
        // Pivô no chão (y = 24 px): estica para os lados quando encolhe na altura
        ps.translate(0, 24F / 16F, 0);
        ps.scale(open * (1F + wob), open * (1F - wob), open * (1F + wob));
        ps.translate(0, -24F / 16F, 0);
        jelly.render(ps, vc, LightTexture.FULL_BRIGHT, overlay, 0x6A8FE05A);
        if (FxQuality.atLeast(Graphics.HIGH)) {
            ps.translate(0, 24F / 16F * 0.12F, 0);
            ps.scale(0.88F, 0.88F, 0.88F);
            jelly.render(ps, vc, LightTexture.FULL_BRIGHT, overlay, 0x3AB8FF80);
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
