package com.renanpontes.isekaimahou.feature.legends_g.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.legends_g.LgRegistry;
import com.renanpontes.isekaimahou.feature.legends_g.LgUtil;
import com.renanpontes.isekaimahou.feature.legends_g.glyph.LloydTechniques;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * Grim — o grimório-demônio do Lloyd. Flutua sobre o ombro direito do dono (não colide, não é atingível, não salva:
 * tipo {@code noSave}) e, enquanto dura:
 * <ul>
 *   <li>a cada {@link #SHOT_EVERY} ticks atira uma bala de mana no inimigo mais perto que o dono enxerga;</li>
 *   <li>a cada {@link #DEVOUR_EVERY} ticks DEVORA projéteis e entidades de magia inimigas a até {@link #DEVOUR_RADIUS}
 *       blocos do dono (cada uma devolve 3% de mana a um jogador);</li>
 *   <li>engole uma conjuração inimiga PENDENTE da Pilha por perto (contramágica, poder ×2), no máx. uma a cada 3 s.</li>
 * </ul>
 * Fica quieto (só acompanha) com o dono em Modo Paz ou numa zona segura.
 */
public class GrimoireEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(GrimoireEntity.class, EntityDataSerializers.INT);
    /** Tick (do mundo) da última "mordida" — o renderer abre e fecha a boca (as páginas) quando come. */
    private static final EntityDataAccessor<Long> DATA_BITE = SynchedEntityData.defineId(GrimoireEntity.class, EntityDataSerializers.LONG);

    public static final int SHOT_EVERY = 30;
    public static final int DEVOUR_EVERY = 10;
    public static final double DEVOUR_RADIUS = 5.0;
    public static final double SHOT_RANGE = 16.0;
    public static final float SHOT_DAMAGE = 5F;

    /** Um Grim por dono. */
    private static final Map<UUID, GrimoireEntity> ACTIVE = new HashMap<>();

    @Nullable private SpellContext ctx;
    @Nullable private SpellStats stats;
    @Nullable private LivingEntity owner;
    private int age;
    private int nextCounter;

    public GrimoireEntity(EntityType<? extends GrimoireEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    /** Invoca (ou renova) o Grim do dono. */
    public static void summon(SpellContext ctx, SpellStats stats, LivingEntity owner, int life) {
        ServerLevel level = ctx.level();
        GrimoireEntity g = ACTIVE.get(owner.getUUID());
        boolean fresh = g == null || g.isRemoved() || g.level() != level;
        if (fresh) {
            g = new GrimoireEntity(LgRegistry.GRIMOIRE.get(), level);
            Vec3 p = anchor(owner, 0);
            g.setPos(p.x, p.y, p.z);
            g.setYRot(owner.getYRot());
        }
        g.ctx = ctx;
        g.stats = stats;
        g.owner = owner;
        g.age = 0;
        g.entityData.set(DATA_LIFE, Math.max(20, life));
        if (fresh) {
            level.addFreshEntity(g);
            ACTIVE.put(owner.getUUID(), g);
        }
        Vec3 p = g.position();
        ElementFx.portal(level, p, null, Element.ARCANE, LloydTechniques.GRIM_COLOR, 0.9F, 3, 20);
        LgUtil.sound(level, p, SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 1.3F);
        LgUtil.sound(level, p, SoundEvents.BOOK_PAGE_TURN, 1.4F, 0.7F);
        LgUtil.actionbar(owner, Component.translatable("message.isekai_mahou.legends_g.grim_summon", life / 20)
                .withStyle(ChatFormatting.RED));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    /** Onde o Grim flutua: acima e à direita do ombro, balançando de leve. */
    public static Vec3 anchor(LivingEntity o, int age) {
        float yaw = o.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        return o.position().add(0, o.getBbHeight() + 0.2 + 0.12 * Mth.sin(age * 0.15F), 0)
                .add(right.scale(0.8)).add(fwd.scale(-0.25));
    }

    public int life() { return entityData.get(DATA_LIFE); }

    /** Ticks desde a última mordida (grande = faz tempo). */
    public long sinceBite() { return level().getGameTime() - entityData.get(DATA_BITE); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE, 600);
        builder.define(DATA_BITE, -1000L);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel l = (ServerLevel) level();
        age++;
        if (ctx == null || stats == null || owner == null || !owner.isAlive() || owner.level() != l || age > life()) {
            vanish(l);
            return;
        }
        Vec3 target = anchor(owner, age);
        Vec3 now = position();
        Vec3 next = now.distanceToSqr(target) > 64 ? target : now.add(target.subtract(now).scale(0.45));
        setPos(next.x, next.y, next.z);
        setYRot(Mth.rotLerp(0.35F, getYRot(), owner.getYRot()));
        if (age % 5 == 0) {
            l.sendParticles(new DustParticleOptions(new Vector3f(0.7F, 0.12F, 0.25F), 0.9F), getX(), getY() + 0.1, getZ(),
                    1, 0.2, 0.1, 0.2, 0);
        }
        if (age > life() - 20 && age % 4 == 0) l.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 2, 0.2, 0.1, 0.2, 0.01);
        if (LgUtil.calm(owner)) return;
        if (age % DEVOUR_EVERY == 0) devour(l);
        if (age % 20 == 0 && age >= nextCounter) swallowPending(l);
        if (age % SHOT_EVERY == 0) shoot(l);
    }

    /** Come magias inimigas (projéteis, zonas, runas...) perto do dono. */
    private void devour(ServerLevel l) {
        int n = ArcaneUtil.destroyHostileMagic(ctx, LgUtil.center(owner), DEVOUR_RADIUS);
        if (n <= 0) return;
        bite(l);
        if (owner instanceof ServerPlayer sp) {
            MagicData d = MagicApi.get(sp);
            d.addMana(d.maxMana() * 0.03F * n);
            LgUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_g.grim_devour", n).withStyle(ChatFormatting.RED));
        }
    }

    /** Engole a conjuração inimiga pendente mais próxima (contramágica). */
    private void swallowPending(ServerLevel l) {
        for (SpellStack.Pending p : SpellStack.near(l, owner.position(), 8.0)) {
            if (!p.active() || !p.counterable() || !SpellStack.isHostile(p, owner)) continue;
            nextCounter = age + 60;
            float power = 2F * Math.max(0.5F, stats.power() * ctx.powerMult());
            if (SpellStack.counter(l, p, owner, power) == SpellStack.CounterResult.COUNTERED) {
                bite(l);
                LgUtil.actionbar(owner, Component.translatable("message.isekai_mahou.legends_g.grim_swallow").withStyle(ChatFormatting.RED));
            }
            return;
        }
    }

    private void shoot(ServerLevel l) {
        List<LivingEntity> foes = LgUtil.foesAround(ctx, owner.position(), SHOT_RANGE, 6);
        for (LivingEntity e : foes) {
            if (!owner.hasLineOfSight(e)) continue;
            Vec3 from = position();
            Vec3 to = LgUtil.center(e);
            ElementFx.beam(l, from, to, Element.ARCANE, LloydTechniques.GRIM_COLOR, 0.18F, 1);
            LgUtil.sound(l, from, SoundEvents.SHULKER_SHOOT, 0.7F, 1.5F);
            ctx.damage(e, SHOT_DAMAGE, stats, this, Element.ARCANE);
            return;
        }
    }

    private void bite(ServerLevel l) {
        entityData.set(DATA_BITE, l.getGameTime());
        LgUtil.sound(l, position(), SoundEvents.GENERIC_EAT, 1.0F, 0.6F);
        l.sendParticles(ParticleTypes.WITCH, getX(), getY() + 0.1, getZ(), 8, 0.25, 0.15, 0.25, 0.05);
    }

    private void vanish(ServerLevel l) {
        l.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.02);
        LgUtil.sound(l, position(), SoundEvents.BOOK_PUT, 1.0F, 0.6F);
        if (owner != null && ACTIVE.get(owner.getUUID()) == this) ACTIVE.remove(owner.getUUID());
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (owner != null && ACTIVE.get(owner.getUUID()) == this) ACTIVE.remove(owner.getUUID());
        super.remove(reason);
    }

    // ---------------- É magia: sem física nem interação ----------------
    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public boolean isIgnoringBlockTriggers() { return true; }

    @Override
    public boolean canUsePortal(boolean allowPassengers) { return false; }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 96 * 96; }

    @Override
    public boolean displayFireAnimation() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
