package com.renanpontes.isekaimahou.feature.depths.entity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.feature.bosses_g.entity.BgBones;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.GBossEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base das criaturas da masmorra (Verme Cavador, Explorador Perdido): GeckoLib com "idle"/"walk" + golpes
 * disparáveis, caçam jogadores, não se ferem entre si nem ferem o Verme-Rei (e vice-versa).
 */
public abstract class DepthsMob extends Monster implements GeoEntity, BgBones {
    private static final Map<String, RawAnimation> ANIMS = new ConcurrentHashMap<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected DepthsMob(EntityType<? extends DepthsMob> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    protected abstract String modelId();

    protected abstract List<String> actions();

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, DepthsMob.class, WormKing.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Lado da masmorra: as criaturas, o Verme-Rei e as larvas que ele invoca. */
    public static boolean isDepthsSide(Entity e) {
        return e instanceof DepthsMob || e instanceof WormKing || e.getTags().contains(GBossEntity.MINION_TAG);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return isDepthsSide(other) || super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != this && isDepthsSide(attacker)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isDepthsSide(target) && super.canAttack(target);
    }

    protected void action(String anim) {
        triggerAnim("action", anim);
    }

    protected RawAnimation anim(String name, boolean loop) {
        String full = "animation." + modelId() + "." + name;
        return ANIMS.computeIfAbsent((loop ? "loop:" : "play:") + full,
                k -> loop ? RawAnimation.begin().thenLoop(full) : RawAnimation.begin().thenPlay(full));
    }

    /** Animação de base (o Verme Cavador troca quando está enterrado). */
    protected RawAnimation baseAnimation(boolean moving) {
        return anim(moving ? "walk" : "idle", true);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(baseAnimation(state.isMoving()))));
        AnimationController<DepthsMob> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        for (String a : actions()) action.triggerableAnim(a, anim(a, false));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
