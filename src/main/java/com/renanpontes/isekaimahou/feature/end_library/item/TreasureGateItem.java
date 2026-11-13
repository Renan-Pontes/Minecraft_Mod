package com.renanpontes.isekaimahou.feature.end_library.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.BgFx;
import com.renanpontes.isekaimahou.feature.end_library.entity.TreasureBlade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Portão do Tesouro (inspirado no "Gate of Babylon"): abre portais dourados nas costas do jogador e cada um dispara
 * uma arma do tesouro na direção para onde ele olha. Quantas armas e quanto dano crescem com o Círculo.
 * Custo: 35 de mana; recarga 20 s.
 */
public class TreasureGateItem extends Item {
    public static final int MANA_COST = 35;
    private static final int COOLDOWN = 400;
    private static final int GOLD = 0xFFD24A;
    private static final Item[] ARSENAL = {Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.TRIDENT, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.IRON_AXE, Items.NETHERITE_AXE};

    public TreasureGateItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        MagicData data = MagicApi.get(player);
        if (!player.getAbilities().instabuild && !data.consumeMana(MANA_COST)) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.end_library.no_mana", MANA_COST)
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        int circle = Math.max(1, data.circle());
        int count = Math.min(12, 4 + circle);
        float damage = 5F + circle * 1.2F;
        Vec3 aim = aimPoint(sl, player);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 1.6F);
        player.displayClientMessage(Component.translatable("item.isekai_mahou.void_treasure_gate.opened").withStyle(ChatFormatting.GOLD), true);
        for (int i = 0; i < count; i++) {
            int index = i;
            BossKit.schedule(sl, i * 3, () -> fire(sl, player, aim, index, damage));
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    /** Onde o jogador está mirando (até 48 blocos). */
    private static Vec3 aimPoint(ServerLevel sl, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(48));
        HitResult hit = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** Um portal atrás do jogador (posição em leque) + uma arma disparada dele. */
    private static void fire(ServerLevel sl, Player player, Vec3 aim, int index, float damage) {
        if (!player.isAlive() || player.level() != sl) return;
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        double spread = ((index % 5) - 2) * 1.1;
        double up = 1.6 + (index / 5) * 1.0 + sl.random.nextDouble() * 0.4;
        Vec3 gate = player.position().add(look.scale(-1.2)).add(side.scale(spread)).add(0, up, 0);
        // Ondulação dourada do portal (anel de pó virado para frente).
        for (int k = 0; k < 14; k++) {
            double a = Math.PI * 2 * k / 14;
            Vec3 p = gate.add(side.scale(Math.cos(a) * 0.6)).add(0, Math.sin(a) * 0.6, 0);
            sl.sendParticles(BgFx.dust(GOLD, 1.0F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        Vec3 dir = aim.subtract(gate);
        if (dir.lengthSqr() < 1.0E-3) dir = player.getLookAngle();
        Vec3 vel = dir.normalize().scale(1.9);
        Item shown = ARSENAL[sl.random.nextInt(ARSENAL.length)];
        sl.addFreshEntity(TreasureBlade.create(sl, player, gate, vel, damage, shown));
        sl.playSound(null, gate.x, gate.y, gate.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8F, 1.2F + sl.random.nextFloat() * 0.3F);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.void_treasure_gate.desc").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("item.isekai_mahou.void_treasure_gate.use", MANA_COST).withStyle(ChatFormatting.GRAY));
    }
}
