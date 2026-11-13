package com.renanpontes.isekaimahou.feature.legends_c.entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.LcRegistry;
import com.renanpontes.isekaimahou.feature.legends_c.infinity.Infinity;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A esfera do Infinito: acompanha o dono (o cliente desenha a distorção) e SEGURA os projéteis parados — cada um preso
 * na posição relativa ao dono em que parou (acompanha quando ele anda, então ele nunca "esbarra" no que parou). Quando o
 * Infinito acaba, solta todos: eles caem.
 */
public class InfinityFieldEntity extends LcEntity {
    private static final EntityDataAccessor<Boolean> DATA_PIERCED = SynchedEntityData.defineId(InfinityFieldEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_HELD = SynchedEntityData.defineId(InfinityFieldEntity.class, EntityDataSerializers.INT);
    /** Máximo de projéteis suspensos ao mesmo tempo. */
    public static final int MAX_HELD = 48;

    /** Projéteis suspensos: id de rede → posição relativa aos pés do dono. */
    private final Map<Integer, Vec3> held = new HashMap<>();

    public InfinityFieldEntity(EntityType<? extends InfinityFieldEntity> type, Level level) {
        super(type, level);
    }

    public static InfinityFieldEntity spawn(ServerLevel level, LivingEntity owner) {
        InfinityFieldEntity f = new InfinityFieldEntity(LcRegistry.INFINITY_FIELD.get(), level);
        f.bindOwner(owner);
        f.setPos(owner.getX(), owner.getY(), owner.getZ());
        level.addFreshEntity(f);
        return f;
    }

    @Override
    protected void defineExtra(SynchedEntityData.Builder builder) {
        builder.define(DATA_PIERCED, false);
        builder.define(DATA_HELD, 0);
    }

    /** Sob Acerto Garantido: a esfera falha (o cliente desenha rachada/piscando). */
    public boolean pierced() { return entityData.get(DATA_PIERCED); }

    public void setPierced(boolean p) {
        if (pierced() != p) entityData.set(DATA_PIERCED, p);
    }

    /** Quantos projéteis estão parados (para o brilho do cliente). */
    public int heldCount() { return entityData.get(DATA_HELD); }

    public boolean isHeld(Projectile p) {
        return held.containsKey(p.getId());
    }

    /** Prende o projétil onde está. Falso se já há projéteis demais (aí ele só fica parado). */
    public boolean hold(Projectile p) {
        if (owner == null || held.size() >= MAX_HELD) return false;
        held.put(p.getId(), p.position().subtract(owner.position()));
        p.setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_HELD, held.size());
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != sl || !Infinity.has(owner)) {
            release(sl);
            if (owner != null) Infinity.forget(owner.getUUID(), this);
            discard();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        if (pierced()) {
            // Acerto Garantido: o Infinito não funciona — o que estava suspenso volta a cair
            release(sl);
            if (entityData.get(DATA_HELD) != 0) entityData.set(DATA_HELD, 0);
            return;
        }
        Iterator<Map.Entry<Integer, Vec3>> it = held.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Vec3> en = it.next();
            Entity e = sl.getEntity(en.getKey());
            if (!(e instanceof Projectile p) || !p.isAlive()) {
                it.remove();
                continue;
            }
            Vec3 at = owner.position().add(en.getValue());
            p.setPos(at.x, at.y, at.z);
            p.setDeltaMovement(Vec3.ZERO);
            p.hurtMarked = true;
            if ((tickCount + en.getKey()) % 10 == 0) sl.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
        if (entityData.get(DATA_HELD) != held.size()) entityData.set(DATA_HELD, held.size());
    }

    /** O Infinito acabou: o que estava suspenso cai. */
    private void release(ServerLevel sl) {
        if (held.isEmpty()) return;
        for (Integer id : held.keySet()) {
            if (sl.getEntity(id) instanceof Projectile p && p.isAlive()) {
                p.setDeltaMovement(new Vec3(0, -0.25, 0));
                p.hurtMarked = true;
            }
        }
        LcFx.sound(sl, position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.6F);
        held.clear();
    }

    /** Partículas presas na casca (pontinhos imóveis que brilham e somem). */
    private void clientTick() {
        LivingEntity o = ownerEntity();
        if (o == null || random.nextInt(3) != 0) return;
        double r = Infinity.STOP + o.getBbWidth() * 0.5 + 0.2;
        double z = random.nextDouble() * 2 - 1, a = random.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        Vec3 c = o.position().add(0, o.getBbHeight() * 0.5, 0).add(Math.cos(a) * s * r, z * r, Math.sin(a) * s * r);
        level().addParticle(pierced() ? Vfx.crit() : Vfx.endRod(), c.x, c.y, c.z, 0, 0, 0);
    }
}
