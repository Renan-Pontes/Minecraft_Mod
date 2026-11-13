package com.renanpontes.isekaimahou.feature.legends_j;

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
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "legends_j" (release 1.0, onda 10) — EFEITOS e AUMENTOS de anime para o Grimório. Não são Magias Lendárias:
 * são glifos COMUNS que o jogador combina livremente com qualquer forma (pedido do dono: "efeitos de animes e
 * augments"). Ver {@link LjGlyphs}.
 * <ul>
 *   <li>Efeitos: Boogie Woogie (JJK), Roubar (Konosuba), Alquimia de Fogo e Parede de Alquimia (Fullmetal),
 *       Zoltraak (Frieren), Geass (Code Geass), Corrente Prisão / Sagrada / Rastreadora (Hunter x Hunter),
 *       Tengai Shinsei (Naruto).</li>
 *   <li>Aumentos: Kokusen/Raio Negro (JJK), Starburst Stream (SAO), Excalibur (Fate).</li>
 * </ul>
 * Teste: {@code /mahou glyphs grant legends_j [jogador]} ensina os 13 glifos.
 */
public class LegendsJModule implements Module {
    @Override
    public String id() {
        return "legends_j";
    }

    @Override
    public void init(IEventBus modBus) {
        LjRegistry.register(modBus);
        LjGlyphs.register();

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOW, SpellCastEvent.Pre.class, LjAugments::onCastPre);
        bus.addListener(SpellCastEvent.Post.class, LjAugments::onCastPost);
        bus.addListener(EventPriority.LOW, SpellDamageEvent.Pre.class, LjAugments::onDamagePre);
        bus.addListener(SpellDamageEvent.Post.class, LjAugments::onDamagePost);
        bus.addListener(LivingChangeTargetEvent.class, GeassControl::onChangeTarget);
        bus.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, GeassControl::onIncoming);
        bus.addListener(ServerTickEvent.Post.class, e -> GeassControl.tick(e.getServer()));
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> LjAugments.forget(e.getEntity().getUUID()));
        bus.addListener(ServerStoppedEvent.class, e -> {
            LjAugments.clear();
            GeassControl.clear();
        });

        registerCommands();
    }

    /** {@code /mahou glyphs grant legends_j [jogador]} (op): ensina todos os glifos deste módulo. */
    private static void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("glyphs")
                .then(MahouCommands.op("grant").then(Commands.literal("legends_j")
                        .executes(c -> grant(c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> grant(EntityArgument.getPlayer(c, "player"))))))));
    }

    private static int grant(ServerPlayer p) {
        MutableComponent list = Component.empty();
        int n = 0;
        for (ResourceLocation id : LjGlyphs.all()) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null) continue;
            MagicApi.learnGlyphSilently(p, id);
            if (n++ > 0) list.append(Component.literal(", "));
            list.append(g.displayName());
        }
        p.sendSystemMessage(Component.translatable("command.isekai_mahou.legends_j.granted", n, list)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return n;
    }
}
