package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Roubar Bênção (origem Ladino): arranca até 2 efeitos BENÉFICOS do alvo (+1 por Amplificar) e veste em você, com a
 * duração que restava (máx. 2 min; Estender +25% cada). Os mais fortes primeiro. Sem nada para roubar, só um sopro.
 */
public class BuffTheftEffect extends EffectGlyph {
    private static final int MAX_TICKS = 2400;

    public BuffTheftEffect() {
        super("rogue_buff_theft", 14);
        origin(MagicPath.ROGUE);
        tier(3);
        minCircle(2);
    }

    @Override
    public float baseDamage() {
        return 0F;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        if (caster == null || target == caster || !(target instanceof LivingEntity le) || ctx.isAlly(le)) return;
        ServerLevel level = ctx.level();
        int max = Math.max(1, 2 + stats.amplify);
        List<MobEffectInstance> buffs = new ArrayList<>();
        for (MobEffectInstance inst : le.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) buffs.add(inst);
        }
        Vec3 a = RogueFx.center(le);
        if (buffs.isEmpty()) {
            level.sendParticles(ParticleTypes.SMOKE, a.x, a.y, a.z, 8, 0.3, 0.3, 0.3, 0.01);
            RogueFx.actionbar(caster, Component.translatable("message.isekai_mahou.class_rogue.buff_theft.none", le.getDisplayName()), 0xA89EC0);
            return;
        }
        buffs.sort((x, y) -> Integer.compare(y.getAmplifier(), x.getAmplifier()));
        float keep = 1.0F + 0.25F * Math.max(0, stats.duration);
        int n = 0;
        for (MobEffectInstance inst : buffs) {
            if (n >= max) break;
            le.removeEffect(inst.getEffect());
            int ticks = inst.isInfiniteDuration() ? 1200 : Math.min(MAX_TICKS, Math.round(inst.getDuration() * keep));
            caster.addEffect(new MobEffectInstance(inst.getEffect(), Math.max(60, ticks), inst.getAmplifier(), false, true), caster);
            n++;
        }
        Vec3 b = RogueFx.center(caster);
        RogueFx.line(level, a, b, RogueFx.dust(RogueFx.GOLD, 1.0F), 0.45);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, b.x, b.y + 0.4, b.z, 10, 0.4, 0.5, 0.4, 0.02);
        level.sendParticles(ParticleTypes.WAX_OFF, a.x, a.y, a.z, 10, 0.3, 0.4, 0.3, 0.1);
        RogueFx.sound(level, b, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.3F);
        RogueFx.sound(level, a, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.5F, 1.6F);
        if (caster instanceof ServerPlayer sp) {
            MagicApi.addCounter(sp, "rogue.buff_stolen", n);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.buff_theft.ok", n, le.getDisplayName())
                    .withColor(RogueFx.GOLD), true);
        }
        if (le instanceof ServerPlayer vp) {
            vp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.buff_theft.victim", caster.getDisplayName())
                    .withColor(0xFF6A6A), true);
        }
    }
}
