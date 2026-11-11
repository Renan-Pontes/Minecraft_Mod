package com.renanpontes.isekaimahou.feature.arcane_duel;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.event.PendingCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.arcane_duel.entity.DuelFxEntity;
import com.renanpontes.isekaimahou.feature.arcane_duel.glyph.DuelGlyphs;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Gatilho "Em Resposta" (Magic: The Gathering). Um slot do loadout ATIVO cuja receita começa com {@code in_response}
 * fica preparado: quando uma conjuração pendente HOSTIL entra na Pilha a ≤20 blocos, a magia do slot sai sozinha (na
 * hora, sem tempo de conjuração), mirando o conjurador da pendente (ou o ponto dela). Paga mana, respeita recarga,
 * silêncio e Exaustão Mágica; no máximo UMA resposta por pendente por jogador. Apertar a tecla do slot não conjura.
 */
public final class InResponse {
    public static final double RANGE = 20.0;

    /** > 0 enquanto o módulo conjura uma resposta (thread do servidor). */
    private static int depth;
    /** "idDaPendente|uuid" que já responderam. */
    private static final Set<String> RESPONDED = new HashSet<>();

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    public static void onPending(PendingCastEvent event) {
        SpellStack.Pending p = event.getPending();
        ServerLevel level = event.getLevel();
        // Quem apertou a tecla de um slot preparado: a magia não carrega (só sai em resposta)
        if (depth == 0 && p.fromPlayerCast() && p.caster() instanceof ServerPlayer owner) {
            MagicData d = MagicApi.get(owner);
            if (d.channelRecipe != null && isResponse(d.channelRecipe)) {
                SpellCaster.interrupt(owner);
                owner.displayClientMessage(Component.translatable("message.isekai_mahou.arcane_duel.in_response.only")
                        .withStyle(ChatFormatting.GOLD), true);
                return;
            }
        }
        if (RESPONDED.size() > 2048) prune();
        for (ServerPlayer sp : level.players()) {
            if (sp.isSpectator() || !sp.isAlive() || !p.hostileTo(sp)) continue;
            LivingEntity c = p.caster();
            double d2 = sp.distanceToSqr(p.pos());
            if (c != null) d2 = Math.min(d2, sp.distanceToSqr(c));
            if (d2 > RANGE * RANGE || !hasPreparedSlot(sp)) continue;
            if (!RESPONDED.add(p.id() + "|" + sp.getUUID())) continue;
            // Próximo tick: fora do meio da conjuração/golpe de quem empilhou
            SpellScheduler.schedule(1, () -> {
                if (p.active() && !sp.isRemoved() && sp.isAlive() && sp.level() == level) respond(sp, p);
            });
        }
    }

    /** O slot preparado não sai pela tecla (só em resposta). */
    public static void onPre(SpellCastEvent.Pre event) {
        if (depth > 0 || !(event.getCaster() instanceof ServerPlayer p) || !event.getContext().isPlayerCast()) return;
        if (event.getContext().spell().hasTrigger(DuelGlyphs.IN_RESPONSE.getPath())) {
            event.setCanceled(true);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.arcane_duel.in_response.only")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    // =====================================================================================
    //  Resposta
    // =====================================================================================

    private static boolean hasPreparedSlot(ServerPlayer p) {
        MagicData d = MagicApi.get(p);
        Element el = d.activeElement();
        int loadout = d.activeLoadout(el);
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe r = d.peekSlot(el, loadout, slot).main();
            if (r != null && isResponse(r)) return true;
        }
        return false;
    }

    /** Conjura o primeiro slot preparado que puder sair. */
    private static void respond(ServerPlayer p, SpellStack.Pending pending) {
        MagicData d = MagicApi.get(p);
        Element el = d.activeElement();
        int loadout = d.activeLoadout(el);
        if (d.channelSlot >= 0) return;   // conjurando outra coisa: não atropela
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(el, loadout, slot).main();
            if (recipe == null || !isResponse(recipe)) continue;
            if (cast(p, d, el, loadout, slot, recipe, pending)) return;
        }
    }

    private static boolean cast(ServerPlayer p, MagicData d, Element el, int loadout, int slot, SpellRecipe recipe,
                                SpellStack.Pending pending) {
        if (p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) return false;
        boolean creative = p.getAbilities().instabuild;
        SpellParser.Result r = SpellParser.parse(recipe, el, creative ? null : d);
        if (!r.ok()) return false;
        ParsedSpell spell = r.spell();
        if (spell.isUltimate() != (slot == Progression.ULTIMATE_SLOT)) return false;
        if (spell.isUltimate() && !creative && d.charge(el) < Progression.MAX_CHARGE) return false;
        String key = MagicData.slotKey(el, loadout, slot);
        if (p.serverLevel().getGameTime() < d.cooldowns.getOrDefault(key, 0L)) return false;
        if (!creative && !canPay(p, d, spell)) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.arcane_duel.in_response.no_mana")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Mira: vira o olhar para o conjurador (ou ponto) da pendente só durante a conjuração e devolve depois
        LivingEntity c = pending.caster();
        Vec3 aim = c != null && c.isAlive() ? c.position().add(0, c.getBbHeight() * 0.6, 0) : pending.pos();
        Vec3 dir = aim.subtract(p.getEyePosition());
        float oldYaw = p.getYRot(), oldPitch = p.getXRot(), oldHead = p.getYHeadRot();
        if (dir.lengthSqr() > 1.0E-4) {
            double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            p.setYRot((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F);
            p.setXRot((float) -(Mth.atan2(dir.y, h) * Mth.RAD_TO_DEG));
            p.setYHeadRot(p.getYRot());
        }
        boolean ok;
        depth++;
        try {
            ok = SpellCaster.performCast(p, d, el, recipe, spell, key, false);
        } finally {
            depth--;
            p.setYRot(oldYaw);
            p.setXRot(oldPitch);
            p.setYHeadRot(oldHead);
        }
        if (ok) feedback(p, recipe, el, pending);
        return ok;
    }

    private static void feedback(ServerPlayer p, SpellRecipe recipe, Element el, SpellStack.Pending pending) {
        ServerLevel level = p.serverLevel();
        int rgb = recipe.color() >= 0 ? recipe.color() : el.color;
        // A carta "virada" em resposta sobre a cabeça + o anel do gatilho
        DuelFx.spawn(level, DuelFxEntity.CARD, p.position().add(0, p.getBbHeight() + 0.7, 0), 0.75F, 26, rgb, 3F);
        ElementFx.ring(level, p.position(), el, 1.1, 18);
        DuelFx.sound(level, p.position(), SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.4F);
        DuelFx.sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 1.6F);
        Component name = recipe.name().isEmpty()
                ? Component.translatable("announce.isekai_mahou.unnamed", el.displayName())
                : Component.literal(recipe.name());
        p.displayClientMessage(Component.translatable("message.isekai_mahou.arcane_duel.in_response.fired", name, pending.name())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static boolean canPay(ServerPlayer p, MagicData d, ParsedSpell spell) {
        if (spell.anyStats(s -> s.overload)) return p.getHealth() > SpellCaster.overloadHealthCost(spell) + 1.0F; // v1.0: mesma conta do núcleo
        return d.mana() >= spell.manaCost();
    }

    /** A receita começa com o gatilho Em Resposta (entre os gatilhos iniciais)? */
    public static boolean isResponse(SpellRecipe recipe) {
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (!(g instanceof TriggerGlyph t) || t.isLink()) return false;
            if (id.equals(DuelGlyphs.IN_RESPONSE)) return true;
        }
        return false;
    }

    /** Esquece respostas de pendentes que já saíram da Pilha. */
    private static void prune() {
        for (Iterator<String> it = RESPONDED.iterator(); it.hasNext(); ) {
            String k = it.next();
            int bar = k.indexOf('|');
            try {
                if (SpellStack.get(Integer.parseInt(k.substring(0, bar))) == null) it.remove();
            } catch (NumberFormatException ex) {
                it.remove();
            }
        }
    }

    public static void clear() {
        RESPONDED.clear();
        depth = 0;
    }

    private InResponse() {}
}
