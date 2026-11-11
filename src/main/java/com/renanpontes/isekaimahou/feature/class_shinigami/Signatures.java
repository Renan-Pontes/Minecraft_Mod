package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * As "assinaturas" dos 5 arquétipos — o que um corte da Shikai (ou um glifo de efeito do arquétipo, ou um pulso da
 * Bankai) faz no alvo, além do dano:
 * <ul>
 *   <li><b>Peso</b>: cada acerto DOBRA o peso (×2, ×4, ×8, ×16, ×32): lentidão crescente, depois preso ao chão e exposto;</li>
 *   <li><b>Pétalas</b>: as pétalas abrem cortes — Sangrando acumulado (dano contínuo, dobra se o alvo se mexe);</li>
 *   <li><b>Gelo</b>: Gelado acumulado (no 5º, o alvo congela — regra do núcleo);</li>
 *   <li><b>Chama Ancestral</b>: Em Chamas acumulado + fogo que não se apaga tão fácil;</li>
 *   <li><b>Pincel Branco</b>: a tinta "apaga o nome": Silenciado (sem magias) + Fraqueza + perde os efeitos bons; um
 *   alvo apagado fica imune ao pincel por 8 s depois que a tinta some (para não prender ninguém para sempre).</li>
 * </ul>
 * Estado em memória: pesos por alvo e imunidade do pincel (limpos periodicamente e ao parar o servidor).
 */
public final class Signatures {
    /** Peso máximo (×32). */
    public static final int MAX_WEIGHT = 5;
    /** Tempo sem novo acerto até o peso começar a cair (ticks). */
    private static final int WEIGHT_WINDOW = 80;
    private static final int BRUSH_IMMUNITY = 160;

    private record Weight(int stacks, long expire) {}

    private static final Map<UUID, Weight> WEIGHTS = new HashMap<>();
    private static final Map<UUID, Long> BRUSH_IMMUNE = new HashMap<>();

    /**
     * Aplica a assinatura do arquétipo.
     * @param strength 1 = acerto normal de glifo; 0,5 = corte da Shikai; aumenta com Amplificar/Bankai
     * @param durationTicks duração base dos estados (já com Estender, se veio de magia)
     */
    public static void apply(@Nullable LivingEntity source, LivingEntity target, Archetype a, float strength, int durationTicks) {
        if (!(target.level() instanceof ServerLevel sl) || !target.isAlive()) return;
        int dur = Math.max(20, durationTicks);
        switch (a) {
            case WEIGHT -> weight(source, target, strength >= 1.4F ? 2 : 1, dur);
            case PETALS -> stack(target, CoreRegistries.BLEEDING, dur, strength >= 1F ? 2 : 1, 6, source);
            case FROST -> stack(target, CoreRegistries.CHILLED, dur, 1, 4, source);
            case FLAME -> {
                stack(target, CoreRegistries.BURNING, dur, 1, strength >= 1F ? 4 : 2, source);
                target.igniteForTicks(Math.max(target.getRemainingFireTicks(), dur));
            }
            case BRUSH -> brush(source, target, Math.round(dur * Math.min(1.5F, 0.5F + strength * 0.5F)));
        }
        ShinigamiFx.signature(sl, a, target.position().add(0, target.getBbHeight() * 0.6, 0), 6 + Math.round(4 * strength), 0.35);
        if (source instanceof ServerPlayer sp && Shinigami.isShinigami(sp)) {
            com.renanpontes.isekaimahou.core.MagicApi.addCounter(sp, Shinigami.SHIKAI_HITS, 1);
        }
    }

    /** Sobe um acúmulo de um efeito do núcleo (renova a duração), até {@code maxAmp}. */
    private static void stack(LivingEntity target, Holder<MobEffect> effect, int dur, int add, int maxAmp, @Nullable LivingEntity source) {
        MobEffectInstance cur = target.getEffect(effect);
        int amp = cur == null ? add - 1 : Math.min(maxAmp, cur.getAmplifier() + add);
        target.addEffect(new MobEffectInstance(effect, Math.max(dur, cur == null ? 0 : Math.min(cur.getDuration(), dur * 2)), amp), source);
    }

    // =====================================================================================
    //  Peso
    // =====================================================================================
    /** Peso atual de um alvo (0..5), já descontando a queda por tempo. */
    public static int weight(LivingEntity target) {
        Weight w = WEIGHTS.get(target.getUUID());
        if (w == null) return 0;
        long now = target.level().getGameTime();
        if (now <= w.expire) return w.stacks;
        int lost = 1 + (int) ((now - w.expire) / 40);
        return Math.max(0, w.stacks - lost);
    }

    private static void weight(@Nullable LivingEntity source, LivingEntity target, int add, int dur) {
        int stacks = Math.min(MAX_WEIGHT, weight(target) + add);
        long now = target.level().getGameTime();
        WEIGHTS.put(target.getUUID(), new Weight(stacks, now + WEIGHT_WINDOW));
        int d = Math.max(60, dur);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, d, Math.min(5, stacks)), source);
        if (stacks >= 3) target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, d, stacks - 2), source);
        if (stacks >= 4) {
            target.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, stacks >= 5 ? d : d / 2, 0), source);
            Vec3 v = target.getDeltaMovement();
            target.setDeltaMovement(v.x * 0.3, Math.min(v.y, -0.6), v.z * 0.3);
            target.hurtMarked = true;
        }
        if (stacks >= MAX_WEIGHT) target.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, d, 0), source);
        ServerLevel sl = (ServerLevel) target.level();
        sl.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, target.getX(), target.getY() + target.getBbHeight() + 0.4, target.getZ(),
                2 + stacks * 2, 0.3, 0.1, 0.3, 0);
        ShinigamiFx.sound(sl, target.position(), stacks >= 4 ? SoundEvents.ANVIL_LAND : SoundEvents.CHAIN_PLACE, 0.5F, 1.4F - 0.12F * stacks);
        if (source instanceof ServerPlayer sp) {
            sp.displayClientMessage(Shinigami.msg("weight", 1 << stacks).withColor(Archetype.WEIGHT.rgb), true);
        }
    }

    // =====================================================================================
    //  Pincel Branco
    // =====================================================================================
    public static boolean brushImmune(LivingEntity target) {
        Long until = BRUSH_IMMUNE.get(target.getUUID());
        return until != null && target.level().getGameTime() < until && !target.hasEffect(CoreRegistries.SILENCED);
    }

    private static void brush(@Nullable LivingEntity source, LivingEntity target, int dur) {
        if (brushImmune(target)) {
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BOOK_PUT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F);
            return;
        }
        // chefes resistem: a tinta dura menos neles
        int d = target.getMaxHealth() > 300 ? Math.max(20, dur / 3) : dur;
        boolean fresh = !target.hasEffect(CoreRegistries.SILENCED);
        target.addEffect(new MobEffectInstance(CoreRegistries.SILENCED, d, 0), source);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, d, 1), source);
        // apaga os efeitos benéficos (o "nome" das habilidades)
        List<Holder<MobEffect>> good = new ArrayList<>();
        for (MobEffectInstance inst : target.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) good.add(inst.getEffect());
        }
        for (Holder<MobEffect> h : good) target.removeEffect(h);
        if (target instanceof Mob mob && fresh && mob.getMaxHealth() <= 300) {
            // esquece quem atacava por um instante
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        BRUSH_IMMUNE.put(target.getUUID(), target.level().getGameTime() + d + BRUSH_IMMUNITY);
        ServerLevel sl = (ServerLevel) target.level();
        Vec3 c = target.position().add(0, target.getBbHeight() + 0.5, 0);
        // um traço de tinta horizontal sobre a cabeça (o nome riscado)
        ShinigamiFx.line(sl, c.add(-0.7, 0, 0), c.add(0.7, 0, 0), ShinigamiFx.INK, 0.12);
        ShinigamiFx.sound(sl, target.position(), SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.6F);
        if (fresh && source instanceof ServerPlayer sp) sp.displayClientMessage(Shinigami.msg("erased", target.getDisplayName()).withColor(0xF2F2F2), true);
    }

    /** Limpeza periódica (chamada a cada 10 s pelo tick do servidor). */
    static void purge(long now) {
        WEIGHTS.values().removeIf(w -> now > w.expire + 40L * MAX_WEIGHT);
        BRUSH_IMMUNE.values().removeIf(t -> now > t);
    }

    static void forget(UUID id) {
        WEIGHTS.remove(id);
        BRUSH_IMMUNE.remove(id);
    }

    static void clear() {
        WEIGHTS.clear();
        BRUSH_IMMUNE.clear();
    }

    private Signatures() {}
}
