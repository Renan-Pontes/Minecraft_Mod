package com.renanpontes.isekaimahou.feature.travel.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.travel.guide.GuideNet;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Pontos do mod no minimapa do <b>Xaero's Minimap</b> (aparecem também no mapa grande dele), criados sozinhos quando o
 * jogador descobre uma estrutura importante. O servidor manda a lista da dimensão atual ({@link GuideNet.Markers}); aqui
 * ela entra nos "pontos de terceiros" do Xaero por reflexão ({@code MinimapWorldManager.getCustomWaypoints(id)} — a
 * porta que o Xaero abre para outros mods; o mod não depende do Xaero para compilar nem para rodar).
 * <p>
 * São pontos "vivos": não ficam gravados nos arquivos do Xaero, são recolocados a cada sessão e trocados ao mudar de
 * dimensão, então nunca duplicam nem sobram de outro mundo. Sem o Xaero (ou com uma versão de API diferente) isto
 * simplesmente não faz nada.
 */
final class XaeroMarkers {
    private static final ResourceLocation ORIGIN = IsekaiMahou.id("places");

    private static List<GuideNet.Marker> list = List.of();
    @Nullable
    private static ResourceLocation listDim;
    private static String announce = "";
    private static boolean dirty;
    /** Sessão do minimapa e dimensão em que a lista foi aplicada por último. */
    @Nullable
    private static Object appliedSession;
    @Nullable
    private static ResourceLocation appliedDim;
    private static int timer;
    @Nullable
    private static Boolean present;
    private static boolean broken;

    // Reflexão (resolvida uma vez)
    private static Object module;
    private static Method getCurrentSession, getWorldManager, getCustomWaypoints;
    private static Constructor<?> waypointCtor;

    static void accept(GuideNet.Markers m) {
        list = m.list();
        listDim = m.dim();
        announce = m.newKey();
        dirty = true;
    }

    static void reset() {
        list = List.of();
        listDim = null;
        announce = "";
        dirty = false;
        appliedSession = null;
        appliedDim = null;
    }

    private static boolean present() {
        if (present == null) {
            ModList mods = ModList.get();
            present = mods != null && (mods.isLoaded("xaerominimap") || mods.isLoaded("xaerominimapfair"));
        }
        return present;
    }

    static void tick() {
        if (broken || !present()) return;
        if (!dirty && ++timer % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            apply(mc);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            IsekaiMahou.LOGGER.info("Isekai Mahou: API de pontos do Xaero's Minimap diferente da esperada; pontos automáticos desligados ({})",
                    e.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static void apply(Minecraft mc) throws ReflectiveOperationException {
        if (module == null) {
            module = Class.forName("xaero.hud.minimap.BuiltInHudModules").getField("MINIMAP").get(null);
            getCurrentSession = Class.forName("xaero.hud.module.HudModule").getMethod("getCurrentSession");
            getWorldManager = Class.forName("xaero.hud.minimap.module.MinimapSession").getMethod("getWorldManager");
            getCustomWaypoints = Class.forName("xaero.hud.minimap.world.MinimapWorldManager")
                    .getMethod("getCustomWaypoints", ResourceLocation.class);
            waypointCtor = Class.forName("xaero.common.minimap.waypoints.Waypoint")
                    .getConstructor(int.class, int.class, int.class, String.class, String.class, int.class);
        }
        Object session = getCurrentSession.invoke(module);
        if (session == null) return; // o minimapa ainda não abriu a sessão deste mundo: tenta de novo daqui a 1 s
        ResourceLocation here = mc.level.dimension().location();
        if (!dirty && session == appliedSession && here.equals(appliedDim)) return;

        Object manager = getWorldManager.invoke(session);
        Object raw = manager == null ? null : getCustomWaypoints.invoke(manager, ORIGIN);
        if (!(raw instanceof Map<?, ?>)) return;
        Map<Integer, Object> map = (Map<Integer, Object>) raw;
        map.clear();
        // A lista é de UMA dimensão: fora dela (troca de dimensão antes da lista nova chegar) o mapa fica sem os pontos
        boolean rightDim = here.equals(listDim);
        if (rightDim) {
            int i = 0;
            for (GuideNet.Marker m : list) {
                String name = m.name().getString();
                map.put(i++, waypointCtor.newInstance(m.pos().getX(), m.pos().getY(), m.pos().getZ(), name, symbol(m.category(), name),
                        color(m.category())));
            }
        }
        appliedSession = session;
        appliedDim = here;
        dirty = false;

        if (rightDim && !announce.isEmpty()) {
            for (GuideNet.Marker m : list) {
                if (!m.key().equals(announce)) continue;
                // Uma linha discreta na barra de ação, só para o ponto recém-descoberto
                mc.gui.setOverlayMessage(Component.translatable("map.isekai_mahou.marked", m.name()).withStyle(ChatFormatting.AQUA), false);
                break;
            }
        }
        announce = "";
    }

    /** Letra do ponto: a da categoria (traduzida) ou a inicial do nome. */
    private static String symbol(String category, String name) {
        String key = "map.isekai_mahou.symbol." + category;
        if (I18n.exists(key)) return I18n.get(key);
        return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
    }

    /** Cor do Xaero (índice 0–15, as cores de formatação do Minecraft). */
    private static int color(String category) {
        return switch (category) {
            case "city" -> 6;      // dourado
            case "portal" -> 13;   // roxo claro
            case "dungeon" -> 7;   // cinza
            case "tower" -> 11;    // água
            case "citadel" -> 4;   // vermelho escuro
            case "library" -> 9;   // azul
            case "lair" -> 12;     // vermelho
            case "sanctum" -> 14;  // amarelo
            default -> 15;
        };
    }

    private XaeroMarkers() {}
}
