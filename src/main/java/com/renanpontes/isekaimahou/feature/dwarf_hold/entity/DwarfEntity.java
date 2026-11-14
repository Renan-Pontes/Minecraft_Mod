package com.renanpontes.isekaimahou.feature.dwarf_hold.entity;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.dwarf_hold.DwarfRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Anão da Cidade Anã Perdida: Comerciante (Mithril, essências, Monóculo de Avaliação) ou Ferreiro (Forja Rúnica,
 * Machado de Guerra, Escudo do Herói). Moram na cidade: ficam perto de casa, não somem e não morrem (só por /kill) —
 * a cidade é um ponto de comércio permanente. Repõem o estoque a cada dia do jogo. Jogadores da raça Anã ganham 25%
 * de desconto ("parente da montanha").
 */
public class DwarfEntity extends AbstractVillager implements GeoEntity {
    /** Profissão do anão (cada uma tem o seu tipo de entidade e modelo). */
    public enum Kind { MERCHANT, SMITH }

    public static final String FLAG_TRADE = "dwarf_hold.trade";
    private static final String MSG = "message.isekai_mahou.dwarf_hold.";
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation idle;
    private final RawAnimation walk;
    private final RawAnimation work;
    private final Kind kind;
    private BlockPos home;
    private long lastRestockDay = -1;
    private int complainCooldown;

    public DwarfEntity(EntityType<? extends AbstractVillager> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        String id = kind == Kind.MERCHANT ? "dwarf_merchant" : "dwarf_smith";
        idle = RawAnimation.begin().thenLoop("animation." + id + ".idle");
        walk = RawAnimation.begin().thenLoop("animation." + id + ".walk");
        work = RawAnimation.begin().thenPlay("animation." + id + ".work");
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.45)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.5));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(8, new InteractGoal(this, Player.class, 4.0F, 1.0F));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    // =====================================================================================
    //  Comércio
    // =====================================================================================
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || isTrading()) return super.mobInteract(player, hand);
        if (hand == InteractionHand.MAIN_HAND) player.awardStat(Stats.TALKED_TO_VILLAGER);
        if (!level().isClientSide) {
            if (getOffers().isEmpty()) return InteractionResult.CONSUME;
            boolean kin = isKin(player);
            for (MerchantOffer o : getOffers()) {
                o.resetSpecialPriceDiff();
                // parente da montanha: 25% de desconto no item principal
                if (kin) o.addToSpecialPriceDiff(-Math.max(1, o.getBaseCostA().getCount() / 4));
            }
            if (kin) player.displayClientMessage(Component.translatable(MSG + "kin").withStyle(ChatFormatting.GOLD), true);
            else player.displayClientMessage(Component.translatable(MSG + "greet." + kind.name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.YELLOW), true);
            setTradingPlayer(player);
            openTradingScreen(player, getDisplayName(), 1);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private static boolean isKin(Player player) {
        try {
            return CharacterContent.data(player).race() == Race.DWARF;
        } catch (RuntimeException e) {
            return false; // módulo de personagem ausente/sem dados: sem desconto
        }
    }

    @Override
    protected void updateTrades() {
        MerchantOffers offers = getOffers();
        Item ingot = DwarfRegistry.MITHRIL_INGOT.get();
        if (kind == Kind.MERCHANT) {
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(ingot), 6, 10, 0.05F));
            offers.add(new MerchantOffer(new ItemCost(DwarfRegistry.RAW_MITHRIL.get(), 1), new ItemStack(Items.EMERALD, 5), 12, 5, 0.05F));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(DwarfRegistry.MITHRIL_NUGGET.get(), 2), 16, 2, 0.05F));
            offers.add(new MerchantOffer(new ItemCost(Items.DIAMOND, 1), new ItemStack(Items.EMERALD, 6), 12, 5, 0.05F));
            offers.add(new MerchantOffer(new ItemCost(Items.GOLD_INGOT, 4), new ItemStack(Items.EMERALD, 1), 16, 2, 0.05F));
            List<Element> els = new ArrayList<>(List.of(Element.FIRE, Element.EARTH, Element.WATER, Element.WIND, Element.LIGHT,
                    Element.SHADOW, Element.ARCANE));
            for (int i = 0; i < 3; i++) {
                Element e = els.remove(random.nextInt(els.size()));
                var ess = CoreRegistries.ESSENCES.get(e);
                if (ess != null) offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 7), new ItemStack(ess.get()), 4, 5, 0.05F));
            }
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 28), java.util.Optional.of(new ItemCost(ingot, 4)),
                    new ItemStack(DwarfRegistry.APPRAISAL_MONOCLE.get()), 1, 30, 0.05F));
        } else {
            offers.add(new MerchantOffer(new ItemCost(ingot, 3), java.util.Optional.of(new ItemCost(Items.EMERALD, 8)),
                    new ItemStack(DwarfRegistry.RUNE_FORGE_ITEM.get()), 2, 15, 0.05F));
            offers.add(new MerchantOffer(new ItemCost(Items.IRON_INGOT, 6), new ItemStack(Items.EMERALD, 1), 16, 2, 0.05F));
            offers.add(new MerchantOffer(new ItemCost(Items.COAL, 15), new ItemStack(Items.EMERALD, 1), 16, 2, 0.05F));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.BLAST_FURNACE), 4, 2, 0.05F));
            offers.add(new MerchantOffer(new ItemCost(ingot, 2), java.util.Optional.of(new ItemCost(Items.EMERALD, 10)),
                    new ItemStack(DwarfRegistry.MITHRIL_PICKAXE.get()), 2, 15, 0.05F));
            offers.add(new MerchantOffer(new ItemCost(ingot, 4), java.util.Optional.of(new ItemCost(Items.EMERALD, 20)),
                    new ItemStack(DwarfRegistry.MITHRIL_WAR_AXE.get()), 1, 20, 0.05F));
            offers.add(new MerchantOffer(new ItemCost(ingot, 12), java.util.Optional.of(new ItemCost(Items.EMERALD, 48)),
                    new ItemStack(DwarfRegistry.HERO_SHIELD.get()), 1, 40, 0.05F));
        }
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            level().addFreshEntity(new ExperienceOrb(level(), getX(), getY() + 0.5, getZ(), 2 + random.nextInt(4)));
        }
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);
        if (getTradingPlayer() instanceof ServerPlayer sp) MagicApi.get(sp).setFlag(FLAG_TRADE, true);
        if (kind == Kind.SMITH) triggerAnim("action", "work");
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
    //  Casa, estoque, imortalidade
    // =====================================================================================
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (kind == Kind.SMITH && random.nextInt(14) == 0) {
                level().addParticle(ParticleTypes.SMALL_FLAME, getRandomX(0.5), getY() + 0.9, getRandomZ(0.5), 0, 0.02, 0);
            }
            return;
        }
        if (home == null) home = blockPosition();
        if (!hasRestriction()) restrictTo(home, 10);
        if (complainCooldown > 0) complainCooldown--;
        long day = level().getDayTime() / 24000L;
        if (day != lastRestockDay) {
            lastRestockDay = day;
            for (MerchantOffer o : getOffers()) o.resetUses();
        }
        if (kind == Kind.SMITH && !isTrading() && tickCount % 200 == 0 && random.nextInt(3) == 0) {
            triggerAnim("action", "work");
            level().playSound(null, blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.3F, 1.2F);
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof Player p && complainCooldown <= 0 && isInvulnerableTo(source)) {
            complainCooldown = 40;
            p.displayClientMessage(Component.translatable(MSG + "hit").withStyle(ChatFormatting.RED), true);
            playSound(SoundEvents.ANVIL_LAND, 0.35F, 1.4F);
        }
        return super.hurt(source, amount);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("DwarfHome", NbtUtils.writeBlockPos(home));
        tag.putLong("RestockDay", lastRestockDay);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home = NbtUtils.readBlockPos(tag, "DwarfHome").orElse(null);
        lastRestockDay = tag.contains("RestockDay") ? tag.getLong("RestockDay") : -1;
    }

    // =====================================================================================
    //  Sons — sem o "hmm" de villager (o dono achava irritante, 13/11): anão de forja soa como forja.
    //  Ambiente raro e baixinho (martelinho no metal), trocas com tilintar de ferro, dor/morte de gente.
    // =====================================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        return isTrading() ? SoundEvents.ARMOR_EQUIP_IRON.value() : SoundEvents.ANVIL_STEP;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 320;
    }

    @Override
    protected float getSoundVolume() {
        return 0.45F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean yes) {
        return yes ? SoundEvents.CHAIN_PLACE : SoundEvents.CHAIN_STEP;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.SMITHING_TABLE_USE;
    }

    @Override
    public float getVoicePitch() {
        return 0.65F + random.nextFloat() * 0.1F;
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    private PlayState mainState(AnimationState<DwarfEntity> state) {
        return state.setAndContinue(state.isMoving() ? walk : idle);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        reg.add(new AnimationController<>(this, "main", 4, this::mainState),
                new AnimationController<>(this, "action", 2, s -> PlayState.STOP).triggerableAnim("work", work));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
