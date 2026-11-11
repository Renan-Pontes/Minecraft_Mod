package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.block.SpellBarrierBlock;
import com.renanpontes.isekaimahou.feature.forms_b.block.SpellBarrierBlockEntity;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellWallEntity;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.core.ElementFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Muralha: linha de barreiras temporárias (5 de largura × 3 de altura; Expandir aumenta) de frente
 * para onde você olha, no bloco mirado (até 16) ou 3 blocos à frente. Só ocupa espaço livre (ar,
 * água, plantinhas) e nunca prende criaturas dentro. Quem encosta recebe os efeitos a cada segundo.
 * Como estágio de magia programável, sobe no ponto atingido, de frente para a direção do estágio.
 */
public class WallForm extends FormGlyph {
    public WallForm() {
        super("wall", 10);
        element(Element.ARCANE);
        tier(2);
        minCircle(2);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 6; }

    @Override
    public int baseCooldown() { return 60; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();

        Vec3 look = FormsBUtil.aimDir(ctx);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = Vec3.directionFromRotation(0, ctx.hasOrigin() ? 0 : caster.getYRot());
        flat = flat.normalize();

        Vec3 center;
        Vec3 anchor = ctx.anchor();
        if (anchor != null) {
            // Estágio nascido de um impacto: a muralha sobe ali. Se atingiu uma criatura, sobe logo ATRÁS dela
            // (bloqueia a fuga e quem for empurrado bate nela) — nunca prende ninguém dentro.
            center = FormsBUtil.groundAtAnchor(ctx, anchor);
            if (ctx.previousTarget() != null) center = center.add(flat.scale(1.5));
        } else {
            BlockHitResult hit = FormsBUtil.aim(ctx, 16);
            Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : caster.position();
            center = hit != null ? Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()))
                    : from.add(flat.scale(3.0));
            // Distância mínima: não levanta a muralha em cima de você
            Vec3 rel = center.subtract(caster.position());
            if (!ctx.hasOrigin() && rel.x * rel.x + rel.z * rel.z < 2.0 * 2.0) center = caster.position().add(flat.scale(2.5));
        }
        Vec3 ground = FormsBUtil.groundBelow(level, center.add(0, 0.5, 0), 6);
        double baseY = ground != null ? ground.y : center.y;
        BlockPos base = BlockPos.containing(center.x, baseY + 0.01, center.z);

        // A linha é perpendicular ao olhar (alinhada à grade)
        Direction along = Math.abs(flat.x) > Math.abs(flat.z) ? Direction.SOUTH : Direction.EAST;
        int extra = Math.max(0, s.aoe);
        int width = 5 + 2 * extra;
        int height = 3 + extra;
        int duration = s.duration(200);
        long expire = level.getGameTime() + duration + 5;
        BlockState state = FormsBRegistry.SPELL_BARRIER.get().stateFor(ctx.element(), false);
        ServerPlayer sp = ctx.casterPlayer();

        List<BlockPos> placed = new ArrayList<>();
        for (int i = -width / 2; i <= width / 2; i++) {
            BlockPos col = settle(level, base.relative(along, i));
            if (col == null) continue;
            for (int h = 0; h < height; h++) {
                BlockPos p = col.above(h);
                BlockState cur = level.getBlockState(p);
                boolean water = cur.is(Blocks.WATER) && cur.getFluidState().isSource();
                boolean free = cur.isAir() || water || (cur.canBeReplaced() && cur.getFluidState().isEmpty());
                if (!free || cur.hasBlockEntity()) continue;
                if (sp != null && !level.mayInteract(sp, p)) continue;
                if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(p), LivingEntity::isAlive).isEmpty()) continue;
                // Plantinhas de 1 bloco (grama, flores, neve fina) voltam quando a muralha some;
                // as de 2 blocos são colhidas (caem como item); fogo simplesmente se apaga.
                BlockState previous = null;
                if (!cur.isAir() && !water && !cur.is(BlockTags.FIRE)) {
                    if (cur.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                        level.destroyBlock(p, true, caster);
                        if (!level.getBlockState(p).isAir()) continue;
                    } else {
                        previous = cur;
                    }
                }
                level.setBlock(p, state.setValue(SpellBarrierBlock.WATERLOGGED, water), Block.UPDATE_ALL);
                if (level.getBlockEntity(p) instanceof SpellBarrierBlockEntity be) {
                    be.setExpireAt(expire);
                    be.setPrevious(previous);
                }
                placed.add(p.immutable());
                level.sendParticles(FormsBUtil.dust(ctx.color() & 0xFFFFFF, 1.2F), p.getX() + 0.5, p.getY() + 0.5,
                        p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
            }
        }
        if (placed.isEmpty()) {
            FormsBUtil.fail(ctx, "message.isekai_mahou.forms_b.wall_no_space");
            return;
        }
        FormsBUtil.succeeded(ctx);
        // Visual: um pilar de luz sobe em cada coluna da muralha + estilhaços do elemento no pé
        java.util.Map<Long, BlockPos> bottoms = new java.util.HashMap<>();
        for (BlockPos p : placed) bottoms.merge(((long) p.getX() << 32) ^ (p.getZ() & 0xFFFFFFFFL), p, (a, b) -> a.getY() <= b.getY() ? a : b);
        for (BlockPos p : bottoms.values()) {
            Vec3 foot = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            ElementFx.pillar(level, foot, ctx.color() & 0xFFFFFF, 0.55F, height + 0.8F, 14);
            level.sendParticles(ElementFx.style(ctx.element(), 1.1F), foot.x, foot.y + 0.2, foot.z, 3, 0.3, 0.1, 0.3, 0.08);
        }
        SpellWallEntity wall = new SpellWallEntity(FormsBRegistry.SPELL_WALL.get(), level);
        wall.setup(ctx, duration, 0F);
        wall.setBlocks(placed);
        wall.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        level.addFreshEntity(wall);
        level.playSound(null, base, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, base, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    /** Ajusta a coluna ao relevo: sobe se estiver dentro do chão, desce se estiver flutuando. */
    private static BlockPos settle(ServerLevel level, BlockPos p) {
        BlockPos.MutableBlockPos m = p.mutable();
        for (int i = 0; i < 3 && solid(level, m); i++) m.move(Direction.UP);
        if (solid(level, m)) return null;
        for (int i = 0; i < 4 && !solid(level, m.below()); i++) m.move(Direction.DOWN);
        return m.immutable();
    }

    private static boolean solid(ServerLevel level, BlockPos p) {
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }
}
