package com.renanpontes.isekaimahou.feature.legends_i;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.legends_i.effect.AmaterasuEffect;
import com.renanpontes.isekaimahou.feature.legends_i.entity.KageBunshinEntity;
import com.renanpontes.isekaimahou.feature.legends_i.glyph.PainGlyphs;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "legends_i" (release 1.0, onda 10): EFEITOS e AUMENTOS de anime para o Grimório — peças comuns que o jogador
 * combina livremente com qualquer forma (não são Magias Lendárias; o dono pediu "efeitos de animes e augments").
 * <ul>
 *   <li>One Piece: Haki do Armamento (aumento), Haki da Observação, Haki do Rei, Gomu Gomu no Pistol/Gatling.</li>
 *   <li>Naruto — Pain: Shinra Tensei, Bansho Ten'in, Chibaku Tensei.</li>
 *   <li>Naruto — Konan: Shuriken de Papel (efeito) e Asas de Papel (aumento).</li>
 *   <li>Naruto — Uchiha: Amaterasu, Susanoo.</li>
 *   <li>Naruto: Chidori/Raikiri, Kage Bunshin. (Rasengan já existe: Esfera Espiral, legends_b.)</li>
 * </ul>
 * Como todo glifo comum (sem origem, sem "lendário"), chegam pelo caminho do elemento e pelos sorteios de pergaminhos
 * de sempre. Teste: {@code /mahou glyphs grant legends_i [jogador]} ensina os 13 glifos. Ver {@link LiGlyphs}.
 */
public class LegendsIModule implements Module {
    @Override
    public String id() {
        return "legends_i";
    }

    @Override
    public void init(IEventBus modBus) {
        LiRegistry.register(modBus);
        modBus.addListener(LiNet::register);
        LiGlyphs.register();
        LiEvents.register();

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(PlayerEvent.StartTracking.class, LiUtil::onStartTracking);
        bus.addListener(ServerTickEvent.Post.class, e -> {
            PainGlyphs.Chibaku.tick(e.getServer());
            long now = e.getServer().overworld().getGameTime();
            if (now % 100 == 0) {
                LiUtil.prune(now);
                LiEvents.cleanup(now);
            }
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            PainGlyphs.Chibaku.clear();
            LiEvents.clear();
            LiUtil.clearStates();
            KageBunshinEntity.clearAll();
            AmaterasuEffect.clear();
        });
        registerCommands();
    }

    /** {@code /mahou glyphs grant legends_i [jogador]} (op): ensina todos os glifos deste módulo. */
    private static void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("glyphs")
                .then(MahouCommands.op("grant").then(Commands.literal("legends_i")
                        .executes(c -> grant(c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> grant(EntityArgument.getPlayer(c, "player"))))))));
    }

    private static int grant(ServerPlayer p) {
        MutableComponent list = Component.empty();
        int n = 0;
        for (ResourceLocation id : LiGlyphs.all()) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null) continue;
            MagicApi.learnGlyphSilently(p, id);
            if (n++ > 0) list.append(Component.literal(", "));
            list.append(g.displayName());
        }
        p.sendSystemMessage(Component.translatable("command.isekai_mahou.legends_i.granted", n, list)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return n;
    }
}
