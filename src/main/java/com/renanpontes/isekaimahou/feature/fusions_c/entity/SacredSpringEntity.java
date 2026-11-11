package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.feature.fusions_c.glyph.FcCleansing;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Fonte Sagrada (modelo GeckoLib): uma fonte de mármore com um lótus de luz brota do chão e jorra água benta.
 * A cada segundo: cura os aliados no raio e tira deles UMA condição ruim (a pior primeiro); inimigos ficam Molhados
 * (ótimo para combinar com Raio/Gelo) e mortos-vivos queimam com a água benta.
 */
public class SacredSpringEntity extends FcSpellEntity implements GeoEntity {
    private static final RawAnimation RISE = RawAnimation.begin().thenPlay("animation.sacred_spring_fountain.rise");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.sacred_spring_fountain.idle");
    private static final RawAnimation SINK = RawAnimation.begin().thenPlay("animation.sacred_spring_fountain.sink");
    public static final int RISE_TICKS = 16;
    public static final int SINK_TICKS = 16;
    private static final int MAX_PER_OWNER = 1;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float heal = 2.5F;

    public SacredSpringEntity(EntityType<? extends SacredSpringEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(SpellContext ctx, SpellStats stats, Vec3 ground) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            List<SacredSpringEntity> mine = new ArrayList<>(sl.getEntities(FcRegistries.SACRED_SPRING.get(), s -> s.isAlive() && owner.equals(s.ownerId())));
            // Zona/Aura repetem o efeito a cada pulso com a MESMA conjuração: a fonte dela continua (senão brotaria de novo
            // a cada segundo, sem nunca terminar de subir). Uma conjuração NOVA troca a fonte.
            for (SacredSpringEntity s : mine) if (s.ctx == ctx) return;
            while (mine.size() >= MAX_PER_OWNER) mine.remove(0).discard();
        }
        SacredSpringEntity s = new SacredSpringEntity(FcRegistries.SACRED_SPRING.get(), sl);
        s.setPos(ground.x, ground.y, ground.z);
        s.setYRot(sl.random.nextFloat() * 360F);
        s.setup(ctx, stats, Element.PURITY, stats.duration(240), stats.radius(5F));
        s.heal = 2.5F;
        sl.addFreshEntity(s);
        FcFx.sound(sl, ground, SoundEvents.CONDUIT_ACTIVATE, 1.6F, 1.2F);
        FcFx.sound(sl, ground, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.4F, 1F);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        int age = age();
        if (age < RISE_TICKS) return;
        float r = scale();
        Vec3 c = position();
        if ((age - RISE_TICKS) % 20 == 0) {
            int cleansed = 0;
            for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 4, r),
                    le -> le.isAlive() && le.distanceToSqr(c) <= r * r + 9)) {
                if (ally(le)) {
                    le.heal(heal * power * (ctx != null ? ctx.powerMult() * ctx.casterSpellPower() : 1F));
                    if (FcCleansing.cleanseWorst(le)) cleansed++;
                    sl.sendParticles(ParticleTypes.HEART, le.getX(), le.getY() + le.getBbHeight() + 0.3, le.getZ(), 1, 0.2, 0.1, 0.2, 0);
                } else if (!FcUtil.untouchable(le)) {
                    Statuses.apply(le, Statuses.Kind.WET, 1, 100, owner(), power);
                    if (FcUtil.undead(le)) {
                        hit(le, 3F, Element.PURITY);
                        sl.sendParticles(ParticleTypes.END_ROD, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
                    }
                }
            }
            // Pulso: anel de água benta no chão
            FcFx.ring(sl, c.add(0, 0.15, 0), FcFx.fade(FcFx.HOLY, FcFx.PURITY, 1.3F), r, Math.min(64, (int) (r * 8)), 0);
            sl.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 2.2, c.z, 20, 0.6, 0.2, 0.6, 0.2);
            FcFx.sound(sl, c, cleansed > 0 ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.2F, 1.2F);
        }
        if (age % 4 == 0) {
            sl.sendParticles(ParticleTypes.FALLING_WATER, c.x, c.y + 2.6, c.z, 3, 0.5, 0.1, 0.5, 0);
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.2, c.z, 1, r * 0.4, 0.6, r * 0.4, 0.01);
        }
    }

    @Override
    protected void clientTick() {
        if (age() < RISE_TICKS) return;
        // Coluna de luz fina sobre a fonte
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 2.4, getZ() + (random.nextDouble() - 0.5) * 0.4,
                    0, 0.12, 0);
        }
        level().addParticle(ParticleTypes.SPLASH, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 0.9, getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0.1, 0);
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            if (age() < RISE_TICKS) return state.setAndContinue(RISE);
            if (lifeLeft() <= SINK_TICKS) return state.setAndContinue(SINK);
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
