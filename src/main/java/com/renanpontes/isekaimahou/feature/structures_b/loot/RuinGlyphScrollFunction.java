package com.renanpontes.isekaimahou.feature.structures_b.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.structures_b.StructuresBContent;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * {@code isekai_mahou:ruin_glyph_scroll} — escolhe o glifo de um Pergaminho de Glifo NA HORA do loot,
 * entre os glifos realmente registrados (módulos ausentes não quebram o baú).
 *
 * <pre>
 * glyphs         lista preferida (ids). Se algum existir e passar no filtro de tier, sorteia entre eles.
 * elements       filtro de elemento para o sorteio geral ("fire", "arcane"...). Vazio = qualquer.
 * min_tier/max_tier  faixa de tier (padrão 1–3).
 * prefer_unknown se quem abre o baú é um jogador, prefere glifos que ele ainda não conhece (padrão true).
 * </pre>
 * Sem nenhum candidato, o pergaminho vira um Cristal de Mana (nunca sai um pergaminho "em branco").
 * Leis, formas supremas, glifos lendários e de Magia de Origem nunca saem (nem pela lista nem pelo sorteio geral).
 */
public class RuinGlyphScrollFunction extends LootItemConditionalFunction {
    public static final MapCodec<RuinGlyphScrollFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .and(ResourceLocation.CODEC.listOf().optionalFieldOf("glyphs", List.of()).forGetter(f -> f.glyphs))
            .and(Codec.STRING.listOf().optionalFieldOf("elements", List.of()).forGetter(f -> f.elements))
            .and(Codec.intRange(1, 5).optionalFieldOf("min_tier", 1).forGetter(f -> f.minTier))
            .and(Codec.intRange(1, 5).optionalFieldOf("max_tier", 3).forGetter(f -> f.maxTier))
            .and(Codec.BOOL.optionalFieldOf("prefer_unknown", true).forGetter(f -> f.preferUnknown))
            .apply(i, RuinGlyphScrollFunction::new));

    private final List<ResourceLocation> glyphs;
    private final List<String> elements;
    private final int minTier;
    private final int maxTier;
    private final boolean preferUnknown;

    private RuinGlyphScrollFunction(List<LootItemCondition> conditions, List<ResourceLocation> glyphs, List<String> elements,
                                    int minTier, int maxTier, boolean preferUnknown) {
        super(conditions);
        this.glyphs = glyphs;
        this.elements = elements;
        this.minTier = Math.min(minTier, maxTier);
        this.maxTier = Math.max(minTier, maxTier);
        this.preferUnknown = preferUnknown;
    }

    @Override
    public LootItemFunctionType<RuinGlyphScrollFunction> getType() {
        return StructuresBContent.RUIN_GLYPH_SCROLL.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext ctx) {
        List<Glyph> pool = new ArrayList<>();
        for (ResourceLocation id : glyphs) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && lootable(g)) pool.add(g);
        }
        if (pool.isEmpty()) {
            for (Glyph g : GlyphRegistry.all()) {
                if (randomAllowed(g)) pool.add(g);
            }
        }
        if (pool.isEmpty()) return new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), stack.getCount());

        Entity opener = ctx.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (preferUnknown && opener instanceof Player player) {
            List<Glyph> unknown = new ArrayList<>();
            for (Glyph g : pool) if (!MagicApi.get(player).knowsGlyph(g.id())) unknown.add(g);
            if (!unknown.isEmpty()) pool = unknown;
        }
        Glyph pick = pick(pool, ctx.getRandom());
        ItemStack out = stack.is(CoreRegistries.GLYPH_SCROLL.get()) ? stack : new ItemStack(CoreRegistries.GLYPH_SCROLL.get(), stack.getCount());
        out.set(CoreRegistries.GLYPH.get(), pick.id());
        return out;
    }

    private boolean inTier(Glyph g) {
        return g.tier() >= minTier && g.tier() <= maxTier;
    }

    /** Pode sair de baú: na faixa de tier e nunca lendário, de Magia de Origem, Lei ou forma suprema (vale também para a lista). */
    private boolean lootable(Glyph g) {
        if (!inTier(g) || g.isLegendary() || g.origin() != null || g.kind() == GlyphKind.LAW) return false;
        return !(g instanceof FormGlyph form && form.isUltimate());
    }

    private boolean randomAllowed(Glyph g) {
        if (!lootable(g)) return false;
        if (elements.isEmpty()) return true;
        for (String e : elements) {
            Element el = Element.byId(e);
            if (el != null && g.element() == el) return true;
        }
        return false;
    }

    /** Glifos mais baratos aparecem um pouco mais (são mais "comuns" em ruínas). */
    private static Glyph pick(List<Glyph> pool, RandomSource random) {
        int total = 0;
        int[] w = new int[pool.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = Math.max(1, 6 - pool.get(i).tier());
            total += w[i];
        }
        int r = random.nextInt(total);
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }
}
