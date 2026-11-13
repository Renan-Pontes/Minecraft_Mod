package com.renanpontes.isekaimahou.client.spellfx;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Opções de CLIENTE dos efeitos das magias, em arquivo próprio ({@code config/isekai_mahou-spellfx-client.toml}) para
 * não disputar o {@code MahouConfig}. Aparecem sozinhas na tela de configuração do mod (menu do Minecraft → Mods).
 */
public final class SpellFxConfig {
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CHANNEL_AURA = CLIENT
            .comment("Aura discreta em volta de quem medita ou segura uma conjuração longa (falso = sem aura)",
                    "Subtle aura around whoever meditates or holds a long cast (false = no aura)")
            .define("channelAura", true);

    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    /** Lê a opção sem quebrar se a config ainda não carregou (tela de título, primeiros ticks). */
    public static boolean channelAura() {
        try {
            return CHANNEL_AURA.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private SpellFxConfig() {}
}
