package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.character.RaceProgress;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;
import com.renanpontes.isekaimahou.glyph.TransformTargets;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Como se APRENDE uma Lei de Transformação no survival:
 * <ol>
 *   <li>como as Leis de Domínio (são do tipo LAW): o nó Domínio dos Caminhos ensina as leis do Círculo atual e as
 *       Provações de rank VI+ podem dar leis de recompensa (módulos paths/trials, sem mudança);</li>
 *   <li>aqui: quem já sabe TRANSFORMAR — conhece algum glifo de transformação ({@link TransformTargets}: formas de
 *       Druida, Kaioken, Forma de Lich, Transcendência...) ou liberou a Transformação Racial (raça nível V) — aprende as
 *       leis cujo Círculo mínimo (e nível mínimo: Transcender = 100) já alcançou. Checado ao entrar, ao subir de nível ou
 *       Círculo, ao aprender um glifo de transformação e a cada 30 s.</li>
 * </ol>
 */
final class TlawUnlocks {
    static void init() {
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.LevelUp e) -> check(e.getPlayer(), true));
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.CircleChanged e) -> check(e.getPlayer(), true));
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.GlyphLearned e) -> {
            if (TransformTargets.isEffect(e.glyph) || TransformTargets.isForm(e.glyph)) check(e.getPlayer(), true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) check(sp, true);
        });
    }

    /** Sabe transformar (glifo de transformação conhecido ou forma racial liberada)? */
    static boolean canTransform(ServerPlayer sp, MagicData d) {
        if (d.path() == com.renanpontes.isekaimahou.core.MagicPath.HOLLOW) return true; // Resurrección
        for (ResourceLocation id : d.glyphs()) {
            if (TransformTargets.isEffect(id) || TransformTargets.isForm(id)) return true;
        }
        try {
            return RaceProgress.level(sp) >= RaceProgress.MAX_LEVEL;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    static void check(ServerPlayer sp, boolean announce) {
        if (sp instanceof FakePlayer) return;
        MagicData d = MagicApi.get(sp);
        if (!canTransform(sp, d)) return;
        List<TransformLawGlyph> learned = new ArrayList<>();
        for (TransformLawGlyph l : TlawGlyphs.all()) {
            if (d.knowsGlyph(l.id()) || !l.usableAt(d.level(), d.circle())) continue;
            MagicApi.learnGlyphSilently(sp, l.id());
            learned.add(l);
        }
        if (learned.isEmpty() || !announce) return;
        sp.sendSystemMessage(Component.literal("⛓ ").withColor(TlawFx.CHAIN_COLOR)
                .append(Component.translatable("message.isekai_mahou.tlaw.learned", TlawFx.names(learned)).withColor(0xE8D8F0)));
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.tlaw.learned_hint").withColor(0xA89EC0));
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.tlaw.learned"),
                Component.translatable("toast.isekai_mahou.tlaw.learned_sub", learned.size()), new ItemStack(Items.CHAIN), TlawFx.CHAIN_COLOR);
        TlawFx.sound(sp, SoundEvents.CHAIN_PLACE, 0.9F, 0.8F);
    }

    private TlawUnlocks() {}
}
