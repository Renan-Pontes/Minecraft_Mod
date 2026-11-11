package com.renanpontes.isekaimahou.network;

import java.util.HashMap;
import java.util.Map;

import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.nbt.NbtOps;

/**
 * Cópia do estado mágico NO CLIENTE (HUD, menus, editor). Classe sem imports de cliente:
 * é seguro existir no servidor (os handlers de pacote ficam no código comum).
 */
public final class ClientMagic {
    private static MagicData data = new MagicData();
    public static float mana = data.mana();
    public static int maxMana = data.maxMana();
    public static boolean meditating;

    /** Recargas: chave do slot -> ticks restantes (o cliente decrementa a cada tick). */
    public static final Map<String, Integer> cooldownLeft = new HashMap<>();
    public static final Map<String, Integer> cooldownTotal = new HashMap<>();

    /** Conjuração em andamento (previsão local para a barra). slot = -1 quando nenhuma. */
    public static int castSlot = -1;
    public static int castTotal;
    public static int castElapsed;
    public static String castChant = "";

    /** Sinais do servidor tratados no próximo tick do cliente. */
    public static volatile boolean pendingPathScreen;

    /** Incrementado a cada sincronização completa (telas podem detectar mudanças). */
    public static int syncVersion;

    public static MagicData data() {
        return data;
    }

    static void applySync(CorePayloads.MagicSync p) {
        MagicData.CODEC.parse(NbtOps.INSTANCE, p.data()).result().ifPresent(d -> {
            // Bônus de equipamento = máximo recebido no ManaSync − Limite de Mana (v1.0)
            d.manaBonus = Math.max(0, maxMana - d.manaLimit());
            d.lastCastSlot = data.lastCastSlot;
            data = d;
            syncVersion++;
        });
    }

    static void applyMana(CorePayloads.ManaSync p) {
        mana = p.mana();
        maxMana = p.maxMana();
        meditating = p.meditating();
    }

    static void applyCooldown(CorePayloads.Cooldown p) {
        cooldownLeft.put(p.key(), p.ticks());
        cooldownTotal.put(p.key(), Math.max(1, p.ticks()));
    }

    static void applyCastState(CorePayloads.CastState p) {
        if (p.state() == 0) {
            castSlot = p.slot();
            if (p.slot() >= 0) data.lastCastSlot = p.slot();
            castTotal = Math.max(1, p.ticks());
            castElapsed = 0;
            castChant = p.chant();
        } else {
            castSlot = -1;
            castChant = "";
        }
    }

    /** Chamado a cada tick do cliente (pelo IsekaiMahouClient). */
    public static void clientTick() {
        cooldownLeft.replaceAll((k, v) -> Math.max(0, v - 1));
        if (castSlot >= 0 && ++castElapsed > castTotal + 40) castSlot = -1;
    }

    /**
     * v1.0: previsão local da troca de loadout (o HUD muda na hora; o servidor confirma no próximo sync).
     * Quem chama manda {@code CorePayloads.SelectLoadout} (ver {@code client.LoadoutInput}).
     */
    public static void predictLoadout(int loadout) {
        data.setActiveLoadout(loadout);
        data.lastCastSlot = -1;
    }

    public static void reset() {
        data = new MagicData();
        mana = data.mana();
        maxMana = data.maxMana();
        cooldownLeft.clear();
        cooldownTotal.clear();
        castSlot = -1;
    }

    private ClientMagic() {}
}
