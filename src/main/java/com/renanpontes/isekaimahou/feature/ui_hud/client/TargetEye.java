package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.status.ClientStatuses;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Reputation;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Olho Analítico: ao olhar para uma criatura a até 32 blocos, um painel no topo central (logo abaixo das barras
 * de chefe) mostra nome (com selo de Chefe/Elite), vida com números e absorção, armadura e as fraquezas/resistências
 * elementais. Depois de ~2 s sem alvo o painel some. Tudo no cliente, com dados já sincronizados.
 * v0.8: facção e patamar do jogador com ela (NPCs de facção), selo "Procurado" e as condições de status do alvo
 * (ícone, acúmulos, nome quando cabe, segundos e barrinha de tempo).
 * v0.9: posição, largura e altura reservada FIXAS (ver {@link TopSpace#eye}). Cada linha extra (facção, afinidades,
 * condições) cabe em UMA linha: o que sobra vira versão compacta e depois "+N". A linha de condições, depois que
 * aparece para um alvo, fica até ele sair da mira (o painel não cresce e encolhe a cada condição que entra e sai).
 */
final class TargetEye {
    static final double RANGE = 32.0;
    private static final long HOLD_MS = 2000L;
    private static final long FADE_OUT_MS = 350L;
    private static final long FADE_IN_MS = 140L;
    /** Largura fixa do painel (telas muito estreitas usam o que couber, sempre o mesmo valor para a tela). */
    static final int WIDTH = 180;
    private static final int BAR_H = 9;
    private static final int ROW_Y = 25;
    private static final int ROW_H = 11;
    /** Linhas extras no máximo: facção, armadura/afinidades e condições. */
    private static final int MAX_ROWS = 3;
    /** Altura reservada (o painel nunca passa dela; o que vem abaixo começa sempre no mesmo lugar). */
    static final int RESERVED_H = ROW_Y + MAX_ROWS * ROW_H + 2;

    /** Última leitura do alvo (continua valendo enquanto o painel some, mesmo se a criatura sumir). */
    private static final class Target {
        final int id;
        final long acquired;
        long lastSeen;
        Component name = Component.empty();
        MobInfo.Rank rank = MobInfo.Rank.NORMAL;
        int accent = HudDraw.SYS_BORDER;
        int[] bar = {0xFFFF6B6B, 0xFFB01E2A};
        float health, max = 1F, absorption;
        int armor;
        List<MobInfo.Affinity> affinities = List.of();
        double distance;
        boolean dead;
        /** Facção do alvo e o patamar do jogador local com ela (null = sem facção). */
        Reputation.Faction faction;
        Reputation.Standing standing = Reputation.Standing.NEUTRAL;
        /** Guarda ou cidadão de uma cidade: reage ao "Procurado" (o selo aparece no painel dele). */
        boolean cityFolk;
        /** Já teve condição de status desde que entrou na mira: a linha fica reservada (sem pular). */
        boolean statusRow;
        /** Idem para a linha de armadura/afinidades (perder a armadura não encolhe o painel). */
        boolean infoRow;
        // Suavização por quadro: a barra desliza e o "fantasma" mostra o dano que acabou de entrar
        float shown = -1F, ghost = -1F, lastHealth = -1F;
        long ghostHoldUntil, lastFrame;

        Target(int id, long now) {
            this.id = id;
            this.acquired = now;
            this.lastSeen = now;
        }

        void update(LivingEntity e, Vec3 eye, Element active) {
            name = e.getDisplayName();
            rank = MobInfo.rank(e);
            accent = MobInfo.accent(e, rank);
            bar = MobInfo.barColors(e, rank);
            max = Math.max(1F, e.getMaxHealth());
            health = Mth.clamp(e.getHealth(), 0F, max);
            absorption = Math.max(0F, e.getAbsorptionAmount());
            armor = e.getArmorValue();
            affinities = MobInfo.affinities(e, active);
            distance = eye.distanceTo(e.getBoundingBox().getCenter());
            dead = e.isDeadOrDying();
            if (dead) health = 0F;
            faction = StatusView.factionOf(e);
            LocalPlayer me = Minecraft.getInstance().player;
            if (faction != null && me != null) standing = Reputation.standing(me, faction.id());
            cityFolk = faction != null && StatusView.reactsToWanted(e);
        }
    }

    /** Posição de um "chip" de afinidade (texto null = compacto: só runa e seta). */
    private record Chip(MobInfo.Affinity aff, Component text, int x, int y, int w) {}

    /** Posição de um "chip" de condição de status (nome null = modo compacto). */
    private record StatusChip(ClientStatuses.Entry en, Component name, String time, int x, int y, int w) {}

    /** Resultado do layout de uma linha: quantos itens ficaram de fora ("+N") e onde o "+N" vai. */
    private record Overflow(int hidden, int x) {}

    private static Target current;
    /** Mundo do alvo atual: trocar de dimensão (portal, Câmara de Provação) descarta o painel antigo. */
    private static ClientLevel trackedLevel;

    private TargetEye() {}

    static void clear() {
        current = null;
        trackedLevel = null;
    }

    // =====================================================================================
    //  Tick: raycast no cliente
    // =====================================================================================
    static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (mc.level != trackedLevel) {
            current = null;
            trackedLevel = mc.level;
        }
        if (player == null || mc.level == null || !HudDraw.config(MahouConfig.SHOW_TARGET_INFO)) {
            current = null;
            return;
        }
        long now = Util.getMillis();
        Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : player;
        // v1.0: o elemento "em uso" é o da magia sendo conjurada, senão o da 1ª magia do loadout ativo
        Element active = LoadoutCache.focusElement();
        Vec3 eye = cam.getEyePosition();
        LivingEntity hit = raycast(mc, player, cam);
        if (hit != null) {
            if (current == null || current.id != hit.getId()) current = new Target(hit.getId(), now);
            current.update(hit, eye, active);
            current.lastSeen = now;
            return;
        }
        if (current == null) return;
        // v1.1: mirando um bloco com o Jade instalado, a caixa dele aparece no mesmo lugar — o painel some logo
        if (JadeCompat.loaded() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                && now - current.lastSeen < HOLD_MS) {
            current.lastSeen = now - HOLD_MS;
        }
        // Sem alvo na mira: o último continua sendo acompanhado (vida caindo, morte) enquanto o painel some
        Entity e = mc.level.getEntity(current.id);
        if (e instanceof LivingEntity le && !e.isRemoved()) current.update(le, eye, active);
        if (now - current.lastSeen > HOLD_MS + FADE_OUT_MS) current = null;
    }

    /** Criatura viva mais próxima na linha da mira, parando em blocos sólidos. */
    private static LivingEntity raycast(Minecraft mc, LocalPlayer player, Entity cam) {
        Vec3 from = cam.getEyePosition();
        Vec3 look = cam.getViewVector(1F);
        Vec3 to = from.add(look.scale(RANGE));
        BlockHitResult block = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cam));
        double reach = block.getType() == HitResult.Type.MISS ? RANGE : from.distanceTo(block.getLocation());
        Vec3 end = from.add(look.scale(reach));
        LivingEntity best = null;
        double bestDist = reach;
        for (Entity e : mc.level.getEntities(cam, new AABB(from, end).inflate(1.0), x -> targetable(x, player, cam))) {
            // Caixa um pouco maior com a distância: mirar em algo pequeno lá longe continua fácil
            double far = from.distanceTo(e.position());
            AABB box = e.getBoundingBox().inflate(e.getPickRadius() + Math.min(0.35, 0.05 + far * 0.01));
            double d;
            if (box.contains(from)) {
                d = 0;
            } else {
                Optional<Vec3> h = box.clip(from, end);
                if (h.isEmpty()) continue;
                d = from.distanceTo(h.get());
            }
            if (d <= bestDist) {
                bestDist = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    private static boolean targetable(Entity e, LocalPlayer player, Entity cam) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e.isSpectator() || e instanceof ArmorStand) return false;
        if (e == player || e == cam || e.isInvisibleTo(player)) return false;
        // A própria montaria (e quem vai junto nela) não "tapa" a mira
        return !player.isPassengerOfSameVehicle(e) && !e.hasPassenger(player);
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    /** Desenha o painel no retângulo FIXO {@code r} (x, y e largura nunca mudam com o conteúdo). */
    static void render(GuiGraphics g, Minecraft mc, TopSpace.Rect r) {
        Target t = current;
        if (t == null) return;
        long now = Util.getMillis();
        float alpha = Math.min(1F, (now - t.acquired) / (float) FADE_IN_MS);
        long since = now - t.lastSeen;
        if (since > HOLD_MS) alpha *= 1F - Math.min(1F, (since - HOLD_MS) / (float) FADE_OUT_MS);
        if (alpha <= 0.03F) return;
        smooth(t, now);

        Font font = mc.font;
        List<ClientStatuses.Entry> statuses = ClientStatuses.get(t.id);
        StatusView.forgetMissing(t.id, statuses);
        if (!statuses.isEmpty()) t.statusRow = true;
        if (t.armor > 0 || !t.affinities.isEmpty()) t.infoRow = true;
        // O selo só aparece em quem reage ao crime (guarda caça, cidadão foge): não num goblin da Horda nem num aventureiro
        boolean wanted = t.cityFolk && StatusView.isWanted(mc.player);
        int x = r.x(), y = r.y(), w = r.w();

        // Linhas abaixo da vida (cada uma com UMA linha): facção · armadura/afinidades · condições de status
        int cursor = y + ROW_Y;
        int factionY = -1;
        if (t.faction != null) {
            factionY = cursor;
            cursor += ROW_H;
        }
        int chipsY = -1;
        List<Chip> chips = new ArrayList<>();
        Overflow chipMore = null;
        if (t.infoRow) {
            chipsY = cursor;
            chipMore = layoutChips(font, t, x, chipsY, w, chips);
            cursor += ROW_H;
        }
        int statusY = -1;
        List<StatusChip> sChips = new ArrayList<>();
        Overflow statusMore = null;
        if (t.statusRow) {
            statusY = cursor;
            statusMore = layoutStatuses(font, statuses, x, statusY, w, sChips);
            cursor += ROW_H;
        }
        int h = Math.min(RESERVED_H, cursor - y + 1);

        panel(g, t, x, y, w, h, alpha);
        header(g, font, t, x, y, w, alpha);
        healthBar(g, font, t, x + 4, y + 13, w - 8, alpha);
        if (factionY >= 0) factionRow(g, font, t, x, factionY, w, wanted, alpha);
        if (chipsY >= 0) {
            infoRow(g, font, t, x, chipsY, chips, alpha);
            more(g, font, chipMore, chipsY, alpha);
        }
        if (statusY >= 0) {
            if (factionY >= 0 || chipsY >= 0) g.fill(x + 5, statusY - 1, x + w - 5, statusY, HudDraw.fade(0x22FFFFFF, alpha));
            if (sChips.isEmpty() && statusMore == null) {
                // A condição acabou: a linha continua reservada, só com um aviso discreto
                HudDraw.scaledText(g, font, Component.translatable("hud.isekai_mahou.ui_hud.target.no_status"),
                        x + 6, statusY + 1.5F, 0.75F, HudDraw.fade(HudDraw.SYS_MUTED, 0.7F * alpha), false);
            } else {
                statusRows(g, font, t, sChips, alpha);
                more(g, font, statusMore, statusY, alpha);
            }
        }
    }

    /** "+N" no fim de uma linha que não coube inteira. */
    private static void more(GuiGraphics g, Font font, Overflow o, int ry, float a) {
        if (o == null || o.hidden() <= 0) return;
        HudDraw.scaledText(g, font, Component.literal("+" + o.hidden()), o.x(), ry + 1.5F, 0.75F, HudDraw.fade(HudDraw.SYS_TEXT, a), true);
    }

    /** Largura do "+N" (escala 0,75). */
    private static int moreWidth(Font font, int hidden) {
        return hidden <= 0 ? 0 : (int) Math.ceil(font.width("+" + hidden) * 0.75F) + 2;
    }

    /** Vida mostrada desliza até a real; o "fantasma" segura o dano por um instante e depois desce. */
    private static void smooth(Target t, long now) {
        float dt = t.lastFrame == 0 ? 0F : Math.min(0.25F, (now - t.lastFrame) / 1000F);
        t.lastFrame = now;
        if (t.shown < 0) {
            t.shown = t.ghost = t.lastHealth = t.health;
            return;
        }
        if (t.health < t.lastHealth - 0.01F) t.ghostHoldUntil = now + 420;
        t.lastHealth = t.health;
        t.shown += (t.health - t.shown) * (1F - (float) Math.exp(-dt * 12F));
        if (Math.abs(t.health - t.shown) < 0.02F) t.shown = t.health;
        if (t.ghost < t.shown) t.ghost = t.shown;
        else if (now > t.ghostHoldUntil) t.ghost += (t.shown - t.ghost) * (1F - (float) Math.exp(-dt * 4F));
    }

    private static void panel(GuiGraphics g, Target t, int x, int y, int w, int h, float a) {
        if (t.rank == MobInfo.Rank.BOSS) HudDraw.glow(g, x, y, w, h, t.accent, (0.35F + 0.35F * HudDraw.pulse(1600)) * a, 3);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, HudDraw.fade(HudDraw.SYS_BG_TOP, a), HudDraw.fade(HudDraw.SYS_BG_BOTTOM, a));
        g.renderOutline(x, y, w, h, HudDraw.fade(t.accent, 0.5F * a));
        g.fill(x + 2, y + 2, x + w - 2, y + 3, HudDraw.fade(0x18FFFFFF, a));
        HudDraw.corners(g, x, y, w, h, HudDraw.fade(t.accent, a), 4);
    }

    /** Linha 1: olho, selo de Chefe/Elite, nome e distância. */
    private static void header(GuiGraphics g, Font font, Target t, int x, int y, int w, float a) {
        int cx = x + 4;
        HudDraw.sprite(g, HudDraw.EYE, cx, y + 3, HudDraw.fade(t.accent, a));
        cx += 12;

        if (t.rank != MobInfo.Rank.NORMAL) {
            boolean boss = t.rank == MobInfo.Rank.BOSS;
            Component label = Component.translatable(boss ? "hud.isekai_mahou.ui_hud.target.boss" : "hud.isekai_mahou.ui_hud.target.elite");
            int lw = Math.round(font.width(label) * 0.75F);
            int[] icon = boss ? HudDraw.CROWN : HudDraw.ELITE;
            int bw = icon[2] + 3 + lw + 3;
            int bg = boss ? 0xE0401008 : 0xE0281040;
            g.fill(cx, y + 2, cx + bw, y + 11, HudDraw.fade(bg, a));
            g.renderOutline(cx, y + 2, bw, 9, HudDraw.fade(t.accent, 0.85F * a));
            HudDraw.sprite(g, icon, cx + 2, y + 3, HudDraw.fade(t.accent, a));
            HudDraw.scaledText(g, font, label, cx + icon[2] + 3, y + 4F, 0.75F, HudDraw.fade(t.accent, a), false);
            cx += bw + 4;
        }

        Component dist = Component.translatable("hud.isekai_mahou.ui_hud.target.distance", Math.max(1, (int) Math.round(t.distance)));
        float dw = font.width(dist) * 0.75F;
        HudDraw.scaledText(g, font, dist, x + w - 4 - dw, y + 4F, 0.75F, HudDraw.fade(HudDraw.SYS_MUTED, a), false);

        MutableComponent name = t.name.copy();
        if (t.rank == MobInfo.Rank.BOSS) name.withStyle(ChatFormatting.BOLD);
        int nameMax = x + w - 6 - Math.round(dw) - cx;
        FormattedCharSequence nm = HudDraw.ellipsize(font, name, Math.max(16, nameMax));
        int nameColor = t.rank == MobInfo.Rank.BOSS ? HudDraw.GOLD_LIGHT : HudDraw.SYS_TEXT;
        HudDraw.text(g, font, nm, cx, y + 3, HudDraw.fade(nameColor, a), true);
    }

    /** Linha 2: barra de vida com o dano recente, absorção, marcas de 25% e "atual / máx". */
    private static void healthBar(GuiGraphics g, Font font, Target t, int bx, int by, int bw, float a) {
        g.fill(bx - 1, by - 1, bx + bw + 1, by + BAR_H + 1, HudDraw.fade(0xFF05070D, a));
        g.fill(bx, by, bx + bw, by + BAR_H, HudDraw.fade(0xFF1A1F2C, a));
        float frac = Mth.clamp(t.shown / t.max, 0F, 1F);
        float ghost = Mth.clamp(t.ghost / t.max, 0F, 1F);
        int fw = Math.round(bw * frac), gwid = Math.round(bw * ghost);
        if (gwid > fw) g.fill(bx + fw, by, bx + gwid, by + BAR_H, HudDraw.fade(0xFFFFE6A0, 0.85F * a));
        if (fw > 0) {
            g.fillGradient(bx, by, bx + fw, by + BAR_H, HudDraw.fade(t.bar[0], a), HudDraw.fade(t.bar[1], a));
            g.fill(bx, by, bx + fw, by + 1, HudDraw.fade(0x55FFFFFF, a));
            // Vida baixa: a barra pulsa
            if (frac < 0.25F && !t.dead) g.fill(bx, by, bx + fw, by + BAR_H, HudDraw.fade(0xFFFFFFFF, 0.28F * HudDraw.pulse(700) * a));
        }
        for (int q = 1; q < 4; q++) {
            int qx = bx + bw * q / 4;
            g.fill(qx, by + BAR_H - 3, qx + 1, by + BAR_H, HudDraw.fade(0x70000000, a));
        }
        if (t.absorption > 0.05F) {
            int aw = Math.round(bw * Math.min(1F, t.absorption / t.max));
            g.fill(bx, by, bx + aw, by + 2, HudDraw.fade(0xFFFFD84F, a));
        }

        if (t.dead) {
            Component txt = Component.translatable("hud.isekai_mahou.ui_hud.target.defeated");
            int tw = font.width(txt);
            int tx = bx + bw / 2 - (tw + 9) / 2;
            HudDraw.sprite(g, HudDraw.SKULL, tx, by + 1, HudDraw.fade(0xFFE0E0E0, a));
            HudDraw.text(g, font, txt, tx + 9, by + 1, HudDraw.fade(0xFFFF8A8A, a), true);
            return;
        }
        MutableComponent txt = Component.literal(HudDraw.num(t.health) + " / " + HudDraw.num(t.max));
        if (t.absorption > 0.05F) txt.append(Component.literal(" +" + HudDraw.num(t.absorption)).withColor(0xFFD84F));
        int tw = font.width(txt) + 9;
        int tx = bx + bw / 2 - tw / 2;
        HudDraw.sprite(g, HudDraw.HEART, tx, by + 1, HudDraw.fade(0xFFFF8080, a));
        HudDraw.text(g, font, txt, tx + 9, by + 1, HudDraw.fade(0xFFFFFFFF, a), true);
    }

    /**
     * Armadura + chips de afinidade numa ÚNICA linha a partir de {@code rowY}: com o multiplicador quando cabe, senão
     * compactos (runa + seta), e o que ainda sobrar vira "+N".
     */
    private static Overflow layoutChips(Font font, Target t, int x, int rowY, int w, List<Chip> out) {
        int right = x + w - 4;
        int start = x + 5 + (t.armor > 0 ? 9 + font.width(String.valueOf(t.armor)) + 7 : 0);
        List<Component> texts = new ArrayList<>();
        int full = 0;
        for (MobInfo.Affinity aff : t.affinities) {
            Component text = aff.immune() ? Component.translatable("hud.isekai_mahou.ui_hud.target.immune")
                    : Component.literal(MobInfo.multText(aff.mult()));
            texts.add(text);
            full += chipWidth(font, text) + 3;
        }
        boolean compact = start + full - 3 > right;
        int cx = start;
        int n = t.affinities.size();
        for (int i = 0; i < n; i++) {
            Component text = compact ? null : texts.get(i);
            int cw = chipWidth(font, text);
            int reserve = i < n - 1 ? moreWidth(font, n - i) : 0;
            if (cx + cw + reserve > right) return new Overflow(n - i, cx);
            out.add(new Chip(t.affinities.get(i), text, cx, rowY, cw));
            cx += cw + 3;
        }
        return null;
    }

    /** runa (8) + seta/selo (5) + texto pequeno (sem texto = compacto). */
    private static int chipWidth(Font font, Component text) {
        return 8 + 1 + 5 + (text == null ? 1 : 2 + (int) Math.ceil(font.width(text) * 0.75F) + 2);
    }

    /** Escudo com a armadura e as runas dos elementos com ↑×2 / ↓×0.5 / ⊘ imune (a partir da linha {@code ry}). */
    private static void infoRow(GuiGraphics g, Font font, Target t, int x, int ry, List<Chip> chips, float a) {
        if (t.armor > 0) {
            HudDraw.sprite(g, HudDraw.SHIELD, x + 5, ry + 1, HudDraw.fade(0xFFB8C8DC, a));
            HudDraw.text(g, font, Component.literal(String.valueOf(t.armor)), x + 14, ry + 1, HudDraw.fade(0xFFDDE6F2, a), true);
        }
        for (Chip c : chips) {
            int col = MobInfo.affinityColor(c.aff());
            g.fill(c.x() - 1, c.y(), c.x() + c.w(), c.y() + 9, HudDraw.fade(0x60000000, a));
            g.fill(c.x() - 1, c.y() + 9, c.x() + c.w(), c.y() + 10, HudDraw.fade(col, 0.85F * a));
            // O elemento ativo do jogador ganha um contorno: é o que ele vai usar agora
            if (c.aff().active()) g.renderOutline(c.x() - 2, c.y() - 1, c.w() + 3, 12, HudDraw.fade(0xFFFFFFFF, 0.55F * a));
            g.setColor(1F, 1F, 1F, a);
            GlyphArt.element(g, c.aff().element(), c.x(), c.y(), 8);
            g.setColor(1F, 1F, 1F, 1F);
            // Seta para cima = leva mais dano; para baixo = resiste; círculo cortado = imune (lê sem precisar do número)
            int[] mark = c.aff().immune() ? HudDraw.IMMUNE : c.aff().weak() ? HudDraw.UP : HudDraw.DOWN;
            HudDraw.sprite(g, mark, c.x() + 9, c.y() + 2, HudDraw.fade(col, a));
            if (c.text() != null) HudDraw.scaledText(g, font, c.text(), c.x() + 16, c.y() + 2F, 0.75F, HudDraw.fade(col, a), true);
        }
    }

    // =====================================================================================
    //  v0.8: facção e condições de status
    // =====================================================================================
    /** Emblema, "Facção — Patamar" (cores da facção e do patamar) e o selo "PROCURADO" à direita. */
    private static void factionRow(GuiGraphics g, Font font, Target t, int x, int ry, int w, boolean wanted, float a) {
        Reputation.Faction f = t.faction;
        // Hostil/Inimigo: faixa avermelhada (não negocia, pode atacar); senão um leve tom da cor da facção
        int band = !t.standing.canTrade() ? 0x45FF2020 : (0x1C000000 | (f.color() & 0xFFFFFF));
        g.fill(x + 3, ry - 1, x + w - 3, ry + 10, HudDraw.fade(band, a));
        StatusView.emblem(g, f, x + 5, ry, 9, a);

        int sealW = 0;
        if (wanted) {
            float p = HudDraw.pulse(800);
            int red = HudDraw.lerp(p, 0xFFFF4040, 0xFFFFB8A8);
            Component label = Component.translatable("hud.isekai_mahou.ui_hud.wanted");
            int lw = (int) Math.ceil(font.width(label) * 0.75F);
            int bw = 9 + lw + 3;
            int bx = x + w - 4 - bw;
            g.fill(bx, ry - 1, bx + bw, ry + 9, HudDraw.fade(0xE0400808, a));
            g.renderOutline(bx, ry - 1, bw, 10, HudDraw.fade(red, a));
            HudDraw.sprite(g, HudDraw.WANTED, bx + 1, ry + 1, HudDraw.fade(red, a));
            HudDraw.scaledText(g, font, label, bx + 9, ry + 1.5F, 0.75F, HudDraw.fade(0xFFFFE0D8, a), true);
            sealW = bw + 4;
        }

        MutableComponent line = f.coloredName()
                .append(Component.literal(" — ").withColor(HudDraw.SYS_MUTED & 0xFFFFFF))
                .append(t.standing.displayName());
        FormattedCharSequence seq = HudDraw.ellipsize(font, line, Math.max(20, w - 18 - 4 - sealW));
        HudDraw.text(g, font, seq, x + 17, ry + 1, HudDraw.fade(0xFFFFFFFF, a), true);
    }

    /**
     * Condições numa ÚNICA linha: com nome ("Em Chamas 6s") quando todas cabem, senão compactas (ícone + tempo); o que
     * ainda sobrar vira "+N" no fim.
     */
    private static Overflow layoutStatuses(Font font, List<ClientStatuses.Entry> list, int x, int rowY, int w, List<StatusChip> out) {
        if (list.isEmpty()) return null;
        int left = x + 5, right = x + w - 4;
        int full = 0;
        for (ClientStatuses.Entry en : list) full += chipWidth(font, en, en.kind().displayName(en.stacks())) + 3;
        boolean names = full - 3 <= right - left;
        int cx = left;
        int n = list.size();
        for (int i = 0; i < n; i++) {
            ClientStatuses.Entry en = list.get(i);
            Component name = names ? en.kind().displayName(en.stacks()) : null;
            int cw = chipWidth(font, en, name);
            int reserve = i < n - 1 ? moreWidth(font, n - i) : 0;
            if (cx + cw + reserve > right) return new Overflow(n - i, cx);
            out.add(new StatusChip(en, name, StatusView.time(en.ticksLeft()), cx, rowY, cw));
            cx += cw + 3;
        }
        return null;
    }

    private static int chipWidth(Font font, ClientStatuses.Entry en, Component name) {
        int w = 9 + 2 + (int) Math.ceil(font.width(StatusView.time(en.ticksLeft())) * 0.75F) + 2;
        if (name != null) w += (int) Math.ceil(font.width(name) * 0.75F) + 3;
        return w;
    }

    /** Ícone (com acúmulos), nome, segundos e a barrinha de tempo embaixo; preso = contorno vermelho pulsando. */
    private static void statusRows(GuiGraphics g, Font font, Target t, List<StatusChip> chips, float a) {
        for (StatusChip c : chips) {
            ClientStatuses.Entry en = c.en();
            int col = StatusView.color(en.kind());
            boolean blink = StatusView.expiring(en) && (en.ticksLeft() / 4) % 2 == 0;
            g.fill(c.x() - 1, c.y() - 1, c.x() + c.w(), c.y() + 9, HudDraw.fade(0x70000000, a));
            if (StatusView.incapacitating(en)) {
                g.renderOutline(c.x() - 2, c.y() - 2, c.w() + 3, 12,
                        HudDraw.fade(HudDraw.lerp(HudDraw.pulse(500), 0xFFFF3030, 0xFFFFC0B0), a));
            }
            // Tempo restante: barrinha que esvazia
            float frac = StatusView.fraction(t.id, en);
            g.fill(c.x() - 1, c.y() + 9, c.x() + c.w(), c.y() + 10, HudDraw.fade(HudDraw.darken(col, 0.6F), 0.8F * a));
            int fw = Math.round((c.w() + 1) * frac);
            if (fw > 0) g.fill(c.x() - 1, c.y() + 9, c.x() - 1 + fw, c.y() + 10, HudDraw.fade(HudDraw.brighten(col, 0.25F), a));

            StatusView.icon(g, en, c.x(), c.y() - 1, 9, a * (blink ? 0.5F : 1F));
            float tx = c.x() + 11;
            if (c.name() != null) {
                HudDraw.scaledText(g, font, c.name(), tx, c.y() + 1.5F, 0.75F, HudDraw.fade(HudDraw.brighten(col, 0.45F), a), true);
                tx += (float) Math.ceil(font.width(c.name()) * 0.75F) + 3;
            }
            int timeColor = StatusView.expiring(en) ? 0xFFFF9A9A : HudDraw.SYS_MUTED;
            HudDraw.scaledText(g, font, Component.literal(c.time()), tx, c.y() + 1.5F, 0.75F, HudDraw.fade(timeColor, a), false);
        }
    }
}
