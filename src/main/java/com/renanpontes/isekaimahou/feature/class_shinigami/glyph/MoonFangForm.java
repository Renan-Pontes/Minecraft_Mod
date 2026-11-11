package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;
import com.renanpontes.isekaimahou.feature.class_shinigami.BankaiState;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.ReishiWaveEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Presa Lunar (homenagem Getsuga Tenshō): a lâmina solta o Reiatsu numa meia-lua larga (3 blocos, +1 por Expandir) que
 * voa ~28 blocos e corta TODOS os inimigos no caminho, cada um uma vez. Dividir solta mais meias-luas em leque. Na cor do
 * arquétipo; durante a Bankai sai negra com borda rubra e 50% mais larga. Pode ser estágio de magia.
 */
public class MoonFangForm extends FormGlyph {
    public MoonFangForm() {
        super("zanpakuto_moon_fang", 14);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(2);
        minCircle(3);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCastTicks() {
        return 6;
    }

    @Override
    public int baseCooldown() {
        return 30;
    }

    @Override
    public float hintRange() {
        return 28F;
    }

    @Override
    public float hintRadius() {
        return 1.5F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        boolean bankai = BankaiState.active(caster);
        int rgb = 0x8FE0FF;
        if (caster instanceof ServerPlayer sp) {
            Archetype a = Shinigami.archetype(MagicApi.get(sp));
            if (a != null) rgb = a.rgb;
        }
        if (bankai) rgb = 0x2A0A10;
        float width = (3.0F + Math.max(0, s.aoe)) * (bankai ? 1.5F : 1.0F);
        float speed = 1.6F * s.speedMult();
        int life = Math.max(8, Math.round(28 / speed));
        Vec3 from = ctx.eyePos().add(0, -0.3, 0);
        Vec3 look = ctx.look();
        int n = s.count();
        for (int i = 0; i < n; i++) {
            float yaw = (i - (n - 1) / 2F) * 9F;
            Vec3 dir = rotateYaw(look, yaw);
            ReishiWaveEntity w = ReishiWaveEntity.spell(ctx, caster, from.add(dir.scale(0.8)), ReishiWaveEntity.MOON, width, life, rgb);
            w.shoot(dir.x, dir.y, dir.z, speed, 0F);
            level.addFreshEntity(w);
        }
        ShinigamiFx.slashArc(level, from, look, 1.4, 80, 0.4F, ShinigamiFx.dust(rgb, 1.3F));
        ShinigamiFx.sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
        ShinigamiFx.sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, 0.35F, bankai ? 0.8F : 1.4F);
        caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    static Vec3 rotateYaw(Vec3 dir, float yawDeg) {
        if (yawDeg == 0) return dir;
        double yaw = Math.toRadians(yawDeg);
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        return new Vec3(dir.x * cos - dir.z * sin, dir.y, dir.x * sin + dir.z * cos).normalize();
    }
}
