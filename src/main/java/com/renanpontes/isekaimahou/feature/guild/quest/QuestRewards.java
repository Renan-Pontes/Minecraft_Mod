package com.renanpontes.isekaimahou.feature.guild.quest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Recompensas de uma missão. {@code glyph}: "random" (glifo aleatório do tier do rank da missão) ou o id de um glifo.
 * {@code reputation} (v0.8): {@code {"kingdom": 20, "underworld": -10}} — reputação por facção do núcleo.
 */
public record QuestRewards(int coins, int xp, int points, Optional<String> glyph, int glyphCount, List<RewardItem> items,
                           Map<String, Integer> reputation) {
    public static final QuestRewards EMPTY = new QuestRewards(0, 0, 0, Optional.empty(), 1, List.of(), Map.of());

    public static final Codec<QuestRewards> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 100000).optionalFieldOf("coins", 0).forGetter(QuestRewards::coins),
            Codec.intRange(0, 1000000).optionalFieldOf("xp", 0).forGetter(QuestRewards::xp),
            Codec.intRange(0, 100000).optionalFieldOf("points", 0).forGetter(QuestRewards::points),
            Codec.STRING.optionalFieldOf("glyph").forGetter(QuestRewards::glyph),
            Codec.intRange(1, 16).optionalFieldOf("glyph_count", 1).forGetter(QuestRewards::glyphCount),
            RewardItem.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(QuestRewards::items),
            Codec.unboundedMap(Codec.STRING, Codec.intRange(-1000, 1000)).optionalFieldOf("reputation", Map.of())
                    .forGetter(QuestRewards::reputation)
    ).apply(i, QuestRewards::new));

    public boolean randomGlyph() {
        return glyph.isPresent() && glyph.get().equalsIgnoreCase("random");
    }
}
