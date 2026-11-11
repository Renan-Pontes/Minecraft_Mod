package com.renanpontes.isekaimahou.feature.domain.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.domain.DomainGlyphs;
import com.renanpontes.isekaimahou.feature.domain.entity.DomainDomeEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Domínio Simples (forma normal, defensiva): domo pequeno (raio 3) que acompanha o conjurador por 5 s.
 * Dentro dele o Acerto Garantido de domínios inimigos não vale para você e seus aliados. Efeitos são opcionais:
 * os que ferem golpeiam automaticamente quem entrar (uma vez por segundo por alvo) e os benéficos valem para quem
 * está dentro. Liberado no Círculo de {@link Progression#simpleDomainAllowed}.
 * Exemplo: Domínio Simples → Lâmina de Vento (contra-ataque) ou Domínio Simples → Curar.
 * Preso ao conjurador: não pode ser estágio 2+ de uma magia programável.
 */
public class SimpleDomainForm extends FormGlyph {
    public static final float BASE_RADIUS = 3.0F;
    public static final int BASE_DURATION = 100;

    public SimpleDomainForm() {
        super("simple_domain", 30);
        tier(4);
        minCircle(DomainGlyphs.firstCircle(Progression::simpleDomainAllowed));
    }

    /**
     * Pelo design original o Domínio Simples é DEFESA: não exige efeito. O parser do núcleo ainda pede um efeito em
     * toda forma que não aceita Leis; quando o núcleo consultar este gancho (FormGlyph#requiresEffect), a forma
     * sozinha passa a valer. Sem efeitos, o domo só protege (sem contra-ataque).
     */
    @Override
    public boolean requiresEffect() { return false; }

    @Override
    public int baseCastTicks() { return 4; }

    /** Recarga maior que a duração: não dá para ficar dentro dele o tempo todo. */
    @Override
    public int baseCooldown() { return 160; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats fs = ctx.formStats();
        float radius = fs.radius(BASE_RADIUS);
        int duration = fs.duration(BASE_DURATION);
        Vec3 center = caster.position();

        DomainDomeEntity.replaceOld(level, caster.getUUID(), true);
        DomainDomeEntity dome = DomainDomeEntity.create(ctx, center, radius, duration, DomainDomeEntity.F_SIMPLE, 0);
        level.addFreshEntity(dome);

        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.6F);
        int pts = Math.max(16, (int) (radius * 8));
        for (Element e : dome.elements()) ElementFx.ring(level, center.add(0, 0.1, 0), e, radius, pts);
    }
}
