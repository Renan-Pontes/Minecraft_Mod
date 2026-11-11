package com.renanpontes.isekaimahou.feature.class_rogue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreGameplay;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.class_rogue.entity.RogueKnifeEntity;
import com.renanpontes.isekaimahou.feature.class_rogue.item.RogueArmorItem;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Ouvintes do Ladino (servidor):
 * <ul>
 *   <li><b>Bônus de classe</b> (só Ladinos): crítico (15% +2%/rank +traje, ×1,5) e dano DOBRADO pelas costas nos golpes
 *   corpo a corpo e facas (×1,5 em jogadores, ×1,6 em chefes); Ponto Vital vira crítico certo ×2; magias do Ladino
 *   também podem critar (metade da chance); "Passo Leve": +50% de velocidade agachado; matar alvo marcado devolve 10%
 *   do recurso.</li>
 *   <li><b>Entrada na classe</b>: glifos iniciais, magias prontas no loadout 1 (Apunhalar, Passo Sombrio, Mão Leve) e o
 *   Véu de Fumaça + técnicas do núcleo no loadout 2.</li>
 *   <li><b>Traje das Sombras</b>: graus II/III/IV nos Círculos 3/5/8 (com as mesmas flags do outfits, em prioridade
 *   alta, para o traje antigo não vir junto).</li>
 * </ul>
 */
public final class RogueEvents {
    public static final ResourceLocation LIGHT_STEP = IsekaiMahou.id("rogue_light_step");
    private static final String INIT_FLAG = "rogue.init";
    private static final Map<UUID, Integer> LOGIN_DELAY = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RogueEvents::onPathChosen);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RogueEvents::onCircleChanged);
        NeoForge.EVENT_BUS.addListener(RogueEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(RogueEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(RogueEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(RogueEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RogueEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RogueEvents::onIncoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RogueEvents::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(RogueEvents::onDeath);
        RogueTechniques.register();
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            RogueTechniques.clear();
            RogueCombat.clear();
            RogueSteal.clear();
            LOGIN_DELAY.clear();
        });
    }

    // =====================================================================================
    //  Entrada na classe
    // =====================================================================================
    private static void onPathChosen(MagicProgressEvent.PathChosen e) {
        ServerPlayer sp = e.getPlayer();
        if (MagicApi.get(sp).path() != MagicPath.ROGUE) return;
        init(sp, true);
        grantGear(sp);
        RogueNet.sync(sp);
    }

    /** Glifos iniciais e magias prontas (uma vez por jogador; também para saves que escolheram antes do módulo). */
    static void init(ServerPlayer sp, boolean fresh) {
        MagicData d = MagicApi.get(sp);
        if (d.hasFlag(INIT_FLAG)) return;
        d.setFlag(INIT_FLAG, true);
        for (ResourceLocation g : RogueGlyphs.STARTER) MagicApi.learnGlyphSilently(sp, g);
        // glifos de ranks já alcançados (comando/save antigo)
        for (int r = 1; r <= RogueRanks.rank(d); r++) for (ResourceLocation g : RogueRanks.glyphsFor(r)) MagicApi.learnGlyphSilently(sp, g);
        String lang = sp.clientInformation().language().toLowerCase(java.util.Locale.ROOT);
        boolean coreStarters = slotsAre(d, 0, CoreGameplay.starterSpells(MagicPath.Category.PHYSICAL));
        if (coreStarters || d.loadoutEmpty(0)) {
            List<SpellRecipe> mine = RogueGlyphs.starters(lang);
            for (int i = 0; i < mine.size(); i++) d.setSlotRecipe(0, i, MoveTrigger.NORMAL, mine.get(i));
            if (d.loadoutEmpty(1)) {
                SpellRecipe veil = lang.startsWith("pt") ? RogueGlyphs.START_VEIL : RogueGlyphs.english(RogueGlyphs.START_VEIL);
                d.setSlotRecipe(1, 0, MoveTrigger.NORMAL, veil);
                List<SpellRecipe> core = CoreGameplay.starterSpells(MagicPath.Category.PHYSICAL, lang);
                if (core.size() > 0) d.setSlotRecipe(1, 1, MoveTrigger.NORMAL, core.get(0));
                if (core.size() > 2) d.setSlotRecipe(1, 2, MoveTrigger.NORMAL, core.get(2));
            }
            d.markDirty();
        }
        if (!fresh) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.class_rogue.welcome").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    /** O loadout {@code l} tem exatamente estas magias nos primeiros slots (as prontas do núcleo)? */
    private static boolean slotsAre(MagicData d, int l, List<SpellRecipe> spells) {
        for (int i = 0; i < spells.size() && i < 3; i++) {
            SpellSlot s = d.peekSlot(l, i);
            SpellRecipe r = s.main();
            if (r == null || !r.glyphs().equals(spells.get(i).glyphs())) return false;
        }
        return !spells.isEmpty();
    }

    // =====================================================================================
    //  Traje das Sombras (graus II–IV)
    // =====================================================================================
    private static void onCircleChanged(MagicProgressEvent.CircleChanged e) {
        if (e.newCircle > e.oldCircle && MagicApi.get(e.getPlayer()).path() == MagicPath.ROGUE) grantGear(e.getPlayer());
    }

    /**
     * Entrega o maior grau alcançado e ainda não recebido (marca os menores). As flags são gravadas NA HORA (mesmo
     * morto ou entrando no mundo), para a entrega do outfits — que usa as mesmas flags — nunca mandar o traje antigo;
     * se o jogador não pode receber agora, a entrega fica pendente para depois do login/renascer.
     */
    static void grantGear(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.ROGUE) return;
        RogueGear best = null;
        for (RogueGear g : RogueGear.REWARDS) {
            if (d.circle() < g.circle || d.hasFlag(g.flag())) continue;
            d.setFlag(g.flag(), true);
            best = g;
        }
        if (best == null) return;
        if (sp.isAlive() && !LOGIN_DELAY.containsKey(sp.getUUID())) deliver(sp, best);
        else d.setFlag(PENDING + best.id, true);
    }

    private static final String PENDING = "rogue.gear_pending.";

    private static void deliver(ServerPlayer sp, RogueGear gear) {
        int worn = 0;
        for (ArmorItem.Type type : RogueGear.TYPES) {
            ItemStack fresh = new ItemStack(RogueContent.piece(gear, type).get());
            EquipmentSlot slot = type.getSlot();
            ItemStack cur = sp.getItemBySlot(slot);
            boolean replace = cur.isEmpty() || (cur.getItem() instanceof RogueArmorItem old && old.gear().ordinal() < gear.ordinal()
                    && !cur.isEnchanted());
            if (replace) {
                if (!cur.isEmpty()) Rewards.give(sp, cur.copy());
                sp.setItemSlot(slot, fresh);
                worn++;
            } else {
                Rewards.give(sp, fresh);
            }
        }
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.class_rogue.gear", gear.displayName()),
                Component.translatable("toast.isekai_mahou.class_rogue.gear.sub", gear.circle),
                new ItemStack(RogueContent.piece(gear, ArmorItem.Type.CHESTPLATE).get()), gear.color);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.class_rogue.gear", gear.circle,
                gear.displayName().withStyle(gear.chatColor())).withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(Component.translatable(worn == RogueGear.TYPES.length ? "message.isekai_mahou.class_rogue.gear.worn"
                : "message.isekai_mahou.class_rogue.gear.inventory").withStyle(ChatFormatting.GRAY));
        ServerLevel level = sp.serverLevel();
        RogueFx.vanish(level, sp.position());
        level.sendParticles(RogueFx.dust(gear.color, 1.3F), sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.4, 0.8, 0.4, 0);
        RogueFx.sound(level, sp.position(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.9F);
        RogueFx.sound(level, sp.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.2F);
    }

    // =====================================================================================
    //  Login / respawn / logout
    // =====================================================================================
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || MagicApi.get(sp).path() != MagicPath.ROGUE) return;
        // marca as flags JÁ (antes da entrega do outfits, que espera 60 ticks); a entrega com toast sai em 40 ticks
        LOGIN_DELAY.put(sp.getUUID(), 40);
        grantGear(sp);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && !e.isEndConquered() && MagicApi.get(sp).path() == MagicPath.ROGUE) {
            LOGIN_DELAY.put(sp.getUUID(), 20);
            grantGear(sp);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        LOGIN_DELAY.remove(id);
        RogueCombat.forget(id);
        RogueSteal.forget(id);
        RogueTechniques.forget(id);
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (!LOGIN_DELAY.isEmpty()) {
            Integer left = LOGIN_DELAY.get(sp.getUUID());
            if (left != null) {
                if (left > 0) LOGIN_DELAY.put(sp.getUUID(), left - 1);
                else if (sp.isAlive()) {
                    LOGIN_DELAY.remove(sp.getUUID());
                    afterLogin(sp);
                }
            }
        }
        if (sp.tickCount % 40 == 0) syncLightStep(sp);
    }

    private static void afterLogin(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.ROGUE) return;
        init(sp, false);
        RogueTechniques.catchUp(sp);
        // só o maior grau pendente vem (os menores já ficaram para trás)
        RogueGear best = null;
        for (RogueGear g : RogueGear.REWARDS) {
            if (d.hasFlag(PENDING + g.id)) {
                d.setFlag(PENDING + g.id, false);
                best = g;
            }
        }
        if (best != null) deliver(sp, best);
        grantGear(sp);
        RogueNet.sync(sp);
    }

    /** "Passo Leve": +50% de velocidade agachado para Ladinos (tira de quem deixou de ser). */
    private static void syncLightStep(ServerPlayer sp) {
        AttributeInstance inst = sp.getAttribute(Attributes.SNEAKING_SPEED);
        if (inst == null) return;
        boolean rogue = MagicApi.get(sp).path() == MagicPath.ROGUE;
        boolean has = inst.getModifier(LIGHT_STEP) != null;
        if (rogue && !has) {
            inst.addPermanentModifier(new AttributeModifier(LIGHT_STEP, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!rogue && has) {
            inst.removeModifier(LIGHT_STEP);
        }
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        RogueCombat.tick(e.getServer());
        RogueTechniques.tick(e.getServer());
    }

    // =====================================================================================
    //  Passivas de combate
    // =====================================================================================

    /** Golpes corpo a corpo e facas de arremesso do Ladino: costas ×2 e crítico. */
    private static void onIncoming(LivingIncomingDamageEvent e) {
        DamageSource src = e.getSource();
        if (src.is(CoreRegistries.SPELL_DAMAGE)) return;
        if (!(src.getEntity() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) return;
        Entity direct = src.getDirectEntity();
        boolean melee = direct == sp;
        boolean knife = direct instanceof RogueKnifeEntity k && k.mode() == RogueKnifeEntity.MODE_KNIFE;
        if (!melee && !knife) return;
        LivingEntity target = e.getEntity();
        if (target == sp || MagicApi.get(sp).path() != MagicPath.ROGUE) return;
        float amount = e.getAmount();
        if (RogueCombat.exposedBack(target, sp)) {
            amount *= RogueCombat.backMultiplier(target, RogueCombat.PASSIVE_BACK);
            RogueCombat.consumeAmbush(sp);
            RogueFx.slash(level, target, RogueFx.SHADOW);
            if (melee) MagicApi.addCounter(sp, "rogue.backstab", 1);
        }
        RogueCombat.Mark mark = RogueCombat.markOf(target, sp);
        if (mark != null) {
            amount *= RogueCombat.VITAL_MULT;
            RogueCombat.consumeMark(target);
            RogueFx.crit(level, target, true);
            RogueFx.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
            MagicApi.addCounter(sp, "rogue.vital", 1);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.vital.hit").withColor(RogueFx.BLOOD), true);
        } else if (sp.getRandom().nextFloat() < RogueCombat.critChance(sp)) {
            amount *= RogueCombat.CRIT_MULT;
            RogueFx.crit(level, target, false);
            RogueFx.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.8F, 1.1F);
            MagicApi.addCounter(sp, "rogue.crit", 1);
        }
        e.setAmount(amount);
    }

    /** Magias do Ladino: Ponto Vital (crítico certo ×2) e meia chance de crítico ×1,5. */
    private static void onSpellDamage(SpellDamageEvent.Pre e) {
        if (!(e.ctx().casterPlayer() instanceof ServerPlayer sp) || e.target() == sp) return;
        if (MagicApi.get(sp).path() != MagicPath.ROGUE) return;
        ServerLevel level = e.ctx().level();
        LivingEntity target = e.target();
        RogueCombat.Mark mark = RogueCombat.markOf(target, sp);
        if (mark != null) {
            e.setAmount(e.getAmount() * RogueCombat.VITAL_MULT);
            RogueCombat.consumeMark(target);
            RogueFx.crit(level, target, true);
            MagicApi.addCounter(sp, "rogue.vital", 1);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.vital.hit").withColor(RogueFx.BLOOD), true);
        } else if (sp.getRandom().nextFloat() < RogueCombat.critChance(sp) * 0.5F) {
            e.setAmount(e.getAmount() * RogueCombat.CRIT_MULT);
            RogueFx.crit(level, target, false);
        }
    }

    /** Matar um alvo com a SUA marca de Ponto Vital devolve 10% do recurso. */
    private static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp)) return;
        boolean wasMarked = RogueCombat.wasMarkedBy(dead, sp);
        RogueCombat.forgetSpent(dead);
        if (!wasMarked || MagicApi.get(sp).path() != MagicPath.ROGUE) return;
        MagicData d = MagicApi.get(sp);
        d.addMana(d.maxMana() * 0.10F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.vital.kill", MagicApi.resourceName(sp))
                .withColor(RogueFx.BLOOD), true);
    }

    private RogueEvents() {}
}
