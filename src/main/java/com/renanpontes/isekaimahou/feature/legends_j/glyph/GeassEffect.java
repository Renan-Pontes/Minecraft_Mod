package com.renanpontes.isekaimahou.feature.legends_j.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_j.GeassControl;
import com.renanpontes.isekaimahou.feature.legends_j.LjUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Geass (homenagem a Code Geass): uma ordem pelo CONTATO VISUAL — o alvo precisa conseguir te ver.
 * <ul>
 *   <li><b>"Obedeça!"</b> (padrão) — uma criatura FRACA (vida máxima até 40, +25% por Amplificar; nada de chefes ou
 *       NPCs) luta por você por 12 s (Estender +): ataca os inimigos mais próximos e não encosta em você nem no seu
 *       grupo.</li>
 *   <li><b>"Pare!"</b> (conjurando agachado) — a criatura fraca fica Atordoada 5 s (Estender +).</li>
 *   <li>Criatura forte ou chefe: a ordem só a faz hesitar (Atordoado 1 s). Jogador inimigo (PvP): Atordoado 1 s.</li>
 * </ul>
 * Funciona UMA vez em cada criatura (como no anime). Não mata ninguém. Uma ordem por conjuração.
 */
public class GeassEffect extends EffectGlyph {
    public static final int COLOR = 0xFF2A3A;
    public static final String TAG = "isekai_mahou.lj_geass";
    public static final float WEAK_HEALTH = 40F;

    public GeassEffect() {
        super("geass", 20);
        element(Element.SHADOW);
        tier(4);
        minCircle(4);
    }

    @Override
    public float baseDamage() { return 0F; }

    @Override
    public int hintDuration() { return 240; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity c = ctx.caster();
        if (c == null) return;
        LivingEntity t = target instanceof LivingEntity le && !LjUtil.self(ctx, target) ? le : null;
        if (t == null) t = LjUtil.lookTarget(c, 24, e -> !LjUtil.self(ctx, e));
        if (t == null || !LjUtil.once(ctx, "geass")) return;
        ServerLevel l = ctx.level();
        // Contato visual: o alvo tem que conseguir te ver
        if (!t.hasLineOfSight(c)) {
            LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.geass_no_eyes").withStyle(ChatFormatting.GRAY));
            return;
        }
        sigil(l, c, t);
        if (t instanceof Player p) {
            if (LjUtil.isFoe(ctx, p)) {
                Statuses.apply(p, Statuses.Kind.STUNNED, 1, 20, c, 1F);
                say(c, "geass_stop");
            }
            return;
        }
        boolean weak = LjUtil.ordinaryMob(t) && t.getMaxHealth() <= WEAK_HEALTH * (1F + 0.25F * Math.max(0, stats.amplify));
        if (!weak) {
            Statuses.apply(t, Statuses.Kind.STUNNED, 1, 20, c, 1F);
            LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.geass_resist", t.getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (t.getPersistentData().getBoolean(TAG)) {
            LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.geass_once", t.getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        t.getPersistentData().putBoolean(TAG, true);
        if (c.isShiftKeyDown() || !(t instanceof Mob)) {
            Statuses.apply(t, Statuses.Kind.STUNNED, 1, stats.duration(100), c, 1F);
            if (t instanceof Mob m) {
                m.setTarget(null);
                m.getNavigation().stop();
            }
            say(c, "geass_stop");
            return;
        }
        GeassControl.obey((Mob) t, c, stats.duration(240));
        say(c, "geass_obey");
    }

    private static void say(LivingEntity c, String key) {
        LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j." + key).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    /** O pássaro vermelho do Geass: no olho de quem ordena e no olho de quem obedece. */
    static void sigil(ServerLevel l, LivingEntity c, LivingEntity t) {
        Vec3 ce = c.getEyePosition().add(c.getLookAngle().scale(0.3));
        Vec3 te = t.getEyePosition();
        ElementFx.sendFar(l, ElementFx.glow(COLOR, 0.9F), ce.x, ce.y, ce.z, 3, 0.05, 0.05, 0.05, 0);
        bird(l, te.add(0, 0.25, 0), t.getYRot());
        ElementFx.runeCircle(l, te.add(0, 0.6, 0), COLOR, 0.7F, 30, 2);
        // Linha do olhar
        Vec3 d = te.subtract(ce);
        int n = (int) Math.min(24, d.length() * 1.5);
        for (int i = 1; i < n; i++) {
            Vec3 p = ce.add(d.scale(i / (double) n));
            ElementFx.sendFar(l, ElementFx.glow(COLOR, 0.25F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        LjUtil.sound(l, ce, SoundEvents.EVOKER_CAST_SPELL, 0.8F, 0.6F);
        LjUtil.sound(l, te, SoundEvents.ENDER_EYE_DEATH, 0.8F, 0.7F);
    }

    /** Asas abertas (um "V" com as pontas curvadas) viradas para quem olha. */
    private static void bird(ServerLevel l, Vec3 c, float yRot) {
        double rad = Math.toRadians(yRot);
        Vec3 right = new Vec3(Math.cos(rad), 0, Math.sin(rad));
        for (int i = -6; i <= 6; i++) {
            double x = i * 0.07;
            double y = Math.abs(i) * 0.045 - (Math.abs(i) > 4 ? (Math.abs(i) - 4) * 0.06 : 0);
            Vec3 p = c.add(right.scale(x)).add(0, y, 0);
            ElementFx.sendFar(l, ElementFx.glow(COLOR, 0.35F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}
