package com.renanpontes.isekaimahou.feature.glyphs_classes_b;

import java.util.List;
import java.util.function.ToIntFunction;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.class_enchanter.Enchants;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseRanks;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.TriggerPath;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerProgress;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Como a PRÓPRIA classe aprende os glifos novos: pelo rank da classe (o mesmo que libera as técnicas dela).
 * <pre>
 *   classe       aumento (rank)            gatilho (rank)
 *   Encantador   Runa Gravada (II)         Ao Golpear com Arma Encantada (III)
 *   Shinigami    Shunpo: Conj. Relâmpago (II)  Ao Cortar com a Lâmina Liberada (III, junto da Shikai)
 *   Quincy       Sklaverei (IV)            Ao Absorver Reishi (II)
 *   Pistoleiro   Ricochete Calculado (III) Ao Acertar na Cabeça (II)
 *   Patrulheiro  Ataque Coordenado (III)   Ao Marcar um Alvo (II)
 *   Apostador    Roleta Russa (II)         Ao Tirar Sorte Grande (III)
 *   Steel Ball   Rotação Dourada (IV)      Ao Acertar a Bola de Aço (II)
 * </pre>
 * Os ranks vivem em cada módulo de classe (contadores {@code ritual_rank.<classe>} ou flags): a checagem roda 1 tick
 * depois de qualquer contador {@code ritual_rank*} mudar, ao entrar no mundo e, por segurança, a cada 30 s.
 * Outras classes só aprendem por Lição (os glifos são de origem da classe — o núcleo cuida disso).
 */
public final class ClassUnlocks {
    public record Unlock(MagicPath path, ToIntFunction<ServerPlayer> rank, ResourceLocation glyph, int minRank) {}

    private static int rankOf(ServerPlayer sp, ToIntFunction<MagicData> f) {
        return f.applyAsInt(MagicApi.get(sp));
    }

    public static final List<Unlock> UNLOCKS = List.of(
            new Unlock(MagicPath.ENCHANTER, Enchants::rank, GcbIds.ENGRAVED_RUNE, 2),
            new Unlock(MagicPath.ENCHANTER, Enchants::rank, GcbIds.ON_ENCHANTED_STRIKE, 3),
            new Unlock(MagicPath.SHINIGAMI, sp -> rankOf(sp, Shinigami::rank), GcbIds.SHUNPO_CAST, 2),
            new Unlock(MagicPath.SHINIGAMI, sp -> rankOf(sp, Shinigami::rank), GcbIds.ON_RELEASED_CUT, 3),
            new Unlock(MagicPath.QUINCY, sp -> rankOf(sp, Quincy::rank), GcbIds.ON_REISHI, 2),
            new Unlock(MagicPath.QUINCY, sp -> rankOf(sp, Quincy::rank), GcbIds.SKLAVEREI, 4),
            new Unlock(MagicPath.GUNSLINGER, sp -> rankOf(sp, TriggerPath::rankOf), GcbIds.ON_HEADSHOT, 2),
            new Unlock(MagicPath.GUNSLINGER, sp -> rankOf(sp, TriggerPath::rankOf), GcbIds.TRICK_RICOCHET, 3),
            new Unlock(MagicPath.RANGER, sp -> rankOf(sp, RangerProgress::rank), GcbIds.ON_MARK, 2),
            new Unlock(MagicPath.RANGER, sp -> rankOf(sp, RangerProgress::rank), GcbIds.COORDINATED, 3),
            new Unlock(MagicPath.GAMBLER, sp -> rankOf(sp, HouseRanks::rank), GcbIds.RUSSIAN_ROULETTE, 2),
            new Unlock(MagicPath.GAMBLER, sp -> rankOf(sp, HouseRanks::rank), GcbIds.ON_BIG_WIN, 3),
            new Unlock(MagicPath.STEELBALL, sp -> rankOf(sp, SbProgress::rank), GcbIds.ON_BALL_HIT, 2),
            new Unlock(MagicPath.STEELBALL, sp -> rankOf(sp, SbProgress::rank), GcbIds.GOLDEN_SPIN, 4));

    /** Ensina o que o rank atual da classe já libera (com o aviso de glifo aprendido). */
    public static void check(ServerPlayer sp) {
        if (sp instanceof FakePlayer || sp.isRemoved()) return;
        MagicData d = MagicApi.get(sp);
        MagicPath path = d.path();
        for (Unlock u : UNLOCKS) {
            if (u.path() != path || d.knowsGlyph(u.glyph())) continue;
            int r;
            try {
                r = u.rank().applyAsInt(sp);
            } catch (RuntimeException ex) {
                continue;
            }
            if (r < u.minRank()) continue;
            if (MagicApi.learnGlyph(sp, u.glyph())) {
                sp.sendSystemMessage(Component.translatable(GcbIds.MSG + "class_unlock",
                        com.renanpontes.isekaimahou.glyph.GlyphRegistry.get(u.glyph()).displayName(), path.displayName())
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    /** Qual rank da classe ensina este glifo (para textos); -1 se não é deste módulo. */
    public static int rankFor(ResourceLocation glyph) {
        for (Unlock u : UNLOCKS) if (u.glyph().equals(glyph)) return u.minRank();
        return -1;
    }

    public static void onCounter(MagicProgressEvent.Counter e) {
        if (!e.key.startsWith("ritual_rank")) return;
        ServerPlayer sp = e.getPlayer();
        // 1 tick depois: alguns ranks gravam a flag depois do contador
        SpellScheduler.schedule(1, () -> check(sp));
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) SpellScheduler.schedule(40, () -> check(sp));
    }

    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 600 == 137) check(sp);
    }

    private ClassUnlocks() {}
}
