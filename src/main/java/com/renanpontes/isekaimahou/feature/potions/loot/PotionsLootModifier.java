package com.renanpontes.isekaimahou.feature.potions.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.potions.PotionForm;
import com.renanpontes.isekaimahou.feature.potions.PotionKind;
import com.renanpontes.isekaimahou.feature.potions.PotionTier;
import com.renanpontes.isekaimahou.feature.potions.PotionsContent;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Modificador de loot global (data/isekai_mahou/loot_modifiers/potions_loot.json):
 * <ul>
 *   <li><b>Baús</b> ({@code chests/...}): do mod {@code mod_chest_chance}, de outros {@code other_chest_chance}; tier pela
 *   dimensão (dimensões do mod e Nether/End dão tiers altos).</li>
 *   <li><b>Chefes</b> mortos por jogador ({@code c:bosses}, chefe de provação ou de arena): {@code boss_drops} frascos
 *   (+0–1), tier pela vida máxima do chefe; os mais fortes podem soltar o Elixir Lendário.</li>
 * </ul>
 */
public class PotionsLootModifier extends LootModifier {
    public static final MapCodec<PotionsLootModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            IGlobalLootModifier.LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(m -> m.conditions),
            Codec.floatRange(0F, 1F).optionalFieldOf("mod_chest_chance", 0.45F).forGetter(m -> m.modChestChance),
            Codec.floatRange(0F, 1F).optionalFieldOf("other_chest_chance", 0.2F).forGetter(m -> m.otherChestChance),
            Codec.intRange(0, 8).optionalFieldOf("boss_drops", 2).forGetter(m -> m.bossDrops)
    ).apply(i, PotionsLootModifier::new));

    // Pesos por tier: LESSER, COMMON, GREATER, SUPERIOR, SUPREME, LEGENDARY
    private static final int[] OVERWORLD_OTHER = {60, 34, 6, 0, 0, 0};
    private static final int[] OVERWORLD_MOD = {28, 40, 22, 9, 1, 0};
    private static final int[] NETHER_END = {5, 30, 40, 20, 5, 0};
    private static final int[] MOD_DIMENSION = {0, 10, 35, 35, 17, 3};
    private static final int[] BOSS_WEAK = {0, 0, 50, 45, 5, 0};
    private static final int[] BOSS_MID = {0, 0, 10, 55, 35, 0};
    private static final int[] BOSS_STRONG = {0, 0, 0, 25, 55, 20};

    private final float modChestChance;
    private final float otherChestChance;
    private final int bossDrops;

    public PotionsLootModifier(LootItemCondition[] conditions, float modChestChance, float otherChestChance, int bossDrops) {
        super(conditions);
        this.modChestChance = modChestChance;
        this.otherChestChance = otherChestChance;
        this.bossDrops = bossDrops;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        ResourceLocation id = ctx.getQueriedLootTableId();
        String path = id.getPath();
        RandomSource r = ctx.getRandom();
        if (path.startsWith("chests/")) {
            boolean mod = IsekaiMahou.MODID.equals(id.getNamespace());
            if (r.nextFloat() >= (mod ? modChestChance : otherChestChance)) return loot;
            int[] weights = chestWeights(ctx.getLevel().dimension(), mod);
            int n = 1 + (r.nextFloat() < 0.35F ? 1 : 0);
            for (int k = 0; k < n; k++) loot.add(roll(r, weights, 0.18F, 0.12F));
        } else if (path.startsWith("entities/")) {
            Entity e = ctx.getParamOrNull(LootContextParams.THIS_ENTITY);
            if (!(e instanceof LivingEntity boss) || e instanceof Player || !isBoss(boss)) return loot;
            if (!ctx.hasParam(LootContextParams.LAST_DAMAGE_PLAYER)) return loot;
            float hp = boss.getMaxHealth();
            int[] weights = hp >= 1200 ? BOSS_STRONG : hp >= 400 ? BOSS_MID : BOSS_WEAK;
            int n = bossDrops + r.nextInt(2);
            for (int k = 0; k < n; k++) {
                // metade vida, metade mana — alternando para o grupo sempre levar dos dois
                PotionKind kind = k % 2 == 0 ? PotionKind.HEALTH : PotionKind.MANA;
                loot.add(stack(kind, form(r, 0.25F, 0.1F), pick(r, weights)));
            }
        }
        return loot;
    }

    private static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES) || BossKit.isTrialBoss(e) || BossArena.isArenaBoss(e);
    }

    private static int[] chestWeights(ResourceKey<Level> dim, boolean modChest) {
        if (IsekaiMahou.MODID.equals(dim.location().getNamespace())) return MOD_DIMENSION;
        if (dim == Level.NETHER || dim == Level.END) return NETHER_END;
        return modChest ? OVERWORLD_MOD : OVERWORLD_OTHER;
    }

    private static ItemStack roll(RandomSource r, int[] weights, float splash, float lng) {
        PotionKind kind = r.nextFloat() < 0.55F ? PotionKind.HEALTH : PotionKind.MANA;
        return stack(kind, form(r, splash, lng), pick(r, weights));
    }

    private static PotionForm form(RandomSource r, float splash, float lng) {
        float f = r.nextFloat();
        return f < splash ? PotionForm.SPLASH : f < splash + lng ? PotionForm.LONG : PotionForm.DRINK;
    }

    private static PotionTier pick(RandomSource r, int[] weights) {
        int total = 0;
        for (int w : weights) total += w;
        int x = r.nextInt(Math.max(1, total));
        PotionTier[] tiers = PotionTier.values();
        for (int t = 0; t < tiers.length; t++) {
            x -= weights[t];
            if (x < 0) return tiers[t];
        }
        return PotionTier.LESSER;
    }

    private static ItemStack stack(PotionKind kind, PotionForm form, PotionTier tier) {
        return new ItemStack(PotionsContent.item(kind, form, tier).get());
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return PotionsContent.POTIONS_LOOT.get();
    }
}
