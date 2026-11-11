package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Cutscenes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Ritual de Convergência: funde dois elementos base em que o jogador chegou ao nó "Mestre".
 * Custo: 8 Essências de cada elemento + 1 Bloco de Cristal de Mana (ou 8 Cristais de Mana se o bloco não existir).
 * Crepúsculo (Luz + Sombra) também exige ter derrotado o Soberano do Abismo e o Serafim.
 *
 * {@link #requirements} roda nos dois lados (a tela mostra a mesma lista que o servidor valida).
 */
public final class Convergence {
    public static final int ESSENCE_COST = 8;
    public static final int CRYSTAL_FALLBACK = 8;
    public static final String MASTER_NODE = "master";

    public record Req(Component label, boolean met) {}

    @Nullable
    public static Element fusion(@Nullable Element a, @Nullable Element b) {
        if (a == null || b == null || a == b || !a.isBase() || !b.isBase()) return null;
        return Element.fusionOf(a, b);
    }

    @Nullable
    public static Item crystalBlockItem() {
        return BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id("mana_crystal_block")).orElse(null);
    }

    public static Item essence(Element e) {
        return CoreRegistries.ESSENCES.get(e).get();
    }

    public static List<Req> requirements(Player player, MagicData d, Element a, Element b) {
        List<Req> out = new ArrayList<>();
        Element fusion = fusion(a, b);
        if (fusion == null) return out;
        boolean free = player.getAbilities().instabuild;
        out.add(new Req(Component.translatable("convergence.isekai_mahou.req.place", DimKind.ELEMENTAL.displayName()),
                player.level().dimension().equals(DimKind.ELEMENTAL.key)));
        if (d.isAwakened(fusion)) {
            out.add(new Req(Component.translatable("convergence.isekai_mahou.req.new", fusion.displayName()), false));
        }
        out.add(new Req(Component.translatable("convergence.isekai_mahou.req.master", a.displayName()), d.hasNode(a, MASTER_NODE)));
        out.add(new Req(Component.translatable("convergence.isekai_mahou.req.master", b.displayName()), d.hasNode(b, MASTER_NODE)));
        if (fusion == Element.TWILIGHT) {
            out.add(new Req(Component.translatable("message.isekai_mahou.dimensions.req_boss", DimTravel.bossName(DimTravel.BOSS_ABYSS)),
                    d.counter("boss." + DimTravel.BOSS_ABYSS) >= 1));
            out.add(new Req(Component.translatable("message.isekai_mahou.dimensions.req_boss", DimTravel.bossName(DimTravel.BOSS_SERAPH)),
                    d.counter("boss." + DimTravel.BOSS_SERAPH) >= 1));
        }
        out.add(items(player, essence(a), ESSENCE_COST, free));
        out.add(items(player, essence(b), ESSENCE_COST, free));
        Item block = crystalBlockItem();
        out.add(block != null ? items(player, block, 1, free) : items(player, CoreRegistries.MANA_CRYSTAL.get(), CRYSTAL_FALLBACK, free));
        return out;
    }

    private static Req items(Player player, Item item, int need, boolean free) {
        int have = player.getInventory().countItem(item);
        Component name = new ItemStack(item).getHoverName();
        return new Req(Component.translatable("convergence.isekai_mahou.req.items", need, name, Math.min(have, need), need), free || have >= need);
    }

    /** Executa o ritual (servidor). Valida tudo de novo: o cliente só pede. */
    public static void perform(ServerPlayer player, BlockPos pos, @Nullable Element a, @Nullable Element b) {
        Element fusion = fusion(a, b);
        if (fusion == null) return;
        ServerLevel level = player.serverLevel();
        if (!level.getBlockState(pos).is(DimRegistry.CONVERGENCE_ALTAR.get()) || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0) return;
        MagicData d = MagicApi.get(player);
        for (Req r : requirements(player, d, a, b)) {
            if (!r.met()) {
                player.sendSystemMessage(Component.translatable("message.isekai_mahou.dimensions.convergence_failed", r.label())
                        .withStyle(ChatFormatting.RED));
                level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 0.8F);
                return;
            }
        }
        if (!player.getAbilities().instabuild) {
            Inventory inv = player.getInventory();
            remove(inv, essence(a), ESSENCE_COST);
            remove(inv, essence(b), ESSENCE_COST);
            Item block = crystalBlockItem();
            if (block != null) remove(inv, block, 1);
            else remove(inv, CoreRegistries.MANA_CRYSTAL.get(), CRYSTAL_FALLBACK);
        }
        MagicApi.awakenElement(player, fusion);
        d.setFlag("fusion." + fusion.id, true);
        ritualFx(level, pos, a, b, fusion);
        Cutscenes.playNear(player, 32, "ascension", fusion.displayName().withStyle(ChatFormatting.BOLD),
                Component.translatable("cutscene.isekai_mahou.dimensions.convergence", a.displayName(), b.displayName()),
                fusion.color, 70, player);
    }

    private static void remove(Inventory inv, Item item, int amount) {
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int n = Math.min(amount, s.getCount());
                s.shrink(n);
                amount -= n;
            }
        }
        inv.setChanged();
    }

    /** Duas espirais com as cores dos pais descem e se fundem no altar; explosão e anel com a cor da fusão. */
    private static void ritualFx(ServerLevel level, BlockPos pos, Element a, Element b, Element fusion) {
        Vec3 top = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 0.7F);
        DimTasks.run(70, t -> {
            if (t < 50) {
                double f = 1.0 - t / 50.0;
                double r = 0.5 + 2.6 * f;
                double y = top.y + 0.2 + 2.2 * f;
                for (int k = 0; k < 2; k++) {
                    Element e = k == 0 ? a : b;
                    double ang = t * 0.35 + Math.PI * k;
                    double x = top.x + Math.cos(ang) * r;
                    double z = top.z + Math.sin(ang) * r;
                    level.sendParticles(ElementFx.dust(e, 1.5F), x, y, z, 2, 0.05, 0.05, 0.05, 0.0);
                    if (t % 4 == 0) level.sendParticles(ElementFx.signature(e), x, y, z, 1, 0.02, 0.02, 0.02, 0.01);
                }
                if (t % 10 == 0) level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.6F + t / 50F);
            } else if (t == 50) {
                ElementFx.burst(level, top.add(0, 0.4, 0), fusion, 90, 0.7);
                level.sendParticles(ParticleTypes.FLASH, top.x, top.y + 0.5, top.z, 1, 0.0, 0.0, 0.0, 0.0);
                level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.4F, 1.2F);
                level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 0.6F, 1.3F);
            } else {
                ElementFx.ring(level, new Vec3(top.x, pos.getY() + 0.1, top.z), fusion, 1.0 + (t - 50) * 0.35, 28);
                level.sendParticles(ElementFx.dust(fusion, 1.2F), top.x, top.y + 1.0 + (t - 50) * 0.3, top.z, 4, 0.1, 0.1, 0.1, 0.0);
            }
        });
    }

    private Convergence() {}
}
