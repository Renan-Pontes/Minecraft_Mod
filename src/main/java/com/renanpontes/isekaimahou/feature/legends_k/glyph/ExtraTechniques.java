package com.renanpontes.isekaimahou.feature.legends_k.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_k.LkFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;
import com.renanpontes.isekaimahou.feature.legends_k.state.Auras;
import com.renanpontes.isekaimahou.feature.legends_k.state.Bindings;
import com.renanpontes.isekaimahou.feature.legends_k.state.Guards;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Técnicas de anime extras do legends_k, uma por classe que ainda não tinha uma deste pacote:
 * <ul>
 *   <li>{@link ShadowToad} Sapo das Sombras (Jujutsu Kaisen — Dez Sombras) — Necromante;</li>
 *   <li>{@link MahoragaWheel} Roda de Mahoraga (Jujutsu Kaisen) — Bruxo;</li>
 *   <li>{@link DeadCalm} Calmaria (Demon Slayer — Respiração da Água, 11ª forma) — Cavaleiro;</li>
 *   <li>{@link ReturnByDeath} Retorno pela Morte (Re:Zero) — Bruxa;</li>
 *   <li>{@link FlowerField} Campo de Flores (Frieren) — Druida;</li>
 *   <li>{@link ShadowPossession} Possessão das Sombras (Naruto — Shikamaru) — Patrulheiro;</li>
 *   <li>{@link Aschente} Aschente (No Game No Life) — Apostador.</li>
 * </ul>
 */
public final class ExtraTechniques {

    // =====================================================================================
    //  Sapo das Sombras (Necromante)
    // =====================================================================================

    /**
     * Um sapo de sombra sai do chão e a língua puxa: num inimigo, ele é arrastado até a sua frente (dano 2; chefes não
     * saem do lugar, só ficam lentos). Em você: o sapo te puxa até onde você mira (até 16 blocos) — um gancho.
     */
    public static class ShadowToad extends EffectGlyph {
        public ShadowToad() {
            super("lk_shadow_toad", 12);
            origin(MagicPath.NECROMANCER);
            element(Element.SHADOW);
            tier(2);
            minCircle(2);
        }

        @Override
        public float baseDamage() { return 2F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !(target instanceof LivingEntity t)) return;
            ServerLevel l = ctx.level();
            if (LkUtil.self(ctx, target)) {
                if (!LkUtil.once(ctx, "toad_self")) return;
                Vec3 eye = c.getEyePosition();
                Vec3 end = eye.add(c.getLookAngle().scale(16));
                BlockHitResult bh = l.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
                if (bh.getType() != HitResult.Type.BLOCK) {
                    LkUtil.say(c, "toad_nothing", ChatFormatting.GRAY);
                    return;
                }
                Vec3 dest = bh.getLocation();
                if (LkUtil.wardCrossing(l, c.position(), dest, c)) return;
                tongue(l, dest, c);
                Vec3 d = dest.subtract(c.position());
                Vec3 v = d.scale(0.2).add(0, 0.35, 0);
                if (v.length() > 2.2) v = v.normalize().scale(2.2);
                LkUtil.impulse(c, v);
                c.fallDistance = 0;
                return;
            }
            if (!LkUtil.isFoe(ctx, t)) return;
            ctx.damage(t, 2F, stats, c);
            tongue(l, c.position().add(c.getLookAngle().scale(1.2)).add(0, 0.6, 0), t);
            if (LkUtil.boss(t) || (t instanceof Player p && !LkUtil.isFoe(ctx, p))) {
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                return;
            }
            Vec3 front = c.position().add(new Vec3(c.getLookAngle().x, 0, c.getLookAngle().z).normalize().scale(1.6));
            if (LkUtil.wardCrossing(l, t.position(), front, c)) return;
            Vec3 v = front.subtract(t.position()).scale(0.28).add(0, 0.35, 0);
            if (v.length() > 1.9) v = v.normalize().scale(1.9);
            LkUtil.impulse(t, v);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
        }

        /** A poça de sombra (o sapo) num ponto e a língua até o alvo. */
        private static void tongue(ServerLevel l, Vec3 mouth, Entity other) {
            LkFx.ring(l, mouth.add(0, 0.05, 0), 0.7, LkFx.SHADOW, 0.4F, 14);
            l.sendParticles(ParticleTypes.SQUID_INK, mouth.x, mouth.y + 0.2, mouth.z, 6, 0.3, 0.1, 0.3, 0.01);
            LkFx.line(l, mouth.add(0, 0.3, 0), LkUtil.center(other), 0xD04A6A, 0.3F);
            LkUtil.sound(l, mouth, SoundEvents.FROG_TONGUE, 1.0F, 0.6F);
        }
    }

    // =====================================================================================
    //  Roda de Mahoraga (Bruxo)
    // =====================================================================================

    /**
     * A roda de oito raios aparece sobre a cabeça (você ou um aliado) por 30 s (Estender +). Cada golpe de um TIPO de
     * dano gira a roda para aquele tipo: o próximo do mesmo tipo dói 20% menos, até −80% no 4º giro (fogo, flecha,
     * mordida, magia... cada um conta separado). O pacto com o shikigami mais forte.
     */
    public static class MahoragaWheel extends EffectGlyph {
        public MahoragaWheel() {
            super("lk_mahoraga_wheel", 22);
            origin(MagicPath.WARLOCK);
            element(Element.SHADOW);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public float baseDamage() { return 0F; }

        @Override
        public int hintDuration() { return 600; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity t) || !t.isAlive()) return;
            Guards.wheel(t, stats.duration(600));
            LkFx.wheel(ctx.level(), t, 0);
            LkUtil.sound(ctx.level(), t.position(), SoundEvents.BELL_BLOCK, 0.8F, 0.6F);
            LkUtil.say(t, "wheel_on", ChatFormatting.GOLD);
        }
    }

    // =====================================================================================
    //  Calmaria (Cavaleiro)
    // =====================================================================================

    /**
     * Respiração da Água, 11ª forma: Calmaria. Use com Si Mesmo. Por 3 s (Estender +) você fica num campo de água
     * parada (raio ~2,5): todo golpe corpo a corpo e todo projétil que chega é aparado e o atacante leva um corte de
     * volta (dano 6 × poder). Enquanto dura, você anda devagar (como quem não se move).
     */
    public static class DeadCalm extends EffectGlyph {
        public DeadCalm() {
            super("lk_dead_calm", 18);
            origin(MagicPath.KNIGHT);
            element(Element.WATER);
            tier(4);
            minCircle(4);
        }

        @Override
        public float baseDamage() { return 6F; }

        @Override
        public int hintDuration() { return 60; }

        @Override
        public float hintRadius() { return 2.5F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !LkUtil.self(ctx, target) || !LkUtil.once(ctx, "calm")) return;
            int ticks = stats.duration(60);
            Guards.calm(c, ticks, LkUtil.power(ctx, stats));
            c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 2, false, false));
            ServerLevel l = ctx.level();
            LkFx.ring(l, c.position().add(0, 0.05, 0), 2.6, LkFx.CALM, 0.35F, 28);
            ElementFx.shockwave(l, c.position().add(0, 0.05, 0), LkFx.CALM, 2.6F, 20, null);
            LkUtil.sound(l, c.position(), SoundEvents.PLAYER_SPLASH, 0.6F, 0.6F);
            LkUtil.say(c, "calm_on", ChatFormatting.AQUA);
        }
    }

    // =====================================================================================
    //  Retorno pela Morte (Bruxa)
    // =====================================================================================

    /**
     * Use com Si Mesmo: marca um PONTO DE RETORNO (posição, vida e fome) por 60 s (Estender +). Se você morrer nesse
     * tempo, a morte é desfeita: a tela apaga, você volta ao ponto com a vida que tinha e sem efeitos ruins. Depois de
     * salvar você, só pode ser usado de novo em 10 min. Só jogadores.
     */
    public static class ReturnByDeath extends EffectGlyph {
        public ReturnByDeath() {
            super("lk_return_by_death", 26);
            origin(MagicPath.WITCH);
            element(Element.SHADOW);
            tier(5);
            minCircle(6);
        }

        @Override
        public float baseDamage() { return 0F; }

        @Override
        public int hintDuration() { return 1200; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(ctx.caster() instanceof ServerPlayer sp) || !LkUtil.self(ctx, target) || !LkUtil.once(ctx, "rbd")) return;
            int ticks = stats.duration(1200);
            Guards.checkpoint(sp, ticks);
            ServerLevel l = ctx.level();
            LkFx.swirl(l, LkUtil.center(sp), 1.0, LkFx.RBD, 24);
            ElementFx.runeCircle(l, sp.position().add(0, 0.05, 0), LkFx.RBD, 1.2F, 40, 2);
            LkUtil.sound(l, sp.position(), SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.2F);
            LkUtil.say(sp, "rbd_set", ChatFormatting.LIGHT_PURPLE, ticks / 20);
        }
    }

    // =====================================================================================
    //  Campo de Flores (Druida)
    // =====================================================================================

    /**
     * A magia favorita da Frieren: um campo de flores (raio 5, Expandir +) por 10 s (Estender +), em você, num aliado
     * ou onde a magia bater no chão. Aliados lá dentro recuperam 1 de vida por segundo; monstros comuns se acalmam
     * (perdem o alvo e andam devagar). Nenhum bloco é colocado — só pétalas.
     */
    public static class FlowerField extends EffectGlyph {
        public FlowerField() {
            super("lk_flower_field", 16);
            origin(MagicPath.DRUID);
            element(Element.NATURE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float baseDamage() { return 0F; }

        @Override
        public float baseHeal() { return 1F; }

        @Override
        public int hintDuration() { return 200; }

        @Override
        public float hintRadius() { return 5F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!LkUtil.once(ctx, "flowers")) return;
            plant(ctx, target.position(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!LkUtil.once(ctx, "flowers")) return;
            plant(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().above()), stats);
        }

        private static void plant(SpellContext ctx, Vec3 at, SpellStats stats) {
            float r = stats.radius(5F);
            Auras.field(ctx, at, stats.duration(200), r, 1F * Math.min(3F, LkUtil.power(ctx, stats)));
            ServerLevel l = ctx.level();
            l.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, at.y + 1, at.z, 16, r * 0.5, 0.5, r * 0.5, 0.0);
            ElementFx.runeCircle(l, at.add(0, 0.05, 0), LkFx.FLOWER, r, 60, 1);
            LkUtil.sound(l, at, SoundEvents.AZALEA_LEAVES_PLACE, 1.0F, 1.2F);
        }
    }

    // =====================================================================================
    //  Possessão das Sombras (Patrulheiro)
    // =====================================================================================

    /**
     * A sombra do conjurador se estica e prende o inimigo: por 4 s (Estender +) ele repete os SEUS movimentos — anda
     * quando você anda, para quando você para (ande para trás e ele recua). Chefes: 1 s. Jogador inimigo (PvP): 2 s.
     * Quebra a 16 blocos.
     */
    public static class ShadowPossession extends EffectGlyph {
        public ShadowPossession() {
            super("lk_shadow_possession", 16);
            origin(MagicPath.RANGER);
            element(Element.SHADOW);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return 0F; }

        @Override
        public int hintDuration() { return 80; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !(target instanceof LivingEntity t) || LkUtil.self(ctx, target) || !LkUtil.isFoe(ctx, t)) return;
            int ticks = t instanceof Player ? 40 : LkUtil.boss(t) ? 20 : stats.duration(80);
            Bindings.possess(c, t, ticks);
            ServerLevel l = ctx.level();
            LkFx.line(l, c.position().add(0, 0.05, 0), t.position().add(0, 0.05, 0), LkFx.SHADOW, 0.45F);
            LkFx.ring(l, t.position().add(0, 0.05, 0), Math.max(0.6, t.getBbWidth()), LkFx.SHADOW, 0.4F, 14);
            LkUtil.sound(l, t.position(), SoundEvents.SCULK_CLICKING, 1.0F, 0.6F);
            LkUtil.say(c, "possession_on", ChatFormatting.DARK_PURPLE, t.getDisplayName());
        }
    }

    // =====================================================================================
    //  Aschente (Apostador)
    // =====================================================================================

    /**
     * "Aschente!" — os Dez Pactos: você desafia o inimigo para um jogo de 8 s (Estender +). Quem causar MAIS dano ao
     * outro nesse tempo vence. Se você vencer, o alvo perde 25% da vida máxima (chefes 5%) e fica com Fraqueza II;
     * se perder, você fica com Fraqueza e perde 20% do mana. Empate: nada. Um jogo por vez.
     */
    public static class Aschente extends EffectGlyph {
        public Aschente() {
            super("lk_aschente", 14);
            origin(MagicPath.GAMBLER);
            element(Element.LIGHT);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return 0F; }

        @Override
        public int hintDuration() { return 160; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !(target instanceof LivingEntity t) || LkUtil.self(ctx, target) || !LkUtil.isFoe(ctx, t)) return;
            if (!LkUtil.once(ctx, "aschente")) return;
            int ticks = stats.duration(160);
            Bindings.aschente(c, t, ticks, LkUtil.power(ctx, stats));
            ServerLevel l = ctx.level();
            LkFx.ring(l, t.position().add(0, t.getBbHeight() + 0.4, 0), 0.5, LkFx.ASCHENTE, 0.35F, 12);
            LkFx.ring(l, c.position().add(0, c.getBbHeight() + 0.4, 0), 0.5, LkFx.ASCHENTE, 0.35F, 12);
            LkFx.line(l, LkUtil.center(c), LkUtil.center(t), LkFx.ASCHENTE, 0.2F);
            LkUtil.sound(l, c.position(), SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, 0.7F, 1.4F);
            LkUtil.say(c, "aschente_start", ChatFormatting.LIGHT_PURPLE, ticks / 20);
            LkUtil.say(t, "aschente_start", ChatFormatting.LIGHT_PURPLE, ticks / 20);
        }
    }

    private ExtraTechniques() {}
}
