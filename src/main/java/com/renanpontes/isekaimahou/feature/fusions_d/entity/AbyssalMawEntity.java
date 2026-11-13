package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Bocarra Abissal: uma boca colossal de dentes em anel irrompe do chão sob o alvo, suga quem está perto, FECHA
 * engolindo (os engolidos somem dentro dela e são mastigados) e depois os cospe longe, encharcados. Chefes não cabem:
 * levam uma mordida forte. Modelo GeckoLib com animações próprias.
 */
public class AbyssalMawEntity extends FdSpellEntity implements GeoEntity {
    public static final int EMERGE = 0, OPEN = 1, BITE = 2, CHEW = 3, SPIT = 4, SINK = 5;
    private static final int EMERGE_T = 14, OPEN_T = 14, BITE_T = 6, SPIT_T = 8, SINK_T = 14;
    private static final RawAnimation A_EMERGE = RawAnimation.begin().thenPlay("animation.fd_abyssal_maw.emerge")
            .thenLoop("animation.fd_abyssal_maw.open");
    private static final RawAnimation A_OPEN = RawAnimation.begin().thenLoop("animation.fd_abyssal_maw.open");
    private static final RawAnimation A_BITE = RawAnimation.begin().thenPlay("animation.fd_abyssal_maw.bite")
            .thenLoop("animation.fd_abyssal_maw.chew");
    private static final RawAnimation A_SPIT = RawAnimation.begin().thenPlayAndHold("animation.fd_abyssal_maw.spit");
    private static final RawAnimation A_SINK = RawAnimation.begin().thenPlayAndHold("animation.fd_abyssal_maw.sink");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<UUID> swallowed = new ArrayList<>();
    private float damage = 4F;
    private float radius = 2.8F;
    private int chewTicks = 40;

    public AbyssalMawEntity(EntityType<? extends AbyssalMawEntity> type, Level level) {
        super(type, level);
    }

    public static void erupt(SpellContext ctx, Vec3 at, SpellStats stats) {
        ServerLevel sl = ctx.level();
        enforceCap(sl, FdRegistries.ABYSSAL_MAW.get(), ctx.casterId(), 2);
        Vec3 g = FdUtil.ground(sl, at, 8);
        AbyssalMawEntity m = new AbyssalMawEntity(FdRegistries.ABYSSAL_MAW.get(), sl);
        m.setPos(g.x, g.y, g.z);
        m.setYRot(sl.random.nextFloat() * 360F);
        m.chewTicks = Math.min(120, stats.duration(40));
        m.setup(ctx, EMERGE_T + OPEN_T + BITE_T + m.chewTicks + SPIT_T + SINK_T, stats);
        m.damage = 4F * stats.power();
        m.radius = Math.min(6F, stats.radius(2.8F));
        sl.addFreshEntity(m);
        // v1.3: a bocarra abre num redemoinho escuro do abismo
        com.renanpontes.isekaimahou.core.ElementFx.portal(sl, g.add(0, 0.08, 0), null, com.renanpontes.isekaimahou.core.Element.ABYSS, 0x2A4F9A, m.radius, 4, 40);
        com.renanpontes.isekaimahou.core.ElementFx.impactFx(sl, g.add(0, 0.2, 0), com.renanpontes.isekaimahou.core.Element.ABYSS, 0x2A4F9A, 3, null, 1.0F);
        Destruction.sphere(sl, g.add(0, -1.2, 0), 2.4F, ctx.caster(), Destruction.Mode.BREAK);
        BlockState under = sl.getBlockState(BlockPos.containing(g).below());
        if (under.isAir()) under = Blocks.MUD.defaultBlockState();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), g.x, g.y + 0.3, g.z, 60, 1.4, 0.4, 1.4, 0.3);
        sl.sendParticles(Vfx.splash(), g.x, g.y + 0.6, g.z, 60, 1.4, 0.8, 1.4, 0.3);
        FdFx.sound(sl, g, SoundEvents.RAVAGER_ROAR, 1.6F, 0.5F);
        FdFx.sound(sl, g, SoundEvents.GENERIC_SPLASH, 1.6F, 0.5F);
        BossKit.shake(sl, g, 20, 1.0F, 14);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        Vec3 c = position();
        int s = state();
        switch (s) {
            case EMERGE -> { if (stateAge >= EMERGE_T) setState(OPEN); }
            case OPEN -> {
                // Suga para o centro
                for (LivingEntity le : foes(c.add(0, 1, 0), radius + 2.5)) {
                    Vec3 to = c.subtract(le.position()).multiply(1, 0, 1);
                    double d = to.length();
                    if (d < 0.2) continue;
                    double k = FdUtil.isBoss(le) ? 0.04 : 0.14;
                    FdUtil.setMotion(le, le.getDeltaMovement().multiply(0.7, 1, 0.7).add(to.scale(k / d * (1 + d * 0.3))));
                }
                if (stateAge % 4 == 0) FdFx.implode(sl, c.add(0, 0.6, 0), ParticleTypes.BUBBLE_POP, radius + 2, 20, 0.3);
                if (stateAge >= OPEN_T) bite(sl, c);
            }
            case BITE -> { if (stateAge >= BITE_T) setState(CHEW); holdSwallowed(sl, c); }
            case CHEW -> {
                holdSwallowed(sl, c);
                if (stateAge % 10 == 0) {
                    for (LivingEntity le : swallowedLiving(sl)) {
                        hit(le, damage, null);
                        Statuses.apply(le, Statuses.Kind.CORRUPTED, 1, 80, owner(), 1F);
                    }
                    FdFx.sound(sl, c, SoundEvents.GENERIC_EAT, 1.6F, 0.4F);
                    FdFx.sound(sl, c, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.7F, 0.5F);
                    sl.sendParticles(Vfx.ink(), c.x, c.y + 1.4, c.z, 10, 0.8, 0.3, 0.8, 0.05);
                }
                if (stateAge >= chewTicks) spit(sl, c);
            }
            case SPIT -> { if (stateAge >= SPIT_T) setState(SINK); }
            default -> {}
        }
    }

    private void bite(ServerLevel sl, Vec3 c) {
        setState(BITE);
        for (LivingEntity le : foes(c.add(0, 0.8, 0), radius)) {
            if (FdUtil.isBoss(le)) {
                hit(le, damage * 3F, null);
                continue;
            }
            hit(le, damage * 1.5F, null);
            if (le.isAlive()) swallowed.add(le.getUUID());
        }
        FdFx.sound(sl, c, SoundEvents.EVOKER_FANGS_ATTACK, 2F, 0.4F);
        FdFx.sound(sl, c, SoundEvents.RAVAGER_ATTACK, 1.6F, 0.5F);
        sl.sendParticles(Vfx.splash(), c.x, c.y + 2, c.z, 40, 1.2, 0.6, 1.2, 0.3);
        BossKit.shake(sl, c, 16, 0.9F, 8);
    }

    private List<LivingEntity> swallowedLiving(ServerLevel sl) {
        List<LivingEntity> out = new ArrayList<>();
        swallowed.removeIf(id -> !(sl.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
        for (UUID id : swallowed) if (sl.getEntity(id) instanceof LivingEntity le) out.add(le);
        return out;
    }

    /** Engolidos ficam presos dentro da boca, invisíveis (e cegos, se forem jogadores). */
    private void holdSwallowed(ServerLevel sl, Vec3 c) {
        for (LivingEntity le : swallowedLiving(sl)) {
            Vec3 spot = c.add(0, 0.4, 0);
            FdUtil.setMotion(le, spot.subtract(le.position()).scale(0.6));
            le.resetFallDistance();
            le.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 12, 0, false, false, false));
            if (le instanceof ServerPlayer) le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, false));
            if (le instanceof Mob m) {
                m.getNavigation().stop();
                m.setTarget(null);
            }
        }
    }

    private void spit(ServerLevel sl, Vec3 c) {
        setState(SPIT);
        for (LivingEntity le : swallowedLiving(sl)) {
            le.removeEffect(MobEffects.INVISIBILITY);
            hit(le, damage * 1.5F, null);
            double a = random.nextDouble() * Math.PI * 2;
            // teleportTo (e não setPos): no jogador, setPos não chega ao cliente
            le.teleportTo(c.x, c.y + 2.2, c.z);
            FdUtil.setMotion(le, new Vec3(Math.cos(a) * 1.3, 1.25, Math.sin(a) * 1.3));
        }
        swallowed.clear();
        sl.sendParticles(Vfx.splash(), c.x, c.y + 2, c.z, 80, 0.8, 1, 0.8, 0.6);
        sl.sendParticles(Vfx.ink(), c.x, c.y + 2, c.z, 30, 0.6, 0.6, 0.6, 0.2);
        FdFx.sound(sl, c, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2F, 0.5F);
        FdFx.sound(sl, c, SoundEvents.RAVAGER_STUNNED, 1.4F, 0.5F);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        // Fim antes da hora (descarregou, limite): ninguém fica preso invisível
        for (LivingEntity le : swallowedLiving(sl)) {
            le.removeEffect(MobEffects.INVISIBILITY);
            FdUtil.setMotion(le, new Vec3(0, 0.8, 0));
        }
        swallowed.clear();
        sl.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 0.3, getZ(), 30, 1.2, 0.2, 1.2, 0.05);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl && !swallowed.isEmpty()) {
            for (LivingEntity le : swallowedLiving(sl)) le.removeEffect(MobEffects.INVISIBILITY);
            swallowed.clear();
        }
        super.remove(reason);
    }

    @Override
    protected void clientTick() {
        if (state() <= OPEN && random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = 1 + random.nextDouble() * 2;
            level().addParticle(ParticleTypes.BUBBLE_POP, getX() + Math.cos(a) * r, getY() + 0.3, getZ() + Math.sin(a) * r, 0, 0.05, 0);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, st -> st.setAndContinue(switch (state()) {
            case EMERGE -> A_EMERGE;
            case OPEN -> A_OPEN;
            case BITE, CHEW -> A_BITE;
            case SPIT -> A_SPIT;
            default -> A_SINK;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
