package com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbFx;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbIds;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Aumento do Apostador — <b>Roleta Russa</b>: um tambor de 6 câmaras com UMA bala, girado em segredo. Cada conjuração com
 * o glifo puxa o gatilho:
 * <ul>
 *   <li><b>clique</b> (câmara vazia): a magia sai com poder ×1,20 — e cada clique seguido sobe +0,15 (×1,35, ×1,50,
 *   ×1,65, ×1,80): quanto mais perto da bala, maior o prêmio;</li>
 *   <li><b>BANG</b>: a bala é sua — perde 35% da vida máxima (nunca abaixo de 1 de vida), a magia sai com metade do
 *   poder e o tambor gira de novo. Apostador: a Tensão sobe +20.</li>
 * </ul>
 * A sorte não compra esta aposta (a Sorte não mexe no tambor). O tambor fica na memória: sair do jogo gira de novo.
 * Mobs ignoram o glifo.
 */
public final class RussianRoulette {
    public static final float BASE = 1.20F;
    public static final float STEP = 0.15F;
    public static final float BANG_HEALTH = 0.35F;
    public static final float BANG_POWER = 0.5F;
    public static final int BANG_FEVER = 20;

    private static final class Cylinder {
        int bullet;
        int pulls;
    }

    private static final Map<UUID, Cylinder> CYLINDERS = new HashMap<>();

    public static void onPre(SpellCastEvent.Pre e) {
        SpellContext ctx = e.getContext();
        if (!(e.getCaster() instanceof ServerPlayer sp) || sp instanceof FakePlayer || !ctx.isPlayerCast()) return;
        if (GcbUtil.maxCustom(ctx.spell(), GcbIds.RUSSIAN_ROULETTE) <= 0) return;
        ServerLevel sl = sp.serverLevel();
        Cylinder cyl = CYLINDERS.computeIfAbsent(sp.getUUID(), k -> spin(sp));
        cyl.pulls++;
        Vec3 at = sp.position().add(0, sp.getBbHeight() * 0.7, 0);
        if (cyl.pulls >= cyl.bullet) {
            // BANG
            int chamber = cyl.pulls;
            CYLINDERS.put(sp.getUUID(), spin(sp));
            float dmg = Math.min(sp.getMaxHealth() * BANG_HEALTH, sp.getHealth() - 1.0F);
            if (dmg > 0 && !sp.getAbilities().instabuild) {
                sp.invulnerableTime = 0;
                sp.hurt(sp.damageSources().magic(), dmg);
            }
            ctx.setPowerMult(ctx.powerMult() * BANG_POWER);
            if (LuckService.isGambler(sp)) LuckService.addFever(sp, BANG_FEVER);
            sl.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.03);
            sl.sendParticles(GcbFx.dust(0xC0182E, 1.2F), at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.0);
            sl.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            GcbFx.sound(sl, at, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.9F);
            GcbFx.sound(sl, at, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0F, 0.6F);
            sp.displayClientMessage(Component.translatable(GcbIds.MSG + "roulette_bang", chamber).withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        float mult = BASE + STEP * (cyl.pulls - 1);
        ctx.setPowerMult(ctx.powerMult() * mult);
        GcbFx.sound(sl, at, SoundEvents.LEVER_CLICK, 0.9F, 1.7F);
        GcbFx.sound(sl, at, SoundEvents.DISPENSER_FAIL, 0.4F, 1.9F);
        sl.sendParticles(GcbFx.dust(GcbFx.GAMBLER, 0.7F), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);
        sp.displayClientMessage(Component.translatable(GcbIds.MSG + "roulette_click", cyl.pulls,
                String.format(Locale.ROOT, "%.2f", mult)).withColor(GcbFx.GAMBLER), true);
    }

    private static Cylinder spin(ServerPlayer sp) {
        Cylinder c = new Cylinder();
        c.bullet = 1 + sp.getRandom().nextInt(6);
        c.pulls = 0;
        return c;
    }

    public static void forget(UUID id) {
        CYLINDERS.remove(id);
    }

    public static void clear() {
        CYLINDERS.clear();
    }

    private RussianRoulette() {}
}
