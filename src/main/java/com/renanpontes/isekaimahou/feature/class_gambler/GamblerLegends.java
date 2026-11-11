package com.renanpontes.isekaimahou.feature.class_gambler;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * As 5 Magias Lendárias do Apostador (pergaminhos no rank V da Casa; também entram na galeria/sorteio de lendas):
 * <ul>
 *   <li><b>Leque Cinético Real</b> (homenagem ao Gambit): 5 cartas teleguiadas, cada uma puxa uma Mão Real, tudo
 *   dobrando a aposta. Círculo 5.</li>
 *   <li><b>Cassino da Fortuna Eterna</b> (homenagem ao Hakari): o Domínio Cassino ampliado em que cada pulso puxa uma
 *   Mão Real e joga Cara ou Coroa, dobrando a aposta. Círculo 8.</li>
 *   <li><b>Truque do Mágico: Goma Elástica</b> (homenagem ao mágico das cartas): 3 cartas teleguiadas que grudam a
 *   goma e a textura falsa. Círculo 4.</li>
 *   <li><b>Aposta Total: Tudo ou Nada</b>: o duelo de dados amplificado e dobrado, com os Dados do Destino. Círculo 6.</li>
 *   <li><b>Morte Ociosa: Jackpot Imortal</b>: o domínio de pachinko ampliado, com Dados do Destino e Mão Real em cada
 *   rodada. Círculo 8.</li>
 * </ul>
 * Validadas no setup comum: só registra se o {@link SpellParser} aceita e se cabe num jogador do Círculo mínimo.
 */
public final class GamblerLegends {
    public static final String KINETIC = "kinetic_royal_fan";
    public static final String ETERNAL = "eternal_fortune_casino";
    public static final String GUM_TRICK = "gambler_bungee_trick";
    public static final String ALL_OR_NOTHING = "gambler_all_or_nothing";
    public static final String IMMORTAL = "gambler_immortal_jackpot";
    public static final List<String> IDS = List.of(KINETIC, ETERNAL, GUM_TRICK, ALL_OR_NOTHING, IMMORTAL);

    private record Def(String id, String name, String chant, int color, int circle, String... glyphs) {}

    private static final List<Def> DEFS = List.of(
            new Def(KINETIC, "Leque Cinético Real", "Cinco cartas, uma mão só... a casa paga em dobro!", 0xFF4A5A, 5,
                    "card_throw", "split", "split", "homing", "royal_flush", "amplify", "double_down"),
            new Def(ETERNAL, "Cassino da Fortuna Eterna", "Gire a roleta, puxe a alavanca, jogue os dados — hoje a casa sou eu!",
                    0xFF4AD2, 8, "casino_domain", "expand", "royal_flush", "amplify", "fate_coin", "double_down"),
            // pacote de técnicas de anime
            new Def(GUM_TRICK, "Truque do Mágico: Goma Elástica", "Elástica como borracha, grudenta como chiclete... você já é meu.",
                    0xFF6EC7, 4, "card_throw", "split", "homing", "bungee_gum", "texture_surprise"),
            new Def(ALL_OR_NOTHING, "Aposta Total: Tudo ou Nada", "Vamos apostar tudo — a sua vida contra a minha!", 0xFFC94A, 6,
                    "projectile", "gambler_all_in", "amplify", "double_down", "gambler_fate_dice"),
            new Def(IMMORTAL, "Morte Ociosa: Jackpot Imortal", "Expansão de Domínio... Morte Ociosa! Quatro minutos e onze segundos de sorte.",
                    0x3AE07A, 8, "idle_death_gamble", "expand", "gambler_fate_dice", "royal_flush", "double_down"));

    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (Def def : DEFS) {
                List<ResourceLocation> ids = new ArrayList<>();
                boolean ok = true;
                for (String g : def.glyphs()) {
                    ResourceLocation rl = IsekaiMahou.id(g);
                    if (GlyphRegistry.get(rl) == null) {
                        ok = false;
                        break;
                    }
                    ids.add(rl);
                }
                SpellRecipe recipe = new SpellRecipe(ids, def.name(), def.color(), def.chant());
                SpellParser.Result r = ok ? SpellParser.parse(recipe, Element.ARCANE, null) : null;
                if (r == null || !r.ok() || def.glyphs().length > Progression.maxGlyphs(Progression.levelCap(def.circle()), def.circle())) {
                    IsekaiMahou.LOGGER.warn("Magia Lendária '{}' (class_gambler) não validou e não foi registrada.", def.id());
                    continue;
                }
                Legends.register(def.id(), recipe, Element.ARCANE, def.circle());
            }
        });
    }

    private GamblerLegends() {}
}
