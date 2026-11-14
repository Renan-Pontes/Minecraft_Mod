package com.renanpontes.isekaimahou.feature.legends_h.mha;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_h.LhIds;
import com.renanpontes.isekaimahou.glyph.MasteryMutations;
import com.renanpontes.isekaimahou.feature.legends_h.LhRegistry;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * ONE FOR ALL (servidor, em memória): o Full Cowl ativo de cada criatura e a regra da "porcentagem segura".
 * <p>
 * Porcentagem segura pelo Círculo (o corpo aguenta): C1–3 5%, C4 8%, C5 15%, C6 20%, C7 30%, C8+ 45%; com o Full
 * Cowl ligado, +10% para os golpes do aumento One For All (o corpo está preparado). Acima do seguro, o corpo
 * QUEBRA: dano em si mesmo (nunca mata) e "braço quebrado" (Fraqueza + Cansaço).
 * <p>
 * Full Cowl a {@code p}: velocidade +(8 + 50p)%, dano corpo a corpo +(1 + 10p), ataque +30p%, pulo, queda segura,
 * subir degraus a partir de 20%, resistência a empurrão. Os atributos são TEMPORÁRIOS (não ficam salvos).
 */
public final class FullCowl {
    public static final int GREEN = 0x5CFF7A;
    public static final ResourceLocation MOD_ID = IsekaiMahou.id("lh_full_cowl");

    /** Escada de porcentagens (Amplificar sobe um degrau cada). */
    public static final float[] LADDER = {0.05F, 0.08F, 0.15F, 0.20F, 0.30F, 0.45F, 0.60F, 0.80F, 1.0F};

    private static final class Cowl {
        final LivingEntity e;
        float p;
        long end;

        Cowl(LivingEntity e, float p, long end) {
            this.e = e;
            this.p = p;
            this.end = end;
        }
    }

    private static final Map<UUID, Cowl> ACTIVE = new HashMap<>();

    /** Porcentagem segura pelo Círculo (0..1). */
    public static float safe(int circle) {
        if (circle <= 3) return 0.05F;
        if (circle == 4) return 0.08F;
        if (circle == 5) return 0.15F;
        if (circle == 6) return 0.20F;
        if (circle == 7) return 0.30F;
        return 0.45F;
    }

    /** Degraus extras de teto seguro pelo estágio de evolução do Full Cowl (v1.1): Shoot Style +1, Corpo Forjado +2, Full Bullet +4. */
    public static int stageSteps(int stage) {
        return stage >= 3 ? 4 : Math.max(0, stage);
    }

    /**
     * Porcentagem segura de QUEM USA (v1.1): a do Círculo, subindo degraus pela evolução própria do Full Cowl
     * (maestria do glifo). Mobs e quem não treinou: só o Círculo.
     */
    public static float safeFor(LivingEntity e) {
        int stage = MasteryMutations.stageOf(e, LhIds.FULL_COWL);
        if (stage <= 0) return safe(LhUtil.circle(e));
        return LADDER[Math.min(LADDER.length - 1, safeStep(LhUtil.circle(e)) + stageSteps(stage))];
    }

    /** Degrau seguro de quem usa (Círculo + evolução). */
    public static int safeStepFor(LivingEntity e) {
        float s = safeFor(e);
        for (int i = LADDER.length - 1; i >= 0; i--) if (LADDER[i] <= s + 1.0E-4F) return i;
        return 0;
    }

    /** Índice do degrau seguro na {@link #LADDER}. */
    public static int safeStep(int circle) {
        float s = safe(circle);
        for (int i = LADDER.length - 1; i >= 0; i--) if (LADDER[i] <= s + 1.0E-4F) return i;
        return 0;
    }

    public static boolean active(@Nullable LivingEntity e) {
        return e != null && ACTIVE.containsKey(e.getUUID());
    }

    public static float percent(LivingEntity e) {
        Cowl c = ACTIVE.get(e.getUUID());
        return c == null ? 0F : c.p;
    }

    /** Liga (ou troca a porcentagem de) o Full Cowl por {@code ticks}. */
    public static void activate(LivingEntity e, float p, int ticks) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Cowl c = ACTIVE.get(e.getUUID());
        long end = level.getGameTime() + ticks;
        if (c == null) ACTIVE.put(e.getUUID(), c = new Cowl(e, p, end));
        c.p = p;
        c.end = Math.max(c.end, end);
        apply(e, p);
        e.addEffect(new MobEffectInstance(LhRegistry.FULL_COWL, ticks, Math.round(p * 100) / 10, false, false, true));
    }

    private static void apply(LivingEntity e, float p) {
        set(e, Attributes.MOVEMENT_SPEED, 0.08 + 0.5 * p, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(e, Attributes.ATTACK_DAMAGE, 1 + 10 * p, AttributeModifier.Operation.ADD_VALUE);
        set(e, Attributes.ATTACK_SPEED, 0.3 * p, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(e, Attributes.JUMP_STRENGTH, 0.05 + 0.3 * p, AttributeModifier.Operation.ADD_VALUE);
        set(e, Attributes.SAFE_FALL_DISTANCE, 2 + 8 * p, AttributeModifier.Operation.ADD_VALUE);
        set(e, Attributes.STEP_HEIGHT, p >= 0.2F ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);
        set(e, Attributes.KNOCKBACK_RESISTANCE, 0.4 * p, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void set(LivingEntity e, Holder<Attribute> attr, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(MOD_ID);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(MOD_ID, amount, op));
    }

    private static void strip(LivingEntity e) {
        for (Holder<Attribute> a : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED,
                Attributes.JUMP_STRENGTH, Attributes.SAFE_FALL_DISTANCE, Attributes.STEP_HEIGHT, Attributes.KNOCKBACK_RESISTANCE)) {
            AttributeInstance inst = e.getAttribute(a);
            if (inst != null) inst.removeModifier(MOD_ID);
        }
    }

    /** "Braço quebrado": Fraqueza II + Cansaço por {@code ticks}. */
    public static void breakArm(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, true));
        e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 1, false, true));
        if (e.level() instanceof ServerLevel sl) {
            LhUtil.sound(sl, e.position(), SoundEvents.SKELETON_HURT, 1.0F, 0.6F);
            LhUtil.sound(sl, e.position(), SoundEvents.ITEM_BREAK, 0.8F, 0.6F);
        }
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long tick = server.getTickCount();
        for (Iterator<Cowl> it = ACTIVE.values().iterator(); it.hasNext(); ) {
            Cowl c = it.next();
            LivingEntity e = c.e;
            if (!(e.level() instanceof ServerLevel level) || e.isRemoved() || !e.isAlive() || level.getGameTime() >= c.end
                    || !e.hasEffect(LhRegistry.FULL_COWL)) {
                it.remove();
                strip(e);
                e.removeEffect(LhRegistry.FULL_COWL);
                continue;
            }
            // Faíscas verdes correndo pelo corpo (mais forte = mais faíscas)
            if (tick % 4 == 0) {
                Vec3 p = LhUtil.center(e);
                int n = 1 + Math.round(c.p * 5);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, n, e.getBbWidth() * 0.5, e.getBbHeight() * 0.45,
                        e.getBbWidth() * 0.5, 0.05);
                level.sendParticles(LhUtil.dust(GREEN, 0.7F + c.p), p.x, p.y, p.z, n, e.getBbWidth() * 0.5, e.getBbHeight() * 0.45,
                        e.getBbWidth() * 0.5, 0);
                if (c.p >= 0.45F && tick % 8 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, e.getY() + 0.1, p.z, 3, 0.4, 0, 0.4, 0.2);
            }
            // Acima do seguro, o corpo cobra o preço
            float safe = safeFor(e);
            if (c.p > safe + 1.0E-4F && tick % 40 == 0) {
                LhUtil.strain(e, (c.p - safe) * 0.12F * e.getMaxHealth());
                if (e.getHealth() <= e.getMaxHealth() * 0.25F) {
                    c.p = safe;
                    apply(e, safe);
                    breakArm(e, 200);
                    LhUtil.actionbar(e, Component.translatable("message.isekai_mahou.legends_h.cowl_broke", Math.round(safe * 100))
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    public static void forget(UUID id) {
        Cowl c = ACTIVE.remove(id);
        if (c != null) strip(c.e);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private FullCowl() {}
}
