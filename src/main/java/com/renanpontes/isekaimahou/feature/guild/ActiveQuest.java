package com.renanpontes.isekaimahou.feature.guild;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.feature.guild.hunt.HuntState;

import net.minecraft.resources.ResourceLocation;

/**
 * Uma missão aceita por um jogador: a linha de base dos contadores (no momento do aceite), os lugares já
 * explorados (bit por objetivo) e se o jogador já foi avisado de que ela está pronta para entregar.
 * <p>
 * v0.9: {@link #hunt} (opcional) — a Área de Caça criada no aceite das missões de caça. Saves antigos não têm o campo e
 * continuam abrindo normalmente (a missão segue como caçada comum).
 */
public final class ActiveQuest {
    public static final Codec<ActiveQuest> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(q -> q.id),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("baseline", Map.of()).forGetter(q -> q.baseline),
            Codec.INT.optionalFieldOf("explored", 0).forGetter(q -> q.explored),
            Codec.LONG.optionalFieldOf("day", 0L).forGetter(q -> q.acceptedDay),
            Codec.BOOL.optionalFieldOf("notified", false).forGetter(q -> q.notified),
            HuntState.CODEC.lenientOptionalFieldOf("hunt").forGetter(q -> Optional.ofNullable(q.hunt))
    ).apply(i, (id, base, explored, day, notified, hunt) -> {
        ActiveQuest a = new ActiveQuest(id, base, explored, day, notified);
        a.hunt = hunt.orElse(null);
        return a;
    }));

    public final ResourceLocation id;
    public final Map<String, Integer> baseline;
    public int explored;
    public final long acceptedDay;
    public boolean notified;
    /** Área de Caça desta missão (null = nenhuma). */
    @Nullable
    public HuntState hunt;

    public ActiveQuest(ResourceLocation id, Map<String, Integer> baseline, int explored, long acceptedDay, boolean notified) {
        this.id = id;
        this.baseline = new HashMap<>(baseline);
        this.explored = explored;
        this.acceptedDay = acceptedDay;
        this.notified = notified;
    }

    public boolean explored(int objective) {
        return (explored & (1 << objective)) != 0;
    }

    public void markExplored(int objective) {
        explored |= 1 << objective;
    }
}
