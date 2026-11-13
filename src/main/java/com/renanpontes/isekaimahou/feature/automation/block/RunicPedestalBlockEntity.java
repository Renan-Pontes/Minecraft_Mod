package com.renanpontes.isekaimahou.feature.automation.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.AutoRegistries;
import com.renanpontes.isekaimahou.feature.automation.ManaNet;
import com.renanpontes.isekaimahou.feature.automation.PedestalCondition;
import com.renanpontes.isekaimahou.feature.automation.Pedestals;
import com.renanpontes.isekaimahou.feature.automation.WirelessMana;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Pedestal Rúnico: guarda um Pergaminho de Magia e conjura a receita sozinho a cada N segundos, a partir do topo
 * (mais o deslocamento escolhido), na direção escolhida. Quem conjura é um FakePlayer do dono (abates contam como
 * de jogador → drops e XP como numa fazenda). Paga o custo da magia com Baterias de Mana a até 8 blocos.
 * <p>v1.5: botão LIGA/DESLIGA, espaço de FILTRO (lido pelos glifos via {@link Pedestals#currentFilter()}), ligação sem fio
 * com um Núcleo de Mana ({@link WirelessMana}, paga dele primeiro; sem núcleo/sem mana nele, usa as baterias) e
 * gatilhos de pedestal ({@link PedestalCondition}: conferidos antes de gastar mana).
 */
public class RunicPedestalBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int MIN_INTERVAL = 1, MAX_INTERVAL = 60, MAX_OFFSET = 8;
    /** Altura (a partir da base do bloco) de onde a magia nasce: um pouco acima do tampo. */
    public static final double CAST_HEIGHT = 1.25;
    private static final int[] NO_SLOTS = new int[0];
    /** Espaços: 0 = Pergaminho de Magia, 1 = filtro (v1.5). */
    public static final int SLOT_SCROLL = 0, SLOT_FILTER = 1, SLOTS = 2;
    /** Estado da ligação com o Núcleo (D_CORE). */
    public static final int CORE_NONE = 0, CORE_OK = 1, CORE_UNLOADED = 2, CORE_MISSING = 3, CORE_FAR = 4;

    // Índices do ContainerData (tela)
    public static final int D_INTERVAL = 0, D_DIRECTION = 1, D_DX = 2, D_DY = 3, D_DZ = 4, D_REDSTONE = 5, D_STATUS = 6, D_COST = 7,
            D_MANA_LO = 8, D_MANA_HI = 9, D_BATTERIES = 10, D_TIMER = 11, D_ELEMENT = 12, D_CASTS_LO = 13, D_CASTS_HI = 14,
            D_POWERED = 15, D_ENABLED = 16, D_CORE = 17, D_CORE_LOSS = 18, D_CORE_DIST = 19, D_COUNT = 20;

    // Botões do menu (ServerboundContainerButtonClickPacket)
    public static final int B_INTERVAL_DOWN = 0, B_INTERVAL_UP = 1, B_INTERVAL_DOWN10 = 2, B_INTERVAL_UP10 = 3, B_DIRECTION_NEXT = 4,
            B_DIRECTION_PREV = 5, B_DX_DOWN = 6, B_DX_UP = 7, B_DY_DOWN = 8, B_DY_UP = 9, B_DZ_DOWN = 10, B_DZ_UP = 11,
            B_REDSTONE_NEXT = 12, B_REDSTONE_PREV = 13, B_RESET_OFFSET = 14, B_TEST = 15, B_TOGGLE = 16;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    @Nullable private UUID owner;
    private String ownerName = "";
    private int interval = 5;
    private PedestalDirection direction = PedestalDirection.DOWN;
    private int dx, dy, dz;
    private RedstoneMode redstone = RedstoneMode.ALWAYS;
    private int timer = 20;
    private boolean powered;
    private boolean pulsePending;
    private int totalCasts;
    /** v1.5: ligado/desligado (saves antigos: ligado). */
    private boolean enabled = true;
    /** v1.5: Núcleo de Mana ligado pelo Condutor (mesmo mundo). */
    @Nullable private BlockPos corePos;

    // Estado calculado (não salvo)
    private PedestalStatus status = PedestalStatus.NO_SCROLL;
    private int cost;
    private int mana;
    private int batteries;
    private int coreState = CORE_NONE;
    private int coreLoss;
    /** O gatilho de pedestal disse "ainda não" na última tentativa (zera ao conjurar ou ao mexer). */
    private boolean waitingCondition;
    private Element element = Element.ARCANE;
    @Nullable private SpellRecipe cachedRecipe;
    @Nullable private SpellParser.Result cachedParse;
    /** Receita que deu erro de código ao conjurar: o pedestal para (sem derrubar o servidor) até trocar o pergaminho. */
    @Nullable private SpellRecipe brokenRecipe;

    // Cliente: brilho depois de conjurar
    private int flashTicks;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case D_INTERVAL -> interval;
                case D_DIRECTION -> direction.ordinal();
                case D_DX -> dx + MAX_OFFSET;
                case D_DY -> dy + MAX_OFFSET;
                case D_DZ -> dz + MAX_OFFSET;
                case D_REDSTONE -> redstone.ordinal();
                case D_STATUS -> status.ordinal();
                case D_COST -> cost;
                case D_MANA_LO -> mana & 0xFFFF;
                case D_MANA_HI -> (mana >>> 16) & 0xFFFF;
                case D_BATTERIES -> batteries;
                case D_TIMER -> Math.max(0, timer);
                case D_ELEMENT -> element.ordinal();
                case D_CASTS_LO -> totalCasts & 0xFFFF;
                case D_CASTS_HI -> (totalCasts >>> 16) & 0xFFFF;
                case D_POWERED -> powered ? 1 : 0;
                case D_ENABLED -> enabled ? 1 : 0;
                case D_CORE -> coreState;
                case D_CORE_LOSS -> coreLoss;
                case D_CORE_DIST -> corePos == null ? 0 : (int) Math.sqrt(corePos.distSqr(worldPosition));
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
            // Só o servidor muda o estado (botões chegam por clickMenuButton)
        }

        @Override
        public int getCount() {
            return D_COUNT;
        }
    };

    public RunicPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(AutoRegistries.RUNIC_PEDESTAL_BE.get(), pos, state);
    }

    public ContainerData data() { return data; }
    @Nullable public UUID owner() { return owner; }
    /** v1.5: item do espaço de filtro (semente escolhida, bloco a guardar...). Vazio = sem filtro. */
    public ItemStack filter() { return items.get(SLOT_FILTER); }
    public boolean enabled() { return enabled; }
    @Nullable public BlockPos corePos() { return corePos; }
    public String ownerName() { return ownerName; }
    public ItemStack scroll() { return items.get(SLOT_SCROLL); }
    public Element element() { return element; }
    public PedestalStatus status() { return status; }

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    /** Pode mexer: o dono, quem está no criativo, operadores, ou qualquer um se o pedestal não tem dono. */
    public boolean mayConfigure(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.getAbilities().instabuild || player.hasPermissions(2);
    }

    /** v1.5: liga/desliga (botão da tela ou Condutor de Mana agachado). */
    public void setEnabled(boolean on) {
        if (enabled == on) return;
        enabled = on;
        waitingCondition = false;
        if (on && timer > 20) timer = 20; // ligou: volta a trabalhar logo
        if (level instanceof ServerLevel server) evaluate(server);
        setChanged();
    }

    /** v1.5: liga a um Núcleo de Mana (null = desfaz a ligação). Quem chama confere alcance e mundo. */
    public void setCorePos(@Nullable BlockPos pos) {
        corePos = pos == null ? null : pos.immutable();
        if (level instanceof ServerLevel server) evaluate(server);
        setChanged();
    }

    /** Ponto de onde a magia nasce (topo + deslocamento). */
    public Vec3 castPoint() {
        return new Vec3(worldPosition.getX() + 0.5 + dx, worldPosition.getY() + CAST_HEIGHT + dy, worldPosition.getZ() + 0.5 + dz);
    }

    // =====================================================================================
    //  Tick (servidor)
    // =====================================================================================
    public static void serverTick(Level level, BlockPos pos, BlockState state, RunicPedestalBlockEntity be) {
        if (level instanceof ServerLevel server) be.tick(server, state);
    }

    private void tick(ServerLevel level, BlockState state) {
        if (timer > 0) timer--;
        boolean wantCast = timer <= 0 && redstoneAllows();
        if (wantCast || (level.getGameTime() + worldPosition.asLong()) % 10 == 0) evaluate(level);
        if (wantCast) {
            if (status == PedestalStatus.ACTIVE || status == PedestalStatus.WAITING_CONDITION) {
                // v1.5: gatilho de pedestal ("Ao Amadurecer"...) conferido ANTES de gastar mana
                if (conditionReady(level)) {
                    waitingCondition = false;
                    cast(level);
                } else if (status != PedestalStatus.ERROR) {
                    waitingCondition = true;
                    status = PedestalStatus.WAITING_CONDITION;
                }
                timer = redstone == RedstoneMode.PULSE ? 10 : interval * 20;
            } else {
                timer = 20; // tenta de novo daqui a 1 s
            }
        }
        boolean active = status.running;
        if (state.getValue(RunicPedestalBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(RunicPedestalBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private boolean redstoneAllows() {
        return switch (redstone) {
            case ALWAYS -> true;
            case WITH_SIGNAL -> powered;
            case WITHOUT_SIGNAL -> !powered;
            case PULSE -> pulsePending;
        };
    }

    /**
     * Recalcula o estado (pergaminho, receita, dono, limite do chunk, redstone, baterias). O sinal de redstone é
     * relido aqui (só no tick, com os vizinhos carregados); a SUBIDA para o modo Pulso vem do neighborChanged.
     */
    private void evaluate(ServerLevel level) {
        powered = level.hasNeighborSignal(worldPosition);
        status = computeStatus(level);
    }

    private PedestalStatus computeStatus(ServerLevel level) {
        // Baterias e núcleo sempre atualizados (a tela mostra o mana mesmo sem pergaminho)
        List<ManaBatteryBlockEntity> list = ManaNet.batteries(level, worldPosition);
        batteries = list.size();
        int batteryMana = ManaNet.total(list);
        WirelessMana core = linkedCore(level);
        mana = (int) Math.min(Integer.MAX_VALUE, (long) batteryMana + (core != null ? core.mana() : 0));
        ItemStack scroll = scroll();
        if (scroll.isEmpty()) { cost = 0; return PedestalStatus.NO_SCROLL; }
        SpellRecipe recipe = scroll.get(CoreRegistries.SPELL.get());
        if (recipe == null || recipe.isEmpty()) { cost = 0; return PedestalStatus.EMPTY_SCROLL; }
        SpellParser.Result r = parse(recipe);
        if (!r.ok()) { cost = 0; return PedestalStatus.INVALID; }
        ParsedSpell spell = r.spell();
        // v1.0: custo BASE × escala de mana (×3). O pedestal conjura sem Círculo nem cópias por jogador, então não paga
        // o custo por cópia — fica 3× o da v0.8, como a mana, as baterias e os combustíveis do condensador.
        cost = Math.max(1, Math.round(spell.baseCost() * ResourceTraining.MANA_SCALE));
        if (spell.isUltimate() || spell.form().acceptsLaws() || hasLegendary(recipe)) return PedestalStatus.ULTIMATE;
        if (recipe.equals(brokenRecipe)) return PedestalStatus.ERROR;
        if (!enabled) return PedestalStatus.SWITCHED_OFF;
        int max = maxPerChunk();
        if (max <= 0) return PedestalStatus.DISABLED;
        if (ManaNet.pedestalsInChunk(level, worldPosition).indexOf(this) >= max) return PedestalStatus.CHUNK_LIMIT;
        if (owner == null) return PedestalStatus.NO_OWNER;
        if (redstone == RedstoneMode.WITH_SIGNAL && !powered) return PedestalStatus.PAUSED;
        if (redstone == RedstoneMode.WITHOUT_SIGNAL && powered) return PedestalStatus.PAUSED;
        if (list.isEmpty() && core == null) return PedestalStatus.NO_BATTERY;
        if (!canPay(core, batteryMana)) return PedestalStatus.NO_MANA;
        if (redstone == RedstoneMode.PULSE && !pulsePending) return PedestalStatus.WAITING_PULSE;
        if (waitingCondition) return PedestalStatus.WAITING_CONDITION;
        return PedestalStatus.ACTIVE;
    }

    /** Dá para pagar uma conjuração: o núcleo inteiro (com a perda da distância) ou as baterias inteiras. */
    private boolean canPay(@Nullable WirelessMana core, int batteryMana) {
        return (core != null && core.mana() >= WirelessMana.withLoss(cost, coreLoss)) || batteryMana >= cost;
    }

    /**
     * v1.5: o Núcleo de Mana ligado, se der para usar agora (chunk carregado, dentro do alcance). Atualiza
     * {@link #coreState} e {@link #coreLoss} para a tela.
     */
    @Nullable
    private WirelessMana linkedCore(ServerLevel level) {
        coreLoss = 0;
        if (corePos == null) { coreState = CORE_NONE; return null; }
        if (!WirelessMana.inRange(worldPosition, corePos)) { coreState = CORE_FAR; return null; }
        coreLoss = WirelessMana.lossPercent(worldPosition, corePos);
        WirelessMana core = WirelessMana.at(level, corePos);
        if (core == null) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(corePos.getX() >> 4, corePos.getZ() >> 4);
            coreState = chunk == null ? CORE_UNLOADED : CORE_MISSING;
            return null;
        }
        coreState = CORE_OK;
        return core;
    }

    /**
     * v1.5: confere TODOS os gatilhos do começo da receita (os glifos de gatilho antes da forma) que são gatilhos de
     * pedestal ({@link PedestalCondition}) — todos precisam dizer "pode" (E). Sem gatilho de pedestal = sempre pode.
     * Um gatilho com defeito para o pedestal (como uma magia com erro), sem derrubar o mundo.
     */
    private boolean conditionReady(ServerLevel level) {
        SpellRecipe recipe = cachedRecipe;
        if (recipe == null || recipe.glyphs().isEmpty() || cachedParse == null || !cachedParse.ok()) return true;
        ParsedSpell spell = cachedParse.spell();
        Vec3 origin = castPoint();
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null || g.kind() != GlyphKind.TRIGGER) break; // acabaram os gatilhos do começo
            if (!(g instanceof PedestalCondition condition)) continue;
            try {
                if (!condition.ready(level, worldPosition, origin, spell)) return false;
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.error("Runic Pedestal at {}: condition {} failed; pausing it until the scroll changes",
                        worldPosition, id, ex);
                brokenRecipe = recipe;
                status = PedestalStatus.ERROR;
                return false;
            }
        }
        return true;
    }

    /**
     * Glifos lendários (cataclismos com tremor, cutscene e crateras) são golpes de clímax: repetidos sozinhos a cada
     * segundo viram lag e destruição sem controle. O pedestal recusa, como recusa Supremas e Domínios.
     */
    private static boolean hasLegendary(SpellRecipe recipe) {
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && g.isLegendary()) return true;
        }
        return false;
    }

    /** Estado em texto (com o erro da receita, se for o caso) para a barra de ação. */
    public Component statusMessage() {
        Component text = Component.translatable(status.key());
        if (status == PedestalStatus.INVALID && cachedParse != null && cachedParse.error() != null) {
            text = Component.translatable("message.isekai_mahou.automation.pedestal_invalid_detail", text, cachedParse.error());
        }
        return text;
    }

    private SpellParser.Result parse(SpellRecipe recipe) {
        if (cachedParse == null || !recipe.equals(cachedRecipe)) {
            cachedRecipe = recipe;
            element = primaryElement(recipe);
            cachedParse = SpellParser.parse(recipe, element, null);
        }
        return cachedParse;
    }

    public static int maxPerChunk() {
        try {
            return MahouConfig.PEDESTAL_MAX_PER_CHUNK.get();
        } catch (IllegalStateException e) {
            return 4; // config ainda não carregada
        }
    }

    /**
     * Elemento primário da receita: o primeiro elemento não-Arcano dos glifos; se a receita usa exatamente dois
     * elementos base que formam uma fusão, a fusão (ex.: Fogo + Vento = Raio).
     */
    public static Element primaryElement(SpellRecipe recipe) {
        List<Element> found = new ArrayList<>();
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null || g.origin() != null || g.element() == Element.ARCANE || found.contains(g.element())) continue;
            found.add(g.element());
        }
        if (found.isEmpty()) return Element.ARCANE;
        if (found.size() == 2 && found.get(0).isBase() && found.get(1).isBase()) {
            Element fusion = Element.fusionOf(found.get(0), found.get(1));
            if (fusion != null) return fusion;
        }
        return found.get(0);
    }

    // =====================================================================================
    //  Conjurar
    // =====================================================================================
    private void cast(ServerLevel level) {
        SpellRecipe recipe = cachedRecipe;
        if (recipe == null || owner == null) return;
        List<ManaBatteryBlockEntity> list = ManaNet.batteries(level, worldPosition);
        // v1.5: paga do Núcleo ligado (com a perda da distância); sem núcleo ou sem mana nele, das baterias
        WirelessMana core = linkedCore(level);
        int coreCost = WirelessMana.withLoss(cost, coreLoss);
        boolean fromCore = false;
        if (core != null && core.mana() >= coreCost) {
            core.extract(coreCost);
            fromCore = true;
        } else if (!ManaNet.drain(list, cost)) {
            status = PedestalStatus.NO_MANA;
            return;
        }
        FakePlayer caster = Pedestals.caster(level, owner);
        Vec3 origin = castPoint();
        caster.moveTo(origin.x, origin.y, origin.z, direction.yaw, direction.pitch);
        caster.setYHeadRot(direction.yaw);
        caster.setYBodyRot(direction.yaw);

        SpellContext ctx;
        Pedestals.begin(this, owner);
        try {
            ctx = SpellCaster.castFrom(caster, element, recipe, 1.0F, origin, direction.dir, false);
        } catch (RuntimeException ex) {
            // Um glifo com defeito não pode travar o mundo (o pedestal tica sozinho, até sem ninguém por perto)
            IsekaiMahou.LOGGER.error("Runic Pedestal at {} failed casting {}; pausing it until the scroll changes", worldPosition, recipe, ex);
            refund(core, fromCore, coreCost, list);
            brokenRecipe = recipe;
            status = PedestalStatus.ERROR;
            return;
        } finally {
            Pedestals.end();
        }
        if (ctx == null) {
            refund(core, fromCore, coreCost, list);
            status = PedestalStatus.INVALID;
            return;
        }
        pulsePending = false;
        totalCasts++;
        mana = (int) Math.min(Integer.MAX_VALUE, (long) ManaNet.total(list) + (core != null ? core.mana() : 0));
        saveQuietly();
        castFx(level, origin);
        if (fromCore && corePos != null) {
            core.noteUse(worldPosition, coreCost);
            // O mana "voa" do núcleo até o tampo (a ligação sem fio fica visível)
            AutoFx.line(level, Vec3.atCenterOf(corePos).add(0, 0.6, 0), Vec3.atBottomCenterOf(worldPosition).add(0, 1.0, 0),
                    AutoFx.dust(AutoFx.MANA_RGB, 0.9F), 1.2);
        }
        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) MagicApi.addCounter(ownerPlayer, "pedestal.cast", 1);
    }

    private void refund(@Nullable WirelessMana core, boolean fromCore, int coreCost, List<ManaBatteryBlockEntity> list) {
        if (fromCore && core != null) core.insert(coreCost);
        else ManaNet.refund(list, cost);
    }

    private void castFx(ServerLevel level, Vec3 origin) {
        Vec3 top = Vec3.atBottomCenterOf(worldPosition).add(0, 0.9, 0);
        ElementFx.ring(level, top, element, 0.65, 18);
        level.sendParticles(ElementFx.signature(element), origin.x, origin.y, origin.z, 6, 0.15, 0.15, 0.15, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y, origin.z, 4, 0.1, 0.1, 0.1, 0.03);
        if (dx != 0 || dy != 0 || dz != 0) AutoFx.line(level, top.add(0, 0.3, 0), origin, ElementFx.dust(element, 0.7F), 0.5);
        level.playSound(null, top.x, top.y, top.z, ElementFx.castSoundOf(element), SoundSource.BLOCKS, 0.3F,
                0.9F + level.random.nextFloat() * 0.3F);
        level.playSound(null, top.x, top.y, top.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.25F, 1.4F);
        level.blockEvent(worldPosition, getBlockState().getBlock(), 1, element.ordinal());
    }

    // =====================================================================================
    //  Redstone e botões
    // =====================================================================================
    public void onNeighborChanged(boolean signal) {
        if (signal && !powered && redstone == RedstoneMode.PULSE) pulsePending = true;
        powered = signal;
    }

    public void handleButton(ServerPlayer player, int id) {
        if (!mayConfigure(player)) return;
        boolean big = id == B_INTERVAL_DOWN10 || id == B_INTERVAL_UP10;
        switch (id) {
            case B_INTERVAL_DOWN, B_INTERVAL_DOWN10 -> interval = Mth.clamp(interval - (big ? 10 : 1), MIN_INTERVAL, MAX_INTERVAL);
            case B_INTERVAL_UP, B_INTERVAL_UP10 -> interval = Mth.clamp(interval + (big ? 10 : 1), MIN_INTERVAL, MAX_INTERVAL);
            case B_DIRECTION_NEXT -> direction = direction.cycle(1);
            case B_DIRECTION_PREV -> direction = direction.cycle(-1);
            case B_DX_DOWN -> dx = Mth.clamp(dx - 1, -MAX_OFFSET, MAX_OFFSET);
            case B_DX_UP -> dx = Mth.clamp(dx + 1, -MAX_OFFSET, MAX_OFFSET);
            case B_DY_DOWN -> dy = Mth.clamp(dy - 1, -MAX_OFFSET, MAX_OFFSET);
            case B_DY_UP -> dy = Mth.clamp(dy + 1, -MAX_OFFSET, MAX_OFFSET);
            case B_DZ_DOWN -> dz = Mth.clamp(dz - 1, -MAX_OFFSET, MAX_OFFSET);
            case B_DZ_UP -> dz = Mth.clamp(dz + 1, -MAX_OFFSET, MAX_OFFSET);
            case B_REDSTONE_NEXT -> { redstone = redstone.cycle(1); pulsePending = false; }
            case B_REDSTONE_PREV -> { redstone = redstone.cycle(-1); pulsePending = false; }
            case B_RESET_OFFSET -> { dx = 0; dy = 0; dz = 0; }
            case B_TEST -> testCast(player);
            case B_TOGGLE -> {
                setEnabled(!enabled);
                level.playSound(null, worldPosition, enabled ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 0.5F, 1.4F);
            }
            default -> { return; }
        }
        if (timer > interval * 20) timer = interval * 20;
        waitingCondition = false;
        if (level instanceof ServerLevel server) evaluate(server);
        showAim(player);
        setChanged();
    }

    /** "Testar": conjura agora (paga mana normalmente), útil para mirar. */
    private void testCast(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return;
        evaluate(server);
        // "Testar" ignora o liga/desliga e o gatilho de pedestal (serve para mirar), mas paga mana normalmente
        boolean ok = status == PedestalStatus.ACTIVE || (status == PedestalStatus.WAITING_PULSE)
                || (status == PedestalStatus.PAUSED && redstone != RedstoneMode.ALWAYS)
                || status == PedestalStatus.SWITCHED_OFF || status == PedestalStatus.WAITING_CONDITION;
        WirelessMana core = linkedCore(server);
        int batteryMana = ManaNet.total(ManaNet.batteries(server, worldPosition));
        if (ok && owner != null && cachedRecipe != null && canPay(core, batteryMana)) {
            cast(server);
            timer = redstone == RedstoneMode.PULSE ? 10 : interval * 20;
        } else {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.automation.pedestal_test_failed", statusMessage())
                    .withColor(status.rgb), true);
        }
    }

    /** Partículas mostrando onde a magia nasce e para onde vai (ao mexer nos botões). */
    private void showAim(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 o = castPoint();
        Vec3 tip = o.add(direction.dir.scale(1.5));
        server.sendParticles(player, ParticleTypes.END_ROD, true, o.x, o.y, o.z, 6, 0.08, 0.08, 0.08, 0.0);
        for (int i = 1; i <= 6; i++) {
            Vec3 p = o.lerp(tip, i / 6.0);
            server.sendParticles(player, ElementFx.dust(element, 0.8F), true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    // =====================================================================================
    //  Cliente: brilho depois de conjurar (blockEvent)
    // =====================================================================================
    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            flashTicks = 12;
            element = Element.values()[Mth.clamp(type, 0, Element.values().length - 1)];
            return true;
        }
        return super.triggerEvent(id, type);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RunicPedestalBlockEntity be) {
        if (be.flashTicks > 0) be.flashTicks--;
    }

    public float flash(float partialTick) {
        return flashTicks <= 0 ? 0F : Math.max(0F, (flashTicks - partialTick) / 12F);
    }

    // =====================================================================================
    //  Container (0 = Pergaminho de Magia, 1 = filtro; funis não mexem)
    // =====================================================================================
    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        boolean scroll = stack.is(CoreRegistries.SPELL_SCROLL.get());
        return slot == SLOT_SCROLL ? scroll : !scroll;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return false;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.isekai_mahou.runic_pedestal");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        if (level instanceof ServerLevel server) evaluate(server);
        return new RunicPedestalMenu(id, inventory, this);
    }

    /** Marca para salvar sem mandar pacote aos clientes (contadores, tempo). */
    private void saveQuietly() {
        super.setChanged();
    }

    /** Mudou o pergaminho ou a configuração: salva e manda o visual para os clientes. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            SpellRecipe r = scroll().get(CoreRegistries.SPELL.get());
            if (r != null) element = primaryElement(r);
            if (scroll().isEmpty()) brokenRecipe = null; // tirou o pergaminho: ao voltar, tenta de novo
            waitingCondition = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // =====================================================================================
    //  NBT e sincronização
    // =====================================================================================
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putInt("Interval", interval);
        tag.putString("Direction", direction.id);
        tag.putInt("Dx", dx);
        tag.putInt("Dy", dy);
        tag.putInt("Dz", dz);
        tag.putString("Redstone", redstone.id);
        tag.putInt("Timer", timer);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Pulse", pulsePending);
        tag.putInt("Casts", totalCasts);
        tag.putBoolean("Enabled", enabled);
        if (corePos != null) tag.putLong("CorePos", corePos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY); // saves antigos só têm o espaço 0
        ContainerHelper.loadAllItems(tag, items, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        if (tag.contains("Interval")) interval = Mth.clamp(tag.getInt("Interval"), MIN_INTERVAL, MAX_INTERVAL);
        direction = PedestalDirection.DOWN;
        for (PedestalDirection d : PedestalDirection.values()) if (d.id.equals(tag.getString("Direction"))) direction = d;
        dx = Mth.clamp(tag.getInt("Dx"), -MAX_OFFSET, MAX_OFFSET);
        dy = Mth.clamp(tag.getInt("Dy"), -MAX_OFFSET, MAX_OFFSET);
        dz = Mth.clamp(tag.getInt("Dz"), -MAX_OFFSET, MAX_OFFSET);
        redstone = RedstoneMode.ALWAYS;
        for (RedstoneMode m : RedstoneMode.values()) if (m.id.equals(tag.getString("Redstone"))) redstone = m;
        timer = tag.getInt("Timer");
        powered = tag.getBoolean("Powered");
        pulsePending = tag.getBoolean("Pulse");
        totalCasts = tag.getInt("Casts");
        // v1.5 (opcionais): sem a chave = ligado e sem núcleo
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        corePos = tag.contains("CorePos") ? BlockPos.of(tag.getLong("CorePos")) : null;
        waitingCondition = false;
        // No cliente o pacote de atualização traz o elemento para colorir o anel
        if (tag.contains("Element")) {
            Element e = Element.byId(tag.getString("Element"));
            if (e != null) element = e;
        }
        cachedParse = null;
        cachedRecipe = null;
    }

    /** Para o cliente: só o pergaminho (desenho) e o elemento (cor do anel). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, items, true, registries);
        SpellRecipe r = scroll().get(CoreRegistries.SPELL.get());
        tag.putString("Element", (r != null ? primaryElement(r) : element).id);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
