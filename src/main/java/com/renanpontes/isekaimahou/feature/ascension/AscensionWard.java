package com.renanpontes.isekaimahou.feature.ascension;

import com.renanpontes.isekaimahou.feature.ascension.block.AscensionCoreBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/**
 * v1.0 — proteção e barreira da Ascensão:
 * <ul>
 *   <li><b>Área do altar</b>: em volta de um altar PÚBLICO (sempre) e dentro do domo de uma Tribulação em andamento
 *       (em qualquer altar), ninguém quebra, coloca, usa balde/isqueiro, raspa/ara blocos fora do criativo;
 *       explosões, pistões e mobs não mexem no terreno. As magias já respeitam a área pelo {@code Destruction}.</li>
 *   <li><b>Barreira</b>: quem ascende não sai do domo — pérolas, fruta do coro e outros teleportes com evento para
 *       fora são cancelados, e a troca de dimensão também (o resto é trazido de volta pelo tick da Tribulação).</li>
 * </ul>
 * No servidor a área vem do espelho {@link AscensionAltars} e das zonas da {@link Tribulation}; no cliente, dos Núcleos
 * carregados por perto (para não "quebrar e voltar" nem prever água/blocos que o servidor vai negar).
 */
public final class AscensionWard {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AscensionWard::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(AscensionWard::onPlace);
        NeoForge.EVENT_BUS.addListener(AscensionWard::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(AscensionWard::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(AscensionWard::onToolModify);
        NeoForge.EVENT_BUS.addListener(AscensionWard::onFluidPlace);
        NeoForge.EVENT_BUS.addListener(AscensionWard::onDetonate);
        NeoForge.EVENT_BUS.addListener(AscensionWard::onPiston);
        NeoForge.EVENT_BUS.addListener(AscensionWard::onGrief);
        NeoForge.EVENT_BUS.addListener(AscensionWard::onTeleport);
        NeoForge.EVENT_BUS.addListener(AscensionWard::onTravel);
    }

    // =====================================================================================
    //  Consulta
    // =====================================================================================
    /** A posição está numa área protegida da Ascensão (altar público ou domo de uma Tribulação ativa)? */
    public static boolean isProtected(LevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof Level level)) return false;
        if (!level.isClientSide()) {
            return AscensionAltars.covers(level.dimension(), pos) || Tribulation.zoneCovers(level.dimension(), pos);
        }
        return clientCovers(level, pos);
    }

    /** Cliente: procura Núcleos públicos (ou com ritual) nos chunks carregados em volta (poucos block entities). */
    private static boolean clientCovers(Level level, BlockPos pos) {
        int r = AscensionAltars.AREA_RADIUS;
        int cx0 = (pos.getX() - r) >> 4, cx1 = (pos.getX() + r) >> 4;
        int cz0 = (pos.getZ() - r) >> 4, cz1 = (pos.getZ() + r) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof AscensionCoreBlockEntity core)) continue;
                    BlockPos c = core.getBlockPos();
                    long dx = c.getX() - pos.getX(), dz = c.getZ() - pos.getZ();
                    if (core.isPublic()) {
                        if (dx * dx + dz * dz <= (long) r * r && pos.getY() >= c.getY() - AscensionPattern.BARRIER_BELOW
                                && pos.getY() <= c.getY() + AscensionAltars.AREA_UP) return true;
                    } else if (core.isRitualRunning() && core.boundary() > 0) {
                        double rr = core.boundary() + 1.0;
                        if (dx * dx + dz * dz <= rr * rr && pos.getY() >= c.getY() - AscensionPattern.BARRIER_BELOW
                                && pos.getY() <= c.getY() + AscensionPattern.domeHeight(core.boundary()) + 1) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean exempt(Player player) {
        return player != null && player.getAbilities().instabuild;
    }

    private static void warn(Player player) {
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.ascension.area_protected")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    // =====================================================================================
    //  Construir / quebrar
    // =====================================================================================
    /** Minerar não avança (sem o "quebra e volta"); o {@code BreakEvent} do módulo cancela no servidor. */
    private static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        Player p = e.getEntity();
        if (exempt(p)) return;
        e.getPosition().ifPresent(pos -> {
            if (pos.getY() != -1 && isProtected(p.level(), pos)) e.setNewSpeed(0F);
        });
    }

    /** Quebrar na área (qualquer bloco, não só as peças do padrão). Chamado pelo módulo. */
    static boolean denyBreak(Player player, LevelAccessor level, BlockPos pos) {
        if (player == null || exempt(player)) return false;
        return isProtected(level, pos);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        Entity entity = e.getEntity();
        if (entity instanceof Player p && exempt(p)) return;
        if (!isProtected(e.getLevel(), e.getPos())) return;
        e.setCanceled(true);
        if (entity instanceof Player p) warn(p);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        ItemStack stack = e.getItemStack();
        if (exempt(p) || stack.isEmpty()) return;
        boolean risky = stack.getItem() instanceof BucketItem || stack.getItem() instanceof FlintAndSteelItem
                || stack.getItem() instanceof FireChargeItem;
        boolean block = stack.getItem() instanceof BlockItem;
        if (!risky && !block) return;
        BlockPos pos = e.getPos();
        BlockPos target = pos;
        if (block) {
            BlockPlaceContext ctx = new BlockPlaceContext(p, e.getHand(), stack, e.getHitVec());
            target = ctx.getClickedPos();
        }
        Direction face = e.getFace();
        boolean hit = isProtected(e.getLevel(), target) || risky && face != null && isProtected(e.getLevel(), pos.relative(face));
        if (!hit) return;
        // O bloco continua usável (portas, baús, o próprio Núcleo); só o item não age.
        e.setUseItem(TriState.FALSE);
        if (risky) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
        warn(p);
    }

    /** Baldes agem pelo "usar item" (mira com raio): confere o bloco mirado. */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        Player p = e.getEntity();
        if (exempt(p) || !(e.getItemStack().getItem() instanceof BucketItem)) return;
        double reach = p.blockInteractionRange();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1F).scale(reach));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, p));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        if (!isProtected(p.level(), pos) && !isProtected(p.level(), pos.relative(hit.getDirection()))) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
        warn(p);
    }

    /** Raspar tronco, arar, fazer caminho, apagar fogueira... também é mexer no altar. */
    private static void onToolModify(BlockEvent.BlockToolModificationEvent e) {
        Player p = e.getPlayer();
        if (exempt(p) || !isProtected(e.getLevel(), e.getPos())) return;
        e.setCanceled(true);
        if (!e.isSimulated()) warn(p);
    }

    /** Lava + água virando pedra/obsidiana dentro da área. */
    private static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent e) {
        if (isProtected(e.getLevel(), e.getPos())) e.setCanceled(true);
    }

    private static void onDetonate(ExplosionEvent.Detonate e) {
        Level level = e.getLevel();
        if (level.isClientSide()) return;
        e.getAffectedBlocks().removeIf(p -> isProtected(level, p));
    }

    /** Pistões não empurram blocos para dentro (nem tiram de dentro) da área. */
    private static void onPiston(PistonEvent.Pre e) {
        LevelAccessor level = e.getLevel();
        if (level.isClientSide()) return;
        BlockPos pos = e.getPos();
        Direction dir = e.getDirection();
        for (int i = 0; i <= 13; i++) {
            if (isProtected(level, pos.relative(dir, i))) {
                e.setCanceled(true);
                return;
            }
        }
    }

    /** Enderman pegando bloco, creeper, zumbi quebrando porta... nada disso dentro da área. */
    private static void onGrief(EntityMobGriefingEvent e) {
        Entity entity = e.getEntity();
        if (entity.level().isClientSide()) return;
        if (isProtected(entity.level(), entity.blockPosition())) e.setCanGrief(false);
    }

    // =====================================================================================
    //  Barreira: quem ascende não sai
    // =====================================================================================
    private static void onTeleport(EntityTeleportEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.getEntity().level().isClientSide()) return;
        Tribulation t = Tribulation.byOwner(sp.getUUID());
        if (t == null || t.finished() || sp.level() != t.level()) return;
        if (t.insideBarrier(e.getTarget())) return;
        e.setCanceled(true);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.ascension.barrier_teleport")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    private static void onTravel(EntityTravelToDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Tribulation t = Tribulation.byOwner(sp.getUUID());
        if (t == null || t.finished()) return;
        e.setCanceled(true);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.ascension.barrier_teleport")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private AscensionWard() {}
}
