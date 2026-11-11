package com.renanpontes.isekaimahou.core.stack;

import java.util.ArrayDeque;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Mede o dano REAL (depois de armadura, resistências e outros mods) que um {@code hurt} aplicou, para o
 * {@code SpellDamageEvent.Post} calcular o overkill certo. {@code SpellContext.damage} abre um quadro antes do
 * {@code hurt} e fecha depois; o {@link LivingDamageEvent.Post} do alvo soma no quadro do topo.
 * Quadros aninhados (um dano dentro de outro) funcionam: só o do topo e do mesmo alvo recebe.
 */
public final class DamageCapture {
    private static final ArrayDeque<Frame> FRAMES = new ArrayDeque<>();

    private static final class Frame {
        final LivingEntity target;
        float applied;
        boolean seen;

        Frame(LivingEntity target) {
            this.target = target;
        }
    }

    /** Começa a medir o dano em {@code target} (sempre feche com {@link #end}, num finally). */
    public static void begin(LivingEntity target) {
        FRAMES.push(new Frame(target));
    }

    /** Fecha o quadro aberto por {@link #begin}: dano aplicado à vida, ou -1 se o evento não veio. */
    public static float end(LivingEntity target) {
        Frame f = FRAMES.peek();
        if (f == null || f.target != target) {
            // Quadro trocado por uma exceção no meio: descarta até achar o certo
            while (f != null && f.target != target) {
                FRAMES.pop();
                f = FRAMES.peek();
            }
            if (f == null) return -1F;
        }
        FRAMES.pop();
        return f.seen ? f.applied : -1F;
    }

    static void onDamagePost(LivingDamageEvent.Post event) {
        if (FRAMES.isEmpty() || event.getEntity().level().isClientSide()) return;
        Frame f = FRAMES.peek();
        if (f.target == event.getEntity()) {
            f.applied += event.getNewDamage();
            f.seen = true;
        }
    }

    static void clear() {
        FRAMES.clear();
    }

    private DamageCapture() {}
}
