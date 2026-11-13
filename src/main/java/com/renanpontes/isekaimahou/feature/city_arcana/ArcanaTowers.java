package com.renanpontes.isekaimahou.feature.city_arcana;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Os prédios mágicos "de apoio":
 * <ul>
 *   <li><b>Torre de Mana</b>: quem a cidade protege, dentro do território, recupera {@link #SPIRE_PCT} da mana máxima a
 *       cada 5 s por nível (somando as torres; máx. 2 torres = até 4%/5 s);</li>
 *   <li><b>Farol de Vigia Arcano</b>: a cada 3 s, inimigos e criaturas invisíveis (que a cidade não protege) num raio de
 *       {@link #beaconRadius} ficam Brilhando; no nível 4+ os inimigos também perdem a Invisibilidade.</li>
 * </ul>
 */
public final class ArcanaTowers {
    public static final float SPIRE_PCT = 0.004F;

    public static double beaconRadius(int lv) {
        return 16 + 8 * lv;
    }

    /** Altura do "olho" do farol acima do chão (o mesmo do molde procedural). */
    static int beaconEye(int lv) {
        return new int[] {7, 9, 11, 13, 15}[Math.max(1, Math.min(5, lv)) - 1] + 2;
    }

    /** A cada 100 ticks. */
    static void tickSpires(MinecraftServer server) {
        if (CityArcana.cities().isEmpty()) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.isSpectator()) continue;
            CityArcana.City c = CityArcana.at(sp);
            if (c == null || c.spireTotal <= 0 || !CityArcana.friendly(c, sp)) continue;
            MagicData d = MagicApi.get(sp);
            if (d.mana() >= d.maxMana()) continue;
            d.addMana(d.maxMana() * SPIRE_PCT * c.spireTotal);
            CityArcanaNet.send(sp.serverLevel(), CityArcanaNet.MANA, sp.position(), Vec3.ZERO, 1F, 0xC08CFF);
        }
    }

    /** A cada 60 ticks. */
    static void tickBeacons(MinecraftServer server) {
        for (CityArcana.City c : CityArcana.cities()) {
            ServerLevel sl = null;
            for (CityArcana.Site site : c.sites) {
                if (!site.key().equals(CityArcana.BEACON)) continue;
                if (sl == null) sl = server.getLevel(c.dim);
                if (sl == null || !sl.isLoaded(site.center())) continue;
                int lv = site.level();
                double r = beaconRadius(lv);
                AABB box = new AABB(site.center()).inflate(r, 24, r);
                int seen = 0;
                for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, box,
                        x -> x.isAlive() && !CityArcana.friendly(c, x) && (x.isInvisible() || CityArcana.hostile(c, x)))) {
                    if (le instanceof ServerPlayer sp && (sp.isSpectator() || sp.isCreative())) continue;
                    if (!c.contains(le.getX(), le.getY(), le.getZ())) continue;
                    boolean wasHidden = le.isInvisible();
                    le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, true, false));
                    if (lv >= 4 && le.hasEffect(MobEffects.INVISIBILITY) && CityArcana.hostile(c, le)) le.removeEffect(MobEffects.INVISIBILITY);
                    if (wasHidden && le instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Component.translatable("city_arcana.isekai_mahou.beacon.revealed")
                                .withStyle(ChatFormatting.YELLOW), true);
                    }
                    seen++;
                }
                if (seen > 0 && c.fxReady(CityArcanaNet.REVEAL, sl.getGameTime(), 100)) {
                    Vec3 eye = Vec3.atBottomCenterOf(site.center()).add(0, beaconEye(lv), 0);
                    CityArcanaNet.send(sl, CityArcanaNet.REVEAL, eye, Vec3.ZERO, (float) Math.min(r, 24), 0xFFE9A0);
                    sl.playSound(null, eye.x, eye.y, eye.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4F, 0.6F);
                }
            }
        }
    }

    private ArcanaTowers() {}
}
