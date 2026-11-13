package com.renanpontes.isekaimahou.feature.glyphs_classes_a;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
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
 * Como a CLASSE aprende os próprios glifos deste módulo (os outros só por Lição — regra do núcleo para glifos de
 * origem). Pela progressão da classe ({@link GcaGlyphs.ClassPair#rankCounter()}): o aumento por volta de 40% dos ranks e
 * o gatilho por volta de 60%. Rede de segurança: quem chegar ao Círculo {@link #AUGMENT_CIRCLE}/{@link #TRIGGER_CIRCLE}
 * sem o rank (ritual travado, save antigo) também aprende. As Mágicas ainda recebem pelos próprios rituais, que ensinam
 * os glifos de origem por tier (aumentos tier 3, gatilhos tier 4).
 * <p>
 * Confere ao subir um contador, ao mudar de Círculo, ao escolher a classe e ao entrar no mundo (saves antigos).
 */
public final class GcaUnlocks {
    public static final int AUGMENT_CIRCLE = 6;
    public static final int TRIGGER_CIRCLE = 7;

    public static void onCounter(MagicProgressEvent.Counter event) {
        String k = event.key;
        if (!k.startsWith("ritual_rank") && !k.equals("warlock.pacts") && !k.equals("smith.rank") && !k.equals("knight.oath")) return;
        check(event.getPlayer());
    }

    public static void onCircle(MagicProgressEvent.CircleChanged event) {
        check(event.getPlayer());
    }

    public static void onPathChosen(MagicProgressEvent.PathChosen event) {
        check(event.getPlayer());
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) check(sp);
    }

    /** Ensina o que a classe do jogador já mereceu. */
    public static void check(ServerPlayer p) {
        if (p == null || p instanceof FakePlayer) return;
        MagicData d = MagicApi.get(p);
        for (GcaGlyphs.ClassPair pair : GcaGlyphs.PAIRS) {
            if (pair.path() != d.path()) continue;
            int rank = d.counter(pair.rankCounter());
            if (rank >= pair.augmentRank() || d.circle() >= AUGMENT_CIRCLE) teach(p, d, pair.augment());
            if (rank >= pair.triggerRank() || d.circle() >= TRIGGER_CIRCLE) teach(p, d, pair.trigger());
            return;
        }
    }

    private static void teach(ServerPlayer p, MagicData d, ResourceLocation id) {
        if (d.knowsGlyph(id)) return;
        Glyph g = GlyphRegistry.get(id);
        if (g == null || !MagicApi.learnGlyph(p, id)) return;
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.glyphs_classes_a.learned", g.displayName(),
                d.path().displayName()).withStyle(ChatFormatting.AQUA));
    }

    private GcaUnlocks() {}
}
