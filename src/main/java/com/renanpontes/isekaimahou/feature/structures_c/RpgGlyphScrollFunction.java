package com.renanpontes.isekaimahou.feature.structures_c;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Função de loot "isekai_mahou:rpg_glyph_scroll": grava no Pergaminho de Glifo um glifo SORTEADO entre os que
 * existem no jogo (de qualquer módulo), filtrado por elemento/tipo/tier. Assim os baús nunca apontam para um
 * glifo que não existe, e o sorteio prefere glifos que quem abriu o baú ainda não conhece.
 * <pre>{"function": "isekai_mahou:rpg_glyph_scroll", "elements": ["shadow"], "min_tier": 1, "max_tier": 3}</pre>
 * Campos opcionais: elements (ids de Element; vazio = qualquer), kinds (effect/form/augment/trigger/law;
 * padrão effect+form+augment), min_tier/max_tier (1-5), allow_legendary (false), prefer_unknown (true).
 * Glifos de Magia de Origem (de classe) ficam de fora — eles vêm dos rituais.
 */
public class RpgGlyphScrollFunction extends LootItemConditionalFunction {
    private static final List<String> DEFAULT_KINDS = List.of("effect", "form", "augment");
    /** Ids validados na leitura do JSON: erro de digitação aparece no log em vez de virar filtro vazio. */
    private static final Codec<String> ELEMENT_ID = Codec.STRING.validate(s -> Element.byId(s) != null
            ? DataResult.success(s) : DataResult.error(() -> "Elemento desconhecido: " + s));
    private static final Codec<String> KIND_ID = Codec.STRING.validate(s -> isKnownKind(s)
            ? DataResult.success(s) : DataResult.error(() -> "Tipo de glifo desconhecido: " + s));

    public static final MapCodec<RpgGlyphScrollFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).and(i.group(
            ELEMENT_ID.listOf().optionalFieldOf("elements", List.of()).forGetter(f -> f.elements),
            KIND_ID.listOf().optionalFieldOf("kinds", DEFAULT_KINDS).forGetter(f -> f.kinds),
            Codec.intRange(1, 5).optionalFieldOf("min_tier", 1).forGetter(f -> f.minTier),
            Codec.intRange(1, 5).optionalFieldOf("max_tier", 3).forGetter(f -> f.maxTier),
            Codec.BOOL.optionalFieldOf("allow_legendary", false).forGetter(f -> f.allowLegendary),
            Codec.BOOL.optionalFieldOf("prefer_unknown", true).forGetter(f -> f.preferUnknown)
    )).apply(i, RpgGlyphScrollFunction::new));

    private final List<String> elements;
    private final List<String> kinds;
    private final int minTier;
    private final int maxTier;
    private final boolean allowLegendary;
    private final boolean preferUnknown;

    protected RpgGlyphScrollFunction(List<LootItemCondition> conditions, List<String> elements, List<String> kinds,
                                     int minTier, int maxTier, boolean allowLegendary, boolean preferUnknown) {
        super(conditions);
        this.elements = elements;
        this.kinds = kinds;
        this.minTier = Math.min(minTier, maxTier);
        this.maxTier = Math.max(minTier, maxTier);
        this.allowLegendary = allowLegendary;
        this.preferUnknown = preferUnknown;
    }

    @Override
    public LootItemFunctionType<RpgGlyphScrollFunction> getType() {
        return ScRegistries.RPG_GLYPH_SCROLL.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        List<Glyph> pool = candidates(true);
        if (pool.isEmpty()) pool = candidates(false);   // ninguém do elemento pedido: cai para glifos arcanos
        if (pool.isEmpty()) return ItemStack.EMPTY;     // nada serve: melhor não dar um pergaminho em branco
        Entity opener = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (preferUnknown && opener instanceof Player player) {
            MagicData data = MagicApi.get(player);
            List<Glyph> unknown = pool.stream().filter(g -> !data.knowsGlyph(g.id())).toList();
            if (!unknown.isEmpty()) pool = unknown;
        }
        Glyph chosen = pool.get(context.getRandom().nextInt(pool.size()));
        stack.set(CoreRegistries.GLYPH.get(), chosen.id());
        return stack;
    }

    private List<Glyph> candidates(boolean useElements) {
        List<Element> wanted = new ArrayList<>();
        if (useElements) {
            for (String id : elements) {
                Element e = Element.byId(id);
                if (e != null) wanted.add(e);
            }
        } else {
            wanted.add(Element.ARCANE);
        }
        List<Glyph> out = new ArrayList<>();
        for (Glyph g : GlyphRegistry.all()) {
            if (g.origin() != null) continue;
            if (!kinds.contains(g.kind().name().toLowerCase(Locale.ROOT))) continue;
            if (g.isLegendary() && !allowLegendary) continue;
            int tier = g.tier();
            if (tier < minTier || tier > maxTier) continue;
            if (!wanted.isEmpty() && !wanted.contains(g.element())) continue;
            out.add(g);
        }
        return out;
    }

    private static boolean isKnownKind(String kind) {
        for (GlyphKind k : GlyphKind.values()) {
            if (k.name().equalsIgnoreCase(kind)) return true;
        }
        return false;
    }
}
