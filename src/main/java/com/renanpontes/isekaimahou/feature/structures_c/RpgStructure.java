package com.renanpontes.isekaimahou.feature.structures_c;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * As estruturas de RPG deste módulo (o worldgen é por dados em data/isekai_mahou/worldgen/...; só a Ponte do
 * Troll usa um tipo próprio, {@link TrollBridgeStructure}).
 * Aqui ficam só as regras de jogo ligadas a cada uma: XP e título de descoberta, som, e quem "mora" nela
 * (spawns extras/substitutos dentro da área — ver {@link StructureSpawns}).
 */
public enum RpgStructure {
    GUILD_HALL_VILLAGE("guild_hall_village", 25, 0x5AA9FF, () -> SoundEvents.BELL_BLOCK, SpawnMode.SAFE_ZONE,
            spawn("adventurer", 10, 1, 1)),
    GOBLIN_VILLAGE("goblin_village", 15, 0x7BC043, () -> SoundEvents.PIGLIN_CELEBRATE, SpawnMode.REPLACE_MONSTERS,
            spawn("goblin", 40, 2, 4), spawn("goblin_archer", 20, 1, 2), spawn("goblin_shaman", 6, 1, 1),
            spawn("hobgoblin", 4, 1, 1)),
    GOBLIN_FORTRESS("goblin_fortress", 40, 0x3E8E2A, () -> SoundEvents.RAID_HORN.value(), SpawnMode.REPLACE_MONSTERS,
            spawn("goblin", 30, 2, 3), spawn("goblin_archer", 25, 1, 2), spawn("hobgoblin", 15, 1, 1),
            spawn("goblin_wolf_rider", 8, 1, 1), spawn("goblin_shaman", 6, 1, 1)),
    ORC_CAMP("orc_camp", 30, 0xC0582E, () -> SoundEvents.VINDICATOR_CELEBRATE, SpawnMode.REPLACE_MONSTERS,
            spawn("orc", 40, 1, 3), spawn("orc_berserker", 12, 1, 1), spawn("ogre", 3, 1, 1)),
    KOBOLD_DEN("kobold_den", 25, 0xD39A45, () -> SoundEvents.AMBIENT_CAVE.value(), SpawnMode.REPLACE_MONSTERS,
            spawn("kobold", 50, 2, 4)),
    MINOTAUR_LABYRINTH("minotaur_labyrinth", 50, 0xB0302B, () -> SoundEvents.RAVAGER_ROAR, SpawnMode.ADD,
            spawn("gargoyle", 15, 1, 1)),
    HARPY_NEST("harpy_nest", 30, 0xE6DDF5, () -> SoundEvents.PHANTOM_AMBIENT, SpawnMode.ADD,
            spawn("harpy", 30, 1, 3)),
    LIZARDMAN_VILLAGE("lizardman_village", 25, 0x4FA35E, () -> SoundEvents.FROG_AMBIENT, SpawnMode.REPLACE_MONSTERS,
            spawn("lizardman", 40, 1, 3)),
    TROLL_BRIDGE("troll_bridge", 20, 0x8A9A5B, () -> SoundEvents.POLAR_BEAR_WARNING, SpawnMode.NONE),
    RENEGADE_TOWER("renegade_tower", 45, 0x8A4FC8, () -> SoundEvents.EVOKER_PREPARE_SUMMON, SpawnMode.ADD,
            spawn("cultist", 25, 1, 2));

    /** Como os spawns naturais mudam dentro da estrutura. */
    public enum SpawnMode {
        /** Nada muda. */
        NONE,
        /** Soma os moradores à lista do bioma. */
        ADD,
        /** Troca os monstros do bioma pelos moradores (se algum deles existir). */
        REPLACE_MONSTERS,
        /** Nenhum monstro nasce ali dentro (protege os NPCs da Guilda); moradores pacíficos ainda podem nascer. */
        SAFE_ZONE
    }

    /** Morador: id de entidade do mod (por texto — pode não existir se o módulo dela faltar). */
    public record Spawn(String entity, int weight, int min, int max) {}

    /** Estrutura encontrada numa posição + o seu "start" (caixa, peças). */
    public record Found(RpgStructure structure, StructureStart start) {}

    private static final Map<ResourceLocation, RpgStructure> BY_ID = new HashMap<>();

    static {
        for (RpgStructure s : values()) BY_ID.put(s.location, s);
    }

    public final String id;
    public final ResourceLocation location;
    public final ResourceKey<Structure> key;
    /** XP mágico ganho ao descobrir pela primeira vez. */
    public final int discoveryXp;
    /** Cor do título de descoberta (RGB). */
    public final int color;
    public final SpawnMode spawnMode;
    public final List<Spawn> spawns;
    private final Supplier<SoundEvent> sound;
    /** "structure.isekai_mahou.&lt;id&gt;" (nome) e "...&lt;id&gt;.flavor" (frase da descoberta). */
    private final String translationKey;

    RpgStructure(String id, int discoveryXp, int color, Supplier<SoundEvent> sound, SpawnMode spawnMode, Spawn... spawns) {
        this.id = id;
        this.location = IsekaiMahou.id(id);
        this.key = ResourceKey.create(Registries.STRUCTURE, this.location);
        this.discoveryXp = discoveryXp;
        this.color = color;
        this.sound = sound;
        this.spawnMode = spawnMode;
        this.spawns = List.of(spawns);
        this.translationKey = "structure." + IsekaiMahou.MODID + "." + id;
    }

    private static Spawn spawn(String entity, int weight, int min, int max) {
        return new Spawn(entity, weight, min, max);
    }

    public SoundEvent sound() {
        return sound.get();
    }

    /** Nome traduzido ("structure.isekai_mahou.<id>"), já na cor da estrutura. */
    public MutableComponent title() {
        return Component.translatable(translationKey).withStyle(s -> s.withColor(color));
    }

    /** Frase de ambientação mostrada como subtítulo na descoberta. */
    public MutableComponent flavor() {
        return Component.translatable(translationKey + ".flavor");
    }

    /** Contador do MagicData: instâncias exploradas desta estrutura (mesma convenção do structures_b). */
    public String counterKey() {
        return "explore." + id;
    }

    @Nullable
    public static RpgStructure byId(@Nullable ResourceLocation id) {
        return id == null ? null : BY_ID.get(id);
    }

    /**
     * Qual estrutura deste módulo contém a posição. {@code requirePiece}: true = só dentro de uma peça (prédio,
     * rua, sala); false = em qualquer lugar da caixa total da estrutura. Só consulta chunks já carregados.
     */
    @Nullable
    public static Found find(ServerLevel level, BlockPos pos, boolean requirePiece) {
        if (!level.isLoaded(pos)) return null;
        StructureManager sm = level.structureManager();
        if (!sm.hasAnyStructureAt(pos)) return null;
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Structure structure : sm.getAllStructuresAt(pos).keySet()) {
            RpgStructure def = byId(registry.getKey(structure));
            if (def == null) continue;
            StructureStart start = requirePiece ? sm.getStructureWithPieceAt(pos, structure) : sm.getStructureAt(pos, structure);
            if (start.isValid()) return new Found(def, start);
        }
        return null;
    }

    /** O "start" DESTA estrutura que contém a posição (caixa total), ou null. */
    @Nullable
    public StructureStart startAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        if (structure == null) return null;
        StructureStart start = level.structureManager().getStructureAt(pos, structure);
        return start.isValid() ? start : null;
    }
}
