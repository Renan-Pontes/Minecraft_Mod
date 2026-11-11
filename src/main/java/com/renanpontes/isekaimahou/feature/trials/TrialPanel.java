package com.renanpontes.isekaimahou.feature.trials;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Painéis de texto flutuante (entidade vanilla {@code text_display}) da recepção/arena. O texto é um Component
 * traduzível: cada cliente vê no próprio idioma. Os setters da entidade são privados, então tudo passa pelo NBT.
 */
public final class TrialPanel {

    /** Cria um painel que sempre vira para quem olha (eixo vertical), com fundo escuro translúcido e brilho total. */
    @Nullable
    public static UUID spawn(ServerLevel level, Vec3 pos, Component text, UUID instance, int lineWidth) {
        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        if (display == null) return null;
        CompoundTag tag = new CompoundTag();
        tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        tag.putString("billboard", "vertical");
        // sem "alignment" o vanilla registra erro de leitura no log a cada carga
        tag.putString("alignment", "center");
        tag.putInt("line_width", lineWidth);
        tag.putInt("background", 0xB0140C24);
        tag.putBoolean("shadow", true);
        tag.putFloat("view_range", 1.5F);
        CompoundTag light = new CompoundTag();
        light.putInt("block", 15);
        light.putInt("sky", 15);
        tag.put("brightness", light);
        display.load(tag);
        display.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        TrialInstance.tag(display, instance, TrialInstance.ROLE_DECOR);
        level.addFreshEntity(display);
        return display.getUUID();
    }

    /** Troca o texto de um painel existente (sem recriar a entidade). */
    public static void update(ServerLevel level, @Nullable UUID id, Component text) {
        if (id == null || !(level.getEntity(id) instanceof Display.TextDisplay display)) return;
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        display.load(tag);
    }

    public static void remove(ServerLevel level, @Nullable UUID id) {
        if (id == null) return;
        Entity e = level.getEntity(id);
        if (e != null) e.discard();
    }

    private TrialPanel() {}
}
