package com.renanpontes.isekaimahou.mixin;

import com.renanpontes.isekaimahou.core.worldgen.CityExclusion;

import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vilas, postos de saqueadores, ruínas e cabanas do vanilla não nascem dentro (nem colados) das cidades e capitais do
 * mod. A regra fica em {@link CityExclusion}; aqui só o gancho. Só olha quando o vanilla já ia aceitar o chunk (raro),
 * e só age onde existe um conjunto de cidade (Overworld). O {@code /locate} respeita a regra (ele confere o chunk).
 */
@Mixin(StructurePlacement.class)
public abstract class StructurePlacementMixin {

    @Inject(method = "isStructureChunk", at = @At("RETURN"), cancellable = true)
    private void isekaiMahou$avoidCities(ChunkGeneratorStructureState state, int x, int z,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && CityExclusion.blocksVanilla((StructurePlacement) (Object) this, state, x, z)) {
            cir.setReturnValue(false);
        }
    }
}
