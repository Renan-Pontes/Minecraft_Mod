package com.renanpontes.isekaimahou.feature.guild;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Ranks da Guilda dos Aventureiros (F → S).
 * {@link #level()} = 1..7 é o valor espelhado no contador "guild_rank" do MagicData (0 = não registrado),
 * que o HUD (ui_hud) lê. As cores são as mesmas do HUD.
 */
public enum GuildRank {
    F(0xFF9AA4B0, 0, 1, 1),
    E(0xFF6BD66B, 30, 1, 1),
    D(0xFF4FD8D8, 90, 2, 2),
    C(0xFF5B9BFF, 200, 2, 3),
    B(0xFFB46BFF, 380, 3, 3),
    A(0xFFFFC94F, 650, 4, 4),
    S(0xFFFF5B5B, 1050, 4, 5);

    public static final Codec<GuildRank> CODEC = Codec.STRING.comapFlatMap(s -> {
        GuildRank r = byLetter(s);
        return r == null ? DataResult.error(() -> "Rank da Guilda invalido: " + s) : DataResult.success(r);
    }, r -> r.name());

    /** Cor ARGB do rank (igual ao HUD). */
    public final int argb;
    /** Pontos de guilda acumulados para poder fazer a PROVA deste rank. */
    public final int pointsRequired;
    /** Tier dos pergaminhos de glifo aleatórios das missões deste rank. */
    public final int questGlyphTier;
    /** Tier do pergaminho dado como prêmio ao ser promovido a este rank. */
    public final int promotionGlyphTier;

    GuildRank(int argb, int pointsRequired, int questGlyphTier, int promotionGlyphTier) {
        this.argb = argb;
        this.pointsRequired = pointsRequired;
        this.questGlyphTier = questGlyphTier;
        this.promotionGlyphTier = promotionGlyphTier;
    }

    /** 1 = F ... 7 = S. */
    public int level() {
        return ordinal() + 1;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public GuildRank next() {
        return this == S ? null : values()[ordinal() + 1];
    }

    /** Prefixo da chave da letra do rank ("guild.isekai_mahou.rank.f" = "F"). */
    public static final String LETTER_KEY = "guild.isekai_mahou.rank.";

    public MutableComponent letter() {
        return Component.translatable(LETTER_KEY + id());
    }

    /** "Rank C" */
    public MutableComponent displayName() {
        return Component.translatable("guild.isekai_mahou.rank_named", letter());
    }

    public int rgb() {
        return argb & 0xFFFFFF;
    }

    /** Rank pelo nível (1..7); 0 ou inválido = null (não registrado). */
    public static GuildRank byLevel(int level) {
        return level >= 1 && level <= values().length ? values()[level - 1] : null;
    }

    public static GuildRank byLetter(String s) {
        if (s == null) return null;
        for (GuildRank r : values()) if (r.name().equalsIgnoreCase(s.trim())) return r;
        return null;
    }
}
