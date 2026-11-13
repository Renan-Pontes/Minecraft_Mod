package com.renanpontes.isekaimahou.feature.familiars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
import com.renanpontes.isekaimahou.feature.character.Race;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;

/**
 * "Tipo patrono, que depende de como você é": calcula o quanto cada {@link FamiliarForm} combina com o jogador AGORA.
 * <pre>
 *   pontos = 35 × classe + 15 × raça + 30 × fatia das conjurações no elemento + 40 × feitos (0..1) + desempate
 * </pre>
 * Os feitos vêm dos contadores do {@code MagicData} e das estatísticas do próprio Minecraft (pescar, nadar, minerar,
 * mortes...), cada um saturando ({@code n / (n + metade)}) para ninguém "farmar" uma forma com um número gigante.
 * Só roda de vez em quando (despertar, incenso e a cada poucos minutos) — nunca por tick.
 */
public final class FamiliarShaping {
    private static final float W_CLASS = 35F, W_RACE = 15F, W_ELEMENT = 30F, W_FEATS = 40F;
    /** Conjurações mínimas para o elemento contar (com meia dúzia de magias ainda não dá para dizer nada). */
    private static final int MIN_CASTS = 20;
    private static final String[] UNDEAD = {"zombie", "skeleton", "husk", "stray", "drowned", "wither_skeleton",
            "zombie_villager", "phantom", "zombified_piglin", "bogged"};

    /** Um feito do jogador. {@code half} = valor em que o feito vale metade do máximo. */
    public enum Feat {
        KILLS(500), BOSSES(10), MINING(350), HARVEST(300), BREED(40), BREW(40), STUDY(40), MEDITATE(40), FISH(60),
        SWIM(2500), FLIGHT(4000), EXPLORE(60000), DEATHS(15), UNDEAD(150), CHESTS(250), TRADES(60), SNEAK(1500),
        TANK(2500), ENCHANT(20), GLYPHS(40);

        public final String id = name().toLowerCase(java.util.Locale.ROOT);
        final float half;

        Feat(float half) {
            this.half = half;
        }

        float saturate(float raw) {
            return raw <= 0 ? 0 : raw / (raw + half);
        }
    }

    /** Um motivo da forma (para a aba): tipo ({@code class/race/element/feat}), argumento (id) e peso em pontos. */
    public record Reason(String kind, String arg, float points) {}

    /** Resultado: pontos por forma e os motivos da forma vencedora. */
    public record Result(EnumMap<FamiliarForm, Float> scores, FamiliarForm best) {
        public float score(FamiliarForm f) {
            return scores.getOrDefault(f, 0F);
        }
    }

    private FamiliarShaping() {}

    public static Result evaluate(ServerPlayer sp) {
        Snapshot s = new Snapshot(sp);
        EnumMap<FamiliarForm, Float> scores = new EnumMap<>(FamiliarForm.class);
        FamiliarForm best = FamiliarForm.RUNE_WISP;
        float top = -1;
        for (FamiliarForm f : FamiliarForm.ALL) {
            float v = s.classPoints(f) + s.racePoints(f) + s.elementPoints(f) + s.featPoints(f) + s.jitter(f);
            scores.put(f, v);
            if (v > top) {
                top = v;
                best = f;
            }
        }
        return new Result(scores, best);
    }

    /** Os (até 3) maiores motivos de o jogador combinar com a forma {@code f}. */
    public static List<Reason> reasons(ServerPlayer sp, FamiliarForm f) {
        Snapshot s = new Snapshot(sp);
        List<Reason> out = new ArrayList<>();
        if (s.classPoints(f) > 0) out.add(new Reason("class", s.path.id, s.classPoints(f)));
        if (s.racePoints(f) > 0) out.add(new Reason("race", s.race, s.racePoints(f)));
        float e1 = W_ELEMENT * s.share(f.element), e2 = f.element2 == null ? 0 : W_ELEMENT * 0.5F * s.share(f.element2);
        if (e1 >= 3F && e1 >= e2) out.add(new Reason("element", f.element.id, e1 + e2));
        else if (e2 >= 3F) out.add(new Reason("element", f.element2.id, e1 + e2));
        for (Map.Entry<Feat, Float> en : f.feats.entrySet()) {
            float v = W_FEATS * en.getValue() * s.feat(en.getKey());
            if (v >= 2F) out.add(new Reason("feat", en.getKey().id, v));
        }
        out.sort(Comparator.comparingDouble((Reason r) -> -r.points()));
        return out.size() > 3 ? new ArrayList<>(out.subList(0, 3)) : out;
    }

    /** Foto do "jeito de jogar" (lida uma vez por avaliação). */
    private static final class Snapshot {
        final ServerPlayer sp;
        final MagicData d;
        final MagicPath path;
        final String race;
        final EnumMap<Element, Float> casts = new EnumMap<>(Element.class);
        float totalCasts;
        final EnumMap<Feat, Float> feats = new EnumMap<>(Feat.class);

        Snapshot(ServerPlayer sp) {
            this.sp = sp;
            this.d = MagicApi.get(sp);
            this.path = d.path();
            this.race = raceId(sp);
            for (Element e : Element.values()) {
                float n = d.counter("cast." + e.id) + 2F * d.counter("kill.spell." + e.id);
                if (n <= 0) continue;
                totalCasts += n;
                // Fusões contam metade para cada elemento-pai (Vapor é fogo E água)
                if (e.isFusion()) {
                    casts.merge(e.parentA, n * 0.5F, Float::sum);
                    casts.merge(e.parentB, n * 0.5F, Float::sum);
                } else {
                    casts.merge(e, n, Float::sum);
                }
            }
        }

        float classPoints(FamiliarForm f) {
            return f.classes.contains(path) ? W_CLASS : 0;
        }

        float racePoints(FamiliarForm f) {
            return !race.isEmpty() && f.races.contains(race) ? W_RACE : 0;
        }

        float share(Element e) {
            if (e == null || totalCasts < MIN_CASTS) return 0;
            return casts.getOrDefault(e, 0F) / totalCasts;
        }

        float elementPoints(FamiliarForm f) {
            return W_ELEMENT * Math.min(1F, share(f.element) + 0.5F * share(f.element2));
        }

        float featPoints(FamiliarForm f) {
            float sum = 0;
            for (Map.Entry<Feat, Float> en : f.feats.entrySet()) sum += en.getValue() * feat(en.getKey());
            return W_FEATS * sum;
        }

        /** Desempate estável por jogador (dois jogadores "iguais" não precisam ter o mesmo familiar). */
        float jitter(FamiliarForm f) {
            long h = sp.getUUID().getLeastSignificantBits() * 31L + f.id.hashCode() * 0x9E3779B97F4A7C15L;
            h ^= h >>> 29;
            return (Math.floorMod(h, 1000L) / 1000F) * 3F;
        }

        float feat(Feat f) {
            return feats.computeIfAbsent(f, k -> k.saturate(raw(k)));
        }

        private float raw(Feat f) {
            return switch (f) {
                case KILLS -> d.counter("kill.total") + stat(Stats.MOB_KILLS) * 0.25F;
                case BOSSES -> prefixSum("boss.");
                case MINING -> oresMined();
                case HARVEST -> d.counter("harvest");
                case BREED -> d.counter("breed") + stat(Stats.ANIMALS_BRED);
                case BREW -> d.counter("brew");
                case STUDY -> d.counter("study");
                case MEDITATE -> d.counter("meditate");
                case FISH -> stat(Stats.FISH_CAUGHT);
                case SWIM -> (stat(Stats.SWIM_ONE_CM) + stat(Stats.WALK_UNDER_WATER_ONE_CM) + stat(Stats.BOAT_ONE_CM) * 0.25F) / 100F;
                case FLIGHT -> (stat(Stats.AVIATE_ONE_CM) + stat(Stats.FLY_ONE_CM)) / 100F;
                case EXPLORE -> (stat(Stats.WALK_ONE_CM) + stat(Stats.SPRINT_ONE_CM) + stat(Stats.HORSE_ONE_CM)) / 100F;
                case DEATHS -> stat(Stats.DEATHS);
                case UNDEAD -> undeadKills();
                case CHESTS -> stat(Stats.OPEN_CHEST) + stat(Stats.OPEN_BARREL) * 0.5F;
                case TRADES -> stat(Stats.TRADED_WITH_VILLAGER);
                case SNEAK -> stat(Stats.CROUCH_ONE_CM) / 100F;
                case TANK -> (stat(Stats.DAMAGE_TAKEN) + stat(Stats.DAMAGE_BLOCKED_BY_SHIELD)) / 10F;
                case ENCHANT -> stat(Stats.ENCHANT_ITEM);
                case GLYPHS -> d.glyphs().size();
            };
        }

        private float stat(ResourceLocation custom) {
            return sp.getStats().getValue(Stats.CUSTOM.get(custom));
        }

        private float prefixSum(String prefix) {
            float n = 0;
            for (Map.Entry<String, Integer> en : d.counters().entrySet()) {
                if (en.getKey().startsWith(prefix)) n += en.getValue();
            }
            return n;
        }

        private float undeadKills() {
            float n = 0;
            for (String id : UNDEAD) n += d.counter("kill.minecraft." + id);
            return n;
        }

        /** Minérios quebrados (qualquer bloco da tag de minérios) + um pouquinho de pedra/ardósia. */
        private float oresMined() {
            float n = 0;
            for (Holder<Block> h : BuiltInRegistries.BLOCK.getTagOrEmpty(Tags.Blocks.ORES)) {
                n += sp.getStats().getValue(Stats.BLOCK_MINED.get(h.value()));
            }
            n += (sp.getStats().getValue(Stats.BLOCK_MINED.get(Blocks.STONE))
                    + sp.getStats().getValue(Stats.BLOCK_MINED.get(Blocks.DEEPSLATE))) / 20F;
            return n;
        }
    }

    /** Id da raça em vigor ("" se ainda não escolheu ou se o módulo de raças não respondeu). */
    static String raceId(ServerPlayer sp) {
        try {
            Race r = CharacterContent.data(sp).activeRace();
            return r == null ? "" : r.id;
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }
}
