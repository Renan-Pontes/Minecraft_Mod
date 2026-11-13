package com.renanpontes.isekaimahou.feature.nether_citadel.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.GBossEntity;
import com.renanpontes.isekaimahou.feature.nether_citadel.NcDemons;
import com.renanpontes.isekaimahou.feature.nether_citadel.NcRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
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
 * Lorde Demônio — senhor da Cidadela Demoníaca, no salão do trono. Quatro fases:
 * <ol>
 *   <li><b>Espada</b> (100–70%): Corte Amplo (cone), Estocada (linha + avanço) e salto marcado se fugirem;</li>
 *   <li><b>Chamas Infernais</b> (70%): Pilares de Fogo do Inferno debaixo de cada jogador e Anel de Chamas (o lugar
 *       seguro é colado nele ou longe);</li>
 *   <li><b>Legião</b> (45%): invoca diabretes; com 2+ diabretes vivos ele leva só metade do dano (mate-os primeiro);</li>
 *   <li><b>Forma Demoníaca</b> (20%): cresce, abre as asas, fica mais rápido e faz Chover Meteoros marcados.</li>
 * </ol>
 * Base comum (sono, intro, barra, arena selada, escala de grupo, anti-trapaça, recompensas): {@link GBossEntity}.
 */
public class DemonLord extends GBossEntity {
    private static final EntityDataAccessor<Boolean> DATA_DEMON = SynchedEntityData.defineId(DemonLord.class, EntityDataSerializers.BOOLEAN);
    public static final int COLOR = 0xD4242C;
    private static final int HELL_COLOR = 0xFF5A1E;
    private static final ResourceLocation DEMON_SCALE = IsekaiMahou.id("demon_lord_form_scale");
    private static final ResourceLocation DEMON_SPEED = IsekaiMahou.id("demon_lord_form_speed");
    private static final int MAX_IMPS = 4;

    private int cleaveCd = 20;
    private int thrustCd = 70;
    private int pillarCd = 60;
    private int ringCd = 160;
    private int summonCd = 200;
    private int meteorCd = 80;
    private int attackTimer;
    private float attackYaw;
    private int transformTicks;

    public DemonLord(EntityType<? extends DemonLord> type, Level level) {
        super(type, level);
        this.xpReward = 300;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(520.0, 14.0, 0.29, 16.0).add(Attributes.ATTACK_KNOCKBACK, 1.0);
    }

    @Override
    public String bossId() {
        return "demon_lord";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.RED;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.7F, 0.45F, 0.2F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("cleave", "thrust", "hellfire", "summon", "transform", "meteor");
    }

    @Override
    protected double arenaRadius() {
        return 24.0;
    }

    @Override
    protected double wakeRange() {
        return 18.0;
    }

    @Override
    protected int rewardXp() {
        return 450;
    }

    @Override
    public String musicTheme() {
        return "abyss_sovereign";
    }

    public boolean isDemonForm() {
        return entityData.get(DATA_DEMON);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DEMON, false);
    }

    // ------------------------------------------------------------------ Modelo

    @Override
    public List<String> toggledBones() {
        return List.of("left_wing", "right_wing");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return isDemonForm();
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
    public boolean isFoe(LivingEntity e) {
        return !NcDemons.isGarrison(e) && super.isFoe(e);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return NcDemons.isGarrison(other) || other.getTags().contains(MINION_TAG) || super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != this && (NcDemons.isGarrison(attacker) || attacker.getTags().contains(MINION_TAG))) return false;
        if (transformTicks > 0) return false;
        if (!level().isClientSide && phase() >= 2 && level() instanceof ServerLevel sl && countMinions(sl, 24) >= 2) {
            amount *= 0.5F;
            hintOnce(sl, "imp_shield");
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.6, getZ(), 6, 0.6, 0.8, 0.6, 0.01);
        }
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ Fases

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        attackTimer = 0;
        if (phase == 1) {
            pillarCd = 10;
        } else if (phase == 2) {
            summonImps(level);
        } else if (phase == 3) {
            transform(level);
        }
    }

    private void transform(ServerLevel sl) {
        transformTicks = 40;
        Telegraph.cancelAll(this);
        action("transform");
        entityData.set(DATA_DEMON, true);
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null && scale.getModifier(DEMON_SCALE) == null) {
            scale.addPermanentModifier(new AttributeModifier(DEMON_SCALE, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(DEMON_SPEED) == null) {
            speed.addPermanentModifier(new AttributeModifier(DEMON_SPEED, 0.18, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        BossKit.roar(this, SoundEvents.WITHER_SPAWN, 0.7F);
        BossKit.shake(sl, position(), 32, 1.4F, 30);
        BossKit.flashNear(sl, position(), 32, HELL_COLOR, 0.45F, 12);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5, getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.5, getZ(), 80, 1.2, 1.6, 1.2, 0.08);
        meteorCd = 50;
    }

    private void clearDemonForm() {
        entityData.set(DATA_DEMON, false);
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) scale.removeModifier(DEMON_SCALE);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(DEMON_SPEED);
        transformTicks = 0;
    }

    @Override
    protected void onReset(ServerLevel level) {
        clearDemonForm();
        attackTimer = 0;
    }

    @Override
    protected boolean isBusy() {
        return attackTimer > 0 || transformTicks > 0;
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        if (cleaveCd > 0) cleaveCd--;
        if (thrustCd > 0) thrustCd--;
        if (pillarCd > 0) pillarCd--;
        if (ringCd > 0) ringCd--;
        if (summonCd > 0) summonCd--;
        if (meteorCd > 0) meteorCd--;
        if (transformTicks > 0) {
            transformTicks--;
            getNavigation().stop();
            if (transformTicks % 4 == 0) {
                level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.2, getZ(), 12, 1.5, 0.1, 1.5, 0.05);
            }
            return;
        }
        if (attackTimer > 0) {
            attackTimer--;
            getNavigation().stop();
            holdYaw(attackYaw);
            return;
        }
        if (isDemonForm() && tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.FLAME, getX(), getY() + getBbHeight() * 0.6, getZ(), 2, 0.5, 0.8, 0.5, 0.01);
        }
        if (target == null) return;
        double d = distanceTo(target);
        boolean los = hasLineOfSight(target);
        int ph = phase();
        if (ph >= 3 && meteorCd <= 0) {
            meteorRain(level);
        } else if (ph >= 2 && summonCd <= 0 && countMinions(level, 32) < 2) {
            summonImps(level);
        } else if (ph >= 1 && pillarCd <= 0) {
            hellfirePillars(level);
        } else if (ph >= 1 && ringCd <= 0 && d < 10) {
            flameRing(level);
        } else if (d < 5.0 && cleaveCd <= 0) {
            cleave(level, target);
        } else if (d > 4 && d < 12 && thrustCd <= 0 && los) {
            thrust(level, target);
        } else {
            chase(target, isDemonForm() ? 1.2 : 1.0);
        }
    }

    private float meleeDamage() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** Corte Amplo: cone de 140° à frente. */
    private void cleave(ServerLevel level, LivingEntity target) {
        cleaveCd = phase() >= 3 ? 22 : 34;
        attackTimer = 14;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("cleave");
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
        float dmg = meleeDamage();
        float range = isDemonForm() ? 6.2F : 5.2F;
        Telegraph.delayed(level, Telegraph.Shape.cone(position(), attackYaw, 140F, range).warmup(14).color(Telegraph.DANGER).owner(this)
                .noRunes().castName(attackName("cleave")), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            e.igniteForSeconds(3.0F);
                            Statuses.apply(e, Statuses.Kind.BLEEDING, 1, 80, this, 1F);
                        }
                    }
                    playSound(SoundEvents.RAVAGER_ATTACK, 1.4F, 0.8F);
                });
    }

    /** Estocada: linha longa; no fim do aviso ele avança pela linha. */
    private void thrust(ServerLevel level, LivingEntity target) {
        thrustCd = (phase() >= 3 ? 60 : 90) + random.nextInt(30);
        attackTimer = 20;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("thrust");
        playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.6F, 0.5F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = position().add(dir.scale(0.8));
        Vec3 to = lineEnd(level, from, dir, 11.0);
        float dmg = scaled(20F);
        Telegraph.delayed(level, Telegraph.Shape.line(from, to, 1.8F).warmup(18).color(Telegraph.DANGER).owner(this)
                .castName(attackName("thrust")), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 1.0, 0.3, dir.z * 1.0));
                            e.hurtMarked = true;
                        }
                    }
                    Vec3 end = to.subtract(dir.scale(1.2));
                    if (level.noCollision(this, getBoundingBox().move(end.subtract(position())))) teleportTo(end.x, end.y, end.z);
                    for (int i = 0; i <= 12; i++) {
                        Vec3 p = from.add(to.subtract(from).scale(i / 12.0));
                        level.sendParticles(ParticleTypes.FLAME, p.x, p.y + 1.2, p.z, 2, 0.1, 0.1, 0.1, 0.01);
                    }
                    playSound(SoundEvents.TRIDENT_HIT, 1.6F, 0.5F);
                });
    }

    /** Pilares de Fogo do Inferno: um círculo sob cada jogador do grupo. */
    private void hellfirePillars(ServerLevel level) {
        pillarCd = (phase() >= 3 ? 110 : 160) + random.nextInt(40);
        attackTimer = 20;
        action("hellfire");
        announce("hellfire");
        playSound(SoundEvents.BLAZE_AMBIENT, 2.0F, 0.5F);
        float dmg = scaled(14F);
        boolean named = true;
        for (ServerPlayer p : BossKit.partyAround(this, 32)) {
            Vec3 at = groundAt(level, p.position());
            Telegraph.Shape s = Telegraph.Shape.circle(at, 2.4F).warmup(28).color(HELL_COLOR).owner(this);
            if (named) s.castName(attackName("hellfire")).element(Element.FIRE);
            named = false;
            Telegraph.delayed(level, s, hits -> {
                if (!isAlive()) return;
                for (LivingEntity e : hits) {
                    if (isFoe(e) && e.hurt(damageSources().indirectMagic(this, this), dmg)) {
                        e.igniteForSeconds(5.0F);
                        Statuses.apply(e, Statuses.Kind.BURNING, 2, 100, this, 1F);
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                        e.hurtMarked = true;
                    }
                }
                for (int y = 0; y < 8; y++) {
                    level.sendParticles(ParticleTypes.FLAME, at.x, at.y + y * 0.6, at.z, 8, 0.6, 0.2, 0.6, 0.02);
                }
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 6, 0.8, 0.1, 0.8, 0.0);
                level.playSound(null, BlockPos.containing(at), SoundEvents.BLAZE_SHOOT, getSoundSource(), 1.4F, 0.6F);
            });
        }
    }

    /** Anel de Chamas: queima tudo entre 3 e 10 blocos; colado nele (ou longe) é seguro. */
    private void flameRing(ServerLevel level) {
        ringCd = 200 + random.nextInt(60);
        attackTimer = 30;
        action("hellfire");
        playSound(SoundEvents.FIRECHARGE_USE, 2.0F, 0.5F);
        Vec3 c = position();
        float dmg = scaled(12F);
        Telegraph.safe(level, c, 2.6F, 32);
        Telegraph.delayed(level, Telegraph.Shape.ring(c, 3.0F, 10.0F).warmup(30).color(HELL_COLOR).owner(this)
                .castName(attackName("ring")).element(Element.FIRE), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().indirectMagic(this, this), dmg)) e.igniteForSeconds(4.0F);
                    }
                    for (int i = 0; i < 48; i++) {
                        double a = Math.PI * 2 * i / 48;
                        double r = 3.0 + random.nextDouble() * 7.0;
                        level.sendParticles(ParticleTypes.FLAME, c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, 2, 0.1, 0.4, 0.1, 0.03);
                    }
                    BossKit.shake(level, c, 18, 0.8F, 10);
                });
    }

    /** Legião: diabretes de fogo (ember_imp do bestiário; se não existir, cubos de magma). */
    private void summonImps(ServerLevel level) {
        summonCd = 360 + random.nextInt(80);
        attackTimer = 24;
        action("summon");
        announce("summon");
        BossKit.roar(this, SoundEvents.EVOKER_PREPARE_SUMMON, 0.8F);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("ember_imp")).orElse(EntityType.MAGMA_CUBE);
        int want = Math.min(MAX_IMPS - countMinions(level, 32), 1 + BossKit.partySize(this) + (phase() >= 3 ? 1 : 0));
        for (int i = 0; i < want; i++) {
            Vec3 spot = findGroundSpot(level, position(), 3.0, 7.0, type);
            if (spot == null) continue;
            Entity e = type.spawn(level, BlockPos.containing(spot), MobSpawnType.MOB_SUMMONED);
            if (e == null) continue;
            e.addTag(MINION_TAG);
            if (e instanceof Mob m) {
                m.setPersistenceRequired();
                LivingEntity t = getTarget();
                if (t != null) m.setTarget(t);
            }
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spot.x, spot.y + 0.5, spot.z, 20, 0.3, 0.6, 0.3, 0.04);
        }
        hintOnce(level, "imp_shield");
    }

    /** Chuva de Meteoros (forma demoníaca): vários círculos perto dos jogadores; caem em sequência. */
    private void meteorRain(ServerLevel level) {
        meteorCd = 140 + random.nextInt(40);
        attackTimer = 24;
        action("meteor");
        announce("meteor");
        BossKit.roar(this, SoundEvents.GHAST_SCREAM, 0.6F);
        float dmg = scaled(13F);
        int i = 0;
        for (ServerPlayer p : BossKit.partyAround(this, 32)) {
            for (int k = 0; k < 3; k++) {
                Vec3 at = groundAt(level, p.position().add((random.nextDouble() - 0.5) * 7, 0, (random.nextDouble() - 0.5) * 7));
                Telegraph.Shape s = Telegraph.Shape.circle(at, 2.8F).warmup(30 + (i++) * 6).color(HELL_COLOR).owner(this);
                if (i == 1) s.castName(attackName("meteor")).element(Element.FIRE);
                Telegraph.delayed(level, s, hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().indirectMagic(this, this), dmg)) e.igniteForSeconds(4.0F);
                    }
                    for (int y = 10; y >= 0; y--) {
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + y, at.z, 1, 0.1, 0.1, 0.1, 0.0);
                    }
                    level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
                    level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 24, 1.2, 0.2, 1.2, 0.05);
                    level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 1.0F, 0.9F);
                });
            }
        }
    }

    // ------------------------------------------------------------------ Loot

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropItem(NcRegistries.ADAMANTITE_INGOT.get(), 3, 6);
        dropItem(NcRegistries.DEMON_LORD_SIGIL.get(), 1, 2);
        dropEssence(Element.FIRE, 2, 4);
        dropEssence(Element.SHADOW, 2, 4);
        dropItem(Items.NETHERITE_SCRAP, 1, 2);
        if (random.nextFloat() < 0.5F) dropItem(NcRegistries.ANTIMAGIC_BLADE.get(), 1, 1);
        if (random.nextFloat() < 0.12F) dropById("relic_demon_horn", 1, 1);
        dropArcaneScrolls(2);
    }

    // ------------------------------------------------------------------ Salvar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DemonForm", isDemonForm());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_DEMON, tag.getBoolean("DemonForm"));
    }
}
