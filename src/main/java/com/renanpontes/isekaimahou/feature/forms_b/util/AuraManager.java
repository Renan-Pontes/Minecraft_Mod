package com.renanpontes.isekaimahou.feature.forms_b.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Auras ativas (forma Aura): uma por conjurador. A cada segundo aplica os efeitos em volta dele
 * (raio 3×área) e gasta 2 de mana; acaba quando o tempo ou o mana acabam, ou se ele morrer/sair.
 * Não é salva (some ao fechar o mundo, como a conjuração em andamento).
 */
public final class AuraManager {
    public static final float MANA_PER_SECOND = 2.0F;

    private static final class Aura {
        final SpellContext ctx;
        final float radius;
        final int duration;
        /** Elementos da magia (multi-elemento: um braço/partícula de cada) e a cor de destaque. */
        final List<Element> elements;
        final int accent;
        int age;

        Aura(SpellContext ctx, float radius, int duration) {
            this.ctx = ctx;
            this.radius = radius;
            this.duration = duration;
            this.elements = FormsBUtil.spellElements(ctx);
            this.accent = FormsBUtil.accentColor(ctx);
        }
    }

    private static final Map<UUID, Aura> AURAS = new HashMap<>();

    /** Liga (ou substitui) a aura do conjurador. */
    public static void start(SpellContext ctx, float radius, int duration) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        boolean replaced = AURAS.put(caster.getUUID(), new Aura(ctx, radius, duration)) != null;
        ServerLevel level = ctx.level();
        ElementFx.ring(level, caster.position(), ctx.element(), radius, Math.max(20, (int) (radius * 10)));
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                0.8F, replaced ? 1.6F : 1.3F);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (AURAS.isEmpty()) return;
        // Cópia: efeitos aplicados no tick podem mexer nas auras (ex.: conjurar outra)
        for (Map.Entry<UUID, Aura> en : new ArrayList<>(AURAS.entrySet())) {
            Aura a = en.getValue();
            if (!tickAura(a)) AURAS.remove(en.getKey(), a);
        }
    }

    /** @return false quando a aura acabou */
    private static boolean tickAura(Aura a) {
        SpellContext ctx = a.ctx;
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive() || caster.isRemoved() || caster.level() != ctx.level()) return false;
        ServerLevel level = ctx.level();
        a.age++;
        if (a.age >= a.duration) {
            end(level, caster, a, false);
            return false;
        }
        Vec3 center = caster.position().add(0, caster.getBbHeight() / 2, 0);
        if (a.age % 2 == 0) spiral(level, caster, a);
        if (a.age % 20 == 1) {
            // Mana: 2 por segundo (jogadores fora do criativo); o primeiro pulso já foi pago na conjuração
            if (a.age > 1 && caster instanceof ServerPlayer sp && !sp.getAbilities().instabuild
                    && !MagicApi.get(sp).consumeMana(MANA_PER_SECOND)) {
                end(level, caster, a, true);
                return false;
            }
            // Magia programável (Aura → Ao Acertar → ...): o resolveArea não avisa o núcleo, então o 1º atingido de
            // verdade no pulso conta como impacto (o núcleo limita os disparos), antes dos efeitos
            if (FormsBUtil.wantsImpact(ctx)) {
                LivingEntity hit = FormsBUtil.areaHit(ctx, center, a.radius, null);
                if (hit != null) FormsBUtil.impactOn(ctx, hit);
            }
            ctx.resolveArea(center, a.radius, null, false);
            FormsBUtil.applyBeneficialToCaster(ctx);
            ElementFx.ring(level, caster.position(), ctx.element(), a.radius, Math.max(16, (int) (a.radius * 8)));
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.5F, 0.6F + level.random.nextFloat() * 0.3F);
        }
        return true;
    }

    /** Três braços de partículas girando em volta do conjurador. */
    private static void spiral(ServerLevel level, LivingEntity caster, Aura a) {
        int rgb = a.ctx.color() & 0xFFFFFF;
        double base = a.age * 0.18;
        double r = Math.min(a.radius, 1.6);
        for (int i = 0; i < 3; i++) {
            double ang = base + i * Math.PI * 2 / 3;
            double h = 0.2 + ((a.age * 0.05 + i * 0.33) % 1.0) * caster.getBbHeight();
            level.sendParticles(FormsBUtil.dust(i == 1 ? a.accent : rgb, 1.0F), caster.getX() + Math.cos(ang) * r, caster.getY() + h,
                    caster.getZ() + Math.sin(ang) * r, 1, 0, 0, 0, 0);
        }
        if (a.age % 6 == 0) {
            double ang = -base * 0.7;
            Element el = a.elements.get((a.age / 6) % a.elements.size());
            level.sendParticles(ElementFx.signature(el), caster.getX() + Math.cos(ang) * a.radius,
                    caster.getY() + 0.1, caster.getZ() + Math.sin(ang) * a.radius, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private static void end(ServerLevel level, LivingEntity caster, Aura a, boolean noMana) {
        ElementFx.burst(level, caster.position().add(0, 1, 0), a.ctx.element(), 16, 0.5);
        // Magia programável: Aura → Ao Terminar → ... sai do conjurador quando a aura se apaga
        a.ctx.end(caster.position().add(0, caster.getBbHeight() * 0.5, 0), a.ctx.look());
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 1.4F);
        if (caster instanceof ServerPlayer sp) {
            String key = noMana ? "message.isekai_mahou.forms_b.aura_no_mana" : "message.isekai_mahou.forms_b.aura_end";
            sp.displayClientMessage(Component.translatable(key).withStyle(noMana ? ChatFormatting.RED : ChatFormatting.GRAY), true);
        }
    }

    /** Servidor fechou. */
    public static void clear() {
        AURAS.clear();
    }

    private AuraManager() {}
}
