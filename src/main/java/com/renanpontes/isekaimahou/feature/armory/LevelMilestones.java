package com.renanpontes.isekaimahou.feature.armory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryWeapon;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Marcos de nível (a cada 5 níveis): 5 arma I da classe · 10 Talismã da Vitalidade · 20 arma II · 30 Anel da Guarda ·
 * 45 arma III · 60 Sigilo do Arquimago · 75 arma IV; nos outros múltiplos de 5, um pacote de suprimentos
 * (cristais, essências e 1 pergaminho de um glifo que o jogador ainda não conhece).
 * <p>
 * A arma é procurada por REGISTRO, não por um enum fixo ({@link #weaponFor}): nas 10 classes novas é a arma de tier do
 * PRÓPRIO módulo da classe ({@code <MagicPath.weaponPrefix()>_t1..t4}, ex.: {@code smith_hammer_t2}, {@code ranger_bow_t3});
 * nas 5 Mágicas, o foco do Arsenal ({@link WeaponKind#forPath}). Se o módulo da classe não registrou a arma de tier, vale
 * a arma avulsa da categoria (Espada Longa / Besta Arcana) para ninguém ficar sem marco. As armas e o Escudo do Cavaleiro
 * da v0.9 não são mais entregues a nenhuma classe (v1.0: viraram equipamento avulso).
 * <p>
 * Cada marco grava a flag {@code armory.milestone.<nível>} (entrega uma vez só). Um evento pode pular vários níveis
 * (banco de XP, comandos): nesse caso só a arma de MAIOR tier do intervalo é entregue e os pacotes viram um só.
 * Mundos antigos: no 1º login com o módulo, os marcos já passados são entregues de uma vez.
 * <p>
 * As Provações e as promoções da Guilda também dão esses itens. Se o jogador JÁ TEM a peça (arma da classe de tier
 * igual ou maior; acessório em todos os espaços úteis do Curios), o marco vira um Kit de Melhoria Arcana
 * (molde + material do próximo tier) — ou, para a arma de um módulo de classe (que tem a própria forma de melhorar), um
 * pacote de suprimentos —, para nenhuma recompensa ser uma cópia inútil.
 */
public final class LevelMilestones {
    public static final String FLAG = "armory.milestone.";
    /** Gravada no 1º login com o Arsenal (entrega dos marcos que o jogador já tinha passado). */
    public static final String INIT_FLAG = "armory.milestones_init";
    private static final String MSG = "message." + IsekaiMahou.MODID + ".armory.";
    private static final String TOAST = "toast." + IsekaiMahou.MODID + ".armory.";
    /** Material da melhoria I→II (módulo ascension, por id; sem ele vale a receita reserva com Bloco de Ametista). */
    private static final String CRYSTAL_BLOCK = "mana_crystal_block";
    /** Cristais do kit quando não há material de tier a dar (o Núcleo de Guardião só vem de chefes). */
    private static final int KIT_CRYSTALS = 8;

    /**
     * Arma de marco resolvida para uma classe: o item, a cor do toast e como medir o que o jogador já tem — pelo tipo do
     * Arsenal ({@code kind}) ou pelo prefixo de id da arma do módulo da classe ({@code prefix}).
     */
    public record MilestoneWeapon(Item item, int color, @Nullable WeaponKind kind, @Nullable String prefix) {
        /** Arma de um módulo de classe (não do Arsenal). */
        public boolean fromClassModule() {
            return kind == null;
        }
    }

    /**
     * Arma de tier ({@code 1..4}) que a classe recebe nos marcos, procurada por id: {@code <weaponPrefix>_t<tier>} do
     * módulo da classe (Físicas/Destreza); o foco do Arsenal nas Mágicas; senão a arma avulsa da categoria.
     */
    public static MilestoneWeapon weaponFor(MagicPath path, int tier) {
        if (path != null && path != MagicPath.NONE && path.category() != MagicPath.Category.MAGIC) {
            String prefix = path.weaponPrefix();
            Item item = registered(prefix + "_t" + Math.max(1, Math.min(WeaponKind.MAX_TIER, tier)));
            if (item != null) return new MilestoneWeapon(item, path.rgb(), null, prefix);
        }
        WeaponKind kind = WeaponKind.forPath(path);
        return new MilestoneWeapon(ArmoryItems.weapon(kind, tier), kind.color, kind, null);
    }

    /** Item {@code isekai_mahou:<id>} se algum módulo o registrou, senão null (módulo ausente). */
    @Nullable
    private static Item registered(String id) {
        Item item = BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(id)).orElse(Items.AIR);
        return item == Items.AIR ? null : item;
    }

    /** Marcos de equipamento (nível → recompensa); os outros múltiplos de 5 são pacotes. */
    public static int weaponTier(int level) {
        return switch (level) {
            case 5 -> 1;
            case 20 -> 2;
            case 45 -> 3;
            case 75 -> 4;
            default -> 0;
        };
    }

    /** Acessório do marco (ou null). */
    public static Item accessory(int level) {
        return switch (level) {
            case 10 -> ArmoryItems.VITALITY_CHARM.get();
            case 30 -> ArmoryItems.WARD_RING.get();
            case 60 -> ArmoryItems.ARCHMAGE_SIGIL.get();
            default -> null;
        };
    }

    public static void onLevelUp(MagicProgressEvent.LevelUp event) {
        ServerPlayer sp = event.getPlayer();
        if (sp instanceof FakePlayer) return;
        MagicData d = MagicApi.get(sp);
        if (!d.hasFlag(INIT_FLAG)) d.setFlag(INIT_FLAG, true);
        grant(sp, d, event.oldLevel, event.newLevel, false);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        MagicData d = MagicApi.get(sp);
        if (d.hasFlag(INIT_FLAG)) return;
        d.setFlag(INIT_FLAG, true);
        if (d.level() >= 5) grant(sp, d, 0, d.level(), true);
    }

    /** Entrega os marcos do intervalo (from, to]. */
    private static void grant(ServerPlayer sp, MagicData d, int from, int to, boolean catchUp) {
        if (to <= from) return;
        // Arma: só a de maior tier ainda não entregue no intervalo (as menores ficam marcadas)
        int topWeapon = 0;
        for (int lv = from + 1; lv <= to; lv++) {
            if (lv % 5 == 0 && weaponTier(lv) > 0 && !d.hasFlag(FLAG + lv)) topWeapon = Math.max(topWeapon, weaponTier(lv));
        }
        MagicPath path = classOf(d);
        List<Integer> bundleLevels = new ArrayList<>();
        boolean any = false;
        for (int lv = from + 1; lv <= to; lv++) {
            if (lv % 5 != 0 || d.hasFlag(FLAG + lv)) continue;
            d.setFlag(FLAG + lv, true);
            int tier = weaponTier(lv);
            Item acc = accessory(lv);
            if (tier > 0) {
                if (tier == topWeapon) {
                    MilestoneWeapon weapon = weaponFor(path, tier);
                    if (ownedTier(sp, weapon) < tier) {
                        deliverGear(sp, lv, new ItemStack(weapon.item()), weapon.color());
                        any = true;
                    } else if (weapon.fromClassModule()) {
                        // a arma da classe tem o jeito dela de melhorar (forja, ritual...): a cópia vira suprimentos
                        bundleLevels.add(lv);
                    } else {
                        deliverKit(sp, lv, weapon.item(), tier + 1);
                        any = true;
                    }
                }
            } else if (acc != null) {
                if (ownedCount(sp, acc) >= usefulCopies(acc)) deliverKit(sp, lv, acc, 0);
                else deliverGear(sp, lv, new ItemStack(acc), Rewards.GOLD);
                any = true;
            } else {
                bundleLevels.add(lv);
            }
        }
        if (!bundleLevels.isEmpty()) {
            deliverBundles(sp, d, bundleLevels);
            any = true;
        }
        if (any && catchUp) {
            sp.sendSystemMessage(Component.translatable(MSG + "catch_up").withStyle(ChatFormatting.GOLD));
        }
    }

    private static MagicPath classOf(MagicData d) {
        return d.path() == null ? MagicPath.NONE : d.path();
    }

    // =====================================================================================
    //  O que o jogador já tem (para não entregar cópias inúteis)
    // =====================================================================================
    /** Maior tier da arma de marco que o jogador já carrega (do Arsenal ou do módulo da classe); 0 = nenhuma. */
    private static int ownedTier(ServerPlayer sp, MilestoneWeapon weapon) {
        if (weapon.kind() != null) return ownedWeaponTier(sp, weapon.kind());
        String head = weapon.prefix() + "_t";
        int best = 0;
        for (ItemStack s : carried(sp, s -> true)) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(s.getItem());
            if (!IsekaiMahou.MODID.equals(key.getNamespace()) || !key.getPath().startsWith(head)) continue;
            String rest = key.getPath().substring(head.length());
            if (rest.length() == 1 && Character.isDigit(rest.charAt(0))) best = Math.max(best, rest.charAt(0) - '0');
        }
        return best;
    }

    /** Maior tier da arma {@code kind} que o jogador carrega (inventário, mãos, armadura); 0 = nenhuma. */
    private static int ownedWeaponTier(ServerPlayer sp, WeaponKind kind) {
        int best = 0;
        for (ItemStack s : carried(sp, s -> s.getItem() instanceof ArmoryWeapon)) {
            ArmoryWeapon w = ArmoryWeapon.of(s);
            if (w != null && w.kind() == kind) best = Math.max(best, w.tier());
        }
        return best;
    }

    /** Quantas unidades do acessório o jogador tem (carregadas + equipadas no Curios). */
    private static int ownedCount(ServerPlayer sp, Item item) {
        int n = 0;
        for (ItemStack s : carried(sp, s -> s.is(item))) n += s.getCount();
        n += CuriosApi.getCuriosInventory(sp).map(h -> h.findCurios(item).size()).orElse(0);
        return n;
    }

    /** Quantas cópias do acessório servem para algo (espaços do Curios: 1 talismã, 2 anéis, 1 colar). */
    private static int usefulCopies(Item accessory) {
        return accessory == ArmoryItems.WARD_RING.get() ? 2 : 1;
    }

    private static List<ItemStack> carried(ServerPlayer sp, Predicate<ItemStack> test) {
        List<ItemStack> out = new ArrayList<>();
        Inventory inv = sp.getInventory();
        for (List<ItemStack> list : List.of(inv.items, inv.armor, inv.offhand)) {
            for (ItemStack s : list) if (!s.isEmpty() && test.test(s)) out.add(s);
        }
        return out;
    }

    // =====================================================================================
    //  Entrega
    // =====================================================================================
    private static void deliverGear(ServerPlayer sp, int level, ItemStack stack, int rgb) {
        Rewards.give(sp, stack);
        Rewards.toast(sp, Component.translatable(TOAST + "level_reward", level), stack.getHoverName(), stack, rgb);
        sp.sendSystemMessage(Component.translatable(MSG + "level_reward", level, stack.copyWithCount(1).getDisplayName())
                .withStyle(ChatFormatting.GOLD));
    }

    /**
     * Kit de Melhoria Arcana no lugar de uma peça repetida: Molde Arcano + material do tier {@code nextTier}
     * (II: Bloco de Cristal de Mana · III: Diamante Arcano · outros: cristais de mana).
     */
    private static void deliverKit(ServerPlayer sp, int level, Item alreadyHad, int nextTier) {
        List<ItemStack> kit = new ArrayList<>();
        kit.add(new ItemStack(ArmoryItems.ARCANE_UPGRADE_TEMPLATE.get()));
        switch (nextTier) {
            case 2 -> {
                Item block = BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(CRYSTAL_BLOCK)).orElse(Items.AMETHYST_BLOCK);
                kit.add(new ItemStack(block));
            }
            case 3 -> kit.add(new ItemStack(ArmoryItems.ARCANE_DIAMOND.get()));
            default -> kit.add(new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), KIT_CRYSTALS));
        }
        for (ItemStack s : kit) Rewards.give(sp, s);
        Rewards.toast(sp, Component.translatable(TOAST + "level_reward", level), Component.translatable(TOAST + "kit"),
                kit.get(0), Rewards.GOLD);
        sp.sendSystemMessage(Component.translatable(MSG + "kit", level, new ItemStack(alreadyHad).getDisplayName(), itemList(kit))
                .withStyle(ChatFormatting.GOLD));
    }

    /** Um ou mais pacotes de suprimentos (somados num só quando vários marcos chegam juntos). */
    private static void deliverBundles(ServerPlayer sp, MagicData d, List<Integer> levels) {
        RandomSource rnd = sp.getRandom();
        int crystals = 0;
        int arcane = 0;
        Map<Element, Integer> essences = new EnumMap<>(Element.class);
        List<ItemStack> scrolls = new ArrayList<>();
        List<Glyph> picked = new ArrayList<>();
        for (int lv : levels) {
            crystals += 2 + lv / 15;
            arcane += 1 + lv / 30;
            essences.merge(randomEssenceElement(d, rnd), 2 + lv / 25, Integer::sum);
            Glyph g = pickGlyph(d, lv, rnd, picked);
            if (g != null) {
                picked.add(g);
                scrolls.add(GlyphScrollItem.of(g.id()));
            } else {
                crystals += 3; // conhece tudo: compensa em cristais
            }
        }
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), crystals));
        items.add(new ItemStack(CoreRegistries.ESSENCES.get(Element.ARCANE).get(), arcane));
        essences.forEach((e, n) -> items.add(new ItemStack(CoreRegistries.ESSENCES.get(e).get(), n)));
        items.addAll(scrolls);
        for (ItemStack s : items) {
            // pilhas acima do máximo (pacotes combinados) são divididas para não cair no chão como "pilha gigante"
            int left = s.getCount();
            while (left > 0) {
                int n = Math.min(left, s.getMaxStackSize());
                Rewards.give(sp, s.copyWithCount(n));
                left -= n;
            }
        }

        int first = levels.get(0);
        int last = levels.get(levels.size() - 1);
        Component title = levels.size() == 1 ? Component.translatable(TOAST + "level_reward", first)
                : Component.translatable(TOAST + "level_rewards", first, last);
        Component subtitle;
        ItemStack icon;
        if (scrolls.size() == 1) {
            subtitle = Component.translatable(TOAST + "bundle_scroll", picked.get(0).displayName());
            icon = scrolls.get(0);
        } else if (scrolls.size() > 1) {
            subtitle = Component.translatable(TOAST + "bundle_scrolls", scrolls.size());
            icon = scrolls.get(0);
        } else {
            subtitle = Component.translatable(TOAST + "bundle");
            icon = new ItemStack(CoreRegistries.MANA_CRYSTAL.get());
        }
        Rewards.toast(sp, title, subtitle, icon, Rewards.GOLD);

        // Lista no chat (fica registrada; cada item mostra o tooltip ao passar o mouse)
        Component which = levels.size() == 1 ? Component.literal(String.valueOf(first))
                : Component.literal(first + "–" + last);
        sp.sendSystemMessage(Component.translatable(MSG + "bundle", which, itemList(items)).withStyle(ChatFormatting.GOLD));
    }

    /** "3× Cristal de Mana, Essência Arcana, ..." (cada item com tooltip no chat). */
    private static MutableComponent itemList(List<ItemStack> items) {
        MutableComponent list = Component.empty();
        final int maxShown = 6;
        for (int i = 0; i < items.size(); i++) {
            if (i >= maxShown) {
                list.append(Component.translatable("message.isekai_mahou.armory.and_more", items.size() - maxShown)
                        .withStyle(ChatFormatting.GRAY));
                break;
            }
            ItemStack s = items.get(i);
            if (i > 0) list.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            if (s.getCount() > 1) list.append(Component.literal(s.getCount() + "× ").withStyle(ChatFormatting.WHITE));
            list.append(s.copyWithCount(1).getDisplayName()); // hover com >99 derruba a conexão
        }
        return list;
    }

    /** Elemento base das essências do pacote: um despertado pelo jogador (fusão → um dos pais), senão qualquer. */
    private static Element randomEssenceElement(MagicData d, RandomSource rnd) {
        List<Element> options = new ArrayList<>();
        for (Element e : Element.values()) {
            if (e == Element.ARCANE || !d.isAwakened(e)) continue;
            if (e.isFusion()) {
                options.add(e.parentA);
                options.add(e.parentB);
            } else {
                options.add(e);
            }
        }
        if (options.isEmpty()) options.addAll(Element.BASE);
        return options.get(rnd.nextInt(options.size()));
    }

    /**
     * Glifo que o jogador ainda não conhece, não lendário, de tier até {@code 1 + nível/15} (máx. IV) e da classe dele
     * (sem Magia de Origem de outra classe). Preferência: utilizável já (Arcano ou elemento despertado, Círculo
     * suficiente) → elemento despertado mas Círculo ainda baixo → qualquer elemento. Null se não sobrar nenhum.
     */
    private static Glyph pickGlyph(MagicData d, int level, RandomSource rnd, List<Glyph> exclude) {
        int maxTier = Math.min(4, 1 + level / 15);
        List<Glyph> now = new ArrayList<>();
        List<Glyph> later = new ArrayList<>();
        List<Glyph> any = new ArrayList<>();
        for (Glyph g : GlyphRegistry.all()) {
            if (g.isLegendary() || g.tier() > maxTier || d.knowsGlyph(g.id())) continue;
            if (g.origin() != null && g.origin() != d.path()) continue;
            if (exclude.contains(g)) continue; // sem pergaminhos repetidos num pacote combinado
            any.add(g);
            if (!elementUsable(d, g)) continue;
            if (g.minCircle() <= d.circle()) now.add(g);
            else later.add(g);
        }
        List<Glyph> pool = !now.isEmpty() ? now : !later.isEmpty() ? later : any;
        return pool.isEmpty() ? null : pool.get(rnd.nextInt(pool.size()));
    }

    private static boolean elementUsable(MagicData d, Glyph g) {
        if (g.origin() != null) return true; // Magia de Origem da classe: qualquer elemento
        Element e = g.element();
        return e == null || e == Element.ARCANE || d.isAwakened(e);
    }

    private LevelMilestones() {}
}
