package com.renanpontes.isekaimahou.feature.farm.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.farm.FarmFx;
import com.renanpontes.isekaimahou.feature.farm.FarmRegistries;
import com.renanpontes.isekaimahou.feature.farm.ManaLink;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Estado e lógica das fazendas mágicas. Espaços: 0 = entrada (muda, catalisador, comida, vara, núcleo), 1 = combustível
 * (cristal de mana etc.; farinha de osso acelera a Árvore), 2–10 = produção (funis tiram por baixo/lados; a máquina
 * também empurra sozinha para um baú/funil EMBAIXO dela). Redstone pausa.
 *
 * <p>Ciclo: ao começar, paga a mana (e o que consome); ao terminar, entrega. O cliente recebe só o início/fim do ciclo
 * (e calcula o progresso pelo relógio do mundo) — nenhum pacote por tick.
 */
public class FarmMachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOT_INPUT = 0, SLOT_FUEL = 1, FIRST_OUT = 2, SIZE = 11;
    private static final int[] TOP = {SLOT_INPUT, SLOT_FUEL};
    private static final int[] OUTS = IntStream.range(FIRST_OUT, SIZE).toArray();
    private static final int[] SIDES = IntStream.range(0, SIZE).toArray();
    /** Quanto puxa das baterias por vez. */
    private static final int PULL = 500;

    // ContainerData (tela)
    public static final int DATA_MANA = 0, DATA_CAP = 1, DATA_PROGRESS = 2, DATA_LENGTH = 3, DATA_STATUS = 4, DATA_CHARGES = 5,
            DATA_BATTERIES = 6, DATA_COUNT = 7;

    public enum Status {
        IDLE, WORKING, NO_MANA, NO_INPUT, FULL, PAUSED, NO_ANIMAL;

        private static final String STATUS_KEY = "farm.isekai_mahou.machine.status.";

        public Component text() {
            return Component.translatable(STATUS_KEY + name().toLowerCase(java.util.Locale.ROOT));
        }

        public int color() {
            return switch (this) {
                case WORKING -> 0x9BE07A;
                case IDLE, PAUSED -> 0xB8B0C8;
                default -> 0xFF9F5A;
            };
        }
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int mana;
    private int progress;
    private int length;
    private Status status = Status.IDLE;
    /** Cargas do catalisador (gerador) ou da farinha de osso (árvore). */
    private int charges;
    @Nullable
    private Element chargeElement;
    /** Animal guardado (Criadouro). */
    @Nullable
    private ResourceLocation animal;
    /** O que mostrar em cima (último produto). */
    private ItemStack display = ItemStack.EMPTY;
    private long cycleStart;
    private final List<ItemStack> pending = new ArrayList<>();
    // transitórios
    private List<net.minecraft.world.level.block.entity.BlockEntity> batteries = List.of();
    private long nextScan;
    @Nullable
    private Entity dummy;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_MANA -> mana;
                case DATA_CAP -> kind().capacity();
                case DATA_PROGRESS -> progress;
                case DATA_LENGTH -> length;
                case DATA_STATUS -> status.ordinal();
                case DATA_CHARGES -> charges;
                case DATA_BATTERIES -> batteries.size();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public FarmMachineBlockEntity(BlockPos pos, BlockState state) {
        super(FarmRegistries.MACHINE_BE.get(), pos, state);
    }

    public MachineKind kind() {
        return getBlockState().getBlock() instanceof FarmMachineBlock b ? b.kind : MachineKind.STONE_GENERATOR;
    }

    public ContainerData data() { return data; }
    public Status status() { return status; }
    public int mana() { return mana; }
    public long cycleStart() { return cycleStart; }
    public int length() { return length; }
    public ItemStack display() { return display; }
    public ItemStack input() { return items.get(SLOT_INPUT); }
    @Nullable public ResourceLocation animal() { return animal; }

    /** Progresso 0..1 calculado no cliente pelo relógio do mundo. */
    public float clientProgress(float partialTick) {
        if (status != Status.WORKING || length <= 0 || level == null) return 0F;
        return Math.min(1F, (level.getGameTime() - cycleStart + partialTick) / length);
    }

    @Nullable
    public Element element() {
        BlockState s = getBlockState();
        return s.hasProperty(ElementalPedestalBlock.ELEMENT) ? s.getValue(ElementalPedestalBlock.ELEMENT) : null;
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================
    public static void serverTick(Level level, BlockPos pos, BlockState state, FarmMachineBlockEntity be) {
        if (level instanceof ServerLevel sl) be.tick(sl);
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        // Manutenção a cada segundo, espalhada entre as máquinas
        if ((now + worldPosition.asLong()) % 20 == 0) maintain(level, now);
        if (level.hasNeighborSignal(worldPosition)) {
            setStatus(Status.PAUSED);
            return;
        }
        if (length > 0) {
            if (status != Status.WORKING) setStatus(Status.WORKING);
            if (++progress >= length) finishCycle(level);
            return;
        }
        if ((now + worldPosition.asLong()) % 10 == 0 || status == Status.PAUSED) tryStart(level, now);
    }

    private void maintain(ServerLevel level, long now) {
        if (now >= nextScan) {
            nextScan = now + 100;
            batteries = ManaLink.batteries(level, worldPosition);
        }
        int cap = kind().capacity();
        // Combustível do espaço 1
        ItemStack fuel = items.get(SLOT_FUEL);
        int value = Produce.fuelValue(fuel);
        while (value > 0 && mana + value <= cap && !fuel.isEmpty()) {
            fuel.shrink(1);
            mana += value;
            setChanged();
        }
        // Baterias
        if (mana < cap / 2 && !batteries.isEmpty()) {
            int got = ManaLink.drain(batteries, Math.min(PULL, cap - mana));
            if (got > 0) {
                mana += got;
                setChanged();
                if (level.random.nextInt(3) == 0) {
                    BlockPos b = batteries.get(0).getBlockPos();
                    FarmFx.beam(level, Vec3.atCenterOf(b), Vec3.atCenterOf(worldPosition), FarmFx.dust(0xB896FF, 0.6F), 0.7);
                }
            }
        }
        flushPending();
        pushDown(level);
    }

    /** Empurra a produção para um inventário logo abaixo (funil, baú, coletor...). */
    private void pushDown(ServerLevel level) {
        IItemHandler below = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.below(), Direction.UP);
        if (below == null) return;
        for (int i = FIRST_OUT; i < SIZE; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            ItemStack rest = ItemHandlerHelper.insertItemStacked(below, s.copy(), false);
            if (rest.getCount() != s.getCount()) {
                items.set(i, rest);
                setChanged();
                return; // um por segundo basta
            }
        }
    }

    private void tryStart(ServerLevel level, long now) {
        if (!pending.isEmpty()) {
            setStatus(Status.FULL);
            return;
        }
        if (freeOutputSlots() < 2) {
            setStatus(Status.FULL);
            return;
        }
        MachineKind k = kind();
        int ticks, cost;
        ItemStack in = items.get(SLOT_INPUT);
        switch (k) {
            case STONE_GENERATOR -> {
                Element cat = chargeElement;
                Element slotCat = Produce.catalyst(in);
                if (charges <= 0 || (slotCat != null && slotCat != cat)) cat = slotCat;
                ticks = Produce.stoneTicks(cat);
                cost = Produce.stoneMana(cat);
            }
            case ACCELERATED_TREE -> {
                if (!Produce.isPlantable(in)) {
                    setStatus(Status.NO_INPUT);
                    return;
                }
                boolean tree = Produce.isTree(in);
                ticks = tree ? 600 : 300;
                cost = tree ? 30 : 12;
            }
            case HATCHERY -> {
                if (animal == null) {
                    setStatus(Status.NO_ANIMAL);
                    return;
                }
                if (!(dummy(level) instanceof Animal a) || in.isEmpty() || !a.isFood(in)) {
                    setStatus(Status.NO_INPUT);
                    return;
                }
                ticks = 900;
                cost = 40;
            }
            case FISH_POND -> {
                boolean rod = in.is(Items.FISHING_ROD);
                int lure = rod ? enchant(level, in, true) : 0;
                ticks = Math.max(200, 600 - (rod ? 100 : 0) - 80 * lure);
                cost = 25;
            }
            default -> {
                Element e = element();
                boolean core = e != null && isCoreOf(in, e);
                ticks = core ? 3000 : 6000;
                cost = 500;
            }
        }
        if (mana < cost) {
            setStatus(Status.NO_MANA);
            return;
        }
        // Paga e consome
        mana -= cost;
        switch (k) {
            case STONE_GENERATOR -> {
                Element slotCat = Produce.catalyst(in);
                if (slotCat != null && (charges <= 0 || slotCat != chargeElement)) {
                    in.shrink(1);
                    charges = Produce.CATALYST_CHARGES;
                    chargeElement = slotCat;
                }
                if (charges > 0 && chargeElement != null) charges--;
                else chargeElement = null;
                // ciclo de minério marcado pelo elemento até a carga acabar
            }
            case ACCELERATED_TREE -> {
                ItemStack fuel = items.get(SLOT_FUEL);
                if (fuel.is(Items.BONE_MEAL)) {
                    fuel.shrink(1);
                    ticks /= 2;
                }
            }
            case HATCHERY -> in.shrink(1);
            default -> { }
        }
        length = ticks;
        progress = 0;
        cycleStart = now;
        setStatus(Status.WORKING);
        sync();
    }

    private void finishCycle(ServerLevel level) {
        List<ItemStack> out = produce(level);
        length = 0;
        progress = 0;
        for (ItemStack s : out) {
            ItemStack rest = insertOutput(s);
            if (!rest.isEmpty()) pending.add(rest);
        }
        if (!out.isEmpty()) display = out.get(0).copyWithCount(1);
        fx(level, out);
        setStatus(Status.IDLE);
        sync();
    }

    private List<ItemStack> produce(ServerLevel level) {
        List<ItemStack> out = new ArrayList<>();
        var rnd = level.random;
        ItemStack in = items.get(SLOT_INPUT);
        switch (kind()) {
            case STONE_GENERATOR -> {
                ItemStack s = Produce.pick(chargeElement != null || charges > 0 ? Produce.oreTable(chargeElement == null ? Element.EARTH : chargeElement)
                        : Produce.STONE, rnd);
                if (!s.isEmpty()) out.add(s);
                if (charges <= 0) chargeElement = null;
            }
            case ACCELERATED_TREE -> out.addAll(Produce.harvest(in, rnd));
            case HATCHERY -> {
                if (animal != null && dummy(level) instanceof Animal a) {
                    a.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
                    LootTable table = level.getServer().reloadableRegistries().getLootTable(a.getLootTable());
                    LootParams params = new LootParams.Builder(level)
                            .withParameter(LootContextParams.THIS_ENTITY, a)
                            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                            .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().magic())
                            .create(LootContextParamSets.ENTITY);
                    table.getRandomItems(params, out::add);
                    Produce.hatcheryExtras(animal, rnd, out);
                }
            }
            case FISH_POND -> {
                ItemStack rod = in.is(Items.FISHING_ROD) ? in : new ItemStack(Items.FISHING_ROD);
                int luck = in.is(Items.FISHING_ROD) ? enchant(level, in, false) : 0;
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                        .withParameter(LootContextParams.TOOL, rod)
                        .withLuck(luck)
                        .create(LootContextParamSets.FISHING);
                var tables = level.getServer().reloadableRegistries();
                boolean treasure = rnd.nextFloat() < 0.03F + 0.02F * luck;
                tables.getLootTable(treasure ? BuiltInLootTables.FISHING_TREASURE : BuiltInLootTables.FISHING).getRandomItems(params, out::add);
                if (in.is(Items.FISHING_ROD)) {
                    in.hurtAndBreak(1, level, (ServerPlayer) null, item -> items.set(SLOT_INPUT, ItemStack.EMPTY));
                }
            }
            default -> {
                Element e = element();
                if (e != null) {
                    var essence = CoreRegistries.ESSENCES.get(e);
                    if (essence != null) out.add(new ItemStack(essence.get()));
                    var arcane = CoreRegistries.ESSENCES.get(Element.ARCANE);
                    if (arcane != null && rnd.nextFloat() < 0.08F) out.add(new ItemStack(arcane.get()));
                }
            }
        }
        out.removeIf(ItemStack::isEmpty);
        return out;
    }

    private void fx(ServerLevel level, List<ItemStack> out) {
        Vec3 c = Vec3.atCenterOf(worldPosition).add(0, 0.6, 0);
        MachineKind k = kind();
        switch (k) {
            case STONE_GENERATOR -> {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.2, c.z, 3, 0.2, 0.1, 0.2, 0.01);
                FarmFx.sound(level, c, chargeElement != null ? SoundEvents.DEEPSLATE_BREAK : SoundEvents.STONE_PLACE, 0.5F, 0.9F);
                if (chargeElement != null) FarmFx.sound(level, c, SoundEvents.LAVA_EXTINGUISH, 0.4F, 1.2F);
            }
            case ACCELERATED_TREE -> {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 0.3, c.z, 10, 0.4, 0.4, 0.4, 0);
                FarmFx.sound(level, c, SoundEvents.AZALEA_LEAVES_BREAK, 0.8F, 1.0F);
                FarmFx.sound(level, c, SoundEvents.WOOD_BREAK, 0.5F, 0.9F);
            }
            case HATCHERY -> {
                level.sendParticles(ParticleTypes.HEART, c.x, c.y + 0.3, c.z, 3, 0.3, 0.2, 0.3, 0);
                FarmFx.sound(level, c, SoundEvents.CHICKEN_EGG, 0.6F, 1.0F);
            }
            case FISH_POND -> {
                level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 16, 0.3, 0.05, 0.3, 0.1);
                level.sendParticles(ParticleTypes.FISHING, c.x, c.y, c.z, 6, 0.2, 0, 0.2, 0.02);
                FarmFx.sound(level, c, SoundEvents.FISHING_BOBBER_SPLASH, 0.5F, 1.1F);
            }
            default -> {
                Element e = element();
                if (e != null) {
                    ElementFx.burst(level, c.add(0, 0.4, 0), e, 18, 0.35);
                    ElementFx.ring(level, c.add(0, -0.2, 0), e, 0.9, 16);
                }
                FarmFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 1.3F);
            }
        }
    }

    private void setStatus(Status s) {
        if (status == s) return;
        status = s;
        boolean on = s == Status.WORKING;
        BlockState st = getBlockState();
        if (level != null && st.hasProperty(FarmMachineBlock.ACTIVE) && st.getValue(FarmMachineBlock.ACTIVE) != on) {
            level.setBlock(worldPosition, st.setValue(FarmMachineBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
        sync();
    }

    // =====================================================================================
    //  Criadouro
    // =====================================================================================
    @Nullable
    private Entity dummy(ServerLevel level) {
        if (animal == null) return null;
        if (dummy != null && BuiltInRegistries.ENTITY_TYPE.getKey(dummy.getType()).equals(animal)) return dummy;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(animal).orElse(null);
        dummy = type == null ? null : type.create(level);
        return dummy;
    }

    /** Pode guardar este bicho? (animal comum, sem dono, de fora do mod) */
    public static boolean capturable(Entity e) {
        if (!(e instanceof Animal a) || !a.isAlive()) return false;
        if (e instanceof net.minecraft.world.entity.OwnableEntity own && own.getOwnerUUID() != null) return false;
        return !IsekaiMahou.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
    }

    /** Guarda o animal (o antigo, se houver, sai livre). */
    public void capture(ServerLevel level, Animal a, ServerPlayer by) {
        releaseAnimal(level);
        animal = BuiltInRegistries.ENTITY_TYPE.getKey(a.getType());
        dummy = null;
        Vec3 from = a.position().add(0, a.getBbHeight() / 2, 0);
        FarmFx.beam(level, from, Vec3.atCenterOf(worldPosition), FarmFx.dust(MachineKind.HATCHERY.rgb, 1.0F), 0.4);
        level.sendParticles(ParticleTypes.POOF, from.x, from.y, from.z, 12, 0.3, 0.3, 0.3, 0.02);
        a.dropLeash(true, true);
        a.discard();
        FarmFx.sound(level, Vec3.atCenterOf(worldPosition), SoundEvents.CHICKEN_EGG, 1.0F, 0.7F);
        FarmFx.sound(level, Vec3.atCenterOf(worldPosition), SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.5F);
        by.displayClientMessage(Component.translatable("farm.isekai_mahou.hatchery.captured", a.getType().getDescription())
                .withColor(MachineKind.HATCHERY.rgb), true);
        length = 0;
        progress = 0;
        setStatus(Status.IDLE);
        sync();
    }

    /** Solta o animal guardado em cima da máquina. */
    public void releaseAnimal(ServerLevel level) {
        if (animal == null) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(animal).orElse(null);
        animal = null;
        dummy = null;
        if (type == null) return;
        Entity e = type.create(level);
        if (e == null) return;
        e.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        if (e instanceof net.minecraft.world.entity.Mob m) {
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition), MobSpawnType.MOB_SUMMONED, null);
        }
        level.addFreshEntity(e);
        level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.4, e.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
    }

    // =====================================================================================
    //  Ajudantes
    // =====================================================================================
    private int enchant(ServerLevel level, ItemStack rod, boolean lure) {
        var reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var holder = reg.get(lure ? Enchantments.LURE : Enchantments.LUCK_OF_THE_SEA);
        return holder.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, rod)).orElse(0);
    }

    public static boolean isCoreOf(ItemStack s, Element e) {
        var core = CoreRegistries.GUARDIAN_CORES.get(e);
        return core != null && s.is(core.get());
    }

    private int freeOutputSlots() {
        int n = 0;
        for (int i = FIRST_OUT; i < SIZE; i++) if (items.get(i).isEmpty()) n++;
        return n;
    }

    private ItemStack insertOutput(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = FIRST_OUT; i < SIZE && !rest.isEmpty(); i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, rest)) continue;
            int n = Math.min(s.getMaxStackSize() - s.getCount(), rest.getCount());
            if (n > 0) {
                s.grow(n);
                rest.shrink(n);
            }
        }
        for (int i = FIRST_OUT; i < SIZE && !rest.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, rest.split(Math.min(rest.getCount(), rest.getMaxStackSize())));
            }
        }
        setChanged();
        return rest;
    }

    private void flushPending() {
        if (pending.isEmpty()) return;
        List<ItemStack> copy = new ArrayList<>(pending);
        pending.clear();
        for (ItemStack s : copy) {
            ItemStack rest = insertOutput(s);
            if (!rest.isEmpty()) pending.add(rest);
        }
    }

    /** Agachar com a mão vazia: o jogador canaliza parte do próprio recurso para a reserva. */
    public void channel(ServerPlayer player) {
        MagicData d = MagicApi.get(player);
        int space = kind().capacity() - mana;
        if (space <= 0) {
            player.displayClientMessage(Component.translatable("farm.isekai_mahou.machine.full_mana").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int want = Math.min(space, Math.max(30, d.maxMana() / 4));
        int give = player.getAbilities().instabuild ? want : Math.min(want, (int) d.mana());
        if (give <= 0) {
            player.displayClientMessage(MagicApi.noResourceMessage(player).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.getAbilities().instabuild) d.consumeMana(give);
        mana += give;
        setChanged();
        ServerLevel level = player.serverLevel();
        FarmFx.beam(level, player.getEyePosition().add(0, -0.4, 0), Vec3.atCenterOf(worldPosition), FarmFx.dust(MagicApi.resourceColor(player), 0.8F), 0.35);
        FarmFx.sound(level, Vec3.atCenterOf(worldPosition), SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.6F);
        player.displayClientMessage(Component.translatable("farm.isekai_mahou.machine.channeled", give, MagicApi.resourceName(player),
                mana, kind().capacity()).withColor(kind().rgb), true);
    }

    public void dropAll(ServerLevel level) {
        net.minecraft.world.Containers.dropContents(level, worldPosition, this);
        for (ItemStack s : pending) net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s);
        pending.clear();
        releaseAnimal(level);
    }

    // =====================================================================================
    //  Container
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
        return SIZE;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        boolean inputChanged = slot == SLOT_INPUT && !ItemStack.isSameItem(items.get(SLOT_INPUT), stack);
        super.setItem(slot, stack);
        if (inputChanged && level != null && !level.isClientSide) {
            // trocou a muda/vara/núcleo: recomeça o ciclo (a árvore desenhada muda junto)
            if (kind() == MachineKind.ACCELERATED_TREE && length > 0) {
                length = 0;
                progress = 0;
                status = Status.IDLE;
            }
            sync();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_FUEL) return Produce.fuelValue(stack) > 0 || (kind() == MachineKind.ACCELERATED_TREE && stack.is(Items.BONE_MEAL));
        if (slot != SLOT_INPUT) return false;
        return switch (kind()) {
            case STONE_GENERATOR -> Produce.catalyst(stack) != null;
            case ACCELERATED_TREE -> Produce.isPlantable(stack);
            case HATCHERY -> !stack.isEmpty() && (animal == null || !(level instanceof ServerLevel sl)
                    || dummy(sl) instanceof Animal a && a.isFood(stack));
            case FISH_POND -> stack.is(Items.FISHING_ROD);
            case ESSENCE -> element() != null && isCoreOf(stack, element());
        };
    }

    /** Regra simples (sem mundo) para a previsão da tela no cliente; o servidor confere de verdade. */
    public static boolean roughlyAccepts(MachineKind kind, int slot, ItemStack stack) {
        if (slot == SLOT_FUEL) return Produce.fuelValue(stack) > 0 || (kind == MachineKind.ACCELERATED_TREE && stack.is(Items.BONE_MEAL));
        if (slot != SLOT_INPUT) return false;
        return switch (kind) {
            case STONE_GENERATOR -> Produce.catalyst(stack) != null;
            case ACCELERATED_TREE -> Produce.isPlantable(stack);
            case HATCHERY -> !stack.isEmpty();
            case FISH_POND -> stack.is(Items.FISHING_ROD);
            case ESSENCE -> Element.BASE.stream().anyMatch(e -> isCoreOf(stack, e));
        };
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (side) {
            case UP -> TOP;
            case DOWN -> OUTS;
            default -> SIDES;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return index < FIRST_OUT && canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index >= FIRST_OUT;
    }

    @Override
    protected Component getDefaultName() {
        return kind().displayName();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new FarmMachineMenu(id, inventory, this);
    }

    /** Sinal do comparador: quão cheia está a produção. */
    public int comparatorSignal() {
        int filled = 0;
        for (int i = FIRST_OUT; i < SIZE; i++) if (!items.get(i).isEmpty()) filled++;
        return filled == 0 ? 0 : 1 + filled * 14 / (SIZE - FIRST_OUT);
    }

    // =====================================================================================
    //  NBT e sincronização
    // =====================================================================================
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        writeSynced(tag, registries);
        tag.putInt("mana", mana);
        tag.putInt("progress", progress);
        tag.putInt("charges", charges);
        if (chargeElement != null) tag.putString("charge_element", chargeElement.id);
        ListTag list = new ListTag();
        for (ItemStack s : pending) list.add(s.save(registries));
        tag.put("pending", list);
    }

    private void writeSynced(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("status", status.ordinal());
        tag.putInt("length", length);
        tag.putLong("cycle_start", cycleStart);
        if (animal != null) tag.putString("animal", animal.toString());
        if (!display.isEmpty()) tag.put("display", display.save(registries));
        ItemStack in = items.get(SLOT_INPUT);
        if (!in.isEmpty()) tag.put("shown_input", in.copyWithCount(1).save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items", Tag.TAG_LIST)) {
            items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items, registries);
        } else if (tag.contains("shown_input")) {
            // pacote de atualização do cliente: só o item de entrada (para desenhar)
            items.set(SLOT_INPUT, ItemStack.parseOptional(registries, tag.getCompound("shown_input")));
        } else {
            items.set(SLOT_INPUT, ItemStack.EMPTY);
        }
        int st = tag.getInt("status");
        status = st >= 0 && st < Status.values().length ? Status.values()[st] : Status.IDLE;
        length = tag.getInt("length");
        cycleStart = tag.getLong("cycle_start");
        animal = tag.contains("animal") ? ResourceLocation.tryParse(tag.getString("animal")) : null;
        display = tag.contains("display") ? ItemStack.parseOptional(registries, tag.getCompound("display")) : ItemStack.EMPTY;
        mana = tag.getInt("mana");
        progress = tag.getInt("progress");
        charges = tag.getInt("charges");
        chargeElement = tag.contains("charge_element") ? Element.byId(tag.getString("charge_element")) : null;
        pending.clear();
        for (Tag t : tag.getList("pending", Tag.TAG_COMPOUND)) {
            ItemStack s = ItemStack.parseOptional(registries, (CompoundTag) t);
            if (!s.isEmpty()) pending.add(s);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeSynced(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
