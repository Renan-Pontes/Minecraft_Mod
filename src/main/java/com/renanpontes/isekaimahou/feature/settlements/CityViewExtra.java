package com.renanpontes.isekaimahou.feature.settlements;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Parte nova (v1.4) do estado da tela: necessidades, centro (nível, porte e requisitos), Pedidos do Povo, ataque,
 * planta dos terrenos, catálogo e conselho. Tudo em NBT tolerante (a tela ignora o que faltar).
 */
public final class CityViewExtra {

    static void fill(CompoundTag t, ServerPlayer sp, Settlement s, boolean owner, boolean planner, HolderLookup.Provider reg) {
        MinecraftServer server = sp.server;
        CityEconomy.Stats st = CityEconomy.stats(server, s);
        CompoundTag n = new CompoundTag();
        n.putInt("People", s.people);
        n.putInt("Housing", st.housing());
        n.putInt("Food", s.food);
        n.putInt("FoodProd", st.foodProd());
        n.putInt("FoodUse", st.foodUse());
        n.putInt("FoodCap", st.foodCap());
        n.putInt("FoodDays", CityEconomy.daysLeft(s.food, st.foodProd(), st.foodUse()));
        n.putInt("Fuel", s.fuel);
        n.putInt("FuelProd", st.fuelProd());
        n.putInt("FuelUse", st.fuelUse());
        n.putInt("FuelCap", st.fuelCap());
        n.putInt("FuelDays", CityEconomy.daysLeft(s.fuel, st.fuelProd(), st.fuelUse()));
        n.putInt("Sec", st.security());
        n.putInt("SecNeed", st.securityNeed());
        n.putInt("ApprovalFx", st.approvalFx());
        n.putInt("ProdPct", Math.round(st.prodMult() * 100));
        n.putInt("PeopleDelta", s.lastPeopleDelta);
        n.putInt("Festival", Lordship.festivalFood(s));
        n.putInt("RaidsWon", s.raidsWon);
        n.putInt("RaidsLost", s.raidsLost);
        t.put("Needs", n);

        t.putString("Tier", SettlementView.json(CityEconomy.tier(s.level), reg));
        ListTag reqs = new ListTag();
        for (CityEconomy.Req r : CityEconomy.centerReqs(server, s)) {
            CompoundTag e = new CompoundTag();
            e.putString("Text", SettlementView.json(r.text(), reg));
            e.putBoolean("Ok", r.ok());
            reqs.add(e);
        }
        t.put("CenterReqs", reqs);
        boolean busy = false;
        for (Project p : s.projects) busy |= Project.KIND_CENTER.equals(p.kind);
        t.putBoolean("CenterBusy", busy);
        t.putBoolean("CenterReady", CityEconomy.centerReady(server, s));
        // onda 5: o degrau — nome, o próximo, o que ele libera, custo e o andamento da reforma da cidade
        if (s.own()) {
            CompoundTag rk = new CompoundTag();
            rk.putString("Name", SettlementView.json(CityTier.name(s.level), reg));
            ListTag chain = new ListTag();
            for (int l = 1; l <= CityTier.MAX; l++) chain.add(net.minecraft.nbt.StringTag.valueOf(SettlementView.json(CityTier.name(l), reg)));
            rk.put("Chain", chain);
            int shown = Math.min(CityTier.MAX, s.level + 1);
            if (s.level < CityTier.MAX) {
                rk.putString("Next", SettlementView.json(CityTier.name(s.level + 1), reg));
                rk.putInt("Cost", CityTier.cost(s.level + 1));
            }
            ListTag gains = new ListTag();
            for (Component c : CityTier.gains(s, shown)) gains.add(net.minecraft.nbt.StringTag.valueOf(SettlementView.json(c, reg)));
            rk.put("Gains", gains);
            rk.putInt("Renew", CityRenewal.percent(s));
            // onda 5 (city_styles): Reforma de estilo
            rk.putInt("ReformCost", CityReform.cost(s));
            rk.putInt("ReformPending", CityReform.pending(s));
            t.put("Rank", rk);
        }

        CityEconomy.refreshRequests(server, s, false);
        ListTag rq = new ListTag();
        for (CompoundTag r : s.requests) {
            CompoundTag e = new CompoundTag();
            String item = r.getString("Item");
            e.putString("Name", SettlementView.json(CityEconomy.itemName(item), reg));
            e.putString("Icon", BuiltInRegistries.ITEM.getKey(CityEconomy.icon(item).getItem()).toString());
            e.putInt("Need", r.getInt("Need"));
            e.putInt("Got", r.getInt("Got"));
            rq.add(e);
        }
        t.put("Requests", rq);

        ServerLevel sl = SettlementService.level(server, s);
        if (sl != null) {
            t.putInt("Raid", CityRaids.status(sl, s.id));
            String side = CityRaids.side(s.id);
            if (side != null) t.putString("RaidSide", SettlementView.json(SettlementsLang.tr("raid.side." + side), reg));
        }
        t.putBoolean("RaidDue", s.raidDue);

        if (s.own()) plots(t, server, sl, s, reg);
        if (s.own() && planner) catalog(t, s, reg);
        if (owner) council(t, server, s);
        // v1.0 (city_life): felicidade, pedidos, festival, caravanas, defesa
        CityLifeView.fill(t, sp, s, planner, reg);
        // onda 6 (city_pace): ritmo das obras e o Diário da cidade
        if (s.own()) CityPace.view(t, server, s, reg);
        // onda 6 (city_monuments): monumentos ativos, bônus somados e a bênção de quem olha
        if (s.own()) CityMonuments.view(t, sp, s, reg);
        // onda 6 (city_trades): comerciantes de ofício — quem a cidade tem, quem falta e a taxa das vendas
        if (s.own()) CityTrades.view(t, server, s, reg);
    }

    private static void plots(CompoundTag t, MinecraftServer server, ServerLevel sl, Settlement s, HolderLookup.Provider reg) {
        ListTag plots = new ListTag();
        // a planta mostra a cidade + o próximo anel (bloqueado: "expanda para usar")
        int view = Math.min(CityLayout.MAX_RADIUS, s.radius + 1);
        t.putInt("Radius", s.radius);
        t.putInt("View", view);
        // v1.0 (city_plan): onde fica o coração (as prévias no mundo precisam) e os prédios em posição livre
        t.putInt("HeartX", s.heart.getX());
        t.putInt("HeartY", s.heart.getY());
        t.putInt("HeartZ", s.heart.getZ());
        t.putString("Dim", s.dim.location().toString());
        java.util.List<Integer> freeCells = CityPlots.freeOccupied(s);
        ListTag free = new ListTag();
        for (int c : freeCells) {
            CompoundTag e = new CompoundTag();
            e.putInt("Cell", c);
            e.putBoolean("Free", true);
            e.putInt("Dx", CityLayout.freeDx(c));
            e.putInt("Dz", CityLayout.freeDz(c));
            e.putInt("Rot", CityLayout.freeRot(c).ordinal());
            // posição na planta: o terreno da grade mais perto + a fração de terreno até o centro do lote
            e.putFloat("FI", mapIndex(CityLayout.freeDx(c)));
            e.putFloat("FJ", mapIndex(CityLayout.freeDz(c)));
            Blueprint bp = s.blueprintAt(c);
            if (bp != null) {
                e.putString("Key", bp.key());
                e.putString("Lang", bp.langKey());
                e.putString("Style", s.styleOf(c).id);
                if (s.styleOf(c) != s.style && Blueprints.get(s.style, s.buildings.getOrDefault(c, "")) != null) e.putBoolean("Restyle", true);
                if (CityLooks.old(s, c)) e.putBoolean("OldLook", true);
                e.putInt("Level", s.levelOf(c));
                // onda 6 (city_monuments): monumentos param no nível 3
                if (CityMonuments.maxLevel(bp.key()) < Settlement.MAX_BUILDING_LEVEL) e.putInt("MaxLv", CityMonuments.maxLevel(bp.key()));
                if (s.damaged.contains(c)) e.putBoolean("Damaged", true);
            }
            Project p = s.projectAt(c);
            if (p != null) {
                e.putString("Busy", SettlementView.json(CityPlanning.projectName(s, p), reg));
                CityPace.ensure(server, s, p);
                e.putInt("BusyPct", Math.round(CityPace.fraction(p) * 100));
                e.putInt("BusyDay", CityPace.dayNumber(p));
                e.putInt("BusyDays", CityPace.totalDays(p));
            }
            if (bp != null && s.levelOf(c) < CityMonuments.maxLevel(bp.key())) e.putString("UpDays", CityPace.upgradeDaysJson(bp.key(), s.levelOf(c) + 1, reg));
            Integer base = s.plotBase.get(c);
            if (base != null) e.putInt("Height", base - (s.heart.getY() - 1));
            free.add(e);
        }
        t.put("FreePlots", free);
        for (int i = -view; i <= view; i++) {
            for (int j = -view; j <= view; j++) {
                // onda 9 (isles): numa cidade de ilhas o terreno (i, j) tem o id de ilha
                int c = CityLayout.cellAt(s, i, j);
                CompoundTag e = new CompoundTag();
                e.putInt("Cell", c);
                e.putInt("I", i);
                e.putInt("J", j);
                if (c == CityLayout.CENTER_CELL) {
                    e.putBoolean("Center", true);
                    plots.add(e);
                    continue;
                }
                int ring = CityLayout.ring(c);
                // v1.0 (cidade grande): o anel de fora não vai no pacote — a tela desenha vazio o que faltar
                if (ring > s.radius) continue;
                if (ring <= CityLayout.GRID) e.putInt("Unlock", CityLayout.unlockLevel(c));
                Blueprint bp = s.blueprintAt(c);
                if (bp != null) {
                    // v1.0 (cidade grande): pacote enxuto — a tela monta o nome (chave de tradução) e os efeitos (fxLine)
                    e.putString("Key", bp.key());
                    e.putString("Lang", bp.langKey());
                    e.putString("Style", s.styleOf(c).id);
                    if (s.styleOf(c) != s.style && Blueprints.get(s.style, bp.key()) != null) e.putBoolean("Restyle", true);
                    if (CityLooks.old(s, c)) e.putBoolean("OldLook", true);
                    e.putInt("Level", s.levelOf(c));
                    // onda 6 (city_monuments): monumentos param no nível 3
                    if (CityMonuments.maxLevel(bp.key()) < Settlement.MAX_BUILDING_LEVEL) e.putInt("MaxLv", CityMonuments.maxLevel(bp.key()));
                    if (s.damaged.contains(c)) e.putBoolean("Damaged", true);
                }
                Project p = s.projectAt(c);
                if (p != null) {
                    e.putString("Busy", SettlementView.json(CityPlanning.projectName(s, p), reg));
                    CityPace.ensure(server, s, p);
                    e.putInt("BusyPct", Math.round(CityPace.fraction(p) * 100));
                    e.putInt("BusyDay", CityPace.dayNumber(p));
                    e.putInt("BusyDays", CityPace.totalDays(p));
                }
                // onda 6: quanto leva a próxima melhoria (a tela mostra antes de enfileirar)
                if (bp != null && s.levelOf(c) < CityMonuments.maxLevel(bp.key())) e.putString("UpDays", CityPace.upgradeDaysJson(bp.key(), s.levelOf(c) + 1, reg));
                Integer base = s.plotBase.get(c);
                if (base != null) e.putInt("Height", base - (s.heart.getY() - 1));
                if (bp == null && p == null && CityPlots.gridBlocked(freeCells, c, Integer.MIN_VALUE)) e.putBoolean("Blocked", true);
                // o aviso de terreno (íngreme/água) lê o mundo: só nos anéis de perto, para a tela de cidade enorme não pesar
                if (bp == null && ring <= 5 && CityLayout.unlocked(s, c)) {
                    e.putInt("Terrain", sl == null ? -1 : CityPlanning.terrainStatus(sl, s, c));
                }
                plots.add(e);
            }
        }
        t.put("Plots", plots);
        // muralhas: uma por vão (a cada 2 anéis); 0 = vazia, 1 = em obra, 2 = pronta
        ListTag walls = new ListTag();
        for (int k = 1; k <= CityLayout.wallSlots(s); k++) {
            CompoundTag w = new CompoundTag();
            w.putInt("K", k);
            w.putInt("State", s.walls.contains(k) ? 2 : SettlementService.wallInProgress(s, k) ? 1 : 0);
            Component pr = SettlementService.wallProblem(s, k);
            w.putString("Problem", pr == null ? "" : SettlementView.json(pr, reg));
            walls.add(w);
        }
        t.put("Walls", walls);
        // compatibilidade com o botão antigo
        t.putBoolean("WallBuildable", SettlementService.nextWall(s) > 0);
        Component wp = SettlementService.nextWall(s) > 0 ? SettlementService.wallProblem(s, SettlementService.nextWall(s)) : null;
        t.putString("WallProblem", wp == null ? "" : SettlementView.json(wp, reg));
        // expansão
        Component ex = CityPlanning.expandProblem(server, s);
        t.putString("ExpandProblem", ex == null ? "" : SettlementView.json(ex, reg));
        t.putBoolean("ExpandMax", s.radius >= CityLayout.MAX_RADIUS);
        t.putInt("ExpandLevel", CityPlanning.expandLevel(s.radius + 1));
        t.putInt("ExpandBuilt", CityPlanning.builtPlots(s));
        t.putInt("ExpandNeed", CityPlanning.expandBuildings(s));
    }

    /** Posição de um deslocamento (em blocos) na planta da tela, em "terrenos" (os vãos das muralhas somem). */
    private static float mapIndex(int d) {
        int i = CityLayout.nearestIndex(d);
        return i + Math.max(-0.5F, Math.min(0.5F, (d - CityLayout.axis(i)) / (float) CityLayout.CELL));
    }

    private static void catalog(CompoundTag t, Settlement s, HolderLookup.Provider reg) {
        ListTag out = new ListTag();
        Set<String> seen = new LinkedHashSet<>();
        // ordem: o catálogo do estilo da cidade primeiro, depois o que só existe em outros estilos (maravilhas, castelo)
        java.util.List<SettlementStyle> order = new java.util.ArrayList<>(java.util.List.of(s.style));
        for (SettlementStyle o : SettlementStyle.values()) if (o != s.style) order.add(o);
        for (SettlementStyle style : order) {
            for (Blueprint bp : Blueprints.of(style)) {
                if (!bp.usesCell() || !seen.add(bp.key())) continue;
                CompoundTag e = new CompoundTag();
                e.putString("Key", bp.key());
                e.putString("Name", SettlementView.json(bp.displayName(), reg));
                e.putString("Desc", SettlementView.json(bp.description(), reg));
                e.putInt("MinLevel", bp.minLevel());
                e.putInt("Max", CityGrowth.maxCount(s, bp));
                e.putInt("Count", s.count(bp.key()) + s.inProgress(bp.key()));
                e.putBoolean("Wonder", bp.isWonder());
                e.putString("Fx", SettlementView.json(fxLine(bp.key(), 1), reg));
                e.putString("Days", CityPace.buildDaysJson(bp.key(), reg));
                ListTag styles = new ListTag();
                for (SettlementStyle o : SettlementStyle.values()) {
                    if (Blueprints.get(o, bp.key()) != null) styles.add(StringTag.valueOf(o.id));
                }
                e.put("Styles", styles);
                out.add(e);
            }
        }
        t.put("Catalog", out);
    }

    private static void council(CompoundTag t, MinecraftServer server, Settlement s) {
        ListTag out = new ListTag();
        Set<UUID> all = new LinkedHashSet<>(s.council);
        all.addAll(Allegiance.citizens(server, s.id));
        for (UUID u : all) {
            if (u.equals(s.owner)) continue;
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", u);
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            String name = p != null ? p.getGameProfile().getName()
                    : server.getProfileCache() == null ? "?" : server.getProfileCache().get(u).map(gp -> gp.getName()).orElse("?");
            e.putString("Name", name);
            e.putBoolean("Council", s.council.contains(u));
            e.putBoolean("Online", p != null);
            out.add(e);
            if (out.size() >= 24) break;
        }
        t.put("Council", out);
    }

    /** Resumo do que o prédio dá por dia num nível ("+5 comida · +4 moradia · +2 apoio"). */
    public static Component fxLine(String key, int level) {
        CityEconomy.NeedFx f = CityEconomy.fx(key);
        float m = CityEconomy.levelMult(level);
        MutableComponent out = Component.empty();
        int[] vals = {Math.round(f.housing() * m), Math.round(f.food() * m), Math.round(f.fuel() * m), -Math.round(f.fuelUse() * m),
                Math.round(f.storage() * m), Math.round(f.security() * m), Math.round(f.approval() * m), Math.round(f.income() * m)};
        String[] keys = {"housing", "food", "fuel", "fuel_use", "storage", "security", "approval", "income"};
        boolean any = false;
        for (int i = 0; i < vals.length; i++) {
            if (vals[i] == 0) continue;
            if (any) out.append(Component.literal(" · "));
            out.append(SettlementsLang.tr("fx." + keys[i], (vals[i] > 0 ? "+" : "") + vals[i]));
            any = true;
        }
        if (key.startsWith("temple_")) {
            if (any) out.append(Component.literal(" · "));
            out.append(SettlementsLang.tr("fx.temple." + (key.equals("temple_arcane") ? "arcane" : "element"), 3 * level, 2 * level));
            any = true;
        }
        if (key.equals("arcane_ward") || key.equals("arcane_beacon") || key.equals("mana_spire")) {
            if (any) out.append(Component.literal(" · "));
            int pct = switch (key) {
                // mesmos números do módulo city_arcana (ArcaneWard.reduction, ArcanaTowers.SPIRE_PCT / beaconRadius)
                case "arcane_ward" -> 10 + 8 * level;
                case "mana_spire" -> 4 * level;
                default -> 16 + 8 * level;
            };
            out.append(SettlementsLang.tr("fx." + key, pct));
            any = true;
        }
        if (key.equals("null_obelisk")) {
            if (any) out.append(Component.literal(" · "));
            out.append(SettlementsLang.tr("fx.null_obelisk"));
            any = true;
        }
        if (key.equals("watchtower") || key.equals("trap_field") || key.equals("barracks")) {
            // v1.0 (defesa de cerco): o que o prédio faz durante um ataque
            if (any) out.append(Component.literal(" · "));
            out.append(key.equals("watchtower") ? SettlementsLang.tr("fx.watchtower", 20 + 4 * level, 3 + level)
                    : key.equals("trap_field") ? SettlementsLang.tr("fx.trap_field", (3 + level) / 2F)
                    : SettlementsLang.tr("fx.barracks"));
            any = true;
        }
        if (key.equals("hospital")) {
            if (any) out.append(Component.literal(" · "));
            out.append(SettlementsLang.tr("fx.hospital"));
            any = true;
        }
        // onda 6 (city_trades): o comerciante de ofício que o prédio traz e quantas ofertas o nível abre
        Component trader = CityTrades.fxNote(key, level);
        if (trader != null) {
            if (any) out.append(Component.literal(" · "));
            out.append(trader);
            any = true;
        }
        // onda 6 (city_monuments): o bônus com nome do monumento (vale com ele ativo)
        Component mon = CityMonuments.fx(key, level);
        if (mon != null) {
            if (any) out.append(Component.literal(" · "));
            out.append(mon);
            any = true;
        }
        if (!any) out.append(SettlementsLang.tr("fx.none"));
        return out;
    }

    private CityViewExtra() {}
}
