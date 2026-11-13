package com.renanpontes.isekaimahou.feature.legends_j.glyph;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.StoneWorks;
import com.renanpontes.isekaimahou.feature.legends_j.LjFx;
import com.renanpontes.isekaimahou.feature.legends_j.LjUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Alquimia (homenagem a Fullmetal Alchemist):
 * <ul>
 *   <li>{@link FlameAlchemy} — o estalo do Roy Mustang: explosão PRECISA no ponto atingido, sem estragar o terreno.</li>
 *   <li>{@link AlchemyWall} — a palma do Edward Elric: muralha de pedra temporária / espinhos de pedra.</li>
 * </ul>
 */
public final class AlchemyEffects {

    // =====================================================================================
    //  Alquimia de Fogo (Mustang)
    // =====================================================================================
    /**
     * Estalo! Uma linha de faíscas corre da sua mão até o ponto atingido (criatura ou bloco; em Si Mesmo, o ponto da
     * sua mira, até 32 blocos) e, 0,15 s depois, explode ali: dano 9 de FOGO em volta (raio 2,5, Expandir +; máx. 12
     * alvos) e Em Chamas 4 s. Precisa: NÃO quebra blocos nem põe fogo no chão. Cada criatura só é queimada uma vez por
     * conjuração (formas de área não multiplicam o dano). Na chuva ou na água: "inútil" (30% do dano, sem fogo).
     */
    public static class FlameAlchemy extends EffectGlyph {
        public static final float DAMAGE = 9F;
        public static final int COLOR = 0xFF7A1A;

        public FlameAlchemy() {
            super("flame_alchemy", 16);
            element(Element.FIRE);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return 2.5F; }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            boolean self = LjUtil.self(ctx, target);
            if (self && c == null) return;
            Vec3 at = self ? LjUtil.lookPoint(c, 32) : LjUtil.center(target);
            snap(ctx, at, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            // A forma Si Mesmo também aplica no bloco sob os pés: esse já foi tratado no onEntity
            LivingEntity c = ctx.caster();
            if (c != null && hit.getLocation().distanceToSqr(c.position()) < 2.25) return;
            snap(ctx, hit.getLocation(), stats);
        }

        private static void snap(SpellContext ctx, Vec3 at, SpellStats stats) {
            if (!LjUtil.once(ctx, "flame@" + BlockPos.containing(at).asLong())) return;
            ServerLevel l = ctx.level();
            LivingEntity c = ctx.caster();
            boolean wet = c != null && c.isInWaterRainOrBubble();
            if (c != null && LjUtil.once(ctx, "flame_snap")) {
                Vec3 hand = c.getEyePosition().add(c.getLookAngle().scale(0.5)).add(0, -0.25, 0);
                LjUtil.sound(l, hand, SoundEvents.TRIPWIRE_CLICK_ON, 1.0F, 2.0F);
                LjUtil.sound(l, hand, SoundEvents.FLINTANDSTEEL_USE, 0.8F, 1.6F);
                // Linha de ignição: faíscas da mão até o ponto
                Vec3 d = at.subtract(hand);
                int n = (int) Math.min(28, Math.max(3, d.length() * 1.2));
                for (int i = 0; i <= n; i++) {
                    Vec3 p = hand.add(d.scale(i / (double) n));
                    l.sendParticles(wet ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
                }
                if (wet) {
                    LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.flame_rain").withStyle(ChatFormatting.AQUA));
                }
            }
            SpellScheduler.schedule(3, () -> burst(ctx, at, stats, wet));
        }

        private static void burst(SpellContext ctx, Vec3 at, SpellStats stats, boolean wet) {
            ServerLevel l = ctx.level();
            LivingEntity c = ctx.caster();
            float r = Mth.clamp(stats.radius(2.5F), 1.5F, 6F);
            if (wet) {
                l.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 14, r * 0.3, r * 0.3, r * 0.3, 0.02);
                LjUtil.sound(l, at, SoundEvents.FIRE_EXTINGUISH, 0.9F, 1.2F);
            } else {
                ElementFx.impact(l, at, Element.FIRE, COLOR, 3, null);
                ElementFx.flash(l, at, 0xFFC04A, r * 1.2F);
                l.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                l.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 20, r * 0.35, r * 0.35, r * 0.35, 0.05);
                LjUtil.sound(l, at, SoundEvents.GENERIC_EXPLODE, 1.0F, 1.35F);
                LjUtil.sound(l, at, SoundEvents.BLAZE_SHOOT, 0.9F, 0.8F);
            }
            for (LivingEntity e : LjUtil.foesAround(ctx, at, r + 0.5, 12)) {
                if (!LjUtil.once(ctx, "flamehit@" + e.getUUID())) continue;
                ctx.damage(e, DAMAGE * (wet ? 0.3F : 1F), stats, c, Element.FIRE);
                if (!wet) e.igniteForSeconds(4F);
            }
        }
    }

    // =====================================================================================
    //  Parede de Alquimia (Edward)
    // =====================================================================================
    /**
     * Palma no chão! No bloco atingido sobe uma MURALHA de pedra conjurada de 5 de largura × 3 de altura (Expandir +2 de
     * largura, máx. 9), atravessada à direção de quem conjurou, que dura 8 s (Estender +) e some sozinha. Em Si Mesmo
     * ela sobe 3 blocos à sua frente (escudo). Numa criatura inimiga: ESPINHOS de pedra saem do chão debaixo dela —
     * dano 8 de TERRA, arremesso para cima e Enraizado 1 s.
     * <p>Os blocos são temporários (os mesmos da Muralha do Terra/Vento: voltam ao original), nunca nascem em cidades,
     * torres/masmorras em andamento ou onde você não pode construir.
     */
    public static class AlchemyWall extends EffectGlyph {
        public static final float DAMAGE = 8F;
        public static final int SPARK = 0x7FDBFF;

        public AlchemyWall() {
            super("alchemy_wall", 14);
            element(Element.EARTH);
            tier(2);
            minCircle(2);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public int hintDuration() { return 160; }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (LjUtil.self(ctx, target)) {
                if (c == null) return;
                Vec3 look = horizontal(c.getLookAngle());
                Vec3 front = c.position().add(look.scale(3));
                BlockPos ground = EwUtil.groundBelow(ctx.level(), BlockPos.containing(front.x, c.getY() + 1, front.z), 5);
                if (ground != null) wall(ctx, ground, look, stats);
                return;
            }
            if (target instanceof LivingEntity le && LjUtil.isFoe(ctx, le)) spikes(ctx, le, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c != null && hit.getLocation().distanceToSqr(c.position()) < 2.25) return; // Si Mesmo: já tratado
            BlockPos ground = hit.getDirection() == Direction.UP ? hit.getBlockPos()
                    : EwUtil.groundBelow(ctx.level(), hit.getBlockPos().relative(hit.getDirection()), 5);
            if (ground == null) return;
            Vec3 dir = c != null ? horizontal(Vec3.atCenterOf(ground).subtract(c.position())) : horizontal(ctx.look());
            wall(ctx, ground, dir, stats);
        }

        private static Vec3 horizontal(Vec3 v) {
            Vec3 h = new Vec3(v.x, 0, v.z);
            return h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
        }

        private static void wall(SpellContext ctx, BlockPos ground, Vec3 facing, SpellStats stats) {
            if (!LjUtil.once(ctx, "alchemy_wall")) return;
            ServerLevel l = ctx.level();
            Vec3 base = Vec3.atBottomCenterOf(ground.above());
            clap(l, ctx.caster(), base);
            if (LjUtil.safe(l, base)) {
                LjUtil.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.legends_j.sacred_town").withStyle(ChatFormatting.RED));
                return;
            }
            int width = Math.min(9, 5 + 2 * Math.max(0, stats.aoe));
            int height = 3;
            Vec3 right = new Vec3(-facing.z, 0, facing.x);
            List<List<BlockPos>> columns = new ArrayList<>();
            for (int i = -width / 2; i <= width / 2; i++) {
                Vec3 p = base.add(right.scale(i));
                BlockPos col = EwUtil.groundBelow(l, BlockPos.containing(p.x, base.y + 1, p.z), 4);
                if (col == null) continue;
                List<BlockPos> list = new ArrayList<>();
                for (int h = 1; h <= height; h++) list.add(col.above(h));
                columns.add(list);
                // Faíscas azuis da transmutação correndo pelo chão
                l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, col.getY() + 1.05, p.z, 3, 0.3, 0.05, 0.3, 0.05);
            }
            StoneWorks.build(ctx, columns, 2, stats.duration(160), true, true);
            ElementFx.shockwave(l, base.add(0, 0.05, 0), SPARK, width * 0.7F, 12, null);
        }

        private static void spikes(SpellContext ctx, LivingEntity t, SpellStats stats) {
            ServerLevel l = ctx.level();
            if (LjUtil.once(ctx, "alchemy_clap")) clap(l, ctx.caster(), t.position());
            Vec3 p = t.position();
            BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2 / 5;
                double ox = Math.cos(a) * 0.6, oz = Math.sin(a) * 0.6;
                for (int h = 0; h < 4; h++) {
                    l.sendParticles(stone, p.x + ox * (1 - h * 0.25), p.y + h * 0.4, p.z + oz * (1 - h * 0.25), 2, 0.05, 0.1, 0.05, 0.05);
                }
            }
            ElementFx.pillar(l, p, 0x9C9CA8, 0.5F, 2.2F, 10);
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 0.1, p.z, 8, 0.6, 0.05, 0.6, 0.08);
            LjUtil.sound(l, p, SoundEvents.POINTED_DRIPSTONE_LAND, 1.2F, 0.6F);
            LjUtil.sound(l, p, SoundEvents.STONE_BREAK, 1.0F, 0.6F);
            ctx.damage(t, DAMAGE, stats, ctx.caster(), Element.EARTH);
            boolean boss = Statuses.isBossLike(t);
            t.setDeltaMovement(t.getDeltaMovement().add(0, boss ? 0.15 : 0.6, 0));
            t.hurtMarked = true;
            Statuses.apply(t, Statuses.Kind.ROOTED, 1, boss ? 10 : 20, ctx.caster(), 1F);
        }

        private static void clap(ServerLevel l, @Nullable LivingEntity c, Vec3 at) {
            Vec3 from = c != null ? c.position().add(0, c.getBbHeight() * 0.7, 0) : at;
            LjUtil.sound(l, from, SoundEvents.PLAYER_ATTACK_STRONG, 0.9F, 1.8F);
            LjUtil.sound(l, at, SoundEvents.ROOTED_DIRT_BREAK, 1.2F, 0.6F);
            LjUtil.sound(l, at, SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.8F);
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 6, 0.2, 0.2, 0.2, 0.1);
        }
    }

    private AlchemyEffects() {}
}
