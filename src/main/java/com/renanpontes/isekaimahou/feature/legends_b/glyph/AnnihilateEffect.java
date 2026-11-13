package com.renanpontes.isekaimahou.feature.legends_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.feature.legends_b.LbFx;
import com.renanpontes.isekaimahou.feature.legends_b.LbUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Aniquilação (VAZIO): apaga um pedaço do alvo da existência — dano = 12% da vida máxima (no máx. 30) + 6,
 * que IGNORA ARMADURA. O corpo se desfaz em pó. Num bloco atingido, apaga (sem drops) uma esfera de terreno (raio
 * ~1,8, cresce com área e poder até 7) pela API Destruction do núcleo (respeita a regra de destruição).
 */
public class AnnihilateEffect extends EffectGlyph {
    /** Parte percentual do dano (da vida máxima) e o teto dela (chefes não derretem num golpe). */
    private static final float PERCENT = 0.12F;
    private static final float PERCENT_CAP = 30F;
    private static final float FLAT = 6F;

    public AnnihilateEffect() {
        super("annihilate", 30);
        element(Element.VOID);
        tier(4);
        minCircle(4);
    }

    @Override
    public boolean wantsBlocks() { return true; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel sl = ctx.level();
        float base = Math.min(le.getMaxHealth() * PERCENT, PERCENT_CAP) + FLAT;
        // Compensa a armadura mantendo o dano mágico (abate continua contando como magia, Ao Abater dispara)
        float expected = base * stats.power() * ctx.powerMult() * ctx.casterSpellPower();
        float pierced = expected > 0 ? base * LbUtil.armorPierce(le, expected) / expected : base;
        ctx.damage(le, pierced, stats, null);

        int rgb = ctx.color() & 0xFFFFFF;
        Vec3 c = LbUtil.center(le);
        LbFx.disintegrate(sl, le, rgb, 36);
        // Rachaduras de vazio atravessando o corpo
        for (int i = 0; i < 3; i++) {
            Vec3 d = new Vec3(sl.random.nextDouble() - 0.5, sl.random.nextDouble() - 0.5, sl.random.nextDouble() - 0.5)
                    .normalize().scale(le.getBbWidth() * 0.5 + 0.6);
            LbFx.line(sl, c.subtract(d), c.add(d), LbFx.dust(0x0A0010, 0.9F), 6);
        }
        LbFx.sound(sl, c, SoundEvents.ENDER_EYE_DEATH, 1.2F, 0.45F);
        LbFx.sound(sl, c, SoundEvents.RESPAWN_ANCHOR_DEPLETE, 0.8F, 0.6F);
        if (!le.isAlive()) {
            // Aniquilado: implosão (o pó é sugado para o ponto onde ele estava)
            LbFx.send(sl, Vfx.reversePortal(), c, 40, le.getBbWidth() * 0.6, 0.6);
            LbFx.send(sl, LbFx.fade(rgb, 0x000000, 1.8F), c, 30, le.getBbWidth() * 0.4, 0.05);
            LbFx.send(sl, ParticleTypes.FLASH, c, 1, 0, 0);
            LbFx.sound(sl, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2F, 0.5F);
        }
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        ServerLevel sl = ctx.level();
        int rgb = ctx.color() & 0xFFFFFF;
        // Sempre um "furo" de vazio no ponto atingido (mesmo com a destruição de blocos desligada o golpe se vê)
        LbFx.send(sl, LbFx.fade(rgb, 0x000000, 1.1F), hit.getLocation(), 10, 0.25, 0.02);
        LbFx.send(sl, Vfx.reversePortal(), hit.getLocation(), 12, 0.2, 0.15);
        if (!LbUtil.erasureEnabled(ctx)) return;
        // Centro levemente para dentro do bloco atingido; v0.8: esfera apagada pelo núcleo (cresce com área e poder)
        Vec3 center = hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.4));
        float pm = Mth.clamp(ctx.powerMult(), 0.5F, 4F);
        float radius = Mth.clamp(stats.radius(1.8F) * (0.85F + 0.15F * pm), 1.0F, 7.0F);
        if (tooCloseToCaster(ctx, center, radius)) return;
        int n = Destruction.sphere(sl, center, radius, ctx.caster(), Destruction.Mode.ERASE);
        if (n == 0) return;
        for (int i = 0; i < 10; i++) {
            Vec3 v = center.add((sl.random.nextDouble() - 0.5) * radius * 2, (sl.random.nextDouble() - 0.5) * radius * 2,
                    (sl.random.nextDouble() - 0.5) * radius * 2);
            LbFx.send(sl, LbFx.fade(rgb, 0x000000, 1.3F), v, 3, 0.35, 0.01);
        }
        LbFx.send(sl, Vfx.reversePortal(), center, 30, radius * 0.5, 0.4);
        LbFx.sound(sl, center, SoundEvents.RESPAWN_ANCHOR_DEPLETE, 1.0F, 0.5F);
    }

    /** Nunca apaga o chão sob os pés de quem conjurou. */
    private static boolean tooCloseToCaster(SpellContext ctx, Vec3 center, float radius) {
        LivingEntity c = ctx.caster();
        if (c == null || c.level() != ctx.level()) return false;
        double reach = radius + 0.8;
        return c.getBoundingBox().expandTowards(0, -1.5, 0).distanceToSqr(center) < reach * reach;
    }
}
