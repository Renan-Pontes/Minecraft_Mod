package com.renanpontes.isekaimahou.feature.class_smith.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.SmithRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Arma projetada do Ferreiro (desenhada com o modelo do item: Lâmina Forjada, espada vanilla, cópia de Traçar...).
 * Vive poucos segundos, não é salva e não mexe em blocos. Modos:
 * <ul>
 *   <li>{@link #STRIKE}: voa reto e chama um {@link Impact} em cada criatura atravessada (Traçar, Espada Efêmera);</li>
 *   <li>{@link #CARRIER}: voa reto e aplica os EFEITOS da magia em quem atravessar; pode explodir no fim
 *   (Caladbolg II — "Fantasma Partido") — avisa impactos e fim para as ligações de estágio;</li>
 *   <li>{@link #BOOMERANG}: sai num arco, gira e VOLTA para quem lançou cruzando com a gêmea (Kanshō e Bakuya);</li>
 *   <li>{@link #PLANTED}: fica cravada no chão como enfeite (Mundo das Lâminas) e some no fim.</li>
 * </ul>
 * Ao bater num bloco fica cravada ({@link #STUCK}) alguns ticks e some ({@link #FADE}).
 */
public class SmithBladeEntity extends Entity {
    public static final byte STRIKE = 0;
    public static final byte CARRIER = 1;
    public static final byte BOOMERANG = 2;
    public static final byte PLANTED = 3;
    public static final byte STUCK = 4;
    public static final byte FADE = 5;

    public static final int FADE_TICKS = 8;
    /** Armas projetadas por conjurador no nível: acima disso os efeitos agem sem a entidade. */
    public static final int MAX_PER_OWNER = 72;
    private static final int HARD_LIFE = 20 * 40;

    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(SmithBladeEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(SmithBladeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> DATA_DIR = SynchedEntityData.defineId(SmithBladeEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(SmithBladeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(SmithBladeEntity.class, EntityDataSerializers.INT);
    /** 0 = nada; 1 = espiral (Caladbolg); 2 = giro de disco (gêmeas). */
    private static final EntityDataAccessor<Byte> DATA_STYLE = SynchedEntityData.defineId(SmithBladeEntity.class, EntityDataSerializers.BYTE);

    public static final byte STYLE_PLAIN = 0;
    public static final byte STYLE_SPIRAL = 1;
    public static final byte STYLE_SPIN = 2;

    /** O que acontece quando uma lâmina de golpe acerta uma criatura (e quando ela termina). */
    public interface Impact {
        void onEntity(SmithBladeEntity blade, LivingEntity target);

        default void onEnd(SmithBladeEntity blade, Vec3 at, boolean hitBlock) {}
    }

    // ---------- servidor ----------
    @Nullable private SpellContext ctx;
    @Nullable private Impact impact;
    @Nullable private UUID ownerId;
    private float speed = 1.5F;
    private int maxFlight = 40;
    private int pierceLeft = 1;
    private int modeTicks;
    private int life = 60;
    /** Caladbolg: raio e poder da explosão no fim (0 = não explode). */
    private float explodeRadius;
    private float explodePower = 1F;
    private boolean ended;
    private final Set<UUID> hit = new HashSet<>();
    // bumerangue
    private Vec3 start = Vec3.ZERO;
    private Vec3 forward = new Vec3(0, 0, 1);
    private Vec3 side = new Vec3(1, 0, 0);
    private float reach = 10F;
    private float width = 3.5F;
    private int outTicks = 14;
    private boolean returning;
    private boolean twoPasses;
    private Vec3 turnPoint = Vec3.ZERO;
    @Nullable private LivingEntity returnTo;

    // ---------- cliente ----------
    public int clientModeStart;
    private byte lastMode = -1;

    public SmithBladeEntity(EntityType<? extends SmithBladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // =====================================================================================
    //  Criação
    // =====================================================================================

    private static SmithBladeEntity base(ServerLevel level, @Nullable SpellContext ctx, Vec3 at, ItemStack item, int color, float scale) {
        SmithBladeEntity b = new SmithBladeEntity(SmithRegistries.FLYING_BLADE.get(), level);
        b.ctx = ctx;
        b.ownerId = ctx != null ? ctx.casterId() : null;
        b.setPos(at.x, at.y, at.z);
        b.entityData.set(DATA_ITEM, item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1));
        b.entityData.set(DATA_COLOR, color & 0xFFFFFF);
        b.entityData.set(DATA_SCALE, scale);
        return b;
    }

    /** Lâmina de GOLPE: voa reto e chama {@code impact} em quem atravessar (até {@code pierce} criaturas). */
    public static SmithBladeEntity strike(ServerLevel level, SpellContext ctx, Vec3 from, Vec3 dir, ItemStack item, int color, float scale,
                                          float speed, int pierce, Impact impact) {
        SmithBladeEntity b = base(level, ctx, from, item, color, scale);
        b.impact = impact;
        b.pierceLeft = Math.max(1, pierce);
        b.launch(dir, speed, 40);
        b.setMode(STRIKE);
        return b;
    }

    /**
     * Lâmina PORTADORA: aplica os efeitos da magia em quem atravessar (até {@code pierce}); com {@code explodeRadius > 0}
     * explode no último alvo, no bloco ou no fim do voo (Caladbolg II).
     */
    public static SmithBladeEntity carrier(ServerLevel level, SpellContext ctx, Vec3 from, Vec3 dir, ItemStack item, int color, float scale,
                                           float speed, int pierce, int maxFlight, float explodeRadius, float explodePower) {
        SmithBladeEntity b = base(level, ctx, from, item, color, scale);
        b.pierceLeft = Math.max(1, pierce);
        b.explodeRadius = explodeRadius;
        b.explodePower = explodePower;
        b.launch(dir, speed, maxFlight);
        b.setMode(CARRIER);
        return b;
    }

    /**
     * Lâmina BUMERANGUE: sai de {@code from} em arco para o lado {@code sideSign} (±1) até {@code reach} blocos e volta para
     * {@code returnTo} (ou para o ponto de partida) pelo outro lado. {@code twoPasses}: acerta de novo na volta (Sobrefio).
     */
    public static SmithBladeEntity boomerang(ServerLevel level, SpellContext ctx, Vec3 from, Vec3 forward, int sideSign, ItemStack item,
                                             int color, float scale, float reach, float width, int outTicks, boolean twoPasses,
                                             @Nullable LivingEntity returnTo) {
        SmithBladeEntity b = base(level, ctx, from, item, color, scale);
        Vec3 f = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 s = f.cross(new Vec3(0, 1, 0));
        if (s.lengthSqr() < 1.0E-4) s = new Vec3(1, 0, 0);
        b.start = from;
        b.forward = f;
        b.side = s.normalize().scale(sideSign >= 0 ? 1 : -1);
        b.reach = reach;
        b.width = width;
        b.outTicks = Math.max(4, outTicks);
        b.twoPasses = twoPasses;
        b.returnTo = returnTo;
        b.setDir(f);
        b.entityData.set(DATA_STYLE, STYLE_SPIN);
        b.setMode(BOOMERANG);
        return b;
    }

    /** Lâmina CRAVADA (enfeite) com a ponta para {@code tipDir}, por {@code life} ticks. */
    public static SmithBladeEntity planted(ServerLevel level, @Nullable SpellContext ctx, Vec3 at, Vec3 tipDir, ItemStack item, int color,
                                           float scale, int life) {
        SmithBladeEntity b = base(level, ctx, at, item, color, scale);
        b.life = life;
        b.setDir(tipDir);
        b.setMode(PLANTED);
        return b;
    }

    public SmithBladeEntity style(byte style) {
        entityData.set(DATA_STYLE, style);
        return this;
    }

    private void launch(Vec3 dir, float speed, int maxFlight) {
        this.speed = speed;
        this.maxFlight = maxFlight;
        setDir(dir);
    }

    /** Quantas armas projetadas deste conjurador existem agora no nível. */
    public static int countFor(ServerLevel level, @Nullable UUID owner) {
        if (owner == null) return 0;
        int[] n = {0};
        level.getEntities(SmithRegistries.FLYING_BLADE.get(), b -> {
            if (owner.equals(b.ownerId)) n[0]++;
            return false;
        });
        return n[0];
    }

    public static boolean hasRoom(ServerLevel level, @Nullable UUID owner, int wanted) {
        return countFor(level, owner) + wanted <= MAX_PER_OWNER;
    }

    // =====================================================================================
    //  Dados sincronizados
    // =====================================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_MODE, STRIKE);
        builder.define(DATA_DIR, new Vector3f(0, 0, 1));
        builder.define(DATA_SCALE, 1.0F);
        builder.define(DATA_COLOR, SmithFx.STEEL);
        builder.define(DATA_STYLE, STYLE_PLAIN);
    }

    public ItemStack item() { return entityData.get(DATA_ITEM); }
    public byte mode() { return entityData.get(DATA_MODE); }
    public float scale() { return entityData.get(DATA_SCALE); }
    public int color() { return entityData.get(DATA_COLOR); }
    public byte style() { return entityData.get(DATA_STYLE); }

    public Vec3 dir() {
        Vector3f v = entityData.get(DATA_DIR);
        return new Vec3(v.x, v.y, v.z);
    }

    private void setDir(Vec3 d) {
        Vec3 n = d.lengthSqr() < 1.0E-6 ? new Vec3(0, -1, 0) : d.normalize();
        entityData.set(DATA_DIR, new Vector3f((float) n.x, (float) n.y, (float) n.z));
    }

    private void setMode(byte m) {
        entityData.set(DATA_MODE, m);
        modeTicks = 0;
    }

    @Nullable
    public SpellContext context() {
        return ctx;
    }

    // =====================================================================================
    //  Tique
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        modeTicks++;
        if (tickCount > HARD_LIFE) {
            discard();
            return;
        }
        switch (mode()) {
            case STRIKE, CARRIER -> fly();
            case BOOMERANG -> boomerangTick();
            case PLANTED -> {
                if (modeTicks >= life) setMode(FADE);
            }
            case STUCK -> {
                if (modeTicks >= 16) setMode(FADE);
            }
            default -> {
                if (modeTicks >= FADE_TICKS) discard();
            }
        }
    }

    private void fly() {
        ServerLevel sl = (ServerLevel) level();
        Vec3 from = position();
        Vec3 d = dir();
        Vec3 to = from.add(d.scale(speed));
        if (modeTicks > maxFlight || (ctx == null && impact == null)) {
            finish(from, false);
            return;
        }
        BlockHitResult bhr = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 stop = bhr.getType() == HitResult.Type.BLOCK ? bhr.getLocation() : to;
        // criaturas no caminho (até a parede)
        AABB sweep = new AABB(from, stop).inflate(0.6 * Math.max(0.6F, scale()));
        List<LivingEntity> list = sl.getEntitiesOfClass(LivingEntity.class, sweep, this::canHit);
        list.sort((a, b) -> Double.compare(a.distanceToSqr(from), b.distanceToSqr(from)));
        for (LivingEntity e : list) {
            AABB box = e.getBoundingBox().inflate(0.35 * Math.max(0.6F, scale()));
            if (!box.contains(from) && box.clip(from, stop).isEmpty()) continue;
            hit.add(e.getUUID());
            hitEntity(e);
            pierceLeft--;
            if (pierceLeft <= 0) {
                Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                setPos(c.x, c.y, c.z);
                finish(c, false);
                return;
            }
        }
        if (bhr.getType() == HitResult.Type.BLOCK) {
            Vec3 at = bhr.getLocation().subtract(d.scale(0.15));
            setPos(at.x, at.y, at.z);
            if (mode() == CARRIER && ctx != null && explodeRadius <= 0F) {
                ctx.resolveOnBlock(bhr, this);
                ctx.impact(bhr.getLocation(), d, null);
            }
            finish(at, true);
            return;
        }
        setPos(to.x, to.y, to.z);
    }

    private boolean canHit(LivingEntity e) {
        if (!e.isAlive() || e.isSpectator() || hit.contains(e.getUUID())) return false;
        if (ctx == null) return true;
        return e != ctx.caster() && !ctx.isAlly(e);
    }

    private void hitEntity(LivingEntity e) {
        ServerLevel sl = (ServerLevel) level();
        Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
        if ((mode() == CARRIER || mode() == BOOMERANG) && ctx != null) {
            ctx.resolveOnEntity(e, this);
            ctx.impact(c, dir(), e);
        } else if (impact != null) {
            impact.onEntity(this, e);
        }
        sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.25, 0.3, 0.25, 0.3);
        sl.sendParticles(SmithFx.spark(color()), c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.05);
        SmithFx.sound(sl, c, SoundEvents.TRIDENT_HIT, 0.7F, 1.1F + sl.random.nextFloat() * 0.3F);
    }

    /** Fim do voo: explode (Caladbolg), avisa o fim do estágio e crava/some. */
    private void finish(Vec3 at, boolean hitBlock) {
        if (ended) return;
        ended = true;
        ServerLevel sl = (ServerLevel) level();
        if (mode() == CARRIER && ctx != null) {
            if (explodeRadius > 0F) explode(sl, at);
            ctx.end(at, dir());
        } else if (impact != null) {
            impact.onEnd(this, at, hitBlock);
        }
        if (hitBlock && explodeRadius <= 0F) {
            setMode(STUCK);
            SmithFx.sound(sl, at, SoundEvents.TRIDENT_HIT_GROUND, 0.8F, 1.0F);
        } else {
            setMode(FADE);
        }
    }

    /** "Fantasma Partido": a arma se desfaz numa explosão de mana (sem quebrar blocos). */
    private void explode(ServerLevel sl, Vec3 at) {
        float r = explodeRadius;
        SpellContext use = explodePower == 1F ? ctx : ctx.copy(ctx.powerMult() * explodePower);
        use.resolveArea(at, r, this, false);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
                e -> e.isAlive() && e != ctx.caster() && !ctx.isAlly(e) && e.distanceToSqr(at) <= r * r)) {
            Vec3 away = e.position().subtract(at);
            Vec3 push = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0.5, 0) : away.normalize().scale(0.9);
            e.push(push.x, 0.45, push.z);
            e.hurtMarked = true;
        }
        ctx.impact(at, dir(), null);
        int c = color();
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 2, 0, 0, 0, 0);
        sl.sendParticles(SmithFx.dust(c, 2.0F), at.x, at.y, at.z, 70, r * 0.45, r * 0.45, r * 0.45, 0);
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 0.2, 0.2, 0.2, 0.45);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 30, r * 0.3, r * 0.3, r * 0.3, 0.3);
        SmithFx.ring(sl, at, r, SmithFx.dust(c, 1.6F), 48);
        SmithFx.ring(sl, at.add(0, 0.4, 0), r * 0.6, SmithFx.dust(0xFFFFFF, 1.2F), 32);
        SmithFx.sound(sl, at, SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 0.9F);
        SmithFx.sound(sl, at, SoundEvents.GLASS_BREAK, 1.2F, 0.6F);
        SmithFx.sound(sl, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9F, 1.3F);
        BossKit.shake(sl, at, 20, 0.8F, 10);
    }

    // ---------------------------------------------------------------- bumerangue
    private void boomerangTick() {
        ServerLevel sl = (ServerLevel) level();
        if (ctx == null) {
            setMode(FADE);
            return;
        }
        Vec3 from = position();
        Vec3 next;
        if (!returning) {
            float s = Math.min(1F, modeTicks / (float) outTicks);
            next = start.add(forward.scale(reach * s)).add(side.scale(width * Math.sin(Math.PI * s)));
            if (modeTicks >= outTicks) {
                returning = true;
                turnPoint = next;
                modeTicks = 0;
                if (twoPasses) hit.clear();
                SmithFx.sound(sl, next, SoundEvents.TRIDENT_RETURN, 0.8F, 1.3F);
            }
        } else {
            Vec3 home = returnTo != null && returnTo.isAlive() && returnTo.level() == sl
                    ? returnTo.position().add(0, returnTo.getBbHeight() * 0.6, 0) : start;
            float s = Math.min(1F, modeTicks / (float) outTicks);
            Vec3 lerp = turnPoint.add(home.subtract(turnPoint).scale(s));
            next = lerp.add(side.scale(-width * Math.sin(Math.PI * s)));
            if (s >= 1F || next.distanceToSqr(home) < 0.8) {
                caught(sl, home);
                return;
            }
        }
        Vec3 move = next.subtract(from);
        if (move.lengthSqr() > 1.0E-6) setDir(move);
        AABB sweep = new AABB(from, next).inflate(0.8 * scale());
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, sweep, this::canHit)) {
            hit.add(e.getUUID());
            hitEntity(e);
        }
        setPos(next.x, next.y, next.z);
        if (tickCount % 3 == 0) SmithFx.sound(sl, next, SoundEvents.PLAYER_ATTACK_SWEEP, 0.35F, 1.6F);
        if (tickCount > outTicks * 3 + 20) caught(sl, position());
    }

    /** A lâmina volta para a mão (ou some no ponto de partida). */
    private void caught(ServerLevel sl, Vec3 at) {
        if (!ended && ctx != null) ctx.end(at, forward);
        ended = true;
        sl.sendParticles(SmithFx.dust(color(), 1.1F), at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0);
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.03);
        SmithFx.sound(sl, at, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6F, 1.4F);
        discard();
    }

    // ---------------------------------------------------------------- cliente
    private void clientTick() {
        byte m = mode();
        if (m != lastMode) {
            lastMode = m;
            clientModeStart = tickCount;
        }
        Level l = level();
        int rgb = color();
        if (m == STRIKE || m == CARRIER || m == BOOMERANG) {
            Vec3 p = position();
            l.addParticle(SmithFx.spark(rgb), p.x, p.y, p.z, 0, 0, 0);
            if (style() == STYLE_SPIRAL) {
                Vec3 d = dir();
                Vec3 s = d.cross(new Vec3(0, 1, 0));
                if (s.lengthSqr() < 1.0E-4) s = new Vec3(1, 0, 0);
                s = s.normalize();
                Vec3 u = s.cross(d).normalize();
                for (int i = 0; i < 3; i++) {
                    double a = tickCount * 0.9 + i * Math.PI * 2 / 3;
                    double r = 0.45 * scale();
                    Vec3 q = p.subtract(d.scale(0.6 + i * 0.4)).add(s.scale(Math.cos(a) * r)).add(u.scale(Math.sin(a) * r));
                    l.addParticle(ParticleTypes.END_ROD, q.x, q.y, q.z, 0, 0, 0);
                }
            }
        } else if (m == PLANTED && random.nextInt(14) == 0) {
            l.addParticle(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.1, getZ(), 0, 0.01, 0);
        }
    }

    // =====================================================================================
    //  Entidade "fantasma"
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 96 * 96;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
