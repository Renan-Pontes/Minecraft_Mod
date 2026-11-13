package com.renanpontes.isekaimahou.feature.legends_g;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.legends_g.entity.GrimoireEntity;
import com.renanpontes.isekaimahou.feature.legends_g.glyph.AstaTechniques;
import com.renanpontes.isekaimahou.feature.legends_g.glyph.HeroTechniques;
import com.renanpontes.isekaimahou.feature.legends_g.glyph.LloydTechniques;
import com.renanpontes.isekaimahou.feature.legends_g.glyph.RudeusTechniques;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "legends_g" (release 1.0, onda 10): EFEITOS e AUMENTOS de anime para o Grimório — peças COMUNS que o jogador
 * combina livremente com qualquer forma (nada de magia lendária pronta). O dono pediu: "efeitos de animes e augments".
 * <ul>
 *   <li>Lloyd Frontera (O Sétimo Príncipe): Núcleo de Mana (aumento), Sem Encantamento (aumento), Grimório Demoníaco,
 *       Magia Pura (cresce com a maestria de glifos).</li>
 *   <li>Rudeus (Mushoku Tensei): Canhão de Pedra, Atoleiro.</li>
 *   <li>Asta (Black Clover): Espada Matadora de Demônios (corte anti-magia), Forma Negra.</li>
 *   <li>Lei das Fadas (Fairy Tail), Soco Sério (One Punch Man), Dança do Deus do Fogo (Demon Slayer).</li>
 * </ul>
 * Todos são glifos comuns (elemento + tier, sem origem, não lendários): chegam pelo caminho do elemento (Arcanos: por
 * nível) e pelos sorteios normais de pergaminhos (guilda, cidades, guardiões, ruínas, Mago Errante).
 * Explosão (Konosuba) e Esfera Espiral (Naruto) já existem (legends_a/legends_b): ficaram de fora.
 * Teste: {@code /mahou glyphs grant legends_g [jogador]}.
 */
public class LegendsGModule implements Module {
    private static final List<Glyph> GLYPHS = new ArrayList<>();

    @Override
    public String id() {
        return "legends_g";
    }

    @Override
    public void init(IEventBus modBus) {
        LgRegistry.register(modBus);

        for (Glyph g : LloydTechniques.all()) GLYPHS.add(GlyphRegistry.register(g));
        for (Glyph g : RudeusTechniques.all()) GLYPHS.add(GlyphRegistry.register(g));
        for (Glyph g : AstaTechniques.all()) GLYPHS.add(GlyphRegistry.register(g));
        for (Glyph g : HeroTechniques.all()) GLYPHS.add(GlyphRegistry.register(g));

        LgEvents.register();
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ServerTickEvent.Post.class, e -> {
            RudeusTechniques.Zones.tick(e.getServer());
            LgEvents.serverTick(e.getServer());
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            RudeusTechniques.Zones.clear();
            LgEvents.clear();
            GrimoireEntity.clear();
        });
        registerCommands();
    }

    /** Glifos deste módulo (comando de teste). */
    public static List<Glyph> glyphs() {
        return Collections.unmodifiableList(GLYPHS);
    }

    /** {@code /mahou glyphs grant legends_g [jogador]} (op): ensina todos os glifos de anime deste módulo. */
    private static void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("glyphs")
                .then(MahouCommands.op("grant").then(Commands.literal("legends_g")
                        .executes(c -> grant(c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> grant(EntityArgument.getPlayer(c, "player"))))))));
    }

    private static int grant(ServerPlayer p) {
        MutableComponent list = Component.empty();
        int n = 0;
        for (Glyph g : GLYPHS) {
            MagicApi.learnGlyphSilently(p, g.id());
            if (n++ > 0) list.append(Component.literal(", "));
            list.append(g.displayName());
        }
        p.sendSystemMessage(Component.translatable("command.isekai_mahou.legends_g.granted", n, list).withStyle(ChatFormatting.LIGHT_PURPLE));
        return n;
    }
}
