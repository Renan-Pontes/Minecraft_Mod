package com.renanpontes.isekaimahou.feature.legends_j.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_j.LjFx;
import com.renanpontes.isekaimahou.feature.legends_j.LjUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * As correntes do Kurapika (homenagem a Hunter x Hunter), separadas em três glifos comuns:
 * <ul>
 *   <li>{@link ChainJail} Corrente Prisão — prende o alvo; muito mais forte contra "Aranhas" e criaturas das trevas.</li>
 *   <li>{@link HolyChain} Corrente Sagrada — cura e tira um efeito ruim.</li>
 *   <li>{@link DowsingChain} Corrente Rastreadora — o pêndulo que acha inimigos (e não deixa mentir).</li>
 * </ul>
 */
public final class ChainEffects {

    /** "Aranha" (Trupe Fantasma) ou criatura das trevas: aranhas/artrópodes e mortos-vivos. */
    static boolean spiderOrDark(LivingEntity e) {
        return e.getType().is(EntityTypeTags.ARTHROPOD) || e.getType().is(EntityTypeTags.UNDEAD);
    }

    /** Ponto de saída da corrente: a mão do conjurador (ou a origem do estágio). */
    static Vec3 hand(SpellContext ctx) {
        LivingEntity c = ctx.caster();
        if (c == null || ctx.hasOrigin()) return ctx.eyePos();
        return c.getEyePosition().add(c.getLookAngle().scale(0.5)).add(0, -0.35, 0);
    }

    // =====================================================================================
    //  Corrente Prisão (Chain Jail)
    // =====================================================================================
    /**
     * Correntes saem do chão e da sua mão e prendem o inimigo: dano 2, Enraizado 3 s (Estender +) e Silenciado 1 s.
     * Contra ARANHAS e criaturas das TREVAS (artrópodes, mortos-vivos) — "Tempo do Imperador", olhos escarlates —:
     * dano 6, preso o DOBRO do tempo e Silenciado 3 s. Chefes: no máximo 1 s preso. Em Si Mesmo: prende o inimigo na
     * mira (20 blocos).
     */
    public static class ChainJail extends EffectGlyph {
        public ChainJail() {
            super("chain_jail", 12);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return 2F; }

        @Override
        public int hintDuration() { return 60; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            LivingEntity t = target instanceof LivingEntity le && !LjUtil.self(ctx, target) ? le : null;
            if (t == null && c != null && LjUtil.once(ctx, "chain_jail_aim")) t = LjUtil.lookTarget(c, 20, e -> LjUtil.isFoe(ctx, e));
            if (t == null || !LjUtil.isFoe(ctx, t)) return;
            ServerLevel l = ctx.level();
            boolean emperor = spiderOrDark(t);
            boolean boss = Statuses.isBossLike(t);
            int ticks = stats.duration(60) * (emperor ? 2 : 1);
            if (boss) ticks = Math.min(ticks, 20);
            ctx.damage(t, emperor ? 6F : 2F, stats, c, Element.ARCANE);
            Statuses.apply(t, Statuses.Kind.ROOTED, 1, ticks, c, 1F);
            Statuses.apply(t, Statuses.Kind.SILENCED, 1, emperor ? 60 : 20, c, 1F);
            t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
            t.hurtMarked = true;
            // Visual: corrente da mão + 3 correntes do chão, reforçadas enquanto o alvo está preso (máx. 4 vezes)
            Vec3 from = hand(ctx);
            int rgb = emperor ? 0xFF3040 : LjFx.CHAIN;
            int pulses = Math.min(4, Math.max(1, ticks / 15));
            for (int i = 0; i < pulses; i++) {
                final LivingEntity bound = t;
                SpellScheduler.schedule(i * 15, () -> {
                    if (!bound.isAlive() || bound.level() != l) return;
                    Vec3 tc = LjUtil.center(bound);
                    for (int k = 0; k < 3; k++) {
                        double a = k * Math.PI * 2 / 3 + l.getGameTime() * 0.1;
                        Vec3 g = bound.position().add(Math.cos(a) * 1.6, 0.05, Math.sin(a) * 1.6);
                        LjFx.chain(l, g, tc, rgb);
                    }
                    LjFx.coil(l, bound.position(), bound.getBbWidth() * 0.7 + 0.15, bound.getBbHeight() * 0.8, rgb);
                });
            }
            if (c != null && LjUtil.once(ctx, "chain_jail_hand")) LjFx.chain(l, from, LjUtil.center(t), LjFx.CHAIN);
            LjUtil.sound(l, t.position(), SoundEvents.CHAIN_PLACE, 1.2F, 0.7F);
            LjUtil.sound(l, t.position(), SoundEvents.CHAIN_HIT, 1.0F, 0.6F);
            if (emperor && LjUtil.once(ctx, "chain_jail_emperor")) {
                LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.emperor_time").withStyle(ChatFormatting.RED));
                if (c != null) ElementFx.sendFar(l, ElementFx.glow(0xFF2030, 0.7F), c.getX(), c.getEyeY(), c.getZ(), 4, 0.15, 0.05, 0.15, 0);
            }
        }
    }

    // =====================================================================================
    //  Corrente Sagrada (Holy Chain)
    // =====================================================================================
    /**
     * A corrente do polegar se enrola no aliado (ou em você) e cura 5, tirando 1 efeito ruim (+1 por Amplificar).
     */
    public static class HolyChain extends EffectGlyph {
        public static final int COLOR = 0xFFE27A;

        public HolyChain() {
            super("holy_chain", 10);
            element(Element.LIGHT);
            tier(2);
            minCircle(2);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public float baseHeal() { return 5F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity t) || !t.isAlive()) return;
            ServerLevel l = ctx.level();
            ctx.heal(t, 5F, stats);
            int clears = 1 + Math.max(0, stats.amplify);
            for (MobEffectInstance inst : List.copyOf(t.getActiveEffects())) {
                if (clears <= 0) break;
                if (!inst.getEffect().value().isBeneficial()) {
                    t.removeEffect(inst.getEffect());
                    clears--;
                }
            }
            LjFx.coil(l, t.position(), t.getBbWidth() * 0.7 + 0.2, t.getBbHeight(), COLOR);
            if (!LjUtil.self(ctx, target)) LjFx.chain(l, hand(ctx), LjUtil.center(t), COLOR);
            LjUtil.sound(l, t.position(), SoundEvents.CHAIN_PLACE, 0.8F, 1.5F);
            LjUtil.sound(l, t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
        }
    }

    // =====================================================================================
    //  Corrente Rastreadora (Dowsing Chain)
    // =====================================================================================
    /**
     * Em Si Mesmo: o pêndulo balança — inimigos num raio de 24 (Expandir +, máx. 48; até 20) brilham por 8 s
     * (Estender +) e a barra de ação diz quantos são e para onde está o mais perto (uma corrente aponta para ele).
     * Num inimigo: "a corrente não mente" — ele brilha 15 s e você vê a vida, a armadura e quantos efeitos ele tem.
     */
    public static class DowsingChain extends EffectGlyph {
        public DowsingChain() {
            super("dowsing_chain", 6);
            element(Element.ARCANE);
            tier(2);
            minCircle(1);
        }

        @Override
        public float baseDamage() { return 0F; }

        @Override
        public float hintRadius() { return 24F; }

        @Override
        public int hintDuration() { return 160; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            ServerLevel l = ctx.level();
            if (LjUtil.self(ctx, target)) {
                if (c == null || !LjUtil.once(ctx, "dowse")) return;
                dowse(ctx, c, stats);
                return;
            }
            if (!(target instanceof LivingEntity t) || !LjUtil.once(ctx, "dowse@" + t.getUUID())) return;
            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, stats.duration(300), 0, false, false), c);
            LjFx.chain(l, hand(ctx), LjUtil.center(t), LjFx.CHAIN);
            LjUtil.sound(l, t.position(), SoundEvents.CHAIN_STEP, 1.0F, 1.4F);
            if (c != null && LjUtil.once(ctx, "dowse_read")) {
                LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.dowse_read", t.getDisplayName(),
                        Mth.ceil(t.getHealth()), Mth.ceil(t.getMaxHealth()), t.getArmorValue(), t.getActiveEffects().size())
                        .withStyle(ChatFormatting.AQUA));
            }
        }

        private static void dowse(SpellContext ctx, LivingEntity c, SpellStats stats) {
            ServerLevel l = ctx.level();
            float r = Mth.clamp(stats.radius(24F), 12F, 48F);
            List<LivingEntity> foes = LjUtil.foesAround(ctx, c.position(), r, 20);
            int ticks = stats.duration(160);
            for (LivingEntity e : foes) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), c);
            // O pêndulo: uma corrente curta pendurada na mão, balançando
            Vec3 hand = hand(ctx);
            LjFx.chain(l, hand, hand.add(0, -0.9, 0), LjFx.CHAIN);
            LjUtil.sound(l, hand, SoundEvents.CHAIN_STEP, 1.0F, 1.2F);
            LjUtil.sound(l, hand, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.8F);
            if (foes.isEmpty()) {
                LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.dowse_none").withStyle(ChatFormatting.GRAY));
                return;
            }
            LivingEntity near = foes.get(0);
            Vec3 to = near.position().subtract(c.position());
            Vec3 dir = to.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : to.normalize();
            LjFx.chain(l, hand, hand.add(dir.scale(Math.min(6, to.length()))), 0xFFE27A);
            LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.dowse_found", foes.size(),
                    Component.translatable("message.isekai_mahou.legends_j.dir." + compass(dir)), Mth.floor(to.length()))
                    .withStyle(ChatFormatting.AQUA));
        }

        /** Direção cardeal (n, ne, e, se, s, sw, w, nw) de um vetor (norte = −Z no Minecraft). */
        static String compass(Vec3 d) {
            double ang = Math.toDegrees(Math.atan2(d.x, -d.z));
            if (ang < 0) ang += 360;
            String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
            return dirs[(int) Math.round(ang / 45.0) % 8];
        }
    }

    private ChainEffects() {}
}
