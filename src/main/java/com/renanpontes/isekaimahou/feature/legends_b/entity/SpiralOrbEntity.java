package com.renanpontes.isekaimahou.feature.legends_b.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_b.LbFx;
import com.renanpontes.isekaimahou.feature.legends_b.LbUtil;
import com.renanpontes.isekaimahou.feature.legends_b.LegendsBRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Esfera Espiral: esfera de vento girando presa à mão do conjurador por alguns segundos. Ao encostar num inimigo
 * (ou ao golpeá-lo com a mão) ela o PRENDE e tritura — vários acertos rápidos com parte do poder — e então
 * explode em vento, aplicando a magia com poder cheio e arremessando o alvo longe.
 * Como estágio de magia programável (origem própria) ela é lançada para frente devagar em vez de ficar na mão.
 */
public class SpiralOrbEntity extends LegendSpellEntity {
    public static final int STATE_HOLD = 0;
    public static final int STATE_THROWN = 1;
    public static final int STATE_GRIND = 2;
    /** Ticks em que a esfera ainda está se formando (não tritura). */
    public static final int FORM_TICKS = 5;
    /** Fração do poder em cada acerto da trituração. */
    private static final float GRIND_POWER = 0.3F;

    /** Esfera na mão de cada conjurador (servidor): golpear com a mão vazia usa a esfera. */
    private static final Map<UUID, SpiralOrbEntity> HELD = new HashMap<>();

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(SpiralOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(SpiralOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(SpiralOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(SpiralOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DATA_DIR = SynchedEntityData.defineId(SpiralOrbEntity.class, EntityDataSerializers.VECTOR3);

    private int lifetime = 80;
    private double range = 20;
    private double traveled;
    private int grindHits = 5;
    private int hitsDone;
    private int grindAge;
    private int targetId = -1;
    private SpellContext grindCtx;
    /** Alvo do estágio anterior (magia programável): a esfera lançada passa por ele sem triturá-lo. */
    private UUID ignored;

    public SpiralOrbEntity(EntityType<? extends SpiralOrbEntity> type, Level level) {
        super(type, level);
    }

    /**
     * @param held     presa à mão do conjurador (false = lançada de {@code pos} na direção {@code dir})
     * @param lifetime ticks na mão antes de se desfazer (lançada: até cobrir {@code range})
     * @param hits     acertos da trituração
     */
    public static SpiralOrbEntity create(SpellContext ctx, Vec3 pos, Vec3 dir, boolean held, float radius, int lifetime,
                                         float speed, double range, int hits) {
        SpiralOrbEntity e = new SpiralOrbEntity(LegendsBRegistry.SPIRAL_ORB.get(), ctx.level());
        e.bind(ctx);
        boolean hold = held && ctx.caster() != null;
        e.setPos(pos.x, pos.y, pos.z);
        e.entityData.set(DATA_OWNER, hold ? ctx.caster().getId() : -1);
        e.entityData.set(DATA_STATE, hold ? STATE_HOLD : STATE_THROWN);
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_SPEED, speed);
        e.setDir(dir);
        e.lifetime = lifetime;
        e.range = range;
        e.grindHits = Math.max(3, hits);
        e.ignored = ctx.previousTarget();
        return e;
    }

    /** Desfaz (sem disparar nada) a esfera que este conjurador está segurando, se houver. */
    public static void replaceHeld(LivingEntity owner) {
        SpiralOrbEntity old = HELD.remove(owner.getUUID());
        if (old != null && !old.isRemoved()) {
            if (old.level() instanceof ServerLevel sl) old.poof(sl);
            old.discard();
        }
    }

    /** Servidor parou: esquece as esferas (não guarda referência a entidades de um mundo fechado). */
    public static void clearHeld() {
        HELD.clear();
    }

    /** Registra como a esfera na mão do conjurador (depois de adicionada ao mundo). */
    public void markHeld() {
        LivingEntity owner = ownerEntity() instanceof LivingEntity le ? le : null;
        if (owner != null) HELD.put(owner.getUUID(), this);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_STATE, STATE_HOLD);
        builder.define(DATA_RADIUS, 0.55F);
        builder.define(DATA_SPEED, 0.5F);
        builder.define(DATA_DIR, new Vector3f(0, 0, 1));
    }

    public int state() { return entityData.get(DATA_STATE); }
    public float radius() { return entityData.get(DATA_RADIUS); }
    public float speed() { return entityData.get(DATA_SPEED); }
    public boolean isHeld() { return entityData.get(DATA_OWNER) >= 0; }

    public Vec3 dir() {
        Vector3f v = entityData.get(DATA_DIR);
        return new Vec3(v.x(), v.y(), v.z());
    }

    private void setDir(Vec3 d) {
        Vec3 n = d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
        entityData.set(DATA_DIR, new Vector3f((float) n.x, (float) n.y, (float) n.z));
    }

    @Nullable
    public Entity ownerEntity() {
        int id = entityData.get(DATA_OWNER);
        return id >= 0 ? level().getEntity(id) : null;
    }

    /** Ponto da mão direita de quem segura (um pouco à frente, à direita e abaixo dos olhos). */
    public static Vec3 handPoint(LivingEntity owner, float partialTick) {
        Vec3 look = owner.getViewVector(partialTick);
        LbUtil.Frame f = LbUtil.Frame.of(look);
        return owner.getEyePosition(partialTick).add(look.scale(0.95)).add(f.right().scale(0.36)).add(f.up().scale(-0.32));
    }

    /** 0..1: a esfera se formando na mão (o renderer cresce junto). */
    public float formation(float ageF) {
        return Mth.clamp(ageF / FORM_TICKS, 0F, 1F);
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================
    @Override
    protected void serverTick(ServerLevel sl, SpellContext c) {
        switch (state()) {
            case STATE_HOLD -> holdTick(sl, c);
            case STATE_THROWN -> thrownTick(sl, c);
            default -> grindTick(sl, c);
        }
    }

    private boolean ownerValid(SpellContext c, ServerLevel sl) {
        LivingEntity owner = c.caster();
        return owner != null && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator() && owner.level() == sl;
    }

    private void holdTick(ServerLevel sl, SpellContext c) {
        if (!ownerValid(c, sl)) {
            poof(sl);
            discard();
            return;
        }
        LivingEntity owner = c.caster();
        Vec3 p = handPoint(owner, 1F);
        setPos(p.x, p.y, p.z);
        setDir(owner.getLookAngle());
        whirl(sl, p, 1);
        if (age % 10 == 1) LbFx.sound(sl, p, SoundEvents.BREEZE_IDLE_AIR, 0.8F, 1.7F);
        if (age >= FORM_TICKS) {
            LivingEntity t = touching(sl, c, p, p);
            if (t != null) {
                startGrind(sl, c, t);
                return;
            }
        }
        if (age >= lifetime) {
            c.end(p, owner.getLookAngle());
            poof(sl);
            if (owner instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_b.spiral_faded").withStyle(ChatFormatting.GRAY), true);
            }
            discard();
        }
    }

    private void thrownTick(ServerLevel sl, SpellContext c) {
        Vec3 d = dir();
        Vec3 from = position();
        Vec3 to = from.add(d.scale(speed()));
        whirl(sl, from, 2);
        if (age % 8 == 1) LbFx.sound(sl, from, SoundEvents.BREEZE_IDLE_AIR, 0.9F, 1.5F);
        if (age >= 2) {
            LivingEntity t = touching(sl, c, from, to);
            if (t != null) {
                setPos(to.x, to.y, to.z);
                startGrind(sl, c, t);
                return;
            }
        }
        BlockHitResult bh = LbUtil.clip(sl, from, to, ClipContext.Fluid.NONE, null);
        if (bh.getType() == HitResult.Type.BLOCK) {
            Vec3 hp = bh.getLocation();
            setPos(hp.x, hp.y, hp.z);
            c.impact(hp, d, null);
            c.resolveOnBlock(bh, this);
            windBurst(sl, c, hp, d, null);
            discard();
            return;
        }
        setPos(to.x, to.y, to.z);
        traveled += speed();
        if (traveled >= range || age >= lifetime) {
            c.end(to, d);
            poof(sl);
            discard();
        }
    }

    /** Inimigo encostando na esfera entre {@code from} e {@code to} (o mais perto), ou null. */
    @Nullable
    private LivingEntity touching(ServerLevel sl, SpellContext c, Vec3 from, Vec3 to) {
        LivingEntity caster = c.caster();
        double reach = radius() + 0.35;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(reach + 1.0),
                e -> e.isAlive() && !e.isSpectator() && e != caster && !e.getUUID().equals(ignored)
                        && (caster == null || !e.isPassengerOfSameVehicle(caster)))) {
            // Distância até a caixa do alvo no começo e no fim do passo (a esfera lançada não "pula" ninguém)
            double d = Math.min(LbUtil.closestPoint(e, to).distanceToSqr(to), LbUtil.closestPoint(e, from).distanceToSqr(from));
            if (d > reach * reach || d >= bestD || !LbUtil.affects(c, e)) continue;
            best = e;
            bestD = d;
        }
        return best;
    }

    /** Prende o alvo e começa a triturar. */
    public void startGrind(ServerLevel sl, SpellContext c, LivingEntity t) {
        entityData.set(DATA_STATE, STATE_GRIND);
        targetId = t.getId();
        grindAge = 0;
        hitsDone = 0;
        grindCtx = c.copy(c.powerMult() * GRIND_POWER);
        Vec3 contact = LbUtil.closestPoint(t, position());
        c.impact(contact, dir(), t);
        LbFx.sound(sl, contact, SoundEvents.BREEZE_WHIRL, 1.3F, 1.3F);
        LbFx.sound(sl, contact, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.8F);
        LbFx.send(sl, Vfx.cloud(), contact, 10, 0.2, 0.15);
    }

    private void grindTick(ServerLevel sl, SpellContext c) {
        boolean held = isHeld();
        if (held) {
            if (!ownerValid(c, sl)) {
                poof(sl);
                discard();
                return;
            }
            LivingEntity owner = c.caster();
            Vec3 p = handPoint(owner, 1F);
            setPos(p.x, p.y, p.z);
            setDir(owner.getLookAngle());
        }
        LivingEntity t = level().getEntity(targetId) instanceof LivingEntity le ? le : null;
        if (t == null || !t.isAlive() || t.level() != sl) {
            // O alvo morreu na trituração: a esfera explode no lugar
            windBurst(sl, c, position(), dir(), null);
            discard();
            return;
        }
        Vec3 d = dir();
        // Chefes resistem: não são arrastados nem presos, só triturados enquanto estiverem encostados
        boolean boss = LbUtil.isBoss(t);
        // O alvo escapou (teleporte, chefe que se afastou, conjurador que andou para longe): a esfera estoura no ar.
        // Alvos comuns têm alguns ticks para serem puxados até a esfera (golpe dado no limite do alcance).
        double gap = radius() + 3.0;
        if ((boss || grindAge >= 4) && LbUtil.closestPoint(t, position()).distanceToSqr(position()) > gap * gap) {
            windBurst(sl, c, position(), d, null);
            discard();
            return;
        }
        Vec3 hold = position().add(d.scale(radius() * 0.7 + t.getBbWidth() * 0.5));
        Vec3 pull = hold.subtract(LbUtil.center(t));
        if (!boss) pin(t, pull);
        grindAge++;
        if (grindAge % 2 == 0 && hitsDone < grindHits) {
            hitsDone++;
            t.invulnerableTime = 0;
            for (ParsedSpell.EffectEntry entry : c.spell().effects()) {
                if (c.isValidTarget(entry.effect(), entry.stats(), t)) grindCtx.applyEffect(entry, t);
            }
            if (!boss) pin(t, pull); // os empurrões dos efeitos esperam o arremesso final
            Vec3 tc = LbUtil.center(t);
            LbFx.send(sl, ParticleTypes.SWEEP_ATTACK, tc, 1, 0.2, 0);
            LbFx.send(sl, LbFx.dust(LbFx.lighten(color(), 0.5F), 1.0F), tc, 8, 0.35, 0);
            LbFx.send(sl, Vfx.cloud(), hold, 4, 0.15, 0.08);
            LbFx.sound(sl, tc, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.3F + 0.07F * hitsDone);
        }
        whirl(sl, position(), 1);
        if (hitsDone >= grindHits && grindAge >= grindHits * 2 + 2) launch(sl, c, t, d);
    }

    /** Segura o alvo colado na esfera (velocidade em direção ao ponto de trituração). */
    private static void pin(LivingEntity t, Vec3 pull) {
        t.setDeltaMovement(pull.scale(0.5));
        t.hurtMarked = true;
        t.fallDistance = 0;
        if (t instanceof Mob m) m.getNavigation().stop();
    }

    /** Golpe final: a magia com poder cheio + explosão de vento que arremessa o alvo. */
    private void launch(ServerLevel sl, SpellContext c, LivingEntity t, Vec3 d) {
        t.invulnerableTime = 0;
        c.resolveOnEntity(t, this);
        SpellStats fs = c.formStats();
        Vec3 h = new Vec3(d.x, Math.max(0, d.y) * 0.5, d.z);
        h = h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
        double resist = Mth.clamp(t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
        double kb = (1.5 + 0.45 * Math.max(0, fs.amplify + fs.charge)) * (1 - 0.7 * resist);
        if (LbUtil.isBoss(t)) kb *= 0.35;
        if (t.isAlive()) {
            t.setDeltaMovement(h.x * kb, 0.4 + 0.25 * kb, h.z * kb);
            t.hurtMarked = true;
            trail(sl, t, 14);
        }
        windBurst(sl, c, LbUtil.center(t), d, t);
        discard();
    }

    /** Explosão de vento: anéis, rajada, som e um empurrão nos inimigos em volta. */
    private void windBurst(ServerLevel sl, SpellContext c, Vec3 at, Vec3 d, @Nullable LivingEntity skip) {
        int rgb = color();
        LbFx.ring(sl, at, d, 4.2F, 0xEFFBFF, 12);
        LbFx.ring(sl, at, new Vec3(0, 1, 0), 3.2F, rgb, 10);
        LbFx.send(sl, ParticleTypes.GUST_EMITTER_LARGE, at, 1, 0, 0);
        LbFx.send(sl, ParticleTypes.EXPLOSION, at, 2, 0.3, 0);
        LbFx.send(sl, Vfx.cloud(), at, 30, 0.5, 0.25);
        LbFx.send(sl, LbFx.dust(rgb, 1.6F), at, 20, 0.8, 0);
        LbFx.sound(sl, at, SoundEvents.WIND_CHARGE_BURST, 1.8F, 0.7F);
        LbFx.sound(sl, at, SoundEvents.GENERIC_EXPLODE, 0.6F, 1.5F);
        BossKit.shake(sl, at, 18, 0.7F, 10);
        LivingEntity caster = c.caster();
        for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.5),
                e -> e.isAlive() && e != skip && e != caster && !e.isSpectator() && !c.isAlly(e))) {
            Vec3 away = o.position().subtract(at);
            away = away.lengthSqr() < 1.0E-4 ? d : away.normalize();
            double k = 0.8 * (1 - Mth.clamp(o.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1));
            o.push(away.x * k, 0.3 * k, away.z * k);
            o.hurtMarked = true;
        }
    }

    /** Rastro de vento seguindo o alvo arremessado. */
    private static void trail(ServerLevel sl, LivingEntity t, int ticksLeft) {
        if (ticksLeft <= 0 || !t.isAlive() || t.level() != sl) return;
        LbFx.send(sl, Vfx.cloud(), LbUtil.center(t), 2, 0.2, 0.02);
        LbFx.send(sl, ParticleTypes.SMALL_GUST, LbUtil.center(t), 1, 0.1, 0);
        SpellScheduler.schedule(1, () -> trail(sl, t, ticksLeft - 1));
    }

    /** Partículas de redemoinho em volta da esfera. */
    private void whirl(ServerLevel sl, Vec3 p, int every) {
        if (age % every != 0) return;
        double a = age * 0.9;
        double r = radius() * 1.3;
        for (int i = 0; i < 2; i++) {
            double ang = a + i * Math.PI;
            Vec3 q = p.add(Math.cos(ang) * r, Math.sin(ang * 0.7) * r * 0.5, Math.sin(ang) * r);
            LbFx.send(sl, LbFx.dust(LbFx.lighten(color(), 0.6F), 0.6F), q, 1, 0.02, 0);
        }
    }

    /** Some sem estrondo (substituída, conjurador sumiu, tempo acabou). */
    private void poof(ServerLevel sl) {
        Vec3 p = position();
        LbFx.send(sl, Vfx.cloud(), p, 12, 0.2, 0.08);
        LbFx.send(sl, LbFx.dust(color(), 1.0F), p, 8, 0.3, 0);
        LbFx.sound(sl, p, SoundEvents.BREEZE_DEFLECT, 0.7F, 1.5F);
    }

    // =====================================================================================
    //  Golpear com a esfera na mão
    // =====================================================================================

    /** Golpe corpo a corpo com a esfera pronta: em vez do soco, a esfera tritura o alvo. */
    public static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (p.level().isClientSide || !(p.level() instanceof ServerLevel sl)) return;
        SpiralOrbEntity orb = HELD.get(p.getUUID());
        if (orb == null || orb.isRemoved() || orb.state() != STATE_HOLD || orb.age < FORM_TICKS) return;
        if (!(event.getTarget() instanceof LivingEntity t) || !t.isAlive()) return;
        SpellContext c = orb.context();
        if (c == null || !LbUtil.affects(c, t)) return;
        event.setCanceled(true);
        orb.startGrind(sl, c, t);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide) HELD.values().removeIf(o -> o == this);
    }

    // =====================================================================================
    //  Cliente: presa à mão sem atraso; lançada = previsão em linha reta
    // =====================================================================================
    @Override
    protected void clientTick() {
        if (isHeld() && state() != STATE_THROWN && ownerEntity() instanceof LivingEntity owner) {
            Vec3 p = handPoint(owner, 1F);
            setPos(p.x, p.y, p.z);
        } else if (state() == STATE_THROWN) {
            Vec3 n = position().add(dir().scale(speed()));
            setPos(n.x, n.y, n.z);
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (isHeld() && ownerEntity() != null) return;
        if (state() == STATE_THROWN && position().distanceToSqr(x, y, z) < 1.0) return;
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }
}
