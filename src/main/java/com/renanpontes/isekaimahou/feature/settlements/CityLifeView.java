package com.renanpontes.isekaimahou.feature.settlements;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Parte "cidade viva" (v1.0, city_life) do estado da tela: felicidade com os motivos e a dica, avisos do conselheiro,
 * pedidos dos moradores, festival, caravanas, armazém de mercadorias, defesa de cerco e os requisitos de expansão.
 * NBT tolerante, como o resto ({@link CityViewExtra}).
 */
final class CityLifeView {

    static void fill(CompoundTag t, ServerPlayer sp, Settlement s, boolean planner, HolderLookup.Provider reg) {
        MinecraftServer server = sp.server;
        CityEconomy.Stats st = CityEconomy.stats(server, s);
        t.putBoolean("InCity", CityActions.inCity(sp, s));

        // ---- felicidade
        List<CityHappiness.Factor> factors = CityHappiness.forecast(server, s, st);
        CompoundTag h = new CompoundTag();
        h.putInt("Value", s.affinity);
        h.putString("Mood", CityHappiness.mood(s.affinity));
        h.putInt("Delta", CityHappiness.total(factors));
        ListTag fl = new ListTag();
        for (CityHappiness.Factor f : factors) {
            CompoundTag e = new CompoundTag();
            e.putString("Key", f.key());
            e.putInt("D", f.delta());
            fl.add(e);
        }
        h.put("Factors", fl);
        h.putString("Hint", SettlementView.json(CityHappiness.hint(server, s, st, factors), reg));
        h.putInt("ProdPct", Math.round(CityHappiness.prodMult(s.affinity) * 100));
        h.putInt("SpeedPct", CityHappiness.speedBonus(s.affinity));
        h.putInt("Growth", CityHappiness.growthMult(s.affinity));
        h.putInt("RevoltIn", s.lowApprovalDays > 0 ? CityEconomy.REVOLT_DAYS - s.lowApprovalDays : 0);
        t.put("Happy", h);

        // ---- conselheiro
        ListTag tips = new ListTag();
        for (Component c : CityGoals.tips(server, s, st)) tips.add(StringTag.valueOf(SettlementView.json(c, reg)));
        t.put("Tips", tips);

        // ---- festival
        CompoundTag fe = new CompoundTag();
        fe.putInt("Day", Lordship.festivalDayNumber(server, s));
        fe.putInt("Wait", Lordship.festivalWait(server, s));
        fe.putInt("Days", Lordship.FESTIVAL_DAYS);
        t.put("Fest", fe);

        if (!s.own()) return;

        // ---- pedidos dos moradores
        CityGoals.refill(server, s);
        ListTag goals = new ListTag();
        for (CompoundTag g : s.goals) {
            CompoundTag e = new CompoundTag();
            int[] pr = CityGoals.progress(server, s, g);
            e.putString("Text", SettlementView.json(CityGoals.text(s, g), reg));
            e.putInt("Cur", pr[0]);
            e.putInt("Need", pr[1]);
            goals.add(e);
        }
        t.put("Goals", goals);
        t.putInt("GoalCoins", CityGoals.reward(s));

        // ---- expansão: gente + felicidade + prédios
        ListTag ex = new ListTag();
        for (CityEconomy.Req r : CityGrowth.expandReqs(server, s)) {
            CompoundTag e = new CompoundTag();
            e.putString("Text", SettlementView.json(r.text(), reg));
            e.putBoolean("Ok", r.ok());
            ex.add(e);
        }
        t.put("ExpandReqs", ex);

        caravans(t, server, s, planner, reg);
        defense(t, server, s);

        // ---- armazém
        ListTag stash = new ListTag();
        for (CompoundTag g : s.stash) {
            CompoundTag e = new CompoundTag();
            e.putString("Item", g.getString("Item"));
            e.putInt("Count", g.getInt("Count"));
            stash.add(e);
        }
        t.put("Stash", stash);
    }

    private static void caravans(CompoundTag t, MinecraftServer server, Settlement s, boolean planner, HolderLookup.Provider reg) {
        CompoundTag c = new CompoundTag();
        long now = server.overworld().getGameTime();
        c.putInt("Slots", CityCaravans.slots(s));
        c.putBoolean("Market", s.has("market"));
        c.putInt("Sent", s.caravansSent);
        c.putInt("Profit", s.caravanProfit);
        ListTag active = new ListTag();
        for (CompoundTag cv : s.caravans) {
            CompoundTag e = new CompoundTag();
            e.putString("Name", cv.getString("TName"));
            e.putString("Kind", cv.getString("Kind"));
            e.putInt("Cargo", cv.getInt("Cargo"));
            e.putInt("Pct", CityCaravans.percent(cv, now));
            e.putInt("Phase", CityCaravans.phase(cv, now));
            e.putInt("Secs", CityCaravans.secondsLeft(cv, now));
            active.add(e);
        }
        c.put("Active", active);
        if (!s.caravanLast.isEmpty()) {
            c.putString("LastName", s.caravanLast.getString("TName"));
            c.putString("Last", SettlementView.json(CityCaravans.summary(s.caravanLast), reg));
        }
        if (planner) {
            boolean feast = Lordship.festivalActive(server, s);
            CityEconomy.Stats st = CityEconomy.stats(server, s);
            // o que dá para mandar agora (sem deixar a cidade com menos de 2 dias de estoque)
            c.putInt("FoodSpare", Math.max(0, s.food - 2 * st.foodUse()));
            c.putInt("FuelSpare", Math.max(0, s.fuel - 2 * st.fuelUse()));
            c.putIntArray("Units", CityCaravans.CARGO_UNITS);
            c.putIntArray("Coins", CityCaravans.CARGO_COINS);
            c.putBoolean("Feast", feast);
            ListTag dest = new ListTag();
            for (CityCaravans.Dest d : CityCaravans.destinations(server, s)) {
                CompoundTag e = new CompoundTag();
                e.putString("Id", d.place().id());
                e.putString("Name", SettlementView.json(d.place().name(), reg));
                e.putInt("Color", d.place().color());
                e.putInt("Dist", d.dist());
                e.putInt("Secs", CityCaravans.totalTicks(s, d.dist()) / 20);
                e.putInt("Food", CityCaravans.quote(server, s, d.place(), CityCaravans.SELL_FOOD));
                e.putInt("Fuel", CityCaravans.quote(server, s, d.place(), CityCaravans.SELL_FUEL));
                e.putInt("FoodBuy", CityCaravans.quote(server, s, d.place(), CityCaravans.BUY_FOOD));
                e.putInt("FuelBuy", CityCaravans.quote(server, s, d.place(), CityCaravans.BUY_FUEL));
                e.putInt("Margin", CityCaravans.quote(server, s, d.place(), CityCaravans.GOODS));
                e.putInt("Glut", CityCaravans.sentToday(server, s, d.place().id()));
                e.putInt("Bonus", CityCaravans.distanceBonus(d.dist()) + (feast ? 20 : 0));
                e.putInt("Risk", CityCaravans.risk(server, s, d.dist()));
                e.putBoolean("War", d.war());
                e.putBoolean("Player", d.playerCity());
                dest.add(e);
            }
            c.put("Dest", dest);
        }
        t.put("Caravans", c);
    }

    private static void defense(CompoundTag t, MinecraftServer server, Settlement s) {
        CityDefense.Summary d = CityDefense.summary(s);
        CompoundTag e = new CompoundTag();
        e.putInt("Towers", d.towers());
        e.putInt("WallTowers", d.wallTowers());
        e.putInt("Mage", d.mageTowers());
        e.putInt("Traps", d.traps());
        e.putInt("Barracks", d.barracksLevel());
        e.putInt("Range", d.range());
        e.putInt("Damage", Math.round(d.damage()));
        e.putInt("Walls", s.walls.size());
        e.putInt("Forecast", CityDefense.forecastDays(server, s));
        e.putBoolean("Scouts", CityDefense.hasScouts(s));
        e.putBoolean("Raids", s.level >= 2 && SettlementData.get(server).raidsEnabled);
        ServerLevel sl = SettlementService.level(server, s);
        e.putInt("Raid", sl == null ? -1 : CityRaids.status(sl, s.id));
        t.put("Defense", e);
    }

    private CityLifeView() {}
}
