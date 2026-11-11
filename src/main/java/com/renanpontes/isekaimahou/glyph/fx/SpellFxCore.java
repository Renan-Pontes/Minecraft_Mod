package com.renanpontes.isekaimahou.glyph.fx;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import com.renanpontes.isekaimahou.core.event.SpellCastEvent;

/**
 * Inicialização (lado comum) dos efeitos visuais do sistema de glifos: partículas {@code fx_*}, círculo mágico de
 * conjuração ({@link SpellVisuals}) e o comando de vitrine {@code /spellfx <elemento> <tier>} (op 2).
 * O cliente fica em {@code client/spellfx/SpellFxClient}.
 */
public final class SpellFxCore {
    public static void init(IEventBus modBus) {
        SpellFxParticles.register(modBus);
        NeoForge.EVENT_BUS.addListener(SpellCastEvent.Post.class, SpellVisuals::onCast);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> SpellVisuals.clear());
        NeoForge.EVENT_BUS.addListener(SpellFxCore::commands);
    }

    private static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("spellfx")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("element", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(Element.values()).map(el -> el.id), b))
                        .executes(c -> demo(c, 3))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(0, 5))
                                .executes(c -> demo(c, IntegerArgumentType.getInteger(c, "tier"))))));
    }

    /** Vitrine: círculo de conjuração + disparo + impacto, decalque e pilar na frente do jogador. */
    private static int demo(CommandContext<CommandSourceStack> c, int tier) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Element el = Element.byId(StringArgumentType.getString(c, "element"));
        if (el == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.spellfx.bad_element"));
            return 0;
        }
        ServerLevel level = p.serverLevel();
        int rgb = el.color;
        float r = SpellVisuals.radius(tier);
        int rel = SpellFxParticles.FLAG_RELEASE;
        ElementFx.castCircle(level, p, rgb, r, 30, SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, tier <= 1 ? 3 : 0) | rel);
        ElementFx.castCircle(level, p, rgb, 0.45F + 0.12F * tier, 30, SpellFxParticles.flags(tier, SpellFxParticles.MODE_FRONT, tier >= 3 ? 0 : 3) | rel);
        if (tier >= 5) {
            ElementFx.castCircle(level, p, rgb, r * 1.7F, 50, SpellFxParticles.flags(tier, SpellFxParticles.MODE_SKY, 0) | rel);
        }
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        Vec3 at = p.position().add(look.normalize().scale(6)).add(0, 0.05, 0);
        ElementFx.runeCircle(level, at, rgb, 1.2F + 0.5F * tier, 60, tier);
        ElementFx.impact(level, at.add(0, 1.0, 0), el, tier, new Vec3(0, 1, 0));
        ElementFx.burst(level, at.add(0, 1.5, 0), el, 16, 0.4);
        ElementFx.line(level, p.getEyePosition(), at.add(0, 1.0, 0), el);
        if (tier >= 4) ElementFx.pillar(level, at, rgb, 0.6F + 0.2F * tier, 4.0F + 2 * tier, 24);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.spellfx.demo", el.displayName(), tier), false);
        return 1;
    }

    private SpellFxCore() {}
}
