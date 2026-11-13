package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFxNet;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.ThrownCardEntity;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Carta: arremessa cartas carregadas de energia (homenagem ao Gambit) que levam a magia e estouram ao acertar.
 * Cada Dividir abre o leque com +2 cartas (1, 3, 5...), e cada carta aplica os efeitos — por isso conta como cópias
 * no custo. Perfurar, Ricochete, Teleguiado e Acelerar valem. Pode ser estágio 2+ (sai da origem do estágio).
 */
public class CardThrowForm extends FormGlyph {
    public CardThrowForm() {
        super("card_throw", 5);
        origin(MagicPath.GAMBLER);
        tier(1);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCooldown() {
        return 10;
    }

    /** Cartas do leque: 1 + 2 por Dividir. */
    public static int cards(SpellStats s) {
        return 1 + 2 * Math.max(0, s.split);
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return cards(stats);
    }

    @Override
    public float hintRange() {
        return 40F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 look = ctx.look();
        if (!ctx.hasOrigin() && caster instanceof Mob mob && mob.getTarget() != null) {
            look = mob.getTarget().getEyePosition().subtract(caster.getEyePosition()).normalize();
        }
        Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().add(look.scale(0.4)).subtract(0, 0.1, 0);
        int n = cards(s);
        float speed = 1.9F * s.speedMult();
        for (int i = 0; i < n; i++) {
            float yaw = n == 1 ? 0 : (i - (n - 1) / 2.0F) * 7.0F;
            float pitch = 0;
            if (s.spread) {
                yaw = (ctx.random().nextFloat() - 0.5F) * 36F;
                pitch = (ctx.random().nextFloat() - 0.5F) * 14F;
            }
            Vec3 d = CoreGlyphs.rotate(look, yaw, pitch);
            level.addFreshEntity(ThrownCardEntity.spell(ctx, from, d, speed, ThrownCardEntity.Mode.SPELL));
        }
        if (!ctx.hasOrigin()) caster.swing(InteractionHand.MAIN_HAND, true);
        GbFx.sound(level, from, SoundEvents.BOOK_PAGE_TURN, 0.9F, 1.7F);
        GbFx.sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.9F);
        GbFxNet.at(level, GbFxNet.CARD_FAN, from, look, 1F, ctx.color() & 0xFFFFFF, caster.getId());
    }
}
