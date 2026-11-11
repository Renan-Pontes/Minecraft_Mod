package com.renanpontes.isekaimahou.client.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Registro das abas do menu B. Registrar o mesmo id de novo SUBSTITUI a aba (ex.: o módulo ui_hud troca a "status"
 * padrão).
 * <p>
 * v1.1 — menu em DOIS NÍVEIS: a linha de cima mostra só as {@link Category categorias} (Personagem, Magia, Aventura,
 * Social, Avisos) e a de baixo as abas da categoria escolhida. A categoria de cada aba vem de uma tabela central
 * ({@link #defaultCategory}) ou de {@link #setCategory}. Abas podem ainda:
 * <ul>
 *   <li>ser de CLASSE ({@link #setClassTab}; tabela central para as abas das classes do v1.0): só aparecem para quem é
 *       daquela classe;</li>
 *   <li>ficar DENTRO de outra aba ({@link #setParent}; ex.: Diário/Viagem/Bestiário dentro do "Guia de Viagem"): somem da
 *       barra e viram seções da aba-mãe ({@link ContainerTab});</li>
 *   <li>ter um predicado de visibilidade próprio ({@link #setVisibility});</li>
 *   <li>aparecer aos poucos ({@link MenuReveal}): no começo só o essencial, o resto quando faz sentido ("Nova aba!").</li>
 * </ul>
 * Ícone da aba: {@code assets/isekai_mahou/textures/gui/tab/<id>.png} (16×16) por padrão, ou o definido com
 * {@link #setIcon}. Sem textura, a barra desenha um ícone de reserva com a inicial do título.
 */
public final class MenuTabs {
    public record Entry(String id, int order, Component title, Supplier<MenuTab> factory) {}

    /** As categorias da linha de cima, na ordem. {@code iconTab} = aba cujo ícone representa a categoria. */
    public enum Category {
        CHARACTER("character", "status"),
        MAGIC("magic", "grimoire"),
        ADVENTURE("adventure", "journal"),
        SOCIAL("social", "guild"),
        NOTICES("notices", "notices");

        public final String id;
        public final String iconTab;

        Category(String id, String iconTab) {
            this.id = id;
            this.iconTab = iconTab;
        }

        public Component title() {
            return Component.translatable("screen.isekai_mahou.menu.cat." + id);
        }
    }

    private static final Map<String, Entry> TABS = new LinkedHashMap<>();
    private static final Map<String, ResourceLocation> ICONS = new HashMap<>();
    private static final Map<String, Category> CATEGORY = new HashMap<>();
    private static final Map<String, String> PARENT = new HashMap<>();
    private static final Map<String, MagicPath> CLASS_TAB = new HashMap<>();
    private static final Map<String, BooleanSupplier> VISIBLE = new HashMap<>();
    /** Ordem forçada (sobrepõe a do registro) para arrumar as abas dentro da categoria. */
    private static final Map<String, Integer> ORDER = new HashMap<>();

    static {
        // Abas de classe do v1.0 (registradas pelos módulos com ids próprios): só para quem é da classe
        CLASS_TAB.put("smith_forge", MagicPath.SMITH);
        CLASS_TAB.put("rogue", MagicPath.ROGUE);
        CLASS_TAB.put("class_knight", MagicPath.KNIGHT);
        CLASS_TAB.put("enchanter", MagicPath.ENCHANTER);
        CLASS_TAB.put("shinigami", MagicPath.SHINIGAMI);
        CLASS_TAB.put("quincy", MagicPath.QUINCY);
        CLASS_TAB.put("gunslinger_path", MagicPath.GUNSLINGER);
        CLASS_TAB.put("ranger_trail", MagicPath.RANGER);
        CLASS_TAB.put("casino", MagicPath.GAMBLER);
        CLASS_TAB.put("steelball", MagicPath.STEELBALL);
        // Guia de Viagem: Missões (Diário), Lugares (Viagem) e Bestiário viram seções de uma aba só
        PARENT.put("journal", ContainerTab.GUIDE);
        PARENT.put("travel", ContainerTab.GUIDE);
        PARENT.put("bestiary", ContainerTab.GUIDE);
        ORDER.put("journal", 0);
        ORDER.put("travel", 1);
        ORDER.put("bestiary", 2);
        ORDER.put(ContainerTab.GUIDE, 0);
        ORDER.put("trials", 10);
    }

    public static synchronized void register(String id, int order, Component title, Supplier<MenuTab> factory) {
        TABS.put(id, new Entry(id, order, title, factory));
    }

    /** Todas as abas registradas (inclusive escondidas e seções), pela ordem. */
    public static synchronized List<Entry> sorted() {
        List<Entry> list = new ArrayList<>(TABS.values());
        list.sort(Comparator.comparingInt(MenuTabs::orderOf));
        return list;
    }

    public static synchronized Entry get(String id) {
        return TABS.get(id);
    }

    private static int orderOf(Entry e) {
        return ORDER.getOrDefault(e.id(), e.order());
    }

    // ---------------------------------------------------------------- v1.1: categorias, classe, seções, visibilidade
    /** Põe a aba numa categoria (sobrepõe a tabela central). */
    public static synchronized void setCategory(String id, Category category) {
        CATEGORY.put(id, category);
    }

    /** A aba só aparece para quem é desta classe. */
    public static synchronized void setClassTab(String id, MagicPath path) {
        CLASS_TAB.put(id, path);
    }

    /** A aba vira uma seção da aba {@code parentId} (um {@link ContainerTab}). */
    public static synchronized void setParent(String id, @Nullable String parentId) {
        if (parentId == null) PARENT.remove(id);
        else PARENT.put(id, parentId);
    }

    /** Predicado extra de visibilidade (cliente). */
    public static synchronized void setVisibility(String id, BooleanSupplier visible) {
        VISIBLE.put(id, visible);
    }

    @Nullable
    public static synchronized String parentOf(String id) {
        return PARENT.get(id);
    }

    @Nullable
    public static synchronized MagicPath classOf(String id) {
        return CLASS_TAB.get(id);
    }

    /** Categoria da aba (a da mãe, se for uma seção). */
    public static synchronized Category categoryOf(String id) {
        String parent = PARENT.get(id);
        if (parent != null) return categoryOf(parent);
        Category c = CATEGORY.get(id);
        return c != null ? c : defaultCategory(id);
    }

    /** Tabela central das abas conhecidas; desconhecidas vão para Aventura. */
    public static Category defaultCategory(String id) {
        if (CLASS_TAB.containsKey(id)) return Category.CHARACTER;
        return switch (id) {
            case "status", "character", "paths" -> Category.CHARACTER;
            case "grimoire", "loadouts", "glyphs", "legends" -> Category.MAGIC;
            case "guild", "reputation", "party", "settlement" -> Category.SOCIAL;
            case "notices" -> Category.NOTICES;
            default -> Category.ADVENTURE;
        };
    }

    /**
     * A aba aparece para o jogador agora? Classe certa, predicado próprio e a revelação progressiva. Uma aba-mãe só
     * aparece se tiver alguma seção visível.
     */
    public static boolean visible(String id) {
        Entry e;
        MagicPath cls;
        BooleanSupplier pred;
        synchronized (MenuTabs.class) {
            e = TABS.get(id);
            cls = CLASS_TAB.get(id);
            pred = VISIBLE.get(id);
        }
        if (e == null) return false;
        if (cls != null && ClientMagic.data().path() != cls) return false;
        if (pred != null) {
            try {
                if (!pred.getAsBoolean()) return false;
            } catch (RuntimeException ex) {
                return false;
            }
        }
        if (isContainer(id)) return !children(id).isEmpty();
        return MenuReveal.revealed(id);
    }

    /** Alguma aba tem esta como mãe? */
    public static synchronized boolean isContainer(String id) {
        return PARENT.containsValue(id);
    }

    /** Seções VISÍVEIS de uma aba-mãe, na ordem. */
    public static List<Entry> children(String parentId) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : sorted()) {
            if (parentId.equals(parentOf(e.id())) && visible(e.id())) out.add(e);
        }
        return out;
    }

    /** Abas da barra (sem as seções) VISÍVEIS numa categoria, na ordem. */
    public static List<Entry> visibleIn(Category c) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : sorted()) {
            if (parentOf(e.id()) != null || categoryOf(e.id()) != c) continue;
            if (visible(e.id())) out.add(e);
        }
        return out;
    }

    /** Categorias com alguma aba visível (as vazias não aparecem). */
    public static List<Category> visibleCategories() {
        List<Category> out = new ArrayList<>();
        for (Category c : Category.values()) if (!visibleIn(c).isEmpty()) out.add(c);
        return out;
    }

    /** Todas as abas "folha" visíveis (seções no lugar da mãe), na ordem das categorias — para Ctrl+Tab. */
    public static List<String> visibleLeaves() {
        List<String> out = new ArrayList<>();
        for (Category c : Category.values()) {
            for (Entry e : visibleIn(c)) {
                if (isContainer(e.id())) for (Entry k : children(e.id())) out.add(k.id());
                else out.add(e.id());
            }
        }
        return out;
    }

    /** Troca o ícone da aba por outra textura (caminho completo, ex.: {@code isekai_mahou:textures/gui/tab/x.png}). */
    public static synchronized void setIcon(String id, ResourceLocation texture) {
        ICONS.put(id, texture);
    }

    /** Textura do ícone da aba (a de {@link #setIcon} ou a padrão {@code textures/gui/tab/<id>.png}). */
    public static synchronized ResourceLocation icon(String id) {
        ResourceLocation custom = ICONS.get(id);
        if (custom != null) return custom;
        // tryBuild: ids de aba com caracteres inválidos não podem derrubar o menu
        ResourceLocation def = ResourceLocation.tryBuild(IsekaiMahou.MODID, "textures/gui/tab/" + id + ".png");
        return def != null ? def : IsekaiMahou.id("textures/gui/tab/unknown.png");
    }

    private MenuTabs() {}
}
