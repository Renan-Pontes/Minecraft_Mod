package com.renanpontes.isekaimahou.client.objectives;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Uma linha do rastreador de objetivos ({@link ObjectiveTracker}). Só usa tipos comuns, mas é criada e lida
 * apenas no cliente (os provedores rodam no cliente com os dados que já foram sincronizados).
 *
 * @param title     texto principal (ex.: "Provação de Fogo III")
 * @param detail    segunda linha, menor e cinza (pode ser {@link Component#empty()})
 * @param current   progresso atual (ex.: 3)
 * @param target    meta (ex.: 8); {@code target <= 0} = sem barra nem "n/m"
 * @param rgb       cor de destaque (RGB, sem alpha): faixa lateral, barra e título
 * @param icon      ícone (pode ser {@link ItemStack#EMPTY})
 * @param targetPos ponto para onde a setinha aponta (null = sem seta)
 * @param targetDim dimensão do ponto (null = a dimensão atual do jogador)
 */
public record ObjectiveLine(Component title, Component detail, int current, int target, int rgb, ItemStack icon,
                            @Nullable Vec3 targetPos, @Nullable ResourceKey<Level> targetDim) {

    public ObjectiveLine {
        title = title == null ? Component.empty() : title;
        detail = detail == null ? Component.empty() : detail;
        icon = icon == null ? ItemStack.EMPTY : icon;
    }

    /** Linha simples (título + detalhe), sem progresso nem seta. */
    public static ObjectiveLine of(Component title, Component detail, int rgb, ItemStack icon) {
        return new ObjectiveLine(title, detail, 0, 0, rgb, icon, null, null);
    }

    /** Linha com barra de progresso {@code current/target}. */
    public static ObjectiveLine progress(Component title, Component detail, int current, int target, int rgb, ItemStack icon) {
        return new ObjectiveLine(title, detail, current, target, rgb, icon, null, null);
    }

    /** Cópia com seta apontando para {@code pos} na dimensão {@code dim} (null = dimensão atual). */
    public ObjectiveLine withTarget(Vec3 pos, @Nullable ResourceKey<Level> dim) {
        return new ObjectiveLine(title, detail, current, target, rgb, icon, pos, dim);
    }

    /** Cópia com seta apontando para o centro do bloco. */
    public ObjectiveLine withTarget(BlockPos pos, @Nullable ResourceKey<Level> dim) {
        return withTarget(Vec3.atCenterOf(pos), dim);
    }

    /** Progresso de 0 a 1 (0 se não há meta). */
    public float fraction() {
        return target <= 0 ? 0F : Math.max(0F, Math.min(1F, (float) current / target));
    }
}
