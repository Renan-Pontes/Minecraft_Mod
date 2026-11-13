package com.renanpontes.isekaimahou.feature.settlements.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.CityTrades;
import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsNames;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Comerciante de ofício da cidade fundada (onda 6, city_trades): Ferreiro, Curandeiro, Escriba, Taverneiro, Estribeiro,
 * Joalheiro, Lenhador, Minerador e Mercador Arcano. Um tipo de entidade só; o OFÍCIO ({@link CityTrades.Job}) decide a
 * roupa (textura {@code trader_<ofício>_<variante>} e peças do modelo), as falas, as ofertas e o serviço.
 * <p>Fica na loja dele de dia (raio curto em volta do prédio) e passeia um pouco mais à noite; o nome com o ofício
 * aparece sobre a cabeça quando há alguém perto. Clique = loja (estoque do dia da cidade, veja {@link CityTrades});
 * agachar + clique = serviço do ofício. A cidade o controla por uma lista própria ({@link CityTrades#heartbeat}).
 */
public class TraderNpc extends MerchantNpc {
    private static final EntityDataAccessor<String> DATA_JOB = SynchedEntityData.defineId(TraderNpc.class, EntityDataSerializers.STRING);

    /** Chave de cada oferta aberta agora, na ordem da tela de troca (para descontar do estoque do dia). */
    private final List<String> offerKeys = new ArrayList<>();
    private boolean jobSet;
    private boolean dayShift = true;
    private int routine;

    public TraderNpc(EntityType<? extends TraderNpc> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_JOB, CityTrades.Job.BLACKSMITH.id);
    }

    public CityTrades.Job job() {
        CityTrades.Job j = CityTrades.Job.byId(entityData.get(DATA_JOB));
        return j == null ? CityTrades.Job.BLACKSMITH : j;
    }

    /** Dá o ofício e o nome com ele ("Bjorn, Ferreiro"). */
    public void setJob(CityTrades.Job job) {
        entityData.set(DATA_JOB, job.id);
        jobSet = true;
        boolean female = variant() % 2 == 1;
        setCustomName(Component.translatable("settlements.isekai_mahou.npc.named",
                Component.literal(SettlementsNames.pick(style(), female, random)), job.displayName(female)).withColor(job.color));
    }

    @Override
    public Settlement.Role role() {
        return Settlement.Role.MERCHANT;
    }

    /** Uma textura por ofício (0 = homem, 1 = mulher), igual em todos os estilos de cidade. */
    @Override
    public String textureName() {
        return "trader_" + job().id + "_" + Math.floorMod(variant(), 2);
    }

    @Override
    public Set<String> visibleBones() {
        return job().bones;
    }

    @Override
    protected int wanderRadius() {
        return dayShift ? 5 : 12;
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        // ovo/comando: um ofício qualquer (a cidade dá o certo logo depois, em CityTrades.spawn)
        if (!jobSet) setJob(CityTrades.Job.values()[random.nextInt(CityTrades.Job.values().length)]);
        return out;
    }

    // =====================================================================================
    //  Falas
    // =====================================================================================
    @Override
    @Nullable
    protected Component ambientLine(Player near) {
        return CityTrades.line(job(), "ambient", 3, random);
    }

    // =====================================================================================
    //  Loja e serviço
    // =====================================================================================
    @Override
    protected InteractionResult interact(ServerPlayer sp) {
        if (sp.isShiftKeyDown()) return CityTrades.service(sp, this);
        Player busy = getTradingPlayer();
        if (busy != null && busy != sp && busy.isAlive()) {
            say(line("merchant.busy", 2));
            return InteractionResult.CONSUME;
        }
        overrideOffers(CityTrades.offers(sp.server, settlement(), this, offerKeys));
        setTradingPlayer(sp);
        openTradingScreen(sp, getDisplayName(), 1);
        say(CityTrades.line(job(), "hello", 2, random));
        Hints.show(sp, "city_trades");
        // onda 10: primeira vez num Mecânico — o que ele vende e os serviços (agachar + clique)
        if (job() == CityTrades.Job.MECHANIC) Hints.show(sp, "vehicles.mechanic", Component.keybind("key.sneak"));
        return InteractionResult.CONSUME;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        int idx = getOffers().indexOf(offer);
        super.notifyTrade(offer);
        if (idx >= 0 && idx < offerKeys.size() && level() instanceof ServerLevel sl) {
            CityTrades.onTrade(sl, settlement(), this, offerKeys.get(idx), offer);
        }
    }

    // =====================================================================================
    //  Rotina
    // =====================================================================================
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || ++routine < 20) return;
        routine = 0;
        // nome sobre a cabeça só com alguém por perto (tela limpa de longe)
        boolean near = level().getNearestPlayer(this, 10.0) != null;
        if (isCustomNameVisible() != near) setCustomNameVisible(near);
        // de dia fica na loja; à noite passeia um pouco mais longe
        boolean day = level().isDay();
        if (day != dayShift) {
            dayShift = day;
            if (home != null) setHome(home);
        }
        // ofícios de braço martelam/racham de vez em quando, parados na loja
        if (job().manual) {
            boolean work = day && getTradingPlayer() == null && getNavigation().isDone() && speech() == null
                    && (working() ? random.nextInt(4) != 0 : random.nextInt(6) == 0);
            setWorking(work);
        }
    }

    @Override
    public void die(DamageSource source) {
        // cidade é sagrada: a loja fica vaga e outro comerciante chega logo
        if (level() instanceof ServerLevel sl) CityTrades.onDeath(sl, this);
        super.die(source);
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("TradeJob", entityData.get(DATA_JOB));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TradeJob")) {
            CityTrades.Job j = CityTrades.Job.byId(tag.getString("TradeJob"));
            if (j != null) entityData.set(DATA_JOB, j.id);
            jobSet = true;
        }
    }
}
