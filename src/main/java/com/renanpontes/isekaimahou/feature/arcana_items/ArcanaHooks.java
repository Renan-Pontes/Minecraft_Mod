package com.renanpontes.isekaimahou.feature.arcana_items;

import java.util.function.Consumer;

import net.minecraft.world.InteractionHand;

/** Ganchos de cliente (preenchidos pelo ArcanaItemsClient): o código comum nunca toca em classes de cliente. */
public final class ArcanaHooks {
    /** Abre a tela de escolha da magia a gravar no Pergaminho de Receita em Branco que está nesta mão. */
    public static volatile Consumer<InteractionHand> openInscribe = hand -> {};

    private ArcanaHooks() {}
}
