package com.renanpontes.isekaimahou.client.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.status.StatusPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Condições de status das entidades no CLIENTE (sincronizadas pelo servidor para quem as rastreia, inclusive o próprio
 * jogador). Use para desenhar ícones (Olho Analítico, barras flutuantes, HUD do jogador).
 *
 * <pre>
 * for (ClientStatuses.Entry s : ClientStatuses.get(entidade)) ClientStatuses.drawIcon(g, s.kind(), s.stacks(), x += 11, y, 10);
 * ClientStatuses.setWorldIconsHandledExternally(true);   // o seu módulo desenha os ícones sobre os mobs
 * </pre>
 */
public final class ClientStatuses {
    /** Uma condição: tipo, acúmulos e ticks restantes (estimados no cliente; -1 = infinita). */
    public record Entry(Statuses.Kind kind, int stacks, int ticksLeft) {}

    private record Data(List<StatusPayloads.Entry> raw, long received) {}

    private static final Map<Integer, Data> BY_ID = new HashMap<>();
    private static ClientLevel trackedLevel;
    private static boolean external;
    private static int cleanupTimer;

    /** Condições da entidade (lista vazia se não tiver nenhuma). */
    public static List<Entry> get(Entity e) {
        return e == null ? List.of() : get(e.getId());
    }

    public static List<Entry> get(int entityId) {
        Data d = BY_ID.get(entityId);
        Minecraft mc = Minecraft.getInstance();
        if (d == null || mc.level == null || mc.level != trackedLevel) return List.of();
        long now = mc.level.getGameTime();
        List<Entry> out = new ArrayList<>(d.raw.size());
        for (StatusPayloads.Entry r : d.raw) {
            Statuses.Kind k = Statuses.Kind.byOrdinal(r.kind());
            if (k == null) continue;
            int left = r.ticks() < 0 ? -1 : (int) Math.max(0, r.ticks() - (now - d.received));
            out.add(new Entry(k, r.stacks(), left));
        }
        return out;
    }

    /** Acúmulos de um tipo na entidade (0 = não tem). */
    public static int stacks(Entity e, Statuses.Kind kind) {
        for (Entry en : get(e)) if (en.kind() == kind) return en.stacks();
        return 0;
    }

    /** Textura 18×18 do ícone: {@code isekai_mahou:textures/mob_effect/<id>.png}. */
    public static ResourceLocation icon(Statuses.Kind kind) {
        return IsekaiMahou.id("textures/mob_effect/" + kind.id() + ".png");
    }

    /** O seu módulo desenha os ícones de status sobre os mobs (o núcleo para de desenhar os dele). */
    public static void setWorldIconsHandledExternally(boolean handled) {
        external = handled;
    }

    public static boolean worldIconsHandledExternally() {
        return external;
    }

    /**
     * Desenha o ícone (quadrado {@code size}) com o número de acúmulos no canto quando > 1. Sem a textura, desenha um
     * selo colorido com a inicial do nome.
     */
    public static void drawIcon(GuiGraphics g, Statuses.Kind kind, int stacks, int x, int y, int size) {
        ResourceLocation tex = icon(kind);
        if (GlyphArt.exists(tex)) {
            g.blit(tex, x, y, size, size, 0, 0, 18, 18, 18, 18);
        } else {
            int c = 0xFF000000 | kind.color();
            g.fill(x + 1, y, x + size - 1, y + size, c);
            g.fill(x, y + 1, x + size, y + size - 1, c);
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF000000 | darken(kind.color()));
            Font font = Minecraft.getInstance().font;
            String letter = kind.displayName(stacks).getString();
            letter = letter.isEmpty() ? "?" : letter.substring(0, 1);
            g.drawString(font, letter, x + (size - font.width(letter)) / 2 + 1, y + (size - 8) / 2 + 1, 0xFFFFFFFF, true);
        }
        if (stacks > 1) {
            Font font = Minecraft.getInstance().font;
            String n = Integer.toString(stacks);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, n, x + size - font.width(n) + 1, y + size - 7, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
    }

    private static int darken(int rgb) {
        int r = (rgb >> 16) & 255, gg = (rgb >> 8) & 255, b = rgb & 255;
        return ((r * 6 / 10) << 16) | ((gg * 6 / 10) << 8) | (b * 6 / 10);
    }

    // =====================================================================================
    //  Interno (pacotes e limpeza)
    // =====================================================================================
    static Set<Integer> trackedIds() {
        return BY_ID.keySet();
    }

    static void receive(StatusPayloads.StatusList p) {
        ensureLevel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (p.entries().isEmpty()) BY_ID.remove(p.entityId());
        else BY_ID.put(p.entityId(), new Data(List.copyOf(p.entries()), mc.level.getGameTime()));
    }

    /** Troca de mundo/dimensão: ids de entidade antigos não valem mais. */
    static void ensureLevel() {
        ClientLevel now = Minecraft.getInstance().level;
        if (now != trackedLevel) {
            BY_ID.clear();
            trackedLevel = now;
        }
    }

    static void tick() {
        ensureLevel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || BY_ID.isEmpty() || ++cleanupTimer < 40) return;
        cleanupTimer = 0;
        // Entidades que sumiram do cliente (morreram, saíram do alcance) param de ser acompanhadas
        BY_ID.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    /**
     * A entidade saiu do mundo do cliente (morreu, saiu do alcance): esquece as condições dela na hora. Se ela voltar ao
     * alcance, o servidor manda a lista atual de novo (ao começar a rastrear).
     */
    static void forget(Entity e) {
        if (e.level() == trackedLevel) BY_ID.remove(e.getId());
    }

    static void clear() {
        BY_ID.clear();
        trackedLevel = null;
        cleanupTimer = 0;
    }

    private ClientStatuses() {}
}
