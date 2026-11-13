package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Qualidade de vida da cidade fundada (v1.0, city_life):
 * <ul>
 *   <li><b>Pedidos dos moradores</b> — duas mini-metas por vez ("construam uma Taverna", "cheguem a 20 habitantes",
 *       "mandem uma caravana"...). Cumpriu = moedas no cofre, felicidade e um pouco de XP para o governante, e chega
 *       outro pedido. Não têm prazo nem castigo.</li>
 *   <li><b>Conselheiro</b> — {@link #tips}: até 3 avisos curtos do que a cidade mais precisa agora (na tela, nada de
 *       mensagens pulando).</li>
 * </ul>
 */
public final class CityGoals {
    private static final int ACTIVE = 2;
    /** Prédios que os moradores pedem (quando ainda não há nenhum e o nível do centro já libera). */
    private static final String[] WISH = {"well", "garden", "granary", "tavern", "market", "temple", "hospital", "statue",
            "watchtower", "library", "smithy", "sawmill", "arena", "stable"};

    // =====================================================================================
    //  Metas
    // =====================================================================================
    /** Valor atual de uma meta ({@code Id} = tipo ou "build:<prédio>"). */
    private static int value(MinecraftServer server, Settlement s, String id) {
        if (id.startsWith("build:")) return s.count(id.substring(6));
        return switch (id) {
            case "people" -> Math.max(0, s.people);
            case "happy" -> s.affinity;
            case "food" -> {
                CityEconomy.Stats st = CityEconomy.stats(server, s);
                yield Math.min(9, CityEconomy.daysLeft(s.food, st.foodProd(), st.foodUse()));
            }
            case "festival" -> s.festivals;
            case "caravan" -> s.caravansSent;
            case "raid" -> s.raidsWon;
            case "upgrade" -> {
                int n = 0;
                for (int c : s.buildings.keySet()) if (c != CityLayout.CENTER_CELL && s.levelOf(c) >= 2) n++;
                yield n;
            }
            case "buildings" -> CityPlanning.builtPlots(s);
            case "wall" -> s.walls.size();
            case "expand" -> 2 * s.radius + 1;
            case "builders" -> s.population(Settlement.Role.BUILDER);
            default -> 0;
        };
    }

    /** Cria uma meta nova do tipo (null = não faz sentido agora). */
    @Nullable
    private static CompoundTag make(MinecraftServer server, Settlement s, String id) {
        int cur = value(server, s, id);
        int need;
        if (id.startsWith("build:")) {
            Blueprint bp = Blueprints.get(s.style, id.substring(6));
            if (bp == null || cur > 0 || bp.minLevel() > s.level) return null;
            need = 1;
        } else {
            switch (id) {
                case "people" -> need = (cur / 6 + 1) * 6 + (cur % 6 >= 4 ? 6 : 0);
                case "happy" -> {
                    if (cur >= CityHappiness.HAPPY) return null;
                    need = cur < CityHappiness.UNHAPPY ? 50 : CityHappiness.HAPPY;
                }
                case "food" -> {
                    if (cur >= 5) return null;
                    need = 5;
                }
                case "festival" -> need = cur + 1;
                case "caravan" -> {
                    if (!s.has("market")) return null;
                    need = cur + 1;
                }
                case "raid" -> {
                    if (s.level < 2 || !SettlementData.get(server).raidsEnabled) return null;
                    need = cur + 1;
                }
                case "upgrade" -> {
                    if (s.level < 2) return null;
                    need = cur + 1;
                }
                case "buildings" -> need = cur + 2;
                case "wall" -> {
                    if (s.level < 2 || SettlementService.nextWall(s) < 0) return null;
                    need = cur + 1;
                }
                case "expand" -> {
                    if (s.level < 3 || s.radius >= CityLayout.MAX_RADIUS) return null;
                    need = cur + 2;
                }
                case "builders" -> {
                    if (cur >= 4 || s.cap(Settlement.Role.BUILDER) < 4) return null;
                    need = 4;
                }
                default -> {
                    return null;
                }
            }
        }
        CompoundTag t = new CompoundTag();
        t.putString("Id", id);
        t.putInt("Need", need);
        return t;
    }

    private static boolean active(Settlement s, String id) {
        for (CompoundTag g : s.goals) if (g.getString("Id").equals(id)) return true;
        return false;
    }

    /** Completa a lista de pedidos até {@link #ACTIVE} (sorteio estável pelo dia + cidade). */
    public static void refill(MinecraftServer server, Settlement s) {
        if (!s.own() || !s.has(Blueprint.PLAZA) || s.goals.size() >= ACTIVE) return;
        List<String> pool = new ArrayList<>(List.of("people", "happy", "food", "festival", "caravan", "raid", "upgrade", "buildings",
                "wall", "expand", "builders"));
        for (String w : WISH) pool.add("build:" + w);
        RandomSource r = RandomSource.create(s.id.hashCode() * 31L + SettlementService.day(server) * 7L + s.goalsCompleted);
        for (int pass = 0; pass < 2 && s.goals.size() < ACTIVE; pass++) {
            List<String> left = new ArrayList<>(pool);
            while (!left.isEmpty() && s.goals.size() < ACTIVE) {
                String id = left.remove(r.nextInt(left.size()));
                if (active(s, id) || pass == 0 && s.goalsDone.contains(id)) continue;
                CompoundTag g = make(server, s, id);
                if (g != null) s.goals.add(g);
            }
            // acabaram os pedidos inéditos: os repetíveis voltam a valer
            if (s.goals.size() < ACTIVE) s.goalsDone.clear();
        }
    }

    /** Recompensa em moedas de um pedido (cresce com o porte da cidade). */
    public static int reward(Settlement s) {
        return 10 + 6 * s.level;
    }

    /** A cada 10 s: confere os pedidos das cidades fundadas e paga os cumpridos. */
    public static void tick(MinecraftServer server) {
        for (Settlement s : SettlementData.get(server).all()) {
            if (!s.own() || s.owner == null) continue;
            boolean changed = false;
            for (Iterator<CompoundTag> it = s.goals.iterator(); it.hasNext();) {
                CompoundTag g = it.next();
                String id = g.getString("Id");
                if (value(server, s, id) < g.getInt("Need")) continue;
                it.remove();
                s.goalsDone.add(id);
                s.goalsCompleted++;
                int coins = reward(s);
                s.treasury = Math.min(Settlement.MAX_TREASURY, s.treasury + coins);
                s.affinity = Math.min(Settlement.MAX_AFFINITY, s.affinity + 3);
                ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
                if (owner != null) MagicApi.giveXp(owner, 10 * s.level, "city_goal");
                for (ServerPlayer p : CityEconomy.governors(server, s)) {
                    Rewards.toast(p, SettlementsLang.tr("goal.done.title"), text(s, g), new ItemStack(Items.EMERALD), 0x7DDA6B);
                    p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.5F);
                }
                changed = true;
            }
            int before = s.goals.size();
            refill(server, s);
            if (changed || s.goals.size() != before) SettlementService.refreshViewers(server, s);
        }
    }

    /** Texto do pedido ("Os moradores querem uma Taverna", "Cheguem a 24 habitantes"). */
    public static Component text(Settlement s, CompoundTag g) {
        String id = g.getString("Id");
        if (id.startsWith("build:")) {
            Blueprint bp = Blueprints.get(s.style, id.substring(6));
            return SettlementsLang.tr("goal.build", bp == null ? Component.literal(id.substring(6)) : bp.displayName());
        }
        return SettlementsLang.tr("goal." + id, g.getInt("Need"));
    }

    /** Progresso para a barra: {atual, pedido}. */
    public static int[] progress(MinecraftServer server, Settlement s, CompoundTag g) {
        return new int[] {Math.min(value(server, s, g.getString("Id")), g.getInt("Need")), g.getInt("Need")};
    }

    /** Comando de teste: cumpre os pedidos atuais na próxima conferência. */
    public static void forceComplete(Settlement s) {
        for (CompoundTag g : s.goals) g.putInt("Need", 0);
    }

    // =====================================================================================
    //  Conselheiro: o que a cidade precisa agora
    // =====================================================================================
    /** Até 3 avisos, do mais urgente para o menos. */
    public static List<Component> tips(MinecraftServer server, Settlement s, CityEconomy.Stats st) {
        List<Component> out = new ArrayList<>();
        int foodDays = CityEconomy.daysLeft(s.food, st.foodProd(), st.foodUse());
        int fuelDays = CityEconomy.daysLeft(s.fuel, st.fuelProd(), st.fuelUse());
        if (CityRaids.active(s.id)) out.add(SettlementsLang.tr("tip.raid_now"));
        else if (s.raidDue) out.add(SettlementsLang.tr("tip.raid_due"));
        if (foodDays < 2) out.add(SettlementsLang.tr("tip.food", foodDays));
        if (fuelDays < 2) out.add(SettlementsLang.tr("tip.fuel", fuelDays));
        if (s.affinity < CityHappiness.MISERABLE + 5) out.add(SettlementsLang.tr("tip.unhappy"));
        if (!s.damaged.isEmpty()) out.add(SettlementsLang.tr("tip.repair", s.damaged.size()));
        if (s.own()) {
            if (s.people >= st.housing()) out.add(SettlementsLang.tr("tip.housing"));
            if (st.securityNeed() > 0 && st.securityRatio() < 1F) out.add(SettlementsLang.tr("tip.security"));
            if (CityEconomy.centerReady(server, s) && !centerBusy(s)) out.add(SettlementsLang.tr("tip.center", s.level + 1));
            if (s.radius < CityLayout.MAX_RADIUS && CityPlanning.expandProblem(server, s) == null) out.add(SettlementsLang.tr("tip.expand"));
            if (s.projects.isEmpty() && SettlementService.freeCell(s) >= 0) out.add(SettlementsLang.tr("tip.idle"));
            if (s.level >= 2 && !s.has("market")) out.add(SettlementsLang.tr("tip.market"));
            else if (CityCaravans.slots(s) > s.caravans.size()) out.add(SettlementsLang.tr("tip.caravan"));
            if (CityStash.total(s) >= 32) out.add(SettlementsLang.tr("tip.stash"));
        }
        if (s.treasury >= 200) out.add(SettlementsLang.tr("tip.treasury", s.treasury));
        return out.size() > 3 ? new ArrayList<>(out.subList(0, 3)) : out;
    }

    private static boolean centerBusy(Settlement s) {
        for (var p : s.projects) if (com.renanpontes.isekaimahou.feature.settlements.build.Project.KIND_CENTER.equals(p.kind)) return true;
        return false;
    }

    private CityGoals() {}
}
