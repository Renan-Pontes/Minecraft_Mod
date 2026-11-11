package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Comportamento do gatilho "Ao Jackpot": quando o Jackpot começa, cada slot do loadout ativo que começa com o
 * gatilho sai sozinho (um a cada 6 ticks), virado para o inimigo mais próximo (até 20 blocos). Segue as regras de uma
 * conjuração normal (receita válida, recarga, Silêncio); o recurso está cheio pela sorte infinita.
 */
public final class JackpotTriggers {
    private static final double AIM_RANGE = 20;
    private static int autoDepth;

    /** Apertar a tecla de um slot com "Ao Jackpot" não conjura (só sai no Jackpot). */
    public static void onPre(SpellCastEvent.Pre event) {
        if (autoDepth > 0 || !(event.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (!event.getContext().isPlayerCast()) return;
        if (event.getContext().spell().hasTrigger(GamblerGlyphs.TRIGGER_JACKPOT_ID.getPath())) {
            event.setCanceled(true);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.trigger_only").withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** O Jackpot começou: agenda os slots com o gatilho. */
    public static void fire(ServerPlayer p) {
        if (p.hasEffect(CoreRegistries.SILENCED)) return;
        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        int delay = 4;
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(loadout, slot).main();
            if (recipe == null || recipe.isEmpty() || !leadsWithJackpot(recipe)) continue;
            int s = slot;
            SpellScheduler.schedule(delay, () -> {
                if (!p.isRemoved() && p.isAlive()) autoCast(p, loadout, s, recipe);
            });
            delay += 6;
        }
    }

    private static void autoCast(ServerPlayer p, int loadout, int slot, SpellRecipe recipe) {
        if (p.isSpectator() || p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) return;
        MagicData d = MagicApi.get(p);
        if (d.activeLoadout() != loadout) return;
        boolean creative = p.getAbilities().instabuild;
        Element el = SpellCost.primaryElement(recipe);
        SpellParser.Result r = SpellParser.parse(recipe, el, creative ? null : d);
        if (!r.ok()) return;
        ParsedSpell spell = r.spell();
        if (spell.isUltimate() != (slot == Progression.ULTIMATE_SLOT)) return;
        if (spell.isUltimate() && !creative && d.charge() < Progression.MAX_CHARGE) return;
        String cdKey = com.renanpontes.isekaimahou.core.MagicData.slotKey(loadout, slot);
        if (p.serverLevel().getGameTime() < d.cooldowns.getOrDefault(cdKey, 0L)) return;
        if (!creative && d.mana() < spell.manaCost()) return;

        LivingEntity target = nearestEnemy(p);
        float yRot = p.getYRot(), xRot = p.getXRot(), headRot = p.getYHeadRot();
        if (target != null) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
            float pitch = (float) -(Mth.atan2(to.y, horiz) * Mth.RAD_TO_DEG);
            p.setYRot(yaw);
            p.setXRot(Mth.clamp(pitch, -90F, 90F));
            p.setYHeadRot(yaw);
        }
        autoDepth++;
        boolean ok;
        try {
            ok = SpellCaster.performCast(p, d, spell.primary(), recipe, spell, cdKey, false);
        } finally {
            autoDepth--;
            if (target != null) {
                p.setYRot(yRot);
                p.setXRot(xRot);
                p.setYHeadRot(headRot);
            }
        }
        if (ok) GbFx.sound(p.serverLevel(), p.position(), SoundEvents.NOTE_BLOCK_BELL.value(), 0.8F, 1.6F);
    }

    @Nullable
    private static LivingEntity nearestEnemy(ServerPlayer p) {
        LivingEntity best = null;
        double bestD = AIM_RANGE * AIM_RANGE;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(AIM_RANGE),
                e -> e != p && e.isAlive() && e instanceof net.minecraft.world.entity.monster.Enemy && p.hasLineOfSight(e))) {
            double d = e.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** A receita começa com "Ao Jackpot" (entre os gatilhos antes da forma)? */
    public static boolean leadsWithJackpot(SpellRecipe recipe) {
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (!(g instanceof TriggerGlyph)) return false;
            if (GamblerGlyphs.TRIGGER_JACKPOT_ID.equals(id)) return true;
        }
        return false;
    }

    public static void clear() {
        autoDepth = 0;
    }

    private JackpotTriggers() {}
}
