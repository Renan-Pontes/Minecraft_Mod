package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.ArenaBounds;
import com.renanpontes.isekaimahou.core.boss.BossKit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Arenas de chefe protegidas (servidor). Todo chefe ({@code c:bosses}, chefe de provação, chefe com arena do
 * {@link BossKit#setArena}, tipo registrado) que entra em combate sela uma arena (centro + raio 24–48 pelo tamanho):
 * enquanto a luta durar, ninguém quebra/coloca blocos, baldes e fogo não funcionam, explosões e a {@code Destruction}
 * não mexem no terreno e o chefe não sai dela (puxado de volta ou teleportado ao centro se cair/fugir). A arena é
 * liberada 30 s depois do fim da luta. Efeitos TEMPORÁRIOS do próprio chefe (blocos que ele põe e tira sozinho) passam.
 */
public final class BossArenaService {
    public static final int RELEASE_TICKS = 600;
    /** Sem combate por este tempo (jogadores fugiram/morreram): a luta é dada como encerrada. */
    public static final int IDLE_TICKS = 600;
    public static final double MIN_RADIUS = 24, MAX_RADIUS = 48;
    private static final int ARENA_COLOR = 0xFF8A5C;
    /** Tipos que NUNCA selam arena (lutas vanilla que dependem de construir: o Dragão do Fim). */
    private static final Set<ResourceLocation> EXCLUDED = new CopyOnWriteArraySet<>(Set.of(
            ResourceLocation.withDefaultNamespace("ender_dragon")));
    private static final Set<ResourceLocation> EXTRA_TYPES = new CopyOnWriteArraySet<>();
    /** Tag de entidade ({@code addTag}) que marca qualquer mob como chefe de arena. */
    public static final String ARENA_TAG = "isekai_mahou.arena_boss";

    /** Uma arena selada. */
    public static final class Arena {
        public final UUID boss;
        public final ResourceKey<Level> dim;
        public final Vec3 center;
        public final double radius;
        public final Component bossName;
        int lastCombat;
        /** Tick em que a arena é liberada; −1 = luta em andamento. */
        int releaseAt = -1;
        boolean dead;
        int lastPull = -1000;

        Arena(LivingEntity boss, Vec3 center, double radius, int now) {
            this.boss = boss.getUUID();
            this.dim = boss.level().dimension();
            this.center = center;
            this.radius = radius;
            this.bossName = boss.getDisplayName().copy();
            this.lastCombat = now;
        }

        public boolean releasing() { return releaseAt >= 0; }

        /** Ticks até liberar (0 com a luta em andamento). */
        public int ticksLeft(int now) { return releaseAt < 0 ? 0 : Math.max(0, releaseAt - now); }

        double floor() { return center.y - Math.max(12, radius * 0.5); }

        double ceiling() { return center.y + radius + 16; }

        public boolean contains(BlockPos pos) {
            double dx = pos.getX() + 0.5 - center.x, dz = pos.getZ() + 0.5 - center.z;
            return dx * dx + dz * dz <= radius * radius && pos.getY() >= floor() && pos.getY() <= ceiling();
        }

        public boolean contains(Vec3 p) {
            double dx = p.x - center.x, dz = p.z - center.z;
            return dx * dx + dz * dz <= radius * radius && p.y >= floor() && p.y <= ceiling();
        }
    }

    private static final Map<UUID, Arena> ARENAS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> LAST_HINT = new HashMap<>();

    // =====================================================================================
    //  Quem é chefe de arena
    // =====================================================================================
    public static boolean isArenaBoss(@Nullable Entity e) {
        if (!(e instanceof LivingEntity le) || e instanceof Player || !e.isAlive()) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        if (EXCLUDED.contains(id)) return false;
        return le.getType().is(BossKit.BOSSES_TAG) || BossKit.isTrialBoss(le) || EXTRA_TYPES.contains(id)
                || le.getTags().contains(ARENA_TAG) || le.getPersistentData().contains(BossKit.ARENA_KEY);
    }

    static void registerType(ResourceLocation id) {
        if (id != null) EXTRA_TYPES.add(id);
    }

    static void excludeType(ResourceLocation id) {
        if (id != null) EXCLUDED.add(id);
    }

    static void registerType(EntityType<?> type) {
        registerType(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    // =====================================================================================
    //  Selar / consultar
    // =====================================================================================
    /** Sela (ou renova) a arena do chefe com centro/raio automáticos. */
    @Nullable
    public static Arena engage(LivingEntity boss) {
        return engage(boss, null, 0);
    }

    /** Sela com centro/raio dados (center null = automático; radius ≤ 0 = automático). */
    @Nullable
    public static Arena engage(LivingEntity boss, @Nullable Vec3 center, double radius) {
        if (!(boss.level() instanceof ServerLevel level) || !boss.isAlive() || boss instanceof Player) return null;
        int now = level.getServer().getTickCount();
        Arena a = ARENAS.get(boss.getUUID());
        if (a != null) {
            a.lastCombat = now;
            if (a.releasing() && !a.dead) {
                a.releaseAt = -1;
                notifyNear(level, a, Component.translatable("social.isekai_mahou.arena.resumed", a.bossName).withColor(ARENA_COLOR), false);
            }
            return a;
        }
        Vec3 c = center;
        double r = radius;
        BoundingBox box = c == null ? StructureGuard.boxAt(level, boss.blockPosition()) : null;
        if (c == null) {
            c = BossKit.arenaCenter(boss);
            if (c == null && box != null) {
                c = new Vec3((box.minX() + box.maxX() + 1) * 0.5, boss.getY(), (box.minZ() + box.maxZ() + 1) * 0.5);
            }
            if (c == null && boss instanceof Mob mob && mob.hasRestriction()) c = Vec3.atBottomCenterOf(mob.getRestrictCenter());
            if (c == null) c = boss.position();
            // Chefe voando ao entrar em combate: o centro desce até o chão (o "chão" da arena é o do terreno)
            if (BossKit.arenaCenter(boss) == null) c = new Vec3(c.x, groundY(level, c), c.z);
        }
        if (r <= 0) {
            double kit = BossKit.arenaRadius(boss);
            double size = Math.max(boss.getBbWidth(), boss.getBbHeight());
            r = kit > 0 ? kit : 24 + Math.max(0, size - 1) * 5;
            if (kit <= 0 && box != null) r = Math.max(r, Math.max(box.getXSpan(), box.getZSpan()) * 0.5 + 4);
        }
        r = Mth.clamp(r, MIN_RADIUS, MAX_RADIUS);
        a = new Arena(boss, c, r, now);
        ARENAS.put(boss.getUUID(), a);
        // Provação: o módulo trials mostra a borda da própria arena (ArenaBounds) — uma segunda parede, com outro raio,
        // só confundiria. A proteção/puxão continuam valendo.
        if (!BossKit.isTrialBoss(boss)) showBounds(level.getServer(), a);
        notifyNear(level, a, Component.translatable("social.isekai_mahou.arena.sealed", a.bossName).withColor(ARENA_COLOR), true);
        burst(level, a);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.6F, 0.6F);
        NeoForge.EVENT_BUS.post(new SocialEvent.ArenaSealed(boss, c, r));
        SocialSync.markAllStates();
        return a;
    }

    /** Topo do primeiro bloco sólido abaixo de {@code c} (até 32 blocos); sem chão, o próprio y. */
    private static double groundY(ServerLevel level, Vec3 c) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int x = Mth.floor(c.x), z = Mth.floor(c.z), top = Mth.floor(c.y);
        for (int y = top; y >= top - 32 && y > level.getMinBuildHeight(); y--) {
            m.set(x, y - 1, z);
            if (!level.isLoaded(m)) break;
            VoxelShape s = level.getBlockState(m).getCollisionShape(level, m);
            if (!s.isEmpty()) return m.getY() + s.max(Direction.Axis.Y);
        }
        return c.y;
    }

    @Nullable
    public static Arena arenaOf(UUID boss) {
        return ARENAS.get(boss);
    }

    /** Arena (selada ou liberando) que contém a posição neste nível, ou null. */
    @Nullable
    public static Arena at(Level level, Vec3 pos) {
        for (Arena a : ARENAS.values()) if (a.dim.equals(level.dimension()) && a.contains(pos)) return a;
        return null;
    }

    @Nullable
    public static Arena at(Level level, BlockPos pos) {
        for (Arena a : ARENAS.values()) if (a.dim.equals(level.dimension()) && a.contains(pos)) return a;
        return null;
    }

    public static List<Arena> all() {
        return new ArrayList<>(ARENAS.values());
    }

    /** Protegido agora: arena de chefe (luta ou os 30 s depois) ou área sempre protegida. */
    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        return at(level, pos) != null || StructureGuard.isProtected(level, pos);
    }

    /** Libera já (sem esperar os 30 s). Devolve false se não havia arena. */
    public static boolean release(UUID boss) {
        Arena a = ARENAS.remove(boss);
        if (a == null) return false;
        released(a);
        return true;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    static void tick(MinecraftServer server) {
        if (ARENAS.isEmpty()) return;
        int now = server.getTickCount();
        for (Arena a : new ArrayList<>(ARENAS.values())) {
            ServerLevel level = server.getLevel(a.dim);
            if (level == null) {
                ARENAS.remove(a.boss);
                hideBounds(server, a);
                continue;
            }
            Entity e = level.getEntity(a.boss);
            LivingEntity boss = e instanceof LivingEntity le && le.isAlive() ? le : null;
            if (boss == null) {
                if (!a.releasing()) startRelease(level, a, now, false);
            } else {
                if (inCombat(boss, a)) {
                    a.lastCombat = now;
                    if (a.releasing() && !a.dead) {
                        a.releaseAt = -1;
                        SocialSync.markAllStates();
                    }
                } else if (!a.releasing() && now - a.lastCombat > IDLE_TICKS) {
                    startRelease(level, a, now, false);
                }
                if (!a.releasing()) leash(level, boss, a, now);
            }
            if (a.releasing() && now >= a.releaseAt) {
                ARENAS.remove(a.boss);
                released(a);
                continue;
            }
            if (now % 40 == 0) border(level, a);
        }
    }

    private static boolean inCombat(LivingEntity boss, Arena a) {
        if (boss instanceof Mob m && m.getTarget() instanceof Player p && p.isAlive() && !p.isSpectator() && !p.isCreative()) {
            double reach = a.radius * 1.5 + 16;
            if (p.distanceToSqr(a.center) <= reach * reach) return true;
        }
        return boss.getLastHurtByMob() instanceof Player && boss.tickCount - boss.getLastHurtByMobTimestamp() < 200;
    }

    private static void startRelease(ServerLevel level, Arena a, int now, boolean victory) {
        if (a.releasing()) return;
        a.releaseAt = now + RELEASE_TICKS;
        notifyNear(level, a, Component.translatable(victory ? "social.isekai_mahou.arena.victory" : "social.isekai_mahou.arena.ending",
                a.bossName, RELEASE_TICKS / 20).withColor(victory ? 0xFFD86B : 0xC8C0D8), false);
        SocialSync.markAllStates();
    }

    private static void released(Arena a) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(a.dim);
        if (server != null) hideBounds(server, a);
        if (level != null) {
            notifyNear(level, a, Component.translatable("social.isekai_mahou.arena.released").withStyle(ChatFormatting.GRAY), false);
            level.playSound(null, a.center.x, a.center.y, a.center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.2F, 0.8F);
        }
        NeoForge.EVENT_BUS.post(new SocialEvent.ArenaReleased(a.boss, a.center));
        SocialSync.markAllStates();
    }

    /** Parede de runas + aviso "Limite da arena" do núcleo de UX ({@link ArenaBounds}). */
    private static void showBounds(MinecraftServer server, Arena a) {
        try {
            ArenaBounds.register(server, ArenaBounds.Arena.circle(boundsId(a), a.dim, a.center, a.radius)
                    .withColor(ARENA_COLOR).withName(Component.translatable("social.isekai_mahou.arena.name", a.bossName))
                    .withVertical(a.floor(), a.ceiling()));
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.error("[social] não deu para mostrar a borda da arena", ex);
        }
    }

    private static void hideBounds(MinecraftServer server, Arena a) {
        try {
            ArenaBounds.unregister(server, boundsId(a));
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.error("[social] não deu para tirar a borda da arena", ex);
        }
    }

    private static String boundsId(Arena a) {
        return "boss_arena_" + a.boss;
    }

    /** Chefe saindo do raio: empurra de volta; longe demais, caindo ou no vazio: teleporta perto do centro. */
    private static void leash(ServerLevel level, LivingEntity boss, Arena a, int now) {
        double dx = boss.getX() - a.center.x, dz = boss.getZ() - a.center.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        double voidY = level.getMinBuildHeight() + 2;
        boolean fell = boss.getY() < a.floor() || boss.getY() < voidY;
        boolean far = d > a.radius + 8 || boss.getY() > a.ceiling() + 24;
        if (fell || far) {
            if (now - a.lastPull < 10) return;
            a.lastPull = now;
            Vec3 spot = safeSpot(level, boss, a);
            Vec3 from = boss.position();
            if (boss.isPassenger()) boss.stopRiding();
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + boss.getBbHeight() * 0.5, from.z, 40,
                    boss.getBbWidth() * 0.5, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.5, 0.06);
            boss.teleportTo(spot.x, spot.y, spot.z);
            boss.setDeltaMovement(Vec3.ZERO);
            boss.fallDistance = 0;
            if (boss instanceof Mob mob) mob.getNavigation().stop();
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.x, spot.y + boss.getBbHeight() * 0.5, spot.z, 50,
                    boss.getBbWidth() * 0.5, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.5, 0.08);
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2F, 0.6F);
            notifyNear(level, a, Component.translatable("social.isekai_mahou.arena.pulled", a.bossName).withColor(ARENA_COLOR), false);
        } else if (d > a.radius - 1.5 && d > 1.0E-3) {
            double k = 0.35 + (d - (a.radius - 1.5)) * 0.08;
            boss.push(-dx / d * k, 0.08, -dz / d * k);
            boss.hurtMarked = true;
            if (now % 10 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(), 12,
                        boss.getBbWidth() * 0.5, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.5, 0.4);
            }
        }
    }

    /** Chão firme perto do centro onde o chefe cabe (sem colisão nem líquido); sem achar, o próprio centro. */
    private static Vec3 safeSpot(ServerLevel level, LivingEntity boss, Arena a) {
        for (int i = 0; i < 12; i++) {
            double ang = i * 2.399963;
            double r = i == 0 ? 0 : Math.min(a.radius * 0.5, 2.0 + i * 1.5);
            double x = a.center.x + Math.cos(ang) * r, z = a.center.z + Math.sin(ang) * r;
            int top = Mth.floor(a.center.y) + 6;
            for (int y = top; y >= top - 18; y--) {
                BlockPos below = BlockPos.containing(x, y - 1, z);
                if (!level.isLoaded(below)) break;
                VoxelShape s = level.getBlockState(below).getCollisionShape(level, below);
                if (s.isEmpty()) continue;
                double feet = below.getY() + s.max(Direction.Axis.Y);
                AABB box = boss.getDimensions(boss.getPose()).makeBoundingBox(x, feet, z);
                if (level.noCollision(boss, box) && !level.containsAnyLiquid(box)) return new Vec3(x, feet, z);
                break;
            }
        }
        return a.center;
    }

    // =====================================================================================
    //  Visual e avisos
    // =====================================================================================
    private static void border(ServerLevel level, Arena a) {
        DustParticleOptions dust = a.releasing() ? new DustParticleOptions(new Vector3f(0.7F, 0.7F, 0.75F), 1.2F)
                : new DustParticleOptions(new Vector3f(1.0F, 0.42F, 0.3F), 1.5F);
        int n = (int) Math.ceil(Math.PI * 2 * a.radius / (a.releasing() ? 5.0 : 3.0));
        double near = (a.radius + 24) * (a.radius + 24);
        boolean anyone = false;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(a.center) <= near) {
                anyone = true;
                break;
            }
        }
        if (!anyone) return;
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            double x = a.center.x + Math.cos(ang) * a.radius, z = a.center.z + Math.sin(ang) * a.radius;
            level.sendParticles(dust, x, a.center.y + 0.3, z, 1, 0, 0.05, 0, 0);
            if (!a.releasing() && i % 2 == 0) level.sendParticles(dust, x, a.center.y + 2.2, z, 1, 0, 0.3, 0, 0);
        }
    }

    private static void burst(ServerLevel level, Arena a) {
        int n = (int) Math.ceil(Math.PI * 2 * a.radius / 1.5);
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            double x = a.center.x + Math.cos(ang) * a.radius, z = a.center.z + Math.sin(ang) * a.radius;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, a.center.y + 0.5, z, 2, 0.1, 0.8, 0.1, 0.02);
        }
    }

    private static void notifyNear(ServerLevel level, Arena a, Component msg, boolean chat) {
        double r = a.radius + 24;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(a.center) > r * r) continue;
            if (chat) p.sendSystemMessage(msg);
            else p.displayClientMessage(msg, true);
        }
    }

    private static void hint(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        int now = sp.server.getTickCount();
        Integer last = LAST_HINT.get(sp.getUUID());
        if (last != null && now - last < 30) return;
        LAST_HINT.put(sp.getUUID(), now);
        sp.displayClientMessage(Component.translatable("social.isekai_mahou.arena.protected").withColor(ARENA_COLOR), true);
        sp.playNotifySound(SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, 0.5F, 1.4F);
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    static void onChangeTarget(LivingChangeTargetEvent e) {
        if (e.getNewAboutToBeSetTarget() instanceof Player p && !p.isCreative() && !p.isSpectator() && isArenaBoss(e.getEntity())) {
            engage(e.getEntity());
        }
    }

    static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide() || !(SocialRules.playerBehind(e.getSource().getEntity()) instanceof ServerPlayer p)) return;
        if (!p.isCreative() && isArenaBoss(victim)) engage(victim);
    }

    static void onDeath(LivingDeathEvent e) {
        Arena a = ARENAS.get(e.getEntity().getUUID());
        if (a == null || !(e.getEntity().level() instanceof ServerLevel level)) return;
        a.dead = true;
        a.releaseAt = -1;
        startRelease(level, a, level.getServer().getTickCount(), true);
    }

    static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || e.getPlayer().getAbilities().instabuild) return;
        if (isProtected(level, e.getPos())) {
            e.setCanceled(true);
            hint(e.getPlayer());
        }
    }

    static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        Entity who = e.getEntity();
        if (who instanceof Player p && p.getAbilities().instabuild) return;
        // O próprio chefe pode pôr blocos (efeitos temporários dele)
        if (who != null && ARENAS.containsKey(who.getUUID())) return;
        if (at(level, e.getPos()) == null) return;
        e.setCanceled(true);
        if (who instanceof Player p) hint(p);
    }

    /** Baldes, isqueiro e bola de fogo mexem em blocos sem o evento de colocar: bloqueados na arena selada. */
    static void onUseItem(PlayerInteractEvent.RightClickItem e) {
        Player p = e.getEntity();
        if (!(p.level() instanceof ServerLevel level) || p.getAbilities().instabuild || !changesTerrain(e.getItemStack().getItem())) return;
        if (at(level, p.position()) == null) return;
        e.setCanceled(true);
        hint(p);
    }

    static void onUseOnBlock(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (!(p.level() instanceof ServerLevel level) || p.getAbilities().instabuild || !changesTerrain(e.getItemStack().getItem())) return;
        if (at(level, e.getPos()) == null && at(level, e.getPos().relative(e.getFace() != null ? e.getFace() : Direction.UP)) == null) return;
        e.setCanceled(true);
        hint(p);
    }

    private static boolean changesTerrain(Item item) {
        return item instanceof BucketItem || item instanceof SolidBucketItem || item instanceof FlintAndSteelItem
                || item instanceof FireChargeItem;
    }

    static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        e.getAffectedBlocks().removeIf(pos -> isProtected(level, pos));
    }

    /**
     * Monstros (enderman, zumbi quebrando porta, Wither...) não mexem na arena nem nas áreas sempre protegidas. Chefes do
     * mod podem (os efeitos temporários deles — gelo, paredes que somem — às vezes consultam o mobGriefing); o que eles
     * destroem de verdade passa pela explosão/Destruction, que já são filtradas.
     */
    static void onGriefing(EntityMobGriefingEvent e) {
        Entity ent = e.getEntity();
        if (ent == null || !(ent.level() instanceof ServerLevel level)) return;
        if (isArenaBoss(ent) && IsekaiMahou.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(ent.getType()).getNamespace())) return;
        if (isProtected(level, ent.blockPosition())) e.setCanGrief(false);
    }

    static void forget(UUID player) {
        LAST_HINT.remove(player);
    }

    static void clear() {
        ARENAS.clear();
        LAST_HINT.clear();
        StructureGuard.clear();
    }

    private BossArenaService() {}
}
