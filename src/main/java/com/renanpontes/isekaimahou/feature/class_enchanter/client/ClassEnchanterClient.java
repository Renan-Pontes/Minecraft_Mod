package com.renanpontes.isekaimahou.feature.class_enchanter.client;

import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchRegistries;
import com.renanpontes.isekaimahou.feature.class_enchanter.GearEnchant;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterArmorItem;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterCodexItem;
import com.renanpontes.isekaimahou.module.ClientModule;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

/**
 * Parte de cliente do módulo "class_enchanter": renderer GeckoLib do traje, do Santuário do Soberano, as manoplas em par,
 * a aba "Encantador" no menu B (registrada quando o jogador local é Encantador) e a dica dos itens encantados.
 */
public class ClassEnchanterClient implements ClientModule {
    public static final String TAB_ID = "enchanter";
    private static boolean tabRegistered;

    @Override
    public void initClient(IEventBus modBus) {
        EnchanterArmorItem.renderFactory = ArmorProvider::new;
        EnchanterCodexItem.openScreen = ClassEnchanterClient::openTab;
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(EnchRegistries.SANCTUM.get(), SanctumRenderer::new));
        modBus.addListener(EntityRenderersEvent.AddLayers.class, TwinGauntletsRender::addLayers);
        NeoForge.EVENT_BUS.addListener(RenderHandEvent.class, TwinGauntletsRender::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> ensureTab(false));
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, ClassEnchanterClient::onTooltip);
    }

    /** A aba aparece para o Encantador (o Códex abre a aba mesmo antes da primeira sincronização). */
    static void ensureTab(boolean force) {
        if (tabRegistered) return;
        if (!force && (Minecraft.getInstance().player == null || ClientMagic.data().path() != MagicPath.ENCHANTER)) return;
        tabRegistered = true;
        MenuTabs.register(TAB_ID, 16, Component.translatable("screen.isekai_mahou.enchanter.tab"), EnchanterTab::new);
    }

    static void openTab() {
        ensureTab(true);
        StatusScreen.open(TAB_ID);
    }

    private static void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        if (cd != null && cd.contains(GearEnchant.MARK)) {
            e.getToolTip().add(Component.translatable("tooltip.isekai_mahou.enchanter.temp_enchanted").withStyle(ChatFormatting.AQUA));
        }
    }

    /** Entrega ao GeckoLib o renderer do traje (um por grau, criado na primeira vez que aparece). */
    static final class ArmorProvider implements GeoRenderProvider {
        private final EnchanterArmorItem item;
        private EnchanterArmorRenderer renderer;

        ArmorProvider(EnchanterArmorItem item) {
            this.item = item;
        }

        @Override
        public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(T entity, ItemStack stack, EquipmentSlot slot,
                                                                          HumanoidModel<T> original) {
            if (renderer == null) renderer = new EnchanterArmorRenderer(item.tier());
            return renderer;
        }
    }
}
