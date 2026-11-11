package com.renanpontes.isekaimahou.feature.hueco_mundo;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Os dois reinos do módulo.
 * <ul>
 *   <li><b>Mundo Oco</b> ({@code hollow_world}): deserto branco sob a lua crescente eterna. Chegada no Mirante da Garganta
 *       (sul), Palácio da Noite Eterna no centro com o Rei Vazio e a Imperatriz das Areias.</li>
 *   <li><b>Reino das Fadas</b> ({@code fey_realm}): colinas em crepúsculo eterno, cogumelos que brilham; a Corte das Fadas no
 *       centro com a Rainha dos Espinhos.</li>
 * </ul>
 * Entrada: rituais nos altares do Mundo Normal (Fenda da Garganta / Círculo das Fadas), a Chave da Garganta, os glifos de
 * atalho do Shinigami e do Quincy, ou a Viagem (hub já descoberto).
 */
public enum HmRealm {
    HOLLOW("hollow_world", 0xB8C8E8, "garganta_site", "hollow_hub", "hub_hollow_world", 0, 150),
    FEY("fey_realm", 0xF29AD8, "fey_ring", "fey_court", "hub_fey_realm", 0, 0);

    public final String id;
    public final ResourceKey<Level> key;
    public final int color;
    /** Estrutura do Mundo Normal com o altar de entrada. */
    public final String overworldSite;
    /** Molde .nbt do lugar de chegada. */
    public final ResourceLocation hubTemplate;
    /** Id do hub no {@code Places} (destino de Viagem). */
    public final String hubPlaceId;
    /** Centro (x, z) do hub no reino. */
    public final int hubX, hubZ;

    HmRealm(String id, int color, String overworldSite, String hubTemplate, String hubPlaceId, int hubX, int hubZ) {
        this.id = id;
        this.key = ResourceKey.create(Registries.DIMENSION, IsekaiMahou.id(id));
        this.color = color;
        this.overworldSite = overworldSite;
        this.hubTemplate = IsekaiMahou.id(hubTemplate);
        this.hubPlaceId = hubPlaceId;
        this.hubX = hubX;
        this.hubZ = hubZ;
    }

    public MutableComponent displayName() {
        return HmText.of("dimension.isekai_mahou", id).withColor(color);
    }

    public MutableComponent hubName() {
        return HmText.of("place.isekai_mahou.hueco_mundo", hubPlaceId);
    }

    /** Contador de visita (convenção do núcleo: visit.&lt;ns&gt;.&lt;dimensão&gt;). */
    public String visitKey() {
        return "visit." + IsekaiMahou.MODID + "." + id;
    }

    @Nullable
    public static HmRealm of(ResourceKey<Level> key) {
        for (HmRealm r : values()) if (r.key.equals(key)) return r;
        return null;
    }

    @Nullable
    public static HmRealm byId(String id) {
        for (HmRealm r : values()) if (r.id.equals(id)) return r;
        return null;
    }
}
