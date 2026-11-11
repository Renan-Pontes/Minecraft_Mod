package com.renanpontes.isekaimahou.feature.dimensions;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * As quatro dimensões do mod. Cada uma tem um "ponto de chegada" fixo (hub) construído a partir de um
 * template .nbt na primeira vez que alguém chega: assim todo portal leva a um lugar seguro e conhecido.
 *
 * {@code hubCenter} = posição no mundo do bloco de chão central do hub; {@code tx/ty/tz} = esse mesmo bloco
 * nas coordenadas do template (o template é colocado com a origem em {@code hubCenter - (tx,ty,tz)}).
 */
public enum DimKind {
    ELEMENTAL("elemental_plane", 0x7FE3FF, new BlockPos(0, 110, 0), "elemental_plane_hub", 10, 10, 10,
            new Vec3(0.5, 111.0, 5.5), 180F, 10, 12, "elemental_gate"),
    ABYSS("abyss", 0x8A4FC8, new BlockPos(0, 64, 0), "abyss_hub", 9, 1, 9,
            new Vec3(0.5, 65.0, 4.5), 180F, 7, 6, "abyssal_chasm"),
    CELESTIAL("celestial_sanctuary", 0xFFE6A0, new BlockPos(0, 150, 0), "celestial_hub", 10, 6, 10,
            new Vec3(0.5, 151.0, 5.5), 180F, 10, 12, "celestial_stairway"),
    // v1.0: arena GRANDE do Dragão Primordial (raio 43,5 + ponte ao sul); ver tools/gen/dimensions.py --only arena.
    TWILIGHT("twilight_rift", 0xC06AA0, new BlockPos(0, 64, 0), "twilight_arena", 46, 18, 46,
            new Vec3(0.5, 65.0, 47.5), 180F, 52, 24, "twilight_rupture");

    public final String id;
    public final ResourceKey<Level> key;
    public final int color;
    public final BlockPos hubCenter;
    public final ResourceLocation hubTemplate;
    public final int tx, ty, tz;
    public final Vec3 arrival;
    public final float arrivalYaw;
    /** Área protegida em volta do hub (raio horizontal e altura acima/abaixo do chão). */
    public final int protectRadius, protectHeight;
    /**
     * Estrutura do Mundo Normal (módulo world_v09) onde o portal desta dimensão espera adormecido. O world_v09
     * registra cada uma no {@code Places} com id {@code <estrutura>_<chunkX>_<chunkZ>} e tipo {@code portal_site}.
     */
    public final String portalSite;

    DimKind(String id, int color, BlockPos hubCenter, String hubTemplate, int tx, int ty, int tz,
            Vec3 arrival, float arrivalYaw, int protectRadius, int protectHeight, String portalSite) {
        this.id = id;
        this.key = ResourceKey.create(Registries.DIMENSION, IsekaiMahou.id(id));
        this.color = color;
        this.hubCenter = hubCenter;
        this.hubTemplate = IsekaiMahou.id(hubTemplate);
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        this.arrival = arrival;
        this.arrivalYaw = arrivalYaw;
        this.protectRadius = protectRadius;
        this.protectHeight = protectHeight;
        this.portalSite = portalSite;
    }

    /** Id do hub no {@code Places} (destino de Viagem): {@code hub_<dimensão>}. */
    public String hubPlaceId() {
        return "hub_" + id;
    }

    /** Nome do hub no letreiro e na lista de Viagem ("Plano Elemental — Altar da Convergência"). */
    public MutableComponent hubName() {
        return Component.translatable(langKey("place", "dimensions.hub."));
    }

    /** Pés no ponto de chegada (centro do lugar do hub). */
    public BlockPos arrivalFeet() {
        return BlockPos.containing(arrival);
    }

    /** Nome da estrutura do portal ("Portão Elemental"), traduzido por este módulo. */
    public MutableComponent siteName() {
        return Component.translatable(langKey("site", "dimensions."));
    }

    /** Onde a estrutura do portal costuma ficar (dica para quem ainda não a encontrou). */
    public MutableComponent siteWhere() {
        return Component.translatable(langKey("site", "dimensions.where."));
    }

    public BlockPos hubOrigin() {
        return hubCenter.offset(-tx, -ty, -tz);
    }

    public MutableComponent displayName() {
        return Component.translatable(langKey("dimension", "")).withColor(color);
    }

    /** Título da cutscene da primeira abertura do portal desta dimensão. */
    public MutableComponent portalOpenTitle() {
        return Component.translatable(langKey("cutscene", "dimensions.portal_open."));
    }

    /** Subtítulo da cutscene da primeira chegada. */
    public MutableComponent arrivalSubtitle() {
        return Component.translatable(langKey("cutscene", "dimensions.arrive."));
    }

    /** "<tipo>.isekai_mahou.<meio><id>" (ex.: dimension.isekai_mahou.abyss). */
    private String langKey(String type, String middle) {
        return type + "." + IsekaiMahou.MODID + "." + middle + id;
    }

    /** Contador de visitas (mesma convenção do módulo de Caminhos: visit.<ns>.<dimensão>). */
    public String visitKey() {
        return "visit." + IsekaiMahou.MODID + "." + id;
    }

    /** O ponto está dentro da área protegida do hub? */
    public boolean isProtected(BlockPos pos) {
        int dx = pos.getX() - hubCenter.getX();
        int dz = pos.getZ() - hubCenter.getZ();
        int dy = pos.getY() - hubCenter.getY();
        return dx * dx + dz * dz <= protectRadius * protectRadius && Math.abs(dy) <= protectHeight;
    }

    @Nullable
    public static DimKind of(ResourceKey<Level> key) {
        for (DimKind k : values()) if (k.key.equals(key)) return k;
        return null;
    }

    @Nullable
    public static DimKind byId(String id) {
        for (DimKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }
}
