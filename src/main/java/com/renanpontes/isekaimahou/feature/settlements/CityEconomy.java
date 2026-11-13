package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Necessidades da cidade (v1.4, docs/V14_DESIGN.md "cidades"): construir é de graça, mas MANTER a cidade custa o que os
 * jogadores trazem.
 * <ul>
 *   <li><b>Comida</b>: cada habitante come 1 por dia (+1 por nível do centro); fazendas/hortas/porto produzem só uma
 *       parte; o resto vem de comida entregue no coração (vale a nutrição do item).</li>
 *   <li><b>Combustível/Lenha</b>: aquecer casas, forjas e luzes (Nórdico gasta +50%); serraria e mina produzem; toras,
 *       tábuas, carvão e varas de blaze entregues.</li>
 *   <li><b>Segurança</b>: guardas, quartel, torres, muralha, guilda... contra o tamanho da cidade.</li>
 *   <li><b>Apoio da População</b> (a "afinidade" antiga, 0–100): comida, combustível, segurança, prédios de lazer,
 *       impostos, festivais, doações, Pedidos do Povo e ataques vencidos/perdidos. Muito baixo = revolta (o governante
 *       cai, a cidade fica com tudo).</li>
 * </ul>
 * A conta roda uma vez por dia do Overworld ({@link Lordship#daily}); os números ficam simples e redondos.
 */
public final class CityEconomy {
    /**
     * O que um prédio dá por dia, no nível 1 (o nível multiplica: ×1, ×1,5, ×2, ×2,5, ×3).
     *
     * @param housing  vagas de moradia (habitantes)
     * @param food     comida produzida
     * @param fuel     combustível produzido
     * @param fuelUse  combustível gasto (forjas, quartel, taverna)
     * @param storage  armazenagem extra (comida; combustível guarda 1/3 disso)
     * @param security segurança
     * @param approval apoio do povo por dia
     * @param income   impostos extras (%)
     */
    public record NeedFx(int housing, int food, int fuel, int fuelUse, int storage, int security, int approval, int income) {
        static final NeedFx NONE = new NeedFx(0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static final Map<String, NeedFx> FX = new HashMap<>();

    static {
        fx("plaza", 4, 0, 0, 0, 0, 1, 1, 0);
        fx("house_small", 4, 0, 0, 0, 0, 0, 0, 0);
        fx("house_large", 8, 0, 0, 0, 0, 0, 0, 0);
        fx("builders_hall", 1, 0, 0, 0, 0, 0, 0, 0);
        fx("farm", 1, 5, 0, 0, 0, 0, 0, 0);
        fx("granary", 0, 1, 0, 0, 120, 0, 0, 0);
        fx("well", 0, 1, 0, 0, 0, 0, 1, 0);
        fx("garden", 0, 2, 0, 0, 0, 0, 1, 0);
        fx("port", 0, 4, 0, 0, 0, 0, 0, 3);
        fx("market", 0, 0, 0, 0, 0, 0, 1, 10);
        fx("barracks", 0, 0, 0, 1, 0, 8, 0, 0);
        fx("watchtower", 0, 0, 0, 0, 0, 3, 0, 0);
        fx(Blueprint.WALL, 0, 0, 0, 0, 0, 10, 0, 0);
        fx("tavern", 2, 0, 0, 1, 0, 0, 3, 0);
        fx("temple", 0, 0, 0, 0, 0, 0, 2, 0);
        fx(Blueprint.TOWN_HALL, 0, 0, 0, 0, 0, 2, 1, 10);
        fx("smithy", 0, 0, 0, 2, 0, 3, 0, 5);
        fx("stable", 0, 1, 0, 0, 0, 2, 0, 0);
        fx("hospital", 0, 0, 0, 0, 0, 0, 2, 0);
        fx("sawmill", 0, 0, 6, 0, 0, 0, 0, 0);
        fx("workshop", 0, 0, 0, 0, 0, 0, 0, 3);
        fx("mine", 0, 0, 3, 0, 0, 0, 0, 8);
        fx("statue", 0, 0, 0, 0, 0, 0, 2, 0);
        fx("arch", 0, 0, 0, 0, 0, 0, 1, 0);
        fx("library", 0, 0, 0, 0, 0, 0, 2, 2);
        fx("guild", 0, 0, 0, 0, 0, 3, 0, 5);
        fx("arena", 0, 0, 0, 0, 0, 1, 3, 0);
        for (String t : Blueprints.ELEMENT_TEMPLES) fx(t, 0, 0, 0, 0, 0, 0, 2, 0);
        fx("magic_tower", 0, 0, 0, 0, 0, 5, 1, 0);
        fx("castle", 0, 0, 0, 0, 0, 10, 1, 0);
        fx("obelisk", 0, 0, 0, 0, 0, 0, 2, 0);
        fx("pyramid", 0, 0, 0, 0, 0, 0, 3, 20);
        fx("sphinx", 0, 0, 0, 0, 0, 6, 0, 0);
        // v1.5 (city_arcana): prédios mágicos gastam combustível (luz e catalisadores) e dão segurança
        fx("arcane_ward", 0, 0, 0, 2, 0, 6, 1, 0);
        fx("null_obelisk", 0, 0, 0, 2, 0, 4, 0, 0);
        fx("mana_spire", 0, 0, 0, 1, 0, 0, 1, 2);
        fx("arcane_beacon", 0, 0, 0, 1, 0, 3, 0, 0);
        // v1.0 (city_life): campo de armadilhas — defesa de cerco (CityDefense)
        fx("trap_field", 0, 0, 0, 0, 0, 4, 0, 0);
        // onda 10: Oficina Mecânica — gasta um pouco de combustível (forja), dá um pouco de segurança e impostos
        fx("garage", 0, 0, 0, 1, 0, 1, 0, 3);
    }

    private static void fx(String key, int housing, int food, int fuel, int fuelUse, int storage, int security, int approval, int income) {
        FX.put(key, new NeedFx(housing, food, fuel, fuelUse, storage, security, approval, income));
    }

    public static NeedFx fx(String key) {
        return FX.getOrDefault(key, NeedFx.NONE);
    }

    /** Multiplicador do nível do prédio (×1 no nível 1 até ×3 no nível 5). */
    public static float levelMult(int level) {
        return 1F + 0.5F * (Math.max(1, Math.min(5, level)) - 1);
    }

    public static final String[] TIERS = {"village", "town", "city", "metropolis", "capital"};

    /** Nome do porte da cidade pelo nível do centro: Aldeia → Vila → Cidade → Metrópole → Capital. */
    public static MutableComponent tier(int level) {
        // onda 5: os degraus têm nome próprio (Acampamento, Vila, Burgo, Cidade, Capital) — veja CityTier
        return CityTier.name(level);
    }

    // =====================================================================================
    //  Números do dia
    // =====================================================================================
    /** Retrato das necessidades (calculado na hora; a conta diária e a tela usam o mesmo). */
    public record Stats(int housing, int foodProd, int foodUse, int foodCap, int fuelProd, int fuelUse, int fuelCap, int security,
                        int securityNeed, int approvalFx, int incomePct, boolean hospital, float prodMult) {
        public float securityRatio() {
            return securityNeed <= 0 ? 1F : security / (float) securityNeed;
        }
    }

    public static void ensureInit(Settlement s, @Nullable Places.Place place) {
        if (s.people >= 0) return;
        if (s.own()) {
            s.people = Math.max(4, s.population(Settlement.Role.RESIDENT) + 2);
        } else {
            s.people = place != null && "capital".equals(place.type()) ? 60 : 30;
            s.food = 120;
            s.fuel = 60;
        }
    }

    public static float prodMult(int approval) {
        return CityHappiness.prodMult(approval);
    }

    public static Stats stats(MinecraftServer server, Settlement s) {
        ServerLevel sl = SettlementService.level(server, s);
        Places.Place place = sl == null ? null : Places.get(sl, s.id);
        ensureInit(s, place);
        float mult = prodMult(s.affinity);
        int housing, food, fuel, fuelUse, storage, security, approval, income;
        boolean hospital = false;
        if (s.own()) {
            NeedFx base = fx(Blueprint.PLAZA);
            housing = s.has(Blueprint.PLAZA) ? base.housing() : 2;
            food = 0;
            fuel = 0;
            fuelUse = 0;
            storage = 0;
            security = s.has(Blueprint.PLAZA) ? base.security() : 0;
            approval = s.has(Blueprint.PLAZA) ? base.approval() : 0;
            income = 0;
            for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
                if (e.getKey() == com.renanpontes.isekaimahou.feature.settlements.build.CityLayout.CENTER_CELL) continue;
                NeedFx f = fx(e.getValue());
                float m = levelMult(s.levelOf(e.getKey())) * (s.damaged.contains(e.getKey()) ? 0.5F : 1F);
                housing += Math.round(f.housing() * m);
                food += Math.round(f.food() * m);
                fuel += Math.round(f.fuel() * m);
                fuelUse += Math.round(f.fuelUse() * m);
                storage += Math.round(f.storage() * m);
                security += Math.round(f.security() * m);
                approval += Math.round(f.approval() * m);
                income += Math.round(f.income() * m);
                if (e.getValue().equals("hospital") && !s.damaged.contains(e.getKey())) hospital = true;
            }
            security += fx(Blueprint.WALL).security() * s.walls.size();
            housing = Math.max(housing, 2);
        } else {
            boolean capital = place != null && "capital".equals(place.type());
            housing = capital ? 80 : 45;
            // as fazendas do reino cobrem só parte: o Senhor precisa abastecer
            food = Math.round(s.people * 0.6F);
            fuel = Math.max(2, Math.round(s.people / 4F * 0.5F));
            fuelUse = 0;
            storage = capital ? 300 : 180;
            security = (capital ? 30 : 18);
            approval = 2;
            income = 0;
            hospital = true;
        }
        security += 2 * s.population(Settlement.Role.GUARD) + s.population(Settlement.Role.MILITIA);
        // onda 6 (city_monuments): Colosso, Catedral... (segurança) e Jardim Suspenso, Árvore-Mãe (comida)
        security += CityMonuments.cityStat(s, CityMonuments.Stat.SECURITY);
        // onda 5: personalidade do estilo (Élfico colhe mais, Anão colhe menos; Sombrio é mais seguro)
        security = Math.round(security * (100 + s.style.securityPct()) / 100F);
        int foodProd = Math.round(food * mult * s.style.foodMult() * (100 + CityMonuments.cityStat(s, CityMonuments.Stat.FOOD)) / 100F);
        // onda 8 (water_city): traço "Porto" — a pesca abastece a Cidade das Águas (modesto; cresce com o degrau)
        foodProd += WaterCity.fishing(s);
        int fuelProd = Math.round(fuel * mult);
        int foodUse = s.people + s.level;
        int fuelNeed = (int) Math.ceil(s.people / 4.0) + fuelUse;
        // Nórdico e Anão gastam mais lenha/carvão; Élfico quase nada
        if (s.style.fuelMult() != 1F) fuelNeed = Math.max(1, Math.round(fuelNeed * s.style.fuelMult()));
        int foodCap = 60 + 20 * s.level + storage;
        int fuelCap = 40 + 10 * s.level + storage / 3;
        // aldeia (centro nível 1) é pacata: segurança só passa a contar a partir da Vila
        int secNeed = s.level <= 1 ? 0 : 2 + s.people / 3 + 2 * s.level;
        approval = Math.min(8, approval / 2 + (approval > 0 ? 1 : 0));
        return new Stats(housing, foodProd, foodUse, foodCap, fuelProd, fuelNeed, fuelCap, security, secNeed, approval, income,
                hospital, mult);
    }

    /** Dias que o estoque dura (99 = está sobrando). */
    public static int daysLeft(int stock, int prod, int use) {
        int net = use - prod;
        if (net <= 0) return 99;
        return stock / net;
    }

    // =====================================================================================
    //  Conta diária
    // =====================================================================================
    /** O que aconteceu na conta do dia (a {@link CityHappiness} transforma isto nos motivos da felicidade). */
    public record Day(boolean starving, boolean cold, int foodDays) {}

    /**
     * Roda as necessidades do dia (estoques e população). A felicidade do dia é somada pela {@link Lordship} a partir
     * dos motivos da {@link CityHappiness}.
     */
    public static Day daily(MinecraftServer server, Settlement s) {
        Stats st = stats(server, s);
        int foodBefore = s.food;
        s.food = Math.min(st.foodCap(), s.food + st.foodProd()) - st.foodUse();
        boolean starving = s.food < 0;
        if (starving) s.food = 0;
        s.fuel = Math.min(st.fuelCap(), s.fuel + st.fuelProd()) - st.fuelUse();
        boolean cold = s.fuel < 0;
        if (cold) s.fuel = 0;
        s.lastFoodProd = st.foodProd();
        s.lastFoodUse = st.foodUse();
        s.lastFuelProd = st.fuelProd();
        s.lastFuelUse = st.fuelUse();
        s.lastSecurity = st.security();
        s.lastSecurityNeed = st.securityNeed();

        int foodDays = daysLeft(s.food, st.foodProd(), st.foodUse());

        // população: cresce com comida, calor e apoio; vai embora com fome ou revolta
        int before = s.people;
        if (starving) {
            int loss = Math.max(1, s.people / 6);
            if (st.hospital()) loss = Math.max(1, loss / 2);
            s.people -= loss;
        } else if (s.affinity < 20) {
            s.people -= Math.max(1, s.people / 10);
        } else if (s.people > st.housing()) {
            s.people -= 1;
        } else if (s.people < st.housing() && !cold && s.affinity >= 35 && foodDays >= 1) {
            // v1.0: cidade feliz atrai o dobro de imigrantes
            int arrive = Math.max(1, (st.housing() - s.people + 2) / 4) * CityHappiness.growthMult(s.affinity);
            s.people = Math.min(st.housing(), s.people + arrive);
        }
        if (cold && s.people > 4 && !st.hospital()) s.people -= 1;
        s.people = Math.max(2, s.people);
        s.lastPeopleDelta = s.people - before;
        if (s.people < before) trimResidents(server, s);

        refreshRequests(server, s, false);
        report(server, s, st, starving, cold, foodDays, foodBefore);
        return new Day(starving, cold, foodDays);
    }

    /** Moradores (NPCs) a mais que a população: os excedentes vão embora. */
    private static void trimResidents(MinecraftServer server, Settlement s) {
        ServerLevel sl = SettlementService.level(server, s);
        if (sl == null) return;
        List<UUID> res = s.npcs.get(Settlement.Role.RESIDENT);
        while (res.size() > s.cap(Settlement.Role.RESIDENT) && !res.isEmpty()) {
            UUID u = res.remove(res.size() - 1);
            var e = sl.getEntity(u);
            if (e != null) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                e.discard();
            }
        }
    }

    private static void report(MinecraftServer server, Settlement s, Stats st, boolean starving, boolean cold, int foodDays, int foodBefore) {
        Component name = SettlementService.cityName(server, s);
        for (ServerPlayer p : governors(server, s)) {
            p.displayClientMessage(SettlementsLang.tr("needs.daily", name, s.people, s.food, foodDays >= 99 ? "∞" : Integer.toString(foodDays),
                    s.fuel, Math.round(st.securityRatio() * 100)).withColor(0xC9E8A0), false);
            // v1.0 (tela limpa): depois do resumo, só UM aviso — o mais urgente
            if (starving) {
                Rewards.toast(p, SettlementsLang.tr("needs.starving.title"), name, new ItemStack(Items.BREAD), 0xE8735E);
            } else if (foodDays < 2) {
                p.displayClientMessage(SettlementsLang.tr("needs.food_low", name, foodDays).withColor(0xE8A440), false);
            } else if (cold) {
                p.displayClientMessage(SettlementsLang.tr("needs.cold", name).withColor(0x8FC8FF), false);
            } else if (s.affinity < 25) {
                p.displayClientMessage(SettlementsLang.tr("needs.protest", name).withColor(0xE8735E), false);
            } else if (s.lastPeopleDelta < 0) {
                p.displayClientMessage(SettlementsLang.tr("needs.left", name, -s.lastPeopleDelta).withColor(0xE8735E), false);
            } else if (s.lastPeopleDelta > 0) {
                p.displayClientMessage(SettlementsLang.tr("needs.grew", name, s.lastPeopleDelta).withColor(0x7DDA6B), false);
            }
        }
    }

    /** Quem governa e está online: o Senhor e os conselheiros. */
    public static List<ServerPlayer> governors(MinecraftServer server, Settlement s) {
        List<ServerPlayer> out = new ArrayList<>();
        if (s.owner != null) {
            ServerPlayer o = server.getPlayerList().getPlayer(s.owner);
            if (o != null) out.add(o);
        }
        for (UUID u : s.council) {
            ServerPlayer c = server.getPlayerList().getPlayer(u);
            if (c != null && !out.contains(c)) out.add(c);
        }
        return out;
    }

    /** Membro da cidade: Senhor, conselheiro, cidadão ou do grupo do Senhor (pode entregar recursos). */
    public static boolean isMember(MinecraftServer server, Settlement s, ServerPlayer p) {
        if (s.canPlan(p.getUUID())) return true;
        if (s.id.equals(Allegiance.of(p))) return true;
        return s.owner != null && Party.members(p).contains(s.owner);
    }

    /** Dias seguidos de felicidade abaixo de 10 até a revolta (v1.0: 3, com aviso a cada dia). */
    public static final int REVOLT_DAYS = 3;

    /** Revolta: felicidade abaixo de 10 por {@link #REVOLT_DAYS} dias seguidos derruba o governante (a cidade fica com tudo). */
    public static void checkRevolt(MinecraftServer server, Settlement s) {
        if (s.owner == null) {
            s.lowApprovalDays = 0;
            return;
        }
        s.lowApprovalDays = s.affinity < 10 ? s.lowApprovalDays + 1 : 0;
        if (s.lowApprovalDays > 0 && s.lowApprovalDays < REVOLT_DAYS) {
            for (ServerPlayer p : governors(server, s)) {
                Rewards.banner(p, SettlementsLang.tr("needs.revolt.warn", SettlementService.cityName(server, s),
                        REVOLT_DAYS - s.lowApprovalDays), 0xE8735E);
            }
        }
        if (s.lowApprovalDays >= REVOLT_DAYS) {
            s.lowApprovalDays = 0;
            s.affinity = 30;
            server.getPlayerList().broadcastSystemMessage(SettlementsLang.tr("needs.revolt", SettlementService.cityName(server, s),
                    s.ownerName).withColor(0xE8735E), false);
            SettlementService.stepDown(server, s, false);
        }
    }

    // =====================================================================================
    //  Entregas: comida e combustível
    // =====================================================================================
    /** Valor de comida de 1 item (nutrição), 0 = não é comida. */
    public static int foodValue(ItemStack st, @Nullable ServerPlayer p) {
        FoodProperties fp = st.getFoodProperties(p);
        if (fp == null) return 0;
        if (st.is(Items.ROTTEN_FLESH) || st.is(Items.SPIDER_EYE) || st.is(Items.POISONOUS_POTATO)) return 0;
        return Math.max(1, fp.nutrition());
    }

    /** Valor de combustível de 1 item (0 = não serve). */
    public static int fuelValue(ItemStack st) {
        if (st.is(Items.COAL_BLOCK)) return 72;
        if (st.is(Items.COAL) || st.is(Items.CHARCOAL)) return 8;
        if (st.is(Items.BLAZE_ROD)) return 12;
        if (st.is(Items.DRIED_KELP_BLOCK)) return 4;
        if (st.is(ItemTags.LOGS)) return 4;
        if (st.is(ItemTags.PLANKS)) return 1;
        return 0;
    }

    /** Entrega uma pilha (a da mão ou do inventário): devolve quantos itens entraram. */
    public static int depositStack(MinecraftServer server, Settlement s, ServerPlayer p, ItemStack st) {
        if (st.isEmpty()) return 0;
        Stats cap = stats(server, s);
        int fv = foodValue(st, p);
        if (fv > 0) {
            int room = Math.max(0, cap.foodCap() - s.food);
            int n = Math.min(st.getCount(), room / fv);
            if (n <= 0) return 0;
            s.food += n * fv;
            st.shrink(n);
            return n;
        }
        int uv = fuelValue(st);
        if (uv > 0) {
            int room = Math.max(0, cap.fuelCap() - s.fuel);
            int n = Math.min(st.getCount(), room / uv);
            if (n <= 0) return 0;
            s.fuel += n * uv;
            st.shrink(n);
            return n;
        }
        return 0;
    }

    /** Botão "Entregar tudo": comida e combustível do inventário principal (não mexe na armadura nem na mão secundária). */
    public static void depositAll(ServerPlayer p, Settlement s) {
        int foodBefore = s.food, fuelBefore = s.fuel;
        var inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack st = inv.items.get(i);
            if (st.isEmpty()) continue;
            depositStack(p.server, s, p, st);
        }
        feedback(p, s, s.food - foodBefore, s.fuel - fuelBefore);
    }

    public static void feedback(ServerPlayer p, Settlement s, int food, int fuel) {
        if (food <= 0 && fuel <= 0) {
            p.displayClientMessage(SettlementsLang.tr("needs.deposit.none").withColor(0xE8735E), true);
            return;
        }
        p.displayClientMessage(SettlementsLang.tr("needs.deposit.ok", food, fuel, s.food, s.fuel).withColor(0x7DDA6B), true);
        p.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // =====================================================================================
    //  Doações, festival, Pedidos do Povo
    // =====================================================================================
    /** Doa moedas/esmeraldas/ouro ao cofre (até 640 moedas por vez): +1 de apoio a cada 16. */
    public static void donate(ServerPlayer p, Settlement s) {
        Item coin = SettlementService.coinItem();
        int coins = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.items.size() && coins < 640; i++) {
            ItemStack st = inv.items.get(i);
            int per = st.is(coin) || st.is(Items.EMERALD) ? 1 : st.is(Items.GOLD_INGOT) ? 2 : 0;
            if (per == 0) continue;
            int n = Math.min(st.getCount(), (640 - coins + per - 1) / per);
            st.shrink(n);
            coins += n * per;
        }
        if (coins <= 0) {
            p.displayClientMessage(SettlementsLang.tr("needs.donate.none").withColor(0xE8735E), true);
            return;
        }
        s.treasury = Math.min(Settlement.MAX_TREASURY, s.treasury + coins);
        int aff = Math.min(8, coins / 16);
        s.affinity = Math.min(Settlement.MAX_AFFINITY, s.affinity + aff);
        p.displayClientMessage(SettlementsLang.tr("needs.donate.ok", coins, aff).withColor(0xFFD86B), true);
        p.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    /** Pedidos possíveis: item (ou "#tag"), quantidade base. */
    private static final String[][] REQUESTS = {
            {"minecraft:bread", "32"}, {"minecraft:cooked_beef", "16"}, {"minecraft:baked_potato", "32"},
            {"minecraft:cooked_cod", "16"}, {"minecraft:apple", "24"}, {"minecraft:pumpkin_pie", "8"},
            {"minecraft:iron_ingot", "16"}, {"minecraft:gold_ingot", "8"}, {"minecraft:coal", "32"},
            {"#logs", "32"}, {"#wool", "16"}, {"minecraft:glass", "24"}, {"minecraft:book", "8"},
            {"minecraft:torch", "48"}, {"minecraft:leather", "12"}, {"minecraft:honey_bottle", "6"},
            {"minecraft:arrow", "48"}, {"minecraft:potion", "6"}, {"minecraft:bricks", "32"}, {"minecraft:lantern", "8"},
            {"minecraft:cake", "2"}, {"minecraft:candle", "8"}};

    /** Gera 3 pedidos novos a cada 3 dias (ou quando todos foram atendidos). */
    public static void refreshRequests(MinecraftServer server, Settlement s, boolean force) {
        long today = SettlementService.day(server);
        boolean allDone = !s.requests.isEmpty();
        for (CompoundTag r : s.requests) allDone &= r.getInt("Got") >= r.getInt("Need");
        if (!force && !allDone && !s.requests.isEmpty() && s.requestsDay >= 0 && today - s.requestsDay < 3) return;
        s.requests.clear();
        s.requestsDay = today;
        RandomSource r = RandomSource.create(s.id.hashCode() * 31L + today);
        List<Integer> picks = new ArrayList<>();
        while (picks.size() < 3) {
            int i = r.nextInt(REQUESTS.length);
            if (!picks.contains(i)) picks.add(i);
        }
        for (int i : picks) {
            CompoundTag t = new CompoundTag();
            t.putString("Item", REQUESTS[i][0]);
            t.putInt("Need", Math.max(1, Integer.parseInt(REQUESTS[i][1]) * (2 + s.level) / 3));
            t.putInt("Got", 0);
            s.requests.add(t);
        }
    }

    public static boolean matches(String item, ItemStack st) {
        if (item.equals("#logs")) return st.is(ItemTags.LOGS);
        if (item.equals("#wool")) return st.is(ItemTags.WOOL);
        ResourceLocation id = ResourceLocation.tryParse(item);
        return id != null && BuiltInRegistries.ITEM.getKey(st.getItem()).equals(id);
    }

    /** Ícone/nome de um pedido. */
    public static ItemStack icon(String item) {
        if (item.equals("#logs")) return new ItemStack(Items.OAK_LOG);
        if (item.equals("#wool")) return new ItemStack(Items.WHITE_WOOL);
        ResourceLocation id = ResourceLocation.tryParse(item);
        return id == null ? new ItemStack(Items.BARRIER) : new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    public static Component itemName(String item) {
        if (item.equals("#logs")) return SettlementsLang.tr("needs.req.logs");
        if (item.equals("#wool")) return SettlementsLang.tr("needs.req.wool");
        return icon(item).getHoverName();
    }

    /** Entrega o que tiver no inventário para o pedido i. */
    public static void deliver(ServerPlayer p, Settlement s, int index) {
        if (index < 0 || index >= s.requests.size()) return;
        CompoundTag r = s.requests.get(index);
        int left = r.getInt("Need") - r.getInt("Got");
        if (left <= 0) return;
        String item = r.getString("Item");
        int given = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.items.size() && given < left; i++) {
            ItemStack st = inv.items.get(i);
            if (st.isEmpty() || !matches(item, st)) continue;
            int n = Math.min(st.getCount(), left - given);
            st.shrink(n);
            given += n;
        }
        if (given <= 0) {
            p.displayClientMessage(SettlementsLang.tr("needs.req.missing", itemName(item)).withColor(0xE8735E), true);
            return;
        }
        r.putInt("Got", r.getInt("Got") + given);
        if (r.getInt("Got") >= r.getInt("Need")) {
            s.affinity = Math.min(Settlement.MAX_AFFINITY, s.affinity + 5);
            int coins = 8 * s.level;
            s.treasury = Math.min(Settlement.MAX_TREASURY, s.treasury + coins);
            Rewards.toast(p, SettlementsLang.tr("needs.req.done.title"), itemName(item), icon(item), 0x7DDA6B);
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
            boolean all = true;
            for (CompoundTag o : s.requests) all &= o.getInt("Got") >= o.getInt("Need");
            if (all) {
                s.affinity = Math.min(Settlement.MAX_AFFINITY, s.affinity + 5);
                p.displayClientMessage(SettlementsLang.tr("needs.req.all").withColor(0xFFD86B), false);
            }
        } else {
            p.displayClientMessage(SettlementsLang.tr("needs.req.partial", given, itemName(item), r.getInt("Got"), r.getInt("Need"))
                    .withColor(0xC9E8A0), true);
            p.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    // =====================================================================================
    //  Nível do centro (Coração/Prefeitura): requisitos para subir
    // =====================================================================================
    public record Req(Component text, boolean ok) {}

    private static final int[] REQ_PEOPLE = {0, 8, 16, 28, 42};
    private static final int[] REQ_APPROVAL = {0, 40, 50, 60, 70};
    private static final int[] REQ_BUILDINGS = {0, 4, 3, 4, 5};
    private static final float[] REQ_SECURITY = {0F, 0F, 0.6F, 0.8F, 1.0F};
    private static final int[] REQ_FOOD_DAYS = {0, 2, 3, 3, 4};
    private static final int[] REQ_FUEL_DAYS = {0, 0, 2, 3, 3};

    /** Requisitos para o centro ir do nível atual para o próximo (vazio no nível 5). */
    public static List<Req> centerReqs(MinecraftServer server, Settlement s) {
        List<Req> out = new ArrayList<>();
        int to = s.level + 1;
        if (to > Blueprints.MAX_LEVEL) return out;
        int i = to - 1;
        Stats st = stats(server, s);
        out.add(new Req(SettlementsLang.tr("center.req.people", s.people, REQ_PEOPLE[i]), s.people >= REQ_PEOPLE[i]));
        int needLevel = Math.max(1, to - 1);
        int count = 0;
        for (int cell : s.buildings.keySet()) {
            if (cell != com.renanpontes.isekaimahou.feature.settlements.build.CityLayout.CENTER_CELL && s.levelOf(cell) >= needLevel) count++;
        }
        out.add(new Req(needLevel <= 1 ? SettlementsLang.tr("center.req.buildings", count, REQ_BUILDINGS[i])
                : SettlementsLang.tr("center.req.buildings_lvl", count, REQ_BUILDINGS[i], needLevel), count >= REQ_BUILDINGS[i]));
        out.add(new Req(SettlementsLang.tr("center.req.approval", s.affinity, REQ_APPROVAL[i]), s.affinity >= REQ_APPROVAL[i]));
        int foodNeed = REQ_FOOD_DAYS[i] * st.foodUse();
        out.add(new Req(SettlementsLang.tr("center.req.food", s.food, foodNeed, REQ_FOOD_DAYS[i]), s.food >= foodNeed));
        if (REQ_FUEL_DAYS[i] > 0) {
            int fuelNeed = REQ_FUEL_DAYS[i] * st.fuelUse();
            out.add(new Req(SettlementsLang.tr("center.req.fuel", s.fuel, fuelNeed, REQ_FUEL_DAYS[i]), s.fuel >= fuelNeed));
        }
        if (REQ_SECURITY[i] > 0) {
            int pct = Math.round(st.securityRatio() * 100);
            out.add(new Req(SettlementsLang.tr("center.req.security", pct, Math.round(REQ_SECURITY[i] * 100)),
                    st.securityRatio() >= REQ_SECURITY[i]));
        }
        if (to >= 4) out.add(new Req(SettlementsLang.tr("center.req.hall"), s.has(Blueprint.TOWN_HALL)));
        // onda 5: requisitos do DEGRAU (prédios-chave, tamanho, grande projeto e o cofre)
        if (s.own()) out.addAll(CityTier.reqs(server, s));
        return out;
    }

    public static boolean centerReady(MinecraftServer server, Settlement s) {
        List<Req> reqs = centerReqs(server, s);
        if (reqs.isEmpty()) return false;
        for (Req r : reqs) if (!r.ok()) return false;
        return true;
    }

    private CityEconomy() {}
}
