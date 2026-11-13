package com.renanpontes.isekaimahou.feature.endless_abyss;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Rewards;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Recompensas do Abismo Infinito. Balanço (palavra do dono: nada de poder fácil, upar pesa mais que item):
 * <ul>
 *   <li>todo andar: um pouco de XP ({@code 40 + 12·andar}; andar de chefe ×4) — bem menos que um nível pós-100 pede;</li>
 *   <li>andar de chefe (a cada 5): Estilhaços do Abismo e Cristais de Mana; se era uma Sombra, +1 Fragmento de Sombra;</li>
 *   <li>marco de 10 andares, só na PRIMEIRA vez (recorde novo): Núcleo do Abismo + um item raro que roda numa tabela;
 *       repetir o marco dá só Estilhaços extras.</li>
 * </ul>
 * Contadores espelhados no MagicData (para outros módulos lerem por texto): {@code abyss.floors},
 * {@code abyss.best_floor}, {@code abyss.shadow_wins}, {@code abyss.shadow.<chefe>}.
 */
final class AbyssRewards {
    static final int PURPLE = 0x9A5CFF;

    /** XP de um andar vencido. */
    static int floorXp(int floor, boolean bossFloor) {
        int xp = 40 + 12 * floor;
        return bossFloor ? xp * 4 : xp;
    }

    /**
     * Paga o andar para um jogador. {@code oldBest} = recorde dele ANTES deste andar (para saber se o marco é inédito).
     */
    static void floor(ServerPlayer p, int floor, boolean bossFloor, @Nullable String shadowPath, int oldBest) {
        MagicApi.giveXp(p, floorXp(floor, bossFloor), "abyss");
        MagicApi.addCounter(p, "abyss.floors", 1);
        MagicData d = MagicApi.get(p);
        if (floor > d.counter("abyss.best_floor")) MagicApi.addCounter(p, "abyss.best_floor", floor - d.counter("abyss.best_floor"));
        if (!bossFloor) return;

        Rewards.give(p, new ItemStack(AbyssContent.ABYSS_SHARD.get(), 1 + floor / 10));
        Rewards.give(p, new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 2));
        if (shadowPath != null) {
            Rewards.give(p, new ItemStack(AbyssContent.SHADOW_FRAGMENT.get()));
            MagicApi.addCounter(p, "abyss.shadow_wins", 1);
            MagicApi.addCounter(p, "abyss.shadow." + shadowPath, 1);
        }

        if (floor % AbyssFloors.CHECKPOINT_EVERY != 0) return;
        if (floor > oldBest) {
            ItemStack core = new ItemStack(AbyssContent.ABYSS_CORE.get());
            Rewards.give(p, core);
            ItemStack rare = milestoneRare(p, floor);
            Rewards.give(p, rare);
            Rewards.toast(p, Component.translatable("toast.isekai_mahou.endless_abyss.milestone", floor),
                    Component.translatable("toast.isekai_mahou.endless_abyss.milestone_sub", rare.getHoverName()), core, PURPLE);
        } else {
            Rewards.give(p, new ItemStack(AbyssContent.ABYSS_SHARD.get(), 3));
        }
    }

    /** O item raro do marco: a tabela roda a cada 50 andares (10, 20, 30, 40, 50, 60 = de novo o primeiro...). */
    private static ItemStack milestoneRare(ServerPlayer p, int floor) {
        return switch ((floor / AbyssFloors.CHECKPOINT_EVERY - 1) % 5) {
            case 0 -> new ItemStack(CoreRegistries.GUARDIAN_CORES.get(Element.BASE.get(p.getRandom().nextInt(Element.BASE.size()))).get());
            case 1 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2);
            case 2 -> new ItemStack(Items.NETHERITE_INGOT, 2);
            case 3 -> new ItemStack(Items.TOTEM_OF_UNDYING);
            default -> new ItemStack(Items.NETHER_STAR);
        };
    }

    /**
     * Paga uma Luta das Sombras avulsa. {@code winsNow} = vitórias contra ESSA Sombra contando esta (1 = primeira vez:
     * bônus de um Núcleo do Abismo).
     */
    static void shadowFight(ServerPlayer p, String bossPath, int winsNow, float bossMaxHealth) {
        // XP proporcional à vida da Sombra, com teto — repetível, então fica abaixo do que o chefe de verdade paga
        int xp = Math.max(200, Math.min(2500, Math.round(bossMaxHealth * 0.5F)));
        MagicApi.giveXp(p, xp, "abyss");
        MagicApi.addCounter(p, "abyss.shadow_wins", 1);
        MagicApi.addCounter(p, "abyss.shadow." + bossPath, 1);
        ItemStack frag = new ItemStack(AbyssContent.SHADOW_FRAGMENT.get(), 2);
        Rewards.give(p, frag);
        Rewards.give(p, new ItemStack(AbyssContent.ABYSS_SHARD.get(), 2));
        if (winsNow == 1) Rewards.give(p, new ItemStack(AbyssContent.ABYSS_CORE.get()));
        Rewards.toast(p, Component.translatable("toast.isekai_mahou.endless_abyss.shadow_win").withStyle(ChatFormatting.BOLD),
                Component.translatable("toast.isekai_mahou.endless_abyss.shadow_win_sub", AbyssShadows.bossName(bossPath), winsNow),
                frag, PURPLE);
    }

    private AbyssRewards() {}
}
