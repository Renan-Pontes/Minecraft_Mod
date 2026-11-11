package com.renanpontes.isekaimahou.glyph.weapon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Posturas ativas (forma {@code stance}). O cliente avisa quais teclas de slot estão seguradas
 * ({@link #setHeld}); a Postura do jogador dura enquanto a tecla do slot dela estiver segurada.
 * Não é salvo: ao sair do mundo/fechar o servidor a Postura acaba (os modificadores são temporários).
 */
public final class WeaponStances {
    /** Intervalo entre os pulsos dos efeitos. */
    public static final int PULSE_TICKS = 20;
    /** Custo de manter: fração do custo da magia por pulso. */
    public static final float UPKEEP = 0.3F;
    /** Duração quando ninguém segura a tecla (gatilho, mob, sequência). */
    public static final int UNHELD_TICKS = 100;

    public static final ResourceLocation SLOW_ID = IsekaiMahou.id("stance_slow");
    public static final ResourceLocation FIRM_ID = IsekaiMahou.id("stance_firm");
    private static final double SLOW = -0.4;
    private static final double FIRM = 0.5;

    /** Ticks depois de conjurar em que a tecla ainda pode "pegar" a Postura (o aviso da tecla pode chegar depois). */
    private static final int BIND_GRACE = 6;

    private static final class Stance {
        final LivingEntity entity;
        final SpellContext ctx;
        /** Slot cuja tecla mantém a Postura (-1 = sem tecla: dura até {@link #until}). */
        int slot;
        /** Slot com esta receita cuja tecla ainda não chegou como segurada (-1 = nenhum). */
        final int candidate;
        final long started;
        long until;
        long nextPulse;
        final int rgb;
        final float upkeep;

        Stance(LivingEntity entity, SpellContext ctx, int slot, int candidate, long started, long until, long nextPulse) {
            this.entity = entity;
            this.ctx = ctx;
            this.slot = slot;
            this.candidate = candidate;
            this.started = started;
            this.until = until;
            this.nextPulse = nextPulse;
            this.rgb = ctx.color() & 0xFFFFFF;
            this.upkeep = Math.max(1F, ctx.rootSpell().manaCost() * UPKEEP);
        }
    }

    private static final Map<UUID, Stance> ACTIVE = new HashMap<>();
    /** Teclas de slot seguradas por jogador (bit i = slot i), vindas do cliente. */
    private static final Map<UUID, Integer> HELD = new HashMap<>();

    /** A forma: primeiro pulso agora e começa a manter. */
    public static void begin(SpellContext ctx) {
        LivingEntity e = ctx.caster();
        if (e == null || !(e.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Stance old = ACTIVE.remove(e.getUUID());
        if (old != null) removeModifiers(old.entity);

        int slot = -1, candidate = -1;
        if (e instanceof ServerPlayer sp && ctx.isPlayerCast()) {
            slot = slotWith(sp, ctx, true);
            if (slot < 0) candidate = slotWith(sp, ctx, false);
        }
        long until = slot >= 0 ? Long.MAX_VALUE : now + UNHELD_TICKS;
        Stance st = new Stance(e, ctx, slot, candidate, now, until, now + PULSE_TICKS);
        ACTIVE.put(e.getUUID(), st);
        addModifiers(e);
        pulse(level, st, true);
        WeaponFx.sound(level, e.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 0.8F);
        WeaponFx.sound(level, e.position(), SoundEvents.ANVIL_PLACE, 0.25F, 1.8F);
        if (e instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(slot >= 0 || candidate >= 0 ? "message.isekai_mahou.classes.stance_hold"
                    : "message.isekai_mahou.classes.stance_timed", WeaponStrikes.spellName(ctx)).withColor(st.rgb), true);
        }
    }

    /** {@code e} está numa Postura? */
    public static boolean inStance(LivingEntity e) {
        return ACTIVE.containsKey(e.getUUID());
    }

    /** Desfaz a Postura de {@code e} (se houver). */
    public static void stop(LivingEntity e) {
        Stance st = ACTIVE.remove(e.getUUID());
        if (st != null) end(st, null);
    }

    /** O cliente mandou o estado das teclas de slot. */
    public static void setHeld(ServerPlayer p, int mask) {
        if (mask == 0) HELD.remove(p.getUUID());
        else HELD.put(p.getUUID(), mask);
    }

    private static boolean held(ServerPlayer p, int slot) {
        return slot >= 0 && (HELD.getOrDefault(p.getUUID(), 0) & (1 << slot)) != 0;
    }

    /**
     * Slot (do loadout ativo do elemento da magia) que tem esta receita — com {@code mustHold}, só se a tecla dele
     * estiver segurada; -1 se nenhum.
     */
    private static int slotWith(ServerPlayer p, SpellContext ctx, boolean mustHold) {
        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        SpellRecipe recipe = ctx.recipe();
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            if (mustHold && !held(p, slot)) continue;
            SpellSlot s = d.peekSlot(loadout, slot);
            for (SpellRecipe r : s.all().values()) {
                if (r != null && r.equals(recipe)) return slot;
            }
        }
        return -1;
    }

    /** Todo tick do servidor: soltou a tecla? acabou o tempo? hora do pulso (pagando a manutenção)? */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        // Cópia: os pulsos aplicam efeitos (dano, gatilhos...) que podem começar/desfazer outras Posturas
        for (Stance st : new ArrayList<>(ACTIVE.values())) {
            LivingEntity e = st.entity;
            if (ACTIVE.get(e.getUUID()) != st) continue;
            if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
                ACTIVE.remove(e.getUUID());
                removeModifiers(e);
                continue;
            }
            long now = level.getGameTime();
            // A tecla chegou como segurada logo depois da conjuração: a Postura passa a durar enquanto ela estiver
            if (st.slot < 0 && st.candidate >= 0 && now - st.started <= BIND_GRACE && e instanceof ServerPlayer bp
                    && held(bp, st.candidate)) {
                st.slot = st.candidate;
                st.until = Long.MAX_VALUE;
            }
            if (st.slot >= 0 && e instanceof ServerPlayer sp && !held(sp, st.slot)) {
                ACTIVE.remove(e.getUUID());
                end(st, null);
                continue;
            }
            if (now >= st.until) {
                ACTIVE.remove(e.getUUID());
                end(st, "message.isekai_mahou.classes.stance_over");
                continue;
            }
            if (now >= st.nextPulse) {
                st.nextPulse = now + PULSE_TICKS;
                if (!payUpkeep(e, st)) {
                    ACTIVE.remove(e.getUUID());
                    end(st, null);
                    if (e instanceof ServerPlayer sp) {
                        sp.displayClientMessage(MagicApi.noResourceMessage(sp).withStyle(ChatFormatting.RED), true);
                    }
                    continue;
                }
                pulse(level, st, false);
            } else if (now % 5 == 0) {
                WeaponFx.groundRing(level, e, st.rgb, 0.8, 8);
            }
        }
    }

    private static boolean payUpkeep(LivingEntity e, Stance st) {
        if (!(e instanceof ServerPlayer sp) || !st.ctx.isPlayerCast() || sp.getAbilities().instabuild) return true;
        return MagicApi.get(sp).consumeMana(st.upkeep);
    }

    private static void pulse(ServerLevel level, Stance st, boolean first) {
        LivingEntity e = st.entity;
        st.ctx.resolveOnSelf();
        WeaponFx.groundRing(level, e, st.rgb, 1.1, 18);
        ElementFx.burst(level, e.position().add(0, e.getBbHeight() * 0.5, 0), st.ctx.element(), first ? 16 : 6, 0.45);
        if (!first) WeaponFx.sound(level, e.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 0.5F, 1.3F);
    }

    private static void end(Stance st, String reasonKey) {
        LivingEntity e = st.entity;
        removeModifiers(e);
        if (e.level() instanceof ServerLevel level) {
            WeaponFx.sound(level, e.position(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.7F, 0.7F);
            level.sendParticles(WeaponFx.dust(st.rgb, 1.0F), e.getX(), e.getY() + 0.6, e.getZ(), 10, 0.35, 0.4, 0.35, 0);
        }
        if (reasonKey != null && e instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(reasonKey).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void addModifiers(LivingEntity e) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW_ID);
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance kb = e.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(FIRM_ID);
            kb.addTransientModifier(new AttributeModifier(FIRM_ID, FIRM, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Tira os modificadores da Postura (seguro chamar sempre). */
    public static void removeModifiers(LivingEntity e) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW_ID);
        AttributeInstance kb = e.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.removeModifier(FIRM_ID);
    }

    /** Jogador saiu: esquece as teclas e desfaz a Postura. */
    public static void forget(ServerPlayer p) {
        HELD.remove(p.getUUID());
        Stance st = ACTIVE.remove(p.getUUID());
        if (st != null) removeModifiers(st.entity);
    }

    public static void clear() {
        ACTIVE.clear();
        HELD.clear();
    }

    private WeaponStances() {}
}
