package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import java.util.ArrayList;
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
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Patrono Aprisionado (Altar Profano): demônio preso por 4 correntes cravadas no chão.
 * <ul>
 *   <li>Cada 20% de vida perdida <b>parte uma corrente</b>: ele fica mais forte, mais rápido e alcança mais longe;</li>
 *   <li><b>Fogo Negro</b>: bolas de fogo das trevas que explodem (1 → 3 conforme se solta);</li>
 *   <li><b>Golpe de Corrente</b>: patada em leque na frente (o leque fica marcado no chão);</li>
 *   <li><b>Garra Infernal</b> (preso): marca uma faixa até quem está fora do alcance e puxa quem não sair dela;</li>
 *   <li><b>Corrente Infernal</b> (com 1+ corrente partida): arremessa uma corrente em chamas numa linha marcada
 *       (três em leque com 3+ correntes partidas);</li>
 *   <li><b>Nova Infernal</b> (com 3+ correntes partidas): onda rasteira de fogo das almas — pule por cima dela;</li>
 *   <li>totalmente livre, persegue o alvo pela arena.</li>
 * </ul>
 * Deixa Giz Ritual (ritual do Bruxo) se o módulo existir.
 */
public class ImprisonedPatron extends ClassBossEntity {
    private static final EntityDataAccessor<Integer> DATA_CHAINS = SynchedEntityData.defineId(ImprisonedPatron.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> DATA_HOME = SynchedEntityData.defineId(ImprisonedPatron.class, EntityDataSerializers.BLOCK_POS);
    private static final ResourceLocation RAGE_DAMAGE = IsekaiMahou.id("imprisoned_patron_rage_damage");
    private static final ResourceLocation RAGE_SPEED = IsekaiMahou.id("imprisoned_patron_rage_speed");
    private static final int COLOR = 0xC0182E;
    private static final int ALL_CHAINS = 0b1111;
    /** Pontos de ancoragem das correntes em volta do lugar de origem (mão direita, mão esquerda, pé direito, pé esquerdo). */
    private static final double[][] ANCHORS = {{-4.5, -4.5}, {4.5, -4.5}, {-4.0, 4.0}, {4.0, 4.0}};
    private static final double REACH = 4.6;
    private static final int SWIPE_WARMUP = 10;
    private static final float SWIPE_ANGLE = 150F;
    private static final int GRIP_WARMUP = 14;
    private static final int NOVA_WARMUP = 20;
    private static final float NOVA_RADIUS = 8.1F;
    private static final int SOUL_FIRE = 0x4FD6FF;
    private static final int LASH_WARMUP = 22;
    private static final int LASH_COLOR = 0xFF5A1F;
    private static final float LASH_WIDTH = 2.0F;

    private boolean homeSet;
    private int swipeCd = 20;
    private int fireCd = 50;
    private int gripCd = 160;
    private int novaCd = 120;
    private int lashCd = 80;
    private int swipeTimer;
    private int lashTimer;
    private float attackYaw;
    private int swipeMark = -1;
    @Nullable
    private Vec3 swipeOrigin;
    private int castTimer;
    private int gripTimer;
    private int novaTimer;
    private int novaStep = -1;
    /** Centro da Nova (onde a marca foi desenhada): a onda nasce ali mesmo que ele ande durante ela. */
    @Nullable
    private Vec3 novaCenter;
    private final Set<UUID> novaHit = new HashSet<>();

    public ImprisonedPatron(EntityType<? extends ImprisonedPatron> type, Level level) {
        super(type, level);
        this.xpReward = 200;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(250.0, 10.0, 0.25, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2);
    }

    @Override
    public String bossId() {
        return "imprisoned_patron";
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
        return new float[] {0.8F, 0.6F, 0.4F, 0.2F};
    }

    @Override
    protected boolean phaseCutscene(int phase) {
        return phase == 2 || phase == 4;
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("swipe", "cast", "grip", "roar", "nova", "chain_lash");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHAINS, ALL_CHAINS);
        builder.define(DATA_HOME, BlockPos.ZERO);
    }

    // ------------------------------------------------------------------ Correntes

    /** Máscara de bits das correntes inteiras (bit i = corrente i). */
    public int chainMask() {
        return entityData.get(DATA_CHAINS);
    }

    public int chainsLeft() {
        return Integer.bitCount(chainMask());
    }

    public BlockPos home() {
        return entityData.get(DATA_HOME);
    }

    /** Ponto (no mundo) onde a corrente {@code i} está cravada. */
    public Vec3 anchor(int i) {
        BlockPos h = home();
        return new Vec3(h.getX() + 0.5 + ANCHORS[i][0], h.getY() + 0.05, h.getZ() + 0.5 + ANCHORS[i][1]);
    }

    /** Distância máxima que as correntes deixam ele andar (negativo = livre). */
    private double leashRadius() {
        int left = chainsLeft();
        return left > 0 ? 2.5 + (4 - left) * 1.5 : -1;
    }

    private void setHome(BlockPos pos) {
        entityData.set(DATA_HOME, pos);
        homeSet = true;
        applyRage();
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        setHome(blockPosition());
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isDeadOrDying()) return;
        if (!homeSet) setHome(blockPosition());
        holdByChains();
    }

    /** As correntes puxam de volta quem passa do comprimento delas (inclusive se for empurrado). */
    private void holdByChains() {
        double r = leashRadius();
        if (r < 0) return;
        Vec3 h = Vec3.atBottomCenterOf(home());
        Vec3 off = position().subtract(h).multiply(1, 0, 1);
        double d = off.length();
        if (d <= r) return;
        Vec3 dir = off.normalize();
        if (d > r + 2.0) {
            Vec3 to = h.add(dir.scale(r));
            teleportTo(to.x, getY(), to.z);
        } else {
            setDeltaMovement(-dir.x * 0.25, getDeltaMovement().y, -dir.z * 0.25);
            hasImpulse = true;
        }
        if (tickCount % 20 == 0) playSound(SoundEvents.CHAIN_HIT, 1.2F, 0.6F);
    }

    private void breakChain(ServerLevel level) {
        int mask = chainMask();
        List<Integer> intact = new ArrayList<>();
        for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0) intact.add(i);
        if (intact.isEmpty()) return;
        int i = intact.get(random.nextInt(intact.size()));
        entityData.set(DATA_CHAINS, mask & ~(1 << i));
        Vec3 a = anchor(i);
        Vec3 mid = a.add(position().add(0, 1.5, 0)).scale(0.5);
        ItemParticleOption links = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.CHAIN));
        level.sendParticles(links, mid.x, mid.y, mid.z, 30, 1.2, 0.8, 1.2, 0.15);
        level.sendParticles(links, a.x, a.y + 0.3, a.z, 15, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, a.x, a.y + 0.3, a.z, 20, 0.3, 0.5, 0.3, 0.05);
        level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 2, getZ(), 8, 0.6, 0.6, 0.6, 0);
        playSound(SoundEvents.CHAIN_BREAK, 3.0F, 0.5F);
        playSound(SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.6F);
        BossKit.shake(level, position(), 28, 1.4F, 18);
        action("roar");
        applyRage();
    }

    /** Cada corrente partida: +10% de dano e +8% de velocidade. */
    private void applyRage() {
        int broken = 4 - chainsLeft();
        setModifier(Attributes.ATTACK_DAMAGE, RAGE_DAMAGE, 0.10 * broken);
        setModifier(Attributes.MOVEMENT_SPEED, RAGE_SPEED, 0.08 * broken);
        double r = leashRadius();
        if (r > 0) restrictTo(home(), (int) Math.ceil(r));
        else clearRestriction();
    }

    private void setModifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id, double amount) {
        AttributeInstance inst = getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount > 0) inst.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected boolean isBusy() {
        return swipeTimer > 0 || castTimer > 0 || gripTimer > 0 || novaTimer > 0 || lashTimer > 0 || novaStep >= 0;
    }

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        int broken = 4 - chainsLeft();
        if (swipeCd > 0) swipeCd--;
        if (fireCd > 0) fireCd--;
        if (gripCd > 0) gripCd--;
        if (novaCd > 0) novaCd--;
        if (lashCd > 0) lashCd--;
        tickNovaRing(level);

        if (swipeTimer > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            if (--swipeTimer == 0) doSwipe(level);
            return;
        }
        if (castTimer > 0) {
            getNavigation().stop();
            if (target != null) faceTarget(target);
            Vec3 hand = handPos(false);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y, hand.z, 3, 0.15, 0.15, 0.15, 0.01);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, hand.x, hand.y, hand.z, 1, 0.1, 0.1, 0.1, 0.01);
            if (--castTimer == 0 && target != null) shootFire(level, target, broken);
            return;
        }
        if (gripTimer > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            Vec3 hand = handPos(true);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y, hand.z, 2, 0.15, 0.15, 0.15, 0.01);
            gripTimer--;
            return;
        }
        if (lashTimer > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            Vec3 hand = handPos(true);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y, hand.z, 3, 0.2, 0.2, 0.2, 0.02);
            if (lashTimer % 4 == 0) level.sendParticles(ParticleTypes.LAVA, hand.x, hand.y, hand.z, 1, 0.1, 0.1, 0.1, 0);
            lashTimer--;
            return;
        }
        if (novaTimer > 0) {
            getNavigation().stop();
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.2, getZ(), 6, 1.2, 0.1, 1.2, 0.02);
            if (--novaTimer == 0) {
                novaStep = 0;
                novaHit.clear();
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.6F);
                playSound(SoundEvents.BLAZE_SHOOT, 2.0F, 0.5F);
                BossKit.shake(level, position(), 20, 1.2F, 14);
            }
            return;
        }
        if (target == null) return;

        double dist = distanceTo(target);
        double r = leashRadius();
        if (dist <= REACH && swipeCd <= 0) {
            startSwipe(level, target, broken);
            return;
        }
        if (broken >= 3 && novaCd <= 0 && dist < 7) {
            startNova(level);
            return;
        }
        if (broken >= 1 && lashCd <= 0 && dist > REACH && dist < 20 && hasLineOfSight(target)) {
            startLash(level, target, broken);
            return;
        }
        if (r > 0 && gripCd <= 0 && dist > REACH + 1.5 && dist < 22 && hasLineOfSight(target)) {
            startGrip(level, target);
            return;
        }
        if (fireCd <= 0 && hasLineOfSight(target)) {
            castTimer = 12;
            fireCd = Math.max(28, 70 - 9 * broken) + random.nextInt(15);
            action("cast");
            playSound(SoundEvents.BLAZE_AMBIENT, 1.5F, 0.5F);
            return;
        }
        // Movimento: livre persegue; preso vai até onde as correntes deixam.
        if (r < 0) {
            chase(target, 1.15);
        } else {
            Vec3 h = Vec3.atBottomCenterOf(home());
            Vec3 off = target.position().subtract(h).multiply(1, 0, 1);
            if (off.length() > r - 0.5) off = off.normalize().scale(Math.max(0, r - 0.5));
            Vec3 to = h.add(off);
            if (tickCount % 10 == 0) getNavigation().moveTo(to.x, getY(), to.z, 1.0);
            getLookControl().setLookAt(target, 30F, 30F);
        }
    }

    private void faceTarget(LivingEntity target) {
        getLookControl().setLookAt(target, 30F, 30F);
        lookAt(target, 30F, 30F);
    }

    /** Mão esquerda (fogo) ou direita (garra), aproximada pela direção do corpo. */
    private Vec3 handPos(boolean right) {
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 side = new Vec3(-look.z, 0, look.x).scale(right ? -1 : 1);
        return position().add(look.scale(1.0)).add(side.scale(0.8)).add(0, 2.4, 0);
    }

    private Telegraph.Shape swipeCone() {
        Vec3 o = swipeOrigin != null ? swipeOrigin : position();
        return Telegraph.Shape.cone(o, attackYaw, SWIPE_ANGLE, (float) REACH + 0.5F);
    }

    /** Patada: o leque fica marcado no chão por 10 ticks com a direção travada (dá para sair ou passar por trás). */
    private void startSwipe(ServerLevel level, LivingEntity target, int broken) {
        swipeTimer = SWIPE_WARMUP;
        swipeCd = broken >= 4 ? 22 : 32;
        attackYaw = yawTo(target.position());
        swipeOrigin = position();
        holdYaw(attackYaw);
        getNavigation().stop();
        swipeMark = Telegraph.show(level, swipeCone().warmup(SWIPE_WARMUP).color(Telegraph.DANGER).owner(this).noRunes());
        action("swipe");
        playSound(SoundEvents.RAVAGER_ATTACK, 1.5F, 0.6F);
    }

    private void doSwipe(ServerLevel level) {
        Vec3 look = Vec3.directionFromRotation(0, attackYaw);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity e : Telegraph.entitiesIn(level, swipeCone())) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                e.knockback(1.2, -look.x, -look.z);
                e.igniteForSeconds(2);
            }
        }
        if (swipeMark >= 0) Telegraph.finish(level, swipeMark);
        swipeMark = -1;
        swipeOrigin = null;
        Vec3 c = position().add(look.scale(2.5)).add(0, 1.2, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 3, 0.8, 0.2, 0.8, 0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 15, 1.2, 0.3, 1.2, 0.03);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
        playSound(SoundEvents.CHAIN_HIT, 1.5F, 0.7F);
    }

    private void shootFire(ServerLevel level, LivingEntity target, int broken) {
        int count = broken >= 4 ? 3 : broken >= 2 ? 2 : 1;
        Vec3 from = handPos(false);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize();
        Vec3 side = new Vec3(-to.z, 0, to.x).normalize();
        for (int i = 0; i < count; i++) {
            double spread = count == 1 ? 0 : (i / (double) (count - 1) - 0.5) * 0.5;
            Vec3 dir = to.add(side.scale(spread)).normalize();
            ClassBossBolt.fire(this, ClassBossBolt.Kind.HELLFIRE, from, dir, 0.75, scaled(7.0F + broken), 0.025, target, 1.4F, 80);
        }
        playSound(SoundEvents.GHAST_SHOOT, 1.5F, 0.5F);
        playSound(SoundEvents.FIRECHARGE_USE, 1.5F, 0.6F);
    }

    // ------------------------------------------------------------------ Garra Infernal

    /** Marca uma faixa de fogo até o alvo; quem continuar nela quando encher é puxado até as garras. */
    private void startGrip(ServerLevel level, LivingEntity target) {
        gripTimer = GRIP_WARMUP + 2;
        gripCd = 220;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        getNavigation().stop();
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 start = position().add(dir.scale(1.0));
        Vec3 end = lineEnd(level, start, dir, Math.min(22.0, distanceTo(target) + 2.5));
        Telegraph.delayed(level, Telegraph.Shape.line(start, end, 1.8F).warmup(GRIP_WARMUP).color(SOUL_FIRE).owner(this),
                hits -> grip(level, start, end, hits));
        action("grip");
        announce("grip");
        playSound(SoundEvents.CHAIN_PLACE, 2.0F, 0.5F);
    }

    private void grip(ServerLevel level, Vec3 start, Vec3 end, List<LivingEntity> hits) {
        if (!isAlive()) return;
        BossFx.line(level, start.add(0, 1.0, 0), end.add(0, 1.0, 0), ParticleTypes.SOUL_FIRE_FLAME, 1.5);
        boolean any = false;
        for (LivingEntity target : hits) {
            if (!isFoe(target)) continue;
            Vec3 pull = position().subtract(target.position());
            double d = pull.length();
            if (d < 1.0E-3) continue;
            double strength = Math.min(2.2, d * 0.16);
            Vec3 v = pull.normalize().scale(strength);
            target.setDeltaMovement(v.x, 0.45, v.z);
            target.hurtMarked = true;
            target.hurt(damageSources().mobAttack(this), scaled(3.0F));
            target.igniteForSeconds(2);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + 1, target.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
            any = true;
        }
        playSound(SoundEvents.CHAIN_FALL, 2.0F, 0.6F);
        if (any) swipeCd = 0;
    }

    // ------------------------------------------------------------------ Corrente Infernal

    /** Arremessa correntes em chamas em linha reta (marcadas no chão): queimam, deixam lento e jogam para trás. */
    private void startLash(ServerLevel level, LivingEntity target, int broken) {
        lashTimer = LASH_WARMUP + 4;
        lashCd = (broken >= 3 ? 150 : 200) + random.nextInt(30);
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        getNavigation().stop();
        action("chain_lash");
        announce("chain_lash");
        playSound(SoundEvents.CHAIN_PLACE, 2.2F, 0.4F);
        playSound(SoundEvents.BLAZE_AMBIENT, 1.6F, 0.5F);
        float[] angles = broken >= 3 ? new float[] {-24F, 0F, 24F} : new float[] {0F};
        double range = chainsLeft() == 0 ? 22.0 : 16.0;
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8F;
        for (int i = 0; i < angles.length; i++) {
            Vec3 dir = Vec3.directionFromRotation(0, attackYaw + angles[i]);
            Vec3 start = position().add(dir.scale(1.2));
            Vec3 end = lineEnd(level, start, dir, range);
            boolean loud = i == angles.length / 2;
            Telegraph.delayed(level, Telegraph.Shape.line(start, end, LASH_WIDTH).warmup(LASH_WARMUP).color(LASH_COLOR).owner(this),
                    hits -> lashHit(level, start, end, dir, hits, dmg, loud));
        }
    }

    private void lashHit(ServerLevel level, Vec3 start, Vec3 end, Vec3 dir, List<LivingEntity> hits, float dmg, boolean loud) {
        if (!isAlive()) return;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                e.igniteForSeconds(4);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                e.push(dir.x * 0.6, 0.35, dir.z * 0.6);
                e.hurtMarked = true;
            }
        }
        // A corrente em brasa estala pelo chão de ponta a ponta.
        ItemParticleOption links = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.CHAIN));
        Vec3 a = start.add(0, 0.6, 0);
        Vec3 b = end.add(0, 0.6, 0);
        BossFx.line(level, a, b, links, 1.2);
        BossFx.line(level, a, b, ParticleTypes.SOUL_FIRE_FLAME, 1.0);
        level.sendParticles(ParticleTypes.LAVA, b.x, b.y, b.z, 6, 0.3, 0.2, 0.3, 0);
        if (loud) {
            level.playSound(null, start.x, start.y, start.z, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
            level.playSound(null, start.x, start.y, start.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.6F, 0.6F);
            BossKit.shake(level, start, 16, 0.7F, 8);
        }
    }

    // ------------------------------------------------------------------ Nova Infernal

    /** Onda rasteira: a área inteira acende no chão; a onda se espalha do centro e quem pular por cima escapa. */
    private void startNova(ServerLevel level) {
        novaTimer = NOVA_WARMUP;
        novaCd = 260;
        getNavigation().stop();
        novaCenter = position();
        Telegraph.show(level, Telegraph.Shape.circle(novaCenter, NOVA_RADIUS).warmup(NOVA_WARMUP).hold(16).color(SOUL_FIRE)
                .vertical(0.5, 1.0).owner(this));
        action("nova");
        announce("nova");
        hintOnce(level, "nova");
        playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.7F);
    }

    private void tickNovaRing(ServerLevel level) {
        if (novaStep < 0) return;
        double r = 0.6 + novaStep * 0.5;
        // A onda se espalha do ponto marcado no chão (ele pode voltar a andar enquanto ela corre).
        Vec3 c = novaCenter != null ? novaCenter : position();
        BossFx.ring(level, c.add(0, 0.2, 0), r, (int) (10 + r * 8), ParticleTypes.SOUL_FIRE_FLAME, 0.01);
        if (novaStep % 2 == 0) BossFx.ring(level, c.add(0, 0.4, 0), r, (int) (6 + r * 4), ParticleTypes.LARGE_SMOKE, 0.01);
        float dmg = scaled(9.0F);
        AABB area = new AABB(c.x - r - 1, c.y - 1, c.z - r - 1, c.x + r + 1, c.y + 3, c.z + r + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> isFoe(e) && !novaHit.contains(e.getUUID()))) {
            double d = e.position().subtract(c).multiply(1, 0, 1).length();
            if (Math.abs(d - r) > 0.8) continue;
            // Onda rasteira: quem está no ar (pulando) passa por cima.
            if (e.getY() - c.y > 1.0) continue;
            novaHit.add(e.getUUID());
            if (e.hurt(damageSources().indirectMagic(this, this), dmg)) {
                e.igniteForSeconds(5);
                Vec3 out = e.position().subtract(c).multiply(1, 0, 1);
                out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : out.normalize();
                e.push(out.x * 0.8, 0.4, out.z * 0.8);
                e.hurtMarked = true;
            }
        }
        if (++novaStep > 15) {
            novaStep = -1;
            novaCenter = null;
        }
    }

    /** Alvo fora do alcance: preso, puxa com a garra (se enxergar); livre, salta até ele. */
    @Override
    protected void onUnreachable(ServerLevel level, LivingEntity target) {
        if (chainsLeft() > 0) {
            if (hasLineOfSight(target)) gripCd = 0;
            return;
        }
        super.onUnreachable(level, target);
    }

    // ------------------------------------------------------------------ Fases, reset, loot

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        breakChain(level);
        // A primeira corrente partida libera a Corrente Infernal logo em seguida.
        if (phase == 1) lashCd = Math.min(lashCd, 50);
        if (phase == 4) {
            playSound(SoundEvents.WITHER_SPAWN, 1.0F, 1.2F);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.5, getZ(), 80, 1.5, 1.5, 1.5, 0.15);
            fireCd = 0;
            novaCd = 40;
        }
    }

    @Override
    protected void onReset(ServerLevel level) {
        // As correntes se refazem: o selo volta inteiro e ele volta para o centro do círculo.
        entityData.set(DATA_CHAINS, ALL_CHAINS);
        applyRage();
        swipeTimer = castTimer = gripTimer = novaTimer = lashTimer = 0;
        novaStep = -1;
        novaCenter = null;
        swipeMark = -1;
        swipeOrigin = null;
        swipeCd = 20;
        fireCd = 50;
        gripCd = 160;
        novaCd = 120;
        lashCd = 80;
    }

    @Override
    protected BlockPos resetPos() {
        return homeSet ? home() : super.resetPos();
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.CHAIN_FALL, 2.5F, 0.5F);
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.5F);
        applyRage();
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 6 == 0) {
            for (int i = 0; i < 4; i++) {
                if ((chainMask() & (1 << i)) == 0) continue;
                Vec3 a = anchor(i);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, a.x, a.y + 0.2, a.z, 3, 0.15, 0.1, 0.15, 0.02);
            }
            playSound(SoundEvents.CHAIN_HIT, 1.5F, 0.5F + random.nextFloat() * 0.2F);
        }
        if (t == 36) {
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
            BossFx.ring(level, position().add(0, 0.2, 0), 3.5, 40, ParticleTypes.SOUL_FIRE_FLAME, 0.03);
        }
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropById("ritual_chalk_item", 3, 5);
        dropItem(CoreRegistries.ESSENCES.get(Element.SHADOW).get(), 3, 4);
        dropItem(CoreRegistries.ESSENCES.get(Element.FIRE).get(), 2, 3);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(BossFx.reduced() ? 8 : 2) == 0) {
            level().addParticle(ParticleTypes.SMOKE, getRandomX(0.8), getY() + 2.6 + random.nextDouble() * 0.6, getRandomZ(0.8), 0, 0.03, 0);
        }
        if (random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getRandomX(0.7), getY() + 1.6 + random.nextDouble(), getRandomZ(0.7), 0, 0.02, 0);
        }
    }

    // ------------------------------------------------------------------ Sons e dados

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + random.nextFloat() * 0.1F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Chains", chainMask());
        tag.put("Home", NbtUtils.writeBlockPos(home()));
        tag.putBoolean("HomeSet", homeSet);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_CHAINS, tag.contains("Chains") ? tag.getInt("Chains") & ALL_CHAINS : ALL_CHAINS);
        homeSet = tag.getBoolean("HomeSet");
        NbtUtils.readBlockPos(tag, "Home").ifPresent(p -> entityData.set(DATA_HOME, p));
        applyRage();
    }
}
