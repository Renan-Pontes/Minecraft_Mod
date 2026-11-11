package com.renanpontes.isekaimahou.feature.class_ranger.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerCompanions;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerMarks;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerUtil;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base dos companheiros do Ranger que lutam (Lobo e Águia): persistentes, crescem com o dono ({@link #applyLevel}:
 * vida, dano, armadura e TAMANHO), mudam de aparência em 3 estágios (Filhote 1–3, Adulto 4–7, Alfa 8–10), seguem
 * o dono, caçam monstros perto dele e PREFEREM os alvos que o dono marcou. Um companheiro substituído (o dono
 * chamou outro) ou sem dono (ovo) some sozinho.
 */
public abstract class RangerCompanion extends TamableAnimal implements GeoEntity {
    public static final ResourceLocation HEALTH_ID = IsekaiMahou.id("ranger_companion_health");
    public static final ResourceLocation DAMAGE_ID = IsekaiMahou.id("ranger_companion_damage");
    public static final ResourceLocation ARMOR_ID = IsekaiMahou.id("ranger_companion_armor");
    public static final ResourceLocation SCALE_ID = IsekaiMahou.id("ranger_companion_scale");
    public static final int MAX_LEVEL = 10;
    /** Companheiro de ovo (sem dono) desaparece depois deste tempo. */
    private static final int WILD_LIFETIME = 5 * 60 * 20;
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(RangerCompanion.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected RangerCompanion(EntityType<? extends RangerCompanion> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    protected abstract double healthPerLevel();

    protected abstract double damagePerLevel();

    /** Chave do companheiro no registro do dono ({@link RangerCompanions#WOLF} / {@link RangerCompanions#EAGLE}). */
    public abstract String slotKey();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEVEL, 1);
    }

    public int companionLevel() {
        return entityData.get(DATA_LEVEL);
    }

    /** 0 = Filhote, 1 = Adulto, 2 = Alfa (decide a textura e o título). */
    public int stage() {
        return RangerCompanions.stageOf(companionLevel());
    }

    /** Ajusta vida, dano, armadura e tamanho ao nível (1..10); cura a diferença quando sobe. */
    public void applyLevel(int lvl) {
        lvl = Math.max(1, Math.min(MAX_LEVEL, lvl));
        int old = companionLevel();
        entityData.set(DATA_LEVEL, lvl);
        float before = getMaxHealth();
        set(Attributes.MAX_HEALTH, HEALTH_ID, healthPerLevel() * (lvl - 1));
        set(Attributes.ATTACK_DAMAGE, DAMAGE_ID, damagePerLevel() * (lvl - 1));
        set(Attributes.ARMOR, ARMOR_ID, 0.6 * (lvl - 1));
        set(Attributes.SCALE, SCALE_ID, 0.05 * (lvl - 1));
        if (lvl > old || getHealth() > getMaxHealth()) setHealth(Math.min(getMaxHealth(), getHealth() + Math.max(0, getMaxHealth() - before)));
    }

    private void set(Holder<Attribute> attr, ResourceLocation id, double v) {
        AttributeInstance inst = getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (v != 0) inst.addPermanentModifier(new AttributeModifier(id, v, AttributeModifier.Operation.ADD_VALUE));
    }

    // =====================================================================================
    //  Alvos
    // =====================================================================================

    /** Caça monstros perto do dono (sem dono: perto dele). Nunca creepers (explodiriam o dono). */
    protected boolean huntable(LivingEntity e) {
        if (e instanceof Creeper || !(e instanceof Enemy)) return false;
        LivingEntity owner = getOwner();
        if (owner == null) return e.distanceToSqr(this) < 16 * 16;
        return e.distanceToSqr(owner) < 16 * 16 && !RangerUtil.isFriend(owner, e);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (RangerUtil.isFriend(owner, target) || target instanceof Creeper) return false;
        return !(target instanceof Player p && owner instanceof Player o && !o.canHarmPlayer(p));
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        LivingEntity owner = getOwner();
        if (owner != null && (other == owner || RangerUtil.isFriend(owner, other))) return true;
        return super.isAlliedTo(other);
    }

    /** Dono com a mão vazia: alterna "Fica!" / "Segue!". */
    @Override
    public net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || !isOwnedBy(player) || !player.getItemInHand(hand).isEmpty()) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide) {
            boolean sit = !isOrderedToSit();
            setOrderedToSit(sit);
            setInSittingPose(sit);
            setTarget(null);
            getNavigation().stop();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    sit ? "message.isekai_mahou.class_ranger.stay" : "message.isekai_mahou.class_ranger.follow", getDisplayName()), true);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Ordem do dono: vá para cima deste alvo. */
    public void command(LivingEntity target) {
        setOrderedToSit(false);
        setInSittingPose(false);
        setTarget(target);
    }

    /** O alvo atual ainda vale (vivo, mesmo mundo, perto). */
    protected boolean targetValid() {
        LivingEntity t = getTarget();
        return t != null && t.isAlive() && t.level() == level() && t.distanceToSqr(this) < 40 * 40;
    }

    /** Alvo prioritário: o inimigo marcado pelo dono mais próximo (até 24 blocos do dono). */
    protected final class MarkedTargetGoal extends TargetGoal {
        private LivingEntity found;

        public MarkedTargetGoal() {
            super(RangerCompanion.this, false, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (tickCount % 10 != 0) return false;
            LivingEntity owner = getOwner();
            if (owner == null || !(level() instanceof ServerLevel)) return false;
            LivingEntity cur = getTarget();
            if (cur != null && cur.isAlive() && RangerMarks.isMarkedBy(cur, owner.getUUID())) return false;
            found = RangerMarks.nearestMarked(owner, 24.0);
            return found != null && found != cur && wantsToAttack(found, owner);
        }

        @Override
        public void start() {
            setTarget(found);
            super.start();
        }
    }

    // =====================================================================================
    //  Tick, dano e morte
    // =====================================================================================

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        // regenera fora de combate
        if (tickCount % 40 == 0 && getTarget() == null && getHealth() < getMaxHealth()) heal(1.0F + companionLevel() * 0.3F);
        if (tickCount % 100 == 0) {
            if (getOwnerUUID() == null) {
                if (tickCount > WILD_LIFETIME) vanish(sl);
            } else if (!RangerCompanions.isCurrent(sl, this)) {
                vanish(sl);
            }
        }
        // Alfa: brilho espiritual discreto
        if (stage() == 2 && tickCount % 12 == 0) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, getBbWidth() * 0.4, 0.2,
                    getBbWidth() * 0.4, 0);
        }
    }

    /** Some em fumaça (companheiro trocado/sem dono). */
    public void vanish(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) RangerCompanions.onDeath(sl, this);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        LivingEntity owner = getOwner();
        // magias e flechas do próprio dono não ferem o companheiro
        if (owner != null && source.getEntity() == owner) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canMate(Animal other) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    protected int getBaseExperienceReward() {
        return 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CompanionLevel", companionLevel());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("CompanionLevel")) applyLevel(tag.getInt("CompanionLevel"));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
