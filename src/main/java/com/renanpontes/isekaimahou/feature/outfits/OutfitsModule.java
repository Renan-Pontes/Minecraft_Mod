package com.renanpontes.isekaimahou.feature.outfits;

import java.util.Arrays;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "outfits": roupas 3D (GeckoLib). Quinze trajes em quatro graus — I (kit inicial), II (Círculo 3), III (Círculo 5)
 * e IV (Círculo 8):
 * <ul>
 *   <li>os 5 trajes das classes Mágicas (Mago, Bruxa, Warlock, Druida, Necromante): kit e entregas por Círculo;</li>
 *   <li>v1.0: os 10 trajes da v0.9 (Guerreiro, Paladino, Bárbaro, Monge, Cavaleiro, Patrulheiro, Ladino, Bardo,
 *   Artífice, Caçador) são AVULSOS: sem classe, fora do kit e das entregas (as 10 classes novas trazem o traje no próprio
 *   módulo {@code class_<id>}). Continuam no criativo, na reforma e nos saves; só viram reserva se o módulo de uma
 *   classe não registrar armadura ({@link OutfitSet#fallbackFor}, {@link OutfitRewards#outfitFor}).</li>
 * </ul>
 * Cada grau tem modelo, paleta e glowmask próprios; proteção e bônus seguem a categoria ({@link OutfitStats}, com a
 * inflação da v1.0: defesa ×1,5). Os graus II–IV chegam sozinhos ao subir de Círculo ({@link OutfitRewards}), podem ser
 * aprimorados na Mesa de Ferraria e reformados para outro traje na bancada.
 */
public class OutfitsModule implements Module {
    @Override
    public String id() {
        return "outfits";
    }

    @Override
    public void init(IEventBus modBus) {
        OutfitRegistries.register(modBus);
        modBus.addListener(this::onCreativeTabs);
        OutfitRewards.register();

        // Kit inicial: SÓ o grau I vai vestido quando o jogador escolhe o caminho (as 5 Mágicas; as outras 10 classes
        // registram o traje do próprio módulo — a reserva sem módulo sai em OutfitRewards.onPathChosen)
        for (OutfitSet set : OutfitSet.values()) {
            if (set.isGeneric()) continue; // traje avulso (v1.0): sem kit
            for (ArmorItem.Type type : OutfitRegistries.TYPES) {
                ClassKits.setArmor(set.path, type.getSlot(), () -> new ItemStack(OutfitRegistries.piece(set, type).get()));
            }
        }

        // /mahou outfit <classe> [grau]: veste o traje completo (o que estava vestido vai para o inventário)
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("outfit")
                .then(Commands.argument("class", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(OutfitSet.values()).map(s -> s.id), b))
                        .executes(c -> dressCommand(c, OutfitTier.APPRENTICE))
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(OutfitTier.values()).map(t -> t.id), b))
                                .executes(c -> {
                                    OutfitTier tier = OutfitTier.byId(StringArgumentType.getString(c, "tier"));
                                    if (tier == null) {
                                        c.getSource().sendFailure(Component.translatable("command.isekai_mahou.outfit.unknown_tier"));
                                        return 0;
                                    }
                                    return dressCommand(c, tier);
                                })))));
    }

    private static int dressCommand(CommandContext<CommandSourceStack> c, OutfitTier tier) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        OutfitSet set = OutfitSet.byId(StringArgumentType.getString(c, "class"));
        if (set == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.outfit.unknown"));
            return 0;
        }
        ServerPlayer player = c.getSource().getPlayerOrException();
        dress(player, set, tier);
        Component tierName = set.tierName(tier).withStyle(tier.chatColor());
        c.getSource().sendSuccess(() -> set.isGeneric()
                ? Component.translatable("command.isekai_mahou.outfit.done_generic",
                        OutfitRegistries.piece(set, tier, ArmorItem.Type.CHESTPLATE).get().getDescription(), tierName, set.ownerName())
                : Component.translatable("command.isekai_mahou.outfit.done_tier", set.ownerName(), tierName), false);
        return 1;
    }

    /** Veste o traje inteiro; peças antigas vão para o inventário (ou caem no chão se não couber). */
    static void dress(ServerPlayer player, OutfitSet set, OutfitTier tier) {
        for (ArmorItem.Type type : OutfitRegistries.TYPES) {
            ItemStack old = player.getItemBySlot(type.getSlot());
            if (!old.isEmpty() && !player.getInventory().add(old.copy())) player.drop(old.copy(), false);
            player.setItemSlot(type.getSlot(), new ItemStack(OutfitRegistries.piece(set, tier, type).get()));
        }
        player.level().playSound(null, player.blockPosition(), set.equipSound.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Aba "Rituais": um bloco por grau (I, II, III, IV), cada um com os 15 trajes (Mágicas primeiro, depois os avulsos). */
    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_RITUALS.getKey()) return;
        for (OutfitTier tier : OutfitTier.values()) {
            for (OutfitSet set : OutfitSet.values()) {
                for (ArmorItem.Type type : OutfitRegistries.TYPES) e.accept(OutfitRegistries.piece(set, tier, type).get());
            }
        }
    }
}
