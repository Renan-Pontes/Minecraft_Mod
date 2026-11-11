package com.renanpontes.isekaimahou.feature.legends_f.necro;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.legends_f.LfNet;
import com.renanpontes.isekaimahou.feature.legends_f.LfRegistry;
import com.renanpontes.isekaimahou.feature.legends_f.LfStates;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.feature.legends_f.entity.ShadowSoldierEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Monarca das Sombras (forma G do Necromante): por 30–60 s todo o exército sai da sombra de graça (mesmo quem estava
 * regenerando), os soldados ficam ×1,5 mais fortes e resistentes, o dono ganha a aura do Monarca, inimigos por perto
 * sentem o Medo do Monarca, os efeitos da receita pulsam a cada 2 s nos inimigos a até 8 blocos e TODA criatura que
 * morre a até 24 blocos é erguida na hora ("Levante-se!" automático, 100%), enquanto houver vaga.
 */
public final class MonarchState {
    public static final double PULSE_RADIUS = 8.0;
    public static final double ARISE_RADIUS = 24.0;
    public static final int COLOR = 0x6A3DD8;

    private static final class St {
        final SpellContext ctx;
        final long end;

        St(SpellContext ctx, long end) {
            this.ctx = ctx;
            this.end = end;
        }
    }

    private static final Map<UUID, St> ACTIVE = new HashMap<>();

    public static boolean active(ServerPlayer sp) {
        St st = ACTIVE.get(sp.getUUID());
        return st != null && st.end > sp.serverLevel().getGameTime();
    }

    public static boolean active(UUID owner, long now) {
        St st = ACTIVE.get(owner);
        return st != null && st.end > now;
    }

    public static void start(ServerPlayer sp, SpellContext ctx, int ticks) {
        ServerLevel level = sp.serverLevel();
        ACTIVE.put(sp.getUUID(), new St(ctx, level.getGameTime() + ticks));
        LfUtil.banner(sp, "announce.isekai_mahou.legends_f.monarch", COLOR, true);
        sp.addEffect(new MobEffectInstance(LfRegistry.SHADOW_MONARCH, ticks, 0, false, false, true));
        LfStates.set(sp, LfNet.S_MONARCH, ticks, 0);
        ShadowArmy.clearCooldowns(sp);
        int n = ShadowArmy.manifestAll(sp, true);
        for (ShadowSoldierEntity s : ShadowArmy.live(sp.getUUID())) s.setMonarch(true);
        Vec3 p = sp.position();
        LfUtil.fx(level, LfNet.ARISE, p, new Vec3(0, 1, 0), COLOR, 40, 9F);
        LfUtil.ring(level, p.add(0, 0.1, 0), COLOR, 30, 24F);
        LfUtil.sound(level, p, SoundEvents.WITHER_SPAWN, 0.8F, 0.6F);
        LfUtil.sound(level, p, SoundEvents.WARDEN_EMERGE, 1.4F, 0.7F);
        // Medo do Monarca: quem é inimigo por perto fraqueja
        for (LivingEntity f : LfUtil.foesAround(ctx, p, 14, 40)) {
            f.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), sp);
            f.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), sp);
        }
        int size = ShadowArmy.size(sp);
        LfUtil.chat(sp, Component.translatable(size == 0 ? "message.isekai_mahou.legends_f.monarch_empty"
                : "message.isekai_mahou.legends_f.monarch_start", n, size, ShadowArmy.cap(sp)).withStyle(ChatFormatting.DARK_PURPLE));
        MagicApi.addCounter(sp, "legends_f.monarch", 1);
    }

    /**
     * Uma criatura morreu: se algum Monarca ativo está a até 24 blocos e tem vaga, ela vira soldado na hora.
     * @return true se foi erguida (não fica sombra no chão)
     */
    static boolean autoArise(ServerLevel level, LivingEntity dead) {
        if (ACTIVE.isEmpty()) return false;
        long now = level.getGameTime();
        for (Map.Entry<UUID, St> en : ACTIVE.entrySet()) {
            if (en.getValue().end <= now) continue;
            ServerPlayer sp = level.getServer().getPlayerList().getPlayer(en.getKey());
            if (sp == null || sp.level() != level || sp.distanceToSqr(dead) > ARISE_RADIUS * ARISE_RADIUS) continue;
            if (ShadowArmy.size(sp) >= ShadowArmy.cap(sp)) continue;
            ShadowRemnants.Remnant r = ShadowRemnants.of(dead);
            CompoundTag e = ShadowArmy.enlist(sp, r);
            if (e == null) continue;
            ShadowSoldierEntity s = ShadowArmy.manifest(sp, e, dead.position(), true);
            if (s != null) s.setMonarch(true);
            ShadowArmy.ariseFx(level, dead.position(), r.height);
            LfUtil.sound(level, dead.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.9F, 0.6F);
            LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.arise_ok",
                    s != null ? s.getDisplayName() : Component.literal(r.type), ShadowArmy.size(sp), ShadowArmy.cap(sp))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            MagicApi.addCounter(sp, "legends_f.arise", 1);
            return true;
        }
        return false;
    }

    static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, St>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, St> en = it.next();
            St st = en.getValue();
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp == null || !sp.isAlive() || st.end <= now) {
                it.remove();
                if (sp != null) end(sp);
                continue;
            }
            ServerLevel level = sp.serverLevel();
            aura(level, sp, now);
            if ((st.end - now) % 40 == 0 && st.ctx.level() == level) pulse(level, sp, st.ctx);
        }
    }

    private static void end(ServerPlayer sp) {
        sp.removeEffect(LfRegistry.SHADOW_MONARCH);
        LfStates.clear(sp, LfNet.S_MONARCH);
        for (ShadowSoldierEntity s : ShadowArmy.live(sp.getUUID())) s.setMonarch(false);
        LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.monarch_end").withStyle(ChatFormatting.GRAY));
    }

    private static void aura(ServerLevel level, ServerPlayer sp, long now) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.42F, 0.24F, 0.85F), 1.6F);
        double h = sp.getBbHeight();
        for (int k = 0; k < 3; k++) {
            double a = now * 0.3 + k * Math.PI * 2 / 3;
            level.sendParticles(dust, sp.getX() + Math.cos(a) * 0.7, sp.getY() + (now * 0.07 + k * 0.33) % 1.0 * h,
                    sp.getZ() + Math.sin(a) * 0.7, 1, 0, 0, 0, 0);
        }
        if (now % 4 == 0) level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, sp.getX(), sp.getY() + 0.1, sp.getZ(), 2, 0.4, 0.05, 0.4, 0.01);
        if (now % 6 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, sp.getX(), sp.getY() + 0.3, sp.getZ(), 2, 0.5, 0.2, 0.5, 0.01);
    }

    /** Pulso: os efeitos da receita nos inimigos a até 8 blocos (sem encher a carga de ultimate). */
    private static void pulse(ServerLevel level, ServerPlayer sp, SpellContext ctx) {
        Vec3 c = sp.position().add(0, sp.getBbHeight() * 0.5, 0);
        List<LivingEntity> foes = LfUtil.foesAround(ctx, c, PULSE_RADIUS, 16);
        if (foes.isEmpty()) return;
        MagicData d = MagicApi.get(sp);
        float snap = d.charge();
        for (LivingEntity f : foes) ctx.resolveOnEntity(f, sp);
        d.setCharge(snap);
        LfUtil.ring(level, sp.position().add(0, 0.15, 0), COLOR, 12, (float) PULSE_RADIUS);
        LfUtil.sound(level, c, SoundEvents.SCULK_CLICKING, 1.2F, 0.6F);
    }

    static void clear() {
        ACTIVE.clear();
    }

    private MonarchState() {}
}
