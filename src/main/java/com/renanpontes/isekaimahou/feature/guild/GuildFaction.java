package com.renanpontes.isekaimahou.feature.guild;

import java.util.Locale;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Reputation;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

/**
 * Facções que têm Quadro de Missões ({@code faction_board[faction=...]}). Os ids batem com as facções do núcleo
 * ({@link Reputation}) e com os moldes das cidades (city_build): NÃO renomeie.
 * A Guilda tem rank e prova; as outras liberam missões por PATAMAR de reputação.
 */
public enum GuildFaction implements StringRepresentable {
    GUILD(0xE8B84A),
    KINGDOM(0x3F6FD8),
    ACADEMY(0xB98CFF),
    CHURCH(0xFFE9A8),
    UNDERWORLD(0x8A2B3A);

    /** Cor padrão (a do JSON da facção vale quando existe). */
    public final int fallbackRgb;

    GuildFaction(int rgb) {
        this.fallbackRgb = rgb;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    public boolean isGuild() {
        return this == GUILD;
    }

    /** Cor da facção (do JSON do núcleo, ou a padrão). */
    public int rgb() {
        Reputation.Faction f = Reputation.faction(id());
        return f != null ? f.color() & 0xFFFFFF : fallbackRgb;
    }

    public int argb() {
        return 0xFF000000 | rgb();
    }

    /** "Reino de Aldória" (tradução do núcleo). */
    public MutableComponent displayName() {
        return Component.translatable(NAME_KEY + id());
    }

    /** Prefixo das traduções de nome das facções (núcleo, lang core_reputation). */
    private static final String NAME_KEY = "faction.isekai_mahou.";

    @Nullable
    public static GuildFaction byId(@Nullable String id) {
        if (id == null) return null;
        for (GuildFaction f : values()) if (f.id().equalsIgnoreCase(id.trim())) return f;
        return null;
    }

    /** Facção de um quadro/missão (desconhecida = Guilda). */
    public static GuildFaction of(@Nullable String id) {
        GuildFaction f = byId(id);
        return f == null ? GUILD : f;
    }
}
