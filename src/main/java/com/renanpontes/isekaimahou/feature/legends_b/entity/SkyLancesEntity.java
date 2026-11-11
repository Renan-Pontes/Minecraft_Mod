package com.renanpontes.isekaimahou.feature.legends_b.entity;

import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.feature.legends_b.LbFx;
import com.renanpontes.isekaimahou.feature.legends_b.LbUtil;
import com.renanpontes.isekaimahou.feature.legends_b.LegendsBRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lanças do Céu: uma formação de lentes/círculos mágicos no céu sobre a área mirada. Depois de se formar
 * ({@link #FORM_TICKS}), a cada {@link #INTERVAL} ticks as lentes disparam feixes de luz em TODOS os alvos da
 * área (cada feixe aplica a magia). Teto no caminho bloqueia o feixe (dá para se proteger). Sem alvos, as lentes
 * ainda riscam o chão de luz para o jogador ver o alcance. v0.8: com a destruição ligada, cada feixe derrete o chão
 * em vidro e o último clarão vitrifica a superfície da área inteira.
 */
public class SkyLancesEntity extends LegendSpellEntity {
    public static final int FORM_TICKS = 16;
    public static final int INTERVAL = 15;
    public static final int FADE_TICKS = 14;
    /** Máximo de alvos por pulso (desempenho em multidões). */
    private static final int MAX_TARGETS = 24;

    private static final EntityDataAccessor<Integer> DATA_COUNT = SynchedEntityData.defineId(SkyLancesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AREA = SynchedEntityData.defineId(SkyLancesEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RING = SynchedEntityData.defineId(SkyLancesEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(SkyLancesEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ACTIVE = SynchedEntityData.defineId(SkyLancesEntity.class, EntityDataSerializers.INT);

    private float beamRadius = 0.22F;
    private boolean ended;

    public SkyLancesEntity(EntityType<? extends SkyLancesEntity> type, Level level) {
        super(type, level);
    }

    /**
     * @param ground  centro da área, no chão
     * @param height  altura das lentes acima do chão
     * @param area    raio da área atingida
     * @param lenses  quantas lentes (3 + Dividir)
     * @param active  ticks disparando (depois de se formar)
     */
    public static SkyLancesEntity create(SpellContext ctx, Vec3 ground, float height, float area, int lenses, int active, float beamRadius) {
        SkyLancesEntity e = new SkyLancesEntity(LegendsBRegistry.SKY_LANCES.get(), ctx.level());
        e.bind(ctx);
        e.setPos(ground.x, ground.y + height, ground.z);
        e.entityData.set(DATA_COUNT, Math.max(1, lenses));
        e.entityData.set(DATA_AREA, area);
        e.entityData.set(DATA_RING, Math.min(area * 0.55F, 2.2F + lenses * 0.45F));
        e.entityData.set(DATA_HEIGHT, height);
        e.entityData.set(DATA_ACTIVE, Math.max(INTERVAL, active));
        e.beamRadius = beamRadius;
        return e;
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COUNT, 3);
        builder.define(DATA_AREA, 6F);
        builder.define(DATA_RING, 3F);
        builder.define(DATA_HEIGHT, 14F);
        builder.define(DATA_ACTIVE, 60);
    }

    public int count() { return entityData.get(DATA_COUNT); }
    public float area() { return entityData.get(DATA_AREA); }
    public float ring() { return entityData.get(DATA_RING); }
    public float height() { return entityData.get(DATA_HEIGHT); }
    public int active() { return entityData.get(DATA_ACTIVE); }

    /** Centro da área no chão. */
    public Vec3 ground() {
        return position().subtract(0, height(), 0);
    }

    /** Posição da lente {@code i} (a formação gira devagar). Mesma conta no cliente e no servidor. */
    public Vec3 lensPos(int i, float ageF) {
        int n = count();
        double a = Math.PI * 2 * i / n + ageF * 0.015;
        double r = n == 1 ? 0 : ring();
        return position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
    }

    /** 0..1: formação (cresce) e fim (some). Usado pelo renderer. */
    public float presence(float ageF) {
        float in = Math.min(1F, ageF / FORM_TICKS);
        float out = Math.min(1F, Math.max(0F, (FORM_TICKS + active() + FADE_TICKS - ageF) / FADE_TICKS));
        return Math.min(in, out);
    }

    /** 0..1: brilho de "carga" da lente, sobe até o disparo e cai logo depois. */
    public float charge(float ageF) {
        float t = ageF - FORM_TICKS;
        if (t < -6 || t > active()) return 0F;
        float m = ((t % INTERVAL) + INTERVAL) % INTERVAL;
        return m < 3 ? 1F - m / 3F : (m - 3) / (INTERVAL - 3F);
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================
    @Override
    protected void serverTick(ServerLevel sl, SpellContext c) {
        int t = age - FORM_TICKS;
        if (age == 1) {
            LbFx.sound(sl, position(), SoundEvents.BEACON_ACTIVATE, 2.0F, 1.3F);
            LbFx.sound(sl, ground(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.8F);
        }
        if (age % 3 == 0 && t < active()) {
            Vec3 l = lensPos(random.nextInt(count()), age);
            LbFx.send(sl, ParticleTypes.END_ROD, l.x, l.y - 0.3, l.z, 1, 0.6, 0.05, 0.6, 0.01);
            LbFx.send(sl, LbFx.dust(color(), 1.0F), l.x, l.y, l.z, 2, 0.8, 0.05, 0.8, 0);
        }
        if (t >= 0 && t < active() && t % INTERVAL == 0) pulse(sl, c, t / INTERVAL);
        if (!ended && t >= active()) {
            ended = true;
            // Último clarão: a luz concentrada derrete a superfície da área inteira (vidro e obsidiana chorona)
            if (LbUtil.erasureEnabled(c)) {
                Destruction.surface(sl, ground(), Math.min(48F, area() * 0.85F), c.caster(), Destruction.Mode.VITRIFY);
                LbFx.send(sl, ParticleTypes.FLASH, ground().add(0, 1, 0), 2, area() * 0.3, 0);
                LbFx.sound(sl, ground(), SoundEvents.GLASS_BREAK, 2.0F, 0.6F);
            }
            c.end(ground(), new Vec3(0, -1, 0));
            LbFx.sound(sl, position(), SoundEvents.BEACON_DEACTIVATE, 1.6F, 1.2F);
        }
        if (t >= active() + FADE_TICKS) discard();
    }

    /** Um pulso: um feixe em cada alvo da área (lentes em rodízio) + riscos de luz no chão onde não há alvos. */
    private void pulse(ServerLevel sl, SpellContext c, int index) {
        int n = count();
        int rgb = color();
        List<LivingEntity> targets = targets(sl, c);
        int sounds = 0;
        for (int i = 0; i < targets.size(); i++) {
            LivingEntity e = targets.get(i);
            Vec3 lens = lensPos((i + index) % n, age).add(0, -0.3, 0);
            Vec3 aim = LbUtil.center(e);
            Vec3 dir = aim.subtract(lens).normalize();
            BlockHitResult bh = LbUtil.clip(sl, lens, aim, ClipContext.Fluid.NONE, null);
            if (bh.getType() == HitResult.Type.BLOCK) {
                // Coberto: o feixe derrete no teto (abrigo de verdade contra a magia — mas o teto vira vidro)
                LbFx.beam(sl, lens, bh.getLocation(), rgb, 8, beamRadius);
                c.impact(bh.getLocation(), dir, null);
                c.resolveOnBlock(bh, this);
                scorch(sl, bh.getLocation(), rgb);
                vitrify(sl, c, bh.getLocation(), 1.4F);
                continue;
            }
            LbFx.beam(sl, lens, aim, rgb, 8, beamRadius);
            c.impact(aim, dir, e);
            c.resolveOnEntity(e, this);
            LbFx.send(sl, ParticleTypes.FLASH, aim, 1, 0, 0);
            LbFx.send(sl, ParticleTypes.END_ROD, aim, 10, 0.25, 0.12);
            LbFx.send(sl, LbFx.dust(LbFx.lighten(rgb, 0.4F), 1.4F), aim, 8, 0.3, 0);
            if (sounds++ < 4) LbFx.sound(sl, aim, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.9F);
            // O chão sob o alvo derrete em vidro
            if (i < 8) vitrify(sl, c, LbUtil.groundBelow(sl, aim, 6), 1.6F);
        }
        // Lentes sem alvo riscam o chão de luz (mostra a área e fica bonito)
        int extra = Math.max(0, Math.min(n, 4) - targets.size());
        Vec3 g = ground();
        for (int k = 0; k < extra; k++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * area() * 0.85;
            Vec3 lens = lensPos((k + index + targets.size()) % n, age).add(0, -0.3, 0);
            Vec3 top = new Vec3(g.x + Math.cos(a) * d, lens.y, g.z + Math.sin(a) * d);
            BlockHitResult bh = LbUtil.clip(sl, lens, LbUtil.groundBelow(sl, top, height() + 10), ClipContext.Fluid.ANY, null);
            Vec3 hitPos = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : LbUtil.groundBelow(sl, top, height() + 10);
            LbFx.beam(sl, lens, hitPos, rgb, 7, beamRadius * 0.8F);
            if (bh.getType() == HitResult.Type.BLOCK) c.resolveOnBlock(bh, this);
            else c.resolveOnBlock(new BlockHitResult(hitPos, Direction.UP, BlockPos.containing(hitPos.subtract(0, 0.1, 0)), false), this);
            scorch(sl, hitPos, rgb);
            vitrify(sl, c, hitPos, 1.8F);
        }
        LbFx.sound(sl, position(), SoundEvents.BEACON_POWER_SELECT, 1.8F, 1.6F);
        LbFx.sound(sl, g, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.4F);
    }

    /** Alvos da área: cilindro do chão até as lentes, quem a magia afeta, os mais perto do centro primeiro. */
    private List<LivingEntity> targets(ServerLevel sl, SpellContext c) {
        Vec3 g = ground();
        double r = area();
        LivingEntity caster = c.caster();
        AABB box = new AABB(g.x - r, g.y - 4, g.z - r, g.x + r, getY(), g.z + r);
        List<LivingEntity> list = sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && e != caster
                && horizontal(e.position(), g) <= r + e.getBbWidth() * 0.5 && LbUtil.affects(c, e));
        list.sort(Comparator.comparingDouble(e -> horizontal(e.position(), g)));
        return list.size() > MAX_TARGETS ? list.subList(0, MAX_TARGETS) : list;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Mancha de vidro derretido onde o feixe bateu (v0.8, fila do núcleo; respeita a regra de destruição). */
    private void vitrify(ServerLevel sl, SpellContext c, Vec3 p, float radius) {
        if (p == null || !LbUtil.erasureEnabled(c)) return;
        LivingEntity caster = c.caster();
        if (caster != null && caster.level() == sl && caster.position().distanceToSqr(p) < (radius + 1.5) * (radius + 1.5)) return;
        Destruction.surface(sl, p, radius + beamRadius * 2F, caster, Destruction.Mode.VITRIFY);
    }

    private void scorch(ServerLevel sl, Vec3 p, int rgb) {
        LbFx.send(sl, ParticleTypes.END_ROD, p.x, p.y + 0.1, p.z, 6, 0.2, 0.05, 0.2, 0.06);
        LbFx.send(sl, LbFx.dust(rgb, 1.2F), p.x, p.y + 0.1, p.z, 6, 0.35, 0.05, 0.35, 0);
        LbFx.send(sl, ParticleTypes.SMOKE, p.x, p.y + 0.1, p.z, 3, 0.2, 0.05, 0.2, 0.01);
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("Lenses", count());
        tag.putFloat("Area", area());
        tag.putFloat("Ring", ring());
        tag.putFloat("Height", height());
        tag.putInt("Active", active());
        tag.putFloat("BeamRadius", beamRadius);
        tag.putBoolean("Ended", ended);
    }

    @Override
    protected void readExtra(CompoundTag tag) {
        entityData.set(DATA_COUNT, Math.max(1, tag.getInt("Lenses")));
        entityData.set(DATA_AREA, tag.getFloat("Area"));
        entityData.set(DATA_RING, tag.getFloat("Ring"));
        entityData.set(DATA_HEIGHT, tag.getFloat("Height"));
        entityData.set(DATA_ACTIVE, Math.max(INTERVAL, tag.getInt("Active")));
        beamRadius = tag.contains("BeamRadius") ? tag.getFloat("BeamRadius") : 0.22F;
        ended = tag.getBoolean("Ended");
    }
}
