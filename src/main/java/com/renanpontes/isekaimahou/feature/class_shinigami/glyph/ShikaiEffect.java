package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;
import com.renanpontes.isekaimahou.feature.class_shinigami.Signatures;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Base dos 5 efeitos de origem dos arquétipos de Shikai (a "assinatura" da lâmina): dano + o estado do arquétipo
 * ({@link Signatures}). Amplificar reforça a assinatura (ex.: o Peso sobe 2 de cada vez); Estender alonga os estados.
 * Cada Shinigami aprende o do seu arquétipo no Mundo Interior (rank II); outros só por Lição.
 */
public abstract class ShikaiEffect extends EffectGlyph {
    private final Archetype archetype;
    private final float damage;
    private final int duration;

    protected ShikaiEffect(String path, int cost, Archetype archetype, float damage, int duration) {
        super(path, cost);
        this.archetype = archetype;
        this.damage = damage;
        this.duration = duration;
        origin(MagicPath.SHINIGAMI);
        element(archetype.element);
        tier(2);
        minCircle(2);
    }

    public Archetype archetype() {
        return archetype;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        if (damage > 0) ctx.damage(le, damage, stats, ctx.caster());
        if (le.isAlive()) Signatures.apply(ctx.caster(), le, archetype, stats.power(), stats.duration(duration));
    }

    @Override
    public int argb() {
        return 0xFF000000 | archetype.rgb;
    }

    @Override
    public float baseDamage() {
        return damage;
    }

    @Override
    public int hintDuration() {
        return duration;
    }

    @Override
    public float hintRadius() {
        return -1F;
    }
}
