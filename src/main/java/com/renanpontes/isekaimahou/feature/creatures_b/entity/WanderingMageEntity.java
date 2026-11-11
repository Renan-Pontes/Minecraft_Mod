package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.feature.creatures_b.CbRegistries;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Mago Errante: NPC comerciante (como o vendedor ambulante) que aparece perto dos jogadores de vez em quando.
 * Vende Pergaminhos de Glifo (tier 1–2), Cristais de Mana, essências e poções; compra partes de monstros.
 * Defende-se com Projéteis Arcanos contra monstros e se teleporta quando é atacado. Some depois de um tempo.
 */
public class WanderingMageEntity extends AbstractVillager implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.wandering_mage.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.wandering_mage.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.wandering_mage.run");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.wandering_mage.cast");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private BlockPos wanderTarget;
    private int despawnDelay;
    private int spellCooldown;
    private int blinkCooldown;

    public WanderingMageEntity(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Monster.class, 8.0F, 0.5, 0.6));
        goalSelector.addGoal(1, new PanicGoal(this, 0.55));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(2, new WanderToTargetGoal());
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(9, new InteractGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    public void setDespawnDelay(int ticks) {
        despawnDelay = ticks;
    }

    public void setWanderTarget(BlockPos pos) {
        wanderTarget = pos;
    }

    // =====================================================================================
    //  Comércio
    // =====================================================================================
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isAlive() && !isTrading() && !isBaby()) {
            if (hand == InteractionHand.MAIN_HAND) player.awardStat(Stats.TALKED_TO_VILLAGER);
            if (!level().isClientSide) {
                if (getOffers().isEmpty()) return InteractionResult.CONSUME;
                setTradingPlayer(player);
                openTradingScreen(player, getDisplayName(), 1);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected void updateTrades() {
        MerchantOffers offers = getOffers();
        // Pergaminhos de glifo (tier 1 e tier 2; o tier 2 também pede um livro)
        List<Glyph> t1 = sellableGlyphs(1);
        List<Glyph> t2 = sellableGlyphs(2);
        if (!t1.isEmpty()) offers.add(glyphOffer(t1.get(random.nextInt(t1.size())), 1));
        if (!t2.isEmpty()) offers.add(glyphOffer(t2.get(random.nextInt(t2.size())), 2));
        List<Glyph> any = new ArrayList<>(t1);
        any.addAll(t2);
        if (!any.isEmpty() && random.nextBoolean()) {
            Glyph g = any.get(random.nextInt(any.size()));
            if (offers.stream().noneMatch(o -> g.id().equals(o.getResult().get(CoreRegistries.GLYPH.get())))) {
                offers.add(glyphOffer(g, g.tier()));
            }
        }
        // Cristal de mana
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(CoreRegistries.MANA_CRYSTAL.get()), 6, 5, 0.05F));
        // Essências (2 diferentes)
        List<Element> els = new ArrayList<>(CoreRegistries.ESSENCES.keySet());
        for (int i = 0; i < 2 && !els.isEmpty(); i++) {
            Element e = els.remove(random.nextInt(els.size()));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 6 + random.nextInt(3)),
                    new ItemStack(CoreRegistries.ESSENCES.get(e).get()), 4, 5, 0.05F));
        }
        // Poções (2 diferentes)
        List<Holder<Potion>> potions = new ArrayList<>(List.of(Potions.HEALING, Potions.REGENERATION, Potions.SWIFTNESS,
                Potions.NIGHT_VISION, Potions.FIRE_RESISTANCE, Potions.WATER_BREATHING, Potions.STRENGTH, Potions.LEAPING));
        for (int i = 0; i < 2; i++) {
            Holder<Potion> p = potions.remove(random.nextInt(potions.size()));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 4 + random.nextInt(4)),
                    PotionContents.createItemStack(Items.POTION, p), 3, 3, 0.05F));
        }
        // Compra partes de monstros (2 diferentes)
        List<Buy> buys = new ArrayList<>(List.of(
                new Buy(CbRegistries.ORC_TUSK.get(), 4, 1),
                new Buy(CbRegistries.TROLL_HIDE.get(), 2, 1),
                new Buy(CbRegistries.LIZARD_SCALE.get(), 5, 1),
                new Buy(CbRegistries.WEREWOLF_FANG.get(), 2, 1),
                new Buy(CbRegistries.OGRE_TOOTH.get(), 2, 1),
                new Buy(CbRegistries.MINOTAUR_HORN.get(), 1, 6)));
        for (int i = 0; i < 2; i++) {
            Buy b = buys.remove(random.nextInt(buys.size()));
            offers.add(new MerchantOffer(new ItemCost(b.item(), b.count()), new ItemStack(Items.EMERALD, b.emeralds()), 12, 2, 0.05F));
        }
    }

    /** Oferta de compra: {@code count} itens por {@code emeralds} esmeraldas. */
    private record Buy(Item item, int count, int emeralds) {}

    private static List<Glyph> sellableGlyphs(int tier) {
        List<Glyph> out = new ArrayList<>();
        for (Glyph g : GlyphRegistry.all()) {
            // Magias de Origem são da classe (liberadas pelos caminhos): o mago não as vende.
            if (g.isLegendary() || g.tier() != tier || g.origin() != null || CoreGlyphs.STARTER.contains(g.id().getPath())) continue;
            if (g.kind() == GlyphKind.TRIGGER && !(g instanceof TriggerGlyph t && t.isPlaceable())) continue;
            out.add(g);
        }
        return out;
    }

    private MerchantOffer glyphOffer(Glyph g, int tier) {
        ItemStack scroll = GlyphScrollItem.of(g.id());
        if (tier <= 1) {
            return new MerchantOffer(new ItemCost(Items.EMERALD, 8 + random.nextInt(7)), scroll, 1, 10, 0.05F);
        }
        return new MerchantOffer(new ItemCost(Items.EMERALD, 16 + random.nextInt(9)), Optional.of(new ItemCost(Items.BOOK, 1)),
                scroll, 1, 15, 0.05F);
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            level().addFreshEntity(new ExperienceOrb(level(), getX(), getY() + 0.5, getZ(), 3 + random.nextInt(4)));
        }
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    // =====================================================================================
    //  Ciclo: some com o tempo, se defende, partículas
    // =====================================================================================
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(6) == 0) {
                level().addParticle(ParticleTypes.ENCHANT, getRandomX(0.6), getY() + 1.6 + random.nextDouble() * 0.8, getRandomZ(0.6),
                        (random.nextDouble() - 0.5) * 0.5, -0.2, (random.nextDouble() - 0.5) * 0.5);
            }
            return;
        }
        if (spellCooldown > 0) spellCooldown--;
        if (blinkCooldown > 0) blinkCooldown--;
        if (despawnDelay > 0 && !isTrading() && --despawnDelay == 0) {
            vanishFx();
            discard();
            return;
        }
        if (spellCooldown <= 0 && tickCount % 10 == 0 && !isTrading()) defend();
    }

    /** Projétil arcano no monstro mais próximo que estiver vindo atrás dele. */
    private void defend() {
        Monster threat = null;
        double best = 7 * 7;
        for (Monster m : level().getEntitiesOfClass(Monster.class, getBoundingBox().inflate(7.0), m -> m.isAlive() && m.getTarget() == this)) {
            double d = distanceToSqr(m);
            if (d < best && hasLineOfSight(m)) {
                best = d;
                threat = m;
            }
        }
        if (threat == null) return;
        SpellRecipe r = new SpellRecipe(List.of(IsekaiMahou.id("projectile"), IsekaiMahou.id("arcane_damage")), "", -1, "");
        lookAt(threat, 360.0F, 360.0F);
        Vec3 d = threat.position().add(0, threat.getBbHeight() * 0.5, 0).subtract(getEyePosition());
        setXRot((float) (-(Mth.atan2(d.y, d.horizontalDistance()) * (180F / Math.PI))));
        SpellCaster.castAsMob(this, Element.ARCANE, r, 1.0F);
        triggerAnim("action", "cast");
        spellCooldown = 40;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive() && blinkCooldown <= 0 && source.getEntity() instanceof LivingEntity) {
            blink();
        }
        return hurt;
    }

    private void blink() {
        Vec3 from = position();
        for (int i = 0; i < 16; i++) {
            double x = getX() + (random.nextDouble() - 0.5) * 24;
            double z = getZ() + (random.nextDouble() - 0.5) * 24;
            double y = getY() + random.nextInt(7) - 3;
            if (new Vec3(x, y, z).distanceToSqr(from) < 36) continue;
            if (randomTeleport(x, y, z, false)) {
                blinkCooldown = 80;
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ElementFx.dust(Element.ARCANE, 1.4F), from.x, from.y + 1, from.z, 30, 0.4, 0.8, 0.4, 0);
                    sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 20, 0.3, 0.8, 0.3, 0.05);
                    sl.playSound(null, from.x, from.y, from.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 1.0F, 1.2F);
                }
                triggerAnim("action", "cast");
                return;
            }
        }
    }

    private void vanishFx() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 30, 0.4, 0.9, 0.4, 0.03);
            sl.sendParticles(ElementFx.dust(Element.ARCANE, 1.5F), getX(), getY() + 1, getZ(), 30, 0.4, 0.9, 0.4, 0);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }

    /** Efeito de chegada (usado pelo spawner). */
    public void arrivalFx() {
        vanishFx();
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1.0F, 0.8F);
        }
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DespawnDelay", despawnDelay);
        if (wanderTarget != null) tag.put("WanderTarget", NbtUtils.writeBlockPos(wanderTarget));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        despawnDelay = tag.getInt("DespawnDelay");
        wanderTarget = NbtUtils.readBlockPos(tag, "WanderTarget").orElse(null);
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        return isTrading() ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.WANDERING_TRADER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WANDERING_TRADER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WANDERING_TRADER_DEATH;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean yes) {
        return yes ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.WANDERING_TRADER_NO;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.WANDERING_TRADER_YES;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.85F;
    }

    // =====================================================================================
    //  Goal: ir até o ponto de chegada (perto do jogador que ele veio visitar)
    // =====================================================================================
    private class WanderToTargetGoal extends Goal {
        WanderToTargetGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return wanderTarget != null && !isTrading() && !wanderTarget.closerToCenterThan(position(), 3.0);
        }

        @Override
        public void stop() {
            wanderTarget = null;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (wanderTarget == null) return;
            if (getNavigation().isDone()) {
                Vec3 t = Vec3.atBottomCenterOf(wanderTarget);
                Vec3 dir = t.subtract(position());
                if (dir.length() > 10) t = position().add(dir.normalize().scale(10));
                getNavigation().moveTo(t.x, t.y, t.z, 0.4);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    private PlayState mainState(AnimationState<WanderingMageEntity> state) {
        if (!state.isMoving()) return state.setAndContinue(IDLE);
        return state.setAndContinue(getDeltaMovement().horizontalDistanceSqr() > 0.012 ? RUN : WALK);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        reg.add(new AnimationController<>(this, "main", 4, this::mainState),
                new AnimationController<>(this, "action", 1, s -> PlayState.STOP).triggerableAnim("cast", CAST));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof WanderingMageEntity || super.isAlliedTo(other);
    }
}
