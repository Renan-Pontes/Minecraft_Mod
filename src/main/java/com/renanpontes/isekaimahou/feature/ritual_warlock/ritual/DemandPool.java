package com.renanpontes.isekaimahou.feature.ritual_warlock.ritual;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Pool de tributos de cada Patrono. Quanto maior o nº do pacto (1..10), mais caro o tributo.
 * Mobs/itens de outros módulos (isekai_mahou:goblin...) só entram se existirem no registro.
 */
public final class DemandPool {
    /** Entrada do pool: alvo, faixa de pactos em que aparece, quantidade base e acréscimo por pacto. */
    private record Entry(String target, int minTier, int maxTier, int base, float perTier, int minutes) {
        int amount(int tier) {
            return Math.max(1, Math.min(64, base + Math.round(perTier * (tier - minTier))));
        }

        boolean fits(int tier) {
            return tier >= minTier && tier <= maxTier;
        }
    }

    private static final Map<PatronKind, List<Entry>> ITEMS = new EnumMap<>(PatronKind.class);
    private static final Map<PatronKind, List<Entry>> KILLS = new EnumMap<>(PatronKind.class);

    static {
        ITEMS.put(PatronKind.EMBER_LORD, List.of(
                item("minecraft:blaze_rod", 1, 10, 4, 1F),
                item("minecraft:coal_block", 1, 4, 2, 1F),
                item("minecraft:gold_ingot", 1, 6, 6, 2F),
                item("minecraft:magma_cream", 2, 8, 4, 1F),
                item("minecraft:quartz", 2, 7, 12, 4F),
                item("minecraft:diamond", 3, 10, 1, 0.5F),
                item("minecraft:ghast_tear", 5, 10, 1, 0.34F),
                item("minecraft:wither_skeleton_skull", 6, 10, 1, 0.25F),
                item("minecraft:netherite_scrap", 8, 10, 1, 0.5F),
                item("isekai_mahou:essence_fire", 2, 10, 2, 0.5F)));
        ITEMS.put(PatronKind.VOID_LADY, List.of(
                item("minecraft:ender_pearl", 1, 10, 4, 1F),
                item("minecraft:amethyst_shard", 1, 5, 8, 2F),
                item("minecraft:obsidian", 1, 5, 6, 2F),
                item("minecraft:phantom_membrane", 2, 8, 3, 0.5F),
                item("minecraft:ender_eye", 3, 9, 2, 0.5F),
                item("minecraft:chorus_fruit", 4, 9, 12, 2F),
                item("minecraft:diamond", 3, 10, 1, 0.5F),
                item("minecraft:echo_shard", 6, 10, 1, 0.5F),
                item("minecraft:wither_skeleton_skull", 7, 10, 1, 0.25F),
                item("minecraft:shulker_shell", 8, 10, 1, 0.5F),
                item("isekai_mahou:essence_shadow", 2, 10, 2, 0.5F)));
        ITEMS.put(PatronKind.DROWNED_KING, List.of(
                item("minecraft:cod", 1, 4, 8, 2F),
                item("minecraft:ink_sac", 1, 5, 8, 2F),
                item("minecraft:gold_ingot", 1, 6, 6, 2F),
                item("minecraft:prismarine_shard", 2, 9, 8, 2F),
                item("minecraft:prismarine_crystals", 3, 9, 6, 1F),
                item("minecraft:nautilus_shell", 3, 10, 1, 0.4F),
                item("minecraft:diamond", 4, 10, 1, 0.5F),
                item("minecraft:heart_of_the_sea", 8, 10, 1, 0F),
                item("minecraft:trident", 9, 10, 1, 0F),
                item("isekai_mahou:essence_water", 2, 10, 2, 0.5F)));

        KILLS.put(PatronKind.EMBER_LORD, List.of(
                kill("minecraft:zombie", 1, 4, 6, 1.5F, 10),
                kill("minecraft:skeleton", 1, 5, 5, 1F, 10),
                kill("minecraft:creeper", 2, 6, 3, 0.75F, 12),
                kill("minecraft:magma_cube", 3, 8, 5, 1F, 12),
                kill("minecraft:blaze", 4, 10, 4, 0.75F, 15),
                kill("minecraft:wither_skeleton", 6, 10, 3, 0.5F, 15),
                kill("minecraft:ghast", 7, 10, 2, 0.34F, 15),
                kill("isekai_mahou:goblin", 1, 5, 6, 1.5F, 10),
                kill("isekai_mahou:salamander", 2, 7, 3, 0.75F, 12),
                kill("isekai_mahou:ember_imp", 3, 8, 4, 1F, 12),
                kill("isekai_mahou:orc", 3, 8, 3, 0.75F, 12)));
        KILLS.put(PatronKind.VOID_LADY, List.of(
                kill("minecraft:spider", 1, 4, 5, 1.5F, 10),
                kill("minecraft:zombie", 1, 3, 6, 1.5F, 10),
                kill("minecraft:enderman", 2, 9, 2, 0.5F, 15),
                kill("minecraft:cave_spider", 2, 6, 4, 1F, 12),
                kill("minecraft:witch", 3, 8, 1, 0.34F, 15),
                kill("minecraft:shulker", 8, 10, 3, 0.5F, 15),
                kill("isekai_mahou:cultist", 2, 8, 3, 0.75F, 12),
                kill("isekai_mahou:creeping_shade", 2, 7, 3, 0.75F, 12),
                kill("isekai_mahou:void_bat", 3, 8, 4, 1F, 12),
                kill("isekai_mahou:werewolf", 5, 10, 2, 0.5F, 15)));
        KILLS.put(PatronKind.DROWNED_KING, List.of(
                kill("minecraft:zombie", 1, 3, 6, 1.5F, 10),
                kill("minecraft:squid", 1, 4, 4, 1F, 10),
                kill("minecraft:drowned", 1, 7, 4, 1F, 12),
                kill("minecraft:glow_squid", 2, 6, 3, 0.75F, 12),
                kill("minecraft:guardian", 5, 10, 3, 0.5F, 15),
                kill("isekai_mahou:lizardman", 3, 8, 3, 0.75F, 12),
                kill("isekai_mahou:undine", 3, 8, 3, 0.75F, 12),
                kill("isekai_mahou:tide_serpent", 6, 10, 1, 0.34F, 15)));
    }

    private static Entry item(String id, int min, int max, int base, float per) {
        return new Entry(id, min, max, base, per, 0);
    }

    private static Entry kill(String id, int min, int max, int base, float per, int minutes) {
        return new Entry(id, min, max, base, per, minutes);
    }

    /**
     * Sorteia o tributo.
     * @param pact  nº do pacto (escala a dificuldade; acima de 10 usa 10)
     * @param favor oferenda generosa: o Patrono pede como se fosse 2 pactos antes
     */
    public static PatronDemand roll(PatronKind kind, int pact, boolean favor, RandomSource rnd) {
        int tier = Math.max(1, Math.min(10, pact) - (favor ? 2 : 0));
        int r = rnd.nextInt(100);
        if (r < 40) {
            PatronDemand d = pick(ITEMS.get(kind), tier, rnd, PatronDemand.Type.ITEM);
            if (d != null) return d;
        } else if (r < 70) {
            PatronDemand d = pick(KILLS.get(kind), tier, rnd, PatronDemand.Type.KILL);
            if (d != null) return d;
        } else if (r < 82) {
            return new PatronDemand(PatronDemand.Type.SOULS, "", 3 + tier, 12 + tier);
        } else if (r < 91) {
            int hearts = tier <= 3 ? 2 : tier <= 6 ? 3 : 4;
            int minutes = tier <= 3 ? 6 : tier <= 6 ? 10 : 15;
            return new PatronDemand(PatronDemand.Type.HEARTS, "", hearts, minutes);
        } else {
            int levels = tier <= 3 ? 5 : tier <= 6 ? 10 : 15 + (tier - 7) * 2;
            return new PatronDemand(PatronDemand.Type.LEVELS, "", levels, 0);
        }
        // Categoria sem opções (pool vazio para o tier): cai num tributo de itens.
        PatronDemand d = pick(ITEMS.get(kind), tier, rnd, PatronDemand.Type.ITEM);
        return d != null ? d : new PatronDemand(PatronDemand.Type.SOULS, "", 3 + tier, 12 + tier);
    }

    private static PatronDemand pick(List<Entry> pool, int tier, RandomSource rnd, PatronDemand.Type type) {
        List<Entry> ok = new ArrayList<>();
        for (Entry e : pool) {
            if (!e.fits(tier)) continue;
            ResourceLocation id = ResourceLocation.tryParse(e.target());
            if (id == null) continue;
            boolean exists = type == PatronDemand.Type.ITEM ? BuiltInRegistries.ITEM.containsKey(id)
                    : BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            if (exists) ok.add(e);
        }
        if (ok.isEmpty()) return null;
        Entry e = ok.get(rnd.nextInt(ok.size()));
        return new PatronDemand(type, e.target(), e.amount(tier), e.minutes());
    }

    private DemandPool() {}
}
