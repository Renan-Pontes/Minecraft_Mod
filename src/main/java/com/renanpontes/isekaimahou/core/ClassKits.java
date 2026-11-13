package com.renanpontes.isekaimahou.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Kit inicial de cada classe, montado pelos módulos:
 * <ul>
 *   <li>O módulo de roupas registra a armadura ({@link #setArmor}).</li>
 *   <li>Cada módulo de ritual registra os itens do ritual ({@link #addItem}).</li>
 * </ul>
 * Entregue quando o jogador escolhe a classe na tela inicial.
 * v1.0: as 10 classes Físicas/Destreza recebem kit pelos módulos {@code class_<id>} (arma própria + roupa, por id,
 * tolerando ausência). Reserva: {@link #icon} cai na arma do kit ({@link MagicPath#kitWeapon()}) e depois num item
 * vanilla que lembra a classe; {@link #give} garante a arma do kit mesmo que o módulo não a registre.
 */
public final class ClassKits {
    private static final EquipmentSlot[] ORDER = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Map<MagicPath, List<Supplier<ItemStack>>> ITEMS = new EnumMap<>(MagicPath.class);
    private static final Map<MagicPath, Map<EquipmentSlot, Supplier<ItemStack>>> ARMOR = new EnumMap<>(MagicPath.class);

    public static synchronized void addItem(MagicPath path, Supplier<ItemStack> stack) {
        ITEMS.computeIfAbsent(path, p -> new ArrayList<>()).add(stack);
    }

    public static synchronized void setArmor(MagicPath path, EquipmentSlot slot, Supplier<ItemStack> stack) {
        ARMOR.computeIfAbsent(path, p -> new EnumMap<>(EquipmentSlot.class)).put(slot, stack);
    }

    public static List<ItemStack> items(MagicPath path) {
        List<ItemStack> out = new ArrayList<>();
        for (Supplier<ItemStack> s : ITEMS.getOrDefault(path, List.of())) {
            ItemStack st = safeGet(s);
            if (!st.isEmpty()) out.add(st);
        }
        return out;
    }

    /** Armadura na ordem cabeça, peito, pernas, pés (só as registradas). */
    public static List<ItemStack> armor(MagicPath path) {
        List<ItemStack> out = new ArrayList<>();
        Map<EquipmentSlot, Supplier<ItemStack>> m = ARMOR.getOrDefault(path, Map.of());
        for (EquipmentSlot slot : ORDER) {
            Supplier<ItemStack> s = m.get(slot);
            if (s == null) continue;
            ItemStack st = safeGet(s);
            if (!st.isEmpty()) out.add(st);
        }
        return out;
    }

    /**
     * Ícone da classe: o peitoral (se registrado); nas classes Físicas/Destreza, a arma do kit
     * ({@link MagicPath#kitWeapon()}, se o módulo da classe estiver presente); senão um item vanilla padrão.
     */
    public static ItemStack icon(MagicPath path) {
        if (path == null) return new ItemStack(Items.BOOK);
        Supplier<ItemStack> chest = ARMOR.getOrDefault(path, Map.of()).get(EquipmentSlot.CHEST);
        if (chest != null) {
            ItemStack s = safeGet(chest);
            if (!s.isEmpty()) return s;
        }
        ItemStack weapon = weapon(path);
        if (!weapon.isEmpty()) return weapon;
        return new ItemStack(switch (path) {
            case MAGE -> Items.ENCHANTED_BOOK;
            case WITCH -> Items.BREWING_STAND;
            case WARLOCK -> Items.WITHER_ROSE;
            case DRUID -> Items.OAK_SAPLING;
            case NECROMANCER -> Items.SKELETON_SKULL;
            case SMITH -> Items.ANVIL;
            case ROGUE -> Items.BUNDLE;
            case KNIGHT -> Items.DIAMOND_SWORD;
            case ENCHANTER -> Items.ENCHANTING_TABLE;
            case SHINIGAMI -> Items.NETHERITE_SWORD;
            case QUINCY -> Items.SPECTRAL_ARROW;
            case GUNSLINGER -> Items.FIRE_CHARGE;
            case RANGER -> Items.BOW;
            case GAMBLER -> Items.RABBIT_FOOT;
            case STEELBALL -> Items.HEAVY_CORE;
            case HOLLOW -> Items.BONE_BLOCK;
            default -> Items.BOOK;
        });
    }

    /**
     * Arma do kit inicial da classe ({@link MagicPath#kitWeapon()}), ou vazio se a classe não tem (Mágicas) ou o
     * item não existe (módulo {@code class_<id>} ausente).
     */
    public static ItemStack weapon(MagicPath path) {
        ResourceLocation id = path == null ? null : path.kitWeapon();
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Prefixo de uma arma COM TIERS ({@code <prefixo>_t1..t4}) para a classe — usado por quem entrega armas de tier
     * (Guilda, Provações). Ordem: a arma da própria classe ({@link MagicPath#weaponPrefix()}) se o módulo dela registrou
     * {@code _t1}; a arma do Arsenal que a classe herdou da v0.9 (Cavaleiro → {@code knight_blade}, Ranger →
     * {@code longbow}, Ladino → {@code twin_daggers}); senão a genérica da categoria ({@code mage_staff},
     * {@code longsword}, {@code arcane_crossbow}). Pode devolver um prefixo cujo item não existe (Arsenal ausente).
     */
    public static String tieredWeaponPrefix(MagicPath path) {
        MagicPath p = path == null ? MagicPath.NONE : path;
        String own = p.weaponPrefix();
        if (exists(own + "_t1")) return own;
        String inherited = switch (p) {
            case KNIGHT -> "knight_blade";
            case RANGER -> "longbow";
            case ROGUE -> "twin_daggers";
            default -> null;
        };
        if (inherited != null && exists(inherited + "_t1")) return inherited;
        return switch (p.category()) {
            case PHYSICAL -> "longsword";
            case DEXTERITY -> "arcane_crossbow";
            default -> p == MagicPath.NONE ? "mage_staff" : own;
        };
    }

    private static boolean exists(String itemPath) {
        return BuiltInRegistries.ITEM.getOptional(com.renanpontes.isekaimahou.IsekaiMahou.id(itemPath)).isPresent();
    }

    /** Fornecedores de módulos podem falhar (item de outro módulo ausente): nunca derruba a tela/escolha. */
    private static ItemStack safeGet(Supplier<ItemStack> s) {
        try {
            ItemStack st = s.get();
            return st == null ? ItemStack.EMPTY : st;
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    /** Entrega o kit: armadura vestida nos espaços vazios (o resto vai pro inventário). */
    public static void give(ServerPlayer player, MagicPath path) {
        Map<EquipmentSlot, Supplier<ItemStack>> m = ARMOR.getOrDefault(path, Map.of());
        for (EquipmentSlot slot : ORDER) {
            Supplier<ItemStack> s = m.get(slot);
            if (s == null) continue;
            ItemStack stack = safeGet(s);
            if (stack.isEmpty()) continue;
            if (player.getItemBySlot(slot).isEmpty()) player.setItemSlot(slot, stack);
            else giveItem(player, stack);
        }
        // Guarda os ITENS antes de entregar (o inventário esvazia a pilha entregue)
        java.util.Set<Item> registered = new java.util.HashSet<>();
        for (ItemStack st : armor(path)) registered.add(st.getItem());
        List<ItemStack> items = items(path);
        for (ItemStack st : items) registered.add(st.getItem());
        for (ItemStack stack : items) giveItem(player, stack);
        // Reserva: a arma do kit se nenhum módulo a registrou — a classe nunca começa desarmada
        for (ItemStack extra : fallbackGear(path)) {
            if (!registered.contains(extra.getItem())) giveItem(player, extra);
        }
        // Kit de aventureiro iniciante, igual para TODAS as classes
        for (ItemStack extra : starterPack()) giveItem(player, extra);
    }

    /**
     * Kit de aventureiro iniciante comum a todas as classes: comida para os primeiros dias, tochas, ferramentas básicas,
     * uma cama e poções de vida/mana menores do módulo de poções (por id; se o módulo faltar, poção de cura vanilla).
     */
    public static List<ItemStack> starterPack() {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(Items.BREAD, 16));
        out.add(new ItemStack(Items.COOKED_BEEF, 8));
        out.add(new ItemStack(Items.APPLE, 6));
        out.add(new ItemStack(Items.TORCH, 24));
        out.add(new ItemStack(Items.STONE_PICKAXE));
        out.add(new ItemStack(Items.STONE_AXE));
        out.add(new ItemStack(Items.STONE_SHOVEL));
        out.add(new ItemStack(Items.WHITE_BED));
        out.add(new ItemStack(Items.OAK_LOG, 16));
        ItemStack hp = modItem("health_potion_lesser", 3);
        ItemStack mp = modItem("mana_potion_lesser", 3);
        out.add(hp.isEmpty() ? net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,
                net.minecraft.world.item.alchemy.Potions.HEALING) : hp);
        if (!mp.isEmpty()) out.add(mp);
        return out;
    }

    private static ItemStack modItem(String path, int count) {
        Item item = BuiltInRegistries.ITEM.getOptional(com.renanpontes.isekaimahou.IsekaiMahou.id(path)).orElse(Items.AIR);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    /**
     * Equipamento-base da classe que {@link #give} garante mesmo sem registro dos módulos: a arma do kit
     * ({@link #weapon}); se o módulo da classe não registrou o item, uma reserva vanilla (Físicas: espada de ferro;
     * Destreza: arco — a forma Flecha conjura sem flechas). (v1.0: o escudo do Cavaleiro da v0.9 saiu — o Cavaleiro Rúnico luta com
     * a lâmina; o módulo {@code class_knight} registra o que mais quiser com {@link #addItem}.)
     */
    public static List<ItemStack> fallbackGear(MagicPath path) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack weapon = weapon(path);
        if (!weapon.isEmpty()) {
            out.add(weapon);
        } else if (path != null && path.kitWeapon() != null) {
            // Módulo da classe ausente: uma arma vanilla da categoria, para não começar desarmado
            out.add(new ItemStack(path.category() == MagicPath.Category.DEXTERITY ? Items.BOW : Items.IRON_SWORD));
        }
        return out;
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private ClassKits() {}
}
