package com.renanpontes.isekaimahou.feature.bosses_b;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.AbyssSovereign;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.BbBoss;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.Seraph;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.TwilightHerald;
import com.renanpontes.isekaimahou.feature.bosses_b.item.TwilightHeartItem;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Modulo "bosses_b": os chefes de Luz, Sombra e o chefe final.
 * <ul>
 *   <li>{@code abyss_sovereign} — Soberano do Abismo (deixa guardian_core_shadow);</li>
 *   <li>{@code seraph} — Serafim de seis asas (deixa guardian_core_light);</li>
 *   <li>{@code twilight_herald} — Arauto do Crepúsculo, chefe final com Expansão de Domínio
 *       (deixa twilight_heart e o troféu twilight_herald_trophy).</li>
 * </ul>
 * Todos: GeckoLib, barra de chefe, fases, cutscenes boss_intro/boss_phase, falas STYLE_BOSS, magias de glifo por id
 * (castAsMob / castFrom pela linha telegrafada) com ataque próprio quando o glifo não existe e /mahou spawnboss [trial].
 * v0.7: todo golpe em área/linha é telegrafado no chão (core/boss/Telegraph), escala de grupo na apresentação,
 * voadores pousam exaustos depois de golpes grandes (janela de corpo a corpo), anti-trapaça (salto/teleporte),
 * modo Provação (BossKit.TRIAL_TAG: sem reset, a arena é a casa) e recompensa de grupo (BossRewards: XP + boss.&lt;id&gt;).
 */
public class BossesBModule implements Module {
    @Override
    public String id() {
        return "bosses_b";
    }

    @Override
    public void init(IEventBus modBus) {
        BbRegistries.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTabs);
        BbCommands.register();
        // Bônus permanentes do Coração do Crepúsculo sobrevivem à morte (o vanilla descarta modificadores ao renascer).
        NeoForge.EVENT_BUS.addListener(TwilightHeartItem::onRespawn);
        NeoForge.EVENT_BUS.addListener(TwilightHeartItem::onLogin);
        // v1.0: o chefe provoca quando derruba um jogador (a fala tem trava no próprio chefe)
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer && e.getSource().getEntity() instanceof BbBoss b) b.onPlayerSlain();
        });

        // Resistências elementais: cada guardião resiste ao próprio elemento e é fraco ao oposto.
        ElementReactions.setMultiplier(IsekaiMahou.id("abyss_sovereign"), Element.SHADOW, 0.25F);
        ElementReactions.setMultiplier(IsekaiMahou.id("abyss_sovereign"), Element.LIGHT, 1.75F);
        ElementReactions.setMultiplier(IsekaiMahou.id("seraph"), Element.LIGHT, 0.25F);
        ElementReactions.setMultiplier(IsekaiMahou.id("seraph"), Element.SHADOW, 1.75F);
        // O Arauto troca de fraqueza conforme a postura (TwilightHerald#modifyIncomingDamage).
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(BbRegistries.ABYSS_SOVEREIGN.get(), AbyssSovereign.createAttributes().build());
        e.put(BbRegistries.SERAPH.get(), Seraph.createAttributes().build());
        e.put(BbRegistries.TWILIGHT_HERALD.get(), TwilightHerald.createAttributes().build());
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(BbRegistries.ABYSS_SOVEREIGN_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            e.accept(BbRegistries.SERAPH_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            e.accept(BbRegistries.TWILIGHT_HERALD_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } else if (e.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            e.accept(BbRegistries.TWILIGHT_HEART.get());
        } else if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            e.accept(BbRegistries.HERALD_TROPHY_ITEM.get());
        }
    }
}
