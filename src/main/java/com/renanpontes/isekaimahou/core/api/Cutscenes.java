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
        ultimate(caster, recipe, element, null);
    }

    /**
     * v1.1 — Cutscene de ultimate (G) com coreografia por elemento (e por forma, ex.: "domain_expansion", "meteor_shower").
     * Tipo de rede: {@code "ultimate:<elemento>[:<forma>]"} (ver {@link #ultimateKind}). O "ultimate" puro (sem ':')
     * continua valendo para chefes (órbita antiga).
     */
    public static void ultimate(ServerPlayer caster, SpellRecipe recipe, Element element, String formId) {
        Component title = recipe.name().isEmpty()
                ? Component.translatable("announce.isekai_mahou.unnamed", element.displayName())
                : Component.literal(recipe.name());
        int rgb = recipe.color() >= 0 ? recipe.color() : element.color;
        playNear(caster, 32, ultimateKind(element, formId), title, element.displayName(), rgb, 50, caster);
    }

    /** {@code "ultimate:<elemento>"} ou {@code "ultimate:<elemento>:<forma>"} (forma = caminho do id do glifo de forma). */
    public static String ultimateKind(Element element, String formId) {
        String kind = "ultimate:" + element.id;
        return formId == null || formId.isEmpty() ? kind : kind + ":" + formId;
    }

    /** Tipo de rede das cutscenes de transformação: {@code "transform:<estilo>"} (o cliente lê o estilo depois do ':'). */
    public static final String TRANSFORM = "transform";

    /**
     * v1.1 — Cutscene de TRANSFORMAÇÃO: quem se transforma (dono, controles travados e pulável) + quem estiver a 32 blocos.
     * {@code style} escolhe a coreografia no cliente: "default", "holy", "dark", "time", "speed", "beast", "spirit",
     * "fire", "ice", "storm", "blood" (desconhecido = "default"). {@code ticks} recomendado: 50–70.
     */
    public static void transform(ServerPlayer who, Component title, Component subtitle, int rgb, String style, int ticks) {
        playNear(who, 32, TRANSFORM + ":" + (style == null || style.isEmpty() ? "default" : style), title, subtitle, rgb, ticks, who);
    }

    /** Igual a {@link #transform(ServerPlayer, Component, Component, int, String, int)} para mobs/chefes (ninguém trava). */
    public static void transform(Entity who, Component title, Component subtitle, int rgb, String style, int ticks) {
        if (who instanceof ServerPlayer sp) {
            transform(sp, title, subtitle, rgb, style, ticks);
            return;
        }
        playNear(who, 32, TRANSFORM + ":" + (style == null || style.isEmpty() ? "default" : style), title, subtitle, rgb, ticks, null);
    }

    private Cutscenes() {}
}
