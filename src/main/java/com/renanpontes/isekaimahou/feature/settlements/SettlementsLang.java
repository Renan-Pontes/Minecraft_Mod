package com.renanpontes.isekaimahou.feature.settlements;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Textos do módulo com chave montada em tempo de execução ({@code settlements.isekai_mahou.<sufixo>}): estilos, decretos,
 * papéis, falas sorteadas... Todas as chaves estão em src/main/lang/settlements (geradas por tools/gen/settlements_lang.py).
 */
public final class SettlementsLang {
    public static final String PREFIX = "settlements.isekai_mahou.";

    public static MutableComponent tr(String suffix, Object... args) {
        return Component.translatable(PREFIX + suffix, args);
    }

    private SettlementsLang() {}
}
