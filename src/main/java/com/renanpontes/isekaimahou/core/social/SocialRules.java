package com.renanpontes.isekaimahou.core.social;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.boss.BossKit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Quem pode ferir quem (servidor): grupo (nunca), duelo (sempre, entre os dois), guerra de cidades (PvP liberado só
 * entre os lados inimigos, mesmo com o PvP do servidor desligado) e facções aliadas (o jogador não fere membros de
 * uma facção que ele escolheu como aliada — golpes, flechas, magias, mascotes — e eles ajudam nas brigas).
 */
public final class SocialRules {
    /** Raio em que membros de facção aliada vêm ajudar. */
    public static final double HELP_RADIUS = 16.0;
    private static final int HELP_COOLDOWN = 20;
    private static final int HINT_COOLDOWN = 40;

    private static final Map<UUID, Integer> LAST_HELP = new HashMap<>();
    private static final Map<UUID, Integer> LAST_HINT = new HashMap<>();

    // =====================================================================================
    //  Decisões
    // =====================================================================================
    /** O jogador por trás de uma entidade: ela mesma, o dono (mascote/invocação) ou quem atirou (projétil). */
    @Nullable
    public static ServerPlayer playerBehind(@Nullable Entity e) {
        for (int i = 0; i < 4 && e != null; i++) {
            if (e instanceof ServerPlayer sp) return sp instanceof FakePlayer ? null : sp;
            Entity next = null;
            if (e instanceof Projectile proj) next = proj.getOwner();
            else if (e instanceof OwnableEntity own) next = own.getOwner();
            else if (e instanceof TraceableEntity tr) next = tr.getOwner();
            e = next;
        }
        return null;
    }

    /**
     * PvP social: TRUE = pode ferir mesmo com PvP desligado (duelo/guerra); FALSE = nunca (mesmo grupo, contagem do
     * duelo); null = regras normais do servidor.
     */
    @Nullable
    public static Boolean canHarm(Player victim, Player attacker) {
        if (victim == attacker) return null;
        MinecraftServer server = victim.getServer();
        if (server == null) return null;
        UUID v = victim.getUUID(), a = attacker.getUUID();
        if (DuelService.dueling(v, a)) return Boolean.TRUE;
        if (DuelService.countingDown(v, a)) return Boolean.FALSE;
        if (PartyService.sameParty(server, v, a)) return Boolean.FALSE;
        if (AllegianceService.atWar(server, v, a)) return Boolean.TRUE;
        return null;
    }

    /** Regra de aliado para magias ({@code SpellContext.isAlly}): TRUE aliado, FALSE inimigo, null sem opinião. */
    @Nullable
    public static Boolean allyOverride(@Nullable Entity source, @Nullable Entity target) {
        if (source == null || target == null || source.level().isClientSide()) return null;
        ServerPlayer caster = playerBehind(source);
        if (caster == null) return null;
        ServerPlayer targetPlayer = target instanceof Projectile ? null : playerBehind(target);
        if (targetPlayer != null) {
            if (targetPlayer == caster) return null;
            Boolean harm = canHarm(targetPlayer, caster);
            return harm == null ? null : !harm;
        }
        return protectedAllyMob(caster, target) ? Boolean.TRUE : null;
    }

    /**
     * Membro de facção que o jogador escolheu como aliada e que NÃO está brigando com ele (se o membro mirar o jogador,
     * a proteção cai). Chefes nunca.
     */
    public static boolean protectedAllyMob(ServerPlayer player, @Nullable Entity target) {
        if (!(target instanceof LivingEntity le) || target instanceof Player || realBoss(le)) return false;
        String fid = Reputation.factionOf(target);
        if (fid == null || fid.isEmpty() || !Reputation.isAllied(player, fid)) return false;
        if (target instanceof Mob mob && (mob.getTarget() == player || mob.getLastHurtByMob() == player && mob.getTarget() != null)) return false;
        return true;
    }

    /**
     * Chefe "de verdade" (tag {@code c:bosses}, chefe de provação ou de arena). NÃO usa o limiar de vida do
     * {@code BossKit.isBossLike}: com a vida dos mobs do mod ×2 (v1.0), guardas e cavaleiros de facção passariam de 150 e
     * perderiam a proteção de aliado.
     */
    static boolean realBoss(LivingEntity e) {
        return e.getType().is(BossKit.BOSSES_TAG) || BossKit.isTrialBoss(e) || BossArenaService.isArenaBoss(e);
    }

    /** Ferir esta entidade é bloqueado pelas regras sociais? (jogador do grupo, mascote de alguém do grupo, aliado) */
    static boolean blocked(ServerPlayer attacker, Entity victim) {
        if (victim instanceof Player vp) {
            return canHarm(vp, attacker) == Boolean.FALSE;
        }
        ServerPlayer owner = victim instanceof OwnableEntity ? playerBehind(victim) : null;
        if (owner != null && owner != attacker) return canHarm(owner, attacker) == Boolean.FALSE;
        return protectedAllyMob(attacker, victim);
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    /** Qualquer dano (golpe, flecha, magia, mascote) de um jogador contra quem as regras protegem: cancelado. */
    static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide()) return;
        ServerPlayer attacker = playerBehind(e.getSource().getEntity());
        if (attacker == null || attacker == victim) return;
        if (blocked(attacker, victim)) e.setCanceled(true);
    }

    /** Golpe direto: cancela antes (sem empurrão nem desgaste da arma) e explica na barra. */
    static void onAttack(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer attacker) || attacker instanceof FakePlayer) return;
        Entity target = e.getTarget();
        if (!blocked(attacker, target)) return;
        e.setCanceled(true);
        hint(attacker, target);
    }

    /** Flechas passam por quem as regras protegem. */
    static void onProjectile(ProjectileImpactEvent e) {
        if (!(e.getProjectile() instanceof AbstractArrow arrow) || arrow.level().isClientSide()) return;
        if (!(e.getRayTraceResult() instanceof EntityHitResult hit)) return;
        ServerPlayer shooter = playerBehind(arrow);
        if (shooter == null || hit.getEntity() == shooter) return;
        if (blocked(shooter, hit.getEntity())) e.setCanceled(true);
    }

    /** Membros de facção aliada não escolhem o jogador como alvo (a não ser que ele os tenha atacado há pouco). */
    static void onChangeTarget(LivingChangeTargetEvent e) {
        if (!(e.getNewAboutToBeSetTarget() instanceof ServerPlayer player)) return;
        LivingEntity mob = e.getEntity();
        if (mob.level().isClientSide() || realBoss(mob)) return;
        String fid = Reputation.factionOf(mob);
        if (fid == null || fid.isEmpty() || !Reputation.isAllied(player, fid)) return;
        if (mob.getLastHurtByMob() == player || Reputation.recentlyAttacked(player, fid)) return;
        e.setNewAboutToBeSetTarget(null);
    }

    /**
     * Aliados ajudam: quando o jogador é ferido por alguém, ou fere um monstro, membros das facções aliadas por perto
     * (que sabem lutar e estão sem alvo) partem para cima do agressor/monstro.
     */
    static void onDamagePost(LivingDamageEvent.Post e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide()) return;
        Entity src = e.getSource().getEntity();
        if (victim instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            if (src instanceof LivingEntity attacker && attacker != player && attacker.isAlive()) {
                ServerPlayer behind = playerBehind(attacker);
                // Jogador agressor: só se a briga for "legal" (guerra/duelo não chamam aliados; PvP normal sim)
                if (behind != null && (canHarm(player, behind) != null || behind == player)) return;
                if (behind == null && protectedAllyMob(player, attacker)) return;
                callHelp(player, attacker);
            }
        } else if (src instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && (victim instanceof Enemy) && !protectedAllyMob(player, victim) && victim.isAlive()) {
            callHelp(player, victim);
        }
    }

    private static void callHelp(ServerPlayer player, LivingEntity enemy) {
        if (Reputation.allied(player).isEmpty()) return;
        int now = player.server.getTickCount();
        Integer last = LAST_HELP.get(player.getUUID());
        if (last != null && now - last < HELP_COOLDOWN) return;
        LAST_HELP.put(player.getUUID(), now);
        List<PathfinderMob> helpers = player.serverLevel().getEntitiesOfClass(PathfinderMob.class,
                player.getBoundingBox().inflate(HELP_RADIUS), m -> m.isAlive() && m != enemy
                        && (m.getTarget() == null || !m.getTarget().isAlive())
                        && m.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                        && protectedAllyMob(player, m)
                        && !sameFaction(m, enemy));
        int n = 0;
        for (PathfinderMob m : helpers) {
            if (n++ >= 4) break;
            m.setTarget(enemy);
        }
    }

    private static boolean sameFaction(Entity a, Entity b) {
        String fa = Reputation.factionOf(a);
        return fa != null && !fa.isEmpty() && fa.equals(Reputation.factionOf(b));
    }

    private static void hint(ServerPlayer player, Entity target) {
        int now = player.server.getTickCount();
        Integer last = LAST_HINT.get(player.getUUID());
        if (last != null && now - last < HINT_COOLDOWN) return;
        LAST_HINT.put(player.getUUID(), now);
        Component msg;
        if (target instanceof Player || target instanceof OwnableEntity) {
            msg = Component.translatable("social.isekai_mahou.hint.party_member", target.getDisplayName());
        } else {
            Reputation.Faction f = Reputation.faction(Reputation.factionOf(target));
            msg = Component.translatable("social.isekai_mahou.hint.ally", target.getDisplayName(),
                    f != null ? f.coloredName() : Component.literal("?"));
        }
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.GREEN), true);
    }

    static void forget(UUID player) {
        LAST_HELP.remove(player);
        LAST_HINT.remove(player);
    }

    static void clear() {
        LAST_HELP.clear();
        LAST_HINT.clear();
    }

    private SocialRules() {}
}
