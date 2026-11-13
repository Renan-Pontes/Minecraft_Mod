package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellConjurerEntity;
import com.renanpontes.isekaimahou.feature.forms_b.summon.SummonForge;
import com.renanpontes.isekaimahou.feature.forms_b.summon.SummonOrder;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Invocação: chama um espírito (1+Dividir) que te segue por 600×duração ticks e lança os efeitos
 * no inimigo mais próximo a cada 2 segundos. Conjurar de novo substitui os espíritos antigos.
 * <p>
 * v1.0 (onda 4) — Forja de Invocações: se a receita tem o glifo de um CORPO (Invocar Golem, Salamandra, Sombra
 * Viva...), nasce o corpo em vez do espírito e o resto da receita manda nele ({@link SummonForge}). Os glifos de
 * ordem (Guardar, Caçar, Ficar) valem para o corpo e para o espírito. Sem corpo e sem ordem: igual a sempre.
 */
public class ConjureForm extends FormGlyph {
    public ConjureForm() {
        super("conjure", 25);
        element(Element.ARCANE);
        tier(4);
        minCircle(3);
    }

    @Override
    public int baseCastTicks() { return 30; }

    @Override
    public int baseCooldown() { return 300; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        // Receita com corpo: quem nasce é a criatura (o espírito fica para as receitas sem corpo)
        if (SummonForge.summonBodies(ctx)) {
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON,
                    SoundSource.PLAYERS, 0.8F, 1.1F);
            return;
        }
        SummonOrder order = SummonForge.orderOf(s);
        LivingEntity mark = null;
        if (order == SummonOrder.HUNT) {
            LivingEntity looked = SummonForge.lookTarget(caster, SummonForge.MARK_RANGE);
            if (looked != null && SummonForge.canMark(caster, looked)) mark = looked;
            else SummonForge.awaitMark(caster.getUUID());
        }
        // Todos os espíritos antigos deste dono no mundo (não só os de perto: um espírito de guarda pode estar longe)
        for (SpellConjurerEntity old : level.getEntities(FormsBRegistry.SPELL_CONJURER.get(),
                o -> caster.getUUID().equals(o.getOwnerUUID()))) {
            old.fizzle();
        }
        int n = s.count();
        for (int i = 0; i < n; i++) {
            SpellConjurerEntity spirit = new SpellConjurerEntity(FormsBRegistry.SPELL_CONJURER.get(), level);
            spirit.setup(ctx, s.duration(600), 0F);
            spirit.bind(caster, i);
            if (order != SummonOrder.FOLLOW) spirit.command(order, null, mark);
            level.addFreshEntity(spirit);
        }
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON,
                SoundSource.PLAYERS, 0.8F, 1.3F);
    }
}
