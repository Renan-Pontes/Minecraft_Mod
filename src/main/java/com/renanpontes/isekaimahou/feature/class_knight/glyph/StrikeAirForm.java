package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFxNet;
import com.renanpontes.isekaimahou.feature.class_knight.KnightWards;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Forma "Vento Invisível — Golpe do Ar" (homenagem ao "Strike Air" do rei cavaleiro; Vento, origem do Cavaleiro):
 * o Cavaleiro solta de uma vez a barreira de vento que esconde a lâmina — um túnel de vento em espiral corre 16 blocos à
 * frente (Acelerar +3 cada) com 1,8 de raio (Expandir +): cada criatura no caminho recebe os efeitos uma vez e é jogada
 * longe. Depois, o vento volta a envolver a espada: <b>Vento Invisível</b> por 20 s (a lâmina some da vista de quem a
 * empunha e dos outros; +2 de dano, +1 de alcance e golpes que empurram). Dividir = túneis paralelos. Pode ser estágio.
 */
public class StrikeAirForm extends FormGlyph {
    public static final float LENGTH = 16.0F;
    public static final float RADIUS = 1.8F;
    public static final int WIND = 0xBFF4FF;
    public static final int AIR_TICKS = 400;

    public StrikeAirForm() {
        super("knight_strike_air", 14);
        element(Element.WIND);
        origin(MagicPath.KNIGHT);
        tier(2);
        minCircle(1);
    }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 50; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public float hintRange() { return LENGTH; }

    @Override
    public float hintRadius() { return RADIUS; }

    @Override
    public int hintDuration() { return AIR_TICKS; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel sl = ctx.level();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        Vec3 eye = ctx.eyePos();
        Vec3 look = ctx.look();
        if (!ctx.hasOrigin()) caster.swing(InteractionHand.MAIN_HAND, true);
        float len = LENGTH + 3F * Math.max(0, s.speed);
        float rad = s.radius(RADIUS);
        int tunnels = Math.min(5, s.count());
        Vec3 side = KnightFx.side(look);
        for (int i = 0; i < tunnels; i++) {
            double off = tunnels == 1 ? 0 : (i - (tunnels - 1) / 2.0) * (rad * 2.2);
            Vec3 start = eye.add(side.scale(off)).add(look.scale(0.8));
            tunnel(ctx, sl, start, look, len, rad, i == tunnels - 1);
        }
        KnightFx.sound(sl, eye, SoundEvents.WIND_CHARGE_BURST.value(), 1.4F, 0.7F);
        KnightFx.sound(sl, eye, SoundEvents.BREEZE_SHOOT, 1.0F, 0.6F);
        // o vento volta a envolver a lâmina
        KnightWards.invisibleAir(caster, AIR_TICKS);
        if (!s.silent) Announce.send(caster, Component.translatable("announce.isekai_mahou.knight_strike_air"), WIND, Announce.STYLE_SPELL);
    }

    /** Um túnel: 7 trechos em 7 ticks; cada criatura recebe os efeitos uma vez e é arremessada. */
    private static void tunnel(SpellContext ctx, ServerLevel sl, Vec3 start, Vec3 dir, float len, float rad, boolean last) {
        Set<UUID> hit = new HashSet<>();
        // o túnel de vento inteiro (espiral, anéis, linhas de velocidade) é desenhado no cliente
        KnightFxNet.at(sl, KnightFxNet.STRIKE_AIR, start, dir.scale(len), rad, WIND, -1);
        int steps = 7;
        for (int k = 1; k <= steps; k++) {
            final int step = k;
            SpellScheduler.schedule(k - 1, () -> {
                double d0 = len * (step - 1) / steps, d1 = len * step / steps;
                Vec3 a = start.add(dir.scale(d0));
                Vec3 b = start.add(dir.scale(d1));
                AABB box = new AABB(a, b).inflate(rad);
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e.isAlive() && e != ctx.caster() && !e.isSpectator())) {
                    Vec3 c = KnightFx.center(e);
                    Vec3 rel = c.subtract(start);
                    double along = rel.dot(dir);
                    if (along < d0 - rad || along > d1 + rad) continue;
                    if (rel.subtract(dir.scale(along)).length() > rad + e.getBbWidth() * 0.5) continue;
                    if (!hit.add(e.getUUID())) continue;
                    ctx.resolveOnEntity(e, null);
                    if (!ctx.isAlly(e)) {
                        e.push(dir.x * 1.6, 0.35 + Math.max(0, dir.y), dir.z * 1.6);
                        e.hurtMarked = true;
                    }
                    ctx.impact(c, dir, e);
                }
                if (step == steps && last) ctx.end(b, dir);
            });
        }
        KnightFx.sound(sl, start, SoundEvents.ELYTRA_FLYING, 0.5F, 2.0F);
    }
}
