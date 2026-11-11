package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Proteções das técnicas novas do Cavaleiro (servidor):
 * <ul>
 *   <li><b>Avalon</b>: o alvo fica "longe do mundo" — todo golpe que não ignora invencibilidade é anulado; esfera de
 *   luz dourada girando em volta;</li>
 *   <li><b>Postura Anti-Magia</b>: dano de magia −75% e projéteis inimigos que chegam perto são cortados no ar; aura negra;</li>
 *   <li><b>Vento Invisível</b> (efeito {@code knight_invisible_air}): a lâmina some (o cliente esconde o item) e os golpes
 *   empurram com uma lufada.</li>
 * </ul>
 * Estado pequeno, limpo no fim de cada efeito, ao morrer/sair e ao parar o servidor.
 */
public final class KnightWards {
    public static final float ANTI_MAGIC_CUT = 0.25F;
    private static final int CAP = 128;

    private record Ward(LivingEntity e, long until) {}

    private static final Map<UUID, Ward> AVALON = new HashMap<>();
    private static final Map<UUID, Ward> ANTI_MAGIC = new HashMap<>();

    // =====================================================================================
    //  Aplicar
    // =====================================================================================
    public static void avalon(LivingEntity e, int ticks) {
        if (!(e.level() instanceof ServerLevel sl) || (AVALON.size() >= CAP && !AVALON.containsKey(e.getUUID()))) return;
        AVALON.put(e.getUUID(), new Ward(e, sl.getGameTime() + ticks));
        Vec3 c = KnightFx.center(e);
        KnightFx.pillar(sl, e.position(), 8, 0.9, 0xFFE9A8);
        KnightFx.runeCircle(sl, e.position(), 2.2, 0xFFE9A8, 40, 0F);
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 40, 0.5, 0.8, 0.5, 0.35);
        sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.8, 1.0, 0.8, 0.08);
        KnightFx.sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1.2F, 1.4F);
        KnightFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 1.6F);
        KnightFx.sound(sl, c, SoundEvents.BELL_RESONATE, 1.0F, 1.2F);
        if (e instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_knight.avalon", ticks / 20).withColor(0xFFE9A8), true);
        }
    }

    public static void antiMagic(LivingEntity e, int ticks) {
        if (!(e.level() instanceof ServerLevel sl) || (ANTI_MAGIC.size() >= CAP && !ANTI_MAGIC.containsKey(e.getUUID()))) return;
        ANTI_MAGIC.put(e.getUUID(), new Ward(e, sl.getGameTime() + ticks));
        // maldições saem na hora
        java.util.List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> curses = new java.util.ArrayList<>();
        for (MobEffectInstance inst : e.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) curses.add(inst.getEffect());
        }
        for (var h : curses) e.removeEffect(h);
        Vec3 c = KnightFx.center(e);
        sl.sendParticles(KnightFx.dust(0x1A1A22, 2.0F), c.x, c.y, c.z, 40, 0.6, 0.9, 0.6, 0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 20, 0.5, 0.8, 0.5, 0.03);
        KnightFx.sound(sl, c, SoundEvents.WITHER_SHOOT, 0.5F, 0.5F);
        KnightFx.sound(sl, c, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.0F, 0.7F);
        if (e instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_knight.anti_magic", ticks / 20).withColor(0x6FAA7A), true);
        }
    }

    /** Vento Invisível: a lâmina envolta em vento (efeito sincronizado também com quem está vendo). */
    public static void invisibleAir(LivingEntity e, int ticks) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        MobEffectInstance inst = new MobEffectInstance(KnightRegistries.INVISIBLE_AIR, ticks, 0, false, false, true);
        e.addEffect(inst, e);
        // o vanilla só manda os efeitos para o próprio jogador: quem está vendo precisa saber para esconder a lâmina
        MobEffectInstance now = e.getEffect(KnightRegistries.INVISIBLE_AIR);
        if (now != null) sl.getChunkSource().broadcast(e, new ClientboundUpdateMobEffectPacket(e.getId(), now, false));
    }

    /** Anti-Magia num inimigo: desfaz as proteções mágicas dele (Avalon e a própria Postura Anti-Magia). */
    public static void dispel(LivingEntity e) {
        boolean had = AVALON.remove(e.getUUID()) != null;
        had |= ANTI_MAGIC.remove(e.getUUID()) != null;
        if (had && e.level() instanceof ServerLevel sl) {
            KnightFx.sound(sl, e.position(), SoundEvents.GLASS_BREAK, 1.0F, 0.6F);
        }
    }

    public static boolean protectedByAvalon(LivingEntity e) {
        Ward w = AVALON.get(e.getUUID());
        return w != null && e.level().getGameTime() <= w.until();
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (AVALON.isEmpty() && ANTI_MAGIC.isEmpty()) return;
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel sl) || event.getAmount() <= 0F) return;
        DamageSource src = event.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Ward av = AVALON.get(e.getUUID());
        if (av != null && sl.getGameTime() <= av.until()) {
            event.setCanceled(true);
            if (e.tickCount % 4 == 0) {
                Vec3 c = KnightFx.center(e);
                sl.sendParticles(KnightFx.dust(0xFFE9A8, 1.2F), c.x, c.y, c.z, 14, 0.6, 0.8, 0.6, 0);
                KnightFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
            }
            return;
        }
        Ward am = ANTI_MAGIC.get(e.getUUID());
        if (am != null && sl.getGameTime() <= am.until() && src.is(CoreRegistries.SPELL_DAMAGE)) {
            event.setAmount(event.getAmount() * ANTI_MAGIC_CUT);
            Vec3 c = KnightFx.center(e);
            sl.sendParticles(KnightFx.dust(0x1A1A22, 1.6F), c.x, c.y, c.z, 12, 0.5, 0.6, 0.5, 0);
            KnightFx.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 0.6F, 0.8F);
        }
    }

    /** Golpe corpo a corpo com o Vento Invisível: uma lufada empurra o alvo. */
    public static void onMelee(LivingDamageEvent.Post event) {
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof LivingEntity attacker) || src.getDirectEntity() != attacker || !src.is(DamageTypes.PLAYER_ATTACK)) return;
        if (!attacker.hasEffect(KnightRegistries.INVISIBLE_AIR) || !(attacker.level() instanceof ServerLevel sl)) return;
        LivingEntity target = event.getEntity();
        Vec3 push = KnightFx.flat(target.position().subtract(attacker.position()), attacker.getLookAngle()).scale(0.6);
        target.push(push.x, 0.15, push.z);
        target.hurtMarked = true;
        Vec3 c = KnightFx.center(target);
        sl.sendParticles(ParticleTypes.SMALL_GUST, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0);
        KnightFx.sound(sl, c, SoundEvents.BREEZE_IDLE_GROUND, 0.6F, 1.6F);
    }

    // =====================================================================================
    //  Tique
    // =====================================================================================
    public static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (!AVALON.isEmpty()) {
            Iterator<Ward> it = AVALON.values().iterator();
            while (it.hasNext()) {
                Ward w = it.next();
                LivingEntity e = w.e();
                if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel sl) || sl.getGameTime() > w.until()) {
                    if (!e.isRemoved() && e.level() instanceof ServerLevel l) KnightFx.sound(l, e.position(), SoundEvents.BEACON_DEACTIVATE, 0.7F, 1.4F);
                    it.remove();
                    continue;
                }
                if (t % 2 != 0) continue;
                // esfera de luz da bainha girando
                Vec3 c = KnightFx.center(e);
                double r = Math.max(1.0, e.getBbHeight() * 0.7);
                for (int i = 0; i < 10; i++) {
                    float a = t * 0.12F + i * Mth.TWO_PI / 10;
                    float b = (float) Math.sin(t * 0.05 + i) * 1.2F;
                    double x = c.x + Mth.cos(a) * Mth.cos(b) * r, y = c.y + Mth.sin(b) * r, z = c.z + Mth.sin(a) * Mth.cos(b) * r;
                    sl.sendParticles(KnightFx.dust(i % 3 == 0 ? 0xFFFFFF : 0xFFE9A8, 0.9F), x, y, z, 1, 0, 0, 0, 0);
                }
                if (t % 6 == 0) sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + r, c.z, 2, 0.3, 0.1, 0.3, 0.01);
            }
        }
        if (!ANTI_MAGIC.isEmpty()) {
            Iterator<Ward> it = ANTI_MAGIC.values().iterator();
            while (it.hasNext()) {
                Ward w = it.next();
                LivingEntity e = w.e();
                if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel sl) || sl.getGameTime() > w.until()) {
                    it.remove();
                    continue;
                }
                cutProjectiles(sl, e);
                if (t % 3 != 0) continue;
                Vec3 c = KnightFx.center(e);
                for (int i = 0; i < 4; i++) {
                    float a = -t * 0.3F + i * Mth.HALF_PI;
                    sl.sendParticles(KnightFx.dust(i % 2 == 0 ? 0x1A1A22 : 0x3E7A4A, 1.4F), c.x + Mth.cos(a) * 0.7,
                            e.getY() + 0.2 + (t % 30) / 15.0, c.z + Mth.sin(a) * 0.7, 1, 0, 0.05, 0, 0);
                }
            }
        }
    }

    /** Projéteis inimigos a 2,5 blocos vindo na direção do alvo: cortados no ar (máx. 4 por tique). */
    private static void cutProjectiles(ServerLevel sl, LivingEntity e) {
        Vec3 c = KnightFx.center(e);
        int cut = 0;
        for (Projectile p : sl.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(2.5))) {
            if (cut >= 4) break;
            Entity owner = p.getOwner();
            if (owner == e || (owner != null && (owner.isAlliedTo(e)
                    || (owner instanceof Player && e instanceof Player && !sl.getServer().isPvpAllowed())))) continue;
            Vec3 v = p.getDeltaMovement();
            if (v.lengthSqr() < 1.0E-4 || v.dot(c.subtract(p.position())) <= 0) continue;
            Vec3 at = p.position();
            p.discard();
            cut++;
            KnightFx.crescent(sl, at, KnightFx.flat(v, new Vec3(0, 0, 1)), 0.7, 0x1A1A22, 5, 1.2F);
            sl.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.02);
            KnightFx.sound(sl, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.6F);
        }
    }

    public static void forget(UUID id) {
        AVALON.remove(id);
        ANTI_MAGIC.remove(id);
    }

    public static void clear() {
        AVALON.clear();
        ANTI_MAGIC.clear();
    }

    private KnightWards() {}
}
