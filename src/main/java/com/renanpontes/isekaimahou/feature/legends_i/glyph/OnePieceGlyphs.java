package com.renanpontes.isekaimahou.feature.legends_i.glyph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_i.LiGlyphs;
import com.renanpontes.isekaimahou.feature.legends_i.LiNet;
import com.renanpontes.isekaimahou.feature.legends_i.LiRegistry;
import com.renanpontes.isekaimahou.feature.legends_i.LiUtil;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Glifos de One Piece (efeitos comuns do Grimório):
 * <ul>
 *   <li>{@link Observation} Haki da Observação (Kenbunshoku) — sentir presenças através das paredes + desvio do "futuro".</li>
 *   <li>{@link Conqueror} Haki do Rei (Haoshoku) — a vontade derruba os fracos; relâmpagos negros e vermelhos.</li>
 *   <li>{@link Gomu} Gomu Gomu no Pistol — soco de borracha que estica até 10 blocos; com Dividir vira Gatling.</li>
 * </ul>
 * O Haki do Armamento é um AUMENTO (ver {@link LiGlyphs#HAKI_ARMAMENT}).
 */
public final class OnePieceGlyphs {
    public static final int HAKI_RED = 0xD01830;
    public static final int RUBBER = 0xE9B48C;

    public static List<EffectGlyph> all() {
        return List.of(new Observation(), new Conqueror(), new Gomu());
    }

    // =====================================================================================
    //  Haki da Observação
    // =====================================================================================
    /**
     * Em você ou num aliado: 8 s (Estender +) sentindo tudo num raio de 20 blocos (Expandir +; máx. 40) — contornos
     * vermelhos (ameaças) e brancos (o resto) vistos ATRAVÉS das paredes, só por quem usou, atualizados a cada 2 s. E
     * "vê o futuro": 25% de chance (+8% por nível de poder, máx. 50%; contra chefes metade) de desviar de um golpe ou
     * projétil; depois de desviar, 1,5 s sem poder desviar de novo. Não dá dano.
     */
    public static class Observation extends EffectGlyph {
        public static final int TICKS = 160;
        public static final float RADIUS = 20F;
        /** jogador → raio sentido (o LiEvents renova a varredura enquanto o efeito durar). */
        public static final Map<UUID, Float> SENSE_RADIUS = new HashMap<>();

        public Observation() {
            super("haki_observation", 14);
            LiGlyphs.config(this, Element.ARCANE, 2, 1);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return TICKS; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
            ServerLevel l = ctx.level();
            int ticks = Math.min(600, stats.duration(TICKS));
            int amp = Mth.clamp(Math.round((LiUtil.power(ctx, stats) - 1F) * 2F), 0, 3);
            le.addEffect(new MobEffectInstance(LiRegistry.OBSERVATION, ticks, amp, false, true, true), ctx.caster());
            float r = Math.min(40F, stats.radius(RADIUS));
            if (le instanceof ServerPlayer sp) {
                SENSE_RADIUS.put(sp.getUUID(), r);
                sense(sp, r, 50);
            }
            ElementFx.shockwave(l, le.position().add(0, 0.1, 0), HAKI_RED, Math.min(8F, r * 0.4F), 16, null);
            ElementFx.sendFar(l, ElementFx.glow(HAKI_RED, 1.2F), le.getX(), le.getEyeY(), le.getZ(), 10, 0.3, 0.2, 0.3, 0.02);
            LiUtil.sound(l, le.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 0.6F);
            LiUtil.sound(l, le.position(), SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.3F);
        }

        /** Varre o raio e manda os contornos SÓ para {@code sp} (no máx. 48 presenças, as mais perto primeiro). */
        public static int sense(ServerPlayer sp, float r, int ticks) {
            Vec3 c = sp.position();
            List<LivingEntity> list = new ArrayList<>(sp.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                    e -> e != sp && e.isAlive() && !e.isSpectator() && e.distanceToSqr(c) <= r * r));
            list.sort((a, b) -> Double.compare(a.distanceToSqr(c), b.distanceToSqr(c)));
            int n = Math.min(48, list.size());
            int[] ids = new int[n];
            boolean[] hostile = new boolean[n];
            for (int i = 0; i < n; i++) {
                ids[i] = list.get(i).getId();
                hostile[i] = LiUtil.hostile(sp, list.get(i));
            }
            PacketDistributor.sendToPlayer(sp, new LiNet.Sense(ids, hostile, ticks));
            return n;
        }

        /** Chance de desvio para um nível do efeito (contra chefes, metade). */
        public static float dodgeChance(int amplifier, boolean boss) {
            float c = Math.min(0.5F, 0.25F + 0.08F * amplifier);
            return boss ? c * 0.5F : c;
        }
    }

    // =====================================================================================
    //  Haki do Rei
    // =====================================================================================
    /**
     * A vontade de um rei. No alvo (ou, em você, numa onda de raio 8 — Expandir +; máx. 24): criaturas FRACAS (vida até
     * 60% da sua vida máxima × poder) DESMAIAM — Atordoadas 5 s e esquecem quem caçavam; MÉDIAS (até 2×) fogem
     * apavoradas por 4 s com Fraqueza; as fortes só ficam Lentas. Chefes NUNCA caem (só ficam Expostos); jogadores
     * inimigos sentem a pressão: Lentidão II e Náusea por 3–4 s. Nunca mata nem causa dano. Relâmpagos negros e vermelhos.
     */
    public static class Conqueror extends EffectGlyph {
        public static final float RADIUS = 8F;
        /** mob → até quando está apavorado (não pode mirar em quem o aterrorizou). */
        public static final Map<UUID, Long> FEARED = new HashMap<>();

        public Conqueror() {
            super("haki_conqueror", 30);
            LiGlyphs.config(this, Element.ARCANE, 4, 4);
        }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public int hintDuration() { return 100; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !(target instanceof LivingEntity le)) return;
            ServerLevel l = ctx.level();
            float power = LiUtil.power(ctx, stats);
            if (LiUtil.self(ctx, target)) {
                if (!LiUtil.once(ctx, "conqueror")) return;
                float r = Mth.clamp(stats.radius(RADIUS), 4F, 24F);
                LiUtil.fx(l, LiNet.CONQUEROR, c.position().add(0, 1.0, 0), Vec3.ZERO, HAKI_RED, 26, r);
                ElementFx.shockwave(l, c.position().add(0, 0.15, 0), 0x2A0008, r, 18, null);
                ElementFx.shockwave(l, c.position().add(0, 1.0, 0), HAKI_RED, r * 0.8F, 14, null);
                ElementFx.shake(l, c.position(), r * 3, 1.2F, 16);
                LiUtil.sound(l, c.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.7F, 0.5F);
                LiUtil.sound(l, c.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 0.6F);
                int knocked = 0;
                for (LivingEntity e : LiUtil.foesAround(ctx, c.position(), r, 40)) {
                    if (dominate(ctx, c, e, power)) knocked++;
                }
                if (knocked > 0) {
                    LiUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_i.conqueror", knocked).withStyle(ChatFormatting.DARK_RED));
                }
                return;
            }
            LiUtil.fx(l, LiNet.CONQUEROR, LiUtil.center(le), Vec3.ZERO, HAKI_RED, 16, Math.max(2F, le.getBbWidth() * 2.5F));
            LiUtil.sound(l, le.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 0.5F);
            dominate(ctx, c, le, power);
        }

        /** Aplica a pressão do Haki em um alvo. Devolve true se ele desmaiou. */
        static boolean dominate(SpellContext ctx, LivingEntity c, LivingEntity e, float power) {
            ServerLevel l = ctx.level();
            if (LiUtil.boss(e)) {
                Statuses.apply(e, Statuses.Kind.EXPOSED, 1, 60, c, power);
                l.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 8, 0.4, 0.2, 0.4, 0.02);
                return false;
            }
            if (e instanceof Player p) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true), c);
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false), c);
                return false;
            }
            float base = c instanceof Player ? c.getMaxHealth() : 20F;
            float weak = base * 0.6F * Math.max(0.5F, power);
            if (e.getHealth() <= weak) {
                Statuses.apply(e, Statuses.Kind.STUNNED, 1, 100, c, power);
                if (e instanceof Mob m) m.setTarget(null);
                l.sendParticles(ParticleTypes.SQUID_INK, e.getX(), e.getY() + e.getBbHeight() * 0.8, e.getZ(), 10, 0.3, 0.2, 0.3, 0.02);
                ElementFx.sendFar(l, ElementFx.glow(HAKI_RED, 1.0F), e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 6, 0.3, 0.05, 0.3, 0.01);
                return true;
            }
            if (e.getHealth() <= weak * 2F) {
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, true), c);
                if (e instanceof Mob m) m.setTarget(null);
                FEARED.put(e.getUUID(), l.getGameTime() + 80);
                if (e instanceof PathfinderMob pm) {
                    Vec3 away = DefaultRandomPos.getPosAway(pm, 16, 7, c.position());
                    if (away != null) pm.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
                }
                l.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
                return false;
            }
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0, false, true), c);
            return false;
        }
    }

    // =====================================================================================
    //  Gomu Gomu no Pistol / Gatling
    // =====================================================================================
    /**
     * O braço estica feito borracha: soco de 7 de dano (×1,25 de mão vazia) que empurra. Em você (Si Mesmo): acerta o
     * primeiro inimigo à frente até 10 blocos (Expandir +; máx. 20). Em outras formas: o braço voa até cada alvo
     * atingido (até 20 blocos de você). Com Dividir vira <b>Gatling</b>: 3 socos + 2 por Dividir (máx. 12) em rajada,
     * 45% do dano cada.
     */
    public static class Gomu extends EffectGlyph {
        public static final float DAMAGE = 7F;
        public static final float REACH = 10F;

        public Gomu() {
            super("gomu_pistol", 10);
            LiGlyphs.config(this, Element.ARCANE, 2, 1);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return REACH; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel l = ctx.level();
            Vec3 shoulder = shoulder(c);
            if (LiUtil.self(ctx, target)) {
                if (!LiUtil.once(ctx, "gomu")) return;
                float reach = Mth.clamp(stats.radius(REACH), 4F, 20F);
                Vec3 dir = c.getLookAngle();
                Vec3 end = shoulder.add(dir.scale(reach));
                BlockHitResult bh = l.clip(new ClipContext(shoulder, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
                if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
                LivingEntity hit = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(shoulder, end).inflate(1.2), e -> LiUtil.isFoe(ctx, e))) {
                    if (e.getBoundingBox().inflate(0.6).clip(shoulder, end).isEmpty()) continue;
                    double d = e.distanceToSqr(shoulder);
                    if (d < best) {
                        best = d;
                        hit = e;
                    }
                }
                if (hit != null) end = LiUtil.center(hit);
                punch(ctx, stats, c, hit, shoulder, end);
                return;
            }
            if (!(target instanceof LivingEntity le) || le.distanceToSqr(c) > 20 * 20) return;
            punch(ctx, stats, c, le, shoulder, LiUtil.center(le));
        }

        static Vec3 shoulder(LivingEntity c) {
            float yaw = c.yBodyRot * Mth.DEG_TO_RAD;
            // ombro direito: um pouco à direita do corpo, na altura do peito
            return c.position().add(-Mth.cos(yaw) * 0.35, c.getBbHeight() * 0.75, -Mth.sin(yaw) * 0.35);
        }

        private static void punch(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity hit, Vec3 from, Vec3 to) {
            ServerLevel l = ctx.level();
            int split = Math.max(0, stats.count() - 1);
            boolean bare = c.getMainHandItem().isEmpty();
            float dmg = DAMAGE * (bare ? 1.25F : 1F);
            Vec3 dir = to.subtract(from);
            if (dir.lengthSqr() < 1.0E-4) dir = c.getLookAngle();
            Vec3 n = dir.normalize();
            if (split <= 0) {
                LiUtil.fx(l, LiNet.GOMU, from, to.subtract(from), RUBBER, 9, 0.38F);
                LiUtil.sound(l, from, SoundEvents.SLIME_JUMP, 1.0F, 1.4F);
                if (hit != null) {
                    ctx.damage(hit, dmg, stats, c);
                    knock(hit, n, LiUtil.boss(hit) ? 0.4F : 1.4F);
                    ElementFx.shockwave(l, to, 0xFFFFFF, 1.6F, 8, n);
                    LiUtil.sound(l, to, SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.8F);
                }
                return;
            }
            // Gatling: rajada de socos esticados (um a cada 2 ticks), braços em volta do eixo
            int hits = Math.min(12, 3 + 2 * split);
            Vec3 sideRaw = n.cross(new Vec3(0, 1, 0));
            final Vec3 side = sideRaw.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : sideRaw.normalize();
            final Vec3 up = side.cross(n).normalize();
            Vec3 axis = to.subtract(from);
            for (int i = 0; i < hits; i++) {
                final int k = i;
                BossKit.schedule(l, i * 2, () -> {
                    if (!c.isAlive() || c.level() != l) return;
                    float a = (k * 2.39996F);
                    Vec3 off = side.scale(Mth.cos(a) * 0.45).add(up.scale(Mth.sin(a) * 0.35));
                    Vec3 start = shoulder(c).add(off);
                    Vec3 end = hit != null && hit.isAlive() ? LiUtil.center(hit).add(off.scale(0.6)) : start.add(axis);
                    LiUtil.fx(l, LiNet.GOMU, start, end.subtract(start), RUBBER, 6, 0.3F);
                    if (k % 2 == 0) LiUtil.sound(l, start, SoundEvents.SLIME_JUMP_SMALL, 0.8F, 1.6F);
                    if (hit != null && hit.isAlive()) {
                        hit.invulnerableTime = 0;
                        ctx.damage(hit, dmg * 0.45F, stats, c);
                        knock(hit, n, LiUtil.boss(hit) ? 0.05F : 0.25F);
                        if (k % 3 == 0) LiUtil.sound(l, end, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 1.2F);
                    }
                });
            }
        }

        private static void knock(LivingEntity e, Vec3 dir, float strength) {
            e.push(dir.x * strength, 0.15 + 0.1 * strength, dir.z * strength);
            e.hurtMarked = true;
        }
    }

    private OnePieceGlyphs() {}
}
