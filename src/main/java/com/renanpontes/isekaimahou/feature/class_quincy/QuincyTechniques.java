package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * As TÉCNICAS AVANÇADAS do Quincy (pacote de técnicas icônicas, homenagens a Bleach), todas glifos de origem montáveis
 * no Grimório, ligadas à progressão do Auswählen — todas chegam sozinhas quando os requisitos são cumpridos:
 * <pre>
 *  Heilig Bogen (Arco Sagrado)     rank IV
 *  Sankt Zwinger (Gaiola Sagrada)  rank IV · 80 usos da Letra
 *  A Antítese                      rank IV · 350 absorções
 *  Quincy: Letzt Stil (forma G)    rank V · treino com a Luva Sanrei (150 abates segurando a luva)
 *  O Todo-Poderoso (forma G)       rank V · 8 Auswählen · 6 chefes
 * </pre>
 * Também: o treino da <b>Luva Sanrei</b> (na outra mão: técnicas Quincy −30% de poder, cada abate com elas conta) e o
 * estado "sem poder Quincy" que a Letzt Stil deixa ({@link Quincy#POWERLESS}, em segundos, salvo nos contadores).
 */
public final class QuincyTechniques {
    /** Uma técnica: glifo, rank mínimo, requisitos, cor e a lenda que vem junto (ou null). */
    public record Technique(String id, String glyph, int rank, List<QuincyRanks.Req> reqs, int rgb, @Nullable String legend) {
        public boolean known(MagicData d) {
            return d.knowsGlyph(IsekaiMahou.id(glyph));
        }

        public boolean ready(MagicData d) {
            if (Quincy.rank(d) < rank) return false;
            for (QuincyRanks.Req q : reqs) if (!q.ok(d)) return false;
            return true;
        }

        @Nullable
        public Glyph glyphObj() {
            return GlyphRegistry.get(IsekaiMahou.id(glyph));
        }

        public MutableComponent howText() {
            return Component.translatable(TECH_HOW + id);
        }
    }

    private static final String TECH_HOW = "screen.isekai_mahou.quincy.tech_how.";
    private static final String TECH_TIP = "screen.isekai_mahou.quincy.tech_tip.";
    /** Segundos sem poder Quincy depois da Letzt Stil (10 minutos). */
    public static final int LETZT_POWERLESS = 600;
    /** Abates de treino com a Luva Sanrei para a Letzt Stil. */
    public static final int SANREI_GOAL = 150;
    /** Poder das técnicas Quincy segurando a Luva Sanrei (o peso do treino). */
    public static final float SANREI_POWER = 0.7F;
    public static final ResourceLocation POWERLESS_REGEN = IsekaiMahou.id("quincy_powerless");

    private static List<Technique> all;

    private static QuincyRanks.Req counter(String key, String counter, int amount) {
        return new QuincyRanks.Req(key, amount, d -> d.counter(counter));
    }

    public static synchronized List<Technique> all() {
        if (all == null) {
            all = List.of(
                    new Technique("heilig_bogen", Quincy.HEILIG_BOGEN, 4, List.of(), 0xBFE8FF, QuincyLegends.BOGEN),
                    new Technique("sankt_zwinger", Quincy.SANKT_ZWINGER, 4, List.of(counter("schrift", Quincy.SCHRIFT_USES, 80)), 0xE8F8FF,
                            QuincyLegends.ZWINGER),
                    new Technique("antithesis", Quincy.ANTITHESIS, 4, List.of(counter("absorbed", Quincy.ABSORBED, 350)), 0xD8E4FF, null),
                    new Technique("letzt_stil", Quincy.LETZT_STIL, 5, List.of(counter("sanrei", Quincy.SANREI, SANREI_GOAL)), 0xFFF2C0,
                            QuincyLegends.LETZT),
                    new Technique("almighty", Quincy.ALMIGHTY, 5, List.of(counter("auswahlen", Quincy.AUSWAHLEN, 8),
                            new QuincyRanks.Req("bosses", 6, Quincy::bossKills)), 0x9A9AC8, null));
        }
        return all;
    }

    /** Linhas de "o que falta" de uma técnica. */
    public static List<Component> missing(MagicData d, Technique t) {
        List<Component> out = new ArrayList<>();
        if (Quincy.rank(d) < t.rank()) out.add(Component.translatable(Quincy.REQ_KEY + "rank", Quincy.roman(t.rank())));
        for (QuincyRanks.Req q : t.reqs()) {
            if (!q.ok(d)) out.add(q.label().append(" (" + Math.min(q.current().applyAsInt(d), q.amount()) + "/" + q.amount() + ")"));
        }
        return out;
    }

    /** Confere as técnicas (login, feitos, rank). Só para Quincy. */
    public static void check(ServerPlayer sp) {
        if (!Quincy.isQuincy(sp)) return;
        MagicData d = MagicApi.get(sp);
        for (Technique t : all()) {
            if (!t.known(d) && t.ready(d)) grant(sp, t);
        }
    }

    public static void grant(ServerPlayer sp, Technique t) {
        MagicApi.learnGlyph(sp, IsekaiMahou.id(t.glyph()));
        if (t.legend() != null) {
            ItemStack scroll = Legends.scroll(t.legend());
            if (!scroll.isEmpty()) Rewards.give(sp, scroll);
        }
        Glyph g = t.glyphObj();
        MutableComponent name = g == null ? Component.literal(t.id()) : g.displayName();
        ServerLevel sl = sp.serverLevel();
        Vec3 at = sp.position();
        Announce.send(sp, Quincy.msg("tech_announce", sp.getDisplayName(), name), t.rgb(), Announce.STYLE_SYSTEM);
        Rewards.toast(sp, Quincy.msg("tech_toast"), name.copy().withColor(t.rgb()), new ItemStack(QuincyRegistries.CROSS.get()), t.rgb());
        sp.sendSystemMessage(Quincy.msg("tech_learned", name.copy().withColor(t.rgb())).withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(Component.translatable(TECH_TIP + t.id()).withStyle(ChatFormatting.GRAY));
        QuincyFx.pentacle(sl, at.add(0, 0.1, 0), 2.8, 0F);
        QuincyFx.pillar(sl, at, 7.0, 100);
        QuincyFx.sound(sl, at, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.0F);
        QuincyFx.sound(sl, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.6F);
    }

    // =====================================================================================
    //  Luva Sanrei (treino da Letzt Stil)
    // =====================================================================================
    public static boolean holdingSanrei(ServerPlayer sp) {
        return sp.getOffhandItem().is(QuincyRegistries.SANREI_GLOVE.get()) || sp.getMainHandItem().is(QuincyRegistries.SANREI_GLOVE.get());
    }

    /** Um abate com técnica Quincy: se estiver com a luva, conta para o treino. */
    static void onKill(ServerPlayer sp) {
        if (!holdingSanrei(sp)) return;
        int v = MagicApi.addCounter(sp, Quincy.SANREI, 1);
        if (v == SANREI_GOAL || v % 25 == 0) {
            sp.displayClientMessage(Quincy.msg("sanrei_progress", Math.min(v, SANREI_GOAL), SANREI_GOAL).withColor(0xFFF2C0), true);
        }
    }

    // =====================================================================================
    //  Sem poder Quincy (depois da Letzt Stil)
    // =====================================================================================
    public static int powerless(MagicData d) {
        return Math.max(0, d.counter(Quincy.POWERLESS));
    }

    public static void startPowerless(ServerPlayer sp, int seconds) {
        MagicData d = MagicApi.get(sp);
        if (seconds > powerless(d)) MagicApi.addCounter(sp, Quincy.POWERLESS, seconds - d.counter(Quincy.POWERLESS));
        d.setMana(0F);
        applyRegen(sp, true);
        sp.sendSystemMessage(Quincy.msg("powerless_start", seconds / 60).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean blockIfPowerless(ServerPlayer sp) {
        int s = powerless(MagicApi.get(sp));
        if (s <= 0 || sp.getAbilities().instabuild) return false;
        sp.displayClientMessage(Quincy.msg("powerless_block", String.format("%d:%02d", s / 60, s % 60)).withStyle(ChatFormatting.DARK_GRAY), true);
        sp.playNotifySound(SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 0.6F);
        return true;
    }

    /** Uma vez por segundo: desconta o tempo sem poder. */
    static void secondTick(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        int s = powerless(d);
        if (s <= 0) {
            if (d.counter(Quincy.POWERLESS) < 0) MagicApi.addCounter(sp, Quincy.POWERLESS, -d.counter(Quincy.POWERLESS));
            applyRegen(sp, false);
            return;
        }
        applyRegen(sp, true);
        int left = MagicApi.addCounter(sp, Quincy.POWERLESS, -1);
        if (sp.tickCount % 60 == 0) sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, false, true));
        if (left <= 0) {
            applyRegen(sp, false);
            sp.sendSystemMessage(Quincy.msg("powerless_end").withColor(Quincy.RGB));
            QuincyFx.pillar(sp.serverLevel(), sp.position(), 4.0, 50);
            QuincyFx.sound(sp.serverLevel(), sp.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.4F);
        }
    }

    private static void applyRegen(ServerPlayer sp, boolean on) {
        AttributeInstance inst = sp.getAttribute(CoreRegistries.MANA_REGEN);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(POWERLESS_REGEN);
        if (!on) {
            if (cur != null) inst.removeModifier(POWERLESS_REGEN);
            return;
        }
        if (cur == null) inst.addTransientModifier(new AttributeModifier(POWERLESS_REGEN, -0.7, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private QuincyTechniques() {}
}
