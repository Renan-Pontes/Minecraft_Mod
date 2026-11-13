package com.renanpontes.isekaimahou.feature.dwarf_hold.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.dwarf_hold.DwarfRegistry;
import com.renanpontes.isekaimahou.feature.dwarf_hold.rune.Rune;
import com.renanpontes.isekaimahou.feature.dwarf_hold.rune.Runes;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Monóculo de Avaliação (cliente): painel "Avaliação" do alvo olhado (até {@link #RANGE} blocos) e o valor estimado
 * dos itens na dica. Também acrescenta a linha da runa gravada na dica de qualquer item.
 */
public final class AppraisalHud implements LayeredDraw.Layer {
    private static final double RANGE = 24.0;
    private static final String KEY = "hud.isekai_mahou.dwarf_hold.appraisal.";
    private static boolean wearing;
    @Nullable private static LivingEntity target;
    private static int lostTicks;

    // =====================================================================================
    //  Tick: está usando o monóculo? quem está na mira?
    // =====================================================================================
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null) {
            wearing = false;
            target = null;
            return;
        }
        if (p.tickCount % 10 == 0) {
            wearing = CuriosApi.getCuriosInventory(p).map(h -> h.isEquipped(DwarfRegistry.APPRAISAL_MONOCLE.get())).orElse(false);
        }
        if (!wearing) {
            target = null;
            return;
        }
        LivingEntity found = pick(p);
        if (found != null) {
            target = found;
            lostTicks = 0;
        } else if (target != null && (++lostTicks > 20 || !target.isAlive())) {
            target = null; // o painel fica 1 s depois de tirar a mira (fica mais fácil de ler)
        }
    }

    @Nullable
    private static LivingEntity pick(Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1F).scale(RANGE));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box,
                en -> en instanceof LivingEntity le && le.isAlive() && !en.isSpectator() && !en.isInvisible(), eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    // =====================================================================================
    //  Painel
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity t = target;
        if (!wearing || t == null || mc.options.hideGui || mc.screen != null) return;
        Font font = mc.font;
        List<Component> lines = new ArrayList<>();
        float threat = threat(t);
        String rank = rank(threat);
        lines.add(Component.translatable(KEY + "rank", Component.literal(rank).withStyle(s -> s.withColor(rankColor(rank)).withBold(true)))
                .withStyle(ChatFormatting.GRAY));
        if (t instanceof Player pl) {
            lines.add(Component.translatable(KEY + "player").withStyle(ChatFormatting.DARK_AQUA));
            lines.add(Component.translatable(KEY + "level_hidden", pl.experienceLevel).withStyle(ChatFormatting.GRAY));
        }
        double armor = t.getArmorValue();
        double atk = t.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? t.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        lines.add(Component.translatable(KEY + "stats", fmt(armor), atk > 0 ? fmt(atk) : "—").withStyle(ChatFormatting.GRAY));
        weaknessLines(t, lines);
        if (t.getType().fireImmune()) lines.add(Component.translatable(KEY + "fire_immune").withStyle(ChatFormatting.GOLD));

        int w = font.width(t.getDisplayName()) + 12;
        for (Component c : lines) w = Math.max(w, font.width(c) + 12);
        w = Math.max(w, 120);
        int h = 12 + 14 + lines.size() * 10 + 4;
        int x = g.guiWidth() - w - 8;
        int y = g.guiHeight() / 2 - h / 2;
        int gold = 0xFFD9A441;
        g.fill(x, y, x + w, y + h, 0xC0141018);
        g.renderOutline(x, y, w, h, gold);
        g.fill(x + 1, y + 1, x + w - 1, y + 11, 0x60D9A441);
        g.drawString(font, Component.translatable(KEY + "title"), x + 5, y + 2, 0xFFFFE6A0, false);
        int ty = y + 14;
        g.drawString(font, t.getDisplayName(), x + 5, ty, 0xFFFFFFFF, true);
        ty += 11;
        // barra de vida
        float hp = t.getHealth(), max = Math.max(1F, t.getMaxHealth());
        int bw = w - 10;
        g.fill(x + 5, ty, x + 5 + bw, ty + 5, 0xFF3A1010);
        g.fill(x + 5, ty, x + 5 + Math.round(bw * Mth.clamp(hp / max, 0F, 1F)), ty + 5, hp / max > 0.5F ? 0xFF4CD04C : hp / max > 0.25F ? 0xFFE0C040 : 0xFFE04040);
        String hpText = fmt(hp) + " / " + fmt(max);
        ty += 7;
        g.drawString(font, Component.translatable(KEY + "health", hpText), x + 5, ty, 0xFFE0E0E0, false);
        ty += 10;
        for (Component c : lines) {
            g.drawString(font, c, x + 5, ty, 0xFFFFFFFF, false);
            ty += 10;
        }
    }

    private static void weaknessLines(LivingEntity t, List<Component> out) {
        MutableComponent weak = null, resist = null, immune = null;
        for (Element e : Element.BASE) {
            float m = ElementReactions.multiplier(t.getType(), e);
            Component name = e.displayName().withStyle(s -> s.withColor(e.color));
            if (m <= 0.001F) immune = join(immune, name);
            else if (m > 1.01F) weak = join(weak, name);
            else if (m < 0.99F) resist = join(resist, name);
        }
        if (weak != null) out.add(Component.translatable(KEY + "weak", weak).withStyle(ChatFormatting.RED));
        if (resist != null) out.add(Component.translatable(KEY + "resist", resist).withStyle(ChatFormatting.BLUE));
        if (immune != null) out.add(Component.translatable(KEY + "immune", immune).withStyle(ChatFormatting.DARK_PURPLE));
        if (weak == null && resist == null && immune == null) out.add(Component.translatable(KEY + "neutral").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent join(@Nullable MutableComponent acc, Component c) {
        return acc == null ? Component.empty().append(c) : acc.append(Component.literal(", ").withStyle(ChatFormatting.GRAY)).append(c);
    }

    /** Pontuação de perigo: vida, ataque e armadura. */
    private static float threat(LivingEntity t) {
        double atk = t.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? t.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        return (float) (t.getMaxHealth() + atk * 4 + t.getArmorValue() * 2);
    }

    private static String rank(float s) {
        if (s < 20) return "F";
        if (s < 35) return "E";
        if (s < 60) return "D";
        if (s < 100) return "C";
        if (s < 180) return "B";
        if (s < 320) return "A";
        if (s < 650) return "S";
        return "SS";
    }

    private static int rankColor(String r) {
        return switch (r) {
            case "F", "E" -> 0x9A9A9A;
            case "D" -> 0x7CD07C;
            case "C" -> 0x6AB0FF;
            case "B" -> 0xC08CFF;
            case "A" -> 0xFFB040;
            default -> 0xFF4A4A;
        };
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.round(v)) < 0.05 ? Long.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    // =====================================================================================
    //  Dica: runa gravada + valor estimado (com o monóculo)
    // =====================================================================================
    public static void onTooltip(ItemTooltipEvent e) {
        ItemStack stack = e.getItemStack();
        List<Component> tip = e.getToolTip();
        Rune rune = Runes.of(stack);
        if (rune != null && !tip.isEmpty()) {
            Runes.Target t = Runes.target(stack);
            MutableComponent line = Component.translatable("tooltip.isekai_mahou.dwarf_hold.rune", rune.displayName())
                    .withStyle(s -> s.withColor(rune.color));
            tip.add(1, line);
            if (t != null) tip.add(2, Component.literal("  ").append(rune.effectText(t.kind())).withStyle(ChatFormatting.GRAY));
        }
        if (wearing && e.getEntity() != null && !stack.isEmpty()) {
            double v = value(stack);
            tip.add(v < 0.5 ? Component.translatable(KEY + "value.none").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable(KEY + "value", fmt(Math.round(v * 10) / 10.0)).withStyle(ChatFormatting.GREEN));
        }
    }

    /** Valores de referência (em esmeraldas) dos materiais e itens raros. */
    private static final Map<Item, Double> VALUES = Map.ofEntries(
            Map.entry(Items.EMERALD, 1.0), Map.entry(Items.EMERALD_BLOCK, 9.0), Map.entry(Items.DIAMOND, 8.0),
            Map.entry(Items.DIAMOND_BLOCK, 72.0), Map.entry(Items.NETHERITE_INGOT, 64.0), Map.entry(Items.NETHERITE_SCRAP, 15.0),
            Map.entry(Items.ANCIENT_DEBRIS, 15.0), Map.entry(Items.NETHERITE_BLOCK, 576.0), Map.entry(Items.GOLD_INGOT, 0.5),
            Map.entry(Items.GOLD_BLOCK, 4.5), Map.entry(Items.IRON_INGOT, 0.2), Map.entry(Items.IRON_BLOCK, 1.8),
            Map.entry(Items.LAPIS_LAZULI, 0.1), Map.entry(Items.AMETHYST_SHARD, 0.3), Map.entry(Items.NETHER_STAR, 128.0),
            Map.entry(Items.TOTEM_OF_UNDYING, 32.0), Map.entry(Items.ELYTRA, 96.0), Map.entry(Items.ENCHANTED_GOLDEN_APPLE, 64.0),
            Map.entry(Items.GOLDEN_APPLE, 5.0), Map.entry(Items.HEART_OF_THE_SEA, 24.0), Map.entry(Items.ECHO_SHARD, 6.0),
            Map.entry(Items.BEACON, 160.0), Map.entry(Items.DRAGON_EGG, 999.0), Map.entry(Items.TRIDENT, 48.0),
            Map.entry(Items.MACE, 64.0), Map.entry(Items.HEAVY_CORE, 40.0), Map.entry(Items.SHULKER_SHELL, 8.0),
            Map.entry(Items.EXPERIENCE_BOTTLE, 1.0), Map.entry(Items.ENDER_PEARL, 1.0), Map.entry(Items.BLAZE_ROD, 1.0),
            Map.entry(Items.LEATHER, 0.15), Map.entry(Items.STICK, 0.0), Map.entry(Items.STRING, 0.05));

    private static double materialValue(Item item) {
        if (item == DwarfRegistry.MITHRIL_INGOT.get()) return 12.0;
        if (item == DwarfRegistry.RAW_MITHRIL.get()) return 5.0;
        if (item == DwarfRegistry.MITHRIL_NUGGET.get()) return 1.3;
        if (item == DwarfRegistry.MITHRIL_BLOCK_ITEM.get()) return 108.0;
        if (item == DwarfRegistry.HERO_SHIELD.get()) return 600.0;
        if (item == DwarfRegistry.APPRAISAL_MONOCLE.get()) return 80.0;
        Double v = VALUES.get(item);
        if (v != null) return v;
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.startsWith("essence_")) return 6.0;
        if (path.equals("mana_crystal")) return 4.0;
        if (path.startsWith("relic_")) return 150.0;
        return -1;
    }

    /** Estimativa: material × peças, encantamentos, durabilidade, runa e quantidade. */
    private static double value(ItemStack stack) {
        Item item = stack.getItem();
        double v = materialValue(item);
        if (v < 0) {
            Ingredient repair = item instanceof TieredItem ti ? ti.getTier().getRepairIngredient()
                    : item instanceof ArmorItem ai ? ai.getMaterial().value().repairIngredient().get() : null;
            double mat = -1;
            if (repair != null) for (ItemStack r : repair.getItems()) mat = Math.max(mat, materialValue(r.getItem()));
            if (mat > 0) {
                double pieces = item instanceof ArmorItem ai ? switch (ai.getType()) {
                    case HELMET -> 5; case CHESTPLATE -> 8; case LEGGINGS -> 7; case BOOTS -> 4; default -> 6;
                } : item instanceof SwordItem ? 2 : item instanceof DiggerItem ? 3 : 2;
                v = mat * pieces;
            } else {
                v = switch (stack.getRarity()) {
                    case UNCOMMON -> 4;
                    case RARE -> 16;
                    case EPIC -> 48;
                    default -> 0;
                };
            }
        }
        ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        int levels = 0;
        for (var en : ench.entrySet()) levels += en.getIntValue();
        for (var en : stored.entrySet()) levels += en.getIntValue();
        v += levels * 3;
        if (Runes.of(stack) != null) v += 15;
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) v *= 0.4 + 0.6 * (1.0 - stack.getDamageValue() / (double) stack.getMaxDamage());
        return v * stack.getCount();
    }
}
