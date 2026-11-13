package com.renanpontes.isekaimahou.feature.combat_feel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * <b>Golpe de misericórdia</b> — "inimigo comum com &lt;10% de vida pode ser finalizado (tecla de interação perto) com
 * animação curta".
 * <ul>
 *   <li>Quem vale: monstro COMUM (nada de chefe, de bicho com dono ou da tag {@code isekai_mahou:no_finisher}) vivo e
 *       com a vida abaixo da porcentagem da config (padrão 10%).</li>
 *   <li>Como: tecla de usar (botão direito) mirando nele, de perto. Agachado o clique passa direto (interação normal).</li>
 *   <li>O que acontece: o monstro fica atordoado, o jogador faz um golpe curto e no impacto o monstro morre como morto
 *       pelo jogador (saque e XP normais). Vale o contador {@value #COUNTER} e devolve um pouquinho de mana.</li>
 *   <li>Para dar tempo: na primeira vez que um monstro comum cai abaixo do limite por um golpe de jogador ele CAMBALEIA
 *       (lento e fraco por ~3 s). Só uma vez por monstro.</li>
 * </ul>
 * A regra {@link #canFinish} roda nos dois lados: no cliente ela só segura o clique (para o item da mão não ser usado)
 * e mostra o aviso na mira; quem decide é o servidor.
 */
public final class Finisher {
    /** Criaturas que nunca podem ser finalizadas (para pacotes de dados; vazia por padrão). */
    public static final TagKey<EntityType<?>> NO_FINISHER = TagKey.create(Registries.ENTITY_TYPE, IsekaiMahou.id("no_finisher"));
    /** Contador do {@code MagicData}: inimigos finalizados. */
    public static final String COUNTER = "feat.finisher";
    /** Marca (tag da entidade, salva com ela) de quem já cambaleou. */
    static final String WOUNDED_TAG = "isekai_mahou.cf_wounded";
    public static final String HINT = "combat_feel_finisher";

    private static final double REACH = 4.5;
    private static final int STAGGER_TICKS = 70;
    /** Mana devolvida ao finalizar (fração da mana máxima) — mimo pequeno, não é fonte de mana. */
    private static final float MANA_REFUND = 0.03F;

    /** Golpe da finalização e o tick em que ele acerta. */
    private record Move(String anim, int impact) {}

    private static final Move CLEAVE = new Move(MahouAnims.HOLY_CLEAVE, 15);
    private static final Move SLAM = new Move(MahouAnims.GROUND_SLAM, 10);

    /** Jogadores no meio de uma finalização → tick em que ela acaba. */
    private static final Map<UUID, Long> BUSY = new ConcurrentHashMap<>();

    static void init(IEventBus bus) {
        bus.addListener(PlayerInteractEvent.EntityInteract.class, Finisher::onInteract);
        bus.addListener(EventPriority.LOWEST, false, LivingDamageEvent.Post.class, Finisher::onDamage);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> BUSY.remove(e.getEntity().getUUID()));
        bus.addListener(ServerStoppedEvent.class, e -> BUSY.clear());
    }

    // =====================================================================================
    //  Regras (cliente e servidor)
    // =====================================================================================
    /** Fração da vida máxima abaixo da qual dá para finalizar. */
    public static float threshold() {
        return FeelConfig.of(FeelConfig.FINISHER_HEALTH_PERCENT) / 100F;
    }

    /** O monstro está vivo e com a vida abaixo do limite? */
    public static boolean isWounded(LivingEntity mob) {
        float max = mob.getMaxHealth(), hp = mob.getHealth();
        return mob.isAlive() && hp > 0F && max > 0F && hp < max * threshold();
    }

    /** Inimigo comum: hostil, sem dono, não é chefe. {@code player} só serve para "está me caçando" (servidor). */
    public static boolean isCommonEnemy(Mob mob, @Nullable Player player) {
        if (BossKit.isBossLike(mob) || mob.getType().is(NO_FINISHER) || mob.isInvulnerable()) return false;
        if (mob instanceof OwnableEntity own && own.getOwnerUUID() != null) return false;
        if (mob instanceof Enemy || mob.isAggressive()) return true;
        return player != null && mob.getTarget() == player;
    }

    /** O jogador pode finalizar este alvo agora? */
    public static boolean canFinish(@Nullable Player player, @Nullable Entity target) {
        if (player == null || !(target instanceof Mob mob) || !FeelConfig.on(FeelConfig.FINISHER_ENABLED)) return false;
        if (player.isSpectator() || !player.isAlive() || player.isShiftKeyDown() || player.isPassenger()) return false;
        if (PeaceMode.isOn(player) || !isWounded(mob) || !isCommonEnemy(mob, player)) return false;
        double reach = REACH + mob.getBbWidth() * 0.5;
        return player.distanceToSqr(mob) <= reach * reach;
    }

    // =====================================================================================
    //  Clique
    // =====================================================================================
    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!canFinish(player, event.getTarget())) return;
        // Segura o clique nos dois lados: o item da mão não é usado e a interação normal do monstro não roda
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player instanceof ServerPlayer sp && event.getHand() == InteractionHand.MAIN_HAND) {
            start(sp, (Mob) event.getTarget());
        }
    }

    /** Começa a finalização (servidor). @return false se o jogador já está no meio de uma. */
    static boolean start(ServerPlayer sp, Mob mob) {
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        Long busy = BUSY.get(sp.getUUID());
        if (busy != null && now < busy && busy - now < 60) return false;

        Move move = moveFor(sp.getMainHandItem());
        BUSY.put(sp.getUUID(), now + move.impact + 4);
        // O monstro trava no lugar até o golpe chegar (e um pouco mais, caso algo impeça a morte)
        mob.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, move.impact + 12, 0, false, false));
        mob.getNavigation().stop();
        // O jogador não é punido por parar para finalizar: proteção escondida só durante o gesto
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, move.impact + 4, 3, false, false, false));
        MahouAnims.play(sp, move.anim);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 0.6F);

        UUID id = sp.getUUID();
        BossKit.schedule(level, move.impact, () -> strike(sp, mob, id));
        return true;
    }

    /** Lâmina = corte vertical com as duas mãos; o resto (machado, martelo, punho, cajado) = pancada no chão. */
    private static Move moveFor(ItemStack stack) {
        if (stack.isEmpty()) return SLAM;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        boolean blade = stack.getItem() instanceof SwordItem || stack.is(ItemTags.SWORDS) || path.contains("katana")
                || path.contains("zanpakuto") || path.contains("blade") || path.contains("saber") || path.contains("rapier");
        return blade ? CLEAVE : SLAM;
    }

    private static void strike(ServerPlayer sp, Mob mob, UUID id) {
        BUSY.remove(id);
        if (!mob.isAlive() || sp.isRemoved() || sp.isDeadOrDying() || sp.level() != mob.level() || sp.distanceToSqr(mob) > 64.0) {
            return;
        }
        ServerLevel level = sp.serverLevel();
        double x = mob.getX(), y = mob.getY() + mob.getBbHeight() * 0.5, z = mob.getZ();

        // Primeiro a micro-pausa da finalização (a do "golpe pesado" que o dano geraria fica por baixo dela)
        HitStopServer.send(sp, FeelPayloads.STOP_FINISHER);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, x, y, z, 14, 0.3, 0.3, 0.3, 0.45);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 8, 0.25, 0.25, 0.25, 0.3);
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9F, 0.8F);

        // Morte normal por ataque do jogador: saque, XP e contadores de abate funcionam como sempre
        DamageSource src = level.damageSources().playerAttack(sp);
        mob.invulnerableTime = 0;
        mob.hurt(src, mob.getMaxHealth() * 4F + 40F);
        if (mob.isAlive()) {
            // Algo segurou o dano (armadura absurda, limite de dano de outro módulo): a finalização não falha
            mob.setLastHurtByPlayer(sp);
            mob.setHealth(0F);
            mob.die(src);
        }

        MagicApi.addCounter(sp, COUNTER, 1);
        MagicData data = MagicApi.get(sp);
        if (data != null && data.maxMana() > 0) data.addMana(data.maxMana() * MANA_REFUND);
    }

    // =====================================================================================
    //  Cambalear (a janela para finalizar)
    // =====================================================================================
    private static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || event.getNewDamage() <= 0F) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp) || !FeelConfig.on(FeelConfig.FINISHER_ENABLED)) return;
        if (!isWounded(mob) || mob.getTags().contains(WOUNDED_TAG) || !isCommonEnemy(mob, sp)) return;
        mob.addTag(WOUNDED_TAG);
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STAGGER_TICKS, 3, false, true));
        mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STAGGER_TICKS, 0, false, true));
        ((ServerLevel) mob.level()).sendParticles(ParticleTypes.DAMAGE_INDICATOR, mob.getX(), mob.getY() + mob.getBbHeight() + 0.2,
                mob.getZ(), 3, 0.2, 0.1, 0.2, 0.02);
        Hints.show(sp, HINT, Component.keybind("key.use"));
    }

    private Finisher() {}
}
