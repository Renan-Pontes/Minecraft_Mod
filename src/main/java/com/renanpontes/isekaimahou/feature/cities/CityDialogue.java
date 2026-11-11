package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.cities.entity.CityGuard;
import com.renanpontes.isekaimahou.feature.cities.entity.CityLord;
import com.renanpontes.isekaimahou.feature.cities.entity.CityMerchant;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;
import com.renanpontes.isekaimahou.feature.cities.entity.Citizen;
import com.renanpontes.isekaimahou.feature.cities.entity.Profession;
import com.renanpontes.isekaimahou.feature.cities.network.CitiesNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Diálogo com os NPCs da cidade (servidor): escolhe a fala pela reputação, monta os botões (Conversar / Trocar /
 * Serviços / Rumores / Despedir-se) e responde aos cliques da tela do cliente.
 */
public final class CityDialogue {
    private static final double MAX_DIST_SQR = 8.0 * 8.0;

    /** Clique direito no NPC: abre a tela com a saudação. */
    public static void open(ServerPlayer player, CityNpc npc) {
        RandomSource r = npc.getRandom();
        // v1.0: o guarda que está abordando o jogador volta para a conversa da abordagem.
        if (npc instanceof CityGuard g && CityConfront.reopen(player, g)) return;
        // v1.0: cidade em guerra com o jogador — ninguém conversa nem negocia.
        if (npc.isWarEnemy(player)) {
            sendEnemy(player, npc);
            return;
        }
        String mood = mood(player, npc);
        MutableComponent line;
        boolean wantedHere = CityCrimes.isWanted(player, npc.kingdomFaction());
        if (npc instanceof CityGuard && wantedHere) {
            line = CityLines.pick("guard.wanted", r);
        } else if (npc instanceof Citizen && wantedHere) {
            line = CityLines.pick("citizen.wanted", r);
        } else if (npc instanceof CityMerchant m && !mood.equals("hostile")) {
            line = Component.translatable(CityLines.PREFIX + "merchant." + m.profession().id + ".greet."
                    + r.nextInt(CityLines.PROFESSION_GREETINGS), player.getDisplayName());
        } else {
            line = CityLines.pick(npc.kindId() + "." + mood, r, player.getDisplayName(), npc.kingdomName());
        }
        if (!mood.equals("hostile") && !mood.equals("bad")) npc.playAction(npc instanceof CityLord ? "bow" : "wave");
        send(player, npc, line);
    }

    /** Clique num botão da tela. */
    public static void handle(ServerPlayer player, CitiesNetwork.Action action) {
        Entity e = player.serverLevel().getEntity(action.entityId());
        if (!(e instanceof CityNpc npc) || !npc.isAlive() || npc.distanceToSqr(player) > MAX_DIST_SQR) return;
        if (action.action().equals("confront")) {
            CityConfront.answer(player, npc, action.arg());
            return;
        }
        if (action.action().equals("close") && CityConfront.isConfronting(player, npc)) {
            CityConfront.onScreenClosed(player, npc);
            return;
        }
        if (!action.action().equals("close") && npc instanceof CityGuard g && CityConfront.isConfronting(player, npc)) {
            CityConfront.reopen(player, g);
            return;
        }
        if (!action.action().equals("close") && npc.isWarEnemy(player)) {
            sendEnemy(player, npc);
            return;
        }
        npc.faceFor(player, 120);
        RandomSource r = npc.getRandom();
        switch (action.action()) {
            case "talk" -> send(player, npc, talkLine(player, npc, r));
            case "trade" -> {
                if (!(npc instanceof CityMerchant m)) return;
                if (!Reputation.canTrade(player, m.faction())) {
                    player.level().playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
                    send(player, npc, CityLines.pick("merchant.refuse", r, Reputation.faction(m.faction()) != null
                            ? Reputation.faction(m.faction()).coloredName() : Component.literal(m.faction())));
                    return;
                }
                if (!m.openTrades(player)) send(player, npc, CityLines.line("trade_busy"));
            }
            case "rumor" -> send(player, npc, CityRumors.tell(player, npc));
            case "service" -> send(player, npc, CityServices.perform(player, npc, action.arg()));
            case "close" -> {
                if (!mood(player, npc).equals("hostile")) npc.playAction("wave");
            }
            default -> { }
        }
    }

    /** "Conversar": outra fala (cidadão às vezes dá dicas; o Lorde dá notícias do reino). */
    private static MutableComponent talkLine(ServerPlayer player, CityNpc npc, RandomSource r) {
        String mood = mood(player, npc);
        if (mood.equals("hostile")) return CityLines.pick(npc.kindId() + ".hostile", r, player.getDisplayName(), npc.kingdomName());
        if (npc instanceof Citizen) {
            if (player.level().isNight() && r.nextInt(3) == 0) return CityLines.pick("citizen.night", r);
            if (r.nextInt(100) < 40) return CityLines.pick("citizen.hint", r);
        }
        if (npc instanceof CityLord && r.nextBoolean()) {
            MutableComponent news = otherCityNews(player, npc, r);
            if (news != null && r.nextBoolean()) return news;
            return r.nextInt(3) == 0 ? CityLines.kingdomLord(npc.kingdom(), r) : CityLines.pick("lord.news", r);
        }
        if (npc instanceof CityMerchant m && r.nextInt(100) < 45) {
            return Component.translatable(CityLines.PREFIX + "merchant." + m.profession().id + ".ambient."
                    + r.nextInt(CityLines.PROFESSION_AMBIENT));
        }
        return CityLines.pick(npc.kindId() + "." + mood, r, player.getDisplayName(), npc.kingdomName());
    }

    /** O Lorde conta onde fica outra cidade conhecida (nome, distância, direção). */
    private static MutableComponent otherCityNews(ServerPlayer player, CityNpc npc, RandomSource r) {
        ServerLevel sl = player.serverLevel();
        Places.Place here = CityPlaces.cityAt(sl, npc.blockPosition());
        List<Places.Place> others = new ArrayList<>();
        for (Places.Place p : Places.all(sl)) {
            if ((p.type().equals("city") || p.type().equals("capital")) && (here == null || !p.id().equals(here.id()))) others.add(p);
        }
        if (others.isEmpty()) return null;
        Places.Place p = others.get(r.nextInt(others.size()));
        BlockPos c = p.center();
        int dist = (int) Math.sqrt(npc.blockPosition().distSqr(c));
        return CityLines.line("lord.city_news", p.name(), dist, CityRumors.direction(npc.blockPosition(), c));
    }

    /** Humor pelo patamar na facção do NPC. */
    public static String mood(ServerPlayer player, CityNpc npc) {
        return CityLines.mood(Reputation.standing(player, npc.faction()));
    }

    /** Monta e manda a tela com a fala dada; o NPC também "fala" (balão + animação). */
    public static void send(ServerPlayer player, CityNpc npc, Component line) {
        npc.say(line);
        String faction = npc.faction();
        Reputation.Faction f = Reputation.faction(faction);
        Reputation.Standing st = Reputation.standing(player, faction);
        int buttons = CitiesNetwork.BTN_TALK;
        if (npc instanceof CityMerchant) {
            buttons |= Reputation.canTrade(player, faction) ? CitiesNetwork.BTN_TRADE : CitiesNetwork.BTN_TRADE_LOCKED;
        }
        if (rumorsFrom(npc)) buttons |= CitiesNetwork.BTN_RUMORS;
        List<CitiesNetwork.Service> services = CityServices.list(player, npc);
        if (!services.isEmpty()) buttons |= CitiesNetwork.BTN_SERVICES;
        Component factionName = f != null ? f.name() : Component.literal(faction);
        CitiesNetwork.send(player, new CitiesNetwork.Dialogue(npc.getId(), npc.kindId(), npc.getDisplayName(), role(npc),
                factionName, st.displayName(), f != null ? f.color() : 0x3F6FD8, line, buttons, services, CityPlayer.coins(player),
                Component.empty()));
    }

    /**
     * Tela da abordagem da guarda (v1.0): a acusação como fala, {@code note} com o que viram e a reincidência e as
     * respostas como "serviços" (Persuadir, Pagar propina, Devolver, Fugir, Brigar).
     */
    public static void sendConfront(ServerPlayer player, CityGuard guard, Component line, List<CitiesNetwork.Service> options,
                                    Component note) {
        guard.say(line);
        String faction = guard.faction();
        Reputation.Faction f = Reputation.faction(faction);
        Reputation.Standing st = Reputation.standing(player, faction);
        Component factionName = f != null ? f.name() : Component.literal(faction);
        CitiesNetwork.send(player, new CitiesNetwork.Dialogue(guard.getId(), guard.kindId(), guard.getDisplayName(), role(guard),
                factionName, st.displayName(), f != null ? f.color() : 0x3F6FD8, line, CitiesNetwork.BTN_CONFRONT, options,
                CityPlayer.coins(player), note));
    }

    /** Pacote que fecha a tela de diálogo deste NPC no cliente (a conversa virou briga). */
    public static CitiesNetwork.Dialogue closeNow(CityNpc npc) {
        return new CitiesNetwork.Dialogue(npc.getId(), npc.kindId(), npc.getDisplayName(), Component.empty(), Component.empty(),
                Component.empty(), 0, Component.empty(), CitiesNetwork.BTN_CLOSE_NOW, List.of(), 0, Component.empty());
    }

    /** Cidade em guerra com o jogador: fala hostil, sem comércio, serviços nem rumores. */
    private static void sendEnemy(ServerPlayer player, CityNpc npc) {
        String group = npc instanceof CityGuard ? "guard.enemy" : npc instanceof CityMerchant ? "merchant.enemy" : "citizen.enemy";
        Component line = CityLines.pick(group, npc.getRandom(), npc.kingdomName());
        npc.say(line);
        String faction = npc.faction();
        Reputation.Faction f = Reputation.faction(faction);
        Component factionName = f != null ? f.name() : Component.literal(faction);
        CitiesNetwork.send(player, new CitiesNetwork.Dialogue(npc.getId(), npc.kindId(), npc.getDisplayName(), role(npc),
                factionName, Component.translatable("screen.isekai_mahou.cities.at_war").withColor(0xD03A2C), 0xD03A2C, line,
                CitiesNetwork.BTN_ENEMY, List.of(), CityPlayer.coins(player), Component.empty()));
    }

    /** Quem conta rumores: cidadãos, taverneiro, receptador e guardas. */
    private static boolean rumorsFrom(CityNpc npc) {
        if (npc instanceof Citizen || npc instanceof CityGuard) return true;
        return npc instanceof CityMerchant m && (m.profession() == Profession.INNKEEPER || m.profession() == Profession.FENCE);
    }

    /** "Guarda da Cidade", "Ferreira", "Lorde de Valdória"... */
    public static Component role(CityNpc npc) {
        if (npc instanceof CityMerchant m) return m.profession().displayName(m.female());
        String suffix = npc.female() ? ".f" : "";
        if (npc instanceof CityLord && npc.level() instanceof ServerLevel sl) {
            Places.Place p = CityPlaces.cityAt(sl, npc.blockPosition());
            if (p != null) return Component.translatable(CityRealm.key("role.isekai_mahou.cities.lord_of" + suffix, npc.kingdom()), p.name());
        }
        if (npc instanceof CityLord) return CityLines.tr(CityRealm.key("role.isekai_mahou.cities.lord" + suffix, npc.kingdom()));
        return CityLines.tr("role.isekai_mahou.cities." + npc.kindId() + suffix);
    }

    private CityDialogue() {}
}
