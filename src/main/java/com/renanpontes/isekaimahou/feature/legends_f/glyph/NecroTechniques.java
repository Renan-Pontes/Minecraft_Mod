package com.renanpontes.isekaimahou.feature.legends_f.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_f.LfNet;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.feature.legends_f.entity.ShadowSoldierEntity;
import com.renanpontes.isekaimahou.feature.legends_f.necro.MonarchState;
import com.renanpontes.isekaimahou.feature.legends_f.necro.ShadowArmy;
import com.renanpontes.isekaimahou.feature.legends_f.necro.ShadowRemnants;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Técnicas de origem do NECROMANTE (homenagem a Solo Leveling): "Levante-se!", Comando do Monarca e Troca de Sombras.
 * A forma G "Monarca das Sombras" fica em {@link MonarchForm}.
 */
public final class NecroTechniques {
    public static final int COLOR = 0x6A3DD8;

    public static List<EffectGlyph> all() {
        return List.of(new Arise(), new ShadowCommand(), new ShadowExchange());
    }

    // =====================================================================================
    //  Levante-se!
    // =====================================================================================
    /**
     * Ergue as sombras de criaturas mortas por perto (até 60 s depois da morte) como Soldados das Sombras PERMANENTES do
     * seu exército (até o limite: 3 + Círculo + 2×rank). Cada sombra tem 3 tentativas: criaturas fracas sempre
     * levantam; fortes e chefes resistem mais (o seu nível e Amplificar ajudam).
     */
    public static class Arise extends EffectGlyph {
        public Arise() {
            super("necro_arise", 30);
            element(Element.SHADOW);
            origin(MagicPath.NECROMANCER);
            tier(4);
            minCircle(4);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float hintRadius() { return 6F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            boolean self = target == ctx.caster();
            arise(ctx, target.position(), stats.radius(self ? 10F : 6F), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            arise(ctx, hit.getLocation(), stats.radius(6F), stats);
        }

        private void arise(SpellContext ctx, Vec3 c, float radius, SpellStats stats) {
            if (!(ctx.caster() instanceof ServerPlayer sp) || LfUtil.isFake(sp) || !LfUtil.oncePerTick(ctx, "arise")) return;
            ServerLevel level = ctx.level();
            List<ShadowRemnants.Remnant> list = ShadowRemnants.near(level, c, radius);
            if (list.isEmpty()) {
                LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.arise_none").withStyle(ChatFormatting.GRAY));
                return;
            }
            LfUtil.banner(sp, "announce.isekai_mahou.legends_f.arise", COLOR, true);
            // v1.3: o chão inteiro vira sombra e a energia corre para a mão do Monarca antes de as sombras subirem
            ElementFx.area(level, c, Element.SHADOW, 0x6A3DD8, radius, 4, 60);
            ElementFx.gather(level, sp, Element.SHADOW, 0x8A4FF0, 3.5F, 4, 16);
            ElementFx.shake(level, c, radius + 16, 0.6F, 24);
            LfUtil.sound(level, sp.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 0.6F);
            int playerLevel = MagicApi.get(sp).level();
            int max = 4 + Math.max(0, stats.amplify) * 2 + stats.count() - 1;
            int raised = 0;
            for (ShadowRemnants.Remnant r : list) {
                if (max-- <= 0) break;
                if (ShadowArmy.size(sp) >= ShadowArmy.cap(sp)) {
                    LfUtil.chat(sp, Component.translatable("message.isekai_mahou.legends_f.army_full", ShadowArmy.cap(sp))
                            .withStyle(ChatFormatting.DARK_PURPLE));
                    break;
                }
                float chance = chance(r, playerLevel, stats, MonarchState.active(sp));
                if (level.random.nextFloat() < chance) {
                    CompoundTag e = ShadowArmy.enlist(sp, r);
                    if (e == null) break;
                    ShadowRemnants.remove(r);
                    ShadowSoldierEntity s = ShadowArmy.manifest(sp, e, r.pos, true);
                    ShadowArmy.ariseFx(level, r.pos, r.height);
                    LfUtil.sound(level, r.pos, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.9F, 0.6F);
                    LfUtil.sound(level, r.pos, SoundEvents.WITHER_SKELETON_AMBIENT, 1.0F, 0.5F);
                    LfUtil.chat(sp, Component.translatable("message.isekai_mahou.legends_f.arise_ok",
                            s != null ? s.getDisplayName() : Component.literal(r.type), ShadowArmy.size(sp), ShadowArmy.cap(sp))
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                    raised++;
                } else {
                    r.tries--;
                    LfUtil.ring(level, r.pos.add(0, 0.1, 0), 0x3A2A4A, 14, 1.8F);
                    level.sendParticles(Vfx.smoke(), r.pos.x, r.pos.y + 0.5, r.pos.z, 14, 0.4, 0.4, 0.4, 0.02);
                    if (r.tries <= 0) {
                        ShadowRemnants.remove(r);
                        LfUtil.chat(sp, Component.translatable("message.isekai_mahou.legends_f.arise_lost").withStyle(ChatFormatting.GRAY));
                    } else {
                        LfUtil.chat(sp, Component.translatable("message.isekai_mahou.legends_f.arise_fail", r.tries,
                                Math.round(chance * 100)).withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            if (raised > 0) MagicApi.addCounter(sp, "legends_f.arise", raised);
        }

        /** Chance de erguer: fácil para criaturas mais fracas que você, difícil para as fortes e os chefes. */
        static float chance(ShadowRemnants.Remnant r, int playerLevel, SpellStats stats, boolean monarch) {
            if (monarch) return 1F;
            float power = 30F + 6F * playerLevel;
            float c = 1.15F - 0.6F * (r.maxHp / power) + 0.1F * Math.max(0, stats.amplify);
            if (r.boss) c *= 0.5F;
            return Mth.clamp(c, r.boss ? 0.08F : 0.12F, 1F);
        }
    }

    // =====================================================================================
    //  Comando do Monarca
    // =====================================================================================
    /**
     * Ordens ao exército: num inimigo — "Ataquem!" (todos focam nele; chama quem estiver na sombra); em você ou num bloco
     * — "Saiam!" (chama o exército), de novo alterna seguir/guardar posição, agachado — "Voltem." (de volta à sombra);
     * num soldado seu — mostra a ficha dele (agachado: liberta-o para sempre).
     */
    public static class ShadowCommand extends EffectGlyph {
        public ShadowCommand() {
            super("necro_shadow_command", 6);
            element(Element.SHADOW);
            origin(MagicPath.NECROMANCER);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public boolean isBeneficial() { return false; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(ctx.caster() instanceof ServerPlayer sp) || LfUtil.isFake(sp) || !LfUtil.oncePerTick(ctx, "command")) return;
            if (target == sp || !(target instanceof LivingEntity le) || ctx.isAlly(target)) {
                selfOrder(sp);
                return;
            }
            if (ShadowArmy.live(sp.getUUID()).isEmpty()) ShadowArmy.manifestAll(sp, false);
            if (ShadowArmy.live(sp.getUUID()).isEmpty()) {
                emptyArmy(sp);
                return;
            }
            ShadowArmy.focus(sp.getUUID(), le);
            LfUtil.banner(sp, "announce.isekai_mahou.legends_f.attack", COLOR, false);
            LfUtil.ring(ctx.level(), le.position().add(0, 0.1, 0), 0xB050FF, 14, 2F);
            LfUtil.sound(ctx.level(), sp.position(), SoundEvents.WITHER_SKELETON_AMBIENT, 1.0F, 0.6F);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!(ctx.caster() instanceof ServerPlayer sp) || LfUtil.isFake(sp) || !LfUtil.oncePerTick(ctx, "command")) return;
            selfOrder(sp);
        }

        private static void selfOrder(ServerPlayer sp) {
            List<ShadowSoldierEntity> live = ShadowArmy.live(sp.getUUID());
            ShadowSoldierEntity looked = lookedAt(sp, live);
            if (looked != null) {
                soldierCard(sp, looked);
                return;
            }
            if (sp.isShiftKeyDown()) {
                int n = ShadowArmy.recallAll(sp.getUUID(), true);
                LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.army_recall", n).withStyle(ChatFormatting.DARK_PURPLE));
                return;
            }
            if (live.isEmpty() || live.size() < ShadowArmy.size(sp) && !ShadowArmy.holding(sp.getUUID())) {
                int n = ShadowArmy.manifestAll(sp, false);
                if (n == 0 && live.isEmpty()) {
                    emptyArmy(sp);
                    return;
                }
                if (n > 0) {
                    LfUtil.banner(sp, "announce.isekai_mahou.legends_f.come_out", COLOR, false);
                    int total = ShadowArmy.live(sp.getUUID()).size();
                    LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.army_out", total, ShadowArmy.size(sp),
                            ShadowArmy.upkeepShown(total)).withStyle(ChatFormatting.LIGHT_PURPLE));
                    return;
                }
            }
            boolean hold = ShadowArmy.toggleHold(sp.getUUID());
            LfUtil.actionbar(sp, Component.translatable(hold ? "message.isekai_mahou.legends_f.army_hold"
                    : "message.isekai_mahou.legends_f.army_follow").withStyle(ChatFormatting.DARK_PURPLE));
        }

        private static void emptyArmy(ServerPlayer sp) {
            LfUtil.actionbar(sp, Component.translatable(ShadowArmy.size(sp) == 0 ? "message.isekai_mahou.legends_f.army_empty"
                    : "message.isekai_mahou.legends_f.army_none_ready").withStyle(ChatFormatting.GRAY));
        }

        /** Soldado seu na mira (até 8 blocos). */
        private static ShadowSoldierEntity lookedAt(ServerPlayer sp, List<ShadowSoldierEntity> live) {
            Vec3 eye = sp.getEyePosition();
            Vec3 end = eye.add(sp.getLookAngle().scale(8));
            ShadowSoldierEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (ShadowSoldierEntity s : live) {
                var hit = s.getBoundingBox().inflate(0.2).clip(eye, end);
                if (hit.isPresent() && hit.get().distanceToSqr(eye) < bestD) {
                    bestD = hit.get().distanceToSqr(eye);
                    best = s;
                }
            }
            return best;
        }

        /** Pedido de libertação pendente: dono -> [id do soldado, tempo]. */
        private static final java.util.Map<java.util.UUID, long[]> RELEASE = new java.util.HashMap<>();

        private static void soldierCard(ServerPlayer sp, ShadowSoldierEntity s) {
            if (sp.isShiftKeyDown()) {
                long now = sp.serverLevel().getGameTime();
                long[] pend = RELEASE.get(sp.getUUID());
                if (pend == null || pend[0] != s.rosterId() || now - pend[1] > 100) {
                    RELEASE.put(sp.getUUID(), new long[] {s.rosterId(), now});
                    LfUtil.chat(sp, Component.translatable("message.isekai_mahou.legends_f.soldier_release_confirm", s.getDisplayName())
                            .withStyle(ChatFormatting.GOLD));
                    return;
                }
                RELEASE.remove(sp.getUUID());
                Component name = s.getDisplayName();
                if (ShadowArmy.release(sp, s.rosterId())) {
                    LfUtil.chat(sp, Component.translatable("message.isekai_mahou.legends_f.soldier_released", name).withStyle(ChatFormatting.GRAY));
                }
                return;
            }
            CompoundTag e = ShadowArmy.entry(sp, s.rosterId());
            int kills = e == null ? 0 : e.getInt("kills");
            LfUtil.chat(sp, Component.translatable("message.isekai_mahou.legends_f.soldier_card", s.getDisplayName(),
                    Math.round(s.getHealth()), Math.round(s.getMaxHealth()), kills).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    // =====================================================================================
    //  Troca de Sombras
    // =====================================================================================
    /** Troca de lugar na hora com um dos seus soldados manifestados (o mais perto do ponto atingido / da sua mira). */
    public static class ShadowExchange extends EffectGlyph {
        public ShadowExchange() {
            super("necro_shadow_exchange", 20);
            element(Element.SHADOW);
            origin(MagicPath.NECROMANCER);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target instanceof ShadowSoldierEntity s && ctx.caster() != null && ctx.caster().getUUID().equals(s.getOwnerUUID())) {
                swap(ctx, s);
            } else {
                exchange(ctx, target == ctx.caster() ? null : target.position());
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            exchange(ctx, hit.getLocation());
        }

        private void exchange(SpellContext ctx, Vec3 near) {
            if (!(ctx.caster() instanceof ServerPlayer sp) || LfUtil.isFake(sp)) return;
            List<ShadowSoldierEntity> live = ShadowArmy.live(sp.getUUID());
            ShadowSoldierEntity best = null;
            double bestScore = Double.MAX_VALUE;
            Vec3 eye = sp.getEyePosition();
            Vec3 look = sp.getLookAngle();
            for (ShadowSoldierEntity s : live) {
                if (s.level() != sp.level() || s.rising() > 0) continue;
                double score;
                if (near != null) {
                    score = s.distanceToSqr(near);
                } else {
                    Vec3 to = s.position().add(0, s.getBbHeight() * 0.5, 0).subtract(eye);
                    double d = to.length();
                    double dot = d < 0.01 ? 1 : to.dot(look) / d;
                    score = (1.5 - dot) * 100 + d * 0.1;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = s;
                }
            }
            if (best == null) {
                LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.exchange_none").withStyle(ChatFormatting.GRAY));
                return;
            }
            swap(ctx, best);
        }

        private void swap(SpellContext ctx, ShadowSoldierEntity s) {
            if (!(ctx.caster() instanceof ServerPlayer sp) || !LfUtil.oncePerTick(ctx, "exchange")) return;
            ServerLevel level = ctx.level();
            Vec3 a = sp.position(), b = s.position();
            LfUtil.fx(level, LfNet.SWAP, a.add(0, 1, 0), b.subtract(a), COLOR, 16, 1.2F);
            sp.teleportTo(b.x, b.y, b.z);
            sp.resetFallDistance();
            s.teleportTo(a.x, a.y, a.z);
            s.getNavigation().stop();
            LfUtil.sound(level, a, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.5F);
            LfUtil.sound(level, b, SoundEvents.SCULK_CLICKING, 1.2F, 0.6F);
            level.sendParticles(Vfx.largeSmoke(), b.x, b.y + 1, b.z, 16, 0.4, 0.6, 0.4, 0.02);
            level.sendParticles(Vfx.largeSmoke(), a.x, a.y + 1, a.z, 16, 0.4, 0.6, 0.4, 0.02);
        }
    }

    private NecroTechniques() {}
}
