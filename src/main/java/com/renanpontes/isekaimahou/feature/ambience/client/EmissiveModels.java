package com.renanpontes.isekaimahou.feature.ambience.client;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ambience.AmbienceConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * SÓ CLIENTE. <b>Brilho no escuro</b> do jeito simples: depois de os modelos serem montados, os blocos/itens da lista
 * {@code assets/isekai_mahou/ambience/emissive.json} ganham uma luz mínima própria (0–15) nas faces — cristais, runas e
 * essências continuam visíveis numa caverna sem tocha. Não muda textura, modelo nem a luz que o bloco emite no mundo, e
 * nenhum arquivo de outro módulo é editado: os ids são lidos como texto (o que não existir é ignorado).
 * <p>
 * Formato: {@code {"blocks": {"isekai_mahou:void_crystal": 10}, "items": {"isekai_mahou:mana_crystal": 15}}}. O item de um
 * bloco da lista brilha igual ao bloco. Pacotes de recursos podem trazer o mesmo arquivo para acrescentar/mudar ids.
 */
public final class EmissiveModels {
    private static final ResourceLocation LIST = IsekaiMahou.id("ambience/emissive.json");

    static void onBake(ModelEvent.ModifyBakingResult event) {
        if (!AmbienceConfig.on(AmbienceConfig.GLOW)) return;
        Map<ResourceLocation, Integer> blocks = new HashMap<>();
        Map<ResourceLocation, Integer> items = new HashMap<>();
        load(blocks, items);
        if (blocks.isEmpty() && items.isEmpty()) return;
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        List<Map.Entry<ModelResourceLocation, BakedModel>> swaps = new ArrayList<>();
        for (Map.Entry<ModelResourceLocation, BakedModel> en : models.entrySet()) {
            ModelResourceLocation key = en.getKey();
            BakedModel model = en.getValue();
            if (model == null || model instanceof Glow || model.isCustomRenderer()) continue;
            Integer level;
            if (ModelResourceLocation.INVENTORY_VARIANT.equals(key.variant())) {
                level = items.get(key.id());
                if (level == null) level = blocks.get(key.id());
            } else {
                level = blocks.get(key.id());
            }
            if (level != null && level > 0) swaps.add(Map.entry(key, new Glow(model, level)));
        }
        for (Map.Entry<ModelResourceLocation, BakedModel> swap : swaps) models.put(swap.getKey(), swap.getValue());
    }

    /** Lê a lista de todos os pacotes (os de cima ganham); arquivo quebrado é ignorado, nunca derruba o carregamento. */
    private static void load(Map<ResourceLocation, Integer> blocks, Map<ResourceLocation, Integer> items) {
        try {
            for (Resource res : Minecraft.getInstance().getResourceManager().getResourceStack(LIST)) {
                try (Reader reader = res.openAsReader()) {
                    JsonObject root = GsonHelper.parse(reader);
                    read(root, "blocks", blocks);
                    read(root, "items", items);
                } catch (Exception e) {
                    IsekaiMahou.LOGGER.warn("[ambience] emissive.json invalido em {}: {}", res.sourcePackId(), e.toString());
                }
            }
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.warn("[ambience] nao deu para ler emissive.json: {}", e.toString());
        }
    }

    private static void read(JsonObject root, String key, Map<ResourceLocation, Integer> out) {
        if (!root.has(key) || !root.get(key).isJsonObject()) return;
        for (Map.Entry<String, JsonElement> en : root.getAsJsonObject(key).entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(en.getKey());
            if (id == null || !en.getValue().isJsonPrimitive()) continue;
            out.put(id, Mth.clamp(en.getValue().getAsInt(), 0, 15));
        }
    }

    /** O modelo original com as faces "acesas". As faces convertidas ficam guardadas (a malha do chunk pede muitas vezes). */
    private static final class Glow extends BakedModelWrapper<BakedModel> {
        private final IQuadTransformer light;
        private final Map<BakedQuad, BakedQuad> lit = new ConcurrentHashMap<>();

        Glow(BakedModel original, int level) {
            super(original);
            this.light = QuadTransformers.settingEmissivity(level);
        }

        private List<BakedQuad> glow(List<BakedQuad> quads) {
            if (quads.isEmpty()) return quads;
            List<BakedQuad> out = new ArrayList<>(quads.size());
            for (BakedQuad q : quads) out.add(lit.computeIfAbsent(q, light::process));
            return out;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return glow(originalModel.getQuads(state, side, rand));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                        @Nullable RenderType renderType) {
            return glow(originalModel.getQuads(state, side, rand, data, renderType));
        }

        // os dois abaixo devolveriam o modelo ORIGINAL (sem brilho) para o desenho de itens
        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
            BakedModel result = originalModel.applyTransform(context, poseStack, leftHand);
            return result == originalModel ? this : result;
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            List<BakedModel> passes = originalModel.getRenderPasses(stack, fabulous);
            return passes.size() == 1 && passes.get(0) == originalModel ? List.of(this) : passes;
        }
    }

    private EmissiveModels() {}
}
