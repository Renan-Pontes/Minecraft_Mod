package com.renanpontes.isekaimahou.feature.ritual_mage.research;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;

/**
 * Minijogo da Tese: grade 5×5 com runa de entrada (coluna 0), runa de saída (coluna 4), fissuras e
 * runas fixas de fusão. O jogador escreve runas (notas) nas células vazias para formar um circuito.
 *
 * Gerado de forma determinística a partir de (tese, semente): cliente e servidor montam a mesma grade.
 * A geração garante que exista solução usando só as notas exigidas.
 */
public final class ThesisPuzzle {
    public static final int SIZE = 5;
    public static final int CELLS = SIZE * SIZE;
    public static final byte EMPTY = 0, BLOCKED = 1, FIXED = 2, ENTRY = 3, EXIT = 4;

    public final byte[] type = new byte[CELLS];
    public final Element[] element = new Element[CELLS];
    public int entry, exit;

    private ThesisPuzzle() {}

    public static int cell(int row, int col) { return row * SIZE + col; }
    public static int row(int cell) { return cell / SIZE; }
    public static int col(int cell) { return cell % SIZE; }

    /** Vizinhos ortogonais de uma célula. */
    public static int[] neighbors(int cell) {
        int r = row(cell), c = col(cell);
        int[] tmp = new int[4];
        int n = 0;
        if (r > 0) tmp[n++] = cell - SIZE;
        if (r < SIZE - 1) tmp[n++] = cell + SIZE;
        if (c > 0) tmp[n++] = cell - 1;
        if (c < SIZE - 1) tmp[n++] = cell + 1;
        return Arrays.copyOf(tmp, n);
    }

    /**
     * Compatibilidade entre runas vizinhas: mesmo elemento; Arcano com tudo; dois elementos base
     * (sempre formam uma fusão); ou uma fusão ao lado de um dos seus elementos-pai.
     */
    public static boolean compatible(Element a, Element b) {
        if (a == b || a == Element.ARCANE || b == Element.ARCANE) return true;
        if (a.isBase() && b.isBase()) return Element.fusionOf(a, b) != null;
        if (a.isFusion() && b.isBase()) return a.parentA == b || a.parentB == b;
        if (b.isFusion() && a.isBase()) return b.parentA == a || b.parentB == a;
        return false;
    }

    public boolean isWritable(int cell) {
        return cell >= 0 && cell < CELLS && type[cell] == EMPTY;
    }

    // =====================================================================================
    //  Geração
    // =====================================================================================
    public static ThesisPuzzle generate(Thesis thesis, int seed) {
        Random rnd = new Random(seed * 0x9E3779B1L + thesis.index() * 7919L + 17);
        for (int attempt = 0; attempt < 120; attempt++) {
            int relax = attempt / 30;
            int blocked = Math.max(0, thesis.blocked() - relax);
            int fixed = Math.max(0, thesis.fixed() - relax);
            ThesisPuzzle p = tryGenerate(thesis, rnd, blocked, fixed);
            if (p.constructSolution(thesis) != null) return p;
        }
        // Sem sorte: grade limpa com entrada e saída na mesma linha (sempre tem solução).
        ThesisPuzzle p = new ThesisPuzzle();
        p.entry = cell(2, 0);
        p.exit = cell(2, SIZE - 1);
        p.type[p.entry] = ENTRY;
        p.element[p.entry] = thesis.entry();
        p.type[p.exit] = EXIT;
        p.element[p.exit] = thesis.exit();
        return p;
    }

    private static ThesisPuzzle tryGenerate(Thesis thesis, Random rnd, int blocked, int fixed) {
        ThesisPuzzle p = new ThesisPuzzle();
        p.entry = cell(rnd.nextInt(SIZE), 0);
        p.exit = cell(rnd.nextInt(SIZE), SIZE - 1);
        p.type[p.entry] = ENTRY;
        p.element[p.entry] = thesis.entry();
        p.type[p.exit] = EXIT;
        p.element[p.exit] = thesis.exit();
        // Runas fixas: fusões, longe da entrada/saída (não podem bloquear o acesso a elas)
        int placed = 0;
        for (int tries = 0; placed < fixed && tries < 60; tries++) {
            int c = rnd.nextInt(CELLS);
            if (p.type[c] != EMPTY || adjacentTo(c, p.entry) || adjacentTo(c, p.exit)) continue;
            boolean nearFixed = false;
            for (int n : neighbors(c)) nearFixed |= p.type[n] == FIXED;
            if (nearFixed) continue;
            p.type[c] = FIXED;
            p.element[c] = Element.FUSIONS.get(rnd.nextInt(Element.FUSIONS.size()));
            placed++;
        }
        placed = 0;
        for (int tries = 0; placed < blocked && tries < 60; tries++) {
            int c = rnd.nextInt(CELLS);
            if (p.type[c] != EMPTY) continue;
            p.type[c] = BLOCKED;
            placed++;
        }
        return p;
    }

    private static boolean adjacentTo(int c, int other) {
        for (int n : neighbors(c)) if (n == other) return true;
        return c == other;
    }

    /**
     * Monta uma solução de referência usando exatamente as notas exigidas (requisito "qualquer" vira Arcano),
     * só em células "seguras" (vazias e sem runa fixa ao lado). Retorna null se não achar — a geração então
     * tenta outro arranjo. A solução é conferida com {@link #evaluate}, então nunca aceita grade impossível.
     */
    @Nullable
    public Element[] constructSolution(Thesis thesis) {
        List<Element> notes = new ArrayList<>();
        for (Thesis.Req r : thesis.reqs()) {
            for (int k = 0; k < r.count(); k++) notes.add(r.element() == null ? Element.ARCANE : r.element());
        }
        boolean[] safe = new boolean[CELLS];
        for (int c = 0; c < CELLS; c++) {
            if (type[c] != EMPTY) continue;
            boolean ok = true;
            for (int n : neighbors(c)) ok &= type[n] != FIXED;
            safe[c] = ok;
        }
        // Menor caminho de um vizinho da entrada até um vizinho da saída
        int[] prev = new int[CELLS];
        Arrays.fill(prev, -2);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int n : neighbors(entry)) {
            if (safe[n]) { prev[n] = -1; queue.add(n); }
        }
        int end = -1;
        while (!queue.isEmpty() && end < 0) {
            int c = queue.poll();
            if (adjacentTo(c, exit)) { end = c; break; }
            for (int n : neighbors(c)) {
                if (safe[n] && prev[n] == -2) { prev[n] = c; queue.add(n); }
            }
        }
        if (end < 0) return null;
        List<Integer> cells = new ArrayList<>();
        for (int c = end; c >= 0; c = prev[c]) cells.add(0, c);
        if (cells.size() > notes.size()) return null;
        // Células extras grudadas no circuito, preferindo as que não encostam na entrada/saída
        while (cells.size() < notes.size()) {
            int best = -1;
            boolean bestTouchesIo = true;
            for (int c = 0; c < CELLS; c++) {
                if (!safe[c] || cells.contains(c)) continue;
                boolean attached = false, touchesIo = false;
                for (int n : neighbors(c)) {
                    attached |= cells.contains(n) || n == entry;
                    touchesIo |= n == entry || n == exit;
                }
                if (attached && (best < 0 || (bestTouchesIo && !touchesIo))) {
                    best = c;
                    bestTouchesIo = touchesIo;
                }
            }
            if (best < 0) return null;
            cells.add(best);
        }
        // Emparelha notas e células (as que encostam na entrada/saída precisam combinar com ela)
        int[] owner = new int[notes.size()];
        Arrays.fill(owner, -1);
        for (int ci = 0; ci < cells.size(); ci++) {
            if (!assign(ci, cells, notes, owner, new boolean[notes.size()])) return null;
        }
        Element[] solution = new Element[CELLS];
        for (int ni = 0; ni < notes.size(); ni++) solution[cells.get(owner[ni])] = notes.get(ni);
        return evaluate(solution, thesis).valid() ? solution : null;
    }

    private boolean fits(int cell, Element note) {
        for (int n : neighbors(cell)) {
            if ((n == entry || n == exit) && !compatible(note, element[n])) return false;
        }
        return true;
    }

    private boolean assign(int ci, List<Integer> cells, List<Element> notes, int[] owner, boolean[] seen) {
        for (int ni = 0; ni < notes.size(); ni++) {
            if (seen[ni] || !fits(cells.get(ci), notes.get(ni))) continue;
            seen[ni] = true;
            if (owner[ni] < 0 || assign(owner[ni], cells, notes, owner, seen)) {
                owner[ni] = ci;
                return true;
            }
        }
        return false;
    }

    // =====================================================================================
    //  Avaliação
    // =====================================================================================
    /** Resultado da avaliação do circuito. */
    public static final class Evaluation {
        public final boolean[] inCircuit = new boolean[CELLS];
        public final boolean[] floating = new boolean[CELLS];
        public final boolean[] conflict = new boolean[CELLS];
        /** Pares de células vizinhas incompatíveis (a, b). */
        public final List<int[]> conflictPairs = new ArrayList<>();
        /** Pares de células vizinhas ligadas no circuito (para desenhar as linhas). */
        public final List<int[]> links = new ArrayList<>();
        public boolean connected;
        public boolean anyFloating;
        public int bridges;
        public int[] reqHave;
        public boolean requirementsMet;
        public int placedCount;

        public boolean valid() {
            return connected && conflictPairs.isEmpty() && !anyFloating && requirementsMet;
        }
    }

    /** Elemento da runa numa célula (fixa/entrada/saída ou escrita pelo jogador), ou null. */
    @Nullable
    public Element runeAt(int cell, Element[] placed) {
        if (type[cell] == FIXED || type[cell] == ENTRY || type[cell] == EXIT) return element[cell];
        return type[cell] == EMPTY ? placed[cell] : null;
    }

    public Evaluation evaluate(Element[] placed, Thesis thesis) {
        Evaluation ev = new Evaluation();
        // Conflitos: todo par vizinho com pelo menos uma runa escrita pelo jogador
        for (int c = 0; c < CELLS; c++) {
            Element a = runeAt(c, placed);
            if (a == null) continue;
            for (int n : neighbors(c)) {
                if (n < c) continue;
                Element b = runeAt(n, placed);
                if (b == null) continue;
                boolean playerInvolved = (type[c] == EMPTY) || (type[n] == EMPTY);
                if (!compatible(a, b)) {
                    if (playerInvolved) {
                        ev.conflictPairs.add(new int[]{c, n});
                        ev.conflict[c] = true;
                        ev.conflict[n] = true;
                    }
                }
            }
        }
        // Circuito: busca a partir da entrada por arestas compatíveis
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        ev.inCircuit[entry] = true;
        queue.add(entry);
        while (!queue.isEmpty()) {
            int c = queue.poll();
            if (c == exit) continue; // a saída fecha o circuito, a energia não segue por ela
            Element a = runeAt(c, placed);
            for (int n : neighbors(c)) {
                Element b = runeAt(n, placed);
                if (b == null || ev.inCircuit[n] || !compatible(a, b)) continue;
                ev.inCircuit[n] = true;
                queue.add(n);
            }
        }
        ev.connected = ev.inCircuit[exit];
        for (int c = 0; c < CELLS; c++) {
            if (!ev.inCircuit[c]) continue;
            for (int n : neighbors(c)) {
                if (n > c && ev.inCircuit[n] && compatible(runeAt(c, placed), runeAt(n, placed))) ev.links.add(new int[]{c, n});
            }
        }
        List<Element> placedList = new ArrayList<>();
        for (int c = 0; c < CELLS; c++) {
            if (type[c] == EMPTY && placed[c] != null) {
                ev.placedCount++;
                placedList.add(placed[c]);
                if (!ev.inCircuit[c]) {
                    ev.floating[c] = true;
                    ev.anyFloating = true;
                }
            }
            if (type[c] == FIXED && ev.inCircuit[c]) ev.bridges++;
        }
        ev.reqHave = matchRequirements(placedList, thesis.reqs());
        boolean met = true;
        for (int i = 0; i < thesis.reqs().size(); i++) met &= ev.reqHave[i] >= thesis.reqs().get(i).count();
        ev.requirementsMet = met;
        return ev;
    }

    /**
     * Distribui as runas escritas entre os requisitos (emparelhamento bipartido, já que uma fusão pode
     * contar para qualquer um dos pais). Retorna quantas notas cada requisito recebeu.
     */
    public static int[] matchRequirements(List<Element> notes, List<Thesis.Req> reqs) {
        List<Integer> slotReq = new ArrayList<>();
        for (int i = 0; i < reqs.size(); i++) for (int k = 0; k < reqs.get(i).count(); k++) slotReq.add(i);
        int slots = slotReq.size();
        int[] slotOwner = new int[slots];
        Arrays.fill(slotOwner, -1);
        for (int n = 0; n < notes.size(); n++) {
            augment(n, notes, reqs, slotReq, slotOwner, new boolean[slots]);
        }
        int[] have = new int[reqs.size()];
        for (int s = 0; s < slots; s++) if (slotOwner[s] >= 0) have[slotReq.get(s)]++;
        return have;
    }

    private static boolean augment(int note, List<Element> notes, List<Thesis.Req> reqs, List<Integer> slotReq,
                                   int[] slotOwner, boolean[] seen) {
        for (int s = 0; s < slotReq.size(); s++) {
            if (seen[s] || !reqs.get(slotReq.get(s)).accepts(notes.get(note))) continue;
            seen[s] = true;
            if (slotOwner[s] < 0 || augment(slotOwner[s], notes, reqs, slotReq, slotOwner, seen)) {
                slotOwner[s] = note;
                return true;
            }
        }
        return false;
    }
}
