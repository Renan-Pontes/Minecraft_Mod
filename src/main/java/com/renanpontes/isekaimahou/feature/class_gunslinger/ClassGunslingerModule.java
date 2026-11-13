package com.renanpontes.isekaimahou.feature.class_gunslinger;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.feature.class_gunslinger.glyph.GunGlyphs;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.GsOutfitTier;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.ManaGunItem;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Módulo "class_gunslinger" (v1.0) — o PISTOLEIRO (Destreza, recurso Foco): "seu mana permitiu a criação de diferentes
 * armas mágicas; lança magia através delas, um pistoleiro clássico".
 * <ul>
 *   <li>Armas de mana com modelo 3D: revólver (kit, tiers I–IV), pistola, escopeta, pistolas duplas e fuzil — tiro
 *       básico, tambor, recarga (tecla F) que gasta Foco, animações de tiro/recarga (playerAnimator).</li>
 *   <li>Glifos de origem montáveis no Grimório: formas Bala, Rajada, Tiro Carregado, Tiro Ricochete, Bala Perfurante,
 *       G "Doze Badaladas"; munições Incendiária/Explosiva/Marcar Alvo; aumento Leque do Cão; gatilho Ao Recarregar.</li>
 *   <li>Caminho do Gatilho (5 ranks, {@code ritual_rank}), com o Duelo ao Meio-Dia contra o Forasteiro Espectral; aba
 *       própria no menu B.</li>
 *   <li>Traje de 4 graus (GeckoLib) no kit e nos Círculos 3/5/8; bônus de classe; magias iniciais; 2 lendas.</li>
 * </ul>
 */
public class ClassGunslingerModule implements Module {
    @Override
    public String id() {
        return "class_gunslinger";
    }

    @Override
    public void init(IEventBus modBus) {
        GsRegistries.register(modBus);
        GsNetwork.register(modBus);
        modBus.addListener(GsFxNet::register);
        GunGlyphs.register();
        GsLegends.register();
        GsEvents.register();
        GsCommands.register();
        modBus.addListener(FMLCommonSetupEvent.class, GsLegends::validate);

        // Kit: o Revólver de Mana (arma exigida pelo MagicPath) e o traje grau I vestido
        ClassKits.addItem(MagicPath.GUNSLINGER, () -> new ItemStack(GsRegistries.MANA_REVOLVER.get()));
        for (ArmorItem.Type type : GsOutfitTier.TYPES) {
            ClassKits.setArmor(MagicPath.GUNSLINGER, type.getSlot(),
                    () -> new ItemStack(GsRegistries.piece(GsOutfitTier.APPRENTICE, type).get()));
        }
        // Segurando o gatilho de uma arma automática carregada, parado e agachado: conta como "mirando" para o Foco
        ClassResources.addAimItem(s -> s.getItem() instanceof ManaGunItem && ManaGunItem.rounds(s) > 0);
    }
}
