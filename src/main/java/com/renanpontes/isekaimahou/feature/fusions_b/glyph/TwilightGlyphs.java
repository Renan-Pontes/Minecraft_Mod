package com.renanpontes.isekaimahou.feature.fusions_b.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbTasks;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.EclipseFieldEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.TwilightCloneEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos do CREPÚSCULO (Luz + Sombra).
 * Tier 1: Equilíbrio · Tier 2: Eclipse · Tier 3: Dualidade · Tier 5 (lendário): Julgamento Final.
 */
public final class TwilightGlyphs {

    public static void register() {
        GlyphRegistry.register(new Equilibrium());
        GlyphRegistry.register(new Eclipse());
        GlyphRegistry.register(new Duality());
        GlyphRegistry.register(new FinalJudgment());
    }

    /** Espiral dupla (luz dourada e sombra roxa) em volta da entidade. */
    static void yinYang(ServerLevel sl, LivingEntity e) {
        double r = e.getBbWidth() * 0.6 + 0.35;
        for (int i = 0; i < 24; i++) {
            double t = i / 24.0;
            double a = t * Math.PI * 4;
            double y = e.getY() + t * e.getBbHeight();
            sl.sendParticles(FbFx.dust(FbFx.TWILIGHT_GOLD, 1.0F), e.getX() + Math.cos(a) * r, y, e.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            sl.sendParticles(FbFx.dust(FbFx.TWILIGHT_SHADOW, 1.0F), e.getX() - Math.cos(a) * r, y, e.getZ() - Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 4, 0.2, 0.1, 0.2, 0.02);
        sl.sendParticles(ParticleTypes.WITCH, e.getX(), e.getY() + 0.2, e.getZ(), 4, 0.2, 0.1, 0.2, 0.02);
    }

    /**
     * Equilíbrio: iguala a porcentagem de vida e de mana do alvo (ex.: vida cheia e mana baixa → perde vida e
     * ganha mana; o contrário cura gastando mana). Com mais poder, o ponto de equilíbrio fica mais alto.
     * Nunca deixa abaixo de meio coração. Em criaturas sem mana: cura 3. Invertido em inimigos: rouba vida
     * deles e devolve como mana para você.
     */
    public static class Equilibrium extends EffectGlyph {
        public Equilibrium() {
            super("equilibrium", 5);
            element(Element.TWILIGHT);
            tier(1);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            LivingEntity caster = ctx.caster();
            if (caster != null && le != caster && !ctx.isAlly(le)) {
                FbUtil.hurt(ctx, le, 3.0F, stats, null);
                if (caster instanceof ServerPlayer sp) MagicApi.get(sp).addMana(10F * ResourceTraining.MANA_SCALE * FbUtil.power(ctx, stats)); // v1.0: mana ×3
                FbFx.line(sl, FbUtil.center(le), FbUtil.center(caster), FbFx.fade(FbFx.TWILIGHT_GOLD, FbFx.TWILIGHT_SHADOW, 1.0F), 3.0);
                yinYang(sl, le);
                FbFx.sound(sl, le.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 0.6F);
                return;
            }
            if (le instanceof ServerPlayer sp) {
                MagicData d = MagicApi.get(sp);
                float hp = sp.getHealth() / sp.getMaxHealth();
                float mana = d.maxMana() > 0 ? d.mana() / d.maxMana() : 1F;
                float bonus = Math.max(0F, (FbUtil.power(ctx, stats) - 1F) * 0.1F);
                float avg = Mth.clamp((hp + mana) / 2F + bonus, 0F, 1F);
                sp.setHealth(Math.max(1.0F, avg * sp.getMaxHealth()));
                d.setMana(avg * d.maxMana());
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.equilibrium", Math.round(avg * 100)), true);
            } else {
                ctx.heal(le, 3.0F, stats);
            }
            yinYang(sl, le);
            FbFx.sound(sl, le.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F);
            FbFx.sound(sl, le.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 0.8F);
        }
    }

    /**
     * Eclipse: por 10s um disco escuro cobre o céu acima da área (raio 6). Inimigos dentro ficam no escuro,
     * Fracos, Lentos e Expostos (recebem mais dano mágico). Um eclipse por conjurador.
     */
    public static class Eclipse extends EffectGlyph {
        private static final String KEY = "eclipse";

        public Eclipse() {
            super("eclipse", 20);
            element(Element.TWILIGHT);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FbUtil.once(ctx, KEY)) return;
            cast(ctx, FbUtil.ground(ctx.level(), target.position().add(0, 0.5, 0), 6), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (FbUtil.isSelfFeetEcho(ctx, hit, KEY) || !FbUtil.once(ctx, KEY)) return;
            cast(ctx, FbUtil.ground(ctx.level(), hit.getLocation().add(0, 0.5, 0), 6), stats);
        }

        private static void cast(SpellContext ctx, Vec3 at, SpellStats stats) {
            EclipseFieldEntity.cast(ctx, at, stats.radius(6F), stats.duration(200), FbUtil.amp(ctx, stats));
        }
    }

    /**
     * Dualidade: você se divide em dois clones por 20s. O clone de LUZ segue você e cura o aliado mais ferido;
     * o clone de SOMBRA caça inimigos (dano 4, enfraquece). Se a magia acertou um inimigo, a sombra vai nele.
     */
    public static class Duality extends EffectGlyph {
        public Duality() {
            super("duality", 30);
            element(Element.TWILIGHT);
            tier(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !FbUtil.once(ctx, "duality")) return;
            ServerLevel sl = ctx.level();
            int life = stats.duration(400);
            float power = FbUtil.power(ctx, stats);
            LivingEntity enemy = !FbUtil.isSelf(ctx, target) && target instanceof LivingEntity le && !ctx.isAlly(le) ? le : null;
            Vec3 look = FbUtil.flatLook(ctx);
            Vec3 right = new Vec3(-look.z, 0, look.x);
            TwilightCloneEntity.create(sl, c, true, c.position().add(right.scale(1.6)), life, power, null);
            TwilightCloneEntity.create(sl, c, false, c.position().subtract(right.scale(1.6)), life, power, enemy);
            Vec3 cc = FbUtil.center(c);
            FbFx.line(sl, cc.add(right.scale(1.6)), cc.subtract(right.scale(1.6)), FbFx.fade(FbFx.TWILIGHT_GOLD, FbFx.TWILIGHT_SHADOW, 1.2F), 4.0);
            FbFx.sound(sl, cc, SoundEvents.ILLUSIONER_CAST_SPELL, 1.2F, 1.0F);
            FbFx.sound(sl, cc, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.0F);
        }
    }

    /**
     * Julgamento Final (LENDÁRIO): dois anéis — de luz e de sombra — giram no chão (raio 8) enquanto raios caem e
     * sombras sobem por 2s, prendendo os inimigos. Então o céu desaba: relâmpagos em cada inimigo e dano 18 em
     * área (luz pesa contra mortos-vivos; a sombra deixa Murchar). Não quebra blocos nem acende fogo.
     */
    public static class FinalJudgment extends EffectGlyph {
        private static final String KEY = "final_judgment";

        public FinalJudgment() {
            super("final_judgment", 70);
            element(Element.TWILIGHT);
            legendary();
            minCircle(8);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FbUtil.once(ctx, KEY)) return;
            start(ctx, FbUtil.ground(ctx.level(), target.position().add(0, 0.5, 0), 6), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (FbUtil.isSelfFeetEcho(ctx, hit, KEY) || !FbUtil.once(ctx, KEY)) return;
            start(ctx, FbUtil.ground(ctx.level(), hit.getLocation().add(0, 0.5, 0), 6), stats);
        }

        private static void start(SpellContext ctx, Vec3 at, SpellStats stats) {
            FbTasks.add(new JudgmentTask(ctx, stats, at, stats.radius(8F)));
        }

        static final class JudgmentTask implements FbTasks.Task {
            private static final int CHARGE = 40;
            private static final int END = 60;
            private final SpellContext ctx;
            private final SpellStats stats;
            private final ServerLevel level;
            private final Vec3 at;
            private final float radius;
            private int age;

            JudgmentTask(SpellContext ctx, SpellStats stats, Vec3 at, float radius) {
                this.ctx = ctx;
                this.stats = stats;
                this.level = ctx.level();
                this.at = at;
                this.radius = radius;
            }

            @Override
            public boolean tick() {
                age++;
                if (age == 1) {
                    FbFx.sound(level, at, SoundEvents.BEACON_ACTIVATE, 2.0F, 0.5F);
                    FbFx.sound(level, at, SoundEvents.END_PORTAL_SPAWN, 1.0F, 1.4F);
                }
                if (age <= CHARGE) charge();
                if (age == CHARGE) strike();
                if (age > CHARGE) aftermath();
                return age < END;
            }

            private void charge() {
                double spin = age * 0.12;
                if (age % 2 == 0) {
                    // Anel de luz girando para um lado, de sombra para o outro
                    for (int i = 0; i < 16; i++) {
                        double a = spin + i * Math.PI * 2 / 16;
                        level.sendParticles(FbFx.dust(FbFx.TWILIGHT_GOLD, 1.6F), at.x + Math.cos(a) * radius, at.y + 0.15,
                                at.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
                        double b = -spin + i * Math.PI * 2 / 16;
                        level.sendParticles(FbFx.dust(FbFx.TWILIGHT_SHADOW, 1.6F), at.x + Math.cos(b) * radius * 0.65, at.y + 0.15,
                                at.z + Math.sin(b) * radius * 0.65, 1, 0, 0, 0, 0);
                    }
                }
                if (age % 4 == 0) {
                    // Raio de luz descendo e raio de sombra subindo em pontos aleatórios
                    Vec3 l = randomInside();
                    for (int i = 0; i < 14; i++) {
                        level.sendParticles(ParticleTypes.END_ROD, l.x, l.y + 12 - i * 0.85, l.z, 1, 0.03, 0.1, 0.03, 0);
                    }
                    level.sendParticles(ParticleTypes.FLASH, l.x, l.y + 0.3, l.z, 1, 0, 0, 0, 0);
                    Vec3 s = randomInside();
                    for (int i = 0; i < 14; i++) {
                        level.sendParticles(FbFx.dust(FbFx.VOID_BLACK, 1.8F), s.x, s.y + i * 0.6, s.z, 1, 0.05, 0.1, 0.05, 0);
                    }
                    level.sendParticles(ParticleTypes.SQUID_INK, s.x, s.y + 0.3, s.z, 6, 0.2, 0.1, 0.2, 0.05);
                }
                if (age % 10 == 0) {
                    for (LivingEntity e : enemies()) {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false, true), ctx.caster());
                        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, false), ctx.caster());
                    }
                }
                if (age == 20) {
                    FbFx.sound(level, at, SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.6F);
                    FbFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.5F);
                }
            }

            private void strike() {
                List<LivingEntity> targets = enemies();
                int n = 0;
                for (LivingEntity e : targets) {
                    n++;
                    if (n <= 5) bolt(e.position());
                    else FbFx.column(level, e.position(), ParticleTypes.END_ROD, 10, 20);
                    FbUtil.hurt(ctx, e, 18.0F, stats, null);
                    if (!e.isAlive()) continue;
                    // Metade "julgada pela sombra": murcha
                    if (n % 2 == 0) e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1), ctx.caster());
                    FbUtil.addMotion(e, new Vec3(0, 0.6, 0));
                }
                for (int i = 0; i < 2; i++) bolt(randomInside());
                level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 3, 1, 0.5, 1, 0);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
                FbFx.shockwave(level, at.add(0, 0.3, 0), ParticleTypes.END_ROD, 48, 0.6);
                FbFx.shockwave(level, at.add(0, 0.4, 0), FbFx.dust(FbFx.TWILIGHT_SHADOW, 2.0F), 48, 0.8);
                FbFx.sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 0.8F);
                FbFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.7F);
                FbFx.sound(level, at, SoundEvents.TOTEM_USE, 1.2F, 0.6F);
            }

            private void aftermath() {
                if (age % 3 != 0) return;
                for (int i = 0; i < 6; i++) {
                    Vec3 p = randomInside();
                    level.sendParticles(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.WITCH, p.x, p.y + 0.5, p.z, 1, 0.1, 0.3, 0.1, 0.03);
                }
            }

            /** Relâmpago só visual (não acende fogo nem fere: o dano é o da magia). */
            private void bolt(Vec3 p) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt == null) return;
                bolt.moveTo(Vec3.atBottomCenterOf(BlockPos.containing(p)));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }

            private Vec3 randomInside() {
                double a = level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(level.random.nextDouble()) * radius;
                return FbUtil.ground(level, at.add(Math.cos(a) * d, 2, Math.sin(a) * d), 8);
            }

            private List<LivingEntity> enemies() {
                return ctx.enemiesAround(at.add(0, 1, 0), radius + 0.5);
            }
        }
    }

    private TwilightGlyphs() {}
}
