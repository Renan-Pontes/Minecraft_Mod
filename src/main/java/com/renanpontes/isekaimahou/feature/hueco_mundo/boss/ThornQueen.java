package com.renanpontes.isekaimahou.feature.hueco_mundo.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

import net.minecraft.core.particles.ParticleTypes;
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
 * Rainha dos Espinhos — Morgwen, soberana da Corte das Fadas. Asas de vidro rachado, coroa de roseira, vestido de pétalas.
 * <ul>
 *   <li>Fase 1: <b>Lanças de Espinho</b> (linhas que enraízam), <b>Tempestade de Pétalas</b> (anel ao redor dela),
 *       <b>Encanto da Rainha</b> (marcas que seguem os jogadores: tonteira, lentidão e puxão para ela) e
 *       <b>Corte Travessa</b> (fadas e cães de espinhos).</li>
 *   <li>Fase 2 (50%): <b>Chuva de Sementes</b>: sementes caem em volta de cada jogador e explodem em espinhos.</li>
 *   <li>v1.3 — <b>Valsa das Pétalas</b> (fase 2+): abre as asas e gira; três braços de pétalas cortantes varrem a
 *       arena em volta dela por 3 s (pule por cima do braço ou fique longe), e tudo termina numa explosão de pétalas.</li>
 *   <li>Fase 3 (20%): <b>Veredito da Lua</b>: só os círculos verdes salvam; quem fica fora vira estátua por instantes.</li>
 * </ul>
 */
public class ThornQueen extends HmBoss {
    private static final int PETAL = 0xF29AD8, THORN = 0x5E8A3A, MOON = 0xCFE8FF;
    private int busy, lanceCd = 30, petalCd = 60, charmCd = 120, courtCd = 160, seedCd = 40, verdictCd = 60, waltzCd = 60;

    public ThornQueen(EntityType<? extends ThornQueen> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(480, 10, 0.28, 12);
    }

    @Override
    public String bossId() {
        return "thorn_queen";
    }

    @Override
    public int bossColor() {
        return PETAL;
    }

    @Override
    public HmRealm side() {
        return HmRealm.FEY;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.PINK;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.5F, 0.2F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("lance", "petals", "charm", "summon", "seeds", "verdict", "waltz");
    }

    @Override
    public String textureSuffix() {
        return phase() >= 2 ? "_p2" : "";
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
        HmFx.sphere(sl, position().add(0, 1.6, 0), 2.6, 60, HmFx.dust(phase >= 2 ? MOON : PETAL, 1.8F));
        sl.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 2, getZ(), 60, 2.0, 1.5, 2.0, 0.05);
        playSound(SoundEvents.ALLAY_ITEM_TAKEN, 2.0F, 0.5F);
        if (phase == 1) seedCd = 20;
        if (phase == 2) verdictCd = 20;
    }

    @Override
    protected void onReset(ServerLevel sl) {
        busy = 0;
    }

    @Override
    protected void clientTick() {
        if (isAwake() && tickCount % 3 == 0) {
            level().addParticle(ParticleTypes.CHERRY_LEAVES, getX() + (random.nextDouble() - 0.5) * 2, getY() + 2.4,
                    getZ() + (random.nextDouble() - 0.5) * 2, 0, -0.02, 0);
        }
    }

    @Override
    protected void tickCombat(ServerLevel sl, @Nullable LivingEntity target) {
        if (busy > 0) {
            busy--;
            getNavigation().stop();
            return;
        }
        if (lanceCd > 0) lanceCd--;
        if (petalCd > 0) petalCd--;
        if (charmCd > 0) charmCd--;
        if (courtCd > 0) courtCd--;
        if (seedCd > 0) seedCd--;
        if (verdictCd > 0) verdictCd--;
        if (waltzCd > 0) waltzCd--;
        if (target == null) return;
        double d = distanceTo(target);
        if (phase() >= 2 && verdictCd == 0) {
            verdict(sl);
        } else if (phase() >= 1 && waltzCd == 0 && d < 13) {
            waltz(sl);
        } else if (phase() >= 1 && seedCd == 0) {
            seeds(sl);
        } else if (courtCd == 0) {
            court(sl);
        } else if (d < 7 && petalCd == 0) {
            petals(sl);
        } else if (charmCd == 0) {
            charm(sl);
        } else if (lanceCd == 0 && hasLineOfSight(target)) {
            lances(sl);
        } else if (d > 6) {
            chase(target, 1.0);
        } else {
            getLookControl().setLookAt(target, 30F, 30F);
        }
    }

    private void lances(ServerLevel sl) {
        lanceCd = 70;
        busy = 22;
        action("lance");
        float dmg = scaled(12F);
        List<ServerPlayer> targets = new ArrayList<>(BossKit.partyAround(this, 32));
        for (int i = 0; i < Math.min(3, targets.size()); i++) {
            ServerPlayer p = targets.get(i);
            Vec3 dir = p.position().subtract(position()).multiply(1, 0, 1).normalize();
            Vec3 from = position();
            Vec3 to = lineEnd(sl, from, dir, 20);
            Telegraph.delayedLine(sl, from, to, 1.8F, 18, THORN, hits -> {
                hurtAll(hits, dmg, true);
                for (LivingEntity e : hits) if (isFoe(e)) e.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, 30, 0), this);
                HmFx.line(sl, from.add(0, 0.5, 0), to.add(0, 0.5, 0), HmFx.dust(THORN, 1.6F), 1.5);
                playSound(SoundEvents.SWEET_BERRY_BUSH_PLACE, 1.6F, 0.6F);
            });
        }
    }

    private void petals(ServerLevel sl) {
        petalCd = 90;
        busy = 22;
        action("petals");
        float dmg = scaled(11F);
        Telegraph.delayed(sl, Telegraph.Shape.ring(position(), 2F, 8.5F).warmup(20).color(PETAL).owner(this), hits -> {
            hurtAll(hits, dmg, true);
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.1);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.45, push.z));
                e.hurtMarked = true;
            }
            HmFx.ring(sl, position().add(0, 1.0, 0), 5.0, 48, ParticleTypes.CHERRY_LEAVES, 0.15);
            playSound(SoundEvents.AZALEA_LEAVES_BREAK, 2.0F, 0.6F);
        });
    }

    private void charm(ServerLevel sl) {
        charmCd = 200;
        busy = 18;
        action("charm");
        List<ServerPlayer> targets = new ArrayList<>(BossKit.partyAround(this, 36));
        for (int i = 0; i < Math.min(4, targets.size()); i++) {
            Telegraph.delayedFollow(sl, targets.get(i), 2.8F, 36, PETAL, hits -> {
                if (!isAlive()) return;
                for (LivingEntity e : hits) {
                    if (!isFoe(e)) continue;
                    e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0), this);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                    Vec3 pull = position().subtract(e.position()).multiply(1, 0, 1).normalize().scale(1.3);
                    e.setDeltaMovement(e.getDeltaMovement().add(pull.x, 0.3, pull.z));
                    e.hurtMarked = true;
                    sl.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + 2, e.getZ(), 4, 0.3, 0.2, 0.3, 0);
                }
                playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 1.4F);
            });
        }
        hintOnce("charm");
    }

    private void court(ServerLevel sl) {
        courtCd = 600;
        busy = 20;
        action("summon");
        summonMinions(sl, HmRegistries.FEY_PIXIE.get(), 3, 6);
        summonMinions(sl, HmRegistries.BRAMBLE_HOUND.get(), 1, 7);
        playSound(SoundEvents.ALLAY_AMBIENT_WITH_ITEM, 2.0F, 0.8F);
        hintOnce("court");
    }

    private void seeds(ServerLevel sl) {
        seedCd = 160;
        busy = 34;
        action("seeds");
        float dmg = scaled(10F);
        for (ServerPlayer p : BossKit.partyAround(this, 36)) {
            for (int i = 0; i < 3; i++) {
                Vec3 at = p.position().add((random.nextDouble() - 0.5) * 7, 0, (random.nextDouble() - 0.5) * 7);
                Telegraph.delayedBlast(sl, at, 2.5F, 30 + i * 4, THORN, hits -> {
                    hurtAll(hits, dmg, true);
                    for (LivingEntity e : hits) if (isFoe(e)) e.addEffect(new MobEffectInstance(CoreRegistries.BLEEDING, 80, 0), this);
                    HmFx.column(sl, at, 2.5, HmFx.dust(THORN, 1.4F), 12);
                });
            }
        }
        playSound(SoundEvents.CHERRY_LEAVES_BREAK, 2.0F, 0.5F);
        hintOnce("seeds");
    }

    private void verdict(ServerLevel sl) {
        verdictCd = 400;
        busy = 80;
        action("verdict");
        announce("verdict");
        List<Vec3> safes = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Vec3 s = arenaSpot(sl, 5, Math.max(7, arenaRadius() - 3));
            if (s == null) continue;
            safes.add(s);
            Telegraph.safe(sl, s, 3.0F, 70);
        }
        float dmg = scaled(24F);
        BossKit.schedule(sl, 70, () -> {
            if (!isAlive()) return;
            for (ServerPlayer p : BossKit.partyAround(this, arenaRadius() + 8)) {
                if (!isFoe(p)) continue;
                boolean safe = false;
                for (Vec3 s : safes) if (Telegraph.Shape.safe(s, 3.0F).contains(p)) safe = true;
                if (safe) continue;
                p.hurt(damageSources().indirectMagic(this, this), dmg);
                p.addEffect(new MobEffectInstance(CoreRegistries.PETRIFIED, 40, 0), this);
            }
            Vec3 c = arenaCenter() != null ? arenaCenter() : position();
            HmFx.ring(sl, c.add(0, 0.6, 0), arenaRadius() * 0.8, 64, HmFx.dust(MOON, 2.0F), 0);
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 3, c.z, 60, arenaRadius() * 0.5, 2, arenaRadius() * 0.5, 0.05);
            playSound(SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.5F);
            BossKit.shake(sl, c, arenaRadius() + 8, 1.4F, 24);
        });
        hintOnce("verdict");
    }

    /**
     * Valsa das Pétalas: antecipação (1 s: pétalas se fecham em volta dela, sinos, as três linhas iniciais marcadas no
     * chão), 3 s de giro com três braços de pétalas de 14 blocos (cada braço fere uma vez a cada meio segundo, sangra e
     * empurra de lado) e o final: explosão de pétalas e luar; as pétalas ficam caindo por um tempo.
     */
    private void waltz(ServerLevel sl) {
        waltzCd = 320;
        busy = 90;
        action("waltz");
        final Vec3 c = position();
        final double len = 14.0;
        final float start = getYRot();
        for (int i = 0; i < 3; i++) {
            Vec3 dir = Vec3.directionFromRotation(0, start + i * 120F);
            Telegraph.show(sl, Telegraph.Shape.line(c, c.add(dir.scale(len)), 1.6F).warmup(20).color(PETAL).owner(this).noRunes());
        }
        for (int t = 0; t < 20; t += 4) {
            final int tick = t;
            BossKit.schedule(sl, t, () -> {
                if (!isAlive()) return;
                HmFx.ring(sl, c.add(0, 1.2, 0), 4.0 - tick * 0.15, 20, ParticleTypes.CHERRY_LEAVES, 0.0);
                if (tick % 8 == 0) playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.6F + tick * 0.04F);
            });
        }
        float dmg = scaled(8F);
        Map<UUID, Long> lastHit = new HashMap<>();
        for (int t = 0; t <= 60; t += 3) {
            final int tick = t;
            BossKit.schedule(sl, 20 + t, () -> {
                if (!isAlive()) return;
                double base = Math.toRadians(start + tick * 6.0);
                for (int arm = 0; arm < 3; arm++) {
                    double a = base + arm * Math.PI * 2 / 3;
                    double dx = -Math.sin(a), dz = Math.cos(a);
                    for (double r = 2; r <= len; r += 2.4) {
                        if (r > len - 3) {
                            sl.sendParticles(HmFx.dust(PETAL, 1.6F), c.x + dx * r, c.y + 1.0, c.z + dz * r, 2, 0.15, 0.15, 0.15, 0.02);
                        } else {
                            sl.sendParticles(ParticleTypes.CHERRY_LEAVES, c.x + dx * r, c.y + 1.0, c.z + dz * r, 2, 0.15, 0.15, 0.15, 0.02);
                        }
                    }
                }
                for (ServerPlayer p : BossKit.partyAround(this, len + 2)) {
                    if (!isFoe(p)) continue;
                    Vec3 rel = p.position().subtract(c);
                    double dist = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
                    if (dist < 1.5 || dist > len || p.getY() > c.y + 2.2) continue;
                    double pa = Math.atan2(-rel.x, rel.z);
                    boolean onArm = false;
                    for (int arm = 0; arm < 3; arm++) {
                        double a = base + arm * Math.PI * 2 / 3;
                        double diff = Math.abs(Math.atan2(Math.sin(pa - a), Math.cos(pa - a)));
                        if (diff * dist < 1.3) onArm = true;
                    }
                    Long last = lastHit.get(p.getUUID());
                    if (!onArm || (last != null && sl.getGameTime() - last < 10)) continue;
                    lastHit.put(p.getUUID(), sl.getGameTime());
                    p.hurt(damageSources().indirectMagic(this, this), dmg);
                    p.addEffect(new MobEffectInstance(CoreRegistries.BLEEDING, 60, 0), this);
                    Vec3 tangent = new Vec3(-rel.z, 0, rel.x).normalize().scale(0.8);
                    p.setDeltaMovement(p.getDeltaMovement().add(tangent.x, 0.25, tangent.z));
                    p.hurtMarked = true;
                }
                if (tick % 12 == 0) playSound(SoundEvents.CHERRY_LEAVES_BREAK, 1.8F, 0.8F + tick * 0.01F);
            });
        }
        BossKit.schedule(sl, 82, () -> {
            if (!isAlive()) return;
            sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.5, c.z, 1, 0, 0, 0, 0);
            HmFx.sphere(sl, c.add(0, 1.5, 0), 3.0, 70, HmFx.dust(PETAL, 1.8F));
            HmFx.ring(sl, c.add(0, 0.4, 0), 6.0, 56, ParticleTypes.CHERRY_LEAVES, 0.3);
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 2, c.z, 30, 1.5, 1.0, 1.5, 0.15);
            playSound(SoundEvents.ALLAY_ITEM_TAKEN, 2.0F, 0.4F);
            BossKit.shake(sl, c, 24, 1.2F, 14);
        });
        for (int t = 90; t <= 150; t += 12) {
            BossKit.schedule(sl, t, () -> sl.sendParticles(ParticleTypes.CHERRY_LEAVES, c.x, c.y + 6, c.z, 24, len * 0.5, 1.5, len * 0.5, 0));
        }
        announce("waltz");
        hintOnce("waltz");
    }

    @Override
    protected void dropBossLoot(ServerLevel level) {
        spawnAtLocation(new ItemStack(HmRegistries.QUEEN_WINGS.get()));
        spawnAtLocation(new ItemStack(HmRegistries.FEY_DUST.get(), 4 + random.nextInt(5)));
        spawnAtLocation(new ItemStack(HmRegistries.BRAMBLE_HEART.get(), 1 + random.nextInt(2)));
        dropEssence(Element.LIGHT, 2, 4);
        dropEssence(Element.WIND, 1, 3);
        dropScroll("fey_charm");
        if (random.nextFloat() < 0.6F) dropScroll("fey_thornbind");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }
}
