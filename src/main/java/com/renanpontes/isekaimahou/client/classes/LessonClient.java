package com.renanpontes.isekaimahou.client.classes;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.classes.ClassesRegistries;
import com.renanpontes.isekaimahou.core.item.TechniqueLessonItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Cliente das Lições: abre a tela de escrever e registra o modelo "escrita" do item. */
public final class LessonClient {
    /** Propriedade do modelo: 1 = Lição escrita (textura com o selo), 0 = em branco. */
    public static final ResourceLocation WRITTEN = IsekaiMahou.id("written");

    /** Chamado pelo pacote {@code OpenLessonWriter}. */
    public static void openWriter(int hand, List<ResourceLocation> glyphs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        InteractionHand h = hand == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        mc.setScreen(new LessonWriteScreen(h, glyphs));
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ClassesRegistries.TECHNIQUE_LESSON.get(), WRITTEN,
                (stack, level, entity, seed) -> TechniqueLessonItem.isWritten(stack) ? 1F : 0F));
    }

    private LessonClient() {}
}
