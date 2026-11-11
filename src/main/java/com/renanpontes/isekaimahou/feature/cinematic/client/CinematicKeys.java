package com.renanpontes.isekaimahou.feature.cinematic.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.client.CoreKeys;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Teclas do módulo (Opções > Controles > Isekai Mahou). */
public final class CinematicKeys {
    /** Pula a cutscene atual (padrão: Enter). */
    public static final KeyMapping SKIP = new KeyMapping("key.isekai_mahou.skip_cutscene", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, CoreKeys.CATEGORY);

    private CinematicKeys() {}
}
