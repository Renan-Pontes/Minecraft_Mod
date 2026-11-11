package com.renanpontes.isekaimahou.feature.class_gambler.luck;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbNet;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.glyph.GamblerGlyphs;
import com.renanpontes.isekaimahou.feature.class_gambler.outfit.GamblerOutfits;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseChallenge;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseRanks;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sistema "Sorte" do Apostador: TODA magia de um Apostador (e toda magia com um glifo de origem do Apostador, de
 * quem aprendeu por Lição) rola a sorte antes de sair — de −99% a +100% de poder ({@link LuckBand}). O resultado vira
 * o multiplicador de poder do contexto (dano, cura, escudos... de todos os estágios e cópias).
 * <ul>
 *   <li><b>Sorte</b> ({@link #luck}): empurra o sorteio para as faixas boas. Apostador +1 de base, +1 por rank da Casa,
 *   +1 com o traje completo, +1..3 com o efeito Sortudo, −2 com o efeito Azarado.</li>
 *   <li><b>Tensão</b> (só o Apostador, contador {@link #FEVER}): cada rolagem enche (o azar enche mais); em 100 o
 *   próximo sorteio é JACKPOT garantido. Também há a chance pura de Jackpot a cada rolagem.</li>
 *   <li><b>Dobrar a Aposta</b> (aumento): exagera o resultado (o bom fica melhor, o ruim, pior) e dobra a chance de
 *   Jackpot. <b>Dados Viciados</b>: tira as faixas extremas (sem azar total, sem sorte grande).</li>
 * </ul>
 */
public final class LuckService {
    /** Contador de Tensão (0..100) do Apostador. Guardado cru (sem evento) para não espalhar avisos a cada magia. */
    public static final String FEVER = "gambler.fever";
    public static final int FEVER_MAX = 100;
    public static final String ROLLS = "gambler.rolls";
    public static final String BIG_WIN = "gambler.big_win";
    public static final String LUCKY_KILL = "gambler.lucky_kill";

    /** Chance base de Jackpot por rolagem, e o extra por ponto de Sorte. */
    private static final float JACKPOT_BASE = 0.008F;
    private static final float JACKPOT_PER_LUCK = 0.003F;
    /** Ticks em que um abate por magia ainda conta como "abate de sorte" depois de uma rolagem boa. */
    private static final int LUCKY_KILL_WINDOW = 100;

    /** Tipo de visual da rolagem (o que o HUD desenha). */
    public enum Kind { CARDS, DICE, ROULETTE, SLOT, COIN, DOMAIN }

    /** Resultado de uma rolagem. */
    public record Roll(LuckBand band, float mult, boolean doubled) {
        public int percent() {
            return LuckBand.percent(mult);
        }
    }

    /** Opções de uma rolagem (aumentos presentes na magia). */
    public record Options(boolean doubleDown, boolean loaded) {
        public static final Options NONE = new Options(false, false);
    }

    private record LastRoll(LuckBand band, long tick) {}

    private static final Map<UUID, LastRoll> LAST = new HashMap<>();

    // =====================================================================================
    //  Sorte e Tensão
    // =====================================================================================

    public static boolean isGambler(LivingEntity e) {
        return e instanceof Player p && MagicApi.get(p).path() == MagicPath.GAMBLER;
    }

    /** Pontos de Sorte de quem rola (pode ser negativo). */
    public static int luck(LivingEntity e) {
        int l = 0;
        if (e instanceof Player p) {
            MagicData d = MagicApi.get(p);
            if (d.path() == MagicPath.GAMBLER) {
                l += 1 + HouseRanks.rank(d);
                if (GamblerOutfits.fullSet(p)) l += 1;
            }
        }
        MobEffectInstance lucky = e.getEffect(GbRegistries.LUCKY);
        if (lucky != null) l += 1 + Math.min(2, lucky.getAmplifier());
        if (e.hasEffect(GbRegistries.JINXED)) l -= 2;
        return l;
    }

    public static int fever(MagicData d) {
        return Math.max(0, Math.min(FEVER_MAX, d.counter(FEVER)));
    }

    public static void setFever(MagicData d, int value) {
        d.counters().put(FEVER, Math.max(0, Math.min(FEVER_MAX, value)));
        d.markDirty();
    }

    /** Soma Tensão (Apostador). Retorna o novo valor. */
    public static int addFever(ServerPlayer sp, int amount) {
        MagicData d = MagicApi.get(sp);
        int v = Math.min(FEVER_MAX, fever(d) + Math.max(0, amount));
        setFever(d, v);
        return v;
    }

    /** Peso de cada faixa para a Sorte dada (as faixas ruins perdem peso, as boas ganham). */
    public static float[] weights(int luck, boolean loaded) {
        float[] w = new float[LuckBand.VALUES.length];
        for (LuckBand b : LuckBand.VALUES) {
            float base = b.weight;
            if (loaded && (b == LuckBand.BUST || b == LuckBand.BAD || b == LuckBand.NATURAL)) base = 0;
            if (b.bad()) base *= Math.max(0.2F, 1F - 0.07F * luck);
            else if (b.good) base *= Math.max(0.3F, 1F + 0.08F * luck);
            w[b.ordinal()] = Math.max(0F, base);
        }
        return w;
    }

    /** Chance de Jackpot por rolagem (sem contar a Tensão cheia). */
    public static float jackpotChance(int luck, Options o) {
        float c = JACKPOT_BASE + JACKPOT_PER_LUCK * Math.max(0, luck);
        if (o.doubleDown()) c *= 2F;
        if (o.loaded()) c *= 0.5F;
        return Math.min(0.10F, c);
    }

    /** Valor esperado do multiplicador para a Sorte dada (a aba da Casa mostra). */
    public static float expected(int luck, Options o) {
        float[] w = weights(luck, o.loaded());
        float sum = 0, ev = 0;
        for (LuckBand b : LuckBand.VALUES) {
            sum += w[b.ordinal()];
            ev += w[b.ordinal()] * apply(b.mult, o);
        }
        float jp = jackpotChance(luck, o);
        float base = sum > 0 ? ev / sum : 1F;
        return base * (1F - jp) + apply(LuckBand.JACKPOT.mult, o) * jp;
    }

    /** Dobrar a Aposta: o lado bom dobra o ganho, o lado ruim corta pela metade. */
    public static float apply(float mult, Options o) {
        if (!o.doubleDown()) return mult;
        return mult >= 1F ? 1F + (mult - 1F) * 2F : Math.max(0.01F, mult * 0.5F);
    }

    // =====================================================================================
    //  Rolar
    // =====================================================================================

    /**
     * Rola a sorte para {@code who}. Não mexe em contadores nem mostra nada (ver {@link #rollAndShow}).
     * Jackpot ativo = sempre o máximo.
     */
    public static Roll roll(LivingEntity who, Options o, RandomSource rnd) {
        if (JackpotService.active(who)) return new Roll(LuckBand.JACKPOT, apply(LuckBand.JACKPOT.mult, o), o.doubleDown());
        int l = luck(who);
        boolean feverFull = who instanceof Player p && isGambler(p) && fever(MagicApi.get(p)) >= FEVER_MAX;
        if (feverFull || rnd.nextFloat() < jackpotChance(l, o)) {
            return new Roll(LuckBand.JACKPOT, apply(LuckBand.JACKPOT.mult, o), o.doubleDown());
        }
        float[] w = weights(l, o.loaded());
        float total = 0;
        for (float v : w) total += v;
        float pick = rnd.nextFloat() * total;
        LuckBand band = LuckBand.EVEN;
        for (LuckBand b : LuckBand.VALUES) {
            pick -= w[b.ordinal()];
            if (pick < 0 && w[b.ordinal()] > 0) {
                band = b;
                break;
            }
        }
        return new Roll(band, apply(band.mult, o), o.doubleDown());
    }

    /**
     * Rola, conta (rolagens, Tensão, grandes vitórias), mostra (HUD + partículas + som) e, se deu JACKPOT, liga a
     * sorte infinita. Usado na conjuração e pelas formas que rolam por conta própria (Cassino).
     */
    public static Roll rollAndShow(LivingEntity who, Options o, Kind kind, int jackpotTicksBonus) {
        Roll r = roll(who, o, who.getRandom());
        if (who instanceof ServerPlayer sp && !(sp instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            boolean wasActive = JackpotService.active(sp);
            boolean gambler = isGambler(sp);
            MagicApi.addCounter(sp, ROLLS, 1);
            if (r.band() == LuckBand.GREAT || r.band() == LuckBand.NATURAL || r.band() == LuckBand.JACKPOT) {
                MagicApi.addCounter(sp, BIG_WIN, 1);
            }
            LAST.put(sp.getUUID(), new LastRoll(r.band(), sp.serverLevel().getGameTime()));
            MagicData d = MagicApi.get(sp);
            int fever = fever(d);
            if (gambler && !wasActive) {
                if (r.band() == LuckBand.JACKPOT) {
                    fever = 0;
                } else {
                    float gain = r.band().fever * (o.doubleDown() ? 1.5F : 1F);
                    fever = Math.min(FEVER_MAX, fever + Math.round(gain));
                }
                setFever(d, fever);
            }
            PacketDistributor.sendToPlayer(sp, new GbNet.RollShown((byte) r.band().ordinal(), r.percent(), (byte) kind.ordinal(),
                    (byte) fever, gambler));
            GbFx.roll(sp.serverLevel(), sp, r);
            if (r.band() == LuckBand.JACKPOT && !wasActive) JackpotService.start(sp, jackpotTicksBonus);
        } else {
            GbFx.roll((net.minecraft.server.level.ServerLevel) who.level(), who, r);
        }
        return r;
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    /** Toda conjuração do Apostador (ou com glifo de origem dele) rola a sorte antes de sair. */
    public static void onCastPre(SpellCastEvent.Pre event) {
        if (event.isCanceled()) return;
        LivingEntity caster = event.getCaster();
        if (caster == null || caster.level().isClientSide) return;
        SpellContext ctx = event.getContext();
        ParsedSpell spell = ctx.spell();
        boolean gambler = isGambler(caster);
        if (!gambler && !hasGamblerGlyph(ctx.recipe())) return;
        // Os gatilhos "Ao Jackpot" saem durante o próprio Jackpot (que já é o máximo): rolam igual (sempre máximo)
        // O Cassino e a Morte Ociosa rolam pulso a pulso (rodada a rodada), não na conjuração
        if (spell.form() != null && (GamblerGlyphs.CASINO_DOMAIN_ID.equals(spell.form().id()) || GamblerGlyphs.IDLE_DEATH_ID.equals(spell.form().id()))) {
            return;
        }
        Options o = options(spell, caster);
        Roll r = rollAndShow(caster, o, kindOf(spell), 0);
        ctx.setPowerMult(ctx.powerMult() * r.mult());
    }

    /** Aumentos de sorte na magia (em qualquer estágio) + Dobrar automático na Rodada da Casa. */
    public static Options options(ParsedSpell spell, LivingEntity caster) {
        boolean dd = spell.anyStats(s -> s.custom(GamblerGlyphs.DOUBLE_DOWN_ID) > 0);
        boolean loaded = spell.anyStats(s -> s.custom(GamblerGlyphs.LOADED_DICE_ID) > 0);
        if (caster instanceof ServerPlayer sp && HouseChallenge.active(sp)) dd = true;
        return new Options(dd, loaded);
    }

    private static Kind kindOf(ParsedSpell spell) {
        if (spell.form() == null) return Kind.CARDS;
        ResourceLocation id = spell.form().id();
        if (GamblerGlyphs.DICE_ROLL_ID.equals(id)) return Kind.DICE;
        if (GamblerGlyphs.ROULETTE_ID.equals(id)) return Kind.ROULETTE;
        if (GamblerGlyphs.SLOT_MACHINE_ID.equals(id)) return Kind.SLOT;
        if (GamblerGlyphs.CARD_THROW_ID.equals(id)) return Kind.CARDS;
        for (ParsedSpell.EffectEntry e : spell.effects()) {
            if (GamblerGlyphs.FATE_COIN_ID.equals(e.effect().id())) return Kind.COIN;
        }
        // Outras magias: o visual muda a cada vez (cartas, dados ou roleta), pelo custo
        return Kind.values()[Math.floorMod(spell.baseCost(), 3)];
    }

    public static boolean hasGamblerGlyph(SpellRecipe recipe) {
        if (recipe == null) return false;
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && g.origin() == MagicPath.GAMBLER) return true;
        }
        return false;
    }

    /** O abate por magia veio logo depois de uma rolagem boa? (requisito "abates de sorte" da Casa) */
    public static boolean recentGoodRoll(ServerPlayer sp) {
        LastRoll last = LAST.get(sp.getUUID());
        return last != null && last.band().good && sp.serverLevel().getGameTime() - last.tick() <= LUCKY_KILL_WINDOW;
    }

    public static void forget(UUID id) {
        LAST.remove(id);
    }

    public static void clear() {
        LAST.clear();
    }

    private LuckService() {}
}
