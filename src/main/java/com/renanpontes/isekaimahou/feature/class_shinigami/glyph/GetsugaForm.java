package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_shinigami.BankaiState;
import com.renanpontes.isekaimahou.feature.class_shinigami.HollowForms;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.ReishiWaveEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Getsuga Tenshō (homenagem): a lâmina libera o Reiatsu numa LUA CRESCENTE ENORME na diagonal (5 blocos de largura,
 * +2 por Expandir; Carregar a deixa 25% maior) que voa ~40 blocos e corta TODOS os inimigos no caminho, com dano
 * próprio (5) além dos efeitos da magia. Durante a Bankai ou com a Máscara do Vazio sai o <b>Getsuga Tenshō Negro</b>:
 * negro com borda rubra, 40% mais largo, dano próprio 7 e +25% de poder. Dividir solta mais crescentes em leque.
 * Pode ser estágio de magia. Rank IV do Caminho da Zanpakutō.
 */
public class GetsugaForm extends FormGlyph {
    public static final float DAMAGE = 5.0F;
    public static final float BLACK_DAMAGE = 7.0F;

    public GetsugaForm() {
        super("zanpakuto_getsuga", 22);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(3);
        minCircle(5);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCastTicks() {
        return 8;
    }

    @Override
    public int baseCooldown() {
        return 40;
    }

    @Override
    public float hintRange() {
        return 40F;
    }

    @Override
    public float hintRadius() {
        return 2.5F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        boolean black = BankaiState.active(caster) || HollowForms.masked(caster);
        if (black) ctx.setPowerMult(ctx.powerMult() * 1.25F);
        float charge = 1F + 0.25F * Math.max(0, s.charge);
        float width = (5.0F + 2.0F * Math.max(0, s.aoe)) * (black ? 1.4F : 1.0F) * charge;
        float speed = 1.8F * s.speedMult();
        int life = Math.max(10, Math.round(40 / speed));
        float bonus = (black ? BLACK_DAMAGE : DAMAGE) * charge;
        Vec3 from = ctx.eyePos().add(0, -0.4, 0);
        Vec3 look = ctx.look();
        int n = s.count();
        int kind = black ? ReishiWaveEntity.GETSUGA_BLACK : ReishiWaveEntity.GETSUGA;
        for (int i = 0; i < n; i++) {
            float yaw = (i - (n - 1) / 2F) * 8F;
            Vec3 dir = MoonFangForm.rotateYaw(look, yaw);
            ReishiWaveEntity w = ReishiWaveEntity.spell(ctx, caster, from.add(dir.scale(1.0)), kind, width, life, black ? 0x140810 : 0xF4FAFF)
                    .bonus(bonus, false);
            w.shoot(dir.x, dir.y, dir.z, speed, 0F);
            level.addFreshEntity(w);
        }
        // o golpe: arco largo, explosão de Reiatsu e o grito
        int rgb = black ? 0xC0102A : 0x8FE0FF;
        ShinigamiFx.slashArc(level, from, look, 2.2, 90, 1.6F, ShinigamiFx.dust(rgb, 1.6F));
        ShinigamiFx.burst(level, from.add(look.scale(1.2)), 30, 0.6, black ? ShinigamiFx.BLACK : ShinigamiFx.SPIRIT);
        level.sendParticles(ParticleTypes.SONIC_BOOM, from.x + look.x * 1.5, from.y + look.y * 1.5, from.z + look.z * 1.5, 1, 0, 0, 0, 0);
        ShinigamiFx.sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.4F);
        ShinigamiFx.sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, 0.6F, black ? 0.6F : 1.0F);
        if (black) ShinigamiFx.sound(level, from, SoundEvents.WITHER_SHOOT, 0.7F, 0.6F);
        if (!ctx.hasOrigin()) caster.swing(InteractionHand.MAIN_HAND, true);
        if (caster instanceof ServerPlayer sp && ctx.stage() == 0) {
            if (!s.silent) Announce.send(sp, Shinigami.msg(black ? "getsuga_black_shout" : "getsuga_shout"), black ? 0xC0102A : 0xDDF4FF,
                    Announce.STYLE_SPELL);
            if (Shinigami.isShinigami(sp)) MagicApi.addCounter(sp, Shinigami.GETSUGA_USES, 1);
        }
    }
}
