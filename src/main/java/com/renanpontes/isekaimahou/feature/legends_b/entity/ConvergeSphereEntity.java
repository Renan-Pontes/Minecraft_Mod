package com.renanpontes.isekaimahou.feature.legends_b.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_b.LbFx;
import com.renanpontes.isekaimahou.feature.legends_b.LbUtil;
import com.renanpontes.isekaimahou.feature.legends_b.LegendsBRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Convergência: dois orbes (cores do 1º e do 2º efeito) surgem à direita e à esquerda, giram em espiral e se
 * fundem à frente do conjurador durante {@link #chargeTicks()}; então sai uma ESFERA enorme e lenta que atravessa
 * blocos e criaturas em linha reta, aplicando os efeitos em tudo o que toca (uma vez por alvo) e, se permitido,
 * apagando os blocos do caminho (sem drops). Projéteis inimigos no caminho também somem.
 */
public class ConvergeSphereEntity extends LegendSpellEntity {
    public static final int PHASE_CHARGE = 0;
    public static final int PHASE_FLY = 1;
    public static final int PHASE_FADE = 2;
    public static final int GROW_TICKS = 6;
    public static final int FADE_TICKS = 10;
    /** Distância dos olhos até o ponto de fusão. */
    public static final double HOLD_DIST = 1.9;

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(ConvergeSphereEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_DIR = SynchedEntityData.defineId(ConvergeSphereEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(ConvergeSphereEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE_START = SynchedEntityData.defineId(ConvergeSphereEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(ConvergeSphereEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(ConvergeSphereEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(ConvergeSphereEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(ConvergeSphereEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR_A = SynchedEntityData.defineId(ConvergeSphereEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR_B = SynchedEntityData.defineId(ConvergeSphereEntity.class, EntityDataSerializers.INT);

    // ---------------- Servidor ----------------
    private boolean follow;
    private boolean erase;
    private double traveled;
    private double range = 32;
    private final Set<UUID> hit = new HashSet<>();
    private Boolean opposing;
    private static final ResourceLocation PULL = IsekaiMahou.id("pull");
    private static final ResourceLocation PUSH = IsekaiMahou.id("push");

    public ConvergeSphereEntity(EntityType<? extends ConvergeSphereEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Cria a convergência (sem adicionar ao mundo).
     * @param follow    acompanha a mão do conjurador durante a carga (false = nasce parada na origem do estágio)
     * @param yawOffset desvio horizontal em graus (Dividir abre esferas em leque)
     */
    public static ConvergeSphereEntity create(SpellContext ctx, Vec3 pos, Vec3 dir, boolean follow, float yawOffset, int chargeTicks,
                                              float radius, float speed, double range, int colorA, int colorB) {
        ConvergeSphereEntity e = new ConvergeSphereEntity(LegendsBRegistry.CONVERGE_SPHERE.get(), ctx.level());
        e.bind(ctx);
        e.follow = follow && ctx.caster() != null;
        e.range = range;
        e.setPos(pos.x, pos.y, pos.z);
        e.entityData.set(DATA_OWNER, e.follow ? ctx.caster().getId() : -1);
        e.setDir(dir);
        e.entityData.set(DATA_CHARGE, Math.max(1, chargeTicks));
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_SPEED, speed);
        e.entityData.set(DATA_YAW, yawOffset);
        e.entityData.set(DATA_COLOR_A, colorA & 0xFFFFFF);
        e.entityData.set(DATA_COLOR_B, colorB & 0xFFFFFF);
        // Estágio de magia programável: quem o estágio anterior acabou de atingir não é atingido de novo por esta esfera
        if (ctx.previousTarget() != null) e.hit.add(ctx.previousTarget());
        return e;
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_DIR, new Vector3f(0, 0, 1));
        builder.define(DATA_PHASE, PHASE_CHARGE);
        builder.define(DATA_PHASE_START, 0);
        builder.define(DATA_CHARGE, 20);
        builder.define(DATA_RADIUS, 2.0F);
        builder.define(DATA_SPEED, 0.4F);
        builder.define(DATA_YAW, 0F);
        builder.define(DATA_COLOR_A, 0x3A7BFF);
        builder.define(DATA_COLOR_B, 0xFF2A3A);
    }

    // =====================================================================================
    //  Dados (também usados pelo renderer)
    // =====================================================================================
    public int phase() { return entityData.get(DATA_PHASE); }
    public int phaseStart() { return entityData.get(DATA_PHASE_START); }
    public int chargeTicks() { return entityData.get(DATA_CHARGE); }
    public float radius() { return entityData.get(DATA_RADIUS); }
    public float speed() { return entityData.get(DATA_SPEED); }
    public float yawOffset() { return entityData.get(DATA_YAW); }
    public int colorA() { return entityData.get(DATA_COLOR_A); }
    public int colorB() { return entityData.get(DATA_COLOR_B); }

    public Vec3 dir() {
        Vector3f v = entityData.get(DATA_DIR);
        return new Vec3(v.x(), v.y(), v.z());
    }

    private void setDir(Vec3 d) {
        Vec3 n = d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
        entityData.set(DATA_DIR, new Vector3f((float) n.x, (float) n.y, (float) n.z));
    }

    /** Dono que segura a carga (cliente e servidor), ou null. */
    public Entity ownerEntity() {
        int id = entityData.get(DATA_OWNER);
        return id >= 0 ? level().getEntity(id) : null;
    }

    /** Direção de mira de quem segura a carga (com o desvio do leque). */
    public Vec3 holdDir(LivingEntity owner, float partialTick) {
        Vec3 look = level().isClientSide ? owner.getViewVector(partialTick) : LbUtil.aimDirOf(owner);
        return look.yRot((float) Math.toRadians(yawOffset()));
    }

    /** Ponto de fusão à frente de quem segura. */
    public static Vec3 holdPoint(LivingEntity owner, Vec3 dir, float partialTick) {
        return owner.getEyePosition(partialTick).add(dir.scale(HOLD_DIST)).add(0, -0.15, 0);
    }

    /**
     * Deslocamento do orbe A em relação ao ponto de fusão durante a carga (o orbe B fica no oposto).
     * Os dois começam afastados à direita/esquerda, giram em espiral cada vez mais rápido e se encontram no centro.
     */
    public static Vec3 orbOffset(Vec3 dir, float prog) {
        LbUtil.Frame f = LbUtil.Frame.of(dir);
        float p = Mth.clamp(prog, 0F, 1F);
        double ease = p * p * (3 - 2 * p);
        double sep = 1.75 * (1 - ease);
        double ang = p * p * Math.PI * 3.0;
        return f.right().scale(Math.cos(ang) * sep).add(f.up().scale(Math.sin(ang) * sep))
                .add(f.forward().scale(-0.45 * (1 - ease)));
    }

    /** Raio atual da esfera (cresce ao sair, encolhe ao sumir). */
    public float visualRadius(float ageF) {
        float t = ageF - phaseStart();
        return switch (phase()) {
            case PHASE_FLY -> radius() * Math.min(1F, 0.3F + 0.7F * t / GROW_TICKS);
            case PHASE_FADE -> radius() * Math.max(0F, 1F - t / FADE_TICKS);
            default -> 0F;
        };
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================
    @Override
    protected void serverTick(ServerLevel sl, SpellContext c) {
        switch (phase()) {
            case PHASE_CHARGE -> chargeTick(sl, c);
            case PHASE_FLY -> flyTick(sl, c);
            default -> {
                if (age - phaseStart() >= FADE_TICKS) discard();
            }
        }
        if (age > 20 * 40) discard();
    }

    private void chargeTick(ServerLevel sl, SpellContext c) {
        LivingEntity owner = c.caster();
        if (follow) {
            if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != sl) {
                fizzle(sl);
                return;
            }
            Vec3 d = holdDir(owner, 1F);
            setDir(d);
            Vec3 p = holdPoint(owner, d, 1F);
            setPos(p.x, p.y, p.z);
            // Segurar duas forças opostas pesa: o conjurador quase para durante a fusão
            if (age == 1) owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, chargeTicks() + 4, 3, false, false, true));
        }
        int ct = chargeTicks();
        float prog = age / (float) ct;
        Vec3 ctr = position();
        Vec3 off = orbOffset(dir(), prog);
        LbFx.send(sl, LbFx.dust(colorA(), 1.3F), ctr.add(off), 2, 0.07, 0);
        LbFx.send(sl, LbFx.dust(colorB(), 1.3F), ctr.subtract(off), 2, 0.07, 0);
        if (age % 2 == 0) LbFx.send(sl, ParticleTypes.PORTAL, ctr, 10, 0.1, 1.4);
        if (age == 1) {
            LbFx.sound(sl, ctr, SoundEvents.BEACON_ACTIVATE, 1.2F, 0.6F);
            LbFx.sound(sl, ctr, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.9F, 0.7F);
        }
        if (age % 4 == 0) LbFx.sound(sl, ctr, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 0.5F + prog);
        if (age == Math.max(1, ct - 6)) LbFx.sound(sl, ctr, SoundEvents.WARDEN_SONIC_CHARGE, 1.1F, 1.4F);
        if (age >= ct) launch(sl, c);
    }

    private void launch(ServerLevel sl, SpellContext c) {
        entityData.set(DATA_PHASE, PHASE_FLY);
        entityData.set(DATA_PHASE_START, age);
        syncAge();
        erase = LbUtil.erasureEnabled(c);
        if (erase && c.caster() instanceof net.minecraft.server.level.ServerPlayer sp) {
            erase = sp.mayBuild();
        }
        Vec3 p = position();
        Vec3 d = dir();
        int core = color();
        LbFx.ring(sl, p, d, radius() * 2.6F, core, 14);
        LbFx.ring(sl, p, d, radius() * 1.6F, LbFx.lighten(core, 0.5F), 9);
        LbFx.send(sl, ParticleTypes.FLASH, p, 1, 0, 0);
        LbFx.send(sl, ParticleTypes.SONIC_BOOM, p, 1, 0, 0);
        LbFx.send(sl, LbFx.dust(core, 2.0F), p, 30, radius() * 0.5, 0);
        LbFx.sound(sl, p, SoundEvents.WARDEN_SONIC_BOOM, 1.8F, 0.6F);
        LbFx.sound(sl, p, SoundEvents.BEACON_DEACTIVATE, 1.4F, 0.5F);
        BossKit.shake(sl, p, 28, 1.1F, 14);
        LivingEntity owner = c.caster();
        if (follow && owner != null) {
            owner.swing(InteractionHand.MAIN_HAND, true);
            // Coice da soltura
            owner.push(-d.x * 0.35, 0.05, -d.z * 0.35);
            owner.hurtMarked = true;
        }
    }

    private void flyTick(ServerLevel sl, SpellContext c) {
        int t = age - phaseStart();
        Vec3 d = dir();
        double sp = speed();
        Vec3 from = position();
        Vec3 to = from.add(d.scale(sp));
        setPos(to.x, to.y, to.z);
        traveled += sp;
        float r = visualRadius(age);
        int core = color();

        hitEntities(sl, c, from, to, r, d, core);
        if (erase && clearOfCaster(c, to, r)) {
            // v0.8: um TÚNEL de terreno apagado no caminho, da largura da esfera (fila do núcleo, sem drops)
            int n = Destruction.line(sl, from, to, r * 2F, r * 2F, c.caster(), Destruction.Mode.ERASE);
            if (n > 0) erasedFx(sl, to, d, r, core, t);
        }

        // Rastro: pó do vazio sendo "sugado" pela esfera
        LbFx.send(sl, Vfx.reversePortal(), to, 8, r * 0.7, 0.05);
        LbFx.send(sl, LbFx.dust(core, 2.2F), to.subtract(d.scale(r)), 4, r * 0.5, 0);
        LbFx.send(sl, LbFx.fade(LbFx.lighten(core, 0.6F), 0x1A0024, 1.4F), to, 5, r * 0.8, 0);
        if (t % 9 == 0) {
            LbFx.sound(sl, to, SoundEvents.BEACON_AMBIENT, 2.2F, 0.5F);
            LbFx.sound(sl, to, SoundEvents.PORTAL_AMBIENT, 0.8F, 0.6F);
        }

        if (traveled >= range || !sl.isLoaded(BlockPos.containing(to))
                || to.y < sl.getMinBuildHeight() - 32 || to.y > sl.getMaxBuildHeight() + 64) {
            c.end(to, d);
            entityData.set(DATA_PHASE, PHASE_FADE);
            entityData.set(DATA_PHASE_START, age);
            syncAge();
            LbFx.send(sl, LbFx.fade(core, 0x000000, 2.0F), to, 40, r * 0.6, 0.02);
            LbFx.send(sl, Vfx.reversePortal(), to, 30, r * 0.5, 0.3);
            LbFx.sound(sl, to, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.4F, 0.5F);
        }
    }

    /** Aplica a magia em quem a esfera encosta (uma vez por alvo); projéteis inimigos são apagados. */
    private void hitEntities(ServerLevel sl, SpellContext c, Vec3 from, Vec3 to, float r, Vec3 d, int core) {
        LivingEntity caster = c.caster();
        AABB box = new AABB(from, to).inflate(r + 0.5);
        for (Entity e : sl.getEntities(this, box, e -> e.isAlive() && !e.isSpectator())) {
            if (e instanceof Projectile proj) {
                Entity owner = proj.getOwner();
                if (owner != caster && (owner == null || !c.isAlly(owner))) {
                    LbFx.send(sl, LbFx.fade(core, 0x000000, 1.0F), proj.position(), 8, 0.2, 0.02);
                    proj.discard();
                }
                continue;
            }
            if (!(e instanceof LivingEntity le) || e == caster || hit.contains(e.getUUID())) continue;
            Vec3 cp = LbUtil.closestPoint(e, nearestOnSegment(from, to, LbUtil.center(e)));
            if (cp.distanceToSqr(nearestOnSegment(from, to, cp)) > (double) r * r) continue;
            if (!LbUtil.affects(c, e)) continue;
            hit.add(e.getUUID());
            Vec3 before = le.getDeltaMovement();
            c.impact(cp, d, e);
            c.resolveOnEntity(e, this);
            if (opposingForces(c) && le.isAlive()) {
                // Atração + repulsão se anulam na fusão: em vez de puxado/empurrado (o Puxar cresce com a distância e
                // jogaria os alvos distantes em cima do conjurador), o alvo é arrastado um pouco no sentido do voo
                double k = 0.45 * (1 - Mth.clamp(le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1));
                le.setDeltaMovement(before.scale(0.2).add(d.x * k, 0.12 + Math.max(0, d.y) * k, d.z * k));
                le.hurtMarked = true;
            }
            LbFx.disintegrate(sl, le, core, 30);
            LbFx.sound(sl, cp, SoundEvents.ENDER_EYE_DEATH, 1.2F, 0.5F);
        }
    }

    /** Matéria sendo apagada na frente da esfera: pó do vazio na borda e um estalo grave. */
    private void erasedFx(ServerLevel sl, Vec3 to, Vec3 d, float r, int core, int t) {
        Vec3 front = to.add(d.scale(r * 0.8));
        for (int i = 0; i < 6; i++) {
            Vec3 off = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize().scale(r * 0.9);
            Vec3 v = front.add(off);
            LbFx.send(sl, LbFx.fade(core, 0x000000, 1.2F), v, 3, 0.35, 0.01);
            LbFx.send(sl, Vfx.reversePortal(), v, 2, 0.3, 0.05);
        }
        if (t % 4 == 0) LbFx.sound(sl, front, SoundEvents.RESPAWN_ANCHOR_DEPLETE, 0.9F, 0.5F);
        if (t % 10 == 0) BossKit.shake(sl, to, 20 + r * 4, 0.5F, 8);
    }

    /** A esfera ainda está colada no conjurador (nos primeiros blocos): não apaga o chão sob os pés dele. */
    private static boolean clearOfCaster(SpellContext c, Vec3 at, float r) {
        LivingEntity caster = c.caster();
        if (caster == null || caster.level() != c.level()) return true;
        double reach = r + 1.5;
        return caster.getBoundingBox().inflate(0.5, 0, 0.5).expandTowards(0, -2.2, 0).distanceToSqr(at) > reach * reach;
    }

    /** O conjurador sumiu no meio da fusão: os orbes se desfazem sem disparar. */
    private void fizzle(ServerLevel sl) {
        Vec3 p = position();
        LbFx.send(sl, LbFx.dust(colorA(), 1.0F), p, 12, 0.5, 0);
        LbFx.send(sl, LbFx.dust(colorB(), 1.0F), p, 12, 0.5, 0);
        LbFx.send(sl, Vfx.smoke(), p, 10, 0.3, 0.02);
        LbFx.sound(sl, p, SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.2F);
        discard();
    }

    /** A magia tem Puxar e Empurrar (as duas forças opostas da Convergência)? Calculado uma vez. */
    private boolean opposingForces(SpellContext c) {
        if (opposing == null) {
            boolean pull = false, push = false;
            for (ParsedSpell.EffectEntry e : c.spell().effects()) {
                if (e.effect().id().equals(PULL)) pull = true;
                else if (e.effect().id().equals(PUSH)) push = true;
            }
            opposing = pull && push;
        }
        return opposing;
    }

    private static Vec3 nearestOnSegment(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1.0E-9) return a;
        double t = Mth.clamp(p.subtract(a).dot(ab) / len2, 0, 1);
        return a.add(ab.scale(t));
    }

    // =====================================================================================
    //  Cliente: segue a mão de quem segura (sem atraso) e prevê o voo em linha reta
    // =====================================================================================
    @Override
    protected void clientTick() {
        if (phase() == PHASE_CHARGE) {
            if (ownerEntity() instanceof LivingEntity owner) {
                Vec3 p = holdPoint(owner, holdDir(owner, 1F), 1F);
                setPos(p.x, p.y, p.z);
            }
        } else if (phase() == PHASE_FLY) {
            Vec3 n = position().add(dir().scale(speed()));
            setPos(n.x, n.y, n.z);
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (phase() == PHASE_CHARGE && entityData.get(DATA_OWNER) >= 0) return;
        // O voo é previsto exatamente no cliente: só aceita correções grandes
        if (phase() == PHASE_FLY && position().distanceToSqr(x, y, z) < 2.25) return;
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    protected void saveExtra(CompoundTag tag) {
        Vec3 d = dir();
        tag.putDouble("DirX", d.x);
        tag.putDouble("DirY", d.y);
        tag.putDouble("DirZ", d.z);
        tag.putInt("Phase", phase());
        tag.putInt("PhaseStart", phaseStart());
        tag.putInt("Charge", chargeTicks());
        tag.putFloat("Radius", radius());
        tag.putFloat("Speed", speed());
        tag.putFloat("Yaw", yawOffset());
        tag.putInt("ColorA", colorA());
        tag.putInt("ColorB", colorB());
        tag.putDouble("Traveled", traveled);
        tag.putDouble("Range", range);
        tag.putBoolean("Erase", erase);
        ListTag list = new ListTag();
        for (UUID id : hit) list.add(NbtUtils.createUUID(id));
        tag.put("Hit", list);
    }

    @Override
    protected void readExtra(CompoundTag tag) {
        setDir(new Vec3(tag.getDouble("DirX"), tag.getDouble("DirY"), tag.getDouble("DirZ")));
        entityData.set(DATA_PHASE, tag.getInt("Phase"));
        entityData.set(DATA_PHASE_START, tag.getInt("PhaseStart"));
        entityData.set(DATA_CHARGE, Math.max(1, tag.getInt("Charge")));
        entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
        entityData.set(DATA_SPEED, tag.getFloat("Speed"));
        entityData.set(DATA_YAW, tag.getFloat("Yaw"));
        entityData.set(DATA_COLOR_A, tag.getInt("ColorA"));
        entityData.set(DATA_COLOR_B, tag.getInt("ColorB"));
        entityData.set(DATA_OWNER, -1);
        traveled = tag.getDouble("Traveled");
        range = tag.contains("Range") ? tag.getDouble("Range") : 32;
        erase = tag.getBoolean("Erase");
        follow = false; // depois de recarregar, a carga termina onde está
        hit.clear();
        for (Tag t : tag.getList("Hit", Tag.TAG_INT_ARRAY)) hit.add(NbtUtils.loadUUID(t));
    }
}
