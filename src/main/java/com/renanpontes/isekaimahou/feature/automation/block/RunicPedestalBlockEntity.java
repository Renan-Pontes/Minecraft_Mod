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
import com.renanpontes.isekaimahou.feature.automation.Pedestals;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Pedestal Rúnico: guarda um Pergaminho de Magia e conjura a receita sozinho a cada N segundos, a partir do topo
 * (mais o deslocamento escolhido), na direção escolhida. Quem conjura é um FakePlayer do dono (abates contam como
 * de jogador → drops e XP como numa fazenda). Paga o custo da magia com Baterias de Mana a até 8 blocos.
 */
public class RunicPedestalBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int MIN_INTERVAL = 1, MAX_INTERVAL = 60, MAX_OFFSET = 8;
    /** Altura (a partir da base do bloco) de onde a magia nasce: um pouco acima do tampo. */
    public static final double CAST_HEIGHT = 1.25;
    private static final int[] NO_SLOTS = new int[0];

    // Índices do ContainerData (tela)
    public static final int D_INTERVAL = 0, D_DIRECTION = 1, D_DX = 2, D_DY = 3, D_DZ = 4, D_REDSTONE = 5, D_STATUS = 6, D_COST = 7,
            D_MANA_LO = 8, D_MANA_HI = 9, D_BATTERIES = 10, D_TIMER = 11, D_ELEMENT = 12, D_CASTS_LO = 13, D_CASTS_HI = 14,
            D_POWERED = 15, D_COUNT = 16;

    // Botões do menu (ServerboundContainerButtonClickPacket)
    public static final int B_INTERVAL_DOWN = 0, B_INTERVAL_UP = 1, B_INTERVAL_DOWN10 = 2, B_INTERVAL_UP10 = 3, B_DIRECTION_NEXT = 4,
            B_DIRECTION_PREV = 5, B_DX_DOWN = 6, B_DX_UP = 7, B_DY_DOWN = 8, B_DY_UP = 9, B_DZ_DOWN = 10, B_DZ_UP = 11,
            B_REDSTONE_NEXT = 12, B_REDSTONE_PREV = 13, B_RESET_OFFSET = 14, B_TEST = 15;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
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

    // Estado calculado (não salvo)
    private PedestalStatus status = PedestalStatus.NO_SCROLL;
    private int cost;
    private int mana;
    private int batteries;
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
    public String ownerName() { return ownerName; }
    public ItemStack scroll() { return items.get(0); }
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
            if (status == PedestalStatus.ACTIVE) {
                cast(level);
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
        // Baterias sempre atualizadas (a tela mostra o mana mesmo sem pergaminho)
        List<ManaBatteryBlockEntity> list = ManaNet.batteries(level, worldPosition);
        batteries = list.size();
        mana = ManaNet.total(list);
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
        int max = maxPerChunk();
        if (max <= 0) return PedestalStatus.DISABLED;
        if (ManaNet.pedestalsInChunk(level, worldPosition).indexOf(this) >= max) return PedestalStatus.CHUNK_LIMIT;
        if (owner == null) return PedestalStatus.NO_OWNER;
        if (redstone == RedstoneMode.WITH_SIGNAL && !powered) return PedestalStatus.PAUSED;
        if (redstone == RedstoneMode.WITHOUT_SIGNAL && powered) return PedestalStatus.PAUSED;
        if (list.isEmpty()) return PedestalStatus.NO_BATTERY;
        if (mana < cost) return PedestalStatus.NO_MANA;
        if (redstone == RedstoneMode.PULSE && !pulsePending) return PedestalStatus.WAITING_PULSE;
        return PedestalStatus.ACTIVE;
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
        if (!ManaNet.drain(list, cost)) {
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
            ManaNet.refund(list, cost);
            brokenRecipe = recipe;
            status = PedestalStatus.ERROR;
            return;
        } finally {
            Pedestals.end();
        }
        if (ctx == null) {
            ManaNet.refund(list, cost);
            status = PedestalStatus.INVALID;
            return;
        }
        pulsePending = false;
        totalCasts++;
        mana = ManaNet.total(list);
        saveQuietly();
        castFx(level, origin);
        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) MagicApi.addCounter(ownerPlayer, "pedestal.cast", 1);
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
            default -> { return; }
        }
        if (timer > interval * 20) timer = interval * 20;
        if (level instanceof ServerLevel server) evaluate(server);
        showAim(player);
        setChanged();
    }

    /** "Testar": conjura agora (paga mana normalmente), útil para mirar. */
    private void testCast(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return;
        evaluate(server);
        boolean ok = status == PedestalStatus.ACTIVE || (status == PedestalStatus.WAITING_PULSE)
                || (status == PedestalStatus.PAUSED && redstone != RedstoneMode.ALWAYS);
        if (ok && owner != null && cachedRecipe != null && mana >= cost) {
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
    //  Container (1 espaço, só Pergaminho de Magia; funis não mexem)
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
        return 1;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(CoreRegistries.SPELL_SCROLL.get());
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
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
