package com.renanpontes.isekaimahou.feature.cinematic.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.network.ClientMagic;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Textos NO MUNDO, virados para a câmera, sobre a cabeça das entidades:
 * <ul>
 *   <li>balão do anúncio (nome da magia / fala) por ~3 s, com rabicho apontando para quem "gritou";</li>
 *   <li>cântico aparecendo palavra por palavra durante a conjuração (com notas e runas de encantamento).</li>
 * </ul>
 */
final class WorldLabels {
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final Style ITALIC = Style.EMPTY.withItalic(true);
    /** Escala pentatônica menor (semitons): cada palavra do cântico sobe uma nota. */
    private static final int[] SCALE = {0, 3, 5, 7, 10, 12, 15, 17, 19, 22, 24};
    private static final int CHANT_HOLD = 30, CHANT_FADE = 10, BROKEN_FADE = 14;
    private static final int CHANT_WIDTH = 150, BUBBLE_WIDTH = 200;
    /** Cântico maior que isso (só via comando; o núcleo limita a 120) é cortado para não cobrir a tela. */
    private static final int MAX_CHANT_CHARS = 240;

    private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();
    private static final Map<Integer, Chant> CHANTS = new HashMap<>();
    private static final RandomSource RNG = RandomSource.create();
    private static MultiBufferSource.BufferSource buffers;
    /** Mundo a que os ids dos mapas pertencem: ids de entidade se repetem entre dimensões. */
    private static ClientLevel trackedLevel;

    private static final class Bubble {
        final List<FormattedCharSequence> lines;
        final int width;
        final int rgb;
        final int style;
        final int life;
        int age;

        Bubble(List<FormattedCharSequence> lines, int width, int rgb, int style) {
            this.lines = lines;
            this.width = width;
            this.rgb = rgb & 0xFFFFFF;
            this.style = style;
            this.life = switch (style) {
                case Announce.STYLE_ULTIMATE -> 70;
                case Announce.STYLE_BOSS -> 90;
                case Announce.STYLE_SYSTEM -> 50;
                default -> 60;
            };
        }
    }

    private static final class Chant {
        final String[] words;
        final float[] wordX;      // posição x de cada palavra na linha (layout fixo: as linhas não "pulam")
        final int[] wordLine;
        final int[] wordWidth;
        final int[] lineWidth;
        final int ticks;
        final int rgb;
        final boolean own;
        int age;
        int shown;
        int brokenAt = -1;
        boolean sawCast;

        Chant(String[] words, Font font, int ticks, int rgb, boolean own) {
            this.words = words;
            this.ticks = Math.max(1, ticks);
            this.rgb = rgb;
            this.own = own;
            this.wordX = new float[words.length];
            this.wordLine = new int[words.length];
            this.wordWidth = new int[words.length];
            List<Integer> widths = new ArrayList<>();
            int space = font.width(FormattedCharSequence.forward(" ", ITALIC));
            int line = 0, x = 0;
            for (int i = 0; i < words.length; i++) {
                wordWidth[i] = font.width(FormattedCharSequence.forward(words[i], ITALIC));
                if (x > 0 && x + space + wordWidth[i] > CHANT_WIDTH) {
                    widths.add(x);
                    line++;
                    x = 0;
                }
                if (x > 0) x += space;
                wordX[i] = x;
                wordLine[i] = line;
                x += wordWidth[i];
            }
            widths.add(x);
            this.lineWidth = widths.stream().mapToInt(Integer::intValue).toArray();
        }

        /** Quantas palavras já deveriam estar visíveis (a última aparece a 85% da conjuração). */
        int due() {
            if (words.length <= 1) return words.length;
            float span = ticks * 0.85F;
            if (age >= span) return words.length;
            return 1 + (int) ((words.length - 1) * (age / span));
        }

        boolean done() {
            return brokenAt >= 0 ? age - brokenAt > BROKEN_FADE : age > ticks + CHANT_HOLD + CHANT_FADE;
        }

        int linesShown() {
            return shown <= 0 ? 0 : wordLine[shown - 1] + 1;
        }
    }

    // =====================================================================================
    // Entrada
    // =====================================================================================

    /** Balão de anúncio sobre a cabeça (substitui o anterior da mesma entidade). */
    static void bubble(Entity source, Component text, int rgb, int style) {
        if (!(source.level() instanceof ClientLevel level)) return;
        syncLevel(level);
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(text, BUBBLE_WIDTH);
        if (lines.isEmpty()) return;
        if (lines.size() > 3) lines = new ArrayList<>(lines.subList(0, 3));
        int width = 0;
        for (FormattedCharSequence l : lines) width = Math.max(width, font.width(l));
        BUBBLES.put(source.getId(), new Bubble(lines, width, rgb, style));
    }

    /** Gancho do pacote Chant (ClientHooks.chant). */
    static void onChant(CorePayloads.Chant p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        syncLevel(mc.level);
        Entity caster = mc.level.getEntity(p.entityId());
        String text = p.chant().trim();
        if (caster == null || text.isEmpty() || p.ticks() <= 0) return;
        boolean own = caster == mc.player;
        if (!own && !CinematicSettings.othersAnnouncements()) return;   // mesma opção dos nomes de magia dos outros
        if (text.length() > MAX_CHANT_CHARS) text = text.substring(0, MAX_CHANT_CHARS);
        String[] words = splitWords(mc.font, text);
        Chant c = new Chant(words, mc.font, p.ticks(), chantColor(own), own);
        c.sawCast = own && ClientMagic.castSlot >= 0;
        CHANTS.put(caster.getId(), c);
        revealWords(mc.level, caster, c);
    }

    /** Cor do cântico: a do elemento ativo (próprio jogador) ou um dourado de pergaminho. */
    private static int chantColor(boolean own) {
        if (own) {
            try {
                Element e = ClientMagic.data().activeElement();
                return CinematicDraw.mix(e.color, 0xFFFFFF, 0.45F);
            } catch (RuntimeException ignored) {
                // roda vazia/dados ainda não sincronizados: usa a cor padrão
            }
        }
        return 0xF0E2B6;
    }

    /** Palavras do cântico; uma "palavra" mais larga que a linha é quebrada em pedaços (senão o balão vira uma faixa enorme). */
    private static String[] splitWords(Font font, String text) {
        List<String> out = new ArrayList<>();
        for (String word : text.split("\\s+")) {
            String rest = word;
            while (font.width(FormattedCharSequence.forward(rest, ITALIC)) > CHANT_WIDTH) {
                String head = font.plainSubstrByWidth(rest, CHANT_WIDTH - 4);
                if (head.isEmpty()) head = rest.substring(0, 1);
                out.add(head);
                rest = rest.substring(head.length());
            }
            if (!rest.isEmpty()) out.add(rest);
        }
        return out.toArray(new String[0]);
    }

    /** Trocou de mundo (dimensão/servidor): os ids antigos apontariam para outras entidades. */
    private static void syncLevel(ClientLevel level) {
        if (level != trackedLevel) {
            BUBBLES.clear();
            CHANTS.clear();
            trackedLevel = level;
        }
    }

    static void clear() {
        BUBBLES.clear();
        CHANTS.clear();
        trackedLevel = null;
    }

    // =====================================================================================
    // Tick
    // =====================================================================================

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            clear();
            return;
        }
        syncLevel(level);
        if (mc.isPaused()) return;

        BUBBLES.entrySet().removeIf(en -> {
            Entity e = level.getEntity(en.getKey());
            return e == null || e.isRemoved() || ++en.getValue().age >= en.getValue().life;
        });

        Iterator<Map.Entry<Integer, Chant>> it = CHANTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Chant> en = it.next();
            Entity caster = level.getEntity(en.getKey());
            Chant c = en.getValue();
            if (caster == null || caster.isRemoved() || (caster instanceof Player p && p.isDeadOrDying())) {
                it.remove();
                continue;
            }
            c.age++;
            if (c.brokenAt < 0) {
                revealWords(level, caster, c);
                if (c.age == c.ticks) completeChant(level, caster);
                // Só sabemos da interrupção da NOSSA conjuração (estado da barra do núcleo)
                if (c.own) {
                    if (ClientMagic.castSlot >= 0) c.sawCast = true;
                    else if (c.sawCast && c.age < c.ticks - 3) c.brokenAt = c.age;
                }
            }
            if (c.done()) it.remove();
        }
    }

    private static void revealWords(ClientLevel level, Entity caster, Chant c) {
        int due = c.due();
        while (c.shown < due) {
            int i = c.shown++;
            onWord(level, caster, i, c.words.length);
        }
    }

    /** Cada palavra: uma nota subindo a escala + runas de encantamento voando para a cabeça. */
    private static void onWord(ClientLevel level, Entity caster, int index, int total) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 head = caster.getEyePosition().add(0, 0.3, 0);
        if (mc.player != null && mc.player.distanceToSqr(caster) < 24 * 24) {
            int step = SCALE[Math.min(SCALE.length - 1, (int) ((float) index / Math.max(1, total - 1) * (SCALE.length - 1)))];
            float pitch = 0.5F * (float) Math.pow(2.0, step / 12.0);
            level.playLocalSound(head.x, head.y, head.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.35F, pitch, false);
        }
        int count = CinematicSettings.reducedParticles() ? 2 : 7;
        for (int k = 0; k < count; k++) {
            // ENCHANT: a partícula nasce em (pos + vel) e voa até pos
            level.addParticle(ParticleTypes.ENCHANT, head.x, head.y, head.z,
                    (RNG.nextFloat() - 0.5F) * 2.6F, RNG.nextFloat() * 1.2F + 0.2F, (RNG.nextFloat() - 0.5F) * 2.6F);
        }
    }

    /** Cântico completo: brilho final. */
    private static void completeChant(ClientLevel level, Entity caster) {
        Vec3 head = caster.getEyePosition().add(0, 0.4, 0);
        int count = CinematicSettings.reducedParticles() ? 3 : 12;
        for (int k = 0; k < count; k++) {
            double a = k * Mth.TWO_PI / count;
            level.addParticle(ParticleTypes.ENCHANTED_HIT, head.x + Math.cos(a) * 0.5, head.y, head.z + Math.sin(a) * 0.5,
                    Math.cos(a) * 0.15, 0.05, Math.sin(a) * 0.15);
        }
    }

    // =====================================================================================
    // Desenho no mundo
    // =====================================================================================

    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (BUBBLES.isEmpty() && CHANTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(4096));

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Font font = mc.font;
        PoseStack ps = new PoseStack();
        Set<Integer> ids = new HashSet<>(BUBBLES.keySet());
        ids.addAll(CHANTS.keySet());

        for (int id : ids) {
            Entity ent = mc.level.getEntity(id);
            if (ent == null) continue;
            if (ent == cam.getEntity() && !cam.isDetached()) continue;   // a própria cabeça em 1ª pessoa
            Vec3 pos = ent.getPosition(pt);
            boolean nameTag = ent.shouldShowName() || (ent instanceof Player && ent != mc.player);
            Vec3 anchor = pos.add(0, ent.getBbHeight() + (nameTag ? 0.75 : 0.45), 0);
            double dist = anchor.distanceTo(camPos);
            if (dist > 64) continue;
            float distScale = 1F + Mth.clamp((float) (dist - 6.0) / 26F, 0F, 1F) * 1.3F;

            float lift = 0F;
            Chant chant = CHANTS.get(id);
            if (chant != null) lift = drawChant(ps, font, chant, anchor, cam, camPos, pt, distScale);
            Bubble bubble = BUBBLES.get(id);
            if (bubble != null) drawBubble(ps, font, bubble, anchor.add(0, lift, 0), cam, camPos, pt, distScale);
        }
        buffers.endBatch();
    }

    /** Prepara o PoseStack: no ponto do mundo, virado para a câmera, em "unidades de fonte". */
    private static void billboard(PoseStack ps, Vec3 at, Camera cam, Vec3 camPos, float scale) {
        ps.translate(at.x - camPos.x, at.y - camPos.y, at.z - camPos.z);
        ps.mulPose(cam.rotation());
        ps.scale(scale, -scale, scale);
    }

    /** Balão do anúncio. A âncora é a ponta do rabicho. */
    private static void drawBubble(PoseStack ps, Font font, Bubble b, Vec3 anchor, Camera cam, Vec3 camPos, float pt, float distScale) {
        float age = Math.min(b.age + pt, b.life);
        float alpha = Math.min(Mth.clamp(age / 4F, 0F, 1F), Mth.clamp((b.life - age) / 10F, 0F, 1F));
        if (alpha <= 0.02F) return;
        float base = switch (b.style) {
            case Announce.STYLE_ULTIMATE -> 0.034F;
            case Announce.STYLE_BOSS -> 0.031F;
            case Announce.STYLE_SYSTEM -> 0.022F;
            default -> 0.027F;
        };
        float pop = Mth.lerp(CinematicDraw.easeOutBack(age / 6F), 0.3F, 1F);
        float rise = age / b.life * 0.25F;

        ps.pushPose();
        billboard(ps, anchor.add(0, rise, 0), cam, camPos, base * distScale * pop);
        Matrix4f m = ps.last().pose();
        float padX = 5, padY = 3, tail = 4;
        float height = b.lines.size() * 10 - 1;
        float left = -b.width / 2F - padX, right = b.width / 2F + padX;
        float bottom = -tail, top = bottom - height - padY * 2;

        int bg = b.style == Announce.STYLE_BOSS ? 0x1A0508 : 0x0A0812;
        float bgAlpha = (b.style == Announce.STYLE_SYSTEM ? 0.4F : 0.62F) * alpha;
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackgroundSeeThrough());
        rect(vc, m, left, top, right, bottom, 0.03F, CinematicDraw.argb(bg, bgAlpha));
        triangle(vc, m, -3.5F, bottom, 3.5F, 0F, bottom + tail, 0.03F, CinematicDraw.argb(bg, bgAlpha));
        if (b.style != Announce.STYLE_SYSTEM) {
            float pulse = b.style == Announce.STYLE_ULTIMATE ? 0.7F + 0.3F * Mth.sin((b.age + pt) * 0.4F) : 1F;
            float t = b.style == Announce.STYLE_ULTIMATE ? 1.5F : 1F;
            int border = CinematicDraw.argb(b.rgb, 0.95F * alpha * pulse);
            rect(vc, m, left - t, top - t, right + t, top, 0.02F, border);
            rect(vc, m, left - t, bottom, -3.5F, bottom + t, 0.02F, border);
            rect(vc, m, 3.5F, bottom, right + t, bottom + t, 0.02F, border);
            rect(vc, m, left - t, top, left, bottom, 0.02F, border);
            rect(vc, m, right, top, right + t, bottom, 0.02F, border);
            // contorno do rabicho: duas tiras finas por fora (sem sobrepor o fundo: a ordem dentro do lote não é garantida)
            float cy = bottom + tail / 3F;
            edge(vc, m, -3.5F, bottom, 0F, bottom + tail, t, 0F, cy, 0.02F, border);
            edge(vc, m, 3.5F, bottom, 0F, bottom + tail, t, 0F, cy, 0.02F, border);
        }

        int textRgb = b.style == Announce.STYLE_BOSS ? CinematicDraw.mix(b.rgb, 0xFFFFFF, 0.6F) : CinematicDraw.mix(b.rgb, 0xFFFFFF, 0.2F);
        float y = top + padY;
        for (FormattedCharSequence line : b.lines) {
            float x = -font.width(line) / 2F;
            text(font, line, x, y, CinematicDraw.argb(textRgb, 0.3F * alpha), m, Font.DisplayMode.SEE_THROUGH);
            text(font, line, x, y, CinematicDraw.argb(textRgb, alpha), m, Font.DisplayMode.NORMAL);
            y += 10;
        }
        ps.popPose();
    }

    /** Cântico (sem rabicho). Retorna a altura ocupada no mundo (para o balão ir por cima). */
    private static float drawChant(PoseStack ps, Font font, Chant c, Vec3 anchor, Camera cam, Vec3 camPos, float pt, float distScale) {
        int lines = c.linesShown();
        if (lines == 0) return 0F;
        float age = c.age + pt;
        boolean broken = c.brokenAt >= 0;
        float alpha;
        if (broken) alpha = Mth.clamp(1F - (age - c.brokenAt) / BROKEN_FADE, 0F, 1F);
        else alpha = Mth.clamp((c.ticks + CHANT_HOLD + CHANT_FADE - age) / CHANT_FADE, 0F, 1F);
        if (alpha <= 0.02F) return 0F;
        float scale = 0.022F * distScale;
        float drop = broken ? (age - c.brokenAt) * 0.6F : 0F;          // palavras "caem" quando o cântico quebra
        float glowPulse = !broken && age >= c.ticks ? Mth.clamp(1F - (age - c.ticks) / 10F, 0F, 1F) : 0F;

        ps.pushPose();
        billboard(ps, anchor, cam, camPos, scale);
        Matrix4f m = ps.last().pose();
        float height = lines * 10 - 1;
        int widest = 0;
        for (int l = 0; l < lines; l++) widest = Math.max(widest, c.lineWidth[l]);
        float padX = 4, padY = 2;
        float left = -widest / 2F - padX, right = widest / 2F + padX, bottom = 0F, top = -height - padY * 2;
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackgroundSeeThrough());
        int edge = broken ? 0x555555 : CinematicDraw.mix(c.rgb, 0xFFFFFF, glowPulse);
        rect(vc, m, left, top, right, bottom, 0.03F, CinematicDraw.argb(0x06040A, 0.38F * alpha));
        rect(vc, m, left, bottom, right, bottom + 0.7F, 0.02F, CinematicDraw.argb(edge, (0.55F + 0.45F * glowPulse) * alpha));

        int newest = c.shown - 1;
        for (int i = 0; i < c.shown; i++) {
            int line = c.wordLine[i];
            float x = -c.lineWidth[line] / 2F + c.wordX[i];
            float y = top + padY + line * 10 + drop;
            int rgb;
            float wordAlpha = alpha;
            float rise = 0F;
            if (broken) {
                rgb = 0x8A8A8A;
            } else {
                rgb = CinematicDraw.mix(c.rgb, 0xFFFFFF, glowPulse * 0.8F);
                if (i == newest && glowPulse == 0F) {
                    // A palavra recém-dita chega branca e "pousa" no lugar
                    float since = Math.min(age - wordTime(c, i), 6F) / 6F;
                    rgb = CinematicDraw.mix(0xFFFFFF, c.rgb, CinematicDraw.smooth(since));
                    wordAlpha *= Mth.clamp(since * 2F, 0.25F, 1F);
                    rise = (1F - CinematicDraw.easeOut(since)) * -4F;
                }
            }
            FormattedCharSequence seq = FormattedCharSequence.forward(c.words[i], ITALIC);
            text(font, seq, x, y + rise, CinematicDraw.argb(rgb, 0.25F * wordAlpha), m, Font.DisplayMode.SEE_THROUGH);
            text(font, seq, x, y + rise, CinematicDraw.argb(rgb, wordAlpha), m, Font.DisplayMode.NORMAL);
        }
        ps.popPose();
        return (height + padY * 2 + 3) * scale;
    }

    /** Texto no mundo; alpha quase zero é pulado (o Font trata alpha < 4 como OPACO e o fim do fade "piscaria"). */
    private static void text(Font font, FormattedCharSequence seq, float x, float y, int argb, Matrix4f m, Font.DisplayMode mode) {
        if ((argb >>> 24) < 5) return;
        font.drawInBatch(seq, x, y, argb, false, m, buffers, mode, 0, LIGHT);
    }

    /** Momento (em ticks) em que a palavra i apareceu. */
    private static float wordTime(Chant c, int i) {
        if (c.words.length <= 1 || i == 0) return 0F;
        return (float) i / (c.words.length - 1) * c.ticks * 0.85F;
    }

    /** Retângulo alinhado aos eixos (em unidades de fonte; y cresce para baixo). */
    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        quad(vc, m, x0, y1, x1, y1, x1, y0, x0, y0, z, argb);
    }

    /** Triângulo (quad degenerado) com base de (x0,y0) a (x1,y0) e ponta em (tipX, tipY). */
    private static void triangle(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float tipX, float tipY, float z, int argb) {
        quad(vc, m, tipX, tipY, tipX, tipY, x1, y0, x0, y0, z, argb);
    }

    /** Tira de espessura t ao longo do segmento a→b, deslocada para o lado oposto a (awayX, awayY). */
    private static void edge(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float t,
                             float awayX, float awayY, float z, int argb) {
        float dx = bx - ax, dy = by - ay;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4F) return;
        float nx = -dy / len, ny = dx / len;
        float mx = (ax + bx) / 2F - awayX, my = (ay + by) / 2F - awayY;
        if (nx * mx + ny * my < 0) {
            nx = -nx;
            ny = -ny;
        }
        quad(vc, m, ax, ay, bx, by, bx + nx * t, by + ny * t, ax + nx * t, ay + ny * t, z, argb);
    }

    /**
     * Quad com a face voltada para a câmera: o render type usa culling, então garantimos a mesma orientação
     * do fundo de texto do vanilla (área "negativa" com y para baixo), invertendo a ordem se preciso.
     */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2,
                             float x3, float y3, float z, int argb) {
        float area = (x0 * y1 - x1 * y0) + (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y0 - x0 * y3);
        if (area > 0) {
            vertex(vc, m, x3, y3, z, argb);
            vertex(vc, m, x2, y2, z, argb);
            vertex(vc, m, x1, y1, z, argb);
            vertex(vc, m, x0, y0, z, argb);
        } else {
            vertex(vc, m, x0, y0, z, argb);
            vertex(vc, m, x1, y1, z, argb);
            vertex(vc, m, x2, y2, z, argb);
            vertex(vc, m, x3, y3, z, argb);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
        vc.addVertex(m, x, y, z).setColor(argb).setLight(LIGHT);
    }

    private WorldLabels() {}
}
