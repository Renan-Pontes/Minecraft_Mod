package com.renanpontes.isekaimahou.feature.paths;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Regras dos Caminhos (código comum, sem efeitos): requisitos de cada nó (Provações), feitos-bônus por
 * elemento, glifos que cada nó libera, caminho Arcano por NÍVEL, elemento de afinidade da classe e a magia
 * pronta que ele ganha.
 */
public final class PathRules {

    /** Guardião de cada elemento base (chefe da Provação V; contador "boss.<id>"). */
    public static final Map<Element, String> GUARDIANS = new EnumMap<>(Element.class);

    static {
        GUARDIANS.put(Element.FIRE, "ignis");
        GUARDIANS.put(Element.WATER, "tide_guardian");
        GUARDIANS.put(Element.EARTH, "primordial_golem");
        GUARDIANS.put(Element.WIND, "zephyr");
        GUARDIANS.put(Element.LIGHT, "seraph");
        GUARDIANS.put(Element.SHADOW, "abyss_sovereign");
    }

    /** Glifos dados por nós especiais (nunca pelos tiers nem pelo caminho Arcano). */
    public static final ResourceLocation CATACLYSM_GLYPH = IsekaiMahou.id("cataclysm");
    public static final ResourceLocation DOMAIN_GLYPH = IsekaiMahou.id("domain_expansion");

    /** Tiers do caminho Arcano (liberados nos níveis {@link Progression#arcaneUnlockLevel}). */
    public static final int ARCANE_TIERS = 5;

    /** Contadores dos feitos (este módulo mantém os feat.* e visit.*). */
    public static final String RITUAL_RANK = "ritual_rank";
    public static final String VISIT_NETHER = "visit.minecraft.the_nether";

    /** Flag "path_fluidity.<elemento>.<gatilho>": qual gatilho o nó Fluidez daquele elemento ensinou. */
    public static final String FLUIDITY_FLAG = "path_fluidity.";
    /** Flag "paths.feat.<elemento>.<1..3>": bônus do feito já entregue. */
    public static final String FEAT_FLAG = "paths.feat.";
    /** Flag: o elemento de afinidade da classe já foi despertado/preparado (escolha de classe ou migração). */
    public static final String START_FLAG = "paths.affinity_start";

    /** Feitos por elemento (antigos requisitos de Aprendiz/Adepto/Mestre, agora só bônus). */
    public static final int FEAT_STAGES = 3;
    private static final int[] FEAT_BONUS = {120, 300, 600};

    // =====================================================================================
    //  Requisitos dos nós (v0.7: Provações)
    // =====================================================================================

    /**
     * Requisitos do nó: o Despertar pede o elemento despertado; os outros, a Provação do rank do nó
     * (Aprendiz = I ... Arquimago = VII). Vale igual para elementos base e fusões.
     */
    public static List<PathReq> requirements(Element e, PathNode node) {
        int rank = node.trialRank();
        if (rank <= 0) return List.of(new PathReq.Awakened(e));
        return List.of(new PathReq.Trial(e, rank));
    }

    public static boolean allMet(Element e, PathNode node, MagicData d, @Nullable Player player) {
        for (PathReq r : requirements(e, node)) {
            if (!r.met(d, player)) return false;
        }
        return true;
    }

    /**
     * O que a Provação do rank pede para ENTRAR (§3: nível do elemento, Círculo e o rank anterior) — só para
     * exibir ✔/✘ com números; a regra oficial é {@code TrialsApi.meetsRequirements}.
     */
    public static List<PathReq> trialEntry(Element e, int rank) {
        List<PathReq> list = new ArrayList<>();
        list.add(new PathReq.ElementLevel(e, Progression.trialElementLevel(rank)));
        list.add(new PathReq.Circle(Progression.trialCircle(rank)));
        if (rank > 1) list.add(new PathReq.Trial(e, rank - 1));
        return list;
    }

    /** Ondas da arena por rank (§4: I–II 3, III–IV 4, V–VII 5). */
    public static int trialWaves(int rank) {
        return rank <= 2 ? 3 : rank <= 4 ? 4 : 5;
    }

    /** Quem espera no fim da Provação (§4), para a aba mostrar. */
    public static MutableComponent trialBoss(Element e, int rank) {
        Component avatar = Component.translatable("paths.isekai_mahou.trial_boss.avatar", e.displayName());
        Component guardian = e.isBase() ? guardianName(e) : null;
        if (rank == 5 && guardian != null) return Component.translatable("paths.isekai_mahou.trial_boss.guardian", guardian);
        if (rank == 6) return Component.translatable("paths.isekai_mahou.trial_boss.sentinels", avatar);
        if (rank == 7) {
            return guardian != null ? Component.translatable("paths.isekai_mahou.trial_boss.final", guardian, avatar)
                    : Component.translatable("paths.isekai_mahou.trial_boss.final_fusion", avatar);
        }
        return avatar.copy();
    }

    /** Requisitos da Convergência (só exibição: quem desperta a fusão é o módulo de dimensões). */
    public static List<PathReq> convergence(Element fusion) {
        List<PathReq> list = new ArrayList<>();
        if (!fusion.isFusion()) return list;
        list.add(new PathReq.Node(fusion.parentA, PathNode.MASTER));
        list.add(new PathReq.Node(fusion.parentB, PathNode.MASTER));
        if (fusion == Element.TWILIGHT) {
            list.add(new PathReq.Boss("abyss_sovereign", guardianFallbackKey(Element.SHADOW)));
            list.add(new PathReq.Boss("seraph", guardianFallbackKey(Element.LIGHT)));
        }
        return list;
    }

    // =====================================================================================
    //  Feitos (bônus de XP do elemento)
    // =====================================================================================

    /** Quantidade pedida num feito, ajustada pela afinidade da classe (afinidade ×0,5, difícil ×2). */
    public static int feat(int base, MagicPath path, Element e) {
        return Math.max(1, (int) Math.ceil(base * path.affinity(e).featMult));
    }

    /**
     * Requisitos do feito {@code stage} (1..3) do elemento — os mesmos limites que a v0.6 pedia para
     * Aprendiz, Adepto e Mestre. Cumprir todos dá {@link #featBonus} de XP do elemento uma única vez.
     */
    public static List<PathReq> feats(Element e, int stage, MagicPath path) {
        List<PathReq> list = new ArrayList<>();
        Component name = e.displayName();
        if (e.isFusion()) {
            switch (stage) {
                case 1 -> list.add(new PathReq.Counter("cast." + e.id, feat(30, path, e), "paths.isekai_mahou.req.cast", name));
                case 2 -> list.add(new PathReq.Counter("kill.spell." + e.id, feat(50, path, e), "paths.isekai_mahou.req.kill_spell", name));
                default -> list.add(new PathReq.Counter("kill.spell." + e.id, feat(200, path, e), "paths.isekai_mahou.req.kill_spell", name));
            }
            return list;
        }
        if (stage == 1) {
            list.add(new PathReq.Counter("kill.spell." + e.id, feat(20, path, e), "paths.isekai_mahou.req.kill_spell", name));
            return list;
        }
        if (stage == 3) {
            list.add(new PathReq.Counter("kill.spell." + e.id, feat(150, path, e), "paths.isekai_mahou.req.kill_spell", name));
            return list;
        }
        // Feito 2: algo "do mundo" ligado ao elemento. Visitar/alcançar não escala (é um marco único).
        switch (e) {
            case FIRE -> {
                list.add(new PathReq.Counter(VISIT_NETHER, 1, "paths.isekai_mahou.req.visit_nether"));
                list.add(new PathReq.Counter("kill.minecraft.blaze", feat(5, path, e), "paths.isekai_mahou.req.kill_blaze"));
            }
            case WATER -> list.add(new PathReq.Counter(FeatTracker.UNDERWATER, feat(6000, path, e),
                    "paths.isekai_mahou.req.underwater", List.of(), 20));
            case EARTH -> list.add(new PathReq.Counter(FeatTracker.DEEP_BLOCKS, feat(200, path, e), "paths.isekai_mahou.req.deep_blocks"));
            case WIND -> {
                list.add(new PathReq.Counter(FeatTracker.HIGH_ALTITUDE, 1, "paths.isekai_mahou.req.high_altitude"));
                list.add(new PathReq.Counter(FeatTracker.FALL_SAVED, feat(10, path, e), "paths.isekai_mahou.req.fall_saved"));
            }
            case LIGHT -> list.add(new PathReq.Counter(FeatTracker.UNDEAD_KILLS, feat(50, path, e), "paths.isekai_mahou.req.undead_kills"));
            case SHADOW -> list.add(new PathReq.Counter(FeatTracker.NIGHT_KILLS, feat(50, path, e), "paths.isekai_mahou.req.night_kills"));
            default -> {}
        }
        return list;
    }

    /** XP do elemento (antes da afinidade) entregue pelo feito 1..3. */
    public static int featBonus(int stage) {
        return FEAT_BONUS[Math.max(1, Math.min(FEAT_STAGES, stage)) - 1];
    }

    public static String featFlag(Element e, int stage) {
        return FEAT_FLAG + e.id + "." + stage;
    }

    public static boolean featMet(Element e, int stage, MagicData d, @Nullable Player player) {
        List<PathReq> reqs = feats(e, stage, d.path());
        if (reqs.isEmpty()) return false;
        for (PathReq r : reqs) if (!r.met(d, player)) return false;
        return true;
    }

    // =====================================================================================
    //  Nomes de chefes
    // =====================================================================================

    /** Nome reserva do Guardião (se o módulo de chefes não registrou a entidade). */
    public static String guardianFallbackKey(Element e) {
        return GUARDIAN_KEY_PREFIX + e.id;
    }

    private static final String GUARDIAN_KEY_PREFIX = "paths.isekai_mahou.guardian.";

    public static Component guardianName(Element e) {
        String boss = GUARDIANS.get(e);
        return boss == null ? e.displayName() : entityName(boss, guardianFallbackKey(e));
    }

    /** Nome da entidade registrada (módulo de chefes) ou o nome reserva deste módulo. */
    public static Component entityName(String path, String fallbackKey) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id(path))
                .<Component>map(t -> t.getDescription())
                .orElseGet(() -> Component.translatable(fallbackKey));
    }

    // =====================================================================================
    //  Afinidade da classe
    // =====================================================================================

    /**
     * 1º elemento de afinidade de cada classe: despertado automaticamente ao escolher a classe.
     * v0.9: vem do núcleo ({@link MagicPath#primaryAffinity()}), que cobre as 15 classes (as 5 antigas iguais).
     */
    @Nullable
    public static Element primaryAffinity(MagicPath path) {
        return path == null ? null : path.primaryAffinity();
    }

    /** Elementos base de afinidade da classe: o primário primeiro, depois os outros na ordem do enum. */
    public static List<Element> affinityElements(MagicPath path) {
        List<Element> list = new ArrayList<>();
        Element primary = primaryAffinity(path);
        if (primary != null) list.add(primary);
        for (Element e : Element.BASE) {
            if (e != primary && path.affinity(e) == MagicPath.Affinity.AFFINITY) list.add(e);
        }
        return list;
    }

    /**
     * Rank de ritual necessário para despertar o elemento por afinidade: 0 para o primário (já vem com a
     * classe), 1, 2, 3... para os seguintes; -1 = não é elemento de afinidade.
     */
    public static int affinityRank(MagicPath path, Element e) {
        return affinityElements(path).indexOf(e);
    }

    public static boolean canAwakenByAffinity(MagicData d, Element e) {
        int rank = affinityRank(d.path(), e);
        return rank >= 0 && !d.isAwakened(e) && d.counter(RITUAL_RANK) >= rank;
    }

    /** Magia pronta do elemento de afinidade (slot Z): projétil + o efeito tier 1 que mais combina. */
    private record Starter(String effect, String namePt, String nameEn) {}

    private static final Map<Element, Starter> STARTERS = new EnumMap<>(Element.class);

    static {
        STARTERS.put(Element.FIRE, new Starter("ignite", "Bola de Fogo", "Fireball"));
        STARTERS.put(Element.WATER, new Starter("tide", "Maré", "Tide"));
        STARTERS.put(Element.EARTH, new Starter("rock", "Pedrada", "Stone Throw"));
        STARTERS.put(Element.SHADOW, new Starter("life_drain", "Drenar", "Drain"));
    }

    /**
     * Receita pronta do elemento (null se o elemento não tem uma ou o glifo não foi registrado). O nome é
     * texto da receita (o jogador pode renomear), escolhido pelo idioma do cliente.
     */
    @Nullable
    public static SpellRecipe starterSpell(Element e, String language) {
        Starter s = STARTERS.get(e);
        if (s == null) return null;
        ResourceLocation form = IsekaiMahou.id("projectile"), effect = IsekaiMahou.id(s.effect());
        if (GlyphRegistry.get(form) == null || GlyphRegistry.get(effect) == null) return null;
        String name = language != null && language.startsWith("pt") ? s.namePt() : s.nameEn();
        return new SpellRecipe(List.of(form, effect), name, e.color, "");
    }

    /** Ícone do elemento para toasts/rastreador: a Essência dele (fusões: a do pai A). */
    public static ItemStack elementIcon(Element e) {
        Element base = e.isFusion() ? e.parentA : e;
        var essence = CoreRegistries.ESSENCES.get(base);
        return essence != null ? new ItemStack(essence.get()) : new ItemStack(Items.AMETHYST_SHARD);
    }

    // =====================================================================================
    //  Glifos
    // =====================================================================================

    /** Gatilhos de movimento (trigger_sneak...) vêm só da Fluidez. */
    public static boolean isMoveTrigger(Glyph g) {
        return g instanceof TriggerGlyph t && !t.isPlaceable();
    }

    /** Glifos de nós especiais: ultimate de Cataclismo e Expansão de Domínio (gerais ou "<nó>_<elemento>"). */
    public static boolean isSpecial(Glyph g) {
        if (!g.id().getNamespace().equals(IsekaiMahou.MODID)) return false;
        String p = g.id().getPath();
        if (p.equals(CATACLYSM_GLYPH.getPath()) || p.equals(DOMAIN_GLYPH.getPath())) return true;
        for (Element e : Element.values()) {
            if (p.equals("cataclysm_" + e.id) || p.equals("domain_" + e.id)) return true;
        }
        return false;
    }

    /** Glifos de um tier do elemento (sem leis, gatilhos de movimento e glifos especiais). */
    public static List<Glyph> tierGlyphs(Element e, int tier) {
        List<Glyph> list = new ArrayList<>();
        for (Glyph g : GlyphRegistry.byElement(e)) {
            if (g.tier() == tier && g.kind() != GlyphKind.LAW && !isMoveTrigger(g) && !isSpecial(g)) list.add(g);
        }
        return list;
    }

    /** Leis de Domínio (todas, ordenadas pelo Círculo mínimo). */
    public static List<Glyph> laws() {
        List<Glyph> list = new ArrayList<>(GlyphRegistry.byKind(GlyphKind.LAW));
        list.sort((a, b) -> Integer.compare(a.minCircle(), b.minCircle()));
        return list;
    }

    /** Tier Arcano do glifo (1..5). */
    public static int arcaneTier(Glyph g) {
        return Math.max(1, Math.min(ARCANE_TIERS, g.tier()));
    }

    /** Glifos neutros (Arcano, sem Origem) de um tier do caminho Arcano. */
    public static List<Glyph> arcaneGlyphs(int tier) {
        List<Glyph> list = new ArrayList<>();
        for (Glyph g : GlyphRegistry.byElement(Element.ARCANE)) {
            if (g.kind() == GlyphKind.LAW || isMoveTrigger(g) || isSpecial(g)) continue;
            if (arcaneTier(g) == tier) list.add(g);
        }
        return list;
    }

    /**
     * O glifo Arcano já pode ser ensinado: classe escolhida, nível do jogador ≥ nível do tier
     * ({@link Progression#arcaneUnlockLevel}) e Círculo ≥ Círculo mínimo do glifo.
     */
    public static boolean arcaneReady(Glyph g, MagicData d) {
        return d.path() != MagicPath.NONE && d.level() >= Progression.arcaneUnlockLevel(arcaneTier(g))
                && g.minCircle() <= d.circle();
    }

    /** Ordem dos gatilhos de movimento liberados pela Fluidez. */
    public static List<ResourceLocation> moveTriggers() {
        List<ResourceLocation> list = new ArrayList<>();
        for (MoveTrigger t : MoveTrigger.values()) {
            if (t.glyphId() != null && GlyphRegistry.get(t.glyphId()) != null) list.add(t.glyphId());
        }
        return list;
    }

    /** Próximo gatilho de movimento ainda não aprendido (null = já sabe todos). */
    @Nullable
    public static ResourceLocation nextTrigger(MagicData d) {
        for (ResourceLocation id : moveTriggers()) {
            if (!d.knowsGlyph(id)) return id;
        }
        return null;
    }

    /** Gatilho que o nó Fluidez do elemento ensinou (salvo numa flag), ou null. */
    @Nullable
    public static ResourceLocation fluidityTrigger(MagicData d, Element e) {
        for (ResourceLocation id : moveTriggers()) {
            if (d.hasFlag(FLUIDITY_FLAG + e.id + "." + id.getPath())) return id;
        }
        return null;
    }

    /** Glifos opcionais "cataclysm_<el>" / "domain_<el>", se algum módulo registrou. */
    @Nullable
    public static Glyph elementSpecial(String prefix, Element e) {
        return GlyphRegistry.get(IsekaiMahou.id(prefix + "_" + e.id));
    }

    /**
     * Glifos ligados a um nó (para exibir e para ensinar). Domínio lista todas as leis (as de Círculo
     * maior ficam para depois — ver {@link #teachableNow}).
     */
    public static List<Glyph> nodeGlyphs(Element e, PathNode node, MagicData d) {
        List<Glyph> list = new ArrayList<>();
        if (node.glyphTier > 0) return tierGlyphs(e, node.glyphTier);
        switch (node) {
            case FLUIDITY -> {
                ResourceLocation id = d.hasNode(e, node.id) ? fluidityTrigger(d, e) : nextTrigger(d);
                Glyph g = id == null ? null : GlyphRegistry.get(id);
                if (g != null) list.add(g);
            }
            case CATACLYSM -> {
                addIfPresent(list, GlyphRegistry.get(CATACLYSM_GLYPH));
                addIfPresent(list, elementSpecial("cataclysm", e));
            }
            case DOMAIN -> {
                addIfPresent(list, GlyphRegistry.get(DOMAIN_GLYPH));
                addIfPresent(list, elementSpecial("domain", e));
                list.addAll(laws());
            }
            default -> {}
        }
        return list;
    }

    /** O glifo já pode ser ensinado (leis respeitam o Círculo mínimo; o resto, sempre). */
    public static boolean teachableNow(Glyph g, MagicData d) {
        return g.kind() != GlyphKind.LAW || g.minCircle() <= d.circle();
    }

    private static void addIfPresent(List<Glyph> list, @Nullable Glyph g) {
        if (g != null && !list.contains(g)) list.add(g);
    }

    /** Elementos que têm caminho: os 6 base e as 15 fusões, na ordem do enum. */
    public static List<Element> pathElements() {
        List<Element> list = new ArrayList<>(Element.BASE);
        list.addAll(Element.FUSIONS);
        return list;
    }

    private PathRules() {}
}
