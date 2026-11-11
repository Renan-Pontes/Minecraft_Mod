package com.renanpontes.isekaimahou.feature.guild.quest;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.guild.GuildRank;

import net.minecraft.resources.ResourceLocation;

/**
 * Uma oferta da loja da Guilda ({@code data/<ns>/guild_shop/<arquivo>.json → "offers": [...]}).
 * Vende um item ({@code item}), um pergaminho de glifo específico ({@code glyph}), um pergaminho aleatório de um tier
 * ({@code glyph_tier}: glifo que o jogador ainda não conhece) ou um Pergaminho Lendário aleatório
 * ({@code "legend_scroll": true}: uma Magia Lendária pronta, de preferência uma que o Círculo do jogador já permite).
 * v0.8: {@code "standing"} (opcional) exige também um patamar de reputação com a Guilda (friendly, honored...); o preço
 * final é {@code price × Reputation.priceMultiplier(jogador, "guild")}.
 */
public record ShopOffer(GuildRank rank, int price, Optional<RewardItem> item, Optional<ResourceLocation> glyph, int glyphTier,
                        boolean legendScroll, Optional<Reputation.Standing> standing) {
    private static final Codec<Reputation.Standing> STANDING_CODEC = Codec.STRING.comapFlatMap(s -> {
        Reputation.Standing st = Reputation.Standing.byId(s.toLowerCase(java.util.Locale.ROOT));
        return st == null ? DataResult.error(() -> "Patamar invalido: " + s) : DataResult.success(st);
    }, st -> st.id);

    public static final Codec<ShopOffer> CODEC = RecordCodecBuilder.<ShopOffer>create(i -> i.group(
            GuildRank.CODEC.optionalFieldOf("rank", GuildRank.F).forGetter(ShopOffer::rank),
            Codec.intRange(1, 100000).fieldOf("price").forGetter(ShopOffer::price),
            RewardItem.CODEC.optionalFieldOf("item").forGetter(ShopOffer::item),
            ResourceLocation.CODEC.optionalFieldOf("glyph").forGetter(ShopOffer::glyph),
            Codec.intRange(0, 5).optionalFieldOf("glyph_tier", 0).forGetter(ShopOffer::glyphTier),
            Codec.BOOL.optionalFieldOf("legend_scroll", false).forGetter(ShopOffer::legendScroll),
            STANDING_CODEC.optionalFieldOf("standing").forGetter(ShopOffer::standing)
    ).apply(i, ShopOffer::new)).validate(o -> {
        int n = (o.item.isPresent() ? 1 : 0) + (o.glyph.isPresent() ? 1 : 0) + (o.glyphTier > 0 ? 1 : 0) + (o.legendScroll ? 1 : 0);
        return n == 1 ? DataResult.success(o)
                : DataResult.error(() -> "a oferta precisa de exatamente um: item, glyph, glyph_tier ou legend_scroll");
    });

    /** Preço para este jogador (reputação com a Guilda). */
    public int priceFor(net.minecraft.world.entity.player.Player player) {
        return Math.max(1, Math.round(price * Reputation.priceMultiplier(player, "guild")));
    }

    /** Patamar com a Guilda alcançado (ou a oferta não exige nenhum)? */
    public boolean standingOk(net.minecraft.world.entity.player.Player player) {
        return standing.isEmpty() || Reputation.standing(player, "guild").atLeast(standing.get());
    }

    public static final Codec<java.util.List<ShopOffer>> FILE_CODEC =
            RecordCodecBuilder.create(i -> i.group(CODEC.listOf().fieldOf("offers").forGetter(l -> l)).apply(i, l -> l));
}
