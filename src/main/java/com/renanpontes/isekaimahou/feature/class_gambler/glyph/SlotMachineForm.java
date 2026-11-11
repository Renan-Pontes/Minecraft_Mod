package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.SlotMachineEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Caça-Níquel: invoca uma máquina à sua frente (virada para você); os 3 rolos giram ~2 s e o prêmio sai em fichas
 * teleguiadas que levam a magia (1 a 7 fichas, de ×0,7 a ×2; 7-7-7 = JACKPOT). Dividir = +1 ficha em qualquer prêmio.
 * Custo: conta ~2,5 cópias (a média do prêmio) + 1 por Dividir.
 */
public class SlotMachineForm extends FormGlyph {
    public SlotMachineForm() {
        super("slot_machine", 10);
        origin(MagicPath.GAMBLER);
        tier(3);
        minCircle(4);
    }

    @Override
    public int baseCooldown() {
        return 60;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 2.5F + Math.max(0, stats.split);
    }

    @Override
    public float hintRange() {
        return 18F;
    }

    @Override
    public int hintDuration() {
        return 42;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        Vec3 flat = new Vec3(caster.getLookAngle().x, 0, caster.getLookAngle().z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        Vec3 pos = RouletteForm.ground(level, caster.position().add(flat.scale(2.6)).add(0, 0.5, 0));
        float yaw = caster.getYRot() + 180F;
        level.addFreshEntity(SlotMachineEntity.create(ctx, pos, yaw, LuckService.luck(caster), ctx.formStats().split));
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 1, pos.z, 16, 0.4, 0.8, 0.4, 0.02);
        level.sendParticles(GbFx.dust(GbFx.GOLD, 1.3F), pos.x, pos.y + 1, pos.z, 20, 0.5, 0.9, 0.5, 0);
        GbFx.sound(level, pos, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.8F, 1.3F);
    }
}
