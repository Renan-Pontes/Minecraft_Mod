package com.renanpontes.isekaimahou.feature.arcane.glyph;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneRegistries;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.arcane.StatusAuras;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Efeitos arcanos de controle: Atordoar, Marcar, Silêncio, Desarmar, Encolher, Crescer, Acelerar/Desacelerar Tempo. */
public final class ControlEffects {

    // =====================================================================================
    //  Atordoar (C2) — Inverter: liberta (remove atordoamento/lentidão)
    // =====================================================================================
    public static class StunEffect extends EffectGlyph {
        public StunEffect() {
            super("stun", 14);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            Vec3 top = le.position().add(0, le.getBbHeight() + 0.3, 0);
            if (stats.inverted) {
                boolean any = le.removeEffect(CoreRegistries.STUNNED) | le.removeEffect(CoreRegistries.ROOTED)
                        | le.removeEffect(CoreRegistries.FROZEN) | le.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                if (any) {
                    level.sendParticles(Vfx.happy(), top.x, top.y - 0.4, top.z, 10, 0.3, 0.3, 0.3, 0);
                    ArcaneUtil.sound(level, top, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.8F);
                }
                return;
            }
            int ticks = stats.duration(50);
            if (ArcaneUtil.isBoss(le)) ticks /= 3;
            ctx.status(le, CoreRegistries.STUNNED, ticks, 0, stats);
            if (le instanceof Mob m) m.getNavigation().stop();
            if (le instanceof ServerPlayer p) SpellCaster.interrupt(p);
            StatusAuras.track(le, StatusAuras.Kind.STUN, ticks + 20);
            level.sendParticles(ArcaneUtil.dust(0xFFF27A, 1.2F), top.x, top.y, top.z, 14, 0.35, 0.15, 0.35, 0);
            level.sendParticles(Vfx.crit(), top.x, top.y - 0.3, top.z, 10, 0.3, 0.2, 0.3, 0.1);
            ArcaneUtil.sound(level, top, SoundEvents.ANVIL_LAND, 0.35F, 1.8F);
        }
    }

    // =====================================================================================
    //  Marcar (C2): Exposto + brilho — Inverter: remove a marca
    // =====================================================================================
    public static class MarkTargetEffect extends EffectGlyph {
        public MarkTargetEffect() {
            super("mark_target", 10);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            Vec3 c = ArcaneUtil.center(le);
            if (stats.inverted) {
                if (le.removeEffect(CoreRegistries.EXPOSED)) {
                    le.removeEffect(MobEffects.GLOWING);
                    level.sendParticles(Vfx.smoke(), c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.01);
                }
                return;
            }
            int ticks = stats.duration(160);
            ctx.status(le, CoreRegistries.EXPOSED, 160, Mth.clamp(stats.amplify, 0, 2), stats);
            le.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), ctx.caster());
            StatusAuras.track(le, StatusAuras.Kind.MARK, ticks + 20);
            double r = le.getBbWidth() * 0.5 + 0.5;
            ArcaneUtil.ring(level, c, r, ArcaneUtil.dust(0xFF4040, 1.0F), 18);
            level.sendParticles(ArcaneUtil.dust(0xFF4040, 1.0F), c.x, c.y, c.z, 6, 0.05, le.getBbHeight() * 0.4, 0.05, 0);
            ArcaneUtil.sound(level, c, SoundEvents.ARROW_HIT_PLAYER, 0.7F, 0.6F);
        }
    }

    // =====================================================================================
    //  Silêncio (C3) — Inverter: remove o silêncio
    // =====================================================================================
    public static class SilenceEffect extends EffectGlyph {
        public SilenceEffect() {
            super("silence", 15);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            silence(ctx, le, stats, 100);
        }

        /** Usado também por Contramagia e Roubar Mana. */
        static void silence(SpellContext ctx, LivingEntity le, SpellStats stats, int baseTicks) {
            ServerLevel level = ctx.level();
            Vec3 top = le.position().add(0, le.getBbHeight() + 0.25, 0);
            if (stats.inverted) {
                if (le.removeEffect(CoreRegistries.SILENCED)) {
                    level.sendParticles(ParticleTypes.NOTE, top.x, top.y, top.z, 4, 0.3, 0.1, 0.3, 1.0);
                    ArcaneUtil.sound(level, top, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
                }
                return;
            }
            int ticks = stats.duration(baseTicks);
            if (ArcaneUtil.isBoss(le)) ticks /= 2;
            ctx.status(le, CoreRegistries.SILENCED, ticks, 0, stats);
            if (le instanceof ServerPlayer p) SpellCaster.interrupt(p);
            StatusAuras.track(le, StatusAuras.Kind.SILENCE, ticks + 20);
            level.sendParticles(ArcaneUtil.dust(0x9A9AB0, 1.3F), top.x, top.y, top.z, 12, 0.3, 0.1, 0.3, 0);
            level.sendParticles(Vfx.smoke(), le.getX(), le.getEyeY(), le.getZ(), 8, 0.2, 0.1, 0.2, 0.02);
            ArcaneUtil.sound(level, top, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.5F);
        }
    }

    // =====================================================================================
    //  Desarmar (C3)
    // =====================================================================================
    public static class DisarmEffect extends EffectGlyph {
        public DisarmEffect() {
            super("disarm", 14);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || target == ctx.caster()) return;
            ServerLevel level = ctx.level();
            Vec3 hand = new Vec3(le.getX(), le.getEyeY() - 0.4, le.getZ());
            if (le instanceof Player p) {
                // Jogadores não perdem o item (seria fácil cair na lava): ficam sem poder usá-lo um tempo
                ItemStack held = p.getMainHandItem();
                if (held.isEmpty()) return;
                p.getCooldowns().addCooldown(held.getItem(), stats.duration(60));
                p.stopUsingItem();
                // A recarga do item não impede o golpe corpo a corpo: Fraqueza deixa a arma "frouxa" no mesmo tempo
                ctx.status(p, MobEffects.WEAKNESS, 60, 0, stats);
                ArcaneUtil.actionbar(p, Component.translatable("message.isekai_mahou.arcane_disarmed"));
                fx(ctx, level, hand);
                return;
            }
            if (!(le instanceof Mob m)) return;
            ItemStack held = m.getMainHandItem();
            if (held.isEmpty()) return;
            if (ArcaneUtil.isBoss(m)) {
                ArcaneUtil.resisted(level, m, ctx.caster());
                return;
            }
            ItemStack drop = held.copy();
            m.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            // Armas "de nascença" do mob saem gastas, como no drop normal; itens que ele pegou do chão saem intactos
            if (handDropChance(m) <= 1.0F && drop.isDamageableItem()) {
                int max = drop.getMaxDamage();
                drop.setDamageValue(max - level.random.nextInt(1 + level.random.nextInt(Math.max(max - 3, 1))));
            }
            ItemEntity item = new ItemEntity(level, hand.x, hand.y, hand.z, drop);
            Vec3 away = ctx.caster() != null ? le.position().subtract(ctx.caster().position()).multiply(1, 0, 1) : Vec3.ZERO;
            if (away.lengthSqr() > 1.0E-4) away = away.normalize().scale(0.25);
            item.setDeltaMovement(away.x, 0.3, away.z);
            item.setPickUpDelay(40);
            level.addFreshEntity(item);
            ctx.status(m, MobEffects.WEAKNESS, 100, 0, stats);
            fx(ctx, level, hand);
        }

        /** Chance de drop da mão principal (protegida no Mob; lida do NBT salvo). */
        private static float handDropChance(Mob m) {
            try {
                CompoundTag tag = m.saveWithoutId(new CompoundTag());
                var list = tag.getList("HandDropChances", Tag.TAG_FLOAT);
                return list.isEmpty() ? 0.085F : list.getFloat(0);
            } catch (RuntimeException ex) {
                return 0.085F;
            }
        }

        private static void fx(SpellContext ctx, ServerLevel level, Vec3 at) {
            level.sendParticles(Vfx.crit(), at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.25);
            level.sendParticles(ArcaneUtil.dust(ctx, 1.0F), at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0);
            ArcaneUtil.sound(level, at, SoundEvents.SHIELD_BREAK, 0.8F, 1.3F);
        }
    }

    // =====================================================================================
    //  Encolher / Crescer (C3) — Inverter troca um pelo outro
    // =====================================================================================

    /** Aplica Encolhido ou Agigantado; se o alvo estiver com o efeito oposto, só o desfaz. */
    static void applyScale(SpellContext ctx, LivingEntity le, SpellStats stats, boolean grow) {
        ServerLevel level = ctx.level();
        Holder<MobEffect> effect = grow ? ArcaneRegistries.GROWTH : ArcaneRegistries.SHRINK;
        Holder<MobEffect> opposite = grow ? ArcaneRegistries.SHRINK : ArcaneRegistries.GROWTH;
        Vec3 c = ArcaneUtil.center(le);
        if (le.removeEffect(opposite)) {
            level.sendParticles(Vfx.poof(), c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.02);
            ArcaneUtil.sound(level, c, SoundEvents.PUFFER_FISH_BLOW_OUT, 1.0F, 1.0F);
            return;
        }
        int ticks = grow ? 400 : 300;
        if (!grow && ArcaneUtil.isBoss(le)) ticks /= 3;
        ctx.status(le, effect, ticks, Mth.clamp(stats.amplify, 0, 1), stats);
        ArcaneUtil.sphere(level, c, le.getBbWidth() * 0.6 + 0.6, ArcaneUtil.dust(ctx, 1.0F), 26);
        level.sendParticles(Vfx.poof(), c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.02);
        ArcaneUtil.sound(level, c, grow ? SoundEvents.PUFFER_FISH_BLOW_UP : SoundEvents.PUFFER_FISH_BLOW_OUT, 1.0F, grow ? 0.7F : 1.5F);
    }

    public static class ShrinkEffect extends EffectGlyph {
        public ShrinkEffect() {
            super("shrink", 15);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target instanceof LivingEntity le) applyScale(ctx, le, stats, stats.inverted);
        }
    }

    public static class GrowEffect extends EffectGlyph {
        public GrowEffect() {
            super("grow", 18);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target instanceof LivingEntity le) applyScale(ctx, le, stats, !stats.inverted);
        }
    }

    // =====================================================================================
    //  Acelerar / Desacelerar Tempo (C5) — Inverter troca um pelo outro
    // =====================================================================================

    static void applyTime(SpellContext ctx, LivingEntity le, SpellStats stats, boolean haste) {
        ServerLevel level = ctx.level();
        Vec3 c = ArcaneUtil.center(le);
        int amp = Mth.clamp(stats.amplify, 0, 2);
        if (haste) {
            int ticks = stats.duration(300);
            le.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            le.removeEffect(MobEffects.DIG_SLOWDOWN);
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1 + amp), ctx.caster());
            le.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ticks, 1 + amp), ctx.caster());
            // Ponteiros de relógio girando para cima
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                double y = le.getY() + i * le.getBbHeight() / 12.0;
                level.sendParticles(Vfx.endRod(), le.getX() + Math.cos(a) * 0.7, y, le.getZ() + Math.sin(a) * 0.7, 1, 0, 0.02, 0, 0.01);
            }
            ArcaneUtil.ring(level, le.position().add(0, 0.1, 0), 0.9, ArcaneUtil.dust(0xFFE27A, 1.0F), 16);
            ArcaneUtil.sound(level, c, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.9F);
        } else {
            int ticks = stats.duration(120);
            if (ArcaneUtil.isBoss(le)) ticks /= 2;
            le.removeEffect(MobEffects.MOVEMENT_SPEED);
            le.removeEffect(MobEffects.DIG_SPEED);
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 2 + amp), ctx.caster());
            le.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 1 + amp), ctx.caster());
            // O tempo em volta também "engrossa": projéteis próximos quase param
            for (Projectile p : level.getEntitiesOfClass(Projectile.class, le.getBoundingBox().inflate(4.0),
                    p -> p.getOwner() != ctx.caster())) {
                p.setDeltaMovement(p.getDeltaMovement().scale(0.25));
                p.hasImpulse = true;
            }
            level.sendParticles(Vfx.reversePortal(), c.x, c.y, c.z, 30, 0.5, 0.6, 0.5, 0.01);
            ArcaneUtil.ring(level, le.position().add(0, 0.1, 0), 1.0, ArcaneUtil.dust(0x5A6BC8, 1.2F), 18);
            ArcaneUtil.sound(level, c, SoundEvents.BEACON_DEACTIVATE, 0.8F, 0.6F);
        }
    }

    public static class HasteTimeEffect extends EffectGlyph {
        public HasteTimeEffect() {
            super("haste_time", 22);
            element(Element.ARCANE);
            tier(4);
            minCircle(2);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target instanceof LivingEntity le) applyTime(ctx, le, stats, !stats.inverted);
        }

        /** Com Sensível: acelera o tempo do bloco (plantas crescem, cobre oxida...). */
        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (stats.inverted) return;
            ServerLevel level = ctx.level();
            BlockPos center = hit.getBlockPos();
            int ticks = Math.round(10 * stats.power());
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
                BlockState s = level.getBlockState(p);
                if (!s.isRandomlyTicking()) continue;
                BlockPos ip = p.immutable();
                for (int i = 0; i < ticks; i++) {
                    BlockState now = level.getBlockState(ip);
                    if (!now.isRandomlyTicking()) break;
                    now.randomTick(level, ip, level.random);
                }
                level.sendParticles(Vfx.happy(), ip.getX() + 0.5, ip.getY() + 0.7, ip.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0);
                n++;
            }
            if (n > 0) ArcaneUtil.sound(level, hit.getLocation(), SoundEvents.BEACON_POWER_SELECT, 0.6F, 2.0F);
        }
    }

    public static class SlowTimeEffect extends EffectGlyph {
        public SlowTimeEffect() {
            super("slow_time", 22);
            element(Element.ARCANE);
            tier(4);
            minCircle(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target instanceof LivingEntity le) applyTime(ctx, le, stats, stats.inverted);
        }

        /** Com Sensível no chão: desacelera os projéteis em volta do impacto. */
        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (stats.inverted) return;
            ServerLevel level = ctx.level();
            Vec3 at = hit.getLocation();
            float r = stats.radius(4);
            for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(at, at).inflate(r), p -> p.getOwner() != ctx.caster())) {
                p.setDeltaMovement(p.getDeltaMovement().scale(0.25));
                p.hasImpulse = true;
            }
            level.sendParticles(Vfx.reversePortal(), at.x, at.y + 0.5, at.z, 30, r * 0.5, 0.5, r * 0.5, 0.01);
            ArcaneUtil.sound(level, at, SoundEvents.BEACON_DEACTIVATE, 0.7F, 0.6F);
        }
    }

    private ControlEffects() {}
}
