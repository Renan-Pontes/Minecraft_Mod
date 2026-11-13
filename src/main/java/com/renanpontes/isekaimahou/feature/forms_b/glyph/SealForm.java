package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.seal.SealHandler;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Selo: grava a magia na arma da mão principal com 5×(1+Estender) cargas. Cada golpe corpo a corpo
 * cheio aplica os efeitos no alvo e gasta uma carga. Selar de novo substitui o selo anterior.
 */
public class SealForm extends FormGlyph {
    public SealForm() {
        super("seal", 12);
        element(Element.ARCANE);
        tier(3);
        minCircle(3);
    }

    @Override
    public int baseCastTicks() { return 20; }

    @Override
    public int baseCooldown() { return 100; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        ItemStack weapon = caster.getMainHandItem();
        if (!SealHandler.canSeal(weapon)) {
            FormsBUtil.fail(ctx, "message.isekai_mahou.forms_b.seal_no_weapon");
            return;
        }
        int charges = Math.max(2, 5 * (1 + s.duration));
        SealHandler.seal(ctx, weapon, charges);
        FormsBUtil.succeeded(ctx);

        double x = caster.getX(), y = caster.getY() + caster.getBbHeight() * 0.55, z = caster.getZ();
        ElementFx.burst(level, caster.position().add(0, 1.0, 0), ctx.element(), 20, 0.5);
        level.sendParticles(Vfx.enchant(), x, y + 0.5, z, 40, 0.6, 0.6, 0.6, 0.8);
        level.sendParticles(FormsBUtil.dust(ctx.color() & 0xFFFFFF, 1.0F), x, y, z, 16, 0.4, 0.4, 0.4, 0);
        level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 1.6F);
        ServerPlayer sp = ctx.casterPlayer();
        if (sp != null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.forms_b.seal_done",
                    weapon.getHoverName(), charges).withColor(ctx.color() & 0xFFFFFF), true);
        }
    }
}
