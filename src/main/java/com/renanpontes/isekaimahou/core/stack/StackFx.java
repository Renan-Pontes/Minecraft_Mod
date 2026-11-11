package com.renanpontes.isekaimahou.core.stack;

import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.api.SpellStack;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Visual e som da Pilha no servidor (partículas, sons) + pacote do letreiro/estilhaço para os clientes perto. */
final class StackFx {
    private static final int ARCANE = 0xB98CFF;

    /** Contramágica bem-sucedida: vidro mágico estilhaçando no ponto, no conjurador e em cada forma cancelada. */
    static void countered(ServerLevel level, PendingState s, @Nullable LivingEntity caster, @Nullable LivingEntity by,
                          List<Vec3> shapes, boolean stunned) {
        int rgb = s.element != null ? s.element.color : ARCANE;
        float r = Mth.clamp(s.radius, 1.2F, 6.0F);
        Vec3 p = s.pos;
        shatter(level, p.add(0, 0.25, 0), r, rgb, 1.0F);
        for (Vec3 c : shapes) {
            if (c.distanceToSqr(p) > 1.0) shatter(level, c.add(0, 0.25, 0), 1.0F, rgb, 0.35F);
        }
        if (caster != null) {
            Vec3 chest = caster.position().add(0, caster.getBbHeight() * 0.55, 0);
            shatter(level, chest, caster.getBbWidth() * 0.6F + 0.6F, rgb, 0.7F);
            level.sendParticles(ParticleTypes.FLASH, chest.x, chest.y, chest.z, 1, 0, 0, 0, 0);
        }

        boolean big = s.tier != SpellStack.Tier.MINOR;
        sound(level, p, SoundEvents.GLASS_BREAK, 1.6F, 0.55F);
        sound(level, p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4F, 0.6F);
        sound(level, p, SoundEvents.BEACON_DEACTIVATE, 1.2F, 1.5F);
        if (caster != null && caster.distanceToSqr(p) > 16) {
            sound(level, caster.position(), SoundEvents.GLASS_BREAK, 1.2F, 0.7F);
        }
        if (big) sound(level, p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.5F, 0.6F);
        if (s.tier == SpellStack.Tier.ULTIMATE) level.playSound(null, p.x, p.y, p.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.9F, 1.4F);

        int flags = (s.boss ? StackPayloads.FX_BOSS : 0) | (stunned ? StackPayloads.FX_STUNNED : 0) | (by != null ? StackPayloads.FX_HAS_BY : 0);
        Component casterName = caster != null ? caster.getDisplayName() : s.name;
        Component spell = s.castName != null ? s.castName : Component.translatable("stack.isekai_mahou.attack");
        PacketDistributor.sendToPlayersNear(level, null, p.x, p.y, p.z, SpellStack.VIEW_RANGE,
                new StackPayloads.Countered(s.id, p.x, p.y, p.z, r, caster != null ? caster.getId() : -1, casterName, spell,
                        by != null ? by.getDisplayName() : Component.empty(), s.tier.ordinal(), rgb, flags));
    }

    /** Poder insuficiente: a contramágica "bate" e se desfaz em fumaça. */
    static void resisted(ServerLevel level, PendingState s, @Nullable LivingEntity caster) {
        Vec3 c = focus(s, caster);
        ring(level, c, 1.1, 0x9A9AB0, 16, 1.0F);
        level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 14, 0.35, 0.3, 0.35, 0.02);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.1);
        sound(level, c, SoundEvents.SHIELD_BLOCK, 0.8F, 1.4F);
        sound(level, c, SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.6F);
    }

    /** Chefe na recarga de 12 s: escudo dourado. */
    static void bossResists(ServerLevel level, PendingState s, @Nullable LivingEntity caster) {
        Vec3 c = focus(s, caster);
        float r = caster != null ? caster.getBbWidth() * 0.7F + 0.6F : 1.4F;
        ring(level, c, r, 0xFFD86B, 24, 1.3F);
        ring(level, c.add(0, 0.6, 0), r * 0.8, 0xFFF2B0, 18, 1.0F);
        sound(level, c, SoundEvents.SHIELD_BLOCK, 1.0F, 0.7F);
        sound(level, c, SoundEvents.ANVIL_LAND, 0.35F, 1.8F);
    }

    /** Não pode ser contrariada / protegida: faísca arcana sem efeito. */
    static void warded(ServerLevel level, PendingState s, @Nullable LivingEntity caster) {
        Vec3 c = focus(s, caster);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 12, 0.4, 0.4, 0.4, 0.15);
        ring(level, c, 0.9, 0xE0E0FF, 12, 0.9F);
        sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.6F);
    }

    // ------------------------------------------------------------------ peças

    /** Estilhaço: vidro roxo voando, anel rúnico abrindo para fora e faíscas. {@code scale} reduz a quantidade. */
    private static void shatter(ServerLevel level, Vec3 c, float radius, int rgb, float scale) {
        BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PURPLE_STAINED_GLASS.defaultBlockState());
        BlockParticleOption pane = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
        int n = Math.max(6, Math.round(36 * scale));
        level.sendParticles(glass, c.x, c.y + 0.3, c.z, n, radius * 0.35, 0.35, radius * 0.35, 0.18);
        level.sendParticles(pane, c.x, c.y + 0.3, c.z, n / 2, radius * 0.3, 0.3, radius * 0.3, 0.15);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y + 0.4, c.z, Math.round(24 * scale), radius * 0.3, 0.4, radius * 0.3, 0.25);
        ring(level, c, radius, rgb, Math.max(10, Math.round(radius * 10 * scale)), 1.5F);
        // Faíscas abrindo para fora (count 0 = o deslocamento vira a velocidade)
        int sparks = Math.max(8, Math.round(28 * scale));
        for (int i = 0; i < sparks; i++) {
            double a = i * Math.PI * 2 / sparks;
            double cx = Math.cos(a), cz = Math.sin(a);
            level.sendParticles(ParticleTypes.END_ROD, c.x + cx * radius * 0.5, c.y + 0.2, c.z + cz * radius * 0.5, 0, cx, 0.08, cz, 0.22);
        }
    }

    private static void ring(ServerLevel level, Vec3 c, double radius, int rgb, int count, float size) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F,
                (rgb & 255) / 255F), size);
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            level.sendParticles(dust, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    private static Vec3 focus(PendingState s, @Nullable LivingEntity caster) {
        return caster != null ? caster.position().add(0, caster.getBbHeight() * 0.55, 0) : s.pos.add(0, 0.5, 0);
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private StackFx() {}
}
