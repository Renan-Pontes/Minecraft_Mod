package com.renanpontes.isekaimahou.mixin;

import java.util.function.Consumer;

import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.feature.flat_lands.FlatLandsBiomes;
import com.renanpontes.isekaimahou.feature.magic_biomes.MagicBiomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Biomas do mod no Overworld sem TerraBlender. Em cada bioma de superfície que o vanilla registra:
 * <ul>
 *   <li>{@link FlatLandsBiomes#replaceSurface} (flat_lands: Campos de Sakura, Estepe Dourada, Prados do Reino) decide se
 *       uma faixa do meio daquela fatia (só fatias já planas de planície/savana) vira um bioma plano;</li>
 *   <li>{@link MagicBiomes#replaceSurface} (magic_biomes: Floresta de Cristal, Pântano Assombrado, Bosque Crepuscular)
 *       faz o mesmo com florestas frias, pântanos e florestas escuras.</li>
 * </ul>
 * E no fim dos biomas subterrâneos entra a Caverna de Cristal Mágico. Só afeta chunks novos.
 */
@Mixin(OverworldBiomeBuilder.class)
public abstract class OverworldBiomeBuilderFlatMixin {

    @Inject(method = "addSurfaceBiome", at = @At("HEAD"), cancellable = true)
    private void isekaiMahou$flatLands(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
                                       Climate.Parameter temperature, Climate.Parameter humidity,
                                       Climate.Parameter continentalness, Climate.Parameter erosion,
                                       Climate.Parameter weirdness, float offset, ResourceKey<Biome> key, CallbackInfo ci) {
        if (FlatLandsBiomes.replaceSurface(consumer, temperature, humidity, continentalness, erosion, weirdness, offset, key)
                || MagicBiomes.replaceSurface(consumer, temperature, humidity, continentalness, erosion, weirdness, offset, key)) {
            ci.cancel();
        }
    }

    @Inject(method = "addUndergroundBiomes", at = @At("TAIL"))
    private void isekaiMahou$magicCaverns(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer, CallbackInfo ci) {
        MagicBiomes.addUnderground(consumer);
    }
}
