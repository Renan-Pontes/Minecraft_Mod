package com.renanpontes.isekaimahou.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Teclas do núcleo (Opções > Controles > Isekai Mahou). Módulos registram as suas próprias. */
public final class CoreKeys {
    public static final String CATEGORY = "key.categories.isekai_mahou";

    /** Z, X, C = magias 1-3; G = ultimate. */
    public static final KeyMapping[] SLOTS = {
            key("slot1", GLFW.GLFW_KEY_Z),
            key("slot2", GLFW.GLFW_KEY_X),
            key("slot3", GLFW.GLFW_KEY_C),
            key("ultimate", GLFW.GLFW_KEY_G),
    };
    public static final KeyMapping MENU = key("menu", GLFW.GLFW_KEY_B);
    /**
     * v1.0 — segurar + scroll = troca o LOADOUT (9 loadouts; padrão: Mouse 5). Faz o mesmo que {@link #LOADOUT_MODIFIER}.
     * Ver {@link LoadoutInput}.
     */
    public static final KeyMapping LOADOUT_MOUSE = new KeyMapping("key.isekai_mahou.loadout_mouse",
            KeyConflictContext.IN_GAME, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, CATEGORY);
    /** @deprecated v1.0 não tem Roda Elemental: é o mesmo objeto que {@link #LOADOUT_MOUSE} (Mouse 5 + scroll = loadout). */
    @Deprecated
    public static final KeyMapping WHEEL_MODIFIER = LOADOUT_MOUSE;
    /** Segurar + scroll = troca o LOADOUT; segurar + 1..9 = vai direto ao loadout (padrão: Alt esquerdo). */
    public static final KeyMapping LOADOUT_MODIFIER = key("loadout_modifier", GLFW.GLFW_KEY_LEFT_ALT);
    /** Mostra/esconde o rastreador de objetivos (padrão: J). */
    public static final KeyMapping TRACKER = key("tracker", GLFW.GLFW_KEY_J);
    /** Avança a caixa de diálogo do núcleo (padrão: Enter). Registrada pelo {@code OnboardCoreClient}. */
    public static final KeyMapping DIALOGUE_NEXT = key("dialogue_next", GLFW.GLFW_KEY_ENTER);

    private static KeyMapping key(String name, int glfw) {
        return new KeyMapping("key.isekai_mahou." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, glfw, CATEGORY);
    }

    private CoreKeys() {}
}
