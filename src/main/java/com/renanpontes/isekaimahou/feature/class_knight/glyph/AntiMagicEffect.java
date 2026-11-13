package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_knight.KnightAegis;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFxNet;
import com.renanpontes.isekaimahou.feature.class_knight.KnightPower;
import com.renanpontes.isekaimahou.feature.class_knight.KnightWards;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * "Lâmina Anti-Magia" (homenagem ao garoto sem mana das espadas negras do grimório de cinco folhas; origem do Cavaleiro):
 * a lâmina se cobre de anti-magia negra.
 * <ul>
 *   <li><b>Num inimigo</b>: corta 8 (×1,5 em quem estava com bênçãos ou é criatura mágica) e ANULA a magia dele —
 *   tira todas as bênçãos, quebra placas de Égide, interrompe a conjuração de jogadores e Silencia por 3 s (Estender +).</li>
 *   <li><b>Em você</b> (Si Mesmo): Postura Anti-Magia por 6 s (Estender +) — dano de magia que você recebe cai 75%,
 *   maldições saem na hora e projéteis inimigos que chegam perto são cortados no ar.</li>
 * </ul>
 */
public class AntiMagicEffect extends EffectGlyph {
    public static final float DAMAGE = 8.0F;
    public static final int SILENCE = 60;
    public static final int STANCE = 120;
    public static final int BLACK = 0x1A1A22;
    public static final int GREEN = 0x3E7A4A;

    public AntiMagicEffect() {
        super("knight_anti_magic", 18);
        element(Element.ARCANE);
        origin(MagicPath.KNIGHT);
        tier(3);
        minCircle(3);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return STANCE; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel sl = ctx.level();
        LivingEntity caster = ctx.caster();
        if (le == caster) {
            KnightWards.antiMagic(le, stats.duration(STANCE));
            if (!stats.silent) Announce.send(le, Component.translatable("announce.isekai_mahou.knight_anti_magic"), GREEN, Announce.STYLE_SPELL);
            return;
        }
        // tira as bênçãos (antes do dano: elas não protegem do corte)
        List<Holder<MobEffect>> blessings = new ArrayList<>();
        for (MobEffectInstance inst : le.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) blessings.add(inst.getEffect());
        }
        boolean magical = !blessings.isEmpty() || KnightAegis.plates(le) > 0
                || BuiltInRegistries.ENTITY_TYPE.getKey(le.getType()).getNamespace().equals(IsekaiMahou.MODID);
        for (Holder<MobEffect> h : blessings) le.removeEffect(h);
        KnightAegis.strip(le);
        KnightWards.dispel(le);
        ctx.damage(le, DAMAGE * (magical ? 1.5F : 1F) * KnightPower.mult(ctx), stats, null);
        if (le.isAlive()) Statuses.apply(le, Statuses.Kind.SILENCED, 1, stats.duration(SILENCE), caster, stats.power());
        if (le instanceof ServerPlayer victim) SpellCaster.interrupt(victim);
        Vec3 c = KnightFx.center(le);
        Vec3 dir = caster != null ? KnightFx.flat(c.subtract(caster.position()), caster.getLookAngle()) : new Vec3(0, 0, 1);
        KnightFxNet.at(sl, KnightFxNet.ANTI_MAGIC, c, dir, 1F, GREEN, le.getId());
        if (!blessings.isEmpty()) sl.sendParticles(ParticleTypes.ENCHANT, c.x, c.y + 0.5, c.z, 30, 0.5, 0.6, 0.5, 0.8);
        KnightFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
        KnightFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_BREAK, 0.9F, 0.5F);
        KnightFx.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 0.6F, 0.6F);
    }
}
