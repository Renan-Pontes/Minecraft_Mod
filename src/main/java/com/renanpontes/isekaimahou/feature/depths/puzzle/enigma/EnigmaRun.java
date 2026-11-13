package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.depths.DepthsDungeon;
import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleCoreBlock;
import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/**
 * Um Enigma em andamento, guardado no Selo Rúnico ({@link PuzzleCoreBlockEntity}, chave NBT {@code Enigma}).
 * <p>NBT do molde/comando: {@code Type} ("lights"… ou "random"), {@code Tier} 1–3, {@code U0} (coluna do Selo na
 * fileira da frente, padrão 4), {@code Slot} (posição na estrutura: escolhe o tipo sem repetir), {@code Chain} +
 * {@code Role} (1 = primeiro, 2 = segundo: o segundo fica selado até o primeiro ser resolvido, e o primeiro, ao ser
 * resolvido, dá uma pista grátis do segundo), {@code Locked} (trava externa, ex.: andar de torre antes das ondas).
 * <p>Controles: tocar no Selo = regra + informações; agachado + tocar = comprar pista (níveis 2–3 custam níveis de
 * experiência); botão em cima do Selo = recomeçar (sempre, ninguém fica preso).
 */
public final class EnigmaRun {
    public static final String MSG = "message.isekai_mahou.enigma.";
    public static final String NAME = "enigma.isekai_mahou.";
    public static final String COUNTER = "puzzle.enigma";
    public static final String MOB_TAG = "isekai_mahou.enigma_mob";
    private static final int[] XP = {0, 150, 320, 650};
    private static final int[] HINT_COST = {0, 0, 3, 5};

    private final PuzzleCoreBlockEntity core;
    @Nullable
    private EnigmaType type;
    private int tier = 1;
    private long seed;
    private boolean seeded;
    private int u0 = 4;
    private int slot = -1;
    private String chain = "";
    private int role;
    private boolean extLock;
    private boolean built;
    private boolean clued;
    private int hints;
    @Nullable
    private Enigma impl;
    private CompoundTag implState = new CompoundTag();
    private long cooldownUntil;
    private boolean chainLocked;
    private int lockCheck;
    private final Map<UUID, Long> lastMsg = new HashMap<>();

    public EnigmaRun(PuzzleCoreBlockEntity core) {
        this.core = core;
    }

    // ------------------------------------------------------------------ criação / NBT

    /** Configura (comando de teste, torres). {@code type} null = sorteado. */
    public void configure(@Nullable EnigmaType type, int tier, long seed, int u0, String chain, int role, boolean locked) {
        this.type = type;
        this.tier = Math.max(1, Math.min(3, tier));
        this.seed = seed;
        this.seeded = seed != 0;
        this.u0 = u0;
        this.chain = chain == null ? "" : chain;
        this.role = role;
        this.extLock = locked;
        this.built = false;
        this.impl = null;
        this.implState = new CompoundTag();
    }

    public static EnigmaRun load(PuzzleCoreBlockEntity core, Tag raw) {
        EnigmaRun r = new EnigmaRun(core);
        if (!(raw instanceof CompoundTag t)) {
            // forma curta: "Enigma": "lights"
            r.type = EnigmaType.byId(raw.getAsString());
            return r;
        }
        r.type = EnigmaType.byId(t.getString("Type"));
        r.tier = Math.max(1, Math.min(3, t.contains("Tier") ? t.getInt("Tier") : 1));
        r.seeded = t.contains("Seed");
        r.seed = t.getLong("Seed");
        r.u0 = t.contains("U0") ? t.getInt("U0") : 4;
        r.slot = t.contains("Slot") ? t.getInt("Slot") : -1;
        r.chain = t.getString("Chain");
        r.role = t.getInt("Role");
        r.extLock = t.getBoolean("Locked");
        r.built = t.getBoolean("Built");
        r.clued = t.getBoolean("Clued");
        r.hints = t.getInt("Hints");
        r.implState = t.getCompound("State");
        return r;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Type", type == null ? "random" : type.id());
        t.putInt("Tier", tier);
        if (seeded) t.putLong("Seed", seed);
        t.putInt("U0", u0);
        if (slot >= 0) t.putInt("Slot", slot);
        if (!chain.isEmpty()) {
            t.putString("Chain", chain);
            t.putInt("Role", role);
        }
        if (extLock) t.putBoolean("Locked", true);
        t.putBoolean("Built", built);
        if (clued) t.putBoolean("Clued", true);
        if (hints > 0) t.putInt("Hints", hints);
        CompoundTag st = new CompoundTag();
        if (impl != null) impl.save(st);
        else st = implState;
        t.put("State", st);
        return t;
    }

    // ------------------------------------------------------------------ consultas

    public ServerLevel level() {
        return (ServerLevel) core.getLevel();
    }

    public Frame frame() {
        BlockState s = core.getBlockState();
        Direction f = s.hasProperty(PuzzleCoreBlock.FACING) ? s.getValue(PuzzleCoreBlock.FACING) : Direction.NORTH;
        return new Frame(core.getBlockPos(), f, u0);
    }

    @Nullable
    public EnigmaType type() {
        return type;
    }

    public int tier() {
        return tier;
    }

    public long seed() {
        return seed;
    }

    public String chain() {
        return chain;
    }

    public int role() {
        return role;
    }

    public boolean solved() {
        return core.solved();
    }

    public boolean covers(BlockPos p) {
        return frame().covers(p);
    }

    public boolean locked() {
        return extLock || chainLocked;
    }

    public boolean externallyLocked() {
        return extLock;
    }

    /** Trava/destrava de fora (andar de torre: só depois das ondas). */
    public void setExternalLock(boolean lock) {
        if (lock == extLock) return;
        extLock = lock;
        core.setChanged();
        if (!lock && !chainLocked && level() != null) awaken();
    }

    public Component title() {
        Component name = type == null ? Component.translatable(NAME + "name.random") : Component.translatable(NAME + "name." + type.id());
        return Component.translatable(NAME + "title", name, Component.translatable(NAME + "tier." + tier));
    }

    // ------------------------------------------------------------------ ciclo

    /** Chamado pelo tique do Selo (a cada 2 ticks, só no servidor). */
    public void tick(ServerLevel sl, long t) {
        if (solved()) return;
        BlockPos c = core.getBlockPos();
        if (!sl.hasNearbyAlivePlayer(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 24)) return;
        if (!ensure(sl)) return;
        if (++lockCheck >= 10) {
            lockCheck = 0;
            boolean was = chainLocked;
            chainLocked = role == 2 && partner(sl, false) != null;
            if (was && !chainLocked && !extLock) awaken();
        }
        if (!locked()) impl.tick(this, t);
        if (t % 40 == 0) {
            for (ServerPlayer sp : playersNear(0)) Hints.show(sp, "depths_enigma");
        }
    }

    /** Resolve tipo/semente, cria a lógica e monta as peças se ainda não montou. false = chunks ainda carregando. */
    private boolean ensure(ServerLevel sl) {
        if (impl != null && built) return true;
        Frame f = frame();
        if (!sl.isAreaLoaded(f.pos(4, 4, 0), 10)) return false;
        if (!seeded) {
            seed = mix(core.getBlockPos().asLong(), sl.getSeed());
            seeded = true;
        }
        if (type == null) type = pickType(sl);
        if (impl == null) {
            impl = type.create(tier, seed);
            if (built) impl.load(implState);
        }
        if (!built) {
            placeResetButton();
            impl.build(this);
            built = true;
            // quem estava de pé onde nasceu uma peça sólida volta para a fileira da frente (ninguém preso em bloco)
            for (ServerPlayer sp : playersNear(0)) {
                BlockPos feet = sp.blockPosition();
                if (!sl.getBlockState(feet).getCollisionShape(sl, feet).isEmpty()
                        || !sl.getBlockState(feet.above()).getCollisionShape(sl, feet.above()).isEmpty()) sendToStart(sp);
            }
            chainLocked = role == 2 && partner(sl, false) != null;
        }
        core.setChanged();
        return true;
    }

    private static long mix(long a, long b) {
        long x = a * 0x9E3779B97F4A7C15L ^ b;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        return x ^ (x >>> 29);
    }

    /**
     * Tipo sorteado SEM repetir dentro da mesma estrutura: embaralha os 8 tipos com a semente da masmorra e usa o
     * {@code Slot} (posição do Selo na estrutura). Fora de estrutura: pela posição.
     */
    private EnigmaType pickType(ServerLevel sl) {
        EnigmaType[] all = EnigmaType.values();
        long key = core.getBlockPos().asLong();
        int s = slot;
        StructureStart st = DepthsDungeon.startWithPieceAt(sl, core.getBlockPos());
        if (st != null) key = st.getChunkPos().toLong();
        if (s < 0) s = (int) Math.floorMod(mix(core.getBlockPos().asLong(), sl.getSeed()), all.length);
        return order(mix(key, sl.getSeed() ^ 0x5EEDL))[Math.floorMod(s, all.length)];
    }

    /** Os 8 tipos embaralhados por uma semente (usado também pelas torres). */
    public static EnigmaType[] order(long seed) {
        List<EnigmaType> list = new ArrayList<>(List.of(EnigmaType.values()));
        java.util.Collections.shuffle(list, new Random(seed));
        return list.toArray(new EnigmaType[0]);
    }

    /** Outro Selo da mesma corrente (papel oposto), carregado, a até 64 blocos. {@code solved}: só os resolvidos/não. */
    @Nullable
    private PuzzleCoreBlockEntity partner(ServerLevel sl, boolean wantSecond) {
        if (chain.isEmpty()) return null;
        int want = wantSecond ? 2 : 1;
        PuzzleCoreBlockEntity[] found = {null};
        PuzzleCoreBlockEntity.forEachLoaded(sl, be -> {
            EnigmaRun r = be.enigma();
            if (be == core || r == null || r.role != want || !chain.equals(r.chain)) return;
            if (be.getBlockPos().distSqr(core.getBlockPos()) > 64 * 64) return;
            if (!wantSecond && be.solved()) return;      // o primeiro só trava se ainda não foi resolvido
            found[0] = be;
        });
        return found[0];
    }

    private void awaken() {
        ServerLevel sl = level();
        BlockPos c = core.getBlockPos();
        sl.playSound(null, c, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.2F);
        sl.sendParticles(ParticleTypes.ENCHANT, c.getX() + 0.5, c.getY() + 1.2, c.getZ() + 0.5, 40, 0.6, 0.6, 0.6, 0.8);
        for (ServerPlayer sp : playersNear(6)) sp.sendSystemMessage(Component.translatable(MSG + "awake", title()).withColor(0xC8A0FF));
    }

    // ------------------------------------------------------------------ cliques

    /** Clique direito numa posição da área (roteado por {@link EnigmaEvents}). */
    public Enigma.Use onUse(ServerPlayer sp, BlockPos pos) {
        ServerLevel sl = level();
        if (sl == null) return Enigma.Use.PASS;
        BlockPos c = core.getBlockPos();
        if (pos.equals(c)) {
            onCoreClicked(sp);
            return Enigma.Use.CONSUME;
        }
        if (solved() || !ensure(sl)) return Enigma.Use.PASS;
        Frame f = frame();
        if (pos.equals(c.above()) && sl.getBlockState(pos).getBlock() instanceof ButtonBlock) {
            if (locked()) return Enigma.Use.ALLOW;
            impl.reset(this);
            cooldownUntil = 0;
            core.setChanged();
            sl.playSound(null, c, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
            for (ServerPlayer p : playersNear(4)) p.displayClientMessage(Component.translatable(MSG + "reset").withColor(0xC8A070), true);
            return Enigma.Use.ALLOW;
        }
        Enigma.Use r = impl.use(this, sp, pos, f.u(pos), f.v(pos), f.h(pos));
        if (r == Enigma.Use.PASS) return r;
        if (locked()) {
            tell(sp, Component.translatable(MSG + (chainLocked ? "locked_chain" : "locked")).withColor(0x9A8AB0));
            return Enigma.Use.CONSUME;
        }
        core.setChanged();
        return r;
    }

    /** O jogador quer agir agora? (falso durante a pausa depois de um erro; avisa). Use dentro de {@link Enigma#use}. */
    public boolean ready(ServerPlayer sp) {
        if (locked()) return false;
        if (level().getGameTime() < cooldownUntil) {
            tell(sp, Component.translatable(MSG + "cooldown").withColor(0x9A8AB0));
            return false;
        }
        return true;
    }

    public void onCoreClicked(ServerPlayer sp) {
        ServerLevel sl = level();
        if (solved()) {
            sp.displayClientMessage(Component.translatable(MSG + "already").withColor(0x9AF0FF), true);
            return;
        }
        if (!ensure(sl)) return;
        if (sp.isShiftKeyDown()) {
            buyHint(sp);
            return;
        }
        sp.sendSystemMessage(title().copy().withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(Component.translatable(NAME + "hint." + type.id() + "." + tier).withColor(0xE0C8FF));
        impl.describe(this, sp);
        if (locked()) sp.sendSystemMessage(Component.translatable(MSG + (chainLocked ? "locked_chain" : "locked")).withColor(0x9A8AB0));
        else if (tier >= 2) sp.sendSystemMessage(Component.translatable(MSG + "hint_offer", HINT_COST[tier]).withColor(0x8A8AA0));
        else sp.sendSystemMessage(Component.translatable(MSG + "hint_offer_free").withColor(0x8A8AA0));
    }

    private void buyHint(ServerPlayer sp) {
        int cost = sp.getAbilities().instabuild ? 0 : HINT_COST[tier];
        if (cost > 0 && sp.experienceLevel < cost) {
            sp.sendSystemMessage(Component.translatable(MSG + "hint_poor", cost).withColor(0xD06060));
            return;
        }
        if (!impl.reveal(this)) {
            sp.sendSystemMessage(Component.translatable(MSG + "hint_none").withColor(0x9A8AB0));
            return;
        }
        if (cost > 0) sp.giveExperienceLevels(-cost);
        hints++;
        core.setChanged();
        level().playSound(null, core.getBlockPos(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.4F);
    }

    // ------------------------------------------------------------------ resultado

    /** Erro de resposta: dano (nunca mata), e nos níveis 2–3 uma pequena onda de criaturas. Pausa de 3 s. */
    public void punish(@Nullable ServerPlayer sp) {
        ServerLevel sl = level();
        cooldownUntil = sl.getGameTime() + 60;
        BlockPos c = core.getBlockPos();
        sl.playSound(null, c, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
        if (sp != null) {
            float dmg = Math.min(2.0F + 2.0F * tier, sp.getHealth() - 1.0F);
            if (dmg > 0 && !sp.getAbilities().instabuild) sp.hurt(sl.damageSources().magic(), dmg);
            sl.sendParticles(ParticleTypes.SMOKE, sp.getX(), sp.getY() + 1.0, sp.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
        }
        for (ServerPlayer p : playersNear(4)) p.displayClientMessage(Component.translatable(MSG + "wrong").withColor(0xD06060), true);
        if (tier < 2) return;
        Frame f = frame();
        EntityType<?>[] pool = tier == 2
                ? new EntityType<?>[] {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER}
                : new EntityType<?>[] {EntityType.VINDICATOR, EntityType.STRAY, EntityType.ZOMBIE, EntityType.SKELETON};
        int count = tier;
        int[] cols = {8, 0, 7, 2, 6};
        int spawned = 0;
        for (int col : cols) {
            if (spawned >= count) break;
            if (col == u0) continue;
            BlockPos p = f.pos(col, 0, 0);
            if (!sl.getBlockState(p).isAir() || !sl.getBlockState(p.above()).isAir()) continue;
            Entity e = pool[sl.random.nextInt(pool.length)].spawn(sl, p, MobSpawnType.MOB_SUMMONED);
            if (e == null) continue;
            e.addTag(MOB_TAG);
            if (e instanceof Mob m && sp != null && !sp.getAbilities().instabuild) m.setTarget(sp);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 0.8, e.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
            spawned++;
        }
    }

    /** Enigma resolvido: Selo aceso, recompensa (XP + baú) para quem está perto e, se houver, pista do próximo. */
    public void onSolved() {
        ServerLevel sl = level();
        if (sl == null || solved()) return;
        BlockPos c = core.getBlockPos();
        sl.setBlock(c, core.getBlockState().setValue(PuzzleCoreBlock.SOLVED, true), 3);
        cooldownUntil = 0;
        core.setChanged();
        sl.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, 40, 0.6, 0.8, 0.6, 0.06);
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, 30, 0.8, 0.8, 0.8, 0.3);
        sl.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.3F);
        sl.playSound(null, c, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.1F);
        for (ServerPlayer sp : playersNear(6)) {
            if (sp.isSpectator()) continue;
            sp.displayClientMessage(Component.translatable(MSG + "solved", title()).withColor(0xFFE27A), true);
            MagicApi.get(sp).setFlag(PuzzleCoreBlockEntity.FLAG_SOLVED, true);
            MagicApi.addCounter(sp, PuzzleCoreBlockEntity.COUNTER, 1);
            MagicApi.addCounter(sp, COUNTER, 1);
            if (tier >= 3) MagicApi.addCounter(sp, COUNTER + ".hard", 1);
            MagicApi.giveXp(sp, XP[tier], "puzzle");
        }
        rewardChest(sl);
        // corrente: o próximo desperta e ganha uma pista de presente
        PuzzleCoreBlockEntity next = role == 1 ? partner(sl, true) : null;
        if (next != null && next.enigma() != null) next.enigma().giftClue(sl);
    }

    /** Pista grátis vinda do enigma anterior da corrente (uma vez). */
    void giftClue(ServerLevel sl) {
        chainLocked = false;
        if (clued || !ensure(sl)) return;
        clued = true;
        for (ServerPlayer sp : playersNear(24)) sp.sendSystemMessage(Component.translatable(MSG + "clue_gift", title()).withColor(0xC8A0FF));
        impl.reveal(this);
        core.setChanged();
        if (!extLock) awaken();
    }

    private void rewardChest(ServerLevel sl) {
        Frame f = frame();
        String table = tier >= 3 ? "chests/depths_puzzle_deep" : "chests/depths_puzzle";
        for (int col : new int[] {8, 0, 7, 1, 6, 2}) {
            if (col == u0) continue;
            BlockPos p = f.pos(col, 0, 0);
            if (!sl.getBlockState(p).isAir() || !sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)) continue;
            sl.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f.forward().getOpposite()), 3);
            RandomizableContainer.setBlockEntityLootTable(sl, sl.random, p,
                    ResourceKey.create(Registries.LOOT_TABLE, IsekaiMahou.id(table)));
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.0);
            return;
        }
    }

    /** Teste: resolve já (comando). */
    public void forceSolve(ServerLevel sl) {
        if (!ensure(sl)) return;
        chainLocked = false;
        extLock = false;
        onSolved();
    }

    /** Teste/comando: uma pista de graça (como se tivesse comprado). */
    public void giftClueForTest(ServerLevel sl) {
        if (!ensure(sl)) return;
        if (!impl.reveal(this)) broadcast(Component.translatable(MSG + "hint_none").withColor(0x9A8AB0));
        core.setChanged();
    }

    /** Teste/comando: recomeça. */
    public void forceReset(ServerLevel sl) {
        if (!ensure(sl)) return;
        impl.reset(this);
        core.setChanged();
    }

    // ------------------------------------------------------------------ ajudantes para os enigmas

    public BlockPos pos(int u, int v, int h) {
        return frame().pos(u, v, h);
    }

    public void set(int u, int v, int h, BlockState state) {
        level().setBlock(pos(u, v, h), state, Block.UPDATE_ALL);
    }

    public BlockState get(int u, int v, int h) {
        return level().getBlockState(pos(u, v, h));
    }

    /** Bloco de "pedestal" dos enigmas (combina com qualquer masmorra). */
    public static BlockState pedestal() {
        return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    }

    /** Botão no chão (em cima de um bloco), virado para quem está na frente. */
    public BlockState floorButton() {
        return Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR)
                .setValue(ButtonBlock.FACING, frame().forward());
    }

    private void placeResetButton() {
        BlockPos above = core.getBlockPos().above();
        ServerLevel sl = level();
        if (sl.getBlockState(above).isAir()) sl.setBlock(above, floorButton(), Block.UPDATE_ALL);
    }

    /** Placa (de pé, h acima de um pedestal) ou de parede (presa ao bloco à frente), com o texto virado para a entrada. */
    public void sign(int u, int v, int h, boolean wall, Component... lines) {
        Direction face = frame().forward().getOpposite();
        BlockState st = wall ? Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, face)
                : Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(face));
        BlockPos p = pos(u, v, h);
        ServerLevel sl = level();
        sl.setBlock(p, st, Block.UPDATE_ALL);
        if (sl.getBlockEntity(p) instanceof SignBlockEntity sign) {
            SignText text = new SignText().setColor(DyeColor.WHITE).setHasGlowingText(true);
            for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, lines[i]);
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
            sl.sendBlockUpdated(p, st, st, Block.UPDATE_ALL);
        }
    }

    public List<ServerPlayer> playersNear(double margin) {
        AABB box = frame().aabb().inflate(margin, 2, margin);
        return level().getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator());
    }

    /** Mensagem na barra de ação, no máximo uma a cada 0,75 s por jogador (cliques repetidos não fazem spam). */
    public void tell(ServerPlayer sp, Component msg) {
        long now = level().getGameTime();
        Long last = lastMsg.get(sp.getUUID());
        if (last != null && now - last < 15) return;
        lastMsg.put(sp.getUUID(), now);
        sp.displayClientMessage(msg, true);
    }

    /** Mensagem no chat para todos perto (pistas; alcance largo para o grupo inteiro ver). */
    public void broadcast(Component msg) {
        for (ServerPlayer sp : playersNear(24)) sp.sendSystemMessage(msg);
    }

    public void sound(BlockPos p, SoundEvent s, float vol, float pitch) {
        level().playSound(null, p, s, SoundSource.BLOCKS, vol, pitch);
    }

    public void particles(BlockPos p, ParticleOptions type, int n, double spread) {
        level().sendParticles(type, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, n, spread, spread, spread, 0.02);
    }

    /** Casa (u, v) do chão em que o jogador está pisando, na altura do enigma; null se no ar ou em outra altura. */
    @Nullable
    public int[] feetCell(ServerPlayer sp) {
        Frame f = frame();
        double y0 = f.core().getY();
        if (!sp.onGround() || sp.getY() < y0 - 0.2 || sp.getY() > y0 + 0.7) return null;
        BlockPos p = BlockPos.containing(sp.getX(), y0, sp.getZ());
        return new int[] {f.u(p), f.v(p)};
    }

    /** Leva o jogador de volta para a fileira da frente (entrada do enigma). */
    public void sendToStart(ServerPlayer sp) {
        Frame f = frame();
        int col = u0 == 4 ? 3 : 4;
        BlockPos p = f.pos(col, 0, 0);
        level().sendParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY() + 1, sp.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
        sp.teleportTo(level(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5, f.forward().toYRot(), sp.getXRot());
        sp.resetFallDistance();
    }

    public static MutableComponent lit(String s) {
        return Component.literal(s);
    }
}
