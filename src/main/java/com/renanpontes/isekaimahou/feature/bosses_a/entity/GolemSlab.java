package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;
import com.renanpontes.isekaimahou.feature.bosses_a.BossesARegistry;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Placa de terra do Arremesso de Placa (Golem). Fica erguida sobre a cabeça do Golem enquanto ele mira e depois
 * voa baixa e em linha reta, girando como um disco, pela linha telegrafada: atinge quem estiver no caminho
 * (a área de acerto é exatamente a faixa desenhada) e se despedaça no fim. Não quebra blocos; não é salva.
 */
public class GolemSlab extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(GolemSlab.class, EntityDataSerializers.BOOLEAN);
    /** Largura da faixa de acerto (a mesma da telegrafia). */
    public static final float WIDTH = 3.0F;
    /** Tempo máximo segurada antes de ser arremessada (se o Golem for interrompido, ela se desfaz). */
    private static final int MAX_HELD = 40;

    @Nullable private UUID ownerId;
    private Vec3 lineFrom = Vec3.ZERO;
    private Vec3 lineTo = Vec3.ZERO;
    private Vec3 airFrom = Vec3.ZERO;
    private Vec3 airTo = Vec3.ZERO;
    private int flightTicks;
    private int flown;
    private int held;
    private float damage = 12F;
    private final Set<UUID> hit = new HashSet<>();

    public GolemSlab(EntityType<? extends GolemSlab> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Arranca a placa do chão (fica segura sobre o Golem até {@link #launch}). */
    public static GolemSlab rip(LivingEntity owner, Vec3 pos) {
        GolemSlab s = new GolemSlab(BossesARegistry.GOLEM_SLAB.get(), owner.level());
        s.ownerId = owner.getUUID();
        s.setPos(pos.x, pos.y, pos.z);
        s.setYRot(owner.getYRot());
        owner.level().addFreshEntity(s);
        return s;
    }

    public boolean isFlying() { return entityData.get(DATA_FLYING); }

    /** Segue as mãos do Golem enquanto ele mira. */
    public void holdAt(Vec3 pos, float yaw) {
        if (isFlying()) return;
        setPos(pos.x, pos.y, pos.z);
        setYRot(yaw);
    }

    /**
     * Arremessa ao longo da linha do chão {@code from → to} (a telegrafada), voando a ~1 bloco do chão.
     * @param speed blocos por tick
     */
    public void launch(Vec3 from, Vec3 to, float speed, float damage) {
        this.lineFrom = from;
        this.lineTo = to;
        this.airFrom = position();
        this.airTo = to.add(0, 0.6, 0);
        this.flightTicks = Math.max(1, (int) Math.ceil(from.distanceTo(to) / speed));
        this.flown = 0;
        this.damage = damage;
        entityData.set(DATA_FLYING, true);
        setDeltaMovement(airTo.subtract(airFrom).scale(1.0 / flightTicks));
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.5F, 0.4F);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FLYING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (isFlying()) {
            Vec3 v = getDeltaMovement();
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        }
        if (level().isClientSide) {
            if (random.nextInt(isFlying() ? 1 : 3) == 0) {
                level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.DIRT.defaultBlockState()),
                        getX() + (random.nextDouble() - 0.5) * 2.0, getY() + 0.1, getZ() + (random.nextDouble() - 0.5) * 2.0, 0, 0, 0);
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity ownerEntity = ownerId != null ? sl.getEntity(ownerId) : null;
        if (!(ownerEntity instanceof LivingEntity owner) || !owner.isAlive()) {
            shatter(sl, false);
            return;
        }
        if (!isFlying()) {
            if (++held > MAX_HELD) shatter(sl, false);
            return;
        }
        flown++;
        // Varre o trecho da faixa percorrido neste tick (o desenho no chão é a área de acerto)
        double f0 = (flown - 1) / (double) flightTicks, f1 = Math.min(1.0, flown / (double) flightTicks);
        Telegraph.Shape swept = Telegraph.Shape.line(lineFrom.lerp(lineTo, f0), lineFrom.lerp(lineTo, f1), WIDTH);
        Vec3 dir = BossSpells.flatDir(lineFrom, lineTo, Vec3.ZERO);
        for (LivingEntity e : Telegraph.entitiesIn(sl, swept)) {
            if (!BossSpells.isFoe(owner, e) || !hit.add(e.getUUID())) continue;
            BossSpells.hit(owner, e, Element.EARTH, damage);
            BossSpells.knock(e, dir.add(e.position().subtract(position()).normalize().scale(0.4)), 1.6, 0.55);
            sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8F, 0.6F);
        }
        if (tickCount % 4 == 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 0.4F, 0.5F);
        if (flown >= flightTicks) shatter(sl, true);
    }

    private void shatter(ServerLevel sl, boolean impact) {
        if (isRemoved()) return;
        Vec3 c = position().add(0, 0.4, 0);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_COBBLESTONE.defaultBlockState()),
                c.x, c.y, c.z, impact ? 70 : 35, 1.0, 0.4, 1.0, 0.25);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()),
                c.x, c.y, c.z, impact ? 40 : 20, 1.0, 0.3, 1.0, 0.2);
        sl.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 10, 0.8, 0.3, 0.8, 0.03);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
        if (impact) {
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4F, 0.7F);
            BossKit.shake(sl, c, 14, 0.8F, 10);
        }
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
