package com.renanpontes.isekaimahou.feature.familiars;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * O corpo do Familiar no mundo (uma entidade só para as 10 formas × 3 estágios; forma e estágio são sincronizados e o
 * renderer troca modelo/textura). Não é salvo com o mundo: os dados moram no jogador ({@link FamiliarData}) e o
 * {@link FamiliarService} recria o corpo quando preciso.
 * <p>
 * Não luta, não toma dano, não é alvo de monstros, não empurra nem aciona placas de pressão: só acompanha o dono
 * (andando atrás ou flutuando ao lado do ombro) e ajuda ({@link FamiliarHelp}).
 */
public class FamiliarEntity extends PathfinderMob implements GeoEntity, OwnableEntity {
    private static final EntityDataAccessor<String> FORM = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> SITTING = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.BOOLEAN);
    /** Dono em combate: o familiar deixa de ser "clicável" para não ficar na frente dos golpes e da mira. */
    private static final EntityDataAccessor<Boolean> BUSY = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("move");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
    private static final RawAnimation ALERT = RawAnimation.begin().thenPlay("alert");
    private static final RawAnimation HAPPY = RawAnimation.begin().thenPlay("happy");
    private static final RawAnimation FIND = RawAnimation.begin().thenPlay("find");

    /** Tamanho por estágio (atributo de escala: vale para o desenho e para a caixa de colisão). */
    private static final double[] SCALE = {0.75, 1.0, 1.3};
    private static final double TELEPORT_DIST = 18.0;
    /** Ticks sem bater nem apanhar para o dono contar como fora de combate. */
    private static final int COMBAT_TICKS = 100;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Estado da ajuda (recargas, achados recentes) — some com o corpo, de propósito. */
    final FamiliarHelp.State helpState = new FamiliarHelp.State();
    @Nullable
    private Vec3 lastOwnerPos;
    private int ownerStill;
    private int stuck;
    /** Enquanto &gt; 0 o familiar "aponta" para {@link #pointAt}. */
    private int pointTicks;
    @Nullable
    private Vec3 pointAt;

    public FamiliarEntity(EntityType<? extends FamiliarEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.SAFE_FALL_DISTANCE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FORM, FamiliarForm.RUNE_WISP.id);
        builder.define(STAGE, 1);
        builder.define(OWNER, Optional.empty());
        builder.define(SITTING, false);
        builder.define(BUSY, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new FollowOwnerGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    // =====================================================================================
    //  Forma, estágio e dono
    // =====================================================================================
    /** Liga o corpo ao dono e aos dados dele (servidor). */
    public void bind(ServerPlayer owner, FamiliarData d) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        apply(d);
    }

    /** Reaplica forma/estágio/nome dos dados (mudou de forma, evoluiu ou foi renomeado). */
    public void apply(FamiliarData d) {
        entityData.set(FORM, d.form().id);
        entityData.set(STAGE, d.stage());
        setCustomName(d.name().isEmpty() ? null : Component.literal(d.name()));
        setCustomNameVisible(false);
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(SCALE[Math.max(1, Math.min(3, d.stage())) - 1]);
        setNoGravity(d.form().flying);
        if (d.form().flying) {
            getNavigation().stop();
            setSitting(false);
        }
        refreshDimensions();
    }

    public FamiliarForm form() {
        FamiliarForm f = FamiliarForm.byId(entityData.get(FORM));
        return f == null ? FamiliarForm.RUNE_WISP : f;
    }

    public int stage() {
        return Math.max(1, Math.min(FamiliarForm.MAX_STAGE, entityData.get(STAGE)));
    }

    public boolean isSitting() {
        return entityData.get(SITTING);
    }

    private void setSitting(boolean on) {
        if (entityData.get(SITTING) != on) entityData.set(SITTING, on);
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return entityData.get(OWNER).orElse(null);
    }

    @Override
    @Nullable
    public LivingEntity getOwner() {
        UUID id = getOwnerUUID();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    @Nullable
    private ServerPlayer serverOwner() {
        return getOwner() instanceof ServerPlayer sp ? sp : null;
    }

    @Override
    protected Component getTypeName() {
        return form().displayName(stage());
    }

    // =====================================================================================
    //  Servidor: acompanhar o dono e ajudar
    // =====================================================================================
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerPlayer o = serverOwner();
        // Sem dono por perto, em outra dimensão ou substituído por outro corpo: some (os dados ficam com o jogador)
        if (o == null || o.isRemoved() || o.level() != level() || !FamiliarService.isActiveBody(o, this)) {
            discard();
            return;
        }
        if (o.isSpectator() || o.isDeadOrDying()) return;
        FamiliarForm form = form();
        Vec3 op = o.position();
        if (lastOwnerPos != null && lastOwnerPos.distanceToSqr(op) < 0.0004) ownerStill++;
        else ownerStill = 0;
        lastOwnerPos = op;
        if (pointTicks > 0 && --pointTicks == 0) pointAt = null;

        double dist2 = distanceToSqr(o);
        if (dist2 > TELEPORT_DIST * TELEPORT_DIST || stuck > 40) {
            teleportNear(o);
            dist2 = distanceToSqr(o);
        }
        if (form.flying) {
            flyStep(o);
        } else {
            if (pointAt != null) getLookControl().setLookAt(pointAt.x, pointAt.y, pointAt.z, 30F, 30F);
            boolean far = dist2 > 7 * 7;
            // Andando e sem caminho até o dono (penhasco, água, parede): conta como preso e teleporta
            stuck = far && getNavigation().isDone() ? stuck + 1 : 0;
            setSitting(ownerStill > 100 && dist2 < 5 * 5 && getNavigation().isDone() && pointAt == null && onGround());
        }
        if (tickCount % 10 == 0) {
            long now = level().getGameTime();
            boolean busy = now - FamiliarService.lastHurt(o) < COMBAT_TICKS || o.tickCount - o.getLastHurtMobTimestamp() < COMBAT_TICKS;
            if (entityData.get(BUSY) != busy) entityData.set(BUSY, busy);
            FamiliarData d = FamiliarContent.data(o);
            if (d.help()) {
                FamiliarHelp.tick(this, o, d, (ServerLevel) level());
                // v1.0 (onda 4): a receita de apoio que o dono ensinou, usada sozinha e com recarga
                FamiliarSpell.tick(this, o, d, (ServerLevel) level());
            }
        }
    }

    /** Voo simples (sem pathfinding): paira ao lado do ombro do dono; se apontando, adianta-se na direção do achado. */
    private void flyStep(ServerPlayer o) {
        Vec3 forward = Vec3.directionFromRotation(0, o.getYRot());
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 target = o.position().add(0, o.getBbHeight() * 0.8, 0).add(right.scale(0.95)).add(forward.scale(-0.45));
        if (pointAt != null) {
            Vec3 dir = pointAt.subtract(o.getEyePosition());
            if (dir.lengthSqr() > 1) target = o.getEyePosition().add(dir.normalize().scale(Math.min(2.2, dir.length() * 0.5)));
        }
        Vec3 d = target.subtract(position());
        double len = d.length();
        Vec3 before = getDeltaMovement();
        if (len < 0.06) {
            setDeltaMovement(before.scale(0.5));
        } else {
            double speed = Math.min(0.75, len * 0.22);
            setDeltaMovement(d.scale(speed / len));
        }
        // Longe e sem sair do lugar (parede no caminho): conta como preso
        boolean blocked = len > 3.0 && (horizontalCollision || verticalCollision);
        stuck = blocked ? stuck + 1 : 0;
        float yaw;
        if (pointAt != null) {
            yaw = (float) (Mth.atan2(pointAt.z - getZ(), pointAt.x - getX()) * Mth.RAD_TO_DEG) - 90F;
        } else if (len > 0.8) {
            yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        } else {
            yaw = o.getYRot();
        }
        setYRot(Mth.rotLerp(0.3F, getYRot(), yaw));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    public void travel(Vec3 input) {
        if (!form().flying) {
            super.travel(input);
            return;
        }
        // Quem voa não usa a física de andar: move pelo impulso calculado em flyStep
        if (isEffectiveAi()) {
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.85));
        }
        calculateEntityAnimation(true);
    }

    private void teleportNear(ServerPlayer o) {
        Vec3 back = Vec3.directionFromRotation(0, o.getYRot()).scale(-1.2);
        Vec3 to = o.position().add(back).add(0, form().flying ? o.getBbHeight() * 0.8 : 0.1, 0);
        // Atrás do dono pode ser uma parede: nesse caso, no próprio lugar do dono
        if (!level().noCollision(this, getBoundingBox().move(to.subtract(position())))) {
            to = o.position().add(0, form().flying ? o.getBbHeight() * 0.8 : 0.1, 0);
        }
        teleportTo(to.x, to.y, to.z);
        getNavigation().stop();
        stuck = 0;
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.POOF, to.x, to.y + 0.3, to.z, 6, 0.2, 0.2, 0.2, 0.01);
        }
    }

    /** O familiar "aponta": vira para o ponto por alguns segundos (quem voa se adianta um pouco). */
    public void pointTo(Vec3 pos, int ticks) {
        pointAt = pos;
        pointTicks = ticks;
        setSitting(false);
    }

    public void playAction(String name) {
        triggerAnim("action", name);
    }

    // =====================================================================================
    //  Interação: carinho (e a ajuda "ativa") / agachado = abrir a bolsa
    // =====================================================================================
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        UUID owner = getOwnerUUID();
        if (owner == null || !owner.equals(player.getUUID())) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) FamiliarService.openStash(sp);
            else FamiliarService.pet(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // =====================================================================================
    //  "Espírito": não é alvo, não toma dano, não atrapalha
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Só o vazio/comando remove o corpo (o serviço recria ao lado do dono)
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (!level().isClientSide) discard();
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return super.isPickable() && !entityData.get(BUSY);
    }

    /** Flechas e projéteis de magia atravessam (ele não serve de escudo nem atrapalha o tiro do dono). */
    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {}

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean isAlliedTo(Entity e) {
        UUID owner = getOwnerUUID();
        if (owner != null) {
            if (owner.equals(e.getUUID())) return true;
            if (e instanceof OwnableEntity oe && owner.equals(oe.getOwnerUUID())) return true;
            LivingEntity o = getOwner();
            if (o != null && o != this && o.isAlliedTo(e)) return true;
        }
        return super.isAlliedTo(e);
    }

    // =====================================================================================
    //  Sons (baixinhos e raros: companhia, não barulho)
    // =====================================================================================
    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return switch (form()) {
            case EMBER_FOX -> SoundEvents.FOX_AMBIENT;
            case TIDE_OTTER -> SoundEvents.AXOLOTL_IDLE_AIR;
            case STONE_TORTOISE -> SoundEvents.TURTLE_AMBIENT_LAND;
            case GALE_OWL, BONE_RAVEN -> SoundEvents.PARROT_AMBIENT;
            case DAWN_HARE -> SoundEvents.RABBIT_AMBIENT;
            case SHADE_CAT -> SoundEvents.CAT_PURR;
            case RUNE_WISP -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case VERDANT_SPRITE -> SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
            case FORTUNE_DRAKE -> SoundEvents.STRIDER_AMBIENT;
        };
    }

    /** Som do aviso/achado (tocado só para o dono pelo {@link FamiliarHelp}). */
    public SoundEvent alertSound() {
        return switch (form()) {
            case EMBER_FOX -> SoundEvents.FOX_SNIFF;
            case TIDE_OTTER -> SoundEvents.DOLPHIN_PLAY;
            case STONE_TORTOISE -> SoundEvents.AMETHYST_BLOCK_RESONATE;
            case GALE_OWL -> SoundEvents.PARROT_IMITATE_PHANTOM;
            case DAWN_HARE -> SoundEvents.AMETHYST_CLUSTER_HIT;
            case SHADE_CAT -> SoundEvents.CAT_HISS;
            case RUNE_WISP -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case VERDANT_SPRITE -> SoundEvents.ALLAY_ITEM_GIVEN;
            case BONE_RAVEN -> SoundEvents.PARROT_IMITATE_WITHER_SKELETON;
            case FORTUNE_DRAKE -> SoundEvents.EXPERIENCE_ORB_PICKUP;
        };
    }

    @Override
    public int getAmbientSoundInterval() {
        return 600;
    }

    @Override
    protected float getSoundVolume() {
        return 0.35F;
    }

    @Override
    public float getVoicePitch() {
        float base = form() == FamiliarForm.BONE_RAVEN ? 0.7F : 1.0F;
        // Filhotes têm a voz mais fina
        return base + (3 - stage()) * 0.15F + (random.nextFloat() - 0.5F) * 0.1F;
    }

    // =====================================================================================
    //  Seguir o dono (formas que andam)
    // =====================================================================================
    static class FollowOwnerGoal extends Goal {
        private final FamiliarEntity fam;
        private int repath;

        FollowOwnerGoal(FamiliarEntity fam) {
            this.fam = fam;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = fam.getOwner();
            return o != null && !fam.form().flying && !o.isSpectator() && fam.distanceToSqr(o) > 4.5 * 4.5;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity o = fam.getOwner();
            return o != null && !fam.form().flying && !fam.getNavigation().isDone() && fam.distanceToSqr(o) > 2.5 * 2.5;
        }

        @Override
        public void start() {
            repath = 0;
            fam.setSitting(false);
        }

        @Override
        public void stop() {
            fam.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity o = fam.getOwner();
            if (o == null) return;
            fam.getLookControl().setLookAt(o, 10.0F, fam.getMaxHeadXRot());
            if (--repath <= 0) {
                repath = 10;
                fam.getNavigation().moveTo(o, fam.distanceToSqr(o) > 10 * 10 ? 1.7 : 1.25);
            }
        }
    }

    // =====================================================================================
    //  Animação (GeckoLib) — nomes curtos iguais em todas as formas: idle, move, sit, alert, happy, find
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isSitting() && !form().flying) return state.setAndContinue(SIT);
            double dx = getX() - xo, dz = getZ() - zo, dy = getY() - yo;
            boolean moving = dx * dx + dz * dz + (form().flying ? dy * dy : 0) > 0.0006;
            return state.setAndContinue(moving ? MOVE : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("alert", ALERT).triggerableAnim("happy", HAPPY).triggerableAnim("find", FIND));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
