package com.renanpontes.isekaimahou.feature.farm.blueprint;

import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.feature.farm.FarmFx;
import com.renanpontes.isekaimahou.feature.farm.FarmRegistries;
import com.renanpontes.isekaimahou.feature.farm.build.Placer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Planta de Casa: clique direito no chão monta o Canteiro de Obras (a obra fica na sua frente, com a fachada virada
 * para você — segurando a planta, o contorno aparece antes). O canteiro constrói sozinho com os blocos dos baús a até
 * {@link ConstructionSiteBlockEntity#CHEST_RANGE} blocos e devolve a planta no fim (dá para reusar).
 */
public class BlueprintItem extends Item {
    public final BlueprintKind kind;

    public BlueprintItem(BlueprintKind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedFace() == Direction.UP ? ctx.getClickedPos().above() : ctx.getClickedPos().relative(ctx.getClickedFace());
        Direction look = sp.getDirection();
        if (!Placer.replaceable(level, pos) || !Placer.canEdit(level, sp, pos)) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.site.blocked").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        AABB box = kind.template().bounds(pos, look);
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.site.height").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        BlockState state = FarmRegistries.CONSTRUCTION_SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, look);
        if (!Placer.place(level, sp, pos, state)) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.site.blocked").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (level.getBlockEntity(pos) instanceof ConstructionSiteBlockEntity be) be.setup(kind, sp.getUUID());
        if (!sp.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        Vec3 c = Vec3.atBottomCenterOf(pos);
        FarmFx.sound(level, c, SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F);
        FarmFx.sound(level, c, SoundEvents.WOOD_PLACE, 1.0F, 1.0F);
        FarmFx.ring(level, c.add(0, 0.1, 0), 1.0, FarmFx.dust(kind.rgb, 1.0F), 16);
        sp.displayClientMessage(Component.translatable("farm.isekai_mahou.site.placed", kind.displayName(),
                ConstructionSiteBlockEntity.CHEST_RANGE).withColor(kind.rgb), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        Template t = kind.template();
        tooltip.add(kind.description().withStyle(ChatFormatting.GRAY));
        AABB b = t.bounds(BlockPos.ZERO, Direction.NORTH);
        tooltip.add(Component.translatable("farm.isekai_mahou.blueprint.size", (int) b.getXsize(), (int) b.getZsize(), (int) b.getYsize())
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("farm.isekai_mahou.blueprint.materials").withColor(kind.rgb));
        for (Map.Entry<Mat, Integer> e : t.counts().entrySet()) {
            tooltip.add(Component.literal("  ").append(Component.translatable("farm.isekai_mahou.blueprint.material_line",
                    e.getValue(), e.getKey().displayName())).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("farm.isekai_mahou.blueprint.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
