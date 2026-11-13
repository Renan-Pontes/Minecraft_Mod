package com.renanpontes.isekaimahou.feature.fusions_a.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.FaRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.FaUtil;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Zona das fusões: guarda a magia (contexto salvo) e aplica um efeito contínuo numa área — Poça de Lava, Névoa
 * Quente, Erupção, Tempestade, Nevasca, Chão Escorregadio, Tempestade de Areia, Areia Movediça, Coroa Solar e
 * nuvem de Esporos. Invisível: o visual são partículas desenhadas no cliente (e algumas no servidor).
 *
 * O dano sai sempre por {@code ctx.damage} (reações elementais, crédito do abate, XP). Cada conjurador tem no
 * máximo {@link #MAX_PER_KIND} zonas de cada tipo; lançar de novo no mesmo lugar só renova a zona.
 */
public class FusionZoneEntity extends Entity {
    public static final int MAX_PER_KIND = 3;

    public enum Kind { LAVA_POOL, HOT_MIST, ERUPTION, THUNDERSTORM, BLIZZARD, SLIPPERY, SANDSTORM, SINKHOLE, SOLAR_CROWN, SPORE_CLOUD }

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(FusionZoneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(FusionZoneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ANCHOR = SynchedEntityData.defineId(FusionZoneEntity.class, EntityDataSerializers.INT);

    private SpellContext ctx;
    private CompoundTag ctxTag;
    private SpellStats stats = FaUtil.PLAIN;
    private ResourceLocation glyphId;
    @Nullable
    private UUID ownerId;
    @Nullable
    private UUID anchorId;
    private int life = 100;
    private int age;
    private int nextStrike = 10;
    private int missingAnchor;

    /** Jatos da Erupção (posição + tick em que explodem). Não precisam ser salvos: duram poucos ticks. */
    private record Jet(Vec3 pos, int at) {}

    private final List<Jet> jets = new ArrayList<>();
    /** Quantas checagens seguidas cada alvo ficou na Nevasca (congela ao acumular). */
    private final Map<UUID, Integer> exposure = new HashMap<>();

    public FusionZoneEntity(EntityType<? extends FusionZoneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // =====================================================================================
    //  Criação
    // =====================================================================================

    /**
     * Cria (ou renova) uma zona. {@code anchor} != null faz a zona seguir essa entidade (Coroa Solar).
     */
    @Nullable
    public static FusionZoneEntity spawn(SpellContext ctx, SpellStats stats, Glyph glyph, Kind kind, Vec3 pos,
                                         float radius, int life, @Nullable Entity anchor) {
        ServerLevel level = ctx.level();
        UUID owner = ctx.casterId();
        List<FusionZoneEntity> mine = new ArrayList<>(level.getEntities(FaRegistries.FUSION_ZONE.get(),
                z -> z.isAlive() && z.kind() == kind && Objects.equals(owner, z.ownerId)));
        for (FusionZoneEntity z : mine) {
            boolean same = anchor != null ? anchor.getUUID().equals(z.anchorId)
                    : z.anchorId == null && z.position().distanceTo(pos) < Math.max(1.5, radius * 0.75);
            if (same) {
                z.renew(ctx, stats, life, radius);
                return z;
            }
        }
        while (mine.size() >= MAX_PER_KIND) {
            FusionZoneEntity oldest = mine.get(0);
            for (FusionZoneEntity z : mine) if (z.life - z.age < oldest.life - oldest.age) oldest = z;
            oldest.discard();
            mine.remove(oldest);
        }
        FusionZoneEntity z = FaRegistries.FUSION_ZONE.get().create(level);
        if (z == null) return null;
        z.moveTo(pos.x, pos.y, pos.z, 0, 0);
        z.ctx = ctx;
        z.ctxTag = ctx.save();
        z.stats = stats;
        z.glyphId = glyph.id();
        z.ownerId = owner;
        z.life = life;
        z.entityData.set(DATA_KIND, kind.ordinal());
        z.entityData.set(DATA_RADIUS, radius);
        if (anchor != null) {
            z.anchorId = anchor.getUUID();
            z.entityData.set(DATA_ANCHOR, anchor.getId());
        }
        level.addFreshEntity(z);
        return z;
    }

    private void renew(SpellContext ctx, SpellStats stats, int newLife, float radius) {
        this.ctx = ctx;
        this.ctxTag = ctx.save();
        this.stats = stats;
        this.life = age + Math.max(newLife, life - age);
        if (radius > radius()) entityData.set(DATA_RADIUS, radius);
    }

    public Kind kind() {
        int k = entityData.get(DATA_KIND);
        Kind[] all = Kind.values();
        return all[Math.max(0, Math.min(all.length - 1, k))];
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_KIND, 0);
        b.define(DATA_RADIUS, 3.0F);
        b.define(DATA_ANCHOR, -1);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (ctx == null) {
            ctx = SpellContext.load(sl, ctxTag);
            if (ctx == null) {
                discard();
                return;
            }
            stats = findStats();
        }
        if (++age >= life) {
            discard();
            return;
        }
        if (anchorId != null) {
            Entity a = sl.getEntity(anchorId);
            if (a == null) {
                // Ao carregar o mundo o portador pode entrar alguns ticks depois da zona; se sumiu de vez, a coroa acaba
                if (++missingAnchor > 100) discard();
                return;
            }
            missingAnchor = 0;
            if (!(a instanceof LivingEntity le) || !le.isAlive()
                    || (kind() == Kind.SOLAR_CROWN && !le.hasEffect(FaRegistries.SOLAR_CROWN))) {
                discard();
                return;
            }
            setPos(a.getX(), a.getY(), a.getZ());
            if (entityData.get(DATA_ANCHOR) != a.getId()) entityData.set(DATA_ANCHOR, a.getId());
        }
        switch (kind()) {
            case LAVA_POOL -> tickLavaPool(sl);
            case HOT_MIST -> tickHotMist(sl);
            case ERUPTION -> tickEruption(sl);
            case THUNDERSTORM -> tickStorm(sl);
            case BLIZZARD -> tickBlizzard(sl);
            case SLIPPERY -> tickSlippery(sl);
            case SANDSTORM -> tickSandstorm(sl);
            case SINKHOLE -> tickSinkhole(sl);
            case SOLAR_CROWN -> tickCrown(sl);
            case SPORE_CLOUD -> tickSpores(sl);
        }
    }

    private SpellStats findStats() {
        if (ctx != null && glyphId != null) {
            for (ParsedSpell.EffectEntry en : ctx.spell().effects()) {
                if (en.effect().id().equals(glyphId)) return en.stats();
            }
        }
        return FaUtil.PLAIN;
    }

    /** Inimigos (da magia) dentro do raio, com uma faixa de altura em volta do centro. */
    private List<LivingEntity> victims(double below, double above) {
        float r = radius();
        Vec3 c = position();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : ctx.enemiesAround(c.add(0, (above - below) * 0.5, 0), r + Math.max(below, above))) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double dy = e.getY() - c.y;
            if (dx * dx + dz * dz <= r * r && dy >= -below && dy <= above && !e.isSpectator()
                    && !(e instanceof ArmorStand)) out.add(e);
        }
        return out;
    }

    private void hurt(LivingEntity e, float base) {
        FaUtil.hurt(ctx, e, base, stats, this);
    }

    private void tickLavaPool(ServerLevel sl) {
        if (age % 10 != 0) return;
        for (LivingEntity e : victims(0.6, 1.2)) {
            hurt(e, 2.0F);
            e.igniteForSeconds(3);
        }
        if (age % 30 == 0) FaUtil.sound(sl, position(), SoundEvents.LAVA_AMBIENT, 0.5F, 1.0F);
    }

    private void tickHotMist(ServerLevel sl) {
        if (age % 10 != 0) return;
        for (LivingEntity e : victims(1.5, 3.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0), ctx.caster());
            if (age % 20 == 0) hurt(e, 1.5F);
            if (e instanceof Mob m && m.getTarget() != null && random.nextFloat() < 0.3F) m.setTarget(null);
        }
        if (age % 40 == 0) FaUtil.sound(sl, position(), SoundEvents.FIRE_EXTINGUISH, 0.4F, 0.6F);
    }

    private void tickEruption(ServerLevel sl) {
        float r = radius();
        if (age % 7 == 0 && age < life - 12) {
            Vec3 at;
            List<LivingEntity> foes = victims(2.0, 4.0);
            if (!foes.isEmpty() && random.nextBoolean()) {
                LivingEntity t = foes.get(random.nextInt(foes.size()));
                at = new Vec3(t.getX(), t.getY(), t.getZ());
            } else {
                double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
                at = new Vec3(getX() + Math.cos(a) * d, getY() + 1, getZ() + Math.sin(a) * d);
            }
            at = FaUtil.ground(sl, at, 4);
            jets.add(new Jet(at, age + 10));
        }
        Iterator<Jet> it = jets.iterator();
        while (it.hasNext()) {
            Jet j = it.next();
            Vec3 p = j.pos;
            if (age < j.at) {
                // Aviso: o chão racha e solta fumaça antes do jato
                if (age % 2 == 0) {
                    sl.sendParticles(Vfx.largeSmoke(), p.x, p.y + 0.1, p.z, 2, 0.25, 0.02, 0.25, 0.01);
                    sl.sendParticles(FaUtil.dust(0xFF5A1A, 1.3F), p.x, p.y + 0.05, p.z, 4, 0.4, 0.01, 0.4, 0);
                }
                continue;
            }
            it.remove();
            FaUtil.column(sl, p, ParticleTypes.LAVA, 3.5, 3, 0.15, 0);
            FaUtil.column(sl, p, Vfx.flame(), 4.0, 5, 0.2, 0.03);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.MAGMA_BLOCK.defaultBlockState()),
                    p.x, p.y + 0.1, p.z, 25, 0.4, 0.1, 0.4, 0.3);
            sl.sendParticles(Vfx.largeSmoke(), p.x, p.y + 3.5, p.z, 6, 0.3, 0.3, 0.3, 0.02);
            FaUtil.sound(sl, p, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.5F);
            FaUtil.sound(sl, p, SoundEvents.LAVA_POP, 1.0F, 0.7F);
            for (LivingEntity e : ctx.enemiesAround(p.add(0, 1.5, 0), 2.8)) {
                double dx = e.getX() - p.x, dz = e.getZ() - p.z;
                if (dx * dx + dz * dz > 1.6 * 1.6 || e.getY() < p.y - 0.6 || e.getY() > p.y + 3.5) continue;
                hurt(e, 5.0F);
                e.igniteForSeconds(4);
                FaUtil.addMotion(e, new Vec3(0, 0.85 * FaUtil.kbFactor(e), 0));
            }
        }
    }

    private void tickStorm(ServerLevel sl) {
        if (--nextStrike > 0) return;
        nextStrike = 16 + random.nextInt(14);
        float r = radius();
        List<LivingEntity> foes = victims(3.0, 6.0);
        Vec3 at;
        LivingEntity main = null;
        if (!foes.isEmpty()) {
            main = foes.get(random.nextInt(foes.size()));
            at = main.position();
        } else if (random.nextFloat() < 0.6F) {
            double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
            at = FaUtil.ground(sl, new Vec3(getX() + Math.cos(a) * d, getY() + 2, getZ() + Math.sin(a) * d), 6);
        } else {
            return;
        }
        FaUtil.visualLightning(sl, at);
        sl.sendParticles(Vfx.electric(), at.x, at.y + 0.5, at.z, 30, 0.6, 0.6, 0.6, 0.3);
        if (main != null) {
            hurt(main, 6.0F);
            main.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 15, 0), ctx.caster());
        }
        for (LivingEntity e : ctx.enemiesAround(at, 2.2)) {
            if (e == main) continue;
            hurt(e, 3.0F);
        }
    }

    private void tickBlizzard(ServerLevel sl) {
        if (age % 10 != 0) return;
        for (LivingEntity e : victims(2.0, 4.0)) {
            if (e.isOnFire()) e.clearFire();
            int n = exposure.merge(e.getUUID(), 1, Integer::sum);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, n >= 3 ? 1 : 0), ctx.caster());
            if (n >= 6) {
                exposure.put(e.getUUID(), 0);
                e.addEffect(new MobEffectInstance(CoreRegistries.FROZEN, 40, 0), ctx.caster());
                FaUtil.around(sl, e, Vfx.snowflake(), 14, 0.05);
                FaUtil.sound(sl, e.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.6F, 1.0F);
            }
            if (age % 20 == 0) hurt(e, 1.5F);
        }
        if (age % 40 == 0) FaUtil.sound(sl, position(), SoundEvents.POWDER_SNOW_STEP, 0.8F, 0.6F);
    }

    private void tickSlippery(ServerLevel sl) {
        if (age % 5 != 0) return;
        for (LivingEntity e : victims(0.6, 1.5)) {
            if (!e.onGround()) continue;
            double a = random.nextDouble() * Math.PI * 2;
            double s = 0.08 + e.getDeltaMovement().horizontalDistance() * 0.6;
            FaUtil.addMotion(e, new Vec3(Math.cos(a) * s, 0, Math.sin(a) * s).scale(FaUtil.kbFactor(e)));
            if (random.nextFloat() < 0.07F && !e.hasEffect(CoreRegistries.STUNNED)) {
                // Escorregou e caiu
                e.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 18, 0), ctx.caster());
                hurt(e, 1.5F);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState()),
                        e.getX(), e.getY() + 0.1, e.getZ(), 12, 0.3, 0.05, 0.3, 0.1);
                FaUtil.sound(sl, e.position(), SoundEvents.PLAYER_SMALL_FALL, 0.8F, 0.9F);
            }
        }
    }

    private void tickSandstorm(ServerLevel sl) {
        if (age % 10 != 0) return;
        Vec3 c = position();
        for (LivingEntity e : victims(2.0, 5.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), ctx.caster());
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), ctx.caster());
            Vec3 rel = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            if (rel.lengthSqr() > 0.01) {
                Vec3 tangent = new Vec3(-rel.z, 0, rel.x).normalize();
                FaUtil.addMotion(e, tangent.scale(0.18).add(rel.normalize().scale(-0.04)).scale(FaUtil.kbFactor(e)));
            }
            if (age % 20 == 0) hurt(e, 1.5F);
        }
        if (age % 30 == 0) FaUtil.sound(sl, c, SoundEvents.SAND_BREAK, 0.6F, 0.6F);
        if (age % 45 == 0) FaUtil.sound(sl, c, SoundEvents.SAND_FALL, 0.8F, 0.5F);
    }

    private void tickSinkhole(ServerLevel sl) {
        if (age % 4 != 0) return;
        Vec3 c = position();
        for (LivingEntity e : victims(3.0, 1.5)) {
            Vec3 in = new Vec3(c.x - e.getX(), 0, c.z - e.getZ());
            boolean inSand = sl.getBlockState(e.blockPosition()).is(FaRegistries.SINKING_SAND.get())
                    || sl.getBlockState(BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ())).is(FaRegistries.SINKING_SAND.get());
            if (!inSand) continue;
            Vec3 pull = in.lengthSqr() > 0.04 ? in.normalize().scale(0.05) : Vec3.ZERO;
            FaUtil.addMotion(e, new Vec3(pull.x, -0.04, pull.z));
            if (age % 20 == 0) {
                hurt(e, 2.0F);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                        e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
            }
        }
        if (age % 24 == 0) FaUtil.sound(sl, c, SoundEvents.SAND_STEP, 0.7F, 0.6F);
    }

    private void tickCrown(ServerLevel sl) {
        if (age % 20 != 0) return;
        Entity a = anchorId != null ? sl.getEntity(anchorId) : null;
        Vec3 crown = a != null ? a.position().add(0, a.getBbHeight() + 0.3, 0) : position().add(0, 2, 0);
        for (LivingEntity e : ctx.enemiesAround(position().add(0, 1, 0), radius())) {
            if (a != null && e == a) continue;
            float dmg = FaUtil.isUndead(e) ? 3.0F : 2.0F;
            hurt(e, dmg);
            e.igniteForSeconds(2);
            FaUtil.line(sl, crown, e.position().add(0, e.getBbHeight() * 0.6, 0), FaUtil.dust(0xFFC83A, 0.8F), 4);
            sl.sendParticles(Vfx.flame(), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
        }
    }

    private void tickSpores(ServerLevel sl) {
        if (age % 10 != 0) return;
        for (LivingEntity e : victims(1.0, 2.5)) {
            // Quem dorme pelos esporos continua dormindo; os acordados respiram veneno
            if (!e.hasEffect(FaRegistries.SPORE_SLEEP)) e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), ctx.caster());
        }
    }

    // =====================================================================================
    //  Partículas no cliente
    // =====================================================================================

    private void clientFx() {
        RandomSource r = random;
        boolean reduced = FaUtil.reducedParticles();
        float rad = radius();
        Vec3 c = position();
        int anchorNet = entityData.get(DATA_ANCHOR);
        if (anchorNet >= 0) {
            Entity a = level().getEntity(anchorNet);
            if (a != null) c = a.position();
        }
        double area = Math.max(1.0, rad * rad);
        int n;
        switch (kind()) {
            case LAVA_POOL -> {
                n = density(area * 0.5, reduced);
                for (int i = 0; i < n; i++) {
                    Vec3 p = randomIn(c, rad, r);
                    if (r.nextInt(5) == 0) level().addParticle(ParticleTypes.LAVA, p.x, c.y + 0.15, p.z, 0, 0, 0);
                    level().addParticle(r.nextBoolean() ? Vfx.smallFlame() : Vfx.smoke(), p.x, c.y + 0.15, p.z, 0, 0.03, 0);
                }
            }
            case HOT_MIST -> {
                n = density(area * 0.8, reduced);
                for (int i = 0; i < n; i++) {
                    Vec3 p = randomIn(c, rad, r);
                    double y = c.y + r.nextDouble() * 2.6;
                    level().addParticle(Vfx.cloud(), p.x, y, p.z, (r.nextDouble() - 0.5) * 0.02, 0.01, (r.nextDouble() - 0.5) * 0.02);
                    if (r.nextInt(3) == 0) level().addParticle(FaUtil.dust(0xF2F6F8, 2.5F), p.x, y, p.z, 0, 0, 0);
                }
            }
            case ERUPTION -> {
                n = density(area * 0.3, reduced);
                for (int i = 0; i < n; i++) {
                    Vec3 p = randomIn(c, rad, r);
                    level().addParticle(FaUtil.dust(r.nextBoolean() ? 0xFF4500 : 0x5A1E0A, 1.2F), p.x, c.y + 0.08, p.z, 0, 0, 0);
                    if (r.nextInt(4) == 0) level().addParticle(ParticleTypes.SMOKE, p.x, c.y + 0.1, p.z, 0, 0.04, 0);
                }
            }
            case THUNDERSTORM -> {
                double cy = c.y + 7.0;
                n = density(area * 0.6, reduced);
                for (int i = 0; i < n; i++) {
                    Vec3 p = randomIn(c, rad * 1.1, r);
                    level().addParticle(FaUtil.dust(r.nextBoolean() ? 0x3A3F4A : 0x5A6070, 3.5F), p.x, cy + r.nextDouble() * 0.8, p.z, 0, 0, 0);
                    if (r.nextInt(3) == 0) level().addParticle(ParticleTypes.FALLING_WATER, p.x, cy - 0.3, p.z, 0, 0, 0);
                    if (r.nextInt(25) == 0) level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, cy, p.z, 0, -0.1, 0);
                }
            }
            case BLIZZARD -> {
                n = density(area * 0.9, reduced);
                double t = tickCount * 0.08;
                for (int i = 0; i < n; i++) {
                    Vec3 p = randomIn(c, rad, r);
                    double y = c.y + r.nextDouble() * 3.5;
                    level().addParticle(Vfx.snowflake(), p.x, y, p.z, Math.cos(t) * 0.25, -0.05, Math.sin(t) * 0.25);
                    if (r.nextInt(3) == 0) level().addParticle(FaUtil.dust(0xFFFFFF, 1.2F), p.x, y, p.z, 0, 0, 0);
                }
            }
            case SLIPPERY -> {
                n = density(area * 0.15, reduced);
                for (int i = 0; i < n; i++) {
                    Vec3 p = randomIn(c, rad, r);
                    level().addParticle(r.nextBoolean() ? Vfx.snowflake() : Vfx.waxOff(), p.x, c.y + 0.1, p.z, 0, 0.01, 0);
                }
            }
            case SANDSTORM -> {
                n = density(area * 1.0, reduced);
                ParticleOptions sand = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * rad;
                    double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d, y = c.y + r.nextDouble() * 4.0;
                    double sp = 0.35;
                    level().addParticle(r.nextInt(3) == 0 ? sand : FaUtil.dust(r.nextBoolean() ? 0xE3C77A : 0xC9A24E, 1.6F),
                            x, y, z, -Math.sin(a) * sp, 0.02, Math.cos(a) * sp);
                }
            }
            case SINKHOLE -> {
                n = density(area * 0.5, reduced);
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble() * Math.PI * 2, d = rad * (0.4 + r.nextDouble() * 0.6);
                    double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
                    level().addParticle(FaUtil.dust(0xD4B26A, 1.1F), x, c.y + 0.05, z, 0, 0, 0);
                    if (r.nextInt(3) == 0) {
                        level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                                x, c.y + 0.1, z, (c.x - x) * 0.08, 0.02, (c.z - z) * 0.08);
                    }
                }
            }
            case SOLAR_CROWN -> {
                double h = c.y + 0.3;
                if (anchorNet >= 0) {
                    Entity a = level().getEntity(anchorNet);
                    if (a != null) h = a.getY() + a.getBbHeight() + 0.3;
                }
                double spin = tickCount * 0.12;
                int pts = reduced ? 3 : 7;
                for (int i = 0; i < pts; i++) {
                    double a = spin + Math.PI * 2 * i / pts;
                    double x = c.x + Math.cos(a) * 0.38, z = c.z + Math.sin(a) * 0.38;
                    level().addParticle(FaUtil.dust(0xFFC83A, 0.9F), x, h, z, 0, 0, 0);
                    if (tickCount % 4 == 0) level().addParticle(ParticleTypes.SMALL_FLAME, x, h + 0.12, z, 0, 0.01, 0);
                }
                if (tickCount % 6 == 0) {
                    int ring = reduced ? 8 : 20;
                    for (int i = 0; i < ring; i++) {
                        double a = -spin * 0.5 + Math.PI * 2 * i / ring;
                        level().addParticle(FaUtil.dust(0xFF9A1F, 1.0F), c.x + Math.cos(a) * rad, c.y + 0.15, c.z + Math.sin(a) * rad, 0, 0, 0);
                    }
                }
            }
            case SPORE_CLOUD -> {
                n = density(area * 0.7, reduced);
                for (int i = 0; i < n; i++) {
                    Vec3 p = randomIn(c, rad, r);
                    double y = c.y + r.nextDouble() * 2.2;
                    level().addParticle(r.nextBoolean() ? Vfx.sporeAir() : Vfx.crimsonSpore(), p.x, y, p.z, 0, 0, 0);
                    if (r.nextInt(3) == 0) level().addParticle(FaUtil.dust(r.nextBoolean() ? 0x9C6BD8 : 0x7FBF4A, 1.3F), p.x, y, p.z, 0, 0, 0);
                }
            }
        }
    }

    private static int density(double base, boolean reduced) {
        int n = (int) Math.ceil(Math.min(40, base));
        return reduced ? Math.max(1, n / 3) : n;
    }

    private static Vec3 randomIn(Vec3 c, double r, RandomSource rnd) {
        double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
        return new Vec3(c.x + Math.cos(a) * d, c.y, c.z + Math.sin(a) * d);
    }

    // =====================================================================================
    //  Regras de entidade "fantasma"
    // =====================================================================================

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) { return true; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_KIND, tag.getInt("Kind"));
        entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
        life = tag.getInt("Life");
        age = tag.getInt("Age");
        ctxTag = tag.getCompound("Spell");
        glyphId = ResourceLocation.tryParse(tag.getString("Glyph"));
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        anchorId = tag.hasUUID("Anchor") ? tag.getUUID("Anchor") : null;
        ctx = null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kind", entityData.get(DATA_KIND));
        tag.putFloat("Radius", radius());
        tag.putInt("Life", life);
        tag.putInt("Age", age);
        if (ctx != null) ctxTag = ctx.save();
        if (ctxTag != null) tag.put("Spell", ctxTag);
        if (glyphId != null) tag.putString("Glyph", glyphId.toString());
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (anchorId != null) tag.putUUID("Anchor", anchorId);
    }
}
