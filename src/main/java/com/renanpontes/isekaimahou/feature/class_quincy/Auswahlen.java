package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.QuincyNpcEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Auswählen (homenagem): o Quincy absorve o poder de OUTROS Quincy para evoluir.
 * <ul>
 *   <li><b>NPC</b>: depois de vencer um Quincy do acampamento num duelo (ele se rende), clique nele e fique perto por
 *   3 s enquanto a luz dele flui para você. Ele se desfaz; você ganha 1 Auswählen, XP e recupera Foco.</li>
 *   <li><b>Jogador</b>: agachado e com a Cruz Quincy na mão, clique num jogador Quincy. Se ele ACEITAR (clique no
 *   chat ou {@code /mahou quincy accept}), você ganha 1 Auswählen e XP; ele também ganha XP, mas fica sem Reishi por um
 *   minuto (Fraqueza e metade do Foco). Cada jogador pode doar 1 vez por dia do mundo.</li>
 * </ul>
 */
public final class Auswahlen {
    private static final int NPC_CHANNEL = 60;
    private static final int REQUEST_TICKS = 20 * 30;
    private static final double NPC_RANGE = 6.0;
    private static final double PLAYER_RANGE = 10.0;

    private record Channel(UUID npc, ServerLevel level, int[] ticks) {}

    private record Request(UUID from, long expires) {}

    /** Jogador → canalização em andamento num NPC. */
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    /** Alvo do pedido → quem pediu. */
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();

    // =====================================================================================
    //  NPC
    // =====================================================================================
    public static void beginNpc(ServerPlayer sp, QuincyNpcEntity npc) {
        if (npc.absorbed()) return;
        if (CHANNELS.containsKey(sp.getUUID())) {
            sp.displayClientMessage(Quincy.msg("auswahlen_busy").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        for (Channel c : CHANNELS.values()) {
            if (c.npc().equals(npc.getUUID())) {
                sp.displayClientMessage(Quincy.msg("auswahlen_taken").withStyle(ChatFormatting.GRAY), true);
                return;
            }
        }
        CHANNELS.put(sp.getUUID(), new Channel(npc.getUUID(), sp.serverLevel(), new int[]{0}));
        sp.displayClientMessage(Quincy.msg("auswahlen_begin", npc.getDisplayName()).withColor(Quincy.RGB), true);
        QuincyFx.sound(sp.serverLevel(), npc.position(), SoundEvents.BEACON_ACTIVATE, 0.8F, 1.6F);
    }

    static void tick(MinecraftServer server) {
        if (!CHANNELS.isEmpty()) {
            Iterator<Map.Entry<UUID, Channel>> it = CHANNELS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Channel> e = it.next();
                ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
                Channel c = e.getValue();
                Entity ent = c.level().getEntity(c.npc());
                if (sp == null || !sp.isAlive() || sp.serverLevel() != c.level() || !(ent instanceof QuincyNpcEntity npc)
                        || !npc.yielded() || npc.absorbed()) {
                    if (sp != null) sp.displayClientMessage(Quincy.msg("auswahlen_broken").withStyle(ChatFormatting.RED), true);
                    it.remove();
                    continue;
                }
                if (sp.distanceToSqr(npc) > NPC_RANGE * NPC_RANGE) {
                    sp.displayClientMessage(Quincy.msg("auswahlen_too_far").withStyle(ChatFormatting.RED), true);
                    it.remove();
                    continue;
                }
                int t = ++c.ticks()[0];
                ServerLevel sl = c.level();
                Vec3 from = npc.position().add(0, 1.1, 0);
                Vec3 to = sp.position().add(0, 1.1, 0);
                if (t % 2 == 0) QuincyFx.stream(sl, from, to, 3);
                if (t % 10 == 0) {
                    QuincyFx.pentacle(sl, sp.position().add(0, 0.05, 0), 1.4, t * 0.05F);
                    QuincyFx.sound(sl, to, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.8F + t / (float) NPC_CHANNEL);
                }
                if (t >= NPC_CHANNEL) {
                    it.remove();
                    completeNpc(sp, npc);
                }
            }
        }
        if (!REQUESTS.isEmpty()) {
            long now = server.overworld().getGameTime();
            REQUESTS.values().removeIf(r -> r.expires() < now);
        }
    }

    private static void completeNpc(ServerPlayer sp, QuincyNpcEntity npc) {
        ServerLevel sl = sp.serverLevel();
        npc.markAbsorbed();
        int total = MagicApi.addCounter(sp, Quincy.AUSWAHLEN, 1);
        int xp = 90 + 30 * npc.variant();
        MagicApi.giveXp(sp, xp, "quincy");
        Quincy.gainFocus(sp, 0.3F);
        Quincy.addReishi(sp, 3);
        sp.heal(6.0F);
        Rewards.toast(sp, Quincy.msg("auswahlen_toast"), Quincy.msg("auswahlen_toast_sub", total), new ItemStack(QuincyRegistries.CROSS.get()),
                Quincy.RGB);
        sp.sendSystemMessage(Quincy.msg("auswahlen_done", npc.getDisplayName(), total, xp).withColor(Quincy.RGB));
        QuincyFx.pillar(sl, sp.position(), 3.0, 50);
        QuincyFx.sound(sl, sp.position(), SoundEvents.PLAYER_LEVELUP, 0.7F, 1.3F);
        QuincyRanks.hintIfReady(sp);
    }

    // =====================================================================================
    //  Jogador
    // =====================================================================================
    /** {@code from} (agachado, Cruz na mão) pede o Auswählen a {@code to}. */
    public static void request(ServerPlayer from, ServerPlayer to) {
        if (!Quincy.isQuincy(from)) return;
        if (!Quincy.isQuincy(to)) {
            from.displayClientMessage(Quincy.msg("auswahlen_target_not_quincy", to.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (MagicApi.dailyUses(to, Quincy.DAILY_GIVEN) >= 1) {
            from.displayClientMessage(Quincy.msg("auswahlen_target_tired", to.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long now = from.serverLevel().getGameTime();
        Request old = REQUESTS.get(to.getUUID());
        if (old != null && old.from().equals(from.getUUID()) && old.expires() > now) {
            from.displayClientMessage(Quincy.msg("auswahlen_pending").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        REQUESTS.put(to.getUUID(), new Request(from.getUUID(), now + REQUEST_TICKS));
        MutableComponent accept = Quincy.msg("auswahlen_accept_button").withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mahou quincy accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Quincy.msg("auswahlen_accept_hover"))));
        to.sendSystemMessage(Quincy.msg("auswahlen_request", from.getDisplayName()).withColor(Quincy.RGB).append(" ").append(accept));
        from.displayClientMessage(Quincy.msg("auswahlen_sent", to.getDisplayName()).withColor(Quincy.RGB), true);
        QuincyFx.sound(to.serverLevel(), to.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
    }

    /** {@code to} aceitou o último pedido. */
    public static boolean accept(ServerPlayer to) {
        Request r = REQUESTS.remove(to.getUUID());
        if (r == null || r.expires() < to.serverLevel().getGameTime()) {
            to.displayClientMessage(Quincy.msg("auswahlen_no_request").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ServerPlayer from = to.server.getPlayerList().getPlayer(r.from());
        if (from == null || from.serverLevel() != to.serverLevel() || from.distanceToSqr(to) > PLAYER_RANGE * PLAYER_RANGE) {
            to.displayClientMessage(Quincy.msg("auswahlen_too_far").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!MagicApi.tryUseDaily(to, Quincy.DAILY_GIVEN, 1)) {
            to.displayClientMessage(Quincy.msg("auswahlen_target_tired", to.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ServerLevel sl = to.serverLevel();
        int total = MagicApi.addCounter(from, Quincy.AUSWAHLEN, 1);
        MagicApi.giveXp(from, 120, "quincy");
        MagicApi.giveXp(to, 80, "quincy");
        Quincy.gainFocus(from, 0.3F);
        Quincy.takeAllReishi(to);
        MagicData td = MagicApi.get(to);
        td.setMana(td.mana() * 0.5F);
        to.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60, 0));
        QuincyFx.stream(sl, to.position().add(0, 1.1, 0), from.position().add(0, 1.1, 0), 30);
        QuincyFx.pillar(sl, from.position(), 3.0, 50);
        QuincyFx.sound(sl, from.position(), SoundEvents.PLAYER_LEVELUP, 0.7F, 1.3F);
        from.sendSystemMessage(Quincy.msg("auswahlen_player_gain", to.getDisplayName(), total).withColor(Quincy.RGB));
        to.sendSystemMessage(Quincy.msg("auswahlen_player_give", from.getDisplayName()).withStyle(ChatFormatting.GRAY));
        Rewards.toast(from, Quincy.msg("auswahlen_toast"), Quincy.msg("auswahlen_toast_sub", total), new ItemStack(QuincyRegistries.CROSS.get()),
                Quincy.RGB);
        QuincyRanks.hintIfReady(from);
        return true;
    }

    static void forget(UUID player) {
        CHANNELS.remove(player);
        REQUESTS.remove(player);
    }

    static void clear() {
        CHANNELS.clear();
        REQUESTS.clear();
    }

    private Auswahlen() {}
}
