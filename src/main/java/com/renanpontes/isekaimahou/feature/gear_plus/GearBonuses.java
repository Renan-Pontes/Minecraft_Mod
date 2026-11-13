package com.renanpontes.isekaimahou.feature.gear_plus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Aplica no jogador os bônus de CONJUNTO e de GEMAS como modificadores de atributo temporários (não ficam salvos: são
 * recalculados quando o equipamento muda, ao entrar/renascer/subir de nível e, por garantia, a cada 5 s).
 * Ids: {@code gear_plus.<set|gem>.<atributo>.<operação>} — um por atributo, com a soma de tudo.
 */
public final class GearBonuses {
    private record Key(String source, Holder<Attribute> attribute, AttributeModifier.Operation op) {
        ResourceLocation id() {
            ResourceLocation attr = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value());
            String name = attr == null ? "unknown" : attr.getNamespace() + "." + attr.getPath();
            return IsekaiMahou.id("gear_plus." + source + "." + name.replace('/', '.') + "." + op.ordinal());
        }
    }

    /** O que está aplicado em cada jogador (para tirar o que deixou de valer). */
    private static final Map<UUID, Map<Key, Double>> APPLIED = new ConcurrentHashMap<>();
    /** Jogadores com recálculo pedido para o próximo tick (junta várias trocas de equipamento numa só). */
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

    static void register() {
        NeoForge.EVENT_BUS.addListener((LivingEquipmentChangeEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) DIRTY.add(sp.getUUID());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> fresh(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> fresh(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> DIRTY.add(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            APPLIED.remove(e.getEntity().getUUID());
            DIRTY.remove(e.getEntity().getUUID());
            GearNet.forget(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.LevelUp e) -> DIRTY.add(e.getPlayer().getUUID()));
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.PathChosen e) -> DIRTY.add(e.getPlayer().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            boolean periodic = (sp.tickCount + sp.getId()) % 100 == 0;
            if (DIRTY.remove(sp.getUUID()) || periodic) refresh(sp);
            if (periodic) GearNet.syncIfClassChanged(sp);
        });
    }

    /** Entidade nova (login/renascer): os modificadores temporários sumiram com a antiga. */
    private static void fresh(net.minecraft.world.entity.player.Player player) {
        APPLIED.remove(player.getUUID());
        DIRTY.add(player.getUUID());
        if (player instanceof ServerPlayer sp) GearNet.sync(sp);
    }

    /** Recalcula e aplica os bônus de conjunto e de gemas. */
    public static void refresh(ServerPlayer sp) {
        MagicData md = MagicApi.get(sp);
        Map<Key, Double> want = new HashMap<>();
        SetBonuses.Active set = SetBonuses.active(sp);
        if (set != null) {
            for (SetBonuses.Line line : SetBonuses.lines(set.family())) {
                want.merge(new Key("set", line.attribute(), line.op()), line.at(set.rank()), Double::sum);
            }
            // Todo mundo começa com o traje do kit completo: a dica espera o jogador se ambientar
            if (md.level() >= 3) Hints.show(sp, "gear_plus.set_bonus");
        }
        Gems.forEachActive(sp, md, (type, grade) ->
                want.merge(new Key("gem", type.attribute(), type.op), type.amount(grade), Double::sum));

        Map<Key, Double> had = APPLIED.getOrDefault(sp.getUUID(), Map.of());
        // Garantia: se a entidade foi recriada sem passar pelos eventos, o que "estava aplicado" já não existe
        for (Key k : had.keySet()) {
            AttributeInstance inst = sp.getAttribute(k.attribute());
            if (inst == null || !inst.hasModifier(k.id())) had = Map.of();
            break;
        }
        if (had.equals(want)) return;
        Set<Key> gone = new HashSet<>(had.keySet());
        gone.removeAll(want.keySet());
        for (Key k : gone) {
            AttributeInstance inst = sp.getAttribute(k.attribute());
            if (inst != null) inst.removeModifier(k.id());
        }
        for (Map.Entry<Key, Double> e : want.entrySet()) {
            AttributeInstance inst = sp.getAttribute(e.getKey().attribute());
            if (inst != null) inst.addOrUpdateTransientModifier(new AttributeModifier(e.getKey().id(), e.getValue(), e.getKey().op()));
        }
        if (want.isEmpty()) APPLIED.remove(sp.getUUID());
        else APPLIED.put(sp.getUUID(), want);
    }

    private GearBonuses() {}
}
