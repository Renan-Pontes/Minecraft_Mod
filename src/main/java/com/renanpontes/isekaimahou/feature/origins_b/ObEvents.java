package com.renanpontes.isekaimahou.feature.origins_b;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.origins_b.effect.LichFormEffect;
import com.renanpontes.isekaimahou.feature.origins_b.entity.ImpFireballEntity;
import com.renanpontes.isekaimahou.feature.origins_b.entity.ObSummonEntity;
import com.renanpontes.isekaimahou.feature.origins_b.item.ShadowBladeItem;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Regras de jogo do módulo que dependem de eventos:
 * <ul>
 *   <li>Escudo de Ossos (reflexo), Forma de Lich (redução, dreno, mortos-vivos ignoram);</li>
 *   <li>Contrato de Morte (recompensa na morte do marcado);</li>
 *   <li>Lâmina Sombria some ao ser largada, guardada fora do inventário ou na morte;</li>
 *   <li>Voo das Asas/Lich: pouso seguro quando acaba e restauração da velocidade de voo;</li>
 *   <li>Registro de "quem o dono atacou" para as invocações.</li>
 * </ul>
 */
public final class ObEvents {
    /** Marca (dados do módulo no jogador) de "pousar em segurança" depois do voo. */
    private static final String SAFE_LANDING = "safe_landing";
    private static boolean reflecting;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ObEvents::incomingDamage);
        NeoForge.EVENT_BUS.addListener(ObEvents::damagePost);
        // Por último e só se ninguém cancelou (ex.: ressurreição): a recompensa é paga na morte de verdade
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ObEvents::death);
        NeoForge.EVENT_BUS.addListener(ObEvents::changeTarget);
        NeoForge.EVENT_BUS.addListener(ObEvents::toss);
        NeoForge.EVENT_BUS.addListener(ObEvents::drops);
        NeoForge.EVENT_BUS.addListener(ObEvents::containerClose);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ObEvents::effectExpired);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ObEvents::effectRemoved);
        NeoForge.EVENT_BUS.addListener(ObEvents::playerTick);
        NeoForge.EVENT_BUS.addListener(ObEvents::loggedIn);
    }

    // =====================================================================================
    //  Dano
    // =====================================================================================
    private static void incomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource src = e.getSource();

        // Forma de Lich: corpo de morto-vivo encouraçado
        if (victim.hasEffect(ObRegistries.LICH_FORM) && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            e.setAmount(e.getAmount() * 0.75F);
        }

        // Escudo de Ossos: devolve parte do golpe corpo a corpo
        MobEffectInstance shield = victim.getEffect(ObRegistries.BONE_SHIELD);
        // Fora dos quadros de invulnerabilidade: golpes "engolidos" pelos i-frames não refletem
        if (shield != null && !reflecting && victim.invulnerableTime <= 10 && src.isDirect()
                && src.getEntity() instanceof LivingEntity attacker && attacker != victim
                && !src.is(DamageTypeTags.IS_PROJECTILE) && !src.is(DamageTypeTags.IS_EXPLOSION) && !src.is(DamageTypes.THORNS)
                && e.getAmount() > 0) {
            float back = e.getAmount() * (0.35F + 0.1F * shield.getAmplifier());
            reflecting = true;
            try {
                attacker.hurt(victim.damageSources().thorns(victim), back);
            } finally {
                reflecting = false;
            }
            if (victim.level() instanceof ServerLevel sl) {
                Vec3 mid = attacker.position().add(victim.position()).scale(0.5).add(0, victim.getBbHeight() * 0.6, 0);
                ObFx.boneBurst(sl, mid, 8, 0.2);
                sl.sendParticles(Vfx.crit(), attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(),
                        6, 0.2, 0.2, 0.2, 0.2);
                sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 0.7F);
            }
        }
    }

    private static void damagePost(LivingDamageEvent.Post e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide || e.getNewDamage() <= 0) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof LivingEntity attacker) || attacker == victim) return;

        // Para as invocações saberem quem o dono atacou (golpes das próprias invocações não contam: evita troca de alvo em cadeia)
        Entity direct = src.getDirectEntity();
        if (attacker instanceof Player && !(direct instanceof ObSummonEntity) && !(direct instanceof ImpFireballEntity)) {
            ObUtil.recordAttack(attacker, victim);
        }

        // Forma de Lich: tudo que o lich fere (golpes e magias) devolve vida
        if (attacker.hasEffect(ObRegistries.LICH_FORM) && attacker.isAlive() && !(direct instanceof ObSummonEntity)
                && !(direct instanceof ImpFireballEntity)) {
            attacker.heal(e.getNewDamage() * 0.25F);
            if (attacker.level() instanceof ServerLevel sl && attacker.getRandom().nextInt(2) == 0) {
                ObFx.soulStream(sl, victim.position().add(0, victim.getBbHeight() * 0.6, 0),
                        attacker.position().add(0, attacker.getBbHeight() * 0.6, 0), ObFx.SOUL_CYAN);
            }
            if (direct == attacker && victim.isAlive()) victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), attacker);
        }
    }

    // =====================================================================================
    //  Contrato de Morte
    // =====================================================================================
    private static void death(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !ObUtil.hasData(dead)) return;
        CompoundTag data = dead.getPersistentData().getCompound(ObUtil.DATA);
        if (!data.hasUUID("contract_owner") || !dead.hasEffect(ObRegistries.DEATH_CONTRACT)) return;
        UUID ownerId = data.getUUID("contract_owner");
        float power = data.contains("contract_power") ? data.getFloat("contract_power") : 1.0F;
        data.remove("contract_owner");

        LivingEntity owner = ObUtil.owner(level, ownerId);
        if (owner == null) {
            // Dono em outra dimensão: ainda recebe (jogador online)
            Player p = level.getServer().getPlayerList().getPlayer(ownerId);
            owner = p;
        }
        if (owner == null || !owner.isAlive()) return;

        float maxHp = dead.getMaxHealth();
        float heal = Math.min(16F, (3.0F + maxHp * 0.2F) * power);
        float mana = Math.min(60F, (15.0F + maxHp * 0.25F) * power);
        owner.heal(heal);
        int manaGained = 0;
        if (owner instanceof ServerPlayer sp) {
            MagicData md = MagicApi.get(sp);
            float before = md.mana();
            md.addMana(mana);
            manaGained = Math.round(md.mana() - before);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.origins_b.contract_fulfilled",
                    dead.getDisplayName(), Math.round(heal), manaGained).withStyle(ChatFormatting.DARK_RED), true);
        }

        Vec3 corpse = dead.position().add(0, dead.getBbHeight() * 0.6, 0);
        level.sendParticles(ObFx.dust(ObFx.CRIMSON, 1.8F), corpse.x, corpse.y, corpse.z, 30, 0.4, 0.5, 0.4, 0);
        level.sendParticles(Vfx.soul(), corpse.x, corpse.y, corpse.z, 10, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, corpse.x, corpse.y, corpse.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5F, 0.6F);
        if (owner.level() == level && owner.distanceToSqr(dead) < 48 * 48) {
            ObFx.soulStream(level, corpse, owner.position().add(0, owner.getBbHeight() * 0.6, 0), ObFx.CRIMSON);
        }
        owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    // =====================================================================================
    //  Forma de Lich: mortos-vivos comuns não atacam um lich
    // =====================================================================================
    private static void changeTarget(LivingChangeTargetEvent e) {
        LivingEntity mob = e.getEntity();
        LivingEntity target = e.getNewAboutToBeSetTarget();
        if (target == null || mob.level().isClientSide) return;
        if (target.hasEffect(ObRegistries.LICH_FORM) && ObUtil.isUndead(mob) && !ObUtil.isBoss(mob)
                && !(mob instanceof ObSummonEntity) && mob.getLastHurtByMob() != target) {
            e.setCanceled(true);
        }
    }

    // =====================================================================================
    //  Lâmina Sombria: só existe na mão/inventário de quem a conjurou
    // =====================================================================================
    /** Largou: cancelar o evento faz o item (já tirado do inventário) simplesmente sumir. */
    private static void toss(ItemTossEvent e) {
        ItemEntity item = e.getEntity();
        if (!ShadowBladeItem.isBlade(item.getItem())) return;
        e.setCanceled(true);
        if (e.getPlayer().level() instanceof ServerLevel sl) ShadowBladeItem.vanishFx(sl, item.getX(), item.getY() + 0.3, item.getZ());
    }

    private static void drops(LivingDropsEvent e) {
        e.getDrops().removeIf(item -> ShadowBladeItem.isBlade(item.getItem()));
    }

    /** Fechou um baú/fornalha/etc.: lâminas guardadas lá dentro se desfazem. */
    private static void containerClose(PlayerContainerEvent.Close e) {
        Player player = e.getEntity();
        if (player.level().isClientSide) return;
        AbstractContainerMenu menu = e.getContainer();
        boolean vanished = false;
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (ShadowBladeItem.isBlade(stack)) {
                slot.set(ItemStack.EMPTY);
                vanished = true;
            }
        }
        if (ShadowBladeItem.isBlade(menu.getCarried())) {
            menu.setCarried(ItemStack.EMPTY);
            vanished = true;
        }
        if (vanished && player.level() instanceof ServerLevel sl) {
            ShadowBladeItem.vanishFx(sl, player.getX(), player.getY() + 1.0, player.getZ());
            player.displayClientMessage(Component.translatable("message.isekai_mahou.origins_b.blade_vanished").withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    // =====================================================================================
    //  Voo (Asas Demoníacas / Forma de Lich): pouso seguro e velocidade de voo
    // =====================================================================================
    private static void effectExpired(MobEffectEvent.Expired e) {
        if (e.getEffectInstance() != null) onFlightEffectEnd(e.getEntity(), e.getEffectInstance().getEffect());
    }

    private static void effectRemoved(MobEffectEvent.Remove e) {
        // removeEffect de um efeito ausente também dispara o evento (sem instância): nada a encerrar
        if (e.getEffectInstance() != null) onFlightEffectEnd(e.getEntity(), e.getEffect());
    }

    /**
     * Fim do voo. ATENÇÃO: estes eventos rodam DENTRO da iteração do mapa de efeitos (tickEffects, leite, totem,
     * /effect clear); adicionar um efeito aqui daria ConcurrentModificationException. Por isso o pouso seguro só
     * é marcado aqui e a Queda Lenta é aplicada no tick do jogador ({@link #landingTick}).
     */
    private static void onFlightEffectEnd(LivingEntity entity, Holder<MobEffect> effect) {
        if (entity.level().isClientSide) return;
        boolean wings = effect.is(ObRegistries.DEMON_WINGS.getKey());
        boolean lich = effect.is(ObRegistries.LICH_FORM.getKey());
        if (!wings && !lich) return;
        if (entity instanceof ServerPlayer sp) {
            if (lich) restoreFlySpeed(sp);
            // Se ainda tiver o outro efeito de voo, continua voando
            boolean stillFlies = (wings && sp.hasEffect(ObRegistries.LICH_FORM)) || (lich && sp.hasEffect(ObRegistries.DEMON_WINGS));
            if (!stillFlies && !sp.onGround() && !sp.isCreative() && !sp.isSpectator()) {
                ObUtil.data(sp).putBoolean(SAFE_LANDING, true);
                sp.resetFallDistance();
            }
        }
        if (entity.level() instanceof ServerLevel sl) {
            if (wings) ObFx.hellfireBurst(sl, entity.position().add(0, entity.getBbHeight() * 0.6, 0), 0.6);
            else ObFx.soulBurst(sl, entity.position(), entity.getBbHeight());
            sl.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    wings ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
        }
    }

    /** Devolve a velocidade de voo padrão se ainda estiver a do lich (não mexe em valores de outros mods). */
    private static void restoreFlySpeed(ServerPlayer sp) {
        if (Math.abs(sp.getAbilities().getFlyingSpeed() - LichFormEffect.LICH_FLY_SPEED) < 1.0E-4) {
            sp.getAbilities().setFlyingSpeed(LichFormEffect.DEFAULT_FLY_SPEED);
            sp.onUpdateAbilities();
        }
    }

    private static void playerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (ObUtil.hasData(sp)) {
            CompoundTag d = sp.getPersistentData().getCompound(ObUtil.DATA);
            if (d.getBoolean(SAFE_LANDING)) landingTick(sp, d);
        }
        // Rede de segurança (morte, troca de dimensão, efeito apagado por comando): sem efeito, sem voo
        if (sp.tickCount % 20 == 0) cleanFlight(sp);
    }

    /**
     * Pouso seguro depois que as asas/forma de lich acabam no ar: mantém a Queda Lenta até tocar o chão (ou a água),
     * por mais alto que esteja. Fora da iteração de efeitos, então é seguro adicionar o efeito aqui.
     */
    private static void landingTick(ServerPlayer sp, CompoundTag d) {
        if (sp.onGround() || sp.isInWater() || sp.isPassenger() || sp.isFallFlying() || sp.mayFly() || !sp.isAlive()) {
            d.remove(SAFE_LANDING);
            return;
        }
        MobEffectInstance slow = sp.getEffect(MobEffects.SLOW_FALLING);
        if (slow == null || (!slow.isInfiniteDuration() && slow.getDuration() < 20)) {
            sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
        }
        sp.resetFallDistance();
    }

    private static void loggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) cleanFlight(sp);
    }

    /**
     * Os modificadores de efeito são salvos junto dos atributos; se o efeito sumir sem removê-los
     * (save editado, efeito apagado por outro mod), o voo ficaria preso. Aqui o voo só existe com o efeito.
     */
    private static void cleanFlight(ServerPlayer sp) {
        boolean lich = sp.hasEffect(ObRegistries.LICH_FORM);
        if (!lich) restoreFlySpeed(sp);
        AttributeInstance flight = sp.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight == null) return;
        if (!lich && flight.hasModifier(ObRegistries.LICH_FLIGHT_ID)) flight.removeModifier(ObRegistries.LICH_FLIGHT_ID);
        if (!sp.hasEffect(ObRegistries.DEMON_WINGS) && flight.hasModifier(ObRegistries.WINGS_FLIGHT_ID)) {
            flight.removeModifier(ObRegistries.WINGS_FLIGHT_ID);
        }
    }

    private ObEvents() {}
}
