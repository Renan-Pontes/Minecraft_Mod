package com.renanpontes.isekaimahou.feature.world_events;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.feature.world_events.event.ArcaneStorm;
import com.renanpontes.isekaimahou.feature.world_events.event.BloodMoon;
import com.renanpontes.isekaimahou.feature.world_events.event.Earthquake;
import com.renanpontes.isekaimahou.feature.world_events.event.ManaStorm;
import com.renanpontes.isekaimahou.feature.world_events.event.MeteorFall;
import com.renanpontes.isekaimahou.feature.world_events.event.StarShower;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Coração dos eventos do mundo: sorteia (raro), começa, faz o tick e encerra. Só UM evento por vez, só no overworld.
 * <p>
 * Sorteio: uma vez por dia (numa hora que muda a cada dia) e uma vez ao anoitecer. Depois de um evento o mundo
 * descansa {@link #REST_DAYS} dias. Nos {@link #CALM_DAYS} primeiros dias do mundo nada acontece.
 * Admin: {@code /mahou world_events ...} liga/desliga o sorteio e muda a chance.
 */
public final class WeManager {
    /** Raio (blocos) em volta do lugar de um evento local em que os jogadores recebem o aviso. */
    public static final int LOCAL_NOTICE_RANGE = 200;
    private static final int REST_DAYS = 3;
    private static final int CALM_DAYS = 2;

    private WeManager() {}

    static void register() {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(LevelTickEvent.Post.class, WeManager::onLevelTick);
        bus.addListener(SpellDamageEvent.Pre.class, WeManager::onSpellDamage);
        bus.addListener(BlockEvent.EntityPlaceEvent.class, WeSites::onBlockPlaced);
        bus.addListener(LivingExperienceDropEvent.class, BloodMoon::onXpDrop);
        bus.addListener(LivingDropsEvent.class, BloodMoon::onDrops);
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> syncTo(e.getEntity()));
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> syncTo(e.getEntity()));
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> syncTo(e.getEntity()));
        bus.addListener(ServerStoppedEvent.class, e -> {
            ManaStorm.reset();
            StarShower.reset();
        });
    }

    // =====================================================================================
    //  Consulta
    // =====================================================================================
    /** Evento ativo neste nível (só o overworld tem eventos), ou null. */
    @Nullable
    public static WorldEventType active(Level level) {
        if (!(level instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return null;
        return WeState.get(sl.getServer()).type();
    }

    /** Lugar do evento local ativo (cratera, epicentro), ou null. */
    @Nullable
    public static BlockPos site(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return null;
        WeState s = WeState.get(level.getServer());
        return s.type() == null ? null : s.site;
    }

    /** Ticks que faltam para o evento ativo acabar (0 = nenhum). */
    public static int ticksLeft(ServerLevel level) {
        WeState s = WeState.get(level.getServer());
        return s.type() == null ? 0 : (int) Math.max(0, s.endsAt - level.getServer().overworld().getGameTime());
    }

    // =====================================================================================
    //  Tick e sorteio
    // =====================================================================================
    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        WeState s = WeState.get(level.getServer());
        long now = level.getGameTime();
        WorldEventType type = s.type();
        if (type == null) {
            if (now % 100 == 0) schedule(level, s);
            return;
        }
        if (now >= s.endsAt) {
            stop(level, true);
            return;
        }
        int age = (int) Math.max(0, now - s.startedAt);
        boolean keep = switch (type) {
            case METEOR -> MeteorFall.tick(level, s, age);
            case BLOOD_MOON -> BloodMoon.tick(level, s, age);
            case ARCANE_STORM -> ArcaneStorm.tick(level, s, age);
            case EARTHQUAKE -> Earthquake.tick(level, s, age);
            case MANA_STORM -> ManaStorm.tick(level, s, age);
            case STAR_SHOWER -> StarShower.tick(level, s, age);
        };
        if (!keep) stop(level, true);
    }

    private static void schedule(ServerLevel level, WeState s) {
        if (!s.auto || s.chancePct <= 0 || level.players().isEmpty()) return;
        long dayTime = level.getDayTime();
        long day = dayTime / 24000L;
        int tod = (int) (dayTime % 24000L);
        if (day < CALM_DAYS) return;
        float k = s.chancePct / 100F;
        RandomSource rnd = level.random;

        // Anoitecer: Lua de Sangue (lua cheia chama mais), um meteoro riscando a noite ou uma Chuva de Estrelas
        if (tod >= 13000 && tod < 14500 && s.rolledNight != day) {
            s.rolledNight = day;
            s.setDirty();
            if (day < s.nextAllowedDay) return;
            float r = rnd.nextFloat();
            float blood = (level.getMoonPhase() == 0 ? 0.12F : 0.05F) * k;
            if (r < blood) start(level, WorldEventType.BLOOD_MOON, null, false);
            else if (r < blood + 0.03F * k) start(level, WorldEventType.METEOR, randomPlayer(level), false);
            // Chuva de Estrelas (v1.1): noite calma; lua nova (céu mais escuro) chama mais
            else if (r < blood + 0.03F * k + (level.getMoonPhase() == 4 ? 0.10F : 0.045F) * k) {
                start(level, WorldEventType.STAR_SHOWER, null, false);
            }
            return;
        }
        // De dia: numa hora diferente a cada dia
        int rollAt = 1000 + (int) Math.floorMod(day * 7919L + level.getSeed(), 9000L);
        if (tod >= rollAt && tod < 11500 && s.rolledDay != day) {
            s.rolledDay = day;
            s.setDirty();
            if (day < s.nextAllowedDay) return;
            float meteor = 0.03F * k;
            float quake = 0.03F * k;
            float arcane = (level.isRaining() ? 0.08F : 0.035F) * k;
            float mana = 0.03F * k;
            float r = rnd.nextFloat();
            WorldEventType pick = null;
            if (r < meteor) pick = WorldEventType.METEOR;
            else if (r < meteor + quake) pick = WorldEventType.EARTHQUAKE;
            else if (r < meteor + quake + arcane) pick = WorldEventType.ARCANE_STORM;
            else if (r < meteor + quake + arcane + mana) pick = WorldEventType.MANA_STORM;
            if (pick != null) start(level, pick, randomPlayer(level), false);
        }
    }

    @Nullable
    private static ServerPlayer randomPlayer(ServerLevel level) {
        List<ServerPlayer> ok = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (p.isAlive() && !p.isSpectator()) ok.add(p);
        return ok.isEmpty() ? null : ok.get(level.random.nextInt(ok.size()));
    }

    // =====================================================================================
    //  Começar / encerrar
    // =====================================================================================
    /**
     * Começa um evento. {@code around} = jogador perto de quem os eventos locais procuram um lugar livre (pode ser null
     * para os globais). {@code forced} = pedido por comando (a Lua de Sangue dura mesmo de dia).
     * Devolve false se não deu (já há evento, sem lugar livre por perto...).
     */
    public static boolean start(ServerLevel overworld, WorldEventType type, @Nullable ServerPlayer around, boolean forced) {
        if (overworld.dimension() != Level.OVERWORLD) return false;
        WeState s = WeState.get(overworld.getServer());
        if (s.type() != null) return false;
        if (around != null && around.serverLevel() != overworld) around = null;

        BlockPos site = null;
        if (type.local) {
            if (around == null) return false;
            site = type == WorldEventType.METEOR ? MeteorFall.findSite(overworld, around) : Earthquake.findSite(overworld, around);
            if (site == null) return false;
        }
        long now = overworld.getGameTime();
        s.begin(type, now, now + type.duration, site);
        s.extra.putBoolean("forced", forced);
        s.nextAllowedDay = overworld.getDayTime() / 24000L + REST_DAYS;
        switch (type) {
            case METEOR -> MeteorFall.start(overworld, s);
            case BLOOD_MOON -> BloodMoon.start(overworld, s);
            case ARCANE_STORM -> ArcaneStorm.start(overworld, s);
            case EARTHQUAKE -> Earthquake.start(overworld, s, around);
            case MANA_STORM -> ManaStorm.start(overworld, s);
            case STAR_SHOWER -> StarShower.start(overworld, s);
        }
        s.setDirty();
        for (ServerPlayer p : audience(overworld, s)) {
            // Um aviso curto e, na primeira vez, uma dica de uma linha explicando o evento
            Rewards.banner(p, type.startText(), type.color);
            Hints.show(p, type.hintId());
            // "presenciou o evento": contador world_event.<id> (títulos/ranking de outros módulos leem por id)
            MagicApi.addCounter(p, "world_event." + type.id, 1);
        }
        syncAll(overworld.getServer());
        return true;
    }

    /** Encerra o evento ativo (se houver). {@code notify} = mostra o aviso discreto de fim. */
    public static boolean stop(ServerLevel overworld, boolean notify) {
        WeState s = WeState.get(overworld.getServer());
        WorldEventType type = s.type();
        if (type == null) return false;
        List<ServerPlayer> audience = audience(overworld, s);
        switch (type) {
            case ARCANE_STORM -> ArcaneStorm.end(overworld, s);
            case MANA_STORM -> ManaStorm.reset();
            case STAR_SHOWER -> StarShower.reset();
            default -> { }
        }
        s.clear();
        if (notify) {
            for (ServerPlayer p : audience) p.displayClientMessage(type.endText().withColor(type.color), true);
        }
        syncAll(overworld.getServer());
        return true;
    }

    /** Quem recebe os avisos: todos do overworld (evento global) ou quem está perto do lugar (evento local). */
    static List<ServerPlayer> audience(ServerLevel overworld, WeState s) {
        List<ServerPlayer> out = new ArrayList<>();
        WorldEventType type = s.type();
        if (type == null) return out;
        for (ServerPlayer p : overworld.players()) {
            if (type.local && s.site != null
                    && p.blockPosition().distSqr(s.site) > (double) LOCAL_NOTICE_RANGE * LOCAL_NOTICE_RANGE) continue;
            out.add(p);
        }
        return out;
    }

    // =====================================================================================
    //  Magia mais forte
    // =====================================================================================
    /** Bônus de dano de magia durante os eventos (e um pouco em qualquer tempestade com trovões). */
    private static void onSpellDamage(SpellDamageEvent.Pre e) {
        ServerLevel level = e.ctx().level();
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        WorldEventType type = WeState.get(level.getServer()).type();
        Element el = e.element();
        boolean stormy = el != null && (el.matches(Element.WATER) || el.matches(Element.WIND));
        float mult = 1F;
        if (type == WorldEventType.MANA_STORM) mult = ManaStorm.SPELL_BONUS;
        else if (type == WorldEventType.ARCANE_STORM && stormy) mult = ArcaneStorm.SPELL_BONUS;
        else if (stormy && level.isThundering()) mult = ArcaneStorm.NATURAL_THUNDER_BONUS;
        if (mult != 1F) e.setAmount(e.getAmount() * mult);
    }

    // =====================================================================================
    //  Sincronização (o cliente só precisa saber QUAL evento, para o céu e as partículas)
    // =====================================================================================
    static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) syncTo(p);
    }

    static void syncTo(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        WorldEventType type = active(sp.serverLevel());
        PacketDistributor.sendToPlayer(sp, new WeNetwork.State(type == null ? -1 : type.ordinal()));
    }
}
