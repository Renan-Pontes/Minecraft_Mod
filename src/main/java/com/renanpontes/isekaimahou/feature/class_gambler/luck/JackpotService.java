package com.renanpontes.isekaimahou.feature.class_gambler.luck;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbNet;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.CasinoDomainEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.glyph.JackpotTriggers;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseRanks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * JACKPOT — "sorte infinita" por alguns segundos (homenagem ao Hakari): toda rolagem sai no máximo, o recurso
 * (Mana/Vigor/Foco) fica cheio o tempo todo, a vida regenera forte, anda mais rápido e uma musiquinha de cassino toca.
 * Duração: 6 s + 1 s por rank da Casa (×1,5 dentro do próprio Cassino). Ao começar: +30 de carga de ultimate, fichas
 * de cassino, contador {@code gambler.jackpot} e os slots com o gatilho "Ao Jackpot" saem sozinhos.
 */
public final class JackpotService {
    public static final String COUNTER = "gambler.jackpot";
    private static final int BASE_TICKS = 120;
    private static final int PER_RANK_TICKS = 20;
    /** Vida curada por segundo (fração da vida máxima). */
    private static final float HEAL_PER_SECOND = 0.08F;

    /**
     * Jackpot IMORTAL (Domínio da Morte Ociosa, homenagem aos 4 min 11 s de imortalidade): 251 ticks + 2 s por rank da
     * Casa — o "4:11" virou ticks. Enquanto durar, golpes fatais não matam (a vida volta cheia) e a regeneração é total.
     */
    public static final int IMMORTAL_TICKS = 251;
    private static final int IMMORTAL_PER_RANK = 40;
    private static final float IMMORTAL_HEAL_PER_SECOND = 0.15F;

    private record State(long end, int total, boolean immortal) {}

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    public static boolean active(LivingEntity e) {
        State s = e == null ? null : ACTIVE.get(e.getUUID());
        return s != null && e.level().getGameTime() < s.end();
    }

    /** Ticks que faltam (0 = sem Jackpot). */
    public static int ticksLeft(LivingEntity e) {
        State s = ACTIVE.get(e.getUUID());
        return s == null ? 0 : (int) Math.max(0, s.end() - e.level().getGameTime());
    }

    /** Jackpot Imortal ativo? (golpes fatais não matam) */
    public static boolean immortal(LivingEntity e) {
        State s = e == null ? null : ACTIVE.get(e.getUUID());
        return s != null && s.immortal() && e.level().getGameTime() < s.end();
    }

    /** Liga o Jackpot. {@code bonusTicks} soma à duração (formas que dão mais tempo). */
    public static void start(ServerPlayer sp, int bonusTicks) {
        start(sp, bonusTicks, false);
    }

    /** Liga o Jackpot IMORTAL do Domínio da Morte Ociosa (sobrepõe um Jackpot comum em andamento). */
    public static void startImmortal(ServerPlayer sp) {
        start(sp, 0, true);
    }

    private static void start(ServerPlayer sp, int bonusTicks, boolean immortal) {
        ServerLevel level = sp.serverLevel();
        MagicData d = MagicApi.get(sp);
        int ticks = immortal ? IMMORTAL_TICKS + IMMORTAL_PER_RANK * HouseRanks.rank(d)
                : BASE_TICKS + PER_RANK_TICKS * HouseRanks.rank(d) + Math.max(0, bonusTicks);
        if (!immortal && CasinoDomainEntity.insideOwnCasino(sp)) ticks = Math.round(ticks * 1.5F);
        ACTIVE.put(sp.getUUID(), new State(level.getGameTime() + ticks, ticks, immortal));
        sp.addEffect(new MobEffectInstance(GbRegistries.JACKPOT, ticks, 0, false, true, true));
        LuckService.setFever(d, 0);
        d.addCharge(30F);
        d.fillMana();
        MagicApi.addCounter(sp, COUNTER, 1);
        int chips = 1 + HouseRanks.rank(d) / 2 + sp.getRandom().nextInt(2);
        Rewards.give(sp, new ItemStack(GbRegistries.CASINO_CHIP.get(), chips));
        if (immortal) {
            Rewards.banner(sp, Component.translatable("message.isekai_mahou.gambler.immortal_banner"), 0x4AFFB4);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.immortal_start",
                    String.format(java.util.Locale.ROOT, "%.1f", ticks / 20F), chips).withStyle(ChatFormatting.GREEN), true);
        } else {
            Rewards.banner(sp, Component.translatable("message.isekai_mahou.gambler.jackpot_banner"), 0xFF4AD2);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.jackpot_start", ticks / 20, chips)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        PacketDistributor.sendToPlayer(sp, new GbNet.JackpotState(ticks, ticks));
        GbFx.jackpotBurst(level, sp);
        JackpotTriggers.fire(sp);
    }

    /** Termina na hora (morte, saída do mundo, comando). */
    public static void stop(ServerPlayer sp, boolean quiet) {
        if (ACTIVE.remove(sp.getUUID()) == null) return;
        sp.removeEffect(GbRegistries.JACKPOT);
        PacketDistributor.sendToPlayer(sp, new GbNet.JackpotState(0, 0));
        if (!quiet) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.jackpot_end").withStyle(ChatFormatting.GRAY), true);
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.9F, 0.6F);
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (ACTIVE.isEmpty() || !(event.getEntity() instanceof ServerPlayer sp)) return;
        State s = ACTIVE.get(sp.getUUID());
        if (s == null) return;
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if (now >= s.end() || !sp.isAlive()) {
            stop(sp, !sp.isAlive());
            return;
        }
        // Sorte infinita: o recurso nunca baixa
        MagicData d = MagicApi.get(sp);
        if (d.mana() < d.maxMana()) d.fillMana();
        long age = s.total() - (s.end() - now);
        if (age % 20 == 0) {
            sp.heal(sp.getMaxHealth() * (s.immortal() ? IMMORTAL_HEAL_PER_SECOND : HEAL_PER_SECOND));
            sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, s.immortal() ? 3 : 1, true, false, true));
            if (s.immortal()) {
                // regeneração total: o corpo repara até as condições ruins
                for (MobEffectInstance i : new java.util.ArrayList<>(sp.getActiveEffects())) {
                    if (i.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) sp.removeEffect(i.getEffect());
                }
            }
        }
        JackpotMusic.play(level, sp, age, s.immortal());
        if (age % 2 == 0) GbFx.jackpotAura(level, sp, age);
        if (s.immortal() && age % 3 == 0) GbFx.immortalAura(level, sp, age);
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) stop(sp, true);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    /** Limpa entradas vencidas (servidor parando). */
    public static void clear() {
        ACTIVE.clear();
    }

    /** Remove estados vencidos de quem já não está no servidor (chamado a cada minuto pelo módulo). */
    public static void prune(long now) {
        for (Iterator<Map.Entry<UUID, State>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().end() + 200 < now) it.remove();
        }
    }

    private JackpotService() {}
}
