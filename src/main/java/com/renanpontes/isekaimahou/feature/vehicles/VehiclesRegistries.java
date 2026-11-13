package com.renanpontes.isekaimahou.feature.vehicles;

import java.util.EnumMap;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.vehicles.entity.MagicVehicle;
import com.renanpontes.isekaimahou.feature.vehicles.item.VehiclePartItem;
import com.renanpontes.isekaimahou.feature.vehicles.item.VehicleItem;
import com.renanpontes.isekaimahou.feature.vehicles.item.VehicleRepairKitItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros do módulo vehicles: uma entidade e um item por {@link VehicleKind} (mesmo id nos dois); onda 10: as peças
 * de melhoria ({@link VehicleUpgrades.Part}) e o Kit de Reparos, vendidos pelo Mecânico.
 */
public final class VehiclesRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    private static final Map<VehicleKind, DeferredHolder<EntityType<?>, EntityType<MagicVehicle>>> TYPES = new EnumMap<>(VehicleKind.class);
    private static final Map<VehicleKind, DeferredItem<VehicleItem>> VEHICLE_ITEMS = new EnumMap<>(VehicleKind.class);
    private static final Map<VehicleUpgrades.Part, DeferredItem<VehiclePartItem>> PARTS = new EnumMap<>(VehicleUpgrades.Part.class);

    /** Kit de Reparos: devolve 40% da vida de um veículo (clique nele). */
    public static final DeferredItem<VehicleRepairKitItem> REPAIR_KIT = ITEMS.register("vehicle_repair_kit",
            () -> new VehicleRepairKitItem(new Item.Properties().stacksTo(16)));

    static {
        for (VehicleKind kind : VehicleKind.values()) {
            TYPES.put(kind, ENTITIES.register(kind.id, () -> EntityType.Builder.<MagicVehicle>of(MagicVehicle::new, MobCategory.MISC)
                    .sized(kind.width, kind.height).eyeHeight(kind.height * 0.5F).clientTrackingRange(10).build(kind.id)));
            VEHICLE_ITEMS.put(kind, ITEMS.register(kind.id, () -> new VehicleItem(kind, new Item.Properties().stacksTo(1)
                    .rarity(kind.shopLevel >= 5 ? Rarity.EPIC : kind.shopLevel >= 3 ? Rarity.RARE : Rarity.UNCOMMON))));
        }
        for (VehicleUpgrades.Part part : VehicleUpgrades.Part.values()) {
            PARTS.put(part, ITEMS.register(part.itemId(), () -> new VehiclePartItem(part, new Item.Properties().stacksTo(16)
                    .rarity(Rarity.UNCOMMON))));
        }
    }

    public static EntityType<MagicVehicle> type(VehicleKind kind) {
        return TYPES.get(kind).get();
    }

    public static VehicleItem item(VehicleKind kind) {
        return VEHICLE_ITEMS.get(kind).get();
    }

    public static VehiclePartItem part(VehicleUpgrades.Part part) {
        return PARTS.get(part).get();
    }

    /** Item do veículo com a carga do tanque guardada (veículo novo: inteiro e sem melhorias). */
    public static ItemStack stackWithFuel(VehicleKind kind, int fuel) {
        return VehicleStacks.withFuel(new ItemStack(item(kind)), fuel);
    }

    public static int fuelOf(ItemStack stack) {
        return VehicleStacks.read(stack).fuel();
    }

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    private VehiclesRegistries() {}
}
