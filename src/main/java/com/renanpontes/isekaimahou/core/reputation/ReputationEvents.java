package com.renanpontes.isekaimahou.core.reputation;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Reputation.Faction;
import com.renanpontes.isekaimahou.core.api.Reputation.Standing;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Fontes automáticas de reputação e efeitos no mundo (servidor):
 * <ul>
 *   <li>matar membro → {@code kill_penalty}; matar membro com {@code kill_rewards} → bônus em outras facções (limite diário);</li>
 *   <li>matar monstro dentro de uma cidade/capital ({@link Places}) → +1 na facção do lugar (limite diário);</li>
 *   <li>contadores do MagicData com {@code counter_rewards} (ex.: provação concluída → Academia);</li>
 *   <li>neutralidade: membros "hostis por natureza" não miram jogadores Amigáveis, a menos que sejam atacados (vingança);</li>
 *   <li>guardas sem IA própria ({@code guards} do JSON, ex.: golem de ferro) atacam quem está Hostil/Inimigo;
 *       membros que sabem lutar atacam quem é Inimigo;</li>
 *   <li>comerciantes vanilla da facção ({@code traders}, ex.: aldeão) recusam trocas com quem está Hostil/Inimigo.</li>
 * </ul>
 */
public final class ReputationEvents {
    /** Janela da vingança: atacar um membro libera a facção inteira para revidar por 30 s. */
    private static final int AGGRESSION_TICKS = 600;
    /** Limite diário do "matar monstro dentro da cidade". */
    static final int CITY_DEFENSE_DAILY_CAP = 30;
    /** Vida máxima a partir da qual um membro é tratado como chefe (não fica neutro). */
    private static final float BOSS_HEALTH = 150F;
    private static final double PACIFY_RADIUS = 48;

    /** jogador → facção → tick do servidor do último ataque a um membro. */
    private static final Map<UUID, Map<String, Integer>> AGGRESSION = new ConcurrentHashMap<>();

    static void register(IEventBus bus) {
        bus.addListener((AddReloadListenerEvent e) -> e.addListener(new FactionLoader()));
        bus.addListener(ReputationEvents::onDatapackSync);
        bus.addListener(ReputationEvents::onTagsUpdated);
        bus.addListener(EventPriority.LOW, ReputationEvents::onDeath);
        bus.addListener(ReputationEvents::onCounter);
        bus.addListener(EventPriority.LOW, ReputationEvents::onChangeTarget);
        bus.addListener(ReputationEvents::onVisibility);
        bus.addListener(ReputationEvents::onDamage);
        bus.addListener(ReputationEvents::onJoin);
        bus.addListener(ReputationEvents::onInteract);
        bus.addListener(ReputationEvents::onLogout);
        bus.addListener(ReputationEvents::onServerStopped);
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================
    private static void onDatapackSync(OnDatapackSyncEvent e) {
        ReputationPayloads.FactionSync packet = new ReputationPayloads.FactionSync(FactionRegistry.server().json());
        e.getRelevantPlayers().forEach(p -> PacketDistributor.sendToPlayer(p, packet));
    }

    private static void onTagsUpdated(TagsUpdatedEvent e) {
        FactionRegistry.clearCaches();
    }

    // =====================================================================================
    //  Abates
    // =====================================================================================
    private static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof Player) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;

        String fid = FactionRegistry.server().factionOf(victim);
        Faction f = fid == null ? null : FactionRegistry.server().get(fid);
        if (f != null) {
            int penalty = f.killPenalty(victim.getType());
            if (penalty != 0) {
                ReputationService.add(player, f.id(), penalty,
                        Component.translatable("reputation.isekai_mahou.reason.kill_member", victim.getDisplayName()));
            }
            for (Map.Entry<String, Integer> bonus : f.killRewards().entrySet()) {
                int amount = bonus.getValue();
                if (amount < 0) {
                    ReputationService.add(player, bonus.getKey(), amount,
                            Component.translatable("reputation.isekai_mahou.reason.kill_member", victim.getDisplayName()));
                    continue;
                }
                int granted = ReputationService.takeDaily(player, "kill." + f.id() + "." + bonus.getKey(), amount, f.killRewardDailyCap());
                if (granted > 0) {
                    ReputationService.add(player, bonus.getKey(), granted,
                            Component.translatable("reputation.isekai_mahou.reason.kill_enemy", victim.getDisplayName()));
                }
            }
        }
        if (isHostileMonster(victim, f)) cityDefense(player, level, victim);
    }

    /** Monstro de verdade (não domado, não membro "civil" de facção). */
    private static boolean isHostileMonster(LivingEntity victim, @Nullable Faction f) {
        if (f != null && !f.neutralWhenFriendly()) return false;
        if (victim instanceof OwnableEntity own && own.getOwnerUUID() != null) return false;
        return victim instanceof Enemy || victim.getType().getCategory() == MobCategory.MONSTER;
    }

    private static void cityDefense(ServerPlayer player, ServerLevel level, LivingEntity victim) {
        Places.Place place = Places.at(level, victim.blockPosition());
        if (place == null || !("city".equals(place.type()) || "capital".equals(place.type()))) return;
        String faction = place.faction() == null || place.faction().isBlank() ? "kingdom" : place.faction();
        if (FactionRegistry.server().get(faction) == null) return;
        int granted = ReputationService.takeDaily(player, "city_defense", 1, CITY_DEFENSE_DAILY_CAP);
        if (granted > 0) {
            ReputationService.add(player, faction, granted,
                    Component.translatable("reputation.isekai_mahou.reason.city_defense", place.name()));
        }
    }

    private static void onCounter(MagicProgressEvent.Counter e) {
        ReputationService.onCounter(e.getPlayer(), e.key, e.value);
    }

    // =====================================================================================
    //  Neutralidade e vingança
    // =====================================================================================
    private static void onDamage(LivingDamageEvent.Post e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide() || victim instanceof Player) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        String fid = FactionRegistry.server().factionOf(victim);
        if (fid == null) return;
        AGGRESSION.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>()).put(fid, player.server.getTickCount());
    }

    private static void onChangeTarget(LivingChangeTargetEvent e) {
        if (!(e.getNewAboutToBeSetTarget() instanceof ServerPlayer player)) return;
        LivingEntity mob = e.getEntity();
        if (mob.level().isClientSide() || mob.getLastHurtByMob() == player) return;
        if (shouldIgnore(mob, player)) e.setNewAboutToBeSetTarget(null);
    }

    /** Membro hostil por natureza que deve deixar este jogador em paz? */
    private static boolean shouldIgnore(LivingEntity mob, ServerPlayer player) {
        String fid = FactionRegistry.server().factionOf(mob);
        if (fid == null) return false;
        Faction f = FactionRegistry.server().get(fid);
        if (f == null || !f.neutralWhenFriendly() || isBossLike(mob)) return false;
        return Reputation.isFriendly(player, fid) && !recentlyAttacked(player, fid);
    }

    /** Ao virar Amigável: membros por perto que miravam o jogador desistem. */
    static void pacify(ServerPlayer player, Faction f) {
        if (!f.neutralWhenFriendly()) return;
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(PACIFY_RADIUS),
                m -> m.getTarget() == player)) {
            if (mob.getLastHurtByMob() != player && shouldIgnore(mob, player)) mob.setTarget(null);
        }
    }

    static boolean isBossLike(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES) || BossKit.isTrialBoss(e) || e.getMaxHealth() >= BOSS_HEALTH;
    }

    public static boolean recentlyAttacked(Player player, String faction) {
        if (!(player instanceof ServerPlayer sp)) return false;
        Map<String, Integer> map = AGGRESSION.get(sp.getUUID());
        if (map == null) return false;
        Integer tick = map.get(faction);
        return tick != null && sp.server.getTickCount() - tick <= AGGRESSION_TICKS;
    }

    /**
     * Neutralidade "de verdade": para membros que devem deixar o jogador em paz, o jogador fica praticamente invisível
     * na BUSCA de alvo (só a 2 blocos). Sem isso o objetivo de alvo vanilla acha o jogador, é barrado no
     * {@link #onChangeTarget} e tenta de novo a cada meio segundo, roubando a vez de outros alvos (ex.: aldeões).
     */
    private static void onVisibility(LivingEvent.LivingVisibilityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || !(e.getLookingEntity() instanceof Mob mob)) return;
        if (mob.getLastHurtByMob() == player) return;
        if (shouldIgnore(mob, player)) e.modifyVisibility(0);
    }

    // =====================================================================================
    //  Quem ataca jogadores de reputação ruim
    // =====================================================================================
    /**
     * Guardas ({@code guards} do JSON, ex.: golem de ferro do Reino) atacam quem está Hostil/Inimigo com a facção.
     * Membros que sabem lutar (têm dano de ataque) atacam quem é Inimigo da facção deles — a facção é lida na hora,
     * então um {@link Reputation.FactionMember} que muda (ex.: mercenário contratado = sem facção) é respeitado.
     */
    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof PathfinderMob mob)) return;
        FactionRegistry.Snapshot snap = FactionRegistry.server();
        if (snap.isEmpty()) return;
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        boolean guard = false;
        for (Faction f : snap.sorted()) {
            if (f.guards().contains(typeId)) {
                addTargetGoal(mob, f.id());
                guard = true;
            }
        }
        if (guard || !mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) return;
        String fid = snap.factionOf(mob);
        Faction f = fid == null ? null : snap.get(fid);
        // Horda/Povo Selvagem já atacam todo mundo; não precisam de alvo extra
        if (mob instanceof Reputation.FactionMember || (f != null && !f.neutralWhenFriendly())) addTargetGoal(mob, null);
    }

    private static void addTargetGoal(PathfinderMob mob, @Nullable String guardOf) {
        boolean present = mob.targetSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof FactionTargetGoal g && Objects.equals(g.guardOf, guardOf));
        if (!present) mob.targetSelector.addGoal(guardOf != null ? 2 : 3, new FactionTargetGoal(mob, guardOf));
    }

    /** Mira jogadores com reputação ruim (criativo/espectador nunca). {@code guardOf} null = membro comum da facção do mob. */
    static final class FactionTargetGoal extends NearestAttackableTargetGoal<Player> {
        @Nullable final String guardOf;

        FactionTargetGoal(Mob mob, @Nullable String guardOf) {
            super(mob, Player.class, 10, true, false, t -> t instanceof Player p && shouldAttack(mob, p, guardOf));
            this.guardOf = guardOf;
        }
    }

    private static boolean shouldAttack(Mob mob, Player player, @Nullable String guardOf) {
        if (player.isCreative() || player.isSpectator()) return false;
        if (guardOf != null) return Reputation.isHostile(player, guardOf);
        FactionRegistry.Snapshot snap = FactionRegistry.server();
        String fid = snap.factionOf(mob);
        Faction f = fid == null ? null : snap.get(fid);
        return f != null && !f.neutralWhenFriendly() && Reputation.standing(player, fid) == Standing.ENEMY;
    }

    // =====================================================================================
    //  Comércio vanilla recusado (Hostil/Inimigo)
    // =====================================================================================
    /**
     * Tipos em {@code traders} (ex.: aldeão do Reino, mago andarilho da Academia) não abrem as trocas para quem não pode
     * negociar com a facção. Roda nos dois lados: no cliente evita o "aceno" da mão; no servidor avisa o jogador.
     */
    private static void onInteract(PlayerInteractEvent.EntityInteract e) {
        Player player = e.getEntity();
        if (player.isSpectator() || !(e.getTarget() instanceof LivingEntity target) || !target.isAlive() || target.isBaby()) return;
        ItemStack held = e.getItemStack();
        // nomear, laçar e ovos continuam funcionando
        if (held.getItem() instanceof NameTagItem || held.getItem() instanceof LeadItem || held.getItem() instanceof SpawnEggItem) return;
        FactionRegistry.Snapshot snap = FactionRegistry.current();
        if (snap.isEmpty()) return;
        String fid = snap.factionOf(target);
        Faction f = fid == null ? null : snap.get(fid);
        if (f == null || f.traders().isEmpty() || !f.traders().contains(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()))) return;
        if (Reputation.canTrade(player, fid)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide()));
        if (player instanceof ServerPlayer sp && e.getHand() == InteractionHand.MAIN_HAND) {
            sp.displayClientMessage(Component.translatable("reputation.isekai_mahou.refuse_trade", f.coloredName(),
                    Reputation.standing(sp, fid).displayName()).withStyle(ChatFormatting.RED), true);
            if (target instanceof AbstractVillager villager) villager.setUnhappyCounter(40);
            sp.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.VILLAGER_NO,
                    target.getSoundSource(), 1F, 0.9F + sp.getRandom().nextFloat() * 0.2F);
        }
    }

    // =====================================================================================
    //  Limpeza
    // =====================================================================================
    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Entity p = e.getEntity();
        AGGRESSION.remove(p.getUUID());
    }

    private static void onServerStopped(ServerStoppedEvent e) {
        AGGRESSION.clear();
        ReputationService.clearState();
        FactionRegistry.clearServer();
    }

    private ReputationEvents() {}
}
