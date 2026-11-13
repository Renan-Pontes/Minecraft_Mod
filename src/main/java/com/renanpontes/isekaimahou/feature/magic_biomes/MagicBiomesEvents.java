package com.renanpontes.isekaimahou.feature.magic_biomes;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * O que os biomas mágicos fazem com o jogador (bem pouco, de propósito):
 * <ul>
 *   <li>primeira visita a cada um: marcador {@code magic_biomes.visit.<bioma>} (o capítulo do Diário conta) e UMA dica;</li>
 *   <li>Floresta de Cristal e Caverna de Cristal: a mana volta 15% mais rápido enquanto estiver lá.</li>
 * </ul>
 * Só olha o bioma do jogador a cada 2 s. Não mexe em blocos nem cria criaturas.
 */
public final class MagicBiomesEvents {
    public static final String VISIT_PREFIX = "magic_biomes.visit.";
    private static final ResourceLocation CRYSTAL_AIR = IsekaiMahou.id("magic_biomes_crystal_air");
    private static final double CRYSTAL_AIR_BONUS = 0.15;
    private static final int PERIOD = 40;

    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if ((sp.tickCount + sp.getId()) % PERIOD != 0) return;
        Holder<Biome> biome = sp.serverLevel().getBiome(sp.blockPosition());
        boolean magic = biome.is(MagicBiomes.TAG);
        crystalAir(sp, magic && (biome.is(MagicBiomes.CRYSTAL_FOREST) || biome.is(MagicBiomes.CRYSTAL_CAVERN)));
        if (!magic || sp.isSpectator()) return;
        String path = biome.unwrapKey().map(ResourceKey::location).map(ResourceLocation::getPath).orElse(null);
        if (path == null) return;
        MagicData d = MagicApi.get(sp);
        if (!d.hasFlag(VISIT_PREFIX + path)) d.setFlag(VISIT_PREFIX + path, true);
        Hints.show(sp, "magic_biomes." + path);
    }

    /** Liga/desliga o bônus de regeneração de mana (modificador temporário: some ao sair do jogo). */
    private static void crystalAir(ServerPlayer sp, boolean inside) {
        AttributeInstance regen = sp.getAttribute(CoreRegistries.MANA_REGEN);
        if (regen == null) return;
        boolean has = regen.hasModifier(CRYSTAL_AIR);
        if (inside && !has) {
            regen.addTransientModifier(new AttributeModifier(CRYSTAL_AIR, CRYSTAL_AIR_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!inside && has) {
            regen.removeModifier(CRYSTAL_AIR);
        }
    }

    private MagicBiomesEvents() {}
}
