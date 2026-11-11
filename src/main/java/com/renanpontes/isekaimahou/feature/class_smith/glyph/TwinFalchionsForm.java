package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.entity.SmithBladeEntity;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeMaterial;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Forma "Kanshō e Bakuya" (homenagem às lâminas gêmeas, uma negra e uma branca; origem do Ferreiro, Círculo 2): o
 * Ferreiro projeta o par e arremessa os dois em arcos espelhados (12 blocos; Acelerar = mais longe). Eles cortam quem
 * estiver no caminho, giram no fim e VOLTAM para a mão cruzando um com o outro — as gêmeas se atraem. Cada lâmina
 * aplica os efeitos uma vez por alvo. Inverter = "Sobrefio": lâminas maiores que cortam também na volta. Dividir:
 * mais pares, em leque. Custo por cópia: 2 por par (4 com Sobrefio).
 */
public class TwinFalchionsForm extends FormGlyph {
    public static final float REACH = 12.0F;
    public static final int BLACK = 0x3A3550;
    public static final int WHITE = 0xF2F4FF;

    public TwinFalchionsForm() {
        super("smith_twin_falchions", 14);
        origin(MagicPath.SMITH);
        tier(2);
        minCircle(2);
    }

    @Override
    public int baseCastTicks() { return 6; }

    @Override
    public int baseCooldown() { return 40; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public float copies(SpellStats stats, int circle) {
        return (stats.inverted ? 4F : 2F) * stats.count();
    }

    @Override
    public float hintRange() { return REACH; }

    /** Kanshō: lâmina curta e curva, negra com padrão de casco de tartaruga (netherite fosca). */
    public static ItemStack kansho() {
        return ForgedBladeItem.create(ForgedBlade.DEFAULT.withMaterial(BladeMaterial.NETHERITE)
                .withPart(ForgedBlade.Part.LENGTH, 1).withPart(ForgedBlade.Part.CURVE, 2).withPart(ForgedBlade.Part.WIDTH, 1)
                .withFinish(ForgedBlade.Finish.MATTE));
    }

    /** Bakuya: a gêmea branca (ferro polido brilhante). */
    public static ItemStack bakuya() {
        return ForgedBladeItem.create(ForgedBlade.DEFAULT.withMaterial(BladeMaterial.IRON)
                .withPart(ForgedBlade.Part.LENGTH, 1).withPart(ForgedBlade.Part.CURVE, 2).withPart(ForgedBlade.Part.WIDTH, 1)
                .withFinish(ForgedBlade.Finish.BRIGHT));
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel sl = ctx.level();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        Vec3 eye = ctx.eyePos().subtract(0, 0.3, 0);
        Vec3 look = ctx.look();
        if (!ctx.hasOrigin()) caster.swing(InteractionHand.MAIN_HAND, true);
        int pairs = Math.min(4, s.count());
        boolean over = s.inverted;
        float reach = REACH * Math.max(0.6F, s.speedMult());
        float scale = over ? 1.9F : 1.15F;
        int out = Math.max(8, Math.round(12 / Math.max(0.6F, s.speedMult())));
        LivingEntity home = ctx.hasOrigin() ? null : caster;
        int room = SmithBladeEntity.MAX_PER_OWNER - SmithBladeEntity.countFor(sl, ctx.casterId());
        for (int p = 0; p < pairs; p++) {
            float yaw = pairs == 1 ? 0F : (p - (pairs - 1) / 2.0F) * 18.0F;
            Vec3 dir = CoreGlyphs.rotate(look, yaw, 0F);
            if (room < 2) {
                // sem espaço para mais lâminas no mundo: o par corta direto o que estiver na frente
                for (LivingEntity e : ctx.enemiesAround(eye.add(dir.scale(4)), 3.0)) ctx.resolveOnEntity(e, null);
                continue;
            }
            room -= 2;
            sl.addFreshEntity(SmithBladeEntity.boomerang(sl, ctx, eye, dir, 1, kansho(), BLACK, scale, reach, 3.5F, out, over, home));
            sl.addFreshEntity(SmithBladeEntity.boomerang(sl, ctx, eye, dir, -1, bakuya(), WHITE, scale, reach, 3.5F, out, over, home));
        }
        sl.sendParticles(SmithFx.dust(BLACK, 1.2F), eye.x, eye.y, eye.z, 10, 0.3, 0.2, 0.3, 0);
        sl.sendParticles(SmithFx.dust(WHITE, 1.2F), eye.x, eye.y, eye.z, 10, 0.3, 0.2, 0.3, 0);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, eye.x + look.x, eye.y + look.y, eye.z + look.z, 1, 0, 0, 0, 0);
        SmithFx.sound(sl, eye, SoundEvents.TRIDENT_THROW, 1.0F, 1.4F);
        SmithFx.sound(sl, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.9F);
        if (!s.silent) {
            Announce.send(caster, Component.translatable(over ? "announce.isekai_mahou.smith_twin_falchions.over"
                    : "announce.isekai_mahou.smith_twin_falchions"), WHITE, Announce.STYLE_SPELL);
        }
    }
}
