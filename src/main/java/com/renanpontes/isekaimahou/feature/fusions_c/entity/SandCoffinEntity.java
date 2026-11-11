package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Casulo de areia em volta de um alvo.
 * <ul>
 *   <li>CAIXÃO (Caixão de Areia): a areia sobe em espiral e fecha o alvo ({@link #WRAP} ticks), ergue-o do chão e o
 *       aperta (Enraizado, cego, dano a cada segundo); no fim o "Funeral": o casulo implode esmagando (dano alto).</li>
 *   <li>SOTERRAR (fim do Tsunami de Areia): um monte de areia cobre o alvo — preso, cego e sufocando por alguns segundos.</li>
 * </ul>
 */
public class SandCoffinEntity extends FcSpellEntity {
    public static final int WRAP = 16;
    public static final int FUNERAL = 10;
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(SandCoffinEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BURIAL = SynchedEntityData.defineId(SandCoffinEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(SandCoffinEntity.class, EntityDataSerializers.FLOAT);

    private float crushDamage = 14F;
    @Nullable
    private Vec3 holdAt;

    public SandCoffinEntity(EntityType<? extends SandCoffinEntity> type, Level level) {
        super(type, level);
    }

    /** Teto de casulos vivos por conjurador (Zona/Aura que repetem não prendem o mapa inteiro). */
    private static final int MAX_PER_OWNER = 16;

    public static void wrap(SpellContext ctx, SpellStats stats, LivingEntity target, boolean burial, int holdTicks, float crushDamage) {
        ServerLevel sl = ctx.level();
        if (FcUtil.owned(sl, FcRegistries.SAND_COFFIN.get(), ctx.casterId()) >= MAX_PER_OWNER) return;
        for (SandCoffinEntity old : sl.getEntities(FcRegistries.SAND_COFFIN.get(), c -> c.isAlive() && c.targetId() == target.getId())) {
            // Já está num Caixão (que vai esmagar): soterrar por cima não cancela o Funeral
            if (burial && !old.burial()) return;
            old.discard();
        }
        boolean boss = Statuses.isBossLike(target);
        int hold = boss ? Math.min(holdTicks, 30) : holdTicks;
        SandCoffinEntity c = new SandCoffinEntity(FcRegistries.SAND_COFFIN.get(), sl);
        c.setPos(target.getX(), target.getY(), target.getZ());
        c.setup(ctx, stats, Element.SAND, burial ? hold : WRAP + hold + FUNERAL, Math.max(0.55F, target.getBbWidth() * 0.5F + 0.3F));
        c.entityData.set(TARGET, target.getId());
        c.entityData.set(BURIAL, burial);
        c.entityData.set(HEIGHT, target.getBbHeight() + 0.35F);
        c.crushDamage = crushDamage;
        sl.addFreshEntity(c);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()),
                target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 30, target.getBbWidth(), 0.3, target.getBbWidth(), 0);
        FcFx.sound(sl, target.position(), SoundEvents.SAND_BREAK, 1.6F, 0.6F);
        FcFx.sound(sl, target.position(), SoundEvents.SAND_FALL, 1.6F, 0.5F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET, -1);
        builder.define(BURIAL, false);
        builder.define(HEIGHT, 2F);
    }

    public int targetId() { return entityData.get(TARGET); }

    public boolean burial() { return entityData.get(BURIAL); }

    public float coffinHeight() { return entityData.get(HEIGHT); }

    /** 0→1 enquanto a areia sobe e fecha (no Soterrar fecha rápido). */
    public float closed(float pt) {
        float t = (age() + pt) / (burial() ? 6F : WRAP);
        return Math.min(1F, t);
    }

    /** 0 durante o aperto; sobe até 1 durante a implosão do Funeral. */
    public float crush(float pt) {
        if (burial()) return 0F;
        return Math.max(0F, Math.min(1F, 1F - (lifeLeft() - pt) / FUNERAL));
    }

    @Nullable
    private LivingEntity target() {
        Entity e = level().getEntity(targetId());
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        LivingEntity t = target();
        if (t == null) {
            discard();
            return;
        }
        int age = age();
        boolean burial = burial();
        if (holdAt == null) holdAt = burial ? t.position() : t.position().add(0, 0.05, 0);
        // Caixão: depois de fechar, ergue o preso devagar até ~1,2 bloco do chão
        Vec3 want = burial ? holdAt : holdAt.add(0, Math.min(1.2, Math.max(0, (age - WRAP) * 0.08)), 0);
        if (!FcUtil.untouchable(t) && !Statuses.isBossLike(t)) {
            if (t.position().distanceToSqr(want) > 0.04) t.teleportTo(want.x, want.y, want.z);
            t.setDeltaMovement(Vec3.ZERO);
            t.hurtMarked = true;
            t.resetFallDistance();
            FcUtil.stopMob(t);
        }
        setPos(t.getX(), t.getY(), t.getZ());
        if (age >= (burial ? 4 : WRAP / 2) && age % 10 == 0) {
            Statuses.apply(t, Statuses.Kind.ROOTED, 1, 30, owner(), power);
            t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), owner());
        }
        // Aperto (Caixão) ou sufocar (Soterrar) a cada segundo
        if (age > (burial ? 0 : WRAP) && age % 20 == 0 && lifeLeft() > FUNERAL) {
            hit(t, burial ? 2F : 3F);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                    t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 12, t.getBbWidth() * 0.5, t.getBbHeight() * 0.4, t.getBbWidth() * 0.5, 0.05);
            FcFx.sound(sl, t.position(), SoundEvents.SAND_STEP, 1.2F, 0.6F);
        }
        if (!burial && lifeLeft() == FUNERAL) FcFx.sound(sl, t.position(), SoundEvents.SAND_FALL, 2F, 0.4F);
        if (age % 3 == 0) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()),
                    getX(), getY() + coffinHeight(), getZ(), 2, scale(), 0.1, scale(), 0);
        }
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        LivingEntity t = target();
        Vec3 c = position().add(0, coffinHeight() * 0.5, 0);
        if (!burial() && t != null) {
            // Funeral do Deserto: a areia implode
            hit(t, crushDamage);
            if (t.isAlive()) Statuses.apply(t, Statuses.Kind.PETRIFIED, 1, 100, owner(), power);
            BossKit.shake(sl, c, 16, 0.8F, 10);
            FcFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.6F);
            FcFx.sound(sl, c, SoundEvents.BONE_BLOCK_BREAK, 1.4F, 0.5F);
        }
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                c.x, c.y, c.z, 70, scale() + 0.4, coffinHeight() * 0.4, scale() + 0.4, 0.3);
        sl.sendParticles(ParticleTypes.DUST_PLUME, c.x, c.y, c.z, 20, scale(), coffinHeight() * 0.3, scale(), 0.05);
        FcFx.sound(sl, c, SoundEvents.SAND_BREAK, 2F, 0.5F);
    }

    @Override
    protected void clientTick() {
        if (age() < WRAP && !burial()) {
            double a = age() * 0.9 + random.nextDouble();
            double r = scale() + 0.4;
            level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()),
                    getX() + Math.cos(a) * r, getY() + random.nextDouble() * coffinHeight(), getZ() + Math.sin(a) * r, 0, 0, 0);
        }
    }
}
