package com.renanpontes.isekaimahou.feature.class_gunslinger;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.TriggerPath;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Técnicas de anime do Pistoleiro (v1.0 — homenagem Gun Gale Online e clássicos de pistoleiro): glifos de origem que o
 * Pistoleiro aprende sozinho pelo rank do Caminho do Gatilho (e, nas mais fortes, um feito extra). Conferido ao concluir
 * rank, ao entrar no mundo e quando um contador do Pistoleiro muda — quem já passou do rank recebe o que é dele.
 * As três mais fortes entregam um Pergaminho Lendário.
 */
public final class GsTechniques {
    /** Prefixos das chaves de tradução montadas pelo id (letreiros e feitos). */
    private static final String CALLOUT_KEY = "callout.isekai_mahou.gunslinger.";
    private static final String FEAT_KEY = "screen.isekai_mahou.gunslinger.tech.";
    /** Acertos da Espada de Fótons / Rajada Estelar. */
    public static final String PHOTON_HITS = "gunslinger.photon_hits";
    /** Ataques de emboscada saindo da Camuflagem Óptica. */
    public static final String CAMO_SHOTS = "gunslinger.camo_shots";

    /** Técnica: glifo, rank mínimo do Caminho, feito extra (contador/quantidade, opcional) e lenda entregue. */
    public record Technique(String glyph, int rank, @Nullable String counter, int amount, @Nullable String legend, int rgb) {
        public boolean meets(MagicData d) {
            if (d.path() != MagicPath.GUNSLINGER || TriggerPath.rankOf(d) < rank) return false;
            return counter == null || d.counter(counter) >= amount;
        }

        @Nullable
        public Glyph glyphObj() {
            return GlyphRegistry.get(IsekaiMahou.id(glyph));
        }

        /** Linhas do requisito (para a aba). */
        public List<Component> requirement(MagicData d) {
            List<Component> out = new ArrayList<>();
            out.add(Component.translatable("screen.isekai_mahou.gunslinger.tech.rank", rank));
            if (counter != null) {
                out.add(Component.translatable(FEAT_KEY + counter.substring(counter.indexOf('.') + 1),
                        amount).append(" (" + Math.min(amount, d.counter(counter)) + "/" + amount + ")"));
            }
            return out;
        }
    }

    public static final List<Technique> TECHNIQUES = List.of(
            new Technique("aim_circle", 1, null, 0, null, 0x7CFF6A),
            new Technique("spirit_gun", 2, null, 0, null, 0x6FD8FF),
            new Technique("bullet_line", 2, null, 0, null, 0xFF3A3A),
            new Technique("photon_sword", 3, null, 0, null, 0xB98CFF),
            new Technique("two_hands", 3, null, 0, null, 0xC08CFF),
            new Technique("anti_materiel", 4, null, 0, GsLegends.MOON_GODDESS, 0x9AF0FF),
            new Technique("optical_camo", 4, null, 0, null, 0x9FB8C8),
            new Technique("black_star", 4, CAMO_SHOTS, 10, GsLegends.BLACK_STAR, 0xFF1030),
            new Technique("stellar_burst", 5, PHOTON_HITS, 150, GsLegends.STARBURST, 0x8FD8FF));

    /** Técnicas liberadas no rank {@code n} (para o cartão do rank na aba). */
    public static List<Technique> ofRank(int n) {
        List<Technique> out = new ArrayList<>();
        for (Technique t : TECHNIQUES) if (t.rank() == n) out.add(t);
        return out;
    }

    /** Ensina o que o Pistoleiro já merece. */
    public static void check(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.GUNSLINGER) return;
        for (Technique t : TECHNIQUES) {
            Glyph g = t.glyphObj();
            if (g == null || d.knowsGlyph(g.id()) || !t.meets(d)) continue;
            MagicApi.learnGlyph(sp, g.id());
            Rewards.banner(sp, Component.translatable("banner.isekai_mahou.gunslinger.tech", g.displayName()), t.rgb());
            Rewards.toast(sp, Component.translatable("toast.isekai_mahou.gunslinger.tech"), g.displayName(),
                    new ItemStack(GsRegistries.MANA_REVOLVER.get()), t.rgb());
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gunslinger.tech", g.displayName()).withStyle(ChatFormatting.GOLD));
            sp.sendSystemMessage(g.description().withStyle(ChatFormatting.GRAY));
            GsFx.sound(sp.serverLevel(), sp.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.5F);
            GsFx.sound(sp.serverLevel(), sp.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.4F);
            sp.serverLevel().sendParticles(ParticleTypes.FIREWORK, sp.getX(), sp.getY() + 1.2, sp.getZ(), 24, 0.4, 0.6, 0.4, 0.06);
            if (t.legend() != null) {
                String flag = "gunslinger.legend." + t.legend();
                if (!d.hasFlag(flag)) {
                    d.setFlag(flag, true);
                    d.markDirty();
                    ItemStack scroll = Legends.scroll(t.legend());
                    if (!scroll.isEmpty()) Rewards.give(sp, scroll);
                }
            }
        }
    }

    // =====================================================================================
    //  Letreiros
    // =====================================================================================

    /** Título grande com o nome da técnica (só para quem conjurou). */
    public static void callout(@Nullable Entity e, Component title, @Nullable Component sub, int rgb) {
        if (!(e instanceof ServerPlayer sp)) return;
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(3, 24, 8));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(sub == null ? Component.empty() : sub.copy().withColor(0xE8E0D0)));
        sp.connection.send(new ClientboundSetTitleTextPacket(title.copy().withColor(rgb).withStyle(ChatFormatting.BOLD)));
    }

    /** Nome na barra de ação (técnicas rápidas). */
    public static void actionbar(@Nullable Entity e, Component text, int rgb) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(text.copy().withColor(rgb).withStyle(ChatFormatting.BOLD), true);
    }

    public static MutableComponent key(String id) {
        return Component.translatable(CALLOUT_KEY + id);
    }

    private GsTechniques() {}
}
