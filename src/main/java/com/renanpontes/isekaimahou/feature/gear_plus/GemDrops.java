package com.renanpontes.isekaimahou.feature.gear_plus;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * De onde vêm as gemas (survival):
 * <ul>
 *   <li><b>Minérios</b>: minerar um minério (sem Toque de Seda) tem uma chance pequena de soltar uma gema LASCADA do tipo
 *       que combina com o minério (redstone → rubi, lápis → safira, esmeralda → esmeralda, ouro → topázio, diamante →
 *       ametista, carvão → ônix; os outros, uma qualquer). As classes que encaixam gemas acham mais.</li>
 *   <li><b>Chefes</b>: todo chefe solta 1 gema LAPIDADA (e às vezes uma lascada).</li>
 *   <li><b>Monstros</b>: chance bem pequena de uma lascada.</li>
 * </ul>
 * As lapidações melhores saem na bancada: 3 lascadas → 1 lapidada; 3 lapidadas + 1 Estilhaço do Abismo → 1 perfeita.
 */
public final class GemDrops {
    public static final float ORE_CHANCE = 0.025F;
    public static final float ORE_CHANCE_CRAFTER = 0.06F;
    public static final float MOB_CHANCE = 0.005F;
    public static final float MOB_CHANCE_CRAFTER = 0.015F;

    static void register() {
        // LOWEST e sem cancelados: bloco protegido (cidade) que não quebrou não solta gema
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, GemDrops::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDropsEvent.class, GemDrops::onDrops);
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp) || sp instanceof FakePlayer || sp.isCreative()) return;
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        BlockState state = e.getState();
        if (!state.is(Tags.Blocks.ORES) || !sp.hasCorrectToolForDrops(state, level, e.getPos())) return;
        ItemStack tool = sp.getMainHandItem();
        var silk = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SILK_TOUCH);
        if (silk.isPresent() && EnchantmentHelper.getItemEnchantmentLevel(silk.get(), tool) > 0) return;
        boolean crafter = Gems.canUse(MagicApi.get(sp).path());
        RandomSource random = sp.getRandom();
        if (random.nextFloat() >= (crafter ? ORE_CHANCE_CRAFTER : ORE_CHANCE)) return;
        Block.popResource(level, e.getPos(), new ItemStack(GearRegistry.gem(typeFor(state, random), 1)));
        Hints.show(sp, "gear_plus.gem");
    }

    private static GemType typeFor(BlockState ore, RandomSource random) {
        if (ore.is(Tags.Blocks.ORES_REDSTONE)) return GemType.RUBY;
        if (ore.is(Tags.Blocks.ORES_LAPIS)) return GemType.SAPPHIRE;
        if (ore.is(Tags.Blocks.ORES_EMERALD)) return GemType.EMERALD;
        if (ore.is(Tags.Blocks.ORES_GOLD)) return GemType.TOPAZ;
        if (ore.is(Tags.Blocks.ORES_DIAMOND)) return GemType.AMETHYST;
        if (ore.is(Tags.Blocks.ORES_COAL)) return GemType.ONYX;
        return random(random);
    }

    private static GemType random(RandomSource random) {
        GemType[] all = GemType.values();
        return all[random.nextInt(all.length)];
    }

    private static void onDrops(LivingDropsEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player || !(dead.level() instanceof ServerLevel level)) return;
        RandomSource random = sp.getRandom();
        if (BossKit.isBossLike(dead)) {
            drop(e, level, dead, new ItemStack(GearRegistry.gem(random(random), 2)));
            if (random.nextBoolean()) drop(e, level, dead, new ItemStack(GearRegistry.gem(random(random), 1)));
            Hints.show(sp, "gear_plus.gem");
        } else if (dead instanceof Enemy) {
            boolean crafter = Gems.canUse(MagicApi.get(sp).path());
            if (random.nextFloat() < (crafter ? MOB_CHANCE_CRAFTER : MOB_CHANCE)) {
                drop(e, level, dead, new ItemStack(GearRegistry.gem(random(random), 1)));
                Hints.show(sp, "gear_plus.gem");
            }
        }
    }

    private static void drop(LivingDropsEvent e, ServerLevel level, LivingEntity dead, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, dead.getX(), dead.getY() + 0.3, dead.getZ(), stack);
        item.setDefaultPickUpDelay();
        e.getDrops().add(item);
    }

    private GemDrops() {}
}
