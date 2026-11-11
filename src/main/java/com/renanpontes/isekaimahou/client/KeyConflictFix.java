package com.renanpontes.isekaimahou.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.onboard.ClientOnboarding;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * Outros mods (Xaero's Minimap usa B para "novo waypoint", por exemplo) às vezes vêm com a mesma tecla
 * de uma tecla do Isekai Mahou (Z/X/C/G, B, J, Alt...). Na primeira entrada no mundo, a tecla do OUTRO mod
 * é movida para uma tecla livre e o jogador é avisado. Cada tecla é corrigida uma única vez (lista salva em
 * config/isekai_mahou-keyfix.txt): se depois o jogador escolher de propósito a mesma tecla, a escolha dele vale.
 * O aviso no chat só aparece depois da entrada no jogo ({@link ClientOnboarding#defer}): no login, só a tela de classe.
 */
public final class KeyConflictFix {
    private static final String OUR_PREFIX = "key.isekai_mahou.";
    /** Categorias do vanilla: não mexemos nas teclas do próprio jogo. */
    private static final Set<String> VANILLA_CATEGORIES = Set.of(
            KeyMapping.CATEGORY_MOVEMENT, KeyMapping.CATEGORY_GAMEPLAY, KeyMapping.CATEGORY_INVENTORY,
            KeyMapping.CATEGORY_CREATIVE, KeyMapping.CATEGORY_MULTIPLAYER, KeyMapping.CATEGORY_INTERFACE,
            KeyMapping.CATEGORY_MISC);
    /** Teclas livres preferidas, em ordem. */
    private static final int[] CANDIDATES = {
            GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_Y,
            GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE,
            GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_KP_3 };
    private static boolean checked;

    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (checked) return;
        checked = true;
        Minecraft mc = Minecraft.getInstance();
        KeyMapping[] all = mc.options.keyMappings;
        Path file = FMLPaths.CONFIGDIR.get().resolve("isekai_mahou-keyfix.txt");
        Set<String> done = load(file);

        List<KeyMapping> ours = new ArrayList<>();
        for (KeyMapping k : all) if (k.getName().startsWith(OUR_PREFIX) && !k.isUnbound()) ours.add(k);

        List<MutableComponent> moved = new ArrayList<>();
        for (KeyMapping other : all) {
            if (other.getName().startsWith(OUR_PREFIX) || VANILLA_CATEGORIES.contains(other.getCategory())) continue;
            if (other.isUnbound() || done.contains(other.getName())) continue;
            KeyMapping clash = null;
            for (KeyMapping mine : ours) {
                // conflicts() não é simétrico (IN_GAME só conflita com IN_GAME; UNIVERSAL conflita com tudo)
                if (mine.same(other) && mine.getKeyModifier() == other.getKeyModifier()
                        && (mine.getKeyConflictContext().conflicts(other.getKeyConflictContext())
                            || other.getKeyConflictContext().conflicts(mine.getKeyConflictContext()))) {
                    clash = mine;
                    break;
                }
            }
            if (clash == null) continue;
            Component before = other.getTranslatedKeyMessage().copy();
            other.setKeyModifierAndCode(KeyModifier.NONE, freeKey(all));
            done.add(other.getName());
            moved.add(Component.translatable("message.isekai_mahou.key_moved",
                    Component.translatable(other.getName()).withStyle(ChatFormatting.WHITE),
                    before.copy().withStyle(ChatFormatting.RED),
                    other.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GREEN),
                    Component.translatable(clash.getName()).withStyle(ChatFormatting.AQUA)));
        }
        if (moved.isEmpty()) return;
        KeyMapping.resetMapping();
        mc.options.save();
        save(file, done);
        // A troca vale já; o AVISO espera a entrada no jogo acabar (escolha da classe + tutorial, ou 5 min)
        ClientOnboarding.defer(Component.translatable("message.isekai_mahou.key_moved_header").withStyle(ChatFormatting.GOLD));
        for (MutableComponent line : moved) ClientOnboarding.defer(line.withStyle(ChatFormatting.GRAY));
    }

    private static InputConstants.Key freeKey(KeyMapping[] all) {
        for (int code : CANDIDATES) {
            InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(code);
            boolean used = false;
            for (KeyMapping k : all) {
                if (k.getKey().equals(key)) { used = true; break; }
            }
            if (!used) return key;
        }
        return InputConstants.UNKNOWN;
    }

    private static Set<String> load(Path file) {
        Set<String> set = new LinkedHashSet<>();
        try {
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!line.isBlank() && !line.startsWith("#")) set.add(line.trim());
                }
            }
        } catch (IOException e) {
            IsekaiMahou.LOGGER.warn("Não consegui ler {}", file, e);
        }
        return set;
    }

    private static void save(Path file, Set<String> done) {
        List<String> lines = new ArrayList<>();
        lines.add("# Teclas de outros mods que o Isekai Mahou já moveu (uma vez cada). Apague a linha para checar de novo.");
        lines.addAll(done);
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            IsekaiMahou.LOGGER.warn("Não consegui salvar {}", file, e);
        }
    }

    private KeyConflictFix() {}
}
