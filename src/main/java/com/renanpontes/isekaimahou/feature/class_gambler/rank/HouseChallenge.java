package com.renanpontes.isekaimahou.feature.class_gambler.rank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_gambler.GamblerLegends;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbNet;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * RODADA DA CASA — a prova de cada rank do Apostador. Com os requisitos cumpridos, o jogador APOSTA fichas de cassino
 * (1/2/4/7/12) e tem um tempo para abater N inimigos (6/10/15/20/30 em 60–120 s). Durante a rodada TODA magia rola
 * com "Dobrar a Aposta" (tudo ou nada). Venceu: sobe de rank, recebe a aposta em dobro, XP, glifos e itens.
 * Perdeu (tempo, morte ou saiu do mundo): perde a aposta e espera 2 minutos para tentar de novo.
 */
public final class HouseChallenge {
    private static final int RETRY_TICKS = 2400;

    private static final class Run {
        final int rank;
        final int target;
        final long end;
        int kills;

        Run(int rank, int target, long end) {
            this.rank = rank;
            this.target = target;
            this.end = end;
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Long> RETRY = new HashMap<>();

    public static boolean active(ServerPlayer sp) {
        return RUNS.containsKey(sp.getUUID());
    }

    /** Pedido da aba/baralho: confere tudo e começa a rodada do próximo rank. */
    public static void start(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.GAMBLER) {
            fail(sp, "message.isekai_mahou.gambler.challenge.not_gambler");
            return;
        }
        if (active(sp)) return;
        int next = HouseRanks.rank(d) + 1;
        HouseRanks.Rank r = HouseRanks.rank(next);
        if (r == null) {
            fail(sp, "message.isekai_mahou.gambler.challenge.maxed");
            return;
        }
        long now = sp.serverLevel().getGameTime();
        Long retry = RETRY.get(sp.getUUID());
        if (retry != null && now < retry && !sp.getAbilities().instabuild) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.challenge.retry", (retry - now) / 20 + 1)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        List<Component> missing = new ArrayList<>();
        if (!HouseRanks.meets(d, r, missing)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.challenge.missing").withStyle(ChatFormatting.RED), false);
            for (Component c : missing) sp.displayClientMessage(Component.literal(" • ").append(c).withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (!sp.getAbilities().instabuild) {
            if (countChips(sp.getInventory()) < r.stake()) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.challenge.no_chips", r.stake())
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            takeChips(sp.getInventory(), r.stake());
        }
        Run run = new Run(next, r.kills(), now + r.seconds() * 20L);
        RUNS.put(sp.getUUID(), run);
        Rewards.banner(sp, Component.translatable("message.isekai_mahou.gambler.challenge.banner", r.title()), 0xFFC94A);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gambler.challenge.start", r.stake(), r.kills(), r.seconds())
                .withStyle(ChatFormatting.GOLD));
        GbFx.sound(sp.serverLevel(), sp.position(), SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 0.8F);
        GbFx.sound(sp.serverLevel(), sp.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 0.6F, 1.4F);
        sync(sp, run, now, (byte) 0);
    }

    private static void fail(ServerPlayer sp, String key) {
        sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (RUNS.isEmpty() || !(event.getEntity() instanceof ServerPlayer sp)) return;
        Run run = RUNS.get(sp.getUUID());
        if (run == null) return;
        long now = sp.serverLevel().getGameTime();
        if (now >= run.end) {
            lose(sp, run);
            return;
        }
        if (now % 20 == 0) sync(sp, run, now, (byte) 0);
    }

    /** Abate de inimigo pelo jogador (direto, projétil, magia ou bicho dele) conta para a rodada. */
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer dead) {
            Run run = RUNS.get(dead.getUUID());
            if (run != null) lose(dead, run);
            return;
        }
        if (RUNS.isEmpty() || !(event.getEntity() instanceof Enemy)) return;
        ServerPlayer killer = killer(event.getSource().getEntity());
        if (killer == null) return;
        Run run = RUNS.get(killer.getUUID());
        if (run == null) return;
        run.kills++;
        long now = killer.serverLevel().getGameTime();
        GbFx.sound(killer.serverLevel(), killer.position(), SoundEvents.NOTE_BLOCK_PLING.value(), 0.6F, 0.8F + Math.min(1F, run.kills / (float) run.target));
        if (run.kills >= run.target) win(killer, run);
        else sync(killer, run, now, (byte) 0);
    }

    private static ServerPlayer killer(Entity src) {
        if (src instanceof ServerPlayer sp) return sp;
        if (src instanceof OwnableEntity own && own.getOwner() instanceof ServerPlayer sp) return sp;
        return null;
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RUNS.remove(event.getEntity().getUUID());
    }

    private static void lose(ServerPlayer sp, Run run) {
        RUNS.remove(sp.getUUID());
        long now = sp.serverLevel().getGameTime();
        RETRY.put(sp.getUUID(), now + RETRY_TICKS);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gambler.challenge.lose", run.kills, run.target)
                .withStyle(ChatFormatting.DARK_RED));
        GbFx.sound(sp.serverLevel(), sp.position(), SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
        GbFx.sound(sp.serverLevel(), sp.position(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.0F, 0.5F);
        sync(sp, run, now, (byte) 2);
    }

    private static void win(ServerPlayer sp, Run run) {
        RUNS.remove(sp.getUUID());
        HouseRanks.Rank r = HouseRanks.rank(run.rank);
        sync(sp, run, sp.serverLevel().getGameTime(), (byte) 1);
        if (r != null) rankUp(sp, r, true);
    }

    /** Sobe para o rank {@code r} e entrega as recompensas (também usado pelo comando de teste). */
    public static void rankUp(ServerPlayer sp, HouseRanks.Rank r, boolean refundStake) {
        MagicData d = MagicApi.get(sp);
        if (HouseRanks.rank(d) >= r.number()) return;
        MagicApi.addCounter(sp, HouseRanks.RANK, r.number() - HouseRanks.rank(d));
        MagicApi.addCounter(sp, "ritual_rank", 1);
        MagicApi.addCounter(sp, "ritual_rank.gambler", 1);
        MagicApi.giveXp(sp, r.xp(), "ritual");
        for (String g : r.glyphs()) MagicApi.learnGlyph(sp, IsekaiMahou.id(g));
        if (refundStake && r.stake() > 0) Rewards.give(sp, new ItemStack(GbRegistries.CASINO_CHIP.get(), r.stake() * 2));
        if (r.deckTier() > 0) Rewards.give(sp, new ItemStack(GbRegistries.deckOfTier(r.deckTier())));
        if (r.legends()) {
            for (String id : GamblerLegends.IDS) {
                ItemStack scroll = Legends.scroll(id);
                if (!scroll.isEmpty()) Rewards.give(sp, scroll);
            }
        }
        ItemStack icon = new ItemStack(GbRegistries.CASINO_CHIP.get());
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.gambler.rank", r.title()),
                Component.translatable("toast.isekai_mahou.gambler.rank.sub", r.number(), HouseRanks.MAX_RANK), icon, 0xFFC94A);
        Rewards.banner(sp, Component.translatable("message.isekai_mahou.gambler.rank_banner", r.title()), 0xFFC94A);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gambler.rank_up", r.title(), r.xp()).withStyle(ChatFormatting.GOLD));
        GbFx.jackpotBurst(sp.serverLevel(), sp);
    }

    private static void sync(ServerPlayer sp, Run run, long now, byte result) {
        PacketDistributor.sendToPlayer(sp, new GbNet.ChallengeState(run.kills, run.target, (int) Math.max(0, run.end - now), run.rank, result));
    }

    private static int countChips(Inventory inv) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(GbRegistries.CASINO_CHIP.get())) n += s.getCount();
        }
        return n;
    }

    private static void takeChips(Inventory inv, int amount) {
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(GbRegistries.CASINO_CHIP.get())) continue;
            int take = Math.min(amount, s.getCount());
            s.shrink(take);
            amount -= take;
        }
    }

    public static void clear() {
        RUNS.clear();
        RETRY.clear();
    }

    private HouseChallenge() {}
}
