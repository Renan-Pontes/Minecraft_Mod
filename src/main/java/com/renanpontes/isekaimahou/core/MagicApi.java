package com.renanpontes.isekaimahou.core;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellPreset;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

/**
 * API do servidor para MUDAR o progresso mágico de um jogador. Módulos usam SEMPRE estes métodos
 * (eles disparam os eventos de {@link MagicProgressEvent} e cuidam das mensagens/sincronização).
 */
public final class MagicApi {

    public static MagicData get(Player player) {
        return player.getData(CoreRegistries.MAGIC);
    }

    // ---------------------------------------------------------------- XP e nível
    /**
     * Dá XP de nível mágico. {@code source} é só informativo (ex.: "cast", "ritual", "quest", "kill").
     * No limite do Círculo o XP NÃO se perde: vai para o banco ({@link MagicData#xpBank()}, até
     * {@link Progression#bankCap}) e vira níveis quando o jogador sobe de Círculo ({@link #setCircle}).
     */
    public static void giveXp(ServerPlayer player, int amount, String source) {
        if (amount <= 0) return;
        MagicData d = get(player);
        int old = d.level();
        int gained = d.addXpRaw(amount);
        if (gained > 0) levelUpFeedback(player, d, old);
        if (d.atLevelCap()) warnCap(player, d);
    }

    public static void setLevel(ServerPlayer player, int level) {
        MagicData d = get(player);
        int old = d.level();
        d.setLevel(level);
        d.capWarned = false;
        d.bankFullWarned = false;
        if (d.level() != old) NeoForge.EVENT_BUS.post(new MagicProgressEvent.LevelUp(player, old, d.level()));
    }

    /**
     * Muda o Círculo (Ritual de Ascensão concluído, comandos). Ao SUBIR, o XP guardado no banco é aplicado
     * logo depois do evento {@link MagicProgressEvent.CircleChanged} (com as mensagens e eventos de LevelUp normais).
     */
    public static void setCircle(ServerPlayer player, int circle) {
        MagicData d = get(player);
        int old = d.circle();
        d.setCircle(circle);
        d.capWarned = false;
        d.bankFullWarned = false;
        boolean up = d.circle() > old;
        if (up) {
            d.fillMana();
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.circle_up", d.circle())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        NeoForge.EVENT_BUS.post(new MagicProgressEvent.CircleChanged(player, old, d.circle()));
        if (up) applyBank(player, d);
    }

    /** Converte o banco de XP em níveis (o que passar do novo limite volta para o banco). */
    private static void applyBank(ServerPlayer player, MagicData d) {
        int bank = d.takeBankRaw();
        if (bank <= 0) return;
        int old = d.level();
        int gained = d.addXpRaw(bank);
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.xp_bank_applied", bank)
                .withStyle(ChatFormatting.AQUA));
        if (gained > 0) levelUpFeedback(player, d, old);
        if (d.atLevelCap()) warnCap(player, d);
    }

    private static void levelUpFeedback(ServerPlayer player, MagicData d, int oldLevel) {
        int oldFloor = ResourceTraining.floor(oldLevel, d.circle());
        d.fillMana();
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.level_up", d.level())
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        // v1.0: marco de nível — o piso do Limite de Mana salta
        if (ResourceTraining.milestone(d.level()) > ResourceTraining.milestone(oldLevel) && d.manaFloor() > oldFloor) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.training.milestone", resourceName(player),
                    ResourceTraining.milestone(d.level()), d.manaLimit(), d.manaCeiling()).withStyle(ChatFormatting.AQUA));
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
        NeoForge.EVENT_BUS.post(new MagicProgressEvent.LevelUp(player, oldLevel, d.level()));
    }

    /** Avisos do limite do Círculo: uma vez ao chegar (limite + "XP guardado"), outra quando o banco enche. */
    private static void warnCap(ServerPlayer player, MagicData d) {
        if (d.circle() >= Progression.MAX_CIRCLE) return; // nível máximo absoluto: não há Ascensão nem banco
        if (!d.capWarned) {
            d.capWarned = true;
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.level_cap", d.circle() + 1)
                    .withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.xp_banked", d.bankCap())
                    .withStyle(ChatFormatting.AQUA));
        }
        if (!d.bankFullWarned && d.bankFull()) {
            d.bankFullWarned = true;
            player.displayClientMessage(Component.translatable("message.isekai_mahou.xp_bank_full", d.bankCap())
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    // ---------------------------------------------------------------- Elementos
    /** Desperta um elemento (prova do santuário, atalho de classe, Convergência...). */
    public static boolean awakenElement(ServerPlayer player, Element e) {
        MagicData d = get(player);
        if (!d.awakenRaw(e)) return false;
        // v1.0: sem Roda — o elemento fica disponível em qualquer loadout (vem dos glifos da magia)
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.element_awakened", e.displayName())
                .withStyle(ChatFormatting.GOLD));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        NeoForge.EVENT_BUS.post(new MagicProgressEvent.ElementAwakened(player, e));
        return true;
    }

    /** XP do elemento (multiplicado pela afinidade da classe). Só conta se o elemento estiver despertado. */
    public static void giveElementXp(ServerPlayer player, Element e, int base) {
        MagicData d = get(player);
        if (!d.isAwakened(e) || base <= 0) return;
        int amount = Math.max(1, Math.round(base * d.path().affinity(e).xpMult));
        int oldLevel = d.elementLevel(e);
        d.addElementXpRaw(e, amount);
        int newLevel = d.elementLevel(e);
        if (newLevel > oldLevel) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.element_level", e.displayName(), newLevel), true);
        }
        NeoForge.EVENT_BUS.post(new MagicProgressEvent.ElementXp(player, e, amount, oldLevel, newLevel));
    }

    // ---------------------------------------------------------------- Glifos
    public static boolean learnGlyph(ServerPlayer player, ResourceLocation id) {
        Glyph g = GlyphRegistry.get(id);
        if (g == null) return false;
        if (!get(player).learnGlyphRaw(id)) return false;
        player.displayClientMessage(Component.translatable("message.isekai_mahou.glyph_learned", g.displayName())
                .withStyle(ChatFormatting.AQUA), true);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
        NeoForge.EVENT_BUS.post(new MagicProgressEvent.GlyphLearned(player, id));
        return true;
    }

    /** Ensina sem mensagem (comandos, kits). */
    public static void learnGlyphSilently(ServerPlayer player, ResourceLocation id) {
        if (GlyphRegistry.get(id) != null && get(player).learnGlyphRaw(id)) {
            NeoForge.EVENT_BUS.post(new MagicProgressEvent.GlyphLearned(player, id));
        }
    }

    // ---------------------------------------------------------------- Feitos (contadores)
    /** Soma num contador de feito/estatística (ex.: "kill.isekai_mahou.goblin", "shrine.fire"). */
    public static int addCounter(ServerPlayer player, String key, int amount) {
        int v = get(player).addCounterRaw(key, amount);
        NeoForge.EVENT_BUS.post(new MagicProgressEvent.Counter(player, key, v));
        return v;
    }

    // ---------------------------------------------------------------- Usos diários (v0.8)
    /** Dia do mundo usado pelos usos diários ({@code getDayTime()/24000}). */
    public static int worldDay(ServerPlayer player) {
        return (int) Math.floorDiv(player.serverLevel().getDayTime(), 24000L);
    }

    /**
     * Quantas vezes {@code key} foi usado HOJE (dia do mundo). Guardado nos contadores {@code daily.<key>} e
     * {@code daily.<key>.day}; num dia novo volta a 0.
     */
    public static int dailyUses(ServerPlayer player, String key) {
        MagicData d = get(player);
        if (d.counter(dailyDayKey(key)) != worldDay(player)) return 0;
        return Math.max(0, d.counter(dailyKey(key)));
    }

    /**
     * Gasta um uso diário de {@code key} se ainda houver (máx. {@code max} por dia do mundo). Criativo: sempre pode e
     * não gasta. Ex.: Magia de Nível Supremo — {@code tryUseDaily(p, "super_tier", 1 + circle / 3)}.
     * @return true se pode usar (e o uso foi contado)
     */
    public static boolean tryUseDaily(ServerPlayer player, String key, int max) {
        if (player.getAbilities().instabuild) return true;
        MagicData d = get(player);
        int day = worldDay(player);
        String usesKey = dailyKey(key), dayKey = dailyDayKey(key);
        if (d.counter(dayKey) != day) {
            // Dia novo: zera (sem eventos de contador — são só a contabilidade do limite)
            d.addCounterRaw(dayKey, day - d.counter(dayKey));
            d.addCounterRaw(usesKey, -d.counter(usesKey));
        }
        if (d.counter(usesKey) >= max) return false;
        d.addCounterRaw(usesKey, 1);
        return true;
    }

    private static String dailyKey(String key) { return "daily." + key; }

    private static String dailyDayKey(String key) { return "daily." + key + ".day"; }

    // ---------------------------------------------------------------- Recurso por categoria (v0.9)
    /**
     * Recurso do jogador (Mana, Vigor ou Foco), pela categoria da classe. Funciona nos dois lados: no cliente lê a
     * cópia sincronizada ({@link ClientMagic#data()}); sem classe = Mana.
     */
    public static MagicPath.Resource resource(Player player) {
        if (player == null) return MagicPath.Resource.MANA;
        MagicData d = player.level().isClientSide ? ClientMagic.data() : get(player);
        return d == null ? MagicPath.Resource.MANA : d.path().resource();
    }

    /** "Mana" / "Vigor" / "Foco", já colorido com {@link #resourceColor}. */
    public static MutableComponent resourceName(Player player) {
        return resource(player).displayName();
    }

    /** Cor ARGB do recurso do jogador (Mana 0xFF7A5CFF, Vigor 0xFFE0563B, Foco 0xFF3BC9A8). */
    public static int resourceColor(Player player) {
        return resource(player).argb();
    }

    /** "Mana insuficiente!" / "Vigor insuficiente!" / "Foco insuficiente!" (sem estilo: quem chama colore). */
    public static MutableComponent noResourceMessage(Player player) {
        MagicPath.Resource r = resource(player);
        if (r == MagicPath.Resource.MANA) return Component.translatable("message.isekai_mahou.no_mana");
        return Component.translatable("message.isekai_mahou.no_resource",
                Component.translatable("resource.isekai_mahou." + r.id));
    }

    // ---------------------------------------------------------------- Loadouts (v1.0)
    /** Troca o loadout ativo (0..8): interrompe a conjuração em andamento. */
    public static void selectLoadout(ServerPlayer player, int loadout) {
        MagicData d = get(player);
        int l = Math.floorMod(loadout, MagicData.LOADOUTS);
        if (l == d.activeLoadout()) return;
        SpellCaster.interrupt(player);
        d.setActiveLoadout(l);
        d.lastCastSlot = -1;
    }

    /**
     * Grava (receita vazia/null = apaga) a versão {@code trigger} de um slot de um loadout. Valida limites e tamanho;
     * a receita pode estar inválida (é rascunho: o erro aparece ao conjurar e no Grimório).
     * @return true se gravou
     */
    public static boolean setSlot(ServerPlayer player, int loadout, int slot, MoveTrigger trigger, @Nullable SpellRecipe recipe) {
        if (loadout < 0 || loadout >= MagicData.LOADOUTS || slot < 0 || slot >= Progression.SLOTS) return false;
        if (recipe != null && recipe.glyphs().size() > Progression.MAX_GLYPHS_HARD) return false;
        MagicData d = get(player);
        if (d.channelSlot == slot && loadout == d.activeLoadout()) SpellCaster.interrupt(player);
        d.setSlotRecipe(loadout, slot, trigger, recipe);
        return true;
    }

    // ---------------------------------------------------------------- Presets (v1.0)
    /** Presets do jogador (funciona nos dois lados: no cliente lê a cópia sincronizada). */
    public static List<SpellPreset> presets(Player player) {
        if (player == null) return List.of();
        MagicData d = player.level().isClientSide ? ClientMagic.data() : get(player);
        return d == null ? List.of() : d.presets();
    }

    /**
     * Salva um preset (mesmo nome = substitui). Avisa o jogador (sucesso, lista cheia, receita vazia).
     * @return índice salvo ou -1
     */
    public static int savePreset(ServerPlayer player, String name, SpellRecipe recipe) {
        MagicData d = get(player);
        if (recipe == null || recipe.isEmpty() || recipe.glyphs().size() > Progression.MAX_GLYPHS_HARD) {
            presetMessage(player, Component.translatable("message.isekai_mahou.preset.empty"), ChatFormatting.RED);
            return -1;
        }
        String n = SpellPreset.cleanName(name);
        if (n.isEmpty()) n = d.uniquePresetName(recipe.name().isBlank() ? "#" + (d.presets().size() + 1) : recipe.name());
        boolean replace = d.presetIndex(n) >= 0;
        int at = d.savePreset(n, recipe);
        if (at < 0) {
            presetMessage(player, Component.translatable("message.isekai_mahou.preset.full", MagicData.MAX_PRESETS), ChatFormatting.RED);
            return -1;
        }
        presetMessage(player, Component.translatable(replace ? "message.isekai_mahou.preset.replaced" : "message.isekai_mahou.preset.saved",
                d.presets().get(at).name(), d.presets().size(), MagicData.MAX_PRESETS), ChatFormatting.AQUA);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.2F);
        return at;
    }

    /** Apaga um preset. */
    public static boolean deletePreset(ServerPlayer player, int index) {
        MagicData d = get(player);
        SpellPreset p = d.preset(index);
        if (p == null || !d.deletePreset(index)) return false;
        presetMessage(player, Component.translatable("message.isekai_mahou.preset.deleted", p.name()), ChatFormatting.GRAY);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 0.6F, 0.9F);
        return true;
    }

    /** Renomeia um preset (o nome não pode repetir outro). */
    public static boolean renamePreset(ServerPlayer player, int index, String name) {
        MagicData d = get(player);
        if (!d.renamePreset(index, name)) {
            presetMessage(player, Component.translatable("message.isekai_mahou.preset.rename_failed"), ChatFormatting.RED);
            return false;
        }
        presetMessage(player, Component.translatable("message.isekai_mahou.preset.renamed", d.presets().get(index).name()), ChatFormatting.AQUA);
        return true;
    }

    /** Carrega um preset num slot de um loadout (versão {@code trigger}). */
    public static boolean loadPreset(ServerPlayer player, int index, int loadout, int slot, MoveTrigger trigger) {
        MagicData d = get(player);
        SpellPreset p = d.preset(index);
        if (p == null || !setSlot(player, loadout, slot, trigger, p.recipe())) return false;
        presetMessage(player, Component.translatable("message.isekai_mahou.preset.loaded", p.name(), loadout + 1,
                SLOT_KEYS[Math.max(0, Math.min(SLOT_KEYS.length - 1, slot))]), ChatFormatting.AQUA);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
        return true;
    }

    /** Letras padrão das teclas dos slots (para mensagens do servidor). */
    private static final String[] SLOT_KEYS = {"Z", "X", "C", "G"};

    private static void presetMessage(ServerPlayer player, MutableComponent msg, ChatFormatting color) {
        player.displayClientMessage(msg.withStyle(color), true);
    }

    // ---------------------------------------------------------------- Interno
    /**
     * Chamado quando uma magia do jogador causa dano: enche a carga de ultimate (v1.0: carga ÚNICA). O dano já vem
     * com o {@code DAMAGE_SCALE}: a carga desconta para encher no mesmo ritmo da v0.8.
     */
    public static void onSpellDamage(ServerPlayer player, Element e, float dealt) {
        if (dealt <= 0) return;
        get(player).addCharge(dealt * 0.6F / Math.max(0.01F, SpellCost.damageScale()));
    }

    private MagicApi() {}
}
