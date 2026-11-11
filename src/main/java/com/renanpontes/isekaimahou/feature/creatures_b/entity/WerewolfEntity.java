package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Lobisomem: só aparece em noites de LUA CHEIA, na superfície. Muito rápido, salta no alvo e UIVA chamando os
 * lobos selvagens da região (e, se não houver, surgem lobos da mata). Ao amanhecer volta a ser humano e some.
 */
public class WerewolfEntity extends CbMonster {
    private int howlCooldown = 0;
    /** Nasceu naturalmente na lua cheia: some ao amanhecer (os de ovo/comando ficam). */
    private boolean moonbound;

    public WerewolfEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 45.0)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    /** Lua cheia + noite + céu aberto. */
    public static boolean checkSpawn(EntityType<WerewolfEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                     RandomSource random) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
        }
        ServerLevel sl = level.getLevel();
        return sl.isNight() && sl.getMoonPhase() == 0 && level.canSeeSky(pos)
                && Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        SpawnGroupData data) {
        moonbound = reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Moonbound", moonbound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        moonbound = tag.getBoolean("Moonbound");
    }

    @Override
    public CbFaction faction() {
        return CbFaction.BEAST;
    }

    @Override
    protected String animId() {
        return "werewolf";
    }

    @Override
    protected String[] actionAnims() {
        return new String[] {"attack", "howl"};
    }

    @Override
    protected float voicePitch() {
        return 0.7F;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof Wolf w && !w.isTame()) return true;
        return super.isAlliedTo(other);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, busyGoal());
        goalSelector.addGoal(1, new HowlGoal());
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Sheep.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (howlCooldown > 0) howlCooldown--;
        // Amanhecer: volta à forma humana e foge (some), a não ser que esteja preso por nome/ovo.
        if (moonbound && tickCount % 20 == 0 && level().isDay() && !isPersistenceRequired() && random.nextInt(8) == 0
                && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 25, 0.4, 0.8, 0.4, 0.02);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_WHINE, SoundSource.HOSTILE, 1.2F, 0.6F);
            discard();
        }
    }

    /** Uivo: para, uiva alto e chama os lobos selvagens contra o alvo. */
    private class HowlGoal extends Goal {
        private int t;

        HowlGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return howlCooldown <= 0 && !isBusy() && target instanceof Player && target.isAlive()
                    && distanceToSqr(target) < 24 * 24 && onGround();
        }

        @Override
        public boolean canContinueToUse() {
            return t < 40 && isAlive();
        }

        @Override
        public void start() {
            t = 0;
            getNavigation().stop();
            playAction("howl");
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            t++;
            if (t == 8) howl();
        }

        @Override
        public void stop() {
            howlCooldown = 600;
        }
    }

    private void howl() {
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity target = getTarget();
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 4.0F, 0.65F);
        sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getEyeY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        if (target == null) return;
        List<Wolf> wolves = sl.getEntitiesOfClass(Wolf.class, getBoundingBox().inflate(32.0), w -> w.isAlive() && !w.isTame());
        for (Wolf w : wolves) anger(w, target);
        // Sem matilha por perto: lobos da mata respondem ao chamado.
        int need = Math.max(0, 2 - wolves.size());
        for (int i = 0; i < need; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            BlockPos pos = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(getX() + Math.cos(a) * 8, getY(), getZ() + Math.sin(a) * 8));
            if (Math.abs(pos.getY() - getY()) > 6) continue;
            Wolf w = EntityType.WOLF.spawn(sl, pos, MobSpawnType.MOB_SUMMONED);
            if (w == null) continue;
            anger(w, target);
            sl.sendParticles(ParticleTypes.POOF, w.getX(), w.getY() + 0.5, w.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private static void anger(Wolf w, LivingEntity target) {
        w.setTarget(target);
        w.setPersistentAngerTarget(target.getUUID());
        w.startPersistentAngerTimer();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOLF_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    protected void playAttackSound() {
        playSound(SoundEvents.WOLF_GROWL, 1.0F, 0.6F);
    }
}
