package com.renanpontes.isekaimahou.feature.outfits;

import org.jetbrains.annotations.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Os 15 trajes (5 Mágicas da v0.1 + 10 da v0.9). v1.0: as 10 classes da v0.9 saíram e as classes novas (Ferreiro,
 * Ladino, Cavaleiro Rúnico...) têm o traje no PRÓPRIO módulo ({@code class_<id>}). Os 10 trajes da v0.9 — inclusive o
 * Cavaleiro, o Patrulheiro e o Ladino antigos, que só dividem o id com as classes novas — ficaram GENÉRICOS (sem classe,
 * {@link MagicPath#NONE}, categoria guardada): continuam existindo (saves, reforma, loja, criativo), mas não entram em kit
 * nem são entregues por {@link #byPath}. Só servem de RESERVA ({@link #fallbackFor}) para uma classe cujo módulo não
 * registrou armadura no {@code ClassKits}. Cada traje existe em 4 graus ({@link OutfitTier}): o I é o kit inicial, os
 * outros chegam ao subir de Círculo. O modelo 3D de cada traje/grau fica em
 * {@code geo/item/armor/outfit_<id>[_<grau>].geo.json} (gerado por tools/gen/outfits.py). Proteção e bônus dependem da
 * categoria ({@link OutfitStats}). Novos trajes só no FIM (a ordem é a da aba criativa).
 */
public enum OutfitSet {
    MAGE("mage", MagicPath.MAGE, SoundEvents.ARMOR_EQUIP_LEATHER, true),
    WITCH("witch", MagicPath.WITCH, SoundEvents.ARMOR_EQUIP_LEATHER, true),
    WARLOCK("warlock", MagicPath.WARLOCK, SoundEvents.ARMOR_EQUIP_CHAIN, true),
    DRUID("druid", MagicPath.DRUID, SoundEvents.ARMOR_EQUIP_LEATHER, false),
    NECROMANCER("necromancer", MagicPath.NECROMANCER, SoundEvents.ARMOR_EQUIP_TURTLE, true),
    // ---- Físicos genéricos (v0.9, sem classe): cota de malha, placas, peles, gi e armadura completa
    WARRIOR("warrior", MagicPath.Category.PHYSICAL, SoundEvents.ARMOR_EQUIP_CHAIN, false),
    /** O sol do elmo já brilha no grau I. */
    PALADIN("paladin", MagicPath.Category.PHYSICAL, SoundEvents.ARMOR_EQUIP_IRON, true),
    BARBARIAN("barbarian", MagicPath.Category.PHYSICAL, SoundEvents.ARMOR_EQUIP_LEATHER, false),
    MONK("monk", MagicPath.Category.PHYSICAL, SoundEvents.ARMOR_EQUIP_LEATHER, false),
    /** Armadura do cavaleiro da v0.9 (genérica: o Cavaleiro Rúnico tem a dele no class_knight). */
    KNIGHT("knight", MagicPath.Category.PHYSICAL, SoundEvents.ARMOR_EQUIP_IRON, false),
    // ---- Destreza genéricos (v0.9, sem classe): capuzes, couro, gibão, avental e peles
    /** Traje do patrulheiro da v0.9 (genérico: o Ranger novo tem o dele no class_ranger). */
    RANGER("ranger", MagicPath.Category.DEXTERITY, SoundEvents.ARMOR_EQUIP_LEATHER, false),
    /** Traje do ladino da v0.9 (genérico: o Ladino novo tem o dele no class_rogue). */
    ROGUE("rogue", MagicPath.Category.DEXTERITY, SoundEvents.ARMOR_EQUIP_LEATHER, false),
    BARD("bard", MagicPath.Category.DEXTERITY, SoundEvents.ARMOR_EQUIP_LEATHER, false),
    /** As lentes dos óculos de proteção já brilham no grau I. */
    ARTIFICER("artificer", MagicPath.Category.DEXTERITY, SoundEvents.ARMOR_EQUIP_CHAIN, true),
    HUNTER("hunter", MagicPath.Category.DEXTERITY, SoundEvents.ARMOR_EQUIP_LEATHER, false);

    public final String id;
    /** Classe dona (v1.0: {@link MagicPath#NONE} nos trajes genéricos da v0.9). */
    public final MagicPath path;
    private final MagicPath.Category category;
    public final Holder<SoundEvent> equipSound;
    /** O grau I tem textura _glowmask (runas, estrelas, olhos de alma, sol, lentes...); os graus II–IV sempre têm. */
    public final boolean glows;
    /** Chave da frase de ambientação mostrada na dica da peça do grau I. */
    public final String flavorKey;

    OutfitSet(String id, MagicPath path, Holder<SoundEvent> equipSound, boolean glows) {
        this(id, path, path.category(), equipSound, glows);
    }

    /** Traje genérico (sem classe) da categoria informada. */
    OutfitSet(String id, MagicPath.Category category, Holder<SoundEvent> equipSound, boolean glows) {
        this(id, MagicPath.NONE, category, equipSound, glows);
    }

    OutfitSet(String id, MagicPath path, MagicPath.Category category, Holder<SoundEvent> equipSound, boolean glows) {
        this.id = id;
        this.path = path;
        this.category = category;
        this.equipSound = equipSound;
        this.glows = glows;
        this.flavorKey = "tooltip.isekai_mahou.outfit." + id;
    }

    /** Categoria da classe dona do traje (decide proteção, bônus e o nome dos graus). */
    public MagicPath.Category category() {
        return category;
    }

    /** Traje avulso (v1.0): não pertence a nenhuma classe. */
    public boolean isGeneric() {
        return path == MagicPath.NONE;
    }

    /** "Mago" nos trajes de classe; "Físicas"/"Destreza" nos genéricos (para mensagens e dicas). */
    public MutableComponent ownerName() {
        return isGeneric() ? category.displayName() : path.displayName();
    }

    /**
     * Nome do grau para este traje: as Mágicas usam Aprendiz/Adepto/Mestre/Arquimago; Guerreiras e À Distância têm
     * nomes próprios (os ids dos graus e dos itens são os mesmos para todos).
     */
    public MutableComponent tierName(OutfitTier tier) {
        if (category() == MagicPath.Category.MAGIC) return tier.displayName();
        String key = String.join(".", "outfit.isekai_mahou.tier", tier.id, category().legacyId);
        return Component.translatable(key);
    }

    /** Base do modelo GeckoLib do grau I: geo/item/armor/outfit_x.geo.json + textures/item/armor/outfit_x.png. */
    public ResourceLocation modelId() {
        return modelId(OutfitTier.APPRENTICE);
    }

    /** Modelo do traje no grau dado (o grau I mantém o nome antigo). */
    public ResourceLocation modelId(OutfitTier tier) {
        return IsekaiMahou.id("armor/" + tier.materialId(this));
    }

    /** Todo grau acima do I tem runas/gemas que brilham no escuro. */
    public boolean glows(OutfitTier tier) {
        return tier != OutfitTier.APPRENTICE || glows;
    }

    /** Frase de ambientação da peça (cada grau tem a sua). */
    public String flavorKey(OutfitTier tier) {
        return tier == OutfitTier.APPRENTICE ? flavorKey : flavorKey + "_" + tier.id;
    }

    @Nullable
    public static OutfitSet byId(String id) {
        for (OutfitSet s : values()) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    /** Traje DA classe (só as 5 Mágicas têm traje neste módulo na v1.0), ou null. */
    @Nullable
    public static OutfitSet byPath(MagicPath path) {
        if (path == null || path == MagicPath.NONE) return null;
        for (OutfitSet s : values()) {
            if (s.path == path) return s;
        }
        return null;
    }

    /**
     * Traje genérico que combina com a classe (por id, sem depender do módulo dela), usado só como RESERVA quando o
     * módulo {@code class_<id>} não registrou armadura no kit: Shinigami → gi do Monge, Quincy → capuz do Patrulheiro,
     * Apostador → gibão do Bardo... Sempre da MESMA categoria da classe (os bônus seguem a categoria do traje); classes
     * desconhecidas caem no genérico da categoria. Null para as Mágicas e NONE.
     */
    @Nullable
    public static OutfitSet fallbackFor(MagicPath path) {
        if (path == null || path == MagicPath.NONE || path.category() == MagicPath.Category.MAGIC) return null;
        OutfitSet hint = switch (path.id) {
            case "smith" -> WARRIOR;
            case "rogue", "shinigami" -> MONK;
            case "knight" -> KNIGHT;
            case "enchanter" -> PALADIN;
            case "quincy", "ranger" -> RANGER;
            case "gunslinger" -> HUNTER;
            case "gambler" -> BARD;
            case "steelball" -> ARTIFICER;
            case "hollowfied" -> BARBARIAN;
            default -> null;
        };
        if (hint != null && hint.category() == path.category()) return hint;
        return path.category() == MagicPath.Category.PHYSICAL ? WARRIOR : HUNTER;
    }
}
