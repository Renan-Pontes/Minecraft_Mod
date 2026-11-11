package com.renanpontes.isekaimahou.feature.class_shinigami;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.event.PendingCastEvent;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ganchos de VISUAL do Shinigami no servidor (nenhuma regra de jogo): a carga das técnicas enquanto o jogador conjura
 * (Cero na mão, Getsuga/Kidō pesado na lâmina), a cicatriz do Getsuga no chão e os atalhos para os efeitos curtos.
 */
public final class ShinigamiVfx {
    /** Uma conjuração com tempo entrou na Pilha: mostra a carga certa enquanto o jogador segura. */
    static void onPendingCast(PendingCastEvent e) {
        if (!e.getPending().fromPlayerCast() || !(e.getCaster() instanceof ServerPlayer sp)) return;
        MagicData d = MagicApi.get(sp);
        SpellRecipe r = d.channelRecipe;
        if (r == null) return;
        int ticks = Math.max(4, e.getPending().ticksLeft(e.getLevel()));
        Vec3 at = sp.getEyePosition();
        if (Shinigami.hasGlyph(r, Shinigami.CERO)) {
            ShinigamiFxNet.burst(sp, ShinigamiFxNet.CERO_CHARGE, at, Vec3.ZERO, ticks, HollowForms.CERO_RGB, null);
        } else if (Shinigami.hasGlyph(r, Shinigami.GETSUGA)) {
            boolean black = BankaiState.active(sp) || HollowForms.masked(sp);
            ShinigamiFxNet.burst(sp, ShinigamiFxNet.BLADE_CHARGE, at, Vec3.ZERO, ticks, black ? 0xC0102A : 0x8FE0FF, null);
        } else if (Shinigami.hasGlyph(r, Shinigami.MUGETSU)) {
            ShinigamiFxNet.burst(sp, ShinigamiFxNet.BLADE_CHARGE, at, Vec3.ZERO, ticks, 0x2A0A3A, null);
        } else if (Shinigami.hasGlyph(r, Shinigami.HADO_90)) {
            ShinigamiFxNet.burst(sp, ShinigamiFxNet.BLADE_CHARGE, at, Vec3.ZERO, ticks, 0x7A2AC8, null);
        } else if (Shinigami.hasGlyph(r, Shinigami.BANKAI) || Shinigami.hasGlyph(r, Shinigami.VASTO_LORDE)) {
            Archetype a = Shinigami.archetype(d);
            int rgb = Shinigami.hasGlyph(r, Shinigami.VASTO_LORDE) ? HollowForms.CERO_RGB : a == null ? Shinigami.RGB : a.rgb;
            ShinigamiFxNet.burst(sp, ShinigamiFxNet.BLADE_CHARGE, at, Vec3.ZERO, ticks, rgb, null);
        }
    }

    /**
     * Rastro de uma onda enorme passando rente ao chão: blocos quebradiços voando e uma cicatriz escura que o cliente
     * desenha por alguns segundos. {@code bottom} = ponto mais baixo da onda.
     */
    public static void groundScar(ServerLevel level, LivingEntity owner, Vec3 bottom, Vec3 dir, float width, int rgb) {
        BlockPos p = BlockPos.containing(bottom);
        BlockPos ground = null;
        for (int i = 0; i < 4; i++) {
            BlockPos q = p.below(i);
            if (!level.getBlockState(q).isAir() && level.getBlockState(q).isCollisionShapeFullBlock(level, q)) {
                ground = q;
                break;
            }
        }
        if (ground == null) return;
        BlockState st = level.getBlockState(ground);
        Vec3 top = new Vec3(bottom.x, ground.getY() + 1.02, bottom.z);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), top.x, top.y + 0.1, top.z, 6, width * 0.15, 0.1, width * 0.15, 0.25);
        level.sendParticles(ParticleTypes.CLOUD, top.x, top.y + 0.1, top.z, 1, width * 0.1, 0.05, width * 0.1, 0.02);
        ShinigamiFxNet.burstNear(level, ShinigamiFxNet.SCAR, top, dir, width, rgb, owner == null ? -1 : owner.getId(), 96);
    }

    private ShinigamiVfx() {}
}
