package com.renanpontes.isekaimahou.feature.talents.client;

import javax.annotation.Nullable;

import com.google.gson.JsonParser;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.talents.TalentData;
import com.renanpontes.isekaimahou.feature.talents.TalentTree;
import com.renanpontes.isekaimahou.feature.talents.Talents;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;

/** Cópia dos talentos no cliente: a árvore da classe do jogador (vinda do servidor em JSON) e os dados dele. */
public final class ClientTalents {
    private static TalentData data = new TalentData();
    @Nullable private static TalentTree tree;
    private static String classId = "";
    /** Sobe a cada sincronia (a aba recalcula o painel de bônus quando muda). */
    public static int version;

    static void onSync(CompoundTag tag) {
        classId = tag.getString("class");
        TalentData d = new TalentData();
        d.deserializeNBT(RegistryAccess.EMPTY, tag.getCompound("data"));
        data = d;
        if (tag.contains("tree")) {
            String json = tag.getString("tree");
            try {
                tree = json.isEmpty() ? null : TalentTree.parse(classId, JsonParser.parseString(json).getAsJsonObject());
            } catch (RuntimeException e) {
                tree = null;
                IsekaiMahou.LOGGER.warn("Árvore de talentos ilegível vinda do servidor ({})", classId, e);
            }
        }
        version++;
    }

    static void reset() {
        data = new TalentData();
        tree = null;
        classId = "";
        version++;
    }

    public static TalentData data() { return data; }

    /** Árvore da classe ATUAL do jogador (null enquanto não chegou ou se a classe não tem). */
    @Nullable
    public static TalentTree tree() {
        return tree != null && classId.equals(ClientMagic.data().path().id) ? tree : null;
    }

    public static String classId() { return classId; }

    public static int rank(String node) { return data.rank(classId, node); }

    public static int spent() { return data.spent(classId); }

    public static int earned() { return Talents.earned(ClientMagic.data().level(), data.bonusPoints()); }

    public static int unspent() { return Math.max(0, earned() - spent()); }

    /** As mesmas regras de compra do servidor ({@link Talents#check}), com os dados deste cliente. */
    public static Talents.Result check(TalentTree.Node node) {
        TalentTree t = tree();
        return t == null ? Talents.Result.NO_TREE : Talents.check(t, data, classId, node, ClientMagic.data().level(), unspent());
    }

    /** Pontos que contam para liberar o nó (fileiras acima, no mesmo ramo ou na fileira da especialização). */
    public static int groupPoints(TalentTree.Node node) {
        TalentTree t = tree();
        return t == null ? 0 : Talents.groupPoints(t, data, classId, node);
    }

    /** Especialização da classe atual ("" = nenhuma). */
    public static String subclass() { return data.subclass(ClientMagic.data().path().id); }

    private ClientTalents() {}
}
