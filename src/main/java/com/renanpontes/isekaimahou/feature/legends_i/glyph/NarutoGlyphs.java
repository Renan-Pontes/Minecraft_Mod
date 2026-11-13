package com.renanpontes.isekaimahou.feature.legends_i.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.legends_i.LiGlyphs;
import com.renanpontes.isekaimahou.feature.legends_i.LiNet;
import com.renanpontes.isekaimahou.feature.legends_i.LiUtil;
import com.renanpontes.isekaimahou.feature.legends_i.entity.KageBunshinEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos de Naruto (efeitos comuns do Grimório):
 * <ul>
 *   <li>{@link Chidori} Chidori / Raikiri (Sasuke, Kakashi) — investida de raio que atravessa o primeiro inimigo.</li>
 *   <li>{@link KageBunshin} Kage Bunshin no Jutsu — clones das sombras que lutam um pouco e somem em fumaça.</li>
 *   <li>{@link PaperShuriken} Shuriken de Papel (Konan) — tempestade de folhas de papel cortantes.</li>
 * </ul>
 * O Rasengan não está aqui: a "Esfera Espiral" (legends_b) já é um glifo comum de forma.
 */
public final class NarutoGlyphs {
    public static final int CHIDORI_BLUE = 0x8FD8FF;
    public static final int PAPER = 0xF2EEDD;

    public static List<EffectGlyph> all() {
        return List.of(new Chidori(), new KageBunshin(), new PaperShuriken());
    }

    /** Inimigos cuja caixa encosta no segmento {@code from→to} (com folga {@code width}), do mais perto ao mais longe. */
    static List<LivingEntity> foesOnLine(SpellContext ctx, Vec3 from, Vec3 to, double width, int max) {
        AABB box = new AABB(from, to).inflate(width + 1.0);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, box, e -> LiUtil.isFoe(ctx, e))) {
            AABB hb = e.getBoundingBox().inflate(width);
            if (hb.contains(from) || hb.clip(from, to).isPresent()) out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    // =====================================================================================
    //  Chidori / Raikiri
    // =====================================================================================
    /**
     * "Mil pássaros cantando": a mão se enche de raios e você dispara. Em você: investida de 8 blocos (Acelerar +;
     * máx. 14; para antes de paredes) que ATRAVESSA o primeiro inimigo e para logo atrás dele, acertando até 3 no
     * caminho. Em outras formas: se o alvo está a até 12 blocos e à vista, você surge na frente dele (uma vez por
     * conjuração); longe demais, só o raio chega (70%). Dano 10 de RAIO + Eletrizado ×2. Com Amplificar o corte fica
     * azul-claro — o Raikiri do Kakashi.
     */
    public static class Chidori extends EffectGlyph {
        public static final float DAMAGE = 10F;

        public Chidori() {
            super("chidori", 18);
            LiGlyphs.config(this, Element.LIGHTNING, 3, 3);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return 8F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel l = ctx.level();
            int color = stats.amplify > 0 ? 0xD8F4FF : CHIDORI_BLUE;
            if (LiUtil.self(ctx, target)) {
                if (!LiUtil.once(ctx, "chidori")) return;
                Vec3 aim = c.getLookAngle();
                Vec3 dir = new Vec3(aim.x, Mth.clamp(aim.y, -0.35, 0.35), aim.z);
                dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
                float dist = Mth.clamp(8F * stats.speedMult(), 4F, 14F);
                Vec3 waist = c.position().add(0, 0.9, 0);
                double go = clearDistance(l, c, waist, dir, dist);
                List<LivingEntity> hit = foesOnLine(ctx, waist, waist.add(dir.scale(go)), 0.9, 3);
                if (!hit.isEmpty()) {
                    LivingEntity first = hit.get(0);
                    double past = Math.sqrt(first.distanceToSqr(waist)) + first.getBbWidth() * 0.5 + 1.2;
                    go = Math.min(go, past);
                }
                dash(ctx, stats, c, dir, go, hit, color);
                return;
            }
            if (!(target instanceof LivingEntity le)) return;
            Vec3 tc = LiUtil.center(le);
            boolean near = le.distanceToSqr(c) <= 12 * 12 && c.hasLineOfSight(le);
            if (near && LiUtil.once(ctx, "chidori_dash")) {
                Vec3 waist = c.position().add(0, 0.9, 0);
                Vec3 d = tc.subtract(waist);
                Vec3 dir = d.lengthSqr() < 1.0E-4 ? c.getLookAngle() : d.normalize();
                double go = Math.max(0, Math.min(clearDistance(l, c, waist, dir, (float) d.length()), d.length() - le.getBbWidth() * 0.5 - 0.8));
                dash(ctx, stats, c, dir, go, List.of(le), color);
                return;
            }
            // Só o raio (longe demais, ou outros alvos da mesma conjuração)
            ctx.damage(le, DAMAGE * (near ? 1F : 0.7F), stats, c, Element.LIGHTNING);
            Statuses.apply(le, Statuses.Kind.SHOCKED, 2, 80, c, LiUtil.power(ctx, stats));
            LiUtil.fx(l, LiNet.CHIDORI, LiUtil.center(c), tc.subtract(LiUtil.center(c)), color, 8, 0.5F);
            LiUtil.sound(l, tc, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.6F);
        }

        private static double clearDistance(ServerLevel l, LivingEntity c, Vec3 from, Vec3 dir, float dist) {
            BlockHitResult bh = l.clip(new ClipContext(from, from.add(dir.scale(dist)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
            return bh.getType() == HitResult.Type.BLOCK ? Math.max(0, bh.getLocation().distanceTo(from) - 0.7) : dist;
        }

        private static void dash(SpellContext ctx, SpellStats stats, LivingEntity c, Vec3 dir, double go, List<LivingEntity> hit, int color) {
            ServerLevel l = ctx.level();
            Vec3 start = c.position();
            Vec3 waist = start.add(0, 0.9, 0);
            LiUtil.state(c, LiNet.S_CHIDORI, 14);
            LiUtil.fx(l, LiNet.CHIDORI, waist, dir.scale(Math.max(1.0, go)), color, 12, 0.7F);
            LiUtil.sound(l, start, SoundEvents.TRIDENT_THUNDER, 0.5F, 1.8F);
            LiUtil.sound(l, start, SoundEvents.PARROT_AMBIENT, 1.0F, 1.9F);
            LiUtil.sound(l, start, SoundEvents.BEEHIVE_WORK, 1.0F, 1.6F);
            if (go > 0.5) {
                ArcaneUtil.teleport(c, start.add(dir.scale(go)), c.getYRot(), c.getXRot());
                if (c instanceof ServerPlayer sp) com.renanpontes.isekaimahou.core.MagicHover.allow(sp, 20);
            }
            float power = LiUtil.power(ctx, stats);
            for (LivingEntity e : hit) {
                ctx.damage(e, DAMAGE, stats, c, Element.LIGHTNING);
                Statuses.apply(e, Statuses.Kind.SHOCKED, 2, 80, c, power);
                ElementFx.impact(l, LiUtil.center(e), Element.LIGHTNING, color, 3, dir.scale(-1));
            }
            ElementFx.slash(l, waist.add(dir.scale(go * 0.5)), dir, Element.LIGHTNING, color, (float) Math.max(2.5, go), 3);
            if (!hit.isEmpty()) LiUtil.sound(l, LiUtil.center(hit.get(0)), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.3F);
        }
    }

    // =====================================================================================
    //  Kage Bunshin no Jutsu
    // =====================================================================================
    /**
     * Pof! Clones das sombras: 2 (+1 por Dividir; máx. 5) cópias suas, com a sua arma na mão, surgem numa nuvem de
     * fumaça — em volta de você (Si Mesmo) ou em volta do alvo atingido — e lutam por 15 s (Estender +; máx. 45 s).
     * Cada clone tem 25% da sua vida máxima, bate com 60% do seu ataque × poder e some em fumaça depois de 2 golpes.
     * Monstros que miravam em você podem se confundir e ir atrás de um clone. Máx. 5 clones seus ao mesmo tempo. Em Modo
     * Paz ou dentro de cidades os clones não atacam.
     */
    public static class KageBunshin extends EffectGlyph {
        public static final int TICKS = 300;

        public KageBunshin() {
            super("kage_bunshin", 26);
            LiGlyphs.config(this, Element.SHADOW, 3, 3);
        }

        @Override
        public int hintDuration() { return TICKS; }

        @Override
        public boolean isBeneficial() { return false; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !LiUtil.once(ctx, "kage_bunshin")) return;
            ServerLevel l = ctx.level();
            int n = Mth.clamp(2 + Math.max(0, stats.count() - 1), 1, 5);
            int ticks = Mth.clamp(stats.duration(TICKS), 60, 900);
            float power = LiUtil.power(ctx, stats);
            boolean self = LiUtil.self(ctx, target);
            Vec3 around = self ? c.position() : target.position();
            LivingEntity focus = !self && target instanceof LivingEntity le ? le : null;
            int made = KageBunshinEntity.spawnAround(l, c, around, n, ticks, power, focus);
            if (made > 0) {
                LiUtil.sound(l, around, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0F, 1.2F);
                LiUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_i.kage_bunshin", made).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    // =====================================================================================
    //  Shuriken de Papel (Konan)
    // =====================================================================================
    /**
     * Folhas de papel afiadas como lâminas cortam o alvo 4 vezes em 0,6 s: 2,2 de dano cada (VENTO) e Sangrando no
     * último corte. Em você: um redemoinho de papel de raio 4 (Expandir +; máx. 12) corta até 12 inimigos em volta. Com
     * Expandir e formas de área vira o "Mar de Papel" da Konan.
     */
    public static class PaperShuriken extends EffectGlyph {
        public static final float DAMAGE = 2.2F;
        public static final float RADIUS = 4F;
        static final ItemParticleOption PAPER_BITS = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER));

        public PaperShuriken() {
            super("paper_shuriken", 12);
            LiGlyphs.config(this, Element.WIND, 2, 1);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel l = ctx.level();
            if (LiUtil.self(ctx, target)) {
                if (!LiUtil.once(ctx, "paper_storm")) return;
                float r = Mth.clamp(stats.radius(RADIUS), 2F, 12F);
                Vec3 p = c.position();
                for (int k = 0; k < 4; k++) {
                    final int step = k;
                    BossKit.schedule(l, k * 3, () -> {
                        if (!c.isAlive() || c.level() != l) return;
                        Vec3 at = c.position();
                        for (int i = 0; i < 10; i++) {
                            double a = (i / 10.0) * Math.PI * 2 + step * 0.6;
                            double rr = r * (0.4 + 0.6 * ((i + step) % 3) / 2.0);
                            ElementFx.sendFar(l, PAPER_BITS, at.x + Math.cos(a) * rr, at.y + 0.6 + (i % 3) * 0.5, at.z + Math.sin(a) * rr,
                                    2, 0.1, 0.1, 0.1, 0.15);
                        }
                    });
                }
                ElementFx.shockwave(l, p.add(0, 0.8, 0), PAPER, r, 14, null);
                LiUtil.sound(l, p, SoundEvents.BOOK_PAGE_TURN, 1.4F, 0.8F);
                LiUtil.sound(l, p, SoundEvents.PHANTOM_FLAP, 1.0F, 1.4F);
                for (LivingEntity e : LiUtil.foesAround(ctx, p, r, 12)) cut(ctx, stats, c, e);
                return;
            }
            if (!(target instanceof LivingEntity le)) return;
            cut(ctx, stats, c, le);
        }

        private static void cut(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity e) {
            ServerLevel l = ctx.level();
            Vec3 from = c.getEyePosition();
            for (int k = 0; k < 4; k++) {
                final int step = k;
                BossKit.schedule(l, k * 3, () -> {
                    if (!e.isAlive() || e.level() != l) return;
                    Vec3 tc = LiUtil.center(e);
                    Vec3 src = c.isAlive() ? c.getEyePosition() : from;
                    // folhas voando da sua mão até o alvo
                    for (int i = 1; i <= 4; i++) {
                        Vec3 p = src.lerp(tc, i / 4.0);
                        ElementFx.sendFar(l, PAPER_BITS, p.x, p.y, p.z, 1, 0.15, 0.15, 0.15, 0.05);
                    }
                    ElementFx.sendFar(l, PAPER_BITS, tc.x, tc.y, tc.z, 6, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, e.getBbWidth() * 0.4, 0.12);
                    e.invulnerableTime = 0;
                    ctx.damage(e, DAMAGE, stats, c, Element.WIND);
                    if (step == 3) Statuses.apply(e, Statuses.Kind.BLEEDING, 1, 100, c, LiUtil.power(ctx, stats));
                    LiUtil.sound(l, tc, SoundEvents.SHEEP_SHEAR, 0.6F, 1.6F + step * 0.1F);
                });
            }
        }
    }

    private NarutoGlyphs() {}
}
