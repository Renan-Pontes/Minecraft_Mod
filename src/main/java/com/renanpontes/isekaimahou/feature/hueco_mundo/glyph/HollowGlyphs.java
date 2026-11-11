package com.renanpontes.isekaimahou.feature.hueco_mundo.glyph;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmTravel;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HmAllies;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos do Mundo Oco e do Reino das Fadas — todos montáveis no Grimório (combinam com formas, aumentos, estágios e
 * ligações; o custo por cópia e a inflação {@code DAMAGE_SCALE} do núcleo valem normalmente via {@code ctx.damage}).
 * Saem como pergaminho dos chefes, Arrancar, Menos e baús dos reinos. Os de ORIGEM são dos espirituais:
 * Shinigami ({@code garganta_senkai}, {@code visored_mask}) e Quincy ({@code garganta_reishi}, {@code reishi_rend}).
 */
public final class HollowGlyphs {

    public static void register() {
        GlyphRegistry.register(new Cero());
        GlyphRegistry.register(new GranCero());
        GlyphRegistry.register(new Sonido());
        GlyphRegistry.register(new Hierro());
        GlyphRegistry.register(new SandTide());
        GlyphRegistry.register(new GargantaGate("garganta_senkai", MagicPath.SHINIGAMI, Element.SHADOW, 0xD9483B));
        GlyphRegistry.register(new GargantaGate("garganta_reishi", MagicPath.QUINCY, Element.LIGHT, HmFx.REISHI));
        GlyphRegistry.register(new VisoredMask());
        GlyphRegistry.register(new ReishiRend());
        GlyphRegistry.register(new Thornbind());
        GlyphRegistry.register(new FeyCharm());
    }

    /** Cero (Sombra, tier 3): feixe concentrado da mão até o alvo — dano alto num alvo só e uma pequena explosão. */
    static final class Cero extends EffectGlyph {
        Cero() {
            super("hollow_cero", 22);
            element(Element.SHADOW);
            tier(3);
        }

        @Override
        public float baseDamage() {
            return 9F;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
            HmFx.beam(sl, ctx.caster().getEyePosition().add(ctx.look().scale(0.6)), to, HmFx.CERO_RED, 0.5F);
            ctx.damage(target, 9F, stats, null);
            sl.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y, to.z, 1, 0, 0, 0, 0);
            sl.playSound(null, to.x, to.y, to.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9F, 0.6F);
            Vec3 push = target.position().subtract(ctx.caster().position()).multiply(1, 0, 1);
            if (push.lengthSqr() > 1.0E-4) {
                push = push.normalize().scale(0.6 * stats.power());
                target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.15, push.z));
                target.hurtMarked = true;
            }
        }
    }

    /** Cero Real (Sombra, lendário, Círculo 7): o Cero do Rei — feixe enorme e explosão que fere todos em volta. */
    static final class GranCero extends EffectGlyph {
        GranCero() {
            super("hollow_gran_cero", 64);
            element(Element.SHADOW);
            legendary();
            minCircle(7);
        }

        @Override
        public float baseDamage() {
            return 16F;
        }

        @Override
        public float hintRadius() {
            return 3.5F;
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            blast(ctx, target.position().add(0, target.getBbHeight() * 0.5, 0), target, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            blast(ctx, hit.getLocation(), null, stats);
        }

        private void blast(SpellContext ctx, Vec3 at, Entity direct, SpellStats stats) {
            ServerLevel sl = ctx.level();
            HmFx.beam(sl, ctx.caster().getEyePosition().add(ctx.look().scale(0.6)), at, 0xC9A23A, 1.2F);
            if (direct != null) ctx.damage(direct, 16F, stats, null);
            float r = stats.radius(3.5F);
            for (LivingEntity e : ctx.enemiesAround(at, r)) {
                if (e == direct) continue;
                ctx.damage(e, 8F, stats, null);
            }
            HmFx.sphere(sl, at, r, 60, HmFx.dust(0xC9A23A, 2.0F));
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.6F);
        }
    }

    /** Sonído (Vento, tier 2): passo instantâneo — o alvo (você, numa magia em Si Mesmo) dispara para a frente. */
    static final class Sonido extends EffectGlyph {
        Sonido() {
            super("hollow_sonido", 10);
            element(Element.WIND);
            tier(2);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public float hintRadius() {
            return 7F;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            Vec3 dir = ctx.caster().getLookAngle().multiply(1, 0, 1);
            if (dir.lengthSqr() < 1.0E-4) return;
            dir = dir.normalize();
            double dist = Math.min(14.0, 7.0 * stats.power());
            Vec3 from = le.position();
            Vec3 to = from;
            // Avança até a parede (passos de meio bloco, sem atravessar blocos).
            for (double s = 0.5; s <= dist; s += 0.5) {
                Vec3 next = from.add(dir.scale(s));
                if (!ctx.level().noCollision(le, le.getBoundingBox().move(next.subtract(from)))) break;
                to = next;
            }
            if (to.distanceToSqr(from) < 1.0) return;
            le.teleportTo(to.x, to.y, to.z);
            le.resetFallDistance();
            HmFx.afterimage(ctx.level(), from, to, 0xE8E8F2);
            ctx.level().playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.9F);
        }
    }

    /** Pele de Ferro (Terra, tier 2): a pele endurece — Resistência (II com Amplificar) e firmeza contra empurrões. */
    static final class Hierro extends EffectGlyph {
        Hierro() {
            super("hollow_hierro", 12);
            element(Element.EARTH);
            tier(2);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public int hintDuration() {
            return 200;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            int ticks = stats.duration(200);
            int amp = stats.power() >= 1.4F ? 1 : 0;
            le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, amp), ctx.caster());
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.max(20, ticks / 4), 0), ctx.caster());
            ctx.level().sendParticles(HmFx.dust(0x9A9AA8, 1.2F), le.getX(), le.getY() + 1, le.getZ(), 20, 0.4, 0.6, 0.4, 0);
            ctx.level().playSound(null, le.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
    }

    /** Maré de Areia (Terra, tier 4): uma onda de areia branca atinge o alvo — dano, empurrão e cegueira. */
    static final class SandTide extends EffectGlyph {
        SandTide() {
            super("sand_tide", 26);
            element(Element.EARTH);
            tier(4);
        }

        @Override
        public float baseDamage() {
            return 7F;
        }

        @Override
        public int hintDuration() {
            return 40;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ctx.damage(target, 7F, stats, null);
            ServerLevel sl = ctx.level();
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, HmRegistries.HOLLOW_SAND.get().defaultBlockState()),
                    target.getX(), target.getY() + 0.5, target.getZ(), 30, 0.6, 0.5, 0.6, 0.15);
            if (target instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, stats.duration(40), 0), ctx.caster());
                Vec3 push = le.position().subtract(ctx.caster().position()).multiply(1, 0, 1);
                if (push.lengthSqr() > 1.0E-4) {
                    push = push.normalize().scale(0.9 * stats.power());
                    le.setDeltaMovement(le.getDeltaMovement().add(push.x, 0.35, push.z));
                    le.hurtMarked = true;
                }
            }
            sl.playSound(null, target.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
        }
    }

    /**
     * Portão Senkai (Shinigami) / Ponte de Reishi (Quincy): rasga uma passagem para o Mundo Oco onde a magia acerta (numa
     * magia em Si Mesmo, à sua frente). Dentro do Mundo Oco, abre a volta. Só no Mundo Normal ou no Mundo Oco; recarga de
     * 2 min por jogador.
     */
    static final class GargantaGate extends EffectGlyph {
        private final int rgb;

        GargantaGate(String id, MagicPath origin, Element element, int rgb) {
            super(id, 30);
            element(element);
            origin(origin);
            tier(3);
            this.rgb = rgb;
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(ctx.caster() instanceof ServerPlayer sp)) return;
            Vec3 at = target == sp ? HmTravel.spotAhead(sp) : target.position();
            open(sp, at);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!(ctx.caster() instanceof ServerPlayer sp)) return;
            BlockPos p = hit.getBlockPos().relative(hit.getDirection());
            open(sp, Vec3.atBottomCenterOf(p));
        }

        private void open(ServerPlayer sp, Vec3 at) {
            if (HmTravel.shortcut(sp, at, true)) {
                sp.serverLevel().sendParticles(HmFx.dust(rgb, 1.6F), at.x, at.y + 1.5, at.z, 40, 0.4, 1.4, 0.4, 0);
            }
        }
    }

    /**
     * Máscara Interior (Shinigami, tier 4): puxa a máscara do Vazio interior sobre o rosto — Força II, Velocidade e
     * Resistência pelo tempo da magia; quando cai, a fome cobra o preço.
     */
    static final class VisoredMask extends EffectGlyph {
        VisoredMask() {
            super("visored_mask", 28);
            element(Element.SHADOW);
            origin(MagicPath.SHINIGAMI);
            tier(4);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public int hintDuration() {
            return 240;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            int ticks = stats.duration(240);
            le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 1), ctx.caster());
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0), ctx.caster());
            le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0), ctx.caster());
            le.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks + 100, 1), ctx.caster());
            ServerLevel sl = ctx.level();
            Vec3 face = le.getEyePosition();
            sl.sendParticles(HmFx.dust(0xF4F2EA, 1.4F), face.x, face.y, face.z, 24, 0.3, 0.3, 0.3, 0);
            sl.sendParticles(HmFx.dust(HmFx.CERO_RED, 1.0F), face.x, face.y, face.z, 10, 0.25, 0.2, 0.25, 0);
            sl.playSound(null, le.blockPosition(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }

    /**
     * Rasgo de Reishi (Quincy, Luz, tier 3): flecha de reishi que desfaz espíritos — dano dobrado em Vazios (criaturas e
     * chefes do Mundo Oco); o Quincy absorve o reishi e recupera um pouco do recurso a cada acerto.
     */
    static final class ReishiRend extends EffectGlyph {
        ReishiRend() {
            super("reishi_rend", 16);
            element(Element.LIGHT);
            origin(MagicPath.QUINCY);
            tier(3);
        }

        @Override
        public float baseDamage() {
            return 6F;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            float base = HmAllies.isHollow(target) ? 12F : 6F;
            ctx.damage(target, base, stats, null);
            ServerLevel sl = ctx.level();
            sl.sendParticles(HmFx.dust(HmFx.REISHI, 1.3F), target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    16, 0.3, 0.4, 0.3, 0);
            if (ctx.caster() instanceof ServerPlayer sp) {
                MagicData d = MagicApi.get(sp);
                d.addMana(d.maxMana() * 0.02F);
                sl.sendParticles(HmFx.dust(HmFx.REISHI, 0.8F), sp.getX(), sp.getY() + 1, sp.getZ(), 6, 0.3, 0.5, 0.3, 0);
            }
        }
    }

    /** Laço de Espinhos (Terra, tier 3): roseira brota sob o alvo — dano, enraizado e sangramento. */
    static final class Thornbind extends EffectGlyph {
        Thornbind() {
            super("fey_thornbind", 18);
            element(Element.EARTH);
            tier(3);
        }

        @Override
        public float baseDamage() {
            return 4F;
        }

        @Override
        public int hintDuration() {
            return 50;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ctx.damage(target, 4F, stats, null);
            if (target instanceof LivingEntity le) {
                ctx.status(le, CoreRegistries.ROOTED, 50, 0, stats);
                ctx.status(le, CoreRegistries.BLEEDING, 80, 0, stats);
            }
            HmFx.column(ctx.level(), target.position(), 1.6, HmFx.dust(HmFx.THORN, 1.3F), 10);
            ctx.level().playSound(null, target.blockPosition(), SoundEvents.SWEET_BERRY_BUSH_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
    }

    /**
     * Encanto Feérico (Luz, tier 3): o alvo é enfeitiçado — monstros esquecem você e atacam o monstro mais próximo;
     * jogadores ficam tontos e lentos. Chefes resistem (só a lentidão).
     */
    static final class FeyCharm extends EffectGlyph {
        FeyCharm() {
            super("fey_charm", 16);
            element(Element.LIGHT);
            tier(3);
        }

        @Override
        public int hintDuration() {
            return 120;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            int ticks = stats.duration(120);
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0), ctx.caster());
            boolean boss = com.renanpontes.isekaimahou.core.boss.BossKit.isBossLike(le);
            if (le instanceof Mob mob && !boss) {
                LivingEntity other = null;
                double best = 10 * 10;
                for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(10),
                        e -> e != mob && e.isAlive() && e instanceof Enemy && !ctx.isAlly(e))) {
                    double dd = e.distanceToSqr(mob);
                    if (dd < best) {
                        best = dd;
                        other = e;
                    }
                }
                mob.setTarget(other);
                mob.setLastHurtByMob(other);
            } else if (le instanceof ServerPlayer) {
                le.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks, 0), ctx.caster());
            }
            ctx.level().sendParticles(ParticleTypes.HEART, le.getX(), le.getY() + le.getBbHeight() + 0.3, le.getZ(), 5, 0.3, 0.2, 0.3, 0);
            ctx.level().sendParticles(HmFx.dust(HmFx.FEY_PINK, 1.0F), le.getX(), le.getY() + 1, le.getZ(), 12, 0.4, 0.5, 0.4, 0);
            ctx.level().playSound(null, le.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.5F);
        }
    }

    private HollowGlyphs() {}
}
