package com.renanpontes.isekaimahou.feature.familiars.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarData;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarEntity;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarForm;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarNetwork;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarNetwork.Action;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarSpell;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Familiar" do menu B (só existe para quem já despertou o seu). À esquerda: o retrato, o nome (dá para trocar) e
 * três botões — chamar/guardar, ligar/desligar a ajuda e abrir a bolsa. À direita: forma e estágio, o que ele faz por
 * você, o vínculo e o que falta para evoluir, e POR QUE ele tem essa forma (os traços do seu jeito de jogar).
 * Poucos elementos, nada piscando.
 * <p>
 * v1.0 (onda 4): o botão "Magia de apoio" troca o lado direito pela escolha da receita que o familiar usa sozinho —
 * a lista só mostra as magias do jogador (loadouts e biblioteca) que servem ({@link FamiliarSpell#problem}).
 */
public class FamiliarTab implements MenuTab {
    private static final int TEXT = 0xFFE9E1F5, MUTED = 0xFFA99BC0, FAINT = 0xFF6F6485, GOLD = 0xFFFFD86B;
    private static final int CARD = 0xFF1B1526, DARK = 0xFF120D1A;

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private int x, y, w, h;
    private long lastRequest;
    private EditBox nameBox;
    /** Nome que a caixa mostrava na última sincronização (para não apagar o que o jogador está digitando). */
    private String syncedName = "";
    /** Lado direito mostrando a escolha da receita de apoio (em vez das informações do familiar). */
    private boolean picking;
    /** Magias do jogador que servem como receita de apoio (refeita ao abrir a escolha e de vez em quando). */
    private List<SpellRecipe> options = List.of();
    private long optionsAt;
    private int scroll;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int leftW = leftWidth();
        nameBox = new EditBox(Minecraft.getInstance().font, x + 1, y + 98, leftW - 2, 14,
                Component.translatable("screen.isekai_mahou.familiars.name"));
        nameBox.setMaxLength(FamiliarData.MAX_NAME);
        nameBox.setHint(Component.translatable("screen.isekai_mahou.familiars.name").withStyle(ChatFormatting.DARK_GRAY));
        syncedName = ClientFamiliar.state.name();
        nameBox.setValue(syncedName);
        screen.addTabWidget(nameBox);
        request();
    }

    private int leftWidth() {
        return Math.min(150, w * 2 / 5);
    }

    private void request() {
        lastRequest = Util.getMillis();
        PacketDistributor.sendToServer(new Action(Action.SYNC, ""));
    }

    @Override
    public void tick() {
        // Vínculo e "tendência" mudam devagar: atualizar a cada 5 s basta
        if (Util.getMillis() - lastRequest > (picking ? 1000 : 5000)) request();
        if (picking && Util.getMillis() - optionsAt > 2000) refreshOptions();
        String name = ClientFamiliar.state.name();
        if (nameBox != null && !nameBox.isFocused() && !name.equals(syncedName)) {
            syncedName = name;
            nameBox.setValue(name);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        Font font = Minecraft.getInstance().font;
        FamiliarNetwork.State s = ClientFamiliar.state;
        if (!s.awakened()) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.familiars.none"), x, y, MUTED, false);
            return;
        }
        FamiliarForm form = ClientFamiliar.form();
        int accent = 0xFF000000 | form.rgb;
        int leftW = leftWidth();
        renderLeft(g, font, s, accent, leftW, mx, my);
        g.fill(x + leftW + 6, y, x + leftW + 7, y + h, accent & 0x60FFFFFF);
        if (picking) renderSpell(g, font, s, accent, x + leftW + 14, w - leftW - 14, mx, my);
        else renderRight(g, font, s, form, accent, x + leftW + 14, w - leftW - 14);
    }

    // =====================================================================================
    //  Receita de apoio
    // =====================================================================================

    /** Junta as magias do jogador (loadouts e biblioteca) que o familiar aceita, sem repetir a mesma sequência de glifos. */
    private void refreshOptions() {
        optionsAt = Util.getMillis();
        Minecraft mc = Minecraft.getInstance();
        MagicData d = ClientMagic.data();
        // criativo: o servidor também não checa os glifos conhecidos
        MagicData check = mc.player != null && mc.player.getAbilities().instabuild ? null : d;
        Map<List<ResourceLocation>, SpellRecipe> found = new LinkedHashMap<>();
        for (int lo = 0; lo < d.loadoutCount(); lo++) {
            for (int slot = 0; slot < 4; slot++) {
                SpellSlot sl = d.peekSlot(lo, slot);
                if (sl == null) continue;
                for (SpellRecipe r : sl.all().values()) offer(found, r, check);
            }
        }
        for (SpellRecipe r : d.library()) offer(found, r, check);
        options = new ArrayList<>(found.values());
        scroll = Math.max(0, Math.min(scroll, options.size() - 1));
    }

    private static void offer(Map<List<ResourceLocation>, SpellRecipe> found, SpellRecipe r, MagicData check) {
        if (r == null || r.isEmpty() || found.containsKey(r.glyphs())) return;
        if (FamiliarSpell.problem(r, check) == null) found.put(r.glyphs(), r);
    }

    private void renderSpell(GuiGraphics g, Font font, FamiliarNetwork.State s, int accent, int rx, int rw, int mx, int my) {
        int cy = y;
        g.drawString(font, Component.translatable("screen.isekai_mahou.familiars.spell.title").withStyle(ChatFormatting.BOLD), rx, cy, accent, true);
        cy += 12;
        cy += wrapped(g, font, Component.translatable("screen.isekai_mahou.familiars.spell.about"), rx, cy, rw, MUTED, 4) + 6;

        SpellRecipe cur = s.spell();
        if (cur.isEmpty()) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.familiars.spell.none"), rx, cy, FAINT, false);
            cy += 14;
        } else {
            String name = font.plainSubstrByWidth(FamiliarSpell.displayName(cur).getString(), rw);
            g.drawString(font, name, rx, cy, GOLD, false);
            cy += 11;
            SpellParser.Result parsed = SpellParser.parse(cur, Element.ARCANE, null, null);
            if (parsed.ok()) {
                cy += wrapped(g, font, FamiliarSpell.when(parsed.spell()).text(), rx, cy, rw, TEXT, 2) + 1;
            }
            Component wait = s.spellWait() > 0 ? Component.translatable("screen.isekai_mahou.familiars.spell.wait", s.spellWait())
                    : Component.translatable("screen.isekai_mahou.familiars.spell.ready");
            g.drawString(font, wait, rx, cy, s.spellWait() > 0 ? MUTED : accent, false);
            cy += 12;
            button(g, font, rx, cy, Math.min(rw, 96), 14, Component.translatable("screen.isekai_mahou.familiars.spell.forget"), mx, my, FAINT,
                    () -> send(Action.FORGET_SPELL, ""));
            cy += 20;
        }

        g.drawString(font, Component.translatable("screen.isekai_mahou.familiars.spell.choose").withStyle(ChatFormatting.BOLD), rx, cy, accent, false);
        cy += 12;
        if (options.isEmpty()) {
            wrapped(g, font, Component.translatable("screen.isekai_mahou.familiars.spell.empty"), rx, cy, rw, FAINT, 5);
            return;
        }
        int rows = Math.max(1, (y + h - cy) / 18);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, options.size() - rows)));
        for (int i = scroll; i < options.size() && i < scroll + rows; i++) {
            SpellRecipe option = options.get(i);
            boolean current = option.glyphs().equals(cur.glyphs());
            button(g, font, rx, cy, rw, 16, FamiliarSpell.displayName(option), mx, my, current ? GOLD : accent, () -> {
                PacketDistributor.sendToServer(new FamiliarNetwork.Teach(option));
                lastRequest = 0;
            });
            cy += 18;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!picking || options.isEmpty()) return false;
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        return true;
    }

    private void renderLeft(GuiGraphics g, Font font, FamiliarNetwork.State s, int accent, int lw, int mx, int my) {
        // Retrato: o próprio familiar, se estiver por perto
        g.fill(x, y, x + lw, y + 92, CARD);
        g.renderOutline(x, y, lw, 92, accent & 0x90FFFFFF);
        FamiliarEntity body = s.summoned() ? ClientFamiliar.body() : null;
        if (body != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 2, y + 2, x + lw - 2, y + 90, 62, 0.1F, mx, my, body);
        } else {
            Component away = Component.translatable(s.summoned() ? "screen.isekai_mahou.familiars.far" : "screen.isekai_mahou.familiars.resting");
            g.drawCenteredString(font, away, x + lw / 2, y + 42, FAINT);
        }
        int cy = y + 116;
        button(g, font, x, cy, lw, 16, Component.translatable(s.summoned() ? "screen.isekai_mahou.familiars.dismiss"
                : "screen.isekai_mahou.familiars.call"), mx, my, accent, () -> send(Action.TOGGLE_SUMMON, ""));
        cy += 18;
        button(g, font, x, cy, lw, 16, Component.translatable(s.help() ? "screen.isekai_mahou.familiars.help_on"
                : "screen.isekai_mahou.familiars.help_off"), mx, my, s.help() ? accent : FAINT, () -> send(Action.TOGGLE_HELP, ""));
        cy += 18;
        button(g, font, x, cy, lw, 16, Component.translatable(picking ? "screen.isekai_mahou.familiars.spell.back"
                : "screen.isekai_mahou.familiars.spell.button"), mx, my, picking ? GOLD : accent, () -> {
            picking = !picking;
            scroll = 0;
            if (picking) refreshOptions();
        });
        cy += 18;
        if (s.summoned()) {
            button(g, font, x, cy, lw, 16, Component.translatable("screen.isekai_mahou.familiars.stash"), mx, my, GOLD, () -> {
                send(Action.STASH, "");
            });
            cy += 18;
        }
        if (cy + 20 <= y + h) {
            wrapped(g, font, Component.translatable("screen.isekai_mahou.familiars.tip"), x, cy + 2, lw, FAINT, 3);
        }
    }

    private void renderRight(GuiGraphics g, Font font, FamiliarNetwork.State s, FamiliarForm form, int accent, int rx, int rw) {
        int cy = y;
        MutableComponent title = s.name().isEmpty() ? form.displayName(s.stage()) : Component.literal(s.name());
        g.drawString(font, title.withStyle(ChatFormatting.BOLD), rx, cy, accent, true);
        cy += 12;
        // Com nome próprio, a forma vai na linha de baixo; os losangos mostram o estágio
        Component sub = Component.translatable("screen.isekai_mahou.familiars.stage", s.stage(), FamiliarForm.MAX_STAGE);
        if (!s.name().isEmpty()) sub = form.displayName(s.stage()).append(Component.literal("  ·  ")).append(sub);
        g.drawString(font, sub, rx, cy, MUTED, false);
        cy += 13;
        cy += wrapped(g, font, form.description(), rx, cy, rw, TEXT, 3) + 5;

        g.drawString(font, Component.translatable("screen.isekai_mahou.familiars.help_title").withStyle(ChatFormatting.BOLD), rx, cy, accent, false);
        cy += 11;
        cy += wrapped(g, font, form.help.description(), rx, cy, rw, s.help() ? TEXT : FAINT, 3) + 6;

        // Vínculo e próximo estágio
        g.drawString(font, Component.translatable("screen.isekai_mahou.familiars.bond").withStyle(ChatFormatting.BOLD), rx, cy, accent, false);
        cy += 11;
        if (s.nextBond() > 0) {
            float frac = Math.min(1F, s.bond() / (float) s.nextBond());
            g.fill(rx, cy, rx + rw, cy + 6, DARK);
            g.fill(rx, cy, rx + (int) (rw * frac), cy + 6, accent);
            g.renderOutline(rx, cy, rw, 6, accent & 0x70FFFFFF);
            cy += 9;
            boolean levelOk = ClientMagic.data().level() >= s.nextLevel();
            boolean bondOk = s.bond() >= s.nextBond();
            g.drawString(font, Component.translatable("screen.isekai_mahou.familiars.next_bond", Math.min(s.bond(), s.nextBond()), s.nextBond()),
                    rx, cy, bondOk ? GOLD : MUTED, false);
            cy += 10;
            g.drawString(font, Component.translatable("screen.isekai_mahou.familiars.next_level", s.nextLevel()), rx, cy, levelOk ? GOLD : MUTED, false);
            cy += 14;
        } else {
            g.drawString(font, Component.translatable("screen.isekai_mahou.familiars.max_stage"), rx, cy, GOLD, false);
            cy += 14;
        }

        // Por que esta forma
        if (!s.reasons().isEmpty() && cy + 22 <= y + h) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.familiars.why").withStyle(ChatFormatting.BOLD), rx, cy, accent, false);
            cy += 11;
            for (FamiliarNetwork.Reason r : s.reasons()) {
                if (cy + 10 > y + h) break;
                g.drawString(font, Component.literal("· ").append(reason(r)), rx, cy, TEXT, false);
                cy += 10;
            }
            cy += 4;
        }
        // Tendência: o jogador mudou e o familiar está "inquieto"
        FamiliarForm cand = FamiliarForm.byId(s.candidate());
        if (cand != null && cy + 10 <= y + h) {
            wrapped(g, font, Component.translatable("screen.isekai_mahou.familiars.drift", cand.displayName(s.stage()), s.drift(), s.driftMax()),
                    rx, cy, rw, 0xFF000000 | cand.rgb, 2);
        }
    }

    private static Component reason(FamiliarNetwork.Reason r) {
        return switch (r.kind()) {
            case "class" -> Component.translatable("screen.isekai_mahou.familiars.reason.class", MagicPath.byId(r.arg()).displayName());
            case "race" -> Component.translatable("screen.isekai_mahou.familiars.reason.race", Component.translatable("race.isekai_mahou." + r.arg()));
            case "element" -> {
                Element e = Element.byId(r.arg());
                yield Component.translatable("screen.isekai_mahou.familiars.reason.element", e == null ? Component.literal(r.arg()) : e.displayName());
            }
            default -> Component.translatable("screen.isekai_mahou.familiars.feat." + r.arg());
        };
    }

    private void send(int op, String text) {
        PacketDistributor.sendToServer(new Action(op, text));
        lastRequest = 0;
    }

    private void button(GuiGraphics g, Font font, int bx, int by, int bw, int bh, Component label, int mx, int my, int accent, Runnable action) {
        boolean hover = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
        g.fill(bx, by, bx + bw, by + bh, mix(accent, DARK, hover ? 0.45F : 0.7F));
        g.renderOutline(bx, by, bw, bh, hover ? 0xFFFFFFFF : accent);
        String shown = font.plainSubstrByWidth(label.getString(), bw - 6);
        g.drawString(font, shown, bx + (bw - font.width(shown)) / 2, by + (bh - 8) / 2, TEXT, true);
        hits.add(new Hit(bx, by, bw, bh, action));
    }

    private static int wrapped(GuiGraphics g, Font font, Component text, int tx, int ty, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) g.drawString(font, lines.get(i), tx, ty + i * 10, color, false);
        return n * 10;
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (Hit hit : List.copyOf(hits)) {
            if (mx >= hit.x && my >= hit.y && mx < hit.x + hit.w && my < hit.y + hit.h) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hit.action.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (nameBox == null || !nameBox.isFocused()) return false;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            syncedName = nameBox.getValue().trim();
            send(Action.RENAME, syncedName);
            nameBox.setFocused(false);
            return true;
        }
        // O resto segue para a caixa de texto (o menu já não fecha com B enquanto ela está em foco)
        return false;
    }

    @Override
    public void removed() {
        // Saiu da aba com um nome digitado e não confirmado: vale o que está escrito
        if (nameBox != null && !nameBox.getValue().trim().equals(ClientFamiliar.state.name())) {
            PacketDistributor.sendToServer(new Action(Action.RENAME, nameBox.getValue().trim()));
        }
    }
}
