package com.renanpontes.isekaimahou.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Lifecycle;
import com.renanpontes.isekaimahou.IsekaiMahou;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O Minecraft marca qualquer dimensão que não seja Overworld/Nether/End como "experimental" e mostra
 * o aviso "Worlds using Experimental Settings are not supported" ao criar/abrir o mundo.
 * As dimensões do Isekai Mahou fazem parte do mod (não são um datapack solto), então só ELAS são tratadas
 * como estáveis. Dimensões de outros mods/datapacks continuam gerando o aviso normalmente.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {

    @Unique
    private static final Set<ResourceKey<LevelStem>> isekaiMahou$VANILLA = Set.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END);

    @Unique
    private static boolean isekaiMahou$ours(ResourceKey<LevelStem> key) {
        return IsekaiMahou.MODID.equals(key.location().getNamespace());
    }

    @Unique
    private static boolean isekaiMahou$foreign(ResourceKey<LevelStem> key) {
        return !isekaiMahou$VANILLA.contains(key) && !isekaiMahou$ours(key);
    }

    /** Cada dimensão do mod entra no registro como estável. */
    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void isekaiMahou$stableOwnDimension(ResourceKey<LevelStem> key, LevelStem stem, CallbackInfoReturnable<Lifecycle> cir) {
        if (isekaiMahou$ours(key)) cir.setReturnValue(Lifecycle.stable());
    }

    /** O registro inteiro vira "experimental" se tiver mais de 3 dimensões; se as extras forem todas do mod, fica estável. */
    @ModifyArg(method = "bake",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/MappedRegistry;<init>(Lnet/minecraft/resources/ResourceKey;Lcom/mojang/serialization/Lifecycle;)V"),
            index = 1)
    private Lifecycle isekaiMahou$stableRegistry(Lifecycle original, @Local(argsOnly = true) Registry<LevelStem> stemRegistry) {
        if (original == Lifecycle.stable()) return original;
        for (ResourceKey<LevelStem> key : stemRegistry.registryKeySet()) {
            if (isekaiMahou$foreign(key)) return original;
        }
        for (ResourceKey<LevelStem> key : ((WorldDimensions) (Object) this).dimensions().keySet()) {
            if (isekaiMahou$foreign(key)) return original;
        }
        return Lifecycle.stable();
    }
}
