package com.renanpontes.isekaimahou.feature.legends_k.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.legends_k.LkFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkNet;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.phys.EntityHitResult;

/**
 * A dimensão de bolso do Kamui (Naruto) e o "entre dimensões" do D4C (JoJo SBR) — o MESMO núcleo:
 * <ul>
 *   <li><b>Fase</b> (você entra): a tela fica azulada só para você; ninguém te acerta e você não acerta ninguém
 *       (golpes, projéteis, magias e efeitos de outros passam direto), os mobs perdem você de vista. Sai sozinho depois de
 *       2 minutos, agachando 2 vezes rápido (½ s) ou conjurando o glifo de novo. Um alvo PRESO (PvP) não sai agachando.</li>
 *   <li><b>Bolso</b> (você suga uma criatura fraca): ela some do mundo (vai para baixo do mundo, parada, invulnerável e
 *       invisível) e volta no mesmo ponto depois de alguns segundos. A marca fica salva na própria criatura: se o mundo
 *       fechar no meio, ela volta assim que carregar.</li>
 * </ul>
 * Tudo é estado do servidor em mapas simples; o cliente só recebe o tom azul ({@link LkNet}).
 */
public final class PocketDimension {
    /** Tempo máximo dentro da dimensão (palavra do dono: "sai forçado após 2 min"). */
    public static final int MAX_PHASE = 2400;
    /** Janela do "agachar 2 vezes" (ticks). */
    public static final int DOUBLE_SNEAK = 10;
    public static final String TAG = "isekai_mahou.lk_pocket";

    private record Phase(byte style, long end, boolean forced) {}

    private static final Map<UUID, Phase> PHASED = new HashMap<>();
    /** Última vez (tick) que o jogador em fase COMEÇOU a agachar, e se estava agachado no tick anterior. */
    private static final Map<UUID, Long> LAST_SNEAK = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_SNEAKING = new HashMap<>();

    private record Stash(ResourceKey<Level> level, long until, byte style) {}

    private static final Map<UUID, Stash> STASHED = new HashMap<>();

    // =====================================================================================
    //  Fase (você dentro da dimensão)
    // =====================================================================================

    public static boolean phased(@Nullable Entity e) {
        return e != null && !PHASED.isEmpty() && PHASED.containsKey(e.getUUID());
    }

    /** Entra (ou, se já estiver dentro e não estiver preso, sai). */
    public static void toggle(LivingEntity e, byte style) {
        Phase cur = PHASED.get(e.getUUID());
        if (cur != null) {
            if (!cur.forced()) exit(e, false);
            return;
        }
        enter(e, style, MAX_PHASE, false);
    }

    /** Entra na dimensão por {@code ticks}. {@code forced} = preso (não sai agachando nem reconjurando). */
    public static void enter(LivingEntity e, byte style, int ticks, boolean forced) {
        if (!(e.level() instanceof ServerLevel l)) return;
        int t = Math.min(MAX_PHASE, Math.max(20, ticks));
        PHASED.put(e.getUUID(), new Phase(style, l.getGameTime() + t, forced));
        WAS_SNEAKING.put(e.getUUID(), e.isShiftKeyDown());
        int rgb = color(style);
        LkFx.swirl(l, LkUtil.center(e), 1.4, rgb, 32);
        LkUtil.sound(l, e.position(), SoundEvents.ENDERMAN_TELEPORT, 0.7F, style == LkNet.KAMUI ? 0.5F : 1.4F);
        if (e instanceof Mob m) m.setTarget(null);
        if (e instanceof ServerPlayer sp) {
            LkNet.screen(sp, style, t);
            LkUtil.say(sp, forced ? "pocket_trapped" : "pocket_enter", ChatFormatting.AQUA);
        }
        // quem mirava em você te perde
        for (Mob m : l.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(24), m -> m.getTarget() == e)) {
            m.setTarget(null);
        }
    }

    public static void exit(LivingEntity e, boolean timeUp) {
        Phase p = PHASED.remove(e.getUUID());
        LAST_SNEAK.remove(e.getUUID());
        WAS_SNEAKING.remove(e.getUUID());
        if (p == null) return;
        if (e.level() instanceof ServerLevel l) {
            LkFx.swirl(l, LkUtil.center(e), 1.0, color(p.style()), 24);
            LkUtil.sound(l, e.position(), SoundEvents.ENDERMAN_TELEPORT, 0.6F, p.style() == LkNet.KAMUI ? 0.7F : 1.6F);
        }
        if (e instanceof ServerPlayer sp) {
            LkNet.screen(sp, p.style(), 0);
            LkUtil.say(sp, timeUp ? "pocket_forced_out" : "pocket_exit", ChatFormatting.GRAY);
        }
    }

    private static int color(byte style) {
        return style == LkNet.KAMUI ? LkFx.KAMUI : LkFx.D4C;
    }

    /** Agachar 2 vezes rápido sai da dimensão (só jogadores, só quem não está preso). */
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (PHASED.isEmpty() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        Phase p = PHASED.get(sp.getUUID());
        if (p == null) return;
        boolean now = sp.isShiftKeyDown();
        boolean was = WAS_SNEAKING.getOrDefault(sp.getUUID(), false);
        WAS_SNEAKING.put(sp.getUUID(), now);
        if (now && !was && !p.forced()) {
            long t = sp.serverLevel().getGameTime();
            Long last = LAST_SNEAK.get(sp.getUUID());
            if (last != null && t - last <= DOUBLE_SNEAK) {
                exit(sp, false);
                return;
            }
            LAST_SNEAK.put(sp.getUUID(), t);
        }
        // um fiapo de redemoinho para os outros saberem que você está "do outro lado" (leve: 1x por segundo)
        if (sp.tickCount % 20 == 0) LkFx.swirl(sp.serverLevel(), LkUtil.center(sp), 0.7, color(p.style()), 10);
    }

    // =====================================================================================
    //  Bolso (criatura sugada)
    // =====================================================================================

    public static boolean stashed(Entity e) {
        return e.getPersistentData().contains(TAG);
    }

    /** Suga a criatura para a dimensão por {@code ticks}. Falso se não deu (já está lá, mundo errado...). */
    public static boolean stash(LivingEntity t, int ticks, byte style) {
        if (!(t.level() instanceof ServerLevel l) || stashed(t) || t.isPassenger() || t.isVehicle()) return false;
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", t.getX());
        tag.putDouble("y", t.getY());
        tag.putDouble("z", t.getZ());
        tag.putBoolean("ai", t instanceof Mob m && m.isNoAi());
        tag.putBoolean("grav", t.isNoGravity());
        tag.putBoolean("inv", t.isInvulnerable());
        tag.putBoolean("invis", t.isInvisible());
        tag.putByte("style", style);
        t.getPersistentData().put(TAG, tag);
        LkFx.swirl(l, LkUtil.center(t), Math.max(1.0, t.getBbWidth() * 1.6), color(style), 36);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 16, 0.3, 0.5, 0.3, 0.05);
        LkUtil.sound(l, t.position(), SoundEvents.PORTAL_TRAVEL, 0.25F, 1.8F);
        if (t instanceof Mob m) {
            m.setTarget(null);
            m.getNavigation().stop();
            m.setNoAi(true);
        }
        t.setNoGravity(true);
        t.setInvulnerable(true);
        t.setInvisible(true);
        t.setDeltaMovement(Vec3.ZERO);
        t.teleportTo(t.getX(), l.getMinBuildHeight() - 6, t.getZ());
        STASHED.put(t.getUUID(), new Stash(l.dimension(), l.getGameTime() + Math.max(20, ticks), style));
        return true;
    }

    /** Devolve a criatura ao mundo, no ponto de onde saiu. */
    public static void release(Entity t) {
        STASHED.remove(t.getUUID());
        CompoundTag tag = t.getPersistentData().getCompound(TAG);
        if (tag.isEmpty()) return;
        t.getPersistentData().remove(TAG);
        t.teleportTo(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        if (t instanceof Mob m) m.setNoAi(tag.getBoolean("ai"));
        t.setNoGravity(tag.getBoolean("grav"));
        t.setInvulnerable(tag.getBoolean("inv"));
        t.setInvisible(tag.getBoolean("invis"));
        t.setDeltaMovement(0, 0.25, 0);
        t.fallDistance = 0;
        byte style = tag.getByte("style");
        if (t.level() instanceof ServerLevel l) {
            LkFx.swirl(l, LkUtil.center(t), Math.max(1.0, t.getBbWidth() * 1.6), color(style), 30);
            LkUtil.sound(l, t.position(), SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.6F);
        }
        if (t instanceof LivingEntity le) {
            // cuspido de volta: tonto por um instante (o D4C deixa um enjoo de "outro mundo")
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, style == LkNet.D4C ? 60 : 30, 1));
            if (style == LkNet.D4C) le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
        }
    }

    /** Lá embaixo ela fica longe de todo jogador: não pode sumir por distância enquanto está no bolso. */
    public static void onDespawn(MobDespawnEvent e) {
        if (!STASHED.isEmpty() && e.getEntity().getPersistentData().contains(TAG)) e.setResult(MobDespawnEvent.Result.DENY);
    }

    /** Criatura carregada com a marca mas fora do mapa (o mundo fechou com ela lá dentro): volta no tick seguinte. */
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity t = e.getEntity();
        if (!t.getPersistentData().contains(TAG) || STASHED.containsKey(t.getUUID())) return;
        SpellScheduler.schedule(1, () -> {
            if (!t.isRemoved() && !STASHED.containsKey(t.getUUID())) release(t);
        });
    }

    // =====================================================================================
    //  Tick e regras de "intangível"
    // =====================================================================================

    public static void tick(MinecraftServer server) {
        if (!PHASED.isEmpty()) {
            for (Map.Entry<UUID, Phase> en : new ArrayList<>(PHASED.entrySet())) {
                LivingEntity e = find(server, en.getKey());
                if (e == null || !e.isAlive()) {
                    PHASED.remove(en.getKey());
                    continue;
                }
                if (e.level().getGameTime() >= en.getValue().end()) exit(e, true);
            }
        }
        if (!STASHED.isEmpty()) {
            for (Map.Entry<UUID, Stash> en : new ArrayList<>(STASHED.entrySet())) {
                ServerLevel l = server.getLevel(en.getValue().level());
                if (l == null) {
                    STASHED.remove(en.getKey());
                    continue;
                }
                if (l.getGameTime() < en.getValue().until()) continue;
                Entity t = l.getEntity(en.getKey());
                if (t == null) {
                    // descarregou: a marca salva devolve quando carregar
                    STASHED.remove(en.getKey());
                    continue;
                }
                release(t);
            }
        }
    }

    @Nullable
    private static LivingEntity find(MinecraftServer server, UUID id) {
        ServerPlayer sp = server.getPlayerList().getPlayer(id);
        if (sp != null) return sp;
        for (ServerLevel l : server.getAllLevels()) {
            if (l.getEntity(id) instanceof LivingEntity le) return le;
        }
        return null;
    }

    /** Ninguém acerta quem está do outro lado — e quem está lá não acerta ninguém. */
    public static boolean onIncoming(LivingIncomingDamageEvent e) {
        if (PHASED.isEmpty() || LkUtil.unstoppable(e.getSource())) return false;
        if (phased(e.getEntity()) || phased(e.getSource().getEntity()) || phased(e.getSource().getDirectEntity())) {
            e.setCanceled(true);
            return true;
        }
        return false;
    }

    /** Projéteis atravessam quem está em fase. */
    public static void onProjectile(ProjectileImpactEvent e) {
        if (PHASED.isEmpty()) return;
        if (e.getRayTraceResult() instanceof EntityHitResult hit && phased(hit.getEntity())) e.setCanceled(true);
    }

    /** Mobs não miram em quem está em fase. */
    public static void onTarget(LivingChangeTargetEvent e) {
        if (!PHASED.isEmpty() && phased(e.getNewAboutToBeSetTarget())) e.setCanceled(true);
    }

    /** Efeitos vindos de OUTROS não chegam a quem está em fase. */
    public static void onEffect(MobEffectEvent.Applicable e) {
        if (PHASED.isEmpty() || !phased(e.getEntity())) return;
        Entity src = e.getEffectSource();
        if (src != null && src != e.getEntity()) e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    public static void forget(UUID id) {
        PHASED.remove(id);
        LAST_SNEAK.remove(id);
        WAS_SNEAKING.remove(id);
    }

    /** Servidor fechando: devolve todo mundo que está no bolso (a marca salva cobre o resto). */
    public static void releaseAll(MinecraftServer server) {
        for (Map.Entry<UUID, Stash> en : new ArrayList<>(STASHED.entrySet())) {
            ServerLevel l = server.getLevel(en.getValue().level());
            Entity t = l == null ? null : l.getEntity(en.getKey());
            if (t != null) release(t);
        }
        STASHED.clear();
        PHASED.clear();
        LAST_SNEAK.clear();
        WAS_SNEAKING.clear();
    }

    private PocketDimension() {}
}
