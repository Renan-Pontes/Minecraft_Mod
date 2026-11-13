package com.renanpontes.isekaimahou.feature.combat_feel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * <b>Conjuradores mais espertos</b> — mobs da tag de entidade {@code isekai_mahou:smart_casters} ganham, ao entrar no
 * mundo, dois objetivos de IA extras (nenhum módulo de criatura é editado):
 * <ul>
 *   <li><b>Desviam</b>: quando um jogador conjura mirando neles, dão um salto de lado (às vezes; com recarga);</li>
 *   <li><b>Recuam</b>: com o alvo colado, correm para abrir distância (só quem anda por caminho);</li>
 *   <li><b>Se curam</b>: abaixo de 45% de vida canalizam ~1,5 s e recuperam 25% — levar dano no meio CANCELA a cura
 *       (no máximo 2 curas por luta de vida; quem já está cambaleando para o golpe de misericórdia não se cura);</li>
 *   <li><b>Pedem ajuda</b>: feridos, chamam até 4 monstros da mesma família que estavam à toa por perto.</li>
 * </ul>
 * Chefes ficam de fora (têm IA própria). Nada mexe em blocos. Barato: os objetivos só rodam com alvo; o desvio é
 * disparado pelo evento de conjuração (uma busca pequena por conjuração de jogador), sem varredura por tick.
 */
public final class SmartCasters {
    public static final TagKey<EntityType<?>> TAG = TagKey.create(Registries.ENTITY_TYPE, IsekaiMahou.id("smart_casters"));

    private static final double DODGE_RANGE = 18.0;
    /** Cosseno do cone de mira que conta como "mirando em mim" (~20°). */
    private static final double DODGE_CONE = 0.94;
    private static final float DODGE_CHANCE = 0.6F;
    private static final int HEAL_TICKS = 30, HEAL_COOLDOWN = 400, HEAL_FAIL_COOLDOWN = 160, MAX_HEALS = 2;
    private static final float HEAL_BELOW = 0.45F, HEAL_AMOUNT = 0.25F, HELP_BELOW = 0.75F;
    private static final int HELP_COOLDOWN = 600, HELP_MAX = 4;
    private static final double HELP_RANGE = 14.0;
    private static final double RETREAT_FROM = 5.0, RETREAT_TO = 10.0;

    /** Memória curta de cada conjurador (não é salva: recomeça quando a entidade recarrega). */
    static final class Mind {
        long nextDodge, nextHeal, nextHelp, nextRetreat;
        int heals;
        /** Tick em que a canalização da cura termina (0 = não está curando). */
        long healEnd;
        float healHealth;
    }

    private static final Map<Mob, Mind> MINDS = Collections.synchronizedMap(new WeakHashMap<>());

    static void init(IEventBus bus) {
        bus.addListener(EntityJoinLevelEvent.class, SmartCasters::onJoin);
        bus.addListener(EventPriority.LOWEST, false, SpellCastEvent.Post.class, SmartCasters::onCast);
        bus.addListener(ServerStoppedEvent.class, e -> MINDS.clear());
    }

    private static boolean enabled() {
        return FeelConfig.on(FeelConfig.SMART_CASTERS);
    }

    /** A memória do conjurador, ou null se o mob não é um conjurador esperto carregado. */
    @Nullable
    static Mind mind(Mob mob) {
        return MINDS.get(mob);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        if (!mob.getType().is(TAG) || BossKit.isBossLike(mob) || MINDS.containsKey(mob)) return;
        Mind mind = new Mind();
        MINDS.put(mob, mind);
        mob.goalSelector.addGoal(1, new ReflexGoal(mob, mind));
        if (mob instanceof PathfinderMob walker) mob.goalSelector.addGoal(1, new RetreatGoal(walker, mind));
    }

    private static boolean held(Mob mob) {
        return mob.hasEffect(CoreRegistries.STUNNED) || mob.hasEffect(CoreRegistries.ROOTED) || mob.hasEffect(CoreRegistries.FROZEN)
                || mob.isPassenger() || mob.isNoAi();
    }

    private static boolean owned(Mob mob) {
        return mob instanceof OwnableEntity own && own.getOwnerUUID() != null;
    }

    // =====================================================================================
    //  Desviar
    // =====================================================================================
    private static void onCast(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer sp) || !enabled() || sp.isSpectator()) return;
        ServerLevel level = sp.serverLevel();
        Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle();
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(DODGE_RANGE, 8.0, DODGE_RANGE),
                m -> m.isAlive() && m.getType().is(TAG));
        for (Mob mob : mobs) {
            Mind mind = MINDS.get(mob);
            if (mind == null || owned(mob)) continue;
            // Só quem já está em luta com alguém (ou foi ferido por este jogador) reage
            if (mob.getTarget() == null && mob.getLastHurtByMob() != sp) continue;
            Vec3 to = mob.getBoundingBox().getCenter().subtract(eye);
            double len = to.length();
            if (len < 2.5 || len > DODGE_RANGE || to.scale(1.0 / len).dot(look) < DODGE_CONE) continue;
            if (mob.getRandom().nextFloat() > DODGE_CHANCE) continue;
            dodge(mob, mind, eye, false);
        }
    }

    /** Salto de lado em relação a {@code from}. @return true se saltou. */
    static boolean dodge(Mob mob, Mind mind, Vec3 from, boolean force) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        if (!force && (now < mind.nextDodge || mind.healEnd > 0 || held(mob))) return false;
        if (!force && !mob.onGround() && !mob.isNoGravity() && !mob.isInWater()) return false;
        Vec3 away = new Vec3(mob.getX() - from.x, 0, mob.getZ() - from.z);
        if (away.lengthSqr() < 1.0E-4) return false;
        away = away.normalize();
        Vec3 side = new Vec3(-away.z, 0, away.x);
        if (mob.getRandom().nextBoolean()) side = side.scale(-1);
        if (!safeLanding(level, mob, side)) {
            side = side.scale(-1);
            if (!safeLanding(level, mob, side)) return false;
        }
        mind.nextDodge = now + 50 + mob.getRandom().nextInt(30);
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(v.x * 0.3 + side.x * 0.75, mob.onGround() ? 0.3 : v.y + 0.05, v.z * 0.3 + side.z * 0.75);
        mob.hurtMarked = true;
        mob.hasImpulse = true;
        level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.15, mob.getZ(), 5, 0.2, 0.05, 0.2, 0.02);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.4F, 1.7F);
        return true;
    }

    /** O lado do salto tem chão por perto e não é lava/fogo? (para ninguém se jogar de penhasco) */
    private static boolean safeLanding(ServerLevel level, Mob mob, Vec3 side) {
        BlockPos p = BlockPos.containing(mob.getX() + side.x * 2.2, mob.getY() + 0.2, mob.getZ() + side.z * 2.2);
        if (!level.isLoaded(p)) return false;
        if (level.getBlockState(p).isSuffocating(level, p)) return false;
        if (mob.isNoGravity()) return true;
        for (int i = 0; i <= 3; i++) {
            BlockPos q = p.below(i);
            var state = level.getBlockState(q);
            if (state.getFluidState().is(FluidTags.LAVA) || state.is(net.minecraft.world.level.block.Blocks.FIRE)) return false;
            if (i > 0 && !state.getCollisionShape(level, q).isEmpty()) return true;
        }
        return false;
    }

    // =====================================================================================
    //  Curar
    // =====================================================================================
    /** Começa a canalizar a cura. @return true se começou. */
    static boolean startHeal(Mob mob, Mind mind, boolean force) {
        if (!(mob.level() instanceof ServerLevel level) || mind.healEnd > 0) return false;
        long now = level.getGameTime();
        if (!force && (now < mind.nextHeal || mind.heals >= MAX_HEALS || held(mob))) return false;
        mind.healEnd = now + HEAL_TICKS;
        mind.healHealth = mob.getHealth();
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HEAL_TICKS, 2, false, false));
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 0.6F, 1.5F);
        return true;
    }

    /** Um passo da canalização: cancela se levou dano, cura no fim. */
    private static void tickHeal(Mob mob, Mind mind, ServerLevel level, long now) {
        double x = mob.getX(), y = mob.getY() + mob.getBbHeight() * 0.6, z = mob.getZ();
        if (mob.getHealth() < mind.healHealth - 0.01F || held(mob)) {
            // Contra-jogada: bater no conjurador no meio da cura quebra a magia
            mind.healEnd = 0;
            mind.nextHeal = now + HEAL_FAIL_COOLDOWN;
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 8, 0.25, 0.3, 0.25, 0.02);
            level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.5F, 1.4F);
            return;
        }
        if (now < mind.healEnd) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 2, 0.35, 0.4, 0.35, 0);
            return;
        }
        mind.healEnd = 0;
        mind.heals++;
        mind.nextHeal = now + HEAL_COOLDOWN;
        mob.heal(mob.getMaxHealth() * HEAL_AMOUNT);
        level.sendParticles(ParticleTypes.HEART, x, mob.getY() + mob.getBbHeight() + 0.3, z, 4, 0.3, 0.2, 0.3, 0);
        level.playSound(null, x, y, z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.7F, 1.5F);
    }

    // =====================================================================================
    //  Pedir ajuda
    // =====================================================================================
    /** Chama monstros da mesma família que estão à toa por perto para atacar {@code target}. @return quantos vieram. */
    static int callHelp(Mob mob, Mind mind, LivingEntity target) {
        if (!(mob.level() instanceof ServerLevel level)) return 0;
        mind.nextHelp = level.getGameTime() + HELP_COOLDOWN;
        String family = family(mob);
        boolean raider = mob instanceof Raider;
        List<Mob> near = level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(HELP_RANGE, 6.0, HELP_RANGE),
                m -> m != mob && m.isAlive() && m.getTarget() == null && !m.isNoAi() && !BossKit.isBossLike(m) && !owned(m)
                        && (m.getType() == mob.getType() || raider && m instanceof Raider
                                || m instanceof Enemy && family.equals(family(m))));
        int came = 0;
        for (Mob ally : near) {
            if (came >= HELP_MAX) break;
            if (!ally.canAttack(target)) continue;
            ally.setTarget(target);
            if (ally.getTarget() != target) continue; // outro módulo vetou (ex.: Modo Paz)
            came++;
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, ally.getX(), ally.getY() + ally.getBbHeight() + 0.3, ally.getZ(),
                    1, 0, 0, 0, 0);
        }
        // O grito aparece mesmo sem ninguém por perto: o jogador entende o que o conjurador tentou
        level.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getY() + mob.getBbHeight() + 0.4, mob.getZ(), 3, 0.3, 0.1, 0.3, 1);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 0.5F, 1.8F);
        return came;
    }

    /** "Família" pelo id: {@code isekai_mahou:goblin_shaman} → {@code isekai_mahou:goblin}. */
    private static String family(Mob mob) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        String path = key.getPath();
        int cut = path.indexOf('_');
        return key.getNamespace() + ":" + (cut > 0 ? path.substring(0, cut) : path);
    }

    // =====================================================================================
    //  Objetivos de IA
    // =====================================================================================
    /** Reflexos sem dono de movimento/olhar (roda junto com os objetivos da própria criatura): cura e pedido de ajuda. */
    private static final class ReflexGoal extends Goal {
        private final Mob mob;
        private final Mind mind;

        ReflexGoal(Mob mob, Mind mind) {
            this.mob = mob;
            this.mind = mind;
            setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            if (mind.healEnd > 0) return true;
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && enabled() && !owned(mob);
        }

        @Override
        public void tick() {
            if (!(mob.level() instanceof ServerLevel level)) return;
            long now = level.getGameTime();
            if (mind.healEnd > 0) {
                tickHeal(mob, mind, level, now);
                return;
            }
            LivingEntity target = mob.getTarget();
            if (target == null) return;
            float ratio = mob.getHealth() / Math.max(1F, mob.getMaxHealth());
            if (ratio < HELP_BELOW && now >= mind.nextHelp) callHelp(mob, mind, target);
            // Quem já cambaleia para o golpe de misericórdia está fraco demais para conjurar a cura
            if (ratio < HEAL_BELOW && !Finisher.isWounded(mob) && !mob.getTags().contains(Finisher.WOUNDED_TAG)) {
                startHeal(mob, mind, false);
            }
        }

        @Override
        public void stop() {
            // Perdeu o alvo no meio: a canalização termina sozinha no próximo canUse (healEnd > 0 mantém o objetivo)
        }
    }

    /** Com o alvo colado, corre para abrir distância e volta a conjurar de longe. */
    private static final class RetreatGoal extends Goal {
        private final PathfinderMob mob;
        private final Mind mind;
        private Vec3 dest;
        private long until;

        RetreatGoal(PathfinderMob mob, Mind mind) {
            this.mob = mob;
            this.mind = mind;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            if (t == null || !t.isAlive() || !enabled() || owned(mob) || mind.healEnd > 0 || held(mob)) return false;
            long now = mob.level().getGameTime();
            if (now < mind.nextRetreat || mob.distanceToSqr(t) > RETREAT_FROM * RETREAT_FROM) return false;
            dest = DefaultRandomPos.getPosAway(mob, 10, 5, t.position());
            if (dest == null || dest.distanceToSqr(t.position()) < mob.distanceToSqr(t)) {
                mind.nextRetreat = now + 40;
                return false;
            }
            return true;
        }

        @Override
        public void start() {
            until = mob.level().getGameTime() + 50;
            mob.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.3);
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && !mob.getNavigation().isDone() && mob.level().getGameTime() < until
                    && mob.distanceToSqr(t) < RETREAT_TO * RETREAT_TO && !held(mob);
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t != null) mob.getLookControl().setLookAt(t, 30F, 30F);
        }

        @Override
        public void stop() {
            mind.nextRetreat = mob.level().getGameTime() + 100 + mob.getRandom().nextInt(60);
            mob.getNavigation().stop();
            dest = null;
        }
    }

    private SmartCasters() {}
}
