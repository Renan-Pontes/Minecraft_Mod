package com.renanpontes.isekaimahou.feature.espada;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Magias Lendárias dos Espada (uma por chefe, cai como pergaminho na vitória). Os glifos vêm de outros módulos: no setup
 * comum (todos os glifos já registrados) a primeira variante que passa no SpellParser é registrada; se nenhuma passar,
 * o chefe só não solta o pergaminho (o log avisa). Nome: {@code legend.isekai_mahou.<id>}.
 * <p>
 * w10: a primeira variante agora é a TÉCNICA de verdade ({@link EspadaTechniques}: garras lançadas, Respira em 360°,
 * Lanza com a explosão gigante). Quem derrota o Espada aprende a forma ({@link #teach}); as variantes antigas (glifos
 * comuns) só ficam de reserva caso a técnica não valide.
 */
public final class EspadaLegends {
    public static final String ULQUIORRA = "espada_lanza_relampago";
    public static final String BARRAGAN = "espada_respira";
    public static final String GRIMMJOW = "espada_desgarron";

    private record Def(String id, String name, int color, String chant, int minCircle, Element element, String[][] variants) {}

    private static final List<Def> DEFS = List.of(
            new Def(ULQUIORRA, "Lanza del Relámpago", 0x3DFF8E, "Afaste-se... se puder. Lanza del Relámpago.", 8, Element.SHADOW,
                    new String[][] {
                            {EspadaTechniques.LANZA, "amplify"},
                            {"projectile", "hollow_cero", "amplify", "amplify", "pierce", "expand"},
                            {"projectile", "hollow_cero", "amplify", "pierce"},
                            {"projectile", "darkness", "curse", "amplify", "expand"},
                            {"projectile", "arcane_damage", "amplify", "amplify", "expand"}}),
            new Def(BARRAGAN, "Respira", 0x7A4E96, "Tudo apodrece diante do rei. Respira.", 7, Element.SHADOW,
                    new String[][] {
                            {EspadaTechniques.RESPIRA, "expand"},
                            {"cone", "curse", "weaken", "darkness", "extend", "expand"},
                            {"cone", "curse", "weaken", "expand"},
                            {"cone", "weaken", "amplify", "expand"},
                            {"cone", "arcane_damage", "expand"}}),
            new Def(GRIMMJOW, "Desgarrón", 0x39C2FF, "Rasga tudo, Pantera! Desgarrón!", 6, Element.WIND,
                    new String[][] {
                            {EspadaTechniques.DESGARRON, "amplify"},
                            {"slash", "slash_storm", "amplify", "split", "split"},
                            {"slash", "slash_storm", "amplify"},
                            {"slash", "arcane_damage", "split", "split", "amplify"},
                            {"slash", "arcane_damage", "amplify"}}));

    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (Def def : DEFS) register(def);
        });
    }

    private static void register(Def def) {
        for (String[] ids : def.variants) {
            List<ResourceLocation> list = new ArrayList<>();
            boolean ok = true;
            for (String id : ids) {
                ResourceLocation rl = IsekaiMahou.id(id);
                if (GlyphRegistry.get(rl) == null) {
                    ok = false;
                    break;
                }
                list.add(rl);
            }
            if (!ok) continue;
            SpellRecipe recipe = new SpellRecipe(list, def.name, def.color, def.chant);
            if (!SpellParser.parse(recipe, def.element, null).ok()) continue;
            Legends.register(def.id, recipe, def.element, def.minCircle);
            return;
        }
        IsekaiMahou.LOGGER.warn("Magia Lendária '{}' (espada): nenhuma variante validou; o chefe não solta o pergaminho.", def.id);
    }

    /** Forma (técnica) de cada lenda. */
    private static String technique(String legendId) {
        return switch (legendId) {
            case ULQUIORRA -> EspadaTechniques.LANZA;
            case BARRAGAN -> EspadaTechniques.RESPIRA;
            case GRIMMJOW -> EspadaTechniques.DESGARRON;
            default -> null;
        };
    }

    /**
     * w10: o Espada caiu — quem lutou (jogadores a até 64 blocos) APRENDE a técnica dele (a forma da Magia Lendária),
     * com mensagem. Chamado pelo próprio chefe junto do loot. Quem já sabe não recebe nada de novo.
     */
    public static void teach(LivingEntity boss, String legendId) {
        String tech = technique(legendId);
        if (tech == null || GlyphRegistry.get(IsekaiMahou.id(tech)) == null) return;
        Legends.Legend legend = Legends.get(legendId);
        for (ServerPlayer sp : BossKit.partyAround(boss, 64)) {
            if (!MagicApi.learnGlyph(sp, IsekaiMahou.id(tech))) continue;
            Component name = legend != null ? legend.title().withColor(legend.color()) : Component.literal(tech);
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.espada.technique_learned", name)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Pergaminho da lenda (vazio se não foi registrada). */
    public static ItemStack scroll(String id) {
        return Legends.get(id) != null ? Legends.scroll(id) : ItemStack.EMPTY;
    }

    private EspadaLegends() {}
}
