package com.renanpontes.isekaimahou.feature.class_hollow.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_hollow.Hollow;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowFx;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowSounds;
import com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowMoves;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "Cero Fusionado" (aumento de origem do Hollow, Adjuchas): a magia ganha um CERO — no instante em que ela sai, um feixe
 * fino de Reiatsu dispara da boca (ou da mão, no Arrancar) para onde você olha: 22 blocos, atravessa todos os inimigos
 * (dano próprio 3 +1 por estágio do Hollow, com o poder da magia), na cor do seu Cero. Não aplica os efeitos da magia
 * (é um tiro a mais). Custa 8 de mana e +0,4 s de conjuração; não acumula. Uma vez por conjuração.
 */
public class CeroFusionAugment extends AugmentGlyph {
    public static final String PATH = "hollowfied_cero_fusionado";
    public static final ResourceLocation ID = IsekaiMahou.id(PATH);

    public CeroFusionAugment() {
        super(PATH, 8, CeroFusionAugment::modify);
        origin(MagicPath.HOLLOW);
        element(Element.SHADOW);
        tier(3);
        minCircle(4);
        castTicks(8);
    }

    private static void modify(SpellStats s) {
        s.custom.merge(ID, 1, Integer::sum);
    }

    static boolean has(SpellContext ctx) {
        return ctx.rootSpell() != null && ctx.rootSpell().anyStats(st -> st.custom(ID) > 0);
    }

    /** Depois da conjuração: o Cero sai junto. */
    public static void onPost(SpellCastEvent.Post e) {
        SpellContext ctx = e.getContext();
        LivingEntity caster = e.getCaster();
        if (caster == null || ctx == null || !(caster.level() instanceof ServerLevel level) || !has(ctx)) return;
        int rank = caster instanceof Player p && Hollow.isHollow(p) ? Hollow.rank(MagicApi.get(p)) : 0;
        int rgb = HollowMoves.ceroRgb(caster);
        Vec3 look = caster.getLookAngle();
        Vec3 from = HollowMoves.muzzle(caster, look, HollowMoves.fromHand(caster));
        Vec3 to = from.add(look.scale(22));
        HitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (r.getType() == HitResult.Type.BLOCK) to = r.getLocation();
        Vec3 end = to;
        float width = 0.7F;
        List<LivingEntity> along = new ArrayList<>();
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.5), LivingEntity::isAlive)) {
            if (t == caster || ctx.isAlly(t)) continue;
            if (t.getBoundingBox().inflate(width * 0.5).clip(from, end).isPresent()) along.add(t);
        }
        along.sort(Comparator.comparingDouble(t -> t.distanceToSqr(from)));
        SpellStats stats = ctx.formStats();
        for (LivingEntity t : along) {
            ctx.damage(t, 3F + rank, stats, caster);
            t.setDeltaMovement(t.getDeltaMovement().add(look.x * 0.4, 0.15, look.z * 0.4));
            t.hurtMarked = true;
        }
        HollowNetwork.fxNear(level, HollowNetwork.CERO_BEAM, caster.getId(), from, end, width, rgb, 96);
        level.sendParticles(HollowFx.dust(rgb, 1.2F), end.x, end.y, end.z, 8, 0.3, 0.3, 0.3, 0.05);
        HollowFx.sound(level, from, HollowSounds.CERO_FIRE.get(), 0.9F, 1.3F);
        HollowFx.sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, 0.4F, 1.4F);
        if (caster instanceof ServerPlayer sp && Hollow.isHollow(sp)) MagicApi.addCounter(sp, Hollow.CEROS, 1);
    }
}
