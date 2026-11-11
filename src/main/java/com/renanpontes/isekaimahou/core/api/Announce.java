package com.renanpontes.isekaimahou.core.api;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Anúncios "falados": o nome de magias fortes, falas de chefes e o cântico durante a conjuração
 * aparecem na tela de quem estiver perto. O servidor só ENVIA; o módulo "cinematic" desenha no cliente.
 */
public final class Announce {
    public static final int STYLE_SPELL = 0;
    public static final int STYLE_ULTIMATE = 1;
    public static final int STYLE_BOSS = 2;
    public static final int STYLE_SYSTEM = 3;

    /** Anuncia o nome de uma magia (se a magia não tiver nome, usa "Magia de <Elemento>"). */
    public static void spell(LivingEntity caster, SpellRecipe recipe, Element element, boolean ultimate) {
        Component text = recipe.name().isEmpty()
                ? Component.translatable("announce.isekai_mahou.unnamed", element.displayName())
                : Component.literal(recipe.name());
        int color = recipe.color() >= 0 ? recipe.color() : element.color;
        send(caster, text, color, ultimate ? STYLE_ULTIMATE : STYLE_SPELL);
    }

    /** Envia um anúncio a todos os jogadores perto da fonte (raio da config). */
    public static void send(LivingEntity source, Component text, int rgb, int style) {
        if (!(source.level() instanceof ServerLevel level)) return;
        double r = MahouConfig.ANNOUNCE_RADIUS.get();
        PacketDistributor.sendToPlayersNear(level, null, source.getX(), source.getY(), source.getZ(), r,
                new CorePayloads.AnnounceText(source.getId(), text, rgb, style));
    }

    /** Cântico: mostrado palavra por palavra sobre a cabeça de quem conjura, durante {@code ticks}. */
    public static void chant(LivingEntity caster, String chant, int ticks) {
        if (!(caster.level() instanceof ServerLevel level) || chant.isEmpty()) return;
        double r = MahouConfig.ANNOUNCE_RADIUS.get();
        PacketDistributor.sendToPlayersNear(level, null, caster.getX(), caster.getY(), caster.getZ(), r,
                new CorePayloads.Chant(caster.getId(), chant, ticks));
    }

    private Announce() {}
}
