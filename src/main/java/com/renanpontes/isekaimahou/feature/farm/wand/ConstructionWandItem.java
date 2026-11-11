package com.renanpontes.isekaimahou.feature.farm.wand;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.farm.FarmFx;
import com.renanpontes.isekaimahou.feature.farm.FarmRegistries;
import com.renanpontes.isekaimahou.feature.farm.build.BuildJob;
import com.renanpontes.isekaimahou.feature.farm.build.BuildMaterial;
import com.renanpontes.isekaimahou.feature.farm.build.BuildQueue;
import com.renanpontes.isekaimahou.feature.farm.build.Placer;
import com.renanpontes.isekaimahou.feature.farm.build.Supply;
import com.renanpontes.isekaimahou.feature.farm.build.UndoHistory;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Varinha de Construção ("Construção Arcana"). Controles:
 * <ul>
 *   <li>Clique direito num bloco: marca o 1º canto, depois o 2º (um 3º clique recomeça).</li>
 *   <li>Clique direito no ar: executa o modo (Preencher, Parede, Piso, Trocar, Copiar) / Desfazer.</li>
 *   <li>Colar: clique direito num bloco cola a prancheta ali (gira com você).</li>
 *   <li>Agachar + clique no ar: troca o modo. Agachar + clique num bloco: escolhe o material; num baú: liga o baú.</li>
 * </ul>
 * Gasta {@link #MANA_PER_BLOCK} do recurso por bloco + os blocos do inventário/baú ligado (criativo: de graça).
 */
public class ConstructionWandItem extends Item {
    public static final float MANA_PER_BLOCK = 1.0F;
    /** Lado máximo da seleção e volume máximo de uma ordem. */
    public static final int MAX_SIDE = 32;
    public static final int MAX_VOLUME = 8192;
    /** Blocos por tick desta obra (a fila global ainda limita o total). */
    public static final int SPEED = 24;

    public ConstructionWandItem(Properties props) {
        super(props);
    }

    public static WandData data(ItemStack stack) {
        WandData d = stack.get(FarmRegistries.WAND.get());
        return d == null ? WandData.EMPTY : d;
    }

    private static void set(ItemStack stack, WandData d) {
        stack.set(FarmRegistries.WAND.get(), d);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return data(stack).hasSelection();
    }

    // =====================================================================================
    //  Clique num bloco
    // =====================================================================================
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        WandData d = data(stack);
        BlockPos pos = ctx.getClickedPos();

        if (sp.isShiftKeyDown()) {
            if (Supply.handler(level, pos) != null && level.getBlockEntity(pos) != null) {
                boolean same = d.link().isPresent() && d.link().get().pos().equals(pos) && d.link().get().dimension().equals(level.dimension());
                set(stack, d.withLink(same ? null : GlobalPos.of(level.dimension(), pos)));
                sp.displayClientMessage(Component.translatable(same ? "farm.isekai_mahou.wand.unlinked" : "farm.isekai_mahou.wand.linked",
                        level.getBlockState(pos).getBlock().getName(), Supply.LINK_RANGE).withColor(0x9BE07A), true);
                FarmFx.sound(level, Vec3.atCenterOf(pos), SoundEvents.CHEST_LOCKED, 0.6F, same ? 0.8F : 1.4F);
                FarmFx.beam(level, sp.getEyePosition().add(0, -0.3, 0), Vec3.atCenterOf(pos), FarmFx.dust(0x9BE07A, 0.8F), 0.4);
                return InteractionResult.CONSUME;
            }
            Block b = level.getBlockState(pos).getBlock();
            if (!BuildMaterial.valid(b)) {
                sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.bad_material").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            set(stack, d.withMaterial(BuiltInRegistries.BLOCK.getKey(b)));
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.material", b.getName()).withColor(FarmFx.BUILD_RGB), true);
            FarmFx.sound(level, Vec3.atCenterOf(pos), SoundEvents.AMETHYST_CLUSTER_HIT, 0.7F, 1.5F);
            level.sendParticles(FarmFx.dust(FarmFx.BUILD_RGB, 1.0F), pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 10, 0.3, 0.05, 0.3, 0);
            return InteractionResult.CONSUME;
        }

        WandMode mode = d.wandMode();
        if (mode == WandMode.PASTE) {
            paste(sp, level, stack, ctx.getClickedPos().relative(ctx.getClickedFace()));
            return InteractionResult.CONSUME;
        }
        if (mode == WandMode.UNDO) {
            undo(sp);
            return InteractionResult.CONSUME;
        }
        // Marcar cantos
        if (d.a().isEmpty() || d.b().isPresent()) {
            set(stack, d.withCorners(pos, null));
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.corner1", pos.getX(), pos.getY(), pos.getZ())
                    .withColor(FarmFx.BUILD_RGB), true);
            FarmFx.sound(level, Vec3.atCenterOf(pos), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.2F);
        } else {
            WandData nd = d.withCorners(d.a().get(), pos);
            set(stack, nd);
            BlockPos s = nd.size();
            long vol = (long) s.getX() * s.getY() * s.getZ();
            boolean tooBig = !fits(s);
            Component msg = Component.translatable("farm.isekai_mahou.wand.corner2", s.getX(), s.getY(), s.getZ(), vol)
                    .withColor(tooBig ? 0xFF7070 : FarmFx.BUILD_RGB);
            sp.displayClientMessage(msg, true);
            if (tooBig) {
                sp.sendSystemMessage(Component.translatable("farm.isekai_mahou.wand.too_big", MAX_SIDE, MAX_VOLUME).withStyle(ChatFormatting.RED));
            }
            FarmFx.sound(level, Vec3.atCenterOf(pos), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.6F);
        }
        level.sendParticles(FarmFx.dust(d.wandMode().rgb, 1.2F), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14, 0.45, 0.45, 0.45, 0);
        return InteractionResult.CONSUME;
    }

    private static boolean fits(BlockPos s) {
        return s.getX() <= MAX_SIDE && s.getY() <= MAX_SIDE && s.getZ() <= MAX_SIDE
                && (long) s.getX() * s.getY() * s.getZ() <= MAX_VOLUME;
    }

    // =====================================================================================
    //  Clique no ar
    // =====================================================================================
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        WandData d = data(stack);
        if (sp.isShiftKeyDown()) {
            WandMode next = d.wandMode().next();
            set(stack, d.withMode(next));
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.mode", next.displayName()).append(" — ")
                    .append(next.hint().withStyle(ChatFormatting.GRAY)), true);
            FarmFx.sound(sl, sp.position(), SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.3F + next.ordinal() * 0.05F);
            return InteractionResultHolder.consume(stack);
        }
        switch (d.wandMode()) {
            case UNDO -> undo(sp);
            case PASTE -> sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.paste_aim").withStyle(ChatFormatting.YELLOW), true);
            case COPY -> copy(sp, sl, stack, d);
            default -> execute(sp, sl, d);
        }
        return InteractionResultHolder.consume(stack);
    }

    private static boolean checkSelection(ServerPlayer sp, WandData d) {
        if (!d.hasSelection()) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.no_selection").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        if (!d.a().get().closerThan(sp.blockPosition(), 96)) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.far").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!fits(d.size())) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.too_big", MAX_SIDE, MAX_VOLUME).withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    /** Preencher / Parede / Piso / Trocar. */
    private static void execute(ServerPlayer sp, ServerLevel level, WandData d) {
        if (!checkSelection(sp, d)) return;
        Block material = BuildMaterial.forPlayer(sp, d.material().orElse(null));
        if (material == null) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.no_material").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        BlockPos a = d.a().get(), b = d.b().get();
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        BlockState state = BuildMaterial.orient(material, sp.getDirection());
        WandMode mode = d.wandMode();
        Block expect = mode == WandMode.SWAP ? level.getBlockState(a).getBlock() : null;
        if (mode == WandMode.SWAP && (expect == material || level.getBlockState(a).isAir())) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.swap_same").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        List<BuildJob.Op> ops = new ArrayList<>();
        for (int y = y0; y <= y1; y++) {
            if (mode == WandMode.FLOOR && y != y0) break;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (mode == WandMode.WALL && x != x0 && x != x1 && z != z0 && z != z1) continue;
                    BlockPos p = new BlockPos(x, y, z);
                    if (mode == WandMode.SWAP) {
                        if (level.getBlockState(p).getBlock() == expect) ops.add(new BuildJob.Op(p, state, BuildJob.Kind.SWAP, expect));
                    } else {
                        ops.add(new BuildJob.Op(p, state, BuildJob.Kind.PLACE, null));
                    }
                }
            }
        }
        start(sp, ops, mode.rgb);
    }

    private static void start(ServerPlayer sp, List<BuildJob.Op> ops, int rgb) {
        if (ops.isEmpty()) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.nothing").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        BuildJob job = new BuildJob(sp, ops, sp.getEyePosition().add(sp.getLookAngle().scale(0.6)).add(0, -0.25, 0), rgb,
                MANA_PER_BLOCK, SPEED, true);
        if (!BuildQueue.add(job)) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.build.busy").withStyle(ChatFormatting.RED), true);
            return;
        }
        sp.displayClientMessage(Component.translatable("farm.isekai_mahou.build.started", ops.size()).withColor(rgb), true);
        FarmFx.sound(sp.serverLevel(), sp.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.6F, 1.5F);
    }

    private static void copy(ServerPlayer sp, ServerLevel level, ItemStack stack, WandData d) {
        if (!checkSelection(sp, d)) return;
        BlockPos s = d.size();
        if (s.getX() > Clipboard.MAX_SIDE || s.getY() > Clipboard.MAX_SIDE || s.getZ() > Clipboard.MAX_SIDE
                || s.getX() * s.getY() * s.getZ() > Clipboard.MAX_VOLUME) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.copy_too_big", Clipboard.MAX_SIDE).withStyle(ChatFormatting.RED), true);
            return;
        }
        BlockPos a = d.a().get(), b = d.b().get();
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        List<BlockState> states = new ArrayList<>(s.getX() * s.getY() * s.getZ());
        int solid = 0;
        for (int y = 0; y < s.getY(); y++) {
            for (int z = 0; z < s.getZ(); z++) {
                for (int x = 0; x < s.getX(); x++) {
                    BlockState st = level.getBlockState(min.offset(x, y, z));
                    // Blocos com inventário/dados e líquidos não são copiados (só a "casca" da construção)
                    if (level.getBlockEntity(min.offset(x, y, z)) != null || st.getBlock() instanceof LiquidBlock
                            || st.getBlock().asItem() == Items.AIR || Placer.isSecondaryPart(st)) {
                        st = Blocks.AIR.defaultBlockState();
                    }
                    if (!st.isAir()) solid++;
                    states.add(st);
                }
            }
        }
        Clipboard.save(sp, s.getX(), s.getY(), s.getZ(), sp.getDirection(), states);
        set(stack, d.withClip(s, sp.getDirection()).withMode(WandMode.PASTE));
        sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.copied", s.getX(), s.getY(), s.getZ(), solid)
                .withColor(WandMode.COPY.rgb), true);
        FarmFx.sound(level, sp.position(), SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.2F);
        FarmFx.ring(level, Vec3.atCenterOf(a), 0.8, FarmFx.dust(WandMode.COPY.rgb, 1.0F), 12);
        FarmFx.ring(level, Vec3.atCenterOf(b), 0.8, FarmFx.dust(WandMode.COPY.rgb, 1.0F), 12);
    }

    private static void paste(ServerPlayer sp, ServerLevel level, ItemStack stack, BlockPos origin) {
        Clipboard.Loaded clip = Clipboard.load(sp);
        if (clip == null) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.no_clip").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        Direction facing = sp.getDirection();
        Rotation r = Clipboard.rotation(clip.facing(), facing);
        List<BuildJob.Op> ops = new ArrayList<>();
        for (int y = 0; y < clip.h(); y++) {
            for (int z = 0; z < clip.d(); z++) {
                for (int x = 0; x < clip.w(); x++) {
                    BlockState st = clip.at(x, y, z);
                    if (st.isAir()) continue;
                    BlockPos p = origin.offset(Clipboard.place(x, y, z, clip.w(), clip.d(), r, facing));
                    ops.add(new BuildJob.Op(p, st.rotate(level, p, r), BuildJob.Kind.PLACE, null));
                }
            }
        }
        start(sp, ops, WandMode.PASTE.rgb);
    }

    static void undo(ServerPlayer sp) {
        UndoHistory.Record r = UndoHistory.pop(sp.getUUID());
        if (r == null) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.no_undo").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!r.dimension().equals(sp.level().dimension())) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.undo_dimension").withStyle(ChatFormatting.RED), true);
            return;
        }
        BuildJob job = new BuildJob(sp, UndoHistory.undoOps(r), sp.getEyePosition(), WandMode.UNDO.rgb, 0F, SPEED * 2, false);
        if (!BuildQueue.add(job)) {
            sp.displayClientMessage(Component.translatable("farm.isekai_mahou.build.busy").withStyle(ChatFormatting.RED), true);
            UndoHistory.push(sp.getUUID(), r); // devolve ao histórico para tentar de novo
            return;
        }
        sp.displayClientMessage(Component.translatable("farm.isekai_mahou.wand.undoing", r.entries().size()).withColor(WandMode.UNDO.rgb), true);
        FarmFx.sound(sp.serverLevel(), sp.position(), SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.6F);
    }

    // =====================================================================================
    //  Dica
    // =====================================================================================
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        WandData d = data(stack);
        tooltip.add(Component.translatable("farm.isekai_mahou.wand.mode", d.wandMode().displayName()));
        tooltip.add(d.wandMode().hint().withStyle(ChatFormatting.GRAY));
        d.material().ifPresent(m -> tooltip.add(Component.translatable("farm.isekai_mahou.wand.material",
                BuiltInRegistries.BLOCK.get(m).getName()).withStyle(ChatFormatting.AQUA)));
        d.link().ifPresent(l -> tooltip.add(Component.translatable("farm.isekai_mahou.wand.link_at",
                l.pos().getX(), l.pos().getY(), l.pos().getZ()).withStyle(ChatFormatting.GREEN)));
        if (d.hasClip()) {
            tooltip.add(Component.translatable("farm.isekai_mahou.wand.clip", d.clip().getX(), d.clip().getY(), d.clip().getZ())
                    .withColor(WandMode.COPY.rgb));
        }
        tooltip.add(Component.translatable("farm.isekai_mahou.wand.help1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("farm.isekai_mahou.wand.help2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("farm.isekai_mahou.wand.help3").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("farm.isekai_mahou.wand.cost", MANA_PER_BLOCK).withStyle(ChatFormatting.DARK_AQUA));
    }

    @Nullable
    public static WandData held(Player p) {
        ItemStack s = p.getMainHandItem();
        return s.getItem() instanceof ConstructionWandItem ? data(s) : null;
    }
}
