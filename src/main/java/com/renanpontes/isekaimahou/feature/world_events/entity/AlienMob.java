package com.renanpontes.isekaimahou.feature.world_events.entity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base dos invasores do meteoro: GeckoLib com "idle"/"walk" + golpes disparáveis, caçam jogadores, não brigam entre
 * si e NÃO quebram blocos. Os que vêm de um evento ({@link #markInvader}) não somem por distância, mas vão embora
 * sozinhos depois de um tempo sem ninguém por perto — o mundo não fica cheio de alienígenas esquecidos.
 */
public abstract class AlienMob extends Monster implements GeoEntity {
    /** Tempo de vida de um invasor de evento (ticks) quando ninguém aparece para lutar. */
    private static final int INVADER_LIFE = 20 * 60 * 18;
    private static final Map<String, RawAnimation> ANIMS = new ConcurrentHashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks até ir embora; -1 = sem prazo (ovo, comando de invocar). */
    private int lifeLeft = -1;

    protected AlienMob(EntityType<? extends AlienMob> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    protected abstract String modelId();

    /** Animações de golpe (controlador "action"). */
    protected abstract List<String> actions();

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        addCombatGoals();
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, AlienMob.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    protected abstract void addCombatGoals();

    // ------------------------------------------------------------------ invasor de evento
    /** Marca como invasor de evento: fica até alguém vir lutar, mas tem prazo para ir embora. */
    public void markInvader() {
        setPersistenceRequired();
        lifeLeft = INVADER_LIFE;
    }

    /**
     * Cria um invasor de evento do tipo dado em {@code pos} (pés). Devolve null se o tipo não criou a entidade.
     * Não nasce em dificuldade Pacífica (o jogo o removeria no tick seguinte).
     */
    @Nullable
    public static <T extends AlienMob> T spawnInvader(ServerLevel level, EntityType<T> type, BlockPos pos) {
        T mob = type.create(level);
        if (mob == null) return null;
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        mob.markInvader();
        if (!level.addFreshEntity(mob)) return null;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                24, 0.4, 0.6, 0.4, 0.05);
        return mob;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (lifeLeft < 0) return;
        // Com alvo, o prazo não corre (a luta nunca é cortada no meio)
        if (getTarget() != null) return;
        if (--lifeLeft <= 0 && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + getBbHeight() * 0.5, getZ(), 30, 0.4, 0.6, 0.4, 0.3);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.6F);
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("WeLife", lifeLeft);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("WeLife")) lifeLeft = tag.getInt("WeLife");
    }

    // ------------------------------------------------------------------ lados
    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof AlienMob || super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != this && attacker instanceof AlienMob) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof AlienMob) && super.canAttack(target);
    }

    // ------------------------------------------------------------------ animação
    protected void action(String anim) {
        triggerAnim("action", anim);
    }

    protected RawAnimation anim(String name, boolean loop) {
        String full = "animation." + modelId() + "." + name;
        return ANIMS.computeIfAbsent((loop ? "loop:" : "play:") + full,
                k -> loop ? RawAnimation.begin().thenLoop(full) : RawAnimation.begin().thenPlay(full));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4,
                state -> state.setAndContinue(anim(state.isMoving() ? "walk" : "idle", true))));
        AnimationController<AlienMob> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        for (String a : actions()) action.triggerableAnim(a, anim(a, false));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
