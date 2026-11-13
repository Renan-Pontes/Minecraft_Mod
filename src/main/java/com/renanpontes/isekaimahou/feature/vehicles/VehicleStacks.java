package com.renanpontes.isekaimahou.feature.vehicles;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.vehicles.entity.MagicVehicle;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Dados de um veículo GUARDADO no item (componente de dados personalizados). Tudo opcional: item antigo (só com a carga
 * do tanque) vira um veículo inteiro, sem melhorias, que ganha uma identidade nova ao ser colocado.
 * <ul>
 *   <li>{@code VehicleFuel} — carga do tanque (v1.0);</li>
 *   <li>{@code VehicleId} / {@code VehicleGen} — identidade do veículo e "geração" (o Mecânico resgata o veículo
 *       entregando um item novo de geração maior: a cópia antiga, onde estiver, some — ver {@link VehicleRegistry});</li>
 *   <li>{@code VehicleHp} — vida em fração (ausente = cheia);</li>
 *   <li>{@code VehicleUpg} — melhorias ({@link VehicleUpgrades}).</li>
 * </ul>
 * Não toca nos registros do módulo: os outros módulos (lojas) podem usar mesmo que o item venha pelo id.
 */
public final class VehicleStacks {
    static final String FUEL = "VehicleFuel", ID = "VehicleId", GEN = "VehicleGen", HP = "VehicleHp", UPG = "VehicleUpg";

    /** O que o item guarda. {@code hp} é a fração da vida (1 = cheia). */
    public record Data(int fuel, @Nullable UUID id, int gen, float hp, int upg) {
        public static final Data NEW = new Data(0, null, 0, 1F, 0);

        public Data withFuel(int f) {
            return new Data(f, id, gen, hp, upg);
        }
    }

    public static Data read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Data.NEW;
        CompoundTag t = data.copyTag();
        return new Data(Mth.clamp(t.getInt(FUEL), 0, MagicVehicle.MAX_FUEL), t.hasUUID(ID) ? t.getUUID(ID) : null, t.getInt(GEN),
                t.contains(HP) ? Mth.clamp(t.getFloat(HP), 0.01F, 1F) : 1F, t.getInt(UPG));
    }

    /** Grava os dados no item (mantém outros dados que o item já tenha). */
    public static ItemStack write(ItemStack stack, Data d) {
        CustomData old = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag t = old == null ? new CompoundTag() : old.copyTag();
        t.remove(FUEL);
        t.remove(ID);
        t.remove(GEN);
        t.remove(HP);
        t.remove(UPG);
        if (d.fuel() > 0) t.putInt(FUEL, Mth.clamp(d.fuel(), 0, MagicVehicle.MAX_FUEL));
        if (d.id() != null) t.putUUID(ID, d.id());
        if (d.gen() > 0) t.putInt(GEN, d.gen());
        if (d.hp() < 0.999F) t.putFloat(HP, Mth.clamp(d.hp(), 0.01F, 1F));
        if (d.upg() != 0) t.putInt(UPG, d.upg());
        if (t.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return stack;
    }

    /** Item de loja com o tanque já com alguma carga (o comprador sai andando mesmo sem mana sobrando). */
    public static ItemStack withFuel(ItemStack stack, int fuel) {
        if (stack.isEmpty()) return stack;
        return write(stack, read(stack).withFuel(fuel));
    }

    private VehicleStacks() {}
}
