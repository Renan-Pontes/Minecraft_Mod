package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import java.util.HashMap;
import java.util.Map;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base de todas as criaturas do módulo: modelo GeckoLib com dois controladores
 * ("movement" = loop idle/andar/voar/nadar e "action" = ataques/conjurações disparados pelo servidor),
 * aliança entre elementais (não brigam entre si) e ganchos de efeitos de cliente e de golpe corpo a corpo.
 */
public abstract class ElementalCreature extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<String, RawAnimation> loops = new HashMap<>();
    private final String animPrefix;

    protected ElementalCreature(EntityType<? extends ElementalCreature> type, Level level) {
        super(type, level);
        this.animPrefix = "animation." + EntityType.getKey(type).getPath() + ".";
    }

    /** Elemento da criatura (cor das partículas, essência que deixa cair). */
    public abstract Element element();

    // =====================================================================================
    //  Animações
    // =====================================================================================

    /** Nome da animação em loop do estado atual (sem o prefixo "animation.<id>."). */
    protected String loopAnimation(boolean moving) {
        return moving ? "walk" : "idle";
    }

    /** Animações que o servidor pode disparar com {@link #playAction}. */
    protected String[] actionAnimations() {
        return new String[] {"attack"};
    }

    protected boolean isAnimMoving(AnimationState<ElementalCreature> state) {
        return state.isMoving();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ElementalCreature>(this, "movement", 4,
                state -> state.setAndContinue(loop(loopAnimation(isAnimMoving(state))))));
        AnimationController<ElementalCreature> actions = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        for (String a : actionAnimations()) actions.triggerableAnim(a, RawAnimation.begin().thenPlay(animPrefix + a));
        controllers.add(actions);
    }

    private RawAnimation loop(String name) {
        return loops.computeIfAbsent(name, n -> RawAnimation.begin().thenLoop(animPrefix + n));
    }

    /** Dispara uma animação de ação (sincronizada para os clientes). */
    public void playAction(String name) {
        if (!level().isClientSide) triggerAnim("action", name);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Transparência usada pelo renderer (1 = opaco). Só faz efeito em renderers translúcidos. */
    public float renderAlpha(float partialTick) {
        return 1.0F;
    }

    // =====================================================================================
    //  Comportamento comum
    // =====================================================================================

    /** Elementais do Overworld formam um "ecossistema": não se atacam nem se ferem com as próprias magias. */
    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof ElementalCreature || super.isAlliedTo(other);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && isAlive()) clientEffects();
    }

    /** Partículas ambientes (roda só no cliente). */
    protected void clientEffects() {
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        playAction("attack");
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) onMeleeHit(living);
        return hit;
    }

    /** Efeito extra do golpe corpo a corpo (queimar, encharcar...). */
    protected void onMeleeHit(LivingEntity target) {
    }

    // =====================================================================================
    //  Ajudantes de efeitos
    // =====================================================================================

    /** Ponto relativo ao corpo: frente (+), cima e direita (+), usando a rotação do corpo. */
    public Vec3 localPoint(double forward, double up, double right) {
        Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
        return new Vec3(getX() + f.x * forward - f.z * right, getY() + up, getZ() + f.z * forward + f.x * right);
    }

    protected void serverParticles(ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        if (level() instanceof ServerLevel sl) sl.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    protected void playSoundHere(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, getSoundSource(), volume, pitch);
    }

    /** Partícula de cliente num ponto aleatório do corpo. */
    protected void bodyParticle(ParticleOptions p, double heightFrac, double spread, double vy) {
        level().addParticle(p, getRandomX(spread), getY() + getBbHeight() * heightFrac, getRandomZ(spread), 0, vy, 0);
    }
}
