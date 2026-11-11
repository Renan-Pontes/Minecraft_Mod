package com.renanpontes.isekaimahou;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.module.Modules;
import com.renanpontes.isekaimahou.network.CoreNetwork;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Isekai Mahou — classe principal.
 * Ordem: registros do núcleo → rede do núcleo → glifos do núcleo → todos os módulos (feature/*).
 */
@Mod(IsekaiMahou.MODID)
public class IsekaiMahou {
    public static final String MODID = "isekai_mahou";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IsekaiMahou(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, MahouConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, MahouConfig.CLIENT_SPEC);

        CoreRegistries.register(modBus);
        modBus.addListener(CoreNetwork::register);
        CoreGlyphs.register();
        com.renanpontes.isekaimahou.core.boss.BossToolkit.init(modBus);
        com.renanpontes.isekaimahou.core.status.StatusCore.init(modBus);
        com.renanpontes.isekaimahou.core.stack.StackCore.init(modBus);
        com.renanpontes.isekaimahou.core.reputation.ReputationCore.init(modBus);
        com.renanpontes.isekaimahou.core.MagicHover.init();
        com.renanpontes.isekaimahou.core.item.SoulBinding.init();
        com.renanpontes.isekaimahou.core.classes.ClassesCore.init(modBus);
        com.renanpontes.isekaimahou.core.onboard.OnboardCore.init(modBus);
        com.renanpontes.isekaimahou.core.worldgen.WorldgenCore.init(modBus);
        com.renanpontes.isekaimahou.core.social.SocialCore.init(modBus);
        com.renanpontes.isekaimahou.core.ux.UxCore.init(modBus);
        com.renanpontes.isekaimahou.glyph.fx.SpellFxCore.init(modBus); // efeitos visuais das magias (partículas fx_*, círculos)
        com.renanpontes.isekaimahou.core.anim.MahouAnims.init(modBus, container); // animações de combate (Epic Fight)

        Modules.initAll(modBus);
    }

    /** Atalho para "isekai_mahou:<path>". */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
