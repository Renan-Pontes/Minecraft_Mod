package com.renanpontes.isekaimahou.feature.gear_plus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.item.SoulBinding;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * <b>A melhor peça de cada vaga do kit</b> — palavra do dono: "o recovery kit tem que ser do lvl que estiver, se meus
 * itens forem tier alto, serão de tier alto".
 * <p>
 * O kit tem VAGAS: cada espaço de armadura, a arma principal da classe e cada ferramenta do kit. A cada 2 s o servidor
 * olha o inventário do jogador e guarda, por classe e por vaga, a MELHOR peça que ele já carregou ({@link GearData},
 * chave {@code kit_best}: sobrevive à morte, ao relog e a reiniciar o servidor). A recuperação devolve essa peça em vez
 * da básica; sem nada guardado (mundos antigos), vale o kit básico de sempre.
 * <p>
 * <b>O que é "melhor" em cada vaga</b> (neste mod o tier é sempre um ITEM diferente, não um número dentro do item):
 * <ul>
 *   <li>armadura: peça do mesmo conjunto no mesmo espaço, de maior grau ({@link SetBonuses#gradeOf});</li>
 *   <li>arma principal: a do kit (0) ou a de tier {@code <prefixo>_t1..t4}. As classes Mágicas não têm arma no kit; a
 *       vaga só aparece para elas depois de carregarem o foco de tier da classe (cajado, varinha...);</li>
 *   <li>ferramenta: o mesmo item do kit.</li>
 * </ul>
 * No empate de grau desempata o que foi investido na peça (encantamentos, qualidade da Lâmina Forjada, runa anã,
 * infusão): o registro nunca piora.
 * <p>
 * <b>O que volta na peça</b> — só uma lista fixa de componentes ({@link #KEEP_IDS}): encantamentos (inclui o Reforço do
 * ferreiro da cidade, que é Inquebrável), nome dado pelo jogador, partes da Lâmina Forjada, runas gravadas do Cavaleiro,
 * runa anã e infusão da Forja Infernal. Nada disso sai do item como objeto trocável. <b>Gemas NÃO voltam</b>: o Cinzel
 * as tira inteiras, então devolvê-las seria duplicar (as da cópia antiga caem de volta quando ela se desfaz). Desgaste,
 * munição, almas e encantamento temporário também ficam de fora.
 * <p>
 * <b>Sem duplicar:</b> a peça que vira registro é selada na hora (dono + número da entrega), como as do kit inicial.
 * Como o registro nunca piora, nenhuma peça selada é melhor do que a que a recuperação devolve — a cópia antiga que se
 * desfaz nunca leva progresso embora. Peças seladas por outro jogador, cópias antigas e armas vinculadas a outro dono
 * não contam.
 */
final class KitBest {
    enum Kind { ARMOR, WEAPON, TOOL }

    /**
     * Uma vaga do kit. {@code basic} é a peça do kit inicial (vazia na vaga da arma das classes Mágicas, que só existe
     * depois de registrada). {@code key} é a chave no NBT do jogador.
     */
    record Slot(String key, Kind kind, ItemStack basic, @Nullable EquipmentSlot armorSlot, @Nullable String family) {}

    /** O que está guardado numa vaga (para o comando de depuração). */
    record Entry(Slot slot, ItemStack stack, boolean recorded, int rank, List<String> kept) {}

    private static final String ID = "id", RANK = "rank", SCORE = "score", KEEP = "keep";
    private static final String ENCHANTMENTS = "minecraft:enchantments";
    private static final String SMITH_BLADE = "isekai_mahou:smith_blade";
    private static final String DWARF_RUNE = "isekai_mahou:dwarf_rune";
    private static final String INFUSION = "isekai_mahou:citadel_infusion";
    /** Componentes que voltam com a peça (por id: o módulo dono pode não existir). Ver o texto da classe. */
    private static final String[] KEEP_IDS = {ENCHANTMENTS, "minecraft:custom_name", SMITH_BLADE,
            "isekai_mahou:knight_runes", DWARF_RUNE, INFUSION};
    /** Encantamento Temporário (módulo origins_a): enquanto durar, os encantamentos do item não são dele de verdade. */
    private static final ResourceLocation TEMP_ENCHANT = IsekaiMahou.id("temp_enchantment");

    private static final Map<MagicPath, List<Slot>> SLOTS = new EnumMap<>(MagicPath.class);
    @Nullable
    private static Map<String, DataComponentType<?>> keepTypes;

    static void clear() {
        synchronized (SLOTS) {
            SLOTS.clear();
        }
    }

    // =====================================================================================
    //  Vagas e graus
    // =====================================================================================

    /** Vagas do kit da classe, na ordem do kit básico. As pilhas {@code basic} são compartilhadas: não altere. */
    static List<Slot> slots(MagicPath path) {
        if (path == null || path == MagicPath.NONE) return List.of();
        synchronized (SLOTS) {
            return SLOTS.computeIfAbsent(path, KitBest::buildSlots);
        }
    }

    private static List<Slot> buildSlots(MagicPath path) {
        List<Slot> out = new ArrayList<>();
        boolean weapon = false;
        for (ItemStack st : KitRecovery.kit(path)) {
            if (!weapon && weaponTier(path, st) >= 0) {
                weapon = true;
                out.add(new Slot("weapon", Kind.WEAPON, st, null, null));
            } else if (st.getItem() instanceof ArmorItem armor) {
                out.add(new Slot("armor_" + armor.getEquipmentSlot().getName(), Kind.ARMOR, st, armor.getEquipmentSlot(),
                        SetBonuses.familyOf(st)));
            } else {
                out.add(new Slot("item:" + BuiltInRegistries.ITEM.getKey(st.getItem()), Kind.TOOL, st, null, null));
            }
        }
        // Classes sem arma no kit (Mágicas): a vaga existe, mas só devolve algo depois de registrada
        String prefix = path.weaponPrefix();
        if (!weapon && prefix != null && !prefix.isEmpty()) {
            out.add(new Slot("weapon", Kind.WEAPON, ItemStack.EMPTY, null, null));
        }
        return List.copyOf(out);
    }

    /**
     * Tier da arma PRINCIPAL da classe: 0 = a do kit, 1..9 = {@code <prefixo>_tN}, -1 = não é a arma da classe. Vale
     * também para as Mágicas (que não têm arma no kit, só as de tier).
     */
    static int weaponTier(MagicPath path, ItemStack stack) {
        if (stack.isEmpty() || path == null || path == MagicPath.NONE) return -1;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!IsekaiMahou.MODID.equals(id.getNamespace())) return -1;
        String p = id.getPath();
        ResourceLocation kit = path.kitWeapon();
        if (kit != null && kit.getPath().equals(p)) return 0;
        String prefix = path.weaponPrefix();
        if (prefix == null || prefix.isEmpty() || p.length() != prefix.length() + 3 || !p.startsWith(prefix + "_t")) return -1;
        char digit = p.charAt(p.length() - 1);
        return Character.isDigit(digit) ? digit - '0' : -1;
    }

    private static int armorRank(Item basic, @Nullable EquipmentSlot slot, @Nullable String family, ItemStack st) {
        if (!(st.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) return -1;
        if (st.is(basic) || (family != null && family.equals(SetBonuses.familyOf(st)))) return SetBonuses.gradeOf(st);
        return -1;
    }

    /** Grau de {@code st} na vaga (-1 = não serve nela). */
    static int rank(MagicPath path, Slot slot, ItemStack st) {
        if (st.isEmpty()) return -1;
        return switch (slot.kind()) {
            case WEAPON -> weaponTier(path, st);
            case ARMOR -> armorRank(slot.basic().getItem(), slot.armorSlot(), slot.family(), st);
            case TOOL -> st.is(slot.basic().getItem()) ? 0 : -1;
        };
    }

    /**
     * Grau de {@code st} na vaga da peça {@code ref} (-1 = não serve nela). Não precisa da lista de vagas: vale nos dois
     * lados (a aba do menu usa com as peças que o servidor mandou).
     */
    static int rankIn(MagicPath path, ItemStack ref, ItemStack st) {
        if (ref.isEmpty() || st.isEmpty()) return -1;
        if (weaponTier(path, ref) >= 0) return weaponTier(path, st);
        if (ref.getItem() instanceof ArmorItem armor) {
            return armorRank(ref.getItem(), armor.getEquipmentSlot(), SetBonuses.familyOf(ref), st);
        }
        return st.is(ref.getItem()) ? 0 : -1;
    }

    // =====================================================================================
    //  Componentes que voltam
    // =====================================================================================

    private static Map<String, DataComponentType<?>> keepTypes() {
        Map<String, DataComponentType<?>> types = keepTypes;
        if (types == null) {
            types = new LinkedHashMap<>();
            for (String id : KEEP_IDS) {
                DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(id));
                if (type != null && type.codec() != null) types.put(id, type);
            }
            keepTypes = types;
        }
        return types;
    }

    /** Os componentes da lista que o item tem DIFERENTES do padrão dele, já em NBT (id do componente → valor). */
    private static CompoundTag keepOf(ItemStack st, HolderLookup.Provider reg) {
        CompoundTag out = new CompoundTag();
        DataComponentType<?> temp = BuiltInRegistries.DATA_COMPONENT_TYPE.get(TEMP_ENCHANT);
        boolean borrowed = temp != null && st.has(temp);
        for (Map.Entry<String, DataComponentType<?>> e : keepTypes().entrySet()) {
            if (borrowed && e.getKey().equals(ENCHANTMENTS)) continue;
            Tag tag = encode(st, e.getValue(), reg);
            if (tag != null) out.put(e.getKey(), tag);
        }
        return out;
    }

    @Nullable
    private static <T> Tag encode(ItemStack st, DataComponentType<T> type, HolderLookup.Provider reg) {
        T value = st.get(type);
        Codec<T> codec = type.codec();
        if (value == null || codec == null || value.equals(st.getItem().components().get(type))) return null;
        return codec.encodeStart(reg.createSerializationContext(NbtOps.INSTANCE), value).result().orElse(null);
    }

    /** Valor que não lê mais (encantamento/receita removidos) é só ignorado: a peça volta sem ele. */
    private static <T> void apply(ItemStack st, DataComponentType<T> type, Tag tag, HolderLookup.Provider reg) {
        Codec<T> codec = type.codec();
        if (codec == null) return;
        codec.parse(reg.createSerializationContext(NbtOps.INSTANCE), tag).result().ifPresent(v -> st.set(type, v));
    }

    /** Desempate entre peças do mesmo grau: quanto foi investido nela. */
    private static int score(ItemStack st, CompoundTag keep) {
        int s = 0;
        if (keep.contains(ENCHANTMENTS)) {
            ItemEnchantments ench = st.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (Object2IntMap.Entry<Holder<Enchantment>> e : ench.entrySet()) s += e.getIntValue();
        }
        s += 1000 * keep.getCompound(SMITH_BLADE).getInt("quality");
        if (keep.contains(DWARF_RUNE)) s += 50;
        if (keep.contains(INFUSION)) s += 50;
        return s;
    }

    // =====================================================================================
    //  Registro
    // =====================================================================================

    /** Registro da vaga, ou null. Um registro cujo item não existe mais (módulo removido) vale como "sem registro". */
    @Nullable
    private static CompoundTag record(CompoundTag all, Slot slot) {
        if (!all.contains(slot.key(), Tag.TAG_COMPOUND)) return null;
        CompoundTag rec = all.getCompound(slot.key());
        ResourceLocation id = ResourceLocation.tryParse(rec.getString(ID));
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? rec : null;
    }

    /** A peça conta para ESTE jogador? (dele de verdade: não é de outro dono nem cópia antiga) */
    private static boolean eligible(ServerPlayer sp, ItemStack st, int serial) {
        if (st.getMaxStackSize() != 1) return false;
        UUID owner = KitRecovery.sealOwner(st);
        if (owner != null && (!owner.equals(sp.getUUID()) || KitRecovery.sealNumber(st) < serial)) return false;
        return !SoulBinding.isBoundToOther(st, sp.getUUID());
    }

    /**
     * Olha o inventário e atualiza o registro das vagas da classe. Devolve true se algo mudou (quem chama sincroniza).
     * Barato: só as peças que servem numa vaga E não são piores que o registro chegam a ser comparadas.
     */
    static boolean scan(ServerPlayer sp, MagicPath path) {
        List<Slot> slots = slots(path);
        if (slots.isEmpty()) return false;
        Inventory inv = sp.getInventory();
        HolderLookup.Provider reg = sp.registryAccess();
        CompoundTag all = GearData.kitBest(sp, path);
        int serial = -1;
        boolean changed = false;
        for (Slot slot : slots) {
            CompoundTag rec = record(all, slot);
            ItemStack basic = slot.basic();
            // Sem registro, a régua é a peça básica: ela mesma (sem nada investido) não precisa ser guardada
            int recRank = rec != null ? rec.getInt(RANK) : basic.isEmpty() ? 0 : Math.max(0, rank(path, slot, basic));
            int recScore = rec != null ? rec.getInt(SCORE) : 0;
            String recId = rec != null ? rec.getString(ID) : basic.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(basic.getItem()).toString();
            CompoundTag recKeep = rec != null ? rec.getCompound(KEEP) : basic.isEmpty() ? new CompoundTag() : keepOf(basic, reg);

            ItemStack best = null;
            CompoundTag bestKeep = null;
            int bestRank = -1, bestScore = -1;
            boolean sameAsRecord = false;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty()) continue;
                int r = rank(path, slot, st);
                if (r < recRank) continue;
                if (serial < 0) serial = KitRecovery.Serials.of(sp.server).get(sp.getUUID());
                if (!eligible(sp, st, serial)) continue;
                CompoundTag keep = keepOf(st, reg);
                int sc = score(st, keep);
                if (r == recRank) {
                    if (sc < recScore) continue;
                    if (sc == recScore && keep.equals(recKeep)
                            && BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(recId)) sameAsRecord = true;
                }
                if (r > bestRank || (r == bestRank && sc > bestScore)) {
                    best = st;
                    bestKeep = keep;
                    bestRank = r;
                    bestScore = sc;
                }
            }
            // Empate com o registro e a peça registrada continua aqui: nada novo (e duas iguais não ficam se revezando)
            if (best == null || (bestRank == recRank && bestScore == recScore && sameAsRecord)) continue;
            CompoundTag out = new CompoundTag();
            out.putString(ID, BuiltInRegistries.ITEM.getKey(best.getItem()).toString());
            out.putInt(RANK, bestRank);
            out.putInt(SCORE, bestScore);
            out.put(KEEP, bestKeep);
            GearData.setKitBest(sp, path, slot.key(), out);
            // A peça registrada entra no selo: se ela ficar para trás e o jogador recuperar outra, esta se desfaz
            KitRecovery.sealIfFree(best, sp.getUUID(), serial);
            changed = true;
        }
        return changed;
    }

    /** Remonta a peça de uma vaga: a registrada (se ainda existe e ainda serve na vaga) ou a básica. Cópia nova. */
    private static ItemStack rebuild(MagicPath path, Slot slot, @Nullable CompoundTag rec, HolderLookup.Provider reg) {
        ItemStack basic = slot.basic().copy();
        if (rec == null) return basic;
        ResourceLocation id = ResourceLocation.tryParse(rec.getString(ID));
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        if (item == Items.AIR) return basic;
        // Ferramenta do kit: parte da pilha básica (ela já traz o componente padrão, ex.: a Lâmina Forjada inicial)
        ItemStack made = basic.is(item) ? basic : new ItemStack(item);
        if (made.getMaxStackSize() != 1 || rank(path, slot, made) < 0) return slot.basic().copy();
        CompoundTag keep = rec.getCompound(KEEP);
        for (Map.Entry<String, DataComponentType<?>> e : keepTypes().entrySet()) {
            Tag tag = keep.get(e.getKey());
            if (tag != null) apply(made, e.getValue(), tag, reg);
        }
        return made;
    }

    /** O que a recuperação devolveria em cada vaga (a melhor registrada, senão a básica). Cópias novas, sem selo. */
    static List<ItemStack> restorable(ServerPlayer sp, MagicPath path) {
        List<ItemStack> out = new ArrayList<>();
        CompoundTag all = GearData.kitBest(sp, path);
        for (Slot slot : slots(path)) {
            ItemStack st = rebuild(path, slot, record(all, slot), sp.registryAccess());
            if (!st.isEmpty()) out.add(st);
        }
        return out;
    }

    /** Vaga a vaga, para o comando de depuração (vagas vazias — arma das Mágicas sem registro — também aparecem). */
    static List<Entry> entries(ServerPlayer sp, MagicPath path) {
        List<Entry> out = new ArrayList<>();
        CompoundTag all = GearData.kitBest(sp, path);
        for (Slot slot : slots(path)) {
            CompoundTag rec = record(all, slot);
            ItemStack st = rebuild(path, slot, rec, sp.registryAccess());
            List<String> kept = rec == null ? List.of() : List.copyOf(rec.getCompound(KEEP).getAllKeys());
            out.add(new Entry(slot, st, rec != null, st.isEmpty() ? 0 : Math.max(0, rank(path, slot, st)), kept));
        }
        return out;
    }

    private KitBest() {}
}
