package com.renanpontes.isekaimahou.feature.creatures_e.entity.abyss;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.CreatureMove;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalCreature;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Olho Vigia (Ocos do Vazio): olho flutuante com tentaculos. Carrega um raio de sombra (o feixe vai
 * engrossando; da para quebrar a linha de visao) e quem o encara sofre o Olhar do Vazio.
 */
public class WatcherEye extends DimensionalCreature implements Enemy {
    private static final int BEAM_CHARGE = 40;

    public WatcherEye(EntityType<? extends WatcherEye> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(55, 4, 6, 0.25)
                .add(Attributes.FLYING_SPEED, 0.11)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    public Faction faction() {
        return Faction.ABYSS;
    }

    @Override
    protected int experience() {
        return 25;
    }

    @Override
    public boolean flies() {
        return true;
    }

    @Override
    public double preferredRange() {
        return 9.0;
    }

    @Override
    public double hoverHeight() {
        return 3.5;
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of(
                CreatureMove.of("gaze").timing(10, 22).cooldown(220).range(0, 12)
                        .when(t -> t instanceof Player p && isLookingAtMe(p))
                        .perform(this::gaze),
                CreatureMove.of("attack").timing(6, 12).cooldown(24).range(0, 1.8).perform(this::lash),
                CreatureMove.of("beam").timing(BEAM_CHARGE, BEAM_CHARGE + 6).cooldown(80).range(1.5, 22)
                        .onStart(t -> {
                            setFocus(t);
                            CeFx.sound(level(), position(), SoundEvents.GUARDIAN_ATTACK, 1.2F, 0.6F);
                        })
                        .during((t, i) -> {
                            if (i == 1 && t instanceof Player) t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
                            if (i < BEAM_CHARGE && i % 10 == 0) CeFx.sound(level(), position(), SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.6F);
                        })
                        .perform(this::fireBeam));
    }

    /** Mesmo teste do enderman: o jogador esta olhando direto para o olho? */
    private boolean isLookingAtMe(Player p) {
        Vec3 view = p.getViewVector(1.0F).normalize();
        Vec3 to = new Vec3(getX() - p.getX(), getEyeY() - p.getEyeY(), getZ() - p.getZ());
        double d = to.length();
        return view.dot(to.normalize()) > 1.0 - 0.025 / d && p.hasLineOfSight(this);
    }

    private void gaze(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), this);
        t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0), this);
        CeFx.line(level, position().add(0, 0.6, 0), t.getEyePosition(), CeFx.dust(0x8A4FC8, 0.8F), 3);
        CeFx.burst(level, t.getEyePosition(), ParticleTypes.REVERSE_PORTAL, 20, 0.3, 0.05);
        CeFx.sound(level, position(), SoundEvents.ENDERMAN_STARE, 1.0F, 1.3F);
    }

    private void lash(LivingEntity t) {
        if (!(level() instanceof ServerLevel level) || gapTo(t) > 2.6) return;
        if (meleeHit(t, 1.0F, 0.5)) t.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 60, 0), this);
        CeFx.burst(level, center(t), ParticleTypes.SQUID_INK, 10, 0.3, 0.03);
        CeFx.sound(level, center(t), SoundEvents.SLIME_ATTACK, 1.0F, 0.6F);
    }

    /** Dispara o raio de sombra se o alvo ainda estiver visivel; senao, o feixe se dissipa. */
    private void fireBeam(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        if (!t.isAlive() || !hasLineOfSight(t) || distanceTo(t) > 26) {
            CeFx.burst(level, from, ParticleTypes.SMOKE, 12, 0.3, 0.02);
            CeFx.sound(level, from, SoundEvents.FIRE_EXTINGUISH, 0.8F, 0.6F);
            return;
        }
        Vec3 to = center(t);
        CeFx.line(level, from, to, ParticleTypes.SQUID_INK, 3);
        CeFx.line(level, from, to, CeFx.dust(0xA04BFF, 1.6F), 4);
        CeFx.line(level, from, to, ParticleTypes.WITCH, 1.5);
        CeFx.burst(level, to, ParticleTypes.SONIC_BOOM, 1, 0.0, 0.0);
        CeFx.burst(level, to, ParticleTypes.REVERSE_PORTAL, 30, 0.4, 0.1);
        CeFx.sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, 0.9F, 1.7F);
        if (strike(t, 12.0F, magicSource(this))) {
            t.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 100, 0), this);
            t.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 100, 0), this);
        }
    }

    @Override
    protected void clientFx() {
        var r = random;
        if (r.nextInt(2) == 0) level().addParticle(ParticleTypes.PORTAL, getRandomX(0.6), getY() + r.nextDouble() * 1.2, getRandomZ(0.6),
                (r.nextDouble() - 0.5) * 0.4, -0.2, (r.nextDouble() - 0.5) * 0.4);
        if (r.nextInt(4) == 0) level().addParticle(CeFx.dust(0x5A2A8A, 0.7F), getRandomX(0.5), getY(), getRandomZ(0.5), 0, -0.05, 0);
        // feixe carregando: fino no inicio, cada vez mais denso
        if ("beam".equals(actionName())) {
            Entity f = focus();
            int age = actionAge();
            if (f != null && age < BEAM_CHARGE) {
                Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
                Vec3 to = f.position().add(0, f.getBbHeight() * 0.5, 0);
                double density = 0.6 + age / (double) BEAM_CHARGE * 2.5;
                CeFx.clientLine(level(), from, to, CeFx.dust(0x8A4FC8, 0.5F + age / (float) BEAM_CHARGE), density, 0.06);
                if (age > BEAM_CHARGE - 12) CeFx.clientLine(level(), from, to, ParticleTypes.REVERSE_PORTAL, 0.8, 0.1);
            }
        }
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 0.6, 0);
        CeFx.burst(level, c, ParticleTypes.REVERSE_PORTAL, 40, 0.5, 0.08);
        CeFx.burst(level, c, ParticleTypes.SQUID_INK, 20, 0.4, 0.03);
        CeFx.sound(level, c, SoundEvents.ENDER_EYE_DEATH, 1.2F, 0.6F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }
}
