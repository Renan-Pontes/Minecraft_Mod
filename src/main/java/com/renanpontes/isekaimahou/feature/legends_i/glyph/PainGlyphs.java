package com.renanpontes.isekaimahou.feature.legends_i.glyph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.legends_i.LiGlyphs;
import com.renanpontes.isekaimahou.feature.legends_i.LiNet;
import com.renanpontes.isekaimahou.feature.legends_i.LiUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos do Pain (Naruto — Rinnegan), elemento Gravidade:
 * <ul>
 *   <li>{@link Shinra} Shinra Tensei — repulsão; a versão GRANDE trava a técnica por 5 s.</li>
 *   <li>{@link Bansho} Bansho Ten'in — puxa o alvo até você (com Inverter, puxa VOCÊ até ele).</li>
 *   <li>{@link Chibaku} Chibaku Tensei — núcleo negro que ergue e prende os inimigos numa esfera flutuante.</li>
 * </ul>
 * Nenhum deles quebra blocos (os detritos do Chibaku são só visuais), então cidades/torres ficam intactas.
 */
public final class PainGlyphs {
    public static final int RINNEGAN = 0xB9A6E8;
    public static final int CORE = 0x1A1026;

    public static List<EffectGlyph> all() {
        return List.of(new Shinra(), new Bansho(), new Chibaku());
    }

    // =====================================================================================
    //  Shinra Tensei
    // =====================================================================================
    /**
     * Repulsão. No alvo: 4 de dano e um empurrão forte para longe de você (ou do ponto de origem do estágio). Em você:
     * uma esfera de raio 5 (Expandir +50% cada; máx. 20) empurra TODO inimigo e rebate projéteis inimigos. A versão
     * GRANDE (raio 9 ou mais) cansa: por 5 s nenhum Shinra Tensei grande sai de novo ("cinco segundos"). Chefes são
     * empurrados só um pouco.
     */
    public static class Shinra extends EffectGlyph {
        public static final float DAMAGE = 4F;
        public static final float RADIUS = 5F;
        public static final float BIG = 9F;
        public static final int LOCKOUT = 100;
        /** conjurador → até quando o Shinra Tensei grande está travado. */
        public static final Map<UUID, Long> LOCKED = new HashMap<>();

        public Shinra() {
            super("shinra_tensei", 22);
            LiGlyphs.config(this, Element.GRAVITY, 3, 3);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel l = ctx.level();
            float power = LiUtil.power(ctx, stats);
            if (LiUtil.self(ctx, target)) {
                if (!LiUtil.once(ctx, "shinra")) return;
                float r = Mth.clamp(stats.radius(RADIUS), 2F, 20F);
                long now = l.getGameTime();
                if (r >= BIG) {
                    Long until = LOCKED.get(c.getUUID());
                    if (until != null && until > now) {
                        LiUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_i.shinra_locked",
                                String.format("%.1f", (until - now) / 20F)).withStyle(ChatFormatting.GRAY));
                        LiUtil.sound(l, c.position(), SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.6F);
                        return;
                    }
                    LOCKED.put(c.getUUID(), now + LOCKOUT);
                }
                Vec3 p = LiUtil.center(c);
                LiUtil.fx(l, LiNet.SHINRA, p, Vec3.ZERO, RINNEGAN, 14 + (int) r, r);
                ElementFx.shockwave(l, c.position().add(0, 0.15, 0), RINNEGAN, r * 1.1F, 14, null);
                ElementFx.flash(l, p, 0xFFFFFF, Math.min(6F, r * 0.4F));
                ElementFx.shake(l, p, r * 3, Math.min(2.2F, 0.5F + r * 0.08F), 14);
                LiUtil.sound(l, p, SoundEvents.WARDEN_SONIC_BOOM, Math.min(1.6F, 0.6F + r * 0.06F), 1.3F);
                LiUtil.sound(l, p, SoundEvents.GENERIC_EXPLODE, 0.9F, 1.5F);
                for (LivingEntity e : LiUtil.foesAround(ctx, p, r, 40)) {
                    double d = Math.sqrt(e.distanceToSqr(p));
                    float falloff = (float) (1.0 - 0.5 * d / r);
                    ctx.damage(e, DAMAGE * falloff, stats, c);
                    repel(e, p, (float) (Math.min(3.2, 1.6 + r * 0.08) * falloff * Math.min(1.6F, power)));
                }
                // Projéteis inimigos voltam
                for (Projectile pr : l.getEntitiesOfClass(Projectile.class, new AABB(p, p).inflate(r),
                        pr -> pr.getOwner() != c && pr.distanceToSqr(p) <= r * r && !ctx.isAlly(pr.getOwner()))) {
                    Vec3 out = pr.position().subtract(p);
                    if (out.lengthSqr() < 1.0E-4) out = c.getLookAngle();
                    double sp = Math.max(0.8, pr.getDeltaMovement().length());
                    pr.setDeltaMovement(out.normalize().scale(sp));
                    pr.hurtMarked = true;
                }
                return;
            }
            if (!(target instanceof LivingEntity le)) return;
            Vec3 from = ctx.hasOrigin() && ctx.anchor() != null ? ctx.anchor() : LiUtil.center(c);
            ctx.damage(le, DAMAGE, stats, c);
            repel(le, from, 1.8F * Math.min(1.6F, power));
            LiUtil.fx(l, LiNet.SHINRA, LiUtil.center(le), Vec3.ZERO, RINNEGAN, 10, Math.max(1.5F, le.getBbWidth() * 2F));
            LiUtil.sound(l, le.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.35F, 1.8F);
        }

        static void repel(LivingEntity e, Vec3 from, float strength) {
            Vec3 out = LiUtil.center(e).subtract(from);
            if (out.lengthSqr() < 1.0E-4) out = new Vec3(0, 0, 1);
            out = out.normalize();
            float s = LiUtil.boss(e) ? strength * 0.2F : strength;
            e.setDeltaMovement(e.getDeltaMovement().add(out.x * s, 0.25 + Math.max(0, out.y) * s * 0.5 + 0.08 * s, out.z * s));
            e.hurtMarked = true;
        }
    }

    // =====================================================================================
    //  Bansho Ten'in
    // =====================================================================================
    /**
     * Atração. No alvo: puxa-o até a sua frente (força cresce com a distância; chefes quase não saem do lugar). Em você:
     * puxa os inimigos num raio de 8 (Expandir +; máx. 20; até 8 alvos). Com <b>Inverter</b>: é VOCÊ quem voa até o alvo.
     * 1 de dano (só para marcar o acerto).
     */
    public static class Bansho extends EffectGlyph {
        public static final float RADIUS = 8F;

        public Bansho() {
            super("bansho_tenin", 12);
            LiGlyphs.config(this, Element.GRAVITY, 2, 3);
        }

        @Override
        public float baseDamage() { return 1F; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel l = ctx.level();
            if (LiUtil.self(ctx, target)) {
                if (!LiUtil.once(ctx, "bansho")) return;
                float r = Mth.clamp(stats.radius(RADIUS), 3F, 20F);
                ElementFx.shockwave(l, c.position().add(0, 1, 0), RINNEGAN, r, 14, null);
                LiUtil.sound(l, c.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.6F);
                for (LivingEntity e : LiUtil.foesAround(ctx, c.position(), r, 8)) pull(ctx, stats, c, e, false);
                return;
            }
            if (!(target instanceof LivingEntity le)) return;
            pull(ctx, stats, c, le, stats.inverted);
        }

        static void pull(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity e, boolean inverted) {
            ServerLevel l = ctx.level();
            Vec3 a = LiUtil.center(c), b = LiUtil.center(e);
            LiUtil.fx(l, LiNet.BANSHO, a, b.subtract(a), RINNEGAN, 10, 1F);
            LiUtil.sound(l, b, SoundEvents.ENDER_EYE_LAUNCH, 0.8F, 0.6F);
            if (!LiUtil.self(ctx, e)) ctx.damage(e, 1F, stats, c);
            Entity mover = inverted ? c : e;
            Vec3 to = inverted ? b : a.add(c.getLookAngle().scale(1.5));
            Vec3 d = to.subtract(LiUtil.center(mover));
            double dist = d.length();
            if (dist < 1.5) return;
            double s = Math.min(2.4, 0.35 + dist * 0.22);
            if (!inverted && LiUtil.boss(e)) s *= 0.15;
            Vec3 v = d.normalize().scale(s);
            mover.setDeltaMovement(v.x, Math.max(v.y, 0) + 0.25 + Math.min(0.3, dist * 0.02), v.z);
            mover.hurtMarked = true;
            mover.resetFallDistance();
        }
    }

    // =====================================================================================
    //  Chibaku Tensei
    // =====================================================================================
    /**
     * Um núcleo negro sobe 5 blocos acima do ponto (em você: 6 blocos à frente) e puxa para o alto TODO inimigo num raio
     * de 7 (Expandir +; máx. 16), prendendo-os numa casca girando em volta dele por 6 s (Estender +; máx. 20 s): ninguém
     * escapa e cada um leva 2,5 de dano a cada 0,5 s. Pedras e terra voam em volta (só visual — nenhum bloco sai do
     * lugar). Chefes não são erguidos: ficam Lentos III enquanto o núcleo existir. No fim, todos caem. No máx. 16
     * presos por núcleo e 1 núcleo por conjurador.
     */
    public static class Chibaku extends EffectGlyph {
        public static final float DAMAGE = 2.5F;
        public static final float RADIUS = 7F;
        public static final int TICKS = 120;
        private static final Map<UUID, Zone> ZONES = new HashMap<>();

        public Chibaku() {
            super("chibaku_tensei", 40);
            LiGlyphs.config(this, Element.GRAVITY, 4, 5);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public int hintDuration() { return TICKS; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !LiUtil.once(ctx, "chibaku")) return;
            ServerLevel l = ctx.level();
            Vec3 base;
            if (LiUtil.self(ctx, target)) {
                Vec3 look = c.getLookAngle();
                base = c.position().add(new Vec3(look.x, 0, look.z).normalize().scale(6));
            } else {
                base = target.position();
            }
            Vec3 core = base.add(0, 5, 0);
            float r = Mth.clamp(stats.radius(RADIUS), 4F, 16F);
            int ticks = Mth.clamp(stats.duration(TICKS), 40, 400);
            BlockState debris = l.getBlockState(BlockPos.containing(base).below());
            if (debris.isAir()) debris = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
            Zone old = ZONES.put(c.getUUID(), new Zone(ctx, stats, c, l, core, r, l.getGameTime() + ticks, debris));
            if (old != null) old.release();
            LiUtil.fx(l, LiNet.CHIBAKU, core, Vec3.ZERO, CORE, ticks, Math.min(4F, 1.2F + r * 0.18F));
            ElementFx.shockwave(l, base.add(0, 0.2, 0), RINNEGAN, r, 20, null);
            ElementFx.shake(l, core, 32, 1.0F, 30);
            LiUtil.sound(l, core, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.4F, 0.5F);
            LiUtil.sound(l, core, SoundEvents.WARDEN_EMERGE, 0.9F, 0.7F);
        }

        /** Todo tick do servidor. */
        public static void tick(MinecraftServer server) {
            if (ZONES.isEmpty()) return;
            for (Iterator<Zone> it = ZONES.values().iterator(); it.hasNext(); ) {
                Zone z = it.next();
                if (!z.tick()) {
                    z.release();
                    it.remove();
                }
            }
        }

        public static void clear() {
            ZONES.clear();
        }

        private static final class Zone {
            final SpellContext ctx;
            final SpellStats stats;
            final LivingEntity caster;
            final ServerLevel level;
            final Vec3 core;
            final float radius;
            final long end;
            final BlockState debris;
            final List<LivingEntity> held = new ArrayList<>();
            int age;

            Zone(SpellContext ctx, SpellStats stats, LivingEntity caster, ServerLevel level, Vec3 core, float radius, long end, BlockState debris) {
                this.ctx = ctx;
                this.stats = stats;
                this.caster = caster;
                this.level = level;
                this.core = core;
                this.radius = radius;
                this.end = end;
                this.debris = debris;
            }

            boolean tick() {
                if (level.getGameTime() >= end || caster.isRemoved() || caster.level() != level) return false;
                age++;
                // Puxa (a cada 5 ticks procura novos inimigos)
                if (age % 5 == 1) {
                    for (LivingEntity e : LiUtil.foesAround(ctx, core, radius + 2, 24)) {
                        if (LiUtil.boss(e)) {
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 2, false, true), caster);
                            continue;
                        }
                        if (!held.contains(e) && held.size() < 16) held.add(e);
                    }
                }
                float shell = 1.6F + radius * 0.15F;
                for (int i = 0; i < held.size(); i++) {
                    LivingEntity e = held.get(i);
                    if (!e.isAlive() || e.level() != level) continue;
                    // Cada preso tem um lugar fixo na casca (espiral de Fibonacci), girando devagar
                    double golden = i * 2.39996 + age * 0.04;
                    double y = 1 - 2 * ((i + 0.5) / Math.max(4, held.size()));
                    double rr = Math.sqrt(1 - y * y);
                    Vec3 spot = core.add(Math.cos(golden) * rr * shell, y * shell - e.getBbHeight() * 0.5, Math.sin(golden) * rr * shell);
                    Vec3 d = spot.subtract(e.position());
                    double len = d.length();
                    Vec3 v = len > 0.05 ? d.scale(Math.min(0.9, 0.25 + len * 0.12) / len) : Vec3.ZERO;
                    e.setDeltaMovement(v);
                    e.hurtMarked = true;
                    e.resetFallDistance();
                    if (age % 10 == 0) {
                        e.invulnerableTime = 0;
                        ctx.damage(e, DAMAGE, stats, caster);
                    }
                }
                // Detritos só visuais (pedaços do chão girando em volta do núcleo)
                if (age % 3 == 0) {
                    BlockParticleOption bp = new BlockParticleOption(ParticleTypes.BLOCK, debris);
                    for (int k = 0; k < 3; k++) {
                        double a = level.random.nextDouble() * Math.PI * 2, y = level.random.nextDouble() * 2 - 1;
                        double rr = Math.sqrt(1 - y * y) * (shell + 0.6);
                        ElementFx.sendFar(level, bp, core.x + Math.cos(a) * rr, core.y + y * (shell + 0.6), core.z + Math.sin(a) * rr,
                                3, 0.15, 0.15, 0.15, 0.05);
                    }
                    // poeira subindo do chão em direção ao núcleo
                    ElementFx.sendFar(level, new BlockParticleOption(ParticleTypes.FALLING_DUST, debris),
                            core.x, core.y - 4.5, core.z, 4, radius * 0.4, 0.4, radius * 0.4, 0.02);
                }
                if (age % 30 == 0) LiUtil.sound(level, core, SoundEvents.RESPAWN_ANCHOR_AMBIENT, 1.0F, 0.5F);
                return true;
            }

            void release() {
                for (LivingEntity e : held) {
                    if (!e.isAlive()) continue;
                    e.resetFallDistance();
                    e.hurtMarked = true;
                }
                held.clear();
                ElementFx.flash(level, core, 0xB9A6E8, 4F);
                LiUtil.sound(level, core, SoundEvents.GENERIC_EXPLODE, 0.8F, 0.6F);
            }
        }
    }

    private PainGlyphs() {}
}
