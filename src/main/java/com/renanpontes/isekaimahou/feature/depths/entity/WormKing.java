package com.renanpontes.isekaimahou.feature.depths.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.GBossEntity;
import com.renanpontes.isekaimahou.feature.depths.DepthsRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Verme-Rei das Profundezas — no fundo da Masmorra em Camadas. Vive ENTERRADO: quando dorme é só um monte de terra
 * que pulsa no meio do ninho. Ciclo de luta:
 * <ul>
 *   <li><b>Fora da terra</b> (alguns segundos, parado): Mordida (cone), Cuspe Ácido (linha: veneno + Exposto),
 *       Pancada Sísmica (anel — colado nele ou longe é seguro);</li>
 *   <li><b>Mergulho</b>: some na terra (intocável), persegue um jogador por baixo do chão e IRROMPE embaixo dele
 *       (círculo marcado; joga para o alto). É a hora de correr — e de bater quando ele sair.</li>
 * </ul>
 * Fases: 60% — <b>Ninhada</b> (invoca Vermes Cavadores e tremores extras em outros jogadores); 30% — <b>Fúria das
 * Profundezas</b> (irrompe 3 vezes seguidas e faz cair estalactites do teto). Cristais de mana cresceram na carapaça
 * de tanto comer geodos. Base comum (sono, intro, barra, arena selada, grupo, recompensas): {@link GBossEntity}.
 */
public class WormKing extends GBossEntity {
    private static final EntityDataAccessor<Boolean> DATA_BURROWED = SynchedEntityData.defineId(WormKing.class, EntityDataSerializers.BOOLEAN);
    public static final int COLOR = 0xC8873E;
    private static final int EARTH = 0x9A6A3A;
    private static final int ACID = 0x9BE04A;
    private static final int MAX_LARVAE = 5;
    private static final EntityDimensions BURIED = EntityDimensions.scalable(2.4F, 0.8F);

    private int emergedTicks;
    private int burrowAnim;
    private int travelTicks;
    private int eruptPending;
    private int chainLeft;
    private boolean eruptOnTarget;
    private int attackTimer;
    private float attackYaw;
    private int biteCd = 30;
    private int spitCd = 70;
    private int slamCd = 120;
    private int summonCd = 200;
    private int dripCd = 100;

    public WormKing(EntityType<? extends WormKing> type, Level level) {
        super(type, level);
        this.xpReward = 260;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(400.0, 10.0, 0.3, 14.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public String bossId() {
        return "depths_worm_king";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.YELLOW;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.6F, 0.3F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("burrow", "emerge", "bite", "spit", "slam", "summon");
    }

    @Override
    protected double arenaRadius() {
        return 24.0;
    }

    @Override
    protected double wakeRange() {
        return 14.0;
    }

    @Override
    protected int rewardXp() {
        return 380;
    }

    @Override
    public String musicTheme() {
        return "primordial_dragon";
    }

    /** Ele sente os passos pelo chão: acorda sem precisar ver. */
    @Override
    protected boolean canWakeSee(net.minecraft.world.entity.player.Player p) {
        return true;
    }

    // ------------------------------------------------------------------ Estado enterrado

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BURROWED, true);
    }

    public boolean isBurrowed() {
        return entityData.get(DATA_BURROWED);
    }

    private void setBurrowed(boolean b) {
        if (isBurrowed() != b) entityData.set(DATA_BURROWED, b);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_BURROWED.equals(key)) refreshDimensions();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return isBurrowed() ? BURIED : super.getDefaultDimensions(pose);
    }

    @Override
    public List<String> toggledBones() {
        return List.of("body");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return isDeadOrDying() || !isBurrowed();
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (isDeadOrDying()) return hold("death");
        if (introTicks() > 0) return play("intro");
        if (!isAwake()) return loop("sleep");
        return loop(isBurrowed() ? "burrowed" : "idle");
    }

    @Override
    public boolean isPickable() {
        return super.isPickable() && (!isBurrowed() || !isAwake());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isFoe(LivingEntity e) {
        return !DepthsMob.isDepthsSide(e) && super.isFoe(e);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return DepthsMob.isDepthsSide(other) || super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != this && DepthsMob.isDepthsSide(attacker)) return false;
        if (!level().isClientSide && isAwake() && isBurrowed() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (level() instanceof ServerLevel sl) {
                dirt(sl, position(), 6);
                hintOnce(sl, "buried");
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ Fases e intro

    @Override
    protected void onIntroStart(ServerLevel level) {
        setBurrowed(false);
        emergedTicks = 0;
        BossKit.shake(level, position(), 32, 1.2F, 30);
        BossKit.debris(level, position(), 3.0, 24);
        BossKit.roar(this, SoundEvents.RAVAGER_ROAR, 0.5F);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 4 == 0) BossKit.debris(level, position(), 2.5, 8);
    }

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        attackTimer = 0;
        if (phase == 1) {
            summonCd = 0;
        } else if (phase == 2) {
            dripCd = 30;
            BossKit.roar(this, SoundEvents.WARDEN_ROAR, 0.6F);
            BossKit.shake(level, position(), 32, 1.5F, 30);
            BossKit.crackFloor(level, position(), 10, 6, 60);
        }
    }

    @Override
    protected void onReset(ServerLevel level) {
        setBurrowed(true);
        burrowAnim = travelTicks = eruptPending = chainLeft = attackTimer = 0;
        eruptOnTarget = false;
    }

    @Override
    protected boolean isBusy() {
        return attackTimer > 0 || burrowAnim > 0 || eruptPending > 0 || isBurrowed();
    }

    /** Anti-trapaça: mergulha e irrompe embaixo de quem fugiu para um pilar ou ficou fora de alcance. */
    @Override
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        if (isBurrowed() || burrowAnim > 0 || eruptPending > 0) return;
        eruptOnTarget = true;
        startBurrow(sl, 12);
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        if (biteCd > 0) biteCd--;
        if (spitCd > 0) spitCd--;
        if (slamCd > 0) slamCd--;
        if (summonCd > 0) summonCd--;
        if (dripCd > 0) dripCd--;
        if (burrowAnim > 0) {
            getNavigation().stop();
            if (--burrowAnim == 0) {
                setBurrowed(true);
                if (eruptOnTarget && target != null) {
                    eruptOnTarget = false;
                    beginErupt(level, target.position());
                }
            }
            return;
        }
        if (eruptPending > 0) {
            getNavigation().stop();
            if (eruptPending % 3 == 0) dirt(level, position(), 4);
            // Segurança: se o aviso sumiu sem disparar (alvo saiu, servidor parou), sai da terra mesmo assim.
            if (--eruptPending == 0 && isBurrowed()) surface(level, position(), false);
            return;
        }
        if (isBurrowed()) {
            tickBurrowed(level, target);
            return;
        }
        getNavigation().stop();
        if (attackTimer > 0) {
            attackTimer--;
            holdYaw(attackYaw);
            return;
        }
        emergedTicks++;
        if (target == null) return;
        getLookControl().setLookAt(target, 20F, 20F);
        double d = distanceTo(target);
        int ph = phase();
        int stay = ph >= 2 ? 100 : ph == 1 ? 130 : 160;
        if (emergedTicks > stay || (d > 16 && emergedTicks > 30)) {
            startBurrow(level, 20);
        } else if (ph >= 1 && summonCd <= 0 && countMinions(level, 32) < 2) {
            summonLarvae(level);
        } else if (ph >= 2 && dripCd <= 0) {
            stalactiteRain(level);
        } else if (ph >= 1 && slamCd <= 0 && d < 9) {
            slam(level);
        } else if (d < 6.5 && biteCd <= 0) {
            bite(level, target);
        } else if (d < 18 && spitCd <= 0 && hasLineOfSight(target)) {
            spit(level, target);
        }
    }

    private void startBurrow(ServerLevel level, int anim) {
        burrowAnim = anim;
        emergedTicks = 0;
        travelTicks = phase() >= 2 ? 40 : 60;
        Telegraph.cancelAll(this);
        action("burrow");
        playSound(SoundEvents.ROOTED_DIRT_BREAK, 2.0F, 0.5F);
        BossKit.debris(level, position(), 2.5, 16);
    }

    private void tickBurrowed(ServerLevel level, @Nullable LivingEntity target) {
        travelTicks--;
        if (tickCount % 2 == 0) dirt(level, position(), 5);
        if (tickCount % 12 == 0) playSound(SoundEvents.ROOTED_DIRT_BREAK, 1.6F, 0.4F);
        if (target == null) {
            if (travelTicks <= 0) beginErupt(level, position());
            return;
        }
        if (tickCount % 8 == 0) getNavigation().moveTo(target, 1.5);
        if (distanceToSqr(target) < 9.0 || travelTicks <= 0) beginErupt(level, target.position());
    }

    /** Marca o chão onde ele vai sair; ao fim do aviso ele irrompe ali e joga todos para cima. */
    private void beginErupt(ServerLevel level, Vec3 at) {
        getNavigation().stop();
        Vec3 spot = groundAt(level, at);
        int warm = phase() >= 2 ? 16 : 22;
        eruptPending = warm + 2;
        float dmg = scaled(16F);
        hintOnce(level, "erupt");
        Telegraph.delayed(level, Telegraph.Shape.circle(spot, 3.2F).warmup(warm).color(EARTH).owner(this).vertical(1.5, 5.0)
                .castName(attackName("erupt")).element(Element.EARTH), hits -> {
                    if (!isAlive()) return;
                    surface(level, spot, true);
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            e.setDeltaMovement(e.getDeltaMovement().add(0, 1.0, 0));
                            e.hurtMarked = true;
                        }
                    }
                });
        // Ninhada (fase 2+): tremores menores sob os outros jogadores do grupo.
        if (phase() >= 1) {
            float small = scaled(9F);
            for (ServerPlayer p : BossKit.partyAround(this, 32)) {
                if (p.position().distanceToSqr(spot) < 25) continue;
                Vec3 q = groundAt(level, p.position());
                Telegraph.delayed(level, Telegraph.Shape.circle(q, 2.2F).warmup(warm + 4).color(EARTH).owner(this).noRunes(), hits -> {
                    if (!isAlive()) return;
                    BossKit.debris(level, q, 1.6, 12);
                    level.playSound(null, BlockPos.containing(q), SoundEvents.POINTED_DRIPSTONE_LAND, getSoundSource(), 1.4F, 0.6F);
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), small)) {
                            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                            e.hurtMarked = true;
                        }
                    }
                });
            }
        }
    }

    /** Sai da terra (no ponto marcado, se couber). Na Fúria, volta a mergulhar e irrompe de novo até 3 vezes. */
    private void surface(ServerLevel level, Vec3 spot, boolean fromErupt) {
        eruptPending = 0;
        EntityDimensions full = getType().getDimensions();
        if (level.noCollision(this, full.makeBoundingBox(spot))) {
            teleportTo(spot.x, spot.y, spot.z);
        } else {
            Vec3 alt = findGroundSpot(level, spot, 0.0, 3.0, getType(), null);
            if (alt != null) teleportTo(alt.x, alt.y, alt.z);
        }
        setDeltaMovement(Vec3.ZERO);
        setBurrowed(false);
        emergedTicks = 0;
        action("emerge");
        BossKit.debris(level, position(), 3.0, 24);
        BossKit.shake(level, position(), 20, 1.1F, 14);
        BossKit.crackFloor(level, position(), 3.5, 7, 40);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.6F);
        BossKit.roar(this, SoundEvents.RAVAGER_ROAR, 0.6F);
        if (!fromErupt) return;
        if (phase() >= 2) {
            if (chainLeft <= 0) chainLeft = 2;
            else chainLeft--;
            if (chainLeft > 0) {
                attackTimer = 0;
                startBurrow(level, 10);
                travelTicks = 14;
                return;
            }
        }
        attackTimer = 16;
    }

    private float meleeDamage() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** Mordida: cone de 75° à frente; faz sangrar. */
    private void bite(ServerLevel level, LivingEntity target) {
        biteCd = phase() >= 2 ? 26 : 38;
        attackTimer = 16;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("bite");
        playSound(SoundEvents.RAVAGER_ATTACK, 1.6F, 0.5F);
        float dmg = meleeDamage();
        Telegraph.delayed(level, Telegraph.Shape.cone(position(), attackYaw, 75F, 6.5F).warmup(14).color(Telegraph.DANGER).owner(this)
                .noRunes().castName(attackName("bite")), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            Statuses.apply(e, Statuses.Kind.BLEEDING, 1, 80, this, 1F);
                        }
                    }
                    playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.6F, 0.6F);
                });
    }

    /** Cuspe Ácido: linha longa; veneno + Exposto (o ácido derrete a armadura). */
    private void spit(ServerLevel level, LivingEntity target) {
        spitCd = 90 + random.nextInt(30);
        attackTimer = 20;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("spit");
        playSound(SoundEvents.LLAMA_SPIT, 2.0F, 0.4F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = position().add(dir.scale(1.2));
        Vec3 to = lineEnd(level, from, dir, 16.0);
        float dmg = scaled(10F);
        Telegraph.delayed(level, Telegraph.Shape.line(from, to, 2.2F).warmup(18).color(ACID).owner(this)
                .castName(attackName("spit")).element(Element.EARTH), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().indirectMagic(this, this), dmg)) {
                            e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1), this);
                            Statuses.apply(e, Statuses.Kind.EXPOSED, 1, 120, this, 1F);
                        }
                    }
                    for (int i = 0; i <= 14; i++) {
                        Vec3 p = from.add(to.subtract(from).scale(i / 14.0));
                        level.sendParticles(ParticleTypes.ITEM_SLIME, p.x, p.y + 0.6, p.z, 3, 0.3, 0.2, 0.3, 0.02);
                        level.sendParticles(ParticleTypes.SNEEZE, p.x, p.y + 0.4, p.z, 1, 0.2, 0.1, 0.2, 0.01);
                    }
                    playSound(SoundEvents.SLIME_SQUISH, 1.6F, 0.5F);
                });
    }

    /** Pancada Sísmica: anel de 3 a 9 blocos; colado nele ou longe é seguro. */
    private void slam(ServerLevel level) {
        slamCd = 180 + random.nextInt(60);
        attackTimer = 28;
        action("slam");
        playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 2.0F, 0.5F);
        Vec3 c = position();
        float dmg = scaled(13F);
        Telegraph.safe(level, c, 2.6F, 30);
        Telegraph.delayed(level, Telegraph.Shape.ring(c, 3.0F, 9.0F).warmup(26).color(EARTH).owner(this)
                .castName(attackName("slam")).element(Element.EARTH), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            Vec3 away = e.position().subtract(c).multiply(1, 0, 1);
                            if (away.lengthSqr() > 1.0E-4) away = away.normalize();
                            e.setDeltaMovement(e.getDeltaMovement().add(away.x * 0.9, 0.5, away.z * 0.9));
                            e.hurtMarked = true;
                        }
                    }
                    for (int i = 0; i < 6; i++) {
                        double a = Math.PI * 2 * i / 6;
                        BossKit.debris(level, c.add(Math.cos(a) * 6, 0, Math.sin(a) * 6), 1.5, 8);
                    }
                    BossKit.shake(level, c, 20, 1.0F, 12);
                    BossKit.crackFloor(level, c, 9, 5, 40);
                });
    }

    /** Ninhada: Vermes Cavadores saem da terra em volta dele. */
    private void summonLarvae(ServerLevel level) {
        summonCd = 320 + random.nextInt(80);
        attackTimer = 20;
        action("summon");
        announce("summon");
        BossKit.roar(this, SoundEvents.RAVAGER_ROAR, 1.2F);
        EntityType<?> type = DepthsRegistries.BURROWER.get();
        int want = Math.min(MAX_LARVAE - countMinions(level, 32), 1 + BossKit.partySize(this) + (phase() >= 2 ? 1 : 0));
        for (int i = 0; i < want; i++) {
            Vec3 spot = findGroundSpot(level, position(), 3.0, 8.0, type, null);
            if (spot == null) continue;
            Entity e = type.spawn(level, BlockPos.containing(spot), MobSpawnType.MOB_SUMMONED);
            if (e == null) continue;
            e.addTag(MINION_TAG);
            if (e instanceof Mob m) {
                m.setPersistenceRequired();
                LivingEntity t = getTarget();
                if (t != null) m.setTarget(t);
            }
            BossKit.debris(level, spot, 1.0, 10);
        }
        hintOnce(level, "larvae");
    }

    /** Chuva de Estalactites (Fúria): círculos pequenos em volta de cada jogador, caindo em sequência. */
    private void stalactiteRain(ServerLevel level) {
        dripCd = 150 + random.nextInt(40);
        attackTimer = 18;
        action("summon");
        announce("drip");
        BossKit.shake(level, position(), 32, 1.2F, 20);
        float dmg = scaled(11F);
        BlockParticleOption drip = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.POINTED_DRIPSTONE.defaultBlockState());
        int i = 0;
        for (ServerPlayer p : BossKit.partyAround(this, 32)) {
            for (int k = 0; k < 3; k++) {
                Vec3 at = groundAt(level, p.position().add((random.nextDouble() - 0.5) * 8, 0, (random.nextDouble() - 0.5) * 8));
                Telegraph.Shape s = Telegraph.Shape.circle(at, 1.8F).warmup(24 + (i++) * 6).color(Telegraph.WARNING).owner(this).noRunes();
                if (i == 1) s.castName(attackName("drip")).element(Element.EARTH);
                Telegraph.delayed(level, s, hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().fallingStalactite(this), dmg)) {
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                        }
                    }
                    for (int y = 6; y >= 0; y--) level.sendParticles(drip, at.x, at.y + y, at.z, 2, 0.1, 0.1, 0.1, 0.0);
                    level.sendParticles(drip, at.x, at.y + 0.2, at.z, 16, 0.6, 0.1, 0.6, 0.1);
                    level.playSound(null, BlockPos.containing(at), SoundEvents.POINTED_DRIPSTONE_LAND, getSoundSource(), 1.6F, 0.8F);
                });
            }
        }
    }

    private void dirt(ServerLevel level, Vec3 at, int count) {
        BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
        BlockState state = level.getBlockState(under);
        if (state.isAir()) state = Blocks.DIRT.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + 0.2, at.z, count, 1.0, 0.1, 1.0, 0.15);
    }

    // ------------------------------------------------------------------ Loot

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropItem(DepthsRegistries.WORM_KING_FANG.get(), 1, 2);
        dropItem(DepthsRegistries.PURE_MANA_SHARD.get(), 3, 5);
        dropItem(CoreRegistries.MANA_CRYSTAL.get(), 6, 12);
        dropEssence(Element.EARTH, 2, 4);
        dropEssence(Element.ARCANE, 1, 2);
        dropItem(Items.DIAMOND, 2, 4);
        dropItem(Items.EMERALD, 4, 8);
        if (random.nextFloat() < 0.35F) dropItem(DepthsRegistries.DRAGONSLAYER.get(), 1, 1);
        if (random.nextFloat() < 0.35F) dropItem(DepthsRegistries.GODDESS_KNIFE.get(), 1, 1);
        if (random.nextFloat() < 0.10F) dropById("relic_dwarven_rune", 1, 1);
        dropArcaneScrolls(2);
    }

    // ------------------------------------------------------------------ Salvar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Burrowed", isBurrowed());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Burrowed")) setBurrowed(tag.getBoolean("Burrowed"));
    }
}
