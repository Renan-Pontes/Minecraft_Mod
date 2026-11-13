package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.CoreGameplay;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Aba "Status" do menu B: a "Janela de Status" estilo isekai.
 * Esquerda: perfil (retrato 3D, classe, nível + XP guardado, Círculo, recurso da classe — Mana/Vigor/Foco —, vida,
 * armadura, atributos, evolução).
 * Direita: elementos despertados (nível, XP, afinidade, nós dos caminhos), estatísticas e grimório.
 * v0.8: seção Reputação (título em uso, melhor facção com patamar e progresso, facções hostis, Procurado) e
 * interruptores dos ícones de condição, texto das reações, Pilha e avisos de reputação.
 * As duas colunas rolam com a roda do mouse quando o conteúdo não cabe.
 */
public class StatusWindowTab implements MenuTab {
    private static final int HEADER = 14;
    private static final int PAD = 6;
    private static final int ROW = 10;
    private static final int ELEMENT_ROW = 25;

    /** Coluna com rolagem. (x, y, w, h) = área visível abaixo do cabeçalho do painel. */
    private static final class Column {
        int x, y, w, h;
        int contentH;
        float scroll;

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int maxScroll() {
            return Math.max(0, contentH - h);
        }

        void scrollBy(double amount) {
            scroll = Mth.clamp(scroll - (float) amount * 16F, 0F, maxScroll());
        }

        int top() {
            return y - Math.round(scroll);
        }
    }

    private final Column left = new Column();
    private final Column right = new Column();
    /** Área clicável da linha "Opções da interface" (posição do último quadro, já com a rolagem), ou null. */
    private int[] settingsRect;
    private List<Component> tooltip;
    private int mouseX, mouseY;
    /** v1.1: Status simples por padrão (o essencial); "Ver mais detalhes" mostra atributos, evolução, estatísticas... */
    private static boolean details;
    /** Botão "Ver mais/menos" (posição do último quadro, já com a rolagem; null = fora da área visível). */
    private int[] moreRect;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        int leftW = Mth.clamp(Math.round(w * 0.42F), 150, 200);
        if (w - leftW - 6 < 170) leftW = Math.max(132, w - 6 - 170);
        left.x = x;
        left.y = y + HEADER;
        left.w = leftW;
        left.h = h - HEADER;
        right.x = x + leftW + 6;
        right.y = y + HEADER;
        right.w = w - leftW - 6;
        right.h = h - HEADER;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !left.contains(mx, my)) return false;
        if (moreRect != null && mx >= moreRect[0] && mx < moreRect[0] + moreRect[2] && my >= moreRect[1] && my < moreRect[1] + moreRect[3]) {
            details = !details;
            left.scroll = 0F;
            right.scroll = 0F;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, details ? 1.2F : 0.9F));
            return true;
        }
        if (settingsRect != null && mx >= settingsRect[0] && mx < settingsRect[0] + settingsRect[2]
                && my >= settingsRect[1] && my < settingsRect[1] + settingsRect[3]) {
            // v1.0 RELEASE: as opções moram no menu do Minecraft (Opções → Isekai Mahou); aqui é só o atalho
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            MahouSettings.open(mc.screen);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (left.contains(mx, my) && left.maxScroll() > 0) { left.scrollBy(scrollY); return true; }
        if (right.contains(mx, my) && right.maxScroll() > 0) { right.scrollBy(scrollY); return true; }
        return false;
    }

    // =====================================================================================
    //  Render
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        Font font = mc.font;
        MagicData d = ClientMagic.data();
        tooltip = null;
        settingsRect = null;
        moreRect = null;
        mouseX = mx;
        mouseY = my;
        MagicPath path = d.path();
        int accent = path == MagicPath.NONE ? HudDraw.SYS_BORDER : path.argb();

        panel(g, font, left, Component.translatable("screen.isekai_mahou.status_window.header.profile"), accent);
        panel(g, font, right, Component.translatable("screen.isekai_mahou.status_window.header.records"), HudDraw.SYS_BORDER);

        g.enableScissor(left.x + 1, left.y, left.x + left.w - 1, left.y + left.h - 1);
        int lw = left.w - PAD * 2 - (left.maxScroll() > 0 ? 4 : 0);
        left.contentH = renderProfile(g, font, player, d, left.x + PAD, left.top() + 5, lw, accent) - left.top() + 4;
        g.disableScissor();
        scrollbar(g, left, accent);

        g.enableScissor(right.x + 1, right.y, right.x + right.w - 1, right.y + right.h - 1);
        int rw = right.w - PAD * 2 - (right.maxScroll() > 0 ? 4 : 0);
        right.contentH = renderRecords(g, font, d, right.x + PAD, right.top() + 5, rw) - right.top() + 4;
        g.disableScissor();
        scrollbar(g, right, HudDraw.SYS_BORDER);

        left.scroll = Mth.clamp(left.scroll, 0F, left.maxScroll());
        right.scroll = Mth.clamp(right.scroll, 0F, right.maxScroll());
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    /** Painel "janela de sistema" com faixa de título. */
    private static void panel(GuiGraphics g, Font font, Column c, Component title, int accent) {
        int px = c.x, py = c.y - HEADER, pw = c.w, ph = c.h + HEADER;
        HudDraw.systemPanel(g, px, py, pw, ph, accent);
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + HEADER - 1, HudDraw.fade(accent, 0.45F), HudDraw.fade(accent, 0.12F));
        g.fill(px + 1, py + HEADER - 1, px + pw - 1, py + HEADER, HudDraw.fade(accent, 0.7F));
        HudDraw.diamond(g, px + 7, py + 6, 2, HudDraw.brighten(accent, 0.4F));
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), px + 13, py + 3, HudDraw.SYS_TEXT, true);
    }

    private static void scrollbar(GuiGraphics g, Column c, int accent) {
        int max = c.maxScroll();
        if (max <= 0) return;
        int tx = c.x + c.w - 4, ty = c.y + 2, th = c.h - 4;
        g.fill(tx, ty, tx + 2, ty + th, 0x40FFFFFF);
        int knob = Math.max(12, th * c.h / Math.max(1, c.contentH));
        int ky = ty + Math.round((th - knob) * (c.scroll / max));
        g.fill(tx, ky, tx + 2, ky + knob, HudDraw.fade(accent, 0.9F));
    }

    /** Define o tooltip se o mouse está no retângulo E dentro da área visível da coluna. */
    private void hover(Column c, int x, int y, int w, int h, List<Component> lines) {
        if (!c.contains(mouseX, mouseY)) return;
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) tooltip = lines;
    }

    // =====================================================================================
    //  Coluna esquerda: perfil
    // =====================================================================================
    private int renderProfile(GuiGraphics g, Font font, LocalPlayer player, MagicData d, int cx, int top, int cw, int accent) {
        MagicPath path = d.path();
        int pathColor = path == MagicPath.NONE ? 0xFF9AA4B0 : path.argb();
        int yy = top;

        // Retrato 3D (segue o mouse)
        int bw = 40, bh = 52;
        g.fill(cx, yy, cx + bw, yy + bh, 0xFF060910);
        g.fillGradient(cx + 1, yy + 1, cx + bw - 1, yy + bh - 1, HudDraw.fade(accent, 0.05F), HudDraw.fade(accent, 0.32F));
        // "círculo mágico" no chão do retrato
        g.fill(cx + 6, yy + bh - 6, cx + bw - 6, yy + bh - 5, HudDraw.fade(accent, 0.7F));
        g.fill(cx + 10, yy + bh - 5, cx + bw - 10, yy + bh - 4, HudDraw.fade(accent, 0.45F));
        g.renderOutline(cx, yy, bw, bh, HudDraw.fade(accent, 0.8F));
        HudDraw.corners(g, cx, yy, bw, bh, HudDraw.brighten(accent, 0.3F), 3);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx + 1, yy + 1, cx + bw - 1, yy + bh - 1,
                Math.round((bh - 8) / 1.8F), 0.0625F, mouseX, mouseY, player);

        // Nome, classe, estilo, título
        int tx = cx + bw + 6, tw = cw - bw - 6;
        g.drawString(font, HudDraw.ellipsize(font, player.getName().copy().withStyle(ChatFormatting.BOLD), tw), tx, yy + 2, 0xFFFFFFFF, true);
        g.drawString(font, HudDraw.ellipsize(font, path.displayName(), tw), tx, yy + 13, pathColor, true);
        Component style = path == MagicPath.NONE
                ? Component.translatable("screen.isekai_mahou.status_window.no_style")
                : HudDraw.tr("path.isekai_mahou." + path.id + ".style");
        g.drawString(font, HudDraw.ellipsize(font, style.copy().withStyle(ChatFormatting.ITALIC), tw), tx, yy + 23, HudDraw.SYS_MUTED, false);
        Component title = HudDraw.tr("screen.isekai_mahou.status_window.circle_title." + d.circle());
        g.drawString(font, HudDraw.ellipsize(font, title, tw), tx, yy + 33, HudDraw.GOLD, true);
        hover(left, tx, yy + 32, tw, 10, List.of(
                Component.translatable("screen.isekai_mahou.status_window.title_label").withStyle(ChatFormatting.GOLD),
                Component.translatable("screen.isekai_mahou.status_window.title_desc").withStyle(ChatFormatting.GRAY)));
        int rank = Math.min(HudCard.GUILD_RANKS.length, d.counter("guild_rank"));
        if (rank > 0) {
            Component r = Component.translatable("screen.isekai_mahou.status_window.guild_rank", HudCard.GUILD_RANKS[rank - 1]);
            g.drawString(font, HudDraw.ellipsize(font, r, tw), tx, yy + 43, HudCard.GUILD_RANK_COLORS[rank - 1], true);
        }
        yy += bh + 6;

        // Nível + XP
        boolean cap = d.atLevelCap();
        int next = Progression.xpToNext(d.level());
        g.drawString(font, Component.translatable("screen.isekai_mahou.status_window.level", d.level(), d.levelCap()), cx, yy, HudDraw.SYS_TEXT, true);
        Component xpText = cap ? Component.translatable("screen.isekai_mahou.status_window.xp_capped")
                : Component.translatable("screen.isekai_mahou.status_window.xp", d.xp(), next);
        int xw = font.width(xpText);
        g.drawString(font, xpText, cx + cw - xw, yy, cap ? HudDraw.GOLD : HudDraw.SYS_MUTED, false);
        yy += ROW + 1;
        float xpFrac = cap ? 1F : Math.min(1F, (float) d.xp() / next);
        if (cap) HudDraw.bar(g, cx + 1, yy, cw - 2, 4, 1F, HudDraw.GOLD_LIGHT, 0xFFD69A1E, HudDraw.fade(HudDraw.GOLD, 0.7F));
        else HudDraw.bar(g, cx + 1, yy, cw - 2, 4, xpFrac, 0xFF8FEFFF, 0xFF2A9FD6, 0xFF1E3A55);
        hover(left, cx, yy - 2, cw, 8, List.of(cap
                ? Component.translatable("screen.isekai_mahou.status_window.xp_capped_desc").withStyle(ChatFormatting.GOLD)
                : Component.translatable("screen.isekai_mahou.status_window.xp_desc", next - d.xp()).withStyle(ChatFormatting.GRAY)));
        yy += 9;
        if (d.xpBank() > 0 || cap && d.circle() < Progression.MAX_CIRCLE) yy = bankRow(g, font, d, cx, yy, cw);

        // Círculo: pips dourados
        g.drawString(font, Component.translatable("screen.isekai_mahou.status_window.circle", d.circle()), cx, yy + 1, HudDraw.GOLD, true);
        int pipX = cx + cw - Progression.MAX_CIRCLE * 8 + 1;
        for (int i = 1; i <= Progression.MAX_CIRCLE; i++) {
            int col = i <= d.circle() ? HudDraw.GOLD : 0xFF30364A;
            HudDraw.sprite(g, HudDraw.ORB_S, pipX + (i - 1) * 8, yy + 1, col);
        }
        yy += ROW + 1;
        Component unlocks = Component.translatable("screen.isekai_mahou.status_window.circle_unlocks",
                d.awakenedElements().size(), d.loadoutCount(), Progression.maxGlyphs(d.level(), d.circle()));
        if (details) {
            HudDraw.scaledText(g, font, unlocks, cx, yy, 0.75F, HudDraw.SYS_MUTED, false);
            hover(left, cx, yy - 12, cw, 20, circleTooltip(d));
            yy += 9;
        } else {
            hover(left, cx, yy - 12, cw, 11, circleTooltip(d));
            yy += 1;
        }

        // Recurso da classe (v0.9): Mana (Mágicas), Vigor (Guerreiras) ou Foco (À Distância) — o mesmo valor salvo
        int max = Math.max(1, ClientMagic.maxMana);
        ResourceStyle res = ResourceStyle.of(player);
        MagicPath.Resource resource = res.resource();
        res.icon(g, cx, yy - 1, 9, 1F);
        g.drawString(font, ResourceStyle.name(player), cx + 12, yy, res.labelColor(), true);
        String manaText = (int) Math.floor(Math.max(0F, ClientMagic.mana)) + " / " + max;
        g.drawString(font, manaText, cx + cw - font.width(manaText), yy, res.light(), false);
        List<Component> resTip = new ArrayList<>();
        resTip.add(ResourceStyle.name(player).withStyle(ChatFormatting.BOLD));
        if (path != MagicPath.NONE) {
            resTip.add(Component.translatable("screen.isekai_mahou.status_window.resource.category",
                    path.category().displayName()).withStyle(ChatFormatting.DARK_AQUA));
        }
        resTip.add(resource.description().withStyle(ChatFormatting.GRAY));
        hover(left, cx, yy - 1, cw, ROW + 6, resTip);
        yy += ROW + 1;
        HudDraw.bar(g, cx + 1, yy, cw - 2, 4, Math.max(0F, ClientMagic.mana) / max, res.fillTop(), res.fillBottom(),
                HudDraw.darken(res.frame(), 0.6F));
        yy += 10;
        // v1.0: Limite treinável (piso pelo nível + treino até o teto), treinos do dia e fadiga
        if (details) yy = limitRow(g, font, player, d, res, cx, yy, cw);

        // Vida (com o bônus de crescimento por nível) e armadura
        yy = healthRow(g, font, player, d, cx, yy, cw);
        if (details) yy = armorRow(g, font, player, cx, yy, cw);
        yy += 3;
        // v1.1: o resto (atributos, evolução, reputação, interface) só em "Ver mais detalhes"
        yy = moreRow(g, font, cx, yy, cw);
        if (!details) return yy;

        // Atributos (lidos do jogador local: equipamentos e efeitos já sincronizados)
        yy = sectionHeader(g, font, Component.translatable("screen.isekai_mahou.status_window.attributes"), cx, yy, cw, HudDraw.GOLD);
        double power = attr(player, CoreRegistries.SPELL_POWER, 1.0);
        double regenAttr = attr(player, CoreRegistries.MANA_REGEN, 1.0);
        // v1.0: o atributo carrega a escala x3 do recurso (a barra ficou 3x maior): o multiplicador mostrado é o real
        double regenMult = regenAttr / ResourceTraining.MANA_SCALE;
        int extraMana = (int) Math.round(attr(player, CoreRegistries.MAX_MANA, 0.0));
        // Exaustão Mágica corta a regeneração no servidor: mostra 0 em vermelho
        boolean exhausted = player.hasEffect(CoreRegistries.MAGIC_EXHAUSTION);
        float regenSec = exhausted ? 0F : Progression.regenPerTick(d.level(), d.circle()) * 20F * (float) regenAttr;
        boolean fatigued = !player.getAbilities().instabuild && d.fatigued(player.level().getGameTime());

        Component powerV = Component.literal(Math.round(power * 100) + "%");
        yy = attrRow(g, font, cx, yy, cw, Component.translatable("attribute.isekai_mahou.spell_power"), powerV,
                power > 1.001 ? 0xFF7CFF8A : power < 0.999 ? 0xFFFF7C7C : 0xFFFFFFFF,
                List.of(Component.translatable("attribute.isekai_mahou.spell_power").withStyle(ChatFormatting.AQUA),
                        Component.translatable("screen.isekai_mahou.status_window.attr.spell_power.desc").withStyle(ChatFormatting.GRAY)));
        Component regenV = Component.translatable("screen.isekai_mahou.status_window.per_second", String.format(Locale.ROOT, "%.1f", regenSec));
        // Vigor/Foco regeneram menos sozinhos (o modificador da classe já está no atributo): a referência é a da categoria
        Component resPlain = res.plainName();
        double baseRegen = resource.passiveRegen;
        List<Component> regenTip = new ArrayList<>();
        regenTip.add(Component.translatable("attribute.isekai_mahou.mana_regen").withStyle(ChatFormatting.AQUA));
        regenTip.add(Component.translatable("screen.isekai_mahou.status_window.attr.regen.desc_res", resPlain,
                String.format(Locale.ROOT, "%.2f", regenMult),
                String.format(Locale.ROOT, "%.1f", regenSec * Progression.MEDITATION_REGEN_MULT)).withStyle(ChatFormatting.GRAY));
        if (resource != MagicPath.Resource.MANA) {
            regenTip.add(Component.translatable("screen.isekai_mahou.status_window.attr.regen.class",
                    String.format(Locale.ROOT, "%.2f", baseRegen)).withStyle(ChatFormatting.DARK_AQUA));
            regenTip.add(resource.description().withStyle(ChatFormatting.GRAY));
        }
        if (exhausted) {
            regenTip.add(Component.translatable("screen.isekai_mahou.status_window.attr.regen.exhausted_res", resPlain)
                    .withStyle(ChatFormatting.RED));
        }
        if (fatigued) {
            regenTip.add(Component.translatable("screen.isekai_mahou.status_window.attr.regen.fatigue", resPlain,
                    StatusView.time((int) Math.max(0L, d.fatigueUntil() - player.level().getGameTime()))).withStyle(ChatFormatting.RED));
        }
        yy = attrRow(g, font, cx, yy, cw, Component.translatable("screen.isekai_mahou.status_window.attr.regen"), regenV,
                exhausted || fatigued || regenMult < baseRegen - 0.001 ? 0xFFFF7C7C : regenMult > baseRegen + 0.001 ? 0xFF7CFF8A : 0xFFFFFFFF, regenTip);
        yy = attrRow(g, font, cx, yy, cw, Component.translatable("screen.isekai_mahou.status_window.attr.extra_res", resPlain),
                Component.literal((extraMana >= 0 ? "+" : "") + extraMana), extraMana > 0 ? 0xFF7CFF8A : 0xFFFFFFFF,
                List.of(Component.translatable("attribute.isekai_mahou.max_mana").withStyle(ChatFormatting.AQUA),
                        Component.translatable("screen.isekai_mahou.status_window.attr.extra_res.desc", resPlain).withStyle(ChatFormatting.GRAY)));
        // Complexidade (v0.7): glifos por magia crescem com o nível E o Círculo; elementos por magia com o Círculo
        int nextGlyph = nextGlyphLevel(d);
        yy = attrRow(g, font, cx, yy, cw, Component.translatable("screen.isekai_mahou.status_window.attr.glyphs"),
                Component.literal(String.valueOf(Progression.maxGlyphs(d.level(), d.circle()))), 0xFF8FEFFF,
                List.of(Component.translatable("screen.isekai_mahou.status_window.attr.glyphs").withStyle(ChatFormatting.AQUA),
                        Component.translatable("screen.isekai_mahou.status_window.attr.glyphs.desc").withStyle(ChatFormatting.GRAY),
                        (nextGlyph > 0
                                ? Component.translatable("screen.isekai_mahou.status_window.circle_tip.glyphs_next", nextGlyph)
                                : Component.translatable("screen.isekai_mahou.status_window.circle_tip.glyphs_ascension")).withStyle(ChatFormatting.DARK_AQUA)));
        yy = attrRow(g, font, cx, yy, cw, Component.translatable("screen.isekai_mahou.status_window.attr.elements"),
                Component.literal(String.valueOf(Progression.maxSpellElements(d.circle()))), 0xFFFFB27A,
                List.of(Component.translatable("screen.isekai_mahou.status_window.attr.elements").withStyle(ChatFormatting.AQUA),
                        Component.translatable("screen.isekai_mahou.status_window.attr.elements.desc").withStyle(ChatFormatting.GRAY)));
        yy += 4;

        // Evolução: ritual da classe, Ascensão, ultimates e Domínio
        yy = sectionHeader(g, font, Component.translatable("screen.isekai_mahou.status_window.evolution"), cx, yy, cw, HudDraw.GOLD);
        int ritual = d.counter("ritual_rank");
        yy = attrRow(g, font, cx, yy, cw, Component.translatable("screen.isekai_mahou.status_window.ritual"),
                ritual > 0 ? Component.translatable("screen.isekai_mahou.status_window.ritual_stage", ritual)
                        : Component.translatable("screen.isekai_mahou.status_window.ritual_none"),
                ritual > 0 ? 0xFFFFFFFF : HudDraw.SYS_MUTED,
                List.of(Component.translatable("screen.isekai_mahou.status_window.ritual").withStyle(ChatFormatting.GOLD),
                        HudDraw.tr("screen.isekai_mahou.status_window.ritual.desc." + path.id).withStyle(ChatFormatting.GRAY)));

        Component asc;
        int ascColor;
        if (d.circle() >= Progression.MAX_CIRCLE) {
            asc = Component.translatable("screen.isekai_mahou.status_window.ascension_max");
            ascColor = HudDraw.GOLD;
        } else if (cap) {
            asc = Component.translatable("screen.isekai_mahou.status_window.ascension_ready");
            ascColor = HudDraw.lerp(HudDraw.pulse(1400), HudDraw.GOLD, HudDraw.GOLD_LIGHT);
        } else {
            asc = Component.translatable("screen.isekai_mahou.status_window.ascension_at", d.levelCap());
            ascColor = HudDraw.SYS_MUTED;
        }
        yy = attrRow(g, font, cx, yy, cw, Component.translatable("screen.isekai_mahou.status_window.ascension"), asc, ascColor,
                List.of(Component.translatable("screen.isekai_mahou.status_window.ascension").withStyle(ChatFormatting.GOLD),
                        Component.translatable("screen.isekai_mahou.status_window.ascension.desc").withStyle(ChatFormatting.GRAY)));
        yy = unlockRow(g, font, cx, yy, cw, "ultimates", Progression.ultimatesAllowed(d.circle()), 4);
        yy = unlockRow(g, font, cx, yy, cw, "simple_domain", Progression.simpleDomainAllowed(d.circle()), 4);
        yy = unlockRow(g, font, cx, yy, cw, "domain", Progression.domainAllowed(d.circle()), 6);
        yy += 4;

        // v0.8: Reputação — título em uso, melhor facção (patamar e progresso), facções hostis e "Procurado"
        yy = reputationSection(g, font, player, cx, yy, cw);
        yy += 4;

        // Interface — v1.0 RELEASE: "Deixe isso no menu do Minecraft, não do mod". Os interruptores saíram daqui;
        // fica só um atalho para a tela de opções (a mesma de Opções → Isekai Mahou e Mods → Config)
        yy = sectionHeader(g, font, Component.translatable("screen.isekai_mahou.status_window.interface"), cx, yy, cw, HudDraw.SYS_BORDER);
        yy = settingsRow(g, font, cx, yy, cw);
        return yy;
    }

    // =====================================================================================
    //  v0.8: Reputação (resumo; a aba "Reputação" do núcleo tem os detalhes)
    // =====================================================================================
    private int reputationSection(GuiGraphics g, Font font, LocalPlayer player, int cx, int yy, int cw) {
        yy = sectionHeader(g, font, Component.translatable("screen.isekai_mahou.status_window.reputation"), cx, yy, cw, HudDraw.GOLD);

        // Título escolhido
        MutableComponent title = Reputation.selectedTitle(player);
        int titles = Reputation.titles(player).size();
        List<Component> titleTip = new ArrayList<>();
        titleTip.add(Component.translatable("screen.isekai_mahou.status_window.rep.title").withStyle(ChatFormatting.GOLD));
        titleTip.add(Component.translatable("screen.isekai_mahou.status_window.rep.title.desc", titles).withStyle(ChatFormatting.GRAY));
        yy = attrRow(g, font, cx, yy, cw, Component.translatable("screen.isekai_mahou.status_window.rep.title"),
                title != null ? title : Component.translatable("screen.isekai_mahou.status_window.rep.no_title"),
                title != null ? HudDraw.GOLD : HudDraw.SYS_MUTED, titleTip);

        Collection<Reputation.Faction> all = Reputation.factions();
        Reputation.Faction best = all.isEmpty() ? null : Reputation.best(player);
        int bestValue = best == null ? 0 : Reputation.get(player, best.id());
        List<Component> allTip = new ArrayList<>();
        allTip.add(Component.translatable("screen.isekai_mahou.status_window.rep.all").withStyle(ChatFormatting.GOLD));
        for (Reputation.Faction f : all) {
            int v = Reputation.get(player, f.id());
            allTip.add(Component.empty().append(f.coloredName()).append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Reputation.standing(player, f.id()).displayName())
                    .append(Component.literal(" (" + (v > 0 ? "+" : "") + v + ")").withStyle(ChatFormatting.DARK_GRAY)));
        }
        allTip.add(Component.translatable("screen.isekai_mahou.status_window.rep.open_tab").withStyle(ChatFormatting.DARK_AQUA));

        if (best == null || bestValue <= 0) {
            // Ninguém conhece você ainda
            yy = attrRow(g, font, cx, yy, cw, Component.translatable("screen.isekai_mahou.status_window.rep.best"),
                    Component.translatable("screen.isekai_mahou.status_window.rep.unknown"), HudDraw.SYS_MUTED, allTip);
        } else {
            Reputation.Standing s = Reputation.standing(player, best.id());
            // Emblema + nome da facção à esquerda, patamar colorido à direita
            StatusView.emblem(g, best, cx, yy - 1, 10, 1F);
            Component st = s.displayName();
            int sw = font.width(st);
            g.drawString(font, HudDraw.ellipsize(font, best.coloredName(), cw - sw - 18), cx + 13, yy, 0xFFFFFFFF, true);
            g.drawString(font, st, cx + cw - sw, yy, 0xFFFFFFFF, true);
            hover(left, cx, yy - 1, cw, ROW, allTip);
            yy += ROW + 1;
            // Barra até o próximo patamar
            Reputation.Standing next = s.next();
            float frac;
            Component prog;
            if (next == null) {
                frac = 1F;
                prog = Component.translatable("screen.isekai_mahou.status_window.rep.max", bestValue);
            } else {
                frac = Mth.clamp((bestValue - s.min) / (float) Math.max(1, next.min - s.min), 0F, 1F);
                prog = Component.translatable("screen.isekai_mahou.status_window.rep.progress", bestValue, next.min, next.displayName());
            }
            HudDraw.bar(g, cx + 1, yy, cw - 2, 3, frac, HudDraw.brighten(best.argb(), 0.35F), HudDraw.darken(best.argb(), 0.25F), 0xFF1E2434);
            yy += 5;
            HudDraw.scaledText(g, font, prog, cx, yy, 0.75F, HudDraw.SYS_MUTED, false);
            yy += 9;
        }

        // Facções que já estão contra você
        List<Component> hostile = new ArrayList<>();
        for (Reputation.Faction f : all) if (Reputation.isHostile(player, f.id())) hostile.add(f.coloredName());
        if (!hostile.isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < hostile.size(); i++) {
                if (i > 0) names.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
                names.append(hostile.get(i));
            }
            Component label = Component.translatable("screen.isekai_mahou.status_window.rep.hostile");
            int lw = font.width(label) + 4;
            g.drawString(font, label, cx, yy, 0xFFFF7C7C, true);
            g.drawString(font, HudDraw.ellipsize(font, names, cw - lw), cx + lw, yy, 0xFFFFFFFF, false);
            List<Component> tip = new ArrayList<>();
            tip.add(label.copy().withStyle(ChatFormatting.RED));
            tip.add(names);
            tip.add(Component.translatable("screen.isekai_mahou.status_window.rep.hostile.desc").withStyle(ChatFormatting.GRAY));
            hover(left, cx, yy - 1, cw, ROW, tip);
            yy += ROW;
        }

        // Procurado (crime numa cidade)
        int wanted = StatusView.wantedTicks(player);
        if (wanted != 0) {
            int red = HudDraw.lerp(HudDraw.pulse(900), 0xFFFF4D4D, 0xFFFFB0A0);
            g.fill(cx - 1, yy - 1, cx + cw + 1, yy + 9, 0x50400808);
            g.renderOutline(cx - 1, yy - 1, cw + 2, 10, HudDraw.fade(red, 0.8F));
            HudDraw.sprite(g, HudDraw.WANTED, cx + 1, yy, red);
            g.drawString(font, Component.translatable("hud.isekai_mahou.ui_hud.wanted"), cx + 10, yy, 0xFFFFE0D8, true);
            if (wanted > 0) {
                String time = StatusView.time(wanted);
                g.drawString(font, time, cx + cw - font.width(time) - 1, yy, 0xFFFFA0A0, false);
            }
            hover(left, cx, yy - 1, cw, 10, List.of(
                    Component.translatable("hud.isekai_mahou.ui_hud.wanted").withStyle(ChatFormatting.RED),
                    Component.translatable("screen.isekai_mahou.status_window.rep.wanted.desc").withStyle(ChatFormatting.GRAY)));
            yy += ROW + 1;
        }
        return yy;
    }

    /** Botão "▾ Ver mais detalhes" / "▴ Mostrar menos" (alterna o Status simples e o completo). */
    private int moreRow(GuiGraphics g, Font font, int x, int y, int w) {
        Component label = Component.translatable(details ? "screen.isekai_mahou.status_window.less" : "screen.isekai_mahou.status_window.more");
        boolean hot = left.contains(mouseX, mouseY) && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 13;
        int c = hot ? HudDraw.GOLD : HudDraw.fade(HudDraw.GOLD, 0.75F);
        g.fill(x, y, x + w, y + 13, HudDraw.fade(HudDraw.GOLD, hot ? 0.22F : 0.1F));
        g.renderOutline(x, y, w, 13, c);
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2, y + 3, hot ? 0xFFFFFFFF : HudDraw.GOLD, true);
        if (y + 13 > left.y && y < left.y + left.h) moreRect = new int[] {x, y, w, 13};
        if (!details) {
            hover(left, x, y, w, 13, List.of(label.copy().withStyle(ChatFormatting.GOLD),
                    Component.translatable("screen.isekai_mahou.status_window.more.desc").withStyle(ChatFormatting.GRAY)));
        }
        return y + 17;
    }

    /** Atalho "Opções do mod" (abre a tela de opções do menu do Minecraft; voltar retorna para este menu). */
    private int settingsRow(GuiGraphics g, Font font, int x, int y, int w) {
        Component label = Component.translatable("screen.isekai_mahou.status_window.settings");
        boolean hot = left.contains(mouseX, mouseY) && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 13;
        int c = hot ? HudDraw.SYS_TEXT : HudDraw.fade(HudDraw.SYS_BORDER, 0.9F);
        g.fill(x, y, x + w, y + 13, HudDraw.fade(HudDraw.SYS_BORDER, hot ? 0.3F : 0.14F));
        g.renderOutline(x, y, w, 13, c);
        net.minecraft.util.FormattedCharSequence shown = HudDraw.ellipsize(font, label, w - 8);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + 3, hot ? 0xFFFFFFFF : HudDraw.SYS_TEXT, false);
        // Só é clicável a parte visível da coluna (a rolagem pode esconder a linha)
        if (y + 13 > left.y && y < left.y + left.h) settingsRect = new int[] {x, y, w, 13};
        hover(left, x, y, w, 13, List.of(label.copy().withStyle(ChatFormatting.AQUA),
                Component.translatable("screen.isekai_mahou.status_window.settings.desc").withStyle(ChatFormatting.GRAY)));
        return y + 17;
    }

    /** XP guardado no limite do Círculo (vira níveis depois da Ascensão). */
    private int bankRow(GuiGraphics g, Font font, MagicData d, int cx, int yy, int cw) {
        int bank = d.xpBank(), bankCap = Math.max(1, d.bankCap());
        boolean full = d.bankFull();
        Component label = Component.translatable("screen.isekai_mahou.status_window.xp_bank");
        Component value = Component.translatable("screen.isekai_mahou.status_window.xp_bank_value", bank, bankCap);
        int vw = font.width(value);
        g.drawString(font, HudDraw.ellipsize(font, label, cw - vw - 6), cx, yy, 0xFF8FEFFF, true);
        g.drawString(font, value, cx + cw - vw, yy, full ? HudDraw.GOLD : 0xFFC4F6FF, false);
        yy += ROW;
        HudDraw.bar(g, cx + 1, yy, cw - 2, 2, (float) bank / bankCap, full ? HudDraw.GOLD_LIGHT : 0xFFB8F6FF,
                full ? 0xFFD69A1E : 0xFF2A9FD6, 0xFF1E3A55);
        List<Component> tip = new ArrayList<>();
        tip.add(label.copy().withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("screen.isekai_mahou.status_window.xp_bank.desc").withStyle(ChatFormatting.GRAY));
        if (full) tip.add(Component.translatable("screen.isekai_mahou.status_window.xp_bank.full").withStyle(ChatFormatting.GOLD));
        hover(left, cx, yy - ROW - 1, cw, ROW + 4, tip);
        return yy + 6;
    }

    /** Vida atual/máxima com o bônus de crescimento por nível em destaque e barra (absorção em dourado). */
    private int healthRow(GuiGraphics g, Font font, LocalPlayer p, MagicData d, int cx, int yy, int cw) {
        float hp = p.getHealth(), maxHp = Math.max(1F, p.getMaxHealth()), abs = p.getAbsorptionAmount();
        AttributeInstance inst = p.getAttribute(Attributes.MAX_HEALTH);
        double base = inst != null ? inst.getBaseValue() : 20.0;
        AttributeModifier mod = inst != null ? inst.getModifier(CoreGameplay.GROWTH_HEALTH) : null;
        int growth = mod != null ? (int) Math.round(mod.amount()) : Progression.bonusHealth(d.level());
        double other = maxHp - base - growth;

        HudDraw.sprite(g, HudDraw.HEART, cx, yy, 0xFFFF6B6B);
        Component label = Component.translatable("screen.isekai_mahou.status_window.health");
        g.drawString(font, label, cx + 9, yy, 0xFFFF9A9A, true);
        MutableComponent value = Component.literal(HudDraw.num(hp) + " / " + HudDraw.num(maxHp));
        if (abs > 0.05F) value.append(Component.literal(" +" + HudDraw.num(abs)).withColor(0xFFD84F));
        int vw = font.width(value);
        g.drawString(font, value, cx + cw - vw, yy, 0xFFFFE0E0, false);
        if (growth > 0) {
            // Bônus de crescimento por nível em destaque, se couber entre o rótulo e o valor
            Component tag = Component.translatable("screen.isekai_mahou.status_window.health_growth", growth);
            float tx = cx + 9 + font.width(label) + 4;
            if (tx + font.width(tag) * 0.75F < cx + cw - vw - 3) HudDraw.scaledText(g, font, tag, tx, yy + 1.5F, 0.75F, 0xFF7CFF8A, false);
        }
        yy += ROW + 1;
        HudDraw.bar(g, cx + 1, yy, cw - 2, 4, hp / maxHp, 0xFFFF8A8A, 0xFFB01E2A, 0xFF3A1418);
        if (abs > 0.05F) g.fill(cx + 1, yy, cx + 1 + Math.round((cw - 2) * Math.min(1F, abs / maxHp)), yy + 1, 0xFFFFD84F);

        List<Component> tip = new ArrayList<>();
        tip.add(label.copy().withStyle(ChatFormatting.RED));
        tip.add(Component.translatable("screen.isekai_mahou.status_window.health.base", HudDraw.num((float) base)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("screen.isekai_mahou.status_window.health.growth", growth).withStyle(ChatFormatting.GREEN));
        if (Math.abs(other) >= 0.5) {
            String sign = other > 0 ? "+" : "";
            tip.add(Component.translatable("screen.isekai_mahou.status_window.health.other", sign + HudDraw.num((float) other))
                    .withStyle(other > 0 ? ChatFormatting.AQUA : ChatFormatting.RED));
        }
        // v1.0: o próximo nível (até 150, ou 200 depois do Dragão) em que a vida cresce — há um platô entre 75 e 100
        int nextLevel = -1;
        for (int lv = (d.level() / 5 + 1) * 5; lv <= Progression.maxLevel(d); lv += 5) {
            if (Progression.bonusHealth(lv) > Progression.bonusHealth(d.level())) {
                nextLevel = lv;
                break;
            }
        }
        if (nextLevel > 0) {
            tip.add(Component.translatable("screen.isekai_mahou.status_window.health.next", nextLevel).withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tip.add(Component.translatable("screen.isekai_mahou.status_window.health.max_growth").withStyle(ChatFormatting.GOLD));
        }
        hover(left, cx, yy - ROW - 2, cw, ROW + 7, tip);
        return yy + 8;
    }

    /**
     * v1.0 - Limite treinável do recurso: "Limite de Mana 543 / 624" com a barra dividida (piso do nível em dourado
     * escuro, parte treinada em dourado, o que falta até o teto vazio), os 3 treinos do dia e a fadiga.
     */
    private int limitRow(GuiGraphics g, Font font, LocalPlayer p, MagicData d, ResourceStyle res, int cx, int yy, int cw) {
        Minecraft mc = Minecraft.getInstance();
        int limit = d.manaLimit(), floor = d.manaFloor(), ceil = Math.max(1, d.manaCeiling());
        boolean capped = limit >= ceil;
        int today = HudState.trainingsToday(mc, d);
        int perDay = ResourceTraining.TRAININGS_PER_DAY;
        long now = p.level().getGameTime();
        boolean fatigued = !p.getAbilities().instabuild && d.fatigued(now);
        int top = yy;

        Component label = Component.translatable("screen.isekai_mahou.status_window.limit", res.plainName());
        HudDraw.sprite(g, HudDraw.UP, cx + 1, yy + 1, HudDraw.GOLD);
        HudDraw.scaledText(g, font, label, cx + 9, yy + 1F, 0.75F, 0xFFE8D9A0, true);
        Component value = capped
                ? Component.translatable("screen.isekai_mahou.status_window.limit_capped", limit)
                : Component.literal(limit + " / " + ceil);
        float vw = font.width(value) * 0.75F;
        HudDraw.scaledText(g, font, value, cx + cw - vw, yy + 1F, 0.75F, capped ? HudDraw.GOLD : 0xFFFFF0C8, true);
        yy += 9;

        // Barra: piso | treinado | falta até o teto
        int bx = cx + 1, bw = cw - 2;
        g.fill(bx - 1, yy - 1, bx + bw + 1, yy + 3, 0xFF2A2414);
        g.fill(bx, yy, bx + bw, yy + 2, 0xFF0B0F1A);
        int fw = Math.round(bw * Math.min(1F, floor / (float) ceil));
        int lw = Math.round(bw * Math.min(1F, limit / (float) ceil));
        if (fw > 0) g.fill(bx, yy, bx + fw, yy + 2, 0xFF8A6A24);
        if (lw > fw) g.fill(bx + fw, yy, bx + lw, yy + 2, HudDraw.lerp(HudDraw.pulse(1800) * 0.4F, HudDraw.GOLD, HudDraw.GOLD_LIGHT));
        yy += 5;

        // Treinos do dia (losangos) + fadiga
        Component tr = Component.translatable("screen.isekai_mahou.status_window.limit_today", today, perDay);
        HudDraw.scaledText(g, font, tr, cx + 1, yy + 1F, 0.6F, capped ? HudDraw.SYS_MUTED : 0xFFD8CFA8, false);
        int px = cx + 2 + Math.round(font.width(tr) * 0.6F) + 4;
        for (int i = 0; i < perDay; i++) {
            HudDraw.diamond(g, px + i * 6, yy + 3, 2, i < today ? HudDraw.GOLD : 0xFF4A4636);
            if (i >= today) HudDraw.diamond(g, px + i * 6, yy + 3, 1, 0xFF0A0C14);
        }
        if (fatigued) {
            Component ft = Component.translatable("screen.isekai_mahou.status_window.limit_fatigue",
                    StatusView.time((int) Math.max(0L, d.fatigueUntil() - now)));
            float ftw = font.width(ft) * 0.6F;
            HudDraw.scaledText(g, font, ft, cx + cw - ftw, yy + 1F, 0.6F, 0xFFFF9A9A, false);
        }
        yy += 8;

        List<Component> tip = new ArrayList<>();
        tip.add(label.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tip.add(Component.translatable("screen.isekai_mahou.status_window.limit.desc", res.plainName()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("screen.isekai_mahou.status_window.limit.parts", floor, Math.max(0, limit - floor), ceil)
                .withStyle(ChatFormatting.YELLOW));
        tip.add(Component.translatable("screen.isekai_mahou.status_window.limit.how", res.plainName(),
                ResourceTraining.trainGain(d.level(), d.circle()), perDay).withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.translatable("screen.isekai_mahou.status_window.limit.count", today, perDay, d.trainings())
                .withStyle(ChatFormatting.GRAY));
        int bonus = Math.max(0, ClientMagic.maxMana - limit);
        if (bonus > 0) {
            tip.add(Component.translatable("screen.isekai_mahou.status_window.limit.bonus", bonus, res.plainName())
                    .withStyle(ChatFormatting.AQUA));
        }
        if (capped) {
            tip.add(Component.translatable("screen.isekai_mahou.status_window.limit.capped_desc").withStyle(ChatFormatting.GOLD));
        }
        tip.add(Component.translatable("screen.isekai_mahou.status_window.limit.fatigue_rule").withStyle(ChatFormatting.DARK_GRAY));
        if (fatigued) {
            tip.add(Component.translatable("screen.isekai_mahou.status_window.attr.regen.fatigue", res.plainName(),
                    StatusView.time((int) Math.max(0L, d.fatigueUntil() - now))).withStyle(ChatFormatting.RED));
        }
        hover(left, cx, top - 1, cw, yy - top, tip);
        return yy + 2;
    }

    /** Armadura e dureza, com quanto de um golpe comum ela segura. */
    private int armorRow(GuiGraphics g, Font font, LocalPlayer p, int cx, int yy, int cw) {
        int armor = p.getArmorValue();
        double tough = attr(p, Attributes.ARMOR_TOUGHNESS, 0.0);
        Component label = Component.translatable("screen.isekai_mahou.status_window.armor");
        Component value = tough >= 0.05
                ? Component.translatable("screen.isekai_mahou.status_window.armor_value", armor, HudDraw.num((float) tough))
                : Component.literal(String.valueOf(armor));
        HudDraw.sprite(g, HudDraw.SHIELD, cx, yy, 0xFFB8C8DC);
        int vw = font.width(value);
        g.drawString(font, HudDraw.ellipsize(font, label, cw - vw - 15), cx + 9, yy, 0xFFC8D4E4, true);
        g.drawString(font, value, cx + cw - vw, yy, armor > 0 ? 0xFFFFFFFF : HudDraw.SYS_MUTED, false);
        // Fórmula de armadura do vanilla, para um golpe de 10 de dano
        float f = 2F + (float) tough / 4F;
        float eff = Mth.clamp(armor - 10F / f, armor * 0.2F, 20F);
        int reduction = Math.round(eff / 25F * 100F);
        hover(left, cx, yy - 1, cw, ROW, List.of(
                label.copy().withStyle(ChatFormatting.AQUA),
                Component.translatable("screen.isekai_mahou.status_window.armor.points", armor, HudDraw.num((float) tough)).withStyle(ChatFormatting.GRAY),
                Component.translatable("screen.isekai_mahou.status_window.armor.reduction", reduction).withStyle(ChatFormatting.GRAY),
                Component.translatable("screen.isekai_mahou.status_window.armor.progress").withStyle(ChatFormatting.DARK_AQUA)));
        return yy + ROW;
    }

    /** Próximo nível (dentro do limite do Círculo) que dá +1 glifo por magia, ou -1 se só depois da Ascensão. */
    private static int nextGlyphLevel(MagicData d) {
        int cur = Progression.maxGlyphs(d.level(), d.circle());
        for (int lv = d.level() + 1; lv <= d.levelCap(); lv++) {
            if (Progression.maxGlyphs(lv, d.circle()) > cur) return lv;
        }
        return -1;
    }

    private static List<Component> circleTooltip(MagicData d) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.isekai_mahou.status_window.circle", d.circle()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("screen.isekai_mahou.status_window.circle_tip.awakened", d.awakenedElements().size()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.isekai_mahou.status_window.circle_tip.loadouts", d.loadoutCount()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.isekai_mahou.status_window.circle_tip.glyphs", Progression.maxGlyphs(d.level(), d.circle())).withStyle(ChatFormatting.GRAY));
        int nextGlyph = nextGlyphLevel(d);
        lines.add((nextGlyph > 0
                ? Component.translatable("screen.isekai_mahou.status_window.circle_tip.glyphs_next", nextGlyph)
                : Component.translatable("screen.isekai_mahou.status_window.circle_tip.glyphs_ascension")).withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("screen.isekai_mahou.status_window.circle_tip.elements", Progression.maxSpellElements(d.circle())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.isekai_mahou.status_window.circle_tip.cap", d.levelCap()).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static double attr(LocalPlayer p, Holder<Attribute> holder, double fallback) {
        AttributeInstance inst = p.getAttribute(holder);
        return inst == null ? fallback : inst.getValue();
    }

    private int sectionHeader(GuiGraphics g, Font font, Component title, int x, int y, int w, int color) {
        g.drawString(font, title, x, y, color, true);
        int tw = font.width(title) + 4;
        g.fill(x + tw, y + 4, x + w, y + 5, HudDraw.fade(color, 0.45F));
        HudDraw.diamond(g, x + w - 2, y + 4, 2, HudDraw.fade(color, 0.8F));
        return y + 12;
    }

    /** Linha "rótulo ........ valor" com pontilhado e tooltip. */
    private int attrRow(GuiGraphics g, Font font, int x, int y, int w, Component label, Component value, int valueColor, List<Component> tip) {
        int vw = font.width(value);
        FormattedCharSequence lab = HudDraw.ellipsize(font, label, w - vw - 8);
        int lw = font.width(lab);
        g.drawString(font, lab, x, y, HudDraw.SYS_TEXT, false);
        for (int dx = x + lw + 3; dx < x + w - vw - 3; dx += 3) g.fill(dx, y + 6, dx + 1, y + 7, 0x40FFFFFF);
        g.drawString(font, value, x + w - vw, y, valueColor, true);
        hover(left, x, y - 1, w, ROW, tip);
        return y + ROW;
    }

    private int unlockRow(GuiGraphics g, Font font, int x, int y, int w, String key, boolean unlocked, int circle) {
        Component value = unlocked ? Component.translatable("screen.isekai_mahou.status_window.unlocked")
                : Component.translatable("screen.isekai_mahou.status_window.locked_at", circle);
        return attrRow(g, font, x, y, w, HudDraw.tr("screen.isekai_mahou.status_window.unlock." + key), value,
                unlocked ? 0xFF7CFF8A : 0xFF7A8494,
                List.of(HudDraw.tr("screen.isekai_mahou.status_window.unlock." + key).withStyle(ChatFormatting.GOLD),
                        HudDraw.tr("screen.isekai_mahou.status_window.unlock." + key + ".desc").withStyle(ChatFormatting.GRAY)));
    }

    // =====================================================================================
    //  Coluna direita: elementos, estatísticas, grimório
    // =====================================================================================
    private int renderRecords(GuiGraphics g, Font font, MagicData d, int cx, int top, int cw) {
        int yy = top;
        MagicPath path = d.path();

        int awakened = 0;
        for (Element e : Element.values()) if (d.isAwakened(e)) awakened++;
        Component elemTitle = Component.translatable("screen.isekai_mahou.status_window.elements", awakened, Element.values().length);
        yy = sectionHeaderRight(g, font, elemTitle, cx, yy, cw, HudDraw.SYS_BORDER);
        for (Element e : Element.values()) {
            if (!d.isAwakened(e)) continue;
            renderElementRow(g, font, d, path, e, cx, yy, cw);
            yy += ELEMENT_ROW;
        }
        yy += 2;

        boolean twoCols = cw >= 200;
        int colW = twoCols ? (cw - 8) / 2 : cw;
        // Estatísticas em duas colunas (v1.1: só em "Ver mais detalhes")
        if (details) {
        yy = sectionHeaderRight(g, font, Component.translatable("screen.isekai_mahou.status_window.statistics"), cx, yy, cw, HudDraw.SYS_BORDER);
        Element fav = StatusStats.favoriteElement(d);
        Component favV = fav != null ? fav.displayName() : Component.translatable("screen.isekai_mahou.status_window.none");
        yy = statRow(g, font, cx, yy, cw, Component.translatable("screen.isekai_mahou.status_window.stat.favorite"), favV,
                fav != null ? fav.argb() : HudDraw.SYS_MUTED,
                Component.translatable("screen.isekai_mahou.status_window.stat.favorite.desc"));
        List<StatusStats.Stat> stats = StatusStats.counters(d);
        for (int i = 0; i < stats.size(); i++) {
            StatusStats.Stat s = stats.get(i);
            int col = twoCols ? i % 2 : 0;
            int sx = cx + col * (colW + 8);
            statRow(g, font, sx, yy, colW, s.label(), s.value(), s.color(), s.tooltip());
            if (!twoCols || col == 1 || i == stats.size() - 1) yy += ROW;
        }
        yy += 4;
        }

        // Grimório: glifos conhecidos
        yy = sectionHeaderRight(g, font, Component.translatable("screen.isekai_mahou.status_window.grimoire"), cx, yy, cw, HudDraw.SYS_BORDER);
        int[] tot = StatusStats.glyphTotals(d);
        Component known = Component.translatable("screen.isekai_mahou.status_window.glyphs_known", tot[0], tot[1]);
        g.drawString(font, known, cx, yy, 0xFF9FF0FF, true);
        yy += ROW + 1;
        HudDraw.bar(g, cx + 1, yy, cw - 2, 3, tot[1] > 0 ? (float) tot[0] / tot[1] : 0F, 0xFFB8F6FF, 0xFF2A9FD6, 0xFF1E3A55);
        yy += 7;
        if (!details) return yy;
        GlyphKind[] kinds = GlyphKind.values();
        for (int i = 0; i < kinds.length; i++) {
            int[] k = StatusStats.glyphTotals(d, kinds[i]);
            int col = twoCols ? i % 2 : 0;
            int sx = cx + col * (colW + 8);
            statRow(g, font, sx, yy, colW, HudDraw.tr("glyph_kind.isekai_mahou." + kinds[i].name().toLowerCase(Locale.ROOT)),
                    Component.literal(k[0] + "/" + k[1]), k[0] > 0 ? 0xFFFFFFFF : HudDraw.SYS_MUTED,
                    Component.translatable("screen.isekai_mahou.status_window.kind_tip"));
            if (!twoCols || col == 1 || i == kinds.length - 1) yy += ROW;
        }
        int[] leg = StatusStats.legendaryTotals(d);
        if (leg[1] > 0) {
            yy = statRow(g, font, cx, yy, cw, Component.translatable("screen.isekai_mahou.status_window.legendary"),
                    Component.literal(leg[0] + "/" + leg[1]), leg[0] > 0 ? HudDraw.GOLD : HudDraw.SYS_MUTED,
                    Component.translatable("screen.isekai_mahou.status_window.legendary.desc"));
        }
        return yy;
    }

    private int sectionHeaderRight(GuiGraphics g, Font font, Component title, int x, int y, int w, int color) {
        g.fill(x - 2, y - 1, x + w + 2, y + 10, HudDraw.fade(color, 0.12F));
        g.fill(x - 2, y + 10, x + w + 2, y + 11, HudDraw.fade(color, 0.5F));
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x + 1, y + 1, HudDraw.SYS_TEXT, true);
        return y + 14;
    }

    private int statRow(GuiGraphics g, Font font, int x, int y, int w, Component label, Component value, int valueColor, Component tip) {
        int vw = font.width(value);
        FormattedCharSequence lab = HudDraw.ellipsize(font, label, w - vw - 6);
        g.drawString(font, lab, x, y, HudDraw.SYS_MUTED, false);
        g.drawString(font, value, x + w - vw, y, valueColor, true);
        hover(right, x, y - 1, w, ROW, List.of(label.copy().withStyle(ChatFormatting.AQUA), tip.copy().withStyle(ChatFormatting.GRAY)));
        return y + ROW;
    }

    /** Uma linha de elemento: esfera, nome, nível, afinidade; barra de XP e os nós do caminho. */
    private void renderElementRow(GuiGraphics g, Font font, MagicData d, MagicPath path, Element e, int x, int y, int w) {
        MagicData.ElementProgress prog = d.element(e);
        int xp = prog == null ? 0 : prog.xp;
        int level = Progression.elementLevel(xp);
        boolean maxed = level >= Progression.MAX_ELEMENT_LEVEL;
        int into = Progression.elementXpIntoLevel(xp);
        int toNext = Progression.elementXpToNext(level);
        // v1.0 (sem Roda): anel claro = elemento despertado (pode ser o primário das suas magias)
        boolean inWheel = e == Element.ARCANE || d.isAwakened(e);

        // Fundo da linha (destaca ao passar o mouse)
        boolean hot = right.contains(mouseX, mouseY) && mouseX >= x - 2 && mouseX < x + w + 2 && mouseY >= y - 2 && mouseY < y + ELEMENT_ROW - 3;
        g.fill(x - 2, y - 2, x + w + 2, y + ELEMENT_ROW - 3, hot ? HudDraw.fade(e.argb(), 0.16F) : 0x14FFFFFF);
        g.fill(x - 2, y - 2, x - 1, y + ELEMENT_ROW - 3, e.argb());

        // Runa do elemento (anel claro = despertado)
        GlyphArt.element(g, e, x, y - 1, 11);
        if (inWheel) HudDraw.sprite(g, HudDraw.RING, x - 1, y - 2, HudDraw.fade(0xFFFFFFFF, 0.55F));

        // Afinidade (tag colorida à direita)
        MagicPath.Affinity aff = path.affinity(e);
        int affColor = switch (aff) {
            case AFFINITY -> 0xFF6BE36B;
            case HARD -> 0xFFFF6B6B;
            default -> 0xFFB0B8C8;
        };
        Component affText = aff.displayName();
        int aw = font.width(affText);
        int ax = x + w - aw - 4;
        g.fill(ax - 1, y - 1, ax + aw + 3, y + 9, HudDraw.fade(affColor, 0.18F));
        g.renderOutline(ax - 1, y - 1, aw + 4, 10, HudDraw.fade(affColor, 0.8F));
        g.drawString(font, affText, ax + 1, y, affColor, false);
        hover(right, ax - 1, y - 1, aw + 4, 10, List.of(
                aff.displayName().withStyle(s -> s.withColor(affColor & 0xFFFFFF)),
                Component.translatable("screen.isekai_mahou.status_window.affinity_tip",
                        String.format(Locale.ROOT, "%.1f", aff.xpMult), String.format(Locale.ROOT, "%.1f", aff.featMult)).withStyle(ChatFormatting.GRAY)));

        // Nome + nível
        MutableComponent lvl = maxed ? Component.translatable("screen.isekai_mahou.status_window.element_max")
                : Component.translatable("screen.isekai_mahou.status_window.element_level", level);
        int nameMax = ax - (x + 13) - 6 - font.width(lvl) - 4;
        FormattedCharSequence nm = HudDraw.ellipsize(font, e.displayName(), Math.max(20, nameMax));
        g.drawString(font, nm, x + 13, y, e.argb(), true);
        g.drawString(font, lvl, x + 13 + font.width(nm) + 4, y, maxed ? HudDraw.GOLD : 0xFFFFFFFF, true);

        // Barra de XP do elemento + nós
        int nodesW = StatusStats.NODES.length * 6;
        int barX = x + 13, barY = y + 12, barW = w - 13 - nodesW - 6;
        float frac = maxed ? 1F : Math.min(1F, (float) into / toNext);
        HudDraw.bar(g, barX, barY, barW, 3, frac, HudDraw.brighten(e.argb(), 0.35F), HudDraw.darken(e.argb(), 0.2F), 0xFF1A2030);
        List<Component> xpTip = new ArrayList<>();
        xpTip.add(e.displayName());
        xpTip.add(maxed ? Component.translatable("screen.isekai_mahou.status_window.element_xp_max", xp).withStyle(ChatFormatting.GOLD)
                : Component.translatable("screen.isekai_mahou.status_window.element_xp", into, toNext, level + 1).withStyle(ChatFormatting.GRAY));
        xpTip.add(Component.translatable("screen.isekai_mahou.status_window.element_casts", d.counter("cast." + e.id),
                d.counter("kill.spell." + e.id)).withStyle(ChatFormatting.DARK_GRAY));
        if (inWheel) xpTip.add(Component.translatable("screen.isekai_mahou.status_window.in_wheel").withStyle(ChatFormatting.AQUA));
        hover(right, barX - 13, barY - 3, barW + 13, 8, xpTip);

        int nx = x + w - nodesW;
        for (int i = 0; i < StatusStats.NODES.length; i++) {
            String node = StatusStats.NODES[i];
            boolean has = d.hasNode(e, node);
            int px = nx + i * 6;
            HudDraw.sprite(g, has ? HudDraw.NODE_ON : HudDraw.NODE_OFF, px, barY - 1, has ? HudDraw.brighten(e.argb(), 0.25F) : 0xFF3A4256);
            List<Component> nodeTip = new ArrayList<>();
            nodeTip.add(HudDraw.tr("screen.isekai_mahou.status_window.node." + node).withStyle(has ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            nodeTip.add(Component.translatable(has ? "screen.isekai_mahou.status_window.node_reached" : "screen.isekai_mahou.status_window.node_locked")
                    .withStyle(has ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            // v0.7: os nós depois do Despertar vêm das Câmaras de Provação (rank I..VII) — diz o que falta
            int trialRank = Progression.trialRankOfNode(node);
            if (!has && trialRank > 0 && e != Element.ARCANE) {
                nodeTip.add(Component.translatable("screen.isekai_mahou.status_window.node_trial", TrialsApi.title(e, trialRank),
                        Progression.trialElementLevel(trialRank), Progression.trialCircle(trialRank)).withStyle(ChatFormatting.DARK_AQUA));
            }
            hover(right, px - 1, barY - 2, 6, 7, nodeTip);
        }
    }
}
