package com.renanpontes.isekaimahou.feature.guild;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.AdvAction;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.AdvDialog;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.CompanionView;
import com.renanpontes.isekaimahou.feature.guild.entity.AdventurerEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Contratos de aventureiros (servidor): diálogo, contratar/renovar/dispensar, limites, fim de contrato e morte, a lista de
 * companheiros mandada ao cliente (rastreador) e as regras de "fogo amigo" (o companheiro nunca fere o dono, os aliados
 * dele nem jogadores; o dono também não fere o próprio companheiro sem querer).
 */
public final class CompanionService {
    /** Duração de um contrato: 3 dias de jogo. */
    public static final int CONTRACT_DAYS = 3;
    public static final long CONTRACT_TICKS = CONTRACT_DAYS * 24000L;
    /** Contrato renovado acumula no máximo 3 contratos à frente. */
    private static final long MAX_AHEAD = CONTRACT_TICKS * 3;
    /** Preço base por rank (F … S), em Moedas da Guilda, antes da reputação. */
    private static final int[] PRICE = {8, 14, 24, 38, 56, 84, 120};
    private static final double DIALOG_REACH = 8.0;
    /** Assinatura da última lista mandada a cada jogador (só reenvia quando muda). */
    private static final Map<UUID, Integer> LAST_SENT = new ConcurrentHashMap<>();
    /** Companheiros que morreram com o dono offline (dono → companheiro → nome): avisados quando ele voltar. */
    private static final Map<UUID, Map<UUID, Component>> DIED_AWAY = new ConcurrentHashMap<>();
    /** Seguindo e mais longe que isto (ou noutra dimensão): o companheiro carregado é trazido até o dono. */
    private static final double RECALL_DIST = 48.0;
    /** Dimensão das Câmaras de Provação (módulo trials): o companheiro não é puxado para lá. */
    private static final net.minecraft.resources.ResourceLocation TRIAL_REALM = com.renanpontes.isekaimahou.IsekaiMahou.id("trial_realm");
    /** Abates do companheiro a até esta distância do dono contam nas missões de caça do dono. */
    private static final double ASSIST_DIST = 48.0;

    // =====================================================================================
    //  Regras
    // =====================================================================================
    /** 2 companheiros; 3 com a Guilda em Honrado ou melhor. */
    public static int maxCompanions(Player p) {
        return Reputation.standing(p, GuildFaction.GUILD.id()).atLeast(Reputation.Standing.HONORED) ? 3 : 2;
    }

    public static int basePrice(GuildRank rank) {
        return PRICE[Math.min(PRICE.length - 1, rank.ordinal())];
    }

    /** Preço do contrato para este jogador (rank do aventureiro × reputação com a Guilda). */
    public static int price(Player p, AdventurerEntity a) {
        return Math.max(1, Math.round(basePrice(a.rank()) * Reputation.priceMultiplier(p, GuildFaction.GUILD.id())));
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Contratos ainda valendo (os vencidos saem da lista). */
    private static int activeHires(ServerPlayer sp, GuildData d) {
        long t = now(sp.server);
        d.hires().removeIf(h -> h.end() <= t);
        return d.hires().size();
    }

    // =====================================================================================
    //  Diálogo
    // =====================================================================================
    public static void openDialog(ServerPlayer sp, AdventurerEntity a, Component line, boolean refresh) {
        GuildData d = GuildService.data(sp);
        int hire = a.isHiredBy(sp) ? GuildNetwork.HIRE_MINE : a.isHired() ? GuildNetwork.HIRE_OTHER : GuildNetwork.HIRE_FREE;
        long left = a.isHired() ? Math.max(0, a.contractEnd() - now(sp.server)) : 0;
        boolean refused = !Reputation.canTrade(sp, GuildFaction.GUILD.id());
        boolean canTrade = !refused && !a.getOffers().isEmpty() && hire != GuildNetwork.HIRE_OTHER;
        PacketDistributor.sendToPlayer(sp, new AdvDialog(a.getId(), a.getDisplayName(), a.variant().ordinal(), a.rank().level(), a.xp(),
                a.xpToNext(), a.element().ordinal(), price(sp, a), CONTRACT_DAYS, hire, a.isFollowing(), left, line, activeHires(sp, d),
                maxCompanions(sp), canTrade, refused, a.kills(), refresh));
        a.faceFor(sp, 120);
    }

    public static void handleDialog(ServerPlayer sp, AdvAction act) {
        Entity e = sp.level().getEntity(act.entityId());
        if (!(e instanceof AdventurerEntity a) || !a.isAlive() || sp.distanceToSqr(a) > DIALOG_REACH * DIALOG_REACH) return;
        switch (act.action()) {
            case GuildNetwork.ADV_TALK -> openDialog(sp, a, a.pickLine(sp), true);
            case GuildNetwork.ADV_TRADE -> {
                if (!Reputation.canTrade(sp, GuildFaction.GUILD.id()) || (a.isHired() && !a.isHiredBy(sp))) return;
                a.startTrading(sp);
            }
            case GuildNetwork.ADV_HIRE -> hire(sp, a);
            case GuildNetwork.ADV_RENEW -> renew(sp, a);
            case GuildNetwork.ADV_DISMISS -> dismiss(sp, a);
            case GuildNetwork.ADV_FOLLOW -> {
                if (a.isHiredBy(sp)) {
                    a.toggleFollow(sp);
                    openDialog(sp, a, a.speech() != null ? a.speech() : Component.empty(), true);
                }
            }
            case GuildNetwork.ADV_BYE -> {
                a.say(com.renanpontes.isekaimahou.feature.guild.entity.GuildNpcEntity.key("adventurer.bye." + sp.getRandom().nextInt(3)));
                a.triggerAnim("action", "wave");
            }
            default -> { }
        }
    }

    private static Component reply(AdventurerEntity a, String key) {
        a.say(com.renanpontes.isekaimahou.feature.guild.entity.GuildNpcEntity.key(key));
        Component c = a.speech();
        return c == null ? Component.empty() : c;
    }

    private static void hire(ServerPlayer sp, AdventurerEntity a) {
        GuildData d = GuildService.data(sp);
        if (a.isHired()) return;
        if (!Reputation.canTrade(sp, GuildFaction.GUILD.id())) {
            openDialog(sp, a, reply(a, "adventurer.hire.refuse_rep"), true);
            return;
        }
        int max = maxCompanions(sp);
        if (activeHires(sp, d) >= max) {
            GuildService.fail(sp, Component.translatable("message.isekai_mahou.guild.companion_limit", max));
            openDialog(sp, a, reply(a, "adventurer.hire.too_many"), true);
            return;
        }
        int price = price(sp, a);
        if (GuildService.countCoins(sp) < price) {
            GuildService.fail(sp, Component.translatable("message.isekai_mahou.guild.no_coins", price));
            openDialog(sp, a, reply(a, "adventurer.hire.no_coins"), true);
            return;
        }
        GuildService.removeCoins(sp, price);
        long end = now(sp.server) + CONTRACT_TICKS;
        a.hire(sp, end);
        d.addHire(new GuildData.Hire(a.getUUID(), end, a.shortName(), a.variant().ordinal(), a.rank().level()));
        MagicApi.addCounter(sp, "guild.hired", 1);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.companion_hired", a.getDisplayName(), CONTRACT_DAYS)
                .withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.companion_hint").withStyle(ChatFormatting.GRAY));
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.guild.hired"), a.getDisplayName(),
                new ItemStack(GuildContent.ADVENTURER_EGG.get()), a.rank().rgb());
        sp.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
        sp.playNotifySound(SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
        LAST_SENT.remove(sp.getUUID());
        openDialog(sp, a, a.speech() != null ? a.speech() : Component.empty(), true);
    }

    private static void renew(ServerPlayer sp, AdventurerEntity a) {
        if (!a.isHiredBy(sp)) return;
        GuildData d = GuildService.data(sp);
        long t = now(sp.server);
        if (a.contractEnd() - t + CONTRACT_TICKS > MAX_AHEAD) {
            GuildService.fail(sp, Component.translatable("message.isekai_mahou.guild.companion_renew_max"));
            return;
        }
        int price = price(sp, a);
        if (GuildService.countCoins(sp) < price) {
            GuildService.fail(sp, Component.translatable("message.isekai_mahou.guild.no_coins", price));
            return;
        }
        GuildService.removeCoins(sp, price);
        long end = Math.max(a.contractEnd(), t) + CONTRACT_TICKS;
        a.hire(sp, end);
        d.addHire(new GuildData.Hire(a.getUUID(), end, a.shortName(), a.variant().ordinal(), a.rank().level()));
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.guild.companion_renewed", a.shortName(), CONTRACT_DAYS)
                .withStyle(ChatFormatting.GREEN), true);
        sp.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.1F);
        LAST_SENT.remove(sp.getUUID());
        openDialog(sp, a, reply(a, "adventurer.hire.renew"), true);
    }

    private static void dismiss(ServerPlayer sp, AdventurerEntity a) {
        if (!a.isHiredBy(sp)) return;
        GuildService.data(sp).removeHire(a.getUUID());
        a.release(true);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.companion_dismissed", a.shortName())
                .withStyle(ChatFormatting.GRAY));
        LAST_SENT.remove(sp.getUUID());
        openDialog(sp, a, a.speech() != null ? a.speech() : Component.empty(), true);
    }

    // =====================================================================================
    //  Fim de contrato, morte, rank
    // =====================================================================================
    /** O contrato venceu (chamado pela entidade): despedida e aviso ao dono, se online. */
    public static void contractEnded(AdventurerEntity a) {
        ServerPlayer owner = a.ownerPlayer();
        UUID ownerId = a.getOwnerUUID();
        Component name = a.shortName();
        a.release(true);
        if (owner != null) {
            GuildService.data(owner).removeHire(a.getUUID());
            owner.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.companion_ended", name).withStyle(ChatFormatting.YELLOW));
            Rewards.toast(owner, Component.translatable("toast.isekai_mahou.guild.contract_ended"), name,
                    new ItemStack(GuildContent.GUILD_COIN.get()), 0xC9A55A);
            LAST_SENT.remove(owner.getUUID());
        } else if (ownerId != null) {
            LAST_SENT.remove(ownerId);
        }
    }

    public static void companionDied(AdventurerEntity a, DamageSource source) {
        ServerPlayer owner = a.ownerPlayer();
        if (owner == null) {
            // Dono offline: o contrato sai da lista (e libera a vaga) quando ele voltar, com o aviso.
            UUID ownerId = a.getOwnerUUID();
            if (ownerId != null) {
                DIED_AWAY.computeIfAbsent(ownerId, k -> new ConcurrentHashMap<>()).put(a.getUUID(), a.shortName());
            }
            return;
        }
        GuildService.data(owner).removeHire(a.getUUID());
        owner.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.companion_died", a.shortName(), source.getLocalizedDeathMessage(a))
                .withStyle(ChatFormatting.RED));
        Rewards.banner(owner, Component.translatable("toast.isekai_mahou.guild.companion_died", a.shortName()), 0xE8735E);
        owner.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.6F, 0.5F);
        LAST_SENT.remove(owner.getUUID());
    }

    public static void onRankUp(AdventurerEntity a, GuildRank rank) {
        ServerPlayer owner = a.ownerPlayer();
        if (owner == null) return;
        GuildData d = GuildService.data(owner);
        for (GuildData.Hire h : List.copyOf(d.hires())) {
            if (h.id().equals(a.getUUID())) d.addHire(new GuildData.Hire(h.id(), h.end(), a.shortName(), a.variant().ordinal(), rank.level()));
        }
        Rewards.toast(owner, Component.translatable("toast.isekai_mahou.guild.companion_rank", rank.letter()), a.shortName(),
                new ItemStack(GuildContent.ADVENTURER_CARD.get()), rank.rgb());
        LAST_SENT.remove(owner.getUUID());
    }

    /** Desfaz todos os contratos (reset de comando). */
    public static void releaseAll(ServerPlayer sp, GuildData d) {
        for (GuildData.Hire h : List.copyOf(d.hires())) {
            AdventurerEntity a = find(sp.server, h.id());
            if (a != null && a.isHiredBy(sp)) a.release(false);
        }
        d.hires().clear();
        LAST_SENT.remove(sp.getUUID());
    }

    @Nullable
    private static AdventurerEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.getEntity(id) instanceof AdventurerEntity a) return a;
        }
        return null;
    }

    // =====================================================================================
    //  Tick do dono (1x/s): limpa contratos vencidos e manda a lista ao cliente
    // =====================================================================================
    static void tickOwner(ServerPlayer sp, GuildData d) {
        // Companheiros que morreram enquanto o dono estava fora: aviso e vaga liberada.
        Map<UUID, Component> away = DIED_AWAY.remove(sp.getUUID());
        if (away != null) {
            away.forEach((id, name) -> {
                if (d.removeHire(id)) {
                    sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.companion_died_away", name)
                            .withStyle(ChatFormatting.RED));
                }
            });
        }
        if (d.hires().isEmpty()) {
            if (LAST_SENT.getOrDefault(sp.getUUID(), 0) != 0) {
                LAST_SENT.put(sp.getUUID(), 0);
                PacketDistributor.sendToPlayer(sp, new GuildNetwork.Companions(List.of()));
            }
            return;
        }
        long t = now(sp.server);
        List<CompanionView> views = new ArrayList<>();
        for (Iterator<GuildData.Hire> it = d.hires().iterator(); it.hasNext(); ) {
            GuildData.Hire h = it.next();
            AdventurerEntity a = find(sp.server, h.id());
            if (h.end() <= t) {
                // Venceu longe do jogador (entidade descarregada ou já liberada): avisa aqui; a entidade se libera
                // sozinha quando carregar.
                it.remove();
                d.markDirty();
                if (a == null || !a.isHiredBy(sp)) {
                    sp.sendSystemMessage(Component.translatable("message.isekai_mahou.guild.companion_ended", h.name())
                            .withStyle(ChatFormatting.YELLOW));
                }
                continue;
            }
            if (a != null && !a.isHiredBy(sp)) {
                // A entidade não é mais deste jogador (dispensado, contrato de outro): some da lista.
                it.remove();
                d.markDirty();
                continue;
            }
            if (a != null) a = recall(sp, a);
            if (views.size() >= 8) continue;
            if (a != null) {
                views.add(new CompanionView(h.id(), a.level() == sp.level() ? a.getId() : -1, a.shortName(), a.variant().ordinal(),
                        a.rank().level(), a.getHealth(), a.getMaxHealth(), h.end() - t, a.isFollowing(),
                        Optional.of(GlobalPos.of(a.level().dimension(), a.blockPosition()))));
            } else {
                views.add(new CompanionView(h.id(), -1, h.name(), h.variant(), h.rank(), 0, 0, h.end() - t, false, Optional.empty()));
            }
        }
        int hash = 1;
        for (CompanionView v : views) {
            hash = hash * 31 + v.id().hashCode();
            hash = hash * 31 + Math.round(v.health() * 2) + v.rank() * 7 + (v.following() ? 1 : 0) + v.entityId();
            hash = hash * 31 + (int) (v.ticksLeft() / 1200);
            hash = hash * 31 + v.pos().map(p -> p.pos().getX() / 4 * 31 + p.pos().getZ() / 4).orElse(0);
        }
        if (views.isEmpty()) hash = 0;
        if (LAST_SENT.getOrDefault(sp.getUUID(), 0) != hash) {
            LAST_SENT.put(sp.getUUID(), hash);
            PacketDistributor.sendToPlayer(sp, new GuildNetwork.Companions(views));
        }
    }

    /**
     * Companheiro SEGUINDO que ficou para trás (carregado, mas a mais de {@link #RECALL_DIST} blocos — o dono usou pérola,
     * comando, cavalo rápido — ou noutra dimensão, porque o dono passou por um portal): vem até o dono, com fumaça de
     * portal dos dois lados. Devolve a entidade que vale agora (trocar de dimensão cria uma cópia com o mesmo UUID).
     */
    private static AdventurerEntity recall(ServerPlayer sp, AdventurerEntity a) {
        if (!a.isAlive() || !a.isFollowing() || !sp.isAlive() || sp.isSpectator() || a.getTradingPlayer() != null) return a;
        if (a.level() == sp.level()) {
            if (a.distanceToSqr(sp) > RECALL_DIST * RECALL_DIST) {
                a.teleportNear(sp);
                a.setTarget(null);
            }
            return a;
        }
        if (!(a.level() instanceof ServerLevel from)) return a;
        // Reino das Provações: a arena é limpa (entidades descartadas) quando a provação acaba — o companheiro espera
        // do lado de fora e volta a acompanhar quando o dono sair.
        if (TRIAL_REALM.equals(sp.level().dimension().location())) return a;
        from.sendParticles(ParticleTypes.PORTAL, a.getX(), a.getY() + 1, a.getZ(), 30, 0.3, 0.6, 0.3, 0.4);
        a.setTarget(null);
        a.getNavigation().stop();
        if (!a.teleportTo(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ(), java.util.Set.of(), sp.getYRot(), 0.0F)) return a;
        Entity moved = sp.serverLevel().getEntity(a.getUUID());
        if (!(moved instanceof AdventurerEntity na)) return a;
        na.teleportNear(sp);
        sp.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, na.getX(), na.getY() + 1, na.getZ(), 30, 0.3, 0.6, 0.3, 0.1);
        sp.serverLevel().playSound(null, na.getX(), na.getY(), na.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6F, 1.2F);
        return na;
    }

    public static void forget(ServerPlayer sp) {
        LAST_SENT.remove(sp.getUUID());
    }

    public static void clearAll() {
        LAST_SENT.clear();
        DIED_AWAY.clear();
    }

    // =====================================================================================
    //  Eventos: fogo amigo e experiência
    // =====================================================================================
    /** Companheiro nunca fere o dono, aliados do dono nem jogadores; o dono não fere o próprio companheiro. */
    static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) return;
        Entity attacker = e.getSource().getEntity();
        if (attacker == null) return;
        if (attacker instanceof AdventurerEntity a && a.isHired()) {
            UUID owner = a.getOwnerUUID();
            if (victim instanceof Player || victim.getUUID().equals(owner)
                    || (victim instanceof OwnableEntity o && owner.equals(o.getOwnerUUID()))
                    || (victim instanceof AdventurerEntity other && owner.equals(other.getOwnerUUID()))) {
                e.setCanceled(true);
                return;
            }
        }
        if (victim instanceof AdventurerEntity a && a.isHired() && attacker.getUUID().equals(a.getOwnerUUID())) {
            e.setCanceled(true);
        }
        // Companheiros do mesmo dono não se ferem.
        if (victim instanceof AdventurerEntity v && attacker instanceof AdventurerEntity at && v.isHired()
                && v.getOwnerUUID().equals(at.getOwnerUUID())) {
            e.setCanceled(true);
        }
    }

    /**
     * Abate de um aventureiro (registrado com prioridade LOWEST: só conta mortes que aconteceram de verdade). O abate do
     * companheiro perto do dono conta como abate do dono para as missões de caça ({@code kill.<ns>.<mob>}), como num grupo.
     */
    static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) return;
        if (e.getSource().getEntity() instanceof AdventurerEntity a && a != victim) {
            a.onKill(victim);
            ServerPlayer owner = a.ownerPlayer();
            if (owner != null) {
                MagicApi.addCounter(owner, "guild.companion_kills", 1);
                if (victim.level() instanceof ServerLevel sl && owner.level() == sl && owner.distanceToSqr(a) < ASSIST_DIST * ASSIST_DIST
                        && !(victim instanceof Player)) {
                    net.minecraft.resources.ResourceLocation type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
                    MagicApi.addCounter(owner, "kill." + type.getNamespace() + "." + type.getPath(), 1);
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, a.getX(), a.getY() + 2.1, a.getZ(), 3, 0.2, 0.1, 0.2, 0);
                }
            }
        }
    }

    private CompanionService() {}
}
