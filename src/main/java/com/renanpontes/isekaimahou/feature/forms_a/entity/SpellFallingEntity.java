package com.renanpontes.isekaimahou.feature.forms_a.entity;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_a.FormAim;
import com.renanpontes.isekaimahou.feature.forms_a.FormsAEntities;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Queda (forma "fall"): um "meteoro" mágico que surge no alto e cai acelerando até o ponto mirado.
 * Explode ao tocar um bloco, um alvo ou o ponto: aplica a magia numa área (raio 3 × área) e no bloco do impacto.
 * Enquanto cai, marca no chão o círculo onde vai bater (dá para desviar). Teleguiado segue o alvo mirado;
 * Fantasma atravessa blocos até o ponto. Visual: núcleo brilhante (renderer) + rastro de partículas.
 * <p>
 * Estágios (v0.7): ao cair avisa {@code impact} (com a criatura mirada, se a explosão a pega; senão a que ele acertou
 * no ar ou a mais perto na explosão) e
 * {@code end} — a queda sempre termina no impacto —, na direção HORIZONTAL da queda (o próximo estágio segue "para a
 * frente", não para dentro do chão). O contexto (estágio, disparos já feitos) é salvo de novo junto com o meteoro.
 */
public class SpellFallingEntity extends Entity {
    private static final EntityDataAccessor<Integer> ELEMENT =
            SynchedEntityData.defineId(SpellFallingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(SpellFallingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(SpellFallingEntity.class, EntityDataSerializers.FLOAT);

    public static final float BASE_RADIUS = 3.0F;
    private static final int MAX_AGE = 200;

    private CompoundTag spellTag;
    private SpellContext ctx;
    private Vec3 target = Vec3.ZERO;
    private UUID targetId;
    private double speed = 0.35;
    private double accel = 0.09;
    private double maxSpeed = 2.2;
    private float radius = BASE_RADIUS;
    private boolean ghost;
    private boolean homing;
    private int age;
    /** Cliente: posição no tick anterior (xo já é igualado a x antes do tick, então não serve para o rastro). */
    private Vec3 lastClientPos;

    public SpellFallingEntity(EntityType<? extends SpellFallingEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Cria o meteoro em {@code start} caindo até {@code targetPos}. {@code targetEntity} (opcional) é seguido com Teleguiado. */
    public static SpellFallingEntity create(SpellContext ctx, Vec3 start, Vec3 targetPos, Entity targetEntity) {
        SpellFallingEntity f = new SpellFallingEntity(FormsAEntities.SPELL_FALLING.get(), ctx.level());
        SpellStats s = ctx.formStats();
        f.ctx = ctx;
        f.spellTag = ctx.save();
        f.target = targetPos;
        f.targetId = targetEntity != null ? targetEntity.getUUID() : null;
        f.accel = 0.09 * s.speedMult();
        f.maxSpeed = 2.2 * s.speedMult();
        f.radius = s.radius(BASE_RADIUS);
        f.ghost = s.ghost;
        f.homing = s.homing;
        f.setPos(start.x, start.y, start.z);
        f.entityData.set(ELEMENT, ctx.element().ordinal());
        f.entityData.set(COLOR, ctx.color());
        f.entityData.set(SIZE, 1.0F + 0.2F * Math.max(0, s.amplify + s.charge) + 0.15F * Math.max(0, s.aoe));
        return f;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ELEMENT, 0);
        builder.define(COLOR, Element.ARCANE.argb());
        builder.define(SIZE, 1.0F);
    }

    public Element element() {
        Element[] v = Element.values();
        int i = entityData.get(ELEMENT);
        return i >= 0 && i < v.length ? v[i] : Element.ARCANE;
    }

    public int color() { return entityData.get(COLOR); }

    public float size() { return entityData.get(SIZE); }

    private SpellContext context() {
        if (ctx == null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag);
        return ctx;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // Visível de longe (cai a até 48 blocos de quem conjurou)
        return distance < 160 * 160;
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
        // Contexto inválido, ou preso a outro mundo (a área cairia nos alvos da dimensão errada): some
        if (c == null || c.level() != level()) {
            discard();
            return;
        }
        age++;
        if (homing && targetId != null && level() instanceof ServerLevel sl) {
            Entity t = sl.getEntity(targetId);
            if (t != null && t.isAlive()) target = t.position();
        }
        Vec3 pos = position();
        Vec3 to = target.subtract(pos);
        double dist = to.length();
        speed = Math.min(maxSpeed, speed + accel);
        if (dist <= speed + 0.05 || age > MAX_AGE) {
            impact(c, dist <= speed + 0.05 ? target : pos, null);
            return;
        }
        Vec3 motion = to.scale(speed / dist);
        Vec3 next = pos.add(motion);

        if (!ghost) {
            BlockHitResult bh = level().clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (bh.getType() == HitResult.Type.BLOCK) {
                impact(c, bh.getLocation(), null);
                return;
            }
        }
        LivingEntity caster = c.caster();
        for (Entity e : level().getEntities(this, getBoundingBox().expandTowards(motion).inflate(0.3),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != caster && FormAim.affects(c, e))) {
            AABB box = e.getBoundingBox().inflate(0.45);
            if (box.contains(pos)) {
                impact(c, pos, e);
                return;
            }
            var p = box.clip(pos, next);
            if (p.isPresent()) {
                impact(c, p.get(), e);
                return;
            }
        }
        setDeltaMovement(motion);
        setPos(next.x, next.y, next.z);
        if (age % 4 == 1) warning(c);
        if (age % 10 == 1) FormsFx.sound(c, position(), SoundEvents.FIRECHARGE_USE, 0.5F, 0.6F);
    }

    /** Círculo no chão mostrando onde o meteoro vai cair. */
    private void warning(SpellContext c) {
        FormsFx.ring(c, target.add(0, 0.05, 0), radius, 1.2);
        FormsFx.send(c.level(), FormsFx.dust(c.color(), 1.0F), target.add(0, 0.1, 0), 3, 0.15, 0);
        // v1.1: círculo rúnico de mira no chão (renovado enquanto o meteoro cai)
        if (age % 12 == 1) {
            ElementFx.runeCircle(c.level(), target.add(0, 0.05, 0), c.color() & 0xFFFFFF, radius, 16,
                    Math.max(1, com.renanpontes.isekaimahou.glyph.fx.SpellVisuals.tier(c)));
        }
    }

    /**
     * Impacto: avisa o estágio, aplica na área + bloco, explosão visual (sem destruir terreno) e some.
     * {@code struck} = criatura em que o meteoro bateu em pleno ar (null se caiu no chão/no ponto).
     */
    private void impact(SpellContext c, Vec3 at, Entity struck) {
        ServerLevel level = (ServerLevel) level();
        // Direção horizontal da queda (o meteoro vem em diagonal do lado de quem lançou)
        Vec3 travel = getDeltaMovement().lengthSqr() > 1.0E-6 ? getDeltaMovement() : target.subtract(position());
        Vec3 flat = FormAim.flatDir(c, travel);
        // "Alvo atingido" (Ao Acertar ignora ele; Saltar parte dele): quem o meteoro mirou, se a explosão o pega —
        // com Saltar é o próximo inimigo, mesmo que o anterior tenha entrado na frente (senão a magia voltaria para
        // ele, porque a explosão em área não conta como "atingido" para o salto). Depois, quem ele acertou no ar;
        // por fim, a criatura mais perto que a explosão pega.
        Entity victim = null;
        Entity aimed = targetId != null ? level.getEntity(targetId) : null;
        if (aimed != null && aimed.isAlive() && aimed.distanceToSqr(at) <= radius * radius && FormAim.affects(c, aimed)) {
            victim = aimed;
        }
        if (victim == null) victim = struck != null ? struck : FormAim.nearestAffected(c, at, radius);
        Vec3 p = at.add(0, 0.4, 0);
        c.impact(p, flat, victim);
        c.resolveArea(at, radius, this, true);

        Vec3 up = at.add(0, 0.3, 0);
        FormsFx.burst(c, up, (int) (30 + radius * 10), radius * 0.35);
        FormsFx.send(level, radius >= 4.5F ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, up, 2, radius * 0.2, 0);
        FormsFx.send(level, Vfx.largeSmoke(), up, 16, radius * 0.3, 0.03);
        FormsFx.ring(c, at.add(0, 0.1, 0), radius, 3.0);
        FormsFx.ring(c, at.add(0, 0.1, 0), radius * 0.55, 2.5);
        BlockPos ground = BlockPos.containing(at.x, at.y - 0.2, at.z);
        BlockState st = level.getBlockState(ground);
        if (!st.isAir() && st.getRenderShape() != RenderShape.INVISIBLE) {
            FormsFx.send(level, new BlockParticleOption(ParticleTypes.BLOCK, st), up, (int) (20 + radius * 8), radius * 0.3, 0.2);
        } else if (!st.getFluidState().isEmpty()) {
            FormsFx.send(level, Vfx.splash(), up, 40, radius * 0.3, 0.3);
        }
        int jets = (int) (8 + radius * 3);
        ParticleOptions sig = FormsFx.jet(c.element());
        for (int i = 0; i < jets; i++) {
            double a = Math.PI * 2 * i / jets;
            FormsFx.moving(level, sig, up, new Vec3(Math.cos(a), 0.6, Math.sin(a)), 0.25 + radius * 0.03);
        }
        // v1.1: clarão, onda de choque do tamanho da explosão e estilhaços do elemento
        int tier = com.renanpontes.isekaimahou.glyph.fx.SpellVisuals.tier(c);
        ElementFx.impact(level, up, c.element(), c.color() & 0xFFFFFF, Math.max(2, Math.min(5, tier + 1)), new Vec3(0, 1, 0));
        ElementFx.shockwave(level, at.add(0, 0.15, 0), c.color() & 0xFFFFFF, radius * 1.4F, 14, null);
        FormsFx.sound(c, at, SoundEvents.GENERIC_EXPLODE, 1.1F, 0.95F);
        FormsFx.sound(c, at, ElementFx.castSoundOf(c.element()), 0.8F, 0.8F);
        // A queda sempre termina no impacto: Ao Terminar nasce na cratera
        c.end(p, flat);
        discard();
    }

    /** O meteoro é preso ao mundo onde foi conjurado: não entra em portais. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }

    // =====================================================================================
    //  Visual (cliente): rastro de meteoro entre a posição anterior e a atual
    // =====================================================================================
    private void clientFx() {
        Level level = level();
        float size = size();
        Vec3 cur = position().add(0, getBbHeight() / 2, 0);
        Vec3 prev = lastClientPos != null && lastClientPos.distanceToSqr(cur) < 16 * 16 ? lastClientPos : cur;
        lastClientPos = cur;
        Vec3 d = cur.subtract(prev);
        ParticleOptions glow = FormsFx.dust(color(), 2.2F * size);
        ParticleOptions hot = FormsFx.dust(FormsFx.lighten(color(), 0.6F), 1.2F * size);
        ParticleOptions sig = FormsFx.jet(element());
        int n = Math.max(3, (int) (d.length() * (FormsFx.reducedParticles() ? 1.5 : 4)));
        double j = 0.18 * size;
        for (int i = 0; i < n; i++) {
            Vec3 p = prev.add(d.scale(i / (double) n));
            double x = p.x + (random.nextDouble() - 0.5) * j, y = p.y + (random.nextDouble() - 0.5) * j,
                    z = p.z + (random.nextDouble() - 0.5) * j;
            level.addParticle(glow, x, y, z, 0, 0, 0);
            if (i % 2 == 0) level.addParticle(hot, x, y, z, 0, 0, 0);
            if (random.nextFloat() < 0.45F) level.addParticle(sig, x, y, z, (random.nextDouble() - 0.5) * 0.06, 0.02, (random.nextDouble() - 0.5) * 0.06);
            if (random.nextFloat() < 0.25F) level.addParticle(Vfx.largeSmoke(), x, y, z, 0, 0.03, 0);
        }
        // Faíscas soltando do núcleo
        for (int i = 0; i < 2; i++) {
            level.addParticle(Vfx.endRod(), cur.x, cur.y, cur.z,
                    (random.nextDouble() - 0.5) * 0.12, (random.nextDouble() - 0.2) * 0.12, (random.nextDouble() - 0.5) * 0.12);
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
        tag.putDouble("TX", target.x);
        tag.putDouble("TY", target.y);
        tag.putDouble("TZ", target.z);
        if (targetId != null) tag.putUUID("TargetId", targetId);
        tag.putDouble("Speed", speed);
        tag.putDouble("Accel", accel);
        tag.putDouble("MaxSpeed", maxSpeed);
        tag.putFloat("Radius", radius);
        tag.putBoolean("Ghost", ghost);
        tag.putBoolean("Homing", homing);
        tag.putInt("Age", age);
        tag.putInt("Color", color());
        tag.putFloat("Size", size());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        spellTag = tag.getCompound("Spell");
        target = new Vec3(tag.getDouble("TX"), tag.getDouble("TY"), tag.getDouble("TZ"));
        targetId = tag.hasUUID("TargetId") ? tag.getUUID("TargetId") : null;
        speed = tag.contains("Speed") ? tag.getDouble("Speed") : 0.35;
        accel = tag.contains("Accel") ? tag.getDouble("Accel") : 0.09;
        maxSpeed = tag.contains("MaxSpeed") ? tag.getDouble("MaxSpeed") : 2.2;
        radius = tag.contains("Radius") ? tag.getFloat("Radius") : BASE_RADIUS;
        ghost = tag.getBoolean("Ghost");
        homing = tag.getBoolean("Homing");
        age = tag.getInt("Age");
        if (tag.contains("Color")) entityData.set(COLOR, tag.getInt("Color"));
        if (tag.contains("Size")) entityData.set(SIZE, tag.getFloat("Size"));
        Element e = Element.byId(spellTag.getString("element"));
        if (e != null) entityData.set(ELEMENT, e.ordinal());
    }
}
