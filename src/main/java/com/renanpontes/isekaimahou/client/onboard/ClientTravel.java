package com.renanpontes.isekaimahou.client.onboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.core.onboard.OnboardPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Viagem rápida no cliente: a última lista de destinos recebida do servidor e os pedidos. A tela/aba "Viagem" (módulo
 * travel) usa assim:
 * <pre>
 * ClientTravel.onMenu(menu -> mc.setScreen(new TravelScreen(menu)));     // registro no init de cliente
 * ClientTravel.requestMenu();                                           // aba do menu B pede a lista
 * ClientTravel.travel(dest.id());                                       // botão Viajar
 * ClientTravel.onOutcome((id, result) -> { if (result.success()) onClose(); });
 * </pre>
 * Um destino com {@code status() != OK} aparece desabilitado com {@code status().message()}.
 */
public final class ClientTravel {

    /** Um destino (posição = pedra ou centro do lugar; {@code cost} no recurso da classe). */
    public record Destination(String id, Component name, String type, String faction, ResourceKey<Level> dim,
                              BlockPos pos, int color, int cost, Travel.TravelResult status, boolean hub) {
        public boolean available() {
            return status == Travel.TravelResult.OK;
        }
    }

    /**
     * A lista. {@code fromStone} = aberta numa Pedra de Teleporte; {@code cooldownTicks} = recarga do menu quando a lista
     * chegou ({@link #cooldownLeft()} desconta o tempo passado); {@code resourceName/Rgb} = Mana/Vigor/Foco.
     */
    public record Menu(boolean fromStone, @Nullable BlockPos stone, int cooldownTicks, int cooldownTotal,
                       Component resourceName, int resourceRgb, List<Destination> destinations, long receivedAt) {}

    private static final List<Consumer<Menu>> MENU_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<BiConsumer<String, Travel.TravelResult>> OUTCOME_LISTENERS = new CopyOnWriteArrayList<>();
    @Nullable
    private static Menu last;
    private static int version;
    private static long clientTicks;

    /** Última lista recebida (null antes da primeira). */
    @Nullable
    public static Menu menu() {
        return last;
    }

    /** Muda a cada lista nova (telas podem reconstruir quando mudar). */
    public static int version() {
        return version;
    }

    /** Recarga do menu que falta agora (ticks), descontando o tempo desde que a lista chegou. */
    public static int cooldownLeft() {
        Menu m = last;
        if (m == null) return 0;
        return (int) Math.max(0, m.cooldownTicks() - (clientTicks - m.receivedAt()));
    }

    /** Pede a lista do menu (viagem sem pedra). A resposta chega em {@link #onMenu}. */
    public static void requestMenu() {
        if (Minecraft.getInstance().getConnection() != null) PacketDistributor.sendToServer(OnboardPayloads.TravelOpen.INSTANCE);
    }

    /** Pede a viagem até o destino (pela última lista aberta: pedra ou menu). Resposta em {@link #onOutcome}. */
    public static void travel(String placeId) {
        if (placeId != null && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new OnboardPayloads.TravelGo(placeId));
        }
    }

    /** Chamado a cada lista nova (na thread do cliente). */
    public static void onMenu(Consumer<Menu> listener) {
        if (listener != null) MENU_LISTENERS.add(listener);
    }

    /** Chamado com o resultado de cada pedido de viagem. */
    public static void onOutcome(BiConsumer<String, Travel.TravelResult> listener) {
        if (listener != null) OUTCOME_LISTENERS.add(listener);
    }

    static void receive(OnboardPayloads.TravelMenu p) {
        List<Destination> list = p.dests().stream().map(d -> new Destination(d.id(), d.name(), d.type(), d.faction(), d.dim(),
                d.pos(), d.color(), d.cost(), Travel.TravelResult.byOrdinal(d.status()), d.hub())).toList();
        last = new Menu(p.fromStone(), p.stone().orElse(null), p.cooldown(), p.cooldownTotal(), p.resourceName(), p.resourceRgb(),
                list, clientTicks);
        version++;
        for (Consumer<Menu> l : MENU_LISTENERS) {
            try {
                l.accept(last);
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.error("[travel] ouvinte da lista falhou", e);
            }
        }
    }

    static void outcome(OnboardPayloads.TravelOutcome p) {
        Travel.TravelResult r = Travel.TravelResult.byOrdinal(p.result());
        for (BiConsumer<String, Travel.TravelResult> l : OUTCOME_LISTENERS) {
            try {
                l.accept(p.placeId(), r);
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.error("[travel] ouvinte do resultado falhou", e);
            }
        }
    }

    static void tick() {
        clientTicks++;
    }

    /** Saída do mundo (os ouvintes registrados no init continuam). */
    static void clear() {
        last = null;
        version++;
    }

    /** Para telas que querem agrupar por dimensão. */
    public static Map<ResourceKey<Level>, List<Destination>> byDimension() {
        Menu m = last;
        if (m == null) return Map.of();
        LinkedHashMap<ResourceKey<Level>, List<Destination>> out = new LinkedHashMap<>();
        for (Destination d : m.destinations()) out.computeIfAbsent(d.dim(), k -> new ArrayList<>()).add(d);
        return out;
    }

    private ClientTravel() {}
}
