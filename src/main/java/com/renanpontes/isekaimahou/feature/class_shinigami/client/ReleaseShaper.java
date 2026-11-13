package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.class_shinigami.ReleaseForge;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * SÓ CLIENTE. O botão "Moldar liberação" da aba Shinigami: abre a aba Votos (módulo {@code transform_laws}) já no
 * espaço de receita da liberação, e monta a dica do botão (o que é, a receita atual ou o que falta).
 */
final class ReleaseShaper {
    /** Cor do botão (violeta da forja de transformações, para não se confundir com os botões da classe). */
    static final int COLOR = 0xFF7A5CC8;
    private static final String KEY = "screen.isekai_mahou.release_forge.";
    private static final String FORGE_CLASS = "com.renanpontes.isekaimahou.feature.transform_laws.TransformForge";
    private static final String VOWS_TAB = "transform_laws";
    private static boolean looked;
    private static Method opener;

    /** Abre a aba Votos no espaço pedido; se o módulo de leis não tiver o atalho, abre a aba como está. */
    static void open(String slot) {
        if (!looked) {
            looked = true;
            try {
                opener = Class.forName(FORGE_CLASS).getMethod("openShaper", String.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // sem o atalho: cai na abertura simples da aba
            }
        }
        if (opener != null) {
            try {
                opener.invoke(null, slot);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // cai na abertura simples
            }
        }
        StatusScreen.open(VOWS_TAB);
    }

    /** Dica do botão: o nome da liberação, o que a receita faz e a receita atual (ou o que falta para moldar). */
    static List<Component> tip(MagicData d, String slot) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(KEY + "title", Component.translatable("screen.isekai_mahou.tforge.slot." + slot))
                .withStyle(ChatFormatting.BOLD).withColor(COLOR & 0xFFFFFF));
        out.add(Component.translatable(KEY + "tip").withStyle(ChatFormatting.GRAY));
        if (!ReleaseForge.canShape(d)) {
            out.add(Component.translatable(KEY + "need_form", ReleaseForge.formCircle()).withColor(0xE07070));
            return out;
        }
        SpellRecipe r = ReleaseForge.recipe(Minecraft.getInstance().player, slot);
        if (r == null) {
            out.add(Component.translatable(KEY + "no_recipe").withColor(0xB8AEA6));
        } else {
            Component name = r.name().isEmpty() ? Component.translatable("screen.isekai_mahou.tforge.unnamed") : Component.literal(r.name());
            out.add(Component.translatable(KEY + "recipe", name).withColor(0x7CE08A));
        }
        out.add(Component.translatable(KEY + "click").withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    private ReleaseShaper() {}
}
