package com.renanpontes.isekaimahou.feature.legends_h.jojo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * KILLER QUEEN — Primeira Bomba (servidor, em memória). O glifo transforma o que ele toca em bomba:
 * <ul>
 *   <li><b>Criatura</b>: explode quando o pavio acaba (3 s; Estender +) ou quando VOCÊ a golpeia de novo corpo a
 *       corpo ("click!"). Dano 9 nela e 4,5 em volta (raio 2,5).</li>
 *   <li><b>Bloco</b>: vira uma mina por 20 s (Estender +): explode quando um inimigo chega a 1,6 bloco, ou no fim.
 *       Abre uma cratera pequena — só onde a destruição de terreno é permitida (cidades, torres e masmorras ficam
 *       intactas; a regra {@code mahouSpellDestruction} vale).</li>
 * </ul>
 * No máximo 3 bombas por conjurador (a 4ª desarma a mais antiga). Só o dono vê o brilho rosa das bombas.
 */
public final class Bombs {
    public static final int PINK = 0xFF6EB4;
    public static final float DAMAGE = 9F;
    public static final float SPLASH = 4.5F;
    public static final int MAX_PER_CASTER = 3;

    private static final class Bomb {
        final SpellContext ctx;
        final SpellStats stats;
        @Nullable
        final LivingEntity target;
        final Vec3 pos;
        final long end;
        boolean armed = true;

        Bomb(SpellContext ctx, SpellStats stats, @Nullable LivingEntity target, Vec3 pos, long end) {
            this.ctx = ctx;
            this.stats = stats;
            this.target = target;
            this.pos = pos;
            this.end = end;
        }

        Vec3 where() {
            return target != null ? LhUtil.center(target) : pos;
        }
    }

    private static final List<Bomb> BOMBS = new ArrayList<>();

    public static void plantEntity(SpellContext ctx, SpellStats stats, LivingEntity target, int ticks) {
        for (Bomb b : BOMBS) {
            if (b.target == target && b.ctx.casterId() != null && b.ctx.casterId().equals(ctx.casterId())) b.armed = false;
        }
        add(new Bomb(ctx, stats, target, target.position(), ctx.level().getGameTime() + ticks));
        click(ctx.level(), LhUtil.center(target));
    }

    public static void plantBlock(SpellContext ctx, SpellStats stats, Vec3 pos, int ticks) {
        add(new Bomb(ctx, stats, null, pos, ctx.level().getGameTime() + ticks));
        click(ctx.level(), pos);
    }

    private static void add(Bomb b) {
        BOMBS.removeIf(x -> !x.armed);
        List<Bomb> mine = new ArrayList<>();
        for (Bomb x : BOMBS) if (x.ctx.casterId() != null && x.ctx.casterId().equals(b.ctx.casterId())) mine.add(x);
        while (mine.size() >= MAX_PER_CASTER) {
            Bomb old = mine.remove(0);
            old.armed = false;
        }
        BOMBS.add(b);
    }

    private static void click(ServerLevel level, Vec3 p) {
        LhUtil.sound(level, p, SoundEvents.STONE_BUTTON_CLICK_ON, 1.0F, 1.2F);
        level.sendParticles(LhUtil.dust(PINK, 1.2F), p.x, p.y, p.z, 8, 0.2, 0.2, 0.2, 0);
    }

    /** O dono golpeia corpo a corpo uma criatura-bomba: detona (no tick seguinte, fora do evento de dano). */
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (BOMBS.isEmpty() || e.getEntity().level().isClientSide) return;
        Entity src = e.getSource().getEntity();
        if (src == null || e.getSource().getDirectEntity() != src) return;
        LivingEntity victim = e.getEntity();
        for (Bomb b : BOMBS) {
            if (b.armed && b.target == victim && src.getUUID().equals(b.ctx.casterId())) {
                b.armed = false;
                if (victim.level() instanceof ServerLevel sl) BossKit.schedule(sl, 1, () -> detonate(b));
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (BOMBS.isEmpty()) return;
        long tick = server.getTickCount();
        List<Bomb> boom = new ArrayList<>();
        for (Iterator<Bomb> it = BOMBS.iterator(); it.hasNext(); ) {
            Bomb b = it.next();
            if (!b.armed) {
                it.remove();
                continue;
            }
            ServerLevel level = b.ctx.level();
            if (b.target != null && (b.target.isRemoved() || b.target.level() != level)) {
                it.remove();
                continue;
            }
            boolean go = level.getGameTime() >= b.end;
            // Mina: um inimigo pisou perto
            if (!go && b.target == null && tick % 2 == 0) {
                go = !LhUtil.foesAround(b.ctx, b.pos, 1.6, 1).isEmpty();
            }
            if (go) {
                it.remove();
                b.armed = false;
                boom.add(b);
                continue;
            }
            // Brilho rosa que só o dono vê
            if (tick % 10 == 0 && b.ctx.caster() instanceof ServerPlayer sp && sp.level() == level) {
                Vec3 p = b.where();
                level.sendParticles(sp, LhUtil.dust(PINK, 0.8F), true, p.x, p.y + 0.2, p.z, 2, 0.15, 0.15, 0.15, 0);
            }
        }
        for (Bomb b : boom) detonate(b);
    }

    private static void detonate(Bomb b) {
        SpellContext ctx = b.ctx;
        ServerLevel level = ctx.level();
        Vec3 p = b.where();
        LivingEntity caster = ctx.caster();
        float r = Mth.clamp(b.stats.radius(2.5F), 1.5F, 6F);
        if (!LhUtil.safeAt(level, p)) {
            if (b.target != null && b.target.isAlive()) {
                b.target.invulnerableTime = 0;
                ctx.damage(b.target, DAMAGE, b.stats, null, Element.FIRE);
            }
            for (LivingEntity e : LhUtil.foesAround(ctx, p, r, 12)) {
                if (e == b.target) continue;
                ctx.damage(e, b.target == null ? DAMAGE * 0.8F : SPLASH, b.stats, null, Element.FIRE);
            }
            if (b.target == null && caster != null) Destruction.crater(level, p, Math.min(2.5F, 1.5F * Math.max(1F, b.stats.power())), caster);
        }
        ElementFx.flash(level, p, 0xFFD0E8, r);
        ElementFx.nova(level, p, Element.FIRE, PINK, r, 3);
        level.sendParticles(LhUtil.dust(PINK, 2F), p.x, p.y, p.z, 20, r * 0.3, r * 0.3, r * 0.3, 0);
        LhUtil.sound(level, p, SoundEvents.STONE_BUTTON_CLICK_OFF, 1.2F, 1.4F);
        LhUtil.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 1.1F);
    }

    public static void clear() {
        BOMBS.clear();
    }

    private Bombs() {}
}
