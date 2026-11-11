package com.renanpontes.isekaimahou.feature.origins_b.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.origins_b.ObFx;
import com.renanpontes.isekaimahou.feature.origins_b.ObRegistries;
import com.renanpontes.isekaimahou.feature.origins_b.ObUtil;
import com.renanpontes.isekaimahou.feature.origins_b.entity.SummonedDemonEntity;
import com.renanpontes.isekaimahou.feature.origins_b.entity.SummonedImpEntity;
import com.renanpontes.isekaimahou.feature.origins_b.item.ShadowBladeItem;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de Origem do Warlock: PACTO (invocações demoníacas, arma conjurada, contrato e asas). */
public final class PactGlyphs {

    public static List<EffectGlyph> all() {
        return List.of(new SummonImp(), new ShadowWeapon(), new DeathContract(), new DemonWings(), new SummonDemon());
    }

    /** Onde a invocação aparece: perto do alvo (se for inimigo), na frente do conjurador (se for ele) ou no bloco atingido. */
    static Vec3 summonSpot(SpellContext ctx, Entity target) {
        LivingEntity caster = ctx.caster();
        if (target == null || target == caster) {
            Vec3 fwd = caster.getLookAngle().multiply(1, 0, 1);
            if (fwd.lengthSqr() < 1.0E-4) fwd = new Vec3(0, 0, 1);
            return caster.position().add(fwd.normalize().scale(1.8));
        }
        Vec3 away = caster.position().subtract(target.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
        return target.position().add(away.normalize().scale(target.getBbWidth() + 1.2));
    }

    // =====================================================================================
    //  T1 — Invocar Diabrete
    // =====================================================================================
    /** Invoca 1 Diabrete (+1 por Dividir) por 60s. Até 3 por dono; os mais antigos voltam ao inferno. */
    public static class SummonImp extends EffectGlyph {
        public SummonImp() {
            super("summon_imp", 18);
            element(Element.ARCANE);
            origin(MagicPath.WARLOCK);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (ctx.caster() == null) return;
            summon(ctx, summonSpot(ctx, target), target != ctx.caster() && target instanceof LivingEntity le ? le : null, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (ctx.caster() == null) return;
            summon(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), null, stats);
        }

        private void summon(SpellContext ctx, Vec3 at, LivingEntity foe, SpellStats stats) {
            LivingEntity owner = ctx.caster();
            if (!ObUtil.oncePerTick(ctx, id().getPath())) return;
            ServerLevel level = ctx.level();
            EntityType<SummonedImpEntity> type = ObRegistries.SUMMONED_IMP.get();
            int count = ObUtil.makeRoom(level, SummonedImpEntity.class, owner.getUUID(), owner.position(), 3, Math.min(3, stats.count()));
            for (int i = 0; i < count; i++) {
                SummonedImpEntity imp = type.create(level);
                if (imp == null) return;
                Vec3 p = ObUtil.freeSpot(level, type, at.add(0, 1.0, 0), 0.8);
                imp.moveTo(p.x, p.y, p.z, owner.getYRot(), 0);
                imp.setup(owner, stats.duration(1200), ObUtil.power(ctx, stats));
                level.addFreshEntity(imp);
                if (foe != null && !ctx.isAlly(foe)) imp.setTarget(foe);
                ObFx.hellfireBurst(level, p, 0.8);
                ObFx.pactCircle(level, new Vec3(p.x, Math.floor(at.y) + 0.02, p.z), 0.8);
            }
            ObFx.sound(level, at, SoundEvents.BLAZE_SHOOT, 0.8F, 1.6F);
            ObFx.sound(level, at, SoundEvents.VEX_CHARGE, 1.0F, 0.8F);
        }
    }

    // =====================================================================================
    //  T2 — Arma Sombria
    // =====================================================================================
    /**
     * Conjura a Lâmina Sombria na mão de quem recebe (o conjurador com Si Mesmo, ou um jogador aliado).
     * Recastar renova a lâmina. Mobs recebem Força no lugar da espada.
     */
    public static class ShadowWeapon extends EffectGlyph {
        public ShadowWeapon() {
            super("shadow_weapon", 15);
            element(Element.ARCANE);
            origin(MagicPath.WARLOCK);
            tier(2);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            // Nunca arma inimigos (mesmo com Inverter)
            if (!(target instanceof LivingEntity le) || (target != ctx.caster() && !ctx.isAlly(target))) return;
            ServerLevel level = ctx.level();
            int ticks = stats.duration(900);
            float power = ObUtil.power(ctx, stats);
            if (le instanceof Player player) {
                if (!giveBlade(player, ShadowBladeItem.create(ticks, power, level.getGameTime()))) {
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.origins_b.blade_no_room")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
            } else {
                le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, Mth.clamp(Math.round(power) - 1, 0, 2)), ctx.caster());
            }
            Vec3 hand = le.position().add(0, le.getBbHeight() * 0.6, 0);
            level.sendParticles(ParticleTypes.SQUID_INK, hand.x, hand.y, hand.z, 14, 0.3, 0.3, 0.3, 0.04);
            level.sendParticles(ObFx.dust(ObFx.VIOLET, 1.3F), hand.x, hand.y, hand.z, 18, 0.35, 0.4, 0.35, 0);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, hand.x, hand.y, hand.z, 10, 0.3, 0.3, 0.3, 0.1);
            ObFx.sound(level, hand, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.9F, 0.8F);
            ObFx.sound(level, hand, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 0.7F);
        }

        /**
         * Remove lâminas antigas e coloca a nova na mão principal. O que estava na mão vai para um espaço livre
         * do inventário; sem espaço, a lâmina vai para onde couber (ou falha).
         */
        private static boolean giveBlade(Player player, ItemStack blade) {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (ShadowBladeItem.isBlade(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
            }
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty()) {
                int free = inv.getFreeSlot();
                if (free < 0 || free == inv.selected) return inv.add(blade);
                inv.setItem(free, held);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, blade);
            return true;
        }
    }

    // =====================================================================================
    //  T3 — Contrato de Morte
    // =====================================================================================
    /** Marca o alvo por 15s. Se ele morrer marcado (por qualquer causa), o conjurador recupera vida e mana. */
    public static class DeathContract extends EffectGlyph {
        public DeathContract() {
            super("death_contract", 14);
            element(Element.ARCANE);
            origin(MagicPath.WARLOCK);
            tier(3);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            // Só em inimigos: marcar a própria invocação/aliado (com "Só Aliados") viraria fonte infinita de mana
            if (!(target instanceof LivingEntity le) || target == ctx.caster() || ctx.casterId() == null || ctx.isAlly(target)) return;
            ServerLevel level = ctx.level();
            var data = ObUtil.data(le);
            data.putUUID("contract_owner", ctx.casterId());
            data.putFloat("contract_power", ObUtil.power(ctx, stats));
            le.addEffect(new MobEffectInstance(ObRegistries.DEATH_CONTRACT, stats.duration(300), 0, false, true, true), ctx.caster());
            Vec3 head = le.position().add(0, le.getBbHeight() + 0.4, 0);
            level.sendParticles(ObFx.dust(ObFx.CRIMSON, 1.4F), head.x, head.y, head.z, 20, 0.3, 0.2, 0.3, 0);
            level.sendParticles(ParticleTypes.ENCHANT, head.x, head.y, head.z, 20, 0.4, 0.4, 0.4, 0.4);
            ObFx.sound(level, head, SoundEvents.EVOKER_PREPARE_ATTACK, 0.8F, 0.6F);
            ObFx.sound(level, head, SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.5F);
        }
    }

    // =====================================================================================
    //  T4 — Asas Demoníacas
    // =====================================================================================
    /** Asas de demônio por 20s: voo livre (jogadores) com pouso seguro ao acabar. */
    public static class DemonWings extends EffectGlyph {
        public DemonWings() {
            super("demon_wings", 28);
            element(Element.ARCANE);
            origin(MagicPath.WARLOCK);
            tier(4);
            minCircle(4);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || (target != ctx.caster() && !ctx.isAlly(target))) return;
            le.addEffect(new MobEffectInstance(ObRegistries.DEMON_WINGS, stats.duration(400), 0, false, false, true), ctx.caster());
            if (le instanceof ServerPlayer sp && !sp.onGround()) sp.resetFallDistance();
        }
    }

    // =====================================================================================
    //  T5 — Invocar Demônio Maior (lendário)
    // =====================================================================================
    /** Abre um portal infernal e traz um Demônio Maior por 45s. Só 1 por dono. */
    public static class SummonDemon extends EffectGlyph {
        public SummonDemon() {
            super("summon_demon", 70);
            element(Element.ARCANE);
            origin(MagicPath.WARLOCK);
            legendary();
            minCircle(6);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (ctx.caster() == null) return;
            summon(ctx, summonSpot(ctx, target), target != ctx.caster() && target instanceof LivingEntity le ? le : null, stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (ctx.caster() == null) return;
            summon(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), null, stats);
        }

        private void summon(SpellContext ctx, Vec3 at, LivingEntity foe, SpellStats stats) {
            LivingEntity owner = ctx.caster();
            if (!ObUtil.oncePerTick(ctx, id().getPath())) return;
            ServerLevel level = ctx.level();
            EntityType<SummonedDemonEntity> type = ObRegistries.SUMMONED_DEMON.get();
            if (ObUtil.makeRoom(level, SummonedDemonEntity.class, owner.getUUID(), owner.position(), 1, 1) <= 0) return;
            SummonedDemonEntity demon = type.create(level);
            if (demon == null) return;
            Vec3 p = ObUtil.freeSpot(level, type, at, 1.5);
            float yaw = foe != null ? (float) (Mth.atan2(foe.getZ() - p.z, foe.getX() - p.x) * Mth.RAD_TO_DEG) - 90F : owner.getYRot();
            demon.moveTo(p.x, p.y, p.z, yaw, 0);
            demon.setYHeadRot(yaw);
            demon.setYBodyRot(yaw);
            demon.setup(owner, stats.duration(900), ObUtil.power(ctx, stats));
            level.addFreshEntity(demon);
            if (foe != null && !ctx.isAlly(foe)) demon.setTarget(foe);
            ObFx.pactCircle(level, p.add(0, 0.02, 0), 2.2);
            ObFx.hellfireBurst(level, p, 2.5);
            level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 1.0, p.z, 3, 0.8, 0.8, 0.8, 0);
            ObFx.sound(level, p, SoundEvents.END_PORTAL_SPAWN, 0.7F, 0.6F);
            ObFx.sound(level, p, SoundEvents.BLAZE_SHOOT, 1.2F, 0.5F);
        }
    }

    private PactGlyphs() {}
}
