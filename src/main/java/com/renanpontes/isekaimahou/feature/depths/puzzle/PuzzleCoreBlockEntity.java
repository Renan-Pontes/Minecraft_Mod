package com.renanpontes.isekaimahou.feature.depths.puzzle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.EnigmaRun;
import com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.EnigmaType;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lógica do Selo Rúnico. Quatro enigmas, escolhidos pelas peças encontradas em volta (raio {@code Range}, padrão 6):
 * <ul>
 *   <li><b>LIGHT</b> (há Espelhos): o feixe sai do Selo ({@code facing}), quica nos espelhos e precisa acertar a Lâmpada;</li>
 *   <li><b>SEQUENCE</b> (há Pedras Rúnicas): clicar no Selo mostra a ordem; o jogador repete tocando nas pedras;</li>
 *   <li><b>TILES</b> (há Placas): acender todas as placas pisando, sem pisar duas vezes na mesma nem sair da grade;</li>
 *   <li><b>LEVERS</b> (alavancas + Lâmpadas): cada alavanca inverte um grupo de lâmpadas; acenda todas.</li>
 * </ul>
 * Resolvido: as Grades Seladas somem, o Selo acende para sempre e quem está perto ganha um pouco de XP.
 * Custo: nada roda sem jogador por perto; a varredura das peças é refeita a cada 5 s (só chunks carregados).
 * <p><b>Enigmas de raciocínio</b> (v1.0 r9): com a chave NBT {@code Enigma} o Selo deixa de adivinhar o tipo pelas
 * peças e passa a rodar um {@link EnigmaRun} (Lights Out, estátuas, cifra, Sokoban…), que monta as próprias peças.
 */
public class PuzzleCoreBlockEntity extends BlockEntity {
    public enum Kind { NONE, LEVERS, TILES, LIGHT, SEQUENCE }

    public static final String FLAG_SOLVED = "depths.puzzle";
    public static final String COUNTER = "puzzle.solved";
    private static final String MSG = "message.isekai_mahou.depths.puzzle.";
    private static final int UP = 7;
    private static final int DOWN = 3;
    private static final int XP = 150;
    private static final int STEP_TICKS = 14;
    /** Selos carregados no servidor (só a thread do servidor mexe aqui). */
    private static final Set<PuzzleCoreBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final DustParticleOptions BEAM = new DustParticleOptions(new Vector3f(1.0F, 0.86F, 0.45F), 0.9F);

    private int range = 6;
    private Kind kind = Kind.NONE;
    private boolean scanned;
    private int scanAge;
    private List<BlockPos> lamps = List.of();
    private List<BlockPos> tiles = List.of();
    private List<BlockPos> mirrors = List.of();
    private List<BlockPos> stones = List.of();
    private List<BlockPos> levers = List.of();
    private List<BlockPos> gates = List.of();
    private Set<BlockPos> tileSet = Set.of();
    private boolean[][] wiring = new boolean[0][0];
    private int[] sequence = new int[0];
    private int progress;
    private long playback = -1;
    private int playedStep = -1;
    private final Map<UUID, BlockPos> lastTile = new HashMap<>();
    private final Map<BlockPos, Long> flashUntil = new HashMap<>();
    /** Enigma de raciocínio (null = quebra-cabeça clássico detectado pelas peças). */
    @Nullable
    private EnigmaRun enigma;

    public PuzzleCoreBlockEntity(BlockPos pos, BlockState state) {
        super(PuzzleBlocks.CORE_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------ ciclo de vida / dados salvos

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) LOADED.remove(this);
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Range")) range = Math.max(2, Math.min(16, tag.getInt("Range")));
        enigma = tag.contains("Enigma") ? EnigmaRun.load(this, tag.get("Enigma")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Range", range);
        if (enigma != null) tag.put("Enigma", enigma.save());
    }

    // ------------------------------------------------------------------ enigmas (v1.0 r9)

    @Nullable
    public EnigmaRun enigma() {
        return enigma;
    }

    /** Transforma este Selo num Enigma (comando de teste, torres). {@code type} null = sorteado. */
    public EnigmaRun setupEnigma(@Nullable EnigmaType type, int tier, long seed, int u0, String chain, int role, boolean locked) {
        enigma = new EnigmaRun(this);
        enigma.configure(type, tier, seed, u0, chain, role, locked);
        setChanged();
        return enigma;
    }

    /** Nome curto para comandos: tipo clássico ou "enigma:tipo/nível". */
    public String label() {
        if (enigma == null) return kind.name();
        return "enigma:" + (enigma.type() == null ? "random" : enigma.type().id()) + "/" + enigma.tier();
    }

    /** Percorre os Selos carregados deste nível (só a thread do servidor). */
    public static void forEachLoaded(Level level, java.util.function.Consumer<PuzzleCoreBlockEntity> action) {
        for (PuzzleCoreBlockEntity be : new ArrayList<>(LOADED)) {
            if (be.level == level && !be.isRemoved()) action.accept(be);
        }
    }

    /** Selo-Enigma carregado cuja área contém {@code pos}, ou null. */
    @Nullable
    public static PuzzleCoreBlockEntity enigmaAt(Level level, BlockPos pos) {
        for (PuzzleCoreBlockEntity be : LOADED) {
            if (be.level == level && !be.isRemoved() && be.enigma != null && be.enigma.covers(pos)) return be;
        }
        return null;
    }

    public Kind kind() {
        return kind;
    }

    public boolean solved() {
        return getBlockState().getValue(PuzzleCoreBlock.SOLVED);
    }

    /** A posição faz parte da área deste Selo? */
    public boolean covers(BlockPos p) {
        if (enigma != null) return enigma.covers(p);
        BlockPos c = worldPosition;
        int dy = p.getY() - c.getY();
        return Math.abs(p.getX() - c.getX()) <= range && Math.abs(p.getZ() - c.getZ()) <= range && dy >= -DOWN && dy <= UP;
    }

    /** Selo (carregado) mais próximo cuja área contém {@code pos}. Só no servidor. */
    @Nullable
    public static PuzzleCoreBlockEntity find(Level level, BlockPos pos) {
        PuzzleCoreBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (PuzzleCoreBlockEntity be : LOADED) {
            if (be.level != level || be.isRemoved() || !be.covers(pos)) continue;
            double d = be.worldPosition.distSqr(pos);
            if (d < bestD) {
                best = be;
                bestD = d;
            }
        }
        return best;
    }

    /** Selo carregado mais próximo de {@code pos} (até {@code maxDist} blocos), resolvido ou não. Para os comandos. */
    @Nullable
    public static PuzzleCoreBlockEntity nearest(Level level, BlockPos pos, double maxDist) {
        PuzzleCoreBlockEntity best = null;
        double bestD = maxDist * maxDist;
        for (PuzzleCoreBlockEntity be : LOADED) {
            if (be.level != level || be.isRemoved()) continue;
            double d = be.worldPosition.distSqr(pos);
            if (d <= bestD) {
                best = be;
                bestD = d;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, PuzzleCoreBlockEntity be) {
        if (!(level instanceof ServerLevel sl) || state.getValue(PuzzleCoreBlock.SOLVED)) return;
        long t = sl.getGameTime();
        if ((t & 1L) != 0L) return;
        if (be.enigma != null) {
            be.enigma.tick(sl, t);
            return;
        }
        if (!sl.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.range + 8)) {
            be.lastTile.clear();
            return;
        }
        if ((!be.scanned || ++be.scanAge >= 50) && !be.scan(sl)) return;
        be.expireFlashes(sl, t);
        switch (be.kind) {
            case LEVERS -> {
                if (t % 4 == 0) be.tickLevers(sl);
            }
            case TILES -> be.tickTiles(sl);
            case LIGHT -> {
                if (t % 4 == 0) be.tickLight(sl, t);
            }
            case SEQUENCE -> be.tickSequence(sl, t);
            default -> { }
        }
        if (t % 40 == 0 && be.kind != Kind.NONE) {
            for (ServerPlayer sp : sl.players()) {
                if (be.covers(sp.blockPosition())) Hints.show(sp, "depths_puzzle");
            }
        }
    }

    /** Procura as peças em volta. Devolve false se algum chunk da área ainda não carregou (tenta de novo depois). */
    private boolean scan(ServerLevel sl) {
        BlockPos c = worldPosition;
        if (!sl.hasChunksAt(c.getX() - range, c.getZ() - range, c.getX() + range, c.getZ() + range)) return false;
        List<BlockPos> la = new ArrayList<>(), ti = new ArrayList<>(), mi = new ArrayList<>(), st = new ArrayList<>(),
                le = new ArrayList<>(), ga = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = -DOWN; y <= UP; y++) {
            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    Block b = sl.getBlockState(m).getBlock();
                    // peças de um Enigma vizinho não são deste Selo
                    if ((b instanceof RuneLitBlock || b instanceof RuneStoneBlock || b instanceof LightMirrorBlock || b instanceof LeverBlock)
                            && enigmaAt(sl, m) != null) continue;
                    if (b == PuzzleBlocks.LAMP.get()) la.add(m.immutable());
                    else if (b == PuzzleBlocks.TILE.get()) ti.add(m.immutable());
                    else if (b == PuzzleBlocks.MIRROR.get()) mi.add(m.immutable());
                    else if (b == PuzzleBlocks.STONE.get()) st.add(m.immutable());
                    else if (b == PuzzleBlocks.GATE.get()) ga.add(m.immutable());
                    else if (b instanceof LeverBlock) le.add(m.immutable());
                }
            }
        }
        Kind k = !mi.isEmpty() ? Kind.LIGHT : !st.isEmpty() ? Kind.SEQUENCE : !ti.isEmpty() ? Kind.TILES
                : !le.isEmpty() && !la.isEmpty() ? Kind.LEVERS : Kind.NONE;
        boolean changed = k != kind || le.size() != levers.size() || la.size() != lamps.size() || st.size() != stones.size();
        kind = k;
        lamps = la;
        tiles = ti;
        tileSet = new HashSet<>(ti);
        mirrors = mi;
        stones = st;
        levers = le;
        gates = ga;
        if (changed) {
            long seed = c.asLong() * 0x9E3779B97F4A7C15L ^ sl.getSeed();
            if (k == Kind.LEVERS) buildWiring(seed);
            if (k == Kind.SEQUENCE) buildSequence(seed);
            progress = 0;
            playback = -1;
        }
        scanned = true;
        scanAge = 0;
        return true;
    }

    private static boolean setLit(ServerLevel sl, BlockPos p, boolean lit) {
        BlockState s = sl.getBlockState(p);
        if (!s.hasProperty(RuneLitBlock.LIT) || s.getValue(RuneLitBlock.LIT) == lit) return false;
        sl.setBlock(p, s.setValue(RuneLitBlock.LIT, lit), 3);
        return true;
    }

    private void sound(ServerLevel sl, SoundEvent sound, float volume, float pitch) {
        sl.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
    }

    // ------------------------------------------------------------------ alavancas

    /**
     * Sorteia (sempre igual para o mesmo Selo) quais lâmpadas cada alavanca inverte, garantindo que existe uma
     * combinação que acende todas: primeiro sorteia a resposta, depois acerta a paridade de cada lâmpada com ela.
     */
    private void buildWiring(long seed) {
        int n = levers.size(), m = lamps.size();
        Random r = new Random(seed);
        boolean[] sol = new boolean[n];
        int ones = 0;
        for (int i = 0; i < n; i++) {
            sol[i] = r.nextBoolean();
            if (sol[i]) ones++;
        }
        while (ones < Math.min(2, n)) {
            int i = r.nextInt(n);
            if (!sol[i]) {
                sol[i] = true;
                ones++;
            }
        }
        wiring = new boolean[n][m];
        for (int j = 0; j < m; j++) {
            boolean odd = false;
            for (int i = 0; i < n; i++) {
                if (r.nextInt(5) < 2) {
                    wiring[i][j] = true;
                    if (sol[i]) odd = !odd;
                }
            }
            if (!odd) {
                int pick = r.nextInt(ones);
                for (int i = 0; i < n; i++) {
                    if (sol[i] && pick-- == 0) {
                        wiring[i][j] = !wiring[i][j];
                        break;
                    }
                }
            }
        }
    }

    private void tickLevers(ServerLevel sl) {
        if (wiring.length != levers.size() || lamps.isEmpty() || wiring[0].length != lamps.size()) return;
        boolean[] on = new boolean[levers.size()];
        for (int i = 0; i < on.length; i++) {
            BlockState s = sl.getBlockState(levers.get(i));
            on[i] = s.getBlock() instanceof LeverBlock && s.getValue(LeverBlock.POWERED);
        }
        boolean all = true, changed = false;
        for (int j = 0; j < lamps.size(); j++) {
            boolean lit = false;
            for (int i = 0; i < on.length; i++) if (on[i] && wiring[i][j]) lit = !lit;
            changed |= setLit(sl, lamps.get(j), lit);
            all &= lit;
        }
        if (changed) sound(sl, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, all ? 1.6F : 1.0F);
        if (all) solve(sl);
    }

    // ------------------------------------------------------------------ placas

    private void tickTiles(ServerLevel sl) {
        for (ServerPlayer sp : sl.players()) {
            if (!sp.isAlive() || sp.isSpectator() || !sp.onGround()) continue;
            BlockPos under = BlockPos.containing(sp.getX(), sp.getY() - 0.2, sp.getZ());
            BlockPos last = lastTile.get(sp.getUUID());
            if (tileSet.contains(under) && sl.getBlockState(under).is(PuzzleBlocks.TILE.get())) {
                if (under.equals(last)) continue;
                if (sl.getBlockState(under).getValue(RuneLitBlock.LIT)) {
                    failTiles(sl, sp);
                }
                lastTile.put(sp.getUUID(), under);
                setLit(sl, under, true);
                sl.playSound(null, under, SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.BLOCKS, 1.0F, 1.3F);
                boolean all = true;
                for (BlockPos p : tiles) all &= sl.getBlockState(p).getValue(RuneLitBlock.LIT);
                if (all) {
                    solve(sl);
                    return;
                }
            } else if (last != null) {
                // saiu da grade no meio do caminho: recomeça
                lastTile.remove(sp.getUUID());
                failTiles(sl, sp);
            }
        }
    }

    private void failTiles(ServerLevel sl, ServerPlayer sp) {
        boolean any = false;
        for (BlockPos p : tiles) any |= setLit(sl, p, false);
        lastTile.clear();
        if (!any) return;
        sound(sl, SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.4F);
        sp.displayClientMessage(Component.translatable(MSG + "reset").withColor(0xC8A070), true);
    }

    // ------------------------------------------------------------------ luz e espelhos

    private void tickLight(ServerLevel sl, long t) {
        Direction dir = getBlockState().getValue(PuzzleCoreBlock.FACING);
        BlockPos.MutableBlockPos p = worldPosition.mutable();
        BlockPos hit = null;
        boolean draw = t % 8 == 0;
        for (int step = 0; step < 64; step++) {
            p.move(dir);
            if (!covers(p)) break;
            BlockState s = sl.getBlockState(p);
            if (s.is(PuzzleBlocks.MIRROR.get())) {
                dir = LightMirrorBlock.reflect(dir, s.getValue(LightMirrorBlock.FLIPPED));
                if (draw) sl.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.0);
                continue;
            }
            if (s.is(PuzzleBlocks.LAMP.get())) {
                hit = p.immutable();
                break;
            }
            if (!s.getCollisionShape(sl, p).isEmpty()) break;
            if (draw) sl.sendParticles(BEAM, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.18, 0.03, 0.18, 0.0);
        }
        boolean all = !lamps.isEmpty();
        for (BlockPos l : lamps) {
            boolean lit = l.equals(hit);
            if (setLit(sl, l, lit) && lit) sl.playSound(null, l, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.5F);
            all &= lit;
        }
        if (all) solve(sl);
    }

    // ------------------------------------------------------------------ sequência de runas

    private void buildSequence(long seed) {
        int n = stones.size();
        Random r = new Random(seed);
        sequence = new int[Math.min(6, n + 1)];
        int prev = -1;
        for (int i = 0; i < sequence.length; i++) {
            int v = r.nextInt(n);
            if (n > 1 && v == prev) v = (v + 1 + r.nextInt(n - 1)) % n;
            sequence[i] = v;
            prev = v;
        }
    }

    private float pitchOf(ServerLevel sl, int stone) {
        BlockState s = sl.getBlockState(stones.get(stone));
        int rune = s.hasProperty(RuneStoneBlock.RUNE) ? s.getValue(RuneStoneBlock.RUNE) : stone;
        return 0.7F + rune * 0.25F;
    }

    private void tickSequence(ServerLevel sl, long t) {
        if (playback < 0) return;
        long dt = t - playback;
        int step = (int) (dt / STEP_TICKS);
        if (step >= sequence.length) {
            playback = -1;
            playedStep = -1;
            for (BlockPos p : stones) setLit(sl, p, false);
            return;
        }
        BlockPos stone = stones.get(sequence[step]);
        if (step != playedStep) {
            playedStep = step;
            setLit(sl, stone, true);
            sl.playSound(null, stone, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.5F, pitchOf(sl, sequence[step]));
        } else if (dt % STEP_TICKS >= STEP_TICKS - 4) {
            setLit(sl, stone, false);
        }
    }

    private void expireFlashes(ServerLevel sl, long t) {
        if (flashUntil.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, Long>> it = flashUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            if (t >= e.getValue()) {
                if (playback < 0) setLit(sl, e.getKey(), false);
                it.remove();
            }
        }
    }

    /** O jogador tocou numa Pedra Rúnica da área deste Selo. */
    public void onStoneClicked(ServerPlayer sp, BlockPos pos) {
        if (!(level instanceof ServerLevel sl) || solved() || enigma != null) return;
        if (!scanned && !scan(sl)) return;
        if (kind != Kind.SEQUENCE || playback >= 0 || sequence.length == 0) return;
        int idx = stones.indexOf(pos);
        if (idx < 0) return;
        if (sequence[Math.min(progress, sequence.length - 1)] == idx) {
            setLit(sl, pos, true);
            flashUntil.put(pos.immutable(), sl.getGameTime() + 8);
            sl.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.5F, pitchOf(sl, idx));
            if (++progress >= sequence.length) solve(sl);
        } else {
            progress = 0;
            flashUntil.clear();
            for (BlockPos p : stones) setLit(sl, p, false);
            sound(sl, SoundEvents.BEACON_DEACTIVATE, 0.8F, 0.7F);
            sl.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.01);
            sp.hurt(sl.damageSources().magic(), 2.0F);
            sp.displayClientMessage(Component.translatable(MSG + "wrong").withColor(0xD06060), true);
        }
    }

    /** O jogador tocou no Selo: explica o enigma em uma linha (e, na sequência de runas, mostra a ordem). */
    public void onCoreClicked(ServerPlayer sp) {
        if (!(level instanceof ServerLevel sl)) return;
        if (enigma != null) {
            enigma.onCoreClicked(sp);
            return;
        }
        if (solved()) {
            sp.displayClientMessage(Component.translatable(MSG + "already").withColor(0x9AF0FF), true);
            return;
        }
        if (!scanned && !scan(sl)) return;
        if (kind == Kind.SEQUENCE && playback < 0) {
            progress = 0;
            flashUntil.clear();
            for (BlockPos p : stones) setLit(sl, p, false);
            playback = sl.getGameTime() + 2 - (sl.getGameTime() & 1L);
            playedStep = -1;
        }
        sp.displayClientMessage(Component.translatable(MSG + "hint." + kind.name().toLowerCase(java.util.Locale.ROOT))
                .withColor(0xE0C8FF), true);
    }

    // ------------------------------------------------------------------ resolvido

    /** Rompe o selo: grades somem, peças ficam acesas, quem está perto ganha XP. Também usado pelo comando de teste. */
    public void solve(ServerLevel sl) {
        if (solved()) return;
        if (enigma != null) {
            enigma.forceSolve(sl);
            return;
        }
        if (!scanned) scan(sl);
        sl.setBlock(worldPosition, getBlockState().setValue(PuzzleCoreBlock.SOLVED, true), 3);
        playback = -1;
        flashUntil.clear();
        lastTile.clear();
        for (BlockPos p : lamps) setLit(sl, p, true);
        for (BlockPos p : tiles) setLit(sl, p, true);
        for (BlockPos p : stones) setLit(sl, p, true);
        for (BlockPos g : gates) {
            if (!sl.getBlockState(g).is(PuzzleBlocks.GATE.get())) continue;
            sl.removeBlock(g, false);
            sl.sendParticles(ParticleTypes.POOF, g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.01);
        }
        BlockPos c = worldPosition;
        sl.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 0.8, c.getZ() + 0.5, 30, 0.5, 0.6, 0.5, 0.05);
        sound(sl, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.3F);
        if (!gates.isEmpty()) sound(sl, SoundEvents.IRON_DOOR_OPEN, 1.0F, 0.7F);
        for (ServerPlayer sp : sl.players()) {
            if (sp.isSpectator() || sp.distanceToSqr(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5) > (range + 6.0) * (range + 6.0)) continue;
            sp.displayClientMessage(Component.translatable(MSG + "solved").withColor(0xFFE27A), true);
            MagicApi.get(sp).setFlag(FLAG_SOLVED, true);
            MagicApi.addCounter(sp, COUNTER, 1);
            MagicApi.giveXp(sp, XP, "puzzle");
        }
    }
}
