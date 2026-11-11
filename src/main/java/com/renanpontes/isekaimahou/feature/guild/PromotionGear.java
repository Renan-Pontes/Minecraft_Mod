package com.renanpontes.isekaimahou.feature.guild;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Presente de equipamento do Arsenal (módulo armory, referenciado só por id) ao ser promovido na Guilda — a arma da classe
 * (qualquer uma das 15: Mágicas, Guerreiras e À Distância).
 * <p>
 * Cada rank tem candidatos em ordem de preferência: E arma I da classe · D Talismã da Vitalidade · C Anel da Guarda ·
 * B arma II · A arma III · S arma IV ou Sigilo do Arquimago. O que não existe no jogo (módulo ausente) e o que o jogador
 * JÁ TEM (inventário, mãos, armadura ou Curios; para armas, uma do mesmo tipo com tier igual ou maior) é pulado — os
 * marcos de nível e as Provações também dão esses itens, e uma cópia não serviria para nada. Nesse caso vem o Molde de
 * Ferraria Arcano (melhora o equipamento na mesa de ferraria) e, em último caso, Moedas da Guilda.
 */
public final class PromotionGear {
    private static final String TEMPLATE = "arcane_upgrade_template";

    /**
     * Candidatos por rank alcançado: "weapon:N" (arma tier N da classe), "template:N" (N moldes) ou o id de um item.
     * v0.9: o Cavaleiro ganha o Escudo de Cavaleiro (armory {@code knight_shield}) no rank E se já tiver a espada.
     */
    private static List<String> candidates(GuildRank rank, MagicPath path) {
        boolean knight = path == MagicPath.KNIGHT;
        return switch (rank) {
            case F -> List.of();
            case E -> knight ? List.of("weapon:1", "knight_shield", "template:1") : List.of("weapon:1", "template:1");
            case D -> knight ? List.of("vitality_charm", "knight_shield", "template:1") : List.of("vitality_charm", "template:1");
            case C -> List.of("ward_ring", "vitality_charm", "template:1");
            case B -> List.of("weapon:2", "ward_ring", "template:2");
            case A -> List.of("weapon:3", "template:2");
            case S -> List.of("weapon:4", "archmage_sigil", "template:3");
        };
    }

    /**
     * O presente que o jogador receberia AGORA ao chegar a {@code rank} (sem efeitos colaterais: serve também para mostrar
     * na tela do Mestre). Vazio só para o rank F (que não tem promoção).
     */
    public static ItemStack pick(ServerPlayer sp, GuildRank rank) {
        MagicPath path = MagicApi.get(sp).path();
        for (String c : candidates(rank, path == null ? MagicPath.NONE : path)) {
            ItemStack s = resolve(sp, c);
            if (!s.isEmpty()) return s;
        }
        return rank == GuildRank.F ? ItemStack.EMPTY : new ItemStack(GuildContent.GUILD_COIN.get(), 8 * rank.level());
    }

    private static ItemStack resolve(ServerPlayer sp, String candidate) {
        int colon = candidate.indexOf(':');
        String kind = colon < 0 ? candidate : candidate.substring(0, colon);
        int n = colon < 0 ? 1 : Integer.parseInt(candidate.substring(colon + 1));
        if (kind.equals("weapon")) {
            String prefix = weaponPrefix(MagicApi.get(sp).path());
            Item weapon = item(prefix + "_t" + n);
            if (weapon == null || ownedWeaponTier(sp, prefix) >= n) return ItemStack.EMPTY;
            return new ItemStack(weapon);
        }
        if (kind.equals("template")) {
            Item template = item(TEMPLATE);
            return template == null ? ItemStack.EMPTY : new ItemStack(template, n);
        }
        Item it = item(kind);
        return it == null || owns(sp, it) ? ItemStack.EMPTY : new ItemStack(it);
    }

    /**
     * Prefixo da arma da classe no Arsenal ({@code <prefixo>_t1..t4}) para as 15 classes — vem do núcleo
     * ({@link MagicPath#weaponPrefix()}: cajado, varinha, lâmina do pacto, cajado druídico, foice; espada longa, maça sagrada,
     * machado grande, manoplas de ki, lâmina de cavaleiro; arco longo, adagas gêmeas, alaúde, besta arcana, besta de caçador).
     * Sem classe: o cajado do Mago.
     */
    static String weaponPrefix(MagicPath path) {
        // v1.0: a arma de tier da classe, a herdada do Arsenal ou a genérica da categoria (ver ClassKits)
        return com.renanpontes.isekaimahou.core.ClassKits.tieredWeaponPrefix(path);
    }

    /** Item do mod pelo id, ou null se o módulo que o registra não está presente. */
    private static Item item(String path) {
        Item it = BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(path)).orElse(Items.AIR);
        return it == Items.AIR ? null : it;
    }

    /** Maior tier de arma "<prefixo>_tN" que o jogador carrega (0 = nenhuma). */
    private static int ownedWeaponTier(ServerPlayer sp, String prefix) {
        int best = 0;
        String start = prefix + "_t";
        var inv = sp.getInventory();
        for (List<ItemStack> list : List.of(inv.items, inv.armor, inv.offhand)) {
            for (ItemStack s : list) {
                if (s.isEmpty()) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
                if (!id.getNamespace().equals(IsekaiMahou.MODID) || !id.getPath().startsWith(start)) continue;
                String tier = id.getPath().substring(start.length());
                if (tier.length() == 1 && Character.isDigit(tier.charAt(0))) best = Math.max(best, tier.charAt(0) - '0');
            }
        }
        return best;
    }

    /** O jogador tem o item no inventário/mãos/armadura ou equipado num slot do Curios. */
    private static boolean owns(ServerPlayer sp, Item item) {
        if (sp.getInventory().contains(s -> s.is(item))) return true;
        return CuriosApi.getCuriosInventory(sp).map(h -> h.isEquipped(item)).orElse(false);
    }

    private PromotionGear() {}
}
