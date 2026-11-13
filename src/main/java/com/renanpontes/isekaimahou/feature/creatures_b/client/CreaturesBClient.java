package com.renanpontes.isekaimahou.feature.creatures_b.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_b.CbRegistries;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.CbBoss;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.CbBossRegistry;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.CultistEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.OrcBerserkerEntity;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.TrollEntity;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.util.Color;

/** Parte de cliente do modulo "creatures_b": renderers GeckoLib de todos os humanoides. */
public class CreaturesBClient implements ClientModule {
    /** Fúria do berserker / fase 2 dos chefes: avermelha o modelo. */
    private static final Color RAGE_TINT = Color.ofRGBA(1.0F, 0.72F, 0.68F, 1.0F);
    private static final Color BURNT_TINT = Color.ofRGBA(0.8F, 0.75F, 0.72F, 1.0F);

    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(CreaturesBClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(CbRegistries.KOBOLD.get(), KoboldRenderer::new);
        e.registerEntityRenderer(CbRegistries.ORC.get(), ctx -> new CbRenderer<>(ctx, CbRenderer.model("orc", "orc"), false, 0.6F, null));
        e.registerEntityRenderer(CbRegistries.ORC_BERSERKER.get(), ctx -> new CbRenderer<OrcBerserkerEntity>(ctx,
                CbRenderer.model("orc", "orc_berserker"), true, 0.6F, b -> b.isRaging() ? RAGE_TINT : null));
        e.registerEntityRenderer(CbRegistries.ORC_LORD.get(), ctx -> new CbBossRenderer<>(ctx, CbRenderer.model("orc_lord", "orc_lord"),
                0.9F, CreaturesBClient::bossTint));
        e.registerEntityRenderer(CbRegistries.OGRE.get(), ctx -> new CbRenderer<>(ctx, CbRenderer.model("ogre", "ogre"), false, 1.2F, null));
        e.registerEntityRenderer(CbRegistries.TROLL.get(), ctx -> new CbRenderer<TrollEntity>(ctx, CbRenderer.model("troll", "troll"),
                true, 0.9F, t -> t.isOnFire() ? BURNT_TINT : null));
        e.registerEntityRenderer(CbRegistries.LIZARDMAN.get(), ctx -> new CbRenderer<>(ctx, CbRenderer.model("lizardman", "lizardman"),
                true, 0.5F, null));
        e.registerEntityRenderer(CbRegistries.MINOTAUR.get(), ctx -> new CbBossRenderer<>(ctx, CbRenderer.model("minotaur", "minotaur"),
                1.1F, CreaturesBClient::bossTint));
        e.registerEntityRenderer(CbBossRegistry.THROWN_AXE.get(), BossThrownAxeRenderer::new);
        e.registerEntityRenderer(CbRegistries.WEREWOLF.get(), ctx -> new CbRenderer<>(ctx, CbRenderer.model("werewolf", "werewolf"),
                true, 0.6F, null));
        e.registerEntityRenderer(CbRegistries.CULTIST.get(), ctx -> new CbRenderer<>(ctx, new CultistModel(), true, 0.5F, null));
        e.registerEntityRenderer(CbRegistries.WANDERING_MAGE.get(), WanderingMageRenderer::new);
    }

    private static Color bossTint(CbBoss boss) {
        return boss.isPhase2() ? RAGE_TINT : null;
    }

    /** Cultista: mesma malha, túnica da escola (sombra/fogo/arcano). */
    private static final class CultistModel extends DefaultedEntityGeoModel<CultistEntity> {
        private static final ResourceLocation[] TEXTURES = {
                IsekaiMahou.id("textures/entity/cultist.png"),
                IsekaiMahou.id("textures/entity/cultist_fire.png"),
                IsekaiMahou.id("textures/entity/cultist_arcane.png")};

        CultistModel() {
            super(IsekaiMahou.id("cultist"), true);
        }

        @Override
        public ResourceLocation getTextureResource(CultistEntity cultist) {
            return TEXTURES[Math.max(0, Math.min(TEXTURES.length - 1, cultist.school()))];
        }
    }
}
