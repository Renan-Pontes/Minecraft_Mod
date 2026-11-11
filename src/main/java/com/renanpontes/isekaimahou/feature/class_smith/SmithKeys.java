package com.renanpontes.isekaimahou.feature.class_smith;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Prefixos das chaves de tradução montadas em tempo de execução (família, material, parte, tarefa...). Todas as chaves
 * finais estão em {@code src/main/lang/class_smith} (geradas por {@code tools/gen/class_smith_lang.py}).
 */
public final class SmithKeys {
    public static final String BLADE_TYPE = "blade.isekai_mahou.type.";
    public static final String BLADE_FAMILY = "blade.isekai_mahou.family.";
    public static final String BLADE_MATERIAL = "blade.isekai_mahou.material.";
    public static final String BLADE_GRIP = "blade.isekai_mahou.grip.";
    public static final String BLADE_FINISH = "blade.isekai_mahou.finish.";
    public static final String BLADE_PART = "blade.isekai_mahou.part.";
    public static final String BLADE_WEIGHT = "blade.isekai_mahou.weight.";
    public static final String TIP_FAMILY = "tooltip.isekai_mahou.class_smith.blade.family.";
    public static final String TIP_FINISH = "tooltip.isekai_mahou.class_smith.blade.finish.";
    public static final String MESSAGE = "message.isekai_mahou.class_smith.";
    public static final String OUTFIT_TIER = "outfit.isekai_mahou.smith.tier.";
    public static final String OUTFIT_FLAVOR = "outfit.isekai_mahou.smith.flavor.";
    public static final String TASK = "smith.isekai_mahou.task.";
    public static final String RANK = "smith.isekai_mahou.rank.";
    public static final String STATE = "screen.isekai_mahou.class_smith.state.";
    public static final String REWARD_ITEMS = "screen.isekai_mahou.class_smith.reward_items.";
    public static final String BONUS = "screen.isekai_mahou.class_smith.bonus.";

    /** {@code prefixo + sufixo}, com argumentos. */
    public static MutableComponent of(String prefix, Object suffix, Object... args) {
        return Component.translatable(prefix + suffix, args);
    }

    private SmithKeys() {}
}
