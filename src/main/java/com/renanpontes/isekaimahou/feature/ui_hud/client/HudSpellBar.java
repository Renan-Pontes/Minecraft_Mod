package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.Map;

import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.LoadoutInput;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.Util;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * Cabeçalho do loadout + slots Z X C G ao lado direito da hotbar: ícone da magia, tecla, custo, varredura de recarga,
 * falta de mana, versão de movimento e carga da ultimate.
 * <p>
 * v1.0 — <b>9 loadouts fixos, sem Roda Elemental</b>: o cabeçalho mostra "Loadout 3/9", as runas dos elementos das
 * magias dele e 9 marcadores (cada um com 4 listrinhas nas cores das magias do loadout; o ativo maior e brilhando).
 * Segurando o modificador de loadout (Alt ou Mouse 5) abre o <b>seletor</b> logo acima: os 9 loadouts com as 4 magias
 * de cada um (Alt + scroll / Mouse 5 + scroll / Alt + 1..9 trocam — {@code client/LoadoutInput} do núcleo).
 * Nada desenha por cima do minimapa ({@link HudLayout}).
 */
final class HudSpellBar {
    static final int SIZE = 20;
    static final int GAP = 3;
    static final int WIDTH = Progression.SLOTS * SIZE + (Progression.SLOTS - 1) * GAP;
    /** Altura do cabeçalho do loadout (acima das teclas). */
    private static final int HEADER_H = 23;
    private static final MoveTrigger[] MOVES = {MoveTrigger.SNEAK, MoveTrigger.SPRINT, MoveTrigger.AIR, MoveTrigger.WATER};

    // Seletor de loadouts (segurando Alt / Mouse 5)
    private static final int PICK_ROW = 10;
    private static final int PICK_HEAD = 12;
    private static final int PICK_ICON = 8;
    private static long pickerSince;
    private static boolean pickerOpen;

    private HudSpellBar() {}

    static int triggerColor(MoveTrigger t) {
        return switch (t) {
            case SNEAK -> 0xFFA8F06A;
            case SPRINT -> 0xFFFFB24F;
            case AIR -> 0xFFA6ECFF;
            case WATER -> 0xFF4FA8FF;
            default -> 0xFFFFFFFF;
        };
    }

    static int[] triggerIcon(MoveTrigger t) {
        return switch (t) {
            case SNEAK -> HudDraw.ICON_SNEAK;
            case SPRINT -> HudDraw.ICON_SPRINT;
            case AIR -> HudDraw.ICON_AIR;
            default -> HudDraw.ICON_WATER;
        };
    }

    static void render(GuiGraphics g, Minecraft mc, MagicData d) {
        Player p = mc.player;
        Font font = mc.font;
        int gw = HudScale.w(g), gh = HudScale.h(g);
        int center = gw / 2;

        // Começa depois do que o vanilla desenha à direita da hotbar (mão secundária / indicador de ataque)
        HumanoidArm off = p.getMainArm().getOpposite();
        // (HudScale.v: a barra de itens é do vanilla, em pixels reais; aqui as contas são na tela virtual do HUD)
        int base = center + HudScale.v(91) + 6;
        if (off == HumanoidArm.RIGHT && !p.getOffhandItem().isEmpty()) base = center + HudScale.v(91 + 29) + 5;
        else if (off == HumanoidArm.LEFT && mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) base = center + HudScale.v(91 + 28);
        boolean fits = base + WIDTH + 3 <= gw;
        // Telas estreitas: canto direito, acima das barras de fome/ar
        int x0 = fits ? base : gw - WIDTH - 4;
        int ys = fits ? gh - 23 : gh - Math.max(SIZE + 4, HudScale.v(76));
        int blockTop = ys - 11 - HEADER_H - 3;

        // Minimapa no canto de baixo (o Xaero deixa escolher): o bloco desvia — para a esquerda dele, ou para cima
        HudLayout.Rect m = HudScale.minimap();
        if (m.intersects(x0 - 3, blockTop, WIDTH + 6, ys + SIZE + 1 - blockTop)) {
            int left = m.x() - HudLayout.MARGIN - WIDTH - 3;
            if (left >= center + HudScale.v(91) + 4 || (!fits && left >= 4)) {
                x0 = left;
            } else {
                ys = m.y() - HudLayout.MARGIN - SIZE - 1;
            }
        }

        int lo = d.activeLoadout();
        int wy = ys - 11 - 22;
        renderHeader(g, font, d, lo, x0, wy);

        float mana = Math.min(ClientMagic.mana, ClientMagic.maxMana);
        for (int s = 0; s < Progression.SLOTS; s++) {
            renderSlot(g, font, p, d, lo, s, x0 + s * (SIZE + GAP), ys, mana);
        }

        // Seletor: abre enquanto o modificador de loadout está segurado
        boolean want = mc.screen == null && LoadoutInput.modifierDown();
        if (want && !pickerOpen) pickerSince = Util.getMillis();
        pickerOpen = want;
        if (want) renderPicker(g, font, d, lo, x0, wy - 3);
    }

    // =====================================================================================
    //  Cabeçalho do loadout ("Loadout 3/9" + 9 marcadores)
    // =====================================================================================
    private static void renderHeader(GuiGraphics g, Font font, MagicData d, int lo, int x0, int wy) {
        int count = d.loadoutCount();
        LoadoutCache.Summary cur = LoadoutCache.summary(d, lo);
        int accent = cur.accent();

        // Fundo discreto com a linha na cor do loadout (a da primeira magia)
        g.fillGradient(x0 - 3, wy - 3, x0 + WIDTH + 3, wy + 20, 0x900A0E1A, 0x600A0E1A);
        g.fill(x0 - 3, wy - 3, x0 + WIDTH + 3, wy - 2, HudDraw.fade(accent, 0.9F));
        g.fill(x0 - 3, wy - 2, x0 + WIDTH + 3, wy - 1, HudDraw.fade(accent, 0.25F));

        // Trocou de loadout: lampejo na cor do novo
        float flash = HudState.loadoutFlashTicks / (float) HudState.LOADOUT_FLASH_TICKS;
        if (flash > 0F) {
            g.fill(x0 - 3, wy - 1, x0 + WIDTH + 3, wy + 20, HudDraw.fade(accent, flash * 0.35F));
            HudDraw.glow(g, x0 - 3, wy - 3, WIDTH + 6, 23, accent, flash, 3);
        }

        // 9 marcadores: 4 listrinhas nas cores das magias (vazio = só a moldura); o ativo é maior e brilha
        float pulse = HudDraw.pulse(1600);
        int pw = 8, gap = 2;
        int total = count * pw + (count - 1) * gap;
        int px0 = x0 + (WIDTH - total) / 2;
        for (int i = 0; i < count; i++) {
            int px = px0 + i * (pw + gap);
            LoadoutCache.Summary s = LoadoutCache.summary(d, i);
            boolean active = i == lo;
            int py = active ? wy - 1 : wy + 1;
            int ph = active ? 9 : 6;
            g.fill(px, py, px + pw, py + ph, 0xFF080B14);
            if (s.count() > 0) {
                for (int k = 0; k < Progression.SLOTS; k++) {
                    if (s.spells()[k] == null) continue;
                    int c = active ? s.colors()[k] : HudDraw.fade(s.colors()[k], 0.6F);
                    int sx0 = px + 1 + k * (pw - 2) / Progression.SLOTS, sx1 = px + 1 + (k + 1) * (pw - 2) / Progression.SLOTS;
                    if (sx1 > sx0) g.fill(sx0, py + 1, sx1, py + ph - 1, c);
                }
            }
            int border = active ? HudDraw.lerp(pulse, 0xFFFFFFFF, HudDraw.brighten(accent, 0.4F))
                    : s.count() > 0 ? 0xFF55607A : 0xFF2A3040;
            g.renderOutline(px, py, pw, ph, border);
            if (active) g.fill(px + 3, py + ph, px + pw - 3, py + ph + 1, border);
        }

        // "Loadout 3/9" (desliza um pouco para o lado da troca) + runas dos elementos das magias dele
        int slide = flash > 0F ? Math.round(HudState.loadoutDir * flash * flash * 6F) : 0;
        int runes = Math.min(3, cur.distinct().size());
        int runeW = runes * 9;
        Component label = Component.translatable("hud.isekai_mahou.ui_hud.loadout", lo + 1, count);
        FormattedCharSequence text = HudDraw.ellipsize(font, label, WIDTH - runeW - 2);
        int textColor = cur.count() > 0 ? HudDraw.brighten(accent, 0.35F) : 0xFFAAB4C8;
        g.drawString(font, text, x0 + slide, wy + 10, textColor, true);
        if (cur.count() == 0) {
            int tw = font.width(text);
            HudDraw.scaledText(g, font, Component.translatable("hud.isekai_mahou.ui_hud.loadout_empty"), x0 + tw + 4 + slide, wy + 11.5F,
                    0.6F, 0xFF6A7488, true);
        }
        int rx = x0 + WIDTH - runeW + 1;
        int r = 0;
        for (Element e : cur.distinct()) {
            if (r++ >= runes) break;
            GlyphArt.element(g, e, rx, wy + 10, 8);
            rx += 9;
        }
    }

    // =====================================================================================
    //  Seletor de loadouts (segurando Alt / Mouse 5)
    // =====================================================================================
    private static void renderPicker(GuiGraphics g, Font font, MagicData d, int lo, int x0, int bottom) {
        int count = d.loadoutCount();
        int pw = WIDTH + 6;
        int ph = PICK_HEAD + count * PICK_ROW + 3;
        int px = x0 - 3;
        int py = bottom - 4 - ph;
        // Nunca por cima do minimapa: vai para a esquerda dele
        HudLayout.Rect m = HudScale.minimap();
        if (m.intersects(px, py, pw, ph)) px = Math.max(4, m.x() - HudLayout.MARGIN - pw);
        if (py < 4) py = 4;

        float open = Math.min(1F, (Util.getMillis() - pickerSince) / 140F);
        float a = HudDraw.easeOut(open);
        int slideY = Math.round((1F - a) * 6F);
        py += slideY;

        HudDraw.systemPanel(g, px, py, pw, ph, HudDraw.fade(HudDraw.GOLD, a));
        // Título + dica das teclas
        Component title = Component.translatable("hud.isekai_mahou.ui_hud.picker.title");
        HudDraw.scaledText(g, font, title, px + 4, py + 3F, 0.75F, HudDraw.fade(HudDraw.GOLD_LIGHT, a), true);
        Component hint = Component.translatable("hud.isekai_mahou.ui_hud.picker.hint");
        float hw = font.width(hint) * 0.5F;
        float tw = font.width(title) * 0.75F;
        if (px + 4 + tw + 4 + hw <= px + pw - 3) {
            HudDraw.scaledText(g, font, hint, px + pw - 3 - hw, py + 4.5F, 0.5F, HudDraw.fade(0xFF8A94AA, a), false);
        }

        int iconsX = px + 12;
        for (int i = 0; i < count; i++) {
            int ry = py + PICK_HEAD + i * PICK_ROW;
            LoadoutCache.Summary s = LoadoutCache.summary(d, i);
            boolean active = i == lo;
            if (active) {
                int c = s.accent();
                g.fill(px + 2, ry - 1, px + pw - 2, ry + PICK_ROW - 1, HudDraw.fade(c, 0.28F * a));
                g.renderOutline(px + 2, ry - 1, pw - 4, PICK_ROW, HudDraw.fade(HudDraw.lerp(HudDraw.pulse(900), HudDraw.GOLD, 0xFFFFFFFF), a));
            }
            // Número (= Alt + número)
            String num = String.valueOf(i + 1);
            HudDraw.scaledText(g, font, Component.literal(num), px + 4F, ry + 1.5F, 0.75F,
                    HudDraw.fade(active ? HudDraw.GOLD_LIGHT : s.count() > 0 ? 0xFFD8E0EC : 0xFF5A6478, a), true);
            if (s.count() == 0) {
                HudDraw.scaledText(g, font, Component.translatable("hud.isekai_mahou.ui_hud.loadout_empty"), iconsX + 1F, ry + 2.5F,
                        0.5F, HudDraw.fade(0xFF5A6478, a), false);
                continue;
            }
            // As 4 magias (Z X C G) em miniatura
            int ix = iconsX;
            for (int k = 0; k < Progression.SLOTS; k++) {
                if (s.spells()[k] == null) {
                    g.fill(ix + 2, ry + 3, ix + PICK_ICON - 2, ry + PICK_ICON - 3, HudDraw.fade(0xFF2A3040, a));
                } else {
                    if (a < 1F) g.setColor(1F, 1F, 1F, a);
                    GlyphArt.spell(g, s.spells()[k], s.elements()[k], ix, ry, PICK_ICON);
                    if (a < 1F) g.setColor(1F, 1F, 1F, 1F);
                }
                ix += PICK_ICON + 2;
            }
            // Nome da 1ª magia, pequeno, no espaço que sobra
            int nameX = ix + 2;
            int room = px + pw - 4 - nameX;
            if (room > 12) {
                int first = 0;
                while (first < Progression.SLOTS && s.spells()[first] == null) first++;
                if (first < Progression.SLOTS) {
                    Component nm = LoadoutInput.spellName(s.spells()[first]);
                    FormattedCharSequence fit = HudDraw.ellipsize(font, nm, room * 2);
                    g.pose().pushPose();
                    g.pose().translate(nameX, ry + 2.5F, 0);
                    g.pose().scale(0.5F, 0.5F, 1F);
                    HudDraw.text(g, font, fit, 0, 0, HudDraw.fade(HudDraw.brighten(s.colors()[first], 0.35F), a), true);
                    g.pose().popPose();
                }
            }
        }
    }

    // =====================================================================================
    //  Slots
    // =====================================================================================
    private static void renderSlot(GuiGraphics g, Font font, Player p, MagicData d, int lo, int s, int x, int y, float mana) {
        boolean ult = s == Progression.ULTIMATE_SLOT;
        boolean locked = ult && !Progression.ultimatesAllowed(d.circle());
        LoadoutCache.Variant v = LoadoutCache.active(s, p, d);
        String key = MagicData.slotKey(lo, s);
        int left = ClientMagic.cooldownLeft.getOrDefault(key, 0);
        int total = Math.max(1, ClientMagic.cooldownTotal.getOrDefault(key, 1));
        boolean casting = HudState.casting && HudState.castSlot == s;
        boolean valid = v != null && v.valid();
        boolean creative = p.getAbilities().instabuild;
        boolean charged = d.charge() >= Progression.MAX_CHARGE || creative;
        boolean affordable = valid && LoadoutCache.canPay(v, p, mana);
        // Silenciado / Exaustão Mágica: o servidor recusa qualquer magia (mesma checagem do SpellCaster.beginCast)
        boolean sealed = p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION);
        int color = v != null ? v.color() : 0xFF3A4150;
        float pulse = HudDraw.pulse(900);

        // Tecla acima
        Component keyName = CoreKeys.SLOTS[s].getTranslatedKeyMessage();
        FormattedCharSequence keyText = HudDraw.ellipsize(font, keyName, SIZE + GAP);
        int kw = font.width(keyText);
        int keyColor = locked ? 0xFF5A6070 : v != null ? 0xFFE6EEF8 : 0xFF7A8494;
        g.drawString(font, keyText, x + SIZE / 2 - kw / 2, y - 10, keyColor, true);

        // Anel de carga da ultimate (em volta do G)
        if (ult && !locked) renderCharge(g, d, x, y);

        // Fundo
        g.fill(x, y, x + SIZE, y + SIZE, 0xD80A0E18);
        if (v != null && !locked) {
            g.fillGradient(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, HudDraw.fade(color, 0.32F), HudDraw.fade(color, 0.06F));
        }
        if (casting) HudDraw.glow(g, x, y, SIZE, SIZE, color, 0.6F + 0.4F * pulse, 2);

        int border;
        if (locked) border = 0xFF2A2F3A;
        else if (v == null) border = 0xFF323946;
        else if (!valid) border = 0xFF8A3030;
        else if (casting) border = HudDraw.lerp(pulse, color, 0xFFFFFFFF);
        else if (v.trigger() != MoveTrigger.NORMAL) border = HudDraw.lerp(pulse * 0.6F, color, triggerColor(v.trigger()));
        else border = color;
        g.renderOutline(x, y, SIZE, SIZE, border);
        g.fill(x + 1, y + 1, x + SIZE - 1, y + 2, 0x22FFFFFF);

        if (locked) {
            HudDraw.spriteCentered(g, HudDraw.LOCK, x + SIZE / 2, y + SIZE / 2, 0xFF6A7080);
            return;
        }

        if (v != null) {
            boolean waitingCharge = v.ultimate() && !charged;
            // Ícone da magia (glifo principal + runa do elemento primário; pontinhos = outros elementos usados).
            // Esmaecido quando ela não sairia agora: inválida, selada, sem carga de ultimate ou sem mana.
            float dim = !valid ? 0.4F : sealed ? 0.45F : waitingCharge ? 0.5F : !affordable && left <= 0 ? 0.65F : 1F;
            if (dim < 1F) g.setColor(dim, dim, dim, 1F);
            GlyphArt.spell(g, v.recipe(), v.element(), x + 1, y + 1, SIZE - 2);
            if (dim < 1F) g.setColor(1F, 1F, 1F, 1F);
            if (valid && sealed) g.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0x50602090);
            else if (valid && !affordable && left <= 0) g.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0x50FF2020);
            if (valid && sealed && left <= 0) {
                // Selo violeta (pulsando) no lugar do motivo de mana: nada sai até o efeito acabar
                HudDraw.spriteCentered(g, HudDraw.LOCK, x + SIZE / 2, y + SIZE / 2 - 1, HudDraw.lerp(pulse, 0xFFB98CFF, 0xFFFFFFFF));
            } else if (left > 0) {
                // Varredura de recarga + segundos
                HudDraw.cooldownSweep(g, x + 1, y + 1, SIZE - 2, (float) left / total, 0xB8000000);
                // Segundos inteiros; com décimos nos últimos 3 s
                String secs = left > 60 ? String.valueOf(Mth.ceil(left / 20F))
                        : String.format(java.util.Locale.ROOT, "%.1f", left / 20F);
                g.drawCenteredString(font, secs, x + SIZE / 2 + 1, y + 6, 0xFFFFFFFF);
            }
            if (!valid) HudDraw.sprite(g, HudDraw.WARN, x + SIZE - 6, y + 2, HudDraw.RED);
            // Custo da magia (mana real, com o custo por cópia) — ou a vida paga, com Sobrecarga. Vermelho = falta.
            if (valid && !creative && left <= 0 && !sealed && !waitingCharge) renderCost(g, font, v, affordable, x, y);
        }

        renderVariantPips(g, d, s, v, x, y);
        if (v != null && v.trigger() != MoveTrigger.NORMAL) renderVersionBadge(g, v.trigger(), x, y);

        // Recarga terminou: lampejo
        int flash = HudState.readyFlash[s];
        if (flash > 0) {
            float a = flash / (float) HudState.READY_FLASH_TICKS;
            g.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, HudDraw.fade(0xFFFFFFFF, a * 0.55F));
            HudDraw.glow(g, x, y, SIZE, SIZE, 0xFFFFFFFF, a, 2);
        }
    }

    /** Custo pequeno no canto de cima à esquerda do slot (vermelho quando falta). */
    private static void renderCost(GuiGraphics g, Font font, LoadoutCache.Variant v, boolean affordable, int x, int y) {
        String txt;
        int c;
        if (v.overload()) {
            txt = HudDraw.num(v.healthCost());
            c = affordable ? 0xFFFF9AAE : HudDraw.RED;
        } else {
            if (v.cost() <= 0) return;
            txt = v.cost() >= 10000 ? (v.cost() / 1000) + "k" : String.valueOf(v.cost());
            c = affordable ? 0xFFE8E0FF : 0xFFFF8080;
        }
        float w = font.width(txt) * 0.5F;
        int bx = x + 1, by = y + 1;
        g.fill(bx, by, bx + Math.round(w) + 2, by + 5, 0xB0060810);
        if (v.overload()) g.fill(bx, by + 5, bx + Math.round(w) + 2, by + 6, 0xA0FF4060);
        HudDraw.scaledText(g, font, Component.literal(txt), bx + 1, by + 0.5F, 0.5F, c, false);
    }

    /** Pontinhos no rodapé do slot: quais versões de movimento existem (a que sai agora brilha). */
    private static void renderVariantPips(GuiGraphics g, MagicData d, int s, LoadoutCache.Variant active, int x, int y) {
        Map<MoveTrigger, LoadoutCache.Variant> vars = LoadoutCache.variants(s);
        int px = x + 3;
        for (MoveTrigger t : MOVES) {
            LoadoutCache.Variant v = vars.get(t);
            if (v != null) {
                boolean known = t.glyphId() == null || d.knowsGlyph(t.glyphId());
                int c = triggerColor(t);
                if (!known) c = 0xFF4A505C;
                else if (active != v) c = HudDraw.fade(c, 0.55F);
                g.fill(px, y + SIZE - 3, px + 3, y + SIZE - 2, c);
            }
            px += 4;
        }
    }

    /** Selo no canto: a versão de movimento que vai sair agora (agachado, correndo, no ar, na água). */
    private static void renderVersionBadge(GuiGraphics g, MoveTrigger t, int x, int y) {
        int c = triggerColor(t);
        int bx = x + SIZE - 6, by = y - 3;
        g.fill(bx, by, bx + 9, by + 9, 0xF00A0E18);
        g.renderOutline(bx, by, 9, 9, c);
        HudDraw.sprite(g, triggerIcon(t), bx + 1, by + 1, c);
    }

    /** Anel de Carga de Ultimate em volta do slot G (carga única na v1.0); brilha quando cheio. */
    private static void renderCharge(GuiGraphics g, MagicData d, int x, int y) {
        float frac = Math.min(1F, d.charge() / Progression.MAX_CHARGE);
        int tint = LoadoutCache.ultimateColor();
        int rx = x - 2, ry = y - 2, rw = SIZE + 4, rh = SIZE + 4;
        // Trilho
        HudDraw.perimeter(g, rx, ry, rw, rh, 1, 1F, 0xFF1C2130);
        if (frac >= 1F) {
            float pulse = HudDraw.pulse(1000);
            int gold = HudDraw.lerp(pulse, HudDraw.GOLD, HudDraw.GOLD_LIGHT);
            HudDraw.glow(g, rx, ry, rw, rh, HudDraw.GOLD, 0.55F + 0.45F * pulse, 3);
            HudDraw.perimeter(g, rx, ry, rw, rh, 1, 1F, gold);
            // Faísca dando a volta no anel
            float t = (Util.getMillis() % 1500) / 1500F;
            int[] head = HudDraw.perimeterPoint(rx, ry, rw, rh, t);
            HudDraw.spriteCentered(g, HudDraw.STAR, head[0], head[1], 0xFFFFFFFF);
        } else if (frac > 0F) {
            int c = HudDraw.lerp(frac, HudDraw.darken(tint, 0.2F), HudDraw.brighten(tint, 0.3F));
            int[] head = HudDraw.perimeter(g, rx, ry, rw, rh, 1, frac, c);
            g.fill(head[0] - 1, head[1] - 1, head[0] + 1, head[1] + 1, 0xFFFFFFFF);
        }
        int flash = HudState.ultFlash;
        if (flash > 0) {
            float a = flash / (float) HudState.ULT_FLASH_TICKS;
            int spread = Math.round((1F - a) * 6F);
            HudDraw.glow(g, rx - spread, ry - spread, rw + spread * 2, rh + spread * 2, HudDraw.GOLD_LIGHT, a, 3);
        }
    }
}
