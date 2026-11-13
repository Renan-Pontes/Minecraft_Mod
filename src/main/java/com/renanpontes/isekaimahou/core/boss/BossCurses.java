package com.renanpontes.isekaimahou.core.boss;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Maldições ASSINATURA dos chefes (v1.3): cada luta tem um efeito que só ela causa. São efeitos de status normais
 * (sincronizados pelo vanilla, salvos no jogador, somem com leite), então o cliente só precisa olhar se o jogador local
 * os tem para desenhar a tela ({@code client/boss/BossScreenFx}). Servidor e cliente.
 * <ul>
 *   <li>{@link #SCORCHED} Calor Escaldante (Ignis): a armadura DERRETE (durabilidade) e o ar tremula;</li>
 *   <li>{@link #DROWNING} Afogamento (Maré): o ar acaba mesmo fora d'água — quebre a bolha / afaste-se;</li>
 *   <li>{@link #WHISPERS} Sussurros do Abismo (Soberano): visão escura que pisca invertida, vozes;</li>
 *   <li>{@link #TIME_SLOW} Tempo Lento (Arauto): tudo lento, menos ele;</li>
 *   <li>{@link #SOUL_DRAIN} Alma Drenada (Lich): −2 de vida MÁXIMA por nível até o filactério quebrar;</li>
 *   <li>{@link #FROG} Maldição do Sapo (Bruxa): encolhe, quase sem dano, pula muito;</li>
 *   <li>{@link #INVERTED} Mente Invertida (Bruxa): controles trocados;</li>
 *   <li>{@link #CHAINED} Pacto Acorrentado (Patrono): ícone das correntes que prendem jogadores entre si.</li>
 * </ul>
 */
public final class BossCurses {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> SCORCHED = EFFECTS.register("boss_scorched", Scorched::new);
    public static final DeferredHolder<MobEffect, MobEffect> DROWNING = EFFECTS.register("boss_drowning", Drowning::new);
    public static final DeferredHolder<MobEffect, MobEffect> WHISPERS = EFFECTS.register("boss_whispers",
            () -> new Plain(MobEffectCategory.HARMFUL, 0x3A1466));
    public static final DeferredHolder<MobEffect, MobEffect> TIME_SLOW = EFFECTS.register("boss_time_slow",
            () -> new Plain(MobEffectCategory.HARMFUL, 0xC9A96E)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("boss_time_slow_speed"), -0.32,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("boss_time_slow_attack"), -0.25,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.GRAVITY, IsekaiMahou.id("boss_time_slow_gravity"), -0.3,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("boss_time_slow_jump"), -0.12,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect, MobEffect> SOUL_DRAIN = EFFECTS.register("boss_soul_drain",
            () -> new Plain(MobEffectCategory.HARMFUL, 0x7FE8D8)
                    .addAttributeModifier(Attributes.MAX_HEALTH, IsekaiMahou.id("boss_soul_drain"), -2.0,
                            AttributeModifier.Operation.ADD_VALUE));
    public static final DeferredHolder<MobEffect, MobEffect> FROG = EFFECTS.register("boss_frog", Frog::new);
    public static final DeferredHolder<MobEffect, MobEffect> INVERTED = EFFECTS.register("boss_inverted",
            () -> new Plain(MobEffectCategory.HARMFUL, 0xB040E0));
    public static final DeferredHolder<MobEffect, MobEffect> CHAINED = EFFECTS.register("boss_chained",
            () -> new Plain(MobEffectCategory.HARMFUL, 0x8C1F1F)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("boss_chained_speed"), -0.1,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Teto do nível da Alma Drenada (nível 4 = −10 de vida máxima). */
    public static final int SOUL_DRAIN_MAX_AMP = 4;

    static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    // =====================================================================================
    //  Ajudantes (servidor)
    // =====================================================================================
    /** Aplica (ou renova) a maldição; nunca encurta uma que já está mais longa/forte. */
    public static void apply(LivingEntity e, Holder<MobEffect> effect, int ticks, int amp) {
        if (e == null || !e.isAlive() || e.level().isClientSide()) return;
        MobEffectInstance cur = e.getEffect(effect);
        if (cur != null && cur.getAmplifier() > amp) amp = cur.getAmplifier();
        if (cur != null && cur.getAmplifier() == amp && cur.getDuration() > ticks) return;
        e.addEffect(new MobEffectInstance(effect, ticks, amp, false, true, true));
    }

    /** Sobe um nível (até {@code maxAmp}) e renova a duração — para efeitos que acumulam (Alma Drenada). */
    public static int stack(LivingEntity e, Holder<MobEffect> effect, int ticks, int maxAmp) {
        if (e == null || !e.isAlive() || e.level().isClientSide()) return -1;
        MobEffectInstance cur = e.getEffect(effect);
        int amp = cur == null ? 0 : Math.min(maxAmp, cur.getAmplifier() + 1);
        e.removeEffect(effect);
        e.addEffect(new MobEffectInstance(effect, ticks, amp, false, true, true));
        return amp;
    }

    public static boolean has(@Nullable LivingEntity e, Holder<MobEffect> effect) {
        return e != null && e.hasEffect(effect);
    }

    public static void cleanse(LivingEntity e, Holder<MobEffect> effect) {
        if (e != null && e.hasEffect(effect)) e.removeEffect(effect);
    }

    // =====================================================================================
    //  Efeitos
    // =====================================================================================
    /** Efeito sem lógica própria (o visual é do cliente; atributos via addAttributeModifier). */
    static final class Plain extends MobEffect {
        Plain(MobEffectCategory c, int color) {
            super(c, color);
        }
    }

    /** Calor Escaldante: a cada segundo cada peça de armadura perde 1 + nível de durabilidade (Resistência ao Fogo: metade). */
    static final class Scorched extends MobEffect {
        Scorched() {
            super(MobEffectCategory.HARMFUL, 0xFF6A1A);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amp) {
            return duration % 20 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amp) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            boolean resist = e.hasEffect(MobEffects.FIRE_RESISTANCE);
            if (resist && e.getRandom().nextBoolean()) return true;
            boolean melted = false;
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack s = e.getItemBySlot(slot);
                if (s.isEmpty() || !s.isDamageableItem()) continue;
                s.hurtAndBreak(1 + amp, e, slot);
                melted = true;
            }
            // Fumacinha subindo da armadura: o jogador VÊ que ela está derretendo
            if (melted) {
                sl.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 4,
                        e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, e.getBbWidth() * 0.4, 0.01);
                if (e.getRandom().nextInt(3) == 0) {
                    sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 0.35F, 1.6F);
                }
            }
            return true;
        }
    }

    /** Afogamento: o ar cai ~4/tick mesmo em terra; sem ar, 2 de dano de afogamento por segundo. */
    static final class Drowning extends MobEffect {
        Drowning() {
            super(MobEffectCategory.HARMFUL, 0x2E6FD8);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amp) {
            return true;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amp) {
            if (e.level().isClientSide() || e.canBreatheUnderwater() || e.hasEffect(MobEffects.WATER_BREATHING)
                    || e.hasEffect(MobEffects.CONDUIT_POWER)) return true;
            // Em terra o vanilla devolve +4 por tick: o efeito tira mais que isso
            int drain = e.isEyeInFluid(FluidTags.WATER) ? 1 + amp : 8 + amp * 2;
            int air = e.getAirSupply() - drain;
            if (air <= -20) {
                air = 0;
                e.hurt(e.damageSources().drown(), 2.0F + amp);
            }
            e.setAirSupply(Math.max(-20, air));
            if (e.tickCount % 6 == 0 && e.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.BUBBLE_POP, e.getX(), e.getEyeY(), e.getZ(), 3, 0.25, 0.2, 0.25, 0.02);
            }
            return true;
        }
    }

    /** Maldição do Sapo: tamanho ×0,4, dano ×0,25, pulo alto, sem dano de queda pequena; coaxa de vez em quando. */
    static final class Frog extends MobEffect {
        Frog() {
            super(MobEffectCategory.HARMFUL, 0x5DBB3A);
            addAttributeModifier(Attributes.SCALE, IsekaiMahou.id("boss_frog_scale"), -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("boss_frog_damage"), -0.75,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("boss_frog_jump"), 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id("boss_frog_fall"), 4.0, AttributeModifier.Operation.ADD_VALUE);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amp) {
            return duration % 30 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amp) {
            if (e.level() instanceof ServerLevel sl && e.getRandom().nextInt(2) == 0) {
                sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.FROG_AMBIENT, SoundSource.HOSTILE, 0.9F,
                        0.9F + e.getRandom().nextFloat() * 0.4F);
                sl.sendParticles(ParticleTypes.ITEM_SLIME, e.getX(), e.getY() + 0.3, e.getZ(), 5, 0.2, 0.1, 0.2, 0.05);
            }
            return true;
        }
    }

    private BossCurses() {}
}
