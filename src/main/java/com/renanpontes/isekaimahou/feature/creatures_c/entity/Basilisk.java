package com.renanpontes.isekaimahou.feature.creatures_c.entity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.creatures_c.CcFx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Basilisco: lagarto-rei dos desertos e cavernas. Quem OLHAR para ele por 2 s fica petrificado
 * (Atordoado + Lentidão IV). Ele avisa: ergue a cabeça e o colar, e o corpo do jogador começa a soltar pó de pedra.
 * Abóbora entalhada na cabeça protege do olhar. Fraco a LUZ: sob o sol fica fraco, o olhar demora o dobro
 * e ele procura sombra; magia de luz causa dano dobrado.
 */
public class Basilisk extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_GAZING = SynchedEntityData.defineId(Basilisk.class, EntityDataSerializers.BOOLEAN);
    /** Ticks olhando para virar pedra (2 s). */
    public static final int GAZE_TICKS = 40;
    private static final double GAZE_RANGE = 20.0;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.basilisk.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.basilisk.walk");
    private static final RawAnimation GAZE = RawAnimation.begin().thenLoop("animation.basilisk.gaze");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.basilisk.bite");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Por jogador: >0 = ticks sendo encarado; <0 = tempo de graça depois de petrificar. */
    private final Map<UUID, Integer> gaze = new HashMap<>();

    public Basilisk(EntityType<? extends Basilisk> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GAZING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.addGoal(3, new SeekShadeGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean isGazing() {
        return entityData.get(DATA_GAZING);
    }

    /** Sob sol direto (dia, céu aberto, sem chuva): enfraquecido — fraco a luz. */
    public boolean isDazzled() {
        Level lv = level();
        if (!lv.isDay() || isInWaterRainOrBubble()) return false;
        BlockPos eye = BlockPos.containing(getX(), getEyeY(), getZ());
        return lv.canSeeSky(eye) && lv.getBrightness(LightLayer.SKY, eye) >= 13;
    }

    // =====================================================================================
    //  Olhar petrificante
    // =====================================================================================

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel sl && isAlive()) {
            updateGaze(sl);
            if (tickCount % 20 == 0 && isDazzled()) {
                addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
            }
        } else if (level().isClientSide && isGazing() && tickCount % 3 == 0) {
            // Brilho dourado saindo dos olhos
            Vec3 head = headPos();
            level().addParticle(CcFx.GAZE_DUST, head.x + (random.nextDouble() - 0.5) * 0.4, head.y + 0.1,
                    head.z + (random.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
        }
    }

    /** Posição aproximada da cabeça (na frente do corpo). */
    private Vec3 headPos() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(getX() - Mth.sin(yaw) * 0.95, getY() + 0.45, getZ() + Mth.cos(yaw) * 0.95);
    }

    private void updateGaze(ServerLevel sl) {
        boolean dazzled = isDazzled();
        boolean watched = false;
        for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(GAZE_RANGE),
                pl -> pl.isAlive() && !pl.isSpectator() && !pl.isCreative())) {
            UUID id = p.getUUID();
            int v = gaze.getOrDefault(id, 0);
            if (v >= 0 && isLookedAtBy(p)) {
                watched = true;
                // Sob o sol o olhar perde força: acumula na metade da velocidade.
                // Avisos só no tick em que sobe (senão repetiriam enquanto o valor fica parado)
                if (!dazzled || tickCount % 2 == 0) {
                    v++;
                    if (v == 10) p.displayClientMessage(Component.translatable("message.isekai_mahou.basilisk_gaze"), true);
                    if (v % 10 == 0 && v < GAZE_TICKS) warn(sl, p, v);
                    if (v >= GAZE_TICKS) {
                        CcFx.petrify(sl, p, 60);
                        p.displayClientMessage(Component.translatable("message.isekai_mahou.basilisk_petrified"), true);
                        v = -100;
                    }
                }
            } else if (v > 0) {
                v = Math.max(0, v - 2);
            } else if (v < 0) {
                v++;
            }
            gaze.put(id, v);
        }
        if (tickCount % 100 == 0) {
            gaze.keySet().removeIf(id -> {
                Player p = sl.getPlayerByUUID(id);
                return p == null || p.distanceToSqr(this) > GAZE_RANGE * GAZE_RANGE * 4;
            });
        }
        if (watched != isGazing()) entityData.set(DATA_GAZING, watched);
    }

    /** O jogador está olhando para o basilisco (com linha de visão)? */
    private boolean isLookedAtBy(Player p) {
        if (p.hasEffect(MobEffects.BLINDNESS) || p.hasEffect(MobEffects.DARKNESS)) return false;
        if (p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) return false;
        Vec3 look = p.getViewVector(1.0F).normalize();
        Vec3 to = new Vec3(getX() - p.getX(), getY(0.5) - p.getEyeY(), getZ() - p.getZ());
        double d = to.length();
        if (d > GAZE_RANGE || d < 0.01) return false;
        double dot = look.dot(to.scale(1.0 / d));
        return dot > 1.0 - 0.12 / Math.max(1.0, d) && p.hasLineOfSight(this);
    }

    /** Aviso crescente: pó de pedra subindo pelo corpo do jogador e estalos de pedra. */
    private void warn(ServerLevel sl, Player p, int v) {
        float t = v / (float) GAZE_TICKS;
        sl.sendParticles(CcFx.STONE_DUST, p.getX(), p.getY() + p.getBbHeight() * t, p.getZ(), 8, 0.3, 0.2, 0.3, 0.0);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.STONE_HIT, SoundSource.HOSTILE, 0.8F, 0.6F + t * 0.6F);
    }

    // =====================================================================================
    //  Combate
    // =====================================================================================

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            triggerAnim("action", "bite");
            if (target instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), this);
            playSound(SoundEvents.CAT_HISS, 1.0F, 0.5F);
        }
        return hit;
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CAT_HISS;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.TURTLE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TURTLE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.TURTLE_SHAMBLE, 0.4F, 0.6F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.55F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    // =====================================================================================
    //  Animações
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (isGazing()) return state.setAndContinue(GAZE);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("bite", BITE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Fraco a luz: sem alvo e debaixo do sol, procura um lugar coberto por perto. */
    class SeekShadeGoal extends Goal {
        private Vec3 shade;

        SeekShadeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (getTarget() != null || !isDazzled() || random.nextInt(30) != 0) return false;
            shade = findShade();
            return shade != null;
        }

        @Override
        public void start() {
            getNavigation().moveTo(shade.x, shade.y, shade.z, 1.1);
        }

        @Override
        public boolean canContinueToUse() {
            return getTarget() == null && !getNavigation().isDone();
        }

        private Vec3 findShade() {
            BlockPos base = blockPosition();
            for (int i = 0; i < 12; i++) {
                BlockPos p = base.offset(random.nextInt(21) - 10, random.nextInt(7) - 3, random.nextInt(21) - 10);
                if (!level().canSeeSky(p) && level().getBlockState(p).isAir()
                        && level().getBlockState(p.below()).isSolidRender(level(), p.below())) {
                    return Vec3.atBottomCenterOf(p);
                }
            }
            return null;
        }
    }
}
