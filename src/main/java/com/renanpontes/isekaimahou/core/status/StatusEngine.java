package com.renanpontes.isekaimahou.core.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.api.Statuses.Kind;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Regras das condições de status (v0.8, §2.1 do design). Tudo roda no servidor.
 *
 * <p>Dados por entidade (poder e quem aplicou, para o dano contínuo creditar o conjurador; imunidade a Congelado;
 * última posição para o Sangrando) ficam no {@code persistentData} em {@value #TAG}. Mudanças de efeitos no próprio
 * alvo feitas DE DENTRO do tick de um efeito são adiadas para o fim do tick ({@link #defer}), senão o laço de efeitos
 * do Minecraft perde o resto do tick (ConcurrentModificationException engolida).
 */
public final class StatusEngine {
    public static final String TAG = "isekai_mahou_status";
    /** Imunidade a Congelado depois de descongelar (6 s). */
    public static final int FROZEN_IMMUNITY_TICKS = 120;
    /** Congelado padrão (3 s) quando vem de reação ou de Gelado 5. */
    public static final int FREEZE_TICKS = 60;
    /** Imunidade a nova Petrificação depois de sair da pedra (4 s). */
    public static final int STONE_IMMUNITY_TICKS = 80;
    private static final String FROZEN_IMMUNE = "frozen_imm";
    private static final String STONE_IMMUNE = "stone_imm";

    /** >0 enquanto o próprio motor mexe nos efeitos (o evento Applicable deixa passar, Remove não dá imunidade). */
    private static int internal;
    /** true durante um tique de dano contínuo (o recuo é cancelado). */
    private static boolean dotInProgress;
    private static final List<Runnable> DEFERRED = new ArrayList<>();
    /** Conjuração canalizada já ajustada pelo Gelado (início, slot) por jogador. */
    private static final Map<UUID, long[]> CHANNEL = new HashMap<>();

    // =====================================================================================
    //  Registro (chamado por StatusCore.init)
    // =====================================================================================
    static void registerEvents(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener(StatusEngine::onEntityTick);
        bus.addListener(StatusEngine::onPlayerTick);
        bus.addListener(StatusEngine::onServerTick);
        bus.addListener(StatusEngine::onApplicable);
        bus.addListener(StatusEngine::onAdded);
        bus.addListener(EventPriority.LOWEST, StatusEngine::onRemove);
        bus.addListener(StatusEngine::onExpired);
        bus.addListener(StatusEngine::onIncomingDamage);
        bus.addListener(StatusEngine::onHeal);
        bus.addListener(StatusEngine::onKnockBack);
        bus.addListener(StatusEngine::onAttack);
        bus.addListener(StatusEngine::onSpellCast);
        bus.addListener(StatusEngine::onProjectileJoin);
        bus.addListener(StatusEngine::onUseItem);
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) ->
                CHANNEL.remove(e.getEntity().getUUID()));
    }

    static void clear() {
        internal = 0;
        dotInProgress = false;
        DEFERRED.clear();
        CHANNEL.clear();
    }

    /** Roda no fim do tick do servidor (mudanças no próprio alvo pedidas de dentro do tick de um efeito). */
    public static void defer(Runnable r) {
        DEFERRED.add(r);
    }

    // =====================================================================================
    //  API (Statuses delega para cá)
    // =====================================================================================
    public static void apply(LivingEntity target, Kind kind, int stacks, int ticks, @Nullable LivingEntity source, float power) {
        if (target == null || kind == null || stacks <= 0 || target.level().isClientSide || !target.isAlive()) return;
        // Suportes de armadura e afins não recebem efeitos
        if (!target.isAffectedByPotions() || !affects(target, kind)) return;
        if (ticks <= 0) ticks = Statuses.DEFAULT_TICKS;
        power = sanitize(power);
        boolean boss = Statuses.isBossLike(target);
        if (boss) {
            // Chefes: metade dos acúmulos (arredonda para cima) e da duração
            stacks = (stacks + 1) / 2;
            ticks = Math.max(1, ticks / 2);
        }
        switch (kind) {
            case WET -> applyWet(target, ticks, source);
            case BURNING -> applyBurning(target, stacks, ticks, source, power);
            case CHILLED -> applyChilled(target, stacks, ticks, source, power, boss);
            case FROZEN -> freeze(target, ticks, source, power, boss);
            case SHOCKED, BLEEDING, CORRUPTED, EXPOSED -> {
                int n = Math.min(kind.maxStacks(), effectStacks(target, kind) + stacks);
                store(target, kind, source, power);
                put(target, kind, n - 1, ticks, source);
                if (kind == Kind.BLEEDING) markPosition(target);
            }
            case PETRIFIED -> applyPetrify(target, stacks, ticks, source, power, boss);
            case STUNNED, ROOTED, SILENCED -> put(target, kind, 0, ticks, source);
        }
    }

    /**
     * Como {@link #apply}, mas sem passar de {@code cap} acúmulos: no limite só renova a duração (ex.: a condição
     * automática do Crepúsculo não empilha Corrompido até V num feixe).
     */
    public static void applyUpTo(LivingEntity target, Kind kind, int stacks, int ticks, @Nullable LivingEntity source,
                                 float power, int cap) {
        if (target == null || kind == null || target.level().isClientSide) return;
        int cur = effectStacks(target, kind);
        if (cur < cap) {
            apply(target, kind, Math.min(stacks, cap - cur), ticks, source, power);
            return;
        }
        if (cur <= 0 || !target.isAlive() || !target.isAffectedByPotions() || !affects(target, kind)) return;
        int t = ticks <= 0 ? Statuses.DEFAULT_TICKS : ticks;
        if (Statuses.isBossLike(target)) t = Math.max(1, t / 2);
        put(target, kind, cur - 1, t, source);
    }

    /** Acúmulos (amplificador + 1). Em Chamas: o fogo vanilla conta como 1. */
    public static int stacks(LivingEntity e, Kind kind) {
        int s = effectStacks(e, kind);
        if (kind == Kind.BURNING && s == 0 && e.isOnFire()) return 1;
        return s;
    }

    public static int effectStacks(LivingEntity e, Kind kind) {
        MobEffectInstance inst = e.getEffect(Statuses.effect(kind));
        return inst == null ? 0 : Math.min(kind.maxStacks(), inst.getAmplifier() + 1);
    }

    public static void clear(LivingEntity e, Kind kind) {
        if (e == null || e.level().isClientSide) return;
        if (kind == Kind.BURNING && e.isOnFire()) e.clearFire();
        MobEffectInstance had = e.getEffect(Statuses.effect(kind));
        if (had == null) return;
        internal++;
        try {
            e.removeEffect(Statuses.effect(kind));
        } finally {
            internal--;
        }
        forget(e, kind);
        if (kind == Kind.FROZEN) grantFrozenImmunity(e);
        if (kind == Kind.PETRIFIED && had.getAmplifier() >= 2) grantStoneImmunity(e);
        StatusSync.markDirty(e);
    }

    public static boolean incapacitated(LivingEntity e) {
        if (e.hasEffect(CoreRegistries.FROZEN)) return true;
        MobEffectInstance p = e.getEffect(CoreRegistries.PETRIFIED);
        return p != null && p.getAmplifier() >= 2;
    }

    public static float storedPower(LivingEntity e, Kind kind) {
        CompoundTag t = e.getPersistentData().getCompound(TAG).getCompound(kind.id());
        return t.contains("p") ? sanitize(t.getFloat("p")) : 1.0F;
    }

    @Nullable
    public static LivingEntity storedSource(LivingEntity e, Kind kind) {
        CompoundTag t = e.getPersistentData().getCompound(TAG).getCompound(kind.id());
        if (!t.hasUUID("s") || !(e.level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(t.getUUID("s")) instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    /** Pode receber esta condição? (config para jogadores, espectadores). */
    public static boolean affects(LivingEntity target, Kind kind) {
        if (target instanceof Player p) {
            if (p.isSpectator()) return false;
            if (kind.elemental() && !MahouConfig.STATUS_ON_PLAYERS.get()) return false;
        }
        return true;
    }

    // =====================================================================================
    //  Aplicação por tipo
    // =====================================================================================
    private static void applyWet(LivingEntity t, int ticks, @Nullable LivingEntity source) {
        // Água apaga Em Chamas (com um chiado)
        if (effectStacks(t, Kind.BURNING) > 0 || t.isOnFire()) {
            clear(t, Kind.BURNING);
            hiss(t);
        }
        put(t, Kind.WET, 0, ticks, source);
    }

    private static void applyBurning(LivingEntity t, int stacks, int ticks, @Nullable LivingEntity source, float power) {
        if (t.fireImmune() || t.hasEffect(MobEffects.FIRE_RESISTANCE)) return;
        if (t.isInWaterOrBubble()) return;
        if (t.hasEffect(CoreRegistries.SOAKED)) {
            // Molhado: o fogo só evapora a água
            clear(t, Kind.WET);
            hiss(t);
            return;
        }
        int n = Math.min(Kind.BURNING.maxStacks(), effectStacks(t, Kind.BURNING) + stacks);
        store(t, Kind.BURNING, source, power);
        put(t, Kind.BURNING, n - 1, ticks, source);
    }

    private static void applyChilled(LivingEntity t, int stacks, int ticks, @Nullable LivingEntity source, float power, boolean boss) {
        if (t.hasEffect(CoreRegistries.FROZEN)) return;
        // Molhado: o frio acumula em dobro
        int add = t.hasEffect(CoreRegistries.SOAKED) ? stacks * 2 : stacks;
        int n = effectStacks(t, Kind.CHILLED) + add;
        store(t, Kind.CHILLED, source, power);
        if (n >= Kind.CHILLED.maxStacks()) {
            if (!frozenImmune(t)) {
                clear(t, Kind.CHILLED);
                freeze(t, FREEZE_TICKS, source, power, boss);
                return;
            }
            n = Kind.CHILLED.maxStacks() - 1;
        }
        put(t, Kind.CHILLED, n - 1, ticks, source);
    }

    /**
     * Congelado (2–4 s; chefes 1 s + Lentidão e depois Gelado 3). Imune → vira Gelado forte. Já congelado: NÃO renova
     * (senão uma magia que congela a cada pulso prenderia o alvo para sempre).
     */
    private static void freeze(LivingEntity t, int ticks, @Nullable LivingEntity source, float power, boolean boss) {
        if (t.hasEffect(CoreRegistries.FROZEN)) return;
        if (frozenImmune(t)) {
            int n = Math.min(Kind.CHILLED.maxStacks() - 1, Math.max(2, effectStacks(t, Kind.CHILLED) + 2));
            store(t, Kind.CHILLED, source, power);
            put(t, Kind.CHILLED, n - 1, Statuses.DEFAULT_TICKS, source);
            return;
        }
        if (effectStacks(t, Kind.BURNING) > 0) clear(t, Kind.BURNING);
        if (t.isOnFire()) t.clearFire();
        if (boss) {
            put(t, Kind.FROZEN, 0, 20, source);
            putRaw(t, MobEffects.MOVEMENT_SLOWDOWN, 1, 60, source);
            store(t, Kind.CHILLED, source, power);
            put(t, Kind.CHILLED, 2, 80, source);
        } else {
            put(t, Kind.FROZEN, 0, Mth.clamp(ticks, 40, 80), source);
        }
        if (t.level() instanceof ServerLevel sl) {
            sl.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GLASS_PLACE, SoundSource.HOSTILE, 1.0F, 0.6F);
            sl.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.HOSTILE, 0.8F, 0.8F);
            sl.sendParticles(ParticleTypes.SNOWFLAKE, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(),
                    24, t.getBbWidth() * 0.5, t.getBbHeight() * 0.4, t.getBbWidth() * 0.5, 0.02);
        }
    }

    private static void applyPetrify(LivingEntity t, int stacks, int ticks, @Nullable LivingEntity source, float power, boolean boss) {
        MobEffectInstance cur = t.getEffect(CoreRegistries.PETRIFIED);
        if (cur != null && cur.getAmplifier() >= 2) return; // já é pedra
        // Acabou de sair da pedra: 4 s sem enrijecer de novo (um feixe de Cristal não prende para sempre)
        if (stoneImmune(t)) return;
        int n = (cur == null ? 0 : cur.getAmplifier() + 1) + stacks;
        if (n < 3) {
            store(t, Kind.PETRIFIED, source, power);
            put(t, Kind.PETRIFIED, n - 1, ticks, source);
            return;
        }
        // Virou pedra: atordoado 2 s (chefes 1 s + Lentidão), +50% de armadura; depois zera
        int stone = boss ? 20 : 40;
        clear(t, Kind.PETRIFIED);
        store(t, Kind.PETRIFIED, source, power);
        put(t, Kind.PETRIFIED, 2, stone, source);
        put(t, Kind.STUNNED, 0, stone, source);
        if (boss) putRaw(t, MobEffects.MOVEMENT_SLOWDOWN, 1, 60, source);
        if (t.level() instanceof ServerLevel sl) {
            sl.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 1.2F, 0.5F);
            sl.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.HOSTILE, 1.0F, 0.6F);
            sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 30, t.getBbWidth() * 0.5, t.getBbHeight() * 0.4,
                    t.getBbWidth() * 0.5, 0.1);
        }
    }

    // =====================================================================================
    //  Efeitos: escrever exatamente (amplificador, duração ≥ atual)
    // =====================================================================================
    private static void put(LivingEntity t, Kind kind, int amp, int ticks, @Nullable Entity source) {
        Holder<MobEffect> eff = Statuses.effect(kind);
        MobEffectInstance cur = t.getEffect(eff);
        int dur = ticks;
        boolean refreshOnly = false;
        if (cur != null) {
            dur = cur.isInfiniteDuration() ? MobEffectInstance.INFINITE_DURATION : Math.max(cur.getDuration(), ticks);
            if (amp < cur.getAmplifier()) {
                internal++;
                try {
                    t.removeEffect(eff);
                } finally {
                    internal--;
                }
            } else if (amp == cur.getAmplifier()) {
                refreshOnly = true;
            }
        }
        internal++;
        try {
            // Sem as partículas genéricas do vanilla: o cliente desenha o visual de cada condição
            t.addEffect(new MobEffectInstance(eff, dur, amp, false, false, true), source);
        } finally {
            internal--;
        }
        if (refreshOnly) StatusSync.markRefreshed(t, kind, dur);
        else StatusSync.markDirty(t);
    }

    private static void putRaw(LivingEntity t, Holder<MobEffect> eff, int amp, int ticks, @Nullable Entity source) {
        internal++;
        try {
            t.addEffect(new MobEffectInstance(eff, ticks, amp), source);
        } finally {
            internal--;
        }
    }

    private static void store(LivingEntity t, Kind kind, @Nullable LivingEntity source, float power) {
        CompoundTag root = t.getPersistentData().getCompound(TAG);
        CompoundTag k = root.getCompound(kind.id());
        // O mais forte manda enquanto a condição dura (um golpe fraco não "apaga" o poder de quem aplicou antes)
        float prev = k.contains("p") && effectStacks(t, kind) > 0 ? k.getFloat("p") : 0F;
        if (power >= prev || !k.hasUUID("s")) {
            k.putFloat("p", power);
            if (source != null) k.putUUID("s", source.getUUID());
            else k.remove("s");
        }
        root.put(kind.id(), k);
        t.getPersistentData().put(TAG, root);
    }

    /** Apaga o poder/quem aplicou guardados de uma condição que acabou (não vaza para a próxima aplicação). */
    private static void forget(LivingEntity e, Kind kind) {
        CompoundTag pd = e.getPersistentData();
        if (!pd.contains(TAG)) return;
        CompoundTag root = pd.getCompound(TAG);
        if (!root.contains(kind.id())) return;
        root.remove(kind.id());
        pd.put(TAG, root);
    }

    private static float sanitize(float p) {
        if (Float.isNaN(p) || Float.isInfinite(p)) return 1.0F;
        return Mth.clamp(p, 0.25F, 6.0F);
    }

    // =====================================================================================
    //  Imunidade a Congelado
    // =====================================================================================
    public static boolean frozenImmune(LivingEntity e) {
        CompoundTag root = e.getPersistentData().getCompound(TAG);
        return root.contains(FROZEN_IMMUNE) && e.level().getGameTime() < root.getLong(FROZEN_IMMUNE);
    }

    private static void grantFrozenImmunity(LivingEntity e) {
        CompoundTag root = e.getPersistentData().getCompound(TAG);
        root.putLong(FROZEN_IMMUNE, e.level().getGameTime() + FROZEN_IMMUNITY_TICKS);
        e.getPersistentData().put(TAG, root);
    }

    /** Depois de ser pedra (Petrificado), não acumula Petrificação por {@link #STONE_IMMUNITY_TICKS}. */
    public static boolean stoneImmune(LivingEntity e) {
        CompoundTag root = e.getPersistentData().getCompound(TAG);
        return root.contains(STONE_IMMUNE) && e.level().getGameTime() < root.getLong(STONE_IMMUNE);
    }

    private static void grantStoneImmunity(LivingEntity e) {
        CompoundTag root = e.getPersistentData().getCompound(TAG);
        root.putLong(STONE_IMMUNE, e.level().getGameTime() + STONE_IMMUNITY_TICKS);
        e.getPersistentData().put(TAG, root);
    }

    // =====================================================================================
    //  Ticks dos efeitos (chamados pelas classes de efeito, só no servidor)
    // =====================================================================================
    public static void tickBurning(LivingEntity e, int stacks) {
        if (e.fireImmune() || e.hasEffect(MobEffects.FIRE_RESISTANCE) || e.isInWaterRainOrBubble()) {
            // Água/chuva apaga (fora do laço de efeitos)
            defer(() -> {
                if (e.isAlive()) {
                    clear(e, Kind.BURNING);
                    if (e.isInWaterRainOrBubble()) hiss(e);
                }
            });
            return;
        }
        float dmg = (0.5F + 0.5F * stacks) * storedPower(e, Kind.BURNING);
        dot(e, dmg, storedSource(e, Kind.BURNING));
    }

    public static void tickChilled(LivingEntity e, int stacks) {
        if (!e.canFreeze()) return;
        // Geada parcial do vanilla (vinheta azul na tela do jogador), sem chegar ao congelamento que causa dano
        int want = Math.min(e.getTicksRequiredToFreeze() - 1, stacks * 10);
        if (e.getTicksFrozen() < want) e.setTicksFrozen(want);
    }

    public static void tickShocked(LivingEntity e, int stacks) {
        LivingEntity src = storedSource(e, Kind.SHOCKED);
        float power = storedPower(e, Kind.SHOCKED);
        ServerLevel sl = (ServerLevel) e.level();
        // Micro-atordoamento: 12% por acúmulo a cada segundo
        if (e.getRandom().nextFloat() < 0.12F * stacks) {
            defer(() -> {
                if (!e.isAlive()) return;
                apply(e, Kind.STUNNED, 1, 10, src, 1.0F);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                        14, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, e.getBbWidth() * 0.4, 0.15);
                sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BEE_STING, SoundSource.HOSTILE, 0.5F, 1.8F);
            });
        }
        // Jogador perde 2 de mana por segundo por acúmulo
        if (e instanceof ServerPlayer sp && !sp.getAbilities().instabuild) {
            MagicData d = MagicApi.get(sp);
            d.addMana(-2.0F * stacks);
        }
        // Molhado + Eletrizado: arqueia para outros alvos Molhados a ≤4 blocos
        if (e.hasEffect(CoreRegistries.SOAKED)) {
            int arcs = 0;
            for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(4.0),
                    o -> o != e && o.isAlive() && !o.isSpectator() && o.hasEffect(CoreRegistries.SOAKED) && !friendly(src, o))) {
                if (o.distanceToSqr(e) > 16.0) continue;
                if (arcs++ >= 3) break;
                dot(o, 0.5F * stacks * power, src);
                sparkLine(sl, center(e), center(o));
                if (!o.hasEffect(CoreRegistries.SHOCKED)) defer(() -> {
                    if (o.isAlive()) apply(o, Kind.SHOCKED, 1, Statuses.DEFAULT_TICKS, src, power);
                });
            }
            if (arcs > 0) sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.35F, 1.9F);
        }
    }

    public static void tickBleeding(LivingEntity e, int stacks) {
        CompoundTag root = e.getPersistentData().getCompound(TAG);
        boolean moved = false;
        if (root.contains("bx")) {
            double dx = e.getX() - root.getDouble("bx"), dz = e.getZ() - root.getDouble("bz"), dy = e.getY() - root.getDouble("by");
            moved = dx * dx + dy * dy + dz * dz > 0.09;
        }
        markPosition(e);
        float dmg = 0.4F * stacks * (moved ? 2.0F : 1.0F) * storedPower(e, Kind.BLEEDING);
        dot(e, dmg, storedSource(e, Kind.BLEEDING));
    }

    /**
     * Congelado/Petrificado: para a navegação e o movimento horizontal (a gravidade continua) e solta o item em uso
     * (arco/besta/tridente não carregam, comida não termina). A IA e o alvo continuam (sem "perder e reachar" o alvo
     * a cada tick, que faria criaturas rugirem/falarem sem parar); os disparos vanilla saem bloqueados em
     * {@link #onProjectileJoin}.
     */
    public static void holdInPlace(LivingEntity e) {
        if (e instanceof Mob m) {
            m.getNavigation().stop();
            m.setSpeed(0F);
        }
        if (e.isUsingItem()) e.stopUsingItem();
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(0, Math.min(v.y, 0), 0);
    }

    /** Molhado apaga Em Chamas (chamado pelo tick do Molhado: fora do laço). */
    public static void extinguishBurning(LivingEntity e) {
        if (!e.hasEffect(CoreRegistries.BURNING)) return;
        defer(() -> {
            if (e.isAlive() && e.hasEffect(CoreRegistries.SOAKED)) {
                clear(e, Kind.BURNING);
                hiss(e);
            }
        });
    }

    private static void markPosition(LivingEntity e) {
        CompoundTag root = e.getPersistentData().getCompound(TAG);
        root.putDouble("bx", e.getX());
        root.putDouble("by", e.getY());
        root.putDouble("bz", e.getZ());
        e.getPersistentData().put(TAG, root);
    }

    // =====================================================================================
    //  Dano contínuo
    // =====================================================================================
    /** Dano mágico de condição (tipo "spell"), creditando quem aplicou; sem recuo e sem gastar a invulnerabilidade. */
    public static void dot(LivingEntity e, float amount, @Nullable LivingEntity src) {
        if (amount <= 0F || !Float.isFinite(amount) || !e.isAlive() || !(e.level() instanceof ServerLevel sl)) return;
        if (src == e) src = null; // nunca "atacado por si mesmo" (a IA de vingança miraria a própria criatura)
        DamageSource ds = new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CoreRegistries.SPELL_DAMAGE), null, src);
        int prevInvul = e.invulnerableTime;
        e.invulnerableTime = 0;
        dotInProgress = true;
        try {
            e.hurt(ds, amount);
        } finally {
            dotInProgress = false;
            if (e.isAlive()) e.invulnerableTime = prevInvul;
        }
    }

    // =====================================================================================
    //  Aliados (para arcos e espalhar fogo sem um contexto de magia)
    // =====================================================================================
    public static boolean friendly(@Nullable LivingEntity src, Entity e) {
        if (src == null || e == null) return false;
        if (e == src || e.getUUID().equals(src.getUUID())) return true;
        if (e instanceof OwnableEntity own && src.getUUID().equals(own.getOwnerUUID())) return true;
        if (src.isAlliedTo(e)) return true;
        return src instanceof Player && e instanceof Player && src.getServer() != null && !src.getServer().isPvpAllowed();
    }

    // =====================================================================================
    //  Ambiente (a cada 20 ticks por entidade) e jogadores
    // =====================================================================================
    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity e) || e.level().isClientSide) return;
        if ((e.tickCount + e.getId()) % 20 != 0 || !e.isAlive()) return;
        if (e instanceof Player p && (p.isSpectator() || !MahouConfig.STATUS_ON_PLAYERS.get())) return;
        if (e.isInWaterRainOrBubble()) {
            // Exposição acumula: cada segundo na água soma tempo de Molhado (mergulhado > na água > na chuva).
            // Uns 10 s nadando ≈ 1 min molhado; 20 s ≈ 2 min (máximo). Só chuva chega a 1 min.
            boolean water = e.isInWaterOrBubble();
            int gain = e.isUnderWater() ? 240 : water ? 140 : 50;
            int cap = water ? WET_MAX_WATER : WET_MAX_RAIN;
            MobEffectInstance cur = e.getEffect(Statuses.effect(Kind.WET));
            int have = cur == null || cur.isInfiniteDuration() ? 0 : cur.getDuration();
            if (have < cap) apply(e, Kind.WET, 1, Math.min(cap, Math.max(200, have + gain)), null, 1.0F);
        } else if (e.isInPowderSnow || e.wasInPowderSnow) {
            // Neve em pó gela (até 3 por conta própria: não prende ninguém para sempre numa armadilha de neve)
            if (effectStacks(e, Kind.CHILLED) < 3) apply(e, Kind.CHILLED, 1, Statuses.DEFAULT_TICKS, null, 1.0F);
        }
        // Lava e blocos de fogo acendem o vanilla primeiro: só confere quem já está pegando fogo
        if (e.isOnFire() && (e.isInLava() || inFireBlock(e)) && effectStacks(e, Kind.BURNING) < 1) {
            apply(e, Kind.BURNING, 1, 60, null, 1.0F);
        }
    }

    /** Molhado do ambiente: máximo de 2 min na água e 1 min só de chuva (ticks). */
    private static final int WET_MAX_WATER = 2400, WET_MAX_RAIN = 1200;

    private static boolean inFireBlock(LivingEntity e) {
        return e.level().getBlockStatesIfLoaded(e.getBoundingBox().deflate(1.0E-3))
                .anyMatch(s -> s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) && s.getValue(net.minecraft.world.level.block.CampfireBlock.LIT));
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        MagicData d = MagicApi.get(sp);
        UUID id = sp.getUUID();
        if (d.channelSlot < 0 || d.channelRecipe == null) {
            CHANNEL.remove(id);
            return;
        }
        // Preso no gelo/pedra: a conjuração em andamento é interrompida
        if (incapacitated(sp)) {
            CHANNEL.remove(id);
            SpellCaster.interrupt(sp);
            return;
        }
        // Gelado: +15% de tempo de conjuração por acúmulo (ajusta UMA vez por início de canalização)
        long[] seen = CHANNEL.get(id);
        if (seen != null && seen[0] == d.channelStart && seen[1] == d.channelSlot) return;
        boolean fresh = seen == null || seen[1] != d.channelSlot;
        CHANNEL.put(id, new long[]{d.channelStart, d.channelSlot});
        int chill = effectStacks(sp, Kind.CHILLED);
        if (chill <= 0 || d.channelTicks <= 0) return;
        int slowed = Math.round(d.channelTicks * (1.0F + 0.15F * chill));
        if (slowed == d.channelTicks) return;
        d.channelTicks = slowed;
        // Só no começo de uma conjuração nova a barra do cliente recomeça (ciclos de canalização contínua não)
        if (fresh) PacketDistributor.sendToPlayer(sp, new CorePayloads.CastState(d.channelSlot, slowed,
                d.channelRecipe.chant(), (byte) 0));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (DEFERRED.isEmpty()) return;
        List<Runnable> batch = new ArrayList<>(DEFERRED);
        DEFERRED.clear();
        for (Runnable r : batch) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                com.renanpontes.isekaimahou.IsekaiMahou.LOGGER.error("[status] tarefa adiada falhou", ex);
            }
        }
    }

    // =====================================================================================
    //  Eventos de efeitos (inclusive efeitos aplicados direto por outros módulos)
    // =====================================================================================
    private static void onApplicable(MobEffectEvent.Applicable event) {
        LivingEntity t = event.getEntity();
        if (internal > 0 || t.level().isClientSide) return;
        Kind kind = Kind.of(event.getEffectInstance().getEffect());
        if (kind == null) return;
        if (!affects(t, kind)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        // Congelado direto (addEffect de um módulo) em quem acabou de descongelar: vira Gelado forte. Chefes ficam de fora
        // aqui: eles têm a própria tenacidade no addEffect (duração reduzida, janela de imunidade a controle)
        if (kind == Kind.FROZEN && !Statuses.isBossLike(t) && frozenImmune(t)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            int ticks = event.getEffectInstance().getDuration();
            LivingEntity src = event.getEffectSource() instanceof LivingEntity le ? le : null;
            defer(() -> {
                if (t.isAlive()) apply(t, Kind.FROZEN, 1, ticks <= 0 ? FREEZE_TICKS : ticks, src, 1.0F);
            });
        } else if (kind == Kind.BURNING && (t.fireImmune() || t.hasEffect(MobEffects.FIRE_RESISTANCE))) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    private static void onAdded(MobEffectEvent.Added event) {
        LivingEntity t = event.getEntity();
        if (t.level().isClientSide) return;
        MobEffectInstance inst = event.getEffectInstance();
        Kind kind = Kind.of(inst.getEffect());
        if (kind == null) return;
        if (internal == 0) StatusSync.markDirty(t);
        boolean stuck = kind == Kind.FROZEN || (kind == Kind.PETRIFIED && inst.getAmplifier() >= 2);
        if (stuck && t instanceof ServerPlayer sp) {
            defer(() -> {
                if (sp.isAlive() && incapacitated(sp)) SpellCaster.interrupt(sp);
            });
        }
        // Molhado aplicado direto por um módulo também apaga o fogo
        if (kind == Kind.WET && internal == 0 && t.hasEffect(CoreRegistries.BURNING)) {
            defer(() -> {
                if (t.isAlive()) {
                    clear(t, Kind.BURNING);
                    hiss(t);
                }
            });
        }
    }

    private static void onRemove(MobEffectEvent.Remove event) {
        LivingEntity t = event.getEntity();
        if (t.level().isClientSide || event.isCanceled() || event.getEffect() == null) return;
        Kind kind = Kind.of(event.getEffect());
        if (kind == null) return;
        StatusSync.markDirty(t);
        if (internal == 0) {
            // Removido por fora (leite, totem, glifo de purificar, módulo): esquece o poder de quem aplicou
            forget(t, kind);
            if (kind == Kind.FROZEN) grantFrozenImmunity(t);
            MobEffectInstance inst = event.getEffectInstance();
            if (kind == Kind.PETRIFIED && inst != null && inst.getAmplifier() >= 2) grantStoneImmunity(t);
        }
    }

    private static void onExpired(MobEffectEvent.Expired event) {
        LivingEntity t = event.getEntity();
        if (t.level().isClientSide || event.getEffectInstance() == null) return;
        Kind kind = Kind.of(event.getEffectInstance().getEffect());
        if (kind == null) return;
        StatusSync.markDirty(t);
        forget(t, kind);
        if (kind == Kind.FROZEN) grantFrozenImmunity(t);
        if (kind == Kind.PETRIFIED && event.getEffectInstance().getAmplifier() >= 2) grantStoneImmunity(t);
    }

    /**
     * Preso no gelo/pedra não atira: projéteis VANILLA (flechas, bolas de fogo de Ghast/Blaze, poções de Bruxa, tridentes
     * de Afogado, bolas de neve...) de um dono incapacitado não entram no mundo. Projéteis de mods/magias ficam de fora
     * (magias já são barradas no SpellCastEvent; criaturas do mod podem guardar referência ao projétil). Chefes também.
     */
    private static void onProjectileJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj)) return;
        if (!(proj.getOwner() instanceof LivingEntity owner) || !incapacitated(owner)) return;
        if (owner instanceof Player p ? p.isCreative() : Statuses.isBossLike(owner)) return;
        if (!"minecraft".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(proj.getType()).getNamespace())) return;
        event.setCanceled(true);
    }

    // =====================================================================================
    //  Dano, cura, recuo, ataques e conjuração
    // =====================================================================================
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity t = event.getEntity();
        if (t.level().isClientSide) return;
        DamageSource s = event.getSource();
        Entity cause = s.getEntity();
        boolean melee = cause instanceof LivingEntity && s.getDirectEntity() == cause && !s.is(DamageTypeTags.IS_PROJECTILE)
                && (s.is(DamageTypes.PLAYER_ATTACK) || s.is(DamageTypes.MOB_ATTACK) || s.is(DamageTypes.MOB_ATTACK_NO_AGGRO));
        // Preso no gelo/pedra não golpeia
        if (melee && incapacitated((LivingEntity) cause)) {
            event.setCanceled(true);
            return;
        }
        // Fogo comum (lava, fogo, flechas em chamas): +8% por acúmulo de Em Chamas; −50% se Molhado
        if (s.is(DamageTypeTags.IS_FIRE)) {
            int burning = effectStacks(t, Kind.BURNING);
            if (burning > 0) event.setAmount(event.getAmount() * (1.0F + 0.08F * burning));
            if (t.hasEffect(CoreRegistries.SOAKED)) event.setAmount(event.getAmount() * 0.5F);
        }
        // Estilhaçar: golpe físico corpo a corpo em alvo Congelado/Petrificado
        if (melee && incapacitated(t) && cause != t) {
            event.setAmount(event.getAmount() * 1.75F);
            LivingEntity attacker = (LivingEntity) cause;
            defer(() -> ElementReactions.shatterByMelee(t, attacker));
        }
    }

    private static void onHeal(LivingHealEvent event) {
        LivingEntity t = event.getEntity();
        if (t.level().isClientSide || event.getAmount() <= 0F) return;
        if (t.hasEffect(CoreRegistries.BURNING)) event.setAmount(event.getAmount() * 0.7F);
    }

    private static void onKnockBack(LivingKnockBackEvent event) {
        if (dotInProgress) event.setCanceled(true);
    }

    /** Preso no gelo/pedra não golpeia (nos dois lados: o cliente do próprio jogador conhece os efeitos dele). */
    private static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (!p.isCreative() && incapacitated(p)) event.setCanceled(true);
    }

    /** ...nem usa itens (arco, comida, pérola): sem gastar munição. */
    private static void onUseItem(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        Player p = event.getEntity();
        if (p.isCreative() || !incapacitated(p)) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
    }

    private static void onSpellCast(SpellCastEvent.Pre event) {
        LivingEntity c = event.getCaster();
        if (c == null || c.level().isClientSide || !incapacitated(c)) return;
        event.setCanceled(true);
        if (c instanceof ServerPlayer sp) {
            boolean ice = sp.hasEffect(CoreRegistries.FROZEN);
            sp.displayClientMessage(Component.translatable(ice ? "message.isekai_mahou.status.frozen_no_cast"
                    : "message.isekai_mahou.status.petrified_no_cast").withStyle(ChatFormatting.AQUA), true);
        }
    }

    // =====================================================================================
    //  Visual/som
    // =====================================================================================
    public static void hiss(LivingEntity e) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                8, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, e.getBbWidth() * 0.4, 0.02);
        sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.5F, 1.4F);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    public static void sparkLine(ServerLevel sl, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int n = Mth.clamp((int) (d.length() * 3), 3, 24);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double jitter = (i == 0 || i == n) ? 0 : 0.18;
            // release 1.0 (perf): entra no lote do tick (um pacote por jogador, não um por faísca)
            com.renanpontes.isekaimahou.core.fx.FxBatch.emit(sl, ParticleTypes.ELECTRIC_SPARK,
                    a.x + d.x * t + (sl.random.nextDouble() - 0.5) * jitter, a.y + d.y * t + (sl.random.nextDouble() - 0.5) * jitter,
                    a.z + d.z * t + (sl.random.nextDouble() - 0.5) * jitter, 1, 0, 0, 0, 0, false);
        }
    }

    private StatusEngine() {}
}
