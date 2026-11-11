package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.CasinoDomainEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Domínio "Cassino da Fortuna" (forma G, ultimate): abre um cassino de raio 10 (Expandir vale) por 12 s (Estender vale)
 * em volta de você. A cada segundo a sorte é rolada e TODOS os inimigos dentro recebem os efeitos com o poder sorteado
 * (Acerto Garantido). Aceita Leis de Domínio. Custo por pulsos moderado: 1 + √(pulsos), como os outros Domínios.
 * Homenagem ao domínio do Hakari, com regras próprias.
 */
public class CasinoDomainForm extends FormGlyph {
    public static final float BASE_RADIUS = 10F;
    public static final int BASE_DURATION = 240;

    public CasinoDomainForm() {
        super("casino_domain", 70);
        origin(MagicPath.GAMBLER);
        tier(5);
        minCircle(4);
    }

    @Override
    public boolean isUltimate() {
        return true;
    }

    @Override
    public boolean acceptsLaws() {
        return true;
    }

    @Override
    public int baseCastTicks() {
        return 20;
    }

    @Override
    public int baseCooldown() {
        return 200;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(stats.duration(BASE_DURATION), CasinoDomainEntity.PULSE);
    }

    @Override
    public float hintRadius() {
        return BASE_RADIUS;
    }

    @Override
    public int hintDuration() {
        return BASE_DURATION;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        CasinoDomainEntity e = CasinoDomainEntity.open(ctx, caster, s.radius(BASE_RADIUS), s.duration(BASE_DURATION));
        ctx.level().addFreshEntity(e);
        if (caster instanceof ServerPlayer sp) CasinoDomainEntity.countCast(sp);
    }
}
