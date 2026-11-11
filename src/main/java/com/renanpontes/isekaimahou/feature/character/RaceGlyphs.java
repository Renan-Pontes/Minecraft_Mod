package com.renanpontes.isekaimahou.feature.character;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.character.glyph.AcidSplashEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.BloodFuryEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.DivineShieldEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.DragonBreathForm;
import com.renanpontes.isekaimahou.feature.character.glyph.DragonScalesEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.ForestStepEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.ForgeShoutEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.HellfireEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.HolyLightEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.JellyBodyEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.MoonArrowForm;
import com.renanpontes.isekaimahou.feature.character.glyph.PhaseShiftEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.PredatorLeapForm;
import com.renanpontes.isekaimahou.feature.character.glyph.ResolveEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.SavageCleaveEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.ShadowWingsEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.SpectralTouchEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.StoneSkinEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.VenomFangEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.WebShotEffect;
import com.renanpontes.isekaimahou.feature.character.glyph.WildInstinctEffect;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Skills raciais = glifos montáveis no Grimório (v1.0, §0: "raças continuam, com skills únicas").
 * <p>
 * <b>Marca de origem racial:</b> o núcleo só conhece origem de CLASSE ({@link Glyph#origin()} = {@link MagicPath}). Os
 * glifos raciais usam {@code origin(MagicPath.NONE)} — isso os tira de TODOS os sorteios (pergaminhos de loot, guilda,
 * mercadores, chefes, caminhos elementais filtram {@code origin() != null}), os deixa usáveis em magia de qualquer
 * elemento (regra de origem do parser) e impede Lições (ninguém tem a classe NONE). Qual raça é dona de cada glifo fica
 * aqui ({@link #raceOf}); este módulo ensina os glifos da raça do jogador por nível ({@link #FIRST_SKILL_LEVEL},
 * {@link #SECOND_SKILL_LEVEL}) e tira os de outra raça (renascer no Altar troca as skills).
 * <p>
 * <b>Regra do jogador:</b> skills de OUTRAS raças só se aprendem com a <b>Relíquia Racial</b> daquela raça
 * ({@link com.renanpontes.isekaimahou.feature.character.item.RaceRelicItem}, drops em {@link RelicDrops}). A skill
 * aprendida assim fica marcada no contador {@code race_relic.<glifo>} e nunca é esquecida (nem ao renascer).
 * <p>
 * O custo segue o núcleo (custo por cópia e {@code COST_SCALE}); o dano passa por {@code SpellContext.damage}
 * ({@code DAMAGE_SCALE}).
 */
public final class RaceGlyphs {
    public static final int FIRST_SKILL_LEVEL = 1;
    /** = nível de raça II ({@link RaceProgress#CHAR_LEVEL}). */
    public static final int SECOND_SKILL_LEVEL = 15;
    /** Marcador do aumento Adaptável (Humano) nos {@code SpellStats.custom}. */
    public static final ResourceLocation ADAPTIVE = IsekaiMahou.id("race_adaptive");

    /** Skill racial: glifo, raça dona, posição (0 = nasce com ela) e nível para despertar. */
    public record Skill(String path, Race race, int index, int level) {
        public ResourceLocation id() { return IsekaiMahou.id(path); }

        @Nullable
        public Glyph glyph() { return GlyphRegistry.get(id()); }
    }

    private static final Map<ResourceLocation, Skill> BY_ID = new LinkedHashMap<>();
    private static final List<Skill> ALL = new ArrayList<>();

    static {
        for (Race r : Race.ALL) {
            for (int i = 0; i < r.skills.size(); i++) {
                Skill s = new Skill(r.skills.get(i), r, i, i == 0 ? FIRST_SKILL_LEVEL : SECOND_SKILL_LEVEL);
                ALL.add(s);
                BY_ID.put(s.id(), s);
            }
        }
    }

    /** Registra os 22 glifos raciais (lado comum). */
    static void register() {
        // Humano
        GlyphRegistry.register(new ResolveEffect());
        AugmentGlyph adaptive = new AugmentGlyph("race_adaptive", 4, s -> s.custom.merge(ADAPTIVE, 1, Integer::sum));
        adaptive.origin(MagicPath.NONE);
        adaptive.tier(2);
        GlyphRegistry.register(adaptive);
        // Elfo
        GlyphRegistry.register(new MoonArrowForm());
        GlyphRegistry.register(new ForestStepEffect());
        // Anão
        GlyphRegistry.register(new StoneSkinEffect());
        GlyphRegistry.register(new ForgeShoutEffect());
        // Meio-Orc
        GlyphRegistry.register(new BloodFuryEffect());
        GlyphRegistry.register(new SavageCleaveEffect());
        // Demônio
        GlyphRegistry.register(new HellfireEffect());
        GlyphRegistry.register(new ShadowWingsEffect());
        // Dragonato
        GlyphRegistry.register(new DragonBreathForm());
        GlyphRegistry.register(new DragonScalesEffect());
        // Bestial
        GlyphRegistry.register(new PredatorLeapForm());
        GlyphRegistry.register(new WildInstinctEffect());
        // v1.0.1 — Aracnídeo, Fantasma, Anjo e Slime
        GlyphRegistry.register(new WebShotEffect());
        GlyphRegistry.register(new VenomFangEffect());
        GlyphRegistry.register(new SpectralTouchEffect());
        GlyphRegistry.register(new PhaseShiftEffect());
        GlyphRegistry.register(new HolyLightEffect());
        GlyphRegistry.register(new DivineShieldEffect());
        GlyphRegistry.register(new AcidSplashEffect());
        GlyphRegistry.register(new JellyBodyEffect());

        NeoForge.EVENT_BUS.addListener(RaceGlyphs::onGlyphLearned);
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.LevelUp e) -> reconcile(e.getPlayer(), true));
    }

    // =====================================================================================
    //  Consultas (lado comum)
    // =====================================================================================
    public static List<Skill> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static List<Skill> of(Race r) {
        List<Skill> out = new ArrayList<>(2);
        for (Skill s : ALL) if (s.race() == r) out.add(s);
        return out;
    }

    /** Raça dona do glifo (null = não é racial). */
    @Nullable
    public static Race raceOf(ResourceLocation glyph) {
        Skill s = BY_ID.get(glyph);
        return s == null ? null : s.race();
    }

    @Nullable
    public static Skill skill(ResourceLocation glyph) {
        return BY_ID.get(glyph);
    }

    public static boolean isRacial(Glyph g) {
        return g != null && BY_ID.containsKey(g.id());
    }

    // =====================================================================================
    //  Ensinar / esquecer (servidor)
    // =====================================================================================
    /**
     * Deixa as skills raciais do jogador iguais à raça e ao nível dele: ensina as que faltam (com aviso se
     * {@code announce}) e esquece as de outra raça (criativo guarda tudo, para testes).
     */
    public static void reconcile(ServerPlayer sp, boolean announce) {
        if (sp instanceof FakePlayer) return;
        CharacterData d = CharacterContent.data(sp);
        MagicData md = MagicApi.get(sp);
        Race race = d.activeRace();
        List<Component> forgotten = new ArrayList<>();
        for (Skill s : ALL) {
            Glyph g = s.glyph();
            if (g == null) continue;
            if (s.race() != race) {
                // Aprendida com uma Relíquia Racial: é do jogador para sempre (renascer não tira).
                if (learnedByRelic(md, s)) {
                    if (!md.knowsGlyph(s.id())) MagicApi.learnGlyphSilently(sp, s.id());
                    continue;
                }
                if (!sp.getAbilities().instabuild && md.knowsGlyph(s.id())) {
                    md.glyphs().remove(s.id());
                    md.dirty = true;
                    forgotten.add(g.displayName());
                }
                continue;
            }
            if (md.level() < s.level() || md.knowsGlyph(s.id())) continue;
            if (announce && !Onboarding.quiet(sp)) {
                MagicApi.learnGlyph(sp, s.id());
                awakenFx(sp, s, g);
            } else {
                MagicApi.learnGlyphSilently(sp, s.id());
            }
        }
        if (!forgotten.isEmpty() && announce) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < forgotten.size(); i++) {
                if (i > 0) list.append(", ");
                list.append(forgotten.get(i));
            }
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.skills_forgotten", list)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // =====================================================================================
    //  Relíquias Raciais: a ÚNICA forma de outra raça aprender estas skills
    // =====================================================================================
    /** Contador (MagicData) que marca a skill como aprendida por relíquia: {@code race_relic.<glifo>}. */
    public static String relicKey(Skill s) {
        return "race_relic." + s.path();
    }

    public static boolean learnedByRelic(MagicData md, Skill s) {
        return md.counter(relicKey(s)) > 0;
    }

    /** Próxima skill da raça {@code r} (na ordem de despertar) que o jogador ainda não conhece; null = já sabe todas. */
    @Nullable
    public static Skill nextUnknown(MagicData md, Race r) {
        for (Skill s : of(r)) {
            if (s.glyph() != null && !md.knowsGlyph(s.id())) return s;
        }
        return null;
    }

    /**
     * Ensina a skill por relíquia: marca o contador ANTES de aprender (assim {@link #onGlyphLearned} e
     * {@link #reconcile} não a tiram) e toca o aviso (toast, mensagem, partículas e som na cor da raça).
     */
    public static void teachByRelic(ServerPlayer sp, Skill s) {
        Glyph g = s.glyph();
        if (g == null) return;
        MagicApi.addCounter(sp, relicKey(s), 1);
        MagicApi.addCounter(sp, "race_relic.used", 1);
        MagicApi.learnGlyph(sp, s.id());
        int rgb = s.race().rgb;
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.character.relic"), g.displayName().withColor(rgb),
                new ItemStack(CharacterContent.relic(s.race()).get()), rgb);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.relic_learned", g.displayName().withColor(rgb),
                s.race().displayName(), Component.keybind("key.isekai_mahou.menu")).withColor(0xFFD86B));
        RaceFx.dustBurst(sp.serverLevel(), sp.position().add(0, 1.0, 0), rgb, 50, 0.7);
        RaceFx.spiral(sp.serverLevel(), sp, RaceFx.dust(rgb, 1.4F), 36, 0.9);
        sp.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1.2, sp.getZ(), 30, 0.4, 0.6, 0.4, 0.35);
        sp.serverLevel().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.4, sp.getZ(), 60, 0.7, 0.7, 0.7, 0.8);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.7F);
    }

    /** Aviso de skill racial despertada: toast, som e partículas na cor da raça. */
    private static void awakenFx(ServerPlayer sp, Skill s, Glyph g) {
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.character.skill"), g.displayName().withColor(s.race().rgb),
                new ItemStack(CharacterContent.ALTAR_ITEM.get()), s.race().rgb);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.skill_awakened", g.displayName()
                .withColor(s.race().rgb), Component.keybind("key.isekai_mahou.menu")).withColor(0xFFD86B));
        RaceFx.dustBurst(sp.serverLevel(), sp.position().add(0, 1.0, 0), s.race().rgb, 30, 0.5);
        sp.serverLevel().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.4, sp.getZ(), 40, 0.6, 0.6, 0.6, 0.6);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.5F);
    }

    /** Glifo racial de outra raça (ex.: pergaminho do criativo) não fica com quem não tem o sangue. */
    private static void onGlyphLearned(MagicProgressEvent.GlyphLearned e) {
        Race owner = raceOf(e.glyph);
        if (owner == null) return;
        ServerPlayer sp = e.getPlayer();
        if (sp.getAbilities().instabuild) return;
        CharacterData d = CharacterContent.data(sp);
        if (d.activeRace() == owner) return;
        MagicData md = MagicApi.get(sp);
        Skill skill = BY_ID.get(e.glyph);
        if (skill != null && learnedByRelic(md, skill)) return; // veio de uma Relíquia Racial
        md.glyphs().remove(e.glyph);
        md.dirty = true;
        Glyph g = GlyphRegistry.get(e.glyph);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.skill_wrong_race",
                g == null ? Component.literal(e.glyph.toString()) : g.displayName(), owner.displayName()).withColor(0xE07A7A), true);
    }

    private RaceGlyphs() {}
}
