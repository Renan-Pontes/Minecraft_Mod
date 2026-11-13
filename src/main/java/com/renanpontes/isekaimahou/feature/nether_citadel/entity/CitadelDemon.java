package com.renanpontes.isekaimahou.feature.nether_citadel.entity;

import java.util.List;

import com.renanpontes.isekaimahou.feature.nether_citadel.NcDemons;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
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
 * Base da guarnição da Cidadela (Cavaleiro Infernal, Súcubo): GeckoLib com "idle"/"walk" + golpes disparáveis, só caçam
 * jogadores, não se ferem entre si nem ferem o Lorde Demônio, chamam os vizinhos quando apanham.
 */
public abstract class CitadelDemon extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected CitadelDemon(EntityType<? extends CitadelDemon> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    /** Id do modelo/animação (= id do tipo). */
    protected abstract String modelId();

    /** Animações disparáveis no controlador "action". */
    protected abstract List<String> actions();

    /** Metas de combate próprias (prioridade 2..4). */
    protected abstract void registerCombatGoals();

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        registerCombatGoals();
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, CitadelDemon.class, DemonLord.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() != null && source.getEntity() != this && NcDemons.isGarrison(source.getEntity())) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return NcDemons.isGarrison(other) || super.isAlliedTo(other);
    }

    /** Alvo válido dos golpes em área. */
    protected boolean isFoe(LivingEntity e) {
        if (!e.isAlive() || e == this || NcDemons.isGarrison(e)) return false;
        return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    protected void action(String anim) {
        triggerAnim("action", anim);
    }

    private RawAnimation anim(String name, boolean loop) {
        String full = "animation." + modelId() + "." + name;
        return loop ? RawAnimation.begin().thenLoop(full) : RawAnimation.begin().thenPlay(full);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = anim("idle", true);
        RawAnimation walk = anim("walk", true);
        controllers.add(new AnimationController<>(this, "main", 5, state -> state.setAndContinue(state.isMoving() ? walk : idle)));
        AnimationController<CitadelDemon> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        for (String a : actions()) action.triggerableAnim(a, anim(a, false));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
