package com.renanpontes.isekaimahou.feature.afterlife;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.afterlife.entity.CorpseEntity;
import com.renanpontes.isekaimahou.feature.afterlife.entity.ReaperEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo: o Corpo, o Ceifador e os dados do jogador (último chão seguro, recarga, alma pendente). */
public final class AfterlifeContent {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,
            IsekaiMahou.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CorpseEntity>> CORPSE = ENTITIES.register("afterlife_corpse",
            () -> EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
                    .sized(1.3F, 0.45F).fireImmune().clientTrackingRange(10).updateInterval(20).build("afterlife_corpse"));

    public static final DeferredHolder<EntityType<?>, EntityType<ReaperEntity>> REAPER = ENTITIES.register("afterlife_reaper",
            () -> EntityType.Builder.<ReaperEntity>of(ReaperEntity::new, MobCategory.MISC)
                    .sized(0.8F, 2.2F).fireImmune().noSave().clientTrackingRange(6).updateInterval(1).build("afterlife_reaper"));

    /** Dados do módulo no jogador (sobrevivem à morte): chão seguro, fim da recarga, marca de alma pendente. */
    public static final Supplier<AttachmentType<CompoundTag>> PLAYER_DATA = ATTACHMENTS.register("afterlife_player",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(CORPSE.get(), LivingEntity.createLivingAttributes().build()));
    }

    private AfterlifeContent() {}
}
