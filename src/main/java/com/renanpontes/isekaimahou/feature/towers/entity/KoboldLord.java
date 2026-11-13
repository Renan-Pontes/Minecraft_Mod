package com.renanpontes.isekaimahou.feature.towers.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.GBossEntity;
import com.renanpontes.isekaimahou.feature.towers.TwRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Lorde Kobold do Pináculo — guardião das Torres de Desafio (homenagem ao chefe do 1º andar de SAO). Três fases:
 * <ol>
 *   <li><b>Machado e Broquel</b> (100–60%): Machadada (cone), Investida de Broquel (linha + atordoa) e Sentinelas
 *       (kobolds) que o protegem: com 2+ vivas ele leva só metade do dano;</li>
 *   <li><b>Fúria do Rei</b> (60%): novas sentinelas e Rodopio (círculo em volta dele);</li>
 *   <li><b>A Lâmina Escondida</b> (25%): joga fora machado e broquel e saca uma nodachi — mais rápido, Corte Triplo
 *       (três cones em sequência), Iai Relâmpago (linha longa + avanço, sangra) e Lua Ascendente (círculo que lança para cima).</li>
 * </ol>
 * Base comum (sono, intro, barra, arena selada, escala de grupo, anti-trapaça, recompensas): {@link GBossEntity}.
 */
public class KoboldLord extends GBossEntity {
    private static final EntityDataAccessor<Boolean> DATA_BLADE = SynchedEntityData.defineId(KoboldLord.class, EntityDataSerializers.BOOLEAN);
    public static final int COLOR = 0xD9A632;
    private static final int BLADE_COLOR = 0x9FE8FF;
    private static final ResourceLocation BLADE_SPEED = IsekaiMahou.id("tower_kobold_lord_blade_speed");
    private static final int MAX_SENTINELS = 4;

    private int cleaveCd = 20;
    private int bashCd = 80;
    private int spinCd = 120;
    private int summonCd = 300;
    private int slashCd = 30;
    private int lungeCd = 80;
    private int risingCd = 140;
    private int attackTimer;
    private float attackYaw;
    private int drawTicks;

    public KoboldLord(EntityType<? extends KoboldLord> type, Level level) {
        super(type, level);
        this.xpReward = 220;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(380.0, 12.0, 0.28, 13.0).add(Attributes.ATTACK_KNOCKBACK, 0.8);
    }

    @Override
    public String bossId() {
        return "tower_kobold_lord";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.YELLOW;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.6F, 0.25F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("cleave", "bash", "spin", "summon", "draw", "slash3", "lunge");
    }

    @Override
    protected double arenaRadius() {
        return 24.0;
    }

    @Override
    protected double wakeRange() {
        return 14.0;
    }

    @Override
    protected int rewardXp() {
        return 380;
    }

    @Override
    public String musicTheme() {
        return "generic_boss";
    }

    public boolean hasBlade() {
        return entityData.get(DATA_BLADE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BLADE, false);
    }

    // ------------------------------------------------------------------ Modelo

    @Override
    public List<String> toggledBones() {
        return List.of("axe", "buckler", "nodachi");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return "nodachi".equals(bone) == hasBlade();
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (isDeadOrDying()) return hold("death");
        if (introTicks() > 0) return play("intro");
        if (!isAwake()) return loop("sleep");
        return loop(moving ? "walk" : "idle");
    }

    // ------------------------------------------------------------------ Aliados e dano

    @Override
    public boolean isAlliedTo(Entity other) {
        return other.getTags().contains(MINION_TAG) || super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != this && attacker.getTags().contains(MINION_TAG)) return false;
        if (drawTicks > 0) return false;
        if (!level().isClientSide && isAwake() && level() instanceof ServerLevel sl && countMinions(sl, 24) >= 2) {
            amount *= 0.5F;
            hintOnce(sl, "sentinels");
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.8, getZ(), 6, 0.6, 0.8, 0.6, 0.05);
        }
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ Fases

    @Override
    protected void onIntroEnd(ServerLevel level) {
        summonSentinels(level);
    }

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        attackTimer = 0;
        if (phase == 1) {
            summonSentinels(level);
            spinCd = 30;
        } else if (phase == 2) {
            drawBlade(level);
        }
    }

    /** A virada: joga fora machado e broquel e saca a nodachi escondida. */
    private void drawBlade(ServerLevel sl) {
        drawTicks = 30;
        Telegraph.cancelAll(this);
        action("draw");
        entityData.set(DATA_BLADE, true);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(BLADE_SPEED) == null) {
            speed.addPermanentModifier(new AttributeModifier(BLADE_SPEED, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        BossKit.roar(this, SoundEvents.RAVAGER_ROAR, 1.2F);
        BossKit.shake(sl, position(), 24, 0.8F, 16);
        BossKit.flashNear(sl, position(), 24, BLADE_COLOR, 0.35F, 10);
        sl.sendParticles(ParticleTypes.ITEM_SNOWBALL, getX(), getY() + 2, getZ(), 30, 0.8, 0.8, 0.8, 0.1);
        playSound(SoundEvents.ANVIL_LAND, 1.0F, 0.6F);
        hintOnce(sl, "nodachi");
        slashCd = 20;
        lungeCd = 50;
        risingCd = 90;
    }

    private void sheathe() {
        entityData.set(DATA_BLADE, false);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(BLADE_SPEED);
        drawTicks = 0;
    }

    @Override
    protected void onReset(ServerLevel level) {
        sheathe();
        attackTimer = 0;
    }

    @Override
    protected boolean isBusy() {
        return attackTimer > 0 || drawTicks > 0;
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        if (cleaveCd > 0) cleaveCd--;
        if (bashCd > 0) bashCd--;
        if (spinCd > 0) spinCd--;
        if (summonCd > 0) summonCd--;
        if (slashCd > 0) slashCd--;
        if (lungeCd > 0) lungeCd--;
        if (risingCd > 0) risingCd--;
        if (drawTicks > 0) {
            drawTicks--;
            getNavigation().stop();
            if (drawTicks % 5 == 0) level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.2, getZ(), 6, 0.4, 0.6, 0.4, 0.05);
            return;
        }
        if (attackTimer > 0) {
            attackTimer--;
            getNavigation().stop();
            holdYaw(attackYaw);
            return;
        }
        if (target == null) return;
        double d = distanceTo(target);
        boolean los = hasLineOfSight(target);
        if (hasBlade()) {
            if (risingCd <= 0 && d < 5.5) {
                risingMoon(level);
            } else if (lungeCd <= 0 && d > 4 && d < 13 && los) {
                lunge(level, target);
            } else if (slashCd <= 0 && d < 6.0) {
                tripleSlash(level, target);
            } else {
                chase(target, 1.15);
            }
            return;
        }
        if (phase() >= 1 && summonCd <= 0 && countMinions(level, 24) < 2) {
            summonSentinels(level);
        } else if (phase() >= 1 && spinCd <= 0 && d < 5.0) {
            spin(level);
        } else if (bashCd <= 0 && d > 3 && d < 9 && los) {
            bash(level, target);
        } else if (cleaveCd <= 0 && d < 4.8) {
            cleave(level, target);
        } else {
            chase(target, 1.0);
        }
    }

    private float melee() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** Machadada: cone de 120° à frente. */
    private void cleave(ServerLevel level, LivingEntity target) {
        cleaveCd = phase() >= 1 ? 26 : 34;
        attackTimer = 14;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("cleave");
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
        float dmg = melee();
        Telegraph.delayed(level, Telegraph.Shape.cone(position(), attackYaw, 120F, 5.0F).warmup(14).color(Telegraph.DANGER).owner(this)
                .noRunes().castName(attackName("cleave")), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e)) e.hurt(damageSources().mobAttack(this), dmg);
                    }
                    playSound(SoundEvents.ANVIL_LAND, 0.6F, 1.4F);
                    BossKit.shake(level, position(), 10, 0.4F, 6);
                });
    }

    /** Investida de Broquel: linha de 7 blocos; ao fim do aviso ele avança, empurra e atordoa. */
    private void bash(ServerLevel level, LivingEntity target) {
        bashCd = 90 + random.nextInt(30);
        attackTimer = 20;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("bash");
        playSound(SoundEvents.SHIELD_BLOCK, 1.4F, 0.6F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = position().add(dir.scale(0.8));
        Vec3 to = lineEnd(level, from, dir, 8.0);
        float dmg = scaled(15F);
        Telegraph.delayed(level, Telegraph.Shape.line(from, to, 1.8F).warmup(16).color(Telegraph.WARNING).owner(this)
                .castName(attackName("bash")), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 1.3, 0.4, dir.z * 1.3));
                            e.hurtMarked = true;
                            e.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 20, 0), this);
                        }
                    }
                    Vec3 end = to.subtract(dir.scale(1.2));
                    if (level.noCollision(this, getBoundingBox().move(end.subtract(position())))) teleportTo(end.x, end.y, end.z);
                    level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 12, 0.6, 0.1, 0.6, 0.05);
                    playSound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1.4F, 0.7F);
                });
    }

    /** Rodopio: círculo de 4,5 blocos em volta dele; empurra para fora. */
    private void spin(ServerLevel level) {
        spinCd = 160 + random.nextInt(40);
        attackTimer = 24;
        action("spin");
        announce("spin");
        Vec3 c = position();
        float dmg = scaled(13F);
        Telegraph.delayed(level, Telegraph.Shape.circle(c, 4.5F).warmup(22).color(Telegraph.DANGER).owner(this)
                .castName(attackName("spin")), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (!isFoe(e) || !e.hurt(damageSources().mobAttack(this), dmg)) continue;
                        Vec3 away = e.position().subtract(c).multiply(1, 0, 1);
                        if (away.lengthSqr() > 1.0E-4) away = away.normalize();
                        e.setDeltaMovement(e.getDeltaMovement().add(away.x * 1.1, 0.35, away.z * 1.1));
                        e.hurtMarked = true;
                    }
                    for (int i = 0; i < 32; i++) {
                        double a = Math.PI * 2 * i / 32;
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + Math.cos(a) * 3.0, c.y + 1.2, c.z + Math.sin(a) * 3.0, 1, 0, 0, 0, 0);
                    }
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
                });
    }

    /** Sentinelas: kobolds do bestiário (ou zumbis, se o módulo de criaturas não existir). */
    private void summonSentinels(ServerLevel level) {
        summonCd = 420 + random.nextInt(80);
        attackTimer = 24;
        action("summon");
        announce("summon");
        BossKit.roar(this, SoundEvents.WOLF_HOWL, 0.6F);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("kobold")).orElse(EntityType.ZOMBIE);
        int want = Math.min(MAX_SENTINELS - countMinions(level, 32), 1 + BossKit.partySize(this));
        for (int i = 0; i < want; i++) {
            Vec3 spot = findGroundSpot(level, position(), 3.0, 7.0, type);
            if (spot == null) continue;
            Entity e = type.spawn(level, BlockPos.containing(spot), MobSpawnType.MOB_SUMMONED);
            if (e == null) continue;
            e.addTag(MINION_TAG);
            if (e instanceof Mob m) {
                m.setPersistenceRequired();
                AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) {
                    hp.addPermanentModifier(new AttributeModifier(IsekaiMahou.id("tower_sentinel_health"), 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                    m.setHealth(m.getMaxHealth());
                }
                LivingEntity t = getTarget();
                if (t != null) m.setTarget(t);
            }
            level.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.5, spot.z, 16, 0.3, 0.5, 0.3, 0.04);
        }
        hintOnce(level, "sentinels");
    }

    /** Corte Triplo: três cones em sequência (esquerda, direita, frente). */
    private void tripleSlash(ServerLevel level, LivingEntity target) {
        slashCd = 50 + random.nextInt(20);
        attackTimer = 30;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("slash3");
        float dmg = scaled(11F);
        float[] offsets = {-35F, 35F, 0F};
        for (int i = 0; i < 3; i++) {
            float yaw = attackYaw + offsets[i];
            Telegraph.Shape s = Telegraph.Shape.cone(position(), yaw, 100F, 6.0F).warmup(10 + i * 8).color(BLADE_COLOR).owner(this).noRunes();
            if (i == 0) s.castName(attackName("slash3"));
            Telegraph.delayed(level, s, hits -> {
                if (!isAlive()) return;
                for (LivingEntity e : hits) {
                    if (isFoe(e)) e.hurt(damageSources().mobAttack(this), dmg);
                }
                Vec3 dir = Vec3.directionFromRotation(0, yaw);
                for (int k = 1; k <= 5; k++) {
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + dir.x * k, getY() + 1.3, getZ() + dir.z * k, 1, 0, 0, 0, 0);
                }
                playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 1.2F);
            });
        }
    }

    /** Iai Relâmpago: linha longa; ao fim do aviso ele atravessa a linha cortando (sangramento). */
    private void lunge(ServerLevel level, LivingEntity target) {
        lungeCd = 90 + random.nextInt(30);
        attackTimer = 20;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("lunge");
        playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.6F, 0.6F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = position().add(dir.scale(0.8));
        Vec3 to = lineEnd(level, from, dir, 12.0);
        float dmg = scaled(18F);
        Telegraph.delayed(level, Telegraph.Shape.line(from, to, 1.4F).warmup(14).color(BLADE_COLOR).owner(this)
                .castName(attackName("lunge")).element(Element.WIND), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            Statuses.apply(e, Statuses.Kind.BLEEDING, 2, 100, this, 1F);
                        }
                    }
                    Vec3 end = to.subtract(dir.scale(1.2));
                    if (level.noCollision(this, getBoundingBox().move(end.subtract(position())))) teleportTo(end.x, end.y, end.z);
                    for (int i = 0; i <= 12; i++) {
                        Vec3 p = from.add(to.subtract(from).scale(i / 12.0));
                        level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1.3, p.z, 2, 0.1, 0.1, 0.1, 0.01);
                    }
                    playSound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.6F, 1.2F);
                });
    }

    /** Lua Ascendente: círculo de 5,5 blocos em volta dele que lança para cima. */
    private void risingMoon(ServerLevel level) {
        risingCd = 150 + random.nextInt(40);
        attackTimer = 24;
        action("slash3");
        Vec3 c = position();
        float dmg = scaled(12F);
        Telegraph.delayed(level, Telegraph.Shape.circle(c, 5.5F).warmup(18).color(BLADE_COLOR).owner(this)
                .castName(attackName("rising")), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.95, 0));
                            e.hurtMarked = true;
                        }
                    }
                    for (int i = 0; i < 40; i++) {
                        double a = Math.PI * 2 * i / 40;
                        level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * 4.5, c.y + 0.3, c.z + Math.sin(a) * 4.5, 1, 0, 0.4, 0, 0.05);
                    }
                    BossKit.shake(level, c, 16, 0.6F, 8);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 1.5F);
                });
    }

    // ------------------------------------------------------------------ Loot

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropItem(Items.DIAMOND, 2, 4);
        dropItem(Items.EMERALD, 3, 7);
        dropItem(TwRegistries.TELEPORT_CRYSTAL.get(), 2, 3);
        dropEssence(Element.EARTH, 1, 3);
        dropEssence(Element.WIND, 1, 2);
        if (random.nextFloat() < 0.4F) dropItem(random.nextBoolean() ? TwRegistries.TWIN_NOIR.get() : TwRegistries.TWIN_AZURE.get(), 1, 1);
        dropArcaneScrolls(1);
    }

    // ------------------------------------------------------------------ Salvar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Blade", hasBlade());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_BLADE, tag.getBoolean("Blade"));
    }
}
