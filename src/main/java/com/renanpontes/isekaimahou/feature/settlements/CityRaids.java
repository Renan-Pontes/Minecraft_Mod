package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Ataques de monstros às cidades fundadas (v1.4) — SEM chefes, só criaturas comuns, mais fortes conforme o nível do
 * centro. A partir do nível 2, a cada poucos dias um ataque fica "marcado" e só acontece com alguém da cidade
 * (governante, conselheiro, cidadão) por perto e o coração carregado. Aviso 2 minutos antes ("uma horda se aproxima do
 * norte"), 2–3 ondas vindas daquele lado. Segurança (guardas, quartel, torres, muralha) diminui a horda e o prejuízo.
 * <ul>
 *   <li>Vitória: moedas no cofre, apoio +6, recompensa para quem defendeu;</li>
 *   <li>Derrota (a horda fica no coração ou o tempo acaba): apoio e comida caem, 1–2 prédios ficam DANIFICADOS (blocos
 *       arrancados, efeito pela metade) até o conserto no planejamento (de graça, ou pago do cofre para ser na hora).</li>
 * </ul>
 * Liga/desliga para o mundo todo: {@code /mahou city raids on|off}. Estado do ataque só em memória (servidor reiniciado =
 * o ataque some e os monstros marcados são recolhidos).
 */
public final class CityRaids {
    public static final String TAG = "isekai_mahou_raid";
    private static final int WARN_TICKS = 2400;
    private static final int DURATION = 6000;
    private static final int BREACH_LIMIT = 40;
    private static final int MEMBER_RANGE = 96;
    private static final String[] SIDES = {"north", "south", "west", "east"};

    private static final class Raid {
        final String city;
        final int side;
        final long startAt;
        final long endAt;
        final int waves;
        int wave;
        boolean spawned;
        int breach;
        long lastMember;
        boolean warnedMinute;
        final Set<UUID> mobs = new LinkedHashSet<>();

        Raid(String city, int side, long startAt, int waves) {
            this.city = city;
            this.side = side;
            this.startAt = startAt;
            this.endAt = startAt + DURATION;
            this.waves = waves;
        }
    }

    private static final Map<String, Raid> ACTIVE = new HashMap<>();

    public static boolean active(String cityId) {
        return ACTIVE.containsKey(cityId);
    }

    /** Conta diária: marca o próximo ataque. */
    public static void daily(MinecraftServer server, Settlement s) {
        if (!s.own() || s.level < 2 || !SettlementData.get(server).raidsEnabled) return;
        long today = SettlementService.day(server);
        if (s.nextRaidDay < 0) s.nextRaidDay = today + 2;
        if (today >= s.nextRaidDay) s.raidDue = true;
        // v1.0 (defesa de cerco): com Torre de Vigia, os batedores avisam UM DIA ANTES (um aviso só por ataque)
        if (!s.raidDue && today == s.nextRaidDay - 1 && s.scoutDay != s.nextRaidDay && CityDefense.hasScouts(s)) {
            s.scoutDay = s.nextRaidDay;
            for (ServerPlayer p : CityEconomy.governors(server, s)) {
                p.displayClientMessage(SettlementsLang.tr("raid.scouts", SettlementService.cityName(server, s)).withColor(0xE8A440), false);
            }
        }
    }

    /** A cada 20 ticks (servidor). */
    public static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (!ACTIVE.isEmpty()) {
            for (Raid r : new ArrayList<>(ACTIVE.values())) step(server, r, t);
        }
        if (t % 200 != 0) return;
        SettlementData data = SettlementData.get(server);
        for (Settlement s : data.all()) {
            if (!s.own()) continue;
            ServerLevel sl = SettlementService.level(server, s);
            if (sl == null || !sl.hasChunk(s.heart.getX() >> 4, s.heart.getZ() >> 4)) continue;
            if (s.raidDue && data.raidsEnabled && !ACTIVE.containsKey(s.id) && memberNear(server, s)) {
                start(sl, s);
            } else if (!ACTIVE.containsKey(s.id) && t % 1200 == 0) {
                sweepLeftovers(sl, s);
            }
        }
    }

    private static boolean memberNear(MinecraftServer server, Settlement s) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() != s.dim || p.isSpectator()) continue;
            if (p.blockPosition().distSqr(s.heart) > MEMBER_RANGE * MEMBER_RANGE) continue;
            if (CityEconomy.isMember(server, s, p)) return true;
        }
        return false;
    }

    /** Começa o aviso (comando de teste também usa). */
    public static void start(ServerLevel sl, Settlement s) {
        RandomSource r = sl.random;
        long now = sl.getGameTime();
        Raid raid = new Raid(s.id, r.nextInt(4), now + WARN_TICKS, s.level >= 4 ? 3 : 2);
        raid.lastMember = now;
        ACTIVE.put(s.id, raid);
        long today = SettlementService.day(sl.getServer());
        s.raidDue = false;
        s.nextRaidDay = today + Math.max(2, 5 - s.level / 2) + r.nextInt(3);
        broadcast(sl, s, SettlementsLang.tr("raid.warn", SettlementService.cityName(sl.getServer(), s),
                SettlementsLang.tr("raid.side." + SIDES[raid.side]), 2), 0xE8735E);
        // v1.0: o aviso também aparece na faixa do topo para quem governa (mesmo longe do chat)
        for (ServerPlayer g : CityEconomy.governors(sl.getServer(), s)) {
            Rewards.banner(g, SettlementsLang.tr("raid.banner", SettlementService.cityName(sl.getServer(), s),
                    SettlementsLang.tr("raid.side." + SIDES[raid.side])), 0xE8735E);
        }
        sl.playSound(null, s.heart, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 6.0F, 1.0F);
    }

    private static void step(MinecraftServer server, Raid raid, int tick) {
        Settlement s = SettlementService.city(server, raid.city);
        ServerLevel sl = s == null ? null : SettlementService.level(server, s);
        if (s == null || sl == null) {
            ACTIVE.remove(raid.city);
            return;
        }
        long now = sl.getGameTime();
        if (memberNear(server, s)) raid.lastMember = now;
        boolean loaded = sl.hasChunk(s.heart.getX() >> 4, s.heart.getZ() >> 4);
        if (!loaded || now - raid.lastMember > 1200) {
            // todo mundo foi embora: o ataque fica para depois (sem prejuízo)
            discardMobs(sl, raid);
            ACTIVE.remove(raid.city);
            s.raidDue = true;
            return;
        }
        if (!raid.spawned) {
            if (!raid.warnedMinute && raid.startAt - now <= 1200) {
                raid.warnedMinute = true;
                broadcast(sl, s, SettlementsLang.tr("raid.warn", SettlementService.cityName(server, s),
                        SettlementsLang.tr("raid.side." + SIDES[raid.side]), 1), 0xE8735E);
            }
            if (now >= raid.startAt) {
                raid.spawned = true;
                spawnWave(sl, s, raid);
                broadcast(sl, s, SettlementsLang.tr("raid.arrived", raid.wave, raid.waves), 0xE8735E);
                sl.playSound(null, s.heart, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 6.0F, 0.8F);
            }
            return;
        }
        List<Mob> alive = new ArrayList<>();
        raid.mobs.removeIf(u -> {
            Entity e = sl.getEntity(u);
            if (e instanceof Mob m && m.isAlive()) {
                alive.add(m);
                return false;
            }
            return e == null ? false : true;
        });
        // monstros longe (chunk descarregado) contam como vivos até o tempo acabar
        int near = 0;
        for (Mob m : alive) {
            if (m.blockPosition().distSqr(s.heart) < 12 * 12) near++;
            if (tick % 40 == 0) steer(sl, s, m);
        }
        raid.breach += near;
        // v1.0 (defesa de cerco): torres atiram, armadilhas ferem e atrasam
        CityDefense.assist(sl, s, alive, tick);
        if (raid.mobs.isEmpty()) {
            if (raid.wave < raid.waves) {
                spawnWave(sl, s, raid);
                broadcast(sl, s, SettlementsLang.tr("raid.wave", raid.wave, raid.waves), 0xE8A440);
            } else {
                win(sl, s, raid);
            }
            return;
        }
        if (raid.breach >= BREACH_LIMIT || now > raid.endAt) lose(sl, s, raid);
    }

    private static void steer(ServerLevel sl, Settlement s, Mob m) {
        // onda 7 (sky_access): atacante que caiu da Ilha Celeste volta para a borda (senão o ataque ficaria esperando por ele)
        if (s.sky && SkyAccess.raidRecover(sl, s, m)) return;
        // onda 8 (water_city): atacante boiando longe de tudo sobe na plataforma mais perto
        if (s.water && WaterLife.raidRecover(sl, s, m)) return;
        LivingEntity target = m.getTarget();
        if (target != null && target.isAlive()) return;
        AABB box = m.getBoundingBox().inflate(16, 6, 16);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof SettlementNpc || e instanceof ServerPlayer sp && !sp.isCreative() && !sp.isSpectator())) {
            double d = e.distanceToSqr(m);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best != null) {
            m.setTarget(best);
        } else {
            m.getNavigation().moveTo(s.heart.getX() + 0.5, s.heart.getY(), s.heart.getZ() + 0.5, 1.0);
        }
    }

    private static List<EntityType<? extends Mob>> pool(Settlement s) {
        List<EntityType<? extends Mob>> l = new ArrayList<>(List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER));
        if (s.level >= 3) {
            l.add(switch (s.style) {
                case DESERT -> EntityType.HUSK;
                case NORDIC, DWARVEN -> EntityType.STRAY;
                case GOTHIC -> EntityType.WITHER_SKELETON;
                case ELVEN -> EntityType.PILLAGER;
                default -> EntityType.ZOMBIE_VILLAGER;
            });
        }
        if (s.level >= 4) {
            l.add(EntityType.VINDICATOR);
            l.add(EntityType.WITCH);
        }
        if (s.level >= 5) {
            l.add(EntityType.PILLAGER);
            l.add(EntityType.VINDICATOR);
        }
        return l;
    }

    private static void spawnWave(ServerLevel sl, Settlement s, Raid raid) {
        raid.wave++;
        if (raid.wave == 1) CityDefense.rally(sl, s);
        float sec = Math.min(1F, CityEconomy.stats(sl.getServer(), s).securityRatio());
        int count = Math.max(3, Math.round((3 + 2 * s.level + raid.wave) * (1.1F - 0.3F * sec)));
        // onda 8 (water_city): numa Cidade das Águas os afogados entram no elenco
        List<EntityType<? extends Mob>> pool = WaterLife.raidPool(s, pool(s));
        int d = CityLayout.half(s) + 8;
        int bx = s.heart.getX() + (raid.side == 2 ? -d : raid.side == 3 ? d : 0);
        int bz = s.heart.getZ() + (raid.side == 0 ? -d : raid.side == 1 ? d : 0);
        RandomSource r = sl.random;
        for (int i = 0; i < count; i++) {
            int x = bx + (raid.side <= 1 ? r.nextInt(21) - 10 : r.nextInt(5) - 2);
            int z = bz + (raid.side >= 2 ? r.nextInt(21) - 10 : r.nextInt(5) - 2);
            if (!sl.hasChunk(x >> 4, z >> 4)) continue;
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (s.sky) {
                // onda 7 (sky_access): numa Ilha Celeste a horda chega "pelo ar" — nasce em terra da ilha, nunca no chão lá embaixo
                BlockPos land = SkyAccess.raidSpot(sl, s, x, z);
                if (land == null) continue;
                x = land.getX();
                y = land.getY();
                z = land.getZ();
            }
            boolean sea = false;
            if (s.water) {
                // onda 8 (water_city): a horda chega "pelo mar" — nasce na borda das plataformas (sem piso ali, chega nadando)
                BlockPos edge = WaterLife.raidSpot(sl, s, x, z);
                if (edge != null) {
                    x = edge.getX();
                    y = edge.getY();
                    z = edge.getZ();
                    sea = true;
                }
            }
            Mob m = pool.get(r.nextInt(pool.size())).create(sl);
            if (m == null) continue;
            m.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360F, 0F);
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null);
            m.setPersistenceRequired();
            m.addTag(TAG);
            if (m.isInvertedHealAndHarm()) {
                // mortos-vivos de capacete: o sol não acaba com o ataque sozinho
                m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                m.setDropChance(EquipmentSlot.HEAD, 0F);
            }
            sl.addFreshEntity(m);
            raid.mobs.add(m.getUUID());
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, y + 1, z + 0.5, 6, 0.3, 0.5, 0.3, 0.02);
            if (s.sky) SkyAccess.raidFx(sl, x + 0.5, y, z + 0.5);
            if (sea) WaterLife.raidFx(sl, x + 0.5, y, z + 0.5);
        }
    }

    private static void win(ServerLevel sl, Settlement s, Raid raid) {
        ACTIVE.remove(raid.city);
        MinecraftServer server = sl.getServer();
        int coins = 15 * s.level;
        s.treasury = Math.min(Settlement.MAX_TREASURY, s.treasury + coins);
        s.affinity = Math.min(Settlement.MAX_AFFINITY, s.affinity + 6);
        s.raidsWon++;
        for (ServerPlayer p : defenders(server, s)) {
            Rewards.give(p, new ItemStack(SettlementService.coinItem(), 3 * s.level));
            MagicApi.giveXp(p, 25 * s.level, "city_raid");
            Rewards.banner(p, SettlementsLang.tr("raid.won", SettlementService.cityName(server, s)), 0x7DDA6B);
        }
        for (ServerPlayer g : CityEconomy.governors(server, s)) {
            g.displayClientMessage(SettlementsLang.tr("raid.won.detail", coins, 6).withColor(0x7DDA6B), false);
        }
        sl.sendParticles(ParticleTypes.FIREWORK, s.heart.getX() + 0.5, s.heart.getY() + 6, s.heart.getZ() + 0.5, 80, 3, 2, 3, 0.15);
        sl.playSound(null, s.heart, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    private static void lose(ServerLevel sl, Settlement s, Raid raid) {
        ACTIVE.remove(raid.city);
        MinecraftServer server = sl.getServer();
        float sec = Math.min(1F, CityEconomy.stats(server, s).securityRatio());
        int aff = Math.round(10 * (1F - 0.5F * sec));
        int food = Math.round(s.food * 0.25F * (1F - 0.5F * sec));
        s.affinity = Math.max(0, s.affinity - aff);
        s.food = Math.max(0, s.food - food);
        s.raidsLost++;
        List<Integer> cells = new ArrayList<>();
        for (int c : s.buildings.keySet()) if (c != CityLayout.CENTER_CELL && !s.damaged.contains(c)) cells.add(c);
        int n = Math.min(cells.size(), sec < 0.5F ? 2 : 1);
        List<Component> hit = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int c = cells.remove(sl.random.nextInt(cells.size()));
            s.damaged.add(c);
            carve(sl, s, c);
            var bp = s.blueprintAt(c);
            if (bp != null) hit.add(bp.displayName());
        }
        discardMobs(sl, raid);
        Component names = hit.isEmpty() ? Component.literal("—") : join(hit);
        for (ServerPlayer p : defenders(server, s)) {
            Rewards.banner(p, SettlementsLang.tr("raid.lost", SettlementService.cityName(server, s)), 0xE8735E);
        }
        for (ServerPlayer g : CityEconomy.governors(server, s)) {
            g.displayClientMessage(SettlementsLang.tr("raid.lost.detail", aff, food, names).withColor(0xE8735E), false);
        }
        sl.playSound(null, s.heart, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 1.6F);
    }

    private static Component join(List<Component> parts) {
        var out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(parts.get(i));
        }
        return out;
    }

    /** Arranca alguns blocos do prédio (sem tocar em baús/blocos com dados). */
    public static void carve(ServerLevel sl, Settlement s, int cell) {
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        int base = s.plotBase.getOrDefault(cell, s.heart.getY() - 1);
        RandomSource r = sl.random;
        int removed = 0;
        for (int tries = 0; tries < 120 && removed < 14; tries++) {
            BlockPos p = new BlockPos(c.getX() + r.nextInt(11) - 5, base + 1 + r.nextInt(8), c.getZ() + r.nextInt(11) - 5);
            if (!sl.hasChunk(p.getX() >> 4, p.getZ() >> 4) || p.equals(s.heart)) continue;
            var st = sl.getBlockState(p);
            if (st.isAir() || sl.getBlockEntity(p) != null || st.is(SettlementsContent.HEART.get())) continue;
            sl.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
            removed++;
        }
    }

    private static List<ServerPlayer> defenders(MinecraftServer server, Settlement s) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() == s.dim && p.blockPosition().distSqr(s.heart) <= MEMBER_RANGE * MEMBER_RANGE) out.add(p);
        }
        return out;
    }

    private static void broadcast(ServerLevel sl, Settlement s, Component text, int color) {
        Set<ServerPlayer> to = new LinkedHashSet<>(defenders(sl.getServer(), s));
        to.addAll(CityEconomy.governors(sl.getServer(), s));
        for (ServerPlayer p : to) p.displayClientMessage(text.copy().withColor(color), false);
    }

    private static void discardMobs(ServerLevel sl, Raid raid) {
        for (UUID u : raid.mobs) {
            Entity e = sl.getEntity(u);
            if (e != null) {
                sl.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
                e.discard();
            }
        }
        raid.mobs.clear();
    }

    /** Monstros de um ataque que sobraram (servidor reiniciado no meio): somem. */
    private static void sweepLeftovers(ServerLevel sl, Settlement s) {
        AABB box = new AABB(s.heart).inflate(CityLayout.half(s) + 24, 48, CityLayout.half(s) + 24);
        for (Mob m : sl.getEntitiesOfClass(Mob.class, box, m -> m.getTags().contains(TAG))) m.discard();
    }

    /** Comando de teste: começa o ataque na hora (sem os 2 minutos de aviso se {@code now}). */
    public static boolean forceStart(ServerLevel sl, Settlement s, boolean now) {
        if (ACTIVE.containsKey(s.id)) return false;
        start(sl, s);
        if (now) {
            Raid r = ACTIVE.get(s.id);
            Raid fast = new Raid(r.city, r.side, sl.getGameTime() + 20, r.waves);
            fast.lastMember = sl.getGameTime();
            fast.warnedMinute = true;
            ACTIVE.put(s.id, fast);
        }
        return true;
    }

    public static void stop(ServerLevel sl, Settlement s) {
        Raid r = ACTIVE.remove(s.id);
        if (r != null) discardMobs(sl, r);
    }

    /** Linha de estado para a tela: segundos para o ataque (>0), ataque em andamento (0) ou nada (-1). */
    public static int status(ServerLevel sl, String cityId) {
        Raid r = ACTIVE.get(cityId);
        if (r == null) return -1;
        return r.spawned ? 0 : (int) Math.max(1, (r.startAt - sl.getGameTime()) / 20);
    }

    @Nullable
    public static String side(String cityId) {
        Raid r = ACTIVE.get(cityId);
        return r == null ? null : SIDES[r.side];
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private CityRaids() {}
}
