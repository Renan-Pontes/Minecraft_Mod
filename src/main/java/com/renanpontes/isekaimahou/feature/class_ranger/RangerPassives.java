package com.renanpontes.isekaimahou.feature.class_ranger;

import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerCompanion;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerSteedEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.glyph.RainArrows;
import com.renanpontes.isekaimahou.feature.class_ranger.item.RangerArmorItem;
import com.renanpontes.isekaimahou.feature.class_ranger.item.RangerOutfitTier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Bônus de classe do Ranger (passivos) e os feitos da Trilha:
 * <ul>
 *   <li><b>Flechas mais rápidas</b>: toda flecha disparada pelo Ranger sai 25% mais rápida (mais alcance e precisão).</li>
 *   <li><b>Olho de Caçador</b>: flechas do Ranger causam +10% (+3% por rank da Trilha), +1% por bloco além de 16 (máx.
 *   +25%), +15% montado (arqueiro a cavalo) e o bônus do Traje Selvagem completo (+5..16%).</li>
 *   <li><b>Marca do Caçador</b>: alvos marcados levam o bônus da marca do dono e dos companheiros dele.</li>
 *   <li><b>Pés Leves</b>: +6% de velocidade de movimento enquanto for Ranger.</li>
 *   <li><b>Companheiros mais fortes</b>: o nível deles sobe com o rank e o Círculo ({@link RangerCompanions#level}); os
 *   abates deles dão XP ao dono (60%).</li>
 * </ul>
 */
public final class RangerPassives {
    private static final String FAST_TAG = "isekai_mahou.ranger_fast";
    public static final ResourceLocation LIGHT_FEET = IsekaiMahou.id("ranger_light_feet");
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));
    /** Vida máxima a partir da qual a presa é "grande" (já com a inflação dos mobs). */
    private static final float GREAT_PREY_HEALTH = 150F;

    // =====================================================================================
    //  Flechas mais rápidas
    // =====================================================================================

    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof AbstractArrow arrow) || e.loadedFromDisk()) return;
        if (arrow.getTags().contains(RainArrows.TAG) || arrow.getTags().contains(FAST_TAG)) return;
        if (!(arrow.getOwner() instanceof ServerPlayer sp) || MagicApi.get(sp).path() != MagicPath.RANGER) return;
        arrow.addTag(FAST_TAG);
        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.25));
    }

    // =====================================================================================
    //  Dano: Olho de Caçador e Marca
    // =====================================================================================

    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity target = e.getEntity();
        DamageSource src = e.getSource();
        Entity attacker = src.getEntity();
        if (attacker == null || target.level().isClientSide()) return;
        float mult = 1.0F + RangerMarks.bonusFor(target, attacker);
        if (attacker instanceof ServerPlayer sp && src.getDirectEntity() instanceof AbstractArrow) {
            MagicData d = MagicApi.get(sp);
            if (d.path() == MagicPath.RANGER) {
                float bonus = 0.10F + 0.03F * RangerProgress.rank(d);
                double dist = sp.distanceTo(target);
                if (dist > 16) bonus += (float) Math.min(0.25, (dist - 16) * 0.01);
                if (sp.isPassenger()) bonus += 0.15F;
                RangerOutfitTier set = RangerArmorItem.fullSet(sp);
                if (set != null) bonus += RangerArmorItem.setArrowBonus(set);
                mult += bonus;
            }
        }
        if (mult > 1.0001F) e.setAmount(e.getAmount() * mult);
    }

    // =====================================================================================
    //  Feitos da Trilha (abates)
    // =====================================================================================

    public static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide()) return;
        UUID markOwner = RangerMarks.ownerOf(victim);
        RangerMarks.clear(victim);
        DamageSource src = e.getSource();
        Entity killer = src.getEntity();
        ServerPlayer hunter = null;
        boolean byCompanion = false;
        if (killer instanceof ServerPlayer sp) {
            hunter = sp;
        } else if ((killer instanceof RangerCompanion || killer instanceof RangerSteedEntity) && killer instanceof OwnableEntity own
                && own.getOwnerUUID() != null && victim.getServer() != null) {
            hunter = victim.getServer().getPlayerList().getPlayer(own.getOwnerUUID());
            byCompanion = hunter != null;
        }
        if (hunter == null || victim == hunter) return;
        if (byCompanion) {
            RangerProgress.count(hunter, RangerProgress.COMPANION_KILL, 1);
            // o dono aprende com a caçada dos companheiros (60% do XP do abate)
            int xp = Math.max(1, Math.round(Progression.killXp(victim.getMaxHealth(), false) * 0.6F));
            MagicApi.giveXp(hunter, xp, "ranger_companion");
        } else {
            if (src.getDirectEntity() instanceof AbstractArrow) RangerProgress.count(hunter, RangerProgress.ARROW_KILL, 1);
            if (hunter.distanceTo(victim) >= 20) RangerProgress.count(hunter, RangerProgress.LONG_KILL, 1);
        }
        if (markOwner != null && markOwner.equals(hunter.getUUID())) RangerProgress.count(hunter, RangerProgress.MARK_KILL, 1);
        if (victim.getMaxHealth() >= GREAT_PREY_HEALTH || victim.getType().is(BOSSES)) {
            RangerProgress.count(hunter, RangerProgress.GREAT_PREY, 1);
        }
    }

    // =====================================================================================
    //  Pés Leves
    // =====================================================================================

    /** Põe/tira o +6% de velocidade conforme a classe (login, renascer, escolha, a cada 5 s). */
    public static void updateLightFeet(ServerPlayer sp) {
        AttributeInstance inst = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        boolean want = MagicApi.get(sp).path() == MagicPath.RANGER;
        boolean has = inst.getModifier(LIGHT_FEET) != null;
        if (want && !has) inst.addPermanentModifier(new AttributeModifier(LIGHT_FEET, 0.06, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else if (!want && has) inst.removeModifier(LIGHT_FEET);
    }

    private RangerPassives() {}
}
