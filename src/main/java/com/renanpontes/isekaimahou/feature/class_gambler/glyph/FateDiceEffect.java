package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFxNet;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.GamblerDiceEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * DADOS DO DESTINO: dois dados gigantes caem do céu sobre o alvo e o destino sai da soma (os dados mostram o resultado
 * de verdade). Dano = 1,2 × soma; e as jogadas especiais:
 * <ul>
 *   <li><b>2 (Olhos de Cobra)</b>: o destino se vira contra você — você fica Azarado e o alvo leva quase nada;</li>
 *   <li><b>7 (Sete da Sorte)</b>: além do dano, você cura e fica Sortudo;</li>
 *   <li><b>dupla</b> (faces iguais): ×1,5 e o alvo fica Azarado;</li>
 *   <li><b>12 (Doze)</b>: ×3, um raio de luz cai e você ganha +10 de Tensão.</li>
 * </ul>
 * A Sorte de quem joga pode rolar de novo um dado baixo; Dados Viciados fazem toda face ser ≥ 3.
 */
public class FateDiceEffect extends EffectGlyph {
    public static final float PER_PIP = 1.2F;
    private static final int SETTLE = 22;

    public FateDiceEffect() {
        super("gambler_fate_dice", 12);
        origin(MagicPath.GAMBLER);
        tier(1);
    }

    @Override
    public float baseDamage() {
        return PER_PIP * 7F;
    }

    @Override
    public int hintDuration() {
        return 160;
    }

    private static int face(RandomSource r, boolean loaded, int luck) {
        int f = loaded ? 3 + r.nextInt(4) : 1 + r.nextInt(6);
        if (f <= 2 && luck > 0 && r.nextFloat() < 0.06F * luck) f = Math.max(f, 1 + r.nextInt(6));
        return f;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || target == ctx.caster()) return;
        ServerLevel level = ctx.level();
        LivingEntity caster = ctx.caster();
        boolean loaded = ctx.spell().anyStats(s -> s.custom(GamblerGlyphs.LOADED_DICE_ID) > 0);
        int luck = caster != null ? LuckService.luck(caster) : 0;
        int a = face(level.random, loaded, luck), b = face(level.random, loaded, luck);
        Vec3 top = target.position().add(0, target.getBbHeight() + 2.6, 0);
        int color = ctx.color() & 0xFFFFFF;
        level.addFreshEntity(GamblerDiceEntity.show(level, top.add(-0.45, 0, 0), new Vec3(-0.05, 0.1, 0.03), color, a, SETTLE - 2));
        level.addFreshEntity(GamblerDiceEntity.show(level, top.add(0.45, 0, 0), new Vec3(0.05, 0.1, -0.03), color, b, SETTLE - 2));
        GbFx.sound(level, top, SoundEvents.BONE_BLOCK_PLACE, 0.9F, 1.4F);
        SpellScheduler.schedule(SETTLE, () -> resolve(ctx, le, stats, a, b));
    }

    private static void resolve(SpellContext ctx, LivingEntity target, SpellStats stats, int a, int b) {
        ServerLevel level = ctx.level();
        if (!target.isAlive() || target.level() != level) return;
        LivingEntity caster = ctx.caster();
        int sum = a + b;
        Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
        float dmg = PER_PIP * sum;
        if (sum == 2) {
            dmg = 1F;
            if (caster != null) caster.addEffect(new MobEffectInstance(GbRegistries.JINXED, 120, 0), caster);
            GbFxNet.at(level, GbFxNet.DICE_SNAKE, c, Vec3.ZERO, 1F, 0x6A6478, target.getId());
            GbFx.sound(level, c, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.0F, 0.5F);
        } else if (sum == 12) {
            dmg *= 3F;
            GbFxNet.at(level, GbFxNet.DICE_TWELVE, c, Vec3.ZERO, 1F, GbFx.GOLD, target.getId());
            GbFx.sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F, 1.4F);
            if (caster instanceof ServerPlayer sp && LuckService.isGambler(sp)) LuckService.addFever(sp, 10);
        } else if (a == b) {
            dmg *= 1.5F;
            ctx.status(target, GbRegistries.JINXED, 120, 0, stats);
            GbFx.coins(level, c, 6);
            GbFx.sound(level, c, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F, 1.5F);
        } else if (sum == 7) {
            if (caster != null) {
                ctx.heal(caster, 4F, stats);
                caster.addEffect(new MobEffectInstance(GbRegistries.LUCKY, 120, 0, false, true, true), caster);
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0);
            GbFx.sound(level, c, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.2F);
        }
        target.invulnerableTime = 0;
        ctx.damage(target, dmg, stats, null, sum == 12 ? Element.LIGHT : ctx.element());
        GbFx.blast(level, target.position(), 1.2 + sum * 0.1, sum == 2 ? 0x6A6478 : GbFx.GOLD);
        if (caster instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(sum == 2 ? "message.isekai_mahou.gambler.fate_snake"
                    : sum == 12 ? "message.isekai_mahou.gambler.fate_twelve" : sum == 7 ? "message.isekai_mahou.gambler.fate_seven"
                    : a == b ? "message.isekai_mahou.gambler.fate_double" : "message.isekai_mahou.gambler.fate_roll", a, b, sum), true);
        }
    }
}
