package com.renanpontes.isekaimahou.feature.forms_a.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_a.FormAim;
import com.renanpontes.isekaimahou.feature.forms_a.FormsAEntities;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Onda (forma "wave"): desliza pelo chão na direção em que foi lançada, sobe degraus de 1 bloco, desce barrancos,
 * corre por cima d'água e quebra ao bater numa parede (Fantasma atravessa). Aplica a magia UMA vez em cada entidade
 * que a frente da onda tocar. A largura começa pela metade e abre nos primeiros ticks (Expandir = mais larga).
 * Visual: só partículas (renderer invisível) — crista colorida + pedaços do chão levantados.
 * <p>
 * Estágios (v0.7): avisa {@code impact} em cada criatura que toca e na parede onde quebra; {@code end} quando se desfaz
 * (fim da duração ou caiu de um barranco alto). Atravessa o alvo que o estágio anterior acabou de atingir. O contexto
 * (estágio, disparos já feitos) é salvo de novo junto com a onda.
 */
public class SpellWaveEntity extends Entity {
    private static final EntityDataAccessor<Integer> ELEMENT =
            SynchedEntityData.defineId(SpellWaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(SpellWaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH =
            SynchedEntityData.defineId(SpellWaveEntity.class, EntityDataSerializers.FLOAT);

    public static final double BASE_SPEED = 0.55;
    public static final int BASE_LIFE = 40;
    public static final float BASE_WIDTH = 3.0F;
    /** Degrau máximo que a onda sobe sozinha. */
    private static final double STEP_UP = 1.1;
    /** Queda máxima "colada" no chão por tick; acima disso ela cai em arco. */
    private static final double MAX_DROP = 3.0;

    private CompoundTag spellTag;
    private SpellContext ctx;
    private Vec3 dir = new Vec3(0, 0, 1);
    private double speed = BASE_SPEED;
    private float maxWidth = BASE_WIDTH;
    private float height = 1.6F;
    private int life = BASE_LIFE;
    private int age;
    private int airTicks;
    private boolean ghost;
    private boolean homing;
    private BlockPos lastGround;
    private final Set<UUID> hit = new HashSet<>();

    public SpellWaveEntity(EntityType<? extends SpellWaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Cria a onda no ponto {@code pos} indo para {@code direction} (horizontal). Depois chame addFreshEntity. */
    public static SpellWaveEntity create(SpellContext ctx, Vec3 pos, Vec3 direction) {
        SpellWaveEntity w = new SpellWaveEntity(FormsAEntities.SPELL_WAVE.get(), ctx.level());
        SpellStats s = ctx.formStats();
        w.ctx = ctx;
        w.spellTag = ctx.save();
        w.dir = new Vec3(direction.x, 0, direction.z).normalize();
        w.speed = BASE_SPEED * s.speedMult();
        w.maxWidth = s.radius(BASE_WIDTH);
        w.height = 1.6F + 0.4F * Math.max(0, s.aoe);
        w.life = s.duration(BASE_LIFE);
        w.ghost = s.ghost;
        w.homing = s.homing;
        // Quem o estágio anterior acabou de atingir não é atingido de novo por esta onda (ela nasce nele)
        UUID prev = ctx.previousTarget();
        if (prev != null) w.hit.add(prev);
        w.setPos(pos.x, pos.y, pos.z);
        w.faceDir();
        w.entityData.set(ELEMENT, ctx.element().ordinal());
        w.entityData.set(COLOR, ctx.color());
        w.entityData.set(WIDTH, w.maxWidth * 0.5F);
        return w;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ELEMENT, 0);
        builder.define(COLOR, Element.ARCANE.argb());
        builder.define(WIDTH, BASE_WIDTH);
    }

    public Element element() {
        Element[] v = Element.values();
        int i = entityData.get(ELEMENT);
        return i >= 0 && i < v.length ? v[i] : Element.ARCANE;
    }

    public int color() { return entityData.get(COLOR); }

    public float width() { return entityData.get(WIDTH); }

    private SpellContext context() {
        if (ctx == null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag);
        return ctx;
    }

    private void faceDir() {
        setYRot((float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
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
        SpellContext c = context();
        // Contexto inválido, ou preso a outro mundo (a magia aplicaria nos alvos da dimensão errada): some
        if (c == null || c.level() != level()) {
            discard();
            return;
        }
        if (++age > life) {
            FormsFx.burst(c, position().add(0, 0.4, 0), 10, maxWidth * 0.2);
            // A onda se desfez: Ao Terminar nasce aqui, seguindo a mesma direção
            c.end(position().add(0, 0.5, 0), dir);
            discard();
            return;
        }
        float w = maxWidth * Math.min(1.0F, 0.5F + age / 16.0F);
        entityData.set(WIDTH, w);
        if (homing) steer(c);

        Vec3 next = position().add(dir.scale(speed));
        double g = FormAim.groundHeight(level(), next.x, getY(), next.z, STEP_UP, MAX_DROP);
        if (ghost) {
            // Fantasma: continua colada no chão quando há chão, mas atravessa paredes e vãos na mesma altura
            if (!Double.isNaN(g) && g <= getY() + STEP_UP) next = new Vec3(next.x, g, next.z);
        } else if (Double.isNaN(g)) {
            // Sem chão perto: despenca (e some se cair demais)
            next = new Vec3(next.x, getY() - 0.8, next.z);
            if (++airTicks > 12) {
                c.end(position().add(0, 0.5, 0), dir);
                discard();
                return;
            }
        } else if (g > getY() + STEP_UP) {
            crash(c, next);
            return;
        } else {
            next = new Vec3(next.x, g, next.z);
            airTicks = 0;
        }
        setPos(next.x, next.y, next.z);
        faceDir();
        hitEntities(c, w);
        touchGround(c);
        if (age % 6 == 1) FormsFx.sound(c, position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.45F, 0.6F);
    }

    /** Teleguiado: curva suave em direção ao alvo (quem a magia afeta) mais perto à frente. */
    private void steer(SpellContext c) {
        LivingEntity best = null;
        double bestD = 12 * 12;
        for (LivingEntity e : FormAim.targetsAround(c, position(), 12)) {
            if (hit.contains(e.getUUID())) continue;
            Vec3 to = e.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() < 1.0E-4 || flat.normalize().dot(dir) < 0.3) continue;
            double d = flat.lengthSqr();
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) return;
        Vec3 to = best.position().subtract(position());
        dir = dir.lerp(new Vec3(to.x, 0, to.z).normalize(), 0.15).normalize();
    }

    /** Aplica nas entidades na faixa varrida pela frente da onda neste tick (uma vez por entidade). */
    private void hitEntities(SpellContext c, float w) {
        Vec3 pos = position();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        double reach = w / 2.0 + speed + 1.5;
        AABB box = new AABB(pos.x - reach, pos.y - 0.6, pos.z - reach, pos.x + reach, pos.y + height, pos.z + reach);
        LivingEntity caster = c.caster();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && e != caster && !hit.contains(e.getUUID()))) {
            Vec3 rel = e.position().subtract(pos);
            double halfW = e.getBbWidth() / 2.0;
            if (Math.abs(rel.dot(side)) > w / 2.0 + halfW) continue;
            double along = rel.dot(dir);
            if (along < -speed - 0.6 - halfW || along > 0.6 + halfW) continue;
            // Quem a magia não afeta (aliado numa onda de dano) é atravessado sem estouro visual
            if (!FormAim.affects(c, e)) continue;
            hit.add(e.getUUID());
            // Impacto real (a onda tocou a criatura): Ao Acertar/Saltar partem dela, na direção da onda
            c.impact(FormAim.center(e), dir, e);
            c.resolveOnEntity(e, this);
            FormsFx.burst(c, FormAim.center(e), 10, 0.3);
        }
    }

    /** Efeitos de bloco (Sensível ou efeitos que pedem blocos) no chão por onde a onda passa, 1 vez por bloco. */
    private void touchGround(SpellContext c) {
        BlockPos below = BlockPos.containing(getX(), getY() - 0.2, getZ());
        if (below.equals(lastGround)) return;
        lastGround = below;
        if (!level().getBlockState(below).isAir()) {
            c.resolveOnBlock(new BlockHitResult(position(), Direction.UP, below, false), this);
        }
    }

    /** Bateu numa parede: estoura, aplica no bloco da parede e some. */
    private void crash(SpellContext c, Vec3 at) {
        BlockPos wall = BlockPos.containing(at.x, getY() + 0.5, at.z);
        if (level().getBlockState(wall).getCollisionShape(level(), wall).isEmpty()) wall = wall.above();
        Vec3 hitPos = new Vec3(at.x, getY() + 0.6, at.z);
        // Quebrar na parede é um impacto real (em bloco); raspar o chão pelo caminho não é
        c.impact(hitPos, dir, null);
        c.resolveOnBlock(new BlockHitResult(hitPos, Direction.getNearest(-dir.x, 0, -dir.z), wall, false), this);
        FormsFx.burst(c, hitPos, 24, maxWidth * 0.25);
        BlockState st = level().getBlockState(wall);
        if (st.getRenderShape() != RenderShape.INVISIBLE) {
            FormsFx.send((ServerLevel) level(), new BlockParticleOption(ParticleTypes.BLOCK, st), hitPos, 20, maxWidth * 0.2, 0.15);
        }
        FormsFx.sound(c, hitPos, SoundEvents.GENERIC_EXPLODE, 0.4F, 1.6F);
        discard();
    }

    /** A onda é presa ao mundo onde foi conjurada: não entra em portais. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }

    // =====================================================================================
    //  Visual (cliente)
    // =====================================================================================
    private void clientFx() {
        Level level = level();
        float w = width();
        Vec3 d = Vec3.directionFromRotation(0, getYRot());
        Vec3 side = new Vec3(-d.z, 0, d.x);
        ParticleOptions crest = FormsFx.dust(color(), 1.5F);
        ParticleOptions foam = FormsFx.dust(FormsFx.lighten(color(), 0.55F), 0.9F);
        ParticleOptions sig = FormsFx.jet(element());
        boolean reduced = FormsFx.reducedParticles();
        int n = Math.max(5, (int) (w * (reduced ? 1.5F : 3.5F)));
        for (int i = 0; i <= n; i++) {
            double t = (i / (double) n - 0.5) * w;
            double k = 1.0 - Math.pow(2 * t / Math.max(0.1, w), 2);
            double h = 0.35 + 0.85 * k;
            double x = getX() + side.x * t + d.x * random.nextDouble() * 0.3;
            double z = getZ() + side.z * t + d.z * random.nextDouble() * 0.3;
            double y = getY() + random.nextDouble() * h;
            level.addParticle(crest, x, y, z, 0, 0, 0);
            if (random.nextFloat() < 0.5F) level.addParticle(foam, x, getY() + h, z, 0, 0, 0);
            if (random.nextFloat() < 0.3F) {
                level.addParticle(sig, x, getY() + 0.1, z, d.x * 0.08, 0.06 + random.nextDouble() * 0.1, d.z * 0.08);
            }
            if (random.nextFloat() < 0.22F) groundBits(level, x, z, d);
        }
        // Rastro baixo atrás da crista
        for (int i = 0; i < (reduced ? 1 : 3); i++) {
            double t = (random.nextDouble() - 0.5) * w;
            level.addParticle(crest, getX() + side.x * t - d.x * 0.9, getY() + 0.1, getZ() + side.z * t - d.z * 0.9, 0, 0, 0);
        }
    }

    /** Pedaços do chão (ou respingos, sobre líquido) jogados para cima pela onda. */
    private void groundBits(Level level, double x, double z, Vec3 d) {
        BlockPos p = BlockPos.containing(x, getY() - 0.2, z);
        BlockState st = level.getBlockState(p);
        if (!st.getFluidState().isEmpty()) {
            level.addParticle(ParticleTypes.SPLASH, x, getY() + 0.1, z, d.x * 0.2, 0.3, d.z * 0.2);
        } else if (!st.isAir() && st.getRenderShape() != RenderShape.INVISIBLE) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), x, getY() + 0.1, z, d.x * 0.25, 0.3, d.z * 0.25);
        }
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // Salva o contexto ATUAL (estágio, disparos de ligação já feitos), não só o do momento em que nasceu
        if (ctx != null) tag.put("Spell", ctx.save());
        else if (spellTag != null) tag.put("Spell", spellTag);
        tag.putDouble("DirX", dir.x);
        tag.putDouble("DirZ", dir.z);
        tag.putDouble("Speed", speed);
        tag.putFloat("MaxWidth", maxWidth);
        tag.putFloat("Height", height);
        tag.putInt("Life", life);
        tag.putInt("Age", age);
        tag.putBoolean("Ghost", ghost);
        tag.putBoolean("Homing", homing);
        tag.putInt("Color", color());
        ListTag list = new ListTag();
        for (UUID id : hit) list.add(NbtUtils.createUUID(id));
        tag.put("Hit", list);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        spellTag = tag.getCompound("Spell");
        Vec3 d = new Vec3(tag.getDouble("DirX"), 0, tag.getDouble("DirZ"));
        dir = d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
        speed = tag.contains("Speed") ? tag.getDouble("Speed") : BASE_SPEED;
        maxWidth = tag.contains("MaxWidth") ? tag.getFloat("MaxWidth") : BASE_WIDTH;
        height = tag.contains("Height") ? tag.getFloat("Height") : 1.6F;
        life = tag.contains("Life") ? tag.getInt("Life") : BASE_LIFE;
        age = tag.getInt("Age");
        ghost = tag.getBoolean("Ghost");
        homing = tag.getBoolean("Homing");
        hit.clear();
        for (Tag t : tag.getList("Hit", Tag.TAG_INT_ARRAY)) hit.add(NbtUtils.loadUUID(t));
        if (tag.contains("Color")) entityData.set(COLOR, tag.getInt("Color"));
        Element e = Element.byId(spellTag.getString("element"));
        if (e != null) entityData.set(ELEMENT, e.ordinal());
        faceDir();
    }
}
