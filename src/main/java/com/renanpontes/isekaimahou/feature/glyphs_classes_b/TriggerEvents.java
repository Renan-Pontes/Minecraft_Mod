package com.renanpontes.isekaimahou.feature.glyphs_classes_b;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.TriggerPath;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerMarks;
import com.renanpontes.isekaimahou.feature.class_shinigami.Release;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ZanpakutoItem;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SteelBallEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * As CONDIÇÕES dos 7 gatilhos do módulo (a conjuração em si é do {@link TriggerCaster}). Todos usam só APIs públicas das
 * classes: contadores ({@link MagicProgressEvent.Counter}), eventos de dano e de efeito.
 * <ul>
 *   <li>Encantador — Ao Golpear com Arma Encantada: golpe corpo a corpo com Imbuir ou Equipamento Encantado ativo (ou
 *   arma com encantamento de verdade); no máx. 1× a cada 3 s.</li>
 *   <li>Shinigami — Ao Cortar com a Lâmina Liberada: ao gritar a Shikai e, com a zanpakutō liberada (Shikai/Bankai),
 *   a cada 4 cortes.</li>
 *   <li>Quincy — Ao Absorver Reishi: ao ganhar Reishi guardado com 3 ou mais; cada disparo GASTA 3 de Reishi.</li>
 *   <li>Pistoleiro — Ao Acertar na Cabeça: tiro na cabeça (arma de mana ou forma de arma).</li>
 *   <li>Patrulheiro — Ao Marcar um Alvo: uma marca nova do caçador (sua ou da sua Águia) num alvo.</li>
 *   <li>Apostador — Ao Tirar Sorte Grande: rolagem de sorte Grande Sorte, Natural ou JACKPOT.</li>
 *   <li>Steel Ball — Ao Acertar a Bola de Aço: sua bola de aço (arremessada ou da forma) acerta uma criatura.</li>
 * </ul>
 */
public final class TriggerEvents {
    public static final int CUTS_PER_FIRE = 4;
    public static final int QUINCY_COST = 3;

    /** Cortes liberados seguidos por jogador (Shinigami). */
    private static final Map<UUID, Integer> CUTS = new HashMap<>();
    /** Último Reishi guardado visto por jogador (Quincy: só ganho dispara). */
    private static final Map<UUID, Integer> REISHI_SEEN = new HashMap<>();
    /** "dono|alvo" → última vez que a marca nova disparou (Patrulheiro: a mesma marca renovada não dispara de novo). */
    private static final Map<String, Long> MARKED = new HashMap<>();

    // =====================================================================================
    //  Golpes (Encantador, Shinigami, Steel Ball)
    // =====================================================================================
    public static void onDamage(LivingDamageEvent.Post e) {
        if (e.getNewDamage() <= 0) return;
        LivingEntity victim = e.getEntity();
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer || victim == sp) return;
        if (TriggerCaster.busy()) return;

        // Steel Ball: a bola acertou (arremessada ou da forma "Bola de Aço")
        if (src.getDirectEntity() instanceof SteelBallEntity) {
            if (TriggerCaster.hasSlot(sp, GcbIds.ON_BALL_HIT)) {
                TriggerCaster.fire(sp, GcbIds.ON_BALL_HIT, victim, 20);
            }
            return;
        }
        // Daqui para baixo: só golpe corpo a corpo de verdade (não magia)
        if (src.getDirectEntity() != sp || src.is(CoreRegistries.SPELL_DAMAGE)) return;

        if (TriggerCaster.hasSlot(sp, GcbIds.ON_ENCHANTED_STRIKE) && enchantedWeapon(sp)) {
            if (TriggerCaster.fire(sp, GcbIds.ON_ENCHANTED_STRIKE, victim, 60)) {
                Vec3 c = GcbFx.center(victim);
                ((ServerLevel) victim.level()).sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.3);
                GcbFx.sound((ServerLevel) victim.level(), c, SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, 1.8F);
            }
        }

        if (sp.getMainHandItem().getItem() instanceof ZanpakutoItem && Release.released(sp)
                && TriggerCaster.hasSlot(sp, GcbIds.ON_RELEASED_CUT)) {
            int cuts = CUTS.merge(sp.getUUID(), 1, Integer::sum);
            if (cuts >= CUTS_PER_FIRE) {
                CUTS.put(sp.getUUID(), 0);
                if (TriggerCaster.fire(sp, GcbIds.ON_RELEASED_CUT, victim, 10)) slashFx(sp, victim);
            }
        }
    }

    /** Imbuir / Equipamento Encantado (do Encantador, em você — mesmo posto por um aliado) ou arma encantada de verdade. */
    private static boolean enchantedWeapon(ServerPlayer sp) {
        if (sp.hasEffect(EnchRegistries.IMBUE) || sp.hasEffect(EnchRegistries.GEAR)) return true;
        ItemStack main = sp.getMainHandItem();
        return !main.isEmpty() && main.isEnchanted();
    }

    private static void slashFx(ServerPlayer sp, LivingEntity victim) {
        ServerLevel sl = sp.serverLevel();
        Vec3 c = GcbFx.center(victim);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(GcbFx.dust(GcbFx.SHINIGAMI, 1.0F), c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.0);
        GcbFx.sound(sl, c, SoundEvents.TRIDENT_RETURN, 0.7F, 1.4F);
    }

    // =====================================================================================
    //  Contadores (Shinigami: liberação · Quincy: Reishi · Pistoleiro: cabeça · Apostador: sorte grande)
    // =====================================================================================
    public static void onCounter(MagicProgressEvent.Counter e) {
        ServerPlayer sp = e.getPlayer();
        if (sp instanceof FakePlayer) return;
        String k = e.key;
        if (Quincy.REISHI.equals(k)) {
            Integer before = REISHI_SEEN.put(sp.getUUID(), e.value);
            boolean gained = before == null || e.value > before;
            if (gained && !TriggerCaster.busy() && e.value >= QUINCY_COST && TriggerCaster.hasSlot(sp, GcbIds.ON_REISHI)
                    && TriggerCaster.ready(sp, GcbIds.ON_REISHI, 20)) {
                // só em combate: sem inimigo por perto o Reishi não é gasto à toa
                LivingEntity foe = TriggerCaster.nearestEnemy(sp);
                if (foe == null) return;
                TriggerCaster.fire(sp, GcbIds.ON_REISHI, foe, 20,
                        p -> Quincy.reishi(MagicApi.get(p)) >= QUINCY_COST || p.getAbilities().instabuild,
                        TriggerEvents::spendReishi);
            }
            return;
        }
        if (TriggerCaster.busy()) return;
        if (Shinigami.RELEASES.equals(k)) {
            CUTS.put(sp.getUUID(), 0);
            if (TriggerCaster.hasSlot(sp, GcbIds.ON_RELEASED_CUT)) {
                // o grito da Shikai só dispara em combate (inimigo a até 20 blocos)
                LivingEntity foe = TriggerCaster.nearestEnemy(sp);
                if (foe != null && TriggerCaster.fire(sp, GcbIds.ON_RELEASED_CUT, foe, 0)) slashFx(sp, foe);
            }
        } else if (TriggerPath.HEADSHOTS.equals(k)) {
            if (TriggerCaster.hasSlot(sp, GcbIds.ON_HEADSHOT) && TriggerCaster.ready(sp, GcbIds.ON_HEADSHOT, 20)) {
                TriggerCaster.fire(sp, GcbIds.ON_HEADSHOT, TriggerCaster.aimed(sp, 48), 20);
            }
        } else if (LuckService.BIG_WIN.equals(k)) {
            if (!TriggerCaster.hasSlot(sp, GcbIds.ON_BIG_WIN) || !TriggerCaster.ready(sp, GcbIds.ON_BIG_WIN, 30)) return;
            // só em combate: uma cura fora de briga que tira Ótimo não dispara magia no vazio
            LivingEntity foe = TriggerCaster.nearestEnemy(sp);
            if (foe != null && TriggerCaster.fire(sp, GcbIds.ON_BIG_WIN, foe, 30)) {
                Vec3 at = sp.position().add(0, sp.getBbHeight() + 0.3, 0);
                sp.serverLevel().sendParticles(GcbFx.dust(GcbFx.GOLD, 1.0F), at.x, at.y, at.z, 10, 0.4, 0.2, 0.4, 0.0);
                GcbFx.sound(sp.serverLevel(), at, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.8F);
            }
        }
    }

    /** Um disparo do "Ao Absorver Reishi" saiu: gasta 3 de Reishi guardado (o fio de luz volta ao arco). */
    private static void spendReishi(ServerPlayer sp) {
        if (sp.getAbilities().instabuild) return;
        int have = Quincy.reishi(MagicApi.get(sp));
        int take = Math.min(have, QUINCY_COST);
        if (take > 0) MagicApi.addCounter(sp, Quincy.REISHI, -take);
        Vec3 at = sp.position().add(0, 1.1, 0);
        sp.serverLevel().sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 8, 0.3, 0.4, 0.3, 0.05);
        sp.displayClientMessage(Quincy.reishiLine(MagicApi.get(sp)), true);
    }

    // =====================================================================================
    //  Marca do Caçador (Patrulheiro): a marca acende o Brilho com o dono como fonte
    // =====================================================================================
    public static void onEffectAdded(MobEffectEvent.Added e) {
        MobEffectInstance inst = e.getEffectInstance();
        if (inst == null || !inst.is(MobEffects.GLOWING)) return;
        if (!(e.getEffectSource() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        LivingEntity target = e.getEntity();
        if (target == sp || TriggerCaster.busy() || !RangerMarks.isMarkedBy(target, sp.getUUID())) return;
        if (!TriggerCaster.hasSlot(sp, GcbIds.ON_MARK)) return;
        long now = sp.serverLevel().getGameTime();
        String key = sp.getUUID() + "|" + target.getUUID();
        Long last = MARKED.get(key);
        if (last != null && now - last < 100) return;
        MARKED.put(key, now);
        if (MARKED.size() > 256) {
            Iterator<Map.Entry<String, Long>> it = MARKED.entrySet().iterator();
            while (it.hasNext()) if (now - it.next().getValue() > 200) it.remove();
        }
        if (TriggerCaster.fire(sp, GcbIds.ON_MARK, target, 10)) {
            Vec3 c = target.position().add(0, target.getBbHeight() + 0.5, 0);
            GcbFx.ring(sp.serverLevel(), c, 0.5, 8, GcbFx.dust(GcbFx.RANGER, 0.8F));
            GcbFx.sound(sp.serverLevel(), c, SoundEvents.CROSSBOW_LOADING_END.value(), 0.6F, 1.5F);
        }
    }

    public static void forget(UUID id) {
        CUTS.remove(id);
        REISHI_SEEN.remove(id);
        String prefix = id + "|";
        MARKED.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void clear() {
        CUTS.clear();
        REISHI_SEEN.clear();
        MARKED.clear();
    }

    private TriggerEvents() {}
}
