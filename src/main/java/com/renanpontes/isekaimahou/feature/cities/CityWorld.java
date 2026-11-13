package com.renanpontes.isekaimahou.feature.cities;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Dados do MUNDO das cidades (release 1.0), guardados no Overworld ({@code data/isekai_mahou_cities_world.dat}):
 * a cidade inicial (onde os jogadores novos nascem) e a chave geral da rotina dos moradores. Arquivo novo — mundos
 * antigos simplesmente não têm e começam com os padrões.
 */
public class CityWorld extends SavedData {
    private static final String NAME = "isekai_mahou_cities_world";

    /** Cidade inicial: ainda não decidido / definida / nenhuma cidade achada perto / desligada por comando. */
    public static final int START_UNDECIDED = 0, START_SET = 1, START_NONE = 2, START_OFF = 3;

    public int startState = START_UNDECIDED;
    /** Onde o jogador pisa ao nascer (praça da cidade inicial). */
    @Nullable public BlockPos startPos;
    /** Coração da cidade inicial (centro do lugar). */
    @Nullable public BlockPos startHeart;
    /** Ponto de nascimento do mundo antes de a cidade inicial ser escolhida (para o comando de desfazer). */
    @Nullable public BlockPos originalSpawn;
    public float startAngle;
    /** Rotina dos moradores desligada por comando (chave geral de segurança). */
    public boolean routineOff;

    public static CityWorld get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CityWorld::new, CityWorld::load, null), NAME);
    }

    private static CityWorld load(CompoundTag tag, HolderLookup.Provider registries) {
        CityWorld w = new CityWorld();
        w.startState = tag.getInt("StartState");
        if (tag.contains("StartPos")) w.startPos = BlockPos.of(tag.getLong("StartPos"));
        if (tag.contains("StartHeart")) w.startHeart = BlockPos.of(tag.getLong("StartHeart"));
        if (tag.contains("OriginalSpawn")) w.originalSpawn = BlockPos.of(tag.getLong("OriginalSpawn"));
        w.startAngle = tag.getFloat("StartAngle");
        w.routineOff = tag.getBoolean("RoutineOff");
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("StartState", startState);
        if (startPos != null) tag.putLong("StartPos", startPos.asLong());
        if (startHeart != null) tag.putLong("StartHeart", startHeart.asLong());
        if (originalSpawn != null) tag.putLong("OriginalSpawn", originalSpawn.asLong());
        tag.putFloat("StartAngle", startAngle);
        tag.putBoolean("RoutineOff", routineOff);
        return tag;
    }
}
