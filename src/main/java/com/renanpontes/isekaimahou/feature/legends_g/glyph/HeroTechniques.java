package com.renanpontes.isekaimahou.feature.legends_g.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.legends_g.LgUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos de outros heróis de anime (todos EFEITOS comuns, combináveis com qualquer forma):
 * <ul>
 *   <li>{@link FairyLaw} Lei das Fadas (Luz, homenagem a "Fairy Tail") — luz que só fere quem é inimigo de verdade.</li>
 *   <li>{@link SeriousPunch} Soco Sério (Arcano, homenagem a "One Punch Man") — um soco que arremessa longe.</li>
 *   <li>{@link Hinokami} Dança do Deus do Fogo (Fogo, homenagem a "Demon Slayer") — três golpes de fogo seguidos.</li>
 * </ul>
 * São MAGIAS (glifos), não poderes de raça: a raça Draconato (Mata-Dragões) continua sendo de outro módulo.
 */
public final class HeroTechniques {
    public static final int GOLD = 0xFFE27A;
    public static final int FIRE = 0xFF7A1A;

    /** Glifos COMUNS: elemento + tier os põem no caminho do elemento (Arcano: por nível) e nos sorteios de pergaminhos. */
    public static List<EffectGlyph> all() {
        return List.of(new FairyLaw(), new SeriousPunch(), new Hinokami());
    }

    // =====================================================================================
    //  Lei das Fadas
    // =====================================================================================
    /**
     * EFEITO (Luz, tier 4). Só julga INIMIGOS de verdade (monstros, chefes, quem ataca você ou um aliado, jogadores
     * rivais): bichos, aldeões e aliados no meio da área não sentem nada. No inimigo: um pilar de luz dourada, dano 12
     * de LUZ (×1,25 se você o enxerga; chefes ×0,7). Com Si Mesmo: a luz cobre um raio de 8 (Expandir +) à sua volta
     * e julga todo inimigo que você enxerga (até 20). Com uma forma de área grande vira a Lei das Fadas de verdade.
     */
    public static class FairyLaw extends EffectGlyph {
        public static final float DAMAGE = 12F;

        public FairyLaw() {
            super("lg_fairy_law", 26);
            element(Element.LIGHT);
            tier(4);
            minCircle(4);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel l = ctx.level();
            if (LgUtil.self(ctx, target)) {
                if (!LgUtil.once(ctx, "fairy_law_self")) return;
                float r = Mth.clamp(stats.radius(8F), 3F, 24F);
                ElementFx.flash(l, c.getEyePosition(), 0xFFF6D8, r * 0.6F);
                ElementFx.shockwave(l, c.position().add(0, 0.2, 0), GOLD, r, 24, null);
                LgUtil.sound(l, c.position(), SoundEvents.BELL_RESONATE, 1.2F, 1.2F);
                int judged = 0;
                for (LivingEntity e : LgUtil.foesAround(ctx, c.position(), r, 20)) {
                    if (!c.hasLineOfSight(e)) continue;
                    judge(ctx, stats, c, e);
                    judged++;
                }
                LgUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_g.fairy_law", judged).withStyle(ChatFormatting.GOLD));
                return;
            }
            if (!LgUtil.isFoe(ctx, target) || !(target instanceof LivingEntity le)) return;
            if (LgUtil.once(ctx, "fairy_law_sound")) LgUtil.sound(l, c.position(), SoundEvents.BELL_RESONATE, 1.0F, 1.4F);
            judge(ctx, stats, c, le);
        }

        private static void judge(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity e) {
            float mult = (c.hasLineOfSight(e) ? 1.25F : 1F) * (Statuses.isBossLike(e) ? 0.7F : 1F);
            ctx.damage(e, DAMAGE * mult, stats, c, Element.LIGHT);
            // Pilares: no máx. 8 por conjuração (área grande não vira mar de partículas)
            for (int i = 0; i < 8; i++) {
                if (LgUtil.once(ctx, "fairy_pillar" + i)) {
                    ElementFx.pillar(ctx.level(), e.position(), GOLD, 0.6F, 6F, 16);
                    break;
                }
            }
        }
    }

    // =====================================================================================
    //  Soco Sério
    // =====================================================================================
    /**
     * EFEITO (Arcano, tier 4). No alvo: dano 10 + o seu Ataque (até 30; ×1,3 de mão vazia) e um arremesso enorme para
     * longe de você (chefes quase não saem do lugar), com anel de choque. Com Si Mesmo: o soco vira uma onda em cone à
     * frente (16 blocos, Expandir +; atravessa paredes; até 16 inimigos) e cava um túnel curto de 10 blocos (via
     * {@link Destruction}: respeita cidades, torres, masmorras e a regra de jogo); se estiver chovendo no mundo normal,
     * o soco ABRE O CÉU (tempo limpo por 5 min).
     */
    public static class SeriousPunch extends EffectGlyph {
        public static final float DAMAGE = 10F;

        public SeriousPunch() {
            super("lg_serious_punch", 24);
            element(Element.ARCANE);
            tier(4);
            minCircle(4);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel l = ctx.level();
            if (LgUtil.self(ctx, target)) {
                if (LgUtil.once(ctx, "serious_punch_cone")) cone(ctx, stats, c);
                return;
            }
            if (!(target instanceof LivingEntity le)) return;
            Vec3 dir = le.position().subtract(c.position()).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1.0E-4 ? c.getLookAngle() : dir.normalize();
            punch(ctx, stats, c, le, dir);
            ElementFx.shockwave(l, LgUtil.center(le), 0xFFFFFF, 2.2F, 10, dir);
            if (LgUtil.once(ctx, "serious_punch_sound")) {
                LgUtil.sound(l, le.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.4F, 0.6F);
                LgUtil.sound(l, le.position(), SoundEvents.GENERIC_EXPLODE, 0.8F, 1.3F);
            }
        }

        private static float damageOf(LivingEntity c) {
            double atk = c.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? c.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1.0;
            return (float) (DAMAGE + Math.min(30.0, atk)) * (c.getMainHandItem().isEmpty() ? 1.3F : 1F);
        }

        private static void punch(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity e, Vec3 dir) {
            ctx.damage(e, damageOf(c), stats, c, Element.ARCANE);
            boolean boss = Statuses.isBossLike(e);
            e.knockback(boss ? 0.4F : 2.0F, -dir.x, -dir.z);
            e.setDeltaMovement(e.getDeltaMovement().add(0, boss ? 0.05 : 0.35, 0));
            e.hurtMarked = true;
        }

        private static void cone(SpellContext ctx, SpellStats stats, LivingEntity c) {
            ServerLevel l = ctx.level();
            Vec3 dir = c.getLookAngle();
            float len = Mth.clamp(stats.radius(16F), 8F, 32F);
            Vec3 from = c.getEyePosition().add(0, -0.4, 0).add(dir.scale(0.8));
            Vec3 end = from.add(dir.scale(len));
            List<LivingEntity> hit = new ArrayList<>();
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(len * 0.15 + 2),
                    e -> LgUtil.isFoe(ctx, e))) {
                Vec3 ec = LgUtil.center(e);
                double t = ec.subtract(from).dot(dir);
                if (t < -1 || t > len) continue;
                double perp = ec.distanceTo(from.add(dir.scale(Math.max(0, t))));
                if (perp <= 1.5 + 0.15 * Math.max(0, t) + e.getBbWidth() * 0.5) hit.add(e);
                if (hit.size() >= 16) break;
            }
            for (LivingEntity e : hit) punch(ctx, stats, c, e, dir);
            Destruction.line(l, from, from.add(dir.scale(Math.min(len, 10F))), 2.5F, 2.5F, c, Destruction.Mode.BREAK);
            ElementFx.flash(l, from, 0xFFFFFF, 2.5F);
            for (int i = 1; i <= 4; i++) {
                ElementFx.shockwave(l, from.add(dir.scale(len * i / 5.0)), 0xFFFFFF, 1.2F + i * 1.1F, 10 + i * 2, dir);
            }
            ElementFx.shake(l, from, 40, 1.4F, 14);
            LgUtil.sound(l, from, SoundEvents.GENERIC_EXPLODE, 1.6F, 0.6F);
            LgUtil.sound(l, from, SoundEvents.PLAYER_ATTACK_STRONG, 1.6F, 0.5F);
            // "O soco abriu as nuvens": só no mundo normal, só se estiver chovendo
            if (c instanceof ServerPlayer && l.dimension() == Level.OVERWORLD && l.isRaining()) {
                l.setWeatherParameters(6000, 0, false, false);
                LgUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_g.serious_punch_sky").withStyle(ChatFormatting.AQUA));
            }
        }
    }

    // =====================================================================================
    //  Dança do Deus do Fogo (Hinokami Kagura)
    // =====================================================================================
    /**
     * EFEITO (Fogo, tier 3). No alvo: três golpes de chamas seguidos — Dança (6), Céu Azul Claro (4, 0,3 s depois) e
     * Halo Solar (6, 0,6 s depois) — cada um com Em Chamas 4 s; mortos-vivos ("demônios") levam ×1,5. Com Si Mesmo:
     * você avança até 6 blocos (Acelerar +; para antes de paredes) cortando quem estiver no caminho, depois o arco à
     * frente e o halo em volta. Não põe fogo em blocos.
     */
    public static class Hinokami extends EffectGlyph {
        public static final float DANCE = 6F, ARC = 4F, HALO = 6F;

        public Hinokami() {
            super("lg_hinokami", 20);
            element(Element.FIRE);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return DANCE + ARC + HALO; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel l = ctx.level();
            if (LgUtil.self(ctx, target)) {
                if (LgUtil.once(ctx, "hinokami_self")) dash(ctx, stats, c);
                return;
            }
            if (!(target instanceof LivingEntity le) || !LgUtil.once(ctx, "hinokami:" + le.getId())) return;
            Vec3 dir = LgUtil.center(le).subtract(c.getEyePosition());
            Vec3 d = dir.lengthSqr() < 1.0E-4 ? c.getLookAngle() : dir.normalize();
            strike(ctx, stats, c, le, DANCE, d, FIRE);
            BossKit.schedule(l, 6, () -> {
                if (le.isAlive() && le.level() == l) strike(ctx, stats, c, le, ARC, new Vec3(d.x, 0.8, d.z), 0xFFC04A);
            });
            BossKit.schedule(l, 12, () -> {
                if (!le.isAlive() || le.level() != l) return;
                strike(ctx, stats, c, le, HALO, d, FIRE);
                ElementFx.shockwave(l, LgUtil.center(le), FIRE, 2.2F, 10, null);
            });
        }

        private static void strike(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity e, float base, Vec3 dir, int color) {
            float undead = e.getType().is(EntityTypeTags.UNDEAD) ? 1.5F : 1F;
            ctx.damage(e, base * undead, stats, c, Element.FIRE);
            e.igniteForSeconds(4F);
            ElementFx.slash(ctx.level(), LgUtil.center(e), dir, Element.FIRE, color, 2.4F, 3);
            LgUtil.sound(ctx.level(), e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.0F);
        }

        private static void dash(SpellContext ctx, SpellStats stats, LivingEntity c) {
            ServerLevel l = ctx.level();
            Vec3 look = c.getLookAngle();
            Vec3 dir = new Vec3(look.x, Mth.clamp(look.y, -0.3, 0.3), look.z);
            dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
            float dist = Mth.clamp(6F * stats.speedMult(), 3F, 10F);
            Vec3 start = c.position();
            Vec3 waist = start.add(0, 0.9, 0);
            BlockHitResult bh = l.clip(new ClipContext(waist, waist.add(dir.scale(dist)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, c));
            double go = bh.getType() == HitResult.Type.BLOCK ? Math.max(0, bh.getLocation().distanceTo(waist) - 0.7) : dist;
            Vec3 to = start.add(dir.scale(go));
            for (LivingEntity e : LgUtil.foesOnLine(ctx, waist, to.add(0, 0.9, 0), 1.3, 8)) strike(ctx, stats, c, e, DANCE, dir, FIRE);
            if (go > 0.5) ArcaneUtil.teleport(c, to, c.getYRot(), c.getXRot());
            ElementFx.slash(l, waist.add(to.add(0, 0.9, 0)).scale(0.5), dir, Element.FIRE, FIRE, (float) Math.max(2.5, go), 3);
            int pts = Math.min(12, (int) (go * 2));
            for (int i = 0; i <= pts; i++) {
                Vec3 p = waist.add(dir.scale(go * i / Math.max(1, pts)));
                l.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.2, 0.3, 0.2, 0.02);
            }
            LgUtil.sound(l, start, SoundEvents.FIRECHARGE_USE, 1.1F, 0.8F);
            final Vec3 fwd = dir;
            BossKit.schedule(l, 6, () -> {
                if (!c.isAlive() || c.level() != l) return;
                Vec3 eye = c.getEyePosition();
                ElementFx.slash(l, eye.add(fwd.scale(1.5)), fwd, Element.FIRE, 0xFFC04A, 3.5F, 3);
                for (LivingEntity e : LgUtil.foesAround(ctx, eye, 4.0, 8)) {
                    if (LgUtil.center(e).subtract(eye).normalize().dot(fwd) >= 0.3) strike(ctx, stats, c, e, ARC, fwd, 0xFFC04A);
                }
            });
            BossKit.schedule(l, 12, () -> {
                if (!c.isAlive() || c.level() != l) return;
                Vec3 p = c.position();
                ElementFx.shockwave(l, p.add(0, 0.9, 0), FIRE, 4F, 12, null);
                l.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.9, p.z, 20, 2.0, 0.3, 2.0, 0.02);
                LgUtil.sound(l, p, SoundEvents.BLAZE_SHOOT, 1.2F, 0.7F);
                for (LivingEntity e : LgUtil.foesAround(ctx, p, 4.0, 10)) {
                    Vec3 out = e.position().subtract(p);
                    strike(ctx, stats, c, e, HALO, out.lengthSqr() > 1.0E-4 ? out.normalize() : fwd, FIRE);
                    if (out.lengthSqr() > 1.0E-4) e.knockback(0.6F, -out.x, -out.z);
                }
            });
        }
    }

    private HeroTechniques() {}
}
