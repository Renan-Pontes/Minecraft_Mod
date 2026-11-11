package com.renanpontes.isekaimahou.feature.cinematic.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Anúncios na tela ("gritos" de magia, falas de chefe, avisos). Um por vez, numa fila: quando há outros esperando,
 * o atual acelera. Estilos (Announce.STYLE_*): SPELL normal, ULTIMATE grande com brilho e letras se aproximando,
 * BOSS com moldura e nome de quem fala (texto aparecendo como máquina de escrever), SYSTEM discreto.
 */
final class AnnounceOverlay {
    private static final int MAX_QUEUE = 6;
    private static final Deque<Banner> QUEUE = new ArrayDeque<>();
    private static Banner current;

    private static final class Banner {
        final Component text;
        Component subtitle;
        final int rgb;
        final int style;
        final Component speaker;
        final int life;
        float age;

        Banner(Component text, Component subtitle, int rgb, int style, Component speaker) {
            this.text = text;
            this.subtitle = subtitle;
            this.rgb = rgb & 0xFFFFFF;
            this.style = style;
            this.speaker = speaker;
            int len = text.getString().length();
            this.life = switch (style) {
                case Announce.STYLE_ULTIMATE -> 64;
                case Announce.STYLE_BOSS -> Mth.clamp(55 + (int) (len * 1.4F), 70, 160);
                case Announce.STYLE_SYSTEM -> 46;
                default -> 50;
            };
        }

        boolean important() {
            return style == Announce.STYLE_ULTIMATE || style == Announce.STYLE_BOSS;
        }

        boolean sameText(Component other) {
            return text.getString().equals(other.getString());
        }
    }

    // =====================================================================================
    // Entrada
    // =====================================================================================

    /** Gancho do pacote AnnounceText (ClientHooks.announce): tela + balão sobre a cabeça. */
    static void onAnnounce(CorePayloads.AnnounceText p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean own = p.entityId() == mc.player.getId();
        boolean spellName = p.style() == Announce.STYLE_SPELL || p.style() == Announce.STYLE_ULTIMATE;
        // A opção desliga os NOMES DE MAGIA dos outros; falas de chefe e avisos do sistema sempre aparecem
        if (!own && spellName && !CinematicSettings.othersAnnouncements()) return;

        Entity source = mc.level.getEntity(p.entityId());
        if (source != null) WorldLabels.bubble(source, p.text(), p.rgb(), p.style());
        // A cutscene em andamento já mostra este nome em tela cheia: só o balão no mundo
        if (CutsceneDirector.showingTitle(p.text())) return;
        Component speaker = p.style() == Announce.STYLE_BOSS && source != null ? source.getDisplayName() : null;
        enqueue(new Banner(p.text(), null, p.rgb(), p.style(), speaker));
        playSound(mc, p.style());
    }

    /** Cutscenes desligadas: mostra o título da cena como anúncio (sem câmera). */
    static void cutsceneFallback(CorePayloads.Cutscene p, boolean boss) {
        for (Banner b : allBanners()) {
            if (b.sameText(p.title())) {
                if (b.subtitle == null) b.subtitle = p.subtitle();
                return;
            }
        }
        int style = boss ? Announce.STYLE_BOSS : Announce.STYLE_ULTIMATE;
        enqueue(new Banner(p.title(), p.subtitle(), p.rgb(), style, null));
        playSound(Minecraft.getInstance(), style);
    }

    /** Uma cutscene vai mostrar este título em tela cheia: tira o anúncio igual para não repetir. */
    static void absorbCutsceneTitle(Component title) {
        if (current != null && current.sameText(title)) current = null;
        QUEUE.removeIf(b -> b.sameText(title));
    }

    static void clear() {
        QUEUE.clear();
        current = null;
    }

    private static List<Banner> allBanners() {
        List<Banner> all = new ArrayList<>(QUEUE.size() + 1);
        if (current != null) all.add(current);
        all.addAll(QUEUE);
        return all;
    }

    private static void enqueue(Banner b) {
        if (b.important()) {
            // Ultimates e falas de chefe passam na frente dos anúncios comuns
            List<Banner> rest = new ArrayList<>(QUEUE);
            QUEUE.clear();
            Iterator<Banner> it = rest.iterator();
            while (it.hasNext()) {
                Banner q = it.next();
                if (!q.important()) break;
                QUEUE.addLast(q);
                it.remove();
            }
            QUEUE.addLast(b);
            QUEUE.addAll(rest);
        } else {
            QUEUE.addLast(b);
        }
        while (QUEUE.size() > MAX_QUEUE) {
            Banner drop = null;
            for (Banner q : QUEUE) {
                if (!q.important()) {
                    drop = q;
                    break;
                }
            }
            QUEUE.remove(drop != null ? drop : QUEUE.peekFirst());
        }
    }

    private static void playSound(Minecraft mc, int style) {
        SoundEvent sound;
        float pitch, volume;
        switch (style) {
            case Announce.STYLE_ULTIMATE -> { sound = SoundEvents.ILLUSIONER_PREPARE_MIRROR; pitch = 0.9F; volume = 0.5F; }
            case Announce.STYLE_BOSS -> { sound = SoundEvents.EVOKER_PREPARE_SUMMON; pitch = 0.6F; volume = 0.45F; }
            case Announce.STYLE_SYSTEM -> { sound = SoundEvents.AMETHYST_BLOCK_CHIME; pitch = 1.4F; volume = 0.6F; }
            default -> { return; }   // magia comum: o som da conjuração já toca no mundo
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // =====================================================================================
    // Tick
    // =====================================================================================

    static void tick() {
        if (Minecraft.getInstance().isPaused()) return;
        if (current == null) {
            current = QUEUE.pollFirst();
            return;
        }
        // Com fila, o atual acelera depois de um tempo mínimo de leitura
        float speed = QUEUE.isEmpty() || current.age < 22 ? 1F : 2.5F;
        current.age += speed;
        if (current.age >= current.life) current = QUEUE.pollFirst();
    }

    // =====================================================================================
    // Desenho
    // =====================================================================================

    /** Camada de HUD "cinematic_announce". */
    static void render(GuiGraphics g, DeltaTracker delta) {
        Banner b = current;
        Minecraft mc = Minecraft.getInstance();
        if (b == null || mc.options.hideGui || mc.player == null) return;
        float pt = delta.getGameTimeDeltaPartialTick(true);   // pausado: o timer congela a fração sozinho
        float age = Math.min(b.age + pt, b.life);
        float alpha = Math.min(Mth.clamp(age / 5F, 0F, 1F), Mth.clamp((b.life - age) / 10F, 0F, 1F));
        if (alpha <= 0.02F) return;
        Font font = mc.font;
        switch (b.style) {
            case Announce.STYLE_ULTIMATE -> renderUltimate(g, font, b, age, alpha);
            case Announce.STYLE_BOSS -> renderBoss(g, font, b, age, alpha);
            case Announce.STYLE_SYSTEM -> renderSystem(g, font, b, age, alpha);
            default -> renderSpell(g, font, b, age, alpha);
        }
    }

    /** Nome da magia grande, com contorno, linhas laterais e losangos. */
    private static void renderSpell(GuiGraphics g, Font font, Banner b, float age, float alpha) {
        int w = g.guiWidth(), h = g.guiHeight();
        FormattedCharSequence seq = b.text.getVisualOrderText();
        int tw = Math.max(1, font.width(seq));
        float scale = Math.min(2.0F, w * 0.8F / tw) * Mth.lerp(CinematicDraw.easeOut(age / 6F), 1.4F, 1.0F);
        float cx = w / 2F, cy = h * 0.2F - (1F - alpha) * 4F;
        float half = tw * scale / 2F;

        CinematicDraw.glow(g, cx, cy, half * 2.6F + 40, 9 * scale * 2.6F, b.rgb, 0.22F * alpha);
        float line = 46F * CinematicDraw.easeOut(age / 10F);
        CinematicDraw.fadingLine(g, cx - half - 10 - line, cx - half - 10, cy - 0.5F, 1.5F, b.rgb, 0.85F * alpha, true);
        CinematicDraw.fadingLine(g, cx + half + 10, cx + half + 10 + line, cy - 0.5F, 1.5F, b.rgb, 0.85F * alpha, false);
        CinematicDraw.diamond(g, cx - half - 7, cy + 0.25F, 7, b.rgb, alpha);
        CinematicDraw.diamond(g, cx + half + 7, cy + 0.25F, 7, b.rgb, alpha);

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0F);
        ps.scale(scale, scale, 1F);
        int color = CinematicDraw.argb(CinematicDraw.mix(0xFFFFFF, b.rgb, Mth.clamp(age / 4F, 0F, 1F)), alpha);
        CinematicDraw.outlined(g, font, seq, -tw / 2F, -4.5F, color, CinematicDraw.argb(CinematicDraw.darken(b.rgb, 0.8F), alpha * 0.9F));
        ps.popPose();
        renderSubtitle(g, font, b, cx, cy + 9 * scale / 2F + 6, alpha);
    }

    /** Ultimate: maior, letras se aproximando, brilho pulsante e reflexo correndo pelas letras. */
    private static void renderUltimate(GuiGraphics g, Font font, Banner b, float age, float alpha) {
        int w = g.guiWidth(), h = g.guiHeight();
        String text = b.text.getString();
        int[] cps = text.codePoints().toArray();
        if (cps.length == 0) return;
        float spacing = Mth.lerp(CinematicDraw.easeOut(age / 12F), 5F, 0.8F);
        float raw = 0;
        float[] widths = new float[cps.length];
        for (int i = 0; i < cps.length; i++) {
            widths[i] = font.width(FormattedCharSequence.forward(new String(Character.toChars(cps[i])), CinematicDraw.BOLD));
            raw += widths[i];
        }
        float finalWidth = raw + 0.8F * (cps.length - 1);
        float total = raw + spacing * (cps.length - 1);
        float scale = Math.min(3.0F, w * 0.86F / Math.max(1F, finalWidth)) * Mth.lerp(CinematicDraw.easeOutBack(age / 7F), 0.6F, 1.0F);
        float cx = w / 2F, cy = h * 0.19F;
        float pulse = 0.75F + 0.25F * Mth.sin(age * 0.35F);

        CinematicDraw.glow(g, cx, cy, finalWidth * scale * 1.5F + 60, 9 * scale * 3.4F, b.rgb, 0.55F * alpha * pulse);
        CinematicDraw.glow(g, cx, cy, finalWidth * scale * 0.9F + 20, 9 * scale * 1.6F, 0xFFFFFF, 0.18F * alpha * pulse);
        float bar = (finalWidth * scale / 2F + 24) * CinematicDraw.easeOut(age / 9F);
        CinematicDraw.fadingLine(g, cx - bar, cx, cy - 9 * scale / 2F - 5, 1F, b.rgb, 0.9F * alpha, true);
        CinematicDraw.fadingLine(g, cx, cx + bar, cy - 9 * scale / 2F - 5, 1F, b.rgb, 0.9F * alpha, false);
        CinematicDraw.fadingLine(g, cx - bar, cx, cy + 9 * scale / 2F + 4, 1F, b.rgb, 0.9F * alpha, true);
        CinematicDraw.fadingLine(g, cx, cx + bar, cy + 9 * scale / 2F + 4, 1F, b.rgb, 0.9F * alpha, false);
        CinematicDraw.diamond(g, cx, cy - 9 * scale / 2F - 4.5F, 9, b.rgb, alpha);
        CinematicDraw.diamond(g, cx, cy + 9 * scale / 2F + 4.5F, 9, b.rgb, alpha);

        // Reflexo branco atravessando o texto
        float band = Mth.lerp(Mth.clamp((age - 6F) / 20F, 0F, 1F), -0.3F, 1.3F);
        int outline = CinematicDraw.argb(CinematicDraw.darken(b.rgb, 0.82F), alpha);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0F);
        ps.scale(scale, scale, 1F);
        float x = -total / 2F;
        for (int i = 0; i < cps.length; i++) {
            float f = (x + total / 2F + widths[i] / 2F) / Math.max(1F, total);
            float shine = Math.max(0F, 1F - Math.abs(f - band) / 0.14F);
            int rgb = CinematicDraw.mix(CinematicDraw.mix(b.rgb, 0xFFFFFF, 0.15F), 0xFFFFFF, shine * 0.85F + Mth.clamp(1F - age / 4F, 0F, 1F));
            FormattedCharSequence ch = FormattedCharSequence.forward(new String(Character.toChars(cps[i])), CinematicDraw.BOLD);
            CinematicDraw.outlined(g, font, ch, x, -4.5F, CinematicDraw.argb(rgb, alpha), outline);
            x += widths[i] + spacing;
        }
        ps.popPose();
        renderSubtitle(g, font, b, cx, cy + 9 * scale / 2F + 10, alpha);
    }

    /** Fala de chefe: painel escuro com moldura, nome de quem fala e texto aparecendo letra a letra. */
    private static void renderBoss(GuiGraphics g, Font font, Banner b, float age, float alpha) {
        int w = g.guiWidth(), h = g.guiHeight();
        float textScale = 1.25F;
        int maxLine = (int) (Math.min(w * 0.7F, 340) / textScale);
        String full = b.text.getString();
        int shown = Math.min(full.length(), (int) (age * 2.2F));
        List<String> lines = wrap(font, full, maxLine);
        int widest = 0;
        for (String l : lines) widest = Math.max(widest, font.width(l));
        float panelW = Math.max(widest * textScale, b.speaker != null ? font.width(b.speaker) + 16 : 0) + 28;
        float panelH = lines.size() * 11 * textScale + 16 + (b.speaker != null ? 6 : 0);
        float cx = w / 2F;
        float top = h * 0.13F + (1F - CinematicDraw.easeOut(age / 6F)) * -10F;
        float left = cx - panelW / 2F, right = cx + panelW / 2F, bottom = top + panelH;

        CinematicDraw.glow(g, cx, top + panelH / 2F, panelW * 1.4F, panelH * 2.2F, b.rgb, 0.25F * alpha);
        g.fill((int) left, (int) top, (int) right, (int) bottom, CinematicDraw.argb(0x07040C, 0.78F * alpha));
        CinematicDraw.fillGradientH(g, left, top, cx, top + 1, CinematicDraw.argb(b.rgb, 0F), CinematicDraw.argb(b.rgb, alpha));
        CinematicDraw.fillGradientH(g, cx, top, right, top + 1, CinematicDraw.argb(b.rgb, alpha), CinematicDraw.argb(b.rgb, 0F));
        CinematicDraw.fillGradientH(g, left, bottom - 1, cx, bottom, CinematicDraw.argb(b.rgb, 0F), CinematicDraw.argb(b.rgb, alpha));
        CinematicDraw.fillGradientH(g, cx, bottom - 1, right, bottom, CinematicDraw.argb(b.rgb, alpha), CinematicDraw.argb(b.rgb, 0F));
        CinematicDraw.corner(g, left - 2, top - 2, 12, 0, b.rgb, alpha);
        CinematicDraw.corner(g, right + 2, top - 2, 12, 1, b.rgb, alpha);
        CinematicDraw.corner(g, right + 2, bottom + 2, 12, 2, b.rgb, alpha);
        CinematicDraw.corner(g, left - 2, bottom + 2, 12, 3, b.rgb, alpha);

        float y = top + 8;
        if (b.speaker != null) {
            Component name = b.speaker.copy().withStyle(CinematicDraw.BOLD);
            FormattedCharSequence seq = name.getVisualOrderText();
            float nx = cx - font.width(seq) / 2F;
            // placa do nome em cima da moldura
            g.fill((int) nx - 6, (int) top - 6, (int) (nx + font.width(seq)) + 6, (int) top + 5, CinematicDraw.argb(CinematicDraw.darken(b.rgb, 0.65F), 0.95F * alpha));
            CinematicDraw.outlined(g, font, seq, nx, top - 4, CinematicDraw.argb(CinematicDraw.mix(b.rgb, 0xFFFFFF, 0.55F), alpha), CinematicDraw.argb(0x000000, 0.6F * alpha));
            y += 6;
        }
        PoseStack ps = g.pose();
        int consumed = 0;
        for (String line : lines) {
            int visible = Mth.clamp(shown - consumed, 0, line.length());
            consumed += line.length() + 1;
            if (visible <= 0) break;
            String part = line.substring(0, visible);
            ps.pushPose();
            ps.translate(cx - font.width(line) * textScale / 2F, y, 0F);
            ps.scale(textScale, textScale, 1F);
            CinematicDraw.shadowed(g, font, FormattedCharSequence.forward(part, b.text.getStyle()), 0, 0, CinematicDraw.argb(0xF2ECE0, alpha));
            ps.popPose();
            y += 11 * textScale;
        }
        renderSubtitle(g, font, b, cx, bottom + 8, alpha);
    }

    /** Aviso discreto: faixa fina translúcida no alto da tela. */
    private static void renderSystem(GuiGraphics g, Font font, Banner b, float age, float alpha) {
        int w = g.guiWidth(), h = g.guiHeight();
        FormattedCharSequence seq = b.text.getVisualOrderText();
        int tw = font.width(seq);
        float cx = w / 2F, y = h * 0.1F - (1F - CinematicDraw.easeOut(age / 6F)) * 6F;
        float half = tw / 2F + 30;
        CinematicDraw.fillGradientH(g, cx - half, y - 3, cx, y + 11, CinematicDraw.argb(0x000000, 0F), CinematicDraw.argb(0x000000, 0.5F * alpha));
        CinematicDraw.fillGradientH(g, cx, y - 3, cx + half, y + 11, CinematicDraw.argb(0x000000, 0.5F * alpha), CinematicDraw.argb(0x000000, 0F));
        CinematicDraw.shadowed(g, font, seq, cx - tw / 2F, y, CinematicDraw.argb(b.rgb, alpha));
        renderSubtitle(g, font, b, cx, y + 14, alpha);
    }

    private static void renderSubtitle(GuiGraphics g, Font font, Banner b, float cx, float y, float alpha) {
        if (b.subtitle == null) return;
        FormattedCharSequence seq = b.subtitle.getVisualOrderText();
        CinematicDraw.shadowed(g, font, seq, cx - font.width(seq) / 2F, y, CinematicDraw.argb(CinematicDraw.mix(b.rgb, 0xFFFFFF, 0.65F), alpha));
    }

    /** Quebra simples por palavras (mantém o texto exato para o efeito de máquina de escrever). */
    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = cur.isEmpty() ? word : cur + " " + word;
            if (font.width(candidate) > maxWidth && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(candidate);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private AnnounceOverlay() {}
}
