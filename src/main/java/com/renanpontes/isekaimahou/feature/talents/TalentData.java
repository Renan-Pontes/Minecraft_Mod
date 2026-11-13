package com.renanpontes.isekaimahou.feature.talents;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Talentos do jogador (Data Attachment {@code isekai_mahou:talents}, salvo e copiado na morte — NÃO fica no MagicData).
 * <p>
 * Tudo é guardado POR CLASSE (id da classe → nós/especialização): trocar de classe abre a árvore da classe nova zerada
 * e, se voltar, a antiga continua como estava. Os pontos NÃO são salvos: vêm do nível ({@link Talents#earned}) mais os
 * {@link #bonusPoints() pontos bônus} (recompensas/comando). NBT: {@code ranks{classe{nó:int}}}, {@code subclass{classe:id}},
 * {@code bonus}. Campo ausente = padrão.
 */
public final class TalentData implements INBTSerializable<CompoundTag> {
    private final Map<String, Map<String, Integer>> ranks = new HashMap<>();
    private final Map<String, String> subclass = new HashMap<>();
    private int bonusPoints;

    /** Algo mudou: reaplicar os modificadores e reenviar ao cliente. */
    public transient boolean dirty = true;
    /** Servidor: classe para a qual os efeitos estão aplicados (null = nunca aplicou nesta entidade). */
    public transient String appliedClass;
    /** Servidor: versão das árvores com que os efeitos foram aplicados (-1 = nenhuma). */
    public transient int sentTreeVersion = -1;
    /** Servidor: "classe#versão" da árvore (JSON) que o cliente deste jogador já tem. */
    public transient String sentTreeKey;
    /** Servidor: passivas somadas (recalculadas quando os talentos mudam). */
    public transient TalentStats stats = TalentStats.EMPTY;
    /** Servidor: "classe#versão" da árvore com que a distribuição foi conferida pela última vez (mundos antigos). */
    public transient String checkedKey;

    // ---- leitura ----
    public int rank(String classId, String node) {
        Map<String, Integer> m = ranks.get(classId);
        return m == null ? 0 : m.getOrDefault(node, 0);
    }

    /** Nós com nível da classe (somente leitura). */
    public Map<String, Integer> ranks(String classId) {
        Map<String, Integer> m = ranks.get(classId);
        return m == null ? Map.of() : Collections.unmodifiableMap(m);
    }

    /** Pontos gastos na árvore da classe. */
    public int spent(String classId) {
        int n = 0;
        for (int r : ranks(classId).values()) n += r;
        return n;
    }

    /** Especialização da classe ("" = ainda não escolheu). */
    public String subclass(String classId) {
        return subclass.getOrDefault(classId, "");
    }

    public int bonusPoints() { return bonusPoints; }

    // ---- escrita (servidor) ----
    public void setRank(String classId, String node, int rank) {
        if (rank <= 0) {
            Map<String, Integer> m = ranks.get(classId);
            if (m != null) {
                m.remove(node);
                if (m.isEmpty()) ranks.remove(classId);
            }
        } else {
            ranks.computeIfAbsent(classId, k -> new HashMap<>()).put(node, rank);
        }
        dirty = true;
    }

    public void clearRanks(String classId) {
        if (ranks.remove(classId) != null) dirty = true;
    }

    public void setSubclass(String classId, String id) {
        if (id == null || id.isEmpty()) subclass.remove(classId);
        else subclass.put(classId, id);
        dirty = true;
    }

    public void setBonusPoints(int n) {
        bonusPoints = Math.max(0, n);
        dirty = true;
    }

    // ---- NBT ----
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        CompoundTag r = new CompoundTag();
        for (Map.Entry<String, Map<String, Integer>> e : ranks.entrySet()) {
            CompoundTag c = new CompoundTag();
            for (Map.Entry<String, Integer> n : e.getValue().entrySet()) c.putInt(n.getKey(), n.getValue());
            r.put(e.getKey(), c);
        }
        tag.put("ranks", r);
        CompoundTag s = new CompoundTag();
        for (Map.Entry<String, String> e : subclass.entrySet()) s.putString(e.getKey(), e.getValue());
        tag.put("subclass", s);
        tag.putInt("bonus", bonusPoints);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        ranks.clear();
        subclass.clear();
        CompoundTag r = tag.getCompound("ranks");
        for (String cls : r.getAllKeys()) {
            if (!r.contains(cls, Tag.TAG_COMPOUND)) continue;
            CompoundTag c = r.getCompound(cls);
            Map<String, Integer> m = new HashMap<>();
            for (String node : c.getAllKeys()) {
                int v = c.getInt(node);
                if (v > 0) m.put(node, v);
            }
            if (!m.isEmpty()) ranks.put(cls, m);
        }
        CompoundTag s = tag.getCompound("subclass");
        for (String cls : s.getAllKeys()) {
            String id = s.getString(cls);
            if (!id.isEmpty()) subclass.put(cls, id);
        }
        bonusPoints = Math.max(0, tag.getInt("bonus"));
        dirty = true;
    }
}
