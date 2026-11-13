package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossCurses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ignis, o Titã das Chamas Eternas (Guardião do Fogo).
 * Fase 1: soco (cone curto), pisão com onda de fogo que dá para PULAR, bolas de fogo (glifos), sopro ígneo que varre
 * um leque desenhado, chuva de meteoros (cada um marca onde cai), erupção de lava, Chuva de Brasas (dezenas de
 * pequenos círculos em ondas) e Investida Flamejante (linha marcada + rastro de fogo).
 * Fase 2 (60%): o corpo arde em chamas AZUIS — tudo mais forte, ondas duplas, mais meteoros.
 * Fase 3 (25%): aura de calor visível (anel pulsante, dano leve) e a Supernova (fuja do círculo ou esconda-se atrás
 * de um pilar).
 */
public class IgnisEntity extends ElementalGuardian {
    static final List<GuardianMove> MOVES = List.of(
            GuardianMove.of("punch", 22).range(0, 4.0).cooldown(16).weight(16).aimUntil(1),
            GuardianMove.of("slam", 36).range(0, 9).cooldown(100).weight(10).announce().aimUntil(1),
            GuardianMove.of("fireball", 32).range(3, 32).cooldown(50).weight(12),
            GuardianMove.of("breath", 44).range(0, 9).cooldown(140).weight(8).announce().aimUntil(1),
            GuardianMove.of("meteor", 64).range(0, 40).cooldown(380).weight(9).announce(),
            GuardianMove.of("eruption", 44).range(0, 26).cooldown(240).weight(8).announce(),
            GuardianMove.of("ember_rain", 56).range(0, 30).cooldown(260).weight(9).announce(),
            GuardianMove.of("flame_charge", 44).range(5, 22).cooldown(170).weight(10).announce().selfMoving(),
            GuardianMove.of("supernova", 80).range(0, 18).cooldown(520).weight(14).phases(3, 3).announce(),
            leapMove());

    /** Cor das chamas azuis na telegrafia (fase 2+). */
    private static final int BLUE_FIRE = 0x4FC3FF;
    private static final float BREATH_RANGE = 10F;
    private static final float BREATH_WIDTH = 50F;
    private static final float BREATH_SWEEP = 60F;

    private final List<Vec3> eruptionSpots = new ArrayList<>();
    private final Map<UUID, Integer> breathHit = new HashMap<>();
    private final Set<UUID> chargeHit = new HashSet<>();
    @Nullable private Telegraph.Shape punchShape;
    private int punchId = -1;
    @Nullable private Telegraph.Shape slamShape;
    private float breathYaw;
    private Vec3 chargeFrom = Vec3.ZERO;
    private Vec3 chargeDir = Vec3.ZERO;
    private int chargeTicks;
    private boolean charging;
    /** Faixa da investida (fica no chão enquanto o Ignis corre; some quando ele para). */
    private int chargeMark = -1;

    public IgnisEntity(EntityType<? extends IgnisEntity> type, Level level) {
        super(type, level, MOVES);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 420.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    public Element element() { return Element.FIRE; }

    /** Chamas azuis a partir da fase 2 (o renderer troca a textura). */
    public boolean isBlueFlame() { return phase() >= 2; }

    private ParticleOptions flame() {
        return isBlueFlame() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
    }

    /** Cor da telegrafia: laranja do fogo, azul na fase 2+. */
    private int fireColor() {
        return isBlueFlame() ? BLUE_FIRE : Telegraph.color(Element.FIRE);
    }

    @Override
    protected BossEvent.BossBarColor barColor(int phase) {
        return phase >= 2 ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED;
    }

    @Override
    protected SoundEvent roarSound() { return SoundEvents.RAVAGER_ROAR; }

    @Override
    protected float roarPitch() { return 0.55F; }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.BLAZE_BURN; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.BLAZE_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.BLAZE_DEATH; }

    /** Passos pesados de pedra vulcânica. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.2F, 0.6F);
    }

    @Override
    protected void addIdleGoals() {
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
    }

    @Override
    protected double preferredRange() { return 2.5; }

    @Override
    protected String[] rangedRecipes() {
        return new String[] {"projectile homing ignite amplify", "projectile homing ignite", "projectile ignite"};
    }

    // =====================================================================================
    //  Golpes
    // =====================================================================================
    @Override
    protected void performMove(ServerLevel level, GuardianMove move, int t, @Nullable LivingEntity target) {
        switch (move.id) {
            case "punch" -> punch(level, t);
            case "slam" -> slam(level, t);
            case "fireball" -> fireball(level, t, target);
            case "breath" -> breath(level, t);
            case "meteor" -> meteor(level, t, target);
            case "eruption" -> eruption(level, t, target);
            case "ember_rain" -> emberRain(level, t, target);
            case "flame_charge" -> flameCharge(level, t, target);
            case "supernova" -> supernova(level, t);
            default -> {}
        }
    }

    private Vec3 forward() {
        return BossSpells.dirOf(bodyYaw());
    }

    /** Soco: cone curto de 10 ticks na frente; acerta quem AINDA está no cone quando o punho desce. */
    private void punch(ServerLevel level, int t) {
        if (t == 1) {
            punchShape = Telegraph.Shape.cone(position(), bodyYaw(), 100F, getBbWidth() * 0.5F + 4.2F);
            punchId = Telegraph.show(level, Telegraph.Shape.cone(position(), bodyYaw(), 100F, getBbWidth() * 0.5F + 4.2F)
                    .warmup(10).color(fireColor()).owner(this).noRunes());
        }
        if (t == 6) level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4F, 0.5F);
        if (t != 11 || punchShape == null) return;
        Vec3 fist = position().add(forward().scale(getBbWidth() * 0.5 + 1.6)).add(0, 1.6, 0);
        level.sendParticles(flame(), fist.x, fist.y, fist.z, 30, 0.6, 0.6, 0.6, 0.08);
        level.sendParticles(ParticleTypes.EXPLOSION, fist.x, fist.y, fist.z, 1, 0, 0, 0, 0);
        level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2F, 0.6F);
        for (LivingEntity e : foesIn(level, punchShape)) {
            if (e.hurt(damageSources().mobAttack(this), meleeDamage())) {
                e.igniteForSeconds(isBlueFlame() ? 6 : 4);
                BossSpells.knock(e, e.position().subtract(position()), 1.1, 0.4);
            }
        }
        Telegraph.finish(level, punchId);
        punchShape = null;
    }

    /**
     * Pisão: o centro (vermelho) explode no tick 18; a onda de fogo (anel laranja) corre rente ao chão — PULE na hora
     * em que ela passa. Na fase 2+ vem uma segunda onda maior.
     */
    private void slam(ServerLevel level, int t) {
        float inner = getBbWidth() * 0.5F + 2.5F;
        if (t == 0) {
            slamShape = Telegraph.Shape.circle(position(), inner);
            Telegraph.show(level, Telegraph.Shape.circle(position(), inner).warmup(18).color(Telegraph.DANGER).owner(this));
            Telegraph.show(level, Telegraph.Shape.ring(position(), inner, 13F).warmup(18).hold(18).vertical(0.5, 1.0)
                    .color(Telegraph.WARNING).owner(this));
            hint("slam", 32);
        }
        if (t == 18 && phase() >= 2) {
            Telegraph.show(level, Telegraph.Shape.ring(position(), inner, 17F).warmup(9).hold(22).vertical(0.5, 1.0)
                    .color(fireColor()).owner(this).noRunes());
        }
        if (t < 16 && t % 3 == 0) {
            level.sendParticles(flame(), getX(), getY() + getBbHeight(), getZ(), 6, 0.8, 0.3, 0.8, 0.03);
        }
        if (t == 18 || (t == 27 && phase() >= 2)) {
            boolean second = t == 27;
            Vec3 c = position();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(flame(), c.x, c.y + 0.4, c.z, 60, 2.0, 0.3, 2.0, 0.15);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5F, 0.6F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, 0.5F);
            BossKit.shake(level, c, 24, second ? 1.0F : 1.4F, 14);
            GuardianShockwave.create(this, c, Element.FIRE)
                    .radius(second ? 17F : 13F, second ? 0.85F : 0.7F)
                    .damage(10F * mult())
                    .ignite(isBlueFlame() ? 7 : 5).knock(0.9, 0.45).blue(isBlueFlame())
                    .groundOnly()
                    .launch();
            if (!second && slamShape != null) {
                for (LivingEntity e : foesIn(level, slamShape)) e.hurt(damageSources().mobAttack(this), meleeDamage() * 1.2F);
            }
        }
    }

    private void fireball(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (target == null) return;
        boolean cast = phase() >= 2 ? (t == 12 || t == 19 || t == 26) : (t == 12 || t == 24);
        if (t < 12 && t % 2 == 0) {
            Vec3 hand = position().add(forward().scale(1.6)).add(0, getBbHeight() * 0.6, 0);
            level.sendParticles(flame(), hand.x, hand.y, hand.z, 4, 0.3, 0.3, 0.3, 0.02);
        }
        if (!cast) return;
        if (phase() >= 3) {
            // Receitas abaixo de 45 de mana: não disparam o anúncio genérico (o chefe anuncia os próprios golpes)
            BossSpells.cast(this, Element.FIRE, target, spellPower(), "projectile split split ignite", "projectile split ignite");
        } else if (phase() == 2) {
            BossSpells.cast(this, Element.FIRE, target, spellPower(), "projectile split ignite amplify",
                    "projectile split ignite");
        } else {
            BossSpells.cast(this, Element.FIRE, target, spellPower(), "projectile accelerate ignite amplify",
                    "projectile ignite");
        }
    }

    /**
     * Sopro Ígneo: o leque inteiro é desenhado antes (12 ticks); depois o jato VARRE de um lado ao outro.
     * Fique fora do leque ou acompanhe o lado de onde o jato já passou.
     */
    private void breath(ServerLevel level, int t) {
        if (t == 0) {
            breathYaw = bodyYaw();
            breathHit.clear();
            Telegraph.show(level, Telegraph.Shape.cone(position(), breathYaw, BREATH_WIDTH + BREATH_SWEEP, BREATH_RANGE)
                    .warmup(12).hold(26).color(fireColor()).owner(this));
        }
        if (t == 8) level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 2.5F, 0.4F);
        if (t < 12 || t > 38) return;
        float yaw = breathYaw + Mth.lerp((t - 12) / 26F, -BREATH_SWEEP * 0.5F, BREATH_SWEEP * 0.5F);
        BossSpells.setYaw(this, yaw);
        Vec3 dir = BossSpells.dirOf(yaw);
        Vec3 mouth = getEyePosition().add(dir.scale(1.2));
        for (int i = 0; i < 6; i++) {
            Vec3 spread = dir.add((random.nextDouble() - 0.5) * 0.45, -0.28 + (random.nextDouble() - 0.5) * 0.2,
                    (random.nextDouble() - 0.5) * 0.45);
            level.sendParticles(flame(), mouth.x, mouth.y, mouth.z, 0, spread.x, spread.y, spread.z, 0.9);
        }
        if (t % 3 == 0) level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.7F);
        if (t % 2 != 0) return;
        Telegraph.Shape jet = Telegraph.Shape.cone(position(), yaw, BREATH_WIDTH, BREATH_RANGE);
        float dmg = 4F * mult();
        for (LivingEntity e : foesIn(level, jet)) {
            // Cada um leva no máx. um golpe a cada 10 ticks (o jato passa, não "trava" no jogador)
            Integer last = breathHit.get(e.getUUID());
            if (last != null && t - last < 10) continue;
            breathHit.put(e.getUUID(), t);
            BossSpells.hit(this, e, Element.FIRE, dmg);
            e.igniteForSeconds(4);
            // O jato é o calor mais forte: derrete a armadura mais rápido
            BossCurses.apply(e, BossCurses.SCORCHED, 100, Math.min(3, phase()));
        }
    }

    /** Chuva de Meteoros: cada meteoro marca o chão (círculo) e cai exatamente quando o círculo enche. */
    private void meteor(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_DEATH, SoundSource.HOSTILE, 3.0F, 0.4F);
        if (t < 20) {
            // Chamas subindo das mãos para o céu
            for (int s = -1; s <= 1; s += 2) {
                Vec3 hand = position().add(forward().yRot(s * 1.2F).scale(1.6)).add(0, getBbHeight() + 0.5, 0);
                level.sendParticles(flame(), hand.x, hand.y, hand.z, 3, 0.2, 0.6, 0.2, 0.15);
            }
            return;
        }
        int every = switch (phase()) { case 1 -> 6; case 2 -> 5; default -> 4; };
        if (t > 56 || (t - 20) % every != 0) return;
        List<LivingEntity> foes = BossSpells.foesAround(this, position(), 40);
        if (foes.isEmpty() && target != null) foes = List.of(target);
        if (foes.isEmpty()) return;
        LivingEntity aim = foes.get(random.nextInt(foes.size()));
        double spread = phase() >= 3 ? 3.0 : 5.0;
        double ix = aim.getX() + (random.nextDouble() - 0.5) * 2 * spread;
        double iz = aim.getZ() + (random.nextDouble() - 0.5) * 2 * spread;
        Vec3 impact = groundAt(new Vec3(ix, aim.getY(), iz));
        // Cai de até 20 blocos, ou de logo abaixo do teto (templos fechados)
        double drop = Math.max(2.5, Math.min(20, BossSpells.headroom(level, impact.add(0, 0.5, 0), 21) - 1.5));
        double a = random.nextDouble() * Math.PI * 2;
        Vec3 from = impact.add(Math.cos(a) * drop * 0.3, drop, Math.sin(a) * drop * 0.3);
        int fall = switch (phase()) { case 1 -> 30; case 2 -> 28; default -> 26; };
        float size = 0.8F + random.nextFloat() * 0.6F;
        int mark = Telegraph.show(level, Telegraph.Shape.circle(impact, IgnisMeteor.radiusFor(size)).warmup(fall).color(fireColor())
                .owner(this).noRunes());
        IgnisMeteor.launch(this, from, impact, fall, size, 12F * mult(), phase() >= 2 ? 0.45F : 0.3F, isBlueFlame(), mark);
    }

    /** Erupção: marca quem está perto (e alguns pontos ao acaso); 16 ticks depois a lava sobe do chão. */
    private void eruption(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t == 14) {
            eruptionSpots.clear();
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 0.4F);
            for (LivingEntity e : BossSpells.foesAround(this, position(), 26)) {
                if (eruptionSpots.size() >= 4) break;
                eruptionSpots.add(groundAt(e.position()));
            }
            int extra = phase() >= 2 ? 3 : 2;
            for (int i = 0; i < extra; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = 5 + random.nextDouble() * 7;
                eruptionSpots.add(groundAt(new Vec3(getX() + Math.cos(a) * d, getY(), getZ() + Math.sin(a) * d)));
            }
            float dmg = 9F * mult();
            boolean blue = isBlueFlame();
            for (Vec3 s : eruptionSpots) {
                Telegraph.delayed(level, Telegraph.Shape.circle(s, 2.8F).warmup(16).color(fireColor()).owner(this),
                        hits -> erupt(level, s, dmg, blue, hits));
            }
        }
        if (t > 14 && t < 30) {
            // O chão borbulha onde a lava vai subir
            for (Vec3 s : eruptionSpots) {
                level.sendParticles(ParticleTypes.LAVA, s.x, s.y + 0.1, s.z, 1, 0.8, 0, 0.8, 0);
                level.sendParticles(ParticleTypes.SMOKE, s.x, s.y + 0.1, s.z, 3, 1.0, 0.05, 1.0, 0.01);
            }
        }
        if (t == 30) eruptionSpots.clear();
    }

    private void erupt(ServerLevel level, Vec3 s, float dmg, boolean blue, List<LivingEntity> hits) {
        if (!isAlive()) return;
        level.sendParticles(blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, s.x, s.y + 0.5, s.z, 50, 0.7, 1.6, 0.7, 0.12);
        level.sendParticles(ParticleTypes.LAVA, s.x, s.y + 0.5, s.z, 20, 0.8, 0.8, 0.8, 0);
        level.playSound(null, s.x, s.y, s.z, SoundEvents.LAVA_POP, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.playSound(null, s.x, s.y, s.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.5F, 0.5F);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            BossSpells.hit(this, e, Element.FIRE, dmg);
            e.igniteForSeconds(6);
            BossSpells.knock(e, e.position().subtract(s), 0.3, 0.9);
        }
        IgnisLavaPool.spawn(level, this, s, 2.6F, phase() >= 2 ? 260 : 200, blue);
    }

    /**
     * Chuva de Brasas: ondas de pequenos círculos (metade em cima de quem luta, metade espalhada); cada brasa cai
     * quando o seu círculo enche. Ande pelos espaços vazios.
     */
    private void emberRain(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2.0F, 0.4F);
        }
        if (t < 40 && t % 2 == 0) {
            Vec3 top = position().add(0, getBbHeight() + 0.6, 0);
            level.sendParticles(flame(), top.x, top.y, top.z, 6, 0.6, 0.2, 0.6, 0.18);
            level.sendParticles(ParticleTypes.LAVA, top.x, top.y, top.z, 1, 0.4, 0.1, 0.4, 0);
        }
        int waves = phase() >= 3 ? 4 : 3;
        int wave = (t - 8) / 10;
        if (t < 8 || (t - 8) % 10 != 0 || wave >= waves) return;
        List<LivingEntity> foes = BossSpells.foesAround(this, position(), 30);
        if (foes.isEmpty() && target != null) foes = List.of(target);
        int n = 5 + 2 * phase();
        float dmg = 6F * mult();
        boolean blue = isBlueFlame();
        for (int i = 0; i < n; i++) {
            Vec3 p;
            if (i < foes.size() * 2 && !foes.isEmpty() && i % 2 == 0) {
                LivingEntity f = foes.get((i / 2) % foes.size());
                double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * 2.0;
                p = new Vec3(f.getX() + Math.cos(a) * d, f.getY(), f.getZ() + Math.sin(a) * d);
            } else {
                double a = random.nextDouble() * Math.PI * 2, d = 4 + random.nextDouble() * 12;
                p = new Vec3(getX() + Math.cos(a) * d, getY(), getZ() + Math.sin(a) * d);
            }
            Vec3 spot = groundAt(p);
            int warm = 22 + random.nextInt(5);
            Telegraph.delayed(level, Telegraph.Shape.circle(spot, 1.8F).warmup(warm).color(fireColor()).owner(this).noRunes(),
                    hits -> emberImpact(level, spot, dmg, blue, hits));
            // A brasa aparece caindo do céu nos últimos ticks
            BossKit.schedule(level, warm - 9, () -> {
                if (!isAlive()) return;
                for (int k = 0; k < 3; k++) {
                    level.sendParticles(blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, spot.x + (random.nextDouble() - 0.5) * 0.4,
                            spot.y + 7 + k * 0.6, spot.z + (random.nextDouble() - 0.5) * 0.4, 0, 0, -1, 0, 0.9);
                }
                level.sendParticles(ParticleTypes.LARGE_SMOKE, spot.x, spot.y + 7.5, spot.z, 0, 0, -1, 0, 0.6);
            });
        }
    }

    private void emberImpact(ServerLevel level, Vec3 s, float dmg, boolean blue, List<LivingEntity> hits) {
        if (!isAlive()) return;
        level.sendParticles(blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, s.x, s.y + 0.3, s.z, 16, 0.6, 0.3, 0.6, 0.08);
        level.sendParticles(ParticleTypes.LAVA, s.x, s.y + 0.2, s.z, 4, 0.5, 0.1, 0.5, 0);
        level.sendParticles(ParticleTypes.SMALL_FLAME, s.x, s.y + 0.1, s.z, 10, 0.8, 0.05, 0.8, 0.02);
        level.playSound(null, s.x, s.y, s.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.9F, 0.9F + random.nextFloat() * 0.3F);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            BossSpells.hit(this, e, Element.FIRE, dmg);
            e.igniteForSeconds(4);
            BossSpells.knock(e, e.position().subtract(s), 0.35, 0.3);
        }
    }

    /**
     * Investida Flamejante: a linha da corrida é desenhada (16 ticks); depois o Ignis dispara em linha reta,
     * atropela quem ficou nela e deixa um rastro de fogo que queima por alguns segundos.
     */
    private void flameCharge(ServerLevel level, int t, @Nullable LivingEntity target) {
        if (t == 0) {
            chargeHit.clear();
            charging = false;
            chargeFrom = position();
            chargeDir = BossSpells.flatDir(position(), target != null ? target.position() : position().add(forward()), forward());
            double want = Mth.clamp(target != null ? distanceTo(target) + 4 : 12, 8, 18);
            double len = BossSpells.clearDistance(level, position(), chargeDir, want + getBbWidth() * 0.5, 1.2) - getBbWidth() * 0.5;
            len = Math.max(3, len);
            chargeTicks = Mth.ceil(len / 1.3);
            BossSpells.face(this, position().add(chargeDir));
            Vec3 end = chargeFrom.add(chargeDir.scale(len + getBbWidth() * 0.5));
            chargeMark = Telegraph.show(level, Telegraph.Shape.line(chargeFrom, end, getBbWidth() + 1.2F).warmup(16).hold(chargeTicks)
                    .color(fireColor()).owner(this));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5F, 0.7F);
        }
        if (t < 16) {
            BossSpells.face(this, position().add(chargeDir));
            if (t % 2 == 0) {
                level.sendParticles(flame(), getX(), getY() + 0.2, getZ(), 8, getBbWidth() * 0.4, 0.1, getBbWidth() * 0.4, 0.05);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.4, getZ(), 2, 0.6, 0.2, 0.6, 0.02);
            }
            if (t == 12) level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.5F, 0.4F);
            return;
        }
        if (t < 16 + chargeTicks) {
            charging = true;
            BossSpells.face(this, position().add(chargeDir));
            setDeltaMovement(chargeDir.x * 1.3, getDeltaMovement().y, chargeDir.z * 1.3);
            level.sendParticles(flame(), getX(), getY() + 0.3, getZ(), 10, getBbWidth() * 0.4, 0.3, getBbWidth() * 0.4, 0.04);
            if (t % 3 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 2.0F, 0.5F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6),
                    e -> isFoe(e) && !chargeHit.contains(e.getUUID()))) {
                chargeHit.add(e.getUUID());
                if (e.hurt(damageSources().mobAttack(this), meleeDamage() * 1.25F)) {
                    e.igniteForSeconds(6);
                    BossSpells.knock(e, e.position().subtract(position()).add(chargeDir.scale(2)), 1.8, 0.6);
                }
            }
            if (horizontalCollision && t > 17) chargeTicks = t - 15;
            return;
        }
        if (charging) {
            // Fim da corrida: freia e deixa o rastro de fogo no caminho percorrido
            charging = false;
            if (chargeMark >= 0) Telegraph.cancel(level, chargeMark);
            chargeMark = -1;
            setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
            BossKit.shake(level, position(), 18, 0.8F, 10);
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 2, 0.6, 0.4, 0.6, 0);
            fireTrail(level, chargeFrom, position());
        }
    }

    private void fireTrail(ServerLevel level, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < 1.0) return;
        Telegraph.Shape trail = Telegraph.Shape.line(from, to, 2.4F);
        int mark = Telegraph.show(level, Telegraph.Shape.line(from, to, 2.4F).warmup(6).hold(80).color(fireColor()).owner(this)
                .noFlash().noRunes());
        float dmg = 2F * mult();
        boolean blue = isBlueFlame();
        int points = Math.max(2, (int) (from.distanceTo(to) / 1.5));
        for (int k = 1; k <= 17; k++) {
            int delay = k * 5;
            BossKit.schedule(level, delay, () -> {
                // O rastro só queima enquanto está desenhado (some junto se o Ignis trocar de fase ou morrer)
                if (!isAlive() || (mark >= 0 && !Telegraph.isActive(mark))) return;
                for (int i = 0; i <= points; i++) {
                    Vec3 p = from.lerp(to, i / (double) points);
                    level.sendParticles(blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, p.x, p.y + 0.15, p.z, 2, 0.5, 0.1, 0.5, 0.01);
                }
                if (delay % 10 != 0) return;
                for (LivingEntity e : foesIn(level, trail)) {
                    e.igniteForSeconds(3);
                    BossSpells.hit(this, e, Element.FIRE, dmg);
                }
            });
        }
    }

    /** Supernova: o círculo vermelho mostra o alcance; fuja dele ou fique atrás de um pilar (sem linha de visão). */
    private void supernova(ServerLevel level, int t) {
        Vec3 c = position().add(0, getBbHeight() * 0.55, 0);
        double radius = 11.0;
        if (t == 2) {
            Telegraph.show(level, Telegraph.Shape.circle(position(), (float) radius).warmup(60).color(Telegraph.DANGER).owner(this));
            hint("supernova", 32);
        }
        if (t < 62) {
            // Partículas convergindo para o núcleo
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2, b = (random.nextDouble() - 0.5) * Math.PI;
                Vec3 p = c.add(Math.cos(a) * Math.cos(b) * 7, Math.sin(b) * 5, Math.sin(a) * Math.cos(b) * 7);
                Vec3 v = c.subtract(p).normalize();
                level.sendParticles(flame(), p.x, p.y, p.z, 0, v.x, v.y, v.z, 0.45);
            }
            if (t % 10 == 0) {
                level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.5F, 0.5F + t / 120F);
            }
            return;
        }
        if (t != 62) return;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 4, 2.0, 1.0, 2.0, 0);
        level.sendParticles(flame(), c.x, c.y, c.z, 160, 3.0, 2.0, 3.0, 0.5);
        // Impacto: clarão na tela de todos por perto e chão rachado (só visual: a arena não quebra)
        BossKit.flashNear(level, c, 40, isBlueFlame() ? 0x9FE8FF : 0xFFC060, 0.75F, 18);
        BossKit.crackFloor(level, position(), radius, 7, 120);
        BossKit.debris(level, position(), radius * 0.6, 24);
        level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 60, 3.0, 2.0, 3.0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0F, 0.4F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 1.5F);
        BossKit.shake(level, c, 40, 2.5F, 30);
        for (LivingEntity e : foesIn(level, Telegraph.Shape.circle(position(), (float) radius).vertical(2.0, 8.0))) {
            // Esconder-se atrás de um bloco protege
            if (!hasLineOfSight(e)) continue;
            double d = e.position().distanceTo(position());
            float dmg = (float) (26F * mult() * Math.max(0.4, 1.0 - d / (radius + 2)));
            BossSpells.hit(this, e, Element.FIRE, dmg);
            e.igniteForSeconds(8);
            BossCurses.apply(e, BossCurses.SCORCHED, 200, 3);
            BossSpells.knock(e, e.position().subtract(position()), 2.0, 0.8);
        }
        GuardianShockwave.create(this, position(), Element.FIRE).radius(14F, 1.2F).damage(0F).knock(0.4, 0.2)
                .blue(true).launch();
    }

    // =====================================================================================
    //  Passivos e visual
    // =====================================================================================
    @Override
    protected void passiveTick(ServerLevel level, @Nullable LivingEntity target) {
        // ASSINATURA — Calor Escaldante: perto do Ignis o ar tremula e a armadura DERRETE (durabilidade). O raio cresce
        // com a fase; ficar longe (ou com Resistência ao Fogo) é a resposta.
        if (stage() != 2 && target != null && tickCount % 20 == 0 && !isDeadOrDying()) {
            double heat = getBbWidth() * 0.5 + 5.0 + 2.5 * phase();
            boolean any = false;
            for (LivingEntity e : BossSpells.foesAround(this, position(), heat)) {
                BossCurses.apply(e, BossCurses.SCORCHED, 50, phase() - 1);
                any = true;
            }
            if (any) hint("heat", 32);
            // Ondas de calor visíveis no chão em volta
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = 1.5 + random.nextDouble() * heat;
                level.sendParticles(ParticleTypes.WHITE_ASH, getX() + Math.cos(a) * d, getY() + 0.2, getZ() + Math.sin(a) * d,
                        1, 0.1, 0.4, 0.1, 0.02);
            }
        }
        // Fase 3: aura de calor — um anel que acompanha o Ignis e pulsa a cada 2 s (dano leve em quem fica colado)
        if (phase() >= 3 && stage() == 0 && tickCount % 40 == 0 && !isDeadOrDying()) {
            float r = getBbWidth() * 0.5F + 3.0F;
            float dmg = 1.5F * mult();
            Telegraph.delayed(level, Telegraph.Shape.follow(this, r).lock(0).warmup(40).color(BLUE_FIRE).owner(this).noRunes(),
                    hits -> {
                        if (!isAlive()) return;
                        for (LivingEntity e : hits) {
                            if (!isFoe(e)) continue;
                            e.igniteForSeconds(2);
                            BossSpells.hit(this, e, Element.FIRE, dmg);
                        }
                    });
        }
    }

    @Override
    protected void onIntro(ServerLevel level) {
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.5, getZ(), 120, 2.5, 3.0, 2.5, 0.1);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.4F);
    }

    @Override
    protected void introImpact(ServerLevel level) {
        super.introImpact(level);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.3, getZ(), 150, 3.5, 0.4, 3.5, 0.2);
        level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.3, getZ(), 30, 2.5, 0.3, 2.5, 0);
    }

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        if (phase == 2) {
            // As chamas mudam de cor: explosão de fogo azul
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.5, getZ(), 200,
                    2.0, 2.5, 2.0, 0.2);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 3.0F, 0.5F);
        } else if (phase == 3) {
            // Onda azul ao fim do rugido: o anel avisa e dá para pular
            Vec3 c = position();
            Telegraph.show(level, Telegraph.Shape.ring(c, getBbWidth() * 0.5F, 12F).warmup(30).hold(16).vertical(0.5, 1.0)
                    .color(BLUE_FIRE).owner(this));
            BossKit.schedule(level, 30, () -> {
                if (!isAlive()) return;
                GuardianShockwave.create(this, c, Element.FIRE).radius(12F, 0.8F).damage(6F * mult()).ignite(6)
                        .knock(1.2, 0.5).blue(true).groundOnly().launch();
            });
        }
    }

    @Override
    protected void stageFx(ServerLevel level, int stage, int left) {
        super.stageFx(level, stage, left);
        double h = getBbHeight();
        for (int i = 0; i < 4; i++) {
            double a = (left * 0.4) + i * Math.PI / 2;
            level.sendParticles(flame(), getX() + Math.cos(a) * 2.2, getY() + (left % 20) / 20.0 * h, getZ() + Math.sin(a) * 2.2,
                    1, 0, 0, 0, 0.01);
        }
    }

    @Override
    protected void deathFx(ServerLevel level, int t) {
        super.deathFx(level, t);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.7, getZ(), 3, 0.8, 0.8, 0.8, 0.03);
        if (t % 10 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    @Override
    protected void clientAmbient() {
        double w = getBbWidth() * 0.5, h = getBbHeight();
        ParticleOptions f = isBlueFlame() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        for (int i = 0; i < 2; i++) {
            level().addParticle(f, getX() + (random.nextDouble() - 0.5) * 2 * w, getY() + h * Mth.lerp(random.nextFloat(), 0.55F, 1.0F),
                    getZ() + (random.nextDouble() - 0.5) * 2 * w, 0, 0.04, 0);
        }
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + (random.nextDouble() - 0.5) * w, getY() + h + 0.2,
                    getZ() + (random.nextDouble() - 0.5) * w, 0, 0.05, 0);
        }
        if (random.nextInt(12) == 0) level().addParticle(ParticleTypes.LAVA, getX(), getY() + h * 0.6, getZ(), 0, 0, 0);
        if (walkAnimation.speed() > 0.1F && random.nextInt(2) == 0) {
            level().addParticle(f, getX() + (random.nextDouble() - 0.5) * 2 * w, getY() + 0.1, getZ() + (random.nextDouble() - 0.5) * 2 * w,
                    0, 0.02, 0);
        }
    }
}
