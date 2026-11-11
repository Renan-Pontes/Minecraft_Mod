package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.GumTetherEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * GOMA ELÁSTICA (homenagem ao "chiclete" de aura do mágico das cartas): a magia gruda um fio rosa, elástico como
 * borracha e grudento como chiclete.
 * <ul>
 *   <li><b>Num inimigo</b>: 3 de dano ao grudar; por 6 s (Estender) ele não se afasta mais que 4 blocos — a goma o
 *   puxa de volta. <b>Agache</b> para contrair: ele vem voando até você e é ARREMESSADO para onde você olha (8 de dano
 *   ao bater). Chefes só são puxados de leve.</li>
 *   <li><b>Em você</b> (Si Mesmo): a goma gruda no bloco para onde você olha (até 24 blocos) e te puxa
 *   até lá — fuga/escalada. Agache para soltar.</li>
 * </ul>
 */
public class BungeeGumEffect extends EffectGlyph {
    public static final float STICK_DAMAGE = 3F;
    public static final int BASE_TICKS = 120;

    public BungeeGumEffect() {
        super("bungee_gum", 14);
        origin(MagicPath.GAMBLER);
        tier(2);
    }

    @Override
    public float baseDamage() {
        return STICK_DAMAGE + GumTetherEntity.IMPACT_DAMAGE;
    }

    @Override
    public int hintDuration() {
        return BASE_TICKS;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        if (target == caster) {
            Vec3 from = caster.getEyePosition();
            Vec3 to = from.add(caster.getLookAngle().scale(24));
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (hit.getType() != HitResult.Type.BLOCK) {
                if (caster instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.gum_no_anchor"), true);
                }
                return;
            }
            GumTetherEntity.grapple(ctx, caster, hit.getLocation(), stats, stats.duration(60));
            GbFx.sound(level, hit.getLocation(), SoundEvents.SLIME_BLOCK_PLACE, 1.0F, 1.3F);
            return;
        }
        if (ctx.isAlly(target) || !(target instanceof LivingEntity le) || !le.isAlive()) return;
        ctx.damage(le, STICK_DAMAGE, stats, null);
        GumTetherEntity.attach(ctx, caster, le, stats, stats.duration(BASE_TICKS));
        Vec3 c = le.position().add(0, le.getBbHeight() * 0.5, 0);
        level.sendParticles(GbFx.dust(0xFF6EC7, 1.3F), c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0);
        GbFx.sound(level, c, SoundEvents.SLIME_BLOCK_PLACE, 1.0F, 1.1F);
        GbFx.sound(level, c, SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 1.4F);
        if (caster instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.gum_stuck"), true);
        }
    }
}
