package com.renanpontes.isekaimahou.feature.class_shinigami;

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
 * As TÉCNICAS AVANÇADAS do Shinigami (pacote de técnicas icônicas, homenagens a Bleach), todas glifos de origem montáveis
 * no Grimório, ligadas à progressão da classe:
 * <pre>
 *  Getsuga Tenshō           rank IV                                         → aprende sozinho
 *  Hadō nº 90: Kurohitsugi  rank IV · 200 Kidō                              → aprende sozinho
 *  Máscara do Vazio + Cero  rank IV · 60 liberações · 2 chefes              → vencer o VAZIO INTERIOR no Mundo Interior
 *  Vasto Lorde (forma G)    rank V · Vazio domado · 30 usos da Máscara      → aprende sozinho
 *  Mugetsu (forma G)        rank V · 150 Getsuga · 6 chefes                 → a última lição do espírito (Mundo Interior)
 * </pre>
 * Também guarda o estado "sem poder espiritual" que o Mugetsu deixa ({@link Shinigami#POWERLESS}, em segundos, salvo
 * nos contadores): sem técnicas Shinigami, sem Shikai/Bankai e com o Vigor quase parado.
 */
public final class ShinigamiTechniques {
    /** Como a técnica é aprendida. */
    public enum How { AUTO, HOLLOW, LESSON }

    /** Uma técnica: glifos que ensina, rank mínimo, requisitos e como chega. */
    public record Technique(String id, List<String> glyphs, How how, int rank, List<ShinigamiRanks.Req> reqs, int rgb,
                            @Nullable String legend) {
        public boolean known(MagicData d) {
            return d.knowsGlyph(IsekaiMahou.id(glyphs.get(0)));
        }

        /** Rank e requisitos cumpridos (a técnica pode ser aprendida/desafiada). */
        public boolean ready(MagicData d) {
            if (Shinigami.rank(d) < rank) return false;
            for (ShinigamiRanks.Req q : reqs) if (!q.ok(d)) return false;
            return true;
        }

        @Nullable
        public Glyph glyph() {
            return GlyphRegistry.get(IsekaiMahou.id(glyphs.get(0)));
        }

        public MutableComponent howText() {
            return Component.translatable(TECH_HOW + id);
        }
    }

    private static final String TECH_HOW = "screen.isekai_mahou.shinigami.tech_how.";
    private static final String TECH_TIP = "screen.isekai_mahou.shinigami.tech_tip.";
    /** Segundos de "sem poder" depois do Mugetsu (10 minutos). */
    public static final int MUGETSU_POWERLESS = 600;
    public static final ResourceLocation POWERLESS_REGEN = IsekaiMahou.id("shinigami_powerless");

    private static List<Technique> all;

    private static ShinigamiRanks.Req counter(String key, String counter, int amount) {
        return new ShinigamiRanks.Req(key, amount, d -> d.counter(counter));
    }

    /** As 5 técnicas (montadas na primeira consulta). */
    public static synchronized List<Technique> all() {
        if (all == null) {
            ShinigamiRanks.Req bosses2 = new ShinigamiRanks.Req("bosses", 2, Shinigami::bossKills);
            ShinigamiRanks.Req bosses6 = new ShinigamiRanks.Req("bosses", 6, Shinigami::bossKills);
            ShinigamiRanks.Req hollow = new ShinigamiRanks.Req("hollow", 1, d -> d.hasFlag(Shinigami.HOLLOW_TAMED) ? 1 : 0);
            all = List.of(
                    new Technique("getsuga", List.of(Shinigami.GETSUGA), How.AUTO, 4, List.of(), 0x8FE0FF, ShinigamiLegends.GETSUGA_TENSHO),
                    new Technique("hado_90", List.of(Shinigami.HADO_90), How.AUTO, 4, List.of(counter("kido", Shinigami.KIDO, 200)), 0x6A2A9A,
                            ShinigamiLegends.KUROHITSUGI),
                    new Technique("visored", List.of(Shinigami.VISORED, Shinigami.CERO), How.HOLLOW, 4,
                            List.of(counter("releases", Shinigami.RELEASES, 60), bosses2), 0xE01030, null),
                    new Technique("vasto_lorde", List.of(Shinigami.VASTO_LORDE), How.AUTO, 5,
                            List.of(hollow, counter("visored_uses", Shinigami.VISORED_USES, 30)), 0xF2EEE6, null),
                    new Technique("mugetsu", List.of(Shinigami.MUGETSU), How.LESSON, 5,
                            List.of(counter("getsuga", Shinigami.GETSUGA_USES, 150), bosses6), 0x6A4AA0,
                            ShinigamiLegends.MUGETSU));
        }
        return all;
    }

    @Nullable
    public static Technique byId(String id) {
        for (Technique t : all()) if (t.id().equals(id)) return t;
        return null;
    }

    /** O Vazio Interior já pode ser desafiado (e ainda não foi domado). */
    public static boolean hollowReady(MagicData d) {
        Technique t = byId("visored");
        return t != null && !d.hasFlag(Shinigami.HOLLOW_TAMED) && t.ready(d);
    }

    /** O espírito já pode ensinar o Mugetsu. */
    public static boolean mugetsuReady(MagicData d) {
        Technique t = byId("mugetsu");
        return t != null && !t.known(d) && t.ready(d);
    }

    /** Linhas de "o que falta" de uma técnica. */
    public static List<Component> missing(MagicData d, Technique t) {
        List<Component> out = new ArrayList<>();
        if (Shinigami.rank(d) < t.rank()) out.add(Component.translatable(Shinigami.REQ_KEY + "rank", Shinigami.roman(t.rank())));
        for (ShinigamiRanks.Req q : t.reqs()) {
            if (!q.ok(d)) out.add(q.label().append(" (" + Math.min(q.current().applyAsInt(d), q.amount()) + "/" + q.amount() + ")"));
        }
        return out;
    }

    // =====================================================================================
    //  Aprender
    // =====================================================================================
    /** Confere as técnicas que chegam sozinhas (login, feitos, rank). Só para Shinigami. */
    public static void check(ServerPlayer sp) {
        if (!Shinigami.isShinigami(sp)) return;
        MagicData d = MagicApi.get(sp);
        for (Technique t : all()) {
            if (t.how() == How.AUTO && !t.known(d) && t.ready(d)) grant(sp, t);
        }
    }

    /** Ensina a técnica (todos os glifos) com cerimônia. */
    public static void grant(ServerPlayer sp, Technique t) {
        for (String g : t.glyphs()) MagicApi.learnGlyph(sp, IsekaiMahou.id(g));
        if (t.legend() != null) {
            ItemStack scroll = Legends.scroll(t.legend());
            if (!scroll.isEmpty()) Rewards.give(sp, scroll);
        }
        Glyph glyph = t.glyph();
        MutableComponent name = glyph == null ? Component.literal(t.id()) : glyph.displayName();
        ServerLevel sl = sp.serverLevel();
        Vec3 at = sp.position();
        Announce.send(sp, Shinigami.msg("tech_announce", sp.getDisplayName(), name), t.rgb(), Announce.STYLE_SYSTEM);
        Rewards.toast(sp, Shinigami.msg("tech_toast"), name.copy().withColor(t.rgb()), new ItemStack(ShinigamiRegistries.ZANPAKUTO.get()), t.rgb());
        sp.sendSystemMessage(Shinigami.msg("tech_learned", name.copy().withColor(t.rgb())).withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(Component.translatable(TECH_TIP + t.id()).withStyle(ChatFormatting.GRAY));
        ShinigamiFx.pillar(sl, at, 7.0, 100, ShinigamiFx.dust(t.rgb(), 1.5F));
        ShinigamiFx.ring(sl, at.add(0, 0.1, 0), 2.4, 32, 0F, ShinigamiFx.dust(t.rgb(), 1.3F));
        ShinigamiFx.sound(sl, at, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.7F);
        ShinigamiFx.sound(sl, at, SoundEvents.BELL_RESONATE, 1.0F, 0.5F);
    }

    /** A última lição (Mugetsu), chamada pelo espírito. */
    public static void teachMugetsu(ServerPlayer sp) {
        Technique t = byId("mugetsu");
        MagicData d = MagicApi.get(sp);
        if (t == null || t.known(d) || !t.ready(d)) return;
        grant(sp, t);
    }

    /** O Vazio Interior foi vencido: Máscara + Cero. */
    public static void tameHollow(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        d.setFlag(Shinigami.HOLLOW_TAMED, true);
        d.markDirty();
        Technique t = byId("visored");
        if (t != null && !t.known(d)) grant(sp, t);
        check(sp);
    }

    // =====================================================================================
    //  Sem poder espiritual (depois do Mugetsu)
    // =====================================================================================
    public static int powerless(MagicData d) {
        return Math.max(0, d.counter(Shinigami.POWERLESS));
    }

    /** Começa (ou renova) o tempo sem poder. */
    public static void startPowerless(ServerPlayer sp, int seconds) {
        MagicData d = MagicApi.get(sp);
        int cur = powerless(d);
        if (seconds > cur) MagicApi.addCounter(sp, Shinigami.POWERLESS, seconds - d.counter(Shinigami.POWERLESS));
        Release.seal(sp, false);
        HollowForms.cancel(sp);
        d.setMana(0F);
        applyRegen(sp, true);
        sp.sendSystemMessage(Shinigami.msg("powerless_start", seconds / 60).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Recusa uma técnica/liberação enquanto sem poder. true = bloqueado (já avisou). */
    public static boolean blockIfPowerless(ServerPlayer sp) {
        int s = powerless(MagicApi.get(sp));
        if (s <= 0 || sp.getAbilities().instabuild) return false;
        sp.displayClientMessage(Shinigami.msg("powerless_block", String.format("%d:%02d", s / 60, s % 60)).withStyle(ChatFormatting.DARK_GRAY), true);
        sp.playNotifySound(SoundEvents.SOUL_ESCAPE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 0.5F);
        return true;
    }

    /** Uma vez por segundo (tick do jogador): desconta o tempo sem poder. */
    static void secondTick(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        int s = powerless(d);
        if (s <= 0) {
            if (d.counter(Shinigami.POWERLESS) < 0) MagicApi.addCounter(sp, Shinigami.POWERLESS, -d.counter(Shinigami.POWERLESS));
            applyRegen(sp, false);
            return;
        }
        applyRegen(sp, true);
        int left = MagicApi.addCounter(sp, Shinigami.POWERLESS, -1);
        if (sp.tickCount % 60 == 0) {
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, false, true));
            sp.serverLevel().sendParticles(ShinigamiFx.BLACK, sp.getX(), sp.getY() + 1.0, sp.getZ(), 4, 0.3, 0.5, 0.3, 0.01);
        }
        if (left <= 0) {
            applyRegen(sp, false);
            sp.sendSystemMessage(Shinigami.msg("powerless_end").withColor(Shinigami.REIATSU_RGB));
            ShinigamiFx.pillar(sp.serverLevel(), sp.position(), 4.0, 50, ShinigamiFx.SPIRIT);
            ShinigamiFx.sound(sp.serverLevel(), sp.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F);
        }
    }

    /** Vigor quase parado (×0,2) enquanto sem poder. */
    private static void applyRegen(ServerPlayer sp, boolean on) {
        AttributeInstance inst = sp.getAttribute(CoreRegistries.MANA_REGEN);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(POWERLESS_REGEN);
        if (!on) {
            if (cur != null) inst.removeModifier(POWERLESS_REGEN);
            return;
        }
        if (cur == null) inst.addTransientModifier(new AttributeModifier(POWERLESS_REGEN, -0.8, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private ShinigamiTechniques() {}
}
