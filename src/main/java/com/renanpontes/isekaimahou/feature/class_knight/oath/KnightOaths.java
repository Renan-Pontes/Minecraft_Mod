package com.renanpontes.isekaimahou.feature.class_knight.oath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_knight.KnightCounters;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightLegends;
import com.renanpontes.isekaimahou.feature.class_knight.KnightTechniques;
import com.renanpontes.isekaimahou.feature.class_knight.KnightRegistries;
import com.renanpontes.isekaimahou.feature.class_knight.LegendSword;
import com.renanpontes.isekaimahou.feature.class_knight.glyph.KnightGlyphs;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Os 5 JURAMENTOS do Cavaleiro Rúnico (o ritual/progressão da classe) e o despertar das Espadas Lendárias, ambos
 * feitos no Altar do Juramento. Cada Juramento pede Círculo, golpes rúnicos, abates com espada, chefes vencidos e
 * espadas despertadas, e consome uma oferenda; dá glifos, itens, XP e soma {@code ritual_rank} +1.
 * <pre>
 *  I   Escudeiro Rúnico      C1  25 golpes rúnicos · 15 abates de espada                     → Aura de Espada, Investida Rúnica
 *  II  Cavaleiro Gravado     C2  150 · 80                                                    → Égide Rúnica, Chuva de Espadas, Lâmina II
 *  III Cavaleiro da Távola   C4  400 · 200 · 1 chefe · 1 espada lendária                     → EXCALIBUR (forma G), Lâmina III
 *  IV  Paladino das Runas    C6   700 · 450 · 2 chefes · 2 espadas                           → Sentença do Juramento, +1 espaço de runa, Lâmina IV
 *  V   Rei Cavaleiro Rúnico  C8  1200 · 800 · 4 chefes · 3 espadas                           → Coroa Rúnica (+15% nas técnicas, +armadura)
 * Ritmo igual ao das outras classes (Provas da Forja, Livro-Razão do Ladino): XP 600/1500/3500/7000/14000.
 * </pre>
 */
public final class KnightOaths {
    public static final int MAX_RANK = 5;

    /** Um Juramento. */
    public record Rank(int number, List<Req> reqs, List<Offer> offers, int xp, List<String> glyphs, List<Supplier<ItemStack>> items) {
        public MutableComponent title() {
            return com.renanpontes.isekaimahou.feature.class_knight.KnightText.tr("oath", number);
        }

        public MutableComponent reward() {
            return com.renanpontes.isekaimahou.feature.class_knight.KnightText.tr("oath", number, "reward");
        }

        public boolean reqsMet(MagicData d) {
            for (Req r : reqs) if (!r.met(d)) return false;
            return true;
        }
    }

    private static final String PROC_LABEL = "req.isekai_mahou.class_knight.rune_proc";
    private static final String KILL_LABEL = "req.isekai_mahou.class_knight.sword_kill";
    private static final String BOSS_LABEL = "req.isekai_mahou.class_knight.bosses";
    private static final String SWORD_LABEL = "req.isekai_mahou.class_knight.legend_swords";

    public static final List<Rank> RANKS = List.of(
            new Rank(1, List.of(Req.circle(1), Req.counter(KnightCounters.RUNE_PROC, 25, PROC_LABEL),
                    Req.counter(KnightCounters.SWORD_KILL, 15, KILL_LABEL)),
                    List.of(new Offer("minecraft:iron_ingot", 8), new Offer("isekai_mahou:essence_light", 2)),
                    600, List.of(KnightGlyphs.BLADE_AURA, KnightGlyphs.RUNIC_THRUST, KnightGlyphs.STRIKE_AIR),
                    List.of(() -> new ItemStack(CoreRegistries.ESSENCES.get(Element.LIGHT).get(), 3))),
            new Rank(2, List.of(Req.circle(2), Req.counter(KnightCounters.RUNE_PROC, 150, PROC_LABEL),
                    Req.counter(KnightCounters.SWORD_KILL, 80, KILL_LABEL)),
                    List.of(new Offer("minecraft:gold_ingot", 6), new Offer("isekai_mahou:essence_light", 4),
                            new Offer("isekai_mahou:essence_earth", 4)),
                    1500, List.of(KnightGlyphs.AEGIS, KnightGlyphs.SWORD_RAIN, KnightGlyphs.ANTI_MAGIC),
                    List.of(() -> new ItemStack(KnightRegistries.RUNIC_TIERED.get(1).get()))),
            new Rank(3, List.of(Req.circle(4), Req.counter(KnightCounters.RUNE_PROC, 400, PROC_LABEL),
                    Req.counter(KnightCounters.SWORD_KILL, 200, KILL_LABEL), Req.counter(KnightCounters.BOSSES, 1, BOSS_LABEL),
                    Req.counter(KnightCounters.LEGEND_SWORDS, 1, SWORD_LABEL)),
                    List.of(new Offer("minecraft:diamond", 4), new Offer("isekai_mahou:mana_crystal", 6)),
                    3500, List.of(KnightGlyphs.EXCALIBUR),
                    List.of(() -> new ItemStack(KnightRegistries.RUNIC_TIERED.get(2).get()))),
            new Rank(4, List.of(Req.circle(6), Req.counter(KnightCounters.RUNE_PROC, 700, PROC_LABEL),
                    Req.counter(KnightCounters.SWORD_KILL, 450, KILL_LABEL), Req.counter(KnightCounters.BOSSES, 2, BOSS_LABEL),
                    Req.counter(KnightCounters.LEGEND_SWORDS, 2, SWORD_LABEL)),
                    List.of(new Offer("minecraft:netherite_ingot", 1), new Offer("isekai_mahou:essence_light", 8),
                            new Offer("isekai_mahou:essence_earth", 8)),
                    7000, List.of(KnightGlyphs.JUDGMENT, KnightGlyphs.BALMUNG),
                    List.of(() -> new ItemStack(KnightRegistries.RUNIC_TIERED.get(3).get()))),
            new Rank(5, List.of(Req.circle(8), Req.counter(KnightCounters.RUNE_PROC, 1200, PROC_LABEL),
                    Req.counter(KnightCounters.SWORD_KILL, 800, KILL_LABEL), Req.counter(KnightCounters.BOSSES, 4, BOSS_LABEL),
                    Req.counter(KnightCounters.LEGEND_SWORDS, 3, SWORD_LABEL)),
                    List.of(new Offer("minecraft:nether_star", 1), new Offer("isekai_mahou:mana_crystal", 16)),
                    14000, List.of(),
                    List.of(() -> new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 8),
                            () -> Legends.scroll(KnightLegends.AVALON_UTOPIA),
                            () -> Legends.scroll(KnightLegends.BALMUNG_TWILIGHT),
                            () -> Legends.scroll(KnightLegends.BLACK_DIVIDER))));

    public static int rank(MagicData d) {
        return d == null ? 0 : Math.min(MAX_RANK, d.counter(KnightCounters.OATH));
    }

    @Nullable
    public static Rank next(MagicData d) {
        int r = rank(d);
        return r >= MAX_RANK ? null : RANKS.get(r);
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    // =====================================================================================
    //  Altar
    // =====================================================================================
    /** Clique no altar: presta o Juramento pronto, ou desperta uma espada pronta, ou explica o que falta. */
    public static void useAltar(ServerPlayer player, ServerLevel level, BlockPos altar) {
        MagicData d = MagicApi.get(player);
        if (d.path() != MagicPath.KNIGHT) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.class_knight.altar_not_knight")
                    .withStyle(ChatFormatting.GRAY), true);
            KnightFx.sound(level, Vec3.atCenterOf(altar), SoundEvents.STONE_HIT, 0.8F, 0.6F);
            return;
        }
        KnightCounters.flush(player);
        Rank next = next(d);
        if (next != null && next.reqsMet(d)) {
            if (Offer.allPresent(player, next.offers)) {
                Offer.consume(player, next.offers);
                grantRank(player, next, level, altar);
            } else {
                missingOffers(player, next.offers);
            }
            return;
        }
        for (LegendSword s : LegendSword.values()) {
            if (d.hasFlag(s.flag()) || !reqsMet(d, s)) continue;
            if (!s.site.check(level, altar)) {
                player.sendSystemMessage(Component.translatable("message.isekai_mahou.class_knight.sword_site", s.displayName(), s.site.text())
                        .withStyle(ChatFormatting.YELLOW));
                continue;
            }
            if (!Offer.allPresent(player, s.offers)) {
                player.sendSystemMessage(Component.translatable("message.isekai_mahou.class_knight.sword_ready", s.displayName())
                        .withStyle(ChatFormatting.GOLD));
                missingOffers(player, s.offers);
                continue;
            }
            Offer.consume(player, s.offers);
            awaken(player, s, level, altar);
            return;
        }
        explain(player, d, next);
    }

    public static boolean reqsMet(MagicData d, LegendSword s) {
        for (Req r : s.reqs) if (!r.met(d)) return false;
        return true;
    }

    private static void missingOffers(ServerPlayer player, List<Offer> offers) {
        MutableComponent line = Component.translatable("message.isekai_mahou.class_knight.offer_missing");
        boolean first = true;
        for (Offer o : offers) {
            if (!o.available()) continue;
            int have = o.have(player);
            if (!first) line.append(Component.literal(", "));
            first = false;
            line.append(Component.translatable("message.isekai_mahou.class_knight.offer_item", Math.min(have, o.count()), o.count(),
                    o.item().getDescription()).withStyle(have >= o.count() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        player.sendSystemMessage(line.withStyle(ChatFormatting.GRAY));
        KnightFx.sound(player.serverLevel(), player.position(), SoundEvents.VILLAGER_NO, 0.6F, 1.0F);
    }

    private static void explain(ServerPlayer player, MagicData d, @Nullable Rank next) {
        if (next == null) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.class_knight.oath_max").withStyle(ChatFormatting.GOLD));
        } else {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.class_knight.oath_next", roman(next.number()), next.title())
                    .withStyle(ChatFormatting.GOLD));
            for (Req r : next.reqs()) {
                player.sendSystemMessage(Component.literal(r.met(d) ? " ✔ " : " ✘ ").append(r.text(d))
                        .withStyle(r.met(d) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.class_knight.oath_tab").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Presta o Juramento: glifos, itens, XP, ritual_rank e cerimônia. Usado também pelo comando de teste. */
    public static void grantRank(ServerPlayer player, Rank rank, @Nullable ServerLevel level, @Nullable BlockPos altar) {
        MagicData d = MagicApi.get(player);
        if (rank(d) >= rank.number()) return;
        MagicApi.addCounter(player, KnightCounters.OATH, rank.number() - rank(d));
        MagicApi.addCounter(player, "ritual_rank", 1);
        for (String g : rank.glyphs()) MagicApi.learnGlyph(player, IsekaiMahou.id(g));
        if (rank.number() >= MAX_RANK) MagicApi.get(player).setFlag(KnightTechniques.LEGENDS_FLAG, true);
        List<ItemStack> items = new ArrayList<>();
        for (Supplier<ItemStack> s : rank.items()) {
            ItemStack st = s.get();
            if (!st.isEmpty()) items.add(st);
        }
        Rewards.giveAll(player, items);
        MagicApi.giveXp(player, rank.xp(), "ritual");
        Component title = Component.translatable("message.isekai_mahou.class_knight.oath_title", roman(rank.number()), rank.title());
        Rewards.banner(player, title, KnightFx.GOLD);
        Rewards.toast(player, title, rank.reward(), new ItemStack(KnightRegistries.OATH_ALTAR_ITEM.get()), KnightFx.GOLD);
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.class_knight.oath_done", roman(rank.number()), rank.title(),
                rank.reward()).withStyle(ChatFormatting.GOLD));
        ceremony(player, level != null ? level : player.serverLevel(), altar != null ? altar : player.blockPosition(), KnightFx.GOLD);
    }

    /** Desperta uma Espada Lendária (flag + contador + item). Usado também pelo comando de teste. */
    public static void awaken(ServerPlayer player, LegendSword s, @Nullable ServerLevel level, @Nullable BlockPos altar) {
        MagicData d = MagicApi.get(player);
        if (d.hasFlag(s.flag())) return;
        d.setFlag(s.flag(), true);
        d.markDirty();
        MagicApi.addCounter(player, KnightCounters.LEGEND_SWORDS, 1);
        MagicApi.addCounter(player, "feat.knight_sword." + s.key(), 1);
        ItemStack sword = new ItemStack(KnightRegistries.LEGENDS.get(s).get());
        Rewards.give(player, sword.copy());
        // técnica ligada ao feito (Caliburn: Espada da Seleção; Espada do Lago: Avalon)
        String tech = KnightTechniques.swordTechnique(s);
        if (tech != null) MagicApi.learnGlyph(player, IsekaiMahou.id(tech));
        Component title = Component.translatable("message.isekai_mahou.class_knight.sword_awakened", s.displayName());
        Rewards.banner(player, title, s.rgb);
        Rewards.toast(player, title, s.featName(), sword, s.rgb);
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.class_knight.sword_awakened_chat", s.displayName(), s.featName())
                .withStyle(ChatFormatting.GOLD));
        if (s == LegendSword.LAKE) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.class_knight.lake_lady").withStyle(ChatFormatting.AQUA,
                    ChatFormatting.ITALIC));
        }
        ceremony(player, level != null ? level : player.serverLevel(), altar != null ? altar : player.blockPosition(), s.rgb);
    }

    /** Coluna de luz no altar, raio só visual, sinos e runas. */
    private static void ceremony(ServerPlayer player, ServerLevel level, BlockPos altar, int rgb) {
        Vec3 c = Vec3.atBottomCenterOf(altar).add(0, 1, 0);
        KnightFx.pillar(level, c, 18, 0.8, rgb);
        KnightFx.runeCircle(level, c.subtract(0, 0.9, 0), 3.0, rgb, 48, 0F);
        KnightFx.runeCircle(level, c.subtract(0, 0.9, 0), 4.5, KnightFx.GOLD, 56, 0.6F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1, c.z, 60, 0.6, 1.2, 0.6, 0.4);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(c.x, c.y, c.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        KnightFx.sound(level, c, SoundEvents.BELL_BLOCK, 1.5F, 0.7F);
        KnightFx.sound(level, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        KnightFx.sound(level, c, SoundEvents.ANVIL_USE, 0.6F, 0.6F);
    }

    private KnightOaths() {}
}
