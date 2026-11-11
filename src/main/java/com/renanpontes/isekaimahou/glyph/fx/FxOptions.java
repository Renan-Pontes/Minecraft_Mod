package com.renanpontes.isekaimahou.glyph.fx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Opções de TODAS as partículas {@code isekai_mahou:fx_*} (pacote compacto: cor + escala + vida + 2 inteiros).
 *
 * @param type  o tipo da partícula (cada tipo tem seu próprio codec ligado a ele)
 * @param rgb   cor RGB (sem alfa) que tinge a textura branca
 * @param scale escala relativa (partículas soltas: 1 = tamanho padrão) ou RAIO em blocos (círculos, ondas, pilares, clarão)
 * @param life  vida em ticks (0 = padrão do tipo)
 * @param data  id da entidade a seguir (círculos mágicos) ou -1; pilares: altura em 1/16 de bloco
 * @param flags bits de comportamento (ver {@link SpellFxParticles} — FLAG_*)
 */
public record FxOptions(ParticleType<FxOptions> type, int rgb, float scale, int life, int data, int flags)
        implements ParticleOptions {

    @Override
    public ParticleType<?> getType() { return type; }

    public FxOptions withScale(float s) { return new FxOptions(type, rgb, s, life, data, flags); }

    public static MapCodec<FxOptions> codec(ParticleType<FxOptions> type) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(FxOptions::rgb),
                Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(FxOptions::scale),
                Codec.INT.optionalFieldOf("life", 0).forGetter(FxOptions::life),
                Codec.INT.optionalFieldOf("data", -1).forGetter(FxOptions::data),
                Codec.INT.optionalFieldOf("flags", 0).forGetter(FxOptions::flags)
        ).apply(i, (c, s, l, d, f) -> new FxOptions(type, c, s, l, d, f)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, FxOptions> streamCodec(ParticleType<FxOptions> type) {
        return StreamCodec.of(
                (buf, o) -> {
                    buf.writeMedium(o.rgb & 0xFFFFFF);
                    buf.writeFloat(o.scale);
                    buf.writeVarInt(Math.max(0, o.life));
                    buf.writeVarInt(o.data + 1);
                    buf.writeVarInt(Math.max(0, o.flags));
                },
                buf -> new FxOptions(type, buf.readUnsignedMedium(), buf.readFloat(), buf.readVarInt(), buf.readVarInt() - 1,
                        buf.readVarInt()));
    }
}
