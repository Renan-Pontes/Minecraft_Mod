package com.renanpontes.isekaimahou.feature.legends_k;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Como a PRÓPRIA classe aprende as técnicas do legends_k: sozinha, ao alcançar o Círculo indicado (o Círculo sobe com
 * o nível do jogador). Confere em toda subida de nível/Círculo, na escolha da classe e no login (quem já passou do
 * ponto ganha na hora). Outras classes só aprendem por Lição (os glifos são de origem — o núcleo cuida disso).
 */
public final class LkUnlocks {
    /** Técnica, classe e Círculo mínimo. */
    public record Unlock(ResourceLocation glyph, MagicPath path, int circle) {}

    public static final List<Unlock> ALL = List.of(
            new Unlock(LkGlyphs.KAMUI, MagicPath.ROGUE, 4),
            new Unlock(LkGlyphs.D4C, MagicPath.STEELBALL, 5),
            new Unlock(LkGlyphs.RETURN_TO_ZERO, MagicPath.STEELBALL, 6),
            new Unlock(LkGlyphs.SPIRITUAL_PRESSURE, MagicPath.SHINIGAMI, 3),
            new Unlock(LkGlyphs.TITAN_MODE, MagicPath.SMITH, 5),
            new Unlock(LkGlyphs.SANTEN_KESSHUN, MagicPath.ENCHANTER, 2),
            new Unlock(LkGlyphs.SOTEN_KISSHUN, MagicPath.ENCHANTER, 3),
            new Unlock(LkGlyphs.KOTEN_ZANSHUN, MagicPath.ENCHANTER, 4),
            new Unlock(LkGlyphs.PROJECTION, MagicPath.GUNSLINGER, 3),
            new Unlock(LkGlyphs.VECTOR_PUNCH, MagicPath.MAGE, 2),
            new Unlock(LkGlyphs.VECTOR_REFLECT, MagicPath.MAGE, 3),
            new Unlock(LkGlyphs.VECTOR_BURST, MagicPath.MAGE, 4),
            new Unlock(LkGlyphs.SHADOW_TOAD, MagicPath.NECROMANCER, 2),
            new Unlock(LkGlyphs.MAHORAGA, MagicPath.WARLOCK, 5),
            new Unlock(LkGlyphs.DEAD_CALM, MagicPath.KNIGHT, 4),
            new Unlock(LkGlyphs.RETURN_BY_DEATH, MagicPath.WITCH, 6),
            new Unlock(LkGlyphs.FLOWER_FIELD, MagicPath.DRUID, 3),
            new Unlock(LkGlyphs.SHADOW_POSSESSION, MagicPath.RANGER, 3),
            new Unlock(LkGlyphs.ASCHENTE, MagicPath.GAMBLER, 3));

    /** Ensina o que o jogador já merece. */
    public static void check(ServerPlayer sp) {
        if (sp instanceof FakePlayer || sp.isRemoved()) return;
        MagicData d = MagicApi.get(sp);
        MagicPath path = d.path();
        for (Unlock u : ALL) {
            if (u.path() != path || d.circle() < u.circle() || d.knowsGlyph(u.glyph())) continue;
            Glyph g = GlyphRegistry.get(u.glyph());
            if (g == null || !MagicApi.learnGlyph(sp, u.glyph())) continue;
            sp.sendSystemMessage(Component.translatable(LkUtil.MSG + "unlock", g.displayName().withStyle(ChatFormatting.GOLD),
                    path.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
            sp.sendSystemMessage(Component.literal("  ").append(g.description().withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Círculo em que a classe aprende o glifo (para textos); -1 se não é deste módulo. */
    public static int circleFor(ResourceLocation glyph) {
        for (Unlock u : ALL) if (u.glyph().equals(glyph)) return u.circle();
        return -1;
    }

    static void onLevel(MagicProgressEvent.LevelUp e) {
        check(e.getPlayer());
    }

    static void onCircle(MagicProgressEvent.CircleChanged e) {
        check(e.getPlayer());
    }

    static void onPath(MagicProgressEvent.PathChosen e) {
        check(e.getPlayer());
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) check(sp);
    }

    private LkUnlocks() {}
}
