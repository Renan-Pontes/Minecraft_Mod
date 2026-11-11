package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Progressão própria do Quincy: os 5 ranks do Auswählen. Cada rank pede Círculo e feitos (abates com técnicas Quincy,
 * absorções, Auswählen em outros Quincy, chefes, usos da Letra) e dá glifos de origem, itens e XP — dificuldade
 * parecida com as progressões das outras classes (I–V nos Círculos 1/2/4/6/8, XP 600→14000). Ao subir: +1 em {@link Quincy#RANK} e na
 * convenção global {@code ritual_rank}. No rank III o Quincy escolhe a sua Letra (Schrift).
 */
public final class QuincyRanks {
    /** Um requisito: contador do MagicData (ou derivado) até {@code amount}. */
    public record Req(String key, int amount, ToIntFunction<MagicData> current) {
        public MutableComponent label() {
            return Component.translatable(Quincy.REQ_KEY + key, amount);
        }

        public boolean ok(MagicData d) {
            return current.applyAsInt(d) >= amount;
        }
    }

    /** Um rank: Círculo mínimo, XP, requisitos, glifos ensinados, itens e uma lenda (pergaminho) opcional. */
    public record Rank(int number, int circle, int xp, List<Req> reqs, List<String> glyphs, List<ItemStack> items,
                       @Nullable String legend) {}

    private static Req counter(String key, String counter, int amount) {
        return new Req(key, amount, d -> d.counter(counter));
    }

    private static Req kills(int n) {
        return counter("kills", Quincy.KILLS, n);
    }

    private static Req absorbed(int n) {
        return counter("absorbed", Quincy.ABSORBED, n);
    }

    private static Req auswahlen(int n) {
        return counter("auswahlen", Quincy.AUSWAHLEN, n);
    }

    private static Req bosses(int n) {
        return new Req("bosses", n, Quincy::bossKills);
    }

    private static List<Rank> ranks;

    /** Os 5 ranks (montados na primeira consulta: os itens precisam do registro pronto). */
    public static synchronized List<Rank> ranks() {
        if (ranks == null) {
            ranks = List.of(
                    new Rank(1, 1, 600, List.of(kills(20), absorbed(15)),
                            List.of(Quincy.ABSORB, Quincy.RELEASE),
                            List.of(new ItemStack(QuincyRegistries.SILVER_TUBE.get(), 8)), null),
                    new Rank(2, 2, 1500, List.of(kills(80), absorbed(60), auswahlen(1)),
                            List.of(Quincy.SEELE_BLADE, Quincy.BLUT, Quincy.SILVER_TUBE),
                            List.of(new ItemStack(QuincyRegistries.SEELE_SCHNEIDER.get()), new ItemStack(QuincyRegistries.SILVER_TUBE.get(), 8)), null),
                    new Rank(3, 4, 3500, List.of(kills(200), auswahlen(2), bosses(1)),
                            List.of(Quincy.LIGHT_RAIN, Quincy.SCHRIFT),
                            List.of(new ItemStack(QuincyRegistries.SILVER_TUBE.get(), 12)), null),
                    new Rank(4, 6, 7000, List.of(absorbed(200), auswahlen(4), counter("schrift", Quincy.SCHRIFT_USES, 40), bosses(2)),
                            List.of(),
                            List.of(new ItemStack(QuincyRegistries.SILVER_TUBE.get(), 16), new ItemStack(QuincyRegistries.SANREI_GLOVE.get())),
                            QuincyLegends.STORM),
                    new Rank(5, 8, 14000, List.of(kills(600), auswahlen(6), bosses(4)),
                            List.of(Quincy.VOLLSTANDIG),
                            List.of(new ItemStack(QuincyRegistries.SILVER_TUBE.get(), 16)), QuincyLegends.JUDGEMENT));
        }
        return ranks;
    }

    @Nullable
    public static Rank rank(int number) {
        return number >= 1 && number <= Quincy.MAX_RANK ? ranks().get(number - 1) : null;
    }

    /** Requisitos do próximo rank (vazio = pronto). Escreve o que falta em {@code missing}. */
    public static boolean meets(MagicData d, Rank r, @Nullable List<Component> missing) {
        boolean ok = true;
        if (d.circle() < r.circle()) {
            ok = false;
            if (missing != null) missing.add(Component.translatable("quincy.isekai_mahou.req.circle", r.circle()));
        }
        for (Req q : r.reqs()) {
            if (!q.ok(d)) {
                ok = false;
                if (missing != null) missing.add(q.label().append(" (" + Math.min(q.current().applyAsInt(d), q.amount()) + "/" + q.amount() + ")"));
            }
        }
        if (r.number() == 4 && Quincy.letter(d) == null) {
            ok = false;
            if (missing != null) missing.add(Component.translatable("quincy.isekai_mahou.req.letter"));
        }
        return ok;
    }

    /** Pedido de subir de rank (aba do menu / comando). */
    public static void tryRankUp(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (!Quincy.isQuincy(sp)) {
            sp.displayClientMessage(Quincy.msg("not_quincy").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Rank next = rank(Quincy.rank(d) + 1);
        if (next == null) {
            sp.displayClientMessage(Quincy.msg("max_rank").withColor(Quincy.RGB), true);
            return;
        }
        List<Component> missing = new ArrayList<>();
        if (!meets(d, next, missing)) {
            sp.sendSystemMessage(Quincy.msg("rank_missing", Quincy.roman(next.number())).withStyle(ChatFormatting.RED));
            for (Component c : missing) sp.sendSystemMessage(Component.literal(" ✘ ").withStyle(ChatFormatting.RED).append(c.copy().withStyle(ChatFormatting.GRAY)));
            sp.playNotifySound(SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.0F);
            return;
        }
        grant(sp, next);
    }

    /** Sobe para o rank {@code r} (sem checar requisitos — comandos usam direto). */
    public static void grant(ServerPlayer sp, Rank r) {
        MagicData d = MagicApi.get(sp);
        int delta = r.number() - Quincy.rank(d);
        if (delta <= 0) return;
        MagicApi.addCounter(sp, Quincy.RANK, delta);
        MagicApi.addCounter(sp, Quincy.GLOBAL_RANK, delta);
        MagicApi.giveXp(sp, r.xp(), "ritual");
        for (String g : r.glyphs()) MagicApi.learnGlyph(sp, IsekaiMahou.id(g));
        for (ItemStack it : r.items()) Rewards.give(sp, it.copy());
        if (r.legend() != null) {
            ItemStack scroll = Legends.scroll(r.legend());
            if (!scroll.isEmpty()) Rewards.give(sp, scroll);
        }
        ceremony(sp, r);
        if (r.number() == 3 && Quincy.letter(d) == null) {
            sp.sendSystemMessage(Quincy.msg("choose_letter").withStyle(ChatFormatting.GOLD));
        }
    }

    private static void ceremony(ServerPlayer sp, Rank r) {
        ServerLevel sl = sp.serverLevel();
        Vec3 at = sp.position();
        MutableComponent title = Quincy.msg("rank_up_title", Quincy.roman(r.number()));
        MutableComponent sub = Quincy.rankTitle(r.number());
        for (ServerPlayer viewer : sl.players()) {
            if (viewer.distanceToSqr(sp) <= 32 * 32) Cutscenes.play(viewer, "ascension", sp, title, sub, Quincy.RGB, 70, false);
        }
        Announce.send(sp, Quincy.msg("rank_up_announce", sp.getDisplayName(), sub), Quincy.RGB, Announce.STYLE_SYSTEM);
        Rewards.toast(sp, title, sub, new ItemStack(QuincyRegistries.CROSS.get()), Quincy.RGB);
        sp.sendSystemMessage(Quincy.msg("rank_up", Quincy.roman(r.number()), sub, r.xp()).withColor(Quincy.RGB));
        QuincyFx.pentacle(sl, at.add(0, 0.1, 0), 2.6, 0F);
        QuincyFx.pentacle(sl, at.add(0, 0.1, 0), 1.3, 0.6F);
        QuincyFx.pillar(sl, at, 6.0, 90);
        QuincyFx.sound(sl, at, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F);
        QuincyFx.sound(sl, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.7F + 0.1F * r.number());
        QuincyFx.sound(sl, at, SoundEvents.PLAYER_LEVELUP, 0.6F, 1.1F);
    }

    /** Escolha da Letra (rank III ou mais; definitiva — só um comando de operador troca). */
    public static void chooseLetter(ServerPlayer sp, Quincy.Letter letter, boolean force) {
        MagicData d = MagicApi.get(sp);
        if (!force) {
            if (!Quincy.isQuincy(sp) || Quincy.rank(d) < 3) {
                sp.displayClientMessage(Quincy.msg("letter_locked").withStyle(ChatFormatting.RED), true);
                return;
            }
            if (Quincy.letter(d) != null) {
                sp.displayClientMessage(Quincy.msg("letter_already").withStyle(ChatFormatting.GRAY), true);
                return;
            }
        }
        MagicApi.addCounter(sp, Quincy.LETTER, letter.ordinal() + 1 - d.counter(Quincy.LETTER));
        MagicApi.learnGlyph(sp, IsekaiMahou.id(Quincy.SCHRIFT));
        QuincyEvents.syncPassives(sp);
        ServerLevel sl = sp.serverLevel();
        sp.sendSystemMessage(Quincy.msg("letter_chosen", letter.displayName()).withColor(Quincy.RGB));
        sp.sendSystemMessage(letter.passive().withStyle(ChatFormatting.GRAY));
        Rewards.toast(sp, Quincy.msg("letter_toast", String.valueOf(letter.symbol)), letter.displayName(),
                new ItemStack(QuincyRegistries.CROSS.get()), letter.rgb);
        QuincyFx.pillar(sl, sp.position(), 3.5, 60);
        sl.sendParticles(QuincyFx.dust(letter.rgb, 1.4F), sp.getX(), sp.getY() + 1.2, sp.getZ(), 40, 0.5, 0.7, 0.5, 0.05);
        QuincyFx.sound(sl, sp.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.8F);
    }

    /** Avisa (uma vez por rank) que o próximo rank já pode ser feito. */
    static void hintIfReady(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (!Quincy.isQuincy(sp)) return;
        Rank next = rank(Quincy.rank(d) + 1);
        if (next == null) return;
        String flag = "quincy.rank_hint." + next.number();
        if (d.hasFlag(flag) || !meets(d, next, null)) return;
        d.setFlag(flag, true);
        sp.sendSystemMessage(Quincy.msg("rank_ready", Quincy.roman(next.number())).withStyle(ChatFormatting.GOLD));
        sp.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private QuincyRanks() {}
}
