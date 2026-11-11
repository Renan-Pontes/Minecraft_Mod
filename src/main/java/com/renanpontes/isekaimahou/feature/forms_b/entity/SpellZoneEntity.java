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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Zona (círculo mágico no chão que aplica os efeitos a cada segundo em quem está dentro)
 * e Pulso (ponto fixo que solta ondas a cada segundo). As duas formas usam esta entidade,
 * com visual diferente ({@link #variant()}).
 */
public class SpellZoneEntity extends SpellFormEntity {
    public static final int ZONE = 0;
    public static final int PULSE = 1;
    /** Evento de entidade: o pulso disparou (o cliente desenha a onda). */
    public static final byte EVENT_PULSE = 72;
    private static final int INTERVAL = 20;

    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(SpellZoneEntity.class, EntityDataSerializers.INT);

    /** Cliente: tickCount do último pulso (para animar a onda). */
    private int lastPulseTick = -1000;

    public SpellZoneEntity(EntityType<? extends SpellZoneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VARIANT, ZONE);
    }

    public int variant() { return entityData.get(DATA_VARIANT); }
    public void setVariant(int v) { entityData.set(DATA_VARIANT, v); }

    @Override
    public boolean isPersistentSpell() { return true; }

    /** Cliente: 0..1 da onda de pulso (1 = acabou). */
    public float pulseProgress(float partialTick) {
        return Mth.clamp((tickCount + partialTick - lastPulseTick) / 14F, 0F, 1F);
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        if (age % INTERVAL != 1 || casterAbsent(ctx)) return;
        Vec3 center = position().add(0, 0.4, 0);
        float r = radius();
        // Magia programável: o 1º alvo atingido de verdade avisa o núcleo (Ao Acertar / Saltar), antes dos efeitos
        if (FormsBUtil.wantsImpact(ctx)) {
            LivingEntity hit = FormsBUtil.areaHit(ctx, center, r, null);
            if (hit != null) FormsBUtil.impactOn(ctx, hit);
        }
        ctx.resolveArea(center, r, this, ctx.formStats().sensitive);
        LivingEntity caster = ctx.caster();
        if (caster != null && caster.level() == level && FormsBUtil.inside(caster, center, r)) {
            FormsBUtil.applyBeneficialToCaster(ctx);
        }
        if (variant() == PULSE) {
            level.broadcastEntityEvent(this, EVENT_PULSE);
            int pts = Math.max(16, (int) (r * 10));
            ElementFx.ring(level, position(), element(), r, pts);
            if (accentElement() != element()) ElementFx.ring(level, position().add(0, 0.25, 0), accentElement(), r * 0.8, pts / 2);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                    0.8F, 0.7F + level.random.nextFloat() * 0.2F);
        } else if (age % (INTERVAL * 2) == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }

    @Override
    protected void onExpire(ServerLevel level, SpellContext ctx) {
        ElementFx.ring(level, position(), element(), radius(), 24);
        if (accentElement() != element()) ElementFx.ring(level, position(), accentElement(), radius() * 0.6, 16);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5F, 1.5F);
        // Ao Terminar: o próximo estágio nasce no centro da zona
        stageEnd(ctx, position().add(0, 0.4, 0));
    }

    @Override
    protected void clientTick() {
        boolean reduced = FormsBUtil.reducedParticles();
        float r = radius();
        int rgb = color();
        if (variant() == ZONE) {
            // Pó subindo de pontos aleatórios dentro do círculo + brilho na borda
            int n = reduced ? 1 : Math.max(1, (int) (r * 0.8F));
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(random.nextDouble()) * r;
                level().addParticle(FormsBUtil.dust(rgb, 0.9F), getX() + Math.cos(a) * d, getY() + 0.1,
                        getZ() + Math.sin(a) * d, 0, 0.02, 0);
            }
            if (!reduced && age % 2 == 0) {
                // Dois braços girando na borda: em magias multi-elemento, um de cada elemento
                double a = age * 0.12;
                for (int k = 0; k < 2; k++) {
                    double ang = a + k * Math.PI;
                    level().addParticle(ElementFx.signature(elementFor(k)), getX() + Math.cos(ang) * r, getY() + 0.15,
                            getZ() + Math.sin(ang) * r, 0, 0.01, 0);
                }
            }
        } else if (!reduced && age % 3 == 0) {
            // Pulso: faíscas girando em volta do núcleo
            double a = age * 0.3;
            level().addParticle(FormsBUtil.dust((age / 3) % 2 == 0 ? rgb : accentColor(), 1.0F), getX() + Math.cos(a) * 0.8,
                    getY() + 0.3, getZ() + Math.sin(a) * 0.8, 0, 0.03, 0);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_PULSE) {
            lastPulseTick = tickCount;
            float r = radius();
            int pts = FormsBUtil.reducedParticles() ? 12 : Math.max(20, (int) (r * 12));
            for (int i = 0; i < pts; i++) {
                double a = Math.PI * 2 * i / pts;
                double vx = Math.cos(a) * 0.25 * r / 3.5;
                double vz = Math.sin(a) * 0.25 * r / 3.5;
                level().addParticle(ElementFx.signature(elementFor(i)), getX() + Math.cos(a) * 0.5, getY() + 0.2,
                        getZ() + Math.sin(a) * 0.5, vx, 0.02, vz);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected void readExtra(CompoundTag tag) {
        setVariant(tag.getInt("Variant"));
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("Variant", variant());
    }
}
