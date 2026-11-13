package com.renanpontes.isekaimahou.feature.home_realm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeManager;
import com.renanpontes.isekaimahou.feature.home_realm.HomeData.Home;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Gerenciador da Dimensão Casa (servidor) — "ter uma 'dimensão casa', se comprar libera pra entrar a qualquer hora que
 * não em combate por 2 min".
 * <ul>
 *   <li><b>Comprar</b>: usar a Chave do Lar ({@link #unlock}) cria o terreno do jogador e já começa a construir a ilha.</li>
 *   <li><b>Entrar/sair</b> ({@link #enter}, {@link #leave}): por tecla, menu B ou {@code /mahou home}. Só entra quem está há
 *       {@link #COMBAT_TICKS} sem lutar; ao sair volta exatamente para onde estava.</li>
 *   <li><b>Convidados</b>: o dono convida amigos, que podem visitar quando quiserem; só mexem nas coisas se ele deixar.</li>
 *   <li><b>Seguro</b>: lá dentro jogador não leva dano (sem PvP), monstros não existem, explosões e magias não quebram
 *       nada, e quem cai no vazio volta para a chegada.</li>
 * </ul>
 */
public final class HomeManager {
    /** "Não em combate por 2 min". */
    public static final int COMBAT_TICKS = 2 * 60 * 20;
    /** Blocos da ilha colocados por tick durante a construção (~1 s para a ilha inteira). */
    private static final int BLOCKS_PER_TICK = 1200;
    private static final int GOLD = 0xFFD86B, SKY = 0x8FD8FF;

    public static final TicketController TICKETS = new TicketController(IsekaiMahou.id("home_realm"), (level, helper) -> {
        // No boot nenhuma construção sobrevive: os tickets que sobraram são descartados
        for (UUID owner : List.copyOf(helper.getEntityTickets().keySet())) helper.removeAllTickets(owner);
        for (BlockPos owner : List.copyOf(helper.getBlockTickets().keySet())) helper.removeAllTickets(owner);
    });

    /** Construção da ilha de um terreno, feita aos poucos. */
    private static final class Job {
        final Home home;
        final List<HomeDimension.Placement> list;
        int cursor;

        Job(Home home) {
            this.home = home;
            this.list = HomeDimension.layout(home.index);
        }
    }

    private record Later(long at, Runnable run) {}

    /** Último tick (contador deste módulo) em que o jogador lutou. Não é salvo: reiniciar o servidor zera. */
    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();
    private static final Map<Integer, Job> JOBS = new LinkedHashMap<>();
    /** Pedido de entrada guardado enquanto a ilha sobe. {@code bypass} = veio de um comando de teste. */
    private record Wait(UUID owner, boolean bypass) {}

    /** Jogadores esperando a ilha ficar pronta para serem levados. */
    private static final Map<UUID, Wait> WAITING = new HashMap<>();
    private static final Map<UUID, Long> LAST_DENY = new HashMap<>();
    private static final List<Later> LATER = new ArrayList<>();
    @Nullable private static MinecraftServer server;
    private static long ticks;

    static HomeData data() {
        if (server == null) throw new IllegalStateException("Dimensão Casa usada sem servidor");
        return HomeData.get(server);
    }

    private static void later(int delay, Runnable run) {
        LATER.add(new Later(ticks + Math.max(1, delay), run));
    }

    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable("message.isekai_mahou.home_realm." + key, args);
    }

    private static boolean refuse(ServerPlayer p, Component reason) {
        p.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
        return false;
    }

    // =====================================================================================
    //  Combate ("2 min fora de combate")
    // =====================================================================================
    /** Ticks que faltam para o jogador completar os 2 min sem lutar (0 = já pode entrar). */
    public static int combatLeft(ServerPlayer p) {
        Long last = LAST_COMBAT.get(p.getUUID());
        if (last == null) return 0;
        long left = COMBAT_TICKS - (ticks - last);
        if (left <= 0) {
            LAST_COMBAT.remove(p.getUUID());
            return 0;
        }
        return (int) left;
    }

    static void markCombat(ServerPlayer p) {
        LAST_COMBAT.put(p.getUUID(), ticks);
    }

    static void clearCombat(ServerPlayer p) {
        LAST_COMBAT.remove(p.getUUID());
    }

    // =====================================================================================
    //  Comprar
    // =====================================================================================
    /** O jogador tem um lar? */
    public static boolean owns(ServerPlayer p) {
        return server != null && data().home(p.getUUID()) != null;
    }

    /** Usa a Chave do Lar: cria o terreno do jogador. Devolve false (e avisa) se ele já tem um ou a dimensão não existe. */
    public static boolean unlock(ServerPlayer p) {
        if (server == null || HomeDimension.level(p.server) == null) return refuse(p, msg("deny.no_dimension"));
        if (data().home(p.getUUID()) != null) return refuse(p, msg("deny.already_owned"));
        Home home = data().create(p.getUUID(), p.getGameProfile().getName());
        startJob(home);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.3F);
        Rewards.toast(p, Component.translatable("toast.isekai_mahou.home_realm.unlocked"),
                Component.translatable("toast.isekai_mahou.home_realm.unlocked_sub"), new ItemStack(HomeContent.HOME_KEY.get()), GOLD);
        Hints.show(p, "home_realm_unlocked", Component.keybind("key.isekai_mahou.home_realm"));
        sendInfo(p);
        IsekaiMahou.LOGGER.info("Dimensão Casa: terreno {} criado para {}", home.index, home.ownerName);
        return true;
    }

    // =====================================================================================
    //  Construção da ilha
    // =====================================================================================
    private static void startJob(Home home) {
        ServerLevel level = server == null ? null : HomeDimension.level(server);
        if (level == null || home.built || JOBS.containsKey(home.index)) return;
        for (ChunkPos cp : HomeDimension.chunks(home.index)) TICKETS.forceChunk(level, HomeDimension.center(home.index), cp.x, cp.z, true, true);
        JOBS.put(home.index, new Job(home));
    }

    private static void tickJobs(ServerLevel level) {
        for (Iterator<Job> it = JOBS.values().iterator(); it.hasNext(); ) {
            Job job = it.next();
            int end = Math.min(job.list.size(), job.cursor + BLOCKS_PER_TICK);
            for (; job.cursor < end; job.cursor++) {
                HomeDimension.Placement pl = job.list.get(job.cursor);
                BlockState state = pl.connect() ? Block.updateFromNeighbourShapes(pl.state(), level, pl.pos()) : pl.state();
                level.setBlock(pl.pos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            if (job.cursor < job.list.size()) continue;
            it.remove();
            finishJob(level, job.home);
        }
    }

    private static void finishJob(ServerLevel level, Home home) {
        BlockPos c = HomeDimension.center(home.index);
        plantTrees(level, c);
        // Um agrado de boas-vindas no baú (nada que pule progresso)
        if (level.getBlockEntity(c.offset(3, 1, -8)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.BREAD, 8));
            chest.setItem(1, new ItemStack(Items.TORCH, 16));
            chest.setItem(2, new ItemStack(Items.WHEAT_SEEDS, 8));
            chest.setItem(3, new ItemStack(Items.OAK_SAPLING, 4));
            chest.setItem(4, new ItemStack(Items.BONE_MEAL, 8));
        }
        home.built = true;
        data().touch();
        for (ChunkPos cp : HomeDimension.chunks(home.index)) TICKETS.forceChunk(level, c, cp.x, cp.z, false, true);
        // Quem pediu para entrar enquanto a ilha subia vai agora (as regras são conferidas de novo)
        for (Map.Entry<UUID, Wait> w : List.copyOf(WAITING.entrySet())) {
            if (!w.getValue().owner().equals(home.owner)) continue;
            WAITING.remove(w.getKey());
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(w.getKey());
            if (p != null) enter(p, home.owner, w.getValue().bypass());
        }
    }

    /** Uma cerejeira, um carvalho e uma bétula de verdade (com folhas que se comportam como as do mundo). */
    private static void plantTrees(ServerLevel level, BlockPos c) {
        List<ResourceKey<ConfiguredFeature<?, ?>>> kinds = List.of(TreeFeatures.CHERRY, TreeFeatures.OAK, TreeFeatures.BIRCH);
        var registry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        for (int i = 0; i < HomeDimension.TREES.length; i++) {
            BlockPos at = c.offset(HomeDimension.TREES[i][0], 1, HomeDimension.TREES[i][1]);
            try {
                var feature = registry.getHolder(kinds.get(i % kinds.size()));
                if (feature.isPresent()) {
                    feature.get().value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), at);
                }
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.warn("Dimensão Casa: árvore não plantada em {}", at, ex);
            }
        }
    }

    // =====================================================================================
    //  Entrar
    // =====================================================================================
    private static boolean mayVisit(Home home, UUID player) {
        return home.owner.equals(player) || home.invited.containsKey(player);
    }

    /** Motivo para NÃO poder ir para a Dimensão Casa agora, ou null se pode. {@code bypass} = comando de teste. */
    @Nullable
    private static Component denyReason(ServerPlayer p, boolean bypass) {
        if (server == null || HomeDimension.level(p.server) == null) return msg("deny.no_dimension");
        if (!p.isAlive() || p.isSpectator()) return msg("deny.spectator");
        if (AfterlifeManager.isSpirit(p)) return msg("deny.spirit");
        // Instâncias de luta de outros sistemas têm o próprio ponto de retorno: de lá não se foge para casa
        if (isInstance(p.level().dimension())) return msg("deny.not_here");
        if (BossArena.at(p.level(), p.position()) != null) return msg("deny.boss_fight");
        if (!bypass) {
            int left = combatLeft(p);
            if (left > 0) return msg("deny.combat", clock(left));
        }
        return null;
    }

    /** Dimensões-instância do mod (Provações, Mundo Interior, Abismo Infinito): entram e devolvem o jogador sozinhas. */
    private static boolean isInstance(ResourceKey<Level> dim) {
        String path = dim.location().getPath();
        return dim.location().getNamespace().equals(IsekaiMahou.MODID)
                && (path.equals("trial_realm") || path.equals("inner_world") || path.equals("endless_abyss"));
    }

    private static String clock(int ticksLeft) {
        int s = (ticksLeft + 19) / 20;
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    /**
     * Leva o jogador para o lar de {@code owner} (o dele mesmo ou o de quem o convidou). Devolve false e avisa se não der.
     * Com a ilha ainda em construção o jogador fica na espera e é levado quando ela terminar.
     */
    public static boolean enter(ServerPlayer p, UUID owner, boolean bypass) {
        if (server == null) return false;
        boolean own = owner.equals(p.getUUID());
        Home home = data().home(owner);
        if (home == null) return refuse(p, msg(own ? "deny.not_owned" : "deny.no_home"));
        if (!bypass && !mayVisit(home, p.getUUID())) return refuse(p, msg("deny.not_invited"));
        boolean inside = HomeDimension.is(p.level());
        if (inside && HomeDimension.indexAt(p.getX(), p.getZ()) == home.index) return refuse(p, msg("deny.already_there"));
        // Trocar de um terreno para outro já dentro da dimensão não precisa das regras de entrada
        if (!inside) {
            Component deny = denyReason(p, bypass);
            if (deny != null) return refuse(p, deny);
        }
        if (!home.built) {
            startJob(home);
            WAITING.put(p.getUUID(), new Wait(owner, bypass));
            p.displayClientMessage(msg("preparing").withStyle(ChatFormatting.AQUA), true);
            return true;
        }
        ServerLevel level = HomeDimension.level(p.server);
        if (level == null) return refuse(p, msg("deny.no_dimension"));
        if (!inside) {
            data().putReturn(p.getUUID(), new HomeData.ReturnPoint(p.level().dimension(), p.position(), p.getYRot()));
        }
        Vec3 at = safeArrival(level, home);
        move(p, level, at, home.arrival != null ? home.arrivalYaw : HomeDimension.ARRIVAL_YAW);
        MagicApi.addCounter(p, "visit." + IsekaiMahou.MODID + ".home_realm", 1);
        if (own) {
            p.displayClientMessage(msg("welcome_own").withColor(SKY), true);
            Hints.show(p, "home_realm_arrived");
        } else {
            p.displayClientMessage(msg("welcome_guest", home.ownerName).withColor(SKY), true);
            ServerPlayer host = p.server.getPlayerList().getPlayer(home.owner);
            if (host != null) host.displayClientMessage(msg("guest_arrived", p.getGameProfile().getName()).withColor(SKY), false);
        }
        sendInfo(p);
        return true;
    }

    /**
     * Ponto de chegada seguro: o escolhido pelo dono ou o círculo de pedra. Se estiver tapado sobe até achar espaço; se o
     * chão sumiu (o dono demoliu tudo), repõe um bloco no lugar original para ninguém cair no vazio em laço.
     */
    private static Vec3 safeArrival(ServerLevel level, Home home) {
        Vec3 base = home.arrival != null ? home.arrival : HomeDimension.arrival(home.index);
        if (!HomeDimension.inPlot(home.index, base.x, base.z)) base = HomeDimension.arrival(home.index);
        BlockPos.MutableBlockPos pos = BlockPos.containing(base).mutable();
        int top = level.getMaxBuildHeight() - 3;
        while (pos.getY() < top && !(free(level, pos) && free(level, pos.above()))) pos.move(0, 1, 0);
        while (pos.getY() > HomeDimension.VOID_Y && free(level, pos.below())) pos.move(0, -1, 0);
        if (pos.getY() <= HomeDimension.VOID_Y) {
            Vec3 def = HomeDimension.arrival(home.index);
            BlockPos floor = BlockPos.containing(def).below();
            level.setBlockAndUpdate(floor, Blocks.STONE_BRICKS.defaultBlockState());
            level.setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(floor.above(2), Blocks.AIR.defaultBlockState());
            return def;
        }
        double floorTop = level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).max(net.minecraft.core.Direction.Axis.Y);
        double y = pos.getY() - 1 + (Double.isFinite(floorTop) && floorTop > 0 ? floorTop : 1.0);
        return new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty();
    }

    private static void move(ServerPlayer p, ServerLevel dest, Vec3 pos, float yaw) {
        fx(p.serverLevel(), p.position());
        if (p.isSleeping()) p.stopSleepInBed(true, true);
        p.stopRiding();
        p.teleportTo(dest, pos.x, pos.y, pos.z, yaw, 0F);
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
        p.invulnerableTime = 60;
        fx(dest, pos);
    }

    /** Brilho curto e um som de sino: discreto, só para marcar a ida e a volta. */
    private static void fx(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 14, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.1F);
    }

    // =====================================================================================
    //  Sair
    // =====================================================================================
    /** Sai da Dimensão Casa e volta para onde estava. */
    public static boolean leave(ServerPlayer p) {
        if (server == null) return false;
        if (WAITING.remove(p.getUUID()) != null && !HomeDimension.is(p.level())) return true;
        if (!HomeDimension.is(p.level())) return refuse(p, msg("deny.not_in"));
        sendBack(p);
        sendInfo(p);
        return true;
    }

    /** Devolve o jogador ao ponto de onde entrou (ou ao spawn do mundo, se não houver registro). */
    private static void sendBack(ServerPlayer p) {
        HomeData.ReturnPoint rp = data().takeReturn(p.getUUID());
        ServerLevel dest = rp == null ? null : p.server.getLevel(rp.dim());
        Vec3 pos;
        float yaw;
        if (dest == null || HomeDimension.is(dest)) {
            dest = p.server.overworld();
            BlockPos spawn = dest.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dest.getSharedSpawnPos());
            pos = Vec3.atBottomCenterOf(spawn);
            yaw = dest.getSharedSpawnAngle();
        } else {
            pos = rp.pos();
            yaw = rp.yaw();
        }
        move(p, dest, pos, yaw);
    }

    /** Está na dimensão num lugar onde não pode estar (convite retirado, teleporte de outro mod): é levado para fora. */
    private static void evacuate(ServerPlayer p) {
        if (p.hasDisconnected() || !HomeDimension.is(p.level())) return;
        p.sendSystemMessage(msg("evacuated").withStyle(ChatFormatting.YELLOW));
        sendBack(p);
        sendInfo(p);
    }

    // =====================================================================================
    //  Convites e opções do dono
    // =====================================================================================
    static void invite(ServerPlayer owner, UUID targetId) {
        Home home = data().home(owner.getUUID());
        if (home == null) {
            refuse(owner, msg("deny.not_owned"));
            return;
        }
        ServerPlayer target = owner.server.getPlayerList().getPlayer(targetId);
        if (target == null || target == owner) {
            refuse(owner, msg("deny.invite_offline"));
            return;
        }
        if (home.invited.containsKey(targetId)) return;
        if (home.invited.size() >= HomeNetwork.MAX_PEOPLE) {
            refuse(owner, msg("deny.invite_full", HomeNetwork.MAX_PEOPLE));
            return;
        }
        home.invited.put(targetId, target.getGameProfile().getName());
        data().touch();
        owner.displayClientMessage(msg("invited", target.getGameProfile().getName()).withColor(SKY), true);
        target.sendSystemMessage(msg("invite_received", owner.getGameProfile().getName()).withColor(SKY));
        Hints.show(target, "home_realm_invited");
        sendInfo(owner);
        sendInfo(target);
    }

    static void uninvite(ServerPlayer owner, UUID targetId) {
        Home home = data().home(owner.getUUID());
        if (home == null || home.invited.remove(targetId) == null) return;
        data().touch();
        Wait waiting = WAITING.get(targetId);
        if (waiting != null && waiting.owner().equals(owner.getUUID())) WAITING.remove(targetId);
        ServerPlayer target = owner.server.getPlayerList().getPlayer(targetId);
        if (target != null) {
            if (HomeDimension.is(target.level()) && HomeDimension.indexAt(target.getX(), target.getZ()) == home.index
                    && !target.isSpectator()) {
                evacuate(target);
            }
            sendInfo(target);
        }
        sendInfo(owner);
    }

    private static void toggleGuestsBuild(ServerPlayer owner) {
        Home home = data().home(owner.getUUID());
        if (home == null) return;
        home.guestsBuild = !home.guestsBuild;
        data().touch();
        owner.displayClientMessage(msg(home.guestsBuild ? "guests_build_on" : "guests_build_off").withColor(SKY), true);
        sendInfo(owner);
    }

    /** O dono marca onde está como ponto de chegada do lar. */
    private static void setArrival(ServerPlayer owner) {
        Home home = data().home(owner.getUUID());
        if (home == null || !HomeDimension.is(owner.level()) || !owner.onGround()
                || !HomeDimension.inPlot(home.index, owner.getX(), owner.getZ())
                || HomeDimension.indexAt(owner.getX(), owner.getZ()) != home.index) {
            refuse(owner, msg("deny.arrival"));
            return;
        }
        home.arrival = owner.position();
        home.arrivalYaw = owner.getYRot();
        data().touch();
        owner.displayClientMessage(msg("arrival_set").withColor(SKY), true);
    }

    // =====================================================================================
    //  Pacotes (tecla e aba)
    // =====================================================================================
    static void onAction(ServerPlayer p, HomeNetwork.Action a) {
        if (server == null) return;
        switch (a.op()) {
            case HomeNetwork.Action.TOGGLE -> {
                if (HomeDimension.is(p.level())) leave(p);
                else enter(p, p.getUUID(), false);
            }
            case HomeNetwork.Action.ENTER -> enter(p, p.getUUID(), false);
            case HomeNetwork.Action.LEAVE -> leave(p);
            case HomeNetwork.Action.VISIT -> enter(p, a.target(), false);
            case HomeNetwork.Action.INVITE -> invite(p, a.target());
            case HomeNetwork.Action.UNINVITE -> uninvite(p, a.target());
            case HomeNetwork.Action.GUESTS_BUILD -> toggleGuestsBuild(p);
            case HomeNetwork.Action.SET_ARRIVAL -> setArrival(p);
            default -> sendInfo(p);
        }
    }

    /** Manda o estado atual para o cliente do jogador (aba "Lar" e tecla). */
    static void sendInfo(ServerPlayer p) {
        if (server == null) return;
        HomeData d = data();
        Home own = d.home(p.getUUID());
        boolean in = HomeDimension.is(p.level());
        String host = "";
        if (in) {
            Home here = d.byIndex(HomeDimension.indexAt(p.getX(), p.getZ()));
            if (here != null && !here.owner.equals(p.getUUID())) host = here.ownerName;
        }
        List<HomeNetwork.Person> invited = new ArrayList<>();
        if (own != null) {
            own.invited.forEach((id, name) -> invited.add(new HomeNetwork.Person(id, name, p.server.getPlayerList().getPlayer(id) != null)));
        }
        List<HomeNetwork.Person> visitable = new ArrayList<>();
        for (Home h : d.invitedTo(p.getUUID())) {
            visitable.add(new HomeNetwork.Person(h.owner, h.ownerName, p.server.getPlayerList().getPlayer(h.owner) != null));
        }
        PacketDistributor.sendToPlayer(p, new HomeNetwork.Info(own != null, in, host, combatLeft(p), own != null && own.guestsBuild,
                invited, visitable));
    }

    // =====================================================================================
    //  Proteção
    // =====================================================================================
    /** O jogador pode mexer (quebrar, colocar, abrir baús) nesta posição da Dimensão Casa? */
    static boolean canModify(Player player, BlockPos pos) {
        if (server == null) return false;
        if (player.isCreative() && player.hasPermissions(2)) return true; // admin arrumando algo
        int index = HomeDimension.indexAt(pos.getX(), pos.getZ());
        Home home = data().byIndex(index);
        if (home == null || !HomeDimension.inPlot(index, pos)) return false;
        if (home.owner.equals(player.getUUID())) return true;
        return home.guestsBuild && home.invited.containsKey(player.getUUID());
    }

    /** Aviso curto na barra de ação, no máximo um a cada 2 s (clicar várias vezes não enche a tela). */
    private static void denyNotice(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        Long last = LAST_DENY.get(sp.getUUID());
        if (last != null && ticks - last < 40) return;
        LAST_DENY.put(sp.getUUID(), ticks);
        refuse(sp, msg("deny.not_yours"));
    }

    private static boolean inHome(LevelAccessor level) {
        return level instanceof Level l && !l.isClientSide() && HomeDimension.is(l);
    }

    /** Blocos que um convidado sem permissão ainda pode usar (passar por portas, deitar, usar a bancada). */
    private static boolean guestUsable(BlockState state) {
        Block b = state.getBlock();
        return b instanceof DoorBlock || b instanceof FenceGateBlock || b instanceof TrapDoorBlock || b instanceof ButtonBlock
                || b instanceof BedBlock || b instanceof CraftingTableBlock || b instanceof EnderChestBlock;
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
            if (HomeDimension.level(server) == null) {
                IsekaiMahou.LOGGER.warn("Dimensão isekai_mahou:home_realm não carregada — Dimensão Casa indisponível neste mundo");
            }
        });
        bus.addListener((ServerStoppedEvent e) -> {
            LAST_COMBAT.clear();
            JOBS.clear();
            WAITING.clear();
            LAST_DENY.clear();
            LATER.clear();
            server = null;
        });

        // --- combate: bater em algo vivo ou apanhar de algo vivo zera os 2 min (dano de queda, fogo etc. não conta)
        bus.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, e -> {
            LivingEntity victim = e.getEntity();
            if (victim.level().isClientSide() || e.getAmount() <= 0 || HomeDimension.is(victim.level())) return;
            Entity attacker = e.getSource().getEntity();
            if (attacker == null || attacker == victim || victim instanceof ArmorStand) return;
            if (victim instanceof ServerPlayer vp && attacker instanceof LivingEntity) markCombat(vp);
            // Abater uma vaca para o jantar não é luta: só conta bater em inimigo, em jogador ou em bicho que revida
            if (attacker instanceof ServerPlayer ap && (victim instanceof Enemy || victim instanceof Player
                    || (victim instanceof Mob mob && mob.getTarget() != null))) markCombat(ap);
        });

        // --- seguro: jogador não leva dano lá dentro (sem PvP, sem queda, sem afogar); /kill continua valendo
        bus.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, e -> {
            LivingEntity victim = e.getEntity();
            if (!inHome(victim.level()) || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            if (victim instanceof Player) {
                e.setCanceled(true);
                return;
            }
            // Bichos e enfeites do dono: visita sem permissão não machuca (golpe, flecha, magia)
            if (e.getSource().getEntity() instanceof Player attacker && !canModify(attacker, victim.blockPosition())) e.setCanceled(true);
        });
        bus.addListener(MobEffectEvent.Applicable.class, e -> {
            // Poção ruim jogada por outra pessoa não pega em jogador
            if (!(e.getEntity() instanceof Player target) || !inHome(target.level())) return;
            Entity src = e.getEffectSource();
            if (src != null && src != target && e.getEffectInstance().getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        });
        // Sem monstros: nenhum inimigo entra na dimensão (ovo, gerador, invocação solta). Bichos com dono passam.
        bus.addListener(EntityJoinLevelEvent.class, e -> {
            if (e.getLevel().isClientSide() || !HomeDimension.is(e.getLevel())) return;
            Entity en = e.getEntity();
            if (en instanceof Enemy && !(en instanceof OwnableEntity o && o.getOwnerUUID() != null)) e.setCanceled(true);
        });

        // --- cada um mexe só no próprio terreno (ou no do amigo que deixou)
        bus.addListener(BlockEvent.BreakEvent.class, e -> {
            if (inHome(e.getLevel()) && !canModify(e.getPlayer(), e.getPos())) {
                e.setCanceled(true);
                denyNotice(e.getPlayer());
            }
        });
        bus.addListener(BlockEvent.EntityPlaceEvent.class, e -> {
            if (inHome(e.getLevel()) && e.getEntity() instanceof Player pl && !canModify(pl, e.getPos())) {
                e.setCanceled(true);
                denyNotice(pl);
            }
        });
        bus.addListener(BlockEvent.FarmlandTrampleEvent.class, e -> {
            if (inHome(e.getLevel()) && !(e.getEntity() instanceof Player pl && canModify(pl, e.getPos()))) e.setCanceled(true);
        });
        bus.addListener(BlockEvent.BlockToolModificationEvent.class, e -> {
            if (inHome(e.getLevel()) && !e.isSimulated() && e.getPlayer() != null && !canModify(e.getPlayer(), e.getPos())) e.setCanceled(true);
        });
        bus.addListener(PlayerInteractEvent.RightClickBlock.class, e -> {
            if (!inHome(e.getLevel()) || canModify(e.getEntity(), e.getPos())) return;
            e.setUseItem(TriState.FALSE);
            if (!guestUsable(e.getLevel().getBlockState(e.getPos()))) {
                e.setUseBlock(TriState.FALSE);
                denyNotice(e.getEntity());
            }
        });
        bus.addListener(PlayerInteractEvent.RightClickItem.class, e -> {
            // Baldes, barcos, ovos e vitórias-régias miram o bloco por conta própria: aqui é onde dá para barrar
            if (!inHome(e.getLevel())) return;
            ItemStack s = e.getItemStack();
            if (!(s.getItem() instanceof BucketItem || s.getItem() instanceof BoatItem || s.getItem() instanceof SpawnEggItem
                    || s.getItem() instanceof PlaceOnWaterBlockItem)) return;
            if (!canModify(e.getEntity(), e.getEntity().blockPosition())) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener(PlayerInteractEvent.EntityInteract.class, e -> {
            if (inHome(e.getLevel()) && !(e.getTarget() instanceof Player) && !canModify(e.getEntity(), e.getTarget().blockPosition())) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener(PlayerInteractEvent.EntityInteractSpecific.class, e -> {
            if (inHome(e.getLevel()) && !(e.getTarget() instanceof Player) && !canModify(e.getEntity(), e.getTarget().blockPosition())) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener(AttackEntityEvent.class, e -> {
            if (inHome(e.getEntity().level()) && !(e.getTarget() instanceof Player)
                    && !canModify(e.getEntity(), e.getTarget().blockPosition())) e.setCanceled(true);
        });
        // Nada explode nem abre portal lá dentro; magias não destroem blocos
        bus.addListener(ExplosionEvent.Detonate.class, e -> {
            if (!HomeDimension.is(e.getLevel())) return;
            e.getAffectedBlocks().clear();
            e.getAffectedEntities().clear();
        });
        bus.addListener(BlockEvent.PortalSpawnEvent.class, e -> {
            if (inHome(e.getLevel())) e.setCanceled(true);
        });
        Destruction.addProtection((level, pos) -> HomeDimension.is(level));

        // --- camas: dormir lá passa a noite do mundo se ninguém acordado ficou do lado de fora
        bus.addListener(SleepFinishedTimeEvent.class, e -> {
            if (!(e.getLevel() instanceof ServerLevel sl) || !HomeDimension.is(sl)) return;
            if (!sl.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return;
            for (ServerPlayer other : sl.getServer().getPlayerList().getPlayers()) {
                if (other.isSpectator() || HomeDimension.is(other.level()) || other.isSleepingLongEnough()) continue;
                for (ServerPlayer sleeper : sl.players()) {
                    if (sleeper.isSleeping()) sleeper.displayClientMessage(msg("sleep_no_skip"), true);
                }
                return;
            }
            sl.getServer().overworld().setDayTime(e.getNewTime());
        });

        // --- entrar no jogo, sair, mudar de dimensão
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (!(e.getEntity() instanceof ServerPlayer sp) || server == null) return;
            String name = sp.getGameProfile().getName();
            HomeData d = data();
            Home own = d.home(sp.getUUID());
            if (own != null && !name.equals(own.ownerName)) {
                own.ownerName = name;
                d.touch();
            }
            for (Home h : d.invitedTo(sp.getUUID())) {
                if (!name.equals(h.invited.get(sp.getUUID()))) {
                    h.invited.put(sp.getUUID(), name);
                    d.touch();
                }
            }
            later(20, () -> {
                if (!sp.hasDisconnected()) sendInfo(sp);
            });
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            WAITING.remove(e.getEntity().getUUID());
            LAST_DENY.remove(e.getEntity().getUUID());
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (!(e.getEntity() instanceof ServerPlayer sp) || server == null) return;
            boolean from = e.getFrom() == HomeDimension.KEY, to = e.getTo() == HomeDimension.KEY;
            // Saiu por outro caminho (comando, pergaminho, teleporte de outro mod): o retorno guardado não serve mais.
            // Exceção: instâncias que devolvem o jogador para cá (Abismo, Provações) — o retorno continua valendo.
            if (from && !to && !isInstance(e.getTo())) data().takeReturn(sp.getUUID());
            if (from != to) later(1, () -> {
                if (!sp.hasDisconnected()) sendInfo(sp);
            });
        });
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (!(e.getEntity() instanceof ServerPlayer sp) || server == null) return;
            // Renasceu na cama do Lar depois de morrer lá fora: "onde estava" não existe mais (sair leva ao spawn do mundo)
            if (!e.isEndConquered() && HomeDimension.is(sp.level())) data().takeReturn(sp.getUUID());
            later(1, () -> {
                if (!sp.hasDisconnected()) sendInfo(sp);
            });
        });
    }

    private static void tick() {
        if (server == null) return;
        ticks++;
        ServerLevel level = HomeDimension.level(server);
        if (level != null) {
            if (!JOBS.isEmpty()) tickJobs(level);
            if (ticks % 10 == 0 && !level.players().isEmpty()) watch(level);
        }
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

    /**
     * Ronda dos jogadores que estão na dimensão (2× por segundo, só eles): quem está num terreno onde não pode estar é
     * levado para fora; quem caiu no vazio ou saiu voando da área do terreno volta para a chegada.
     */
    private static void watch(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (!p.isAlive() || p.isSpectator() || (p.isCreative() && p.hasPermissions(2))) continue;
            int index = HomeDimension.indexAt(p.getX(), p.getZ());
            Home here = data().byIndex(index);
            if (here == null || !here.built || !mayVisit(here, p.getUUID())) {
                // Fora de qualquer terreno dele: se tem lar, volta para o lar; senão sai da dimensão
                Home own = data().home(p.getUUID());
                if (here == null && own != null && own.built) move(p, level, safeArrival(level, own), HomeDimension.ARRIVAL_YAW);
                else evacuate(p);
                continue;
            }
            if (p.getY() < HomeDimension.VOID_Y || !HomeDimension.inPlot(index, p.getX(), p.getZ())) {
                move(p, level, safeArrival(level, here), here.arrival != null ? here.arrivalYaw : HomeDimension.ARRIVAL_YAW);
                p.displayClientMessage(msg("void_rescue").withColor(SKY), true);
            }
        }
    }

    private HomeManager() {}
}
