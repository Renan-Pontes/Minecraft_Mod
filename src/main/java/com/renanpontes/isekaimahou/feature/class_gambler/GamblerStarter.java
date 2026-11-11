package com.renanpontes.isekaimahou.feature.class_gambler;

import java.util.List;
import java.util.Locale;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreGameplay;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.class_gambler.glyph.GamblerGlyphs;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ao escolher o Apostador: ensina os 3 glifos de origem iniciais (Carta, Cara ou Coroa, Amuleto da Sorte) e troca as
 * magias prontas de Destreza do loadout 1 pelas do Apostador — Z "Carta Carregada", X "Cara ou Coroa" (leque de 3
 * cartas) e C "Amuleto da Sorte" — só se o loadout ainda tiver as magias iniciais do núcleo (nunca apaga as do jogador).
 */
public final class GamblerStarter {
    private static final ResourceLocation ARCANE_DAMAGE = IsekaiMahou.id("arcane_damage");
    private static final ResourceLocation SPLIT = IsekaiMahou.id("split");
    private static final ResourceLocation SELF = IsekaiMahou.id("self");

    public static void onPathChosen(MagicProgressEvent.PathChosen event) {
        ServerPlayer sp = event.getPlayer();
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.GAMBLER) return;
        for (ResourceLocation id : GamblerGlyphs.STARTER) MagicApi.learnGlyphSilently(sp, id);
        boolean pt = sp.clientInformation().language().toLowerCase(Locale.ROOT).startsWith("pt");
        List<SpellRecipe> spells = List.of(
                new SpellRecipe(List.of(GamblerGlyphs.CARD_THROW_ID, ARCANE_DAMAGE), pt ? "Carta Carregada" : "Charged Card", 0xFF4A5A, ""),
                new SpellRecipe(List.of(GamblerGlyphs.CARD_THROW_ID, SPLIT, GamblerGlyphs.FATE_COIN_ID), pt ? "Cara ou Coroa" : "Heads or Tails",
                        0xFFC94A, ""),
                new SpellRecipe(List.of(SELF, GamblerGlyphs.LUCKY_CHARM_ID), pt ? "Amuleto da Sorte" : "Lucky Charm", 0x6FD88A, ""));
        boolean changed = false;
        for (int i = 0; i < spells.size(); i++) {
            SpellSlot slot = d.peekSlot(0, i);
            SpellRecipe cur = slot.main();
            if (cur == null || cur.isEmpty() || isCoreStarter(cur)) {
                d.setSlotRecipe(0, i, MoveTrigger.NORMAL, spells.get(i));
                changed = true;
            }
        }
        // sem chat aqui: a entrada no jogo fica quieta até o tutorial (o núcleo cuida do "Parabéns!")
        if (changed) d.markDirty();
    }

    private static boolean isCoreStarter(SpellRecipe r) {
        return r.glyphs().equals(CoreGameplay.STARTER_ARROW.glyphs()) || r.glyphs().equals(CoreGameplay.STARTER_PUSH_ARROW.glyphs());
    }

    private GamblerStarter() {}
}
