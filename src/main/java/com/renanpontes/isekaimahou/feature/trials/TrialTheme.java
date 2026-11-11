package com.renanpontes.isekaimahou.feature.trials;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Tema de cada elemento nas Câmaras: paleta de blocos (recepção e arena), criaturas por "tier" (ids do mod com
 * reserva vanilla), Guardião, cor da barra e sons. Fusões misturam os dois pais (paleta, criaturas e perigos).
 */
public final class TrialTheme {

    /** Blocos usados na construção. {@code light} é um bloco cheio que ilumina (vai embutido no piso/colunas). */
    public record Palette(BlockState floorA, BlockState floorB, BlockState accent, BlockState trim, BlockState pillar,
                          BlockState wall, BlockState glass, BlockState light, BlockState carpet, BlockState brazier) {}

    /** Criaturas do elemento por força (1 fraca, 2 média, 3 forte) — ids do mod e reservas vanilla. */
    private record Roster(List<String> t1, List<String> t2, List<String> t3, List<String> v1, List<String> v2, List<String> v3) {}

    /** Criaturas que voam: nascem um pouco acima do chão. */
    private static final Set<String> FLYERS = Set.of("isekai_mahou:storm_hawk", "isekai_mahou:harpy", "isekai_mahou:void_bat",
            "isekai_mahou:lesser_phoenix", "isekai_mahou:storm_wyvern", "isekai_mahou:solar_sentinel", "isekai_mahou:sylph",
            "isekai_mahou:watcher_eye", "isekai_mahou:gargoyle", "isekai_mahou:angel_sentinel", "isekai_mahou:greater_air_elemental",
            "minecraft:phantom", "minecraft:vex", "minecraft:blaze", "minecraft:breeze");

    // ------------------------------------------------------------------------------------------------ paleta
    public static Palette palette(Element e) {
        if (e.isFusion()) {
            Palette a = basePalette(e.parentA), b = basePalette(e.parentB);
            // Fusão: piso e parede do pai A, detalhes/colunas/luz do pai B — as duas identidades aparecem.
            return new Palette(a.floorA, b.floorA, a.accent, b.trim, b.pillar, a.wall, a.glass, b.light, a.carpet, b.brazier);
        }
        return basePalette(e);
    }

    private static Palette basePalette(Element e) {
        return switch (e) {
            case FIRE -> new Palette(s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.BLACKSTONE), s(Blocks.RED_NETHER_BRICKS),
                    s(Blocks.GILDED_BLACKSTONE), s(Blocks.POLISHED_BASALT), s(Blocks.NETHER_BRICKS), s(Blocks.ORANGE_STAINED_GLASS),
                    s(Blocks.SHROOMLIGHT), s(Blocks.ORANGE_CARPET), s(Blocks.CAMPFIRE));
            case WATER -> new Palette(s(Blocks.PRISMARINE_BRICKS), s(Blocks.PRISMARINE), s(Blocks.DARK_PRISMARINE),
                    s(Blocks.CYAN_GLAZED_TERRACOTTA), s(Blocks.DARK_PRISMARINE), s(Blocks.DARK_PRISMARINE), s(Blocks.LIGHT_BLUE_STAINED_GLASS),
                    s(Blocks.SEA_LANTERN), s(Blocks.CYAN_CARPET),
                    // o conduíte nasce "alagado" por padrão: sem isso a água escorreria pela recepção
                    Blocks.CONDUIT.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
            case EARTH -> new Palette(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.POLISHED_GRANITE),
                    s(Blocks.MUD_BRICKS), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.DEEPSLATE_BRICKS), s(Blocks.BROWN_STAINED_GLASS),
                    s(Blocks.OCHRE_FROGLIGHT), s(Blocks.MOSS_CARPET), s(Blocks.LANTERN));
            case WIND -> new Palette(s(Blocks.CALCITE), s(Blocks.POLISHED_DIORITE), s(Blocks.SMOOTH_QUARTZ),
                    s(Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA), s(Blocks.QUARTZ_PILLAR), s(Blocks.QUARTZ_BRICKS), s(Blocks.WHITE_STAINED_GLASS),
                    s(Blocks.PEARLESCENT_FROGLIGHT), s(Blocks.WHITE_CARPET),
                    Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
            case LIGHT -> new Palette(s(Blocks.SMOOTH_SANDSTONE), s(Blocks.CUT_SANDSTONE), s(Blocks.CHISELED_QUARTZ_BLOCK),
                    s(Blocks.GOLD_BLOCK), s(Blocks.QUARTZ_PILLAR), s(Blocks.SMOOTH_QUARTZ), s(Blocks.YELLOW_STAINED_GLASS),
                    s(Blocks.GLOWSTONE), s(Blocks.YELLOW_CARPET), s(Blocks.LANTERN));
            case SHADOW -> new Palette(s(Blocks.DEEPSLATE_TILES), s(Blocks.POLISHED_BLACKSTONE), s(Blocks.OBSIDIAN),
                    s(Blocks.CRYING_OBSIDIAN), s(Blocks.OBSIDIAN), s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.PURPLE_STAINED_GLASS),
                    s(Blocks.CRYING_OBSIDIAN), s(Blocks.PURPLE_CARPET), s(Blocks.SOUL_CAMPFIRE));
            default -> new Palette(s(Blocks.DEEPSLATE_BRICKS), s(Blocks.POLISHED_DEEPSLATE), s(Blocks.AMETHYST_BLOCK),
                    s(Blocks.PURPUR_BLOCK), s(Blocks.PURPUR_PILLAR), s(Blocks.DEEPSLATE_TILES), s(Blocks.MAGENTA_STAINED_GLASS),
                    s(Blocks.SEA_LANTERN), s(Blocks.MAGENTA_CARPET), s(Blocks.LANTERN));
        };
    }

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    /** Elementos base que formam o elemento (fusão → os dois pais; base → ele mesmo). */
    public static List<Element> bases(Element e) {
        if (e.isFusion()) return List.of(e.parentA, e.parentB);
        return List.of(e);
    }

    // ------------------------------------------------------------------------------------------------ barra / sons
    public static BossEvent.BossBarColor barColor(Element e) {
        return switch (e) {
            case FIRE, MAGMA, HELLFIRE -> BossEvent.BossBarColor.RED;
            case WATER, LIGHTNING, ICE, ABYSS, ASTRAL -> BossEvent.BossBarColor.BLUE;
            case EARTH, NATURE -> BossEvent.BossBarColor.GREEN;
            case WIND, STEAM, PURITY -> BossEvent.BossBarColor.WHITE;
            case LIGHT, SUN, SAND -> BossEvent.BossBarColor.YELLOW;
            case CRYSTAL, TWILIGHT -> BossEvent.BossBarColor.PINK;
            default -> BossEvent.BossBarColor.PURPLE;
        };
    }

    /** Som "de presença" do elemento (abertura das ondas, portal da recepção). */
    public static SoundEvent ambience(Element e) {
        return switch (e.isFusion() ? e.parentA : e) {
            case FIRE -> SoundEvents.BLAZE_AMBIENT;
            case WATER -> SoundEvents.CONDUIT_ACTIVATE;
            case EARTH -> SoundEvents.DEEPSLATE_BREAK;
            case WIND -> SoundEvents.BREEZE_IDLE_GROUND;
            case LIGHT -> SoundEvents.AMETHYST_BLOCK_RESONATE;
            case SHADOW -> SoundEvents.SCULK_SHRIEKER_SHRIEK;
            default -> SoundEvents.BEACON_AMBIENT;
        };
    }

    /** Som do círculo de invocação de cada criatura (o "estalo" do elemento; o zumbido do círculo é o do spawner). */
    public static SoundEvent summon(Element e) {
        return switch (e.isFusion() ? e.parentB : e) {
            case FIRE -> SoundEvents.FIRECHARGE_USE;
            case WATER -> SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
            case EARTH -> SoundEvents.ROOTED_DIRT_PLACE;
            case WIND -> SoundEvents.PHANTOM_FLAP;
            case LIGHT -> SoundEvents.BEACON_POWER_SELECT;
            case SHADOW -> SoundEvents.ENDERMAN_TELEPORT;
            default -> SoundEvents.EVOKER_CAST_SPELL;
        };
    }

    /** Partícula de "assinatura" do perigo/ambiente do elemento base (lado do servidor, sendParticles). */
    public static net.minecraft.core.particles.ParticleOptions mote(Element e) {
        return switch (e.isFusion() ? e.parentA : e) {
            case FIRE -> net.minecraft.core.particles.ParticleTypes.FLAME;
            case WATER -> net.minecraft.core.particles.ParticleTypes.BUBBLE_POP;
            case EARTH -> net.minecraft.core.particles.ParticleTypes.MYCELIUM;
            case WIND -> net.minecraft.core.particles.ParticleTypes.CLOUD;
            case LIGHT -> net.minecraft.core.particles.ParticleTypes.END_ROD;
            case SHADOW -> net.minecraft.core.particles.ParticleTypes.SCULK_SOUL;
            default -> net.minecraft.core.particles.ParticleTypes.ENCHANT;
        };
    }

    // ------------------------------------------------------------------------------------------------ criaturas
    private static Roster roster(Element base) {
        return switch (base) {
            case FIRE -> new Roster(
                    List.of("isekai_mahou:fire_slime", "isekai_mahou:salamander"),
                    List.of("isekai_mahou:ember_imp", "isekai_mahou:orc_berserker", "isekai_mahou:salamander"),
                    List.of("isekai_mahou:greater_fire_elemental", "isekai_mahou:lesser_phoenix"),
                    List.of("minecraft:magma_cube"), List.of("minecraft:blaze"), List.of("minecraft:wither_skeleton"));
            case WATER -> new Roster(
                    List.of("isekai_mahou:water_slime", "isekai_mahou:lizardman"),
                    List.of("isekai_mahou:lizardman", "isekai_mahou:water_slime"),
                    List.of("isekai_mahou:greater_water_elemental"),
                    List.of("minecraft:drowned"), List.of("minecraft:drowned", "minecraft:stray"), List.of("minecraft:drowned"));
            case EARTH -> new Roster(
                    List.of("isekai_mahou:stone_slime", "isekai_mahou:kobold", "isekai_mahou:goblin"),
                    List.of("isekai_mahou:crystal_mole", "isekai_mahou:moss_golem", "isekai_mahou:hobgoblin", "isekai_mahou:orc"),
                    List.of("isekai_mahou:greater_earth_elemental", "isekai_mahou:troll", "isekai_mahou:ogre"),
                    List.of("minecraft:husk"), List.of("minecraft:zombie"), List.of("minecraft:vindicator"));
            case WIND -> new Roster(
                    List.of("isekai_mahou:wind_slime", "isekai_mahou:sylph", "isekai_mahou:goblin_archer"),
                    List.of("isekai_mahou:storm_hawk", "isekai_mahou:harpy"),
                    List.of("isekai_mahou:greater_air_elemental", "isekai_mahou:storm_wyvern"),
                    List.of("minecraft:stray"), List.of("minecraft:phantom"), List.of("minecraft:breeze"));
            case LIGHT -> new Roster(
                    List.of("isekai_mahou:solar_sentinel", "isekai_mahou:gargoyle"),
                    List.of("isekai_mahou:solar_sentinel", "isekai_mahou:basilisk"),
                    List.of("isekai_mahou:angel_sentinel", "isekai_mahou:crystal_golem"),
                    List.of("minecraft:skeleton"), List.of("minecraft:vex"), List.of("minecraft:evoker"));
            case SHADOW -> new Roster(
                    List.of("isekai_mahou:creeping_shade", "isekai_mahou:void_bat", "isekai_mahou:goblin_shaman"),
                    List.of("isekai_mahou:werewolf", "isekai_mahou:cultist"),
                    List.of("isekai_mahou:void_crawler", "isekai_mahou:watcher_eye"),
                    List.of("minecraft:spider"), List.of("minecraft:witch"), List.of("minecraft:wither_skeleton"));
            default -> new Roster(
                    List.of("isekai_mahou:wandering_spirit"), List.of("isekai_mahou:arcane_golem"), List.of("isekai_mahou:arcane_golem"),
                    List.of("minecraft:zombie"), List.of("minecraft:skeleton"), List.of("minecraft:vindicator"));
        };
    }

    /**
     * Sorteia uma criatura do tier (1..3) para o elemento. Fusões sorteiam entre os dois pais. Ids do mod que não
     * existirem caem na reserva vanilla do mesmo tier (o módulo de criaturas pode estar ausente).
     */
    public static EntityType<?> pick(Element e, int tier, RandomSource r) {
        List<Element> bases = bases(e);
        Roster ro = roster(bases.get(r.nextInt(bases.size())));
        int t = Math.max(1, Math.min(3, tier));
        List<String> mod = t == 1 ? ro.t1 : t == 2 ? ro.t2 : ro.t3;
        List<String> van = t == 1 ? ro.v1 : t == 2 ? ro.v2 : ro.v3;
        List<EntityType<?>> found = resolve(mod);
        // Com as criaturas do mod presentes, a reserva vanilla aparece só de vez em quando (variedade).
        if (!found.isEmpty() && r.nextFloat() < 0.85F) return found.get(r.nextInt(found.size()));
        List<EntityType<?>> vanilla = resolve(van);
        if (!vanilla.isEmpty()) return vanilla.get(r.nextInt(vanilla.size()));
        return found.isEmpty() ? EntityType.ZOMBIE : found.get(r.nextInt(found.size()));
    }

    private static List<EntityType<?>> resolve(List<String> ids) {
        List<EntityType<?>> out = new ArrayList<>();
        for (String id : ids) type(id).ifPresent(out::add);
        return out;
    }

    public static Optional<EntityType<?>> type(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(rl);
    }

    public static boolean isFlyer(EntityType<?> type) {
        return FLYERS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    // ------------------------------------------------------------------------------------------------ chefes
    /** Id do Guardião do elemento base (módulos bosses_a / bosses_b). Fusão → Guardião do pai A. */
    @Nullable
    public static String guardian(Element e) {
        return switch (e.isFusion() ? e.parentA : e) {
            case FIRE -> "isekai_mahou:ignis";
            case WATER -> "isekai_mahou:tide_guardian";
            case EARTH -> "isekai_mahou:primordial_golem";
            case WIND -> "isekai_mahou:zephyr";
            case LIGHT -> "isekai_mahou:seraph";
            case SHADOW -> "isekai_mahou:abyss_sovereign";
            default -> null;
        };
    }

    /** Nome do nó do caminho liberado pelo rank (Aprendiz … Arquimago). */
    public static net.minecraft.network.chat.Component nodeName(int rank) {
        return switch (rank) {
            case 1 -> net.minecraft.network.chat.Component.translatable("trials.isekai_mahou.node.apprentice");
            case 2 -> net.minecraft.network.chat.Component.translatable("trials.isekai_mahou.node.adept");
            case 3 -> net.minecraft.network.chat.Component.translatable("trials.isekai_mahou.node.fluidity");
            case 4 -> net.minecraft.network.chat.Component.translatable("trials.isekai_mahou.node.master");
            case 5 -> net.minecraft.network.chat.Component.translatable("trials.isekai_mahou.node.cataclysm");
            case 6 -> net.minecraft.network.chat.Component.translatable("trials.isekai_mahou.node.domain");
            default -> net.minecraft.network.chat.Component.translatable("trials.isekai_mahou.node.archmage");
        };
    }

    public static final String AVATAR = "isekai_mahou:trial_avatar";
    public static final String SENTINEL = "isekai_mahou:trial_sentinel";

    private TrialTheme() {}
}
