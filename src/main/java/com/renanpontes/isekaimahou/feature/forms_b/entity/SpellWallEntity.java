package com.renanpontes.isekaimahou.feature.forms_b.entity;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.feature.forms_b.block.SpellBarrierBlock;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Controlador invisível da Muralha: guarda as posições das barreiras, aplica os efeitos a cada
 * segundo em quem encosta nelas e as desfaz no fim. As barreiras também têm cronômetro próprio,
 * então somem mesmo se este controlador for removido antes. Magias programáveis: encostar = impacto
 * (Ao Acertar/Saltar); a muralha se desfazer = Ao Terminar.
 */
public class SpellWallEntity extends SpellFormEntity {
    private final List<BlockPos> blocks = new ArrayList<>();
    private AABB bounds;

    public SpellWallEntity(EntityType<? extends SpellWallEntity> type, Level level) {
        super(type, level);
    }

    public void setBlocks(List<BlockPos> list) {
        blocks.clear();
        blocks.addAll(list);
        bounds = null;
    }

    private AABB bounds() {
        if (bounds == null && !blocks.isEmpty()) {
            AABB box = new AABB(blocks.get(0));
            for (BlockPos p : blocks) box = box.minmax(new AABB(p));
            bounds = box;
        }
        return bounds;
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        if (blocks.isEmpty()) {
            discard();
            return;
        }
        if (age % 20 != 0) return;
        // As barreiras têm cronômetro próprio (e o chunk pode ter ficado descarregado): sem nenhuma, acabou
        blocks.removeIf(p -> !(level.getBlockState(p).getBlock() instanceof SpellBarrierBlock));
        if (blocks.isEmpty()) {
            stageEnd(ctx, position().add(0, 1.0, 0));
            discard();
            return;
        }
        bounds = null;
        AABB box = bounds();
        boolean any = false;
        boolean dormant = casterAbsent(ctx);
        boolean wantsImpact = FormsBUtil.wantsImpact(ctx);
        boolean support = FormsBUtil.onlyBeneficial(ctx);
        boolean heals = FormsBUtil.anyBeneficial(ctx);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box.inflate(0.6),
                e -> !dormant && e.isAlive() && !e.isSpectator())) {
            if (!touching(level, e)) continue;
            if (e == ctx.caster()) {
                FormsBUtil.applyBeneficialToCaster(ctx);
            } else {
                // Aliado numa muralha sem cura/escudo: nada a aplicar (e o Encadear não pode pular dele para inimigos)
                if (!heals && ctx.isAlly(e)) continue;
                // Inimigo encostar na muralha é um impacto real (Ao Acertar/Saltar), avisado antes dos efeitos
                if (wantsImpact && FormsBUtil.countsAsHit(ctx, e, support)) FormsBUtil.impactOn(ctx, e);
                ctx.resolveOnEntity(e, this);
            }
            any = true;
        }
        // Brilho nas faces: algumas partículas por segundo
        for (int i = 0; i < 3; i++) {
            BlockPos p = blocks.get(level.random.nextInt(blocks.size()));
            level.sendParticles(FormsBUtil.dust(i == 2 ? accentColor() : color(), 1.0F), p.getX() + 0.5, p.getY() + 0.5,
                    p.getZ() + 0.5, 2, 0.35, 0.35, 0.35, 0);
        }
        if (any) level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6F, 0.8F);
    }

    /** A caixa da entidade (um pouco inflada) encosta em alguma barreira desta muralha? */
    private boolean touching(ServerLevel level, LivingEntity e) {
        AABB eb = e.getBoundingBox().inflate(0.2);
        for (BlockPos p : blocks) {
            if (eb.intersects(new AABB(p)) && level.getBlockState(p).getBlock() instanceof SpellBarrierBlock) return true;
        }
        return false;
    }

    @Override
    protected void onExpire(ServerLevel level, SpellContext ctx) {
        for (BlockPos p : blocks) SpellBarrierBlock.dissolve(level, p);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
        // Ao Terminar: o próximo estágio nasce no meio da muralha que se desfez
        stageEnd(ctx, position().add(0, 1.0, 0));
    }

    @Override
    protected void readExtra(CompoundTag tag) {
        blocks.clear();
        for (long l : tag.getLongArray("Blocks")) blocks.add(BlockPos.of(l));
        bounds = null;
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        long[] arr = new long[blocks.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = blocks.get(i).asLong();
        tag.put("Blocks", new LongArrayTag(arr));
    }
}
