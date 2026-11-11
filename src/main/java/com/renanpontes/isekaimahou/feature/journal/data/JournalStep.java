package com.renanpontes.isekaimahou.feature.journal.data;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Um passo (objetivo) de um capítulo. Textos por tradução, derivados dos ids:
 * {@code journal.isekai_mahou.<capitulo>.<passo>} (objetivo), {@code .lore} (crônica), {@code .hint} (dica do Espírito
 * Guia) e {@code .tip} (dica curtinha do rastreador de objetivos).
 *
 * @param optional     passos opcionais não bloqueiam o capítulo (dão só o bônus)
 * @param xp           XP mágico extra ao concluir o passo
 * @param icon         item mostrado no rastreador (null = o do capítulo)
 * @param locate       estrutura (ou bioma) para onde o rastreador aponta enquanto este é o objetivo atual (null = nenhum)
 * @param rewards      recompensas extras ao concluir o passo
 * @param requires     ids que precisam existir no jogo (item, bloco, entidade ou glifo; "legends" = há Magias Lendárias).
 *                     Se faltar algum (módulo ausente), o passo some do Diário e não bloqueia o capítulo.
 * @param hintBy       {@code "path"} ou {@code "category"}: a dica e a dica curta têm variantes por classe
 *                     ({@code .hint.<classe>}) ou por categoria ({@code .hint.<magic|martial|ranged>}); null = uma só
 * @param rewardUnless se já for verdade quando o passo se cumpre, as {@code rewards} não são entregues (ex.: tributo de
 *                     Ascensão para quem já está naquele Círculo); o XP do passo vale sempre
 */
public record JournalStep(String id, JournalCondition condition, boolean optional, int xp, @Nullable ResourceLocation icon,
                          @Nullable JournalLocate locate, JournalRewards rewards, List<String> requires,
                          @Nullable String hintBy, @Nullable JournalCondition rewardUnless) {

    public static JournalStep parse(JsonObject o) {
        String id = GsonHelper.getAsString(o, "id");
        if (!id.matches("[a-z0-9_]+") || id.equals("begun")) throw new JsonParseException("id de passo invalido: " + id);
        List<String> requires = new ArrayList<>();
        if (o.has("requires")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(o, "requires")) requires.add(e.getAsString());
        }
        String hintBy = o.has("hint_by") ? GsonHelper.getAsString(o, "hint_by") : null;
        if (hintBy != null && !hintBy.equals("path") && !hintBy.equals("category")) {
            throw new JsonParseException("hint_by invalido (path|category): " + hintBy);
        }
        return new JournalStep(id, JournalCondition.parse(GsonHelper.getAsJsonObject(o, "condition")),
                GsonHelper.getAsBoolean(o, "optional", false), Math.max(0, GsonHelper.getAsInt(o, "xp", 0)),
                o.has("icon") ? ResourceLocation.parse(GsonHelper.getAsString(o, "icon")) : null,
                o.has("locate") ? JournalLocate.parse(GsonHelper.getAsJsonObject(o, "locate")) : null,
                o.has("rewards") ? JournalRewards.parse(GsonHelper.getAsJsonObject(o, "rewards")) : JournalRewards.NONE,
                List.copyOf(requires), hintBy,
                o.has("reward_unless") ? JournalCondition.parse(GsonHelper.getAsJsonObject(o, "reward_unless")) : null);
    }

    /** O passo existe neste jogo? (requisitos presentes e condição cumprível). Igual no cliente e no servidor. */
    public boolean available() {
        for (String r : requires) if (!present(r)) return false;
        return condition.available();
    }

    private static boolean present(String token) {
        if (token.equals("legends")) return !Legends.all().isEmpty();
        ResourceLocation id = JournalCondition.modId(token);
        return BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.BLOCK.containsKey(id)
                || BuiltInRegistries.ENTITY_TYPE.containsKey(id) || GlyphRegistry.get(id) != null;
    }

    /** Marcador gravado no MagicData quando o passo é concluído (convenção: journal.&lt;capitulo&gt;.&lt;passo&gt;). */
    public String flag(JournalChapter chapter) {
        return "journal." + chapter.id() + "." + id;
    }

    /** Identificador "capitulo.passo" (alvos do rastreador). */
    public String uid(JournalChapter chapter) {
        return chapter.id() + "." + id;
    }

    public String key(JournalChapter chapter) {
        return "journal.isekai_mahou." + chapter.id() + "." + id;
    }

    public MutableComponent title(JournalChapter chapter) {
        return Component.translatable(key(chapter));
    }

    public MutableComponent lore(JournalChapter chapter) {
        return Component.translatable(key(chapter) + ".lore");
    }

    /** Dica longa (sem variante de classe). */
    public MutableComponent hint(JournalChapter chapter) {
        return Component.translatable(hintKey(chapter, null));
    }

    /** Dica longa na variante da classe do jogador (passos com {@code hint_by}). */
    public MutableComponent hint(JournalChapter chapter, @Nullable MagicPath path) {
        return Component.translatable(hintKey(chapter, path));
    }

    /** Chave da dica longa: {@code .hint}, ou {@code .hint.<classe|categoria>} quando o passo tem {@code hint_by}. */
    public String hintKey(JournalChapter chapter, @Nullable MagicPath path) {
        return key(chapter) + ".hint" + variant(path);
    }

    /** Chave da dica curta do rastreador (pode não existir em capítulos de datapacks: use a dica longa). */
    public String tipKey(JournalChapter chapter) {
        return tipKey(chapter, null);
    }

    public String tipKey(JournalChapter chapter, @Nullable MagicPath path) {
        return key(chapter) + ".tip" + variant(path);
    }

    /** Sufixo da variante por classe/categoria ("" sem {@code hint_by} ou sem classe). */
    private String variant(@Nullable MagicPath path) {
        if (hintBy == null || path == null || path == MagicPath.NONE) return "";
        return "." + (hintBy.equals("category") ? path.category().id : path.id);
    }
}
