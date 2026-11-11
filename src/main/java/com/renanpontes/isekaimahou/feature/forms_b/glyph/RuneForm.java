package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellRuneEntity;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Runa: armadilha quase invisível no bloco mirado (até 20 blocos). Dispara quando um inimigo chega
 * perto. Dividir = várias runas em círculo em volta do ponto. Como estágio de magia programável, é
 * gravada no chão do ponto atingido (com Saltar, cai sob o próximo inimigo).
 */
public class RuneForm extends FormGlyph {
    public RuneForm() {
        super("rune", 8);
        element(Element.ARCANE);
        tier(2);
        minCircle(2);
    }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 30; }

    @Override
    public void cast(SpellContext ctx) {
        if (ctx.caster() == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 at = FormsBUtil.aimGround(ctx, 20);
        int n = s.count();
        int rgb = ctx.color() & 0xFFFFFF;
        int accent = FormsBUtil.accentColor(ctx);
        for (int i = 0; i < n; i++) {
            Vec3 p = at;
            if (n > 1) {
                double a = Math.PI * 2 * i / n;
                Vec3 g = FormsBUtil.groundBelow(level, at.add(Math.cos(a) * 1.8, 1.5, Math.sin(a) * 1.8), 4);
                if (g != null) p = g;
            }
            SpellRuneEntity rune = new SpellRuneEntity(FormsBRegistry.SPELL_RUNE.get(), level);
            rune.setup(ctx, s.duration(1200), s.radius(2.0F));
            rune.setPos(p.x, p.y + 0.01, p.z);
            level.addFreshEntity(rune);
            level.sendParticles(FormsBUtil.dust(rgb, 0.8F), p.x, p.y + 0.1, p.z, 8, 0.4, 0.02, 0.4, 0);
            level.sendParticles(FormsBUtil.dust(accent, 0.6F), p.x, p.y + 0.1, p.z, 4, 0.3, 0.02, 0.3, 0);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 0.6F, 1.5F);
    }
}
