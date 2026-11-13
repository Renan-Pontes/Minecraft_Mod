package com.renanpontes.isekaimahou.feature.glyphs_elements_a;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneTriggers;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Gatilhos elementais REATIVOS (v1.4). Vão no começo da receita; quando a condição acontece, cada slot do loadout ativo
 * que começa com o gatilho é conjurado sozinho (na hora, sem tempo de conjuração, pagando mana e respeitando
 * recarga/Silêncio — mesmas regras dos gatilhos Arcanos). Apertar a tecla desses slots não conjura.
 * <ul>
 *   <li>Fogo — Ao Incendiar: um inimigo começa a arder (Em Chamas) por sua causa. Mira nele.</li>
 *   <li>Água — Maré: você fica Molhado (chuva, água, magia) ou um inimigo fica Molhado por sua causa.</li>
 *   <li>Terra — Ao Tocar o Chão: você aterrissa depois de cair 4+ blocos.</li>
 *   <li>Vento — Ao Pegar Embalo: depois de 3 s correndo sem parar (uma vez por corrida).</li>
 *   <li>Luz — Ao Curar um Aliado: uma magia sua cura um aliado (não você).</li>
 *   <li>Sombra — Ao Mergulhar nas Sombras: você entra na escuridão (luz ≤ 4) ou fica invisível. Mira no inimigo mais perto.</li>
 *   <li>Arcano — Ressonância: a cada 4 magias que você conjura, o slot sai junto.</li>
 * </ul>
 * Os gatilhos que dependem de checagem periódica (Embalo, Sombras, Maré da chuva) só rodam para quem tem o gatilho no
 * loadout ativo (cache refeito a cada 2 s) e no máximo a cada 5–10 ticks.
 */
public final class ElementTriggers {
    /** Intervalo mínimo entre dois disparos do MESMO gatilho para o mesmo jogador (além da recarga do slot). */
    private static final int MIN_INTERVAL = 20;
    private static final float MIN_FALL = 4.0F;
    private static final int MOMENTUM_TICKS = 60;
    private static final int DARK_LIGHT = 4;
    private static final int RESONANCE_EVERY = 4;
    /** Ao Curar um Aliado: só conta cura logo depois de uma magia sua de efeito benéfico (4 s), até 32 blocos. */
    private static final long SUPPORT_WINDOW = 80;
    private static final double SUPPORT_RANGE = 32.0;
    /** Curas menores que isso são regeneração natural/comida (1 ponto), não magia. */
    private static final float MIN_HEAL = 1.5F;
    private static final double AIM_RANGE = 20.0;

    private static int autoDepth;
    private static final Set<String> PENDING = new HashSet<>();
    /** "jogador|gatilho" → tick do último disparo. */
    private static final Map<String, Long> LAST = new HashMap<>();
    /** Gatilhos deste módulo no loadout ativo de cada jogador (cache). */
    private static final Map<UUID, Set<ResourceLocation>> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> SPRINT = new HashMap<>();
    private static final Set<UUID> MOMENTUM_FIRED = new HashSet<>();
    private static final Set<UUID> IN_DARK = new HashSet<>();
    private static final Set<UUID> IN_RAIN = new HashSet<>();
    private static final Map<UUID, Integer> RESONANCE = new HashMap<>();
    /** Jogador → tick da última magia com efeito benéfico (cura/escudo...). */
    private static final Map<UUID, Long> SUPPORT = new HashMap<>();

    // =====================================================================================
    //  Bloqueio da tecla e contagem da Ressonância
    // =====================================================================================

    /** Apertar a tecla de um slot com gatilho elemental não conjura (ele só sai sozinho). */
    public static void onPre(SpellCastEvent.Pre event) {
        if (autoDepth > 0 || !(event.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (!event.getContext().isPlayerCast()) return;
        ParsedSpell spell = event.getContext().spell();
        for (TriggerGlyph t : spell.triggers()) {
            if (ElementGlyphsA.TRIGGERS.contains(t.id())) {
                event.setCanceled(true);
                p.displayClientMessage(Component.translatable("message.isekai_mahou.glyphs_elements_a.reactive_only", t.displayName())
                        .withStyle(ChatFormatting.GOLD), true);
                return;
            }
        }
    }

    public static void onPost(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        ParsedSpell spell = event.getContext().spell();
        long now = p.serverLevel().getGameTime();
        // Magia de suporte (para "Ao Curar um Aliado")
        for (ParsedSpell.EffectEntry e : spell.effects()) {
            EffectGlyph g = e.effect();
            if (g.isBeneficial() || g.baseHeal() > 0F) {
                SUPPORT.put(p.getUUID(), now);
                break;
            }
        }
        // Ressonância: conta as magias conjuradas (não as que este módulo disparou, nem as próprias de Ressonância)
        if (autoDepth > 0 || spell.hasTrigger(ElementGlyphsA.RESONANCE.getPath())) return;
        if (!active(p).contains(ElementGlyphsA.RESONANCE)) return;
        int n = RESONANCE.merge(p.getUUID(), 1, Integer::sum);
        if (n >= RESONANCE_EVERY) {
            RESONANCE.put(p.getUUID(), 0);
            react(p, ElementGlyphsA.RESONANCE, null);
        }
    }

    // =====================================================================================
    //  Condições
    // =====================================================================================

    /** Ao Incendiar (alvo começa a arder por sua causa) e Maré (você ou um alvo seu fica Molhado). */
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getOldEffectInstance() != null) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        var eff = event.getEffectInstance().getEffect();
        boolean burning = eff.is(CoreRegistries.BURNING);
        boolean wet = eff.is(CoreRegistries.SOAKED);
        if (!burning && !wet) return;
        if (wet && target instanceof ServerPlayer self && !(self instanceof FakePlayer)) {
            react(self, ElementGlyphsA.ON_WET, null);
            return;
        }
        Entity src = event.getEffectSource();
        if (!(src instanceof ServerPlayer p) || p == target || p instanceof FakePlayer) return;
        react(p, burning ? ElementGlyphsA.ON_IGNITE : ElementGlyphsA.ON_WET, target);
    }

    /** Ao Tocar o Chão: aterrissou depois de cair 4+ blocos. */
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) && event.getDistance() >= MIN_FALL
                && !p.getAbilities().flying) {
            react(p, ElementGlyphsA.ON_LANDING, null);
        }
    }

    /** Ao Curar um Aliado: a cura vem logo depois de uma magia de suporte de um jogador próximo e aliado. */
    public static void onHeal(LivingHealEvent event) {
        if (SUPPORT.isEmpty() || event.getAmount() < MIN_HEAL) return;
        LivingEntity healed = event.getEntity();
        if (!(healed.level() instanceof ServerLevel level) || !healed.isAlive() || healed.getHealth() >= healed.getMaxHealth()) return;
        long now = level.getGameTime();
        for (Map.Entry<UUID, Long> e : SUPPORT.entrySet()) {
            if (now - e.getValue() > SUPPORT_WINDOW) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || p == healed || p.level() != level || p.distanceToSqr(healed) > SUPPORT_RANGE * SUPPORT_RANGE) continue;
            if (!isAlly(p, healed) || !active(p).contains(ElementGlyphsA.ON_ALLY_HEAL)) continue;
            react(p, ElementGlyphsA.ON_ALLY_HEAL, null);
        }
    }

    /** Checagens periódicas (Embalo, Sombras, Maré da chuva/água sem a condição Molhado). */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || !p.isAlive()) return;
        int phase = (p.tickCount + p.getId()) % 40;
        if (phase == 0) refreshActive(p);
        if (phase % 5 != 0) return;
        Set<ResourceLocation> act = ACTIVE.get(p.getUUID());
        if (act == null || act.isEmpty()) return;
        UUID id = p.getUUID();

        if (act.contains(ElementGlyphsA.ON_MOMENTUM)) {
            if (p.isSprinting() && !p.isSpectator()) {
                int t = SPRINT.merge(id, 5, Integer::sum);
                if (t >= MOMENTUM_TICKS && MOMENTUM_FIRED.add(id)) react(p, ElementGlyphsA.ON_MOMENTUM, null);
            } else {
                SPRINT.remove(id);
                MOMENTUM_FIRED.remove(id);
            }
        }
        if (phase % 10 != 0) return;
        if (act.contains(ElementGlyphsA.IN_SHADOWS)) {
            BlockPos eye = BlockPos.containing(p.getEyePosition());
            boolean dark = p.isInvisible() || p.serverLevel().getMaxLocalRawBrightness(eye) <= DARK_LIGHT;
            if (dark) {
                if (IN_DARK.add(id)) {
                    p.serverLevel().sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 8, 0.3, 0.5, 0.3, 0.01);
                    react(p, ElementGlyphsA.IN_SHADOWS, nearestEnemy(p));
                }
            } else {
                IN_DARK.remove(id);
            }
        }
        if (act.contains(ElementGlyphsA.ON_WET)) {
            // Com a condição Molhado desligada para jogadores (config), a chuva/água ainda dispara a Maré
            boolean wet = p.isInWaterRainOrBubble();
            if (wet) {
                if (IN_RAIN.add(id)) react(p, ElementGlyphsA.ON_WET, null);
            } else {
                IN_RAIN.remove(id);
            }
        }
    }

    // =====================================================================================
    //  Disparo
    // =====================================================================================

    /** Agenda (próximo tick) os slots do loadout ativo que começam com o gatilho. */
    public static void react(ServerPlayer p, ResourceLocation trigger, @Nullable LivingEntity aim) {
        if (!active(p).contains(trigger)) return;
        long now = p.serverLevel().getGameTime();
        String lastKey = p.getUUID() + "|" + trigger.getPath();
        Long last = LAST.get(lastKey);
        if (last != null && now - last < MIN_INTERVAL && now >= last) return;
        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        boolean any = false;
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(loadout, slot).main();
            if (recipe == null || recipe.isEmpty() || !ArcaneTriggers.leadingTriggers(recipe).contains(trigger)) continue;
            String key = p.getUUID() + "|" + MagicData.slotKey(loadout, slot);
            if (!PENDING.add(key)) continue;
            any = true;
            int s = slot;
            SpellScheduler.schedule(1, () -> {
                PENDING.remove(key);
                if (!p.isRemoved() && p.isAlive()) autoCast(p, loadout, s, recipe, trigger, aim);
            });
        }
        if (any) LAST.put(lastKey, now);
    }

    private static void autoCast(ServerPlayer p, int loadout, int slot, SpellRecipe recipe, ResourceLocation trigger,
                                 @Nullable LivingEntity aim) {
        if (p.isSpectator() || p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) return;
        MagicData d = MagicApi.get(p);
        if (d.activeLoadout() != loadout) return;
        boolean creative = p.getAbilities().instabuild;
        Element el = SpellCost.primaryElement(recipe);
        SpellParser.Result r = SpellParser.parse(recipe, el, creative ? null : d);
        if (!r.ok()) return;
        ParsedSpell spell = r.spell();
        if (spell.isUltimate() != (slot == Progression.ULTIMATE_SLOT)) return;
        if (spell.isUltimate() && !creative && d.charge() < Progression.MAX_CHARGE) return;
        String cdKey = MagicData.slotKey(loadout, slot);
        if (p.serverLevel().getGameTime() < d.cooldowns.getOrDefault(cdKey, 0L)) return;
        if (!creative && !canPay(p, d, spell)) return;

        boolean turn = aim != null && aim.isAlive() && aim.level() == p.level() && aim.distanceToSqr(p) <= AIM_RANGE * AIM_RANGE;
        float yRot = p.getYRot(), xRot = p.getXRot(), headRot = p.getYHeadRot();
        if (turn) {
            Vec3 to = aim.position().add(0, aim.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
            float pitch = (float) -(Mth.atan2(to.y, horiz) * Mth.RAD_TO_DEG);
            p.setYRot(yaw);
            p.setXRot(Mth.clamp(pitch, -90F, 90F));
            p.setYHeadRot(yaw);
        }
        autoDepth++;
        boolean ok;
        try {
            ok = SpellCaster.performCast(p, d, spell.primary(), recipe, spell, cdKey, false);
        } finally {
            autoDepth--;
            if (turn) {
                p.setYRot(yRot);
                p.setXRot(xRot);
                p.setYHeadRot(headRot);
            }
        }
        if (ok) {
            // "O gatilho disparou": anel do elemento do gatilho e um toque curto
            Element te = com.renanpontes.isekaimahou.glyph.GlyphRegistry.get(trigger) instanceof TriggerGlyph tg ? tg.element() : el;
            ElementFx.ring(p.serverLevel(), p.position().add(0, 0.1, 0), te, 1.0, 16);
            sound(p, triggerSound(trigger), 0.7F, 1.3F);
        }
    }

    private static boolean canPay(ServerPlayer p, MagicData d, ParsedSpell spell) {
        if (spell.anyStats(s -> s.overload)) return p.getHealth() > SpellCaster.overloadHealthCost(spell) + 1.0F;
        return d.mana() >= spell.manaCost();
    }

    private static SoundEvent triggerSound(ResourceLocation t) {
        if (t.equals(ElementGlyphsA.ON_IGNITE)) return SoundEvents.FIRECHARGE_USE;
        if (t.equals(ElementGlyphsA.ON_WET)) return SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE;
        if (t.equals(ElementGlyphsA.ON_LANDING)) return SoundEvents.ROOTED_DIRT_BREAK;
        if (t.equals(ElementGlyphsA.ON_MOMENTUM)) return SoundEvents.PHANTOM_FLAP;
        if (t.equals(ElementGlyphsA.ON_ALLY_HEAL)) return SoundEvents.AMETHYST_BLOCK_RESONATE;
        if (t.equals(ElementGlyphsA.IN_SHADOWS)) return SoundEvents.SCULK_CLICKING;
        return SoundEvents.ENCHANTMENT_TABLE_USE;
    }

    private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
    }

    // =====================================================================================
    //  Auxiliares
    // =====================================================================================

    /** Gatilhos deste módulo nos slots do loadout ativo (cache; refeito a cada 2 s ou na primeira consulta). */
    static Set<ResourceLocation> active(ServerPlayer p) {
        Set<ResourceLocation> s = ACTIVE.get(p.getUUID());
        return s != null ? s : refreshActive(p);
    }

    private static Set<ResourceLocation> refreshActive(ServerPlayer p) {
        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        Set<ResourceLocation> out = new HashSet<>();
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(loadout, slot).main();
            if (recipe == null || recipe.isEmpty()) continue;
            for (ResourceLocation id : ArcaneTriggers.leadingTriggers(recipe)) {
                if (ElementGlyphsA.TRIGGERS.contains(id)) out.add(id);
            }
        }
        ACTIVE.put(p.getUUID(), out);
        return out;
    }

    private static boolean isAlly(ServerPlayer p, LivingEntity e) {
        if (e instanceof OwnableEntity own && p.getUUID().equals(own.getOwnerUUID())) return true;
        Boolean social = Party.allyOverride(p, e);
        if (social != null) return social;
        if (p.isAlliedTo(e)) return true;
        return e instanceof Player && !p.serverLevel().getServer().isPvpAllowed();
    }

    @Nullable
    private static LivingEntity nearestEnemy(ServerPlayer p) {
        LivingEntity best = null;
        double bestD = AIM_RANGE * AIM_RANGE;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(AIM_RANGE),
                e -> e != p && e.isAlive() && e instanceof Enemy && p.hasLineOfSight(e))) {
            double d = e.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    static void cleanup(long now) {
        LAST.values().removeIf(t -> now - t > 200 || t > now);
        SUPPORT.values().removeIf(t -> now - t > SUPPORT_WINDOW || t > now);
    }

    static void forget(UUID id) {
        ACTIVE.remove(id);
        SPRINT.remove(id);
        MOMENTUM_FIRED.remove(id);
        IN_DARK.remove(id);
        IN_RAIN.remove(id);
        RESONANCE.remove(id);
        SUPPORT.remove(id);
        String prefix = id + "|";
        LAST.keySet().removeIf(k -> k.startsWith(prefix));
    }

    static void clear() {
        autoDepth = 0;
        PENDING.clear();
        LAST.clear();
        ACTIVE.clear();
        SPRINT.clear();
        MOMENTUM_FIRED.clear();
        IN_DARK.clear();
        IN_RAIN.clear();
        RESONANCE.clear();
        SUPPORT.clear();
    }

    private ElementTriggers() {}
}
