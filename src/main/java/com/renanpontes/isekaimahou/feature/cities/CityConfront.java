package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.cities.entity.CityGuard;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;
import com.renanpontes.isekaimahou.feature.cities.network.CitiesNetwork;
import com.renanpontes.isekaimahou.feature.cities.world.CityBuildingPiece;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;
import com.renanpontes.isekaimahou.feature.cities.world.CityWorldgen;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Abordagem da guarda (v1.0, §0 "os players tão saqueando tudo"). Um guarda (ou um morador que chama a guarda) que VÊ o
 * jogador saquear a cidade vai até ele e reclama:
 * <ul>
 *   <li><b>O que conta</b>: abrir baú/barril da cidade (marca {@code CityChest}), quebrar blocos dos PRÉDIOS da cidade
 *       (peças {@link CityBuildingPiece}; o que o próprio jogador pôs ali não conta) e furtar um morador (glifos do
 *       Ladino — contador {@code rogue.steal} + marca de bolso roubado).</li>
 *   <li><b>Quem vê</b>: guarda do reino a até 24 blocos (12 agachado), com linha de visão e o jogador à frente dele (perto
 *       de 6 blocos ele ouve); invisível só a 4 blocos. Morador que vê grita e o guarda mais perto (48 blocos) vem.</li>
 *   <li><b>Na hora</b>: −reputação no reino (baú −5 e +2 Submundo, bloco −2 até −8 por abordagem; o furto já tirou a dele),
 *       "!" sobre o guarda, apito, linha no rastreador. O guarda anda até o jogador e abre o diálogo com a acusação.</li>
 *   <li><b>Respostas</b>: Persuadir (chance pela reputação, classe, raça, ser morador/aliado, reincidência e o crime — uma
 *       tentativa), Pagar propina (alguns guardas são honestos e se ofendem), Devolver (as coisas voltam para o baú e o
 *       guarda conserta os blocos com os itens que caíram; quem não pegou nada mostra os bolsos), Fugir (Procurado 3 min
 *       com vantagem de 4 s) ou Brigar (Procurado 5 min).</li>
 *   <li><b>Reincidência</b>: cada abordagem é 1 marca no reino (some depois de 1 dia de jogo sem crime; persuadir ou
 *       devolver tira a marca). Na 3ª o guarda não conversa: Procurado e briga.</li>
 *   <li>Fugir de longe (40 blocos na chegada, 14 durante a conversa), sumir de dimensão, sair do jogo ou não responder
 *       em 30 s também é fuga. O Senhor da cidade ({@code Allegiance.isLeader}) não é abordado; no Cerco ninguém liga.</li>
 * </ul>
 * Estado só em memória (abordagens duram segundos); as marcas ficam no jogador ({@code CityPlayer/Strikes}).
 */
public final class CityConfront {
    public enum Offense {
        CHEST("chest"), BLOCK("block"), THEFT("theft");

        public final String id;

        Offense(String id) {
            this.id = id;
        }
    }

    public static final int MAX_STRIKES = 3;
    private static final long STRIKE_MEMORY = 24000L;
    private static final int APPROACH_TIMEOUT = 800;
    private static final int ANSWER_TIMEOUT = 600;
    private static final double TALK_DIST = 3.2;
    private static final double FLEE_APPROACH = 40.0;
    private static final double FLEE_TALK = 14.0;
    private static final int FLEE_WANTED = 3600;
    private static final int FIGHT_WANTED = 6000;
    private static final int REPEAT_WANTED = 6000;
    private static final int FLEE_BAIL = 15;
    private static final int REPEAT_BAIL = 20;
    private static final int MAX_REP_LOSS = 12;
    private static final int MAX_EVIDENCE = 64;
    private static final int BLOCK_PENALTY_COOLDOWN = 200;
    private static final String STRIKES = "Strikes";

    /** Abordagem em andamento por jogador. */
    private static final Map<UUID, Incident> ACTIVE = new HashMap<>();
    /** Jogador → tick do último furto (glifos do Ladino), para saber por que um guarda quis atacá-lo. */
    private static final Map<UUID, Long> RECENT_THEFT = new HashMap<>();
    /** Blocos que jogadores puseram dentro dos prédios das cidades (não contam como vandalismo ao quebrar). */
    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> PLACED = new HashMap<>();
    private static final int PLACED_CAP = 8192;

    /** Um bloco quebrado (para o guarda consertar ao devolver). */
    private record Broken(BlockPos pos, BlockState state) {}

    static final class Incident {
        final UUID player;
        UUID guard;
        final String faction;
        final String cityId;
        final ResourceKey<Level> dim;
        Offense offense;
        int repLost;
        int strikes;
        long started;
        long arrived = -1;
        /** Distância máxima antes de contar como fuga na chegada (o guarda chamado pode vir de longe). */
        double fleeDist = FLEE_APPROACH;
        long lastPrompt;
        long lastNag;
        boolean screenOpen;
        boolean persuadeTried;
        boolean bribeRefused;
        @Nullable BlockPos chestPos;
        List<ItemStack> chestSnapshot = List.of();
        final List<Broken> broken = new ArrayList<>();
        final List<ItemStack> drops = new ArrayList<>();

        Incident(UUID player, UUID guard, String faction, String cityId, ResourceKey<Level> dim, Offense offense, long now) {
            this.player = player;
            this.guard = guard;
            this.faction = faction;
            this.cityId = cityId;
            this.dim = dim;
            this.offense = offense;
            this.started = now;
        }

        boolean talking() {
            return arrived >= 0;
        }
    }

    // =====================================================================================
    //  Detecção
    // =====================================================================================
    /** Abriu um baú/barril marcado da cidade (chamado pelo CityCrimes no clique). */
    public static void onOpenChest(ServerPlayer p, ServerLevel sl, BlockPos pos) {
        Incident inc = report(sl, p, Offense.CHEST, pos, null, null);
        if (inc != null && inc.chestPos == null) {
            Container c = container(sl, pos);
            if (c != null) {
                inc.chestPos = pos.immutable();
                inc.chestSnapshot = snapshot(c);
            }
        }
    }

    /** Quebrou um bloco: vandalismo se for de um prédio da cidade e não foi o próprio jogador que pôs. */
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.isCanceled() || !(e.getPlayer() instanceof ServerPlayer p) || !eligible(p)) return;
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        BlockPos pos = e.getPos();
        BlockState state = e.getState();
        if (state.isAir() || state.canBeReplaced()) return;
        if (CityPlaces.cityAt(sl, pos) == null) return;
        Long2LongOpenHashMap placed = PLACED.get(sl.dimension());
        if (placed != null && placed.remove(pos.asLong()) != placed.defaultReturnValue()) return;   // o jogador mesmo pôs
        if (!isBuilding(sl, pos)) return;
        Incident inc = report(sl, p, Offense.BLOCK, pos, null, null);
        if (inc != null && inc.broken.size() < MAX_EVIDENCE) inc.broken.add(new Broken(pos.immutable(), state));
    }

    /** O que caiu do bloco quebrado durante a abordagem vira "prova" (o jogador devolve para o guarda consertar). */
    public static void onDrops(BlockDropsEvent e) {
        if (e.isCanceled() || !(e.getBreaker() instanceof ServerPlayer p)) return;
        Incident inc = ACTIVE.get(p.getUUID());
        if (inc == null || inc.broken.isEmpty() || !inc.broken.get(inc.broken.size() - 1).pos().equals(e.getPos())) return;
        for (ItemEntity ie : e.getDrops()) {
            if (inc.drops.size() >= MAX_EVIDENCE) break;
            if (!ie.getItem().isEmpty()) inc.drops.add(ie.getItem().copy());
        }
    }

    /** Bloco posto por jogador dentro de uma cidade: lembrado (quebrar de volta não é crime). */
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || !(e.getLevel() instanceof ServerLevel sl)) return;
        if (CityPlaces.cityAt(sl, e.getPos()) == null) return;
        Long2LongOpenHashMap m = PLACED.computeIfAbsent(sl.dimension(), k -> new Long2LongOpenHashMap());
        if (m.size() >= PLACED_CAP) {
            // Esquece a metade mais antiga.
            long now = sl.getGameTime();
            long cut = now - 72000L;
            m.long2LongEntrySet().removeIf(en -> en.getLongValue() < cut);
            if (m.size() >= PLACED_CAP) m.clear();
        }
        m.put(e.getPos().asLong(), sl.getGameTime());
    }

    /** Contador do Ladino: lembra o furto (o guarda que viu tenta atacar logo depois → vira abordagem). */
    public static void onCounter(MagicProgressEvent.Counter e) {
        if (e.key.equals("rogue.steal")) RECENT_THEFT.put(e.getPlayer().getUUID(), e.getPlayer().serverLevel().getGameTime());
    }

    /**
     * Alguém (o furto do Ladino, um alerta de outro mod) mandou um guarda atacar um jogador que NÃO é fora da lei: o
     * guarda não ataca — se foi um furto agora há pouco, ele aborda o jogador. Sempre devolve true (o ataque é cancelado).
     */
    public static boolean onGuardProvoked(CityGuard g, ServerPlayer p) {
        Long t = RECENT_THEFT.get(p.getUUID());
        if (t != null && g.level() instanceof ServerLevel sl && sl.getGameTime() - t <= 60 && sl.getGameTime() >= t) {
            report(sl, p, Offense.THEFT, g.blockPosition(), g, null);
        }
        return true;
    }

    /** Um morador percebeu que o bolso ficou mais leve (marca do Ladino): se viu o ladrão, é crime. */
    public static void onRobbed(CityNpc victim, ServerPlayer thief) {
        if (!(victim.level() instanceof ServerLevel sl) || !victim.hasLineOfSight(thief)) return;
        victim.alert(CityNpc.ALERT_SUSPICIOUS, 80);
        report(sl, thief, Offense.THEFT, victim.blockPosition(), victim instanceof CityGuard g ? g : null, victim);
    }

    private static boolean eligible(ServerPlayer p) {
        return !(p instanceof FakePlayer) && !p.isCreative() && !p.isSpectator() && p.isAlive();
    }

    /**
     * Alguém viu o crime? Aplica a reputação e começa (ou soma à) abordagem. null = ninguém viu, só um morador viu e
     * não há guarda, ou o jogador já é caçado.
     */
    @Nullable
    static Incident report(ServerLevel sl, ServerPlayer p, Offense offense, BlockPos where, @Nullable CityGuard knownGuard,
                           @Nullable CityNpc knownWitness) {
        if (!eligible(p)) return null;
        Places.Place city = CityPlaces.cityAt(sl, where);
        if (city == null) return null;
        if (Allegiance.isLeader(p, city.id())) return null;          // o Senhor da cidade mexe no que é dele
        if (CitySiege.underSiege(sl, where)) return null;            // no Cerco ninguém liga para baús
        String faction = kingdomFaction(city, knownGuard != null ? knownGuard : knownWitness);
        if (CityGuard.isOutlaw(p, faction)) return null;             // já é caçado: os guardas atacam à vista
        if (Allegiance.isEnemyOf(p, city.id())) return null;         // em guerra com a cidade: é briga, não conversa
        long now = sl.getGameTime();

        Incident cur = ACTIVE.get(p.getUUID());
        if (cur != null) {
            if (!cur.faction.equals(faction)) return null;
            // Continua saqueando com o guarda vindo: soma ao mesmo caso.
            penalty(sl, p, cur, offense, where);
            if (offense == Offense.THEFT || offense == Offense.CHEST && cur.offense == Offense.BLOCK) cur.offense = offense;
            return cur;
        }

        CityGuard guard = knownGuard != null && knownGuard.isAlive() ? knownGuard : seeingGuard(sl, p, faction);
        CityNpc witness = guard != null ? guard : knownWitness != null ? knownWitness : seeingNpc(sl, p, faction);
        if (witness == null) return null;
        if (guard == null) {
            witness.faceFor(p, 60);
            witness.alert(CityNpc.ALERT_SUSPICIOUS, 100);
            if (CityCrimes.cooldown(p.getStringUUID() + "/shout/" + witness.getId(), now, 100)) {
                witness.say(CityLines.pick("citizen.call_guard", witness.getRandom()));
                sl.playSound(null, witness.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
            guard = nearestGuard(sl, witness.position(), faction, 48);
        }
        Incident tmp = new Incident(p.getUUID(), guard != null ? guard.getUUID() : witness.getUUID(), faction, city.id(),
                sl.dimension(), offense, now);
        penalty(sl, p, tmp, offense, where);
        if (guard == null) {
            // Só um morador viu e não há guarda por perto: fica a reputação (como na v0.8).
            p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.seen", witness.getDisplayName())
                    .withColor(0xF0A043), true);
            return null;
        }

        int strikes = addStrike(p, faction, now);
        tmp.strikes = strikes;
        guard.faceFor(p, 40);
        if (strikes >= MAX_STRIKES) {
            // Reincidente: sem conversa.
            guard.say(CityLines.pick("confront.repeat", guard.getRandom(), p.getDisplayName()));
            guard.playAction("scold");
            CityCrimes.makeWanted(p, faction, REPEAT_WANTED, REPEAT_BAIL);
            Reputation.add(p, faction, -5, Component.translatable("reason.isekai_mahou.cities.repeat"));
            guard.setTarget(p);
            CityCrimes.callGuards(sl, guard, p, faction);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.confront.repeat").withColor(0xD03A2C), false);
            return null;
        }
        tmp.fleeDist = Math.max(FLEE_APPROACH, guard.distanceTo(p) + 8.0);
        ACTIVE.put(p.getUUID(), tmp);
        guard.setConfronting(p);
        guard.alert(CityNpc.ALERT_CONFRONT, 60);
        guard.say(CityLines.pick("confront.halt", guard.getRandom(), p.getDisplayName()));
        guard.playAction("halt");
        whistle(sl, guard);
        p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.confront.seen", guard.getDisplayName())
                .withColor(0xF0A043), false);
        sendStatus(p, tmp, guard, now);
        return tmp;
    }

    /** Reputação perdida no crime (com teto por abordagem). */
    private static void penalty(ServerLevel sl, ServerPlayer p, Incident inc, Offense offense, BlockPos where) {
        int loss = 0;
        Component reason;
        switch (offense) {
            case CHEST -> {
                if (!CityCrimes.cooldown(p.getStringUUID() + "/chest/" + where.asLong(), sl.getGameTime(), 2400)) return;
                loss = 5;
                reason = Component.translatable("reason.isekai_mahou.cities.theft");
                Reputation.add(p, "underworld", 2, reason);
            }
            case BLOCK -> {
                if (!CityCrimes.cooldown(p.getStringUUID() + "/vandal", sl.getGameTime(), BLOCK_PENALTY_COOLDOWN / 4)) return;
                loss = 2;
                reason = Component.translatable("reason.isekai_mahou.cities.vandalism");
            }
            default -> {
                return;   // o furto do Ladino já tirou a reputação dele
            }
        }
        loss = Math.min(loss, MAX_REP_LOSS - inc.repLost);
        if (loss <= 0) return;
        inc.repLost += loss;
        Reputation.add(p, inc.faction, -loss, reason);
    }

    // =====================================================================================
    //  Quem vê
    // =====================================================================================
    /** O guarda percebe o jogador: perto (6) ouve; mais longe precisa de linha de visão e o jogador à frente dele. */
    private static boolean notices(CityNpc npc, ServerPlayer p, double maxDist) {
        double d2 = npc.distanceToSqr(p);
        if (p.isInvisible()) maxDist = Math.min(maxDist, 4.0);
        else if (p.isCrouching()) maxDist = Math.min(maxDist, maxDist / 2.0);
        if (d2 > maxDist * maxDist) return false;
        if (d2 > 36.0) {
            Vec3 look = npc.getViewVector(1.0F);
            Vec3 to = p.getEyePosition().subtract(npc.getEyePosition()).normalize();
            if (look.dot(to) < 0.34) return false;
        }
        return npc.hasLineOfSight(p);
    }

    @Nullable
    private static CityGuard seeingGuard(ServerLevel sl, ServerPlayer p, String faction) {
        List<CityGuard> list = sl.getEntitiesOfClass(CityGuard.class, p.getBoundingBox().inflate(24),
                g -> g.isAlive() && g.getTarget() == null && g.confronting() == null && faction.equals(g.kingdomFaction()));
        list.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        int tries = 0;
        for (CityGuard g : list) {
            if (++tries > 6) break;
            if (notices(g, p, 24)) return g;
        }
        return null;
    }

    @Nullable
    private static CityNpc seeingNpc(ServerLevel sl, ServerPlayer p, String faction) {
        List<CityNpc> list = sl.getEntitiesOfClass(CityNpc.class, p.getBoundingBox().inflate(16),
                n -> n.isAlive() && !(n instanceof CityGuard) && n.getTarget() == null && faction.equals(n.kingdomFaction()));
        list.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        int tries = 0;
        for (CityNpc n : list) {
            if (++tries > 6) break;
            if (notices(n, p, 16)) return n;
        }
        return null;
    }

    @Nullable
    private static CityGuard nearestGuard(ServerLevel sl, Vec3 at, String faction, double radius) {
        CityGuard best = null;
        double bestD = radius * radius;
        for (CityGuard g : sl.getEntitiesOfClass(CityGuard.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius),
                g -> g.isAlive() && g.getTarget() == null && g.confronting() == null && faction.equals(g.kingdomFaction()))) {
            double d = g.distanceToSqr(at);
            if (d < bestD) {
                bestD = d;
                best = g;
            }
        }
        return best;
    }

    private static String kingdomFaction(Places.Place city, @Nullable CityNpc npc) {
        if (CityRealm.isKingdomFaction(city.faction())) return city.faction();
        return npc != null ? npc.kingdomFaction() : CityKingdom.ALDORIA.factionId;
    }

    /** O bloco é de um prédio da cidade (não do chão/rua)? */
    public static boolean isBuilding(ServerLevel sl, BlockPos pos) {
        StructureStart start = sl.structureManager().getStructureWithPieceAt(pos,
                h -> h.value().type() == CityWorldgen.GUILD_CITY.get());
        if (start == null || !start.isValid()) return false;
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof CityBuildingPiece && piece.getBoundingBox().isInside(pos)) return true;
        }
        return false;
    }

    // =====================================================================================
    //  Marcas de reincidência (por reino, no jogador)
    // =====================================================================================
    private static int strikes(ServerPlayer p, String faction, long now) {
        CompoundTag all = CityPlayer.tag(p).getCompound(STRIKES);
        if (!all.contains(faction)) return 0;
        CompoundTag s = all.getCompound(faction);
        long last = s.getLong("Last");
        if (now < last || now - last > STRIKE_MEMORY) return 0;
        return s.getInt("N");
    }

    private static int addStrike(ServerPlayer p, String faction, long now) {
        int n = strikes(p, faction, now) + 1;
        setStrikes(p, faction, n, now);
        return n;
    }

    private static void setStrikes(ServerPlayer p, String faction, int n, long now) {
        CompoundTag t = CityPlayer.tag(p);
        CompoundTag all = t.getCompound(STRIKES);
        if (n <= 0) {
            all.remove(faction);
        } else {
            CompoundTag s = new CompoundTag();
            s.putInt("N", n);
            s.putLong("Last", now);
            all.put(faction, s);
        }
        t.put(STRIKES, all);
    }

    private static void forgiveStrike(ServerPlayer p, String faction) {
        long now = p.serverLevel().getGameTime();
        int n = strikes(p, faction, now);
        if (n > 0) {
            CompoundTag s = CityPlayer.tag(p).getCompound(STRIKES).getCompound(faction);
            CompoundTag t = CityPlayer.tag(p);
            CompoundTag all = t.getCompound(STRIKES);
            if (n - 1 <= 0) {
                all.remove(faction);
            } else {
                s.putInt("N", n - 1);
                all.put(faction, s);
            }
            t.put(STRIKES, all);
        }
    }

    /** Ficha limpa de marcas (comando, receptador). */
    public static void clearStrikes(ServerPlayer p) {
        CityPlayer.tag(p).remove(STRIKES);
        Incident inc = ACTIVE.remove(p.getUUID());
        if (inc != null) finish(p, inc, null);
    }

    // =====================================================================================
    //  Andamento (a cada 5 ticks)
    // =====================================================================================
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            if (!RECENT_THEFT.isEmpty() && server.getTickCount() % 200 == 0) RECENT_THEFT.clear();
            return;
        }
        Iterator<Map.Entry<UUID, Incident>> it = ACTIVE.entrySet().iterator();
        List<Runnable> later = new ArrayList<>();
        while (it.hasNext()) {
            Incident inc = it.next().getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(inc.player);
            if (p == null) {
                it.remove();
                continue;
            }
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) {
                it.remove();
                later.add(() -> finish(p, inc, null));
                continue;
            }
            ServerLevel sl = p.serverLevel();
            long now = sl.getGameTime();
            if (sl.dimension() != inc.dim) {
                it.remove();
                later.add(() -> escalate(p, inc, null, "flee", false));
                continue;
            }
            if (CityGuard.isOutlaw(p, inc.faction) || Allegiance.isEnemyOf(p, inc.cityId)) {
                // Virou briga por outro caminho (feriu alguém, guerra): a conversa acabou.
                it.remove();
                later.add(() -> finish(p, inc, null));
                continue;
            }
            CityGuard g = guard(sl, inc);
            if (g == null) {
                g = nearestGuard(sl, p.position(), inc.faction, 32);
                if (g == null) {
                    it.remove();
                    later.add(() -> {
                        finish(p, inc, null);
                        p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.confront.lost").withColor(0xA8E07A), true);
                    });
                    continue;
                }
                inc.guard = g.getUUID();
                inc.arrived = -1;
                inc.started = now;
                inc.fleeDist = Math.max(FLEE_APPROACH, g.distanceTo(p) + 8.0);
                g.say(CityLines.pick("confront.halt", g.getRandom(), p.getDisplayName()));
            }
            if (g.getTarget() != null) {
                // O guarda está brigando com um monstro: a conversa espera.
                inc.lastPrompt = Math.max(inc.lastPrompt, now - ANSWER_TIMEOUT / 2);
                inc.started = Math.max(inc.started, now - APPROACH_TIMEOUT / 2);
                continue;
            }
            g.setConfronting(p);
            g.alert(CityNpc.ALERT_CONFRONT, 40);
            double d = g.distanceTo(p);
            final CityGuard guard = g;
            if (!inc.talking()) {
                if (d <= TALK_DIST || now - inc.started > APPROACH_TIMEOUT && d <= 8.0) {
                    later.add(() -> arrive(p, guard, inc));
                } else if (d > inc.fleeDist || now - inc.started > APPROACH_TIMEOUT) {
                    it.remove();
                    later.add(() -> escalate(p, inc, guard, "flee", false));
                    continue;
                }
            } else {
                if (d > FLEE_TALK) {
                    it.remove();
                    later.add(() -> escalate(p, inc, guard, "flee", false));
                    continue;
                }
                if (now - inc.lastPrompt > ANSWER_TIMEOUT) {
                    it.remove();
                    later.add(() -> escalate(p, inc, guard, "impatient", false));
                    continue;
                }
                if (!inc.screenOpen && now - inc.lastNag > 200) {
                    inc.lastNag = now;
                    guard.say(CityLines.pick("confront.waiting", guard.getRandom(), p.getDisplayName()));
                    guard.playAction("scold");
                }
                guard.getLookControl().setLookAt(p, 30.0F, 30.0F);
            }
            if (server.getTickCount() % 20 == 0) sendStatus(p, inc, guard, now);
        }
        for (Runnable r : later) r.run();
    }

    @Nullable
    private static CityGuard guard(ServerLevel sl, Incident inc) {
        Entity e = sl.getEntity(inc.guard);
        return e instanceof CityGuard g && g.isAlive() ? g : null;
    }

    /** O guarda chegou: interrompe o saque (fecha o baú) e abre o diálogo com a acusação. */
    private static void arrive(ServerPlayer p, CityGuard g, Incident inc) {
        if (ACTIVE.get(p.getUUID()) != inc || inc.talking()) return;
        long now = p.serverLevel().getGameTime();
        inc.arrived = now;
        inc.lastPrompt = now;
        inc.lastNag = now;
        if (p.containerMenu != p.inventoryMenu) p.closeContainer();
        g.getNavigation().stop();
        g.faceFor(p, 200);
        g.playAction("scold");
        open(p, g, inc, CityLines.pick("confront.accuse." + inc.offense.id, g.getRandom(), p.getDisplayName()));
        sendStatus(p, inc, g, now);
    }

    /** Abre/atualiza a tela da abordagem. */
    private static void open(ServerPlayer p, CityGuard g, Incident inc, Component line) {
        inc.screenOpen = true;
        CityDialogue.sendConfront(p, g, line, options(p, inc), note(p, inc));
    }

    /**
     * O jogador clicou no guarda (diálogo normal): se é o guarda da abordagem dele, volta para a tela da abordagem.
     * true = tratado aqui.
     */
    public static boolean reopen(ServerPlayer p, CityGuard g) {
        Incident inc = ACTIVE.get(p.getUUID());
        if (inc == null || !inc.guard.equals(g.getUUID())) return false;
        if (!inc.talking()) {
            arrive(p, g, inc);
        } else {
            inc.lastPrompt = Math.max(inc.lastPrompt, p.serverLevel().getGameTime() - ANSWER_TIMEOUT / 2);
            open(p, g, inc, CityLines.pick("confront.waiting", g.getRandom(), p.getDisplayName()));
        }
        return true;
    }

    /** Fechou a tela sem responder: o guarda espera (e reclama). */
    public static void onScreenClosed(ServerPlayer p, CityNpc npc) {
        Incident inc = ACTIVE.get(p.getUUID());
        if (inc == null || !inc.guard.equals(npc.getUUID()) || !inc.talking()) return;
        inc.screenOpen = false;
        inc.lastNag = p.serverLevel().getGameTime();
        npc.say(CityLines.pick("confront.waiting", npc.getRandom(), p.getDisplayName()));
    }

    /** O jogador está sendo abordado por este guarda? */
    public static boolean isConfronting(ServerPlayer p, CityNpc npc) {
        Incident inc = ACTIVE.get(p.getUUID());
        return inc != null && inc.guard.equals(npc.getUUID());
    }

    // =====================================================================================
    //  Respostas
    // =====================================================================================
    public static void answer(ServerPlayer p, CityNpc npc, String option) {
        Incident inc = ACTIVE.get(p.getUUID());
        if (inc == null || !(npc instanceof CityGuard g) || !inc.guard.equals(g.getUUID())) return;
        if (!inc.talking()) {
            arrive(p, g, inc);
            return;
        }
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        switch (option) {
            case "persuade" -> {
                if (inc.persuadeTried) return;
                inc.persuadeTried = true;
                Persuasion ps = persuasion(p, inc);
                MagicApi.addCounter(p, "cities.persuade_try", 1);
                if (p.getRandom().nextInt(100) < ps.chance()) {
                    MagicApi.addCounter(p, "cities.persuade", 1);
                    forgiveStrike(p, inc.faction);
                    refund(p, inc, inc.repLost / 2, "reason.isekai_mahou.cities.persuaded");
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, g.getX(), g.getY() + g.getBbHeight() + 0.2, g.getZ(), 8, 0.3, 0.2, 0.3, 0);
                    sl.playSound(null, g.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.9F);
                    resolve(p, g, inc, CityLines.pick("confront.persuade_ok", g.getRandom(), p.getDisplayName()), "bow");
                } else {
                    Reputation.add(p, inc.faction, -2, Component.translatable("reason.isekai_mahou.cities.lied"));
                    inc.lastPrompt = now;
                    sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, g.getX(), g.getY() + g.getBbHeight() + 0.2, g.getZ(), 4, 0.3, 0.2, 0.3, 0);
                    sl.playSound(null, g.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.8F);
                    g.playAction("scold");
                    open(p, g, inc, CityLines.pick("confront.persuade_fail", g.getRandom(), p.getDisplayName()));
                }
            }
            case "bribe" -> {
                if (inc.bribeRefused) return;
                int cost = bribeCost(p, inc);
                if (CityPlayer.coins(p) < cost && !p.getAbilities().instabuild) {
                    open(p, g, inc, CityLines.line("confront.no_coins"));
                    return;
                }
                if (honest(g)) {
                    inc.bribeRefused = true;
                    inc.lastPrompt = now;
                    Reputation.add(p, inc.faction, -3, Component.translatable("reason.isekai_mahou.cities.bribe_attempt"));
                    sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, g.getX(), g.getY() + g.getBbHeight() + 0.2, g.getZ(), 6, 0.3, 0.2, 0.3, 0);
                    sl.playSound(null, g.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.7F);
                    g.playAction("scold");
                    open(p, g, inc, CityLines.pick("confront.bribe_refuse", g.getRandom(), p.getDisplayName()));
                    return;
                }
                CityPlayer.pay(p, cost);
                MagicApi.addCounter(p, "cities.bribe", 1);
                Reputation.add(p, "underworld", 1, Component.translatable("reason.isekai_mahou.cities.bribe"));
                sl.playSound(null, g.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.NEUTRAL, 0.8F, 1.4F);
                sl.playSound(null, g.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.6F, 0.6F);
                resolve(p, g, inc, CityLines.pick("confront.bribe_ok", g.getRandom(), p.getDisplayName()), "wave");
            }
            case "return" -> {
                Evidence ev = evidence(sl, p, inc);
                if (!ev.applicable()) return;
                if (!ev.missing().isEmpty()) {
                    open(p, g, inc, CityLines.line("confront.return_missing", describe(ev.missing())));
                    return;
                }
                boolean clean = ev.owed().isEmpty() && inc.broken.isEmpty();
                giveBack(sl, p, inc, ev);
                forgiveStrike(p, inc.faction);
                refund(p, inc, clean ? inc.repLost : inc.repLost / 2, clean ? "reason.isekai_mahou.cities.innocent"
                        : "reason.isekai_mahou.cities.returned");
                MagicApi.addCounter(p, "cities.returned", 1);
                sl.playSound(null, g.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
                resolve(p, g, inc, CityLines.pick(clean ? "confront.return_clean" : "confront.return_ok", g.getRandom(),
                        p.getDisplayName()), clean ? "bow" : "wave");
            }
            case "flee" -> {
                ACTIVE.remove(p.getUUID());
                escalate(p, inc, g, "flee", true);
            }
            case "fight" -> {
                ACTIVE.remove(p.getUUID());
                escalate(p, inc, g, "fight", true);
            }
            default -> { }
        }
    }

    /** Fim pacífico: o guarda fala e volta à ronda; a tela vira o diálogo normal com a fala dele. */
    private static void resolve(ServerPlayer p, CityGuard g, Incident inc, Component line, String anim) {
        ACTIVE.remove(p.getUUID());
        finish(p, inc, g);
        g.playAction(anim);
        CityDialogue.send(p, g, line);
    }

    /** Limpa a abordagem (sem punição). */
    private static void finish(ServerPlayer p, Incident inc, @Nullable CityGuard g) {
        if (g == null && p.level() instanceof ServerLevel sl) g = guard(sl, inc);
        if (g != null && p.getUUID().equals(g.confronting())) {
            g.clearConfronting();
            g.alert(CityNpc.ALERT_NONE, 0);
        }
        CitiesNetwork.send(p, CitiesNetwork.Confront.none());
    }

    /**
     * A conversa virou caso de polícia: Procurado no reino, reputação e o guarda parte para cima ("flee": com 4 s de
     * vantagem; "fight": já; "impatient": não respondeu; logout/dimensão: sem guarda).
     */
    private static void escalate(ServerPlayer p, Incident inc, @Nullable CityGuard g, String kind, boolean chose) {
        ACTIVE.remove(p.getUUID());
        if (g != null) {
            g.clearConfronting();
            g.alert(CityNpc.ALERT_NONE, 0);
        }
        boolean fight = kind.equals("fight");
        CityCrimes.makeWanted(p, inc.faction, fight ? FIGHT_WANTED : FLEE_WANTED, fight ? CityServices.HURT_BAIL : FLEE_BAIL);
        Reputation.add(p, inc.faction, fight ? -8 : -5, CityLines.tr("reason.isekai_mahou.cities." + (fight ? "resisted" : "fled")));
        CitiesNetwork.send(p, CitiesNetwork.Confront.none());
        if (g != null) {
            CitiesNetwork.send(p, CityDialogue.closeNow(g));
            g.say(CityLines.pick("confront." + kind, g.getRandom(), p.getDisplayName()));
            if (kind.equals("flee") && chose) {
                // Escapuliu: vantagem de alguns segundos.
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, false, false, true));
                g.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2, false, false, false));
                if (p.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 10, 0.3, 0.1, 0.3, 0.02);
                    sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.6F);
                }
            } else {
                g.playAction("attack");
            }
            g.setTarget(p);
            if (p.level() instanceof ServerLevel sl) {
                whistle(sl, g);
                CityCrimes.callGuards(sl, g, p, inc.faction);
            }
        }
        p.displayClientMessage(CityLines.tr("message.isekai_mahou.cities.confront." + kind).withColor(0xD03A2C), false);
    }

    private static void refund(ServerPlayer p, Incident inc, int amount, String reasonKey) {
        if (amount > 0) Reputation.add(p, inc.faction, amount, Component.translatable(reasonKey));
    }

    // =====================================================================================
    //  Opções da tela
    // =====================================================================================
    private static List<CitiesNetwork.Service> options(ServerPlayer p, Incident inc) {
        List<CitiesNetwork.Service> out = new ArrayList<>();
        Persuasion ps = persuasion(p, inc);
        MutableComponent why = Component.translatable("screen.isekai_mahou.cities.confront.persuade.desc");
        for (Component part : ps.parts()) why.append("\n").append(part);
        out.add(new CitiesNetwork.Service("persuade", 0, !inc.persuadeTried,
                Component.translatable("screen.isekai_mahou.cities.confront.persuade", ps.chance()),
                inc.persuadeTried ? Component.translatable("screen.isekai_mahou.cities.confront.persuade.used") : why));
        int cost = bribeCost(p, inc);
        out.add(new CitiesNetwork.Service("bribe", cost, !inc.bribeRefused,
                Component.translatable("screen.isekai_mahou.cities.confront.bribe"),
                Component.translatable(inc.bribeRefused ? "screen.isekai_mahou.cities.confront.bribe.refused"
                        : "screen.isekai_mahou.cities.confront.bribe.desc")));
        Evidence ev = evidence(p.serverLevel(), p, inc);
        if (ev.applicable()) {
            boolean clean = ev.owed().isEmpty() && inc.broken.isEmpty();
            Component desc = !ev.missing().isEmpty()
                    ? Component.translatable("screen.isekai_mahou.cities.confront.return.missing", describe(ev.missing()))
                    : clean ? Component.translatable("screen.isekai_mahou.cities.confront.return.clean_desc")
                    : Component.translatable("screen.isekai_mahou.cities.confront.return.desc", describe(ev.owed()), inc.broken.size());
            out.add(new CitiesNetwork.Service("return", 0, ev.missing().isEmpty(),
                    Component.translatable(clean ? "screen.isekai_mahou.cities.confront.return.clean"
                            : "screen.isekai_mahou.cities.confront.return"), desc));
        }
        out.add(new CitiesNetwork.Service("flee", 0, true, Component.translatable("screen.isekai_mahou.cities.confront.flee"),
                Component.translatable("screen.isekai_mahou.cities.confront.flee.desc", FLEE_WANTED / 1200)));
        out.add(new CitiesNetwork.Service("fight", 0, true, Component.translatable("screen.isekai_mahou.cities.confront.fight"),
                Component.translatable("screen.isekai_mahou.cities.confront.fight.desc", FIGHT_WANTED / 1200)));
        return out;
    }

    /** Texto sob a fala: o que viram, reputação perdida e a reincidência. */
    private static Component note(ServerPlayer p, Incident inc) {
        MutableComponent n = CityLines.tr("screen.isekai_mahou.cities.confront.note." + inc.offense.id);
        if (inc.repLost > 0) n.append(" ").append(Component.translatable("screen.isekai_mahou.cities.confront.note.rep", inc.repLost));
        n.append("\n").append(Component.translatable(inc.strikes >= MAX_STRIKES - 1
                ? "screen.isekai_mahou.cities.confront.note.last" : "screen.isekai_mahou.cities.confront.note.strikes",
                inc.strikes, MAX_STRIKES));
        return n;
    }

    // ---------------------------------------------------------------- Persuadir
    public record Persuasion(int chance, List<Component> parts) {}

    static Persuasion persuasion(ServerPlayer p, Incident inc) {
        List<Component> parts = new ArrayList<>();
        int c = 40;
        parts.add(part("base", 40));
        Reputation.Standing st = Reputation.standing(p, inc.faction);
        int rep = switch (st) {
            case UNFRIENDLY -> -15;
            case FRIENDLY -> 10;
            case HONORED -> 18;
            case REVERED -> 25;
            case EXALTED -> 35;
            default -> 0;
        };
        if (rep != 0) {
            c += rep;
            parts.add(Component.translatable("screen.isekai_mahou.cities.confront.bonus.standing", st.displayName(), signed(rep)));
        }
        MagicPath path = MagicApi.get(p).path();
        int cls = switch (path.id) {
            case "rogue" -> 15;
            case "gambler", "enchanter", "knight" -> 10;
            default -> 0;
        };
        if (cls != 0) {
            c += cls;
            parts.add(CityLines.tr("screen.isekai_mahou.cities.confront.bonus.class." + path.id, signed(cls)));
        }
        String race = raceId(p);
        CityKingdom k = CityRealm.byFaction(inc.faction);
        int rc = switch (race) {
            case "human" -> 5;
            case "elf" -> 8;
            case "dwarf" -> k == CityKingdom.NORDHEIM ? 10 : 0;
            case "dragonborn" -> k == CityKingdom.SAHRA ? 8 : 0;
            case "half_orc" -> -5;
            case "demon" -> -10;
            default -> 0;
        };
        if (rc != 0) {
            c += rc;
            parts.add(CityLines.tr("screen.isekai_mahou.cities.confront.bonus.race." + race, signed(rc)));
        }
        if (inc.cityId.equals(Allegiance.of(p))) {
            c += 10;
            parts.add(part("citizen", 10));
        }
        if (Reputation.isAllied(p, inc.faction)) {
            c += 10;
            parts.add(part("allied", 10));
        }
        if (inc.strikes > 1) {
            int s = -15 * (inc.strikes - 1);
            c += s;
            parts.add(part("strikes", s));
        }
        int crime = switch (inc.offense) {
            case THEFT -> -10;
            case BLOCK -> -Math.min(15, 2 * Math.max(0, inc.broken.size() - 2));
            default -> 0;
        };
        if (crime != 0) {
            c += crime;
            parts.add(part("crime", crime));
        }
        return new Persuasion(Math.max(5, Math.min(95, c)), parts);
    }

    private static Component part(String id, int v) {
        return CityLines.tr("screen.isekai_mahou.cities.confront.bonus." + id, signed(v));
    }

    private static String signed(int v) {
        return (v > 0 ? "+" : "") + v + "%";
    }

    /** Raça do jogador (módulo character, lida pelo id do anexo — sem o módulo: ""). */
    private static String raceId(ServerPlayer p) {
        try {
            AttachmentType<?> type = NeoForgeRegistries.ATTACHMENT_TYPES.get(IsekaiMahou.id("character"));
            if (type == null) return "";
            if (!p.hasData(type)) return "human";
            Object data = p.getData(type);
            if (data instanceof INBTSerializable<?> s) {
                Tag t = s.serializeNBT(p.registryAccess());
                if (t instanceof CompoundTag c) return c.getString("race");
            }
        } catch (RuntimeException ignored) {
            // anexo de outro formato: sem bônus de raça
        }
        return "";
    }

    // ---------------------------------------------------------------- Propina
    private static int bribeCost(ServerPlayer p, Incident inc) {
        int base = switch (inc.offense) {
            case CHEST -> 12;
            case BLOCK -> Math.min(30, 6 + 2 * inc.broken.size());
            case THEFT -> 18;
        };
        float mult = (1.0F + 0.5F * Math.max(0, inc.strikes - 1)) * Reputation.priceMultiplier(p, inc.faction);
        return Math.max(1, Math.round(base * mult));
    }

    /** Guardas honestos não aceitam propina (fixo por guarda; Nordheim é mais honrado, Sahra menos). */
    private static boolean honest(CityGuard g) {
        int pct = switch (g.kingdom()) {
            case NORDHEIM -> 40;
            case SAHRA -> 15;
            default -> 30;
        };
        return Math.floorMod(g.getUUID().getLeastSignificantBits() ^ 0x5DEECE66DL, 100L) < pct;
    }

    // ---------------------------------------------------------------- Devolver
    /**
     * @param applicable dá para devolver (baú anotado ou blocos quebrados)
     * @param owed       o que o jogador deve (sumiu do baú + o que caiu dos blocos)
     * @param missing    o que falta no inventário dele para devolver tudo
     */
    private record Evidence(boolean applicable, List<ItemStack> owed, List<ItemStack> missing) {}

    private static Evidence evidence(ServerLevel sl, ServerPlayer p, Incident inc) {
        boolean applicable = inc.chestPos != null || !inc.broken.isEmpty();
        if (!applicable) return new Evidence(false, List.of(), List.of());
        List<ItemStack> owed = new ArrayList<>();
        if (inc.chestPos != null) {
            Container c = container(sl, inc.chestPos);
            if (c != null) owed.addAll(deficit(inc.chestSnapshot, snapshot(c)));
        }
        for (ItemStack s : inc.drops) addMerged(owed, s);
        List<ItemStack> missing = new ArrayList<>();
        for (ItemStack s : owed) {
            int have = count(p, s);
            if (have < s.getCount()) missing.add(s.copyWithCount(s.getCount() - have));
        }
        return new Evidence(true, owed, missing);
    }

    /** Tira do jogador o que ele deve, põe de volta no baú e conserta os blocos (com faíscas). */
    private static void giveBack(ServerLevel sl, ServerPlayer p, Incident inc, Evidence ev) {
        List<ItemStack> chestOwed = new ArrayList<>();
        if (inc.chestPos != null) {
            Container c = container(sl, inc.chestPos);
            if (c != null) chestOwed = deficit(inc.chestSnapshot, snapshot(c));
        }
        for (ItemStack s : ev.owed()) take(p, s);
        if (inc.chestPos != null) {
            Container c = container(sl, inc.chestPos);
            for (ItemStack s : chestOwed) {
                ItemStack left = c != null ? insert(c, s.copy()) : s.copy();
                if (!left.isEmpty()) {
                    ItemEntity ie = new ItemEntity(sl, inc.chestPos.getX() + 0.5, inc.chestPos.getY() + 1.1, inc.chestPos.getZ() + 0.5, left);
                    sl.addFreshEntity(ie);
                }
            }
            if (c != null) c.setChanged();
        }
        for (int i = inc.broken.size() - 1; i >= 0; i--) {
            Broken b = inc.broken.get(i);
            if (!sl.isLoaded(b.pos()) || !sl.getBlockState(b.pos()).canBeReplaced()) continue;
            sl.setBlock(b.pos(), b.state(), 3);
            sl.sendParticles(ParticleTypes.WAX_ON, b.pos().getX() + 0.5, b.pos().getY() + 0.5, b.pos().getZ() + 0.5, 6, 0.35, 0.35, 0.35, 0.02);
        }
        if (!inc.broken.isEmpty()) {
            BlockPos at = inc.broken.get(0).pos();
            sl.playSound(null, at, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.5F);
        }
        p.getInventory().setChanged();
    }

    @Nullable
    private static Container container(ServerLevel sl, BlockPos pos) {
        if (!sl.isLoaded(pos)) return null;
        BlockState st = sl.getBlockState(pos);
        if (st.getBlock() instanceof ChestBlock cb) {
            Container c = ChestBlock.getContainer(cb, st, sl, pos, true);
            if (c != null) return c;
        }
        BlockEntity be = sl.getBlockEntity(pos);
        return be instanceof Container c ? c : null;
    }

    private static List<ItemStack> snapshot(Container c) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty()) addMerged(out, s);
        }
        return out;
    }

    /** O que havia antes e não há mais (por item + componentes). */
    private static List<ItemStack> deficit(List<ItemStack> before, List<ItemStack> now) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack b : before) {
            int have = 0;
            for (ItemStack n : now) if (ItemStack.isSameItemSameComponents(b, n)) have += n.getCount();
            if (have < b.getCount()) out.add(b.copyWithCount(b.getCount() - have));
        }
        return out;
    }

    private static void addMerged(List<ItemStack> list, ItemStack s) {
        for (ItemStack e : list) {
            if (ItemStack.isSameItemSameComponents(e, s)) {
                e.grow(s.getCount());
                return;
            }
        }
        list.add(s.copy());
    }

    private static int count(Player p, ItemStack proto) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (ItemStack.isSameItemSameComponents(s, proto)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (ItemStack.isSameItemSameComponents(s, proto)) n += s.getCount();
        return n;
    }

    private static void take(Player p, ItemStack proto) {
        int left = proto.getCount();
        for (List<ItemStack> list : List.of(p.getInventory().items, p.getInventory().offhand)) {
            for (ItemStack s : list) {
                if (left <= 0) return;
                if (!ItemStack.isSameItemSameComponents(s, proto)) continue;
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
        }
    }

    /** Põe no container (junta em pilhas iguais, depois espaços vazios); devolve o que sobrou. */
    private static ItemStack insert(Container c, ItemStack s) {
        for (int i = 0; i < c.getContainerSize() && !s.isEmpty(); i++) {
            ItemStack in = c.getItem(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, s)) {
                int room = Math.min(c.getMaxStackSize(in), in.getMaxStackSize()) - in.getCount();
                int t = Math.min(room, s.getCount());
                if (t > 0) {
                    in.grow(t);
                    s.shrink(t);
                }
            }
        }
        for (int i = 0; i < c.getContainerSize() && !s.isEmpty(); i++) {
            if (c.getItem(i).isEmpty() && c.canPlaceItem(i, s)) {
                int t = Math.min(s.getCount(), Math.min(c.getMaxStackSize(s), s.getMaxStackSize()));
                c.setItem(i, s.copyWithCount(t));
                s.shrink(t);
            }
        }
        return s;
    }

    /** "3× Pão, 1× Esmeralda" (até 4 tipos). */
    private static Component describe(List<ItemStack> items) {
        MutableComponent out = Component.empty();
        int n = 0;
        for (ItemStack s : items) {
            if (n == 4) {
                out.append(Component.translatable("screen.isekai_mahou.cities.confront.more", items.size() - 4));
                break;
            }
            if (n > 0) out.append(", ");
            out.append(Component.translatable("screen.isekai_mahou.cities.confront.item", s.getCount(), s.getHoverName()));
            n++;
        }
        if (n == 0) out.append(Component.translatable("screen.isekai_mahou.cities.confront.nothing"));
        return out;
    }

    // =====================================================================================
    //  Rede, efeitos e limpeza
    // =====================================================================================
    private static void sendStatus(ServerPlayer p, Incident inc, CityGuard g, long now) {
        int secs = inc.talking() ? (int) Math.max(0, (ANSWER_TIMEOUT - (now - inc.lastPrompt)) / 20)
                : (int) Math.max(0, (APPROACH_TIMEOUT - (now - inc.started)) / 20);
        CitiesNetwork.send(p, new CitiesNetwork.Confront(g.getId(), g.getDisplayName(), secs, inc.talking()));
    }

    /** Apito da guarda (duas notas agudas) + partículas de "!" */
    private static void whistle(ServerLevel sl, CityGuard g) {
        sl.playSound(null, g.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 1.4F, 1.9F);
        sl.playSound(null, g.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 1.2F, 1.6F);
        sl.sendParticles(ParticleTypes.NOTE, g.getX(), g.getY() + g.getBbHeight() + 0.5, g.getZ(), 2, 0.2, 0.1, 0.2, 0.5);
    }

    /** Saiu do jogo no meio da abordagem: conta como fuga. */
    public static void onLogout(ServerPlayer p) {
        Incident inc = ACTIVE.remove(p.getUUID());
        if (inc != null) {
            CityGuard g = p.level() instanceof ServerLevel sl ? guard(sl, inc) : null;
            if (g != null) {
                g.clearConfronting();
                g.alert(CityNpc.ALERT_NONE, 0);
            }
            CityCrimes.makeWanted(p, inc.faction, FLEE_WANTED, FLEE_BAIL);
            Reputation.add(p, inc.faction, -5, Component.translatable("reason.isekai_mahou.cities.fled"));
        }
        RECENT_THEFT.remove(p.getUUID());
    }

    public static void clear() {
        ACTIVE.clear();
        RECENT_THEFT.clear();
        PLACED.clear();
    }

    private CityConfront() {}
}
