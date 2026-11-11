package com.renanpontes.isekaimahou.feature.class_shinigami.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Projéteis de Reiatsu do Shinigami, com dois tipos:
 * <ul>
 *   <li>{@link #MOON} — a Presa Lunar: meia-lua larga que atravessa TODOS os inimigos no caminho (cada um uma vez),
 *   para nos blocos (a menos que a magia tenha Fantasma);</li>
 *   <li>{@link #CANNON} — o Canhão Rubro (Hadō nº 31): esfera vermelha que explode no primeiro acerto, em área;</li>
 *   <li>{@link #GETSUGA} / {@link #GETSUGA_BLACK} — o Getsuga Tenshō: lua crescente ENORME na diagonal (branca, ou negra
 *   com borda rubra durante a Bankai/Máscara), atravessa todos os inimigos e corta com dano próprio;</li>
 *   <li>{@link #MUGETSU} — a escuridão do Mugetsu: uma parede vertical de trevas que atravessa blocos e corta tudo.</li>
 * </ul>
 * Carregam o {@link SpellContext} (salvo junto, para sobreviver a um salvamento). Vida máxima limitada; somem sozinhos.
 */
public class ReishiWaveEntity extends Projectile {
    public static final int MOON = 0;
    public static final int CANNON = 1;
    public static final int GETSUGA = 2;
    public static final int GETSUGA_BLACK = 3;
    public static final int MUGETSU = 4;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ReishiWaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(ReishiWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ReishiWaveEntity.class, EntityDataSerializers.INT);

    @Nullable
    private CompoundTag spellTag;
    @Nullable
    private SpellContext ctx;
    private int life = 40;
    private float blastRadius = 2.5F;
    private boolean ghost;
    /** Dano próprio da onda (antes da escala do núcleo), aplicado em cada inimigo cortado, além dos efeitos. */
    private float bonusDamage;
    private final Set<UUID> hit = new HashSet<>();

    public ReishiWaveEntity(EntityType<? extends ReishiWaveEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** Cria o projétil que carrega a magia (depois: {@code shoot} e {@code addFreshEntity}). */
    public static ReishiWaveEntity spell(SpellContext ctx, LivingEntity shooter, Vec3 from, int kind, float width, int life, int rgb) {
        ReishiWaveEntity w = new ReishiWaveEntity(ShinigamiRegistries.REISHI_WAVE.get(), ctx.level());
        w.setOwner(shooter);
        w.setPos(from.x, from.y, from.z);
        w.ctx = ctx;
        w.spellTag = ctx.save();
        if (ctx.previousTarget() != null) w.hit.add(ctx.previousTarget());
        SpellStats s = ctx.formStats();
        w.ghost = s.ghost;
        w.life = life;
        w.blastRadius = s.radius(2.5F);
        w.entityData.set(KIND, kind);
        w.entityData.set(WIDTH, width);
        w.entityData.set(COLOR, rgb);
        return w;
    }

    /** Dano próprio da onda (Getsuga/Mugetsu); {@code throughBlocks} faz a onda atravessar blocos. */
    public ReishiWaveEntity bonus(float damage, boolean throughBlocks) {
        this.bonusDamage = Math.max(0F, damage);
        this.ghost |= throughBlocks;
        return this;
    }

    public int kind() {
        return entityData.get(KIND);
    }

    /** Ondas largas (meia-lua, Getsuga, Mugetsu) — não explodem, cortam tudo no caminho. */
    public boolean isWave() {
        return kind() != CANNON;
    }

    public float width() {
        return entityData.get(WIDTH);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, MOON);
        builder.define(WIDTH, 3.0F);
        builder.define(COLOR, 0x8FE0FF);
    }

    @Nullable
    private SpellContext context() {
        if (ctx == null && spellTag != null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag);
        return ctx;
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        super.shoot(x, y, z, velocity, inaccuracy);
        faceMotion();
    }

    private void faceMotion() {
        Vec3 v = getDeltaMovement();
        if (v.lengthSqr() < 1.0E-6) return;
        setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 pos = position();
        Vec3 v = getDeltaMovement();
        if (level().isClientSide) {
            clientTrail(pos, v);
            setPos(pos.add(v));
            return;
        }
        if (--life <= 0) {
            finish(pos, v, null);
            return;
        }
        SpellContext c = context();
        if (c == null) {
            discard();
            return;
        }
        Vec3 next = pos.add(v);
        // blocos
        BlockHitResult block = null;
        if (!ghost) {
            HitResult r = level().clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (r.getType() == HitResult.Type.BLOCK) {
                block = (BlockHitResult) r;
                next = r.getLocation();
            }
        }
        // entidades no caminho (largura = da meia-lua)
        float half = isWave() ? width() / 2 : 0.6F;
        double tall = switch (kind()) {
            case MOON -> 0.9;
            case GETSUGA, GETSUGA_BLACK -> Math.max(1.2, width() * 0.4);
            case MUGETSU -> Math.max(3.0, width() * 0.5);
            default -> 0.6;
        };
        AABB swept = new AABB(pos, next).inflate(half, tall, half);
        for (Entity e : level().getEntities(this, swept, this::canHit)) {
            if (kind() == CANNON) {
                explode(c, e.position().add(0, e.getBbHeight() / 2, 0), v, e);
                return;
            }
            hit.add(e.getUUID());
            c.impact(e.position().add(0, e.getBbHeight() / 2, 0), v, e);
            if (bonusDamage > 0 && e.isAlive()) c.damage(e, bonusDamage, c.formStats(), this);
            if (e.isAlive()) c.resolveOnEntity(e, this);
            ServerLevel sl = (ServerLevel) level();
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, 0, 0, 0, 0);
            if (kind() == GETSUGA_BLACK || kind() == MUGETSU) {
                sl.sendParticles(ShinigamiFx.BLACK, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
            } else if (kind() == GETSUGA) {
                sl.sendParticles(ShinigamiFx.SPIRIT, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
            }
            ShinigamiFx.sound(sl, e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, kind() == MOON ? 0.7F : 0.5F);
        }
        if (block != null) {
            if (kind() == CANNON) {
                explode(c, block.getLocation(), v, null);
            } else {
                c.impact(block.getLocation(), v, null);
                c.resolveOnBlock(block, this);
                finish(block.getLocation(), v, null);
            }
            return;
        }
        setPos(next);
        faceMotion();
        // a onda rasga o chão por onde passa (cicatriz + blocos voando)
        if (tickCount % 2 == 0 && (kind() == GETSUGA || kind() == GETSUGA_BLACK || kind() == MUGETSU)) {
            double drop = kind() == MUGETSU ? width() * 0.5 : width() * 0.42;
            int rgb = kind() == GETSUGA ? 0x8FE0FF : kind() == GETSUGA_BLACK ? 0xC0102A : 0x3A1450;
            com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiVfx.groundScar((ServerLevel) level(),
                    getOwner() instanceof LivingEntity o ? o : null, next.subtract(0, drop, 0), v, Math.min(6F, width() * 0.35F), rgb);
        }
    }

    private boolean canHit(Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || hit.contains(e.getUUID())) return false;
        Entity owner = getOwner();
        if (e == owner) return false;
        SpellContext c = context();
        return c == null || !c.isAlly(e);
    }

    private void explode(SpellContext c, Vec3 at, Vec3 dir, @Nullable Entity direct) {
        ServerLevel sl = (ServerLevel) level();
        c.impact(at, dir, direct);
        c.resolveArea(at, blastRadius, this, false);
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ShinigamiFx.KIDO_RED, at.x, at.y, at.z, 30, blastRadius * 0.4, blastRadius * 0.4, blastRadius * 0.4, 0.05);
        sl.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 16, blastRadius * 0.3, blastRadius * 0.3, blastRadius * 0.3, 0.06);
        ShinigamiFx.sound(sl, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.3F);
        c.end(at, dir);
        discard();
    }

    private void finish(Vec3 at, Vec3 dir, @Nullable Entity direct) {
        SpellContext c = context();
        if (c != null) c.end(at, dir);
        if (level() instanceof ServerLevel sl) {
            DustParticleOptions p = switch (kind()) {
                case CANNON -> ShinigamiFx.KIDO_RED;
                case GETSUGA_BLACK, MUGETSU -> ShinigamiFx.BLACK;
                default -> ShinigamiFx.SPIRIT;
            };
            int n = kind() == MUGETSU ? 60 : kind() == MOON || kind() == CANNON ? 10 : 24;
            double spread = Math.max(0.4, width() * 0.25);
            sl.sendParticles(p, at.x, at.y, at.z, n, spread, spread * 0.6, spread, 0.02);
        }
        discard();
    }

    private void clientTrail(Vec3 pos, Vec3 v) {
        int rgb = color();
        DustParticleOptions dust = ShinigamiFx.dust(rgb, kind() == CANNON ? 1.4F : 1.0F);
        if (kind() == CANNON) {
            for (int i = 0; i < 3; i++) {
                level().addParticle(dust, pos.x - v.x * i / 3 + (random.nextDouble() - 0.5) * 0.3, pos.y + (random.nextDouble() - 0.5) * 0.3,
                        pos.z - v.z * i / 3 + (random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
            }
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 0, 0, 0);
            return;
        }
        if (kind() != MOON) {
            bigTrail(pos, v);
            return;
        }
        // pontas da meia-lua deixando rastro
        Vec3 dir = v.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : v.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        float half = width() / 2;
        for (int k = -2; k <= 2; k++) {
            double t = k / 2.0;
            Vec3 p = pos.add(side.scale(t * half)).subtract(dir.scale(Math.abs(t) * 0.6));
            level().addParticle(dust, p.x, p.y + Math.abs(t) * 0.3, p.z, 0, 0, 0);
        }
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0, 0, 0);
    }

    /** Rastro do Getsuga (diagonal, branco/negro) e da escuridão do Mugetsu (parede vertical). */
    private void bigTrail(Vec3 pos, Vec3 v) {
        Vec3 dir = v.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : v.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        float half = width() / 2;
        boolean mugetsu = kind() == MUGETSU;
        boolean black = kind() == GETSUGA_BLACK || mugetsu;
        DustParticleOptions core = ShinigamiFx.dust(black ? 0x0A0608 : 0xF4FAFF, mugetsu ? 2.4F : 1.6F);
        DustParticleOptions rim = ShinigamiFx.dust(black ? (mugetsu ? 0x3A1450 : 0xC0102A) : 0x8FE0FF, 1.2F);
        int n = mugetsu ? 10 : 6;
        for (int k = 0; k < n; k++) {
            double t = random.nextDouble() * 2 - 1;
            Vec3 p;
            if (mugetsu) {
                // parede vertical: a largura vira altura
                p = pos.add(side.scale(t * 0.6)).add(0, t * half, 0).subtract(dir.scale(random.nextDouble() * 1.5));
            } else {
                // diagonal, como o corte da espada
                p = pos.add(side.scale(t * half * 0.7)).add(0, t * half * 0.7, 0).subtract(dir.scale(Math.abs(t) * 0.8));
            }
            level().addParticle(k % 3 == 0 ? rim : core, p.x, p.y, p.z, 0, 0, 0);
        }
        if (tickCount % 2 == 0) level().addParticle(black ? ParticleTypes.SMOKE : ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0, 0, 0);
        if (mugetsu && tickCount % 3 == 0) level().addParticle(ParticleTypes.SQUID_INK, pos.x, pos.y, pos.z, 0, 0.05, 0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ctx != null) tag.put("Spell", ctx.save());
        else if (spellTag != null) tag.put("Spell", spellTag);
        tag.putInt("Life", life);
        tag.putFloat("Blast", blastRadius);
        tag.putBoolean("Ghost", ghost);
        tag.putFloat("Bonus", bonusDamage);
        tag.putInt("Kind", kind());
        tag.putFloat("Width", width());
        tag.putInt("Color", color());
        ListTag list = new ListTag();
        for (UUID id : hit) list.add(NbtUtils.createUUID(id));
        tag.put("Hit", list);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Spell", Tag.TAG_COMPOUND)) spellTag = tag.getCompound("Spell");
        life = tag.getInt("Life");
        blastRadius = tag.getFloat("Blast");
        ghost = tag.getBoolean("Ghost");
        bonusDamage = tag.getFloat("Bonus");
        entityData.set(KIND, tag.getInt("Kind"));
        entityData.set(WIDTH, tag.contains("Width") ? tag.getFloat("Width") : 3.0F);
        entityData.set(COLOR, tag.getInt("Color"));
        hit.clear();
        for (Tag t : tag.getList("Hit", Tag.TAG_INT_ARRAY)) hit.add(NbtUtils.loadUUID(t));
    }
}
