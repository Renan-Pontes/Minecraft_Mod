package com.renanpontes.isekaimahou.feature.journal.data;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Recompensas de um capítulo (ou de um passo): XP mágico, itens (por id; ids ausentes são ignorados na entrega),
 * Pergaminhos de Glifo aleatórios (até um tier), glifos específicos, essências dos elementos despertados do jogador
 * e Pergaminhos Lendários sorteados (Magias Lendárias registradas no núcleo).
 *
 * @param elementEssences essências de CADA elemento base despertado (sem nenhum: essências arcanas)
 * @param legendScrolls   Pergaminhos Lendários (só lendas do Círculo do jogador ou abaixo; sem lendas: cristais)
 */
public record JournalRewards(int xp, List<ItemReward> items, int scrolls, int scrollTier, List<ResourceLocation> glyphs,
                             int elementEssences, int legendScrolls) {
    public static final JournalRewards NONE = new JournalRewards(0, List.of(), 0, 1, List.of(), 0, 0);

    public record ItemReward(ResourceLocation id, int count) {}

    public static JournalRewards parse(JsonObject o) {
        List<ItemReward> items = new ArrayList<>();
        if (o.has("items")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(o, "items")) {
                JsonObject it = e.getAsJsonObject();
                items.add(new ItemReward(ResourceLocation.parse(GsonHelper.getAsString(it, "id")),
                        Math.max(1, GsonHelper.getAsInt(it, "count", 1))));
            }
        }
        List<ResourceLocation> glyphs = new ArrayList<>();
        if (o.has("glyphs")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(o, "glyphs")) glyphs.add(JournalCondition.modId(e.getAsString()));
        }
        return new JournalRewards(Math.max(0, GsonHelper.getAsInt(o, "xp", 0)), List.copyOf(items),
                Math.max(0, GsonHelper.getAsInt(o, "glyph_scrolls", 0)),
                Math.max(1, Math.min(5, GsonHelper.getAsInt(o, "glyph_tier", 1))), List.copyOf(glyphs),
                Math.max(0, Math.min(16, GsonHelper.getAsInt(o, "element_essences", 0))),
                Math.max(0, Math.min(5, GsonHelper.getAsInt(o, "legend_scrolls", 0))));
    }

    public boolean isEmpty() {
        return xp <= 0 && items.isEmpty() && scrolls <= 0 && glyphs.isEmpty() && elementEssences <= 0 && legendScrolls <= 0;
    }

    /** Tem algo além de XP (itens, pergaminhos...)? */
    public boolean hasItems() {
        return !items.isEmpty() || scrolls > 0 || !glyphs.isEmpty() || elementEssences > 0 || legendScrolls > 0;
    }
}
