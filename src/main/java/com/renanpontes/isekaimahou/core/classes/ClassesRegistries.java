package com.renanpontes.isekaimahou.core.classes;

import static com.renanpontes.isekaimahou.IsekaiMahou.MODID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.item.LessonData;
import com.renanpontes.isekaimahou.core.item.TechniqueLessonItem;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do núcleo de classes v1.0: a Lição ({@code technique_lesson}) e o componente que guarda o que ela ensina. */
public final class ClassesRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    /** Lição escrita: glifo de origem + professor. Sem o componente, a Lição está em branco. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LessonData>> LESSON = COMPONENTS.registerComponentType("lesson",
            b -> b.persistent(LessonData.CODEC).networkSynchronized(LessonData.STREAM_CODEC));

    /** Lição (em branco: papel + tinta + essência; escrita: ensina um glifo de origem a quem ler). */
    public static final DeferredItem<TechniqueLessonItem> TECHNIQUE_LESSON = ITEMS.register("technique_lesson",
            () -> new TechniqueLessonItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, e -> {
            if (e.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) e.accept(TECHNIQUE_LESSON.get());
        });
    }

    private ClassesRegistries() {}
}
