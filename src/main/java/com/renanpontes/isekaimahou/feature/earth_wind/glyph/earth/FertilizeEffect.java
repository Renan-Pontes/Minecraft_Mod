package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fertilizar: efeito de farinha de osso em area (plantacoes, mudas, flores). Amplificar = mais "doses".
 * Num animal filhote, ele cresce na hora; animais adultos recuperam um pouco de vida.
 */
public class FertilizeEffect extends EffectGlyph {
    public FertilizeEffect() {
        super("fertilize", 8);
        element(Element.EARTH);
        tier(2);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (target instanceof AgeableMob am && am.isBaby()) {
            // todos os segundos que faltavam (o getSpeedUpSecondsWhenFeeding do jogo so adiantaria 10%)
            am.ageUp((-am.getAge() + 19) / 20, true);
        }
        if (target instanceof Animal a) a.heal(4.0F * stats.power());
        ctx.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getBbHeight() * 0.6,
                target.getZ(), 10, 0.4, 0.4, 0.4, 0);
        grow(ctx, BlockPos.containing(target.getX(), target.getY() + 0.2, target.getZ()), stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        grow(ctx, hit.getBlockPos(), stats);
    }

    private static void grow(SpellContext ctx, BlockPos center, SpellStats stats) {
        if (!EwUtil.once(ctx, "fertilize")) return;
        ServerLevel level = ctx.level();
        float radius = stats.radius(2.5F);
        int r = (int) Math.ceil(radius);
        int doses = 1 + Math.max(0, stats.amplify);
        boolean spreaderUsed = false;
        int grown = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof BonemealableBlock bb) || !bb.isValidBonemealTarget(level, pos, state)) continue;
                    // grama/musgo espalham sozinhos: so uma dose no centro, para nao virar um tapete de flores
                    if (bb.getType() == BonemealableBlock.Type.NEIGHBOR_SPREADER) {
                        if (spreaderUsed || Math.abs(dx) > 1 || Math.abs(dz) > 1) continue;
                        spreaderUsed = true;
                    }
                    if (!EwUtil.canEdit(ctx, pos)) continue;
                    boolean any = false;
                    for (int i = 0; i < doses; i++) {
                        if (BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), level, pos, ctx.casterPlayer())) any = true;
                    }
                    if (any) {
                        level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
                        grown++;
                    }
                }
            }
        }
        Vec3 at = Vec3.atCenterOf(center).add(0, 0.6, 0);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 12 + grown * 2, radius * 0.6, 0.4, radius * 0.6, 0);
        level.sendParticles(ParticleTypes.COMPOSTER, at.x, at.y, at.z, 10, radius * 0.5, 0.2, radius * 0.5, 0);
        EwFx.sound(level, at, SoundEvents.BONE_MEAL_USE, 1.0F, 0.9F);
        EwFx.sound(level, at, SoundEvents.ROOTED_DIRT_PLACE, 0.8F, 1.1F);
    }
}
