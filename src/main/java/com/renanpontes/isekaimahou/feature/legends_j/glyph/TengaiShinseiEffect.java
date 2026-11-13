package com.renanpontes.isekaimahou.feature.legends_j.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.legends_j.LjUtil;
import com.renanpontes.isekaimahou.feature.legends_j.entity.MeteorRockEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tengai Shinsei — "Queda do Céu" (homenagem a Madara, Naruto): uma rocha GIGANTE aparece no alto e desce devagar
 * (2,5 s) no ponto atingido (em Si Mesmo: no ponto da sua mira, até 64 blocos). Um círculo no chão avisa onde vai cair.
 * Impacto: dano 30 de TERRA num raio de 6 (Expandir +, máx. 12; até 30 alvos; chefes ×0,7), arremesso e tremor.
 * Terreno: uma cratera PEQUENA (raio até 4) só se a destruição de magias estiver ligada ({@code mahouSpellDestruction})
 * — a API {@code Destruction} já poupa cidades, torres, masmorras, Provações e blocos com inventário.
 * Uma rocha por conjuração; não cai dentro de cidades. Amplificar = rocha maior.
 */
public class TengaiShinseiEffect extends EffectGlyph {
    public static final float DAMAGE = 30F;
    public static final int COLOR = 0xFF7A3A;

    public TengaiShinseiEffect() {
        super("tengai_shinsei", 40);
        element(Element.EARTH);
        tier(4);
        minCircle(6);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public float hintRadius() { return 6F; }

    @Override
    public boolean wantsBlocks() { return true; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity c = ctx.caster();
        if (LjUtil.self(ctx, target)) {
            if (c == null) return;
            call(ctx, LjUtil.lookPoint(c, 64), stats);
        } else {
            call(ctx, target.position(), stats);
        }
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        LivingEntity c = ctx.caster();
        if (c != null && hit.getLocation().distanceToSqr(c.position()) < 2.25) return; // Si Mesmo: já tratado
        call(ctx, hit.getLocation(), stats);
    }

    private static void call(SpellContext ctx, Vec3 at, SpellStats stats) {
        if (!LjUtil.once(ctx, "tengai")) return;
        ServerLevel l = ctx.level();
        LivingEntity c = ctx.caster();
        if (LjUtil.safe(l, at)) {
            LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.sacred_town").withStyle(ChatFormatting.RED));
            return;
        }
        float radius = Mth.clamp(stats.radius(6F), 4F, 12F);
        float size = Mth.clamp(4.5F + 0.75F * Math.max(0, stats.amplify), 3F, 7F);
        MeteorRockEntity.spawn(ctx, stats, at, radius, size);
        // Aviso no chão e o céu "rasgando"
        ElementFx.runeCircle(l, at.add(0, 0.05, 0), COLOR, radius, MeteorRockEntity.FALL_TICKS + 10, 4);
        LjUtil.sound(l, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5F, 0.5F);
        LjUtil.sound(l, at, SoundEvents.BEACON_DEACTIVATE, 1.5F, 0.4F);
        if (c != null) {
            LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.tengai").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }
}
