package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcNet;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tempestade de Pólen: nuvem rodopiante de pólen e pétalas. A cada segundo os inimigos dentro ficam envenenados e
 * mais sonolentos (Sonolência I→III); no III adormecem. Aliados dentro recebem uma regeneração leve.
 * O pólen é INFLAMÁVEL: se um inimigo Em Chamas (ou fogo no chão) estiver na nuvem, ela explode inteira (explosão de
 * pólen: dano alto, Em Chamas 2) — combo natural com magias de Fogo/Magma/Sol.
 */
public class PollenCloudEntity extends FcSpellEntity {
    private static final int MAX_PER_OWNER = 2;
    private static final ResourceKey<MobEffect> SPORE_SLEEP = ResourceKey.create(Registries.MOB_EFFECT, IsekaiMahou.id("spore_sleep"));

    private boolean ignited;

    public PollenCloudEntity(EntityType<? extends PollenCloudEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(SpellContext ctx, SpellStats stats, Vec3 ground) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            List<PollenCloudEntity> mine = new ArrayList<>(sl.getEntities(FcRegistries.POLLEN_CLOUD.get(), c -> c.isAlive() && owner.equals(c.ownerId())));
            // Zona/Aura repetem o efeito a cada pulso com a MESMA conjuração: se a nuvem dela ainda cobre o ponto, só segue
            // (sem trocar de nuvem a cada segundo)
            float r = stats.radius(5F);
            for (PollenCloudEntity old : mine) {
                if (old.ctx == ctx && !old.ignited && old.position().distanceToSqr(ground) < r * r * 0.25) return;
            }
            while (mine.size() >= MAX_PER_OWNER) mine.remove(0).discard();
        }
        PollenCloudEntity c = new PollenCloudEntity(FcRegistries.POLLEN_CLOUD.get(), sl);
        c.setPos(ground.x, ground.y, ground.z);
        c.setup(ctx, stats, Element.NATURE, stats.duration(160), stats.radius(5F));
        sl.addFreshEntity(c);
        FcFx.sound(sl, ground, SoundEvents.SPORE_BLOSSOM_BREAK, 1.6F, 0.6F);
        FcFx.sound(sl, ground, SoundEvents.AZALEA_LEAVES_BREAK, 1.4F, 0.6F);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        if (ignited) return;
        float r = scale();
        Vec3 c = position().add(0, 1.2, 0);
        int age = age();
        if (age % 20 == 10) {
            for (LivingEntity le : enemiesIn(c, r)) {
                // Fogo no meio do pólen: explosão!
                if (Statuses.has(le, Statuses.Kind.BURNING)) {
                    ignite(sl);
                    return;
                }
                le.addEffect(new MobEffectInstance(MobEffects.POISON, 60, power >= 1.4F ? 1 : 0), owner());
                drowse(le);
            }
            for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                    le -> le.isAlive() && ally(le) && le.distanceToSqr(c) <= r * r)) {
                le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0), owner());
            }
        }
        // Fogo no chão dentro da nuvem também detona (checado devagar: poucos blocos sorteados)
        if (age % 10 == 0) {
            for (int i = 0; i < 6; i++) {
                BlockPos p = BlockPos.containing(position().add((random.nextDouble() - 0.5) * 2 * r, random.nextInt(3), (random.nextDouble() - 0.5) * 2 * r));
                if (sl.getBlockState(p).is(BlockTags.FIRE) || sl.getBlockState(p).is(net.minecraft.world.level.block.Blocks.LAVA)) {
                    ignite(sl);
                    return;
                }
            }
        }
        if (age % 4 == 0) {
            sl.sendParticles(Vfx.sporeAir(), c.x, c.y, c.z, 4, r * 0.6, 1.0, r * 0.6, 0.0);
            sl.sendParticles(FcFx.dust(age % 8 == 0 ? FcFx.POLLEN : FcFx.POLLEN_PINK, 1.3F), c.x, c.y, c.z, 3, r * 0.6, 1.0, r * 0.6, 0.0);
        }
        if (age % 40 == 0) FcFx.sound(sl, c, SoundEvents.BEEHIVE_WORK, 0.6F, 0.6F);
    }

    /** Sonolência sobe um nível; no III, adormece (chefes só ficam lentos). */
    private void drowse(LivingEntity le) {
        var drowsy = FcRegistries.POLLEN_DROWSY;
        MobEffectInstance cur = le.getEffect(drowsy);
        int lvl = cur == null ? 0 : Math.min(2, cur.getAmplifier() + 1);
        le.addEffect(new MobEffectInstance(drowsy, 80, lvl), owner());
        if (cur != null && cur.getAmplifier() >= 2 && !Statuses.isBossLike(le)) {
            le.removeEffect(drowsy);
            Holder<MobEffect> sleep = BuiltInRegistries.MOB_EFFECT.getHolder(SPORE_SLEEP).map(h -> (Holder<MobEffect>) h).orElse(null);
            if (sleep != null) le.addEffect(new MobEffectInstance(sleep, 100, 0), owner());
            else Statuses.apply(le, Statuses.Kind.STUNNED, 1, 60, owner(), power);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(Vfx.sporeFall(), le.getX(), le.getY() + le.getBbHeight() + 0.4, le.getZ(), 8, 0.3, 0.1, 0.3, 0);
                FcFx.sound(sl, le.position(), SoundEvents.FOX_SLEEP, 1F, 0.8F);
            }
        }
    }

    /** Explosão de pólen: tudo dentro da nuvem queima de uma vez. */
    private void ignite(ServerLevel sl) {
        ignited = true;
        float r = scale();
        Vec3 c = position().add(0, 1, 0);
        for (LivingEntity le : enemiesIn(c, r + 0.5)) {
            hit(le, 12F, Element.FIRE);
            Statuses.applyUpTo(le, Statuses.Kind.BURNING, 1, 120, owner(), power, 2);
            Vec3 out = FcUtil.flat(le.position().subtract(c));
            FcUtil.knock(le, new Vec3(out.x * 0.6, 0.5, out.z * 0.6));
        }
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        int n = Mth.clamp((int) (r * r * 3), 30, 140);
        sl.sendParticles(Vfx.flame(), c.x, c.y, c.z, n, r * 0.5, 1.0, r * 0.5, 0.12);
        sl.sendParticles(FcFx.fade(FcFx.POLLEN, 0xFF6020, 2F), c.x, c.y, c.z, n / 2, r * 0.5, 1.0, r * 0.5, 0);
        FusionShockwaveEntity.visual(sl, position().add(0, 0.2, 0), FusionShockwaveEntity.Kind.POLLEN, r + 2, 14);
        FcFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 2F, 0.9F);
        FcFx.sound(sl, c, SoundEvents.FIRECHARGE_USE, 2F, 0.6F);
        BossKit.shake(sl, c, 20, 1F, 14);
        FcNet.flash(sl, c, r + 8, 0xFFB040, 0.3F, 10);
        discard();
    }

    @Override
    protected void clientTick() {
        float r = scale();
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
            double x = getX() + Math.cos(a) * d, z = getZ() + Math.sin(a) * d;
            level().addParticle(i == 0 ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.SPORE_BLOSSOM_AIR, x, getY() + 0.3 + random.nextDouble() * 2.6, z,
                    -Math.sin(a) * 0.08, 0.01, Math.cos(a) * 0.08);
        }
    }
}
