package com.renanpontes.isekaimahou.client.social;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.social.ClientSocialView;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.Member;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Release 1.0 — grupo melhorado: <b>amigos no mapa</b>. Os membros do grupo entram como "jogadores rastreados" no
 * minimapa e no mapa grande do Xaero (a cabeça do jogador, inclusive de quem está longe demais para o cliente ver),
 * pela porta que o Xaero abre para mods de grupo: {@code HudMod.INSTANCE.getPlayerTracker().register(nome, sistema)} no
 * minimapa e {@code WorldMap.playerTrackerSystemManager.register(nome, sistema)} no mapa.
 * <p>
 * Tudo por reflexão + {@link Proxy} (o mod não depende do Xaero para compilar nem rodar). Sem o Xaero, ou com uma API
 * diferente da esperada, isto simplesmente não faz nada.
 */
final class XaeroPartyTracker {
    private static final String NAME = "isekai_mahou_party";

    /**
     * Um membro no formato que os leitores do Xaero pedem. O MESMO objeto é reaproveitado enquanto o membro está na
     * lista (o Xaero guarda o ponto pelo objeto: trocar a cada tick faria o ponto ser recriado sem parar).
     */
    private static final class Tracked {
        final UUID id;
        volatile double x, y, z;
        volatile ResourceKey<Level> dim;

        Tracked(UUID id) {
            this.id = id;
        }
    }

    private static final Map<UUID, Tracked> CACHE = new HashMap<>();

    /** Lista atual (trocada inteira a cada tick; o Xaero a percorre no desenho). */
    private static volatile List<Tracked> tracked = List.of();
    @Nullable private static Boolean minimap, worldMap;
    private static boolean minimapDone, worldMapDone;
    private static int timer;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (minimap == null) {
            ModList mods = ModList.get();
            minimap = mods != null && (mods.isLoaded("xaerominimap") || mods.isLoaded("xaerominimapfair"));
            worldMap = mods != null && mods.isLoaded("xaeroworldmap");
        }
        if (!minimap && !worldMap) return;
        if ((!minimapDone || !worldMapDone) && timer++ % 40 == 0) register();
        refresh(mc);
    }

    static void clear() {
        tracked = List.of();
        CACHE.clear();
    }

    private static void refresh(Minecraft mc) {
        List<Member> members = ClientSocialView.partyMembers();
        if (members.isEmpty()) {
            if (!tracked.isEmpty()) clear();
            return;
        }
        UUID me = mc.player.getUUID();
        String myDim = mc.level.dimension().location().toString();
        List<Tracked> out = new ArrayList<>(members.size());
        for (Member m : members) {
            if (!m.online() || m.id().equals(me) || !m.dim().equals(myDim)) continue;
            Vec3 pos = PartyMarkers.where(mc, m.id(), 1F);
            if (pos == null) continue;
            Tracked t = CACHE.computeIfAbsent(m.id(), Tracked::new);
            t.x = pos.x;
            t.y = pos.y;
            t.z = pos.z;
            t.dim = mc.level.dimension();
            out.add(t);
        }
        if (CACHE.size() > out.size()) CACHE.values().retainAll(out);
        tracked = out;
    }

    /** Registra o sistema uma vez por sessão do jogo em cada mod do Xaero presente. */
    private static void register() {
        if (!minimapDone && Boolean.TRUE.equals(minimap)) {
            try {
                Object hud = Class.forName("xaero.common.HudMod").getField("INSTANCE").get(null);
                if (hud != null) {
                    Object manager = hud.getClass().getMethod("getPlayerTracker").invoke(hud);
                    if (manager != null) {
                        Class<?> sys = Class.forName("xaero.hud.minimap.player.tracker.system.IRenderedPlayerTracker");
                        Class<?> reader = Class.forName("xaero.hud.minimap.player.tracker.system.ITrackedPlayerReader");
                        manager.getClass().getMethod("register", String.class, sys).invoke(manager, NAME, system(sys, reader));
                        minimapDone = true;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                minimapDone = true;
                IsekaiMahou.LOGGER.info("Isekai Mahou: rastreador de jogadores do Xaero's Minimap diferente do esperado; amigos no minimapa desligados ({})",
                        e.toString());
            }
        }
        if (!worldMapDone && Boolean.TRUE.equals(worldMap)) {
            try {
                Object manager = Class.forName("xaero.map.WorldMap").getField("playerTrackerSystemManager").get(null);
                if (manager != null) {
                    Class<?> sys = Class.forName("xaero.map.radar.tracker.system.IPlayerTrackerSystem");
                    Class<?> reader = Class.forName("xaero.map.radar.tracker.system.ITrackedPlayerReader");
                    manager.getClass().getMethod("register", String.class, sys).invoke(manager, NAME, system(sys, reader));
                    worldMapDone = true;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                worldMapDone = true;
                IsekaiMahou.LOGGER.info("Isekai Mahou: rastreador de jogadores do Xaero's World Map diferente do esperado; amigos no mapa desligados ({})",
                        e.toString());
            }
        }
        if (!Boolean.TRUE.equals(minimap)) minimapDone = true;
        if (!Boolean.TRUE.equals(worldMap)) worldMapDone = true;
    }

    /** Implementa as duas interfaces do Xaero (sistema + leitor) sem compilar contra elas. */
    private static Object system(Class<?> systemType, Class<?> readerType) {
        Object reader = Proxy.newProxyInstance(readerType.getClassLoader(), new Class<?>[] {readerType}, handler((proxy, method, args) -> {
            Tracked t = args != null && args.length == 1 && args[0] instanceof Tracked tr ? tr : null;
            if (t == null) return null;
            return switch (method.getName()) {
                case "getId" -> t.id;
                case "getX" -> t.x;
                case "getY" -> t.y;
                case "getZ" -> t.z;
                case "getDimension" -> t.dim;
                default -> null;
            };
        }));
        return Proxy.newProxyInstance(systemType.getClassLoader(), new Class<?>[] {systemType}, handler((proxy, method, args) ->
                switch (method.getName()) {
                    case "getReader" -> reader;
                    case "getTrackedPlayerIterator" -> tracked.iterator();
                    default -> null;
                }));
    }

    /** Responde os métodos de {@code Object} (o Xaero guarda o sistema num mapa) e passa o resto adiante. */
    private static InvocationHandler handler(InvocationHandler inner) {
        return (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> NAME + "@" + Integer.toHexString(System.identityHashCode(proxy));
                };
            }
            return inner.invoke(proxy, method, args);
        };
    }

    private XaeroPartyTracker() {}
}
