package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.boss.BossCurses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_c.BossesCRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Lich (Cripta Esquecida). Morto-vivo feiticeiro ligado a um <b>filactério</b> que aparece perto dele na
 * apresentação. Enquanto o filactério existir ele se cura aos poucos e, ao "morrer", desaba e se recompõe junto
 * dele. Quebre o filactério para que ele morra de vez (se estiver desabado nesse momento, vira pó na hora).
 * <ul>
 *   <li><b>Raios de Alma</b> teleguiados (murcham);</li>
 *   <li><b>Erguer os Mortos</b>: círculos de invocação marcam o chão e, 1 s depois, zumbis e esqueletos saem dele;</li>
 *   <li><b>Drenar Vida</b>: canaliza um feixe que tira vida do alvo e cura o Lich (quebre a linha de visão);</li>
 *   <li><b>Círculo Necrótico</b>: um anel marcado em volta do alvo suga a vida de quem estiver NELE e cura o Lich —
 *       fique no miolo seguro ou saia do anel (na fase 2, maior e também em outro jogador);</li>
 *   <li><b>Lamento</b> (fase 2): grito com área marcada que enfraquece e deixa lento quem estiver perto.</li>
 * </ul>
 * Deixa o Filactério (item do ritual do Necromante) se o módulo existir.
 */
public class Lich extends ClassBossEntity {
    private static final EntityDataAccessor<Boolean> DATA_REFORMING = SynchedEntityData.defineId(Lich.class, EntityDataSerializers.BOOLEAN);
    private static final int COLOR = 0x5FD8E8;
    private static final int MAX_MINIONS = 6;
    private static final int REFORM_TICKS = 100;
    private static final int WAIL_WARMUP = 16;
    private static final float WAIL_RADIUS = 7.0F;
    private static final int RAISE_DELAY = 20;
    private static final int NECRO_CAST = 20;
    private static final int NECRO_WARMUP = 30;
    private static final int NECRO_HOLD = 80;
    private static final float NECRO_INNER = 2.2F;
    private static final int NECRO_COLOR = 0x4FE0B0;

    @Nullable
    private UUID phylacteryId;
    @Nullable
    private Vec3 phylacteryPos;
    private boolean phylacterySpawned;
    private boolean phylacteryBroken;
    private boolean forceDeath;
    private int reformTicks;
    private int reforms;
    private int defendCd;

    private int boltCd = 30;
    private int raiseCd = 100;
    private int drainCd = 140;
    private int wailCd = 80;
    private int blinkCd;
    private int necroCd = 160;
    private int necroTimer;
    private int castTimer;
    private int wailTimer;
    /** Marca do Lamento em preparo (some sem "acertar" se ele desabar antes do grito). */
    private int wailMark = -1;
    private int drainTicks;
    @Nullable
    private LivingEntity drainTarget;

    public Lich(EntityType<? extends Lich> type, Level level) {
        super(type, level);
        this.xpReward = 200;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(220.0, 8.0, 0.26, 5.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7);
    }

    @Override
    public String bossId() {
        return "lich";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.BLUE;
    }

    @Override
    protected void configureBar(ServerBossEvent bossBar) {
        bossBar.setDarkenScreen(true);
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.5F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("cast", "summon", "drain", "wail", "reform", "necrotic");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_REFORMING, false);
    }

    public boolean isReforming() {
        return entityData.get(DATA_REFORMING);
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (isReforming()) return hold("collapse");
        return super.mainAnimation(moving);
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isReforming();
    }

    @Override
    protected boolean canChangePhase() {
        return !isReforming();
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.WITHER) && super.canBeAffected(effect);
    }

    // ------------------------------------------------------------------ Filactério

    private boolean phylacteryActive() {
        return phylacterySpawned && !phylacteryBroken;
    }

    @Nullable
    private LichPhylactery phylactery(ServerLevel level) {
        if (phylacteryId == null || phylacteryBroken) return null;
        return level.getEntity(phylacteryId) instanceof LichPhylactery p && p.isAlive() ? p : null;
    }

    private void spawnPhylactery(ServerLevel level) {
        phylacterySpawned = true;
        EntityType<LichPhylactery> type = BossesCRegistry.LICH_PHYLACTERY.get();
        Vec3 at = findGroundSpot(level, position(), 6.0, 10.0, type);
        if (at == null) at = findGroundSpot(level, position(), 2.0, 4.0, type);
        if (at == null) at = position();
        LichPhylactery p = type.create(level);
        if (p == null) {
            phylacteryBroken = true;
            return;
        }
        p.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0F);
        p.setLich(this);
        level.addFreshEntity(p);
        phylacteryId = p.getUUID();
        phylacteryPos = at;
        BossFx.line(level, position().add(0, 1.6, 0), at.add(0, 0.6, 0), ParticleTypes.SOUL, 3);
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.6, at.z, 20, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, getSoundSource(), 1.5F, 0.6F);
    }

    /** Chamado pelo filactério quando um jogador o acerta: o Lich manda mortos defendê-lo. */
    public void onPhylacteryAttacked(Player attacker) {
        if (!(level() instanceof ServerLevel level) || isReforming()) return;
        if (!isAwake()) {
            // Mexeram no filactério com ele adormecido (depois de uma luta abandonada): ele acorda na hora.
            startIntro(level);
            setTarget(attacker);
            return;
        }
        if (defendCd > 0) return;
        defendCd = 300;
        announce("phylactery_hurt");
        setTarget(attacker);
        LichPhylactery p = phylactery(level);
        if (p != null) raiseAt(level, p.position(), attacker, 2, false);
    }

    /** Chamado quando o filactério é destruído. */
    public void onPhylacteryDestroyed(@Nullable DamageSource source) {
        if (phylacteryBroken || !(level() instanceof ServerLevel level)) return;
        phylacteryBroken = true;
        phylacteryId = null;
        announce("phylactery_broken");
        releaseSouls(level);
        playSound(SoundEvents.WITHER_HURT, 2.0F, 0.5F);
        if (!isAwake()) {
            // Quebraram o filactério com ele adormecido: ele acorda furioso, já mortal.
            startIntro(level);
            return;
        }
        Cutscenes.playNear(this, 40, "boss_phase", getDisplayName(),
                Component.translatable("boss.isekai_mahou.lich.phylactery_broken"), COLOR, 45, null);
        if (isReforming()) {
            // Desabado e sem filactério: vira pó de vez. O crédito vai para quem quebrou o filactério.
            entityData.set(DATA_REFORMING, false);
            forceDeath = true;
            if (source != null && source.getEntity() instanceof Player p) setLastHurtByPlayer(p);
            invulnerableTime = 0;
            hurt(source != null ? source : damageSources().magic(), Float.MAX_VALUE);
            // Se algo barrou o golpe (imunidade, evento de outro mod), garante a morte assim mesmo.
            if (isAlive() && !isDeadOrDying()) hurt(damageSources().genericKill(), Float.MAX_VALUE);
            return;
        }
        forcePhase(1);
    }

    private void checkPhylactery(ServerLevel level) {
        if (!phylacteryActive() || phylacteryPos == null) return;
        LichPhylactery p = phylactery(level);
        // Só conclui que sumiu se as entidades daquele chunk já estiverem carregadas.
        if (p == null && level.areEntitiesLoaded(ChunkPos.asLong(BlockPos.containing(phylacteryPos)))) onPhylacteryDestroyed(null);
    }

    /** Liga visual (fios de alma) e cura lenta vinda do filactério. */
    private void tether(ServerLevel level) {
        LichPhylactery p = phylactery(level);
        if (p == null || p.distanceToSqr(this) > 32 * 32) return;
        if (tickCount % 6 == 0) {
            Vec3 from = p.position().add(0, 0.7, 0);
            Vec3 to = position().add(0, getBbHeight() * 0.6, 0);
            double t = (tickCount % 30) / 30.0;
            Vec3 at = from.add(to.subtract(from).scale(t));
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0);
            level.sendParticles(BossFx.dust(COLOR, 0.8F), at.x, at.y, at.z, 2, 0.08, 0.08, 0.08, 0);
        }
        if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) heal(1.5F);
    }

    // ------------------------------------------------------------------ Morte e recomposição

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isReforming() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !forceDeath && phylacteryActive() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(1.0F);
            beginCollapse((ServerLevel) level());
            return;
        }
        super.die(source);
    }

    private void beginCollapse(ServerLevel level) {
        entityData.set(DATA_REFORMING, true);
        reformTicks = REFORM_TICKS;
        castTimer = wailTimer = drainTicks = necroTimer = 0;
        drainTarget = null;
        // O grito em preparo morre com ele: a marca some sem piscar (nada acontece, e o chão não engana).
        if (wailMark >= 0) Telegraph.cancel(level, wailMark);
        wailMark = -1;
        getNavigation().stop();
        announce("collapse");
        playSound(SoundEvents.SKELETON_DEATH, 2.0F, 0.6F);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE)), getX(), getY() + 1.2, getZ(),
                30, 0.4, 0.6, 0.4, 0.12);
        level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.2, getZ(), 30, 0.4, 0.6, 0.4, 0.05);
    }

    private void tickReform(ServerLevel level) {
        LichPhylactery p = phylactery(level);
        if (p != null && tickCount % 3 == 0) {
            BossFx.line(level, p.position().add(0, 0.7, 0), position().add(0, 0.6, 0), BossFx.dust(COLOR, 0.9F), 1.2);
        }
        if (tickCount % 5 == 0) level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.4, getZ(), 3, 0.4, 0.2, 0.4, 0.02);
        if (--reformTicks > 0) return;
        Vec3 at = p != null ? findGroundSpot(level, p.position(), 1.5, 3.0, getType(), p.position().add(0, 1.0, 0)) : null;
        if (at != null) teleportTo(at.x, at.y, at.z);
        boolean firstPhaseShift = phase() == 0;
        setHealth(getMaxHealth() * Math.max(0.25F, 0.5F - 0.1F * reforms));
        reforms++;
        entityData.set(DATA_REFORMING, false);
        action("reform");
        playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0F, 0.5F);
        playSound(SoundEvents.SKELETON_AMBIENT, 2.0F, 0.5F);
        BossFx.sphere(level, position().add(0, 1.2, 0), 1.5, 50, BossFx.dust(COLOR, 1.4F));
        level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1.2, getZ(), 25, 0.5, 0.8, 0.5, 0.06);
        // A primeira recomposição já cai abaixo de 50%: a mudança de fase faz a fala; nas outras ele mesmo fala.
        if (!firstPhaseShift) announce("reform");
        blinkCd = 40;
        raiseCd = Math.min(raiseCd, 20);
    }

    // ------------------------------------------------------------------ Tick e combate

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isDeadOrDying()) return;
        if (defendCd > 0) defendCd--;
        if (isReforming()) {
            tickReform(level);
            return;
        }
        if (isAwake()) tether(level);
        if (tickCount % 40 == 0) checkPhylactery(level);
    }

    @Override
    protected boolean isBusy() {
        return drainTicks > 0 || castTimer > 0 || wailTimer > 0 || necroTimer > 0 || isReforming();
    }

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        boolean p2 = phase() >= 1;
        if (boltCd > 0) boltCd--;
        if (raiseCd > 0) raiseCd--;
        if (drainCd > 0) drainCd--;
        if (wailCd > 0) wailCd--;
        if (blinkCd > 0) blinkCd--;
        if (necroCd > 0) necroCd--;

        if (drainTicks > 0) {
            tickDrain(level, p2);
            return;
        }
        if (castTimer > 0) {
            getNavigation().stop();
            if (target != null) faceTarget(target);
            Vec3 staff = staffPos();
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, staff.x, staff.y, staff.z, 2, 0.1, 0.1, 0.1, 0.01);
            if (--castTimer == 0 && target != null) shootSouls(level, target, p2);
            return;
        }
        if (wailTimer > 0) {
            getNavigation().stop();
            wailTimer--;
            return;
        }
        if (necroTimer > 0) {
            getNavigation().stop();
            necroTimer--;
            Vec3 staff = staffPos();
            level.sendParticles(ParticleTypes.SCULK_SOUL, staff.x, staff.y, staff.z, 2, 0.2, 0.2, 0.2, 0.02);
            if (necroTimer % 4 == 0) BossFx.ring(level, position().add(0, 0.1, 0), 1.2, 12, BossFx.dust(NECRO_COLOR, 1.0F), 0);
            return;
        }
        if (target == null) return;

        double dist = distanceTo(target);
        if (blinkCd <= 0 && dist < 3.5) {
            Vec3 from = position();
            if (blinkAround(target, 8.0, 12.0)) {
                level.sendParticles(ParticleTypes.SOUL, from.x, from.y + 1.2, from.z, 25, 0.4, 0.8, 0.4, 0.05);
                level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1.2, getZ(), 15, 0.4, 0.8, 0.4, 0.05);
                playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.6F);
            }
            blinkCd = p2 ? 90 : 130;
            return;
        }
        if (raiseCd <= 0 && countMinions(level, 32) < MAX_MINIONS) {
            action("summon");
            announce("summon");
            raiseAt(level, target.position(), target, p2 ? 4 : 3, p2);
            raiseCd = p2 ? 280 : 380;
            return;
        }
        if (p2 && wailCd <= 0 && dist < 8.0) {
            startWail(level);
            return;
        }
        if (necroCd <= 0 && dist < 20 && hasLineOfSight(target)) {
            startNecrotic(level, target, p2);
            return;
        }
        if (drainCd <= 0 && dist < 14 && hasLineOfSight(target)) {
            drainTarget = target;
            drainTicks = 40;
            drainCd = p2 ? 160 : 220;
            action("drain");
            announce("drain");
            playSound(SoundEvents.SOUL_ESCAPE.value(), 2.0F, 0.6F);
            return;
        }
        if (boltCd <= 0 && hasLineOfSight(target)) {
            castTimer = 10;
            boltCd = (p2 ? 30 : 45) + random.nextInt(15);
            action("cast");
            return;
        }
        kite(target, 6.0, 12.0, 1.0);
    }

    private void faceTarget(LivingEntity target) {
        getLookControl().setLookAt(target, 30F, 30F);
        lookAt(target, 30F, 30F);
    }

    private Vec3 staffPos() {
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 side = new Vec3(-look.z, 0, look.x);
        return position().add(look.scale(0.5)).add(side.scale(0.45)).add(0, 2.2, 0);
    }

    private void shootSouls(ServerLevel level, LivingEntity target, boolean p2) {
        int count = p2 ? 3 : 1;
        Vec3 from = staffPos();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize();
        Vec3 side = new Vec3(-to.z, 0, to.x).normalize();
        for (int i = 0; i < count; i++) {
            double spread = count == 1 ? 0 : (i / (double) (count - 1) - 0.5) * 0.8;
            ClassBossBolt.fire(this, ClassBossBolt.Kind.SOUL, from, to.add(side.scale(spread)).normalize(), 0.7, scaled(6.0F), 0.04, target, 1.0F, 80);
        }
        playSound(SoundEvents.WITHER_SHOOT, 1.0F, 1.3F);
    }

    private void tickDrain(ServerLevel level, boolean p2) {
        LivingEntity t = drainTarget;
        getNavigation().stop();
        if (t == null || !t.isAlive() || t.distanceTo(this) > 16 || !hasLineOfSight(t)
                || (t instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            drainTicks = 0;
            drainTarget = null;
            return;
        }
        faceTarget(t);
        Vec3 hand = position().add(Vec3.directionFromRotation(0, yBodyRot).scale(0.8)).add(0, 1.6, 0);
        Vec3 chest = t.position().add(0, t.getBbHeight() * 0.6, 0);
        if (drainTicks % 2 == 0) {
            BossFx.line(level, chest, hand, BossFx.dust(0x9A1020, 0.7F), 1.5);
            double k = (drainTicks % 10) / 10.0;
            Vec3 at = chest.add(hand.subtract(chest).scale(1 - k));
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.0);
        }
        if (drainTicks % 8 == 0 && t.hurt(damageSources().indirectMagic(this, this), scaled(2.0F))) {
            heal(p2 ? 4.5F : 3.0F);
            if (drainTicks % 16 == 0) drainSoul(level, t);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, chest.x, chest.y, chest.z, 2, 0.2, 0.2, 0.2, 0.1);
            playSound(SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.8F);
        }
        if (--drainTicks <= 0) drainTarget = null;
    }

    /**
     * ASSINATURA — Alma Drenada: um pedaço da alma do alvo fica preso no filactério (−2 de vida MÁXIMA por nível, até
     * −10). Só volta quando o filactério quebra ou o Lich cai. Dura 5 min (sai também ao morrer/beber leite).
     */
    private void drainSoul(ServerLevel level, LivingEntity t) {
        if (phylacteryBroken) return;
        BossCurses.stack(t, BossCurses.SOUL_DRAIN, 6000, BossCurses.SOUL_DRAIN_MAX_AMP);
        Vec3 chest = t.position().add(0, t.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, chest.x, chest.y, chest.z, 6, 0.2, 0.3, 0.2, 0.04);
        // A alma voa até o filactério (fio visível)
        if (phylacteryPos != null) BossFx.line(level, chest, phylacteryPos.add(0, 0.6, 0), ParticleTypes.SOUL, 2.5);
        level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.SOUL_ESCAPE.value(), net.minecraft.sounds.SoundSource.HOSTILE,
                1.4F, 0.5F);
        hintOnce(level, "soul_drain");
    }

    /** Devolve as almas: tira a Alma Drenada de todos por perto (filactério quebrado ou Lich derrotado). */
    private void releaseSouls(ServerLevel level) {
        for (ServerPlayer p : BossKit.partyAround(this, 64)) {
            if (!p.hasEffect(BossCurses.SOUL_DRAIN)) continue;
            BossCurses.cleanse(p, BossCurses.SOUL_DRAIN);
            level.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 16, 0.3, 0.6, 0.3, 0.05);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.lich.souls_released")
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
        }
    }

    /** Gelo da cripta: congelamento do vanilla (tela gelada, lento, dano de frio se travar) por {@code ticks}. */
    private void freeze(ServerLevel level, LivingEntity e, int ticks) {
        if (!e.canFreeze()) return;
        e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + ticks));
        level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + 1, e.getZ(), 14, 0.4, 0.6, 0.4, 0.02);
    }

    // ------------------------------------------------------------------ Lamento

    private void startWail(ServerLevel level) {
        wailTimer = WAIL_WARMUP;
        wailCd = 300;
        getNavigation().stop();
        action("wail");
        playSound(SoundEvents.WITHER_AMBIENT, 2.0F, 0.5F);
        Vec3 c = position();
        float dmg = scaled(5.0F);
        wailMark = Telegraph.delayed(level, Telegraph.Shape.circle(c, WAIL_RADIUS).warmup(WAIL_WARMUP).color(COLOR).owner(this),
                hits -> wail(level, c, hits, dmg));
    }

    private void wail(ServerLevel level, Vec3 c, List<LivingEntity> hits, float dmg) {
        wailMark = -1;
        if (!isAlive() || isReforming()) return;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().indirectMagic(this, this), dmg)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), this);
                freeze(level, e, 120);
            }
        }
        for (int k = 1; k <= 3; k++) BossFx.ring(level, c.add(0, 0.3 * k, 0), 2.3 * k, 14 * k, ParticleTypes.SCULK_SOUL, 0.02);
        level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.6, c.z, 1, 0, 0, 0, 0);
        playSound(SoundEvents.GHAST_SCREAM, 2.0F, 0.5F);
        playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 2.0F, 0.7F);
        BossKit.shake(level, c, 16, 0.8F, 12);
    }

    // ------------------------------------------------------------------ Círculo Necrótico

    private void startNecrotic(ServerLevel level, LivingEntity target, boolean p2) {
        necroTimer = NECRO_CAST;
        necroCd = (p2 ? 220 : 300) + random.nextInt(40);
        getNavigation().stop();
        faceTarget(target);
        action("necrotic");
        announce("necrotic");
        hintOnce(level, "necrotic");
        playSound(SoundEvents.SOUL_ESCAPE.value(), 2.0F, 0.4F);
        playSound(SoundEvents.WITHER_AMBIENT, 1.5F, 0.7F);
        float outer = p2 ? 8.0F : 6.5F;
        Vec3 first = groundAt(level, target.position());
        necroticRing(level, first, outer, p2);
        if (p2) {
            // Fase 2: um segundo anel em outro jogador do grupo (longe do primeiro).
            for (ServerPlayer p : BossKit.partyAround(this, 24)) {
                if (p == target || p.isCreative() || p.position().distanceTo(first) < outer + 2) continue;
                necroticRing(level, groundAt(level, p.position()), outer, true);
                break;
            }
        }
    }

    /** Anel que suga: o miolo é seguro; a cada 0,5 s quem está NO anel perde vida (murcha) e cura o Lich. */
    private void necroticRing(ServerLevel level, Vec3 c, float outer, boolean p2) {
        float dmg = scaled(2.0F);
        float heal = p2 ? 2.5F : 1.5F;
        Telegraph.Shape zone = Telegraph.Shape.ring(c, NECRO_INNER, outer);
        Telegraph.delayed(level, Telegraph.Shape.ring(c, NECRO_INNER, outer).warmup(NECRO_WARMUP).hold(NECRO_HOLD).color(NECRO_COLOR).owner(this),
                hits -> drainPulse(level, c, outer, hits, dmg * 2, heal));
        for (int t = 6; t < NECRO_WARMUP; t += 8) {
            BossKit.schedule(level, t, () -> {
                if (isAlive()) BossFx.ring(level, c.add(0, 0.2, 0), (NECRO_INNER + outer) * 0.5, 20, ParticleTypes.SOUL, 0.01);
            });
        }
        for (int t = NECRO_WARMUP + 10; t <= NECRO_WARMUP + NECRO_HOLD; t += 10) {
            BossKit.schedule(level, t, () -> {
                if (isAlive()) drainPulse(level, c, outer, Telegraph.entitiesIn(level, zone), dmg, heal);
            });
        }
    }

    private void drainPulse(ServerLevel level, Vec3 c, float outer, List<LivingEntity> hits, float dmg, float heal) {
        if (!isAlive()) return;
        int drained = 0;
        Vec3 heart = position().add(0, getBbHeight() * 0.6, 0);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().indirectMagic(this, this), dmg)) {
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), this);
                drained++;
                BossFx.line(level, e.position().add(0, e.getBbHeight() * 0.6, 0), heart, ParticleTypes.SOUL, 0.7);
            }
        }
        if (drained > 0 && !isReforming() && getHealth() < getMaxHealth()) {
            heal(heal * drained);
            level.sendParticles(ParticleTypes.SCULK_SOUL, heart.x, heart.y, heart.z, 6 * drained, 0.3, 0.4, 0.3, 0.03);
        }
        BossFx.ring(level, c.add(0, 0.3, 0), (NECRO_INNER + outer) * 0.5, 18, ParticleTypes.SCULK_SOUL, 0.04);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.HOSTILE, 0.9F, 0.6F);
    }

    // ------------------------------------------------------------------ Erguer os mortos

    /**
     * Ergue mortos-vivos em volta de um ponto (com capacete para não queimarem no sol): primeiro aparecem os círculos
     * de invocação no chão, 1 s depois os mortos saem deles.
     */
    private void raiseAt(ServerLevel level, Vec3 center, @Nullable LivingEntity target, int count, boolean p2) {
        int room = MAX_MINIONS - countMinions(level, 32);
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.8F, 0.6F);
        List<Vec3> spots = new ArrayList<>();
        List<EntityType<? extends Mob>> types = new ArrayList<>();
        for (int i = 0; i < Math.min(room, count); i++) {
            EntityType<? extends Mob> type = switch (random.nextInt(p2 ? 4 : 2)) {
                case 0 -> EntityType.ZOMBIE;
                case 1 -> EntityType.SKELETON;
                case 2 -> EntityType.STRAY;
                default -> EntityType.HUSK;
            };
            Vec3 at = findGroundSpot(level, center, 2.0, 4.5, type, center.add(0, 1.0, 0));
            if (at == null) continue;
            spots.add(at);
            types.add(type);
            Telegraph.show(level, Telegraph.Shape.circle(at, 0.9F).warmup(RAISE_DELAY).color(COLOR).owner(this).noFlash().noRunes());
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.2, at.z, 6, 0.3, 0.1, 0.3, 0.02);
        }
        BossKit.schedule(level, RAISE_DELAY, () -> {
            if (!isAlive()) return;
            int free = MAX_MINIONS - countMinions(level, 32);
            for (int i = 0; i < spots.size() && i < free; i++) spawnUndead(level, spots.get(i), types.get(i), target);
            playSound(SoundEvents.ZOMBIE_VILLAGER_CURE, 1.0F, 0.5F);
        });
    }

    private void spawnUndead(ServerLevel level, Vec3 at, EntityType<? extends Mob> type, @Nullable LivingEntity target) {
        Mob m = type.create(level);
        if (m == null) return;
        m.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0F);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
        m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        m.setDropChance(EquipmentSlot.HEAD, 0.0F);
        m.addTag(MINION_TAG);
        if (target != null && target.isAlive()) m.setTarget(target);
        level.addFreshEntity(m);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.3, at.z, 15, 0.3, 0.2, 0.3, 0.04);
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.8, at.z, 8, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE)), at.x, at.y + 0.2, at.z, 10, 0.3, 0.1, 0.3, 0.08);
    }

    /** Alvo escondido sem linha de visão: reaparece num ponto de onde enxerga o alvo. */
    @Override
    protected void onUnreachable(ServerLevel level, LivingEntity target) {
        if (hasLineOfSight(target)) return;
        if (BossKit.blinkNear(this, target, 7.0)) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1.2, getZ(), 15, 0.4, 0.8, 0.4, 0.05);
            blinkCd = Math.max(blinkCd, 40);
        }
    }

    // ------------------------------------------------------------------ Fases, reset, loot

    /** A fala da fase 2 ("enquanto meu filactério existir...") só cabe com o filactério inteiro. */
    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        if (!phylacteryBroken) super.onPhaseChange(level, phase);
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.WITHER_AMBIENT, 2.0F, 0.6F);
        if (!phylacterySpawned) spawnPhylactery(level);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 3 == 0) BossFx.ring(level, position().add(0, 0.1, 0), 1.0 + t * 0.04, 20, ParticleTypes.SOUL, 0.01);
        if (t == 40) {
            playSound(SoundEvents.SKELETON_AMBIENT, 2.0F, 0.4F);
            level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2.4, getZ(), 30, 0.6, 0.4, 0.6, 0.04);
        }
    }

    @Override
    protected void onReset(ServerLevel level) {
        castTimer = wailTimer = drainTicks = necroTimer = 0;
        wailMark = -1;
        drainTarget = null;
        raiseCd = 100;
        drainCd = 140;
        necroCd = 160;
        // Todos foram embora com ele desabado: ele se levanta inteiro (a vida já foi restaurada pela base).
        if (isReforming()) {
            entityData.set(DATA_REFORMING, false);
            reformTicks = 0;
        }
    }

    @Override
    protected void onDefeated(ServerLevel level) {
        super.onDefeated(level);
        releaseSouls(level);
        LichPhylactery p = phylacteryId == null ? null : (level.getEntity(phylacteryId) instanceof LichPhylactery lp ? lp : null);
        if (p != null) p.shatter();
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropById("phylactery", 1, 1);
        dropItem(CoreRegistries.ESSENCES.get(Element.SHADOW).get(), 3, 5);
        dropItem(CoreRegistries.ESSENCES.get(Element.ARCANE).get(), 1, 2);
    }

    @Override
    protected void clientTick() {
        if (isReforming()) return;
        if (random.nextInt(BossFx.reduced() ? 8 : 2) == 0) {
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getRandomX(0.4), getY() + 0.2, getRandomZ(0.4), 0, -0.01, 0);
        }
        if (random.nextInt(6) == 0) level().addParticle(ParticleTypes.SOUL, getRandomX(0.6), getY() + 1.5, getRandomZ(0.6), 0, 0.02, 0);
    }

    // ------------------------------------------------------------------ Sons e dados

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + random.nextFloat() * 0.1F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PhylacterySpawned", phylacterySpawned);
        tag.putBoolean("PhylacteryBroken", phylacteryBroken);
        if (phylacteryId != null) tag.putUUID("Phylactery", phylacteryId);
        if (phylacteryPos != null) {
            tag.putDouble("PhyX", phylacteryPos.x);
            tag.putDouble("PhyY", phylacteryPos.y);
            tag.putDouble("PhyZ", phylacteryPos.z);
        }
        tag.putInt("Reforms", reforms);
        tag.putInt("ReformTicks", isReforming() ? reformTicks : 0);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        phylacterySpawned = tag.getBoolean("PhylacterySpawned");
        phylacteryBroken = tag.getBoolean("PhylacteryBroken");
        phylacteryId = tag.hasUUID("Phylactery") ? tag.getUUID("Phylactery") : null;
        phylacteryPos = tag.contains("PhyX") ? new Vec3(tag.getDouble("PhyX"), tag.getDouble("PhyY"), tag.getDouble("PhyZ")) : null;
        reforms = tag.getInt("Reforms");
        reformTicks = tag.getInt("ReformTicks");
        entityData.set(DATA_REFORMING, reformTicks > 0);
    }
}
