package com.renanpontes.isekaimahou.feature.ambience;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Módulo "ambience" — v1.0 RELEASE (áudio):
 * <ul>
 *   <li><b>Sons próprios das magias</b> por elemento e tamanho, gerados por código ({@code tools/gen/ambience_sfx.py}),
 *       tocados pelos eventos de conjuração/dano do núcleo — nenhum glifo foi editado ({@link SpellSounds}).</li>
 *   <li><b>Música ambiente</b> baixa, de dark fantasy, gerada por código ({@code tools/gen/ambience_music.py}): masmorra,
 *       cidadela, biblioteca, torre, cidade, Abismo e Reino do Lar. O servidor diz a zona ({@link ZoneTracker}); o
 *       cliente troca de faixa com fade ({@code client/AmbientPlayer}). Chefe por perto sempre tem prioridade.</li>
 *   <li><b>Brilho no escuro</b> de cristais, runas e essências, sem mexer nos modelos dos outros módulos
 *       ({@code client/EmissiveModels}, lista em {@code assets/isekai_mahou/ambience/emissive.json}).</li>
 * </ul>
 * Opções (volume e liga/desliga) em {@link AmbienceConfig}. Comandos de teste:
 * {@code /mahou ambience zone}, {@code /mahou ambience music <zona|off|auto>},
 * {@code /mahou ambience sfx <família> [cast|impact] [tamanho 0-2]}.
 */
public class AmbienceModule implements Module {
    @Override
    public String id() {
        return "ambience";
    }

    @Override
    public void init(IEventBus modBus) {
        AmbienceConfig.register();
        AmbienceSounds.register(modBus);
        modBus.addListener(AmbienceNet::register);
        ZoneTracker.init(NeoForge.EVENT_BUS);
        SpellSounds.init(NeoForge.EVENT_BUS);
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("ambience")
                .then(Commands.literal("zone").executes(c -> zone(c.getSource())))
                .then(Commands.literal("music")
                        .then(Commands.literal("auto").executes(c -> music(c.getSource(), "", AmbienceNet.MODE_AUTO)))
                        .then(Commands.literal("off").executes(c -> music(c.getSource(), "", AmbienceNet.MODE_FORCED)))
                        .then(Commands.argument("track", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(AmbienceSounds.ZONES, b))
                                .executes(c -> music(c.getSource(), StringArgumentType.getString(c, "track"),
                                        AmbienceNet.MODE_FORCED))))
                .then(Commands.literal("sfx")
                        .then(Commands.argument("family", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(AmbienceSounds.Family.values()).map(f -> f.id), b))
                                .executes(c -> sfx(c.getSource(), StringArgumentType.getString(c, "family"), "cast", 1))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"cast", "impact"}, b))
                                        .executes(c -> sfx(c.getSource(), StringArgumentType.getString(c, "family"),
                                                StringArgumentType.getString(c, "kind"), 1))
                                        .then(Commands.argument("size", IntegerArgumentType.integer(0, 2))
                                                .executes(c -> sfx(c.getSource(), StringArgumentType.getString(c, "family"),
                                                        StringArgumentType.getString(c, "kind"),
                                                        IntegerArgumentType.getInteger(c, "size")))))))));
    }

    // ---------------- Teste ----------------
    private static Component zoneName(String zone) {
        return Component.translatable("ambience.isekai_mahou.zone." + (zone.isEmpty() ? "none" : zone));
    }

    private static int zone(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        if (p == null) return 0;
        String zone = ZoneTracker.zoneAt(p.serverLevel(), p.blockPosition());
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.ambience.zone", zoneName(zone)), false);
        return 1;
    }

    private static int music(CommandSourceStack source, String track, byte mode) {
        ServerPlayer p = source.getPlayer();
        if (p == null) return 0;
        if (mode == AmbienceNet.MODE_FORCED && !track.isEmpty() && !AmbienceSounds.isZone(track)) {
            source.sendFailure(Component.translatable("command.isekai_mahou.ambience.unknown", track));
            return 0;
        }
        PacketDistributor.sendToPlayer(p, new AmbienceNet.Zone(track, mode));
        source.sendSuccess(() -> mode == AmbienceNet.MODE_AUTO
                ? Component.translatable("command.isekai_mahou.ambience.music_auto")
                : Component.translatable("command.isekai_mahou.ambience.music", zoneName(track)), false);
        return 1;
    }

    private static int sfx(CommandSourceStack source, String family, String kind, int size) {
        ServerPlayer p = source.getPlayer();
        AmbienceSounds.Family f = AmbienceSounds.Family.byId(family);
        if (p == null) return 0;
        if (f == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.ambience.unknown", family));
            return 0;
        }
        byte k = "impact".equals(kind) ? AmbienceSounds.KIND_IMPACT : AmbienceSounds.KIND_CAST;
        // 3 blocos à frente do jogador, para dar para ouvir de onde vem
        var pos = p.getEyePosition().add(p.getLookAngle().scale(3));
        PacketDistributor.sendToPlayer(p, new AmbienceNet.Sfx(k, (byte) f.ordinal(), (byte) size, (float) pos.x, (float) pos.y,
                (float) pos.z, 1F));
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.ambience.sfx", family, kind, size), false);
        return 1;
    }
}
