package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreGameplay;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.class_knight.glyph.KnightGlyphs;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.weapon.MeleeSwings;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Eventos de jogo do Cavaleiro Rúnico: golpes rúnicos, eco das lendárias, marca, égide, contadores dos Juramentos,
 * bônus de classe, magias iniciais e os tiques dos sistemas (ondas, investidas, espadas que caem).
 * <p><b>Bônus de classe</b> (Cavaleiro com espada na mão principal, conferido a cada 0,5 s): Armadura
 * +(3 + Círculo/2 + Juramento), Resistência de armadura +(1 + 0,4·Juramento) e resistência a repulsão
 * +(30% + 5%·Juramento); a Coroa Rúnica (Juramento V) soma +2 de Armadura.
 */
public final class KnightEvents {
    private static final ResourceLocation ARMOR = IsekaiMahou.id("knight_class.armor");
    private static final ResourceLocation TOUGHNESS = IsekaiMahou.id("knight_class.toughness");
    private static final ResourceLocation STEADY = IsekaiMahou.id("knight_class.knockback");

    public static void register() {
        NeoForge.EVENT_BUS.addListener(KnightEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(KnightAegis::onIncoming);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, KnightWards::onIncoming);
        NeoForge.EVENT_BUS.addListener(KnightEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(KnightEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(KnightEvents::onCounter);
        NeoForge.EVENT_BUS.addListener(KnightEvents::onPathChosen);
        NeoForge.EVENT_BUS.addListener(KnightEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(KnightEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(KnightEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(KnightEvents::onStopped);
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        RuneEngraving.onDamage(event);
        SwordAbilities.onDamage(event);
        KnightWards.onMelee(event);
        // golpe de espada do dono da Marca Rúnica soma um acúmulo
        DamageSource src = event.getSource();
        if (src.getEntity() instanceof ServerPlayer p && src.getDirectEntity() == p && src.is(DamageTypes.PLAYER_ATTACK)
                && KnightSwords.holdsSword(p) && MeleeSwings.mainHitStrength(p, event.getEntity(), p.level().getGameTime()) >= 0.9F) {
            KnightMarks.onMelee(p, event.getEntity());
        }
    }

    /** Abate com espada (golpe corpo a corpo do Cavaleiro). */
    private static void onDeath(LivingDeathEvent event) {
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p) || src.getDirectEntity() != p || !src.is(DamageTypes.PLAYER_ATTACK)) return;
        if (!KnightSwords.holdsSword(p) || !KnightPower.isKnight(p)) return;
        LivingEntity dead = event.getEntity();
        // só inimigos de verdade (hostis ou jogadores): galinhas e vacas não contam para o Juramento
        if (dead == p || !(dead instanceof net.minecraft.world.entity.monster.Enemy || dead instanceof net.minecraft.world.entity.player.Player)) return;
        MagicApi.addCounter(p, KnightCounters.SWORD_KILL, 1);
    }

    /** Qualquer {@code boss.<mob>} somado pelo núcleo conta como chefe vencido para o Juramento. */
    private static void onCounter(MagicProgressEvent.Counter event) {
        if (!event.key.startsWith("boss.")) return;
        ServerPlayer p = event.getPlayer();
        if (MagicApi.get(p).path() == MagicPath.KNIGHT) MagicApi.addCounter(p, KnightCounters.BOSSES, 1);
    }

    // =====================================================================================
    //  Classe escolhida: glifos e magias iniciais
    // =====================================================================================
    private static void onPathChosen(MagicProgressEvent.PathChosen event) {
        ServerPlayer p = event.getPlayer();
        MagicData d = MagicApi.get(p);
        if (d.path() != MagicPath.KNIGHT) return;
        for (String g : KnightGlyphs.STARTER) MagicApi.learnGlyphSilently(p, IsekaiMahou.id(g));
        boolean pt = p.clientInformation().language().toLowerCase(java.util.Locale.ROOT).startsWith("pt");
        SpellRecipe beam = new SpellRecipe(List.of(IsekaiMahou.id(KnightGlyphs.SWORD_BEAM), IsekaiMahou.id("arcane_damage")),
                pt ? "Raio de Espada" : "Sword Beam", 0xBFE0FF, "");
        SpellRecipe engrave = new SpellRecipe(List.of(IsekaiMahou.id(KnightGlyphs.ENGRAVE), IsekaiMahou.id(KnightGlyphs.RUNE_MARK)),
                pt ? "Runa da Marca" : "Rune of Marking", 0x8FD8FF, "");
        // as técnicas genéricas da categoria (Golpe Arcano, Postura Firme) vão para o loadout 2; as do Cavaleiro ficam no 1
        SpellRecipe z = d.peekSlot(0, 0).get(MoveTrigger.NORMAL);
        SpellRecipe x = d.peekSlot(0, 1).get(MoveTrigger.NORMAL);
        boolean starter = sameGlyphs(z, CoreGameplay.STARTER_STRIKE) && sameGlyphs(x, CoreGameplay.STARTER_STANCE);
        boolean empty = (z == null || z.isEmpty()) && (x == null || x.isEmpty());
        if (!starter && !empty) return;
        if (starter && d.loadoutEmpty(1)) {
            d.setSlotRecipe(1, 0, MoveTrigger.NORMAL, z);
            d.setSlotRecipe(1, 1, MoveTrigger.NORMAL, x);
        }
        d.setSlotRecipe(0, 0, MoveTrigger.NORMAL, beam);
        d.setSlotRecipe(0, 1, MoveTrigger.NORMAL, engrave);
        d.markDirty();
    }

    private static boolean sameGlyphs(SpellRecipe a, SpellRecipe b) {
        return a != null && b != null && a.glyphs().equals(b.glyphs());
    }

    // =====================================================================================
    //  Tiques
    // =====================================================================================
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        MagicData d = MagicApi.get(p);
        boolean knight = d.path() == MagicPath.KNIGHT;
        boolean bonus = knight && KnightSwords.holdsSword(p);
        int oath = knight ? d.counter(KnightCounters.OATH) : 0;
        setBonus(p, Attributes.ARMOR, ARMOR, bonus, 3.0 + d.circle() * 0.5 + oath + (oath >= 5 ? 2 : 0));
        setBonus(p, Attributes.ARMOR_TOUGHNESS, TOUGHNESS, bonus, 1.0 + 0.4 * oath);
        setBonus(p, Attributes.KNOCKBACK_RESISTANCE, STEADY, bonus, 0.3 + 0.05 * oath);
        if (p.tickCount % 20 == 0) SwordAbilities.passive(p);
    }

    private static void setBonus(ServerPlayer p, Holder<Attribute> attr, ResourceLocation id, boolean on, double value) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        if (on) {
            AttributeModifier cur = inst.getModifier(id);
            if (cur == null || Math.abs(cur.amount() - value) > 1.0E-6) {
                inst.addOrUpdateTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
            }
        } else if (inst.getModifier(id) != null) {
            inst.removeModifier(id);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        BladeWaves.tick(server);
        KnightMotion.tick(server);
        KnightSkyfall.tick(server);
        KnightAegis.tick(server);
        KnightWards.tick(server);
        KnightMarks.tick(server);
        if (server.getTickCount() % 200 == 0) KnightCounters.flushAll(server);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        KnightCounters.flush(p);
        KnightCounters.forget(p.getUUID());
        RuneEngraving.forget(p.getUUID());
        SwordAbilities.forget(p.getUUID());
        KnightWards.forget(p.getUUID());
    }

    /** Login: técnicas novas dos Juramentos/espadas já conquistados (saves de antes do pacote). */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) KnightTechniques.catchUp(p);
    }

    private static void onStopped(ServerStoppedEvent event) {
        BladeWaves.clear();
        KnightMotion.clear();
        KnightSkyfall.clear();
        KnightAegis.clear();
        KnightWards.clear();
        KnightMarks.clear();
        KnightCounters.clear();
        RuneEngraving.clear();
        SwordAbilities.clear();
        com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightOutfitRewards.clear();
    }

    private KnightEvents() {}
}
