package com.renanpontes.isekaimahou.feature.class_gambler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.GumTetherEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.IdleDeathDomainEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.JackpotService;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseRanks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Regras do pacote de técnicas de anime do Apostador fora dos glifos:
 * <ul>
 *   <li><b>Disfarce</b> (Textura Surpresa): monstros não miram quem está disfarçado (só de perto ou se ele os feriu); o
 *   primeiro golpe/magia saindo do disfarce é uma SURPRESA (+60%) e encerra o disfarce;</li>
 *   <li><b>Jackpot Imortal</b> (Morte Ociosa): um golpe fatal não mata — a vida volta cheia (exceto /kill e o vazio);</li>
 *   <li>catch-up dos glifos dos ranks da Casa já concluídos (saves de antes do pacote);</li>
 *   <li>limpeza ao sair/parar o servidor.</li>
 * </ul>
 */
public final class AnimeTricks {
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));
    private static final Map<UUID, Long> IMMORTAL_FX = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LivingChangeTargetEvent.class, AnimeTricks::onTarget);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LivingIncomingDamageEvent.class, AnimeTricks::onSurprise);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingDeathEvent.class, AnimeTricks::onImmortalDeath);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) catchUp(sp);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            UUID id = e.getEntity().getUUID();
            GumTetherEntity.forget(id);
            IdleDeathDomainEntity.forget(id);
            IMMORTAL_FX.remove(id);
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            GumTetherEntity.clearAll();
            IdleDeathDomainEntity.clearAll();
            IMMORTAL_FX.clear();
        });
    }

    /** Chefe DE VERDADE (tag {@code c:bosses}, chefe de provação ou de arena) — não só "muita vida". */
    public static boolean isBoss(@Nullable LivingEntity e) {
        if (e == null || e instanceof Player) return false;
        return e.getType().is(BOSSES) || BossKit.isTrialBoss(e) || BossArena.isArenaBoss(e);
    }

    /** Monstros por perto que miravam {@code who} perdem o alvo (Disfarce). */
    public static void dropAggro(LivingEntity who, double radius) {
        if (!(who.level() instanceof ServerLevel level)) return;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(who.blockPosition()).inflate(radius), m -> m.getTarget() == who)) {
            if (isBoss(m)) continue;
            m.setTarget(null);
            m.getNavigation().stop();
        }
    }

    /** Glifos dos ranks da Casa já concluídos (em silêncio): o pacote novo chega a quem já tinha subido. */
    public static void catchUp(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.GAMBLER) return;
        int rank = HouseRanks.rank(d);
        for (int n = 1; n <= rank; n++) {
            HouseRanks.Rank r = HouseRanks.rank(n);
            if (r == null) continue;
            for (String g : r.glyphs()) {
                ResourceLocation id = IsekaiMahou.id(g);
                if (!d.knowsGlyph(id)) MagicApi.learnGlyphSilently(sp, id);
            }
        }
    }

    // =====================================================================================
    //  Disfarce
    // =====================================================================================
    private static void onTarget(LivingChangeTargetEvent e) {
        LivingEntity target = e.getNewAboutToBeSetTarget();
        if (target == null || !target.hasEffect(GbRegistries.DISGUISE)) return;
        LivingEntity mob = e.getEntity();
        if (isBoss(mob) || mob.getLastHurtByMob() == target) return;
        if (mob.distanceToSqr(target) > 2.5 * 2.5) e.setCanceled(true);
    }

    private static void onSurprise(LivingIncomingDamageEvent e) {
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof LivingEntity att) || att == e.getEntity() || !att.hasEffect(GbRegistries.DISGUISE)) return;
        e.setAmount(e.getAmount() * 1.6F);
        att.removeEffect(GbRegistries.DISGUISE);
        att.removeEffect(MobEffects.INVISIBILITY);
        if (att.level() instanceof ServerLevel level) {
            LivingEntity v = e.getEntity();
            Vec3 c = v.position().add(0, v.getBbHeight() * 0.5, 0);
            level.sendParticles(GbFx.dust(0xFF6EC7, 1.4F), c.x, c.y, c.z, 18, 0.4, 0.4, 0.4, 0);
            level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.4);
            GbFx.sound(level, c, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0F, 1.6F);
            GbFx.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.2F);
        }
        if (att instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.surprise").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    // =====================================================================================
    //  Jackpot Imortal
    // =====================================================================================
    private static void onImmortalDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !JackpotService.immortal(sp)) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        e.setCanceled(true);
        sp.setHealth(sp.getMaxHealth());
        for (MobEffectInstance i : new ArrayList<>(sp.getActiveEffects())) {
            if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) sp.removeEffect(i.getEffect());
        }
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        Long last = IMMORTAL_FX.get(sp.getUUID());
        if (last == null || now - last > 10) {
            IMMORTAL_FX.put(sp.getUUID(), now);
            Vec3 c = sp.position().add(0, 1, 0);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 40, 0.5, 0.8, 0.5, 0.4);
            level.sendParticles(GbFx.dust(0x4AFFB4, 1.6F), c.x, c.y, c.z, 30, 0.5, 0.8, 0.5, 0);
            GbFx.sound(level, c, SoundEvents.TOTEM_USE, 0.6F, 1.6F);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.immortal_save").withStyle(ChatFormatting.GREEN), true);
        }
        MagicApi.addCounter(sp, "gambler.immortal_saves", 1);
    }

    private AnimeTricks() {}
}
