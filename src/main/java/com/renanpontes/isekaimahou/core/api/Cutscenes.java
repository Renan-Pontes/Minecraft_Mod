package com.renanpontes.isekaimahou.core.api;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Cutscenes curtas (faixas de cinema, câmera girando em volta do foco, título).
 * O servidor só ENVIA; o módulo "cinematic" implementa a câmera no cliente.
 *
 * Tipos sugeridos: "ultimate", "domain", "boss_intro", "boss_phase", "patron", "ascension", "portal".
 * {@code lockControls}: só para quem é o "dono" da cena (ex.: quem conjurou). Espectadores nunca perdem o controle.
 */
public final class Cutscenes {

    public static void play(ServerPlayer viewer, String type, Entity focus, Component title, Component subtitle,
                            int rgb, int ticks, boolean lockControls) {
        PacketDistributor.sendToPlayer(viewer, new CorePayloads.Cutscene(type, focus.getId(), title, subtitle, rgb, ticks, lockControls));
    }

    /** Toca para todos perto do foco (sem travar controles), e para {@code owner} travando (se não for null). */
    public static void playNear(Entity focus, double radius, String type, Component title, Component subtitle,
                                int rgb, int ticks, ServerPlayer owner) {
        if (!(focus.level() instanceof ServerLevel level)) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(focus) > radius * radius) continue;
            play(p, type, focus, title, subtitle, rgb, ticks, p == owner);
        }
    }

    /** Cutscene padrão de ultimate (Cataclismo / Domínio): quem conjurou + quem estiver a 32 blocos. */
    public static void ultimate(ServerPlayer caster, SpellRecipe recipe, Element element) {
        Component title = recipe.name().isEmpty()
                ? Component.translatable("announce.isekai_mahou.unnamed", element.displayName())
                : Component.literal(recipe.name());
        int rgb = recipe.color() >= 0 ? recipe.color() : element.color;
        playNear(caster, 32, "ultimate", title, element.displayName(), rgb, 50, caster);
    }

    private Cutscenes() {}
}
