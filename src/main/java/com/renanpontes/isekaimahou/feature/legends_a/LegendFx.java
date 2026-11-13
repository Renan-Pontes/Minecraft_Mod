package com.renanpontes.isekaimahou.feature.legends_a;

import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Utilidades visuais/sonoras das Magias Lendárias A (lado servidor). */
public final class LegendFx {
    /** Dourado do Portão do Arsenal (cor padrão quando a receita não tem cor e é Arcana). */
    public static final int GOLD = 0xFFD86B;
    /** Carmesim da Explosão Colossal (cor padrão quando a receita não tem cor). */
    public static final int CRIMSON = 0xFF3B1F;

    /** Dimensão das Câmaras de Provação: lá nada de crateras (a arena é protegida). */
    public static final ResourceLocation TRIAL_REALM = IsekaiMahou.id("trial_realm");

    /** Armas do "tesouro do rei" (Portão do Arsenal): armas nobres e brilhantes. */
    private static final List<Item> TREASURE = List.of(Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.TRIDENT, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE, Items.MACE, Items.IRON_SWORD);
    /** Armas de uma Arma Conjurada comum. */
    private static final List<Item> CONJURED = List.of(Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.TRIDENT, Items.IRON_AXE,
            Items.GOLDEN_SWORD);
    /** Espadas do Campo de Lâminas (um cemitério de espadas: de todo tipo). */
    private static final List<Item> FIELD = List.of(Items.IRON_SWORD, Items.STONE_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.GOLDEN_SWORD, Items.IRON_SWORD, Items.WOODEN_SWORD, Items.IRON_AXE);

    public static ItemStack treasureWeapon(RandomSource r) {
        ItemStack s = new ItemStack(TREASURE.get(r.nextInt(TREASURE.size())));
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    public static ItemStack conjuredWeapon(RandomSource r) {
        ItemStack s = new ItemStack(CONJURED.get(r.nextInt(CONJURED.size())));
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    public static ItemStack fieldSword(RandomSource r) {
        return new ItemStack(FIELD.get(r.nextInt(FIELD.size())));
    }

    /** Cor RGB a usar no visual: a da receita, ou a padrão informada para magias Arcanas, ou a do elemento. */
    public static int colorOf(SpellContext ctx, int arcaneDefault) {
        if (ctx.recipe().color() >= 0) return ctx.recipe().color() & 0xFFFFFF;
        return ctx.element() == Element.ARCANE ? arcaneDefault : ctx.element().color;
    }

    public static ParticleOptions dust(int rgb, float size) {
        // v1.3: brilho fx aditivo (mesmo tamanho do pó do vanilla; respeita o preset gráfico no cliente)
        return com.renanpontes.isekaimahou.core.ElementFx.glow(rgb, size);
    }

    /**
     * Partículas visíveis de LONGE (até 512 blocos, ignorando a opção de partículas reduzidas do cliente):
     * explosões gigantes precisam ser vistas por quem está fora do raio normal de 32 blocos.
     */
    public static void far(ServerLevel level, ParticleOptions p, double x, double y, double z, int count,
                           double dx, double dy, double dz, double speed) {
        for (ServerPlayer sp : level.players()) {
            level.sendParticles(sp, p, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    public static void far(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        far(level, p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Centro do corpo. */
    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /**
     * Superfície do chão abaixo (ou logo acima, se o ponto estiver enterrado) de {@code at}: o topo do primeiro
     * bloco sólido, procurando até {@code maxDown} blocos. null = nada embaixo (no ar).
     */
    @Nullable
    public static Vec3 groundBelow(ServerLevel level, Vec3 at, double maxDown) {
        Vec3 from = at.add(0, 0.6, 0);
        Vec3 to = at.subtract(0, maxDown, 0);
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : null;
    }

    /** Bloco sólido onde algo pode ser cravado (topo exposto com ar em cima), perto de {@code pos}. */
    @Nullable
    public static BlockPos plantable(ServerLevel level, BlockPos pos) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos p = pos.offset(0, dy, 0);
            BlockState st = level.getBlockState(p);
            if (!st.isAir() && st.isCollisionShapeFullBlock(level, p) && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
                return p;
            }
        }
        return null;
    }

    private LegendFx() {}
}
