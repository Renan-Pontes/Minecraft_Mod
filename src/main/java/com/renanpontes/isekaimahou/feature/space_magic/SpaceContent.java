package com.renanpontes.isekaimahou.feature.space_magic;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.space_magic.entity.SpaceGateEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo: o Portal Espacial (entidade temporária) e os dados do jogador (Item Box, marca, últimos lugares). */
public final class SpaceContent {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,
            IsekaiMahou.MODID);

    /** Portal de ida e volta (some sozinho; não é salvo no mundo). */
    public static final DeferredHolder<EntityType<?>, EntityType<SpaceGateEntity>> SPACE_GATE = ENTITIES.register("space_gate",
            () -> EntityType.Builder.<SpaceGateEntity>of(SpaceGateEntity::new, MobCategory.MISC)
                    .sized(1.6F, 2.6F).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(10).build("space_gate"));

    /**
     * Dados do jogador que SOBREVIVEM à morte: o Inventário Dimensional (o "Item Box" não cai quando você morre),
     * a Marca de Retorno e o último chão firme em cada dimensão (destino do Abrir Portal sem hub).
     */
    public static final Supplier<AttachmentType<CompoundTag>> PLAYER_DATA = ATTACHMENTS.register("space_magic_player",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    private SpaceContent() {}
}
