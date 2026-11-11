package com.renanpontes.isekaimahou.feature.guild.client;

import java.util.Map;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.guild.entity.AdventurerEntity;
import com.renanpontes.isekaimahou.feature.guild.entity.AdventurerEntity.Variant;
import com.renanpontes.isekaimahou.feature.guild.entity.GuildNpcEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Modelo GeckoLib dos NPCs da Guilda (geo/animações/textura por id da entidade).
 * Aventureiro: textura por classe e peças do equipamento (capuz, chapéu de mago, cajado, aljava, túnica, símbolo sagrado e
 * auréola do clérigo...) mostradas conforme a classe. A auréola do clérigo gira devagar e flutua.
 */
public class GuildNpcModel<T extends GuildNpcEntity> extends DefaultedEntityGeoModel<T> {
    /** Peças opcionais do aventureiro → classes que as usam. */
    private static final Map<String, Set<Variant>> PIECES = Map.ofEntries(
            Map.entry("headband", Set.of(Variant.WARRIOR)),
            Map.entry("pauldron_r", Set.of(Variant.WARRIOR)),
            Map.entry("pauldron_l", Set.of(Variant.WARRIOR)),
            Map.entry("cape", Set.of(Variant.WARRIOR, Variant.ARCHER, Variant.MAGE, Variant.CLERIC)),
            Map.entry("hood", Set.of(Variant.ARCHER, Variant.ROGUE, Variant.CLERIC)),
            Map.entry("quiver", Set.of(Variant.ARCHER)),
            Map.entry("hat_mage", Set.of(Variant.MAGE)),
            Map.entry("staff", Set.of(Variant.MAGE, Variant.CLERIC)),
            Map.entry("scarf", Set.of(Variant.ROGUE)),
            Map.entry("halo", Set.of(Variant.CLERIC)),
            Map.entry("robe", Set.of(Variant.CLERIC)),
            Map.entry("holy_symbol", Set.of(Variant.CLERIC)));

    public GuildNpcModel(String id) {
        super(IsekaiMahou.id(id), true);
    }

    @Override
    public ResourceLocation getTextureResource(T npc) {
        if (npc instanceof AdventurerEntity a) {
            return IsekaiMahou.id("textures/entity/adventurer_" + a.variant().id() + ".png");
        }
        return super.getTextureResource(npc);
    }

    @Override
    public void setCustomAnimations(T npc, long instanceId, AnimationState<T> state) {
        super.setCustomAnimations(npc, instanceId, state);
        if (npc instanceof AdventurerEntity a) {
            Variant v = a.variant();
            PIECES.forEach((bone, variants) -> getBone(bone).ifPresent(b -> b.setHidden(!variants.contains(v))));
            if (v == Variant.CLERIC) {
                float t = (npc.tickCount + state.getPartialTick()) * 0.05F;
                getBone("halo").ifPresent(b -> {
                    b.setRotY(t);
                    b.setPosY(Mth.sin(t * 2.0F) * 0.6F);
                });
            }
        }
    }
}
