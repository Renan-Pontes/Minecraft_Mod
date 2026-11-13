package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.core.classes.GlyphLearning;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphMastery;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.LinkGlyph;
import com.renanpontes.isekaimahou.glyph.MasteryMutations;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;
import com.renanpontes.isekaimahou.glyph.TransformTargets;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Ícones de glifo (arte do núcleo, {@link GlyphArt}) com os estados da interface (desconhecido, bloqueado,
 * selecionado, erro...) e as dicas (tooltips) de glifos e magias usadas pelas três abas.
 */
public final class GlyphIcons {
    /** Tamanho padrão dos ícones nas grades (16 = pixel art nítida nas escalas 2 e 4 da GUI). */
    public static final int SIZE = 16;

    public static final int UNKNOWN = 1;   // desconhecido: silhueta escura com "?"
    public static final int LOCKED = 2;    // conhecido, mas não pode ser usado agora (Círculo/elemento)
    public static final int SELECTED = 4;  // contorno dourado
    public static final int HOVER = 8;     // realce
    public static final int DIM = 16;      // apagado (ex.: arrastando)
    public static final int WARN = 32;     // aviso leve (ex.: adicionaria um elemento além do limite)
    public static final int ERROR = 64;    // culpado do erro da magia: moldura vermelha pulsante
    public static final int LESSON = 128;  // glifo de origem de OUTRA classe (aprendido/aprendível só por Lição): selo de pergaminho

    /** Ordem dos tipos na interface (a mesma das abas de tipo). */
    public static final GlyphKind[] KIND_ORDER = {GlyphKind.FORM, GlyphKind.EFFECT, GlyphKind.AUGMENT, GlyphKind.TRIGGER, GlyphKind.LAW};

    // ------------------------------------------------------------------ tipos
    public static boolean isLink(Glyph g) {
        return g instanceof LinkGlyph;
    }

    /** Cor do tipo (ligações têm cor própria), igual à arte do núcleo. */
    public static int kindColor(Glyph g) {
        return GlyphArt.kindColor(g.kind(), isLink(g));
    }

    public static int kindColor(GlyphKind k) {
        return GlyphArt.kindColor(k, false);
    }

    public static MutableComponent kindName(GlyphKind k) {
        return switch (k) {
            case FORM -> Component.translatable("glyph_kind.isekai_mahou.form");
            case EFFECT -> Component.translatable("glyph_kind.isekai_mahou.effect");
            case AUGMENT -> Component.translatable("glyph_kind.isekai_mahou.augment");
            case TRIGGER -> Component.translatable("glyph_kind.isekai_mahou.trigger");
            case LAW -> Component.translatable("glyph_kind.isekai_mahou.law");
        };
    }

    /** Nome do tipo do glifo ("Ligação" para as ligações, que tecnicamente são gatilhos). */
    public static MutableComponent kindName(Glyph g) {
        if (g instanceof TransformLawGlyph) return Component.translatable("glyph_kind.isekai_mahou.transform_law");
        return isLink(g) ? Component.translatable("screen.isekai_mahou.glyph.kind.link") : kindName(g.kind());
    }

    public static int kindIndex(GlyphKind k) {
        for (int i = 0; i < KIND_ORDER.length; i++) if (KIND_ORDER[i] == k) return i;
        return KIND_ORDER.length;
    }

    /** Dentro dos gatilhos: ligações primeiro, depois gatilhos de magia, por fim os de movimento. */
    private static int subOrder(Glyph g) {
        if (isLink(g)) return 0;
        if (g instanceof TriggerGlyph t && !t.isPlaceable()) return 2;
        return 1;
    }

    /** Ordenação padrão: tipo, subtipo, elemento (Origem por último), tier, nome. */
    public static final Comparator<Glyph> ORDER = Comparator.<Glyph>comparingInt(g -> kindIndex(g.kind()))
            .thenComparingInt(GlyphIcons::subOrder)
            .thenComparingInt(g -> g.origin() != null ? 100 + g.origin().ordinal() : g.element().ordinal())
            .thenComparingInt(Glyph::tier)
            .thenComparing(g -> g.displayName().getString());

    /** Cor base do glifo (cor do elemento; glifos de Origem usam a cor da classe). */
    public static int baseColor(Glyph glyph) {
        return GlyphArt.color(glyph);
    }

    // ------------------------------------------------------------------ desenho
    /** Desenha o ícone do glifo com os estados de {@code flags}. */
    public static void draw(GuiGraphics g, Glyph glyph, int x, int y, int size, int flags) {
        boolean unknown = (flags & UNKNOWN) != 0;
        if ((flags & SELECTED) != 0) {
            MahouUi.roundOutline(g, x - 2, y - 2, size + 4, size + 4, MahouUi.GOLD);
            MahouUi.roundOutline(g, x - 1, y - 1, size + 2, size + 2, MahouUi.alpha(MahouUi.GOLD_PALE, 0x90));
        } else if ((flags & HOVER) != 0) {
            MahouUi.roundOutline(g, x - 1, y - 1, size + 2, size + 2, 0xC0FFFFFF);
        }
        if (unknown) g.setColor(0.30F, 0.28F, 0.36F, 1F);
        else if ((flags & DIM) != 0) g.setColor(1F, 1F, 1F, 0.3F);
        GlyphArt.glyph(g, glyph, x, y, size);
        g.setColor(1F, 1F, 1F, 1F);

        if (unknown) {
            float s = Math.max(0.6F, size / 16F);
            Font font = Gfx.font();
            MahouUi.smallText(g, font, Component.literal("?"), x + (size - font.width("?") * s) / 2F + 0.5F, y + (size - 7 * s) / 2F, s, 0xFFC8C0DC, true);
        }
        if ((flags & LOCKED) != 0) {
            g.fill(x, y, x + size, y + size, 0x60500000);
            g.fill(x + 1, y + size - 2, x + size - 1, y + size, 0xFFE04040);
        }
        if ((flags & HOVER) != 0 && !unknown) g.fill(x, y, x + size, y + 1, 0x60FFFFFF);
        if ((flags & WARN) != 0) {
            // triângulo laranja no canto superior direito
            int tx = x + size - 5;
            g.fill(tx + 2, y, tx + 3, y + 1, 0xFFFFA040);
            g.fill(tx + 1, y + 1, tx + 4, y + 2, 0xFFFFA040);
            g.fill(tx, y + 2, tx + 5, y + 3, 0xFFFFA040);
        }
        if ((flags & LESSON) != 0) lessonBadge(g, x, y, size);
        if ((flags & ERROR) != 0) {
            float pulse = (float) (0.55 + 0.45 * Math.sin(Util.getMillis() / 140.0));
            int c = MahouUi.alpha(0xFF4848, (int) (130 + 125 * pulse));
            MahouUi.roundOutline(g, x - 1, y - 1, size + 2, size + 2, c);
            MahouUi.roundOutline(g, x - 2, y - 2, size + 4, size + 4, MahouUi.alpha(0xFF4848, (int) (60 * pulse)));
            g.fill(x, y, x + size, y + size, MahouUi.alpha(0xFF2020, (int) (40 * pulse)));
        }
    }

    // pergaminho enrolado 5×5 (selo de Lição)
    private static final String[] LESSON_SYMBOL = {"####.", "#..##", ".#..#", ".#..#", ".####"};
    private static final int LESSON_COLOR = 0xFFE7A6FF;

    /** Selo de "Lição" no canto inferior esquerdo: pergaminho lilás sobre fundo escuro. */
    public static void lessonBadge(GuiGraphics g, int x, int y, int size) {
        int bx = x, by = y + size - 7;
        g.fill(bx, by, bx + 7, by + 7, 0xE0201030);
        g.fill(bx, by, bx + 7, by + 1, 0xFF8A4FB0);
        pixels(g, LESSON_SYMBOL, bx + 1, by + 1, 1, LESSON_COLOR);
    }

    /** Glifo de origem de OUTRA classe (o jogador só aprende/aprendeu por Lição de um jogador daquela classe). */
    public static boolean isLesson(Glyph glyph, MagicData data) {
        return GlyphLearning.isForeignOrigin(glyph, data.path());
    }

    /** "Aprendido por Lição — técnica do Ferreiro" (ou "Só por Lição..." se ainda não conhece). */
    public static MutableComponent lessonLine(Glyph glyph, MagicData data) {
        Component cls = glyph.origin() == null ? Component.literal("?") : glyph.origin().displayName();
        return data.knowsGlyph(glyph.id())
                ? Component.translatable("screen.isekai_mahou.glyph.lesson_known", cls)
                : Component.translatable("screen.isekai_mahou.glyph.lesson_only", cls);
    }

    /** Ícone da categoria de classe (Mágicas/Físicas/Destreza); reserva: quadrado na cor do recurso. */
    public static void categoryIcon(GuiGraphics g, MagicPath.Category cat, int x, int y, int size) {
        ResourceLocation tex = cat.iconTexture();
        if (GlyphArt.exists(tex)) {
            GlyphArt.icon(g, tex, x, y, size);
            return;
        }
        int c = cat.resource.argb();
        MahouUi.roundRect(g, x, y, size, size, MahouUi.lighten(c, 0.2F), MahouUi.darken(c, 0.45F));
        MahouUi.roundOutline(g, x, y, size, size, MahouUi.darken(c, 0.7F));
        MahouUi.diamond(g, x + size / 2, y + size / 2, Math.max(1, size / 4), 0xFFFFFFFF);
    }

    /** Cor de destaque da categoria (a do recurso: Mana / Vigor / Foco). */
    public static int categoryColor(MagicPath.Category cat) {
        return cat.resource.argb();
    }

    /** Ícone do tipo de glifo tingido com a cor do tipo (as texturas de tipo são claras/neutras). */
    public static void kindIcon(GuiGraphics g, GlyphKind kind, boolean link, int x, int y, int size, boolean bright) {
        int c = GlyphArt.kindColor(kind, link);
        float f = bright ? 1F : 0.75F;
        g.setColor(((c >> 16) & 255) / 255F * f, ((c >> 8) & 255) / 255F * f, (c & 255) / 255F * f, 1F);
        GlyphArt.kind(g, kind, link, x, y, size);
        g.setColor(1F, 1F, 1F, 1F);
    }

    /** Ícone de "magia de Origem" da classe: quadrado na cor da classe com a estrela do núcleo. */
    public static void originIcon(GuiGraphics g, MagicPath path, int x, int y, int size) {
        if (path != null && path != MagicPath.NONE && GlyphArt.exists(path.iconTexture())) {
            GlyphArt.icon(g, path.iconTexture(), x, y, size);
            return;
        }
        int c = path == null || path == MagicPath.NONE ? 0xFFE0A85C : path.argb();
        MahouUi.roundRect(g, x, y, size, size, MahouUi.lighten(c, 0.2F), MahouUi.darken(c, 0.45F));
        MahouUi.roundOutline(g, x, y, size, size, MahouUi.darken(c, 0.7F));
        if (GlyphArt.exists(MahouUi.SPRITES) && size >= 9) {
            MahouUi.sprite(g, x + (size - 7) / 2, y + (size - 7) / 2, MahouUi.STAR_U, MahouUi.STAR_V, 7, 7);
        } else {
            MahouUi.diamond(g, x + size / 2, y + size / 2, Math.max(1, size / 4), 0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ versões de movimento
    private static final String[] NORMAL_SYMBOL = {"..###..", ".#####.", "#######", "#######", "#######", ".#####.", "..###.."};

    /** Cor de cada versão de movimento. */
    public static int triggerColor(MoveTrigger t) {
        return switch (t) {
            case NORMAL -> 0xFFE6E0F5;
            case SNEAK -> 0xFFB7A1FF;
            case SPRINT -> 0xFFFFB25C;
            case AIR -> 0xFF9FF0D0;
            case WATER -> 0xFF5CB8FF;
        };
    }

    /** Ícone de uma versão de movimento: o glifo de gatilho correspondente (Normal = ponto). */
    public static void triggerIcon(GuiGraphics g, MoveTrigger t, int x, int y, int size, boolean enabled) {
        Glyph glyph = t.glyphId() == null ? null : GlyphRegistry.get(t.glyphId());
        if (glyph != null) {
            if (!enabled) g.setColor(0.35F, 0.33F, 0.4F, 1F);
            GlyphArt.glyph(g, glyph, x, y, size);
            g.setColor(1F, 1F, 1F, 1F);
            return;
        }
        int c = enabled ? triggerColor(t) : 0xFF4A4458;
        MahouUi.roundRect(g, x, y, size, size, 0xFF2A2438, 0xFF14101E);
        MahouUi.roundOutline(g, x, y, size, size, MahouUi.darken(c, 0.4F));
        int px = Math.max(1, (size - 4) / 7);
        pixels(g, NORMAL_SYMBOL, x + (size - 7 * px) / 2, y + (size - 7 * px) / 2, px, c);
    }

    /** Desenha um padrão de pixels ("#" = aceso) em escala inteira {@code px}. */
    public static void pixels(GuiGraphics g, String[] rows, int x, int y, int px, int color) {
        Gfx.batch(g, () -> {
            for (int r = 0; r < rows.length; r++) {
                String row = rows[r];
                int start = -1;
                for (int c = 0; c <= row.length(); c++) {
                    boolean on = c < row.length() && row.charAt(c) == '#';
                    if (on && start < 0) start = c;
                    if (!on && start >= 0) {
                        g.fill(x + start * px, y + r * px, x + c * px, y + (r + 1) * px, color);
                        start = -1;
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ textos de ligação
    /** Prefixos das traduções por tipo de ligação (sufixo: hit, end, kill, leap). */
    private static final String LINK_ROLE = "screen.isekai_mahou.glyph.link_role.";
    private static final String LINK_DETAIL = "screen.isekai_mahou.glyph.link_detail.";

    /** Onde o próximo estágio nasce, por tipo de ligação (frase curta para o conector do programa). */
    public static MutableComponent linkRole(LinkGlyph.LinkKind kind) {
        return Component.translatable(LINK_ROLE + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    // ------------------------------------------------------------------ origem / tier
    private static final Map<Integer, String> TIER_NODES = Map.of(1, "awakening", 2, "apprentice", 3, "adept", 4, "master", 5, "archmage");

    /** Como o glifo é obtido (pelo tier/origem). */
    public static Component source(Glyph glyph) {
        if (glyph instanceof TransformLawGlyph tl) {
            return tl.minLevel() > 1
                    ? Component.translatable("screen.isekai_mahou.glyph.source.tlaw_level", glyph.minCircle(), tl.minLevel())
                    : Component.translatable("screen.isekai_mahou.glyph.source.tlaw", glyph.minCircle());
        }
        if (glyph.origin() != null) {
            return Component.translatable("screen.isekai_mahou.glyph.source.origin", glyph.origin().displayName());
        }
        if (glyph.element() == Element.ARCANE) {
            int level = Progression.arcaneUnlockLevel(glyph.tier());
            return glyph.minCircle() > 1
                    ? Component.translatable("screen.isekai_mahou.glyph.source.arcane_circle", level, glyph.minCircle())
                    : Component.translatable("screen.isekai_mahou.glyph.source.arcane", level);
        }
        int rank = Progression.trialRankOfNode(TIER_NODES.getOrDefault(glyph.tier(), "awakening"));
        if (rank > 0) {
            return Component.translatable("screen.isekai_mahou.glyph.source.trial", glyph.element().displayName(), nodeName(glyph.tier()),
                    TrialsApi.roman(rank));
        }
        return Component.translatable("screen.isekai_mahou.glyph.source.path", glyph.element().displayName(), nodeName(glyph.tier()));
    }

    /** "Tier N — <nó>" (glifos de elemento) ou "Tier N — Nível X" (Arcanos); Origem só "Tier N". */
    public static MutableComponent tierLabel(Glyph glyph) {
        MutableComponent t = Component.translatable("screen.isekai_mahou.glyph.tier", glyph.tier());
        if (glyph.origin() != null) return t;
        if (glyph.element() == Element.ARCANE) {
            return t.append(" — ").append(Component.translatable("screen.isekai_mahou.glyph.level", Progression.arcaneUnlockLevel(glyph.tier())));
        }
        return t.append(" — ").append(nodeName(glyph.tier()));
    }

    /** Nome do nó do caminho que libera o tier (1 Despertar ... 5 Arquimago). */
    public static MutableComponent nodeName(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 1 -> Component.translatable("screen.isekai_mahou.glyph.node.awakening");
            case 2 -> Component.translatable("screen.isekai_mahou.glyph.node.apprentice");
            case 3 -> Component.translatable("screen.isekai_mahou.glyph.node.adept");
            case 4 -> Component.translatable("screen.isekai_mahou.glyph.node.master");
            default -> Component.translatable("screen.isekai_mahou.glyph.node.archmage");
        };
    }

    // ------------------------------------------------------------------ tooltips
    /** Linha "Tipo · Elemento" (ou Origem) com as cores certas. */
    public static MutableComponent kindLine(Glyph glyph) {
        MutableComponent line = kindName(glyph).withColor(kindColor(glyph) & 0xFFFFFF)
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
        if (glyph.origin() != null) line.append(Component.translatable("screen.isekai_mahou.glyph.origin_tag", glyph.origin().displayName()));
        else line.append(glyph.element().displayName());
        return line;
    }

    /** Linhas completas da dica de um glifo. {@code data} = dados do jogador (para conhecido/Círculo). */
    public static List<Component> tooltip(Glyph glyph, MagicData data, boolean extended) {
        List<Component> lines = new ArrayList<>();
        boolean known = data.knowsGlyph(glyph.id());
        int nameColor = MahouUi.lighten(baseColor(glyph), 0.35F) & 0xFFFFFF;
        lines.add(glyph.displayName().withStyle(ChatFormatting.BOLD).withColor(nameColor));
        lines.add(kindLine(glyph));
        int cost = glyph.cost();
        lines.add(Component.translatable("screen.isekai_mahou.glyph.cost", cost > 0 ? "+" + cost : String.valueOf(cost))
                .withColor(cost < 0 ? Gfx.GOOD & 0xFFFFFF : Gfx.MANA & 0xFFFFFF));
        MutableComponent tier = tierLabel(glyph).withStyle(ChatFormatting.GRAY);
        if (glyph.isLegendary()) tier.append(" ").append(Component.translatable("screen.isekai_mahou.glyph.legendary").withColor(Gfx.GOLD & 0xFFFFFF));
        lines.add(tier);
        if (glyph.minCircle() > 1 || data.circle() < glyph.minCircle()) {
            lines.add(Component.translatable("screen.isekai_mahou.glyph.min_circle", glyph.minCircle())
                    .withStyle(data.circle() >= glyph.minCircle() ? ChatFormatting.GRAY : ChatFormatting.RED));
        }
        if (extended || glyph instanceof TransformLawGlyph) lines.addAll(details(glyph));
        lines.add(glyph.description().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_AQUA));
        if (known) lines.addAll(masteryLines(glyph, data, extended));
        if (isLesson(glyph, data)) lines.add(lessonLine(glyph, data).withColor(LESSON_COLOR & 0xFFFFFF));
        if (!known) {
            lines.add(Component.translatable("screen.isekai_mahou.glyph.unknown").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(source(glyph).copy().withStyle(ChatFormatting.DARK_GRAY));
        } else if (data.circle() < glyph.minCircle()) {
            lines.add(Component.translatable("screen.isekai_mahou.glyph.locked", glyph.minCircle()).withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    // =====================================================================================
    //  Maestria por glifo (release 1.0)
    // =====================================================================================

    /**
     * Cor da maestria pelo nível: I–III lilás apagado, IV–VI azul, VII–IX violeta, X–XIX dourado; depois do X (maestria
     * infinita): 20+ azul cósmico, 35+ verde profano, 100+ carmim.
     */
    public static int masteryColor(int level) {
        if (level >= MasteryMutations.SINGULAR) return 0xFFFF5A7A;
        if (level >= MasteryMutations.ELDRITCH) return 0xFF7CFFB0;
        if (level >= MasteryMutations.COSMIC) return 0xFF6FE8FF;
        return level >= GlyphMastery.MAX_LEVEL ? Gfx.GOLD : level >= 7 ? 0xFFC58CFF : level >= 4 ? 0xFF7FB8FF : 0xFF9A8FC0;
    }

    /**
     * Fração 0..1 para a barrinha da coleção: até o X é o caminho inteiro I→X; depois do X a barra mostra só o nível
     * atual (enche e recomeça a cada nível, na cor da faixa).
     */
    public static float masteryFraction(int xp) {
        int level = GlyphMastery.level(xp);
        if (level >= GlyphMastery.MAX_LEVEL) return GlyphMastery.progress(xp);
        return (level - 1 + GlyphMastery.progress(xp)) / (GlyphMastery.MAX_LEVEL - 1);
    }

    /**
     * Barrinha fina de maestria embaixo de um ícone de glifo (caminho I→X, cor pelo nível). Só aparece depois do
     * primeiro uso de verdade — a grade continua limpa para quem ainda não treinou nada.
     */
    public static void masteryBar(GuiGraphics g, MagicData data, Glyph glyph, int x, int y, int size) {
        int xp = data.glyphXp(glyph.id());
        if (xp <= 0 || !data.knowsGlyph(glyph.id())) return;
        int fill = Math.max(1, Math.round(size * masteryFraction(xp)));
        // depois do X o fundo da barra fica dourado apagado (a barra "dá voltas" a cada nível)
        g.fill(x, y + size + 1, x + size, y + size + 2, GlyphMastery.level(xp) >= GlyphMastery.MAX_LEVEL ? 0x90806020 : 0x70000000);
        g.fill(x, y + size + 1, x + fill, y + size + 2, masteryColor(GlyphMastery.level(xp)));
    }

    /** "−6% de custo · +4,5% de efeito" do nível dado (efeito só para glifos de EFEITO); null no nível I. */
    public static MutableComponent masteryBonus(Glyph glyph, int level) {
        if (level <= 1) return null;
        String cost = String.format(java.util.Locale.ROOT, level > GlyphMastery.MAX_LEVEL ? "%.1f" : "%.0f",
                GlyphMastery.costDiscount(level) * 100F);
        MutableComponent c = Component.translatable("screen.isekai_mahou.glyph.mastery.cost", cost);
        if (glyph.kind() == GlyphKind.EFFECT) {
            String power = String.format(java.util.Locale.ROOT, "%.1f", (GlyphMastery.powerMult(level) - 1F) * 100F);
            c.append(" · ").append(Component.translatable("screen.isekai_mahou.glyph.mastery.power", power));
        }
        return c;
    }

    /** "Rumo ao nível XII: 1,2k/11,7k" (ou "máxima" no teto do número salvo). */
    public static MutableComponent masteryProgressText(int xp) {
        int level = GlyphMastery.level(xp);
        if (level >= GlyphMastery.LEVEL_CAP) return Component.translatable("screen.isekai_mahou.glyph.mastery.maxed");
        int from = GlyphMastery.xpForLevel(level), to = GlyphMastery.xpForLevel(level + 1);
        return Component.translatable("screen.isekai_mahou.glyph.mastery.next", GlyphMastery.roman(level + 1),
                GlyphMastery.compact((long) xp - from), GlyphMastery.compact((long) to - from));
    }

    /**
     * Linhas das MUTAÇÕES (maestria infinita): as já despertas (nomes) e a PRÓXIMA como mistério — "Próxima mutação no
     * nível XVIII: ???" + uma dica vaga. Vazio para aumentos/gatilhos/leis. {@code full} = também a descrição de cada
     * mutação desperta (painel de detalhes); senão só os nomes numa linha.
     */
    public static List<Component> mutationLines(Glyph glyph, int level, boolean full) {
        List<Component> lines = new ArrayList<>();
        if (MasteryMutations.mutations(glyph).isEmpty()) return lines;
        List<MasteryMutations.Mut> on = MasteryMutations.active(glyph, level);
        if (!on.isEmpty()) {
            if (full) {
                lines.add(Component.translatable("screen.isekai_mahou.glyph.mutations", on.size()).withColor(0xC58CFF));
                for (MasteryMutations.Mut m : on) {
                    lines.add(Component.literal("✦ ").append(m.displayName()).withColor(0xE0C8FF)
                            .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(m.description().withStyle(ChatFormatting.GRAY)));
                }
            } else {
                MutableComponent names = Component.empty();
                for (int i = 0; i < on.size(); i++) {
                    if (i > 0) names.append(", ");
                    names.append(on.get(i).displayName());
                }
                lines.add(Component.literal("✦ ").append(names).withColor(0xC58CFF));
            }
        }
        MasteryMutations.Mut next = MasteryMutations.next(glyph, level);
        if (next != null) {
            lines.add(Component.translatable("screen.isekai_mahou.glyph.mutation_next", GlyphMastery.roman(next.level()))
                    .withStyle(ChatFormatting.DARK_PURPLE)
                    .append(Component.literal(" ").append(next.hint().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY))));
        }
        return lines;
    }

    /** Linhas de maestria da dica de um glifo CONHECIDO: nível + progresso, bônus atuais e (dica longa) como evoluir. */
    public static List<Component> masteryLines(Glyph glyph, MagicData data, boolean extended) {
        List<Component> lines = new ArrayList<>();
        int xp = data.glyphXp(glyph.id());
        int level = GlyphMastery.level(xp);
        int color = masteryColor(level) & 0xFFFFFF;
        MutableComponent head = Component.translatable("screen.isekai_mahou.glyph.mastery", GlyphMastery.roman(level)).withColor(color);
        if (level >= GlyphMastery.LEVEL_CAP) {
            head.append(Component.literal(" ").append(Component.translatable("screen.isekai_mahou.glyph.mastery.max")));
        } else {
            int from = GlyphMastery.xpForLevel(level), to = GlyphMastery.xpForLevel(level + 1);
            head.append(Component.literal("  " + GlyphMastery.compact((long) xp - from) + "/"
                    + GlyphMastery.compact((long) to - from)).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(head);
        MutableComponent bonus = masteryBonus(glyph, level);
        if (bonus != null) lines.add(bonus.withColor(Gfx.GOOD & 0xFFFFFF));
        // Mutações: na dica curta só a partir do X (antes disso é cedo demais para falar delas)
        if (extended || level >= GlyphMastery.MAX_LEVEL) lines.addAll(mutationLines(glyph, level, false));
        if (extended && level < GlyphMastery.MAX_LEVEL) {
            lines.add(Component.translatable("screen.isekai_mahou.glyph.mastery.how").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /** Detalhes técnicos por tipo (forma: tempos, estágios; ligação: onde o próximo estágio nasce...). */
    public static List<Component> details(Glyph glyph) {
        List<Component> lines = new ArrayList<>();
        if (glyph instanceof TransformLawGlyph tl) {
            // v1.4: Lei de Transformação — bônus (verde), preço (vermelho) e onde vai
            lines.add(Component.literal("✔ ").append(tl.boon()).withColor(0x7CE08A));
            lines.add(Component.literal("✖ ").append(tl.price()).withColor(0xFF7070));
            if (tl.minLevel() > 1) {
                lines.add(Component.translatable("screen.isekai_mahou.glyph.tlaw_min_level", tl.minLevel()).withColor(Gfx.GOLD & 0xFFFFFF));
            }
            lines.add(Component.translatable("screen.isekai_mahou.glyph.tlaw_rule").withColor(kindColor(GlyphKind.LAW) & 0xFFFFFF));
            return lines;
        }
        if (glyph instanceof FormGlyph f) {
            if (f.baseCastTicks() > 0) lines.add(Component.translatable("screen.isekai_mahou.glyph.cast_time", Gfx.seconds(f.baseCastTicks())).withStyle(ChatFormatting.GRAY));
            else lines.add(Component.translatable("screen.isekai_mahou.glyph.instant").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.isekai_mahou.glyph.base_cooldown", Gfx.seconds(f.baseCooldown())).withStyle(ChatFormatting.GRAY));
            if (f.isUltimate()) lines.add(Component.translatable("screen.isekai_mahou.glyph.ultimate").withColor(Gfx.GOLD & 0xFFFFFF));
            if (f.acceptsLaws()) lines.add(Component.translatable("screen.isekai_mahou.glyph.accepts_laws").withColor(kindColor(GlyphKind.LAW) & 0xFFFFFF));
            if (f.acceptsTransformLaws()) lines.add(Component.translatable("screen.isekai_mahou.glyph.accepts_tlaws").withColor(kindColor(GlyphKind.LAW) & 0xFFFFFF));
            if (f.isPersistent()) lines.add(Component.translatable("screen.isekai_mahou.glyph.persistent").withStyle(ChatFormatting.GRAY));
            boolean stage = f.stageable() && !f.isUltimate() && !f.acceptsLaws();
            lines.add(Component.translatable(stage ? "screen.isekai_mahou.glyph.stageable" : "screen.isekai_mahou.glyph.first_stage_only")
                    .withColor(stage ? GlyphArt.kindColor(GlyphKind.TRIGGER, true) & 0xFFFFFF : 0x9A93AD));
        } else if (glyph instanceof EffectGlyph e) {
            if (e.isBeneficial()) lines.add(Component.translatable("screen.isekai_mahou.glyph.beneficial").withStyle(ChatFormatting.GREEN));
            if (TransformTargets.isEffect(e.id())) lines.add(Component.translatable("screen.isekai_mahou.glyph.accepts_tlaws").withColor(kindColor(GlyphKind.LAW) & 0xFFFFFF));
            if (e.wantsBlocks()) lines.add(Component.translatable("screen.isekai_mahou.glyph.blocks").withStyle(ChatFormatting.GRAY));
        } else if (glyph instanceof AugmentGlyph a) {
            if (a.castTicksBonus() > 0) lines.add(Component.translatable("screen.isekai_mahou.glyph.cast_bonus", Gfx.seconds(a.castTicksBonus())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.isekai_mahou.glyph.augment_rule").withStyle(ChatFormatting.GRAY));
        } else if (glyph instanceof LinkGlyph link) {
            lines.add(Component.translatable(LINK_DETAIL + link.linkKind().name().toLowerCase(java.util.Locale.ROOT))
                    .withColor(GlyphArt.kindColor(GlyphKind.TRIGGER, true) & 0xFFFFFF));
            lines.add(Component.translatable(link.linkKind() == LinkGlyph.LinkKind.LEAP
                    ? "screen.isekai_mahou.glyph.link_rule_leap" : "screen.isekai_mahou.glyph.link_rule").withStyle(ChatFormatting.GRAY));
        } else if (glyph instanceof TriggerGlyph t) {
            lines.add(Component.translatable(t.isPlaceable() ? "screen.isekai_mahou.glyph.trigger_rule" : "screen.isekai_mahou.glyph.move_trigger")
                    .withColor(kindColor(GlyphKind.TRIGGER) & 0xFFFFFF));
        }
        return lines;
    }

    /** Quebra as linhas de dica para caber na largura. */
    public static List<FormattedCharSequence> wrap(List<Component> lines, int width) {
        Font font = Gfx.font();
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : lines) out.addAll(font.split(c, width));
        return out;
    }

    // ------------------------------------------------------------------ magias
    /** Nome curto de uma magia (nome dado, ou "Magia de <forma>"). */
    public static Component recipeTitle(SpellRecipe r) {
        if (!r.name().isBlank()) return Component.literal(r.name());
        Glyph form = r.glyphs().stream().map(GlyphRegistry::get).filter(g -> g instanceof FormGlyph).findFirst().orElse(null);
        if (form != null) return Component.translatable("screen.isekai_mahou.grimoire.unnamed_form", form.displayName());
        return Component.translatable("screen.isekai_mahou.grimoire.unnamed");
    }

    /** Lista de glifos de uma magia (nomes coloridos por tipo; ligações viram setas). */
    public static Component glyphList(SpellRecipe r) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < r.glyphs().size(); i++) {
            Glyph g = GlyphRegistry.get(r.glyphs().get(i));
            if (i > 0) out.append(Component.literal(g instanceof LinkGlyph ? " ➜ " : " › ").withStyle(ChatFormatting.DARK_GRAY));
            if (g == null) out.append(Component.literal(r.glyphs().get(i).getPath()).withStyle(ChatFormatting.RED));
            else out.append(g.displayName().withColor(kindColor(g) & 0xFFFFFF));
        }
        return out;
    }

    /** Cor de exibição de uma magia (cor própria ou do elemento). */
    public static int recipeColor(SpellRecipe r, Element fallback) {
        if (r.color() >= 0) return Gfx.opaque(r.color());
        return fallback != null ? fallback.argb() : 0xFFB98CFF;
    }

    /** Elemento de uma receita (o primário dos glifos, como o núcleo calcula); receita vazia = {@code fallback}. */
    public static Element naturalElement(SpellRecipe r, Element fallback) {
        if (r == null || r.isEmpty()) return fallback;
        return SpellCost.primaryElement(r);
    }

    /** Elemento da magia (v1.0: sempre dos glifos — mesma regra do núcleo). */
    public static Element elementOf(SpellRecipe r) {
        return r == null || r.isEmpty() ? Element.ARCANE : SpellCost.primaryElement(r);
    }

    // ------------------------------------------------------------------ v1.4: glifos aposentados
    private static java.util.Set<ResourceLocation> savedCache = java.util.Set.of();
    private static int savedStamp = Integer.MIN_VALUE;
    private static MagicData savedData;

    /**
     * O glifo aparece nas listas do Grimório? Aposentados ({@link Glyph#isHidden()}) só aparecem se o jogador ainda os
     * usa numa magia salva (slots de todos os loadouts, biblioteca, predefinições) ou no rascunho aberto.
     */
    public static boolean listed(Glyph gl, MagicData d) {
        if (!gl.isHidden()) return true;
        return savedIds(d).contains(gl.id()) || GrimoireDraft.glyphs.contains(gl.id());
    }

    /** Ids de glifo usados nas magias salvas do jogador (cache por sincronização). */
    public static java.util.Set<ResourceLocation> savedIds(MagicData d) {
        int stamp = com.renanpontes.isekaimahou.network.ClientMagic.syncVersion;
        if (stamp == savedStamp && d == savedData) return savedCache;
        java.util.Set<ResourceLocation> out = new java.util.HashSet<>();
        for (int l = 0; l < d.loadoutCount(); l++) {
            for (int s = 0; s < Progression.SLOTS; s++) {
                for (SpellRecipe r : d.peekSlot(l, s).all().values()) if (r != null) out.addAll(r.glyphs());
            }
        }
        for (SpellRecipe r : d.library()) if (r != null) out.addAll(r.glyphs());
        for (com.renanpontes.isekaimahou.glyph.SpellPreset p : d.presets()) if (p != null && p.recipe() != null) out.addAll(p.recipe().glyphs());
        savedCache = out;
        savedStamp = stamp;
        savedData = d;
        return out;
    }

    private GlyphIcons() {}
}
