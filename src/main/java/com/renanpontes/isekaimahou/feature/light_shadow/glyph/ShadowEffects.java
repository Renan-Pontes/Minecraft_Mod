package com.renanpontes.isekaimahou.feature.light_shadow.glyph;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;
import com.renanpontes.isekaimahou.feature.light_shadow.LsRegistries;
import com.renanpontes.isekaimahou.feature.light_shadow.LsUtil;
import com.renanpontes.isekaimahou.feature.light_shadow.ai.LsAi;
import com.renanpontes.isekaimahou.feature.light_shadow.effect.ShackledEffect;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.LivingShadowEntity;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.SummonUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de efeito de SOMBRA. */
public final class ShadowEffects {

    /** Partículas de "vida" saindo do alvo e indo para o conjurador. */
    static void drainFx(ServerLevel level, Entity from, LivingEntity to) {
        if (to == null) return;
        LsFx.line(level, LsFx.mid(from), LsFx.mid(to), LsFx.fade(LsFx.BLOOD, LsFx.VIOLET, 0.9F), 4);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, from.getX(), from.getY() + from.getBbHeight() * 0.6, from.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
    }

    /** Drenar Vida (T1): dano de sombra e o conjurador recupera metade do que tirou. */
    public static class LifeDrain extends EffectGlyph {
        public LifeDrain() {
            super("life_drain", 9);
            element(Element.SHADOW);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel level = ctx.level();
            float before = target instanceof LivingEntity le ? le.getHealth() : 0;
            ctx.damage(target, 4.0F, stats, null);
            LivingEntity caster = ctx.caster();
            if (caster != null && target instanceof LivingEntity le && caster.isAlive()) {
                float dealt = Math.max(0, before - le.getHealth());
                if (dealt > 0) caster.heal(dealt * 0.5F);
                drainFx(level, target, caster);
            }
            LsFx.sound(level, target.position(), SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.8F);
        }
    }

    /** Enfraquecer (T1): Fraqueza (e Cansaço com mais poder). */
    public static class Weaken extends EffectGlyph {
        public Weaken() {
            super("weaken", 7);
            element(Element.SHADOW);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            int amp = stats.power() >= 1.6F ? 1 : 0;
            ctx.status(le, MobEffects.WEAKNESS, 200, amp, stats);
            if (amp > 0) ctx.status(le, MobEffects.DIG_SLOWDOWN, 200, 0, stats);
            ctx.damage(le, 1.5F, stats, null);
            ctx.level().sendParticles(LsFx.dust(LsFx.DEEP_VIOLET, 1.2F), le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 14, 0.3, 0.4, 0.3, 0);
            ctx.level().sendParticles(Vfx.ink(), le.getX(), le.getY() + le.getBbHeight() * 0.7, le.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            LsFx.sound(ctx.level(), le.position(), SoundEvents.WITCH_CELEBRATE, 0.4F, 0.6F);
        }
    }

    /** Medo (T2): mobs largam o alvo e fogem. Em si mesmo, aterroriza os inimigos em volta. */
    public static class Fear extends EffectGlyph {
        public Fear() {
            super("fear", 12);
            element(Element.SHADOW);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel level = ctx.level();
            Vec3 src = ctx.caster() != null ? ctx.caster().position() : target.position();
            if (target == ctx.caster()) {
                float r = stats.radius(6.0F);
                for (LivingEntity e : ctx.enemiesAround(target.position(), r)) frighten(ctx, e, src, stats);
                LsFx.ring(level, target.position().add(0, 0.2, 0), r, LsFx.dust(LsFx.DEEP_VIOLET, 1.3F), 40);
                LsFx.sound(level, target.position(), SoundEvents.WARDEN_ROAR, 0.6F, 1.6F);
                return;
            }
            if (target instanceof LivingEntity le) frighten(ctx, le, src, stats);
            LsFx.sound(level, target.position(), SoundEvents.GHAST_SCREAM, 0.5F, 0.6F);
        }

        static void frighten(SpellContext ctx, LivingEntity e, Vec3 src, SpellStats stats) {
            frighten(ctx, e, src, stats, 120);
        }

        /** Amedronta por {@code baseTicks} (ajustado pelos aumentos). Chefes não fogem: só ficam lentos. */
        static void frighten(SpellContext ctx, LivingEntity e, Vec3 src, SpellStats stats, int baseTicks) {
            if (LsUtil.isBoss(e)) {
                ctx.status(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 0, stats);
                return;
            }
            LsAi.setFearSource(e, src);
            ctx.status(e, LsRegistries.SHADOW_FEAR, baseTicks, 0, stats);
            if (e instanceof Mob mob) LsAi.clearTarget(mob);
            // Foge já, sem esperar o próximo tick do efeito (o alvo nunca é o próprio conjurador)
            if (e instanceof PathfinderMob pm && e.hasEffect(LsRegistries.SHADOW_FEAR)) LsAi.ensureFlee(pm);
            ctx.level().sendParticles(Vfx.sculkSoul(), e.getX(), e.getEyeY() + 0.3, e.getZ(), 5, 0.2, 0.2, 0.2, 0.03);
        }
    }

    /** Escuridão (T2): Escuridão + Cegueira; mobs ficam Ofuscados e perdem o alvo. */
    public static class Darkness extends EffectGlyph {
        public Darkness() {
            super("darkness", 11);
            element(Element.SHADOW);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            ctx.status(le, MobEffects.DARKNESS, 160, 0, stats);
            ctx.status(le, MobEffects.BLINDNESS, 80, 0, stats);
            if (!(le instanceof Player)) ctx.status(le, LsRegistries.HOLY_DAZZLE, LsUtil.isBoss(le) ? 15 : 80, 0, stats);
            level.sendParticles(Vfx.ink(), le.getX(), le.getEyeY(), le.getZ(), 30, 0.4, 0.4, 0.4, 0.05);
            level.sendParticles(Vfx.largeSmoke(), le.getX(), le.getEyeY(), le.getZ(), 12, 0.4, 0.3, 0.4, 0.01);
            LsFx.sound(level, le.position(), SoundEvents.WARDEN_NEARBY_CLOSE, 0.8F, 1.0F);
        }
    }

    /** Passo Sombrio (T2): teleporta o conjurador para as costas do alvo (em si mesmo: do inimigo mais perto à frente). */
    public static class ShadowStep extends EffectGlyph {
        public ShadowStep() {
            super("shadow_step", 12);
            element(Element.SHADOW);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (caster == null || !LsUtil.oncePerTick(ctx, "shadow_step")) return;
            ServerLevel level = ctx.level();
            Entity dest = target;
            if (target == caster) {
                dest = enemyAhead(ctx, caster, stats.radius(16F));
                if (dest == null) {
                    blinkForward(ctx, caster, stats.radius(8F));
                    return;
                }
            }
            Vec3 back = Vec3.directionFromRotation(0, dest.getYRot()).scale(-1);
            double dist = dest.getBbWidth() * 0.5 + caster.getBbWidth() * 0.5 + 0.8;
            Vec3[] tries = {back, back.yRot((float) Math.PI / 2), back.yRot((float) -Math.PI / 2), back.scale(-1)};
            Vec3 found = null;
            // 1ª passada: só pontos com chão logo abaixo; 2ª: qualquer ponto livre (alvo voando, por exemplo)
            for (int pass = 0; pass < 2 && found == null; pass++) {
                for (Vec3 dir : tries) {
                    for (int dy = 0; dy <= 1 && found == null; dy++) {
                        Vec3 p = dest.position().add(dir.scale(dist)).add(0, dy, 0);
                        AABB box = caster.getBoundingBox().move(p.subtract(caster.position()));
                        if (!level.noCollision(caster, box)) continue;
                        if (pass == 0 && level.noCollision(caster, box.expandTowards(0, -2.5, 0))) continue;
                        found = p;
                    }
                    if (found != null) break;
                }
            }
            if (found != null) {
                Vec3 look = dest.position().subtract(found);
                float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90F;
                teleport(level, caster, found, yaw);
                if (dest instanceof Mob m && m.getTarget() == caster) LsAi.clearTarget(m);
                if (dest instanceof LivingEntity le) ctx.status(le, MobEffects.CONFUSION, 40, 0, stats);
                return;
            }
            if (caster instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.shadow_step_blocked").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        }

        private static Entity enemyAhead(SpellContext ctx, LivingEntity caster, float range) {
            Vec3 look = caster.getLookAngle();
            Entity best = null;
            double bestScore = Double.MAX_VALUE;
            for (LivingEntity e : ctx.enemiesAround(caster.position(), range)) {
                Vec3 to = e.position().subtract(caster.position());
                if (to.normalize().dot(look) < 0.6 || !caster.hasLineOfSight(e)) continue;
                double score = to.lengthSqr();
                if (score < bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
            return best;
        }

        private static void blinkForward(SpellContext ctx, LivingEntity caster, float range) {
            ServerLevel level = ctx.level();
            Vec3 eye = caster.getEyePosition();
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(caster.getLookAngle().scale(range)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? hit.getLocation() : hit.getLocation().subtract(caster.getLookAngle().scale(0.8));
            Vec3 foot = new Vec3(end.x, end.y - caster.getEyeHeight(), end.z);
            for (int i = 0; i < 4; i++) {
                Vec3 p = foot.add(0, i * 0.5, 0);
                if (level.noCollision(caster, caster.getBoundingBox().move(p.subtract(caster.position())))) {
                    teleport(level, caster, p, caster.getYRot());
                    return;
                }
            }
        }

        private static void teleport(ServerLevel level, LivingEntity caster, Vec3 p, float yaw) {
            LsFx.shadowPuff(level, caster.position(), caster.getBbHeight());
            LsFx.sound(level, caster.position(), SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.6F);
            caster.teleportTo(level, p.x, p.y, p.z, Set.of(), yaw, caster.getXRot());
            caster.resetFallDistance();
            LsFx.shadowPuff(level, p, caster.getBbHeight());
            LsFx.sound(level, p, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.5F);
        }
    }

    /** Correntes (T3): correntes sombrias prendem o alvo no lugar (Enraizado + âncora). */
    public static class Chains extends EffectGlyph {
        public Chains() {
            super("chains", 16);
            element(Element.SHADOW);
            tier(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            int ticks = LsUtil.isBoss(le) ? 40 : 100;
            ShackledEffect.setAnchor(le, le.position());
            ctx.status(le, LsRegistries.SHADOW_CHAINS, ticks, 0, stats);
            ctx.status(le, CoreRegistries.ROOTED, ticks, 0, stats);
            ctx.damage(le, 2.0F, stats, null);
            LsFx.sound(ctx.level(), le.position(), SoundEvents.CHAIN_PLACE, 1.2F, 0.6F);
            LsFx.sound(ctx.level(), le.position(), SoundEvents.CHAIN_HIT, 1.0F, 0.5F);
        }
    }

    /** Maldição (T3): Corrompido (dano contínuo de sombra) + Azar. */
    public static class Curse extends EffectGlyph {
        public Curse() {
            super("curse", 15);
            element(Element.SHADOW);
            tier(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            float p = stats.power();
            int amp = p >= 1.8F ? 2 : p >= 1.35F ? 1 : 0;
            ctx.status(le, CoreRegistries.CORRUPTED, 200, amp, stats);
            ctx.status(le, MobEffects.UNLUCK, 400, 0, stats);
            ctx.level().sendParticles(Vfx.witch(), le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
            LsFx.spiral(ctx.level(), le, LsFx.dust(0x5A1E7A, 1.1F), 20, le.getBbWidth() * 0.6 + 0.3);
            LsFx.sound(ctx.level(), le.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 0.35F, 1.3F);
        }
    }

    /** Invisibilidade (T3): fica invisível e os mobs que o perseguiam perdem a pista. Invertido em inimigos: revela. */
    public static class Invisibility extends EffectGlyph {
        public Invisibility() {
            super("invisibility", 16);
            element(Element.SHADOW);
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
            LsFx.shadowPuff(level, le.position(), le.getBbHeight());
            if (ctx.isAlly(target)) {
                ctx.status(le, MobEffects.INVISIBILITY, 600, 0, stats);
                for (Mob m : level.getEntitiesOfClass(Mob.class, le.getBoundingBox().inflate(24), m -> m.getTarget() == le)) {
                    LsAi.clearTarget(m);
                }
                LsFx.sound(level, le.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 0.8F);
            } else {
                le.removeEffect(MobEffects.INVISIBILITY);
                ctx.status(le, MobEffects.GLOWING, 200, 0, stats);
            }
        }
    }

    /** Sombra Viva (T3): invoca um clone sombrio que luta ao seu lado. */
    public static class SummonLivingShadow extends EffectGlyph {
        public SummonLivingShadow() {
            super("living_shadow", 20);
            element(Element.SHADOW);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (target == caster || caster == null) {
                if (caster != null) spawn(ctx, caster.position().add(caster.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5)), null, stats);
                return;
            }
            // Surge da sombra do alvo e já parte para cima dele
            spawn(ctx, target.position().add(Vec3.directionFromRotation(0, target.getYRot()).scale(-1.2)),
                    target instanceof LivingEntity le ? le : null, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            spawn(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), null, stats);
        }

        private static void spawn(SpellContext ctx, Vec3 at, LivingEntity foe, SpellStats stats) {
            LivingEntity owner = ctx.caster();
            if (owner == null || !LsUtil.oncePerTick(ctx, "living_shadow")) return;
            ServerLevel level = ctx.level();
            EntityType<LivingShadowEntity> type = LsRegistries.LIVING_SHADOW.get();
            for (int i = 0; i < stats.count(); i++) {
                SummonUtil.limit(level, LivingShadowEntity.class, owner.getUUID(), owner.position(), 4);
                LivingShadowEntity shadow = type.create(level);
                if (shadow == null) return;
                Vec3 p = SummonUtil.freeSpot(level, type, at);
                shadow.moveTo(p.x, p.y, p.z, owner.getYRot(), 0);
                shadow.setYHeadRot(owner.getYRot());
                shadow.setup(owner, stats.duration(600), stats.power() * ctx.powerMult() * ctx.casterSpellPower());
                level.addFreshEntity(shadow);
                if (foe != null && !ctx.isAlly(foe)) shadow.setTarget(foe);
                LsFx.shadowPuff(level, p, 1.8);
                level.sendParticles(Vfx.sculkSoul(), p.x, p.y + 0.2, p.z, 10, 0.4, 0.1, 0.4, 0.04);
            }
            LsFx.sound(level, at, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0F, 0.6F);
            LsFx.sound(level, at, SoundEvents.SCULK_CATALYST_BLOOM, 1.0F, 0.6F);
        }
    }

    /** Devorar (T4): arranca 15% da vida máxima do alvo (+4) e o conjurador se cura com metade. */
    public static class Devour extends EffectGlyph {
        public Devour() {
            super("devour", 26);
            element(Element.SHADOW);
            tier(4);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            // A parte percentual tem teto, para não apagar chefes de uma vez
            float base = 4.0F + Math.min(30.0F, le.getMaxHealth() * 0.15F);
            float before = le.getHealth();
            ctx.damage(le, base, stats, null);
            float dealt = Math.max(0, before - le.getHealth());
            LivingEntity caster = ctx.caster();
            if (caster != null && caster.isAlive() && dealt > 0) {
                caster.heal(dealt * 0.5F);
                drainFx(level, le, caster);
            }
            // Bocarra de sombra se fechando sobre o alvo
            Vec3 c = LsFx.mid(le);
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16;
                double r = le.getBbWidth() + 0.8;
                level.sendParticles(Vfx.ink(), c.x + Math.cos(a) * r, c.y + Math.sin(a * 2) * 0.3, c.z + Math.sin(a) * r,
                        0, -Math.cos(a), 0, -Math.sin(a), 0.25);
            }
            level.sendParticles(Vfx.sculkSoul(), c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.05);
            LsFx.sound(level, le.position(), SoundEvents.WARDEN_ATTACK_IMPACT, 1.0F, 0.7F);
            LsFx.sound(level, le.position(), SoundEvents.GENERIC_EAT, 1.0F, 0.5F);
        }
    }

    /** Vínculo de Dor (T4): por 10s, parte do dano que o alvo recebe se espalha para os inimigos perto dele. */
    public static class PainLink extends EffectGlyph {
        public PainLink() {
            super("pain_link", 24);
            element(Element.SHADOW);
            tier(4);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            var d = LsUtil.data(le);
            if (ctx.casterId() != null) d.putUUID("link_owner", ctx.casterId());
            int amp = Math.max(0, Math.round((stats.power() - 1.0F) / 0.4F));
            ctx.status(le, LsRegistries.PAIN_LINK, 200, Math.min(3, amp), stats);
            LsFx.spiral(ctx.level(), le, LsFx.dust(LsFx.BLOOD, 1.2F), 24, le.getBbWidth() * 0.6 + 0.35);
            LsFx.sound(ctx.level(), le.position(), SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.8F);
        }
    }

    /** Roubar Bênção (T4): os efeitos positivos do alvo passam para o conjurador (ou rouba a Absorção). */
    public static class StealBlessing extends EffectGlyph {
        public StealBlessing() {
            super("steal_blessing", 22);
            element(Element.SHADOW);
            tier(4);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity victim)) return;
            ServerLevel level = ctx.level();
            LivingEntity caster = ctx.caster();
            LivingEntity le = victim;
            if (caster != null && victim == caster) {
                // Em si mesmo: rouba do inimigo mais perto (nunca dos próprios efeitos)
                le = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity e : ctx.enemiesAround(caster.position(), stats.radius(8F))) {
                    double d = e.distanceToSqr(caster);
                    if (d < best && caster.hasLineOfSight(e)) {
                        best = d;
                        le = e;
                    }
                }
                if (le == null) {
                    LsFx.sound(level, caster.position(), SoundEvents.VEX_AMBIENT, 0.8F, 0.6F);
                    return;
                }
            }
            List<MobEffectInstance> stolen = new ArrayList<>();
            for (MobEffectInstance inst : new ArrayList<>(le.getActiveEffects())) {
                if (inst.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL && le.removeEffect(inst.getEffect())) {
                    stolen.add(inst);
                }
            }
            if (caster != null && caster != le) {
                for (MobEffectInstance inst : stolen) {
                    int dur = inst.isInfiniteDuration() ? 1200 : Math.min(1200, inst.getDuration());
                    caster.addEffect(new MobEffectInstance(inst.getEffect(), dur, inst.getAmplifier()), caster);
                }
                if (stolen.isEmpty() && le.getAbsorptionAmount() > 0) {
                    // Absorção sem efeito (escudos): o conjurador ganha Absorção do mesmo tamanho. Só setAbsorptionAmount
                    // não serviria — o valor é limitado pelo atributo MAX_ABSORPTION, que vem do efeito.
                    float abs = Math.min(20F, le.getAbsorptionAmount());
                    le.setAbsorptionAmount(0);
                    caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, Math.max(0, Mth.ceil(abs / 4F) - 1)), caster);
                    stolen.add(null);
                }
                if (!stolen.isEmpty()) LsFx.line(level, LsFx.mid(le), LsFx.mid(caster), LsFx.fade(LsFx.GOLD, LsFx.VIOLET, 1.0F), 5);
            }
            ctx.damage(le, 2.0F, stats, null);
            level.sendParticles(Vfx.witch(), le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 10 + 6 * stolen.size(), 0.3, 0.4, 0.3, 0.05);
            LsFx.sound(level, le.position(), stolen.isEmpty() ? SoundEvents.VEX_AMBIENT : SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 0.7F);
        }
    }

    /** Pesadelo (T4): Náusea, Lentidão, Medo e o efeito Pesadelo (sussurros e dano psíquico). */
    public static class Nightmare extends EffectGlyph {
        public Nightmare() {
            super("nightmare", 25);
            element(Element.SHADOW);
            tier(4);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            int amp = stats.power() >= 1.6F ? 1 : 0;
            ctx.status(le, LsRegistries.NIGHTMARE_CURSE, 200, amp, stats);
            ctx.status(le, MobEffects.CONFUSION, 200, 0, stats);
            ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, 160, 1, stats);
            // O pavor dura o pesadelo inteiro
            Fear.frighten(ctx, le, ctx.caster() != null ? ctx.caster().position() : le.position(), stats, 200);
            level.sendParticles(Vfx.sculkSoul(), le.getX(), le.getEyeY() + 0.3, le.getZ(), 12, 0.4, 0.3, 0.4, 0.05);
            level.sendParticles(Vfx.ink(), le.getX(), le.getEyeY(), le.getZ(), 16, 0.4, 0.4, 0.4, 0.03);
            if (le instanceof ServerPlayer sp) sp.playNotifySound(SoundEvents.WARDEN_NEARBY_CLOSEST, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.6F);
            LsFx.sound(level, le.position(), SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.6F);
        }
    }

    /**
     * Possessão (T5, lendário): controla um mob fraco (até 40 de vida, não chefe) por 30s — ele luta ao seu lado,
     * ataca os seus inimigos e segue você. Mobs fortes resistem.
     */
    public static class Possession extends EffectGlyph {
        public Possession() {
            super("possession", 70);
            element(Element.SHADOW);
            legendary();
            minCircle(8);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            ServerLevel level = ctx.level();
            if (caster == null || !(target instanceof Mob mob) || target instanceof Player) return;
            ServerPlayer sp = ctx.casterPlayer();
            if (mob.getMaxHealth() > 40.0F || LsUtil.isBoss(mob)) {
                LsFx.shadowPuff(level, mob.position(), mob.getBbHeight());
                LsFx.sound(level, mob.position(), SoundEvents.SHIELD_BLOCK, 1.0F, 0.6F);
                if (sp != null) {
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.possession_resisted", mob.getDisplayName())
                            .withStyle(ChatFormatting.DARK_PURPLE), true);
                }
                return;
            }
            int ticks = stats.duration(600);
            LsUtil.data(mob).putUUID("possessor", caster.getUUID());
            if (!mob.addEffect(new MobEffectInstance(LsRegistries.SHADOW_POSSESSION, ticks, 0, false, true, true), caster)) {
                // Imune a efeitos: desfaz a marca
                LsUtil.data(mob).remove("possessor");
                return;
            }
            LsAi.clearTarget(mob);
            LsAi.ensurePossessed(mob);
            LsFx.line(level, LsFx.mid(caster), LsFx.mid(mob), LsFx.fade(LsFx.VIOLET, LsFx.DEEP_VIOLET, 1.2F), 5);
            LsFx.spiral(level, mob, LsFx.dust(LsFx.VIOLET, 1.3F), 30, mob.getBbWidth() * 0.6 + 0.4);
            // v1.3: a sombra do alvo vira um portal sob os pés e uma aura violeta marca o novo servo
            com.renanpontes.isekaimahou.core.ElementFx.portal(level, mob.position().add(0, 0.06, 0), null, Element.SHADOW, LsFx.VIOLET, Math.max(1.2F, mob.getBbWidth() * 1.2F), 4, 30);
            com.renanpontes.isekaimahou.core.ElementFx.aura(level, mob, Element.SHADOW, LsFx.VIOLET, 1.0F, 3, 100);
            level.sendParticles(Vfx.soul(), mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
            LsFx.sound(level, mob.position(), SoundEvents.EVOKER_PREPARE_WOLOLO, 1.0F, 0.6F);
            LsFx.sound(level, mob.position(), SoundEvents.WITHER_AMBIENT, 0.4F, 1.5F);
            if (sp != null) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.possession_start", mob.getDisplayName(), ticks / 20)
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }

    public static List<EffectGlyph> all() {
        return List.of(new LifeDrain(), new Weaken(), new Fear(), new Darkness(), new ShadowStep(), new Chains(), new Curse(),
                new Invisibility(), new SummonLivingShadow(), new Devour(), new PainLink(), new StealBlessing(), new Nightmare(),
                new Possession());
    }

    private ShadowEffects() {}
}
