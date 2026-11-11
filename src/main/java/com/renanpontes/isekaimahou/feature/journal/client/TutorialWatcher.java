package com.renanpontes.isekaimahou.feature.journal.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.journal.JournalNetwork;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Passos do tutorial que só o cliente enxerga (o servidor não sabe quando uma tela abre):
 * <ul>
 *   <li>"menu": a Janela de Status (tecla B) foi aberta;</li>
 *   <li>"grimoire": a aba Grimório foi aberta;</li>
 *   <li>"spell_built": apareceu uma magia NOVA (glifos diferentes de todas as que existiam quando o jogador abriu o
 *       menu pela 1ª vez nesta sessão) num slot ou na biblioteca — ou seja, ele montou e salvou uma magia.</li>
 * </ul>
 * A "foto" das magias existentes é tirada só depois da escolha de classe (as magias prontas do núcleo e do caminho já
 * estão lá) e vale até sair do mundo: salvar e fechar o menu logo em seguida ainda conta quando a sincronização chega.
 * Cada aviso vai ao servidor uma vez (ele confere e grava o marcador {@code journal.tutorial.<chave>}).
 */
public final class TutorialWatcher {
    private static final String FLAG = "journal.tutorial.";
    private static final Set<String> SENT = new HashSet<>();
    /** Magias (listas de glifos) que existiam quando o menu abriu pela 1ª vez (null = ainda sem foto). */
    private static Set<List<ResourceLocation>> baseline;
    private static int baselineSync = -1;

    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            reset();
            return;
        }
        MagicData d = ClientMagic.data();
        if (mc.screen instanceof StatusScreen screen) {
            send(d, "menu");
            if ("grimoire".equals(screen.currentTab())) send(d, "grimoire");
            // Primeira abertura com a classe escolhida: guarda as magias que já existem.
            if (baseline == null && d.path() != MagicPath.NONE) {
                baseline = spells(d);
                baselineSync = ClientMagic.syncVersion;
                return;
            }
        }
        if (baseline == null || d.hasFlag(FLAG + "spell_built") || SENT.contains("spell_built")) return;
        // Só recalcula quando chegam dados novos do servidor (a magia salva volta pela sincronização).
        if (ClientMagic.syncVersion == baselineSync) return;
        baselineSync = ClientMagic.syncVersion;
        for (List<ResourceLocation> spell : spells(d)) {
            if (!baseline.contains(spell)) {
                send(d, "spell_built");
                return;
            }
        }
    }

    private static void send(MagicData d, String key) {
        if (d.hasFlag(FLAG + key) || !SENT.add(key)) return;
        PacketDistributor.sendToServer(new JournalNetwork.Tutorial(key));
    }

    /** Todas as magias com forma + efeito (2+ glifos) nos slots de todos os elementos e na biblioteca. */
    private static Set<List<ResourceLocation>> spells(MagicData d) {
        Set<List<ResourceLocation>> out = new HashSet<>();
        Set<Element> elements = new HashSet<>(d.elements().keySet());
        elements.add(Element.ARCANE);
        for (Element e : elements) {
            for (int l = 0; l < Progression.MAX_LOADOUTS; l++) {
                for (int s = 0; s < Progression.SLOTS; s++) {
                    SpellSlot slot = d.peekSlot(e, l, s);
                    for (SpellRecipe r : slot.all().values()) add(out, r);
                }
            }
        }
        for (SpellRecipe r : d.library()) add(out, r);
        return out;
    }

    private static void add(Set<List<ResourceLocation>> out, SpellRecipe r) {
        if (r != null && r.glyphs().size() >= 2) out.add(List.copyOf(r.glyphs()));
    }

    /** Saída do mundo: esquece o que já foi avisado (outro mundo, outros marcadores). */
    static void reset() {
        SENT.clear();
        baseline = null;
        baselineSync = -1;
    }

    private TutorialWatcher() {}
}
