package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.cities.block.CityHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;
import com.renanpontes.isekaimahou.feature.cities.entity.Citizen;
import com.renanpontes.isekaimahou.feature.cities.trade.CityTrades;
import com.renanpontes.isekaimahou.network.CorePayloads;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cerco à Cidade (§5.2): à noite, com um jogador na cidade, 4% de chance por noite de a Horda atacar. Telegrafia
 * (título, sinos, trompa, fumaça e marca no chão no portão por 10 s), 3 ondas de goblins/orcs vindo do portão, barra
 * de evento, e no fim: +40 Reino, moedas, XP e fogos. Sem jogador defendendo por 1 min (ou 12 min no total), a Horda
 * saqueia e vai embora. Estado só em memória: os mobs do cerco têm uma marca e somem se o cerco não existir mais.
 */
public final class CitySiege {
    public static final String SIEGE_TAG = "isekai_mahou.city_siege";
    public static final float NIGHTLY_CHANCE = 0.04F;
    private static final int WAVES = 3;
    private static final int WARNING_TICKS = 200;
    private static final int INTERMISSION_TICKS = 160;
    private static final int MAX_TICKS = 14400;
    private static final int ABANDON_TICKS = 1200;
    private static final int MAX_ALIVE = 24;
    private static final int COLOR = 0xD0402A;

    private enum Phase { WARNING, WAVE, INTERMISSION }

    private static final Map<String, Siege> ACTIVE = new HashMap<>();

    /** Mobs de cada onda (ids do mod; os que não existirem viram zumbi/vindicador/saqueador). */
    private static final String[][] POOLS = {
            {"goblin", "goblin", "goblin_archer", "kobold"},
            {"goblin", "goblin_archer", "goblin_shaman", "hobgoblin", "goblin_wolf_rider", "kobold"},
            {"hobgoblin", "orc", "orc_berserker", "goblin_wolf_rider", "goblin_shaman", "goblin_archer"}};
    private static final EntityType<?>[] FALLBACK = {EntityType.ZOMBIE, EntityType.PILLAGER, EntityType.VINDICATOR};

    private static final class Siege {
        final String placeId;
        final ServerLevel level;
        final BlockPos heart;
        final AABB area;
        final Vec3 spawn;
        final Direction gate;
        final Component cityName;
        final ServerBossEvent bar;
        final Set<UUID> mobs = new HashSet<>();
        final Set<UUID> participants = new HashSet<>();
        Phase phase = Phase.WARNING;
        int wave;
        int phaseTicks;
        int age;
        int quota;
        int spawned;
        int lonely;
        long lastTick;

        Siege(String placeId, ServerLevel level, BlockPos heart, AABB area, Vec3 spawn, Direction gate, Component cityName) {
            this.placeId = placeId;
            this.level = level;
            this.heart = heart;
            this.area = area;
            this.spawn = spawn;
            this.gate = gate;
            this.cityName = cityName;
            this.bar = new ServerBossEvent(Component.translatable("bossbar.isekai_mahou.cities.siege_warning", cityName),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    public static boolean active(String placeId) {
        return ACTIVE.containsKey(placeId);
    }

    /** Há um cerco acontecendo e esta posição está dentro da cidade sitiada? */
    public static boolean underSiege(ServerLevel sl, BlockPos pos) {
        if (ACTIVE.isEmpty()) return false;
        for (Siege s : ACTIVE.values()) {
            if (s.level == sl && s.area.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) return true;
        }
        return false;
    }

    public static boolean isSiegeMob(Entity e) {
        return e.getTags().contains(SIEGE_TAG);
    }

    /** O mob do cerco pertence a um cerco que ainda existe? */
    public static boolean isOrphan(Entity e) {
        String id = e.getPersistentData().getString(SIEGE_TAG);
        return !ACTIVE.containsKey(id);
    }

    // =====================================================================================
    //  Começo
    // =====================================================================================
    /** Chamado a cada tick pelo coração da cidade. */
    public static void tickHeart(ServerLevel sl, CityHeartBlockEntity be) {
        Siege s = ACTIVE.get(be.placeId());
        if (s != null) {
            tick(s);
            return;
        }
        if (sl.getGameTime() % 100 != 13) return;
        long time = sl.getDayTime() % 24000L;
        if (time < 13500L || time > 21000L) return;
        if (playersInside(sl, be.area()).isEmpty()) return;
        CityData data = CityData.get(sl);
        CityData.City city = data.city(be.placeId());
        long day = sl.getDayTime() / 24000L;
        if (city.lastSiegeRollDay >= day) return;
        city.lastSiegeRollDay = day;
        data.setDirty();
        if (sl.random.nextFloat() < NIGHTLY_CHANCE) start(sl, be);
    }

    /** Começa um cerco nesta cidade (também usado pelo comando). */
    public static boolean start(ServerLevel sl, CityHeartBlockEntity be) {
        if (ACTIVE.containsKey(be.placeId())) return false;
        Direction gate = findGate(sl, be);
        BlockPos h = be.getBlockPos();
        // Fora do portão — mas num chunk que esteja rodando entidades (distância de simulação baixa): senão a Horda
        // nasceria parada e o cerco nunca andaria. Se precisar, ela surge no próprio vão do portão.
        int dist = be.radius() + 7;
        int minDist = Math.max(8, be.radius() - 12);
        while (dist > minDist && !sl.isPositionEntityTicking(new BlockPos(h.getX() + gate.getStepX() * dist, h.getY(),
                h.getZ() + gate.getStepZ() * dist))) {
            dist -= 3;
        }
        int sx = h.getX() + gate.getStepX() * dist;
        int sz = h.getZ() + gate.getStepZ() * dist;
        int sy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
        Vec3 spawn = new Vec3(sx + 0.5, sy, sz + 0.5);
        Component name = be.cityName(sl);
        Siege s = new Siege(be.placeId(), sl, h, be.area(), spawn, gate, name);
        s.lastTick = sl.getGameTime();
        ACTIVE.put(s.placeId, s);
        CityData data = CityData.get(sl);
        data.city(be.placeId()).sieges++;
        data.setDirty();
        // Telegrafia: título, trompa de guerra, marca no chão onde a Horda vai aparecer.
        Component dir = CityLines.tr("rumor.isekai_mahou.cities.dir." + dirKey(gate));
        for (ServerPlayer p : playersNear(s, 64)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.isekai_mahou.cities.siege").withColor(COLOR)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.isekai_mahou.cities.siege.sub", dir)));
            p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        Telegraph.show(sl, Telegraph.Shape.circle(spawn, 8.0F).warmup(WARNING_TICKS).color(Telegraph.DANGER));
        for (CityNpc npc : sl.getEntitiesOfClass(CityNpc.class, s.area)) {
            if (npc instanceof Citizen && npc.getRandom().nextInt(3) == 0) npc.say(CityLines.pick("siege.fear", npc.getRandom()));
        }
        IsekaiMahou.LOGGER.info("Cerco à cidade {} começou (portão {})", s.placeId, gate);
        return true;
    }

    /** Portão aberto (corredor livre atravessando a muralha); sem nenhum, o lado sul (a Horda escala o muro). */
    private static Direction findGate(ServerLevel sl, CityHeartBlockEntity be) {
        List<Direction> open = new ArrayList<>();
        BlockPos h = be.getBlockPos();
        int floor = be.floorY();
        int wallIn = be.radius() - 8;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            boolean clear = true;
            // v1.4: cidade em terraços — o corredor do portão pode subir/descer meio bloco por coluna; segue o vão
            // andável (2 de altura, chão firme) a partir do nível mais perto do piso, sem pular mais de 1 bloco
            int prevY = Integer.MIN_VALUE;
            for (int k = wallIn - 1; k <= be.radius() && clear; k++) {
                int px = h.getX() + d.getStepX() * k, pz = h.getZ() + d.getStepZ() * k;
                if (!sl.isLoaded(new BlockPos(px, floor + 1, pz))) {
                    clear = false;
                    break;
                }
                int found = Integer.MIN_VALUE;
                int from = prevY == Integer.MIN_VALUE ? floor + 1 : prevY;
                int span = prevY == Integer.MIN_VALUE ? 6 : 1;
                for (int i = 0; i <= 2 * span && found == Integer.MIN_VALUE; i++) {
                    int dy = (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2);
                    BlockPos q = new BlockPos(px, from + dy, pz);
                    if (sl.getBlockState(q).getCollisionShape(sl, q).isEmpty()
                            && sl.getBlockState(q.above()).getCollisionShape(sl, q.above()).isEmpty()
                            && !sl.getBlockState(q.below()).getCollisionShape(sl, q.below()).isEmpty()) {
                        found = q.getY();
                    }
                }
                if (found == Integer.MIN_VALUE) clear = false;
                prevY = found;
            }
            if (clear) open.add(d);
        }
        if (open.isEmpty()) return Direction.SOUTH;
        return open.get(sl.random.nextInt(open.size()));
    }

    private static String dirKey(Direction d) {
        return switch (d) {
            case NORTH -> "n";
            case EAST -> "e";
            case WEST -> "w";
            default -> "s";
        };
    }

    // =====================================================================================
    //  Andamento
    // =====================================================================================
    private static void tick(Siege s) {
        ServerLevel sl = s.level;
        s.lastTick = sl.getGameTime();
        s.age++;
        s.phaseTicks++;
        if (s.age % 20 == 0) {
            List<ServerPlayer> inside = playersInside(sl, s.area);
            for (ServerPlayer p : inside) s.participants.add(p.getUUID());
            s.lonely = inside.isEmpty() ? s.lonely + 20 : 0;
            updateBarPlayers(s);
            if (s.lonely >= ABANDON_TICKS || s.age >= MAX_TICKS) {
                fail(s);
                return;
            }
        }
        switch (s.phase) {
            case WARNING -> {
                s.bar.setProgress(1.0F - s.phaseTicks / (float) WARNING_TICKS);
                if (s.phaseTicks % 30 == 1) ringBells(s);
                if (s.phaseTicks % 5 == 0) {
                    sl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, s.spawn.x, s.spawn.y + 1, s.spawn.z, 3, 1.5, 0.5, 1.5, 0.02);
                }
                if (s.phaseTicks >= WARNING_TICKS) beginWave(s, 1);
            }
            case WAVE -> {
                if (s.spawned < s.quota && s.phaseTicks % 30 == 5) {
                    int burst = Math.min(3, s.quota - s.spawned);
                    for (int i = 0; i < burst && s.mobs.size() < MAX_ALIVE; i++) spawnMob(s, s.spawned + 1 == s.quota && s.wave == WAVES);
                }
                if (s.phaseTicks % 20 == 0) {
                    pruneAndDrive(s);
                    int left = s.mobs.size() + (s.quota - s.spawned);
                    s.bar.setProgress(Math.max(0F, left / (float) Math.max(1, s.quota)));
                    if (s.spawned >= s.quota && s.mobs.isEmpty()) {
                        if (s.wave >= WAVES) victory(s);
                        else {
                            s.phase = Phase.INTERMISSION;
                            s.phaseTicks = 0;
                            s.bar.setName(Component.translatable("bossbar.isekai_mahou.cities.siege_break", s.cityName, s.wave + 1, WAVES));
                            s.bar.setProgress(0F);
                            announce(s, Component.translatable("announce.isekai_mahou.cities.wave_clear", s.wave), 0x7BD86A);
                        }
                    }
                }
            }
            case INTERMISSION -> {
                s.bar.setProgress(s.phaseTicks / (float) INTERMISSION_TICKS);
                if (s.phaseTicks >= INTERMISSION_TICKS) beginWave(s, s.wave + 1);
            }
        }
    }

    private static void beginWave(Siege s, int wave) {
        s.wave = wave;
        s.phase = Phase.WAVE;
        s.phaseTicks = 0;
        s.spawned = 0;
        int players = Math.max(1, playersInside(s.level, s.area).size());
        s.quota = Math.min(22, new int[] {0, 6, 9, 12}[wave] + 3 * (players - 1));
        s.bar.setName(Component.translatable("bossbar.isekai_mahou.cities.siege_wave", s.cityName, wave, WAVES));
        s.bar.setProgress(1.0F);
        for (ServerPlayer p : playersNear(s, 96)) p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
        announce(s, Component.translatable("announce.isekai_mahou.cities.wave", wave, WAVES), COLOR);
        BossKit.shake(s.level, s.spawn, 48, 0.6F, 20);
    }

    private static void spawnMob(Siege s, boolean chief) {
        ServerLevel sl = s.level;
        String[] pool = POOLS[Math.min(s.wave, WAVES) - 1];
        String id = chief ? "orc_berserker" : pool[sl.random.nextInt(pool.length)];
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id(id))
                .orElse(FALLBACK[Math.min(s.wave, WAVES) - 1]);
        Entity e = type.create(sl);
        if (!(e instanceof Mob mob)) {
            if (e != null) e.discard();
            return;
        }
        double along = (sl.random.nextDouble() - 0.5) * 10.0;
        double out = sl.random.nextDouble() * 4.0;
        double x = s.spawn.x + (s.gate.getAxis() == Direction.Axis.X ? out * s.gate.getStepX() : along);
        double z = s.spawn.z + (s.gate.getAxis() == Direction.Axis.Z ? out * s.gate.getStepZ() : along);
        if (!sl.isPositionEntityTicking(BlockPos.containing(x, s.spawn.y, z))) {
            x = s.spawn.x;
            z = s.spawn.z;
        }
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        float yaw = s.gate.getOpposite().toYRot();
        mob.moveTo(x, y, z, yaw, 0.0F);
        mob.finalizeSpawn(sl, sl.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
        mob.addTag(SIEGE_TAG);
        mob.getPersistentData().putString(SIEGE_TAG, s.placeId);
        mob.setPersistenceRequired();
        if (chief) {
            // Chefe de guerra da última onda: mais vida e força, brilho e nome.
            var hp = mob.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.addPermanentModifier(new AttributeModifier(IsekaiMahou.id("siege_chief_health"), 1.5,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            var dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmg != null) dmg.addPermanentModifier(new AttributeModifier(IsekaiMahou.id("siege_chief_damage"), 0.5,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            mob.setHealth(mob.getMaxHealth());
            mob.setCustomName(Component.translatable("entity.isekai_mahou.cities.siege_chief").withColor(COLOR));
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));
        }
        if (!sl.addFreshEntity(mob)) return;
        s.mobs.add(mob.getUUID());
        s.spawned++;
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1, z, 8, 0.4, 0.6, 0.4, 0.02);
        if (chief) {
            BossKit.roar(mob);
            announce(s, Component.translatable("announce.isekai_mahou.cities.chief"), COLOR);
        }
    }

    /** Tira os mortos da lista e empurra os vivos para dentro da cidade (alvo: jogadores e moradores por perto). */
    private static void pruneAndDrive(Siege s) {
        ServerLevel sl = s.level;
        Iterator<UUID> it = s.mobs.iterator();
        while (it.hasNext()) {
            Entity e = sl.getEntity(it.next());
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                it.remove();
                continue;
            }
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isAlive()) {
                LivingEntity near = nearestPrey(sl, mob);
                if (near != null) mob.setTarget(near);
                else mob.getNavigation().moveTo(s.heart.getX() + 0.5, s.heart.getY() - 3, s.heart.getZ() + 0.5, 1.1);
            }
        }
    }

    @Nullable
    private static LivingEntity nearestPrey(ServerLevel sl, Mob mob) {
        LivingEntity best = null;
        double bestD = 24 * 24;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(24),
                e -> (e instanceof Player p && !p.isCreative() && !p.isSpectator()) || e instanceof CityNpc)) {
            double d = e.distanceToSqr(mob);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    // =====================================================================================
    //  Fim
    // =====================================================================================
    private static void victory(Siege s) {
        ServerLevel sl = s.level;
        CityData data = CityData.get(sl);
        data.city(s.placeId).siegesWon++;
        data.setDirty();
        Component reason = Component.translatable("reason.isekai_mahou.cities.siege", s.cityName);
        // A reputação vai para o reino da cidade (Aldória, Sahra ou Nordheim).
        Places.Place place = Places.get(sl, s.placeId);
        String faction = place != null && CityRealm.isKingdomFaction(place.faction()) ? place.faction() : "kingdom";
        for (UUID id : s.participants) {
            if (!(sl.getPlayerByUUID(id) instanceof ServerPlayer p) || !p.isAlive()) continue;
            if (p.position().distanceToSqr(Vec3.atCenterOf(s.heart)) > Math.pow(s.area.getXsize() / 2 + 64, 2)) continue;
            Reputation.add(p, faction, 40, reason);
            ItemStack coins = new ItemStack(CityTrades.coin(), 25);
            Rewards.give(p, coins);
            MagicApi.giveXp(p, 250, "cities.siege");
            MagicApi.addCounter(p, "cities.siege_won", 1);
            Rewards.toast(p, Component.translatable("toast.isekai_mahou.cities.siege_won"), s.cityName, coins, 0x6FA8FF);
            Rewards.banner(p, Component.translatable("banner.isekai_mahou.cities.siege_won", s.cityName), 0x6FA8FF);
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
        }
        for (CityNpc npc : sl.getEntitiesOfClass(CityNpc.class, s.area)) {
            npc.playAction("cheer");
            if (npc.getRandom().nextInt(3) == 0) npc.say(CityLines.pick("siege.cheer", npc.getRandom()));
        }
        fireworks(sl, s.heart);
        IsekaiMahou.LOGGER.info("Cerco à cidade {} repelido", s.placeId);
        end(s);
    }

    private static void fail(Siege s) {
        for (ServerPlayer p : playersNear(s, 128)) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.siege_failed", s.cityName).withColor(COLOR), false);
        }
        end(s);
    }

    /** Termina sem recompensa (coração quebrado, comando, servidor parando). */
    public static void stop(ServerLevel sl, String placeId, boolean announce) {
        Siege s = ACTIVE.get(placeId);
        if (s == null) return;
        if (announce) fail(s);
        else end(s);
    }

    private static void end(Siege s) {
        ACTIVE.remove(s.placeId);
        s.bar.removeAllPlayers();
        for (UUID id : s.mobs) {
            Entity e = s.level.getEntity(id);
            if (e != null) {
                s.level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
                e.discard();
            }
        }
        s.mobs.clear();
    }

    /** Cercos cujo coração parou de rodar (chunk descarregado) acabam sozinhos. */
    public static void sweep(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (Siege s : new ArrayList<>(ACTIVE.values())) {
            if (s.level.getGameTime() - s.lastTick > 100) end(s);
        }
    }

    public static void clearAll() {
        for (Siege s : ACTIVE.values()) s.bar.removeAllPlayers();
        ACTIVE.clear();
    }

    // =====================================================================================
    //  Efeitos
    // =====================================================================================
    private static void ringBells(Siege s) {
        s.level.playSound(null, s.heart, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 0.9F);
        BlockPos g = BlockPos.containing(s.spawn);
        s.level.playSound(null, g, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.7F);
    }

    private static void announce(Siege s, Component text, int rgb) {
        for (ServerPlayer p : playersNear(s, 96)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                    new CorePayloads.AnnounceText(-1, text, rgb, Announce.STYLE_BOSS));
        }
    }

    private static void fireworks(ServerLevel sl, BlockPos heart) {
        int[] colors = {0x3F6FD8, 0xFFD86B, 0xFFFFFF};
        for (int i = 0; i < 6; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            FireworkExplosion ex = new FireworkExplosion(i % 2 == 0 ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.STAR,
                    IntList.of(colors[i % 3], colors[(i + 1) % 3]), IntList.of(0xFFFFFF), true, true);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + i % 2, List.of(ex)));
            double a = i * Math.PI / 3;
            FireworkRocketEntity f = new FireworkRocketEntity(sl, heart.getX() + 0.5 + Math.cos(a) * 6, heart.getY() + 1,
                    heart.getZ() + 0.5 + Math.sin(a) * 6, rocket);
            sl.addFreshEntity(f);
        }
    }

    private static void updateBarPlayers(Siege s) {
        Set<ServerPlayer> want = new HashSet<>(playersNear(s, 48));
        for (ServerPlayer p : new ArrayList<>(s.bar.getPlayers())) if (!want.contains(p)) s.bar.removePlayer(p);
        for (ServerPlayer p : want) s.bar.addPlayer(p);
    }

    private static List<ServerPlayer> playersInside(ServerLevel sl, AABB area) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : sl.players()) if (!p.isSpectator() && area.contains(p.position())) out.add(p);
        return out;
    }

    private static List<ServerPlayer> playersNear(Siege s, double margin) {
        AABB big = s.area.inflate(margin);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : s.level.players()) if (big.contains(p.position())) out.add(p);
        return out;
    }

    private CitySiege() {}
}
