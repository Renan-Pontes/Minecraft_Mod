package com.renanpontes.isekaimahou.feature.legends_d.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_d.LdNet;
import com.renanpontes.isekaimahou.feature.legends_d.LdRegistry;
import com.renanpontes.isekaimahou.feature.legends_d.LdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Desintegração. Um cilindro de círculos mágicos sagrados (anéis empilhados que giram em sentidos alternados, grades
 * de luz) se fecha em volta do alvo/área; {@link #CHARGE} ticks depois, luz pura desce do céu: quem está dentro leva
 * 40% da vida máxima (chefes 12%) + 20·poder que IGNORA armadura e se desfaz em pó de luz, e o terreno do cilindro é
 * APAGADO até o céu ({@link Destruction#cylinder} ERASE).
 */
public class DisintegrationCageEntity extends LdSpellEntity {
    public static final int CHARGE = 30;
    public static final int PILLAR = 22;
    public static final int FADE = 14;
    public static final int GOLD = 0xFFE9A8;
    public static final int WHITE_GOLD = 0xFFF6DC;
    /** Gaiolas ativas por conjurador (multidões/área com Dividir não viram centenas de pilares). */
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();
    public static final int MAX_PER_CASTER = 12;

    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(DisintegrationCageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(DisintegrationCageEntity.class, EntityDataSerializers.FLOAT);

    private SpellStats stats = new SpellStats();
    private boolean struck;
    private boolean counted;

    public DisintegrationCageEntity(EntityType<? extends DisintegrationCageEntity> type, Level level) {
        super(type, level);
    }

    public static DisintegrationCageEntity create(SpellContext ctx, Vec3 ground, float radius, float height, SpellStats stats) {
        DisintegrationCageEntity e = new DisintegrationCageEntity(LdRegistry.DISINTEGRATION_CAGE.get(), ctx.level());
        e.bind(ctx, GOLD);
        e.stats = stats.copy();
        e.setPos(ground.x, ground.y, ground.z);
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_HEIGHT, height);
        return e;
    }

    public static int active(UUID caster) {
        return ACTIVE.getOrDefault(caster, 0);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 2F);
        builder.define(DATA_HEIGHT, 8F);
    }

    public float radius() { return entityData.get(DATA_RADIUS); }

    public float height() { return entityData.get(DATA_HEIGHT); }

    /** 0..1: os círculos se fechando (carga). */
    public float closing(float ageF) {
        return Mth.clamp(ageF / CHARGE, 0F, 1F);
    }

    /** 0..1: intensidade do pilar de luz (sobe num estalo e afina). */
    public float pillar(float ageF) {
        float t = ageF - CHARGE;
        if (t < 0) return 0F;
        if (t < 3) return t / 3F;
        return Mth.clamp(1F - (t - 3) / (PILLAR + FADE - 3), 0F, 1F);
    }

    /** 0..1: a gaiola some depois do julgamento. */
    public float presence(float ageF) {
        float t = ageF - CHARGE;
        if (t < 0) return Mth.clamp(ageF / 8F, 0F, 1F);
        return Mth.clamp(1F - t / (PILLAR * 0.6F), 0F, 1F);
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================
    @Override
    protected void serverTick(ServerLevel sl, SpellContext c) {
        Vec3 base = position();
        float r = radius();
        if (age == 1) {
            LdUtil.sound(sl, base, SoundEvents.BEACON_ACTIVATE, 2.0F, 1.5F);
            LdUtil.sound(sl, base, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 1.2F);
        }
        if (!struck) {
            if (age % 6 == 0) LdUtil.sound(sl, base, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 1.2F + age / (float) CHARGE);
            if (age % 2 == 0) {
                double a = age * 0.4;
                for (int i = 0; i < 4; i++) {
                    double ang = a + i * Math.PI / 2;
                    Vec3 p = base.add(Math.cos(ang) * r, random.nextDouble() * height(), Math.sin(ang) * r);
                    LdUtil.send(sl, Vfx.endRod(), p, 1, 0.02, 0.01);
                }
                LdUtil.send(sl, LdUtil.dust(WHITE_GOLD, 1.0F), base.add(0, height() * 0.5, 0), 3, r * 0.5, 0);
            }
            if (age >= CHARGE) judge(sl, c);
        }
        if (age > CHARGE + PILLAR + FADE) discard();
    }

    /** O julgamento: luz pura do céu. */
    private void judge(ServerLevel sl, SpellContext c) {
        struck = true;
        syncAge();
        Vec3 base = position();
        float r = radius();
        LivingEntity caster = c.caster();
        int top = sl.getMaxBuildHeight();
        AABB box = new AABB(base.x - r - 1, base.y - 3, base.z - r - 1, base.x + r + 1, top, base.z + r + 1);
        List<LivingEntity> inside = sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator()
                && horizontal(e.position(), base) <= r + e.getBbWidth() * 0.5 && e != caster && !c.isAlly(e));
        float power = LdUtil.powerOf(c, stats);
        for (LivingEntity e : inside) {
            boolean boss = LdUtil.isBoss(e);
            // 40% da vida máxima (chefes 12%) + 20 × poder, tudo DEPOIS da armadura
            float base0 = e.getMaxHealth() * (boss ? 0.12F : 0.40F) / Math.max(0.25F, power) + 20F;
            LdUtil.pierceDamage(c, e, base0, stats);
            disintegrateFx(sl, e);
            if (!e.isAlive() && !(e instanceof Player)) {
                // Desfeito: o corpo some em pó de luz (o loot cai normalmente)
                e.setInvisible(true);
                LdUtil.send(sl, LdUtil.fade(WHITE_GOLD, 0x000000, 1.8F), LdUtil.center(e), 40, e.getBbWidth() * 0.5, 0.06);
                LdUtil.send(sl, Vfx.whiteAsh(), LdUtil.center(e), 30, e.getBbWidth() * 0.6, 0.05);
            }
        }
        LdUtil.sound(sl, base, SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.5F);
        LdUtil.sound(sl, base, SoundEvents.TRIDENT_THUNDER, 4.0F, 1.4F);
        LdUtil.sound(sl, base, SoundEvents.AMETHYST_CLUSTER_BREAK, 3.0F, 0.6F);
        LdUtil.send(sl, ParticleTypes.FLASH, base.add(0, 1, 0), 2, 0.3, 0);
        LdUtil.send(sl, Vfx.endRod(), base.add(0, height() * 0.5, 0), 60, r * 0.6, 0.25);
        LdNet.near(sl, base, 40, new LdNet.Fx(LdNet.HOLY_FLASH, base, r, getId(), 14));
        BossKit.shake(sl, base, 28, 0.9F, 14);
        // Terreno apagado num cilindro até o céu (do chão um pouco abaixo dos pés)
        float bottom = (float) base.y - 3F;
        Destruction.cylinder(sl, new Vec3(base.x, bottom, base.z), r, top - bottom, caster, Destruction.Mode.ERASE);
    }

    private void disintegrateFx(ServerLevel sl, LivingEntity e) {
        Vec3 ce = LdUtil.center(e);
        double w = e.getBbWidth() * 0.45, h = e.getBbHeight() * 0.45;
        LdUtil.send(sl, LdUtil.fade(WHITE_GOLD, GOLD, 1.3F), ce.x, ce.y, ce.z, 30, w, h, w, 0.03);
        LdUtil.send(sl, Vfx.endRod(), ce.x, ce.y, ce.z, 14, w, h, w, 0.12);
        LdUtil.send(sl, ParticleTypes.FLASH, ce, 1, 0, 0);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide && !counted && casterId != null) {
            counted = true;
            ACTIVE.merge(casterId, 1, Integer::sum);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide && counted && casterId != null) {
            counted = false;
            ACTIVE.computeIfPresent(casterId, (k, v) -> v <= 1 ? null : v - 1);
        }
    }
}
