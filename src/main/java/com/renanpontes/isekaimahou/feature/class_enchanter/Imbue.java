package com.renanpontes.isekaimahou.feature.class_enchanter;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * IMBUIR ARMA: o elemento da magia vai para a arma de quem recebe o encantamento (si mesmo ou um aliado). Cada golpe
 * corpo a corpo causa +1,5 de dano por nível (Arcano: +2) e o toque do elemento: Fogo queima, Água molha, Gelo gela, Raio
 * eletrifica, Terra pesa, Vento empurra, Luz expõe, Sombra corrompe (fusões usam o elemento-pai principal).
 * O elemento fica nos dados da entidade, então sobrevive ao recarregar o mundo junto com o efeito.
 */
public final class Imbue {
    public static final String KEY = "isekai_mahou.enchanter.imbue";

    static void register() {
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, Imbue::onIncoming);
    }

    public static void set(LivingEntity e, Element el) {
        e.getPersistentData().putString(KEY, el.id);
    }

    public static Element element(LivingEntity e) {
        String id = e.getPersistentData().getString(KEY);
        Element el = id.isEmpty() ? null : Element.byId(id);
        return el == null ? Element.ARCANE : el;
    }

    /** Partículas do elemento saindo da mão da arma. */
    public static void ambient(ServerLevel level, LivingEntity e) {
        Element el = element(e);
        Vec3 hand = handPos(e);
        level.sendParticles(EnchFx.dust(el.color, 0.8F), hand.x, hand.y, hand.z, 3, 0.08, 0.08, 0.08, 0);
        if (e.tickCount % 24 == 0) level.sendParticles(ElementFx.signature(el), hand.x, hand.y, hand.z, 1, 0.05, 0.05, 0.05, 0.01);
    }

    static Vec3 handPos(LivingEntity e) {
        float yaw = e.yBodyRot * ((float) Math.PI / 180F);
        double side = e.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        double ox = Math.cos(yaw) * 0.38 * side;
        double oz = Math.sin(yaw) * 0.38 * side;
        return new Vec3(e.getX() + ox, e.getY() + e.getBbHeight() * 0.45, e.getZ() + oz);
    }

    private static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (!(target.level() instanceof ServerLevel level)) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof LivingEntity attacker) || src.getDirectEntity() != attacker || attacker == target) return;
        if (AntiMagic.isMagic(src)) return;
        int lvl = Enchants.level(attacker, EnchRegistries.IMBUE);
        if (lvl <= 0) return;
        Element el = element(attacker);
        float bonus = (el == Element.ARCANE ? 2.0F : 1.5F) * lvl;
        e.setAmount(e.getAmount() + bonus);
        touch(target, attacker, el, lvl);
        Vec3 c = EnchFx.center(target);
        level.sendParticles(EnchFx.dust(el.color, 1.1F), c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0);
        level.sendParticles(ElementFx.signature(el), c.x, c.y, c.z, 3, 0.25, 0.25, 0.25, 0.02);
    }

    /** O "toque" do elemento no golpe. */
    private static void touch(LivingEntity t, LivingEntity attacker, Element el, int lvl) {
        Element base = switch (el) {
            case ICE, LIGHTNING -> el;
            default -> el.isFusion() ? el.parentA : el;
        };
        int ticks = 60 + 20 * lvl;
        switch (base) {
            case FIRE -> Statuses.apply(t, Statuses.Kind.BURNING, 1, ticks, attacker, 1F);
            case WATER -> Statuses.apply(t, Statuses.Kind.WET, 1, ticks, attacker, 1F);
            case ICE -> Statuses.apply(t, Statuses.Kind.CHILLED, 1, ticks, attacker, 1F);
            case LIGHTNING -> Statuses.apply(t, Statuses.Kind.SHOCKED, 1, ticks, attacker, 1F);
            case EARTH -> t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, Math.min(2, lvl / 2)), attacker);
            case WIND -> {
                Vec3 d = t.position().subtract(attacker.position()).multiply(1, 0, 1);
                if (d.lengthSqr() > 1.0E-4) {
                    d = d.normalize();
                    t.push(d.x * 0.35 * lvl, 0.15, d.z * 0.35 * lvl);
                    t.hurtMarked = true;
                }
                if (t.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.GUST, t.getX(), t.getY() + 0.8, t.getZ(), 1, 0, 0, 0, 0);
            }
            case LIGHT -> Statuses.apply(t, Statuses.Kind.EXPOSED, 1, ticks, attacker, 1F);
            case SHADOW -> Statuses.apply(t, Statuses.Kind.CORRUPTED, 1, ticks, attacker, 1F);
            default -> {
            }
        }
    }

    private Imbue() {}
}
