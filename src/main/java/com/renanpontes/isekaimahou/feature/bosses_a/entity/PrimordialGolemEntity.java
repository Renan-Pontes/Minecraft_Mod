package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Golem Primordial, o Colosso da Terra (Guardião da Terra).
 * Fase 1: esmagar (cone curto), terremoto que atordoa quem está no chão (anel no chão — pule para desviar),
 * arremesso de rochas (o ponto de queda é marcado), espinhos dos glifos, Agarrão de Raízes (círculo em volta),
 * Pilares Ascendentes (linha de pilares que sobem um após o outro) e Arremesso de Placa (faixa marcada).
 * Fase 2 (60%): ESCUDO DE PEDRA — fica parado, só magia (dano de feitiço) racha o escudo; ataques físicos não
 * fazem nada. Enquanto isso se regenera. Quebrado, o Golem cambaleia (vulnerável, +50% de dano).
 * Fase 3 (25%): Avalanche — investidas em linha marcada; bater numa parede o deixa tonto.
 */
public class PrimordialGolemEntity extends ElementalGuardian {
    static final List<GuardianMove> MOVES = List.of(
            GuardianMove.of("smash", 28).range(0, 4.5).cooldown(20).weight(15).aimUntil(5),
            GuardianMove.of("quake", 42).range(0, 14).cooldown(150).weight(10).announce(),
            GuardianMove.of("rock_throw", 40).range(5, 36).cooldown(80).weight(13),
            GuardianMove.of("earth_spike", 30).range(2, 28).cooldown(70).weight(10),
            GuardianMove.of("root_grasp", 32).range(0, 8).cooldown(160).weight(8),
            GuardianMove.of("rising_pillars", 44).range(3, 26).cooldown(150).weight(11).announce().aimUntil(6),
            GuardianMove.of("slab_throw", 40).range(6, 30).cooldown(130).weight(10).announce().aimUntil(14),
            GuardianMove.of("charge", 50).range(6, 24).cooldown(200).weight(11).phases(3, 3).announce().selfMoving(),
            // v1.3 ASSINATURA — Terra Partida: fendas rasgam o chão da arena em raios a partir do Golem
            GuardianMove.of("shatter", 64).anim("quake").range(0, 20).cooldown(320).weight(12).announce(),
            leapMove());

    /** Comprimento das fendas da Terra Partida. */
    private static final float FAULT_LENGTH = 17F;
    private static final float FAULT_WIDTH = 2.4F;

    /** Velocidade da placa arremessada (blocos/tick). */
    private static final float SLAB_SPEED = 1.5F;
    private static final EntityDataAccessor<Float> DATA_SHIELD = SynchedEntityData.defineId(PrimordialGolemEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_STAGGER = SynchedEntityData.defineId(PrimordialGolemEntity.class, EntityDataSerializers.BOOLEAN);

    private final RawAnimation animShield;
    private final RawAnimation animStunned;
    private float shieldHp;
    private float shieldMax;
    private int staggerTicks;
    private Vec3 chargeDir = Vec3.ZERO;
    private final Set<UUID> chargeHit = new HashSet<>();
    private final Map<UUID, Integer> shieldWarned = new HashMap<>();
    @Nullable private Telegraph.Shape smashShape;
    private int smashId = -1;
    @Nullable private GolemSlab slab;
    private Vec3 slabFrom = Vec3.ZERO;
    private Vec3 slabTo = Vec3.ZERO;

    public PrimordialGolemEntity(EntityType<? extends PrimordialGolemEntity> type, Level level) {
        super(type, level, MOVES);
        this.animShield = loop("shield");
        this.animStunned = loop("stunned");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.ARMOR, 18.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 17.0)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected double cullMargin() { return 1.6; }

    @Override
    public Element element() { return Element.EARTH; }

    private int earthColor() {
        return Telegraph.color(Element.EARTH);
    }

    @Override
    protected BossEvent.BossBarColor barColor(int phase) {
        return phase >= 3 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN;
    }

    @Override
    protected SoundEvent roarSound() { return SoundEvents.RAVAGER_ROAR; }

    @Override
    protected float roarPitch() { return 0.4F; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }

    /** O Golem é mudo; o que se ouve dele são os passos que fazem o chão tremer. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.8F, 0.45F);
    }

    @Override
    protected void addIdleGoals() {
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5));
    }

    @Override
    protected double preferredRange() { return 3.0; }

    @Override
    protected String[] rangedRecipes() {
        return new String[] {"projectile homing rock amplify", "projectile homing rock", "projectile rock"};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SHIELD, 0F);
        builder.define(DATA_STAGGER, false);
    }

    /** Escudo de pedra ativo (o renderer mostra a casca de pedra). */
    public boolean isShielded() { return entityData.get(DATA_SHIELD) > 0F; }

    /** Fração do escudo que resta (0..1): a casca racha conforme cai. */
    public float shieldFraction() { return entityData.get(DATA_SHIELD); }

    public boolean isStaggered() { return entityData.get(DATA_STAGGER); }

    // =====================================================================================
    //  Escudo de pedra
    // =====================================================================================
    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        if (phase == 2) raiseShield(level);
        if (phase == 3) {
            level.sendParticles(ParticleTypes.WAX_ON, getX(), getY() + getBbHeight() * 0.7, getZ(), 60, 1.5, 1.0, 1.5, 0.2);
        }
    }

    private void raiseShield(ServerLevel level) {
        shieldMax = getMaxHealth() * 0.2F;
        shieldHp = shieldMax;
        entityData.set(DATA_SHIELD, 1F);
        setBarName(line("shield_bar"));
        setBarColor(BossEvent.BossBarColor.WHITE);
        Announce.send(this, line("shield_up"), element().color, Announce.STYLE_BOSS);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                getX(), getY() + 2, getZ(), 150, 2.0, 2.0, 2.0, 0.2);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0F, 0.4F);
    }

    private void breakShield(ServerLevel level) {
        shieldHp = 0;
        entityData.set(DATA_SHIELD, 0F);
        staggerTicks = 100;
        entityData.set(DATA_STAGGER, true);
        cancelMove();
        resetBarName();
        setBarColor(barColor(phase()));
        Announce.send(this, line("shield_broken"), element().color, Announce.STYLE_BOSS);
        Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_COBBLESTONE.defaultBlockState()),
                c.x, c.y, c.z, 250, 2.2, 2.2, 2.2, 0.4);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 3.0F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 2.5F, 0.5F);
        BossKit.shake(level, c, 30, 1.6F, 20);
    }

    @Override
    protected float modifyIncomingDamage(DamageSource source, float amount) {
        if (isShielded() && level() instanceof ServerLevel sl) {
            Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
            if (source.is(CoreRegistries.SPELL_DAMAGE) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
                // Só magia racha a pedra
                shieldHp -= amount;
                entityData.set(DATA_SHIELD, Math.max(0.01F, shieldHp / shieldMax));
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        c.x, c.y, c.z, 20, 1.5, 1.5, 1.5, 0.2);
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F);
                if (shieldHp <= 0) breakShield(sl);
            } else {
                sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 1.2, 1.2, 1.2, 0.3);
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.5F, 1.8F);
                if (source.getEntity() instanceof ServerPlayer p) {
                    int now = tickCount;
                    if (now - shieldWarned.getOrDefault(p.getUUID(), -100) > 60) {
                        shieldWarned.put(p.getUUID(), now);
                        p.displayClientMessage(line("shield_immune").copy().withStyle(ChatFormatting.GOLD), true);
                    }
                }
            }
            return 0F;
        }
        return isStaggered() ? amount * 1.5F : amount;
    }

    @Override
    protected void updateBossBar() {
        if (isShielded() && shieldMax > 0) bossEvent.setProgress(Math.max(0F, shieldHp / shieldMax));
        else super.updateBossBar();
    }

    @Override
    protected boolean canUseMove(GuardianMove move, LivingEntity target, double dist) {
        if (isStaggered()) return false;
        return !isShielded() || !move.id.equals("charge");
    }

    /** Com o escudo de pé o Golem não sai do lugar: contra quem se esconde, responde com magia de pedra. */
    @Override
    protected void onUnreachable(ServerLevel level, LivingEntity target) {
        if (isShielded() || isStaggered()) {
            BossSpells.cast(this, Element.EARTH, target, spellPower(), rangedRecipes());
            return;
        }
        super.onUnreachable(level, target);
    }

    @Override
    protected void combatMovement(LivingEntity target, double dist) {
        if (isShielded() || isStaggered()) {
            getNavigation().stop();
            if (!isStaggered() && !isBusy()) getLookControl().setLookAt(target, 30.0F, 30.0F);
            return;
        }
        super.combatMovement(target, dist);
    }

    @Override
    protected int moveGap() {
        // Com o escudo de pé, ataca à distância mais seguido
        return isShielded() ? 20 : super.moveGap();
    }

    @Override
    protected void cancelMove() {
        super.cancelMove();
        // Placa segurada quando o golpe é interrompido: se desfaz sem atingir ninguém
        if (slab != null && !slab.isRemoved() && !slab.isFlying()) slab.discard();
        slab = null;
    }

    // =====================================================================================
    //  Golpes
    // =====================================================================================
    @Override
    protected void performMove(ServerLevel level, GuardianMove move, int t, @Nullable LivingEntity target) {
        switch (move.id) {
            case "smash" -> smash(level, t);
            case "quake" -> quake(level, t);
            case "rock_throw" -> rockThrow(level, t, target);
            case "earth_spike" -> earthSpike(level, t, target);
            case "root_grasp" -> rootGrasp(level, t);
            case "rising_pillars" -> risingPillars(level, t);
            case "slab_throw" -> slabThrow(level, t);
            case "charge" -> charge(level, t, target);
            case "shatter" -> shatter(level, t, target);
            default -> {}
        }
    }

    private Vec3 forward() {
        return BossSpells.dirOf(bodyYaw());
    }

    private void groundBurst(ServerLevel level, Vec3 p, int count, double spread) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BossSpells.groundState(level, p)),
                p.x, p.y + 0.2, p.z, count, spread, 0.2, spread, 0.25);
        level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.3, p.z, count / 4, spread, 0.1, spread, 0.03);
    }

    /** Esmagar: cone curto de 10 ticks na frente; os punhos descem no tick 15. */
    private void smash(ServerLevel level, int t) {
        float range = getBbWidth() * 0.5F + 5.0F;
        if (t == 5) {
            smashShape = Telegraph.Shape.cone(position(), bodyYaw(), 90F, range);
            smashId = Telegraph.show(level, Telegraph.Shape.cone(position(), bodyYaw(), 90F, range).warmup(10).color(earthColor())
                    .owner(this).noRunes());
        }
        if (t == 8) level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_HURT, SoundSource.HOSTILE, 2.0F, 0.4F);
        if (t != 15 || smashShape == null) return;
        Vec3 impact = position().add(forward().scale(getBbWidth() * 0.5 + 1.8));
        groundBurst(level, impact, 60, 1.2);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.5F, 0.4F);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
        BossKit.shake(level, impact, 16, 0.9F, 10);
        for (LivingEntity e : foesIn(level, smashShape)) {
            if (e.hurt(damageSources().mobAttack(this), meleeDamage())) {
                BossSpells.knock(e, e.position().subtract(position()), 1.2, 0.6);
            }
        }
        Telegraph.finish(level, smashId);
        smashShape = null;
    }

    /** Terremoto: o anel mostra até onde a onda vai; ela corre rente ao chão — quem estiver no ar desvia. */
    private void quake(ServerLevel level, int t) {
        float inner = getBbWidth() * 0.5F;
        if (t == 0) {
            // hold = tempo da onda chegar à borda (14 / 0,55 ≈ 26 ticks): o anel fica no chão enquanto ela corre
            Telegraph.show(level, Telegraph.Shape.ring(position(), inner, 14F).warmup(22).hold(26).vertical(0.5, 1.0)
                    .color(Telegraph.WARNING).owner(this));
            hint("quake", 32);
        }
        if (t == 22 && phase() >= 3) {
            Telegraph.show(level, Telegraph.Shape.ring(position(), inner, 18F).warmup(10).hold(26).vertical(0.5, 1.0)
                    .color(earthColor()).owner(this).noRunes());
        }
        if (t == 4) level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.0F, 0.6F);
        if (t == 10 && onGround()) setDeltaMovement(getDeltaMovement().add(0, 0.5, 0));
        boolean second = t == 32 && phase() >= 3;
        if (t != 22 && !second) return;
        Vec3 c = position();
        groundBurst(level, c, 120, 3.0);
        BossKit.crackFloor(level, c, second ? 9 : 7, 6, 80);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F, 0.4F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0F, 0.3F);
        BossKit.shake(level, c, 32, second ? 1.4F : 2.0F, 20);
        // Atordoa 1,5 s (acaba antes do próximo golpe telegrafado encher); a 2ª onda poupa quem ainda está atordoado
        // (atordoado não consegue pular — levar as duas seria indesviável)
        GuardianShockwave wave = GuardianShockwave.create(this, c, Element.EARTH)
                .radius(second ? 18F : 14F, second ? 0.7F : 0.55F)
                .damage(10F * mult())
                .stun(30)
                .groundOnly()
                .knock(0.5, 0.55);
        if (second) wave.spareStunned();
        wave.launch();
    }

    /**
     * Terra Partida (assinatura do Golem): ele ergue os punhos (o chão em volta já racha), salta e esmurra o chão —
     * FENDAS (linhas marcadas) correm em raios até 17 blocos: quem estiver numa é lançado e fica com o "chão partido"
     * (lento) enquanto a fenda fumega. Na fase 3 uma segunda leva de fendas, girada, sai logo depois. A arena não
     * quebra de verdade: as rachaduras são visuais.
     */
    private void shatter(ServerLevel level, int t, @Nullable LivingEntity target) {
        int faults = 4 + phase();
        if (t == 0) {
            float base = target != null ? BossSpells.yawOf(target.position().subtract(position())) : bodyYaw();
            shatterYaw = base;
            showFaults(level, base, faults, 26);
            hint("shatter", 32);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.5F, 0.5F);
        }
        // Antecipação: o chão em volta trinca cada vez mais enquanto ele junta força
        if (t == 6 || t == 14 || t == 20) {
            BossKit.crackFloor(level, position(), getBbWidth() * 0.5 + 1 + t * 0.12, 2 + t / 4, 40);
            groundBurst(level, position(), 20, getBbWidth() * 0.4);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
        if (t == 14 && onGround()) setDeltaMovement(getDeltaMovement().add(0, 0.55, 0));
        if (t == 26) faultImpact(level, shatterYaw, faults, false);
        if (phase() >= 3) {
            if (t == 34) showFaults(level, shatterYaw + 180F / faults, faults, 14);
            if (t == 48) faultImpact(level, shatterYaw + 180F / faults, faults, true);
        }
    }

    private float shatterYaw;

    private void showFaults(ServerLevel level, float baseYaw, int n, int warmup) {
        Vec3 c = position();
        for (int i = 0; i < n; i++) {
            Vec3 dir = BossSpells.dirOf(baseYaw + i * 360F / n);
            Vec3 from = c.add(dir.scale(getBbWidth() * 0.5));
            Telegraph.show(level, Telegraph.Shape.line(from, c.add(dir.scale(FAULT_LENGTH)), FAULT_WIDTH).warmup(warmup).hold(6)
                    .vertical(0.5, 2.0).color(Telegraph.DANGER).owner(this));
        }
    }

    private void faultImpact(ServerLevel level, float baseYaw, int n, boolean second) {
        Vec3 c = position();
        float dmg = (second ? 8F : 11F) * mult();
        BossKit.shake(level, c, 40, second ? 1.6F : 2.4F, 26);
        BossKit.flashNear(level, c, 28, 0xC8B28A, 0.35F, 10);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.4, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.5F, 0.35F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 3.0F, 0.4F);
        Set<UUID> hit = new HashSet<>();
        for (int i = 0; i < n; i++) {
            Vec3 dir = BossSpells.dirOf(baseYaw + i * 360F / n);
            Vec3 from = c.add(dir.scale(getBbWidth() * 0.5));
            Vec3 to = c.add(dir.scale(FAULT_LENGTH));
            Telegraph.Shape fault = Telegraph.Shape.line(from, to, FAULT_WIDTH).vertical(0.5, 2.0);
            for (LivingEntity e : foesIn(level, fault)) {
                if (!hit.add(e.getUUID())) continue;
                BossSpells.hit(this, e, Element.EARTH, dmg);
                e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 0.85, 0));
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
            }
            // A fenda "corre" do Golem para fora: escombros e rachaduras em sequência (aftermath: fumaça por 6 s)
            for (int k = 0; k < 6; k++) {
                Vec3 p = groundAt(from.lerp(to, (k + 0.5) / 6.0));
                int delay = k * 2;
                BossKit.schedule(level, Math.max(1, delay), () -> {
                    if (!isAlive()) return;
                    BossKit.debris(level, p, 1.2, 8);
                    BossKit.crackFloor(level, p, 1.6, 8, 120);
                    if (delay % 4 == 0) level.playSound(null, p.x, p.y, p.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.6F, 0.5F);
                });
            }
            Vec3 a = from, b = to;
            for (int k = 1; k <= 6; k++) {
                BossKit.schedule(level, 10 + k * 20, () -> {
                    if (!isAlive()) return;
                    for (int j = 0; j <= 5; j++) {
                        Vec3 p = a.lerp(b, j / 5.0);
                        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y + 0.1, p.z, 1, 0.4, 0.05, 0.4, 0.01);
                    }
                    for (LivingEntity e : foesIn(level, fault)) {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), this);
                    }
                });
            }
        }
        groundBurst(level, c, 80, 2.5);
    }

    /** Arremesso de Rocha: a rocha voa em arco e o ponto de queda é marcado no chão durante o voo. */
    private void rockThrow(ServerLevel level, int t, @Nullable LivingEntity target) {
        Vec3 ground = position().add(forward().scale(getBbWidth() * 0.5 + 1.0));
        if (t == 12) {
            groundBurst(level, ground, 50, 0.8);
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.5F, 0.5F);
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
        if (t > 12 && t < 26) {
            Vec3 above = position().add(0, getBbHeight() + 0.8, 0);
            level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.DIRT.defaultBlockState()),
                    above.x, above.y, above.z, 2, 0.6, 0.3, 0.6, 0);
        }
        if (target == null) return;
        boolean second = t == 32 && phase() >= 3;
        if (t != 26 && !second) return;
        Vec3 from = position().add(forward().scale(0.5)).add(0, getBbHeight() + 0.6, 0);
        // Mira onde o alvo vai estar quando a rocha cair
        double flight = Math.max(12, Math.min(34, target.distanceTo(this) / 1.1));
        Vec3 lead = target.getDeltaMovement().multiply(1, 0, 1).scale(flight * 0.6);
        Vec3 aim = target.position().add(lead);
        if (second) aim = aim.add((random.nextDouble() - 0.5) * 6, 0, (random.nextDouble() - 0.5) * 6);
        float size = phase() >= 2 ? 1.25F : 1.0F;
        GolemBoulder boulder = GolemBoulder.throwAt(this, from, aim, size, 14F * mult(), phase() >= 2);
        Telegraph.show(level, Telegraph.Shape.circle(groundAt(aim), GolemBoulder.impactRadius(size)).warmup(boulder.flightTicks())
                .color(earthColor()).owner(this).noRunes());
        level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.4F);
    }

    /** Espinhos (glifo Queda): o ponto mirado é marcado no chão enquanto a rocha cai do céu. */
    private void earthSpike(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t == 12) {
            Vec3 fist = position().add(forward().scale(getBbWidth() * 0.5 + 0.8));
            groundBurst(level, fist, 30, 0.6);
            level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
        if (target == null) return;
        if (t == 14 || (t == 22 && phase() >= 2)) {
            // A marca só aparece se a rocha vai mesmo CAIR ali (glifo Queda do módulo forms_a); sem ele, o Golem
            // atira a rocha reto (projétil visível) e uma marca no chão enganaria o jogador
            if (GlyphRegistry.get(IsekaiMahou.id("fall")) != null) {
                Telegraph.show(level, Telegraph.Shape.circle(groundAt(target.position()), 3.0F).warmup(16).color(earthColor())
                        .owner(this).noFlash());
            }
            BossSpells.cast(this, Element.EARTH, target, spellPower(), "fall rock amplify", "fall rock",
                    "projectile rock amplify", "projectile rock");
        }
    }

    /** Agarrão de Raízes: o círculo em volta do Golem enche em 18 ticks; quem ficar dentro é enraizado. */
    private void rootGrasp(ServerLevel level, int t) {
        if (t == 0) {
            float dmg = 4F * mult();
            Telegraph.delayed(level, Telegraph.Shape.circle(position(), 6.0F).warmup(18).color(earthColor()).owner(this),
                    hits -> roots(level, dmg, hits));
        }
        if (t == 8) level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        if (t > 0 && t < 18 && t % 4 == 0) groundBurst(level, position(), 12, 3.0);
    }

    private void roots(ServerLevel level, float dmg, List<LivingEntity> hits) {
        if (!isAlive()) return;
        Vec3 c = position();
        groundBurst(level, c, 80, 3.5);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 2.5F, 0.4F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.MUD_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        BossKit.shake(level, c, 16, 0.8F, 10);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            BossSpells.hit(this, e, Element.EARTH, dmg);
            // 1,5 s: as raízes soltam antes de o próximo golpe (intervalo + aviso) acertar — prende, mas não vira combo
            e.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, 30, 0), this);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MANGROVE_ROOTS.defaultBlockState()),
                    e.getX(), e.getY() + 0.4, e.getZ(), 30, 0.4, 0.5, 0.4, 0.1);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()),
                    e.getX(), e.getY() + 0.1, e.getZ(), 20, 0.5, 0.1, 0.5, 0.15);
        }
    }

    /**
     * Pilares Ascendentes: uma (fase 1) ou três (fase 2+) linhas de círculos saem do Golem; cada pilar sobe quando
     * o seu círculo enche, um após o outro (2 ticks de diferença) — saia para o lado da linha.
     */
    private void risingPillars(ServerLevel level, int t) {
        if (t == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.0F, 0.8F);
        if (t < 18 && t % 3 == 0) groundBurst(level, position(), 10, 2.0);
        if (t == 6) {
            int lines = phase() >= 2 ? 3 : 1;
            float dmg = 9F * mult();
            for (int l = 0; l < lines; l++) {
                float yaw = bodyYaw() + (lines == 1 ? 0F : (l - 1) * 28F);
                Vec3 dir = BossSpells.dirOf(yaw);
                double maxLen = BossSpells.clearDistance(level, position(), dir, 22, 1.0);
                int i = 0;
                for (double d = getBbWidth() * 0.5 + 1.5; d <= maxLen; d += 2.0, i++) {
                    Vec3 p = groundAt(position().add(dir.scale(d)));
                    // Borda de penhasco ou degrau alto: a linha para ali
                    if (Math.abs(p.y - getY()) > 4) break;
                    int warm = 12 + i * 2;
                    Telegraph.delayed(level, Telegraph.Shape.circle(p, 1.4F).warmup(warm).color(earthColor()).owner(this).noRunes(),
                            hits -> pillar(level, p, dmg, hits));
                }
            }
        }
        if (t == 18) {
            Vec3 fists = position().add(forward().scale(getBbWidth() * 0.5 + 1.2));
            groundBurst(level, fists, 70, 1.5);
            level.sendParticles(ParticleTypes.EXPLOSION, fists.x, fists.y + 0.5, fists.z, 1, 0, 0, 0, 0);
            level.playSound(null, fists.x, fists.y, fists.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.5F, 0.4F);
            BossKit.shake(level, fists, 24, 1.2F, 16);
        }
    }

    private void pillar(ServerLevel level, Vec3 p, float dmg, List<LivingEntity> hits) {
        if (!isAlive()) return;
        GolemPillar.spawn(level, p);
        BlockState rock = BossSpells.groundState(level, p);
        level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, rock), p.x, p.y + 0.2, p.z, 20, 0.6, 0.2, 0.6, 0.3);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_COBBLESTONE.defaultBlockState()),
                p.x, p.y + 1.0, p.z, 16, 0.4, 1.0, 0.4, 0.2);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.HOSTILE, 1.6F, 0.5F);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.BASALT_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            BossSpells.hit(this, e, Element.EARTH, dmg);
            e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 0.85, e.getDeltaMovement().z * 0.3);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
        }
    }

    /** Posição da placa: acima da cabeça enquanto mira, descendo para a frente do peito antes do arremesso. */
    private Vec3 slabHold(int t) {
        Vec3 over = position().add(forward().scale(0.6)).add(0, getBbHeight() + 0.8, 0);
        Vec3 chest = position().add(forward().scale(getBbWidth() * 0.5 + 1.0)).add(0, getBbHeight() * 0.45, 0);
        double f = Math.max(0, Math.min(1, (t - 20) / 6.0));
        return over.lerp(chest, f);
    }

    /**
     * Arremesso de Placa: o Golem arranca uma placa de terra, mira (a faixa é desenhada no tick 14, 12 ticks antes) e
     * a lança rente ao chão — saia da faixa.
     */
    private void slabThrow(ServerLevel level, int t) {
        Vec3 front = position().add(forward().scale(getBbWidth() * 0.5 + 1.2));
        if (t == 0) {
            groundBurst(level, front, 30, 1.0);
            level.playSound(null, front.x, front.y, front.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
        if (t == 8) {
            slab = GolemSlab.rip(this, slabHold(t));
            groundBurst(level, front, 90, 1.6);
            level.playSound(null, front.x, front.y, front.z, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 2.5F, 0.4F);
            level.playSound(null, front.x, front.y, front.z, SoundEvents.PACKED_MUD_BREAK, SoundSource.HOSTILE, 2.5F, 0.5F);
            BossKit.shake(level, front, 16, 0.8F, 10);
        }
        if (slab != null && !slab.isRemoved() && !slab.isFlying()) slab.holdAt(slabHold(t), bodyYaw());
        if (t == 14) {
            Vec3 dir = forward();
            double len = BossSpells.clearDistance(level, position(), dir, 28, 1.0);
            double start = getBbWidth() * 0.5 + 0.5;
            slabFrom = groundAt(position().add(dir.scale(start)));
            slabTo = groundAt(position().add(dir.scale(Math.max(start + 2, len))));
            // A faixa continua no chão enquanto a placa voa por ela (hold = tempo de voo a 1,5 bloco/tick)
            int flight = Math.max(1, (int) Math.ceil(slabFrom.distanceTo(slabTo) / SLAB_SPEED));
            Telegraph.show(level, Telegraph.Shape.line(slabFrom, slabTo, GolemSlab.WIDTH).warmup(12).hold(flight)
                    .color(earthColor()).owner(this));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.8F, 0.5F);
        }
        if (t == 26 && slab != null && !slab.isRemoved()) {
            slab.launch(slabFrom, slabTo, SLAB_SPEED, 13F * mult());
            slab = null;
        }
    }

    /** Avalanche (fase 3): a linha da corrida é desenhada 14 ticks antes; bater na parede deixa o Golem tonto. */
    private void charge(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t == 0) {
            chargeHit.clear();
            chargeDir = BossSpells.flatDir(position(), target != null ? target.position() : position().add(forward()), forward());
            double len = BossSpells.clearDistance(level, position(), chargeDir, 0.55 * 26 + getBbWidth() * 0.5, 1.5);
            Vec3 end = position().add(chargeDir.scale(Math.max(2, len) + getBbWidth() * 0.5));
            // A faixa fica no chão durante toda a corrida (26 ticks), não só no aviso
            Telegraph.show(level, Telegraph.Shape.line(position(), end, getBbWidth() + 1.0F).warmup(14).hold(26)
                    .color(Telegraph.DANGER).owner(this));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.5F);
        }
        if (t < 14) {
            BossSpells.face(this, position().add(chargeDir));
            if (t % 3 == 0) groundBurst(level, position(), 12, 1.0);
            return;
        }
        if (t < 40) {
            if (chargeDir.lengthSqr() < 1.0E-4) chargeDir = forward();
            Vec3 dir = chargeDir.normalize();
            BossSpells.face(this, position().add(dir));
            setDeltaMovement(dir.x * 0.55, getDeltaMovement().y, dir.z * 0.55);
            if (t % 2 == 0) groundBurst(level, position(), 10, 1.2);
            if (t % 6 == 0) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 2.5F, 0.4F);
                BossKit.shake(level, position(), 14, 0.6F, 8);
            }
            // Caixa do Golem + 0,5 de cada lado = exatamente a largura da faixa desenhada (largura + 1)
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.5, 0.5, 0.5),
                    e -> isFoe(e) && !chargeHit.contains(e.getUUID()))) {
                chargeHit.add(e.getUUID());
                e.hurt(damageSources().mobAttack(this), meleeDamage() * 1.15F);
                BossSpells.knock(e, e.position().subtract(position()).add(dir.scale(2)), 2.2, 0.7);
            }
            if (horizontalCollision && t > 16) {
                // Bateu na parede: fica tonto
                Vec3 c = position().add(dir.scale(getBbWidth() * 0.5)).add(0, 2, 0);
                level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.5, 0.5, 0.5, 0);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 2.5F, 0.4F);
                BossKit.shake(level, c, 20, 1.5F, 14);
                setDeltaMovement(Vec3.ZERO);
                cancelMove();
                staggerTicks = 50;
                entityData.set(DATA_STAGGER, true);
            }
            return;
        }
        setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
    }

    // =====================================================================================
    //  Passivos e visual
    // =====================================================================================
    @Override
    protected void passiveTick(ServerLevel level, @Nullable LivingEntity target) {
        if (staggerTicks > 0) {
            staggerTicks--;
            getNavigation().stop();
            if (tickCount % 4 == 0) {
                double a = tickCount * 0.3;
                level.sendParticles(ParticleTypes.CRIT, getX() + Math.cos(a) * 1.2, getY() + getBbHeight() + 0.3,
                        getZ() + Math.sin(a) * 1.2, 2, 0.1, 0.1, 0.1, 0);
            }
            if (staggerTicks == 0) entityData.set(DATA_STAGGER, false);
        }
        if (isShielded() && !isDeadOrDying()) {
            // Regenera atrás da pedra, mas no máximo até 70% (demorar para quebrar custa caro, sem desfazer a luta toda)
            if (tickCount % 20 == 0 && getHealth() < getMaxHealth() * 0.7F) heal(1.5F);
            if (tickCount % 6 == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE.defaultBlockState()),
                        getX(), getY() + getBbHeight(), getZ(), 3, 2.0, 0.5, 2.0, 0);
            }
        }
    }

    @Override
    protected void onFightReset() {
        shieldHp = 0;
        shieldMax = 0;
        staggerTicks = 0;
        entityData.set(DATA_SHIELD, 0F);
        entityData.set(DATA_STAGGER, false);
    }

    @Override
    protected void introImpact(ServerLevel level) {
        super.introImpact(level);
        groundBurst(level, position(), 160, 4.0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.5F, 0.3F);
    }

    @Override
    protected void deathFx(ServerLevel level, int t) {
        super.deathFx(level, t);
        groundBurst(level, position().add((random.nextDouble() - 0.5) * 3, 0, (random.nextDouble() - 0.5) * 3), 12, 1.0);
        if (t % 8 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 2.5F, 0.5F);
    }

    @Override
    protected void stageFx(ServerLevel level, int stage, int left) {
        super.stageFx(level, stage, left);
        if (left % 5 == 0) groundBurst(level, position(), 20, 2.5);
    }

    @Override
    protected void clientAmbient() {
        double w = getBbWidth() * 0.5, h = getBbHeight();
        if (random.nextInt(4) == 0) {
            level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST,
                            random.nextBoolean() ? Blocks.DIRT.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState()),
                    getX() + (random.nextDouble() - 0.5) * 2 * w, getY() + h * (0.4 + random.nextDouble() * 0.5),
                    getZ() + (random.nextDouble() - 0.5) * 2 * w, 0, 0, 0);
        }
        if (phase() >= 3 && random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.WAX_ON, getX() + (random.nextDouble() - 0.5) * 2 * w, getY() + h * 0.85,
                    getZ() + (random.nextDouble() - 0.5) * 2 * w, 0, 0.05, 0);
        }
    }

    @Override
    protected RawAnimation baseAnimation(AnimationState<ElementalGuardian> state) {
        if (isStaggered()) return animStunned;
        if (isShielded()) return animShield;
        return state.isMoving() ? walkAnim() : idleAnim();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ShieldHp", shieldHp);
        tag.putFloat("ShieldMax", shieldMax);
        tag.putInt("Stagger", staggerTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        shieldHp = tag.getFloat("ShieldHp");
        shieldMax = tag.getFloat("ShieldMax");
        staggerTicks = tag.getInt("Stagger");
        entityData.set(DATA_SHIELD, shieldHp > 0 && shieldMax > 0 ? shieldHp / shieldMax : 0F);
        entityData.set(DATA_STAGGER, staggerTicks > 0);
        if (isShielded()) {
            setBarName(line("shield_bar"));
            setBarColor(BossEvent.BossBarColor.WHITE);
        }
    }
}
