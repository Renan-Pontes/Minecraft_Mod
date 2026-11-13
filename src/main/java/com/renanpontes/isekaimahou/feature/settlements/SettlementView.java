package com.renanpontes.isekaimahou.feature.settlements;

import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Monta o estado da tela de uma cidade para um jogador (NBT; textos já como JSON de Component).
 * Modos: {@code manage} (o Senhor/fundador), {@code visitor} (cidade governada por outro ou cidade comum),
 * {@code candidate} (cidade de reino que dá para conquistar — Juramento e desafio).
 */
public final class SettlementView {

    public static CompoundTag build(ServerPlayer sp, String id) {
        MinecraftServer server = sp.server;
        HolderLookup.Provider reg = sp.registryAccess();
        CompoundTag t = new CompoundTag();
        Settlement s = SettlementService.city(server, id);
        Places.Place place = Places.get(sp.serverLevel(), id);
        if (s == null && place == null) {
            t.putString("Mode", "none");
            return t;
        }
        t.putString("City", id);
        Component name = s != null ? SettlementService.cityName(server, s) : place.name();
        t.putString("Name", json(name, reg));
        UUID leader = Allegiance.leader(server, id);
        t.putString("Lord", s != null && !s.ownerName.isEmpty() ? s.ownerName : leader != null ? nameOf(server, leader) : "");
        t.putBoolean("Citizen", id.equals(Allegiance.of(sp)));
        t.putInt("Citizens", Allegiance.citizens(server, id).size());
        t.putBoolean("Enemy", Allegiance.isEnemyOf(sp, id));
        if (place != null && !place.faction().isEmpty() && Reputation.faction(place.faction()) != null) {
            Reputation.Faction f = Reputation.faction(place.faction());
            t.putString("Faction", json(f.coloredName(), reg));
            t.putString("Standing", json(Reputation.standing(sp, f.id()).displayName(), reg));
        }
        wars(t, server, id, s, reg);
        if (s != null && !s.own() && s.owner == null && Conquest.conquerable(place)) {
            // v1.4: cidade de reino sem Senhor (abdicou/caiu): volta a ser conquistável, sem perder o estado
            t.putString("Mode", "candidate");
            t.putString("Kind", "city");
            t.putInt("Color", place.color());
            candidate(t, sp, place, reg);
        } else if (s != null) {
            boolean owner = sp.getUUID().equals(s.owner);
            boolean planner = s.canPlan(sp.getUUID());
            t.putString("Mode", planner ? "manage" : "visitor");
            t.putString("Kind", s.own() ? "own" : "conquered");
            t.putBoolean("Owner", owner);
            t.putBoolean("Planner", planner);
            t.putBoolean("Member", CityEconomy.isMember(server, s, sp));
            t.putBoolean("Claimable", s.own() && s.owner == null && id.equals(Allegiance.of(sp)));
            settlement(t, sp, s, planner, reg);
            CityViewExtra.fill(t, sp, s, owner, planner, reg);
        } else {
            t.putString("Mode", Conquest.conquerable(place) ? "candidate" : "visitor");
            t.putString("Kind", "city");
            t.putInt("Color", place.color());
            if (Conquest.conquerable(place)) candidate(t, sp, place, reg);
        }
        return t;
    }

    private static void settlement(CompoundTag t, ServerPlayer sp, Settlement s, boolean owner, HolderLookup.Provider reg) {
        MinecraftServer server = sp.server;
        t.putString("Style", s.style.id);
        t.putInt("Color", s.style.color);
        t.putInt("Level", s.level);
        t.putInt("MaxLevel", Blueprints.MAX_LEVEL);
        t.putInt("Points", s.points);
        t.putInt("Prev", Blueprints.pointsFor(s.style, s.level));
        t.putInt("Next", s.level >= Blueprints.MAX_LEVEL ? -1 : Blueprints.pointsFor(s.style, s.level + 1));
        t.putBoolean("NeedHall", s.own() && s.level == 3 && !s.has(Blueprint.TOWN_HALL));
        t.putInt("Affinity", s.affinity);
        t.putInt("Treasury", s.treasury);
        t.putInt("Tax", s.tax);
        t.putInt("Gates", s.gates);
        t.putInt("Order", s.militiaOrder);
        t.putInt("Income", s.lastIncome);
        t.putInt("AffDelta", s.lastAffinityDelta);
        t.putInt("TradesToday", s.tradesToday);
        t.putBoolean("FestivalOk", Lordship.festivalWait(server, s) == 0 && s.treasury >= SettlementService.FESTIVAL_COST);
        // v1.0 (coleta fácil): o cofre abre de qualquer lugar dentro da cidade
        t.putBoolean("AtHeart", CityActions.inCity(sp, s));
        t.putBoolean("WallDone", s.wallDone);
        CompoundTag pop = new CompoundTag(), cap = new CompoundTag();
        for (Settlement.Role r : Settlement.Role.values()) {
            pop.putInt(r.id(), s.population(r));
            cap.putInt(r.id(), s.cap(r));
        }
        t.put("Pop", pop);
        t.put("Cap", cap);
        t.putBoolean("CanDeclare", WarService.canDeclare(s));
        t.putBoolean("CanTrain", WarService.canTrain(s));
        // onda 7 (sky_island): Ilha Celeste — altitude e andamento da terra (cidade no chão: nada é enviado)
        SkyCity.view(t, s);
        // onda 7 (sky_access): onde a Corrente Ascendente toca o chão e se o Cais Celeste já existe
        SkyAccess.view(t, s);
        // onda 8 (water_city): tipo de terreno por id e o traço da Cidade das Águas
        WaterCity.view(t, s);
        if (!owner) return;
        ListTag projects = new ListTag();
        // onda 7 (city_pause): "em obra" = quem tem equipe agora (obra pausada libera os construtores para as outras)
        java.util.List<Project> crewed = CityPause.crewed(s);
        for (int i = 0; i < s.projects.size(); i++) {
            Project p = s.projects.get(i);
            CompoundTag e = new CompoundTag();
            e.putString("Name", json(CityPlanning.projectName(s, p), reg));
            e.putBoolean("Rush", Project.KIND_REPAIR.equals(p.kind));
            e.putInt("RushCost", CityActions.rushCost(s));
            boolean active = crewed.contains(p);
            e.putBoolean("Active", active);
            // onda 6 (city_pace): barra pelos DIAS, "dia X de Y", etapa e se aceita materiais
            CityPace.viewProject(e, server, s, p, active, reg);
            // onda 7 (sky_island): obra esperando a terra do lote se formar
            if (SkyCity.waitingLand(s, p)) {
                e.putBoolean("NoLand", true);
                // o motivo aparece já na linha da obra, no lugar da etapa ("Aguardando a terra se formar")
                e.putString("Stage", json(SettlementsLang.tr("sky.wait.land"), reg));
            }
            // onda 7 (city_pause): pausada/desmontando, se dá para desistir agora (em qualquer etapa) ou o motivo de só pausar
            CityPause.viewProject(e, s, p, reg);
            projects.add(e);
        }
        t.put("Projects", projects);
        t.putInt("QueueMax", SettlementService.MAX_QUEUE);
        if (s.own()) {
            ListTag bps = new ListTag();
            for (Blueprint bp : Blueprints.of(s.style)) {
                if (bp.isPlaza()) continue;
                CompoundTag e = new CompoundTag();
                e.putString("Key", bp.key());
                e.putString("Lang", bp.langKey());
                e.putInt("MinLevel", bp.minLevel());
                e.putInt("Points", bp.points());
                e.putInt("Count", s.count(bp.key()));
                e.putInt("Building", s.inProgress(bp.key()));
                e.putInt("Max", CityGrowth.maxCount(s, bp));
                e.putBoolean("Wonder", bp.isWonder());
                Component problem = SettlementService.buildProblem(s, bp);
                e.putString("Problem", problem == null ? "" : json(problem, reg));
                bps.add(e);
            }
            t.put("Blueprints", bps);
        }
        ListTag targets = new ListTag();
        for (Places.Place p : WarService.targets(server, s)) {
            CompoundTag e = new CompoundTag();
            e.putString("Id", p.id());
            e.putString("Name", json(p.name(), reg));
            e.putInt("Dist", (int) Math.sqrt(p.center().distSqr(s.heart)));
            e.putBoolean("War", Allegiance.areEnemies(server, s.id, p.id()));
            targets.add(e);
        }
        t.put("Targets", targets);
    }

    private static void wars(CompoundTag t, MinecraftServer server, String id, @javax.annotation.Nullable Settlement s, HolderLookup.Provider reg) {
        ListTag wars = new ListTag();
        Set<String> enemies = Allegiance.enemiesOf(server, id);
        for (String e : enemies) {
            CompoundTag w = new CompoundTag();
            w.putString("Id", e);
            w.putString("Name", json(Allegiance.cityName(server, e), reg));
            w.putBoolean("OfferIn", s != null && s.peaceOffers.contains(e));
            Settlement other = SettlementData.get(server).city(e);
            w.putBoolean("OfferOut", other != null && other.peaceOffers.contains(id));
            w.putBoolean("PlayerLed", other != null && other.owner != null);
            wars.add(w);
        }
        t.put("Wars", wars);
    }

    private static void candidate(CompoundTag t, ServerPlayer sp, Places.Place p, HolderLookup.Provider reg) {
        t.putBoolean("Exalted", Conquest.exalted(sp, p));
        SettlementData.Oath o = SettlementData.get(sp.server).oath(sp.getUUID());
        boolean here = o != null && o.city.equals(p.id());
        t.putBoolean("OathActive", here);
        if (o != null && !here) {
            Places.Place other = Places.get(sp.serverLevel(), o.city);
            if (other != null) t.putString("OathOther", json(other.name(), reg));
        }
        if (here) {
            t.putInt("Kills", o.kills);
            t.putInt("Tribute", o.tribute);
            t.putInt("Vigil", o.vigil);
            t.putBoolean("Seal", o.sealGiven);
            long now = sp.serverLevel().getGameTime();
            t.putInt("Retry", o.lastFail < 0 ? 0 : (int) Math.max(0, (Conquest.RETRY_TICKS - (now - o.lastFail)) / 20));
        }
        t.putInt("KillsNeed", Conquest.KILLS_NEEDED);
        t.putInt("TributeNeed", Conquest.TRIBUTE_NEEDED);
        t.putInt("VigilNeed", Conquest.VIGIL_NEEDED);
        t.putBoolean("HasSeal", sp.getInventory().hasAnyMatching(st -> st.is(SettlementsContent.OATH_SEAL.get())));
        ServerLevel sl = sp.serverLevel();
        t.putBoolean("Running", Conquest.challengeRunning(sl, p.id()));
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p.getGameProfile().getName();
        return server.getProfileCache() == null ? "?" : server.getProfileCache().get(id).map(gp -> gp.getName()).orElse("?");
    }

    static String json(Component c, HolderLookup.Provider reg) {
        return Component.Serializer.toJson(c, reg);
    }

    private SettlementView() {}
}
