package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Base dos chefes do módulo (Lorde Orc, Minotauro):
 * <ul>
 *   <li>barra de chefe; apresentação quando um jogador se aproxima (invulnerável durante a intro) com escala de grupo;</li>
 *   <li>fase 2 abaixo de 50% de vida;</li>
 *   <li>golpe corpo a corpo TELEGRAFADO (cone no chão antes do golpe — {@link BossMeleeGoal});</li>
 *   <li>anti-trapaça: alvo inalcançável por 2 s → {@link #onUnreachable}; salto com pouso marcado ({@link #leapAt});
 *       arremesso de arma com linha marcada ({@link #aimThrow}/{@link #releaseThrow}); não monta em barcos;</li>
 *   <li>tenacidade contra controle (atordoar/congelar/enraizar duram no máx. 1,5 s e dão 5 s de imunidade);</li>
 *   <li>modo provação ({@link BossKit#TRIAL_TAG}): intro pela arena inteira, sem recuperação por ociosidade, não sai
 *       da arena; fora dela, largado sozinho por 30 s se recupera devagar;</li>
 *   <li>recompensa de grupo ({@link BossRewards}) e animação de morte própria.</li>
 * </ul>
 */
public abstract class CbBoss extends CbMonster {
    private static final EntityDataAccessor<Boolean> PHASE2 = SynchedEntityData.defineId(CbBoss.class, EntityDataSerializers.BOOLEAN);
    private static final double BAR_RANGE = 48.0;
    private static final int CC_MAX = 30;
    private static final int CC_IMMUNITY = 100;
    private static final int IDLE_REGEN_AFTER = 600;
    /** Duração da cutscene de fase 2: as subclasses pausam os ataques por esse tempo (a câmera não deixa ver os avisos). */
    protected static final int PHASE_PAUSE = 40;

    protected final ServerBossEvent bossEvent;
    private boolean introDone;
    private int introTicks;
    private long ccImmuneUntil;
    private long lastResistFx;
    private int unreachableTicks;
    private int idleTicks;
    private int leashCooldown;
    // golpe corpo a corpo telegrafado
    private boolean meleeActive;
    private int meleeTicks;
    private float meleeYaw;
    private int meleeMarker = -1;
    protected int meleeCooldown;
    // salto anti-pilar
    private boolean leaping;
    private int leapTicks;
    private int leapFlight;
    private int leapMarker = -1;
    private float leapRadius;
    /** Centro do círculo de pouso marcado no chão. */
    @Nullable
    private Vec3 leapMark;

    protected CbBoss(EntityType<? extends Monster> type, Level level, BossEvent.BossBarColor color) {
        super(type, level);
        bossEvent = new ServerBossEvent(getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
        xpReward = 150;
        setPersistenceRequired();
    }

    /** Id do chefe (path da entidade): chaves de tradução. */
    protected abstract String bossKey();

    /** Cor RGB dos anúncios/cutscenes. */
    protected abstract int bossColor();

    /** XP mágico para cada jogador que lutou ({@link BossRewards#grant}). */
    protected abstract int rewardXp();

    /** Chave de tradução "boss.isekai_mahou.<chefe>.<sufixo>". */
    protected String langKey(String suffix) {
        return "boss." + IsekaiMahou.MODID + "." + bossKey() + "." + suffix;
    }

    protected int introLength() {
        return 60;
    }

    /** Duração da animação de morte (ticks). */
    protected int deathLength() {
        return 60;
    }

    /** Animação/som do começo da intro. */
    protected abstract void onIntro();

    /** Entrou na fase 2 (≤ 50% de vida). */
    protected abstract void onPhase2();

    /** O alvo está inalcançável há 2 s (pilar, barco, voando): o chefe reage (arremesso, salto...). */
    protected abstract void onUnreachable(ServerLevel level, LivingEntity target);

    // ---- golpe corpo a corpo (subclasses ajustam ao tamanho/animação)
    /** Ticks de aviso do cone = quadro de impacto da animação "attack". */
    protected int meleeWindup() {
        return 9;
    }

    protected float meleeRange() {
        return 3.8F;
    }

    protected float meleeAngle() {
        return 120F;
    }

    /** Ticks de descanso depois de um golpe corpo a corpo. */
    protected int meleeRecover() {
        return 18;
    }

    /** Animação tocada ao pousar do salto anti-pilar. */
    protected String landAnim() {
        return "slam";
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE2, false);
    }

    public boolean isPhase2() {
        return entityData.get(PHASE2);
    }

    public boolean inIntro() {
        return introTicks > 0;
    }

    protected boolean isLeaping() {
        return leaping;
    }

    /** Ocupado também durante o golpe telegrafado e o salto (as outras metas esperam). */
    @Override
    public boolean isBusy() {
        return super.isBusy() || meleeActive || leaping;
    }

    /** Pode começar um ataque agora? */
    protected boolean canAct() {
        LivingEntity t = getTarget();
        return !isBusy() && t != null && t.isAlive();
    }

    @Override
    protected double alertRadius() {
        return 24.0;
    }

    // =====================================================================================
    //  Ciclo
    // =====================================================================================
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 10 == 0) updateBar();
        if (introTicks > 0) introTicks--;
        if (!introDone && tickCount % 5 == 0) checkIntro();
        if (!isPhase2() && getHealth() <= getMaxHealth() * 0.5F) {
            entityData.set(PHASE2, true);
            Component key = Component.translatable(langKey("phase"));
            Cutscenes.playNear(this, BAR_RANGE, "boss_phase", getDisplayName(), key, bossColor(), PHASE_PAUSE, null);
            Announce.send(this, key, bossColor(), Announce.STYLE_BOSS);
            onPhase2();
        }
        if (!(level() instanceof ServerLevel sl)) return;
        if (meleeCooldown > 0) meleeCooldown--;
        if (leashCooldown > 0) leashCooldown--;
        if (introDone) {
            if (BossKit.isTrialBoss(this)) arenaLeash();
            else idleRegen(sl);
        }
        if (meleeActive) tickMelee(sl);
        if (leaping) {
            tickLeap(sl);
            return;
        }
        LivingEntity target = getTarget();
        if (introDone && introTicks <= 0 && target != null && target.isAlive() && !isBusy()) {
            unreachableTicks = BossKit.isUnreachable(this, target) ? unreachableTicks + 1 : 0;
            if (unreachableTicks >= 40) {
                unreachableTicks = 0;
                onUnreachable(sl, target);
            }
        }
    }

    /** Só jogadores "de verdade" (não criativo/espectador) disparam a introdução; na provação, a arena inteira. */
    private void checkIntro() {
        if (BossKit.isTrialBoss(this)) {
            double r = Math.max(20.0, BossKit.arenaRadius(this) + 8);
            Player p = level().getNearestPlayer(getX(), getY(), getZ(), r, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
            if (p != null) startIntro();
            return;
        }
        Player p = level().getNearestPlayer(getX(), getY(), getZ(), 20.0, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        if (p != null && hasLineOfSight(p)) startIntro();
    }

    private void startIntro() {
        introDone = true;
        introTicks = introLength();
        BossKit.scaleToParty(this, BAR_RANGE);
        setBusy(introLength());
        onIntro();
        Cutscenes.playNear(this, BAR_RANGE, "boss_intro", getDisplayName(),
                Component.translatable(langKey("subtitle")), bossColor(), introLength(), null);
        Announce.send(this, Component.translatable(langKey("intro")), bossColor(), Announce.STYLE_BOSS);
        if (level() instanceof ServerLevel sl) BossKit.shake(sl, position(), 32, 1.0F, 30);
    }

    private void updateBar() {
        if (!(level() instanceof ServerLevel level)) return;
        for (ServerPlayer p : level.players()) {
            boolean near = isAlive() && p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE;
            boolean has = bossEvent.getPlayers().contains(p);
            if (near && !has) bossEvent.addPlayer(p);
            else if (!near && has) bossEvent.removePlayer(p);
        }
    }

    /** Provação: não sai da arena (salta de volta para o centro). */
    private void arenaLeash() {
        Vec3 center = BossKit.arenaCenter(this);
        double r = BossKit.arenaRadius(this);
        if (center == null || r <= 0 || leashCooldown > 0 || !onGround() || isBusy() || inCommittedAttack()) return;
        double dx = getX() - center.x, dz = getZ() - center.z;
        if (dx * dx + dz * dz > (r - 1) * (r - 1)) {
            BossKit.leapTo(this, center, 1.0);
            leashCooldown = 80;
        }
    }

    /** No meio de um ataque que depende da posição (investida, pisão): a volta para a arena espera ele terminar. */
    protected boolean inCommittedAttack() {
        return false;
    }

    /** Fora das provações: largado sozinho por 30 s, recupera 2% da vida por segundo (bater e fugir não vale). */
    private void idleRegen(ServerLevel level) {
        LivingEntity t = getTarget();
        boolean alone = (t == null || !t.isAlive())
                && level.getNearestPlayer(getX(), getY(), getZ(), 32, EntitySelector.NO_CREATIVE_OR_SPECTATOR) == null;
        idleTicks = alone ? idleTicks + 1 : 0;
        if (idleTicks > IDLE_REGEN_AFTER && tickCount % 20 == 0 && getHealth() < getMaxHealth()) {
            heal(getMaxHealth() * 0.02F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() * 0.8, getZ(), 3, 0.4, 0.3, 0.4, 0.0);
        }
    }

    // =====================================================================================
    //  Combate: utilidades
    // =====================================================================================
    /** Alvo legítimo dos golpes do chefe (não aliado, não criativo/espectador). */
    public boolean isFoe(Entity e) {
        if (e == this || !(e instanceof LivingEntity le) || !le.isAlive() || isAlliedTo(e)) return false;
        return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    /**
     * Golpe físico do chefe: dano do atributo (já com escala de grupo e Força) × {@code mult}, empurrão para fora.
     * @return se entrou
     */
    protected boolean strike(LivingEntity e, float mult, double knock) {
        DamageSource src = damageSources().mobAttack(this);
        if (!e.hurt(src, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * mult)) return false;
        push(e, position(), knock, 0.25);
        if (level() instanceof ServerLevel sl) EnchantmentHelper.doPostAttackEffects(sl, e, src);
        setLastHurtMob(e);
        return true;
    }

    /** Empurra {@code e} para longe de {@code from} (respeita a resistência a repulsão). */
    protected static void push(LivingEntity e, Vec3 from, double out, double up) {
        Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
        d = d.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : d.normalize();
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        e.push(d.x * out * resist, up * resist, d.z * out * resist);
        e.hurtMarked = true;
    }

    protected float yawTo(Entity e) {
        return (float) (Mth.atan2(e.getZ() - getZ(), e.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** Encara a direção dada (corpo e cabeça) — usado para "travar" o chefe durante um golpe telegrafado. */
    protected void lockYaw(float yaw) {
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    // ---- golpe corpo a corpo telegrafado
    private void startMelee(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return;
        meleeActive = true;
        meleeTicks = 0;
        meleeYaw = yawTo(target);
        lockYaw(meleeYaw);
        getNavigation().stop();
        playAction("attack");
        meleeMarker = Telegraph.show(sl, Telegraph.Shape.cone(position(), meleeYaw, meleeAngle(), meleeRange())
                .warmup(meleeWindup()).color(Telegraph.DANGER).owner(this).noRunes());
    }

    private void tickMelee(ServerLevel sl) {
        meleeTicks++;
        lockYaw(meleeYaw);
        if (meleeTicks < meleeWindup()) return;
        meleeActive = false;
        meleeCooldown = meleeRecover();
        for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.cone(position(), meleeYaw, meleeAngle(), meleeRange()))) {
            if (isFoe(e)) strike(e, 1.0F, 0.6);
        }
        if (meleeMarker >= 0) Telegraph.finish(sl, meleeMarker);
        meleeMarker = -1;
        Vec3 look = Vec3.directionFromRotation(0, meleeYaw);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + look.x * 2.2, getY() + getBbHeight() * 0.45, getZ() + look.z * 2.2,
                2, 0.5, 0.1, 0.5, 0.0);
        playAttackSound();
    }

    /** Persegue como o corpo a corpo vanilla, mas o golpe é o cone telegrafado do chefe. */
    protected class BossMeleeGoal extends MeleeAttackGoal {
        public BossMeleeGoal(double speed) {
            super(CbBoss.this, speed, true);
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (canPerformAttack(target) && meleeCooldown <= 0 && !isBusy()) {
                resetAttackCooldown();
                startMelee(target);
            }
        }
    }

    // ---- salto com pouso marcado (anti-pilar / aproximação)
    /** Salta no alvo; o ponto de queda (raio {@code radius}) fica marcado no chão durante o voo. */
    protected void leapAt(ServerLevel sl, LivingEntity target, float radius) {
        playAction("leap");
        leapRadius = radius;
        // Voo longo o bastante para sair do círculo andando (de perto o arco fica mais alto).
        leapFlight = BossKit.leapAt(this, target, fairLeapPower(target.position(), radius));
        leapMark = target.position();
        leapMarker = Telegraph.show(sl, Telegraph.Shape.circle(leapMark, radius).warmup(leapFlight)
                .color(Telegraph.DANGER).owner(this));
        leaping = true;
        leapTicks = 0;
        CbFx.sound(this, SoundEvents.RAVAGER_STEP, 1.5F, 0.6F);
    }

    /**
     * Força do {@link BossKit#leapAt} (mesma conta de tempo do kit) para o voo durar pelo menos 6 ticks de reação +
     * o tempo de sair andando (0,25 bloco/tick) do círculo de pouso centrado no alvo.
     */
    private double fairLeapPower(Vec3 dest, float radius) {
        double dist = Math.hypot(dest.x - getX(), dest.z - getZ());
        double base = 8 + dist * 1.1 + Math.max(0, dest.y - getY()) * 0.8;
        double minTicks = 6 + (radius + 0.4) / 0.25;
        return Mth.clamp(base / minTicks, 0.3, 1.0);
    }

    private void tickLeap(ServerLevel sl) {
        leapTicks++;
        getNavigation().stop();
        if (!((onGround() && leapTicks > 3) || leapTicks > leapFlight + 30 || isInWater())) return;
        leaping = false;
        playAction(landAnim());
        if (leapMarker >= 0) Telegraph.finish(sl, leapMarker);
        leapMarker = -1;
        Vec3 c = position();
        // Caiu longe da marca (teto, parede, empurrão no ar): só acerta quem está colado nele.
        double off = leapMark == null ? 0 : Math.hypot(getX() - leapMark.x, getZ() - leapMark.z);
        float r = off <= 2.0 ? leapRadius : Math.min(leapRadius, getBbWidth() * 0.5F + 1.5F);
        leapMark = null;
        for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(c, r))) {
            if (!isFoe(e)) continue;
            float falloff = (float) (1.0 - 0.4 * Math.min(1.0, e.position().distanceTo(c) / r));
            if (strike(e, 0.9F * falloff, 0.0)) push(e, c, 1.3 * falloff, 0.55);
        }
        CbFx.groundBurst(sl, c, r);
        BossKit.shake(sl, c, 24, 1.4F, 16);
        CbFx.sound(this, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.6F);
        CbFx.sound(this, SoundEvents.ANVIL_LAND, 0.9F, 0.5F);
        meleeCooldown = Math.max(meleeCooldown, 10);
    }

    // ---- arremesso de arma com linha marcada
    /** Mira travada de um arremesso: direção 3D do lançamento + linha desenhada no chão até o alvo. */
    protected record ThrowAim(Vec3 from, Vec3 dir, double range, int marker) {}

    /**
     * Trava a mira AGORA na posição atual do alvo e desenha a linha no chão (aquecimento {@code warmup} = quadro em
     * que a arma sai da mão). Desviar para o lado depois do aviso faz a arma errar.
     */
    protected ThrowAim aimThrow(ServerLevel sl, LivingEntity target, int warmup) {
        float yaw = yawTo(target);
        lockYaw(yaw);
        Vec3 side = Vec3.directionFromRotation(0, yaw - 90F);
        Vec3 from = position().add(0, getBbHeight() * 0.8, 0).add(side.scale(getBbWidth() * 0.35));
        Vec3 aimAt = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 dir = aimAt.subtract(from);
        double dist = dir.length();
        dir = dist < 1.0E-3 ? Vec3.directionFromRotation(0, yaw) : dir.scale(1.0 / dist);
        double range = Mth.clamp(dist + 6.0, 10.0, 36.0);
        // Faixa no chão: da frente do chefe até além do alvo (mesma direção horizontal do arremesso).
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, yaw) : flat.normalize();
        double flatLen = Math.hypot(aimAt.x - from.x, aimAt.z - from.z) + 4.0;
        Vec3 g0 = position().add(flat.scale(getBbWidth() * 0.5));
        Vec3 g1 = new Vec3(getX() + flat.x * flatLen, target.getY(), getZ() + flat.z * flatLen);
        int marker = Telegraph.show(sl, Telegraph.Shape.line(g0, g1, 1.6F).warmup(warmup).color(Telegraph.WARNING).owner(this));
        return new ThrowAim(from, dir, range, marker);
    }

    /** Direção (yaw) da mira travada — o chefe fica virado para a linha até soltar a arma. */
    protected static float aimYaw(ThrowAim aim) {
        return (float) (Mth.atan2(aim.dir().z, aim.dir().x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** Solta a arma na mira travada. O dano físico é o do atributo × {@code mult} (escala de grupo incluída). */
    protected void releaseThrow(ServerLevel sl, ThrowAim aim, ItemStack weapon, float mult, double knock, float scale) {
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * mult;
        BossThrownAxe axe = BossThrownAxe.launch(sl, this, aim.from(), aim.dir(), 1.35, dmg, knock, aim.range(), weapon, scale);
        if (axe != null && aim.marker() >= 0) Telegraph.finish(sl, aim.marker());
        CbFx.sound(this, SoundEvents.TRIDENT_THROW.value(), 1.6F, 0.55F);
        CbFx.sound(this, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
    }

    // =====================================================================================
    //  Dano, efeitos, morte
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        // Atacado de longe por um jogador antes de vê-lo: a introdução começa agora (e o golpe é ignorado).
        if (!introDone && !bypass && !level().isClientSide && source.getEntity() instanceof Player p
                && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)) {
            startIntro();
            setTarget(p);
        }
        // Intro: invulnerável (a cena é de graça para todos).
        if (introTicks > 0 && !bypass) return false;
        return super.hurt(source, amount);
    }

    /**
     * Tenacidade: controles fortes duram no máx. 1,5 s e deixam o chefe imune a eles por 5 s (com faísca de "resistiu");
     * lentidão vira no máx. Lentidão II.
     */
    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        if (level().isClientSide) return super.addEffect(effect, source);
        MobEffectInstance e = effect;
        if (isHardControl(effect.getEffect().value())) {
            long now = level().getGameTime();
            if (now < ccImmuneUntil) {
                resistFx(now);
                return false;
            }
            // Efeito "infinito" (duração −1) também vira 1,5 s.
            int dur = effect.isInfiniteDuration() ? CC_MAX : Math.min(effect.getDuration(), CC_MAX);
            ccImmuneUntil = now + dur + CC_IMMUNITY;
            e = new MobEffectInstance(effect.getEffect(), dur, Math.min(effect.getAmplifier(), 1), effect.isAmbient(),
                    effect.isVisible(), effect.showIcon());
        } else if (effect.is(MobEffects.MOVEMENT_SLOWDOWN) && (effect.getAmplifier() > 1 || effect.isInfiniteDuration())) {
            int dur = effect.isInfiniteDuration() ? 80 : Math.min(effect.getDuration(), 80);
            e = new MobEffectInstance(effect.getEffect(), dur, Math.min(effect.getAmplifier(), 1), effect.isAmbient(),
                    effect.isVisible(), effect.showIcon());
        }
        boolean added = super.addEffect(e, source);
        if (added && (e.getEffect().value() == CoreRegistries.STUNNED.get() || e.getEffect().value() == CoreRegistries.FROZEN.get())) {
            interruptAttack(e.getDuration());
        }
        return added;
    }

    /**
     * Atordoado/congelado: cancela o golpe corpo a corpo em preparo e fica parado pelo tempo do efeito — as metas de
     * ataque param sozinhas (estão "ocupadas") e apagam os próprios avisos no chão.
     */
    protected void interruptAttack(int ticks) {
        if (meleeActive && level() instanceof ServerLevel sl && meleeMarker >= 0) Telegraph.cancel(sl, meleeMarker);
        meleeActive = false;
        meleeMarker = -1;
        if (!leaping) setBusy(Math.max(10, ticks));
    }

    private static boolean isHardControl(MobEffect v) {
        return v == CoreRegistries.STUNNED.get() || v == CoreRegistries.FROZEN.get() || v == CoreRegistries.ROOTED.get()
                || v == CoreRegistries.SILENCED.get() || v == MobEffects.LEVITATION.value();
    }

    private void resistFx(long now) {
        if (now - lastResistFx < 10 || !(level() instanceof ServerLevel sl)) return;
        lastResistFx = now;
        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + getBbHeight() * 0.7, getZ(), 14, 0.5, 0.6, 0.5, 0.2);
        CbFx.sound(this, SoundEvents.SHIELD_BLOCK, 1.0F, 0.7F);
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        // Barco/carrinho seguraria o chefe parado (trapaça clássica).
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        // Saltos de chefe são de propósito.
        return false;
    }

    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        // O evento de morte pode ser cancelado (ou die() chamado de novo): só conta a vitória uma vez, de verdade.
        if (wasDead || !dead) return;
        meleeActive = false;
        leaping = false;
        if (level() instanceof ServerLevel level) {
            BossRewards.grant(this, rewardXp(), BAR_RANGE);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) > BAR_RANGE * BAR_RANGE) continue;
                p.sendSystemMessage(Component.translatable(langKey("defeated"), getDisplayName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
            Telegraph.cancelAll(this);
        }
        bossEvent.removeAllPlayers();
    }

    /** Morte animada (a vanilla tomba e some em 1 s): poeira quando o corpo bate no chão. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        if (deathTime == 36) {
            CbFx.groundBurst(sl, position(), 2.5);
            CbFx.sound(this, SoundEvents.ANVIL_LAND, 0.7F, 0.45F);
            BossKit.shake(sl, position(), 20, 0.8F, 10);
        }
        if (deathTime >= deathLength() && !isRemoved()) {
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.6, getZ(), 30, 0.9, 0.5, 0.9, 0.02);
            sl.broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IntroDone", introDone);
        tag.putBoolean("Phase2", isPhase2());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        introDone = tag.getBoolean("IntroDone");
        entityData.set(PHASE2, tag.getBoolean("Phase2"));
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    // =====================================================================================
    //  GeckoLib: controlador de morte (por último, sobrepõe os outros e segura o último quadro)
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        super.registerControllers(reg);
        RawAnimation death = RawAnimation.begin().thenPlayAndHold(animName("death"));
        reg.add(new AnimationController<CbBoss>(this, "death", 3, s -> isDeadOrDying() ? s.setAndContinue(death) : PlayState.STOP));
    }
}
