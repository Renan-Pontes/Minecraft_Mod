package com.renanpontes.isekaimahou.feature.legends_e.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_e.LeFx;
import com.renanpontes.isekaimahou.feature.legends_e.LeRegistry;
import com.renanpontes.isekaimahou.feature.legends_e.LeUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A mão espectral do Agarrar o Coração (modelo GeckoLib: mão translúcida + coração). Segue o alvo entre ele e quem
 * conjurou; o coração sai do peito, bate cada vez mais rápido e é esmagado no tick {@link #SQUEEZE} — aí o efeito
 * acontece (a animação "grasp" tem o mesmo tempo). Temporária.
 */
public class GraspHeartEntity extends LeSpellEntity implements GeoEntity {
    public static final int SQUEEZE = 25;
    public static final int LIFE = 36;
    /** Vida máxima de quem morre na hora com poder 1. */
    public static final float THRESHOLD = 60F;
    private static final int MAX_PER_CASTER = 16;
    private static final int[] BEATS = {6, 12, 17, 21, 24};
    private static final RawAnimation GRASP = RawAnimation.begin().thenPlayAndHold("animation.grasp_heart_hand.grasp");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private LivingEntity target;
    @Nullable
    private UUID owner;

    public GraspHeartEntity(EntityType<? extends GraspHeartEntity> type, Level level) {
        super(type, level);
    }

    /** Começa o golpe no alvo (com a mão, se não houver mãos demais desse conjurador; senão só o efeito, no mesmo tempo). */
    public static void grasp(SpellContext ctx, LivingEntity target, SpellStats stats) {
        ServerLevel level = ctx.level();
        UUID id = ctx.casterId();
        SpellStats s = stats.copy();
        if (id != null && level.getEntities(LeRegistry.GRASP_HEART.get(), e -> id.equals(e.owner)).size() >= MAX_PER_CASTER) {
            BossKit.schedule(level, SQUEEZE, () -> {
                if (target.isAlive() && target.level() == level) crush(level, ctx, target, s, null);
            });
            return;
        }
        GraspHeartEntity e = new GraspHeartEntity(LeRegistry.GRASP_HEART.get(), level);
        e.bind(ctx, s);
        e.owner = id;
        e.target = target;
        e.follow();
        e.xo = e.getX();
        e.yo = e.getY();
        e.zo = e.getZ();
        level.addFreshEntity(e);
        LeFx.sound(level, target.position(), SoundEvents.SOUL_ESCAPE.value(), 1.6F, 0.6F);
        LeFx.send(level, Vfx.sculkSoul(), e.position(), 6, 0.3, 0.02);
    }

    /** Fica entre o alvo e o conjurador, na altura do peito, com a palma virada para o alvo. */
    private void follow() {
        if (target == null) return;
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.62, 0);
        Vec3 dir = null;
        LivingEntity c = ctx != null ? ctx.caster() : null;
        if (c != null && c.level() == level()) dir = c.position().subtract(target.position()).multiply(1, 0, 1);
        if (dir == null || dir.lengthSqr() < 1.0E-4) dir = target.getLookAngle().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();
        Vec3 p = chest.add(dir.scale(target.getBbWidth() * 0.5 + 0.55)).subtract(0, 0.45, 0);
        setPos(p.x, p.y, p.z);
        // A frente do modelo (palma) olha para o alvo: o "olhar" é -dir
        setYRot((float) (Mth.atan2(dir.x, -dir.z) * Mth.RAD_TO_DEG));
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        if (target == null || !target.isAlive() || target.level() != level) {
            if (age < SQUEEZE) LeFx.send(level, Vfx.sculkSoul(), position(), 8, 0.3, 0.03);
            discard();
            return;
        }
        if (age <= SQUEEZE) follow();
        for (int b : BEATS) {
            if (age == b) LeFx.sound(level, position(), SoundEvents.WARDEN_HEARTBEAT, 2.2F, 0.8F + age / 60F);
        }
        if (age == SQUEEZE) crush(level, ctx, target, stats != null ? stats : new SpellStats(), this);
        if (age >= LIFE) discard();
    }

    /** O esmagamento: mata criaturas comuns fracas; o resto sangra; chefes e jogadores perdem 15% e ficam atordoados. */
    static void crush(ServerLevel level, SpellContext ctx, LivingEntity t, SpellStats s, @Nullable Entity direct) {
        Vec3 c = t.position().add(0, t.getBbHeight() * 0.62, 0);
        LivingEntity caster = ctx.caster();
        float power = LeUtil.power(ctx, s);
        if (LeUtil.resistsExecution(t)) {
            LeUtil.percent(ctx, t, 0.15F, s, direct);
            Statuses.apply(t, Statuses.Kind.STUNNED, 1, 40, caster, ctx.powerMult());
        } else if (t.getHealth() + t.getAbsorptionAmount() <= THRESHOLD * Math.max(1F, power)) {
            LeUtil.execute(ctx, t, s, direct);
        } else {
            ctx.damage(t, 18F, s, direct);
            if (t.isAlive()) {
                Statuses.apply(t, Statuses.Kind.STUNNED, 1, 20, caster, ctx.powerMult());
                Statuses.apply(t, Statuses.Kind.BLEEDING, 3, 120, caster, ctx.powerMult());
            }
        }
        LeFx.send(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), c, 30, 0.25, 0.25);
        LeFx.send(level, LeFx.dust(0xB0102A, 1.8F), c, 26, 0.35, 0.05);
        LeFx.send(level, LeFx.fade(0xFF3A4A, 0x200008, 1.2F), c, 16, 0.5, 0.1);
        LeFx.send(level, Vfx.sculkSoul(), c, 6, 0.2, 0.05);
        LeFx.sound(level, c, SoundEvents.SLIME_BLOCK_BREAK, 2.0F, 0.5F);
        LeFx.sound(level, c, SoundEvents.HONEY_BLOCK_BREAK, 1.6F, 0.6F);
        LeFx.sound(level, c, SoundEvents.WARDEN_HEARTBEAT, 2.5F, 0.5F);
    }

    @Override
    protected void clientTick() {
        if (age < SQUEEZE && random.nextInt(3) == 0) {
            level().addParticle(Vfx.soulFlame(), getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * 0.9,
                    getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.01, 0);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(GRASP)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
