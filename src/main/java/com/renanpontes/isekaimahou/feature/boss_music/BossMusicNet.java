package com.renanpontes.isekaimahou.feature.boss_music;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Comando de teste {@code /mahou music test <tema>} / {@code /mahou music stop} (precisa de cheats): o servidor só avisa o
 * cliente, que toca o tema na hora (sem chefe por perto) para ouvir rápido. A tocagem real é toda no cliente.
 */
public final class BossMusicNet {

    /** Servidor → cliente: força um tema ("" = volta ao normal; "boss_victory" = toca a vinheta). */
    public record Test(String theme) implements CustomPacketPayload {
        public static final Type<Test> TYPE = new Type<>(IsekaiMahou.id("boss_music_test"));
        public static final StreamCodec<FriendlyByteBuf, Test> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Test::theme, Test::new);

        @Override public Type<Test> type() { return TYPE; }
    }

    /** Servidor → cliente: a ENTRADA do chefe começou — toca o tema do início; ele acorda em {@code awaken} ticks. */
    public record Entrance(int entityId, String theme, int awaken) implements CustomPacketPayload {
        public static final Type<Entrance> TYPE = new Type<>(IsekaiMahou.id("boss_music_entrance"));
        public static final StreamCodec<FriendlyByteBuf, Entrance> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entrance::entityId, ByteBufCodecs.stringUtf8(64), Entrance::theme,
                ByteBufCodecs.VAR_INT, Entrance::awaken, Entrance::new);

        @Override public Type<Entrance> type() { return TYPE; }
    }

    /** Servidor → cliente: este chefe ainda não fez a entrada — não comece a música só por estar perto. */
    public record Pending(int entityId) implements CustomPacketPayload {
        public static final Type<Pending> TYPE = new Type<>(IsekaiMahou.id("boss_music_pending"));
        public static final StreamCodec<FriendlyByteBuf, Pending> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Pending::entityId, Pending::new);

        @Override public Type<Pending> type() { return TYPE; }
    }

    /** Servidor → cliente: a entrada acabou (música de proximidade liberada para quem chegou no meio). */
    public record Release(int entityId) implements CustomPacketPayload {
        public static final Type<Release> TYPE = new Type<>(IsekaiMahou.id("boss_music_release"));
        public static final StreamCodec<FriendlyByteBuf, Release> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Release::entityId, Release::new);

        @Override public Type<Release> type() { return TYPE; }
    }

    static void register(IEventBus modBus) {
        BossEntrance.register();
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("boss_music-1").optional();
            r.playToClient(Test.TYPE, Test.STREAM_CODEC, (p, c) -> c.enqueueWork(
                    () -> com.renanpontes.isekaimahou.feature.boss_music.client.BossMusicPlayer.forceTheme(p.theme())));
            r.playToClient(Entrance.TYPE, Entrance.STREAM_CODEC, (p, c) -> c.enqueueWork(
                    () -> com.renanpontes.isekaimahou.feature.boss_music.client.BossMusicPlayer.entrance(p)));
            r.playToClient(Pending.TYPE, Pending.STREAM_CODEC, (p, c) -> c.enqueueWork(
                    () -> com.renanpontes.isekaimahou.feature.boss_music.client.BossMusicPlayer.pending(p.entityId(), true)));
            r.playToClient(Release.TYPE, Release.STREAM_CODEC, (p, c) -> c.enqueueWork(
                    () -> com.renanpontes.isekaimahou.feature.boss_music.client.BossMusicPlayer.pending(p.entityId(), false)));
        });
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("music")
                .then(Commands.literal("test")
                        .then(Commands.argument("theme", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(choices(), b))
                                .executes(c -> test(c, StringArgumentType.getString(c, "theme")))))
                .then(Commands.literal("stop").executes(c -> test(c, "")))
                .then(Commands.literal("entrance").executes(c -> {
                    int n = BossEntrance.rearm(c.getSource().getPlayerOrException(), 48);
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.boss_music.entrance", n), false);
                    return n;
                }))));
    }

    private static List<String> choices() {
        List<String> l = new ArrayList<>(BossMusicSounds.THEMES);
        for (String t : BossMusicSounds.BOSS_TRACKS) if (BossMusicSounds.isTheme(t)) l.add(t);
        l.add(BossMusicSounds.VICTORY);
        for (String t : BossMusicSounds.THEMES) l.add("victory_" + t);
        for (String t : BossMusicSounds.BOSS_TRACKS) if (BossMusicSounds.isTheme(t)) l.add("victory_" + t);
        return l;
    }

    private static int test(CommandContext<CommandSourceStack> c, String theme) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        boolean victory = theme.startsWith("victory_") && BossMusicSounds.isTheme(theme.substring(8));
        if (!theme.isEmpty() && !victory && !BossMusicSounds.isTheme(theme) && !BossMusicSounds.VICTORY.equals(theme)) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.boss_music.unknown", theme,
                    String.join(", ", choices())));
            return 0;
        }
        PacketDistributor.sendToPlayer(p, new Test(theme));
        if (theme.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.boss_music.stop"), false);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.boss_music.test",
                    victory ? Component.literal(theme) : Component.translatable(
                            (BossMusicSounds.BOSS_TRACKS.contains(theme) ? "entity.isekai_mahou." : "boss_music.isekai_mahou.") + theme)), false);
        }
        return 1;
    }

    private BossMusicNet() {}
}
