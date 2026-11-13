package com.renanpontes.isekaimahou.feature.auto_glyphs.glyph;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.auto_glyphs.Delivery;
import com.renanpontes.isekaimahou.feature.auto_glyphs.WorkMana;
import com.renanpontes.isekaimahou.feature.auto_glyphs.WorkProbe;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.Pedestals;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.glyph.AreaEffect;
import com.renanpontes.isekaimahou.feature.automation.glyph.AutoArea;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Pesca Arcana (Arcano t2): pesca na água parada da área com a tabela de pesca do Minecraft (peixes e lixo; tesouros
 * só pescando de verdade). Precisa de pelo menos {@value #MIN_WATER} blocos de fonte de água na área.
 * <ul>
 *   <li>1 tentativa por conjuração (+1 por Amplificar, até 3). Chance de fisgar = água/24 (até 90%).</li>
 *   <li>Cada lago só dá peixe a cada 4 s (mais rápido que isso, o peixe se assusta).</li>
 *   <li>Custo: a 1ª fisgada vem com a magia; as outras custam 4 de mana cada. XP 1–3 por peixe.</li>
 *   <li>Fortuna: Sorte do Mar III. Drops: Coletor Arcano → baús do pedestal → topo (na mão: seu inventário).</li>
 * </ul>
 */
public class FishingGlyph extends AreaEffect implements WorkProbe {
    public static final String ID = "arcane_fishing";
    public static final float BASE_RADIUS = 3.0F;
    public static final int MIN_WATER = 6;
    private static final float CATCH_POINTS = 4F;
    private static final long POND_COOLDOWN = 80;

    /** Próximo tick em que cada lago pode dar peixe ("dimensão|posição do centro"). */
    private static final Map<String, Long> NEXT = new HashMap<>();

    public FishingGlyph() {
        super(ID, 6);
        element(Element.ARCANE);
        tier(2);
    }

    @Override
    public float hintRadius() {
        return BASE_RADIUS;
    }

    @Override
    protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
        ServerLevel level = ctx.level();
        int r = AutoArea.radius(stats, BASE_RADIUS);
        int water = countWater(level, center, r, 64);
        if (water < MIN_WATER) {
            if (AutoArea.firstThisTick(ctx, "fishing_dry")) {
                AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.auto_glyphs.fishing_no_water", MIN_WATER)
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        long now = level.getGameTime();
        String key = level.dimension().location() + "|" + center.asLong();
        Long next = NEXT.get(key);
        if (next != null && now < next) {
            level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y + 0.2, at.z, 4, 0.4, 0.1, 0.4, 0.01);
            return;
        }
        NEXT.put(key, now + POND_COOLDOWN);
        if (NEXT.size() > 512) prune(now);

        RunicPedestalBlockEntity pedestal = Pedestals.of(ctx, at);
        WorkMana mana = WorkMana.open(ctx, pedestal, CATCH_POINTS);
        Delivery out = Delivery.open(ctx, pedestal, at);
        int rolls = Mth.clamp(1 + Math.max(0, stats.amplify), 1, 3);
        float chance = Math.min(0.9F, water / 24F) * Math.min(1.25F, ctx.powerMult());
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        if (stats.fortune) {
            rod.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LUCK_OF_THE_SEA), 3);
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        Vec3 pond = Vec3.atCenterOf(center).add(0, 0.5, 0);
        int caught = 0, xp = 0;
        for (int i = 0; i < rolls; i++) {
            if (level.random.nextFloat() >= chance) continue;
            if (!mana.pay(CATCH_POINTS)) break;
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, pond)
                    .withParameter(LootContextParams.TOOL, rod)
                    .withLuck(stats.fortune ? 3F : 0F)
                    .create(LootContextParamSets.FISHING);
            List<ItemStack> loot = table.getRandomItems(params);
            out.give(loot);
            xp += 1 + level.random.nextInt(3);
            caught++;
        }
        mana.commit();
        out.xp(xp);
        level.sendParticles(ParticleTypes.SPLASH, pond.x, pond.y, pond.z, 12 + caught * 6, r * 0.4, 0.1, r * 0.4, 0.1);
        level.sendParticles(ParticleTypes.FISHING, pond.x, pond.y, pond.z, 6, r * 0.4, 0.05, r * 0.4, 0.02);
        if (caught > 0) {
            AutoFx.ring(level, pond, r * 0.7, ElementFx.dust(Element.WATER, 0.9F), 10 + r * 4);
            AutoFx.sound(level, pond, SoundEvents.FISHING_BOBBER_SPLASH, 0.8F, 1.0F);
            out.trail(ctx, pond);
        } else {
            AutoFx.sound(level, pond, SoundEvents.FISHING_BOBBER_THROW, 0.5F, 1.2F);
        }
    }

    /** Conta fontes de água na área (para quando chegar a {@code enough}). */
    static int countWater(ServerLevel level, BlockPos center, int r, int enough) {
        int n = 0;
        for (BlockPos p : AutoArea.disk(center, r, 2, 1)) {
            if (!level.isLoaded(p)) continue;
            if (level.getFluidState(p).isSourceOfType(Fluids.WATER) && ++n >= enough) return n;
        }
        return n;
    }

    private static void prune(long now) {
        Iterator<Map.Entry<String, Long>> it = NEXT.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() < now) it.remove();
    }

    /** Limpa os tempos dos lagos (servidor parou). */
    public static void clear() {
        NEXT.clear();
    }

    @Override
    public boolean hasWork(ServerLevel level, BlockPos center, @Nullable BlockPos pedestal, SpellStats stats) {
        return countWater(level, center, AutoArea.radius(stats, BASE_RADIUS), MIN_WATER) >= MIN_WATER;
    }
}
