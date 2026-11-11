package com.renanpontes.isekaimahou.feature.arcane_duel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellCounteredEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.arcane_duel.glyph.CounterEffects;
import com.renanpontes.isekaimahou.feature.arcane_duel.glyph.DuelGlyphs;
import com.renanpontes.isekaimahou.feature.arcane_duel.glyph.JudgmentEffects;
import com.renanpontes.isekaimahou.feature.arcane_duel.glyph.WardEffects;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Regras dos glifos de duelo que vivem em eventos (dano de magia, Pilha, efeitos, morte). Tudo no servidor. */
public final class DuelEvents {
    /** Resguardo: "atacante|alvo|magia" → {tick da decisão, pagou (1) ou não (0)} — um golpe multi-acerto paga uma vez. */
    private static final Map<String, long[]> WARD_TAX = new HashMap<>();
    /** Efeitos rápidos (faísca do Resguardo, clangor da égide) com limite por entidade. */
    private static final Map<Integer, Long> FLASH = new HashMap<>();
    /**
     * Toque Mortal infla o dano para matar; aqui fica o dano ORIGINAL (id do alvo → {dano, tick}) para o Atropelar não
     * passar adiante o dano inflado (como no Magic: com Toque Mortal, só o golpe original atropela).
     */
    private static final Map<Integer, Executed> DEATHTOUCH_ORIGINAL = new HashMap<>();

    private record Executed(float amount, long tick) {}
    /** Imposto do Resguardo (v0.8: 20; ×{@link ResourceTraining#MANA_SCALE} com a mana da v1.0). */
    public static final int WARD_COST = Math.round(20 * ResourceTraining.MANA_SCALE);
    private static final int WARD_WINDOW = 100;

    // =====================================================================================
    //  Dano de magia: Resguardo (imposto), Toque Mortal, Atropelar
    // =====================================================================================

    public static void onSpellDamagePre(SpellDamageEvent.Pre e) {
        LivingEntity t = e.target();
        SpellContext ctx = e.ctx();
        if (t.hasEffect(DuelRegistry.HEXPROOF) && !ward(e, ctx, t)) return;
        if (!DuelUtil.augmented(ctx, e.stats(), DuelGlyphs.DEATHTOUCH) || t instanceof Player) return;
        ServerLevel level = ctx.level();
        if (DuelUtil.isBoss(t)) {
            e.setAmount(e.getAmount() * 1.15F);
            if (flash(t, level, 10)) level.sendParticles(ParticleTypes.SCULK_SOUL, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(),
                    4, 0.3, 0.3, 0.3, 0.02);
            return;
        }
        if (t.getHealth() - e.getAmount() <= t.getMaxHealth() * 0.25F) {
            if (DEATHTOUCH_ORIGINAL.size() > 256) DEATHTOUCH_ORIGINAL.clear();
            DEATHTOUCH_ORIGINAL.put(t.getId(), new Executed(e.getAmount(), level.getGameTime()));
            e.setAmount(Math.max(e.getAmount(), t.getHealth() + t.getAbsorptionAmount() + 1000F));
            Vec3 c = DuelFx.center(t);
            DuelFx.spawn(level, com.renanpontes.isekaimahou.feature.arcane_duel.entity.DuelFxEntity.SHATTER, c,
                    Math.max(0.8F, t.getBbHeight() * 0.55F), 18, DuelFx.DEATH, 0F);
            level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.04);
            level.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.02);
            DuelFx.sound(level, c, SoundEvents.WITHER_SKELETON_AMBIENT, 0.9F, 0.6F);
            DuelFx.sound(level, c, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.25F, 1.8F);
        }
    }

    /**
     * Resguardo ("Ward" do Magic): magia de JOGADOR inimigo que atinge quem tem o efeito cobra {@link #WARD_COST} de
     * mana do atacante; sem mana, a magia é anulada. @return false se anulou (o evento já foi cancelado)
     */
    private static boolean ward(SpellDamageEvent.Pre e, SpellContext ctx, LivingEntity t) {
        LivingEntity attacker = ctx.caster();
        if (!(attacker instanceof ServerPlayer sp) || sp == t || ctx.isAlly(t) || DuelUtil.isCreative(sp) || sp instanceof FakePlayer) {
            return true;
        }
        long now = ctx.level().getGameTime();
        // Uma cobrança por conjuração e alvo (zona/canal que acerta várias vezes paga de novo só a cada 5 s)
        String key = sp.getUUID() + "|" + t.getId() + "|" + System.identityHashCode(ctx.rootSpell());
        long[] decision = WARD_TAX.get(key);
        if (decision == null || now - decision[0] > WARD_WINDOW) {
            MagicData d = MagicApi.get(sp);
            boolean paid = d.consumeMana(WARD_COST);
            decision = new long[] {now, paid ? 1 : 0};
            WARD_TAX.put(key, decision);
            if (WARD_TAX.size() > 256) WARD_TAX.values().removeIf(v -> now - v[0] > WARD_WINDOW);
            sp.displayClientMessage(Component.translatable(paid ? "message.isekai_mahou.arcane_duel.hexproof.taxed"
                    : "message.isekai_mahou.arcane_duel.hexproof.blocked", WARD_COST, t.getDisplayName())
                    .withStyle(paid ? ChatFormatting.AQUA : ChatFormatting.RED), true);
            Vec3 c = DuelFx.center(t);
            ServerLevel level = ctx.level();
            level.sendParticles(DuelFx.dust(DuelFx.HEX, 1.2F), c.x, c.y, c.z, 16, 0.5, 0.6, 0.5, 0);
            DuelFx.sound(level, c, paid ? SoundEvents.AMETHYST_BLOCK_HIT : SoundEvents.SHIELD_BLOCK, 0.8F, 1.5F);
        }
        if (decision[1] == 0) {
            e.setCanceled(true);
            return false;
        }
        return true;
    }

    public static void onSpellDamagePost(SpellDamageEvent.Post e) {
        Executed original = DEATHTOUCH_ORIGINAL.remove(e.target().getId());
        if (!e.killed() || e.overkill() <= 0.5F || !DuelUtil.augmented(e.ctx(), e.stats(), DuelGlyphs.TRAMPLE)) return;
        SpellContext ctx = e.ctx();
        ServerLevel level = ctx.level();
        LivingEntity from = e.target();
        float left = e.overkill();
        // Toque Mortal inflou o dano para matar: só o dano original passa por cima (não os +1000 da execução)
        if (original != null && original.tick() == level.getGameTime()) left = Math.min(left, original.amount());
        DamageSource src = ctx.damageSource(null);
        Set<UUID> hit = new HashSet<>();
        hit.add(from.getUUID());
        // Até 3 saltos: o que sobra continua passando por cima
        for (int hop = 0; hop < 3 && left > 0.5F; hop++) {
            LivingEntity next = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity cand : ctx.enemiesAround(from.position(), 5.0)) {
                if (hit.contains(cand.getUUID()) || !cand.isAlive()) continue;
                double d = cand.distanceToSqr(from);
                if (d < best) {
                    best = d;
                    next = cand;
                }
            }
            if (next == null) break;
            hit.add(next.getUUID());
            Vec3 a = DuelFx.center(from), b = DuelFx.center(next);
            DuelFx.line(level, a, b, ParticleTypes.CRIT, 0.35);
            DuelFx.line(level, a, b, DuelFx.dust(0xFF7A3A, 1.4F), 0.5);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, b.x, b.y, b.z, 1, 0, 0, 0, 0);
            DuelFx.sound(level, b, SoundEvents.RAVAGER_STEP, 1.0F, 0.8F + 0.15F * hop);
            float before = next.getHealth() + next.getAbsorptionAmount();
            next.invulnerableTime = 0;
            next.hurt(src, left);   // hurt direto: não reaplica reações nem dispara outro Atropelar
            if (next.isAlive()) break;
            left -= before;
            from = next;
        }
    }

    // =====================================================================================
    //  Resguardo: imune a status negativos e a contramágica
    // =====================================================================================

    public static void onEffectApplicable(MobEffectEvent.Applicable e) {
        LivingEntity t = e.getEntity();
        if (!t.hasEffect(DuelRegistry.HEXPROOF)) return;
        MobEffectInstance inst = e.getEffectInstance();
        if (inst == null || inst.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;
        if (DuelUtil.isRuleEffect(inst.getEffect())) return;   // Exaustão (Domínio) e Procurado (cidades): valem sempre
        e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        if (t.level() instanceof ServerLevel level && flash(t, level, 8)) {
            Vec3 c = DuelFx.center(t);
            level.sendParticles(DuelFx.dust(DuelFx.HEX, 1.0F), c.x, c.y, c.z, 10, 0.45, 0.5, 0.45, 0);
            DuelFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_HIT, 0.6F, 1.8F);
        }
    }

    public static void onCounterAttempt(SpellCounteredEvent.Attempt e) {
        LivingEntity c = e.getPending().caster();
        if (c != null && c.hasEffect(DuelRegistry.HEXPROOF)) e.setCanceled(true);
    }

    /** Jogador que contraria um CHEFE: letreiro, som de vitória e tremor (os contadores o núcleo já grava). */
    public static void onCountered(SpellCounteredEvent e) {
        if (!e.wasBoss() || !(e.getCounteredBy() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        SpellStack.Pending p = e.getPending();
        LivingEntity boss = p.caster();
        Component bossName = boss != null ? boss.getDisplayName() : p.name();
        Component text = Component.translatable("announce.isekai_mahou.arcane_duel.boss_countered", sp.getDisplayName(), bossName);
        Announce.send(boss != null ? boss : sp, text, 0xC9A0FF, Announce.STYLE_BOSS);
        sp.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.3F);
        ServerLevel level = e.getLevel();
        Vec3 at = boss != null ? DuelFx.center(boss) : p.pos();
        DuelFx.spawn(level, com.renanpontes.isekaimahou.feature.arcane_duel.entity.DuelFxEntity.SHATTER, at,
                boss != null ? Math.max(2F, boss.getBbHeight() * 0.8F) : 2.5F, 26, DuelFx.GOLD, sp.getYRot());
        DuelFx.sound(level, at, SoundEvents.BELL_RESONATE, 2.0F, 0.8F);
        DuelFx.sound(level, at, SoundEvents.ANVIL_LAND, 0.8F, 0.6F);
        BossKit.shake(level, at, 32, p.tier() == SpellStack.Tier.ULTIMATE ? 1.4F : 0.8F, 14);
    }

    // =====================================================================================
    //  Indestrutível
    // =====================================================================================

    private static boolean bypassesIndestructible(DamageSource src) {
        return src.is(DamageTypes.FELL_OUT_OF_WORLD) || src.is(DamageTypes.GENERIC_KILL);
    }

    public static void onLivingDamagePre(LivingDamageEvent.Pre e) {
        LivingEntity t = e.getEntity();
        if (!t.hasEffect(DuelRegistry.INDESTRUCTIBLE) || bypassesIndestructible(e.getSource())) return;
        float max = t.getHealth() + t.getAbsorptionAmount() - 1F;
        if (e.getNewDamage() <= max) return;
        e.setNewDamage(Math.max(0F, max));
        if (t.level() instanceof ServerLevel level && flash(t, level, 6)) {
            Vec3 c = DuelFx.center(t);
            level.sendParticles(ParticleTypes.WAX_ON, c.x, c.y, c.z, 14, 0.4, 0.6, 0.4, 0.2);
            level.sendParticles(DuelFx.dust(DuelFx.GOLD, 1.4F), c.x, c.y, c.z, 12, 0.5, 0.7, 0.5, 0);
            DuelFx.sound(level, c, SoundEvents.ANVIL_LAND, 0.5F, 1.9F);
        }
    }

    /** Rede de segurança (dano que não passa pelo LivingDamageEvent): fica com 1 de vida. */
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity t = e.getEntity();
        if (!t.hasEffect(DuelRegistry.INDESTRUCTIBLE) || bypassesIndestructible(e.getSource())) return;
        e.setCanceled(true);
        t.setHealth(1F);
    }

    // =====================================================================================
    //  Exilar: sem loot nem XP
    // =====================================================================================

    public static void onDrops(LivingDropsEvent e) {
        if (e.getEntity().getPersistentData().getBoolean(JudgmentEffects.EXILED)) e.setCanceled(true);
    }

    public static void onXpDrop(LivingExperienceDropEvent e) {
        if (e.getEntity().getPersistentData().getBoolean(JudgmentEffects.EXILED)) e.setCanceled(true);
    }

    // =====================================================================================
    //  Passo no Tempo: o slot que o conjurou fica 120 s em recarga
    // =====================================================================================

    public static void onCastPost(SpellCastEvent.Post e) {
        if (!(e.getCaster() instanceof ServerPlayer p) || !e.getContext().isPlayerCast()) return;
        ParsedSpell spell = e.getContext().rootSpell();
        if (!DuelUtil.hasEffect(spell, WardEffects.TimeWalkEffect.ID)) return;
        MagicData d = MagicApi.get(p);
        Element el = e.getContext().primaryElement();
        int loadout = d.activeLoadout(el);
        long now = p.serverLevel().getGameTime();
        String key = findSlotKey(d, el, loadout, e.getContext().recipe(), now + spell.cooldown());
        if (key == null) return;
        long until = now + WardEffects.TimeWalkEffect.OWN_COOLDOWN;
        if (d.cooldowns.getOrDefault(key, 0L) < until) {
            d.cooldowns.put(key, until);
            PacketDistributor.sendToPlayer(p, new CorePayloads.Cooldown(key, WardEffects.TimeWalkEffect.OWN_COOLDOWN));
        }
        CompoundTag pd = WardEffects.TimeWalkEffect.data(p);
        pd.putString(WardEffects.TimeWalkEffect.KEY, key);
        pd.putLong(WardEffects.TimeWalkEffect.KEY_UNTIL, until);
    }

    /** O slot com esta receita cuja recarga acabou de ser marcada (o núcleo grava antes do Post). */
    private static String findSlotKey(MagicData d, Element el, int loadout, SpellRecipe recipe, long expected) {
        String fallback = null;
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellSlot s = d.peekSlot(el, loadout, slot);
            boolean has = false;
            for (SpellRecipe r : List.copyOf(s.all().values())) if (r.equals(recipe)) has = true;
            if (!has) continue;
            String key = MagicData.slotKey(el, loadout, slot);
            Long cd = d.cooldowns.get(key);
            if (cd != null && cd == expected) return key;
            if (fallback == null) fallback = key;
        }
        return fallback;
    }

    // =====================================================================================

    /** Limita efeitos rápidos por entidade (no máx. 1 a cada {@code gap} ticks). */
    private static boolean flash(LivingEntity e, ServerLevel level, int gap) {
        long now = level.getGameTime();
        Long last = FLASH.get(e.getId());
        if (last != null && now - last < gap) return false;
        FLASH.put(e.getId(), now);
        if (FLASH.size() > 512) {
            for (Iterator<Map.Entry<Integer, Long>> it = FLASH.entrySet().iterator(); it.hasNext(); ) {
                if (now - it.next().getValue() > 40) it.remove();
            }
        }
        return true;
    }

    public static void clear() {
        WARD_TAX.clear();
        FLASH.clear();
        DEATHTOUCH_ORIGINAL.clear();
        DuelFx.clear();
        DuelUtil.clear();
        InResponse.clear();
        CounterEffects.clear();
        JudgmentEffects.clear();
    }

    private DuelEvents() {}
}
