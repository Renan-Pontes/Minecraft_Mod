package com.renanpontes.isekaimahou.feature.farm.wand;

import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Estado da Varinha guardado no próprio item (o cliente lê para desenhar a seleção).
 *
 * @param mode     índice do {@link WandMode}
 * @param a        1º canto
 * @param b        2º canto
 * @param material bloco escolhido (agachar + clique num bloco); a mão secundária tem prioridade
 * @param link     baú ligado (fonte extra de blocos)
 * @param clip     tamanho da área copiada (0,0,0 = prancheta vazia)
 * @param clipFacing para onde você olhava ao copiar (valor 2D da direção) — a colagem gira junto
 */
public record WandData(int mode, Optional<BlockPos> a, Optional<BlockPos> b, Optional<ResourceLocation> material,
                       Optional<GlobalPos> link, BlockPos clip, int clipFacing) {
    public static final WandData EMPTY = new WandData(0, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), BlockPos.ZERO, 2);

    public static final Codec<WandData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("mode", 0).forGetter(WandData::mode),
            BlockPos.CODEC.optionalFieldOf("a").forGetter(WandData::a),
            BlockPos.CODEC.optionalFieldOf("b").forGetter(WandData::b),
            ResourceLocation.CODEC.optionalFieldOf("material").forGetter(WandData::material),
            GlobalPos.CODEC.optionalFieldOf("link").forGetter(WandData::link),
            BlockPos.CODEC.optionalFieldOf("clip", BlockPos.ZERO).forGetter(WandData::clip),
            Codec.INT.optionalFieldOf("clip_facing", 2).forGetter(WandData::clipFacing)
    ).apply(i, WandData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public WandMode wandMode() {
        return WandMode.byIndex(mode);
    }

    public WandData withMode(WandMode m) {
        return new WandData(m.ordinal(), a, b, material, link, clip, clipFacing);
    }

    public WandData withCorners(@Nullable BlockPos na, @Nullable BlockPos nb) {
        return new WandData(mode, Optional.ofNullable(na), Optional.ofNullable(nb), material, link, clip, clipFacing);
    }

    public WandData withMaterial(@Nullable ResourceLocation m) {
        return new WandData(mode, a, b, Optional.ofNullable(m), link, clip, clipFacing);
    }

    public WandData withLink(@Nullable GlobalPos l) {
        return new WandData(mode, a, b, material, Optional.ofNullable(l), clip, clipFacing);
    }

    public WandData withClip(BlockPos size, Direction facing) {
        return new WandData(mode, a, b, material, link, size, facing.get2DDataValue());
    }

    public Direction clipDirection() {
        return Direction.from2DDataValue(clipFacing);
    }

    public boolean hasSelection() {
        return a.isPresent() && b.isPresent();
    }

    public boolean hasClip() {
        return clip.getX() > 0 && clip.getY() > 0 && clip.getZ() > 0;
    }

    /** Caixa marcada (inclui o 2º canto) ou só o 1º canto. */
    @Nullable
    public AABB box() {
        if (a.isEmpty()) return null;
        BlockPos p = a.get(), q = b.orElse(p);
        return new AABB(Math.min(p.getX(), q.getX()), Math.min(p.getY(), q.getY()), Math.min(p.getZ(), q.getZ()),
                Math.max(p.getX(), q.getX()) + 1, Math.max(p.getY(), q.getY()) + 1, Math.max(p.getZ(), q.getZ()) + 1);
    }

    /** Tamanho da seleção (x, y, z), ou zero. */
    public BlockPos size() {
        if (!hasSelection()) return BlockPos.ZERO;
        BlockPos p = a.get(), q = b.get();
        return new BlockPos(Math.abs(p.getX() - q.getX()) + 1, Math.abs(p.getY() - q.getY()) + 1, Math.abs(p.getZ() - q.getZ()) + 1);
    }
}
