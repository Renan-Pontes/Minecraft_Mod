package com.renanpontes.isekaimahou.feature.glyphs_classes_b;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Conjuração automática dos 7 gatilhos do módulo (mesmo modelo do "Ao Jackpot"): quando a condição acontece, cada slot do
 * loadout ativo cuja receita COMEÇA com o gatilho sai sozinho — 1 tick depois (fora do meio do cálculo de dano), um slot
 * a cada 5 ticks, virado para o alvo do gatilho (ou o inimigo mais próximo). Segue as regras de uma conjuração normal:
 * receita válida, Silêncio/Exaustão, recarga do slot, slot G só com ultimate e carga cheia, recurso (ou vida, com
 * Sobrecarga). Apertar a tecla de um slot desses não conjura.
 * <p>
 * Cada gatilho tem um intervalo mínimo próprio ({@code icdTicks}) além da recarga do slot, e nunca dispara a partir de
 * uma conjuração automática deste módulo ({@link #busy()}), para não virar laço.
 */
public final class TriggerCaster {
    private static final double AIM_RANGE = 20;
    private static int autoDepth;
    /** "uuid|gatilho" → último disparo (tempo de jogo). */
    private static final Map<String, Long> LAST = new HashMap<>();

    /** Uma conjuração automática deste módulo está acontecendo agora (thread do servidor). */
    public static boolean busy() {
        return autoDepth > 0;
    }

    /** Apertar a tecla de um slot que começa com um gatilho do módulo não conjura. */
    public static void onPre(SpellCastEvent.Pre event) {
        if (autoDepth > 0 || !(event.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (!event.getContext().isPlayerCast()) return;
        for (TriggerGlyph t : event.getContext().spell().triggers()) {
            if (GcbIds.TRIGGERS.contains(t.id())) {
                event.setCanceled(true);
                p.displayClientMessage(Component.translatable(GcbIds.MSG + "trigger_only", t.displayName())
                        .withStyle(ChatFormatting.GOLD), true);
                return;
            }
        }
    }

    /** A receita começa com o gatilho (entre os gatilhos antes da forma)? */
    public static boolean leadsWith(@Nullable SpellRecipe recipe, ResourceLocation trigger) {
        if (recipe == null) return false;
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (!(g instanceof TriggerGlyph)) return false;
            if (trigger.equals(id)) return true;
        }
        return false;
    }

    /** Algum slot do loadout ativo começa com o gatilho? (checagem barata antes de qualquer outra conta) */
    public static boolean hasSlot(ServerPlayer p, ResourceLocation trigger) {
        if (p instanceof FakePlayer) return false;
        MagicData d = MagicApi.get(p);
        if (!d.knowsGlyph(trigger) && !p.getAbilities().instabuild) return false;
        int loadout = d.activeLoadout();
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            if (leadsWith(d.peekSlot(loadout, slot).main(), trigger)) return true;
        }
        return false;
    }

    /** O intervalo mínimo do gatilho já passou? (checagem barata antes de procurar alvos) */
    public static boolean ready(ServerPlayer p, ResourceLocation trigger, int icdTicks) {
        Long last = LAST.get(p.getUUID() + "|" + trigger.getPath());
        return last == null || p.serverLevel().getGameTime() - last >= icdTicks;
    }

    public static boolean fire(ServerPlayer p, ResourceLocation trigger, @Nullable LivingEntity target, int icdTicks) {
        return fire(p, trigger, target, icdTicks, null, null);
    }

    /**
     * Dispara o gatilho: agenda os slots que começam com ele.
     *
     * @param target    alvo para mirar (null = inimigo mais próximo com linha de visão)
     * @param icdTicks  intervalo mínimo entre dois disparos deste gatilho para este jogador
     * @param gate      checado na hora de cada conjuração (null = sempre); false = não conjura
     * @param onSuccess chamado depois de cada conjuração que saiu
     * @return true se algum slot foi agendado
     */
    public static boolean fire(ServerPlayer p, ResourceLocation trigger, @Nullable LivingEntity target, int icdTicks,
                               @Nullable Predicate<ServerPlayer> gate, @Nullable Consumer<ServerPlayer> onSuccess) {
        if (autoDepth > 0 || p instanceof FakePlayer || p.isSpectator() || !p.isAlive()) return false;
        if (p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) return false;
        long now = p.serverLevel().getGameTime();
        String key = p.getUUID() + "|" + trigger.getPath();
        Long last = LAST.get(key);
        if (last != null && now - last < icdTicks) return false;
        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        int delay = 1;
        boolean any = false;
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(loadout, slot).main();
            if (recipe == null || recipe.isEmpty() || !leadsWith(recipe, trigger)) continue;
            int s = slot;
            SpellScheduler.schedule(delay, () -> {
                if (p.isRemoved() || !p.isAlive()) return;
                if (gate != null && !gate.test(p)) return;
                if (autoCast(p, loadout, s, recipe, target) && onSuccess != null) onSuccess.accept(p);
            });
            delay += 5;
            any = true;
        }
        if (any) {
            LAST.put(key, now);
            if (LAST.size() > 512) {
                Iterator<Map.Entry<String, Long>> it = LAST.entrySet().iterator();
                while (it.hasNext()) if (now - it.next().getValue() > 1200) it.remove();
            }
        }
        return any;
    }

    private static boolean autoCast(ServerPlayer p, int loadout, int slot, SpellRecipe recipe, @Nullable LivingEntity wanted) {
        if (p.isSpectator() || p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) return false;
        MagicData d = MagicApi.get(p);
        if (d.activeLoadout() != loadout || d.channelSlot >= 0) return false;
        boolean creative = p.getAbilities().instabuild;
        Element el = SpellCost.primaryElement(recipe);
        SpellParser.Result r = SpellParser.parse(recipe, el, creative ? null : d);
        if (!r.ok()) return false;
        ParsedSpell spell = r.spell();
        if (spell.isUltimate() != (slot == Progression.ULTIMATE_SLOT)) return false;
        if (spell.isUltimate() && !creative && d.charge() < Progression.MAX_CHARGE) return false;
        String cdKey = MagicData.slotKey(loadout, slot);
        if (p.serverLevel().getGameTime() < d.cooldowns.getOrDefault(cdKey, 0L)) return false;
        if (!creative && !canPay(p, d, spell)) return false;

        LivingEntity target = wanted != null && wanted.isAlive() && wanted.level() == p.level() ? wanted : nearestEnemy(p);
        float yRot = p.getYRot(), xRot = p.getXRot(), headRot = p.getYHeadRot();
        if (target != null) face(p, center(target));
        autoDepth++;
        boolean ok;
        try {
            ok = SpellCaster.performCast(p, d, spell.primary(), recipe, spell, cdKey, false);
        } finally {
            autoDepth--;
            if (target != null) {
                p.setYRot(yRot);
                p.setXRot(xRot);
                p.setYHeadRot(headRot);
            }
        }
        if (ok) {
            ServerLevel sl = p.serverLevel();
            GcbFx.ring(sl, p.position().add(0, 0.1, 0), 0.9, 12, GcbFx.dust(p.getRandom().nextBoolean() ? 0xFFFFFF : 0xFFE27A, 0.7F));
            GcbFx.sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.5F);
        }
        return ok;
    }

    private static boolean canPay(ServerPlayer p, MagicData d, ParsedSpell spell) {
        if (spell.anyStats(s -> s.overload)) return p.getHealth() > SpellCaster.overloadHealthCost(spell) + 1.0F;
        return d.mana() >= spell.manaCost();
    }

    private static Vec3 center(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Vira o jogador (só no servidor, durante a conjuração) para um ponto. */
    public static void face(ServerPlayer p, Vec3 at) {
        Vec3 to = at.subtract(p.getEyePosition());
        double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(to.y, horiz) * Mth.RAD_TO_DEG);
        p.setYRot(yaw);
        p.setXRot(Mth.clamp(pitch, -90F, 90F));
        p.setYHeadRot(yaw);
    }

    /** Inimigo vivo mais próximo com linha de visão (monstros ou quem está mirando o jogador). */
    @Nullable
    public static LivingEntity nearestEnemy(ServerPlayer p) {
        LivingEntity best = null;
        double bestD = AIM_RANGE * AIM_RANGE;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(AIM_RANGE),
                e -> e != p && e.isAlive() && (e instanceof Enemy || (e instanceof Mob m && m.getTarget() == p)) && p.hasLineOfSight(e))) {
            double dd = e.distanceToSqr(p);
            if (dd < bestD) {
                bestD = dd;
                best = e;
            }
        }
        return best;
    }

    /** Criatura viva mais perto da mira (cone de ~10°) até {@code range} blocos, com linha de visão; ou null. */
    @Nullable
    public static LivingEntity aimed(LivingEntity p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = -1;
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(2.0);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator())) {
            Vec3 to = center(e).subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.01) continue;
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot < 0.985 || !p.hasLineOfSight(e)) continue;
            double score = dot - dist * 0.001;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    public static void forget(UUID id) {
        String prefix = id + "|";
        LAST.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void clear() {
        LAST.clear();
        autoDepth = 0;
    }

    private TriggerCaster() {}
}
