package com.renanpontes.isekaimahou.feature.gear_plus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.gear_plus.item.GemItem;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.CustomData;

/**
 * <b>Gemas com encaixe</b> — palavra do dono: "só pra algumas classes físicas (como rune knight ou ferreiro)".
 * Só o Ferreiro, o Cavaleiro Rúnico e o Encantador ENCAIXAM gemas e só neles a gema encaixada funciona: passar a
 * arma para outra classe não leva o bônus junto.
 * <ul>
 *   <li>Armas: até 3 encaixes (nível 10 / 35 / 60). Armaduras: 1 encaixe por peça (nível 20).</li>
 *   <li>Lapidações melhores pedem nível ({@link GemType#GRADE_LEVEL}).</li>
 *   <li>Encaixar: no inventário, pegue a gema e clique com o botão DIREITO no equipamento. O Cinzel de Lapidário tira a
 *       última gema do mesmo jeito (a gema volta inteira).</li>
 * </ul>
 * As gemas ficam guardadas no próprio item (dados personalizados, chave {@value #KEY}: lista de ids).
 */
public final class Gems {
    public static final String KEY = "isekai_mahou_gems";
    public static final int WEAPON_SOCKETS = 3;
    public static final int ARMOR_SOCKETS = 1;
    /** Nível do jogador para usar o 1º, 2º e 3º encaixe de uma arma. */
    public static final int[] WEAPON_LEVELS = {10, 35, 60};
    public static final int ARMOR_LEVEL = 20;

    public enum Code { OK, WRONG_CLASS, NOT_SOCKETABLE, FULL, NEED_LEVEL, GRADE_LEVEL }

    /** Resultado de uma tentativa de encaixe ({@code level} = nível pedido, quando faz sentido). */
    public record Result(Code code, int level) {}

    /** Dados mágicos do jogador nos dois lados (no cliente só existe a cópia do jogador local). */
    public static MagicData magic(Player player) {
        return player.level().isClientSide ? ClientMagic.data() : MagicApi.get(player);
    }

    /** Classes que encaixam gemas. */
    public static boolean canUse(MagicPath path) {
        return path == MagicPath.SMITH || path == MagicPath.KNIGHT || path == MagicPath.ENCHANTER;
    }

    public static boolean isArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem;
    }

    /** Arma ou ferramenta de mão: tem dano de ataque, é de tier, dispara projéteis ou é a arma de uma classe. */
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() != 1 || isArmor(stack)) return false;
        Item item = stack.getItem();
        if (item instanceof TieredItem || item instanceof ProjectileWeaponItem || item instanceof TridentItem
                || item instanceof MaceItem || WeaponBond.isAnyClassWeapon(stack)) return true;
        boolean[] attack = {false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.is(Attributes.ATTACK_DAMAGE) && mod.amount() > 0) attack[0] = true;
        });
        return attack[0];
    }

    public static int maxSockets(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() != 1) return 0;
        if (isArmor(stack)) return ARMOR_SOCKETS;
        return isWeapon(stack) ? WEAPON_SOCKETS : 0;
    }

    /** Encaixes que o jogador deste nível consegue usar no item. */
    public static int unlocked(int level, ItemStack stack) {
        if (isArmor(stack)) return level >= ARMOR_LEVEL ? ARMOR_SOCKETS : 0;
        int n = 0;
        for (int need : WEAPON_LEVELS) if (level >= need) n++;
        return Math.min(n, maxSockets(stack));
    }

    /** Nível pedido pelo encaixe de número {@code index} (0 = primeiro) do item. */
    public static int levelFor(ItemStack stack, int index) {
        if (isArmor(stack)) return ARMOR_LEVEL;
        return WEAPON_LEVELS[Math.max(0, Math.min(WEAPON_LEVELS.length - 1, index))];
    }

    /** Gemas encaixadas, na ordem (ids desconhecidos — módulo/gema removida — são ignorados sem apagar). */
    public static List<GemItem> gems(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(KEY)) return List.of();
        ListTag list = data.copyTag().getList(KEY, Tag.TAG_STRING);
        List<GemItem> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item instanceof GemItem gem) out.add(gem);
        }
        return out;
    }

    public static boolean hasGems(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(KEY);
    }

    /** Tenta encaixar {@code gem} em {@code target} (não consome a gema: quem chama faz isso no OK). */
    public static Result trySocket(Player player, ItemStack target, GemItem gem) {
        MagicData md = magic(player);
        if (!canUse(md.path())) return new Result(Code.WRONG_CLASS, 0);
        int max = maxSockets(target);
        if (max == 0) return new Result(Code.NOT_SOCKETABLE, 0);
        int used = gems(target).size();
        if (used >= max) return new Result(Code.FULL, 0);
        if (used >= unlocked(md.level(), target)) return new Result(Code.NEED_LEVEL, levelFor(target, used));
        int gradeLevel = GemType.GRADE_LEVEL[gem.grade() - 1];
        if (md.level() < gradeLevel) return new Result(Code.GRADE_LEVEL, gradeLevel);
        String id = BuiltInRegistries.ITEM.getKey(gem).toString();
        CustomData.update(DataComponents.CUSTOM_DATA, target, tag -> {
            ListTag list = tag.getList(KEY, Tag.TAG_STRING);
            list.add(StringTag.valueOf(id));
            tag.put(KEY, list);
        });
        return new Result(Code.OK, 0);
    }

    /** Tira a última gema do item e a devolve (vazio se não havia). */
    public static ItemStack extractLast(ItemStack target) {
        CustomData data = target.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(KEY)) return ItemStack.EMPTY;
        CompoundTag copy = data.copyTag();
        ListTag list = copy.getList(KEY, Tag.TAG_STRING);
        if (list.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(list.getString(list.size() - 1));
        CustomData.update(DataComponents.CUSTOM_DATA, target, tag -> {
            ListTag l = tag.getList(KEY, Tag.TAG_STRING);
            l.remove(l.size() - 1);
            if (l.isEmpty()) tag.remove(KEY);
            else tag.put(KEY, l);
        });
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Gemas que estão VALENDO para o jogador agora: arma da mão principal + as 4 peças de armadura, só para as classes
     * que encaixam, respeitando os encaixes liberados pelo nível e o nível de cada lapidação.
     */
    public static void forEachActive(Player player, MagicData md, BiConsumer<GemType, Integer> out) {
        if (!canUse(md.path())) return;
        ItemStack hand = player.getMainHandItem();
        if (isWeapon(hand)) collect(hand, md.level(), out);
        for (EquipmentSlot slot : SetBonuses.ARMOR_SLOTS) {
            ItemStack worn = player.getItemBySlot(slot);
            if (isArmor(worn)) collect(worn, md.level(), out);
        }
    }

    private static void collect(ItemStack stack, int level, BiConsumer<GemType, Integer> out) {
        if (!hasGems(stack)) return;
        List<GemItem> gems = gems(stack);
        int usable = Math.min(gems.size(), unlocked(level, stack));
        for (int i = 0; i < usable; i++) {
            GemItem gem = gems.get(i);
            if (level >= GemType.GRADE_LEVEL[gem.grade() - 1]) out.accept(gem.type(), gem.grade());
        }
    }

    private Gems() {}
}
