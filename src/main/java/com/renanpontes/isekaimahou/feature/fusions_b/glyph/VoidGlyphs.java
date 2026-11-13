package com.renanpontes.isekaimahou.feature.fusions_b.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.VoidRiftEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos do VAZIO (Vento + Sombra).
 * Tier 1: Apagar · Tier 2: Rasgo · Tier 3: Banir · Tier 4: Dobra.
 */
public final class VoidGlyphs {

    public static void register() {
        GlyphRegistry.register(new Erase());
        GlyphRegistry.register(new Rift());
        GlyphRegistry.register(new Banish());
        GlyphRegistry.register(new Warp());
    }

    /** Efeito visual de "pedaço apagado da realidade" em volta de um ponto. */
    static void voidFx(ServerLevel sl, Vec3 c, double spread, int count) {
        sl.sendParticles(Vfx.portal(), c.x, c.y, c.z, count, spread, spread, spread, 0.6);
        sl.sendParticles(Vfx.reversePortal(), c.x, c.y, c.z, count / 2, spread * 0.6, spread * 0.6, spread * 0.6, 0.05);
        sl.sendParticles(FbFx.fade(FbFx.VOID, FbFx.VOID_BLACK, 1.5F), c.x, c.y, c.z, count / 2, spread, spread, spread, 0);
    }

    /**
     * Apagar: arranca um pedaço do alvo da existência — dano 4,5 que IGNORA ARMADURA (encantamentos de proteção
     * ainda contam). Em Si Mesmo: apaga os inimigos em volta.
     */
    public static class Erase extends EffectGlyph {
        public Erase() {
            super("erase", 14);
            element(Element.VOID);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity e : FbUtil.offensiveTargets(ctx, target, stats.radius(2.5F))) {
                FbUtil.typedSpellDamage(ctx, e, 4.5F, stats, FbRegistries.VOID_ERASE_DAMAGE, ctx.element());
                Vec3 c = FbUtil.center(e);
                voidFx(sl, c, e.getBbWidth() * 0.5, 30);
                // "Cortes" retos de vazio atravessando o corpo
                for (int i = 0; i < 3; i++) {
                    Vec3 d = new Vec3(sl.random.nextDouble() - 0.5, sl.random.nextDouble() - 0.5, sl.random.nextDouble() - 0.5)
                            .normalize().scale(e.getBbWidth() * 0.5 + 0.5);
                    FbFx.line(sl, c.subtract(d), c.add(d), FbFx.dust(FbFx.VOID_BLACK, 1.0F), 6.0);
                }
                FbFx.sound(sl, c, SoundEvents.ENDER_EYE_DEATH, 1.2F, 0.5F);
                FbFx.sound(sl, c, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.4F);
            }
        }
    }

    /**
     * Rasgo: abre uma fenda no espaço (no alvo, no bloco atingido ou à sua frente) por 6s. Quem fica a até 2,5
     * blocos leva dano 2 a cada meio segundo e é puxado de leve para dentro. Máx. 2 fendas por conjurador.
     */
    public static class Rift extends EffectGlyph {
        private static final String KEY = "rift";

        public Rift() {
            super("rift", 18);
            element(Element.VOID);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FbUtil.once(ctx, KEY)) return;
            Vec3 at = FbUtil.isSelf(ctx, target)
                    ? target.position().add(FbUtil.flatLook(ctx).scale(2.5)).add(0, 1, 0)
                    : target.position().add(0, 0.5, 0);
            open(ctx, FbUtil.ground(ctx.level(), at, 6), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (FbUtil.isSelfFeetEcho(ctx, hit, KEY) || !FbUtil.once(ctx, KEY)) return;
            open(ctx, FbUtil.ground(ctx.level(), hit.getLocation().add(0, 0.5, 0), 6), stats);
        }

        private static void open(SpellContext ctx, Vec3 at, SpellStats stats) {
            float yaw = ctx.caster() != null ? ctx.caster().getYRot() + 90F : ctx.random().nextFloat() * 360F;
            VoidRiftEntity.open(ctx, at, yaw, stats.radius(2.5F), stats.duration(120), 2.0F * stats.power(), stats);
        }
    }

    /**
     * Banir: arremessa o alvo pelo vazio para um lugar seguro aleatório a 16–32 blocos (×poder, máx. ×2).
     * Ele chega tonto e esquece quem estava atacando. Chefes resistem (só levam o dano).
     */
    public static class Banish extends EffectGlyph {
        public Banish() {
            super("banish", 20);
            element(Element.VOID);
            tier(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            float scale = Math.min(2F, FbUtil.power(ctx, stats));
            for (LivingEntity e : FbUtil.offensiveTargets(ctx, target, stats.radius(3F))) {
                if (FbUtil.untouchable(e)) continue;
                FbUtil.hurt(ctx, e, 2.0F, stats, null);
                if (!e.isAlive()) continue;
                Vec3 from = FbUtil.center(e);
                if (FbUtil.isBoss(e) || e.isPassenger() || e.isVehicle()) {
                    voidFx(sl, from, 0.4, 16);
                    FbFx.sound(sl, from, SoundEvents.SHIELD_BLOCK, 0.8F, 0.5F);
                    continue;
                }
                Vec3 spot = null;
                for (int i = 0; i < 20 && spot == null; i++) {
                    double a = sl.random.nextDouble() * Math.PI * 2;
                    double d = (16 + sl.random.nextDouble() * 16) * scale;
                    spot = FbUtil.findSafeSpot(e, e.position().add(Math.cos(a) * d, 0, Math.sin(a) * d), 10, 1);
                }
                if (spot == null) {
                    voidFx(sl, from, 0.4, 16);
                    continue;
                }
                FbFx.implode(sl, from, Vfx.portal(), 1.5, 24, 0.3);
                voidFx(sl, from, 0.4, 30);
                FbFx.sound(sl, from, SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.6F);
                FbUtil.teleport(e, spot);
                FbUtil.forgetTarget(e);
                Vec3 to = FbUtil.center(e);
                voidFx(sl, to, 0.5, 40);
                FbFx.sound(sl, to, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);
                if (e instanceof Player) e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0), ctx.caster());
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0), ctx.caster());
            }
        }
    }

    /**
     * Dobra: dobra o espaço. Em Si Mesmo leva você até 48 blocos (×poder, máx. 96) na direção do olhar,
     * ATRAVESSANDO paredes, sempre para um lugar seguro (com chão, sem lava, sem sufocar). Se a magia acerta
     * alguém, vocês trocam de lugar; se acerta um bloco, você aparece ali.
     */
    public static class Warp extends EffectGlyph {
        private static final String KEY = "warp";

        public Warp() {
            super("warp", 24);
            element(Element.VOID);
            tier(4);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !c.isAlive() || !FbUtil.once(ctx, KEY)) return;
            if (target == c) {
                double range = Math.min(96.0, 48.0 * FbUtil.power(ctx, stats));
                Vec3 dest = forward(c, range);
                if (dest == null) {
                    if (c instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.isekai_mahou.warp.no_space"), true);
                    voidFx(ctx.level(), FbUtil.center(c), 0.3, 10);
                    return;
                }
                warp(ctx.level(), c, dest);
            } else if (target instanceof LivingEntity le && !FbUtil.isBoss(le) && !FbUtil.untouchable(le)
                    && !le.isPassenger() && !le.isVehicle() && le.level() == c.level()) {
                // Só troca se cada um cabe no lugar do outro (um mob grande não pode ir parar num túnel estreito)
                if (FbUtil.fitsBlocks(le, c.position()) && FbUtil.fitsBlocks(c, le.position())) {
                    swap(ctx.level(), c, le);
                } else {
                    voidFx(ctx.level(), FbUtil.center(le), 0.4, 16);
                    if (c instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.isekai_mahou.warp.no_space"), true);
                }
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !c.isAlive() || FbUtil.isSelfFeetEcho(ctx, hit, KEY) || !FbUtil.once(ctx, KEY)) return;
            Vec3 want = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
            Vec3 dest = FbUtil.findSafeSpot(c, want, 4, 2);
            if (dest != null) warp(ctx.level(), c, dest);
        }

        /** O ponto seguro mais distante na linha do olhar (procura de longe para perto). */
        @Nullable
        static Vec3 forward(LivingEntity c, double range) {
            Vec3 eye = c.getEyePosition();
            Vec3 look = c.getLookAngle();
            for (double d = range; d >= 4; d -= 2) {
                Vec3 p = eye.add(look.scale(d)).subtract(0, c.getEyeHeight(), 0);
                Vec3 spot = FbUtil.findSafeSpot(c, p, 6, 1);
                if (spot != null) return spot;
            }
            return null;
        }

        static void warp(ServerLevel sl, LivingEntity c, Vec3 dest) {
            Vec3 from = FbUtil.center(c);
            FbFx.implode(sl, from, Vfx.portal(), 1.8, 30, 0.4);
            voidFx(sl, from, 0.5, 30);
            FbFx.sound(sl, from, SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.5F);
            FbFx.sound(sl, from, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.8F, 1.4F);
            FbUtil.teleport(c, dest);
            Vec3 to = FbUtil.center(c);
            FbFx.shockwave(sl, to, FbFx.dust(FbFx.VOID, 1.4F), 24, 0.3);
            voidFx(sl, to, 0.5, 30);
            FbFx.sound(sl, to, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.9F);
        }

        static void swap(ServerLevel sl, LivingEntity a, LivingEntity b) {
            Vec3 pa = a.position(), pb = b.position();
            voidFx(sl, FbUtil.center(a), 0.4, 24);
            voidFx(sl, FbUtil.center(b), 0.4, 24);
            FbUtil.teleport(a, pb);
            FbUtil.teleport(b, pa);
            FbUtil.forgetTarget(b);
            FbFx.line(sl, pa.add(0, 1, 0), pb.add(0, 1, 0), Vfx.reversePortal(), 1.5);
            FbFx.sound(sl, pa, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
            FbFx.sound(sl, pb, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.7F);
        }
    }

    private VoidGlyphs() {}
}
