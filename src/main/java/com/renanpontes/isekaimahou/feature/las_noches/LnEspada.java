package com.renanpontes.isekaimahou.feature.las_noches;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * As Espada nas suas salas. Las Noches NÃO conhece as classes do módulo espada: invoca por id de registro
 * ({@code isekai_mahou:barragan / ulquiorra / grimmjow}) quando alguém entra na sala (gatilho da porta). Sem o módulo, as
 * salas ficam só vazias.
 * <ul>
 *   <li>Chegada: 2 s de pressão espiritual (coluna de reiatsu, tremor crescente, batidas) → clarão, onda de choque e a
 *       Espada no meio da sala, olhando para a porta → poeira e cinzas assentando.</li>
 *   <li>Arena gravada ({@link BossKit#setArena}, raio 24, centro da sala) e tag de arena: indestrutível durante a luta
 *       (Las Noches inteira já é protegida sempre).</li>
 *   <li>Derrotada (morte de QUALQUER entidade do tipo dentro do complexo — vale se a Segunda Etapa trocar a entidade):
 *       volta um dia de jogo depois, quando alguém entrar na sala de novo.</li>
 *   <li>persistentData {@code isekai_mahou.las_noches}: {@code chamber}, {@code home_x/y/z} (centro da sala) e
 *       {@code dome_x/y/z} + {@code dome_r} (arena da Segunda Etapa no terraço da cúpula) — o módulo espada lê.</li>
 * </ul>
 */
final class LnEspada {
    static final String TAG = "isekai_mahou_las_noches_espada";
    static final String DATA_KEY = "isekai_mahou.las_noches";
    static final long RESPAWN_TICKS = 24000L;
    static final double ARENA_RADIUS = 24.0;
    private static final int BUILDUP = 40;
    private static final Set<String> PENDING = new HashSet<>();
    /** Último aviso de "sala vazia" por jogador (não repetir a cada segundo). */
    private static final Map<UUID, Long> EMPTY_NOTICE = new HashMap<>();

    static Optional<EntityType<?>> type(LnLayout.Chamber ch) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id(ch.espadaId()));
    }

    /** Alguém está na sala: chama a Espada se ela não estiver lá nem em descanso. */
    static void onChamberEntered(ServerLevel level, LnData d, LnLayout.Chamber ch, ServerPlayer who) {
        String id = ch.espadaId();
        Long at = d.defeatedAt.get(id);
        long now = level.getGameTime();
        if (at != null && now - at < RESPAWN_TICKS) {
            Long last = EMPTY_NOTICE.get(who.getUUID());
            if (last == null || now - last > 1200) {
                EMPTY_NOTICE.put(who.getUUID(), now);
                long min = Math.max(1, (RESPAWN_TICKS - (now - at)) / 1200);
                who.displayClientMessage(Component.translatable("message.isekai_mahou.las_noches.chamber_empty." + id, min).withColor(ch.color()), true);
            }
            return;
        }
        Optional<EntityType<?>> type = type(ch);
        if (type.isEmpty() || PENDING.contains(id) || d.center == null) return;
        if (present(level, d, ch, type.get())) return;
        summon(level, d, ch, type.get(), at != null);
    }

    /** A Espada já existe (pelo UUID guardado ou procurando o tipo no complexo inteiro)? */
    private static boolean present(ServerLevel level, LnData d, LnLayout.Chamber ch, EntityType<?> type) {
        UUID u = d.espada.get(ch.espadaId());
        if (u != null) {
            Entity e = level.getEntity(u);
            if (e != null && e.isAlive()) return true;
        }
        List<? extends Entity> found = find(level, type, LnGuards.complexBox(d.center));
        if (found.isEmpty()) return false;
        d.espada.put(ch.espadaId(), found.get(0).getUUID());
        d.setDirty();
        return true;
    }

    private static <T extends Entity> List<T> find(ServerLevel level, EntityType<T> type, AABB box) {
        return level.getEntities(type, box, Entity::isAlive);
    }

    /** Pressão → clarão → Espada. */
    static void summon(ServerLevel level, LnData d, LnLayout.Chamber ch, EntityType<?> type, boolean returning) {
        String id = ch.espadaId();
        PENDING.add(id);
        BlockPos c = d.center;
        Vec3 spot = Vec3.atBottomCenterOf(c.offset(ch.spawn()));
        Vec3 home = Vec3.atBottomCenterOf(c.offset(ch.center()));
        int color = ch.color();
        Component msg = Component.translatable("message.isekai_mahou.las_noches.pressure." + id).withColor(color);
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(home) < 40 * 40) p.displayClientMessage(msg, true);
        // Antecipação: reiatsu subindo em espiral e se fechando no ponto, batidas cada vez mais rápidas.
        for (int t = 0; t < BUILDUP; t += 4) {
            final int tick = t;
            BossKit.schedule(level, t, () -> {
                double k = tick / (double) BUILDUP;
                double r = 3.5 * (1 - k) + 0.4;
                for (int i = 0; i < 10; i++) {
                    double a = tick * 0.35 + i * Math.PI / 5;
                    level.sendParticles(HmFx.dust(color, 1.3F + (float) k), spot.x + Math.cos(a) * r, spot.y + 0.2 + i * 0.35 * (1 + k),
                            spot.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                level.sendParticles(ParticleTypes.ASH, spot.x, spot.y + 1, spot.z, 6, 2.5, 1.5, 2.5, 0.01);
                if (tick % 8 == 0) level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.6F, 0.6F + (float) k * 0.5F);
                if (tick % 12 == 0) BossKit.shake(level, spot, 28, 0.3F + (float) k, 10);
            });
        }
        BossKit.schedule(level, BUILDUP, () -> {
            PENDING.remove(id);
            Entity e = type.create(level);
            if (e == null) return;
            e.moveTo(spot.x, spot.y, spot.z, ch.yaw(), 0F);
            e.setYHeadRot(ch.yaw());
            e.setYBodyRot(ch.yaw());
            if (e instanceof Mob m) {
                m.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(spot)), MobSpawnType.STRUCTURE, null);
                m.setPersistenceRequired();
                m.restrictTo(BlockPos.containing(home), (int) ARENA_RADIUS);
            }
            e.addTag(TAG);
            e.addTag(BossArena.ARENA_TAG);
            BossKit.setArena(e, home, ARENA_RADIUS);
            e.getPersistentData().put(DATA_KEY, tagFor(c, ch));
            level.addFreshEntity(e);
            d.espada.put(id, e.getUUID());
            if (returning) d.defeatedAt.remove(id);
            d.setDirty();
            // Impacto.
            level.sendParticles(ParticleTypes.FLASH, spot.x, spot.y + 1.2, spot.z, 3, 0.3, 0.3, 0.3, 0);
            BossKit.shake(level, spot, 36, 2.0F, 18);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, spot.x, spot.y + 1, spot.z, 1, 0, 0, 0, 0);
            HmFx.ring(level, spot.add(0, 0.2, 0), 2.5, 40, HmFx.dust(color, 2.0F), 0.0);
            HmFx.ring(level, spot.add(0, 0.2, 0), 6.0, 56, ParticleTypes.CLOUD, 0.15);
            HmFx.sphere(level, spot.add(0, 1.2, 0), 1.6, 50, HmFx.dust(0xFFFFFF, 1.6F));
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.4F, 0.7F);
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.6F);
            // Rescaldo: poeira e cinzas assentando.
            for (int t = 5; t <= 45; t += 5) {
                BossKit.schedule(level, t, () -> {
                    level.sendParticles(ParticleTypes.ASH, spot.x, spot.y + 2, spot.z, 14, 5, 2.5, 5, 0.01);
                    level.sendParticles(HmFx.dust(color, 1.0F), spot.x, spot.y + 0.3, spot.z, 6, 4, 0.2, 4, 0);
                });
            }
        });
    }

    static CompoundTag tagFor(BlockPos c, LnLayout.Chamber ch) {
        CompoundTag t = new CompoundTag();
        t.putString("chamber", ch.espadaId());
        BlockPos home = c.offset(ch.center());
        t.putDouble("home_x", home.getX() + 0.5);
        t.putDouble("home_y", home.getY());
        t.putDouble("home_z", home.getZ() + 0.5);
        Vec3 dome = Vec3.atLowerCornerOf(c).add(LnLayout.DOME_ARENA);
        t.putDouble("dome_x", dome.x);
        t.putDouble("dome_y", dome.y);
        t.putDouble("dome_z", dome.z);
        t.putDouble("dome_r", LnLayout.DOME_ARENA_RADIUS);
        return t;
    }

    /** Uma entidade de um dos tipos das Espada morreu dentro do complexo: a sala dela fica vazia por um dia. */
    static void onDeath(ServerLevel level, LnData d, Entity e) {
        if (d.center == null) return;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        LnLayout.Chamber ch = chamber(id);
        if (ch == null || !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(IsekaiMahou.MODID)) return;
        if (!e.getTags().contains(TAG) && !LnGuards.complexBox(d.center).contains(e.position())) return;
        d.defeatedAt.put(id, level.getGameTime());
        d.espada.remove(id);
        d.setDirty();
    }

    @Nullable
    static LnLayout.Chamber chamber(String id) {
        for (LnLayout.Chamber ch : LnLayout.CHAMBERS) if (ch.espadaId().equals(id)) return ch;
        return null;
    }

    static void forget(UUID player) {
        EMPTY_NOTICE.remove(player);
    }

    static void clearPending() {
        PENDING.clear();
    }

    private LnEspada() {}
}
