package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.status.ClientStatuses;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Barrinhas de vida flutuantes: criaturas feridas há pouco (a até 24 blocos) mostram uma barra virada para a câmera
 * sobre a cabeça, que some alguns segundos depois do último dano. Chefes têm moldura dourada; Elites, roxa.
 * Desenhada no mundo (funciona com qualquer renderizador, inclusive GeckoLib) e escondida atrás de paredes.
 * <p>
 * v0.8: também desenha os ícones de condição de status (Em Chamas, Gelado...) com acúmulos e uma barrinha de tempo
 * que esvazia — logo acima da barra de vida, ou sozinhos sobre a cabeça de quem não está ferido (jogadores também).
 * Enquanto as barras estão ligadas, o núcleo NÃO desenha os ícones dele ({@code setWorldIconsHandledExternally});
 * com as barras desligadas, o núcleo volta a desenhar. A config {@code showStatusIcons} esconde os ícones.
 * <p>
 * v0.9: a altura da âncora (cabeça + nome + passageiros) é SUAVIZADA por criatura: agachar, mudar de pose, nadar,
 * montar/desmontar ou o nome aparecer não fazem a barra pular de uma vez.
 */
final class WorldHealthBars {
    private static final double RANGE = 24.0;
    /** Ticks que a barra fica visível depois do último dano (os últimos {@link #FADE_TICKS} são o fade). */
    private static final int SHOW_TICKS = 110;
    private static final int FADE_TICKS = 20;
    private static final int MAX_BARS = 40;
    private static final int MAX_ICONS = 8;
    private static final float ICON = 9F;
    private static final float ICON_GAP = 2F;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private static final class Track {
        float lastHealth;
        /** Vida antes do golpe que fez a barra aparecer (a barra nasce mostrando esse dano). */
        float hitFrom;
        long hurtTick = Long.MIN_VALUE / 2;
        long seenTick;
        MobInfo.Rank rank = MobInfo.Rank.NORMAL;
        // Suavização por quadro
        float shown = -1F, ghost = -1F;
        long ghostHoldUntil, lastFrame;

        Track(float health) {
            this.lastHealth = health;
        }
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    /** Altura suavizada da âncora por criatura desenhada (some junto quando ela deixa de ser desenhada). */
    private static final Map<Integer, Anchor> ANCHORS = new HashMap<>();
    /** Contador de quadros desenhados (marca quais âncoras ainda estão em uso). */
    private static long frame;

    private static final class Anchor {
        double height;
        long lastMs;
        long frame;
    }

    /** Criaturas (e outros jogadores) no alcance com alguma condição de status neste tick. */
    private static final Set<Integer> STATUS_IDS = new HashSet<>();
    private static ClientLevel trackedLevel;
    private static long ticks;
    private static MultiBufferSource.BufferSource buffers;

    private WorldHealthBars() {}

    static void clear() {
        TRACKS.clear();
        STATUS_IDS.clear();
        ANCHORS.clear();
        trackedLevel = null;
    }

    private static boolean enabled() {
        return HudDraw.config(MahouConfig.FLOATING_HEALTH_BARS);
    }

    private static boolean iconsEnabled() {
        return HudDraw.config(MahouConfig.SHOW_STATUS_ICONS);
    }

    /** Pode ter algo desenhado sobre a cabeça (ícones valem também para outros jogadores). */
    private static boolean visible(LivingEntity e, Player self) {
        if (e == self || e instanceof ArmorStand || e.isInvisibleTo(self) || e.isSpectator()) return false;
        // A própria montaria fica colada na câmera: a barra dela taparia a visão
        return !e.hasPassenger(self) && !self.isPassengerOfSameVehicle(e);
    }

    // =====================================================================================
    //  Tick: quem levou dano (a vida é sincronizada para todos os clientes que veem a criatura)
    // =====================================================================================
    static void tick(Minecraft mc) {
        ticks++;
        if (mc.level != trackedLevel) {
            TRACKS.clear();
            STATUS_IDS.clear();
            ANCHORS.clear();
            trackedLevel = mc.level;
        }
        boolean on = enabled();
        // Com as barras ligadas, os ícones de condição sobre os mobs são nossos; desligadas, o núcleo desenha os dele
        ClientStatuses.setWorldIconsHandledExternally(on);
        if (mc.player == null || mc.level == null || !on) {
            TRACKS.clear();
            STATUS_IDS.clear();
            ANCHORS.clear();
            return;
        }
        Player self = mc.player;
        boolean icons = iconsEnabled();
        STATUS_IDS.clear();
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(RANGE), x -> visible(x, self))) {
            if (icons && !StatusView.visible(e).isEmpty()) STATUS_IDS.add(e.getId());
            if (e instanceof Player) continue;
            float hp = e.getHealth();
            Track t = TRACKS.get(e.getId());
            if (t == null) {
                t = new Track(hp);
                TRACKS.put(e.getId(), t);
                // Entrou no alcance já apanhando (golpe acabou de acontecer): mostra também
                if (e.hurtTime > 0 && hp < e.getMaxHealth()) {
                    t.hurtTick = ticks;
                    t.hitFrom = hp;
                }
            } else if (hp < t.lastHealth - 0.01F) {
                if (t.shown < 0) t.hitFrom = t.lastHealth;
                t.hurtTick = ticks;
            }
            t.lastHealth = hp;
            t.seenTick = ticks;
            if (ticks - t.hurtTick <= SHOW_TICKS) t.rank = MobInfo.rank(e);
            else t.shown = -1F; // escondida: a próxima vez começa do zero (sem deslizar de um valor velho)
        }
        // Quem saiu do alcance ou sumiu deixa de ser acompanhado
        TRACKS.values().removeIf(t -> t.seenTick != ticks);
    }

    // =====================================================================================
    //  Desenho no mundo
    // =====================================================================================
    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || TRACKS.isEmpty() && STATUS_IDS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || !enabled()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(2048));

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        long now = Util.getMillis();
        boolean icons = iconsEnabled();

        // Feridas há pouco + quem tem condição de status; as mais próximas primeiro (limite em lutas grandes)
        Set<Integer> ids = new HashSet<>();
        for (Map.Entry<Integer, Track> en : TRACKS.entrySet()) {
            if (ticks - en.getValue().hurtTick <= SHOW_TICKS) ids.add(en.getKey());
        }
        if (icons) ids.addAll(STATUS_IDS);
        if (ids.isEmpty()) return;
        List<LivingEntity> shown = new ArrayList<>(ids.size());
        for (int id : ids) {
            Entity ent = mc.level.getEntity(id);
            if (ent instanceof LivingEntity le && ent != cam.getEntity() && !le.isRemoved()) shown.add(le);
        }
        if (shown.isEmpty()) return;
        shown.sort((a, b) -> Double.compare(a.distanceToSqr(camPos), b.distanceToSqr(camPos)));

        PoseStack ps = new PoseStack();
        int drawn = 0;
        frame++;
        for (LivingEntity le : shown) {
            if (drawn >= MAX_BARS) break;
            Track t = TRACKS.get(le.getId());
            float barFade = 0F;
            if (t != null) {
                float age = ticks - t.hurtTick + pt;
                barFade = Mth.clamp((SHOW_TICKS - age) / FADE_TICKS, 0F, 1F);
            }
            List<ClientStatuses.Entry> statuses = icons ? StatusView.visible(le) : List.of();
            if (barFade <= 0.02F && statuses.isEmpty()) continue;
            Vec3 pos = le.getPosition(pt);
            double dist = pos.distanceTo(camPos);
            float distAlpha = Mth.clamp((float) (RANGE + 2 - dist) / 4F, 0F, 1F);
            if (distAlpha <= 0.02F) continue;
            drawn++;
            if (barFade > 0.02F) smooth(t, le, now);
            else t = null;
            // Lista COMPLETA (com Molhado na água, mesmo com os ícones desligados): senão a barrinha de tempo do Olho
            // Analítico, que mostra tudo, voltaria a "cheia" a cada quadro
            StatusView.forgetMissing(le.getId(), ClientStatuses.get(le));
            draw(ps, le, t, barFade, statuses, pos.add(0, smoothAnchor(le, now), 0), cam, camPos, dist, distAlpha);
        }
        buffers.endBatch();
        // Quem não foi desenhado neste quadro perde a âncora (a próxima vez começa da altura real, sem deslizar)
        ANCHORS.values().removeIf(a -> a.frame != frame);
    }

    /** Altura da âncora que desliza até a real (~0,1 s); diferenças enormes (teleporte de pose) pulam direto. */
    private static double smoothAnchor(LivingEntity le, long now) {
        double want = anchorHeight(le);
        Anchor a = ANCHORS.get(le.getId());
        if (a == null) {
            a = new Anchor();
            a.height = want;
            ANCHORS.put(le.getId(), a);
        } else {
            float dt = Math.min(0.25F, Math.max(0L, now - a.lastMs) / 1000F);
            double diff = want - a.height;
            if (Math.abs(diff) > 4.0) a.height = want;
            else a.height += diff * (1.0 - Math.exp(-dt * 9.0));
            if (Math.abs(want - a.height) < 0.002) a.height = want;
        }
        a.lastMs = now;
        a.frame = frame;
        return a.height;
    }

    /** Altura da barra acima dos pés: acima da cabeça, do nome (se houver) e de quem estiver montado. */
    private static double anchorHeight(LivingEntity le) {
        double h = le.getBbHeight() + 0.42;
        // Nome visível em cima (mob com nome / outro jogador): a barra e os ícones ficam acima dele
        if (le.hasCustomName() || le instanceof Player) h += 0.3;
        if (le.isVehicle()) {
            double top = 0;
            for (Entity p : le.getPassengers()) top = Math.max(top, p.getY() - le.getY() + p.getBbHeight());
            h = Math.max(h, top + 0.42 + 0.22);
        }
        return h;
    }

    private static void smooth(Track t, LivingEntity le, long now) {
        float hp = Math.max(0F, le.getHealth());
        float dt = t.lastFrame == 0 ? 0F : Math.min(0.25F, (now - t.lastFrame) / 1000F);
        t.lastFrame = now;
        if (t.shown < 0) {
            // A barra nasce mostrando o golpe que a fez aparecer
            t.shown = hp;
            t.ghost = Math.max(hp, t.hitFrom);
            t.ghostHoldUntil = now + 350;
            return;
        }
        if (hp < t.shown - 0.01F && t.ghost <= t.shown + 0.01F) t.ghostHoldUntil = now + 350;
        t.shown += (hp - t.shown) * (1F - (float) Math.exp(-dt * 14F));
        if (Math.abs(hp - t.shown) < 0.02F) t.shown = hp;
        if (t.ghost < t.shown) t.ghost = t.shown;
        else if (now > t.ghostHoldUntil) t.ghost += (t.shown - t.ghost) * (1F - (float) Math.exp(-dt * 4.5F));
    }

    /** Barra (se houver) e, acima dela, a fileira de condições. Sem barra, os ícones descem até a altura dela. */
    private static void draw(PoseStack ps, LivingEntity le, @Nullable Track t, float barFade, List<ClientStatuses.Entry> statuses,
                             Vec3 at, Camera cam, Vec3 camPos, double dist, float distAlpha) {
        float scale = 0.025F * (1F + Mth.clamp((float) (dist - 6.0) / 18F, 0F, 1F) * 0.7F);
        ps.pushPose();
        ps.translate(at.x - camPos.x, at.y - camPos.y, at.z - camPos.z);
        ps.mulPose(cam.rotation());
        ps.scale(scale, -scale, scale);
        float barH = t != null ? drawBar(ps, le, t, barFade * distAlpha) : 0F;
        if (!statuses.isEmpty()) {
            // Acima da moldura da barra; quando ela some, os ícones deslizam para baixo até o lugar dela
            float bottom = t != null ? -(barH + 2.5F) * barFade : 0F;
            iconRow(ps, le.getId(), statuses, bottom, distAlpha);
        }
        ps.popPose();
    }

    /** Desenha a barra no pose atual; devolve a altura dela. */
    private static float drawBar(PoseStack ps, LivingEntity le, Track t, float alpha) {
        boolean special = t.rank != MobInfo.Rank.NORMAL;
        float w = Mth.clamp(Math.round(le.getBbWidth() * 34F), 30, special ? 72 : 56);
        float h = special ? 5F : 4F;
        float max = Math.max(1F, le.getMaxHealth());
        float frac = Mth.clamp(t.shown / max, 0F, 1F);
        float ghost = Mth.clamp(t.ghost / max, 0F, 1F);

        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());

        float x0 = -w / 2F, x1 = w / 2F, y0 = -h, y1 = 0F;
        int frame = switch (t.rank) {
            case BOSS -> MobInfo.COLOR_BOSS;
            case ELITE -> MobInfo.COLOR_ELITE;
            default -> 0xFF0A0C12;
        };
        // Camadas com z diferentes (maior = mais longe): moldura, fundo, dano recente, vida, brilho
        rect(vc, m, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 2.0F, HudDraw.fade(frame, 0.95F * alpha));
        rect(vc, m, x0, y0, x1, y1, 1.6F, HudDraw.fade(0xFF20242E, 0.9F * alpha));
        float fx = x0 + w * frac;
        if (ghost > frac + 0.002F) rect(vc, m, fx, y0, x0 + w * ghost, y1, 1.2F, HudDraw.fade(0xFFFFE6A0, 0.9F * alpha));
        if (frac > 0F) {
            int col = MobInfo.healthColor(frac);
            rect(vc, m, x0, y0, fx, y1, 0.8F, HudDraw.fade(col, alpha));
            rect(vc, m, x0, y0, fx, y0 + 1, 0.4F, HudDraw.fade(HudDraw.brighten(col, 0.5F), alpha));
        }
        if (special) {
            // Losangos nas pontas: Chefe/Elite se destaca no meio da multidão
            diamond(vc, m, x0 - 2.5F, (y0 + y1) / 2F, 2.5F, 0.3F, HudDraw.fade(frame, alpha));
            diamond(vc, m, x1 + 2.5F, (y0 + y1) / 2F, 2.5F, 0.3F, HudDraw.fade(frame, alpha));
        }
        return h;
    }

    // =====================================================================================
    //  Ícones de condição de status
    // =====================================================================================
    /**
     * Fileira centrada de ícones com a base em {@code bottom}: ícone (ou selo colorido de reserva), número de
     * acúmulos, barrinha de tempo que esvazia embaixo e contorno vermelho pulsando em quem está preso. Piscam nos
     * últimos 2 s.
     */
    private static void iconRow(PoseStack ps, int entityId, List<ClientStatuses.Entry> list, float bottom, float alpha) {
        int n = Math.min(list.size(), MAX_ICONS);
        float total = n * ICON + (n - 1) * ICON_GAP;
        float left = -total / 2F;
        float iy0 = bottom - 2F - ICON, iy1 = bottom - 2F;
        Font font = Minecraft.getInstance().font;
        float stuckPulse = HudDraw.pulse(500);
        Matrix4f m = ps.last().pose();

        // Em 3 passadas (fundos -> ícones -> números): poucas trocas de render type = poucas chamadas de desenho
        // mesmo com dezenas de criaturas com condições na tela.
        // 1) Tempo restante (fundo escuro + parte cheia na cor da condição), contorno de "preso" e selos de reserva
        VertexConsumer bg = buffers.getBuffer(RenderType.textBackground());
        boolean anyTexture = false;
        float x = left;
        for (int i = 0; i < n; i++) {
            ClientStatuses.Entry en = list.get(i);
            int color = StatusView.color(en.kind());
            float a = alpha * blink(en);
            float frac = StatusView.fraction(entityId, en);
            rect(bg, m, x - 0.5F, bottom - 1.5F, x + ICON + 0.5F, bottom, 0.3F, HudDraw.fade(0xFF05070D, 0.85F * alpha));
            if (frac > 0F) rect(bg, m, x, bottom - 1F, x + ICON * frac, bottom - 0.5F, 0.2F, HudDraw.fade(HudDraw.brighten(color, 0.3F), alpha));
            if (StatusView.incapacitating(en)) {
                int red = HudDraw.fade(HudDraw.lerp(stuckPulse, 0xFFFF3030, 0xFFFFC0B0), alpha);
                rect(bg, m, x - 1F, iy0 - 1F, x + ICON + 1F, iy1 + 1F, 0.25F, red);
            }
            if (GlyphArt.exists(ClientStatuses.icon(en.kind()))) {
                anyTexture = true;
            } else {
                // Sem textura (pacote de recursos incompleto): selo na cor da condição
                rect(bg, m, x, iy0, x + ICON, iy1, 0.1F, HudDraw.fade(color, a));
                rect(bg, m, x + 1, iy0 + 1, x + ICON - 1, iy1 - 1, 0.05F, HudDraw.fade(HudDraw.darken(color, 0.4F), a));
            }
            x += ICON + ICON_GAP;
        }

        // 2) Ícones texturizados
        if (anyTexture) {
            x = left;
            for (int i = 0; i < n; i++) {
                ClientStatuses.Entry en = list.get(i);
                ResourceLocation tex = ClientStatuses.icon(en.kind());
                if (GlyphArt.exists(tex)) {
                    int ca = Math.max(4, (int) (alpha * blink(en) * 255));
                    quadTex(buffers.getBuffer(RenderType.text(tex)), m, x, iy0, x + ICON, iy1, (ca << 24) | 0xFFFFFF);
                }
                x += ICON + ICON_GAP;
            }
        }

        // 3) Número de acúmulos (contornado, legível sobre qualquer ícone)
        x = left;
        int ta = Math.max(8, (int) (alpha * 255));
        for (int i = 0; i < n; i++) {
            ClientStatuses.Entry en = list.get(i);
            if (en.stacks() > 1) {
                String s = Integer.toString(en.stacks());
                ps.pushPose();
                ps.translate(x + ICON - font.width(s) * 0.6F + 1F, iy1 - 4.6F, -0.05F);
                ps.scale(0.6F, 0.6F, 0.6F);
                font.drawInBatch8xOutline(Component.literal(s).getVisualOrderText(), 0, 0, (ta << 24) | 0xFFFFFF,
                        (ta << 24) | 0x101018, ps.last().pose(), buffers, LIGHT);
                ps.popPose();
            }
            x += ICON + ICON_GAP;
        }
    }

    /** Pisca (meio apagado) nos últimos 2 s. */
    private static float blink(ClientStatuses.Entry en) {
        return StatusView.expiring(en) && (en.ticksLeft() / 4) % 2 == 0 ? 0.5F : 1F;
    }

    /** Quad texturizado na ordem do texto do vanilla (sup. esq., inf. esq., inf. dir., sup. dir.): visível de frente. */
    private static void quadTex(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        vc.addVertex(m, x0, y0, 0).setColor(argb).setUv(0, 0).setLight(LIGHT);
        vc.addVertex(m, x0, y1, 0).setColor(argb).setUv(0, 1).setLight(LIGHT);
        vc.addVertex(m, x1, y1, 0).setColor(argb).setUv(1, 1).setLight(LIGHT);
        vc.addVertex(m, x1, y0, 0).setColor(argb).setUv(1, 0).setLight(LIGHT);
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        quad(vc, m, x0, y1, x1, y1, x1, y0, x0, y0, z, argb);
    }

    private static void diamond(VertexConsumer vc, Matrix4f m, float cx, float cy, float r, float z, int argb) {
        quad(vc, m, cx, cy - r, cx + r, cy, cx, cy + r, cx - r, cy, z, argb);
    }

    /** Quad virado para a câmera (o render type tem culling): mesma orientação do fundo de texto do vanilla. */
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
}
