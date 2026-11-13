package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;
import com.renanpontes.isekaimahou.glyph.TransformLaws;
import com.renanpontes.isekaimahou.glyph.TransformTargets;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Release 1.0 (onda 4) — <b>Transformação montada no Grimório</b>, igual ao Domínio: a forma {@code transformation} +
 * EFEITOS + aumentos + Leis de Transformação. Enquanto o jogador está transformado:
 * <ul>
 *   <li>efeitos <b>ofensivos</b> ({@link Role#STRIKE}) caem em quem ele ACERTA (corpo a corpo e projéteis), com
 *       {@link #STRIKE_POWER} do poder e no máx. 1 vez a cada {@link #STRIKE_GAP} ticks;</li>
 *   <li>efeitos <b>benéficos</b> ({@link Role#AURA}) pulsam NELE a cada {@link #PULSE_TICKS} ticks, com
 *       {@link #AURA_POWER} do poder;</li>
 *   <li>com Expandir na forma a aura ganha alcance ({@link #auraRadius}): os benéficos chegam aos aliados e os
 *       ofensivos também pulsam nos inimigos dentro dela ({@link #FOE_AURA_POWER});</li>
 *   <li>um efeito que já é uma transformação fixa (Kaioken, Forma de Lobo... — {@link TransformTargets#isEffect}) é o
 *       CORPO ({@link Role#BODY}): entra uma vez, no começo;</li>
 *   <li>as leis da receita valem pela duração ({@link TransformLaws}).</li>
 * </ul>
 * O elemento (aura, partículas, cor) é o primário da receita. Custo, duração e recarga saem da receita.
 *
 * <h3>Para uma LIBERAÇÃO de classe (Bankai, Vollständig...) usar uma receita</h3>
 * Um <b>espaço de receita</b> ({@link Slot}) guarda a receita que o jogador escolheu (aba Votos) para uma transformação
 * que não é magia. O id do espaço é o PATH da fonte da transformação. Dois caminhos, que fazem a mesma coisa:
 * <pre>
 *   // (A) API antiga do núcleo — a receita do espaço com o mesmo path da fonte entra sozinha por cima:
 *   TransformLaws.begin(sp, IsekaiMahou.id("bankai"), leisSoltas /* pode ser List.of() *&#47;, ticks, probeOuNull);
 *   TransformLaws.end(sp, IsekaiMahou.id("bankai"));
 *   // (B) API deste módulo — o mesmo, com o resultado na mão:
 *   TransformForge.Result r = TransformForge.beginSlot(sp, "bankai", ticks, probeOuNull, leisSoltas);
 *   TransformForge.end(sp, "bankai", natural);
 *   // cliente, botão "Moldar liberação":
 *   TransformForge.openShaper("bankai");      // onda 9: abre o Grimório no editor (glifo Transformação)
 * </pre>
 * Espaços já registrados: {@link #RACE_SLOT} (tecla V), {@link #HOLLOW_SLOT} (Resurrección) — os dois já ligados por este
 * módulo — e {@link #SHIKAI_SLOT}, {@link #BANKAI_SLOT}, {@link #VOLLSTANDIG_SLOT}, que só aparecem para o jogador depois
 * da primeira vez que a liberação dele chamar um dos caminhos acima. Outros: {@link #registerSlot}.
 * <p>
 * A receita é validada de novo a cada uso (glifos conhecidos, Círculo, limites) e cobra a mana dela no começo; sem
 * receita, sem mana ou inválida, a transformação segue igual a antes (só as leis soltas).
 *
 * <h3>Onda 5 — TODA transformação é editável</h3>
 * Palavra do Renan: <i>"Transformações tem que ser editáveis pros efeitos que o player quiser"</i>. Cada transformação do
 * mod (tecla V, liberações e formas de classe, e todas as transformações fixas de {@link TransformTargets}) tem um
 * espaço, e a receita dele é MOLDADA na própria aba Votos (botão "Moldar"): o corpo já é a transformação, então a forma
 * {@code transformation} da receita é implícita — o jogador NÃO precisa conhecê-la nem do Círculo dela; todo o resto
 * (efeitos, aumentos, leis) precisa ser conhecido e respeita Círculo, elementos e o limite de glifos das magias
 * ({@link #validate}). Não há lista fechada de efeitos: qualquer glifo de EFEITO conhecido entra.
 * <p>
 * Os aumentos postos NO CORPO (logo depois da forma) também mexem no tempo da transformação base:
 * Estender/Encurtar/Persistente → {@link #durationMult}; Acelerar/Retardar → {@link #cooldownMult}. Tempo é poder,
 * então sai caro: {@link #timeTax} entra na mana do espaço ({@link #slotMana}).
 */
public final class TransformForge {
    /** Id (path) do glifo da forma. */
    public static final String FORM = "transformation";
    public static final ResourceLocation FORM_ID = IsekaiMahou.id(FORM);
    /** MobEffect que marca a transformação conjurada (ícone com o tempo; a sessão acaba quando ele some). */
    public static final ResourceLocation PROBE_ID = IsekaiMahou.id("forged_form");

    /** Espaço da Transformação Racial (tecla V) — o path de {@code TlawSessions.RACE_SOURCE}. */
    public static final String RACE_SLOT = "race_form";
    /** Espaço da Resurrección do Hollow — o path de {@code TlawSessions.HOLLOW_SOURCE}. */
    public static final String HOLLOW_SLOT = "hollow_resurreccion";
    public static final String SHIKAI_SLOT = "shikai";
    public static final String BANKAI_SLOT = "bankai";
    public static final String VOLLSTANDIG_SLOT = "vollstandig";
    /** Marca (sincronizada) de que a liberação do espaço já foi usada pelo jogador: {@code tforge.slot.<id>}. */
    public static final String SEEN_FLAG = "tforge.slot.";

    /** Círculo mínimo da forma (um antes da Expansão de Domínio: primeiro o corpo, depois o mundo). */
    public static final int MIN_CIRCLE = 5;
    public static final int BASE_DURATION = 30 * 20;
    /** Teto da duração conjurada (Estender/Persistente não passam disto; Último Suspiro ainda estica ×1,5). */
    public static final int MAX_DURATION = 60 * 20;
    public static final int BASE_COOLDOWN = 120 * 20;
    /** Ticks entre os pulsos da aura. */
    public static final int PULSE_TICKS = 60;
    /** Ticks mínimos entre dois golpes que aplicam os efeitos. */
    public static final int STRIKE_GAP = 10;
    public static final float STRIKE_POWER = 0.6F;
    public static final float AURA_POWER = 0.5F;
    public static final float FOE_AURA_POWER = 0.35F;
    public static final float MAX_AURA_RADIUS = 12F;
    /** Onda 5: limites do que a receita de um espaço faz com a DURAÇÃO da transformação base. */
    public static final float MIN_TIME_MULT = 0.5F, MAX_TIME_MULT = 2.0F;
    /** Onda 5: limites da RECARGA (Acelerar −10% cada, até −40%; Retardar +15% cada, até +45%). */
    public static final float MIN_COOLDOWN_MULT = 0.6F, MAX_COOLDOWN_MULT = 1.45F;
    /** Mana fixa por "degrau" de tempo comprado (ver {@link #timeTax}). */
    public static final int TIME_TAX = 120;

    /** O papel de cada efeito da receita enquanto a transformação dura. */
    public enum Role { BODY, STRIKE, AURA }

    public static Role role(ParsedSpell.EffectEntry entry) {
        if (TransformTargets.isEffect(entry.effect().id())) return Role.BODY;
        return entry.effect().isBeneficial() != entry.stats().inverted ? Role.AURA : Role.STRIKE;
    }

    /** Efeitos da receita (estágio 0) com o papel pedido, na ordem da receita. */
    public static List<ParsedSpell.EffectEntry> effects(ParsedSpell spell, Role role) {
        List<ParsedSpell.EffectEntry> out = new ArrayList<>();
        for (ParsedSpell.EffectEntry e : spell.effects()) if (role(e) == role) out.add(e);
        return out;
    }

    /** A magia é uma transformação montada? */
    public static boolean isForge(@Nullable ParsedSpell spell) {
        return spell != null && spell.form().id().equals(FORM_ID);
    }

    /** A receita começa (depois dos gatilhos) com a forma Transformação? Não valida o resto. */
    public static boolean isForgeRecipe(@Nullable SpellRecipe recipe) {
        if (recipe == null || recipe.isEmpty()) return false;
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && g.kind() == GlyphKind.FORM) return id.equals(FORM_ID);
        }
        return false;
    }

    /** Duração da transformação conjurada (aumentos da forma), em ticks. */
    public static int duration(SpellStats formStats) {
        return Math.min(MAX_DURATION, formStats.duration(BASE_DURATION));
    }

    /** Alcance da aura em blocos: 0 sem Expandir (só em você); 4/6/8... com Expandir (Concentrar ×0,6). */
    public static float auraRadius(SpellStats formStats) {
        if (formStats.aoe <= 0) return 0F;
        float r = 2F + 2F * formStats.aoe;
        if (formStats.concentrate) r *= 0.6F;
        return Math.min(MAX_AURA_RADIUS, r);
    }

    // =====================================================================================
    //  Onda 5 — tempo da transformação base, custo e validação das receitas de espaço
    // =====================================================================================

    /** Quanto a receita estica/encolhe a transformação base: a mesma razão da forma conjurada (30 s → até 60 s). */
    public static float durationMult(SpellStats formStats) {
        return Mth.clamp(duration(formStats) / (float) BASE_DURATION, MIN_TIME_MULT, MAX_TIME_MULT);
    }

    /** Quanto a receita mexe na recarga: Acelerar no corpo −10% cada; Retardar +15% cada (e barateia os efeitos). */
    public static float cooldownMult(SpellStats formStats) {
        float m = formStats.speed >= 0 ? 1F - 0.10F * formStats.speed : 1F + 0.15F * -formStats.speed;
        return Mth.clamp(m, MIN_COOLDOWN_MULT, MAX_COOLDOWN_MULT);
    }

    /** Recarga curta encarece os pulsos dos efeitos (+20% por Acelerar); Retardar dá um desconto pequeno (−8%). */
    public static float speedCostMult(SpellStats formStats) {
        float m = formStats.speed >= 0 ? 1F + 0.20F * formStats.speed : 1F - 0.08F * -formStats.speed;
        return Mth.clamp(m, 0.76F, 1.8F);
    }

    /**
     * Mana fixa pelo TEMPO comprado num espaço (vale mesmo sem efeitos na receita): {@value #TIME_TAX} por +50% de
     * duração e por −12,5% de recarga. Nada de poder fácil: dobrar uma Bankai custa caro em mana toda vez.
     */
    public static int timeTax(SpellStats formStats) {
        float dur = Math.max(0F, durationMult(formStats) - 1F) * 2F;
        float cd = Math.max(0F, 1F - cooldownMult(formStats)) * 8F;
        return Math.round(TIME_TAX * (dur + cd));
    }

    /** Mana cobrada quando a receita entra por cima de uma transformação (espaço): a da receita + o tempo comprado. */
    public static int slotMana(ParsedSpell spell) {
        return spell.manaCost() + timeTax(spell.formStats());
    }

    private static SpellParser.Result fail(String key, Object... args) {
        return new SpellParser.Result(null, Component.translatable(key, args));
    }

    /**
     * Valida a receita de um ESPAÇO. A forma {@code transformation} (1º glifo) é o corpo e não é checada; tudo depois
     * dela precisa ser efeito, aumento ou Lei de Transformação que o jogador CONHECE, do Círculo dele, com os elementos
     * dele e dentro do limite de glifos das magias (a forma conta como 1). {@code data} null = sem checagens (criativo).
     * A maestria dos glifos vale normalmente. Vale igual no cliente (editor) e no servidor.
     */
    public static SpellParser.Result validate(@Nullable SpellRecipe recipe, @Nullable MagicData data) {
        if (recipe == null || recipe.isEmpty() || !recipe.glyphs().get(0).equals(FORM_ID)) {
            return fail("message.isekai_mahou.tforge.not_forge");
        }
        SpellParser.Result res = SpellParser.parse(recipe, Element.ARCANE, null, data);
        if (!res.ok() || data == null) return res;
        ParsedSpell spell = res.spell();
        if (!isForge(spell) || spell.hasNext()) return fail("message.isekai_mahou.tforge.not_forge");
        List<Glyph> all = new ArrayList<>();
        int laws = 0;
        int maxLaws = TransformTargets.maxLaws(data.level(), data.circle());
        for (int i = 0; i < recipe.glyphs().size(); i++) {
            ResourceLocation id = recipe.glyphs().get(i);
            Glyph g = GlyphRegistry.get(id);
            if (g == null) return fail("spell_error.isekai_mahou.unknown", id.toString());
            all.add(g);
            if (i == 0) continue; // o corpo
            if (g.kind() == GlyphKind.FORM || g.kind() == GlyphKind.TRIGGER) {
                return fail("message.isekai_mahou.tforge.only_parts", g.displayName());
            }
            if (!data.knowsGlyph(id)) return fail("spell_error.isekai_mahou.not_known", g.displayName());
            if (data.circle() < g.minCircle()) return fail("spell_error.isekai_mahou.circle", g.displayName(), g.minCircle());
            if (!SpellParser.canUse(spell.primary(), g, data)) {
                return fail("spell_error.isekai_mahou.wrong_element", g.displayName(), spell.primary().displayName());
            }
            if (g instanceof TransformLawGlyph tl) {
                if (data.level() < tl.minLevel()) return fail("spell_error.isekai_mahou.tlaw_level", g.displayName(), tl.minLevel());
                if (++laws > maxLaws) return fail("spell_error.isekai_mahou.tlaw_too_many", laws, maxLaws);
            }
        }
        int max = Progression.maxGlyphs(data.level(), data.circle());
        if (all.size() > max) return fail("spell_error.isekai_mahou.too_many_glyphs", all.size(), max);
        Element primary = spell.primary();
        if (primary != null && primary != Element.ARCANE && !data.isAwakened(primary) && !SpellCost.primaryFromOrigin(all)) {
            return fail("spell_error.isekai_mahou.element_not_awakened", primary.displayName());
        }
        int distinct = 0;
        for (Element e : spell.elements()) if (e != Element.ARCANE) distinct++;
        int maxEl = Progression.maxSpellElements(data.circle());
        if (distinct > maxEl) return fail("spell_error.isekai_mahou.too_many_elements", distinct, maxEl);
        return res;
    }

    /**
     * Para liberações que guardam o próprio relógio (Shikai, Resurrección...): quanto a receita ESCOLHIDA do espaço
     * vai esticar/encurtar a liberação que está para começar. 1 se não há receita, se ela não vale mais ou se a mana
     * não dá (aí ela não entra e nada muda). Chame ANTES de {@code begin}, com o tempo base.
     */
    public static float plannedDurationMult(@Nullable Player player, String slot) {
        ParsedSpell s = planned(player, slot);
        return s == null ? 1F : durationMult(s.formStats());
    }

    @Nullable
    private static ParsedSpell planned(@Nullable Player player, String slot) {
        if (player == null || slot == null || player.level().isClientSide) return null;
        SpellRecipe r = ForgeData.recipe(player, slot);
        if (r.isEmpty()) return null;
        boolean creative = player.getAbilities().instabuild;
        MagicData d = com.renanpontes.isekaimahou.core.MagicApi.get(player);
        SpellParser.Result res = validate(r, creative ? null : d);
        if (!res.ok() || !isForge(res.spell())) return null;
        if (!creative && d.mana() < slotMana(res.spell())) return null;
        return res.spell();
    }

    /** Multiplicador de duração que a receita ATIVA do espaço dá à transformação base (1 = sem receita ativa). */
    public static float activeDurationMult(@Nullable LivingEntity entity, String slot) {
        ParsedSpell s = entity == null ? null : ForgeSessions.activeSpell(entity, IsekaiMahou.id(slot));
        return s == null ? 1F : durationMult(s.formStats());
    }

    /** Multiplicador de recarga que a receita ATIVA do espaço dá à transformação base (1 = sem receita ativa). */
    public static float activeCooldownMult(@Nullable LivingEntity entity, String slot) {
        ParsedSpell s = entity == null ? null : ForgeSessions.activeSpell(entity, IsekaiMahou.id(slot));
        return s == null ? 1F : cooldownMult(s.formStats());
    }

    // =====================================================================================
    //  Resumo (Grimório e aba Votos)
    // =====================================================================================

    /**
     * Resumo da transformação para o jogador: o que os golpes aplicam, o que a aura faz, o que cada lei dá e cobra,
     * duração, recarga e mana. {@code castTimes} = false para receitas usadas por cima de outra transformação (tecla V,
     * liberações): a duração e a recarga são as da transformação base, então só o custo aparece.
     */
    public static List<Component> summary(ParsedSpell spell, boolean castTimes) {
        List<Component> out = new ArrayList<>();
        Element el = spell.primary() == null ? Element.ARCANE : spell.primary();
        out.add(tr("title", el.displayName().copy().withColor(el.color)).withStyle(ChatFormatting.BOLD));
        List<ParsedSpell.EffectEntry> body = effects(spell, Role.BODY);
        List<ParsedSpell.EffectEntry> strike = effects(spell, Role.STRIKE);
        List<ParsedSpell.EffectEntry> aura = effects(spell, Role.AURA);
        float radius = auraRadius(spell.formStats());
        int every = PULSE_TICKS / 20;
        if (!body.isEmpty()) out.add(tr("body", names(body)).withColor(0xE8D8F0));
        if (!strike.isEmpty()) out.add(tr("strike", names(strike), pct(STRIKE_POWER)).withColor(0xFF9A8A));
        if (!aura.isEmpty()) {
            out.add((radius > 0F ? tr("aura_allies", names(aura), every, num(radius), pct(AURA_POWER))
                    : tr("aura_self", names(aura), every, pct(AURA_POWER))).withColor(0x7CE08A));
        }
        if (radius > 0F && !strike.isEmpty()) {
            out.add(tr("aura_foes", names(strike), num(radius), every, pct(FOE_AURA_POWER)).withColor(0xFFB070));
        }
        for (TransformLawGlyph law : TransformLaws.lawsOf(spell)) {
            out.add(Component.literal("⛓ ").append(law.displayName()).withColor(law.rgbColor()));
            out.add(Component.literal("  + ").append(law.boon()).withColor(0x7CE08A));
            out.add(Component.literal("  − ").append(law.price()).withColor(0xE07070));
        }
        if (castTimes) {
            out.add(tr("times", seconds(duration(spell.formStats())), seconds(spell.cooldown()), spell.manaCost())
                    .withColor(0x7FB8FF));
        } else {
            // Num espaço: a receita mexe no tempo da transformação base (aumentos no corpo) e cobra por isso
            float dm = durationMult(spell.formStats());
            float cm = cooldownMult(spell.formStats());
            if (dm != 1F || cm != 1F) out.add(tr("slot_times", mult(dm), mult(cm)).withColor(0xFFD86B));
            out.add(tr("cost_only", slotMana(spell)).withColor(0x7FB8FF));
        }
        return out;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("screen.isekai_mahou.tforge." + key, args);
    }

    private static MutableComponent names(List<ParsedSpell.EffectEntry> list) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) out.append(Component.literal(", "));
            out.append(list.get(i).effect().displayName());
        }
        return out;
    }

    private static String pct(float f) {
        return Math.round(f * 100F) + "%";
    }

    private static String num(float f) {
        return f == Math.rint(f) ? String.valueOf((int) f) : String.format(java.util.Locale.ROOT, "%.1f", f);
    }

    private static String mult(float f) {
        return "×" + String.format(java.util.Locale.ROOT, "%.2f", f);
    }

    private static String seconds(int ticks) {
        return String.valueOf(Math.round(ticks / 20F));
    }

    // =====================================================================================
    //  Espaços de receita (tecla V, liberações de classe)
    // =====================================================================================

    /**
     * Um espaço de receita: uma transformação que não é magia (forma racial, Bankai...) e que pode receber uma receita.
     *
     * @param id        id curto e estável (fica salvo com o jogador), igual ao PATH da fonte da transformação
     * @param title     nome mostrado na aba Votos
     * @param available o jogador (dados do cliente ou do servidor) tem essa transformação?
     */
    public record Slot(String id, Supplier<Component> title, Predicate<MagicData> available) {}

    private static final Map<String, Slot> SLOTS = new LinkedHashMap<>();

    /** Registra um espaço de receita (no init COMUM do módulo: precisa existir no cliente e no servidor). */
    public static synchronized void registerSlot(String id, Supplier<Component> title, Predicate<MagicData> available) {
        if (id == null || id.isEmpty() || id.length() > 48) return;
        SLOTS.put(id, new Slot(id, title, available == null ? d -> true : available));
    }

    @Nullable
    public static synchronized Slot slot(String id) {
        return id == null ? null : SLOTS.get(id);
    }

    /** Ids de todos os espaços registrados, na ordem de registro (comandos de teste). */
    public static synchronized List<String> slotIds() {
        return List.copyOf(SLOTS.keySet());
    }

    /** Espaços que o jogador tem, na ordem de registro. */
    public static synchronized List<Slot> slotsFor(MagicData data) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : SLOTS.values()) {
            try {
                if (s.available().test(data)) out.add(s);
            } catch (RuntimeException ignored) {
                // um módulo com teste quebrado não derruba a aba
            }
        }
        return out;
    }

    // =====================================================================================
    //  Começar / terminar
    // =====================================================================================

    /**
     * Resultado de começar uma transformação por receita.
     *
     * @param ok    a receita entrou
     * @param error por que não entrou (null se entrou ou se não havia receita)
     * @param laws  leis em vigor por causa desta chamada (da receita + as soltas passadas)
     */
    public record Result(boolean ok, @Nullable Component error, List<TransformLawGlyph> laws) {
        /** Sem receita escolhida: nada mudou (a transformação segue igual a antes). */
        public static final Result NONE = new Result(false, null, List.of());
    }

    /**
     * A liberação do espaço {@code slot} começou: a receita escolhida entra por cima (cobra a mana dela) e as leis da
     * receita + {@code looseLaws} valem enquanto durar. Sem receita, só as leis soltas (igual a
     * {@link TransformLaws#begin}).
     *
     * @param ticks     quanto a transformação base dura (ignorado se houver {@code probe})
     * @param probe     MobEffect presente enquanto a transformação base dura (tudo acaba quando ele some), ou null
     * @param looseLaws leis soltas escolhidas para esta transformação (entram depois das da receita, até o limite do
     *                  jogador — {@link TransformTargets#maxLaws}); pode ser vazio
     */
    public static Result beginSlot(ServerPlayer player, String slot, int ticks, @Nullable Holder<MobEffect> probe,
                                   Collection<TransformLawGlyph> looseLaws) {
        ResourceLocation source = IsekaiMahou.id(slot);
        TlawSessions.INSTANCE.begin(player, source, looseLaws == null ? List.of() : looseLaws, ticks, probe);
        boolean on = ForgeSessions.active(player, source);
        Component error = on ? null : ForgeSessions.lastError(player, source);
        if (!on && error == null && TlawSessions.lawsOf(player, source).isEmpty()) return Result.NONE;
        return new Result(on, error, TlawSessions.lawsOf(player, source));
    }

    /** A liberação do espaço acabou. {@code natural} = acabou sozinha (cobra os preços de fim das leis). */
    public static void end(ServerPlayer player, String slot, boolean natural) {
        ResourceLocation source = IsekaiMahou.id(slot);
        if (natural) TlawSessions.INSTANCE.end(player, source);
        else TlawSessions.drop(player, source);
    }

    /** A entidade está com alguma transformação por receita ativa (conjurada ou num espaço)? */
    public static boolean active(@Nullable LivingEntity entity) {
        return entity != null && ForgeSessions.active(entity, null);
    }

    /** A receita do espaço {@code slot} está ativa na entidade? */
    public static boolean active(@Nullable LivingEntity entity, String slot) {
        return entity != null && ForgeSessions.active(entity, IsekaiMahou.id(slot));
    }

    /** Receita escolhida para o espaço ({@link SpellRecipe#EMPTY} = nenhuma). Vale no servidor e no cliente. */
    public static SpellRecipe recipe(@Nullable Player player, String slot) {
        if (player == null || slot == null) return SpellRecipe.EMPTY;
        return ForgeData.recipe(player, slot);
    }

    // =====================================================================================
    //  Cliente
    // =====================================================================================

    /** Cliente: abre o editor de transformações do Grimório no espaço pedido (instalado pela parte de cliente do módulo). */
    private static volatile Consumer<String> opener;

    public static void installOpener(Consumer<String> o) {
        opener = o;
    }

    /** CLIENTE: abre o menu B no Grimório, no editor do glifo Transformação, já no espaço {@code slot} (botão "Moldar liberação"). */
    public static void openShaper(String slot) {
        Consumer<String> o = opener;
        if (o != null) o.accept(slot);
    }

    private TransformForge() {}
}
