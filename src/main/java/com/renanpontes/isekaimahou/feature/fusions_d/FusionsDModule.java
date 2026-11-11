package com.renanpontes.isekaimahou.feature.fusions_d;

import com.renanpontes.isekaimahou.feature.fusions_d.effect.SoulBurn;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.CrystalSpikeEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.KrakenTentacleEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.RubbleEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.glyph.CrystalAstralGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_d.glyph.GravityVoidGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_d.glyph.HellAbyssGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_d.glyph.TwilightGlyphs;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Módulo "fusions_d" (v0.8, §9): Misturas de Cristal, Astral, Chama Infernal, Abismo, Gravidade, Vazio e
 * Crepúsculo — 14 glifos com manifestações próprias (Lótus de Cristal, Canhão Prismático, Buraco Negro, Chuva de
 * Estrelas, Pilar de Chamas Negras, Queimar Alma, Garras do Kraken, Bocarra Abissal, Esmagamento Planetário,
 * Poço Gravitacional, Corte Dimensional, Fenda do Vazio, Ragnarök, Equilíbrio Absoluto) e 3 Magias Lendárias.
 */
public class FusionsDModule implements Module {
    @Override
    public String id() {
        return "fusions_d";
    }

    @Override
    public void init(IEventBus modBus) {
        FdRegistries.register(modBus);
        CrystalAstralGlyphs.register();
        HellAbyssGlyphs.register();
        GravityVoidGlyphs.register();
        TwilightGlyphs.register();
        SoulBurn.register();
        FdLegends.register();
        modBus.addListener(FdLegends::validate);
        // Depois de todos (como o núcleo): se a morte foi cancelada, não chega aqui
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FdUtil::onDeath);
        // Estado estático (contadores, deduplicação, quem está preso) não sobrevive ao servidor
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            FdUtil.clear();
            RubbleEntity.resetCount();
            CrystalSpikeEntity.resetCount();
            KrakenTentacleEntity.clearAll();
        });
    }
}
