package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base das criaturas do módulo (Vazios, Menos, Arrancar, fadas): GeckoLib com controlador "main" (parado/andar/voar) e
 * "action" (golpes disparados por {@link #action}), lado do reino (criaturas do mesmo reino não se ferem) e ajudas.
 */
public abstract class HmMob extends Monster implements GeoEntity {
    private static final Map<String, RawAnimation> ANIMS = new ConcurrentHashMap<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected HmMob(EntityType<? extends HmMob> type, Level level) {
        super(type, level);
    }

    /** Id do modelo/animações (= id da entidade). */
    public abstract String modelId();

    /** Reino de onde a criatura vem (aliados entre si). */
    public abstract HmRealm side();

    /** Golpes disparáveis no controlador "action". */
    protected abstract List<String> actionAnimations();

    /** Animação de base. */
    protected String baseAnimation(boolean moving) {
        return moving ? "walk" : "idle";
    }

    protected void action(String anim) {
        triggerAnim("action", anim);
    }

    /** Inimigo válido para os golpes de área (jogadores fora do criativo e quem não for do mesmo reino). */
    public boolean isFoe(LivingEntity e) {
        if (!e.isAlive() || e == this || HmAllies.isAlly(this, e)) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e == getTarget() || (e instanceof OwnableEntity own && own.getOwnerUUID() != null);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != this && HmAllies.isAlly(this, attacker)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !HmAllies.isAlly(this, target) && super.canAttack(target);
    }

    @Nullable
    protected LivingEntity liveTarget() {
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || (t instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            setTarget(null);
            return null;
        }
        return t;
    }

    // ------------------------------------------------------------------ GeckoLib

    protected RawAnimation loop(String name) {
        String full = "animation." + modelId() + "." + name;
        return ANIMS.computeIfAbsent("loop:" + full, k -> RawAnimation.begin().thenLoop(full));
    }

    protected RawAnimation play(String name) {
        String full = "animation." + modelId() + "." + name;
        return ANIMS.computeIfAbsent("play:" + full, k -> RawAnimation.begin().thenPlay(full));
    }

    private PlayState mainPredicate(AnimationState<HmMob> state) {
        return state.setAndContinue(loop(baseAnimation(state.isMoving())));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainPredicate));
        AnimationController<HmMob> act = new AnimationController<>(this, "action", 2, s -> PlayState.STOP);
        for (String a : actionAnimations()) act.triggerableAnim(a, play(a));
        controllers.add(act);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
