package com.renanpontes.isekaimahou.feature.forms_b.entity;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

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

/**
 * Orbe da forma Órbita: gira em volta do conjurador (raio {@link #radius()}) e, ao tocar uma
 * ameaça, aplica os efeitos e se desfaz. A posição é calculada igual no cliente e no servidor
 * (dono + ângulo pelo tempo do mundo), então o giro fica suave.
 */
public class SpellOrbEntity extends SpellFormEntity {
    private static final EntityDataAccessor<Integer> DATA_OWNER_NET = SynchedEntityData.defineId(SpellOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PHASE = SynchedEntityData.defineId(SpellOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(SpellOrbEntity.class, EntityDataSerializers.FLOAT);

    /** Os efeitos só curam/protegem: o orbe toca aliados em vez de inimigos. */
    private boolean supportOrb;

    public SpellOrbEntity(EntityType<? extends SpellOrbEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_NET, -1);
        builder.define(DATA_PHASE, 0F);
        builder.define(DATA_SPEED, 0.12F);
    }

    public void setOrbit(Entity owner, float phase, float speed, boolean support) {
        entityData.set(DATA_OWNER_NET, owner.getId());
        entityData.set(DATA_PHASE, phase);
        entityData.set(DATA_SPEED, speed);
        this.supportOrb = support;
        followOwner(owner);
        setOldPosAndRot();
    }

    public Entity ownerEntity() {
        int id = entityData.get(DATA_OWNER_NET);
        return id >= 0 ? level().getEntity(id) : null;
    }

    /** Coloca o orbe no ponto da órbita (mesma conta no cliente e no servidor). */
    private void followOwner(Entity owner) {
        double a = entityData.get(DATA_PHASE) + level().getGameTime() * entityData.get(DATA_SPEED);
        double r = radius();
        double bob = Math.sin(level().getGameTime() * 0.15 + entityData.get(DATA_PHASE) * 3) * 0.15;
        setPos(owner.getX() + Math.cos(a) * r, owner.getY() + owner.getBbHeight() * 0.6 + bob, owner.getZ() + Math.sin(a) * r);
    }

    @Override
    public void tick() {
        Entity owner = ownerEntity();
        if (owner != null) followOwner(owner);
        super.tick();
    }

    /** Cliente: com o dono à vista a posição é calculada aqui; ignorar a do servidor evita "tremidas". */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (level().isClientSide && ownerEntity() != null) return;
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive() || caster.level() != level) {
            discard();
            return;
        }
        // Dono mudou de objeto (renasceu) ou ainda não estava ligado
        if (entityData.get(DATA_OWNER_NET) != caster.getId()) {
            entityData.set(DATA_OWNER_NET, caster.getId());
            followOwner(caster);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.35),
                e -> e != caster && e.isAlive() && !e.isSpectator())) {
            boolean ok = supportOrb ? ctx.isAlly(e) : FormsBUtil.isThreat(ctx, e);
            if (!ok) continue;
            ctx.resolveOnEntity(e, this);
            ElementFx.burst(level, position(), element(), 12, 0.2);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
            discard();
            return;
        }
        if (age % 30 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.25F, 1.8F);
        }
    }

    @Override
    protected void onExpire(ServerLevel level, SpellContext ctx) {
        level.sendParticles(FormsBUtil.dust(color(), 0.8F), getX(), getY(), getZ(), 8, 0.15, 0.15, 0.15, 0);
        // Orbe que acabou sem tocar ninguém: Ao Terminar nasce aqui (tocar alguém já é Ao Acertar, pelo núcleo)
        stageEnd(ctx, position());
    }

    @Override
    protected void clientTick() {
        if (FormsBUtil.reducedParticles() && age % 3 != 0) return;
        level().addParticle(FormsBUtil.dust(color(), 0.7F), xo, yo, zo, 0, 0, 0);
        if (random.nextInt(5) == 0) level().addParticle(ElementFx.signature(elementFor(random.nextInt(2))), getX(), getY(), getZ(), 0, 0.01, 0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 64 * 64; }

    @Override
    protected void readExtra(CompoundTag tag) {
        entityData.set(DATA_PHASE, tag.getFloat("Phase"));
        entityData.set(DATA_SPEED, tag.contains("Speed") ? tag.getFloat("Speed") : 0.12F);
        supportOrb = tag.getBoolean("Support");
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putFloat("Phase", entityData.get(DATA_PHASE));
        tag.putFloat("Speed", entityData.get(DATA_SPEED));
        tag.putBoolean("Support", supportOrb);
    }
}
