package com.renanpontes.isekaimahou.feature.legends_f.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_f.LfNet;
import com.renanpontes.isekaimahou.feature.legends_f.LfRegistry;
import com.renanpontes.isekaimahou.feature.legends_f.LfStates;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.feature.legends_f.entity.BroomEntity;
import com.renanpontes.isekaimahou.feature.legends_f.entity.EnvyHandsEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Técnicas de origem da BRUXA: Vassoura da Bruxa Viajante (homenagem a Elaina), Maldição do Sono Eterno (Bela
 * Adormecida) e Mãos da Inveja (homenagem leve à Bruxa da Inveja de Re:Zero).
 */
public final class WitchTechniques {
    public static List<EffectGlyph> all() {
        return List.of(new Broom(), new SleepCurse(), new EnvyHands());
    }

    // =====================================================================================
    //  Vassoura da Bruxa Viajante
    // =====================================================================================
    /**
     * Conjura uma vassoura e monta nela: voa para onde você olha (W/S, A/D desliza, agachar desce). Dura 90 s (Estender
     * aumenta); Acelerar e Amplificar deixam mais rápida. Ao descer, você cai devagar.
     */
    public static class Broom extends EffectGlyph {
        public Broom() {
            super("witch_broom", 12);
            element(Element.WIND);
            origin(MagicPath.WITCH);
            tier(2);
            minCircle(2);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return 1800; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof Player p) || LfUtil.isFake(p) || !LfUtil.oncePerTick(ctx, "broom:" + p.getId())) return;
            ServerLevel level = ctx.level();
            if (p.getVehicle() instanceof BroomEntity old) {
                old.setup(old.maxSpeed(), Mth.clamp(stats.duration(1800), 400, 6000));
                LfUtil.actionbar(p, Component.translatable("message.isekai_mahou.legends_f.broom_renew").withStyle(ChatFormatting.LIGHT_PURPLE));
                return;
            }
            if (p.isPassenger()) p.stopRiding();
            BroomEntity b = LfRegistry.BROOM.get().create(level);
            if (b == null) return;
            float speed = 0.7F * stats.speedMult() * (1F + 0.12F * Math.max(0, stats.amplify));
            b.setup(speed, Mth.clamp(stats.duration(1800), 400, 6000));
            b.moveTo(p.getX(), p.getY() + 0.2, p.getZ(), p.getYRot(), 0F);
            level.addFreshEntity(b);
            p.startRiding(b, true);
            p.resetFallDistance();
            level.sendParticles(Vfx.cherry(), p.getX(), p.getY() + 0.5, p.getZ(), 24, 0.6, 0.3, 0.6, 0.03);
            // v1.3: a vassoura sai de um pequeno selo de bruxa no chão, num redemoinho de pétalas
            com.renanpontes.isekaimahou.core.ElementFx.portal(level, p.position().add(0, 0.06, 0), new Vec3(0, 1, 0),
                    com.renanpontes.isekaimahou.core.Element.NATURE, 0xF0C8E8, 1.4F, 2, 16);
            com.renanpontes.isekaimahou.core.ElementFx.impactFx(level, p.position().add(0, 0.3, 0),
                    com.renanpontes.isekaimahou.core.Element.WIND, 0xF0C8E8, 2, null, 0.8F);
            LfUtil.sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.2F);
            LfUtil.sound(level, p.position(), SoundEvents.ELYTRA_FLYING, 0.4F, 1.6F);
            LfUtil.banner(p, "announce.isekai_mahou.legends_f.broom", 0xF0C8E8, false);
            LfUtil.actionbar(p, Component.translatable("message.isekai_mahou.legends_f.broom_on").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    // =====================================================================================
    //  Maldição do Sono Eterno
    // =====================================================================================
    /**
     * Uma roca encantada e espinhos de roseira: o alvo cai num sono encantado por 8 s (chefes 2 s; jogadores 3 s) —
     * não anda nem ataca, e larga o alvo. O primeiro dano acorda, mas sai ×1,5 ("o despertar doloroso").
     */
    public static class SleepCurse extends EffectGlyph {
        public SleepCurse() {
            super("witch_sleep_curse", 18);
            element(Element.SHADOW);
            origin(MagicPath.WITCH);
            tier(3);
            minCircle(3);
        }

        @Override
        public int hintDuration() { return 160; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
            int ticks = stats.duration(160);
            if (Statuses.isBossLike(le)) ticks = Math.max(30, ticks / 4);
            else if (le instanceof Player) ticks = Math.max(40, ticks * 3 / 8);
            le.addEffect(new MobEffectInstance(LfRegistry.ENCHANTED_SLEEP, ticks, 0, false, true, true), ctx.caster());
            if (le instanceof Player) {
                le.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks, 0, false, false), ctx.caster());
                le.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 3, false, false), ctx.caster());
            }
            if (le instanceof Mob m) {
                m.setTarget(null);
                m.getNavigation().stop();
            }
            LfStates.set(le, LfNet.S_SLEEP, ticks, 0);
            ServerLevel level = ctx.level();
            level.sendParticles(Vfx.cherry(), le.getX(), le.getY() + le.getBbHeight(), le.getZ(), 16, 0.4, 0.3, 0.4, 0.02);
            LfUtil.sound(level, le.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.6F);
            LfUtil.sound(level, le.position(), SoundEvents.SWEET_BERRY_BUSH_PLACE, 1.0F, 0.7F);
            if (le instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_f.sleep_you").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }

    // =====================================================================================
    //  Mãos da Inveja
    // =====================================================================================
    /**
     * Mãos negras de sombra brotam do chão em volta do ponto (raio 6) por 5 s: cada uma agarra um inimigo, arrasta para
     * o centro e aperta (3,5 de dano a cada 0,5 s, Corrupção, Lentidão IV). 6 mãos; Dividir dá +2 cada.
     */
    public static class EnvyHands extends EffectGlyph {
        public EnvyHands() {
            super("witch_envy_hands", 28);
            element(Element.SHADOW);
            origin(MagicPath.WITCH);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float baseDamage() { return EnvyHandsEntity.DAMAGE; }

        @Override
        public float hintRadius() { return 6F; }

        @Override
        public int hintDuration() { return 100; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            Vec3 at = target == ctx.caster() ? ctx.caster().position().add(ctx.look().multiply(1, 0, 1).scale(5)) : target.position();
            spawn(ctx, LfUtil.groundBelow(ctx.level(), at.add(0, 1.5, 0), 8), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            Vec3 p = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.3));
            spawn(ctx, LfUtil.groundBelow(ctx.level(), p.add(0, 0.5, 0), 8), stats);
        }

        private void spawn(SpellContext ctx, Vec3 at, SpellStats stats) {
            if (!LfUtil.oncePerTick(ctx, "envy")) return;
            float radius = Mth.clamp(stats.radius(6F), 3F, 14F);
            int hands = 6 + 2 * Math.max(0, stats.split);
            int life = Mth.clamp(stats.duration(100), 40, 400);
            EnvyHandsEntity.spawn(ctx, at, radius, hands, life, stats);
            if (ctx.caster() != null) LfUtil.banner(ctx.caster(), "announce.isekai_mahou.legends_f.envy", 0x8A4FC8, false);
        }
    }

    private WitchTechniques() {}
}
