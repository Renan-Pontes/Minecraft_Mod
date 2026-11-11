package com.renanpontes.isekaimahou.feature.hueco_mundo.block;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmText;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmTravel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Altar de entrada de um reino (Altar da Garganta / Pedra do Círculo das Fadas). Fora do reino é um ritual: à noite, com a
 * oferenda na mão (Garganta: Essência das Sombras ou Fragmento de Máscara; Fadas: Essência da Luz, Pó de Fada ou
 * Pétala-da-Lua), 3 s de cântico abrem o portal por 30 s. Dentro do reino o altar da chegada abre a volta de graça.
 * Inquebrável (faz parte das estruturas protegidas).
 */
public class GateAltarBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);
    private static final int RITUAL_TICKS = 60, GATE_LIFE = 600;
    private final HmRealm realm;

    public GateAltarBlock(HmRealm realm, Properties props) {
        super(props);
        this.realm = realm;
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        activate((ServerLevel) level, pos, (ServerPlayer) player, stack);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        activate((ServerLevel) level, pos, (ServerPlayer) player, ItemStack.EMPTY);
        return InteractionResult.CONSUME;
    }

    private void activate(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        Vec3 base = gateBase(level, pos);
        if (level.dimension().equals(realm.key)) {
            if (HmTravel.openGate(level, base, realm, 300, player.getYRot()) == null) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.hueco_mundo.gate_already_open"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.hueco_mundo.return_open").withColor(realm.color), true);
            }
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        long time = level.getDayTime() % 24000L;
        boolean night = time >= 12500L && time <= 23500L;
        if (!creative && !night) {
            player.displayClientMessage(HmText.of("message.isekai_mahou.hueco_mundo", realm.id, "need_night"), true);
            level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 0.6F);
            return;
        }
        if (!creative && !isOffering(stack)) {
            player.displayClientMessage(HmText.of("message.isekai_mahou.hueco_mundo", realm.id, "need_offering"), true);
            level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 0.6F);
            return;
        }
        if (!level.getEntitiesOfClass(com.renanpontes.isekaimahou.feature.hueco_mundo.entity.RealmGate.class,
                new net.minecraft.world.phys.AABB(pos).inflate(6), g -> g.isAlive()).isEmpty()
                || level.getBlockTicks().hasScheduledTick(pos, this)) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.hueco_mundo.gate_already_open"), true);
            return;
        }
        if (!creative) stack.shrink(1);
        // Marca "ritual em andamento" com um tick agendado (evita dois rituais seguidos no mesmo altar).
        level.scheduleTick(pos, this, RITUAL_TICKS);
        player.displayClientMessage(HmText.of("message.isekai_mahou.hueco_mundo", realm.id, "ritual").withColor(realm.color), true);
        level.playSound(null, pos, realm == HmRealm.HOLLOW ? SoundEvents.SOUL_ESCAPE.value() : SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 1.4F, 0.6F);
        Vec3 c = Vec3.atBottomCenterOf(pos).add(0, 1, 0);
        for (int t = 0; t < RITUAL_TICKS; t += 6) {
            final int tt = t;
            BossKit.schedule(level, t, () -> ritualFx(level, c, tt));
        }
    }

    /** Fim do cântico: o portal se abre (rasgo no céu / anel de pétalas). */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.dimension().equals(realm.key)) return;
        Vec3 base = gateBase(level, pos);
        HmTravel.openGate(level, base, realm, GATE_LIFE, 0F);
        BossKit.shake(level, base, 24, 0.8F, 20);
        Component msg = HmText.of("message.isekai_mahou.hueco_mundo", realm.id, "opened").withColor(realm.color);
        for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(base) < 32 * 32)) p.displayClientMessage(msg, true);
    }

    private void ritualFx(ServerLevel level, Vec3 c, int t) {
        double r = 2.5 - t / (double) RITUAL_TICKS * 1.8;
        if (realm == HmRealm.HOLLOW) {
            HmFx.ring(level, c, r, 20, ParticleTypes.SOUL_FIRE_FLAME, 0.0);
            level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 3 + t / 20.0, c.z, 6, 0.3, 0.3, 0.3, 0.01);
            if (t % 18 == 0) level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 1.2F, 0.6F);
        } else {
            HmFx.ring(level, c, r, 20, ParticleTypes.CHERRY_LEAVES, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1, c.z, 4, 0.6, 0.6, 0.6, 0.02);
            if (t % 18 == 0) level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 1.3F);
        }
    }

    /** Pés do portal: 2 blocos à frente (sul) do altar, no chão. */
    private static Vec3 gateBase(Level level, BlockPos altar) {
        BlockPos at = altar.south(3);
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos q = at.offset(0, dy, 0);
            if (level.getBlockState(q).getCollisionShape(level, q).isEmpty() && !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty()) {
                return Vec3.atBottomCenterOf(q);
            }
        }
        return Vec3.atBottomCenterOf(at);
    }

    private boolean isOffering(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (realm == HmRealm.HOLLOW) {
            return item == essence(Element.SHADOW) || item == HmRegistries.HOLLOW_MASK_FRAGMENT.get();
        }
        return item == essence(Element.LIGHT) || item == HmRegistries.FEY_DUST.get() || item == HmRegistries.MOONPETAL_ITEM.get();
    }

    private static Item essence(Element e) {
        var holder = CoreRegistries.ESSENCES.get(e);
        return holder != null ? holder.get() : null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.9, z = pos.getZ() + 0.5;
        if (realm == HmRealm.HOLLOW) {
            if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (random.nextDouble() - 0.5) * 0.6, y,
                    z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.ASH, x, y + 0.5, z, 0, 0, 0);
            // À noite, no Mundo Normal, a "rachadura no céu" aparece sobre o altar (fenda vertical em zigue-zague).
            long time = level.getDayTime() % 24000L;
            if (!level.dimension().equals(realm.key) && time >= 12500L && time <= 23500L) {
                double h = random.nextDouble() * 7.0;
                double zig = Math.sin(h * 2.3) * 0.35;
                double gz = z + 3.0;
                level.addParticle(ParticleTypes.SQUID_INK, x + zig, y + 9.0 + h, gz, 0, -0.01, 0);
                if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.REVERSE_PORTAL, x + zig, y + 9.0 + h, gz, 0, 0, 0);
            }
        } else {
            if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.8, y,
                    z + (random.nextDouble() - 0.5) * 0.8, 0, 0.03, 0);
            if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y + 0.6, z, 0, 0, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(HmText.of("block", IsekaiMahou.MODID, realm == HmRealm.HOLLOW ? "garganta_altar" : "fey_ring_stone", "hint")
                .withStyle(ChatFormatting.GRAY));
    }
}
