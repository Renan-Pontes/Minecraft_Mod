package com.renanpontes.isekaimahou.feature.world_events.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.world_events.WeRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Arauto Estelar — mini-chefe da Queda de Meteoro. Três truques, todos avisados no chão:
 * <ul>
 *   <li><b>Pulso</b> (círculo roxo em volta dele): empurra e fere quem ficar perto — afaste-se;</li>
 *   <li><b>Raio</b> (faixa roxa): linha reta comprida — saia de lado;</li>
 *   <li>com 2/3 e 1/3 da vida chama Larvas Estelares.</li>
 * </ul>
 * Não quebra blocos. Recompensa: XP para todos que lutaram + Núcleo Alienígena.
 */
public class AlienOverseer extends AlienMob {
    private static final int REWARD_XP = 140;
    private static final int COLOR = 0xB06CFF;
    private static final float PULSE_RADIUS = 6.5F;
    private static final float BEAM_RANGE = 20F;

    private final ServerBossEvent bar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_6);
    private int abilityCd = 80;
    private int casting;
    private int summons;

    public AlienOverseer(EntityType<? extends AlienOverseer> type, Level level) {
        super(type, level);
        this.xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected String modelId() {
        return "world_events_alien_overseer";
    }

    @Override
    protected List<String> actions() {
        return List.of("attack", "pulse", "beam", "summon");
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        action("attack");
        return super.doHurtTarget(target);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        bar.setProgress(getHealth() / getMaxHealth());
        LivingEntity target = getTarget();
        if (casting > 0) {
            getNavigation().stop();
            casting--;
            return;
        }
        if (abilityCd > 0) abilityCd--;
        if (target == null || !target.isAlive()) return;

        if (summons < 2 && getHealth() < getMaxHealth() * (summons == 0 ? 0.66F : 0.33F)) {
            summons++;
            summonLarvae(sl);
            return;
        }
        if (abilityCd > 0) return;
        double d2 = distanceToSqr(target);
        if (d2 < 7 * 7) pulse(sl);
        else if (d2 < BEAM_RANGE * BEAM_RANGE && hasLineOfSight(target)) beam(sl, target);
    }

    private void pulse(ServerLevel sl) {
        casting = 28;
        abilityCd = 110 + random.nextInt(40);
        action("pulse");
        playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 0.6F);
        Vec3 center = position();
        Telegraph.delayed(sl, Telegraph.Shape.circle(center, PULSE_RADIUS).warmup(26).color(COLOR).owner(this), hits -> {
            if (!isAlive()) return;
            sl.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 1.2, center.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 0.5, center.z, 60, 2.5, 0.4, 2.5, 0.1);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.8F, 1.3F);
            BossKit.shake(sl, center, 16, 0.8F, 12);
            float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.4F;
            for (LivingEntity e : hits) {
                if (e instanceof AlienMob) continue;
                if (!e.hurt(damageSources().mobAttack(this), dmg)) continue;
                Vec3 away = new Vec3(e.getX() - center.x, 0, e.getZ() - center.z);
                if (away.lengthSqr() > 0.001) {
                    away = away.normalize();
                    e.push(away.x * 1.1, 0.45, away.z * 1.1);
                    e.hurtMarked = true;
                }
            }
        });
    }

    private void beam(ServerLevel sl, LivingEntity target) {
        Vec3 from = position();
        Vec3 flat = new Vec3(target.getX() - from.x, 0, target.getZ() - from.z);
        if (flat.lengthSqr() < 0.01) return;
        Vec3 dir = flat.normalize();
        Vec3 to = from.add(dir.scale(BEAM_RANGE));
        casting = 24;
        abilityCd = 90 + random.nextInt(40);
        getLookControl().setLookAt(target, 60F, 60F);
        action("beam");
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.7F);
        Telegraph.delayed(sl, Telegraph.Shape.line(from, to, 1.8F).warmup(22).color(COLOR).owner(this), hits -> {
            if (!isAlive()) return;
            for (double d = 0.5; d < BEAM_RANGE; d += 0.5) {
                Vec3 p = from.add(dir.scale(d));
                sl.sendParticles(ParticleTypes.WITCH, p.x, p.y + 1.4, p.z, 3, 0.25, 0.25, 0.25, 0.01);
                sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1.4, p.z, 1, 0.1, 0.1, 0.1, 0.01);
            }
            playSound(SoundEvents.GUARDIAN_ATTACK, 1.0F, 0.7F);
            float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5F;
            for (LivingEntity e : hits) {
                if (!(e instanceof AlienMob)) e.hurt(damageSources().indirectMagic(this, this), dmg);
            }
        });
    }

    private void summonLarvae(ServerLevel sl) {
        casting = 20;
        action("summon");
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 0.6F);
        if (sl.getDifficulty() == Difficulty.PEACEFUL) return;
        for (int i = 0; i < 3; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            int x = (int) Math.floor(getX() + Math.cos(ang) * 3);
            int z = (int) Math.floor(getZ() + Math.sin(ang) * 3);
            BlockPos pos = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (Math.abs(pos.getY() - getY()) > 6) pos = blockPosition();
            AlienLarva larva = AlienMob.spawnInvader(sl, WeRegistries.ALIEN_LARVA.get(), pos);
            if (larva != null && getTarget() != null) larva.setTarget(getTarget());
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel) BossRewards.grant(this, REWARD_XP, 48);
    }

    // ------------------------------------------------------------------ barra de chefe
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("WeSummons", summons);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        summons = tag.getInt("WeSummons");
        if (hasCustomName()) bar.setName(getDisplayName());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ELDER_GUARDIAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ELDER_GUARDIAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ELDER_GUARDIAN_DEATH;
    }
}
