package com.renanpontes.isekaimahou.feature.class_ranger;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerEagleEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerSteedEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerWolfEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.ChainTetherEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.glyph.AnimeArrowEffects;
import com.renanpontes.isekaimahou.feature.class_ranger.glyph.ArrowEffects;
import com.renanpontes.isekaimahou.feature.class_ranger.glyph.RainArrows;
import com.renanpontes.isekaimahou.feature.class_ranger.glyph.RangerGlyphs;
import com.renanpontes.isekaimahou.feature.class_ranger.item.RangerOutfitTier;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "class_ranger" (v1.0) — RANGER: o arqueiro clássico das trilhas, nunca sozinho. Companheiros que evoluem
 * (Águia Batedora que marca alvos, Lobo que luta, Corcel-montaria), Arco do Ranger (kit + 4 graus), Traje Selvagem
 * (4 graus GeckoLib), 10 glifos de origem de flecha/caçada + a forma G "Tempestade das Mil Penas", a Trilha do Ranger
 * (5 ranks, {@code ritual_rank}), bônus de classe e 2 Lendas. Tudo montável no Grimório.
 */
public class ClassRangerModule implements Module {
    @Override
    public String id() {
        return "class_ranger";
    }

    @Override
    public void init(IEventBus modBus) {
        RangerRegistries.register(modBus);
        modBus.addListener(RangerFxNet::register);
        modBus.addListener(ClassRangerModule::attributes);
        modBus.addListener(ClassRangerModule::creativeTabs);
        modBus.addListener(RangerNet::register);
        modBus.addListener(RangerGlyphs::validate);
        RangerGlyphs.register();
        RangerCommands.register();

        // Kit: arco do Ranger + flechas + Traje Selvagem (grau I) — substitui o traje antigo do outfits
        ClassKits.addItem(MagicPath.RANGER, () -> new ItemStack(RangerRegistries.BOW.get()));
        ClassKits.addItem(MagicPath.RANGER, () -> new ItemStack(Items.ARROW, 48));
        for (ArmorItem.Type type : RangerOutfitTier.TYPES) {
            ClassKits.setArmor(MagicPath.RANGER, type.getSlot(), () -> new ItemStack(RangerRegistries.piece(RangerOutfitTier.SCOUT, type).get()));
        }

        // A Águia é do vento; o Lobo e o Corcel temem o fogo (afinidade difícil do Ranger)
        ElementReactions.setMultiplier(IsekaiMahou.id(RangerEagleEntity.ID), Element.WIND, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id(RangerWolfEntity.ID), Element.FIRE, 1.25F);
        ElementReactions.setMultiplier(IsekaiMahou.id(RangerSteedEntity.ID), Element.FIRE, 1.25F);

        NeoForge.EVENT_BUS.addListener(RangerPassives::onJoin);
        NeoForge.EVENT_BUS.addListener(RangerArrowFx::onJoin);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RangerPassives::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(RangerPassives::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RainArrows::onImpact);
        NeoForge.EVENT_BUS.addListener(ClassRangerModule::onPathChosen);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ClassRangerModule::onCircleEarly);
        NeoForge.EVENT_BUS.addListener(ClassRangerModule::onCircle);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ClassRangerModule::onLogin);
        NeoForge.EVENT_BUS.addListener(ClassRangerModule::onRespawn);
        NeoForge.EVENT_BUS.addListener(ClassRangerModule::onClone);
        NeoForge.EVENT_BUS.addListener(ClassRangerModule::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            RangerMarks.tick(e.getServer());
            RainArrows.tick(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            RangerMarks.reset();
            RainArrows.clear();
            ArrowEffects.reset();
            AnimeArrowEffects.SacredArrow.reset();
            ChainTetherEntity.clearAll();
        });
        // Correntes Conjuradas: quem está preso não se teletransporta (endermen, pérolas, frutas do coro)
        NeoForge.EVENT_BUS.addListener((EntityTeleportEvent.EnderEntity e) -> {
            if (ChainTetherEntity.isBound(e.getEntityLiving())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((EntityTeleportEvent.EnderPearl e) -> {
            if (ChainTetherEntity.isBound(e.getPlayer())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((EntityTeleportEvent.ChorusFruit e) -> {
            if (ChainTetherEntity.isBound(e.getEntityLiving())) e.setCanceled(true);
        });
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(RangerRegistries.WOLF.get(), RangerWolfEntity.createAttributes().build());
        e.put(RangerRegistries.EAGLE.get(), RangerEagleEntity.createAttributes().build());
        e.put(RangerRegistries.STEED.get(), RangerSteedEntity.createAttributes().build());
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(RangerRegistries.WOLF_EGG.get());
            e.accept(RangerRegistries.EAGLE_EGG.get());
            e.accept(RangerRegistries.STEED_EGG.get());
        } else if (e.getTabKey() == CoreRegistries.TAB_RITUALS.getKey()) {
            e.accept(RangerRegistries.BOW.get());
            RangerRegistries.BOW_TIERS.forEach(b -> e.accept(b.get()));
            for (RangerOutfitTier tier : RangerOutfitTier.values()) {
                for (ArmorItem.Type type : RangerOutfitTier.TYPES) e.accept(RangerRegistries.piece(tier, type).get());
            }
        }
    }

    // =====================================================================================
    //  Eventos de jogador
    // =====================================================================================

    private static void onPathChosen(MagicProgressEvent.PathChosen e) {
        RangerService.onClassChosen(e.getPlayer());
        RangerPassives.updateLightFeet(e.getPlayer());
    }

    /** Antes do módulo de roupas: marca as flags do traje antigo para ele não entregar a versão da v0.9. */
    private static void onCircleEarly(MagicProgressEvent.CircleChanged e) {
        RangerService.claimOutfitFlags(MagicApi.get(e.getPlayer()));
    }

    private static void onCircle(MagicProgressEvent.CircleChanged e) {
        if (e.newCircle <= e.oldCircle) return;
        RangerService.grantOutfits(e.getPlayer());
        RangerCompanions.refreshLevel(e.getPlayer());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        RangerService.onLogin(sp);
        RangerPassives.updateLightFeet(sp);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        RangerPassives.updateLightFeet(sp);
    }

    private static void onClone(PlayerEvent.Clone e) {
        RangerCompanions.copyOnClone(e.getOriginal(), e.getEntity());
    }

    /**
     * A cada 5 s: Pés Leves acompanha a classe (troca por comando, reencarnação). 3 s depois de entrar/renascer: entrega o
     * traje pendente (o cliente já está pronto para o toast).
     */
    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 100 == 0) RangerPassives.updateLightFeet(sp);
        if (sp.tickCount == 60) RangerService.grantOutfits(sp);
    }
}
