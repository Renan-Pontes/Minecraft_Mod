package com.renanpontes.isekaimahou.feature.glyphs_elements_b;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.event.ElementReactionEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "glyphs_elements_b" — v1.4: 1 aumento + 1 gatilho para cada um dos 15 elementos de fusão (30 glifos).
 * Glifos em {@link GebGlyphs}; efeitos dos aumentos em {@link GebAugments}; gatilhos em {@link GebTriggers}.
 * Aprendidos pelo caminho da fusão (nós Adepto/Mestre), como os outros glifos de fusão.
 * Teste: {@code /mahou glyphs grant glyphs_elements_b [jogadores]}.
 */
public class GlyphsElementsBModule implements Module {
    @Override
    public String id() {
        return "glyphs_elements_b";
    }

    @Override
    public void init(IEventBus modBus) {
        GebGlyphs.register();

        var bus = NeoForge.EVENT_BUS;
        // Aumentos
        bus.addListener(SpellDamageEvent.Pre.class, GebAugments::onDamagePre);
        bus.addListener(SpellDamageEvent.Post.class, GebAugments::onDamagePost);
        bus.addListener(EventPriority.LOWEST, SpellCastEvent.Pre.class, GebAugments::onCastPre);
        bus.addListener(SpellCastEvent.Post.class, GebAugments::onCastPost);
        bus.addListener(LivingDeathEvent.class, GebAugments::onDeath);
        bus.addListener(EventPriority.HIGH, LivingDropsEvent.class, GebAugments::onDrops);
        bus.addListener(LivingExperienceDropEvent.class, GebAugments::onXp);
        // Gatilhos
        bus.addListener(EventPriority.HIGH, SpellCastEvent.Pre.class, GebTriggers::onCastPre);
        bus.addListener(LivingFallEvent.class, GebTriggers::onFall);
        bus.addListener(ElementReactionEvent.class, GebTriggers::onReaction);
        bus.addListener(SpellDamageEvent.Post.class, GebTriggers::onSpellDamage);
        bus.addListener(MobEffectEvent.Added.class, GebTriggers::onEffectAdded);
        bus.addListener(LivingDamageEvent.Post.class, GebTriggers::onPlayerDamaged);
        bus.addListener(EventPriority.LOWEST, true, LivingDropsEvent.class, GebTriggers::onDrops);
        bus.addListener(PlayerTickEvent.Post.class, GebTriggers::onPlayerTick);
        // Limpeza
        bus.addListener(ServerTickEvent.Post.class, GlyphsElementsBModule::onServerTick);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            GebTriggers.forget(e.getEntity().getUUID());
            GebAugments.forget(e.getEntity().getUUID());
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            GebTriggers.clear();
            GebAugments.clear();
            GebUtil.clear();
        });

        // /mahou glyphs grant glyphs_elements_b [jogadores]  (op)
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("glyphs")
                .then(Commands.literal("grant")
                        .then(Commands.literal(id())
                                .executes(c -> grant(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> grant(c, EntityArgument.getPlayers(c, "targets"))))))));
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        long now = e.getServer().overworld().getGameTime();
        if (now % 600 != 0) return;
        GebUtil.sweep(now);
        GebAugments.sweep(now);
    }

    private static int grant(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
        List<ResourceLocation> ids = GebGlyphs.all();
        for (ServerPlayer p : players) {
            for (ResourceLocation id : ids) MagicApi.learnGlyphSilently(p, id);
        }
        int n = players.size();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.geb.granted", ids.size(), n), true);
        return n;
    }
}
