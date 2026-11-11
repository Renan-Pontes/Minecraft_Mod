package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.DiceRound;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.GamblerDiceEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Dados: joga dois dados mágicos que quicam pelo chão; quando param, a SOMA decide a explosão (poder soma/7, raio
 * 2,5 + soma/4). Dobradinha ×1,5; Doze (6+6) ×2,5; Olhos de Cobra (1+1) ×0,3. Uma explosão por jogada (o Dividir
 * joga um dado a mais, que entra na soma — não multiplica os efeitos). Acelerar joga mais longe.
 */
public class DiceRollForm extends FormGlyph {
    public static final float BASE_RADIUS = 2.5F;

    public DiceRollForm() {
        super("dice_roll", 7);
        origin(MagicPath.GAMBLER);
        tier(2);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCooldown() {
        return 24;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 1F;
    }

    @Override
    public float hintRadius() {
        return 4.25F;
    }

    @Override
    public float hintRange() {
        return 10F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 look = ctx.look();
        Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().add(look.scale(0.5));
        boolean loaded = ctx.spell().anyStats(st -> st.custom(GamblerGlyphs.LOADED_DICE_ID) > 0);
        DiceRound round = new DiceRound(ctx, loaded, LuckService.luck(caster), look, BASE_RADIUS);
        int n = 2 + Math.max(0, s.split);
        double speed = 0.55 * s.speedMult();
        for (int i = 0; i < n; i++) {
            float yaw = (i - (n - 1) / 2.0F) * 9F + (ctx.random().nextFloat() - 0.5F) * 6F;
            Vec3 d = CoreGlyphs.rotate(look, yaw, -8F);
            Vec3 vel = d.scale(speed * (0.9 + ctx.random().nextFloat() * 0.25)).add(0, 0.18, 0);
            GamblerDiceEntity die = GamblerDiceEntity.create(level, from, vel, ctx.color(), round);
            round.add(die);
            level.addFreshEntity(die);
        }
        if (!ctx.hasOrigin()) caster.swing(InteractionHand.MAIN_HAND, true);
        GbFx.sound(level, from, SoundEvents.BONE_BLOCK_PLACE, 0.8F, 1.7F);
        GbFx.sound(level, from, SoundEvents.ITEM_PICKUP, 0.6F, 0.7F);
    }
}
