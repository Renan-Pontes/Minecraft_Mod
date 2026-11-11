package com.renanpontes.isekaimahou.feature.fusions_d.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.feature.fusions_d.effect.SoulBurn;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.AbyssalMawEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.FdSpellEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.HellfirePillarEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.KrakenTentacleEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Misturas D de CHAMA INFERNAL (Fogo + Sombra) e ABISMO (Água + Sombra). */
public final class HellAbyssGlyphs {

    public static void register() {
        GlyphRegistry.register(new BlackFlamePillar().element(Element.HELLFIRE).tier(3).minCircle(4));
        GlyphRegistry.register(new SoulBurnGlyph().element(Element.HELLFIRE).tier(4).minCircle(4));
        GlyphRegistry.register(new KrakenGrasp().element(Element.ABYSS).tier(4).minCircle(4));
        GlyphRegistry.register(new AbyssalMaw().element(Element.ABYSS).tier(3).minCircle(3));
    }

    /** Pilar de Chamas Negras: aviso no chão e então uma coluna de fogo negro até o céu (Dividir = pilares extras nos inimigos). */
    static class BlackFlamePillar extends FdAreaGlyph {
        BlackFlamePillar() { super("black_flame_pillar", 30, 12); }

        @Override
        protected void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats) {
            HellfirePillarEntity.raise(ctx, at, stats);
            int extra = Math.min(3, Math.max(0, stats.split));
            int n = 0;
            for (LivingEntity e : FdUtil.enemies(ctx, at, 12)) {
                if (n >= extra) break;
                if (e == target || e.distanceToSqr(at) < 9) continue;
                Vec3 p = e.position();
                BossKit.schedule(ctx.level(), 4 + 4 * n, () -> HellfirePillarEntity.raise(ctx, p, stats));
                n++;
            }
        }
    }

    /** Queimar Alma: chama que queima a alma (não apaga com água), pela vida máxima e pelo mana do alvo. */
    static class SoulBurnGlyph extends EffectGlyph {
        SoulBurnGlyph() { super("soul_burn", 30); }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            List<LivingEntity> list = new ArrayList<>();
            if (FdUtil.isSelf(ctx, target)) list.addAll(FdUtil.enemies(ctx, target.position(), stats.radius(4.5F)));
            else if (target instanceof LivingEntity le && le.isAlive()) list.add(le);
            for (LivingEntity le : list) {
                le.invulnerableTime = 0;
                ctx.damage(le, 3F, stats, null);
                if (le.isAlive()) SoulBurn.ignite(ctx, le, stats.duration(120), stats, false);
            }
            if (!list.isEmpty()) FdFx.sound(ctx.level(), list.get(0).position(), SoundEvents.WITHER_AMBIENT, 0.6F, 1.6F);
        }
    }

    /** Garras do Kraken: tentáculos gigantes rompem o chão em volta do alvo, golpeiam, agarram e arremessam. */
    static class KrakenGrasp extends FdAreaGlyph {
        KrakenGrasp() { super("kraken_grasp", 40, 12); }

        /** Um novo cardume de tentáculos (e buracos no chão) no máximo a cada 1,5 s por conjurador. */
        @Override
        protected int minInterval() { return 30; }

        @Override
        protected void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 c = FdUtil.ground(sl, at, 10);
            int n = Math.min(7, 4 + Math.max(0, stats.split));
            // Teto de 10 tentáculos por conjurador CONTANDO os que vão nascer agora
            FdSpellEntity.enforceCap(sl, FdRegistries.KRAKEN_TENTACLE.get(), ctx.casterId(), 11 - n);
            float ring = Math.min(7F, stats.radius(3.4F));
            int life = Math.min(400, stats.duration(140));
            double start = sl.random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < n; i++) {
                double a = start + Math.PI * 2 * i / n;
                Vec3 spot = FdUtil.ground(sl, c.add(Math.cos(a) * ring, 3, Math.sin(a) * ring), 8);
                Vec3 to = c.subtract(spot);
                float yaw = (float) Math.atan2(-to.x, -to.z);
                int delay = i * 3;
                if (delay == 0) KrakenTentacleEntity.spawn(ctx, spot, yaw, stats, life);
                else BossKit.schedule(sl, delay, () -> KrakenTentacleEntity.spawn(ctx, spot, yaw, stats, life - delay));
            }
            FdFx.sound(sl, c, SoundEvents.ELDER_GUARDIAN_AMBIENT, 1.6F, 0.4F);
            BossKit.shake(sl, c, 24, 1.0F, 20);
        }
    }

    /** Bocarra Abissal: boca colossal que irrompe, engole, mastiga e cospe. */
    static class AbyssalMaw extends FdAreaGlyph {
        AbyssalMaw() { super("abyssal_maw", 32, 10); }

        @Override
        protected void manifest(SpellContext ctx, Vec3 at, Entity target, SpellStats stats) {
            AbyssalMawEntity.erupt(ctx, at, stats);
        }
    }

    private HellAbyssGlyphs() {}
}
