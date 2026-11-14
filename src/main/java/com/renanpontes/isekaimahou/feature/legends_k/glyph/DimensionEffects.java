package com.renanpontes.isekaimahou.feature.legends_k.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_k.LkFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkNet;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;
import com.renanpontes.isekaimahou.feature.legends_k.state.PocketDimension;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Os dois glifos da dimensão de bolso ({@link PocketDimension}), com o mesmo núcleo:
 * <ul>
 *   <li><b>Kamui</b> (Naruto, Obito — Ladino): em você, entra/sai da dimensão (tela azulada, intangível, até 2 min,
 *       agache 2× para sair). Num inimigo FRACO (vida máx. até 40), suga-o para a dimensão por 8 s (Estender +) e ele
 *       volta no mesmo ponto. Inimigo forte/chefe: o espaço só torce em volta dele (dano e lentidão). Jogador inimigo
 *       (PvP): preso na dimensão por 5 s (não acerta nem é acertado).</li>
 *   <li><b>Entre Dimensões</b> (D4C, JoJo — Steel Ball): igual em você (azul-claro). Num inimigo fraco (até 50), ele
 *       é jogado "entre" dois mundos por 5 s e volta enjoado (Fraqueza e lentidão). Forte/chefe: espremido entre as
 *       dimensões (mais dano). Jogador inimigo: preso 3 s.</li>
 * </ul>
 * De dentro da dimensão você não alcança ninguém: o glifo só serve para sair.
 */
public abstract class DimensionEffects extends EffectGlyph {
    private final byte style;

    protected DimensionEffects(String path, int cost, byte style) {
        super(path, cost);
        this.style = style;
    }

    protected abstract float weakHealth();

    protected abstract int stashTicks();

    protected abstract int trapTicks();

    protected abstract float strongDamage();

    @Override
    public float baseDamage() { return strongDamage(); }

    @Override
    public int hintDuration() { return stashTicks(); }

    @Override
    public float hintRadius() { return -1F; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity c = ctx.caster();
        if (c == null || !(target instanceof LivingEntity t)) return;
        if (LkUtil.self(ctx, target)) {
            if (LkUtil.once(ctx, "pocket_self")) PocketDimension.toggle(c, style);
            return;
        }
        if (PocketDimension.phased(c)) return; // do outro lado não se alcança ninguém
        int rgb = style == LkNet.KAMUI ? LkFx.KAMUI : LkFx.D4C;
        if (t instanceof Player p) {
            if (LkUtil.isFoe(ctx, p) && !PocketDimension.phased(p)) PocketDimension.enter(p, style, trapTicks(), true);
            return;
        }
        if (LkUtil.weak(t, weakHealth(), stats)) {
            if (style == LkNet.D4C) ctx.damage(t, 3F, stats, c);
            if (t.isAlive() && PocketDimension.stash(t, stats.duration(stashTicks()), style)) {
                LkUtil.say(c, style == LkNet.KAMUI ? "kamui_suck" : "d4c_suck", ChatFormatting.AQUA, t.getDisplayName());
            }
            return;
        }
        // forte ou chefe: o espaço só torce em volta
        ctx.damage(t, strongDamage(), stats, c);
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, style == LkNet.D4C ? 2 : 1));
        LkFx.swirl(ctx.level(), LkUtil.center(t), Math.max(0.8, t.getBbWidth()), rgb, 24);
        LkUtil.say(c, "pocket_resist", ChatFormatting.GRAY, t.getDisplayName());
    }

    /** Kamui (Obito): a dimensão do Sharingan — Ladino. */
    public static class Kamui extends DimensionEffects {
        public Kamui() {
            super("lk_kamui", 22, LkNet.KAMUI);
            origin(MagicPath.ROGUE);
            element(Element.VOID);
            tier(4);
            minCircle(4);
        }

        @Override
        protected float weakHealth() { return 40F; }

        @Override
        protected int stashTicks() { return 160; }

        @Override
        protected int trapTicks() { return 100; }

        @Override
        protected float strongDamage() { return 5F; }
    }

    /** D4C — Entre Dimensões (Valentine): Steel Ball. */
    public static class D4C extends DimensionEffects {
        public D4C() {
            super("lk_d4c", 24, LkNet.D4C);
            origin(MagicPath.STEELBALL);
            element(Element.VOID);
            tier(4);
            minCircle(5);
        }

        @Override
        protected float weakHealth() { return 50F; }

        @Override
        protected int stashTicks() { return 100; }

        @Override
        protected int trapTicks() { return 60; }

        @Override
        protected float strongDamage() { return 7F; }
    }
}
