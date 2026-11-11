package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Zéfiro, o Djinn dos Ventos (Guardião do Vento): voa em círculos sobre o alvo.
 * Golpes: rasante com garras (a faixa do rasante é desenhada antes — saia dela), rajada em leque que arremessa,
 * lâminas de vento (glifos), tornados que perseguem (o ponto onde nascem é marcado), Mergulho do Céu (um círculo
 * segue o alvo e trava; ele mergulha ali e fica exposto no chão), Cortina de Ventos (fase 2+: paredes de vento
 * girando em volta — ande junto com a brecha) e, na fase 3, Olho da Tempestade (suga todos e explode no círculo).
 */
public class ZephyrEntity extends ElementalGuardian {
    static final List<GuardianMove> MOVES = List.of(
            // Alcance 14: em órbita (raio 9, até 6 de altura) o alvo fica a ~10 blocos da borda
            GuardianMove.of("talon", 28).range(0, 14).cooldown(30).weight(12).selfMoving(),
            GuardianMove.of("gale", 30).range(0, 16).cooldown(90).weight(10).announce().aimUntil(1),
            GuardianMove.of("wind_blades", 30).range(3, 32).cooldown(46).weight(14),
            GuardianMove.of("tornado", 40).range(0, 30).cooldown(300).weight(9).announce(),
            GuardianMove.of("dive", 52).range(3, 28).cooldown(180).weight(10).announce().selfMoving(),
            GuardianMove.of("wind_curtain", 110).range(0, 20).cooldown(400).weight(11).phases(2, 3).announce(),
            GuardianMove.of("vacuum", 56).range(0, 16).cooldown(420).weight(13).phases(3, 3).announce());

    private static final EntityDataAccessor<Boolean> DATA_EXPOSED = SynchedEntityData.defineId(ZephyrEntity.class, EntityDataSerializers.BOOLEAN);
    private static final float CURTAIN_INNER = 2.5F;
    private static final float CURTAIN_OUTER = 15F;

    private final RawAnimation animFly;
    private final RawAnimation animExposed;
    private double orbitAngle;
    private Vec3 diveTarget = Vec3.ZERO;
    private int diveMark = -1;
    private double diveSpeed = 1.15;
    private boolean diveLanded;
    private int exposedTicks;
    private final Set<UUID> swoopHit = new HashSet<>();
    private Vec3 swoopA = Vec3.ZERO;
    private Vec3 swoopB = Vec3.ZERO;
    /** Faixa do rasante (a mesma desenhada no chão): só quem está nela leva as garras. */
    private static final float SWOOP_WIDTH = 2.6F;
    /** Tick do mergulho em que o círculo travado termina de encher (o impacto nunca vem antes). */
    private static final int DIVE_HIT = 26;
    private Vec3 galeDir = Vec3.ZERO;
    private Vec3 vacuumGround = Vec3.ZERO;
    private final List<Vec3> tornadoSpots = new ArrayList<>();
    private Vec3 curtainCenter = Vec3.ZERO;
    private float curtainStart;
    private float curtainSpeed;

    public ZephyrEntity(EntityType<? extends ZephyrEntity> type, Level level) {
        super(type, level, MOVES);
        this.moveControl = new HoverMoveControl(this);
        this.animFly = loop("fly");
        this.animExposed = loop("exposed");
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 340.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.ATTACK_DAMAGE, 11.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected double cullMargin() { return 2.6; }

    @Override
    public Element element() { return Element.WIND; }

    private int windColor() {
        return Telegraph.color(Element.WIND);
    }

    @Override
    protected BossEvent.BossBarColor barColor(int phase) {
        return phase >= 3 ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.WHITE;
    }

    @Override
    protected SoundEvent roarSound() { return SoundEvents.ENDER_DRAGON_GROWL; }

    @Override
    protected float roarPitch() { return 1.4F; }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.BREEZE_IDLE_AIR; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.BREEZE_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.BREEZE_DEATH; }

    @Override
    protected String[] rangedRecipes() {
        return new String[] {"projectile homing wind_blade", "projectile homing gust", "projectile gust"};
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void addIdleGoals() {
        goalSelector.addGoal(6, new HoverWanderGoal());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EXPOSED, false);
    }

    /** Depois do mergulho fica no chão, vulnerável. */
    public boolean isExposed() { return entityData.get(DATA_EXPOSED); }

    @Override
    protected float modifyIncomingDamage(DamageSource source, float amount) {
        return isExposed() ? amount * 1.5F : amount;
    }

    /** Chão logo abaixo do Zéfiro (ele voa até ~7 blocos de altura). */
    private Vec3 groundBelow() {
        return new Vec3(getX(), BossSpells.groundY(level(), getX(), getY(), getZ(), 0, 14), getZ());
    }

    // =====================================================================================
    //  Voo
    // =====================================================================================
    @Override
    protected void combatMovement(LivingEntity target, double dist) {
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        GuardianMove m = currentMove();
        if (m != null && !m.rooted()) return; // mergulho/rasante controlam o próprio voo
        if (isBusy() || isExposed()) {
            // Exposto depois do mergulho: fica caído no chão até recuperar o fôlego (a janela de punição é real)
            moveControl.setWantedPosition(getX(), getY(), getZ(), 0.3);
            return;
        }
        orbitAngle += 0.025 + 0.01 * phase();
        double radius = phase() >= 3 ? 7.0 : 9.0;
        Vec3 base = target.position();
        double height = Math.min(6.0, BossSpells.headroom(level(), base.add(0, 1, 0), 9.0) - getBbHeight() - 0.5);
        height = Math.max(1.5, height);
        Vec3 want = base.add(Math.cos(orbitAngle) * radius, height, Math.sin(orbitAngle) * radius);
        moveControl.setWantedPosition(want.x, want.y, want.z, 1.0);
        BossSpells.face(this, target.position());
    }

    /** Sem alvo: paira perto de casa. */
    private class HoverWanderGoal extends Goal {
        HoverWanderGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getTarget() == null && !moveControl.hasWanted() && random.nextInt(20) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            BlockPos h = home() != null ? home() : blockPosition();
            double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * 10;
            Vec3 ground = new Vec3(h.getX() + 0.5 + Math.cos(a) * d, h.getY(), h.getZ() + 0.5 + Math.sin(a) * d);
            double y = BossSpells.groundY(level(), ground.x, ground.y + 3, ground.z, 4, 10);
            double up = Math.min(5.0, BossSpells.headroom(level(), new Vec3(ground.x, y + 1, ground.z), 8.0) - getBbHeight() - 0.5);
            moveControl.setWantedPosition(ground.x, y + Math.max(1.0, up), ground.z, 0.5);
        }
    }

    /** Controle de voo direto (estilo Vex): acelera até o ponto desejado e freia no ar. */
    static class HoverMoveControl extends MoveControl {
        HoverMoveControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            // Mergulho/rasante definem a velocidade direto: o controle não pode frear nem desviar para o ponto antigo
            if (mob instanceof ElementalGuardian g && g.currentMove() != null && !g.currentMove().rooted()) {
                operation = Operation.WAIT;
                return;
            }
            if (operation != Operation.MOVE_TO) return;
            Vec3 d = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
            double len = d.length();
            if (len < 0.6) {
                operation = Operation.WAIT;
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.6));
                return;
            }
            double accel = 0.045 * speedModifier;
            Vec3 v = mob.getDeltaMovement().add(d.scale(accel / len));
            double max = 0.6 * speedModifier;
            if (v.length() > max) v = v.normalize().scale(max);
            mob.setDeltaMovement(v);
            if (mob.getTarget() == null) {
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
                mob.setYRot(rotlerp(mob.getYRot(), yaw, 10.0F));
                mob.yBodyRot = mob.getYRot();
            }
        }
    }

    // =====================================================================================
    //  Golpes
    // =====================================================================================
    @Override
    protected void performMove(ServerLevel level, GuardianMove move, int t, @Nullable LivingEntity target) {
        switch (move.id) {
            case "talon" -> talon(level, t, target);
            case "gale" -> gale(level, t);
            case "wind_blades" -> windBlades(level, t, target);
            case "tornado" -> tornado(level, t);
            case "dive" -> dive(level, t, target);
            case "wind_curtain" -> windCurtain(level, t);
            case "vacuum" -> vacuum(level, t);
            default -> {}
        }
    }

    private Vec3 forward() {
        return BossSpells.dirOf(bodyYaw());
    }

    /**
     * Rasante: a faixa no chão (passando pelo alvo) é desenhada 14 ticks antes; o Zéfiro desce até o começo dela e
     * a percorre rente ao chão com as garras. Dê um passo para o lado.
     */
    private void talon(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t == 0) {
            swoopHit.clear();
            Vec3 tp = target != null ? target.position() : groundBelow().add(forward().scale(6));
            Vec3 dir = BossSpells.flatDir(position(), tp, forward());
            swoopA = groundAt(tp.subtract(dir.scale(4)));
            double len = BossSpells.clearDistance(level, swoopA, dir, 8, 1.0);
            swoopB = groundAt(swoopA.add(dir.scale(Math.max(2, len))));
            Telegraph.show(level, Telegraph.Shape.line(swoopA, swoopB, SWOOP_WIDTH).warmup(14).hold(6).color(windColor()).owner(this));
            BossSpells.face(this, tp);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 2.0F, 0.7F);
        }
        Vec3 dir = BossSpells.flatDir(swoopA, swoopB, forward());
        if (t < 10) {
            // Recua um pouco e se prepara (asas para trás)
            setDeltaMovement(dir.scale(-0.06).add(0, 0.03, 0));
            BossSpells.face(this, position().add(dir));
            return;
        }
        Vec3 a = swoopA.add(0, 0.4, 0), b = swoopB.add(0, 0.4, 0);
        if (t < 15) {
            setDeltaMovement(clampSpeed(a.subtract(position()).scale(1.0 / (15 - t)), 2.0));
        } else if (t < 20) {
            setDeltaMovement(clampSpeed(b.subtract(position()).scale(1.0 / (20 - t)), 2.0));
        } else {
            setDeltaMovement(getDeltaMovement().scale(0.7).add(0, 0.12, 0));
        }
        if (t >= 10 && t < 20) {
            BossSpells.face(this, position().add(dir));
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 2, 0.4, 0.3, 0.4, 0.01);
        }
        if (t >= 14 && t <= 20) {
            // Acerta quem o Zéfiro alcança E está dentro da faixa desenhada (a descida até a faixa não fere ninguém)
            Telegraph.Shape lane = Telegraph.Shape.line(swoopA, swoopB, SWOOP_WIDTH);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.9),
                    e -> isFoe(e) && !swoopHit.contains(e.getUUID()) && lane.contains(e))) {
                swoopHit.add(e.getUUID());
                if (e.hurt(damageSources().mobAttack(this), meleeDamage())) {
                    BossSpells.knock(e, dir.add(e.position().subtract(position()).normalize().scale(0.5)), 1.0, 0.5);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.5F, 0.6F);
                }
            }
        }
    }

    private static Vec3 clampSpeed(Vec3 v, double max) {
        double l = v.length();
        return l > max ? v.scale(max / l) : v;
    }

    /** Rajada Divina: o leque é desenhado no chão 14 ticks antes; a rajada corre por ele e arremessa longe. */
    private void gale(ServerLevel level, int t) {
        double half = phase() >= 3 ? 50 : 32;
        if (t == 0) {
            galeDir = forward();
            Telegraph.show(level, Telegraph.Shape.cone(groundBelow(), BossSpells.yawOf(galeDir), (float) (half * 2), 17F)
                    .warmup(14).hold(15).color(windColor()).owner(this));
        }
        if (t < 14 && t % 2 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 1.2, 0.6, 1.2, 0.02);
        }
        if (t != 14) return;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3.0F, 0.8F);
        GuardianShockwave.create(this, groundBelow(), Element.WIND)
                .arc(galeDir, half)
                .radius(17F, 1.1F)
                .damage(7F * mult())
                .knock(2.3, 1.0)
                .launch();
        for (int i = 1; i < 12; i++) {
            Vec3 p = position().add(0, getBbHeight() * 0.5, 0).add(galeDir.scale(i * 1.4));
            level.sendParticles(ParticleTypes.GUST, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0);
        }
    }

    private void windBlades(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (target == null) return;
        if (t != 10 && t != 16 && t != 22) return;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.HOSTILE, 2.0F, 0.8F);
        if (phase() >= 2) {
            BossSpells.cast(this, Element.WIND, target, spellPower(), "projectile split split accelerate wind_blade",
                    "projectile split split gust", "projectile split split");
        } else {
            BossSpells.cast(this, Element.WIND, target, spellPower(), "projectile split accelerate wind_blade",
                    "projectile split gust");
        }
    }

    /** Tornados: os pontos onde nascem são marcados 16 ticks antes; depois perseguem o inimigo mais próximo. */
    private void tornado(ServerLevel level, int t) {
        if (t < 20 && t % 2 == 0) {
            double a = t * 0.5;
            level.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(a) * 2.5, getY() + 1, getZ() + Math.sin(a) * 2.5, 4, 0.2, 0.4, 0.2, 0.05);
        }
        if (t == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 3.0F, 0.5F);
        if (t == 4) {
            tornadoSpots.clear();
            int n = phase();
            Vec3 g = groundBelow();
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n + random.nextDouble();
                Vec3 s = groundAt(new Vec3(g.x + Math.cos(a) * 3.5, g.y, g.z + Math.sin(a) * 3.5));
                tornadoSpots.add(s);
                Telegraph.show(level, Telegraph.Shape.circle(s, 2.3F).warmup(16).color(windColor()).owner(this).noRunes());
            }
        }
        if (t != 20) return;
        for (Vec3 s : tornadoSpots) {
            ZephyrTornado.spawn(this, s, phase() >= 3 ? 260 : 200, 0.13F + 0.03F * phase(), 3F * mult());
        }
        tornadoSpots.clear();
    }

    /**
     * Mergulho do Céu: um círculo vermelho segue o alvo e TRAVA no tick 14; o Zéfiro sobe e mergulha exatamente ali
     * no tick ~26. A onda de vento do impacto (anel laranja) corre rente ao chão — pule. Depois fica exposto.
     */
    private void dive(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t == 0) {
            diveLanded = false;
            swoopHit.clear();
            if (target != null) {
                diveTarget = target.position();
                diveMark = Telegraph.show(level, Telegraph.Shape.follow(target, 4.0F).warmup(26).lock(12).color(Telegraph.DANGER).owner(this));
            } else {
                // Sem alvo, mergulha no chão logo abaixo (nunca num ponto antigo/na origem do mundo)
                diveTarget = groundBelow();
                diveMark = Telegraph.show(level, Telegraph.Shape.circle(diveTarget, 4.0F).warmup(26).color(Telegraph.DANGER).owner(this));
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3.0F, 1.2F);
        }
        if (t < 16) {
            double room = BossSpells.headroom(level(), position().add(0, getBbHeight(), 0), 3.0);
            setDeltaMovement(0, room > 1.0 ? 0.35 : 0.0, 0);
            if (t % 2 == 0) level.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 4, 0.6, 0.2, 0.6, 0.03);
            if (t < 15 && target != null) diveTarget = target.position();
            if (t == 15) {
                Vec3 locked = Telegraph.centerOf(diveMark);
                if (locked != null) diveTarget = locked;
                Telegraph.show(level, Telegraph.Shape.ring(groundAt(diveTarget), 4.0F, 9.0F).warmup(11).hold(10).vertical(0.5, 1.0)
                        .color(Telegraph.WARNING).owner(this).noRunes());
            }
            BossSpells.face(this, diveTarget);
            return;
        }
        if (t == 16) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 3.0F, 0.5F);
            // Velocidade para chegar no ponto travado junto com o fim do círculo (tick 26)
            diveSpeed = Math.max(0.35, diveTarget.distanceTo(position()) / (DIVE_HIT - 16.0));
        }
        if (!diveLanded && t < 36) {
            Vec3 to = diveTarget.subtract(position());
            double len = to.length();
            boolean arrived = onGround() || verticalCollision || (horizontalCollision && t > 18) || len < 1.0 || t == 35;
            if (arrived && t < DIVE_HIT) {
                // Chegou antes da hora: espera o círculo encher (o jogador sempre tem o tempo inteiro do aviso)
                setDeltaMovement(Vec3.ZERO);
                return;
            }
            if (len > 1.0E-3) setDeltaMovement(to.scale(Math.min(diveSpeed, len) / len));
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 3, 0.4, 0.4, 0.4, 0.01);
            level.sendParticles(ElementFx.dust(Element.WIND, 1.6F), getX(), getY() + 1, getZ(), 4, 0.5, 0.5, 0.5, 0);
            if (arrived) diveImpact(level);
            return;
        }
        // Recuperação no chão: exposto (o tempo corre em passiveTick)
        setDeltaMovement(getDeltaMovement().multiply(0.3, 0.3, 0.3));
    }

    private void diveImpact(ServerLevel level) {
        diveLanded = true;
        setDeltaMovement(Vec3.ZERO);
        Vec3 c = groundAt(diveTarget);
        Telegraph.finish(level, diveMark);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 3.0F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.8F);
        BossKit.shake(level, c, 24, 1.6F, 16);
        for (LivingEntity e : foesIn(level, Telegraph.Shape.circle(c, 4.0F))) {
            if (e.hurt(damageSources().mobAttack(this), meleeDamage() * 1.5F)) {
                BossSpells.knock(e, e.position().subtract(c), 1.4, 0.6);
            }
        }
        GuardianShockwave.create(this, c, Element.WIND)
                .radius(9F, 0.8F).damage(6F * mult()).knock(1.6, 0.7).groundOnly().launch();
        entityData.set(DATA_EXPOSED, true);
        exposedTicks = 40;
    }

    /**
     * Cortina de Ventos (fase 2+): as paredes são desenhadas no chão 20 ticks antes; depois giram em volta do
     * Zéfiro por ~4 s. Encostar empurra e fere — caminhe junto com a brecha entre duas paredes.
     */
    private void windCurtain(ServerLevel level, int t) {
        int walls = phase() >= 3 ? 4 : 3;
        if (t == 0) {
            curtainCenter = groundBelow();
            curtainStart = random.nextFloat() * 360F;
            // Velocidade em que dá para acompanhar a brecha andando (a ~8 blocos do centro: ~4 blocos/s)
            curtainSpeed = (random.nextBoolean() ? 1F : -1F) * (phase() >= 3 ? 1.8F : 1.4F);
            for (int k = 0; k < walls; k++) {
                Vec3 dir = BossSpells.dirOf(curtainStart + k * 360F / walls);
                Telegraph.show(level, Telegraph.Shape.line(curtainCenter.add(dir.scale(CURTAIN_INNER)), curtainCenter.add(dir.scale(CURTAIN_OUTER)),
                        ZephyrCurtain.HALF_WIDTH * 2).warmup(20).color(windColor()).owner(this));
            }
            hint("wind_curtain", 32);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 3.0F, 0.4F);
        }
        if (t < 20 && t % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = 3 + random.nextDouble() * 10;
                Vec3 p = curtainCenter.add(Math.cos(a) * d, 0.3 + random.nextDouble() * 2, Math.sin(a) * d);
                level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, -Math.sin(a), 0.05, Math.cos(a), 0.3);
            }
        }
        if (t == 20) {
            ZephyrCurtain.spawn(this, curtainCenter, walls, curtainStart, curtainSpeed, CURTAIN_INNER, CURTAIN_OUTER, 88, 4F * mult());
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 3.0F, 0.5F);
            BossKit.shake(level, curtainCenter, 24, 0.8F, 12);
        }
    }

    /**
     * Olho da Tempestade (fase 3): puxa todos para dentro por 2 s e explode no círculo vermelho; depois uma onda
     * rente ao chão (anel laranja, pule). Corra contra o vento.
     */
    private void vacuum(ServerLevel level, int t) {
        Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
        if (t == 0) {
            vacuumGround = groundBelow();
            Telegraph.show(level, Telegraph.Shape.circle(vacuumGround, 6.5F).warmup(42).color(Telegraph.DANGER).owner(this));
            Telegraph.show(level, Telegraph.Shape.ring(vacuumGround, 6.5F, 13F).warmup(42).hold(14).vertical(0.5, 1.0)
                    .color(Telegraph.WARNING).owner(this).noRunes());
            hint("vacuum", 24);
        }
        if (t < 40) {
            for (LivingEntity e : BossSpells.foesAround(this, c, 16)) {
                Vec3 to = c.subtract(e.position());
                if (to.length() < 2.0) continue;
                Vec3 v = e.getDeltaMovement().add(to.normalize().scale(0.06));
                if (v.length() > 0.5) v = v.normalize().scale(0.5);
                e.setDeltaMovement(v);
                e.hurtMarked = true;
            }
            for (int i = 0; i < 5; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = 6 + random.nextDouble() * 9;
                Vec3 p = c.add(Math.cos(a) * d, (random.nextDouble() - 0.3) * 4, Math.sin(a) * d);
                Vec3 v = c.subtract(p).normalize();
                level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, v.x, v.y, v.z, 0.6);
            }
            if (t % 10 == 0) level.playSound(null, c.x, c.y, c.z, SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 3.0F, 0.4F + t / 80F);
            return;
        }
        if (t != 42) return;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 1.0, 1.0, 1.0, 0);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y, c.z, 3, 1.0, 1.0, 1.0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 4.0F, 0.4F);
        BossKit.shake(level, c, 30, 2.0F, 20);
        for (LivingEntity e : foesIn(level, Telegraph.Shape.circle(vacuumGround, 6.5F).vertical(1.5, 10.0))) {
            BossSpells.hit(this, e, Element.WIND, 14F * mult());
            BossSpells.knock(e, e.position().subtract(c), 2.2, 0.9);
        }
        GuardianShockwave.create(this, vacuumGround, Element.WIND).radius(13F, 1.0F).damage(4F * mult())
                .knock(1.0, 0.5).groundOnly().launch();
    }

    // =====================================================================================
    //  Passivos e visual
    // =====================================================================================
    @Override
    protected void passiveTick(ServerLevel level, @Nullable LivingEntity target) {
        if (exposedTicks > 0 && --exposedTicks == 0) entityData.set(DATA_EXPOSED, false);
        if (isExposed() && tickCount % 4 == 0) {
            double a = tickCount * 0.3;
            level.sendParticles(ParticleTypes.CRIT, getX() + Math.cos(a), getY() + getBbHeight() + 0.2, getZ() + Math.sin(a), 2, 0.1, 0.1, 0.1, 0);
        }
        if (tickCount % 18 == 0 && getDeltaMovement().lengthSqr() > 0.01 && !isDeadOrDying()) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.2F, 0.6F);
        }
        // Sem alvo e longe do chão de casa: desce devagar até a altura de pairar
        if (target == null && !isBusy() && tickCount % 40 == 0 && home() != null && position().distanceTo(home().getCenter()) > 24) {
            moveControl.setWantedPosition(home().getX() + 0.5, home().getY() + 3, home().getZ() + 0.5, 0.6);
        }
    }

    @Override
    protected void introImpact(ServerLevel level) {
        // Abre as asas no ar: rajada em volta em vez de explosão no chão
        Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
        BossKit.shake(level, c, 32, 1.4F, 20);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y, c.z, 3, 1.5, 0.8, 1.5, 0);
        ElementFx.burst(level, c, Element.WIND, 80, 2.0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3.0F, 0.9F);
    }

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, getX(), getY() + 1.5, getZ(), 2, 1.0, 1.0, 1.0, 0);
        if (phase == 3) {
            // Rajada em volta ao fim do rugido: o anel avisa e dá para pular
            Vec3 g = groundBelow();
            Telegraph.show(level, Telegraph.Shape.ring(g, 1.0F, 12F).warmup(30).hold(14).vertical(0.5, 1.0)
                    .color(windColor()).owner(this));
            BossKit.schedule(level, 30, () -> {
                if (!isAlive()) return;
                GuardianShockwave.create(this, g, Element.WIND).radius(12F, 0.9F).damage(5F * mult())
                        .knock(1.6, 0.8).groundOnly().launch();
            });
        }
    }

    @Override
    protected void stageFx(ServerLevel level, int stage, int left) {
        super.stageFx(level, stage, left);
        for (int i = 0; i < 3; i++) {
            double a = left * 0.45 + i * Math.PI * 2 / 3;
            double r = 1.5 + (left % 20) * 0.1;
            level.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(a) * r, getY() + (left % 20) * 0.15, getZ() + Math.sin(a) * r,
                    1, 0, 0, 0, 0.01);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!dead) return; // morte cancelada por outro mod/evento
        // Sem o vento que o sustenta, cai durante a animação de morte
        setNoGravity(false);
        entityData.set(DATA_EXPOSED, false);
    }

    @Override
    protected void onFightReset() {
        exposedTicks = 0;
        entityData.set(DATA_EXPOSED, false);
    }

    @Override
    protected void deathFx(ServerLevel level, int t) {
        super.deathFx(level, t);
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, 0.8, 1.0, 0.8, 0.05);
        if (t % 10 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_DEATH, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    @Override
    protected void clientAmbient() {
        // Cauda de vento e penas soltas
        double y = getY() + 0.2;
        if (random.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.CLOUD, getX() + (random.nextDouble() - 0.5) * 0.6, y, getZ() + (random.nextDouble() - 0.5) * 0.6,
                    0, -0.03, 0);
        }
        double a = tickCount * 0.4;
        level().addParticle(ElementFx.dust(Element.WIND, 1.0F), getX() + Math.cos(a) * 0.9, y + 0.6, getZ() + Math.sin(a) * 0.9, 0, 0, 0);
        if (phase() >= 2 && random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.SMALL_GUST, getX() + (random.nextDouble() - 0.5) * 3, getY() + getBbHeight() * random.nextDouble(),
                    getZ() + (random.nextDouble() - 0.5) * 3, 0, 0, 0);
        }
    }

    /** Voando: "fly"; parado: "idle"; caído depois do mergulho: "exposed" (ofegante, asas no chão). */
    @Override
    protected RawAnimation baseAnimation(AnimationState<ElementalGuardian> state) {
        if (isExposed()) return animExposed;
        return getDeltaMovement().horizontalDistanceSqr() > 0.004 ? animFly : idleAnim();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Orbit", orbitAngle);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        orbitAngle = tag.getDouble("Orbit");
        setNoGravity(true);
    }
}
