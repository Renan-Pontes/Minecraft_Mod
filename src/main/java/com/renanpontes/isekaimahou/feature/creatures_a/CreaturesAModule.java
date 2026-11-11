package com.renanpontes.isekaimahou.feature.creatures_a;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.CaBossCommands;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.Goblin;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.GoblinArcher;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.GoblinKing;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.GoblinShaman;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.GoblinWolfRider;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.Hobgoblin;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.slime.KingSlime;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.slime.MahouSlime;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;

/**
 * Modulo "creatures_a": Slimes e Goblins (+ Rei Slime, minichefe, e Rei Goblin, chefe da fortaleza goblin).
 *
 * Slimes usam o sistema de tamanhos do slime vanilla (grandes se dividem) com modelos GeckoLib e comportamento
 * por variedade; goblins são humanoides em bando que roubam itens e fogem feridos. Spawns por biome modifier,
 * loot tables em data/isekai_mahou/loot_table/entities/, resistências elementais pelo núcleo.
 */
public class CreaturesAModule implements Module {
    @Override
    public String id() {
        return "creatures_a";
    }

    @Override
    public void init(IEventBus modBus) {
        CaRegistry.register(modBus);
        modBus.addListener(this::onAttributes);
        modBus.addListener((RegisterSpawnPlacementsEvent e) -> CaSpawns.register(e));
        modBus.addListener(this::onCreativeTabs);
        NeoForge.EVENT_BUS.addListener(this::onMobSplit);
        NeoForge.EVENT_BUS.addListener(this::onChangeTarget);
        CaBossCommands.register();
        registerResistances();
    }

    private void onAttributes(EntityAttributeCreationEvent e) {
        var slime = MahouSlime.createAttributes().build();
        e.put(CaRegistry.SLIME.get(), slime);
        e.put(CaRegistry.FIRE_SLIME.get(), slime);
        e.put(CaRegistry.WATER_SLIME.get(), slime);
        e.put(CaRegistry.STONE_SLIME.get(), slime);
        e.put(CaRegistry.WIND_SLIME.get(), slime);
        e.put(CaRegistry.POISON_JELLY.get(), slime);
        e.put(CaRegistry.KING_SLIME.get(), MahouSlime.createAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, 32.0).build());
        e.put(CaRegistry.GOBLIN.get(), Goblin.createAttributes().build());
        e.put(CaRegistry.GOBLIN_ARCHER.get(), GoblinArcher.createAttributes().build());
        e.put(CaRegistry.GOBLIN_SHAMAN.get(), GoblinShaman.createAttributes().build());
        e.put(CaRegistry.GOBLIN_WOLF_RIDER.get(), GoblinWolfRider.createAttributes().build());
        e.put(CaRegistry.HOBGOBLIN.get(), Hobgoblin.createAttributes().build());
        e.put(CaRegistry.GOBLIN_KING.get(), GoblinKing.createAttributes().build());
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_CREATURES.getKey()) return;
        CaRegistry.EGGS.forEach(i -> e.accept(i.get()));
        CaRegistry.DROPS.forEach(i -> e.accept(i.get()));
    }

    /** O Rei Slime cuida da própria divisão (fases); a divisão vanilla criaria vários reis. */
    private void onMobSplit(MobSplitEvent e) {
        if (e.getParent() instanceof KingSlime) e.setCanceled(true);
    }

    /** O slime azul é neutro: golens (e outros mobs) não o caçam enquanto ele não brigar com ninguém. */
    private void onChangeTarget(LivingChangeTargetEvent e) {
        if (e.getNewAboutToBeSetTarget() instanceof MahouSlime slime && slime.isNeutral() && slime.getTarget() == null
                && !(e.getEntity() instanceof Player) && e.getEntity().getLastHurtByMob() != slime) {
            e.setCanceled(true);
        }
    }

    /** Resistências/fraquezas elementais (as imunidades que conflitam com fusões ficam na entidade). */
    private static void registerResistances() {
        set("slime", Element.LIGHTNING, 1.5F);
        set("fire_slime", Element.WATER, 2.0F);
        set("fire_slime", Element.ICE, 1.25F);
        set("water_slime", Element.LIGHTNING, 2.0F);
        set("water_slime", Element.ICE, 2.0F);
        set("stone_slime", Element.WIND, 2.0F);
        set("stone_slime", Element.EARTH, 0.5F);
        set("wind_slime", Element.EARTH, 2.0F);
        set("wind_slime", Element.WIND, 0.5F);
        set("poison_jelly", Element.LIGHT, 1.5F);
        set("poison_jelly", Element.SHADOW, 0.5F);
        set("king_slime", Element.LIGHTNING, 1.5F);
        set("king_slime", Element.WATER, 0.75F);
        set("goblin_shaman", Element.FIRE, 0.75F);
        set("goblin_wolf_rider", Element.FIRE, 1.25F);
        set("hobgoblin", Element.EARTH, 0.75F);
        set("goblin_king", Element.SHADOW, 0.75F);
        set("goblin_king", Element.LIGHT, 1.25F);
    }

    private static void set(String entity, Element element, float mult) {
        ElementReactions.setMultiplier(IsekaiMahou.id(entity), element, mult);
    }
}
