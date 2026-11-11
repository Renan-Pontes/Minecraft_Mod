package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Maré, a Serpente das Profundezas (Guardiã da Água): naga anfíbia que prefere lutar de dentro d'água.
 * Na água nada rápido e se regenera; fora dela volta para a água mais próxima quando pode.
 * Golpes: rabada (círculo curto), onda que empurra (leque desenhado; +glifo Onda), jatos d'água (glifos),
 * redemoinhos (o funil é marcado antes de abrir), gêiseres, Prisão de Bolha (um círculo persegue o alvo e trava —
 * quem não sair fica preso numa bolha que se estoura a golpes) e, a partir da fase 2, Tempestade das Marés:
 * encharca todos e chama raios em círculos marcados sob cada um (Encharcado + Raio = Eletrocutar).
 * Respira debaixo d'água pela tag minecraft:can_breathe_under_water (merge do módulo).
 */
public class TideGuardianEntity extends ElementalGuardian {
    static final List<GuardianMove> MOVES = List.of(
            GuardianMove.of("tail_sweep", 24).range(0, 4.0).cooldown(20).weight(14),
            GuardianMove.of("tidal_wave", 36).range(0, 20).cooldown(110).weight(10).announce().aimUntil(1),
            GuardianMove.of("water_jets", 30).range(3, 30).cooldown(48).weight(13),
            GuardianMove.of("whirlpool", 36).range(0, 28).cooldown(280).weight(8).announce(),
            GuardianMove.of("geyser", 40).range(0, 24).cooldown(150).weight(9),
            GuardianMove.of("bubble_prison", 34).range(0, 26).cooldown(300).weight(9).announce(),
            GuardianMove.of("storm_call", 58).range(0, 30).cooldown(260).weight(11).phases(2, 3).announce(),
            leapMove());

    /** Cor dos raios da Tempestade das Marés. */
    private static final int STORM = 0x9AD8FF;

    private final RawAnimation animSwim;
    private final List<Vec3> geyserSpots = new ArrayList<>();
    private final List<Vec3> whirlSpots = new ArrayList<>();
    @Nullable private BlockPos waterPos;
    @Nullable private Telegraph.Shape sweepShape;
    private int sweepId = -1;
    private Vec3 waveDir = Vec3.ZERO;
    /** Quem já levou um raio nesta Tempestade (cada um leva no máx. um: o Eletrocutar atordoa, e atordoado não desvia). */
    private final Set<UUID> stormHit = new HashSet<>();

    public TideGuardianEntity(EntityType<? extends TideGuardianEntity> type, Level level) {
        super(type, level, MOVES);
        this.moveControl = new TideMoveControl(this);
        setPathfindingMalus(PathType.WATER, 0.0F);
        this.animSwim = loop("swim");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 380.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected double cullMargin() { return 3.0; }

    @Override
    public Element element() { return Element.WATER; }

    private int waterColor() {
        return Telegraph.color(Element.WATER);
    }

    @Override
    protected BossEvent.BossBarColor barColor(int phase) {
        return phase >= 3 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.BLUE;
    }

    @Override
    protected SoundEvent roarSound() { return SoundEvents.ELDER_GUARDIAN_CURSE; }

    @Override
    protected float roarPitch() { return 0.8F; }

    @Override
    protected SoundEvent getAmbientSound() {
        return isInWater() ? SoundEvents.ELDER_GUARDIAN_AMBIENT : SoundEvents.ELDER_GUARDIAN_AMBIENT_LAND;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isInWater() ? SoundEvents.ELDER_GUARDIAN_HURT : SoundEvents.ELDER_GUARDIAN_HURT_LAND;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return isInWater() ? SoundEvents.ELDER_GUARDIAN_DEATH : SoundEvents.ELDER_GUARDIAN_DEATH_LAND;
    }

    @Override
    protected boolean immuneTo(Holder<MobEffect> effect) {
        return effect.is(CoreRegistries.SOAKED.getKey());
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void addIdleGoals() {
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.7));
    }

    @Override
    protected double preferredRange() { return 3.0; }

    @Override
    protected String[] rangedRecipes() {
        return new String[] {"projectile homing water_blade soak", "projectile homing soak", "projectile soak"};
    }

    /** Na água a Maré nada (sem gravidade, com atrito de água); fora dela anda normalmente. */
    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance() && isInWater()) {
            double startY = getY();
            moveRelative(0.09F, input);
            move(MoverType.SELF, getDeltaMovement());
            Vec3 v = getDeltaMovement().scale(0.9);
            // Encostou na margem nadando: sobe para sair d'água (sem isso flutua preso na parede do lago)
            if (horizontalCollision && isFree(v.x, v.y + 0.6 - getY() + startY, v.z)) v = new Vec3(v.x, 0.3, v.z);
            setDeltaMovement(v);
        } else {
            super.travel(input);
        }
    }

    // =====================================================================================
    //  Ficar na água
    // =====================================================================================
    @Override
    protected void combatMovement(LivingEntity target, double dist) {
        if (isBusy()) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (tickCount % 40 == 0 || (waterPos == null && tickCount % 10 == 0)) waterPos = findWater(14);
        boolean inWater = isInWater();
        if (inWater && !target.isInWater() && dist <= 22) {
            // Luta de dentro d'água: só se aproxima da margem mais perto do alvo
            if (dist > 8 && (tickCount % 10 == 0 || getNavigation().isDone())) getNavigation().moveTo(target, 1.0);
            else if (dist <= 8) getNavigation().stop();
            return;
        }
        if (!inWater && waterPos != null && target.distanceToSqr(Vec3.atCenterOf(waterPos)) < 24 * 24) {
            if (tickCount % 10 == 0 || getNavigation().isDone()) {
                getNavigation().moveTo(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5, 1.2);
            }
            return;
        }
        super.combatMovement(target, dist);
    }

    /** Água mais perto (busca espaçada para ficar barata). */
    @Nullable
    private BlockPos findWater(int radius) {
        BlockPos c = blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int dy = -4; dy <= 3; dy++) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!level().getFluidState(p).is(FluidTags.WATER) || !level().getFluidState(p.below()).is(FluidTags.WATER)) continue;
                    double d = p.distSqr(c);
                    if (d < bestD) {
                        bestD = d;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }

    /**
     * Alvo que ainda pode desviar de uma marca no chão: preso numa bolha ou atordoado não se mexe, então não recebe
     * marcas novas (senão o golpe seria garantido).
     */
    private boolean canDodge(ServerLevel level, LivingEntity e) {
        return !e.hasEffect(CoreRegistries.STUNNED) && !TideBubble.isTrapped(level, e);
    }

    // =====================================================================================
    //  Golpes
    // =====================================================================================
    @Override
    protected void performMove(ServerLevel level, GuardianMove move, int t, @Nullable LivingEntity target) {
        switch (move.id) {
            case "tail_sweep" -> tailSweep(level, t);
            case "tidal_wave" -> tidalWave(level, t, target);
            case "water_jets" -> waterJets(level, t, target);
            case "whirlpool" -> whirlpool(level, t, target);
            case "geyser" -> geyser(level, t, target);
            case "bubble_prison" -> bubblePrison(level, t, target);
            case "storm_call" -> stormCall(level, t);
            default -> {}
        }
    }

    /** Rabada: um círculo curto em volta da Maré enche em 10 ticks; quem ainda estiver dentro leva o golpe. */
    private void tailSweep(ServerLevel level, int t) {
        float r = getBbWidth() * 0.5F + 4.2F;
        if (t == 2) {
            sweepShape = Telegraph.Shape.circle(position(), r);
            sweepId = Telegraph.show(level, Telegraph.Shape.circle(position(), r).warmup(10).color(waterColor()).owner(this).noRunes());
        }
        if (t == 6) level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.5F);
        if (t != 12 || sweepShape == null) return;
        Vec3 c = position();
        level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.5, c.z, 60, 3.0, 0.3, 3.0, 0.3);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0F, 0.6F);
        for (LivingEntity e : foesIn(level, sweepShape)) {
            if (e.hurt(damageSources().mobAttack(this), meleeDamage())) {
                e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 120, 0), this);
                BossSpells.knock(e, e.position().subtract(c), 1.6, 0.4);
            }
        }
        Telegraph.finish(level, sweepId);
        sweepShape = null;
    }

    /** Onda Gigante: o leque é desenhado 16 ticks antes e a onda corre por ele — saia pelos lados. */
    private void tidalWave(ServerLevel level, int t, @Nullable LivingEntity target) {
        double half = phase() >= 3 ? 70 : 45;
        if (t == 0) {
            waveDir = BossSpells.flatDir(position(), target != null ? target.position() : position().add(BossSpells.dirOf(bodyYaw())),
                    BossSpells.dirOf(bodyYaw()));
            // Fase 2+: a Onda dos glifos (forms_a) vai até ~22 blocos pelo meio do leque — o desenho cobre o alcance dela
            float reach = phase() >= 2 ? 23F : 20F;
            Telegraph.show(level, Telegraph.Shape.cone(position(), BossSpells.yawOf(waveDir), (float) (half * 2), reach)
                    .warmup(16).hold(phase() >= 2 ? 40 : 26).color(waterColor()).owner(this));
        }
        if (t < 16 && t % 2 == 0) {
            level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1.5, getZ(), 10, 1.0, 1.0, 1.0, 0.2);
        }
        if (t != 16) return;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 3.0F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.HOSTILE, 2.0F, 0.7F);
        BossKit.shake(level, position(), 24, 0.8F, 12);
        GuardianShockwave.create(this, position(), Element.WATER)
                .arc(waveDir, half)
                .radius(20F, 0.75F)
                .damage(8F * mult())
                .soak(160)
                .knock(2.0, 0.5)
                .launch();
        // Na fase 2+ a Maré também conjura a Onda dos glifos atrás da sua, na MESMA direção do leque
        if (phase() >= 2) {
            BossSpells.setYaw(this, BossSpells.yawOf(waveDir));
            setXRot(0);
            BossSpells.cast(this, Element.WATER, null, spellPower(), "wave tide amplify", "wave tide", "wave push");
        }
    }

    private void waterJets(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (target == null) return;
        if (t < 10 && t % 2 == 0) {
            Vec3 tip = position().add(BossSpells.dirOf(bodyYaw()).scale(1.5)).add(0, getBbHeight() * 0.9, 0);
            level.sendParticles(ParticleTypes.BUBBLE_POP, tip.x, tip.y, tip.z, 6, 0.3, 0.3, 0.3, 0.05);
        }
        if (t != 10 && t != 17 && t != 24) return;
        if (phase() >= 2) {
            BossSpells.cast(this, Element.WATER, target, spellPower(), "projectile split water_blade soak",
                    "projectile split water_blade", "projectile split soak");
        } else {
            BossSpells.cast(this, Element.WATER, target, spellPower(), "projectile accelerate water_blade",
                    "projectile accelerate soak");
        }
    }

    /** Redemoinho: o funil (azul) e o centro que afoga (vermelho) são marcados 24 ticks antes de abrir. */
    private void whirlpool(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t < 28 && t % 2 == 0) {
            double a = t * 0.6;
            level.sendParticles(ParticleTypes.SPLASH, getX() + Math.cos(a) * 2, getY() + 2.5, getZ() + Math.sin(a) * 2, 6, 0.2, 0.2, 0.2, 0.1);
        }
        int life = phase() >= 3 ? 220 : 160;
        if (t == 4) {
            whirlSpots.clear();
            List<LivingEntity> foes = new ArrayList<>(BossSpells.foesAround(this, position(), 28));
            foes.removeIf(e -> !canDodge(level, e));
            if (foes.isEmpty() && target != null && canDodge(level, target)) foes.add(target);
            int n = Math.min(foes.size(), phase());
            for (int i = 0; i < n; i++) {
                Vec3 s = groundAt(foes.get(i).position());
                whirlSpots.add(s);
                Telegraph.show(level, Telegraph.Shape.circle(s, (float) TideWhirlpool.RADIUS).warmup(24).color(waterColor())
                        .owner(this).noFlash());
                Telegraph.show(level, Telegraph.Shape.circle(s, (float) TideWhirlpool.CORE).warmup(24).hold(life)
                        .color(Telegraph.DANGER).owner(this).noRunes());
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.HOSTILE, 2.5F, 0.6F);
        }
        if (t > 4 && t < 28) {
            for (Vec3 s : whirlSpots) level.sendParticles(ParticleTypes.BUBBLE_POP, s.x, s.y + 0.1, s.z, 2, 1.5, 0.05, 1.5, 0.02);
        }
        if (t != 28) return;
        for (Vec3 s : whirlSpots) TideWhirlpool.spawn(this, s, life, 3F * mult());
        whirlSpots.clear();
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.5F);
    }

    /** Gêiser: marca até 3 alvos; 14 ticks depois a água explode para cima no círculo. */
    private void geyser(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t == 12) {
            geyserSpots.clear();
            for (LivingEntity e : BossSpells.foesAround(this, position(), 24)) {
                if (geyserSpots.size() >= 3) break;
                if (canDodge(level, e)) geyserSpots.add(groundAt(e.position()));
            }
            if (geyserSpots.isEmpty() && target != null && canDodge(level, target)) geyserSpots.add(groundAt(target.position()));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 2.0F, 0.6F);
            float dmg = 8F * mult();
            for (Vec3 s : geyserSpots) {
                Telegraph.delayed(level, Telegraph.Shape.circle(s, 2.4F).warmup(14).color(waterColor()).owner(this),
                        hits -> geyserBurst(level, s, dmg, hits));
            }
        }
        if (t > 12 && t < 26) {
            for (Vec3 s : geyserSpots) {
                level.sendParticles(ParticleTypes.BUBBLE_POP, s.x, s.y + 0.1, s.z, 4, 0.7, 0.05, 0.7, 0.02);
                level.sendParticles(ParticleTypes.SPLASH, s.x, s.y + 0.1, s.z, 3, 0.7, 0.05, 0.7, 0.05);
            }
        }
        if (t == 26) geyserSpots.clear();
    }

    private void geyserBurst(ServerLevel level, Vec3 s, float dmg, List<LivingEntity> hits) {
        if (!isAlive()) return;
        for (int h = 0; h < 8; h++) {
            level.sendParticles(ParticleTypes.SPLASH, s.x, s.y + h * 0.7, s.z, 14, 0.5, 0.3, 0.5, 0.3);
            level.sendParticles(ElementFx.dust(Element.WATER, 2.0F), s.x, s.y + h * 0.7, s.z, 6, 0.4, 0.3, 0.4, 0);
        }
        level.sendParticles(ParticleTypes.CLOUD, s.x, s.y + 5.5, s.z, 20, 1.0, 0.3, 1.0, 0.05);
        level.playSound(null, s.x, s.y, s.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 2.0F, 0.6F);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            BossSpells.hit(this, e, Element.WATER, dmg);
            e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 160, 0), this);
            e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 1.25, e.getDeltaMovement().z * 0.3);
            e.hurtMarked = true;
        }
    }

    /**
     * Prisão de Bolha: um círculo persegue cada alvo (até o nº da fase) e TRAVA nos últimos 12 ticks — saia dele.
     * Quem ficar dentro é preso numa bolha que flutua e afoga aos poucos; golpes (do preso ou dos aliados) a estouram.
     */
    private void bubblePrison(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t < 8 && t % 2 == 0) {
            Vec3 hands = position().add(BossSpells.dirOf(bodyYaw()).scale(1.6)).add(0, getBbHeight() * 0.65, 0);
            level.sendParticles(ParticleTypes.BUBBLE, hands.x, hands.y, hands.z, 12, 0.4, 0.4, 0.4, 0.05);
            level.sendParticles(ElementFx.dust(Element.WATER, 1.4F), hands.x, hands.y, hands.z, 6, 0.4, 0.4, 0.4, 0);
        }
        if (t != 8) return;
        List<LivingEntity> foes = new ArrayList<>();
        if (target != null && isFoe(target) && canDodge(level, target)) foes.add(target);
        for (LivingEntity e : BossSpells.foesAround(this, position(), 26)) {
            if (foes.size() >= phase()) break;
            if (!foes.contains(e) && canDodge(level, e)) foes.add(e);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.HOSTILE, 2.5F, 0.8F);
        int life = 50 + 10 * phase();
        float hp = 10F + 5F * (phase() - 1);
        float dmg = 2F * mult();
        for (LivingEntity e : foes) {
            int[] id = new int[1];
            id[0] = Telegraph.delayed(level, Telegraph.Shape.follow(e, 1.7F).warmup(40).lock(12).color(waterColor()).owner(this),
                    hits -> {
                        if (!isAlive()) return;
                        Vec3 at = Telegraph.centerOf(id[0]);
                        boolean caught = false;
                        for (LivingEntity h : hits) {
                            if (isFoe(h) && TideBubble.trap(this, h, life, hp, dmg)) caught = true;
                        }
                        if (!caught && at != null) {
                            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 30, 1.0, 0.4, 1.0, 0.2);
                            level.playSound(null, at.x, at.y, at.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.HOSTILE, 1.5F, 0.8F);
                        }
                    });
            if (e instanceof ServerPlayer sp) {
                sp.displayClientMessage(line("hint.bubble_prison").copy().withStyle(net.minecraft.ChatFormatting.AQUA), true);
            }
        }
    }

    /**
     * Tempestade das Marés: encharca todos; depois cada um recebe um círculo de raio (20 ticks) onde está.
     * Saia do círculo antes que ele encha (fase 3: um raio extra perto de cada um).
     */
    private void stormCall(ServerLevel level, int t) {
        if (t == 0) stormHit.clear();
        if (t < 10 && t % 2 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() + 1.5, getZ(), 10, 1.5, 0.3, 1.5, 0.02);
        }
        if (t == 10) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WEATHER_RAIN, SoundSource.HOSTILE, 3.0F, 0.6F);
            for (LivingEntity e : BossSpells.foesAround(this, position(), 30)) {
                e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 220, 0), this);
                level.sendParticles(ParticleTypes.FALLING_WATER, e.getX(), e.getY() + 5, e.getZ(), 50, 2.0, 0.5, 2.0, 0);
                level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 6, e.getZ(), 12, 2.0, 0.3, 2.0, 0.01);
            }
        }
        if (t > 10 && t < 52 && t % 3 == 0) {
            for (LivingEntity e : BossSpells.foesAround(this, position(), 30)) {
                level.sendParticles(ParticleTypes.FALLING_WATER, e.getX(), e.getY() + 5, e.getZ(), 12, 2.0, 0.5, 2.0, 0);
            }
        }
        boolean mark = t == 10 || t == 22 || (phase() >= 3 && t == 32);
        if (!mark) return;
        float dmg = 7F * mult();
        int count = 0;
        for (LivingEntity e : BossSpells.foesAround(this, position(), 30)) {
            // Quem já levou o raio desta tempestade, está atordoado ou preso numa bolha não recebe nova marca
            if (stormHit.contains(e.getUUID()) || !canDodge(level, e)) continue;
            if (count++ >= 4) break;
            List<Vec3> spots = new ArrayList<>();
            spots.add(groundAt(e.position()));
            if (phase() >= 3) {
                double a = random.nextDouble() * Math.PI * 2, d = 3.0 + random.nextDouble() * 1.5;
                spots.add(groundAt(e.position().add(Math.cos(a) * d, 0, Math.sin(a) * d)));
            }
            for (Vec3 s : spots) {
                Telegraph.delayed(level, Telegraph.Shape.circle(s, 2.2F).warmup(20).color(STORM).owner(this),
                        hits -> lightning(level, s, dmg, hits));
            }
        }
        if (count > 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 0.8F, 1.6F);
    }

    private void lightning(ServerLevel level, Vec3 s, float dmg, List<LivingEntity> hits) {
        if (!isAlive()) return;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(s);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, s.x, s.y + 0.5, s.z, 30, 1.0, 0.6, 1.0, 0.15);
        for (LivingEntity e : hits) {
            if (!isFoe(e) || !stormHit.add(e.getUUID())) continue;
            // Encharcado + Raio = Eletrocutar (dano dobrado e atordoa — reação do núcleo)
            BossSpells.hit(this, e, Element.LIGHTNING, dmg);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1, e.getZ(), 20, 0.4, 0.8, 0.4, 0.1);
        }
    }

    // =====================================================================================
    //  Passivos e visual
    // =====================================================================================
    @Override
    protected void passiveTick(ServerLevel level, @Nullable LivingEntity target) {
        if (isInWater() && !isDeadOrDying() && tickCount % 20 == 0 && getHealth() < getMaxHealth()) {
            heal(phase() >= 2 ? 2F : 1F);
            level.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 1.5, getZ(), 6, 0.8, 0.8, 0.8, 0.02);
        }
        if (target == null && !isInWater() && tickCount % 60 == 0 && !isBusy()) {
            waterPos = findWater(16);
            if (waterPos != null) getNavigation().moveTo(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5, 0.9);
        }
    }

    @Override
    protected void introImpact(ServerLevel level) {
        super.introImpact(level);
        level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 200, 4.0, 0.5, 4.0, 0.5);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 3.0F, 0.5F);
    }

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 150, 3.0, 1.5, 3.0, 0.4);
        if (phase == 3) {
            // A maré estoura em volta ao fim do rugido: o anel avisa e dá para pular
            Vec3 c = position();
            Telegraph.show(level, Telegraph.Shape.ring(c, getBbWidth() * 0.5F, 12F).warmup(30).hold(16).vertical(0.5, 1.0)
                    .color(waterColor()).owner(this));
            BossKit.schedule(level, 30, () -> {
                if (!isAlive()) return;
                GuardianShockwave.create(this, c, Element.WATER).radius(12F, 0.7F).damage(6F * mult()).soak(200).knock(1.4, 0.4)
                        .groundOnly().launch();
            });
        }
    }

    @Override
    protected void stageFx(ServerLevel level, int stage, int left) {
        super.stageFx(level, stage, left);
        double a = left * 0.5;
        for (int i = 0; i < 3; i++) {
            double b = a + i * Math.PI * 2 / 3;
            level.sendParticles(ParticleTypes.SPLASH, getX() + Math.cos(b) * 2.0, getY() + (left % 25) / 25.0 * getBbHeight(),
                    getZ() + Math.sin(b) * 2.0, 3, 0.1, 0.1, 0.1, 0.05);
        }
    }

    @Override
    protected void deathFx(ServerLevel level, int t) {
        super.deathFx(level, t);
        level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + getBbHeight() * 0.6, getZ(), 8, 0.8, 0.8, 0.8, 0.1);
        if (t % 12 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_DEATH, SoundSource.HOSTILE, 1.5F, 0.8F);
    }

    @Override
    protected void clientAmbient() {
        double w = getBbWidth() * 0.5, h = getBbHeight();
        if (random.nextInt(2) == 0) {
            level().addParticle(isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.DRIPPING_WATER,
                    getX() + (random.nextDouble() - 0.5) * 2 * w, getY() + h * Mth.lerp(random.nextFloat(), 0.4F, 1.0F),
                    getZ() + (random.nextDouble() - 0.5) * 2 * w, 0, 0.02, 0);
        }
        if (phase() >= 3 && random.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.FALLING_WATER, getX() + (random.nextDouble() - 0.5) * 4, getY() + h + 1.5,
                    getZ() + (random.nextDouble() - 0.5) * 4, 0, 0, 0);
        }
        if (random.nextInt(8) == 0) {
            level().addParticle(ElementFx.dust(Element.WATER, 1.0F), getX() + (random.nextDouble() - 0.5) * 2 * w,
                    getY() + h * random.nextFloat(), getZ() + (random.nextDouble() - 0.5) * 2 * w, 0, 0, 0);
        }
    }

    @Override
    protected RawAnimation baseAnimation(AnimationState<ElementalGuardian> state) {
        if (isInWater()) return state.isMoving() || getDeltaMovement().horizontalDistanceSqr() > 0.002 ? animSwim : idleAnim();
        return state.isMoving() ? walkAnim() : idleAnim();
    }

    // =====================================================================================
    //  Controle de movimento anfíbio
    // =====================================================================================
    /** Nada em 3D na água (sobe e desce até o ponto do caminho); em terra usa o controle padrão. */
    static class TideMoveControl extends MoveControl {
        TideMoveControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (!mob.isInWater()) {
                super.tick();
                return;
            }
            if (operation == Operation.MOVE_TO && !mob.getNavigation().isDone()) {
                double dx = wantedX - mob.getX(), dy = wantedY - mob.getY(), dz = wantedZ - mob.getZ();
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < 2.5E-7) {
                    mob.setZza(0.0F);
                    return;
                }
                float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                mob.setYRot(rotlerp(mob.getYRot(), yaw, 12.0F));
                mob.yBodyRot = mob.getYRot();
                float speed = (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)) * 1.6F;
                mob.setSpeed(speed);
                double vy = Mth.clamp(dy / Math.sqrt(d2), -1.0, 1.0) * 0.04;
                mob.setDeltaMovement(mob.getDeltaMovement().add(0, vy, 0));
            } else {
                mob.setSpeed(0.0F);
                mob.setXxa(0.0F);
                mob.setYya(0.0F);
                mob.setZza(0.0F);
            }
        }
    }
}
