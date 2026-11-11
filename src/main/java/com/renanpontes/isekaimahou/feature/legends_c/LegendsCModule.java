package com.renanpontes.isekaimahou.feature.legends_c;

import com.renanpontes.isekaimahou.feature.legends_c.entity.BlueOrbEntity;
import com.renanpontes.isekaimahou.feature.legends_c.entity.FugaArrowEntity;
import com.renanpontes.isekaimahou.feature.legends_c.entity.MalevolentShrineEntity;
import com.renanpontes.isekaimahou.feature.legends_c.entity.VoidFieldEntity;
import com.renanpontes.isekaimahou.feature.legends_c.glyph.CleaveEffect;
import com.renanpontes.isekaimahou.feature.legends_c.glyph.DismantleEffect;
import com.renanpontes.isekaimahou.feature.legends_c.glyph.FlameArrowEffect;
import com.renanpontes.isekaimahou.feature.legends_c.glyph.InfinityEffect;
import com.renanpontes.isekaimahou.feature.legends_c.glyph.LapseBlueEffect;
import com.renanpontes.isekaimahou.feature.legends_c.glyph.ReversalRedEffect;
import com.renanpontes.isekaimahou.feature.legends_c.infinity.InfinityEvents;
import com.renanpontes.isekaimahou.feature.legends_c.law.LcLaws;
import com.renanpontes.isekaimahou.feature.legends_c.law.VoidLaw;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Módulo "legends_c" (v0.8): TÉCNICAS MALDITAS (homenagem a Jujutsu Kaisen).
 * Glifos: Infinito ({@code infinity}), Azul ({@code lapse_blue}), Vermelho ({@code reversal_red}), Desmantelar
 * ({@code dismantle}), Clivar ({@code cleave}), Fuga ({@code flame_arrow}) e as leis Santuário Maligno
 * ({@code law_shrine}) e Vazio Infinito ({@code law_infinite_void}).
 * Lendas: Vazio Infinito, Azul, Vermelho, Infinito, Fuga, Corte que Divide o Mundo (+ o Santuário Maligno do legends_b).
 */
public class LegendsCModule implements Module {
    @Override
    public String id() {
        return "legends_c";
    }

    @Override
    public void init(IEventBus modBus) {
        LcRegistry.register(modBus);
        modBus.addListener(LcNet::register);
        modBus.addListener(LcRecipes::validate);

        GlyphRegistry.register(new InfinityEffect());
        GlyphRegistry.register(new LapseBlueEffect());
        GlyphRegistry.register(new ReversalRedEffect());
        GlyphRegistry.register(new DismantleEffect());
        GlyphRegistry.register(new CleaveEffect());
        GlyphRegistry.register(new FlameArrowEffect());
        LcLaws.register();

        LcRecipes.register();

        InfinityEvents.register();
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            LcUtil.clear();
            BlueOrbEntity.clear();
            FugaArrowEntity.clear();
            MalevolentShrineEntity.clear();
            VoidFieldEntity.clear();
            VoidLaw.clear();
        });
    }
}
