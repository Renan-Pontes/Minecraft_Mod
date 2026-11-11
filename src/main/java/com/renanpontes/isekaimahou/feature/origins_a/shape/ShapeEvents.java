package com.renanpontes.isekaimahou.feature.origins_a.shape;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.origins_a.OaData;
import com.renanpontes.isekaimahou.feature.origins_a.OaNetwork;
import com.renanpontes.isekaimahou.feature.origins_a.OaRegistries;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Metamorfose no servidor: troca/saída de forma (efeitos + sincronização do visual), passivas que dependem
 * de eventos (respiração, queda, imunidade a fogo) e os golpes especiais de cada forma.
 */
public final class ShapeEvents {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ShapeEvents::onAdded);
        NeoForge.EVENT_BUS.addListener(ShapeEvents::onRemove);
        NeoForge.EVENT_BUS.addListener(ShapeEvents::onExpired);
        NeoForge.EVENT_BUS.addListener(ShapeEvents::onStartTracking);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> syncSelf(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> syncSelf(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> syncSelf(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(ShapeEvents::onBreathe);
        NeoForge.EVENT_BUS.addListener(ShapeEvents::onFall);
        NeoForge.EVENT_BUS.addListener(ShapeEvents::onDamage);
    }

    // =====================================================================================
    //  Forma atual
    // =====================================================================================
    public static Holder<MobEffect> effect(Shape s) {
        return OaRegistries.SHAPE_EFFECTS.get(s);
    }

    /** Forma atual da entidade (null = forma normal). */
    public static Shape current(LivingEntity e) {
        for (Shape s : Shape.values()) {
            if (e.hasEffect(effect(s))) return s;
        }
        return null;
    }

    private static Shape shapeOf(MobEffectInstance inst) {
        return inst != null && inst.getEffect().value() instanceof ShapeshiftEffect se ? se.shape : null;
    }

    /** Resultado de {@link #toggle}. */
    public enum Toggle { ENTERED, LEFT, NO_ROOM }

    /**
     * Assume a forma (ou sai dela, se já estiver nela: conjurar de novo desfaz a Metamorfose).
     * Formas maiores que o jogador (Urso, Fera) precisam de espaço, senão ele ficaria entalado no teto.
     */
    public static Toggle toggle(ServerPlayer player, Shape shape, Element element, int duration) {
        if (player.hasEffect(effect(shape))) {
            player.removeEffect(effect(shape));
            return Toggle.LEFT;
        }
        if (!hasRoom(player, shape)) return Toggle.NO_ROOM;
        for (Shape s : Shape.values()) {
            if (s != shape) player.removeEffect(effect(s));
        }
        OaData.of(player).setFormElement(element);
        player.addEffect(new MobEffectInstance(effect(shape), duration, 0, false, false, true));
        return Toggle.ENTERED;
    }

    /** Cabe o corpo da forma (tamanho padrão do jogador × escala da forma) onde ele está? */
    private static boolean hasRoom(ServerPlayer player, Shape shape) {
        double grow = switch (shape) {
            case BEAR -> 1.15;
            case BEAST -> 1.3;
            default -> 0.0;
        };
        if (grow <= 1.0) return true;
        double half = 0.3 * grow, h = 1.8 * grow;
        AABB box = new AABB(player.getX() - half, player.getY(), player.getZ() - half,
                player.getX() + half, player.getY() + h, player.getZ() + half).deflate(1.0E-3);
        return player.level().noCollision(player, box);
    }

    // =====================================================================================
    //  Sincronização do visual
    // =====================================================================================
    private static void broadcast(ServerPlayer player, Shape shape) {
        Element el = OaData.of(player).formElement();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new OaNetwork.FormSync(player.getId(), shape == null ? 0 : shape.netId(), el.ordinal()));
    }

    private static void syncSelf(Player p) {
        if (p instanceof ServerPlayer sp) broadcast(sp, current(sp));
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            // Sempre envia (também a forma humana): o jogador renascido mantém o id de entidade e quem o via
            // ainda guardaria a forma antiga no cliente
            Shape s = current(target);
            PacketDistributor.sendToPlayer(viewer, new OaNetwork.FormSync(target.getId(), s == null ? 0 : s.netId(),
                    OaData.of(target).formElement().ordinal()));
        }
    }

    private static void onAdded(MobEffectEvent.Added event) {
        Shape s = shapeOf(event.getEffectInstance());
        if (s == null || !(event.getEntity() instanceof ServerPlayer sp)) return;
        broadcast(sp, s);
        if (event.getOldEffectInstance() == null) transformFx(sp, s, true);
    }

    private static void onRemove(MobEffectEvent.Remove event) {
        Shape s = shapeOf(event.getEffectInstance());
        if (s != null) ended(event.getEntity(), s);
    }

    private static void onExpired(MobEffectEvent.Expired event) {
        Shape s = shapeOf(event.getEffectInstance());
        if (s != null) ended(event.getEntity(), s);
    }

    private static void ended(LivingEntity entity, Shape s) {
        if (!(entity instanceof ServerPlayer sp)) return;
        broadcast(sp, null);
        transformFx(sp, s, false);
        // Remover outros efeitos aqui mexeria no mapa de efeitos durante a iteração do vanilla: fica para o próximo tick.
        // Se o jogador já trocou de forma, a nova forma recoloca os efeitos dela no tick seguinte.
        SpellScheduler.schedule(1, () -> {
            if (sp.isAlive()) ShapeshiftEffect.clearHidden(sp);
        });
    }

    /** Fumaça + pó do elemento + som do animal ao entrar/sair da forma. */
    private static void transformFx(ServerPlayer sp, Shape s, boolean entering) {
        ServerLevel level = sp.serverLevel();
        Element el = OaData.of(sp).formElement();
        double y = sp.getY() + 0.6;
        level.sendParticles(ParticleTypes.POOF, sp.getX(), y, sp.getZ(), 24, 0.4, 0.5, 0.4, 0.04);
        level.sendParticles(ElementFx.dust(el, 1.3F), sp.getX(), y, sp.getZ(), 20, 0.5, 0.6, 0.5, 0.0);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sp.getX(), y, sp.getZ(), 8, 0.5, 0.5, 0.5, 0.0);
        SoundEvent sound = entering ? switch (s) {
            case WOLF -> SoundEvents.WOLF_HOWL;
            case BEAR -> SoundEvents.POLAR_BEAR_WARNING;
            case FALCON -> SoundEvents.PARROT_AMBIENT;
            case DOLPHIN -> SoundEvents.DOLPHIN_PLAY;
            case SPIDER -> SoundEvents.SPIDER_AMBIENT;
            case BEAST -> SoundEvents.RAVAGER_ROAR;
        } : SoundEvents.ILLUSIONER_MIRROR_MOVE;
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), sound, SoundSource.PLAYERS, 1.0F, entering ? 1.0F : 1.3F);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    // =====================================================================================
    //  Passivas
    // =====================================================================================
    private static void onBreathe(LivingBreatheEvent event) {
        LivingEntity e = event.getEntity();
        Shape s = current(e);
        if (s == Shape.DOLPHIN || (s == Shape.BEAST && e instanceof Player p && OaData.of(p).formElement().matches(Element.WATER))) {
            event.setCanBreathe(true);
        }
    }

    private static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && current(p) == Shape.BEAST && OaData.of(p).formElement().matches(Element.WIND)) {
            event.setDamageMultiplier(0.0F);
        }
    }

    private static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource src = event.getSource();
        // Fera de fogo: imune a fogo e lava
        if (target instanceof Player tp && src.is(DamageTypeTags.IS_FIRE) && current(tp) == Shape.BEAST
                && OaData.of(tp).formElement().matches(Element.FIRE)) {
            event.setCanceled(true);
            return;
        }
        // Golpes corpo a corpo de quem está transformado
        if (!(src.getEntity() instanceof Player attacker) || src.getDirectEntity() != attacker) return;
        if (attacker.level().isClientSide) return;
        Shape s = current(attacker);
        if (s == null) return;
        switch (s) {
            case SPIDER -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), attacker);
            case BEAR -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), attacker);
            case WOLF -> {
                // Mordida: alvos já feridos sangram mais
                if (target.getHealth() < target.getMaxHealth() * 0.5F) event.setAmount(event.getAmount() * 1.25F);
            }
            case BEAST -> beastHit(event, attacker, target, OaData.of(attacker).formElement());
            default -> {}
        }
    }

    private static void beastHit(LivingIncomingDamageEvent event, Player attacker, LivingEntity target, Element el) {
        if (el.matches(Element.FIRE)) target.igniteForSeconds(4.0F);
        if (el.matches(Element.WATER)) target.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 120, 0), attacker);
        if (el.matches(Element.EARTH)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), attacker);
            target.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, 15, 0), attacker);
        }
        if (el.matches(Element.WIND)) {
            target.push(0, 0.45, 0);
            target.hurtMarked = true;
        }
        if (el.matches(Element.LIGHT)) {
            if (target.getType().is(EntityTypeTags.UNDEAD)) event.setAmount(event.getAmount() * 1.5F);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0), attacker);
        }
        if (el.matches(Element.SHADOW)) {
            target.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 80, 0), attacker);
            attacker.heal(event.getAmount() * 0.2F);
        }
        // Fera arcana (magia sem elemento): garras que expõem o alvo à magia
        if (el == Element.ARCANE) target.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 80, 0), attacker);
        if (attacker.level() instanceof ServerLevel level) {
            ElementFx.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0), el, 10, 0.3);
        }
    }

    private ShapeEvents() {}
}
