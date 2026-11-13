package com.renanpontes.isekaimahou.feature.vehicles;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Os veículos mágicos (v1.0; onda 10 — "melhore os veículos, pra que sejam vendidos na cidade por um mecânico").
 * Cada um é bom para um terreno; o Mecânico ({@link VehicleMechanic}) vende pelo nível da Oficina Mecânica:
 * <ul>
 *   <li>nível 1: <b>Barco Arcano</b> (água, 2 lugares) e <b>Carroça-Golem</b> (terra, 2 lugares, baú grande);</li>
 *   <li>nível 2: <b>Tapete Voador</b> (plana baixo acompanhando o relevo, 2 lugares);</li>
 *   <li>nível 3: <b>Vassoura Arcana</b> (voo livre e rápido, 1 lugar) e <b>Triciclo de Mana</b> (terra, rápido);</li>
 *   <li>nível 4: <b>Dirigível Arcano</b> (voo em grupo, 3 lugares) e <b>Lancha das Marés</b> (água, 4 lugares, porão);</li>
 *   <li>nível 5: <b>Galeão Celeste</b> (voo, 5 lugares e porão grande — para as cidades no céu).</li>
 * </ul>
 * Os números ficam todos aqui para o balanço ser fácil de mexer. O id é salvo (entidade e item): só ACRESCENTE tipos.
 */
public enum VehicleKind {
    //                                  lugares vel.  acel. sobe  giro  mana/s carga/s baú  larg. alt.  vida preço oficina
    BROOM("arcane_broom", Mode.FLY, 1, 0.80, 0.10, 0.40, 14F, 8.0F, 2, 0, 0.8F, 0.5F, 24, 72, 3,
            List.of(new Vec3(0.0, 0.5, -0.05))),
    CARPET("flying_carpet", Mode.HOVER, 2, 0.50, 0.08, 0.22, 10F, 4.0F, 1, 0, 1.6F, 0.3F, 30, 45, 2,
            List.of(new Vec3(0.0, 0.22, 0.35), new Vec3(0.0, 0.22, -0.55))),
    BOAT("arcane_boat", Mode.WATER, 2, 0.90, 0.06, 0.0, 8F, 3.0F, 1, 0, 1.5F, 0.6F, 40, 22, 1,
            List.of(new Vec3(0.0, 0.3, 0.25), new Vec3(0.0, 0.3, -0.65))),
    CART("golem_cart", Mode.GROUND, 2, 0.34, 0.12, 0.0, 9F, 2.5F, 1, 27, 1.5F, 1.4F, 60, 28, 1,
            List.of(new Vec3(-0.3, 1.2, 0.35), new Vec3(0.3, 1.2, 0.35))),
    AIRSHIP("mini_airship", Mode.FLY, 3, 0.38, 0.035, 0.20, 5F, 5.0F, 2, 9, 1.8F, 1.2F, 70, 110, 4,
            List.of(new Vec3(0.0, 0.5, 0.7), new Vec3(-0.35, 0.5, -0.35), new Vec3(0.35, 0.5, -0.35))),
    // onda 10: um de cada terreno, mais caros, para Oficinas de nível alto
    ROVER("mana_rover", Mode.GROUND, 2, 0.62, 0.09, 0.0, 10F, 4.0F, 1, 9, 1.4F, 1.0F, 50, 64, 3,
            List.of(new Vec3(0.0, 0.62, 0.1), new Vec3(0.0, 0.8, -0.6))),
    SKIFF("tide_skiff", Mode.WATER, 4, 0.95, 0.05, 0.0, 7F, 4.0F, 1, 18, 2.0F, 0.7F, 80, 90, 4,
            List.of(new Vec3(0.0, 0.35, 0.75), new Vec3(-0.45, 0.35, 0.0), new Vec3(0.45, 0.35, 0.0), new Vec3(0.0, 0.35, -0.75))),
    GALLEON("sky_galleon", Mode.FLY, 5, 0.55, 0.03, 0.22, 4F, 6.0F, 2, 27, 2.6F, 1.4F, 140, 128, 5,
            List.of(new Vec3(0.0, 0.6, 1.25), new Vec3(-0.6, 0.6, 0.25), new Vec3(0.6, 0.6, 0.25), new Vec3(-0.6, 0.6, -0.75),
                    new Vec3(0.6, 0.6, -0.75)));

    /** Como o veículo se move. */
    public enum Mode {
        /** Voa livre (sobe/desce pelas teclas; a vassoura também segue a inclinação do olhar). */
        FLY,
        /** Paira baixo: acompanha o relevo e não passa de {@link VehicleKind#HOVER_MAX} blocos do chão. */
        HOVER,
        /** Flutua e corre na água; em terra quase não anda. */
        WATER,
        /** Anda por terra com gravidade, subindo degraus. */
        GROUND
    }

    /** Altura máxima do Tapete sobre o chão/água. */
    public static final double HOVER_MAX = 10.0;

    public final String id;
    public final Mode mode;
    public final int seats;
    /** Velocidade máxima (blocos por tick). */
    public final double speed;
    public final double accel;
    /** Velocidade de subida/descida pelas teclas. */
    public final double climb;
    /** Graus por tick (no máximo) que o veículo gira para acompanhar o olhar. */
    public final float turnRate;
    /** Mana do piloto por segundo em uso. */
    public final float manaPerSecond;
    /** Carga do tanque (cristais) por segundo em uso. */
    public final int burnPerSecond;
    /** Espaços do baú sem melhoria (0 = sem carga; o Porão Expandido soma 9 por nível). */
    public final int cargo;
    public final float width;
    public final float height;
    /** Vida sem Blindagem (onda 10). */
    public final int maxHealth;
    /** Preço no Mecânico, em moedas (até 128: duas pilhas na tela de troca). Também baseia conserto e resgate. */
    public final int price;
    /** Nível da Oficina Mecânica que começa a vender este veículo. */
    public final int shopLevel;
    /** Assentos: x = para a esquerda, y = altura do assento, z = para a frente. O primeiro é o do piloto. */
    public final List<Vec3> seatOffsets;

    VehicleKind(String id, Mode mode, int seats, double speed, double accel, double climb, float turnRate, float manaPerSecond,
                int burnPerSecond, int cargo, float width, float height, int maxHealth, int price, int shopLevel, List<Vec3> seatOffsets) {
        this.id = id;
        this.mode = mode;
        this.seats = seats;
        this.speed = speed;
        this.accel = accel;
        this.climb = climb;
        this.turnRate = turnRate;
        this.manaPerSecond = manaPerSecond;
        this.burnPerSecond = burnPerSecond;
        this.cargo = cargo;
        this.width = width;
        this.height = height;
        this.maxHealth = maxHealth;
        this.price = price;
        this.shopLevel = shopLevel;
        this.seatOffsets = seatOffsets;
    }

    public boolean flies() {
        return mode == Mode.FLY || mode == Mode.HOVER;
    }

    public String modeId() {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    /** Espaços de carga com {@code storage} níveis de Porão Expandido (até 54 = baú duplo). */
    public int cargoSlots(int storage) {
        return Math.min(54, cargo + 9 * Math.max(0, storage));
    }

    @Nullable
    public static VehicleKind byId(String id) {
        for (VehicleKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }

    /** O tipo de veículo de um tipo de entidade (todos os tipos usam a mesma classe). */
    public static VehicleKind of(EntityType<?> type) {
        VehicleKind k = byId(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
        return k != null ? k : BROOM;
    }
}
