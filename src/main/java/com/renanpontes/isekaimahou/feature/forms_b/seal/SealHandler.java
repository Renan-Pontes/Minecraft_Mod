package com.renanpontes.isekaimahou.feature.forms_b.seal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Selo: grava a magia na arma da mão principal e a dispara a cada golpe corpo a corpo.
 * Só golpes "cheios" (barra de ataque ≥ 85%) de jogador gastam carga; varridas não contam.
 */
public final class SealHandler {
    /** Último ataque de cada jogador: alvo, tick e força (o dano chega logo depois no mesmo tick). */
    private record Swing(UUID target, long tick, float strength) {}

    private static final Map<UUID, Swing> SWINGS = new HashMap<>();
    private static final float MIN_STRENGTH = 0.85F;

    /**
     * A arma pode receber um selo? (itens únicos com durabilidade: espadas, machados, tridentes...;
     * conta também os inquebráveis, que têm durabilidade máxima mas não gastam)
     */
    public static boolean canSeal(ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxStackSize() == 1 && stack.has(DataComponents.MAX_DAMAGE);
    }

    /** Grava a magia do contexto na arma. */
    public static void seal(SpellContext ctx, ItemStack weapon, int charges) {
        SealData old = weapon.get(FormsBRegistry.WEAPON_SEAL.get());
        boolean glintAdded = old != null ? old.glintAdded() : !weapon.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        if (glintAdded) weapon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        weapon.set(FormsBRegistry.WEAPON_SEAL.get(),
                new SealData(ctx.recipe(), ctx.element(), ctx.powerMult(), charges, charges, glintAdded));
    }

    /** Tira o selo (e o brilho, se fomos nós que pusemos). */
    public static void unseal(ItemStack weapon) {
        SealData d = weapon.get(FormsBRegistry.WEAPON_SEAL.get());
        if (d == null) return;
        if (d.glintAdded()) weapon.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        weapon.remove(FormsBRegistry.WEAPON_SEAL.get());
    }

    public static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (p.level().isClientSide) return;
        SWINGS.put(p.getUUID(), new Swing(event.getTarget().getUUID(), p.level().getGameTime(), p.getAttackStrengthScale(0.5F)));
    }

    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) return;
        DamageSource src = event.getSource();
        Entity attackerE = src.getEntity();
        if (!(attackerE instanceof LivingEntity attacker) || src.getDirectEntity() != attacker) return;
        if (!src.is(DamageTypes.PLAYER_ATTACK) && !src.is(DamageTypes.MOB_ATTACK)) return;
        ItemStack weapon = attacker.getMainHandItem();
        SealData data = weapon.get(FormsBRegistry.WEAPON_SEAL.get());
        if (data == null || data.charges() <= 0) return;
        if (attacker instanceof Player p) {
            Swing s = SWINGS.remove(p.getUUID());
            if (s == null || !s.target().equals(target.getUUID()) || s.tick() != level.getGameTime() || s.strength() < MIN_STRENGTH) return;
        }
        if (!target.isAlive() || target.getHealth() <= 0) return;

        // Sem MagicData: não checa conhecimento/Círculo de novo (já valeu ao selar); o elemento do selo é o primário
        SpellParser.Result r = SpellParser.parse(data.recipe(), data.element(), null);
        if (!r.ok()) {
            unseal(weapon);
            return;
        }
        SpellContext ctx = new SpellContext(level, attacker, data.element(), data.recipe(), r.spell());
        ctx.setPowerMult(data.power());
        // Golpe de JOGADOR: efeitos de outro elemento usam o elemento do glifo e Saltar/Insaciável pagam com mana
        if (attacker instanceof Player) ctx.markPlayerCast();
        if (ctx.isAlly(target)) return;

        // Gasta a carga já (o efeito sai no próximo tick, depois do dano do golpe)
        int left = data.charges() - 1;
        if (left > 0) weapon.set(FormsBRegistry.WEAPON_SEAL.get(), data.withCharges(left));
        else unseal(weapon);
        int rgb = data.rgb();
        SpellScheduler.schedule(1, () -> {
            if (!target.isAlive()) return;
            target.invulnerableTime = 0;
            ctx.resolveOnEntity(target, attacker);
            ElementFx.burst(level, target.getBoundingBox().getCenter(), data.element(), 12, 0.3);
            level.sendParticles(FormsBUtil.dust(rgb, 1.3F), target.getX(), target.getY() + target.getBbHeight() * 0.6,
                    target.getZ(), 10, 0.25, 0.3, 0.25, 0);
            level.sendParticles(Vfx.magicCrit(), target.getX(), target.getY() + target.getBbHeight() * 0.6,
                    target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
        });
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 1.4F);
        if (attacker instanceof ServerPlayer sp) {
            if (left > 0) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.forms_b.seal_charges", left, data.maxCharges())
                        .withColor(rgb), true);
            } else {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.forms_b.seal_depleted")
                        .withStyle(ChatFormatting.GRAY), true);
                level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        }
    }

    /** Servidor fechou. */
    public static void clear() {
        SWINGS.clear();
    }

    private SealHandler() {}
}
