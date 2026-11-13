package com.renanpontes.isekaimahou.feature.class_rogue;

import com.renanpontes.isekaimahou.feature.class_rogue.RogueFxNet;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Progressão própria do Ladino: o <b>Livro-Razão do Ladrão</b> (5 ranks, contador {@code ritual_rank.rogue} e o geral
 * {@code ritual_rank}). Cada rank pede um Círculo e "feitos de ladrão" contados pelo próprio módulo (furtos, golpes
 * pelas costas, mana/bênçãos/magias roubadas, chefes roubados, Grandes Golpes). Sobe-se pelo Livro-Razão (usar) ou pela
 * aba "Ladino" do menu B. Dificuldade igual à das outras classes da v1.0 (Quincy, Apostador, Ranger, Steel Ball): nível
 * 5/15/35/55/75, Círculo 1/2/4/6/8 e XP 600/1500/3500/7000/14000.
 * <ol>
 *   <li><b>Gatuno</b> (nv 5, C1): 10 furtos, 20 pelas costas → Ponto Vital, Sifão.</li>
 *   <li><b>Larápio das Sombras</b> (nv 15, C2): 40 furtos, 80 pelas costas, 1000 de recurso roubado, 15 Pontos Vitais →
 *   Roubar Magia, Roubar Bênção, Página Roubada, Presa da Sombra (adaga II).</li>
 *   <li><b>Sombra da Guilda</b> (nv 35, C4): 100 furtos, 200 pelas costas, 8 magias roubadas, 6 bênçãos roubadas, 1 chefe
 *   roubado → Leque de Facas.</li>
 *   <li><b>Mestre-Ladrão</b> (nv 55, C6): 200 furtos, 400 pelas costas, 20 magias roubadas, 15 páginas conjuradas, 2 chefes
 *   roubados → Golpe do Ladrão Fantasma (forma G), Ferrão do Mestre-Ladrão (adaga III).</li>
 *   <li><b>Rei dos Ladrões</b> (nv 75, C8): 350 furtos, 800 pelas costas, 40 magias roubadas, 10 Golpes do Ladrão
 *   Fantasma, 4 chefes roubados → as 2 Lendas do Ladino, Lâmina Fantasma (adaga IV), 4ª página no Grimório e bolso duplo.</li>
 * </ol>
 * Cada rank também dá +2% de crítico (passiva).
 */
public final class RogueRanks {
    public static final String RANK_COUNTER = "ritual_rank.rogue";
    public static final String GLOBAL_RANK = "ritual_rank";
    public static final int MAX_RANK = 5;
    private static final int[] XP = {600, 1500, 3500, 7000, 14000};
    private static final int[] CIRCLE = {1, 2, 4, 6, 8};
    private static final int[] LEVEL = {5, 15, 35, 55, 75};

    /** Um requisito: contador do MagicData e quanto precisa. */
    public record Req(String counter, int amount) {
        public MutableComponent label() {
            return Component.translatable(RogueKeys.k("class_rogue.isekai_mahou.req.", counter.replace('.', '_')), amount);
        }
    }

    private static final List<List<Req>> REQS = List.of(
            List.of(new Req("rogue.steal", 10), new Req("rogue.backstab", 20)),
            List.of(new Req("rogue.steal", 40), new Req("rogue.backstab", 80), new Req("rogue.mana_stolen", 1000),
                    new Req("rogue.vital", 15)),
            List.of(new Req("rogue.steal", 100), new Req("rogue.backstab", 200), new Req("rogue.spell_stolen", 8),
                    new Req("rogue.buff_stolen", 6), new Req("rogue.steal.elite", 1)),
            List.of(new Req("rogue.steal", 200), new Req("rogue.backstab", 400), new Req("rogue.spell_stolen", 20),
                    new Req("rogue.stolen_cast", 15), new Req("rogue.steal.elite", 2)),
            List.of(new Req("rogue.steal", 350), new Req("rogue.backstab", 800), new Req("rogue.spell_stolen", 40),
                    new Req("rogue.heist", 10), new Req("rogue.steal.elite", 4)));

    private static final List<List<ResourceLocation>> GLYPHS = List.of(
            List.of(RogueGlyphs.VITAL_MARK, RogueGlyphs.MANA_SIPHON, RogueGlyphs.LURK, RogueGlyphs.DRAIN_TOUCH),
            List.of(RogueGlyphs.SPELL_THEFT, RogueGlyphs.BUFF_THEFT, RogueGlyphs.STOLEN_PAGE, RogueGlyphs.ASSASSIN_CLAW),
            List.of(RogueGlyphs.KNIFE_FAN, RogueGlyphs.RHYTHM_ECHO),
            List.of(RogueGlyphs.PHANTOM_HEIST, RogueGlyphs.GODSPEED),
            List.of(RogueGlyphs.ONE_CUT));

    public static int rank(MagicData d) {
        return Math.max(0, Math.min(MAX_RANK, d.counter(RANK_COUNTER)));
    }

    /** Requisitos do rank {@code r} (1..5). */
    public static List<Req> requirements(int r) {
        return r < 1 || r > MAX_RANK ? List.of() : REQS.get(r - 1);
    }

    public static int circleFor(int r) {
        return r < 1 || r > MAX_RANK ? 1 : CIRCLE[r - 1];
    }

    public static int levelFor(int r) {
        return r < 1 || r > MAX_RANK ? 1 : LEVEL[r - 1];
    }

    public static int xpFor(int r) {
        return r < 1 || r > MAX_RANK ? 0 : XP[r - 1];
    }

    /** Glifos ensinados no rank {@code r}. */
    public static List<ResourceLocation> glyphsFor(int r) {
        return r < 1 || r > MAX_RANK ? List.of() : GLYPHS.get(r - 1);
    }

    public static MutableComponent rankName(int r) {
        return Component.translatable(RogueKeys.k("class_rogue.isekai_mahou.rank.", Math.max(0, Math.min(MAX_RANK, r))));
    }

    /** Pode subir para o próximo rank agora? */
    public static boolean ready(MagicData d) {
        int next = rank(d) + 1;
        if (next > MAX_RANK || d.path() != MagicPath.ROGUE || d.circle() < circleFor(next) || d.level() < levelFor(next)) return false;
        for (Req q : requirements(next)) if (d.counter(q.counter()) < q.amount()) return false;
        return true;
    }

    /**
     * Tenta subir de rank (Livro-Razão / aba). Sem requisitos: mostra o que falta.
     * @param verbose true = lista o que falta no chat
     */
    public static boolean tryRankUp(ServerPlayer sp, boolean verbose) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.ROGUE) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.rank.not_rogue").withStyle(ChatFormatting.RED), true);
            return false;
        }
        int next = rank(d) + 1;
        if (next > MAX_RANK) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.rank.max").withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        if (!ready(d)) {
            if (verbose) {
                sp.sendSystemMessage(Component.translatable("message.isekai_mahou.class_rogue.rank.missing", rankName(next))
                        .withStyle(ChatFormatting.GRAY));
                if (d.level() < levelFor(next)) {
                    sp.sendSystemMessage(Component.literal(" ✗ ").withStyle(ChatFormatting.RED)
                            .append(Component.translatable("class_rogue.isekai_mahou.req.level", levelFor(next)).withStyle(ChatFormatting.GRAY)));
                }
                if (d.circle() < circleFor(next)) {
                    sp.sendSystemMessage(Component.literal(" ✗ ").withStyle(ChatFormatting.RED)
                            .append(Component.translatable("class_rogue.isekai_mahou.req.circle", circleFor(next)).withStyle(ChatFormatting.GRAY)));
                }
                for (Req q : requirements(next)) {
                    int have = d.counter(q.counter());
                    boolean ok = have >= q.amount();
                    sp.sendSystemMessage(Component.literal(ok ? " ✔ " : " ✗ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
                            .append(q.label().withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(" (" + Math.min(have, q.amount()) + "/" + q.amount() + ")").withStyle(ChatFormatting.DARK_GRAY)));
                }
            }
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.0F);
            return false;
        }
        promote(sp, next);
        return true;
    }

    /** Concede o rank (já validado ou por comando). */
    public static void promote(ServerPlayer sp, int next) {
        MagicApi.addCounter(sp, RANK_COUNTER, 1);
        MagicApi.addCounter(sp, GLOBAL_RANK, 1);
        MagicApi.giveXp(sp, xpFor(next), "ritual");
        for (ResourceLocation g : glyphsFor(next)) MagicApi.learnGlyph(sp, g);
        List<ItemStack> items = new ArrayList<>();
        switch (next) {
            case 2 -> items.add(new ItemStack(RogueContent.DAGGER_T2.get()));
            case 4 -> items.add(new ItemStack(RogueContent.DAGGER_T3.get()));
            case 5 -> {
                items.add(new ItemStack(RogueContent.DAGGER_T4.get()));
                items.add(Legends.scroll(RogueGlyphs.LEGEND_HEIST));
                items.add(Legends.scroll(RogueGlyphs.LEGEND_BANDIT));
                items.addAll(RogueTechniques.legendScrolls());
                MagicApi.get(sp).setFlag(RogueTechniques.LEGENDS_FLAG, true);
            }
            default -> items.add(new ItemStack(RogueContent.THROWING_KNIFE.get(), 8));
        }
        items.removeIf(ItemStack::isEmpty);
        Rewards.giveAll(sp, items);

        ServerLevel level = sp.serverLevel();
        Component title = Component.translatable("message.isekai_mahou.class_rogue.rank.title", roman(next));
        Cutscenes.play(sp, "ascension", sp, title, rankName(next), RogueFx.PURPLE, 60, false);
        Rewards.toast(sp, title, rankName(next), new ItemStack(RogueContent.LEDGER.get()), RogueFx.PURPLE);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.class_rogue.rank.up", rankName(next), xpFor(next))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        sp.sendSystemMessage(Component.translatable(RogueKeys.k("message.isekai_mahou.class_rogue.rank.flavor.", next))
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        // sombras em espiral subindo (cliente), moedas caindo
        RogueFxNet.at(level, RogueFxNet.LEARN, sp.position(), net.minecraft.world.phys.Vec3.ZERO, 1F, RogueFx.PURPLE, sp.getId());
        level.sendParticles(ParticleTypes.WAX_OFF, sp.getX(), sp.getY() + 2.2, sp.getZ(), 30, 0.6, 0.2, 0.6, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, sp.getX(), sp.getY() + 0.3, sp.getZ(), 20, 0.6, 0.1, 0.6, 0.02);
        RogueFx.sound(level, sp.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.1F);
        RogueFx.sound(level, sp.position(), SoundEvents.BUNDLE_DROP_CONTENTS, 1.0F, 0.8F);
    }

    public static String roman(int r) {
        return switch (r) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }

    private RogueRanks() {}
}
