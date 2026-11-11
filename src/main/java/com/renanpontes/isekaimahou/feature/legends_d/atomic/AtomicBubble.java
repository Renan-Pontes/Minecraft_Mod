package com.renanpontes.isekaimahou.feature.legends_d.atomic;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicHover;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.feature.legends_d.LdNet;
import com.renanpontes.isekaimahou.feature.legends_d.LdRegistry;
import com.renanpontes.isekaimahou.feature.legends_d.LdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * BOLHA PROTETORA do "Eu Sou Atômico" — o cuidado para um amigo jogar junto.
 *
 * <p>Quem fica na bolha ({@link #protects}): o conjurador, jogadores aliados (mesmo time; com o PvP desligado, todos os
 * jogadores), companheiros contratados da Guilda, invocações, servos e animais domados do conjurador ou de um jogador
 * aliado, e a montaria de um aliado. Quem está na bolha:
 * <ul>
 *   <li><b>não leva dano</b> da explosão, do conjurador, de outros jogadores, do ambiente (fogo da cratera, sufocar,
 *       queda) — só o vazio/{@code /kill} continuam valendo; monstros de verdade ainda conseguem bater;</li>
 *   <li><b>não é empurrado</b> (efeito com resistência total a empurrão + evento cancelado);</li>
 *   <li><b>sem gravidade</b>: se a cratera engolir o chão, fica flutuando até {@link #FLOAT} ticks depois da explosão e
 *       então desce devagar (queda lenta até pousar). Tem chão firme embaixo depois da explosão (e a cratera já
 *       terminou de ser cavada): a bolha estoura.</li>
 *   <li>a proteção contra queda/fogo do chão continua até pousar E a fila de destruição esvaziar (a cratera grande é
 *       cavada aos poucos: o chão de um amigo na borda pode sumir depois que a bolha estourou).</li>
 * </ul>
 * Estado só na memória (limpo ao parar o servidor); no máximo {@link #MAX} bolhas ao mesmo tempo.
 */
public final class AtomicBubble {
    /** Flutuação depois do fim da explosão (3 s), se o chão sumiu. */
    public static final int FLOAT = 60;
    /** Descida lenta: no máximo este tempo depois da flutuação (pousou antes: acabou). */
    private static final int DESCENT = 400;
    /** Depois de estourar, só solta quem está no chão há pelo menos isto (e a cratera terminou). */
    private static final int LAND_GRACE = 20;
    private static final int MAX = 64;
    public static final int COLOR = 0x5AB8FF;
    /** Marca de dono dos servos do Necromante (módulo ritual_necro, por texto). */
    private static final String NECRO_OWNER_TAG = "isekai_mahou.necro_owner";

    private static final class Bubble {
        final LivingEntity entity;
        final UUID caster;
        long shieldUntil;
        long floatUntil;
        long descentUntil;
        long lastHitFx;
        long popTick;
        /** A bolha já estourou (pousou ou acabou a flutuação): só a proteção de queda continua. */
        boolean popped;

        Bubble(LivingEntity entity, UUID caster) {
            this.entity = entity;
            this.caster = caster;
        }
    }

    private static final Map<UUID, Bubble> BUBBLES = new HashMap<>();

    // =====================================================================================
    //  Quem é protegido
    // =====================================================================================

    /** {@code e} fica na bolha do Atômico de {@code ctx}? */
    public static boolean protects(SpellContext ctx, @Nullable LivingEntity caster, LivingEntity e) {
        if (!e.isAlive() || e instanceof ArmorStand) return false;
        if (e == caster || (ctx.casterId() != null && e.getUUID().equals(ctx.casterId()))) return true;
        if (e instanceof Player p) return !p.isSpectator() && ctx.isAlly(p);
        if (ctx.isAlly(e)) return true;
        // Bichos/companheiros/invocações de um jogador aliado (ou do próprio conjurador)
        UUID owner = ownerOf(e);
        if (owner != null && allyOwner(ctx, owner)) return true;
        // A montaria de quem está protegido
        for (Entity rider : e.getPassengers()) {
            if (rider instanceof Player p && !p.isSpectator() && ctx.isAlly(p)) return true;
        }
        return false;
    }

    /** Dono de uma criatura domada/invocada/contratada (null se não tiver). */
    @Nullable
    public static UUID ownerOf(Entity e) {
        if (e instanceof OwnableEntity own) {
            UUID u = own.getOwnerUUID();
            if (u != null) return u;
        }
        if (e instanceof LivingEntity) {
            CompoundTag d = e.getPersistentData();
            if (d.hasUUID(NECRO_OWNER_TAG)) return d.getUUID(NECRO_OWNER_TAG);
        }
        return null;
    }

    private static boolean allyOwner(SpellContext ctx, UUID owner) {
        if (owner.equals(ctx.casterId())) return true;
        ServerLevel sl = ctx.level();
        MinecraftServer server = sl.getServer();
        Player online = server.getPlayerList().getPlayer(owner);
        if (online != null) return !online.isSpectator() && ctx.isAlly(online);
        Entity o = sl.getEntity(owner);
        if (o != null) return ctx.isAlly(o);
        // Dono offline: é um jogador conhecido e o PvP está desligado → aliado
        LivingEntity caster = ctx.caster();
        if (!(caster instanceof Player) || server.isPvpAllowed()) return false;
        try {
            return server.getProfileCache() != null && server.getProfileCache().get(owner).isPresent();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Raio visual da bolha para uma entidade. */
    public static float radiusFor(Entity e) {
        return Math.max(e.getBbWidth(), e.getBbHeight()) * 0.62F + 0.55F;
    }

    // =====================================================================================
    //  Bolha
    // =====================================================================================

    /** Já está numa bolha ativa (antes de estourar)? */
    public static boolean inBubble(Entity e) {
        Bubble b = BUBBLES.get(e.getUUID());
        return b != null && !b.popped && b.entity == e;
    }

    /**
     * Põe (ou estende) a bolha em {@code e} até {@code shieldUntil} (tempo de jogo), mais {@link #FLOAT} de flutuação.
     * @return false se o limite de bolhas foi atingido
     */
    public static boolean shield(ServerLevel sl, LivingEntity e, UUID caster, long shieldUntil) {
        long now = sl.getGameTime();
        Bubble b = BUBBLES.get(e.getUUID());
        boolean fresh = b == null || b.entity != e || b.popped;
        if (b == null || b.entity != e) {
            if (BUBBLES.size() >= MAX) return false;
            b = new Bubble(e, caster);
            BUBBLES.put(e.getUUID(), b);
        }
        b.shieldUntil = Math.max(b.shieldUntil, shieldUntil);
        b.floatUntil = b.shieldUntil + FLOAT;
        b.descentUntil = b.floatUntil + DESCENT;
        b.popped = false;
        int dur = (int) Math.max(20, b.floatUntil - now);
        e.addEffect(new MobEffectInstance(LdRegistry.ATOMIC_BUBBLE, dur, 0, false, false, true));
        // Para onde está: nada de velocidade que a jogue longe quando o chão sumir
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * 0.3, Math.max(0, v.y) * 0.2, v.z * 0.3);
        e.hurtMarked = true;
        e.fallDistance = 0F;
        if (e instanceof Player p) MagicHover.allow(p, dur + DESCENT);
        Vec3 c = LdUtil.center(e);
        LdNet.near(sl, c, 160, new LdNet.Fx(LdNet.ATOMIC_BUBBLE, c, radiusFor(e), e.getId(), dur));
        if (fresh) {
            LdUtil.sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.6F);
            LdUtil.sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 1.3F);
            LdUtil.send(sl, LdUtil.dust(COLOR, 1.3F), c, 16, radiusFor(e) * 0.7, 0);
            LdUtil.send(sl, ParticleTypes.END_ROD, c, 6, radiusFor(e) * 0.6, 0.02);
        }
        return true;
    }

    public static void tick(ServerTickEvent.Post ev) {
        if (BUBBLES.isEmpty()) return;
        Iterator<Bubble> it = BUBBLES.values().iterator();
        while (it.hasNext()) {
            Bubble b = it.next();
            LivingEntity e = b.entity;
            if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel sl)) {
                it.remove();
                continue;
            }
            long now = sl.getGameTime();
            e.fallDistance = 0F;
            if (!b.popped && now <= b.shieldUntil) {
                // Leite/limpeza tiraram o efeito? A bolha volta (a explosão ainda está acontecendo)
                if (!e.hasEffect(LdRegistry.ATOMIC_BUBBLE)) {
                    e.addEffect(new MobEffectInstance(LdRegistry.ATOMIC_BUBBLE, (int) Math.max(20, b.floatUntil - now), 0, false, false, true));
                }
                if ((now + e.getId()) % 8 == 0) {
                    Vec3 c = LdUtil.center(e);
                    LdUtil.send(sl, LdUtil.dust(COLOR, 0.9F), c, 3, radiusFor(e) * 0.8, 0);
                }
                continue;
            }
            boolean craterDone = Destruction.queued() == 0;
            if (!b.popped && now <= b.floatUntil) {
                // Sem gravidade o onGround fica falso: olha se há chão firme logo abaixo (e se a cratera já acabou)
                if ((supported(e) && craterDone) || e.isInWater()) pop(sl, b, now);
                continue;
            }
            if (!b.popped) pop(sl, b, now);
            boolean grounded = e.onGround() || e.isInWater() || e.isInLava() || supported(e);
            // Pousou (e o chão não vai mais sumir) ou passou o tempo máximo: acabou a proteção
            if (now > b.descentUntil || (grounded && craterDone && now - b.popTick >= LAND_GRACE)) {
                it.remove();
                continue;
            }
            if (grounded) continue;
            // Descida lenta até pousar
            MobEffectInstance sf = e.getEffect(MobEffects.SLOW_FALLING);
            if (sf == null || sf.getDuration() < 5) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, true));
            }
            if (e instanceof Player p) MagicHover.allow(p, 60);
        }
    }

    /** Há chão (bloco sólido) logo abaixo dos pés? */
    private static boolean supported(LivingEntity e) {
        return !e.level().noCollision(e, e.getBoundingBox().move(0, -0.15, 0).deflate(0.05, 0, 0.05));
    }

    private static void pop(ServerLevel sl, Bubble b, long now) {
        b.popped = true;
        b.popTick = now;
        LivingEntity e = b.entity;
        e.removeEffect(LdRegistry.ATOMIC_BUBBLE);
        e.clearFire();
        Vec3 c = LdUtil.center(e);
        LdNet.near(sl, c, 160, new LdNet.Fx(LdNet.ATOMIC_BUBBLE_END, c, radiusFor(e), e.getId(), 0));
        LdUtil.sound(sl, c, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.6F, 0.7F);
        LdUtil.sound(sl, c, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8F, 1.6F);
        LdUtil.send(sl, LdUtil.dust(COLOR, 1.1F), c, 14, radiusFor(e) * 0.8, 0);
        LdUtil.send(sl, ParticleTypes.END_ROD, c, 4, radiusFor(e) * 0.6, 0.05);
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    /** Dentro da bolha: nada de dano (menos monstros de verdade e o vazio). Depois dela: nada de dano de queda. */
    public static void onIncomingDamage(LivingIncomingDamageEvent ev) {
        if (BUBBLES.isEmpty()) return;
        LivingEntity v = ev.getEntity();
        if (v.level().isClientSide) return;
        Bubble b = BUBBLES.get(v.getUUID());
        if (b == null || b.entity != v) return;
        DamageSource s = ev.getSource();
        if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity src = s.getEntity();
        long now = v.level().getGameTime();
        if (!b.popped && now <= b.floatUntil) {
            if (src == null || !realEnemy(src, b.caster)) {
                ev.setCanceled(true);
                if (now - b.lastHitFx >= 6 && v.level() instanceof ServerLevel sl) {
                    b.lastHitFx = now;
                    Vec3 c = LdUtil.center(v);
                    LdUtil.send(sl, LdUtil.fade(0xFFFFFF, COLOR, 1.4F), c, 10, radiusFor(v) * 0.75, 0);
                    LdUtil.sound(sl, c, SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.4F);
                }
            }
            return;
        }
        if (s.is(DamageTypeTags.IS_FALL) || (src == null && s.is(DamageTypeTags.IS_FIRE))) ev.setCanceled(true);
    }

    /** Monstros de verdade (não do conjurador, não de jogador, não jogadores) ainda batem em quem está na bolha. */
    private static boolean realEnemy(Entity src, UUID caster) {
        if (src.getUUID().equals(caster) || src instanceof Player) return false;
        if (ownerOf(src) != null) return false;
        return src instanceof LivingEntity;
    }

    /**
     * Saiu do jogo (ou o chunk descarregou) dentro da bolha: o efeito volta salvo, mas o registro em memória não.
     * Retoma como bolha já flutuando (o tempo que sobrou do efeito) e depois a descida lenta — nunca cai na cratera.
     */
    public static void onJoin(EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !(ev.getEntity() instanceof LivingEntity e)) return;
        MobEffectInstance inst = e.getEffect(LdRegistry.ATOMIC_BUBBLE);
        if (inst == null) return;
        Bubble old = BUBBLES.get(e.getUUID());
        if (old != null && old.entity == e) return;
        if (old == null && BUBBLES.size() >= MAX) return;
        long now = ev.getLevel().getGameTime();
        Bubble b = new Bubble(e, e.getUUID());
        b.shieldUntil = now;
        b.floatUntil = now + Math.max(1, inst.getDuration());
        b.descentUntil = b.floatUntil + DESCENT;
        BUBBLES.put(e.getUUID(), b);
        e.fallDistance = 0F;
        if (e instanceof Player p) MagicHover.allow(p, (int) (b.descentUntil - now));
    }

    public static void onKnockBack(LivingKnockBackEvent ev) {
        if (BUBBLES.isEmpty()) return;
        LivingEntity v = ev.getEntity();
        if (!v.level().isClientSide && inBubble(v)) ev.setCanceled(true);
    }

    public static void clear() {
        BUBBLES.clear();
    }

    private AtomicBubble() {}
}
