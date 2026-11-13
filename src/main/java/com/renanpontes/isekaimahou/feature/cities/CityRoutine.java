package com.renanpontes.isekaimahou.feature.cities;

import com.renanpontes.isekaimahou.feature.cities.entity.CityMerchant;
import com.renanpontes.isekaimahou.feature.cities.entity.Profession;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Rotina simples dos moradores (release 1.0 — "pode ser simples... se for complexo esquece"). É só o relógio do
 * mundo, sem agenda nem busca de caminho própria:
 * <ul>
 *   <li>de dia cada um fica no seu lugar de trabalho/passeio (como sempre);</li>
 *   <li>no começo da noite ({@link Phase#EVENING}) parte dos cidadãos vai para perto do estalajadeiro (a taverna) e o
 *       resto volta para casa;</li>
 *   <li>de noite todos os civis ficam em casa;</li>
 *   <li>de madrugada ({@link Phase#LATE}) as lojas fecham — menos a estalagem, o templo e o receptador.</li>
 * </ul>
 * O custo é uma conta com a hora do mundo; a única busca (achar a taverna) roda uma vez por dia por cidadão que vai
 * à taverna. {@code /mahou city routine off} desliga tudo (fica salvo no mundo).
 */
public final class CityRoutine {
    public enum Phase { DAY, EVENING, NIGHT, LATE }

    /** Fim da tarde (18h), começo do recolher (21h), madrugada (23h) e a alvorada (5h30) em ticks do dia. */
    private static final long EVENING_AT = 12000L, NIGHT_AT = 15000L, LATE_AT = 17000L, DAWN_AT = 23500L;
    private static final int CLOSED_LINES = 3;

    private static volatile boolean enabled = true;

    public static boolean enabled() {
        return enabled;
    }

    /** Lê a chave salva (subida do servidor). */
    static void load(MinecraftServer server) {
        enabled = !CityWorld.get(server).routineOff;
    }

    public static void setEnabled(MinecraftServer server, boolean on) {
        enabled = on;
        CityWorld w = CityWorld.get(server);
        w.routineOff = !on;
        w.setDirty();
    }

    /** Parte do dia para a rotina (sempre DIA com a rotina desligada ou em dimensão sem ciclo de dia). */
    public static Phase phase(Level level) {
        if (!enabled || level.dimensionType().hasFixedTime()) return Phase.DAY;
        long t = Math.floorMod(level.getDayTime(), 24000L);
        if (t < EVENING_AT || t >= DAWN_AT) return Phase.DAY;
        if (t < NIGHT_AT) return Phase.EVENING;
        return t < LATE_AT ? Phase.NIGHT : Phase.LATE;
    }

    /** A loja deste mercador está fechada agora? Estalagem, templo e receptador não fecham. */
    public static boolean shopClosed(CityMerchant m) {
        if (phase(m.level()) != Phase.LATE) return false;
        Profession p = m.profession();
        return p != Profession.INNKEEPER && p != Profession.PRIEST && p != Profession.FENCE;
    }

    /** "Estamos fechados, volte de manhã." */
    public static MutableComponent closedLine(RandomSource r) {
        return CityLines.line("merchant.closed." + r.nextInt(CLOSED_LINES));
    }

    private CityRoutine() {}
}
