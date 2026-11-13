package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellPortalEntity;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Portal: abre dois anéis ligados — a entrada onde você está e a saída no bloco mirado (até 64).
 * Por 300×duração ticks, quem entra num sai no outro e recebe os efeitos da magia.
 */
public class PortalForm extends FormGlyph {
    public static final double MAX_RANGE = 64.0;

    public PortalForm() {
        super("portal", 40);
        element(Element.ARCANE);
        tier(5);
        minCircle(5);
    }

    @Override
    public int baseCastTicks() { return 30; }

    @Override
    public int baseCooldown() { return 300; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        BlockHitResult hit = FormsBUtil.aim(ctx, MAX_RANGE);
        if (hit == null) {
            FormsBUtil.fail(ctx, "message.isekai_mahou.forms_b.portal_no_target");
            return;
        }
        Vec3 exit = exitPoint(level, hit);
        if (exit == null) {
            FormsBUtil.fail(ctx, "message.isekai_mahou.forms_b.portal_no_space");
            return;
        }
        // Entrada no chão sob você (se conjurou pulando/voando baixo, o anel não fica no ar)
        Vec3 entry = caster.onGround() ? caster.position() : FormsBUtil.groundBelow(level, caster.position(), 4);
        if (entry == null) entry = caster.position();
        if (exit.distanceToSqr(entry) < 3.0 * 3.0) {
            FormsBUtil.fail(ctx, "message.isekai_mahou.forms_b.portal_too_close");
            return;
        }
        FormsBUtil.succeeded(ctx);
        float yaw = caster.getYRot();
        int life = s.duration(300);
        SpellPortalEntity a = new SpellPortalEntity(FormsBRegistry.SPELL_PORTAL.get(), level);
        SpellPortalEntity b = new SpellPortalEntity(FormsBRegistry.SPELL_PORTAL.get(), level);
        a.setup(ctx, life, 1F);
        b.setup(ctx, life, 1F);
        a.moveTo(entry.x, entry.y, entry.z, yaw, 0F);
        b.moveTo(exit.x, exit.y, exit.z, yaw, 0F);
        a.link(b, false, yaw);
        b.link(a, true, yaw);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        for (Vec3 p : new Vec3[] {entry, exit}) {
            level.sendParticles(Vfx.reversePortal(), p.x, p.y + 1.1, p.z, 40, 0.4, 0.8, 0.4, 0.1);
            level.sendParticles(FormsBUtil.dust(ctx.color() & 0xFFFFFF, 1.4F), p.x, p.y + 1.1, p.z, 20, 0.5, 0.9, 0.5, 0);
            level.playSound(null, p.x, p.y, p.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.9F, 1.2F);
            level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);
        }
    }

    /** Chão em frente à face mirada, com 2 blocos livres de altura (sobe até 3 blocos para achar). */
    private static Vec3 exitPoint(ServerLevel level, BlockHitResult hit) {
        BlockPos front = hit.getBlockPos().relative(hit.getDirection());
        Vec3 ground = FormsBUtil.groundBelow(level, Vec3.atCenterOf(front), 8);
        if (ground == null) return null;
        BlockPos feet = BlockPos.containing(ground.x, ground.y + 0.01, ground.z);
        for (int i = 0; i < 4; i++) {
            if (FormsBUtil.hasHeadroom(level, feet)) {
                double y = i == 0 ? ground.y : feet.getY();
                return new Vec3(feet.getX() + 0.5, y, feet.getZ() + 0.5);
            }
            feet = feet.above();
        }
        return null;
    }
}
