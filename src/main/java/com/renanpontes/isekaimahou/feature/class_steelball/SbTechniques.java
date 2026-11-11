package com.renanpontes.isekaimahou.feature.class_steelball;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Técnicas do Santo (v1.0 — pacote de técnicas de anime): os ATOS da unha (homenagem Tusk) como evolução pelas
 * Partes do Cadáver Santo, a Bola Escâner, a Lição do Cavalo (Corrida Dourada), a Rotação Restauradora e a Esfera
 * Quebrantadora (homenagem ao Ball Breaker). Cada técnica é um glifo de origem; o Steel Ball aprende sozinho quando
 * cumpre a lição (rank) e as Partes pedidas — conferido ao subir de lição, ao absorver uma Parte e ao entrar no mundo
 * (então quem já tinha passado da lição recebe o que é dele). Algumas entregam um Pergaminho Lendário.
 * <p>
 * Também guarda o "escudo do buraco" (Ato 3: dentro do buraco de minhoca nada acerta você).
 */
public final class SbTechniques {
    /** Uma técnica: glifo, lição mínima, Partes mínimas, condição extra (pode ser null) e lenda entregue (ou null). */
    public record Technique(String glyph, int rank, int parts, @Nullable SbPart altPart, @Nullable String legend, int rgb) {
        /** Cumpre? A Parte alternativa ({@code altPart}) vale no lugar do número de Partes. */
        public boolean meets(MagicData d) {
            if (d.path() != MagicPath.STEELBALL || SbProgress.rank(d) < rank) return false;
            return SbPart.count(d) >= parts || (altPart != null && altPart.owned(d));
        }

        public MutableComponent requirement() {
            MutableComponent c = Component.translatable("gui.isekai_mahou.steelball.tech.req", SbProgress.roman(rank), parts);
            if (altPart != null) c.append(Component.translatable("gui.isekai_mahou.steelball.tech.req_alt", altPart.displayName()));
            return c;
        }

        @Nullable
        public Glyph glyphObj() {
            return GlyphRegistry.get(IsekaiMahou.id(glyph));
        }
    }

    public static final List<Technique> TECHNIQUES = List.of(
            new Technique("scan_ball", 1, 0, null, null, 0x7FE8FF),
            new Technique("tusk_act1", 1, 2, SbPart.LEFT_ARM, null, SbFx.NAIL),
            new Technique("golden_gallop", 2, 0, null, null, SbFx.GOLD),
            new Technique("tusk_act2", 2, 3, null, SbLegends.GOLDEN_CHASE, SbFx.GOLD),
            new Technique("spin_mend", 3, 4, null, null, SbFx.HEAL),
            new Technique("tusk_act3", 3, 5, null, SbLegends.WORMHOLE, 0xB45CFF),
            new Technique("ball_breaker", 5, 7, null, SbLegends.BALL_BREAKER, 0xFFB43A));

    /** Escudo do buraco de minhoca: jogador → tick do mundo até quando nada o acerta. */
    private static final Map<UUID, Long> SHIELD = new HashMap<>();

    /** Confere e ensina o que o Steel Ball já merece (silencioso se não houver nada novo). */
    public static void check(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.STEELBALL) return;
        for (Technique t : TECHNIQUES) {
            Glyph g = t.glyphObj();
            if (g == null || d.knowsGlyph(g.id()) || !t.meets(d)) continue;
            MagicApi.learnGlyph(sp, g.id());
            announce(sp, t, g);
            if (t.legend() != null) {
                String flag = "steelball.legend." + t.legend();
                if (!d.hasFlag(flag)) {
                    d.setFlag(flag, true);
                    d.markDirty();
                    ItemStack scroll = Legends.scroll(t.legend());
                    if (!scroll.isEmpty()) Rewards.give(sp, scroll);
                }
            }
        }
    }

    private static void announce(ServerPlayer sp, Technique t, Glyph g) {
        Rewards.banner(sp, Component.translatable("banner.isekai_mahou.steelball.tech", g.displayName()), t.rgb());
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.steelball.tech"), g.displayName(),
                new ItemStack(SbRegistry.STEEL_BALL.get()), t.rgb());
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.tech", g.displayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        sp.sendSystemMessage(g.description().withStyle(ChatFormatting.GRAY));
        ServerLevel sl = sp.serverLevel();
        Vec3 p = sp.position();
        SbFx.sound(sl, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.4F);
        SbFx.sound(sl, p, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.5F);
        SbFx.spiral(sl, p, t.rgb(), 1.0, 2.4, 30, 0F);
        SbFx.goldenSpiral(sl, p.add(0, 1.2, 0), sp.getLookAngle(), 1.4);
        sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1, p.z, 16, 0.4, 0.7, 0.4, 0.04);
    }

    // =====================================================================================
    //  Letreiro (título curto com o nome da técnica)
    // =====================================================================================

    /** Título grande no centro da tela do conjurador ("TUSK — ATO 3"). */
    public static void callout(@Nullable Player p, Component title, @Nullable Component sub, int rgb) {
        if (!(p instanceof ServerPlayer sp)) return;
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(3, 22, 8));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(sub == null ? Component.empty() : sub.copy().withColor(0xE8E0C8)));
        sp.connection.send(new ClientboundSetTitleTextPacket(title.copy().withColor(rgb).withStyle(ChatFormatting.BOLD)));
    }

    /** Barra de ação com o nome da técnica (técnicas rápidas, para não poluir a tela). */
    public static void actionbar(@Nullable Player p, Component text, int rgb) {
        if (p instanceof ServerPlayer sp) sp.displayClientMessage(text.copy().withColor(rgb).withStyle(ChatFormatting.BOLD), true);
    }

    // =====================================================================================
    //  Escudo do buraco (Ato 3)
    // =====================================================================================

    public static void shield(ServerPlayer sp, int ticks) {
        SHIELD.put(sp.getUUID(), sp.serverLevel().getGameTime() + ticks);
    }

    public static boolean shielded(ServerPlayer sp) {
        Long until = SHIELD.get(sp.getUUID());
        if (until == null) return false;
        if (sp.serverLevel().getGameTime() > until) {
            SHIELD.remove(sp.getUUID());
            return false;
        }
        return true;
    }

    static void onIncoming(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !shielded(sp)) return;
        if (e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        e.setCanceled(true);
        Vec3 c = sp.position().add(0, 1, 0);
        sp.serverLevel().sendParticles(SbFx.dust(0xB45CFF, 1.0F), c.x, c.y, c.z, 6, 0.3, 0.5, 0.3, 0);
    }

    public static void forget(UUID id) {
        SHIELD.remove(id);
    }

    public static void clear() {
        SHIELD.clear();
    }

    /** Técnicas que um Steel Ball já cumpre (para a aba). */
    public static Predicate<Technique> known(MagicData d) {
        return t -> {
            Glyph g = t.glyphObj();
            return g != null && d.knowsGlyph(g.id());
        };
    }

    private SbTechniques() {}
}
