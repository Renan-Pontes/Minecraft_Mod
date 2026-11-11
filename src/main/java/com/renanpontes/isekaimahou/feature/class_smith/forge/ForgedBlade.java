package com.renanpontes.isekaimahou.feature.class_smith.forge;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.feature.class_smith.SmithKeys;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * As partes de uma Lâmina Forjada (componente {@code isekai_mahou:forged_blade}). Imutável: cada operação de forja
 * devolve uma cópia. Limites: comprimento 0–4, largura/curva/ponta/serrilha 0–3, qualidade 1–5. A soma das partes
 * ({@link #points()}) não pode passar da {@link #capacity()} da qualidade — é isso que obriga a ESCOLHER (uma lâmina
 * longa, larga, curva e serrilhada ao mesmo tempo só sai de uma obra-prima).
 *
 * @param rune   elemento da runa gravada ("" = sem runa)
 * @param smith  nome de quem forjou (só texto da dica)
 */
public record ForgedBlade(BladeFamily family, BladeMaterial material, int length, int width, int curve, int tip,
                          int serration, Grip grip, Finish finish, String rune, int quality, String smith) {

    public static final int MAX_LENGTH = 4;
    public static final int MAX_PART = 3;
    public static final int MAX_QUALITY = 5;

    /** Adaga de ferro simples (lâmina nova, kit, conversão). */
    public static final ForgedBlade DEFAULT = new ForgedBlade(BladeFamily.BLADE, BladeMaterial.IRON, 0, 0, 0, 0, 0,
            Grip.STANDARD, Finish.BRIGHT, "", 1, "");

    /** Empunhadura: Couro (equilibrada), Longa (mais alcance e dano, mais lenta), Leve (mais rápida, menos dano). */
    public enum Grip {
        STANDARD("standard", 0F, 0F, 0F),
        LONG("long", 0.5F, -0.15F, 0.5F),
        LIGHT("light", -0.4F, 0.25F, 0F);

        public static final Grip[] VALUES = values();
        public final String id;
        public final float damage, speed, reach;

        Grip(String id, float damage, float speed, float reach) {
            this.id = id;
            this.damage = damage;
            this.speed = speed;
            this.reach = reach;
        }

        public MutableComponent displayName() {
            return Component.translatable(SmithKeys.BLADE_GRIP + id);
        }

        public Grip next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }

        public static Grip byId(String id) {
            for (Grip g : VALUES) if (g.id.equals(id)) return g;
            return STANDARD;
        }
    }

    /**
     * Acabamento: Brilhante (polido: +20% contra mortos-vivos), Fosco (não reflete: +25% em golpes pelas costas) e
     * Rúnico (exige runa: a runa queima 50% mais forte e brilha).
     */
    public enum Finish {
        BRIGHT("bright"),
        MATTE("matte"),
        RUNIC("runic");

        public static final Finish[] VALUES = values();
        public final String id;

        Finish(String id) {
            this.id = id;
        }

        public MutableComponent displayName() {
            return Component.translatable(SmithKeys.BLADE_FINISH + id);
        }

        public static Finish byId(String id) {
            for (Finish f : VALUES) if (f.id.equals(id)) return f;
            return BRIGHT;
        }
    }

    public static final Codec<ForgedBlade> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("family", "blade").forGetter(b -> b.family.id),
            Codec.STRING.optionalFieldOf("material", "iron").forGetter(b -> b.material.id),
            Codec.INT.optionalFieldOf("length", 0).forGetter(ForgedBlade::length),
            Codec.INT.optionalFieldOf("width", 0).forGetter(ForgedBlade::width),
            Codec.INT.optionalFieldOf("curve", 0).forGetter(ForgedBlade::curve),
            Codec.INT.optionalFieldOf("tip", 0).forGetter(ForgedBlade::tip),
            Codec.INT.optionalFieldOf("serration", 0).forGetter(ForgedBlade::serration),
            Codec.STRING.optionalFieldOf("grip", "standard").forGetter(b -> b.grip.id),
            Codec.STRING.optionalFieldOf("finish", "bright").forGetter(b -> b.finish.id),
            Codec.STRING.optionalFieldOf("rune", "").forGetter(ForgedBlade::rune),
            Codec.INT.optionalFieldOf("quality", 1).forGetter(ForgedBlade::quality),
            Codec.STRING.optionalFieldOf("smith", "").forGetter(ForgedBlade::smith)
    ).apply(i, (f, m, len, w, c, t, s, g, fin, r, q, who) -> new ForgedBlade(BladeFamily.byId(f), BladeMaterial.byId(m),
            len, w, c, t, s, Grip.byId(g), Finish.byId(fin), r, q, who).clamped()));

    public static final StreamCodec<FriendlyByteBuf, ForgedBlade> STREAM_CODEC = StreamCodec.of(
            (buf, b) -> {
                buf.writeByte(b.family.ordinal());
                buf.writeByte(b.material.ordinal());
                buf.writeByte(b.length);
                buf.writeByte(b.width);
                buf.writeByte(b.curve);
                buf.writeByte(b.tip);
                buf.writeByte(b.serration);
                buf.writeByte(b.grip.ordinal());
                buf.writeByte(b.finish.ordinal());
                buf.writeUtf(b.rune, 32);
                buf.writeByte(b.quality);
                buf.writeUtf(b.smith, 64);
            },
            buf -> new ForgedBlade(BladeFamily.VALUES[Mth.clamp(buf.readByte(), 0, BladeFamily.VALUES.length - 1)],
                    BladeMaterial.VALUES[Mth.clamp(buf.readByte(), 0, BladeMaterial.VALUES.length - 1)],
                    buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(),
                    Grip.VALUES[Mth.clamp(buf.readByte(), 0, Grip.VALUES.length - 1)],
                    Finish.VALUES[Mth.clamp(buf.readByte(), 0, Finish.VALUES.length - 1)],
                    buf.readUtf(32), buf.readByte(), buf.readUtf(64)).clamped());

    /** Valores dentro dos limites (NBT editado à mão não quebra o renderer). */
    public ForgedBlade clamped() {
        return new ForgedBlade(family, material, Mth.clamp(length, 0, MAX_LENGTH), Mth.clamp(width, 0, MAX_PART),
                Mth.clamp(curve, 0, MAX_PART), Mth.clamp(tip, 0, MAX_PART), Mth.clamp(serration, 0, MAX_PART), grip,
                finish == Finish.RUNIC && rune.isEmpty() ? Finish.BRIGHT : finish, runeElement() == null ? "" : rune,
                Mth.clamp(quality, 1, MAX_QUALITY), smith == null ? "" : smith);
    }

    /** Elemento da runa, ou null. */
    @Nullable
    public Element runeElement() {
        if (rune == null || rune.isEmpty()) return null;
        Element e = Element.byId(rune);
        return e != null && e.id.equals(rune) ? e : null;
    }

    /** Pontos de forja usados (cada degrau de parte = 1; runa = 2; empunhadura especial = 1). */
    public int points() {
        return length + width + curve + tip + serration + (rune.isEmpty() ? 0 : 2) + (grip == Grip.STANDARD ? 0 : 1);
    }

    /** Pontos que a qualidade permite: 5 / 7 / 9 / 11 / 13. */
    public int capacity() {
        return capacityOf(quality);
    }

    public static int capacityOf(int quality) {
        return 3 + 2 * Mth.clamp(quality, 1, MAX_QUALITY);
    }

    public boolean fits() {
        return points() <= capacity();
    }

    /** Obra-prima: qualidade máxima com todos os pontos usados (tarefa do rank V). */
    public boolean masterwork() {
        return quality >= MAX_QUALITY && points() >= capacity();
    }

    public MutableComponent typeName() {
        return family.typeName(length);
    }

    // ---------------------------------------------------------------- cópias com uma parte trocada
    public ForgedBlade withFamily(BladeFamily f) {
        return new ForgedBlade(f, material, length, width, curve, tip, serration, grip, finish, rune, quality, smith);
    }

    public ForgedBlade withMaterial(BladeMaterial m) {
        return new ForgedBlade(family, m, length, width, curve, tip, serration, grip, finish, rune, quality, smith);
    }

    public ForgedBlade withPart(Part part, int value) {
        int v = Mth.clamp(value, 0, part.max);
        return switch (part) {
            case LENGTH -> new ForgedBlade(family, material, v, width, curve, tip, serration, grip, finish, rune, quality, smith);
            case WIDTH -> new ForgedBlade(family, material, length, v, curve, tip, serration, grip, finish, rune, quality, smith);
            case CURVE -> new ForgedBlade(family, material, length, width, v, tip, serration, grip, finish, rune, quality, smith);
            case TIP -> new ForgedBlade(family, material, length, width, curve, v, serration, grip, finish, rune, quality, smith);
            case SERRATION -> new ForgedBlade(family, material, length, width, curve, tip, v, grip, finish, rune, quality, smith);
        };
    }

    public int part(Part part) {
        return switch (part) {
            case LENGTH -> length;
            case WIDTH -> width;
            case CURVE -> curve;
            case TIP -> tip;
            case SERRATION -> serration;
        };
    }

    public ForgedBlade withGrip(Grip g) {
        return new ForgedBlade(family, material, length, width, curve, tip, serration, g, finish, rune, quality, smith);
    }

    public ForgedBlade withFinish(Finish f) {
        return new ForgedBlade(family, material, length, width, curve, tip, serration, grip, f, rune, quality, smith);
    }

    public ForgedBlade withRune(@Nullable Element e) {
        String r = e == null ? "" : e.id;
        Finish f = e == null && finish == Finish.RUNIC ? Finish.BRIGHT : finish;
        return new ForgedBlade(family, material, length, width, curve, tip, serration, grip, f, r, quality, smith);
    }

    public ForgedBlade withQuality(int q) {
        return new ForgedBlade(family, material, length, width, curve, tip, serration, grip, finish, rune,
                Mth.clamp(q, 1, MAX_QUALITY), smith);
    }

    public ForgedBlade withSmith(String who) {
        return new ForgedBlade(family, material, length, width, curve, tip, serration, grip, finish, rune, quality, who);
    }

    /** As partes ajustáveis em degraus (Alongar, Alargar, Curvar, Afiar Ponta, Serrilhar). */
    public enum Part {
        LENGTH("length", MAX_LENGTH),
        WIDTH("width", MAX_PART),
        CURVE("curve", MAX_PART),
        TIP("tip", MAX_PART),
        SERRATION("serration", MAX_PART);

        public static final Part[] VALUES = values();
        public final String id;
        public final int max;

        Part(String id, int max) {
            this.id = id;
            this.max = max;
        }

        public MutableComponent displayName() {
            return Component.translatable(SmithKeys.BLADE_PART + id);
        }
    }
}
