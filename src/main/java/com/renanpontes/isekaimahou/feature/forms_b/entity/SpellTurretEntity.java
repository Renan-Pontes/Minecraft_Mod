package com.renanpontes.isekaimahou.feature.forms_b.entity;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.entity.SpellProjectileEntity;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Torre: cristal flutuante que, a cada 30 ticks, lança um projétil da magia na ameaça mais
 * próxima (16 blocos, com linha de visão). Dividir = mais projéteis em leque. Conta como persistente.
 * Magias programáveis: os projéteis avisam o núcleo ao acertar/expirar; o cristal sumir = Ao Terminar.
 */
public class SpellTurretEntity extends SpellFormEntity implements GeoEntity {
    public static final int FIRE_INTERVAL = 30;
    public static final double RANGE = 16.0;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.spell_turret.idle");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("animation.spell_turret.shoot");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SpellTurretEntity(EntityType<? extends SpellTurretEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isPersistentSpell() { return true; }

    /** Ponto de onde os projéteis saem (centro do cristal). */
    public Vec3 muzzle() {
        return position().add(0, 0.9, 0);
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        if (age == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        if (age % FIRE_INTERVAL != 0) return;
        LivingEntity shooter = ctx.caster();
        if (shooter == null || shooter.level() != level) return; // dono longe: a torre só espera
        Vec3 from = muzzle();
        boolean ghost = ctx.formStats().ghost;
        LivingEntity target = FormsBUtil.nearestThreat(ctx, from, RANGE, !ghost);
        if (target == null) return;
        fireAt(level, ctx, shooter, from, target);
    }

    private void fireAt(ServerLevel level, SpellContext ctx, LivingEntity shooter, Vec3 from, LivingEntity target) {
        SpellStats s = ctx.formStats();
        // Cada rajada é uma "conjuração" nova do estágio: os projéteis dela podem disparar Ao Acertar/Saltar de novo
        // (o orçamento de estágios-filho da magia inteira continua compartilhado e limitado pelo núcleo)
        SpellContext volley = ctx.copy(ctx.powerMult());
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 dir = to.subtract(from);
        float speed = 1.4F * s.speedMult();
        if (s.arc) {
            // Arco: compensa a queda (gravidade 0.04) para cair no alvo
            double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            dir = dir.add(0, flat * 0.12, 0);
        }
        dir = dir.normalize();
        int n = s.count();
        for (int i = 0; i < n; i++) {
            Vec3 d = dir;
            if (n > 1) {
                float off = (i - (n - 1) / 2.0F) * 9.0F * Mth.DEG_TO_RAD;
                d = dir.yRot(off);
            }
            SpellProjectileEntity p = SpellProjectileEntity.create(volley, shooter);
            p.setPos(from.x + d.x * 0.6, from.y + d.y * 0.6, from.z + d.z * 0.6);
            p.shoot(d.x, d.y, d.z, speed, 1.0F);
            level.addFreshEntity(p);
        }
        setYRot((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F);
        triggerAnim("action", "shoot");
        ElementFx.burst(level, from, element(), 8, 0.15);
        if (accentElement() != element()) ElementFx.burst(level, from, accentElement(), 5, 0.15);
        level.sendParticles(Vfx.endRod(), from.x, from.y, from.z, 4, 0.1, 0.1, 0.1, 0.05);
        level.playSound(null, from.x, from.y, from.z, ElementFx.castSoundOf(element()), SoundSource.PLAYERS, 0.6F, 1.3F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, 1.6F);
    }

    @Override
    protected void onExpire(ServerLevel level, SpellContext ctx) {
        Vec3 c = muzzle();
        ElementFx.burst(level, c, element(), 20, 0.35);
        if (accentElement() != element()) ElementFx.burst(level, c, accentElement(), 12, 0.35);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
        // Ao Terminar: o cristal se parte e o próximo estágio nasce no lugar dele
        stageEnd(ctx, c);
    }

    @Override
    protected void clientTick() {
        boolean reduced = FormsBUtil.reducedParticles();
        if (!reduced || age % 3 == 0) {
            // Fragmentos de luz caindo do cristal até o chão
            level().addParticle(FormsBUtil.dust(color(), 0.6F), getX() + (random.nextDouble() - 0.5) * 0.5,
                    getY() + 0.4 + random.nextDouble() * 0.6, getZ() + (random.nextDouble() - 0.5) * 0.5, 0, -0.03, 0);
        }
        if (!reduced && age % 4 == 0) {
            double a = age * 0.2;
            level().addParticle(ElementFx.signature(elementFor(age / 4)), getX() + Math.cos(a) * 0.7, getY() + 0.05,
                    getZ() + Math.sin(a) * 0.7, 0, 0.01, 0);
        }
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> state.setAndContinue(IDLE)));
        // Controlador separado: o disparo pulsa o cristal sem interromper o giro do "main"
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("shoot", SHOOT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
