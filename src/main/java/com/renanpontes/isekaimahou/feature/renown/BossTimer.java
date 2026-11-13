package com.renanpontes.isekaimahou.feature.renown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.social.SocialEvent;
import com.renanpontes.isekaimahou.core.social.SocialRules;
import com.renanpontes.isekaimahou.feature.renown.RenownData.BossRecord;
import com.renanpontes.isekaimahou.feature.renown.RenownData.PlayerRow;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork.Timer;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * <b>Recorde de tempo por chefe</b> — "isso é legal": cronômetro da luta e o melhor tempo por chefe no ranking.
 * <ul>
 *   <li>A luta começa quando a arena do chefe é selada ({@code SocialEvent.ArenaSealed}: o chefe mirou ou foi ferido
 *       por um jogador) — ou, para chefes sem arena, no primeiro golpe de um jogador.</li>
 *   <li>Quem luta (ou está perto) vê um cronômetro discreto ao lado da barra do chefe.</li>
 *   <li>O chefe morreu: o tempo vale para todos que causaram dano. Fica guardado o melhor de cada jogador e o melhor
 *       do servidor (com os nomes do grupo).</li>
 *   <li>A luta foi abandonada (arena liberada): o cronômetro some e a próxima tentativa começa do zero.</li>
 * </ul>
 * Para o tempo valer, os jogadores precisam ter causado a maior parte da vida do chefe NESTA luta (nada de deixar o
 * chefe quase morto, sair e voltar; nem {@code /kill}) e ninguém do grupo pode estar no criativo. O Abismo Infinito
 * fica de fora (lá os chefes são Sombras, com o ranking próprio dos andares).
 */
final class BossTimer {
    /** Parte da vida do chefe que os jogadores têm que ter tirado nesta luta para o tempo valer. */
    private static final float MIN_DAMAGE_SHARE = 0.6F;
    /** Chefe sem arena: tanto tempo sem dano de jogador e a luta é dada por abandonada. */
    private static final int IDLE_TICKS = 600;
    /** Quem está até esta distância do chefe vê o cronômetro (mesmo sem bater: curandeiros, por exemplo). */
    private static final double WATCH_RANGE = 48;
    private static final ResourceLocation ENDLESS_ABYSS = IsekaiMahou.id("endless_abyss");

    private static final class Fight {
        LivingEntity boss;
        final String type;
        final int start;
        final boolean arena;
        final float maxHealth;
        int lastCombat;
        boolean tainted;
        final Map<UUID, Float> damage = new HashMap<>();
        final Set<UUID> watching = new HashSet<>();

        Fight(LivingEntity boss, int now, boolean arena) {
            this.boss = boss;
            this.type = BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString();
            this.start = now;
            this.lastCombat = now;
            this.arena = arena;
            this.maxHealth = boss.getMaxHealth();
        }
    }

    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();

    private BossTimer() {}

    // ------------------------------------------------------------------------------------------------ começo
    static void onSealed(SocialEvent.ArenaSealed e) {
        start(e.getBoss(), true);
    }

    private static Fight start(LivingEntity boss, boolean arena) {
        if (!(boss.level() instanceof ServerLevel level)) return null;
        if (level.dimension().location().equals(ENDLESS_ABYSS)) return null;
        Fight f = new Fight(boss, level.getServer().getTickCount(), arena);
        FIGHTS.put(boss.getUUID(), f);
        watchNearby(level, f);
        return f;
    }

    static void onDamage(LivingDamageEvent.Post e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide() || e.getNewDamage() <= 0) return;
        Fight f = FIGHTS.get(victim.getUUID());
        // Sem luta em curso, só interessa golpe de alguém num chefe: dano do ambiente (a maioria, em fazendas de mob)
        // sai na primeira comparação, e a consulta ao atacante (cadeia de donos) fica para depois do filtro de chefe
        if (f == null && (e.getSource().getEntity() == null || !isBoss(victim))) return;
        ServerPlayer attacker = SocialRules.playerBehind(e.getSource().getEntity());
        if (attacker == null) return;
        if (f == null) {
            f = start(victim, false);
            if (f == null) return;
        }
        f.boss = victim;
        f.lastCombat = attacker.server.getTickCount();
        f.damage.merge(attacker.getUUID(), e.getNewDamage(), Float::sum);
        if (attacker.isCreative() || attacker.isSpectator()) f.tainted = true;
        watch(f, attacker);
    }

    /** Chefe sem arena selada também conta (tag {@code c:bosses}). */
    private static boolean isBoss(LivingEntity e) {
        return e.isAlive() && (e.getType().is(Tags.EntityTypes.BOSSES) || BossArena.isArenaBoss(e));
    }

    // ------------------------------------------------------------------------------------------------ cronômetro
    private static void watch(Fight f, ServerPlayer p) {
        if (!f.watching.add(p.getUUID())) return;
        RenownData data = RenownData.get(p.server);
        PlayerRow row = data.players.get(p.getUUID());
        BossRecord rec = data.bosses.get(f.type);
        RenownNetwork.send(p, new Timer(Timer.RUNNING, f.type, Math.max(0, p.server.getTickCount() - f.start),
                row == null ? 0 : row.bossBest.getOrDefault(f.type, 0), rec == null ? 0 : rec.ticks));
    }

    private static void watchNearby(ServerLevel level, Fight f) {
        if (f.boss.isRemoved() || f.boss.level() != level) return;
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.distanceToSqr(f.boss) <= WATCH_RANGE * WATCH_RANGE) watch(f, p);
        }
    }

    private static void stopWatching(MinecraftServer server, Fight f, Timer payload) {
        for (UUID id : f.watching) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) RenownNetwork.send(p, payload);
        }
    }

    // ------------------------------------------------------------------------------------------------ abandono
    static void onReleased(SocialEvent.ArenaReleased e) {
        Fight f = FIGHTS.remove(e.getBoss());
        if (f == null) return;
        MinecraftServer server = f.boss.getServer();
        if (server != null) stopWatching(server, f, new Timer(Timer.CANCELLED, f.type, 0, 0, 0));
    }

    static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % 40 != 0 || FIGHTS.isEmpty()) return;
        for (Iterator<Fight> it = FIGHTS.values().iterator(); it.hasNext();) {
            Fight f = it.next();
            // Chefe sem arena: ninguém bate há tempo (ou ele sumiu) = luta abandonada. Com arena, quem avisa é o ArenaReleased
            boolean gone = !f.arena && (now - f.lastCombat > IDLE_TICKS || f.boss.isRemoved());
            if (gone) {
                it.remove();
                stopWatching(server, f, new Timer(Timer.CANCELLED, f.type, 0, 0, 0));
            } else if (f.boss.level() instanceof ServerLevel level) {
                watchNearby(level, f);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ vitória
    static void onDeath(LivingDeathEvent e) {
        LivingEntity boss = e.getEntity();
        Fight f = FIGHTS.remove(boss.getUUID());
        if (f == null || !(boss.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        int ticks = Math.max(1, server.getTickCount() - f.start);

        List<ServerPlayer> fighters = new ArrayList<>();
        float total = 0;
        for (Map.Entry<UUID, Float> d : f.damage.entrySet()) {
            total += d.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(d.getKey());
            if (p != null) fighters.add(p);
        }
        boolean valid = !f.tainted && !fighters.isEmpty() && total >= MIN_DAMAGE_SHARE * Math.max(f.maxHealth, boss.getMaxHealth());

        RenownData data = RenownData.get(server);
        BossRecord rec = data.bosses.get(f.type);
        int oldWorld = rec == null ? 0 : rec.ticks;
        boolean world = valid && (oldWorld == 0 || ticks < oldWorld);
        Component bossName = boss.getType().getDescription();
        Component time = Component.literal(format(ticks));

        Set<UUID> told = new HashSet<>();
        for (ServerPlayer p : fighters) {
            told.add(p.getUUID());
            int old = 0;
            if (valid) {
                PlayerRow row = RenownRanking.snapshot(p);
                old = row.bossBest.getOrDefault(f.type, 0);
                boolean pb = old == 0 || ticks < old;
                if (pb) {
                    row.bossBest.put(f.type, ticks);
                    data.setDirty();
                }
                // UMA linha no chat por luta: recorde do servidor > recorde pessoal > só o tempo.
                // "Recorde do servidor" (e o contador do título) só quando um tempo que já existia cai: a primeira
                // vitória contra um chefe é sempre o melhor tempo, e isso não é feito nenhum
                Component line;
                if (world && oldWorld > 0) {
                    line = Component.translatable("renown.isekai_mahou.boss_time.world_self", bossName, time).withStyle(ChatFormatting.GOLD);
                    MagicApi.addCounter(p, RenownTitles.COUNTER_WORLD_RECORDS, 1);
                } else if (pb) {
                    line = Component.translatable("renown.isekai_mahou.boss_time.personal", bossName, time).withStyle(ChatFormatting.GREEN);
                } else {
                    line = Component.translatable("renown.isekai_mahou.boss_time.done", bossName, time, format(old)).withStyle(ChatFormatting.GRAY);
                }
                p.sendSystemMessage(line);
                RenownTitles.markDirty(p);
                Hints.show(p, "renown.boss_time");
            }
            RenownNetwork.send(p, new Timer(Timer.FINISHED, f.type, ticks, valid ? old : -1, valid ? oldWorld : 0));
        }
        // Quem só assistia: o cronômetro para no tempo final
        for (UUID id : f.watching) {
            if (told.contains(id)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) RenownNetwork.send(p, new Timer(Timer.FINISHED, f.type, ticks, -1, 0));
        }

        if (world) {
            BossRecord nr = new BossRecord();
            nr.ticks = ticks;
            nr.day = level.getDayTime() / 24000L;
            for (ServerPlayer p : fighters) {
                nr.ids.add(p.getUUID());
                nr.names.add(p.getGameProfile().getName());
            }
            data.bosses.put(f.type, nr);
            data.setDirty();
            // O resto do servidor só é avisado quando um recorde que já existia cai (primeira vitória não é notícia)
            if (oldWorld > 0) {
                Component msg = Component.translatable("renown.isekai_mahou.boss_time.world_all", String.join(", ", nr.names), bossName, time)
                        .withStyle(ChatFormatting.GOLD);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (!told.contains(p.getUUID())) p.sendSystemMessage(msg);
                }
            }
        }
    }

    /** {@code m:ss.d} (décimos de segundo). */
    static String format(int ticks) {
        int tenths = ticks / 2;
        int sec = tenths / 10;
        return String.format("%d:%02d.%d", sec / 60, sec % 60, tenths % 10);
    }

    static void clear() {
        FIGHTS.clear();
    }

    // ------------------------------------------------------------------------------------------------ teste
    /** Comando de teste: grava um tempo para o jogador (e o recorde do servidor, se for melhor). */
    static void debugRecord(ServerPlayer p, String type, int ticks) {
        RenownData data = RenownData.get(p.server);
        PlayerRow row = RenownRanking.snapshot(p);
        int old = row.bossBest.getOrDefault(type, 0);
        if (old == 0 || ticks < old) row.bossBest.put(type, ticks);
        BossRecord rec = data.bosses.get(type);
        if (rec == null || ticks < rec.ticks) {
            BossRecord nr = new BossRecord();
            nr.ticks = ticks;
            nr.day = p.serverLevel().getDayTime() / 24000L;
            nr.ids.add(p.getUUID());
            nr.names.add(p.getGameProfile().getName());
            data.bosses.put(type, nr);
        }
        data.setDirty();
        RenownTitles.markDirty(p);
    }

    /** Comando de teste: liga/desliga o cronômetro no HUD (sem chefe). */
    static void debugTimer(ServerPlayer p, boolean on) {
        RenownNetwork.send(p, new Timer(on ? Timer.RUNNING : Timer.CANCELLED, "minecraft:wither", 0, 1500, 1200));
    }

    static int active() {
        return FIGHTS.size();
    }
}
