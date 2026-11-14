package com.renanpontes.isekaimahou.feature.legends_k.glyph;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_k.LkFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Projeção — a regra dos 24 quadros (Jujutsu Kaisen, Naoya Zen'in), do Pistoleiro (a velocidade do gatilho).
 * <ul>
 *   <li><b>Em você</b>: você avança em QUADROS — 4 passos de 1,75 bloco, um a cada 2 ticks (Estender +2 passos),
 *       para antes de paredes. Cada inimigo que você "toca" no caminho tem que acompanhar o ritmo.</li>
 *   <li><b>Num alvo</b> (ou quem você toca): dano 4. Quem não acompanha — criatura FRACA (vida máx. até 30, +25% por
 *       Amplificar) — fica PRESA num quadro: Atordoada 1,5 s (+½ s por Amplificar), com o painel translúcido em volta.
 *       Jogador inimigo (PvP): preso ¾ s. Forte/chefe: só Lentidão.</li>
 * </ul>
 */
public class ProjectionEffect extends EffectGlyph {
    public static final float WEAK = 30F;
    public static final double STEP = 1.75;

    public ProjectionEffect() {
        super("lk_projection", 16);
        origin(MagicPath.GUNSLINGER);
        element(Element.WIND);
        tier(3);
        minCircle(3);
    }

    @Override
    public float baseDamage() { return 4F; }

    @Override
    public int hintDuration() { return 30; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity c = ctx.caster();
        if (c == null || !(target instanceof LivingEntity t)) return;
        if (LkUtil.self(ctx, target)) {
            if (LkUtil.once(ctx, "projection")) dash(ctx, c, stats);
            return;
        }
        if (LkUtil.isFoe(ctx, t)) frame(ctx, c, t, stats);
    }

    /** O avanço quadro a quadro. */
    private static void dash(SpellContext ctx, LivingEntity c, SpellStats stats) {
        int steps = 4 + 2 * Math.max(0, stats.duration);
        Vec3 look = c.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1e-4) return;
        Vec3 step = dir.normalize().scale(STEP);
        Set<UUID> touched = new HashSet<>();
        ServerLevel l = ctx.level();
        LkUtil.sound(l, c.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9F, 1.5F);
        for (int i = 0; i < steps; i++) {
            final int k = i;
            SpellScheduler.schedule(i * 2, () -> {
                if (!c.isAlive() || c.level() != l) return;
                Vec3 from = c.position();
                Vec3 to = from.add(step);
                if (!l.noCollision(c, c.getBoundingBox().move(step)) || LkUtil.wardCrossing(l, from, to, c)) return;
                // o "quadro" que fica para trás
                LkFx.framePanel(l, c, to.add(step.scale(3)));
                c.teleportTo(to.x, to.y, to.z);
                c.fallDistance = 0;
                if (k % 2 == 0) LkUtil.sound(l, to, SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.8F);
                for (LivingEntity o : l.getEntitiesOfClass(LivingEntity.class, new AABB(to, to).inflate(1.4, 1.2, 1.4),
                        o -> o != c && LkUtil.isFoe(ctx, o))) {
                    if (touched.add(o.getUUID())) frame(ctx, c, o, stats);
                }
            });
        }
    }

    /** Quem não acompanha o ritmo dos 24 quadros fica preso num fotograma. */
    static void frame(SpellContext ctx, LivingEntity c, LivingEntity t, SpellStats stats) {
        ctx.damage(t, 4F, stats, c);
        if (!t.isAlive()) return;
        ServerLevel l = ctx.level();
        Vec3 eye = c.getEyePosition();
        if (t instanceof Player) {
            Statuses.apply(t, Statuses.Kind.STUNNED, 1, 15, c, 1F);
        } else if (LkUtil.weak(t, WEAK, stats)) {
            Statuses.apply(t, Statuses.Kind.STUNNED, 1, 30 + 10 * Math.max(0, stats.amplify), c, 1F);
        } else {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            return;
        }
        t.setDeltaMovement(Vec3.ZERO);
        LkFx.framePanel(l, t, eye);
        LkUtil.sound(l, t.position(), SoundEvents.GLASS_HIT, 0.8F, 1.6F);
        // o painel continua lá enquanto ele está preso
        SpellScheduler.schedule(12, () -> {
            if (t.isAlive() && Statuses.has(t, Statuses.Kind.STUNNED)) LkFx.framePanel(l, t, eye);
        });
    }
}
