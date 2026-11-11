package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/**
 * Uma obra: uma planta autorizada no menu, com uma ou mais PEÇAS (um prédio = 1 peça; a muralha = torres, trechos e
 * portões). Cada peça passa pelas fases limpar → fundação → construir → acabamento.
 */
public final class Project {
    public static final int PHASE_CLEAR = 0;
    public static final int PHASE_FOUNDATION = 1;
    public static final int PHASE_BUILD = 2;
    public static final int PHASE_FINISH = 3;

    /** Peça da obra: molde, centro (x, z), rotação e a altura do chão (resolvida quando a obra começa). */
    public static final class Piece {
        public static final int BUILDING = 0, WALL = 1, TOWER = 2, GATE = 3;
        public final ResourceLocation template;
        public final int cx, cz;
        public final Rotation rotation;
        public final int kind;
        /** Altura do piso (camada y=0 do molde); {@link Integer#MIN_VALUE} = ainda não medida. */
        public int baseY;

        public Piece(ResourceLocation template, int cx, int cz, Rotation rotation, int kind, int baseY) {
            this.template = template;
            this.cx = cx;
            this.cz = cz;
            this.rotation = rotation;
            this.kind = kind;
            this.baseY = baseY;
        }

        public boolean resolved() {
            return baseY != Integer.MIN_VALUE;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("T", template.toString());
            t.putInt("X", cx);
            t.putInt("Z", cz);
            t.putInt("R", rotation.ordinal());
            t.putInt("K", kind);
            t.putInt("Y", baseY);
            return t;
        }

        @Nullable
        static Piece load(CompoundTag t) {
            ResourceLocation rl = ResourceLocation.tryParse(t.getString("T"));
            if (rl == null) return null;
            Rotation[] rs = Rotation.values();
            return new Piece(rl, t.getInt("X"), t.getInt("Z"), rs[Math.floorMod(t.getInt("R"), rs.length)], t.getInt("K"),
                    t.getInt("Y"));
        }
    }

    public final String key;
    /** Célula da cidade (−1 para praça e muralha). */
    public final int cell;
    public final List<Piece> pieces;
    public int pieceIdx;
    public int phase;
    public int cursor;
    /** Fração de bloco acumulada (velocidade fracionária). */
    public float accum;
    /** Construtor encarregado (UUID da entidade) ou null. */
    @Nullable public UUID builder;
    /** Ticks esperando o construtor chegar (depois disso ele é trazido até a obra). */
    public int waiting;

    public Project(String key, int cell, List<Piece> pieces) {
        this.key = key;
        this.cell = cell;
        this.pieces = pieces;
    }

    @Nullable
    public Piece current() {
        return pieceIdx < pieces.size() ? pieces.get(pieceIdx) : null;
    }

    public boolean done() {
        return pieceIdx >= pieces.size();
    }

    /** Progresso 0..1 (limpeza 10%, fundação 10%, blocos 78%, acabamento 2% de cada peça). */
    public float progress(int stepsInPhase) {
        if (pieces.isEmpty()) return 1F;
        float piece = switch (phase) {
            case PHASE_CLEAR -> 0.10F * frac(stepsInPhase);
            case PHASE_FOUNDATION -> 0.10F + 0.10F * frac(stepsInPhase);
            case PHASE_BUILD -> 0.20F + 0.78F * frac(stepsInPhase);
            default -> 0.98F;
        };
        return Math.min(1F, (pieceIdx + piece) / pieces.size());
    }

    private float frac(int total) {
        return total <= 0 ? 1F : Math.min(1F, cursor / (float) total);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Key", key);
        t.putInt("Cell", cell);
        ListTag l = new ListTag();
        for (Piece p : pieces) l.add(p.save());
        t.put("Pieces", l);
        t.putInt("PieceIdx", pieceIdx);
        t.putInt("Phase", phase);
        t.putInt("Cursor", cursor);
        if (builder != null) t.putUUID("Builder", builder);
        return t;
    }

    @Nullable
    public static Project load(CompoundTag t) {
        List<Piece> pieces = new ArrayList<>();
        ListTag l = t.getList("Pieces", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            Piece p = Piece.load(l.getCompound(i));
            if (p != null) pieces.add(p);
        }
        if (pieces.isEmpty()) return null;
        Project p = new Project(t.getString("Key"), t.getInt("Cell"), pieces);
        p.pieceIdx = t.getInt("PieceIdx");
        p.phase = t.getInt("Phase");
        p.cursor = t.getInt("Cursor");
        if (t.hasUUID("Builder")) p.builder = t.getUUID("Builder");
        return p;
    }
}
