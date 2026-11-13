package com.renanpontes.isekaimahou.feature.class_gambler;

import com.renanpontes.isekaimahou.core.anim.MahouAnims;

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
        // anel de naipes, fumaça do azar, fogos da sorte: desenhados no cliente (GbFxClient)
        GbFxNet.at(level, GbFxNet.ROLL, head, Vec3.ZERO, 1F, b.rgb, who.getId(), b.ordinal());
        switch (b) {
            case BUST -> {
                sound(level, head, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
                sound(level, head, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.8F, 0.5F);
            }
            case BAD -> sound(level, head, SoundEvents.NOTE_BLOCK_BASS.value(), 0.9F, 0.7F);
            case LOW -> sound(level, head, SoundEvents.NOTE_BLOCK_HAT.value(), 0.7F, 0.8F);
            case EVEN -> sound(level, head, SoundEvents.NOTE_BLOCK_HAT.value(), 0.7F, 1.2F);
            case GOOD -> sound(level, head, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.2F);
            case GREAT -> sound(level, head, SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, 1.4F);
            case NATURAL -> {
                sound(level, head, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.0F);
                sound(level, head, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.3F);
            }
            case JACKPOT -> sound(level, head, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.5F);
        }
    }

    /** Moedinhas de ouro/esmeralda saltando (partícula de item). */
    public static void coins(ServerLevel level, Vec3 at, int count) {
        GbFxNet.at(level, GbFxNet.COINS, at, Vec3.ZERO, count, GOLD, -1);
    }

    /** Explosão do JACKPOT: os três 7, fichas, fogos (cliente) e acorde de sinos. */
    public static void jackpotBurst(ServerLevel level, LivingEntity who) {
        Vec3 c = who.position().add(0, 1.0, 0);
        // os três 7, a explosão de fichas, fogos e raios dourados: coreografia do cliente
        GbFxNet.at(level, GbFxNet.JACKPOT, who.position(), Vec3.ZERO, 1F, GOLD, who.getId());
        MahouAnims.play(who, "taunt");
        sound(level, c, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
        sound(level, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.1F);
        sound(level, c, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 0.75F);
        sound(level, c, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.0F);
        sound(level, c, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.26F);
    }

    /** Aura do Jackpot: espiral dourada e rosa subindo (a cada 2 ticks). */
    public static void jackpotAura(ServerLevel level, LivingEntity who, long age) {
        GbFxNet.at(level, GbFxNet.JACKPOT_AURA, who.position(), Vec3.ZERO, 1F, GOLD, who.getId(), (int) (age & 0x7FFFFF));
    }

    /** Aura do Jackpot IMORTAL: coluna verde-água de "regeneração infinita" e notas musicais subindo (a cada 3 ticks). */
    public static void immortalAura(ServerLevel level, LivingEntity who, long age) {
        GbFxNet.at(level, GbFxNet.IMMORTAL_AURA, who.position(), Vec3.ZERO, 1F, 0x4AFFB4, who.getId(), (int) (age & 0x7FFFFF));
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
        GbFxNet.at(level, GbFxNet.CARD_BURST, at, Vec3.ZERO, 1F, rgb, -1);
        sound(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 0.6F, 1.6F);
    }

    /** Anel no chão (área das mesas: roleta, dados, cassino). */
    public static void groundRing(ServerLevel level, Vec3 center, double radius, int rgb, int points) {
        GbFxNet.at(level, GbFxNet.GROUND_RING, center, Vec3.ZERO, (float) radius, rgb, -1);
    }

    /** Explosão de área na cor (raio em blocos). */
    public static void blast(ServerLevel level, Vec3 c, double radius, int rgb) {
        GbFxNet.at(level, GbFxNet.BLAST, c, Vec3.ZERO, (float) radius, rgb, -1);
    }

    private GbFx() {}
}
