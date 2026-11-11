package com.renanpontes.isekaimahou.feature.armory.client;

import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.armory.WeaponKind;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryWeapon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * Brilho das armas de tier III e IV na mão: faíscas na ponta do cajado/varinha/foice, ao longo da lâmina, nas
 * manoplas, no arco/besta e notas no alaúde, na cor da classe. Só para quem está visível e perto; o próprio jogador em
 * primeira pessoa não vê (não atrapalha a mira).
 */
public final class WeaponAmbience {
    private static final double RANGE_SQR = 48 * 48;
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.9F, 0.12F, 0.18F), 0.8F);
    private static final DustParticleOptions LEAF = new DustParticleOptions(new Vector3f(0.45F, 0.92F, 0.38F), 0.8F);
    private static final DustParticleOptions ARCANE = new DustParticleOptions(new Vector3f(0.55F, 0.72F, 1.0F), 0.7F);
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1.0F, 0.5F, 0.15F), 0.7F);
    private static final DustParticleOptions HOLY = new DustParticleOptions(new Vector3f(1.0F, 0.88F, 0.45F), 0.8F);
    private static final DustParticleOptions RAGE = new DustParticleOptions(new Vector3f(0.85F, 0.15F, 0.1F), 0.8F);
    private static final DustParticleOptions KI = new DustParticleOptions(new Vector3f(0.55F, 0.95F, 0.88F), 0.7F);
    private static final DustParticleOptions STEEL = new DustParticleOptions(new Vector3f(0.82F, 0.86F, 0.95F), 0.7F);
    private static final DustParticleOptions WIND = new DustParticleOptions(new Vector3f(0.75F, 0.95F, 0.65F), 0.6F);
    private static final DustParticleOptions SHADE = new DustParticleOptions(new Vector3f(0.6F, 0.4F, 0.9F), 0.7F);
    private static final DustParticleOptions RUNE = new DustParticleOptions(new Vector3f(0.7F, 0.55F, 1.0F), 0.7F);
    private static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(0.85F, 0.62F, 0.3F), 0.7F);

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || MahouConfig.REDUCED_PARTICLES.get()) return;
        RandomSource rnd = level.random;
        for (Player p : level.players()) {
            if (p.isInvisible() || p.isSpectator() || p.distanceToSqr(mc.player) > RANGE_SQR) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            ArmoryWeapon w = ArmoryWeapon.of(p.getMainHandItem());
            if (w == null || w.tier() < 3) continue;
            int every = w.tier() >= WeaponKind.MAX_TIER ? 3 : 6;
            if ((p.tickCount + p.getId()) % every != 0) continue;
            Vec3 tip = tipPosition(p, w.kind().style, rnd);
            double jx = (rnd.nextDouble() - 0.5) * 0.18;
            double jy = (rnd.nextDouble() - 0.5) * 0.18;
            double jz = (rnd.nextDouble() - 0.5) * 0.18;
            if (w.kind() == WeaponKind.LUTE) {
                // Notas soltas (a cor da nota vai no primeiro parâmetro de velocidade)
                level.addParticle(ParticleTypes.NOTE, tip.x + jx, tip.y + jy + 0.3, tip.z + jz, rnd.nextDouble(), 0, 0);
            } else {
                level.addParticle(particle(w.kind(), rnd), tip.x + jx, tip.y + jy, tip.z + jz, 0, 0.012, 0);
            }
            if (w.tier() >= WeaponKind.MAX_TIER && rnd.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.END_ROD, tip.x, tip.y, tip.z,
                        (rnd.nextDouble() - 0.5) * 0.02, 0.015, (rnd.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    private static ParticleOptions particle(WeaponKind kind, RandomSource rnd) {
        return switch (kind) {
            case MAGE_STAFF -> rnd.nextBoolean() ? ParticleTypes.ENCHANT : ARCANE;
            case WITCH_WAND -> ParticleTypes.WITCH;
            case PACT_BLADE -> rnd.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : BLOOD;
            case DRUID_STAFF -> rnd.nextInt(5) == 0 ? ParticleTypes.HAPPY_VILLAGER : LEAF;
            case REAPER_SCYTHE -> rnd.nextInt(3) == 0 ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME;
            case LONGSWORD -> rnd.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : EMBER;
            case HOLY_MACE -> rnd.nextInt(4) == 0 ? ParticleTypes.END_ROD : HOLY;
            case GREATAXE -> rnd.nextInt(4) == 0 ? ParticleTypes.SMALL_FLAME : RAGE;
            case KI_GAUNTLETS -> rnd.nextInt(5) == 0 ? ParticleTypes.CLOUD : KI;
            case KNIGHT_BLADE -> rnd.nextInt(4) == 0 ? ParticleTypes.ENCHANTED_HIT : STEEL;
            case LONGBOW -> WIND;
            case TWIN_DAGGERS -> rnd.nextInt(3) == 0 ? ParticleTypes.SMOKE : SHADE;
            case LUTE -> HOLY;
            case ARCANE_CROSSBOW -> rnd.nextInt(3) == 0 ? ParticleTypes.ENCHANT : RUNE;
            case HUNTER_CROSSBOW -> AMBER;
        };
    }

    /** Ponto aproximado da ponta da arma (mão direita/esquerda conforme o braço principal, segue o corpo). */
    private static Vec3 tipPosition(Player p, WeaponKind.Style style, RandomSource rnd) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 side = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        if (p.getMainArm() == HumanoidArm.LEFT) side = side.scale(-1);
        double s = p.getBbHeight() / 1.8; // agachado/encolhido: tudo mais baixo
        return switch (style) {
            case STAFF, SCYTHE -> p.position().add(side.scale(0.38)).add(fwd.scale(0.3)).add(0, 1.85 * s, 0);
            case WAND -> p.position().add(side.scale(0.36)).add(fwd.scale(0.8)).add(0, 1.0 * s, 0);
            case BLADE, MACE, AXE -> p.position().add(side.scale(0.38)).add(fwd.scale(0.4 + rnd.nextDouble() * 0.6)).add(0, 0.72 * s, 0);
            case FIST, DAGGER -> p.position().add(side.scale((rnd.nextBoolean() ? 1 : -1) * 0.38)).add(fwd.scale(0.25))
                    .add(0, 0.7 * s, 0);
            case BOW, CROSSBOW -> p.position().add(side.scale(0.36)).add(fwd.scale(0.45)).add(0, 0.85 * s, 0);
            case LUTE -> p.position().add(fwd.scale(0.35)).add(0, 1.1 * s, 0);
        };
    }

    private WeaponAmbience() {}
}
