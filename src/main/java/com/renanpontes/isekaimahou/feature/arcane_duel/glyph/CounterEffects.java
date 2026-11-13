package com.renanpontes.isekaimahou.feature.arcane_duel.glyph;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.feature.arcane_duel.DuelFx;
import com.renanpontes.isekaimahou.feature.arcane_duel.DuelRegistry;
import com.renanpontes.isekaimahou.feature.arcane_duel.DuelUtil;
import com.renanpontes.isekaimahou.feature.arcane_duel.entity.DuelFxEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;
import com.renanpontes.isekaimahou.glyph.entity.SpellProjectileEntity;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Respostas de duelo: Vazamento de Mana, Redirecionar, Copiar Magia e Roubar Encanto. */
public final class CounterEffects {

    // =====================================================================================
    //  Vazamento de Mana (ARCANE t2, C1) — contraria MENORES (MAIORES com Amplificar); jogadores podem pagar 90 de mana
    // =====================================================================================
    public static class ManaLeakEffect extends EffectGlyph {
        /** Imposto (v0.8: 30; ×{@link ResourceTraining#MANA_SCALE} com a mana da v1.0). */
        public static final int TAX = Math.round(30 * ResourceTraining.MANA_SCALE);

        public ManaLeakEffect() {
            super("mana_leak", 12);
            element(Element.ARCANE);
            tier(2);
            minCircle(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity by = ctx.caster();
            if (target == by) {
                // Em você: pulso que vaza tudo em volta
                if (by == null || !DuelUtil.firstThisTick(ctx, "mana_leak")) return;
                leak(ctx, by.position(), stats.radius(5F), null, stats);
                return;
            }
            leak(ctx, target.position(), 3.0 + stats.radius(1F), target instanceof LivingEntity le ? le : null, stats);
        }

        private static void leak(SpellContext ctx, Vec3 center, double range, @Nullable LivingEntity mainTarget, SpellStats stats) {
            ServerLevel level = ctx.level();
            LivingEntity by = ctx.caster();
            float power = stats.power() * ctx.powerMult();
            int countered = 0, taxed = 0, tooStrong = 0;
            for (SpellStack.Pending p : SpellStack.near(level, center, range)) {
                if (!p.hostileTo(by)) continue;
                // Resguardo: não cobra o "imposto" nem drena — o núcleo recusa e mostra o escudo
                if (p.caster() != null && p.caster().hasEffect(DuelRegistry.HEXPROOF)) {
                    SpellStack.counter(level, p, by, Math.max(power, p.tier().requiredPower()));
                    continue;
                }
                boolean ok = p.tier() == SpellStack.Tier.MINOR || (p.tier() == SpellStack.Tier.MAJOR && stats.amplify > 0);
                if (!ok) {
                    tooStrong++;
                    continue;
                }
                // Jogador: paga o "imposto" e a magia segue; sem mana suficiente, a magia vaza. Uma cobrança por pendente
                // e conjuração (uma área que pega vários alvos em volta dele, ou uma zona pulsando, não cobra de novo)
                if (p.caster() instanceof ServerPlayer sp && !DuelUtil.isCreative(sp)) {
                    if (!DuelUtil.firstWithin(ctx, "mana_leak.tax." + p.id(), 100)) {
                        taxed++;   // já pagou nesta conjuração
                        continue;
                    }
                    MagicData d = MagicApi.get(sp);
                    if (d.mana() >= TAX) {
                        d.setMana(d.mana() - TAX);
                        taxed++;
                        DuelUtil.actionbar(sp, Component.translatable("message.isekai_mahou.arcane_duel.mana_leak.paid", TAX), ChatFormatting.AQUA);
                        drip(level, DuelFx.center(sp));
                        continue;
                    }
                    d.setMana(0);
                }
                if (SpellStack.counter(level, p, by, Math.max(power, p.tier().requiredPower())).success()) countered++;
            }
            // Jogador sem nada na Pilha: o vazamento só drena o imposto (× poder)
            if (mainTarget instanceof ServerPlayer tp && countered + taxed == 0 && !ctx.isAlly(tp)
                    && !tp.hasEffect(DuelRegistry.HEXPROOF)
                    && DuelUtil.firstWithin(ctx, "mana_leak.drain." + tp.getUUID(), 40)) {
                MagicData d = MagicApi.get(tp);
                float lost = Math.min(TAX * stats.power(), d.mana());
                if (lost > 0) {
                    d.setMana(d.mana() - lost);
                    DuelUtil.actionbar(tp, Component.translatable("message.isekai_mahou.arcane_duel.mana_leak.drained", Math.round(lost)),
                            ChatFormatting.AQUA);
                    taxed++;
                }
            }
            Vec3 c = mainTarget != null ? DuelFx.center(mainTarget) : center.add(0, 1, 0);
            if (countered > 0) {
                DuelFx.shatter(level, c, 1.1F + 0.2F * countered, DuelFx.MANA, by);
            } else {
                drip(level, c);
                if (taxed == 0) {
                    DuelUtil.actionbar(by, Component.translatable(tooStrong > 0
                            ? "message.isekai_mahou.arcane_duel.mana_leak.too_strong"
                            : "message.isekai_mahou.arcane_duel.mana_leak.nothing"), ChatFormatting.GRAY);
                }
            }
            DuelFx.sound(level, c, SoundEvents.BUCKET_EMPTY, 0.6F, 1.6F);
        }

        /** Mana escorrendo: gotas azuis caindo de um anel trincado. */
        private static void drip(ServerLevel level, Vec3 c) {
            DuelFx.ring(level, c, 0.7, DuelFx.dust(DuelFx.MANA, 1.0F), 12);
            level.sendParticles(Vfx.water(), c.x, c.y, c.z, 14, 0.4, 0.4, 0.4, 0);
            level.sendParticles(DuelFx.dust(0x2A5FB0, 1.3F), c.x, c.y - 0.3, c.z, 10, 0.3, 0.5, 0.3, 0);
            DuelFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.6F);
        }
    }

    // =====================================================================================
    //  Redirecionar (ARCANE t3, C3) — projéteis inimigos perto do alvo trocam de dono e voltam
    // =====================================================================================
    public static class RedirectEffect extends EffectGlyph {
        private static final int MAX_PROJECTILES = 24;
        /** Teto do poder de uma magia devolvida (ela pode ir e voltar entre dois duelistas). */
        private static final float MAX_POWER = 3F;

        public RedirectEffect() {
            super("redirect", 18);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            Vec3 c = DuelFx.center(target);
            double r = stats.radius(5F);
            int n = 0, fx = 0;
            for (Projectile proj : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r),
                    p -> p.isAlive() && p.distanceToSqr(c) <= r * r)) {
                if (n >= MAX_PROJECTILES) break;
                Entity owner = proj.getOwner();
                if (owner == caster || (owner != null && ctx.isAlly(owner))) continue;
                if (proj.getDeltaMovement().lengthSqr() < 1.0E-3) continue;   // flecha cravada, tridente parado...
                Vec3 at = proj.position();
                if (send(level, proj, caster, owner, stats.power() * ctx.powerMult())) {
                    n++;
                    if (fx++ < 6) DuelFx.spawn(level, DuelFxEntity.MIRROR, at, 0.9F, 16, DuelFx.ARCANE, caster.getYRot());
                }
            }
            if (n > 0) {
                DuelFx.sound(level, c, SoundEvents.BREEZE_DEFLECT, 1.0F, 0.9F);
                DuelFx.sound(level, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.2F);
                DuelUtil.actionbar(caster, Component.translatable("message.isekai_mahou.arcane_duel.redirect.done", n), ChatFormatting.LIGHT_PURPLE);
            } else {
                // Nada voando: um espelho rúnico curto em volta do alvo, só de aviso
                DuelFx.spawn(level, DuelFxEntity.MIRROR, c, 0.8F, 10, DuelFx.ARCANE, caster.getYRot());
                DuelFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, 1.4F);
            }
        }

        /** Manda o projétil de volta a quem o lançou; ele passa a ser do conjurador. */
        private static boolean send(ServerLevel level, Projectile proj, LivingEntity caster, @Nullable Entity owner, float power) {
            Vec3 vel = proj.getDeltaMovement();
            double speed = Math.max(0.7, vel.length()) * 1.25;
            Vec3 at = proj.position();
            Vec3 dir;
            if (owner != null && owner.isAlive() && owner.level() == level && owner.distanceToSqr(proj) < 96 * 96) {
                dir = owner.position().add(0, owner.getBbHeight() * 0.6, 0).subtract(at).normalize();
            } else {
                dir = vel.normalize().scale(-1);
            }
            Vec3 newVel = dir.scale(speed);
            if (proj instanceof SpellProjectileEntity spell) {
                // A magia vira do conjurador: aliados/inimigos e créditos passam a contar a partir dele
                CompoundTag tag = spell.saveWithoutId(new CompoundTag());
                SpellContext old = SpellContext.load(level, tag.getCompound("Spell"));
                spell.discard();
                if (old == null) return false;
                SpellContext mine = new SpellContext(level, caster, old.element(), old.recipe(), old.spell());
                // Devolver de novo (dois Redirecionar trocando a mesma magia) não cresce sem limite
                mine.setPowerMult(Math.min(MAX_POWER, Math.max(old.powerMult(), 1F) * Math.max(1F, power)));
                SpellProjectileEntity copy = SpellProjectileEntity.create(mine, caster);
                copy.setPos(at.add(dir.scale(0.6)));
                copy.setDeltaMovement(newVel);
                level.addFreshEntity(copy);
            } else if (proj instanceof ShulkerBullet) {
                proj.discard();   // a bala do shulker sempre volta a perseguir o mesmo alvo: o espelho a desfaz
            } else {
                Entity newOwner = proj instanceof ThrownTrident ? owner : caster;   // tridente: a Lealdade continua do dono
                proj.deflect((p, e, random) -> {
                    p.setDeltaMovement(newVel);
                    p.hasImpulse = true;
                }, caster, newOwner, false);
                proj.setPos(at.add(dir.scale(0.6)));
            }
            level.sendParticles(Vfx.magicCrit(), at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.3);
            level.sendParticles(DuelFx.dust(DuelFx.ARCANE, 1.2F), at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0);
            return true;
        }
    }

    // =====================================================================================
    //  Copiar Magia (ARCANE t4, C4) — copia a última magia de OUTRO a ≤16 blocos (4 s) e conjura como sua
    // =====================================================================================
    public static class ForkEffect extends EffectGlyph {
        public static final ResourceLocation ID = IsekaiMahou.id("fork");
        private static final double RANGE = 16.0;
        private static final int MAX_AGE = 80;
        /** Uma cópia não pode copiar de novo dentro dela mesma (evita laço entre dois "Copiar Magia"). */
        private static int depth;

        public ForkEffect() {
            super("fork", 35);
            element(Element.ARCANE);
            tier(4);
            minCircle(4);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (caster == null || depth > 0 || !DuelUtil.firstThisTick(ctx, "fork")) return;
            ServerLevel level = ctx.level();
            for (SpellStack.Recent r : SpellStack.recent(level, caster.position(), RANGE, MAX_AGE)) {
                if (r.caster().equals(caster.getUUID())) continue;
                SpellRecipe recipe = stripTriggers(r.recipe());
                if (recipe.isEmpty() || recipe.glyphs().contains(ID)) continue;
                SpellParser.Result parsed = SpellParser.parse(recipe, r.element(), null);
                if (!parsed.ok() || parsed.spell().isUltimate()) continue;
                Vec3 eye = caster.getEyePosition();
                Vec3 dir = target != caster ? DuelFx.center(target).subtract(eye) : caster.getLookAngle();
                if (dir.lengthSqr() < 1.0E-4) dir = caster.getLookAngle();
                depth++;
                SpellContext copy;
                try {
                    copy = SpellCaster.castFrom(caster, r.element(), recipe, stats.power() * ctx.powerMult(), eye, dir.normalize(), true);
                } finally {
                    depth--;
                }
                if (copy == null) continue;
                show(level, caster, r, parsed.spell());
                return;
            }
            DuelUtil.actionbar(caster, Component.translatable("message.isekai_mahou.arcane_duel.fork.nothing"), ChatFormatting.GRAY);
            Vec3 c = DuelFx.center(caster);
            level.sendParticles(Vfx.smoke(), c.x, c.y + 0.6, c.z, 10, 0.3, 0.3, 0.3, 0.01);
            DuelFx.sound(level, c, SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.6F);
        }

        private static void show(ServerLevel level, LivingEntity caster, SpellStack.Recent r, ParsedSpell spell) {
            int rgb = r.recipe().color() >= 0 ? r.recipe().color() : r.element().color;
            Vec3 c = DuelFx.center(caster);
            // A carta espelhada surge sobre você; um fio de luz liga o original à cópia
            DuelFx.spawn(level, DuelFxEntity.CARD, caster.position().add(0, caster.getBbHeight() + 0.6, 0), 0.9F, 30, rgb, 1F);
            DuelFx.line(level, r.pos().add(0, 1, 0), c, DuelFx.dust(rgb, 1.0F), 0.6);
            level.sendParticles(Vfx.enchant(), c.x, c.y + 0.6, c.z, 30, 0.4, 0.6, 0.4, 0.9);
            DuelFx.sound(level, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 0.8F);
            DuelFx.sound(level, c, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 1.3F);
            Component name = r.recipe().name().isEmpty()
                    ? Component.translatable("announce.isekai_mahou.unnamed", r.element().displayName())
                    : Component.literal(r.recipe().name());
            DuelUtil.actionbar(caster, Component.translatable("message.isekai_mahou.arcane_duel.fork.copied", name, r.casterName()),
                    ChatFormatting.LIGHT_PURPLE);
        }

        /** Tira os gatilhos do começo (uma cópia sai na hora; "Ao Receber Dano" etc. não fazem sentido nela). */
        private static SpellRecipe stripTriggers(SpellRecipe recipe) {
            List<ResourceLocation> out = new ArrayList<>(recipe.glyphs());
            while (!out.isEmpty()) {
                Glyph g = GlyphRegistry.get(out.get(0));
                if (!(g instanceof TriggerGlyph t) || t.isLink()) break;
                out.remove(0);
            }
            return recipe.withGlyphs(out);
        }
    }

    // =====================================================================================
    //  Roubar Encanto (ARCANE t3, C3) — os efeitos benéficos do alvo passam para você
    // =====================================================================================
    public static class SpellStealEffect extends EffectGlyph {
        private static final int MAX_STOLEN = 6;
        private static final int MAX_TICKS = 2400;

        public SpellStealEffect() {
            super("spell_steal", 20);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            // Aliados (seus bichos, jogadores sem PvP) não são roubados por uma área que os pegou junto
            if (caster == null || target == caster || !(target instanceof LivingEntity le) || ctx.isAlly(le)) return;
            ServerLevel level = ctx.level();
            List<MobEffectInstance> stolen = new ArrayList<>();
            for (MobEffectInstance inst : List.copyOf(le.getActiveEffects())) {
                if (stolen.size() >= MAX_STOLEN) break;
                Holder<MobEffect> eff = inst.getEffect();
                if (eff.value().getCategory() != MobEffectCategory.BENEFICIAL) continue;
                stolen.add(inst);
            }
            Vec3 from = DuelFx.center(le);
            Vec3 to = DuelFx.center(caster);
            if (stolen.isEmpty()) {
                level.sendParticles(Vfx.smoke(), from.x, from.y, from.z, 8, 0.3, 0.3, 0.3, 0.01);
                DuelUtil.actionbar(caster, Component.translatable("message.isekai_mahou.arcane_duel.spell_steal.nothing"), ChatFormatting.GRAY);
                return;
            }
            // Amplificar/Estender: o encanto roubado dura mais em você
            float keep = Math.min(2F, 0.75F + 0.25F * stats.power() + 0.25F * stats.duration);
            for (MobEffectInstance inst : stolen) {
                le.removeEffect(inst.getEffect());
                int ticks = inst.isInfiniteDuration() ? 1200 : Math.min(MAX_TICKS, Math.round(inst.getDuration() * keep));
                caster.addEffect(new MobEffectInstance(inst.getEffect(), Math.max(40, ticks), inst.getAmplifier(), false, true), caster);
            }
            // Cartas: a do alvo se desfaz, a sua aparece; fio dourado entre os dois
            DuelFx.spawn(level, DuelFxEntity.CARD, le.position().add(0, le.getBbHeight() + 0.5, 0), 0.8F, 20, 0x5A3A8A, 2F);
            DuelFx.spawn(level, DuelFxEntity.CARD, caster.position().add(0, caster.getBbHeight() + 0.6, 0), 0.8F, 28, DuelFx.GOLD, 0F);
            DuelFx.line(level, from, to, DuelFx.dust(DuelFx.GOLD, 1.1F), 0.45);
            level.sendParticles(Vfx.enchant(), to.x, to.y + 0.5, to.z, 24, 0.4, 0.5, 0.4, 0.8);
            DuelFx.sound(level, to, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 1.2F);
            DuelFx.sound(level, from, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.7F);
            DuelUtil.actionbar(caster, Component.translatable("message.isekai_mahou.arcane_duel.spell_steal.done", stolen.size(),
                    le.getDisplayName()), ChatFormatting.GOLD);
            DuelUtil.actionbar(le, Component.translatable("message.isekai_mahou.arcane_duel.spell_steal.victim", caster.getDisplayName()),
                    ChatFormatting.RED);
        }
    }

    public static void clear() {
        ForkEffect.depth = 0;
    }

    private CounterEffects() {}
}
