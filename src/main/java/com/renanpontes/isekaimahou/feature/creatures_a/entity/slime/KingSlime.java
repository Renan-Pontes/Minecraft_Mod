package com.renanpontes.isekaimahou.feature.creatures_a.entity.slime;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.creatures_a.CaRegistry;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.BossFeatures;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Rei Slime (minichefe). Gigante de coroa:
 * <ul>
 *   <li>apresentação (quica, a coroa salta) com escala de grupo; barra de chefe e cutscene "boss_intro";</li>
 *   <li>Esmagamento Real: salta no alvo — o ponto de queda é marcado no chão durante todo o voo; da fase 2 em diante
 *       solta também uma onda gelatinosa (anel que dá para pular);</li>
 *   <li>Chuva de Gosma: cospe gel para o céu e círculos marcam onde ela cai (sobre cada jogador e em volta); da fase
 *       2 em diante a gosma fica no chão grudando (lentidão);</li>
 *   <li>a cada 25% de vida perdida se divide: encolhe um pouco e solta slimes menores;</li>
 *   <li>anti-pilar: alvo inalcançável → salta por cima do pilar ou faz chover gosma nele;</li>
 *   <li>tenacidade contra controle, não monta em barcos, recompensa de grupo; ao morrer derrete e vira slimes pequenos.</li>
 * </ul>
 */
public class KingSlime extends MahouSlime {
    public static final double MAX_HEALTH = 180.0;
    private static final int START_SIZE = 6;
    private static final int GEL = 0x55B6FF;
    private static final int INTRO_TICKS = 40;
    private static final int DEATH_TICKS = 40;
    /** Tick da animação "rain" em que o gel é cuspido; a anim inteira dura 28 ticks. */
    private static final int RAIN_SPIT = 14;
    private static final int RAIN_LENGTH = 28;
    /** Aquecimento dos círculos da Chuva de Gosma (do cuspe até a gosma cair). */
    private static final int RAIN_WARMUP = 30;

    private static final RawAnimation LEAP = RawAnimation.begin().thenPlay("animation.king_slime.leap");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("animation.king_slime.slam");
    private static final RawAnimation SPLIT = RawAnimation.begin().thenPlay("animation.king_slime.split");
    private static final RawAnimation INTRO = RawAnimation.begin().thenPlay("animation.king_slime.intro");
    private static final RawAnimation RAIN = RawAnimation.begin().thenPlay("animation.king_slime.rain");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.king_slime.death");

    private final BossFeatures boss = new BossFeatures(this, "king_slime", 0x5FB8FF, BossEvent.BossBarColor.BLUE, 120,
            e -> e instanceof MahouSlime).introLength(INTRO_TICKS);
    private int slamCooldown = 80;
    private int rainCooldown = 140;
    private boolean leaping;
    private int leapTicks;
    private int leapFlight;
    private int leapMarker = -1;
    private float leapRadius;
    /** Centro do círculo de pouso marcado no chão. */
    @Nullable
    private Vec3 leapMark;
    private int phase;
    private int introTicks;
    private int rainTicks;
    /** Fúria (metade da vida): para de atacar enquanto a cutscene de fase toca (a câmera não deixa ver os avisos). */
    private int pauseTicks;

    public KingSlime(EntityType<? extends KingSlime> type, Level level) {
        super(type, level, SlimeKind.KING);
        boss.onEngage(this::startIntro);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        setSize(START_SIZE, true);
        return result;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Ocupado (intro, salto, cuspe): para de pular por conta própria — senão o pulo comum estraga a mira.
        // (Chamado no construtor do Mob: a meta só lê o estado do rei em jogo.)
        goalSelector.addGoal(0, new Goal() {
            {
                setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
            }

            @Override
            public boolean canUse() {
                return isBusy();
            }

            @Override
            public boolean requiresUpdateEveryTick() {
                return true;
            }
        });
    }

    private boolean isBusy() {
        return introTicks > 0 || rainTicks > 0 || pauseTicks > 0 || leaping;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) serverTick();
    }

    private void serverTick() {
        // Morrendo: sem pulo/onda de choque póstuma (a animação de morte ainda roda tick()).
        if (!isAlive() || !(level() instanceof ServerLevel sl)) return;
        boss.tick(isBusy());
        LivingEntity target = getTarget();
        if (!boss.engaged() && target instanceof Player p && !p.isCreative() && !p.isSpectator() && distanceTo(p) < 32) boss.engage();
        if (introTicks > 0) {
            introTicks--;
            if (introTicks % 8 == 0) gelBurst(sl, position().add(0, getBbHeight() * 0.5, 0), 12, 0.3);
            return;
        }
        // Fases: a cada 25% de vida perdida o rei se divide.
        float frac = getHealth() / getMaxHealth();
        int wanted = frac <= 0.25F ? 3 : frac <= 0.5F ? 2 : frac <= 0.75F ? 1 : 0;
        while (phase < wanted && isAlive()) {
            phase++;
            split();
        }

        if (slamCooldown > 0) slamCooldown--;
        if (rainCooldown > 0) rainCooldown--;
        if (leaping) {
            tickLeap(sl);
            return;
        }
        if (rainTicks > 0) {
            tickRain(sl);
            return;
        }
        if (pauseTicks > 0) {
            pauseTicks--;
            return;
        }
        if (target == null || !target.isAlive() || boss.controlled()) return;
        if (boss.unreachable(target, 40)) {
            antiCheese(sl, target);
            return;
        }
        if (!onGround()) return;
        double d = distanceTo(target);
        if (rainCooldown <= 0 && d < 24.0) {
            startRain();
        } else if (slamCooldown <= 0 && d > 3.0 && d < 22.0 && hasLineOfSight(target)) {
            startLeap(sl, target);
        }
    }

    // ------------------------------------------------------------------ apresentação

    private void startIntro() {
        BossKit.scaleToParty(this, 48);
        introTicks = INTRO_TICKS;
        triggerAnim("action", "intro");
        boss.announce("intro");
        BossKit.roar(this, SoundEvents.SLIME_SQUISH, 0.4F);
        playSound(SoundEvents.SLIME_JUMP, 2.5F, 0.4F);
    }

    // ------------------------------------------------------------------ Esmagamento Real

    /** Raio do esmagamento (o rei encolhe a cada divisão): 4,7 no tamanho 6, 3,7 no tamanho 3. */
    private float slamRadius() {
        return (float) (2.6 + getSize() * 0.35);
    }

    private void startLeap(ServerLevel sl, LivingEntity target) {
        boss.announce("slam");
        triggerAnim("action", "leap");
        leapRadius = slamRadius();
        // Arco alto o bastante para dar tempo de sair do círculo andando, mesmo de perto.
        leapFlight = BossKit.leapAt(this, target, BossFeatures.fairLeapPower(this, target.position(), leapRadius, 0.85));
        if (getMoveControl() instanceof SlimeHopControl hop) hop.setDirection(getYRot(), false);
        leapMark = target.position();
        leapMarker = Telegraph.show(sl, Telegraph.Shape.circle(leapMark, leapRadius).warmup(leapFlight)
                .color(Telegraph.DANGER).owner(this));
        leaping = true;
        leapTicks = 0;
        slamCooldown = (phase >= 2 ? 90 : 120) + random.nextInt(50);
        playSound(SoundEvents.SLIME_JUMP, 2.0F, 0.45F);
    }

    private void tickLeap(ServerLevel sl) {
        leapTicks++;
        if ((onGround() && leapTicks > 3) || leapTicks > leapFlight + 30 || isInWater()) {
            leaping = false;
            landSlam(sl);
        }
    }

    private void landSlam(ServerLevel sl) {
        triggerAnim("action", "slam");
        if (leapMarker >= 0) Telegraph.finish(sl, leapMarker);
        leapMarker = -1;
        Vec3 c = position();
        float r = BossFeatures.landingRadius(this, leapMark, leapRadius);
        leapMark = null;
        float dmg = 10.0F * BossKit.damageScale(this);
        MobEffectInstance slow = new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1);
        for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(c, r))) {
            if (!boss.isFoe(e)) continue;
            float falloff = (float) (1.0 - 0.4 * Math.min(1.0, e.position().distanceTo(c) / r));
            boss.hurtFoe(e, dmg * falloff, c, 1.4 * falloff, slow);
        }
        BossFeatures.groundRing(sl, c, r, gel());
        BossKit.shake(sl, c, 24, 1.2F, 14);
        playSound(SoundEvents.SLIME_SQUISH, 3.0F, 0.45F);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.7F);
        if (phase >= 2) gelWave(sl, c, r);
    }

    /** Fase 2+: onda gelatinosa rasteira logo depois do pouso (anel; pular por cima evita). */
    private void gelWave(ServerLevel sl, Vec3 c, float inner) {
        float outer = inner + 4.0F;
        float dmg = 6.0F * BossKit.damageScale(this);
        Telegraph.delayed(sl, Telegraph.Shape.ring(c, inner, outer).warmup(12).vertical(0.5, 1.0).color(Telegraph.WARNING).owner(this),
                hits -> {
                    for (LivingEntity e : hits) {
                        if (boss.isFoe(e)) boss.hurtFoe(e, dmg, c, 1.0, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                    }
                    BossFeatures.ringFx(sl, c, (inner + outer) * 0.5, gel());
                    BossFeatures.ringFx(sl, c, outer, ParticleTypes.SPLASH);
                    sl.playSound(null, c.x, c.y, c.z, SoundEvents.SLIME_SQUISH, getSoundSource(), 2.0F, 0.6F);
                });
    }

    // ------------------------------------------------------------------ Chuva de Gosma

    private void startRain() {
        rainTicks = 1;
        boss.announce("rain");
        triggerAnim("action", "rain");
        rainCooldown = (phase >= 2 ? 150 : 200) + random.nextInt(60);
        playSound(SoundEvents.SLIME_SQUISH, 2.0F, 0.5F);
    }

    private void tickRain(ServerLevel sl) {
        rainTicks++;
        if (rainTicks == RAIN_SPIT) spit(sl);
        if (rainTicks >= RAIN_LENGTH) rainTicks = 0;
    }

    /** Cospe o gel para o céu e marca onde cada gota vai cair. */
    private void spit(ServerLevel sl) {
        Vec3 top = position().add(0, getBbHeight(), 0);
        for (int i = 0; i < 30; i++) {
            sl.sendParticles(gel(), top.x, top.y, top.z, 0, (random.nextDouble() - 0.5) * 0.4, 1.0, (random.nextDouble() - 0.5) * 0.4, 1.1);
        }
        sl.sendParticles(ParticleTypes.SPLASH, top.x, top.y, top.z, 40, getBbWidth() * 0.3, 0.2, getBbWidth() * 0.3, 0.3);
        playSound(SoundEvents.SLIME_SQUISH, 2.5F, 0.4F);
        playSound(SoundEvents.HONEY_BLOCK_BREAK, 2.0F, 0.5F);

        List<Vec3> spots = new ArrayList<>();
        List<ServerPlayer> party = BossKit.partyAround(this, 28);
        for (ServerPlayer p : party) {
            if (!p.isCreative()) spots.add(p.position());
        }
        LivingEntity target = getTarget();
        if (target != null && !(target instanceof Player) && distanceTo(target) < 28) spots.add(target.position());
        // Gotas extras espalhadas em volta dos jogadores e do próprio rei (mais a cada fase).
        int extra = 2 + 2 * phase;
        int aimed = spots.size();
        for (int i = 0; i < extra; i++) {
            boolean nearPlayer = aimed > 0 && random.nextBoolean();
            Vec3 base = nearPlayer ? spots.get(random.nextInt(aimed)) : position();
            double a = random.nextDouble() * Math.PI * 2;
            double dist = nearPlayer ? 2.5 + random.nextDouble() * 4 : 4 + random.nextDouble() * 8;
            Vec3 g = BossFeatures.groundAt(sl, base.x + Math.cos(a) * dist, base.y, base.z + Math.sin(a) * dist);
            if (g != null) spots.add(g);
        }
        float radius = phase >= 3 ? 2.6F : 2.2F;
        float dmg = 6.0F * BossKit.damageScale(this);
        int puddle = phase >= 2 ? 60 : 0;
        for (Vec3 s : spots) dropGoo(sl, s, radius, dmg, puddle);
    }

    private void dropGoo(ServerLevel sl, Vec3 spot, float radius, float dmg, int puddle) {
        Telegraph.Shape shape = Telegraph.Shape.circle(spot, radius).warmup(RAIN_WARMUP).hold(puddle).color(GEL).owner(this);
        Telegraph.delayed(sl, shape, hits -> {
            for (LivingEntity e : hits) {
                if (!boss.isFoe(e)) continue;
                if (boss.hurtFoe(e, dmg, spot, 0.3, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1))) {
                    e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 100, 0), this);
                }
            }
            gelBurst(sl, spot.add(0, 0.2, 0), 26, radius * 0.4);
            sl.sendParticles(ParticleTypes.SPLASH, spot.x, spot.y + 0.2, spot.z, 30, radius * 0.4, 0.1, radius * 0.4, 0.2);
            sl.playSound(null, spot.x, spot.y, spot.z, SoundEvents.SLIME_BLOCK_FALL, getSoundSource(), 1.6F, 0.6F);
        });
        // Gotas caindo do céu nos últimos ticks do aviso.
        for (int t = RAIN_WARMUP - 9; t < RAIN_WARMUP; t += 3) {
            BossKit.schedule(sl, t, () -> {
                if (!isAlive()) return;
                for (int i = 0; i < 4; i++) {
                    sl.sendParticles(gel(), spot.x + (random.nextDouble() - 0.5) * radius, spot.y + 7, spot.z + (random.nextDouble() - 0.5) * radius,
                            0, 0, -1, 0, 0.9);
                }
            });
        }
        // Fase 2+: a gosma fica grudando no chão por 3 s.
        for (int t = RAIN_WARMUP + 10; t <= RAIN_WARMUP + puddle; t += 10) {
            BossKit.schedule(sl, t, () -> {
                if (!isAlive()) return;
                for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(spot, radius))) {
                    if (boss.isFoe(e)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), this);
                }
                sl.sendParticles(ParticleTypes.ITEM_SLIME, spot.x, spot.y + 0.1, spot.z, 5, radius * 0.4, 0.02, radius * 0.4, 0.0);
            });
        }
    }

    // ------------------------------------------------------------------ anti-pilar

    /**
     * Alvo inalcançável há 2 s. Lá no alto (pilar, torre): a Chuva de Gosma cai do céu em cima dele (o rei é largo
     * demais para pousar num pilar). Perto do chão (barco, buraco, cerca): salta em cima.
     */
    private void antiCheese(ServerLevel sl, LivingEntity target) {
        double dy = target.getY() - getY();
        double dh = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        if (dy <= 4 && dh < 24 && onGround()) {
            startLeap(sl, target);
        } else if (dh < 28) {
            startRain();
        }
    }

    // ------------------------------------------------------------------ divisão / morte

    /** Divide-se: solta 2 slimes médios (variedades do pântano) e encolhe. */
    private void split() {
        if (!(level() instanceof ServerLevel sl)) return;
        // Na metade da vida a fala é a da fúria (com cutscene de fase e uma pausa do tamanho dela); nas outras, a da divisão.
        if (phase == 2) {
            boss.phaseChange("rage");
            pauseTicks = BossFeatures.PHASE_CUTSCENE_TICKS;
        } else {
            boss.announce("split");
        }
        triggerAnim("action", "split");
        LivingEntity target = getTarget();
        int count = phase == 3 ? 3 : 2;
        for (int i = 0; i < count; i++) {
            EntityType<MahouSlime> type = switch (random.nextInt(3)) {
                case 0 -> CaRegistry.WATER_SLIME.get();
                case 1 -> CaRegistry.POISON_JELLY.get();
                default -> CaRegistry.SLIME.get();
            };
            MahouSlime child = type.create(sl);
            if (child == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            child.moveTo(getX() + Math.cos(a) * getBbWidth() * 0.4, getY() + getBbHeight() * 0.6, getZ() + Math.sin(a) * getBbWidth() * 0.4,
                    random.nextFloat() * 360F, 0F);
            child.finalizeSpawn(sl, sl.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            child.setSize(2, true);
            child.setDeltaMovement(Math.cos(a) * 0.5, 0.5, Math.sin(a) * 0.5);
            if (target != null) child.setTarget(target);
            sl.addFreshEntity(child);
        }
        setSize(Math.max(3, START_SIZE - phase), false);
        gelBurst(sl, position().add(0, getBbHeight() * 0.5, 0), 60, getBbWidth() * 0.4);
        playSound(SoundEvents.SLIME_SQUISH, 2.5F, 0.6F);
        playSound(SoundEvents.HONEY_BLOCK_SLIDE, 2.0F, 0.5F);
    }

    @Override
    public void die(DamageSource source) {
        // "dead" só vira true se a morte foi processada de verdade (o evento pode cancelar; die() pode ser chamado 2x).
        boolean wasDead = dead;
        super.die(source);
        if (!level().isClientSide && !wasDead && dead) {
            boss.onDeath();
            leaping = false;
            rainTicks = 0;
            playSound(SoundEvents.SLIME_DEATH, 3.0F, 0.4F);
        }
    }

    /** Morte animada: derrete por 2 s (a vanilla tomba e some em 1 s) e então se desfaz em slimes pequeninos. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        if (deathTime % 4 == 0) {
            sl.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.2, getZ(), 6, getBbWidth() * 0.45, 0.05, getBbWidth() * 0.45, 0.0);
            if (deathTime % 12 == 0) playSound(SoundEvents.HONEY_BLOCK_SLIDE, 1.5F, 0.5F);
        }
        if (deathTime >= DEATH_TICKS && !isRemoved()) {
            burstIntoSlimes(sl);
            sl.broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    /** Desfaz-se em slimes azuis pequeninos (inofensivos, são neutros). */
    private void burstIntoSlimes(ServerLevel sl) {
        for (int i = 0; i < 4; i++) {
            MahouSlime child = CaRegistry.SLIME.get().create(sl);
            if (child == null) continue;
            double a = Math.PI * 0.5 * i;
            child.moveTo(getX() + Math.cos(a), getY() + 0.5, getZ() + Math.sin(a), random.nextFloat() * 360F, 0F);
            child.setSize(1, true);
            child.setDeltaMovement(Math.cos(a) * 0.3, 0.4, Math.sin(a) * 0.3);
            sl.addFreshEntity(child);
        }
        gelBurst(sl, position().add(0, 0.4, 0), 50, getBbWidth() * 0.3);
        sl.sendParticles(ParticleTypes.GLOW, getX(), getY() + 0.6, getZ(), 40, 0.6, 0.4, 0.6, 0.1);
        playSound(SoundEvents.SLIME_SQUISH, 2.5F, 0.8F);
    }

    // ------------------------------------------------------------------ dano / efeitos

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !boss.engaged() && source.getEntity() instanceof Player p && !p.isCreative() && !p.isSpectator()) {
            boss.engage();
        }
        // Apresentação: invulnerável (a cena é de graça para todos).
        if (introTicks > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        MobEffectInstance e = level().isClientSide ? effect : boss.tenacity(effect);
        boolean added = e != null && super.addEffect(e, source);
        // Atordoado antes de cuspir: a Chuva de Gosma é interrompida.
        if (added && !level().isClientSide && BossFeatures.interrupts(e) && rainTicks > 0 && rainTicks < RAIN_SPIT) rainTicks = 0;
        return added;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        // Barco/carrinho seguraria o rei parado (trapaça clássica).
        return false;
    }

    @Override
    protected boolean isDealsDamage() {
        // Apresentação: ninguém se machuca encostando (a cena é invulnerável dos dois lados).
        return introTicks <= 0 && super.isDealsDamage();
    }

    private ParticleOptions gel() {
        return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(CaRegistry.SLIME_GEL.get()));
    }

    private void gelBurst(ServerLevel sl, Vec3 at, int count, double spread) {
        sl.sendParticles(gel(), at.x, at.y, at.z, count, spread, spread * 0.6, spread, 0.25);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !boss.engaged() && !BossKit.isTrialBoss(this);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        boss.startSeen(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        boss.stopSeen(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        boss.onNameChanged();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        boss.save(tag);
        tag.putInt("Phase", phase);
        tag.putInt("SlamCooldown", slamCooldown);
        tag.putInt("RainCooldown", rainCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boss.load(tag);
        phase = tag.getInt("Phase");
        slamCooldown = tag.getInt("SlamCooldown");
        rainCooldown = tag.contains("RainCooldown") ? tag.getInt("RainCooldown") : 140;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("leap", LEAP)
                .triggerableAnim("slam", SLAM)
                .triggerableAnim("split", SPLIT)
                .triggerableAnim("intro", INTRO)
                .triggerableAnim("rain", RAIN));
        // Último controlador: na morte sobrepõe todos os outros e segura o último quadro.
        controllers.add(new AnimationController<>(this, "death", 2,
                state -> isDeadOrDying() ? state.setAndContinue(DEATH) : PlayState.STOP));
    }
}
