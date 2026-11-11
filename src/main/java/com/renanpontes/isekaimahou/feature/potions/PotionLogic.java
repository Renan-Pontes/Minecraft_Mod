package com.renanpontes.isekaimahou.feature.potions;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import org.joml.Vector3f;

/** Regras das poções: quanto cura/restaura, fluxo, purificação, recarga compartilhada e o visual. */
public final class PotionLogic {
    private PotionLogic() {}

    /** Cura instantânea (antes do fator de distância do respingo). Elixir Lendário = vida toda. */
    public static float healAmount(LivingEntity e, PotionTier tier, PotionForm form) {
        return (tier.healFlat + tier.healPct * e.getMaxHealth()) * form.instantMult;
    }

    /** Duração do fluxo em ticks. */
    public static int flowTicks(PotionTier tier, PotionForm form) {
        return tier.flowSeconds * 20 * form.flowMult;
    }

    /**
     * Aplica o frasco no alvo (só servidor). {@code factor} (0–1) = força do respingo pela distância (beber = 1).
     * Mana só vale para jogadores (é o recurso da classe deles: Mana, Vigor ou Foco).
     */
    public static void apply(LivingEntity target, PotionKind kind, PotionTier tier, PotionForm form, double factor) {
        if (target.level().isClientSide || factor <= 0 || !target.isAlive()) return;
        float f = (float) Math.min(1.0, factor);
        int flow = Math.max(20, Math.round(flowTicks(tier, form) * f));
        if (kind == PotionKind.HEALTH) {
            target.heal(healAmount(target, tier, form) * f);
            target.addEffect(new MobEffectInstance(PotionsContent.VITAL_FLOW, flow, tier.flowAmp, false, true, true));
            if (tier.legendary() && f >= 0.5F) cleanseHarmful(target);
        } else {
            if (!(target instanceof Player player)) return;
            MagicData d = MagicApi.get(player);
            d.addMana(d.maxMana() * tier.manaPct * form.instantMult * f);
            player.addEffect(new MobEffectInstance(PotionsContent.ARCANE_FLOW, flow, tier.flowAmp, false, true, true));
            if (tier.legendary() && f >= 0.5F) {
                player.removeEffect(CoreRegistries.MAGIC_EXHAUSTION);
                player.removeEffect(CoreRegistries.SILENCED);
            }
        }
        effects(target, kind, tier);
    }

    private static void cleanseHarmful(LivingEntity e) {
        List<Holder<MobEffect>> bad = new ArrayList<>();
        for (MobEffectInstance inst : e.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) bad.add(inst.getEffect());
        }
        for (Holder<MobEffect> h : bad) e.removeEffect(h);
    }

    /** Partículas e som no alvo (poucos pacotes por uso — barato). */
    public static void effects(LivingEntity e, PotionKind kind, PotionTier tier) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        int c = tier.color(kind);
        Vector3f rgb = new Vector3f(((c >> 16) & 0xFF) / 255F, ((c >> 8) & 0xFF) / 255F, (c & 0xFF) / 255F);
        double x = e.getX(), y = e.getY() + e.getBbHeight() * 0.5, z = e.getZ();
        int n = 6 + tier.ordinal() * 3;
        sl.sendParticles(new DustParticleOptions(rgb, 1.1F), x, y, z, n, 0.35, 0.5, 0.35, 0.02);
        if (kind == PotionKind.HEALTH) {
            sl.sendParticles(ParticleTypes.HEART, x, y + 0.6, z, 1 + tier.ordinal() / 2, 0.3, 0.2, 0.3, 0.0);
            sl.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.6F);
        } else {
            sl.sendParticles(ParticleTypes.ENCHANT, x, y + 0.4, z, n * 2, 0.4, 0.6, 0.4, 0.6);
            sl.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.2F);
        }
        if (tier.legendary()) {
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 30, 0.4, 0.7, 0.4, 0.35);
            sl.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
        }
    }

    // ------------------------------------------------------------------ recarga compartilhada

    /** Liga a recarga em TODOS os frascos do mod (para não espammar vida e mana em sequência). */
    public static void startCooldown(Player player, PotionTier tier) {
        int ticks = tier.cooldownSeconds * 20;
        for (DeferredItem<Item> item : PotionsContent.all()) player.getCooldowns().addCooldown(item.get(), ticks);
    }

    // ------------------------------------------------------------------ "já está cheio?" (nos dois lados)

    public static boolean healthFull(Player p) {
        return p.getHealth() >= p.getMaxHealth();
    }

    public static boolean resourceFull(Player p) {
        if (p.level().isClientSide) return ClientMagic.maxMana > 0 && ClientMagic.mana >= ClientMagic.maxMana;
        MagicData d = MagicApi.get(p);
        return d.mana() >= d.maxMana();
    }

    /** Beber agora desperdiçaria o frasco? (o Elixir Lendário ainda serve para purificar.) */
    public static boolean wasted(Player p, PotionKind kind, PotionTier tier) {
        if (tier.legendary()) {
            for (MobEffectInstance inst : p.getActiveEffects()) {
                if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) return false;
            }
        }
        return kind == PotionKind.HEALTH ? healthFull(p) : resourceFull(p);
    }
}
