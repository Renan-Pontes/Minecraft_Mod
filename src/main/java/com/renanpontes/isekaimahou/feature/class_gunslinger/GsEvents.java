package com.renanpontes.isekaimahou.feature.class_gunslinger;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.class_gunslinger.duel.DuelService;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.ManaBulletEntity;
import com.renanpontes.isekaimahou.feature.class_gunslinger.glyph.GunTriggers;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.PathService;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.TriggerPath;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Ouvintes de jogo do módulo (servidor): tiro/recarga, bônus de classe, contadores do Caminho, traje e limpeza. */
public final class GsEvents {
    public static void register() {
        NeoForge.EVENT_BUS.addListener(GsEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(LivingSwapItemsEvent.Hands.class, GunCombat::onSwapHands);
        NeoForge.EVENT_BUS.addListener(SpellCastEvent.Pre.class, GunTriggers::onPre);
        NeoForge.EVENT_BUS.addListener(SpellCastEvent.Pre.class, DuelService::onCastPre);
        NeoForge.EVENT_BUS.addListener(SpellCastEvent.Post.class, GunCombat::onCastPost);
        NeoForge.EVENT_BUS.addListener(GsEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(GsEvents::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(GsEvents::onPathChosen);
        NeoForge.EVENT_BUS.addListener(GsEvents::onCircle);
        NeoForge.EVENT_BUS.addListener(GsEvents::onLevelUp);
        NeoForge.EVENT_BUS.addListener(GsEvents::onCounter);
        NeoForge.EVENT_BUS.addListener(GsEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(GsEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(GsEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(GsEvents::onStopped);
        // v1.0 — técnicas Gun Gale (defesa da espada, Linha de Bala, Camuflagem)
        NeoForge.EVENT_BUS.addListener(GgoState::onServerTick);
        NeoForge.EVENT_BUS.addListener(GgoState::onIncoming);
        NeoForge.EVENT_BUS.addListener(GgoState::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(GgoState::onChangeTarget);
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        GunCombat.tick(sp);
        PathService.tick(sp);
    }

    /** Abate com bala de mana (tiro básico ou forma de projétil): conta para o Caminho; de longe vale mais. */
    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getDirectEntity() instanceof ManaBulletEntity bullet)) return;
        if (!(bullet.getOwner() instanceof ServerPlayer sp) || sp instanceof FakePlayer || e.getEntity() == sp) return;
        countKill(sp, e.getEntity());
    }

    /** Abates das formas instantâneas (Bala Perfurante, Doze Badaladas), que não usam a entidade da bala. */
    private static void onSpellDamage(SpellDamageEvent.Post e) {
        if (!e.killed() || e.direct() instanceof ManaBulletEntity) return;
        if (!(e.ctx().caster() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        if (e.ctx().spell().form().origin() != MagicPath.GUNSLINGER) return;
        countKill(sp, e.target());
    }

    private static void countKill(ServerPlayer sp, LivingEntity victim) {
        MagicApi.addCounter(sp, TriggerPath.KILLS, 1);
        double dist = sp.distanceTo((Entity) victim);
        if (dist >= TriggerPath.LONG_SHOT) {
            MagicApi.addCounter(sp, TriggerPath.LONGSHOTS, 1);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.long_shot", (int) dist).withColor(0x9AF0B0), true);
        }
    }

    private static void onPathChosen(MagicProgressEvent.PathChosen e) {
        PathService.ensureStarter(e.getPlayer());
        PathService.grantOutfit(e.getPlayer());
    }

    private static void onCircle(MagicProgressEvent.CircleChanged e) {
        if (e.newCircle > e.oldCircle) PathService.grantOutfit(e.getPlayer());
        PathService.checkReady(e.getPlayer());
    }

    private static void onLevelUp(MagicProgressEvent.LevelUp e) {
        PathService.checkReady(e.getPlayer());
    }

    private static void onCounter(MagicProgressEvent.Counter e) {
        if (e.key.startsWith("gunslinger.") && MagicApi.get(e.getPlayer()).path() == MagicPath.GUNSLINGER) {
            PathService.checkReady(e.getPlayer());
            if (e.key.equals(GsTechniques.PHOTON_HITS) || e.key.equals(GsTechniques.CAMO_SHOTS)) GsTechniques.check(e.getPlayer());
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) PathService.scheduleLoginCheck(sp);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        var id = e.getEntity().getUUID();
        GunCombat.forget(id);
        PathService.forget(id);
        DuelService.forget(id);
        GgoState.forget(id);
        com.renanpontes.isekaimahou.feature.class_gunslinger.glyph.GgoForms.AimCircleForm.forget(id);
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 100 == 0) GunMarks.prune(e.getServer().overworld().getGameTime());
    }

    private static void onStopped(ServerStoppedEvent e) {
        GunCombat.clear();
        PathService.clear();
        DuelService.clear();
        GunMarks.clear();
        GunTriggers.clear();
        GgoState.clear();
        com.renanpontes.isekaimahou.feature.class_gunslinger.glyph.GgoForms.AimCircleForm.clear();
    }

    private GsEvents() {}
}
