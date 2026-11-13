package com.renanpontes.isekaimahou.feature.end_library.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.BgFx;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.GBossEntity;
import com.renanpontes.isekaimahou.feature.end_library.ElRegistries;
import com.renanpontes.isekaimahou.feature.end_library.ElSpaceGlyphs;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Devorador de Estrelas — chefe do Observatório da Biblioteca do Vazio. Uma boca cósmica que flutua com um halo, uma
 * coroa de estrelas e tentáculos. Golpes sempre marcados no chão ({@link Telegraph}):
 * <ul>
 *   <li><b>Fase 1 — Gravidade:</b> Mordida Estelar (cone), Poço Gravitacional (puxa todos para o círculo e esmaga),
 *       Inversão de Gravidade (círculo segue cada jogador; quem fica dentro flutua e é jogado no chão);</li>
 *   <li><b>Fase 2 — Meteoros (66%):</b> Chuva de Meteoros (3 ondas de círculos em cada jogador e pelo salão), Rajada
 *       Estelar (linha longa), invoca Fagulhas Estelares;</li>
 *   <li><b>Fase 3 — Buraco Negro (33%):</b> abre um Buraco Negro no centro da arena que puxa tudo por 6 s, fere quem
 *       chega perto e COLAPSA num círculo grande — corra contra a gravidade para sair a tempo.</li>
 * </ul>
 * Base comum dos chefes (sono, apresentação, barra, fases, arena protegida, escala de grupo, anti-trapaça,
 * recompensas): {@link GBossEntity}. Paira 2,5 blocos acima do chão quando acordado.
 */
public class StarDevourer extends GBossEntity {
    public static final int COLOR = 0x9B4DFF;
    private static final int METEOR_COLOR = 0xFF8A3B;
    private static final double HOVER = 2.5;
    private static final int HOLE_TICKS = 120;

    private int biteCd = 30;
    private int wellCd = 90;
    private int flipCd = 160;
    private int meteorCd = 60;
    private int beamCd = 100;
    private int holeCd = 80;
    private int summonCd = 400;
    private int attackTimer;
    private float attackYaw;
    private int holeTicks;
    @Nullable
    private Vec3 holeCenter;

    public StarDevourer(EntityType<? extends StarDevourer> type, Level level) {
        super(type, level);
        this.xpReward = 400;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(700.0, 12.0, 0.25, 16.0)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 56.0);
    }

    // ------------------------------------------------------------------ Definições do chefe

    @Override
    public String bossId() {
        return "star_devourer";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.PURPLE;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.66F, 0.33F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("bite", "gravity", "meteor", "beam", "black_hole", "summon");
    }

    @Override
    protected double arenaRadius() {
        return 26.0;
    }

    @Override
    protected double wakeRange() {
        return 18.0;
    }

    @Override
    protected int rewardXp() {
        return 900;
    }

    @Override
    protected boolean isBusy() {
        return attackTimer > 0 || holeTicks > 0;
    }

    @Override
    public String musicTheme() {
        return phase() >= 2 ? "twilight_herald" : "primordial_dragon";
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (isDeadOrDying()) return hold("death");
        if (introTicks() > 0) return play("intro");
        if (!isAwake()) return loop("sleep");
        return loop(moving ? "walk" : "idle");
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        setNoGravity(true);
        if (biteCd > 0) biteCd--;
        if (wellCd > 0) wellCd--;
        if (flipCd > 0) flipCd--;
        if (meteorCd > 0) meteorCd--;
        if (beamCd > 0) beamCd--;
        if (holeCd > 0) holeCd--;
        if (summonCd > 0) summonCd--;
        if (holeTicks > 0) tickBlackHole(level);

        if (attackTimer > 0) {
            attackTimer--;
            setDeltaMovement(getDeltaMovement().scale(0.5));
            holdYaw(attackYaw);
            return;
        }
        hover(level, target);
        if (target == null) return;
        double d = horizontalDistance(target);
        boolean los = hasLineOfSight(target);
        int p = phase();
        if (p >= 2 && holeCd <= 0 && holeTicks <= 0) {
            startBlackHole(level);
        } else if (p >= 1 && summonCd <= 0 && countMinions(level, 40) < 2) {
            summonWisps(level, 2);
        } else if (d < 5.5 && biteCd <= 0) {
            startBite(level, target);
        } else if (p >= 1 && meteorCd <= 0) {
            startMeteors(level);
        } else if (p >= 1 && beamCd <= 0 && los && d < 24) {
            startBeam(level, target);
        } else if (wellCd <= 0 && d < 20) {
            startWell(level, target);
        } else if (flipCd <= 0) {
            startFlip(level);
        }
    }

    private double horizontalDistance(Entity e) {
        return Math.hypot(e.getX() - getX(), e.getZ() - getZ());
    }

    /** Voo: fica a ~7 blocos do alvo (perto quando a mordida está pronta), pairando acima do chão. */
    private void hover(ServerLevel level, @Nullable LivingEntity target) {
        Vec3 goal;
        if (target != null) {
            Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-3) away = new Vec3(1, 0, 0);
            double want = biteCd <= 0 ? 3.5 : 7.5;
            Vec3 flat = target.position().add(away.normalize().scale(want));
            double gy = groundAt(level, flat).y;
            goal = new Vec3(flat.x, Math.max(gy, target.getY()) + HOVER, flat.z);
            getLookControl().setLookAt(target, 30F, 30F);
        } else {
            Vec3 c = BossKit.arenaCenter(this);
            Vec3 home = c != null ? c : position();
            goal = new Vec3(home.x, groundAt(level, home).y + HOVER, home.z);
        }
        Vec3 d = goal.subtract(position());
        double speed = 0.2 + phase() * 0.04;
        Vec3 v = d.lengthSqr() > speed * speed ? d.normalize().scale(speed) : d;
        setDeltaMovement(getDeltaMovement().scale(0.6).add(v.scale(0.4)));
        if (target != null) faceTowards(target);
    }

    private Vec3 ground(ServerLevel level) {
        return groundAt(level, position());
    }

    // ---------------- Mordida Estelar
    private void startBite(ServerLevel level, LivingEntity target) {
        biteCd = phase() >= 2 ? 34 : 46;
        attackTimer = 14;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("bite");
        playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.8F, 0.5F);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        Telegraph.delayed(level, Telegraph.Shape.cone(ground(level), attackYaw, 100F, 6.5F).warmup(12).color(Telegraph.DANGER)
                .vertical(2.0, 5.0).owner(this).noRunes(), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                            e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 100, 0), this);
                        }
                    }
                    playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.6F);
                });
    }

    // ---------------- Poço Gravitacional
    private void startWell(ServerLevel level, LivingEntity target) {
        wellCd = (phase() >= 1 ? 130 : 160) + random.nextInt(40);
        attackTimer = 18;
        attackYaw = yawTo(target.position());
        action("gravity");
        playSound(SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
        Vec3 c = groundAt(level, target.position());
        float dmg = scaled(15F);
        Telegraph.delayed(level, Telegraph.Shape.circle(c, 5.0F).warmup(40).color(Telegraph.VOID).owner(this)
                .castName(attackName("gravity_well")).element(Element.ARCANE), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().indirectMagic(this, this), dmg)) {
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                        }
                    }
                    BgFx.sphere(level, c.add(0, 1, 0), 2.5, 50, BgFx.dust(COLOR, 1.6F));
                    level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.6F);
                });
        // Puxões durante o aquecimento: quem estiver perto escorrega para dentro do círculo.
        for (int i = 0; i < 8; i++) {
            BossKit.schedule(level, 4 + i * 4, () -> {
                if (!isAlive()) return;
                pull(level, c, 10.0, 0.16);
                BgFx.ring(level, c.add(0, 0.3, 0), 5.0, 24, ParticleTypes.REVERSE_PORTAL, 0.02);
            });
        }
    }

    /** Puxa jogadores (e invocações deles) para {@code c} na horizontal; mais forte longe do centro. */
    private void pull(ServerLevel level, Vec3 c, double radius, double strength) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(radius, 8, radius),
                this::isFoe)) {
            Vec3 to = c.subtract(e.position()).multiply(1, 0, 1);
            double dist = to.length();
            if (dist < 0.6 || dist > radius) continue;
            Vec3 v = to.normalize().scale(strength * Mth.clamp(dist / radius + 0.35, 0.35, 1.0));
            e.setDeltaMovement(e.getDeltaMovement().add(v));
            e.hurtMarked = true;
        }
    }

    // ---------------- Inversão de Gravidade
    private void startFlip(ServerLevel level) {
        flipCd = (phase() >= 1 ? 200 : 240) + random.nextInt(60);
        attackTimer = 16;
        action("gravity");
        announce("flip");
        hintOnce(level, "gravity_flip");
        playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 2.0F, 0.6F);
        int n = 0;
        for (ServerPlayer p : BossKit.partyAround(this, 30)) {
            if (!isFoe(p) || n++ >= 5) continue;
            Telegraph.delayed(level, Telegraph.Shape.follow(p, 2.2F).warmup(28).color(Telegraph.ARCANE).owner(this)
                    .castName(n == 1 ? attackName("gravity_flip") : null), hits -> {
                        if (!isAlive()) return;
                        for (LivingEntity e : hits) {
                            if (!isFoe(e)) continue;
                            e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 22, 5), this);
                            level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 0.5, e.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
                            float crush = scaled(7F);
                            BossKit.schedule(level, 24, () -> {
                                if (!isAlive() || !e.isAlive()) return;
                                e.removeEffect(MobEffects.LEVITATION);
                                e.setDeltaMovement(e.getDeltaMovement().x, -1.8, e.getDeltaMovement().z);
                                e.hurtMarked = true;
                                e.hurt(damageSources().indirectMagic(this, this), crush);
                            });
                        }
                    });
        }
    }

    // ---------------- Chuva de Meteoros
    private void startMeteors(ServerLevel level) {
        meteorCd = (phase() >= 2 ? 260 : 300) + random.nextInt(60);
        attackTimer = 30;
        action("meteor");
        announce("meteor");
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.5F, 0.7F);
        int waves = phase() >= 2 ? 4 : 3;
        for (int w = 0; w < waves; w++) {
            boolean named = w == 0;
            BossKit.schedule(level, w * 24, () -> {
                if (!isAlive()) return;
                for (ServerPlayer p : BossKit.partyAround(this, 32)) {
                    if (isFoe(p)) meteor(level, groundAt(level, p.position()), named);
                }
                Vec3 c = BossKit.arenaCenter(this);
                Vec3 base = c != null ? c : position();
                for (int i = 0; i < 2 + phase(); i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = 3 + random.nextDouble() * 10;
                    meteor(level, groundAt(level, base.add(Math.cos(a) * r, 2, Math.sin(a) * r)), false);
                }
            });
        }
    }

    private void meteor(ServerLevel level, Vec3 at, boolean named) {
        float dmg = scaled(16F);
        Telegraph.Shape s = Telegraph.Shape.circle(at, 3.0F).warmup(32).color(METEOR_COLOR).owner(this).noRunes();
        if (named) s.castName(attackName("meteor_rain")).element(Element.FIRE);
        // A estrela cadente aparece no céu pouco antes do impacto.
        BossKit.schedule(level, 22, () -> {
            if (isAlive()) BgFx.line(level, at.add(4, 22, 2), at.add(0.4, 4, 0.2), ParticleTypes.END_ROD, 1.5);
        });
        Telegraph.delayed(level, s, hits -> {
            if (!isAlive()) return;
            for (LivingEntity e : hits) {
                if (isFoe(e) && e.hurt(damageSources().indirectMagic(this, this), dmg)) e.igniteForSeconds(4);
            }
            BgFx.line(level, at.add(1.5, 8, 0.75), at.add(0, 0.5, 0), ParticleTypes.FLAME, 2.0);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.6, 0.2, 0.6, 0);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 6, 0.8, 0.1, 0.8, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 1.2F, 1.1F);
            BossKit.shake(level, at, 10, 0.5F, 6);
        });
    }

    // ---------------- Rajada Estelar
    private void startBeam(ServerLevel level, LivingEntity target) {
        beamCd = (phase() >= 2 ? 110 : 140) + random.nextInt(30);
        attackTimer = 28;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("beam");
        playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 0.6F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = ground(level).add(dir.scale(1.5));
        Vec3 to = lineEnd(level, from, dir, 26.0);
        float dmg = scaled(20F);
        Telegraph.delayed(level, Telegraph.Shape.line(from, to, 2.4F).warmup(22).color(Telegraph.ARCANE).owner(this)
                .vertical(2.0, 5.0).castName(attackName("star_beam")).element(Element.LIGHT), hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().indirectMagic(this, this), dmg)) {
                            e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 1.2, 0.35, dir.z * 1.2));
                            e.hurtMarked = true;
                        }
                    }
                    Vec3 mouth = position().add(0, 1.6, 0);
                    BgFx.line(level, mouth, to.add(0, 1.0, 0), BgFx.dust(0xE8D8FF, 2.0F), 2.0);
                    BgFx.line(level, mouth, to.add(0, 1.0, 0), ParticleTypes.END_ROD, 1.0);
                    playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 1.3F);
                });
    }

    // ---------------- Buraco Negro
    private void startBlackHole(ServerLevel level) {
        holeCd = 440 + random.nextInt(80);
        attackTimer = 30;
        action("black_hole");
        announce("black_hole");
        hintOnce(level, "black_hole");
        Vec3 c = BossKit.arenaCenter(this);
        holeCenter = groundAt(level, c != null ? c : position());
        holeTicks = HOLE_TICKS;
        playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.5F);
        BossKit.flashNear(level, holeCenter, 40, 0x110022, 0.5F, 20);
        Vec3 hc = holeCenter;
        float dmg = scaled(26F);
        // O colapso: círculo grande que estoura no fim do puxão (sair dele contra a gravidade é o desafio).
        BossKit.schedule(level, HOLE_TICKS - 45, () -> {
            if (!isAlive()) return;
            Telegraph.delayed(level, Telegraph.Shape.circle(hc, 9.0F).warmup(45).color(Telegraph.DANGER).owner(this)
                    .castName(attackName("black_hole")).uncounterable(), hits -> {
                        if (!isAlive()) return;
                        for (LivingEntity e : hits) {
                            if (!isFoe(e)) continue;
                            if (e.hurt(damageSources().indirectMagic(this, this), dmg)) {
                                Vec3 out = e.position().subtract(hc).multiply(1, 0, 1);
                                out = out.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : out.normalize();
                                e.setDeltaMovement(out.scale(1.6).add(0, 0.6, 0));
                                e.hurtMarked = true;
                            }
                        }
                        BgFx.sphere(level, hc.add(0, 1.5, 0), 6.0, 80, BgFx.dust(0x2A0A4A, 2.4F));
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, hc.x, hc.y + 1, hc.z, 1, 0, 0, 0, 0);
                        level.playSound(null, hc.x, hc.y, hc.z, SoundEvents.END_PORTAL_SPAWN, getSoundSource(), 2.0F, 1.2F);
                        BossKit.shake(level, hc, 30, 1.2F, 20);
                    });
        });
    }

    private void tickBlackHole(ServerLevel level) {
        holeTicks--;
        Vec3 c = holeCenter;
        if (c == null) {
            holeTicks = 0;
            return;
        }
        int t = HOLE_TICKS - holeTicks;
        double strength = 0.035 + 0.04 * Math.min(1.0, t / 60.0);
        pull(level, c, 30.0, strength);
        Vec3 core = c.add(0, 1.5, 0);
        if (t % 2 == 0) {
            // Espiral de matéria sendo sugada + o núcleo escuro.
            for (int i = 0; i < 6; i++) {
                double a = (t * 0.35) + i * (Math.PI / 3);
                double r = 7.0 - (t % 20) * 0.3;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * r, core.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
            }
            BgFx.sphere(level, core, 1.3, 24, BgFx.dust(0x05010A, 2.2F));
            BgFx.ring(level, core, 2.0, 20, BgFx.dust(COLOR, 1.2F), 0.0);
        }
        if (t % 10 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(core, core).inflate(3.5), this::isFoe)) {
                if (e.hurt(damageSources().indirectMagic(this, this), scaled(5F))) {
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), this);
                }
            }
            if (t % 40 == 0) level.playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_AMBIENT, getSoundSource(), 2.0F, 0.5F);
        }
        if (holeTicks <= 0) holeCenter = null;
    }

    // ---------------- Fagulhas
    private void summonWisps(ServerLevel level, int count) {
        summonCd = 600 + random.nextInt(200);
        attackTimer = 16;
        action("summon");
        announce("summon");
        playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 2.0F, 0.7F);
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 at = position().add(Math.cos(a) * 3, 1.5, Math.sin(a) * 3);
            StarWisp w = ElRegistries.STAR_WISP.get().create(level);
            if (w == null) continue;
            w.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0);
            w.finalizeSpawn(level, level.getCurrentDifficultyAt(w.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            w.addTag(MINION_TAG);
            level.addFreshEntity(w);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.05);
        }
    }

    // ------------------------------------------------------------------ Eventos da base

    @Override
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        // Voa: não salta. Aparece perto e abre um poço embaixo do alvo.
        if (BossKit.blinkNear(this, target, 5.0)) {
            hintOnce(sl, "unreachable");
            wellCd = 0;
        }
    }

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        BossKit.roar(this, SoundEvents.ENDER_DRAGON_GROWL, 0.6F);
        BossKit.shake(level, position(), 32, 1.0F, 16);
        if (phase == 1) {
            meteorCd = 20;
            summonWisps(level, 2);
        } else if (phase == 2) {
            holeCd = 30;
            summonWisps(level, 3);
        }
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.5F);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        super.introTick(level, t);
        if (t == 30) {
            BossKit.flashNear(level, position(), 40, COLOR, 0.35F, 12);
            BgFx.sphere(level, position().add(0, 1.8, 0), 3.0, 60, ParticleTypes.END_ROD);
        }
    }

    @Override
    protected void onReset(ServerLevel level) {
        setNoGravity(false);
        holeTicks = 0;
        holeCenter = null;
        attackTimer = 0;
    }

    @Override
    protected void onDefeated(ServerLevel sl) {
        super.onDefeated(sl);
        holeTicks = 0;
        holeCenter = null;
        BossKit.flashNear(sl, position(), 48, 0xFFFFFF, 0.6F, 20);
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropItem(ElRegistries.STAR_FRAGMENT.get(), 4, 8);
        dropItem(ElRegistries.STAR_UPGRADE_TEMPLATE.get(), 1, 1);
        dropItem(ElRegistries.VOID_TOME.get(), 1, 2);
        dropItem(CoreRegistries.MANA_CRYSTAL.get(), 4, 8);
        dropEssence(Element.ARCANE, 3, 6);
        // Dois pergaminhos dos glifos espaciais (ou arcanos altos, se o módulo de magia espacial faltar).
        Player killer = source.getEntity() instanceof Player p ? p : null;
        for (int i = 0; i < 2; i++) spawnAtLocation(ElSpaceGlyphs.randomScroll(random, killer));
        // Um tesouro garantido: Portão do Tesouro ou Cajado do Rei Supremo.
        spawnAtLocation((random.nextBoolean() ? ElRegistries.VOID_TREASURE_GATE : ElRegistries.VOID_OVERLORD_STAFF).get().getDefaultInstance());
        if (random.nextFloat() < 0.15F) dropById(random.nextBoolean() ? "relic_seraph_feather" : "relic_dragon_scale", 1, 1);
    }

    @Override
    protected void clientTick() {
        if (isDeadOrDying() || !isAwake()) return;
        if (random.nextInt(BgFx.reduced() ? 6 : 2) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(ParticleTypes.END_ROD, getX() + Math.cos(a) * 2.0, getY() + 1.2 + random.nextDouble() * 1.6,
                    getZ() + Math.sin(a) * 2.0, 0, -0.02, 0);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_GROWL;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SdFlying", isNoGravity());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setNoGravity(tag.getBoolean("SdFlying") && isAwake());
    }
}
