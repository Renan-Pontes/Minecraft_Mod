package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFxNet;
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
 * Forma "Caladbolg II" (homenagem à espada-flecha espiral; origem do Ferreiro, Círculo 5): o Ferreiro projeta uma
 * espada torcida como uma broca, puxa como flecha (1,2 s) e dispara — ela atravessa até 3 criaturas (Perfurar +1 cada)
 * aplicando os efeitos e, no último alvo, no bloco ou no fim do voo (48 blocos), se DESFAZ numa explosão de mana de
 * 4 blocos (Expandir aumenta) que aplica os efeitos de novo com 120% do poder ("Fantasma Partido"). Não quebra blocos.
 * Dividir: mais flechas em leque. Custo por cópia: 2 por flecha (atravessar + explosão).
 */
public class CaladbolgForm extends FormGlyph {
    public static final float RADIUS = 4.0F;
    public static final int COLOR = 0xC9A8FF;

    public CaladbolgForm() {
        super("smith_caladbolg", 30);
        origin(MagicPath.SMITH);
        tier(4);
        minCircle(5);
    }

    @Override
    public int baseCastTicks() { return 24; }

    @Override
    public int baseCooldown() { return 160; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 2F * stats.count();
    }

    @Override
    public float hintRange() { return 48F; }

    @Override
    public float hintRadius() { return RADIUS; }

    /** A espada-flecha: longa, fina, com ponta e curva máximas (o renderer a gira como broca). */
    public static ItemStack arrowBlade() {
        return ForgedBladeItem.create(ForgedBlade.DEFAULT.withMaterial(BladeMaterial.STARMETAL)
                .withPart(ForgedBlade.Part.LENGTH, ForgedBlade.MAX_LENGTH)
                .withPart(ForgedBlade.Part.TIP, ForgedBlade.MAX_PART)
                .withPart(ForgedBlade.Part.CURVE, 1)
                .withFinish(ForgedBlade.Finish.RUNIC));
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel sl = ctx.level();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        Vec3 eye = ctx.eyePos();
        Vec3 look = ctx.look();
        if (!ctx.hasOrigin()) caster.swing(InteractionHand.MAIN_HAND, true);
        int n = Math.min(5, s.count());
        float radius = s.radius(RADIUS);
        float speed = 2.6F * s.speedMult();
        int pierce = 3 + Math.max(0, s.pierce);
        int color = ctx.recipe().color() >= 0 && ctx.recipe().color() != 0xFFFFFF ? ctx.recipe().color() : COLOR;
        for (int i = 0; i < n; i++) {
            float yaw = n == 1 ? 0F : (i - (n - 1) / 2.0F) * 8.0F;
            Vec3 dir = CoreGlyphs.rotate(look, yaw, 0F);
            Vec3 from = eye.add(dir.scale(1.0)).subtract(0, 0.15, 0);
            if (SmithBladeEntity.hasRoom(sl, ctx.casterId(), 1)) {
                sl.addFreshEntity(SmithBladeEntity.carrier(sl, ctx, from, dir, arrowBlade(), color, 1.5F, speed, pierce,
                        (int) Math.ceil(48 / speed), radius, 1.2F).style(SmithBladeEntity.STYLE_SPIRAL));
            } else {
                ctx.resolveArea(from.add(dir.scale(6)), radius, null, false);
            }
        }
        // a "corda" do disparo: espiral de vento e um estalo
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 3;
            Vec3 side = look.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
            side = side.normalize();
            Vec3 up = side.cross(look).normalize();
            Vec3 q = eye.add(look.scale(1.0 + k * 0.35)).add(side.scale(Math.cos(a) * 0.5)).add(up.scale(Math.sin(a) * 0.5));
            sl.sendParticles(SmithFx.dust(color, 1.0F), q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        SmithFxNet.at(sl, SmithFxNet.CALADBOLG, eye, look, 1F, color, caster.getId());
        SmithFx.sound(sl, eye, SoundEvents.CROSSBOW_SHOOT, 1.2F, 0.5F);
        SmithFx.sound(sl, eye, SoundEvents.TRIDENT_RIPTIDE_3, 1.0F, 1.2F);
        if (!s.silent) Announce.send(caster, Component.translatable("announce.isekai_mahou.smith_caladbolg"), color, Announce.STYLE_SPELL);
    }
}
