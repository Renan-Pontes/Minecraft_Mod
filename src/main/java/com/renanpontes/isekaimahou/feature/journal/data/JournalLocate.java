package com.renanpontes.isekaimahou.feature.journal.data;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;

/**
 * Campo opcional {@code "locate"} de um passo: para onde o rastreador aponta quando o passo vira o objetivo atual.
 * O servidor procura a estrutura mais próxima do jogador ({@code JournalLocator}) e manda a posição ao cliente.
 * <pre>
 * {"structure": "isekai_mahou:guild_hall_village"}
 * {"tag": "isekai_mahou:elemental_shrines", "dimension": "minecraft:overworld"}
 * {"any": [{"structure": "isekai_mahou:shrine_fire", "unless": {"type": "counter", "key": "shrine.fire"}}, ...]}
 * {"biome": "#c:is_swamp", "dimension": "minecraft:overworld"}     (bioma ou tag de bioma: chefes sem covil)
 * {"place": "hollow_palace", "dimension": "isekai_mahou:hollow_world"}  (v1.0: lugar do Places — palácio, corte, hub)
 * </pre>
 * {@code unless} (uma condição do Diário) tira a estrutura da busca quando já foi cumprida (santuário já vencido,
 * guardião já derrotado...). {@code dimension}: só procura quando o jogador está nela.
 */
public record JournalLocate(List<Target> targets, @Nullable ResourceLocation dimension) {

    /**
     * Uma estrutura (ou tag de estruturas) candidata — ou um bioma/tag de bioma ({@code biome}), ou um lugar registrado no
     * {@code Places} ({@code place}: o id fica no caminho de {@link #id}).
     */
    public record Target(ResourceLocation id, boolean tag, boolean biome, boolean place, @Nullable JournalCondition unless) {
        /** "#tag" ou "id" ("~" na frente para biomas, "@" para lugares), para comparar buscas. */
        public String signature() {
            return (place ? "@" : "") + (biome ? "~" : "") + (tag ? "#" : "") + id;
        }
    }

    public static JournalLocate parse(JsonObject o) {
        List<Target> list = new ArrayList<>();
        if (o.has("any")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(o, "any")) list.add(target(e.getAsJsonObject()));
        } else {
            list.add(target(o));
        }
        if (list.isEmpty()) throw new JsonParseException("locate sem estruturas");
        ResourceLocation dim = o.has("dimension") ? ResourceLocation.parse(GsonHelper.getAsString(o, "dimension")) : null;
        return new JournalLocate(List.copyOf(list), dim);
    }

    private static Target target(JsonObject o) {
        JournalCondition unless = o.has("unless") ? JournalCondition.parse(GsonHelper.getAsJsonObject(o, "unless")) : null;
        if (o.has("structure")) return new Target(JournalCondition.modId(GsonHelper.getAsString(o, "structure")), false, false, false, unless);
        if (o.has("tag")) {
            String t = GsonHelper.getAsString(o, "tag");
            return new Target(JournalCondition.modId(t.startsWith("#") ? t.substring(1) : t), true, false, false, unless);
        }
        if (o.has("biome")) {
            String b = GsonHelper.getAsString(o, "biome");
            boolean tag = b.startsWith("#");
            return new Target(ResourceLocation.parse(tag ? b.substring(1) : b), tag, true, false, unless);
        }
        if (o.has("place")) {
            String id = GsonHelper.getAsString(o, "place");
            if (!ResourceLocation.isValidPath(id)) throw new JsonParseException("locate: lugar invalido: " + id);
            return new Target(JournalCondition.modId(id), false, false, true, unless);
        }
        throw new JsonParseException("locate: informe \"structure\", \"tag\", \"biome\", \"place\" ou \"any\"");
    }

    /** Candidatas que ainda valem para este jogador (sem as já cumpridas pelo {@code unless}). */
    public List<Target> active(MagicData d, @Nullable Player player) {
        List<Target> out = new ArrayList<>(targets.size());
        for (Target t : targets) if (t.unless() == null || !t.unless().done(d, player)) out.add(t);
        return out;
    }
}
