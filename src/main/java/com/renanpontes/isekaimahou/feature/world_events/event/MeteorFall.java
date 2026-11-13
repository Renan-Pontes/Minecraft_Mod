package com.renanpontes.isekaimahou.feature.world_events.event;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.world_events.WeManager;
import com.renanpontes.isekaimahou.feature.world_events.WeRegistries;
import com.renanpontes.isekaimahou.feature.world_events.WeSites;
import com.renanpontes.isekaimahou.feature.world_events.WeState;
import com.renanpontes.isekaimahou.feature.world_events.entity.AlienMob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Queda de Meteoro</b>: um meteoro risca o céu por alguns segundos e cai numa área LIVRE perto de um jogador
 * (regras em {@link WeSites}: longe de cidades, estruturas e de qualquer bloco de jogador). Abre uma cratera pequena
 * com rocha de meteoro e Minério Estelar no fundo; logo depois os invasores saem dela (Batedores, Atiradores, Larvas
 * e o Arauto Estelar). A fumaça da cratera mostra o caminho enquanto o evento dura.
 * <p>
 * Se, na hora do impacto, o lugar deixou de ser livre (alguém construiu, o chunk descarregou), o meteoro se desfaz
 * no ar e nada é tocado.
 */
public final class MeteorFall {
    /** Ticks do risco no céu antes do impacto. */
    private static final int FALL_TICKS = 120;
    private static final int RADIUS = 5;
    private static final int SITE_RADIUS = RADIUS + 3;
    private static final int VIEW_RANGE = 320;

    private MeteorFall() {}

    @Nullable
    public static BlockPos findSite(ServerLevel level, ServerPlayer around) {
        return WeSites.findSite(level, around.blockPosition(), 40, 90, SITE_RADIUS, 14);
    }

    public static void start(ServerLevel level, WeState s) {
        // nada além do risco no céu (feito no tick)
    }

    /** Devolve false quando o evento deve acabar agora. */
    public static boolean tick(ServerLevel level, WeState s, int age) {
        BlockPos site = s.site;
        if (site == null) return false;
        if (!s.extra.getBoolean("impact")) {
            if (age < FALL_TICKS) {
                trail(level, site, age);
                return true;
            }
            s.extra.putBoolean("impact", true);
            s.setDirty();
            if (!impact(level, site)) return false;
            s.extra.putBoolean("landed", true);
            return true;
        }
        if (!s.extra.getBoolean("landed")) return false;
        if (!s.extra.getBoolean("spawned") && age >= FALL_TICKS + 50) {
            s.extra.putBoolean("spawned", true);
            s.setDirty();
            spawnInvaders(level, site);
        }
        if (age % 40 == 0 && level.isLoaded(site)) {
            // Coluna de fumaça: quem viu o meteoro cair acha a cratera
            for (ServerPlayer p : near(level, site, 160)) {
                level.sendParticles(p, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, site.getX() + 0.5, site.getY() - 1.5,
                        site.getZ() + 0.5, 2, 0.8, 0.2, 0.8, 0.01);
            }
        }
        return true;
    }

    private static List<ServerPlayer> near(ServerLevel level, BlockPos site, int range) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(site) <= (double) range * range) out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ o risco no céu
    private static void trail(ServerLevel level, BlockPos site, int age) {
        float t = 1F - age / (float) FALL_TICKS;
        // vem de cima e de lado, sempre da mesma direção para este lugar
        double ang = (site.getX() * 31L + site.getZ() * 17L) % 360 * Mth.DEG_TO_RAD;
        double x = site.getX() + 0.5 + Math.cos(ang) * 70 * t;
        double y = site.getY() + 1 + 130 * t;
        double z = site.getZ() + 0.5 + Math.sin(ang) * 70 * t;
        for (ServerPlayer p : near(level, site, VIEW_RANGE)) {
            // De longe só partícula GRANDE aparece: bola de explosão + clarão; a chama e a fumaça fazem a cauda de perto
            level.sendParticles(p, ParticleTypes.EXPLOSION, true, x, y, z, 2, 0.5, 0.5, 0.5, 0);
            level.sendParticles(p, ParticleTypes.FLAME, true, x, y, z, 12, 0.9, 0.9, 0.9, 0.02);
            level.sendParticles(p, ParticleTypes.LARGE_SMOKE, true, x, y + 1, z, 5, 1.2, 1.2, 1.2, 0.01);
            level.sendParticles(p, ParticleTypes.LAVA, true, x, y, z, 2, 0.5, 0.5, 0.5, 0);
            if (age % 4 == 0) level.sendParticles(p, ParticleTypes.FLASH, true, x, y, z, 1, 0, 0, 0, 0);
            if (age % 30 == 0) p.playNotifySound(SoundEvents.FIRECHARGE_USE, SoundSource.WEATHER, 0.5F, 0.5F);
        }
    }

    // ------------------------------------------------------------------ impacto e cratera
    /** Devolve false se o meteoro se desfez no ar (lugar deixou de ser livre). */
    private static boolean impact(ServerLevel level, BlockPos site) {
        Vec3 c = Vec3.atCenterOf(site);
        if (!level.isLoaded(site) || !WeSites.areaClean(level, site, SITE_RADIUS)) {
            for (ServerPlayer p : near(level, site, VIEW_RANGE)) {
                level.sendParticles(p, ParticleTypes.EXPLOSION_EMITTER, true, c.x, c.y + 40, c.z, 1, 0, 0, 0, 0);
                level.sendParticles(p, ParticleTypes.FIREWORK, true, c.x, c.y + 40, c.z, 60, 2, 2, 2, 0.3);
                p.playNotifySound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.WEATHER, 1.0F, 0.6F);
                p.displayClientMessage(Component.translatable("world_event.isekai_mahou.meteor.fizzle"), true);
            }
            return false;
        }
        carve(level, site);
        // Explosão só de efeito e dano em criaturas: NÃO quebra blocos
        level.explode(null, c.x, c.y, c.z, 5F, false, Level.ExplosionInteraction.NONE);
        level.playSound(null, site, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 8F, 0.5F);
        level.playSound(null, site, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.WEATHER, 6F, 0.6F);
        BossKit.shake(level, c, 110, 2.4F, 50);
        BossKit.flashNear(level, c, 56, 0xFFD9A0, 0.45F, 14);
        for (ServerPlayer p : near(level, site, 160)) {
            level.sendParticles(p, ParticleTypes.EXPLOSION_EMITTER, true, c.x, c.y, c.z, 2, 1, 0.5, 1, 0);
            level.sendParticles(p, ParticleTypes.LAVA, true, c.x, c.y, c.z, 40, 2.5, 0.5, 2.5, 0);
            level.sendParticles(p, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, c.x, c.y, c.z, 30, 2.5, 1, 2.5, 0.02);
        }
        return true;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState st) {
        if (WeSites.canEdit(level, pos)) level.setBlock(pos, st, 3);
    }

    private static void carve(ServerLevel level, BlockPos site) {
        RandomSource rnd = level.random;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState rock = WeRegistries.METEORITE.get().defaultBlockState();
        int reach = RADIUS + 2;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) + rnd.nextFloat() * 0.6 - 0.3;
                int x = site.getX() + dx, z = site.getZ() + dz;
                int gy = WeSites.groundY(level, x, z);
                if (gy == Integer.MIN_VALUE || Math.abs(gy - site.getY()) > 8) continue;
                if (d < RADIUS) {
                    double k = d / RADIUS;
                    int depth = (int) Math.round((1 - k * k) * 4);
                    int floor = Math.min(gy, site.getY() - depth);
                    // tira tudo do fundo da tigela até acima das copas das árvores
                    int top = Math.max(gy, site.getY()) + 10;
                    for (int y = floor + 1; y <= top; y++) set(level, m.set(x, y, z), air);
                    float r = rnd.nextFloat();
                    BlockState floorState = r < 0.30F ? rock
                            : r < 0.42F ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : r < 0.67F ? Blocks.BLACKSTONE.defaultBlockState()
                            : r < 0.82F ? Blocks.BASALT.defaultBlockState() : null;
                    if (floorState != null) set(level, m.set(x, floor, z), floorState);
                } else if (d < RADIUS + 1.6) {
                    // borda: chão queimado, sem o mato
                    for (int y = gy + 1; y <= gy + 2; y++) {
                        if (level.getBlockState(m.set(x, y, z)).canBeReplaced()) set(level, m, air);
                    }
                    if (rnd.nextFloat() < 0.5F) {
                        set(level, m.set(x, gy, z), (rnd.nextBoolean() ? Blocks.COARSE_DIRT : Blocks.BLACKSTONE).defaultBlockState());
                    }
                }
            }
        }
        // Núcleo: rocha de meteoro com Minério Estelar (3–5 + o do centro)
        int coreY = site.getY() - 4;
        List<BlockPos> core = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy >= -1; dy--) {
                    if (Math.abs(dx) + Math.abs(dz) > 1 && dy == 0 && rnd.nextBoolean()) continue;
                    BlockPos p = new BlockPos(site.getX() + dx, coreY + dy, site.getZ() + dz);
                    set(level, p, rock);
                    if (dx != 0 || dz != 0 || dy != 0) core.add(p);
                }
            }
        }
        BlockState ore = WeRegistries.STAR_ORE.get().defaultBlockState();
        set(level, new BlockPos(site.getX(), coreY, site.getZ()), ore);
        int extra = 3 + rnd.nextInt(3);
        for (int i = 0; i < extra && !core.isEmpty(); i++) set(level, core.remove(rnd.nextInt(core.size())), ore);
    }

    // ------------------------------------------------------------------ invasores
    private static void spawnInvaders(ServerLevel level, BlockPos site) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.isLoaded(site)) return;
        // Um pouco mais de invasores para grupos (até +2 de cada)
        int extra = Mth.clamp(near(level, site, 96).size() - 1, 0, 2);
        spawnRing(level, site, WeRegistries.ALIEN_OVERSEER.get(), 1, 0, 1);
        spawnRing(level, site, WeRegistries.ALIEN_GRUNT.get(), 2 + extra, RADIUS, RADIUS + 4);
        spawnRing(level, site, WeRegistries.ALIEN_SPITTER.get(), 1 + (extra + 1) / 2, RADIUS + 1, RADIUS + 5);
        spawnRing(level, site, WeRegistries.ALIEN_LARVA.get(), 3 + extra, 1, RADIUS);
        level.playSound(null, site, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 2.5F, 0.7F);
        for (ServerPlayer p : near(level, site, WeManager.LOCAL_NOTICE_RANGE)) {
            p.displayClientMessage(Component.translatable("world_event.isekai_mahou.meteor.invaders").withColor(0x7CFF6A), true);
        }
    }

    private static <T extends AlienMob> void spawnRing(ServerLevel level, BlockPos site, EntityType<T> type, int count,
                                                      int minR, int maxR) {
        RandomSource rnd = level.random;
        for (int i = 0; i < count; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double r = minR + rnd.nextDouble() * Math.max(0, maxR - minR);
            int x = site.getX() + Mth.floor(Math.cos(ang) * r);
            int z = site.getZ() + Mth.floor(Math.sin(ang) * r);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!level.isLoaded(pos) || Math.abs(pos.getY() - site.getY()) > 10) continue;
            AlienMob.spawnInvader(level, type, pos);
        }
    }
}
