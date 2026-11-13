package com.renanpontes.isekaimahou.feature.endless_abyss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
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
 * Gerenciador do Abismo Infinito (servidor): descidas abertas, entrada/saída (ponto de retorno por jogador), proteção
 * da dimensão e as regras "ninguém morre de verdade lá dentro" e "ninguém fica preso lá".
 * <p>
 * Para o ranking de outros módulos: {@link AbyssRecords#bestFloor}, {@link AbyssRecords#top} (classe pública) ou, por
 * texto, o contador {@code abyss.best_floor} do {@code MagicData}.
 */
public final class AbyssManager {
    /** Nível que libera o Abismo (aba no menu B). */
    public static final int UNLOCK_LEVEL = 100;
    /** Descidas ao mesmo tempo no servidor (cada uma segura ~16 chunks carregados). */
    private static final int MAX_RUNS = 12;
    /** Flag do MagicData: o aviso "o Abismo se abriu" já foi mostrado. */
    private static final String UNLOCK_FLAG = "abyss.unlocked";

    public static final TicketController TICKETS = new TicketController(IsekaiMahou.id("endless_abyss"), (level, helper) -> {
        // No boot nenhuma descida sobrevive: os tickets que sobraram são descartados
        for (UUID owner : List.copyOf(helper.getEntityTickets().keySet())) helper.removeAllTickets(owner);
        for (BlockPos owner : List.copyOf(helper.getBlockTickets().keySet())) helper.removeAllTickets(owner);
    });

    private static final Map<UUID, AbyssRun> RUNS = new LinkedHashMap<>();
    private static final List<Later> LATER = new ArrayList<>();
    /** Jogadores que "caíram" neste tick e esperam o teleporte de volta (ficam intocáveis até lá). */
    private static final Set<UUID> FALLEN = new HashSet<>();
    @Nullable private static MinecraftServer server;
    private static long ticks;

    private record Later(long at, Runnable run) {}

    static AbyssRecords data() {
        if (server == null) throw new IllegalStateException("Abismo Infinito usado sem servidor");
        return AbyssRecords.get(server);
    }

    /** Roda {@code run} daqui a {@code delay} ticks (descartado se o servidor parar). */
    static void later(int delay, Runnable run) {
        LATER.add(new Later(ticks + Math.max(1, delay), run));
    }

    // =====================================================================================
    //  Consultas
    // =====================================================================================
    /** Descida aberta da qual o jogador faz parte, ou null. */
    @Nullable
    public static AbyssRun of(UUID player) {
        for (AbyssRun r : RUNS.values()) if (r.open() && r.isMember(player)) return r;
        return null;
    }

    @Nullable
    static AbyssRun byId(UUID id) {
        AbyssRun r = RUNS.get(id);
        return r != null && r.open() ? r : null;
    }

    static List<AbyssRun> all() {
        return RUNS.values().stream().filter(AbyssRun::open).toList();
    }

    public static boolean unlocked(ServerPlayer p) {
        return MagicApi.get(p).level() >= UNLOCK_LEVEL;
    }

    // =====================================================================================
    //  Entrar
    // =====================================================================================
    /** Motivo para NÃO poder entrar agora, ou null se pode. {@code bypass} = comando de teste (pula o nível 100). */
    @Nullable
    static Component denyReason(ServerPlayer p, boolean bypass) {
        if (server == null || AbyssDimension.level(p.server) == null) return msg("no_dimension");
        if (!bypass && !unlocked(p)) return Component.translatable("message.isekai_mahou.endless_abyss.deny.level", UNLOCK_LEVEL);
        if (of(p.getUUID()) != null || AbyssDimension.is(p.level())) return msg("already_in");
        if (!p.isAlive() || p.isSpectator()) return msg("spectator");
        if (p.level().getDifficulty() == Difficulty.PEACEFUL) return msg("peaceful");
        // Não vale como fuga: nada de entrar no meio de uma luta de chefe, de uma Provação ou apanhando
        if (BossArena.at(p.level(), p.position()) != null) return msg("boss_fight");
        if ("trial_realm".equals(p.level().dimension().location().getPath())) return msg("not_here");
        if (!bypass && p.getLastHurtByMob() != null && p.tickCount - p.getLastHurtByMobTimestamp() < 100) return msg("combat");
        return null;
    }

    private static Component msg(String key) {
        return Component.translatable("message.isekai_mahou.endless_abyss.deny." + key);
    }

    /** Começa (ou entra numa) descida sem fim. Devolve false e avisa o jogador se não der. */
    static boolean enterEndless(ServerPlayer p, int startFloor, boolean bypass) {
        Component deny = denyReason(p, bypass);
        if (deny != null) return refuse(p, deny);
        // Um companheiro de grupo já está lá, na pausa entre andares: entra na descida dele
        for (ServerPlayer mate : Party.onlineMembers(p)) {
            if (mate == p) continue;
            AbyssRun theirs = of(mate.getUUID());
            if (theirs != null && theirs.joinable()) {
                join(p, theirs);
                return true;
            }
        }
        int best = AbyssRecords.bestFloor(p.server, p.getUUID());
        int floor = bypass ? Math.max(1, startFloor) : Math.max(1, Math.min(startFloor, AbyssFloors.checkpointStart(best)));
        return open(p, AbyssRun.Mode.ENDLESS, floor, null);
    }

    /** Começa uma Luta das Sombras contra o chefe (path do id). */
    static boolean enterShadow(ServerPlayer p, String boss, boolean bypass) {
        Component deny = denyReason(p, bypass);
        if (deny != null) return refuse(p, deny);
        if (!AbyssShadows.valid(boss)) return refuse(p, msg("shadow_unknown"));
        if (!bypass && MagicApi.get(p).counter("boss." + boss) < 1) return refuse(p, msg("shadow_locked"));
        return open(p, AbyssRun.Mode.SHADOW, 1, boss);
    }

    private static boolean refuse(ServerPlayer p, Component reason) {
        p.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
        return false;
    }

    private static boolean open(ServerPlayer p, AbyssRun.Mode mode, int floor, @Nullable String boss) {
        ServerLevel abyss = AbyssDimension.level(p.server);
        if (abyss == null) return false;
        if (all().size() >= MAX_RUNS) return refuse(p, msg("full"));
        int index = freeIndex();
        for (ChunkPos cp : AbyssDimension.chunks(index)) TICKETS.forceChunk(abyss, AbyssDimension.center(index), cp.x, cp.z, true, true);
        AbyssRun run = new AbyssRun(abyss, index, mode, floor, boss, !data().isBuilt(index));
        RUNS.put(run.id, run);
        join(p, run);
        IsekaiMahou.LOGGER.info("Abismo Infinito: vaga {} aberta por {} ({}, andar {})", index, p.getGameProfile().getName(), mode, floor);
        return true;
    }

    private static void join(ServerPlayer p, AbyssRun run) {
        data().putReturn(p.getUUID(), new AbyssRecords.ReturnPoint(p.level().dimension(), p.position(), p.getYRot()));
        // No Modo Paz o jogador não conseguiria lutar: desliga ao entrar
        if (PeaceMode.isOn(p)) PeaceMode.set(p, false, false);
        run.addMember(p);
        AbyssNetwork.sendInfo(p);
    }

    private static int freeIndex() {
        Set<Integer> used = new HashSet<>();
        for (AbyssRun r : all()) used.add(r.index);
        int i = 0;
        while (used.contains(i)) i++;
        return i;
    }

    /** A descida fechou: solta os chunks da vaga. */
    static void release(AbyssRun run) {
        for (ChunkPos cp : AbyssDimension.chunks(run.index)) {
            TICKETS.forceChunk(run.level, AbyssDimension.center(run.index), cp.x, cp.z, false, true);
        }
        IsekaiMahou.LOGGER.info("Abismo Infinito: vaga {} fechada (andar {})", run.index, run.floor());
    }

    // =====================================================================================
    //  Sair
    // =====================================================================================
    /** Sair por vontade própria (botão da aba ou {@code /mahou abyss leave}). */
    static void leave(ServerPlayer p) {
        AbyssRun run = of(p.getUUID());
        if (run == null) {
            if (AbyssDimension.is(p.level())) sendHome(p);
            else refuse(p, msg("not_in"));
            return;
        }
        run.reportExit(p, false);
        run.removeMember(p.getUUID());
        if (AbyssDimension.is(p.level())) sendHome(p);
        else data().takeReturn(p.getUUID());
        AbyssNetwork.sendInfo(p);
    }

    /** O jogador "morreu" lá dentro: não morre — sai da descida e volta inteiro para onde estava. */
    private static void fall(ServerPlayer p) {
        FALLEN.remove(p.getUUID());
        if (p.hasDisconnected()) return;
        AbyssRun run = of(p.getUUID());
        if (run != null) {
            run.reportExit(p, true);
            run.removeMember(p.getUUID());
        }
        if (AbyssDimension.is(p.level())) sendHome(p);
        AbyssNetwork.sendInfo(p);
    }

    /**
     * Devolve o jogador ao ponto de onde entrou (ou ao spawn do mundo, se não houver), inteiro: sem fogo, sem queda,
     * sem efeitos ruins e com alguns segundos de proteção na chegada.
     */
    static void sendHome(ServerPlayer p) {
        AbyssRecords.ReturnPoint rp = data().takeReturn(p.getUUID());
        ServerLevel dest = rp == null ? null : p.server.getLevel(rp.dim());
        Vec3 pos;
        float yaw;
        if (dest == null || AbyssDimension.is(dest)) {
            dest = p.server.overworld();
            BlockPos spawn = dest.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dest.getSharedSpawnPos());
            pos = Vec3.atBottomCenterOf(spawn);
            yaw = dest.getSharedSpawnAngle();
        } else {
            pos = rp.pos();
            yaw = rp.yaw();
        }
        AbyssRun.fx(p.serverLevel(), p.position());
        p.clearFire();
        p.setHealth(Math.max(p.getHealth(), p.getMaxHealth() * 0.5F));
        for (var inst : List.copyOf(p.getActiveEffects())) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(inst.getEffect());
        }
        p.teleportTo(dest, pos.x, pos.y, pos.z, yaw, p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
        p.invulnerableTime = 60;
        AbyssRun.fx(dest, pos);
    }

    /** Dentro da dimensão sem estar numa descida (entrou no mundo depois de uma queda do servidor, teleporte de outro mod). */
    static void evacuate(ServerPlayer p) {
        if (p.hasDisconnected() || !AbyssDimension.is(p.level()) || of(p.getUUID()) != null) return;
        if (p.hasPermissions(2) && (p.isCreative() || p.isSpectator())) return; // admin olhando a arena
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.endless_abyss.evacuated").withStyle(ChatFormatting.YELLOW));
        sendHome(p);
    }

    // =====================================================================================
    //  Pacotes da aba
    // =====================================================================================
    static void onAction(ServerPlayer p, AbyssNetwork.Action a) {
        switch (a.op()) {
            case AbyssNetwork.Action.ENTER -> enterEndless(p, a.floor(), false);
            case AbyssNetwork.Action.SHADOW -> enterShadow(p, a.boss(), false);
            case AbyssNetwork.Action.LEAVE -> leave(p);
            default -> AbyssNetwork.sendInfo(p);
        }
    }

    // =====================================================================================
    //  Ciclo de vida e eventos
    // =====================================================================================
    static void register() {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post e) -> tick());
        bus.addListener((ServerStartedEvent e) -> {
            server = e.getServer();
            ticks = 0;
            if (AbyssDimension.level(server) == null) {
                IsekaiMahou.LOGGER.warn("Dimensão isekai_mahou:endless_abyss não carregada — Abismo Infinito indisponível neste mundo");
            }
        });
        bus.addListener((ServerStoppingEvent e) -> {
            for (AbyssRun r : List.copyOf(RUNS.values())) if (r.open()) r.shutdown();
        });
        bus.addListener((ServerStoppedEvent e) -> {
            RUNS.clear();
            LATER.clear();
            FALLEN.clear();
            server = null;
        });

        // Antes de todo mundo: lá dentro a morte vira "cair" (ninguém perde itens nem vira espírito)
        bus.addListener(EventPriority.HIGHEST, LivingDeathEvent.class, AbyssManager::onPlayerDeath);
        bus.addListener(LivingIncomingDamageEvent.class, AbyssManager::onIncomingDamage);
        // Depois de quem cancela a morte (fases de chefe) e antes do XP de abate do núcleo (LOWEST): inimigo comum do
        // Abismo rende o XP da criatura normal, não o da vida inflada. Chefes (Sombras) ficam como estão.
        bus.addListener(EventPriority.LOW, LivingDeathEvent.class, e -> {
            if (isRunMob(e.getEntity()) && !e.getEntity().getType().is(BossKit.BOSSES_TAG)) AbyssRun.stripHealthBonus(e.getEntity());
        });
        bus.addListener(EventPriority.HIGHEST, LivingDropsEvent.class, e -> {
            if (isRunMob(e.getEntity())) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGHEST, LivingExperienceDropEvent.class, e -> {
            if (isRunMob(e.getEntity())) e.setCanceled(true);
        });
        bus.addListener(EntityJoinLevelEvent.class, AbyssManager::onJoin);
        bus.addListener(MobSplitEvent.class, e -> {
            if (isRunMob(e.getParent())) e.setCanceled(true);
        });

        // A dimensão é só arena: nada quebra, nada é colocado
        bus.addListener(BlockEvent.BreakEvent.class, e -> {
            if (inAbyss(e.getLevel()) && !e.getPlayer().isCreative()) e.setCanceled(true);
        });
        bus.addListener(BlockEvent.EntityPlaceEvent.class, e -> {
            if (inAbyss(e.getLevel()) && !(e.getEntity() instanceof Player pl && pl.isCreative())) e.setCanceled(true);
        });
        bus.addListener(PlayerInteractEvent.RightClickItem.class, e -> {
            if (AbyssDimension.is(e.getLevel()) && !e.getEntity().isCreative() && e.getItemStack().getItem() instanceof BucketItem) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener(PlayerInteractEvent.RightClickBlock.class, e -> {
            if (!AbyssDimension.is(e.getLevel()) || e.getEntity().isCreative()) return;
            ItemStack s = e.getItemStack();
            if (s.getItem() instanceof FlintAndSteelItem || s.getItem() instanceof FireChargeItem || s.getItem() instanceof BucketItem) {
                e.setUseItem(TriState.FALSE);
            }
        });
        bus.addListener(BlockEvent.PortalSpawnEvent.class, e -> {
            if (inAbyss(e.getLevel())) e.setCanceled(true);
        });
        bus.addListener(ExplosionEvent.Detonate.class, e -> {
            if (AbyssDimension.is(e.getLevel())) e.getAffectedBlocks().clear();
        });
        bus.addListener(EntityMobGriefingEvent.class, e -> {
            if (AbyssDimension.is(e.getEntity().level())) e.setCanGrief(false);
        });
        bus.addListener(LivingDestroyBlockEvent.class, e -> {
            if (AbyssDimension.is(e.getEntity().level())) e.setCanceled(true);
        });
        bus.addListener(EntityTeleportEvent.EnderPearl.class, e -> {
            if (!allowedTeleport(e.getPlayer(), e.getTarget())) e.setCanceled(true);
        });
        bus.addListener(EntityTeleportEvent.ChorusFruit.class, e -> {
            if (e.getEntityLiving() instanceof ServerPlayer sp && !allowedTeleport(sp, e.getTarget())) e.setCanceled(true);
        });
        Destruction.addProtection((level, pos) -> AbyssDimension.is(level));

        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            if (AbyssDimension.is(sp.level())) later(2, () -> evacuate(sp));
            // Quem já era nível 100+ antes da release também recebe o aviso (uma vez)
            later(200, () -> {
                if (!sp.hasDisconnected() && unlocked(sp)) announceUnlock(sp);
            });
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            // Sair do jogo no meio = sair da descida; o ponto de retorno fica salvo e ele é devolvido ao entrar de novo
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            FALLEN.remove(sp.getUUID());
            AbyssRun run = of(sp.getUUID());
            if (run != null) run.removeMember(sp.getUUID());
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, AbyssManager::onChangeDimension);
        bus.addListener(MagicProgressEvent.LevelUp.class, e -> {
            if (e.oldLevel < UNLOCK_LEVEL && e.newLevel >= UNLOCK_LEVEL) announceUnlock(e.getPlayer());
        });
    }

    private static void tick() {
        if (server == null) return;
        ticks++;
        for (AbyssRun run : List.copyOf(RUNS.values())) {
            if (!run.open()) continue;
            try {
                run.tick();
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.error("Abismo Infinito: a vaga {} falhou; fechando", run.index, ex);
                run.close();
            }
        }
        RUNS.values().removeIf(r -> !r.open());
        if (!LATER.isEmpty()) {
            List<Later> due = new ArrayList<>();
            for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
                Later l = it.next();
                if (l.at <= ticks) {
                    due.add(l);
                    it.remove();
                }
            }
            for (Later l : due) l.run.run();
        }
    }

    /** Um aviso só, uma vez na vida, quando o jogador chega ao nível 100. */
    private static void announceUnlock(ServerPlayer p) {
        if (MagicApi.get(p).hasFlag(UNLOCK_FLAG)) return;
        MagicApi.get(p).setFlag(UNLOCK_FLAG, true);
        Rewards.toast(p, Component.translatable("toast.isekai_mahou.endless_abyss.unlocked"),
                Component.translatable("toast.isekai_mahou.endless_abyss.unlocked_sub"), new ItemStack(AbyssContent.ABYSS_CORE.get()),
                AbyssRewards.PURPLE);
    }

    // ------------------------------------------------------------------------------------------------ morte / dano
    private static void onPlayerDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !AbyssDimension.is(sp.level())) return;
        e.setCanceled(true);
        sp.setHealth(Math.max(1F, sp.getMaxHealth() * 0.5F));
        sp.clearFire();
        sp.resetFallDistance();
        sp.setDeltaMovement(Vec3.ZERO);
        // O teleporte entre dimensões fica para o próximo tick (aqui ainda estamos no meio do cálculo do dano)
        if (FALLEN.add(sp.getUUID())) later(1, () -> fall(sp));
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        if (e.getEntity() instanceof ServerPlayer sp && AbyssDimension.is(sp.level())
                && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            AbyssRun run = of(sp.getUUID());
            // Pausa entre andares, vitória ou já "caído": intocável
            if (FALLEN.contains(sp.getUUID()) || (run != null && run.safeNow())) {
                e.setCanceled(true);
                return;
            }
        }
        Entity attacker = e.getSource().getEntity();
        if (attacker != null && attacker != e.getEntity() && e.getAmount() > 0) {
            CompoundTag pd = attacker.getPersistentData();
            if (pd.contains(AbyssRun.DMG_KEY)) e.setAmount(e.getAmount() * Math.max(1F, pd.getFloat(AbyssRun.DMG_KEY)));
        }
    }

    private static boolean isRunMob(Entity e) {
        return !e.level().isClientSide() && e.getPersistentData().hasUUID(AbyssRun.RUN_KEY);
    }

    /** Criatura de uma descida que não existe mais (reinício no meio da luta): não volta ao mundo. */
    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        CompoundTag pd = e.getEntity().getPersistentData();
        if (pd.hasUUID(AbyssRun.RUN_KEY) && byId(pd.getUUID(AbyssRun.RUN_KEY)) == null) e.setCanceled(true);
    }

    private static boolean inAbyss(LevelAccessor level) {
        return level instanceof Level l && AbyssDimension.is(l);
    }

    /** Pérola/coro: só para dentro da própria arena. */
    private static boolean allowedTeleport(ServerPlayer p, Vec3 target) {
        if (!AbyssDimension.is(p.level()) || p.isCreative()) return true;
        AbyssRun run = of(p.getUUID());
        return run != null && AbyssDimension.inside(run.index, target, -0.5);
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        boolean from = e.getFrom() == AbyssDimension.KEY, to = e.getTo() == AbyssDimension.KEY;
        if (from && !to) {
            // Saiu por outro caminho (comando, teleporte de outro mod): sai da descida; o retorno salvo não serve mais
            AbyssRun run = of(sp.getUUID());
            if (run != null) run.removeMember(sp.getUUID());
            if (server != null) data().takeReturn(sp.getUUID());
        } else if (to && !from && of(sp.getUUID()) == null) {
            later(1, () -> evacuate(sp));
        }
    }

    private AbyssManager() {}
}
