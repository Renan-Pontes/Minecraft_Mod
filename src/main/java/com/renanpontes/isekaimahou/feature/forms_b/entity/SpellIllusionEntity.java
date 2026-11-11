package com.renanpontes.isekaimahou.feature.forms_b.entity;

import java.util.Optional;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Ilusão: clone sombrio do conjurador (com a skin dele, tingida da cor da magia). Atrai a agressão
 * dos monstros em 16 blocos e, ao sumir ou morrer, explode aplicando os efeitos em área (raio 3).
 */
public class SpellIllusionEntity extends PathfinderMob implements GeoEntity, OwnableEntity {
    public static final double TAUNT_RANGE = 16.0;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.spell_illusion.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.spell_illusion.walk");

    private static final EntityDataAccessor<Integer> DATA_ELEMENT = SynchedEntityData.defineId(SpellIllusionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(SpellIllusionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(SpellIllusionEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private CompoundTag spellTag = new CompoundTag();
    private SpellContext ctx;
    private int lifetime = 200;
    private int age;
    private boolean exploded;
    /** Quem conjurou era jogador: se ele estiver fora do mundo na explosão, ela é só visual (sem saber aliados). */
    private boolean byPlayer;

    public SpellIllusionEntity(EntityType<? extends SpellIllusionEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, TAUNT_RANGE);
    }

    /** Liga o clone à magia. Chame antes de {@code addFreshEntity}. */
    public void setup(SpellContext ctx, int lifetime, float health) {
        this.ctx = ctx;
        this.spellTag = ctx.save();
        this.lifetime = lifetime;
        this.byPlayer = ctx.caster() instanceof Player;
        entityData.set(DATA_ELEMENT, ctx.element().ordinal());
        entityData.set(DATA_COLOR, ctx.color() & 0xFFFFFF);
        entityData.set(DATA_OWNER, Optional.ofNullable(ctx.casterId()));
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(health);
        setHealth(health);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ELEMENT, 0);
        builder.define(DATA_COLOR, Element.ARCANE.color);
        builder.define(DATA_OWNER, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Mob.class, (float) TAUNT_RANGE));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public Element element() {
        Element[] v = Element.values();
        int i = entityData.get(DATA_ELEMENT);
        return i >= 0 && i < v.length ? v[i] : Element.ARCANE;
    }

    public int color() { return entityData.get(DATA_COLOR); }

    @Override
    public UUID getOwnerUUID() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    /**
     * Contexto da magia (servidor). Recarrega se o conjurador saiu, morreu ou renasceu (objeto novo) — a partir do
     * estado ATUAL (disparos de ligação já feitos continuam contando).
     */
    private SpellContext context() {
        if (!(level() instanceof ServerLevel sl)) return null;
        boolean stale = ctx == null || (ctx.caster() != null && ctx.caster().isRemoved())
                || (ctx.caster() == null && ctx.casterId() != null && tickCount % 20 == 0);
        if (stale) {
            if (ctx != null) spellTag = ctx.save();
            SpellContext fresh = SpellContext.load(sl, spellTag);
            if (fresh != null || ctx == null) ctx = fresh;
        }
        return ctx;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        SpellContext c = context();
        if (c == null) {
            discard();
            return;
        }
        if (age >= lifetime) {
            explode();
            discard();
            return;
        }
        if (age % 10 == 1) taunt(c);
    }

    /**
     * Monstros (e quem mira no conjurador/aliados) em 16 blocos passam a atacar o clone.
     * Se quem conjurou é um monstro, só quem já mirava nele/aliados é atraído.
     */
    private void taunt(SpellContext c) {
        boolean monsterCaster = c.caster() instanceof Enemy;
        for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(TAUNT_RANGE),
                m -> m.isAlive() && m != this && !c.isAlly(m) && m.distanceToSqr(this) <= TAUNT_RANGE * TAUNT_RANGE)) {
            if (mob.getType().is(Tags.EntityTypes.BOSSES) || mob.getTarget() == this) continue;
            LivingEntity cur = mob.getTarget();
            boolean hostile = (mob instanceof Enemy && !monsterCaster) || (cur != null && c.isAlly(cur));
            if (!hostile || !mob.canAttack(this)) continue;
            mob.setTarget(this);
            if (level() instanceof ServerLevel sl && random.nextInt(3) == 0) {
                sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 1, 0.1, 0.1, 0.1, 0);
            }
        }
    }

    /**
     * Explosão final: aplica os efeitos em área (uma vez só). Magias programáveis: o inimigo mais próximo atingido
     * é o impacto (Ao Acertar/Saltar) e a explosão é o fim do estágio (Ao Terminar).
     */
    private void explode() {
        if (exploded || !(level() instanceof ServerLevel sl)) return;
        exploded = true;
        SpellContext c = context();
        Vec3 center = position().add(0, getBbHeight() / 2, 0);
        float r = c != null ? c.formStats().radius(3.0F) : 3.0F;
        if (c != null && !(byPlayer && c.caster() == null)) {
            if (FormsBUtil.wantsImpact(c)) {
                LivingEntity hit = FormsBUtil.areaHit(c, center, r, this);
                if (hit != null) FormsBUtil.impactOn(c, hit);
            }
            c.resolveArea(center, r, this, c.formStats().sensitive);
            // Ao Terminar só com o conjurador vivo (nenhum estágio novo sai de quem já morreu)
            if (c.caster() != null && c.caster().isAlive()) c.end(center, c.look());
        }
        ElementFx.burst(sl, center, element(), 40, 0.6);
        ElementFx.ring(sl, position(), element(), r, Math.max(18, (int) (r * 10)));
        sl.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 16, 0.4, 0.6, 0.4, 0.02);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 0.8F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    private void clientParticles() {
        boolean reduced = FormsBUtil.reducedParticles();
        if (reduced && age % 4 != 0) return;
        // Silhueta "vazando" fumaça escura e pó da cor da magia
        double x = getX() + (random.nextDouble() - 0.5) * getBbWidth();
        double y = getY() + random.nextDouble() * getBbHeight();
        double z = getZ() + (random.nextDouble() - 0.5) * getBbWidth();
        level().addParticle(FormsBUtil.dust(color(), 0.8F), x, y, z, 0, 0.02, 0);
        if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.SMOKE, x, getY() + 0.1, z, 0, 0.02, 0);
    }

    // =====================================================================================
    //  Dano e morte
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && isOwnerSide(attacker) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    private boolean isOwnerSide(Entity e) {
        UUID owner = getOwnerUUID();
        if (owner == null) return false;
        if (e.getUUID().equals(owner)) return true;
        return e instanceof OwnableEntity o && owner.equals(o.getOwnerUUID());
    }

    @Override
    public boolean isAlliedTo(Entity e) {
        return isOwnerSide(e) || super.isAlliedTo(e);
    }

    @Override
    public void die(DamageSource source) {
        explode();
        super.die(source);
    }

    /** Some na hora (sem a animação de tombar), com a nuvem de "poof". */
    @Override
    protected void tickDeath() {
        if (!level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected boolean shouldDropLoot() { return false; }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean canBeLeashed() { return false; }

    @Override
    public boolean canUsePortal(boolean allowPassengers) { return false; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ILLUSIONER_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return null; }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Estado ATUAL do contexto (disparos/orçamento), não o da criação
        if (ctx != null) spellTag = ctx.save();
        tag.put("Spell", spellTag);
        tag.putInt("Lifetime", lifetime);
        tag.putInt("Age", age);
        tag.putInt("Color", color());
        tag.putBoolean("ByPlayer", byPlayer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spellTag = tag.getCompound("Spell");
        lifetime = tag.contains("Lifetime") ? tag.getInt("Lifetime") : 200;
        age = tag.getInt("Age");
        byPlayer = tag.getBoolean("ByPlayer");
        entityData.set(DATA_COLOR, tag.getInt("Color"));
        Element e = Element.byId(spellTag.getString("element"));
        if (e != null) entityData.set(DATA_ELEMENT, e.ordinal());
        entityData.set(DATA_OWNER, spellTag.hasUUID("caster") ? Optional.of(spellTag.getUUID("caster")) : Optional.empty());
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
