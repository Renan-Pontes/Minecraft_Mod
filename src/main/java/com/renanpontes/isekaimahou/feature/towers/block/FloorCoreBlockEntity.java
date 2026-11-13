package com.renanpontes.isekaimahou.feature.towers.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.towers.TowerAffix;
import com.renanpontes.isekaimahou.feature.towers.TowerData;
import com.renanpontes.isekaimahou.feature.towers.TowerEnigmas;
import com.renanpontes.isekaimahou.feature.towers.TowerFloors;
import com.renanpontes.isekaimahou.feature.towers.TowerRecords;
import com.renanpontes.isekaimahou.feature.towers.TwProgress;
import com.renanpontes.isekaimahou.feature.towers.TwRegistries;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cérebro de um andar da Torre de Desafio. Configurado pelo NBT do molde ({@code Variant}, {@code Floor}, {@code Kind},
 * {@code Waves}, {@code Elite}). Estados: adormecido → (jogador entra na sala) em desafio → vencido.
 * <ul>
 *   <li><b>combat</b>: ondas de criaturas da variante/nível; a próxima onda vem quando a anterior morre;</li>
 *   <li><b>puzzle</b>: embaralha as Alavancas Rúnicas da sala; vence com todas acesas;</li>
 *   <li><b>boss</b> (pináculo): invoca o guardião; vence quando ele morre (evento de morte → {@link #onGuardianDefeated}).
 *       Quem chegar ao pináculo sem vencer os andares é repelido para a entrada.</li>
 * </ul>
 * <b>Afixos</b> (v1.0): andares de combate acima do térreo sorteiam modificadores ({@link TowerAffix}) ao despertar; o
 * tempo do andar (do despertar à vitória) vira recorde em {@link TowerRecords}.
 * <p>Ao vencer: some com os Selos da sala (abre a escada / o cofre), XP e contadores {@code towers.floor}/{@code towers.cleared}.
 * Se todos saírem de um andar em desafio por 1 minuto, as criaturas somem e o andar volta a dormir. Só varre a sala
 * (19×8×19) ao começar/vencer; o resto do tempo só confere a lista de criaturas a cada 0,5 s.
 * <p><b>Enigmas</b> (v1.0 r9): os andares 2, 4, 5 e 7 ganham Enigmas de raciocínio ({@link TowerEnigmas}), montados ao
 * despertar. Salão das Runas: vence com todos os enigmas resolvidos (o 1º destrava e dá pista do 2º). Tesouro/elite:
 * depois da última onda o enigma desperta, e o andar só é vencido com ele resolvido.
 */
public class FloorCoreBlockEntity extends BlockEntity {
    public static final String MOB_TAG = "isekai_mahou.tower_mob";
    public static final String GUARDIAN_TAG = "isekai_mahou.tower_guardian";
    public static final String CORE_KEY = "isekai_mahou.tower_core";
    private static final int IDLE = 0, ACTIVE = 1, CLEARED = 2;
    private static final int RESET_TICKS = 1200;
    private static final int RADIUS = 9;
    private static final int MAX_MOBS = 10;
    /** Com o afixo Horda o teto de criaturas sobe um pouco. */
    private static final int MAX_MOBS_HORDE = 13;

    private String variant = "stone";
    private int floor;
    private String kind = "combat";
    private int waves = 2;
    private boolean elite;

    private int state = IDLE;
    private int wave;
    private final List<UUID> mobs = new ArrayList<>();
    @Nullable
    private UUID guardian;
    private int awayTicks;
    private int missingTicks;
    private long towerKey = TowerData.NONE;
    private boolean keyResolved;
    private int ticker;
    @Nullable
    private List<BlockPos> switches;
    /** Afixos do desafio em curso (sorteados ao despertar; vazio = nenhum). */
    private final List<TowerAffix> affixes = new ArrayList<>();
    /** Afixos forçados pelo comando de teste para o próximo despertar (null = sortear). */
    @Nullable
    private List<TowerAffix> forcedAffixes;
    /** Tick do mundo em que o desafio começou (0 = sem cronômetro). */
    private long startTime;
    /** v1.0 r9: Selos-Enigma deste andar (vazio = andar sem enigma ou sem espaço). */
    private final List<BlockPos> enigmas = new ArrayList<>();
    private boolean enigmasSet;
    /** Andar de ondas: as ondas acabaram e agora falta o enigma. */
    private boolean enigmaPhase;

    public FloorCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TwRegistries.FLOOR_CORE_BE.get(), pos, state);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState st, FloorCoreBlockEntity be) {
        if (level instanceof ServerLevel sl) be.tick(sl);
    }

    // ------------------------------------------------------------------ Consultas

    public boolean isBoss() {
        return "boss".equals(kind);
    }

    public boolean isPuzzle() {
        return "puzzle".equals(kind);
    }

    public String stateName() {
        return state == CLEARED ? "cleared" : state == ACTIVE ? "active" : "idle";
    }

    public Component floorLabel() {
        return isBoss() ? Component.translatable("tower.isekai_mahou.summit_title") : Component.literal(String.valueOf(floor + 1));
    }

    public int floor() {
        return floor;
    }

    public String variant() {
        return variant;
    }

    /** Andar de ondas (combate, tesouro ou elite): o único tipo que sorteia afixos. */
    public boolean isCombat() {
        return !isBoss() && !isPuzzle();
    }

    public List<TowerAffix> affixes() {
        return List.copyOf(affixes);
    }

    /** Debug: afixos do próximo despertar (lista vazia = nenhum; null = volta a sortear). */
    public void forceAffixes(@Nullable List<TowerAffix> list) {
        forcedAffixes = list == null ? null : new ArrayList<>(list);
    }

    private AABB roomBox() {
        BlockPos p = worldPosition;
        int up = isBoss() ? 6 : 7;
        return new AABB(p.getX() - RADIUS, p.getY() - 1, p.getZ() - RADIUS, p.getX() + RADIUS + 1, p.getY() + up, p.getZ() + RADIUS + 1);
    }

    private List<ServerPlayer> playersInside(ServerLevel sl) {
        return sl.getEntitiesOfClass(ServerPlayer.class, roomBox(), p -> p.isAlive() && !p.isSpectator());
    }

    // ------------------------------------------------------------------ Tick (a cada 10 ticks)

    private void tick(ServerLevel sl) {
        if (++ticker % 10 != 0) return;
        if (!keyResolved) {
            keyResolved = true;
            towerKey = TowerData.keyOf(TowerData.towerAt(sl, worldPosition));
            setChanged();
        }
        if (state == CLEARED) return;
        // No Lar do jogador nada desperta (um Núcleo posto lá no criativo é só enfeite)
        if (IsekaiMahou.MODID.equals(sl.dimension().location().getNamespace())
                && "home_realm".equals(sl.dimension().location().getPath())) return;
        List<ServerPlayer> inside = playersInside(sl);
        if (isBoss() && !inside.isEmpty() && towerKey != TowerData.NONE
                && !TowerData.get(sl).floorsBelowCleared(towerKey, floor)) {
            repel(sl, inside);
            return;
        }
        if (state == IDLE) {
            if (!inside.isEmpty()) activate(sl, inside);
            return;
        }
        if (inside.isEmpty()) {
            if (!isBoss()) {
                awayTicks += 10;
                if (awayTicks >= RESET_TICKS) reset(sl);
            }
            return;
        }
        awayTicks = 0;
        if (isPuzzle()) {
            if (enigmas.isEmpty() ? litCount(sl) >= switches().size() : TowerEnigmas.allSolved(sl, enigmas)) clear(sl);
        } else if (isBoss()) {
            tickGuardian(sl, inside);
        } else {
            pruneMobs(sl);
            if (mobs.isEmpty()) {
                if (wave < waves) spawnWave(sl, inside);
                else if (enigmas.isEmpty() || TowerEnigmas.allSolved(sl, enigmas)) clear(sl);
                else if (!enigmaPhase) {
                    // as ondas acabaram: o enigma do andar desperta
                    enigmaPhase = true;
                    TowerEnigmas.setLocked(sl, enigmas, false);
                    for (ServerPlayer p : inside) {
                        p.sendSystemMessage(Component.translatable("message.isekai_mahou.enigma.tower_waves_done").withStyle(ChatFormatting.AQUA));
                    }
                    setChanged();
                }
            }
        }
        if (state == ACTIVE && ticker % 40 == 0) hud(sl, inside);
    }

    private void activate(ServerLevel sl, List<ServerPlayer> inside) {
        state = ACTIVE;
        wave = 0;
        awayTicks = 0;
        startTime = sl.getGameTime();
        affixes.clear();
        if (isCombat()) {
            affixes.addAll(forcedAffixes != null ? forcedAffixes
                    : TowerAffix.roll(sl.random, TowerAffix.countFor(floor, elite)));
        }
        String sub = isBoss() ? "boss" : isPuzzle() ? "puzzle" : elite ? "elite" : waves == 1 ? "treasure" : "combat";
        Component title = isBoss() ? Component.translatable("tower.isekai_mahou.summit_title")
                : Component.translatable("tower.isekai_mahou.floor_title", floor + 1);
        for (ServerPlayer p : inside) {
            title(p, title.copy().withStyle(ChatFormatting.GOLD), Component.translatable("tower.isekai_mahou.kind." + sub));
        }
        sl.playSound(null, worldPosition, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.2F, 0.8F);
        setupEnigmas(sl);
        enigmaPhase = false;
        if (isCombat() && !enigmas.isEmpty()) TowerEnigmas.setLocked(sl, enigmas, true);
        if (isPuzzle() && !enigmas.isEmpty()) {
            for (ServerPlayer p : inside) {
                p.sendSystemMessage(Component.translatable("message.isekai_mahou.enigma.tower_floor", enigmas.size()).withStyle(ChatFormatting.AQUA));
            }
        } else if (isPuzzle()) {
            scramble(sl);
            for (ServerPlayer p : inside) {
                p.sendSystemMessage(Component.translatable("message.isekai_mahou.towers.puzzle_hint").withStyle(ChatFormatting.AQUA));
            }
        } else if (isBoss()) {
            spawnGuardian(sl, inside);
        } else {
            if (!affixes.isEmpty()) {
                Component line = Component.translatable("tower.isekai_mahou.affixes", TowerAffix.list(affixes)).withStyle(ChatFormatting.GRAY);
                for (ServerPlayer p : inside) {
                    p.sendSystemMessage(line);
                    Hints.show(p, "towers.affixes");
                }
            }
            spawnWave(sl, inside);
        }
        setChanged();
    }

    /** v1.0 r9: monta os enigmas do andar uma única vez (as posições ficam salvas). */
    private void setupEnigmas(ServerLevel sl) {
        if (enigmasSet || isBoss() || TowerEnigmas.tiers(floor).length == 0) return;
        enigmasSet = true;
        enigmas.clear();
        enigmas.addAll(TowerEnigmas.setup(sl, worldPosition, floor, towerKey, isCombat()));
        if (isPuzzle() && !enigmas.isEmpty()) TowerEnigmas.retireRuneHall(sl, worldPosition, switches());
        setChanged();
    }

    /** Ninguém ficou: as criaturas somem e o andar volta a dormir (as runas continuam como estão). */
    private void reset(ServerLevel sl) {
        for (UUID id : mobs) {
            Entity e = sl.getEntity(id);
            if (e != null) {
                sl.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
                e.discard();
            }
        }
        mobs.clear();
        state = IDLE;
        wave = 0;
        awayTicks = 0;
        startTime = 0;
        affixes.clear();
        enigmaPhase = false;
        setChanged();
    }

    // ------------------------------------------------------------------ Ondas

    private void spawnWave(ServerLevel sl, List<ServerPlayer> inside) {
        wave++;
        int tier = Math.min(3, TowerFloors.tier(floor) + (elite && wave == waves ? 1 : 0));
        boolean horde = affixes.contains(TowerAffix.HORDE);
        int count = Math.min(horde ? MAX_MOBS_HORDE : MAX_MOBS,
                2 + floor / 2 + (wave - 1) + (elite ? 1 : 0) + Math.max(0, inside.size() - 1) * 2 + (horde ? 2 : 0));
        boolean champion = affixes.contains(TowerAffix.CHAMPION);
        for (int i = 0; i < count; i++) {
            EntityType<?> type = TowerFloors.pickMob(variant, tier, sl.random);
            BlockPos spot = findSpot(sl, type, inside);
            if (spot == null) continue;
            Entity e = type.spawn(sl, spot, MobSpawnType.MOB_SUMMONED);
            if (e == null) continue;
            e.addTag(MOB_TAG);
            if (e instanceof Mob m) {
                m.setPersistenceRequired();
                buff(m);
                for (TowerAffix a : affixes) a.apply(m);
                if (champion) {
                    // Um campeão por onda: o primeiro que nascer
                    TowerAffix.makeChampion(m);
                    champion = false;
                }
                ServerPlayer near = nearest(inside, e);
                if (near != null && !near.isCreative()) m.setTarget(near);
            }
            mobs.add(e.getUUID());
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 0.8, e.getZ(), 24, 0.4, 0.8, 0.4, 0.05);
        }
        sl.playSound(null, worldPosition, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0F, 0.8F);
        if (mobs.isEmpty() && wave >= waves) clear(sl);
        setChanged();
    }

    /** Andares altos: criaturas com mais vida/dano (e mais ainda no Salão da Elite). */
    private void buff(Mob m) {
        double hp = 0.12 * floor + (elite ? 0.3 : 0.0);
        AttributeInstance health = m.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && hp > 0 && health.getModifier(IsekaiMahou.id("tower_floor_health")) == null) {
            health.addPermanentModifier(new AttributeModifier(IsekaiMahou.id("tower_floor_health"), hp, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            m.setHealth(m.getMaxHealth());
        }
        AttributeInstance dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && floor > 0 && dmg.getModifier(IsekaiMahou.id("tower_floor_damage")) == null) {
            dmg.addPermanentModifier(new AttributeModifier(IsekaiMahou.id("tower_floor_damage"), 0.06 * floor,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    @Nullable
    private BlockPos findSpot(ServerLevel sl, EntityType<?> type, List<ServerPlayer> inside) {
        for (int tries = 0; tries < 20; tries++) {
            BlockPos p = worldPosition.offset(sl.random.nextInt(17) - 8, 0, sl.random.nextInt(17) - 8);
            if (!sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)) continue;
            if (!sl.noCollision(type.getSpawnAABB(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))) continue;
            boolean close = false;
            for (ServerPlayer pl : inside) {
                if (pl.distanceToSqr(Vec3.atBottomCenterOf(p)) < 9.0) close = true;
            }
            if (!close) return p;
        }
        return null;
    }

    @Nullable
    private static ServerPlayer nearest(List<ServerPlayer> players, Entity e) {
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double d = p.distanceToSqr(e);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** Tira da lista quem morreu/sumiu; quem escapou da sala (caiu pelo buraco, voou) volta para o centro. */
    private void pruneMobs(ServerLevel sl) {
        Vec3 c = Vec3.atBottomCenterOf(worldPosition.above());
        mobs.removeIf(id -> {
            Entity e = sl.getEntity(id);
            if (e == null || !e.isAlive()) return true;
            if (Math.abs(e.getY() - worldPosition.getY()) > 8 || e.distanceToSqr(c.x, e.getY(), c.z) > 14 * 14) {
                e.teleportTo(c.x, c.y, c.z);
            }
            return false;
        });
    }

    // ------------------------------------------------------------------ Runas

    private List<BlockPos> switches() {
        if (switches == null) {
            switches = new ArrayList<>();
            if (level instanceof ServerLevel sl) {
                for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-RADIUS - 1, 0, -RADIUS - 1),
                        worldPosition.offset(RADIUS + 1, 6, RADIUS + 1))) {
                    if (sl.getBlockState(p).getBlock() instanceof RuneSwitchBlock) switches.add(p.immutable());
                }
            }
        }
        return switches;
    }

    private int litCount(ServerLevel sl) {
        int n = 0;
        for (BlockPos p : switches()) {
            if (RuneSwitchBlock.isLit(sl.getBlockState(p))) n++;
        }
        return n;
    }

    /** Parte de "tudo aceso" e aperta algumas runas ao acaso: sempre tem solução. */
    private void scramble(ServerLevel sl) {
        List<BlockPos> list = switches();
        if (list.isEmpty()) return;
        for (BlockPos p : list) {
            BlockState st = sl.getBlockState(p);
            if (st.getBlock() instanceof RuneSwitchBlock && !st.getValue(RuneSwitchBlock.LIT)) {
                sl.setBlock(p, st.setValue(RuneSwitchBlock.LIT, true), Block.UPDATE_ALL);
            }
        }
        int presses = 2 + sl.random.nextInt(Math.max(1, list.size() - 1));
        for (int i = 0; i < presses || litCount(sl) >= list.size(); i++) {
            RuneSwitchBlock.press(sl, list.get(sl.random.nextInt(list.size())), false);
            if (i > 20) break;
        }
    }

    // ------------------------------------------------------------------ Guardião

    private void spawnGuardian(ServerLevel sl, List<ServerPlayer> inside) {
        missingTicks = 0;
        EntityType<?> type = TowerFloors.pickGuardian(variant, sl.random);
        Entity e = type.spawn(sl, worldPosition.above(), MobSpawnType.MOB_SUMMONED);
        if (e == null && type != TwRegistries.KOBOLD_LORD.get()) e = TwRegistries.KOBOLD_LORD.get().spawn(sl, worldPosition.above(), MobSpawnType.MOB_SUMMONED);
        if (e == null) return;
        e.addTag(GUARDIAN_TAG);
        e.getPersistentData().putLong(CORE_KEY, worldPosition.asLong());
        if (e instanceof Mob m) m.setPersistenceRequired();
        BossKit.setArena(e, Vec3.atBottomCenterOf(worldPosition.above()), 24.0);
        guardian = e.getUUID();
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
        sl.playSound(null, worldPosition, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 1.2F);
        Component name = e.getDisplayName().copy().withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : inside) p.sendSystemMessage(Component.translatable("tower.isekai_mahou.guardian", name).withStyle(ChatFormatting.RED));
        setChanged();
    }

    private void tickGuardian(ServerLevel sl, List<ServerPlayer> inside) {
        if (guardian == null) {
            spawnGuardian(sl, inside);
            return;
        }
        Entity g = sl.getEntity(guardian);
        if (g == null || g.isRemoved()) {
            // Sumiu sem morrer (descarregado, removido por comando): depois de 10 s com gente no pináculo, chama outro.
            missingTicks += 10;
            if (missingTicks >= 200) {
                guardian = null;
                setChanged();
            }
            return;
        }
        missingTicks = 0;
        if (g.getY() < worldPosition.getY() - 4) {
            Vec3 c = Vec3.atBottomCenterOf(worldPosition.above());
            g.teleportTo(c.x, c.y, c.z);
        }
    }

    /** Chamado pelo evento de morte do guardião. */
    public void onGuardianDefeated(ServerLevel sl) {
        if (state != CLEARED) clear(sl);
    }

    /** Chegou ao pináculo sem vencer os andares (voando, escalando): de volta à entrada. */
    private void repel(ServerLevel sl, List<ServerPlayer> inside) {
        BlockPos entrance = worldPosition.below(8 * floor).above();
        for (ServerPlayer p : inside) {
            if (p.isCreative()) continue;
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.1);
            p.teleportTo(sl, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5, p.getYRot(), p.getXRot());
            p.displayClientMessage(Component.translatable("message.isekai_mahou.towers.roof_denied").withStyle(ChatFormatting.RED), true);
            sl.playSound(null, entrance, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    // ------------------------------------------------------------------ Vitória

    /** Vence o andar: tira os Selos, marca o progresso e recompensa quem está perto. */
    public void clear(ServerLevel sl) {
        clear(sl, false);
    }

    /** {@code forced} = vencido pelo comando de teste: não vale recorde nem bônus de afixo. */
    public void clear(ServerLevel sl, boolean forced) {
        if (state == CLEARED) return;
        // Cronômetro: só vale para um desafio que realmente começou e não foi vencido à força
        int elapsed = !forced && state == ACTIVE && startTime > 0 ? (int) Math.min(Integer.MAX_VALUE, sl.getGameTime() - startTime) : -1;
        List<TowerAffix> beaten = forced ? List.of() : List.copyOf(affixes);
        startTime = 0;
        affixes.clear();
        state = CLEARED;
        mobs.clear();
        guardian = null;
        BlockState st = getBlockState();
        if (st.hasProperty(FloorCoreBlock.CLEARED)) sl.setBlock(worldPosition, st.setValue(FloorCoreBlock.CLEARED, true), Block.UPDATE_ALL);
        removeSeals(sl);
        // enigmas que sobraram (andar vencido à força) continuam resolvíveis
        if (!enigmas.isEmpty()) TowerEnigmas.setLocked(sl, enigmas, false);
        TowerData data = TowerData.get(sl);
        if (!isBoss()) data.markFloor(towerKey, floor);
        else data.setConquered(towerKey);

        List<ServerPlayer> near = sl.getEntitiesOfClass(ServerPlayer.class, roomBox().inflate(6), p -> !p.isSpectator());
        // Com alguém no criativo o tempo não conta (seria recorde de graça)
        boolean timed = elapsed > 0 && !near.isEmpty() && near.stream().noneMatch(ServerPlayer::isCreative);
        // Recorde do mundo ANTES de registrar: o grupo inteiro bate o recorde junto (não só o primeiro da lista)
        int prevWorld = timed ? TowerRecords.best(sl.getServer(), variant, floor) : -1;
        boolean beatsWorld = timed && (prevWorld < 0 || elapsed < prevWorld);
        Component worldRecord = null;
        for (ServerPlayer p : near) {
            Component timeLine = null;
            if (timed) {
                TowerRecords.Result r = TowerRecords.get(sl.getServer()).submit(variant, floor, p.getUUID(), p.getGameProfile().getName(),
                        elapsed, beaten.size());
                String time = TowerRecords.format(elapsed);
                if (beatsWorld && r.personalBest()) {
                    timeLine = Component.translatable("tower.isekai_mahou.time.world", time).withStyle(ChatFormatting.GOLD);
                    MagicApi.addCounter(p, TowerRecords.COUNTER_RECORDS, 1);
                    if (prevWorld >= 0) {
                        MagicApi.addCounter(p, TowerRecords.COUNTER_WORLD_RECORDS, 1);
                        if (worldRecord == null) {
                            worldRecord = Component.translatable("tower.isekai_mahou.time.broadcast", p.getDisplayName(), floorLabel(),
                                    Component.translatable("tower.isekai_mahou.variant." + variant), time).withStyle(ChatFormatting.GOLD);
                        }
                    }
                } else if (r.personalBest()) {
                    timeLine = Component.translatable("tower.isekai_mahou.time.personal", time).withStyle(ChatFormatting.AQUA);
                    MagicApi.addCounter(p, TowerRecords.COUNTER_RECORDS, 1);
                } else {
                    timeLine = Component.translatable("tower.isekai_mahou.time.normal", time, TowerRecords.format(r.previousPersonal()));
                }
            }
            if (!beaten.isEmpty()) {
                MagicApi.addCounter(p, TowerRecords.COUNTER_AFFIX_FLOOR, 1);
                MagicApi.addCounter(p, TowerRecords.COUNTER_AFFIXES, beaten.size());
            }
            if (isBoss()) {
                MagicApi.addCounter(p, TwProgress.COUNTER_CLEARED, 1);
                MagicApi.giveXp(p, 800, "towers");
                title(p, Component.translatable("tower.isekai_mahou.conquered_title").withStyle(ChatFormatting.GOLD),
                        Component.translatable("tower.isekai_mahou.conquered_sub"));
                if (timeLine != null) p.sendSystemMessage(Component.translatable("tower.isekai_mahou.time.summit", timeLine));
            } else {
                MagicApi.addCounter(p, TwProgress.COUNTER_FLOOR, 1);
                MagicApi.giveXp(p, Math.round((60 + 30 * floor) * (1F + TowerAffix.XP_BONUS * beaten.size())), "towers");
                if (isPuzzle()) TwProgress.flag(p, TwProgress.FLAG_PUZZLE);
                // Uma linha só: o tempo (se valeu) no lugar do aviso da passagem
                title(p, Component.translatable("tower.isekai_mahou.cleared_title", floor + 1).withStyle(ChatFormatting.GREEN),
                        timeLine != null ? timeLine : Component.translatable("tower.isekai_mahou.cleared_sub"));
            }
        }
        if (worldRecord != null) sl.getServer().getPlayerList().broadcastSystemMessage(worldRecord, false);
        Vec3 c = Vec3.atCenterOf(worldPosition.above());
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 60, 1.0, 1.0, 1.0, 0.4);
        if (isBoss()) {
            sl.sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 2, c.z, 120, 3.0, 2.0, 3.0, 0.15);
            sl.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        sl.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.4F, 1.2F);
        sl.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.9F);
        setChanged();
    }

    private void removeSeals(ServerLevel sl) {
        int y0 = isBoss() ? -1 : 0;
        int y1 = isBoss() ? 4 : 8;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-RADIUS - 1, y0, -RADIUS - 1),
                worldPosition.offset(RADIUS + 1, y1, RADIUS + 1))) {
            if (sl.getBlockState(p).is(TwRegistries.SEAL.get())) {
                sl.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                sl.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
            }
        }
        sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 0.7F);
    }

    // ------------------------------------------------------------------ HUD

    private void hud(ServerLevel sl, List<ServerPlayer> inside) {
        Component msg;
        if (!enigmas.isEmpty() && (isPuzzle() || enigmaPhase)) {
            msg = Component.translatable("tower.isekai_mahou.hud.enigma", floor + 1, TowerEnigmas.solvedCount(sl, enigmas), enigmas.size());
        } else if (isPuzzle()) {
            msg = Component.translatable("tower.isekai_mahou.hud.puzzle", floor + 1, litCount(sl), switches().size());
        } else if (isBoss()) {
            Entity g = guardian == null ? null : sl.getEntity(guardian);
            if (g == null) return;
            msg = Component.translatable("tower.isekai_mahou.hud.boss", g.getDisplayName());
        } else {
            msg = Component.translatable("tower.isekai_mahou.hud.wave", floor + 1, wave, waves, mobs.size());
        }
        MutableComponent line = msg.copy().withStyle(ChatFormatting.YELLOW);
        if (startTime > 0) {
            // Cronômetro discreto no fim da mesma linha (sem décimos: a barra atualiza a cada 2 s)
            int secs = (int) Math.max(0, (sl.getGameTime() - startTime) / 20);
            line.append(Component.literal(String.format(java.util.Locale.ROOT, "  %d:%02d", secs / 60, secs % 60)).withStyle(ChatFormatting.GRAY));
        }
        for (ServerPlayer p : inside) p.displayClientMessage(line, true);
    }

    private static void title(ServerPlayer p, Component title, Component sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    // ------------------------------------------------------------------ Teste

    /** Debug: estrutura de torre deste núcleo (pode ser inválida). */
    public StructureStart tower(ServerLevel sl) {
        return TowerData.towerAt(sl, worldPosition);
    }

    // ------------------------------------------------------------------ Salvar

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Variant")) variant = tag.getString("Variant");
        floor = tag.getInt("Floor");
        if (tag.contains("Kind")) kind = tag.getString("Kind");
        waves = tag.contains("Waves") ? tag.getInt("Waves") : 2;
        elite = tag.getBoolean("Elite");
        state = tag.getInt("State");
        wave = tag.getInt("Wave");
        mobs.clear();
        for (Tag t : tag.getList("Mobs", Tag.TAG_INT_ARRAY)) mobs.add(NbtUtils.loadUUID(t));
        guardian = tag.hasUUID("Guardian") ? tag.getUUID("Guardian") : null;
        keyResolved = tag.getBoolean("KeyResolved");
        towerKey = tag.contains("TowerKey") ? tag.getLong("TowerKey") : TowerData.NONE;
        // v1.0 (opcionais: moldes e mundos antigos não têm)
        startTime = tag.getLong("StartTime");
        enigmas.clear();
        for (long l : tag.getLongArray("Enigmas")) enigmas.add(BlockPos.of(l));
        enigmasSet = tag.getBoolean("EnigmasSet");
        enigmaPhase = tag.getBoolean("EnigmaPhase");
        affixes.clear();
        for (Tag t : tag.getList("Affixes", Tag.TAG_STRING)) {
            TowerAffix a = TowerAffix.byId(t.getAsString());
            if (a != null && !affixes.contains(a)) affixes.add(a);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Variant", variant);
        tag.putInt("Floor", floor);
        tag.putString("Kind", kind);
        tag.putInt("Waves", waves);
        tag.putBoolean("Elite", elite);
        tag.putInt("State", state);
        tag.putInt("Wave", wave);
        ListTag list = new ListTag();
        for (UUID id : mobs) list.add(NbtUtils.createUUID(id));
        tag.put("Mobs", list);
        if (guardian != null) tag.putUUID("Guardian", guardian);
        tag.putBoolean("KeyResolved", keyResolved);
        tag.putLong("TowerKey", towerKey);
        if (startTime > 0) tag.putLong("StartTime", startTime);
        if (enigmasSet) {
            tag.putBoolean("EnigmasSet", true);
            tag.putLongArray("Enigmas", enigmas.stream().mapToLong(BlockPos::asLong).toArray());
            if (enigmaPhase) tag.putBoolean("EnigmaPhase", true);
        }
        if (!affixes.isEmpty()) {
            ListTag al = new ListTag();
            for (TowerAffix a : affixes) al.add(StringTag.valueOf(a.id()));
            tag.put("Affixes", al);
        }
    }
}
