package com.renanpontes.isekaimahou.feature.hueco_mundo.boss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

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
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Imperatriz das Areias — senhora do Jardim das Areias no palácio (homenagem à rainha-tubarão de Hueco Mundo, com nome e
 * golpes próprios). Máscara de mandíbula sobre a boca, espada larga de dente de tubarão.
 * <ul>
 *   <li>Fase 1: <b>Talho</b>, <b>Onda Areia-Azul</b> (linha que arremessa e cega), <b>Cascata das Dunas</b> (pilares de
 *       areia sob cada jogador), <b>Redemoinho</b> (puxa para o centro e mói quem chega lá).</li>
 *   <li>Fase 2 (55%): <b>Ressurreição — Tubarão das Dunas</b>: ganha a barbatana; MERGULHA na areia e persegue um jogador
 *       (círculo que segue e trava) até emergir mordendo.</li>
 *   <li>v1.3 — <b>Maremoto das Dunas</b> (fase 2+): gira a espada, a areia sobe em espiral e uma MURALHA de areia e
 *       água varre a arena na direção do alvo; só a brecha marcada em verde salva.</li>
 *   <li>Fase 3 (25%): <b>Tempestade Real</b>: tempestade de areia (cegueira curta) e Marés em cruz que giram.</li>
 * </ul>
 */
public class SandEmpress extends HmBoss {
    private static final EntityDataAccessor<Boolean> DATA_DIVING = SynchedEntityData.defineId(SandEmpress.class, EntityDataSerializers.BOOLEAN);
    private static final int SAND = 0xE8D7A8, TIDE = 0x3FA7C9;
    private int busy, slashCd = 20, waveCd = 60, cascadeCd = 120, whirlCd = 200, diveCd = 60, stormCd = 60, crossCd = 100, tsunamiCd = 80;

    public SandEmpress(EntityType<? extends SandEmpress> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(460, 10, 0.32, 12);
    }

    @Override
    public String bossId() {
        return "sand_empress";
    }

    @Override
    public int bossColor() {
        return TIDE;
    }

    @Override
    public HmRealm side() {
        return HmRealm.HOLLOW;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.BLUE;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.55F, 0.25F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("slash", "wave", "cascade", "whirl", "dive", "emerge", "storm", "resurrect", "tsunami");
    }

    @Override
    public String textureSuffix() {
        return phase() >= 1 ? "_p2" : "";
    }

    @Override
    public List<String> toggledBones() {
        return List.of("fin", "tail");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return phase() >= 1;
    }

    public boolean isDiving() {
        return entityData.get(DATA_DIVING);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DIVING, false);
    }

    @Override
    protected boolean isBusy() {
        return busy > 0;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || busy > 0;
    }

    @Override
    protected void onPhaseChange(ServerLevel sl, int phase) {
        super.onPhaseChange(sl, phase);
        if (phase == 1) {
            action("resurrect");
            busy = 40;
            sandBurst(sl, position(), 60);
            HmFx.sphere(sl, position().add(0, 1.2, 0), 2.6, 60, HmFx.dust(TIDE, 1.8F));
            playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 1.6F, 0.7F);
            hintOnce("dive");
        } else if (phase == 2) {
            stormCd = 10;
            hintOnce("storm");
        }
    }

    @Override
    protected void onReset(ServerLevel sl) {
        busy = 0;
        surface();
    }

    private void surface() {
        entityData.set(DATA_DIVING, false);
        setInvisible(false);
    }

    @Override
    protected void tickCombat(ServerLevel sl, @Nullable LivingEntity target) {
        if (busy > 0) {
            busy--;
            getNavigation().stop();
            return;
        }
        if (slashCd > 0) slashCd--;
        if (waveCd > 0) waveCd--;
        if (cascadeCd > 0) cascadeCd--;
        if (whirlCd > 0) whirlCd--;
        if (diveCd > 0) diveCd--;
        if (stormCd > 0) stormCd--;
        if (crossCd > 0) crossCd--;
        if (tsunamiCd > 0) tsunamiCd--;
        if (target == null) return;
        double d = distanceTo(target);
        if (phase() >= 2 && stormCd == 0) {
            storm(sl);
        } else if (phase() >= 2 && crossCd == 0) {
            tideCross(sl);
        } else if (phase() >= 1 && tsunamiCd == 0 && hasLineOfSight(target)) {
            tsunami(sl, target);
        } else if (phase() >= 1 && diveCd == 0) {
            dive(sl, target);
        } else if (whirlCd == 0) {
            whirlpool(sl, target);
        } else if (cascadeCd == 0) {
            cascade(sl);
        } else if (d < 4.5 && slashCd == 0) {
            slash(sl, target);
        } else if (d > 5 && d < 22 && waveCd == 0 && hasLineOfSight(target)) {
            sandWave(sl, target);
        } else {
            chase(target, 1.1);
        }
    }

    private void slash(ServerLevel sl, LivingEntity t) {
        slashCd = 24;
        busy = 14;
        float yaw = yawTo(t.position());
        holdYaw(yaw);
        action("slash");
        float dmg = scaled(12F);
        Telegraph.delayed(sl, Telegraph.Shape.cone(position(), yaw, 100F, 4.6F).warmup(10).color(TIDE).owner(this).noRunes(), hits -> {
            hurtAll(hits, dmg, false);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.8F);
        });
    }

    private void sandWave(ServerLevel sl, LivingEntity t) {
        waveCd = 90;
        busy = 22;
        Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1).normalize();
        holdYaw(yawTo(t.position()));
        Vec3 from = position();
        Vec3 to = lineEnd(sl, from, dir, 20);
        action("wave");
        float dmg = scaled(14F);
        Telegraph.delayedLine(sl, from, to, 3.0F, 18, TIDE, hits -> {
            hurtAll(hits, dmg, true);
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 1.2, 0.5, dir.z * 1.2));
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this);
            }
            for (int i = 1; i <= 10; i++) sandBurst(sl, from.add(dir.scale(i * 2)), 6);
            HmFx.line(sl, from.add(0, 0.8, 0), to.add(0, 0.8, 0), HmFx.dust(TIDE, 1.8F), 1.5);
            playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.6F, 0.6F);
        });
    }

    private void cascade(ServerLevel sl) {
        cascadeCd = 220;
        busy = 20;
        action("cascade");
        float dmg = scaled(12F);
        List<ServerPlayer> targets = new ArrayList<>(BossKit.partyAround(this, 40));
        for (int i = 0; i < Math.min(4, targets.size()); i++) {
            int[] id = new int[1];
            id[0] = Telegraph.delayedFollow(sl, targets.get(i), 3.2F, 44, SAND, hits -> {
                Vec3 at = Telegraph.centerOf(id[0]);
                if (at == null) return;
                hurtAll(hits, dmg, true);
                for (LivingEntity e : hits) {
                    if (!isFoe(e)) continue;
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 1.0, 0));
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
                }
                for (int k = 0; k < 5; k++) sandBurst(sl, at.add(0, k * 1.2, 0), 10);
                sl.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.5F);
            });
        }
        hintOnce("cascade");
    }

    private void whirlpool(ServerLevel sl, LivingEntity t) {
        whirlCd = 360;
        busy = 16;
        action("whirl");
        Vec3 spot = t.position();
        Telegraph.show(sl, Telegraph.Shape.circle(spot, 6.5F).warmup(20).hold(100).color(SAND).owner(this));
        float dmg = scaled(6F);
        for (int k = 20; k <= 120; k += 5) {
            final int tick = k;
            BossKit.schedule(sl, k, () -> {
                if (!isAlive()) return;
                HmFx.ring(sl, spot.add(0, 0.3, 0), 1.0 + (tick % 30) / 5.0, 16, ParticleTypes.CLOUD, 0.02);
                for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(spot, 6.5F))) {
                    if (!isFoe(e)) continue;
                    Vec3 pull = spot.subtract(e.position()).multiply(1, 0, 1);
                    if (pull.lengthSqr() > 0.01) {
                        e.setDeltaMovement(e.getDeltaMovement().add(pull.normalize().scale(0.12)));
                        e.hurtMarked = true;
                    }
                    if (tick % 10 == 0 && e.position().distanceToSqr(spot) < 2.0 * 2.0) e.hurt(damageSources().indirectMagic(this, this), dmg);
                }
            });
        }
        hintOnce("whirl");
    }

    private void dive(ServerLevel sl, LivingEntity t) {
        diveCd = 180;
        busy = 70;
        action("dive");
        entityData.set(DATA_DIVING, true);
        setInvisible(true);
        sandBurst(sl, position(), 40);
        playSound(SoundEvents.SAND_BREAK, 2.0F, 0.4F);
        float dmg = scaled(20F);
        int[] id = new int[1];
        id[0] = Telegraph.delayedFollow(sl, t, 3.4F, 52, TIDE, hits -> {
            Vec3 at = Telegraph.centerOf(id[0]);
            if (at == null) at = t.position();
            teleportTo(at.x, at.y, at.z);
            surface();
            action("emerge");
            hurtAll(hits, dmg, false);
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                e.setDeltaMovement(e.getDeltaMovement().add(0, 1.2, 0));
                e.hurtMarked = true;
            }
            sandBurst(sl, at, 70);
            HmFx.ring(sl, at.add(0, 0.2, 0), 3.0, 36, ParticleTypes.SPLASH, 0.1);
            playSound(SoundEvents.ELDER_GUARDIAN_HURT, 2.0F, 0.6F);
            BossKit.shake(sl, at, 20, 1.2F, 14);
            busy = 18;
        });
        // A barbatana corta a areia seguindo o círculo.
        for (int k = 4; k < 52; k += 4) {
            BossKit.schedule(sl, k, () -> {
                Vec3 at = Telegraph.centerOf(id[0]);
                if (at == null || !isAlive()) return;
                sandBurst(sl, at, 6);
                sl.sendParticles(HmFx.dust(TIDE, 1.4F), at.x, at.y + 0.6, at.z, 4, 0.2, 0.3, 0.2, 0);
            });
        }
        // Rede de segurança: se o callback não rodar (alvo saiu), ela volta à tona.
        BossKit.schedule(sl, 70, () -> {
            if (isAlive() && isDiving()) surface();
        });
    }

    private void storm(ServerLevel sl) {
        stormCd = 260;
        busy = 30;
        action("storm");
        announce("storm");
        Vec3 c = arenaCenter() != null ? arenaCenter() : position();
        for (int k = 0; k < 100; k += 10) {
            BossKit.schedule(sl, k, () -> {
                if (!isAlive()) return;
                for (int i = 0; i < 6; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * arenaRadius();
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, HmRegistries.HOLLOW_SAND.get().defaultBlockState()),
                            c.x + Math.cos(a) * r, c.y + 2 + random.nextDouble() * 3, c.z + Math.sin(a) * r, 8, 1.5, 1.0, 1.5, 0.05);
                }
            });
        }
        for (ServerPlayer p : BossKit.partyAround(this, arenaRadius() + 6)) {
            if (isFoe(p)) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0), this);
        }
        playSound(SoundEvents.ELYTRA_FLYING, 2.0F, 0.5F);
    }

    private void tideCross(ServerLevel sl) {
        crossCd = 160;
        busy = 54;
        action("wave");
        float dmg = scaled(14F);
        float base = getYRot();
        for (int wave = 0; wave < 2; wave++) {
            final float off = base + wave * 45F;
            final int delay = wave * 26;
            BossKit.schedule(sl, delay, () -> {
                if (!isAlive()) return;
                for (int i = 0; i < 4; i++) {
                    Vec3 dir = Vec3.directionFromRotation(0, off + i * 90F);
                    Vec3 from = position();
                    Vec3 to = lineEnd(sl, from, dir, 18);
                    Telegraph.delayedLine(sl, from, to, 2.4F, 24, TIDE, hits -> {
                        hurtAll(hits, dmg, true);
                        HmFx.line(sl, from.add(0, 0.6, 0), to.add(0, 0.6, 0), HmFx.dust(TIDE, 1.6F), 1.2);
                    });
                }
            });
        }
        hintOnce("cross");
    }

    /**
     * Maremoto das Dunas: antecipação (1,2 s: areia e água sobem em espiral em volta dela, o chão marca a faixa inteira
     * e três círculos verdes mostram a brecha), a muralha corre 20 blocos (1 bloco/tick) e arremessa quem não estiver na
     * brecha; no fim a onda quebra em espuma e a areia molhada cai devagar.
     */
    private void tsunami(ServerLevel sl, LivingEntity t) {
        tsunamiCd = 300;
        busy = 50;
        action("tsunami");
        Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 0.01) dir = Vec3.directionFromRotation(0, getYRot());
        final Vec3 fwd = dir.normalize(), side = new Vec3(-fwd.z, 0, fwd.x), from = position();
        holdYaw(yawTo(t.position()));
        final double gap = (random.nextDouble() - 0.5) * 10, half = 8.5, len = 20;
        Telegraph.show(sl, Telegraph.Shape.line(from, from.add(fwd.scale(len)), (float) (half * 2)).warmup(24).color(TIDE).owner(this).noRunes());
        for (int i = 1; i <= 3; i++) Telegraph.safe(sl, from.add(fwd.scale(i * 6)).add(side.scale(gap)), 2.2F, 46);
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, HmRegistries.HOLLOW_SAND.get().defaultBlockState());
        BlockParticleOption chunk = new BlockParticleOption(ParticleTypes.BLOCK, HmRegistries.HOLLOW_SAND.get().defaultBlockState());
        for (int k = 0; k < 24; k += 3) {
            final int tick = k;
            BossKit.schedule(sl, k, () -> {
                if (!isAlive()) return;
                double a = tick * 0.5, r = 2.4 - tick * 0.05;
                for (int i = 0; i < 6; i++) {
                    double ang = a + i * Math.PI / 3, y = 0.3 + i * 0.5;
                    if (i % 2 == 0) {
                        sl.sendParticles(dust, getX() + Math.cos(ang) * r, getY() + y, getZ() + Math.sin(ang) * r, 2, 0.05, 0.05, 0.05, 0);
                    } else {
                        sl.sendParticles(HmFx.dust(TIDE, 1.4F), getX() + Math.cos(ang) * r, getY() + y, getZ() + Math.sin(ang) * r, 2, 0.05, 0.05, 0.05, 0);
                    }
                }
                if (tick % 9 == 0) playSound(SoundEvents.ELDER_GUARDIAN_AMBIENT, 1.4F, 0.6F + tick * 0.02F);
            });
        }
        float dmg = scaled(16F);
        Set<UUID> hit = new HashSet<>();
        for (int k = 0; k <= 20; k += 2) {
            final double front = 1.5 + k;
            final boolean first = k == 0, last = k == 20;
            BossKit.schedule(sl, 24 + k, () -> {
                if (!isAlive()) return;
                Vec3 base = from.add(fwd.scale(front));
                for (double s = -half; s <= half; s += 2.5) {
                    if (Math.abs(s - gap) < 2.2) continue;
                    Vec3 p = base.add(side.scale(s));
                    sl.sendParticles(chunk, p.x, p.y + 1.2, p.z, 6, 0.6, 1.0, 0.6, 0.1);
                    sl.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 2.6, p.z, 4, 0.6, 0.3, 0.6, 0.2);
                }
                for (ServerPlayer pl : BossKit.partyAround(this, 34)) {
                    if (!isFoe(pl) || hit.contains(pl.getUUID())) continue;
                    Vec3 rel = pl.position().subtract(from);
                    double along = rel.dot(fwd), across = rel.dot(side);
                    if (along < front - 2.0 || along > front + 0.8 || Math.abs(across) > half || Math.abs(across - gap) < 2.2) continue;
                    hit.add(pl.getUUID());
                    pl.hurt(damageSources().indirectMagic(this, this), dmg);
                    pl.setDeltaMovement(pl.getDeltaMovement().add(fwd.x * 1.4, 0.55, fwd.z * 1.4));
                    pl.hurtMarked = true;
                    pl.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
                    pl.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                }
                if (first) {
                    playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.0F, 0.5F);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.7F);
                    sl.sendParticles(ParticleTypes.FLASH, from.x, from.y + 1, from.z, 1, 0, 0, 0, 0);
                    BossKit.shake(sl, from, 30, 1.6F, 26);
                }
                if (last) {
                    // A onda quebra: espuma alta e areia molhada caindo.
                    Vec3 end = from.add(fwd.scale(front));
                    sl.sendParticles(ParticleTypes.SPLASH, end.x, end.y + 3, end.z, 60, half * 0.6, 1.5, half * 0.6, 0.4);
                    sl.sendParticles(ParticleTypes.CLOUD, end.x, end.y + 1, end.z, 20, half * 0.5, 0.5, half * 0.5, 0.05);
                    playSound(SoundEvents.GENERIC_SPLASH, 2.0F, 0.6F);
                    for (int w = 5; w <= 40; w += 7) {
                        BossKit.schedule(sl, w, () -> sl.sendParticles(dust, end.x, end.y + 5, end.z, 14, half * 0.6, 1.0, half * 0.6, 0));
                    }
                }
            });
        }
        announce("tsunami");
        hintOnce("tsunami");
    }

    private void sandBurst(ServerLevel sl, Vec3 at, int n) {
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, HmRegistries.HOLLOW_SAND.get().defaultBlockState()),
                at.x, at.y + 0.3, at.z, Math.min(n, 80), 0.8, 0.4, 0.8, 0.2);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Mergulhada não leva dano (só a barbatana corta a areia).
        if (isDiving() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return !isDiving() && super.isPushable();
    }

    @Override
    protected void dropBossLoot(ServerLevel level) {
        spawnAtLocation(new ItemStack(HmRegistries.TIDE_SAND_BLADE.get()));
        spawnAtLocation(new ItemStack(HmRegistries.ARRANCAR_SOUL_SHARD.get(), 2 + random.nextInt(3)));
        spawnAtLocation(new ItemStack(HmRegistries.REISHI_CRYSTAL.get(), 2 + random.nextInt(2)));
        spawnAtLocation(new ItemStack(HmRegistries.MENOS_CORE.get(), 1));
        dropEssence(Element.EARTH, 2, 4);
        dropEssence(Element.WATER, 1, 3);
        dropScroll("sand_tide");
        if (random.nextFloat() < 0.5F) dropScroll("reishi_rend");
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ELDER_GUARDIAN_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Diving", isDiving());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Carregada no meio do mergulho: volta à tona.
        if (tag.getBoolean("Diving")) surface();
    }
}
