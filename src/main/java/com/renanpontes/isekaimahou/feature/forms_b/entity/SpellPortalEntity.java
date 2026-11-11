package com.renanpontes.isekaimahou.feature.forms_b.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Portal: um de dois anéis ligados (entrada e saída). Quem entra num é levado ao outro e recebe os
 * efeitos da magia. Anti ping-pong: quem chega (ou já estava dentro quando abriu) é ignorado até sair
 * do anel, e há uma recarga curta por entidade.
 */
public class SpellPortalEntity extends SpellFormEntity {
    private static final EntityDataAccessor<Boolean> DATA_EXIT = SynchedEntityData.defineId(SpellPortalEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(SpellPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final String COOLDOWN_KEY = "isekai_mahou.forms_b_portal_cd";
    private static final int COOLDOWN = 30;
    public static final float HALF_WIDTH = 0.7F;
    public static final float HEIGHT = 2.3F;

    private UUID partnerId;
    private Vec3 partnerPos = Vec3.ZERO;
    /** Entidades que não podem usar este portal até saírem dele. */
    private final Set<UUID> ignored = new HashSet<>();
    private boolean primed;

    public SpellPortalEntity(EntityType<? extends SpellPortalEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_EXIT, false);
        builder.define(DATA_YAW, 0F);
    }

    public boolean isExit() { return entityData.get(DATA_EXIT); }
    public float facing() { return entityData.get(DATA_YAW); }

    /** Liga este portal ao outro. */
    public void link(SpellPortalEntity other, boolean exit, float yaw) {
        this.partnerId = other.getUUID();
        this.partnerPos = other.position();
        entityData.set(DATA_EXIT, exit);
        entityData.set(DATA_YAW, yaw);
        setYRot(yaw);
    }

    public void ignore(UUID id) {
        ignored.add(id);
    }

    private AABB zone() {
        return new AABB(getX() - HALF_WIDTH, getY(), getZ() - HALF_WIDTH, getX() + HALF_WIDTH, getY() + HEIGHT, getZ() + HALF_WIDTH);
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        if (partnerId == null) {
            discard();
            return;
        }
        // O outro lado sumiu (chunk dele está ativo e ele não está lá): este também se fecha
        if (age % 20 == 5 && level.isPositionEntityTicking(BlockPos.containing(partnerPos))
                && !(level.getEntity(partnerId) instanceof SpellPortalEntity)) {
            onExpire(level, ctx);
            discard();
            return;
        }
        if (age % 80 == 2) {
            level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.25F, 1.6F);
        }
        AABB zone = zone();
        Set<UUID> insideNow = new HashSet<>();
        long now = level.getGameTime();
        for (Entity e : level.getEntities(this, zone, x -> !(x instanceof SpellFormEntity))) {
            // Todos que estão dentro contam (inclusive montados), para "ignorar até sair" funcionar
            insideNow.add(e.getUUID());
            if (!canTravel(e)) continue;
            // Na abertura, quem já estava dentro (o conjurador) precisa sair e entrar de novo
            if (!primed) {
                ignored.add(e.getUUID());
                continue;
            }
            if (ignored.contains(e.getUUID())) continue;
            if (e.getPersistentData().getLong(COOLDOWN_KEY) > now) continue;
            travel(level, ctx, e, now);
        }
        primed = true;
        ignored.retainAll(insideNow);
    }

    private boolean canTravel(Entity e) {
        // Quem está montado viaja junto com a montaria (teleportar só o passageiro o desmontaria)
        return e.isAlive() && !e.isSpectator() && !e.isPassenger() && !(e instanceof SpellFormEntity) && e.canUsePortal(false)
                && !e.getType().is(Tags.EntityTypes.TELEPORTING_NOT_SUPPORTED);
    }

    private void travel(ServerLevel level, SpellContext ctx, Entity e, long now) {
        Vec3 dest = partnerPos;
        if (!level.isLoaded(BlockPos.containing(dest))) return;
        Vec3 from = e.position();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + e.getBbHeight() / 2, from.z, 20, 0.3, 0.5, 0.3, 0.05);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);

        // Recarga e "ignorar na chegada" valem também para quem está montado (se desmontar lá dentro)
        SpellPortalEntity partner = level.getEntity(partnerId) instanceof SpellPortalEntity p ? p : null;
        e.getSelfAndPassengers().forEach(t -> {
            t.getPersistentData().putLong(COOLDOWN_KEY, now + COOLDOWN);
            if (partner != null) partner.ignore(t.getUUID());
        });
        e.teleportTo(dest.x, dest.y, dest.z);
        e.resetFallDistance();
        // Montaria guiada por jogador: o cliente dele controla a posição, então avisa já do novo lugar
        if (e.getControllingPassenger() instanceof ServerPlayer rider) {
            rider.connection.send(new ClientboundMoveVehiclePacket(e));
        }

        level.sendParticles(ParticleTypes.PORTAL, dest.x, dest.y + e.getBbHeight() / 2, dest.z, 30, 0.3, 0.6, 0.3, 0.3);
        level.sendParticles(FormsBUtil.dust(color(), 1.2F), dest.x, dest.y + 1, dest.z, 12, 0.4, 0.6, 0.4, 0);
        level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.0F);

        affect(ctx, e);
        for (Entity passenger : e.getIndirectPassengers()) affect(ctx, passenger);
    }

    /** Efeitos em quem atravessou: o conjurador só recebe os benéficos (cura/escudo). */
    private void affect(SpellContext ctx, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || casterAbsent(ctx)) return;
        if (le == ctx.caster() || (ctx.casterId() != null && le.getUUID().equals(ctx.casterId()))) {
            FormsBUtil.applyBeneficialToCaster(ctx);
        } else if (FormsBUtil.anyBeneficial(ctx) || !ctx.isAlly(le)) {
            // Aliado num portal sem cura/escudo: nada a aplicar (o Encadear/Ao Acertar não sai de um amigo)
            ctx.resolveOnEntity(le, this);
        }
    }

    @Override
    protected void onExpire(ServerLevel level, SpellContext ctx) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.1, getZ(), 40, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 1.4F);
        // Ao Terminar: quando os anéis se fecham, o próximo estágio nasce na SAÍDA (só um dos dois portais avisa)
        if (isExit()) stageEnd(ctx, position().add(0, 1.1, 0));
    }

    @Override
    protected void clientTick() {
        boolean reduced = FormsBUtil.reducedParticles();
        int n = reduced ? 1 : 3;
        double yaw = Math.toRadians(facing());
        // Plano do anel: eixo lateral perpendicular ao "olhar" do portal
        double sx = Math.cos(yaw), sz = Math.sin(yaw);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double lx = Math.cos(a) * 0.75, ly = Math.sin(a) * 1.1;
            double x = getX() + sx * lx, y = getY() + 1.15 + ly, z = getZ() + sz * lx;
            level().addParticle(ParticleTypes.PORTAL, x, y, z, -sx * lx * 0.6, -ly * 0.6, -sz * lx * 0.6);
            if (random.nextInt(3) == 0) level().addParticle(FormsBUtil.dust(color(), 0.8F), x, y, z, 0, 0, 0);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }

    @Override
    protected void readExtra(CompoundTag tag) {
        partnerId = tag.hasUUID("Partner") ? tag.getUUID("Partner") : null;
        partnerPos = new Vec3(tag.getDouble("PX"), tag.getDouble("PY"), tag.getDouble("PZ"));
        entityData.set(DATA_EXIT, tag.getBoolean("Exit"));
        entityData.set(DATA_YAW, tag.getFloat("Facing"));
        primed = true;
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        if (partnerId != null) tag.putUUID("Partner", partnerId);
        tag.putDouble("PX", partnerPos.x);
        tag.putDouble("PY", partnerPos.y);
        tag.putDouble("PZ", partnerPos.z);
        tag.putBoolean("Exit", isExit());
        tag.putFloat("Facing", facing());
    }
}
