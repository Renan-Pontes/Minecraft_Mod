package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.feature.settlements.block.SettlementHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.settlements.entity.BuilderNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.GuardNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.ResidentNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;
import com.renanpontes.isekaimahou.feature.settlements.network.SettlementsNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Cidades governadas por jogadores (docs/V10_DESIGN.md §3 "settlements"): fundação pela Carta, coração, plantas e
 * obras, chegada de moradores/guardas/mercadores/construtores, impostos e afinidade do povo (conta diária), decretos,
 * cidadania e a tela de governo (estado em NBT para o cliente). O Juramento/desafio fica em {@link Conquest} e a
 * guerra em {@link WarService}.
 */
public final class SettlementService {
    public static final int FESTIVAL_COST = 40;
    public static final int FESTIVAL_AFFINITY = 15;
    public static final int MAX_QUEUE = 6;
    private static final int LOST_AFTER = 6000;
    private static final ResourceLocation CITY_HEART = IsekaiMahou.id("city_heart");
    private static final ResourceLocation TRAVEL_STONE = IsekaiMahou.id("travel_stone");
    private static final ResourceLocation GUILD_COIN = IsekaiMahou.id("guild_coin");

    /** Último valor da estatística "negociou com aldeão" por jogador (para contar trocas nas cidades). */
    private static final Map<UUID, Integer> TRADE_STATS = new HashMap<>();
    /** Último aviso de "cidade" mandado ao rastreador (para não repetir pacote igual). */
    private static final Map<UUID, Integer> TRACKER_HASH = new HashMap<>();

    // =====================================================================================
    //  Consultas
    // =====================================================================================
    @Nullable
    public static Settlement city(MinecraftServer server, String id) {
        return id.isEmpty() ? null : SettlementData.get(server).city(id);
    }

    /** Moeda dos impostos e do comércio: Moeda da Guilda (se o módulo existir) ou esmeralda. */
    public static Item coinItem() {
        return BuiltInRegistries.ITEM.getOptional(GUILD_COIN).orElse(Items.EMERALD);
    }

    public static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server, Settlement s) {
        return server.getLevel(s.dim);
    }

    /** O lugar do tipo cidade/capital que contém a posição (mesmo com um lugar menor dentro). */
    @Nullable
    public static Places.Place cityAt(ServerLevel level, BlockPos pos) {
        Places.Place here = Places.at(level, pos);
        if (here == null) return null;
        if (isCity(here)) return here;
        for (Places.Place p : Places.all(level)) {
            if (isCity(p) && p.dim() == level.dimension() && p.contains(pos)) return p;
        }
        return null;
    }

    public static boolean isCity(@Nullable Places.Place p) {
        return p != null && ("city".equals(p.type()) || "capital".equals(p.type()));
    }

    /** Senhor, alguém do grupo dele ou cidadão: a cidade protege. */
    public static boolean isProtected(MinecraftServer server, Settlement s, Player p) {
        if (s.owner == null) return false;
        if (p.getUUID().equals(s.owner)) return true;
        if (Party.members(p).contains(s.owner)) return true;
        return s.id.equals(Allegiance.of(p));
    }

    public static Component cityName(MinecraftServer server, Settlement s) {
        return s.own() && !s.name.isEmpty() ? Component.literal(s.name) : Allegiance.cityName(server, s.id);
    }

    // =====================================================================================
    //  Fundação
    // =====================================================================================
    public static void openFounding(ServerPlayer sp, BlockPos pos) {
        Component problem = foundingProblem(sp, pos);
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withColor(0xE8735E), true);
            sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }
        PacketDistributor.sendToPlayer(sp, new SettlementsNetwork.FoundOpen(pos));
    }

    @Nullable
    public static Component foundingProblem(ServerPlayer sp, BlockPos pos) {
        ServerLevel sl = sp.serverLevel();
        if (sl.dimension() != Level.OVERWORLD) return Component.translatable("settlements.isekai_mahou.found.overworld");
        if (SettlementData.get(sp.server).ownedBy(sp.getUUID()) != null) return Component.translatable("settlements.isekai_mahou.found.already");
        if (!sl.getBlockState(pos).canBeReplaced() || !sl.getBlockState(pos.below()).isSolid()) {
            return Component.translatable("settlements.isekai_mahou.found.ground");
        }
        BoundingBox mine = CityLayout.territory(pos).inflatedBy(8);
        for (Places.Place p : Places.all(sl)) {
            if (p.dim() != sl.dimension()) continue;
            boolean town = isCity(p) || "village".equals(p.type());
            double d = Math.sqrt(p.center().distSqr(pos));
            if (p.box().intersects(mine) || town && d < CityLayout.MIN_CITY_DISTANCE) {
                return Component.translatable("settlements.isekai_mahou.found.too_close", p.name(), (int) d);
            }
        }
        if (findCharter(sp) < 0) return Component.translatable("settlements.isekai_mahou.found.no_charter");
        return null;
    }

    private static int findCharter(ServerPlayer sp) {
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            if (sp.getInventory().getItem(i).is(SettlementsContent.FOUNDING_CHARTER.get())) return i;
        }
        return -1;
    }

    public static String sanitizeName(String raw) {
        StringBuilder b = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c >= 32 && c != '§' && c != 127 && b.length() < 32) b.append(c);
        }
        return b.toString().trim();
    }

    /** Funda a cidade (vindo da tela de estilo). */
    public static void found(ServerPlayer sp, BlockPos pos, SettlementStyle style, String rawName) {
        if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 12 * 12) return;
        Component problem = foundingProblem(sp, pos);
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withColor(0xE8735E), true);
            return;
        }
        ServerLevel sl = sp.serverLevel();
        int slot = findCharter(sp);
        if (!sp.isCreative()) sp.getInventory().getItem(slot).shrink(1);
        String name = sanitizeName(rawName);
        // a tela já sugere um nome traduzido; sem nome nenhum, a cidade leva o nome do fundador
        if (name.isEmpty()) name = sp.getGameProfile().getName();
        String id = "settlement_" + pos.getX() + "_" + pos.getZ();
        Settlement s = new Settlement(id, Settlement.Kind.OWN, style, sl.dimension(), pos, name);
        s.owner = sp.getUUID();
        s.ownerName = sp.getGameProfile().getName();
        s.affinity = 30;
        s.lastDay = day(sp.server);
        s.projects.add(new Project(Blueprint.PLAZA, -1, List.of(new Project.Piece(Blueprint.template(style, Blueprint.PLAZA),
                pos.getX(), pos.getZ(), net.minecraft.world.level.block.Rotation.NONE, Project.Piece.BUILDING, pos.getY() - 1))));
        SettlementData.get(sp.server).put(s);

        sl.setBlock(pos, SettlementsContent.HEART.get().defaultBlockState(), 3);
        if (sl.getBlockEntity(pos) instanceof SettlementHeartBlockEntity be) be.bind(id, style);
        registerPlace(sl, s);
        Allegiance.set(sp, id);
        Allegiance.setLeader(sp.server, id, sp.getUUID());
        Travel.discover(sp, id);
        spawnNpc(sl, s, Settlement.Role.BUILDER, pos.offset(2, 0, 2), pos);

        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 80, 1.5, 1.5, 1.5, 0.3);
        sl.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5F, 0.8F);
        sl.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        Rewards.banner(sp, Component.translatable("settlements.isekai_mahou.found.banner", Component.literal(name)), style.color);
        sp.server.getPlayerList().broadcastSystemMessage(Component.translatable("settlements.isekai_mahou.found.announce",
                sp.getDisplayName(), Component.literal(name).withColor(style.color), style.displayName()), false);
        sendState(sp, id, true);
    }

    /** (Re)registra o lugar da cidade própria (nome, estilo, caixa do território). */
    public static void registerPlace(ServerLevel sl, Settlement s) {
        Places.register(sl, new Places.Place(s.id, Component.literal(s.name), "city", "", s.dim, CityLayout.territory(s.heart),
                s.heart.immutable(), true, s.style.color));
    }

    // =====================================================================================
    //  Coração
    // =====================================================================================
    public static void openHeart(ServerPlayer sp, String cityId) {
        if (city(sp.server, cityId) == null) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.heart.orphan"), true);
            return;
        }
        sendState(sp, cityId, true);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    public static void tickHeart(ServerLevel sl, SettlementHeartBlockEntity be) {
        Settlement s = city(sl.getServer(), be.cityId());
        if (s == null || !s.own()) return;
        int age = be.age();
        if (age == 20 && Places.get(sl, s.id) == null) registerPlace(sl, s);
        ConstructionEngine.tick(sl, s, age);
        if (age % 100 == 60) Lordship.gates(sl, s);
        if (age % 100 == 80 && s.wonder("obelisk")) Lordship.obeliskAura(sl, s);
        if (age % 600 == 300) populate(sl, s);
    }

    /** Coração quebrado (criativo/comando): a cidade acaba. */
    public static void onHeartRemoved(ServerLevel sl, String cityId) {
        Settlement s = city(sl.getServer(), cityId);
        if (s != null && s.own()) dissolve(sl, s);
    }

    /** Abandona a cidade própria: lugar, lealdades, guerras e NPCs somem; os prédios ficam como ruínas habitáveis. */
    public static void dissolve(ServerLevel sl, Settlement s) {
        MinecraftServer server = sl.getServer();
        for (String enemy : new HashSet<>(Allegiance.enemiesOf(server, s.id))) Allegiance.makePeace(server, s.id, enemy);
        for (UUID u : Allegiance.citizens(server, s.id)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) Allegiance.set(p, null);
        }
        Allegiance.setLeader(server, s.id, null);
        ConstructionEngine.clearSigns(sl, s);
        Places.remove(sl, s.id);
        SettlementData.get(server).remove(s.id);
        if (sl.getBlockState(s.heart).is(SettlementsContent.HEART.get())) sl.removeBlock(s.heart, false);
        // os NPCs somem sozinhos no próximo sinal de vida (a cidade não existe mais)
    }

    // =====================================================================================
    //  Plantas e obras
    // =====================================================================================
    /** Por que não dá para construir (null = pode). */
    @Nullable
    public static Component buildProblem(Settlement s, Blueprint bp) {
        if (bp.isPlaza()) return Component.translatable("settlements.isekai_mahou.bp.auto");
        if (!s.has(Blueprint.PLAZA)) return Component.translatable("settlements.isekai_mahou.bp.need_plaza");
        if (bp.minLevel() > s.level) return Component.translatable("settlements.isekai_mahou.bp.need_level", bp.minLevel());
        if (s.count(bp.key()) + s.inProgress(bp.key()) >= bp.maxCount()) return Component.translatable("settlements.isekai_mahou.bp.maxed");
        if (s.projects.size() >= MAX_QUEUE) return Component.translatable("settlements.isekai_mahou.bp.queue_full", MAX_QUEUE);
        if (bp.usesCell() && freeCell(s) < 0) return Component.translatable("settlements.isekai_mahou.bp.no_cell");
        return null;
    }

    /** Primeiro terreno livre liberado no nível atual (mais perto da praça primeiro). */
    public static int freeCell(Settlement s) {
        Set<Integer> used = new HashSet<>(s.buildings.keySet());
        for (Project p : s.projects) used.add(p.cell);
        for (int c : CityLayout.cellsFor(s.level)) if (!used.contains(c)) return c;
        return -1;
    }

    private static void queue(ServerPlayer sp, Settlement s, String key) {
        Blueprint bp = Blueprints.get(s.style, key);
        if (bp == null) return;
        Component problem = buildProblem(s, bp);
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withColor(0xE8735E), true);
            return;
        }
        Project p;
        if (bp.isWall()) {
            p = new Project(key, -1, CityLayout.wallPieces(s.style, s.heart));
        } else {
            int cell = freeCell(s);
            BlockPos c = CityLayout.cellCenter(s.heart, cell);
            int variant = Math.floorMod(s.count(key) + s.inProgress(key) + cell, Blueprints.variants(key));
            p = new Project(key, cell, List.of(new Project.Piece(bp.template(variant), c.getX(), c.getZ(), CityLayout.facingHeart(cell),
                    Project.Piece.BUILDING, Integer.MIN_VALUE)));
        }
        s.projects.add(p);
        sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.bp.queued", bp.displayName()).withColor(s.style.color), true);
        sp.playNotifySound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static void onProjectDone(ServerLevel sl, Settlement s, Project p) {
        Blueprint bp = Blueprints.get(s.style, p.key);
        if (bp == null) return;
        if (bp.isWall()) s.wallDone = true;
        else s.buildings.put(bp.isPlaza() ? CityLayout.CENTER_CELL : p.cell, p.key);
        s.points += bp.points();
        if (bp.isPlaza()) findStone(sl, s);
        BlockPos at = bp.isWall() ? s.heart : p.cell >= 0 ? CityLayout.cellCenter(s.heart, p.cell) : s.heart;
        sl.sendParticles(ParticleTypes.FIREWORK, at.getX() + 0.5, at.getY() + 6, at.getZ() + 0.5, 60, 3, 2, 3, 0.15);
        sl.playSound(null, at, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.2F, 1.2F);
        ServerPlayer owner = s.owner == null ? null : sl.getServer().getPlayerList().getPlayer(s.owner);
        if (owner != null) {
            Rewards.toast(owner, Component.translatable("settlements.isekai_mahou.done.title", bp.displayName()),
                    cityName(sl.getServer(), s), new ItemStack(Items.BRICKS), s.style.color);
        }
        if (s.recomputeLevel()) {
            if (owner != null) {
                Rewards.banner(owner, Component.translatable("settlements.isekai_mahou.level.up", cityName(sl.getServer(), s), s.level),
                        s.style.color);
            }
            sl.playSound(null, s.heart, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.0F, 1.0F);
        }
        populate(sl, s);
        refreshViewers(sl.getServer(), s);
    }

    private static void findStone(ServerLevel sl, Settlement s) {
        Block stone = BuiltInRegistries.BLOCK.getOptional(TRAVEL_STONE).orElse(null);
        if (stone == null) return;
        for (BlockPos p : BlockPos.betweenClosed(s.heart.offset(-7, -1, -7), s.heart.offset(7, 3, 7))) {
            if (sl.getBlockState(p).is(stone)) {
                s.stone = p.immutable();
                return;
            }
        }
    }

    // =====================================================================================
    //  NPCs
    // =====================================================================================
    @Nullable
    public static SettlementNpc spawnNpc(ServerLevel sl, Settlement s, Settlement.Role role, BlockPos at, BlockPos home) {
        EntityType<? extends SettlementNpc> type = switch (role) {
            case BUILDER -> SettlementsContent.BUILDER.get();
            case RESIDENT -> SettlementsContent.RESIDENT.get();
            case GUARD -> SettlementsContent.GUARD.get();
            case MERCHANT -> SettlementsContent.MERCHANT.get();
            case MILITIA -> SettlementsContent.MILITIA.get();
        };
        SettlementNpc npc = type.create(sl);
        if (npc == null) return null;
        int y = sl.hasChunk(at.getX() >> 4, at.getZ() >> 4) ? sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) : at.getY();
        npc.moveTo(at.getX() + 0.5, y, at.getZ() + 0.5, sl.random.nextFloat() * 360F, 0F);
        npc.finalizeSpawn(sl, sl.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
        String faction = s.own() ? "" : placeFaction(sl, s);
        npc.bind(s, home, faction);
        if (npc instanceof GuardNpc g) g.refreshPower(s);
        if (npc instanceof ResidentNpc r) r.setHouse(houseFor(s, s.population(Settlement.Role.RESIDENT)));
        sl.addFreshEntity(npc);
        s.npcs.get(role).add(npc.getUUID());
        s.lastSeen.put(npc.getUUID(), sl.getGameTime());
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, npc.getX(), npc.getY() + 1, npc.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
        return npc;
    }

    private static String placeFaction(ServerLevel sl, Settlement s) {
        Places.Place p = Places.get(sl, s.id);
        return p == null ? "" : p.faction();
    }

    @Nullable
    private static BlockPos houseFor(Settlement s, int index) {
        List<Integer> homes = new ArrayList<>();
        s.buildings.forEach((cell, key) -> {
            if (key.startsWith("house") || key.equals("farm") || key.equals("tavern")) homes.add(cell);
        });
        if (homes.isEmpty()) return null;
        return CityLayout.cellCenter(s.heart, homes.get(index % homes.size()));
    }

    @Nullable
    private static BlockPos workplace(Settlement s, String... keys) {
        for (String k : keys) {
            for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
                if (e.getValue().equals(k)) return CityLayout.cellCenter(s.heart, e.getKey());
            }
        }
        return null;
    }

    /** Sinal de vida de um NPC. Falso = a cidade não o quer mais (sumiu ou está lotada). */
    public static boolean heartbeat(ServerLevel sl, SettlementNpc npc) {
        Settlement s = city(sl.getServer(), npc.cityId());
        if (s == null) return false;
        List<UUID> list = s.npcs.get(npc.role());
        if (!list.contains(npc.getUUID())) {
            if (list.size() >= s.cap(npc.role())) return false;
            list.add(npc.getUUID());
        }
        s.lastSeen.put(npc.getUUID(), sl.getGameTime());
        return true;
    }

    public static void onNpcDeath(ServerLevel sl, SettlementNpc npc, DamageSource source) {
        Settlement s = city(sl.getServer(), npc.cityId());
        if (s == null) return;
        s.npcs.get(npc.role()).remove(npc.getUUID());
        s.lastSeen.remove(npc.getUUID());
        s.affinity = Math.max(0, s.affinity - 1);
        ServerPlayer owner = s.owner == null ? null : sl.getServer().getPlayerList().getPlayer(s.owner);
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("settlements.isekai_mahou.npc.died", npc.getDisplayName(),
                    cityName(sl.getServer(), s)).withColor(0xE8735E), false);
        }
    }

    /** Some com os perdidos e traz quem falta (um por vez; construtores sempre que faltar). */
    public static void populate(ServerLevel sl, Settlement s) {
        long now = sl.getGameTime();
        for (Settlement.Role r : Settlement.Role.values()) {
            if (r == Settlement.Role.MILITIA) continue;   // milicianos andam longe com o Senhor
            s.npcs.get(r).removeIf(u -> {
                Long seen = s.lastSeen.get(u);
                if (seen == null) {
                    s.lastSeen.put(u, now);
                    return false;
                }
                return now - seen > LOST_AFTER;
            });
        }
        if (!s.own()) {
            if (s.population(Settlement.Role.GUARD) < s.cap(Settlement.Role.GUARD)) {
                spawnNpc(sl, s, Settlement.Role.GUARD, s.heart.offset(sl.random.nextInt(7) - 3, 0, sl.random.nextInt(7) - 3), s.heart);
            }
            return;
        }
        int gate = sl.random.nextInt(4);
        BlockPos arrive = CityLayout.arrivalPoint(s.heart, gate, s.wallDone);
        if (s.population(Settlement.Role.BUILDER) < s.cap(Settlement.Role.BUILDER)) {
            announceArrival(sl, s, spawnNpc(sl, s, Settlement.Role.BUILDER, arrive, s.heart));
            return;
        }
        if (!s.has(Blueprint.PLAZA)) return;
        Settlement.Role[] order = {Settlement.Role.GUARD, Settlement.Role.MERCHANT, Settlement.Role.RESIDENT};
        for (Settlement.Role r : order) {
            if (s.population(r) >= s.cap(r)) continue;
            BlockPos home = switch (r) {
                case MERCHANT -> {
                    BlockPos w = workplace(s, "market", "tavern");
                    yield w == null ? s.heart : w;
                }
                default -> s.heart;
            };
            announceArrival(sl, s, spawnNpc(sl, s, r, arrive, home));
            return;
        }
    }

    private static void announceArrival(ServerLevel sl, Settlement s, @Nullable SettlementNpc npc) {
        if (npc == null || s.owner == null) return;
        ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(s.owner);
        if (owner != null && owner.level() == sl && owner.distanceToSqr(npc) < 128 * 128) {
            owner.displayClientMessage(Component.translatable("settlements.isekai_mahou.arrival", npc.getDisplayName()).withColor(s.style.color), true);
        }
    }

    /** Segunda página da conversa de um NPC: o que ele acha da cidade. */
    public static Component npcStatusLine(SettlementNpc npc, Settlement s) {
        MinecraftServer server = npc.getServer();
        switch (npc.role()) {
            case BUILDER -> {
                if (s.projects.isEmpty()) return Component.translatable("settlements.isekai_mahou.status.builder.idle");
                Project p = s.projects.get(0);
                Blueprint bp = Blueprints.get(s.style, p.key);
                return Component.translatable("settlements.isekai_mahou.status.builder.busy",
                        bp == null ? Component.literal(p.key) : bp.displayName(), Math.round(p.progress(1) * 100));
            }
            case GUARD -> {
                if (server != null && !Allegiance.enemiesOf(server, s.id).isEmpty()) {
                    String first = Allegiance.enemiesOf(server, s.id).iterator().next();
                    return Component.translatable("settlements.isekai_mahou.status.guard.war", Allegiance.cityName(server, first));
                }
                return SettlementsLang.tr("status.guard.gates." + s.gates);
            }
            case MERCHANT -> {
                return SettlementsLang.tr("status.merchant.tax." + s.tax);
            }
            case MILITIA -> {
                return SettlementsLang.tr("status.militia.order." + s.militiaOrder);
            }
            default -> {
                String mood = s.affinity >= 80 ? "happy" : s.affinity >= 40 ? "ok" : "sad";
                return SettlementsLang.tr("status.resident." + mood);
            }
        }
    }

    // =====================================================================================
    //  Servidor: contas diárias, trocas, visitas
    // =====================================================================================
    public static void tickServer(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % 20 == 0) {
            Conquest.tickPlayers(server);
            WarService.tickPlayers(server);
        }
        if (t % 100 == 40) {
            pollTrades(server);
            lordVisits(server);
            sendTrackers(server);
        }
        if (t % 200 == 100) {
            long today = day(server);
            SettlementData data = SettlementData.get(server);
            for (Settlement s : data.all()) {
                if (!s.own() && !Lordship.validateConquered(server, s)) continue;
                if (s.lastDay < 0) s.lastDay = today;
                if (today > s.lastDay) {
                    s.lastDay = today;
                    Lordship.daily(server, s);
                }
                if (!s.own() && t % 600 == 100) {
                    ServerLevel sl = level(server, s);
                    if (sl != null && sl.hasChunk(s.heart.getX() >> 4, s.heart.getZ() >> 4)) populate(sl, s);
                }
            }
        }
    }

    /** Conta as trocas feitas DENTRO de cada cidade governada (estatística "negociou", vale para qualquer mercador). */
    private static void pollTrades(MinecraftServer server) {
        SettlementData data = SettlementData.get(server);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            int now = sp.getStats().getValue(Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER));
            Integer before = TRADE_STATS.put(sp.getUUID(), now);
            if (before == null || now <= before) continue;
            Places.Place p = cityAt(sp.serverLevel(), sp.blockPosition());
            Settlement s = p == null ? null : data.city(p.id());
            if (s != null) s.tradesToday += Math.min(64, now - before);
        }
    }

    private static void lordVisits(MinecraftServer server) {
        for (Settlement s : SettlementData.get(server).all()) {
            if (s.lordVisited || s.owner == null) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(s.owner);
            if (p != null && p.level().dimension() == s.dim && p.blockPosition().distSqr(s.heart) < 64 * 64) s.lordVisited = true;
        }
    }

    /** Linhas do rastreador: obras da cidade própria (o Juramento vai junto, ver {@link Conquest#trackerTag}). */
    private static void sendTrackers(MinecraftServer server) {
        SettlementData data = SettlementData.get(server);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            CompoundTag tag = new CompoundTag();
            Settlement own = data.ownedBy(sp.getUUID());
            if (own != null && !own.projects.isEmpty()) {
                Project p = own.projects.get(0);
                Blueprint bp = Blueprints.get(own.style, p.key);
                tag.putString("BuildKey", bp == null ? "" : bp.langKey());
                tag.putInt("BuildPct", Math.round(progressOf(server, own, p) * 100));
                tag.putInt("BuildQueue", own.projects.size());
                tag.put("BuildPos", net.minecraft.nbt.NbtUtils.writeBlockPos(own.heart));
                tag.putString("BuildCity", own.name);
            }
            Conquest.trackerTag(sp, tag);
            int hash = tag.hashCode();
            Integer old = TRACKER_HASH.put(sp.getUUID(), hash);
            if (old == null || old != hash) PacketDistributor.sendToPlayer(sp, new SettlementsNetwork.Tracker(tag));
        }
    }

    /** Progresso de uma obra (com o tamanho certo da fase, se o molde já carregou). */
    public static float progressOf(MinecraftServer server, Settlement s, Project p) {
        ServerLevel sl = level(server, s);
        Project.Piece piece = p.current();
        if (sl == null || piece == null) return p.progress(1);
        var tpl = com.renanpontes.isekaimahou.feature.settlements.build.Templates.get(sl, piece.template);
        if (tpl == null) return p.progress(1);
        int steps = switch (p.phase) {
            case Project.PHASE_CLEAR, Project.PHASE_FOUNDATION -> tpl.width(piece.rotation) * tpl.depth(piece.rotation);
            case Project.PHASE_BUILD -> tpl.entries().size();
            default -> 1;
        };
        return p.progress(steps);
    }

    // =====================================================================================
    //  Coração das cidades de reino (módulo cities): agachar + clicar abre Juramento/governo
    // =====================================================================================
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        BlockState st = sp.level().getBlockState(e.getPos());
        if (!CITY_HEART.equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()))) return;
        boolean seal = sp.getMainHandItem().is(SettlementsContent.OATH_SEAL.get());
        if (!seal && !sp.isShiftKeyDown()) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
        if (seal) {
            Conquest.useSealOn(sp, e.getPos());
            return;
        }
        Places.Place p = cityAt(sp.serverLevel(), e.getPos());
        if (p != null) sendState(sp, p.id(), true);
    }

    // =====================================================================================
    //  Ações da tela
    // =====================================================================================
    public static void handleAction(ServerPlayer sp, String cityId, String action, String arg) {
        MinecraftServer server = sp.server;
        Settlement s = city(server, cityId);
        boolean owner = s != null && sp.getUUID().equals(s.owner);
        switch (action) {
            case "build" -> {
                if (owner && s.own()) queue(sp, s, arg);
            }
            case "cancel" -> {
                if (owner && s.own()) cancel(sp, s, parse(arg, -1));
            }
            case "tax" -> {
                if (owner) s.tax = Math.max(0, Math.min(2, parse(arg, 1)));
            }
            case "gates" -> {
                if (owner) s.gates = Math.max(0, Math.min(2, parse(arg, 0)));
            }
            case "order" -> {
                if (owner) s.militiaOrder = parse(arg, 0) == 1 ? Settlement.ORDER_DEFEND : Settlement.ORDER_FOLLOW;
            }
            case "withdraw" -> {
                if (owner) Lordship.withdraw(sp, s, parse(arg, 64));
            }
            case "festival" -> {
                if (owner) Lordship.festival(sp, s);
            }
            case "declare" -> {
                if (owner) WarService.declare(sp, s, arg);
            }
            case "peace" -> {
                if (owner) WarService.proposePeace(sp, s, arg);
            }
            case "accept_peace" -> {
                if (owner) WarService.acceptPeace(sp, s, arg);
            }
            case "train" -> {
                if (owner) WarService.trainMilitia(sp, s);
            }
            case "join" -> joinCity(sp, cityId);
            case "leave" -> {
                if (cityId.equals(Allegiance.of(sp)) && !owner) Allegiance.set(sp, null);
            }
            case "oath" -> Conquest.startOath(sp, cityId);
            case "tribute" -> Conquest.payTribute(sp);
            case "challenge" -> Conquest.challengeFromMenu(sp, cityId);
            case "abdicate" -> {
                if (owner && !s.own()) Conquest.abdicate(server, s, true);
            }
            case "abandon" -> {
                if (owner && s.own() && "confirm".equals(arg)) {
                    ServerLevel sl = level(server, s);
                    if (sl != null) {
                        sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.abandoned", cityName(server, s)).withColor(0xE8735E), false);
                        dissolve(sl, s);
                    }
                }
            }
            default -> { }
        }
        sendState(sp, cityId, false);
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void cancel(ServerPlayer sp, Settlement s, int index) {
        if (index < 0 || index >= s.projects.size()) return;
        Project p = s.projects.get(index);
        if (p.key.equals(Blueprint.PLAZA)) return;   // a praça é obrigatória
        if (p.pieceIdx > 0 || p.phase > Project.PHASE_FOUNDATION) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.bp.cant_cancel").withColor(0xE8735E), true);
            return;
        }
        s.projects.remove(index);
        ServerLevel sl = level(sp.server, s);
        if (sl != null) ConstructionEngine.clearSigns(sl, s);
    }

    private static void joinCity(ServerPlayer sp, String cityId) {
        if (Allegiance.isEnemyOf(sp, cityId)) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.citizen.enemy").withColor(0xE8735E), true);
            return;
        }
        if (cityId.equals(Allegiance.of(sp))) return;
        for (Settlement mine : SettlementData.get(sp.server).ruledBy(sp.getUUID())) {
            if (!mine.id.equals(cityId)) {
                sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.citizen.ruler").withColor(0xE8735E), true);
                return;
            }
        }
        Allegiance.set(sp, cityId);
        sp.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    // =====================================================================================
    //  Sincronização da tela
    // =====================================================================================
    /** Manda o estado para quem está com a tela aberta desta cidade (o dono, se online). */
    public static void refreshViewers(MinecraftServer server, Settlement s) {
        if (s.owner == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(s.owner);
        if (p != null) sendState(p, s.id, false);
    }

    public static void sendState(ServerPlayer sp, String cityId, boolean open) {
        String id = cityId.isEmpty() ? defaultCity(sp) : cityId;
        CompoundTag tag = id == null ? new CompoundTag() : SettlementView.build(sp, id);
        if (id == null) tag.putString("Mode", "none");
        tag.putBoolean("Open", open);
        PacketDistributor.sendToPlayer(sp, new SettlementsNetwork.State(tag));
    }

    /** Cidade mostrada na aba do menu: a própria, depois uma governada, depois a de lealdade, depois onde está. */
    @Nullable
    private static String defaultCity(ServerPlayer sp) {
        SettlementData data = SettlementData.get(sp.server);
        Settlement own = data.ownedBy(sp.getUUID());
        if (own != null) return own.id;
        List<Settlement> ruled = data.ruledBy(sp.getUUID());
        if (!ruled.isEmpty()) return ruled.get(0).id;
        String loyal = Allegiance.of(sp);
        if (loyal != null) return loyal;
        Places.Place here = cityAt(sp.serverLevel(), sp.blockPosition());
        return here == null ? null : here.id();
    }

    public static MutableComponent coins(int n) {
        return Component.translatable("settlements.isekai_mahou.coins", n);
    }

    public static void clear() {
        TRADE_STATS.clear();
        TRACKER_HASH.clear();
    }

    private SettlementService() {}
}
