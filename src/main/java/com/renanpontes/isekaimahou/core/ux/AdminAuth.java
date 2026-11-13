package com.renanpontes.isekaimahou.core.ux;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * v1.0 — <b>senha de administrador</b>: nenhum jogador usa comando de admin (qualquer comando que um jogador comum
 * não poderia usar: /mahou de teste, /give, /gamemode, /tp, /op...) sem antes fazer
 * {@code /mahou admin login <senha>} nesta sessão. Ser "op" ou ter cheats ligados NÃO basta — assim ninguém trapaceia
 * no servidor. O console do servidor e o RCON (quem tem acesso à máquina) continuam livres.
 * <p>
 * A senha NÃO está no código: só o resultado de PBKDF2-HMAC-SHA256 (120 mil voltas) — quem abrir o jar não a lê.
 * No ambiente de desenvolvimento a trava fica desligada (ligue com {@code -Disekai.adminAuth=true} para testar).
 */
public final class AdminAuth {
    private static final String SALT = "isekai_mahou:admin:v1";
    private static final int ROUNDS = 120_000;
    private static final String HASH = "d395e586e70e5e80b320f2ab7d65c64a074d59d551b2e393caa3a47c1c4e8554";
    private static final int MAX_TRIES = 5;
    private static final long LOCK_MS = 5 * 60_000L;

    private static final Set<UUID> AUTHED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> TRIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LOCKED_UNTIL = new ConcurrentHashMap<>();

    public static void init(IEventBus modBus) {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("admin")
                .then(Commands.literal("login").then(Commands.argument("senha", StringArgumentType.greedyString())
                        .executes(c -> login(c.getSource(), StringArgumentType.getString(c, "senha")))))
                .then(Commands.literal("logout").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    AUTHED.remove(sp.getUUID());
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.admin.logout"), false);
                    return 1;
                }))));
        IEventBus bus = NeoForge.EVENT_BUS;
        // Antes de tudo: o comando bloqueado nem chega aos outros ouvintes
        bus.addListener(EventPriority.HIGHEST, CommandEvent.class, AdminAuth::onCommand);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> AUTHED.remove(e.getEntity().getUUID()));
        bus.addListener(ServerStoppedEvent.class, e -> {
            AUTHED.clear();
            TRIES.clear();
            LOCKED_UNTIL.clear();
        });
    }

    public static boolean enabled() {
        return FMLEnvironment.production || Boolean.getBoolean("isekai.adminAuth");
    }

    /** Este jogador já digitou a senha nesta sessão? (sempre verdadeiro com a trava desligada) */
    public static boolean isAdmin(ServerPlayer sp) {
        return !enabled() || AUTHED.contains(sp.getUUID());
    }

    private static int login(CommandSourceStack src, String typed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        UUID id = sp.getUUID();
        long now = System.currentTimeMillis();
        Long lock = LOCKED_UNTIL.get(id);
        if (lock != null && now < lock) {
            src.sendFailure(Component.translatable("command.isekai_mahou.admin.locked", (lock - now) / 1000 + 1));
            return 0;
        }
        if (matches(typed)) {
            AUTHED.add(id);
            TRIES.remove(id);
            LOCKED_UNTIL.remove(id);
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.admin.ok").withStyle(ChatFormatting.GREEN), false);
            IsekaiMahou.LOGGER.info("[admin] {} entrou como administrador", sp.getGameProfile().getName());
            return 1;
        }
        int n = TRIES.merge(id, 1, Integer::sum);
        IsekaiMahou.LOGGER.warn("[admin] senha errada de {} ({}/{})", sp.getGameProfile().getName(), n, MAX_TRIES);
        if (n >= MAX_TRIES) {
            TRIES.remove(id);
            LOCKED_UNTIL.put(id, now + LOCK_MS);
        }
        src.sendFailure(Component.translatable("command.isekai_mahou.admin.wrong"));
        return 0;
    }

    private static boolean matches(String typed) {
        try {
            PBEKeySpec spec = new PBEKeySpec(typed.strip().toCharArray(), SALT.getBytes(StandardCharsets.UTF_8), ROUNDS, 256);
            byte[] got = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(got, HexFormat.of().parseHex(HASH));
        } catch (Exception e) {
            IsekaiMahou.LOGGER.error("[admin] falha ao conferir a senha", e);
            return false;
        }
    }

    private static void onCommand(CommandEvent e) {
        if (!enabled()) return;
        ParseResults<CommandSourceStack> parse = e.getParseResults();
        CommandSourceStack src = parse.getContext().getSource();
        // Só comandos digitados pelo próprio jogador: console, RCON, funções e blocos de comando passam
        if (!(src.source instanceof ServerPlayer sp) || AUTHED.contains(sp.getUUID())) return;
        if (!needsAdmin(parse.getContext(), src.withPermission(0))) return;
        // Teleporte (mapa/comando): quem responde é o guarda do mapa, com a dica das Pedras de Teleporte
        if (MapTeleportGuard.teleports(parse.getContext())) {
            MapTeleportGuard.onCommand(e);
            if (e.isCanceled()) return;
        }
        e.setCanceled(true);
        sp.sendSystemMessage(Component.translatable("command.isekai_mahou.admin.required").withStyle(ChatFormatting.RED));
    }

    /** Algum pedaço do comando (inclusive depois de "/execute ... run") é proibido para um jogador comum? */
    private static boolean needsAdmin(CommandContextBuilder<CommandSourceStack> ctx, CommandSourceStack plain) {
        for (CommandContextBuilder<CommandSourceStack> c = ctx; c != null; c = c.getChild()) {
            for (ParsedCommandNode<CommandSourceStack> n : c.getNodes()) {
                if (!n.getNode().canUse(plain)) return true;
            }
        }
        return false;
    }

    private AdminAuth() {}
}
