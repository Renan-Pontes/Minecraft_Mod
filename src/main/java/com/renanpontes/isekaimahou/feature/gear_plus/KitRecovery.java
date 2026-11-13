package com.renanpontes.isekaimahou.feature.gear_plus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.item.SoulBinding;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * <b>Recuperar o kit de classe</b> pelo menu B — palavra do dono: "permite ele pegar outros no B de alguma forma... pra
 * caso perca por N motivos... é o básico né".
 * <p>
 * Devolve o equipamento da classe (traje do kit + arma e ferramentas do kit; comida, flechas e outros consumíveis não
 * voltam) <b>no nível em que o jogador chegou</b> — palavra do dono: "tem que ser do lvl que estiver, se meus itens forem
 * tier alto, serão de tier alto". Cada vaga do kit devolve a MELHOR peça que ele já carregou ({@link KitBest}: traje de
 * grau maior, arma de tier, com encantamentos/reforço); sem nada registrado, a básica. Só voltam as vagas em que ele
 * NÃO carrega uma peça pelo menos do grau registrado. Recarga longa ({@value #COOLDOWN} ticks de jogo); quem tem pressa
 * paga {@value #SKIP_COST} Cristais de Mana (custo simbólico).
 * <p>
 * <b>Sem duplicar:</b> toda peça entregue (no kit inicial e aqui) e toda peça que vira registro leva um selo com o dono
 * e o número da entrega. Cada recuperação aumenta o número do jogador; as peças seladas que ele carrega acompanham e
 * qualquer cópia ANTIGA (esquecida num baú, emprestada, caída onde ele morreu...) se desfaz quando parar no inventário de
 * alguém — de qualquer tier: vale o selo, não o item. As gemas encaixadas na cópia antiga caem de volta antes (a peça
 * recuperada vem sem gemas). Itens sem selo (mundos antigos, peças fabricadas) nunca são apagados. As armas de classe
 * continuam vinculadas pelo {@code core/item/SoulBinding} (a recuperada já sai vinculada).
 */
public final class KitRecovery {
    /** Selo no item (dados personalizados): {@code id} (UUID do dono) e {@code n} (número da entrega). */
    public static final String KEY = "isekai_mahou_kit";
    /** 40 minutos de jogo. */
    public static final int COOLDOWN = 48000;
    public static final int SKIP_COST = 4;

    public enum Result { OK, NO_CLASS, NOTHING_MISSING, COOLDOWN, NO_KIT }

    private static final Map<MagicPath, List<ItemStack>> KITS = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(KitRecovery::onTick);
        // O kit inicial já sai selado (o evento vem depois da entrega do kit)
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.PathChosen e) -> {
            sealStarterKit(e.getPlayer(), MagicApi.get(e.getPlayer()).path());
            GearNet.sync(e.getPlayer());
        });
        // Renasceu sem alguma peça do kit: uma dica (só na primeira vez) de onde recuperar
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp) || e.isEndConquered()) return;
            MagicPath path = MagicApi.get(sp).path();
            if (path != MagicPath.NONE && !missing(sp, path).isEmpty()) {
                Hints.show(sp, "gear_plus.kit", Component.keybind("key.isekai_mahou.menu"));
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            synchronized (KITS) {
                KITS.clear();
            }
            KitBest.clear();
        });
    }

    // =====================================================================================
    //  O que é o kit
    // =====================================================================================

    /**
     * Peças recuperáveis do kit BÁSICO da classe: o traje do kit, os itens não empilháveis do kit (arma, ferramentas,
     * livros) e os blocos de ritual do mod (1 unidade). Consumíveis ficam de fora. Cópias novas a cada chamada.
     * O que a recuperação devolve de verdade para um jogador está em {@link #restorable}.
     */
    public static List<ItemStack> kit(MagicPath path) {
        List<ItemStack> base;
        synchronized (KITS) {
            base = KITS.computeIfAbsent(path, KitRecovery::buildKit);
        }
        List<ItemStack> out = new ArrayList<>(base.size());
        for (ItemStack st : base) out.add(st.copy());
        return out;
    }

    private static List<ItemStack> buildKit(MagicPath path) {
        List<ItemStack> out = new ArrayList<>();
        if (path == null || path == MagicPath.NONE) return out;
        Set<Item> seen = new HashSet<>();
        for (ItemStack st : ClassKits.armor(path)) {
            if (seen.add(st.getItem())) out.add(st.copyWithCount(1));
        }
        List<ItemStack> items = new ArrayList<>(ClassKits.items(path));
        items.addAll(ClassKits.fallbackGear(path));
        for (ItemStack st : items) {
            if (!recoverable(st) || !seen.add(st.getItem())) continue;
            out.add(st.copyWithCount(1));
        }
        return out;
    }

    private static boolean recoverable(ItemStack st) {
        if (st.isEmpty()) return false;
        if (st.getMaxStackSize() == 1) return true;
        // Blocos de ritual do mod (mesa de pesquisa, bigorna arcana...): 1 unidade, só se não estiver carregando
        return st.getItem() instanceof BlockItem && st.getCount() == 1
                && IsekaiMahou.MODID.equals(BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace());
    }

    /**
     * O que a recuperação devolveria a ESTE jogador em cada vaga do kit: a melhor peça que ele já carregou, senão a
     * básica. Cópias novas, sem selo (a aba do menu mostra exatamente estas).
     */
    public static List<ItemStack> restorable(ServerPlayer sp, MagicPath path) {
        return KitBest.restorable(sp, path);
    }

    // =====================================================================================
    //  Selo
    // =====================================================================================

    static int sealNumber(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? -1 : data.copyTag().getCompound(KEY).getInt("n");
    }

    @Nullable
    static UUID sealOwner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(KEY)) return null;
        CompoundTag t = data.copyTag().getCompound(KEY);
        return t.hasUUID("id") ? t.getUUID("id") : null;
    }

    private static void seal(ItemStack stack, UUID owner, int number) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", owner);
        t.putInt("n", number);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(KEY, t));
    }

    /** Sela a peça para o jogador se ela ainda não tem dono no selo (a que acabou de virar registro). */
    static void sealIfFree(ItemStack stack, UUID owner, int number) {
        if (stack.getMaxStackSize() == 1 && sealOwner(stack) == null) seal(stack, owner, number);
    }

    /** O kit inicial já sai selado: sela as peças do kit básico da classe que o jogador carrega e ainda não têm selo. */
    private static void sealStarterKit(ServerPlayer sp, MagicPath path) {
        if (path == null || path == MagicPath.NONE) return;
        Set<Item> mine = new HashSet<>();
        for (ItemStack st : kit(path)) if (st.getMaxStackSize() == 1) mine.add(st.getItem());
        int number = Serials.of(sp.server).get(sp.getUUID());
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && mine.contains(st.getItem()) && sealOwner(st) == null) seal(st, sp.getUUID(), number);
        }
    }

    /**
     * Nova entrega: tudo o que o jogador carrega com o selo DELE (de qualquer tier) acompanha o número novo. Peças sem
     * selo ou de outro dono não são tocadas.
     */
    private static void resealCarried(ServerPlayer sp, int number) {
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && sp.getUUID().equals(sealOwner(st))) seal(st, sp.getUUID(), number);
        }
    }

    /**
     * A cada 2 s: cópias antigas (selo com número menor que o atual do dono) se desfazem — vale o SELO, então a arma de
     * tier e o traje de grau maior entram na mesma regra do kit básico. Depois, o registro da melhor peça de cada vaga.
     */
    private static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || (sp.tickCount + sp.getId()) % 40 != 0) return;
        Inventory inv = sp.getInventory();
        Serials serials = null;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || st.getMaxStackSize() != 1) continue;
            UUID owner = sealOwner(st);
            if (owner == null) continue;
            if (serials == null) serials = Serials.of(sp.server);
            if (sealNumber(st) >= serials.get(owner)) continue;
            Component name = st.getHoverName();
            inv.setItem(i, ItemStack.EMPTY);
            // As gemas não voltam na peça recuperada (o Cinzel as tira inteiras: seria duplicar); as desta, sim
            for (ItemStack gem = Gems.extractLast(st); !gem.isEmpty(); gem = Gems.extractLast(st)) {
                if (!inv.add(gem)) sp.drop(gem, false);
            }
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gear_plus.kit.old_copy", name)
                    .withStyle(ChatFormatting.GRAY), true);
        }
        if (!sp.isAlive() || sp.isSpectator()) return;
        MagicPath path = MagicApi.get(sp).path();
        if (path != null && path != MagicPath.NONE && KitBest.scan(sp, path)) GearNet.sync(sp);
    }

    // =====================================================================================
    //  Recuperação
    // =====================================================================================

    /** Ticks que faltam para a próxima recuperação grátis (0 = pronta). */
    public static long waitTicks(ServerPlayer sp) {
        long last = GearData.kitLast(sp);
        if (last < 0) return 0;
        long now = sp.server.overworld().getGameTime();
        // relógio do mundo voltou (comando /time não mexe no gameTime, mas mundos trocados sim): libera
        if (now < last) return 0;
        return Math.max(0, COOLDOWN - (now - last));
    }

    /**
     * Peças que a recuperação devolveria agora: as vagas do kit em que o jogador não carrega (inventário, armadura e
     * mão secundária) uma peça pelo menos do grau registrado.
     */
    public static List<ItemStack> missing(ServerPlayer sp, MagicPath path) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack want : restorable(sp, path)) {
            if (!carries(sp, path, want)) out.add(want);
        }
        return out;
    }

    /**
     * O jogador carrega a peça {@code want} — ou algo do MESMO grau ou melhor na vaga dela: uma peça do mesmo conjunto
     * no mesmo espaço (traje de grau igual ou maior) ou a arma da classe de tier igual ou maior. Com a peça básica
     * qualquer grau serve; com a melhor registrada, uma peça mais fraca não conta (a vaga volta no nível dele).
     * Serve nos dois lados (a aba do menu usa para marcar o que falta).
     */
    public static boolean carries(Player player, MagicPath path, ItemStack want) {
        Inventory inv = player.getInventory();
        int need = KitBest.rankIn(path, want, want);
        if (need < 0) return false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (KitBest.rankIn(path, want, inv.getItem(i)) >= need) return true;
        }
        return false;
    }

    /**
     * Recupera o kit. {@code pay}: se ainda está na recarga, gasta {@link #SKIP_COST} Cristais de Mana para pular a
     * espera. {@code force}: comando de depuração (ignora recarga e custo).
     */
    public static Result recover(ServerPlayer sp, boolean pay, boolean force) {
        MagicPath path = MagicApi.get(sp).path();
        if (path == MagicPath.NONE) return fail(sp, Result.NO_CLASS, Component.translatable("message.isekai_mahou.gear_plus.kit.no_class"));
        if (restorable(sp, path).isEmpty()) return fail(sp, Result.NO_KIT, Component.translatable("message.isekai_mahou.gear_plus.kit.no_kit"));
        List<ItemStack> missing = missing(sp, path);
        if (missing.isEmpty()) {
            return fail(sp, Result.NOTHING_MISSING, Component.translatable("message.isekai_mahou.gear_plus.kit.nothing"));
        }
        long wait = force ? 0 : waitTicks(sp);
        if (wait > 0) {
            Item crystal = CoreRegistries.MANA_CRYSTAL.get();
            if (!pay || sp.getInventory().countItem(crystal) < SKIP_COST) {
                return fail(sp, Result.COOLDOWN, Component.translatable("message.isekai_mahou.gear_plus.kit.cooldown",
                        GearText.time(wait), SKIP_COST, crystal.getDescription()));
            }
            sp.getInventory().clearOrCountMatchingItems(st -> st.is(crystal), SKIP_COST, sp.inventoryMenu.getCraftSlots());
        }
        // Nova entrega: as peças que ele carrega acompanham o número; as que ficaram para trás viram cópia antiga
        Serials serials = Serials.of(sp.server);
        int number = serials.bump(sp.getUUID());
        resealCarried(sp, number);
        for (ItemStack st : missing) {
            if (st.getMaxStackSize() == 1) seal(st, sp.getUUID(), number);
            // Já sai vinculada: uma arma de tier que caísse no chão (inventário cheio) não pode ficar com outro
            if (SoulBinding.isClassItem(st)) SoulBinding.bind(st, sp);
            EquipmentSlot slot = st.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot() : null;
            if (slot != null && sp.getItemBySlot(slot).isEmpty()) sp.setItemSlot(slot, st);
            else if (!sp.getInventory().add(st)) sp.drop(st, false);
        }
        GearData.setKitLast(sp, sp.server.overworld().getGameTime());
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.9F, 1.0F);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gear_plus.kit.done", missing.size())
                .withStyle(ChatFormatting.GREEN));
        GearNet.sync(sp);
        return Result.OK;
    }

    /** No chat (não na barra de ação): o pedido vem do menu B, que cobre a barra. */
    private static Result fail(ServerPlayer sp, Result r, Component message) {
        sp.displayClientMessage(message.copy().withStyle(ChatFormatting.YELLOW), false);
        return r;
    }

    /** Número da entrega de kit de cada jogador (guardado com o mundo: vale com o dono offline). */
    static final class Serials extends SavedData {
        private final Map<UUID, Integer> numbers = new HashMap<>();

        static Serials of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Serials::new, Serials::load, null), "isekai_mahou_gear_plus_kits");
        }

        static Serials load(CompoundTag tag, HolderLookup.Provider reg) {
            Serials s = new Serials();
            for (String k : tag.getAllKeys()) {
                try {
                    s.numbers.put(UUID.fromString(k), tag.getInt(k));
                } catch (IllegalArgumentException ignored) {
                    // chave inválida: ignora
                }
            }
            return s;
        }

        int get(UUID player) {
            return numbers.getOrDefault(player, 0);
        }

        int bump(UUID player) {
            int n = get(player) + 1;
            numbers.put(player, n);
            setDirty();
            return n;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
            numbers.forEach((k, v) -> tag.putInt(k.toString(), v));
            return tag;
        }
    }

    private KitRecovery() {}
}
