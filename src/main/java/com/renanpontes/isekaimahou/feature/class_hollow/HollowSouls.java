package com.renanpontes.isekaimahou.feature.class_hollow;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * As ALMAS do Hollow: tudo o que ele mata de forte vira Alma (devorada na hora, com as almas voando para a boca). Dão
 * a evolução ({@link HollowRanks}) e pagam a Regeneração. Sem devorar por muito tempo, a FOME DE ALMAS cobra:
 * <ul>
 *   <li>20 min sem devorar: faminto — Fraqueza I, técnicas Hollow −20% de poder e o Vigor volta pela metade;</li>
 *   <li>40 min: esfomeado — Fraqueza II, Fome e Lentidão; o corpo se desfaz aos poucos (perde Almas).</li>
 * </ul>
 * Almas por abate: criaturas hostis = vida máxima / 6 (mín. 1); jogadores 30; chefes 60 + vida/10; Vazios do Mundo Oco
 * em dobro. Animais não têm alma que valha (0).
 */
public final class HollowSouls {
    public static final int HUNGRY_SECONDS = 20 * 60;
    public static final int STARVING_SECONDS = 40 * 60;
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));

    /** 0 saciado, 1 faminto, 2 esfomeado. */
    public static int hunger(MagicData d, int nowSeconds) {
        int since = nowSeconds - d.counter(Hollow.LAST_FEED);
        if (since >= STARVING_SECONDS) return 2;
        if (since >= HUNGRY_SECONDS) return 1;
        return 0;
    }

    /** Segundos desde a última alma devorada (para a aba). */
    public static int secondsSinceFeed(MagicData d, int nowSeconds) {
        return Math.max(0, nowSeconds - d.counter(Hollow.LAST_FEED));
    }

    /** Quantas almas um abate vale. */
    public static int value(LivingEntity victim) {
        if (victim instanceof Player) return 30;
        float hp = victim.getMaxHealth();
        int base;
        if (victim.getType().is(BOSSES)) base = 60 + (int) (hp / 10F);
        else if (victim instanceof Enemy) base = Math.max(1, (int) (hp / 6F));
        else return 0;
        if (isHuecoHollow(victim)) base *= 2;
        return base;
    }

    /** Criatura "Vazia" do Mundo Oco (por id, sem depender do módulo): vale o dobro. */
    static boolean isHuecoHollow(LivingEntity e) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        if (!IsekaiMahou.MODID.equals(id.getNamespace())) return false;
        String p = id.getPath();
        return p.startsWith("hollow_") || p.equals("menos_giant") || p.equals("arrancar") || p.equals("ulquiorra")
                || p.equals("grimmjow") || p.equals("barragan");
    }

    /** O Hollow matou: devora. */
    static void devour(ServerPlayer sp, LivingEntity victim) {
        int n = value(victim);
        if (n <= 0) return;
        MagicData d = MagicApi.get(sp);
        boolean wasHungry = hunger(d, Hollow.nowSeconds(sp)) > 0;
        MagicApi.addCounter(sp, Hollow.SOULS, n);
        MagicApi.addCounter(sp, Hollow.SOULS_TOTAL, n);
        MagicApi.addCounter(sp, Hollow.LAST_FEED, Hollow.nowSeconds(sp) - d.counter(Hollow.LAST_FEED));
        if (victim instanceof Player) MagicApi.addCounter(sp, Hollow.DEVOURED_PLAYERS, 1);
        if (isHuecoHollow(victim)) MagicApi.addCounter(sp, Hollow.DEVOURED_HOLLOWS, 1);
        Hollow.gainVigor(sp, Math.min(0.15F, 0.01F * n));
        ServerLevel sl = sp.serverLevel();
        Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        HollowNetwork.fx(sp, HollowNetwork.DEVOUR, at, Vec3.ZERO, Math.min(24, 2 + n / 3), Hollow.REIATSU_RGB);
        HollowFx.sound(sl, at, HollowSounds.DEVOUR.get(), 0.8F, 1.0F);
        HollowFx.sound(sl, sp.getEyePosition(), SoundEvents.GENERIC_EAT, 0.6F, 0.6F);
        sp.displayClientMessage(Hollow.msg("devoured", n, Hollow.souls(d)).withColor(Hollow.REIATSU_RGB), true);
        if (wasHungry) {
            sp.removeEffect(MobEffects.HUNGER);
            sp.displayClientMessage(Hollow.msg("hunger_sated").withColor(Hollow.BONE_RGB), true);
            HollowEvents.syncLook(sp);
        }
        HollowRanks.check(sp);
        // v1.4: o gatilho "Ao Devorar uma Alma" (slots que começam com ele saem sozinhos)
        com.renanpontes.isekaimahou.feature.class_hollow.glyph.DevourTrigger.fire(sp, n);
    }

    /** A cada 5 s (tick do jogador): a fome de almas. */
    static void hungerTick(ServerPlayer sp) {
        if (sp.isCreative() || sp.isSpectator() || Hollowfication.inProgress(sp)) return;
        MagicData d = MagicApi.get(sp);
        int h = hunger(d, Hollow.nowSeconds(sp));
        if (h == 0) {
            for (int i = 1; i <= 2; i++) if (d.hasFlag("hollow.hunger_warned." + i)) d.setFlag("hollow.hunger_warned." + i, false);
            return;
        }
        String flag = "hollow.hunger_warned." + h;
        sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, h - 1, false, false, true));
        if (h >= 2) {
            sp.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, 0, false, false, true));
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 0, false, false, true));
            // o corpo se desfaz: perde 1 Alma a cada 30 s
            if (sp.tickCount % 600 < 100 && Hollow.souls(d) > 0) MagicApi.addCounter(sp, Hollow.SOULS, -1);
            sp.serverLevel().sendParticles(HollowFx.BLACK, sp.getX(), sp.getY() + 1.0, sp.getZ(), 2, 0.3, 0.5, 0.3, 0.01);
        }
        if (!d.hasFlag(flag)) {
            d.setFlag(flag, true);
            sp.sendSystemMessage(Hollow.msg(h >= 2 ? "starving" : "hungry").withStyle(ChatFormatting.DARK_RED));
            HollowFx.sound(sp.serverLevel(), sp.position(), SoundEvents.WARDEN_AMBIENT, 0.6F, 0.6F);
            HollowEvents.syncLook(sp);
        }
    }

    private HollowSouls() {}
}
