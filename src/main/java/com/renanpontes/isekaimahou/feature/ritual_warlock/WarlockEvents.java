package com.renanpontes.isekaimahou.feature.ritual_warlock;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactBoons;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactRitual;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactState;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PatronDemand;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Eventos de jogo do Ritual do Warlock: almas da adaga, progresso dos tributos, dádivas e HUD. */
public final class WarlockEvents {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WarlockEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(WarlockEvents::onCounter);
        NeoForge.EVENT_BUS.addListener(WarlockEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(WarlockEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(WarlockEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(WarlockEvents::onDimension);
        NeoForge.EVENT_BUS.addListener(WarlockEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(WarlockEvents::onEndermanAnger);
    }

    /** Matar com a Adaga Ritual colhe a alma do alvo e dá um pouco de XP mágico. */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getSource().getDirectEntity() != player) return;
        if (victim == player || !(victim instanceof Mob || victim instanceof Player)) return;
        if (!player.getMainHandItem().is(WarlockContent.RITUAL_DAGGER.get())) return;

        int xp = Math.max(1, Math.min(8, 1 + Math.round(victim.getMaxHealth() / 8F)));
        MagicApi.giveXp(player, xp, "ritual_dagger");
        MagicApi.addCounter(player, PatronDemand.SOULS_COUNTER, 1);

        ServerLevel level = player.serverLevel();
        Vec3 from = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        Vec3 to = player.position().add(0, 1.0, 0);
        for (int i = 0; i <= 8; i++) {
            Vec3 p = from.lerp(to, i / 8.0);
            level.sendParticles(ParticleTypes.SOUL, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        player.displayClientMessage(Component.translatable("message.isekai_mahou.warlock.soul_harvested",
                MagicApi.get(player).counter(PatronDemand.SOULS_COUNTER)).withStyle(ChatFormatting.DARK_AQUA), true);
    }

    /** Abates/almas contam para o tributo pendente na hora. */
    private static void onCounter(MagicProgressEvent.Counter event) {
        ServerPlayer player = event.getPlayer();
        PactState s = PactRitual.state(player);
        if (!s.active() || !s.demand.isTracked() || s.fulfilled || s.failed) return;
        if (!event.key.equals(s.demand.counterKey())) return;
        updateTracked(player, s, true);
    }

    private static void updateTracked(ServerPlayer player, PactState s, boolean announce) {
        long now = PactRitual.now(player.serverLevel());
        int prog = PactRitual.progress(player, s);
        if (prog >= s.demand.amount()) {
            s.fulfilled = true;
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.warlock.demand_fulfilled", s.kind.displayName(),
                    s.altar.getX(), s.altar.getY(), s.altar.getZ()).withStyle(ChatFormatting.GOLD));
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL,
                    SoundSource.PLAYERS, 1.0F, 0.7F);
            PactRitual.sendHud(player);
        } else if (now > s.windowEnd) {
            s.failed = true;
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.warlock.demand_failed", s.kind.displayName())
                    .withStyle(ChatFormatting.DARK_RED));
            PactRitual.sendHud(player);
        } else if (announce && prog != s.lastShownProgress) {
            s.lastShownProgress = prog;
            player.displayClientMessage(Component.translatable("message.isekai_mahou.warlock.demand_progress",
                    s.demand.targetName(), prog, s.demand.amount()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            PactRitual.sendHud(player);
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        PactBoons.tick(player);
        if (player.tickCount % 200 == 0) PactBoons.refresh(player);

        PactState s = PactRitual.state(player);
        if (!s.active()) return;
        long now = PactRitual.now(player.serverLevel());
        if (now > s.deadline + 200) {
            // O Patrono já deveria ter se libertado (o círculo pode estar descarregado): encerra o pacto.
            int pact = s.pactNumber;
            s.clearPact();
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.warlock.pact_expired", pact).withStyle(ChatFormatting.DARK_RED));
            PactRitual.sendHud(player);
            return;
        }
        if (s.demand.isTracked() && !s.fulfilled && !s.failed) updateTracked(player, s, false);
        else if (s.demand.type() == PatronDemand.Type.ITEM && player.tickCount % 60 == 0) PactRitual.sendHud(player);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            PactBoons.refresh(p);
            PactRitual.sendHud(p);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            PactBoons.refresh(p);
            PactRitual.sendHud(p);
        }
    }

    private static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) PactRitual.sendHud(p);
    }

    /** Pele de Brasa: menos dano de fogo. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getSource().is(DamageTypeTags.IS_FIRE)) return;
        float red = PactBoons.fireReduction(p);
        if (red > 0) event.setAmount(event.getAmount() * (1.0F - red));
    }

    /** Olhos do Vazio (nível 2+): Endermen não se irritam com o olhar. */
    private static void onEndermanAnger(EnderManAngerEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && PactBoons.endermenIgnore(p)) event.setCanceled(true);
    }

    private WarlockEvents() {}
}
