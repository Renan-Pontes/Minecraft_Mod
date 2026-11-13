package com.renanpontes.isekaimahou.core.anim;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.core.boss.BossKit;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

/**
 * "Sensação" do corpo do jogador, detectada no servidor e mostrada a todos (o próprio jogador incluso):
 * <ul>
 *   <li><b>Tranco</b> ao levar um golpe pesado (pela frente ou pelas costas);</li>
 *   <li><b>Queda e levantar</b> num empurrão enorme ou explosão forte;</li>
 *   <li><b>Rolamento</b> (correndo) ou <b>aterrissagem de herói</b> depois de uma queda grande;</li>
 *   <li><b>Pose de vitória</b> para quem matou um chefe e quem estava perto;</li>
 *   <li><b>Arremesso</b> ao soltar um item de lançar (tridente, bola de aço).</li>
 * </ul>
 * O dano chega antes do empurrão no mesmo golpe, então as animações ficam PENDENTES e saem no fim do tick do jogador,
 * a mais importante vence (queda &gt; aterrissagem &gt; vitória &gt; tranco). Barato: só mapas por UUID, nada de busca
 * por entidades fora da morte de chefe (rara).
 */
final class FeelService {
    private static final int PRIO_FLINCH = 1, PRIO_VICTORY = 2, PRIO_LAND = 3, PRIO_KNOCKDOWN = 4;
    private static final int FLINCH_GAP = 12, KNOCKDOWN_GAP = 60;
    private static final double VICTORY_RADIUS = 32;

    private record Pending(String anim, int prio, long due) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_FLINCH = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_KNOCKDOWN = new ConcurrentHashMap<>();

    static void init(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, false, LivingDamageEvent.Post.class, FeelService::onDamage);
        bus.addListener(EventPriority.LOWEST, false, LivingKnockBackEvent.class, FeelService::onKnockback);
        bus.addListener(EventPriority.LOWEST, false, LivingFallEvent.class, FeelService::onFall);
        bus.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, FeelService::onDeath);
        bus.addListener(LivingEntityUseItemEvent.Stop.class, FeelService::onUseStop);
    }

    /** Agenda {@code anim}; só troca o que já estava pendente se for de prioridade igual ou maior. */
    private static void schedule(ServerPlayer p, String anim, int prio, int delay) {
        long due = p.serverLevel().getGameTime() + delay;
        PENDING.merge(p.getUUID(), new Pending(anim, prio, due), (old, neu) -> neu.prio >= old.prio ? neu : old);
    }

    /** Fim do tick do jogador: solta a animação pendente que venceu. */
    static void tick(ServerPlayer p) {
        Pending pending = PENDING.get(p.getUUID());
        if (pending == null) return;
        long now = p.serverLevel().getGameTime();
        if (now < pending.due) return;
        PENDING.remove(p.getUUID());
        if (p.isDeadOrDying() || p.isSpectator() || now - pending.due > 40) return;
        MahouAnims.play(p, pending.anim);
    }

    private static boolean enabled() {
        return AnimConfig.FEEL_ENABLED.get();
    }

    /** Estados em que o corpo não pode ser jogado no chão nem rolar (nadando, planando, montado, voando no criativo). */
    private static boolean busyBody(ServerPlayer p) {
        return p.isSpectator() || p.isPassenger() || p.isFallFlying() || p.isSwimming() || p.isInWater() || p.isSleeping()
                || p.getAbilities().flying || p.isDeadOrDying();
    }

    // =====================================================================================
    //  Tranco / queda
    // =====================================================================================
    private static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !enabled()) return;
        float dmg = event.getNewDamage();
        DamageSource src = event.getSource();
        // Queda tem a própria animação (aterrissagem); dano contínuo (fogo, afogar) quase nunca passa do mínimo
        if (dmg <= 0F || p.isDeadOrDying() || src.is(DamageTypeTags.IS_FALL) || src.is(DamageTypeTags.IS_DROWNING)) return;
        long now = p.serverLevel().getGameTime();
        // Explosão forte joga no chão (o empurrão da explosão não passa pelo LivingKnockBackEvent)
        if (src.is(DamageTypeTags.IS_EXPLOSION) && dmg >= 8F && !busyBody(p)) {
            knockdown(p, now);
            return;
        }
        if (dmg < AnimConfig.FLINCH_MIN_DAMAGE.get() || DodgeService.isDodging(p) || MahouAnims.isHolding(p) || p.isBlocking()
                || GuardService.isGuarding(p)) {
            return; // v1.0: quem está bloqueando com a arma segura o golpe sem tranco
        }
        Long last = LAST_FLINCH.get(p.getUUID());
        if (last != null && now - last < FLINCH_GAP) return;
        Long down = LAST_KNOCKDOWN.get(p.getUUID());
        if (down != null && now - down < 44) return; // já está no chão: o tranco não interrompe o levantar
        LAST_FLINCH.put(p.getUUID(), now);
        schedule(p, fromBehind(p, src) ? MahouAnims.FLINCH_B : MahouAnims.FLINCH_F, PRIO_FLINCH, 0);
    }

    /** O golpe veio de trás do jogador? (sem posição = de frente) */
    private static boolean fromBehind(ServerPlayer p, DamageSource src) {
        Vec3 from = src.getSourcePosition();
        if (from == null) return false;
        Vec3 look = p.getLookAngle();
        double dx = from.x - p.getX(), dz = from.z - p.getZ();
        return dx * look.x + dz * look.z < 0;
    }

    private static void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !enabled()) return;
        if (event.getStrength() < AnimConfig.KNOCKDOWN_MIN_STRENGTH.get() || busyBody(p) || DodgeService.isDodging(p)) return;
        knockdown(p, p.serverLevel().getGameTime());
    }

    private static void knockdown(ServerPlayer p, long now) {
        Long last = LAST_KNOCKDOWN.get(p.getUUID());
        if (last != null && now - last < KNOCKDOWN_GAP) return;
        LAST_KNOCKDOWN.put(p.getUUID(), now);
        schedule(p, MahouAnims.KNOCKDOWN, PRIO_KNOCKDOWN, 0);
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 0.7F);
    }

    // =====================================================================================
    //  Quedas grandes
    // =====================================================================================
    private static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !enabled()) return;
        float dist = event.getDistance();
        if (dist < 5F || busyBody(p) || p.isShiftKeyDown()) return;
        // Correndo = rolamento para a frente (o impulso continua); parado ou queda enorme = aterrissagem de herói
        if (p.isSprinting() && dist < 14F) schedule(p, MahouAnims.LAND_ROLL, PRIO_LAND, 0);
        else if (dist >= 8F) schedule(p, MahouAnims.LAND_HERO, PRIO_LAND, 0);
    }

    // =====================================================================================
    //  Vitória sobre um chefe
    // =====================================================================================
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!enabled() || dead instanceof Player || !(dead.level() instanceof ServerLevel level) || !BossKit.isBossLike(dead)) return;
        double r2 = VICTORY_RADIUS * VICTORY_RADIUS;
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        for (ServerPlayer p : level.players()) {
            if (p != killer && p.distanceToSqr(dead) > r2) continue;
            if (p.isDeadOrDying() || p.isSpectator()) continue;
            // Espera o chefe cair (≈1 s) antes de comemorar
            schedule(p, MahouAnims.VICTORY, PRIO_VICTORY, 25);
        }
    }

    // =====================================================================================
    //  Arremesso (tridente, bola de aço)
    // =====================================================================================
    private static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        ItemStack stack = event.getItem();
        if (stack.isEmpty() || stack.getUseAnimation() != UseAnim.SPEAR) return;
        int used = stack.getUseDuration(p) - event.getDuration();
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int min = path.contains("ball") ? 2 : 10; // tridente só é lançado com 10 ticks de carga
        if (used >= min) MahouAnims.play(p, MahouAnims.THROW_OVERHAND);
    }

    static void forget(UUID id) {
        PENDING.remove(id);
        LAST_FLINCH.remove(id);
        LAST_KNOCKDOWN.remove(id);
    }

    static void clear() {
        PENDING.clear();
        LAST_FLINCH.clear();
        LAST_KNOCKDOWN.clear();
    }

    private FeelService() {}
}
