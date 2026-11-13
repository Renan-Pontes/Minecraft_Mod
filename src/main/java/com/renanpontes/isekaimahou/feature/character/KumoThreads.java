package com.renanpontes.isekaimahou.feature.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.core.MagicHover;
import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * v1.7 — <b>Aracnídeo = Kumoko</b> (Kumo desu ga, Nani ka?). Fios, armadilhas e Olhos Malignos; o nome da raça evolui a
 * cada nível de raça (Pequena Taratect Menor → Pequena Taratect Venenosa → Zoa Ele → Zana Horowa → Arachne,
 * {@link Race#stageName}).
 * <ul>
 *   <li><b>Fio de Aranha</b> (nível I, Shift + tecla racial): lança um fio até o bloco que você olha (12/16/20/24 blocos;
 *       transformado 28) e te puxa até lá — sem dano de queda por 3 s. Acertando um inimigo, puxa ELE até você (chefes
 *       só ficam lentos). Recarga 4/3,5/3/2,5 s (transformado 1,5 s).</li>
 *   <li><b>Armadilha de Fios</b> (nível II+): agachado e parado 2 s, você deixa um fio invisível no chão (2/3/3 ao mesmo
 *       tempo, 4 transformado; 60 s). O 1º inimigo que pisa fica Enraizado e Envenenado. Só você vê um brilho fraco.</li>
 *   <li><b>Olho Maligno</b> (nível III+): a cada 6 s (IV: 4 s; transformado: 2 s) o inimigo que você ENCARA (16 blocos)
 *       sofre a Paralisia (Lentidão forte); do IV também a Maldição (Corrompido); transformado, às vezes Petrifica.</li>
 * </ul>
 */
public final class KumoThreads {
    private static final int TRAP_TICKS = 60 * 20;
    private static final int STILL_TO_TRAP = 40;
    private static final int SAFE_FALL_TICKS = 60;
    private static final int THREAD_RGB = 0xF0F0F0;
    private static final int EYE_RGB = 0xB050F0;

    private record Trap(ResourceKey<Level> dim, BlockPos pos, long until) {}

    private static final Map<UUID, Long> THREAD_READY = new ConcurrentHashMap<>();
    /** Até quando a queda não machuca (depois de um puxão do fio). */
    private static final Map<UUID, Long> SAFE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Trap>> TRAPS = new ConcurrentHashMap<>();
    /** Ticks agachado e parado (−1 = já armou: precisa se levantar para armar outra). */
    private static final Map<UUID, Integer> STILL = new ConcurrentHashMap<>();
    /** Posição na checagem anterior (no servidor a velocidade do jogador não é confiável para "parado"). */
    private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
    }

    static void forget(UUID id) {
        THREAD_READY.remove(id);
        SAFE_UNTIL.remove(id);
        TRAPS.remove(id);
        STILL.remove(id);
        LAST_POS.remove(id);
    }

    static void clearAll() {
        THREAD_READY.clear();
        SAFE_UNTIL.clear();
        TRAPS.clear();
        STILL.clear();
        LAST_POS.clear();
    }

    /** Depois de um puxão do fio a queda não machuca (RacePassives). */
    static boolean safeFall(ServerPlayer sp) {
        Long until = SAFE_UNTIL.get(sp.getUUID());
        return until != null && sp.level().getGameTime() <= until;
    }

    // =====================================================================================
    //  Fio de Aranha (Shift + tecla racial)
    // =====================================================================================
    static void thread(ServerPlayer sp) {
        int lv = RaceProgress.level(sp);
        boolean form = RaceForms.formOf(sp) == Race.ARACHNID;
        long now = sp.level().getGameTime();
        Long ready = THREAD_READY.get(sp.getUUID());
        if (ready != null && now < ready && !sp.getAbilities().instabuild) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.thread_cooldown",
                    String.format("%.1f", (ready - now) / 20.0)).withColor(0xA89EC0), true);
            return;
        }
        double range = form ? 28 : RacePassives.at(lv, 12, 16, 20, 24);
        ServerLevel level = sp.serverLevel();
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        Vec3 blockEnd = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, sp, eye, blockEnd,
                new AABB(eye, blockEnd).inflate(1.0), e -> RaceForms.foe(sp, e));
        Vec3 hand = sp.position().add(0, sp.getBbHeight() * 0.6, 0).add(look.scale(0.4));
        BlockParticleOption web = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());
        if (eh != null && eh.getEntity() instanceof LivingEntity le) {
            // Puxa a presa (chefes não se mexem: só ficam presos)
            Vec3 at = RaceFx.center(le);
            RaceFx.line(level, hand, at, RaceFx.dust(THREAD_RGB, 0.6F));
            if (!Statuses.isBossLike(le)) {
                Vec3 pull = sp.position().subtract(le.position());
                double dist = pull.length();
                pull = pull.normalize().scale(Math.min(1.6, 0.4 + dist * 0.09));
                le.setDeltaMovement(pull.x, Math.max(0.25, pull.y + 0.3), pull.z);
                le.hurtMarked = true;
            }
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), sp);
            level.sendParticles(web, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.05);
        } else if (bh.getType() == HitResult.Type.BLOCK) {
            // Puxa você até o ponto (balançar entre paredes, subir em penhascos)
            Vec3 to = bh.getLocation().subtract(sp.position());
            double dist = to.length();
            double speed = Math.min(form ? 2.2 : 1.8, 0.7 + dist * 0.08);
            Vec3 v = to.normalize().scale(speed);
            sp.setDeltaMovement(v.x, v.y + 0.25, v.z);
            sp.hurtMarked = true;
            sp.resetFallDistance();
            MagicHover.allow(sp, 80);
            SAFE_UNTIL.put(sp.getUUID(), now + SAFE_FALL_TICKS);
            RaceFx.line(level, hand, bh.getLocation(), RaceFx.dust(THREAD_RGB, 0.6F));
            level.sendParticles(web, bh.getLocation().x, bh.getLocation().y, bh.getLocation().z, 6, 0.15, 0.15, 0.15, 0.02);
        } else {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.thread_miss").withColor(0xA89EC0), true);
            return;
        }
        THREAD_READY.put(sp.getUUID(), now + (form ? 30 : (long) RacePassives.at(lv, 80, 70, 60, 50)));
        RaceFx.sound(level, eye, SoundEvents.FISHING_BOBBER_THROW, 0.8F, 1.6F);
        RaceFx.sound(level, eye, SoundEvents.SPIDER_STEP, 0.6F, 1.4F);
    }

    // =====================================================================================
    //  Armadilha de Fios (a cada 5 ticks, CharacterService)
    // =====================================================================================
    static void tickTraps(ServerPlayer sp, CharacterData d) {
        List<Trap> traps = TRAPS.get(sp.getUUID());
        boolean arachnid = d.is(Race.ARACHNID);
        if (!arachnid) {
            if (traps != null) TRAPS.remove(sp.getUUID());
            STILL.remove(sp.getUUID());
            return;
        }
        int lv = RaceProgress.level(sp);
        long now = sp.level().getGameTime();
        // Armar: agachado, no chão e parado
        if (lv >= 2) {
            Vec3 last = LAST_POS.put(sp.getUUID(), sp.position());
            boolean still = sp.isShiftKeyDown() && sp.onGround() && last != null && last.distanceToSqr(sp.position()) < 1e-3;
            int n = STILL.getOrDefault(sp.getUUID(), 0);
            if (!still) {
                if (n != 0) STILL.put(sp.getUUID(), 0);
            } else if (n >= 0) {
                n += 5;
                if (n >= STILL_TO_TRAP) {
                    layTrap(sp, lv);
                    n = -1;
                }
                STILL.put(sp.getUUID(), n);
            }
        }
        if (traps == null || traps.isEmpty()) return;
        ServerLevel level = sp.serverLevel();
        BlockParticleOption web = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());
        traps.removeIf(t -> t.until() < now);
        for (Trap t : new ArrayList<>(traps)) {
            if (!t.dim().equals(level.dimension())) continue;
            if (t.pos().distSqr(sp.blockPosition()) > 64 * 64) continue;
            Vec3 c = Vec3.atBottomCenterOf(t.pos());
            // Só o dono vê o fio (brilho fraco a cada 2 s)
            if (now % 40 == 0) level.sendParticles(sp, RaceFx.dust(THREAD_RGB, 0.5F), false, c.x, c.y + 0.1, c.z, 3, 0.3, 0.02, 0.3, 0);
            List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(c.x - 0.8, c.y - 0.2, c.z - 0.8, c.x + 0.8, c.y + 1.0, c.z + 0.8), e -> RaceForms.foe(sp, e));
            if (caught.isEmpty()) continue;
            traps.remove(t);
            for (LivingEntity le : caught) {
                Statuses.apply(le, Statuses.Kind.ROOTED, 1, 60, sp, 1F);
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2), sp);
                le.addEffect(new MobEffectInstance(MobEffects.POISON, 80, lv >= 4 ? 1 : 0), sp);
                RaceForms.hit(sp, le, 3F);
                level.sendParticles(web, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 16, 0.4, 0.4, 0.4, 0.05);
            }
            RaceFx.ring(level, c.add(0, 0.2, 0), 1.2, 12, RaceFx.dust(THREAD_RGB, 0.9F));
            RaceFx.sound(level, c, SoundEvents.SPIDER_AMBIENT, 0.8F, 1.3F);
            RaceFx.sound(level, c, SoundEvents.WOOL_PLACE, 1.0F, 0.6F);
        }
    }

    private static void layTrap(ServerPlayer sp, int lv) {
        boolean form = RaceForms.formOf(sp) == Race.ARACHNID;
        int max = form ? 4 : lv >= 3 ? 3 : 2;
        List<Trap> traps = TRAPS.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>());
        BlockPos pos = sp.blockPosition();
        traps.removeIf(t -> t.dim().equals(sp.level().dimension()) && t.pos().distSqr(pos) < 4);
        while (traps.size() >= max) traps.remove(0);
        traps.add(new Trap(sp.level().dimension(), pos, sp.level().getGameTime() + TRAP_TICKS));
        ServerLevel level = sp.serverLevel();
        RaceFx.ring(level, Vec3.atBottomCenterOf(pos).add(0, 0.1, 0), 0.9, 10, RaceFx.dust(THREAD_RGB, 0.7F));
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.SPIDER_STEP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.trap_set", traps.size(), max).withColor(Race.ARACHNID.rgb), true);
    }

    // =====================================================================================
    //  Olho Maligno (a cada segundo, RacePassives.tickSecond)
    // =====================================================================================
    static void tickEvilEye(ServerPlayer sp, CharacterData d) {
        if (!d.is(Race.ARACHNID) || !sp.isAlive()) return;
        boolean form = RaceForms.formOf(sp) == Race.ARACHNID;
        int lv = RaceProgress.level(sp);
        if (lv < 3 && !form) return;
        int every = form ? 2 : lv >= 4 ? 4 : 6;
        if ((sp.tickCount / 20) % every != 0) return;
        LivingEntity t = stared(sp, 16.0);
        if (t == null) return;
        ServerLevel level = sp.serverLevel();
        // Paralisia
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3), sp);
        // Maldição
        if (lv >= 4 || form) Statuses.apply(t, Statuses.Kind.CORRUPTED, 1, 60, sp, 1F);
        // Petrificação (só transformado, às vezes)
        if (form && sp.getRandom().nextInt(4) == 0) Statuses.apply(t, Statuses.Kind.PETRIFIED, 1, 20, sp, 1F);
        Vec3 eye = t.getEyePosition();
        level.sendParticles(RaceFx.dust(EYE_RGB, 1.1F), eye.x, eye.y + 0.2, eye.z, 6, 0.2, 0.1, 0.2, 0);
        level.sendParticles(ParticleTypes.WITCH, eye.x, eye.y + 0.3, eye.z, 2, 0.15, 0.05, 0.15, 0);
        RaceFx.sound(level, eye, SoundEvents.ENDER_EYE_DEATH, 0.35F, 1.8F);
    }

    /** O inimigo que o jogador está ENCARANDO (bem no centro da mira, à vista). */
    private static LivingEntity stared(ServerPlayer sp, double range) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        LivingEntity best = null;
        double bestDot = 0.97;
        for (LivingEntity le : RaceForms.foesAround(sp, range)) {
            Vec3 to = le.getEyePosition().subtract(eye);
            double len = to.length();
            if (len < 1e-3) continue;
            double dot = to.scale(1 / len).dot(look);
            if (dot <= bestDot) continue;
            BlockHitResult wall = sp.level().clip(new ClipContext(eye, le.getEyePosition(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, sp));
            if (wall.getType() == HitResult.Type.BLOCK) continue;
            bestDot = dot;
            best = le;
        }
        return best;
    }

    private KumoThreads() {}
}
