package com.renanpontes.isekaimahou.feature.character;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Raça do jogador (Data Attachment {@code isekai_mahou:character}, salvo e copiado na morte).
 * <p>
 * v1.0.1: todas as raças são livres ({@link #isUnlocked} sempre true — as chaves {@code unlocked}/{@code announced} da
 * v1.0 continuam sendo lidas e gravadas só por compatibilidade). Novos campos: os <b>feitos raciais</b> (um contador por
 * raça, exigido no nível de raça V — {@link RaceProgress}), o último nível de raça anunciado e a recarga da
 * Transformação ({@link RaceForms}). Campos ausentes no NBT começam com o padrão.
 */
public final class CharacterData implements INBTSerializable<CompoundTag> {
    private Race race = Race.HUMAN;
    /** v1.0 (sem uso desde a v1.0.1): raças desbloqueadas. Mantido no save para compatibilidade. */
    private int unlocked = Race.HUMAN.bit();
    private int announced = Race.HUMAN.bit();
    /** Escolheu a raça (entrada, aba ou altar). Sem isso, nenhum efeito racial é aplicado. */
    private boolean created;
    /** Ancestralidade dracônica (elemento do sopro e das escamas), definida ao virar Dracônico. */
    private Element ancestry = Element.FIRE;
    /** Visão no escuro ligada (raças que têm). */
    private boolean darkvision = true;
    /** Quantas vezes renasceu num Altar. */
    private int rebirths;

    // ---- feitos raciais ----
    /** Blocos minerados abaixo de y 0 (feito do Anão). */
    private int deepMined;
    /** Animais domados (feito do Bestial). */
    private int tames;
    /** Contador do feito de cada raça (índice = ordinal); Anão e Bestial usam os campos acima. */
    private final int[] feats = new int[Race.ALL.length];
    /** Último nível de raça anunciado (para o letreiro de "nível de raça"); −1 = alinhar em silêncio. */
    private int announcedLevel = -1;

    // ---- recargas (tempo de jogo do Overworld) ----
    private long relentlessReadyAt;
    /** Quando a Transformação pode ser usada de novo. */
    private long formReadyAt;

    /** Algo mudou: reenviar ao cliente e reaplicar os modificadores. */
    public transient boolean dirty = true;

    public Race race() { return race; }
    public boolean created() { return created; }
    public Element ancestry() { return ancestry; }
    public boolean darkvision() { return darkvision; }
    public int rebirths() { return rebirths; }
    public int deepMined() { return deepMined; }
    public int tames() { return tames; }
    public long relentlessReadyAt() { return relentlessReadyAt; }
    public long formReadyAt() { return formReadyAt; }
    public int announcedLevel() { return announcedLevel; }

    /** Contador cru do feito da raça {@code r}. */
    public int feat(Race r) { return feats[r.ordinal()]; }

    /** Raça em vigor (null enquanto não escolhida). */
    public Race activeRace() { return created ? race : null; }

    public boolean is(Race r) { return created && race == r; }

    /** v1.0.1: todas as raças são livres. */
    public boolean isUnlocked(Race r) { return true; }

    // ---- escrita (servidor) ----
    /** Escolhe/troca a raça. {@code newAncestry} null = mantém a atual. */
    public void choose(Race r, Element newAncestry) {
        race = r;
        if (newAncestry != null) ancestry = newAncestry;
        created = true;
        dirty = true;
    }

    public void setDarkvision(boolean on) {
        darkvision = on;
        dirty = true;
    }

    public void addRebirth() {
        rebirths++;
    }

    public void addDeepMined(int n) {
        deepMined = Math.max(0, deepMined + n);
    }

    public void addTame() {
        tames++;
    }

    public void addFeat(Race r, int n) {
        feats[r.ordinal()] = Math.max(0, feats[r.ordinal()] + n);
    }

    public void setFeat(Race r, int n) {
        feats[r.ordinal()] = Math.max(0, n);
        dirty = true;
    }

    public void setDeepMined(int n) {
        deepMined = Math.max(0, n);
        dirty = true;
    }

    public void setTames(int n) {
        tames = Math.max(0, n);
        dirty = true;
    }

    public void setAnnouncedLevel(int lv) {
        announcedLevel = lv;
        dirty = true;
    }

    public void setRelentlessReadyAt(long t) {
        relentlessReadyAt = t;
        dirty = true;
    }

    public void setFormReadyAt(long t) {
        formReadyAt = t;
        dirty = true;
    }

    /** Volta ao estado "sem raça escolhida" (comando de op). Feitos ficam. */
    public void reset() {
        race = Race.HUMAN;
        created = false;
        relentlessReadyAt = 0;
        formReadyAt = 0;
        announcedLevel = -1;
        dirty = true;
    }

    // =====================================================================================
    //  NBT
    // =====================================================================================
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putString("race", race.id);
        t.putInt("unlocked", unlocked);
        t.putInt("announced", announced);
        t.putBoolean("created", created);
        t.putString("ancestry", ancestry.id);
        t.putBoolean("darkvision", darkvision);
        t.putInt("rebirths", rebirths);
        t.putInt("deep_mined", deepMined);
        t.putInt("tames", tames);
        t.putLong("relentless_ready", relentlessReadyAt);
        t.putLong("form_ready", formReadyAt);
        t.putInt("announced_level", announcedLevel);
        CompoundTag f = new CompoundTag();
        for (Race r : Race.ALL) {
            if (feats[r.ordinal()] > 0) f.putInt(r.id, feats[r.ordinal()]);
        }
        t.put("feats", f);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        Race r = Race.byId(t.getString("race"));
        race = r == null ? Race.HUMAN : r;
        unlocked = (t.contains("unlocked") ? t.getInt("unlocked") : 0) | Race.HUMAN.bit();
        announced = (t.contains("announced") ? t.getInt("announced") : 0) | Race.HUMAN.bit();
        created = t.getBoolean("created");
        Element e = t.contains("ancestry") ? Element.byId(t.getString("ancestry")) : null;
        ancestry = e == null || !Element.BASE.contains(e) ? Element.FIRE : e;
        darkvision = !t.contains("darkvision") || t.getBoolean("darkvision");
        rebirths = t.getInt("rebirths");
        deepMined = t.getInt("deep_mined");
        tames = t.getInt("tames");
        relentlessReadyAt = t.getLong("relentless_ready");
        formReadyAt = t.getLong("form_ready");
        // Save antigo (v1.0): −1 = alinhar em silêncio na 1ª checagem (sem uma chuva de letreiros)
        announcedLevel = t.contains("announced_level") ? t.getInt("announced_level") : -1;
        CompoundTag f = t.getCompound("feats");
        for (Race x : Race.ALL) feats[x.ordinal()] = f.getInt(x.id);
        dirty = true;
    }

    /** Cópia para o cliente (o mesmo NBT). */
    public CompoundTag toClientTag(HolderLookup.Provider provider) {
        return serializeNBT(provider);
    }
}
