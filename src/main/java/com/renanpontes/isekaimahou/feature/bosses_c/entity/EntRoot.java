package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_c.BossesCRegistry;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
 * Raiz corrompida do Ent: o chão treme e fica MARCADO (telegrafia do núcleo, do tamanho exato da área de acerto),
 * depois as raízes irrompem, ferem e ENRAÍZAM quem ainda estiver em cima. Recolhem-se sozinhas.
 * Com dano 0 é só visual (as raízes da Linha de Raízes, cujo golpe é a própria marca em linha).
 * Temporária: não é salva no mundo.
 */
public class EntRoot extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(EntRoot.class, EntityDataSerializers.INT);
    private static final RawAnimation EMERGE = RawAnimation.begin().thenLoop("animation.corrupted_ent_root.emerge");
    private static final RawAnimation ERUPT = RawAnimation.begin().thenPlayAndHold("animation.corrupted_ent_root.erupt");
    private static final RawAnimation RETRACT = RawAnimation.begin().thenPlayAndHold("animation.corrupted_ent_root.retract");
    private static final int WARNING = 0, ERUPTED = 1, RETRACTING = 2;
    /** Raio da área de acerto (e da marca no chão). */
    public static final float RADIUS = 1.0F;
    public static final int MARK_COLOR = 0xA34BD8;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int delay = 20;
    private float damage = 6.0F;
    private int rootTicks = 60;
    private int age;
    private int stateAge;

    public EntRoot(EntityType<? extends EntRoot> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Planta uma raiz em {@code pos} (no chão), com a marca no chão durante os {@code delay} ticks de aviso. */
    public static void spawn(ServerLevel level, LivingEntity owner, Vec3 pos, int delay, float damage, int rootTicks) {
        EntRoot r = new EntRoot(BossesCRegistry.ENT_ROOT.get(), level);
        r.ownerId = owner.getUUID();
        r.delay = delay;
        r.damage = damage;
        r.rootTicks = rootTicks;
        r.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360F, 0F);
        level.addFreshEntity(r);
        if (damage > 0 && delay > 0) {
            Telegraph.show(level, Telegraph.Shape.circle(pos, RADIUS).warmup(delay).color(MARK_COLOR).owner(owner).noRunes());
        }
    }

    /** Raiz só visual que irrompe na hora (o dano vem de outra marca). */
    public static void spawnVisual(ServerLevel level, LivingEntity owner, Vec3 pos) {
        spawn(level, owner, pos, 0, 0F, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, WARNING);
    }

    private int state() {
        return entityData.get(DATA_STATE);
    }

    private void setState(int s) {
        entityData.set(DATA_STATE, s);
        stateAge = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        age++;
        stateAge++;
        switch (state()) {
            case WARNING -> {
                if (age % 3 == 0) {
                    BlockState ground = sl.getBlockState(blockPosition().below());
                    if (ground.isAir()) ground = Blocks.DIRT.defaultBlockState();
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(), 6, 0.4, 0.05, 0.4, 0.1);
                    sl.sendParticles(BossFx.dust(0x7A2F9A, 1.0F), getX(), getY() + 0.1, getZ(), 2, 0.4, 0.05, 0.4, 0);
                }
                if (age >= delay) erupt(sl);
            }
            case ERUPTED -> {
                if (stateAge >= 40) {
                    setState(RETRACTING);
                    sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 0.8F, 0.7F);
                }
            }
            default -> {
                if (stateAge >= 14) discard();
            }
        }
    }

    private void erupt(ServerLevel sl) {
        setState(ERUPTED);
        Entity owner = ownerId == null ? null : sl.getEntity(ownerId);
        // Ent morto: a marca no chão já sumiu junto com ele, então a raiz só aparece (sem dano surpresa).
        if (damage > 0 && owner instanceof LivingEntity ent && ent.isAlive()) {
            DamageSource src = damageSources().mobAttack(ent);
            // Mesma área da marca: quem saiu do círculo a tempo escapa.
            for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(position(), RADIUS).vertical(0.6, 1.8))) {
                if (!e.isAlive() || BossAllies.isAlly(owner, e)) continue;
                if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                if (e.hurt(src, damage)) {
                    if (rootTicks > 0) e.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, rootTicks, 0), owner);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.35, 0));
                    e.hurtMarked = true;
                }
            }
        }
        BlockState ground = sl.getBlockState(blockPosition().below());
        if (ground.isAir()) ground = Blocks.DIRT.defaultBlockState();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.3, getZ(), 30, 0.5, 0.3, 0.5, 0.25);
        sl.sendParticles(BossFx.dust(0xC66BFF, 1.3F), getX(), getY() + 1.0, getZ(), 12, 0.3, 0.6, 0.3, 0);
        // As raízes só visuais vêm em fileira: som mais baixo para não ensurdecer.
        float vol = damage > 0 ? 1.5F : 0.6F;
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTS_BREAK, SoundSource.HOSTILE, vol, 0.6F);
        if (damage > 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.8F, 0.5F);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        delay = tag.getInt("Delay");
        damage = tag.getFloat("Damage");
        rootTicks = tag.getInt("RootTicks");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Delay", delay);
        tag.putFloat("Damage", damage);
        tag.putInt("RootTicks", rootTicks);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(switch (state()) {
            case WARNING -> EMERGE;
            case ERUPTED -> ERUPT;
            default -> RETRACT;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
