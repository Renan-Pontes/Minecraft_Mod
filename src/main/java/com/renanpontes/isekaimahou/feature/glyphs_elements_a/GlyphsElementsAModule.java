package com.renanpontes.isekaimahou.feature.glyphs_elements_a;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "glyphs_elements_a" — v1.4: aumentos e gatilhos dos 7 elementos base (arcano, fogo, água, terra, vento, luz,
 * sombra) e o efeito utilitário Aplainar Terreno (Terra). Ver {@link ElementGlyphsA}.
 * <p>
 * Teste: {@code /mahou glyphs grant glyphs_elements_a [jogador]} ensina os 15 glifos.
 */
public class GlyphsElementsAModule implements Module {
    @Override
    public String id() {
        return "glyphs_elements_a";
    }

    @Override
    public void init(IEventBus modBus) {
        ElementGlyphsA.register();

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGH, ElementTriggers::onPre);
        bus.addListener(SpellCastEvent.Post.class, ElementTriggers::onPost);
        bus.addListener(SpellCastEvent.Post.class, ElementAugments::onCastPost);
        bus.addListener(SpellDamageEvent.Pre.class, ElementAugments::onDamagePre);
        bus.addListener(SpellDamageEvent.Post.class, ElementAugments::onDamagePost);
        bus.addListener(MobEffectEvent.Added.class, ElementTriggers::onEffectAdded);
        bus.addListener(LivingFallEvent.class, ElementTriggers::onFall);
        bus.addListener(LivingHealEvent.class, ElementTriggers::onHeal);
        bus.addListener(PlayerTickEvent.Post.class, ElementTriggers::onPlayerTick);
        bus.addListener(ServerTickEvent.Post.class, GlyphsElementsAModule::onServerTick);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            ElementTriggers.forget(e.getEntity().getUUID());
            ElementAugments.forget(e.getEntity().getUUID());
            TerrainShaper.forget(e.getEntity().getUUID());
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            ElementTriggers.clear();
            ElementAugments.clear();
            TerrainShaper.clear();
        });

        registerCommands();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        TerrainShaper.tick(event.getServer());
        long now = event.getServer().overworld().getGameTime();
        if (now % 200 == 0) {
            ElementAugments.cleanup(now);
            ElementTriggers.cleanup(now);
        }
    }

    /** {@code /mahou glyphs grant glyphs_elements_a [jogador]} (op): ensina todos os glifos deste módulo. */
    private static void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("glyphs")
                .then(MahouCommands.op("grant").then(Commands.literal("glyphs_elements_a")
                        .executes(c -> grant(c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> grant(EntityArgument.getPlayer(c, "player"))))))));
    }

    private static int grant(ServerPlayer p) {
        MutableComponent list = Component.empty();
        int n = 0;
        for (ResourceLocation id : ElementGlyphsA.all()) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null) continue;
            MagicApi.learnGlyphSilently(p, id);
            if (n++ > 0) list.append(Component.literal(", "));
            list.append(g.displayName());
        }
        p.sendSystemMessage(Component.translatable("command.isekai_mahou.glyphs_elements_a.granted", n, list)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return n;
    }
}
