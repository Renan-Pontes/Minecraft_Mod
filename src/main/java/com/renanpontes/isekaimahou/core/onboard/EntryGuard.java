package com.renanpontes.isekaimahou.core.onboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * v1.0 — proteção da entrada no jogo. Enquanto o jogador não terminou de criar o personagem (sem classe, ou com a
 * classe escolhida e uma etapa de criação — a raça — por fazer) ele:
 * <ul>
 *   <li>não leva dano (só o vazio e {@code /kill} passam) e não causa dano;</li>
 *   <li>não é alvo de mobs;</li>
 *   <li>tem a tela certa REABERTA se algo a fechou (tela de morte, Esc, outro mod): a cada 2 s o servidor manda um
 *       "cutucão" ({@link OnboardPayloads.EntryNudge}); o cliente só reage se estiver sem tela nenhuma.</li>
 * </ul>
 * Antes: morrer com a tela de classe aberta deixava o jogador "Sem Classe" e, ao renascer, abria a tela de RAÇA sem
 * classe (a etapa de criação contava como pendente mesmo sem classe — corrigido em {@code OnboardService.pending}).
 * Só servidor; nada é salvo (o estado vem das flags do {@link MagicData}).
 */
public final class EntryGuard {
    /** Intervalo do cutucão (ticks). */
    private static final int NUDGE_EVERY = 40;
    /** Intervalo mínimo entre dois pedidos de retomada aceitos do mesmo cliente (ticks do servidor). */
    private static final int RESUME_GAP = 20;

    private static final Map<UUID, Integer> LAST_RESUME = new HashMap<>();

    static void init(IEventBus bus) {
        // Antes de todo mundo: golpe em quem está na entrada nem chega aos outros módulos
        bus.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, EntryGuard::onDamage);
        bus.addListener(LivingChangeTargetEvent.class, EntryGuard::onTarget);
        bus.addListener(PlayerTickEvent.Post.class, EntryGuard::onTick);
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, EntryGuard::onRespawn);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> LAST_RESUME.remove(e.getEntity().getUUID()));
    }

    static void clear() {
        LAST_RESUME.clear();
    }

    /** O jogador ainda está criando o personagem e por isso está protegido (servidor; criativo/espectador não contam). */
    public static boolean isProtected(Player p) {
        if (!(p instanceof ServerPlayer sp) || sp instanceof FakePlayer || sp.isCreative() || sp.isSpectator()) return false;
        return OnboardService.inEntry(sp);
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    private static void onDamage(LivingIncomingDamageEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        // O vazio e /kill sempre passam (senão o jogador ficaria preso caindo para sempre)
        if (e.getEntity() instanceof ServerPlayer victim && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && isProtected(victim)) {
            e.setCanceled(true);
            victim.clearFire();
            return;
        }
        // Protegido também não fere ninguém (não vira um jeito de lutar sem risco)
        if (e.getSource().getEntity() instanceof ServerPlayer attacker && attacker != e.getEntity() && isProtected(attacker)) {
            e.setCanceled(true);
        }
    }

    private static void onTarget(LivingChangeTargetEvent e) {
        if (e.getNewAboutToBeSetTarget() instanceof ServerPlayer sp && isProtected(sp)) e.setNewAboutToBeSetTarget(null);
    }

    /** A cada 2 s: cutuca o cliente e acalma quem já mirava o jogador antes da proteção começar. */
    private static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer || sp.tickCount % NUDGE_EVERY != 7) return;
        if (!sp.isAlive() || !OnboardService.inEntry(sp)) return;
        nudge(sp);
        if (sp.isCreative() || sp.isSpectator()) return;
        for (Mob m : sp.serverLevel().getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(24), m -> m.getTarget() == sp)) {
            m.setTarget(null);
            if (m instanceof NeutralMob nm) nm.stopBeingAngry();
        }
    }

    /** Renasceu no meio da criação (a tela de morte fechou a tela de classe/raça): reabre logo, sem esperar os 2 s. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer || !OnboardService.inEntry(sp)) return;
        OnboardTasks.later(sp, 10, "entry_nudge", EntryGuard::nudge);
    }

    private static void nudge(ServerPlayer sp) {
        if (OnboardService.inEntry(sp)) PacketDistributor.sendToPlayer(sp, OnboardPayloads.EntryNudge.INSTANCE);
    }

    /**
     * O cliente respondeu ao cutucão: está sem tela nenhuma e a criação continua pendente. Sem classe → tela de classe;
     * com classe → reabre a etapa pendente (o módulo dono da etapa abre a tela dele).
     */
    static void onResume(ServerPlayer sp) {
        if (sp instanceof FakePlayer || !sp.isAlive() || !OnboardService.inEntry(sp)) return;
        int now = sp.server.getTickCount();
        Integer last = LAST_RESUME.get(sp.getUUID());
        if (last != null && now - last < RESUME_GAP) return;
        LAST_RESUME.put(sp.getUUID(), now);
        if (MagicApi.get(sp).path() == MagicPath.NONE) {
            PacketDistributor.sendToPlayer(sp, CorePayloads.OpenPathScreen.INSTANCE);
        } else if (!OnboardTasks.pending(sp.getUUID(), OnboardService.TASK_STEP)) {
            // (com a próxima etapa já agendada — acabou de escolher a classe — ela abre sozinha em instantes)
            OnboardService.resumeCreation(sp);
        }
    }

    private EntryGuard() {}
}
