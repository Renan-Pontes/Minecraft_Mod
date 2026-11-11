package com.renanpontes.isekaimahou.feature.class_enchanter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_enchanter.entity.SovereignSanctumEntity;
import com.renanpontes.isekaimahou.feature.class_enchanter.glyph.EnchanterGlyphs;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterArmorItem;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterOutfit;
import com.renanpontes.isekaimahou.feature.class_enchanter.rite.EnchanterRanks;
import com.renanpontes.isekaimahou.feature.class_enchanter.rite.EnchanterRite;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A CLASSE Encantador fora dos glifos: kit, glifos e magias iniciais, catch-up no login, entrega do traje nos Círculos
 * 3/5/8, os bônus de classe e os contadores das tarefas do Rito.
 * <p>Bônus de classe (passivos):
 * <ul>
 *   <li><b>Encantamentos duram bem mais</b> (×1,75, +5% por rank; traje completo e manoplas somam) e o Elo alcança mais
 *   longe (36 blocos) — ver {@link Enchants};</li>
 *   <li><b>Ressonância</b>: teto de acúmulo 4 (5 no rank III) e capacidade 4–6 encantamentos por alvo;</li>
 *   <li><b>Pele Antimágica</b>: −10% de dano mágico (+2% por rank), mesmo sem a Anti-Magia ({@link AntiMagic#innate});</li>
 *   <li><b>Punhos Condutores</b>: com as Manoplas, cada golpe (1× a cada 0,5 s) estende os seus encantamentos em 1 s.</li>
 * </ul>
 */
public final class EnchanterClass {
    private static final Map<UUID, Integer> LOGIN_DELAY = new HashMap<>();
    private static final Map<UUID, Long> CONDUCT = new HashMap<>();
    private static final Map<UUID, Long> FIELD_WARN = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(MagicProgressEvent.PathChosen.class, e -> onPathChosen(e.getPlayer()));
        NeoForge.EVENT_BUS.addListener(MagicProgressEvent.CircleChanged.class, e -> {
            if (e.newCircle > e.oldCircle) grantOutfit(e.getPlayer());
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) LOGIN_DELAY.put(sp.getUUID(), 60);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            UUID id = e.getEntity().getUUID();
            LOGIN_DELAY.remove(id);
            CONDUCT.remove(id);
            FIELD_WARN.remove(id);
        });
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, EnchanterClass::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, EnchanterClass::onDeath);
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, EnchanterClass::onConduct);
        NeoForge.EVENT_BUS.addListener(SpellCastEvent.Pre.class, EnchanterClass::onCastInField);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Expired.class, EnchanterClass::onExpired);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> clearAll());
    }

    // =====================================================================================
    //  Kit
    // =====================================================================================
    static void registerKit() {
        MagicPath p = MagicPath.ENCHANTER;
        ClassKits.addItem(p, () -> new ItemStack(EnchRegistries.GAUNTLETS.get()));
        ClassKits.addItem(p, () -> new ItemStack(EnchRegistries.CODEX.get()));
        ClassKits.addItem(p, () -> new ItemStack(Items.LAPIS_LAZULI, 8));
        ClassKits.addItem(p, () -> new ItemStack(Items.BREAD, 8));
        for (ArmorItem.Type t : EnchRegistries.TYPES) {
            ClassKits.setArmor(p, t.getSlot(), () -> new ItemStack(EnchRegistries.piece(EnchanterOutfit.NOVICE, t).get()));
        }
    }

    // =====================================================================================
    //  Escolha da classe / login
    // =====================================================================================
    private static void onPathChosen(ServerPlayer sp) {
        if (MagicApi.get(sp).path() != MagicPath.ENCHANTER) return;
        init(sp, true);
        grantOutfit(sp);
    }

    /** Glifos iniciais, magias da classe nos slots e as boas-vindas (uma vez). */
    static void init(ServerPlayer sp, boolean welcome) {
        MagicData d = MagicApi.get(sp);
        for (String g : EnchanterRanks.STARTING_GLYPHS) MagicApi.learnGlyphSilently(sp, IsekaiMahou.id(g));
        catchUpGlyphs(sp);
        if (d.hasFlag("enchanter.init")) return;
        d.setFlag("enchanter.init", true);
        placeStarterSpells(sp, d);
        if (welcome) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.enchanter.welcome").withStyle(ChatFormatting.AQUA));
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.enchanter.welcome_hint").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Glifos dos ranks já alcançados (catch-up de saves e de glifos novos). */
    static void catchUpGlyphs(ServerPlayer sp) {
        int rank = EnchanterRanks.current(MagicApi.get(sp));
        for (int r = 1; r <= rank; r++) {
            EnchanterRanks.Rank spec = EnchanterRanks.get(r);
            if (spec == null) continue;
            for (String g : spec.glyphs()) MagicApi.learnGlyphSilently(sp, IsekaiMahou.id(g));
        }
    }

    /** Magias da classe (nome no idioma do jogador). */
    public record ClassSpell(String pt, String en, int color, String chantPt, String chantEn, List<String> glyphs) {
        public SpellRecipe recipe(String lang) {
            boolean ptLang = lang != null && lang.startsWith("pt");
            return new SpellRecipe(glyphs.stream().map(IsekaiMahou::id).toList(), ptLang ? pt : en, color, ptLang ? chantPt : chantEn);
        }
    }

    public static final ClassSpell SELF_ENCHANT = new ClassSpell("Autoencanto", "Self-Enchant", 0x5FE3C0, "Corpo, desperte!",
            "Body, awaken!", List.of("self", EnchanterGlyphs.MIGHT, EnchanterGlyphs.HASTE));
    public static final ClassSpell ANTI = new ClassSpell("Anti-Magia", "Anti-Magic", 0xBFE8FF, "", "",
            List.of("self", EnchanterGlyphs.ANTI_MAGIC));
    public static final ClassSpell BLESSING = new ClassSpell("Bênção de Força", "Blessing of Might", 0xFF8A5C, "", "",
            List.of("touch", EnchanterGlyphs.MIGHT));

    public static List<ClassSpell> starterSpells() {
        return List.of(SELF_ENCHANT, ANTI, BLESSING);
    }

    /**
     * Loadout 1: Z Autoencanto, X Anti-Magia, C o Golpe Arcano do núcleo. Loadout 2: Z Bênção de Força + as outras duas
     * técnicas iniciais do núcleo (Postura e Contragolpe). Só mexe se os loadouts 2–9 estiverem vazios (escolha nova).
     */
    private static void placeStarterSpells(ServerPlayer sp, MagicData d) {
        for (int l = 1; l < MagicData.LOADOUTS; l++) if (!d.loadoutEmpty(l)) return;
        String lang = sp.clientInformation().language().toLowerCase(java.util.Locale.ROOT);
        SpellRecipe[] core = new SpellRecipe[3];
        for (int s = 0; s < 3; s++) {
            SpellSlot slot = d.peekSlot(0, s);
            core[s] = slot == null ? null : slot.main();
        }
        d.setSlotRecipe(0, 0, MoveTrigger.NORMAL, SELF_ENCHANT.recipe(lang));
        d.setSlotRecipe(0, 1, MoveTrigger.NORMAL, ANTI.recipe(lang));
        if (core[0] != null) d.setSlotRecipe(0, 2, MoveTrigger.NORMAL, core[0]);
        else d.setSlotRecipe(0, 2, MoveTrigger.NORMAL, null);
        d.setSlotRecipe(1, 0, MoveTrigger.NORMAL, BLESSING.recipe(lang));
        if (core[1] != null) d.setSlotRecipe(1, 1, MoveTrigger.NORMAL, core[1]);
        if (core[2] != null) d.setSlotRecipe(1, 2, MoveTrigger.NORMAL, core[2]);
        d.setActiveLoadout(0);
        d.markDirty();
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (LOGIN_DELAY.isEmpty() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        Integer left = LOGIN_DELAY.get(sp.getUUID());
        if (left == null) return;
        if (left > 0) {
            LOGIN_DELAY.put(sp.getUUID(), left - 1);
            return;
        }
        if (!sp.isAlive()) return;
        LOGIN_DELAY.remove(sp.getUUID());
        if (MagicApi.get(sp).path() != MagicPath.ENCHANTER) return;
        init(sp, false);
        grantOutfit(sp);
    }

    // =====================================================================================
    //  Traje nos Círculos 3 / 5 / 8
    // =====================================================================================
    static void grantOutfit(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.ENCHANTER || !sp.isAlive()) return;
        EnchanterOutfit best = null;
        for (EnchanterOutfit t : EnchanterOutfit.REWARDS) {
            if (d.circle() < t.circle || d.hasFlag(t.flag())) continue;
            d.setFlag(t.flag(), true);
            best = t;
        }
        if (best != null) deliverOutfit(sp, best);
    }

    public static void deliverOutfit(ServerPlayer sp, EnchanterOutfit tier) {
        int equipped = 0;
        for (ArmorItem.Type type : EnchRegistries.TYPES) {
            ItemStack fresh = new ItemStack(EnchRegistries.piece(tier, type).get());
            EquipmentSlot slot = type.getSlot();
            ItemStack worn = sp.getItemBySlot(slot);
            boolean replace = worn.isEmpty() || (worn.getItem() instanceof EnchanterArmorItem old && old.tier().ordinal() < tier.ordinal()
                    && !worn.isEnchanted());
            if (replace) {
                if (!worn.isEmpty()) Rewards.give(sp, worn.copy());
                sp.setItemSlot(slot, fresh);
                equipped++;
            } else {
                Rewards.give(sp, fresh);
            }
        }
        Component name = tier.displayName().withStyle(tier.chatColor());
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.enchanter_outfit.title", name),
                Component.translatable("toast.isekai_mahou.enchanter_outfit.subtitle", tier.circle),
                new ItemStack(EnchRegistries.piece(tier, ArmorItem.Type.CHESTPLATE).get()), tier.color);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.enchanter.outfit_granted", tier.circle, name,
                tier.totalDefense(), Math.round(tier.setDurationBonus() * 100)).withStyle(ChatFormatting.GOLD));
        if (equipped < EnchRegistries.TYPES.length) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.enchanter.outfit_inventory").withStyle(ChatFormatting.GRAY));
        }
        ServerLevel level = sp.serverLevel();
        EnchFx.spiral(level, sp, tier.color, EnchFx.WHITE);
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1, sp.getZ(), 16, 0.4, 0.6, 0.4, 0.04);
        EnchFx.sound(level, sp.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F + 0.1F * tier.number);
    }

    // =====================================================================================
    //  Contadores e passivas
    // =====================================================================================
    private static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp) || sp == dead || !Enchants.isEnchanter(sp)) return;
        boolean restricted = HeavenlyRestriction.active(sp);
        if (!restricted && !Enchants.hasAny(sp)) return;
        MagicApi.addCounter(sp, Enchants.C_KILLS, 1);
        if (restricted) MagicApi.addCounter(sp, Enchants.C_RESTRICTED, 1);
        if (Enchants.isBoss(dead)) MagicApi.addCounter(sp, Enchants.C_BOSS, 1);
    }

    /** Punhos Condutores: golpes com as manoplas estendem os encantamentos do Encantador. */
    private static void onConduct(LivingIncomingDamageEvent e) {
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp) || src.getDirectEntity() != sp || e.getEntity() == sp) return;
        if (!Enchants.holdsGauntlets(sp) || !Enchants.isEnchanter(sp) || !Enchants.hasAny(sp)) return;
        long now = sp.serverLevel().getGameTime();
        Long last = CONDUCT.get(sp.getUUID());
        if (last != null && now - last < 10) return;
        CONDUCT.put(sp.getUUID(), now);
        Enchants.extendAll(sp, 20, 6000);
        sp.serverLevel().sendParticles(EnchFx.dust(EnchFx.TEAL, 0.7F), e.getEntity().getX(), e.getEntity().getY() + e.getEntity().getBbHeight() * 0.6,
                e.getEntity().getZ(), 5, 0.2, 0.2, 0.2, 0);
    }

    /** Santuário do Soberano: magias de inimigos (exceto chefes) não saem lá dentro. */
    private static void onCastInField(SpellCastEvent.Pre e) {
        LivingEntity c = e.getCaster();
        // chefes são poderosos demais para um campo de encantamento (a contramágica deles é outra história)
        if (Enchants.isBoss(c) || !SovereignSanctumEntity.blocksCaster(c)) return;
        e.setCanceled(true);
        if (c.level() instanceof ServerLevel level) EnchFx.shatter(level, EnchFx.center(c), 1.0);
        if (c instanceof ServerPlayer sp) {
            long now = sp.serverLevel().getGameTime();
            Long last = FIELD_WARN.get(sp.getUUID());
            if (last == null || now - last > 40) {
                FIELD_WARN.put(sp.getUUID(), now);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.field_blocked").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** Sobrecarga acabou: Exaustão (o preço do corpo forçado). */
    private static void onExpired(MobEffectEvent.Expired e) {
        MobEffectInstance i = e.getEffectInstance();
        if (i == null || !i.getEffect().is(EnchRegistries.OVERDRIVE)) return;
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide) return;
        int amp = i.getAmplifier();
        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, Math.min(1, amp / 2)));
        le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
        if (amp >= 2) le.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 0));
        if (le instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.overdrive_end").withStyle(ChatFormatting.GRAY), true);
        }
        if (le.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, le.getX(), le.getY() + 1, le.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            EnchFx.sound(level, le.position(), SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.4F);
        }
    }

    static void clearAll() {
        LOGIN_DELAY.clear();
        CONDUCT.clear();
        FIELD_WARN.clear();
        Enchants.clear();
        AntiMagic.clear();
        HeavenlyRestriction.clear();
        Transformations.clear();
        EnchanterGlyphs.clear();
        EnchanterRite.clear();
        SovereignSanctumEntity.clearAll();
    }

    /** Lista para a validação no setup. */
    static List<SpellRecipe> allStarterRecipes() {
        List<SpellRecipe> out = new ArrayList<>();
        for (ClassSpell c : starterSpells()) out.add(c.recipe("pt_br"));
        return out;
    }

    /** O núcleo ainda dá os iniciais de Físicas (strike/stance/parry/guard). */
    static boolean coreKnows(String id) {
        return CoreGlyphs.starter(MagicPath.Category.PHYSICAL).contains(id);
    }

    private EnchanterClass() {}
}
