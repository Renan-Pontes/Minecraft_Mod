package com.renanpontes.isekaimahou.feature.light_shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.light_shadow.ai.LsAi;
import com.renanpontes.isekaimahou.feature.light_shadow.effect.ShackledEffect;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Regras de jogo dos efeitos de Luz e Sombra que dependem de eventos (lado servidor). */
public final class LsEvents {
    /** Evita que o dano espalhado pelo Vínculo de Dor se espalhe de novo. */
    private static boolean spreading;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LsEvents::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(LsEvents::onDamagePost);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LsEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(LsEvents::onEffectExpired);
        NeoForge.EVENT_BUS.addListener(LsEvents::onEffectRemoved);
        // Remoção segura dos objetivos temporários de IA (fora do tick das entidades)
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> LsAi.flushPending());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> LsAi.clearPending());
    }

    /** Ofuscados e Amedrontados não escolhem alvo; possuídos não atacam o possessor nem os aliados dele. */
    private static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        LivingEntity next = event.getNewAboutToBeSetTarget();
        if (next == null || !(mob.level() instanceof ServerLevel sl)) return;
        if (mob.hasEffect(LsRegistries.HOLY_DAZZLE) || mob.hasEffect(LsRegistries.SHADOW_FEAR)) {
            event.setCanceled(true);
            return;
        }
        UUID owner = LsUtil.possessor(mob);
        if (owner != null && LsUtil.isAlly(sl, owner, next)) {
            event.setCanceled(true);
        }
    }

    /** Vínculo de Dor: parte do dano recebido pelo alvo marcado atinge os inimigos de quem conjurou perto dele. */
    private static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (spreading || event.getNewDamage() <= 0 || !(victim.level() instanceof ServerLevel sl)) return;
        MobEffectInstance link = victim.getEffect(LsRegistries.PAIN_LINK);
        if (link == null || !LsUtil.hasData(victim)) return;
        var d = LsUtil.data(victim);
        UUID owner = d.hasUUID("link_owner") ? d.getUUID("link_owner") : null;
        if (owner == null) return; // sem dono não há como saber quem são os inimigos
        float share = event.getNewDamage() * (0.5F + 0.15F * link.getAmplifier());
        List<LivingEntity> others = new ArrayList<>(LsUtil.enemiesOf(sl, owner, victim.position(), 6.0, victim));
        if (others.isEmpty()) return;
        LivingEntity caster = LsUtil.owner(sl, owner);
        spreading = true;
        try {
            int n = 0;
            for (LivingEntity e : others) {
                if (n++ >= 4) break;
                LsFx.line(sl, LsFx.mid(victim), LsFx.mid(e), LsFx.dust(LsFx.BLOOD, 0.9F), 4);
                sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
                LsUtil.spellHurt(sl, e, share, null, caster, Element.SHADOW);
            }
            LsFx.sound(sl, victim.position(), SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.3F);
        } finally {
            spreading = false;
        }
    }

    /** Ressurreição: a Graça salva da morte como um totem e a luz cura os aliados em volta. */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel sl) || !e.hasEffect(LsRegistries.RESURRECTION_GRACE)) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
        e.removeEffect(LsRegistries.RESURRECTION_GRACE);
        for (MobEffectInstance inst : new ArrayList<>(e.getActiveEffects())) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) e.removeEffect(inst.getEffect());
        }
        e.setHealth(Math.max(1.0F, e.getMaxHealth() * 0.5F));
        e.clearFire();
        e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
        e.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0));
        // Animação de totem (partículas e som do vanilla)
        sl.broadcastEntityEvent(e, (byte) 35);
        LsFx.skyRay(sl, e.position(), 14, 2.4F, LsFx.WHITE_GOLD);
        LsFx.ring(sl, e.position().add(0, 0.2, 0), 8, LsFx.dust(LsFx.GOLD, 1.4F), 64);
        LsFx.sound(sl, e.position(), SoundEvents.BEACON_ACTIVATE, 1.2F, 1.6F);
        // Onda de luz: cura aliados e queima profanos inimigos
        for (LivingEntity o : sl.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(8), x -> x != e && x.isAlive())) {
            if (LsUtil.isAlly(sl, e.getUUID(), o)) {
                o.heal(8.0F);
                o.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
                sl.sendParticles(ParticleTypes.HEART, o.getX(), o.getY() + o.getBbHeight() + 0.2, o.getZ(), 3, 0.3, 0.1, 0.3, 0);
            } else if (LsUtil.isUnholy(o)) {
                LsUtil.spellHurt(sl, o, 10.0F, null, e, Element.LIGHT);
                o.igniteForSeconds(5);
            }
        }
        if (e instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.resurrection_saved", sp.getDisplayName())
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null) cleanup(event.getEntity(), event.getEffectInstance().getEffect());
    }

    private static void onEffectRemoved(MobEffectEvent.Remove event) {
        cleanup(event.getEntity(), event.getEffect());
    }

    /** Desfaz a IA temporária e os dados quando o efeito acaba (ou é removido por leite, Purificar...). */
    private static void cleanup(LivingEntity e, Holder<MobEffect> effect) {
        if (!(e.level() instanceof ServerLevel sl) || effect == null) return;
        if (effect.is(LsRegistries.SHADOW_FEAR.getKey())) {
            if (e instanceof Mob mob) LsAi.clearFear(mob);
        } else if (effect.is(LsRegistries.SHADOW_CHAINS.getKey())) {
            ShackledEffect.clearAnchor(e);
            LsFx.sound(sl, e.position(), SoundEvents.CHAIN_BREAK, 1.0F, 0.7F);
            sl.sendParticles(LsFx.dust(LsFx.CHAIN_GREY, 1.2F), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 16, 0.5, 0.4, 0.5, 0);
        } else if (effect.is(LsRegistries.PAIN_LINK.getKey())) {
            if (LsUtil.hasData(e)) LsUtil.data(e).remove("link_owner");
        } else if (effect.is(LsRegistries.SHADOW_POSSESSION.getKey()) && e instanceof Mob mob) {
            endPossession(sl, mob);
        }
    }

    private static void endPossession(ServerLevel sl, Mob mob) {
        UUID owner = LsUtil.possessor(mob);
        LsAi.removePossessionGoals(mob);
        if (LsUtil.hasData(mob)) LsUtil.data(mob).remove("possessor");
        LsAi.clearTarget(mob);
        LsFx.shadowPuff(sl, mob.position(), mob.getBbHeight());
        LsFx.sound(sl, mob.position(), SoundEvents.VEX_DEATH, 0.8F, 0.6F);
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.possession_end", mob.getDisplayName())
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    private LsEvents() {}
}
