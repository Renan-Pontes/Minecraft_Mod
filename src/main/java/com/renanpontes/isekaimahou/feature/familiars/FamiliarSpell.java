package com.renanpontes.isekaimahou.feature.familiars;

import java.util.Map;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.TransformTargets;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * v1.0 (onda 4) — a receita de apoio do Familiar: "o dono pode ensinar UMA receita de apoio ao familiar (cura leve,
 * luz, proteção, achar coisas) que ele usa sozinho com recarga". E a palavra do Renan na onda 2 continua valendo: o
 * familiar ajuda <b>sem lutar pelo jogador</b> — então nada de receita de dano.
 * <ul>
 *   <li><b>O que pode</b>: só os efeitos da lista {@link #SUPPORT} (cura, limpar, escudo, luz, achar...), sem
 *       Inverter/Sobrecarga, até {@link #MAX_EFFECTS} efeitos, magia de um estágio só, sem leis, gatilhos, ultimates
 *       nem transformações. O dono precisa conhecer os glifos.</li>
 *   <li><b>Como usa</b>: sempre NO DONO, quando faz sentido (o PRIMEIRO efeito decide — {@link When}): ferido, com
 *       efeito ruim, apanhando, caindo, sem ar, no escuro, ou sempre que recarrega (achar coisas).</li>
 *   <li><b>Leve</b>: poder de {@link #power(int)} (50% / 65% / 80% por estágio), recarga longa
 *       ({@link #cooldown(ParsedSpell, int)}), sem mana, sem XP e sem maestria para o dono. Nunca mexe em blocos.</li>
 * </ul>
 * As regras ({@link #problem}) rodam no servidor (que manda) e no cliente (para a aba só listar o que serve).
 */
public final class FamiliarSpell {
    /** Quando o familiar usa a receita (decidido pelo primeiro efeito dela). */
    public enum When {
        HURT, CLEANSE, DANGER, FALLING, UNDERWATER, DARK, ALWAYS;

        /** Texto curto para a aba ("quando você está ferido"...). */
        public MutableComponent text() {
            return Component.translatable("screen.isekai_mahou.familiars.spell.when." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Efeitos de apoio aceitos (id do glifo → quando usar). Só o que cuida do dono; nada que fira, invoque ou mexa em blocos. */
    private static final Map<String, When> SUPPORT = Map.ofEntries(
            Map.entry("heal", When.HURT), Map.entry("greater_heal", When.HURT), Map.entry("regenerate", When.HURT),
            Map.entry("mend", When.HURT), Map.entry("spin_heal", When.HURT),
            Map.entry("cleanse", When.CLEANSE), Map.entry("purify", When.CLEANSE),
            Map.entry("shield", When.DANGER), Map.entry("stoneskin", When.DANGER), Map.entry("protective_bubble", When.DANGER),
            Map.entry("slow_fall", When.FALLING), Map.entry("water_breathing", When.UNDERWATER),
            Map.entry("conjure_light", When.DARK),
            Map.entry("detect", When.ALWAYS), Map.entry("reveal", When.ALWAYS), Map.entry("mage_mana_sense", When.ALWAYS));

    public static final int MAX_EFFECTS = 2;
    /** Recarga mínima (ticks) no estágio 1; a receita pode pedir mais. */
    private static final int MIN_COOLDOWN = 600;
    /** O familiar só usa a receita com o dono a esta distância. */
    private static final double REACH = 12.0;

    private FamiliarSpell() {}

    /** Poder dos efeitos lançados pelo familiar: "cura leve". */
    public static float power(int stage) {
        return stage >= 3 ? 0.8F : stage == 2 ? 0.65F : 0.5F;
    }

    /** Recarga em ticks: 4× a recarga da magia, no mínimo 30 s; estágios mais altos recarregam mais rápido. */
    public static int cooldown(ParsedSpell spell, int stage) {
        float mult = stage >= 3 ? 0.7F : stage == 2 ? 0.85F : 1.0F;
        return Math.round(Math.max(MIN_COOLDOWN, spell.cooldown() * 4) * mult);
    }

    /** Quando o familiar usa esta receita: o primeiro efeito decide. */
    public static When when(ParsedSpell spell) {
        if (spell.effects().isEmpty()) return When.ALWAYS;
        return SUPPORT.getOrDefault(spell.effects().get(0).effect().id().getPath(), When.ALWAYS);
    }

    /**
     * A receita serve como receita de apoio do familiar?
     * @param data dados do dono (checa glifos conhecidos e Círculo); null = não checa (receita já ensinada)
     * @return null se serve; senão o motivo
     */
    @Nullable
    public static Component problem(@Nullable SpellRecipe recipe, @Nullable MagicData data) {
        if (recipe == null || recipe.isEmpty()) return reason("empty");
        SpellParser.Result r = SpellParser.parse(recipe, Element.ARCANE, data);
        if (!r.ok()) return r.error();
        return problem(r.spell());
    }

    @Nullable
    private static Component problem(ParsedSpell s) {
        if (s.isUltimate() || s.hasNext() || s.leap() || !s.laws().isEmpty() || !s.triggers().isEmpty()
                || TransformTargets.isTransformation(s.form(), s.effects())) {
            return reason("complex");
        }
        if (s.effects().isEmpty() || s.effects().size() > MAX_EFFECTS) return reason("count", MAX_EFFECTS);
        SpellStats fs = s.formStats();
        if (fs.inverted || fs.overload || fs.enemiesOnly) return reason("harm", s.form().displayName());
        for (ParsedSpell.EffectEntry e : s.effects()) {
            EffectGlyph g = e.effect();
            SpellStats st = e.stats();
            // Nada de dano no familiar: só os efeitos de apoio da lista, e sem Inverter (que vira o efeito contra inimigos)
            if (!SUPPORT.containsKey(g.id().getPath()) || g.baseDamage() > 0F || g.isLegendary()) return reason("harm", g.displayName());
            if (st.inverted || st.overload || st.enemiesOnly) return reason("harm", g.displayName());
        }
        return null;
    }

    private static Component reason(String key, Object... args) {
        return Component.translatable("familiars.isekai_mahou.spell.no." + key, args);
    }

    /** Nome para mostrar: o que o jogador deu à magia, ou os glifos dela ("Si Mesmo + Cura"). */
    public static MutableComponent displayName(SpellRecipe recipe) {
        if (!recipe.name().isBlank()) return Component.literal(recipe.name());
        MutableComponent out = Component.empty();
        boolean first = true;
        for (var id : recipe.glyphs()) {
            var g = com.renanpontes.isekaimahou.glyph.GlyphRegistry.get(id);
            if (g == null) continue;
            if (!first) out.append(" + ");
            out.append(g.displayName());
            first = false;
        }
        return out;
    }

    // =====================================================================================
    //  Servidor: ensinar
    // =====================================================================================

    /** O dono ensina (ou, com null/vazia, faz esquecer) a receita. Valida tudo de novo aqui: o cliente só sugere. */
    static void teach(ServerPlayer sp, @Nullable SpellRecipe recipe) {
        FamiliarData d = FamiliarContent.data(sp);
        if (!d.awakened()) return;
        if (recipe == null || recipe.isEmpty()) {
            if (d.spell().isEmpty()) return;
            d.setSpell(null);
            sp.displayClientMessage(Component.translatable("familiars.isekai_mahou.spell.forgot", FamiliarService.displayName(d))
                    .withColor(d.form().rgb), true);
            return;
        }
        Component problem = problem(recipe, sp.getAbilities().instabuild ? null : MagicApi.get(sp));
        if (problem != null) {
            sp.displayClientMessage(Component.translatable("familiars.isekai_mahou.spell.refused", problem), true);
            return;
        }
        d.setSpell(recipe);
        // primeira vez só depois de uma recarga curta: ensinar no meio da luta não é cura instantânea
        d.setSpellReady(sp.serverLevel().getGameTime() + 200);
        sp.displayClientMessage(Component.translatable("familiars.isekai_mahou.spell.taught", FamiliarService.displayName(d),
                displayName(recipe)).withColor(d.form().rgb), true);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
        Hints.show(sp, "familiar_spell");
    }

    // =====================================================================================
    //  Servidor: usar sozinho
    // =====================================================================================

    /** A receita ensinada, validada (cache nos dados do dono). null = sem receita ou ela deixou de valer. */
    @Nullable
    private static ParsedSpell parsed(FamiliarData d) {
        if (d.spell().isEmpty()) return null;
        if (d.spellParsed == null) {
            // data null: os glifos foram checados quando o dono ensinou
            SpellParser.Result r = SpellParser.parse(d.spell(), Element.ARCANE, null, null);
            if (!r.ok() || problem(r.spell()) != null) return null;
            d.spellParsed = r.spell();
        }
        return d.spellParsed;
    }

    /** Chamado pelo corpo do familiar a cada 10 ticks (com a ajuda ligada): usa a receita se recarregou e faz sentido. */
    static void tick(FamiliarEntity fam, ServerPlayer owner, FamiliarData d, ServerLevel level) {
        if (d.spell().isEmpty() || level.getGameTime() < d.spellReady()) return;
        if (fam.distanceToSqr(owner) > REACH * REACH || !owner.isAlive()) return;
        ParsedSpell spell = parsed(d);
        if (spell == null || !needed(when(spell), owner, level)) return;
        cast(fam, owner, d, spell, level);
    }

    /** Comando de teste: usa a receita agora, sem olhar recarga nem condição. */
    static boolean castNow(FamiliarEntity fam, ServerPlayer owner, FamiliarData d) {
        ParsedSpell spell = parsed(d);
        if (spell == null) return false;
        cast(fam, owner, d, spell, owner.serverLevel());
        return true;
    }

    private static boolean needed(When when, ServerPlayer owner, ServerLevel level) {
        return switch (when) {
            case HURT -> owner.getHealth() < owner.getMaxHealth() * 0.75F;
            case CLEANSE -> owner.isOnFire() || hasBadEffect(owner);
            case DANGER -> level.getGameTime() - FamiliarService.lastHurt(owner) < 100 && FamiliarService.lastHurt(owner) > 0;
            case FALLING -> owner.fallDistance > 4.0F && !owner.onGround() && !owner.isFallFlying() && !owner.getAbilities().flying;
            case UNDERWATER -> owner.isUnderWater() && owner.getAirSupply() < owner.getMaxAirSupply() * 0.6F
                    && !owner.hasEffect(MobEffects.WATER_BREATHING);
            case DARK -> level.getMaxLocalRawBrightness(owner.blockPosition()) < 5 && !owner.hasEffect(MobEffects.NIGHT_VISION);
            // achar coisas: fora das cidades (lá não há o que farejar e o brilho nos moradores só atrapalha)
            case ALWAYS -> !Places.isSafe(level, owner.blockPosition());
        };
    }

    private static boolean hasBadEffect(ServerPlayer owner) {
        for (MobEffectInstance e : owner.getActiveEffects()) {
            if (!e.getEffect().value().isBeneficial() && !e.isAmbient()) return true;
        }
        return false;
    }

    /**
     * Lança os efeitos no dono. O dono é o "conjurador" (os efeitos de apoio foram feitos para agir em quem conjura:
     * visão noturna, minérios só para ele...), mas a conjuração NÃO é marcada como de jogador: sem mana, sem XP, sem
     * maestria, sem eventos de conjuração — é o familiar ajudando, não o dono upando parado.
     */
    private static void cast(FamiliarEntity fam, ServerPlayer owner, FamiliarData d, ParsedSpell spell, ServerLevel level) {
        d.setSpellReady(level.getGameTime() + cooldown(spell, d.stage()));
        SpellContext ctx = new SpellContext(level, owner, spell.primary(), d.spell(), spell);
        ctx.setPowerMult(power(d.stage()));
        ctx.resolveOnSelf();
        // um fio de brilho do familiar até o dono, na cor da forma, e um som baixinho
        int rgb = d.form().rgb;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F), 0.9F);
        Vec3 from = fam.position().add(0, fam.getBbHeight() * 0.6, 0);
        Vec3 to = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
        int steps = Math.max(3, Math.min(16, (int) (from.distanceTo(to) * 2)));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
        }
        level.playSound(null, fam.getX(), fam.getY(), fam.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.5F, 1.5F);
        Hints.show(owner, "familiar_spell_cast");
    }
}
