package com.renanpontes.isekaimahou.glyph.weapon;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Golpes carregados (forma {@code strike}): quem está com a arma carregada, quantos golpes restam e até quando.
 * Não é salvo (dura segundos): some ao fechar o servidor ou quando a entidade sai do mundo.
 */
public final class WeaponStrikes {
    private static final class Armed {
        final LivingEntity entity;
        final SpellContext ctx;
        final Element element;
        final int rgb;
        final int max;
        int charges;
        final long until;

        Armed(LivingEntity entity, SpellContext ctx, int charges, long until) {
            this.entity = entity;
            this.ctx = ctx;
            this.element = ctx.element();
            this.rgb = ctx.color() & 0xFFFFFF;
            this.max = charges;
            this.charges = charges;
            this.until = until;
        }
    }

    private static final Map<UUID, Armed> ARMED = new HashMap<>();

    /** Carrega a arma de {@code caster} com a magia do contexto (substitui um Golpe anterior). */
    public static void arm(LivingEntity caster, SpellContext ctx, int charges, int ticks) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        int n = Math.max(1, charges);
        Armed a = new Armed(caster, ctx, n, level.getGameTime() + Math.max(20, ticks));
        ARMED.put(caster.getUUID(), a);
        Vec3 hand = WeaponFx.handPos(caster);
        ElementFx.burst(level, hand, a.element, 12, 0.25);
        level.sendParticles(WeaponFx.dust(a.rgb, 1.2F), hand.x, hand.y + 0.3, hand.z, 10, 0.2, 0.3, 0.2, 0);
        WeaponFx.sound(level, hand, SoundEvents.TRIDENT_RETURN, 0.8F, 1.5F);
        WeaponFx.sound(level, hand, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.3F);
        if (caster instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.classes.strike_ready", spellName(ctx), n)
                    .withColor(a.rgb), true);
        }
    }

    /** Golpes carregados que {@code e} ainda tem (0 = nenhum). */
    public static int charges(LivingEntity e) {
        Armed a = ARMED.get(e.getUUID());
        return a == null ? 0 : a.charges;
    }

    /** Tira o Golpe carregado (sem efeito). */
    public static void disarm(LivingEntity e) {
        ARMED.remove(e.getUUID());
    }

    /** Dano corpo a corpo: o golpe principal de quem tem Golpe carregado aplica a magia. */
    public static void onDamage(LivingDamageEvent.Post event) {
        if (ARMED.isEmpty()) return;
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) return;
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof LivingEntity attacker) || src.getDirectEntity() != attacker || attacker == target) return;
        if (!src.is(DamageTypes.PLAYER_ATTACK) && !src.is(DamageTypes.MOB_ATTACK)) return;
        Armed a = ARMED.get(attacker.getUUID());
        if (a == null || a.entity != attacker) return;
        long now = level.getGameTime();
        if (now > a.until) return;
        // Jogador: só o golpe principal (as varridas também são PLAYER_ATTACK)
        if (attacker instanceof Player p && MeleeSwings.mainHitStrength(p, target, now) < 0F) return;
        if (a.ctx.isAlly(target)) return;

        a.charges--;
        if (a.charges <= 0) ARMED.remove(attacker.getUUID());
        Vec3 at = WeaponFx.center(target);
        WeaponFx.slash(level, attacker, at, a.element, a.rgb);
        WeaponFx.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 1.2F);
        ElementFx.castSound(level, attacker, a.element);
        Vec3 dir = at.subtract(attacker.getEyePosition());
        SpellContext ctx = a.ctx;
        // Próximo tick: depois do dano do golpe (e sem a invencibilidade dele engolir o dano da magia)
        SpellScheduler.schedule(1, () -> {
            if (target.level() != level) return;
            ctx.impact(WeaponFx.center(target), dir, target);
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                ctx.resolveOnEntity(target, attacker);
            }
        });
        if (attacker instanceof ServerPlayer sp) {
            if (a.charges > 0) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.classes.strike_left", a.charges, a.max)
                        .withColor(a.rgb), true);
            }
        }
    }

    /** Todo tick do servidor: brilho na arma e fim do tempo. */
    public static void tick(MinecraftServer server) {
        if (ARMED.isEmpty()) return;
        Iterator<Map.Entry<UUID, Armed>> it = ARMED.entrySet().iterator();
        while (it.hasNext()) {
            Armed a = it.next().getValue();
            LivingEntity e = a.entity;
            if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            long now = level.getGameTime();
            if (now > a.until) {
                it.remove();
                WeaponFx.sound(level, WeaponFx.handPos(e), SoundEvents.FIRE_EXTINGUISH, 0.4F, 1.8F);
                if (e instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.classes.strike_faded")
                            .withStyle(ChatFormatting.GRAY), true);
                }
                continue;
            }
            if (now % 2 == 0) WeaponFx.weaponGlow(level, e, a.element, a.rgb);
        }
    }

    /** Nome da magia (ou "Golpe" se a receita não tem nome). */
    static Component spellName(@Nullable SpellContext ctx) {
        if (ctx != null && !ctx.recipe().name().isBlank()) return Component.literal(ctx.recipe().name());
        return Component.translatable("glyph.isekai_mahou." + WeaponGlyphs.STRIKE);
    }

    public static void forget(UUID id) {
        ARMED.remove(id);
    }

    public static void clear() {
        ARMED.clear();
    }

    private WeaponStrikes() {}
}
