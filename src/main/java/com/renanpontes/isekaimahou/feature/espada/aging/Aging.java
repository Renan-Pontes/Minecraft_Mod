package com.renanpontes.isekaimahou.feature.espada.aging;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.espada.EspadaRegistries;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Regras do "Envelhecido". Use {@link #apply} para somar acúmulos (respeita chefes e o limite) e {@link #cure} para
 * tirar tudo. Quando o efeito acaba com mais de 1 acúmulo, ele volta com 1 a menos por {@link #FADE_TICKS}: a velhice
 * vai embora devagar depois da luta (a fila é processada no tick seguinte — não dá para mexer nos efeitos durante o
 * evento de expiração).
 */
public final class Aging {
    public static final int MAX_STACKS = 10;
    /** Duração de cada aplicação (renova a cada golpe de Respira). */
    public static final int APPLY_TICKS = 600;
    /** Quanto dura cada acúmulo ao "desenvelhecer". */
    public static final int FADE_TICKS = 360;

    private static final Map<LivingEntity, Integer> PENDING = new WeakHashMap<>();

    public static Holder<MobEffect> effect() {
        return EspadaRegistries.AGED;
    }

    /** Acúmulos atuais (0 = não está envelhecido). */
    public static int stacks(LivingEntity e) {
        MobEffectInstance inst = e.getEffect(effect());
        return inst == null ? 0 : inst.getAmplifier() + 1;
    }

    /**
     * Soma {@code add} acúmulos (até {@link #MAX_STACKS}; chefes recebem metade e no máx. 3) e renova a duração.
     * Devolve os acúmulos finais.
     */
    public static int apply(LivingEntity target, int add, @Nullable LivingEntity source) {
        if (target.level().isClientSide || add <= 0 || !target.isAlive()) return stacks(target);
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return 0;
        int cap = MAX_STACKS;
        if (BossKit.isBossLike(target)) {
            add = Math.max(1, add / 2);
            cap = 3;
        }
        int now = Math.min(cap, stacks(target) + add);
        if (now <= 0) return 0;
        MobEffectInstance cur = target.getEffect(effect());
        int dur = Math.max(APPLY_TICKS, cur == null ? 0 : cur.getDuration());
        // Troca a instância (o amplificador muda os modificadores de atributo).
        if (cur != null && cur.getAmplifier() != now - 1) target.removeEffect(effect());
        target.addEffect(new MobEffectInstance(effect(), dur, now - 1, false, true, true), source);
        synchronized (PENDING) {
            PENDING.remove(target);
        }
        return now;
    }

    /** Cura total (Restauração de Reishi, comando). */
    public static void cure(LivingEntity target) {
        target.removeEffect(effect());
        synchronized (PENDING) {
            PENDING.remove(target);
        }
    }

    /** A armadura apodrece: {@code amount} de durabilidade em cada peça. */
    public static void rotArmor(LivingEntity e, int amount) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = e.getItemBySlot(slot);
            if (!s.isEmpty() && s.isDamageableItem()) s.hurtAndBreak(amount, e, slot);
        }
    }

    static void onExpired(MobEffectEvent.Expired event) {
        MobEffectInstance inst = event.getEffectInstance();
        if (inst == null || !inst.is(effect())) return;
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || inst.getAmplifier() <= 0) return;
        synchronized (PENDING) {
            PENDING.put(e, inst.getAmplifier() - 1);
        }
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        Map<LivingEntity, Integer> copy;
        synchronized (PENDING) {
            copy = Map.copyOf(PENDING);
            PENDING.clear();
        }
        copy.forEach((e, amp) -> {
            if (e.isAlive() && !e.isRemoved() && !e.hasEffect(effect())) {
                e.addEffect(new MobEffectInstance(effect(), FADE_TICKS, amp, false, true, true));
            }
        });
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(Aging::onExpired);
        NeoForge.EVENT_BUS.addListener(Aging::onServerTick);
    }

    private Aging() {}
}
