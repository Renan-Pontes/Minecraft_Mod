package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.api.Allegiance;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * FELICIDADE da cidade (v1.0, city_life): é o "apoio da população" de sempre (0–100, {@link Settlement#affinity}), mas
 * agora com as contas ABERTAS — cada motivo do dia vira uma linha (+2 comida sobrando, −5 impostos altos...) que a
 * conta diária soma e a tela mostra, junto com uma dica do que falta.
 * <p>Faixas: Miserável (&lt;20) · Infeliz (&lt;40) · Contente (&lt;70) · Feliz (70+) · Radiante (90+).
 * Cidade feliz produz mais, recebe o dobro de imigrantes, constrói mais rápido e ganha uma vaga de caravana; cidade
 * miserável produz pela metade e perde gente. Sem punição injusta: a felicidade nunca cai mais de
 * {@link #MAX_DAILY_LOSS} por dia e a revolta avisa antes.
 */
public final class CityHappiness {
    public static final int MISERABLE = 20, UNHAPPY = 40, HAPPY = 70, RADIANT = 90;
    /** Queda máxima num único dia (um dia ruim não derruba a cidade). */
    public static final int MAX_DAILY_LOSS = 10;
    private static final int[] TAX = {4, 0, -5};

    /** Um motivo do dia: chave de texto ({@code happy.f.<key>}) e quanto muda. */
    public record Factor(String key, int delta) {}

    public static String mood(int v) {
        return v >= RADIANT ? "radiant" : v >= HAPPY ? "happy" : v >= UNHAPPY ? "content" : v >= MISERABLE ? "unhappy" : "miserable";
    }

    /** Multiplicador de produção e impostos. */
    public static float prodMult(int v) {
        if (v < MISERABLE) return 0.5F;
        if (v < UNHAPPY) return 0.75F;
        if (v < HAPPY) return 1.0F;
        return v >= RADIANT ? 1.25F : 1.15F;
    }

    /** Obras mais rápidas (%) em cidade feliz. */
    public static int speedBonus(int v) {
        return v >= RADIANT ? 20 : v >= HAPPY ? 10 : 0;
    }

    /** Imigração: cidade feliz recebe o dobro de gente nova por dia. */
    public static int growthMult(int v) {
        return v >= HAPPY ? 2 : 1;
    }

    /**
     * Motivos do dia com o estado dado (a conta diária passa o que aconteceu de verdade; a tela, a previsão).
     *
     * @param starving faltou comida hoje
     * @param cold     faltou combustível hoje
     * @param foodDays dias de comida que sobram
     * @param feast    o dia foi de festival
     */
    public static List<Factor> factors(MinecraftServer server, Settlement s, CityEconomy.Stats st, boolean starving, boolean cold,
                                       int foodDays, boolean feast) {
        List<Factor> out = new ArrayList<>();
        add(out, "leisure", st.approvalFx());
        add(out, "food", starving ? -10 : foodDays < 2 ? -3 : foodDays >= 4 ? 2 : 0);
        add(out, "fuel", cold ? -5 : 0);
        float sec = st.securityRatio();
        add(out, "security", sec < 0.5F ? -4 : sec < 1F ? -1 : 1);
        add(out, "housing", s.people > st.housing() ? -2 : 0);
        add(out, "tax", TAX[Math.max(0, Math.min(2, s.tax))]);
        add(out, "festival", feast ? 4 : CityDecor.isOn(s) ? 1 : 0);
        add(out, "visit", s.lordVisited ? 2 : 0);
        add(out, "defense", Math.min(3, s.killsToday / 10));
        add(out, "war", -3 * Allegiance.enemiesOf(server, s.id).size());
        // onda 5: o jeito da cidade (Oriental +1, Sombrio −1)
        add(out, "style", s.style.happiness());
        // onda 6: monumentos ativos que alegram o povo (Jardim Suspenso, Salão dos Heróis)
        add(out, "monuments", CityMonuments.cityStat(s, CityMonuments.Stat.APPROVAL));
        // o povo sempre espera mais: lá em cima a felicidade precisa ser mantida
        add(out, "expect", s.affinity > HAPPY ? -1 : 0);
        return out;
    }

    private static void add(List<Factor> out, String key, int delta) {
        if (delta != 0) out.add(new Factor(key, delta));
    }

    /** Previsão de hoje (com os estoques e decretos de agora). */
    public static List<Factor> forecast(MinecraftServer server, Settlement s, CityEconomy.Stats st) {
        int food = Math.min(st.foodCap(), s.food + st.foodProd()) - st.foodUse();
        int fuel = Math.min(st.fuelCap(), s.fuel + st.fuelProd()) - st.fuelUse();
        return factors(server, s, st, food < 0, fuel < 0, CityEconomy.daysLeft(Math.max(0, food), st.foodProd(), st.foodUse()),
                Lordship.festivalActive(server, s));
    }

    /** Soma dos motivos, com o piso de queda diária. */
    public static int total(List<Factor> factors) {
        int t = 0;
        for (Factor f : factors) t += f.delta();
        return Math.max(-MAX_DAILY_LOSS, t);
    }

    /** A dica mais útil agora: o que mais puxa a felicidade para baixo (ou o que ainda dá para ganhar). */
    public static Component hint(MinecraftServer server, Settlement s, CityEconomy.Stats st, List<Factor> factors) {
        Factor worst = null;
        for (Factor f : factors) {
            if (f.key().equals("expect") || f.key().equals("style")) continue;
            if (f.delta() < 0 && (worst == null || f.delta() < worst.delta())) worst = f;
        }
        if (worst != null) return SettlementsLang.tr("happy.hint." + worst.key());
        if (st.approvalFx() < 4) return SettlementsLang.tr("happy.hint.leisure");
        if (s.tax == Settlement.TAX_NORMAL && s.affinity < HAPPY) return SettlementsLang.tr("happy.hint.tax_low");
        if (!Lordship.festivalActive(server, s) && Lordship.festivalWait(server, s) == 0 && s.affinity < RADIANT) {
            return SettlementsLang.tr("happy.hint.festival_idea");
        }
        return SettlementsLang.tr("happy.hint.good");
    }

    private CityHappiness() {}
}
