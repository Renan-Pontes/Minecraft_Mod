package com.renanpontes.isekaimahou.feature.class_gunslinger;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * As cinco armas de mana do Pistoleiro. Cada uma tem o seu jeito de atirar com o clique direito (tiro básico, sem
 * glifo): tambor, dano, cadência, recarga, dispersão, chumbos (escopeta) e se o tiro é automático (segurar o botão).
 * A munição é o Foco: recarregar o tambor gasta uma FRAÇÃO do Foco máximo por cartucho ({@link #reloadCostPerRound}),
 * então escala sozinha com a mana treinável da v1.0.
 * <p>
 * Desbloqueio pelo Caminho do Gatilho ({@link #unlockRank}): o revólver vem no kit; as outras são materializadas na aba
 * do menu B quando o rank é alcançado.
 */
public enum GunKind {
    //            id              tambor dano  interv recarga disp. veloc chumbos auto  perfura rank custo/cartucho
    REVOLVER("revolver", 6, 6.0F, 8, 34, 0.4F, 4.2F, 1, false, 0, 0, 0.012F),
    PISTOL("pistol", 12, 3.4F, 4, 28, 1.2F, 4.0F, 1, true, 0, 1, 0.006F),
    SHOTGUN("shotgun", 2, 2.4F, 16, 40, 9.0F, 3.2F, 7, false, 0, 2, 0.030F),
    DUAL_PISTOLS("dual_pistols", 16, 3.0F, 3, 40, 1.8F, 4.0F, 1, true, 0, 3, 0.005F),
    RIFLE("rifle", 5, 12.0F, 24, 48, 0.1F, 6.0F, 1, false, 1, 4, 0.016F);

    public final String id;
    /** Cartuchos no tambor cheio. */
    public final int rounds;
    /** Dano base de cada projétil do tiro básico (antes de tier, rank, nível e DAMAGE_SCALE). */
    public final float damage;
    /** Ticks entre tiros (recarga do item no clique; cadência no automático). */
    public final int interval;
    /** Ticks para recarregar o tambor inteiro. */
    public final int reloadTicks;
    /** Dispersão em graus. */
    public final float spread;
    /** Velocidade da bala (blocos/tick). */
    public final float speed;
    /** Projéteis por disparo (escopeta = chumbos). */
    public final int pellets;
    /** Automática: segurar o botão continua atirando. */
    public final boolean automatic;
    /** Quantos alvos a bala atravessa. */
    public final int pierce;
    /** Rank do Caminho do Gatilho que libera a arma (0 = kit). */
    public final int unlockRank;
    /** Fração do Foco máximo gasta por cartucho na recarga. */
    public final float reloadCostPerRound;

    GunKind(String id, int rounds, float damage, int interval, int reloadTicks, float spread, float speed, int pellets,
            boolean automatic, int pierce, int unlockRank, float reloadCostPerRound) {
        this.id = id;
        this.rounds = rounds;
        this.damage = damage;
        this.interval = interval;
        this.reloadTicks = reloadTicks;
        this.spread = spread;
        this.speed = speed;
        this.pellets = pellets;
        this.automatic = automatic;
        this.pierce = pierce;
        this.unlockRank = unlockRank;
        this.reloadCostPerRound = reloadCostPerRound;
    }

    /** Id do item (o revólver de tier usa {@code mana_revolver_t<N>}). */
    public String itemId() {
        return "mana_" + id;
    }

    /** Prefixo das chaves de tradução das armas ({@code gun.isekai_mahou.gunslinger.<id>} e {@code .desc}). */
    private static final String KEY = "gun.isekai_mahou.gunslinger.";

    public MutableComponent displayName() {
        return Component.translatable(KEY + id);
    }

    public MutableComponent description() {
        return Component.translatable(KEY + id + ".desc");
    }

    @Nullable
    public static GunKind byId(String id) {
        for (GunKind k : values()) {
            if (k.id.equals(id)) return k;
        }
        return null;
    }

    @Nullable
    public static GunKind byOrdinal(int i) {
        GunKind[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
