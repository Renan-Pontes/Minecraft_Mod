package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.resources.ResourceLocation;

/**
 * Os 10 glifos de origem do Apostador + a forma G (todos {@code origin(GAMBLER)}: montáveis no Grimório com qualquer
 * elemento, ensináveis às outras classes só por Lição):
 * <ul>
 *   <li>Formas: Carta ({@code card_throw}), Dados ({@code dice_roll}), Roleta ({@code roulette_wheel}),
 *   Caça-Níquel ({@code slot_machine}) e o Domínio Cassino ({@code casino_domain}, ultimate G);</li>
 *   <li>Efeitos: Cara ou Coroa ({@code fate_coin}), Amuleto da Sorte ({@code lucky_charm}), Mão Real ({@code royal_flush});</li>
 *   <li>Aumentos: Dobrar a Aposta ({@code double_down}), Dados Viciados ({@code loaded_dice});</li>
 *   <li>Gatilho: Ao Jackpot ({@code trigger_jackpot}).</li>
 *   <li>Pacote de técnicas de anime: Dados do Destino ({@code gambler_fate_dice}), Goma Elástica ({@code bungee_gum}),
 *   Textura Surpresa ({@code texture_surprise}), Aposta Total ({@code gambler_all_in}) e a 2ª forma G, Domínio da
 *   Morte Ociosa ({@code idle_death_gamble}).</li>
 * </ul>
 */
public final class GamblerGlyphs {
    public static final ResourceLocation CARD_THROW_ID = IsekaiMahou.id("card_throw");
    public static final ResourceLocation DICE_ROLL_ID = IsekaiMahou.id("dice_roll");
    public static final ResourceLocation ROULETTE_ID = IsekaiMahou.id("roulette_wheel");
    public static final ResourceLocation SLOT_MACHINE_ID = IsekaiMahou.id("slot_machine");
    public static final ResourceLocation CASINO_DOMAIN_ID = IsekaiMahou.id("casino_domain");
    public static final ResourceLocation FATE_COIN_ID = IsekaiMahou.id("fate_coin");
    public static final ResourceLocation LUCKY_CHARM_ID = IsekaiMahou.id("lucky_charm");
    public static final ResourceLocation ROYAL_FLUSH_ID = IsekaiMahou.id("royal_flush");
    public static final ResourceLocation DOUBLE_DOWN_ID = IsekaiMahou.id("double_down");
    public static final ResourceLocation LOADED_DICE_ID = IsekaiMahou.id("loaded_dice");
    public static final ResourceLocation TRIGGER_JACKPOT_ID = IsekaiMahou.id("trigger_jackpot");
    // pacote de técnicas de anime
    public static final ResourceLocation FATE_DICE_ID = IsekaiMahou.id("gambler_fate_dice");
    public static final ResourceLocation BUNGEE_GUM_ID = IsekaiMahou.id("bungee_gum");
    public static final ResourceLocation TEXTURE_SURPRISE_ID = IsekaiMahou.id("texture_surprise");
    public static final ResourceLocation ALL_IN_ID = IsekaiMahou.id("gambler_all_in");
    public static final ResourceLocation IDLE_DEATH_ID = IsekaiMahou.id("idle_death_gamble");

    /** Ensinados ao escolher a classe (e gravados nas magias iniciais). */
    public static final List<ResourceLocation> STARTER = List.of(CARD_THROW_ID, FATE_COIN_ID, LUCKY_CHARM_ID);

    public static void register() {
        GlyphRegistry.register(new CardThrowForm());
        GlyphRegistry.register(new DiceRollForm());
        GlyphRegistry.register(new RouletteForm());
        GlyphRegistry.register(new SlotMachineForm());
        GlyphRegistry.register(new CasinoDomainForm());
        GlyphRegistry.register(new FateCoinEffect());
        GlyphRegistry.register(new LuckyCharmEffect());
        GlyphRegistry.register(new RoyalFlushEffect());
        // Dobrar a Aposta: o resultado da sorte é exagerado (o bom fica melhor, o ruim pior); 2× chance de Jackpot
        GlyphRegistry.register(new AugmentGlyph("double_down", 6,
                s -> s.custom.merge(DOUBLE_DOWN_ID, 1, Integer::sum)).origin(MagicPath.GAMBLER).tier(2));
        // Dados Viciados: sem azar total/azar e sem sorte grande — resultado estável (metade da chance de Jackpot)
        GlyphRegistry.register(new AugmentGlyph("loaded_dice", 5,
                s -> s.custom.merge(LOADED_DICE_ID, 1, Integer::sum)).origin(MagicPath.GAMBLER).tier(3).minCircle(2));
        GlyphRegistry.register(new JackpotTrigger());
        // Pacote de técnicas de anime: Dados do Destino, Goma Elástica, Textura Surpresa, Aposta Total, Morte Ociosa
        GlyphRegistry.register(new FateDiceEffect());
        GlyphRegistry.register(new BungeeGumEffect());
        GlyphRegistry.register(new TextureSurpriseEffect());
        GlyphRegistry.register(new AllInEffect());
        GlyphRegistry.register(new IdleDeathGambleForm());
    }

    private GamblerGlyphs() {}
}
