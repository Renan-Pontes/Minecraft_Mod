package com.renanpontes.isekaimahou.feature.class_enchanter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterArmorItem;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterGauntletsItem;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterOutfit;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Regras dos ENCANTAMENTOS (o sistema do Encantador):
 * <ul>
 *   <li><b>Empilhamento com limite</b>: cada aplicação soma +1 nível (+1 por Amplificar) até o teto ({@link #stackCap});</li>
 *   <li><b>Capacidade</b>: um alvo carrega no máximo {@link #capacity} encantamentos diferentes — o que tiver menos tempo sai;</li>
 *   <li><b>Duração</b>: a base × Estender × {@link #durationMult} (bônus de classe, rank, traje completo e manoplas);</li>
 *   <li>Encantamentos de assinatura (Anti-Magia, Restrição Celestial) têm nível próprio ({@link #signatureCap}).</li>
 * </ul>
 * Quem não é Encantador (aprendeu por Lição) usa os números base: teto 3, capacidade 3, duração ×1, assinatura até II.
 */
public final class Enchants {
    /** Rank do Rito de Inscrição (0..5). */
    public static final String RANK = "ritual_rank.enchanter";
    public static final int MAX_RANK = 5;

    // Contadores das tarefas de rank
    public static final String C_APPLIED = "enchanter.applied";
    public static final String C_ALLY = "enchanter.ally_applied";
    public static final String C_NULLIFIED = "enchanter.nullified";
    public static final String C_KILLS = "enchanter.enchanted_kills";
    public static final String C_RESTRICTED = "enchanter.restricted_kills";
    public static final String C_OVERDRIVE = "enchanter.overdrive";
    public static final String C_BOSS = "enchanter.boss_kills";

    public static boolean isEnchanter(@Nullable LivingEntity e) {
        return e instanceof ServerPlayer sp && MagicApi.get(sp).path() == MagicPath.ENCHANTER;
    }

    /** Rank do Encantador (0..5), ou -1 se não for Encantador. */
    public static int rank(@Nullable LivingEntity e) {
        if (!isEnchanter(e)) return -1;
        return Math.max(0, Math.min(MAX_RANK, MagicApi.get((ServerPlayer) e).counter(RANK)));
    }

    private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> BOSSES = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "bosses"));

    /**
     * Chefe DE VERDADE (tag {@code c:bosses}, chefe de provação ou de arena) — não o limiar de vida do
     * {@code BossKit.isBossLike}, que a vida ×2 dos mobs da v1.0 faria criaturas comuns passarem.
     */
    public static boolean isBoss(@Nullable LivingEntity e) {
        if (e == null || e instanceof net.minecraft.world.entity.player.Player) return false;
        return e.getType().is(BOSSES) || com.renanpontes.isekaimahou.core.boss.BossKit.isTrialBoss(e)
                || com.renanpontes.isekaimahou.core.api.BossArena.isArenaBoss(e);
    }

    public static boolean holdsGauntlets(@Nullable LivingEntity e) {
        return e != null && e.getMainHandItem().getItem() instanceof EnchanterGauntletsItem;
    }

    /** Bônus de classe "encantamentos duram bem mais": ×1,75 (+5% por rank), traje completo e manoplas na mão. */
    public static float durationMult(@Nullable LivingEntity caster) {
        if (caster == null) return 1F;
        float m = 1F;
        int rank = rank(caster);
        if (rank >= 0) {
            m = 1.75F + 0.05F * rank;
            if (holdsGauntlets(caster)) m += 0.10F;
        }
        EnchanterOutfit set = EnchanterArmorItem.fullSet(caster);
        if (set != null) m += set.setDurationBonus();
        return m;
    }

    /** Teto de níveis empilhados de um encantamento comum. */
    public static int stackCap(@Nullable LivingEntity caster) {
        int rank = rank(caster);
        return rank < 0 ? 3 : 4 + (rank >= 3 ? 1 : 0);
    }

    /** Quantos encantamentos diferentes um alvo carrega ao mesmo tempo. */
    public static int capacity(@Nullable LivingEntity caster) {
        int rank = rank(caster);
        return rank < 0 ? 3 : 4 + rank / 2;
    }

    /** Nível máximo da Anti-Magia e da Restrição Celestial: Encantador I..V conforme o rank; outros até II. */
    public static int signatureCap(@Nullable LivingEntity caster) {
        int rank = rank(caster);
        return rank < 0 ? 2 : Math.min(5, 1 + (rank * 4 + 4) / 5);
    }

    /** Níveis grátis de assinatura ao conjurar (Encantador: rank/2). */
    public static int signatureBonus(@Nullable LivingEntity caster) {
        int rank = rank(caster);
        return rank < 0 ? 0 : rank / 2;
    }

    /** Alcance do Elo de Encantamento (bônus de classe: alcança aliados mais longe). */
    public static double linkRange(@Nullable LivingEntity caster) {
        return isEnchanter(caster) ? 36.0 : 24.0;
    }

    public static int duration(@Nullable LivingEntity caster, SpellStats stats, int baseTicks) {
        return Math.max(20, Math.round(stats.duration(baseTicks) * durationMult(caster)));
    }

    public static boolean isEnchantment(Holder<MobEffect> h) {
        for (Holder<MobEffect> e : EnchRegistries.enchantments()) if (h.is(e)) return true;
        return false;
    }

    /** Nível (1..) do encantamento no alvo, 0 se não tiver. */
    public static int level(LivingEntity e, Holder<MobEffect> eff) {
        MobEffectInstance i = e.getEffect(eff);
        return i == null ? 0 : i.getAmplifier() + 1;
    }

    public static List<MobEffectInstance> active(LivingEntity e) {
        List<MobEffectInstance> out = new ArrayList<>();
        for (Holder<MobEffect> h : EnchRegistries.enchantments()) {
            MobEffectInstance i = e.getEffect(h);
            if (i != null) out.add(i);
        }
        return out;
    }

    public static boolean hasAny(LivingEntity e) {
        for (Holder<MobEffect> h : EnchRegistries.enchantments()) if (e.hasEffect(h)) return true;
        for (Holder<MobEffect> h : EnchRegistries.transformations()) if (e.hasEffect(h)) return true;
        return false;
    }

    /**
     * Aplica (empilha) um encantamento comum. Devolve o nível final.
     *
     * @param baseTicks duração base (antes de Estender e do bônus de classe)
     */
    public static int apply(SpellContext ctx, LivingEntity target, Holder<MobEffect> eff, SpellStats stats, int baseTicks) {
        return apply(ctx.caster(), target, eff, 1 + Math.max(0, stats.amplify), stackCap(ctx.caster()), duration(ctx.caster(), stats, baseTicks));
    }

    /** Versão crua: soma {@code add} níveis até {@code cap}, com {@code ticks} de duração (nunca encurta). */
    public static int apply(@Nullable LivingEntity caster, LivingEntity target, Holder<MobEffect> eff, int add, int cap, int ticks) {
        MobEffectInstance cur = target.getEffect(eff);
        int lvl = cur == null ? 0 : cur.getAmplifier() + 1;
        int newLvl = Math.max(lvl, Math.min(cap, lvl + Math.max(1, add)));
        int dur = cur == null ? ticks : Math.max(ticks, cur.getDuration());
        if (cur == null) ensureCapacity(target, eff, capacity(caster));
        else if (newLvl == lvl && dur <= cur.getDuration()) {
            countApplied(caster, target, eff);
            return lvl;
        }
        target.addEffect(new MobEffectInstance(eff, dur, newLvl - 1, false, false, true), caster);
        countApplied(caster, target, eff);
        return newLvl;
    }

    /** Abre espaço: tira o encantamento com menos tempo restante enquanto o alvo estiver no limite. */
    public static void ensureCapacity(LivingEntity target, Holder<MobEffect> incoming, int capacity) {
        List<MobEffectInstance> list = new ArrayList<>();
        for (MobEffectInstance i : active(target)) if (!i.getEffect().is(incoming)) list.add(i);
        while (list.size() >= Math.max(1, capacity)) {
            MobEffectInstance weakest = list.get(0);
            for (MobEffectInstance i : list) if (i.getDuration() < weakest.getDuration()) weakest = i;
            list.remove(weakest);
            target.removeEffect(weakest.getEffect());
            if (target.level() instanceof ServerLevel sl) {
                EnchFx.sound(sl, target.position(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.6F, 1.6F);
                sl.sendParticles(EnchFx.dust(EnchFx.WHITE, 0.6F), target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                        8, 0.3, 0.4, 0.3, 0.0);
            }
            if (target instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.capacity",
                        weakest.getEffect().value().getDisplayName(), capacity).withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** Estende TODOS os encantamentos do alvo em {@code ticks} (até {@code maxTicks} cada) sem mudar o nível. */
    public static void extendAll(LivingEntity e, int ticks, int maxTicks) {
        for (MobEffectInstance i : active(e)) {
            if (i.getDuration() >= maxTicks || i.isInfiniteDuration()) continue;
            int d = Math.min(maxTicks, i.getDuration() + ticks);
            e.addEffect(new MobEffectInstance(i.getEffect(), d, i.getAmplifier(), i.isAmbient(), i.isVisible(), i.showIcon()), e);
        }
    }

    // ------------------------------------------------------------------ contadores das tarefas (com respiro por alvo)
    private static final Map<Long, Long> LAST_COUNT = new HashMap<>();

    static void countApplied(@Nullable LivingEntity caster, LivingEntity target, Holder<MobEffect> eff) {
        if (!(caster instanceof ServerPlayer sp) || !isEnchanter(sp)) return;
        long now = sp.serverLevel().getGameTime();
        long key = sp.getUUID().getLeastSignificantBits() * 31L + target.getUUID().getMostSignificantBits() * 17L
                + eff.unwrapKey().map(k -> k.location().hashCode()).orElse(0);
        Long last = LAST_COUNT.get(key);
        if (last != null && now - last < 60) return;
        if (LAST_COUNT.size() > 2048) {
            Iterator<Map.Entry<Long, Long>> it = LAST_COUNT.entrySet().iterator();
            while (it.hasNext()) if (now - it.next().getValue() > 200) it.remove();
        }
        LAST_COUNT.put(key, now);
        boolean ally = target != sp;
        MagicApi.addCounter(sp, C_APPLIED, ally ? 2 : 1);
        if (ally) MagicApi.addCounter(sp, C_ALLY, 1);
        if (eff.is(EnchRegistries.OVERDRIVE)) MagicApi.addCounter(sp, C_OVERDRIVE, 1);
    }

    public static void clear() {
        LAST_COUNT.clear();
    }

    private Enchants() {}
}
