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
    /** v1.0: suaviza o terreno em volta do lote aplainado (roda entre a fundação e os blocos). */
    public static final int PHASE_SKIRT = 4;

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

    // ---- v1.4 (planejamento): tudo opcional no NBT, obras antigas carregam com os valores padrão
    /** Tipo da obra: construir, melhorar (nível), consertar, aplainar o terreno ou subir o nível do centro. */
    public static final String KIND_BUILD = "build", KIND_UPGRADE = "upgrade", KIND_REPAIR = "repair", KIND_TERRAIN = "terrain",
            KIND_CENTER = "center";
    /**
     * Onda 5: REFORMA de degrau (muralha velha -> muralha do degrau novo). Usa moldes de troca ({@link TierWalls}): não
     * limpa nem funda nada, só tira o que os construtores tinham posto e põe o novo onde está livre.
     */
    public static final String KIND_TIER = "tier";
    /** Onda 5 (city_styles): refazer um prédio de pé no estilo da cidade (mesmo nível), depois de uma Reforma. */
    public static final String KIND_RESTYLE = "restyle";
    public String kind = KIND_BUILD;
    /** Nível que o prédio (ou o centro) terá quando a obra terminar. */
    public int level = 1;
    /** Estilo escolhido para o prédio ("" = o da cidade). */
    public String style = "";
    /** Meia-largura da área limpa/aplainada (0 = só o molde + 1 bloco de margem). */
    public int clearHalf;

    /**
     * v1.0 (city_plan): lote cuja obra tem de terminar ANTES desta começar ({@link Integer#MIN_VALUE} = nenhum).
     * Usado ao mudar um prédio de lugar: primeiro a demolição do lote antigo, depois a obra no lugar novo.
     */
    public int after = Integer.MIN_VALUE;

    // ---- v1.0 (city_magic): Impulso de Obra — só em memória (a magia dura segundos; recarregar o mundo só a encerra)
    /** Multiplicador de velocidade dado por magia enquanto {@link #magicUntil} (tempo do jogo) não passa. */
    public transient float magicMult = 1F;
    public transient long magicUntil;

    // ---- onda 6 (city_pace): a obra leva DIAS — tudo opcional no NBT (obra antiga: 0 = "medir na primeira vez")
    /**
     * Duração NOMINAL em dias do Minecraft (antes do multiplicador "ritmo da cidade"). 0 = ainda não medida: o
     * {@code CityPace} preenche pela tabela dele na primeira vez que vê a obra. Quem cria a obra pode fixar aqui.
     */
    public float days;
    /** Dias de trabalho já feitos (0..days). A obra física só anda até esta fração. */
    public float worked;
    /** Dias ganhos com materiais entregues pelos jogadores (teto por obra no {@code CityPace}). */
    public float gift;
    /** Dias ganhos HOJE com jogadores trabalhando no canteiro, e o dia a que o número se refere. */
    public float hands;
    public long handsDay = -1;
    /** Dias ganhos HOJE com magia de obra (Impulso/Erguer), e o dia a que o número se refere. */
    public float magic;
    public long magicDay = -1;
    /** Última etapa anunciada no Diário da cidade (0 = nenhuma). */
    public int stage;
    /** Andaimes postos pelos construtores (só estes blocos são tirados no acabamento). */
    public final List<net.minecraft.core.BlockPos> scaffold = new ArrayList<>();

    // ---- onda 7 (city_pause): "quero poder parar as construções no meio" — tudo opcional no NBT (padrão: falso)
    /** Obra PAUSADA: não gasta dias, materiais nem ajuda, não segura construtor; o canteiro fica como está. */
    public boolean paused;
    /** Obra CANCELADA no meio: a equipe desmonta o que tinha posto (peças e blocos de trás para a frente) e ela sai da fila. */
    public boolean undo;
    /** Reconstrução de um prédio mudado de lugar (o antigo já foi desmontado e pago): não se cancela, só se pausa. */
    public boolean moved;

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
            case PHASE_SKIRT -> 0.20F;
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
        t.putString("Kind", kind);
        t.putInt("Level", level);
        t.putString("Style", style);
        t.putInt("ClearHalf", clearHalf);
        if (after != Integer.MIN_VALUE) t.putInt("After", after);
        if (days > 0F) {
            t.putFloat("Days", days);
            t.putFloat("Worked", worked);
        }
        if (gift > 0F) t.putFloat("Gift", gift);
        if (hands > 0F) {
            t.putFloat("Hands", hands);
            t.putLong("HandsDay", handsDay);
        }
        if (magic > 0F) {
            t.putFloat("Magic", magic);
            t.putLong("MagicDay", magicDay);
        }
        if (stage != 0) t.putInt("Stage", stage);
        if (!scaffold.isEmpty()) t.putLongArray("Scaf", scaffold.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
        if (paused) t.putBoolean("Paused", true);
        if (undo) t.putBoolean("Undo", true);
        if (moved) t.putBoolean("Moved", true);
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
        if (t.contains("Kind")) p.kind = t.getString("Kind");
        p.level = Math.max(1, t.getInt("Level"));
        p.style = t.getString("Style");
        p.clearHalf = t.getInt("ClearHalf");
        if (t.contains("After")) p.after = t.getInt("After");
        p.days = Math.max(0F, t.getFloat("Days"));
        p.worked = Math.max(0F, t.getFloat("Worked"));
        p.gift = t.getFloat("Gift");
        p.hands = t.getFloat("Hands");
        p.handsDay = t.contains("HandsDay") ? t.getLong("HandsDay") : -1;
        p.magic = t.getFloat("Magic");
        p.magicDay = t.contains("MagicDay") ? t.getLong("MagicDay") : -1;
        p.stage = t.getInt("Stage");
        for (long at : t.getLongArray("Scaf")) p.scaffold.add(net.minecraft.core.BlockPos.of(at));
        p.paused = t.getBoolean("Paused");
        p.undo = t.getBoolean("Undo");
        p.moved = t.getBoolean("Moved");
        return p;
    }
}
