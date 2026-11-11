package com.renanpontes.isekaimahou.feature.ui_hud.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * Visual do recurso da classe (v0.9): o MESMO valor salvo de mana aparece como Mana (Mágicas, violeta), Vigor
 * (Guerreiras, vermelho-brasa) ou Foco (À Distância, verde-água). Paleta da barra vertical, da Janela de Status e dos
 * banners, nome ({@link MagicApi#resourceName}) e ícone {@code textures/gui/resource/<id>.png} (com reserva).
 *
 * @param label     cor do nome do recurso (a do núcleo, {@link MagicApi#resourceColor})
 * @param frame     moldura da barra
 * @param fillTop   topo do preenchimento (gradiente)
 * @param fillBottom base do preenchimento
 * @param back      fundo da barra vazia
 * @param ghost     "fantasma" do gasto recente
 * @param light     texto claro (números, linhas de banner)
 * @param muted     texto do máximo
 */
record ResourceStyle(MagicPath.Resource resource, int label, int frame, int fillTop, int fillBottom, int back, int ghost,
                     int light, int muted) {

    static final ResourceStyle MANA = new ResourceStyle(MagicPath.Resource.MANA, MagicPath.Resource.MANA.argb(),
            0xFF7A66C8, 0xFFCBAEFF, 0xFF4B22C8, 0xFF150F2A, 0xC8FFD8EE, 0xFFE2D4FF, 0xFFB4A8D8);
    static final ResourceStyle VIGOR = new ResourceStyle(MagicPath.Resource.VIGOR, MagicPath.Resource.VIGOR.argb(),
            0xFFB4553C, 0xFFFFB79C, 0xFFB02A18, 0xFF2A100B, 0xC8FFE8C8, 0xFFFFD6C8, 0xFFE0AFA2);
    static final ResourceStyle FOCUS = new ResourceStyle(MagicPath.Resource.FOCUS, MagicPath.Resource.FOCUS.argb(),
            0xFF3A9E88, 0xFFA8F5E2, 0xFF138570, 0xFF0A221E, 0xC8E2FFF6, 0xFFC8F6EC, 0xFFA2D8CC);

    static ResourceStyle of(MagicPath.Resource r) {
        if (r == null) return MANA;
        return switch (r) {
            case VIGOR -> VIGOR;
            case FOCUS -> FOCUS;
            default -> MANA;
        };
    }

    /** Estilo do recurso do jogador (cliente: classe sincronizada). */
    static ResourceStyle of(Player player) {
        return of(MagicApi.resource(player));
    }

    /** "Mana" / "Vigor" / "Foco" já na cor do recurso. */
    static MutableComponent name(Player player) {
        return MagicApi.resourceName(player);
    }

    /** Nome do recurso SEM cor (para usar dentro de outras frases). */
    MutableComponent plainName() {
        return switch (resource) {
            case VIGOR -> Component.translatable("resource.isekai_mahou.vigor");
            case FOCUS -> Component.translatable("resource.isekai_mahou.focus");
            default -> Component.translatable("resource.isekai_mahou.mana");
        };
    }

    /** "+N Mana máxima · teto X" / Vigor / Foco (banner de nível/Círculo; teto = até onde o treino leva o Limite). */
    MutableComponent maxGain(int amount, int ceiling) {
        return switch (resource) {
            case VIGOR -> Component.translatable("hud.isekai_mahou.ui_hud.banner.vigor", amount, ceiling);
            case FOCUS -> Component.translatable("hud.isekai_mahou.ui_hud.banner.focus", amount, ceiling);
            default -> Component.translatable("hud.isekai_mahou.ui_hud.banner.mana", amount, ceiling);
        };
    }

    /** Cor do texto do nome (sempre opaca, para desenhar com fade). */
    int labelColor() {
        return 0xFF000000 | (label & 0xFFFFFF);
    }

    /** Cor do texto do nome um pouco mais clara (legível sobre o fundo escuro do HUD). */
    int labelLight() {
        return HudDraw.brighten(labelColor(), 0.3F);
    }

    /** Ícone do recurso em size×size; sem textura (pacote incompleto), o cristal do HUD tingido na cor do recurso. */
    void icon(GuiGraphics g, int x, int y, int size, float alpha) {
        if (GlyphArt.exists(resource.iconTexture())) {
            g.setColor(1F, 1F, 1F, alpha);
            GlyphArt.icon(g, resource.iconTexture(), x, y, size);
            g.setColor(1F, 1F, 1F, 1F);
            // GlyphArt.icon desliga a mistura no fim; o resto do HUD continua translúcido
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            int[] s = HudDraw.CRYSTAL;
            HudDraw.sprite(g, s, x + (size - s[2]) / 2, y + (size - s[3]) / 2, HudDraw.fade(labelLight(), alpha));
        }
    }
}
