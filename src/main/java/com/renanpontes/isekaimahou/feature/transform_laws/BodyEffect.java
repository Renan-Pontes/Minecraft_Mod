package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * v1.1 — base dos {@link TransformBody corpos de transformação}: o efeito só age em QUEM CONJURA (os pulsos e flechas
 * do corpo aplicam "os efeitos da magia" nos inimigos — o próprio corpo é ignorado lá), nunca entra duas vezes ao mesmo
 * tempo (proteção contra recursão) e não é "benéfico" (a aura da Transformação não o repete a cada pulso).
 * <p>
 * {@link #start} recebe uma CÓPIA do contexto da transformação (mudar o poder dela não mexe nos golpes/aura da sessão)
 * e a duração já calculada por {@link #bodyTicks}.
 */
public abstract class BodyEffect extends EffectGlyph implements TransformBody {
    private static final Set<UUID> STARTING = ConcurrentHashMap.newKeySet();

    protected BodyEffect(String path, int cost) {
        super(path, cost);
    }

    /** Começa o corpo em {@code self} (servidor). */
    protected abstract void start(SpellContext ctx, LivingEntity self, SpellStats stats, int ticks);

    @Override
    public final void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity self) || self != ctx.caster() || !self.isAlive()) return;
        if (!STARTING.add(self.getUUID())) return;
        try {
            start(ctx.copy(ctx.powerMult()), self, stats, Math.max(20, bodyTicks(stats)));
        } finally {
            STARTING.remove(self.getUUID());
        }
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }

    @Override
    public int hintDuration() {
        return bodyTicks(new SpellStats());
    }

    @Override
    public float hintRadius() {
        return -1F;
    }
}
