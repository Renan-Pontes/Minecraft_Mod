package com.renanpontes.isekaimahou.core.api;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.social.BossArenaService;
import com.renanpontes.isekaimahou.core.social.BossArenaService.Arena;
import com.renanpontes.isekaimahou.core.social.SocialCore;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Arenas de chefe protegidas (v1.0 — A2). AUTOMÁTICO para todo chefe: tag {@code c:bosses}, chefe de provação
 * ({@code BossKit.markTrialBoss}), chefe com {@code BossKit.setArena}, tipos de {@link #registerType} ou entidades com a
 * tag {@link #ARENA_TAG}. Ao entrar em combate (mirar um jogador ou ser ferido por um) o chefe sela uma arena:
 * <ul>
 *   <li>centro = {@code BossKit.arenaCenter} → centro da estrutura de chefe protegida → centro de restrição do mob →
 *       posição atual; raio = {@code BossKit.arenaRadius} ou 24 + 5 × (tamanho − 1), limitado a 24–48;</li>
 *   <li>enquanto a luta durar: jogadores (fora do criativo) e monstros não quebram/colocam blocos, baldes/isqueiro não
 *       funcionam, explosões e {@link Destruction} não mexem; o chefe é empurrado de volta na borda e teleportado ao
 *       centro se sair longe, cair ou for para o vazio;</li>
 *   <li>efeitos TEMPORÁRIOS do próprio chefe (blocos que ele mesmo põe e tira) continuam funcionando;</li>
 *   <li>a luta acaba quando o chefe morre ou fica 30 s sem combate; a arena é liberada 30 s depois.</li>
 * </ul>
 * Áreas SEMPRE protegidas (com ou sem luta: sem quebrar, sem explosão, sem Destruction): as estruturas de chefe do mod
 * ({@code guardian_temple_<fogo|água|terra|vento>}, {@code abyss_throne}, {@code celestial_cathedral},
 * {@code twilight_arena}) e o hub {@code hub_twilight_rift}; módulos somam as suas com {@link #protectStructure} /
 * {@link #protectPlace}. Eventos: {@code SocialEvent.ArenaSealed/ArenaReleased}. Servidor. Ver docs/v10/API_social.md.
 */
public final class BossArena {
    public static final int RELEASE_TICKS = BossArenaService.RELEASE_TICKS;
    public static final double MIN_RADIUS = BossArenaService.MIN_RADIUS, MAX_RADIUS = BossArenaService.MAX_RADIUS;
    /** {@code entity.addTag(BossArena.ARENA_TAG)}: este mob conta como chefe de arena. */
    public static final String ARENA_TAG = BossArenaService.ARENA_TAG;

    /** Arena selada: chefe, dimensão, centro, raio e se está liberando (a luta acabou). */
    public record Info(UUID boss, ResourceLocation dimension, Vec3 center, double radius, boolean releasing) {}

    /** Sela agora (ou renova) a arena do chefe, com centro/raio automáticos. Devolve null se não deu (cliente, morto...). */
    @Nullable
    public static Info seal(LivingEntity boss) {
        return info(BossArenaService.engage(boss));
    }

    /** Sela com centro e raio dados (raio limitado a 24–48; ≤ 0 = automático). */
    @Nullable
    public static Info seal(LivingEntity boss, Vec3 center, double radius) {
        return info(BossArenaService.engage(boss, center, radius));
    }

    /** Move a arena de um chefe em luta (novo centro/raio) sem liberar nem avisar de novo. */
    @Nullable
    public static Info move(LivingEntity boss, Vec3 center, double radius) {
        return info(BossArenaService.move(boss, center, radius));
    }

    /** Libera já, sem esperar os 30 s (ex.: chefe "foge" de propósito numa fase). */
    public static boolean release(Entity boss) {
        return BossArenaService.release(boss.getUUID());
    }

    /** O chefe tem arena selada (luta ou os 30 s depois)? */
    public static boolean isSealed(Entity boss) {
        return BossArenaService.arenaOf(boss.getUUID()) != null;
    }

    /** Arena que contém a posição (servidor), ou null. */
    @Nullable
    public static Info at(Level level, Vec3 pos) {
        return info(BossArenaService.at(level, pos));
    }

    /** O bloco está protegido agora (arena selada ou área sempre protegida)? */
    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        return BossArenaService.isProtected(level, pos);
    }

    /** Todas as arenas seladas agora. */
    public static List<Info> all() {
        return BossArenaService.all().stream().map(BossArena::info).toList();
    }

    /** Esta entidade conta como chefe de arena? */
    public static boolean isArenaBoss(Entity e) {
        return BossArenaService.isArenaBoss(e);
    }

    // ---------------------------------------------------------------- registro (init do módulo)
    /** Tipo extra que sela arena (chefe fora da tag {@code c:bosses}). */
    public static void registerType(ResourceLocation entityTypeId) {
        SocialCore.registerArenaType(entityTypeId);
    }

    public static void registerType(EntityType<?> type) {
        SocialCore.registerArenaType(type);
    }

    /** Tipo que NUNCA sela arena (luta que depende de construir/minerar, como o Dragão do Fim). */
    public static void excludeType(ResourceLocation entityTypeId) {
        SocialCore.excludeArenaType(entityTypeId);
    }

    /** Estrutura (worldgen) sempre protegida, por id (ex.: o palácio de chefes de uma dimensão nova). */
    public static void protectStructure(ResourceLocation structureId) {
        SocialCore.protectStructure(structureId);
    }

    /** Lugar ({@link Places}) sempre protegido, por id. */
    public static void protectPlace(String placeId) {
        SocialCore.protectPlace(placeId);
    }

    @Nullable
    private static Info info(@Nullable Arena a) {
        return a == null ? null : new Info(a.boss, a.dim.location(), a.center, a.radius, a.releasing());
    }

    private BossArena() {}
}
