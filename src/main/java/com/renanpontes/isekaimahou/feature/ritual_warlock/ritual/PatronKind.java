package com.renanpontes.isekaimahou.feature.ritual_warlock.ritual;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.ritual_warlock.WarlockContent;
import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.PatronEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Os três Patronos que respondem ao círculo de giz, cada um ligado a um tipo de oferenda. */
public enum PatronKind {
    EMBER_LORD("ember_lord", Element.FIRE, 0xFF6A2B, BossEvent.BossBarColor.RED),
    VOID_LADY("void_lady", Element.SHADOW, 0xB070FF, BossEvent.BossBarColor.PURPLE),
    DROWNED_KING("drowned_king", Element.WATER, 0x3FD0C0, BossEvent.BossBarColor.BLUE);

    /** Oferendas raras: o Patrono fica satisfeito e pede um tributo mais leve. */
    public static final TagKey<Item> GENEROUS = TagKey.create(Registries.ITEM, IsekaiMahou.id("warlock_offering_generous"));

    public final String id;
    public final Element element;
    public final int color;
    public final BossEvent.BossBarColor bossColor;
    public final TagKey<Item> offerings;

    PatronKind(String id, Element element, int color, BossEvent.BossBarColor bossColor) {
        this.id = id;
        this.element = element;
        this.color = color;
        this.bossColor = bossColor;
        this.offerings = TagKey.create(Registries.ITEM, IsekaiMahou.id("warlock_offering_" + id));
    }

    /** Id da entidade ("patron_ember_lord"...). */
    public String entityId() {
        return "patron_" + id;
    }

    public EntityType<? extends PatronEntity> entityType() {
        return switch (this) {
            case EMBER_LORD -> WarlockContent.EMBER_LORD.get();
            case VOID_LADY -> WarlockContent.VOID_LADY.get();
            case DROWNED_KING -> WarlockContent.DROWNED_KING.get();
        };
    }

    public MutableComponent displayName() {
        String key = "entity.isekai_mahou." + entityId();
        return Component.translatable(key).withColor(color);
    }

    public MutableComponent epithet() {
        String key = "patron.isekai_mahou." + id + ".epithet";
        return Component.translatable(key);
    }

    /** Fala do Patrono: "patron.isekai_mahou.<id>.<key>.<n>" (sorteia entre {@code variants} versões). */
    public MutableComponent line(String key, int variants, RandomSource random) {
        int n = variants <= 1 ? 0 : random.nextInt(variants);
        String full = "patron.isekai_mahou." + id + "." + key + "." + n;
        return Component.translatable(full);
    }

    public MutableComponent boonName() {
        String key = "boon.isekai_mahou." + id;
        return Component.translatable(key).withColor(color);
    }

    /** Descrição da dádiva no nível 1..3. */
    public MutableComponent boonDesc(int level) {
        String key = "boon.isekai_mahou." + id + ".desc." + Math.max(1, Math.min(3, level));
        return Component.translatable(key);
    }

    public static PatronKind forOffering(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (PatronKind k : values()) {
            if (stack.is(k.offerings)) return k;
        }
        return null;
    }

    public static PatronKind byId(String id) {
        for (PatronKind k : values()) if (k.id.equals(id)) return k;
        return EMBER_LORD;
    }
}
