package com.renanpontes.isekaimahou.feature.endless_abyss;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

/**
 * Regras dos andares do Abismo Infinito (fórmulas puras): quantas ondas, quantos inimigos, quanto mais fortes ficam e de
 * que "poço" de criaturas saem. Andares múltiplos de {@link #BOSS_EVERY} são de chefe (uma Sombra); a cada
 * {@link #CHECKPOINT_EVERY} andares vencidos o jogador ganha um ponto de partida novo.
 * <p>
 * As criaturas são referidas só por id em texto (outros módulos); as que não existirem são puladas.
 */
final class AbyssFloors {
    static final int BOSS_EVERY = 5;
    static final int CHECKPOINT_EVERY = 10;

    static boolean isBossFloor(int floor) {
        return floor > 0 && floor % BOSS_EVERY == 0;
    }

    /** Maior andar de onde o jogador pode recomeçar: logo depois do último marco de 10 que ele venceu. */
    static int checkpointStart(int best) {
        return (best / CHECKPOINT_EVERY) * CHECKPOINT_EVERY + 1;
    }

    static int waves(int floor) {
        return floor < 10 ? 2 : floor < 30 ? 3 : 4;
    }

    /** Inimigos por onda: 3 no começo, +1 a cada 4 andares (máx. 9); +50% por jogador extra (máx. 14). */
    static int mobsPerWave(int floor, int players) {
        int base = Math.min(9, 3 + floor / 4);
        return Math.min(14, Math.round(base * (1F + 0.5F * Math.max(0, players - 1))));
    }

    /** Vida dos inimigos comuns: +10% por andar (andar 50: ×6). */
    static float healthMult(int floor) {
        return 1F + 0.10F * floor;
    }

    /** Dano dos inimigos comuns: +4% por andar (andar 50: ×3). */
    static float damageMult(int floor) {
        return 1F + 0.04F * floor;
    }

    /** Vida da Sombra num andar de chefe: ×1,5 no andar 5, +5% por andar. */
    static float bossHealthMult(int floor) {
        return 1.25F + 0.05F * floor;
    }

    /** Dano da Sombra num andar de chefe: ×1,25 +3% por andar. */
    static float bossDamageMult(int floor) {
        return 1.1F + 0.03F * floor;
    }

    /** Luta das Sombras avulsa: ×2 de vida e ×1,5 de dano, +10% por vitória anterior contra a MESMA Sombra (até o dobro). */
    static float shadowFightHealth(int previousWins) {
        return 2.0F * (1F + 0.1F * Math.min(10, previousWins));
    }

    static float shadowFightDamage(int previousWins) {
        return 1.5F * (1F + 0.05F * Math.min(10, previousWins));
    }

    // ------------------------------------------------------------------------------------------------ poços
    private static final String[] TIER_0 = {
            "isekai_mahou:creeping_shade", "isekai_mahou:void_bat", "isekai_mahou:cultist", "isekai_mahou:kobold",
            "isekai_mahou:hobgoblin", "minecraft:wither_skeleton", "minecraft:stray", "minecraft:husk"};
    private static final String[] TIER_1 = {
            "isekai_mahou:gargoyle", "isekai_mahou:werewolf", "isekai_mahou:void_crawler", "isekai_mahou:watcher_eye",
            "isekai_mahou:orc_berserker", "isekai_mahou:lizardman", "minecraft:vindicator", "minecraft:blaze"};
    private static final String[] TIER_2 = {
            "isekai_mahou:ogre", "isekai_mahou:troll", "isekai_mahou:basilisk", "isekai_mahou:orc",
            "isekai_mahou:goblin_shaman", "minecraft:piglin_brute", "minecraft:evoker"};
    /** Reserva se nada do poço existir (sempre existem). */
    private static final String[] FALLBACK = {"minecraft:wither_skeleton", "minecraft:husk", "minecraft:stray"};

    /** Tipos disponíveis no andar (os tiers vão se somando: andar 10 abre o segundo, 25 o terceiro). */
    static List<EntityType<?>> pool(int floor) {
        List<EntityType<?>> out = new ArrayList<>();
        add(out, TIER_0);
        if (floor >= 10) add(out, TIER_1);
        if (floor >= 25) add(out, TIER_2);
        if (out.isEmpty()) add(out, FALLBACK);
        return out;
    }

    /** Um tipo para o "Campeão do Abismo" (andar de chefe sem nenhuma Sombra disponível): do tier mais alto liberado. */
    static EntityType<?> championType(int floor, RandomSource random) {
        List<EntityType<?>> out = new ArrayList<>();
        add(out, floor >= 25 ? TIER_2 : floor >= 10 ? TIER_1 : TIER_0);
        if (out.isEmpty()) add(out, FALLBACK);
        return out.get(random.nextInt(out.size()));
    }

    private static void add(List<EntityType<?>> out, String[] ids) {
        for (String s : ids) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id != null) BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(out::add);
        }
    }

    private AbyssFloors() {}
}
