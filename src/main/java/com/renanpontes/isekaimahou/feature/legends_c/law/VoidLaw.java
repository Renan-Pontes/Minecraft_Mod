package com.renanpontes.isekaimahou.feature.legends_c.law;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.entity.VoidFieldEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Lei: Vazio Infinito. O interior vira COSMOS (cliente: estrelas e nebulosa para quem está dentro). Os inimigos que o
 * domínio alcança recebem informação infinita: Atordoados durante os 3 primeiros segundos e depois 1 s a cada 4 s,
 * e Lentos (e com fadiga) o tempo todo. O Vazio é fechado (tem barreira; pode ser Invertido): quem está protegido pela
 * barreira de um domínio menor ({@link Domains#shielded}) não é atingido.
 */
public class VoidLaw implements Domains.LawHandler {
    private static final Map<UUID, Long> MSG = new HashMap<>();

    @Override
    public void onOpen(Domains.Info d) {
        ServerLevel l = d.level();
        VoidFieldEntity.open(d);
        Vec3 c = d.center();
        Announce.send(d.owner(), Component.translatable("announce.isekai_mahou.legends_c.void"), LcFx.VOID, Announce.STYLE_ULTIMATE);
        LcFx.sound(l, c, SoundEvents.END_PORTAL_SPAWN, 3F, 0.6F);
        LcFx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 3F, 0.4F);
        LcFx.sound(l, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 3F, 0.5F);
        LcFx.wave(l, c, new Vec3(0, 1, 0), d.radius(), LcFx.VOID, 30);
        LcFx.flash(l, c.add(0, 1.5, 0), 6F, 0xE8E4FF, 14);
    }

    @Override
    public void onTick(Domains.Info d, int age) {
        VoidFieldEntity.update(d, age);
    }

    @Override
    public void onEnemyTick(Domains.Info d, LivingEntity e, int age) {
        if (Domains.shielded(e, d)) return;
        if (age % 10 == 0) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, false, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 30, 1, false, false, true));
        }
        boolean stun = age < 60 ? age % 10 == 0 : (age - 60) % 80 == 0;
        if (!stun) return;
        Statuses.apply(e, Statuses.Kind.STUNNED, 1, age < 60 ? 22 : 20, d.owner(), 1F);
        ServerLevel l = d.level();
        Vec3 head = e.getEyePosition();
        l.sendParticles(ParticleTypes.ENCHANT, head.x, head.y + 0.3, head.z, 24, 0.5, 0.5, 0.5, 0.6);
        l.sendParticles(ParticleTypes.END_ROD, head.x, head.y + 0.2, head.z, 4, 0.4, 0.3, 0.4, 0.02);
        if (e instanceof ServerPlayer sp) {
            Long last = MSG.get(sp.getUUID());
            if (last == null || l.getGameTime() - last > 60) {
                MSG.put(sp.getUUID(), l.getGameTime());
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_c.void_overload"), true);
            }
            LcFx.sound(l, head, SoundEvents.AMETHYST_BLOCK_CHIME, 1F, 0.5F);
        }
    }

    @Override
    public void onClose(Domains.Info d) {
        VoidFieldEntity.close(d.id());
        LcFx.sound(d.level(), d.center(), SoundEvents.BEACON_DEACTIVATE, 3F, 0.5F);
    }

    public static void clear() {
        MSG.clear();
    }
}
