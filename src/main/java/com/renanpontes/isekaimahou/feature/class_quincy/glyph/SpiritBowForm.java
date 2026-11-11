package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.SpiritArrowEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Arco Espiritual (homenagem ao Heilig Pfeil): dispara flechas de Reishi azul — rápidas, retas e sem gravidade — que
 * aplicam os efeitos da magia. Dividir = leque apertado (cada flecha é uma cópia), Acelerar/Perfurar/Ricochete/
 * Teleguiado/Arco/Expandir valem. A partir do rank II do Auswählen, as flechas de um Quincy atravessam +1 alvo.
 * Pode ser estágio 2+ (sai da origem do estágio).
 */
public class SpiritBowForm extends FormGlyph {
    public SpiritBowForm() {
        super("quincy_spirit_bow", 5);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(1);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCooldown() {
        return 12;
    }

    @Override
    public float hintRange() {
        return 60F;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return stats.count();
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        int n = s.count();
        float speed = 3.0F * s.speedMult();
        Vec3 origin = ctx.hasOrigin() ? ctx.eyePos() : new Vec3(caster.getX(), caster.getEyeY() - 0.12, caster.getZ());
        int bonusPierce = caster instanceof ServerPlayer sp && Quincy.isQuincy(sp) && Quincy.rank(MagicApi.get(sp)) >= 2 ? 1 : 0;
        // Letra X (O Eixo X): as flechas atravessam +1 alvo
        if (caster instanceof ServerPlayer sp && Quincy.letter(MagicApi.get(sp)) == Quincy.Letter.XAXIS) bonusPierce++;
        for (int i = 0; i < n; i++) {
            float yaw = n == 1 ? 0 : (i - (n - 1) / 2.0F) * 6.0F;
            float pitch = 0;
            if (s.spread) {
                yaw = (ctx.random().nextFloat() - 0.5F) * 40.0F;
                pitch = (ctx.random().nextFloat() - 0.5F) * 18.0F;
            }
            Vec3 d = QuincyGlyphs.rotate(ctx.look(), yaw, pitch);
            SpiritArrowEntity a = SpiritArrowEntity.spell(ctx, caster, origin).pierce(bonusPierce);
            a.shoot(d.x, d.y, d.z, speed, 0.4F);
            ctx.level().addFreshEntity(a);
        }
        QuincyFx.bowFlash(ctx.level(), caster);
        QuincyFx.sound(ctx.level(), origin, SoundEvents.ARROW_SHOOT, 0.9F, 1.6F);
        QuincyFx.sound(ctx.level(), origin, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.5F);
    }
}
