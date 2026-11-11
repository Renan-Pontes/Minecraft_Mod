package com.renanpontes.isekaimahou.feature.class_gunslinger.glyph;

import java.util.HashSet;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Gatilho reativo "Ao Recarregar" ({@code gun_on_reload}): ao recarregar uma arma de mana, os slots do loadout ativo
 * cuja receita começa com o gatilho são conjurados sozinhos no tick seguinte (pagando Foco, respeitando recarga,
 * silêncio e o slot G). Apertar a tecla desses slots não conjura (a magia fica "guardada" para a recarga).
 */
public final class GunTriggers {
    public static final ResourceLocation ON_RELOAD = IsekaiMahou.id("gun_on_reload");

    /** > 0 enquanto este módulo conjura sozinho. */
    private static int autoDepth;
    /** Reações já agendadas ("uuid|slot"), para não empilhar no mesmo tick. */
    private static final Set<String> PENDING = new HashSet<>();

    /** Bloqueia o uso manual de magias "Ao Recarregar". */
    public static void onPre(SpellCastEvent.Pre e) {
        if (autoDepth > 0 || !(e.getCaster() instanceof ServerPlayer p)) return;
        if (!e.getContext().isPlayerCast() || e.getContext().stage() != 0) return;
        for (TriggerGlyph t : e.getContext().spell().triggers()) {
            if (t.id().equals(ON_RELOAD)) {
                e.setCanceled(true);
                p.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.reload_only")
                        .withStyle(ChatFormatting.GOLD), true);
                return;
            }
        }
    }

    /** O jogador começou a recarregar. */
    public static void onReload(ServerPlayer p) {
        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(loadout, slot).main();
            if (recipe == null || !startsWithReload(recipe)) continue;
            String key = p.getUUID() + "|" + slot;
            if (!PENDING.add(key)) continue;
            int s = slot;
            SpellScheduler.schedule(1, () -> {
                PENDING.remove(key);
                if (!p.isRemoved() && p.isAlive()) autoCast(p, loadout, s, recipe);
            });
        }
    }

    private static boolean startsWithReload(SpellRecipe recipe) {
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (!(g instanceof TriggerGlyph)) return false;
            if (id.equals(ON_RELOAD)) return true;
        }
        return false;
    }

    private static void autoCast(ServerPlayer p, int loadout, int slot, SpellRecipe recipe) {
        if (p.isSpectator() || p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) return;
        MagicData d = MagicApi.get(p);
        if (d.activeLoadout() != loadout) return;
        boolean creative = p.getAbilities().instabuild;
        SpellParser.Result r = SpellParser.parse(recipe, creative ? null : d);
        if (!r.ok()) return;
        ParsedSpell spell = r.spell();
        if (spell.isUltimate() != (slot == Progression.ULTIMATE_SLOT)) return;
        if (spell.isUltimate() && !creative && d.charge() < Progression.MAX_CHARGE) return;
        String cdKey = MagicData.slotKey(loadout, slot);
        if (p.serverLevel().getGameTime() < d.cooldowns.getOrDefault(cdKey, 0L)) return;
        boolean overload = spell.anyStats(st -> st.overload);
        if (!creative) {
            if (overload ? p.getHealth() <= SpellCaster.overloadHealthCost(spell) + 1.0F : d.mana() < spell.manaCost()) return;
        }
        int prev = d.lastCastSlot;
        d.lastCastSlot = slot;
        autoDepth++;
        try {
            if (SpellCaster.performCast(p, d, spell.primary(), recipe, spell, cdKey, false)) {
                ElementFx.ring(p.serverLevel(), p.position(), spell.primary(), 0.9, 14);
            } else {
                d.lastCastSlot = prev;
            }
        } finally {
            autoDepth--;
        }
    }

    public static void clear() {
        PENDING.clear();
        autoDepth = 0;
    }

    private GunTriggers() {}
}
