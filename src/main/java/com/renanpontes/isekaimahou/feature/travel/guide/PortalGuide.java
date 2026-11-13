package com.renanpontes.isekaimahou.feature.travel.guide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * v1.0 — <b>Guia de Portais</b> (aba Viagem do menu B): toda dimensão do mod com "como abrir", o requisito e — quando há
 * um portal físico — as estruturas do Mundo Normal onde ele fica (para o botão "rastrear o mais próximo").
 * <p>
 * Tudo por id em texto (dimensão, estrutura, chefe, item): este módulo não depende das classes dos módulos donos das
 * dimensões. Os textos ficam em {@code guide.isekai_mahou.portals.<id>.name|site|where|how|extra}. Módulos mais novos
 * podem acrescentar a sua dimensão com {@link #register(Entry)} (no init, nos dois lados).
 * <p>
 * O estado de cada requisito é calculado no cliente a partir do {@link MagicData} sincronizado (Círculo, flags e
 * contadores), então a tela não precisa perguntar nada ao servidor — só o rastreio pergunta.
 */
public final class PortalGuide {

    /**
     * Uma dimensão do guia.
     *
     * @param id         caminho da dimensão ({@code elemental_plane})
     * @param color      cor RGB da dimensão
     * @param structures estruturas (ids do mod) onde o portal espera; vazio = sem portal físico (entrada por menu/classe)
     * @param siteDim    dimensão onde essas estruturas geram
     * @param minCircle  Círculo mínimo (0 = nenhum)
     * @param bosses     chefes que precisam ter sido vencidos (contador {@code boss.<id>})
     * @param icon       item do ícone (id completo)
     * @param hasExtra   tem uma linha extra de condição ({@code ...<id>.extra}: "só à noite", "ao meio-dia"...)
     */
    public record Entry(String id, int color, List<String> structures, ResourceKey<Level> siteDim, int minCircle,
                        List<String> bosses, String icon, boolean hasExtra) {

        public ResourceKey<Level> dimension() {
            return ResourceKey.create(Registries.DIMENSION, IsekaiMahou.id(id));
        }

        public boolean trackable() {
            return !structures.isEmpty();
        }

        public MutableComponent name() {
            return text("name").withColor(color);
        }

        /** Nome da estrutura do portal ("Portão Elemental"). */
        public MutableComponent site() {
            return text("site");
        }

        public MutableComponent where() {
            return text("where");
        }

        public MutableComponent how() {
            return text("how");
        }

        public MutableComponent extra() {
            return text("extra");
        }

        private MutableComponent text(String part) {
            return Component.translatable("guide." + IsekaiMahou.MODID + ".portals." + id + "." + part);
        }

        /** O jogador já esteve nesta dimensão? (contador de visita do núcleo ou flags do módulo dimensions) */
        public boolean visited(MagicData d) {
            return d.counter("visit." + IsekaiMahou.MODID + "." + id) > 0 || d.hasFlag("dimensions.seen." + id)
                    || d.hasFlag("dimensions.portal_opened." + id);
        }

        public boolean circleOk(MagicData d) {
            return d.circle() >= minCircle;
        }

        public boolean bossDone(MagicData d, String boss) {
            return d.counter("boss." + boss) > 0;
        }

        /** Círculo e chefes cumpridos (a oferenda e a hora são conferidas no próprio portal). */
        public boolean ready(MagicData d) {
            if (!circleOk(d)) return false;
            for (String b : bosses) if (!bossDone(d, b)) return false;
            return true;
        }
    }

    private static final Map<String, Entry> ENTRIES = Collections.synchronizedMap(new LinkedHashMap<>());

    static {
        ResourceKey<Level> ow = Level.OVERWORLD;
        add("elemental_plane", 0x7FE3FF, List.of("elemental_gate"), ow, 3, List.of(), "isekai_mahou:elemental_portal_frame", false);
        add("abyss", 0x8A4FC8, List.of("abyssal_chasm"), ow, 6, List.of(), "isekai_mahou:abyss_portal_frame", false);
        add("celestial_sanctuary", 0xFFE6A0, List.of("celestial_stairway"), ow, 6, List.of(), "isekai_mahou:celestial_beacon", true);
        add("fey_realm", 0xF29AD8, List.of("fey_ring"), ow, 0, List.of(), "isekai_mahou:fey_ring_stone", true);
        add("hollow_world", 0xB8C8E8, List.of("garganta_site"), ow, 0, List.of(), "isekai_mahou:garganta_altar", true);
        add("twilight_rift", 0xC06AA0, List.of("twilight_rupture"), ow, 9, List.of("abyss_sovereign", "seraph"),
                "isekai_mahou:twilight_rift", false);
        // Sem portal físico (de propósito): instâncias pessoais abertas pelo menu / pela classe.
        add("trial_realm", 0xE0B060, List.of(), ow, 0, List.of(), "isekai_mahou:shrine_altar", false);
        add("inner_world", 0x9FB8D8, List.of(), ow, 0, List.of(), "minecraft:iron_sword", false);
        add("endless_abyss", 0x6A4FD8, List.of(), ow, 0, List.of(), "isekai_mahou:abyss_core", false);
    }

    private static void add(String id, int color, List<String> structures, ResourceKey<Level> siteDim, int minCircle,
                            List<String> bosses, String icon, boolean hasExtra) {
        ENTRIES.put(id, new Entry(id, color, structures, siteDim, minCircle, bosses, icon, hasExtra));
    }

    /** Acrescenta (ou troca) uma dimensão do guia. Chame no init do módulo, nos dois lados. */
    public static void register(Entry entry) {
        ENTRIES.put(entry.id(), entry);
    }

    public static List<Entry> entries() {
        synchronized (ENTRIES) {
            return new ArrayList<>(ENTRIES.values());
        }
    }

    @Nullable
    public static Entry get(String id) {
        return ENTRIES.get(id);
    }

    /** Entrada cujo portal fica nesta estrutura (para os marcadores do mapa), ou null. */
    @Nullable
    public static Entry byStructure(String structurePath) {
        for (Entry e : entries()) if (e.structures().contains(structurePath)) return e;
        return null;
    }

    /** Nome de um chefe pelo id da entidade (reserva: o próprio id). */
    public static Component bossName(String id) {
        ResourceLocation rl = IsekaiMahou.id(id);
        return Component.translatableWithFallback("entity." + rl.getNamespace() + "." + rl.getPath(), id.replace('_', ' '));
    }

    private PortalGuide() {}
}
