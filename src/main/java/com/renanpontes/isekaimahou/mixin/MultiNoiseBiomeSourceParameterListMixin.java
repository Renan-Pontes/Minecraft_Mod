package com.renanpontes.isekaimahou.mixin;

import com.renanpontes.isekaimahou.feature.magic_biomes.MagicBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bioma do mod no Nether (magic_biomes: Ermo de Cristal Ígneo) sem trocar o JSON da dimensão: a lista de clima do
 * Nether é fixa no código do vanilla (5 biomas); aqui ela ganha um 6º ponto quando é montada com o registro de
 * verdade. A lista continua sendo a "nether" do vanilla, então o mundo não vira "experimental". Só chunks novos.
 * A regra fica em {@link MagicBiomes#extendNether}.
 */
@Mixin(MultiNoiseBiomeSourceParameterList.class)
public abstract class MultiNoiseBiomeSourceParameterListMixin {

    @Shadow @Final @Mutable
    private Climate.ParameterList<Holder<Biome>> parameters;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void isekaiMahou$netherBiome(MultiNoiseBiomeSourceParameterList.Preset preset, HolderGetter<Biome> biomes,
                                         CallbackInfo ci) {
        this.parameters = MagicBiomes.extendNether(preset, this.parameters, biomes);
    }
}
