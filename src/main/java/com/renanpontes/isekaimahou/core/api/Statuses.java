package com.renanpontes.isekaimahou.core.api;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.status.StatusEngine;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Condições de status do núcleo (v0.8): Molhado, Em Chamas, Gelado, Congelado, Eletrizado, Sangrando, Petrificação e
 * os estados antigos (Corrompido, Exposto, Atordoado, Enraizado, Silenciado). Cada condição é um {@code MobEffect} do
 * núcleo; os acúmulos são o amplificador + 1.
 *
 * <p>Use SEMPRE {@link #apply} para aplicar (soma acúmulos até o máximo, aplica as regras de chefe, de imunidade e de
 * {@code STATUS_ON_PLAYERS}, guarda o poder/quem aplicou para o dano contínuo creditar o conjurador, e sincroniza os
 * ícones com os clientes). Chame no servidor.
 *
 * <pre>
 * Statuses.apply(alvo, Statuses.Kind.BURNING, 2, 120, ctx.caster(), ctx.powerMult());   // Em Chamas +2 por 6 s
 * if (Statuses.has(alvo, Statuses.Kind.WET)) ...                                         // está Molhado?
 * int gelo = Statuses.stacks(alvo, Statuses.Kind.CHILLED);                              // 0..5
 * </pre>
 */
public final class Statuses {
    /** Duração padrão de uma condição aplicada por magia (6 s). */
    public static final int DEFAULT_TICKS = 120;

    /** Tipos de condição. {@code id} = id do efeito ({@code isekai_mahou:<id>}), também o nome do ícone. */
    public enum Kind {
        WET("soaked", 1, 0x3FA9FF, true),
        BURNING("burning", 5, 0xFF6A2B, true),
        CHILLED("chilled", 5, 0x9FDFFF, true),
        FROZEN("frozen", 1, 0xA8E8FF, true),
        SHOCKED("shocked", 3, 0x7FDBFF, true),
        BLEEDING("bleeding", 10, 0xC0182E, true),
        PETRIFIED("petrified", 3, 0x9A948A, true),
        CORRUPTED("corrupted", 5, 0x5A1E7A, false),
        EXPOSED("exposed", 4, 0xFFD86B, false),
        STUNNED("stunned", 1, 0xFFF27A, false),
        ROOTED("rooted", 1, 0x6B8E23, false),
        SILENCED("silenced", 1, 0x9A9AB0, false);

        private final String id;
        private final int maxStacks;
        private final int color;
        private final boolean elemental;

        Kind(String id, int maxStacks, int color, boolean elemental) {
            this.id = id;
            this.maxStacks = maxStacks;
            this.color = color;
            this.elemental = elemental;
        }

        /** Id do efeito (sem namespace): {@code soaked}, {@code burning}... */
        public String id() { return id; }
        /** Acúmulos máximos. */
        public int maxStacks() { return maxStacks; }
        /** Cor RGB (sem alpha) da condição. */
        public int color() { return color; }
        /** Condição elemental nova da v0.8 (desligada em jogadores por {@code STATUS_ON_PLAYERS}). */
        public boolean elemental() { return elemental; }
        /** Nome traduzido ("Molhado", "Em Chamas"...), colorido. */
        public MutableComponent displayName() {
            return Component.translatable("effect.isekai_mahou." + id).withColor(color);
        }

        /**
         * Nome para uma quantidade de acúmulos: igual a {@link #displayName()}, exceto Petrificação ("Enrijecendo" com
         * 1–2, "Petrificado" com 3).
         */
        public MutableComponent displayName(int stacks) {
            if (this == PETRIFIED && stacks > 0) {
                return Component.translatable(stacks >= 3 ? "status.isekai_mahou.petrified.full" : "status.isekai_mahou.petrified.stiff")
                        .withColor(color);
            }
            return displayName();
        }
        /** Holder do efeito (o registro já está pronto depois do carregamento do mod). */
        public Holder<MobEffect> effect() { return Statuses.effect(this); }

        private static final Kind[] VALUES = values();

        @Nullable
        public static Kind byOrdinal(int i) { return i >= 0 && i < VALUES.length ? VALUES[i] : null; }

        @Nullable
        public static Kind byId(String id) {
            for (Kind k : VALUES) if (k.id.equals(id)) return k;
            return null;
        }

        /** Tipo correspondente a um efeito do núcleo, ou null se não for uma condição. */
        @Nullable
        public static Kind of(Holder<MobEffect> effect) {
            for (Kind k : VALUES) if (effect.is(Statuses.effect(k))) return k;
            return null;
        }
    }

    /**
     * Aplica (soma) acúmulos de uma condição.
     *
     * @param stacks acúmulos a somar (≥1; limitado ao máximo do tipo)
     * @param ticks  duração em ticks (≤0 = {@link #DEFAULT_TICKS}); cada acúmulo novo reinicia a duração, sem nunca encurtar
     * @param source quem aplicou (crédito do dano contínuo e das reações), pode ser null (ambiente)
     * @param power  poder de quem aplicou (multiplica o dano contínuo); 1 = normal
     */
    public static void apply(LivingEntity target, Kind kind, int stacks, int ticks, @Nullable LivingEntity source, float power) {
        StatusEngine.apply(target, kind, stacks, ticks, source, power);
    }

    /**
     * Igual a {@link #apply}, mas sem passar de {@code cap} acúmulos; se o alvo já estiver no limite, só renova a duração.
     * Útil para efeitos que pulsam (auras, feixes) e não devem empilhar até o máximo.
     */
    public static void applyUpTo(LivingEntity target, Kind kind, int stacks, int ticks, @Nullable LivingEntity source,
                                 float power, int cap) {
        StatusEngine.applyUpTo(target, kind, stacks, ticks, source, power, cap);
    }

    /** Acúmulos atuais (0 = não tem). Em Chamas também conta o fogo vanilla como 1 acúmulo. */
    public static int stacks(LivingEntity e, Kind kind) {
        return StatusEngine.stacks(e, kind);
    }

    public static boolean has(LivingEntity e, Kind kind) {
        return stacks(e, kind) > 0;
    }

    /** Remove a condição (Em Chamas também apaga o fogo vanilla). */
    public static void clear(LivingEntity e, Kind kind) {
        StatusEngine.clear(e, kind);
    }

    /** Ticks restantes da condição (0 = não tem; -1 = infinita). */
    public static int ticksLeft(LivingEntity e, Kind kind) {
        MobEffectInstance inst = e.getEffect(effect(kind));
        if (inst == null) return 0;
        return inst.isInfiniteDuration() ? -1 : inst.getDuration();
    }

    /** Poder guardado de quem aplicou a condição (1 se não houver). */
    public static float power(LivingEntity e, Kind kind) {
        return StatusEngine.storedPower(e, kind);
    }

    /** Preso (Congelado ou Petrificado por completo): não anda, não ataca, não conjura. */
    public static boolean incapacitated(LivingEntity e) {
        return StatusEngine.incapacitated(e);
    }

    /** Holder do MobEffect de cada condição. */
    public static Holder<MobEffect> effect(Kind kind) {
        return switch (kind) {
            case WET -> CoreRegistries.SOAKED;
            case BURNING -> CoreRegistries.BURNING;
            case CHILLED -> CoreRegistries.CHILLED;
            case FROZEN -> CoreRegistries.FROZEN;
            case SHOCKED -> CoreRegistries.SHOCKED;
            case BLEEDING -> CoreRegistries.BLEEDING;
            case PETRIFIED -> CoreRegistries.PETRIFIED;
            case CORRUPTED -> CoreRegistries.CORRUPTED;
            case EXPOSED -> CoreRegistries.EXPOSED;
            case STUNNED -> CoreRegistries.STUNNED;
            case ROOTED -> CoreRegistries.ROOTED;
            case SILENCED -> CoreRegistries.SILENCED;
        };
    }

    /**
     * "Parece chefe" para as regras de status: tag {@code c:bosses}, chefe de Provação ({@link BossKit#isTrialBoss}) ou
     * vida máxima ≥ 150. Jogadores NUNCA (mesmo com muita vida). Chefes recebem metade dos acúmulos e da duração;
     * Congelado/Petrificado viram 1 s + Lentidão. Mesma regra de {@link BossKit#isBossLike} (Pilha, Telegraph).
     */
    public static boolean isBossLike(LivingEntity e) {
        return BossKit.isBossLike(e);
    }

    private Statuses() {}
}
