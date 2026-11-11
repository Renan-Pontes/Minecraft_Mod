package com.renanpontes.isekaimahou.feature.origins_a.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.origins_a.OaUtil;
import com.renanpontes.isekaimahou.feature.origins_a.rune.CircleTravel;
import com.renanpontes.isekaimahou.feature.origins_a.rune.ExplosiveSealEntity;
import com.renanpontes.isekaimahou.feature.origins_a.rune.TempEnchant;
import com.renanpontes.isekaimahou.feature.origins_a.rune.WardEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * RUNAS — Magia de Origem do Mago. Efeitos arcanos usáveis em magia de qualquer elemento.
 * As runas de chão (Selo, Círculo, Ward) nascem onde a forma acerta um bloco (Si Mesmo = sob os seus pés);
 * acertando uma criatura, a runa nasce sob ela.
 */
public final class RuneGlyphs {

    public static void register() {
        GlyphRegistry.register(new ExplosiveSeal());
        GlyphRegistry.register(new TeleportCircle());
        GlyphRegistry.register(new ReturnRune());
        GlyphRegistry.register(new Ward());
        GlyphRegistry.register(new TemporaryEnchant());
    }

    /** Base das runas de chão: coloca a runa no bloco atingido ou sob a criatura atingida (nunca duas vezes no conjurador). */
    abstract static class GroundRune extends EffectGlyph {
        GroundRune(String id, int cost) {
            super(id, cost);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            place(ctx, OaUtil.runeSpot(ctx.level(), hit), stats, false);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            // Em si mesmo, o onBlock (chão sob os pés) já cuida: evita runa dupla
            if (target == ctx.caster()) return;
            place(ctx, OaUtil.ground(ctx.level(), target.position()), stats, true);
        }

        abstract void place(SpellContext ctx, BlockPos at, SpellStats stats, boolean onCreature);
    }

    // =====================================================================================
    /** Selo Explosivo (T1): runa-armadilha que explode quando um inimigo pisa. */
    public static class ExplosiveSeal extends GroundRune {
        public ExplosiveSeal() {
            super("explosive_seal", 10);
            element(Element.ARCANE);
            origin(MagicPath.MAGE);
            tier(1);
        }

        @Override
        void place(SpellContext ctx, BlockPos at, SpellStats stats, boolean onCreature) {
            // Grudado numa criatura, arma rápido (quase uma mina instantânea)
            ExplosiveSealEntity.place(ctx, at, stats, onCreature ? 6 : 20);
        }
    }

    // =====================================================================================
    /** Círculo de Teleporte (T2): até 2 círculos fixos ligados; pisar num leva ao outro. */
    public static class TeleportCircle extends GroundRune {
        public TeleportCircle() {
            super("teleport_circle", 15);
            element(Element.ARCANE);
            origin(MagicPath.MAGE);
            tier(2);
        }

        @Override
        void place(SpellContext ctx, BlockPos at, SpellStats stats, boolean onCreature) {
            if (onCreature) return; // círculo é desenhado no chão escolhido, não em criaturas
            CircleTravel.createCircle(ctx, at);
        }
    }

    // =====================================================================================
    /**
     * Runa de Retorno (T3): concentração de 3s; leva ao último Círculo de Teleporte usado/criado, ou à cama.
     * Com Inverter em um inimigo: bane a criatura para longe (teleporte aleatório).
     */
    public static class ReturnRune extends EffectGlyph {
        public ReturnRune() {
            super("return_rune", 18);
            element(Element.ARCANE);
            origin(MagicPath.MAGE);
            tier(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target instanceof ServerPlayer sp && ctx.isAlly(sp)) {
                if (!CircleTravel.recalling(sp)) CircleTravel.startRecall(sp, CircleTravel.RECALL_TICKS);
                return;
            }
            if (stats.inverted && target instanceof LivingEntity le && !ctx.isAlly(le)) banish(ctx.level(), le, stats);
        }

        private static void banish(ServerLevel level, LivingEntity e, SpellStats stats) {
            Vec3 from = e.position();
            // Chefes resistem ao banimento (tirar o chefe da arena quebraria a luta)
            if (e.getType().is(Tags.EntityTypes.BOSSES)) {
                level.sendParticles(ParticleTypes.SMOKE, from.x, from.y + e.getBbHeight() * 0.5, from.z, 12, 0.4, 0.4, 0.4, 0.02);
                return;
            }
            double range = 12 + 6 * stats.power();
            for (int i = 0; i < 16; i++) {
                double x = e.getX() + (e.getRandom().nextDouble() - 0.5) * 2 * range;
                double y = e.getY() + e.getRandom().nextInt(9) - 4;
                double z = e.getZ() + (e.getRandom().nextDouble() - 0.5) * 2 * range;
                if (e.randomTeleport(x, y, z, true)) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.05);
                    level.playSound(null, from.x, from.y, from.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.9F, 0.8F);
                    return;
                }
            }
        }
    }

    // =====================================================================================
    /** Ward (T4): área protegida por 10 minutos onde monstros não entram (são empurrados). */
    public static class Ward extends GroundRune {
        public Ward() {
            super("ward", 25);
            element(Element.ARCANE);
            origin(MagicPath.MAGE);
            tier(4);
        }

        @Override
        void place(SpellContext ctx, BlockPos at, SpellStats stats, boolean onCreature) {
            // Ward protege quem conjura: acertando uma criatura, a barreira nasce sob o conjurador
            if (onCreature && ctx.caster() != null) at = OaUtil.ground(ctx.level(), ctx.caster().position());
            WardEntity.place(ctx, at, stats);
        }
    }

    // =====================================================================================
    /**
     * Encantamento Temporário (T5, lendário): encanta a arma/ferramenta da mão com encantamentos do elemento
     * da magia por 5 minutos (Estender aumenta; Amplificar sobe os níveis).
     */
    public static class TemporaryEnchant extends EffectGlyph {
        public TemporaryEnchant() {
            super("temp_enchant", 45);
            element(Element.ARCANE);
            origin(MagicPath.MAGE);
            legendary();
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof Player p)) return;
            ItemStack stack = p.getMainHandItem();
            int bonus = Math.max(0, stats.amplify) + (stats.charge > 0 ? 1 : 0);
            int duration = stats.duration(6000);
            TempEnchant.Result result = TempEnchant.apply(p, stack, ctx.element(), bonus, duration);
            if (result != TempEnchant.Result.OK) {
                OaUtil.actionbar(p, result == TempEnchant.Result.ALREADY_STRONGER ? "enchant_maxed" : "enchant_fail");
                ctx.level().sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 8, 0.2, 0.2, 0.2, 0.01);
                return;
            }
            ServerLevel level = ctx.level();
            level.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 60, 0.5, 0.6, 0.5, 0.8);
            ElementFx.burst(level, p.position().add(0, 1.0, 0), ctx.element(), 16, 0.4);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
            OaUtil.actionbar(p, "enchant_ok", stack.getHoverName(), Math.max(1, Math.round(duration / 1200.0F)));
        }
    }

    private RuneGlyphs() {}
}
