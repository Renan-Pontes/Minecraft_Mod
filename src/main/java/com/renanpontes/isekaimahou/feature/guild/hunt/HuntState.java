package com.renanpontes.isekaimahou.feature.guild.hunt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Área de Caça de UMA missão aceita (v0.9), guardada dentro do {@code ActiveQuest} (campo opcional "hunt": saves antigos
 * abrem sem ele e a missão segue como uma caçada comum).
 * <ul>
 *   <li>{@code placeId}: id do {@code Places} (tipo {@code hunting_ground}) e da marca nos mobs/estandarte;</li>
 *   <li>{@code mobs}: criaturas da missão que podem nascer lá (as "caçáveis" que combinam com o bioma escolhido);</li>
 *   <li>{@code element}: elemento da área (pelo bioma) — define as essências que caem e a cor do círculo;</li>
 *   <li>{@code phase}: {@link #TRAVEL} indo até lá · {@link #FIGHT} ondas em andamento · {@link #CLEARED} limpa;</li>
 *   <li>{@code wave}: ondas já invocadas; {@code expires}: tempo de jogo (Overworld) em que a área se dispersa;</li>
 *   <li>{@code surveyed}: o Y do centro já foi medido no terreno de verdade (antes é a estimativa do gerador).</li>
 * </ul>
 */
public final class HuntState {
    public static final int TRAVEL = 0, FIGHT = 1, CLEARED = 2;

    private static final Codec<ResourceKey<Level>> DIM_CODEC = ResourceKey.codec(Registries.DIMENSION);

    public static final Codec<HuntState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("place").forGetter(h -> h.placeId),
            DIM_CODEC.fieldOf("dim").forGetter(h -> h.dim),
            BlockPos.CODEC.fieldOf("center").forGetter(h -> h.center),
            ResourceLocation.CODEC.listOf().optionalFieldOf("mobs", List.of()).forGetter(h -> h.mobs),
            Codec.STRING.optionalFieldOf("element", "earth").forGetter(h -> h.element),
            Codec.INT.optionalFieldOf("phase", TRAVEL).forGetter(h -> h.phase),
            Codec.INT.optionalFieldOf("wave", 0).forGetter(h -> h.wave),
            Codec.LONG.optionalFieldOf("expires", 0L).forGetter(h -> h.expires),
            Codec.BOOL.optionalFieldOf("surveyed", false).forGetter(h -> h.surveyed),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(h -> Optional.ofNullable(h.owner))
    ).apply(i, (place, dim, center, mobs, element, phase, wave, expires, surveyed, owner) ->
            new HuntState(place, dim, center, mobs, element, phase, wave, expires, surveyed, owner.orElse(null))));

    public final String placeId;
    public final ResourceKey<Level> dim;
    public BlockPos center;
    public final List<ResourceLocation> mobs;
    public final String element;
    public int phase;
    public int wave;
    public final long expires;
    public boolean surveyed;
    @Nullable
    public final UUID owner;

    // ---------- Temporários (não salvos) ----------
    /** Ticks (em segundos de checagem) até a próxima onda poder sair. */
    public int cooldown;
    /** Segundos desde que a onda atual saiu (para acender os que se esconderam). */
    public int waveAge;
    /** Invocações já marcadas no chão e ainda não nascidas (o círculo está aquecendo). */
    public int pending;
    /** Já avisou "você saiu da área" nesta saída. */
    public boolean warnedLeave;
    /**
     * Mobs invocados por esta área (busca O(1) por UUID, mesmo os que saíram correndo atrás do caçador para fora da caixa da
     * área). Depois de relogar fica vazio e a área adota os marcados que achar em volta.
     */
    public final java.util.Set<UUID> spawned = new java.util.LinkedHashSet<>();

    public HuntState(String placeId, ResourceKey<Level> dim, BlockPos center, List<ResourceLocation> mobs, String element, int phase,
                     int wave, long expires, boolean surveyed, @Nullable UUID owner) {
        this.placeId = placeId;
        this.dim = dim;
        this.center = center;
        this.mobs = List.copyOf(mobs);
        this.element = element;
        this.phase = phase;
        this.wave = wave;
        this.expires = expires;
        this.surveyed = surveyed;
        this.owner = owner;
    }

    public GlobalPos globalPos() {
        return GlobalPos.of(dim, center);
    }

    public boolean active() {
        return phase != CLEARED;
    }
}
