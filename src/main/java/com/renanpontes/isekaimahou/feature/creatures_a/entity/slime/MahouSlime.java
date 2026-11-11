package com.renanpontes.isekaimahou.feature.creatures_a.entity.slime;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.ElementReactions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Slime gelatinoso do mod (modelo GeckoLib "fofo", com olhos). Usa o sistema de tamanhos/divisão do slime vanilla,
 * mas com IA própria e comportamento por variedade ({@link SlimeKind}):
 * <ul>
 *   <li>slime azul: neutro, só revida;</li>
 *   <li>fogo: imune a fogo, sofre com água/chuva, queima quem toca e às vezes deixa fogo ao pousar;</li>
 *   <li>água: absorve magia de Água (se cura), encharca quem toca, regenera na água;</li>
 *   <li>pedra: metade do dano físico, empurra forte;</li>
 *   <li>vento: rápido, pulos altos, sem dano de queda;</li>
 *   <li>gelatina venenosa: envenena ao tocar, imune a veneno, deixa nuvens tóxicas quando grande.</li>
 * </ul>
 */
public class MahouSlime extends Slime implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mahou_slime.idle");
    private static final RawAnimation AIR = RawAnimation.begin().thenLoop("animation.mahou_slime.air");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final SlimeKind kind;
    private boolean groundedLastTick = true;
    private int airTicks;

    public MahouSlime(EntityType<? extends MahouSlime> type, Level level, SlimeKind kind) {
        super(type, level);
        this.kind = kind;
        this.moveControl = new SlimeHopControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.FOLLOW_RANGE, 20.0);
    }

    public SlimeKind kind() {
        return kind;
    }

    public boolean isNeutral() {
        return kind.neutral;
    }

    // ------------------------------------------------------------------ IA

    @Override
    protected void registerGoals() {
        // Chamado no construtor do Mob (antes de "kind" existir): as metas consultam a variedade só em tempo de jogo.
        goalSelector.addGoal(1, new SlimeGoals.Swim(this));
        goalSelector.addGoal(2, new SlimeGoals.Attack(this));
        goalSelector.addGoal(3, new SlimeGoals.RandomDirection(this));
        goalSelector.addGoal(5, new SlimeGoals.KeepJumping(this));
        targetSelector.addGoal(1, new SlimeGoals.Retaliate(this));
        targetSelector.addGoal(2, new SlimeGoals.HuntPlayers(this));
        targetSelector.addGoal(3, new SlimeGoals.HuntGolems(this));
    }

    public int hopDelay() {
        return getJumpDelay();
    }

    @Override
    protected int getJumpDelay() {
        return switch (kind) {
            case WIND -> random.nextInt(10) + 5;
            case STONE -> random.nextInt(25) + 20;
            case KING -> random.nextInt(15) + 20;
            default -> random.nextInt(20) + 10;
        };
    }

    public boolean hopsAggressively() {
        return isDealsDamage();
    }

    public void playHopSound() {
        if (!doPlayJumpSound()) return;
        float pitch = ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) * (isTiny() ? 1.4F : 0.8F);
        if (kind == SlimeKind.KING) pitch *= 0.7F;
        playSound(getJumpSound(), getSoundVolume(), pitch);
    }

    @Override
    protected boolean isDealsDamage() {
        if (kind.neutral && getTarget() == null) return false;
        return super.isDealsDamage();
    }

    @Override
    public void push(Entity entity) {
        super.push(entity);
        // Também machuca o alvo atual (mob) ao encostar — o vanilla só faz isso com golens e jogadores.
        if (entity == getTarget() && entity instanceof LivingEntity le && !(entity instanceof net.minecraft.world.entity.player.Player)
                && !(entity instanceof net.minecraft.world.entity.animal.IronGolem) && isDealsDamage()) {
            dealDamage(le);
        }
    }

    @Override
    protected void dealDamage(LivingEntity victim) {
        if (victim instanceof MahouSlime) return;
        if (kind.neutral && getTarget() != victim) return;
        if (!isAlive() || !isWithinMeleeAttackRange(victim) || !hasLineOfSight(victim)) return;
        DamageSource src = damageSources().mobAttack(this);
        if (victim.hurt(src, getAttackDamage())) {
            playSound(SoundEvents.SLIME_ATTACK, 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
            if (level() instanceof ServerLevel sl) EnchantmentHelper.doPostAttackEffects(sl, victim, src);
            onTouchHit(victim);
        }
    }

    /** Efeito extra de cada variedade ao acertar alguém encostando. */
    protected void onTouchHit(LivingEntity victim) {
        int s = getSize();
        switch (kind) {
            case FIRE -> victim.igniteForSeconds(2 + s);
            case WATER -> victim.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 60 + 20 * s, 0), this);
            case STONE -> {
                victim.knockback(0.4 + 0.2 * s, getX() - victim.getX(), getZ() - victim.getZ());
                victim.hurtMarked = true;
            }
            case WIND -> {
                victim.knockback(0.8 + 0.15 * s, getX() - victim.getX(), getZ() - victim.getZ());
                victim.push(0, 0.25, 0);
                victim.hurtMarked = true;
            }
            case POISON -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 60 + 20 * s, s >= 4 ? 1 : 0), this);
            case KING -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ Tamanho / atributos

    @Override
    public void setSize(int size, boolean resetHealth) {
        super.setSize(size, false);
        // O slime vanilla recalcula vida/dano/velocidade pelo tamanho; aqui cada variedade tem os próprios números.
        int s = getSize();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(kind.health(s));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(kind.speed(s));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(kind.damage(s));
        getAttribute(Attributes.ARMOR).setBaseValue(kind.armor(s));
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(kind.knockbackResist);
        getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(kind.jump);
        if (resetHealth) setHealth(getMaxHealth());
        else if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        xpReward = kind.xp(s);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        setSize(kind.pickNaturalSize(level.getRandom(), difficulty), true);
        return result;
    }

    // ------------------------------------------------------------------ Dano / resistências

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.is(CoreRegistries.SPELL_DAMAGE)) {
            // O núcleo marca o elemento ANTES de causar o dano: dá para saber qual magia acertou.
            Element el = ElementReactions.lastElement(this, level().getGameTime(), 0);
            if (el != null && kind.absorbs(el)) {
                absorbSpell();
                return false;
            }
        }
        if (kind == SlimeKind.STONE && !source.is(CoreRegistries.SPELL_DAMAGE) && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_DROWNING)) {
            amount *= 0.5F;
        }
        boolean ok = super.hurt(source, amount);
        if (ok && kind == SlimeKind.FIRE && source.is(DamageTypeTags.IS_DROWNING) && level() instanceof ServerLevel sl) {
            playSound(SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.2F + random.nextFloat() * 0.4F);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        }
        return ok;
    }

    private void absorbSpell() {
        if (!(level() instanceof ServerLevel sl)) return;
        heal(1.0F + getSize());
        // A magia "some" dentro da gelatina: partículas do elemento da variedade + brilho de cura.
        ElementFx.burst(sl, position().add(0, getBbHeight() * 0.5, 0), kind.element, 14, getBbWidth() * 0.3);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() * 0.8, getZ(), 4, getBbWidth() * 0.3, 0.2,
                getBbWidth() * 0.3, 0.0);
        playSound(kind == SlimeKind.FIRE ? SoundEvents.BLAZE_SHOOT : SoundEvents.GENERIC_DRINK, 0.7F, 1.4F);
    }

    @Override
    public boolean isSensitiveToWater() {
        return kind == SlimeKind.FIRE;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        if (kind == SlimeKind.WIND || kind == SlimeKind.KING) return false;
        return super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (kind == SlimeKind.POISON && effect.is(MobEffects.POISON)) return false;
        return super.canBeAffected(effect);
    }

    // ------------------------------------------------------------------ Tick: pouso e partículas

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientAmbient();
            return;
        }
        boolean grounded = onGround();
        if (grounded && !groundedLastTick && airTicks > 2) onLand();
        groundedLastTick = grounded;
        airTicks = grounded ? 0 : airTicks + 1;
        if (kind == SlimeKind.WATER && isInWaterOrRain() && tickCount % 40 == 0 && getHealth() < getMaxHealth()) heal(1.0F);
        // O slime azul "esquece" a briga se ninguém o machucar por 20 s.
        if (kind.neutral && getTarget() != null && tickCount - getLastHurtByMobTimestamp() > 400) setTarget(null);
    }

    /** Pousou depois de um pulo (servidor). */
    protected void onLand() {
        ServerLevel sl = (ServerLevel) level();
        int s = getSize();
        switch (kind) {
            case FIRE -> {
                sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.1, getZ(), 4 + 2 * s, getBbWidth() * 0.4, 0.05, getBbWidth() * 0.4, 0.02);
                // Só em briga, e só onde o fogo não tem o que pegar em volta: vira um rastro de chamas curto
                // que se apaga sozinho, em vez de incendiar a savana inteira enquanto o slime passeia.
                float chance = s >= 2 ? 0.18F : 0.05F;
                if (getTarget() != null && random.nextFloat() < chance && sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    BlockPos pos = blockPosition();
                    if (sl.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(sl, pos, Direction.UP) && !nearFlammable(sl, pos)) {
                        sl.setBlockAndUpdate(pos, BaseFireBlock.getState(sl, pos));
                    }
                }
            }
            case WATER -> {
                sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.1, getZ(), 10 * s, getBbWidth() * 0.4, 0.05, getBbWidth() * 0.4, 0.1);
                // Apaga o fogo em volta ao pousar.
                BlockPos c = blockPosition();
                for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
                    if (sl.getBlockState(p).is(BlockTags.FIRE)) sl.removeBlock(p, false);
                }
                if (getTarget() != null && s >= 2) {
                    for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6 * s),
                            e -> e != this && !(e instanceof MahouSlime))) {
                        e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 80, 0), this);
                    }
                }
            }
            case STONE -> {
                if (s >= 2) {
                    var ground = sl.getBlockState(blockPosition().below());
                    if (!ground.isAir()) {
                        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(),
                                8 * s, getBbWidth() * 0.4, 0.05, getBbWidth() * 0.4, 0.1);
                    }
                    playSound(SoundEvents.BASALT_BREAK, 0.6F + 0.2F * s, 0.6F);
                }
            }
            case WIND -> {
                sl.sendParticles(ParticleTypes.SMALL_GUST, getX(), getY() + 0.2, getZ(), 1 + s / 2, getBbWidth() * 0.3, 0.05, getBbWidth() * 0.3, 0.0);
                if (s >= 2) playSound(SoundEvents.BREEZE_LAND, 0.5F, 1.3F);
            }
            case POISON -> {
                if (s >= 4 && getTarget() != null && random.nextFloat() < 0.25F) {
                    AreaEffectCloud cloud = new AreaEffectCloud(sl, getX(), getY(), getZ());
                    cloud.setOwner(this);
                    cloud.setRadius(1.6F);
                    cloud.setRadiusOnUse(-0.3F);
                    cloud.setWaitTime(5);
                    cloud.setDuration(80);
                    cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
                    cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                    sl.addFreshEntity(cloud);
                }
            }
            default -> { }
        }
    }

    /** Algum vizinho (inclusive em cima/embaixo) pode pegar fogo? */
    private static boolean nearFlammable(ServerLevel sl, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (sl.getBlockState(n).isFlammable(sl, n, d.getOpposite())) return true;
        }
        return false;
    }

    /** Partículas de ambiente (cliente). */
    private void clientAmbient() {
        double w = getBbWidth() * 0.5;
        double rx = getX() + (random.nextDouble() - 0.5) * 2 * w;
        double rz = getZ() + (random.nextDouble() - 0.5) * 2 * w;
        double ry = getY() + random.nextDouble() * getBbHeight();
        switch (kind) {
            case FIRE -> {
                if (random.nextInt(3) == 0) level().addParticle(random.nextBoolean() ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                        rx, getY() + getBbHeight() * (0.7 + random.nextDouble() * 0.4), rz, 0, 0.02, 0);
                if (isInWaterRainOrBubble() && random.nextInt(2) == 0) level().addParticle(ParticleTypes.SMOKE, rx, ry, rz, 0, 0.05, 0);
            }
            case WATER -> {
                if (random.nextInt(6) == 0) level().addParticle(ParticleTypes.DRIPPING_WATER, rx, ry, rz, 0, 0, 0);
            }
            case WIND -> {
                if (random.nextInt(3) == 0) {
                    double a = tickCount * 0.35 + random.nextDouble();
                    double r = w * 1.3;
                    level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * r, getY() + getBbHeight() * 0.4, getZ() + Math.sin(a) * r,
                            -Math.sin(a) * 0.05, 0.01, Math.cos(a) * 0.05);
                }
            }
            case POISON -> {
                if (random.nextInt(4) == 0) level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.55F, 0.88F, 0.3F),
                        rx, ry, rz, 0, 0, 0);
            }
            case STONE -> {
                if (random.nextInt(20) == 0) level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()),
                        rx, getY() + getBbHeight() * 0.8, rz, 0, 0, 0);
            }
            case KING -> {
                if (random.nextInt(5) == 0) level().addParticle(ParticleTypes.GLOW, rx, getY() + getBbHeight() * (1.0 + random.nextDouble() * 0.3), rz,
                        0, 0.02, 0);
            }
            default -> {
                if (random.nextInt(40) == 0) level().addParticle(ParticleTypes.BUBBLE_POP, rx, ry, rz, 0, 0.02, 0);
            }
        }
    }

    @Override
    protected ParticleOptions getParticleType() {
        return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(kind.gel().get()));
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * (kind == SlimeKind.WIND ? 1.25F : kind == SlimeKind.STONE ? 0.8F : 1.0F);
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 3,
                state -> state.setAndContinue(onGround() || isInWater() ? IDLE : AIR)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Quanto o slime está "amassado" agora (para o renderer). */
    public float squishAmount(float partialTick) {
        return Mth.lerp(partialTick, oSquish, squish);
    }

    /** Direção horizontal até o alvo (usada pelo pulo do rei). */
    protected Vec3 horizontalTo(Entity e) {
        Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
        return d.lengthSqr() < 1.0E-4 ? Vec3.ZERO : d.normalize();
    }
}
