package com.renanpontes.isekaimahou.feature.ritual_necro.rite;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Um rank do Rito Ossuário (arquivo data/&lt;ns&gt;/necro_rites/rank_&lt;n&gt;.json).
 * {@link #souls} já vem resolvido: requisitos de mobs que não existem (módulo ausente) trocam pelo
 * {@code fallback} do arquivo, ou somem se nem o fallback existir.
 */
public record NecroRite(int rank, Map<ResourceLocation, Integer> souls, int xp, int glyphTier, Element awaken,
                        Map<Element, Integer> elementXp, int servantCap, int servantGift, int requiredSkulls, int minCircle) {

    /** Requisito de almas como escrito no arquivo. */
    public record SoulCost(ResourceLocation entity, int count, Optional<ResourceLocation> fallback, Optional<Integer> fallbackCount) {
        public static final Codec<SoulCost> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(SoulCost::entity),
                Codec.intRange(1, 99).fieldOf("count").forGetter(SoulCost::count),
                ResourceLocation.CODEC.optionalFieldOf("fallback").forGetter(SoulCost::fallback),
                Codec.intRange(1, 99).optionalFieldOf("fallback_count").forGetter(SoulCost::fallbackCount)
        ).apply(i, SoulCost::new));
    }

    /** Formato do arquivo (antes de resolver os mobs). */
    public record File(int rank, List<SoulCost> souls, int xp, int glyphTier, Optional<String> awaken, Map<String, Integer> elementXp,
                       int servantCap, int servantGift, int requiredSkulls, int minCircle) {
        public static final Codec<File> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("rank", 0).forGetter(File::rank),
                SoulCost.CODEC.listOf().fieldOf("souls").forGetter(File::souls),
                Codec.INT.optionalFieldOf("xp", 200).forGetter(File::xp),
                Codec.intRange(0, 5).optionalFieldOf("glyph_tier", 1).forGetter(File::glyphTier),
                Codec.STRING.optionalFieldOf("awaken").forGetter(File::awaken),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("element_xp", Map.of()).forGetter(File::elementXp),
                Codec.intRange(0, 10).optionalFieldOf("servant_cap", 0).forGetter(File::servantCap),
                Codec.intRange(0, 5).optionalFieldOf("servant_gift", 0).forGetter(File::servantGift),
                Codec.intRange(0, 4).optionalFieldOf("required_skulls", 0).forGetter(File::requiredSkulls),
                Codec.intRange(1, 10).optionalFieldOf("min_circle", 1).forGetter(File::minCircle)
        ).apply(i, File::new));

        /** Resolve ids de mobs e elementos. {@code rankFromName} vale quando o arquivo não informa "rank". */
        public NecroRite resolve(ResourceLocation fileId, int rankFromName) {
            Map<ResourceLocation, Integer> resolved = new LinkedHashMap<>();
            for (SoulCost c : souls) {
                if (BuiltInRegistries.ENTITY_TYPE.containsKey(c.entity())) {
                    resolved.merge(c.entity(), c.count(), Integer::sum);
                } else if (c.fallback().isPresent() && BuiltInRegistries.ENTITY_TYPE.containsKey(c.fallback().get())) {
                    IsekaiMahou.LOGGER.info("Rito Ossuario {}: {} nao existe, usando {}", fileId, c.entity(), c.fallback().get());
                    resolved.merge(c.fallback().get(), c.fallbackCount().orElse(c.count()), Integer::sum);
                } else {
                    IsekaiMahou.LOGGER.warn("Rito Ossuario {}: mob {} nao existe e nao tem fallback valido; requisito ignorado", fileId, c.entity());
                }
            }
            Element aw = awaken.map(Element::byId).orElse(null);
            Map<Element, Integer> exp = new LinkedHashMap<>();
            elementXp.forEach((k, v) -> {
                Element e = Element.byId(k);
                if (e != null && v > 0) exp.put(e, v);
            });
            return new NecroRite(rank > 0 ? rank : rankFromName, Collections.unmodifiableMap(resolved), Math.max(0, xp), glyphTier, aw,
                    Collections.unmodifiableMap(exp), servantCap, servantGift, requiredSkulls, minCircle);
        }
    }

    /** Recompensa mínima caso o arquivo do rank tenha sumido num /reload durante o rito. */
    public static NecroRite fallback(int rank) {
        return new NecroRite(rank, Map.of(), 250 * rank, Math.min(5, rank), null, Map.of(), 0, 0, 0, 1);
    }
}
