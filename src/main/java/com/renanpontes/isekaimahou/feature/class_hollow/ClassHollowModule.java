package com.renanpontes.isekaimahou.feature.class_hollow;

import com.renanpontes.isekaimahou.feature.class_hollow.glyph.HollowGlyphs;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "class_hollow" (v1.3) — a classe HOLLOW, variação do Shinigami (homenagem a Bleach, com nomes e visuais
 * próprios). NÃO se escolhe no início:
 * <ul>
 *   <li>Entrada: o Shinigami que PERDE para o Vazio Interior no Mundo Interior (ou escolhe "Aceitar o hollow interior"
 *   antes da luta) é tomado por ele — cena da máscara, do buraco no peito e do rugido ({@link Hollowfication});</li>
 *   <li>Evolução devorando ALMAS: Hollow → Gillian → Adjuchas → Vasto Lorde → Arrancar, com a Resurrección escolhida
 *   uma vez entre Pantera, Murciélago e Serpiente ({@link HollowRanks}, {@link HollowSouls}); fome de almas;</li>
 *   <li>v1.4: TÉCNICAS INATAS com tecla própria ({@link com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTech}):
 *   tecla "Instinto Hollow" (Bala/Cero/Gran Rey Cero/Cero Oscuras/Sonído/Pesquisa) e a Roda Hollow (Regeneração, Hierro,
 *   Garganta, Negación, Descorrer, Resurrección). Os 10 glifos de forma antigos ficam registrados só para magias salvas
 *   ({@link HollowGlyphs}); glifos novos da classe: o aumento "Cero Fusionado" e o gatilho "Ao Devorar uma Alma";</li>
 *   <li>Visual: máscara branca por rank, buraco no peito, ossos, Reiatsu rubro-negro (cliente); aba "Hollow" no menu B.</li>
 * </ul>
 */
public class ClassHollowModule implements Module {
    @Override
    public String id() {
        return Hollow.MODULE;
    }

    @Override
    public void init(IEventBus modBus) {
        HollowSounds.register(modBus);
        HollowGlyphs.register();
        modBus.addListener(HollowNetwork::register);
        HollowEvents.register();
        HollowCommands.register();
    }
}
