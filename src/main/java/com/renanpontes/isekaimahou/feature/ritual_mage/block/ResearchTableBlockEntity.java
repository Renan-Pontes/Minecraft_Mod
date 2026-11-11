package com.renanpontes.isekaimahou.feature.ritual_mage.block;

import java.util.Arrays;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreEvents;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.ritual_mage.MageRegistries;
import com.renanpontes.isekaimahou.feature.ritual_mage.item.ResearchNoteItem;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.MageResearchData;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.Thesis;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.ThesisPuzzle;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.ThesisService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dados da Mesa de Pesquisa: notas e papel guardados, a tese escolhida, a semente da grade e as runas escritas.
 * Toda ação do minijogo chega aqui pelo clique de botão do menu (validada no servidor).
 */
public class ResearchTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int NOTE_SLOTS = 18;
    public static final int PAPER_SLOT = 18;
    public static final int SIZE = 19;

    // Índices do ContainerData sincronizado com a tela
    public static final int DATA_THESIS = 0, DATA_SEED = 1, DATA_SHELVES = 2, DATA_GRID = 3;
    public static final int DATA_COUNT = DATA_GRID + ThesisPuzzle.CELLS;

    // Botões do menu (ServerboundContainerButtonClickPacket)
    public static final int BTN_CLEAR = 100, BTN_REROLL = 101, BTN_COMPLETE = 102;
    public static final int BTN_PLACE = 1000, BTN_REMOVE = 2000;

    private final SimpleContainer items = new SimpleContainer(SIZE);
    private int thesis = -1;
    private int seed;
    private int rerolls;
    private int shelves;
    private final byte[] grid = new byte[ThesisPuzzle.CELLS];

    private ThesisPuzzle cachedPuzzle;
    private int cachedThesis = -2, cachedSeed;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_THESIS -> thesis;
                case DATA_SEED -> seed;
                case DATA_SHELVES -> shelves;
                default -> i >= DATA_GRID && i < DATA_COUNT ? grid[i - DATA_GRID] : 0;
            };
        }

        @Override
        public void set(int i, int value) {
            switch (i) {
                case DATA_THESIS -> thesis = value;
                case DATA_SEED -> seed = value;
                case DATA_SHELVES -> shelves = value;
                default -> {
                    if (i >= DATA_GRID && i < DATA_COUNT) grid[i - DATA_GRID] = (byte) value;
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(MageRegistries.RESEARCH_TABLE_BE.get(), pos, state);
        Arrays.fill(grid, (byte) -1);
        items.addListener(c -> setChanged());
    }

    public SimpleContainer items() { return items; }
    public ContainerData data() { return data; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ResearchTableBlockEntity table) {
        if (level.getGameTime() % 40 == 0) table.refreshShelves();
    }

    public void refreshShelves() {
        if (level instanceof ServerLevel server) shelves = Math.round(CoreEvents.bookshelfPower(server, worldPosition));
    }

    public int shelves() { return shelves; }

    @Nullable
    public Thesis thesis() {
        return Thesis.byIndex(thesis);
    }

    @Nullable
    public ThesisPuzzle puzzle() {
        Thesis t = thesis();
        if (t == null) return null;
        if (cachedPuzzle == null || cachedThesis != thesis || cachedSeed != seed) {
            cachedPuzzle = ThesisPuzzle.generate(t, seed);
            cachedThesis = thesis;
            cachedSeed = seed;
        }
        return cachedPuzzle;
    }

    /** Runas escritas pelo jogador (null = célula vazia). */
    public Element[] placedElements() {
        return decodeGrid(grid);
    }

    public static Element[] decodeGrid(byte[] grid) {
        Element[] out = new Element[ThesisPuzzle.CELLS];
        Element[] values = Element.values();
        for (int i = 0; i < out.length; i++) out[i] = grid[i] >= 0 && grid[i] < values.length ? values[grid[i]] : null;
        return out;
    }

    // ---------------------------------------------------------------- Inventário
    public static int countNotes(net.minecraft.world.Container container, Element e) {
        int n = 0;
        for (int i = 0; i < NOTE_SLOTS; i++) {
            ItemStack s = container.getItem(i);
            if (s.is(MageRegistries.RESEARCH_NOTE.get()) && ResearchNoteItem.elementOf(s) == e) n += s.getCount();
        }
        return n;
    }

    public static int countPaper(net.minecraft.world.Container container) {
        ItemStack s = container.getItem(PAPER_SLOT);
        return s.is(Items.PAPER) ? s.getCount() : 0;
    }

    private int placedCount(Element e) {
        int n = 0;
        for (byte b : grid) if (b == e.ordinal()) n++;
        return n;
    }

    /** Remove as notas usadas nas runas (por elemento). */
    public void consumeNotes(Element[] placed) {
        int[] need = new int[Element.values().length];
        for (Element e : placed) if (e != null) need[e.ordinal()]++;
        for (int i = 0; i < NOTE_SLOTS; i++) {
            ItemStack s = items.getItem(i);
            if (!s.is(MageRegistries.RESEARCH_NOTE.get())) continue;
            int idx = ResearchNoteItem.elementOf(s).ordinal();
            int take = Math.min(need[idx], s.getCount());
            if (take > 0) {
                s.shrink(take);
                need[idx] -= take;
            }
        }
        items.setChanged();
    }

    public void consumePaper(int amount) {
        items.getItem(PAPER_SLOT).shrink(amount);
        items.setChanged();
    }

    public void resetThesis() {
        thesis = -1;
        Arrays.fill(grid, (byte) -1);
        setChanged();
    }

    // ---------------------------------------------------------------- Ações do minijogo
    public void handleAction(ServerPlayer player, int id) {
        if (id >= 0 && id < Thesis.ALL.size()) selectThesis(player, id);
        else if (id == BTN_CLEAR) clearGrid(player);
        else if (id == BTN_REROLL) reroll(player);
        else if (id == BTN_COMPLETE) ThesisService.tryComplete(player, this);
        else if (id >= BTN_PLACE && id < BTN_PLACE + ThesisPuzzle.CELLS * 32) place((id - BTN_PLACE) / 32, (id - BTN_PLACE) % 32);
        else if (id >= BTN_REMOVE && id < BTN_REMOVE + ThesisPuzzle.CELLS) remove(id - BTN_REMOVE);
    }

    private void selectThesis(ServerPlayer player, int index) {
        Thesis t = Thesis.ALL.get(index);
        MageResearchData research = player.getData(MageRegistries.RESEARCH);
        if (research.hasThesis(t.id())) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.mage.thesis_known").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (research.thesisCount() < t.requiredTheses()) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.mage.thesis_locked", t.requiredTheses())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (thesis == index) return;
        thesis = index;
        seed = seedFor(index);
        Arrays.fill(grid, (byte) -1);
        setChanged();
        playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F);
    }

    private void clearGrid(ServerPlayer player) {
        Arrays.fill(grid, (byte) -1);
        setChanged();
        playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F);
    }

    /** Novo arranjo da grade: custa 1 papel (grátis no criativo). */
    private void reroll(ServerPlayer player) {
        if (thesis() == null) return;
        if (!player.getAbilities().instabuild) {
            if (countPaper(items) < 1) {
                player.sendSystemMessage(Component.translatable("message.isekai_mahou.mage.need_paper", 1).withStyle(ChatFormatting.RED));
                return;
            }
            consumePaper(1);
        }
        rerolls++;
        seed = seedFor(thesis);
        Arrays.fill(grid, (byte) -1);
        setChanged();
        playSound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0F);
    }

    /**
     * Semente da grade: depende da mesa, da tese e de quantas vezes a grade foi rearranjada. Assim trocar de tese
     * e voltar não gera um arranjo novo de graça (só o botão Rearranjar, que custa papel).
     * Fica em 0..32767 porque o ContainerData sincroniza valores como short.
     */
    private int seedFor(int index) {
        int h = worldPosition.hashCode() * 31 + index * 7919 + rerolls * 104729;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) % 32768;
    }

    private void place(int cell, int elementIndex) {
        ThesisPuzzle puzzle = puzzle();
        if (puzzle == null || !puzzle.isWritable(cell) || elementIndex < 0 || elementIndex >= Element.values().length) return;
        Element e = Element.values()[elementIndex];
        int used = placedCount(e) - (grid[cell] == elementIndex ? 1 : 0);
        if (used >= countNotes(items, e)) return;
        grid[cell] = (byte) elementIndex;
        setChanged();
    }

    private void remove(int cell) {
        if (cell < 0 || cell >= grid.length || grid[cell] < 0) return;
        grid[cell] = -1;
        setChanged();
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        if (level != null) level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.7F, pitch);
    }

    // ---------------------------------------------------------------- Menu
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.isekai_mahou.research_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ResearchTableMenu(id, inventory, this);
    }

    // ---------------------------------------------------------------- Salvar
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) list.set(i, items.getItem(i));
        ContainerHelper.saveAllItems(tag, list, registries);
        tag.putInt("Thesis", thesis);
        tag.putInt("Seed", seed);
        tag.putInt("Rerolls", rerolls);
        tag.putByteArray("Grid", grid.clone());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        for (int i = 0; i < SIZE; i++) items.setItem(i, list.get(i));
        thesis = tag.contains("Thesis") ? tag.getInt("Thesis") : -1;
        seed = Math.floorMod(tag.getInt("Seed"), 32768);
        rerolls = tag.getInt("Rerolls");
        byte[] g = tag.getByteArray("Grid");
        Arrays.fill(grid, (byte) -1);
        System.arraycopy(g, 0, grid, 0, Math.min(g.length, grid.length));
    }
}
