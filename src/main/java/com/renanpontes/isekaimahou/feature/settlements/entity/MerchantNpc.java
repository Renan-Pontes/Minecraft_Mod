package com.renanpontes.isekaimahou.feature.settlements.entity;

import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * Mercador da cidade do jogador: compra matéria-prima e vende produtos do estilo (pão e ferramentas no Reino, especiarias
 * e areia de vidro no Deserto, peles e hidromel no Norte). Cada troca conta para os IMPOSTOS do dia; o decreto de preços
 * (baixo/normal/alto) muda o preço aqui também. Não negocia com inimigos (guerra).
 */
public class MerchantNpc extends SettlementNpc implements Merchant {
    private static final Set<String> KINGDOM = Set.of("apron", "satchel", "cap");
    private static final Set<String> DESERT = Set.of("hood", "satchel", "skirt");
    private static final Set<String> NORDIC = Set.of("apron", "satchel", "beard");

    @Nullable private Player tradingPlayer;
    private MerchantOffers offers = new MerchantOffers();

    public MerchantNpc(EntityType<? extends MerchantNpc> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public Settlement.Role role() {
        return Settlement.Role.MERCHANT;
    }

    @Override
    public Set<String> visibleBones() {
        return switch (style()) {
            case DESERT -> DESERT;
            case NORDIC -> NORDIC;
            default -> KINGDOM;
        };
    }

    @Override
    protected int wanderRadius() {
        return 8;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new FleeEnemiesGoal(this));
    }

    // =====================================================================================
    //  Comércio
    // =====================================================================================
    @Override
    protected InteractionResult interact(ServerPlayer sp) {
        if (tradingPlayer != null && tradingPlayer != sp && tradingPlayer.isAlive()) {
            say(line("merchant.busy", 2));
            return InteractionResult.CONSUME;
        }
        Settlement s = settlement();
        offers = buildOffers(s == null ? Settlement.TAX_NORMAL : s.tax);
        setTradingPlayer(sp);
        openTradingScreen(sp, getDisplayName(), 1);
        say(line("merchant.hello", 3));
        return InteractionResult.CONSUME;
    }

    private MerchantOffers buildOffers(int tax) {
        Item coin = SettlementService.coinItem();
        float mult = tax == Settlement.TAX_LOW ? 0.85F : tax == Settlement.TAX_HIGH ? 1.25F : 1.0F;
        MerchantOffers out = new MerchantOffers();
        // compra (o jogador vende): matéria-prima da região
        switch (style()) {
            case DESERT -> {
                buy(out, coin, Items.SAND, 32, 2);
                buy(out, coin, Items.CACTUS, 16, 2);
                buy(out, coin, Items.GOLD_INGOT, 3, 3);
            }
            case NORDIC -> {
                buy(out, coin, Items.SPRUCE_LOG, 24, 2);
                buy(out, coin, Items.LEATHER, 8, 2);
                buy(out, coin, Items.COD, 12, 2);
            }
            default -> {
                buy(out, coin, Items.WHEAT, 24, 2);
                buy(out, coin, Items.COBBLESTONE, 48, 1);
                buy(out, coin, Items.IRON_INGOT, 4, 3);
            }
        }
        // venda (o jogador compra)
        switch (style()) {
            case DESERT -> {
                sell(out, coin, mult, 2, new ItemStack(Items.DRIED_KELP, 16));
                sell(out, coin, mult, 3, new ItemStack(Items.GLASS, 16));
                sell(out, coin, mult, 4, new ItemStack(Items.ORANGE_TERRACOTTA, 16));
                sell(out, coin, mult, 6, new ItemStack(Items.GOLDEN_CARROT, 6));
                sell(out, coin, mult, 10, new ItemStack(Items.SADDLE));
                sell(out, coin, mult, 12, new ItemStack(Items.SPYGLASS));
            }
            case NORDIC -> {
                sell(out, coin, mult, 2, new ItemStack(Items.COOKED_SALMON, 8));
                sell(out, coin, mult, 3, new ItemStack(Items.SPRUCE_PLANKS, 32));
                sell(out, coin, mult, 5, new ItemStack(Items.LEATHER_CHESTPLATE));
                sell(out, coin, mult, 6, new ItemStack(Items.HONEY_BOTTLE, 4));
                sell(out, coin, mult, 9, new ItemStack(Items.IRON_AXE));
                sell(out, coin, mult, 14, new ItemStack(Items.SHIELD));
            }
            default -> {
                sell(out, coin, mult, 2, new ItemStack(Items.BREAD, 8));
                sell(out, coin, mult, 3, new ItemStack(Items.STONE_BRICKS, 32));
                sell(out, coin, mult, 4, new ItemStack(Items.LANTERN, 4));
                sell(out, coin, mult, 6, new ItemStack(Items.IRON_PICKAXE));
                sell(out, coin, mult, 8, new ItemStack(Items.CROSSBOW));
                sell(out, coin, mult, 12, new ItemStack(Items.BELL));
            }
        }
        return out;
    }

    private static void buy(MerchantOffers out, Item coin, Item what, int count, int pay) {
        out.add(new MerchantOffer(new ItemCost(what, count), Optional.empty(), new ItemStack(coin, pay), 12, 0, 0.0F));
    }

    private static void sell(MerchantOffers out, Item coin, float mult, int price, ItemStack result) {
        int p = Math.max(1, Math.min(64, Math.round(price * mult)));
        out.add(new MerchantOffer(new ItemCost(coin, p), Optional.empty(), result, 8, 0, 0.0F));
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        tradingPlayer = player;
    }

    @Override
    @Nullable
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers newOffers) {
        offers = newOffers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.9, getZ(), 5, 0.3, 0.2, 0.3, 0.0);
            // a troca entra nos impostos pela estatística "negociou" (SettlementService.pollTrades)
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.4F);
        }
        say(line("merchant.thanks", 3));
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        if (!level().isClientSide && !stack.isEmpty() && random.nextInt(3) == 0) playSound(SoundEvents.BOOK_PAGE_TURN, 0.6F, 1.2F);
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {}

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.ITEM_PICKUP;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tradingPlayer == null) return;
        if (!tradingPlayer.isAlive() || distanceToSqr(tradingPlayer) > 64 || !(tradingPlayer.containerMenu instanceof MerchantMenu)) {
            setTradingPlayer(null);
        } else {
            getNavigation().stop();
            getLookControl().setLookAt(tradingPlayer, 30.0F, 30.0F);
        }
    }

    @Override
    public void die(DamageSource source) {
        setTradingPlayer(null);
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        setTradingPlayer(null);
        super.remove(reason);
    }
}
