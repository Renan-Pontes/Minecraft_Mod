package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Sifão (origem Ladino): dano 2 e rouba RECURSO. De um jogador: 6% do máximo DELE (× poder) sai dele e vem para você
 * (Mana, Vigor ou Foco — é o mesmo valor). De criaturas: você drena 3% do SEU máximo (× poder), 4,5% de criaturas
 * mágicas (do mod), e elas ficam Fracas por 3 s. Quantidades em fração do máximo (acompanham a inflação de mana).
 */
public class ManaSiphonEffect extends EffectGlyph {
    public static final float DAMAGE = 2.0F;

    public ManaSiphonEffect() {
        super("rogue_mana_siphon", 10);
        origin(MagicPath.ROGUE);
        tier(2);
    }

    @Override
    public float baseDamage() {
        return DAMAGE;
    }

    @Override
    public int hintDuration() {
        return 60;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        if (!(target instanceof LivingEntity le) || caster == null || le == caster) return;
        ServerLevel level = ctx.level();
        float power = stats.power() * ctx.powerMult();
        ctx.damage(le, DAMAGE, stats, null);
        float gained = 0F;
        if (le instanceof Player vp) {
            MagicData vd = MagicApi.get(vp);
            float take = Math.min(vd.mana(), vd.maxMana() * 0.06F * power);
            if (take > 0) {
                vd.setMana(vd.mana() - take);
                gained = take;
                if (vp instanceof ServerPlayer vsp) {
                    vsp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.siphon.victim",
                            caster.getDisplayName(), Math.round(take)).withColor(0xFF6A6A), true);
                }
            }
        } else {
            boolean magical = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType()).getNamespace().equals(IsekaiMahou.MODID);
            if (caster instanceof Player cp) gained = MagicApi.get(cp).maxMana() * (magical ? 0.045F : 0.03F) * power;
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stats.duration(60), 0), caster);
        }
        if (caster instanceof ServerPlayer sp && gained > 0) {
            MagicData d = MagicApi.get(sp);
            float before = d.mana();
            d.addMana(gained);
            int real = Math.round(d.mana() - before);
            if (real > 0) MagicApi.addCounter(sp, "rogue.mana_stolen", real);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.siphon.ok", Math.max(real, Math.round(gained)),
                    MagicApi.resourceName(sp)).withColor(RogueFx.MANA), true);
        }
        Vec3 a = RogueFx.center(le);
        Vec3 b = RogueFx.center(caster);
        RogueFx.line(level, a, b, RogueFx.dust(RogueFx.MANA, 0.9F), 0.45);
        level.sendParticles(ParticleTypes.SOUL, a.x, a.y, a.z, 4, 0.2, 0.2, 0.2, 0.02);
        level.sendParticles(ParticleTypes.ENCHANT, b.x, b.y + 0.3, b.z, 12, 0.3, 0.4, 0.3, 0.6);
        RogueFx.sound(level, b, SoundEvents.BEACON_POWER_SELECT, 0.4F, 1.8F);
    }
}
