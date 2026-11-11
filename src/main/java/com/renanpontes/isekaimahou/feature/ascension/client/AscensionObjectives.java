package com.renanpontes.isekaimahou.feature.ascension.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.objectives.CoreObjectives;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveLine;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.ascension.AscensionPattern;
import com.renanpontes.isekaimahou.feature.ascension.AscensionRegistry;
import com.renanpontes.isekaimahou.feature.ascension.AscensionTracking;
import com.renanpontes.isekaimahou.feature.ascension.AscensionTributes;
import com.renanpontes.isekaimahou.feature.ascension.block.AscensionPedestalBlockEntity;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Rastreador de objetivos da Ascensão. Usa o MESMO id do provedor embutido do núcleo
 * ({@link CoreObjectives#ID}) para substituí-lo por uma versão completa: no limite do Círculo mostra
 * "Ascensão → Círculo N" com o tributo (o que falta, somando inventário e pedestais do Núcleo seguido),
 * setinha para o último Núcleo de Ascensão visto e, v0.9, uma linha com o ALTAR PÚBLICO mais próximo (o servidor
 * informa: altar já conhecido numa cidade/santuário, ou o ponto da estrutura mais próxima que tem um) — e, se
 * houver, o XP guardado no banco.
 */
public final class AscensionObjectives {
    private static final int GOLD = 0xFFD86B;
    private static final int PUBLIC = 0xFFB347;
    private static final int BANK = 0x8FD8FF;

    public static void register() {
        ObjectiveTracker.register(CoreObjectives.ID, CoreObjectives.PRIORITY, AscensionObjectives::lines);
    }

    private static List<ObjectiveLine> lines() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        MagicData d = ClientMagic.data();
        if (player == null || level == null || d.path() == MagicPath.NONE || !d.atLevelCap() || d.circle() >= Progression.MAX_CIRCLE) {
            return List.of();
        }
        int next = d.circle() + 1;
        ItemStack icon = new ItemStack(AscensionRegistry.CORE_ITEM.get());
        Component title = Component.translatable("ascension.isekai_mahou.objective.title", next);
        BlockPos core = trackedCore(level);
        AscensionTracking.PublicAltar altar = AscensionTracking.publicAltar();
        if (altar != null && !altar.dim().equals(level.dimension())) altar = null;

        List<ObjectiveLine> out = new ArrayList<>();
        AscensionTributes.Tribute tribute = AscensionTributes.synced(next);
        ObjectiveLine main;
        if (tribute == null) {
            main = ObjectiveLine.of(title, Component.translatable("objectives.isekai_mahou.ascension_hint"), GOLD, icon);
        } else {
            List<ItemStack> items = new ArrayList<>(player.getInventory().items);
            items.addAll(player.getInventory().offhand);
            if (core != null) items.addAll(pedestalItems(level, core, next));
            AscensionTributes.Check check = AscensionTributes.check(tribute, items, d);
            Component detail;
            if (check.complete()) {
                if (core != null) detail = Component.translatable("ascension.isekai_mahou.objective.ready_core");
                else if (altar != null) detail = Component.translatable("ascension.isekai_mahou.objective.ready_public");
                else detail = Component.translatable("ascension.isekai_mahou.objective.ready");
            } else {
                AscensionTributes.Line missing = firstMissing(check);
                detail = Component.translatable("ascension.isekai_mahou.objective.missing", missing.label(), missing.have(), missing.need());
            }
            main = ObjectiveLine.progress(title, detail, check.satisfied(), check.lines().size(), GOLD, icon);
        }
        if (core != null) main = main.withTarget(core, AscensionTracking.dimension());
        out.add(main);

        // Altar público mais próximo (sem montar nada): seta própria, a menos que o Núcleo seguido seja ele mesmo.
        if (altar != null && (core == null || !core.equals(altar.pos()))) {
            double dx = altar.pos().getX() + 0.5 - player.getX(), dz = altar.pos().getZ() + 0.5 - player.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            Component where = Component.translatable(altar.exact() ? "ascension.isekai_mahou.objective.public_exact"
                    : "ascension.isekai_mahou.objective.public_approx", distance(dist));
            out.add(ObjectiveLine.of(altar.name(), where, PUBLIC, icon).withTarget(altar.pos(), altar.dim()));
        }

        int bank = d.xpBank();
        if (bank > 0) {
            out.add(ObjectiveLine.progress(Component.translatable("objectives.isekai_mahou.xp_banked", bank, d.bankCap()),
                    Component.translatable("ascension.isekai_mahou.objective.bank"), bank, d.bankCap(), BANK, ItemStack.EMPTY));
        }
        return out;
    }

    /** "85 m" / "1,2 km". */
    private static Component distance(int blocks) {
        if (blocks < 1000) return Component.translatable("ascension.isekai_mahou.objective.meters", blocks);
        int tenths = Math.round(blocks / 100F);
        return Component.translatable("ascension.isekai_mahou.objective.km", tenths / 10, tenths % 10);
    }

    /** Núcleo seguido nesta dimensão; esquece se o chunk está carregado e o bloco não é mais um Núcleo. */
    private static BlockPos trackedCore(ClientLevel level) {
        BlockPos core = AscensionTracking.core();
        if (core == null || AscensionTracking.dimension() != level.dimension()) return null;
        if (level.isLoaded(core) && !level.getBlockState(core).is(AscensionRegistry.CORE.get())) {
            AscensionTracking.forgetCore();
            return null;
        }
        return core;
    }

    private static List<ItemStack> pedestalItems(ClientLevel level, BlockPos core, int target) {
        List<ItemStack> out = new ArrayList<>();
        if (!level.isLoaded(core)) return out;
        for (BlockPos p : AscensionPattern.pedestals(core, target)) {
            if (level.getBlockEntity(p) instanceof AscensionPedestalBlockEntity ped && !ped.getItem().isEmpty()) out.add(ped.getItem());
        }
        return out;
    }

    private static AscensionTributes.Line firstMissing(AscensionTributes.Check check) {
        for (AscensionTributes.Line l : check.lines()) if (!l.ok()) return l;
        return check.lines().get(0);
    }

    private AscensionObjectives() {}
}
