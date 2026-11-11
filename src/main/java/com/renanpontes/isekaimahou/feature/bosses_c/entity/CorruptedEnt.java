package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ent Corrompido (Círculo de Pedras Druídico): árvore gigante, lenta e pesada.
 * <ul>
 *   <li><b>Pancada de Galhos</b>: esmaga o chão na frente (marca redonda; empurra para cima);</li>
 *   <li><b>Raízes</b>: o chão marca e treme embaixo de cada jogador, depois as raízes irrompem e ENRAÍZAM;</li>
 *   <li><b>Raízes em Linha</b>: crava os braços no chão e uma fileira de raízes corre pela terra em direção ao alvo
 *       (três fileiras em leque na fase 2) — a marca enche em cascata, segmento por segmento;</li>
 *   <li><b>Esporos</b>: nuvem venenosa em volta dele (e no alvo, na fase 2), com a área marcada enquanto dura;</li>
 *   <li><b>Regenera</b> enquanto pisa em grama/terra/musgo — a não ser que esteja pegando fogo (fraco a fogo).</li>
 * </ul>
 * Deixa a Muda Ancestral (ritual do Druida) se o módulo existir.
 */
public class CorruptedEnt extends ClassBossEntity {
    private static final int COLOR = 0x5E8C31;
    private static final int CORRUPT = 0x7A2F9A;
    private static final int SPORE_COLOR = 0x9A5BC4;
    private static final ResourceLocation FRENZY = IsekaiMahou.id("corrupted_ent_frenzy");
    private static final int SLAM_WARMUP = 14;
    private static final float SLAM_RADIUS = 3.4F;
    private static final int SPORE_WARMUP = 12;
    private static final int SPORE_TICKS = 100;
    private static final int ROOTLINE_CAST = 16;
    private static final double ROOTLINE_RANGE = 18.0;
    private static final double ROOTLINE_SEGMENT = 2.0;
    private static final float ROOTLINE_WIDTH = 2.2F;

    private int slamCd = 20;
    private int rootCd = 60;
    private int sporeCd = 140;
    private int rootLineCd = 100;
    private int slamTimer;
    private int rootTimer;
    private int sporeTimer;
    private int rootLineTimer;
    private float attackYaw;
    @Nullable
    private Vec3 sporeSpot;
    private boolean regenHinted;

    public CorruptedEnt(EntityType<? extends CorruptedEnt> type, Level level) {
        super(type, level);
        this.xpReward = 180;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(240.0, 8.0, 0.21, 12.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    public String bossId() {
        return "corrupted_ent";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.GREEN;
    }

    @Override
    protected void configureBar(ServerBossEvent bossBar) {
        bossBar.setCreateWorldFog(true);
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.5F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("slam", "roots", "spores", "roar", "root_line");
    }

    @Override
    public List<String> toggledBones() {
        return List.of("blossoms");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return !bone.equals("blossoms") || phase() >= 1;
    }

    /** Imune ao próprio veneno dos esporos. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
    }

    /** Madeira: fogo machuca bem mais. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE)) amount *= 1.75F;
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected boolean isBusy() {
        return slamTimer > 0 || rootTimer > 0 || sporeTimer > 0 || rootLineTimer > 0;
    }

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        boolean p2 = phase() >= 1;
        if (slamCd > 0) slamCd--;
        if (rootCd > 0) rootCd--;
        if (sporeCd > 0) sporeCd--;
        if (rootLineCd > 0) rootLineCd--;
        if (tickCount % 20 == 0) regenerate(level, p2);

        if (slamTimer > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            slamTimer--;
            return;
        }
        if (rootTimer > 0) {
            getNavigation().stop();
            if (--rootTimer == 0) summonRoots(level);
            return;
        }
        if (sporeTimer > 0) {
            getNavigation().stop();
            if (--sporeTimer == 0) releaseSpores(level, p2);
            return;
        }
        if (rootLineTimer > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            rootLineTimer--;
            if (rootLineTimer % 4 == 0) {
                BlockState ground = groundState(level, blockPosition());
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(), 10, 1.4, 0.1, 1.4, 0.15);
            }
            return;
        }
        if (target == null) return;

        double dist = distanceTo(target);
        if (dist < 4.8 && slamCd <= 0) {
            startSlam(level, target, p2);
            return;
        }
        if (rootLineCd <= 0 && dist > 5 && dist < 20 && hasLineOfSight(target)) {
            startRootLine(level, target, p2);
            return;
        }
        if (rootCd <= 0 && dist < 22) {
            rootTimer = 14;
            rootCd = (p2 ? 80 : 120) + random.nextInt(30);
            action("roots");
            announce("roots");
            playSound(SoundEvents.ROOTED_DIRT_BREAK, 2.0F, 0.5F);
            return;
        }
        if (sporeCd <= 0) {
            startSpores(level, target, p2);
            return;
        }
        chase(target, 1.0);
    }

    // ------------------------------------------------------------------ Pancada de Galhos

    private void startSlam(ServerLevel level, LivingEntity target, boolean p2) {
        slamTimer = SLAM_WARMUP;
        slamCd = p2 ? 30 : 40;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        getNavigation().stop();
        action("slam");
        playSound(SoundEvents.WOOD_BREAK, 2.0F, 0.5F);
        Vec3 look = Vec3.directionFromRotation(0, attackYaw);
        Vec3 c = position().add(look.scale(2.6));
        Telegraph.delayed(level, Telegraph.Shape.circle(c, SLAM_RADIUS).warmup(SLAM_WARMUP).color(Telegraph.DANGER).owner(this).noRunes(),
                hits -> slam(level, c, look, hits));
    }

    private void slam(ServerLevel level, Vec3 c, Vec3 look, List<LivingEntity> hits) {
        if (!isAlive()) return;
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            double d = e.position().distanceTo(c);
            if (e.hurt(damageSources().mobAttack(this), dmg * (float) (1.0 - Math.min(d, SLAM_RADIUS) / 8.0))) {
                e.setDeltaMovement(e.getDeltaMovement().add(look.x * 0.6, 0.65, look.z * 0.6));
                e.hurtMarked = true;
            }
        }
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, groundState(level, BlockPos.containing(c)));
        BossFx.ring(level, c.add(0, 0.1, 0), 1.5, 20, dust, 0.15);
        BossFx.ring(level, c.add(0, 0.1, 0), 3.0, 30, dust, 0.15);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 2, 0.6, 0.1, 0.6, 0);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.6F);
        playSound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.6F, 0.5F);
        BossKit.shake(level, c, 16, 0.9F, 10);
    }

    private BlockState groundState(ServerLevel level, BlockPos at) {
        BlockState ground = level.getBlockState(at.below());
        return ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground;
    }

    // ------------------------------------------------------------------ Raízes

    private void summonRoots(ServerLevel level) {
        boolean p2 = phase() >= 1;
        int count = 0;
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(22), p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            if (count++ >= 4) break;
            EntRoot.spawn(level, this, groundAt(level, p.position()), 20, scaled(p2 ? 7.0F : 6.0F), p2 ? 80 : 60);
        }
        level.sendParticles(BossFx.dust(CORRUPT, 1.4F), getX(), getY() + 0.2, getZ(), 30, 1.5, 0.1, 1.5, 0);
    }

    /**
     * Raízes em Linha: cada fileira é cortada em segmentos de 2 blocos cuja marca enche um pouco depois do anterior —
     * a "onda" de raízes corre pelo chão. Quem é pego leva dano, fica enraizado e é jogado para cima (uma vez por fileira).
     */
    private void startRootLine(ServerLevel level, LivingEntity target, boolean p2) {
        attackYaw = yawTo(target.position());
        rootLineTimer = ROOTLINE_CAST + 6;
        rootLineCd = (p2 ? 150 : 210) + random.nextInt(30);
        getNavigation().stop();
        holdYaw(attackYaw);
        action("root_line");
        announce("root_line");
        playSound(SoundEvents.ROOTED_DIRT_BREAK, 2.2F, 0.4F);
        playSound(SoundEvents.RAVAGER_ROAR, 1.4F, 0.5F);
        float[] angles = p2 ? new float[] {-20F, 0F, 20F} : new float[] {0F};
        for (float a : angles) rootLine(level, Vec3.directionFromRotation(0, attackYaw + a));
    }

    private void rootLine(ServerLevel level, Vec3 dir) {
        Vec3 start = position().add(dir.scale(getBbWidth() * 0.5 + 0.5));
        Vec3 end = lineEnd(level, start, dir, ROOTLINE_RANGE);
        double len = start.distanceTo(end);
        int segments = Math.max(1, (int) Math.ceil(len / ROOTLINE_SEGMENT));
        double step = len / segments;
        Set<UUID> hit = new HashSet<>();
        for (int k = 0; k < segments; k++) {
            Vec3 a = groundAt(level, start.add(dir.scale(step * k)));
            Vec3 b = groundAt(level, start.add(dir.scale(step * (k + 1))));
            Vec3 mid = a.add(b).scale(0.5);
            int warmup = ROOTLINE_CAST + 2 * k;
            Telegraph.delayed(level, Telegraph.Shape.line(a, b, ROOTLINE_WIDTH).warmup(warmup).color(EntRoot.MARK_COLOR).owner(this).noRunes(),
                    hits -> rootSegment(level, mid, dir, hits, hit));
        }
    }

    private void rootSegment(ServerLevel level, Vec3 mid, Vec3 dir, List<LivingEntity> hits, Set<UUID> alreadyHit) {
        if (!isAlive()) return;
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(0.55);
        EntRoot.spawnVisual(level, this, mid.add(side));
        EntRoot.spawnVisual(level, this, mid.subtract(side));
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F;
        for (LivingEntity e : hits) {
            if (!isFoe(e) || !alreadyHit.add(e.getUUID())) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                e.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, 40, 0), this);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.45, 0));
                e.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------ Esporos

    private void startSpores(ServerLevel level, LivingEntity target, boolean p2) {
        sporeTimer = SPORE_WARMUP;
        sporeCd = p2 ? 160 : 240;
        action("spores");
        announce("spores");
        playSound(SoundEvents.SPORE_BLOSSOM_BREAK, 2.0F, 0.5F);
        // A área fica marcada enquanto a nuvem existir (a nuvem não encolhe: o que está marcado envenena).
        Telegraph.show(level, Telegraph.Shape.circle(position(), 5.0F).warmup(SPORE_WARMUP).hold(SPORE_TICKS).color(SPORE_COLOR).owner(this));
        sporeSpot = null;
        if (p2) {
            sporeSpot = groundAt(level, target.position());
            Telegraph.show(level, Telegraph.Shape.circle(sporeSpot, 3.0F).warmup(SPORE_WARMUP).hold(SPORE_TICKS).color(SPORE_COLOR).owner(this));
        }
    }

    private void releaseSpores(ServerLevel level, boolean p2) {
        sporeCloud(level, position(), 5.0F, p2 ? 1 : 0);
        if (sporeSpot != null) sporeCloud(level, sporeSpot, 3.0F, 0);
        sporeSpot = null;
        Vec3 crown = position().add(0, 4.0, 0);
        level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, crown.x, crown.y, crown.z, 60, 2.0, 0.6, 2.0, 0);
        level.sendParticles(BossFx.dust(CORRUPT, 1.6F), crown.x, crown.y, crown.z, 40, 2.0, 0.8, 2.0, 0);
        playSound(SoundEvents.BIG_DRIPLEAF_TILT_DOWN, 2.0F, 0.5F);
        playSound(SoundEvents.SPORE_BLOSSOM_BREAK, 2.0F, 0.6F);
    }

    private void sporeCloud(ServerLevel level, Vec3 at, float radius, int amp) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
        cloud.setOwner(this);
        cloud.setRadius(radius);
        cloud.setRadiusOnUse(0F);
        cloud.setWaitTime(0);
        cloud.setDuration(SPORE_TICKS);
        cloud.setRadiusPerTick(0F);
        cloud.setParticle(ParticleTypes.SPORE_BLOSSOM_AIR);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, amp));
        level.addFreshEntity(cloud);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SPORE_BLOSSOM_PLACE, SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    /** Regenera pisando em terra viva, a não ser que esteja queimando. */
    private void regenerate(ServerLevel level, boolean p2) {
        if (isOnFire() || getHealth() >= getMaxHealth()) return;
        BlockState below = level.getBlockState(blockPosition().below());
        if (!(below.is(BlockTags.DIRT) || below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.MOSS_BLOCK))) return;
        heal(p2 ? 3.0F : 2.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.2, getZ(), 6, 1.0, 0.2, 1.0, 0);
        level.sendParticles(ParticleTypes.COMPOSTER, getX(), getY() + 0.4, getZ(), 4, 0.8, 0.4, 0.8, 0);
        if (!regenHinted) {
            regenHinted = true;
            announce("regen");
        }
    }

    // ------------------------------------------------------------------ Fases, reset, loot

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        action("roar");
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.4F);
        BossKit.shake(level, position(), 24, 1.2F, 20);
        // Anel de raízes em volta dele (cada uma com a marca no chão).
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            EntRoot.spawn(level, this, groundAt(level, position().add(Math.cos(a) * 4.0, 0, Math.sin(a) * 4.0)), 15, scaled(7.0F), 60);
        }
        setFrenzy(true);
        rootCd = 40;
        sporeCd = 80;
        rootLineCd = Math.min(rootLineCd, 70);
    }

    @Override
    protected void onReset(ServerLevel level) {
        slamTimer = rootTimer = sporeTimer = rootLineTimer = 0;
        slamCd = 20;
        rootCd = 60;
        sporeCd = 140;
        rootLineCd = 100;
        sporeSpot = null;
        setFrenzy(false);
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 2.0F, 0.4F);
        playSound(SoundEvents.WOOD_BREAK, 2.0F, 0.4F);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 4 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState(level, blockPosition())), getX(), getY() + 0.1, getZ(),
                    15, 1.5, 0.1, 1.5, 0.15);
        }
        if (t == 40) {
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.4F);
            level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, getX(), getY() + 4.2, getZ(), 50, 2.0, 0.5, 2.0, 0);
            BossKit.shake(level, position(), 24, 1.0F, 16);
        }
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropById("ancestral_sapling", 1, 1);
        dropItem(CoreRegistries.ESSENCES.get(Element.EARTH).get(), 3, 4);
        dropItem(CoreRegistries.ESSENCES.get(Element.WATER).get(), 1, 2);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(BossFx.reduced() ? 10 : 3) == 0) {
            level().addParticle(ParticleTypes.FALLING_SPORE_BLOSSOM, getRandomX(1.2), getY() + 3.8 + random.nextDouble() * 0.6, getRandomZ(1.2), 0, 0, 0);
        }
        if (phase() >= 1 && random.nextInt(4) == 0) {
            level().addParticle(BossFx.dust(0xC66BFF, 1.0F), getRandomX(1.2), getY() + 4.0 + random.nextDouble(), getRandomZ(1.2), 0, 0.02, 0);
        }
    }

    // ------------------------------------------------------------------ Sons e dados

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.8F, 0.5F);
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("RegenHinted", regenHinted);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        regenHinted = tag.getBoolean("RegenHinted");
        if (phase() >= 1) setFrenzy(true);
    }

    /** Fase 2: +15% de velocidade (modificador permanente com id fixo). */
    private void setFrenzy(boolean on) {
        AttributeInstance inst = getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        inst.removeModifier(FRENZY);
        if (on) inst.addPermanentModifier(new AttributeModifier(FRENZY, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }
}
