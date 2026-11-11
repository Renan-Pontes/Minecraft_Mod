package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * "Toque Dreno" (homenagem à habilidade de lich que o aventureiro azarado aprendeu; Sombra, origem do Ladino): a mão
 * suga VIDA e RECURSO. 5 de dano; você recupera 60% do dano causado em vida. De um jogador, 4% do recurso máximo DELE
 * passa para você; de criaturas, você ganha 2,5% do SEU máximo (4% de criaturas mágicas). Orbes verde-escuros correm
 * do alvo para você.
 */
public class DrainTouchEffect extends EffectGlyph {
    public static final float DAMAGE = 5.0F;
    public static final int COLOR = 0x3FA060;

    public DrainTouchEffect() {
        super("rogue_drain_touch", 10);
        origin(MagicPath.ROGUE);
        element(Element.SHADOW);
        tier(1);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        if (!(target instanceof LivingEntity le) || caster == null || le == caster || !le.isAlive()) return;
        ServerLevel sl = ctx.level();
        float power = stats.power() * ctx.powerMult();
        float before = le.getHealth();
        ctx.damage(le, DAMAGE, stats, null);
        float dealt = Math.max(0F, before - Math.max(0F, le.getHealth()));
        if (dealt > 0F && caster.isAlive()) caster.heal(dealt * 0.6F);
        float gained = 0F;
        if (le instanceof Player vp) {
            MagicData vd = MagicApi.get(vp);
            float take = Math.min(vd.mana(), vd.maxMana() * 0.04F * power);
            if (take > 0) {
                vd.setMana(vd.mana() - take);
                gained = take;
            }
        } else if (caster instanceof Player cp) {
            boolean magical = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType()).getNamespace().equals(IsekaiMahou.MODID);
            gained = MagicApi.get(cp).maxMana() * (magical ? 0.04F : 0.025F) * power;
        }
        if (caster instanceof ServerPlayer sp && gained > 0) {
            MagicData d = MagicApi.get(sp);
            float was = d.mana();
            d.addMana(gained);
            int real = Math.round(d.mana() - was);
            if (real > 0) MagicApi.addCounter(sp, "rogue.mana_stolen", real);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.drain", Math.round(dealt * 0.6F),
                    Math.max(real, 0), MagicApi.resourceName(sp)).withColor(COLOR), true);
        }
        // orbes viajando do alvo até você em 6 ticks
        Vec3 from = RogueFx.center(le);
        for (int k = 0; k < 6; k++) {
            final int step = k;
            SpellScheduler.schedule(k, () -> {
                if (!caster.isAlive() || caster.level() != sl) return;
                Vec3 to = RogueFx.center(caster);
                Vec3 p = from.add(to.subtract(from).scale((step + 1) / 6.0));
                sl.sendParticles(RogueFx.dust(step % 2 == 0 ? COLOR : 0x1A3A22, 1.3F), p.x, p.y, p.z, 4, 0.1, 0.1, 0.1, 0);
                if (step == 5) sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, to.x, to.y + 0.4, to.z, 5, 0.3, 0.3, 0.3, 0);
            });
        }
        sl.sendParticles(ParticleTypes.SOUL, from.x, from.y, from.z, 6, 0.25, 0.3, 0.25, 0.02);
        sl.sendParticles(RogueFx.dust(0x1A3A22, 1.6F), from.x, from.y, from.z, 12, 0.3, 0.4, 0.3, 0);
        RogueFx.sound(sl, from, SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.8F);
        RogueFx.sound(sl, from, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.3F, 1.8F);
    }
}
