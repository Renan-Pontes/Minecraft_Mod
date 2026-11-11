package com.renanpontes.isekaimahou.feature.class_steelball.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

/**
 * Bússola do Santo (lição I): aponta a Sepultura do Santo mais próxima — distância, direção e um rastro de pó dourado
 * no rumo certo. A busca é pesada, então a bússola recarrega por 5 s.
 */
public class SaintCompassItem extends Item {
    public static final TagKey<Structure> TOMBS = TagKey.create(Registries.STRUCTURE, IsekaiMahou.id("steelball_saint_tomb"));
    private static final int COOLDOWN = 100;
    private static final String DIR_KEY = "message.isekai_mahou.steelball.compass.dir.";

    public SaintCompassItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        player.getCooldowns().addCooldown(this, COOLDOWN);
        BlockPos found = sl.findNearestMapStructure(TOMBS, sp.blockPosition(), 64, false);
        if (found == null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.compass.none").withStyle(ChatFormatting.GRAY), true);
            SbFx.sound(sl, sp.position(), SoundEvents.LODESTONE_COMPASS_LOCK, 0.6F, 0.6F);
            return InteractionResultHolder.consume(stack);
        }
        Vec3 to = new Vec3(found.getX() + 0.5 - sp.getX(), 0, found.getZ() + 0.5 - sp.getZ());
        int dist = (int) Math.round(to.length());
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.compass.found", dist,
                Component.translatable(DIR_KEY + direction(to))).withStyle(ChatFormatting.GOLD), true);
        SbFx.sound(sl, sp.position(), SoundEvents.LODESTONE_COMPASS_LOCK, 1.0F, 1.2F);
        // rastro dourado no rumo da sepultura
        if (to.lengthSqr() > 1.0E-3) {
            Vec3 dir = to.normalize();
            Vec3 start = sp.position().add(0, 1.2, 0);
            for (int i = 1; i <= 12; i++) {
                Vec3 p = start.add(dir.scale(i * 0.6));
                sl.sendParticles(SbFx.dust(SbFx.GOLD, 0.9F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    /** "n", "ne", "e"... pelo ângulo no plano (norte = -z). */
    private static String direction(Vec3 v) {
        double deg = Mth.wrapDegrees(Math.toDegrees(Math.atan2(v.x, -v.z)));
        String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        int i = (int) Math.floorMod(Math.round(deg / 45.0), 8);
        return dirs[i];
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.compass").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.compass.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
