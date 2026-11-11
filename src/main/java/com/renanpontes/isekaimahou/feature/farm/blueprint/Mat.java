package com.renanpontes.isekaimahou.feature.farm.blueprint;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.common.Tags;

/**
 * Materiais das Plantas de Casa. Os "de tag" aceitam qualquer variante (carvalho, abeto, cerejeira...) — o canteiro
 * escolhe a primeira que achar no baú e depois prefere a mesma, para a casa não ficar "colcha de retalhos".
 */
public enum Mat {
    /** Limpa o lugar (terra, grama, pedra no caminho). */
    AIR(null, null, Items.AIR),
    PLANKS(ItemTags.PLANKS, null, Items.OAK_PLANKS),
    LOG(ItemTags.LOGS, null, Items.OAK_LOG),
    COBBLE(Tags.Items.COBBLESTONES, null, Items.COBBLESTONE),
    STONE_BRICKS(ItemTags.STONE_BRICKS, null, Items.STONE_BRICKS),
    GLASS_PANE(Tags.Items.GLASS_PANES, null, Items.GLASS_PANE),
    SLAB(ItemTags.WOODEN_SLABS, null, Items.OAK_SLAB),
    STAIRS(ItemTags.WOODEN_STAIRS, null, Items.OAK_STAIRS),
    FENCE(ItemTags.WOODEN_FENCES, null, Items.OAK_FENCE),
    GATE(ItemTags.FENCE_GATES, null, Items.OAK_FENCE_GATE),
    DOOR(ItemTags.WOODEN_DOORS, null, Items.OAK_DOOR),
    TORCH(null, Items.TORCH, Items.TORCH),
    LANTERN(null, Items.LANTERN, Items.LANTERN),
    LADDER(null, Items.LADDER, Items.LADDER),
    CHEST(null, Items.CHEST, Items.CHEST),
    CRAFTING(null, Items.CRAFTING_TABLE, Items.CRAFTING_TABLE),
    FURNACE(null, Items.FURNACE, Items.FURNACE),
    HAY(null, Items.HAY_BLOCK, Items.HAY_BLOCK),
    /** Gasta terra e vira terra arada. */
    FARMLAND(ItemTags.DIRT, null, Items.DIRT),
    /** Gasta um balde d'água (o balde vazio volta para o baú). */
    WATER(null, Items.WATER_BUCKET, Items.WATER_BUCKET);

    private static final String KEY = "farm.isekai_mahou.mat.";

    @Nullable
    private final TagKey<Item> tag;
    @Nullable
    private final Item exact;
    /** Item mostrado nas listas de material. */
    public final Item display;

    Mat(@Nullable TagKey<Item> tag, @Nullable Item exact, Item display) {
        this.tag = tag;
        this.exact = exact;
        this.display = display;
    }

    /** Este item serve para este material? */
    public boolean accepts(ItemStack s) {
        if (s.isEmpty() || this == AIR) return false;
        if (exact != null) return s.is(exact);
        if (tag == null || !s.is(tag)) return false;
        // Só itens que colocam um bloco (as tags às vezes trazem coisas estranhas de outros mods)
        return s.getItem() instanceof BlockItem;
    }

    public Predicate<ItemStack> matcher() {
        return this::accepts;
    }

    public Component displayName() {
        return Component.translatable(KEY + name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Desce/sobe "depois" das paredes no mesmo andar (tochas, escadas de mão, portas, água). */
    public boolean attachable() {
        return this == TORCH || this == LANTERN || this == LADDER || this == DOOR || this == WATER;
    }

    /**
     * Estado no mundo a partir do item gasto. {@code facing} já está girado para o mundo (pode ser null);
     * {@code axis} é o eixo de toras já girado; {@code variant}: laje/degrau de cima = 1, lanterna pendurada = 1.
     */
    public BlockState state(ItemStack taken, @Nullable Direction facing, @Nullable Direction.Axis axis, int variant) {
        Block fromItem = taken.getItem() instanceof BlockItem bi ? bi.getBlock() : Blocks.AIR;
        BlockState s = switch (this) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case FARMLAND -> Blocks.FARMLAND.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case TORCH -> facing == null ? Blocks.TORCH.defaultBlockState()
                    : Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            default -> (fromItem == Blocks.AIR ? ((BlockItem) display).getBlock() : fromItem).defaultBlockState();
        };
        if (facing != null && this != TORCH && s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        if (axis != null && s.hasProperty(BlockStateProperties.AXIS)) s = s.setValue(BlockStateProperties.AXIS, axis);
        if (s.hasProperty(BlockStateProperties.SLAB_TYPE)) s = s.setValue(BlockStateProperties.SLAB_TYPE, variant == 1 ? SlabType.TOP : SlabType.BOTTOM);
        if (s.hasProperty(BlockStateProperties.HALF)) s = s.setValue(BlockStateProperties.HALF, variant == 1 ? Half.TOP : Half.BOTTOM);
        if (s.hasProperty(BlockStateProperties.HANGING)) s = s.setValue(BlockStateProperties.HANGING, variant == 1);
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) s = s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        if (s.hasProperty(BlockStateProperties.DOOR_HINGE)) s = s.setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT);
        return s;
    }

    /** Este bloco já no mundo conta como "feito" para este material? */
    public boolean satisfiedBy(BlockState current) {
        return switch (this) {
            case AIR -> current.isAir();
            case FARMLAND -> current.is(Blocks.FARMLAND);
            case WATER -> current.is(Blocks.WATER);
            case TORCH -> current.is(Blocks.TORCH) || current.is(Blocks.WALL_TORCH);
            default -> {
                Item item = current.getBlock().asItem();
                yield item != Items.AIR && accepts(new ItemStack(item));
            }
        };
    }
}
