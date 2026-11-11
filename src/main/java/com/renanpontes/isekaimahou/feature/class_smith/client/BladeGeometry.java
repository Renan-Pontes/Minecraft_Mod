package com.renanpontes.isekaimahou.feature.class_smith.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeFamily;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeMaterial;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;

import net.minecraft.util.Mth;

/**
 * Monta o modelo 3D da Lâmina Forjada a partir das partes, como uma lista de caixas (unidades de pixel: 16 = 1 bloco;
 * X/Z centrados em 8; a MÃO fica em y = 8; a lâmina/cabeça cresce para +Y). Cada parte muda o desenho de verdade:
 * <ul>
 *   <li>comprimento: tamanho da lâmina (adaga 5,5 px → montante 16 px) ou da haste;</li>
 *   <li>largura: lâmina/cabeça mais larga e guarda maior;</li>
 *   <li>curva: a lâmina dobra (cimitarra/katana), a barba do machado desce, a foice fecha;</li>
 *   <li>ponta: de arredondada a agulha (lança mais longa, martelo vira picareta de guerra);</li>
 *   <li>serrilha: dentes num fio (1), nos dois (2), dentes grandes (3);</li>
 *   <li>acabamento: brilhante (claro, fio reluzente), fosco (escuro, áspero), rúnico (sulco com runas que brilham);</li>
 *   <li>runa: o sulco e as marcas brilham na cor do elemento; empunhadura: couro, cabo longo vermelho, cordão claro.</li>
 * </ul>
 * O resultado é imutável e barato (≈30–80 caixas); o renderer guarda em cache por valor das partes.
 */
public final class BladeGeometry {
    /** Regiões do atlas {@code textures/item/class_smith/forge_parts.png} (64×64): u, v, largura, altura (px). */
    public enum Region {
        METAL(0, 0, 16, 32), EDGE(16, 0, 16, 32), LEATHER(32, 0, 16, 16), WOOD(32, 16, 16, 16),
        GOLD(48, 0, 16, 16), RUNE(48, 16, 16, 16), MATTE(0, 32, 16, 32), GEM(16, 32, 16, 16), WRAP(32, 32, 16, 16);

        public final int u, v, w, h;

        Region(int u, int v, int w, int h) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
        }
    }

    /**
     * Uma caixa: de (x0,y0,z0) a (x1,y1,z1), girada {@code rotZ} graus em volta do eixo Z no ponto (px,py,pz),
     * cor RGB, região do atlas e se brilha no escuro.
     */
    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, float rotZ, float px, float py, float pz,
                      int rgb, Region region, boolean glow) {}

    public final List<Box> boxes = new ArrayList<>();
    public float minX = 8, maxX = 8, minY = 8, maxY = 8;

    private final ForgedBlade b;
    private final int metal, edge, dark, fuller, runeColor;
    private final Region metalRegion;
    private final boolean rune;

    private BladeGeometry(ForgedBlade blade) {
        this.b = blade;
        BladeMaterial m = blade.material();
        int base = m.color;
        Element el = blade.runeElement();
        rune = el != null;
        runeColor = el != null ? el.color : 0xFFFFFF;
        switch (blade.finish()) {
            case MATTE -> {
                metal = mix(darken(base, 0.28F), 0x6A6A6A, 0.25F);
                edge = darken(base, 0.05F);
                metalRegion = Region.MATTE;
            }
            case RUNIC -> {
                metal = mix(base, runeColor, 0.12F);
                edge = lighten(base, 0.35F);
                metalRegion = Region.METAL;
            }
            default -> {
                metal = lighten(base, 0.06F);
                edge = lighten(base, 0.45F);
                metalRegion = Region.METAL;
            }
        }
        dark = darken(metal, 0.35F);
        fuller = darken(metal, 0.2F);
    }

    public static BladeGeometry build(ForgedBlade blade) {
        BladeGeometry g = new BladeGeometry(blade);
        if (blade.family() == BladeFamily.BLADE) g.blade();
        else g.polearm();
        return g;
    }

    // =====================================================================================
    //  Lâminas (adaga, espada, espada longa, montante)
    // =====================================================================================
    private void blade() {
        int len = b.length();
        float gripLen = (b.grip() == ForgedBlade.Grip.LONG ? 6.0F : b.grip() == ForgedBlade.Grip.LIGHT ? 3.6F : 4.6F)
                + (len >= 4 ? 1.6F : len >= 3 ? 0.8F : 0F);
        float g0 = 8 - gripLen * 0.5F, g1 = 8 + gripLen * 0.5F;
        grip(g0, g1, 1.15F);
        pommel(g0);
        // guarda: cresce com a largura e nas lâminas longas
        float gw = 2.6F + 1.0F * b.width() + (len >= 3 ? 1.6F : len >= 1 ? 0.8F : 0F);
        float gy = g1;
        box(8 - gw / 2, gy, 7.2F, 8 + gw / 2, gy + 1.0F, 8.8F, 0, guardColor(), guardRegion(), false);
        // pontas da guarda (quillons) levantando um pouco
        float qh = 0.5F + 0.15F * len;
        box(8 - gw / 2, gy + 1.0F, 7.4F, 8 - gw / 2 + 0.8F, gy + 1.0F + qh, 8.6F, 0, guardColor(), guardRegion(), false);
        box(8 + gw / 2 - 0.8F, gy + 1.0F, 7.4F, 8 + gw / 2, gy + 1.0F + qh, 8.6F, 0, guardColor(), guardRegion(), false);
        if (rune || b.material() == BladeMaterial.STARMETAL) {
            int gem = rune ? runeColor : BladeMaterial.STARMETAL.color;
            box(7.45F, gy + 0.15F, 7.05F, 8.55F, gy + 0.85F, 8.95F, 0, gem, Region.GEM, true);
        }
        float L = new float[] {5.5F, 8.0F, 10.5F, 13.0F, 16.0F}[len] + (b.tip() >= 3 ? 1.5F : 0F);
        float W = 1.5F + 0.55F * b.width() + (len >= 3 ? 0.3F : 0F);
        float T = 0.45F + 0.1F * b.width();
        bladeBody(8, gy + 1.0F, L, W, T, b.curve() * 0.9F, true);
    }

    /**
     * Corpo de uma lâmina que nasce em (cx, y0) e cresce L px para cima, com largura W, espessura T e desvio lateral
     * máximo {@code bend} (curva quadrática). Fio, sulco/runas, serrilha e ponta inclusos.
     */
    private void bladeBody(float cx, float y0, float L, float W, float T, float bend, boolean fullerOn) {
        int n = 10;
        float tipFrac = switch (b.tip()) {
            case 0 -> 0.12F;
            case 1 -> 0.22F;
            case 2 -> 0.34F;
            default -> 0.45F;
        };
        float tipMin = switch (b.tip()) {
            case 0 -> 0.75F;
            case 1 -> 0.28F;
            case 2 -> 0.14F;
            default -> 0.08F;
        };
        float z0 = 8 - T / 2, z1 = 8 + T / 2;
        for (int i = 0; i < n; i++) {
            float t0 = i / (float) n, t1 = (i + 1) / (float) n, tm = (t0 + t1) * 0.5F;
            float ya = y0 + L * t0, yb = y0 + L * t1 + 0.02F;
            float off = bend * tm * tm;
            float slope = 2 * bend * tm / L;
            float rot = (float) -Math.toDegrees(Math.atan(slope));
            float w = W * width(tm, tipFrac, tipMin);
            float x = cx + off;
            float ym = (ya + yb) * 0.5F;
            // corpo
            box(x - w * 0.38F, ya, z0, x + w * 0.38F, yb, z1, rot, x, ym, 8, metal, metalRegion, false);
            // fios (mais finos e claros)
            float ez0 = 8 - T * 0.3F, ez1 = 8 + T * 0.3F;
            box(x - w * 0.5F, ya, ez0, x - w * 0.38F, yb, ez1, rot, x, ym, 8, edge, Region.EDGE, false);
            box(x + w * 0.38F, ya, ez0, x + w * 0.5F, yb, ez1, rot, x, ym, 8, edge, Region.EDGE, false);
            // sulco central (fuller) — brilha com runa
            float fullerEnd = b.finish() == ForgedBlade.Finish.RUNIC ? 0.82F : 0.6F;
            if (fullerOn && tm > 0.05F && tm < fullerEnd && W > 1.6F) {
                boolean glow = rune;
                box(x - 0.16F, ya, z0 - 0.04F, x + 0.16F, yb, z1 + 0.04F, rot, x, ym, 8, glow ? runeColor : fuller,
                        glow ? Region.RUNE : metalRegion, glow);
            }
            // serrilha: dentes no fio direito (1), nos dois (2), grandes (3)
            if (b.serration() > 0 && tm > 0.12F && tm < 0.78F && i % 2 == 0) {
                float s = b.serration() >= 3 ? 0.75F : 0.5F;
                tooth(x + w * 0.5F, ym, s, T * 0.6F, rot);
                if (b.serration() >= 2) tooth(x - w * 0.5F, ym, s, T * 0.6F, rot);
            }
        }
        // runas gravadas nas faces (só acabamento rúnico)
        if (rune && b.finish() == ForgedBlade.Finish.RUNIC) {
            for (int k = 0; k < 3; k++) {
                float tm = 0.2F + 0.2F * k;
                float x = cx + bend * tm * tm;
                float y = y0 + L * tm;
                box(x - 0.45F, y - 0.3F, z0 - 0.06F, x + 0.45F, y + 0.3F, z1 + 0.06F, 0, runeColor, Region.RUNE, true);
            }
        }
    }

    /** Largura relativa ao longo da lâmina (1 na base, afinando na zona da ponta). */
    private static float width(float t, float tipFrac, float tipMin) {
        float start = 1F - tipFrac;
        if (t <= start) return 1F - 0.08F * t;
        float k = (t - start) / tipFrac;
        return Mth.lerp(k, 1F - 0.08F * start, tipMin);
    }

    private void tooth(float x, float y, float s, float t, float rot) {
        box(x - s / 2, y - s / 2, 8 - t / 2, x + s / 2, y + s / 2, 8 + t / 2, rot + 45F, x, y, 8, edge, Region.EDGE, false);
    }

    // =====================================================================================
    //  Armas de haste (machado, lança, martelo, foice)
    // =====================================================================================
    private void polearm() {
        BladeFamily f = b.family();
        int len = b.length();
        float haft = switch (f) {
            case AXE -> 12F + 2.5F * len;
            case SPEAR -> 18F + 3F * len;
            case HAMMER -> 12F + 2.5F * len;
            default -> 16F + 3F * len;
        } + (b.grip() == ForgedBlade.Grip.LONG ? 2F : 0F);
        float y0 = 8 - haft * 0.3F, y1 = y0 + haft;
        float th = f == BladeFamily.HAMMER ? 1.35F : 1.15F;
        // haste de madeira + couro onde a mão segura + ponteira
        box(8 - th / 2, y0, 8 - th / 2, 8 + th / 2, y1, 8 + th / 2, 0, 0xFFFFFF, Region.WOOD, false);
        grip(8 - (b.grip() == ForgedBlade.Grip.LONG ? 3.2F : 2.2F), 8 + (b.grip() == ForgedBlade.Grip.LONG ? 3.2F : 2.2F), th + 0.25F);
        box(8 - th / 2 - 0.2F, y0 - 0.9F, 8 - th / 2 - 0.2F, 8 + th / 2 + 0.2F, y0, 8 + th / 2 + 0.2F, 0, guardColor(), guardRegion(), false);
        switch (f) {
            case AXE -> axeHead(y1);
            case SPEAR -> spearHead(y1);
            case HAMMER -> hammerHead(y1);
            default -> scytheHead(y1);
        }
    }

    private void axeHead(float top) {
        float headH = 4.0F + 0.8F * b.width();
        float hy0 = top - headH - 0.4F;
        // alvado (soquete) em volta da haste
        box(7.0F, hy0, 7.0F, 9.0F, top, 9.0F, 0, metal, metalRegion, false);
        float reach = 3.5F + 0.8F * b.width() + 0.3F * b.length();
        int slices = 5;
        float beard = 0.35F * b.curve();
        for (int k = 0; k < slices; k++) {
            float t = (k + 1) / (float) slices;
            float sx0 = 9.0F + reach * k / slices, sx1 = 9.0F + reach * (k + 1) / slices + 0.02F;
            float h = headH * (0.55F + 0.45F * t);
            float cy = hy0 + headH * 0.5F - beard * t * t * 2.0F;
            boolean last = k == slices - 1;
            box(sx0, cy - h / 2, 7.6F, sx1, cy + h / 2, 8.4F, 0, last ? edge : metal, last ? Region.EDGE : metalRegion, false);
            if (b.serration() > 0 && last) {
                int teeth = 1 + b.serration();
                for (int i = 0; i < teeth; i++) {
                    float ty = cy - h / 2 + h * (i + 0.5F) / teeth;
                    tooth(sx1, ty, b.serration() >= 3 ? 0.8F : 0.55F, 0.5F, 0);
                }
            }
        }
        if (rune) box(9.4F, hy0 + headH * 0.35F, 7.5F, 10.6F, hy0 + headH * 0.65F, 8.5F, 0, runeColor, Region.RUNE, true);
        // ponta: espigão traseiro (machado de barba / bico)
        if (b.tip() > 0) {
            float sl = 1.0F + 0.8F * b.tip();
            float cy = hy0 + headH * 0.55F;
            box(7.0F - sl * 0.6F, cy - 0.7F, 7.6F, 7.0F, cy + 0.7F, 8.4F, 0, metal, metalRegion, false);
            box(7.0F - sl, cy - 0.35F, 7.75F, 7.0F - sl * 0.6F, cy + 0.35F, 8.25F, 0, edge, Region.EDGE, false);
        }
        if (b.tip() >= 2) box(7.6F, top, 7.6F, 8.4F, top + 1.2F + 0.5F * b.tip(), 8.4F, 0, edge, Region.EDGE, false);
    }

    private void spearHead(float top) {
        // anel do alvado + cabeça em folha (ponta e serrilha do corpo de lâmina)
        box(7.2F, top - 0.2F, 7.2F, 8.8F, top + 1.4F, 8.8F, 0, guardColor(), guardRegion(), false);
        if (b.width() >= 2) {
            // lança alada: travessa abaixo da folha
            float ww = 2.0F + 0.8F * b.width();
            box(8 - ww / 2, top + 1.4F, 7.6F, 8 + ww / 2, top + 2.0F, 8.4F, 0, metal, metalRegion, false);
        }
        float L = 4.5F + 1.2F * b.tip() + 0.4F * b.length();
        float W = 1.6F + 0.6F * b.width();
        bladeBody(8, top + 1.4F, L, W, 0.5F, b.curve() * 0.8F, false);
    }

    private void hammerHead(float top) {
        float hx = 5.0F + 1.2F * b.width();
        float hy = 3.0F + 0.5F * b.width();
        float hz = 3.0F + 0.4F * b.width();
        float y0 = top - hy + 0.6F;
        box(8 - hx / 2, y0, 8 - hz / 2, 8 + hx / 2, y0 + hy, 8 + hz / 2, 0, metal, metalRegion, false);
        // faces de bater (mais claras) e cintas
        box(8 - hx / 2 - 0.4F, y0 - 0.2F, 8 - hz / 2 - 0.2F, 8 - hx / 2, y0 + hy + 0.2F, 8 + hz / 2 + 0.2F, 0, edge, Region.EDGE, false);
        if (b.tip() > 0) {
            // picareta de guerra: o lado direito vira espigão
            float sl = 1.5F + 1.0F * b.tip();
            float cy = y0 + hy / 2;
            box(8 + hx / 2, cy - hy * 0.3F, 8 - hz * 0.3F, 8 + hx / 2 + sl * 0.55F, cy + hy * 0.3F, 8 + hz * 0.3F, 0, metal, metalRegion, false);
            box(8 + hx / 2 + sl * 0.55F, cy - hy * 0.15F, 8 - hz * 0.15F, 8 + hx / 2 + sl, cy + hy * 0.15F, 8 + hz * 0.15F,
                    -8F * b.curve(), 8 + hx / 2 + sl * 0.55F, cy, 8, edge, Region.EDGE, false);
        } else {
            box(8 + hx / 2, y0 - 0.2F, 8 - hz / 2 - 0.2F, 8 + hx / 2 + 0.4F, y0 + hy + 0.2F, 8 + hz / 2 + 0.2F, 0, edge, Region.EDGE, false);
        }
        box(8 - hx / 2 + 0.8F, y0 - 0.15F, 8 - hz / 2 - 0.15F, 8 - hx / 2 + 1.3F, y0 + hy + 0.15F, 8 + hz / 2 + 0.15F, 0,
                guardColor(), guardRegion(), false);
        box(8 + hx / 2 - 1.3F, y0 - 0.15F, 8 - hz / 2 - 0.15F, 8 + hx / 2 - 0.8F, y0 + hy + 0.15F, 8 + hz / 2 + 0.15F, 0,
                guardColor(), guardRegion(), false);
        // serrilha vira cravos nas faces
        for (int i = 0; i < b.serration(); i++) {
            float cx = 8 - hx / 2 + hx * (i + 1) / (b.serration() + 1);
            box(cx - 0.3F, y0 + hy, 8 - 0.3F, cx + 0.3F, y0 + hy + 0.7F, 8 + 0.3F, 0, edge, Region.EDGE, false);
        }
        if (rune) box(7.3F, y0 + hy * 0.3F, 8 - hz / 2 - 0.08F, 8.7F, y0 + hy * 0.7F, 8 + hz / 2 + 0.08F, 0, runeColor, Region.RUNE, true);
    }

    private void scytheHead(float top) {
        box(7.1F, top - 1.6F, 7.1F, 8.9F, top + 0.4F, 8.9F, 0, guardColor(), guardRegion(), false);
        float bladeLen = 7.0F + 1.0F * b.width() + 0.5F * b.length() + (b.tip() >= 3 ? 1.5F : 0F);
        float h0 = 1.6F + 0.4F * b.width();
        float drop = 2.0F + 1.4F * b.curve();
        int n = 8;
        float tipFrac = 0.25F + 0.07F * b.tip();
        for (int i = 0; i < n; i++) {
            float t0 = i / (float) n, t1 = (i + 1) / (float) n, tm = (t0 + t1) / 2;
            float xa = 8.9F - bladeLen * t1, xb = 8.9F - bladeLen * t0 + 0.02F;
            float yc = top - 0.5F - drop * tm * tm;
            float slope = 2 * drop * tm / bladeLen;
            float rot = (float) Math.toDegrees(Math.atan(slope));
            float h = h0 * width(tm, tipFrac, 0.15F);
            float xm = (xa + xb) / 2;
            box(xa, yc - h * 0.3F, 7.7F, xb, yc + h * 0.5F, 8.3F, rot, xm, yc, 8, metal, metalRegion, false);
            box(xa, yc - h * 0.5F, 7.82F, xb, yc - h * 0.3F, 8.18F, rot, xm, yc, 8, edge, Region.EDGE, false);
            if (b.serration() > 0 && i % 2 == 1 && tm < 0.8F) tooth(xm, yc - h * 0.5F, b.serration() >= 3 ? 0.7F : 0.45F, 0.36F, rot);
            if (rune && i == 2) box(xa + 0.2F, yc, 7.64F, xb - 0.2F, yc + h * 0.3F, 8.36F, rot, xm, yc, 8, runeColor, Region.RUNE, true);
        }
    }

    // =====================================================================================
    //  Peças comuns
    // =====================================================================================
    private void grip(float g0, float g1, float t) {
        int tint = switch (b.grip()) {
            case LONG -> 0xB0604A;
            case LIGHT -> 0xE6D8B8;
            default -> 0xFFFFFF;
        };
        box(8 - t / 2, g0, 8 - t / 2, 8 + t / 2, g1, 8 + t / 2, 0, tint, Region.LEATHER, false);
        // voltas do couro (anéis um pouco mais grossos)
        for (float y = g0 + 0.5F; y < g1 - 0.3F; y += 1.2F) {
            box(8 - t / 2 - 0.08F, y, 8 - t / 2 - 0.08F, 8 + t / 2 + 0.08F, y + 0.35F, 8 + t / 2 + 0.08F, 0,
                    darken(tint, 0.25F), Region.WRAP, false);
        }
    }

    private void pommel(float g0) {
        box(7.0F, g0 - 1.5F, 7.0F, 9.0F, g0, 9.0F, 0, guardColor(), guardRegion(), false);
        if (b.material() == BladeMaterial.STARMETAL || b.quality() >= 4) {
            int gem = rune ? runeColor : b.material() == BladeMaterial.STARMETAL ? BladeMaterial.STARMETAL.color : 0xE0303A;
            box(7.55F, g0 - 1.1F, 6.9F, 8.45F, g0 - 0.4F, 9.1F, 0, gem, Region.GEM, true);
        }
    }

    /** Guarda/pomo: ouro nas armas de ouro e de qualidade alta; senão o metal escuro. */
    private int guardColor() {
        return b.material() == BladeMaterial.GOLD || b.quality() >= 3 ? 0xFFFFFF : dark;
    }

    private Region guardRegion() {
        return b.material() == BladeMaterial.GOLD || b.quality() >= 3 ? Region.GOLD : metalRegion;
    }

    /** Caixa sem rotação ({@code noRotation} é sempre 0 — só para as chamadas ficarem alinhadas com as giradas). */
    private void box(float x0, float y0, float z0, float x1, float y1, float z1, int noRotation, int rgb, Region region, boolean glow) {
        box(x0, y0, z0, x1, y1, z1, 0F, 8F, 8F, 8F, rgb, region, glow);
    }

    private void box(float x0, float y0, float z0, float x1, float y1, float z1, float rotZ, float px, float py, float pz,
                     int rgb, Region region, boolean glow) {
        boxes.add(new Box(x0, y0, z0, x1, y1, z1, rotZ, px, py, pz, rgb, region, glow));
        // limites aproximados (a rotação é pequena; para enquadrar no inventário basta)
        minX = Math.min(minX, x0);
        maxX = Math.max(maxX, x1);
        minY = Math.min(minY, y0);
        maxY = Math.max(maxY, y1);
    }

    // =====================================================================================
    //  Cores
    // =====================================================================================
    static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    static int lighten(int c, float t) {
        return mix(c, 0xFFFFFF, t);
    }

    static int darken(int c, float t) {
        return mix(c, 0x000000, t);
    }
}
