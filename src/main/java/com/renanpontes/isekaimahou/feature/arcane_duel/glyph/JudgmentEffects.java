package com.renanpontes.isekaimahou.feature.arcane_duel.glyph;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.arcane_duel.DuelFx;
import com.renanpontes.isekaimahou.feature.arcane_duel.DuelRegistry;
import com.renanpontes.isekaimahou.feature.arcane_duel.DuelUtil;
import com.renanpontes.isekaimahou.feature.arcane_duel.entity.DuelFxEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Remoções: Exilar e Ira Divina. */
public final class JudgmentEffects {
    /** Marca (persistent data) posta no instante do exílio: a morte não deixa loot nem XP. */
    public static final String EXILED = "isekai_mahou.arcane_duel.exiled";
    /** Quem está sendo puxado para a fenda agora (não pode ser exilado duas vezes ao mesmo tempo). */
    private static final Set<UUID> EXILING = new HashSet<>();
    private static final ResourceLocation EXILE_SCALE = IsekaiMahou.id("arcane_duel_exile_scale");

    /**
     * Quem é invulnerável a esta magia de verdade (funcionários da guilda, Espírito Guia, fases de chefe imunes...) não
     * pode ser destruído nem exilado: o julgamento não mata quem o jogo protege.
     */
    static boolean untouchable(LivingEntity e, DamageSource src) {
        return e.isInvulnerable() || e.isInvulnerableTo(src);
    }

    /**
     * Mata "de verdade" (crédito do abate para quem conjurou). Se algo impedir o dano (um ouvinte, armadura absurda),
     * morre mesmo assim — exceto com Indestrutível, que sobrevive à destruição (como no Magic), e quem é invulnerável.
     */
    static void obliterate(LivingEntity e, DamageSource src) {
        if (untouchable(e, src)) return;
        e.invulnerableTime = 0;
        e.hurt(src, e.getHealth() + e.getAbsorptionAmount() + 10000F);
        if (e.isAlive() && !e.isDeadOrDying() && !e.hasEffect(DuelRegistry.INDESTRUCTIBLE)) e.kill();
    }

    /** Tira {@code fraction} da vida MÁXIMA (chefes), com crédito e aggro para quem conjurou. */
    static void maim(LivingEntity e, DamageSource src, float fraction) {
        if (untouchable(e, src)) return;
        float loss = e.getMaxHealth() * fraction;
        if (e.getHealth() - loss <= 0.5F) {
            obliterate(e, src);
            return;
        }
        e.setHealth(e.getHealth() - loss + 1F);
        e.invulnerableTime = 0;
        e.hurt(src, 1F);
    }

    // =====================================================================================
    //  Exilar (ARCANE t4, C5) — mob não-chefe com ≤50% de vida é banido; chefes: Exposto II
    // =====================================================================================
    public static class ExileEffect extends EffectGlyph {
        private static final int PULL_TICKS = 18;

        public ExileEffect() {
            super("exile", 30);
            element(Element.ARCANE);
            tier(4);
            minCircle(5);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || target == ctx.caster() || !le.isAlive()) return;
            ServerLevel level = ctx.level();
            Vec3 c = DuelFx.center(le);
            float rift = Math.max(1.1F, le.getBbHeight() * 0.75F);
            if (DuelUtil.isBoss(le) || le instanceof Player) {
                // Grande demais para caber na fenda: ela rasga as defesas
                le.forceAddEffect(new MobEffectInstance(CoreRegistries.EXPOSED, stats.duration(120), 1), ctx.caster());
                DuelFx.spawn(level, DuelFxEntity.RIFT, c, rift, 24, DuelFx.VOID, 0F);
                DuelFx.sound(level, c, SoundEvents.END_PORTAL_SPAWN, 0.5F, 1.6F);
                DuelUtil.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.arcane_duel.exile.boss", le.getDisplayName()),
                        ChatFormatting.LIGHT_PURPLE);
                return;
            }
            // Amplificar: aceita alvos mais saudáveis (50% + 10% por Amplificar, até 80%)
            float threshold = Math.min(0.8F, 0.5F + 0.1F * Math.max(0, stats.amplify));
            if (le.getHealth() > le.getMaxHealth() * threshold) {
                ctx.damage(le, 6F, stats, null);
                ctx.status(le, CoreRegistries.SILENCED, 40, 0, stats);
                DuelFx.spawn(level, DuelFxEntity.RIFT, c, rift * 0.7F, 14, DuelFx.VOID, 0F);
                DuelFx.sound(level, c, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.5F);
                DuelUtil.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.arcane_duel.exile.resists",
                        le.getDisplayName(), Math.round(threshold * 100)), ChatFormatting.GRAY);
                return;
            }
            if (untouchable(le, ctx.damageSource(null))) {
                // Protegido pelo jogo (funcionário da guilda, espírito guia...): a fenda não o alcança
                DuelFx.spawn(level, DuelFxEntity.RIFT, c, rift * 0.6F, 10, DuelFx.VOID, 0F);
                level.sendParticles(Vfx.smoke(), c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.01);
                DuelFx.sound(level, c, SoundEvents.SHIELD_BLOCK, 0.6F, 0.8F);
                return;
            }
            if (EXILING.add(le.getUUID())) exile(ctx, le, c, rift);
        }

        /** A fenda abre, segura o alvo, encolhe-o para dentro dela e fecha. */
        private static void exile(SpellContext ctx, LivingEntity le, Vec3 c, float rift) {
            ServerLevel level = ctx.level();
            le.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, PULL_TICKS + 10, 0), ctx.caster());
            if (le instanceof Mob mob) mob.getNavigation().stop();
            DuelFx.spawn(level, DuelFxEntity.RIFT, c, rift, PULL_TICKS + 16, DuelFx.VOID, 1F);
            DuelFx.sound(level, c, SoundEvents.END_PORTAL_SPAWN, 0.7F, 1.4F);
            DuelFx.sound(level, c, SoundEvents.PORTAL_TRIGGER, 0.6F, 1.8F);
            DamageSource src = ctx.damageSource(null);
            for (int t = 1; t <= PULL_TICKS; t++) {
                int k = t;
                SpellScheduler.schedule(t, () -> {
                    if (k == PULL_TICKS) EXILING.remove(le.getUUID());
                    if (!le.isAlive() || le.isRemoved()) return;
                    // Puxado para o centro da fenda e encolhendo (modificador temporário: não é salvo)
                    Vec3 pull = c.subtract(DuelFx.center(le)).scale(0.35);
                    le.setDeltaMovement(pull.x, pull.y + 0.04, pull.z);
                    le.hurtMarked = true;
                    AttributeInstance scale = le.getAttribute(Attributes.SCALE);
                    if (scale != null) {
                        scale.addOrUpdateTransientModifier(new AttributeModifier(EXILE_SCALE, -0.92 * k / PULL_TICKS,
                                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    }
                    Vec3 p = DuelFx.center(le);
                    level.sendParticles(Vfx.reversePortal(), p.x, p.y, p.z, 6, 0.4, 0.4, 0.4, 0.06);
                    if (k == PULL_TICKS) {
                        level.sendParticles(Vfx.portal(), c.x, c.y, c.z, 50, 0.3, 0.3, 0.3, 1.2);
                        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                        DuelFx.sound(level, c, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.4F);
                        DuelFx.sound(level, c, SoundEvents.ENDER_EYE_DEATH, 1.0F, 0.5F);
                        le.getPersistentData().putBoolean(EXILED, true);
                        obliterate(le, src);
                        if (scale != null) scale.removeModifier(EXILE_SCALE);
                        // Some sem deixar corpo (a morte já foi contada para quem exilou)
                        if (!le.isRemoved()) le.discard();
                    }
                });
            }
            DuelUtil.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.arcane_duel.exile.done", le.getDisplayName()),
                    ChatFormatting.LIGHT_PURPLE);
        }
    }

    // =====================================================================================
    //  Ira Divina (LIGHT lendário, C8) — destrói TODA criatura não-jogador e não-domada na área; chefes perdem 25%
    // =====================================================================================
    public static class WrathEffect extends EffectGlyph {
        private static final int CHARGE = 30;
        private static final int MAX_VICTIMS = 96;

        public WrathEffect() {
            super("wrath", 80);
            element(Element.LIGHT);
            legendary();
            minCircle(8);
        }

        /** Também no chão: um projétil/meteoro que cai no terreno chama o julgamento ali. */
        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            judgment(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), stats);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            judgment(ctx, target.position(), stats);
        }

        private static void judgment(SpellContext ctx, Vec3 center, SpellStats stats) {
            // Uma vez por conjuração: numa zona/Eco que reaplica o efeito, o julgamento não se repete a cada pulso
            if (!DuelUtil.firstWithin(ctx, "wrath", 200)) return;
            ServerLevel level = ctx.level();
            LivingEntity caster = ctx.caster();
            float radius = stats.radius(14F);
            float sky = Math.min(28F, 12F + radius * 0.6F);
            DuelFx.spawn(level, DuelFxEntity.SKY_SIGIL, center.add(0, sky, 0), radius, CHARGE + 40, DuelFx.HOLY, sky);
            DuelFx.sound(level, center, SoundEvents.BEACON_ACTIVATE, 2.0F, 0.6F);
            DuelFx.sound(level, center, SoundEvents.BELL_RESONATE, 2.0F, 0.5F);
            DuelFx.sound(level, center, SoundEvents.EVOKER_PREPARE_WOLOLO, 1.5F, 0.6F);
            BossKit.shake(level, center, radius + 24, 0.5F, CHARGE);
            DamageSource src = ctx.damageSource(null);
            SpellScheduler.schedule(CHARGE, () -> judge(ctx, level, caster, center, radius, src));
        }

        private static void judge(SpellContext ctx, ServerLevel level, LivingEntity caster, Vec3 center, float radius, DamageSource src) {
            double r2 = radius * radius;
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                    e -> e.isAlive() && e != caster && !(e instanceof Player) && !(e instanceof ArmorStand) && !DuelUtil.isTamed(e)
                            && !untouchable(e, src) && e.distanceToSqr(center) <= r2);
            victims.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
            if (victims.size() > MAX_VICTIMS) victims = victims.subList(0, MAX_VICTIMS);
            BossKit.shake(level, center, radius + 32, 1.6F, 24);
            level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
            DuelFx.sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 0.6F);
            DuelFx.sound(level, center, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
            int i = 0;
            for (LivingEntity v : victims) {
                int index = i++;
                // Em onda do centro para fora (até ~1 s)
                int delay = 1 + (int) (Math.sqrt(v.distanceToSqr(center)) / Math.max(1F, radius) * 18F);
                SpellScheduler.schedule(delay, () -> strike(ctx, level, caster, v, src, index));
            }
            if (victims.isEmpty()) {
                DuelUtil.actionbar(caster, Component.translatable("message.isekai_mahou.arcane_duel.wrath.empty"), ChatFormatting.GRAY);
            } else {
                DuelUtil.actionbar(caster, Component.translatable("message.isekai_mahou.arcane_duel.wrath.done", victims.size()),
                        ChatFormatting.GOLD);
            }
        }

        private static void strike(SpellContext ctx, ServerLevel level, LivingEntity caster, LivingEntity v, DamageSource src, int index) {
            if (!v.isAlive() || v.isRemoved() || v.level() != level) return;
            Vec3 feet = v.position();
            if (index < DuelFx.MAX_FX_PER_CAST) {
                DuelFx.spawn(level, DuelFxEntity.PILLAR, feet, Math.max(0.7F, v.getBbWidth() * 0.75F), 24, DuelFx.HOLY, 26F);
                // O chão onde o julgamento caiu vira vidro (respeita a regra de destruição)
                Destruction.surface(level, feet, 1.4F + Math.min(1.6F, v.getBbWidth()), caster, Destruction.Mode.VITRIFY);
            }
            Vec3 c = DuelFx.center(v);
            level.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 14, 0.3, v.getBbHeight() * 0.4, 0.3, 0.12);
            level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            if (index % 4 == 0) DuelFx.sound(level, feet, SoundEvents.TRIDENT_THUNDER.value(), 1.4F, 1.2F);
            if (DuelUtil.isBoss(v)) maim(v, src, 0.25F);
            else obliterate(v, src);
        }
    }

    public static void clear() {
        EXILING.clear();
    }

    private JudgmentEffects() {}
}
