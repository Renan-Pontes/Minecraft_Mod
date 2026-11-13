package com.renanpontes.isekaimahou.feature.gear_plus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.gear_plus.item.GemChiselItem;
import com.renanpontes.isekaimahou.feature.gear_plus.item.GemItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo: as 18 gemas (6 tipos × 3 lapidações), o Cinzel de Lapidário e os dados do jogador. */
public final class GearRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);

    /** Dados do jogador ({@link GearData}). Sobrevive à morte. */
    public static final Supplier<AttachmentType<CompoundTag>> DATA = ATTACHMENTS.register("gear_plus_data",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    /** Gemas por tipo: índice 0..2 = lapidação 1..3. */
    public static final Map<GemType, List<DeferredItem<GemItem>>> GEMS = new EnumMap<>(GemType.class);

    public static final DeferredItem<GemChiselItem> CHISEL = ITEMS.register("gem_chisel",
            () -> new GemChiselItem(new Item.Properties().durability(48)));

    static {
        for (GemType type : GemType.values()) {
            List<DeferredItem<GemItem>> list = new ArrayList<>();
            for (int grade = 1; grade <= 3; grade++) {
                int g = grade;
                Rarity rarity = g == 1 ? Rarity.COMMON : g == 2 ? Rarity.UNCOMMON : Rarity.RARE;
                list.add(ITEMS.register(type.itemId(g), () -> new GemItem(type, g, new Item.Properties().rarity(rarity))));
            }
            GEMS.put(type, list);
        }
    }

    public static GemItem gem(GemType type, int grade) {
        return GEMS.get(type).get(Math.max(1, Math.min(3, grade)) - 1).get();
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() != CoreRegistries.TAB_MAGIC.getKey()) return;
            e.accept(CHISEL.get());
            for (GemType type : GemType.values()) {
                for (DeferredItem<GemItem> gem : GEMS.get(type)) e.accept(gem.get());
            }
        });
    }

    private GearRegistry() {}
}
