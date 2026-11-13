package com.renanpontes.isekaimahou.feature.glyphs_classes_a;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.fx.Vfx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Detecta a condição de cada gatilho de classe e chama {@link GcaTriggerEngine#react}:
 * <ul>
 *   <li><b>Mago — Ao Tecer Elementos</b>: 3 magias de elementos DIFERENTES (Arcano não conta) em até 8 s;</li>
 *   <li><b>Bruxa — Ao Beber Poção</b>: terminar de beber uma poção (ou um preparado do mod);</li>
 *   <li><b>Bruxo — Ao Ser Amaldiçoado</b>: um efeito NEGATIVO aplicado em você por outra criatura (mira em quem foi);</li>
 *   <li><b>Druida — Ao Fincar Raízes</b>: 3 s parado em chão natural (terra, grama, musgo, folhas, areia), com inimigo
 *       por perto ou ferido; repete a cada 6 s enquanto você continuar enraizado;</li>
 *   <li><b>Necromante — Ao Morrer um Lacaio Seu</b>: morre uma criatura sua (servo, invocação, pet) — mira no assassino;</li>
 *   <li><b>Ferreiro — Ao Malhar o Ferro</b>: o 3º golpe corpo a corpo seguido no mesmo alvo (ou forjar/remodelar na Forja);</li>
 *   <li><b>Ladino — Ao Roubar</b>: roubar item, mana, bênção ou magia;</li>
 *   <li><b>Cavaleiro — Ao Proteger um Aliado</b>: um aliado a até 8 blocos é atacado — o golpe nele cai 25%.</li>
 * </ul>
 */
public final class GcaTriggers {
    /** Janela do Ao Tecer Elementos (8 s). */
    private static final int WEAVE_WINDOW = 160;
    /** Janela entre golpes do Ao Malhar o Ferro (3 s). */
    private static final int HAMMER_WINDOW = 60;
    /** Druida: ticks parado para fincar raízes; depois de disparar, quanto falta para o próximo (6 s no total). */
    private static final int ROOT_TICKS = 60;
    private static final int ROOT_REARM = -60;
    /** Cavaleiro: alcance da proteção e redução do golpe no aliado. */
    private static final double GUARD_RANGE = 8.0;
    private static final float GUARD_REDUCTION = 0.25F;

    private static final Map<UUID, EnumMap<Element, Long>> WEAVE = new HashMap<>();
    private static final Map<UUID, Hammer> HAMMER = new HashMap<>();
    private static final Map<UUID, Root> ROOT = new HashMap<>();
    /** Jogadores com "Ao Proteger um Aliado" no loadout ativo (atualizado a cada 1 s). */
    private static final Set<UUID> GUARDS = new HashSet<>();

    private static final class Hammer {
        UUID target;
        int count;
        long last;
    }

    private static final class Root {
        Vec3 pos;
        int still;
    }

    // =====================================================================================
    //  Mago — Ao Tecer Elementos
    // =====================================================================================
    public static void onCastPost(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer || GcaTriggerEngine.autoCasting()) return;
        if (!event.getContext().isPlayerCast()) return;
        Element el = event.getContext().element();
        if (el == null || el == Element.ARCANE) return;
        if (!GcaTriggerEngine.hasTrigger(p, GcaGlyphs.MAGE_WEAVE)) {
            WEAVE.remove(p.getUUID());
            return;
        }
        long now = p.serverLevel().getGameTime();
        EnumMap<Element, Long> seen = WEAVE.computeIfAbsent(p.getUUID(), k -> new EnumMap<>(Element.class));
        seen.values().removeIf(t -> now - t > WEAVE_WINDOW);
        seen.put(el, now);
        ServerLevel level = p.serverLevel();
        if (seen.size() >= 3) {
            seen.clear();
            if (GcaTriggerEngine.react(p, GcaGlyphs.MAGE_WEAVE, null)) {
                ElementFx.runeCircle(level, p.position().add(0, 0.05, 0), MagicPath.MAGE.rgb(), 2.2F, 30, 3);
            }
        } else {
            // "Fio" tecido: o jogador vê quantos elementos já entraram na trama
            level.sendParticles(ElementFx.dust(el, 1.1F), p.getX(), p.getY() + 1.9, p.getZ(), 6 * seen.size(), 0.3, 0.1, 0.3, 0.01);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.glyphs_classes_a.weave", seen.size())
                    .withStyle(ChatFormatting.BLUE), true);
        }
    }

    // =====================================================================================
    //  Bruxa — Ao Beber Poção
    // =====================================================================================
    public static void onDrink(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        ItemStack used = event.getItem();
        if (used.isEmpty() || used.getUseAnimation() != UseAnim.DRINK) return;
        boolean potion = used.has(DataComponents.POTION_CONTENTS)
                || BuiltInRegistries.ITEM.getKey(used.getItem()).getNamespace().equals(IsekaiMahou.MODID);
        if (!potion) return;
        if (GcaTriggerEngine.react(p, GcaGlyphs.WITCH_DRINK, null)) {
            ServerLevel level = p.serverLevel();
            level.sendParticles(Vfx.witch(), p.getX(), p.getY() + 1.2, p.getZ(), 16, 0.4, 0.5, 0.4, 0.02);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }

    // =====================================================================================
    //  Bruxo — Ao Ser Amaldiçoado
    // =====================================================================================
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        MobEffectInstance inst = event.getEffectInstance();
        if (inst == null || inst.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;
        // Silêncio/Exaustão impedem conjurar mesmo: não há o que disparar
        if (inst.getEffect().is(CoreRegistries.SILENCED.getKey()) || inst.getEffect().is(CoreRegistries.MAGIC_EXHAUSTION.getKey())) return;
        Entity src = event.getEffectSource();
        if (src == p) return;
        LivingEntity aim = null;
        if (src instanceof LivingEntity le) aim = le;
        else if (src instanceof Projectile proj && proj.getOwner() instanceof LivingEntity le) aim = le;
        if (aim == p) return;
        if (aim != null && GcaAllies.isAlly(p, aim)) aim = null;
        if (GcaTriggerEngine.react(p, GcaGlyphs.WARLOCK_CURSED, aim)) {
            ServerLevel level = p.serverLevel();
            level.sendParticles(Vfx.crimsonSpore(), p.getX(), p.getY() + 1.0, p.getZ(), 18, 0.4, 0.6, 0.4, 0.02);
            if (aim != null) ElementFx.line(level, p.getEyePosition(), aim.getEyePosition(), Element.HELLFIRE);
        }
    }

    // =====================================================================================
    //  Necromante — Ao Morrer um Lacaio Seu
    // =====================================================================================
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof OwnableEntity own) || own.getOwnerUUID() == null || !(dead.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        ServerPlayer p = server.getPlayerList().getPlayer(own.getOwnerUUID());
        if (p == null || p.level() != level || p.distanceToSqr(dead) > 48 * 48) return;
        LivingEntity aim = null;
        if (event.getSource().getEntity() instanceof LivingEntity killer && killer != p && !GcaAllies.isAlly(p, killer)) aim = killer;
        if (aim == null) aim = GcaTriggerEngine.nearestEnemyAround(p, dead.position(), 10.0);
        if (!GcaTriggerEngine.hasTrigger(p, GcaGlyphs.NECRO_MINION)) return;
        Vec3 corpse = dead.position().add(0, dead.getBbHeight() * 0.5, 0);
        if (GcaTriggerEngine.react(p, GcaGlyphs.NECRO_MINION, aim)) {
            // A alma do lacaio volta para o mestre
            ElementFx.line(level, corpse, p.position().add(0, 1.0, 0), Element.SHADOW);
            level.sendParticles(Vfx.sculkSoul(), corpse.x, corpse.y, corpse.z, 14, 0.3, 0.4, 0.3, 0.04);
            level.playSound(null, corpse.x, corpse.y, corpse.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.4F, 1.5F);
        }
    }

    // =====================================================================================
    //  Ferreiro — Ao Malhar o Ferro
    // =====================================================================================
    public static void onDamagePost(LivingDamageEvent.Post event) {
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || src.getDirectEntity() != p) return;
        if (event.getNewDamage() <= 0 || event.getEntity() == p) return;
        if (src.is(CoreRegistries.SPELL_DAMAGE) || src.is(DamageTypeTags.IS_PROJECTILE) || src.is(DamageTypeTags.IS_EXPLOSION)) return;
        if (!GcaTriggerEngine.hasTrigger(p, GcaGlyphs.SMITH_HAMMER)) {
            HAMMER.remove(p.getUUID());
            return;
        }
        LivingEntity target = event.getEntity();
        long now = p.serverLevel().getGameTime();
        Hammer h = HAMMER.computeIfAbsent(p.getUUID(), k -> new Hammer());
        if (target.getUUID().equals(h.target) && now - h.last <= HAMMER_WINDOW) h.count++;
        else {
            h.target = target.getUUID();
            h.count = 1;
        }
        h.last = now;
        ServerLevel level = p.serverLevel();
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
        if (h.count >= 3) {
            h.count = 0;
            if (GcaTriggerEngine.react(p, GcaGlyphs.SMITH_HAMMER, target)) {
                level.sendParticles(Vfx.flame(), at.x, at.y, at.z, 14, 0.25, 0.25, 0.25, 0.12);
                level.sendParticles(Vfx.crit(), at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.3);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.4F);
            }
        } else {
            // Faíscas do martelo na bigorna: 1ª e 2ª batidas
            level.sendParticles(Vfx.crit(), at.x, at.y, at.z, 4 + 4 * h.count, 0.2, 0.2, 0.2, 0.2);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.12F, 1.6F + 0.2F * h.count);
        }
    }

    // =====================================================================================
    //  Contadores: Ferreiro (forjar/remodelar) e Ladino (roubos)
    // =====================================================================================
    public static void onCounter(MagicProgressEvent.Counter event) {
        ServerPlayer p = event.getPlayer();
        if (p == null || p instanceof FakePlayer) return;
        String key = event.key;
        if (key.equals("smith.forged") || key.equals("smith.reshaped")) {
            GcaTriggerEngine.react(p, GcaGlyphs.SMITH_HAMMER, null);
        } else if (key.equals("rogue.steal") || key.equals("rogue.buff_stolen") || key.equals("rogue.mana_stolen")
                || key.equals("rogue.heist")) {
            LivingEntity victim = GcaTriggerEngine.nearestEnemy(p, 6.0);
            if (GcaTriggerEngine.react(p, GcaGlyphs.ROGUE_STEAL, victim)) {
                ServerLevel level = p.serverLevel();
                level.sendParticles(Vfx.ink(), p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 0.6F);
            }
        }
    }

    // =====================================================================================
    //  Cavaleiro — Ao Proteger um Aliado
    // =====================================================================================
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (GUARDS.isEmpty() || event.getAmount() <= 0) return;
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == victim) return;
        for (UUID id : GUARDS) {
            if (id.equals(victim.getUUID()) || id.equals(attacker.getUUID())) continue;
            ServerPlayer knight = level.getServer().getPlayerList().getPlayer(id);
            if (knight == null || knight.level() != level || !knight.isAlive() || knight.distanceToSqr(victim) > GUARD_RANGE * GUARD_RANGE) continue;
            if (!GcaAllies.isAlly(knight, victim) || GcaAllies.isAlly(knight, attacker)) continue;
            if (!GcaTriggerEngine.react(knight, GcaGlyphs.KNIGHT_GUARD, attacker)) continue;
            event.setAmount(event.getAmount() * (1F - GUARD_REDUCTION));
            Vec3 from = knight.position().add(0, 1.1, 0), to = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
            ElementFx.line(level, from, to, Element.LIGHT);
            level.sendParticles(Vfx.endRod(), to.x, to.y, to.z, 10, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, to.x, to.y, to.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.2F);
            knight.displayClientMessage(Component.translatable("message.isekai_mahou.glyphs_classes_a.guard", victim.getDisplayName())
                    .withStyle(ChatFormatting.AQUA), true);
            return; // um cavaleiro por golpe
        }
    }

    // =====================================================================================
    //  Tick: Druida (raízes) e a lista de cavaleiros
    // =====================================================================================
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 10 != 0) return;
        boolean refreshGuards = tick % 20 == 0;
        if (refreshGuards) GUARDS.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof FakePlayer || p.isSpectator()) continue;
            if (refreshGuards && GcaTriggerEngine.hasTrigger(p, GcaGlyphs.KNIGHT_GUARD)) GUARDS.add(p.getUUID());
            tickRoot(p);
        }
    }

    private static void tickRoot(ServerPlayer p) {
        if (!p.isAlive() || !GcaTriggerEngine.hasTrigger(p, GcaGlyphs.DRUID_ROOTED)) {
            ROOT.remove(p.getUUID());
            return;
        }
        Root r = ROOT.computeIfAbsent(p.getUUID(), k -> new Root());
        Vec3 now = p.position();
        boolean still = r.pos != null && r.pos.distanceToSqr(now) < 0.0025 && p.onGround() && naturalGround(p);
        r.pos = now;
        if (!still) {
            r.still = 0;
            return;
        }
        r.still += 10;
        ServerLevel level = p.serverLevel();
        if (r.still > 0 && r.still < ROOT_TICKS) {
            // Raízes descendo dos pés: o jogador vê que está fincando
            level.sendParticles(Vfx.composter(), p.getX(), p.getY() + 0.1, p.getZ(), 3 + r.still / 10, 0.35, 0.05, 0.35, 0.0);
            return;
        }
        if (r.still < ROOT_TICKS) return;
        LivingEntity enemy = GcaTriggerEngine.nearestEnemy(p, GcaTriggerEngine.AIM_RANGE);
        if (enemy == null && p.getHealth() >= p.getMaxHealth()) return; // nada a fazer: espera enraizado
        if (GcaTriggerEngine.react(p, GcaGlyphs.DRUID_ROOTED, enemy)) {
            r.still = ROOT_REARM;
            ElementFx.area(level, p.position(), Element.NATURE, MagicPath.DRUID.rgb(), 1.6F, 1, 30);
            level.sendParticles(Vfx.happy(), p.getX(), p.getY() + 0.3, p.getZ(), 12, 0.6, 0.1, 0.6, 0.02);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ROOTED_DIRT_PLACE, SoundSource.PLAYERS, 0.9F, 0.7F);
        }
    }

    /** Terra, grama, musgo, lama, areia ou folhas embaixo dos pés. */
    private static boolean naturalGround(ServerPlayer p) {
        BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
        BlockState s = p.serverLevel().getBlockState(below);
        return s.is(BlockTags.DIRT) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAND) || s.is(BlockTags.NYLIUM);
    }

    // =====================================================================================
    //  Limpeza
    // =====================================================================================
    public static void forget(UUID id) {
        WEAVE.remove(id);
        HAMMER.remove(id);
        ROOT.remove(id);
        GUARDS.remove(id);
    }

    public static void clear() {
        WEAVE.clear();
        HAMMER.clear();
        ROOT.clear();
        GUARDS.clear();
    }

    private GcaTriggers() {}
}
