package com.renanpontes.isekaimahou.core.classes;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * v1.0 — Mana maior e <b>Limite de Mana treinável</b> (vale igual para Mana, Vigor e Foco: é o mesmo valor salvo).
 *
 * <h3>Números (inflação ×{@value #MANA_SCALE}, ver docs/v10/API_spells.md)</h3>
 * <pre>
 * L' = min(nível, 100)        M = marco = maior múltiplo de 10 ≤ L' (mín. 1)
 * TETO(L, C)  = 3 × (100 + 12·(L'−1) + 40·(C−1)) + Progression.levelManaLimitBonus(L)        ← o máximo treinável
 * PISO(L, C)  = 3 × (100 +  5·(L'−1) +  3·(M−1) + 40·(C−1)) + levelManaLimitBonus(marco de L) / 2  ← garantido pelos marcos
 * LIMITE      = min(TETO, PISO + treino)      máximo do recurso = LIMITE + bônus de equipamento
 * </pre>
 * Nível 1: 300 (= 3× os 100 da v0.8). Nível 99/C9: piso ≈ 3,3 mil, teto ≈ 4,8 mil.
 *
 * <h3>Treino</h3>
 * Esvaziar o recurso (≤ {@value #EMPTY_FRACTION} do máximo) DEPOIS de tê-lo enchido (≥ {@value #ARM_FRACTION}) conta
 * 1 treino — no máximo {@value #TRAININGS_PER_DAY} por dia do mundo ({@link MagicApi#tryUseDaily}, chave
 * {@value #DAILY_KEY}). Cada treino soma {@link #trainGain} ao limite (até o TETO do nível). O treino é permanente: ao
 * subir de nível o teto cresce e há espaço para treinar de novo.
 * <p>
 * Todo esvaziamento (treinando ou não) deixa o jogador com <b>fadiga</b> por {@value #FATIGUE_TICKS} ticks: a
 * regeneração cai para {@value #FATIGUE_REGEN}× (modificador {@link #FATIGUE_MODIFIER} no atributo
 * {@code isekai_mahou:mana_regen}). A regeneração base também é escalada ×{@value #MANA_SCALE} pelo modificador
 * permanente {@link #SCALE_MODIFIER} (para encher a barra maior no mesmo tempo da v0.8).
 */
@EventBusSubscriber(modid = IsekaiMahou.MODID)
public final class ResourceTraining {
    /** Inflação de números do recurso (custos de magia usam o mesmo fator: {@code SpellCost.COST_SCALE}). */
    public static final float MANA_SCALE = 3.0F;
    /** Marcos de nível: a cada N níveis o piso do limite sobe. */
    public static final int MILESTONE = 10;
    /** Treinos que contam por dia do mundo. */
    public static final int TRAININGS_PER_DAY = 3;
    /** Chave do uso diário ({@code daily.resource_training} nos contadores). */
    public static final String DAILY_KEY = "resource_training";
    /** Cada treino soma esta fração do teto do nível (mínimo {@link #MIN_GAIN}). */
    public static final float TRAIN_GAIN_FRACTION = 0.04F;
    public static final int MIN_GAIN = 6;
    /** Fração do máximo a partir da qual o recurso conta como "cheio" (arma o treino). */
    public static final float ARM_FRACTION = 0.95F;
    /** Fração do máximo abaixo da qual o recurso conta como "esvaziado". */
    public static final float EMPTY_FRACTION = 0.02F;
    /** Duração da fadiga (regen reduzida) depois de esvaziar: 60 s. */
    public static final int FATIGUE_TICKS = 1200;
    /** Multiplicador da regeneração durante a fadiga. */
    public static final float FATIGUE_REGEN = 0.3F;

    /** Modificador temporário da fadiga (ADD_MULTIPLIED_TOTAL). */
    public static final ResourceLocation FATIGUE_MODIFIER = IsekaiMahou.id("resource_fatigue");
    /**
     * Modificador permanente que escala a regeneração ×{@link #MANA_SCALE} (ADD_MULTIPLIED_TOTAL +2). É TOTAL (e não BASE)
     * para que os bônus de equipamento ADD_MULTIPLIED_BASE (+20% de uma roupa...) continuem valendo a mesma porcentagem.
     */
    public static final ResourceLocation SCALE_MODIFIER = IsekaiMahou.id("resource_scale");

    // =====================================================================================
    //  Fórmulas (puras; servem no cliente também)
    // =====================================================================================

    /** Teto do Limite (o máximo que o treino alcança neste nível/Círculo). */
    public static int ceiling(int level, int circle) {
        int l = Math.max(1, Math.min(level, Progression.CIRCLE_TEN_LEVEL));
        int c = Math.max(1, circle);
        return Math.round(MANA_SCALE * (100 + 12 * (l - 1) + 40 * (c - 1))) + Progression.levelManaLimitBonus(level);
    }

    /** Piso do Limite (sobe a cada nível e salta nos marcos). Nunca passa do teto. */
    public static int floor(int level, int circle) {
        int l = Math.max(1, Math.min(level, Progression.CIRCLE_TEN_LEVEL));
        int c = Math.max(1, circle);
        int m = milestone(l);
        int base = Math.round(MANA_SCALE * (100 + 5 * (l - 1) + 3 * (m - 1) + 40 * (c - 1)));
        int high = Progression.levelManaLimitBonus(milestone(level)) / 2;
        return Math.min(ceiling(level, circle), base + high);
    }

    /** Limite atual: piso + treino, cortado no teto. */
    public static int limit(int level, int circle, int trained) {
        return Math.min(ceiling(level, circle), floor(level, circle) + Math.max(0, trained));
    }

    /** Quanto um treino soma ao limite neste nível. */
    public static int trainGain(int level, int circle) {
        return Math.max(MIN_GAIN, Math.round(ceiling(level, circle) * TRAIN_GAIN_FRACTION));
    }

    /** Último marco atingido (1, 10, 20, ...). */
    public static int milestone(int level) {
        int l = Math.max(1, level);
        return l < MILESTONE ? 1 : (l / MILESTONE) * MILESTONE;
    }

    /** O nível {@code level} é um marco (o piso salta ao chegar nele)? */
    public static boolean isMilestone(int level) {
        return level >= MILESTONE && level % MILESTONE == 0;
    }

    /** Abaixo disto o recurso conta como esvaziado. */
    public static float emptyThreshold(int max) {
        return Math.max(1F, max * EMPTY_FRACTION);
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        MagicData d = MagicApi.get(p);
        if (d.migrationNotice >= 0) {
            notifyMigration(p, d.migrationNotice);
            d.migrationNotice = -1;
        }
        long now = p.serverLevel().getGameTime();
        if (d.emptiedPending) {
            d.emptiedPending = false;
            if (!p.isSpectator() && !p.getAbilities().instabuild) onEmptied(p, d, now);
        }
        if (p.tickCount % 10 == 0) syncModifiers(p, d, now);
    }

    /** O recurso acabou de chegar a ~0: fadiga e, se estava "armado", um treino. */
    private static void onEmptied(ServerPlayer p, MagicData d, long now) {
        boolean wasFatigued = d.fatigued(now);
        d.setFatigueUntil(now + FATIGUE_TICKS);
        syncModifiers(p, d, now);
        boolean armed = d.trainingArmed;
        d.trainingArmed = false;
        if (armed) {
            train(p, d);
        } else if (!wasFatigued) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.training.fatigue", MagicApi.resourceName(p))
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Um treino: conta no dia, soma ao limite (até o teto) e avisa. */
    private static void train(ServerPlayer p, MagicData d) {
        int room = d.manaCeiling() - d.manaLimit();
        if (room <= 0) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.training.at_ceiling", MagicApi.resourceName(p))
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (!MagicApi.tryUseDaily(p, DAILY_KEY, TRAININGS_PER_DAY)) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.training.daily_done", MagicApi.resourceName(p), TRAININGS_PER_DAY)
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int gained = d.addTrainedRaw(trainGain(d.level(), d.circle()));
        d.countTrainingRaw();
        int today = MagicApi.dailyUses(p, DAILY_KEY);
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.training.gain", MagicApi.resourceName(p), gained,
                d.manaLimit(), d.manaCeiling(), today, TRAININGS_PER_DAY).withStyle(ChatFormatting.AQUA));
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        p.serverLevel().sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.8, 0.5, 0.8);
        MagicApi.addCounter(p, "resource_training", 1);
    }

    /** Põe/tira os modificadores de regeneração (escala ×3 permanente; fadiga enquanto durar). */
    public static void syncModifiers(ServerPlayer p, MagicData d, long now) {
        AttributeInstance inst = p.getAttribute(CoreRegistries.MANA_REGEN);
        if (inst == null) return;
        double scale = MANA_SCALE - 1.0;
        AttributeModifier cur = inst.getModifier(SCALE_MODIFIER);
        if (cur == null || Math.abs(cur.amount() - scale) > 1.0E-6 || cur.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            inst.removeModifier(SCALE_MODIFIER);
            inst.addTransientModifier(new AttributeModifier(SCALE_MODIFIER, scale, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        boolean fatigued = d.fatigued(now) && !p.getAbilities().instabuild;
        AttributeModifier fat = inst.getModifier(FATIGUE_MODIFIER);
        if (fatigued && fat == null) {
            inst.addTransientModifier(new AttributeModifier(FATIGUE_MODIFIER, FATIGUE_REGEN - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!fatigued && fat != null) {
            inst.removeModifier(FATIGUE_MODIFIER);
            if (d.fatigueUntil() > 0) {
                p.displayClientMessage(Component.translatable("message.isekai_mahou.training.recovered", MagicApi.resourceName(p))
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    /** Aviso único depois de converter um save da v0.8 para os 9 loadouts. */
    private static void notifyMigration(ServerPlayer p, int toPresets) {
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.loadouts_migrated", MagicData.LOADOUTS)
                .withStyle(ChatFormatting.GOLD));
        if (toPresets > 0) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.loadouts_migrated_presets", toPresets)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private ResourceTraining() {}
}
