package com.renanpontes.isekaimahou.feature.class_shinigami;

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
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.InnerWorld;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Progressão própria do Shinigami: os 5 ranks do <b>Caminho da Zanpakutō</b>. Cada rank pede Círculo e feitos (abates com
 * a espada/técnicas, Kidō, meditações, cortes da Shikai, liberações, chefes) e é FEITO no Mundo Interior, conversando
 * (e lutando) com o espírito da espada ({@code SpiritTrials}). Dá glifos de origem, itens e XP — dificuldade parecida
 * com os rituais das outras classes (Círculos 1/2/4/6/8 e XP 600/1500/3500/7000/14000, o mesmo ritmo da Casa do
 * Apostador e dos Juramentos do Cavaleiro). Ao subir: +1 em {@link Shinigami#RANK} e na convenção global
 * {@code ritual_rank}.
 * <pre>
 *  I   Asauchi Desperto   C1  15 abates · 1 meditação                                   → Bakudō nº 61
 *  II  O Nome             C2  60 abates · 25 Kidō · 3 meditações                         → arquétipo + Hadō nº 31
 *  III Shikai             C4  150 abates · 80 cortes da assinatura · 1 chefe + LUTA      → Shikai + Presa Lunar
 *  IV  Mestre do Kidō     C6  120 Kidō · 40 liberações · 400 cortes · 2 chefes            → Bakudō nº 81 + lenda
 *  V   Bankai             C8  600 abates · 100 liberações · 4 chefes + LUTA (3 fases)     → Bankai + lenda
 * </pre>
 */
public final class ShinigamiRanks {
    /** Um requisito: contador do MagicData (ou derivado) até {@code amount}. */
    public record Req(String key, int amount, ToIntFunction<MagicData> current) {
        public MutableComponent label() {
            return Component.translatable(Shinigami.REQ_KEY + key, amount);
        }

        public boolean ok(MagicData d) {
            return current.applyAsInt(d) >= amount;
        }
    }

    /**
     * Um rank: Círculo mínimo, XP, requisitos, glifos ensinados (o do arquétipo entra no rank II), itens, uma lenda
     * opcional e se termina numa luta no Mundo Interior.
     */
    public record Rank(int number, int circle, int xp, List<Req> reqs, List<String> glyphs, List<ItemStack> items,
                       @Nullable String legend, boolean fight) {}

    private static Req counter(String key, String counter, int amount) {
        return new Req(key, amount, d -> d.counter(counter));
    }

    private static Req kills(int n) {
        return counter("kills", Shinigami.KILLS, n);
    }

    private static Req kido(int n) {
        return counter("kido", Shinigami.KIDO, n);
    }

    private static Req bosses(int n) {
        return new Req("bosses", n, Shinigami::bossKills);
    }

    private static List<Rank> ranks;

    /** Os 5 ranks (montados na primeira consulta: os itens precisam do registro pronto). */
    public static synchronized List<Rank> ranks() {
        if (ranks == null) {
            ranks = List.of(
                    new Rank(1, 1, 600, List.of(kills(15), counter("meditations", Shinigami.MEDITATIONS, 1)),
                            List.of(Shinigami.BAKUDO_61),
                            List.of(new ItemStack(ShinigamiRegistries.SPIRIT_CANDY.get(), 4)), null, false),
                    new Rank(2, 2, 1500, List.of(kills(60), kido(25), counter("meditations", Shinigami.MEDITATIONS, 3)),
                            List.of(Shinigami.HADO_31),
                            List.of(new ItemStack(ShinigamiRegistries.SPIRIT_CANDY.get(), 6)), null, false),
                    new Rank(3, 4, 3500, List.of(kills(150), counter("shikai_hits", Shinigami.SHIKAI_HITS, 80), bosses(1)),
                            List.of(Shinigami.MOON_FANG),
                            List.of(new ItemStack(ShinigamiRegistries.SPIRIT_CANDY.get(), 8)), null, true),
                    new Rank(4, 6, 7000, List.of(kido(120), counter("releases", Shinigami.RELEASES, 40),
                            counter("shikai_hits", Shinigami.SHIKAI_HITS, 400), bosses(2)),
                            List.of(Shinigami.BAKUDO_81),
                            List.of(new ItemStack(ShinigamiRegistries.SPIRIT_CANDY.get(), 12)), ShinigamiLegends.BLACK_MOON, false),
                    new Rank(5, 8, 14000, List.of(kills(600), counter("releases", Shinigami.RELEASES, 100), bosses(4)),
                            List.of(Shinigami.BANKAI),
                            List.of(new ItemStack(ShinigamiRegistries.SPIRIT_CANDY.get(), 16)), ShinigamiLegends.DOUBLE_KIDO, true));
        }
        return ranks;
    }

    @Nullable
    public static Rank rank(int number) {
        return number >= 1 && number <= Shinigami.MAX_RANK ? ranks().get(number - 1) : null;
    }

    /** O jogador cumpre os requisitos do rank? Escreve o que falta em {@code missing}. */
    public static boolean meets(MagicData d, Rank r, @Nullable List<Component> missing) {
        boolean ok = true;
        if (d.circle() < r.circle()) {
            ok = false;
            if (missing != null) missing.add(Component.translatable(Shinigami.REQ_KEY + "circle", r.circle()));
        }
        for (Req q : r.reqs()) {
            if (!q.ok(d)) {
                ok = false;
                if (missing != null) missing.add(q.label().append(" (" + Math.min(q.current().applyAsInt(d), q.amount()) + "/" + q.amount() + ")"));
            }
        }
        return ok;
    }

    /** Sobe para o rank {@code r} (sem checar requisitos — o Mundo Interior e os comandos usam direto). */
    public static void grant(ServerPlayer sp, Rank r) {
        MagicData d = MagicApi.get(sp);
        int delta = r.number() - Shinigami.rank(d);
        if (delta <= 0) return;
        MagicApi.addCounter(sp, Shinigami.RANK, delta);
        MagicApi.addCounter(sp, Shinigami.GLOBAL_RANK, delta);
        MagicApi.giveXp(sp, r.xp(), "ritual");
        for (String g : r.glyphs()) MagicApi.learnGlyph(sp, IsekaiMahou.id(g));
        Archetype a = Shinigami.archetype(d);
        if (r.number() == 2 && a != null) MagicApi.learnGlyph(sp, IsekaiMahou.id(a.glyph));
        for (ItemStack it : r.items()) Rewards.give(sp, it.copy());
        if (r.legend() != null) {
            ItemStack scroll = Legends.scroll(r.legend());
            if (!scroll.isEmpty()) Rewards.give(sp, scroll);
        }
        ceremony(sp, r);
        if (r.number() == 3 && a != null) sp.sendSystemMessage(Shinigami.msg("shikai_learned", a.command()).withColor(a.rgb));
        if (r.number() == 5 && a != null) sp.sendSystemMessage(Shinigami.msg("bankai_learned", a.bankaiName()).withColor(a.rgb));
    }

    private static void ceremony(ServerPlayer sp, Rank r) {
        ServerLevel sl = sp.serverLevel();
        Vec3 at = sp.position();
        MutableComponent title = Shinigami.msg("rank_up_title", Shinigami.roman(r.number()));
        MutableComponent sub = Shinigami.rankTitle(r.number());
        for (ServerPlayer viewer : sl.players()) {
            if (viewer.distanceToSqr(sp) <= 32 * 32) Cutscenes.play(viewer, "ascension", sp, title, sub, Shinigami.RGB, 70, false);
        }
        Announce.send(sp, Shinigami.msg("rank_up_announce", sp.getDisplayName(), sub), Shinigami.RGB, Announce.STYLE_SYSTEM);
        Rewards.toast(sp, title, sub, new ItemStack(ShinigamiRegistries.ZANPAKUTO.get()), Shinigami.RGB);
        sp.sendSystemMessage(Shinigami.msg("rank_up", Shinigami.roman(r.number()), sub, r.xp()).withColor(Shinigami.RGB));
        int rgb = Shinigami.spiritRgb(MagicApi.get(sp));
        ShinigamiFx.ring(sl, at.add(0, 0.1, 0), 2.6, 36, 0F, ShinigamiFx.dust(rgb, 1.4F));
        ShinigamiFx.ring(sl, at.add(0, 0.1, 0), 1.3, 20, 0.6F, ShinigamiFx.REIATSU);
        ShinigamiFx.pillar(sl, at, 6.0, 90, ShinigamiFx.dust(rgb, 1.3F));
        ShinigamiFx.sound(sl, at, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.0F);
        ShinigamiFx.sound(sl, at, SoundEvents.BELL_RESONATE, 1.0F, 0.6F + 0.1F * r.number());
        ShinigamiFx.sound(sl, at, SoundEvents.PLAYER_LEVELUP, 0.6F, 1.1F);
    }

    /** Avisa (uma vez por rank) que o próximo rank já pode ser feito no Mundo Interior. */
    public static void hintIfReady(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (!Shinigami.isShinigami(sp)) return;
        Rank next = rank(Shinigami.rank(d) + 1);
        if (next == null) return;
        String flag = "shinigami.rank_hint." + next.number();
        if (d.hasFlag(flag) || !meets(d, next, null)) return;
        d.setFlag(flag, true);
        sp.sendSystemMessage(Shinigami.msg(InnerWorld.is(sp.level()) ? "rank_ready_inner" : "rank_ready", Shinigami.roman(next.number()),
                Shinigami.spiritName(d)).withStyle(ChatFormatting.GOLD));
        sp.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Linhas de "o que falta" para o próximo rank (aba e comando). */
    public static List<Component> missing(MagicData d) {
        List<Component> out = new ArrayList<>();
        Rank next = rank(Shinigami.rank(d) + 1);
        if (next != null) meets(d, next, out);
        return out;
    }

    private ShinigamiRanks() {}
}
