package com.renanpontes.isekaimahou.feature.trials;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Ouvintes de jogo do módulo: ciclo do servidor, "Abatido" no lugar da morte, proteção do Reino (blocos, explosões,
 * pérolas), criaturas das instâncias, entrada/saída de jogadores e avisos de Provação disponível.
 */
final class TrialEvents {
    /** persistentData que o módulo bosses_d grava nos lacaios do Avatar (referência só por texto). */
    private static final String BOSS_MINION_KEY = "isekai_mahou.bosses_d_master";

    static void register() {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post e) -> TrialManager.tick());
        bus.addListener((ServerStartedEvent e) -> TrialManager.onServerStarted(e.getServer()));
        bus.addListener((ServerStoppingEvent e) -> TrialManager.onServerStopping());
        bus.addListener((ServerStoppedEvent e) -> TrialManager.onServerStopped());

        // Alta prioridade: a morte do participante vira "Abatido" antes de outros módulos contarem a morte.
        bus.addListener(EventPriority.HIGH, LivingDeathEvent.class, TrialEvents::onPlayerDeath);
        bus.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, TrialEvents::onTrackedDeath);
        bus.addListener(LivingIncomingDamageEvent.class, TrialEvents::onIncomingDamage);

        bus.addListener(BlockEvent.BreakEvent.class, TrialEvents::onBreak);
        bus.addListener(BlockEvent.EntityPlaceEvent.class, TrialEvents::onPlace);
        bus.addListener(PlayerInteractEvent.RightClickItem.class, TrialEvents::onUseItem);
        bus.addListener(PlayerInteractEvent.RightClickBlock.class, TrialEvents::onUseOnBlock);
        bus.addListener(BlockEvent.PortalSpawnEvent.class, e -> {
            if (inRealm(e.getLevel())) e.setCanceled(true);
        });
        bus.addListener(ExplosionEvent.Detonate.class, e -> {
            if (TrialDimension.is(e.getLevel())) e.getAffectedBlocks().clear();
        });
        bus.addListener(EntityMobGriefingEvent.class, e -> {
            if (TrialDimension.is(e.getEntity().level())) e.setCanGrief(false);
        });
        bus.addListener(LivingDestroyBlockEvent.class, e -> {
            if (TrialDimension.is(e.getEntity().level())) e.setCanceled(true);
        });
        bus.addListener(EntityTeleportEvent.EnderPearl.class, e -> {
            if (!allowedTeleport(e.getPlayer(), e.getTarget())) e.setCanceled(true);
        });
        bus.addListener(EntityTeleportEvent.ChorusFruit.class, e -> {
            if (e.getEntityLiving() instanceof ServerPlayer sp && !allowedTeleport(sp, e.getTarget())) e.setCanceled(true);
        });
        bus.addListener(EntityJoinLevelEvent.class, TrialEvents::onJoin);
        bus.addListener(MobSplitEvent.class, TrialEvents::onSplit);

        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, TrialEvents::onLogin);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, TrialEvents::onLogout);
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, TrialEvents::onRespawn);
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, TrialEvents::onChangeDimension);

        // "Nova Provação disponível": quando o nível do elemento, o Círculo ou uma conclusão mudam.
        bus.addListener(MagicProgressEvent.ElementXp.class, e -> {
            if (e.newLevel > e.oldLevel) TrialManager.later(20, () -> notify(e.getPlayer(), e.element));
        });
        bus.addListener(MagicProgressEvent.CircleChanged.class, e -> {
            if (e.newCircle > e.oldCircle) TrialManager.later(40, () -> notify(e.getPlayer(), null));
        });
        bus.addListener(MagicProgressEvent.ElementAwakened.class, e -> TrialManager.later(40, () -> notify(e.getPlayer(), e.element)));
        bus.addListener(MagicProgressEvent.Counter.class, e -> {
            if (e.key.startsWith("trial.clear.")) TrialManager.later(100, () -> notify(e.getPlayer(), null));
        });
    }

    /** Roda depois de alguns ticks: busca o jogador de novo pelo UUID (renascer troca o objeto ServerPlayer). */
    static void notify(ServerPlayer p, @javax.annotation.Nullable com.renanpontes.isekaimahou.core.Element only) {
        ServerPlayer now = p.server.getPlayerList().getPlayer(p.getUUID());
        if (now != null && !now.hasDisconnected()) TrialNotifier.check(now, only);
    }

    // ------------------------------------------------------------------------------------------------ morte / dano
    private static void onPlayerDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !TrialDimension.is(sp.level())) return;
        TrialInstance inst = TrialManager.of(sp.getUUID());
        if (inst != null && inst.onFatal(sp)) e.setCanceled(true);
    }

    private static void onTrackedDeath(LivingDeathEvent e) {
        Entity en = e.getEntity();
        if (en.level().isClientSide || !en.getPersistentData().hasUUID(TrialInstance.TAG)) return;
        TrialInstance inst = TrialManager.byId(en.getPersistentData().getUUID(TrialInstance.TAG));
        if (inst != null) inst.onTrackedDeath(en);
    }

    /** Recepção, contagem, intro, vitória e derrota são seguras (só /kill e o vazio passam). */
    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !TrialDimension.is(sp.level())) return;
        TrialInstance inst = TrialManager.of(sp.getUUID());
        if (inst == null || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        TrialInstance.Phase ph = inst.phase();
        if (ph == TrialInstance.Phase.LOBBY || ph == TrialInstance.Phase.COUNTDOWN || ph == TrialInstance.Phase.INTRO
                || ph == TrialInstance.Phase.VICTORY || ph == TrialInstance.Phase.DEFEAT) {
            e.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------------------------------------ proteção
    private static boolean inRealm(LevelAccessor level) {
        return level instanceof Level l && TrialDimension.is(l);
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!inRealm(e.getLevel()) || e.getPlayer().isCreative()) return;
        e.setCanceled(true);
        e.getPlayer().displayClientMessage(Component.translatable("message.isekai_mahou.trials.protected").withStyle(ChatFormatting.RED), true);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!inRealm(e.getLevel())) return;
        if (e.getEntity() instanceof Player p && p.isCreative()) return;
        e.setCanceled(true);
        if (e.getEntity() instanceof Player p) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.trials.protected").withStyle(ChatFormatting.RED), true);
        }
    }

    /** Baldes despejam fluido sem passar pelo evento de colocar bloco: bloqueados no Reino. */
    private static void onUseItem(PlayerInteractEvent.RightClickItem e) {
        if (!TrialDimension.is(e.getLevel()) || e.getEntity().isCreative()) return;
        if (e.getItemStack().getItem() instanceof BucketItem) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            e.getEntity().displayClientMessage(Component.translatable("message.isekai_mahou.trials.protected").withStyle(ChatFormatting.RED), true);
        }
    }

    /** Isqueiro e carga de fogo acendem fogo direto (sem o evento de colocar bloco): o tapete da recepção queimaria. */
    private static void onUseOnBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!TrialDimension.is(e.getLevel()) || e.getEntity().isCreative()) return;
        if (e.getItemStack().getItem() instanceof FlintAndSteelItem || e.getItemStack().getItem() instanceof FireChargeItem) {
            e.setUseItem(TriState.FALSE);
            e.getEntity().displayClientMessage(Component.translatable("message.isekai_mahou.trials.protected").withStyle(ChatFormatting.RED), true);
        }
    }

    /** Pérola/coro: só dentro da área da própria instância (nada de pular a barreira). */
    private static boolean allowedTeleport(ServerPlayer p, Vec3 target) {
        if (!TrialDimension.is(p.level()) || p.isCreative()) return true;
        TrialInstance inst = TrialManager.of(p.getUUID());
        return inst != null && inst.allows(target);
    }

    // ------------------------------------------------------------------------------------------------ entidades
    private static void onJoin(EntityJoinLevelEvent e) {
        Level level = e.getLevel();
        if (level.isClientSide) return;
        Entity en = e.getEntity();
        CompoundTag pd = en.getPersistentData();
        if (pd.hasUUID(TrialInstance.TAG)) {
            // Instância que não existe mais (reinício no meio da luta): a criatura/painel não volta ao mundo.
            if (TrialManager.byId(pd.getUUID(TrialInstance.TAG)) == null) e.setCanceled(true);
            return;
        }
        if (!TrialDimension.is(level) || e.loadedFromDisk() || !(en instanceof Mob mob)) return;
        // Criaturas invocadas por chefes/xamãs dentro da arena entram na luta (e somem no fim). Lacaios dos chefes de
        // provação contam mesmo sem ser "Enemy" (elementais neutros); invocações de jogadores nunca.
        boolean bossMinion = pd.contains(BOSS_MINION_KEY);
        if (!(en instanceof Enemy) && !bossMinion) return;
        if (en instanceof OwnableEntity own && own.getOwnerUUID() != null && level.getServer() != null
                && level.getServer().getPlayerList().getPlayer(own.getOwnerUUID()) != null) return;
        int index = TrialDimension.indexAt(en.getX());
        for (TrialInstance inst : TrialManager.all()) {
            if (inst.index == index && inst.phase().arena()) {
                inst.adopt(mob);
                return;
            }
        }
    }

    /** Slimes das Câmaras não se dividem (os pedaços não contariam na onda). */
    private static void onSplit(MobSplitEvent e) {
        Mob parent = e.getParent();
        if (!parent.getPersistentData().hasUUID(TrialInstance.TAG)) return;
        UUID id = parent.getPersistentData().getUUID(TrialInstance.TAG);
        TrialInstance inst = TrialManager.byId(id);
        if (inst == null) return;
        e.setCanceled(true);
        if (parent.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.POOF, parent.getX(), parent.getY() + 0.4, parent.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            ElementFx.burst(sl, parent.position().add(0, 0.4, 0), inst.element, 10, 0.4);
        }
    }

    // ------------------------------------------------------------------------------------------------ jogadores
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (TrialDimension.is(sp.level())) TrialManager.later(2, () -> TrialManager.evacuate(sp));
        TrialManager.later(100, () -> notify(sp, null));
    }

    /** Sair do jogo no meio = sair do grupo (quem estava abatido volta ao modo normal antes de ser salvo). */
    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        TrialInstance inst = TrialManager.of(sp.getUUID());
        if (inst != null) inst.removeMember(sp.getUUID(), TrialInstance.Leave.LOGOUT);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || TrialDimension.is(sp.level())) return;
        TrialInstance inst = TrialManager.of(sp.getUUID());
        if (inst != null) inst.removeMember(sp.getUUID(), TrialInstance.Leave.DIMENSION);
        TrialManager.data().takeReturn(sp.getUUID());
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        boolean fromRealm = e.getFrom() == TrialDimension.KEY, toRealm = e.getTo() == TrialDimension.KEY;
        if (fromRealm && !toRealm) {
            // Saiu por outro caminho (comando, teleporte de outro mod, menu do espectador): sai do grupo.
            TrialInstance inst = TrialManager.of(sp.getUUID());
            if (inst != null) inst.removeMember(sp.getUUID(), TrialInstance.Leave.DIMENSION);
            TrialSavedData.ReturnPoint rp = TrialManager.data().takeReturn(sp.getUUID());
            if (rp != null && rp.prevMode() >= 0 && sp.isSpectator()) {
                sp.setGameMode(net.minecraft.world.level.GameType.byId(rp.prevMode()));
            }
        } else if (toRealm && !fromRealm && TrialManager.of(sp.getUUID()) == null && !sp.hasPermissions(2) && !sp.isSpectator()) {
            TrialManager.later(1, () -> TrialManager.evacuate(sp));
        }
    }

    private TrialEvents() {}
}
