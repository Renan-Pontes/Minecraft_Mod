package com.renanpontes.isekaimahou.feature.ui_hud;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Módulo "ui_hud": HUD completo e aba Status (Janela de Status) — a parte de cliente fica em {@code client/UiHudClient}.
 * <p>
 * v1.0 RELEASE: também as <b>dicas de primeira vez</b> ({@link Hints}): o servidor guarda quais cada jogador já viu
 * (attachment {@code ui_hud_hints}) e dispara as dos sistemas principais ({@link HintTriggers}); e recebe do cliente
 * as preferências da tela de opções que valem no servidor (Modo Paz automático, tutorial).
 */
public class UiHudModule implements Module {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);

    /** Dicas já vistas pelo jogador (chave = id da dica). Sobrevive à morte. */
    public static final Supplier<AttachmentType<CompoundTag>> HINTS = ATTACHMENTS.register("ui_hud_hints",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    @Override
    public String id() {
        return "ui_hud";
    }

    @Override
    public void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(HintPayloads::register);
        HintTriggers.init();
    }
}
