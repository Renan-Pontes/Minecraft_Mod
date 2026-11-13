package com.renanpontes.isekaimahou.mixin;

import java.util.stream.Stream;

import com.renanpontes.isekaimahou.feature.magic_biomes.MagicBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bioma do mod no End (magic_biomes: Jardins Astrais). O gerador de biomas do End tem 5 biomas fixos no código;
 * aqui algumas regiões das terras altas/médias das ilhas de FORA viram Jardins Astrais
 * ({@link MagicBiomes#astralRegion}). A ilha do dragão, as ilhotas e as bordas não mudam. Só chunks novos.
 * <p>O bioma é achado no mesmo registro de onde veio o "the_end" deste gerador (o {@code unwrapLookup} do NeoForge);
 * no registro "só vanilla" ele não existe e nada muda.
 */
@Mixin(TheEndBiomeSource.class)
public abstract class TheEndBiomeSourceMixin {

    @Shadow @Final private Holder<Biome> end;
    @Shadow @Final private Holder<Biome> highlands;
    @Shadow @Final private Holder<Biome> midlands;

    /** Guardado depois de achado (a busca é só uma consulta ao registro, mas roda por coluna gerada). */
    @Unique
    private Holder<Biome> isekaiMahou$astral;

    @Unique
    private Holder<Biome> isekaiMahou$astral() {
        Holder<Biome> cached = this.isekaiMahou$astral;
        if (cached != null) return cached;
        HolderLookup.RegistryLookup<Biome> lookup = this.end.unwrapLookup();
        if (lookup == null) return null;
        Holder<Biome> found = lookup.get(MagicBiomes.ASTRAL_GARDENS).filter(Holder::isBound).orElse(null);
        this.isekaiMahou$astral = found;
        return found;
    }

    /** Sem isto as features do bioma não seriam geradas (o gerador só decora biomas "possíveis"). */
    @Inject(method = "collectPossibleBiomes", at = @At("RETURN"), cancellable = true)
    private void isekaiMahou$possibleAstral(CallbackInfoReturnable<Stream<Holder<Biome>>> cir) {
        Holder<Biome> astral = isekaiMahou$astral();
        if (astral != null) cir.setReturnValue(Stream.concat(cir.getReturnValue(), Stream.of(astral)));
    }

    @Inject(method = "getNoiseBiome", at = @At("RETURN"), cancellable = true)
    private void isekaiMahou$astralGardens(int x, int y, int z, Climate.Sampler sampler,
                                           CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> picked = cir.getReturnValue();
        if (picked != this.highlands && picked != this.midlands) return;
        if (!MagicBiomes.astralRegion(x, z)) return;
        Holder<Biome> astral = isekaiMahou$astral();
        if (astral != null) cir.setReturnValue(astral);
    }
}
