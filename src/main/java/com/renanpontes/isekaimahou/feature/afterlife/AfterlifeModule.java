package com.renanpontes.isekaimahou.feature.afterlife;

import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "afterlife" (v1.1.1): À Beira da Morte (voltar ao corpo fugindo dos Ceifadores e acertando as batidas do
 * coração), Carbonizado (morte pelo fogo: sem desafio) e o Corpo com os itens no local da morte.
 * Com a gamerule keepInventory ligada, nada disto acontece.
 */
public class AfterlifeModule implements Module {
    @Override
    public String id() {
        return "afterlife";
    }

    @Override
    public void init(IEventBus modBus) {
        AfterlifeConfig.register();
        AfterlifeContent.register(modBus);
        AfterlifeNet.register(modBus);

        var bus = NeoForge.EVENT_BUS;
        // dano/morte: por último, respeitando quem cancelou antes (totens, duelos, outros mods)
        bus.addListener(EventPriority.LOWEST, (LivingDamageEvent.Pre e) -> AfterlifeManager.beforeDamage(e.getEntity()));
        bus.addListener(EventPriority.LOWEST, (LivingDamageEvent.Post e) -> AfterlifeManager.afterDamage(e.getEntity(), e.getNewDamage()));
        bus.addListener(EventPriority.LOWEST, (LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && AfterlifeManager.onDeath(sp, e.getSource())) e.setCanceled(true);
        });
        bus.addListener(EventPriority.LOWEST, (LivingDropsEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) AfterlifeManager.onDrops(sp, e.getSource(), e.getDrops());
        });

        // a alma não leva dano (só /kill e o vazio), não causa dano e não é alvo
        bus.addListener(EventPriority.HIGHEST, (LivingIncomingDamageEvent e) -> {
            if (e.getEntity() instanceof Player p && AfterlifeManager.isSpirit(p) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                e.setCanceled(true);
            } else if (e.getSource().getEntity() instanceof Player p && AfterlifeManager.isSpirit(p)) {
                e.setCanceled(true);
            }
        });
        bus.addListener((LivingChangeTargetEvent e) -> {
            if (e.getNewAboutToBeSetTarget() instanceof Player p && AfterlifeManager.isSpirit(p)) e.setNewAboutToBeSetTarget(null);
        });

        // a alma não mexe em nada
        bus.addListener((AttackEntityEvent e) -> {
            if (AfterlifeManager.isSpirit(e.getEntity())) e.setCanceled(true);
        });
        bus.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (AfterlifeManager.isSpirit(e.getEntity())) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener((PlayerInteractEvent.RightClickItem e) -> {
            if (AfterlifeManager.isSpirit(e.getEntity())) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener((PlayerInteractEvent.EntityInteract e) -> {
            if (AfterlifeManager.isSpirit(e.getEntity())) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener((PlayerInteractEvent.EntityInteractSpecific e) -> {
            if (AfterlifeManager.isSpirit(e.getEntity())) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (AfterlifeManager.isSpirit(e.getEntity())) e.setCanceled(true);
        });
        bus.addListener((BlockEvent.BreakEvent e) -> {
            if (AfterlifeManager.isSpirit(e.getPlayer())) e.setCanceled(true);
        });
        bus.addListener((BlockEvent.EntityPlaceEvent e) -> {
            if (e.getEntity() instanceof Player p && AfterlifeManager.isSpirit(p)) e.setCanceled(true);
        });
        bus.addListener((ItemEntityPickupEvent.Pre e) -> {
            if (AfterlifeManager.isSpirit(e.getPlayer())) e.setCanPickup(TriState.FALSE);
        });
        bus.addListener((PlayerXpEvent.PickupXp e) -> {
            if (AfterlifeManager.isSpirit(e.getEntity())) e.setCanceled(true);
        });
        bus.addListener((EntityTravelToDimensionEvent e) -> {
            if (e.getEntity() instanceof Player p && AfterlifeManager.isSpirit(p)) e.setCanceled(true);
        });
        bus.addListener((SpellCastEvent.Pre e) -> {
            if (e.getCaster() instanceof Player p && AfterlifeManager.isSpirit(p)) e.setCanceled(true);
        });

        // ciclo
        bus.addListener((ServerTickEvent.Post e) -> AfterlifeManager.tick(e.getServer()));
        bus.addListener((PlayerTickEvent.Post e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 10 == 0) AfterlifeManager.trackSafeGround(sp);
            if (sp.tickCount % 40 == 0) CorpseIndex.pruneNear(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) AfterlifeManager.onLogin(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) AfterlifeManager.onLogout(sp);
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) CorpseIndex.sendMark(sp);
        });
        bus.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getEntity() instanceof ServerPlayer viewer) AfterlifeManager.onStartTracking(viewer, e.getTarget());
        });
        bus.addListener((ServerStoppingEvent e) -> AfterlifeManager.onServerStopping(e.getServer()));

        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("afterlife")
                .then(MahouCommands.op("test").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    boolean ok = AfterlifeManager.test(sp);
                    c.getSource().sendSuccess(() -> Component.translatable(ok ? "commands.isekai_mahou.afterlife.test"
                            : "commands.isekai_mahou.afterlife.test_fail"), false);
                    return ok ? 1 : 0;
                }))));
    }
}
