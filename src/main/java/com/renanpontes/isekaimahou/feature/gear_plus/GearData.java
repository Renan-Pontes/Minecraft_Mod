package com.renanpontes.isekaimahou.feature.gear_plus;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Dados do módulo guardados com o jogador (attachment {@code gear_plus_data}, sobrevive à morte):
 * <ul>
 *   <li>{@code bond.<classe>}: abates com a arma da classe (Vínculo da arma que desperta);</li>
 *   <li>{@code awake.<classe>}: o despertar já foi anunciado;</li>
 *   <li>{@code kit_last}: hora (tempo de jogo do mundo) da última recuperação do kit;</li>
 *   <li>{@code kit_best.<classe>.<vaga>}: a melhor peça que o jogador já carregou em cada vaga do kit ({@link KitBest}):
 *       {@code id} do item, {@code rank}, {@code score} e {@code keep} (componentes que voltam com ela).</li>
 * </ul>
 * Chaves novas sempre opcionais (ausente = padrão).
 */
public final class GearData {
    private static final String BOND = "bond", AWAKE = "awake", KIT_LAST = "kit_last", KIT_BEST = "kit_best";

    private static CompoundTag tag(ServerPlayer sp) {
        return sp.getData(GearRegistry.DATA.get());
    }

    private static void save(ServerPlayer sp, CompoundTag tag) {
        sp.setData(GearRegistry.DATA.get(), tag);
    }

    public static int bondKills(ServerPlayer sp, MagicPath path) {
        return tag(sp).getCompound(BOND).getInt(path.id);
    }

    public static void setBondKills(ServerPlayer sp, MagicPath path, int kills) {
        CompoundTag t = tag(sp);
        CompoundTag bond = t.getCompound(BOND);
        bond.putInt(path.id, Math.max(0, kills));
        t.put(BOND, bond);
        save(sp, t);
    }

    public static boolean awakeAnnounced(ServerPlayer sp, MagicPath path) {
        return tag(sp).getCompound(AWAKE).getBoolean(path.id);
    }

    public static void setAwakeAnnounced(ServerPlayer sp, MagicPath path, boolean value) {
        CompoundTag t = tag(sp);
        CompoundTag awake = t.getCompound(AWAKE);
        awake.putBoolean(path.id, value);
        t.put(AWAKE, awake);
        save(sp, t);
    }

    /** Hora da última recuperação do kit, ou -1 se nunca recuperou. */
    public static long kitLast(ServerPlayer sp) {
        CompoundTag t = tag(sp);
        return t.contains(KIT_LAST) ? t.getLong(KIT_LAST) : -1L;
    }

    public static void setKitLast(ServerPlayer sp, long time) {
        CompoundTag t = tag(sp);
        if (time < 0) t.remove(KIT_LAST);
        else t.putLong(KIT_LAST, time);
        save(sp, t);
    }

    /** Registros das vagas do kit da classe (vaga → registro). Só para LEITURA: grave com {@link #setKitBest}. */
    public static CompoundTag kitBest(ServerPlayer sp, MagicPath path) {
        return tag(sp).getCompound(KIT_BEST).getCompound(path.id);
    }

    /** Grava (ou apaga, com {@code null}) o registro de uma vaga do kit. */
    public static void setKitBest(ServerPlayer sp, MagicPath path, String slot, @Nullable CompoundTag record) {
        CompoundTag t = tag(sp);
        CompoundTag best = t.getCompound(KIT_BEST);
        CompoundTag mine = best.getCompound(path.id);
        if (record == null) mine.remove(slot);
        else mine.put(slot, record);
        best.put(path.id, mine);
        t.put(KIT_BEST, best);
        save(sp, t);
    }

    /** Depuração: esquece as melhores peças da classe (a recuperação volta a dar o kit básico). */
    public static void clearKitBest(ServerPlayer sp, MagicPath path) {
        CompoundTag t = tag(sp);
        CompoundTag best = t.getCompound(KIT_BEST);
        best.remove(path.id);
        t.put(KIT_BEST, best);
        save(sp, t);
    }

    private GearData() {}
}
