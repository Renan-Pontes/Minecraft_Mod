package com.renanpontes.isekaimahou.feature.forms_a.entity;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_a.FormsAEntities;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Visual do Feixe (e dos raios da Mira): um raio de luz de verdade, desenhado pelo renderer como uma fita brilhante
 * virada para a câmera (núcleo claro + halo aditivo + clarões nas pontas), que abre num instante e afina até sumir.
 * Só visual: não colide, não é salvo (noSave) e some sozinho depois de {@link #life()} ticks.
 * Nasce na ponta de saída; {@link #end()} é o vetor até a ponta de chegada.
 */
public class SpellBeamEntity extends Entity {
    private static final EntityDataAccessor<Vector3f> END =
            SynchedEntityData.defineId(SpellBeamEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(SpellBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH =
            SynchedEntityData.defineId(SpellBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(SpellBeamEntity.class, EntityDataSerializers.INT);

    public static final int DEFAULT_LIFE = 9;

    public SpellBeamEntity(EntityType<? extends SpellBeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Cria o feixe de {@code from} até {@code to}. {@code argb} = cor da magia; {@code width} = largura do núcleo
     * (blocos); {@code life} = ticks até sumir. Depois chame addFreshEntity.
     */
    public static SpellBeamEntity create(ServerLevel level, Vec3 from, Vec3 to, int argb, float width, int life) {
        SpellBeamEntity b = new SpellBeamEntity(FormsAEntities.SPELL_BEAM.get(), level);
        b.setPos(from.x, from.y, from.z);
        Vec3 d = to.subtract(from);
        b.entityData.set(END, new Vector3f((float) d.x, (float) d.y, (float) d.z));
        b.entityData.set(COLOR, argb);
        b.entityData.set(WIDTH, Math.max(0.03F, Math.min(2.0F, width)));
        b.entityData.set(LIFE, Math.max(2, life));
        return b;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(END, new Vector3f(0, 1, 0));
        builder.define(COLOR, 0xFFFFFFFF);
        builder.define(WIDTH, 0.25F);
        builder.define(LIFE, DEFAULT_LIFE);
    }

    /** Vetor da ponta de saída (a posição da entidade) até a ponta de chegada. */
    public Vector3f end() { return entityData.get(END); }

    public int color() { return entityData.get(COLOR); }

    public float width() { return entityData.get(WIDTH); }

    public int life() { return entityData.get(LIFE); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            boolean reduced = FormsFx.reducedParticles();
            if (tickCount <= 2 && !reduced) glitter();
            if (tickCount < life() - 1 && (!reduced || tickCount % 3 == 0)) hitSparks();
            return;
        }
        if (tickCount >= life()) discard();
    }

    /** Cliente: poeira brilhante solta ao longo do raio nos primeiros ticks (some junto com ele). */
    private void glitter() {
        Vector3f e = end();
        double len = Math.sqrt(e.x * e.x + e.y * e.y + e.z * e.z);
        int n = Math.max(2, (int) (len * 0.9));
        ParticleOptions mote = FormsFx.dust(FormsFx.lighten(color(), 0.45F), 0.6F + width());
        for (int i = 0; i < n; i++) {
            double t = random.nextDouble();
            double x = getX() + e.x * t, y = getY() + e.y * t, z = getZ() + e.z * t;
            double s = width() * 0.6;
            level().addParticle(mote, x + (random.nextDouble() - 0.5) * s, y + (random.nextDouble() - 0.5) * s,
                    z + (random.nextDouble() - 0.5) * s, 0, 0, 0);
            if (random.nextFloat() < 0.25F) {
                level().addParticle(Vfx.endRod(), x, y, z, (random.nextDouble() - 0.5) * 0.05,
                        (random.nextDouble() - 0.5) * 0.05, (random.nextDouble() - 0.5) * 0.05);
            }
        }
    }

    /** Cliente: faíscas e brilho saltando do ponto onde o raio bate (enquanto ele existir). */
    private void hitSparks() {
        Vector3f e = end();
        double x = getX() + e.x, y = getY() + e.y, z = getZ() + e.z;
        int rgb = color() & 0xFFFFFF;
        ParticleOptions spark = ElementFx.fx(SpellFxParticles.SPARK, ElementFx.lighten(rgb, 0.45F), 0.7F + width(), 0, -1, 0);
        int n = 1 + (int) (width() * 3);
        for (int i = 0; i < n; i++) {
            level().addParticle(spark, x, y, z, (random.nextDouble() - 0.5) * 0.5, random.nextDouble() * 0.35 + 0.05,
                    (random.nextDouble() - 0.5) * 0.5);
        }
        if (random.nextInt(2) == 0) {
            level().addParticle(ElementFx.glow(rgb, 1.2F + width() * 2F), x, y, z, 0, 0.02, 0);
        }
    }

    /** O raio inteiro (não só a ponta de saída) decide se aparece na tela. */
    @Override
    public AABB getBoundingBoxForCulling() {
        Vector3f e = end();
        Vec3 p = position();
        return new AABB(p, p.add(e.x, e.y, e.z)).inflate(width() * 3.0 + 0.5);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }

    // Só visual e de vida curtíssima: nada a salvar (o tipo é noSave)
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
