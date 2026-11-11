package com.renanpontes.isekaimahou.feature.ascension.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.ascension.Tribulation;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A Sombra da Tribulação: um reflexo escuro do jogador (o cliente desenha com a skin dele, escurecida).
 * Ao nascer copia as magias do LOADOUT ATIVO do dono e as conjura com {@link SpellCaster#castAsMob}.
 * A ultimate (slot G) só é usada uma vez, quando a Sombra está abaixo de metade da vida.
 * Some sozinha quando a Tribulação que a criou termina.
 */
public class AscensionShadowEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(AscensionShadowEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_CASTING =
            SynchedEntityData.defineId(AscensionShadowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(AscensionShadowEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ascension_shadow.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.ascension_shadow.walk");
    private static final RawAnimation CHANNEL = RawAnimation.begin().thenLoop("animation.ascension_shadow.channel");
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("animation.ascension_shadow.emerge");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.ascension_shadow.attack");
    private static final RawAnimation RELEASE = RawAnimation.begin().thenPlay("animation.ascension_shadow.release");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<ShadowSpell> spells = new ArrayList<>();
    private Element element = Element.ARCANE;
    private UUID ritualId;
    private int circle = 5;
    private boolean ultimateUsed;

    /** Uma magia copiada do loadout do dono, já validada. */
    private static final class ShadowSpell {
        final SpellRecipe recipe;
        final boolean ultimate;
        final String form;
        final int castTicks;
        final int cooldown;
        long readyAt;

        ShadowSpell(SpellRecipe recipe, ParsedSpell parsed) {
            this.recipe = recipe;
            this.ultimate = parsed.isUltimate();
            this.form = parsed.form().id().getPath();
            this.castTicks = parsed.castTicks();
            this.cooldown = parsed.cooldown();
        }

        boolean inRange(double dist) {
            return switch (form) {
                case "touch" -> dist < 3.0;
                case "self" -> dist < 10.0;
                default -> dist < 22.0;
            };
        }
    }

    public AscensionShadowEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 25;
        setPersistenceRequired();
        if (!level.isClientSide) setRecipes(List.of());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_CASTING, false);
        builder.define(DATA_COLOR, Element.ARCANE.color);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DuelGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // =====================================================================================
    //  Ligação com o dono e a Tribulação
    // =====================================================================================
    /** Copia o loadout ativo do dono e ajusta a força para o Círculo-alvo. */
    public void bind(ServerPlayer owner, UUID ritualId, int targetCircle) {
        this.ritualId = ritualId;
        this.circle = targetCircle;
        entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));

        MagicData d = MagicApi.get(owner);
        // v1.0: 9 loadouts sem elemento — copia o loadout ATIVO; a cor/elemento vem da 1ª magia dele.
        int loadout = d.activeLoadout();
        element = null;
        List<SpellRecipe> recipes = new ArrayList<>();
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe r = d.peekSlot(loadout, slot).main();
            if (r != null && !r.isEmpty()) {
                recipes.add(r);
                if (element == null) element = d.slotElement(loadout, slot);
            }
        }
        if (element == null) element = Element.ARCANE;
        setRecipes(recipes);
        entityData.set(DATA_COLOR, element.color);

        setBase(Attributes.MAX_HEALTH, 40.0 + 14.0 * targetCircle);
        setBase(Attributes.ATTACK_DAMAGE, 3.0 + 0.7 * targetCircle);
        setBase(Attributes.ARMOR, 2.0 + targetCircle);
        setHealth(getMaxHealth());
        setCustomName(Component.translatable("entity.isekai_mahou.ascension_shadow.of", owner.getName()));
        setTarget(owner);
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double v) {
        AttributeInstance inst = getAttribute(attr);
        if (inst != null) inst.setBaseValue(v);
    }

    private void setRecipes(List<SpellRecipe> recipes) {
        spells.clear();
        for (SpellRecipe r : recipes) {
            SpellParser.Result res = SpellParser.parse(r, null, null);
            if (res.ok()) spells.add(new ShadowSpell(r, res.spell()));
        }
        if (spells.isEmpty()) {
            // Sem loadout: a Sombra usa o básico do Arcano (Projétil + Dano Arcano e Toque + Empurrão).
            addFallback(List.of(IsekaiMahou.id("projectile"), IsekaiMahou.id("arcane_damage")));
            addFallback(List.of(IsekaiMahou.id("touch"), IsekaiMahou.id("push"), IsekaiMahou.id("arcane_damage")));
        }
    }

    private void addFallback(List<net.minecraft.resources.ResourceLocation> glyphs) {
        SpellRecipe r = new SpellRecipe(glyphs, "", -1, "");
        SpellParser.Result res = SpellParser.parse(r, null, null);
        if (res.ok()) spells.add(new ShadowSpell(r, res.spell()));
    }

    public Optional<UUID> ownerId() {
        return entityData.get(DATA_OWNER);
    }

    public boolean isCasting() {
        return entityData.get(DATA_CASTING);
    }

    public int auraColor() {
        return entityData.get(DATA_COLOR);
    }

    private ServerPlayer owner() {
        if (!(level() instanceof ServerLevel sl)) return null;
        return ownerId().map(id -> sl.getServer().getPlayerList().getPlayer(id)).orElse(null);
    }

    private float power() {
        return 0.5F + 0.05F * circle;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 20 == 0) {
            if (ritualId != null && !Tribulation.isActive(ritualId)) {
                vanish();
                return;
            }
            ServerPlayer owner = owner();
            // A Sombra sempre volta a caçar o próprio dono.
            if (owner != null && owner.isAlive() && !owner.isCreative() && !owner.isSpectator()
                    && owner.level() == level() && distanceToSqr(owner) < 40 * 40 && getTarget() != owner) {
                setTarget(owner);
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            int c = auraColor();
            Vector3f col = new Vector3f(((c >> 16) & 255) / 255F * 0.6F, ((c >> 8) & 255) / 255F * 0.4F, (c & 255) / 255F * 0.7F);
            if (random.nextInt(2) == 0) {
                level().addParticle(new DustParticleOptions(col, 1.2F), getRandomX(0.6), getY() + random.nextDouble() * 0.3,
                        getRandomZ(0.6), 0, 0.02, 0);
            }
            if (random.nextInt(5) == 0) {
                level().addParticle(ParticleTypes.SMOKE, getRandomX(0.5), getY() + random.nextDouble() * getBbHeight(),
                        getRandomZ(0.5), 0, 0.03, 0);
            }
            if (isCasting()) {
                double a = tickCount * 0.5;
                for (int i = 0; i < 2; i++) {
                    double ang = a + i * Math.PI;
                    level().addParticle(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F,
                            (c & 255) / 255F), 1.0F), getX() + Math.cos(ang) * 0.8, getY() + 1.2, getZ() + Math.sin(ang) * 0.8, 0, 0.05, 0);
                }
            }
        }
    }

    /** Some em fumaça (fim da Tribulação). */
    public void vanish() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.05);
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 15, 0.3, 0.6, 0.3, 0.02);
        }
        discard();
    }

    // =====================================================================================
    //  Conjuração
    // =====================================================================================
    private ShadowSpell pickSpell(double dist) {
        long now = level().getGameTime();
        boolean desperate = getHealth() < getMaxHealth() * 0.5F && !ultimateUsed;
        List<ShadowSpell> ready = new ArrayList<>();
        for (ShadowSpell s : spells) {
            if (s.readyAt > now || !s.inRange(dist)) continue;
            if (s.ultimate) {
                if (desperate) return s;
                continue;
            }
            ready.add(s);
        }
        return ready.isEmpty() ? null : ready.get(random.nextInt(ready.size()));
    }

    /** Vira exatamente para o alvo (as formas usam a direção do olhar de quem conjura). */
    private void aimAt(LivingEntity target) {
        Vec3 from = getEyePosition();
        Vec3 to = target.getEyePosition().subtract(0, 0.3, 0);
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) (-(Mth.atan2(dy, h) * Mth.RAD_TO_DEG));
        setYRot(yaw);
        setXRot(pitch);
        yHeadRot = yaw;
        yBodyRot = yaw;
    }

    private void release(ShadowSpell spell, LivingEntity target) {
        aimAt(target);
        entityData.set(DATA_CASTING, false);
        triggerAnim("action", "release");
        var ctx = SpellCaster.castAsMob(this, element, spell.recipe, power());
        if (ctx == null) {
            // Conjuração cancelada (ex.: Silêncio de outro módulo): a magia só espera um pouco e volta a ser usada.
            // As receitas já foram validadas em setRecipes, então não há por que descartá-la.
            spell.readyAt = level().getGameTime() + 60;
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.4, getZ(), 12, 0.3, 0.3, 0.3, 0.02);
            }
            playSound(SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.4F);
            return;
        }
        spell.readyAt = level().getGameTime() + Math.max(40, Math.round(spell.cooldown * 1.3F));
        if (spell.ultimate) ultimateUsed = true;
    }

    private void blinkNear(LivingEntity target) {
        Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(3.5));
        for (int i = 0; i < 8; i++) {
            double x = behind.x + (random.nextDouble() - 0.5) * 3, z = behind.z + (random.nextDouble() - 0.5) * 3;
            double ox = getX(), oy = getY(), oz = getZ();
            if (randomTeleport(x, target.getY(), z, true)) {
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SQUID_INK, ox, oy + 1, oz, 20, 0.3, 0.6, 0.3, 0.02);
                    sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.05);
                }
                level().playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.6F);
                return;
            }
        }
    }

    /** IA de duelo: mantém distância e conjura; se o jogador encostar, luta corpo a corpo; se fugir, teleporta. */
    private static final class DuelGoal extends Goal {
        private final AscensionShadowEntity shadow;
        private ShadowSpell casting;
        private int castLeft;
        private int globalCd = 30;
        private int meleeCd;
        private int strafeDir = 1;
        private int strafeTimer;
        private int farTicks;

        DuelGoal(AscensionShadowEntity shadow) {
            this.shadow = shadow;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            // Espera a animação de "emergir do chão" terminar antes de lutar.
            LivingEntity t = shadow.getTarget();
            return shadow.tickCount > 30 && t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            casting = null;
            castLeft = 0;
            shadow.entityData.set(DATA_CASTING, false);
            shadow.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = shadow.getTarget();
            if (t == null) return;
            shadow.getLookControl().setLookAt(t, 30F, 30F);
            double dist = shadow.distanceTo(t);
            boolean sees = shadow.getSensing().hasLineOfSight(t);
            if (globalCd > 0) globalCd--;
            if (meleeCd > 0) meleeCd--;

            if (casting != null) {
                shadow.getNavigation().stop();
                shadow.aimAt(t);
                if (shadow.level() instanceof ServerLevel sl) ElementFx.channeling(sl, shadow, shadow.element);
                if (--castLeft <= 0) {
                    shadow.release(casting, t);
                    casting = null;
                    globalCd = 20 + shadow.random.nextInt(25);
                }
                return;
            }
            if (globalCd <= 0 && sees) {
                ShadowSpell s = shadow.pickSpell(dist);
                if (s != null) {
                    casting = s;
                    castLeft = Mth.clamp(s.castTicks, 12, 30);
                    shadow.entityData.set(DATA_CASTING, true);
                    shadow.getNavigation().stop();
                    shadow.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.7F);
                    return;
                }
            }

            farTicks = (dist > 14 || !sees) ? farTicks + 1 : 0;
            if (farTicks > 60) {
                farTicks = 0;
                shadow.blinkNear(t);
                return;
            }

            if (dist < 3.2) {
                shadow.getNavigation().moveTo(t, 1.15);
                if (dist < 2.3 && meleeCd <= 0) {
                    shadow.swing(InteractionHand.MAIN_HAND);
                    shadow.triggerAnim("action", "attack");
                    shadow.doHurtTarget(t);
                    meleeCd = 20;
                }
            } else if (dist > 11 || !sees) {
                shadow.getNavigation().moveTo(t, 1.0);
            } else {
                shadow.getNavigation().stop();
                if (--strafeTimer <= 0) {
                    strafeTimer = 30 + shadow.random.nextInt(40);
                    strafeDir = -strafeDir;
                }
                shadow.getMoveControl().strafe(dist < 6 ? -0.45F : 0.1F, 0.5F * strafeDir);
            }
        }
    }

    // =====================================================================================
    //  Aliados, regras e sons
    // =====================================================================================
    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof AscensionShadowEntity) return true;
        if (ritualId != null && other.getPersistentData().hasUUID(Tribulation.MOB_TAG)
                && ritualId.equals(other.getPersistentData().getUUID(Tribulation.MOB_TAG))) {
            return true;
        }
        return super.isAlliedTo(other);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            ElementFx.burst(sl, position().add(0, 1, 0), element, 40, 0.6);
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 40, 0.4, 0.7, 0.4, 0.05);
            if (source.getEntity() instanceof ServerPlayer killer) MagicApi.addCounter(killer, "feat.shadow_slain", 1);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + random.nextFloat() * 0.1F;
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ownerId().ifPresent(id -> tag.putUUID("shadow_owner", id));
        if (ritualId != null) tag.putUUID("ritual", ritualId);
        tag.putInt("circle", circle);
        tag.putString("element", element.id);
        tag.putBoolean("ultimate_used", ultimateUsed);
        SpellRecipe.CODEC.listOf().encodeStart(NbtOps.INSTANCE, spells.stream().map(s -> s.recipe).toList())
                .result().ifPresent(t -> tag.put("spells", t));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("shadow_owner")) entityData.set(DATA_OWNER, Optional.of(tag.getUUID("shadow_owner")));
        ritualId = tag.hasUUID("ritual") ? tag.getUUID("ritual") : null;
        circle = tag.contains("circle") ? tag.getInt("circle") : 5;
        Element e = Element.byId(tag.getString("element"));
        element = e != null ? e : Element.ARCANE;
        entityData.set(DATA_COLOR, element.color);
        ultimateUsed = tag.getBoolean("ultimate_used");
        if (tag.contains("spells")) {
            SpellRecipe.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("spells")).result().ifPresent(this::setRecipes);
        }
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (tickCount < 30) return state.setAndContinue(EMERGE);
            if (isCasting()) return state.setAndContinue(CHANNEL);
            if (state.isMoving()) return state.setAndContinue(WALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("release", RELEASE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
