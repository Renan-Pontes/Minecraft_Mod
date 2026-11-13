package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Onda 9 — a <b>Transformação dentro do Grimório</b> (palavra do Renan: <i>"vamos tacar a transformação na área do
 * grimório de magias, colocar como um glifo e colocar como 'vows' lá dentro ao invés de tudo separado"</i>).
 * <p>
 * O Grimório mostra o glifo Transformação fixo no começo da paleta; clicar nele troca a aba inteira pelo editor de
 * transformações (lista de todas as transformações do jogador, efeitos/aumentos e os Votos). O editor em si mora no
 * módulo {@code transform_laws} e entra aqui por {@link #install} — assim este módulo não depende das telas dele, e
 * sem o módulo o Grimório segue igual a antes.
 */
public final class GrimoireTransform {
    /** O editor embutido: ocupa a área inteira da aba Grimório enquanto estiver aberto. */
    public interface Panel {
        void layout(int x, int y, int w, int h);

        /** Desenha o editor; devolve as linhas da dica sob o mouse (ou null). */
        @Nullable
        List<Component> render(GuiGraphics g, int mx, int my);

        boolean mouseClicked(double mx, double my, int button);

        boolean mouseScrolled(double mx, double my, double sy);
    }

    private static Supplier<Panel> factory;
    private static Predicate<MagicData> shown = d -> false;
    private static Consumer<String> selector = s -> {};
    /** O editor está aberto no lugar do Grimório (lembrado entre aberturas do menu). */
    private static boolean open;

    /** Liga o editor (parte de cliente do módulo transform_laws). */
    public static void install(Supplier<Panel> panelFactory, Predicate<MagicData> shownRule, Consumer<String> select) {
        factory = panelFactory;
        shown = shownRule == null ? d -> false : shownRule;
        selector = select == null ? s -> {} : select;
    }

    public static boolean installed() {
        return factory != null;
    }

    /** O glifo Transformação aparece na paleta para este jogador (quem já podia moldar/escolher votos)? */
    public static boolean shown(MagicData d) {
        if (factory == null) return false;
        try {
            return shown.test(d);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Nullable
    static Panel newPanel() {
        return factory == null ? null : factory.get();
    }

    public static boolean isOpen() {
        return open && factory != null;
    }

    /** Abre/fecha o editor sem trocar de tela (a aba Grimório percebe no próximo quadro). */
    public static void setOpen(boolean on) {
        open = on;
    }

    /** Volta para o Grimório de magias. */
    public static void close() {
        open = false;
    }

    /**
     * Abre o menu B no Grimório, já no editor de transformações e (se não for null) na transformação {@code slot}.
     * É o atalho dos botões "Moldar liberação" das classes e do selo "✦ Transformação".
     */
    public static void openAt(@Nullable String slot) {
        if (factory == null) {
            StatusScreen.open("grimoire");
            return;
        }
        if (slot != null) selector.accept(slot);
        open = true;
        StatusScreen.open("grimoire");
    }

    private GrimoireTransform() {}
}
