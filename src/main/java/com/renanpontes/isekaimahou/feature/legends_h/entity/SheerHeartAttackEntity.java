package com.renanpontes.isekaimahou.feature.legends_h.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.legends_f.entity.LfSpellEntity;
import com.renanpontes.isekaimahou.feature.legends_h.LhRegistry;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * SHEER HEART ATTACK: o tanquinho com caveira que anda pelo chão atrás do alvo MAIS QUENTE (pegando fogo, criaturas
 * de fogo, jogadores...) e, ao encostar, explode aplicando os efeitos da magia — e continua: é indestrutível. Para
 * depois de N explosões ou quando o tempo acaba. Um por conjurador. Com o dono em Modo Paz ou numa cidade, só segue
 * o dono e não ataca.
 */
public class SheerHeartAttackEntity extends LfSpellEntity {
    public static final int COLOR = 0xFF8A3A;
    private static final Map<UUID, SheerHeartAttackEntity> BY_OWNER = new HashMap<>();

    private int contacts = 3;
    private float speed = 0.28F;
    private int rearm;
    @Nullable
    private LivingEntity target;

    public SheerHeartAttackEntity(EntityType<? extends SheerHeartAttackEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    /** Solta um tanque novo (o anterior do mesmo dono se desfaz). */
    public static void spawn(SpellContext ctx, SpellStats stats, Vec3 at, int life, int contacts, float speed) {
        LivingEntity owner = ctx.caster();
        if (owner != null) {
            SheerHeartAttackEntity old = BY_OWNER.remove(owner.getUUID());
            if (old != null && !old.isRemoved()) old.fizzle();
        }
        SheerHeartAttackEntity e = new SheerHeartAttackEntity(LhRegistry.SHEER_HEART_ATTACK.get(), ctx.level());
        e.bind(ctx, stats, COLOR, life);
        e.contacts = contacts;
        e.speed = speed;
        e.moveTo(at.x, at.y, at.z, owner != null ? owner.getYRot() : 0F, 0F);
        ctx.level().addFreshEntity(e);
        if (owner != null) BY_OWNER.put(owner.getUUID(), e);
        LhUtil.sound(ctx.level(), at, SoundEvents.IRON_GOLEM_STEP, 1.0F, 1.6F);
        LhUtil.sound(ctx.level(), at, SoundEvents.PISTON_EXTEND, 0.8F, 1.4F);
    }

    @Override
    public float maxUpStep() {
        return 1.0F;
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        LivingEntity owner = ctx.caster();
        if (age >= life() || contacts <= 0 || owner == null || !owner.isAlive() || owner.level() != level) {
            fizzle();
            return;
        }
        boolean calm = LhUtil.calm(owner);
        if (target != null && (!target.isAlive() || target.level() != level || target.distanceToSqr(this) > 32 * 32 || calm)) target = null;
        if (!calm && (target == null || age % 20 == 0)) {
            LivingEntity hot = hottest(ctx);
            if (hot != null && hot != target) {
                target = hot;
                LhUtil.sound(level, position(), SoundEvents.NOTE_BLOCK_BIT.value(), 1.0F, 1.8F);
                if (owner instanceof ServerPlayer sp && age > 5) {
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_h.sha_lock", hot.getDisplayName())
                            .withStyle(ChatFormatting.GOLD), true);
                }
                if (hot instanceof ServerPlayer vp) {
                    vp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_h.sha_look").withStyle(ChatFormatting.GOLD), true);
                }
            }
        }
        Vec3 goal = target != null ? target.position() : owner.position().add(owner.getLookAngle().multiply(1.5, 0, 1.5));
        Vec3 to = goal.subtract(position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        Vec3 v = getDeltaMovement();
        double vx = 0, vz = 0;
        if (flat.lengthSqr() > (target != null ? 0.04 : 4.0)) {
            Vec3 d = flat.normalize().scale(speed);
            vx = d.x;
            vz = d.z;
            setYRot((float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG));
        }
        double vy = onGround() ? -0.04 : Math.max(-0.8, v.y - 0.08);
        if (horizontalCollision && onGround()) vy = 0.42;
        setDeltaMovement(vx, vy, vz);
        move(MoverType.SELF, getDeltaMovement());
        if (isInWater()) setDeltaMovement(getDeltaMovement().add(0, 0.06, 0));
        // Fumacinha do escapamento
        if (age % 3 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.35, getZ(), 1, 0.05, 0.02, 0.05, 0.01);
        // Encostou: BOOM, e segue
        if (target != null && age >= rearm && getBoundingBox().inflate(0.35).intersects(target.getBoundingBox())) {
            boom(level, ctx, target);
        }
    }

    /** O alvo mais quente num raio de 24: pegando fogo > criaturas de fogo > jogadores > o resto; mais perto desempata. */
    @Nullable
    private LivingEntity hottest(SpellContext ctx) {
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity e : LhUtil.foesAround(ctx, position(), 24, 24)) {
            double score = 10;
            if (e.isOnFire()) score += 100;
            if (e.fireImmune()) score += 60;
            if (e instanceof Player) score += 20;
            score -= e.distanceTo(this);
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private void boom(ServerLevel level, SpellContext ctx, LivingEntity t) {
        contacts--;
        rearm = age + 25;
        Vec3 p = position().add(0, 0.3, 0);
        if (!LhUtil.safeAt(level, p)) {
            t.invulnerableTime = 0;
            ctx.resolveOnEntity(t, this);
        }
        ElementFx.flash(level, p, 0xFFD0A0, 2.5F);
        ElementFx.nova(level, p, Element.FIRE, COLOR, 2.2F, 3);
        level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        LhUtil.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 1.1F, 1.3F);
        // O tanque é arremessado para trás com a própria explosão
        Vec3 back = position().subtract(t.position());
        back = back.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : back.normalize().scale(0.5);
        setDeltaMovement(back.x, 0.35, back.z);
        if (contacts <= 0) fizzle();
    }

    private void fizzle() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 8, 0.2, 0.2, 0.2, 0.02);
            LhUtil.sound(sl, position(), SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.4F);
        }
        if (ctx != null && ctx.casterId() != null) BY_OWNER.remove(ctx.casterId(), this);
        discard();
    }

    public static void clearAll() {
        BY_OWNER.clear();
    }
}
