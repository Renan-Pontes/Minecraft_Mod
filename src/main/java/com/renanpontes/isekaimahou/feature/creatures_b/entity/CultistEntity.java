package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * Cultista encapuzado: conjura magias de GLIFOS de verdade (via {@link SpellCaster#castAsMob}) de uma das três
 * escolas — Sombra, Fogo ou Arcano (cor da túnica). Mantém distância, prepara a magia com as mãos erguidas
 * (dá para interromper batendo) e some num passo sombrio quando encurralado. Às vezes deixa cair um
 * Pergaminho de Glifo com um glifo aleatório.
 */
public class CultistEntity extends CbMonster {
    public static final int SHADOW = 0;
    public static final int FIRE = 1;
    public static final int ARCANE = 2;
    private static final EntityDataAccessor<Integer> SCHOOL = SynchedEntityData.defineId(CultistEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CASTING = SynchedEntityData.defineId(CultistEntity.class, EntityDataSerializers.BOOLEAN);
    private int blinkCooldown;
    /** Depois de ter a conjuração interrompida, espera um pouco antes de tentar de novo. */
    private int castLockout;

    public CultistEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    public CbFaction faction() {
        return CbFaction.CULT;
    }

    @Override
    protected String animId() {
        return "cultist";
    }

    @Override
    protected String[] actionAnims() {
        return new String[] {"release"};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SCHOOL, SHADOW);
        builder.define(CASTING, false);
    }

    public int school() {
        return entityData.get(SCHOOL);
    }

    public boolean isCasting() {
        return entityData.get(CASTING);
    }

    public Element schoolElement() {
        return switch (school()) {
            case FIRE -> Element.FIRE;
            case ARCANE -> Element.ARCANE;
            default -> Element.SHADOW;
        };
    }

    @Override
    protected String loopAnim(boolean moving) {
        if (isCasting()) return "cast";
        return super.loopAnim(moving);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        SpawnGroupData data) {
        entityData.set(SCHOOL, level.getRandom().nextInt(3));
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, busyGoal());
        goalSelector.addGoal(2, new CastGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (blinkCooldown > 0) blinkCooldown--;
        if (castLockout > 0) castLockout--;
    }

    // =====================================================================================
    //  Passo sombrio
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            // Apanhar interrompe a conjuração.
            if (isCasting()) {
                entityData.set(CASTING, false);
                castLockout = 30;
                playSound(SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.6F);
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 2.0, getZ(), 10, 0.3, 0.2, 0.3, 0.02);
                }
            }
            Entity src = source.getEntity();
            if (src != null && distanceToSqr(src) < 4.0 * 4.0 && blinkCooldown <= 0 && isAlive() && random.nextFloat() < 0.45F) {
                blinkAway();
            }
        }
        return hurt;
    }

    private void blinkAway() {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 from = position();
        for (int i = 0; i < 12; i++) {
            double x = getX() + (random.nextDouble() - 0.5) * 16;
            double z = getZ() + (random.nextDouble() - 0.5) * 16;
            double y = getY() + random.nextInt(5) - 2;
            if (new Vec3(x, y, z).distanceToSqr(from) < 25) continue;
            if (randomTeleport(x, y, z, false)) {
                blinkCooldown = 100;
                sl.sendParticles(ElementFx.dust(schoolElement(), 1.5F), from.x, from.y + 1, from.z, 30, 0.4, 0.8, 0.4, 0);
                sl.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.8, 0.3, 0.4);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 30, 0.3, 0.8, 0.3, 0.2);
                sl.playSound(null, from.x, from.y, from.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0F, 0.8F);
                return;
            }
        }
    }

    // =====================================================================================
    //  Magias
    // =====================================================================================
    /** Monta a receita só com glifos que existem (os de outros módulos podem faltar). */
    private static SpellRecipe recipe(String... ids) {
        List<ResourceLocation> list = new ArrayList<>();
        for (String id : ids) {
            ResourceLocation rl = IsekaiMahou.id(id);
            if (GlyphRegistry.get(rl) == null) return null;
            list.add(rl);
        }
        return new SpellRecipe(list, "", -1, "");
    }

    /** Escolhe uma magia da escola de acordo com a distância do alvo. */
    private SpellRecipe pickSpell(double dist) {
        List<SpellRecipe> options = new ArrayList<>();
        boolean close = dist < 6.0;
        switch (school()) {
            case FIRE -> {
                add(options, recipe("projectile", "ignite"), 3);
                add(options, recipe("projectile", "split", "ember"), 2);
                if (close) add(options, recipe("cone", "ignite"), 4);
            }
            case ARCANE -> {
                add(options, recipe("projectile", "arcane_damage"), 3);
                add(options, recipe("projectile", "split", "arcane_damage"), 2);
                add(options, recipe("beam", "arcane_damage"), 2);
                if (close) add(options, recipe("projectile", "arcane_damage", "push"), 3);
            }
            default -> {
                add(options, recipe("projectile", "life_drain"), 3);
                add(options, recipe("projectile", "weaken"), 1);
                add(options, recipe("projectile", "curse"), 1);
                add(options, recipe("beam", "life_drain"), 2);
                if (close) add(options, recipe("nova", "darkness"), 3);
            }
        }
        if (options.isEmpty()) return recipe("projectile", "arcane_damage");
        return options.get(random.nextInt(options.size()));
    }

    private static void add(List<SpellRecipe> list, SpellRecipe r, int weight) {
        if (r == null) return;
        for (int i = 0; i < weight; i++) list.add(r);
    }

    private float spellPower() {
        Difficulty d = level().getDifficulty();
        return d == Difficulty.HARD ? 1.2F : d == Difficulty.EASY ? 0.75F : 1.0F;
    }

    private void release(LivingEntity target) {
        CbFx.face(this, target);
        Vec3 d = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(getEyePosition());
        setXRot((float) (-(Mth.atan2(d.y, d.horizontalDistance()) * (180F / Math.PI))));
        SpellRecipe r = pickSpell(Math.sqrt(distanceToSqr(target)));
        // Se a receita de outro módulo não valer (gramática mudou, evento cancelou...), cai no projétil arcano do núcleo.
        if (r == null || SpellCaster.castAsMob(this, schoolElement(), r, spellPower()) == null) {
            SpellRecipe basic = recipe("projectile", "arcane_damage");
            if (basic != null && !basic.equals(r)) SpellCaster.castAsMob(this, schoolElement(), basic, spellPower());
        }
        playAction("release");
        playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.0F);
    }

    /** Mantém distância, prepara (mãos erguidas, partículas) e solta a magia. */
    private class CastGoal extends Goal {
        private int castTicks;
        private int cooldown = 20;
        private int repath;

        CastGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && !isBusy();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            setAggressive(true);
            castTicks = 0;
        }

        @Override
        public void stop() {
            setAggressive(false);
            entityData.set(CASTING, false);
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            double dist = distanceToSqr(target);
            boolean sees = getSensing().hasLineOfSight(target);

            if (isCasting()) {
                getNavigation().stop();
                castTicks++;
                if (level() instanceof ServerLevel sl) {
                    ElementFx.channeling(sl, CultistEntity.this, schoolElement());
                    if (castTicks % 5 == 0) {
                        sl.sendParticles(ElementFx.signature(schoolElement()), getX(), getY() + 2.2, getZ(), 3, 0.3, 0.1, 0.3, 0.01);
                    }
                }
                if (castTicks >= 24) {
                    entityData.set(CASTING, false);
                    if (sees) release(target);
                    cooldown = 45 + random.nextInt(35);
                }
                return;
            }

            if (cooldown > 0) cooldown--;
            // Posicionamento: longe demais = aproxima; perto demais = recua.
            if (--repath <= 0) {
                repath = 10;
                if (dist > 15 * 15 || !sees) {
                    getNavigation().moveTo(target, 1.0);
                } else if (dist < 5 * 5) {
                    Vec3 away = DefaultRandomPos.getPosAway(CultistEntity.this, 10, 4, target.position());
                    if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.2);
                } else {
                    getNavigation().stop();
                }
            }
            if (cooldown <= 0 && castLockout <= 0 && sees && dist < 20 * 20) {
                entityData.set(CASTING, true);
                castTicks = 0;
                getNavigation().stop();
                playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.9F);
            }
        }
    }

    // =====================================================================================
    //  Pergaminho de glifo
    // =====================================================================================
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!recentlyHit) return;
        int looting = 0;
        if (source.getEntity() instanceof LivingEntity killer) {
            looting = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.LOOTING)
                    .map(h -> EnchantmentHelper.getEnchantmentLevel(h, killer)).orElse(0);
        }
        if (random.nextFloat() < 0.08F + 0.03F * looting) {
            Glyph g = randomGlyph();
            if (g != null) spawnAtLocation(GlyphScrollItem.of(g.id()));
        }
    }

    /** Glifo aleatório existente (não lendário); metade das vezes da escola do cultista. */
    private Glyph randomGlyph() {
        List<Glyph> pool = new ArrayList<>();
        List<Glyph> school = new ArrayList<>();
        Element el = schoolElement();
        for (Glyph g : GlyphRegistry.all()) {
            if (g.isLegendary()) continue;
            if (g.kind() == GlyphKind.TRIGGER && !(g instanceof TriggerGlyph t && t.isPlaceable())) continue;
            pool.add(g);
            if (g.element() == el && g.origin() == null) school.add(g);
        }
        List<Glyph> from = !school.isEmpty() && random.nextBoolean() ? school : pool;
        return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
    }

    // =====================================================================================
    //  Salvar / sons
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("School", school());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SCHOOL, Mth.clamp(tag.getInt("School"), 0, 2));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.EVOKER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.EVOKER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.EVOKER_DEATH;
    }

    @Override
    protected float voicePitch() {
        return 0.9F;
    }

    /** Spawn natural: escuro e com algum espaço (não dentro de cavernas profundas). */
    public static boolean checkSpawn(EntityType<CultistEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                     RandomSource random) {
        if (reason == MobSpawnType.NATURAL && pos.getY() < level.getLevel().getChunkSource().getGenerator().getSeaLevel() - 10) {
            return false;
        }
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }
}
