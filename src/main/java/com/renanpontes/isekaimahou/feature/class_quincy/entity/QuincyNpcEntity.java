package com.renanpontes.isekaimahou.feature.class_quincy.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.feature.class_quincy.Auswahlen;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Quincy do acampamento (3 variantes: Arqueiro, Sentinela e o Ancião). Neutro: defende o acampamento de monstros com
 * flechas espirituais e revida quem o ataca. Um jogador NUNCA o mata: abaixo de 30% de vida ele se RENDE (ajoelha,
 * fica invulnerável por 30 s). Rendido, um jogador Quincy pode realizar o <b>Auswählen</b> (clicar nele): o poder do
 * NPC passa para o jogador (contador do rank) e ele se desfaz em luz. Conversar (clicar sem duelo) dá dicas da classe.
 */
public class QuincyNpcEntity extends PathfinderMob implements GeoEntity {
    public static final int VARIANTS = 3;
    private static final String[] VARIANT_IDS = {"archer", "sentinel", "elder"};
    /** Nome por variante: {@code entity.isekai_mahou.quincy_npc.<archer|sentinel|elder>}. */
    private static final String NAME_KEY = "entity.isekai_mahou.quincy_npc.";
    private static final int YIELD_TICKS = 600;
    private static final int TALK_TICKS = 60;

    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(QuincyNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_YIELDED = SynchedEntityData.defineId(QuincyNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_AIMING = SynchedEntityData.defineId(QuincyNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TALK = SynchedEntityData.defineId(QuincyNpcEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.quincy_npc.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.quincy_npc.walk");
    private static final RawAnimation AIM = RawAnimation.begin().thenLoop("animation.quincy_npc.aim");
    private static final RawAnimation TALK = RawAnimation.begin().thenLoop("animation.quincy_npc.talk");
    private static final RawAnimation YIELD = RawAnimation.begin().thenPlayAndHold("animation.quincy_npc.yield");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("animation.quincy_npc.shoot");
    private static final RawAnimation BOW = RawAnimation.begin().thenPlay("animation.quincy_npc.bow");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private BlockPos home;
    private int yieldTicks;
    @Nullable
    private UUID yieldedTo;
    /** Já se rendeu a alguém que não é Quincy (paga uma vez por NPC). */
    private boolean paidTribute;
    /** O Auswählen já tirou o poder dele (some no próximo tick). */
    private boolean absorbed;

    public QuincyNpcEntity(EntityType<? extends QuincyNpcEntity> type, Level level) {
        super(type, level);
        this.xpReward = 8;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_YIELDED, false);
        builder.define(DATA_AIMING, false);
        builder.define(DATA_TALK, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ShootGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                e -> !(e instanceof net.minecraft.world.entity.monster.Creeper)));
    }

    // =====================================================================================
    //  Variantes e estado
    // =====================================================================================
    public int variant() {
        int v = entityData.get(DATA_VARIANT);
        return v >= 0 && v < VARIANTS ? v : 0;
    }

    public void setVariant(int v) {
        entityData.set(DATA_VARIANT, Math.max(0, Math.min(VARIANTS - 1, v)));
    }

    public String variantId() {
        return VARIANT_IDS[variant()];
    }

    public boolean yielded() {
        return entityData.get(DATA_YIELDED);
    }

    public boolean aiming() {
        return entityData.get(DATA_AIMING);
    }

    public boolean talking() {
        return entityData.get(DATA_TALK) > 0;
    }

    @Nullable
    public UUID yieldedTo() {
        return yieldedTo;
    }

    public boolean absorbed() {
        return absorbed;
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable(NAME_KEY + variantId());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data) {
        // do ovo/comando: variante sorteada (o Ancião é raro); da estrutura, a do molde
        if (reason != MobSpawnType.STRUCTURE) setVariant(random.nextFloat() < 0.15F ? 2 : random.nextInt(2));
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof QuincyNpcEntity || super.isAlliedTo(other);
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        if (absorbed) {
            QuincyFx.pillar(sl, position(), 2.6, 40);
            QuincyFx.sound(sl, position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
            discard();
            return;
        }
        if (home == null) {
            home = blockPosition();
            restrictTo(home, 14);
        }
        int talk = entityData.get(DATA_TALK);
        if (talk > 0) entityData.set(DATA_TALK, talk - 1);
        if (yielded()) {
            getNavigation().stop();
            setTarget(null);
            if (tickCount % 10 == 0) {
                sl.sendParticles(QuincyFx.REISHI_SMALL, getX(), getY() + 0.6, getZ(), 3, 0.3, 0.2, 0.3, 0.01);
            }
            if (--yieldTicks <= 0) recover(sl);
        }
        boolean aim = !yielded() && getTarget() != null && getTarget().isAlive() && distanceToSqr(getTarget()) < 24 * 24;
        if (aim != aiming()) entityData.set(DATA_AIMING, aim);
    }

    private void recover(ServerLevel sl) {
        entityData.set(DATA_YIELDED, false);
        yieldedTo = null;
        setHealth(getMaxHealth());
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 6, 0.3, 0.4, 0.3, 0.02);
    }

    // =====================================================================================
    //  Dano e rendição
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (yielded() || absorbed) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player && !player.isCreative() && level() instanceof ServerLevel sl) {
            float floor = getMaxHealth() * 0.3F;
            if (getHealth() - amount <= floor) {
                boolean ok = super.hurt(source, Math.max(0F, getHealth() - floor));
                yieldTo(sl, player);
                return ok;
            }
        }
        return super.hurt(source, amount);
    }

    private void yieldTo(ServerLevel sl, Player player) {
        entityData.set(DATA_YIELDED, true);
        yieldTicks = YIELD_TICKS;
        yieldedTo = player.getUUID();
        setTarget(null);
        setLastHurtByMob(null);
        getNavigation().stop();
        // os outros Quincy do acampamento também param de lutar com esse jogador
        for (QuincyNpcEntity other : sl.getEntitiesOfClass(QuincyNpcEntity.class, getBoundingBox().inflate(24))) {
            if (other.getTarget() == player) other.setTarget(null);
        }
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 12, 0.4, 0.3, 0.4, 0.02);
        QuincyFx.sound(sl, position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 0.8F);
        if (player instanceof ServerPlayer sp) {
            if (Quincy.isQuincy(sp)) {
                sp.displayClientMessage(Quincy.msg("npc_yield_quincy", getDisplayName()).withColor(Quincy.RGB), true);
            } else {
                sp.displayClientMessage(Quincy.msg("npc_yield_other", getDisplayName()).withStyle(ChatFormatting.GRAY), true);
                if (!paidTribute) {
                    paidTribute = true;
                    spawnAtLocation(new ItemStack(QuincyRegistries.SILVER_TUBE.get(), 1 + random.nextInt(2)));
                }
            }
        }
    }

    /** O Auswählen terminou: o poder foi tirado (o NPC some em luz no próximo tick). */
    public void markAbsorbed() {
        this.absorbed = true;
    }

    // =====================================================================================
    //  Interação
    // =====================================================================================
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.sidedSuccess(level().isClientSide);
        if (yielded()) {
            if (Quincy.isQuincy(sp)) {
                Auswahlen.beginNpc(sp, this);
            } else {
                sp.displayClientMessage(Quincy.msg("npc_not_quincy").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (getTarget() != null) return InteractionResult.PASS;
        talk(sp);
        return InteractionResult.SUCCESS;
    }

    private void talk(ServerPlayer sp) {
        entityData.set(DATA_TALK, TALK_TICKS);
        getLookControl().setLookAt(sp);
        triggerAnim("action", "bow");
        List<Dialogue.Page> pages = new ArrayList<>();
        String base = "dialogue.isekai_mahou.quincy.npc." + variantId();
        int line = random.nextInt(3);
        pages.add(Dialogue.Page.of(Component.translatable(base + "." + line)));
        if (Quincy.isQuincy(sp)) {
            pages.add(Dialogue.Page.of(Component.translatable("dialogue.isekai_mahou.quincy.npc.quincy_hint")));
        } else {
            pages.add(Dialogue.Page.of(Component.translatable("dialogue.isekai_mahou.quincy.npc.outsider")));
        }
        Dialogue.show(sp, "class_quincy.npc", getDisplayName(), Quincy.RGB, pages);
        playSound(SoundEvents.VILLAGER_AMBIENT, 0.4F, 1.4F);
    }

    // =====================================================================================
    //  Tiro
    // =====================================================================================
    void shootAt(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return;
        float dmg = variant() == 2 ? 5.0F : variant() == 1 ? 4.5F : 4.0F;
        SpiritArrowEntity a = SpiritArrowEntity.plain(sl, this, dmg);
        Vec3 from = new Vec3(getX(), getEyeY() - 0.15, getZ());
        a.setPos(from.x, from.y, from.z);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(from);
        a.shoot(to.x, to.y + to.horizontalDistance() * 0.02, to.z, 2.6F, 2.5F);
        sl.addFreshEntity(a);
        triggerAnim("action", "shoot");
        QuincyFx.bowFlash(sl, this);
        playSound(SoundEvents.ARROW_SHOOT, 0.8F, 1.5F + random.nextFloat() * 0.2F);
    }

    /** Mantém distância e atira a cada ~1,5 s enquanto vê o alvo. */
    static final class ShootGoal extends Goal {
        private final QuincyNpcEntity npc;
        private int cooldown = 20;
        private int strafe = 1;

        ShootGoal(QuincyNpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = npc.getTarget();
            return t != null && t.isAlive() && !npc.yielded();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = npc.getTarget();
            if (t == null) return;
            npc.getLookControl().setLookAt(t, 30F, 30F);
            double d = npc.distanceToSqr(t);
            boolean sight = npc.getSensing().hasLineOfSight(t);
            if (!sight || d > 18 * 18) {
                npc.getNavigation().moveTo(t, 0.9);
            } else if (d < 6 * 6) {
                Vec3 away = npc.position().subtract(t.position()).normalize().scale(4);
                npc.getNavigation().moveTo(npc.getX() + away.x, npc.getY(), npc.getZ() + away.z, 1.0);
            } else {
                npc.getNavigation().stop();
                if (npc.random.nextInt(40) == 0) strafe = -strafe;
                npc.getMoveControl().strafe(0, strafe * 0.4F);
            }
            if (--cooldown <= 0 && sight && d < 22 * 22) {
                npc.shootAt(t);
                cooldown = npc.variant() == 1 ? 24 : 32;
            }
        }
    }

    // =====================================================================================
    //  Sons, salvar, animações
    // =====================================================================================
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_BLOCK_BREAK;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
        if (home != null) {
            tag.putInt("HomeX", home.getX());
            tag.putInt("HomeY", home.getY());
            tag.putInt("HomeZ", home.getZ());
        }
        tag.putBoolean("PaidTribute", paidTribute);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("Variant"));
        if (tag.contains("HomeX")) {
            home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
            restrictTo(home, 14);
        }
        paidTribute = tag.getBoolean("PaidTribute");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<QuincyNpcEntity>(this, "main", 5, state -> {
            if (yielded()) return state.setAndContinue(YIELD);
            if (aiming()) return state.setAndContinue(AIM);
            if (state.isMoving()) return state.setAndContinue(WALK);
            if (talking()) return state.setAndContinue(TALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<QuincyNpcEntity>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("shoot", SHOOT)
                .triggerableAnim("bow", BOW));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
