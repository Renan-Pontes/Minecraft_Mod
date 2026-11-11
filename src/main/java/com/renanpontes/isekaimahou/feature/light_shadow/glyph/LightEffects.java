package com.renanpontes.isekaimahou.feature.light_shadow.glyph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;
import com.renanpontes.isekaimahou.feature.light_shadow.LsRegistries;
import com.renanpontes.isekaimahou.feature.light_shadow.LsUtil;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.GuardianFairyEntity;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.HolyZoneEntity;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.SummonUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.MossBlock;
import net.minecraft.world.level.block.NyliumBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de efeito de LUZ. */
public final class LightEffects {

    /** Raio Sagrado (T1): um raio desce do céu sobre o alvo. Dano dobrado em mortos-vivos e demônios (que também queimam). */
    public static class HolyRay extends EffectGlyph {
        public HolyRay() {
            super("holy_ray", 9);
            element(Element.LIGHT);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel level = ctx.level();
            boolean unholy = LsUtil.isUnholy(target);
            LsFx.skyRay(level, target.position(), 6 + target.getBbHeight(), unholy ? 1.4F : 1.0F, LsFx.GOLD);
            float dmg = 5.0F;
            if (unholy) {
                // Dobro no total: mortos-vivos já levam ×1,5 de Luz pelo núcleo (ElementReactions)
                dmg *= LsUtil.isUndead(target) ? 2.0F / 1.5F : 2.0F;
                target.igniteForSeconds(3);
                level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 6, 0.25, 0.3, 0.25, 0.03);
            }
            ctx.damage(target, dmg, stats, null);
            LsFx.sound(level, target.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.8F);
            LsFx.sound(level, target.position(), SoundEvents.BEACON_POWER_SELECT, 0.5F, 2.0F);
        }
    }

    /** Curar (T1): cura o aliado. Invertido em inimigos, a luz queima (mais forte em mortos-vivos). */
    public static class Heal extends EffectGlyph {
        public Heal() {
            super("heal", 8);
            element(Element.LIGHT);
            tier(1);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            if (ctx.isAlly(target)) {
                ctx.heal(le, 6.0F, stats);
                LsFx.spiral(level, le, LsFx.dust(LsFx.WHITE_GOLD, 0.9F), 18, le.getBbWidth() * 0.7 + 0.3);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 8, 0.35, 0.4, 0.35, 0);
                LsFx.sound(level, le.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
            } else {
                ctx.damage(target, LsUtil.isUnholy(target) ? 8.0F : 4.0F, stats, null);
                LsFx.holyBurst(level, LsFx.mid(target), 14, 0.3);
                LsFx.sound(level, target.position(), SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 0.7F);
            }
        }
    }

    /** Regenerar (T2): Regeneração II por 12s (≈10 de vida, III com mais poder). Invertido: Murchar. */
    public static class Regenerate extends EffectGlyph {
        public Regenerate() {
            super("regenerate", 12);
            element(Element.LIGHT);
            tier(2);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            int amp = stats.power() >= 1.7F ? 2 : 1;
            if (ctx.isAlly(target)) {
                ctx.status(le, MobEffects.REGENERATION, 240, amp, stats);
                LsFx.spiral(ctx.level(), le, LsFx.fade(LsFx.WHITE_GOLD, 0x9CFFB0, 0.8F), 24, le.getBbWidth() * 0.7 + 0.35);
                LsFx.sound(ctx.level(), le.position(), SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.8F);
            } else {
                ctx.status(le, MobEffects.WITHER, 120, amp - 1, stats);
                ctx.level().sendParticles(ParticleTypes.SMOKE, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    /** Cegar (T2): luz ofuscante — Cegueira em jogadores e Ofuscado (mobs perdem o alvo). */
    public static class Blind extends EffectGlyph {
        public Blind() {
            super("blind", 12);
            element(Element.LIGHT);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            // Chefes só piscam: ficar sem alvo por 5s os deixaria inofensivos
            ctx.status(le, LsRegistries.HOLY_DAZZLE, LsUtil.isBoss(le) ? 15 : 100, 0, stats);
            if (le instanceof Player) ctx.status(le, MobEffects.BLINDNESS, 100, 0, stats);
            level.sendParticles(ParticleTypes.FLASH, le.getX(), le.getEyeY(), le.getZ(), 1, 0, 0, 0, 0);
            LsFx.holyBurst(level, new Vec3(le.getX(), le.getEyeY(), le.getZ()), 12, 0.25);
            LsFx.sound(level, le.position(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.8F, 1.4F);
        }
    }

    /** Revelar (T2): Brilho no alvo; invisíveis perdem a invisibilidade e ficam Expostos. Em si mesmo, revela a área. */
    public static class Reveal extends EffectGlyph {
        public Reveal() {
            super("reveal", 10);
            element(Element.LIGHT);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel level = ctx.level();
            boolean self = target == ctx.caster();
            Vec3 center = target.position();
            double radius = self ? stats.radius(12F) : stats.radius(4F);
            List<LivingEntity> list = new ArrayList<>();
            if (!self && target instanceof LivingEntity le) list.add(le);
            for (LivingEntity e : ctx.enemiesAround(center, radius)) {
                if (!list.contains(e) && (self || e.isInvisible())) list.add(e);
            }
            for (LivingEntity e : list) reveal(ctx, e, stats);
            if (self) LsFx.ring(level, center.add(0, 0.2, 0), radius, LsFx.dust(LsFx.GOLD, 1.0F), 48);
            LsFx.sound(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.5F);
        }

        private static void reveal(SpellContext ctx, LivingEntity e, SpellStats stats) {
            ctx.status(e, MobEffects.GLOWING, 200, 0, stats);
            if (e.isInvisible() || e.hasEffect(MobEffects.INVISIBILITY)) {
                e.removeEffect(MobEffects.INVISIBILITY);
                ctx.status(e, CoreRegistries.EXPOSED, 200, 0, stats);
                ctx.level().sendParticles(ParticleTypes.FLASH, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0, 0, 0, 0);
            }
            LsFx.holyBurst(ctx.level(), LsFx.mid(e), 8, 0.3);
        }
    }

    /** Purificar (T3): remove efeitos negativos e apaga o fogo. Invertido em inimigos: arranca os efeitos positivos. */
    public static class Purify extends EffectGlyph {
        public Purify() {
            super("purify", 16);
            element(Element.LIGHT);
            tier(3);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            boolean ally = ctx.isAlly(target);
            MobEffectCategory remove = ally ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL;
            int removed = 0;
            for (MobEffectInstance inst : new ArrayList<>(le.getActiveEffects())) {
                // A Exaustão Mágica é o preço de um Domínio: a luz não a desfaz
                if (inst.getEffect().is(CoreRegistries.MAGIC_EXHAUSTION.getKey())) continue;
                if (inst.getEffect().value().getCategory() == remove && le.removeEffect(inst.getEffect())) removed++;
            }
            if (ally) {
                le.clearFire();
                le.setTicksFrozen(0);
                LsFx.spiral(level, le, LsFx.dust(0xFFFFFF, 1.0F), 20, le.getBbWidth() * 0.7 + 0.3);
            } else if (LsUtil.isUnholy(le)) {
                ctx.damage(le, 4.0F, stats, null);
            }
            level.sendParticles(ParticleTypes.END_ROD, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 6 + removed * 4, 0.3, 0.5, 0.3, 0.05);
            LsFx.sound(level, le.position(), removed > 0 ? SoundEvents.ZOMBIE_VILLAGER_CURE : SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.5F);
        }
    }

    /** Bênção (T3): Força + Absorção. Invertido em inimigos: Fraqueza e Lentidão. */
    public static class Blessing extends EffectGlyph {
        public Blessing() {
            super("blessing", 18);
            element(Element.LIGHT);
            tier(3);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            int amp = stats.power() >= 1.6F ? 1 : 0;
            if (ctx.isAlly(target)) {
                ctx.status(le, MobEffects.DAMAGE_BOOST, 600, amp, stats);
                ctx.status(le, MobEffects.ABSORPTION, 600, 1 + amp, stats);
                LsFx.skyRay(level, le.position(), 4 + le.getBbHeight(), 0.8F, LsFx.WHITE_GOLD);
                LsFx.sound(level, le.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F);
            } else {
                ctx.status(le, MobEffects.WEAKNESS, 300, amp, stats);
                ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, 300, amp, stats);
                level.sendParticles(ParticleTypes.SMOKE, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    /** Luz Solar (T3): queima mortos-vivos como o sol, ofusca os outros e faz as plantas crescerem. */
    public static class Sunlight extends EffectGlyph {
        public Sunlight() {
            super("sunlight", 15);
            element(Element.LIGHT);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel level = ctx.level();
            LsFx.skyRay(level, target.position(), 8 + target.getBbHeight(), 1.6F, 0xFFD24A);
            if (LsUtil.isUnholy(target)) {
                target.igniteForSeconds(stats.duration(160) / 20F);
                ctx.damage(target, 4.0F, stats, null);
            } else {
                ctx.damage(target, 2.0F, stats, null);
                if (target instanceof LivingEntity le && !LsUtil.isBoss(le)) ctx.status(le, LsRegistries.HOLY_DAZZLE, 40, 0, stats);
            }
            LsFx.sound(level, target.position(), SoundEvents.FIRECHARGE_USE, 0.6F, 1.6F);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel level = ctx.level();
            BlockPos c = hit.getBlockPos();
            int r = Math.round(stats.radius(2.0F));
            int grown = 0;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -1, -r), c.offset(r, 2, r))) {
                BlockState st = level.getBlockState(p);
                // Só plantas: grama/nylium/musgo espalhariam mato e flores por todo lado
                if (st.getBlock() instanceof BonemealableBlock bm && !(bm instanceof GrassBlock || bm instanceof NyliumBlock
                        || bm instanceof MossBlock) && bm.isValidBonemealTarget(level, p, st)
                        && level.random.nextFloat() < 0.6F * stats.power()) {
                    bm.performBonemeal(level, level.random, p.immutable(), st);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
                    grown++;
                }
            }
            Vec3 at = Vec3.atBottomCenterOf(c.above());
            LsFx.skyRay(level, at, 8, 1.6F, 0xFFD24A);
            if (grown > 0) LsFx.sound(level, at, SoundEvents.BONE_MEAL_USE, 1.0F, 1.0F);
        }
    }

    /** Invocar Fada Guardiã (T3): uma fada que cura os aliados e afasta mortos-vivos. */
    public static class SummonGuardianFairy extends EffectGlyph {
        public SummonGuardianFairy() {
            super("summon_guardian_fairy", 20);
            element(Element.LIGHT);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            spawn(ctx, target.position().add(0, target.getBbHeight() + 0.2, 0), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            spawn(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())).add(0, 0.5, 0), stats);
        }

        private static void spawn(SpellContext ctx, Vec3 at, SpellStats stats) {
            LivingEntity owner = ctx.caster();
            if (owner == null || !LsUtil.oncePerTick(ctx, "fairy")) return;
            ServerLevel level = ctx.level();
            EntityType<GuardianFairyEntity> type = LsRegistries.GUARDIAN_FAIRY.get();
            for (int i = 0; i < stats.count(); i++) {
                SummonUtil.limit(level, GuardianFairyEntity.class, owner.getUUID(), owner.position(), 3);
                GuardianFairyEntity fairy = type.create(level);
                if (fairy == null) return;
                Vec3 p = SummonUtil.freeSpot(level, type, at);
                fairy.moveTo(p.x, p.y, p.z, owner.getYRot(), 0);
                fairy.setup(owner, stats.duration(1200), stats.power() * ctx.powerMult() * ctx.casterSpellPower());
                level.addFreshEntity(fairy);
                LsFx.holyBurst(level, p.add(0, 0.4, 0), 24, 0.35);
                level.sendParticles(ParticleTypes.FLASH, p.x, p.y + 0.4, p.z, 1, 0, 0, 0, 0);
            }
            LsFx.sound(level, at, SoundEvents.ALLAY_ITEM_GIVEN, 1.0F, 1.2F);
            LsFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.4F);
        }
    }

    /** Barreira Sagrada (T4): domo onde mortos-vivos e demônios inimigos não entram (são empurrados e queimam). */
    public static class SacredBarrier extends EffectGlyph {
        public SacredBarrier() {
            super("sacred_barrier", 26);
            element(Element.LIGHT);
            tier(4);
            minCircle(3);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            place(ctx, target.position(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            place(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), stats);
        }

        private static void place(SpellContext ctx, Vec3 at, SpellStats stats) {
            if (!LsUtil.oncePerTick(ctx, "sacred_barrier")) return;
            ServerLevel level = ctx.level();
            var type = LsRegistries.SACRED_BARRIER_ZONE.get();
            HolyZoneEntity.limit(level, ctx.casterId(), at, type);
            HolyZoneEntity zone = type.create(level);
            if (zone == null) return;
            zone.moveTo(at.x, at.y, at.z, 0, 0);
            zone.setup(ctx.casterId(), stats.radius(5.0F), stats.duration(400), stats.power() * ctx.powerMult() * ctx.casterSpellPower());
            level.addFreshEntity(zone);
        }
    }

    /** Exorcizar (T4): dano massivo em mortos-vivos, demônios e sombras; pouco nos demais. */
    public static class Exorcise extends EffectGlyph {
        public Exorcise() {
            super("exorcise", 24);
            element(Element.LIGHT);
            tier(4);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel level = ctx.level();
            if (LsUtil.isUnholy(target)) {
                LsFx.skyRay(level, target.position(), 10 + target.getBbHeight(), 2.2F, LsFx.WHITE_GOLD);
                ctx.damage(target, 20.0F, stats, null);
                level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 20, 0.3, 0.6, 0.3, 0.08);
                level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
                LsFx.sound(level, target.position(), SoundEvents.ZOMBIE_VILLAGER_CURE, 1.0F, 1.6F);
                LsFx.sound(level, target.position(), SoundEvents.BEACON_DEACTIVATE, 1.0F, 2.0F);
            } else {
                LsFx.skyRay(level, target.position(), 5 + target.getBbHeight(), 0.9F, LsFx.WHITE_GOLD);
                ctx.damage(target, 5.0F, stats, null);
                LsFx.sound(level, target.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.9F);
            }
        }
    }

    /** Prisma (T4): a luz se divide em 3 feixes coloridos que ricocheteiam entre inimigos próximos. */
    public static class Prism extends EffectGlyph {
        private static final int[] COLORS = {0xFF5A5A, 0x6AFF7A, 0x6AA8FF, 0xFFE27A, 0xD27AFF};

        public Prism() {
            super("prism", 25);
            element(Element.LIGHT);
            tier(4);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel level = ctx.level();
            ctx.damage(target, 6.0F, stats, null);
            Vec3 origin = LsFx.mid(target);
            level.sendParticles(ParticleTypes.FLASH, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
            LsFx.sound(level, origin, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 1.4F);
            Set<UUID> hit = new HashSet<>();
            hit.add(target.getUUID());
            int beams = 3 + Math.max(0, stats.split);
            int bounces = 1 + Math.max(0, stats.ricochet);
            float range = stats.radius(10.0F);
            for (int b = 0; b < beams; b++) {
                Entity from = target;
                int color = COLORS[b % COLORS.length];
                for (int j = 0; j <= bounces; j++) {
                    LivingEntity next = nearest(ctx, from, j == 0 ? range : range * 0.8F, hit);
                    if (next == null) break;
                    hit.add(next.getUUID());
                    LsFx.line(level, LsFx.mid(from), LsFx.mid(next), LsFx.dust(color, 1.1F), 5);
                    level.sendParticles(ParticleTypes.END_ROD, next.getX(), next.getY() + next.getBbHeight() * 0.5, next.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
                    ctx.damage(next, j == 0 ? 4.0F : 3.0F, stats, null);
                    LsFx.sound(level, next.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.2F + 0.2F * b);
                    from = next;
                }
            }
        }

        private static LivingEntity nearest(SpellContext ctx, Entity from, float range, Set<UUID> hit) {
            LivingEntity best = null;
            double bestD = range * range;
            for (LivingEntity e : ctx.enemiesAround(from.position(), range)) {
                if (hit.contains(e.getUUID()) || !e.hasLineOfSight(from)) continue;
                double d = e.distanceToSqr(from);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            return best;
        }
    }

    /** Santuário (T4): zona sagrada que cura os aliados e dá Resistência. */
    public static class Sanctuary extends EffectGlyph {
        public Sanctuary() {
            super("sanctuary", 28);
            element(Element.LIGHT);
            tier(4);
            minCircle(3);
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
            place(ctx, target.position(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            place(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), stats);
        }

        private static void place(SpellContext ctx, Vec3 at, SpellStats stats) {
            if (!LsUtil.oncePerTick(ctx, "sanctuary")) return;
            ServerLevel level = ctx.level();
            var type = LsRegistries.SANCTUARY_ZONE.get();
            HolyZoneEntity.limit(level, ctx.casterId(), at, type);
            HolyZoneEntity zone = type.create(level);
            if (zone == null) return;
            zone.moveTo(at.x, at.y, at.z, 0, 0);
            zone.setup(ctx.casterId(), stats.radius(4.5F), stats.duration(300), stats.power() * ctx.powerMult() * ctx.casterSpellPower());
            level.addFreshEntity(zone);
        }
    }

    /**
     * Ressurreição (T5, lendário): Graça da Ressurreição por 60s — ao morrer, o alvo é salvo, curado e
     * a luz cura os aliados em volta (ver LsEvents). Invertido em mortos-vivos inimigos: desfaz a não-vida.
     */
    public static class Resurrection extends EffectGlyph {
        public Resurrection() {
            super("resurrection", 60);
            element(Element.LIGHT);
            legendary();
            minCircle(8);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            if (ctx.isAlly(target)) {
                ctx.status(le, LsRegistries.RESURRECTION_GRACE, 1200, 0, stats);
                LsFx.skyRay(level, le.position(), 12, 1.8F, LsFx.WHITE_GOLD);
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 40, 0.4, 0.6, 0.4, 0.3);
                LsFx.sound(level, le.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.5F);
                LsFx.sound(level, le.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.8F);
            } else if (LsUtil.isUnholy(target)) {
                LsFx.skyRay(level, le.position(), 12, 2.4F, LsFx.WHITE_GOLD);
                ctx.damage(le, 30.0F, stats, null);
                level.sendParticles(ParticleTypes.SOUL, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 30, 0.4, 0.7, 0.4, 0.1);
                LsFx.sound(level, le.position(), SoundEvents.ZOMBIE_VILLAGER_CURE, 1.2F, 1.2F);
            }
        }
    }

    public static List<EffectGlyph> all() {
        return List.of(new HolyRay(), new Heal(), new Regenerate(), new Blind(), new Reveal(), new Purify(), new Blessing(),
                new Sunlight(), new SummonGuardianFairy(), new SacredBarrier(), new Exorcise(), new Prism(), new Sanctuary(),
                new Resurrection());
    }

    private LightEffects() {}
}
