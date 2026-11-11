package com.renanpontes.isekaimahou.client.objectives;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Provedor embutido do núcleo: "Ascensão disponível" no limite do Círculo, com o XP guardado no banco. */
public final class CoreObjectives {
    public static final String ID = "isekai_mahou:core";
    public static final int PRIORITY = 600;
    private static final int GOLD = 0xFFD86B;

    public static void register() {
        ObjectiveTracker.register(ID, PRIORITY, CoreObjectives::lines);
    }

    private static List<ObjectiveLine> lines() {
        MagicData d = ClientMagic.data();
        if (d.path() == MagicPath.NONE || !d.atLevelCap() || d.circle() >= Progression.MAX_CIRCLE) return List.of();
        Component title = Component.translatable("objectives.isekai_mahou.ascension", d.circle() + 1);
        int bank = d.xpBank();
        if (bank > 0) {
            Component detail = Component.translatable("objectives.isekai_mahou.xp_banked", bank, d.bankCap());
            return List.of(ObjectiveLine.progress(title, detail, bank, d.bankCap(), GOLD, icon()));
        }
        return List.of(ObjectiveLine.of(title, Component.translatable("objectives.isekai_mahou.ascension_hint"), GOLD, icon()));
    }

    /** Núcleo de Ascensão (módulo ascension) se existir; senão um Cristal de Mana. */
    private static ItemStack icon() {
        var core = BuiltInRegistries.ITEM.get(IsekaiMahou.id("ascension_core"));
        if (core != Items.AIR) return new ItemStack(core);
        return new ItemStack(CoreRegistries.MANA_CRYSTAL.get());
    }

    private CoreObjectives() {}
}
