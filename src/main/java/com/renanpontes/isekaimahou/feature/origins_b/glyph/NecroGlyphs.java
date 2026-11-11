package com.renanpontes.isekaimahou.feature.origins_b.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.origins_b.ObFx;
import com.renanpontes.isekaimahou.feature.origins_b.ObRegistries;
import com.renanpontes.isekaimahou.feature.origins_b.ObUtil;
import com.renanpontes.isekaimahou.feature.origins_b.entity.BoneWarriorEntity;
import com.renanpontes.isekaimahou.feature.origins_b.entity.ObSummonEntity;
import com.renanpontes.isekaimahou.feature.origins_b.entity.UndeadServantEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de Origem do Necromante: NECROMANCIA (servos, ossos, almas e a Forma de Lich). */
public final class NecroGlyphs {
    /** Contador do Rito Ossuário que aumenta o limite de servos. */
    public static final String SERVANT_CAP = "necro.servant_cap";

    public static List<EffectGlyph> all() {
        return List.of(new RaiseDead(), new BoneShield(), new SoulDrain(), new CorpseExplosion(), new BoneArmy(), new LichForm());
    }

    /** Limite de servos mortos-vivos: 2 + o que o Rito Ossuário liberou (mobs conjuradores: 2). */
    public static int servantCap(LivingEntity owner) {
        if (owner instanceof ServerPlayer sp) return 2 + Math.max(0, MagicApi.get(sp).counter(SERVANT_CAP));
        return 2;
    }

    // =====================================================================================
    //  T1 — Erguer Morto
    // =====================================================================================
    /**
     * Ergue Servos Mortos-Vivos do chão (1 + Dividir) por 60s, no ponto atingido ou perto do alvo (que vira a
     * primeira presa). Limite de servos: 2 + necro.servant_cap; os temporários mais antigos desmoronam para abrir vaga.
     */
    public static class RaiseDead extends EffectGlyph {
        public RaiseDead() {
            super("raise_dead", 16);
            element(Element.ARCANE);
            origin(MagicPath.NECROMANCER);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (ctx.caster() == null) return;
            raise(ctx, PactGlyphs.summonSpot(ctx, target), target != ctx.caster() && target instanceof LivingEntity le ? le : null, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (ctx.caster() == null) return;
            raise(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), null, stats);
        }

        private void raise(SpellContext ctx, Vec3 at, LivingEntity foe, SpellStats stats) {
            LivingEntity owner = ctx.caster();
            if (!ObUtil.oncePerTick(ctx, id().getPath())) return;
            ServerLevel level = ctx.level();
            EntityType<UndeadServantEntity> type = ObRegistries.UNDEAD_SERVANT.get();
            int cap = servantCap(owner);
            int count = ObUtil.makeRoom(level, UndeadServantEntity.class, owner.getUUID(), owner.position(), cap, stats.count());
            if (count <= 0) {
                ObUtil.actionbar(owner, Component.translatable("message.isekai_mahou.origins_b.servant_cap", cap).withStyle(ChatFormatting.DARK_AQUA));
                return;
            }
            for (int i = 0; i < count; i++) {
                UndeadServantEntity servant = type.create(level);
                if (servant == null) return;
                Vec3 p = ObUtil.freeSpot(level, type, at, 1.2);
                float yaw = foe != null ? (float) (Mth.atan2(foe.getZ() - p.z, foe.getX() - p.x) * Mth.RAD_TO_DEG) - 90F
                        : owner.getYRot() + 180F;
                servant.moveTo(p.x, p.y, p.z, yaw, 0);
                servant.setYHeadRot(yaw);
                servant.setYBodyRot(yaw);
                servant.setup(owner, stats.duration(1200), ObUtil.power(ctx, stats));
                level.addFreshEntity(servant);
                if (foe != null && !ctx.isAlly(foe)) servant.setTarget(foe);
                ObFx.soulBurst(level, p, 1.0);
            }
            ObFx.sound(level, at, SoundEvents.SCULK_CATALYST_BLOOM, 1.0F, 0.6F);
            ObFx.sound(level, at, SoundEvents.WITHER_SKELETON_AMBIENT, 0.8F, 0.6F);
        }
    }

    // =====================================================================================
    //  T2 — Escudo de Ossos
    // =====================================================================================
    /** Absorção (3 corações, 6 se fortalecido) por 30s; devolve 35% do dano corpo a corpo enquanto durar. */
    public static class BoneShield extends EffectGlyph {
        public BoneShield() {
            super("bone_shield", 14);
            element(Element.ARCANE);
            origin(MagicPath.NECROMANCER);
            tier(2);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            // Só protege aliados (mesmo com Inverter)
            if (!(target instanceof LivingEntity le) || (target != ctx.caster() && !ctx.isAlly(target))) return;
            int amp = ObUtil.power(ctx, stats) >= 1.6F ? 1 : 0;
            // Reaplicar recarrega a absorção (remove e põe de novo)
            le.removeEffect(ObRegistries.BONE_SHIELD);
            le.addEffect(new MobEffectInstance(ObRegistries.BONE_SHIELD, stats.duration(600), amp, false, false, true), ctx.caster());
            ServerLevel level = ctx.level();
            ObFx.boneBurst(level, le.position().add(0, le.getBbHeight() * 0.5, 0), 24, 0.6);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, le.getX(), le.getY() + 0.1, le.getZ(), 10, 0.5, 0.05, 0.5, 0.02);
            ObFx.sound(level, le.position(), SoundEvents.BONE_BLOCK_PLACE, 1.0F, 0.6F);
            ObFx.sound(level, le.position(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.0F, 0.6F);
        }
    }

    // =====================================================================================
    //  T3 — Drenar Alma
    // =====================================================================================
    /** Dano (5) que cura o conjurador em metade do causado. Se matar, a alma rende XP mágico e orbes extras. */
    public static class SoulDrain extends EffectGlyph {
        public SoulDrain() {
            super("soul_drain", 16);
            element(Element.ARCANE);
            origin(MagicPath.NECROMANCER);
            tier(3);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            // Drenar é sempre ofensivo: aliados (pets, invocações) não viram fonte de cura/XP
            if (!(target instanceof LivingEntity le) || target == ctx.caster() || ctx.isAlly(target)) return;
            ServerLevel level = ctx.level();
            LivingEntity caster = ctx.caster();
            float before = le.getHealth();
            ctx.damage(le, 5.0F, stats, null);
            float dealt = Math.max(0, before - le.getHealth());
            Vec3 from = le.position().add(0, le.getBbHeight() * 0.6, 0);
            if (caster != null && caster.isAlive()) {
                if (dealt > 0) caster.heal(dealt * 0.5F);
                ObFx.soulStream(level, from, caster.position().add(0, caster.getBbHeight() * 0.6, 0), ObFx.SOUL_CYAN);
            }
            level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y, from.z, 6, 0.25, 0.3, 0.25, 0.03);
            ObFx.sound(level, from, SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.8F);
            if (!le.isAlive()) {
                // Colheita: a alma do morto vira poder
                int xp = 4 + Math.round(Math.min(40F, le.getMaxHealth()) / 4F);
                if (ctx.casterPlayer() != null) MagicApi.giveXp(ctx.casterPlayer(), xp, "soul_drain");
                ExperienceOrb.award(level, le.position(), 2 + Math.round(Math.min(40F, le.getMaxHealth()) / 8F));
                ObFx.soulBurst(level, le.position(), le.getBbHeight());
                ObFx.sound(level, from, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5F, 1.6F);
            }
        }
    }

    // =====================================================================================
    //  T3 — Explodir Cadáver
    // =====================================================================================
    /**
     * Detona um "cadáver": o morto-vivo atingido (não chefe), ou o seu servo mais perto do ponto, ou um morto-vivo
     * próximo. Dano em área (8 + 30% da vida máxima do cadáver, até +20), sem quebrar blocos.
     */
    public static class CorpseExplosion extends EffectGlyph {
        public CorpseExplosion() {
            super("corpse_explosion", 18);
            element(Element.ARCANE);
            origin(MagicPath.NECROMANCER);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (ctx.caster() == null) return;
            Vec3 focus = target == ctx.caster() ? ctx.caster().position().add(ctx.look().scale(4)) : target.position();
            detonateAround(ctx, focus, target != ctx.caster() && target instanceof LivingEntity le ? le : null, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (ctx.caster() == null) return;
            detonateAround(ctx, hit.getLocation(), null, stats);
        }

        private void detonateAround(SpellContext ctx, Vec3 focus, LivingEntity hit, SpellStats stats) {
            if (!ObUtil.oncePerTick(ctx, id().getPath())) return;
            List<LivingEntity> corpses = pick(ctx, focus, hit, stats.count());
            if (corpses.isEmpty()) {
                ObUtil.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.origins_b.no_corpse").withStyle(ChatFormatting.GRAY));
                return;
            }
            for (LivingEntity corpse : corpses) detonate(ctx, corpse, stats);
        }

        /**
         * Cadáver válido: Servo Morto-Vivo/Guerreiro de Ossos TEMPORÁRIO seu (os permanentes do Rito Ossuário e os seus
         * pets, como um cavalo esqueleto domado, nunca), ou morto-vivo "comum" (não chefe, até 60 de vida).
         * Criaturas de outros jogadores só com PvP ligado (as de mobs, como servos de cultistas, sempre).
         */
        private static boolean isCorpse(SpellContext ctx, LivingEntity e) {
            if (!e.isAlive() || e == ctx.caster() || ObUtil.isBoss(e)) return false;
            if (e instanceof OwnableEntity oe && oe.getOwnerUUID() != null) {
                if (ctx.casterId() != null && ctx.casterId().equals(oe.getOwnerUUID())) {
                    return (e instanceof UndeadServantEntity || e instanceof BoneWarriorEntity) && ((ObSummonEntity) e).isTemporary();
                }
                if (!ctx.level().getServer().isPvpAllowed() && !(ctx.level().getEntity(oe.getOwnerUUID()) instanceof Mob)) return false;
            }
            return ObUtil.isUndead(e) && e.getMaxHealth() <= 60F;
        }

        /** Ordem: o atingido (se for morto-vivo), os seus servos perto do ponto, outros mortos-vivos inimigos perto. */
        private static List<LivingEntity> pick(SpellContext ctx, Vec3 focus, LivingEntity hit, int max) {
            List<LivingEntity> out = new ArrayList<>();
            if (hit != null && ObUtil.isUndead(hit) && isCorpse(ctx, hit)) out.add(hit);
            ServerLevel level = ctx.level();
            List<LivingEntity> mine = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(focus, focus).inflate(16),
                    e -> isCorpse(ctx, e) && e instanceof OwnableEntity oe && ctx.casterId() != null && ctx.casterId().equals(oe.getOwnerUUID())));
            mine.sort(Comparator.comparingDouble(e -> e.distanceToSqr(focus)));
            List<LivingEntity> foes = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(focus, focus).inflate(8),
                    e -> isCorpse(ctx, e) && !ctx.isAlly(e)));
            foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(focus)));
            for (LivingEntity e : mine) if (out.size() < max && !out.contains(e)) out.add(e);
            for (LivingEntity e : foes) if (out.size() < max && !out.contains(e)) out.add(e);
            return out;
        }

        private static void detonate(SpellContext ctx, LivingEntity corpse, SpellStats stats) {
            ServerLevel level = ctx.level();
            Vec3 center = corpse.position().add(0, corpse.getBbHeight() * 0.5, 0);
            float radius = stats.radius(4.0F);
            float base = 8.0F + Math.min(20.0F, corpse.getMaxHealth() * 0.3F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                    e -> e.isAlive() && e != corpse && !ctx.isAlly(e) && e.distanceToSqr(center) <= radius * radius)) {
                float falloff = 1.0F - 0.5F * (float) (Math.sqrt(e.distanceToSqr(center)) / radius);
                ctx.damage(e, base * falloff, stats, null);
                Vec3 push = e.position().subtract(center).multiply(1, 0, 1);
                if (push.lengthSqr() < 1.0E-4) push = new Vec3(0, 0, 1);
                push = push.normalize().scale(0.9 * falloff);
                e.push(push.x, 0.35 * falloff, push.z);
                e.hurtMarked = true;
            }
            // O cadáver vai junto: servo se desfaz; morto-vivo inimigo é destruído (abate creditado)
            if (corpse instanceof ObSummonEntity s && ctx.casterId() != null && ctx.casterId().equals(s.getOwnerUUID())) {
                s.dissolve();
            } else {
                corpse.invulnerableTime = 0;
                corpse.hurt(ctx.damageSource(null), Math.max(1000F, corpse.getMaxHealth() * 10F));
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            ObFx.boneBurst(level, center, 40, 0.5);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z, 30, radius * 0.3, 0.4, radius * 0.3, 0.08);
            level.sendParticles(ParticleTypes.SOUL, center.x, center.y, center.z, 12, 0.5, 0.5, 0.5, 0.06);
            level.sendParticles(ObFx.dust(ObFx.SOUL_CYAN, 1.6F), center.x, center.y, center.z, 24, radius * 0.35, 0.6, radius * 0.35, 0);
            ObFx.sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.9F);
            ObFx.sound(level, center, SoundEvents.SKELETON_DEATH, 1.0F, 0.6F);
        }
    }

    // =====================================================================================
    //  T4 — Exército de Ossos
    // =====================================================================================
    /** Levanta 3 Guerreiros de Ossos (+1 por Dividir) por 40s. Até 6 guerreiros por dono. */
    public static class BoneArmy extends EffectGlyph {
        public BoneArmy() {
            super("bone_army", 32);
            element(Element.ARCANE);
            origin(MagicPath.NECROMANCER);
            tier(4);
            minCircle(4);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (ctx.caster() == null) return;
            raise(ctx, PactGlyphs.summonSpot(ctx, target), target != ctx.caster() && target instanceof LivingEntity le ? le : null, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (ctx.caster() == null) return;
            raise(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), null, stats);
        }

        private void raise(SpellContext ctx, Vec3 at, LivingEntity foe, SpellStats stats) {
            LivingEntity owner = ctx.caster();
            if (!ObUtil.oncePerTick(ctx, id().getPath())) return;
            ServerLevel level = ctx.level();
            EntityType<BoneWarriorEntity> type = ObRegistries.BONE_WARRIOR.get();
            int wanted = 2 + stats.count();
            int count = ObUtil.makeRoom(level, BoneWarriorEntity.class, owner.getUUID(), owner.position(), 6, wanted);
            float yawBase = foe != null ? (float) (Mth.atan2(foe.getZ() - at.z, foe.getX() - at.x) * Mth.RAD_TO_DEG) - 90F : owner.getYRot();
            for (int i = 0; i < count; i++) {
                BoneWarriorEntity w = type.create(level);
                if (w == null) return;
                // Em formação: leque de frente para o inimigo (ou para onde o dono olha)
                double ang = Math.toRadians(yawBase + 90 + (i - (count - 1) / 2.0) * 38);
                Vec3 spot = at.add(Math.cos(ang) * 1.6, 0, Math.sin(ang) * 1.6);
                Vec3 p = ObUtil.freeSpot(level, type, spot, 0.6);
                w.moveTo(p.x, p.y, p.z, yawBase, 0);
                w.setYHeadRot(yawBase);
                w.setYBodyRot(yawBase);
                w.setup(owner, stats.duration(800), ObUtil.power(ctx, stats));
                level.addFreshEntity(w);
                if (foe != null && !ctx.isAlly(foe)) w.setTarget(foe);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.1, p.z, 12, 0.3, 0.05, 0.3, 0.03);
            }
            ObFx.sound(level, at, SoundEvents.SKELETON_HORSE_AMBIENT, 1.0F, 0.6F);
            ObFx.sound(level, at, SoundEvents.BONE_BLOCK_BREAK, 1.2F, 0.6F);
        }
    }

    // =====================================================================================
    //  T5 — Forma de Lich (lendário)
    // =====================================================================================
    /** O conjurador vira um lich por 30s: voo lento, resistência, golpes que drenam, aura de almas. */
    public static class LichForm extends EffectGlyph {
        public LichForm() {
            super("lich_form", 75);
            element(Element.ARCANE);
            origin(MagicPath.NECROMANCER);
            legendary();
            minCircle(6);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        /** É uma transformação do próprio conjurador, qualquer que seja a forma da magia. */
        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (caster == null || !ObUtil.oncePerTick(ctx, id().getPath())) return;
            caster.addEffect(new MobEffectInstance(ObRegistries.LICH_FORM, stats.duration(600), 0, false, false, true), caster);
            if (caster instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.origins_b.lich_rise").withStyle(ChatFormatting.DARK_AQUA,
                        ChatFormatting.BOLD), true);
            }
        }
    }

    private NecroGlyphs() {}
}
