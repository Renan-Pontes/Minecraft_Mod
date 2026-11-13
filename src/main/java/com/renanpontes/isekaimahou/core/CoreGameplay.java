package com.renanpontes.isekaimahou.core;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.item.SpellScrollItem;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Regras gerais do núcleo: escolha de classe, biblioteca do Grimório, vida por nível e sincronização.
 * v1.0: vida base 40 ({@link #BASE_HEALTH}), vida por nível dobrada, Poder Mágico dos níveis 100–200
 * ({@link #LEVEL_POWER}) e o rompimento do limite 150→200 ao matar o Dragão Primordial ({@link #onCounter}).
 */
public final class CoreGameplay {
    /** Modificador de vida máxima que cresce com o nível ({@link Progression#bonusHealth}). */
    public static final ResourceLocation GROWTH_HEALTH = IsekaiMahou.id("growth_health");
    /** v1.0: modificador que leva a vida base do jogador de 20 para {@link Progression#BASE_PLAYER_HEALTH}. */
    public static final ResourceLocation BASE_HEALTH = IsekaiMahou.id("base_health");
    /** v1.0: Poder Mágico dos níveis acima do 100 ({@link Progression#highLevelPower}). */
    public static final ResourceLocation LEVEL_POWER = IsekaiMahou.id("level_power");

    /** Magias prontas gravadas no Arcano ao escolher a classe (nomes são texto da receita, como as do jogador). */
    public static final SpellRecipe STARTER_MISSILE = new SpellRecipe(
            List.of(IsekaiMahou.id("projectile"), IsekaiMahou.id("arcane_damage")), "Míssil Arcano", 0xC9A0FF, "");
    public static final SpellRecipe STARTER_SHOCKWAVE = new SpellRecipe(
            List.of(IsekaiMahou.id("touch"), IsekaiMahou.id("push"), IsekaiMahou.id("amplify")), "Onda de Choque", 0x8FD8FF, "");
    /** Físicas: Golpe Arcano (Z), Postura Firme (X) e Contragolpe (C, sai sozinho ao aparar). */
    public static final SpellRecipe STARTER_STRIKE = new SpellRecipe(
            List.of(IsekaiMahou.id("strike"), IsekaiMahou.id("arcane_damage")), "Golpe Arcano", 0xFF8A5C, "");
    public static final SpellRecipe STARTER_STANCE = new SpellRecipe(
            List.of(IsekaiMahou.id("stance"), IsekaiMahou.id("guard")), "Postura Firme", 0xE8C46A, "");
    public static final SpellRecipe STARTER_COUNTER = new SpellRecipe(
            List.of(IsekaiMahou.id("parry"), IsekaiMahou.id("touch"), IsekaiMahou.id("push")), "Contragolpe", 0xBFCADB, "");
    /** Destreza: Flecha Arcana (Z) e Flecha Repulsora (X). */
    public static final SpellRecipe STARTER_ARROW = new SpellRecipe(
            List.of(IsekaiMahou.id("arrow"), IsekaiMahou.id("arcane_damage")), "Flecha Arcana", 0x6FE8C8, "");
    public static final SpellRecipe STARTER_PUSH_ARROW = new SpellRecipe(
            List.of(IsekaiMahou.id("arrow"), IsekaiMahou.id("push")), "Flecha Repulsora", 0x9AD8FF, "");

    /** Magias prontas da categoria, na ordem dos slots Z, X, C (nomes em português). */
    public static List<SpellRecipe> starterSpells(MagicPath.Category category) {
        return switch (category == null ? MagicPath.Category.MAGIC : category) {
            case PHYSICAL -> List.of(STARTER_STRIKE, STARTER_STANCE, STARTER_COUNTER);
            case DEXTERITY -> List.of(STARTER_ARROW, STARTER_PUSH_ARROW);
            default -> List.of(STARTER_MISSILE, STARTER_SHOCKWAVE);
        };
    }

    /**
     * Magias prontas da categoria com os nomes no idioma do jogador ({@code "pt_br"}, {@code "en_us"}...). O nome é
     * texto da receita (o jogador pode renomear); as técnicas das Físicas/Destreza ganham nome em inglês fora do
     * português. As Mágicas continuam como na v0.8.
     */
    public static List<SpellRecipe> starterSpells(MagicPath.Category category, String language) {
        List<SpellRecipe> spells = starterSpells(category);
        if (language == null || language.startsWith("pt")) return spells;
        return spells.stream().map(r -> {
            String en = STARTER_NAMES_EN.get(r.name());
            return en == null ? r : r.withName(en);
        }).toList();
    }

    private static final java.util.Map<String, String> STARTER_NAMES_EN = java.util.Map.of(
            "Golpe Arcano", "Arcane Strike",
            "Postura Firme", "Steadfast Stance",
            "Contragolpe", "Counterstrike",
            "Flecha Arcana", "Arcane Arrow",
            "Flecha Repulsora", "Repelling Arrow");

    /** Escolha na tela inicial: só vale enquanto o jogador está sem classe. Entrega kit e glifos iniciais. */
    public static void choosePath(ServerPlayer player, MagicPath path) {
        MagicData d = MagicApi.get(player);
        if (!path.pickableAtStart() || d.path() != MagicPath.NONE) return;
        d.setPath(path);
        for (String g : CoreGlyphs.starter(path.category())) MagicApi.learnGlyphSilently(player, IsekaiMahou.id(g));
        if (!d.hasFlag("kit_given")) {
            ClassKits.give(player, path);
            d.setFlag("kit_given", true);
        }
        List<SpellRecipe> starterSpells = starterSpells(path.category(), player.clientInformation().language().toLowerCase(java.util.Locale.ROOT));
        boolean starter = giveStarterSpells(d, starterSpells);
        d.fillMana();
        // v0.9: nada de chat — letreiro "Parabéns!" no cliente e a entrada no jogo fica quieta até o tutorial
        // (antes do PathChosen: quem ouve o evento já vê Onboarding.quiet = true)
        com.renanpontes.isekaimahou.core.onboard.OnboardService.onClassChosen(player, path);
        NeoForge.EVENT_BUS.post(new MagicProgressEvent.PathChosen(player));
    }

    /**
     * Grava as magias iniciais da categoria no loadout 1 (Z, X, C...) se os 9 loadouts estiverem TODOS vazios, para o
     * jogador já conseguir conjurar sem abrir o Grimório. Retorna true se gravou.
     */
    private static boolean giveStarterSpells(MagicData d, List<SpellRecipe> spells) {
        for (int l = 0; l < MagicData.LOADOUTS; l++) {
            if (!d.loadoutEmpty(l)) return false;
        }
        // O slot G (ultimate) nunca recebe magia inicial
        for (int i = 0; i < spells.size() && i < Progression.ULTIMATE_SLOT; i++) {
            d.setSlotRecipe(0, i, MoveTrigger.NORMAL, spells.get(i));
        }
        d.setActiveLoadout(0);
        d.markDirty();
        return true;
    }

    // ---------------------------------------------------------------- Vida por nível
    /**
     * Ajusta os modificadores da vida máxima: {@link #BASE_HEALTH} (+{@link Progression#BASE_HEALTH_BONUS}, vida base 40)
     * e {@link #GROWTH_HEALTH} ({@link Progression#bonusHealth}(nível)), e o Poder Mágico dos níveis altos
     * ({@link #LEVEL_POWER}). Ao aumentar a vida, cura a diferença; ao diminuir, só limita a vida atual.
     * Os modificadores são guardados com o jogador (permanentes) para que a vida salva não seja cortada ao entrar no
     * mundo; mesmo assim são recalculados em todo evento relevante, então nunca ficam desatualizados.
     */
    public static void updateGrowthHealth(ServerPlayer player) {
        applyGrowthHealth(player, MagicApi.get(player).level(), true);
    }

    /**
     * Põe os modificadores do nível {@code level}. Com {@code adjustHealth=false} só troca os modificadores (quem chamou
     * cuida da vida — ex.: a saída do End, que restaura a vida antiga do jogador).
     */
    static void applyGrowthHealth(ServerPlayer player, int level, boolean adjustHealth) {
        applyLevelPower(player, level);
        AttributeInstance inst = player.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null) return;
        float oldMax = player.getMaxHealth();
        boolean changed = setModifier(inst, BASE_HEALTH, Progression.BASE_HEALTH_BONUS);
        changed |= setModifier(inst, GROWTH_HEALTH, Progression.bonusHealth(level));
        if (!changed || !adjustHealth || player.isDeadOrDying()) return;
        float newMax = player.getMaxHealth();
        if (newMax > oldMax) player.heal(newMax - oldMax);
        else if (player.getHealth() > newMax) player.setHealth(newMax);
    }

    /** Poder Mágico extra dos níveis acima do 100 (modificador permanente, recalculado junto com a vida). */
    private static void applyLevelPower(ServerPlayer player, int level) {
        AttributeInstance inst = player.getAttribute(CoreRegistries.SPELL_POWER);
        if (inst != null) setModifier(inst, LEVEL_POWER, Progression.highLevelPower(level));
    }

    /** Troca um modificador ADD_VALUE permanente para {@code want} (0 = tira). Retorna true se mudou algo. */
    private static boolean setModifier(AttributeInstance inst, ResourceLocation id, double want) {
        AttributeModifier cur = inst.getModifier(id);
        if (cur != null && Math.abs(cur.amount() - want) < 1.0E-9 && cur.operation() == AttributeModifier.Operation.ADD_VALUE) return false;
        if (cur == null && Math.abs(want) < 1.0E-9) return false;
        inst.removeModifier(id);
        if (Math.abs(want) >= 1.0E-9) inst.addPermanentModifier(new AttributeModifier(id, want, AttributeModifier.Operation.ADD_VALUE));
        return true;
    }

    // ---------------------------------------------------------------- Limite escondido (150 → 200)
    /**
     * {@code MagicProgressEvent.Counter}: na PRIMEIRA vez que o jogador soma {@link Progression#HIDDEN_CAP_COUNTER}
     * (estava na luta quando o Dragão Primordial morreu), o limite de nível sobe para 200 — faixa dourada, toast, som e
     * o XP guardado no banco (acumulado no 150) vira níveis na hora.
     */
    public static void onCounter(MagicProgressEvent.Counter event) {
        // valor ≥ 1 (não "== 1": um comando/módulo pode somar mais de uma vez de uma só) + marcador para nunca repetir
        if (!Progression.HIDDEN_CAP_COUNTER.equals(event.key) || event.value < 1) return;
        ServerPlayer player = event.getPlayer();
        MagicData d = MagicApi.get(player);
        if (d.hasFlag(LIMIT_BROKEN_FLAG)) return;
        d.setFlag(LIMIT_BROKEN_FLAG, true);
        d.capWarned = false;
        d.bankFullWarned = false;
        d.markDirty();
        Rewards.banner(player, Component.translatable("message.isekai_mahou.classes.limit_broken", Progression.HIDDEN_MAX_LEVEL),
                LIMIT_BROKEN_RGB);
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.classes.limit_broken"),
                Component.translatable("toast.isekai_mahou.classes.limit_broken.sub", Progression.HIDDEN_MAX_LEVEL),
                new ItemStack(Items.DRAGON_EGG), LIMIT_BROKEN_RGB);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.7F, 1.4F);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
                60, 0.6, 1.0, 0.6, 0.12);
        if (d.circle() >= Progression.MAX_CIRCLE) {
            int bank = d.takeBankRaw();
            if (bank > 0) {
                player.sendSystemMessage(Component.translatable("message.isekai_mahou.xp_bank_applied", bank)
                        .withStyle(ChatFormatting.AQUA));
                MagicApi.giveXp(player, bank, "bank");
            }
        }
        syncAll(player);
    }

    private static final int LIMIT_BROKEN_RGB = 0xFF8A3A;
    /** Marcador (MagicData.flags) gravado quando o anúncio do limite 200 já foi feito. */
    public static final String LIMIT_BROKEN_FLAG = "limit_broken";

    public static void libraryEdit(ServerPlayer player, int op, int index, SpellRecipe recipe) {
        MagicData d = MagicApi.get(player);
        switch (op) {
            case 0 -> {
                if (recipe.isEmpty()) return;
                if (!d.addToLibrary(recipe)) {
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.library_full").withStyle(ChatFormatting.RED), true);
                }
            }
            case 1 -> d.removeFromLibrary(index);
            case 2 -> {
                if (recipe.isEmpty()) return;
                boolean free = player.getAbilities().instabuild;
                // v1.0: fora do criativo o pergaminho barato (1 papel) acabou; agora é o Pergaminho de Receita (arcana_items), caro
                if (!free) {
                    player.displayClientMessage(Component.translatable("screen.isekai_mahou.grimoire.write_scroll.no_paper").withStyle(ChatFormatting.RED), true);
                    return;
                }
                if (!free) player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.PAPER), 1, player.inventoryMenu.getCraftSlots());
                var stack = SpellScrollItem.of(recipe);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
            default -> {}
        }
    }

    // ---------------------------------------------------------------- Sincronização
    public static void syncAll(ServerPlayer player) {
        MagicData d = MagicApi.get(player);
        MagicData.CODEC.encodeStart(NbtOps.INSTANCE, d).result().ifPresent(tag -> {
            if (tag instanceof CompoundTag ct) PacketDistributor.sendToPlayer(player, new CorePayloads.MagicSync(ct));
        });
        d.dirty = false;
        syncMana(player);
    }

    public static void syncMana(ServerPlayer player) {
        MagicData d = MagicApi.get(player);
        PacketDistributor.sendToPlayer(player, new CorePayloads.ManaSync(d.mana(), d.maxMana(), d.meditating));
        d.manaDirty = false;
    }

    private CoreGameplay() {}
}
