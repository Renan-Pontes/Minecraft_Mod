package com.renanpontes.isekaimahou.feature.cities.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.cities.CityLines;
import com.renanpontes.isekaimahou.feature.cities.trade.CityTrades;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Mercador da cidade. A profissão vem do NBT do template ({@code Profession}) e é sincronizada (a facção do
 * escriba/sacerdote/receptador precisa funcionar no cliente). As trocas são montadas POR JOGADOR ao abrir (preço ×
 * reputação, ofertas extras por patamar) e o estoque é do mercador, renovado a cada dia de jogo.
 */
public class CityMerchant extends CityNpc implements Merchant {
    private static final EntityDataAccessor<String> DATA_PROFESSION =
            SynchedEntityData.defineId(CityMerchant.class, EntityDataSerializers.STRING);
    private static final RawAnimation WORK = loop("work");

    @Nullable private Player tradingPlayer;
    private MerchantOffers offers = new MerchantOffers();
    /** Chave de estoque de cada oferta aberta agora (mesma ordem de {@link #offers}). */
    private List<String> offerKeys = new ArrayList<>();
    /** Usos de cada oferta no dia {@link #stockDay}. */
    private final Map<String, Integer> stock = new HashMap<>();
    private long stockDay = -1;

    public CityMerchant(EntityType<? extends CityMerchant> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new PanicGoal(this, 1.3));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Mob.class, Citizen::isMonster, 9.0F, 0.8, 1.2, e -> true));
        // v1.0: mercador de cidade em guerra com o jogador fecha a banca e se afasta.
        goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, e -> e instanceof Player p && isWarEnemy(p), 8.0F, 0.75, 1.1,
                e -> true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PROFESSION, "");
    }

    // =====================================================================================
    //  Profissão e aparência
    // =====================================================================================
    public Profession profession() {
        Profession p = Profession.byId(entityData.get(DATA_PROFESSION));
        return p != null ? p : Profession.GROCER;
    }

    public void setProfession(Profession p) {
        entityData.set(DATA_PROFESSION, p.id);
    }

    @Override
    protected void randomizeIfNeeded(RandomSource r) {
        // Mantém a profissão do template; só sorteia se veio sem (ovo, /summon).
        if (Profession.byId(entityData.get(DATA_PROFESSION)) == null) setProfession(Profession.random(r));
        super.randomizeIfNeeded(r);
    }

    @Override
    @Nullable
    public String factionId() {
        // escriba → academy, sacerdote → church, receptador → underworld; o resto é do reino da cidade
        String f = profession().faction;
        return f != null ? f : super.factionId();
    }

    @Override
    public String kindId() {
        return "merchant";
    }

    @Override
    public String censusKey() {
        return "merchant:" + profession().id;
    }

    @Override
    public String textureName() {
        return "merchant_" + profession().id + "_" + variant();
    }

    @Override
    protected int variantCount() {
        return 2;
    }

    @Override
    public boolean female() {
        return variant() == 1;
    }

    @Override
    public Set<String> visibleBones() {
        Profession p = profession();
        Set<String> s = new HashSet<>(p.bones);
        if (female()) {
            s.add("hair_long");
            if (p != Profession.BLACKSMITH && p != Profession.FENCE) s.add("skirt");
        } else if (p == Profession.BLACKSMITH || p == Profession.INNKEEPER || p == Profession.JEWELER) {
            s.add("beard");
        }
        return s;
    }

    @Override
    protected int wanderRadius() {
        return 4;
    }

    @Override
    protected int strollInterval() {
        return 400;
    }

    @Override
    protected RawAnimation idleAnimation() {
        Profession p = profession();
        // Ferreiro martela; alquimista mexe nos frascos (a mesma animação de "trabalho").
        return (p == Profession.BLACKSMITH || p == Profession.ALCHEMIST) && tradingPlayer == null ? WORK : super.idleAnimation();
    }

    @Override
    @Nullable
    protected Component ambientLine(Player near) {
        return Component.translatable(CityLines.PREFIX + "merchant." + profession().id + ".ambient."
                + random.nextInt(CityLines.PROFESSION_AMBIENT));
    }

    // =====================================================================================
    //  Comércio
    // =====================================================================================
    /** Abre as trocas para o jogador (o diálogo já checou {@link Reputation#canTrade}). */
    public boolean openTrades(ServerPlayer player) {
        if (tradingPlayer != null && tradingPlayer != player && tradingPlayer.isAlive()) return false;
        refreshStockDay();
        List<CityTrades.Trade> trades = CityTrades.build(this, player);
        MerchantOffers built = new MerchantOffers();
        List<String> keys = new ArrayList<>();
        for (CityTrades.Trade t : trades) {
            MerchantOffer o = t.offer();
            int used = stock.getOrDefault(t.key(), 0);
            for (int i = 0; i < used && i < o.getMaxUses(); i++) o.increaseUses();
            built.add(o);
            keys.add(t.key());
        }
        if (built.isEmpty()) return false;
        offers = built;
        offerKeys = keys;
        setTradingPlayer(player);
        openTradingScreen(player, getDisplayName(), 1);
        return true;
    }

    private void refreshStockDay() {
        long day = level().getDayTime() / 24000L;
        if (day != stockDay) {
            stockDay = day;
            stock.clear();
        }
    }

    /**
     * Só guarda quem está negociando. Ao virar null, a tela de troca do jogador antigo fecha sozinha no próximo tick
     * ({@code MerchantMenu.stillValid} exige {@code getTradingPlayer() == player}) — fechar aqui recursivamente
     * chamaria {@code MerchantMenu.removed} duas vezes.
     */
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
        int idx = offers.indexOf(offer);
        if (idx >= 0 && idx < offerKeys.size()) {
            refreshStockDay();
            stock.merge(offerKeys.get(idx), 1, Integer::sum);
        }
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.9, getZ(), 5, 0.3, 0.2, 0.3, 0.0);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.4F);
            if (tradingPlayer instanceof ServerPlayer sp) {
                // Comércio dá um pouco de reputação na facção da profissão (limitado por dia).
                Reputation.addDaily(sp, faction(), 1, "cities.trade", 15,
                        Component.translatable("reason.isekai_mahou.cities.trade"));
            }
        }
        say(CityLines.pick("merchant.thanks", random));
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

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Profession", profession().id);
        tag.putLong("StockDay", stockDay);
        CompoundTag st = new CompoundTag();
        stock.forEach(st::putInt);
        tag.put("Stock", st);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Profession p = Profession.byId(tag.getString("Profession"));
        if (p != null) setProfession(p);
        stockDay = tag.contains("StockDay") ? tag.getLong("StockDay") : -1;
        stock.clear();
        CompoundTag st = tag.getCompound("Stock");
        for (String k : st.getAllKeys()) stock.put(k, st.getInt(k));
    }
}
