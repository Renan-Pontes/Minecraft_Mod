package com.renanpontes.isekaimahou.feature.forms_b.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.summon.SummonForge;
import com.renanpontes.isekaimahou.feature.forms_b.summon.SummonOrder;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Invocação: espírito flutuante do conjurador. Segue o dono (por cima do ombro) e, a cada 40 ticks,
 * lança os efeitos da magia na ameaça mais próxima (16 blocos), com uma linha de partículas.
 * O movimento é calculado no cliente e no servidor (fica suave).
 * <p>
 * v1.0 (onda 4): obedece às ordens da Forja de Invocações ({@link SummonOrder}) — seguir (padrão, como sempre foi),
 * guardar um ponto / ficar parado (flutua no posto e ataca o que chegar perto) e caçar o alvo marcado (vai atrás dele
 * e só lança nele). Espíritos salvos antes disso carregam em "seguir".
 */
public class SpellConjurerEntity extends SpellFormEntity implements GeoEntity, OwnableEntity {
    public static final int CAST_INTERVAL = 40;
    public static final double RANGE = 16.0;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.spell_conjurer.idle");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.spell_conjurer.cast");

    private static final EntityDataAccessor<Integer> DATA_OWNER_NET = SynchedEntityData.defineId(SpellConjurerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SLOT = SynchedEntityData.defineId(SpellConjurerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(SpellConjurerEntity.class, EntityDataSerializers.INT);
    /** Ordem ({@link SummonOrder#ordinal()}), o posto (Guardar/Ficar) e o id de rede do alvo marcado (Caçar). */
    private static final EntityDataAccessor<Integer> DATA_ORDER = SynchedEntityData.defineId(SpellConjurerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_POST = SynchedEntityData.defineId(SpellConjurerEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_MARK = SynchedEntityData.defineId(SpellConjurerEntity.class, EntityDataSerializers.INT);
    /** Velocidade (blocos/tick) ao ir para o posto ou atrás da caça. */
    private static final double TRAVEL_SPEED = 0.55;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Alvo marcado (servidor; o id de rede em {@link #DATA_MARK} é só para o movimento no cliente). */
    @Nullable
    private UUID markId;

    public SpellConjurerEntity(EntityType<? extends SpellConjurerEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_NET, -1);
        builder.define(DATA_SLOT, 0);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_ORDER, 0);
        builder.define(DATA_POST, new Vector3f());
        builder.define(DATA_MARK, -1);
    }

    // =====================================================================================
    //  Ordens (Forja de Invocações)
    // =====================================================================================

    public SummonOrder order() {
        return SummonOrder.byIndex(entityData.get(DATA_ORDER));
    }

    /** Está em Caçar com um alvo marcado? */
    public boolean hasMark() {
        return markId != null;
    }

    /**
     * Dá uma ordem ao espírito (servidor).
     * @param post ponto a guardar (null = onde o espírito está agora); só vale em Guardar/Ficar
     * @param mark alvo da caça (null = ainda sem alvo: comporta-se como Seguir até o dono marcar um)
     */
    public void command(SummonOrder order, @Nullable Vec3 post, @Nullable LivingEntity mark) {
        entityData.set(DATA_ORDER, order.ordinal());
        if (order.anchored()) {
            Vec3 p = post != null ? post : position();
            entityData.set(DATA_POST, new Vector3f((float) p.x, (float) p.y, (float) p.z));
        }
        markId = order == SummonOrder.HUNT && mark != null ? mark.getUUID() : null;
        entityData.set(DATA_MARK, markId != null ? mark.getId() : -1);
        entityData.set(DATA_TARGET, -1);
    }

    @Nullable
    private Entity markEntity() {
        int id = entityData.get(DATA_MARK);
        Entity e = id >= 0 ? level().getEntity(id) : null;
        return e != null && e.isAlive() ? e : null;
    }

    /** Para onde o espírito quer ir neste tick, conforme a ordem. */
    private Vec3 goal(Entity owner) {
        SummonOrder order = order();
        int slot = entityData.get(DATA_SLOT);
        double bob = Math.sin((tickCount + slot * 13) * 0.12) * 0.15;
        if (order.anchored()) {
            Vector3f p = entityData.get(DATA_POST);
            // vários espíritos no mesmo posto ficam em roda
            double a = slot * 2.4, r = slot == 0 ? 0 : 0.9;
            return new Vec3(p.x() + Math.cos(a) * r, p.y() + bob, p.z() + Math.sin(a) * r);
        }
        if (order == SummonOrder.HUNT) {
            Entity m = markEntity();
            if (m != null) {
                // paira acima e um pouco atrás da caça (do lado do dono), à distância de lançar
                Vec3 back = owner.position().subtract(m.position()).multiply(1, 0, 1);
                back = back.lengthSqr() > 1.0E-4 ? back.normalize().scale(3.0) : Vec3.ZERO;
                return m.position().add(back).add(0, m.getBbHeight() + 1.2 + bob + (slot / 2) * 0.4, 0);
            }
        }
        return anchor(owner);
    }

    /** Liga ao dono; {@code slot} separa vários espíritos (Dividir) em volta dele. */
    public void bind(Entity owner, int slot) {
        entityData.set(DATA_OWNER_NET, owner.getId());
        entityData.set(DATA_SLOT, slot);
        Vec3 p = anchor(owner);
        setPos(p.x, p.y, p.z);
        setYRot(owner.getYRot());
        setOldPosAndRot();
    }

    @Override
    public UUID getOwnerUUID() {
        return ownerId();
    }

    private Entity ownerEntity() {
        int id = entityData.get(DATA_OWNER_NET);
        return id >= 0 ? level().getEntity(id) : null;
    }

    /** Ponto acima do ombro do dono, girando com ele; cada espírito extra fica num lado/altura. */
    private Vec3 anchor(Entity owner) {
        int slot = entityData.get(DATA_SLOT);
        float side = slot % 2 == 0 ? -1F : 1F;
        float yaw = (owner instanceof LivingEntity le ? le.yBodyRot : owner.getYRot()) * Mth.DEG_TO_RAD;
        double sideX = Math.cos(yaw), sideZ = Math.sin(yaw);
        double backX = Math.sin(yaw), backZ = -Math.cos(yaw);
        double dist = 1.1 + (slot / 2) * 0.7;
        double bob = Math.sin((tickCount + slot * 13) * 0.12) * 0.15;
        return new Vec3(owner.getX() + sideX * side * dist + backX * 0.6,
                owner.getY() + owner.getBbHeight() + 0.2 + (slot / 2) * 0.4 + bob,
                owner.getZ() + sideZ * side * dist + backZ * 0.6);
    }

    @Override
    public void tick() {
        Entity owner = ownerEntity();
        if (owner != null) {
            Vec3 goal = goal(owner);
            Vec3 cur = position();
            Vec3 next;
            if (order() == SummonOrder.FOLLOW || (order() == SummonOrder.HUNT && markEntity() == null)) {
                next = goal.distanceToSqr(cur) > 20 * 20 ? goal : cur.lerp(goal, 0.25);
            } else {
                // indo para o posto / atrás da caça: voa com velocidade limitada em vez de "colar" no dono
                Vec3 to = goal.subtract(cur);
                double d = to.length();
                next = d <= TRAVEL_SPEED ? goal : cur.add(to.scale(TRAVEL_SPEED / d));
            }
            setPos(next.x, next.y, next.z);
            Entity t = entityData.get(DATA_TARGET) >= 0 ? level().getEntity(entityData.get(DATA_TARGET)) : null;
            Vec3 face = t != null ? t.position().subtract(position()) : owner.getLookAngle();
            if (face.horizontalDistanceSqr() > 1.0E-4) {
                float want = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F;
                setYRot(Mth.rotLerp(0.3F, getYRot(), want));
            }
        }
        super.tick();
    }

    /** Cliente: o espírito segue o dono por conta própria; ignorar a posição do servidor evita "tremidas". */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (level().isClientSide && ownerEntity() != null) return;
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive() || caster.level() != level) {
            fizzle();
            return;
        }
        if (entityData.get(DATA_OWNER_NET) != caster.getId()) entityData.set(DATA_OWNER_NET, caster.getId());
        if (age == 1) {
            ElementFx.burst(level, position(), element(), 20, 0.4);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 0.9F, 0.8F);
        }
        if (age % 20 == 0) refreshMark(level, ctx, caster);
        // Escolhe o alvo um pouco antes de lançar (o espírito vira para ele)
        if (age % CAST_INTERVAL == CAST_INTERVAL - 10) {
            LivingEntity t;
            if (order() == SummonOrder.HUNT && markId != null) {
                // caçando: só a caça interessa (e só quando já está ao alcance)
                t = markEntity() instanceof LivingEntity m && m.distanceToSqr(this) <= RANGE * RANGE ? m : null;
            } else {
                t = FormsBUtil.nearestThreat(ctx, position(), RANGE, !ctx.formStats().ghost);
            }
            entityData.set(DATA_TARGET, t != null ? t.getId() : -1);
        }
        if (age % CAST_INTERVAL != 0) return;
        Entity te = level.getEntity(entityData.get(DATA_TARGET));
        if (!(te instanceof LivingEntity target) || !FormsBUtil.isThreat(ctx, target)
                || target.distanceToSqr(this) > RANGE * RANGE) {
            entityData.set(DATA_TARGET, -1);
            return;
        }
        Vec3 from = position().add(0, 0.5, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        ElementFx.line(level, from, to, elementFor(age / CAST_INTERVAL));
        level.sendParticles(FormsBUtil.dust(color(), 1.2F), to.x, to.y, to.z, 8, 0.2, 0.2, 0.2, 0);
        ctx.resolveOnEntity(target, this);
        triggerAnim("action", "cast");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.7F, 1.5F);
    }

    /** Caçar: confere se a caça ainda existe (e acerta o id de rede depois de um recarregamento). */
    private void refreshMark(ServerLevel level, SpellContext ctx, LivingEntity caster) {
        if (order() != SummonOrder.HUNT) return;
        if (markId == null) {
            SummonForge.awaitMark(caster.getUUID());
            return;
        }
        Entity m = level.getEntity(markId);
        if (m instanceof LivingEntity le && le.isAlive() && !ctx.isAlly(le) && le.distanceToSqr(this) < 64 * 64) {
            if (entityData.get(DATA_MARK) != le.getId()) entityData.set(DATA_MARK, le.getId());
            return;
        }
        // a caça acabou: espera o dono marcar outro alvo
        markId = null;
        entityData.set(DATA_MARK, -1);
        SummonForge.awaitMark(caster.getUUID());
    }

    @Override
    protected void onExpire(ServerLevel level, SpellContext ctx) {
        level.sendParticles(Vfx.poof(), getX(), getY() + 0.5, getZ(), 10, 0.2, 0.3, 0.2, 0.02);
        ElementFx.burst(level, position().add(0, 0.5, 0), element(), 14, 0.3);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_DEATH, SoundSource.PLAYERS, 0.6F, 1.2F);
        // O espírito partiu: Ao Terminar nasce onde ele estava
        stageEnd(ctx, position().add(0, 0.5, 0));
    }

    @Override
    protected void clientTick() {
        if (FormsBUtil.reducedParticles() && age % 4 != 0) return;
        // Rastro etéreo saindo da cauda
        level().addParticle(FormsBUtil.dust(color(), 0.7F), xo + (random.nextDouble() - 0.5) * 0.2, yo + 0.1,
                zo + (random.nextDouble() - 0.5) * 0.2, 0, -0.01, 0);
        if (random.nextInt(6) == 0) {
            level().addParticle(Vfx.enchant(), getX(), getY() + 0.9, getZ(),
                    (random.nextDouble() - 0.5) * 0.6, 0.3, (random.nextDouble() - 0.5) * 0.6);
        }
    }

    @Override
    protected void readExtra(CompoundTag tag) {
        entityData.set(DATA_SLOT, tag.getInt("Slot"));
        // campos da v1.0 (opcionais): espírito antigo = Seguir
        entityData.set(DATA_ORDER, SummonOrder.byIndex(tag.getInt("Order")).ordinal());
        if (tag.contains("PostX")) {
            entityData.set(DATA_POST, new Vector3f(tag.getFloat("PostX"), tag.getFloat("PostY"), tag.getFloat("PostZ")));
        }
        markId = tag.hasUUID("Mark") ? tag.getUUID("Mark") : null;
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("Slot", entityData.get(DATA_SLOT));
        tag.putInt("Order", entityData.get(DATA_ORDER));
        Vector3f p = entityData.get(DATA_POST);
        tag.putFloat("PostX", p.x());
        tag.putFloat("PostY", p.y());
        tag.putFloat("PostZ", p.z());
        if (markId != null) tag.putUUID("Mark", markId);
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(IDLE)));
        // Controlador separado: o gesto de lançar sobrepõe os braços sem reiniciar a flutuação
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("cast", CAST));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
