package com.renanpontes.isekaimahou.core.balance;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Inflação de números das criaturas (docs/V10_DESIGN.md §0.1), sem mexer em cada classe de mob:
 * <ul>
 *   <li><b>Vida</b>: ao entrar no mundo (nascer OU carregar do disco), toda criatura do namespace {@code isekai_mahou}
 *       recebe o modificador {@link #MOD_HEALTH} (×{@link Progression#MOB_HEALTH_SCALE}) e todo mob HOSTIL do vanilla
 *       ({@link Enemy}, namespace {@code minecraft}) o {@link #VANILLA_HEALTH} (×{@code vanillaHealthMultiplier}, se
 *       {@code vanillaScaling} estiver ligado). O modificador é permanente (salvo com a entidade) e só é trocado se o
 *       valor mudou — recarregar o mundo nunca duplica. A vida atual mantém a PROPORÇÃO (mob cheio continua cheio).</li>
 *   <li><b>Dano</b>: multiplicado no {@link LivingIncomingDamageEvent} quando quem causou é uma dessas criaturas (golpes,
 *       flechas, explosões, projéteis próprios — tudo, inclusive o que os chefes calculam à mão). O dano de MAGIA
 *       ({@code isekai_mahou:spell}) fica de fora: ele já é escalado por {@link Progression#DAMAGE_SCALE} no
 *       {@code SpellContext.damage}, para não multiplicar duas vezes.</li>
 * </ul>
 * Jogadores nunca são afetados (a vida deles é do {@code CoreGameplay}).
 */
public final class MobScaling {
    /** Vida ×2 das criaturas do mod. */
    public static final ResourceLocation MOD_HEALTH = IsekaiMahou.id("balance_mod_health");
    /** Vida ×1,5 dos mobs hostis vanilla. */
    public static final ResourceLocation VANILLA_HEALTH = IsekaiMahou.id("balance_vanilla_health");

    private enum Kind { NONE, MOD, VANILLA }

    /** Que tipo de escala a entidade recebe (jogadores e mobs de outros mods: nenhuma). */
    private static Kind kind(Entity e) {
        if (!(e instanceof LivingEntity) || e instanceof Player) return Kind.NONE;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        if (IsekaiMahou.MODID.equals(id.getNamespace())) return Kind.MOD;
        if ("minecraft".equals(id.getNamespace()) && e instanceof Enemy && vanillaScaling()) return Kind.VANILLA;
        return Kind.NONE;
    }

    /** Multiplicador de vida da entidade (1 = sem escala). Útil para telas (Olho Analítico, bestiário). */
    public static float healthScale(Entity e) {
        return switch (kind(e)) {
            case MOD -> Progression.MOB_HEALTH_SCALE;
            case VANILLA -> (float) vanillaHealthMult();
            default -> 1F;
        };
    }

    /** Multiplicador de dano (não-mágico) causado pela entidade (1 = sem escala). */
    public static float damageScale(Entity e) {
        return switch (kind(e)) {
            case MOD -> Progression.MOB_DAMAGE_SCALE;
            case VANILLA -> (float) vanillaDamageMult();
            default -> 1F;
        };
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) return;
        if (living instanceof Player) return;
        Kind k = kind(living);
        apply(living, MOD_HEALTH, k == Kind.MOD ? Progression.MOB_HEALTH_SCALE - 1.0 : 0.0);
        // Mob vanilla com a opção desligada perde o modificador antigo (a opção pode mudar entre sessões)
        apply(living, VANILLA_HEALTH, k == Kind.VANILLA ? vanillaHealthMult() - 1.0 : 0.0);
    }

    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource src = event.getSource();
        Entity attacker = src.getEntity();
        if (attacker == null || attacker == event.getEntity() || event.getEntity().level().isClientSide()) return;
        if (src.is(CoreRegistries.SPELL_DAMAGE)) return; // magia: já escalada no SpellContext
        float mult = damageScale(attacker);
        if (mult != 1F && event.getAmount() > 0) event.setAmount(event.getAmount() * mult);
    }

    /**
     * Põe (ou tira, com {@code amount = 0}) o modificador ×(1+amount) da vida máxima, preservando a proporção da vida
     * atual. Não faz nada se já está com o valor certo (recarregar o mundo não duplica).
     */
    private static void apply(LivingEntity living, ResourceLocation id, double amount) {
        AttributeInstance inst = living.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        boolean want = Math.abs(amount) > 1.0E-6;
        if (cur == null && !want) return;
        if (cur != null && want && Math.abs(cur.amount() - amount) < 1.0E-6
                && cur.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) return;
        float oldMax = living.getMaxHealth();
        float ratio = oldMax > 0 ? living.getHealth() / oldMax : 1F;
        inst.removeModifier(id);
        if (want) inst.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (living.isDeadOrDying()) return;
        float newMax = living.getMaxHealth();
        living.setHealth(Math.max(Math.min(1F, newMax), Math.min(newMax, ratio * newMax)));
    }

    // =====================================================================================
    //  Config (trilha A3: MahouConfig.VANILLA_SCALING etc.) — tolera a config ainda não carregada
    // =====================================================================================

    private static boolean vanillaScaling() {
        try {
            return MahouConfig.VANILLA_SCALING.get();
        } catch (RuntimeException e) {
            return Progression.VANILLA_SCALING_DEFAULT;
        }
    }

    private static double vanillaHealthMult() {
        try {
            return MahouConfig.VANILLA_HEALTH_MULT.get();
        } catch (RuntimeException e) {
            return Progression.VANILLA_HEALTH_SCALE;
        }
    }

    private static double vanillaDamageMult() {
        try {
            return MahouConfig.VANILLA_DAMAGE_MULT.get();
        } catch (RuntimeException e) {
            return Progression.VANILLA_DAMAGE_SCALE;
        }
    }

    private MobScaling() {}
}
