package com.renanpontes.isekaimahou.feature.paths;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Mantém os contadores dos feitos-bônus dos Caminhos (o núcleo já cuida de kill.* e cast.*):
 * <ul>
 *   <li>{@code visit.<ns>.<dimensão>}: cada entrada numa dimensão</li>
 *   <li>{@code feat.underwater_ticks}: ticks com a cabeça debaixo d'água</li>
 *   <li>{@code feat.deep_blocks}: blocos de picareta minerados abaixo de y=0</li>
 *   <li>{@code feat.high_altitude}: cada subida acima de y=200</li>
 *   <li>{@code feat.fall_saved}: quedas de 10+ blocos sem levar dano (magia de vento, queda lenta...)</li>
 *   <li>{@code feat.undead_kills} / {@code feat.night_kills}: abates de mortos-vivos / de monstros à noite</li>
 * </ul>
 * Contadores muito frequentes (água, mineração) são acumulados aqui e somados a cada 5 s, para não
 * sincronizar o MagicData inteiro a cada tick.
 */
public final class FeatTracker {
    public static final String UNDERWATER = "feat.underwater_ticks";
    public static final String DEEP_BLOCKS = "feat.deep_blocks";
    public static final String HIGH_ALTITUDE = "feat.high_altitude";
    public static final String FALL_SAVED = "feat.fall_saved";
    public static final String UNDEAD_KILLS = "feat.undead_kills";
    public static final String NIGHT_KILLS = "feat.night_kills";

    private static final int HIGH_Y = 200;
    private static final int FALL_MIN = 10;
    private static final int FLUSH_INTERVAL = 100;

    /** Estado temporário por jogador (não salvo). */
    private static final class State {
        int underwater;
        int deepBlocks;
        boolean high;
        boolean airborne;
        double peakY;
        double lastY = Double.NaN;
        long lastFallHurt = Long.MIN_VALUE / 2;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(PlayerTickEvent.Post.class, FeatTracker::onPlayerTick);
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, FeatTracker::onDimension);
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, FeatTracker::onLogin);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, FeatTracker::onLogout);
        bus.addListener(LivingDamageEvent.Post.class, FeatTracker::onDamage);
        bus.addListener(ServerStoppedEvent.class, FeatTracker::onServerStopped);
        // LOWEST: só conta se ninguém cancelou a quebra/morte.
        bus.addListener(EventPriority.LOWEST, BlockEvent.BreakEvent.class, FeatTracker::onBreak);
        bus.addListener(EventPriority.LOWEST, LivingDeathEvent.class, FeatTracker::onDeath);
    }

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = state(p);
        if (p.isCreative() || p.isSpectator()) {
            s.airborne = false;
            s.lastY = p.getY();
            return;
        }
        if (p.isUnderWater()) s.underwater++;

        double y = p.getY();
        if (y > HIGH_Y && !p.getAbilities().flying) {
            if (!s.high) {
                s.high = true;
                MagicApi.addCounter(p, HIGH_ALTITUDE, 1);
            }
        } else if (y < HIGH_Y - 10) {
            s.high = false;
        }

        trackFall(p, s, y);
        s.lastY = y;

        if (p.tickCount % FLUSH_INTERVAL == 0) flush(p, s);
    }

    /** "Salvo da queda": caiu 10+ blocos e tocou o chão sem receber dano de queda (nem caiu na água). */
    private static void trackFall(ServerPlayer p, State s, double y) {
        boolean teleported = !Double.isNaN(s.lastY) && Math.abs(y - s.lastY) > 8;
        if (teleported || p.isInWater() || p.isInLava() || p.getAbilities().flying || p.isFallFlying()
                || p.isPassenger() || p.onClimbable() || p.isSleeping()) {
            s.airborne = false;
            s.peakY = y;
            return;
        }
        if (!p.onGround()) {
            if (!s.airborne) {
                s.airborne = true;
                s.peakY = Double.isNaN(s.lastY) ? y : Math.max(y, s.lastY);
            } else {
                s.peakY = Math.max(s.peakY, y);
            }
            return;
        }
        // O dano de queda chega junto do pacote de pouso; uma janela curta cobre a ordem dos eventos.
        if (s.airborne && s.peakY - y >= FALL_MIN && p.isAlive()
                && p.level().getGameTime() - s.lastFallHurt > 5) {
            MagicApi.addCounter(p, FALL_SAVED, 1);
        }
        s.airborne = false;
        s.peakY = y;
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getNewDamage() > 0
                && event.getSource().is(DamageTypeTags.IS_FALL)) {
            state(p).lastFallHurt = p.level().getGameTime();
        }
    }

    private static void flush(ServerPlayer p, State s) {
        if (s.underwater > 0) {
            MagicApi.addCounter(p, UNDERWATER, s.underwater);
            s.underwater = 0;
        }
        if (s.deepBlocks > 0) {
            MagicApi.addCounter(p, DEEP_BLOCKS, s.deepBlocks);
            s.deepBlocks = 0;
        }
    }

    /** "Minerar": só blocos de picareta (pedra, ardósia, minérios...) que não quebram na hora. */
    private static void onBreak(BlockEvent.BreakEvent event) {
        // FakePlayer (pedestais, máquinas de outros mods) não conta feitos.
        if (!(event.getPlayer() instanceof ServerPlayer p) || p instanceof FakePlayer || p.isCreative()) return;
        if (event.getPos().getY() >= 0) return;
        if (!event.getState().is(BlockTags.MINEABLE_WITH_PICKAXE)) return;
        if (event.getState().getDestroySpeed(event.getLevel(), event.getPos()) <= 0) return;
        state(p).deepBlocks++;
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return;
        if (victim.getType().is(EntityTypeTags.UNDEAD)) MagicApi.addCounter(p, UNDEAD_KILLS, 1);
        if (victim instanceof Enemy && victim.level().isNight()) MagicApi.addCounter(p, NIGHT_KILLS, 1);
    }

    private static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            MagicApi.addCounter(p, visitKey(event.getTo().location()), 1);
            state(p).lastY = Double.NaN;
        }
    }

    /** Quem já estava fora do Mundo Normal antes deste módulo existir também conta a visita. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.level().dimension() != Level.OVERWORLD) {
            String key = visitKey(p.level().dimension().location());
            if (MagicApi.get(p).counter(key) == 0) MagicApi.addCounter(p, key, 1);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State s = STATES.remove(p.getUUID());
        if (s != null) flush(p, s);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    public static String visitKey(ResourceLocation dimension) {
        return "visit." + dimension.getNamespace() + "." + dimension.getPath();
    }

    private FeatTracker() {}
}
