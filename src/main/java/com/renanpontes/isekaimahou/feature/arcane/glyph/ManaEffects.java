package com.renanpontes.isekaimahou.feature.arcane.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneRegistries;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.arcane.StatusAuras;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Efeitos arcanos de mana e anti-magia: Escudo, Refletir, Doar Mana, Roubar Mana, Dissipar, Contramágica. */
public final class ManaEffects {

    private static final int MANA_BLUE = 0x6FB8FF;
    /** Doar Mana: mana transferida por conjuração × poder (v0.8: 20; ×3 com a mana da v1.0). */
    static final float GIFT = 20F * ResourceTraining.MANA_SCALE;
    /** Roubar Mana: mana roubada de jogadores × poder (v0.8: 18; ×3 com a mana da v1.0). */
    static final float STEAL = 18F * ResourceTraining.MANA_SCALE;

    // =====================================================================================
    //  Escudo (C2, benéfico) — Inverter: quebra escudos e absorção dos inimigos
    // =====================================================================================
    public static class ShieldEffect extends EffectGlyph {
        public ShieldEffect() {
            super("shield", 15);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            Vec3 c = ArcaneUtil.center(le);
            double r = le.getBbWidth() * 0.5 + 0.7;
            if (stats.inverted) {
                boolean had = le.getAbsorptionAmount() > 0 || le.hasEffect(MobEffects.ABSORPTION);
                le.removeEffect(MobEffects.ABSORPTION);
                le.setAbsorptionAmount(0);
                if (le instanceof Player p && p.isBlocking()) {
                    p.disableShield();
                    had = true;
                }
                if (had) {
                    level.sendParticles(Vfx.magicCrit(), c.x, c.y, c.z, 25, r * 0.6, r * 0.6, r * 0.6, 0.3);
                    ArcaneUtil.sound(level, c, SoundEvents.GLASS_BREAK, 1.0F, 0.8F);
                }
                return;
            }
            // Amplificar = escudo maior: nível 1 = 8 de absorção (4 corações), +4 por Amplificar
            int amp = Mth.clamp(stats.amplify + 1, 0, 4);
            le.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, stats.duration(400), amp, false, true), ctx.caster());
            ArcaneUtil.sphere(level, c, r, ArcaneUtil.dust(ctx, 0.9F), 42);
            level.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 6, r * 0.5, r * 0.5, r * 0.5, 0.01);
            ArcaneUtil.sound(level, c, SoundEvents.BEACON_ACTIVATE, 0.5F, 1.7F);
            ArcaneUtil.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.2F);
        }
    }

    // =====================================================================================
    //  Refletir (C3, benéfico) — projéteis voltam (ver ArcaneEvents.onProjectileImpact)
    // =====================================================================================
    public static class ReflectEffect extends EffectGlyph {
        public ReflectEffect() {
            super("reflect", 18);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            Vec3 c = ArcaneUtil.center(le);
            if (stats.inverted) {
                if (le.removeEffect(ArcaneRegistries.REFLECT)) {
                    level.sendParticles(Vfx.magicCrit(), c.x, c.y, c.z, 16, 0.4, 0.5, 0.4, 0.2);
                    ArcaneUtil.sound(level, c, SoundEvents.GLASS_BREAK, 0.8F, 1.2F);
                }
                return;
            }
            ctx.status(le, ArcaneRegistries.REFLECT, 160, 0, stats);
            double r = le.getBbWidth() * 0.5 + 0.6;
            for (int ring = 0; ring < 3; ring++) {
                ArcaneUtil.ring(level, le.position().add(0, 0.3 + ring * le.getBbHeight() * 0.35, 0), r, ArcaneUtil.dust(0xC9A6FF, 1.0F), 6);
            }
            ArcaneUtil.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.5F);
            ArcaneUtil.sound(level, c, SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, 1.3F);
        }
    }

    // =====================================================================================
    //  Doar Mana (C3, benéfico)
    // =====================================================================================
    public static class ManaGiftEffect extends EffectGlyph {
        public ManaGiftEffect() {
            super("mana_gift", 10);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (target == c) return; // doar para si mesmo seria mana infinita
            ServerLevel level = ctx.level();
            if (target instanceof ServerPlayer tp) {
                MagicData td = MagicApi.get(tp);
                float amount = Math.min(GIFT * stats.power(), td.maxMana() - td.mana());
                ServerPlayer sp = ctx.casterPlayer();
                // A mana sai de quem doa (o custo da magia é só o "frete")
                if (sp != null && !sp.getAbilities().instabuild) {
                    MagicData cd = MagicApi.get(sp);
                    amount = Math.min(amount, cd.mana());
                    cd.setMana(cd.mana() - amount);
                }
                if (amount <= 0) return;
                td.addMana(amount);
                int shown = Math.round(amount);
                ArcaneUtil.actionbar(tp, Component.translatable("message.isekai_mahou.arcane_mana_received",
                        c != null ? c.getDisplayName() : Component.literal("?"), shown));
                if (sp != null) ArcaneUtil.actionbar(sp, Component.translatable("message.isekai_mahou.arcane_mana_given", shown, tp.getDisplayName()));
                stream(ctx, c, tp);
            } else if (target instanceof OwnableEntity own && target instanceof LivingEntity le && c != null
                    && c.getUUID().equals(own.getOwnerUUID())) {
                // Invocações e familiares se sustentam de mana: curam um pouco
                ctx.heal(le, 3F, stats);
                stream(ctx, c, le);
            }
        }

        private static void stream(SpellContext ctx, LivingEntity from, LivingEntity to) {
            ServerLevel level = ctx.level();
            ParticleOptions blue = ArcaneUtil.dust(MANA_BLUE, 1.0F);
            if (from != null) ArcaneUtil.line(level, ArcaneUtil.center(from), ArcaneUtil.center(to), blue, 0.5);
            Vec3 c = ArcaneUtil.center(to);
            level.sendParticles(Vfx.enchant(), c.x, c.y + 0.5, c.z, 20, 0.4, 0.5, 0.4, 0.6);
            ArcaneUtil.sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
        }
    }

    // =====================================================================================
    //  Roubar Mana (C5)
    // =====================================================================================
    public static class ManaDrainEffect extends EffectGlyph {
        public ManaDrainEffect() {
            super("mana_drain", 20);
            element(Element.ARCANE);
            tier(4);
            minCircle(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (target == c || !(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            float stolen;
            if (le instanceof ServerPlayer tp) {
                MagicData td = MagicApi.get(tp);
                stolen = Math.min(td.mana(), STEAL * stats.power());
                td.setMana(td.mana() - stolen);
                if (stolen > 0) {
                    ArcaneUtil.actionbar(tp, Component.translatable("message.isekai_mahou.arcane_mana_drained",
                            c != null ? c.getDisplayName() : Component.literal("?"), Math.round(stolen)));
                }
            } else {
                // Criaturas não têm barra de mana: a drenagem fere e corta a magia delas por um instante
                ctx.damage(le, 3F, stats, null);
                stolen = (4F * stats.power() + Math.min(12F, le.getMaxHealth() * 0.05F)) * ResourceTraining.MANA_SCALE;
                ctx.status(le, CoreRegistries.SILENCED, 40, 0, stats);
                StatusAuras.track(le, StatusAuras.Kind.SILENCE, stats.duration(40) + 20);
            }
            ServerPlayer sp = ctx.casterPlayer();
            if (sp != null && stolen > 0) {
                MagicApi.get(sp).addMana(stolen);
                ArcaneUtil.actionbar(sp, Component.translatable("message.isekai_mahou.arcane_mana_stolen", Math.round(stolen)));
            }
            // Fluxo de mana do alvo para você
            Vec3 from = ArcaneUtil.center(le);
            if (c != null) {
                Vec3 to = ArcaneUtil.center(c);
                ArcaneUtil.line(level, from, to, ArcaneUtil.dust(MANA_BLUE, 0.9F), 0.4);
                level.sendParticles(Vfx.enchant(), to.x, to.y + 0.6, to.z, 16, 0.3, 0.4, 0.3, 0.8);
            }
            level.sendParticles(ArcaneUtil.dust(0x2A4F9A, 1.3F), from.x, from.y, from.z, 14, 0.3, 0.4, 0.3, 0);
            ArcaneUtil.sound(level, from, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.6F);
        }
    }

    // =====================================================================================
    //  Dissipar (C5)
    // =====================================================================================
    public static class DispelEffect extends EffectGlyph {
        public DispelEffect() {
            super("dispel", 25);
            element(Element.ARCANE);
            tier(4);
            minCircle(2);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ArcaneUtil.markRan(ctx, "dispel");
            ServerLevel level = ctx.level();
            boolean ally = ctx.isAlly(target);
            int removed = 0;
            if (target instanceof LivingEntity le) {
                // Aliado: tira o que é ruim. Inimigo: tira o que é bom. (Exaustão Mágica não sai: é regra do Domínio)
                for (MobEffectInstance inst : List.copyOf(le.getActiveEffects())) {
                    Holder<MobEffect> eff = inst.getEffect();
                    if (eff.is(CoreRegistries.MAGIC_EXHAUSTION.getKey())) continue;
                    MobEffectCategory cat = eff.value().getCategory();
                    if (ally ? cat == MobEffectCategory.HARMFUL : cat == MobEffectCategory.BENEFICIAL) {
                        le.removeEffect(eff);
                        removed++;
                    }
                }
            }
            int spells = ArcaneUtil.destroyHostileMagic(ctx, target.position(), stats.radius(4));
            Vec3 c = ArcaneUtil.center(target);
            ArcaneUtil.sphere(level, c, target.getBbWidth() * 0.5 + 0.6, ArcaneUtil.dust(ctx, 1.0F), 30);
            if (removed + spells > 0) {
                level.sendParticles(Vfx.magicCrit(), c.x, c.y, c.z, 20, 0.4, 0.5, 0.4, 0.3);
                ArcaneUtil.sound(level, c, SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.5F);
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (ArcaneUtil.ranThisTick(ctx, "dispel")) return;
            ServerLevel level = ctx.level();
            Vec3 at = hit.getLocation();
            int n = ArcaneUtil.destroyHostileMagic(ctx, at, stats.radius(4));
            // Barreiras mágicas (forms_b) e pedra conjurada viram pó
            Block barrier = BuiltInRegistries.BLOCK.getOptional(IsekaiMahou.id("spell_barrier")).orElse(null);
            Block stone = ArcaneRegistries.CONJURED_STONE.get();
            int r = Math.min(6, Math.round(stats.radius(3)));
            ServerPlayer sp = ctx.casterPlayer();
            BlockPos center = hit.getBlockPos();
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                if (p.distSqr(center) > r * r) continue;
                var s = level.getBlockState(p);
                if (!(s.is(stone) || (barrier != null && s.is(barrier)))) continue;
                if (sp != null && !level.mayInteract(sp, p)) continue;
                level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(s));
                level.removeBlock(p, false);
                n++;
            }
            ArcaneUtil.ring(level, at.add(0, 0.2, 0), r, ArcaneUtil.dust(ctx, 1.0F), 24);
            if (n > 0) ArcaneUtil.sound(level, at, SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.5F);
        }
    }

    // =====================================================================================
    //  Contramágica (C2, tier 3) — contraria conjurações pendentes da Pilha e silencia
    // =====================================================================================
    public static class CounterspellEffect extends EffectGlyph {
        public CounterspellEffect() {
            super("counterspell", 30);
            element(Element.ARCANE);
            tier(3);
            minCircle(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (target == c) {
                // Em você: pulso anti-magia que contraria tudo o que é hostil a até 8 blocos
                if (c == null || !ArcaneUtil.firstThisTick(ctx, "counterspell")) return;
                pulse(ctx, ArcaneUtil.center(c), stats.radius(8), stats);
                return;
            }
            // No alvo: o que ele está conjurando e o que acontece perto dele (golpes telegrafados) é contrariado
            int n = SpellStack.counterNear(ctx.level(), target.position(), 3.0 + stats.radius(1F), c, power(ctx, stats));
            if (target instanceof LivingEntity le) counter(ctx, le, stats);
            ArcaneUtil.destroyHostileMagic(ctx, target.position(), stats.radius(4));
            glass(ctx.level(), ArcaneUtil.center(target), n > 0 ? 1.6 : 1.0, c);
        }

        /** Só com Sensível: pulso no ponto de impacto. */
        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!ArcaneUtil.firstThisTick(ctx, "counterspell")) return;
            pulse(ctx, hit.getLocation(), stats.radius(4), stats);
        }

        /** Poder da contramágica: o do glifo (Amplificar, Carregar...) vezes o do contexto (cântico...). */
        private static float power(SpellContext ctx, SpellStats stats) {
            return stats.power() * ctx.powerMult();
        }

        /** Resguardo (módulo arcane_duel, lido por id): imune a contramágica — não é interrompido nem silenciado. */
        private static boolean warded(LivingEntity le) {
            return BuiltInRegistries.MOB_EFFECT.getHolder(IsekaiMahou.id("hexproof")).map(le::hasEffect).orElse(false);
        }

        /** Silêncio (o "não conjura mais por um tempo" da Contramágica). */
        private static void counter(SpellContext ctx, LivingEntity le, SpellStats stats) {
            if (warded(le)) return;
            if (le instanceof ServerPlayer p) {
                // Canalizar/conjuração curta (fora da Pilha) também cai
                if (MagicApi.get(p).channelSlot >= 0) SpellCaster.interrupt(p);
                ctx.status(p, CoreRegistries.SILENCED, 60, 0, stats);
                StatusAuras.track(p, StatusAuras.Kind.SILENCE, stats.duration(60) + 20);
            } else {
                int ticks = stats.duration(120);
                if (ArcaneUtil.isBoss(le)) ticks /= 2;
                le.addEffect(new MobEffectInstance(CoreRegistries.SILENCED, ticks, 0), ctx.caster());
                StatusAuras.track(le, StatusAuras.Kind.SILENCE, ticks + 20);
            }
        }

        private static void pulse(SpellContext ctx, Vec3 center, float radius, SpellStats stats) {
            ServerLevel level = ctx.level();
            int n = SpellStack.counterNear(level, center, radius, ctx.caster(), power(ctx, stats));
            for (LivingEntity e : ctx.enemiesAround(center, radius)) counter(ctx, e, stats);
            ArcaneUtil.destroyHostileMagic(ctx, center, radius);
            ParticleOptions dust = ArcaneUtil.dust(ctx, 1.2F);
            for (int i = 1; i <= 3; i++) {
                double r = radius * i / 3.0;
                ArcaneUtil.ring(level, center.add(0, -0.6, 0), r, dust, (int) Math.min(96, 12 * r));
            }
            glass(level, center, 1.4 + (n > 0 ? 0.6 : 0), ctx.caster());
            ArcaneUtil.sound(level, center, SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.2F);
            ArcaneUtil.sound(level, center, SoundEvents.EVOKER_CAST_SPELL, 1.0F, 0.6F);
        }

        /**
         * Vidro mágico quebrando: um painel hexagonal de luz surge virado para quem conjurou e, 3 ticks depois,
         * estilhaça em cacos de vidro tingido com o som de vidro.
         */
        private static void glass(ServerLevel level, Vec3 c, double size, LivingEntity facing) {
            Vec3 fwd = facing != null ? c.subtract(facing.getEyePosition()) : new Vec3(0, 0, 1);
            Vec3 h = new Vec3(fwd.x, 0, fwd.z);
            h = h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
            Vec3 right = new Vec3(-h.z, 0, h.x);
            Vec3 up = new Vec3(0, 1, 0);
            ParticleOptions edge = ArcaneUtil.dust(0xE8F0FF, 1.0F);
            ParticleOptions rune = ArcaneUtil.dust(0xB98CFF, 0.8F);
            for (int k = 0; k < 6; k++) {
                double a0 = Math.PI / 3 * k, a1 = Math.PI / 3 * (k + 1);
                Vec3 p0 = c.add(right.scale(Math.cos(a0) * size)).add(up.scale(Math.sin(a0) * size));
                Vec3 p1 = c.add(right.scale(Math.cos(a1) * size)).add(up.scale(Math.sin(a1) * size));
                ArcaneUtil.line(level, p0, p1, edge, 0.25);
                ArcaneUtil.line(level, c, p0, rune, 0.4);
            }
            ArcaneUtil.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
            SpellScheduler.schedule(3, () -> {
                BlockParticleOption violet = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PURPLE_STAINED_GLASS.defaultBlockState());
                BlockParticleOption clear = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
                int n = (int) (16 * size);
                level.sendParticles(violet, c.x, c.y, c.z, n, size * 0.45, size * 0.45, size * 0.45, 0.3);
                level.sendParticles(clear, c.x, c.y, c.z, n / 2, size * 0.45, size * 0.45, size * 0.45, 0.3);
                level.sendParticles(Vfx.magicCrit(), c.x, c.y, c.z, n, size * 0.4, size * 0.4, size * 0.4, 0.5);
                level.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 6, size * 0.3, size * 0.3, size * 0.3, 0.15);
                ArcaneUtil.sound(level, c, SoundEvents.GLASS_BREAK, 1.2F, 0.7F);
                ArcaneUtil.sound(level, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.2F);
            });
        }
    }

    private ManaEffects() {}
}
