package com.renanpontes.isekaimahou.feature.familiars;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * O Familiar do jogador (Data Attachment {@code isekai_mahou:familiars}, salvo e copiado na morte). Um por jogador.
 * A entidade no mundo é só o "corpo": some e volta à vontade; tudo o que importa (forma, estágio, vínculo, nome e a
 * bolsa) mora aqui. Campos novos devem ser opcionais (ausente no NBT = padrão).
 */
public final class FamiliarData implements INBTSerializable<CompoundTag> {
    /** Tamanho máximo da bolsa (3 linhas); o que o jogador alcança depende da forma/estágio. */
    public static final int STASH_SIZE = 27;
    public static final int MAX_NAME = 24;

    private boolean awakened;
    private FamiliarForm form = FamiliarForm.RUNE_WISP;
    private int stage = 1;
    /** Vínculo: cresce com o tempo junto, com os níveis do dono e com carinho. Nunca diminui. */
    private int bond;
    /** O jogador quer o familiar ao lado (false = guardado). */
    private boolean summoned = true;
    /** Ajuda ligada (dá para desligar sem guardar o familiar). */
    private boolean help = true;
    private String name = "";
    /** Tempo de jogo da última mudança de forma. */
    private long lastShift;
    /** Forma para a qual o jogador está "puxando" e há quantas avaliações seguidas. */
    private String candidate = "";
    private int candidateStreak;
    /** Dia do mundo do último carinho que rendeu vínculo. */
    private int petDay = -1;
    /** Quantas vezes já mudou de forma. */
    private int shifts;
    /** Até quando o Corvo guia sozinho até o lugar da última morte (tempo de jogo). */
    private long deathGuideUntil;
    /**
     * v1.0 (onda 4): a ÚNICA receita de apoio que o dono ensinou ao familiar ({@link FamiliarSpell}); vazia = nenhuma.
     * Campo opcional: familiares salvos antes disso carregam sem receita.
     */
    private SpellRecipe spell = SpellRecipe.EMPTY;
    /** Tempo de jogo em que a receita ensinada pode ser usada de novo (recarga). */
    private long spellReady;
    /** A receita ensinada já validada (cache do servidor; refeito quando a receita muda). */
    @Nullable
    transient ParsedSpell spellParsed;
    private final NonNullList<ItemStack> stash = NonNullList.withSize(STASH_SIZE, ItemStack.EMPTY);

    /** Algo mudou: reenviar ao cliente. */
    public transient boolean dirty = true;

    public boolean awakened() { return awakened; }
    public FamiliarForm form() { return form; }
    public int stage() { return stage; }
    public int bond() { return bond; }
    public boolean summoned() { return summoned; }
    public boolean help() { return help; }
    public String name() { return name; }
    public long lastShift() { return lastShift; }
    public String candidate() { return candidate; }
    public int candidateStreak() { return candidateStreak; }
    public int petDay() { return petDay; }
    public int shifts() { return shifts; }
    public long deathGuideUntil() { return deathGuideUntil; }
    public NonNullList<ItemStack> stash() { return stash; }
    public SpellRecipe spell() { return spell; }
    public long spellReady() { return spellReady; }

    /** Ensina (ou, com receita vazia/null, faz esquecer) a receita de apoio. */
    public void setSpell(@Nullable SpellRecipe recipe) {
        spell = recipe == null || recipe.isEmpty() ? SpellRecipe.EMPTY : recipe;
        spellParsed = null;
        dirty = true;
    }

    public void setSpellReady(long gameTime) {
        spellReady = gameTime;
        dirty = true;
    }

    public void awaken(FamiliarForm f, long gameTime) {
        awakened = true;
        form = f;
        stage = 1;
        summoned = true;
        lastShift = gameTime;
        candidate = "";
        candidateStreak = 0;
        dirty = true;
    }

    public void setForm(FamiliarForm f, long gameTime) {
        if (form != f) shifts++;
        form = f;
        lastShift = gameTime;
        candidate = "";
        candidateStreak = 0;
        dirty = true;
    }

    public void setStage(int s) {
        stage = Math.max(1, Math.min(FamiliarForm.MAX_STAGE, s));
        dirty = true;
    }

    public void addBond(int n) {
        if (n <= 0) return;
        bond = Math.min(100000, bond + n);
        dirty = true;
    }

    public void setBond(int n) {
        bond = Math.max(0, Math.min(100000, n));
        dirty = true;
    }

    public void setSummoned(boolean on) {
        summoned = on;
        dirty = true;
    }

    public void setHelp(boolean on) {
        help = on;
        dirty = true;
    }

    public void setName(String n) {
        name = n == null ? "" : n;
        dirty = true;
    }

    public void setCandidate(String id, int streak) {
        if (!candidate.equals(id) || candidateStreak != streak) dirty = true;
        candidate = id;
        candidateStreak = streak;
    }

    public void setPetDay(int day) { petDay = day; }
    public void setDeathGuideUntil(long t) { deathGuideUntil = t; }

    /** Volta ao zero (comando de teste). A bolsa NÃO é apagada: quem chama devolve os itens antes. */
    public void reset() {
        awakened = false;
        form = FamiliarForm.RUNE_WISP;
        stage = 1;
        bond = 0;
        summoned = true;
        help = true;
        name = "";
        lastShift = 0;
        candidate = "";
        candidateStreak = 0;
        petDay = -1;
        shifts = 0;
        deathGuideUntil = 0;
        spell = SpellRecipe.EMPTY;
        spellParsed = null;
        spellReady = 0;
        dirty = true;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("awakened", awakened);
        t.putString("form", form.id);
        t.putInt("stage", stage);
        t.putInt("bond", bond);
        t.putBoolean("summoned", summoned);
        t.putBoolean("help", help);
        t.putString("name", name);
        t.putLong("last_shift", lastShift);
        t.putString("candidate", candidate);
        t.putInt("candidate_streak", candidateStreak);
        t.putInt("pet_day", petDay);
        t.putInt("shifts", shifts);
        t.putLong("death_guide_until", deathGuideUntil);
        if (!spell.isEmpty()) {
            SpellRecipe.CODEC.encodeStart(NbtOps.INSTANCE, spell).result().ifPresent(tag -> t.put("spell", tag));
            t.putLong("spell_ready", spellReady);
        }
        CompoundTag items = new CompoundTag();
        ContainerHelper.saveAllItems(items, stash, false, provider);
        t.put("stash", items);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        awakened = t.getBoolean("awakened");
        FamiliarForm f = FamiliarForm.byId(t.getString("form"));
        form = f == null ? FamiliarForm.RUNE_WISP : f;
        stage = Math.max(1, Math.min(FamiliarForm.MAX_STAGE, t.contains("stage") ? t.getInt("stage") : 1));
        bond = Math.max(0, t.getInt("bond"));
        summoned = !t.contains("summoned") || t.getBoolean("summoned");
        help = !t.contains("help") || t.getBoolean("help");
        name = t.getString("name");
        lastShift = t.getLong("last_shift");
        candidate = t.getString("candidate");
        candidateStreak = t.getInt("candidate_streak");
        petDay = t.contains("pet_day") ? t.getInt("pet_day") : -1;
        shifts = t.getInt("shifts");
        deathGuideUntil = t.getLong("death_guide_until");
        spell = t.contains("spell") ? SpellRecipe.CODEC.parse(NbtOps.INSTANCE, t.get("spell")).result().orElse(SpellRecipe.EMPTY)
                : SpellRecipe.EMPTY;
        spellParsed = null;
        spellReady = t.getLong("spell_ready");
        for (int i = 0; i < stash.size(); i++) stash.set(i, ItemStack.EMPTY);
        if (t.contains("stash")) ContainerHelper.loadAllItems(t.getCompound("stash"), stash, provider);
        dirty = true;
    }
}
