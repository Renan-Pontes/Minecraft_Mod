package com.renanpontes.isekaimahou.client.onboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.core.onboard.OnboardPayloads;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Caixa de diálogo do núcleo no cliente ({@link Dialogue}): fila de diálogos, texto letra a letra, Enter avança,
 * clique na caixa (com o chat aberto) avança, segurar Backspace pula tudo, páginas com {@code await} esperam a ação do
 * jogador (teclas, abas do menu, conjuração, sinais do servidor). Não pausa o jogo e não tira o controle: enquanto uma
 * tela (menos o chat) está aberta, o texto e o tempo param e só um aviso pequeno fica no canto.
 */
final class DialogueClient {
    private static final float CHARS_PER_TICK = 1.6F;
    private static final int MAX_LINES = 5;
    private static final int SKIP_HOLD_TICKS = 16;
    private static final int DONE_DELAY = 14;
    private static final int FADE_TICKS = 6;
    private static final int MAX_QUEUE = 16;

    /** Diálogo na tela. */
    private static final class Active {
        final String id;
        final Component speaker;
        final int rgb;
        final List<Dialogue.Page> pages;
        int page;
        float shown;
        int idle;
        boolean satisfied;
        int satisfiedTicks;
        int nudge;
        int age;
        int wheelStart;
        int screenWait;

        Active(OnboardPayloads.DialogueShow p) {
            this.id = p.id();
            this.speaker = p.speaker();
            this.rgb = 0xFF000000 | (p.rgb() & 0xFFFFFF);
            this.pages = List.copyOf(p.pages());
            this.wheelStart = ClientMagic.data().activeWheelIndex();
        }

        Dialogue.Page current() {
            return pages.get(Math.min(page, pages.size() - 1));
        }

        @Nullable
        String await() {
            return current().await();
        }

        int total() {
            String s = current().text().getString();
            return s.codePointCount(0, s.length());
        }

        boolean revealed() {
            return shown >= total();
        }
    }

    private static final Deque<OnboardPayloads.DialogueShow> QUEUE = new ArrayDeque<>();
    @Nullable
    private static Active current;
    /** Último diálogo, desenhado sumindo por {@link #FADE_TICKS} ticks. */
    @Nullable
    private static Active fading;
    private static int fadeLeft;
    private static int skipHold;
    private static boolean showTracker;
    /** Caixa desenhada no último quadro (para o clique com o chat aberto). */
    private static int boxX, boxY, boxW, boxH;

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    static void show(OnboardPayloads.DialogueShow p) {
        if (p.pages().isEmpty()) return;
        if (current != null && current.id.equals(p.id())) {
            current = new Active(p); // mesmo id: recomeça com o conteúdo novo
            return;
        }
        QUEUE.removeIf(q -> q.id().equals(p.id()));
        if (current == null) {
            start(p);
        } else if (QUEUE.size() < MAX_QUEUE) {
            QUEUE.add(p);
        }
    }

    static void close(String id) {
        if (id.isEmpty()) {
            QUEUE.clear();
            endCurrent();
            return;
        }
        QUEUE.removeIf(q -> q.id().equals(id));
        if (current != null && current.id.equals(id)) endCurrent();
    }

    static void signal(String token) {
        Active a = current;
        if (a == null || a.await() == null) return;
        String await = a.await();
        if (await.equals(token) || await.equals("signal:" + token)) satisfy();
    }

    // =====================================================================================
    //  Fluxo
    // =====================================================================================
    private static void start(OnboardPayloads.DialogueShow p) {
        current = new Active(p);
        skipHold = 0;
        play(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.5F);
    }

    private static void startNext() {
        OnboardPayloads.DialogueShow next = QUEUE.poll();
        if (next != null) start(next);
    }

    /** Fecha sem avisar o servidor (quem fechou foi ele). */
    private static void endCurrent() {
        if (current == null) return;
        fading = current;
        fadeLeft = FADE_TICKS;
        current = null;
        skipHold = 0;
    }

    private static void finish(boolean skipped) {
        Active a = current;
        if (a == null) return;
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new OnboardPayloads.DialogueDone(a.id, skipped));
        }
        endCurrent();
        if (skipped) play(SoundEvents.BOOK_PAGE_TURN, 0.7F, 0.6F);
    }

    private static void nextPage() {
        Active a = current;
        if (a == null) return;
        if (a.page + 1 >= a.pages.size()) {
            finish(false);
            return;
        }
        a.page++;
        a.shown = 0;
        a.idle = 0;
        a.satisfied = false;
        a.satisfiedTicks = 0;
        a.nudge = 0;
        a.screenWait = 0;
        a.wheelStart = ClientMagic.data().activeWheelIndex();
        play(SoundEvents.BOOK_PAGE_TURN, 1.1F, 0.45F);
    }

    /** Enter / clique: mostra o texto todo; depois avança (páginas com ação esperam a ação). */
    private static void advance() {
        Active a = current;
        if (a == null) return;
        if (!a.revealed()) {
            a.shown = a.total();
            return;
        }
        if (a.await() != null && !a.satisfied) {
            a.nudge = 12;
            play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.9F, 0.35F);
            return;
        }
        nextPage();
    }

    private static void satisfy() {
        Active a = current;
        if (a == null || a.await() == null || a.satisfied) return;
        a.satisfied = true;
        a.satisfiedTicks = 0;
        a.shown = a.total();
        if (Dialogue.AWAIT_TRACKER.equals(a.await())) showTracker = true;
        play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4F, 0.6F);
    }

    /** Tempo de leitura antes de avançar sozinho (páginas sem ação). */
    private static int autoTicks(int chars) {
        return Mth.clamp(60 + chars * 2, 80, 360);
    }

    private static boolean blocking(Minecraft mc) {
        return mc.screen != null && !(mc.screen instanceof ChatScreen);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (fadeLeft > 0) fadeLeft--;
        if (mc.player == null) return;
        if (showTracker) {
            // Depois do J do núcleo (que alterna): o tutorial quer o rastreador VISÍVEL
            showTracker = false;
            ObjectiveTracker.setVisible(true);
        }
        if (current == null) startNext();
        Active a = current;
        if (a == null || mc.isPaused()) {
            while (CoreKeys.DIALOGUE_NEXT.consumeClick()) { /* sem diálogo: Enter não faz nada */ }
            return;
        }
        a.age++;
        if (a.nudge > 0) a.nudge--;
        pollAwaits(mc, a);

        boolean blocked = blocking(mc);
        while (CoreKeys.DIALOGUE_NEXT.consumeClick()) {
            if (!blocked) advance();
        }
        if (current != a) return;

        // Segurar Backspace pula o diálogo inteiro
        if (mc.screen == null && InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_BACKSPACE)) {
            if (++skipHold >= SKIP_HOLD_TICKS) {
                finish(true);
                return;
            }
        } else {
            skipHold = Math.max(0, skipHold - 2);
        }

        if (a.satisfied && ++a.satisfiedTicks >= DONE_DELAY && !blocked) {
            nextPage();
            return;
        }
        if (blocked) return; // texto e tempo param com telas abertas

        int total = a.total();
        if (a.shown < total) {
            int before = (int) a.shown;
            a.shown = Math.min(total, a.shown + CHARS_PER_TICK);
            typeSound(a, before, (int) a.shown);
        } else if (a.await() == null) {
            if (++a.idle >= autoTicks(total)) nextPage();
        }
    }

    /** Clique suave a cada 3 letras novas (não em espaços). */
    private static void typeSound(Active a, int before, int after) {
        String s = a.current().text().getString();
        for (int i = before; i < after; i++) {
            if (i % 3 != 0) continue;
            int idx = s.offsetByCodePoints(0, Math.min(i, s.codePointCount(0, s.length()) - 1));
            if (Character.isWhitespace(s.codePointAt(idx))) continue;
            play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.7F + (i % 7) * 0.03F, 0.07F);
            break;
        }
    }

    /** Ações que se conferem olhando o estado (abas do menu, troca de elemento). */
    private static void pollAwaits(Minecraft mc, Active a) {
        String await = a.await();
        if (await == null || a.satisfied) return;
        if (await.startsWith("screen:")) {
            String tab = await.substring("screen:".length());
            if (mc.screen instanceof StatusScreen ss && (tab.equals(ss.currentTab()) || tab.isEmpty() || tab.equals("menu"))) satisfy();
        } else if (await.equals(Dialogue.AWAIT_WHEEL)) {
            if (ClientMagic.data().activeWheelIndex() != a.wheelStart) satisfy();
        } else if (await.equals(Dialogue.AWAIT_MENU)) {
            if (mc.screen instanceof StatusScreen) satisfy();
        }
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    static void onKey(InputEvent.Key e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        Active a = current;
        if (a == null || a.satisfied || Minecraft.getInstance().screen != null) return;
        KeyMapping km = keyFor(a.await());
        if (km != null && km != CoreKeys.WHEEL_MODIFIER && km.matches(e.getKey(), e.getScanCode())) satisfy();
    }

    static void onMouse(InputEvent.MouseButton.Pre e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        Active a = current;
        if (a == null || a.satisfied || Minecraft.getInstance().screen != null) return;
        KeyMapping km = keyFor(a.await());
        if (km != null && km != CoreKeys.WHEEL_MODIFIER && km.matchesMouse(e.getButton())) satisfy();
    }

    /** Prioridade alta: o núcleo cancela a rolagem com o modificador (e listeners normais não a veriam). */
    static void onScroll(InputEvent.MouseScrollingEvent e) {
        Active a = current;
        if (a == null || a.satisfied || Minecraft.getInstance().screen != null) return;
        if (Dialogue.AWAIT_WHEEL.equals(a.await()) && CoreKeys.WHEEL_MODIFIER.isDown() && e.getScrollDeltaY() != 0) satisfy();
    }

    static void onScreenOpen(ScreenEvent.Opening e) {
        Active a = current;
        if (a != null && !a.satisfied && Dialogue.AWAIT_MENU.equals(a.await()) && e.getNewScreen() instanceof StatusScreen) satisfy();
    }

    /** Com o chat aberto o mouse está livre: clicar na caixa avança. */
    static void onScreenClick(ScreenEvent.MouseButtonPressed.Pre e) {
        if (current == null || !(e.getScreen() instanceof ChatScreen) || boxW <= 0) return;
        double mx = e.getMouseX(), my = e.getMouseY();
        if (mx >= boxX && mx <= boxX + boxW && my >= boxY - 12 && my <= boxY + boxH) {
            advance();
            e.setCanceled(true);
        }
    }

    /** Tecla pedida por um {@code await = "key:..."}; null se não for de tecla ou não existir. */
    @Nullable
    static KeyMapping keyFor(@Nullable String await) {
        if (await == null || !await.startsWith("key:")) return null;
        String k = await.substring(4);
        switch (k) {
            case "tracker": return CoreKeys.TRACKER;
            case "menu": return CoreKeys.MENU;
            case "wheel": return CoreKeys.WHEEL_MODIFIER;
            case "slot1": return CoreKeys.SLOTS[0];
            case "slot2": return CoreKeys.SLOTS[1];
            case "slot3": return CoreKeys.SLOTS[2];
            case "ultimate": return CoreKeys.SLOTS[Progression.ULTIMATE_SLOT];
            case "loadout_modifier": return CoreKeys.LOADOUT_MODIFIER;
            case "dialogue_next": return CoreKeys.DIALOGUE_NEXT;
            default: break;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return null;
        for (KeyMapping m : mc.options.keyMappings) {
            String n = m.getName();
            if (n.equals(k) || n.equals("key.isekai_mahou." + k) || n.equals("key." + k)) return m;
        }
        return null;
    }

    /** Texto da ação pedida, com a tecla REAL configurada em destaque. */
    private static Component prompt(String await) {
        if (await.equals(Dialogue.AWAIT_CAST)) {
            return Component.translatable("dialogue.isekai_mahou.await.cast", keyName(CoreKeys.SLOTS[0]));
        }
        if (await.equals(Dialogue.AWAIT_WHEEL)) {
            return Component.translatable("dialogue.isekai_mahou.await.wheel", keyName(CoreKeys.WHEEL_MODIFIER));
        }
        if (await.startsWith("screen:")) {
            String tab = await.substring("screen:".length());
            if (tab.isEmpty() || tab.equals("menu")) {
                return Component.translatable("dialogue.isekai_mahou.await.key", keyName(CoreKeys.MENU));
            }
            return Component.translatable("dialogue.isekai_mahou.await.screen", keyName(CoreKeys.MENU),
                    Component.translatable("screen.isekai_mahou.tab." + tab).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }
        KeyMapping km = keyFor(await);
        if (km != null) return Component.translatable("dialogue.isekai_mahou.await.key", keyName(km));
        return Component.translatable("dialogue.isekai_mahou.await.signal");
    }

    private static Component keyName(KeyMapping km) {
        return Component.literal("[").append(km.getTranslatedKeyMessage()).append("]")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    /** Camada do HUD (embaixo do chat). */
    static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        boxW = 0;
        if (mc.player == null || mc.options.hideGui || blocking(mc)) return;
        float pt = dt.getGameTimeDeltaPartialTick(true);
        Active a = current;
        float alpha;
        if (a != null) {
            alpha = Mth.clamp((a.age + pt) / FADE_TICKS, 0F, 1F);
        } else if (fading != null && fadeLeft > 0) {
            a = fading;
            alpha = Mth.clamp((fadeLeft - pt) / FADE_TICKS, 0F, 1F);
        } else {
            return;
        }
        if (alpha <= 0.05F) return; // texto com alfa < 4 o Font desenha opaco
        drawBox(g, mc.font, a, alpha, pt, a == current);
    }

    /** Aviso pequeno no canto quando uma tela esconde a caixa: o diálogo continua quando ela fechar. */
    static void renderOverScreen(ScreenEvent.Render.Post e) {
        Active a = current;
        Screen screen = e.getScreen();
        if (a == null || screen instanceof ChatScreen) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        GuiGraphics g = e.getGuiGraphics();
        Component text = Component.empty().append(a.speaker.copy().withColor(a.rgb & 0xFFFFFF))
                .append(Component.literal(": "))
                .append(Component.translatable(a.satisfied ? "dialogue.isekai_mahou.paused_done" : "dialogue.isekai_mahou.paused")
                        .withStyle(ChatFormatting.GRAY));
        int w = font.width(text) + 14;
        float pulse = 0.6F + 0.4F * Mth.sin((a.age + e.getPartialTick()) * 0.2F);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        MahouUi.roundRect(g, 4, 4, w, 14, MahouUi.alpha(MahouUi.INK_TOP, 0xE0), MahouUi.alpha(MahouUi.INK_BOTTOM, 0xE0));
        MahouUi.roundOutline(g, 4, 4, w, 14, MahouUi.alpha(a.rgb, (int) (pulse * 255)));
        g.drawString(font, text, 11, 7, MahouUi.TEXT, true);
        g.pose().popPose();
    }

    private static void drawBox(GuiGraphics g, Font font, Active a, float alpha, float pt, boolean live) {
        int gw = g.guiWidth();
        int w = Math.min(340, gw - 32);
        int innerW = w - 20;
        Dialogue.Page page = a.current();
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(page.text(), innerW));
        if (lines.size() > MAX_LINES) lines = new ArrayList<>(lines.subList(0, MAX_LINES));
        int textH = Math.max(1, lines.size()) * 10;
        int h = 8 + textH + 5 + 10 + 5;
        // Região fixa de diálogo do HudLayout: embaixo, centrada, acima da barra de itens e do HUD de recursos
        HudLayout.Rect area = HudLayout.dialogue(w, h);
        int x = area.x();
        float ease = 1F - (1F - alpha) * (1F - alpha);
        int y = area.y() + Math.round((1F - ease) * 10F);
        HudLayout.drawn(HudLayout.Region.DIALOGUE, area);
        boxX = x;
        boxY = y;
        boxW = w;
        boxH = h;
        int al = (int) (alpha * 255);

        // Fundo, contorno na cor de quem fala e filete de brilho no topo
        MahouUi.roundRect(g, x, y, w, h, MahouUi.alpha(MahouUi.INK_TOP, (int) (alpha * 0xEC)), MahouUi.alpha(MahouUi.INK_BOTTOM, (int) (alpha * 0xEC)));
        MahouUi.roundOutline(g, x, y, w, h, MahouUi.alpha(MahouUi.darken(a.rgb, 0.25F), al));
        g.fillGradient(x + 2, y + 1, x + w - 2, y + 2, MahouUi.alpha(a.rgb, (int) (alpha * 0xC0)), MahouUi.alpha(a.rgb, (int) (alpha * 0x40)));

        // Aba com o nome
        int nameW = font.width(a.speaker) + 14;
        MahouUi.roundRect(g, x + 8, y - 12, nameW, 13, MahouUi.alpha(MahouUi.darken(a.rgb, 0.7F), (int) (alpha * 0xF0)),
                MahouUi.alpha(MahouUi.INK_TOP, (int) (alpha * 0xF0)));
        MahouUi.roundOutline(g, x + 8, y - 12, nameW, 13, MahouUi.alpha(a.rgb, al));
        g.drawString(font, a.speaker, x + 15, y - 9, MahouUi.alpha(MahouUi.lighten(a.rgb, 0.25F), al), true);

        // Pular (segurar Backspace)
        Component skip = Component.translatable("dialogue.isekai_mahou.skip");
        float sw = font.width(skip) * 0.7F;
        float sx = x + w - 6 - sw;
        MahouUi.smallText(g, font, skip, sx, y - 8, 0.7F, MahouUi.alpha(MahouUi.TEXT_DIM, (int) (alpha * 0xB0)), true);
        if (live && skipHold > 0) {
            float f = Math.min(1F, (skipHold + pt) / SKIP_HOLD_TICKS);
            g.fill((int) sx, y - 2, (int) (sx + sw * f), y - 1, MahouUi.alpha(0xFFFF6B6B, al));
        }

        // Texto letra a letra
        int budget = live ? (int) Math.floor(a.shown) : Integer.MAX_VALUE;
        int ty = y + 8;
        int textColor = MahouUi.alpha(MahouUi.TEXT, al);
        for (FormattedCharSequence line : lines) {
            if (budget <= 0) break;
            int[] left = {budget};
            FormattedCharSequence cut = sink -> line.accept((idx, style, cp) -> left[0]-- > 0 && sink.accept(idx, style, cp));
            g.drawString(font, cut, x + 10, ty, textColor, true);
            budget -= length(line);
            ty += 10;
        }

        // Rodapé: ação pedida / pronto / página
        int fy = y + h - 14;
        String await = page.await();
        boolean revealed = !live || a.revealed();
        if (await != null) {
            if (live && a.satisfied) {
                Component done = Component.literal("✔ ").append(Component.translatable("dialogue.isekai_mahou.await.done"))
                        .withStyle(ChatFormatting.GREEN);
                g.drawString(font, done, x + 10, fy, MahouUi.alpha(0xFF7CFF8A, al), true);
            } else {
                Component pr = prompt(await);
                int pw = font.width(pr);
                float pulse = 0.5F + 0.5F * Mth.sin((a.age + pt) * 0.25F);
                int glow = (int) (alpha * (0x30 + 0x50 * pulse + (a.nudge > 0 ? 0x60 : 0)));
                int shake = a.nudge > 0 ? Math.round(Mth.sin((a.nudge + pt) * 2.4F) * 2F) : 0;
                MahouUi.roundRect(g, x + 6 + shake, fy - 3, pw + 8, 13, MahouUi.alpha(MahouUi.GOLD_DIM, glow),
                        MahouUi.alpha(MahouUi.GOLD_DIM, glow / 2));
                MahouUi.roundOutline(g, x + 6 + shake, fy - 3, pw + 8, 13, MahouUi.alpha(MahouUi.GOLD, (int) (alpha * (0x80 + 0x7F * pulse))));
                g.drawString(font, pr, x + 10 + shake, fy, MahouUi.alpha(MahouUi.GOLD_PALE, al), true);
            }
        }

        Component counter = Component.literal((a.page + 1) + "/" + a.pages.size());
        int cw = font.width(counter);
        int rx = x + w - 8;
        if (revealed && (await == null || (live && a.satisfied))) {
            boolean blink = ((a.age / 8) & 1) == 0;
            Component next = Component.translatable("dialogue.isekai_mahou.next", CoreKeys.DIALOGUE_NEXT.getTranslatedKeyMessage());
            int nw = font.width(next);
            g.drawString(font, next, rx - nw, fy, MahouUi.alpha(blink ? MahouUi.GOLD : MahouUi.GOLD_DIM, al), true);
            rx -= nw + 8;
        }
        g.drawString(font, counter, rx - cw, fy, MahouUi.alpha(MahouUi.TEXT_DIM, al), false);

        // Tempo até avançar sozinho
        if (live && await == null && a.revealed()) {
            float f = 1F - Math.min(1F, (a.idle + pt) / autoTicks(a.total()));
            int bw = Math.round((w - 8) * f);
            g.fill(x + 4, y + h - 3, x + 4 + bw, y + h - 2, MahouUi.alpha(a.rgb, (int) (alpha * 0x90)));
        }
    }

    private static int length(FormattedCharSequence seq) {
        int[] n = {0};
        seq.accept((idx, style, cp) -> {
            n[0]++;
            return true;
        });
        return n[0];
    }

    /** Saída do mundo: tudo some sem avisar o servidor (ele já esqueceu). */
    static void clear() {
        QUEUE.clear();
        current = null;
        fading = null;
        fadeLeft = 0;
        skipHold = 0;
        showTracker = false;
        boxW = 0;
    }

    /** Há diálogo na tela? */
    static boolean active() {
        return current != null;
    }

    private DialogueClient() {}
}
