package com.renanpontes.isekaimahou.feature.origins_b.entity;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.origins_b.ObUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base das invocações do módulo (Diabrete, Demônio Maior, Servo Morto-Vivo, Guerreiro de Ossos).
 * <ul>
 *   <li>Dono (UUID): segue, defende, ataca o que o dono ataca, nunca fere aliados do dono.</li>
 *   <li>Tempo de vida: some quando acaba ({@code -1} = permanente, ex.: presente do Rito Ossuário).</li>
 *   <li>Poder: multiplica o dano (vem dos aumentos da magia).</li>
 *   <li>Animação de chegada sincronizada ({@link #arrivalTicks()}): fica parada enquanto "emerge".</li>
 *   <li>O dono agachado com a mão vazia alterna "ficar aqui" / "seguir".</li>
 * </ul>
 * Sem dono (ovo do criativo) a criatura fica no mundo e caça monstros por perto.
 */
public abstract class ObSummonEntity extends PathfinderMob implements OwnableEntity, GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_ARRIVAL = SynchedEntityData.defineId(ObSummonEntity.class, EntityDataSerializers.INT);
    /** Ticks de vida restantes só nos últimos 3s (para o cliente desvanecer o modelo); -1 fora disso. */
    private static final EntityDataAccessor<Integer> DATA_FADE = SynchedEntityData.defineId(ObSummonEntity.class, EntityDataSerializers.INT);
    /** Dono visto pelo cliente (interação "ficar/seguir" responde na hora). */
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(ObSummonEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;
    private int lifetime = -1;
    private float power = 1.0F;
    private boolean staying;

    protected ObSummonEntity(EntityType<? extends ObSummonEntity> type, Level level) {
        super(type, level);
    }

    // ------------------------------------------------------------------ configuração
    /** Configura a invocação feita por uma magia. */
    public void setup(LivingEntity owner, int lifetime, float power) {
        setOwnerId(owner != null ? owner.getUUID() : null);
        this.lifetime = lifetime;
        this.power = Math.max(0.25F, power);
        setPersistenceRequired();
        entityData.set(DATA_ARRIVAL, arrivalLength());
    }

    /** Usado pelo Rito Ossuário (por reflexão) para dar servos permanentes ao Necromante. */
    public void setOwnerUUID(UUID id) {
        setOwnerId(id);
        setPersistenceRequired();
        if (entityData.get(DATA_ARRIVAL) == 0 && tickCount < 5) entityData.set(DATA_ARRIVAL, arrivalLength());
    }

    private void setOwnerId(UUID id) {
        this.owner = id;
        entityData.set(DATA_OWNER, Optional.ofNullable(id));
    }

    public boolean isTemporary() {
        return lifetime >= 0;
    }

    public int lifetime() {
        return lifetime;
    }

    public float power() {
        return power;
    }

    /** Ticks restantes da animação de chegada (sincronizado com o cliente). */
    public int arrivalTicks() {
        return entityData.get(DATA_ARRIVAL);
    }

    /** Vida restante vista pelo cliente: só nos últimos 60 ticks, -1 fora disso (ou permanente). */
    public int lifetimeClient() {
        return entityData.get(DATA_FADE);
    }

    /** Duração da animação de chegada (emergir do chão, montar os ossos...). 0 = nenhuma. */
    protected int arrivalLength() {
        return 0;
    }

    /** Elemento do dano desta criatura (para o XP de abate do dono e as reações). */
    protected abstract Element attackElement();

    /** Efeito visual/sonoro de quando a invocação se desfaz (fim do tempo, limite, Explodir Cadáver). */
    protected abstract void dissolveFx(ServerLevel level);

    /** Voadores (Diabrete) podem reaparecer no ar junto do dono; os outros só em chão firme. */
    protected boolean canFly() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ARRIVAL, 0);
        builder.define(DATA_FADE, -1);
        builder.define(DATA_OWNER, Optional.empty());
    }

    /** Parada enquanto chega: sem IA nem movimento. */
    @Override
    public boolean isImmobile() {
        return super.isImmobile() || arrivalTicks() > 0;
    }

    // ------------------------------------------------------------------ dono e aliados
    @Override
    public UUID getOwnerUUID() {
        return level().isClientSide ? entityData.get(DATA_OWNER).orElse(null) : owner;
    }

    @Override
    public LivingEntity getOwner() {
        UUID id = getOwnerUUID();
        if (id == null) return null;
        if (level() instanceof ServerLevel sl) return ObUtil.owner(sl, id);
        return level().getPlayerByUUID(id);
    }

    @Override
    public boolean isAlliedTo(Entity e) {
        UUID id = getOwnerUUID();
        if (id != null && (id.equals(e.getUUID()) || (e instanceof OwnableEntity oe && id.equals(oe.getOwnerUUID())))) {
            return true;
        }
        return super.isAlliedTo(e);
    }

    /** Aliado do dono (ou, sem dono, qualquer jogador e outras invocações selvagens). */
    public boolean isFriend(Entity e) {
        if (e == null) return false;
        if (owner == null) return e instanceof Player || (e instanceof ObSummonEntity s && s.owner == null);
        return level() instanceof ServerLevel sl && ObUtil.isAlly(sl, owner, e);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isFriend(target) && super.canAttack(target);
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        int arrival = entityData.get(DATA_ARRIVAL);
        if (arrival > 0 && !level().isClientSide) {
            entityData.set(DATA_ARRIVAL, arrival - 1);
            if (level() instanceof ServerLevel sl) arrivalTick(sl, arrival);
        }
    }

    /** Chamado a cada tick da chegada (servidor), com os ticks que faltam. */
    protected void arrivalTick(ServerLevel level, int ticksLeft) {}

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        LivingEntity o = getOwner();
        if (owner != null && isTemporary()) {
            // Temporárias: somem no fim do tempo ou se o dono saiu do mundo/da dimensão
            if (--lifetime <= 0 || (o == null && tickCount > 40)) {
                dissolve();
                return;
            }
            entityData.set(DATA_FADE, lifetime < 60 ? lifetime : -1);
            if (lifetime < 60 && lifetime % 6 == 0) {
                sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.6, getZ(),
                        3, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.01);
            }
        }
        // Alvo inválido, fora do alcance ou dono longe demais: larga a briga (alvos dados pela magia não passam
        // pelas checagens dos TargetGoals e prenderiam a invocação atrás de um inimigo inalcançável)
        LivingEntity t = getTarget();
        if (t != null) {
            double leash = getAttributeValue(Attributes.FOLLOW_RANGE) + 8.0;
            if (isFriend(t) || !t.isAlive() || distanceToSqr(t) > leash * leash
                    || (o != null && !staying && distanceToSqr(o) > 32 * 32)) {
                setTarget(null);
            }
        }
        if (o != null && !staying && getTarget() == null && distanceToSqr(o) > 28 * 28 && o.level() == level()) {
            // Ficou muito para trás: reaparece junto do dono
            teleportToOwner(sl, o);
        }
    }

    /**
     * Reaparece atrás do dono, num lugar onde caiba. Quem não voa só reaparece sobre chão firme (até 6 blocos
     * abaixo do dono): com o dono voando (Asas/Lich) ele espera o pouso em vez de cair do céu.
     */
    private void teleportToOwner(ServerLevel sl, LivingEntity o) {
        Vec3 base = o.position();
        if (canFly()) {
            base = base.add(0, 1.0, 0);
        } else {
            BlockPos.MutableBlockPos p = BlockPos.containing(base).mutable();
            int drop = 0;
            while (drop < 6 && !hasFloor(sl, p)) {
                p.move(0, -1, 0);
                drop++;
            }
            if (!hasFloor(sl, p)) return;
            base = new Vec3(base.x, p.getY(), base.z);
        }
        Vec3 back = o.getLookAngle().multiply(1, 0, 1);
        if (back.lengthSqr() < 1.0E-4) back = new Vec3(0, 0, 1);
        Vec3 spot = ObUtil.freeSpot(sl, getType(), base.subtract(back.normalize().scale(1.5)), 1.2);
        // Sem lugar onde caiba (corredor apertado para o Demônio): tenta de novo quando o dono andar
        if (!sl.noCollision(getType().getSpawnAABB(spot.x, spot.y, spot.z))) return;
        dissolveFx(sl);
        teleportTo(spot.x, spot.y, spot.z);
        getNavigation().stop();
        resetFallDistance();
        dissolveFx(sl);
    }

    private static boolean hasFloor(ServerLevel sl, BlockPos feet) {
        BlockPos below = feet.below();
        return !sl.getBlockState(below).getCollisionShape(sl, below).isEmpty() || !sl.getFluidState(below).isEmpty();
    }

    /** Desfaz a invocação (sem drops nem XP). */
    public void dissolve() {
        if (level() instanceof ServerLevel sl) dissolveFx(sl);
        discard();
    }

    // ------------------------------------------------------------------ combate
    /** Golpe corpo a corpo: dano de magia creditado ao dono (XP de abate e contadores vão para ele). */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(level() instanceof ServerLevel sl)) return false;
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * power;
        boolean ok = target instanceof LivingEntity le
                ? ObUtil.spellHurt(sl, le, dmg, this, getOwner(), attackElement())
                : target.hurt(ObUtil.spellSource(sl, this, getOwner()), dmg);
        if (ok) {
            setLastHurtMob(target);
            // O dano é creditado ao dono (XP/abates), mas a vítima revida contra a invocação: ela segura a atenção
            if (target instanceof LivingEntity le && le.isAlive()) le.setLastHurtByMob(this);
            double kb = getAttributeValue(Attributes.ATTACK_KNOCKBACK);
            if (kb > 0 && target instanceof LivingEntity le) {
                le.knockback(kb * 0.5, Math.sin(Math.toRadians(getYRot())), -Math.cos(Math.toRadians(getYRot())));
            }
            onMeleeHit(sl, target);
        }
        return ok;
    }

    /** Extra de cada criatura ao acertar (partículas, efeitos de status, animação). */
    protected void onMeleeHit(ServerLevel level, Entity target) {}

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isFriendlyFire(source)) return false;
        return super.hurt(source, amount);
    }

    /**
     * Fogo amigo do dono e dos aliados dele não machuca a invocação. Sem dono (ovo do criativo) ela pode ser
     * ferida por qualquer um — senão ficaria imortal no mundo.
     */
    protected boolean isFriendlyFire(DamageSource source) {
        Entity src = source.getEntity();
        return !level().isClientSide && owner != null && src != null && src != this && isFriend(src);
    }

    // ------------------------------------------------------------------ interação
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        UUID ownerId = getOwnerUUID();
        if (ownerId != null && ownerId.equals(player.getUUID()) && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!level().isClientSide) {
                staying = !staying;
                getNavigation().stop();
                setTarget(null);
                player.displayClientMessage(Component.translatable(staying ? "message.isekai_mahou.origins_b.summon_stay"
                        : "message.isekai_mahou.origins_b.summon_follow", getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    public boolean isStaying() {
        return staying;
    }

    // ------------------------------------------------------------------ regras gerais
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    // ------------------------------------------------------------------ salvar
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Lifetime", lifetime);
        tag.putFloat("Power", power);
        tag.putBoolean("Staying", staying);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOwnerId(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        if (tag.contains("Lifetime")) lifetime = tag.getInt("Lifetime");
        if (tag.contains("Power")) power = tag.getFloat("Power");
        staying = tag.getBoolean("Staying");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ IA comum
    /** Alvos: defender o dono, atacar o que ele ataca, revidar e (sem dono) caçar monstros. */
    protected void addTargetGoals() {
        targetSelector.addGoal(1, new OwnerTargetGoal(true));
        targetSelector.addGoal(2, new OwnerTargetGoal(false));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false, this::wantsToHunt));
    }

    /**
     * Caça por conta própria só monstros hostis perto (sem dono: qualquer distância). Ignora creepers (explodiriam no
     * dono) e mobs neutros calmos (Enderman, Piglin Zumbi...: atacá-los traria o bando todo para cima do dono).
     * Invocações de mobs (cultistas) não caçam monstros: seguem só o alvo do dono.
     */
    private boolean wantsToHunt(LivingEntity e) {
        if (!(e instanceof Enemy) || e instanceof Creeper || isFriend(e)) return false;
        if (e instanceof NeutralMob && !(e instanceof Mob m && m.getTarget() != null && isFriend(m.getTarget()))) return false;
        if (owner == null) return true;
        return getOwner() instanceof Player && distanceToSqr(e) < 12 * 12;
    }

    /**
     * Ataca quem feriu o dono ({@code defend}) ou quem o dono feriu. O "quem o dono feriu" vem do registro do módulo
     * ({@link ObUtil#lastAttacked}), que cobre corpo a corpo, flechas e magias; donos que são mobs usam o alvo deles.
     */
    protected class OwnerTargetGoal extends TargetGoal {
        private final boolean defend;
        private long lastStamp = Long.MIN_VALUE;
        private long pendingStamp;
        private LivingEntity candidate;

        public OwnerTargetGoal(boolean defend) {
            super(ObSummonEntity.this, false);
            this.defend = defend;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (staying) return false;
            LivingEntity o = getOwner();
            if (o == null || !(level() instanceof ServerLevel sl)) return false;
            if (defend) {
                candidate = o.getLastHurtByMob();
                pendingStamp = o.getLastHurtByMobTimestamp();
            } else if (o instanceof Mob m && m.getTarget() != null) {
                candidate = m.getTarget();
                pendingStamp = candidate.getId();
            } else {
                candidate = ObUtil.lastAttacked(sl, o, 200);
                pendingStamp = ObUtil.lastAttackTime(o);
            }
            return pendingStamp != lastStamp && candidate != null && candidate != ObSummonEntity.this && !isFriend(candidate)
                    && canAttack(candidate, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(candidate);
            lastStamp = pendingStamp;
            super.start();
        }
    }

    /** Sem alvo, acompanha o dono a pé. */
    protected class FollowOwnerGoal extends Goal {
        private final double speed;
        private final float startDist;
        private final float stopDist;
        private LivingEntity o;
        private int recalc;

        public FollowOwnerGoal(double speed, float startDist, float stopDist) {
            this.speed = speed;
            this.startDist = startDist;
            this.stopDist = stopDist;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (staying) return false;
            o = getOwner();
            return o != null && getTarget() == null && distanceToSqr(o) > startDist * startDist;
        }

        @Override
        public boolean canContinueToUse() {
            return o != null && o.isAlive() && !staying && getTarget() == null && distanceToSqr(o) > stopDist * stopDist
                    && !getNavigation().isDone();
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(o, 10F, getMaxHeadXRot());
            if (--recalc <= 0) {
                recalc = adjustedTickDelay(10);
                getNavigation().moveTo(o, speed);
            }
        }

        @Override
        public void stop() {
            o = null;
            getNavigation().stop();
        }
    }
}
