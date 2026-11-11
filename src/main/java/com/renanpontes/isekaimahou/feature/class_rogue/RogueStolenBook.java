package com.renanpontes.isekaimahou.feature.class_rogue;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Grimório Roubado: copiar a última magia que o alvo conjurou ({@link SpellStack#recent}) e conjurar a cópia depois.
 * <ul>
 *   <li>Roubo: magia do alvo nos últimos 30 s (a até 10 blocos de onde ele está). Gatilhos do começo saem (a cópia
 *   sai na hora). Cargas: 1, +1 com poder ≥ 1,25, +1 no rank III+ do Livro-Razão (máx. 3). Ultimates/Domínios só no
 *   rank V (e gastam a Carga de Ultimate ao conjurar).</li>
 *   <li>Conjuração: sai do Ladino com 90% do poder (+5% por rank) e custa METADE do custo de mana da magia original
 *   (já com custo por cópia e a escala ×3 do núcleo). Não precisa conhecer os glifos nem ter o Círculo.</li>
 * </ul>
 */
public final class RogueStolenBook {
    public static final int RECENT_TICKS = 600;
    public static final double RECENT_RANGE = 10.0;
    public static final float COST_FRACTION = 0.5F;

    /**
     * Rouba a magia mais recente de {@code target}.
     * @return a página guardada, ou null (nada recente / magia proibida)
     */
    @Nullable
    public static RogueData.Page stealFrom(ServerPlayer thief, LivingEntity target, float power) {
        ServerLevel level = thief.serverLevel();
        List<SpellStack.Recent> list = SpellStack.recent(level, target.position(), RECENT_RANGE, RECENT_TICKS);
        SpellStack.Recent found = null;
        for (SpellStack.Recent r : list) {
            if (r.caster().equals(target.getUUID())) {
                found = r;
                break;
            }
        }
        if (found == null) {
            RogueFx.actionbar(thief, Component.translatable("message.isekai_mahou.class_rogue.spell_theft.none", target.getDisplayName()), 0xA89EC0);
            level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 8, 0.2, 0.3, 0.2, 0.01);
            return null;
        }
        SpellRecipe recipe = stripTriggers(found.recipe());
        SpellParser.Result parsed = SpellParser.parse(recipe, null, null);
        if (recipe.isEmpty() || !parsed.ok() || recipe.glyphs().contains(RogueGlyphs.STOLEN_PAGE)) {
            RogueFx.actionbar(thief, Component.translatable("message.isekai_mahou.class_rogue.spell_theft.garbled"), 0xA89EC0);
            return null;
        }
        int rank = RogueCombat.rank(thief);
        boolean ult = parsed.spell().isUltimate();
        if (ult && rank < 5) {
            RogueFx.actionbar(thief, Component.translatable("message.isekai_mahou.class_rogue.spell_theft.too_big"), 0xFF8A6A);
            return null;
        }
        if (recipe.name().isEmpty()) {
            Element e = found.element() == null ? Element.ARCANE : found.element();
            recipe = recipe.withName(Component.translatable("item.isekai_mahou.rogue_stolen_grimoire.unnamed", e.displayName()).getString());
        }
        int charges = 1 + (power >= 1.25F ? 1 : 0) + (rank >= 3 ? 1 : 0);
        String from = target.getDisplayName().getString();
        RogueData.Page page = RogueData.store(thief, new RogueData.Page(recipe,
                found.element() == null ? parsed.spell().primary() : found.element(), Math.min(RogueData.MAX_CHARGES, charges), from, ult),
                rank >= 5 ? RogueData.MAX_PAGES_MASTER : RogueData.MAX_PAGES);
        MagicApi.addCounter(thief, "rogue.spell_stolen", 1);
        RogueNet.sync(thief);

        Vec3 a = RogueFx.center(target);
        Vec3 b = RogueFx.center(thief);
        int rgb = recipe.color() >= 0 ? recipe.color() : page.element().color;
        RogueFx.line(level, a, b, RogueFx.dust(rgb, 1.0F), 0.4);
        level.sendParticles(ParticleTypes.ENCHANT, b.x, b.y + 0.4, b.z, 30, 0.4, 0.5, 0.4, 0.9);
        level.sendParticles(ParticleTypes.WITCH, a.x, a.y, a.z, 10, 0.3, 0.4, 0.3, 0.02);
        RogueFx.sound(level, b, SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F);
        RogueFx.sound(level, a, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8F, 1.3F);
        thief.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.spell_theft.ok", recipe.name(),
                target.getDisplayName(), page.charges()).withColor(rgb), true);
        if (target instanceof ServerPlayer vp) {
            vp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.spell_theft.victim", thief.getDisplayName(),
                    recipe.name()).withColor(0xFF6A6A), true);
        }
        return page;
    }

    /** Tira os gatilhos condicionais do começo (a cópia sai na hora); ligações entre estágios ficam. */
    static SpellRecipe stripTriggers(SpellRecipe recipe) {
        List<ResourceLocation> out = new ArrayList<>(recipe.glyphs());
        while (!out.isEmpty()) {
            Glyph g = GlyphRegistry.get(out.get(0));
            if (!(g instanceof TriggerGlyph t) || t.isLink()) break;
            out.remove(0);
        }
        return recipe.withGlyphs(out).withChant("");
    }

    /**
     * Conjura a página escolhida do Grimório Roubado.
     * @param powerMult multiplicador extra (o da forma Página Roubada; 1 no item)
     * @return true se conjurou (gastou uma carga)
     */
    public static boolean castSelected(ServerPlayer p, float powerMult) {
        // uma página não conjura outra página (nem a si mesma) no meio da conjuração
        if (casting) return false;
        casting = true;
        try {
            return castSelectedInner(p, powerMult);
        } finally {
            casting = false;
        }
    }

    private static boolean casting;

    private static boolean castSelectedInner(ServerPlayer p, float powerMult) {
        List<RogueData.Page> pages = RogueData.pages(p);
        if (pages.isEmpty()) {
            RogueFx.actionbar(p, Component.translatable("message.isekai_mahou.class_rogue.book.empty"), 0xA89EC0);
            return false;
        }
        int idx = RogueData.selected(p);
        RogueData.Page page = pages.get(idx);
        SpellParser.Result parsed = SpellParser.parse(page.recipe(), null, null);
        if (!parsed.ok()) {
            RogueData.spend(p, idx);
            RogueNet.sync(p);
            RogueFx.actionbar(p, Component.translatable("message.isekai_mahou.class_rogue.spell_theft.garbled"), 0xA89EC0);
            return false;
        }
        MagicData d = MagicApi.get(p);
        boolean creative = p.getAbilities().instabuild;
        if (page.ultimate() && !creative && d.charge() < Progression.MAX_CHARGE) {
            RogueFx.actionbar(p, Component.translatable("message.isekai_mahou.no_charge", (int) d.charge()), 0xFF6A6A);
            return false;
        }
        float cost = parsed.spell().manaCost() * COST_FRACTION;
        if (!creative && !d.consumeMana(cost)) {
            RogueFx.actionbar(p, MagicApi.noResourceMessage(p), 0xFF6A6A);
            return false;
        }
        if (page.ultimate() && !creative) d.setCharge(0);
        float power = (0.9F + 0.05F * Math.min(5, RogueCombat.rank(p))) * powerMult;
        if (SpellCaster.castFrom(p, page.element(), page.recipe(), power, null, null, true) == null) {
            if (!creative) d.addMana(cost);
            return false;
        }
        RogueData.spend(p, idx);
        MagicApi.addCounter(p, "rogue.stolen_cast", 1);
        RogueNet.sync(p);
        if (parsed.spell().baseCost() >= SpellCaster.ANNOUNCE_COST || page.ultimate()) {
            Announce.spell(p, page.recipe(), page.element(), page.ultimate());
        }
        ServerLevel level = p.serverLevel();
        Vec3 c = RogueFx.center(p);
        level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y + 0.5, c.z, 24, 0.5, 0.4, 0.5, 1.0);
        level.sendParticles(RogueFx.dust(page.recipe().color() >= 0 ? page.recipe().color() : page.element().color, 1.2F),
                c.x, c.y, c.z, 14, 0.4, 0.5, 0.4, 0);
        RogueFx.sound(level, c, SoundEvents.BOOK_PAGE_TURN, 0.9F, 1.4F);
        int left = page.charges() - 1;
        p.displayClientMessage(Component.translatable(left > 0 ? "message.isekai_mahou.class_rogue.book.cast"
                : "message.isekai_mahou.class_rogue.book.cast_last", page.recipe().name(), left).withColor(RogueFx.PURPLE), true);
        return true;
    }

    private RogueStolenBook() {}
}
