package com.renanpontes.isekaimahou.feature.class_gambler;

import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckBand;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Visual e som do Apostador no servidor (partículas mandadas a quem está perto; nada pesado por tick). */
public final class GbFx {
    public static final int GOLD = 0xFFC94A;
    public static final int PINK = 0xFF4AD2;
    public static final int FELT = 0x1E7A4A;

    public static ParticleOptions dust(int rgb, float size) {
        return WeaponFx.dust(rgb, size);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Anel de naipes sobre a cabeça na cor da faixa + som da faixa (grave no azar, sino na sorte). */
    public static void roll(ServerLevel level, LivingEntity who, LuckService.Roll r) {
        LuckBand b = r.band();
        Vec3 head = who.position().add(0, who.getBbHeight() + 0.45, 0);
        int points = b.good ? 12 : 8;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(dust(b.rgb, b.good ? 1.1F : 0.8F), head.x + Math.cos(a) * 0.55, head.y, head.z + Math.sin(a) * 0.55,
                    1, 0, 0.02, 0, 0);
        }
        switch (b) {
            case BUST -> {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, head.x, head.y, head.z, 6, 0.25, 0.1, 0.25, 0.01);
                sound(level, head, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
                sound(level, head, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.8F, 0.5F);
            }
            case BAD -> {
                level.sendParticles(ParticleTypes.SMOKE, head.x, head.y, head.z, 6, 0.25, 0.1, 0.25, 0.01);
                sound(level, head, SoundEvents.NOTE_BLOCK_BASS.value(), 0.9F, 0.7F);
            }
            case LOW -> sound(level, head, SoundEvents.NOTE_BLOCK_HAT.value(), 0.7F, 0.8F);
            case EVEN -> sound(level, head, SoundEvents.NOTE_BLOCK_HAT.value(), 0.7F, 1.2F);
            case GOOD -> {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, head.x, head.y, head.z, 5, 0.3, 0.15, 0.3, 0);
                sound(level, head, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.2F);
            }
            case GREAT -> {
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, head.x, head.y, head.z, 12, 0.35, 0.2, 0.35, 0.1);
                sound(level, head, SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, 1.4F);
            }
            case NATURAL -> {
                level.sendParticles(ParticleTypes.FIREWORK, head.x, head.y, head.z, 16, 0.3, 0.2, 0.3, 0.08);
                coins(level, head, 6);
                sound(level, head, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.0F);
                sound(level, head, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.3F);
            }
            case JACKPOT -> {
                level.sendParticles(ParticleTypes.FIREWORK, head.x, head.y, head.z, 24, 0.4, 0.3, 0.4, 0.1);
                coins(level, head, 10);
            }
        }
    }

    /** Moedinhas de ouro/esmeralda saltando (partícula de item). */
    public static void coins(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), at.x, at.y, at.z,
                count, 0.3, 0.2, 0.3, 0.15);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.EMERALD)), at.x, at.y, at.z,
                Math.max(1, count / 3), 0.3, 0.2, 0.3, 0.15);
    }

    /** Explosão do JACKPOT: totem, fogos, moedas e acorde de sinos. */
    public static void jackpotBurst(ServerLevel level, LivingEntity who) {
        Vec3 c = who.position().add(0, 1.0, 0);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 60, 0.6, 0.8, 0.6, 0.5);
        level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 0.6, c.z, 40, 0.8, 0.8, 0.8, 0.15);
        coins(level, c.add(0, 0.8, 0), 24);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            level.sendParticles(dust(i % 2 == 0 ? GOLD : PINK, 1.4F), c.x + Math.cos(a) * 1.4, c.y - 0.8, c.z + Math.sin(a) * 1.4,
                    1, 0, 0.05, 0, 0);
        }
        sound(level, c, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
        sound(level, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.1F);
        sound(level, c, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 0.75F);
        sound(level, c, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.0F);
        sound(level, c, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.26F);
    }

    /** Aura do Jackpot: espiral dourada e rosa subindo (a cada 2 ticks). */
    public static void jackpotAura(ServerLevel level, LivingEntity who, long age) {
        double a = age * 0.45;
        double y = who.getY() + (age % 20) * 0.1;
        for (int k = 0; k < 2; k++) {
            double ang = a + Math.PI * k;
            level.sendParticles(dust(k == 0 ? GOLD : PINK, 1.0F), who.getX() + Math.cos(ang) * 0.8, y, who.getZ() + Math.sin(ang) * 0.8,
                    1, 0, 0, 0, 0);
        }
        if (age % 10 == 0) coins(level, who.position().add(0, who.getBbHeight() + 0.2, 0), 2);
    }

    /** Aura do Jackpot IMORTAL: coluna verde-água de "regeneração infinita" e notas musicais subindo (a cada 3 ticks). */
    public static void immortalAura(ServerLevel level, LivingEntity who, long age) {
        double h = who.getBbHeight();
        double a = age * 0.6;
        for (int k = 0; k < 3; k++) {
            double ang = a + Math.PI * 2 * k / 3;
            double y = who.getY() + ((age * 0.12 + k * 0.6) % (h + 0.6));
            level.sendParticles(dust(k == 0 ? 0x4AFFB4 : k == 1 ? 0xFFFFFF : PINK, 1.0F), who.getX() + Math.cos(ang) * 1.0, y,
                    who.getZ() + Math.sin(ang) * 1.0, 1, 0, 0, 0, 0);
        }
        if (age % 6 == 0) level.sendParticles(ParticleTypes.NOTE, who.getX(), who.getY() + h + 0.4, who.getZ(), 1, 0.4, 0.1, 0.4, 1.0);
        if (age % 12 == 0) level.sendParticles(ParticleTypes.HEART, who.getX(), who.getY() + h * 0.6, who.getZ(), 1, 0.4, 0.3, 0.4, 0);
    }

    /** Cor de cada "Reach" do Domínio da Morte Ociosa: verde, vermelho, ouro e arco-íris (que gira). */
    public static int reachColor(int tier, long age) {
        return switch (tier) {
            case 1 -> 0x3AE07A;
            case 2 -> 0xFF3B3B;
            case 3 -> GOLD;
            case 4 -> Mth.hsvToRgb((age % 40) / 40F, 0.8F, 1F) & 0xFFFFFF;
            default -> 0xFFFFFF;
        };
    }

    /** Estouro cinético da carta (homenagem ao Gambit): faísca, fumacinha e um estalo. */
    public static void cardBurst(ServerLevel level, Vec3 at, int rgb) {
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(dust(rgb, 1.2F), at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.3);
        sound(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 0.6F, 1.6F);
    }

    /** Anel no chão (área das mesas: roleta, dados, cassino). */
    public static void groundRing(ServerLevel level, Vec3 center, double radius, int rgb, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(dust(rgb, 1.0F), center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    /** Explosão de área na cor (raio em blocos). */
    public static void blast(ServerLevel level, Vec3 c, double radius, int rgb) {
        int n = Mth.clamp((int) (radius * 10), 10, 60);
        level.sendParticles(dust(rgb, 1.5F), c.x, c.y + 0.4, c.z, n, radius * 0.4, 0.4, radius * 0.4, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.4, c.z, Math.max(1, (int) (radius / 2)), radius * 0.3, 0.2, radius * 0.3, 0);
        groundRing(level, c, radius, rgb, Mth.clamp((int) (radius * 8), 16, 64));
    }

    private GbFx() {}
}
