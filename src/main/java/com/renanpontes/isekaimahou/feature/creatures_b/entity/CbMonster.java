package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
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
 * Base dos humanoides do módulo: modelo GeckoLib com dois controladores
 * ("main": parado/andando/correndo; "action": ataques e habilidades disparados pelo servidor),
 * facções aliadas, chamado de reforços ao apanhar e um estado "ocupado" (introdução de chefe, atordoado).
 */
public abstract class CbMonster extends Monster implements GeoEntity {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final Map<String, RawAnimation> loops = new HashMap<>();
    /** Ticks em que o mob fica parado sem atacar (introdução, atordoado...). */
    protected int busyTicks;

    protected CbMonster(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public abstract CbFaction faction();

    /** Nome do arquivo de animações (animations/entity/<id>.animation.json). */
    protected abstract String animId();

    /** Animações de ação (tocadas uma vez) que o servidor pode disparar. */
    protected String[] actionAnims() {
        return new String[] {"attack"};
    }

    /** Multiplicador do tom de voz (grandes = mais grave). */
    protected float voicePitch() {
        return 1.0F;
    }

    /** Raio do chamado de reforços quando apanha (0 = não chama). */
    protected double alertRadius() {
        return 16.0;
    }

    // =====================================================================================
    //  Estado "ocupado"
    // =====================================================================================
    public boolean isBusy() {
        return busyTicks > 0;
    }

    public void setBusy(int ticks) {
        busyTicks = Math.max(busyTicks, ticks);
        getNavigation().stop();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (busyTicks > 0) {
            busyTicks--;
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        }
    }

    /** Goal de prioridade 0 que trava andar/olhar enquanto ocupado. Subclasses adicionam no registerGoals. */
    protected Goal busyGoal() {
        return new Goal() {
            {
                setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
            }

            @Override
            public boolean canUse() {
                return isBusy();
            }

            @Override
            public boolean requiresUpdateEveryTick() {
                return true;
            }

            @Override
            public void tick() {
                getNavigation().stop();
            }
        };
    }

    // =====================================================================================
    //  Facção e reforços
    // =====================================================================================
    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof CbMonster m && m.faction() == faction()) return true;
        return super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && attacker != this
                && !isAlliedTo(attacker) && alertRadius() > 0) {
            alertFaction(attacker, alertRadius());
        }
        return hurt;
    }

    /** Faz os membros da facção por perto (sem alvo) atacarem {@code target}. */
    public void alertFaction(LivingEntity target, double radius) {
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        for (CbMonster m : level().getEntitiesOfClass(CbMonster.class, getBoundingBox().inflate(radius),
                m -> m != this && m.isAlive() && m.faction() == faction() && m.getTarget() == null)) {
            m.setTarget(target);
        }
    }

    // =====================================================================================
    //  Combate
    // =====================================================================================
    @Override
    public boolean doHurtTarget(Entity target) {
        playAction("attack");
        return super.doHurtTarget(target);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * voicePitch();
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BusyTicks", busyTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        busyTicks = tag.getInt("BusyTicks");
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    /** Dispara uma animação de ação (só no servidor; o GeckoLib envia aos clientes). */
    public void playAction(String name) {
        if (!level().isClientSide) triggerAnim("action", name);
    }

    protected String animName(String n) {
        return "animation." + animId() + "." + n;
    }

    /** Animação contínua atual (idle/walk/run ou estados especiais das subclasses). */
    protected String loopAnim(boolean moving) {
        if (!moving) return "idle";
        return isAggressive() ? "run" : "walk";
    }

    private PlayState mainState(AnimationState<CbMonster> state) {
        String n = loopAnim(state.isMoving());
        return state.setAndContinue(loops.computeIfAbsent(n, k -> RawAnimation.begin().thenLoop(animName(k))));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        AnimationController<CbMonster> main = new AnimationController<>(this, "main", 4, this::mainState);
        AnimationController<CbMonster> action = new AnimationController<>(this, "action", 1, s -> PlayState.STOP);
        for (String a : actionAnims()) action.triggerableAnim(a, RawAnimation.begin().thenPlay(animName(a)));
        reg.add(main, action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
